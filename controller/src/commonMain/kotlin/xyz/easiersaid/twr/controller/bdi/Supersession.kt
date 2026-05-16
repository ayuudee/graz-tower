package xyz.easiersaid.twr.controller.bdi

import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ContinueApproach
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
    // Circuit sequencing: later instructions supersede earlier delays.
    //
    // fn-28.4 (R23) audit: this row is the cancel-output for the new
    // `ARR-EXTEND-FOR-GA` rule's prior ExtendDownwind coordination.
    // When the GA belief clears (Observe.withGoAroundInProgress fold —
    // pattern-rejoin transmission `receivedAt > setAtTime`, OR 60s
    // timeout), ARR-TURN-BASE's guard `Not(GoAroundInProgressOnRunway)`
    // passes again and TURN BASE fires; this relation drops the
    // pending ExtendDownwind on the same cycle. NO new supersession
    // row needed for fn-28.4 — the existing row covers cancel-via-
    // supersession per R23 round-10 Major 2.
    SupersessionRelation(TurnBase::class, ExtendDownwind::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(Orbit::class, ExtendDownwind::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(TurnBase::class, Orbit::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(ClearedToLand::class, TurnBase::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(ClearedToLand::class, Orbit::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(ClearedToLand::class, ExtendDownwind::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(GoAround::class, ClearedToLand::class, PendingReadbackPolicy.ABANDON),
    // fn-12 (R7-supersession): a GA also supersedes a ClearedTouchAndGo
    // — symmetric to the ClearedToLand case. The obstruction-driven GA
    // can fire from `LandingClearanceIssued` after a `ClearedTouchAndGo`
    // (declared T&G intent path), and a stale T&G readback arriving
    // post-regression must NOT advance the commitment back out of
    // `AwaitDownwind`.
    SupersessionRelation(GoAround::class, ClearedTouchAndGo::class, PendingReadbackPolicy.ABANDON),

    // fn-13.1 (R5b — supersession extension): escalation path — when an
    // obstruction-driven CONTINUE APPROACH is in flight and the
    // `ObstructionClearsInTime` predicate flips false on a subsequent
    // cycle (obstruction slipped, ETA shrunk, groundSpeed dropped),
    // `obstructionGoAroundRuleAwaitApproach` fires the GA. The stale
    // ContinueApproach coordination MUST be cleaned up so it does not
    // (a) appear in the coordination ledger after the regression to
    // AwaitDownwind, or (b) gate downstream rules via `NoPendingReadback`.
    SupersessionRelation(GoAround::class, ContinueApproach::class, PendingReadbackPolicy.ABANDON),

    // fn-13.1 (R5b — supersession extension): normal-success path — when
    // the obstruction clears (predicate stops being relevant), `ARR-LAND`
    // / `ARR-LAND-TNG` fire the landing clearance. The stale
    // ContinueApproach coordination MUST be cleaned up; without this the
    // ledger retains a non-terminal CONTINUE APPROACH coordination across
    // the landing, and `NoPendingReadback(instructionOfType<ContinueApproach>())`
    // on the existing `ARR-CONTINUE` rule would otherwise gate-block
    // future rule firings unsafely.
    SupersessionRelation(ClearedToLand::class, ContinueApproach::class, PendingReadbackPolicy.ABANDON),
    SupersessionRelation(ClearedTouchAndGo::class, ContinueApproach::class, PendingReadbackPolicy.ABANDON),

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

    // fn-12 (R7-supersession): the issued-clearances ledger MUST also drop
    // superseded clearances. Without this, an active `ClearedToLand` /
    // `ClearedTouchAndGo` clearance survives in `BeliefState.issuedClearances`
    // even after an obstruction GA — `NoActiveInstruction(...)`-gated
    // downstream rules would silently sleep against a dead disposition.
    // The cleanup walks the same supersession-relation table as the
    // coordinations cleanup above; for each committed superseding
    // instruction we drop matching active clearances on the same aircraft.
    val issued = committedInstructions.fold(beliefs.issuedClearances) { acc, (aircraft, instruction) ->
        if (instruction is Disregard) {
            return@fold acc.filterValues { clr -> clr.aircraft != aircraft }
        }
        val relations = relationsIndex[instruction::class] ?: return@fold acc
        val abandonTypes = relations
            .filter { it.pendingReadbackPolicy == PendingReadbackPolicy.ABANDON }
            .map { it.superseded }
            .toSet()
        if (abandonTypes.isEmpty()) return@fold acc
        acc.filterValues { clr ->
            // Keep clearances for OTHER aircraft, or that don't match a
            // superseded type. Clearances of the matching type for this
            // aircraft are dropped regardless of clearance domain — the
            // supersession-relation table is the authority.
            clr.aircraft != aircraft ||
                abandonTypes.none { type -> type.isInstance(clr.instruction) }
        }
    }

    val coordsChanged = coordinations !== beliefs.coordinations
    val issuedChanged = issued !== beliefs.issuedClearances
    return when {
        coordsChanged && issuedChanged -> beliefs.copy(coordinations = coordinations, issuedClearances = issued)
        coordsChanged -> beliefs.copy(coordinations = coordinations)
        issuedChanged -> beliefs.copy(issuedClearances = issued)
        else -> beliefs
    }
}

