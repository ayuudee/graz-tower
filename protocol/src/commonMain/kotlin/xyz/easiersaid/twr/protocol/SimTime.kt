package xyz.easiersaid.twr.protocol

/**
 * Continuous simulation time in milliseconds.
 *
 * Replaces the discrete [TickNumber] model. Millisecond precision is sufficient
 * for ATC (sub-millisecond timing is meaningless in this domain) and avoids
 * floating-point accumulation errors over long simulations.
 *
 * Used throughout the simulation for event scheduling, clearance timestamps,
 * and controller decision timing. The controller receives [SimTime] in its view
 * and never assumes discrete ticks.
 */
@JvmInline
value class SimTime(val millis: Long) : Comparable<SimTime> {
    override fun compareTo(other: SimTime): Int = millis.compareTo(other.millis)

    operator fun plus(duration: SimDuration): SimTime = SimTime(millis + duration.millis)
    operator fun minus(other: SimTime): SimDuration = SimDuration(millis - other.millis)

    companion object {
        val ZERO = SimTime(0L)
        fun ofSeconds(s: Int): SimTime = SimTime(s.toLong() * 1000L)
        fun ofSeconds(s: Long): SimTime = SimTime(s * 1000L)
        fun ofMillis(ms: Long): SimTime = SimTime(ms)
    }
}

/**
 * A duration in the simulation, in milliseconds.
 *
 * Used for transmission timing (word count -> duration), cognitive delays,
 * physics integration steps, and scheduling intervals.
 */
@JvmInline
value class SimDuration(val millis: Long) : Comparable<SimDuration> {
    override fun compareTo(other: SimDuration): Int = millis.compareTo(other.millis)

    operator fun plus(other: SimDuration): SimDuration = SimDuration(millis + other.millis)
    operator fun minus(other: SimDuration): SimDuration = SimDuration(millis - other.millis)
    operator fun times(factor: Int): SimDuration = SimDuration(millis * factor)
    operator fun times(factor: Double): SimDuration = SimDuration((millis * factor).toLong())

    val seconds: Double get() = millis / 1000.0

    companion object {
        val ZERO = SimDuration(0L)
        fun ofSeconds(s: Int): SimDuration = SimDuration(s.toLong() * 1000L)
        fun ofSeconds(s: Long): SimDuration = SimDuration(s * 1000L)
        fun ofMillis(ms: Long): SimDuration = SimDuration(ms)
    }
}
