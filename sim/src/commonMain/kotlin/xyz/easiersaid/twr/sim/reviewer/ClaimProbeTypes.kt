package xyz.easiersaid.twr.sim.reviewer

/**
 * Claim-probe Phase 0: structured adversarial surface explorer for the overnight reviewer.
 *
 * Each probe exercises one "claim surface" — a contract the sim must honour.
 * A probe runs a NOMINAL scenario (expected path) and one or more ATTACK scenarios
 * (adversarial or failure-mode paths). The [ClaimProbeDisposition] summarises whether
 * the nominal worked and whether the attack exposed a real failure mode.
 *
 * [CANDIDATE_ISSUE] is the *desired* outcome for a meaningful probe: the attack
 * scenario triggered issue detectors, confirming the failure mode is reachable.
 * The morning reviewer audits CANDIDATE_ISSUE reports — they are not bugs, they are
 * confirmed adversarial exposures worth human review.
 *
 * Phase 0 surfaces:
 *   - `shared_frequency_readback_overlap`: step-on prevention on a shared ATC frequency.
 *   - `contact_frequency_handoff`: coordination ledger survives responsibility transfer.
 *
 * Phase 1 (autonomous adversarial loop) is tracked as OR-3.
 */

/**
 * Summary verdict for one probe execution.
 *
 * [CANDIDATE_ISSUE]   — attack triggered at least one issue detector; the failure mode is real.
 * [CLEAN_EXECUTED]    — nominal passed, attack ran but no detectors fired (defence held).
 * [NOMINAL_FAILURE]   — nominal scenario itself failed its success checks.
 * [COVERAGE_GAP]      — probe could not be constructed or seeded correctly.
 * [EXECUTION_REJECTED] — execution infrastructure rejected the probe (bad setup).
 */
enum class ClaimProbeDisposition {
    CANDIDATE_ISSUE,
    CLEAN_EXECUTED,
    NOMINAL_FAILURE,
    COVERAGE_GAP,
    EXECUTION_REJECTED,
}

/** Nominal-path evaluation: which success checks passed and which did not. */
data class NominalReport(
    val satisfiedSuccessChecks: Set<String>,
    val unsatisfiedSuccessChecks: Set<String>,
)

/**
 * Result of one attack scenario.
 *
 * [triggeredIssueDetectors] — detectors that fired (failure mode confirmed).
 * [unsupportedDetectors]    — detectors that could not be evaluated in this probe.
 */
data class AttackReport(
    val attackId: String,
    val triggeredIssueDetectors: Set<String>,
    val unsupportedDetectors: Set<String>,
)

/** Full execution report for one claim surface. */
data class ClaimProbeExecutionReport(
    val surfaceId: String,
    val nominalReport: NominalReport,
    val attackReports: List<AttackReport>,
    val disposition: ClaimProbeDisposition,
)

/**
 * Derive [ClaimProbeDisposition] from nominal and attack results.
 *
 * Priority order:
 *  1. Nominal failure → [NOMINAL_FAILURE] (attack result is irrelevant).
 *  2. Any attack triggered detectors → [CANDIDATE_ISSUE].
 *  3. No detectors triggered → [CLEAN_EXECUTED].
 */
fun computeDisposition(
    nominalReport: NominalReport,
    attackReports: List<AttackReport>,
): ClaimProbeDisposition = when {
    nominalReport.unsatisfiedSuccessChecks.isNotEmpty() -> ClaimProbeDisposition.NOMINAL_FAILURE
    attackReports.any { it.triggeredIssueDetectors.isNotEmpty() } -> ClaimProbeDisposition.CANDIDATE_ISSUE
    else -> ClaimProbeDisposition.CLEAN_EXECUTED
}
