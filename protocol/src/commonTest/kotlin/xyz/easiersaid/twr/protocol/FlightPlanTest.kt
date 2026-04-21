package xyz.easiersaid.twr.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for [FlightPlan], [ClearanceState], and [amendFpl].
 *
 * Layer 5 from the IFR plan: FPL amendment unit tests + state transition tests.
 */
class FlightPlanTest {

    private val depAd = AerodromeId("LOWG")
    private val arrAd = AerodromeId("LJLJ")
    private val altAd = AerodromeId("LOWW")
    private val rwy09 = RunwayId("09")
    private val rwy27 = RunwayId("27")
    private val fl100 = Level.FlightLevel.unsafe(100)
    private val fl080 = Level.FlightLevel.unsafe(80)
    private val fl060 = Level.FlightLevel.unsafe(60)
    private val sidId = SidId("SID-09A")
    private val starId = StarId("STAR-09B")
    private val target = AircraftId("TEST")
    private val wp1 = FixId("WP1")
    private val wp2 = FixId("WP2")
    private val wp3 = FixId("WP3")

    private fun baseFpl() = FlightPlan(
        departureAerodrome = depAd,
        arrivalAerodrome = arrAd,
        alternateAerodrome = altAd,
        requestedLevel = fl100,
        enRouteWaypoints = listOf(wp1, wp2, wp3),
    )

    private fun clearedFpl() = baseFpl().copy(
        clearance = ClearanceState.EnRouteClearance(
            clearanceLimit = wp3,
            departureRunway = rwy09,
            sid = sidId,
        ),
    )

    private fun approachClearedFpl() = baseFpl().copy(
        clearance = ClearanceState.ApproachClearance(
            clearanceLimit = wp3,
            departureRunway = rwy09,
            sid = sidId,
            star = starId,
            approachType = ApproachType.ILS,
            arrivalRunway = rwy09,
        ),
    )

    // ── FlightPlan construction ─────────────────────────────────────

    @Test
    fun `new FlightPlan has Uncleaned state`() {
        val fpl = baseFpl()
        assertTrue(fpl.clearance is ClearanceState.Uncleaned)
        assertEquals(listOf(wp1, wp2, wp3), fpl.enRouteWaypoints)
        assertEquals(fl100, fpl.requestedLevel)
    }

    // ── ClearedTo: Uncleaned → EnRouteClearance ─────────────────────

    @Test
    fun `ClearedTo advances Uncleaned to EnRouteClearance`() {
        // Need a pre-existing departure runway — set via an intermediate cleared state
        val fpl = baseFpl().copy(
            clearance = ClearanceState.EnRouteClearance(
                clearanceLimit = wp1,
                departureRunway = rwy09,
            ),
        )
        val result = amendFpl(fpl, ClearedTo(target, clearanceLimit = wp3, route = RouteSpec.ViaSid(sidId)))
        val amended = result.fold({ fail("Expected Right, got $it") }, { it })
        val clearance = amended.clearance
        assertTrue(clearance is ClearanceState.EnRouteClearance)
        assertEquals(wp3, clearance.clearanceLimit)
        assertEquals(sidId, clearance.sid)
        assertEquals(rwy09, clearance.departureRunway)
    }

    @Test
    fun `ClearedTo on Uncleaned without departure runway returns InvalidTransition`() {
        val fpl = baseFpl()
        val result = amendFpl(fpl, ClearedTo(target, clearanceLimit = wp3))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull() is AmendmentError.InvalidTransition)
    }

    @Test
    fun `ClearedTo with ViaStar sets star`() {
        val fpl = clearedFpl()
        val result = amendFpl(fpl, ClearedTo(target, clearanceLimit = wp3, route = RouteSpec.ViaStar(starId)))
        val amended = result.fold({ fail("Expected Right") }, { it })
        val clearance = amended.clearance as ClearanceState.EnRouteClearance
        assertEquals(starId, clearance.star)
        assertEquals(sidId, clearance.sid) // SID preserved from previous clearance
    }

    // ── ClearedApproach: EnRouteClearance → ApproachClearance ───────

    @Test
    fun `ClearedApproach advances EnRouteClearance to ApproachClearance`() {
        val fpl = clearedFpl()
        val result = amendFpl(fpl, ClearedApproach(target, ApproachType.ILS, rwy09))
        val amended = result.fold({ fail("Expected Right") }, { it })
        val clearance = amended.clearance
        assertTrue(clearance is ClearanceState.ApproachClearance)
        assertEquals(ApproachType.ILS, clearance.approachType)
        assertEquals(rwy09, clearance.arrivalRunway)
        assertEquals(sidId, clearance.sid) // preserved
    }

    @Test
    fun `ClearedApproach on Uncleaned returns InvalidTransition`() {
        val fpl = baseFpl()
        val result = amendFpl(fpl, ClearedApproach(target, ApproachType.ILS, rwy09))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull() is AmendmentError.InvalidTransition)
    }

    @Test
    fun `ClearedApproach re-clears on existing ApproachClearance`() {
        val fpl = approachClearedFpl()
        val result = amendFpl(fpl, ClearedApproach(target, ApproachType.VOR, rwy27))
        val amended = result.fold({ fail("Expected Right") }, { it })
        val clearance = amended.clearance as ClearanceState.ApproachClearance
        assertEquals(ApproachType.VOR, clearance.approachType)
        assertEquals(rwy27, clearance.arrivalRunway)
    }

    // ── ProceedDirect: truncate en-route waypoints ──────────────────

    @Test
    fun `ProceedDirect truncates waypoints from fix onward`() {
        val fpl = clearedFpl()
        val result = amendFpl(fpl, ProceedDirect(target, wp2))
        val amended = result.fold({ fail("Expected Right") }, { it })
        assertEquals(listOf(wp2, wp3), amended.enRouteWaypoints)
    }

    @Test
    fun `ProceedDirect to fix not on route returns FixNotOnRoute`() {
        val fpl = clearedFpl()
        val result = amendFpl(fpl, ProceedDirect(target, FixId("NOWHERE")))
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull() is AmendmentError.FixNotOnRoute)
    }

    // ── Level amendments ────────────────────────────────────────────

    @Test
    fun `ClimbTo updates cleared level on EnRouteClearance`() {
        val fpl = clearedFpl()
        val result = amendFpl(fpl, ClimbTo(target, fl080))
        val amended = result.fold({ fail("Expected Right") }, { it })
        val clearance = amended.clearance as ClearanceState.EnRouteClearance
        assertEquals(fl080, clearance.clearedLevel)
    }

    @Test
    fun `DescendTo updates cleared level on ApproachClearance`() {
        val fpl = approachClearedFpl()
        val result = amendFpl(fpl, DescendTo(target, fl060))
        val amended = result.fold({ fail("Expected Right") }, { it })
        val clearance = amended.clearance as ClearanceState.ApproachClearance
        assertEquals(fl060, clearance.clearedLevel)
    }

    @Test
    fun `ClimbTo on Uncleaned is no-op`() {
        val fpl = baseFpl()
        val result = amendFpl(fpl, ClimbTo(target, fl080))
        val amended = result.fold({ fail("Expected Right") }, { it })
        assertEquals(fpl, amended) // unchanged
    }

    // ── No-op instructions ──────────────────────────────────────────

    @Test
    fun `TaxiTo has no FPL effect`() {
        val fpl = clearedFpl()
        val result = amendFpl(fpl, TaxiTo(target, PointId("HOLD"), emptyList()))
        val amended = result.fold({ fail("Expected Right") }, { it })
        assertEquals(fpl, amended)
    }

    @Test
    fun `FlyHeading has no FPL effect`() {
        val fpl = clearedFpl()
        val result = amendFpl(fpl, FlyHeading(target, Heading.unsafe(270)))
        val amended = result.fold({ fail("Expected Right") }, { it })
        assertEquals(fpl, amended)
    }

    // ── Amendment sequences ─────────────────────────────────────────

    @Test
    fun `ClearedTo then ProceedDirect then ClearedApproach — cumulative state`() {
        // Start with a departure-cleared FPL.
        var fpl = baseFpl().copy(
            clearance = ClearanceState.EnRouteClearance(
                clearanceLimit = wp1,
                departureRunway = rwy09,
            ),
        )

        // Step 1: ClearedTo with full clearance limit + SID.
        fpl = amendFpl(fpl, ClearedTo(target, wp3, RouteSpec.ViaSid(sidId)))
            .fold({ fail("Step 1 failed: $it") }, { it })
        assertEquals(wp3, (fpl.clearance as ClearanceState.EnRouteClearance).clearanceLimit)
        assertEquals(sidId, (fpl.clearance as ClearanceState.EnRouteClearance).sid)

        // Step 2: ProceedDirect to wp2 (skip wp1).
        fpl = amendFpl(fpl, ProceedDirect(target, wp2))
            .fold({ fail("Step 2 failed: $it") }, { it })
        assertEquals(listOf(wp2, wp3), fpl.enRouteWaypoints)

        // Step 3: ClearedApproach.
        fpl = amendFpl(fpl, ClearedApproach(target, ApproachType.ILS, rwy09))
            .fold({ fail("Step 3 failed: $it") }, { it })
        val clearance = fpl.clearance as ClearanceState.ApproachClearance
        assertEquals(ApproachType.ILS, clearance.approachType)
        assertEquals(rwy09, clearance.arrivalRunway)
        assertEquals(sidId, clearance.sid) // preserved through all steps
        assertEquals(listOf(wp2, wp3), fpl.enRouteWaypoints) // ProceedDirect effect preserved
    }
}
