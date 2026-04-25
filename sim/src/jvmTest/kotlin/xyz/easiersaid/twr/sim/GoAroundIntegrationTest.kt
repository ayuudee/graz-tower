package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.core.world.*
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Go-around integration test — exercises the FULL reversal path.
 *
 * An AI aircraft spawns on final approach without landing clearance.
 * At decision altitude, the pilot self-initiates a go-around:
 *   - Mission tree is replaced (GOING_AROUND + fresh CIRCUIT)
 *   - hasClearance is reset
 *   - GoingAround report is transmitted
 *   - Pilot climbs and re-enters the circuit
 *   - Controller re-sequences and issues clearance on the second approach
 *   - Pilot lands, vacates, taxis to stand
 *
 * This test catches the class of bugs found in the ultra review:
 *   C1 (stale hasClearance), C2 (IFR go-around task), C3 (dead transmission),
 *   C4 (replaceChild on completed subtree).
 */
class GoAroundIntegrationTest {

    private val aerodromeId = AerodromeId("TEST")
    private val runwayId = RunwayId("09")
    private val standId = StandId("STAND-1")
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
                        exits = listOf(RunwayExit(Pts.rwyExit, TaxiwayId("A"))),
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
            Pts.apron to setOf(EntityRef.TaxiwayRef(TaxiwayId("A"))),
            Pts.hold to setOf(EntityRef.TaxiwayRef(TaxiwayId("A"))),
            Pts.rwyExit to setOf(EntityRef.TaxiwayRef(TaxiwayId("A"))),
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
        thresholdByRunway = mapOf(runwayId to Pts.thrA),
    )

    /**
     * Aircraft spawns at stand with a 1-circuit mission.
     * The first approach will have no landing clearance at decision altitude
     * (the controller needs time to observe and issue clearance — with a
     * single aircraft this happens naturally if the pilot is fast enough).
     *
     * Actually, with the current controller, clearance IS issued proactively
     * once the pilot is on approach and the runway is clear. To force a
     * go-around, we test that the go-around MECHANISM works correctly by
     * starting the pilot mid-circuit at final without clearance.
     */
    private fun alphaOnFinal(): AircraftState {
        // Create mission starting at final — no clearance.
        val mission = createMission(
            goal = HighLevelGoal.CircuitTraining(circuits = 1, fullStopOnLast = true),
            startPhase = PilotPhase.Final,
            time = SimTime.ZERO,
            humanPiloted = false,
        )
        return AircraftState(
            id = alphaId,
            callsign = Callsign("ALPHA"),
            position = positions[Pts.finalPt]!!,
            positionPoint = Pts.finalPt,
            pilotGoal = PilotGoal.ARRIVE,
            humanPiloted = false,
            route = PilotRoute.None,
            phase = PilotPhase.Final,
            altitudeM = 50.0, // below decision altitude (100m) — triggers go-around
            targetAltitudeM = 0.0,
            speedMps = PilotConstants.APPROACH_SPEED_MPS,
            targetSpeedMps = PilotConstants.APPROACH_SPEED_MPS,
            pilotMission = mission.copy(activeRunway = runwayId),
        )
    }

    @Test
    fun `pilot self-initiates go-around at decision altitude and eventually lands`() {
        val initial = SimState.initial(
            seed = 42L, world = world, worldIndex = worldIndex,
            controllers = listOf(
                ControllerSpec(groundControllerId, RoleName.GROUND, aerodromeId, groundFrequency, emptySet()),
                ControllerSpec(towerControllerId, RoleName.TOWER, aerodromeId, towerFrequency, setOf(alphaId)),
                ControllerSpec(approachControllerId, RoleName.APPROACH, aerodromeId, approachFrequency, emptySet()),
            ),
            weatherByAerodrome = world.aerodromes.keys.associateWith {
                WeatherObservation(wind = null, qnh = null, visibility = null)
            },
        ).getOrElse { error("GoAround self-initiated setup invalid: $it") }
        val events = listOf(
            SimEvent.PhysicsTick(SimTime.ZERO),
            SimEvent.Spawn(SimTime.ZERO, alphaOnFinal()),
            SimEvent.ControllerCycle(SimTime.ZERO, groundControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, towerControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, approachControllerId),
        )

        // After 5 seconds: the pilot should have initiated go-around
        // (below decision altitude without clearance).
        val after5s = runUntil(initial, events, SimTime.ofSeconds(5))
        val ac5 = after5s.aircraft[alphaId]!!
        val mission5 = ac5.pilotMission!!

        // hasClearance must be false (reset by go-around).
        assertEquals(false, mission5.hasClearance,
            "hasClearance must be false after go-around")

        // Mission should contain a CircuitAfterGoAround compound.
        val hasGoAroundTask = mission5.root.children.any {
            it is CompoundTask && it.name is TaskName.CircuitAfterGoAround
        }
        assertTrue(hasGoAroundTask || mission5.root.name is TaskName.CircuitAfterGoAround,
            "Mission tree should contain CircuitAfterGoAround after go-around")

        // Pilot should be climbing (go-around intent).
        assertTrue(ac5.altitudeM > 50.0 || ac5.phase is PilotPhase.Climbing,
            "Pilot should be climbing after go-around, was phase=${ac5.phase} alt=${ac5.altitudeM}")

        // Run longer: the pilot should eventually land.
        val result = runUntil(initial, events, SimTime.ofSeconds(1200))
        val acFinal = result.aircraft[alphaId]!!

        println("Go-around test final state: phase=${acFinal.phase} point=${acFinal.positionPoint} " +
            "alt=${"%.0f".format(acFinal.altitudeM)} step=${acFinal.pilotMission?.currentTask?.step}")

        // The aircraft must have landed and returned to the stand.
        assertEquals(PilotPhase.Parked, acFinal.phase,
            "Aircraft must be parked after go-around + second circuit. " +
            "Actual: phase=${acFinal.phase} point=${acFinal.positionPoint} " +
            "alt=${"%.0f".format(acFinal.altitudeM)} step=${acFinal.pilotMission?.currentTask?.step}")
        assertEquals(Pts.stand, acFinal.positionPoint,
            "Aircraft must have returned to the stand")
    }

    // ── A10: ATC-instructed go-around ─────────────────────────────────

    /**
     * Aircraft on final WITH landing clearance. ATC issues go-around.
     *
     * Tests the [processInstruction](GoAround) path:
     *   1. hasClearance is reset to false.
     *   2. Mission tree is replaced with CircuitAfterGoAround + goAroundTask.
     *   3. Pilot climbs and eventually lands on the second circuit.
     *
     * Distinct from the self-initiated test: here the mission already has
     * hasClearance=true (controller has issued ClearedToLand) when the
     * go-around instruction arrives. Both paths use [goAroundTask] which
     * is now fully symmetric — no AWAITING_ATC_INSTRUCTION in VFR.
     */
    @Test
    fun `ATC-instructed go-around resets clearance and pilot eventually lands`() {
        // Build a mission at the LAND step with hasClearance=true:
        // pilot is on final, has received ClearedToLand, and is about to touch down.
        // ATC then issues go-around (runway incursion, runway not clear, etc.).
        val baseMission = createMission(
            goal = HighLevelGoal.Arrival(),
            startPhase = PilotPhase.Final,
            time = SimTime.ZERO,
            humanPiloted = false,
        )
        // Simulate receiving ClearedToLand: marks sequencing/flying steps complete + hasClearance.
        val clearedMission = processInstruction(
            ClearedToLand(alphaId, runwayId),
            baseMission.copy(activeRunway = runwayId),
            SimTime.ZERO,
        )
        assertEquals(true, clearedMission.hasClearance,
            "Precondition: mission must have clearance before go-around")

        // ATC issues go-around while the pilot has clearance.
        val afterGoAround = processInstruction(GoAround(alphaId), clearedMission, SimTime.ZERO)

        // ── Mission state assertions ─────────────────────────────────
        assertFalse(afterGoAround.hasClearance,
            "hasClearance must be reset to false by ATC go-around instruction")

        // Root must contain CircuitAfterGoAround (Arrive → [ArrivalJoin?, CircuitAfterGoAround, GroundArrival]).
        val gaCompound = afterGoAround.root.children
            .filterIsInstance<CompoundTask>()
            .firstOrNull { it.name is TaskName.CircuitAfterGoAround }
        assertTrue(gaCompound != null,
            "Mission tree must contain CircuitAfterGoAround after ATC go-around")

        // CircuitAfterGoAround must start with a GoAround task (GOING_AROUND step).
        val goAroundCompound = gaCompound!!.children
            .filterIsInstance<CompoundTask>()
            .firstOrNull { it.name is TaskName.GoAround }
        assertTrue(goAroundCompound != null, "CircuitAfterGoAround must contain GoAround task")

        val goAroundStep = goAroundCompound!!.children
            .filterIsInstance<PrimitiveTask>()
            .firstOrNull { it.step == MissionStep.GOING_AROUND }
        assertTrue(goAroundStep != null, "GoAround task must contain GOING_AROUND step")

        // VFR go-around: no AWAITING_ATC_INSTRUCTION — pilot rejoins autonomously.
        val hasAwaitingAtc = goAroundCompound.children
            .filterIsInstance<PrimitiveTask>()
            .any { it.step == MissionStep.AWAITING_ATC_INSTRUCTION }
        assertFalse(hasAwaitingAtc,
            "VFR ATC-instructed go-around must NOT contain AWAITING_ATC_INSTRUCTION — " +
            "pilot rejoins circuit autonomously after reporting going around")

        // ── Integration: pilot must eventually land ───────────────────
        val alphaWithGoAround = AircraftState(
            id = alphaId,
            callsign = Callsign("ALPHA"),
            position = positions[Pts.finalPt]!!,
            positionPoint = Pts.finalPt,
            pilotGoal = PilotGoal.ARRIVE,
            humanPiloted = false,
            route = PilotRoute.None,
            phase = PilotPhase.Final,
            altitudeM = 80.0,
            targetAltitudeM = 0.0,
            speedMps = PilotConstants.APPROACH_SPEED_MPS,
            targetSpeedMps = PilotConstants.APPROACH_SPEED_MPS,
            pilotMission = afterGoAround,
        )

        val initial = SimState.initial(
            seed = 42L, world = world, worldIndex = worldIndex,
            controllers = listOf(
                ControllerSpec(groundControllerId, RoleName.GROUND, aerodromeId, groundFrequency, emptySet()),
                ControllerSpec(towerControllerId, RoleName.TOWER, aerodromeId, towerFrequency, setOf(alphaId)),
                ControllerSpec(approachControllerId, RoleName.APPROACH, aerodromeId, approachFrequency, emptySet()),
            ),
            weatherByAerodrome = world.aerodromes.keys.associateWith {
                WeatherObservation(wind = null, qnh = null, visibility = null)
            },
        ).getOrElse { error("GoAround afterGoAround setup invalid: $it") }
        val events = listOf(
            SimEvent.PhysicsTick(SimTime.ZERO),
            SimEvent.Spawn(SimTime.ZERO, alphaWithGoAround),
            SimEvent.ControllerCycle(SimTime.ZERO, groundControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, towerControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, approachControllerId),
        )

        val result = runUntil(initial, events, SimTime.ofSeconds(1200))
        val acFinal = result.aircraft[alphaId]!!

        println("ATC go-around test final state: phase=${acFinal.phase} point=${acFinal.positionPoint} " +
            "alt=${"%.0f".format(acFinal.altitudeM)} step=${acFinal.pilotMission?.currentTask?.step}")

        assertEquals(PilotPhase.Parked, acFinal.phase,
            "Aircraft must be parked after ATC go-around + second circuit. " +
            "Actual: phase=${acFinal.phase} point=${acFinal.positionPoint} " +
            "alt=${"%.0f".format(acFinal.altitudeM)} step=${acFinal.pilotMission?.currentTask?.step}")
        assertEquals(Pts.stand, acFinal.positionPoint,
            "Aircraft must have returned to the stand")
    }
}
