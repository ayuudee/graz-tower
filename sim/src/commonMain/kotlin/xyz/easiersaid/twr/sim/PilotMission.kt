package xyz.easiersaid.twr.sim

import arrow.core.None
import arrow.core.Option
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
    // ── Structural (set at creation, rarely mutated) ────────────────
    val goal: HighLevelGoal,
    val root: CompoundTask,
    /** How the pilot derives their route — set at creation, updated on ATC amendments. */
    val navigationMode: NavigationMode? = null,
    /** Active runway for circuit reports. Set from the aircraft's assigned circuit. */
    val activeRunway: xyz.easiersaid.twr.protocol.RunwayId? = null,

    // ── Cross-cutting (set by ATC at any time, reset depends on context) ──
    /** Instructions received from ATC that modify current step behaviour. */
    val activeConstraints: Set<ActiveConstraint> = emptySet(),
    /** Temporary route replacement — suspends FPL-based routing when active. */
    val routeOverride: RouteOverride? = null,
    /** Whether initial contact has been made on the current frequency. */
    val contactedOnFrequency: Boolean = false,

    // ── Phase-local (reset on go-around — see resetForGoAround) ────
    /** Timer for missing-clearance escalation (millis since step entered). */
    val stepEnteredAt: SimTime = SimTime.ZERO,
    /** Last circuit leg where a position report was made (suppress duplicates). */
    val lastReportedLeg: xyz.easiersaid.twr.core.world.LegName? = null,
    /** Set true when pilot has reported runway vacated. Used by REPORT_RUNWAY_VACATED completion. */
    val reportedVacated: Boolean = false,
    /** Set true when ClearedToLand/ClearedTouchAndGo received. Used by go-around decision. */
    val hasClearance: Boolean = false,
    /**
     * Circuit leg where an arriving aircraft was instructed to join, from [JoinCircuit].
     *
     * [None] means no join instruction has been received yet — route planner defaults to
     * [LegName.DOWNWIND]. Set when [processInstruction] handles [JoinCircuit]; reset to
     * [None] on go-around (the aircraft re-enters from the go-around path, not the original join).
     */
    val joinLeg: Option<xyz.easiersaid.twr.core.world.LegName> = None,

    /**
     * ATC-issued altitude restriction in metres, from [StopClimbAt].
     *
     * When set, the route planner caps [PilotRoute.Airborne.targetAltitudeM] at this value.
     * The pilot will level off at the restriction rather than climbing to full circuit altitude.
     * Null means unrestricted — pilot climbs to the procedure-defined altitude.
     *
     * Reset on go-around: the aircraft is climbing away from the runway and the approach
     * phase restrictions no longer apply.
     */
    val altitudeRestrictionM: Double? = null,
) {
    /** The current primitive task being executed (leftmost incomplete leaf). */
    val currentTask: PrimitiveTask? get() = root.currentPrimitive()
    val isComplete: Boolean get() = root.isComplete
}

/**
 * Reset phase-local state for go-around. The tree (root) is handled
 * separately by subtree replacement in [processInstruction].
 *
 * Every phase-local field is explicitly listed here. When a new field
 * is added to [PilotMission], the exhaustive field test
 * (PilotCognitiveTest.`resetForGoAround covers every PilotMission field`)
 * will fail until the developer decides the go-around behavior for that field.
 */
fun PilotMission.resetForGoAround(now: SimTime): PilotMission = copy(
    // Phase-local — reset to defaults.
    stepEnteredAt = now,
    lastReportedLeg = null,
    reportedVacated = false,
    hasClearance = false,
    // Cross-cutting — reset on go-around (extend-downwind and vectors are
    // invalidated by the approach abort).
    activeConstraints = emptySet(),
    routeOverride = null,
    // joinLeg: reset — go-around re-enters circuit from go-around path, not original join.
    joinLeg = None,
    // altitudeRestrictionM: reset — go-around discards approach-phase level restrictions.
    altitudeRestrictionM = null,
    // Structural + cross-cutting — preserved.
    // goal: unchanged (still the same mission)
    // root: handled by caller (subtree replacement)
    // navigationMode: unchanged (still VFR/IFR)
    // activeRunway: unchanged (same runway)
    // contactedOnFrequency: unchanged (still on same frequency)
)

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
    fun replaceChild(predicate: (TaskNode) -> Boolean, replacement: TaskNode): CompoundTask {
        var replaced = false
        return copy(children = children.map { child ->
            if (!replaced && predicate(child)) { replaced = true; replacement } else child
        })
    }

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

/**
 * A temporary route replacement — suspends FPL-based routing.
 *
 * Distinct from [ActiveConstraint]: constraints modify HOW the pilot follows
 * their route; overrides REPLACE the route entirely. When the override is
 * cleared (resume own navigation / leave hold), FPL-based routing resumes.
 */
sealed interface RouteOverride {
    /** Pilot flies a heading assigned by ATC (radar vectors). */
    data class Vectoring(val heading: xyz.easiersaid.twr.protocol.Heading) : RouteOverride
    /** Pilot flies a holding pattern at a fix. */
    data class Holding(val hold: xyz.easiersaid.twr.protocol.HoldSpec) : RouteOverride
}

// ── Mission step enum ────────────────────────────────────────────────

enum class MissionStep {
    // Ground (pre-departure)
    REQUEST_STARTUP, AWAIT_STARTUP_APPROVAL, REQUEST_TAXI, TAXI_TO_HOLDING,
    RUN_UP_CHECKS, REPORT_READY, AWAIT_LINE_UP, AWAIT_TAKEOFF_CLEARANCE,
    // Airborne (VFR circuit)
    FLY_DEPARTURE, FLY_DOWNWIND, REPORT_DOWNWIND, AWAIT_SEQUENCING,
    FLY_BASE, REPORT_BASE, FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE,
    // Airborne (IFR)
    FLY_SID, FLY_EN_ROUTE, FLY_STAR, FLY_APPROACH, FLY_MISSED_APPROACH,
    // Landing + post-landing
    LAND, REPORT_RUNWAY_VACATED, AWAIT_VACATE_INSTRUCTION, TAXI_TO_STAND, SHUTDOWN,
    // Arrival (pre-circuit)
    CALL_INBOUND, AWAIT_JOINING_INSTRUCTIONS,
    // Special states
    GOING_AROUND, AWAITING_ATC_INSTRUCTION,
}

// ── Task tree construction ───────────────────────────────────────────

/**
 * Build the GROUND_DEPARTURE compound task.
 *
 * For AI aircraft ([humanPiloted] = false), startup and taxi request steps are
 * omitted — the ground controller acts proactively via [AiProactive] and issues
 * TaxiTo without waiting for a request. Including these steps for AI would cause
 * step-on collisions on the frequency (pilot transmits request at the same time
 * the controller transmits the proactive instruction).
 */
fun groundDepartureTask(humanPiloted: Boolean = true): CompoundTask {
    val steps = buildList {
        if (humanPiloted) {
            add(PrimitiveTask(MissionStep.REQUEST_STARTUP, CompletionMode.INSTRUCTION_GATED))
            add(PrimitiveTask(MissionStep.AWAIT_STARTUP_APPROVAL, CompletionMode.INSTRUCTION_GATED))
            add(PrimitiveTask(MissionStep.REQUEST_TAXI, CompletionMode.INSTRUCTION_GATED))
        }
        add(PrimitiveTask(MissionStep.TAXI_TO_HOLDING, CompletionMode.PHYSICAL))
        add(PrimitiveTask(MissionStep.RUN_UP_CHECKS, CompletionMode.TIMED))
        add(PrimitiveTask(MissionStep.REPORT_READY, CompletionMode.REPORTED))
        add(PrimitiveTask(MissionStep.AWAIT_LINE_UP, CompletionMode.INSTRUCTION_GATED))
        add(PrimitiveTask(MissionStep.AWAIT_TAKEOFF_CLEARANCE, CompletionMode.INSTRUCTION_GATED))
    }
    return CompoundTask(TaskName.GroundDeparture, steps)
}

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

/**
 * Build the GO_AROUND compound task (VFR — rejoin circuit).
 *
 * VFR go-arounds are autonomous: the pilot reports "going around" then
 * re-enters the circuit independently. No AWAITING_ATC_INSTRUCTION step —
 * that would block the pilot until ATC re-sequences, which is correct for
 * IFR (see [ifrGoAroundTask]) but wrong for VFR where ATC may only say
 * "go around, report downwind" and the pilot self-navigates from there.
 *
 * Used for both self-initiated and ATC-instructed VFR go-arounds.
 */
fun goAroundTask(): CompoundTask = CompoundTask(TaskName.GoAround, listOf(
    PrimitiveTask(MissionStep.GOING_AROUND, CompletionMode.REPORTED),
))

/** Build the GO_AROUND compound task (IFR — fly published missed approach). */
fun ifrGoAroundTask(): CompoundTask = CompoundTask(TaskName.GoAround, listOf(
    PrimitiveTask(MissionStep.GOING_AROUND, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.FLY_MISSED_APPROACH, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.AWAITING_ATC_INSTRUCTION, CompletionMode.INSTRUCTION_GATED),
))

/**
 * Pilot planner: decompose a [HighLevelGoal] into the full HTN tree.
 *
 * This is the pilot's planning function — it decides HOW to achieve the goal.
 * The controller never sees this tree; it only sees [derivePilotGoal] which
 * produces the controller-visible [PilotGoal] from the current mission state.
 */
fun planMission(goal: HighLevelGoal, humanPiloted: Boolean = true, ifr: Boolean = false): CompoundTask = when (goal) {
    is HighLevelGoal.Departure -> if (!ifr) CompoundTask(TaskName.Depart, listOf(
        groundDepartureTask(humanPiloted),
        PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
    )) else CompoundTask(TaskName.Depart, listOf(
        groundDepartureTask(humanPiloted),
        PrimitiveTask(MissionStep.FLY_SID, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.FLY_EN_ROUTE, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
    ))
    is HighLevelGoal.Arrival -> if (!ifr) CompoundTask(TaskName.Arrive, listOf(
        arrivalJoinTask(),
        circuitTask().let { it.copy(children = it.children.drop(1)) }, // skip FLY_DEPARTURE for arrivals
        groundArrivalTask(),
    )) else CompoundTask(TaskName.Arrive, listOf(
        PrimitiveTask(MissionStep.FLY_STAR, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.FLY_APPROACH, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.LAND, CompletionMode.PHYSICAL),
        groundArrivalTask(),
    ))
    is HighLevelGoal.CircuitTraining -> {
        // Circuit training is always VFR — ifr parameter is ignored.
        val circuitTasks = (1..goal.circuits).map { i ->
            val isLast = i == goal.circuits && goal.fullStopOnLast
            if (isLast) circuitTask() // full stop: includes LAND
            else touchAndGoCircuitTask() // T&G: includes LAND then lifts off
        }
        CompoundTask(TaskName.CircuitTraining,
            listOf(groundDepartureTask(humanPiloted)) + circuitTasks + listOf(groundArrivalTask()))
    }
    is HighLevelGoal.Transit -> if (!ifr) CompoundTask(TaskName.Transit, listOf(
        PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
    )) else CompoundTask(TaskName.Transit, listOf(
        PrimitiveTask(MissionStep.FLY_SID, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.FLY_EN_ROUTE, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
    ))
}

/** Build a T&G circuit: fly the pattern, land, then take off again. */
fun touchAndGoCircuitTask(): CompoundTask = CompoundTask(TaskName.TouchAndGo, listOf(
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
    val activeCompound = mission.root.activeCompound()
    // Exhaustive over TaskName — compiler forces a decision when new TaskNames are added.
    return when (val name = activeCompound?.name) {
        is TaskName.GroundDeparture -> PilotGoal.DEPART
        is TaskName.GroundArrival -> PilotGoal.ARRIVE
        is TaskName.ArrivalJoin -> PilotGoal.ARRIVE
        is TaskName.Circuit ->
            if (isLastActiveCircuit(mission)) PilotGoal.ARRIVE else PilotGoal.TOUCH_AND_GO
        is TaskName.CircuitAfterGoAround -> {
            // CircuitAfterGoAround contains [goAroundTask, circuitTask(full-stop)].
            // During the go-around phase (GoAround subtask active), the aircraft is
            // climbing away — TOUCH_AND_GO drives circuit sequencing from the controller.
            // Once the go-around completes, the inner Circuit subtask is always a full-stop
            // landing — switch to ARRIVE so the controller issues a landing clearance and,
            // after touchdown, a vacate instruction.
            val innerActive = activeCompound!!.activeCompound()
            if (innerActive?.name is TaskName.GoAround) PilotGoal.TOUCH_AND_GO else PilotGoal.ARRIVE
        }
        is TaskName.GoAround -> PilotGoal.TOUCH_AND_GO
        is TaskName.Arrive -> PilotGoal.ARRIVE
        is TaskName.TouchAndGo -> PilotGoal.TOUCH_AND_GO
        is TaskName.Depart -> PilotGoal.DEPART
        is TaskName.Transit -> PilotGoal.TRANSIT
        // CircuitTraining is structurally never the active compound — it's always
        // the root, and activeCompound() returns its first incomplete child. Defensive
        // fallback rather than crash if invariant is violated.
        is TaskName.CircuitTraining -> PilotGoal.TOUCH_AND_GO
        // No active compound: either (a) all compound children complete (mission done), or
        // (b) the first incomplete top-level child is a PrimitiveTask. Fall back to the
        // mission's HighLevelGoal — the most semantically correct goal for this aircraft.
        null -> when (mission.goal) {
            is HighLevelGoal.Arrival -> PilotGoal.ARRIVE
            is HighLevelGoal.Departure -> PilotGoal.DEPART
            is HighLevelGoal.CircuitTraining -> PilotGoal.TOUCH_AND_GO
            is HighLevelGoal.Transit -> PilotGoal.TRANSIT
        }
    }
}

/**
 * True for circuit task names that can be active during a circuit pattern.
 *
 * Used in go-around replacement to find the active circuit task regardless
 * of whether it's a full-stop or touch-and-go circuit. Intentionally includes
 * [TaskName.TouchAndGo] — a go-around during a T&G approach replaces that task.
 *
 * Note: [isLastActiveCircuit] uses a *narrower* filter (`Circuit | CircuitAfterGoAround`
 * only) because only full-stop circuits contribute to the "last circuit" check;
 * T&G circuits always produce [PilotGoal.TOUCH_AND_GO] directly without going
 * through that path.
 */
fun TaskName.isCircuitLike(): Boolean = when (this) {
    is TaskName.Circuit, is TaskName.CircuitAfterGoAround, is TaskName.TouchAndGo -> true
    is TaskName.Depart, is TaskName.Arrive, is TaskName.Transit,
    is TaskName.GroundDeparture, is TaskName.GroundArrival,
    is TaskName.CircuitTraining, is TaskName.ArrivalJoin,
    is TaskName.GoAround -> false
}

/** Is the currently active CIRCUIT the last one in a CircuitTraining mission? */
private fun isLastActiveCircuit(mission: PilotMission): Boolean {
    if (mission.goal !is HighLevelGoal.CircuitTraining) return false
    // Only full-stop circuits (Circuit | CircuitAfterGoAround) contribute here.
    // TouchAndGo tasks are excluded: they always derive TOUCH_AND_GO directly.
    val circuits = mission.root.children.filterIsInstance<CompoundTask>()
        .filter {
            when (it.name) {
                is TaskName.Circuit, is TaskName.CircuitAfterGoAround -> true
                is TaskName.TouchAndGo, is TaskName.Depart, is TaskName.Arrive,
                is TaskName.Transit, is TaskName.GroundDeparture, is TaskName.GroundArrival,
                is TaskName.CircuitTraining, is TaskName.ArrivalJoin,
                is TaskName.GoAround -> false
            }
        }
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
    humanPiloted: Boolean = true,
): PilotMission {
    var root = planMission(goal, humanPiloted)
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
    val preAirborne = preLineUp + MissionStep.AWAIT_TAKEOFF_CLEARANCE
    val preCircuit = preAirborne + setOf(
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
        is PilotPhase.AtStand, is PilotPhase.Parked -> emptySet()
        is PilotPhase.Taxiing -> preTaxi
        is PilotPhase.HoldingShort -> preHold
        is PilotPhase.LinedUp -> preLineUp
        is PilotPhase.Downwind -> preCircuit
        is PilotPhase.Base -> preBase
        is PilotPhase.Final -> preFinal
        is PilotPhase.LandingRoll -> preLand
        // Mid-departure: skip ground + departure steps. The pilot is already airborne.
        is PilotPhase.TakeoffRoll, is PilotPhase.Climbing, is PilotPhase.Crosswind ->
            preLineUp + MissionStep.AWAIT_TAKEOFF_CLEARANCE
        // Mid-vacate: skip through landing. The pilot is exiting the runway.
        is PilotPhase.Vacating, is PilotPhase.ClearOfRunway -> preLand
    }
    return markStepsComplete(root, stepsToSkip)
}

/** Mark steps as complete, but only in the first incomplete subtree — don't touch future circuits. */
private fun markStepsComplete(task: CompoundTask, steps: Set<MissionStep>): CompoundTask {
    var reachedIncomplete = false
    return task.copy(children = task.children.map { child ->
        when (child) {
            is PrimitiveTask -> if (child.step in steps) child.copy(completed = true) else child
            is CompoundTask -> if (child.isComplete && !reachedIncomplete) {
                child // skip completed compounds
            } else {
                reachedIncomplete = true
                markStepsComplete(child, steps)
            }
        }
    })
}
