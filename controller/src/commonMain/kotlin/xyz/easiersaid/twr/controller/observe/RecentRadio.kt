package xyz.easiersaid.twr.controller.observe

import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime

/** Time-stamped controller event for [RecentRadio]'s ring-buffer. */
data class TimestampedRadio(val time: SimTime, val event: ControllerEvent)

/**
 * Time-windowed buffer of recent radio events for a single aircraft.
 *
 * Pass 5 (D-AUDIT.14 closure): replaces the cached `aircraftIntent` slice.
 * The buffer's invariant — "all entries are within the configured time
 * window of the latest" — is enforced by the smart-constructor pattern: the
 * primary constructor is `private`, the only mutation path is [append],
 * which prunes entries older than `effectiveNow - window`.
 *
 * The `data class` shape (private constructor) is preferred over a
 * `@JvmInline value class` because the buffer is immediately wrapped in a
 * `Map<AircraftId, RecentRadio>` (the `Map` boxes anyway, so `value class`
 * gains nothing), and the `equals/hashCode/toString` defaults from
 * `data class` are useful for test-diff messages and debug snapshots.
 *
 * **Monotonicity clamp.** [append] clamps `now` against the most-recent
 * entry's time so a clock regression (sim-replay merge, cross-cycle
 * reorder) cannot retain entries past their real cutoff. The invariant
 * becomes "all entries are within `window` of `max(now, last_entry_time)`"
 * — which is the property a reader of `recentRadio[ac]` actually relies
 * on. Without the clamp, an out-of-order append with `now < tail.time`
 * would silently widen the effective window.
 *
 * Performance note (Impact O.3): under load (~100 aircraft, ~30 events/cycle,
 * ~50 entries/window) the `filter + plus` rebuild is ~150k cell-touches
 * per cycle — sub-millisecond on modern hardware. An `ArrayDeque`-backed
 * implementation with O(1) prune-from-front is a future optimisation; the
 * smart-constructor surface stays identical, only the internal storage
 * changes.
 */
data class RecentRadio private constructor(val entries: List<TimestampedRadio>) {

    /** Append [event] at [now] and prune entries older than `effectiveNow - window`. */
    fun append(event: ControllerEvent, now: SimTime, window: SimDuration): RecentRadio {
        val effectiveNowMs = maxOf(now.millis, entries.lastOrNull()?.time?.millis ?: now.millis)
        val cutoffMs = effectiveNowMs - window.millis
        val pruned = entries.filter { it.time.millis >= cutoffMs }
        return RecentRadio(pruned + TimestampedRadio(now, event))
    }

    companion object {
        val EMPTY: RecentRadio = RecentRadio(emptyList())
    }
}
