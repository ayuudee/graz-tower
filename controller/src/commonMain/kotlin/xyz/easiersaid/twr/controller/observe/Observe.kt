package xyz.easiersaid.twr.controller.observe

import xyz.easiersaid.twr.controller.ControllerView

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

    return current.copy(
        trackedAircraft = tracked,
        runwayBeliefs = view.runways,
        issuedClearances = view.activeClearances,
        aircraftLastObserved = lastObserved,
        establishedLocaliser = locEstablished,
        previousPositions = history,
    )
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
