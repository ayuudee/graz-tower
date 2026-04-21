package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TowerSequencingTest {

    private val worldIndex = testWorldIndex()

    @Test
    fun `arrival on final gets priority over departure at holding point`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: Arrival reports downwind, departure reports ready — both enter system
        val dep1 = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val arr1 = aircraftAt(TestIds.acBravo, TestIds.downwind, worldIndex, onGround = false, goal = PilotGoal.ARRIVE)
        val view1 = towerView(
            aircraft = mapOf(TestIds.acAlpha to dep1, TestIds.acBravo to arr1),
            receivedMessages = listOf(
                readyForDepartureMessage(TestIds.acAlpha),
                positionReportMessage(TestIds.acBravo, ReportEvent.Downwind()),
            ),
            time = SimTime.ofSeconds(10),
        )
        beliefs = testControllerDecide(view1, beliefs).updatedBeliefs

        // Cycle 2: Arrival now on final (AwaitApproach stage, circuit entity) — should get runway priority
        val dep2 = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val arr2 = aircraftAt(TestIds.acBravo, TestIds.finalApproach, worldIndex, onGround = false, goal = PilotGoal.ARRIVE)
        val view2 = towerView(
            aircraft = mapOf(TestIds.acAlpha to dep2, TestIds.acBravo to arr2),
            time = SimTime.ofSeconds(20),
        )
        val result2 = testControllerDecide(view2, beliefs)

        val instructs = result2.outputs.filterIsInstance<ControllerOutput.Instruct>()
        val arrInstruct = instructs.firstOrNull { it.target == TestIds.acBravo }

        // Arrival should get ClearedToLand (runway granted to arrival by priority)
        assertNotNull(arrInstruct, "Arrival on final should get a clearance")
        assertTrue(arrInstruct.instruction is ClearedToLand,
            "Arrival should get ClearedToLand, got ${arrInstruct.instruction::class.simpleName}")
    }

    @Test
    fun `weather hold prevents departure`() {
        var beliefs = BeliefState.EMPTY

        val ac = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val view = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac),
            receivedMessages = listOf(readyForDepartureMessage(TestIds.acAlpha)),
            time = SimTime.ofSeconds(10),
            weather = WeatherObservation(wind = null, qnh = null, visibility = 2000), // below VMC
        )
        val result = testControllerDecide(view, beliefs)

        val instruct = result.outputs.filterIsInstance<ControllerOutput.Instruct>().firstOrNull()
        assertNotNull(instruct, "Should issue hold position in IMC")
        assertTrue(instruct.instruction is HoldPosition, "Should hold in IMC, got ${instruct.instruction::class.simpleName}")
        assertTrue(instruct.trace.regulations.any { it.document == "SERA" },
            "Should cite SERA for VMC hold")
    }

    @Test
    fun `human-piloted aircraft requires explicit ready report`() {
        var beliefs = BeliefState.EMPTY

        // Human pilot at holding point, no messages sent
        val ac = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true,
            goal = PilotGoal.DEPART, humanPiloted = true)
        val view = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac),
            receivedMessages = emptyList(), // no ready report
            time = SimTime.ofSeconds(10),
        )
        val result = testControllerDecide(view, beliefs)

        // Should NOT issue line-up — pilot hasn't reported ready and AiProactive doesn't fire for humans
        val instructs = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
        val lineUp = instructs.firstOrNull { it.instruction is LineUpAndWait }
        assertTrue(lineUp == null, "Should not issue LineUpAndWait without ready report from human pilot")
    }

    @Test
    fun `human-piloted aircraft gets line-up after ready report`() {
        var beliefs = BeliefState.EMPTY

        val ac = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true,
            goal = PilotGoal.DEPART, humanPiloted = true)
        val view = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac),
            receivedMessages = listOf(readyForDepartureMessage(TestIds.acAlpha)),
            time = SimTime.ofSeconds(10),
        )
        val result = testControllerDecide(view, beliefs)

        val instruct = result.outputs.filterIsInstance<ControllerOutput.Instruct>().firstOrNull()
        assertNotNull(instruct, "Should issue instruction after ready report")
        assertTrue(instruct.instruction is LineUpAndWait, "Should issue LineUpAndWait, got ${instruct.instruction::class.simpleName}")
    }
}
