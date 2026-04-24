package xyz.easiersaid.twr.sim.reviewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates the `contact_frequency_handoff` claim-probe executor.
 *
 * Specifically tests that:
 *   - The nominal path (correct readback delivered) confirms and removes the
 *     coordination from the belief ledger.
 *   - The attack path (no readback) leaves the coordination ISSUED, triggering
 *     the `handoff_reissued_while_pending` detector.
 *
 * This exercises the OR-1 fix: coordinations must survive past responsibility
 * transfer so `processReadback` can confirm them after the handoff.
 */
class ContactFrequencyClaimProbeExecutorTest {

    @Test
    fun `nominal scenario confirms coordination and clears it from beliefs`() {
        val report = ContactFrequencyClaimProbeExecutor.runNominal()

        assertEquals(
            emptySet(),
            report.unsatisfiedSuccessChecks,
            "coordination_confirmed_on_correct_readback must pass on the nominal path",
        )
        assertEquals(
            setOf("coordination_confirmed_on_correct_readback"),
            report.satisfiedSuccessChecks,
        )
    }

    @Test
    fun `attack scenario triggers handoff_reissued_while_pending`() {
        val report = ContactFrequencyClaimProbeExecutor.runAttack()

        assertEquals("NO_READBACK_BEFORE_CHECK", report.attackId)
        assertTrue(
            "handoff_reissued_while_pending" in report.triggeredIssueDetectors,
            "Missing readback must leave the CF coordination ISSUED and fire the pending-handoff detector",
        )
        assertTrue(report.unsupportedDetectors.isEmpty())
    }
}
