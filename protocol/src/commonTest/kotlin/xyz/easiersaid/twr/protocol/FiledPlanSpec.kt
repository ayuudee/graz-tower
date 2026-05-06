package xyz.easiersaid.twr.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pass 11 (D-AUDIT.6) — `FiledPlan` shape and delegation contract.
 *
 * Two rows:
 *  1. [FiledPlan.Vfr] constructs cleanly with `destinationAerodrome = null`
 *     for circuit training (depart and arrive same aerodrome).
 *  2. [FiledPlan.Ifr.departureAerodrome] is delegated to the wrapped
 *     [FlightPlan] — no duplicate field, no `init` invariant. Making
 *     illegal states unrepresentable.
 *
 * (Sealed-leaves smoke test cut per plan-review test-1: any consumer
 * `when(plan: FiledPlan)` is compiler-exhaustiveness-checked already.)
 */
class FiledPlanSpec {

    @Test
    fun `Vfr accepts null destination for circuit training`() {
        val plan = FiledPlan.Vfr(
            departureAerodrome = AerodromeId("LOWG"),
            aircraftType = IcaoTypeDesignator.unsafe("C172"),
            destinationAerodrome = null,
            intent = AircraftIntent.Departing,
        )
        assertEquals(AerodromeId("LOWG"), plan.departureAerodrome)
        assertEquals(null, plan.destinationAerodrome, "circuit training has no destination")
        assertEquals(AircraftIntent.Departing, plan.intent)
    }

    @Test
    fun `Ifr departureAerodrome delegates to wrapped FlightPlan`() {
        val flightPlan = FlightPlan(
            departureAerodrome = AerodromeId("LOWG"),
            arrivalAerodrome = AerodromeId("LJMB"),
            requestedLevel = Level.AltitudeFeet.unsafe(8000),
            enRouteWaypoints = emptyList(),
        )
        val filed = FiledPlan.Ifr(
            aircraftType = IcaoTypeDesignator.unsafe("B738"),
            flightPlan = flightPlan,
        )
        // Delegation: the field is computed from the wrapped FlightPlan,
        // not duplicated. Mutating `flightPlan.departureAerodrome` (impossible
        // here — data class) would propagate; setting it to a different
        // value is impossible at construction.
        assertEquals(flightPlan.departureAerodrome, filed.departureAerodrome)
    }
}
