package xyz.easiersaid.twr.controller.bdi

import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.Disregard
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.IncreaseSpeedTo
import xyz.easiersaid.twr.protocol.MaintainSpeed
import xyz.easiersaid.twr.protocol.Orbit
import xyz.easiersaid.twr.protocol.ReduceSpeedTo
import xyz.easiersaid.twr.protocol.TurnBase
import kotlin.reflect.KClass

/**
 * Instruction supersession: when a new instruction is committed, any active instruction
 * it supersedes is cleaned up. Acts on two stores:
 *
 *   1. [BeliefState.issuedClearances] — superseded clearance terminates.
 *   2. [BeliefState.coordinations] — superseded entries disposed per [PendingReadbackPolicy]
 *      (Pass 9: now reads the coordination ledger directly; the `pendingReadbacks`
 *      projection was removed in favour of typed predicates on `CoordinationState`).
 *
 * Supersession is transparent to rule selection — guards continue to use [NoActiveInstruction]
 * and [NoPendingReadback]. The cleanup fires *after* arbitration commits the superseding
 * instruction, not during guard evaluation.
 *
 * See design doc §3.0 (2026-04-19-approach-sequencing.md).
 */
data class SupersessionRelation(
    val superseding: KClass<out AtcInstruction>,
    val superseded: KClass<out AtcInstruction>,
    val pendingReadbackPolicy: PendingReadbackPolicy,
)

enum class PendingReadbackPolicy {
    /** Superseded instruction's pending readback is GC'd. E.g. TurnBase supersedes ExtendDownwind. */
    ABANDON,
    /**
     * Old pending readback retained (not GC'd) — the new instruction adds its own pending
     * alongside. Effectively both require readback per ICAO Doc 4444 §12.3.1.2.
     * Named ABSORB because the new instruction conceptually replaces the old, but
     * both readback obligations remain until individually satisfied or GC'd.
     */
    ABSORB,
}

/**
 * Registry of supersession relations.
 *
 * Extend as new compounding sequencing instructions land in 5c.
 */
val SUPERSESSION_RELATIONS: List<SupersessionRelation> = listOf(
    // Circuit sequencing: later instructions supersede earlier delays
    SupersessionRelation(TurnBase::class, ExtendDownwind::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(Orbit::class, ExtendDownwind::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(TurnBase::class, Orbit::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(ClearedToLand::class, TurnBase::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(ClearedToLand::class, Orbit::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(ClearedToLand::class, ExtendDownwind::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(GoAround::class, ClearedToLand::class, PendingReadbackPolicy.ABANDON),

    // Speed: new speed control absorbs (replaces) the old — readback obligation transfers
    SupersessionRelation(ReduceSpeedTo::class, MaintainSpeed::class, PendingReadbackPolicy.ABSORB),
    SupersessionRelation(MaintainSpeed::class, ReduceSpeedTo::class, PendingReadbackPolicy.ABSORB),
    SupersessionRelation(IncreaseSpeedTo::class, MaintainSpeed::class, PendingReadbackPolicy.ABSORB),
    SupersessionRelation(IncreaseSpeedTo::class, ReduceSpeedTo::class, PendingReadbackPolicy.ABSORB),

    // Pass 12 (D-PF.9 follow-on F.2): a re-issued ContactFrequency from
    // missedHandoffReissueOutputs supersedes the original ContactFrequency
    // coordination. Without this relation, two ContactFrequency coordinations
    // accumulate on the controller's belief slice (the original escalating,
    // the re-issue fresh) — the controller would emit both
    // TransmittingBlind (from the original's LostCommsDeclared lifecycle)
    // AND a fresh Instruct from the re-issue, double-bothering the pilot.
    // ABANDON: the original is operationally dead once we re-issue.
    SupersessionRelation(
        xyz.easiersaid.twr.protocol.ContactFrequency::class,
        xyz.easiersaid.twr.protocol.ContactFrequency::class,
        PendingReadbackPolicy.ABANDON,
    ),

    // Note: Disregard is handled as a universal superseder in applySupersessionCleanup,
    // not via explicit relations. See the Disregard special case in the cleanup function.
)

/** Pre-grouped index for [applySupersessionCleanup]. Avoids re-grouping every cycle. */
private val RELATIONS_BY_SUPERSEDING = SUPERSESSION_RELATIONS.groupBy { it.superseding }

/**
 * Apply supersession cleanup to pending readbacks after a set of instructions were committed.
 *
 * For each committed instruction, if it supersedes any instruction type that has a pending
 * readback for the same aircraft, handle per policy:
 *   - ABANDON: remove the superseded pending entry.
 *   - ABSORB: keep (the new instruction inherits the obligation).
 */
fun applySupersessionCleanup(
    beliefs: BeliefState,
    committedInstructions: List<Pair<AircraftId, AtcInstruction>>,
    relationsIndex: Map<kotlin.reflect.KClass<out AtcInstruction>, List<SupersessionRelation>> = RELATIONS_BY_SUPERSEDING,
): BeliefState {
    if (committedInstructions.isEmpty()) return beliefs

    val coordinations = committedInstructions.fold(beliefs.coordinations) { acc, (aircraft, instruction) ->
        // Disregard is a universal superseder: cancel ALL coordinations for this aircraft.
        if (instruction is Disregard) return@fold acc - aircraft

        val relations = relationsIndex[instruction::class] ?: return@fold acc
        val coords = acc[aircraft] ?: return@fold acc
        val abandonTypes = relations
            .filter { it.pendingReadbackPolicy == PendingReadbackPolicy.ABANDON }
            .map { it.superseded }
            .toSet()
        if (abandonTypes.isEmpty()) return@fold acc
        val cleaned = coords.filterNot { coord ->
            abandonTypes.any { type -> type.isInstance(coord.instruction) }
        }
        if (cleaned.isEmpty()) acc - aircraft else acc + (aircraft to cleaned)
    }
    return if (coordinations === beliefs.coordinations) beliefs
        else beliefs.copy(coordinations = coordinations)
}
