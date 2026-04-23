package xyz.easiersaid.twr.sim

import arrow.core.None
import arrow.core.NonEmptyList
import arrow.core.Some
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.core.world.*
import xyz.easiersaid.twr.core.world.AltitudeConstraint as AltC
import xyz.easiersaid.twr.core.world.SpeedConstraint as SpdC
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for [buildAirborneRoute] — the pilot route planner.
 *
 * Four layers:
 *   1. Unit tests for implemented cells with strong assertions:
 *      waypoints in positions + circuitLegsByPoint, monotonic leg ordering,
 *      arrivalPhase, targetAltitudeM.
 *   2. Truth table for all mode×taskName cells — every combination is an
 *      explicit decision (Right, Left(InvalidCombination), Left(NotYetImplemented)).
 *   3. Unimplemented modes assert Left with correct error type per cell.
 *   4. Error path tests for CircuitNotFound, InsufficientGeometry, etc.
 */
class PilotRoutePlannerTest {

    // ── Test world ──────────────────────────────────────────────────

    private val runwayId = RunwayId("09")
    private val circuitId = CircuitProcedureId("CCT-09L")
    private val sidId = SidId("SID-09A")
    private val starId = StarId("STAR-09B")
    private val approachId = ApproachId("ILS-09")
    private val holdId = HoldingPatternId("HOLD-MAP")

    private object Pts {
        val thrA = PointId("THR-09")
        val thrB = PointId("THR-27")
        val upwind = PointId("UPWIND-1")
        val crosswind = PointId("CROSSWIND-1")
        val downwind = PointId("DOWNWIND-1")
        val base = PointId("BASE-1")
        val finalPt = PointId("FINAL-1")
        // SID waypoints
        val sidWp1 = PointId("SID-WP1")
        val sidWp2 = PointId("SID-WP2")
        // STAR + approach waypoints
        val starEntry = PointId("STAR-ENTRY")
        val iaf = PointId("IAF")
        val faf = PointId("FAF")
        val mapFix = PointId("MAP-FIX")
    }

    private val positions = mapOf(
        Pts.thrA to Position(0.0, 0.0),
        Pts.thrB to Position(900.0, 0.0),
        Pts.upwind to Position(1200.0, 0.0),
        Pts.crosswind to Position(1200.0, 400.0),
        Pts.downwind to Position(0.0, 400.0),
        Pts.base to Position(-200.0, 250.0),
        Pts.finalPt to Position(-200.0, 0.0),
        Pts.sidWp1 to Position(2000.0, 0.0),
        Pts.sidWp2 to Position(3000.0, 200.0),
        Pts.starEntry to Position(-5000.0, 1000.0),
        Pts.iaf to Position(-3000.0, 500.0),
        Pts.faf to Position(-500.0, 0.0),
        Pts.mapFix to Position(1500.0, 300.0),
    )

    private val world = AviationWorld(
        aerodromes = mapOf(
            AerodromeId("TEST") to Aerodrome(
                icao = AerodromeId("TEST"),
                elevation = Feet(0),
                magneticVariation = Degrees(0.0),
                transitionAltitude = Level.AltitudeFeet.unsafe(3000),
                runways = mapOf(
                    runwayId to Runway(
                        id = runwayId,
                        path = Path(listOf(Pts.thrA, Pts.thrB)),
                        threshold = Pts.thrA,
                        exits = emptyList(),
                    ),
                ),
                circuits = mapOf(
                    circuitId to CircuitProcedure(
                        id = circuitId,
                        runway = runwayId,
                        direction = CircuitDirection.LEFT_HAND,
                        legs = listOf(
                            CircuitLeg(LegName.UPWIND, Path(listOf(Pts.thrA, Pts.upwind))),
                            CircuitLeg(LegName.CROSSWIND, Path(listOf(Pts.upwind, Pts.crosswind))),
                            CircuitLeg(LegName.DOWNWIND, Path(listOf(Pts.crosswind, Pts.downwind))),
                            CircuitLeg(LegName.BASE, Path(listOf(Pts.downwind, Pts.base))),
                            CircuitLeg(LegName.FINAL, Path(listOf(Pts.base, Pts.finalPt, Pts.thrA))),
                        ),
                        altitude = Level.AltitudeFeet.unsafe(500),
                        goAroundPath = Path(listOf(Pts.thrA, Pts.upwind)),
                    ),
                ),
                sids = mapOf(
                    sidId to Sid(
                        id = sidId,
                        name = "DEPARTURE ONE ALPHA",
                        runway = runwayId,
                        waypoints = listOf(
                            Waypoint(Pts.sidWp1, altitudeConstraint = AltC.AtOrAbove(Level.AltitudeFeet.unsafe(2000))),
                            Waypoint(Pts.sidWp2, altitudeConstraint = AltC.AtOrAbove(Level.AltitudeFeet.unsafe(4000))),
                        ),
                    ),
                ),
                stars = mapOf(
                    starId to Star(
                        id = starId,
                        name = "ARRIVAL ONE BRAVO",
                        waypoints = listOf(
                            Waypoint(Pts.starEntry, altitudeConstraint = AltC.AtOrBelow(Level.AltitudeFeet.unsafe(6000))),
                            Waypoint(Pts.iaf, altitudeConstraint = AltC.AtOrBelow(Level.AltitudeFeet.unsafe(3000))),
                        ),
                    ),
                ),
                approaches = mapOf(
                    approachId to InstrumentApproach(
                        id = approachId,
                        name = "ILS RWY 09",
                        type = ApproachType.ILS,
                        runway = runwayId,
                        waypoints = listOf(
                            Waypoint(Pts.iaf, altitudeConstraint = AltC.AtOrBelow(Level.AltitudeFeet.unsafe(3000))),
                            Waypoint(Pts.faf, altitudeConstraint = AltC.At(Level.AltitudeFeet.unsafe(2000))),
                            Waypoint(Pts.thrA),
                        ),
                        minimumAltitude = ApproachMinimum(MinimumType.DECISION_ALTITUDE, Level.AltitudeFeet.unsafe(200)),
                        missedApproach = MissedApproachProcedure(
                            waypoints = listOf(Waypoint(Pts.mapFix), Waypoint(Pts.sidWp1)),
                            holdAt = holdId,
                        ),
                    ),
                ),
            ),
        ),
    )

    /** World with runway but no circuit procedure — for error path tests. */
    private val worldNoCircuit = AviationWorld(
        aerodromes = mapOf(
            AerodromeId("TEST") to Aerodrome(
                icao = AerodromeId("TEST"),
                elevation = Feet(0),
                magneticVariation = Degrees(0.0),
                transitionAltitude = Level.AltitudeFeet.unsafe(3000),
                runways = mapOf(
                    runwayId to Runway(
                        id = runwayId,
                        path = Path(listOf(Pts.thrA, Pts.thrB)),
                        threshold = Pts.thrA,
                        exits = emptyList(),
                    ),
                ),
            ),
        ),
    )

    private val worldIndex = WorldIndex(
        positions = positions,
        circuitLegsByPoint = mapOf(
            Pts.thrA to setOf(LegName.UPWIND, LegName.FINAL),
            Pts.upwind to setOf(LegName.UPWIND),
            Pts.crosswind to setOf(LegName.CROSSWIND),
            Pts.downwind to setOf(LegName.DOWNWIND),
            Pts.base to setOf(LegName.BASE),
            Pts.finalPt to setOf(LegName.FINAL),
        ),
    )

    private val circuitMode = NavigationMode.Circuit(runwayId, circuitId)
    private val visualMode = NavigationMode.Visual(runwayId, AerodromeId("DEST"))
    private val visualLocalMode = NavigationMode.Visual(runwayId, destination = null)

    private val ifrFpl = FlightPlan(
        departureAerodrome = AerodromeId("TEST"),
        arrivalAerodrome = AerodromeId("DEST"),
        requestedLevel = Level.FlightLevel.unsafe(100),
        enRouteWaypoints = listOf(FixId("WP1")),
        clearance = ClearanceState.EnRouteClearance(
            clearanceLimit = FixId("WP1"),
            departureRunway = runwayId,
            sid = sidId,
            star = starId,
        ),
    )
    private val instrumentMode = NavigationMode.Instrument(fpl = ifrFpl)

    /** IFR FPL with approach clearance — for STAR+approach tests. */
    private val ifrApproachFpl = FlightPlan(
        departureAerodrome = AerodromeId("TEST"),
        arrivalAerodrome = AerodromeId("TEST"),
        requestedLevel = Level.FlightLevel.unsafe(100),
        enRouteWaypoints = emptyList(),
        clearance = ClearanceState.ApproachClearance(
            clearanceLimit = FixId("WP1"),
            departureRunway = runwayId,
            sid = sidId,
            star = starId,
            approachType = ApproachType.ILS,
            arrivalRunway = runwayId,
        ),
    )
    private val instrumentApproachMode = NavigationMode.Instrument(fpl = ifrApproachFpl)

    // ── Layer 1: Unit tests for implemented cells ───────────────────

    @Test
    fun `Circuit × Circuit produces full circuit route`() {
        val route = assertRoute(circuitMode, TaskName.Circuit)

        assertEquals(Pts.thrB, route.waypoints.head, "Route should start at departure end")
        assertEquals(Pts.thrA, route.waypoints.last(), "Route should end at threshold")
        assertWaypointsInPositions(route)
        assertWaypointsInCircuitLegs(route, skipFirst = true) // departure end not in circuit legs
        assertMonotonicLegOrder(route.waypoints)
        assertEquals(PilotPhase.LandingRoll, route.arrivalPhase)
        assertEquals(CIRCUIT_ALTITUDE_M, route.targetAltitudeM)
        assertTrue(route.waypoints.size >= 3, "Circuit route must have ≥ 3 waypoints")
    }

    @Test
    fun `Circuit × CircuitAfterGoAround produces full circuit route`() {
        val route = assertRoute(circuitMode, TaskName.CircuitAfterGoAround)
        assertEquals(Pts.thrB, route.waypoints.head)
        assertEquals(Pts.thrA, route.waypoints.last())
        assertEquals(PilotPhase.LandingRoll, route.arrivalPhase)
    }

    @Test
    fun `Circuit × TouchAndGo produces full circuit route`() {
        val route = assertRoute(circuitMode, TaskName.TouchAndGo)
        assertEquals(PilotPhase.LandingRoll, route.arrivalPhase)
    }

    @Test
    fun `Visual × Depart produces departure route through upwind and crosswind`() {
        val route = assertRoute(visualMode, TaskName.Depart)

        assertEquals(Pts.thrB, route.waypoints.head, "Route should start at departure end")
        assertWaypointsInPositions(route)
        // Departure route should NOT include threshold (pilot shouldn't route back).
        assertTrue(Pts.thrA !in route.waypoints, "Departure route must not include threshold")
        assertMonotonicLegOrder(route.waypoints)
        assertEquals(PilotPhase.Crosswind, route.arrivalPhase)
        assertEquals(CIRCUIT_ALTITUDE_M, route.targetAltitudeM)
        assertTrue(route.waypoints.size >= 2, "Departure route must have ≥ 2 waypoints")
    }

    @Test
    fun `Visual × Transit produces departure route`() {
        val route = assertRoute(visualMode, TaskName.Transit)
        assertEquals(PilotPhase.Crosswind, route.arrivalPhase)
    }

    @Test
    fun `Visual × Circuit produces arrival circuit route from downwind`() {
        val route = assertRoute(visualMode, TaskName.Circuit)

        assertEquals(Pts.downwind, route.waypoints.head, "Arrival circuit should start at downwind")
        assertEquals(Pts.thrA, route.waypoints.last(), "Arrival circuit should end at threshold")
        assertWaypointsInPositions(route)
        assertWaypointsInCircuitLegs(route)
        assertMonotonicLegOrder(route.waypoints)
        assertEquals(PilotPhase.LandingRoll, route.arrivalPhase)
        assertEquals(CIRCUIT_ALTITUDE_M, route.targetAltitudeM)
        assertTrue(route.waypoints.size >= 4, "Arrival circuit must have ≥ 4 waypoints")
    }

    @Test
    fun `Visual × CircuitAfterGoAround produces arrival circuit route`() {
        val route = assertRoute(visualMode, TaskName.CircuitAfterGoAround)
        assertEquals(PilotPhase.LandingRoll, route.arrivalPhase)
        assertEquals(Pts.thrA, route.waypoints.last())
    }

    @Test
    fun `Circuit × GoAround produces go-around route through full circuit`() {
        val route = assertRoute(circuitMode, TaskName.GoAround)

        assertEquals(Pts.upwind, route.waypoints.head, "Go-around should start at upwind")
        assertEquals(Pts.thrA, route.waypoints.last(), "Go-around should end at threshold")
        assertWaypointsInPositions(route)
        assertMonotonicLegOrder(route.waypoints)
        assertEquals(PilotPhase.LandingRoll, route.arrivalPhase)
        assertEquals(CIRCUIT_ALTITUDE_M, route.targetAltitudeM)
        assertTrue(route.waypoints.size >= 5, "Go-around route must traverse the circuit")
    }

    @Test
    fun `Visual × GoAround produces go-around route through circuit`() {
        val route = assertRoute(visualMode, TaskName.GoAround)

        assertEquals(Pts.upwind, route.waypoints.head, "Go-around should start at upwind")
        assertEquals(Pts.thrA, route.waypoints.last(), "Go-around should end at threshold")
        assertEquals(PilotPhase.LandingRoll, route.arrivalPhase)
    }

    // ── Visual mode join leg (A12) ───────────────────────────────────

    @Test
    fun `buildVisualModeRoute Circuit with joinLeg BASE starts at base`() {
        val result = buildVisualModeRoute(visualMode, TaskName.Circuit, world, worldIndex,
            joinLeg = arrow.core.Some(LegName.BASE))
        val route = result.fold({ fail("Expected Right, got Left($it)") }, { it })

        assertEquals(Pts.base, route.waypoints.head, "Circuit joined at BASE must start at base waypoint")
        assertEquals(Pts.thrA, route.waypoints.last(), "Circuit must end at threshold")
        assertEquals(PilotPhase.LandingRoll, route.arrivalPhase)
    }

    @Test
    fun `buildVisualModeRoute Circuit with joinLeg FINAL starts at final`() {
        val result = buildVisualModeRoute(visualMode, TaskName.Circuit, world, worldIndex,
            joinLeg = arrow.core.Some(LegName.FINAL))
        val route = result.fold({ fail("Expected Right, got Left($it)") }, { it })

        // FINAL leg waypoints: base → finalPt → thrA
        assertTrue(Pts.thrA in route.waypoints.toList(), "Final join must include threshold")
        assertEquals(PilotPhase.LandingRoll, route.arrivalPhase)
    }

    @Test
    fun `buildVisualModeRoute Circuit with None joinLeg defaults to DOWNWIND`() {
        val withNone = buildVisualModeRoute(visualMode, TaskName.Circuit, world, worldIndex,
            joinLeg = arrow.core.None)
        val withDownwind = buildVisualModeRoute(visualMode, TaskName.Circuit, world, worldIndex,
            joinLeg = arrow.core.Some(LegName.DOWNWIND))
        assertEquals(
            withNone.getOrNull()?.waypoints,
            withDownwind.getOrNull()?.waypoints,
            "None joinLeg must produce identical route to explicit DOWNWIND",
        )
    }

    // ── WaypointNotInIndex (A1) ────────────────────────────────────

    @Test
    fun `DefaultPilot returns Left WaypointNotInIndex when taxiing waypoint absent`() {
        val missingPt = PointId("MISSING")
        val route = PilotRoute.Ground(
            waypoints = NonEmptyList(missingPt, emptyList()),
            arrivalPhase = PilotPhase.HoldingShort,
        )
        val ac = AircraftState(
            id = AircraftId("TEST"), callsign = Callsign("TEST"),
            position = xyz.easiersaid.twr.core.world.Position(0.0, 0.0),
            positionPoint = missingPt,
            altitudeM = 0.0, targetAltitudeM = 0.0,
            speedMps = 5.0, targetSpeedMps = 5.0,
            phase = PilotPhase.Taxiing,
            route = route,
            pilotGoal = PilotGoal.DEPART,
        )
        val emptyIndex = WorldIndex(
            positions = emptyMap(),
            circuitLegsByPoint = emptyMap(),
        )
        val result = DefaultPilot.decide(PilotView(SimTime.ofSeconds(0), ac, emptyIndex))
        assertTrue(result.isLeft(), "Should return Left for missing waypoint")
        val err = result.leftOrNull()
        assertTrue(err is RoutingError.WaypointNotInIndex,
            "Error should be WaypointNotInIndex, got $err")
        assertEquals(missingPt, (err as RoutingError.WaypointNotInIndex).point)
    }

    @Test
    fun `DefaultPilot returns Left WaypointNotInIndex when airborne waypoint absent`() {
        val missingPt = PointId("MISSING-AIR")
        val route = PilotRoute.Airborne(
            waypoints = NonEmptyList(missingPt, emptyList()),
            arrivalPhase = PilotPhase.LandingRoll,
            targetAltitudeM = 300.0,
        )
        val ac = AircraftState(
            id = AircraftId("TEST"), callsign = Callsign("TEST"),
            position = xyz.easiersaid.twr.core.world.Position(0.0, 0.0),
            positionPoint = missingPt,
            altitudeM = 300.0, targetAltitudeM = 300.0,
            speedMps = 50.0, targetSpeedMps = 50.0,
            phase = PilotPhase.Downwind,
            route = route,
            pilotGoal = PilotGoal.ARRIVE,
        )
        val emptyIndex = WorldIndex(
            positions = emptyMap(),
            circuitLegsByPoint = emptyMap(),
        )
        val result = DefaultPilot.decide(PilotView(SimTime.ofSeconds(0), ac, emptyIndex))
        assertTrue(result.isLeft(), "Should return Left for missing airborne waypoint")
        assertTrue(result.leftOrNull() is RoutingError.WaypointNotInIndex,
            "Error should be WaypointNotInIndex, got ${result.leftOrNull()}")
    }

    // ── SID departure (IFR-2) ─────────────────────────────────────

    @Test
    fun `Instrument × Depart with SID produces SID departure route`() {
        val route = assertRoute(instrumentMode, TaskName.Depart)

        // Starts at departure end (thrB), then SID waypoints.
        assertEquals(Pts.thrB, route.waypoints.head, "SID route should start at departure end")
        assertTrue(Pts.sidWp1 in route.waypoints, "SID route should include SID waypoint 1")
        assertTrue(Pts.sidWp2 in route.waypoints, "SID route should include SID waypoint 2")

        // SID waypoints in published order.
        val wp1Idx = route.waypoints.indexOf(Pts.sidWp1)
        val wp2Idx = route.waypoints.indexOf(Pts.sidWp2)
        assertTrue(wp1Idx < wp2Idx, "SID waypoints should be in published order")

        // Waypoints exist in positions.
        assertWaypointsInPositions(route)

        // Altitude constraints populated from SID.
        assertTrue(route.waypointConstraints.isNotEmpty(), "SID route should have constraints")
        assertTrue(Pts.sidWp1 in route.waypointConstraints, "SID WP1 should have altitude constraint")
        assertTrue(route.waypointConstraints[Pts.sidWp1]?.altitude is AltC.AtOrAbove)

        // Terminal properties.
        assertEquals(PilotPhase.Climbing, route.arrivalPhase)
        assertTrue(route.targetAltitudeM > CIRCUIT_ALTITUDE_M, "SID target altitude should exceed circuit altitude")
    }

    @Test
    fun `Instrument × Transit with SID produces same SID route`() {
        val route = assertRoute(instrumentMode, TaskName.Transit)
        assertEquals(Pts.thrB, route.waypoints.head)
        assertEquals(PilotPhase.Climbing, route.arrivalPhase)
    }

    @Test
    fun `Instrument × Depart without SID falls back to visual departure`() {
        val fplNoSid = ifrFpl.copy(
            clearance = ClearanceState.EnRouteClearance(
                clearanceLimit = FixId("WP1"),
                departureRunway = runwayId,
                sid = null,
            ),
        )
        val mode = NavigationMode.Instrument(fpl = fplNoSid)
        val route = assertRoute(mode, TaskName.Depart)

        // Falls back to visual departure — ends at crosswind, not SID exit.
        assertEquals(PilotPhase.Crosswind, route.arrivalPhase)
        assertTrue(route.waypointConstraints.isEmpty(), "Visual fallback should have no constraints")
    }

    @Test
    fun `Instrument × Depart without clearance returns error`() {
        val fplUncleaned = ifrFpl.copy(clearance = ClearanceState.Uncleaned)
        val mode = NavigationMode.Instrument(fpl = fplUncleaned)
        val result = buildAirborneRoute(mode, TaskName.Depart, world, worldIndex)
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull() is RoutingError.InsufficientGeometry)
    }

    @Test
    fun `Instrument × Depart with unknown SID returns ProcedureNotFound`() {
        val fplBadSid = ifrFpl.copy(
            clearance = ClearanceState.EnRouteClearance(
                clearanceLimit = FixId("WP1"),
                departureRunway = runwayId,
                sid = SidId("NONEXISTENT"),
            ),
        )
        val mode = NavigationMode.Instrument(fpl = fplBadSid)
        val result = buildAirborneRoute(mode, TaskName.Depart, world, worldIndex)
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull() is RoutingError.ProcedureNotFound)
    }

    @Test
    fun `SID waypoints are a prefix of the departure route`() {
        val route = assertRoute(instrumentMode, TaskName.Depart)
        // After the departure end, the next waypoints should be the SID waypoints.
        val afterDep = route.waypoints.drop(1) // skip departure end
        val sidPoints = listOf(Pts.sidWp1, Pts.sidWp2)
        for ((i, sidPt) in sidPoints.withIndex()) {
            assertEquals(sidPt, afterDep[i], "SID waypoint $i should be at position $i after departure end")
        }
    }

    // ── STAR + approach (IFR-3) ────────────────────────────────────

    @Test
    fun `Instrument × Arrive with STAR + approach produces combined route`() {
        val route = assertRoute(instrumentApproachMode, TaskName.Arrive)

        // Includes STAR entry, IAF, FAF, threshold.
        assertTrue(Pts.starEntry in route.waypoints, "Should include STAR entry")
        assertTrue(Pts.iaf in route.waypoints, "Should include IAF")
        assertTrue(Pts.faf in route.waypoints, "Should include FAF")
        assertEquals(Pts.thrA, route.waypoints.last(), "Should end at threshold")

        // STAR waypoints before approach waypoints (published order).
        val starIdx = route.waypoints.indexOf(Pts.starEntry)
        val iafIdx = route.waypoints.indexOf(Pts.iaf)
        val fafIdx = route.waypoints.indexOf(Pts.faf)
        assertTrue(starIdx < iafIdx, "STAR entry should be before IAF")
        assertTrue(iafIdx < fafIdx, "IAF should be before FAF")

        // Constraints populated.
        assertTrue(route.waypointConstraints.isNotEmpty(), "Should have constraints")
        assertTrue(Pts.starEntry in route.waypointConstraints, "STAR entry should have constraint")
        assertTrue(Pts.faf in route.waypointConstraints, "FAF should have constraint")

        // Approach constraints override STAR for shared waypoints (IAF).
        val iafConstraint = route.waypointConstraints[Pts.iaf]
        assertNotNull(iafConstraint, "IAF should have constraint (from approach, overriding STAR)")

        // Terminal properties.
        assertEquals(PilotPhase.LandingRoll, route.arrivalPhase)
        // Target altitude is derived from first STAR waypoint constraint (6000ft AtOrBelow).
        assertTrue(route.targetAltitudeM > 0.0, "Target altitude should come from STAR constraints, not zero")

        assertWaypointsInPositions(route)
    }

    @Test
    fun `Instrument × Arrive without approach clearance returns error`() {
        // instrumentMode has EnRouteClearance, not ApproachClearance
        val result = buildAirborneRoute(instrumentMode, TaskName.Arrive, world, worldIndex)
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull() is RoutingError.InsufficientGeometry)
    }

    @Test
    fun `Instrument × Arrive with unknown STAR returns ProcedureNotFound`() {
        val badFpl = ifrApproachFpl.copy(
            clearance = (ifrApproachFpl.clearance as ClearanceState.ApproachClearance).copy(
                star = StarId("NONEXISTENT"),
            ),
        )
        val result = buildAirborneRoute(NavigationMode.Instrument(badFpl), TaskName.Arrive, world, worldIndex)
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull() is RoutingError.ProcedureNotFound)
    }

    @Test
    fun `Instrument × ArrivalJoin routes to first STAR waypoint`() {
        val route = assertRoute(instrumentApproachMode, TaskName.ArrivalJoin)
        assertEquals(Pts.starEntry, route.waypoints.head, "Should route to STAR entry")
    }

    @Test
    fun `STAR waypoints appear in published order`() {
        val route = assertRoute(instrumentApproachMode, TaskName.Arrive)
        val starEntry = route.waypoints.indexOf(Pts.starEntry)
        val iaf = route.waypoints.indexOf(Pts.iaf)
        assertTrue(starEntry >= 0 && iaf >= 0, "Both STAR waypoints should be in route")
        assertTrue(starEntry < iaf, "STAR waypoints must be in published order")
    }

    @Test
    fun `no waypoint appears twice in IFR arrival route`() {
        val route = assertRoute(instrumentApproachMode, TaskName.Arrive)
        val wpList = route.waypoints.toList()
        assertEquals(wpList.size, wpList.distinct().size, "No duplicate waypoints in IFR route")
    }

    // ── Missed approach (IFR-4) ────────────────────────────────────

    @Test
    fun `Instrument × GoAround produces missed approach route`() {
        val route = assertRoute(instrumentApproachMode, TaskName.GoAround)

        // Route follows missed approach waypoints.
        assertTrue(Pts.mapFix in route.waypoints, "Should include MAP fix")
        assertTrue(Pts.sidWp1 in route.waypoints, "Should include second missed approach waypoint")

        // MAP fix before second waypoint (published order).
        val mapIdx = route.waypoints.indexOf(Pts.mapFix)
        val wp2Idx = route.waypoints.indexOf(Pts.sidWp1)
        assertTrue(mapIdx < wp2Idx, "Missed approach waypoints should be in published order")

        // Terminal properties: climbing, at pattern altitude.
        assertEquals(PilotPhase.Climbing, route.arrivalPhase)

        assertWaypointsInPositions(route)
    }

    @Test
    fun `missed approach terminates at published hold fix`() {
        val route = assertRoute(instrumentApproachMode, TaskName.GoAround)
        // Last waypoint should be the last missed approach waypoint.
        assertEquals(Pts.sidWp1, route.waypoints.last(),
            "Missed approach should terminate at the last published waypoint")
    }

    @Test
    fun `Instrument × GoAround without approach clearance returns error`() {
        val result = buildAirborneRoute(instrumentMode, TaskName.GoAround, world, worldIndex)
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull() is RoutingError.InsufficientGeometry)
    }

    // ── Approach-only arrival (no STAR) ─────────────────────────────

    @Test
    fun `Instrument × Arrive without STAR routes directly to approach`() {
        val fplNoStar = ifrApproachFpl.copy(
            clearance = (ifrApproachFpl.clearance as ClearanceState.ApproachClearance).copy(star = null),
        )
        val mode = NavigationMode.Instrument(fpl = fplNoStar)
        val route = assertRoute(mode, TaskName.Arrive)

        // Should start at IAF (first approach waypoint), not STAR entry.
        assertEquals(Pts.iaf, route.waypoints.head, "Should start at IAF when no STAR")
        assertEquals(Pts.thrA, route.waypoints.last(), "Should end at threshold")
    }

    // ── STAR-to-approach transition gap ────────────────────────────

    @Test
    fun `STAR ending at different fix than approach IAF includes both waypoints`() {
        // Create a world where the STAR ends at starEntry (not at IAF).
        // The approach starts at IAF. The route should include both starEntry
        // and IAF, with the pilot flying directly between them.
        val gapStarId = StarId("STAR-GAP")
        val worldWithGap = AviationWorld(
            aerodromes = mapOf(
                AerodromeId("TEST") to Aerodrome(
                    icao = AerodromeId("TEST"),
                    elevation = Feet(0),
                    magneticVariation = Degrees(0.0),
                    transitionAltitude = Level.AltitudeFeet.unsafe(3000),
                    runways = mapOf(
                        runwayId to Runway(
                            id = runwayId,
                            path = Path(listOf(Pts.thrA, Pts.thrB)),
                            threshold = Pts.thrA,
                            exits = emptyList(),
                        ),
                    ),
                    stars = mapOf(
                        gapStarId to Star(
                            id = gapStarId, name = "GAP STAR",
                            // STAR ends at starEntry, NOT at IAF — there is a gap.
                            waypoints = listOf(Waypoint(Pts.starEntry)),
                        ),
                    ),
                    approaches = mapOf(
                        approachId to InstrumentApproach(
                            id = approachId, name = "ILS 09", type = ApproachType.ILS,
                            runway = runwayId,
                            // Approach starts at IAF, not starEntry.
                            waypoints = listOf(Waypoint(Pts.iaf), Waypoint(Pts.faf), Waypoint(Pts.thrA)),
                            minimumAltitude = ApproachMinimum(MinimumType.DECISION_ALTITUDE, Level.AltitudeFeet.unsafe(200)),
                            missedApproach = MissedApproachProcedure(
                                waypoints = listOf(Waypoint(Pts.mapFix)),
                                holdAt = holdId,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val gapFpl = FlightPlan(
            departureAerodrome = AerodromeId("TEST"),
            arrivalAerodrome = AerodromeId("TEST"),
            requestedLevel = Level.FlightLevel.unsafe(100),
            enRouteWaypoints = emptyList(),
            clearance = ClearanceState.ApproachClearance(
                clearanceLimit = FixId("WP1"),
                departureRunway = runwayId,
                star = gapStarId,
                approachType = ApproachType.ILS,
                arrivalRunway = runwayId,
            ),
        )
        val gapMode = NavigationMode.Instrument(fpl = gapFpl)
        val result = buildAirborneRoute(gapMode, TaskName.Arrive, worldWithGap, worldIndex)
        val route = result.fold({ fail("Expected Right, got $it") }, { it })

        // Both starEntry and IAF should be in the route (gap bridged by direct segment).
        assertTrue(Pts.starEntry in route.waypoints, "Route should include STAR terminus")
        assertTrue(Pts.iaf in route.waypoints, "Route should include approach IAF")
        val starIdx = route.waypoints.indexOf(Pts.starEntry)
        val iafIdx = route.waypoints.indexOf(Pts.iaf)
        assertTrue(starIdx < iafIdx, "STAR terminus should come before approach IAF")
        assertEquals(Pts.thrA, route.waypoints.last(), "Route should end at threshold")
    }

    // ── VFR constraint invariant ───────────────────────────────────

    @Test
    fun `all VFR routes have empty waypointConstraints`() {
        val vfrCells = listOf(
            circuitMode to TaskName.Circuit,
            circuitMode to TaskName.GoAround,
            visualMode to TaskName.Depart,
            visualMode to TaskName.Circuit,
            visualMode to TaskName.GoAround,
        )
        for ((mode, task) in vfrCells) {
            val route = assertRoute(mode, task)
            assertTrue(route.waypointConstraints.isEmpty(),
                "${mode::class.simpleName}×${task::class.simpleName} should have empty waypointConstraints")
        }
    }

    // ── buildCircuitFromLeg with different start legs ───────────────

    @Test
    fun `buildCircuitFromLeg BASE produces base through final to threshold`() {
        val route = buildCircuitFromLeg(runwayId, LegName.BASE, world, worldIndex)
            .fold({ fail("Expected Right, got Left($it)") }, { it })
        assertEquals(Pts.base, route.waypoints.head, "BASE join starts at base point")
        assertEquals(Pts.thrA, route.waypoints.last())
        assertEquals(PilotPhase.LandingRoll, route.arrivalPhase)
    }

    @Test
    fun `buildCircuitFromLeg FINAL produces final to threshold`() {
        val route = buildCircuitFromLeg(runwayId, LegName.FINAL, world, worldIndex)
            .fold({ fail("Expected Right, got Left($it)") }, { it })
        assertEquals(Pts.finalPt, route.waypoints.head, "FINAL join starts at final point")
        assertEquals(Pts.thrA, route.waypoints.last())
    }

    @Test
    fun `buildCircuitFromLeg UPWIND produces full circuit`() {
        val route = buildCircuitFromLeg(runwayId, LegName.UPWIND, world, worldIndex)
            .fold({ fail("Expected Right, got Left($it)") }, { it })
        assertEquals(Pts.thrA, route.waypoints.last())
        assertMonotonicLegOrder(route.waypoints)
        assertTrue(route.waypoints.size >= 5, "Full circuit from UPWIND must traverse all legs")
    }

    // ── Layer 2: Truth table ────────────────────────────────────────

    private enum class Expected { RIGHT, INVALID, NYI, LEFT_OTHER }

    @Test
    fun `truth table — every mode x taskName is explicitly handled`() {
        data class Cell(val mode: NavigationMode, val task: TaskName, val expected: Expected)

        val cells = listOf(
            // ── Circuit mode ──
            Cell(circuitMode, TaskName.Circuit,              Expected.RIGHT),
            Cell(circuitMode, TaskName.CircuitAfterGoAround, Expected.RIGHT),
            Cell(circuitMode, TaskName.TouchAndGo,           Expected.RIGHT),
            Cell(circuitMode, TaskName.GoAround,             Expected.RIGHT),
            Cell(circuitMode, TaskName.GroundDeparture,      Expected.INVALID),
            Cell(circuitMode, TaskName.GroundArrival,        Expected.INVALID),
            Cell(circuitMode, TaskName.Depart,               Expected.INVALID),
            Cell(circuitMode, TaskName.Arrive,               Expected.INVALID),
            Cell(circuitMode, TaskName.Transit,              Expected.INVALID),
            Cell(circuitMode, TaskName.CircuitTraining,      Expected.INVALID),
            Cell(circuitMode, TaskName.ArrivalJoin,          Expected.INVALID),

            // ── Visual mode (with destination) ──
            Cell(visualMode, TaskName.Depart,               Expected.RIGHT),
            Cell(visualMode, TaskName.Transit,              Expected.RIGHT),
            Cell(visualMode, TaskName.Circuit,              Expected.RIGHT),
            Cell(visualMode, TaskName.CircuitAfterGoAround, Expected.RIGHT),
            Cell(visualMode, TaskName.TouchAndGo,           Expected.RIGHT),
            Cell(visualMode, TaskName.ArrivalJoin,          Expected.NYI),
            Cell(visualMode, TaskName.Arrive,               Expected.INVALID),
            Cell(visualMode, TaskName.GoAround,             Expected.RIGHT),
            Cell(visualMode, TaskName.GroundDeparture,      Expected.INVALID),
            Cell(visualMode, TaskName.GroundArrival,        Expected.INVALID),
            Cell(visualMode, TaskName.CircuitTraining,      Expected.INVALID),

            // ── Visual mode (local, no destination) ──
            Cell(visualLocalMode, TaskName.Depart,               Expected.RIGHT),
            Cell(visualLocalMode, TaskName.Transit,              Expected.RIGHT),
            Cell(visualLocalMode, TaskName.Circuit,              Expected.RIGHT),
            Cell(visualLocalMode, TaskName.CircuitAfterGoAround, Expected.RIGHT),
            Cell(visualLocalMode, TaskName.TouchAndGo,           Expected.RIGHT),
            Cell(visualLocalMode, TaskName.ArrivalJoin,          Expected.NYI),
            Cell(visualLocalMode, TaskName.Arrive,               Expected.INVALID),
            Cell(visualLocalMode, TaskName.GoAround,             Expected.RIGHT),
            Cell(visualLocalMode, TaskName.GroundDeparture,      Expected.INVALID),
            Cell(visualLocalMode, TaskName.GroundArrival,        Expected.INVALID),
            Cell(visualLocalMode, TaskName.CircuitTraining,      Expected.INVALID),

            // ── Instrument mode (en-route clearance — departure-capable) ──
            Cell(instrumentMode, TaskName.Depart,               Expected.RIGHT),
            Cell(instrumentMode, TaskName.Transit,              Expected.RIGHT),
            Cell(instrumentMode, TaskName.ArrivalJoin,          Expected.RIGHT),
            // GoAround needs ApproachClearance; tested via instrumentApproachMode.
            // instrumentMode returns InsufficientGeometry (same as Arrive).
            Cell(instrumentMode, TaskName.Circuit,              Expected.INVALID),
            Cell(instrumentMode, TaskName.CircuitAfterGoAround, Expected.INVALID),
            Cell(instrumentMode, TaskName.TouchAndGo,           Expected.INVALID),
            Cell(instrumentMode, TaskName.GroundDeparture,      Expected.INVALID),
            Cell(instrumentMode, TaskName.GroundArrival,        Expected.INVALID),
            Cell(instrumentMode, TaskName.CircuitTraining,      Expected.INVALID),
            // Arrive and GoAround need ApproachClearance — instrumentMode has EnRouteClearance,
            // so these return Left(InsufficientGeometry), not NYI or InvalidCombination.
            Cell(instrumentMode, TaskName.Arrive,               Expected.LEFT_OTHER),
            Cell(instrumentMode, TaskName.GoAround,             Expected.LEFT_OTHER),

            // ── Instrument mode (approach clearance — arrival-capable) ──
            Cell(instrumentApproachMode, TaskName.Depart,               Expected.RIGHT),
            Cell(instrumentApproachMode, TaskName.Transit,              Expected.RIGHT),
            Cell(instrumentApproachMode, TaskName.Arrive,               Expected.RIGHT),
            Cell(instrumentApproachMode, TaskName.ArrivalJoin,          Expected.RIGHT),
            Cell(instrumentApproachMode, TaskName.GoAround,             Expected.RIGHT),
            Cell(instrumentApproachMode, TaskName.Circuit,              Expected.INVALID),
            Cell(instrumentApproachMode, TaskName.CircuitAfterGoAround, Expected.INVALID),
            Cell(instrumentApproachMode, TaskName.TouchAndGo,           Expected.INVALID),
            Cell(instrumentApproachMode, TaskName.GroundDeparture,      Expected.INVALID),
            Cell(instrumentApproachMode, TaskName.GroundArrival,        Expected.INVALID),
            Cell(instrumentApproachMode, TaskName.CircuitTraining,      Expected.INVALID),
        )

        for ((mode, task, expected) in cells) {
            val result = buildAirborneRoute(mode, task, world, worldIndex)
            val label = "${mode::class.simpleName} × ${task::class.simpleName}"
            when (expected) {
                Expected.RIGHT -> assertTrue(result.isRight(), "$label should produce a route, got ${result.leftOrNull()}")
                Expected.INVALID -> {
                    assertTrue(result.isLeft(), "$label should be InvalidCombination")
                    assertTrue(result.leftOrNull() is RoutingError.InvalidCombination,
                        "$label should be InvalidCombination, got ${result.leftOrNull()}")
                }
                Expected.NYI -> {
                    assertTrue(result.isLeft(), "$label should be NotYetImplemented")
                    assertTrue(result.leftOrNull() is RoutingError.NotYetImplemented,
                        "$label should be NotYetImplemented, got ${result.leftOrNull()}")
                }
                Expected.LEFT_OTHER -> {
                    assertTrue(result.isLeft(), "$label should be Left (clearance-state error)")
                }
            }
        }
    }

    // ── Layer 3: Unimplemented modes — per-cell error type ──────────

    @Test
    fun `Instrument mode — correct error type per taskName`() {
        val mode = instrumentMode
        val right = setOf(TaskName.Depart, TaskName.Transit, TaskName.ArrivalJoin)
        val nyi = emptySet<TaskName>() // all NYI cells now implemented
        // Arrive and GoAround return InsufficientGeometry (need ApproachClearance, tested via instrumentApproachMode).
        val leftButNotNyiOrInvalid = setOf(TaskName.Arrive, TaskName.GoAround)
        for (task in allTaskNames()) {
            val result = buildAirborneRoute(mode, task, world, worldIndex)
            val label = "Instrument × ${task::class.simpleName}"
            when {
                task in right -> assertTrue(result.isRight(), "$label should be Right, got ${result.leftOrNull()}")
                task in nyi -> {
                    assertTrue(result.isLeft(), "$label should be NYI")
                    assertTrue(result.leftOrNull() is RoutingError.NotYetImplemented, "$label got ${result.leftOrNull()}")
                }
                task in leftButNotNyiOrInvalid -> {
                    // Implemented but returns error due to clearance state mismatch.
                    assertTrue(result.isLeft(), "$label should be Left (clearance-state error)")
                }
                else -> {
                    assertTrue(result.isLeft(), "$label should be Invalid")
                    assertTrue(result.leftOrNull() is RoutingError.InvalidCombination, "$label got ${result.leftOrNull()}")
                }
            }
        }
    }

    @Test
    fun `Emergency mode — correct error type per taskName`() {
        val mode = NavigationMode.Emergency(targetRunway = runwayId)
        val nyi = setOf(
            TaskName.Depart, TaskName.Transit, TaskName.Arrive, TaskName.ArrivalJoin,
            TaskName.Circuit, TaskName.CircuitAfterGoAround, TaskName.TouchAndGo, TaskName.GoAround,
        )
        for (task in allTaskNames()) {
            val result = buildAirborneRoute(mode, task, world, worldIndex)
            assertTrue(result.isLeft(), "Emergency × ${task::class.simpleName} should be Left")
            if (task in nyi) {
                assertTrue(result.leftOrNull() is RoutingError.NotYetImplemented,
                    "Emergency × ${task::class.simpleName} should be NYI, got ${result.leftOrNull()}")
            } else {
                assertTrue(result.leftOrNull() is RoutingError.InvalidCombination,
                    "Emergency × ${task::class.simpleName} should be Invalid, got ${result.leftOrNull()}")
            }
        }
    }

    // ── Layer 4: Error path tests ───────────────────────────────────

    @Test
    fun `Circuit mode with unknown circuit ID returns ProcedureNotFound`() {
        // ById semantics: circuit ID is authoritative; unknown ID → ProcedureNotFound.
        val badMode = NavigationMode.Circuit(runwayId, CircuitProcedureId("NONEXISTENT"))
        val result = buildAirborneRoute(badMode, TaskName.Circuit, world, worldIndex)
        assertTrue(result.leftOrNull() is RoutingError.ProcedureNotFound)
    }

    @Test
    fun `Circuit mode ById lookup uses circuit-declared runway not mode runway field`() {
        // Mode.runway = "99" (nonexistent), but circuitId maps to runway "09" in the world.
        // ById lookup: procedure ID is authoritative → finds runway "09" → route succeeds.
        val modeWithMismatch = NavigationMode.Circuit(RunwayId("99"), circuitId)
        val result = buildAirborneRoute(modeWithMismatch, TaskName.Circuit, world, worldIndex)
        assertTrue(result.isRight(),
            "ById lookup should succeed using the circuit's declared runway, not the mode's runway field")
    }

    @Test
    fun `Visual departure with unknown runway returns RunwayNotFound`() {
        val badMode = NavigationMode.Visual(RunwayId("99"), AerodromeId("DEST"))
        val result = buildAirborneRoute(badMode, TaskName.Depart, world, worldIndex)
        assertTrue(result.leftOrNull() is RoutingError.RunwayNotFound)
    }

    @Test
    fun `Circuit mode go-around with unknown circuit ID returns ProcedureNotFound`() {
        val badMode = NavigationMode.Circuit(runwayId, CircuitProcedureId("NONEXISTENT"))
        val result = buildAirborneRoute(badMode, TaskName.GoAround, world, worldIndex)
        assertTrue(result.leftOrNull() is RoutingError.ProcedureNotFound)
    }

    @Test
    fun `Circuit mode in worldNoCircuit returns ProcedureNotFound`() {
        // ById lookup: circuitId not found in worldNoCircuit → ProcedureNotFound.
        val result = buildAirborneRoute(circuitMode, TaskName.Circuit, worldNoCircuit, worldIndex)
        assertTrue(result.leftOrNull() is RoutingError.ProcedureNotFound)
    }

    @Test
    fun `Visual departure with no circuit procedure returns CircuitNotFound`() {
        // Visual departures use ByRunway lookup — no circuit for runway → CircuitNotFound.
        val result = buildAirborneRoute(visualMode, TaskName.Depart, worldNoCircuit, worldIndex)
        assertTrue(result.leftOrNull() is RoutingError.CircuitNotFound)
    }

    @Test
    fun `Circuit mode go-around in worldNoCircuit returns ProcedureNotFound`() {
        val result = buildAirborneRoute(circuitMode, TaskName.GoAround, worldNoCircuit, worldIndex)
        assertTrue(result.leftOrNull() is RoutingError.ProcedureNotFound)
    }

    // ── deriveNavigationMode ────────────────────────────────────────

    @Test
    fun `deriveNavigationMode — CircuitTraining returns Circuit mode`() {
        val result = deriveNavigationMode(HighLevelGoal.CircuitTraining(2), runwayId, world)
        val mode = result.fold({ fail("Expected Right, got Left($it)") }, { it })
        assertTrue(mode is NavigationMode.Circuit, "Should derive Circuit mode")
        assertEquals(runwayId, mode.runway)
        assertEquals(circuitId, mode.procedure)
    }

    @Test
    fun `deriveNavigationMode — Departure returns Visual mode with destination`() {
        val dest = AerodromeId("LJLJ")
        val result = deriveNavigationMode(HighLevelGoal.Departure(dest), runwayId, world)
        val mode = result.fold({ fail("Expected Right") }, { it })
        assertTrue(mode is NavigationMode.Visual)
        assertEquals(dest, mode.destination)
    }

    @Test
    fun `deriveNavigationMode — Arrival returns Visual mode without destination`() {
        val result = deriveNavigationMode(HighLevelGoal.Arrival(), runwayId, world)
        val mode = result.fold({ fail("Expected Right") }, { it })
        assertTrue(mode is NavigationMode.Visual)
        assertEquals(null, mode.destination)
    }

    @Test
    fun `deriveNavigationMode — Transit returns Visual mode with destination`() {
        val dest = AerodromeId("LJLJ")
        val result = deriveNavigationMode(HighLevelGoal.Transit(dest), runwayId, world)
        val mode = result.fold({ fail("Expected Right") }, { it })
        assertTrue(mode is NavigationMode.Visual)
        assertEquals(dest, mode.destination)
    }

    @Test
    fun `deriveNavigationMode — CircuitTraining with no circuit returns CircuitNotFound`() {
        val result = deriveNavigationMode(HighLevelGoal.CircuitTraining(1), runwayId, worldNoCircuit)
        assertTrue(result.isLeft(), "Should return Left when no circuit exists")
        assertTrue(result.leftOrNull() is RoutingError.CircuitNotFound)
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private val legOrder = listOf(LegName.UPWIND, LegName.CROSSWIND, LegName.DOWNWIND, LegName.BASE, LegName.FINAL)

    /** Assert buildAirborneRoute returns Right, extract the route. */
    private fun assertRoute(mode: NavigationMode, taskName: TaskName): PilotRoute.Airborne =
        buildAirborneRoute(mode, taskName, world, worldIndex)
            .fold({ fail("Expected Right for ${mode::class.simpleName}×${taskName::class.simpleName}, got Left($it)") }, { it })

    /** Assert every waypoint exists in positions. */
    private fun assertWaypointsInPositions(route: PilotRoute.Airborne) {
        for (wp in route.waypoints) {
            assertTrue(wp in positions, "Waypoint $wp must exist in worldIndex.positions")
        }
    }

    /** Assert every waypoint (optionally skipping first) exists in circuitLegsByPoint. */
    private fun assertWaypointsInCircuitLegs(route: PilotRoute.Airborne, skipFirst: Boolean = false) {
        val wps = if (skipFirst) route.waypoints.drop(1) else route.waypoints.toList()
        for (wp in wps) {
            assertTrue(wp in worldIndex.circuitLegsByPoint,
                "Waypoint $wp must exist in circuitLegsByPoint (otherwise phase never transitions)")
        }
    }

    /** Assert monotonic leg ordering through the route. */
    private fun assertMonotonicLegOrder(waypoints: NonEmptyList<PointId>) {
        var maxLegIndex = -1
        for (wp in waypoints) {
            val legs = worldIndex.circuitLegsByPoint[wp] ?: continue
            val wpMaxIndex = legs.maxOf { legOrder.indexOf(it) }
            assertTrue(wpMaxIndex >= maxLegIndex,
                "Waypoint $wp (legs=$legs, index=$wpMaxIndex) violates monotonic leg order (previous max=$maxLegIndex)")
            maxLegIndex = wpMaxIndex
        }
    }

    /**
     * All TaskName variants — compiler-enforced via exhaustive when.
     * Adding a new TaskName variant breaks this function at compile time.
     */
    private fun allTaskNames(): List<TaskName> {
        fun check(t: TaskName): TaskName = when (t) {
            is TaskName.Depart -> t
            is TaskName.Arrive -> t
            is TaskName.TouchAndGo -> t
            is TaskName.Transit -> t
            is TaskName.GroundDeparture -> t
            is TaskName.GroundArrival -> t
            is TaskName.Circuit -> t
            is TaskName.CircuitAfterGoAround -> t
            is TaskName.CircuitTraining -> t
            is TaskName.ArrivalJoin -> t
            is TaskName.GoAround -> t
        }
        return listOf(
            TaskName.Depart, TaskName.Arrive, TaskName.TouchAndGo, TaskName.Transit,
            TaskName.GroundDeparture, TaskName.GroundArrival, TaskName.Circuit,
            TaskName.CircuitAfterGoAround, TaskName.CircuitTraining, TaskName.ArrivalJoin,
            TaskName.GoAround,
        ).also { list -> list.forEach { check(it) } }
    }
}
