package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.protocol.SimTime

/**
 * High-level goal given to the pilot at spawn. The pilot's planner decomposes
 * this into an HTN task tree. Richer than [PilotGoal] — carries parameters
 * like circuit count that the controller doesn't need to see.
 */
sealed interface HighLevelGoal {
    data class Departure(val destination: xyz.easiersaid.twr.protocol.AerodromeId? = null) : HighLevelGoal
    data class Arrival(val from: xyz.easiersaid.twr.protocol.AerodromeId? = null) : HighLevelGoal
    data class CircuitTraining(val circuits: Int, val fullStopOnLast: Boolean = true) : HighLevelGoal {
        init { require(circuits > 0) { "CircuitTraining requires at least 1 circuit" } }
    }
    data class Transit(val destination: xyz.easiersaid.twr.protocol.AerodromeId? = null) : HighLevelGoal
}

/**
 * Hierarchical Task Network for the pilot agent.
 *
 * A mission decomposes a [HighLevelGoal] into a tree of [Task]s:
 *   - [CompoundTask]: decomposes into ordered children (sequential execution)
 *   - [PrimitiveTask]: leaf node with a specific step, completion mode, and optional transmission
 *
 * Interrupts work through subtree replacement: go-around replaces the current
 * CIRCUIT compound task. Touch-and-go loops the CIRCUIT compound. Extend-downwind
 * scopes a constraint to the DOWNWIND primitive within the circuit subtree.
 */
data class PilotMission(
    val goal: HighLevelGoal,
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
    /** Set true when pilot has reported runway vacated. Used by REPORT_RUNWAY_VACATED completion. */
    val reportedVacated: Boolean = false,
    /** Set true when ClearedToLand/ClearedTouchAndGo received. Used by go-around decision. */
    val hasClearance: Boolean = false,
) {
    /** The current primitive task being executed (leftmost incomplete leaf). */
    val currentTask: PrimitiveTask? get() = root.currentPrimitive()
    val isComplete: Boolean get() = root.isComplete
}

// ── Task tree ────────────────────────────────────────────────────────

/**
 * Typed task name for compound tasks. Eliminates stringly-typed dispatch
 * in [derivePilotGoal] and go-around subtree replacement.
 */
sealed interface TaskName {
    data object Depart : TaskName
    data object Arrive : TaskName
    data object TouchAndGo : TaskName
    data object Transit : TaskName
    data object GroundDeparture : TaskName
    data object GroundArrival : TaskName
    data object Circuit : TaskName
    data object CircuitAfterGoAround : TaskName
    data object CircuitTraining : TaskName
    data object ArrivalJoin : TaskName
    data object GoAround : TaskName
}

/**
 * A compound task that decomposes into ordered children.
 * Children are executed left-to-right. The compound is complete when all children are complete.
 */
data class CompoundTask(
    val name: TaskName,
    val children: List<TaskNode>,
) : TaskNode {
    init { require(children.isNotEmpty()) { "CompoundTask '${name::class.simpleName}' must have at least one child" } }
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
fun groundDepartureTask(): CompoundTask = CompoundTask(TaskName.GroundDeparture, listOf(
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
fun circuitTask(): CompoundTask = CompoundTask(TaskName.Circuit, listOf(
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
fun arrivalJoinTask(): CompoundTask = CompoundTask(TaskName.ArrivalJoin, listOf(
    PrimitiveTask(MissionStep.CALL_INBOUND, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.AWAIT_JOINING_INSTRUCTIONS, CompletionMode.INSTRUCTION_GATED),
))

/** Build the GROUND_ARRIVAL compound task. */
fun groundArrivalTask(): CompoundTask = CompoundTask(TaskName.GroundArrival, listOf(
    PrimitiveTask(MissionStep.REPORT_RUNWAY_VACATED, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.AWAIT_VACATE_INSTRUCTION, CompletionMode.INSTRUCTION_GATED),
    PrimitiveTask(MissionStep.TAXI_TO_STAND, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
))

/** Build the GO_AROUND compound task. */
fun goAroundTask(): CompoundTask = CompoundTask(TaskName.GoAround, listOf(
    PrimitiveTask(MissionStep.GOING_AROUND, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.AWAITING_ATC_INSTRUCTION, CompletionMode.INSTRUCTION_GATED),
))

/**
 * Pilot planner: decompose a [HighLevelGoal] into the full HTN tree.
 *
 * This is the pilot's planning function — it decides HOW to achieve the goal.
 * The controller never sees this tree; it only sees [derivePilotGoal] which
 * produces the controller-visible [PilotGoal] from the current mission state.
 */
fun planMission(goal: HighLevelGoal): CompoundTask = when (goal) {
    is HighLevelGoal.Departure -> CompoundTask(TaskName.Depart, listOf(
        groundDepartureTask(),
        PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
    ))
    is HighLevelGoal.Arrival -> CompoundTask(TaskName.Arrive, listOf(
        arrivalJoinTask(),
        circuitTask().let { it.copy(children = it.children.drop(1)) }, // skip FLY_DEPARTURE for arrivals
        groundArrivalTask(),
    ))
    is HighLevelGoal.CircuitTraining -> {
        val circuitTasks = (1..goal.circuits).map { i ->
            val isLast = i == goal.circuits && goal.fullStopOnLast
            if (isLast) circuitTask() // full stop: includes LAND
            else touchAndGoCircuitTask() // T&G: includes LAND then lifts off
        }
        CompoundTask(TaskName.CircuitTraining, listOf(groundDepartureTask()) + circuitTasks + listOf(groundArrivalTask()))
    }
    is HighLevelGoal.Transit -> CompoundTask(TaskName.Transit, listOf(
        PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
    ))
}

/** Build a T&G circuit: fly the pattern, land, then take off again. */
fun touchAndGoCircuitTask(): CompoundTask = CompoundTask(TaskName.Circuit, listOf(
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
    // After landing, the aircraft lifts off again for the next circuit.
    // The controller sees PilotGoal.TOUCH_AND_GO and ARR-TNG-AIRBORNE fires.
    PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
))

/**
 * Derive the controller-visible [PilotGoal] from the current mission state.
 *
 * The controller sees this goal and responds: DEPART → departure procedure,
 * TOUCH_AND_GO → T&G clearances, ARRIVE → landing clearance + vacate.
 * The pilot's internal [HighLevelGoal] and mission tree remain opaque.
 */
fun derivePilotGoal(mission: PilotMission): PilotGoal {
    val activeCompound = mission.root.activeCompound()?.name
    return when {
        activeCompound is TaskName.GroundDeparture -> PilotGoal.DEPART
        activeCompound is TaskName.GroundArrival -> PilotGoal.ARRIVE
        activeCompound is TaskName.ArrivalJoin -> PilotGoal.ARRIVE
        // Circuit: T&G unless this is the last circuit of a CircuitTraining with fullStopOnLast
        activeCompound is TaskName.Circuit && isLastActiveCircuit(mission) -> PilotGoal.ARRIVE
        activeCompound is TaskName.Circuit -> PilotGoal.TOUCH_AND_GO
        activeCompound is TaskName.CircuitAfterGoAround -> PilotGoal.TOUCH_AND_GO
        activeCompound is TaskName.GoAround -> PilotGoal.TOUCH_AND_GO
        else -> PilotGoal.DEPART
    }
}

/** Is the currently active CIRCUIT the last one in a CircuitTraining mission? */
private fun isLastActiveCircuit(mission: PilotMission): Boolean {
    if (mission.goal !is HighLevelGoal.CircuitTraining) return false
    val circuits = mission.root.children.filterIsInstance<CompoundTask>()
        .filter { it.name is TaskName.Circuit || it.name is TaskName.CircuitAfterGoAround }
    val activeIndex = circuits.indexOfFirst { !it.isComplete }
    return activeIndex == circuits.lastIndex
}

/** Find the active (leftmost incomplete) compound task at the top level. */
fun CompoundTask.activeCompound(): CompoundTask? {
    for (child in children) {
        if (child.isComplete) continue
        return when (child) {
            is CompoundTask -> child
            is PrimitiveTask -> null // primitive at top level, no compound
        }
    }
    return null
}

/** Create a fresh mission for an aircraft. */
fun createMission(
    goal: HighLevelGoal,
    startPhase: PilotPhase,
    time: SimTime,
): PilotMission {
    var root = planMission(goal)
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
