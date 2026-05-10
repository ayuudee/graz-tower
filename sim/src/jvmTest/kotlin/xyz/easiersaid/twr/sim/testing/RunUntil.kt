package xyz.easiersaid.twr.sim.testing

import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.HeapEventQueue
import xyz.easiersaid.twr.sim.SimEvent
import xyz.easiersaid.twr.sim.SimState
import xyz.easiersaid.twr.sim.emit
import xyz.easiersaid.twr.sim.step

/**
 * Drive the sim from [initialEvents] until simulated time [untilTime] is
 * exceeded or the queue drains. Returns the final [SimState] paired with
 * every event dequeued during the run (in order).
 *
 * Replaces the per-test re-implementation of the event-fold loop. Callers
 * filter the returned event list for whatever they need:
 *
 * ```
 * val (state, events) = runUntil(initial, init, until)
 * val transmissions = events.filterIsInstance<SimEvent.TransmissionStart>()
 *                           .map { it.toTransmissionRecord() }
 * ```
 *
 * The local `mutableListOf<SimEvent>` is consumed-before-return — the public
 * surface is pure (input → output, no callback hole).
 */
fun runUntil(
    initialState: SimState,
    initialEvents: List<SimEvent>,
    untilTime: SimTime,
): Pair<SimState, List<SimEvent>> {
    var state = initialState
    val (stamped, stampedEvents) = state.emit(initialEvents)
    state = stamped
    val queue = HeapEventQueue()
    stampedEvents.forEach(queue::enqueue)
    val trace = mutableListOf<SimEvent>()
    var nextEvent = queue.dequeueMin()
    while (nextEvent != null && nextEvent.time <= untilTime) {
        trace += nextEvent
        val (next, emitted) = step(state, nextEvent)
        state = next
        emitted.forEach(queue::enqueue)
        nextEvent = queue.dequeueMin()
    }
    return state to trace.toList()
}

/**
 * Convenience: drive the sim and project the transmission stream.
 * Equivalent to `runUntil(...).let { (s, events) -> s to events.filterIsInstance<TransmissionStart>().map { it.toTransmissionRecord() } }`.
 */
fun runUntilWithTransmissions(
    initialState: SimState,
    initialEvents: List<SimEvent>,
    untilTime: SimTime,
): Pair<SimState, List<TransmissionRecord>> {
    val (finalState, events) = runUntil(initialState, initialEvents, untilTime)
    val records = events.filterIsInstance<SimEvent.TransmissionStart>()
        .map { it.toTransmissionRecord() }
    return finalState to records
}

/**
 * Named return record for [runUntilWithStateTrace] (replaces the prior
 * `Triple<SimState, List<TransmissionRecord>, List<Pair<SimEvent, SimState>>>`
 * shape). Tests destructure normally:
 *
 * ```kotlin
 * val (finalState, records, trace) = runUntilWithStateTrace(initial, events, until)
 * ```
 *
 * Per Phase I plan-stage impact-S1: a named record decouples future
 * extensions (e.g., adding a fourth slice) from every existing call
 * site's destructuring.
 */
data class StateTraceResult(
    val finalState: SimState,
    val records: List<TransmissionRecord>,
    val trace: SimTrace,
)

/**
 * Pass 7 (D-AUDIT.5 G0 mid-handoff assertion): drive the sim and capture
 * a state snapshot **after each event is processed**. Returns the
 * transmission records (as `runUntilWithTransmissions`) plus the full
 * [SimTrace] of `(event → state-after)` pairs, so tests can introspect
 * what state looked like at any moment during the run.
 *
 * The [SimTrace] supports cursor-based forward/backward navigation,
 * property-shaped transition extraction (responsibility, commitment-
 * stage, mission-step, positionPoint), and rule-firing reconstruction
 * (transmission-derived + stage-only inferred). See `SimTrace.kt`,
 * `SimTraceQueries.kt`, and `SimTraceFormatters.kt` in this package.
 *
 * Use sparingly: every snapshot is a SimState reference (cheap) but the
 * list grows linearly with event count. G0's run produces ~1500 events
 * over 30 sim minutes; the trace fits in memory comfortably. The
 * [SimTrace] constructor enforces a 100k-entry cap as a defensive
 * fail-loud against tight loops.
 *
 * fn-12.3 (R10 — world-only test trigger): optional [onAfterEvent] hook
 * runs **after each step** and may return a transformed [SimState]. The
 * snapshot persisted in the trace is the post-hook state, so observers
 * see the world as the controllers will on the next cycle. Default is
 * identity (no mutation).
 *
 * The hook is the canonical injection point for **world-only**
 * authorship triggers (per `feedback_world_only_test_triggers.md`) —
 * e.g. setting `runway.obstruction = Some(RunwayObstruction(...))` when
 * an aircraft reaches a target phase. The hook MUST NOT inject
 * `ControllerEvent`s or mutate `BeliefState` directly; the sim's
 * per-cycle world-diff producer handles event derivation from world
 * state, preserving the firewall contract. Hook implementations should
 * be idempotent or guarded (one-shot authorship) so they do not refresh
 * fields that violate immutability invariants (e.g.
 * [xyz.easiersaid.twr.core.world.RunwayObstruction]'s `clearsAt`).
 */
fun runUntilWithStateTrace(
    initialState: SimState,
    initialEvents: List<SimEvent>,
    untilTime: SimTime,
    onAfterEvent: (SimEvent, SimState) -> SimState = { _, st -> st },
): StateTraceResult {
    var state = initialState
    val (stamped, stampedEvents) = state.emit(initialEvents)
    state = stamped
    val queue = HeapEventQueue()
    stampedEvents.forEach(queue::enqueue)
    val eventTrace = mutableListOf<SimEvent>()
    val statesAfterEvent = mutableListOf<Pair<SimEvent, SimState>>()
    var nextEvent = queue.dequeueMin()
    while (nextEvent != null && nextEvent.time <= untilTime) {
        eventTrace += nextEvent
        val (next, emitted) = step(state, nextEvent)
        val postHook = onAfterEvent(nextEvent, next)
        statesAfterEvent.add(nextEvent to postHook)
        state = postHook
        emitted.forEach(queue::enqueue)
        nextEvent = queue.dequeueMin()
    }
    val records = eventTrace.filterIsInstance<SimEvent.TransmissionStart>()
        .map { it.toTransmissionRecord() }
    return StateTraceResult(
        finalState = state,
        records = records.toList(),
        trace = SimTrace.from(initialState, statesAfterEvent.toList()),
    )
}
