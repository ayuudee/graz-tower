package xyz.easiersaid.twr.pilot

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec test (E8) for the Phase D mission-tree shape.
 *
 * Pins the exact ground-departure mission tree so a future change either
 * (a) deliberately updates this test as a one-line diff, or (b) fires
 * before G0 and names the change. Without this pin, G0 would fail with
 * "mission did not complete in 30 minutes" — uninformative.
 *
 * The expected shape after Phase D:
 *  - REQUEST_TAXI (no startup steps — D-PF.1 deferment)
 *  - TAXI_TO_HOLDING
 *  - RUN_UP_CHECKS
 *  - REPORT_READY
 *  - AWAIT_LINE_UP
 *  - AWAIT_TAKEOFF_CLEARANCE
 *
 * If `REQUEST_STARTUP` / `AWAIT_STARTUP_APPROVAL` re-appear in the tree
 * under this `groundDepartureTask()` (no-arg) signature, this test fires.
 * The right way to bring those steps back is via D-PF.1: an
 * airport-conditional variant `groundDepartureTask(airport)` that includes
 * the steps when the airport's manifest declares startup-clearance is
 * required. The single-airport-uniform shape pinned here is correct for
 * LOWG/LJMB/LJLJ-class GA airports today.
 */
class GroundDepartureTaskShapeSpec {

    @Test
    fun `groundDepartureTask is REQUEST_TAXI to AWAIT_TAKEOFF_CLEARANCE — uniform across AI and human`() {
        val task = groundDepartureTask()
        val steps = task.children.filterIsInstance<PrimitiveTask>().map { it.step }
        assertEquals(
            listOf(
                MissionStep.REQUEST_TAXI,
                MissionStep.TAXI_TO_HOLDING,
                MissionStep.RUN_UP_CHECKS,
                MissionStep.REPORT_READY,
                MissionStep.AWAIT_LINE_UP,
                MissionStep.AWAIT_TAKEOFF_CLEARANCE,
            ),
            steps,
        )
    }
}
