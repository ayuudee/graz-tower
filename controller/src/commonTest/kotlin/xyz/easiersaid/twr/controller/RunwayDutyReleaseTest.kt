package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for Bug A (2026-04-16): runway released while arrival still airborne.
 *
 * Before the fix, the runway-duty tracker treated any airborne holder as "gone", which
 * let a queued departure slip onto the runway while an arrival was still on short final.
 * After the fix, an arrival only releases the runway once it has actually reached the
 * runway surface and then left it (normal vacate, touch-and-go, late go-around).
 */
class RunwayDutyReleaseTest {

    private val worldIndex = testWorldIndex()

    @Test
    fun `arrival on final with queued departure — departure must not get runway`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: arrival reports downwind, departure is ready at hold-short.
        val dep1 = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex,
            onGround = true, goal = PilotGoal.DEPART)
        val arr1 = aircraftAt(TestIds.acBravo, TestIds.downwind, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        val view1 = towerView(
            aircraft = mapOf(TestIds.acAlpha to dep1, TestIds.acBravo to arr1),
            receivedMessages = listOf(
                readyForDepartureMessage(TestIds.acAlpha),
                positionReportMessage(TestIds.acBravo, ReportEvent.Downwind()),
            ),
            time = SimTime.ofSeconds(10),
        )
        beliefs = testControllerDecide(view1, beliefs).updatedBeliefs

        // Cycle 2: arrival on final — gets ClearedToLand, is now runway holder.
        val dep2 = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex,
            onGround = true, goal = PilotGoal.DEPART)
        val arr2 = aircraftAt(TestIds.acBravo, TestIds.finalApproach, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        val view2 = towerView(
            aircraft = mapOf(TestIds.acAlpha to dep2, TestIds.acBravo to arr2),
            time = SimTime.ofSeconds(20),
        )
        val result2 = testControllerDecide(view2, beliefs)
        beliefs = result2.updatedBeliefs

        assertEquals(TestIds.acBravo, beliefs.runwayDuty?.holder,
            "Arrival should hold the runway after ClearedToLand")
        // Stage advances to LandingClearanceIssued; readback confirms to AwaitLandedObserved.
        val landInstruct = result2.instructs().firstOrNull { it.instruction is ClearedToLand }
        if (landInstruct != null) {
            // Deliver readback so stage advances.
            beliefs = testControllerDecide(
                towerView(
                    aircraft = mapOf(TestIds.acAlpha to dep2, TestIds.acBravo to arr2),
                    receivedMessages = listOf(readbackFor(landInstruct)),
                    time = SimTime.ofSeconds(22),
                ),
                beliefs,
            ).updatedBeliefs
        }
        assertEquals(TowerArrivalStage.AwaitLandedObserved,
            beliefs.commitments[TestIds.acBravo]?.stage)

        // Cycle 3: arrival still on short final, still airborne, still NOT on a runway
        // point. Old logic would release here; new logic must keep holding.
        val dep3 = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex,
            onGround = true, goal = PilotGoal.DEPART)
        val arr3 = aircraftAt(TestIds.acBravo, TestIds.finalApproach, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        val view3 = towerView(
            aircraft = mapOf(TestIds.acAlpha to dep3, TestIds.acBravo to arr3),
            time = SimTime.ofSeconds(25),
        )
        val result3 = testControllerDecide(view3, beliefs)
        beliefs = result3.updatedBeliefs

        assertEquals(TestIds.acBravo, beliefs.runwayDuty?.holder,
            "Arrival on short final must keep the runway (not released by being airborne)")

        val depInstruct = result3.outputs
            .filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.target == TestIds.acAlpha }
        // Departure may get line-up-and-wait (AI aircraft would) only after runway access;
        // with arrival holding, the departure must not receive a takeoff/line-up clearance.
        if (depInstruct != null) {
            assertTrue(
                depInstruct.instruction !is ClearedForTakeoff &&
                    depInstruct.instruction !is LineUpAndWait,
                "Departure must not get line-up or takeoff while arrival holds runway, got ${depInstruct.instruction::class.simpleName}",
            )
        }
    }

    @Test
    fun `reported late go-around from airborne arrival releases runway`() {
        var beliefs = BeliefState.EMPTY

        // Get arrival into AWAIT_LANDED_OBSERVED as runway holder, having reached the runway.
        val arr1 = aircraftAt(TestIds.acAlpha, TestIds.downwind, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to arr1),
                receivedMessages = listOf(positionReportMessage(TestIds.acAlpha, ReportEvent.Downwind())),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        val arr2 = aircraftAt(TestIds.acAlpha, TestIds.finalApproach, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to arr2),
                time = SimTime.ofSeconds(20),
            ),
            beliefs,
        ).updatedBeliefs

        // Aircraft reaches runway threshold (holder now reachedRunway = true).
        val arr3 = aircraftAt(TestIds.acAlpha, TestIds.rwyThreshold, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to arr3),
                time = SimTime.ofSeconds(25),
            ),
            beliefs,
        ).updatedBeliefs
        assertEquals(TestIds.acAlpha, beliefs.runwayDuty?.holder,
            "Arrival should still hold while over runway")

        // Late go-around reported while airborne and off runway.
        val arr4 = aircraftAt(TestIds.acAlpha, TestIds.upwind, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        val view4 = towerView(
            aircraft = mapOf(TestIds.acAlpha to arr4),
            receivedMessages = listOf(goAroundMessage(TestIds.acAlpha)),
            time = SimTime.ofSeconds(30),
        )
        beliefs = testControllerDecide(view4, beliefs).updatedBeliefs

        assertNull(beliefs.runwayDuty?.holder,
            "Reported go-around must release the runway")
    }

    @Test
    fun `touch-and-go — runway released after aircraft lifts off`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: T&G aircraft reports downwind.
        val ac1 = aircraftAt(TestIds.acAlpha, TestIds.downwind, worldIndex,
            onGround = false, goal = PilotGoal.TOUCH_AND_GO)
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to ac1),
                receivedMessages = listOf(positionReportMessage(TestIds.acAlpha, ReportEvent.Downwind())),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        // Cycle 2: on final — gets ClearedTouchAndGo, holder = alpha.
        val ac2 = aircraftAt(TestIds.acAlpha, TestIds.finalApproach, worldIndex,
            onGround = false, goal = PilotGoal.TOUCH_AND_GO)
        val result2 = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to ac2),
                time = SimTime.ofSeconds(20),
            ),
            beliefs,
        )
        beliefs = result2.updatedBeliefs
        val clearance = result2.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is ClearedTouchAndGo }
        assertNotNull(clearance, "Should issue ClearedTouchAndGo")
        assertEquals(TestIds.acAlpha, beliefs.runwayDuty?.holder)

        // Cycle 3: aircraft on runway, on ground. No vacate should be issued (T&G pilot goal).
        val ac3 = aircraftAt(TestIds.acAlpha, TestIds.rwyMid, worldIndex,
            onGround = true, goal = PilotGoal.TOUCH_AND_GO)
        val result3 = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to ac3),
                time = SimTime.ofSeconds(25),
            ),
            beliefs,
        )
        beliefs = result3.updatedBeliefs
        val vacate = result3.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is AfterLandingVacateVia || it.instruction is BacktrackRunway }
        assertNull(vacate, "T&G must not receive a vacate instruction")
        assertEquals(TestIds.acAlpha, beliefs.runwayDuty?.holder,
            "Runway still held while T&G rolling on runway")

        // Cycle 4: aircraft airborne again past end of runway (upwind), off runway entities.
        val ac4 = aircraftAt(TestIds.acAlpha, TestIds.upwind, worldIndex,
            onGround = false, goal = PilotGoal.TOUCH_AND_GO)
        val result4 = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to ac4),
                time = SimTime.ofSeconds(30),
            ),
            beliefs,
        )
        beliefs = result4.updatedBeliefs

        assertNull(beliefs.runwayDuty?.holder,
            "Runway must be released once T&G aircraft has left the runway surface")

        // Cycle 5: reconciliation prunes the completed T&G arrival and forms a fresh one
        // for the next circuit.
        val ac5 = aircraftAt(TestIds.acAlpha, TestIds.crosswind, worldIndex,
            onGround = false, goal = PilotGoal.TOUCH_AND_GO)
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to ac5),
                time = SimTime.ofSeconds(40),
            ),
            beliefs,
        ).updatedBeliefs

        val commitment = beliefs.commitments[TestIds.acAlpha]
        assertNotNull(commitment, "New arrival commitment should form for the next circuit")
        assertTrue(
            commitment.stage == TowerArrivalStage.AwaitDownwind ||
                commitment.stage == TowerArrivalStage.AwaitApproach,
            "Post-T&G commitment should be in circuit-arrival stages (AwaitDownwind or AwaitApproach), got ${commitment.stage}",
        )
    }
}
