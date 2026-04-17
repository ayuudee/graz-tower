package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.SimTime

/**
 * Run the simulation forward until no events remain at or before [until].
 *
 * This is the only code that touches the [EventQueue]; the step function is
 * queue-agnostic by design. The loop is a [tailrec] function so the driver is
 * expressed as a fold over `(state, queue) -> state'` — swapping in a
 * persistent queue later is a [queueFactory] change, not a loop rewrite.
 *
 * Semantics:
 *   - Events at exactly [until] fire.
 *   - Events strictly after [until] stop the loop (they stay in the queue and
 *     are discarded with it — this entry point is for terminal replay, not
 *     resumable sessions).
 *   - If the queue drains before [until], the final state's `now` is the
 *     time of the last event processed.
 */
fun runUntil(
    initial: SimState,
    initialEvents: List<SimEvent>,
    until: SimTime,
    queueFactory: () -> EventQueue = ::HeapEventQueue,
): SimState {
    // Initial events share seq=0 and possibly the same source; route them
    // through [SimState.emit] so they pick up distinct, monotonic seq values
    // before entering the queue. Without this, two System-sourced events at
    // the same time would tie on (time, source, seq) and dequeue order would
    // depend on the heap's internal behaviour.
    val (stamped, stampedEvents) = initial.emit(initialEvents)
    val queue = queueFactory()
    stampedEvents.forEach(queue::enqueue)
    return drive(stamped, queue, until)
}

private tailrec fun drive(state: SimState, queue: EventQueue, until: SimTime): SimState {
    val event = queue.dequeueMin() ?: return state
    if (event.time > until) return state
    val (next, emitted) = step(state, event)
    emitted.forEach(queue::enqueue)
    return drive(next, queue, until)
}
