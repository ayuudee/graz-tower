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

/**
 * Diagnostic record of which wake-separation rule applied to a pair.
 *
 * Carried alongside the numeric `requiredSeparationNm` on
 * [xyz.easiersaid.twr.controller.observe.SeparationAssessment] so downstream
 * consumers (logs, diagnostics, future RECAT-EU overlay) can see *why* a
 * minimum was chosen, not just the magnitude. NM throughout — no Meters.
 *
 * Three cases:
 *  - [IcaoLeaderFollower] — `(leader, follower)` has an explicit row in
 *    [ICAO_WAKE_TABLE] (e.g. J→J 6.0 NM, H→H 4.0 NM). Carries the explicit
 *    minimum from the table.
 *  - [IcaoNoAdditionalWakeMinimum] — pair has no entry in [ICAO_WAKE_TABLE]
 *    (e.g. L→L, L→M, M→M, or any other non-listed combination). Per
 *    ICAO Doc 4444 §5.8, the radar minimum applies and there is no wake
 *    supplement. **Both leader and follower categories are preserved** so
 *    the case is diagnosable for non-same-category fallbacks (e.g. L→M).
 *  - [UnknownCategory] — at least one aircraft has no published wake
 *    category. Engine fails closed; classifier surfaces the unknown.
 */
sealed interface WakeRule {
    /**
     * No additional wake minimum applies — fallback for any pair not in
     * [ICAO_WAKE_TABLE] (covers L→L, L→M, M→M, etc.). The radar minimum
     * applies via [RADAR_MINIMUM_NM].
     */
    data class IcaoNoAdditionalWakeMinimum(
        val leader: WakeCategory,
        val follower: WakeCategory,
    ) : WakeRule

    /** Explicit ICAO Doc 4444 §5.8 wake supplement from [ICAO_WAKE_TABLE]. */
    data class IcaoLeaderFollower(
        val leader: WakeCategory,
        val follower: WakeCategory,
        val wakeMinimumNm: Double,
    ) : WakeRule

    /**
     * Wake category absent / unknown for at least one aircraft. The engine
     * must fall back to a conservative default (today: treat as Heavy, see
     * [requiredWakeSeparation]) and consumers may surface the unknown to
     * the controller's diagnostic channel.
     */
    data object UnknownCategory : WakeRule
}

/**
 * Classify the wake rule for a `(leader, follower)` pair against the
 * canonical [ICAO_WAKE_TABLE]. Total: every input maps to a [WakeRule]
 * variant.
 *
 * Used by [xyz.easiersaid.twr.controller.assess.assessSeparation] to
 * populate [xyz.easiersaid.twr.controller.observe.SeparationAssessment.wakeRule]
 * alongside the numeric `requiredSeparationNm`.
 *
 * Three concrete examples:
 *  - L→L: not in table → [WakeRule.IcaoNoAdditionalWakeMinimum] with
 *    `leader = L, follower = L`.
 *  - L→M: not in table (no leader-L row exists) →
 *    [WakeRule.IcaoNoAdditionalWakeMinimum] with `leader = L, follower = M`.
 *  - J→J: in table at 6.0 NM → [WakeRule.IcaoLeaderFollower] with
 *    `wakeMinimumNm = 6.0`.
 */
fun classifyWakeRule(leader: WakeCategory?, follower: WakeCategory?): WakeRule {
    if (leader == null || follower == null) return WakeRule.UnknownCategory
    val tableHit = WAKE_TABLE_INDEX[leader to follower]
    return if (tableHit == null) {
        WakeRule.IcaoNoAdditionalWakeMinimum(leader = leader, follower = follower)
    } else {
        WakeRule.IcaoLeaderFollower(
            leader = leader,
            follower = follower,
            wakeMinimumNm = tableHit.distanceNm,
        )
    }
}
