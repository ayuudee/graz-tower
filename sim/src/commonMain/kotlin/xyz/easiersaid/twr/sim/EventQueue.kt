package xyz.easiersaid.twr.sim

/**
 * Priority queue of pending [SimEvent]s, ordered by `(time, source, seq)`.
 *
 * The interface exists so `step` never sees a concrete queue. The default
 * implementation ([HeapEventQueue]) wraps `java.util.PriorityQueue`; a
 * persistent pairing-heap or a test-replay recorder can be substituted
 * without touching any other code.
 *
 * Only the driver ([runUntil]) consumes this interface — everywhere else
 * emissions flow through `List<SimEvent>` returned from [step].
 */
interface EventQueue {
    fun enqueue(event: SimEvent)

    /** Remove and return the earliest event, or null if empty. */
    fun dequeueMin(): SimEvent?

    val isEmpty: Boolean
}

/** Total order on events: time first, then source (stable via [AgentId.sortKey]), then seq. */
internal val EVENT_ORDER: Comparator<SimEvent> = Comparator { a, b ->
    val byTime = a.time.compareTo(b.time)
    if (byTime != 0) return@Comparator byTime
    val bySource = a.source.compareTo(b.source)
    if (bySource != 0) return@Comparator bySource
    a.seq.compareTo(b.seq)
}
