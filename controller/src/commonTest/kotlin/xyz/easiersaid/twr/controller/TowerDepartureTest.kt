package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.TowerDepartureStage
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TowerDepartureTest {

    private val worldIndex = testWorldIndex()

    @Test
    fun `full departure sequence — ready to handoff`() {
        var beliefs = BeliefState.EMPTY

        // ── Cycle 1: Aircraft at holding point, pilot reports ready ──
        val ac1 = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val view1 = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac1),
            receivedMessages = listOf(readyForDepartureMessage(TestIds.acAlpha)),
            time = SimTime.ofSeconds(10),
        )
        val result1 = testControllerDecide(view1, beliefs)
        beliefs = result1.updatedBeliefs

        val instruct1 = result1.outputs.filterIsInstance<ControllerOutput.Instruct>().firstOrNull()
        assertNotNull(instruct1, "Should issue an instruction")
        assertTrue(instruct1.instruction is LineUpAndWait, "Should issue LineUpAndWait, got ${instruct1.instruction::class.simpleName}")
        assertTrue(instruct1.trace.regulations.any { it.document == "ICAO_4444" && it.section == "§7.9" },
            "Should cite ICAO 4444 §7.9 for takeoff clearance procedure")

        // Verify commitment advanced to AwaitLineUpObserved
        val commitment1 = beliefs.commitments[TestIds.acAlpha]
        assertNotNull(commitment1)
        assertEquals(TowerDepartureStage.AwaitLineUpObserved, commitment1.stage)

        // ── Cycle 2: Aircraft now on runway (lined up) ──
        val ac2 = aircraftAt(TestIds.acAlpha, TestIds.rwyMid, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val view2 = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac2),
            time = SimTime.ofSeconds(20),
        )
        val result2 = testControllerDecide(view2, beliefs)
        beliefs = result2.updatedBeliefs

        val instruct2 = result2.outputs.filterIsInstance<ControllerOutput.Instruct>().firstOrNull()
        assertNotNull(instruct2, "Should issue takeoff clearance")
        assertTrue(instruct2.instruction is ClearedForTakeoff, "Should issue ClearedForTakeoff, got ${instruct2.instruction::class.simpleName}")
        assertTrue(instruct2.trace.regulations.any { it.document == "ICAO_4444" },
            "Should cite ICAO 4444")

        // Stage advances immediately to TakeoffClearanceIssued; readback confirms to AwaitTakeoffObserved.
        assertEquals(TowerDepartureStage.TakeoffClearanceIssued, beliefs.commitments[TestIds.acAlpha]?.stage)

        // ── Cycle 2b: Pilot reads back ClearedForTakeoff ──
        val view2b = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac2),
            receivedMessages = listOf(readbackFor(instruct2)),
            time = SimTime.ofSeconds(25),
        )
        val result2b = testControllerDecide(view2b, beliefs)
        beliefs = result2b.updatedBeliefs
        assertEquals(TowerDepartureStage.AwaitTakeoffObserved, beliefs.commitments[TestIds.acAlpha]?.stage,
            "Stage should advance after readback confirmation")

        // ── Cycle 3: Aircraft airborne ──
        val ac3 = aircraftAt(TestIds.acAlpha, TestIds.upwind, worldIndex, onGround = false, goal = PilotGoal.DEPART)
        val view3 = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac3),
            time = SimTime.ofSeconds(30),
        )
        val result3 = testControllerDecide(view3, beliefs)
        beliefs = result3.updatedBeliefs

        val instruct3 = result3.outputs.filterIsInstance<ControllerOutput.Instruct>().firstOrNull()
        assertNotNull(instruct3, "Should issue handoff")
        assertTrue(instruct3.instruction is ContactFrequency, "Should issue ContactFrequency, got ${instruct3.instruction::class.simpleName}")
        assertTrue(instruct3.trace.regulations.any { it.document == "ICAO_4444" && it.section == "§10.1" },
            "Should cite ICAO 4444 §10.1 for transfer of communications")
    }

    @Test
    fun `DEP-LUAW is blocked when another aircraft is on short final`() {
        // Arrival on final, runway clear (arrival has not reached runway yet).
        // Runway duty should grant to the arrival first, but to exercise the guard
        // we pre-seed a state where the departure has access but arrival is on final.
        var beliefs = BeliefState.EMPTY

        // Cycle 1: establish arrival on downwind + departure ready.
        val dep = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val arr = aircraftAt(TestIds.acBravo, TestIds.downwind, worldIndex, onGround = false, goal = PilotGoal.ARRIVE)
        val view1 = towerView(
            aircraft = mapOf(TestIds.acAlpha to dep, TestIds.acBravo to arr),
            receivedMessages = listOf(
                readyForDepartureMessage(TestIds.acAlpha),
                positionReportMessage(TestIds.acBravo, ReportEvent.Downwind),
            ),
            time = SimTime.ofSeconds(10),
        )
        beliefs = testControllerDecide(view1, beliefs).updatedBeliefs

        // Cycle 2: arrival reaches final. Runway duty will grant to arrival; if it
        // somehow granted to departure (edge case), the guard must still refuse LUAW.
        val arr2 = aircraftAt(TestIds.acBravo, TestIds.finalApproach, worldIndex, onGround = false, goal = PilotGoal.ARRIVE)
        val view2 = towerView(
            aircraft = mapOf(TestIds.acAlpha to dep, TestIds.acBravo to arr2),
            time = SimTime.ofSeconds(15),
        )
        val result = testControllerDecide(view2, beliefs)

        val depOutput = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.target == TestIds.acAlpha }
        // Departure must not be told to line up or take off while an arrival is on final.
        if (depOutput != null) {
            assertTrue(
                depOutput.instruction !is LineUpAndWait && depOutput.instruction !is ClearedForTakeoff,
                "Departure must not get line-up or takeoff while arrival on short final, got ${depOutput.instruction::class.simpleName}",
            )
        }
    }

    @Test
    fun `DEP-HANDOFF waits until aircraft has climbed onto upwind`() {
        // Drive departure to AwaitTakeoffObserved.
        var beliefs = BeliefState.EMPTY
        val ac1 = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to ac1),
                receivedMessages = listOf(readyForDepartureMessage(TestIds.acAlpha)),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        val ac2 = aircraftAt(TestIds.acAlpha, TestIds.rwyMid, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val result2 = testControllerDecide(
            towerView(aircraft = mapOf(TestIds.acAlpha to ac2), time = SimTime.ofSeconds(20)),
            beliefs,
        )
        beliefs = result2.updatedBeliefs
        // Deliver readback for ClearedForTakeoff so stage advances (readbackAdvancesToStage).
        val takeoffInstruct = result2.instructs().first { it.instruction is ClearedForTakeoff }
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to ac2),
                receivedMessages = listOf(readbackFor(takeoffInstruct)),
                time = SimTime.ofSeconds(22),
            ),
            beliefs,
        ).updatedBeliefs

        // Aircraft just airborne, still over the runway (rwyEnd, which belongs to the
        // runway strip) — no upwind/crosswind leg yet. Handoff must NOT fire yet.
        val ac3 = aircraftAt(TestIds.acAlpha, TestIds.rwyEnd, worldIndex, onGround = false, goal = PilotGoal.DEPART)
        val result3 = testControllerDecide(
            towerView(aircraft = mapOf(TestIds.acAlpha to ac3), time = SimTime.ofSeconds(25)),
            beliefs,
        )
        val premature = result3.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is ContactFrequency }
        assertTrue(
            premature == null,
            "Handoff must not fire while aircraft is still over the runway strip — saw ${premature?.instruction}",
        )
        beliefs = result3.updatedBeliefs

        // Now on upwind leg — handoff fires.
        val ac4 = aircraftAt(TestIds.acAlpha, TestIds.upwind, worldIndex, onGround = false, goal = PilotGoal.DEPART)
        val result4 = testControllerDecide(
            towerView(aircraft = mapOf(TestIds.acAlpha to ac4), time = SimTime.ofSeconds(30)),
            beliefs,
        )
        assertNotNull(
            result4.outputs.filterIsInstance<ControllerOutput.Instruct>()
                .firstOrNull { it.instruction is ContactFrequency },
            "Handoff should fire once aircraft reaches upwind leg",
        )
    }

    @Test
    fun `all instructions carry regulation traces`() {
        var beliefs = BeliefState.EMPTY

        val ac = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val view = towerView(
            aircraft = mapOf(TestIds.acAlpha to ac),
            receivedMessages = listOf(readyForDepartureMessage(TestIds.acAlpha)),
        )
        val result = testControllerDecide(view, beliefs)

        for (output in result.outputs) {
            when (output) {
                is ControllerOutput.Instruct -> {
                    assertTrue(output.trace.regulations.isNotEmpty(),
                        "Instruction ${output.instruction::class.simpleName} should have regulation trace")
                }
                is ControllerOutput.Respond -> {
                    assertTrue(output.trace.regulations.isNotEmpty(),
                        "Response should have regulation trace")
                }
                is ControllerOutput.InitiateHandoff -> {}
            }
        }
    }

    @Test
    fun `anomalous runway incursion — hold position before evaluating takeoff`() {
        var beliefs = BeliefState.EMPTY

        // Aircraft appears on the runway without any line-up clearance.
        // Reconciliation advances from AwaitReady to AwaitLineUpObserved (ANOMALOUS).
        // The incursion rule should fire before the takeoff rule.
        val ac = aircraftAt(TestIds.acAlpha, TestIds.rwyMid, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val result = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to ac),
                receivedMessages = listOf(readyForDepartureMessage(TestIds.acAlpha)),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        )

        val instruct = result.instructs().firstOrNull { it.target == TestIds.acAlpha }
        assertNotNull(instruct, "Should issue an instruction for the aircraft on the runway")
        assertTrue(instruct.instruction is HoldPosition,
            "Anomalous incursion must get HoldPosition before any takeoff clearance, got ${instruct.instruction::class.simpleName}")
        assertTrue(instruct.trace.ruleId == "DEP-HOLD-INCURSION",
            "Should be the incursion rule, got ${instruct.trace.ruleId}")
    }

    @Test
    fun `DEP-TAKEOFF-REISSUE fires after readback timeout`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: Ready → LUAW.
        val ac1 = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(TestIds.acAlpha to ac1),
                receivedMessages = listOf(readyForDepartureMessage(TestIds.acAlpha)),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        // Cycle 2: On runway → ClearedForTakeoff.
        val ac2 = aircraftAt(TestIds.acAlpha, TestIds.rwyMid, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val result2 = testControllerDecide(
            towerView(aircraft = mapOf(TestIds.acAlpha to ac2), time = SimTime.ofSeconds(20)),
            beliefs,
        )
        beliefs = result2.updatedBeliefs
        val takeoff = result2.instructs().first { it.instruction is ClearedForTakeoff }
        assertNotNull(takeoff)
        assertEquals(TowerDepartureStage.TakeoffClearanceIssued, beliefs.commitments[TestIds.acAlpha]?.stage)

        // Cycle 3: No readback, coordination still active → no re-issue yet.
        beliefs = testControllerDecide(
            towerView(aircraft = mapOf(TestIds.acAlpha to ac2), time = SimTime.ofSeconds(25)),
            beliefs,
        ).updatedBeliefs
        // Coordination for ClearedForTakeoff is pending → re-issue guard blocked by NoPendingReadback.

        // Cycle 4: After MAX_READBACK_AGE, coordination GCs → re-issue fires.
        val reissueTime = SimTime.ofSeconds(20 + 35) // well past 30s GC
        val result4 = testControllerDecide(
            towerView(aircraft = mapOf(TestIds.acAlpha to ac2), time = reissueTime),
            beliefs,
        )
        val reissue = result4.instructs().firstOrNull { it.instruction is ClearedForTakeoff }
        assertNotNull(reissue, "ClearedForTakeoff should be re-issued after readback timeout")
        assertEquals(TowerDepartureStage.TakeoffClearanceIssued,
            result4.updatedBeliefs.commitments[TestIds.acAlpha]?.stage,
            "Stage stays at TakeoffClearanceIssued after re-issue")
    }
}
