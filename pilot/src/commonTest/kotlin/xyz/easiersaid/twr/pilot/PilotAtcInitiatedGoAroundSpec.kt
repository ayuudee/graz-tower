package xyz.easiersaid.twr.pilot

import arrow.core.None
import arrow.core.Option
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
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.CircuitDirection
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * fn-12.2 — pilot-side unit pins for the ATC-issued reactive go-around
 * path (Circuit-mode, post-`Instruction.GoAround` from controller).
 *
 * Architecture under test:
 *  - `PilotMission.pendingAtcGoAroundFrom` flag — set by `handleGoAround`
 *    before the mission-tree rewrite when the original `currentTask.step`
 *    is in the on-final eligible set `{FLY_FINAL, REPORT_FINAL,
 *    AWAIT_LANDING_CLEARANCE, LAND}`. Otherwise stays [None].
 *  - `pilotDecide` recognition arm — reads the flag (post-cognitive) and
 *    fires `applyAtcInitiatedGoAround` when discriminator passes
 *    (effective Circuit-mode + phase=Final). Otherwise defensively
 *    clears the flag.
 *  - `applyAtcInitiatedGoAround` — Tick A intent (`route=PilotRoute.None,
 *    phase=Final`), mirroring fn-11.1's `applyPlannedGoAround` shape.
 *    Tick B is FREE — the existing `isCircuitTrainedGoAroundTickB`
 *    predicate fires for ATC-reactive too (same kinematic signature).
 *
 * **Effective-mode discriminator choice**: option (a) from the task spec
 * — `isEffectiveCircuitMode(mission, world)` reuses `deriveNavigationMode`
 * the same way `planRoute` does. The stored `mission.navigationMode` is
 * often [None] for normal circuit-training missions because `createMission`
 * defaults it to [None] and `planRoute` derives `Circuit` locally. Gating
 * on the stored field alone would silently fail; the helper covers both
 * the stored case and the derived case.
 *
 * Doctrine refs:
 *  - ICAO Doc 4444 §7.4.1.4.1(c) — controller's runway-incursion /
 *    obstruction-driven GoAround instruction.
 *  - CAP 413 §4.64 — pilot compliance with ATC GoAround (Ed 24 — formerly
 *    §4.65 in Ed 23, renumbered per fn-17.1).
 *  - CAP 413 §4.65/§4.66 — climb runway-heading, re-enter circuit
 *    (Ed 24 — formerly §4.66/§4.67 in Ed 23, renumbered per fn-17.1).
 */
class PilotAtcInitiatedGoAroundSpec {

    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ZERO

    // ── Inline synthetic world (mirrors PlannedGoAroundSpec) ──────────────
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

    /**
     * Single-FullStop circuit-training mission — the **normal LOWG case**
     * where `navigationMode` is [None] and `planRoute` derives `Circuit`
     * via [deriveNavigationMode]. Pins that the ATC-reactive recognition
     * does NOT depend on `navigationMode` being explicitly populated.
     */
    private fun singleFullStopMission(navigationMode: Option<NavigationMode> = None): PilotMission {
        val goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop))
        val root = planMission(goal)
        return PilotMission(
            goal = goal,
            root = root,
            stepEnteredAt = now0,
            navigationMode = navigationMode,
            activeRunway = Some(
                RunwayAssignment(RWY_ID, RunwayAssignmentSource.Radio.TaxiClearance),
            ),
        )
    }

    /**
     * Walk a single-FullStop mission forward to a specified on-final step.
     * The active outcome's `circuitTask` carries:
     *   FLY_DEPARTURE, FLY_DOWNWIND, REPORT_DOWNWIND, AWAIT_SEQUENCING,
     *   FLY_BASE, REPORT_BASE, FLY_FINAL, REPORT_FINAL,
     *   AWAIT_LANDING_CLEARANCE, LAND.
     * We mark every primitive STRICTLY BEFORE [target] complete; the target
     * is the active leaf afterwards.
     */
    private fun missionAtStep(target: MissionStep): PilotMission {
        var mission = singleFullStopMission()
        // Ground-departure prelude.
        val groundSteps = listOf(
            MissionStep.REQUEST_TAXI, MissionStep.TAXI_TO_HOLDING,
            MissionStep.RUN_UP_CHECKS, MissionStep.REPORT_READY,
            MissionStep.AWAIT_LINE_UP, MissionStep.AWAIT_TAKEOFF_CLEARANCE,
        )
        // Circuit pattern up to but not including [target].
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
        assertEquals(
            target,
            mission.currentTask?.step,
            "test-fixture sanity: mission must be at $target",
        )
        return mission
    }

    private fun aircraftAt(
        phase: PilotPhase,
        altitudeM: Double = 50.0,
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

    private fun emptyWorldIndex(): WorldIndex = WorldIndex(holdingPointsByRunway = emptyMap())

    // ────────────────────────────────────────────────────────────────────
    // Group A — `handleGoAround` flag-set behaviour over the eligible set
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun `processInstruction(GoAround) at FLY_FINAL stamps pendingAtcGoAroundFrom = Some(FLY_FINAL)`() {
        val mission = missionAtStep(MissionStep.FLY_FINAL)
        val updated = processInstruction(
            instruction = GoAround(target = ac),
            mission = mission,
            now = now0,
            worldIndex = emptyWorldIndex(),
        )
        assertEquals(
            Some(MissionStep.FLY_FINAL),
            updated.pendingAtcGoAroundFrom,
            "handleGoAround must capture the pre-rewrite step (FLY_FINAL) onto the flag",
        )
    }

    @Test
    fun `processInstruction(GoAround) at REPORT_FINAL stamps pendingAtcGoAroundFrom = Some(REPORT_FINAL)`() {
        val mission = missionAtStep(MissionStep.REPORT_FINAL)
        val updated = processInstruction(
            instruction = GoAround(target = ac),
            mission = mission,
            now = now0,
            worldIndex = emptyWorldIndex(),
        )
        assertEquals(Some(MissionStep.REPORT_FINAL), updated.pendingAtcGoAroundFrom)
    }

    @Test
    fun `processInstruction(GoAround) at AWAIT_LANDING_CLEARANCE stamps pendingAtcGoAroundFrom = Some(AWAIT_LANDING_CLEARANCE)`() {
        val mission = missionAtStep(MissionStep.AWAIT_LANDING_CLEARANCE)
        val updated = processInstruction(
            instruction = GoAround(target = ac),
            mission = mission,
            now = now0,
            worldIndex = emptyWorldIndex(),
        )
        assertEquals(Some(MissionStep.AWAIT_LANDING_CLEARANCE), updated.pendingAtcGoAroundFrom)
    }

    @Test
    fun `processInstruction(GoAround) at LAND stamps pendingAtcGoAroundFrom = Some(LAND)`() {
        // LAND is the post-`handleLandingClearance` step — `ClearedToLand`
        // marks AWAIT_LANDING_CLEARANCE complete, advancing currentTask.step
        // to LAND. This pin proves the post-clearance obstruction-GA case
        // (the most-likely fn-12 scenario shape) is captured.
        val mission = missionAtStep(MissionStep.LAND)
        val updated = processInstruction(
            instruction = GoAround(target = ac),
            mission = mission,
            now = now0,
            worldIndex = emptyWorldIndex(),
        )
        assertEquals(
            Some(MissionStep.LAND),
            updated.pendingAtcGoAroundFrom,
            "post-`handleLandingClearance` GoAround must capture step=LAND onto the flag — " +
                "the most-likely fn-12 scenario is post-clearance obstruction-GA",
        )
    }

    @Test
    fun `processInstruction(GoAround) at FLY_DOWNWIND leaves flag = None (non-on-final step)`() {
        val mission = missionAtStep(MissionStep.FLY_DOWNWIND)
        val updated = processInstruction(
            instruction = GoAround(target = ac),
            mission = mission,
            now = now0,
            worldIndex = emptyWorldIndex(),
        )
        assertEquals(
            None,
            updated.pendingAtcGoAroundFrom,
            "non-on-final GoAround must NOT set the flag — Visual-mode reactive special-case " +
                "handles the mid-circuit case via a separate path",
        )
    }

    // ────────────────────────────────────────────────────────────────────
    // Group B — Tick A: pilotDecide consumes flag, emits intent override
    // ────────────────────────────────────────────────────────────────────

    /**
     * **Dedicated `currentTask.step == LAND` pin** (per task spec R9c).
     *
     * Sequence: seed mission post-`handleLandingClearance` (step=LAND),
     * phase=Final, route airborne-final; run `processInstruction(GoAround)
     * → pilotDecide`; assert Tick A fires (route=None, phase=Final), flag
     * cleared, then run a follow-up `pilotDecide` to verify Tick B builds
     * the GA route via the reused planner.
     */
    @Test
    fun `Tick A — post-clearance obstruction GA at LAND fires applyAtcInitiatedGoAround`() {
        val mission = missionAtStep(MissionStep.LAND).copy(hasClearance = true)
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()

        val afterInstruction = processInstruction(
            instruction = GoAround(target = ac),
            mission = mission,
            now = now0,
            worldIndex = worldIndex,
        )
        assertEquals(
            Some(MissionStep.LAND),
            afterInstruction.pendingAtcGoAroundFrom,
            "fixture sanity: post-instruction flag is Some(LAND)",
        )
        // Aircraft state on Tick A: airborne final, phase=Final.
        val airborneFinal = PilotRoute.Airborne(
            waypoints = arrow.core.NonEmptyList(THRESHOLD, emptyList()),
            targetAltitudeM = 0.0,
            arrivalPhase = PilotPhase.LandingRoll,
        )
        val aircraft = aircraftAt(
            phase = PilotPhase.Final,
            altitudeM = 50.0,
            route = airborneFinal,
            mission = afterInstruction,
        )
        val output = pilotDecide(
            PilotInput(aircraft = aircraft, worldIndex = worldIndex, world = world, now = now0),
        ).getOrElse { fail("pilotDecide failed: $it") }

        // Tick A intent invariants — same shape as fn-11.1 trained-GA.
        assertEquals(
            PilotPhase.Final,
            output.intent.phase,
            "Tick A: phase=Final retained so Tick B's isCircuitTrainedGoAroundTickB predicate fires",
        )
        assertEquals(
            PilotRoute.None,
            output.intent.route,
            "Tick A: route=None invalidates the kinematic route so the pilot does not " +
                "continue toward the (now-obstructed) threshold",
        )
        // Mission delta: flag cleared (consume on fire).
        val newMission = output.updatedMission ?: fail("updatedMission must be non-null")
        assertEquals(
            None,
            newMission.pendingAtcGoAroundFrom,
            "Tick A: applyAtcInitiatedGoAround must clear the flag (consume on fire)",
        )
        // Sanity: handleGoAround already called resetForGoAround; the clean
        // mission state must be visible (hasClearance reset by handleGoAround).
        assertEquals(
            false,
            newMission.hasClearance,
            "Tick A pre-state from handleGoAround: hasClearance must be cleared by " +
                "resetForGoAround (called inside handleGoAround, before the flag stamp)",
        )
    }

    @Test
    fun `Tick A at FLY_FINAL — flag-set + Circuit + phase=Final fires Tick A`() {
        val mission = missionAtStep(MissionStep.FLY_FINAL)
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()

        val afterInstruction = processInstruction(
            instruction = GoAround(target = ac),
            mission = mission,
            now = now0,
            worldIndex = worldIndex,
        )
        val aircraft = aircraftAt(
            phase = PilotPhase.Final,
            altitudeM = 80.0,
            route = PilotRoute.Airborne(
                waypoints = arrow.core.NonEmptyList(THRESHOLD, emptyList()),
                targetAltitudeM = 0.0,
                arrivalPhase = PilotPhase.LandingRoll,
            ),
            mission = afterInstruction,
        )
        val output = pilotDecide(
            PilotInput(aircraft = aircraft, worldIndex = worldIndex, world = world, now = now0),
        ).getOrElse { fail("pilotDecide failed: $it") }

        assertEquals(PilotPhase.Final, output.intent.phase)
        assertEquals(PilotRoute.None, output.intent.route)
        val newMission = output.updatedMission ?: fail("updatedMission must be non-null")
        assertEquals(None, newMission.pendingAtcGoAroundFrom)
    }

    // ────────────────────────────────────────────────────────────────────
    // Group C — Tick B: reused predicate + planner builds GA route
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun `Tick B — after Tick A clears route + pins phase=Final, planRoute builds GA path`() {
        // Tick B's input: same mission as post-Tick-A (after handleGoAround
        // and applyAtcInitiatedGoAround). Active step is GOING_AROUND
        // (REPORTED). Cognitive layer's `stepTransmission` will emit the
        // GA report on this tick or the next; the route-planner-level pin
        // is what we exercise here. Specifically, advance past GOING_AROUND
        // to FLY_DEPARTURE so `isCircuitTrainedGoAroundTickB`'s step check
        // matches.
        var mission = missionAtStep(MissionStep.LAND).copy(hasClearance = true)
        // Apply the GA instruction (handleGoAround rewrites tree;
        // active step becomes GOING_AROUND).
        mission = processInstruction(
            instruction = GoAround(target = ac),
            mission = mission,
            now = now0,
            worldIndex = emptyWorldIndex(),
        )
        // Apply Tick A explicitly to clear the flag (mirrors what
        // pilotDecide does on the GA-receipt tick).
        mission = mission.copy(pendingAtcGoAroundFrom = None)
        // Advance past GOING_AROUND (REPORTED) to the next compound's
        // FLY_DEPARTURE — the trained-GA Tick B predicate gates on
        // `step == FLY_DEPARTURE`.
        mission = mission.copy(root = mission.root.markComplete(MissionStep.GOING_AROUND))
        assertEquals(
            MissionStep.FLY_DEPARTURE,
            mission.currentTask?.step,
            "fixture sanity: post-GOING_AROUND step is FLY_DEPARTURE (recovery circuit's first step)",
        )
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        // Tick B aircraft state: phase=Final + route=None (carried from Tick A).
        val aircraft = aircraftAt(
            phase = PilotPhase.Final,
            altitudeM = 100.0,
            route = PilotRoute.None,
            mission = mission,
        )
        val output = pilotDecide(
            PilotInput(aircraft = aircraft, worldIndex = worldIndex, world = world, now = now0),
        ).getOrElse { fail("pilotDecide failed: $it") }

        assertEquals(
            PilotPhase.Climbing,
            output.intent.phase,
            "Tick B: phase=Climbing — planCircuitTrainedGoAround sets it",
        )
        val airborne = output.intent.route as? PilotRoute.Airborne
            ?: fail("Tick B: intent.route must be PilotRoute.Airborne (got ${output.intent.route})")
        // GA path is `Path(THRESHOLD, UPWIND_END)`; route head after
        // THRESHOLD-filter is UPWIND_END — proves the reused planner used
        // `buildGoAroundRoute`, NOT a normal circuit pattern.
        assertEquals(
            UPWIND_END,
            airborne.waypoints.head,
            "Tick B: GA route must use CircuitProcedure.goAroundPath (head=UPWIND_END), " +
                "proving reuse of fn-11.1's planCircuitTrainedGoAround planner — zero new route code",
        )
    }

    // ────────────────────────────────────────────────────────────────────
    // Group D — Recognition discriminator matrix
    // ────────────────────────────────────────────────────────────────────

    /**
     * Positive case: `mission.navigationMode = None` + circuit-training
     * + on-final flag → MUST FIRE. This is the **normal LOWG case** —
     * `createMission` defaults `navigationMode` to [None] and `planRoute`
     * derives `Circuit` locally. The recognition must use the same
     * derivation, not gate on the stored field.
     */
    @Test
    fun `discriminator — navigationMode=None + circuit-training + on-final flag fires Tick A`() {
        // singleFullStopMission already defaults navigationMode = None.
        val mission = missionAtStep(MissionStep.FLY_FINAL)
        assertEquals(
            None,
            mission.navigationMode,
            "fixture sanity: mission.navigationMode is None (the normal LOWG case)",
        )
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        val afterInstruction = processInstruction(
            instruction = GoAround(target = ac),
            mission = mission,
            now = now0,
            worldIndex = worldIndex,
        )
        val aircraft = aircraftAt(
            phase = PilotPhase.Final,
            altitudeM = 80.0,
            route = PilotRoute.Airborne(
                waypoints = arrow.core.NonEmptyList(THRESHOLD, emptyList()),
                targetAltitudeM = 0.0,
                arrivalPhase = PilotPhase.LandingRoll,
            ),
            mission = afterInstruction,
        )
        val output = pilotDecide(
            PilotInput(aircraft = aircraft, worldIndex = worldIndex, world = world, now = now0),
        ).getOrElse { fail("pilotDecide failed: $it") }

        // Discriminator MUST pass via deriveNavigationMode — Tick A fires.
        assertEquals(
            PilotRoute.None,
            output.intent.route,
            "navigationMode=None + Circuit-derivable via deriveNavigationMode + on-final " +
                "flag MUST fire Tick A. Otherwise the normal LOWG case would silently fail.",
        )
        assertEquals(PilotPhase.Final, output.intent.phase)
    }

    /**
     * **Flag-clear-on-discriminator-fail** (per task spec R9b two-layer
     * defense). Seed a mission with `pendingAtcGoAroundFrom = Some(...)`
     * AND a non-Final aircraft phase. The recognition arm's discriminator
     * fails, but the flag MUST still be cleared — otherwise it could
     * fire later when the aircraft happens back into phase=Final.
     */
    @Test
    fun `discriminator-fail — non-Final phase clears the flag without firing Tick A`() {
        // Build a mission with the flag set but in a state where the
        // recognition discriminator will fail (phase != Final).
        val baseMission = missionAtStep(MissionStep.FLY_FINAL)
        // After processing GoAround, currentTask.step is GOING_AROUND;
        // we pin the flag-clear behaviour by using the post-instruction
        // mission directly.
        val afterInstruction = processInstruction(
            instruction = GoAround(target = ac),
            mission = baseMission,
            now = now0,
            worldIndex = emptyWorldIndex(),
        )
        assertEquals(Some(MissionStep.FLY_FINAL), afterInstruction.pendingAtcGoAroundFrom)
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        // Aircraft on the ground (LandingRoll) — phase != Final → discriminator fails.
        val aircraft = aircraftAt(
            phase = PilotPhase.LandingRoll,
            altitudeM = 0.0,
            route = PilotRoute.None,
            mission = afterInstruction,
        )
        val output = pilotDecide(
            PilotInput(aircraft = aircraft, worldIndex = worldIndex, world = world, now = now0),
        ).getOrElse { fail("pilotDecide failed: $it") }

        // Tick A did NOT fire: the intent should NOT be the Tick A route=None
        // override (other code paths may also produce route=None, so the
        // strongest pin is the flag-clear behaviour itself).
        val newMission = output.updatedMission ?: fail("updatedMission must be non-null")
        assertEquals(
            None,
            newMission.pendingAtcGoAroundFrom,
            "discriminator-fail: flag MUST be cleared even though apply did not fire — " +
                "two-layer defense prevents the flag lingering and firing later",
        )
        // Phase=LandingRoll preserved (not pinned to Final).
        assertNotEquals(
            PilotPhase.Final,
            output.intent.phase,
            "Tick A apply did NOT fire — phase override absent",
        )
    }

    /**
     * Discriminator-fail: flag's value outside the eligible step set
     * (defensive). Manually seed the flag with `FLY_DOWNWIND` (which
     * `handleGoAround` would never set) to verify the predicate's
     * defensive eligibility check.
     */
    @Test
    fun `discriminator-fail — flag value outside eligible set clears flag without firing`() {
        var mission = missionAtStep(MissionStep.FLY_FINAL)
        // Manually construct an inconsistent flag — `handleGoAround` would
        // never produce this, but the predicate must defend against the
        // case anyway.
        mission = mission.copy(pendingAtcGoAroundFrom = Some(MissionStep.FLY_DOWNWIND))
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        val aircraft = aircraftAt(
            phase = PilotPhase.Final,
            altitudeM = 80.0,
            route = PilotRoute.Airborne(
                waypoints = arrow.core.NonEmptyList(THRESHOLD, emptyList()),
                targetAltitudeM = 0.0,
                arrivalPhase = PilotPhase.LandingRoll,
            ),
            mission = mission,
        )
        val output = pilotDecide(
            PilotInput(aircraft = aircraft, worldIndex = worldIndex, world = world, now = now0),
        ).getOrElse { fail("pilotDecide failed: $it") }

        val newMission = output.updatedMission ?: fail("updatedMission must be non-null")
        assertEquals(
            None,
            newMission.pendingAtcGoAroundFrom,
            "ineligible flag value: flag MUST be defensively cleared even though apply did not fire",
        )
    }

    /**
     * Sanity: flag = None + on-final + Final phase → recognition is a
     * complete no-op (nothing to clear, nothing to fire).
     */
    @Test
    fun `discriminator — flag=None is a no-op (existing path unchanged)`() {
        val mission = missionAtStep(MissionStep.FLY_FINAL) // flag defaults None
        assertEquals(None, mission.pendingAtcGoAroundFrom)
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        val aircraft = aircraftAt(
            phase = PilotPhase.Final,
            altitudeM = 200.0, // above DECISION_ALTITUDE_M, no self-init GA either
            route = PilotRoute.Airborne(
                waypoints = arrow.core.NonEmptyList(THRESHOLD, emptyList()),
                targetAltitudeM = 0.0,
                arrivalPhase = PilotPhase.LandingRoll,
            ),
            mission = mission,
        )
        val output = pilotDecide(
            PilotInput(aircraft = aircraft, worldIndex = worldIndex, world = world, now = now0),
        ).getOrElse { fail("pilotDecide failed: $it") }
        // Nothing about the route should change to None — flag was None.
        // The intent.route should still be the airborne final (or whatever
        // planRoute decides). The strongest pin is "flag stays None".
        val newMission = output.updatedMission ?: fail("updatedMission must be non-null")
        assertEquals(
            None,
            newMission.pendingAtcGoAroundFrom,
            "flag=None: recognition arm is a no-op",
        )
    }

    // ────────────────────────────────────────────────────────────────────
    // Group E — Mutual exclusivity with trained-GA + future-circuit safety
    // ────────────────────────────────────────────────────────────────────

    /**
     * Precedence: ATC-reactive wins over self-initiated when BOTH would
     * fire. Construct a pathological scenario where:
     *  - `pendingAtcGoAroundFrom = Some(REPORT_FINAL)` (ATC-reactive trigger)
     *  - aircraft at decision altitude, no clearance, on REPORT_FINAL
     *    (self-initiated trigger fires too)
     *
     * The ATC-reactive Tick A apply must fire (`route=None, phase=Final`),
     * NOT the self-initiated path (which would emit `phase=Climbing`).
     * This pins the precedence reordering: ATC-reactive recognition
     * runs BEFORE `derivePilotEvent`.
     */
    @Test
    fun `precedence — ATC-reactive wins over self-initiated when both would fire`() {
        // Mission walked to REPORT_FINAL (eligible step), no clearance.
        var mission = missionAtStep(MissionStep.REPORT_FINAL)
        // Manually set the flag (skip processInstruction to avoid mission
        // tree rewrite — we want self-init's REPORT_FINAL trigger to ALSO
        // be valid, so step must remain REPORT_FINAL).
        mission = mission.copy(pendingAtcGoAroundFrom = Some(MissionStep.REPORT_FINAL))
        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        // Aircraft at decision altitude (50m), phase=Final, no clearance —
        // self-init's `DecisionAltitudeWithoutClearance` fires here too.
        val aircraft = aircraftAt(
            phase = PilotPhase.Final,
            altitudeM = 50.0, // <= DECISION_ALTITUDE_M (100m) — self-init trigger ON
            route = PilotRoute.Airborne(
                waypoints = arrow.core.NonEmptyList(THRESHOLD, emptyList()),
                targetAltitudeM = 0.0,
                arrivalPhase = PilotPhase.LandingRoll,
            ),
            mission = mission,
        )
        val output = pilotDecide(
            PilotInput(aircraft = aircraft, worldIndex = worldIndex, world = world, now = now0),
        ).getOrElse { fail("pilotDecide failed: $it") }

        // ATC-reactive Tick A intent shape: phase=Final + route=None.
        // Self-initiated would emit phase=Climbing — discriminator pin.
        assertEquals(
            PilotPhase.Final,
            output.intent.phase,
            "Precedence: ATC-reactive (phase=Final) MUST win over self-initiated (phase=Climbing)",
        )
        assertEquals(
            PilotRoute.None,
            output.intent.route,
            "Precedence: ATC-reactive Tick A clears route (self-initiated keeps aircraft.route)",
        )
        // Flag consumed.
        val newMission = output.updatedMission ?: fail("updatedMission must be non-null")
        assertEquals(None, newMission.pendingAtcGoAroundFrom)
    }

    /**
     * Precedence: trained-GA wins over ATC-reactive when both somehow
     * apply (defensive — trained-GA's natural flow never sets the flag,
     * but mutual-exclusivity is a stated invariant).
     *
     * **Reset-sensitive sentinels** distinguish the two paths:
     *  - `applyPlannedGoAround` calls `mission.resetForGoAround(now)`,
     *    which clears `hasClearance`, `altitudeRestrictionM`, etc.
     *  - `applyAtcInitiatedGoAround` is INTENT-ONLY (does NOT call
     *    resetForGoAround) — sentinel fields would survive.
     *
     * Pin: seed sentinels, run pilotDecide, assert the trained-GA reset
     * actually fired. If the precedence regressed (ATC-reactive winning),
     * the sentinels would survive and the assertion would fail.
     */
    @Test
    fun `precedence — trained-GA wins over ATC-reactive (reset-sensitive sentinel pin)`() {
        // Walk a trained-GA mission to FLY_FINAL_TO_SHORT_FINAL — the
        // trained-GA Tick A trigger.
        val goal = HighLevelGoal.CircuitTraining(
            outcomes = listOf(CircuitOutcome.GoAround, CircuitOutcome.FullStop),
        )
        var mission = PilotMission(
            goal = goal,
            root = planMission(goal),
            stepEnteredAt = now0,
            navigationMode = Some(NavigationMode.Circuit(RWY_ID, CKT_ID)),
            activeRunway = Some(RunwayAssignment(RWY_ID, RunwayAssignmentSource.Radio.TaxiClearance)),
        )
        listOf(
            MissionStep.REQUEST_TAXI, MissionStep.TAXI_TO_HOLDING,
            MissionStep.RUN_UP_CHECKS, MissionStep.REPORT_READY,
            MissionStep.AWAIT_LINE_UP, MissionStep.AWAIT_TAKEOFF_CLEARANCE,
            MissionStep.FLY_DEPARTURE, MissionStep.FLY_DOWNWIND,
            MissionStep.REPORT_DOWNWIND, MissionStep.AWAIT_SEQUENCING,
            MissionStep.FLY_BASE, MissionStep.REPORT_BASE,
        ).forEach { step ->
            mission = mission.copy(root = mission.root.markComplete(step))
        }
        assertEquals(MissionStep.FLY_FINAL_TO_SHORT_FINAL, mission.currentTask?.step)
        // Reset-sensitive sentinels — applyPlannedGoAround clears these
        // via resetForGoAround; applyAtcInitiatedGoAround leaves them
        // untouched. Together they discriminate trained-GA from ATC.
        // Plus the stale ATC-flag (which trained-GA would normally never
        // have set). The defensive precedence pin: trained-GA must win.
        mission = mission.copy(
            pendingAtcGoAroundFrom = Some(MissionStep.FLY_FINAL),
            hasClearance = true,
            altitudeRestrictionM = Some(80.0),
            activeConstraints = setOf(ActiveConstraint.ExtendingDownwind),
        )

        val world = syntheticWorld()
        val worldIndex = world.buildWorldIndex()
        val aircraft = aircraftAt(
            phase = PilotPhase.Final,
            altitudeM = 50.0, // <= DECISION_ALTITUDE_M — triggers FLY_FINAL_TO_SHORT_FINAL completion
            mission = mission,
        )
        val output = pilotDecide(
            PilotInput(aircraft = aircraft, worldIndex = worldIndex, world = world, now = now0),
        ).getOrElse { fail("pilotDecide failed: $it") }

        // Trained-GA Tick A intent shape (same shape as ATC-reactive).
        assertEquals(PilotPhase.Final, output.intent.phase)
        assertEquals(PilotRoute.None, output.intent.route)
        val newMission = output.updatedMission ?: fail("updatedMission must be non-null")
        // ── Reset-sensitive sentinel pins ──
        // Trained-GA's applyPlannedGoAround calls resetForGoAround. If
        // ATC-reactive had won (precedence regression), these would still
        // be set (applyAtcInitiatedGoAround does NOT reset).
        assertEquals(
            false,
            newMission.hasClearance,
            "Trained-GA's applyPlannedGoAround calls resetForGoAround → hasClearance cleared. " +
                "ATC-reactive's applyAtcInitiatedGoAround would PRESERVE hasClearance. " +
                "If this assertion fails, ATC-reactive incorrectly won precedence over trained-GA.",
        )
        assertEquals(
            None,
            newMission.altitudeRestrictionM,
            "Trained-GA reset clears altitudeRestrictionM; ATC-reactive does NOT.",
        )
        assertTrue(
            newMission.activeConstraints.isEmpty(),
            "Trained-GA reset clears activeConstraints; ATC-reactive does NOT.",
        )
        // Single-cycle flag-clear invariant survives the precedence chain.
        assertEquals(
            None,
            newMission.pendingAtcGoAroundFrom,
            "Single-cycle flag-clear invariant: even when trained-GA wins, the ATC-flag " +
                "is cleared (post-fold reconciliation in pilotDecide)",
        )
    }

    /**
     * Discriminator-fail: genuinely non-Circuit mode (Visual). The flag
     * is set + aircraft on-final, but `mission.navigationMode = Some(Visual)`
     * — `isEffectiveCircuitMode` returns false (the stored field is
     * checked first), so Tick A must NOT fire. Flag is defensively cleared.
     *
     * This is the negative-discriminator pin parallel to the positive
     * `navigationMode=None + circuit-training tree-shape` pin above —
     * verifies the helper correctly distinguishes Visual from Circuit
     * even when both have an active runway and final-leg state.
     *
     * **Tested via `recognizeAtcInitiatedGoAround` directly** rather than
     * a full `pilotDecide` round-trip: a Circuit-training-tree-shape
     * mission with `navigationMode = Visual` is artificial (real missions
     * derive consistently), and `planRoute` legitimately fails on the
     * Visual-mode FINAL geometry of the synthetic test world. The
     * discriminator unit is what we need to pin here, not the full
     * end-to-end planning path.
     */
    @Test
    fun `discriminator-fail — non-Circuit mode (Visual) does NOT fire Tick A`() {
        // Mission has the on-final flag set but navigationMode = Visual.
        val mission = missionAtStep(MissionStep.FLY_FINAL).copy(
            navigationMode = Some(NavigationMode.Visual(RWY_ID, destination = null)),
            pendingAtcGoAroundFrom = Some(MissionStep.FLY_FINAL),
        )
        val world = syntheticWorld()
        val aircraft = aircraftAt(
            phase = PilotPhase.Final,
            altitudeM = 50.0,
            mission = mission,
        )
        val recognized = recognizeAtcInitiatedGoAround(aircraft, mission, world)
            ?: fail("recognize must return non-null when flag is Some")

        // Discriminator MUST reject Visual mode — intent stays null even
        // though the flag was Some.
        assertNull(
            recognized.intent,
            "Visual mode + on-final flag MUST NOT fire Tick A. Discriminator must reject " +
                "non-Circuit mode even with the flag set.",
        )
        // Event must be null when the discriminator rejected.
        assertNull(
            recognized.event,
            "Visual-mode discriminator-fail: typed event must NOT be constructed",
        )
        // Defensive flag-clear: flag is cleared even though apply did not fire.
        assertEquals(
            None,
            recognized.mission.pendingAtcGoAroundFrom,
            "Visual-mode discriminator-fail: flag MUST still be defensively cleared",
        )
    }

    /**
     * Companion to the Visual-mode pin: the typed event leaf is constructed
     * (and surfaced via `RecognizedAtcGoAround.event`) when the
     * discriminator passes. Verifies the leaf is wired (not dead API)
     * AND that future trace consumers can read it.
     */
    @Test
    fun `recognize — typed event AtcGoAroundOnFinal is constructed when discriminator passes`() {
        // Normal LOWG case (navigationMode = None, derived Circuit).
        val mission = missionAtStep(MissionStep.LAND).copy(
            pendingAtcGoAroundFrom = Some(MissionStep.LAND),
            hasClearance = true,
        )
        val world = syntheticWorld()
        val aircraft = aircraftAt(phase = PilotPhase.Final, altitudeM = 50.0, mission = mission)
        val recognized = recognizeAtcInitiatedGoAround(aircraft, mission, world)
            ?: fail("recognize must return non-null when flag is Some")

        // Discriminator passed — Tick A apply fired.
        assertNotNull(recognized.intent, "Tick A intent must be non-null when discriminator passes")
        assertEquals(PilotPhase.Final, recognized.intent!!.phase)
        assertEquals(PilotRoute.None, recognized.intent.route)
        // Typed event leaf surfaced for trace consumers.
        val event = recognized.event
            ?: fail("RecognizedAtcGoAround.event must be non-null when discriminator passes")
        assertEquals(ac, event.aircraft, "event carries aircraft id")
        assertEquals(
            MissionStep.LAND,
            event.originalStep,
            "event carries the pre-rewrite step the flag captured",
        )
    }

    /**
     * Mutual exclusivity: in trained-GA's natural flow, `processInstruction(GoAround)`
     * never runs (the GA is plan-driven from the static mission tree), so
     * the flag stays [None]. This pin verifies the structural exclusivity:
     * a trained-GA mission walked through Tick A's natural transition has
     * `pendingAtcGoAroundFrom == None`.
     */
    @Test
    fun `mutual-exclusivity — trained-GA natural flow leaves the flag None`() {
        val goal = HighLevelGoal.CircuitTraining(
            outcomes = listOf(CircuitOutcome.GoAround, CircuitOutcome.FullStop),
        )
        val mission = PilotMission(
            goal = goal,
            root = planMission(goal),
            stepEnteredAt = now0,
            navigationMode = Some(NavigationMode.Circuit(RWY_ID, CKT_ID)),
            activeRunway = Some(RunwayAssignment(RWY_ID, RunwayAssignmentSource.Radio.TaxiClearance)),
        )
        // No processInstruction(GoAround) — trained-GA's natural flow
        // doesn't trigger handleGoAround. Flag stays None throughout.
        assertEquals(
            None,
            mission.pendingAtcGoAroundFrom,
            "Trained-GA natural flow: no processInstruction(GoAround), so the flag is never set",
        )
    }

    /**
     * Future-circuit non-corruption: ATC-GA on the active circuit of a
     * multi-outcome trained-GA mission must NOT corrupt the recovery
     * circuit's mission state. Specifically, the future-circuit's
     * primitives (`FLY_FINAL`, `AWAIT_LANDING_CLEARANCE`, etc.) must
     * remain incomplete after `handleGoAround` rewrites the active
     * compound.
     *
     * (This pins fn-11.1's `markCompleteInActiveCompound` invariant
     * carries through — `handleGoAround` does not walk past the active
     * compound when stamping the flag.)
     */
    @Test
    fun `future-circuit preservation — ATC-GA does not corrupt the recovery circuit`() {
        // Multi-outcome mission: active full-stop + recovery full-stop.
        val goal = HighLevelGoal.CircuitTraining(
            outcomes = listOf(CircuitOutcome.FullStop, CircuitOutcome.FullStop),
        )
        var mission = PilotMission(
            goal = goal,
            root = planMission(goal),
            stepEnteredAt = now0,
            navigationMode = Some(NavigationMode.Circuit(RWY_ID, CKT_ID)),
            activeRunway = Some(RunwayAssignment(RWY_ID, RunwayAssignmentSource.Radio.TaxiClearance)),
        )
        // Walk to FLY_FINAL on circuit 1.
        listOf(
            MissionStep.REQUEST_TAXI, MissionStep.TAXI_TO_HOLDING,
            MissionStep.RUN_UP_CHECKS, MissionStep.REPORT_READY,
            MissionStep.AWAIT_LINE_UP, MissionStep.AWAIT_TAKEOFF_CLEARANCE,
            MissionStep.FLY_DEPARTURE, MissionStep.FLY_DOWNWIND,
            MissionStep.REPORT_DOWNWIND, MissionStep.AWAIT_SEQUENCING,
            MissionStep.FLY_BASE, MissionStep.REPORT_BASE,
        ).forEach { step ->
            mission = mission.copy(root = mission.root.markComplete(step))
        }
        assertEquals(MissionStep.FLY_FINAL, mission.currentTask?.step)

        // Apply ATC-issued GoAround. The active circuit compound is
        // replaced with `CircuitAfterGoAround = [goAroundTask, circuitTask]`.
        // The original recovery circuit (the second outcome) must remain
        // untouched.
        val updated = processInstruction(
            instruction = GoAround(target = ac),
            mission = mission,
            now = now0,
            worldIndex = emptyWorldIndex(),
        )
        assertEquals(Some(MissionStep.FLY_FINAL), updated.pendingAtcGoAroundFrom)

        // Mission root: [groundDeparture, replaced(CircuitAfterGoAround),
        //                originalRecoveryCircuit, groundArrival].
        // The recovery circuit is the 3rd top-level child (index 2).
        val recoveryCircuit = updated.root.children[2] as CompoundTask
        val recoveryIncompleteSteps = collectIncompleteSteps(recoveryCircuit)
        assertTrue(
            MissionStep.FLY_FINAL in recoveryIncompleteSteps,
            "recovery circuit's FLY_FINAL must remain incomplete (got $recoveryIncompleteSteps)",
        )
        assertTrue(
            MissionStep.AWAIT_LANDING_CLEARANCE in recoveryIncompleteSteps,
            "recovery circuit's AWAIT_LANDING_CLEARANCE must remain incomplete",
        )
        assertTrue(
            MissionStep.LAND in recoveryIncompleteSteps,
            "recovery circuit's LAND must remain incomplete",
        )
    }

    /** Collect MissionSteps of all incomplete primitive leaves in a subtree, in tree order. */
    private fun collectIncompleteSteps(task: TaskNode): List<MissionStep> = when (task) {
        is PrimitiveTask -> if (!task.completed) listOf(task.step) else emptyList()
        is CompoundTask -> task.children.flatMap { collectIncompleteSteps(it) }
    }

    // ────────────────────────────────────────────────────────────────────
    // Group F — applyAtcInitiatedGoAround direct-call shape (unit pin)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Direct call: `applyAtcInitiatedGoAround` is intent-only and clears
     * the flag. Pinned independently of `pilotDecide` so a regression that
     * accidentally calls `mission.resetForGoAround(now)` here would be
     * caught (would wipe state set by `handleGoAround`).
     */
    @Test
    fun `applyAtcInitiatedGoAround is intent-only — does NOT call resetForGoAround`() {
        // Seed a mission with stamped state from an earlier handleGoAround
        // call. resetForGoAround already cleared `hasClearance` etc. (since
        // handleGoAround calls it); preserved fields on this mission shape
        // are what matters: any non-default field set here MUST survive
        // applyAtcInitiatedGoAround unchanged.
        val baseMission = missionAtStep(MissionStep.LAND).copy(
            hasClearance = true,
            pendingAtcGoAroundFrom = Some(MissionStep.LAND),
            // Sentinel fields that resetForGoAround would clear if called
            // again — they must stay set after applyAtcInitiatedGoAround.
            altitudeRestrictionM = Some(80.0),
            activeConstraints = setOf(ActiveConstraint.ExtendingDownwind),
        )
        val aircraft = aircraftAt(phase = PilotPhase.Final, altitudeM = 50.0, mission = baseMission)
        val result = applyAtcInitiatedGoAround(baseMission, aircraft)

        // Intent shape — mirrors fn-11.1 trained-GA Tick A.
        assertEquals(PilotPhase.Final, result.intent.phase)
        assertEquals(PilotRoute.None, result.intent.route)
        // Mission delta — flag cleared, sentinel fields preserved.
        assertEquals(None, result.mission.pendingAtcGoAroundFrom, "flag must be cleared")
        assertEquals(
            true,
            result.mission.hasClearance,
            "applyAtcInitiatedGoAround must NOT call resetForGoAround — preserved hasClearance",
        )
        assertEquals(
            Some(80.0),
            result.mission.altitudeRestrictionM,
            "applyAtcInitiatedGoAround must NOT call resetForGoAround — preserved altitudeRestrictionM",
        )
        assertEquals(
            setOf(ActiveConstraint.ExtendingDownwind),
            result.mission.activeConstraints,
            "applyAtcInitiatedGoAround must NOT call resetForGoAround — preserved activeConstraints",
        )
    }

    /**
     * `handleGoAround` IS responsible for the reset (it calls
     * `resetForGoAround` internally before stamping the flag). Pinning
     * this here so the contract is symmetric with the
     * `applyAtcInitiatedGoAround is intent-only` test above.
     */
    @Test
    fun `handleGoAround calls resetForGoAround — clears hasClearance even at LAND`() {
        val mission = missionAtStep(MissionStep.LAND).copy(
            hasClearance = true,
            altitudeRestrictionM = Some(80.0),
            activeConstraints = setOf(ActiveConstraint.ExtendingDownwind),
        )
        val updated = processInstruction(
            instruction = GoAround(target = ac),
            mission = mission,
            now = now0,
            worldIndex = emptyWorldIndex(),
        )
        // resetForGoAround was called by handleGoAround — phase-local fields cleared.
        assertEquals(false, updated.hasClearance)
        assertEquals(None, updated.altitudeRestrictionM)
        assertTrue(updated.activeConstraints.isEmpty())
        // But the flag was stamped onto the reset result.
        assertEquals(Some(MissionStep.LAND), updated.pendingAtcGoAroundFrom)
    }
}
