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

    /**
     * Aerodrome where the aircraft is going. **Null only for VFR
     * local-circuit / training flights** — IFR plans always have a
     * filed arrival aerodrome.
     *
     * Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13): hoisted
     * onto the sealed interface so AFTN routing (`AftnRouting`) and
     * destination classification (`AftnDestination.classify`) can
     * read a single field uniformly across `Vfr` and `Ifr`. Pre-Pass-14
     * `destinationOf(plan)` was duplicated in two modules; the property
     * deduplicates structurally.
     */
    val destinationAerodrome: AerodromeId?

    /**
     * Planned destination runway, when known at filing time.
     *
     * G2 (D-PF.3 closure): the pilot reads this at sim-init to populate
     * `mission.activeRunway` with `RunwayAssignmentSource.Filing`. Radio
     * sources supersede via `applyPrecedence` once any clearance lands.
     *
     * Null when:
     * - VFR plan with no destination runway specified (typical for circuit
     *   training and many local flights).
     * - IFR plan whose filed clearance does not yet name an arrival runway.
     */
    val destinationRunway: RunwayId?

    /**
     * VFR filed plan. Minimal: departure + intent. Destination is null
     * for circuit-training (depart and arrive same aerodrome — distinct
     * from "depart-to-self" routing by intent).
     *
     * Aircraft type lives on [AircraftState.type] — there is one ICAO
     * designator per physical aircraft, and the strip-display layer
     * looks it up at presentation time. Pass 11 post-impl review M.2:
     * carrying `aircraftType` here would duplicate doctrine across two
     * sources of truth.
     *
     * G2: [destinationRunway] is stored. For circuit training (where
     * `destinationAerodrome == null`), the runway is the planned
     * arrival/touch-and-go runway at the same field.
     */
    data class Vfr(
        override val departureAerodrome: AerodromeId,
        /** Where the aircraft is going. Null for local circuit / training. */
        override val destinationAerodrome: AerodromeId?,
        /** Broad service intent (Departing/Arriving/Transit). */
        val intent: AircraftIntent,
        /** Planned destination runway. Null when the pilot has not pre-selected. */
        override val destinationRunway: RunwayId? = null,
    ) : FiledPlan

    /**
     * IFR filed plan, wrapping the existing [FlightPlan] (which has the
     * route + clearance state machine pre-Pass-11).
     *
     * `departureAerodrome` and `destinationAerodrome` are **delegated**,
     * not duplicated — making illegal states unrepresentable rather
     * than relying on an `init` invariant to police them. IFR's
     * `destinationAerodrome` is non-null because [FlightPlan.arrivalAerodrome]
     * is non-null.
     *
     * G2: [destinationRunway] is **derived** from the wrapped
     * [FlightPlan.clearance], not stored — preventing two-truths drift
     * with [ClearanceState.ApproachClearance.arrivalRunway]. Pre-approach
     * clearance states (Uncleaned, EnRouteClearance) have no arrival
     * runway yet, so the derivation returns null until ATC issues the
     * approach clearance.
     */
    data class Ifr(
        val flightPlan: FlightPlan,
    ) : FiledPlan {
        override val departureAerodrome: AerodromeId get() = flightPlan.departureAerodrome
        override val destinationAerodrome: AerodromeId get() = flightPlan.arrivalAerodrome
        override val destinationRunway: RunwayId? get() = when (val c = flightPlan.clearance) {
            is ClearanceState.ApproachClearance -> c.arrivalRunway
            is ClearanceState.EnRouteClearance -> null
            ClearanceState.Uncleaned -> null
        }
    }
}
