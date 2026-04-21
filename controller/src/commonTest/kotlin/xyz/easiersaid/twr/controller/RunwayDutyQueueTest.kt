package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.assess.RunwayOperation
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for the runway-duty queue with 3+ mixed entries.
 *
 * Two-aircraft scenarios elsewhere in the suite exercise release rules and
 * single-slot grants. These tests cover invariants that only bite at three
 * or more aircraft:
 *
 *   - FIFO ordering within the same operation class is preserved when three
 *     departures queue up across cycles.
 *   - Preemption of a not-yet-rolling departure holder by an incoming
 *     arrival requeues the displaced holder ahead of later queued
 *     departures — its commitment age beats theirs.
 *   - ARRIVAL priority beats DEPARTURE regardless of enqueue order when
 *     both operations are present in the queue.
 *   - Commitment pruning removes queue entries for aircraft whose
 *     commitment disappears without disturbing surviving entries.
 *
 * `updateRunwayDuty` runs *before* `executeProcedures` in the controller
 * pipeline, so an aircraft's stage in cycle N is its stage at *entry* to
 * cycle N — procedures only advance it by the end. These tests reflect
 * that ordering: arrivals only register as `AwaitApproach` in the queue
 * one cycle after they become observable in circuit.
 */
class RunwayDutyQueueTest {

    private val worldIndex = testWorldIndex()

    private val dep1 = AircraftId("G-DEP1")
    private val dep2 = AircraftId("G-DEP2")
    private val dep3 = AircraftId("G-DEP3")
    private val arr1 = AircraftId("G-ARR1")
    private val arr2 = AircraftId("G-ARR2")

    private fun depAt(
        id: AircraftId,
        point: PointId = TestIds.holdShort,
    ): AircraftObservation =
        aircraftAt(id, point, worldIndex, onGround = true, goal = PilotGoal.DEPART)

    private fun arrAt(
        id: AircraftId,
        point: PointId,
    ): AircraftObservation =
        aircraftAt(id, point, worldIndex, onGround = false, goal = PilotGoal.ARRIVE)

    @Test
    fun `three departures ready over successive cycles — FIFO preserved`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: dep1 reports ready → granted as holder.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(dep1 to depAt(dep1)),
                receivedMessages = listOf(readyForDepartureMessage(dep1)),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs
        assertEquals(dep1, beliefs.runwayDuty?.holder)

        // Cycle 2: dep2 reports ready; joins queue behind holder.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(dep1 to depAt(dep1), dep2 to depAt(dep2)),
                receivedMessages = listOf(readyForDepartureMessage(dep2)),
                time = SimTime.ofSeconds(20),
            ),
            beliefs,
        ).updatedBeliefs

        // Cycle 3: dep3 reports ready; joins queue at tail.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    dep1 to depAt(dep1), dep2 to depAt(dep2), dep3 to depAt(dep3),
                ),
                receivedMessages = listOf(readyForDepartureMessage(dep3)),
                time = SimTime.ofSeconds(30),
            ),
            beliefs,
        ).updatedBeliefs

        val duty = beliefs.runwayDuty
        assertNotNull(duty, "runway duty should be tracked")
        assertEquals(dep1, duty.holder, "dep1 remains holder throughout")
        assertEquals(listOf(dep2, dep3), duty.queue.map { it.aircraft },
            "queue should preserve ready-report ordering: dep2 before dep3")
        assertTrue(duty.queue.all { it.operation == RunwayOperation.DEPARTURE },
            "all queued entries should be departures")
    }

    @Test
    fun `preempted departure requeues ahead of later departures`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: dep1 ready, arr1 on downwind (not yet on approach — reconciliation
        // keeps it at AwaitDownwind). dep1 grabs the runway.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    dep1 to depAt(dep1),
                    arr1 to arrAt(arr1, TestIds.downwind),
                ),
                receivedMessages = listOf(
                    readyForDepartureMessage(dep1),
                    positionReportMessage(arr1, ReportEvent.Downwind),
                ),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs
        assertEquals(dep1, beliefs.runwayDuty?.holder,
            "dep1 granted first — arr1 still on downwind, not yet in queue")

        // Cycle 2: dep2 reports ready. arr1 moves to final — reconciliation
        // advances to AwaitApproach, entering the duty queue.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    dep1 to depAt(dep1),
                    dep2 to depAt(dep2),
                    arr1 to arrAt(arr1, TestIds.finalApproach),
                ),
                receivedMessages = listOf(readyForDepartureMessage(dep2)),
                time = SimTime.ofSeconds(20),
            ),
            beliefs,
        ).updatedBeliefs

        // Cycle 3: arr1 on final with AwaitApproach commitment —
        // preempts dep1 (not yet rolling). dep1 requeues ahead of dep2.
        val result = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    dep1 to depAt(dep1),
                    dep2 to depAt(dep2),
                    arr1 to arrAt(arr1, TestIds.finalApproach),
                ),
                time = SimTime.ofSeconds(30),
            ),
            beliefs,
        )
        beliefs = result.updatedBeliefs

        val duty = beliefs.runwayDuty
        assertNotNull(duty)
        assertEquals(arr1, duty.holder,
            "arrival on final preempts grounded departure (ICAO 4444 §7.10)")
        assertEquals(RunwayOperation.ARRIVAL, duty.operation)
        assertEquals(listOf(dep1, dep2), duty.queue.map { it.aircraft },
            "preempted dep1 returns to queue ahead of dep2 — FIFO by commitment age")

        // Neither departure may receive line-up or takeoff this cycle.
        val depInstructs = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .filter { it.target == dep1 || it.target == dep2 }
        assertTrue(
            depInstructs.none { it.instruction is ClearedForTakeoff || it.instruction is LineUpAndWait },
            "departures must not get line-up/takeoff while arrival holds runway: got $depInstructs",
        )
    }

    @Test
    fun `arrival in queue sorts ahead of earlier-enqueued departure`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: arr1 on final (AwaitDownwind) to start the arrival state machine.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(arr1 to arrAt(arr1, TestIds.finalApproach)),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        // Cycle 2: arr1 at AwaitApproach, grabs runway as first queue entry.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(arr1 to arrAt(arr1, TestIds.finalApproach)),
                time = SimTime.ofSeconds(20),
            ),
            beliefs,
        ).updatedBeliefs
        assertEquals(arr1, beliefs.runwayDuty?.holder,
            "arr1 granted runway as sole AwaitApproach arrival")

        // Cycle 3: dep1 reports ready → queues behind holder.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    arr1 to arrAt(arr1, TestIds.finalApproach),
                    dep1 to depAt(dep1),
                ),
                receivedMessages = listOf(readyForDepartureMessage(dep1)),
                time = SimTime.ofSeconds(30),
            ),
            beliefs,
        ).updatedBeliefs
        assertEquals(listOf(dep1), beliefs.runwayDuty?.queue?.map { it.aircraft })

        // Cycle 4: arr2 observed on base (AwaitDownwind → AwaitApproach by end of cycle).
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    arr1 to arrAt(arr1, TestIds.finalApproach),
                    dep1 to depAt(dep1),
                    arr2 to arrAt(arr2, TestIds.base),
                ),
                time = SimTime.ofSeconds(40),
            ),
            beliefs,
        ).updatedBeliefs

        // Cycle 5: arr2 now AwaitApproach — enters queue. Sort must place
        // arr2 (ARRIVAL) ahead of dep1 (DEPARTURE) even though dep1 was
        // enqueued first.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    arr1 to arrAt(arr1, TestIds.finalApproach),
                    dep1 to depAt(dep1),
                    arr2 to arrAt(arr2, TestIds.base),
                ),
                time = SimTime.ofSeconds(50),
            ),
            beliefs,
        ).updatedBeliefs

        val duty = beliefs.runwayDuty
        assertNotNull(duty)
        assertEquals(arr1, duty.holder, "arr1 continues to hold the runway")
        assertEquals(listOf(arr2, dep1), duty.queue.map { it.aircraft },
            "ARRIVAL must sort ahead of DEPARTURE regardless of enqueue time")
        assertEquals(
            listOf(RunwayOperation.ARRIVAL, RunwayOperation.DEPARTURE),
            duty.queue.map { it.operation },
        )
    }

    @Test
    fun `commitment pruning drops departed aircraft from queue without disturbing others`() {
        var beliefs = BeliefState.EMPTY

        // Three departures ready in one cycle: dep1 becomes holder, dep2 and dep3 queue.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(
                    dep1 to depAt(dep1), dep2 to depAt(dep2), dep3 to depAt(dep3),
                ),
                receivedMessages = listOf(
                    readyForDepartureMessage(dep1),
                    readyForDepartureMessage(dep2),
                    readyForDepartureMessage(dep3),
                ),
                time = SimTime.ofSeconds(10),
            ),
            beliefs,
        ).updatedBeliefs

        val preDrop = beliefs.runwayDuty
        assertNotNull(preDrop)
        assertEquals(dep1, preDrop.holder)
        assertEquals(setOf(dep2, dep3), preDrop.queue.map { it.aircraft }.toSet(),
            "dep2 and dep3 both queued behind dep1")

        // Next cycle: dep2 falls out of the view (taxi-back / cancelled request
        // / responsibility handed off). Pruning should drop dep2 from the queue
        // while dep1 and dep3 stay intact.
        beliefs = testControllerDecide(
            towerView(
                aircraft = mapOf(dep1 to depAt(dep1), dep3 to depAt(dep3)),
                responsibilities = setOf(dep1, dep3),
                time = SimTime.ofSeconds(20),
            ),
            beliefs,
        ).updatedBeliefs

        val duty = beliefs.runwayDuty
        assertNotNull(duty)
        assertEquals(dep1, duty.holder, "dep1 still holder")
        assertEquals(listOf(dep3), duty.queue.map { it.aircraft },
            "dep2 pruned when its commitment disappears; dep3 retained")
    }
}
