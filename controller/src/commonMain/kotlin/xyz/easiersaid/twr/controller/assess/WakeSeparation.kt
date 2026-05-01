package xyz.easiersaid.twr.controller.assess

import xyz.easiersaid.twr.protocol.WakeCategory

/**
 * Wake turbulence separation minima per ICAO Doc 4444 §5.8 (17th ed.).
 *
 * Pure data table: leader-follower → required distance (NM) and time (minutes).
 * RECAT-EU is a future switchable overlay (Phase 7+).
 */
data class WakeSeparationMinima(
    val leader: WakeCategory,
    val follower: WakeCategory,
    val distanceNm: Double,
    val timeMinutes: Double,
)

/** Standard radar minimum when no additional wake minimum applies. */
const val RADAR_MINIMUM_NM = 3.0

/** Standard time minimum when no additional wake time applies. */
const val STANDARD_TIME_MINUTES = 2.0

/**
 * ICAO Doc 4444 §5.8 baseline wake separation table.
 * Pairs not in this table use [RADAR_MINIMUM_NM] / [STANDARD_TIME_MINUTES].
 */
val ICAO_WAKE_TABLE: List<WakeSeparationMinima> = listOf(
    // Super (J) leading
    WakeSeparationMinima(WakeCategory.J, WakeCategory.J, 6.0, 2.0),
    WakeSeparationMinima(WakeCategory.J, WakeCategory.H, 6.0, 2.0),
    WakeSeparationMinima(WakeCategory.J, WakeCategory.M, 7.0, 3.0),
    WakeSeparationMinima(WakeCategory.J, WakeCategory.L, 8.0, 3.0),
    // Heavy (H) leading
    WakeSeparationMinima(WakeCategory.H, WakeCategory.H, 4.0, 2.0),
    WakeSeparationMinima(WakeCategory.H, WakeCategory.M, 5.0, 2.0),
    WakeSeparationMinima(WakeCategory.H, WakeCategory.L, 6.0, 3.0),
    // Medium (M) leading lighter
    WakeSeparationMinima(WakeCategory.M, WakeCategory.L, 5.0, 3.0),
)

private val WAKE_TABLE_INDEX: Map<Pair<WakeCategory, WakeCategory>, WakeSeparationMinima> =
    ICAO_WAKE_TABLE.associateBy { it.leader to it.follower }

/**
 * Look up required wake separation for a leader-follower pair.
 *
 * Returns the ICAO wake minimum if the pair has one; otherwise [RADAR_MINIMUM_NM] /
 * [STANDARD_TIME_MINUTES] (same-category non-J, or lighter-behind-heavier).
 *
 * Unknown wake category (null) defaults to [WakeCategory.H] (worst-case conservative).
 */
fun requiredWakeSeparation(
    leader: WakeCategory?,
    follower: WakeCategory?,
): WakeSeparationMinima {
    val effectiveLeader = leader ?: WakeCategory.H
    val effectiveFollower = follower ?: WakeCategory.H
    return WAKE_TABLE_INDEX[effectiveLeader to effectiveFollower]
        ?: WakeSeparationMinima(effectiveLeader, effectiveFollower, RADAR_MINIMUM_NM, STANDARD_TIME_MINUTES)
}

/** Convert NM to metres. */
const val METRES_PER_NM = 1852.0

/** Convert metres to NM. */
fun metresToNm(metres: Double): Double = metres / METRES_PER_NM
