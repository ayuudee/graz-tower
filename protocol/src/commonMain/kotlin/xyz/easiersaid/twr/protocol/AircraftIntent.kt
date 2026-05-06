package xyz.easiersaid.twr.protocol

/**
 * Broad service intent — Departing / Arriving / Transit.
 *
 * Pass 11 (D-AUDIT.6): lifted from `:controller/observe` to `:protocol` —
 * this is the value the AFTN-distributed strip carries, the strip is
 * upstream of the controller's belief, and the controller's belief is
 * downstream-derived. Single source of truth at the protocol layer.
 *
 * Pass 5 (D-AUDIT.14 closure): no cached `BeliefState.aircraftIntent`
 * slice on the controller side. The controller derives intent on demand
 * via `deriveCurrentIntent` from primary observable sources (the strip
 * + recent radio history + position). This type is the classification
 * VALUE, not a cache.
 */
sealed interface AircraftIntent {
    data object Departing : AircraftIntent
    data object Arriving : AircraftIntent
    data object Transit : AircraftIntent
}
