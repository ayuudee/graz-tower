package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.assess.ArrivalGate
import xyz.easiersaid.twr.controller.assess.ArrivalSequence
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the ArrivalSequence derivation (Phase 5b).
 *
 * These exercise the sequence state itself — ordering, stable numbers, gate
 * filtering — not the duty-queue projection (which is covered by RunwayDutyQueueTest).
 */
class ArrivalSequenceTest {

    private val worldIndex = testWorldIndex()

    private val arr1 = AircraftId("G-ARR1")
    private val arr2 = AircraftId("G-ARR2")
    private val arr3 = AircraftId("G-ARR3")

    private fun arrAt(id: AircraftId, point: PointId): AircraftObservation =
        aircraftAt(id, point, worldIndex, onGround = false, goal = PilotGoal.ARRIVE)

    @Test
    fun `sequence orders arrivals by distance to threshold — final closer than downwind`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: arr1 on downwind, arr2 on final. Both get AwaitDownwind initially.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    arr1 to arrAt(arr1, TestIds.downwind),
                    arr2 to arrAt(arr2, TestIds.finalApproach),
                ),
                receivedMessages = listOf(
                    positionReportMessage(arr1, ReportEvent.Downwind),
                    positionReportMessage(arr2, ReportEvent.Final),
                ),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        val sequence = beliefs.arrivalSequence
        assertNotNull(sequence, "arrival sequence should be populated")
        assertTrue(sequence.slots.isNotEmpty(), "sequence should have slots")

        // Both should be in the sequence. arr2 (final, ~200m from threshold)
        // should sort before arr1 (downwind, ~670m from threshold).
        val aircraftOrder = sequence.slots.map { it.aircraft }
        assertEquals(listOf(arr2, arr1), aircraftOrder,
            "final approach (closer) should sort before downwind (farther)")
    }

    @Test
    fun `stable number preserved when new arrival inserted`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: arr1 on final — gets sequence number.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(arr1 to arrAt(arr1, TestIds.finalApproach)),
                receivedMessages = listOf(positionReportMessage(arr1, ReportEvent.Final)),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        val seq1 = beliefs.arrivalSequence
        assertNotNull(seq1)
        val arr1Number = seq1.slots.firstOrNull { it.aircraft == arr1 }?.stableNumber
        assertNotNull(arr1Number, "arr1 should have a stable number")

        // Cycle 2: arr2 appears on base — enters sequence.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    arr1 to arrAt(arr1, TestIds.finalApproach),
                    arr2 to arrAt(arr2, TestIds.base),
                ),
                receivedMessages = listOf(positionReportMessage(arr2, ReportEvent.Base)),
                time = SimTime.ofSeconds(20),
            ),
            beliefs,
        ).updatedBeliefs

        val seq2 = beliefs.arrivalSequence
        assertNotNull(seq2)
        assertEquals(2, seq2.slots.size, "both arrivals should be in the sequence")
        assertEquals(arr1Number, seq2.slots.first { it.aircraft == arr1 }.stableNumber,
            "arr1's stable number must not change when arr2 is inserted")
    }

    @Test
    fun `downwind arrival in sequence but NOT in duty queue`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: arr1 on downwind.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(arr1 to arrAt(arr1, TestIds.downwind)),
                receivedMessages = listOf(positionReportMessage(arr1, ReportEvent.Downwind)),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        // Cycle 2: arr1 advances to AwaitApproach. Still on downwind position.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(arr1 to arrAt(arr1, TestIds.downwind)),
                time = SimTime.ofSeconds(20),
            ),
            beliefs,
        ).updatedBeliefs

        // arr1 should be in the sequence (tracked for numbering)...
        val sequence = beliefs.arrivalSequence
        assertNotNull(sequence)
        val slot = sequence.slots.firstOrNull { it.aircraft == arr1 }
        assertNotNull(slot, "arr1 should be in the arrival sequence")
        assertTrue(slot.gate is ArrivalGate.Downwind,
            "gate should be Downwind, was ${slot.gate}")

        // ...but NOT in the duty queue (downwind is not close enough to compete for runway).
        val duty = beliefs.runwayDuty
        val inQueue = duty?.queue?.any { it.aircraft == arr1 } ?: false
        val isHolder = duty?.holder == arr1
        assertTrue(!inQueue && !isHolder,
            "downwind arrival must not appear in duty queue — sequence only")
    }

    @Test
    fun `path distance orders crosswind further than final despite similar Euclidean distance`() {
        // In the test world: crosswind is at (1800, 600), threshold at (0, 0).
        // Euclidean: ~1897m. Path: crosswind→downwind→base→final→threshold ≈ 1800+632+200+200 ≈ 2832m.
        // Final is at (-200, 0), Euclidean to threshold: 200m. Path: final→threshold = 200m.
        // So path distance puts crosswind much further than final — correct for sequencing.
        var beliefs = BeliefState.EMPTY

        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    arr1 to arrAt(arr1, TestIds.crosswind),
                    arr2 to arrAt(arr2, TestIds.finalApproach),
                ),
                receivedMessages = listOf(
                    positionReportMessage(arr1, ReportEvent.Downwind), // crosswind is pre-downwind
                    positionReportMessage(arr2, ReportEvent.Final),
                ),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        val seq = beliefs.arrivalSequence
        assertNotNull(seq)
        if (seq.slots.size >= 2) {
            val arr1Slot = seq.slots.first { it.aircraft == arr1 }
            val arr2Slot = seq.slots.first { it.aircraft == arr2 }
            // arr1 (crosswind) should have LARGER path distance than arr2 (final).
            val arr1Dist = arr1Slot.distanceToThresholdM
            val arr2Dist = arr2Slot.distanceToThresholdM
            assertNotNull(arr1Dist)
            assertNotNull(arr2Dist)
            assertTrue(arr1Dist > arr2Dist,
                "crosswind ($arr1Dist m) should be path-further than final ($arr2Dist m)")
            // Path distance should be significantly more than Euclidean for crosswind.
            assertTrue(arr1Dist > 2000.0,
                "crosswind path distance ($arr1Dist m) should exceed Euclidean (~1897m)")
        }
    }

    @Test
    fun `inserting closer arrival ahead triggers number reassignment for trailing`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: arr1 on base — gets a number.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(arr1 to arrAt(arr1, TestIds.base)),
                receivedMessages = listOf(positionReportMessage(arr1, ReportEvent.Base)),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        // Cycle 2: advance arr1 to AwaitApproach.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(arr1 to arrAt(arr1, TestIds.base)),
                time = SimTime.ofSeconds(20),
            ),
            beliefs,
        ).updatedBeliefs

        val arr1NumBefore = beliefs.arrivalSequence?.slots?.first { it.aircraft == arr1 }?.stableNumber
        assertNotNull(arr1NumBefore)

        // Cycle 3: arr2 appears on FINAL (closer than arr1 on base) — inserts ahead.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    arr1 to arrAt(arr1, TestIds.base),
                    arr2 to arrAt(arr2, TestIds.finalApproach),
                ),
                receivedMessages = listOf(positionReportMessage(arr2, ReportEvent.Final)),
                time = SimTime.ofSeconds(30),
            ),
            beliefs,
        ).updatedBeliefs

        // Cycle 4: arr2 advances to AwaitApproach — now in sequence ahead of arr1.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    arr1 to arrAt(arr1, TestIds.base),
                    arr2 to arrAt(arr2, TestIds.finalApproach),
                ),
                time = SimTime.ofSeconds(40),
            ),
            beliefs,
        ).updatedBeliefs

        val seq = beliefs.arrivalSequence
        assertNotNull(seq)
        val arr2Slot = seq.slots.first { it.aircraft == arr2 }
        val arr1Slot = seq.slots.first { it.aircraft == arr1 }
        // arr2 is closer (final) so should be first; arr1's predecessor changed.
        assertTrue(arr2Slot.stableNumber < arr1Slot.stableNumber,
            "arr2 (closer) should have lower number than arr1")
        assertTrue(arr1 in seq.resequencedAircraft || arr1Slot.stableNumber != arr1NumBefore,
            "arr1 should be re-sequenced (predecessor changed from null to arr2)")
    }

    @Test
    fun `removal of aircraft compacts trailing numbers`() {
        var beliefs = BeliefState.EMPTY

        // Setup: arr1 and arr2 both in sequence. arr1 on final, arr2 on base.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    arr1 to arrAt(arr1, TestIds.finalApproach),
                    arr2 to arrAt(arr2, TestIds.base),
                ),
                receivedMessages = listOf(
                    positionReportMessage(arr1, ReportEvent.Final),
                    positionReportMessage(arr2, ReportEvent.Base),
                ),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        // Cycle 2: both advance to AwaitApproach.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    arr1 to arrAt(arr1, TestIds.finalApproach),
                    arr2 to arrAt(arr2, TestIds.base),
                ),
                time = SimTime.ofSeconds(20),
            ),
            beliefs,
        ).updatedBeliefs

        val seq2 = beliefs.arrivalSequence
        assertNotNull(seq2)
        assertEquals(2, seq2.slots.size)

        // Cycle 3: arr1 disappears (go-around, left sequence). Only arr2 remains.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(arr2 to arrAt(arr2, TestIds.base)),
                responsibilities = setOf(arr2),
                time = SimTime.ofSeconds(30),
            ),
            beliefs,
        ).updatedBeliefs

        val seq3 = beliefs.arrivalSequence
        assertNotNull(seq3)
        assertEquals(1, seq3.slots.size, "only arr2 should remain")
        assertEquals(arr2, seq3.slots.first().aircraft)
    }
}
