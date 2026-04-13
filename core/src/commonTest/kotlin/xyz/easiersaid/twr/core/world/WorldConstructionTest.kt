package xyz.easiersaid.twr.core.world

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AirspaceVolumeId
import xyz.easiersaid.twr.protocol.AirwayId
import xyz.easiersaid.twr.protocol.ApproachId
import xyz.easiersaid.twr.protocol.ApproachType
import xyz.easiersaid.twr.protocol.AuthorityEntityType
import xyz.easiersaid.twr.protocol.AuthorityOperation
import xyz.easiersaid.twr.protocol.CircuitDirection
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FirId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.HoldingPatternId
import xyz.easiersaid.twr.protocol.JoinType
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SidId
import xyz.easiersaid.twr.protocol.StandId
import xyz.easiersaid.twr.protocol.StarId
import xyz.easiersaid.twr.protocol.TaxiwayId
import xyz.easiersaid.twr.protocol.VfrRouteId

class WorldConstructionTest {

    @Test
    fun wellFormedFixtureBuildsAndValidates() {
        val world = sampleWorld()

        val report = world.validate()

        assertTrue(report.isValid)
        assertTrue(buildValidatedWorld(world).isRight())
    }

    @Test
    fun buildWorldIndexUsesPhysicalGeometry() {
        val index = sampleWorld().buildWorldIndex()

        assertEquals(
            Position(0.0, 0.0),
            index.positions.getValue(FixtureIds.runway09Threshold)
        )
        assertContains(
            index.adjacency.getValue(FixtureIds.runwayMid),
            FixtureIds.runway09Threshold
        )
        assertContains(
            index.adjacency.getValue(FixtureIds.runwayMid),
            FixtureIds.holdShort09
        )
        assertEquals(
            SurfaceType.RUNWAY,
            index.surfaceBySegment.getValue(
                SegmentId(FixtureIds.runway09Threshold, FixtureIds.runwayMid)
            )
        )
        assertEquals(
            SurfaceType.SKY,
            index.surfaceBySegment.getValue(
                SegmentId(FixtureIds.iafPoint, FixtureIds.fafPoint)
            )
        )
        assertEquals(
            Meters(1000.0),
            index.lengthBySegment.getValue(
                SegmentId(FixtureIds.runway09Threshold, FixtureIds.runwayMid)
            )
        )
        assertEquals(
            Meters(45.0),
            index.widthBySegment.getValue(
                SegmentId(FixtureIds.runway09Threshold, FixtureIds.runwayMid)
            )
        )
        assertContains(
            index.entitiesByPoint.getValue(FixtureIds.runway09Threshold),
            EntityRef.RunwayRef(FixtureIds.runway09)
        )
        assertContains(
            index.entitiesByPoint.getValue(FixtureIds.runway09Threshold),
            EntityRef.ApproachRef(FixtureIds.approach)
        )
    }

    @Test
    fun buildValidatedWorldRejectsMissingHoldingPoint() {
        val invalidWorld = sampleWorldWithMissingHoldingPoint()

        val result = buildValidatedWorld(invalidWorld)

        assertTrue(result.isLeft())
        val report = (result as arrow.core.Either.Left).value
        assertContains(
            report.issues.map { issue -> issue.code },
            WorldValidationCode.MISSING_RUNWAY_HOLDING_POINT
        )
    }

    @Test
    fun validationDetectsBrokenSidAnchor() {
        val report = sampleWorldWithBrokenSidAnchor().validate()

        assertContains(
            report.issues.map { issue -> issue.code },
            WorldValidationCode.SID_NOT_AT_RUNWAY_THRESHOLD
        )
    }

    @Test
    fun validationDetectsTaxiwaySegmentOverlap() {
        val report = sampleWorldWithOverlappingTaxiway().validate()

        assertContains(
            report.issues.map { issue -> issue.code },
            WorldValidationCode.TAXIWAY_SEGMENT_OVERLAP
        )
    }

    @Test
    fun validationDetectsMissingGeometrySegmentReference() {
        val report = sampleWorldWithMissingGeometrySegment().validate()

        assertContains(
            report.issues.map { issue -> issue.code },
            WorldValidationCode.UNKNOWN_GEOMETRY_SEGMENT_REFERENCE
        )
    }
}

internal object FixtureIds {
    val aerodrome = AerodromeId("EGT1")
    val fir = FirId("TEST-FIR")
    val airspace = AirspaceVolumeId("EGT1-CTR")

    val runway09 = RunwayId("09")
    val runway27 = RunwayId("27")
    val taxiwayAlpha = TaxiwayId("A")
    val taxiwayBravo = TaxiwayId("B")
    val standA1 = StandId("A1")
    val apronMain = xyz.easiersaid.twr.protocol.ApronId("MAIN")

    val circuit09 = CircuitProcedureId("09-LH")
    val sid = SidId("SID-09")
    val star = StarId("STAR-09")
    val approach = ApproachId("ILS09")
    val hold = HoldingPatternId("HOLD-IAF")
    val airway = AirwayId("W1")
    val vfrRoute = VfrRouteId("VR-1")

    val controller = ControllerId("CTRL-1")

    val runway09Threshold = PointId("RWY09_THR")
    val runwayMid = PointId("RWY_MID")
    val runway27Threshold = PointId("RWY27_THR")
    val apronJunction = PointId("APRON_JUNCTION")
    val holdShort09 = PointId("HOLD_SHORT_09")
    val holdShort27 = PointId("HOLD_SHORT_27")
    val standPoint = PointId("STAND_A1")

    val upwindEnd = PointId("CIRCUIT_UPWIND_END")
    val crosswindEnd = PointId("CIRCUIT_CROSSWIND_END")
    val downwindEnd = PointId("CIRCUIT_DOWNWIND_END")
    val baseTurn = PointId("CIRCUIT_BASE_TURN")
    val joinEntry = PointId("CIRCUIT_JOIN_ENTRY")
    val goAroundClimb = PointId("GO_AROUND_CLIMB")

    val starEntry = PointId("STAR_ENTRY")
    val iafPoint = PointId("IAF")
    val fafPoint = PointId("FAF")
    val sidExit = PointId("SID_EXIT")
    val holdFixPoint = PointId("HOLD_FIX")
    val holdLoopOne = PointId("HOLD_LOOP_ONE")
    val holdLoopTwo = PointId("HOLD_LOOP_TWO")
    val vfrRoutePoint = PointId("VFR_POINT")
}

internal fun sampleWorld(): AviationWorld {
    val runway09 = Runway(
        id = FixtureIds.runway09,
        path = Path(
            listOf(
                FixtureIds.runway09Threshold,
                FixtureIds.runwayMid,
                FixtureIds.runway27Threshold
            )
        ),
        threshold = FixtureIds.runway09Threshold,
        exits = listOf(RunwayExit(FixtureIds.runwayMid, FixtureIds.taxiwayAlpha))
    )
    val runway27 = Runway(
        id = FixtureIds.runway27,
        path = Path(
            listOf(
                FixtureIds.runway27Threshold,
                FixtureIds.runwayMid,
                FixtureIds.runway09Threshold
            )
        ),
        threshold = FixtureIds.runway27Threshold,
        exits = listOf(RunwayExit(FixtureIds.runwayMid, FixtureIds.taxiwayAlpha))
    )
    val taxiwayAlpha = Taxiway(
        id = FixtureIds.taxiwayAlpha,
        name = "A",
        path = Path(
            listOf(
                FixtureIds.apronJunction,
                FixtureIds.holdShort09,
                FixtureIds.runwayMid,
                FixtureIds.holdShort27
            )
        ),
        holdingPoints = listOf(
            HoldingPoint(
                point = FixtureIds.holdShort09,
                name = "A1",
                type = HoldingPointType.CAT_A,
                runway = FixtureIds.runway09
            ),
            HoldingPoint(
                point = FixtureIds.holdShort27,
                name = "A2",
                type = HoldingPointType.CAT_A,
                runway = FixtureIds.runway27
            )
        )
    )
    val apron = Apron(
        id = FixtureIds.apronMain,
        name = "Main Apron",
        paths = listOf(Path(listOf(FixtureIds.standPoint, FixtureIds.apronJunction))),
        stands = setOf(FixtureIds.standA1)
    )
    val stand = Stand(
        id = FixtureIds.standA1,
        name = "Stand A1",
        point = FixtureIds.standPoint
    )
    val holdingPattern = HoldingPattern(
        id = FixtureIds.hold,
        fix = FixId("HOLD"),
        inboundCourse = Degrees(90.0),
        turnDirection = xyz.easiersaid.twr.protocol.TurnDirection.RIGHT,
        loop = Path(
            listOf(
                FixtureIds.holdFixPoint,
                FixtureIds.holdLoopOne,
                FixtureIds.holdLoopTwo,
                FixtureIds.holdFixPoint
            )
        ),
        legTime = xyz.easiersaid.twr.protocol.Minutes(1),
        maxSpeed = Knots(200),
        altitude = Level.AltitudeFeet(3000),
        stackSeparation = Feet(1000)
    )
    val circuit = CircuitProcedure(
        id = FixtureIds.circuit09,
        runway = FixtureIds.runway09,
        direction = CircuitDirection.LEFT_HAND,
        legs = listOf(
            CircuitLeg(LegName.UPWIND, Path(listOf(FixtureIds.runway09Threshold, FixtureIds.upwindEnd))),
            CircuitLeg(LegName.CROSSWIND, Path(listOf(FixtureIds.upwindEnd, FixtureIds.crosswindEnd))),
            CircuitLeg(LegName.DOWNWIND, Path(listOf(FixtureIds.crosswindEnd, FixtureIds.downwindEnd))),
            CircuitLeg(LegName.BASE, Path(listOf(FixtureIds.downwindEnd, FixtureIds.baseTurn))),
            CircuitLeg(LegName.FINAL, Path(listOf(FixtureIds.baseTurn, FixtureIds.runway09Threshold)))
        ),
        altitude = Level.AltitudeFeet(1800),
        reportingPoints = mapOf(LegName.DOWNWIND to FixtureIds.downwindEnd),
        joinProcedures = listOf(
            CircuitJoin(
                type = JoinType.DOWNWIND,
                entryPoint = FixtureIds.crosswindEnd,
                entryPath = Path(listOf(FixtureIds.joinEntry, FixtureIds.crosswindEnd))
            )
        ),
        goAroundPath = Path(
            listOf(
                FixtureIds.runway09Threshold,
                FixtureIds.goAroundClimb,
                FixtureIds.upwindEnd
            )
        )
    )
    val sid = Sid(
        id = FixtureIds.sid,
        name = "TEST1A",
        runway = FixtureIds.runway09,
        waypoints = listOf(
            Waypoint(FixtureIds.runway09Threshold, "RWY09"),
            Waypoint(
                FixtureIds.sidExit,
                "SIDEXIT",
                altitudeConstraint = AltitudeConstraint.AtOrAbove(Level.AltitudeFeet(2500))
            )
        ),
        transitions = mapOf(
            "HOLD" to listOf(
                Waypoint(FixtureIds.sidExit, "SIDEXIT"),
                Waypoint(FixtureIds.holdFixPoint, "HOLD")
            )
        )
    )
    val star = Star(
        id = FixtureIds.star,
        name = "TEST2B",
        waypoints = listOf(
            Waypoint(FixtureIds.starEntry, "STARIN"),
            Waypoint(FixtureIds.iafPoint, "IAF")
        )
    )
    val approach = InstrumentApproach(
        id = FixtureIds.approach,
        name = "ILS RWY 09",
        type = ApproachType.ILS,
        runway = FixtureIds.runway09,
        waypoints = listOf(
            Waypoint(FixtureIds.iafPoint, "IAF"),
            Waypoint(FixtureIds.fafPoint, "FAF"),
            Waypoint(FixtureIds.runway09Threshold, "RWY09")
        ),
        minimumAltitude = ApproachMinimum(
            type = MinimumType.DECISION_ALTITUDE,
            altitude = Level.AltitudeFeet(1500),
            height = Level.HeightFeet(250)
        ),
        missedApproach = MissedApproachProcedure(
            waypoints = listOf(
                Waypoint(FixtureIds.runway09Threshold, "RWY09"),
                Waypoint(FixtureIds.goAroundClimb, "MA1"),
                Waypoint(FixtureIds.holdFixPoint, "HOLD")
            ),
            holdAt = FixtureIds.hold
        )
    )

    val allPoints = setOf(
        FixtureIds.runway09Threshold,
        FixtureIds.runwayMid,
        FixtureIds.runway27Threshold,
        FixtureIds.apronJunction,
        FixtureIds.holdShort09,
        FixtureIds.holdShort27,
        FixtureIds.standPoint,
        FixtureIds.upwindEnd,
        FixtureIds.crosswindEnd,
        FixtureIds.downwindEnd,
        FixtureIds.baseTurn,
        FixtureIds.joinEntry,
        FixtureIds.goAroundClimb,
        FixtureIds.starEntry,
        FixtureIds.iafPoint,
        FixtureIds.fafPoint,
        FixtureIds.sidExit,
        FixtureIds.holdFixPoint,
        FixtureIds.holdLoopOne,
        FixtureIds.holdLoopTwo,
        FixtureIds.vfrRoutePoint
    )

    val aerodrome = Aerodrome(
        icao = FixtureIds.aerodrome,
        elevation = Feet(420),
        magneticVariation = Degrees(2.0),
        transitionAltitude = Level.AltitudeFeet(5000),
        transitionLevel = Level.FlightLevel(60),
        roles = mapOf(
            RoleName.GROUND to role(
                RoleName.GROUND,
                AuthorityGrant(AuthorityEntityType.TAXIWAY, setOf(AuthorityOperation.TAXI)),
                AuthorityGrant(AuthorityEntityType.RUNWAY, setOf(AuthorityOperation.CROSS)),
                AuthorityGrant(AuthorityEntityType.STAND, setOf(AuthorityOperation.PUSHBACK))
            ),
            RoleName.TOWER to role(
                RoleName.TOWER,
                AuthorityGrant(
                    AuthorityEntityType.RUNWAY,
                    setOf(AuthorityOperation.LINE_UP, AuthorityOperation.TAKEOFF, AuthorityOperation.LAND)
                ),
                AuthorityGrant(
                    AuthorityEntityType.CIRCUIT_PROCEDURE,
                    setOf(AuthorityOperation.CIRCUIT)
                )
            ),
            RoleName.APPROACH to role(
                RoleName.APPROACH,
                AuthorityGrant(
                    AuthorityEntityType.INSTRUMENT_APPROACH,
                    setOf(AuthorityOperation.APPROACH_CLEARANCE, AuthorityOperation.SEQUENCE)
                ),
                AuthorityGrant(
                    AuthorityEntityType.HOLDING_PATTERN,
                    setOf(AuthorityOperation.HOLD)
                ),
                AuthorityGrant(
                    AuthorityEntityType.STAR,
                    setOf(AuthorityOperation.SEQUENCE)
                )
            )
        ),
        controllers = mapOf(
            FixtureIds.controller to setOf(RoleName.GROUND, RoleName.TOWER, RoleName.APPROACH)
        ),
        runways = mapOf(
            FixtureIds.runway09 to runway09,
            FixtureIds.runway27 to runway27
        ),
        taxiways = mapOf(FixtureIds.taxiwayAlpha to taxiwayAlpha),
        stands = mapOf(FixtureIds.standA1 to stand),
        aprons = mapOf(FixtureIds.apronMain to apron),
        circuits = mapOf(FixtureIds.circuit09 to circuit),
        sids = mapOf(FixtureIds.sid to sid),
        stars = mapOf(FixtureIds.star to star),
        approaches = mapOf(FixtureIds.approach to approach),
        holdingPatterns = mapOf(FixtureIds.hold to holdingPattern)
    )

    val holdFix = Fix(
        id = FixId("HOLD"),
        point = FixtureIds.holdFixPoint,
        name = "HOLD",
        type = FixType.WAYPOINT
    )
    val starEntryFix = Fix(
        id = FixId("STARIN"),
        point = FixtureIds.starEntry,
        name = "STARIN",
        type = FixType.WAYPOINT
    )
    val iafFix = Fix(
        id = FixId("IAF"),
        point = FixtureIds.iafPoint,
        name = "IAF",
        type = FixType.WAYPOINT
    )
    val sidExitFix = Fix(
        id = FixId("SIDEXIT"),
        point = FixtureIds.sidExit,
        name = "SIDEXIT",
        type = FixType.WAYPOINT
    )
    val airway = Airway(
        id = FixtureIds.airway,
        name = "W1",
        waypoints = listOf(
            Waypoint(FixtureIds.sidExit, "SIDEXIT"),
            Waypoint(FixtureIds.holdFixPoint, "HOLD")
        ),
        altitudeBand = AltitudeBand(
            lower = AltitudeBoundary.AtLevel(Level.AltitudeFeet(3000)),
            upper = AltitudeBoundary.Unlimited
        )
    )
    val vfrRoute = VfrRoute(
        id = FixtureIds.vfrRoute,
        name = "RIVER",
        waypoints = listOf(
            Waypoint(FixtureIds.joinEntry, "JOIN"),
            Waypoint(FixtureIds.vfrRoutePoint, "RIVER")
        ),
        airspaceClass = AirspaceClass.D
    )
    val airspaceVolume = AirspaceVolume(
        id = FixtureIds.airspace,
        name = "EGT1 CTR",
        type = AirspaceVolumeType.CTR,
        airspaceClass = AirspaceClass.D,
        altitudeBand = AltitudeBand(
            lower = AltitudeBoundary.Surface,
            upper = AltitudeBoundary.Unlimited
        ),
        points = allPoints,
        fir = FixtureIds.fir
    )
    val fir = FlightInformationRegion(
        id = FixtureIds.fir,
        name = "Test FIR",
        volumes = setOf(FixtureIds.airspace)
    )

    return AviationWorld(
        geometry = sampleGeometry(),
        fixes = mapOf(
            holdFix.id to holdFix,
            starEntryFix.id to starEntryFix,
            iafFix.id to iafFix,
            sidExitFix.id to sidExitFix
        ),
        aerodromes = mapOf(FixtureIds.aerodrome to aerodrome),
        airways = mapOf(FixtureIds.airway to airway),
        vfrRoutes = mapOf(FixtureIds.vfrRoute to vfrRoute),
        airspace = mapOf(FixtureIds.airspace to airspaceVolume),
        firs = mapOf(FixtureIds.fir to fir)
    )
}

internal fun sampleGeometry(): PhysicalGeometry {
    val points = mapOf(
        FixtureIds.runway09Threshold to Position(0.0, 0.0),
        FixtureIds.runwayMid to Position(1000.0, 0.0),
        FixtureIds.runway27Threshold to Position(2000.0, 0.0),
        FixtureIds.apronJunction to Position(650.0, -150.0),
        FixtureIds.holdShort09 to Position(850.0, -150.0),
        FixtureIds.holdShort27 to Position(1150.0, 150.0),
        FixtureIds.standPoint to Position(450.0, -250.0),
        FixtureIds.upwindEnd to Position(200.0, 400.0),
        FixtureIds.crosswindEnd to Position(700.0, 700.0),
        FixtureIds.downwindEnd to Position(1400.0, 700.0),
        FixtureIds.baseTurn to Position(1600.0, 250.0),
        FixtureIds.joinEntry to Position(500.0, 950.0),
        FixtureIds.goAroundClimb to Position(50.0, 250.0),
        FixtureIds.starEntry to Position(2400.0, 1000.0),
        FixtureIds.iafPoint to Position(1700.0, 850.0),
        FixtureIds.fafPoint to Position(700.0, 200.0),
        FixtureIds.sidExit to Position(0.0, 1200.0),
        FixtureIds.holdFixPoint to Position(600.0, 1600.0),
        FixtureIds.holdLoopOne to Position(850.0, 1800.0),
        FixtureIds.holdLoopTwo to Position(350.0, 1800.0),
        FixtureIds.vfrRoutePoint to Position(150.0, 1100.0)
    )

    return PhysicalGeometry(
        points = points,
        segments = buildMap {
            addSegment(points, FixtureIds.runway09Threshold, FixtureIds.runwayMid, 45.0, SurfaceType.RUNWAY)
            addSegment(points, FixtureIds.runwayMid, FixtureIds.runway27Threshold, 45.0, SurfaceType.RUNWAY)
            addSegment(points, FixtureIds.apronJunction, FixtureIds.holdShort09, 15.0, SurfaceType.GROUND)
            addSegment(points, FixtureIds.holdShort09, FixtureIds.runwayMid, 15.0, SurfaceType.GROUND)
            addSegment(points, FixtureIds.runwayMid, FixtureIds.holdShort27, 15.0, SurfaceType.GROUND)
            addSegment(points, FixtureIds.standPoint, FixtureIds.apronJunction, 25.0, SurfaceType.GROUND)
            addSegment(points, FixtureIds.runway09Threshold, FixtureIds.upwindEnd, 400.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.upwindEnd, FixtureIds.crosswindEnd, 400.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.crosswindEnd, FixtureIds.downwindEnd, 400.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.downwindEnd, FixtureIds.baseTurn, 400.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.baseTurn, FixtureIds.runway09Threshold, 400.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.joinEntry, FixtureIds.crosswindEnd, 400.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.runway09Threshold, FixtureIds.goAroundClimb, 400.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.goAroundClimb, FixtureIds.upwindEnd, 400.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.runway09Threshold, FixtureIds.sidExit, 800.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.sidExit, FixtureIds.holdFixPoint, 800.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.starEntry, FixtureIds.iafPoint, 800.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.iafPoint, FixtureIds.fafPoint, 800.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.fafPoint, FixtureIds.runway09Threshold, 800.0, SurfaceType.SKY)
            addSegment(points, FixtureIds.goAroundClimb, FixtureIds.holdFixPoint, 800.0, SurfaceType.SKY)
            addSegment(
                points,
                FixtureIds.holdFixPoint,
                FixtureIds.holdLoopOne,
                900.0,
                SurfaceType.SKY,
                shape = SegmentShape.Arc(Meters(300.0))
            )
            addSegment(points, FixtureIds.holdLoopOne, FixtureIds.holdLoopTwo, 900.0, SurfaceType.SKY)
            addSegment(
                points,
                FixtureIds.holdLoopTwo,
                FixtureIds.holdFixPoint,
                900.0,
                SurfaceType.SKY,
                shape = SegmentShape.Arc(Meters(300.0))
            )
            addSegment(points, FixtureIds.joinEntry, FixtureIds.vfrRoutePoint, 500.0, SurfaceType.SKY)
        }
    )
}

internal fun sampleWorldWithMissingHoldingPoint(): AviationWorld {
    val world = sampleWorld()
    val aerodrome = world.aerodromes.getValue(FixtureIds.aerodrome)
    val taxiway = aerodrome.taxiways.getValue(FixtureIds.taxiwayAlpha)

    val mutatedAerodrome = aerodrome.copy(
        taxiways = aerodrome.taxiways + (
            FixtureIds.taxiwayAlpha to taxiway.copy(
                holdingPoints = taxiway.holdingPoints.filterNot { holdingPoint ->
                    holdingPoint.runway == FixtureIds.runway27
                }
            )
        )
    )

    return world.copy(
        aerodromes = world.aerodromes + (FixtureIds.aerodrome to mutatedAerodrome)
    )
}

internal fun sampleWorldWithBrokenSidAnchor(): AviationWorld {
    val world = sampleWorld()
    val aerodrome = world.aerodromes.getValue(FixtureIds.aerodrome)
    val sid = aerodrome.sids.getValue(FixtureIds.sid)

    val mutatedAerodrome = aerodrome.copy(
        sids = aerodrome.sids + (
            FixtureIds.sid to sid.copy(
                waypoints = sid.waypoints.mapIndexed { index, waypoint ->
                    if (index == 0) waypoint.copy(point = FixtureIds.holdFixPoint) else waypoint
                }
            )
        )
    )

    return world.copy(
        aerodromes = world.aerodromes + (FixtureIds.aerodrome to mutatedAerodrome)
    )
}

internal fun sampleWorldWithMissingGeometrySegment(): AviationWorld {
    val world = sampleWorld()

    return world.copy(
        geometry = world.geometry.copy(
            segments = world.geometry.segments - GeometrySegmentId.between(
                FixtureIds.runway09Threshold,
                FixtureIds.runwayMid
            )
        )
    )
}

internal fun sampleWorldWithOverlappingTaxiway(): AviationWorld {
    val world = sampleWorld()
    val aerodrome = world.aerodromes.getValue(FixtureIds.aerodrome)
    val overlappingTaxiway = Taxiway(
        id = FixtureIds.taxiwayBravo,
        name = "B",
        path = Path(
            listOf(
                FixtureIds.apronJunction,
                FixtureIds.holdShort09,
                FixtureIds.runwayMid
            )
        )
    )

    val mutatedAerodrome = aerodrome.copy(
        taxiways = aerodrome.taxiways + (FixtureIds.taxiwayBravo to overlappingTaxiway)
    )

    return world.copy(
        aerodromes = world.aerodromes + (FixtureIds.aerodrome to mutatedAerodrome)
    )
}

internal fun role(
    name: RoleName,
    vararg authorities: AuthorityGrant
): AerodromeRole =
    AerodromeRole(
        name = name,
        authorities = authorities.toSet(),
        frequency = when (name) {
            RoleName.GROUND -> Frequency("121.800")
            RoleName.TOWER -> Frequency("118.500")
            RoleName.APPROACH -> Frequency("120.100")
            RoleName.CLEARANCE_DELIVERY -> Frequency("121.600")
            RoleName.DEPARTURE -> Frequency("124.500")
            RoleName.AREA_CONTROL -> Frequency("128.300")
            RoleName.AFIS -> Frequency("122.200")
        }
    )

internal fun MutableMap<GeometrySegmentId, SegmentGeometry>.addSegment(
    points: Map<PointId, Position>,
    from: PointId,
    to: PointId,
    widthMeters: Double,
    surface: SurfaceType,
    shape: SegmentShape = SegmentShape.Straight
) {
    val fromPosition = points.getValue(from)
    val toPosition = points.getValue(to)
    put(
        GeometrySegmentId.between(from, to),
        SegmentGeometry(
            length = Meters(
                hypot(
                    toPosition.xMeters - fromPosition.xMeters,
                    toPosition.yMeters - fromPosition.yMeters
                )
            ),
            width = Meters(widthMeters),
            shape = shape,
            surface = surface
        )
    )
}
