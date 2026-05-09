package xyz.easiersaid.twr.controller.bdi

import xyz.easiersaid.twr.protocol.AircraftId
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
) {
    val isComplete: Boolean get() = stage.isComplete
}
