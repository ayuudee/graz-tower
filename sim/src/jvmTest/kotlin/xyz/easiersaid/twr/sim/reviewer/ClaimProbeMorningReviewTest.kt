package xyz.easiersaid.twr.sim.reviewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end check of the morning review aggregator.
 *
 * Runs the full overnight review (both Phase 0 claim surfaces) and asserts the
 * structural properties the human reviewer relies on:
 *
 *  - Both surfaces report [ClaimProbeDisposition.CANDIDATE_ISSUE]: the attack
 *    scenarios reach their detectors, confirming the failure modes are real.
 *  - The Markdown report names both issue detectors so the reviewer knows
 *    exactly what to investigate.
 *  - No coverage gaps: the probe infrastructure is fully functional.
 */
class ClaimProbeMorningReviewTest {

    @Test
    fun `both Phase 0 surfaces are candidate issues and markdown names their detectors`() {
        val result = ClaimProbeMorningReview.review()

        assertEquals(
            2,
            result.candidateIssueCount,
            "Both Phase 0 surfaces must produce CANDIDATE_ISSUE",
        )
        assertEquals(
            0,
            result.coverageGapCount,
            "No coverage gaps — probe infrastructure must be fully wired",
        )
        assertEquals(
            0,
            result.nominalFailureCount,
            "No nominal failures — both nominal paths must execute successfully",
        )
        assertTrue(
            "missing_required_ack_before_state_change" in result.markdown,
            "Morning report must name the shared-frequency step-on detector",
        )
        assertTrue(
            "handoff_reissued_while_pending" in result.markdown,
            "Morning report must name the contact-frequency handoff detector",
        )
    }
}
