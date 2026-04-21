package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TowerArrivalTest {

    private val worldIndex = testWorldIndex()

    @Test
    fun `arrival in circuit — sequenced then cleared to land`() {
        var beliefs = BeliefState.EMPTY

        // ── Cycle 1: Aircraft on downwind, reports position ──
        val ac1 = aircraftAt(TestIds.acAlpha, TestIds.downwind, worldIndex, onGround = false, goal = PilotGoal.ARRIVE)
        val view1 = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac1),
            receivedMessages = listOf(positionReportMessage(TestIds.acAlpha, ReportEvent.Downwind())),
            time = SimTime.ofSeconds(10),
        )
        val result1 = testControllerDecide(view1, beliefs)
        beliefs = result1.updatedBeliefs

        // Commitment should advance past AwaitDownwind
        val commitment1 = beliefs.commitments[TestIds.acAlpha]
        assertNotNull(commitment1)
        assertEquals(TowerArrivalStage.AwaitApproach, commitment1.stage,
            "Should advance to AwaitApproach after downwind report")

        // ── Cycle 2: Aircraft still in circuit, runway clear → clear to land ──
        val ac2 = aircraftAt(TestIds.acAlpha, TestIds.finalApproach, worldIndex, onGround = false, goal = PilotGoal.ARRIVE)
        val view2 = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac2),
            time = SimTime.ofSeconds(20),
        )
        val result2 = testControllerDecide(view2, beliefs)
        beliefs = result2.updatedBeliefs

        val instruct2 = result2.outputs.filterIsInstance<ControllerOutput.Instruct>().firstOrNull()
        assertNotNull(instruct2, "Should issue landing clearance")
        assertTrue(instruct2.instruction is ClearedToLand, "Should issue ClearedToLand, got ${instruct2.instruction::class.simpleName}")
        assertTrue(instruct2.trace.regulations.any { it.document == "ICAO_4444" && it.section == "§7.10" },
            "Should cite ICAO 4444 §7.10")
    }

    @Test
    fun `touch-and-go gets ClearedTouchAndGo`() {
        var beliefs = BeliefState.EMPTY

        // Aircraft on circuit final, goal = touch and go, position reported
        val ac = aircraftAt(TestIds.acAlpha, TestIds.downwind, worldIndex, onGround = false, goal = PilotGoal.TOUCH_AND_GO)
        val view1 = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac),
            receivedMessages = listOf(positionReportMessage(TestIds.acAlpha, ReportEvent.Downwind())),
            time = SimTime.ofSeconds(10),
        )
        val result1 = testControllerDecide(view1, beliefs)
        beliefs = result1.updatedBeliefs

        // Now on final
        val ac2 = aircraftAt(TestIds.acAlpha, TestIds.finalApproach, worldIndex, onGround = false, goal = PilotGoal.TOUCH_AND_GO)
        val view2 = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac2),
            time = SimTime.ofSeconds(20),
        )
        val result2 = testControllerDecide(view2, beliefs)

        val instruct = result2.outputs.filterIsInstance<ControllerOutput.Instruct>().firstOrNull()
        assertNotNull(instruct, "Should issue clearance")
        assertTrue(instruct.instruction is ClearedTouchAndGo, "Should issue ClearedTouchAndGo, got ${instruct.instruction::class.simpleName}")
    }

    @Test
    fun `go-around resets commitment to AwaitDownwind`() {
        var beliefs = BeliefState.EMPTY

        // Setup: aircraft in AwaitApproach stage
        val ac1 = aircraftAt(TestIds.acAlpha, TestIds.downwind, worldIndex, onGround = false, goal = PilotGoal.ARRIVE)
        val view1 = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac1),
            receivedMessages = listOf(positionReportMessage(TestIds.acAlpha, ReportEvent.Downwind())),
            time = SimTime.ofSeconds(10),
        )
        beliefs = testControllerDecide(view1, beliefs).updatedBeliefs
        assertEquals(TowerArrivalStage.AwaitApproach, beliefs.commitments[TestIds.acAlpha]?.stage)

        // Go-around reported
        val ac2 = aircraftAt(TestIds.acAlpha, TestIds.upwind, worldIndex, onGround = false, goal = PilotGoal.ARRIVE)
        val view2 = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac2),
            receivedMessages = listOf(goAroundMessage(TestIds.acAlpha)),
            time = SimTime.ofSeconds(30),
        )
        beliefs = testControllerDecide(view2, beliefs).updatedBeliefs

        assertEquals(TowerArrivalStage.AwaitDownwind, beliefs.commitments[TestIds.acAlpha]?.stage,
            "Go-around should reset commitment to AwaitDownwind")
    }

    @Test
    fun `ARR-LAND advances to LandingClearanceIssued then AwaitLandedObserved on readback`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: downwind report → AwaitApproach.
        val ac1 = aircraftAt(TestIds.acAlpha, TestIds.downwind, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to ac1),
                receivedMessages = listOf(positionReportMessage(TestIds.acAlpha, ReportEvent.Downwind())),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        // Cycle 2: on final → ClearedToLand → LandingClearanceIssued.
        val ac2 = aircraftAt(TestIds.acAlpha, TestIds.finalApproach, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        val result2 = testControllerDecide(
            towerView(aircraft = mapOf(TestIds.acAlpha to ac2), time = SimTime.ofSeconds(20)),
            beliefs,
        )
        beliefs = result2.updatedBeliefs
        val landInstruct = result2.instructs().firstOrNull { it.instruction is ClearedToLand }
        assertNotNull(landInstruct, "Should issue ClearedToLand")
        assertEquals(TowerArrivalStage.LandingClearanceIssued, beliefs.commitments[TestIds.acAlpha]?.stage,
            "Stage should advance to LandingClearanceIssued immediately")

        // Cycle 3: readback → AwaitLandedObserved.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to ac2),
                receivedMessages = listOf(readbackFor(landInstruct)),
                time = SimTime.ofSeconds(22),
            ),
            beliefs,
        ).updatedBeliefs
        assertEquals(TowerArrivalStage.AwaitLandedObserved, beliefs.commitments[TestIds.acAlpha]?.stage,
            "Readback should advance to AwaitLandedObserved")
    }

    @Test
    fun `ARR-LAND-REISSUE fires after readback timeout`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: downwind → AwaitApproach.
        val ac1 = aircraftAt(TestIds.acAlpha, TestIds.downwind, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to ac1),
                receivedMessages = listOf(positionReportMessage(TestIds.acAlpha, ReportEvent.Downwind())),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        // Cycle 2: on final → ClearedToLand → LandingClearanceIssued.
        val ac2 = aircraftAt(TestIds.acAlpha, TestIds.finalApproach, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        beliefs = testControllerDecide(
            towerView(aircraft = mapOf(TestIds.acAlpha to ac2), time = SimTime.ofSeconds(20)),
            beliefs,
        ).updatedBeliefs
        assertEquals(TowerArrivalStage.LandingClearanceIssued, beliefs.commitments[TestIds.acAlpha]?.stage)

        // Cycle 3: after MAX_READBACK_AGE (30s), coordination GC'd → re-issue fires.
        val reissueTime = SimTime.ofSeconds(20 + 35)
        val result3 = testControllerDecide(
            towerView(aircraft = mapOf(TestIds.acAlpha to ac2), time = reissueTime),
            beliefs,
        )
        val reissue = result3.instructs().firstOrNull { it.instruction is ClearedToLand }
        assertNotNull(reissue, "ClearedToLand should be re-issued after readback timeout")
        assertEquals(TowerArrivalStage.LandingClearanceIssued,
            result3.updatedBeliefs.commitments[TestIds.acAlpha]?.stage,
            "Stage stays at LandingClearanceIssued after re-issue")
    }
}
