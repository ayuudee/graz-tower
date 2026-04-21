package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.core.world.*
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden test — the full stand-to-stand circuit loop.
 *
 * A single AI aircraft spawns at a parking stand with a circuit training
 * mission: 2 circuits, first touch-and-go, second full stop. The pilot
 * plans and executes the mission autonomously. Three controllers (ground,
 * tower, approach) sequence it through:
 *
 *   Stand → taxi → hold → line-up → takeoff →
 *   Circuit 1 (T&G): upwind → crosswind → downwind → base → final → land → lift off →
 *   Circuit 2 (full stop): upwind → crosswind → downwind → base → final → land →
 *   vacate → taxi → stand.
 *
 * The pilot's [HighLevelGoal.CircuitTraining] drives everything: the mission
 * tree, the derived [PilotGoal] transitions (DEPART → TOUCH_AND_GO → ARRIVE),
 * and the T&G lift-off. No test-harness phase stitching.
 */
class FullCircuitTest {

    private val aerodromeId = AerodromeId("TEST")
    private val runwayId = RunwayId("09")
    private val standId = StandId("STAND-1")
    private val taxiwayId = TaxiwayId("A")
    private val circuitId = CircuitProcedureId("CCT-09L")

    private object Pts {
        val stand = PointId("STAND-1")
        val apron = PointId("APRON-1")
        val hold = PointId("HOLD-SHORT-09")
        val thrA = PointId("THR-09")
        val thrB = PointId("THR-27")
        val rwyExit = PointId("EXIT-A")
        val upwind = PointId("UPWIND-1")
        val crosswind = PointId("CROSSWIND-1")
        val downwind = PointId("DOWNWIND-1")
        val base = PointId("BASE-1")
        val finalPt = PointId("FINAL-1")
    }

    private val positions = mapOf(
        Pts.stand to Position(-200.0, 100.0),
        Pts.apron to Position(-80.0, 60.0),
        Pts.hold to Position(-30.0, 30.0),
        Pts.thrA to Position(0.0, 0.0),
        Pts.thrB to Position(900.0, 0.0),
        Pts.rwyExit to Position(50.0, -30.0),
        Pts.upwind to Position(1200.0, 0.0),
        Pts.crosswind to Position(1200.0, 400.0),
        Pts.downwind to Position(0.0, 400.0),
        Pts.base to Position(-200.0, 250.0),
        Pts.finalPt to Position(-200.0, 0.0),
    )

    private val groundControllerId = ControllerId("GND")
    private val towerControllerId = ControllerId("TWR")
    private val approachControllerId = ControllerId("APP")
    private val alphaId = AircraftId("ALPHA")

    private val groundFrequency = Frequency.unsafe("121.800")
    private val towerFrequency = Frequency.unsafe("118.100")
    private val approachFrequency = Frequency.unsafe("125.300")

    private val world = AviationWorld(
        aerodromes = mapOf(
            aerodromeId to Aerodrome(
                icao = aerodromeId,
                elevation = Feet(0),
                magneticVariation = Degrees(0.0),
                transitionAltitude = Level.AltitudeFeet.unsafe(3000),
                runways = mapOf(
                    runwayId to Runway(
                        id = runwayId,
                        path = Path(listOf(Pts.thrA, Pts.thrB)),
                        threshold = Pts.thrA,
                        exits = listOf(RunwayExit(Pts.rwyExit, taxiwayId)),
                    ),
                ),
                stands = mapOf(
                    standId to Stand(id = standId, name = "Stand 1", point = Pts.stand),
                ),
                circuits = mapOf(
                    circuitId to CircuitProcedure(
                        id = circuitId,
                        runway = runwayId,
                        direction = CircuitDirection.LEFT_HAND,
                        legs = listOf(
                            CircuitLeg(LegName.UPWIND, Path(listOf(Pts.thrA, Pts.upwind))),
                            CircuitLeg(LegName.CROSSWIND, Path(listOf(Pts.upwind, Pts.crosswind))),
                            CircuitLeg(LegName.DOWNWIND, Path(listOf(Pts.crosswind, Pts.downwind))),
                            CircuitLeg(LegName.BASE, Path(listOf(Pts.downwind, Pts.base))),
                            CircuitLeg(LegName.FINAL, Path(listOf(Pts.base, Pts.finalPt, Pts.thrA))),
                        ),
                        altitude = Level.AltitudeFeet.unsafe(500),
                        goAroundPath = Path(listOf(Pts.thrA, Pts.upwind)),
                    ),
                ),
                roles = mapOf(
                    RoleName.GROUND to AerodromeRole(
                        name = RoleName.GROUND,
                        authorities = setOf(AuthorityGrant(AuthorityEntityType.TAXIWAY, setOf(AuthorityOperation.TAXI))),
                        frequency = groundFrequency,
                    ),
                    RoleName.TOWER to AerodromeRole(
                        name = RoleName.TOWER,
                        authorities = setOf(AuthorityGrant(AuthorityEntityType.RUNWAY, setOf(
                            AuthorityOperation.LINE_UP, AuthorityOperation.TAKEOFF, AuthorityOperation.LAND,
                        ))),
                        frequency = towerFrequency,
                    ),
                    RoleName.APPROACH to AerodromeRole(
                        name = RoleName.APPROACH,
                        authorities = setOf(AuthorityGrant(AuthorityEntityType.AIRSPACE_VOLUME, setOf(AuthorityOperation.AIRSPACE_TRANSIT))),
                        frequency = approachFrequency,
                    ),
                ),
            ),
        ),
    )

    private val worldIndex = WorldIndex(
        positions = positions,
        adjacency = mapOf(
            Pts.stand to setOf(Pts.apron),
            Pts.apron to setOf(Pts.stand, Pts.hold, Pts.rwyExit),
            Pts.hold to setOf(Pts.apron, Pts.thrA),
            Pts.rwyExit to setOf(Pts.apron, Pts.thrA),
            Pts.thrA to setOf(Pts.hold, Pts.rwyExit, Pts.thrB),
        ),
        entitiesByPoint = mapOf(
            Pts.stand to setOf(EntityRef.StandRef(standId)),
            Pts.apron to setOf(EntityRef.TaxiwayRef(taxiwayId)),
            Pts.hold to setOf(EntityRef.TaxiwayRef(taxiwayId)),
            Pts.rwyExit to setOf(EntityRef.TaxiwayRef(taxiwayId)),
            Pts.thrA to setOf(EntityRef.RunwayRef(runwayId), EntityRef.CircuitProcedureRef(circuitId)),
            Pts.thrB to setOf(EntityRef.RunwayRef(runwayId)),
            Pts.upwind to setOf(EntityRef.CircuitProcedureRef(circuitId)),
            Pts.crosswind to setOf(EntityRef.CircuitProcedureRef(circuitId)),
            Pts.downwind to setOf(EntityRef.CircuitProcedureRef(circuitId)),
            Pts.base to setOf(EntityRef.CircuitProcedureRef(circuitId)),
            Pts.finalPt to setOf(EntityRef.CircuitProcedureRef(circuitId)),
        ),
        holdingPointsByRunway = mapOf(runwayId to setOf(Pts.hold)),
        circuitLegsByPoint = mapOf(
            Pts.thrA to setOf(LegName.UPWIND, LegName.FINAL),
            Pts.upwind to setOf(LegName.UPWIND),
            Pts.crosswind to setOf(LegName.CROSSWIND),
            Pts.downwind to setOf(LegName.DOWNWIND),
            Pts.base to setOf(LegName.BASE),
            Pts.finalPt to setOf(LegName.FINAL),
        ),
    )

    private fun alpha() = AircraftState(
        id = alphaId,
        callsign = Callsign("ALPHA"),
        position = positions[Pts.stand]!!,
        positionPoint = Pts.stand,
        pilotGoal = PilotGoal.DEPART, // initial; derivePilotGoal will update on first tick
        humanPiloted = false,
        route = PilotRoute.None,
        phase = PilotPhase.AtStand,
        pilotMission = createMission(
            HighLevelGoal.CircuitTraining(circuits = 2, fullStopOnLast = true),
            PilotPhase.AtStand,
            SimTime.ZERO,
        ).let { m ->
            // Skip pre-taxi steps for AI aircraft. The ground controller acts
            // proactively (AiProactive) — pilot requests aren't needed and
            // cause step-on collisions on the frequency.
            m.copy(root = m.root
                .markComplete(MissionStep.REQUEST_STARTUP)
                .markComplete(MissionStep.AWAIT_STARTUP_APPROVAL)
                .markComplete(MissionStep.REQUEST_TAXI))
        },
    )

    private fun snapshot(label: String, state: SimState) {
        val ac = state.aircraft[alphaId] ?: run { println("  $label: aircraft not found"); return }
        val commitments = state.beliefs.values.flatMap { it.commitments.entries }
            .filter { it.key == alphaId }
            .map { "${it.value.kind.trafficType}/${it.value.stage}" }
        val owner = state.controllers.entries.firstOrNull { alphaId in it.value.responsibilities }?.key
        val step = ac.pilotMission?.currentTask?.step?.name ?: "none"
        println("  $label: phase=${ac.phase} goal=${ac.pilotGoal} point=${ac.positionPoint} " +
            "alt=${"%.0f".format(ac.altitudeM)}m step=$step owner=$owner commit=$commitments")
    }

    @Test
    fun `stand → 2 circuits → stand — autonomous pilot golden test`() {
        val initial = SimState.initial(
            seed = 42L, world = world, worldIndex = worldIndex,
            controllers = listOf(
                ControllerSpec(groundControllerId, RoleName.GROUND, aerodromeId, groundFrequency, setOf(alphaId)),
                ControllerSpec(towerControllerId, RoleName.TOWER, aerodromeId, towerFrequency, emptySet()),
                ControllerSpec(approachControllerId, RoleName.APPROACH, aerodromeId, approachFrequency, emptySet()),
            ),
        )
        val events = listOf(
            SimEvent.PhysicsTick(SimTime.ZERO),
            SimEvent.Spawn(SimTime.ZERO, alpha()),
            SimEvent.ControllerCycle(SimTime.ZERO, groundControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, towerControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, approachControllerId),
        )

        // Timeline for debugging
        println("=== Full Circuit Timeline (2 circuits, autonomous pilot) ===")
        for (t in listOf(5, 30, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600)) {
            snapshot("t=${t}s", runUntil(initial, events, SimTime.ofSeconds(t.toLong())))
        }
        println("=== End Timeline ===")

        val result = runUntil(initial, events, SimTime.ofSeconds(1800))

        // Determinism
        val result2 = runUntil(initial, events, SimTime.ofSeconds(1800))
        assertEquals(result, result2, "Two runs with the same seed must produce identical state")

        val ac = result.aircraft[alphaId]!!

        // The aircraft must be parked at the stand.
        assertEquals(PilotPhase.Parked, ac.phase,
            "Aircraft must be parked. Actual: ${ac.phase} at ${ac.positionPoint} alt=${"%.0f".format(ac.altitudeM)}m step=${ac.pilotMission?.currentTask?.step}")
        assertEquals(Pts.stand, ac.positionPoint,
            "Aircraft must have returned to the stand")
        assertEquals(0.0, ac.altitudeM, "Aircraft must be on the ground")
        assertEquals(0.0, ac.speedMps, "Aircraft must be stopped")

        // All transmissions cleared.
        assertTrue(result.inFlightTransmissions.isEmpty(),
            "All transmissions must have cleared the air")
    }
}
