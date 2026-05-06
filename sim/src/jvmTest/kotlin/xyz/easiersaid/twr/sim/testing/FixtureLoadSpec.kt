package xyz.easiersaid.twr.sim.testing

import arrow.core.getOrElse
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.IcaoTypeDesignator
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.sim.SimEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pass 11 (D-AUDIT.6) — `LoadedFixture.initialEvents` contract.
 *
 * Pins the loader's filing-event production: one
 * `SimEvent.FlightPlanFiled` per entry in `Fixture.flightPlans`,
 * sorted by `AircraftId.value` ascending so seq-assignment downstream
 * is deterministic across runs.
 *
 * Without this row, "loader silently dropped a plan" only surfaces
 * after a full G0 run (30 sim minutes); this test catches the
 * regression at unit-test latency.
 */
class FixtureLoadSpec {

    @Test
    fun `LOWG fixture produces exactly one FlightPlanFiled event for OE-ABC to GROUND`() {
        val loaded = Fixtures.LOWG.load().getOrElse { fail("LOWG fixture failed to load: $it") }
        val filings = loaded.initialEvents.filterIsInstance<SimEvent.FlightPlanFiled>()
        assertEquals(1, filings.size, "LOWG fixture has one filed plan; loader must emit one event")
        val ev = filings.single()
        assertEquals(AircraftId("OE-ABC"), ev.aircraft)
        assertEquals(RoleName.GROUND, ev.recipient, "C172 circuit training files to GROUND")
        val plan = ev.plan as? FiledPlan.Vfr ?: fail("expected VFR plan, got ${ev.plan}")
        assertEquals(AerodromeId("LOWG"), plan.departureAerodrome)
        assertEquals(IcaoTypeDesignator.unsafe("C172"), plan.aircraftType)
        assertEquals(null, plan.destinationAerodrome, "circuit training has no destination")
        assertEquals(AircraftIntent.Departing, plan.intent)
    }

    @Test
    fun `loader emits zero filing events for an empty flightPlans fixture`() {
        // LJMB has no flightPlans; loader must produce zero filings.
        val loaded = Fixtures.LJMB.load().getOrElse { fail("LJMB fixture failed to load: $it") }
        val filings = loaded.initialEvents.filterIsInstance<SimEvent.FlightPlanFiled>()
        assertTrue(filings.isEmpty(), "LJMB fixture has no flightPlans; loader emits no filings")
    }
}
