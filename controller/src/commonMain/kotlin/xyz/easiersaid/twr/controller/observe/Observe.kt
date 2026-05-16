package xyz.easiersaid.twr.controller.observe

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.core.world.RunwayObstruction
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RequestApproach
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.RequestRightBase
import xyz.easiersaid.twr.protocol.RequestShortApproach
import xyz.easiersaid.twr.protocol.RequestStartup
import xyz.easiersaid.twr.protocol.RequestTaxi
import xyz.easiersaid.twr.protocol.RequestType
import xyz.easiersaid.twr.protocol.RequestVisualApproach

/** Merge view into persistent beliefs. Perfect-sensor model: observation replaces belief. */
fun updateBeliefs(current: BeliefState, view: ControllerView): BeliefState {
    // Start with all observed aircraft (perfect sensor — observation is truth)
    // Then retain any unobserved aircraft we're still responsible for
    val observed = view.aircraft
    val tracked = observed + current.trackedAircraft.filterKeys { it in view.responsibilities && it !in observed }

    // Update last-observed timestamps: fresh for observed aircraft, retained for unobserved.
    val lastObserved = current.aircraftLastObserved
        .filterKeys { it in tracked }
        .plus(observed.keys.associateWith { view.time })

    // Prune establishedLocaliser for aircraft no longer tracked.
    val locEstablished = current.establishedLocaliser.filterTo(mutableSetOf()) { it in tracked }

    // Append current observations to history buffer (bounded ring, last MAX_OBSERVATION_HISTORY).
    val history = buildObservationHistory(current.previousPositions, observed, view.time, tracked)

    // Prune per-aircraft maps for aircraft no longer tracked.
    val prunedConcerns = current.recentConcerns.filterKeys { it in tracked }
    val prunedReports = current.outstandingReports.filterKeys { it in tracked }
    // Pass 12 (D-AUDIT.2.B): narrow prune for LostCommsDeclared only.
    // Pre-Pass-12 the comment said "never prune" because pruning at
    // tracked-loss would destroy in-flight handoff readbacks (the aircraft
    // IS HandingOff during the readback cycle, NOT in view.responsibilities
    // after Pass 7's Owned-only projection). That reasoning still holds for
    // Issued/Querying/Reissued — they may resolve via late readback (Pass 12
    // D-AUDIT.2.E).
    //
    // LostCommsDeclared IS terminal post-mortem; once the aircraft has
    // fully left the controller's world (not in view.responsibilities, not
    // observed) the entry is dead state. Prune.
    val prunedCoordinations = current.coordinations.mapValues { (acId, coords) ->
        if (acId in observed.keys || acId in view.responsibilities) coords
        else coords.filter { it.state !is CoordinationState.LostCommsDeclared }
    }.filterValues { it.isNotEmpty() }

    return current.copy(
        trackedAircraft = tracked,
        runwayBeliefs = view.runways,
        issuedClearances = view.activeClearances,
        aircraftLastObserved = lastObserved,
        establishedLocaliser = locEstablished,
        previousPositions = history,
        recentConcerns = prunedConcerns,
        outstandingReports = prunedReports,
        coordinations = prunedCoordinations,
    )
}

/**
 * Fold [ControllerEvent.CircuitIntentReported] events into
 * [BeliefState.circuitIntent], and clear the entry on
 * [ControllerEvent.GoAroundDetected].
 *
 * Per ICAO Doc 4444 §7.10.2 the pilot must re-declare circuit-end intent on
 * the rejoined circuit after a go-around — the previous intent is no longer
 * authoritative. Clearing the belief forces the controller to wait for the
 * new downwind report before issuing the next landing clearance type.
 *
 * **Firewall invariant:** this is the *only* function in the controller
 * module that writes [BeliefState.circuitIntent]. The architectural test
 * `FirewallBeliefWriteTest` enforces it.
 */
fun BeliefState.withCircuitIntentEvents(events: List<ControllerEvent>): BeliefState {
    if (events.isEmpty()) return this
    val updated = events.fold(circuitIntent) { acc, ev ->
        when (ev) {
            is ControllerEvent.CircuitIntentReported -> acc + (ev.aircraft to ev.intent)
            is ControllerEvent.GoAroundDetected -> acc - ev.aircraft
            // Explicitly listed so the compiler forces a decision when new
            // ControllerEvent variants are added. The `else -> acc` shape
            // hid `RunwayVacated → emptyList()` for years; we don't repeat it.
            is ControllerEvent.ReadyForDepartureReceived,
            is ControllerEvent.InitialContactReceived,
            is ControllerEvent.PositionReported,
            is ControllerEvent.ReadbackReceived,
            is ControllerEvent.StartupRequested,
            is ControllerEvent.TaxiRequested,
            is ControllerEvent.ResponsibilityTaken,
            is ControllerEvent.UnableReceived,
            is ControllerEvent.TrafficInSightReceived,
            is ControllerEvent.PilotRequestReceived,
            is ControllerEvent.AircraftArrivalCommitted,
            // fn-12 (R2): runway-scoped world events — not aircraft-scoped,
            // not circuit-intent-bearing. Explicit no-op arms preserve the
            // totality discipline.
            is ControllerEvent.RunwayObstructionDetected,
            is ControllerEvent.RunwayObstructionCleared -> acc
        }
    }
    return if (updated === circuitIntent) this else copy(circuitIntent = updated)
}

/**
 * fn-12 (R4): fold [ControllerEvent.RunwayObstructionDetected] /
 * [ControllerEvent.RunwayObstructionCleared] events into
 * [BeliefState.runwayObstructions]. Single-write site enforced by
 * [xyz.easiersaid.twr.controller.FirewallBeliefWriteTest].
 *
 * Detected → set the entry; Cleared → drop the entry. The events are
 * already per-controller-scoped (the sim's world-diff producer iterates
 * `state.world.aerodromes[view.aerodromeId].runways` per controller view),
 * so the fold can write directly with no aerodrome filter.
 *
 * Mirrors [withCircuitIntentEvents]'s shape — explicit per-leaf arms,
 * identity-equality short-circuit when no event-leaf changed the slice.
 */
fun BeliefState.withRunwayObstructionEvents(events: List<ControllerEvent>): BeliefState {
    if (events.isEmpty()) return this
    val updated = events.fold(runwayObstructions) { acc, ev ->
        when (ev) {
            is ControllerEvent.RunwayObstructionDetected -> acc + (ev.runway to ev.obstruction)
            is ControllerEvent.RunwayObstructionCleared -> acc - ev.runway
            // Non-obstruction leaves are no-ops on this slice. Listed
            // explicitly so the compiler forces a decision when new
            // ControllerEvent variants are added.
            is ControllerEvent.ReadyForDepartureReceived,
            is ControllerEvent.InitialContactReceived,
            is ControllerEvent.PositionReported,
            is ControllerEvent.ReadbackReceived,
            is ControllerEvent.StartupRequested,
            is ControllerEvent.TaxiRequested,
            is ControllerEvent.GoAroundDetected,
            is ControllerEvent.ResponsibilityTaken,
            is ControllerEvent.UnableReceived,
            is ControllerEvent.TrafficInSightReceived,
            is ControllerEvent.PilotRequestReceived,
            is ControllerEvent.CircuitIntentReported,
            is ControllerEvent.AircraftArrivalCommitted -> acc
        }
    }
    return if (updated === runwayObstructions) this else copy(runwayObstructions = updated)
}

/**
 * Fold [ControllerEvent]s into [BeliefState.recentRadio] (Pass 5,
 * D-AUDIT.14 closure). The buffer is time-windowed via [RecentRadio.append]
 * — entries older than [BeliefState.RECENT_RADIO_WINDOW] are evicted on
 * each append.
 *
 * Replaces the deleted `withAircraftIntentEvents` and `seedAircraftIntentFromStrips`
 * folds. `determineServiceKind` now derives intent on demand from this slice
 * (plus the strip read directly from the view) via [deriveCurrentIntent] —
 * no cached classification.
 *
 * **Firewall invariant:** this is the only write path into
 * [BeliefState.recentRadio]. The architectural test `FirewallBeliefWriteTest`
 * enforces this.
 */
fun BeliefState.withRecentRadio(events: List<ControllerEvent>, now: SimTime): BeliefState {
    if (events.isEmpty()) return this
    val updated = events.fold(recentRadio) { acc, event ->
        val aircraft = aircraftIdOf(event) ?: return@fold acc
        val prior = acc[aircraft] ?: RecentRadio.EMPTY
        acc + (aircraft to prior.append(event, now, BeliefState.RECENT_RADIO_WINDOW))
    }
    return if (updated === recentRadio) this else copy(recentRadio = updated)
}

/**
 * Extract the aircraft id this event concerns. Total over [ControllerEvent]
 * — Pass 1 [ExhaustivenessTest] enforces explicit per-leaf coverage.
 */
internal fun aircraftIdOf(event: ControllerEvent): AircraftId? = when (event) {
    is ControllerEvent.InitialContactReceived -> event.aircraft
    is ControllerEvent.AircraftArrivalCommitted -> event.aircraft
    is ControllerEvent.PositionReported -> event.aircraft
    is ControllerEvent.ReadyForDepartureReceived -> event.aircraft
    is ControllerEvent.ReadbackReceived -> event.aircraft
    is ControllerEvent.StartupRequested -> event.aircraft
    is ControllerEvent.TaxiRequested -> event.aircraft
    is ControllerEvent.GoAroundDetected -> event.aircraft
    is ControllerEvent.ResponsibilityTaken -> event.aircraft
    is ControllerEvent.UnableReceived -> event.aircraft
    is ControllerEvent.TrafficInSightReceived -> event.aircraft
    is ControllerEvent.PilotRequestReceived -> event.aircraft
    is ControllerEvent.CircuitIntentReported -> event.aircraft
    // fn-12 (R2): runway-scoped events have no aircraft id. The
    // recentRadio fold (which uses this lookup) skips events with no
    // aircraft via `?: return@fold acc`, so these contribute nothing
    // to per-aircraft radio history — correct: an obstruction event
    // is not radio.
    is ControllerEvent.RunwayObstructionDetected -> null
    is ControllerEvent.RunwayObstructionCleared -> null
}

/**
 * Derive an aircraft's current service intent on demand from primary sources.
 * Pass 5 (D-AUDIT.14 closure) replaces the cached `aircraftIntent` slice.
 *
 * Most-recent intent-bearing radio event wins; otherwise strip; otherwise
 * Transit. Total over (strip, recentRadio).
 */
fun deriveCurrentIntent(
    strip: arrow.core.Option<AircraftIntent>,
    recentRadio: RecentRadio,
): AircraftIntent {
    val radioIntent = recentRadio.entries
        .asReversed()
        .firstNotNullOfOrNull { intentFromRadio(it.event).getOrNull() }
    return radioIntent ?: strip.getOrElse { AircraftIntent.Transit }
}

/**
 * Compose [deriveCurrentIntent] over the two slice-shapes the controller
 * carries: a per-aircraft strip-intent map (read from the view) and the
 * per-aircraft recent-radio map (read from beliefs). Single source of
 * truth for "absent strip → None, absent radio → EMPTY" defaulting.
 *
 * Called from `OperatorContext.intentOf` (for guard / action consumers)
 * and from `reconcileCommitments`'s `reconcileOne` (for commitment
 * classification). Adding a third call site means routing it through this
 * function — never re-deriving the (strip+radio) composition inline.
 */
fun deriveIntent(
    flightStripIntents: Map<xyz.easiersaid.twr.protocol.AircraftId, AircraftIntent>,
    recentRadio: Map<xyz.easiersaid.twr.protocol.AircraftId, RecentRadio>,
    aircraft: xyz.easiersaid.twr.protocol.AircraftId,
): AircraftIntent {
    val strip = arrow.core.Option.fromNullable(flightStripIntents[aircraft])
    val radio = recentRadio[aircraft] ?: RecentRadio.EMPTY
    return deriveCurrentIntent(strip, radio)
}

/**
 * Per-leaf sealed `when` over [ControllerEvent]. Returns the intent encoded
 * in the event, or [arrow.core.None] if the event is not intent-bearing.
 *
 * Per Pass 1 [ExhaustivenessTest]: every leaf has an explicit arm. Pass 5
 * adds [EventExhaustivenessTest] (controller/jvmTest) scanning this function
 * for `ControllerEvent` leaf coverage.
 */
internal fun intentFromRadio(event: ControllerEvent): arrow.core.Option<AircraftIntent> = when (event) {
    is ControllerEvent.AircraftArrivalCommitted -> arrow.core.Some(AircraftIntent.Arriving)
    // Both InitialContactReceived(intentions) and PilotRequestReceived(request) carry
    // a typed RequestType. The same mapping applies — startup/taxi → Departing,
    // approach variants → Arriving, others → null. Without the PilotRequestReceived
    // arm, a pilot who first contacts with no intentions and then transmits a
    // standalone Request(RequestApproach) would leave the controller's derived
    // intent stuck at Transit. Real ATC reads both kinds of transmission as
    // updates to the working picture.
    is ControllerEvent.InitialContactReceived -> arrow.core.Option.fromNullable(
        event.intentions?.let(::intentFromRequestType),
    )
    is ControllerEvent.PilotRequestReceived -> arrow.core.Option.fromNullable(
        intentFromRequestType(event.request),
    )
    is ControllerEvent.ReadyForDepartureReceived,
    is ControllerEvent.PositionReported,
    is ControllerEvent.ReadbackReceived,
    is ControllerEvent.StartupRequested,
    is ControllerEvent.TaxiRequested,
    is ControllerEvent.GoAroundDetected,
    is ControllerEvent.ResponsibilityTaken,
    is ControllerEvent.UnableReceived,
    is ControllerEvent.TrafficInSightReceived,
    is ControllerEvent.CircuitIntentReported,
    // fn-12 (R2): runway-scoped world events carry no aircraft intent —
    // an obstruction is a runway condition, not a per-aircraft signal.
    is ControllerEvent.RunwayObstructionDetected,
    is ControllerEvent.RunwayObstructionCleared -> arrow.core.None
}

internal fun intentFromRequestType(rt: RequestType): AircraftIntent? = when (rt) {
    is RequestStartup, is RequestTaxi -> AircraftIntent.Departing
    is RequestVisualApproach, is RequestShortApproach,
    is RequestRightBase, is RequestApproach -> AircraftIntent.Arriving
    else -> null
}

/**
 * fn-28.4 (R23): runway-resolution failure for `Report(GoingAround)`.
 *
 * Either branch surfaces the diagnostic shape `resolveGoAroundRunway`
 * returns when the reporting aircraft has no resolvable runway:
 *  - no active TOWER_ARRIVAL commitment with a non-null `runway` field;
 *  - no controller-side `activeRunway` belief either.
 * Fail-closed: no arbitrary RunwayId silently written into the GA-belief.
 */
sealed interface GoAroundRunwayResolutionFailure {
    /** Reporting aircraft has no arrival commitment in `BeliefState.commitments`. */
    data class NoArrivalCommitment(val aircraft: AircraftId) : GoAroundRunwayResolutionFailure

    /** Arrival commitment exists but its `runway` field is null. */
    data class CommitmentRunwayNull(val aircraft: AircraftId) : GoAroundRunwayResolutionFailure

    /** No commitment AND no `BeliefState.activeRunway` fallback. */
    data class NoRunwayAnywhere(val aircraft: AircraftId) : GoAroundRunwayResolutionFailure
}

/**
 * fn-28.4 (R23 + round-10 Major 3): resolve the runway for a
 * `Report(GoingAround)` from [aircraft]. Primary source: the aircraft's
 * active TOWER_ARRIVAL commitment in [beliefs] (the runway the controller
 * committed the aircraft to landing on). Fallback: the controller's
 * `BeliefState.activeRunway` for the aerodrome (round-10 Major 3 — used
 * only when no commitment exists; the .5 scenario setup ensures
 * ARR-LAND/-TNG commitments form before the wind shift so the primary
 * path is the production path).
 *
 * Fail-closed: returns `Left(...)` when neither path yields a runway.
 * The fold then drops the would-be belief write rather than silently
 * writing an arbitrary key.
 *
 * **Plan-review reframing notes**: `Report(GoingAround)` carries no
 * runway payload by design (`Report(listOf(ReportEvent.GoingAround))`).
 * The controller's commitment ledger is the authoritative source — the
 * controller already knows which runway the aircraft was approaching
 * because it issued the landing clearance / arrival sequencing
 * instructions. Using the commitment runway also keeps the belief
 * stable across runway changes mid-cycle (the wind-shift case in .5's
 * golden).
 */
fun resolveGoAroundRunway(
    aircraft: AircraftId,
    beliefs: BeliefState,
): Either<GoAroundRunwayResolutionFailure, RunwayId> {
    val commitment = beliefs.commitments[aircraft]
    if (commitment != null && commitment.kind == CommitmentKind.TOWER_ARRIVAL) {
        val runway = commitment.runway
        if (runway != null) return runway.right()
        // Commitment exists but runway field null — fall through to
        // activeRunway fallback (round-10 Major 3).
        val active = beliefs.activeRunway
        return active?.right()
            ?: GoAroundRunwayResolutionFailure.CommitmentRunwayNull(aircraft).left()
    }
    // No arrival commitment — try the global activeRunway fallback.
    val active = beliefs.activeRunway
    return active?.right()
        ?: GoAroundRunwayResolutionFailure.NoArrivalCommitment(aircraft).left()
}

/**
 * fn-28.4 (R23): fold [ControllerEvent.GoAroundDetected] and
 * [ControllerEvent.PositionReported] events into
 * [BeliefState.goAroundInProgressByRunway] under the R23 lifecycle:
 *
 *  - **SET** on `GoAroundDetected(aircraft)`: resolve runway via
 *    [resolveGoAroundRunway]; on success AND the runway has no existing
 *    entry (first-writer-wins per R23 round-7 Minor 1), write
 *    `GoAroundInProgress(aircraft, setAtTime = now)`. Subsequent GA
 *    reports for a runway with an active entry are IGNORED.
 *  - **CLEAR (pattern-rejoin)**: a `PositionReported(aircraft, Downwind /
 *    Final / Base)` event for the tracked aircraft, where `now >
 *    entry.setAtTime`. The strict-inequality guard prevents a same-cycle
 *    stale Final report (arriving alongside the GA report in the same
 *    event batch) from immediately clearing what was just set —
 *    round-13 Major 3.
 *  - **CLEAR (timeout)**: any entry with
 *    `now.millis - entry.setAtTime.millis >= GO_AROUND_TIMEOUT_MS` is
 *    dropped. Applies regardless of `events` contents.
 *
 * Mirrors [withCircuitIntentEvents]'s shape: identity-equality
 * short-circuit when no leaf changed the slice; explicit per-leaf arms
 * over [ControllerEvent].
 *
 * **Firewall invariant**: sole write site for
 * [BeliefState.goAroundInProgressByRunway]. `FirewallBeliefWriteTest`
 * enforces this.
 *
 * **Doctrine**: ICAO Doc 4444 17th ed. Ch 12 §12.3.4 (aerodrome
 * sequencing / circuit phraseology). The runway-active-GA belief is
 * the controller-observable substrate driving downstream sequencing
 * decisions (extend trailing downwind traffic; do not turn base into
 * a GA-active runway).
 */
fun BeliefState.withGoAroundInProgress(
    events: List<ControllerEvent>,
    now: SimTime,
): BeliefState {
    // Phase 1: drop timed-out entries unconditionally (no events required).
    val afterTimeout = goAroundInProgressByRunway.filterValues { entry ->
        (now.millis - entry.setAtTime.millis) < BeliefState.GO_AROUND_TIMEOUT_MS
    }
    // Phase 2: pattern-rejoin clears — events from tracked aircraft whose
    // current cycle time is strictly later than the entry's setAtTime.
    val afterClears = events.fold(afterTimeout) { acc, ev ->
        when (ev) {
            is ControllerEvent.PositionReported -> {
                if (!isPatternRejoin(ev.event)) acc
                else acc.filterNot { (_, entry) ->
                    entry.aircraftId == ev.aircraft && now.millis > entry.setAtTime.millis
                }
            }
            // Non-position events do not clear.
            is ControllerEvent.GoAroundDetected,
            is ControllerEvent.ReadyForDepartureReceived,
            is ControllerEvent.InitialContactReceived,
            is ControllerEvent.ReadbackReceived,
            is ControllerEvent.StartupRequested,
            is ControllerEvent.TaxiRequested,
            is ControllerEvent.ResponsibilityTaken,
            is ControllerEvent.UnableReceived,
            is ControllerEvent.TrafficInSightReceived,
            is ControllerEvent.PilotRequestReceived,
            is ControllerEvent.CircuitIntentReported,
            is ControllerEvent.AircraftArrivalCommitted,
            is ControllerEvent.RunwayObstructionDetected,
            is ControllerEvent.RunwayObstructionCleared -> acc
        }
    }
    // Phase 3: SETs from GoAroundDetected events (first-writer-wins).
    val updated = events.fold(afterClears) { acc, ev ->
        when (ev) {
            is ControllerEvent.GoAroundDetected -> {
                val runwayResolution = resolveGoAroundRunway(ev.aircraft, this)
                val runway = runwayResolution.getOrNull() ?: return@fold acc
                // First-writer-wins: if entry already exists for this
                // runway, ignore the new report.
                if (runway in acc) acc
                else acc + (runway to GoAroundInProgress(ev.aircraft, now))
            }
            // Non-GA events do not set.
            is ControllerEvent.PositionReported,
            is ControllerEvent.ReadyForDepartureReceived,
            is ControllerEvent.InitialContactReceived,
            is ControllerEvent.ReadbackReceived,
            is ControllerEvent.StartupRequested,
            is ControllerEvent.TaxiRequested,
            is ControllerEvent.ResponsibilityTaken,
            is ControllerEvent.UnableReceived,
            is ControllerEvent.TrafficInSightReceived,
            is ControllerEvent.PilotRequestReceived,
            is ControllerEvent.CircuitIntentReported,
            is ControllerEvent.AircraftArrivalCommitted,
            is ControllerEvent.RunwayObstructionDetected,
            is ControllerEvent.RunwayObstructionCleared -> acc
        }
    }
    return if (updated == goAroundInProgressByRunway) this
    else copy(goAroundInProgressByRunway = updated)
}

/**
 * Is this report event a pattern-rejoin transmission per the R23
 * lifecycle? Downwind / Base / Final indicate the aircraft has rejoined
 * the circuit pattern post-GA. Other report events (Ready, RunwayVacated,
 * etc.) are not pattern-rejoin transmissions in the R23 sense.
 */
internal fun isPatternRejoin(event: ReportEvent): Boolean = when (event) {
    is ReportEvent.Downwind, is ReportEvent.Base,
    is ReportEvent.Final, is ReportEvent.LongFinal -> true
    is ReportEvent.Ready, is ReportEvent.RunwayVacated,
    is ReportEvent.Airborne, is ReportEvent.Established,
    is ReportEvent.EstablishedLocaliser, is ReportEvent.EstablishedGlidepath,
    is ReportEvent.GoingAround, is ReportEvent.VisualWithField,
    is ReportEvent.EstablishedInHold, is ReportEvent.TcasRa,
    is ReportEvent.MinimumFuel, is ReportEvent.PassingLevel,
    is ReportEvent.LeavingLevel, is ReportEvent.DistanceDme,
    is ReportEvent.OverFix -> false
}

private fun buildObservationHistory(
    current: Map<xyz.easiersaid.twr.protocol.AircraftId, List<ObservationSnapshot>>,
    observed: Map<xyz.easiersaid.twr.protocol.AircraftId, xyz.easiersaid.twr.controller.AircraftObservation>,
    time: xyz.easiersaid.twr.protocol.SimTime,
    tracked: Map<xyz.easiersaid.twr.protocol.AircraftId, xyz.easiersaid.twr.controller.AircraftObservation>,
): Map<xyz.easiersaid.twr.protocol.AircraftId, List<ObservationSnapshot>> {
    val maxHistory = BeliefState.MAX_OBSERVATION_HISTORY
    val result = mutableMapOf<xyz.easiersaid.twr.protocol.AircraftId, List<ObservationSnapshot>>()
    for ((acId, ac) in observed) {
        val snapshot = ObservationSnapshot(time, ac.position, ac.altitude, ac.groundSpeed)
        val existing = current[acId] ?: emptyList()
        result[acId] = (existing + snapshot).takeLast(maxHistory)
    }
    // Retain history for unobserved but tracked aircraft (don't drop on temporary loss of sight).
    for ((acId, history) in current) {
        if (acId in tracked && acId !in result) result[acId] = history
    }
    return result
}
