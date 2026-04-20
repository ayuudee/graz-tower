package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.SimTime

/**
 * Hierarchical Task Network for the pilot agent.
 *
 * A mission decomposes a [PilotGoal] into a tree of [Task]s:
 *   - [CompoundTask]: decomposes into ordered children (sequential execution)
 *   - [PrimitiveTask]: leaf node with a specific step, completion mode, and optional transmission
 *
 * Interrupts work through subtree replacement: go-around replaces the current
 * CIRCUIT compound task. Touch-and-go loops the CIRCUIT compound. Extend-downwind
 * scopes a constraint to the DOWNWIND primitive within the circuit subtree.
 */
data class PilotMission(
    val goal: xyz.easiersaid.twr.controller.PilotGoal,
    val root: CompoundTask,
    /** Instructions received from ATC that modify current step behaviour. */
    val activeConstraints: Set<ActiveConstraint> = emptySet(),
    /** Last circuit leg where a position report was made (suppress duplicates). */
    val lastReportedLeg: xyz.easiersaid.twr.core.world.LegName? = null,
    /** Whether initial contact has been made on the current frequency. */
    val contactedOnFrequency: Boolean = false,
    /** Timer for missing-clearance escalation (millis since step entered). */
    val stepEnteredAt: SimTime = SimTime.ZERO,
    /** Active runway for circuit reports. Set from the aircraft's assigned circuit. */
    val activeRunway: xyz.easiersaid.twr.protocol.RunwayId? = null,
) {
    /** The current primitive task being executed (leftmost incomplete leaf). */
    val currentTask: PrimitiveTask? get() = root.currentPrimitive()
    val isComplete: Boolean get() = root.isComplete
}

// ── Task tree ────────────────────────────────────────────────────────

/**
 * A compound task that decomposes into ordered children.
 * Children are executed left-to-right. The compound is complete when all children are complete.
 */
data class CompoundTask(
    val name: String,
    val children: List<TaskNode>,
) : TaskNode {
    init { require(children.isNotEmpty()) { "CompoundTask '$name' must have at least one child" } }
    override val isComplete: Boolean get() = children.all { it.isComplete }

    /** Find the leftmost incomplete primitive leaf. */
    fun currentPrimitive(): PrimitiveTask? {
        for (child in children) {
            if (child.isComplete) continue
            return when (child) {
                is PrimitiveTask -> child
                is CompoundTask -> child.currentPrimitive()
            }
        }
        return null
    }

    /** Replace the first child matching [predicate] with [replacement]. */
    fun replaceChild(predicate: (TaskNode) -> Boolean, replacement: TaskNode): CompoundTask =
        copy(children = children.map { if (predicate(it)) replacement else it })

    /** Mark the current primitive as complete and return the updated tree. */
    fun advanceCurrent(): CompoundTask {
        val current = currentPrimitive() ?: return this
        return markComplete(current.step)
    }

    /** Mark the first incomplete instance of [step] in the tree. Only one node is marked per call. */
    fun markComplete(step: MissionStep): CompoundTask {
        var found = false
        val updated = children.map { child ->
            if (found) return@map child
            when (child) {
                is PrimitiveTask -> if (child.step == step && !child.completed) {
                    found = true; child.copy(completed = true)
                } else child
                is CompoundTask -> {
                    val after = child.markComplete(step)
                    // Check structural equality — copy() creates a new object even if nothing changed
                    if (after != child) found = true
                    after
                }
            }
        }
        return copy(children = updated)
    }
}

/**
 * A primitive (leaf) task with a specific mission step.
 *
 * Each primitive declares its [completionMode] — how it becomes complete:
 *   - PHYSICAL: completes when aircraft reaches a physical state (phase, position)
 *   - REPORTED: completes when a report has been transmitted
 *   - INSTRUCTION_GATED: completes only when a specific ATC instruction arrives
 *   - TIMED: completes after a time delay (e.g., run-up checks)
 *   - INSTANT: completes immediately (structural marker)
 */
data class PrimitiveTask(
    val step: MissionStep,
    val completionMode: CompletionMode,
    val completed: Boolean = false,
) : TaskNode {
    override val isComplete: Boolean get() = completed
}

/** Sealed type for task tree nodes — dispatch via when-is. */
sealed interface TaskNode {
    val isComplete: Boolean
}

enum class CompletionMode {
    /** Completes when aircraft reaches a physical state (phase, position). */
    PHYSICAL,
    /** Completes when a report has been transmitted (lastReportedLeg check). */
    REPORTED,
    /** Completes only when a specific ATC instruction arrives. */
    INSTRUCTION_GATED,
    /** Completes after a time delay. */
    TIMED,
    /** Completes immediately — structural marker. */
    INSTANT,
}

/** An ATC instruction that modifies pilot behaviour without changing the mission plan. */
sealed interface ActiveConstraint {
    data object ExtendingDownwind : ActiveConstraint
    data object Orbiting : ActiveConstraint
    data class SpeedRestriction(val targetKnots: Int) : ActiveConstraint
}

// ── Mission step enum ────────────────────────────────────────────────

enum class MissionStep {
    // Ground (pre-departure)
    REQUEST_STARTUP, AWAIT_STARTUP_APPROVAL, REQUEST_TAXI, TAXI_TO_HOLDING,
    RUN_UP_CHECKS, REPORT_READY, AWAIT_LINE_UP, AWAIT_TAKEOFF_CLEARANCE,
    // Airborne (circuit)
    FLY_DEPARTURE, FLY_DOWNWIND, REPORT_DOWNWIND, AWAIT_SEQUENCING,
    FLY_BASE, REPORT_BASE, FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE,
    // Landing + post-landing
    LAND, REPORT_RUNWAY_VACATED, AWAIT_VACATE_INSTRUCTION, TAXI_TO_STAND, SHUTDOWN,
    // Arrival (pre-circuit)
    CALL_INBOUND, AWAIT_JOINING_INSTRUCTIONS,
    // Special states
    GOING_AROUND, AWAITING_ATC_INSTRUCTION,
}

// ── Task tree construction ───────────────────────────────────────────

/** Build the GROUND_DEPARTURE compound task. */
fun groundDepartureTask(): CompoundTask = CompoundTask("GROUND_DEPARTURE", listOf(
    PrimitiveTask(MissionStep.REQUEST_STARTUP, CompletionMode.INSTRUCTION_GATED),
    PrimitiveTask(MissionStep.AWAIT_STARTUP_APPROVAL, CompletionMode.INSTRUCTION_GATED),
    PrimitiveTask(MissionStep.REQUEST_TAXI, CompletionMode.INSTRUCTION_GATED),
    PrimitiveTask(MissionStep.TAXI_TO_HOLDING, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.RUN_UP_CHECKS, CompletionMode.TIMED),
    PrimitiveTask(MissionStep.REPORT_READY, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.AWAIT_LINE_UP, CompletionMode.INSTRUCTION_GATED),
    PrimitiveTask(MissionStep.AWAIT_TAKEOFF_CLEARANCE, CompletionMode.INSTRUCTION_GATED),
))

/** Build the CIRCUIT compound task (downwind through landing). */
fun circuitTask(): CompoundTask = CompoundTask("CIRCUIT", listOf(
    PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.FLY_DOWNWIND, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.REPORT_DOWNWIND, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.AWAIT_SEQUENCING, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.FLY_BASE, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.REPORT_BASE, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.REPORT_FINAL, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.AWAIT_LANDING_CLEARANCE, CompletionMode.INSTRUCTION_GATED),
    PrimitiveTask(MissionStep.LAND, CompletionMode.PHYSICAL),
))

/** Build the ARRIVAL_JOIN compound task (inbound call + joining). */
fun arrivalJoinTask(): CompoundTask = CompoundTask("ARRIVAL_JOIN", listOf(
    PrimitiveTask(MissionStep.CALL_INBOUND, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.AWAIT_JOINING_INSTRUCTIONS, CompletionMode.INSTRUCTION_GATED),
))

/** Build the GROUND_ARRIVAL compound task. */
fun groundArrivalTask(): CompoundTask = CompoundTask("GROUND_ARRIVAL", listOf(
    PrimitiveTask(MissionStep.REPORT_RUNWAY_VACATED, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.AWAIT_VACATE_INSTRUCTION, CompletionMode.INSTRUCTION_GATED),
    PrimitiveTask(MissionStep.TAXI_TO_STAND, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
))

/** Build the GO_AROUND compound task. */
fun goAroundTask(): CompoundTask = CompoundTask("GO_AROUND", listOf(
    PrimitiveTask(MissionStep.GOING_AROUND, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.AWAITING_ATC_INSTRUCTION, CompletionMode.INSTRUCTION_GATED),
))

/** Decompose a pilot goal into the full HTN tree. */
fun decomposeMission(goal: xyz.easiersaid.twr.controller.PilotGoal): CompoundTask = when (goal) {
    xyz.easiersaid.twr.controller.PilotGoal.DEPART -> CompoundTask("DEPART", listOf(
        groundDepartureTask(),
        PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
    ))
    xyz.easiersaid.twr.controller.PilotGoal.ARRIVE -> CompoundTask("ARRIVE", listOf(
        arrivalJoinTask(),
        circuitTask().let { it.copy(children = it.children.drop(1)) }, // skip FLY_DEPARTURE for arrivals
        groundArrivalTask(),
    ))
    xyz.easiersaid.twr.controller.PilotGoal.TOUCH_AND_GO -> CompoundTask("TOUCH_AND_GO", listOf(
        groundDepartureTask(),
        circuitTask(),
        groundArrivalTask(),
    ))
    xyz.easiersaid.twr.controller.PilotGoal.TRANSIT -> CompoundTask("TRANSIT", listOf(
        PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
    ))
}

/** Create a fresh mission for an aircraft. */
fun createMission(
    goal: xyz.easiersaid.twr.controller.PilotGoal,
    startPhase: PilotPhase,
    time: SimTime,
): PilotMission {
    var root = decomposeMission(goal)
    // Skip completed steps based on starting phase.
    root = skipCompletedSteps(root, startPhase)
    return PilotMission(goal = goal, root = root, stepEnteredAt = time)
}

/** Mark steps as completed that the starting phase has already passed. */
@Suppress("CyclomaticComplexMethod")
private fun skipCompletedSteps(root: CompoundTask, startPhase: PilotPhase): CompoundTask {
    val preStartup = setOf(
        MissionStep.REQUEST_STARTUP, MissionStep.AWAIT_STARTUP_APPROVAL,
    )
    val preTaxi = preStartup + MissionStep.REQUEST_TAXI
    val preHold = preTaxi + MissionStep.TAXI_TO_HOLDING
    val preLineUp = preHold + setOf(
        MissionStep.RUN_UP_CHECKS, MissionStep.REPORT_READY, MissionStep.AWAIT_LINE_UP,
    )
    val preCircuit = setOf(
        MissionStep.CALL_INBOUND, MissionStep.AWAIT_JOINING_INSTRUCTIONS,
        MissionStep.FLY_DEPARTURE, MissionStep.FLY_DOWNWIND,
    )
    val preBase = preCircuit + setOf(
        MissionStep.REPORT_DOWNWIND, MissionStep.AWAIT_SEQUENCING, MissionStep.FLY_BASE,
    )
    val preFinal = preBase + setOf(MissionStep.REPORT_BASE, MissionStep.FLY_FINAL)
    val preLand = preFinal + setOf(
        MissionStep.REPORT_FINAL, MissionStep.AWAIT_LANDING_CLEARANCE, MissionStep.LAND,
    )

    val stepsToSkip = when (startPhase) {
        is PilotPhase.AtStand -> emptySet()
        is PilotPhase.Taxiing -> preTaxi
        is PilotPhase.HoldingShort -> preHold
        is PilotPhase.LinedUp -> preLineUp
        is PilotPhase.Downwind -> preCircuit
        is PilotPhase.Base -> preBase
        is PilotPhase.Final -> preFinal
        is PilotPhase.LandingRoll -> preLand
        else -> emptySet()
    }
    return markStepsComplete(root, stepsToSkip)
}

private fun markStepsComplete(task: CompoundTask, steps: Set<MissionStep>): CompoundTask =
    task.copy(children = task.children.map { child ->
        when (child) {
            is PrimitiveTask -> if (child.step in steps) child.copy(completed = true) else child
            is CompoundTask -> markStepsComplete(child, steps)
        }
    })
