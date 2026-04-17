package xyz.easiersaid.twr.controller.observe

import xyz.easiersaid.twr.controller.ControllerView

/** Merge view into persistent beliefs. Perfect-sensor model: observation replaces belief. */
fun updateBeliefs(current: BeliefState, view: ControllerView): BeliefState {
    // Start with all observed aircraft (perfect sensor — observation is truth)
    // Then retain any unobserved aircraft we're still responsible for
    val observed = view.aircraft
    val tracked = observed + current.trackedAircraft.filterKeys { it in view.responsibilities && it !in observed }

    return current.copy(
        trackedAircraft = tracked,
        runwayBeliefs = view.runways,
        issuedClearances = view.activeClearances,
    )
}
