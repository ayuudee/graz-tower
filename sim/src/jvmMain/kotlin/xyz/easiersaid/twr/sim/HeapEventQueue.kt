package xyz.easiersaid.twr.sim

import java.util.PriorityQueue

/**
 * Default [EventQueue] backed by a mutable binary heap.
 *
 * Uses [java.util.PriorityQueue] with [EVENT_ORDER] as the comparator —
 * O(log n) enqueue / dequeue, well-tested. This is the only place in `:sim`
 * that references `java.util`; everything else is queue-agnostic through the
 * [EventQueue] interface.
 *
 * Not thread-safe: the engine is single-threaded by design (FoundationDB /
 * TigerBeetle / sled patterns), and determinism tests depend on it. If a
 * background-threaded runner is ever wanted, wrap it at the driver level, not
 * inside the queue.
 */
class HeapEventQueue : EventQueue {
    private val heap = PriorityQueue(EVENT_ORDER)

    override fun enqueue(event: SimEvent) {
        heap.add(event)
    }

    override fun dequeueMin(): SimEvent? = heap.poll()

    override val isEmpty: Boolean
        get() = heap.isEmpty()
}
