package xyz.easiersaid.twr.pilot

import arrow.core.None
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
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.CircuitDirection
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * fn-11.1 — pilot-side unit pins for the trained (planned) go-around path.
 *
 * Covers the four Acceptance pins (per the task spec):
 *  1. Two-tick sequence: Tick A emits `phase=Final + route=PilotRoute.None
 *     + Report(GoingAround)`; Tick B emits `phase=Climbing + route built
 *     from CircuitProcedure.goAroundPath`.
 *  2. Circuit-mode discriminator regression: an ordinary `FullStop` circuit's
 *     FLY_DEPARTURE on the ground does NOT hit the trained-GA Tick B
 *     special-case (no GA route built on a normal lift-off).
 *  3. `ClearedToLand` does NOT advance `FLY_FINAL_TO_SHORT_FINAL`. The
 *     trained-GA short-final descent step stays active even after
 *     clearance is received; only the altitude/phase gate completes it.
 *  4. Trained-GA `Report(Downwind)` carries `CircuitIntent.FULL_STOP`
 *     (NOT `TOUCH_AND_GO`, NOT `null`). Pilot announces a normal landing
 *     intent at downwind; the GA at short-final is the instructor's
 *     pre-arranged training exercise, private to the pilot's mission tree
 *     until the announcement at decision altitude per CAP 413 §4.67.
 *
 * Doctrine refs:
 *  - CAP 413 §4.66/§4.67: pilot-initiated go-around announcement at the
 *    decision-altitude gate.
 *  - ICAO Doc 4444 §12.3.4.18: pilot transmission `GOING AROUND`.
 *  - FAA AFH §9: any approach or landing may result in a go-around;
 *    flight-school doctrine pre-arranges trained GAs.
 */
class PlannedGoAroundSpec {

    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ZERO

    // ── Inline synthetic world (mirrors PerTypeCircuitSpec.kt's pattern) ───
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

    private fun trainedGaMission(): PilotMission {
        // Trained-GA + FullStop — minimum legal outcomes list ending in FullStop.
        // Two outcomes: GA on circuit 1 (the trained fork), then FullStop on
        // circuit 2 (the recovery / terminal landing).
        val goal = HighLevelGoal.CircuitTraining(
            outcomes = listOf(CircuitOutcome.GoAround, CircuitOutcome.FullStop),
        )
        val root = planMission(goal)
        return PilotMission(
            goal = goal,
            root = root,
            stepEnteredAt = now0,
            navigationMode = Some(NavigationMode.Circuit(RWY_ID, CKT_ID)),
            activeRunway = Some(
                RunwayAssignment(RWY_ID, RunwayAssignmentSource.Radio.TaxiClearance),
            ),
        )
    }

    private fun aircraftAt(
        phase: PilotPhase,
        altitudeM: Double,
        route: PilotRoute = PilotRoute.None,
        positionPoint: PointId = THRESHOLD,
        mission: PilotMission,
    ): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = Position(0.0, 0.0),
        positionPoint = positionPoint,
        altitudeM = altitudeM,
        phase = phase,
        route = route,
        pilotMission = mission,
    )

    /**
     * Drive a trained-GA mission forward to the FLY_FINAL_TO_SHORT_FINAL
     * step by mechanically marking the prior steps complete. Avoids
     * needing a kinematic harness for steps that only matter as
     * preconditions. Real-world: this is the state the pilot reaches
     * after flying downwind, base, and most of final.
     */
    private fun missionAtFinalToShortFinalStep(): PilotMission {
        var mission = trainedGaMission()
        // Walk forward through each prior step in the trained-GA compound
        // until FLY_FINAL_TO_SHORT_FINAL is the active leaf. The compiler-
        // built tree is: groundDepartureTask + plannedGoAroundCircuitTask +
        // circuitTask + groundArrivalTask. We need every primitive prior
        // to FLY_FINAL_TO_SHORT_FINAL marked complete.
        val priorSteps = listOf(
            // Ground departure
            MissionStep.REQUEST_TAXI, MissionStep.TAXI_TO_HOLDING,
            MissionStep.RUN_UP_CHECKS, MissionStep.REPORT_READY,
            MissionStep.AWAIT_LINE_UP, MissionStep.AWAIT_TAKEOFF_CLEARANCE,
            // Trained-GA circuit pattern up to (not including) FLY_FINAL_TO_SHORT_FINAL
            MissionStep.FLY_DEPARTURE, MissionStep.FLY_DOWNWIND,
            MissionStep.REPORT_DOWNWIND, MissionStep.AWAIT_SEQUENCING,
            MissionStep.FLY_BASE, MissionStep.REPORT_BASE,
        )
        priorSteps.forEach { step ->
            mission = mission.copy(root = mission.root.markComplete(step))
        }
        assertEquals(
            MissionStep.FLY_FINAL_TO_SHORT_FINAL,
            mission.currentTask?.step,
            "test-fixture sanity: mission must be at FLY_FINAL_TO_SHORT_FINAL",
        )
        return mission
    }

    // ── Test 1: Two-tick sequence ──────────────────────────────────────────

    @Test
    fun `Tick A — FLY_FINAL_TO_SHORT_FINAL completion at decision altitude clears route + transmits GoingAround`() {
        // Pilot at decision altitude on Final, FLY_FINAL_TO_SHORT_FINAL active,
        // no airborne route (route value irrelevant here — DefaultPilot's
        // onAirborneLeg with no route returns idle airborne intent).
        val mission = missionAtFinalToShortFinalStep()
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        val aircraft = aircraftAt(
            phase = PilotPhase.Final,
            altitudeM = 50.0, // <= DECISION_ALTITUDE_M (100m)
            mission = mission,
        )
        val output = pilotDecide(
            PilotInput(aircraft = aircraft, worldIndex = worldIndex, world = world, now = now0),
        ).getOrElse { fail("pilotDecide failed: $it") }

        // Tick A intent invariants — phase MUST be Final (NOT Climbing) so
        // Tick B's Circuit-mode special-case fires; route MUST be None so
        // the bootstrap path that builds a normal pattern is skipped.
        assertEquals(
            PilotPhase.Final,
            output.intent.phase,
            "Tick A: phase=Final required for Tick B's Circuit-mode special-case predicate",
        )
        assertEquals(
            PilotRoute.None,
            output.intent.route,
            "Tick A: route=PilotRoute.None invalidates the kinematic route so Tick B builds the GA path",
        )
        // Cognitive layer should emit Report(GoingAround) on the GOING_AROUND
        // step's first-tick (advanced from FLY_FINAL_TO_SHORT_FINAL).
        assertTrue(
            output.transmissions.any { it is Report && it.events.any { e -> e is ReportEvent.GoingAround } },
            "Tick A: Report(GoingAround) per CAP 413 §4.67",
        )
        // Mission state delta: hasClearance cleared (resetForGoAround applied).
        val newMission = output.updatedMission ?: fail("updatedMission must be non-null")
        assertEquals(
            false,
            newMission.hasClearance,
            "Tick A: resetForGoAround clears hasClearance",
        )
    }

    @Test
    fun `Tick B — next tick after Report(GoingAround) builds GA route from CircuitProcedure_goAroundPath`() {
        // Set up Tick B's input directly: the GOING_AROUND step has just
        // completed via the cognitive transmission update, so the active
        // step in the next outcome's compound is FLY_DEPARTURE. Aircraft
        // state carries Tick A's intent: phase=Final, route=None.
        // Marking-complete walk: every prior step in the trained-GA outcome
        // (including the GA's GOING_AROUND primitive) is complete.
        var mission = trainedGaMission()
        val tickAndPriorSteps = listOf(
            // Ground departure
            MissionStep.REQUEST_TAXI, MissionStep.TAXI_TO_HOLDING,
            MissionStep.RUN_UP_CHECKS, MissionStep.REPORT_READY,
            MissionStep.AWAIT_LINE_UP, MissionStep.AWAIT_TAKEOFF_CLEARANCE,
            // Trained-GA pattern (all complete)
            MissionStep.FLY_DEPARTURE, MissionStep.FLY_DOWNWIND,
            MissionStep.REPORT_DOWNWIND, MissionStep.AWAIT_SEQUENCING,
            MissionStep.FLY_BASE, MissionStep.REPORT_BASE,
            MissionStep.FLY_FINAL_TO_SHORT_FINAL, MissionStep.GOING_AROUND,
        )
        tickAndPriorSteps.forEach { step ->
            mission = mission.copy(root = mission.root.markComplete(step))
        }
        // After GOING_AROUND completes, the next outcome's compound becomes
        // active and its FLY_DEPARTURE primitive is the active leaf.
        assertEquals(
            MissionStep.FLY_DEPARTURE,
            mission.currentTask?.step,
            "test-fixture sanity: post-GA mission must be at the next outcome's FLY_DEPARTURE",
        )
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        // Tick B's aircraft state: phase=Final + route=None (carried from
        // Tick A's intent applied by the kinematic engine).
        val aircraft = aircraftAt(
            phase = PilotPhase.Final,
            altitudeM = 100.0,
            route = PilotRoute.None,
            positionPoint = THRESHOLD,
            mission = mission,
        )
        val output = pilotDecide(
            PilotInput(aircraft = aircraft, worldIndex = worldIndex, world = world, now = now0),
        ).getOrElse { fail("pilotDecide failed: $it") }

        assertEquals(
            PilotPhase.Climbing,
            output.intent.phase,
            "Tick B: phase=Climbing — pilot is climbing out via the GA path per CAP 413 §4.66",
        )
        // Route is built from CircuitProcedure.goAroundPath. The synthetic
        // world's go-around path is `Path(THRESHOLD, UPWIND_END)`, so the
        // route's first waypoint after THRESHOLD-filter is UPWIND_END.
        val airborne = output.intent.route as? PilotRoute.Airborne
            ?: fail("Tick B: intent.route must be PilotRoute.Airborne (got ${output.intent.route})")
        assertEquals(
            UPWIND_END,
            airborne.waypoints.head,
            "Tick B: route's first waypoint must be the GA path's published target — proves " +
                "the planner used buildGoAroundRoute, NOT a normal circuit pattern",
        )
        // Pattern altitude (305m for C172) — sanity that the GA route uses
        // the climb-out altitude target, not an approach descent.
        assertEquals(
            AircraftType.C172.circuitPattern.altitudeAglM,
            airborne.targetAltitudeM,
            "Tick B: GA route targets the C172 pattern altitude (305 m AGL per POH §4)",
        )
    }

    // ── Test 2: Circuit-mode discriminator regression ──────────────────────

    @Test
    fun `ordinary FullStop circuit FLY_DEPARTURE on the ground does NOT hit the trained-GA special-case`() {
        // Mission: ordinary single-FullStop circuit (NOT trained GA).
        val goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop))
        val mission = PilotMission(
            goal = goal,
            root = planMission(goal),
            stepEnteredAt = now0,
            navigationMode = Some(NavigationMode.Circuit(RWY_ID, CKT_ID)),
            activeRunway = Some(RunwayAssignment(RWY_ID, RunwayAssignmentSource.Radio.TaxiClearance)),
        )
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        // Walk to FLY_DEPARTURE, on the ground in TakeoffRoll phase (the
        // normal lift-off entry point) — this is what an ordinary first
        // circuit looks like at FLY_DEPARTURE.
        var atDeparture = mission
        listOf(
            MissionStep.REQUEST_TAXI, MissionStep.TAXI_TO_HOLDING,
            MissionStep.RUN_UP_CHECKS, MissionStep.REPORT_READY,
            MissionStep.AWAIT_LINE_UP, MissionStep.AWAIT_TAKEOFF_CLEARANCE,
        ).forEach { step ->
            atDeparture = atDeparture.copy(root = atDeparture.root.markComplete(step))
        }
        assertEquals(
            MissionStep.FLY_DEPARTURE,
            atDeparture.currentTask?.step,
            "test-fixture sanity: mission must be at FLY_DEPARTURE",
        )
        val aircraft = aircraftAt(
            phase = PilotPhase.TakeoffRoll, // on-ground takeoff roll, NOT Final
            altitudeM = 0.0,
            mission = atDeparture,
            // TakeoffRoll requires an Airborne route for DefaultPilot to compute
            // — supply a minimal one that the planner will replace.
            route = PilotRoute.None,
        )
        val output = pilotDecide(
            PilotInput(aircraft = aircraft, worldIndex = worldIndex, world = world, now = now0),
        ).getOrElse { fail("pilotDecide failed: $it") }

        // Discriminator pin: ordinary FLY_DEPARTURE on TakeoffRoll must NOT
        // produce a GA-route. The GA-path is `Path(THRESHOLD, UPWIND_END)`,
        // so a GA-route's head waypoint is UPWIND_END. An ordinary climb-out
        // route (built by `planCircuitDeparture` → `buildCircuitPatternRoute`)
        // starts at the runway's departure end (DEP_END) and traverses the
        // full circuit. The trained-GA Tick B special-case is gated on
        // `aircraft.phase is Final`, which fails here (phase is TakeoffRoll),
        // so the GA-path must NOT appear.
        val airborne = output.intent.route as? PilotRoute.Airborne
            ?: fail("ordinary FullStop FLY_DEPARTURE must produce an airborne route (got ${output.intent.route})")
        assertNotEquals(
            UPWIND_END,
            airborne.waypoints.head,
            "Discriminator regression: ordinary FullStop FLY_DEPARTURE on TakeoffRoll " +
                "must NOT hit the trained-GA Circuit-mode special-case (got GA-path route head=UPWIND_END)",
        )
        // Strongest pin: the route built is the normal circuit pattern,
        // whose head is DEP_END (the runway's departure end).
        assertEquals(
            DEP_END,
            airborne.waypoints.head,
            "Ordinary FullStop FLY_DEPARTURE on TakeoffRoll builds the normal circuit-pattern " +
                "route via planCircuitDeparture → buildCircuitPatternRoute (head=DEP_END)",
        )
    }

    // ── Test 3: ClearedToLand does NOT advance FLY_FINAL_TO_SHORT_FINAL ────

    @Test
    fun `ClearedToLand sets hasClearance but does NOT advance FLY_FINAL_TO_SHORT_FINAL`() {
        val mission = missionAtFinalToShortFinalStep()
        // Pilot well above decision altitude (above the gate, so altitude
        // predicate is NOT satisfied — only ClearedToLand could spuriously
        // advance the step here).
        val worldIndex = WorldIndex(
            holdingPointsByRunway = emptyMap(),
        )
        // Process ClearedToLand instruction.
        val updated = processInstruction(
            instruction = ClearedToLand(target = ac, runway = RWY_ID),
            mission = mission,
            now = now0,
            worldIndex = worldIndex,
        )
        // Step is unchanged — ClearedToLand's `handleLandingClearance` step
        // list deliberately omits FLY_FINAL_TO_SHORT_FINAL (per the audit
        // pin in the task spec; see the KDoc on `handleLandingClearance`).
        assertEquals(
            MissionStep.FLY_FINAL_TO_SHORT_FINAL,
            updated.currentTask?.step,
            "Trained-GA short-final descent step is altitude-gated, NOT clearance-gated — " +
                "ClearedToLand must NOT skip the trained-GA fork",
        )
        // hasClearance is set: the controller's instruction was respected;
        // only the step-completion decoupling is the audit pin.
        assertEquals(
            true,
            updated.hasClearance,
            "ClearedToLand still sets hasClearance — only the step-completion arm is decoupled",
        )
    }

    // ── Test 4: Trained-GA REPORT_DOWNWIND carries CircuitIntent.FULL_STOP ──

    @Test
    fun `trained-GA REPORT_DOWNWIND carries CircuitIntent_FULL_STOP per pass-9 finding hash 4`() {
        // Walk a trained-GA mission forward to REPORT_DOWNWIND.
        var mission = trainedGaMission()
        listOf(
            MissionStep.REQUEST_TAXI, MissionStep.TAXI_TO_HOLDING,
            MissionStep.RUN_UP_CHECKS, MissionStep.REPORT_READY,
            MissionStep.AWAIT_LINE_UP, MissionStep.AWAIT_TAKEOFF_CLEARANCE,
            MissionStep.FLY_DEPARTURE, MissionStep.FLY_DOWNWIND,
        ).forEach { step ->
            mission = mission.copy(root = mission.root.markComplete(step))
        }
        assertEquals(
            MissionStep.REPORT_DOWNWIND,
            mission.currentTask?.step,
            "test-fixture sanity: mission must be at REPORT_DOWNWIND",
        )
        // Drive cognitive decide; the REPORT_DOWNWIND first-tick transmission
        // should fire with CircuitIntent.FULL_STOP (NOT TOUCH_AND_GO, NOT
        // null). Rationale: the trained-GA outer compound uses TaskName.Circuit,
        // and `deriveCircuitIntent` maps TaskName.Circuit → FULL_STOP. The
        // pilot announces a full-stop intent at downwind; the GA at
        // short-final is the instructor's pre-arranged training exercise,
        // private to the mission tree until CAP 413 §4.67's announcement at
        // decision altitude.
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        val aircraft = aircraftAt(
            phase = PilotPhase.Downwind,
            altitudeM = AircraftType.C172.circuitPattern.altitudeAglM,
            mission = mission,
        )
        val decision = pilotCognitiveDecide(
            aircraft = aircraft,
            mission = mission,
            worldIndex = worldIndex,
            now = now0,
        )
        val downwindReport = decision.transmissions
            .filterIsInstance<Report>()
            .firstOrNull { it.events.any { e -> e is ReportEvent.Downwind } }
            ?: fail("Trained-GA mission at REPORT_DOWNWIND must emit Report(Downwind)")
        val downwindEvent = downwindReport.events.filterIsInstance<ReportEvent.Downwind>().single()
        assertEquals(
            CircuitIntent.FULL_STOP,
            downwindEvent.circuitIntent,
            "Trained-GA Report(Downwind) carries FULL_STOP per pass-9 plan-review finding #4 " +
                "(NOT TOUCH_AND_GO, NOT null). Pilot announces landing intent; the trained " +
                "go-around at short-final is private until CAP 413 §4.67's announcement.",
        )
    }
}
