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
 * Pass 7 (D-AUDIT.5 G0 mid-handoff assertion): drive the sim and capture
 * a state snapshot **after each event is processed**. Returns the
 * transmission records (as `runUntilWithTransmissions`) plus the full
 * `(event → state-after)` trace, so a test can introspect what state
 * looked like at any moment during the run.
 *
 * Use sparingly: every snapshot is a SimState reference (cheap) but the
 * list grows linearly with event count. G0's run produces ~1500 events
 * over 30 sim minutes; the trace fits in memory comfortably.
 */
fun runUntilWithStateTrace(
    initialState: SimState,
    initialEvents: List<SimEvent>,
    untilTime: SimTime,
): Triple<SimState, List<TransmissionRecord>, List<Pair<SimEvent, SimState>>> {
    var state = initialState
    val (stamped, stampedEvents) = state.emit(initialEvents)
    state = stamped
    val queue = HeapEventQueue()
    stampedEvents.forEach(queue::enqueue)
    val trace = mutableListOf<SimEvent>()
    val statesAfterEvent = mutableListOf<Pair<SimEvent, SimState>>()
    var nextEvent = queue.dequeueMin()
    while (nextEvent != null && nextEvent.time <= untilTime) {
        trace += nextEvent
        val (next, emitted) = step(state, nextEvent)
        statesAfterEvent.add(nextEvent to next)
        state = next
        emitted.forEach(queue::enqueue)
        nextEvent = queue.dequeueMin()
    }
    val records = trace.filterIsInstance<SimEvent.TransmissionStart>()
        .map { it.toTransmissionRecord() }
    return Triple(state, records.toList(), statesAfterEvent.toList())
}
