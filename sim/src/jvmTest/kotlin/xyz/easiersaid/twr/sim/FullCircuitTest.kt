package xyz.easiersaid.twr.sim

import arrow.core.NonEmptyList
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.core.world.*
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden test — the full stand-to-stand loop.
 *
 * A single AI aircraft departs from a parking stand, flies one circuit, lands,
 * vacates, and taxis back to the stand. Three controllers (ground, tower,
 * approach) must sequence it through every phase:
 *
 *   Stand → taxi → hold → line-up → takeoff → circuit (upwind → crosswind →
 *   downwind → base → final) → land → vacate → taxi → stand.
 *
 * Implementation: two-phase simulation. Phase 1 uses [PilotGoal.DEPART] for
 * correct departure handling. When approach takes ownership on the crosswind
 * leg, phase 2 swaps the goal to [PilotGoal.ARRIVE] and adds an arrival route
 * for the remaining circuit legs, then continues to the stand.
 *
 * This is the 4e closure target: stand→taxi→depart→circuit→land→taxi→stand.
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

    private val patternAltitudeM = 150.0

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

    private val controllers = listOf(
        ControllerSpec(groundControllerId, RoleName.GROUND, aerodromeId, groundFrequency, setOf(alphaId)),
        ControllerSpec(towerControllerId, RoleName.TOWER, aerodromeId, towerFrequency, emptySet()),
        ControllerSpec(approachControllerId, RoleName.APPROACH, aerodromeId, approachFrequency, emptySet()),
    )

    private val seedEvents = listOf(
        SimEvent.PhysicsTick(SimTime.ZERO),
        SimEvent.ControllerCycle(SimTime.ZERO, groundControllerId),
        SimEvent.ControllerCycle(SimTime.ZERO, towerControllerId),
        SimEvent.ControllerCycle(SimTime.ZERO, approachControllerId),
    )

    @Test
    fun `stand → circuit → stand — full loop golden test`() {
        // ── Phase 1: DEPART — stand to crosswind ────────────────────────
        val departAlpha = AircraftState(
            id = alphaId, callsign = Callsign("ALPHA"),
            position = positions[Pts.stand]!!, positionPoint = Pts.stand,
            pilotGoal = PilotGoal.DEPART, humanPiloted = false,
            route = PilotRoute.None, phase = PilotPhase.AtStand,
        )

        val phase1 = runUntil(
            initial = SimState.initial(42L, world, worldIndex, controllers = controllers),
            initialEvents = seedEvents + SimEvent.Spawn(SimTime.ZERO, departAlpha),
            until = SimTime.ofSeconds(300),
        )

        // Verify departure half completed
        val afterDepart = phase1.aircraft[alphaId]!!
        assertTrue(afterDepart.altitudeM > 50.0,
            "Phase 1: aircraft must be airborne. Actual altitude: ${afterDepart.altitudeM}")
        assertTrue(alphaId in phase1.controllers[approachControllerId]!!.responsibilities,
            "Phase 1: approach must own ALPHA after departure handoff")

        // ── Phase 2: ARRIVE — crosswind to stand ────────────────────────
        // Swap goal to ARRIVE and give the aircraft a circuit route for the
        // remaining legs (downwind → base → final → threshold).
        val arriveAlpha = afterDepart.copy(
            pilotGoal = PilotGoal.ARRIVE,
            route = PilotRoute.Airborne(
                waypoints = NonEmptyList(Pts.downwind, listOf(Pts.base, Pts.finalPt, Pts.thrA)),
                targetAltitudeM = patternAltitudeM,
                arrivalPhase = PilotPhase.LandingRoll,
            ),
            targetAltitudeM = patternAltitudeM,
        )
        val aircraft2 = LinkedHashMap(phase1.aircraft).apply { put(alphaId, arriveAlpha) }
        val phase2Initial = phase1.copy(aircraft = aircraft2)

        // Re-seed recurring events from the current sim time.
        val t = phase1.now
        val phase2Events = listOf(
            SimEvent.PhysicsTick(t),
            SimEvent.PilotDecisionTick(t, alphaId),
            SimEvent.ControllerCycle(t, groundControllerId),
            SimEvent.ControllerCycle(t, towerControllerId),
            SimEvent.ControllerCycle(t, approachControllerId),
        )

        val phase2 = runUntil(
            initial = phase2Initial,
            initialEvents = phase2Events,
            until = SimTime.ofSeconds(900),
        )

        // ── Assertions ──────────────────────────────────────────────────
        val run2 = runUntil(phase2Initial, phase2Events, SimTime.ofSeconds(900))
        assertEquals(phase2, run2, "Two phase-2 runs with same state must be identical")

        val ac = phase2.aircraft[alphaId]!!
        assertEquals(PilotPhase.Parked, ac.phase,
            "Aircraft must be parked. Actual: ${ac.phase} at ${ac.positionPoint}")
        assertEquals(Pts.stand, ac.positionPoint,
            "Aircraft must have returned to the stand")
        assertEquals(0.0, ac.altitudeM, "Aircraft must be on the ground")
        assertEquals(0.0, ac.speedMps, "Aircraft must be stopped")

        val gnd = phase2.controllers[groundControllerId]!!
        val twr = phase2.controllers[towerControllerId]!!
        val app = phase2.controllers[approachControllerId]!!
        assertTrue(alphaId !in twr.responsibilities, "Tower must have released ALPHA")
        assertTrue(alphaId !in app.responsibilities, "Approach must have released ALPHA")

        assertTrue(phase2.inFlightTransmissions.isEmpty(),
            "All transmissions must have cleared the air")
    }
}
