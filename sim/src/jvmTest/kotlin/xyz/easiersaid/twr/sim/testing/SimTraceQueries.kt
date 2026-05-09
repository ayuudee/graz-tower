package xyz.easiersaid.twr.sim.testing

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.HandoffTarget
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.ResponsibilityState
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.controller.bdi.Stage
import xyz.easiersaid.twr.pilot.MissionStep
import xyz.easiersaid.twr.sim.SimEvent
import xyz.easiersaid.twr.sim.SimState
import xyz.easiersaid.twr.sim.Utterance

// ── Cursor-returning predicates ──────────────────────────────────────

/** First cursor whose state satisfies [predicate]. */
fun SimTrace.firstWhere(predicate: (SimState) -> Boolean): Option<TraceCursor> =
    cursor().firstAtOrAfter(predicate)

/** Last cursor whose state satisfies [predicate]. */
fun SimTrace.lastWhere(predicate: (SimState) -> Boolean): Option<TraceCursor> =
    TraceCursor(this, steps.size).lastAtOrBefore(predicate)

/** First cursor at-or-after [time] whose state satisfies [predicate]. */
fun SimTrace.firstWhereAfter(
    time: SimTime,
    predicate: (SimState) -> Boolean,
): Option<TraceCursor> {
    val start = firstAtTime(time) ?: return None
    return start.firstAtOrAfter(predicate)
}

/** First cursor at-or-after the given [time]. */
private fun SimTrace.firstAtTime(time: SimTime): TraceCursor? {
    if (initial.now >= time) return cursor()
    val idx = steps.indexOfFirst { it.time >= time }
    return if (idx < 0) null else TraceCursor(this, idx + 1)
}

/**
 * fn-8.2 (G1 §5 conflict-resolution helper): last cursor whose state's
 * sim-time is at-or-before [time]. Returns [None] only when [time] is
 * strictly before the trace's [SimTrace.initial] time (i.e. the trace
 * doesn't cover the requested moment).
 *
 * Used by `G1TwoAircraftCircuitsTest` to pin the conflict-resolution
 * three-event chain `extendDownwind(B).time < touchdown(A).time <
 * turnBase(B).time` against state observables at a given record time
 * (transmission records carry `SimTime`s; this helper bridges the cursor
 * vocabulary to those times).
 */
fun SimTrace.stateAtOrBefore(time: SimTime): Option<TraceCursor> =
    lastWhere { it.now <= time }

// ── Generic transition primitive ─────────────────────────────────────

/**
 * All cursor-pairs where `extract(state)` differs from the previous
 * cursor's value. The first transition (if any) has the initial cursor
 * as `before`. Returns empty list when the property is constant.
 *
 * This is the load-bearing primitive — every specialised transition
 * helper ([responsibilityTransitions], [commitmentStageTransitions], etc.)
 * is a one-liner over [transitionsOf] plus a discriminator (controller /
 * aircraft).
 */
fun <T> SimTrace.transitionsOf(extract: (SimState) -> T): List<Transition<T>> {
    val out = mutableListOf<Transition<T>>()
    var prevCursor = cursor()
    var prevValue = extract(prevCursor.state)
    var i = 1
    while (i <= steps.size) {
        val curCursor = TraceCursor(this, i)
        val curValue = extract(curCursor.state)
        if (curValue != prevValue) {
            out.add(Transition(from = prevValue, to = curValue, before = prevCursor, after = curCursor))
            prevValue = curValue
        }
        prevCursor = curCursor
        i++
    }
    return out
}

// ── Aircraft-scoped specialised transitions ──────────────────────────

/**
 * Per-controller responsibility-state transitions for [aircraft].
 *
 * Each [ResponsibilityTransition] carries the [ControllerId] discriminator
 * plus the underlying [Transition]<Option<ResponsibilityState>>. Use
 * doctrine predicates ([Transition.isReleaseDrop],
 * [Transition.isCrossAerodromePickup]) to assert against doctrine names
 * rather than ADT shape.
 */
fun SimTrace.responsibilityTransitions(
    aircraft: AircraftId,
): List<ResponsibilityTransition> {
    val controllers = controllerIdsAcrossTrace()
    return controllers.flatMap { ctrl ->
        transitionsOf { st ->
            Option.fromNullable(st.controllers[ctrl]?.responsibilities?.get(aircraft))
        }.map { ResponsibilityTransition(ctrl, it) }
    }.sortedBy { it.after.time.millis }
}

data class ResponsibilityTransition(
    val controller: ControllerId,
    val transition: Transition<Option<ResponsibilityState>>,
) {
    val from: Option<ResponsibilityState> get() = transition.from
    val to: Option<ResponsibilityState> get() = transition.to
    val before: TraceCursor get() = transition.before
    val after: TraceCursor get() = transition.after
}

/** Per-controller commitment-stage transitions for [aircraft]. */
fun SimTrace.commitmentStageTransitions(
    aircraft: AircraftId,
    controller: ControllerId,
): List<CommitmentStageTransition> =
    transitionsOf { st ->
        Option.fromNullable(st.beliefs[controller]?.commitments?.get(aircraft)?.stage)
    }.map { CommitmentStageTransition(controller, it) }

data class CommitmentStageTransition(
    val controller: ControllerId,
    val transition: Transition<Option<Stage>>,
) {
    val from: Option<Stage> get() = transition.from
    val to: Option<Stage> get() = transition.to
    val before: TraceCursor get() = transition.before
    val after: TraceCursor get() = transition.after
}

/** Mission-step transitions for [aircraft]. */
fun SimTrace.missionStepTransitions(aircraft: AircraftId): List<MissionStepTransition> =
    transitionsOf { st ->
        Option.fromNullable(st.aircraft[aircraft]?.pilotMission?.currentTask?.step)
    }.map { MissionStepTransition(it) }

data class MissionStepTransition(val transition: Transition<Option<MissionStep>>) {
    val from: Option<MissionStep> get() = transition.from
    val to: Option<MissionStep> get() = transition.to
    val before: TraceCursor get() = transition.before
    val after: TraceCursor get() = transition.after
}

/** Aircraft `positionPoint` transitions over the trace. */
fun SimTrace.positionPointTransitions(aircraft: AircraftId): List<Transition<Option<PointId>>> =
    transitionsOf { st -> Option.fromNullable(st.aircraft[aircraft]?.positionPoint) }

// ── Doctrine predicates over ResponsibilityState transitions ─────────

/** Owned → HandingOff(Released): the cross-aerodrome boundary release shape. */
fun Transition<Option<ResponsibilityState>>.isReleaseDrop(): Boolean =
    from.fold({ false }) { it is ResponsibilityState.Owned } &&
        to.fold({ false }) {
            it is ResponsibilityState.HandingOff && it.target is HandoffTarget.Released
        }

/** absent → Owned: cross-aerodrome destination tower picking up via knownStrips. */
fun Transition<Option<ResponsibilityState>>.isCrossAerodromePickup(): Boolean =
    from.fold({ true }, { false }) && to.fold({ false }) { it is ResponsibilityState.Owned }

/** Watching → Owned: intra-aerodrome handoff completion. */
fun Transition<Option<ResponsibilityState>>.isHandoffPickup(): Boolean =
    from.fold({ false }) { it is ResponsibilityState.Watching } &&
        to.fold({ false }) { it is ResponsibilityState.Owned }

/** Owned → HandingOff(Peer(_)): controller initiating intra-aerodrome handoff. */
fun Transition<Option<ResponsibilityState>>.isHandoffInitiation(): Boolean =
    from.fold({ false }) { it is ResponsibilityState.Owned } &&
        to.fold({ false }) {
            it is ResponsibilityState.HandingOff && it.target is HandoffTarget.Peer
        }

// ── Rule-firing extraction ───────────────────────────────────────────

/** A controller-side rule firing reconstructed from the trace. */
data class RuleFiring(
    val time: SimTime,
    val controller: ControllerId,
    val aircraft: AircraftId,
    val ruleId: String,
    val description: String,
    val instruction: Option<AtcInstruction>,
    val cursor: TraceCursor,
    val source: RuleFiringSource,
)

sealed interface RuleFiringSource {
    /** Extracted from a transmission's `DecisionTrace`. */
    data object Transmission : RuleFiringSource

    /**
     * Reconstructed post-hoc from a commitment-stage transition with no
     * transmission in the same window. The `ruleId` is diagnostic-grade
     * (`<inferred:fromStage→toStage>`) — the actual rule-id-of-record
     * would require sim-side `RuleFired` event emission (out of scope).
     */
    data object StageOnlyInferred : RuleFiringSource
}

/** All rule firings across the trace. */
fun SimTrace.ruleFirings(): List<RuleFiring> =
    transmissionDerivedFirings() + stageOnlyInferredFirings()

/** Rule firings that targeted [aircraft]. */
fun SimTrace.ruleFiringsFor(aircraft: AircraftId): List<RuleFiring> =
    ruleFirings().filter { it.aircraft == aircraft }

/** Rule firings issued by [controller]. */
fun SimTrace.ruleFiringsBy(controller: ControllerId): List<RuleFiring> =
    ruleFirings().filter { it.controller == controller }

private fun SimTrace.transmissionDerivedFirings(): List<RuleFiring> {
    val out = mutableListOf<RuleFiring>()
    var i = 1
    while (i <= steps.size) {
        val cursor = TraceCursor(this, i)
        val event = steps[i - 1].event as? SimEvent.TransmissionStart
        if (event != null) {
            val tx = event.transmission
            val utterance = tx.utterance as? Utterance.FromController
            val instruct = utterance?.output as? ControllerOutput.Instruct
            if (instruct != null) {
                val speaker = (tx.speaker as? xyz.easiersaid.twr.sim.SpeakerRef.Controller)?.id
                if (speaker != null) {
                    out.add(
                        RuleFiring(
                            time = tx.startedAt,
                            controller = speaker,
                            aircraft = instruct.target,
                            ruleId = instruct.trace.ruleId,
                            description = instruct.trace.description,
                            instruction = Some(extractDirectInstruction(instruct)),
                            cursor = cursor,
                            source = RuleFiringSource.Transmission,
                        ),
                    )
                }
            }
        }
        i++
    }
    return out
}

private fun extractDirectInstruction(instruct: ControllerOutput.Instruct): AtcInstruction =
    when (val dispatch = instruct.dispatch) {
        is xyz.easiersaid.twr.controller.bdi.Dispatch.Direct -> dispatch.instruction
        is xyz.easiersaid.twr.controller.bdi.Dispatch.Conditional -> dispatch.instruction
    }

/**
 * Stage-only-advance rule firings reconstructed from
 * [commitmentStageTransitions] WITHOUT a corresponding transmission in
 * the same cursor span.
 *
 * Applied per (controller, aircraft) pair: scan stage transitions and
 * the transmission-derived firings; for each stage transition with no
 * matching transmission firing in `[before.time, after.time]`, emit a
 * synthetic `RuleFiring(source = StageOnlyInferred)`.
 *
 * The synthetic `ruleId` is `<inferred:from→to>` — diagnostic-grade
 * but doesn't carry the actual rule name. Sim-side `SimEvent.RuleFired`
 * emission for stage-only rules is the proper fix; this inference is
 * sufficient for closure-pass debugging.
 */
private fun SimTrace.stageOnlyInferredFirings(): List<RuleFiring> {
    val txFirings = transmissionDerivedFirings()
    val aircraftAndControllers = aircraftIdsAcrossTrace().flatMap { ac ->
        controllerIdsAcrossTrace().map { ctrl -> ac to ctrl }
    }
    return aircraftAndControllers.flatMap { (ac, ctrl) ->
        commitmentStageTransitions(ac, ctrl).mapNotNull { st ->
            val span = st.before.time..st.after.time
            val matching = txFirings.any {
                it.controller == ctrl && it.aircraft == ac && it.time.millis in span.start.millis..span.endInclusive.millis
            }
            if (matching) null
            else RuleFiring(
                time = st.after.time,
                controller = ctrl,
                aircraft = ac,
                ruleId = "<inferred:${st.from.toStageName()}→${st.to.toStageName()}>",
                description = "Stage-only-advance rule (no transmission in cursor span)",
                instruction = None,
                cursor = st.after,
                source = RuleFiringSource.StageOnlyInferred,
            )
        }
    }
}

private fun Option<Stage>.toStageName(): String = fold({ "absent" }, { it.name })

// ── Span queries ─────────────────────────────────────────────────────

/**
 * Events fired between [start] (exclusive) and [end] (inclusive), in
 * time order. The convention "exclusive on `start`, inclusive on `end`"
 * means the triggering event of `end` is included; the prior triggering
 * event of `start` is NOT (it precedes the span).
 */
fun SimTrace.eventsBetween(start: TraceCursor, end: TraceCursor): List<SimEvent> {
    require(start.trace === this && end.trace === this) {
        "eventsBetween: cursors must belong to this SimTrace"
    }
    require(start.index <= end.index) {
        "eventsBetween: start.index (${start.index}) > end.index (${end.index})"
    }
    return (start.index until end.index).map { steps[it].event }
}

/**
 * Sub-trace from [start] (inclusive) to [end] (inclusive). Cursor
 * identity does NOT survive slicing — re-derive cursors against the
 * sub-trace.
 */
fun SimTrace.slice(start: TraceCursor, end: TraceCursor): SimTrace {
    require(start.trace === this && end.trace === this) {
        "slice: cursors must belong to this SimTrace"
    }
    require(start.index <= end.index) {
        "slice: start.index (${start.index}) > end.index (${end.index})"
    }
    val newInitial = start.state
    val newSteps = (start.index until end.index).map { steps[it] }
    return SimTrace(newInitial, newSteps)
}

// ── Helpers ──────────────────────────────────────────────────────────

/**
 * Set of controller IDs that appear in the trace (initial + any state).
 * Used to scope per-controller transition queries.
 */
private fun SimTrace.controllerIdsAcrossTrace(): Set<ControllerId> {
    val ids = mutableSetOf<ControllerId>()
    ids += initial.controllers.keys
    for (s in steps) ids += s.state.controllers.keys
    return ids
}

private fun SimTrace.aircraftIdsAcrossTrace(): Set<AircraftId> {
    val ids = mutableSetOf<AircraftId>()
    ids += initial.aircraft.keys
    for (s in steps) ids += s.state.aircraft.keys
    return ids
}
