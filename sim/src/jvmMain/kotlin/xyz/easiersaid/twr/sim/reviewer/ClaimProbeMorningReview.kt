package xyz.easiersaid.twr.sim.reviewer

/**
 * Morning review aggregator for overnight claim-probe runs.
 *
 * Collects results from all registered claim-probe executors and renders a
 * structured Markdown report. A human reviewer reads this at the start of the
 * day: CANDIDATE_ISSUE entries require attention; CLEAN_EXECUTED entries are
 * confirmation that the nominal paths still hold.
 *
 * The report intentionally surfaces both detector names and surface IDs so
 * the reviewer knows exactly what to look for in the simulation logs.
 *
 * Phase 0 surfaces registered here:
 *   - `shared_frequency_readback_overlap` ([SharedFrequencyClaimProbeExecutor])
 *   - `contact_frequency_handoff` ([ContactFrequencyClaimProbeExecutor])
 */
object ClaimProbeMorningReview {

    data class ReviewResult(
        val reports: List<ClaimProbeExecutionReport>,
        val markdown: String,
    ) {
        val candidateIssueCount: Int get() = reports.count { it.disposition == ClaimProbeDisposition.CANDIDATE_ISSUE }
        val coverageGapCount: Int get() = reports.count { it.disposition == ClaimProbeDisposition.COVERAGE_GAP }
        val nominalFailureCount: Int get() = reports.count { it.disposition == ClaimProbeDisposition.NOMINAL_FAILURE }
    }

    fun review(): ReviewResult {
        val reports = listOf(
            SharedFrequencyClaimProbeExecutor.execute(),
            ContactFrequencyClaimProbeExecutor.execute(),
        )
        return ReviewResult(reports = reports, markdown = render(reports))
    }

    private fun render(reports: List<ClaimProbeExecutionReport>): String = buildString {
        appendLine("# Overnight Claim-Probe Review")
        appendLine()
        val candidateCount = reports.count { it.disposition == ClaimProbeDisposition.CANDIDATE_ISSUE }
        val cleanCount = reports.count { it.disposition == ClaimProbeDisposition.CLEAN_EXECUTED }
        val failureCount = reports.count { it.disposition != ClaimProbeDisposition.CANDIDATE_ISSUE && it.disposition != ClaimProbeDisposition.CLEAN_EXECUTED }
        appendLine("**Summary**: ${reports.size} surfaces — $candidateCount candidate issue(s), $cleanCount clean, $failureCount other")
        appendLine()

        for (report in reports) {
            appendLine("## Surface: `${report.surfaceId}`")
            appendLine()
            appendLine("**Disposition**: ${report.disposition.name}")
            appendLine()

            appendLine("### Nominal")
            if (report.nominalReport.satisfiedSuccessChecks.isNotEmpty()) {
                appendLine("Satisfied: ${report.nominalReport.satisfiedSuccessChecks.sorted().joinToString(", ")}")
            }
            if (report.nominalReport.unsatisfiedSuccessChecks.isNotEmpty()) {
                appendLine("Unsatisfied: ${report.nominalReport.unsatisfiedSuccessChecks.sorted().joinToString(", ")}")
            }
            appendLine()

            for (attack in report.attackReports) {
                appendLine("### Attack: `${attack.attackId}`")
                if (attack.triggeredIssueDetectors.isNotEmpty()) {
                    appendLine("Triggered detectors: ${attack.triggeredIssueDetectors.sorted().joinToString(", ")}")
                } else {
                    appendLine("No detectors triggered.")
                }
                if (attack.unsupportedDetectors.isNotEmpty()) {
                    appendLine("Unsupported detectors: ${attack.unsupportedDetectors.sorted().joinToString(", ")}")
                }
                appendLine()
            }
        }
    }
}
