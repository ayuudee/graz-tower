package xyz.easiersaid.twr.sim.testing

import arrow.core.getOrElse
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.FiledPlan
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
        assertEquals(
            xyz.easiersaid.twr.protocol.AftnAddress(AerodromeId("LOWG"), RoleName.GROUND),
            ev.recipient,
            "C172 circuit training files to LOWG GROUND",
        )
        val plan = ev.plan as? FiledPlan.Vfr ?: fail("expected VFR plan, got ${ev.plan}")
        assertEquals(AerodromeId("LOWG"), plan.departureAerodrome)
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

    @Test
    fun `LOWG_LJMB_VFR fixture loads cleanly with all 4 controllers staffed and per-role frequencies`() {
        // G2 Phase A: cross-aerodrome fixture must load Right with both
        // aerodromes' worlds merged and 4 controllers staffed. Pre-existing
        // FixtureLoadSpec/FixtureSanityTest/LoaderRolesPopulatedTest failures
        // (LJMB world-candidate had no roles block) resolved by Phase A's
        // authoring fix.
        val loaded = Fixtures.LOWG_LJMB_VFR.load().getOrElse {
            fail("LOWG_LJMB_VFR fixture failed to load: $it")
        }
        assertTrue(
            AerodromeId("LOWG") in loaded.world.aerodromes,
            "merged world must contain LOWG",
        )
        assertTrue(
            AerodromeId("LJMB") in loaded.world.aerodromes,
            "merged world must contain LJMB",
        )
        assertEquals(
            setOf(
                xyz.easiersaid.twr.protocol.ControllerId("LOWG_GROUND"),
                xyz.easiersaid.twr.protocol.ControllerId("LOWG_TOWER"),
                xyz.easiersaid.twr.protocol.ControllerId("LOWG_APPROACH"),
                xyz.easiersaid.twr.protocol.ControllerId("LJMB_TOWER"),
            ),
            loaded.controllers.keys,
            "LOWG_LJMB_VFR staffs exactly 4 controllers (cardinal staffing doctrine)",
        )
        // Per-role frequency pin (Test S3): LOWG GND/TWR share 118.200,
        // LOWG APP is 119.300, LJMB TWR is 119.205. A regression that put
        // LOWG_APPROACH on 118.200 would still produce the right ControllerId
        // set; this row catches that.
        assertEquals(
            xyz.easiersaid.twr.protocol.Frequency.unsafe("118.200"),
            loaded.controllerAt(AerodromeId("LOWG"), RoleName.GROUND)?.frequency,
            "LOWG_GROUND on 118.200",
        )
        assertEquals(
            xyz.easiersaid.twr.protocol.Frequency.unsafe("118.200"),
            loaded.controllerAt(AerodromeId("LOWG"), RoleName.TOWER)?.frequency,
            "LOWG_TOWER shares 118.200 with GROUND",
        )
        assertEquals(
            xyz.easiersaid.twr.protocol.Frequency.unsafe("119.300"),
            loaded.controllerAt(AerodromeId("LOWG"), RoleName.APPROACH)?.frequency,
            "LOWG_APPROACH on 119.300",
        )
    }

    @Test
    fun `LOWG_LJMB_VFR LJMB_TOWER controller carries 119_205 frequency from authored roles`() {
        // G2 Phase A regression-pin: LJMB world-candidate authoring publishes
        // TOWER specifically at 119.205 (from MARIBOR TOWER in the SAP). If a
        // future edit replaces TOWER with a different role, or changes the
        // frequency, this row fails — LoaderRolesPopulatedTest's "any role"
        // check would silently pass, so this row pins the specific contract.
        val loaded = Fixtures.LOWG_LJMB_VFR.load().getOrElse {
            fail("LOWG_LJMB_VFR fixture failed to load: $it")
        }
        val ljmbTower = loaded.controllerAt(AerodromeId("LJMB"), RoleName.TOWER)
            ?: fail("LJMB_TOWER missing from staffed controllers")
        assertEquals(
            xyz.easiersaid.twr.protocol.Frequency.unsafe("119.205"),
            ljmbTower.frequency,
            "LJMB_TOWER must be 119.205 (Slovenia AIP AD 2.LJMB / SAP MARIBOR TOWER)",
        )
        assertEquals(AerodromeId("LJMB"), ljmbTower.aerodromeId)
    }

    @Test
    fun `LOWG_LJMB_VFR distributes one filed plan to LOWG_GROUND and LJMB_TOWER`() {
        // G2 Phase A: the cross-aerodrome filing distribution path (Pass 14
        // AftnRouting.routeFiledPlan with destinationAerodrome != departure
        // produces 2 recipients) is exercised end-to-end through Fixture.load.
        // CrossAerodromeFilingSpec already tests routeFiledPlan directly; this
        // row tests the integration with Fixture.load + the new fixture's
        // flightPlans shape.
        val loaded = Fixtures.LOWG_LJMB_VFR.load().getOrElse {
            fail("LOWG_LJMB_VFR fixture failed to load: $it")
        }
        val filings = loaded.initialEvents.filterIsInstance<SimEvent.FlightPlanFiled>()
        assertEquals(2, filings.size,
            "single FiledPlan(LOWG → LJMB) fans out to 2 recipients (departure + destination)")
        // Recipient ordering pin (Test S1): routeFiledPlan returns
        // NonEmptyList(depAddress, [destAddress]) — departure first by Pass 14
        // contract. Asserting the list (not a set) catches a regression that
        // swapped to "destination first", which would mis-sequence the
        // resulting SimEvent.FlightPlanFiled stream downstream.
        assertEquals(
            listOf(
                xyz.easiersaid.twr.protocol.AftnAddress(AerodromeId("LOWG"), RoleName.GROUND),
                xyz.easiersaid.twr.protocol.AftnAddress(AerodromeId("LJMB"), RoleName.TOWER),
            ),
            filings.map { it.recipient },
            "Pass 14 routing: VFR transit fans to departure GROUND first, destination TOWER second",
        )
        // All filings carry the same plan shape (one input plan).
        val plans = filings.map { it.plan }.distinct()
        assertEquals(1, plans.size, "all 2 events carry the same plan instance")
        val plan = plans.single() as? FiledPlan.Vfr
            ?: fail("expected VFR plan, got ${plans.single()}")
        assertEquals(AerodromeId("LOWG"), plan.departureAerodrome)
        assertEquals(AerodromeId("LJMB"), plan.destinationAerodrome)
        assertEquals(xyz.easiersaid.twr.protocol.RunwayId("14"), plan.destinationRunway)
        assertEquals(AircraftIntent.Transit, plan.intent)
    }
}
