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

    // ── fn-8.1: LOWG_TWO_AIRCRAFT fixture + per-aircraft startPoints validation ──

    @Test
    fun `LOWG_TWO_AIRCRAFT loads cleanly with two distinct startPoints and two filings`() {
        val loaded = Fixtures.LOWG_TWO_AIRCRAFT.load().getOrElse {
            fail("LOWG_TWO_AIRCRAFT fixture failed to load: $it")
        }
        // Two filings — one per aircraft, both to LOWG_GROUND (single-
        // aerodrome circuit training; routeFiledPlan returns 1 recipient).
        val filings = loaded.initialEvents.filterIsInstance<SimEvent.FlightPlanFiled>()
        assertEquals(2, filings.size, "two aircraft, each with one filed plan, single-aerodrome routing")
        assertEquals(
            listOf(AircraftId("OE-ABC"), AircraftId("OE-DEF")),
            filings.map { it.aircraft },
            "loader sorts by AircraftId.value ascending: OE-ABC before OE-DEF",
        )
        // requiredStartPoints helper returns the non-null map.
        val starts = Fixtures.LOWG_TWO_AIRCRAFT.requiredStartPoints()
        assertEquals(
            mapOf(
                AircraftId("OE-ABC") to xyz.easiersaid.twr.protocol.PointId("LOWG_STAND_1_POINT"),
                AircraftId("OE-DEF") to xyz.easiersaid.twr.protocol.PointId("LOWG_STAND_2_POINT"),
            ),
            starts,
            "two distinct adjacent GA stands authored from world-candidate",
        )
    }

    @Test
    fun `requiredStartPoints throws on a single-aircraft fixture`() {
        // G0 LOWG fixture has startPoints = null. Calling requiredStartPoints
        // on it must fail loud rather than NPE on a downstream getValue.
        val ex = kotlin.runCatching { Fixtures.LOWG.requiredStartPoints() }.exceptionOrNull()
            ?: fail("requiredStartPoints must throw on a single-aircraft fixture")
        assertTrue(
            ex.message?.contains("single-aircraft fixture") == true,
            "loud error must name the failure mode; got: ${ex.message}",
        )
    }

    @Test
    fun `validate flags StartPointWithoutFlightPlan for orphan startPoints entry`() {
        val fixture = Fixtures.LOWG_TWO_AIRCRAFT.copy(
            // OE-XYZ has a start point but no flight plan.
            startPoints = mapOf(
                AircraftId("OE-ABC") to xyz.easiersaid.twr.protocol.PointId("LOWG_STAND_1_POINT"),
                AircraftId("OE-XYZ") to xyz.easiersaid.twr.protocol.PointId("LOWG_STAND_2_POINT"),
            ),
            // OE-DEF stays in flightPlans → triggers the inverse violation too.
        )
        val result = fixture.load()
        val violations = (result as? arrow.core.Either.Left)?.value as? LoadError.ValidationFailed
            ?: fail("expected ValidationFailed Left; got $result")
        assertTrue(
            violations.violations.any {
                it == FixtureViolation.StartPointWithoutFlightPlan(
                    aircraft = AircraftId("OE-XYZ"),
                    point = xyz.easiersaid.twr.protocol.PointId("LOWG_STAND_2_POINT"),
                )
            },
            "must surface StartPointWithoutFlightPlan for OE-XYZ; got ${violations.violations}",
        )
    }

    @Test
    fun `validate flags FlightPlanMissingStartPoint for orphan plan`() {
        val fixture = Fixtures.LOWG_TWO_AIRCRAFT.copy(
            startPoints = mapOf(
                // Only OE-ABC has a start point; OE-DEF's plan is orphaned.
                AircraftId("OE-ABC") to xyz.easiersaid.twr.protocol.PointId("LOWG_STAND_1_POINT"),
            ),
        )
        val result = fixture.load()
        val violations = (result as? arrow.core.Either.Left)?.value as? LoadError.ValidationFailed
            ?: fail("expected ValidationFailed Left; got $result")
        assertTrue(
            violations.violations.any {
                it == FixtureViolation.FlightPlanMissingStartPoint(AircraftId("OE-DEF"))
            },
            "must surface FlightPlanMissingStartPoint for OE-DEF; got ${violations.violations}",
        )
    }

    @Test
    fun `validate flags DuplicateStartPoint when two aircraft share a point`() {
        val fixture = Fixtures.LOWG_TWO_AIRCRAFT.copy(
            startPoints = mapOf(
                AircraftId("OE-ABC") to xyz.easiersaid.twr.protocol.PointId("LOWG_STAND_1_POINT"),
                AircraftId("OE-DEF") to xyz.easiersaid.twr.protocol.PointId("LOWG_STAND_1_POINT"),
            ),
        )
        val result = fixture.load()
        val violations = (result as? arrow.core.Either.Left)?.value as? LoadError.ValidationFailed
            ?: fail("expected ValidationFailed Left; got $result")
        assertTrue(
            violations.violations.any {
                it is FixtureViolation.DuplicateStartPoint &&
                    it.point == xyz.easiersaid.twr.protocol.PointId("LOWG_STAND_1_POINT") &&
                    it.aircraft.toSet() == setOf(AircraftId("OE-ABC"), AircraftId("OE-DEF"))
            },
            "must surface DuplicateStartPoint; got ${violations.violations}",
        )
    }

    @Test
    fun `validate flags StartPointMissing for an unknown PointId`() {
        val fixture = Fixtures.LOWG_TWO_AIRCRAFT.copy(
            startPoints = mapOf(
                AircraftId("OE-ABC") to xyz.easiersaid.twr.protocol.PointId("LOWG_STAND_1_POINT"),
                AircraftId("OE-DEF") to xyz.easiersaid.twr.protocol.PointId("LOWG_DOES_NOT_EXIST"),
            ),
        )
        val result = fixture.load()
        val violations = (result as? arrow.core.Either.Left)?.value as? LoadError.ValidationFailed
            ?: fail("expected ValidationFailed Left; got $result")
        assertTrue(
            violations.violations.any {
                it == FixtureViolation.StartPointMissing(
                    aircraft = AircraftId("OE-DEF"),
                    point = xyz.easiersaid.twr.protocol.PointId("LOWG_DOES_NOT_EXIST"),
                )
            },
            "must surface StartPointMissing for OE-DEF's bogus point; got ${violations.violations}",
        )
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
