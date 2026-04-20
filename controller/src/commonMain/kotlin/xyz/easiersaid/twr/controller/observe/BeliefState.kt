package xyz.easiersaid.twr.controller.observe

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.ClearanceSummary
import xyz.easiersaid.twr.controller.RunwayObservation
import xyz.easiersaid.twr.controller.assess.ArrivalSequence
import xyz.easiersaid.twr.controller.assess.RunwayDutyState
import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.protocol.*

/**
 * Persistent controller belief state, carried forward between decision cycles.
 * Opaque to the simulation — only the controller reads and writes this.
 */
data class BeliefState(
    val trackedAircraft: Map<AircraftId, AircraftObservation> = emptyMap(),
    val runwayBeliefs: Map<RunwayId, RunwayObservation> = emptyMap(),
    val issuedClearances: Map<ClearanceId, ClearanceSummary> = emptyMap(),
    val commitments: Map<AircraftId, Commitment> = emptyMap(),
    val activeRunway: RunwayId? = null,
    val runwayDuty: RunwayDutyState? = null,
    /**
     * Arrival sequence for the active runway. Source of truth for arrival ordering;
     * [runwayDuty] queue is a *projection* of this for arrivals. Updated between
     * reconcileCommitments and updateRunwayDuty in the pipeline.
     */
    val arrivalSequence: ArrivalSequence? = null,
    /**
     * Instructions awaiting readback, keyed by aircraft. Most-recent last.
     * Populated after arbitration from outgoing [xyz.easiersaid.twr.controller.ControllerOutput.Instruct],
     * consumed by the readback validator, GC'd by age.
     */
    val pendingReadbacks: Map<AircraftId, List<PendingReadback>> = emptyMap(),
    /**
     * Reports the controller is expecting from pilots ("call base", "report final",
     * "report 4 miles"). Keyed by (aircraft, expected event) — consumed by the
     * reconciliation phase when the matching pilot report arrives. GC'd by age
     * when stale (belief decay).
     */
    val outstandingReports: Map<AircraftId, List<OutstandingReport>> = emptyMap(),
    /**
     * When each aircraft was last directly observed (present in the ControllerView).
     * Retained aircraft (in responsibilities but not in the view) keep their last
     * observation timestamp. Consumers check staleness against a configurable TTL
     * to trigger degraded-mode paths (e.g. unknown speed for ETA derivation).
     */
    val aircraftLastObserved: Map<AircraftId, SimTime> = emptyMap(),
    /** Aircraft that have reported established on the localiser. Cleared when aircraft exits sequence. */
    val establishedLocaliser: Set<AircraftId> = emptySet(),
    /**
     * Recent observation history per aircraft (bounded ring buffer, last [MAX_OBSERVATION_HISTORY] entries).
     * Provides successive positions for closure-rate and vertical-rate derivation (Phase 6d).
     */
    val previousPositions: Map<AircraftId, List<ObservationSnapshot>> = emptyMap(),
    /**
     * Pair-wise separation assessments computed early in the pipeline (Phase 6b Phase A).
     * Keyed by the pair (ordered: first.value < second.value to avoid duplicates).
     * Consumed by procedure guards and the Phase B reactive safety net.
     */
    val separationAssessments: List<SeparationAssessment> = emptyList(),
    /**
     * Hysteresis for separation concern: last committed concern per follower aircraft.
     * Concern can only drop severity after [CONCERN_COOLDOWN_MS] has elapsed.
     * Prevents oscillation at threshold boundaries (D2 root cause fix).
     */
    val recentConcerns: Map<AircraftId, RecentConcern> = emptyMap(),
) {
    companion object {
        val EMPTY = BeliefState()
        const val MAX_OBSERVATION_HISTORY = 5
        /** Cooldown before concern severity can drop, in milliseconds. */
        const val CONCERN_COOLDOWN_MS = 15_000L
    }
}

/** Last committed concern level for a follower aircraft, with timestamp. */
data class RecentConcern(
    val concern: SeparationConcern,
    val since: SimTime,
)

/**
 * A report the controller has requested and is awaiting from a pilot.
 *
 * Created when the controller issues a [xyz.easiersaid.twr.protocol.ReportWhen] instruction.
 * Consumed when the matching [xyz.easiersaid.twr.protocol.ReportEvent] arrives in
 * [xyz.easiersaid.twr.controller.ControllerView.receivedMessages].
 */
data class OutstandingReport(
    val aircraft: AircraftId,
    val expected: ReportEvent,
    val issuedAt: SimTime,
)

/** A snapshot of an aircraft's state at a point in time, for observation history. */
data class ObservationSnapshot(
    val time: SimTime,
    val position: PointId,
    val altitude: Level?,
    val groundSpeed: Knots?,
)

/**
 * Pair-wise separation assessment between two aircraft.
 * Computed by the separation engine (Phase 6b Phase A) and written to beliefs.
 */
data class SeparationAssessment(
    val aircraft: AircraftId,
    val other: AircraftId,
    val currentSeparationNm: Double?,
    val requiredSeparationNm: Double,
    val closureRateKt: Double?,
    val timeToMinimumSeconds: Double?,
    val concern: SeparationConcern,
)

/**
 * How concerned the controller is about separation for a specific pair.
 *
 * [Severity] forms an ordered scale (COMFORTABLE < MONITORING < INTERVENTION < VIOLATION).
 * [Delegated] is structurally separate — visual separation applied, orthogonal to severity.
 * The sealed interface makes it impossible to accidentally compare Delegated against Severity.
 */
sealed interface SeparationConcern {
    /** Ordered severity scale. Use [level] for comparison, not ordinal tricks. */
    enum class Severity(val level: Int) : SeparationConcern {
        /** Above minimum + comfortable margin. No action. */
        COMFORTABLE(0),
        /** Above minimum but closure trend is uncomfortable. Increased attention. */
        MONITORING(1),
        /** Below comfort threshold. Proactive correction needed (speed/extend/orbit). */
        INTERVENTION(2),
        /** At or below minimum. Immediate action required (go-around). */
        VIOLATION(3),
    }

    /** Visual separation applied. Controller monitors but cannot issue speed control. */
    data object Delegated : SeparationConcern
}

/** True if this concern is a [SeparationConcern.Severity] at or above [threshold]. Delegated returns false. */
fun SeparationConcern.isSeverityAtLeast(threshold: SeparationConcern.Severity): Boolean =
    this is SeparationConcern.Severity && this.level >= threshold.level
