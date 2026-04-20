package xyz.easiersaid.twr.controller.bdi

import xyz.easiersaid.twr.protocol.*

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
) {
    val isComplete: Boolean get() = stage.isComplete
}
