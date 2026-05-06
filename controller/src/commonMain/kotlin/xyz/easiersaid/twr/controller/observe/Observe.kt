package xyz.easiersaid.twr.controller.observe

import arrow.core.getOrElse
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.RequestApproach
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
    // Coordinations are NOT pruned when an aircraft leaves tracked.
    // Responsibility transfer (e.g. ContactFrequency) happens before the readback arrives,
    // so the issuing controller must keep the coordination until processReadback confirms it
    // or escalateOverdueCoordinations advances it through the lifecycle. Pruning here would destroy the pending
    // state before the readback can be confirmed, making handoff detectors unreachable.

    return current.copy(
        trackedAircraft = tracked,
        runwayBeliefs = view.runways,
        issuedClearances = view.activeClearances,
        aircraftLastObserved = lastObserved,
        establishedLocaliser = locEstablished,
        previousPositions = history,
        recentConcerns = prunedConcerns,
        outstandingReports = prunedReports,
        coordinations = current.coordinations,
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
            is ControllerEvent.AircraftArrivalCommitted -> acc
        }
    }
    return if (updated === circuitIntent) this else copy(circuitIntent = updated)
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
    is ControllerEvent.CircuitIntentReported -> arrow.core.None
}

internal fun intentFromRequestType(rt: RequestType): AircraftIntent? = when (rt) {
    is RequestStartup, is RequestTaxi -> AircraftIntent.Departing
    is RequestVisualApproach, is RequestShortApproach,
    is RequestRightBase, is RequestApproach -> AircraftIntent.Arriving
    else -> null
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
