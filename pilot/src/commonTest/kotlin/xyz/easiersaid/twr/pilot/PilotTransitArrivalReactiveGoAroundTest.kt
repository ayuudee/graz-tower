package xyz.easiersaid.twr.pilot

import arrow.core.Some
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.pilot.observe.PilotEvent
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * fn-28.6 (G3b R18 / R19 / R22 / round-4 Major 1, Major 2 + round-16
 * Major 2): `applyCrosswindGoAround` + `applyTailwindGoAround` dispatch
 * fork — when the mission is a Transit-arrival reactive-GA shape
 * (Transit goal + arrival primitive direct child of Transit + Final-phase
 * + runway assigned), the appliers dispatch to the suffix-replace path via
 * `replaceFromActivePrimitive(listOf(goAroundTask(), circuitTask(),
 * groundArrivalTask()))`.
 *
 * **Distinct from [PilotCrosswindGoAroundTest] / [PilotTailwindGoAroundTest]**
 * — those tests cover the circuit-only `replaceChild { isCircuitLike }`
 * branch. This test covers the Transit-arrival fork.
 *
 * **Acceptance** (from fn-28.6 spec):
 *  - Recognition fires (covered separately in `PilotEventCrosswindTest` /
 *    `PilotEventTailwindTest`'s widened mission-shape rows).
 *  - Applier chooses suffix-replace path (this test).
 *  - Tick A intent uses `climbSpeedMps + Final + None + patternAltitude`
 *    (R19; this test).
 *  - `resetForGoAround(now)` cleared approach-side fields (round-16 Major 2;
 *    this test).
 *  - No-refire post-apply (this test).
 *  - Both axes (crosswind + tailwind) exercise the same fork (this test).
 */
class PilotTransitArrivalReactiveGoAroundTest {

    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ZERO
    private val type = AircraftType.C172
    private val runway27 = RunwayId("27")
    private val ljmb = AerodromeId("LJMB")

    private fun aircraft(phase: PilotPhase = PilotPhase.Final): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = Position(0.0, 0.0),
        positionPoint = PointId("P"),
        altitudeM = 200.0,
        phase = phase,
        type = type,
    )

    private fun crosswindEvent(): PilotEvent.CrosswindLimitExceeded =
        PilotEvent.CrosswindLimitExceeded(
            aircraft = ac,
            componentKnots = 20.0,
            limitKnots = 15,
            runway = runway27,
        )

    private fun tailwindEvent(): PilotEvent.TailwindLimitExceeded =
        PilotEvent.TailwindLimitExceeded(
            aircraft = ac,
            componentKnots = 14.0,
            limitKnots = 10,
            runway = runway27,
        )

    /**
     * Canonical Transit-arrival mission. Arrival primitives are direct
     * children of the Transit compound (mirrors `planMission(Transit)` —
     * see PilotMission.kt:878-892). The active primitive is the first
     * incomplete one (FLY_FINAL by default).
     */
    private fun transitArrivalMission(
        activeStep: MissionStep = MissionStep.FLY_FINAL,
        hasClearance: Boolean = false,
        altitudeRestrictionM: Double? = 80.0,
    ): PilotMission {
        // Build the canonical Transit task list, mark every primitive BEFORE
        // the requested active step as completed.
        val allSteps = listOf(
            MissionStep.FLY_DEPARTURE,
            MissionStep.CALL_INBOUND,
            MissionStep.AWAIT_JOINING_INSTRUCTIONS,
            MissionStep.FLY_DOWNWIND,
            MissionStep.REPORT_DOWNWIND,
            MissionStep.AWAIT_SEQUENCING,
            MissionStep.FLY_BASE,
            MissionStep.REPORT_BASE,
            MissionStep.FLY_FINAL,
            MissionStep.REPORT_FINAL,
            MissionStep.AWAIT_LANDING_CLEARANCE,
            MissionStep.LAND,
        )
        val activeIdx = allSteps.indexOf(activeStep)
        require(activeIdx >= 0) { "step $activeStep not in Transit-arrival sequence" }
        val children: List<TaskNode> = allSteps.mapIndexed { idx, step ->
            PrimitiveTask(step, CompletionMode.PHYSICAL, completed = idx < activeIdx)
        }
        return PilotMission(
            goal = HighLevelGoal.Transit(destination = ljmb),
            root = CompoundTask(name = TaskName.Transit, children = children),
            stepEnteredAt = now0,
            hasClearance = hasClearance,
            activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
            altitudeRestrictionM = altitudeRestrictionM?.let { Some(it) } ?: arrow.core.None,
        )
    }

    /** Walk a TaskNode tree collecting every primitive's MissionStep. */
    private fun collectSteps(task: TaskNode): List<MissionStep> = when (task) {
        is PrimitiveTask -> if (task.completed) emptyList() else listOf(task.step)
        is CompoundTask -> task.children.flatMap { collectSteps(it) }
    }

    /** Walk a TaskNode tree collecting every compound's TaskName. */
    private fun collectCompoundNames(task: TaskNode): List<TaskName> = when (task) {
        is PrimitiveTask -> emptyList()
        is CompoundTask -> listOf(task.name) + task.children.flatMap { collectCompoundNames(it) }
    }

    // ── R19: Tick A intent ──────────────────────────────────────────────

    @Test
    fun `crosswind dispatch — Tick A intent matches R19 climbSpeed + Final + None + patternAlt`() {
        val result = applyCrosswindGoAround(
            crosswindEvent(),
            transitArrivalMission(),
            aircraft(),
            now0,
        )
        assertEquals(
            PilotPhase.Final,
            result.intent.phase,
            "R19: phase=Final retained (Tick B builds GA climb route from Final + no-route)",
        )
        assertTrue(
            result.intent.route is PilotRoute.None,
            "R19: route=None invalidates kinematic route toward threshold",
        )
        assertEquals(
            type.kinematics.climbSpeedMps,
            result.intent.targetSpeedMps,
            1e-9,
            "R19: targetSpeedMps = climbSpeedMps (NOT 0 — that's the DA/abort regime)",
        )
        assertEquals(
            type.circuitPattern.altitudeAglM,
            result.intent.targetAltitudeM,
            1e-9,
            "R19: targetAltitudeM = pattern altitude",
        )
    }

    @Test
    fun `tailwind dispatch — Tick A intent matches R19 climbSpeed + Final + None + patternAlt`() {
        val result = applyTailwindGoAround(
            tailwindEvent(),
            transitArrivalMission(),
            aircraft(),
            now0,
        )
        assertEquals(PilotPhase.Final, result.intent.phase)
        assertTrue(result.intent.route is PilotRoute.None)
        assertEquals(type.kinematics.climbSpeedMps, result.intent.targetSpeedMps, 1e-9)
        assertEquals(type.circuitPattern.altitudeAglM, result.intent.targetAltitudeM, 1e-9)
    }

    @Test
    fun `transmits Report(GoingAround) per CAP 413 sec4dot66 — both axes`() {
        listOf(
            applyCrosswindGoAround(crosswindEvent(), transitArrivalMission(), aircraft(), now0),
            applyTailwindGoAround(tailwindEvent(), transitArrivalMission(), aircraft(), now0),
        ).forEach { result ->
            assertEquals(
                listOf(Report(listOf(ReportEvent.GoingAround))),
                result.transmissions,
                "Transit-arrival reactive-GA transmits Report(GoingAround) — same as circuit-only path",
            )
        }
    }

    // ── R13 + R22: suffix-replace shape ─────────────────────────────────

    @Test
    fun `suffix replacement uses goAroundTask + circuitTask + groundArrivalTask — R22`() {
        val result = applyCrosswindGoAround(
            crosswindEvent(),
            transitArrivalMission(activeStep = MissionStep.FLY_FINAL),
            aircraft(),
            now0,
        )
        val compounds = collectCompoundNames(result.mission.root)
        // R22: post-suffix-replace, the Transit compound contains the GA
        // recovery continuation as nested compounds — GoAround, Circuit,
        // GroundArrival.
        assertTrue(
            TaskName.GoAround in compounds,
            "suffix-replace inserts goAroundTask(); got compounds=$compounds",
        )
        assertTrue(
            TaskName.Circuit in compounds,
            "suffix-replace inserts circuitTask(); got compounds=$compounds",
        )
        assertTrue(
            TaskName.GroundArrival in compounds,
            "suffix-replace inserts groundArrivalTask() (full Transit recovery includes landing-and-taxi continuation); got $compounds",
        )
        // The outer Transit compound remains.
        assertTrue(
            TaskName.Transit in compounds,
            "outer Transit compound preserved through suffix-replace",
        )
        // R13: NOT a circuit-only `CircuitAfterGoAround` rewrite — that's
        // the non-Transit branch.
        assertFalse(
            TaskName.CircuitAfterGoAround in compounds,
            "Transit dispatch does NOT produce CircuitAfterGoAround — that's the circuit-only branch",
        )
    }

    @Test
    fun `incomplete suffix after replace contains GOING_AROUND first`() {
        val result = applyCrosswindGoAround(
            crosswindEvent(),
            transitArrivalMission(activeStep = MissionStep.FLY_FINAL),
            aircraft(),
            now0,
        )
        // The first INCOMPLETE primitive after suffix-replace is the
        // GoAround compound's first primitive (GOING_AROUND).
        val currentStep = result.mission.currentTask?.step
        assertEquals(
            MissionStep.GOING_AROUND,
            currentStep,
            "after suffix-replace, the active step is GoAround's first primitive (GOING_AROUND)",
        )
    }

    @Test
    fun `pre-active completed primitives preserved through suffix-replace`() {
        // Build a mission where FLY_DEPARTURE / CALL_INBOUND / ... are all
        // marked complete (the FLY_FINAL active state is post-arrival-join
        // and pre-landing). After suffix-replace, the completed leading
        // primitives must remain in the rewritten tree (the rewrite drops
        // from the active primitive onward, NOT the completed leading
        // primitives).
        val result = applyCrosswindGoAround(
            crosswindEvent(),
            transitArrivalMission(activeStep = MissionStep.FLY_FINAL),
            aircraft(),
            now0,
        )
        val transitChildren = (result.mission.root as CompoundTask).children
        // The leading children should still include the pre-FLY_FINAL
        // completed primitives (FLY_DEPARTURE, CALL_INBOUND, etc.). Their
        // exact count depends on `transitArrivalMission`'s sequence — the
        // first 8 entries (FLY_DEPARTURE..REPORT_BASE) precede FLY_FINAL.
        val leadingCompleted = transitChildren.takeWhile { it.isComplete }
        assertTrue(
            leadingCompleted.isNotEmpty(),
            "leading completed primitives preserved through suffix-replace; got ${transitChildren.size} children with $leadingCompleted leading complete",
        )
        // The first compound after the leading-complete prefix must be
        // GoAround (the suffix's first entry).
        val firstIncomplete = transitChildren[leadingCompleted.size]
        assertTrue(
            firstIncomplete is CompoundTask && firstIncomplete.name is TaskName.GoAround,
            "first non-complete child post-replace is the GoAround compound; got $firstIncomplete",
        )
    }

    // ── round-16 Major 2: resetForGoAround clears approach-side state ──

    @Test
    fun `resetForGoAround clears hasClearance + altitudeRestriction + joinLeg`() {
        val initial = transitArrivalMission(
            hasClearance = true,
            altitudeRestrictionM = 80.0,
        ).copy(joinLeg = Some(xyz.easiersaid.twr.core.world.LegName.BASE))
        val result = applyCrosswindGoAround(crosswindEvent(), initial, aircraft(), now0)
        assertFalse(
            result.mission.hasClearance,
            "round-16 Major 2: hasClearance cleared",
        )
        assertEquals(
            null,
            result.mission.altitudeRestrictionM.getOrNull(),
            "round-16 Major 2: altitudeRestrictionM cleared",
        )
        assertEquals(
            null,
            result.mission.joinLeg.getOrNull(),
            "round-16 Major 2: joinLeg cleared",
        )
    }

    // ── No-refire invariant (R18) ─────────────────────────────────────

    @Test
    fun `post-apply mission no longer matches Transit-arrival guard — no refire`() {
        val initial = transitArrivalMission(activeStep = MissionStep.FLY_FINAL)
        val result = applyCrosswindGoAround(crosswindEvent(), initial, aircraft(), now0)
        // After apply, the active primitive lives inside a nested GoAround
        // compound — `activeCompound()` returns non-null →
        // `isTransitArrivalReactiveGoAroundEligible` returns false →
        // recognition does NOT re-fire.
        assertFalse(
            isTransitArrivalReactiveGoAroundEligible(aircraft(), result.mission),
            "post-apply mission shape fails the Transit-arrival guard — no re-fire next tick",
        )
        // Sanity: the post-apply active step is GOING_AROUND, which is NOT
        // in the wind-reactive eligible step set anyway, so the recognition
        // would also fail closed on the step guard. The activeCompound
        // check is the harder gate to verify.
        val postActiveCompound = result.mission.root.activeCompound()
        assertNotNull(
            postActiveCompound,
            "post-apply, activeCompound() returns non-null (GoAround compound nested under Transit)",
        )
        assertTrue(
            postActiveCompound.name is TaskName.GoAround,
            "post-apply, the active inner compound is GoAround (first suffix entry); got ${postActiveCompound.name}",
        )
    }

    // ── Cross-check: existing circuit-only path NOT affected ────────────

    @Test
    fun `Arrival mission still routes through circuit-only branch — NOT Transit fork`() {
        // A standard Arrival mission (NOT Transit) with the canonical
        // `planMission(Arrival)` shape: ArrivalJoin + Circuit + GroundArrival
        // children of the Arrive compound. The crosswind GA must use the
        // circuit-only `replaceChild { isCircuitLike }` branch (produces
        // CircuitAfterGoAround), NOT the Transit fork (would produce
        // GoAround+Circuit+GroundArrival inside the Arrive compound).
        val arrivalMission = PilotMission(
            goal = HighLevelGoal.Arrival(),
            root = CompoundTask(
                name = TaskName.Arrive,
                children = listOf(
                    CompoundTask(
                        name = TaskName.Circuit,
                        children = listOf(
                            PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL),
                        ),
                    ),
                ),
            ),
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
        )
        val result = applyCrosswindGoAround(crosswindEvent(), arrivalMission, aircraft(), now0)
        val compounds = collectCompoundNames(result.mission.root)
        assertTrue(
            TaskName.CircuitAfterGoAround in compounds,
            "Arrival mission → circuit-only branch produces CircuitAfterGoAround; got $compounds",
        )
    }
}
