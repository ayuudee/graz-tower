package xyz.easiersaid.twr.controller.observe

import xyz.easiersaid.twr.controller.bdi.Stage
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.AtomicReadback
import xyz.easiersaid.twr.protocol.SimTime

/**
 * An instruction-readback coordination tracked by the controller.
 *
 * Lifecycle (Pass 9 D-AUDIT.2):
 *   Issued → (correct readback) → removed (CONFIRMED is absence)
 *   Issued → (wrong readback) → CORRECTING (handled by Readback fold; not a state)
 *   Issued → (timeout) → Querying → Reissued(N) → LostCommsDeclared
 *   Issued / Querying / Reissued → (superseded) → removed (CANCELLED is absence)
 *
 * Every ATC instruction is the START of a coordination. The controller's
 * commitment stage advances only when the coordination reaches CONFIRMED
 * (entry removed by `acceptReadback`), not when the instruction is
 * emitted. This ensures the controller's mental model reflects what was
 * actually communicated, not what was intended.
 */
data class OutstandingCoordination(
    val aircraft: AircraftId,
    val instruction: AtcInstruction,
    val expectedReadback: Set<AtomicReadback>,
    val issuedAt: SimTime,
    val state: CoordinationState = CoordinationState.Issued,
    /** The stage to advance to when this coordination is CONFIRMED. Null = no advancement. */
    val advanceToStage: Stage? = null,
)

/**
 * Lifecycle state of an instruction-readback coordination.
 *
 * `CONFIRMED` and `CANCELLED` are not leaves: they are the *absence* of an
 * entry in `BeliefState.coordinations`. Confirmation removes via
 * `acceptReadback`; supersession removes via `applySupersessionCleanup`.
 * Mirrors `ResponsibilityState`'s "Released = absence" pattern (Pass 7).
 *
 * Pass 9 (D-AUDIT.2): replaces the pre-Pass-9 enum with a sealed type
 * whose leaves carry their own escalation timestamps.
 */
sealed interface CoordinationState {
    /** Instruction transmitted, awaiting readback. */
    data object Issued : CoordinationState

    /**
     * Readback overdue. Controller has emitted (or will emit this cycle)
     * `ConfirmInstruction` phraseology — "[callsign], confirm [instruction]"
     * (CAP 413 Glossary; Doc 4444 §12.3.1.2).
     *
     * [emittedAt] dampens cycle re-emission: `coordinationEscalationOutputs`
     * emits when `emittedAt == null` (just-transitioned). The companion
     * `markCoordinationEscalationsEmitted` sets `emittedAt = now` after
     * emission so the next cycle does not re-fire until the *next* state
     * transition.
     */
    data class Querying(val queriedAt: SimTime, val emittedAt: SimTime?) : CoordinationState

    /**
     * Re-issued after [Querying] timed out. [attemptCount] is 1 on first
     * re-issue, incremented on subsequent re-emits. Phraseology marker
     * "I SAY AGAIN" (Doc 4444 §12.3.1.2) is a future formatter concern,
     * not encoded here.
     *
     * [emittedAt] dampens cycle re-emission (see [Querying] doc).
     */
    data class Reissued(val reissuedAt: SimTime, val attemptCount: Int, val emittedAt: SimTime?) : CoordinationState {
        init { require(attemptCount >= 1) { "Reissued.attemptCount must be ≥ 1, got $attemptCount" } }
    }

    /**
     * Re-issue attempts exhausted. Per Doc 4444 §15.1.4 there is no
     * on-frequency phraseology; the controller transmits blind, never
     * declaring on the working frequency. Terminal: the entry persists
     * for diagnostics until the aircraft leaves responsibility or is
     * actively superseded.
     *
     * Carries only [declaredAt]. The original `issuedAt` lives on the
     * parent [OutstandingCoordination]; **doc-pin: do not duplicate it
     * here.** Diagnostic post-mortem joins through the parent.
     *
     * Cleanup on aircraft departure is filed as **D-AUDIT.2.B-FOLLOWUP**.
     */
    data class LostCommsDeclared(val declaredAt: SimTime) : CoordinationState
}

/**
 * How a rule's stage transition should be applied.
 *
 * All rules now use [Immediate]. Readback-gated advancement is handled by
 * [xyz.easiersaid.twr.controller.bdi.AtcRule.readbackAdvancesToStage] which
 * records the confirmation stage on the coordination ledger while the
 * initial stage advances immediately.
 */
sealed interface AdvancementPolicy {
    /** Stage advances immediately when the rule fires. */
    data object Immediate : AdvancementPolicy
}
