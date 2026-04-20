package xyz.easiersaid.twr.controller.observe

import xyz.easiersaid.twr.protocol.AircraftId

/**
 * State changes derived from observation history without pilot reports.
 *
 * The controller sees closure rate, vertical rate, track deviation, and speed
 * trend from successive observations. These feed into the separation engine
 * as input signals — closure rate is the critical one.
 */
data class ObservationDelta(
    val aircraft: AircraftId,
    /** Positive = gaining speed, negative = losing speed. Null = insufficient data. */
    val groundSpeedTrend: SpeedTrend?,
    /** Vertical rate in FPM, derived from altitude change / time. Null = insufficient data. */
    val verticalRateFpm: Double?,
)

enum class SpeedTrend { ACCELERATING, DECELERATING, STABLE }

/**
 * Derive observation deltas from the history buffer for all tracked aircraft.
 *
 * Runs between `updateBeliefs` and `updateArrivalSequence` in the pipeline.
 * Currently derives speed trend and vertical rate. Closure rate is computed
 * pair-wise in the separation engine (not per-aircraft).
 */
fun deriveObservationDeltas(beliefs: BeliefState): Map<AircraftId, ObservationDelta> {
    return beliefs.previousPositions.mapNotNull { (acId, history) ->
        if (history.size < 2) return@mapNotNull null
        val latest = history.last()
        val prev = history[history.size - 2]

        // Speed trend.
        val speedTrend = if (latest.groundSpeed != null && prev.groundSpeed != null) {
            val delta = latest.groundSpeed.value - prev.groundSpeed.value
            when {
                delta > 2 -> SpeedTrend.ACCELERATING
                delta < -2 -> SpeedTrend.DECELERATING
                else -> SpeedTrend.STABLE
            }
        } else null

        // Vertical rate from altitude change.
        val verticalRate = deriveVerticalRate(latest, prev)

        acId to ObservationDelta(acId, speedTrend, verticalRate)
    }.toMap()
}

private fun deriveVerticalRate(
    latest: ObservationSnapshot,
    prev: ObservationSnapshot,
): Double? {
    val latestAlt = latest.altitude?.let { altToFeet(it) } ?: return null
    val prevAlt = prev.altitude?.let { altToFeet(it) } ?: return null
    val timeDeltaSeconds = (latest.time.millis - prev.time.millis) / 1000.0
    if (timeDeltaSeconds <= 0) return null
    return (latestAlt - prevAlt) / (timeDeltaSeconds / 60.0) // FPM
}

private fun altToFeet(level: xyz.easiersaid.twr.protocol.Level): Double = when (level) {
    is xyz.easiersaid.twr.protocol.Level.AltitudeFeet -> level.feet.toDouble()
    is xyz.easiersaid.twr.protocol.Level.FlightLevel -> level.fl * 100.0
    is xyz.easiersaid.twr.protocol.Level.HeightFeet -> level.feet.toDouble()
}
