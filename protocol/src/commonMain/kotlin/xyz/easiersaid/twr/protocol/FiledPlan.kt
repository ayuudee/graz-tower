package xyz.easiersaid.twr.protocol

/**
 * Filed flight plan — the AFTN-distributed strip-shaped value the
 * controller receives before the aircraft physically appears on their
 * frequency.
 *
 * Pass 11 (D-AUDIT.6 closure): replaces the pre-Pass-11 fixture cheat
 * (`controllers[GND].responsibilities = setOf(ac)` injected at sim-init)
 * with a typed event-driven flow. Real ATC's strip arrives via AFTN
 * minutes-to-hours before engine start; this type is the data the AFTN
 * carries.
 *
 * Sealed:
 *  - [Vfr] for VFR flights (the bulk of GA traffic; circuit training,
 *    sightseeing, training flights, helicopters under VFR).
 *  - [Ifr] for IFR flights, wrapping the existing [FlightPlan] type
 *    (cruising level, en-route waypoints, alternate, clearance state).
 *
 * The discriminator is operationally meaningful: VFR vs IFR drives
 * separation rules, mandatory equipment, communication procedures, and
 * controller workload. Sealed to force every consumer to decide.
 */
sealed interface FiledPlan {
    /** Aerodrome where the aircraft starts. Always known at filing. */
    val departureAerodrome: AerodromeId
    /** Aircraft type — Doc 8643 designator. Doc 4444 §11 strip data. */
    val aircraftType: IcaoTypeDesignator

    /**
     * VFR filed plan. Minimal: departure + intent. Destination is null
     * for circuit-training (depart and arrive same aerodrome — distinct
     * from "depart-to-self" routing by intent).
     */
    data class Vfr(
        override val departureAerodrome: AerodromeId,
        override val aircraftType: IcaoTypeDesignator,
        /** Where the aircraft is going. Null for local circuit / training. */
        val destinationAerodrome: AerodromeId?,
        /** Broad service intent (Departing/Arriving/Transit). */
        val intent: AircraftIntent,
    ) : FiledPlan

    /**
     * IFR filed plan, wrapping the existing [FlightPlan] (which has the
     * route + clearance state machine pre-Pass-11).
     *
     * `departureAerodrome` is **delegated**, not duplicated — making
     * illegal states unrepresentable rather than relying on an `init`
     * invariant to police them.
     */
    data class Ifr(
        override val aircraftType: IcaoTypeDesignator,
        val flightPlan: FlightPlan,
    ) : FiledPlan {
        override val departureAerodrome: AerodromeId get() = flightPlan.departureAerodrome
    }
}
