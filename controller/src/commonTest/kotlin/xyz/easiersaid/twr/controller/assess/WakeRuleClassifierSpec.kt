package xyz.easiersaid.twr.controller.assess

import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.easiersaid.twr.protocol.WakeCategory

/**
 * fn-8.1: [classifyWakeRule] returns the diagnostic [WakeRule] for a
 * `(leader, follower)` pair without disturbing the numeric
 * [requiredWakeSeparation] minimum.
 *
 * Three regions of behaviour:
 *  - **Table hit** (J→J, J→H, J→M, J→L, H→H, H→M, H→L, M→L) →
 *    [WakeRule.IcaoLeaderFollower] with the table's `distanceNm`.
 *  - **Fallback** (any pair NOT in [ICAO_WAKE_TABLE], including L→L,
 *    L→M, M→M) → [WakeRule.IcaoNoAdditionalWakeMinimum] preserving
 *    both categories so non-same-category fallbacks are diagnosable.
 *  - **Unknown** (null on either side) → [WakeRule.UnknownCategory].
 */
class WakeRuleClassifierSpec {

    @Test
    fun `J to J is a table hit at 6 NM`() {
        assertEquals(
            WakeRule.IcaoLeaderFollower(
                leader = WakeCategory.J,
                follower = WakeCategory.J,
                wakeMinimumNm = 6.0,
            ),
            classifyWakeRule(WakeCategory.J, WakeCategory.J),
        )
    }

    @Test
    fun `H to H is a table hit at 4 NM`() {
        assertEquals(
            WakeRule.IcaoLeaderFollower(
                leader = WakeCategory.H,
                follower = WakeCategory.H,
                wakeMinimumNm = 4.0,
            ),
            classifyWakeRule(WakeCategory.H, WakeCategory.H),
        )
    }

    @Test
    fun `J to L is a table hit at 8 NM`() {
        assertEquals(
            WakeRule.IcaoLeaderFollower(
                leader = WakeCategory.J,
                follower = WakeCategory.L,
                wakeMinimumNm = 8.0,
            ),
            classifyWakeRule(WakeCategory.J, WakeCategory.L),
        )
    }

    @Test
    fun `L to L is fallback - no leader-L row in table`() {
        // Same-category does NOT imply "no additional minimum" — J->J and H->H
        // both have explicit table rows. Only pairs missing from the table
        // (no leader-L row, no leader-M-with-non-L follower) hit the fallback.
        assertEquals(
            WakeRule.IcaoNoAdditionalWakeMinimum(
                leader = WakeCategory.L,
                follower = WakeCategory.L,
            ),
            classifyWakeRule(WakeCategory.L, WakeCategory.L),
        )
    }

    @Test
    fun `L to M is fallback - leader-L row missing covers non-same-category`() {
        // The fallback covers ANY non-listed pair, not only same-category.
        // L→M would otherwise look like "lighter behind heavier" but ICAO
        // §5.8 has no leader-L row at all. Both categories are preserved
        // so the case is diagnosable.
        assertEquals(
            WakeRule.IcaoNoAdditionalWakeMinimum(
                leader = WakeCategory.L,
                follower = WakeCategory.M,
            ),
            classifyWakeRule(WakeCategory.L, WakeCategory.M),
        )
    }

    @Test
    fun `M to M is fallback - no medium-on-medium table row`() {
        assertEquals(
            WakeRule.IcaoNoAdditionalWakeMinimum(
                leader = WakeCategory.M,
                follower = WakeCategory.M,
            ),
            classifyWakeRule(WakeCategory.M, WakeCategory.M),
        )
    }

    @Test
    fun `null leader produces UnknownCategory`() {
        assertEquals(WakeRule.UnknownCategory, classifyWakeRule(null, WakeCategory.M))
    }

    @Test
    fun `null follower produces UnknownCategory`() {
        assertEquals(WakeRule.UnknownCategory, classifyWakeRule(WakeCategory.H, null))
    }

    @Test
    fun `both null produces UnknownCategory`() {
        assertEquals(WakeRule.UnknownCategory, classifyWakeRule(null, null))
    }
}
