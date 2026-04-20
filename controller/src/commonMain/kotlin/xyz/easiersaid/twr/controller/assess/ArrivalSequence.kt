package xyz.easiersaid.twr.controller.assess

import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime

/**
 * First-class representation of the arrival sequence for one runway.
 *
 * The runway-duty queue ([RunwayDutyState]) becomes a *projection* of this state:
 * arrivals no longer self-enqueue via `enqueuePhase`; instead they're projected from
 * here. Departures/crossings/backtracks still enqueue through the old path.
 *
 * See design doc §2 (2026-04-19-approach-sequencing.md).
 */
data class ArrivalSequence(
    val runway: RunwayId,
    val slots: List<ArrivalSlot>,
    /**
     * Aircraft whose stable number changed this cycle. Drives re-emission
     * of NumberInSequence. Populated by [updateArrivalSequence], consumed
     * by companion output derivation.
     */
    val resequencedAircraft: Set<AircraftId> = emptySet(),
) {
    companion object {
        fun empty(runway: RunwayId) = ArrivalSequence(runway, emptyList())
    }
}

data class ArrivalSlot(
    val aircraft: AircraftId,
    /**
     * The sequence number communicated to the pilot. Stable across cycles —
     * only changes on explicit re-sequence (go-around, insertion, unable).
     * See §2.2 for re-sequence triggers.
     */
    val stableNumber: Int,
    val followTarget: FollowTarget?,
    val distanceToThresholdM: Double?,
    val spacingAheadSeconds: Double?,
    val gate: ArrivalGate,
    val approachMode: ApproachMode,
)

// ── Gate taxonomy ────────────────────────────────────────────────────

/**
 * Where the aircraft is in the approach/circuit sequence.
 *
 * Gates are operationally load-bearing — inside-FAF matters because
 * go-around authority semantics shift and landing clearance must be
 * issued by then (Doc 4444 §7.9 / §7.10). Visual-vs-ILS is a *mode*
 * ([ApproachMode]), not a gate — separate dimension.
 */
sealed interface ArrivalGate {
    /** Pre-sequence: known to APP, not yet in pattern. */
    data object Inbound : ArrivalGate

    /** Circuit-based gates. */
    data class Downwind(val phase: DownwindPhase) : ArrivalGate
    data class BaseTurn(val phase: BaseTurnPhase) : ArrivalGate

    /** Final approach gates. */
    data class Final(val phase: FinalPhase) : ArrivalGate

    /** Localiser established — orthogonal to distance. */
    data object LocaliserEstablished : ArrivalGate
}

enum class DownwindPhase { ABEAM, LATE }
enum class BaseTurnPhase { INITIATED, ROLLING_OUT }
enum class FinalPhase { INTERCEPT, FOUR_NM, FAF, INSIDE_FAF }

/** Approach type is a mode, not a gate — separate dimension. LOC is non-precision (different separation minima from ILS). */
enum class ApproachMode { VISUAL, ILS, LOC, RNAV }

// ── Follow target and acquisition lifecycle ──────────────────────────

/**
 * The aircraft this slot is following, with visual acquisition state.
 *
 * "Number 3, follow the Cessna on base" is a sequence instruction — it does NOT
 * by itself delegate separation. The lifecycle is:
 *
 *   NOT_ISSUED → ISSUED → TRAFFIC_IN_SIGHT → VISUAL_SEPARATION_APPLIED → LOST
 *
 * TRAFFIC_IN_SIGHT → VISUAL_SEPARATION_APPLIED is a *controller decision*
 * (Doc 4444 §5.11), not an automatic consequence of the pilot report. If geometry
 * is unsuitable the controller retains prescribed separation despite the pilot
 * having traffic in sight.
 */
data class FollowTarget(
    val aircraft: AircraftId,
    val acquisitionState: AcquisitionState,
)

enum class AcquisitionState {
    /** Number assigned but "following" not yet issued. */
    NOT_ISSUED,
    /** "Follow the Cessna on base" issued. */
    ISSUED,
    /** Pilot reports "traffic in sight" — controller still owns separation. */
    TRAFFIC_IN_SIGHT,
    /** Controller judged geometry suitable and accepted reduced standard (Doc 4444 §5.11). */
    VISUAL_SEPARATION_APPLIED,
    /** Pilot reports "traffic lost" or belief decayed — re-assume separation. */
    LOST,
    /** Pilot reports unable to follow (unable to maintain visual). Controller must re-sequence. */
    UNABLE,
}
