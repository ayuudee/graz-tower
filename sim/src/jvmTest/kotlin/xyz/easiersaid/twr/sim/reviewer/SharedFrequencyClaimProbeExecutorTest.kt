package xyz.easiersaid.twr.sim.reviewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates the `shared_frequency_readback_overlap` claim-probe executor.
 *
 * These tests verify the probe's own internal contracts — nominal and attack
 * scenarios produce the expected reports — so that the morning review is
 * grounded in verified probe behaviour rather than untested harness code.
 */
class SharedFrequencyClaimProbeExecutorTest {

    @Test
    fun `nominal scenario delivers readback to controller inbox`() {
        val report = SharedFrequencyClaimProbeExecutor.runNominal()

        assertEquals(
            emptySet(),
            report.unsatisfiedSuccessChecks,
            "All success checks must pass on the nominal path",
        )
        assertEquals(
            setOf(
                "required_readback_received_on_same_frequency",
                "no_progress_before_ack",
            ),
            report.satisfiedSuccessChecks,
        )
    }

    @Test
    fun `attack scenario triggers missing_required_ack_before_state_change`() {
        val report = SharedFrequencyClaimProbeExecutor.runAttack()

        assertEquals("STEP_ON_READBACK", report.attackId)
        assertTrue(
            "missing_required_ack_before_state_change" in report.triggeredIssueDetectors,
            "Step-on must prevent readback delivery and fire the missing-ack detector",
        )
        assertTrue(report.unsupportedDetectors.isEmpty())
    }
}
