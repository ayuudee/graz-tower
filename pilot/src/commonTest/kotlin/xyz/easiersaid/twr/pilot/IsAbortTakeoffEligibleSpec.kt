package xyz.easiersaid.twr.pilot

import arrow.core.Some
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * fn-28.9 (G0 abort-takeoff R16): `isAbortTakeoffEligible` named-guard
 * mission-shape enumeration.
 *
 * Takeoff-roll shapes only — `AWAIT_TAKEOFF_CLEARANCE` / `FLY_DEPARTURE`.
 * Every other MissionStep returns `false`. Mirrors fn-14.1's
 * `isReactiveGoAroundEligible` named-guard test pattern + fn-28.2's
 * sibling `IsDensityAltitudeDeclineEligibleSpec`.
 *
 * **R16 split contract** (round-4 Major 3): DA decline + abort eligibility
 * are SEPARATE guards. This file pins the abort contract; the DA-decline
 * test lives in `IsDensityAltitudeDeclineEligibleSpec`. A regression that
 * wires `isAbortTakeoffEligible` to fire on pre-taxi shapes (which
 * DA-decline cares about) fails here.
 */
class IsAbortTakeoffEligibleSpec {

    private val now0 = SimTime.ZERO
    private val rwy = RunwayId("09")

    private fun missionWithStep(step: MissionStep): PilotMission {
        return PilotMission(
            goal = HighLevelGoal.Departure(),
            root = CompoundTask(
                name = TaskName.Depart,
                children = listOf(
                    PrimitiveTask(step, CompletionMode.PHYSICAL),
                ),
            ),
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(rwy, RunwayAssignmentSource.Filing)),
        )
    }

    @Test
    fun `AWAIT_TAKEOFF_CLEARANCE is eligible — pilot committed at holding point`() {
        assertTrue(
            isAbortTakeoffEligible(missionWithStep(MissionStep.AWAIT_TAKEOFF_CLEARANCE)),
            "AWAIT_TAKEOFF_CLEARANCE: pilot at holding point with engine running, " +
                "abort still on-runway-terminal",
        )
    }

    @Test
    fun `FLY_DEPARTURE is eligible — post-clearance takeoff roll`() {
        assertTrue(
            isAbortTakeoffEligible(missionWithStep(MissionStep.FLY_DEPARTURE)),
            "FLY_DEPARTURE: cognitive layer advances to this step on " +
                "ClearedForTakeoff; takeoff roll plays out under this step",
        )
    }

    @Test
    fun `MissionStep enumeration — exhaustive non-eligible coverage`() {
        // Codex round-2 finding 1 fix: replace hand-maintained negative
        // lists with an exhaustive assertion over `MissionStep.entries`.
        // The hand-maintained shape was missing `FLY_FINAL_TO_SHORT_FINAL`,
        // `FLY_STAR`, and `AWAITING_ATC_INSTRUCTION`; a future broadening
        // of `isAbortTakeoffEligible` to one of those would have passed
        // the guard tests silently. Exhaustive enumeration is the
        // "expand the condition space, don't carve exceptions" pattern
        // required by the project's testing discipline.
        //
        // **Eligible set** (single source of truth — mirrors the function
        // body):
        val eligible = setOf(
            MissionStep.AWAIT_TAKEOFF_CLEARANCE,
            MissionStep.FLY_DEPARTURE,
        )
        for (step in MissionStep.entries) {
            val expected = step in eligible
            val actual = isAbortTakeoffEligible(missionWithStep(step))
            check(actual == expected) {
                "Abort-eligible mismatch for MissionStep.$step: expected=$expected " +
                    "(eligible iff in $eligible per `isAbortTakeoffEligible` body), got=$actual. " +
                    "If a future regulation / design widens or narrows the abort-eligible step " +
                    "set, update the `eligible` set above to match the function body — " +
                    "exhaustive enumeration over `MissionStep.entries` prevents drift between " +
                    "the guard's negative space and the test's hand-maintained list."
            }
        }
    }

    @Test
    fun `null currentTask returns false — fail-closed on no active primitive`() {
        val completedMission = PilotMission(
            goal = HighLevelGoal.Departure(),
            root = CompoundTask(
                name = TaskName.Depart,
                children = listOf(
                    PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL, completed = true),
                ),
            ),
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(rwy, RunwayAssignmentSource.Filing)),
        )
        assertFalse(
            isAbortTakeoffEligible(completedMission),
            "fail-closed when no active primitive (currentTask is null)",
        )
    }
}
