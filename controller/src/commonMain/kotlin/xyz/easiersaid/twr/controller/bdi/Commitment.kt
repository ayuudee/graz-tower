package xyz.easiersaid.twr.controller.bdi

import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime

enum class TrafficType { DEPARTURE, ARRIVAL, TRANSIT, TAXI }

/**
 * Classification of the obligation this commitment fulfils.
 *
 * Tag on [Commitment], not a type split — the four kinds share nearly all state
 * (aircraft, stage, formedAt, runway). Split into subtypes when a field is
 * meaningful for exactly one kind (likely `spacingTarget` for SEQUENCING in Phase 6).
 */
enum class ObligationType {
    /** Prescribed separation minima, wake, runway buffer. */
    SEPARATION,
    /** Number-in-sequence, follow-target, spacing. */
    SEQUENCING,
    /** Essential traffic, aerodrome traffic information. */
    TRAFFIC_INFO,
    /** Approach, landing, takeoff, taxi clearance. */
    CLEARANCE,
    /** Report position, call again, contact frequency. */
    PROCEDURAL,
}

data class CommitmentKind(
    val role: RoleName,
    val trafficType: TrafficType,
) {
    companion object {
        val TOWER_DEPARTURE = CommitmentKind(RoleName.TOWER, TrafficType.DEPARTURE)
        val TOWER_ARRIVAL = CommitmentKind(RoleName.TOWER, TrafficType.ARRIVAL)
        val GROUND_TAXI = CommitmentKind(RoleName.GROUND, TrafficType.TAXI)
        val APPROACH_ARRIVAL = CommitmentKind(RoleName.APPROACH, TrafficType.ARRIVAL)
        val APPROACH_TRANSIT = CommitmentKind(RoleName.APPROACH, TrafficType.TRANSIT)
        val AREA_TRANSIT = CommitmentKind(RoleName.AREA_CONTROL, TrafficType.TRANSIT)
    }
}

/**
 * Typed stage marker for a [ProcedureSpec]'s state machine.
 *
 * Each commitment kind defines its own sealed hierarchy of stages
 * (see [TowerDepartureStage], [TowerArrivalStage], etc.). [name] is used
 * only for human-readable traces; equality and pattern matching are
 * done against the object identity, not the string.
 */
sealed interface Stage {
    val name: String
    val isComplete: Boolean get() = false
}

/**
 * A controller's staged plan for one aircraft.
 *
 * Tracks progress through a procedure's stages. The procedure spec
 * defines which rules apply at each stage; the commitment tracks
 * which stage this aircraft is at.
 */
data class Commitment(
    val aircraft: AircraftId,
    val kind: CommitmentKind,
    val stage: Stage,
    val runway: RunwayId? = null,
    val formedAt: SimTime,
    val contacted: Boolean = false,
    val obligationType: ObligationType = ObligationType.CLEARANCE,
    /** How the current stage was reached by observation reconciliation.
     *  ANOMALOUS means the aircraft is somewhere the controller didn't expect
     *  (e.g. runway incursion). Reset to null after one cycle so it's a
     *  single-cycle flag, not persistent state. */
    val lastTransition: xyz.easiersaid.twr.controller.procedure.TransitionKind? = null,
    /**
     * fn-8.3 Phase 2 (B2): sticky witness that the controller has observed
     * this aircraft on a runway entity AND on the ground at least once
     * during the **current** commitment lifetime.
     *
     * Set by [reconcileObservedStages] when a `TOWER_ARRIVAL` aircraft's
     * observation has both `RunwayRef` membership and `onGround = true`.
     * Resets to `false` when a fresh commitment is formed in
     * [reconcileCommitments] (because the field is **not** copied across
     * commitment re-creation — fresh commitments take the default).
     *
     * Used by [TouchedDownDuringCommitment] to gate `ARR-TNG-AIRBORNE`:
     * the touch-and-go arrival completes only after the aircraft has
     * actually been observed on the runway-on-ground (real touchdown),
     * not merely from an airborne observation. Pre-fix, `ARR-TNG-AIRBORNE`
     * fired on bare `Airborne`, allowing a re-issued landing-clearance
     * coordination to advance to `AwaitLandedObserved` (via readback) and
     * immediately complete the arrival, reforming a fresh one — the
     * runaway commitment ping-pong documented in the fn-8.3 dive evidence.
     *
     * Analogous to `RunwayDutyState.holderReachedRunway` (already in the
     * runway-duty machine) — same observation, different lifecycle.
     */
    val touchedDownDuringCommitment: Boolean = false,
    /**
     * fn-8.3 Phase 2 (B3): sticky witness that the pilot has reported
     * "Ready for departure" at least once during the **current**
     * commitment lifetime. Set by [reconcileObservedStages] when the
     * controller-side `ControllerEvent.ReadyForDepartureReceived` event
     * fires for this aircraft.
     *
     * The pre-fix `DEP-LUAW` rule gated on `PilotReady` (single-cycle
     * event), which works for the single-aircraft case where the runway
     * is granted in the same cycle as the Ready report. For sequential
     * departures behind an arriving circuit, the runway is granted to
     * the second departure long after the pilot's one-shot Ready report
     * is gone from `ctx.events` — so `DEP-LUAW` never fires and the
     * second aircraft wedges at `AwaitReady` indefinitely. This was
     * surfaced by fn-8.3 Phase 2 once the runaway commitment loop
     * (B2 fix) was collapsed, freeing the runway for B's slot.
     *
     * Real controllers retain "ready to go" on the strip — the pilot
     * doesn't repeat "Ready" every cycle. This sticky witness models
     * that strip-state.
     */
    val pilotReadyDuringCommitment: Boolean = false,
    /**
     * fn-8.3 Phase 4 (B5-α): sticky witness recording every
     * [ReportEvent] the controller has observed during the **current**
     * commitment lifetime. Set by [reconcileObservedStages] for
     * `TOWER_ARRIVAL` from the `ControllerEvent.PositionReported` events
     * fired this cycle — accumulated as a union across cycles.
     *
     * Default empty on commitment formation (fresh `Commitment(...)`
     * instance via `createCommitment`); reset to empty on stage
     * regression (e.g. go-around backtracks `LandingClearanceIssued ->
     * AwaitDownwind` per `GA-POST-CLEAR`) — see the regression-detection
     * arm in [advanceCommittedStages]. The sticky-witness pattern
     * mirrors B2's [touchedDownDuringCommitment] and B3's
     * [pilotReadyDuringCommitment].
     *
     * Used by [HasReportedCircuitPosition] to gate `ARR-LAND` /
     * `ARR-LAND-TNG` (and their re-issue siblings) on doctrinal
     * pre-clearance pilot reports per CAP 413 §4.45-4.49 / ICAO Doc
     * 4444 §7.10. Pre-fix, those rules fired purely on observed
     * geometry + strip-derived `IsCircuitTrafficByStrip` (C2/C3),
     * so a stepped-on Downwind did not block landing-clearance
     * issuance — the controller cleared the aircraft to land before
     * its position call had been delivered (G1 B5 mechanism M1, fn-8.3
     * spec § Phase 3 round 2 evidence).
     *
     * **Commitment-scoped, not BeliefState-scoped.** A flat
     * `Map<AircraftId, Set<ReportEvent>>` on `BeliefState` would let
     * a first-circuit Downwind report unlock the second-circuit
     * landing clearance — recreating the stale-belief class that
     * Phase 2's `circuitIntent`-staleness work already surfaced. The
     * field lives on [Commitment] so a fresh commitment (next circuit
     * after T&G completion) gets the default-empty value structurally.
     */
    val observedReportsDuringCommitment: Set<ReportEvent> = emptySet(),
    /**
     * fn-12 (R7-no-refire): approach-attempt-scoped witness that the
     * controller has already issued an obstruction-driven `GoAround` for
     * this aircraft on the **current** approach attempt. Read by the
     * [xyz.easiersaid.twr.controller.bdi.ObstructionGoAroundAlreadyIssuedThisAttempt]
     * guard (negated in the `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule's
     * guard) to prevent re-firing while the obstruction persists.
     *
     * **Set timing — committed-output path only**. Set in
     * `advanceCommittedStages` (Controller.kt) after arbitration and
     * certification have accepted the rule's candidate output. NOT set at
     * candidate-emit time in `executeProcedure` — if the candidate loses
     * arbitration or fails certification, the witness MUST NOT be set;
     * otherwise the controller would suppress the legitimate obstruction
     * GA on the next cycle.
     *
     * **Re-arm sites** (clears back to `false`):
     *  - Next `Report(Downwind)` arrival from this aircraft on this
     *    commitment, in `reconcileTowerArrival`.
     *  - Commitment replacement (a fresh `Commitment(...)` via
     *    `createCommitment` takes the default `false`).
     *
     * Stage-progression alone is INSUFFICIENT for re-fire prevention —
     * reconciliation may re-advance the aircraft back through eligible
     * stages while the obstruction persists. The witness is the actual
     * suppression mechanism. See fn-12 task spec § R7-no-refire and
     * `feedback_no_corners.md`.
     */
    val obstructionGoAroundIssuedThisAttempt: Boolean = false,
) {
    val isComplete: Boolean get() = stage.isComplete
}
