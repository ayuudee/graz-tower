package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.bdi.Stage

/**
 * Result of observation-driven stage reconciliation.
 *
 * Carries both the reconciled stage and how we got there, so the action
 * layer can respond differently to expected vs anomalous transitions.
 */
data class ReconciledStage<out S : Stage>(
    val stage: S,
    val transition: TransitionKind,
)

/**
 * How the reconciled stage relates to the previous stage.
 *
 * [UNCHANGED] and [EXPECTED] are normal operation. [ADVANCED] means the
 * aircraft is ahead of the controller's expectations — the controller should
 * accept reality and may need to catch up (e.g. issue a handoff sooner).
 * [ANOMALOUS] means the observation is inconsistent with what should have
 * happened — the controller should flag a safety concern.
 */
enum class TransitionKind {
    /** No change from current stage. */
    UNCHANGED,
    /** Observation matches the anticipated next step. */
    EXPECTED,
    /** Observation is ahead of expectation (pilot acted early or without clearance). */
    ADVANCED,
    /** Observation is inconsistent with expectations (incursion, unclearanced action). */
    ANOMALOUS,
}
