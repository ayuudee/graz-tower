package xyz.easiersaid.twr.controller.observe

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.ClearanceSummary
import xyz.easiersaid.twr.controller.RunwayObservation
import xyz.easiersaid.twr.controller.assess.ArrivalSequence
import xyz.easiersaid.twr.controller.assess.RunwayDutyState
import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime

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
     * Outstanding instruction-readback coordinations, keyed by aircraft.
     *
     * Replaces the former `pendingReadbacks` with a richer lifecycle:
     * ISSUED → removed on correct readback (stage advances via acceptReadback).
     * Pass 9 (D-AUDIT.2): no longer silently GC'd. The lifecycle escalates
     * Issued → Querying → Reissued → LostCommsDeclared via
     * [escalateOverdueCoordinations] when no readback arrives.
     *
     * `pendingReadbacks` is now a projection: filter for state == ISSUED.
     */
    val coordinations: Map<AircraftId, List<OutstandingCoordination>> = emptyMap(),
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
    /**
     * Recent radio events per aircraft, time-windowed (entries older than
     * [RECENT_RADIO_WINDOW] are evicted). Pass 5 (D-AUDIT.14 closure)
     * replaces the cached `aircraftIntent` slice — `determineServiceKind`
     * now reads this slice + the strip directly and derives intent on
     * demand via [deriveCurrentIntent], rather than consulting a frozen
     * classification.
     *
     * Single write site: `withRecentRadio` in `Observe.kt`. The
     * architectural test `FirewallBeliefWriteTest` enforces this.
     *
     * Time-windowed (not count-windowed) per Pass 5 review: real ATC
     * controllers remember "the last few minutes" of radio traffic, not
     * "the last N transmissions." A count window silently regresses under
     * multi-aircraft load.
     */
    val recentRadio: Map<AircraftId, RecentRadio> = emptyMap(),
    /**
     * Per-aircraft circuit-end intent (touch-and-go vs full-stop). Populated
     * exclusively from radio: when the pilot transmits a Downwind report
     * carrying a non-null `CircuitIntent`, the belief-update fold emits
     * `CircuitIntentReported` and writes the intent here. Cleared on
     * `GoAroundDetected` per ICAO 4444 §7.10.2 — pilot must re-declare on the
     * rejoined circuit.
     *
     * Read by the `CircuitIntentIs(FULL_STOP)` and `IsCircuitTraffic` guards.
     * The architectural test enforces the same single-write-site rule.
     *
     * **Default semantics on absence:** `CircuitIntentIs(FULL_STOP)` returns
     * `false` for absent entries — operational default per ICAO/SERA is
     * touch-and-go for circuit traffic that hasn't declared.
     */
    val circuitIntent: Map<AircraftId, CircuitIntent> = emptyMap(),
    /**
     * Pass 12 (D-PF.9): per-aircraft last-time we re-issued
     * `ContactFrequency` after a missed-handoff notice. Cycle-level
     * dampening: a notice with `since == handoffReissuedAt[ac]` doesn't
     * re-emit (we already responded to *this* escalation); a notice with
     * `since > handoffReissuedAt[ac]` (a new escalation window) does.
     *
     * **Single-write site**: `missedHandoffReissueOutputs` in
     * `Controller.kt`'s outputs path. `FirewallBeliefWriteTest`
     * extends to enforce.
     */
    val handoffReissuedAt: Map<AircraftId, SimTime> = emptyMap(),
) {
    companion object {
        val EMPTY = BeliefState()
        const val MAX_OBSERVATION_HISTORY = 5
        /** Cooldown before concern severity can drop, in milliseconds. */
        const val CONCERN_COOLDOWN_MS = 15_000L
        /**
         * Time window for the [recentRadio] slice. 5 sim-minutes — bounded
         * by ATC's working-memory horizon. Real controllers remember recent
         * traffic for minutes, not transmission counts.
         */
        val RECENT_RADIO_WINDOW: xyz.easiersaid.twr.protocol.SimDuration =
            xyz.easiersaid.twr.protocol.SimDuration.ofMillis(5 * 60 * 1000L)
    }
}

/** Last committed concern level for a follower aircraft, with timestamp. */
data class RecentConcern(
    val concern: SeparationConcern,
    val since: SimTime,
)

// AircraftIntent moved to its own file — see AircraftIntent.kt

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
