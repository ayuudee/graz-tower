package xyz.easiersaid.twr.pilot

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import xyz.easiersaid.twr.protocol.SimTime

/**
 * High-level goal given to the pilot at spawn. The pilot's planner decomposes
 * this into an HTN task tree. Pilot-internal — never crosses the firewall to
 * the controller; the controller learns broad service intent from the
 * [xyz.easiersaid.twr.sim.FlightStrip] back-channel and per-circuit intent
 * from the pilot's downwind radio call.
 *
 * fn-11.1: [CircuitTraining] now carries a typed list of [CircuitOutcome] —
 * the per-circuit outcome the pilot's training plan dictates (touch-and-go /
 * full-stop / planned go-around). Replaces the old `(circuits, fullStopOnLast)`
 * constructor which couldn't express a planned go-around outcome at all.
 * The mapping is lossless for the existing scenarios:
 *  - old `(circuits=N, fullStopOnLast=true)` ≡ new
 *    `List(N - 1) { TouchAndGo } + listOf(FullStop)`.
 *  - old `(circuits=1)` ≡ new `listOf(FullStop)`.
 *  - `fullStopOnLast=false` had no call sites; the new shape's terminal-
 *    `FullStop` invariant excludes airborne-terminal sessions outright (they
 *    would silently wedge `groundArrivalTask`).
 */
sealed interface HighLevelGoal {
    data class Departure(val destination: xyz.easiersaid.twr.protocol.AerodromeId? = null) : HighLevelGoal
    data class Arrival(val from: xyz.easiersaid.twr.protocol.AerodromeId? = null) : HighLevelGoal

    /**
     * Circuit training — the pilot's instructor-authored per-circuit plan,
     * one [CircuitOutcome] per circuit. List length determines circuit count.
     *
     * Invariants (enforced by [init]):
     *  - non-empty (at least one circuit).
     *  - terminal outcome must be [CircuitOutcome.FullStop] — the mission
     *    appends `groundArrivalTask()` after the last circuit, which expects
     *    the aircraft to be on the ground. Terminating airborne (TouchAndGo
     *    or GoAround) would silently wedge that step.
     */
    data class CircuitTraining(val outcomes: List<CircuitOutcome>) : HighLevelGoal {
        init {
            require(outcomes.isNotEmpty()) { "CircuitTraining requires at least 1 outcome" }
            require(outcomes.last() == CircuitOutcome.FullStop) {
                "CircuitTraining must terminate with FullStop (got ${outcomes.last()})"
            }
        }
    }
    data class Transit(val destination: xyz.easiersaid.twr.protocol.AerodromeId? = null) : HighLevelGoal
}

/**
 * Per-circuit outcome the pilot's training plan dictates — one element per
 * circuit in [HighLevelGoal.CircuitTraining.outcomes]. The pilot's mission-
 * tree compiler ([planMission]) decomposes each outcome into a per-circuit
 * compound:
 *  - [TouchAndGo] → [touchAndGoCircuitTask] (lands then lifts off again).
 *  - [FullStop] → [circuitTask] (lands and rolls out — the only outcome
 *    permitted as the terminal element).
 *  - [GoAround] → [plannedGoAroundCircuitTask] (flies down to short-final
 *    altitude, transmits "going around" per CAP 413 §4.67, climbs out via
 *    the published go-around path, re-enters the circuit). Doctrinally
 *    faithful to flight-school training: the instructor pre-arranges a
 *    go-around at short-final to exercise the procedure without the pilot
 *    needing a sensor trigger.
 *
 * Sealed `data object` leaves: payload-free today; future extensions (e.g.
 * per-circuit altitude target, per-circuit runway selection) can be added
 * by widening a leaf to `data class` without breaking call sites.
 */
sealed interface CircuitOutcome {
    data object TouchAndGo : CircuitOutcome
    data object FullStop : CircuitOutcome
    data object GoAround : CircuitOutcome
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
    val navigationMode: Option<NavigationMode> = None,
    /**
     * Active runway and the clearance class that assigned it. Populated only
     * by [processInstruction] from radio-derived sources: TaxiTo (via the
     * holding-point→runway reverse lookup), LineUpAndWait, ClearedForTakeoff,
     * ClearedToLand, ClearedTouchAndGo, BacktrackRunway. [None] until the
     * controller speaks — the pilot is genuinely silent at sim init, no peek
     * at the controller's commitment ledger.
     *
     * Phase C of the pilot-firewall plan (`/home/andrew/.claude/plans/pilot-firewall.md`).
     *
     * Pass 5 (D-PF.2 closure): the carrier is now [RunwayAssignment]
     * (runway + source). Updates apply [applyPrecedence] so anomalous
     * orderings (e.g. a `Takeoff` clearance arriving while the prior
     * `Land` was for a different runway) are detected and surfaced rather
     * than silently overwritten.
     *
     * G2 (D-PF.3, Option B): the type widens to
     * [xyz.easiersaid.twr.protocol.RunwayAssignment]<[xyz.easiersaid.twr.protocol.RunwayAssignmentSource]>
     * so it can carry either a [RunwayAssignmentSource.Filing] (init-only,
     * from `createMission(filedPlan = …)`) or a [RunwayAssignmentSource.Radio]
     * (radio-derived). The parametric type lets [applyPrecedence] enforce
     * "Filing-as-`new` is impossible" at compile time.
     */
    val activeRunway: Option<xyz.easiersaid.twr.protocol.RunwayAssignment<xyz.easiersaid.twr.protocol.RunwayAssignmentSource>> = None,

    // ── Cross-cutting (set by ATC at any time, reset depends on context) ──
    /** Instructions received from ATC that modify current step behaviour. */
    val activeConstraints: Set<ActiveConstraint> = emptySet(),
    /** Temporary route replacement — suspends FPL-based routing when active. */
    val routeOverride: Option<RouteOverride> = None,
    /** Whether initial contact has been made on the current frequency. */
    val contactedOnFrequency: Boolean = false,

    /**
     * The role the pilot was last told to contact via [ContactFrequency].
     *
     * Pass 7 (D-AUDIT.5): the responsibility transition fires on
     * `Utterance.FromPilot(InitialContact(stationCalled=X))` — *which* role
     * X is determined by what `ContactFrequency` instruction the pilot
     * most recently received. Pre-Pass-7, `CALL_INBOUND` hardcoded TOWER;
     * post-Pass-7 it reads this field for the contextual role.
     *
     * Set by [processInstruction] on `ContactFrequency(role)`; cleared by
     * [updateAfterTransmission] when the matching `InitialContact` is
     * transmitted. [None] before the first ContactFrequency.
     *
     * G2 cross-aerodrome path: this field stays [None] for the autonomous
     * arrival; CALL_INBOUND falls back to `RoleName.TOWER` (D-G2.7 — destination
     * tower-role lookup is hardcoded; future scope reads it from the procedure's
     * `contactRequirement`).
     */
    val pendingInitialContactRole: Option<xyz.easiersaid.twr.protocol.RoleName> = None,

    /**
     * Filed flight plan — the pilot's pre-flight briefing material.
     *
     * G2 (Phase C): the route planner reads this to know the destination
     * aerodrome and, by way of the destination's published procedures, the
     * route's terminal waypoint. Real pilots have this as a paper or digital
     * document; in the model it is supplied to [createMission] at sim-init.
     *
     * Stored on the mission rather than only consumed by `createMission`
     * because Phase C's route planner needs cross-tick access (re-planning
     * on FPL amendment, deferred per D-G2.4, will read this).
     *
     * Per the per-class quotient rule (D-PF.4), [PilotMission] uses [Option]
     * for nullable fields; [createMission]'s `T?` parameter crosses the
     * boundary to `Option` here.
     */
    val filedPlan: Option<xyz.easiersaid.twr.protocol.FiledPlan> = None,

    /**
     * G2 (Phase C): the destination's first published REP, resolved by the
     * route planner on the first `Transit + FLY_DEPARTURE` planning tick.
     * Read by `isPhysicallyComplete`'s FLY_DEPARTURE arm to detect cruise
     * completion; read by `planRoute` itself to short-circuit re-resolution
     * on subsequent ticks.
     *
     * **Order of operations on the first tick:** `pilotCognitiveDecide`
     * (and therefore `isPhysicallyComplete`) runs BEFORE `planRoute` in
     * `pilotDecide`. On tick 1 the slice is [None]; the cognitive layer's
     * Transit-equality arm evaluates `mission.transitContactRep == Some(positionPoint)`
     * which is `false` on `None`, so FLY_DEPARTURE does not complete
     * prematurely. The planner then resolves the REP and writes it via
     * `PlanRouteOutcome.Plan.mission`; tick 2's cognitive layer sees
     * `Some(rep)` and the equality fires when (and only when) the aircraft
     * has reached the REP.
     *
     * **Set once.** [None] for non-Transit missions, or before the planner
     * has resolved on the first relevant tick. Once `Some`, stable for the
     * mission's lifetime — D-G2.4 (fluid replanning) covers the future case
     * where a goal-destination change must clear this slice.
     */
    val transitContactRep: Option<xyz.easiersaid.twr.protocol.PointId> = None,

    // ── Phase-local (reset on go-around — see resetForGoAround) ────
    /** Timer for missing-clearance escalation (millis since step entered). */
    val stepEnteredAt: SimTime = SimTime.ZERO,
    /** Last circuit leg where a position report was made (suppress duplicates). */
    val lastReportedLeg: Option<xyz.easiersaid.twr.core.world.LegName> = None,
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
     * When [Some], the route planner caps [PilotRoute.Airborne.targetAltitudeM] at this value.
     * The pilot will level off at the restriction rather than climbing to full circuit altitude.
     * [None] means unrestricted — pilot climbs to the procedure-defined altitude.
     *
     * Reset on go-around: the aircraft is climbing away from the runway and the approach
     * phase restrictions no longer apply.
     */
    val altitudeRestrictionM: Option<Double> = None,

    /**
     * The mission step for which the pilot most recently emitted a
     * "first-tick" transmission (request, ready report, position report).
     * Used by [stepTransmission] to fire the per-step transmission exactly
     * once per step entry, irrespective of timing — replaces a brittle
     * `(now - stepEnteredAt) < window` check that double-fired on adjacent
     * pilot ticks.
     *
     * Reset to [None] on go-around so the rejoined circuit's transmissions
     * fire fresh.
     */
    val lastTransmittedStep: Option<MissionStep> = None,

    /**
     * Bounded ring of [xyz.easiersaid.twr.protocol.AnomalousAssignment]s
     * surfaced by [xyz.easiersaid.twr.protocol.applyPrecedence] when the
     * pilot's runway-assignment update was anomalous (e.g. a `Takeoff` after
     * a `Land` for a different runway).
     *
     * The pilot's [updateActiveRunwayFromInstruction] obeys the latest
     * controller statement (the new assignment proceeds), but retains the
     * anomaly here so Pass 7's coordination ledger can consume it without
     * re-deriving the history. Capped at [MAX_ANOMALY_HISTORY]; the oldest
     * is dropped.
     *
     * Pass 5 (D-PF.2 closure): retention only — no consumer reads this slot
     * yet. Pass 7 will route entries through a typed `ControllerEvent` for
     * the controller-side reconciliation.
     */
    val recentAnomalies: List<xyz.easiersaid.twr.protocol.AnomalousAssignment> = emptyList(),

    /**
     * fn-12.2 (G3a-obstruction): transient signaling state for ATC-issued
     * reactive go-around recognition in Circuit-mode, with a **single-cycle
     * lifetime**.
     *
     * **Set by** [xyz.easiersaid.twr.pilot.PilotCognitive.handleGoAround]
     * BEFORE the mission-tree rewrite, when the original `currentTask?.step`
     * is one of the on-final eligible steps:
     * `{FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}`. (`LAND` is
     * included because [xyz.easiersaid.twr.pilot.PilotCognitive.handleLandingClearance]
     * marks `AWAIT_LANDING_CLEARANCE` complete after `ClearedToLand`, so by
     * the time a post-clearance obstruction GoAround arrives, `currentTask.step`
     * is already `LAND`.) Otherwise stays [None] — the GA arrived during a
     * non-on-final step (e.g. mid-downwind reactive separation) and the
     * existing Visual-mode reactive special-case at `Pilot.kt:planVisualRoute`
     * handles it.
     *
     * **Read and consumed by** [xyz.easiersaid.twr.pilot.applyAtcInitiatedGoAround]
     * in `pilotDecide`, which clears the flag (`pendingAtcGoAroundFrom = None`)
     * and emits the Tick A intent (`route = PilotRoute.None`,
     * `phase = PilotPhase.Final` retained — same shape as fn-11.1's
     * trained-GA Tick A). The next tick's `planRoute` then hits the existing
     * `isCircuitTrainedGoAroundTickB` predicate and builds the GA route via
     * the reused `planCircuitTrainedGoAround` planner — zero new
     * route-planning code.
     *
     * **Two-layer flag-clear defense**:
     *  1. `handleGoAround` only sets when on-final-eligible.
     *  2. `pilotDecide`'s recognition arm clears the flag on EVERY inspection
     *     — even when the discriminator (Circuit-mode + phase=Final) fails.
     *     Prevents the flag lingering and incorrectly firing later if the
     *     aircraft transitions back to phase=Final via some other path.
     *
     * **Why a flag, not a `preStep + currentStep` recognition predicate**:
     * `processInstruction(GoAround)` runs in `pilotCognitiveDecide` and
     * rewrites the mission tree before the next `pilotDecide` cycle captures
     * `preStep`. By that next cycle, `preStep` is already `GOING_AROUND` and
     * the original on-final step is unrecoverable. The flag captures the
     * pre-rewrite step at the moment the rewrite happens, so recognition is
     * robust regardless of cycle ordering.
     *
     * **Lifecycle invariants**:
     *  - Default-[None] preserves existing call sites (Kotlin data-class
     *    `copy(...)` semantics: omitted fields preserve current value).
     *  - The unique set-site is `handleGoAround`; the unique clear-sites are
     *    `applyAtcInitiatedGoAround` (consume on fire) and `pilotDecide`'s
     *    discriminator-fail arm (clear on miss). [resetForGoAround] does NOT
     *    touch the flag — `handleGoAround` calls `resetForGoAround` first
     *    and stamps the flag onto the reset result.
     *  - Single-cycle lifetime — the flag must NOT survive across more than
     *    one `pilotDecide` invocation.
     */
    val pendingAtcGoAroundFrom: Option<MissionStep> = None,
) {
    companion object {
        const val MAX_ANOMALY_HISTORY: Int = 8
    }

    /** The current primitive task being executed (leftmost incomplete leaf). */
    val currentTask: PrimitiveTask? get() = root.currentPrimitive()
    val isComplete: Boolean get() = root.isComplete
}

/**
 * Reset phase-local state for go-around. The tree (root) is handled
 * separately by subtree replacement in [processInstruction].
 *
 * Every existing field is named here — either reset (with a value) or
 * explicitly preserved (in the trailing comment block). Adding a new
 * field to [PilotMission] requires deciding go-around behavior for it
 * and updating the call sites here. There is no compile-time guarantee
 * the new field is named — reviewers touching this function should
 * re-check the comment block matches the current field set.
 *
 * fn-12.2 (G3a-obstruction): [pendingAtcGoAroundFrom] is **deliberately
 * not touched here**. The unique set-site is
 * [xyz.easiersaid.twr.pilot.PilotCognitive.handleGoAround], which calls
 * [resetForGoAround] FIRST and then stamps the flag onto the reset result
 * via `.copy(pendingAtcGoAroundFrom = ...)`. If `resetForGoAround` cleared
 * the flag, that stamp would be wiped on the same call. Preserving it
 * here keeps `handleGoAround`'s set semantics the unique authority.
 * The unique clear-sites are
 * [xyz.easiersaid.twr.pilot.applyAtcInitiatedGoAround] (consume on fire)
 * and `pilotDecide`'s discriminator-fail arm (defensive clear).
 */
fun PilotMission.resetForGoAround(now: SimTime): PilotMission = copy(
    // Phase-local — reset to defaults.
    stepEnteredAt = now,
    lastReportedLeg = None,
    reportedVacated = false,
    hasClearance = false,
    // Cross-cutting — reset on go-around (extend-downwind and vectors are
    // invalidated by the approach abort).
    activeConstraints = emptySet(),
    routeOverride = None,
    // joinLeg: reset — go-around re-enters circuit from go-around path, not original join.
    joinLeg = None,
    // altitudeRestrictionM: reset — go-around discards approach-phase level restrictions.
    altitudeRestrictionM = None,
    // lastTransmittedStep: reset — rejoined circuit's transmissions fire fresh.
    lastTransmittedStep = None,
    // Structural + cross-cutting — preserved.
    // goal: unchanged (still the same mission)
    // root: handled by caller (subtree replacement)
    // navigationMode: unchanged (still VFR/IFR)
    // activeRunway: unchanged (same runway)
    // contactedOnFrequency: unchanged (still on same frequency)
    // pendingAtcGoAroundFrom: NOT reset — see KDoc above. The set-site
    //     `handleGoAround` calls resetForGoAround first then stamps the flag.
)

// ── Task tree ────────────────────────────────────────────────────────

/**
 * Typed task name for compound tasks. Carries the pilot's mission-level
 * categorisation of what the aircraft is currently doing — read by
 * [xyz.easiersaid.twr.sim.toFlightStrip] to derive a broad service intent
 * for the controller's pre-briefing, and by go-around subtree replacement
 * (`isCircuitLike`) to scope the rewrite to the active circuit compound.
 *
 * The previous reference to `derivePilotGoal` here was stale — that
 * function was removed when the controller-facing `PilotGoal` type was
 * deleted as part of the firewall work.
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
    /**
     * fn-11.1 (G3a-trained): final leg whose PHYSICAL completion fires when
     * the aircraft crosses short-final altitude (`DECISION_ALTITUDE_M`,
     * ~100 m / ~330 ft AGL). Used only by [plannedGoAroundCircuitTask] —
     * the pilot's training plan dictates a go-around at the short-final
     * decision gate. Routes the same as [FLY_FINAL] (no per-leg altitude
     * support is needed; the route planner aliases this step's airborne
     * routing to FLY_FINAL's). MUST NOT be aliased into the
     * `ClearedToLand`/`ClearedTouchAndGo` step-completion list — clearance
     * receipt does NOT advance this step; only the altitude/phase gate
     * does. Otherwise the pilot would skip the trained go-around and
     * proceed straight to GOING_AROUND on clearance receipt.
     */
    FLY_FINAL_TO_SHORT_FINAL,
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
 * Build the GROUND_DEPARTURE compound task. Uniform across AI and human
 * pilots — same mission tree, same timing rules.
 *
 * `REQUEST_STARTUP` and `AWAIT_STARTUP_APPROVAL` are not part of the tree.
 * This is **rot, not a design choice** (deferment **D-PF.1**): we have not
 * built the controller-side `CLEARANCE_DELIVERY` procedure that would gate
 * those steps, so rather than leave dead steps in the tree (or paper over
 * the gap with a `humanPiloted` short-circuit) the steps are simply absent.
 * When an airport requiring startup clearance is exercised (LOWS, LOWW,
 * LJLJ are candidates), D-PF.1 brings the steps back the right way — gated
 * on the airport's procedural requirement, not on cockpit type.
 */
fun groundDepartureTask(): CompoundTask = CompoundTask(TaskName.GroundDeparture, listOf(
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

/**
 * Build the GROUND_ARRIVAL compound task.
 *
 * Pass 7 (D-AUDIT.5): no dedicated CALL_INBOUND step needed for this
 * task — the responsibility transition fires on any pilot transmission
 * to a Watching controller (per ICAO Doc 4444 §10.1.1, two-way comms
 * via "receiving station acknowledges receipt"). The first transmission
 * here is REPORT_RUNWAY_VACATED, which doubles as initial contact (real
 * phraseology: "Ground, OE-ABC, runway 16C vacated, request taxi to stand").
 */
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
 * The controller never sees this tree. The pilot expresses their plan to
 * the controller through the radio (Reports, Requests, Acknowledgements);
 * the broad service intent is in the flight strip pre-briefing.
 *
 * fn-11.1: [HighLevelGoal.CircuitTraining]'s arm walks the typed
 * [CircuitOutcome] list. Each outcome compiles to its per-circuit compound
 * via an exhaustive sealed `when`: [CircuitOutcome.TouchAndGo] →
 * [touchAndGoCircuitTask], [CircuitOutcome.FullStop] → [circuitTask],
 * [CircuitOutcome.GoAround] → [plannedGoAroundCircuitTask] (the planned
 * short-final go-around).
 */
fun planMission(goal: HighLevelGoal, ifr: Boolean = false): CompoundTask = when (goal) {
    is HighLevelGoal.Departure -> if (!ifr) CompoundTask(TaskName.Depart, listOf(
        groundDepartureTask(),
        PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
    )) else CompoundTask(TaskName.Depart, listOf(
        groundDepartureTask(),
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
        // fn-11.1: per-circuit branching on the typed [CircuitOutcome] list.
        // Sealed `when` is exhaustive at compile time — adding a new outcome
        // is a compile error, not a silent fallthrough.
        val circuitTasks = goal.outcomes.map { outcome ->
            when (outcome) {
                is CircuitOutcome.TouchAndGo -> touchAndGoCircuitTask()
                is CircuitOutcome.FullStop -> circuitTask()
                is CircuitOutcome.GoAround -> plannedGoAroundCircuitTask()
            }
        }
        CompoundTask(TaskName.CircuitTraining,
            listOf(groundDepartureTask()) + circuitTasks + listOf(groundArrivalTask()))
    }
    is HighLevelGoal.Transit -> if (!ifr) {
        // G2: static composition representing "this is my plan right now".
        // Real AI pilot behaviour is fluid (D-G2.4): goal stays fixed, plan
        // re-derives on meaningful state change. The shape below is what
        // `planMission` returns for the snapshot {at-stand, transit-to-X};
        // a future scenario forcing mid-mission re-planning will refactor
        // this into a `(goal, snapshot) → tree` contract.
        //
        // Transit = depart from origin + cruise + arrive at destination.
        // VFR cruise is a single FLY_DEPARTURE whose terminal waypoint is
        // the destination's first published REP (resolved by the route
        // planner from world.aerodromes[destination].aip.publishedVfrProcedures
        // and stored on mission.transitContactRep). FLY_DEPARTURE physically
        // completes when aircraft.positionPoint equals that REP — see the
        // Transit arm of isPhysicallyComplete in PilotCognitive.kt.
        // The pilot then self-contacts the destination tower at CALL_INBOUND;
        // circuit pattern follows the same shape as a normal arrival mission.
        //
        // G2 Phase I: the arrival-pattern primitives (FLY_DOWNWIND..LAND)
        // remain direct primitive children of Transit. `Pilot.kt:planRoute`
        // has a Transit-arrival arm that handles the off-pattern → pattern
        // routing once `mission.joinLeg` is set by the controller's
        // ARR-JOIN-CIRCUIT rule.
        CompoundTask(TaskName.Transit, listOf(
            groundDepartureTask(),
            PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
            arrivalJoinTask(),
            PrimitiveTask(MissionStep.FLY_DOWNWIND, CompletionMode.PHYSICAL),
            PrimitiveTask(MissionStep.REPORT_DOWNWIND, CompletionMode.REPORTED),
            PrimitiveTask(MissionStep.AWAIT_SEQUENCING, CompletionMode.PHYSICAL),
            PrimitiveTask(MissionStep.FLY_BASE, CompletionMode.PHYSICAL),
            PrimitiveTask(MissionStep.REPORT_BASE, CompletionMode.REPORTED),
            PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL),
            PrimitiveTask(MissionStep.REPORT_FINAL, CompletionMode.REPORTED),
            PrimitiveTask(MissionStep.AWAIT_LANDING_CLEARANCE, CompletionMode.INSTRUCTION_GATED),
            PrimitiveTask(MissionStep.LAND, CompletionMode.PHYSICAL),
            groundArrivalTask(),
        ))
    } else CompoundTask(TaskName.Transit, listOf(
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
    // ARR-TNG-AIRBORNE on the controller side completes the arrival
    // commitment so the next circuit can form a fresh one.
    PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
))

/**
 * fn-11.1: planned go-around circuit — fly the pattern down to short-final,
 * then climb out via the published go-around path before re-entering the
 * circuit for the next outcome.
 *
 * Doctrinally faithful to flight-school training (FAA AFH §9, CAP 413
 * §4.66/§4.67): the instructor pre-arranges a go-around at the short-final
 * decision gate to exercise the procedure. The pilot's mission tree is the
 * sole trigger — no sensor event, no runtime subtree replacement (cf. the
 * reactive [Pilot.applySelfInitiatedGoAround] path which fires on
 * [xyz.easiersaid.twr.pilot.observe.PilotEvent.DecisionAltitudeWithoutClearance]).
 *
 * Outer compound name is [TaskName.Circuit] (NOT [TaskName.CircuitAfterGoAround]),
 * because structurally this IS a circuit — the go-around does not begin
 * until the inner [goAroundTask] subtree fires. Other consumers
 * (`toFlightStrip`, `isCircuitLike`, route arms) see it as a regular
 * circuit until the GA primitive becomes active.
 *
 * Step list:
 *  - FLY_DEPARTURE → FLY_DOWNWIND → REPORT_DOWNWIND (per-circuit pattern)
 *  - AWAIT_SEQUENCING → FLY_BASE → REPORT_BASE
 *  - **FLY_FINAL_TO_SHORT_FINAL** (replaces FLY_FINAL — completes by altitude
 *    at `DECISION_ALTITUDE_M`, NOT by reaching the threshold)
 *  - [goAroundTask] (GOING_AROUND primitive: REPORTED — emits
 *    `Report(GoingAround)` per CAP 413 §4.67).
 *
 * No `REPORT_FINAL` step: by short-final the pilot has already satisfied
 * `HasReportedPositionCall` via REPORT_DOWNWIND + REPORT_BASE, so the
 * controller's ARR-LAND rule can issue `ClearedToLand` proactively (this
 * is the GA-POST-CLEAR regression-source case). No `AWAIT_LANDING_CLEARANCE`
 * either — the pilot is going around, not waiting for clearance to land.
 *
 * After GOING_AROUND completes, the next outcome's compound takes over via
 * the standard `outcomes.map` list-walk in [planMission]'s CircuitTraining
 * arm. The Circuit-mode go-around route is built by the planner-side
 * special-case in `Pilot.kt:planRoute` (mirrors the Visual-mode special-case
 * for the reactive flow).
 */
fun plannedGoAroundCircuitTask(): CompoundTask = CompoundTask(TaskName.Circuit, listOf(
    PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.FLY_DOWNWIND, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.REPORT_DOWNWIND, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.AWAIT_SEQUENCING, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.FLY_BASE, CompletionMode.PHYSICAL),
    PrimitiveTask(MissionStep.REPORT_BASE, CompletionMode.REPORTED),
    PrimitiveTask(MissionStep.FLY_FINAL_TO_SHORT_FINAL, CompletionMode.PHYSICAL),
    goAroundTask(),
))

/**
 * True for circuit task names that can be active during a circuit pattern.
 *
 * Used in go-around replacement to find the active circuit task regardless
 * of whether it's a full-stop or touch-and-go circuit. Intentionally includes
 * [TaskName.TouchAndGo] — a go-around during a T&G approach replaces that task.
 */
fun TaskName.isCircuitLike(): Boolean = when (this) {
    is TaskName.Circuit, is TaskName.CircuitAfterGoAround, is TaskName.TouchAndGo -> true
    is TaskName.Depart, is TaskName.Arrive, is TaskName.Transit,
    is TaskName.GroundDeparture, is TaskName.GroundArrival,
    is TaskName.CircuitTraining, is TaskName.ArrivalJoin,
    is TaskName.GoAround -> false
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

/**
 * Create a fresh mission for an aircraft.
 *
 * G2 (D-PF.3 closure): when [filedPlan] carries a non-null
 * [xyz.easiersaid.twr.protocol.FiledPlan.destinationRunway], [PilotMission.activeRunway]
 * is initialised to `Some(RunwayAssignment(destinationRunway,
 * RunwayAssignmentSource.Filing))`. Radio sources supersede via
 * [xyz.easiersaid.twr.protocol.applyPrecedence] once any clearance lands.
 *
 * The `T?` → `Option` boundary lives here: [filedPlan] uses `T?` (matching
 * the surrounding nullable style on FiledPlan), [PilotMission] uses
 * `Option` (per-class quotient rule, D-PF.4).
 */
fun createMission(
    goal: HighLevelGoal,
    startPhase: PilotPhase,
    time: SimTime,
    filedPlan: xyz.easiersaid.twr.protocol.FiledPlan? = null,
): PilotMission {
    var root = planMission(goal)
    root = skipCompletedSteps(root, startPhase)
    val initialActiveRunway: Option<xyz.easiersaid.twr.protocol.RunwayAssignment<xyz.easiersaid.twr.protocol.RunwayAssignmentSource>> =
        filedPlan?.destinationRunway?.let {
            Some(xyz.easiersaid.twr.protocol.RunwayAssignment(
                runway = it,
                source = xyz.easiersaid.twr.protocol.RunwayAssignmentSource.Filing,
            ))
        } ?: None
    return PilotMission(
        goal = goal,
        root = root,
        stepEnteredAt = time,
        activeRunway = initialActiveRunway,
        filedPlan = filedPlan?.let { Some(it) } ?: None,
    )
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
    // FLY_FINAL_TO_SHORT_FINAL (fn-11.1) is **deliberately absent** from
    // `preFinal`. Unlike FLY_FINAL (which completes by reaching the FINAL-leg
    // waypoint), the trained-GA short-final descent step completes by
    // ALTITUDE (`DECISION_ALTITUDE_M`). A spawn on PilotPhase.Final at, say,
    // 500m AGL has not yet crossed the decision gate; pre-completing the
    // step would skip the trained-GA fork entirely and advance straight to
    // GOING_AROUND. The altitude predicate in `isPhysicallyComplete` is the
    // only correct trigger for a Final-phase spawn. Per fn-11.1 codex
    // review finding #1.
    val preFinal = preBase + setOf(MissionStep.REPORT_BASE, MissionStep.FLY_FINAL)
    // FLY_FINAL_TO_SHORT_FINAL **and** GOING_AROUND are both included in
    // `preLand` (for LandingRoll / Vacating / ClearOfRunway spawns). For
    // trained-GA missions, the planned go-around is structurally a leg of
    // the airborne pattern — once the aircraft is on the runway, the
    // entire trained-GA outcome (final descent + the goAroundTask's
    // GOING_AROUND announcement) is conceptually past, symmetric to how
    // `LAND`, `REPORT_FINAL`, `AWAIT_LANDING_CLEARANCE` are skipped here.
    // Including only FLY_FINAL_TO_SHORT_FINAL would leave GOING_AROUND
    // active in a post-touchdown phase, causing the pilot to transmit
    // `Report(GoingAround)` while on the runway — incoherent. Per fn-11.1
    // codex re-re-review finding #1 (post-final spawn must skip the entire
    // trained-GA outcome, not just the altitude-gated leg).
    //
    // This also covers regular `Arrival`/`CircuitTraining` missions
    // spawned post-touchdown: those compounds never carry a GOING_AROUND
    // primitive at `createMission` time (GOING_AROUND only appears via
    // runtime `replaceChild` from `applySelfInitiatedGoAround` or
    // `handleGoAround`), so this skip-set entry is a no-op for them.
    val preLand = preFinal + setOf(
        MissionStep.REPORT_FINAL, MissionStep.AWAIT_LANDING_CLEARANCE, MissionStep.LAND,
        MissionStep.FLY_FINAL_TO_SHORT_FINAL, MissionStep.GOING_AROUND,
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
