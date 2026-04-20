package xyz.easiersaid.twr.controller.observe

import xyz.easiersaid.twr.controller.bdi.Stage
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.AtomicReadback
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime

/**
 * An instruction-readback coordination tracked by the controller.
 *
 * Replaces [PendingReadback] with a richer lifecycle:
 *   ISSUED → (correct readback) → CONFIRMED → stage advances
 *   ISSUED → (wrong readback) → CORRECTING
 *   ISSUED → (timeout) → QUERYING → re-issue
 *   ISSUED → (superseded) → CANCELLED
 *
 * Every ATC instruction is the START of a coordination. The controller's
 * commitment stage advances only when the coordination reaches CONFIRMED,
 * not when the instruction is emitted. This ensures the controller's mental
 * model reflects what was actually communicated, not what was intended.
 */
data class OutstandingCoordination(
    val aircraft: AircraftId,
    val instruction: AtcInstruction,
    val expectedReadback: Set<AtomicReadback>,
    val issuedAt: SimTime,
    val state: CoordinationState = CoordinationState.ISSUED,
    /** The stage to advance to when this coordination is CONFIRMED. Null = no advancement. */
    val advanceToStage: Stage? = null,
)

enum class CoordinationState {
    /** Instruction transmitted, awaiting readback. */
    ISSUED,
    /** Correct readback received — coordination complete. */
    CONFIRMED,
    /** No readback within timeout — controller has queried "did you copy?" */
    QUERYING,
    /** Superseded by a later instruction — no longer tracked. */
    CANCELLED,
}

/**
 * How a rule's stage transition should be applied.
 *
 * Declared on [xyz.easiersaid.twr.controller.bdi.AtcRule] to distinguish
 * stage-only rules (immediate) from instruction-bearing rules (confirmation-gated).
 */
sealed interface AdvancementPolicy {
    /** Stage advances immediately when the rule fires (no instruction / no readback needed). */
    data object Immediate : AdvancementPolicy

    /**
     * Stage advances only when the pilot's readback is confirmed.
     * The rule creates an [OutstandingCoordination]; the readback validator
     * resolves it; the pipeline then advances the stage.
     */
    data object OnReadbackConfirmed : AdvancementPolicy
}

/** First timeout: query the pilot. */
val COORDINATION_QUERY_TIMEOUT: SimDuration = SimDuration.ofSeconds(10)

/** Second timeout: re-issue the instruction. */
val COORDINATION_REISSUE_TIMEOUT: SimDuration = SimDuration.ofSeconds(20)
