package xyz.easiersaid.twr.pilot

import arrow.core.Some
import arrow.core.getOrElse
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.CircuitLeg
import xyz.easiersaid.twr.core.world.CircuitProcedure
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.PhysicalGeometry
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.core.world.buildWorldIndex
import xyz.easiersaid.twr.pilot.world.toPilotView
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.CircuitDirection
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * fn-15.1 (G3a-react-tailwind R10) — Tick A → Tick B integration through
 * [pilotDecide] (NOT direct applier call).
 *
 * Sibling of fn-14.1's `PilotCrosswindTickATickBTest`. Protects the
 * **load-bearing reuse assumption** that the existing Circuit-mode
 * planner picks up the tailwind GA route on the next tick — direct
 * applier-postcondition tests don't exercise that path. Mirrors
 * `PilotAtcInitiatedGoAroundSpec`'s `Tick B` pin shape.
 *
 * Sequence:
 *  1. **Tick A**: aircraft on final at runway 09 (heading 090°);
 *     world wind 270°M at 15 kt → dead tailwind component = 15 kt
 *     against C172's 10 kt AFH advisory → fires TailwindLimitExceeded.
 *     `pilotDecide` produces `route=None, phase=Final`, transmits
 *     `Report(GoingAround)`, rewrites mission to CircuitAfterGoAround.
 *  2. **Advance one decision cycle** (post-Tick-A): mark
 *     GOING_AROUND complete → currentTask.step = FLY_DEPARTURE.
 *  3. **Tick B**: same world wind, same aircraft phase=Final +
 *     route=None. `pilotDecide` → `planCircuitTrainedGoAround`'s
 *     Circuit-mode FLY_DEPARTURE + Final + no-route special case
 *     fires → intent is `phase=Climbing, route=PilotRoute.Airborne(
 *     waypoints.head = UPWIND_END, ...)` per the synthetic
 *     `CircuitProcedure.goAroundPath = Path(THRESHOLD, UPWIND_END)`.
 *
 * Doctrine: FAA AFH Ch 9 (tailwind landings as high-risk operations);
 * Boeing 737-800 FCOM Limitations §1 (B738 hard-limit anchor); ICAO Doc
 * 4444 §7.10.2; CAP 413 §4.66 (Ed 24 — formerly §4.67 in Ed 23,
 * renumbered per fn-17.1).
 */
class PilotTailwindTickATickBTest {

    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ZERO

    // ── Synthetic world (runway 09 → heading 090°M) ─────────────────────
    // Tailwind setup: wind 270°M against runway-09 heading 090° produces
    // a 180° (cos = −1.0) dead tailwind. 15 kt > 10 kt C172 AFH advisory
    // fires the event. Note: this same fixture would NOT fire the
    // crosswind branch (|sin(180°)| = 0); the test isolates the tailwind
    // axis.
    private val ADRM_ID = AerodromeId("XXXX")
    private val RWY_ID = RunwayId("09")
    private val CKT_ID = CircuitProcedureId("RWY09-LH")
    private val THRESHOLD = PointId("T")
    private val DEP_END = PointId("DEP")
    private val UPWIND_END = PointId("UE")
    private val CROSSWIND_END = PointId("CE")
    private val DOWNWIND_END = PointId("DE")
    private val BASE_TURN = PointId("BT")

    private fun syntheticWorld(): AviationWorld {
        val runway = Runway(
            id = RWY_ID,
            path = Path(listOf(THRESHOLD, DEP_END)),
            threshold = THRESHOLD,
        )
        val circuit = CircuitProcedure(
            id = CKT_ID,
            runway = RWY_ID,
            direction = CircuitDirection.LEFT_HAND,
            legs = listOf(
                CircuitLeg(LegName.UPWIND, Path(listOf(THRESHOLD, UPWIND_END))),
                CircuitLeg(LegName.CROSSWIND, Path(listOf(UPWIND_END, CROSSWIND_END))),
                CircuitLeg(LegName.DOWNWIND, Path(listOf(CROSSWIND_END, DOWNWIND_END))),
                CircuitLeg(LegName.BASE, Path(listOf(DOWNWIND_END, BASE_TURN))),
                CircuitLeg(LegName.FINAL, Path(listOf(BASE_TURN, THRESHOLD))),
            ),
            altitude = Level.AltitudeFeet.unsafe(1000),
            goAroundPath = Path(listOf(THRESHOLD, UPWIND_END)),
        )
        val aerodrome = Aerodrome(
            icao = ADRM_ID,
            elevation = Feet(0),
            magneticVariation = Degrees(0.0),
            transitionAltitude = Level.AltitudeFeet.unsafe(5000),
            runways = mapOf(RWY_ID to runway),
            circuits = mapOf(CKT_ID to circuit),
        )
        return AviationWorld(
            geometry = PhysicalGeometry(
                points = mapOf(
                    THRESHOLD to Position(0.0, 0.0),
                    DEP_END to Position(1000.0, 0.0),
                    UPWIND_END to Position(2000.0, 0.0),
                    CROSSWIND_END to Position(2000.0, 1000.0),
                    DOWNWIND_END to Position(0.0, 1000.0),
                    BASE_TURN to Position(-500.0, 500.0),
                ),
            ),
            aerodromes = mapOf(ADRM_ID to aerodrome),
        )
    }

    private fun singleFullStopMission(): PilotMission {
        val goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop))
        return PilotMission(
            goal = goal,
            root = planMission(goal),
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(RWY_ID, RunwayAssignmentSource.Radio.TaxiClearance)),
        )
    }

    private fun missionAtStep(target: MissionStep): PilotMission {
        var mission = singleFullStopMission()
        val groundSteps = listOf(
            MissionStep.REQUEST_TAXI, MissionStep.TAXI_TO_HOLDING,
            MissionStep.RUN_UP_CHECKS, MissionStep.REPORT_READY,
            MissionStep.AWAIT_LINE_UP, MissionStep.AWAIT_TAKEOFF_CLEARANCE,
        )
        val circuitOrder = listOf(
            MissionStep.FLY_DEPARTURE, MissionStep.FLY_DOWNWIND,
            MissionStep.REPORT_DOWNWIND, MissionStep.AWAIT_SEQUENCING,
            MissionStep.FLY_BASE, MissionStep.REPORT_BASE,
            MissionStep.FLY_FINAL, MissionStep.REPORT_FINAL,
            MissionStep.AWAIT_LANDING_CLEARANCE, MissionStep.LAND,
        )
        val targetIndex = circuitOrder.indexOf(target)
        require(targetIndex >= 0) { "$target not in circuit pattern" }
        val priorCircuitSteps = circuitOrder.take(targetIndex)
        (groundSteps + priorCircuitSteps).forEach { step ->
            mission = mission.copy(root = mission.root.markComplete(step))
        }
        check(mission.currentTask?.step == target) {
            "fixture: mission must be at $target; got ${mission.currentTask?.step}"
        }
        return mission
    }

    private fun aircraftAt(
        phase: PilotPhase,
        route: PilotRoute,
        mission: PilotMission,
        // Above DA (100 m) so the DA branch of derivePilotEvent does NOT
        // fire — keeps the test isolated to the tailwind branch.
        altitudeM: Double = 200.0,
    ): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = Position(0.0, 0.0),
        positionPoint = THRESHOLD,
        altitudeM = altitudeM,
        phase = phase,
        route = route,
        type = AircraftType.C172,
        pilotMission = mission,
    )

    private fun weatherAt15ktTailwindAgainstRwy09(): Map<AerodromeId, WindReport> = mapOf(
        // Wind from 270°M against runway 09 (heading 090°M) → relative 180°
        // → cos(180°) = −1 → dead tailwind = 15 kt > 10 kt C172 AFH advisory.
        // Sin(180°) = 0 → crosswind = 0, so this fixture isolates the
        // tailwind axis (crosswind branch returns null on the same wind).
        ADRM_ID to WindReport.Available(Wind.unsafe(directionDegrees = 270, speedKnots = 15)),
    )

    @Test
    fun `Tick A pilotDecide produces route=None, phase=Final, Report(GoingAround) on tailwind exceedance`() {
        val mission = missionAtStep(MissionStep.FLY_FINAL)
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        val airborneFinal = PilotRoute.Airborne(
            waypoints = arrow.core.NonEmptyList(THRESHOLD, emptyList()),
            targetAltitudeM = 0.0,
            arrivalPhase = PilotPhase.LandingRoll,
        )
        val aircraft = aircraftAt(
            phase = PilotPhase.Final,
            route = airborneFinal,
            mission = mission,
        )
        val output = pilotDecide(
            PilotInput(
                aircraft = aircraft,
                worldIndex = worldIndex,
                world = world.toPilotView(),
                now = now0,
                weatherByAerodrome = weatherAt15ktTailwindAgainstRwy09(),
            ),
        ).getOrElse { fail("pilotDecide failed: $it") }

        // Reactive Tick A intent
        assertEquals(
            PilotPhase.Final,
            output.intent.phase,
            "Tick A: phase=Final retained so Tick B's planCircuitTrainedGoAround special-case fires",
        )
        assertEquals(
            PilotRoute.None,
            output.intent.route,
            "Tick A: route=None invalidates kinematic route — pilot does not continue toward threshold",
        )
        // Transmission emitted.
        assertTrue(
            Report(listOf(ReportEvent.GoingAround)) in output.transmissions,
            "Tick A: pilotDecide must include Report(GoingAround) per CAP 413 §4.66 / Doc 4444 §12.3.4.18; " +
                "got ${output.transmissions}",
        )
        // Mission tree rewritten via subtree replacement.
        val newMission = output.updatedMission ?: fail("updatedMission must be non-null after Tick A")
        assertEquals(
            MissionStep.GOING_AROUND,
            newMission.currentTask?.step,
            "Tick A: post-rewrite active step is GOING_AROUND (CircuitAfterGoAround subtree)",
        )
    }

    @Test
    fun `Tick B — pilotDecide builds the GA route via the reused Circuit-mode planner`() {
        // Step 1: produce the post-Tick-A mission via pilotDecide (Tick A).
        val initialMission = missionAtStep(MissionStep.FLY_FINAL)
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        val weather = weatherAt15ktTailwindAgainstRwy09()
        val airborneFinal = PilotRoute.Airborne(
            waypoints = arrow.core.NonEmptyList(THRESHOLD, emptyList()),
            targetAltitudeM = 0.0,
            arrivalPhase = PilotPhase.LandingRoll,
        )
        val tickAOutput = pilotDecide(
            PilotInput(
                aircraft = aircraftAt(
                    phase = PilotPhase.Final, route = airborneFinal, mission = initialMission,
                ),
                worldIndex = worldIndex, world = world.toPilotView(), now = now0,
                weatherByAerodrome = weather,
            ),
        ).getOrElse { fail("Tick A pilotDecide failed: $it") }
        val postTickAMission = tickAOutput.updatedMission ?: fail("Tick A: updatedMission must be non-null")

        // Step 2: advance past GOING_AROUND to land on FLY_DEPARTURE for
        // Tick B's predicate (mirrors PilotAtcInitiatedGoAroundSpec's
        // Tick B fixture — the cognitive layer's stepTransmission would
        // mark GOING_AROUND complete after emitting its report on the
        // intervening tick; advance it explicitly here).
        val tickBMission = postTickAMission.copy(
            root = postTickAMission.root.markComplete(MissionStep.GOING_AROUND),
        )
        check(tickBMission.currentTask?.step == MissionStep.FLY_DEPARTURE) {
            "fixture: post-GOING_AROUND active step is FLY_DEPARTURE; got ${tickBMission.currentTask?.step}"
        }

        // Step 3: Tick B — aircraft state carried from Tick A (phase=Final,
        // route=None). pilotDecide must invoke planCircuitTrainedGoAround
        // and build the GA route per the synthetic CircuitProcedure's
        // goAroundPath.
        val tickBAircraft = aircraftAt(
            phase = PilotPhase.Final,
            route = PilotRoute.None,
            mission = tickBMission,
            altitudeM = 100.0,
        )
        val tickBOutput = pilotDecide(
            PilotInput(
                aircraft = tickBAircraft,
                worldIndex = worldIndex,
                world = world.toPilotView(),
                now = now0,
                weatherByAerodrome = weather, // wind still over limit — confirms hysteresis on the planner side
            ),
        ).getOrElse { fail("Tick B pilotDecide failed: $it") }

        assertEquals(
            PilotPhase.Climbing,
            tickBOutput.intent.phase,
            "Tick B: phase=Climbing — planCircuitTrainedGoAround sets it",
        )
        val airborne = tickBOutput.intent.route as? PilotRoute.Airborne
            ?: fail("Tick B: intent.route must be PilotRoute.Airborne (got ${tickBOutput.intent.route})")
        // The GA path is `Path(THRESHOLD, UPWIND_END)`. After the THRESHOLD-
        // filter applied by buildGoAroundRoute the route head is
        // UPWIND_END — proves the reused planner ran on the GA path,
        // NOT a normal circuit pattern.
        assertEquals(
            UPWIND_END,
            airborne.waypoints.head,
            "Tick B: GA route uses CircuitProcedure.goAroundPath — load-bearing reuse of the " +
                "Circuit-mode FLY_DEPARTURE + Final + no-route special case (planCircuitTrainedGoAround)",
        )
    }
}
