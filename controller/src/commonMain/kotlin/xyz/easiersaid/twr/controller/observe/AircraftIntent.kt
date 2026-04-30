package xyz.easiersaid.twr.controller.observe

/**
 * Broad controller-side classification of an aircraft's service intent.
 *
 * Pass 5 (D-AUDIT.14 closure): no longer cached as a [BeliefState] slice.
 * Derived on demand by [deriveCurrentIntent] from primary observable sources:
 * the strip (filed plan), recent radio history, and aircraft position.
 *
 * The type itself remains: it is the classification's value, not a cache.
 * It is the value of [xyz.easiersaid.twr.sim.FlightStrip.intent] (the filed
 * pre-briefing) and the return type of [deriveCurrentIntent].
 */
sealed interface AircraftIntent {
    data object Departing : AircraftIntent
    data object Arriving : AircraftIntent
    data object Transit : AircraftIntent
}
