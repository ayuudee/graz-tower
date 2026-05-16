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
 * fn-28.2 (G3a-react-density-altitude R16): `isDensityAltitudeDeclineEligible`
 * named-guard mission-shape enumeration.
 *
 * Pre-taxi shapes only — REQUEST_TAXI / TAXI_TO_HOLDING. Every other
 * MissionStep returns `false`. Mirrors fn-14.1's `isReactiveGoAroundEligible`
 * named-guard test pattern (`PilotEventCrosswindTest` step-set rows).
 *
 * **R16 split contract** (round-4 Major 3): DA decline + abort eligibility
 * are SEPARATE guards. This file pins the DA-decline contract; the abort
 * eligibility test lives with fn-28.9. A regression that wires
 * `isDensityAltitudeDeclineEligible` to fire on `TakeoffRoll`-shape steps
 * (which abort cares about) fails here.
 */
class IsDensityAltitudeDeclineEligibleSpec {

    private val now0 = SimTime.ZERO
    private val rwy = RunwayId("09")

    private fun missionWithStep(step: MissionStep): PilotMission {
        // Build a minimal valid root with one primitive whose step is
        // `step` and active (incomplete). The helper inspects only
        // `mission.currentTask?.step`; the surrounding tree shape is
        // irrelevant.
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
    fun `REQUEST_TAXI is eligible — pre-taxi pre-line-up shape`() {
        assertTrue(
            isDensityAltitudeDeclineEligible(missionWithStep(MissionStep.REQUEST_TAXI)),
            "REQUEST_TAXI is the canonical apron-pre-taxi DA-decline shape",
        )
    }

    @Test
    fun `TAXI_TO_HOLDING is eligible — pilot may re-compute DA during taxi`() {
        assertTrue(
            isDensityAltitudeDeclineEligible(missionWithStep(MissionStep.TAXI_TO_HOLDING)),
            "TAXI_TO_HOLDING: pilot still on the surface, can re-compute DA + decline",
        )
    }

    @Test
    fun `post-taxi steps are NOT eligible — pilot committed to runway`() {
        // R16 split rationale: post-taxi states fall under abort eligibility,
        // not DA decline. A DA-driven abort uses a different mechanism (R20
        // ABORTED MissionStep + abort-applier, landing in fn-28.8/.9).
        listOf(
            MissionStep.RUN_UP_CHECKS,
            MissionStep.REPORT_READY,
            MissionStep.AWAIT_LINE_UP,
            MissionStep.AWAIT_TAKEOFF_CLEARANCE,
        ).forEach { step ->
            assertFalse(
                isDensityAltitudeDeclineEligible(missionWithStep(step)),
                "DA decline must NOT fire on $step (post-taxi); abort path covers this regime",
            )
        }
    }

    @Test
    fun `airborne steps are NOT eligible — DA decline is apron-side only`() {
        listOf(
            MissionStep.FLY_DEPARTURE,
            MissionStep.FLY_DOWNWIND,
            MissionStep.REPORT_DOWNWIND,
            MissionStep.FLY_BASE,
            MissionStep.FLY_FINAL,
            MissionStep.AWAIT_LANDING_CLEARANCE,
            MissionStep.LAND,
            MissionStep.GOING_AROUND,
        ).forEach { step ->
            assertFalse(
                isDensityAltitudeDeclineEligible(missionWithStep(step)),
                "DA decline must NOT fire on airborne step $step",
            )
        }
    }

    @Test
    fun `arrival-side steps are NOT eligible — DA decline is departure-only`() {
        listOf(
            MissionStep.CALL_INBOUND,
            MissionStep.AWAIT_JOINING_INSTRUCTIONS,
            MissionStep.REPORT_RUNWAY_VACATED,
            MissionStep.AWAIT_VACATE_INSTRUCTION,
            MissionStep.TAXI_TO_STAND,
            MissionStep.SHUTDOWN,
        ).forEach { step ->
            assertFalse(
                isDensityAltitudeDeclineEligible(missionWithStep(step)),
                "DA decline must NOT fire on arrival/post-flight step $step",
            )
        }
    }

    @Test
    fun `null currentTask returns false — fail-closed on no active primitive`() {
        // A mission with all primitives complete has currentTask == null.
        // The guard returns false (no eligible step → no recognition).
        val completedMission = PilotMission(
            goal = HighLevelGoal.Departure(),
            root = CompoundTask(
                name = TaskName.Depart,
                children = listOf(
                    PrimitiveTask(MissionStep.REQUEST_TAXI, CompletionMode.PHYSICAL, completed = true),
                ),
            ),
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(rwy, RunwayAssignmentSource.Filing)),
        )
        assertFalse(
            isDensityAltitudeDeclineEligible(completedMission),
            "fail-closed when no active primitive (currentTask is null)",
        )
    }
}
