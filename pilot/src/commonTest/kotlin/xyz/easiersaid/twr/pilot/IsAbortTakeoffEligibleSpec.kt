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
    fun `pre-taxi steps are NOT eligible — DA-decline path covers this regime`() {
        // R16 split rationale: pre-taxi states fall under DA-decline
        // eligibility, not abort. Engine failure pre-taxi is a different
        // class of stoppage; the abort path requires a commitment to the
        // runway (AWAIT_TAKEOFF_CLEARANCE) or active roll (FLY_DEPARTURE).
        listOf(
            MissionStep.REQUEST_STARTUP,
            MissionStep.AWAIT_STARTUP_APPROVAL,
            MissionStep.REQUEST_TAXI,
            MissionStep.TAXI_TO_HOLDING,
            MissionStep.RUN_UP_CHECKS,
            MissionStep.REPORT_READY,
            MissionStep.AWAIT_LINE_UP,
        ).forEach { step ->
            assertFalse(
                isAbortTakeoffEligible(missionWithStep(step)),
                "Abort must NOT fire on $step (pre-runway-commitment); " +
                    "DA-decline or other ground-side paths cover these regimes",
            )
        }
    }

    @Test
    fun `airborne steps are NOT eligible — post-rotation is engine-out climb, not abort`() {
        // The phase guard inside `deriveAbortTakeoffEvent`
        // (`aircraft.phase == TakeoffRoll`) combines with the mission-shape
        // guard here to keep airborne engine-failure scenarios out of the
        // abort path. The mission-shape guard ALSO fails closed for
        // airborne steps so the two layers are independently sufficient.
        listOf(
            MissionStep.FLY_DOWNWIND,
            MissionStep.REPORT_DOWNWIND,
            MissionStep.AWAIT_SEQUENCING,
            MissionStep.FLY_BASE,
            MissionStep.REPORT_BASE,
            MissionStep.FLY_FINAL,
            MissionStep.REPORT_FINAL,
            MissionStep.AWAIT_LANDING_CLEARANCE,
            MissionStep.LAND,
            MissionStep.GOING_AROUND,
            MissionStep.FLY_SID,
            MissionStep.FLY_EN_ROUTE,
            MissionStep.FLY_APPROACH,
            MissionStep.FLY_MISSED_APPROACH,
        ).forEach { step ->
            assertFalse(
                isAbortTakeoffEligible(missionWithStep(step)),
                "Abort must NOT fire on airborne step $step (engine-out climb is a " +
                    "different emergency class, out of scope at fn-28)",
            )
        }
    }

    @Test
    fun `arrival-side steps are NOT eligible — abort is departure-only`() {
        listOf(
            MissionStep.CALL_INBOUND,
            MissionStep.AWAIT_JOINING_INSTRUCTIONS,
            MissionStep.REPORT_RUNWAY_VACATED,
            MissionStep.AWAIT_VACATE_INSTRUCTION,
            MissionStep.TAXI_TO_STAND,
            MissionStep.SHUTDOWN,
        ).forEach { step ->
            assertFalse(
                isAbortTakeoffEligible(missionWithStep(step)),
                "Abort must NOT fire on arrival/post-flight step $step",
            )
        }
    }

    @Test
    fun `terminal NON_COMPLETING steps are NOT eligible — already terminal`() {
        // DECLINE_DEPARTURE (fn-28.2) and ABORTED (fn-28.8) are
        // NON_COMPLETING terminal steps. Abort-recognition fires on a
        // shape that has NOT yet terminated; matching here would create
        // a double-write loop.
        listOf(
            MissionStep.DECLINE_DEPARTURE,
            MissionStep.ABORTED,
        ).forEach { step ->
            assertFalse(
                isAbortTakeoffEligible(missionWithStep(step)),
                "Abort must NOT fire on terminal step $step (already terminal)",
            )
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
