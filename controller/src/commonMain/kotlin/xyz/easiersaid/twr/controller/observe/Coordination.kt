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
     * Re-issue attempts exhausted. Per Doc 4444 §15.1.4 the controller
     * transitions to "transmit blind" posture — they keep transmitting
     * on the assumption the pilot can hear; if they can, comply silently.
     * No on-frequency phraseology declares lost-comms.
     *
     * Pass 12 (D-AUDIT.2.A): on entry to this state,
     * `coordinationEscalationOutputs` emits one `TransmittingBlind`
     * `ControllerResponse` carrying the original instruction.
     * [emittedBlindAt] dampens cycle re-emission (matches the
     * `emittedAt` pattern on [Querying] / [Reissued] — not the original
     * `declaredAt == now` equality, which is fragile to sub-cycle clocks).
     *
     * Pass 12 (D-AUDIT.2.B): cleanup. The entry IS pruned when the
     * aircraft has fully left the controller's world (not in
     * `view.responsibilities`, not in `view.aircraft`). Other states
     * (Issued/Querying/Reissued) persist through tracked-loss because
     * a late readback could still resolve them; LostCommsDeclared is
     * terminal post-mortem and only departure clears it.
     *
     * `declaredAt` is the original transition timestamp; the parent
     * [OutstandingCoordination] carries `issuedAt` (do not duplicate).
     */
    data class LostCommsDeclared(
        val declaredAt: SimTime,
        val emittedBlindAt: SimTime?,
    ) : CoordinationState
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
