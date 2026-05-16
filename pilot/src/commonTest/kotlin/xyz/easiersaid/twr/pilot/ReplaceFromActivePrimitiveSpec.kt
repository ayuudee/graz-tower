package xyz.easiersaid.twr.pilot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * fn-28.2 (G3a-react-density-altitude R13): `CompoundTask.replaceFromActivePrimitive`
 * primitive — direct unit tests for the SOLE fn-28-introduced task-tree
 * rewrite primitive.
 *
 * **Two shapes** the primitive must support (per round-4 Critical 1):
 *  - **Flat**: active primitive is a direct child of the receiver
 *    (e.g. groundDepartureTask whose children are all PrimitiveTask).
 *  - **Nested**: active primitive lives inside a nested CompoundTask
 *    (e.g. CircuitTraining whose first incomplete child is a nested
 *    Circuit compound). The rewrite happens at the inner compound's
 *    level; outer compounds are rebuilt via `copy(children = ...)`.
 *
 * **Idempotence at terminal** (no active primitive) and **single-primitive
 * suffix** (DA-decline + abort use case) are also pinned.
 */
class ReplaceFromActivePrimitiveSpec {

    private fun collectSteps(task: TaskNode): List<MissionStep> = when (task) {
        is PrimitiveTask -> listOf(task.step)
        is CompoundTask -> task.children.flatMap { collectSteps(it) }
    }

    @Test
    fun `flat — active primitive at index 0, replace with single primitive suffix`() {
        // Receiver: flat compound with three primitives, none complete.
        // Active is index 0 (REQUEST_TAXI).
        val root = CompoundTask(
            name = TaskName.GroundDeparture,
            children = listOf(
                PrimitiveTask(MissionStep.REQUEST_TAXI, CompletionMode.INSTRUCTION_GATED),
                PrimitiveTask(MissionStep.TAXI_TO_HOLDING, CompletionMode.PHYSICAL),
                PrimitiveTask(MissionStep.RUN_UP_CHECKS, CompletionMode.TIMED),
            ),
        )
        val rewritten = root.replaceFromActivePrimitive(
            listOf(PrimitiveTask(MissionStep.DECLINE_DEPARTURE, CompletionMode.NON_COMPLETING)),
        )
        assertEquals(
            listOf(MissionStep.DECLINE_DEPARTURE),
            collectSteps(rewritten),
            "flat / active at index 0: every following sibling is dropped; suffix becomes only child",
        )
    }

    @Test
    fun `flat — completed leading siblings preserved, active primitive replaced from there`() {
        // Receiver: REQUEST_TAXI complete, TAXI_TO_HOLDING active.
        // Active anchor is index 1.
        val root = CompoundTask(
            name = TaskName.GroundDeparture,
            children = listOf(
                PrimitiveTask(MissionStep.REQUEST_TAXI, CompletionMode.INSTRUCTION_GATED, completed = true),
                PrimitiveTask(MissionStep.TAXI_TO_HOLDING, CompletionMode.PHYSICAL),
                PrimitiveTask(MissionStep.RUN_UP_CHECKS, CompletionMode.TIMED),
                PrimitiveTask(MissionStep.AWAIT_LINE_UP, CompletionMode.INSTRUCTION_GATED),
            ),
        )
        val rewritten = root.replaceFromActivePrimitive(
            listOf(PrimitiveTask(MissionStep.DECLINE_DEPARTURE, CompletionMode.NON_COMPLETING)),
        )
        assertEquals(
            listOf(MissionStep.REQUEST_TAXI, MissionStep.DECLINE_DEPARTURE),
            collectSteps(rewritten),
            "flat / active at index 1: completed leading siblings preserved; active + tail replaced",
        )
        // Completed flag on the preserved leading sibling stays true.
        val firstChild = rewritten.children[0] as PrimitiveTask
        assertTrue(firstChild.completed, "preserved REQUEST_TAXI keeps `completed = true`")
    }

    @Test
    fun `nested — active primitive inside inner compound, rewrite at inner level`() {
        // Receiver: CircuitTraining > [groundDeparture COMPLETE, Circuit ACTIVE>
        //     [FLY_DOWNWIND active, REPORT_DOWNWIND, FLY_FINAL...]].
        // Active primitive: FLY_DOWNWIND, inside the Circuit compound.
        // Rewrite suffix: [Report(GoingAround)-ish primitive].
        val groundDep = CompoundTask(
            name = TaskName.GroundDeparture,
            children = listOf(
                PrimitiveTask(MissionStep.REQUEST_TAXI, CompletionMode.INSTRUCTION_GATED, completed = true),
                PrimitiveTask(MissionStep.AWAIT_TAKEOFF_CLEARANCE, CompletionMode.INSTRUCTION_GATED, completed = true),
            ),
        )
        val circuit = CompoundTask(
            name = TaskName.Circuit,
            children = listOf(
                PrimitiveTask(MissionStep.FLY_DOWNWIND, CompletionMode.PHYSICAL),  // ACTIVE
                PrimitiveTask(MissionStep.REPORT_DOWNWIND, CompletionMode.REPORTED),
                PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL),
            ),
        )
        val root = CompoundTask(
            name = TaskName.CircuitTraining,
            children = listOf(groundDep, circuit),
        )
        val rewritten = root.replaceFromActivePrimitive(
            listOf(PrimitiveTask(MissionStep.GOING_AROUND, CompletionMode.REPORTED)),
        )
        // Outer structure preserved (CircuitTraining > [groundDep, circuit]).
        assertEquals(2, rewritten.children.size, "outer compound retains both children")
        // Inner Circuit compound is rewritten: only GOING_AROUND remains.
        val innerCircuit = rewritten.children[1] as CompoundTask
        assertEquals(
            TaskName.Circuit,
            innerCircuit.name,
            "inner compound name preserved by data-class copy",
        )
        assertEquals(
            listOf(MissionStep.GOING_AROUND),
            collectSteps(innerCircuit),
            "inner compound's suffix-from-active is replaced; siblings after active dropped",
        )
        // groundDep is structurally identical to the pre-rewrite version
        // (no side effects on completed-leading siblings).
        assertEquals(groundDep, rewritten.children[0], "completed leading compound unchanged")
    }

    @Test
    fun `nested — multi-element suffix in nested compound (Transit GA shape preview)`() {
        // R22 shape: replaceFromActivePrimitive's nested case must also
        // accept multi-element suffixes (Transit GA = [goAround, circuit,
        // groundArrival]). This pins the contract at fn-28.2 even though
        // fn-28.6/.7 land the Transit dispatch.
        val inner = CompoundTask(
            name = TaskName.Transit,
            children = listOf(
                PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL),
                PrimitiveTask(MissionStep.LAND, CompletionMode.PHYSICAL),
            ),
        )
        val root = CompoundTask(
            name = TaskName.CircuitTraining,
            children = listOf(inner),
        )
        val rewritten = root.replaceFromActivePrimitive(
            listOf(
                goAroundTask(),
                circuitTask(),
                groundArrivalTask(),
            ),
        )
        // Outer wraps a single child (the rewritten inner compound).
        assertEquals(1, rewritten.children.size, "outer compound retains the inner compound slot")
        val rewrittenInner = rewritten.children[0] as CompoundTask
        // Inner Transit now has the new 3-element compound suffix replacing
        // its active primitive's tail.
        assertEquals(
            3,
            rewrittenInner.children.size,
            "inner compound's suffix is the 3-element [GA, Circuit, GroundArrival] list",
        )
        // First two children should be GoAround / Circuit compounds.
        assertTrue(
            (rewrittenInner.children[0] as CompoundTask).name is TaskName.GoAround,
            "suffix index 0 is GoAround",
        )
        assertTrue(
            (rewrittenInner.children[1] as CompoundTask).name is TaskName.Circuit,
            "suffix index 1 is Circuit",
        )
        assertTrue(
            (rewrittenInner.children[2] as CompoundTask).name is TaskName.GroundArrival,
            "suffix index 2 is GroundArrival",
        )
    }

    @Test
    fun `terminal — no active primitive returns the receiver unchanged`() {
        val root = CompoundTask(
            name = TaskName.GroundDeparture,
            children = listOf(
                PrimitiveTask(MissionStep.REQUEST_TAXI, CompletionMode.INSTRUCTION_GATED, completed = true),
                PrimitiveTask(MissionStep.AWAIT_LINE_UP, CompletionMode.INSTRUCTION_GATED, completed = true),
            ),
        )
        val rewritten = root.replaceFromActivePrimitive(
            listOf(PrimitiveTask(MissionStep.DECLINE_DEPARTURE, CompletionMode.NON_COMPLETING)),
        )
        assertEquals(root, rewritten, "no active primitive → receiver returned unchanged")
    }

    @Test
    fun `nested — outer compound siblings AFTER the active inner compound are preserved`() {
        // Edge case: inner active compound at index 1 of three siblings;
        // index 2 (a later sibling) must stay intact.
        val first = CompoundTask(
            name = TaskName.GroundDeparture,
            children = listOf(
                PrimitiveTask(MissionStep.REQUEST_TAXI, CompletionMode.INSTRUCTION_GATED, completed = true),
            ),
        )
        val active = CompoundTask(
            name = TaskName.Circuit,
            children = listOf(
                PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL),
            ),
        )
        val later = CompoundTask(
            name = TaskName.GroundArrival,
            children = listOf(
                PrimitiveTask(MissionStep.TAXI_TO_STAND, CompletionMode.PHYSICAL),
            ),
        )
        val root = CompoundTask(
            name = TaskName.CircuitTraining,
            children = listOf(first, active, later),
        )
        val rewritten = root.replaceFromActivePrimitive(
            listOf(PrimitiveTask(MissionStep.GOING_AROUND, CompletionMode.REPORTED)),
        )
        // Outer structure: three children preserved positionally.
        assertEquals(3, rewritten.children.size, "outer compound retains all three siblings")
        // Index 0: completed compound — unchanged.
        assertEquals(first, rewritten.children[0], "completed leading sibling unchanged")
        // Index 1: rewritten inner compound.
        val rewrittenInner = rewritten.children[1] as CompoundTask
        assertEquals(
            listOf(MissionStep.GOING_AROUND),
            collectSteps(rewrittenInner),
            "active inner compound suffix replaced",
        )
        // Index 2: later compound — unchanged. (Important: the rewrite
        // semantics are "from the active primitive within the inner
        // compound onward"; later outer siblings are FUTURE work and must
        // not be dropped.)
        assertEquals(later, rewritten.children[2], "later sibling preserved (FUTURE work)")
    }
}
