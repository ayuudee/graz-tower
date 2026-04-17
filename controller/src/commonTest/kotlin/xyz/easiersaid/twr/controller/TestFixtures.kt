package xyz.easiersaid.twr.controller

import arrow.core.getOrElse
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.*
import xyz.easiersaid.twr.protocol.*

/**
 * Minimal test fixture for controller integration tests.
 *
 * Builds a WorldIndex directly (no full AviationWorld needed).
 * Represents a single-runway aerodrome with:
 * - Runway 09: threshold → mid → end
 * - Taxiway A: apron → holdShort09 (holding point for 09)
 * - Circuit: upwind → crosswind → downwind → base → final (→ threshold)
 */
object TestIds {
    val aerodrome = AerodromeId("EGTT")
    val controller = ControllerId("TWR-1")
    val runway09 = RunwayId("09")
    val taxiwayA = TaxiwayId("A")
    val circuit = CircuitProcedureId("09-LH")

    // Points
    val rwyThreshold = PointId("RWY09_THR")
    val rwyMid = PointId("RWY09_MID")
    val rwyEnd = PointId("RWY09_END")
    val holdShort = PointId("HOLD_SHORT_09")
    val apron = PointId("APRON")
    val upwind = PointId("CKT_UPWIND")
    val crosswind = PointId("CKT_CROSSWIND")
    val downwind = PointId("CKT_DOWNWIND")
    val base = PointId("CKT_BASE")
    val finalApproach = PointId("CKT_FINAL")

    val stand1 = StandId("S1")
    val standPoint = PointId("STAND_1")

    // Aircraft
    val acAlpha = AircraftId("G-ABCD")
    val acBravo = AircraftId("G-EFGH")
}

fun testWorldIndex(): WorldIndex {
    val rwyRef = EntityRef.RunwayRef(TestIds.runway09)
    val twyRef = EntityRef.TaxiwayRef(TestIds.taxiwayA)
    val cktRef = EntityRef.CircuitProcedureRef(TestIds.circuit)
    val stnRef = EntityRef.StandRef(TestIds.stand1)

    return WorldIndex(
        positions = mapOf(
            TestIds.rwyThreshold to Position(0.0, 0.0),
            TestIds.rwyMid to Position(900.0, 0.0),
            TestIds.rwyEnd to Position(1800.0, 0.0),
            TestIds.holdShort to Position(-30.0, 50.0),
            TestIds.apron to Position(-100.0, 100.0),
            TestIds.standPoint to Position(-150.0, 100.0),
            TestIds.upwind to Position(1800.0, 300.0),
            TestIds.crosswind to Position(1800.0, 600.0),
            TestIds.downwind to Position(0.0, 600.0),
            TestIds.base to Position(-200.0, 300.0),
            TestIds.finalApproach to Position(-200.0, 0.0),
        ),
        adjacency = mapOf(
            TestIds.rwyThreshold to setOf(TestIds.rwyMid, TestIds.holdShort, TestIds.finalApproach),
            TestIds.rwyMid to setOf(TestIds.rwyThreshold, TestIds.rwyEnd),
            TestIds.rwyEnd to setOf(TestIds.rwyMid, TestIds.upwind),
            TestIds.holdShort to setOf(TestIds.rwyThreshold, TestIds.apron),
            TestIds.apron to setOf(TestIds.holdShort, TestIds.standPoint),
            TestIds.standPoint to setOf(TestIds.apron),
            TestIds.upwind to setOf(TestIds.rwyEnd, TestIds.crosswind),
            TestIds.crosswind to setOf(TestIds.upwind, TestIds.downwind),
            TestIds.downwind to setOf(TestIds.crosswind, TestIds.base),
            TestIds.base to setOf(TestIds.downwind, TestIds.finalApproach),
            TestIds.finalApproach to setOf(TestIds.base, TestIds.rwyThreshold),
        ),
        entitiesByPoint = mapOf(
            TestIds.rwyThreshold to setOf(rwyRef, cktRef),
            TestIds.rwyMid to setOf(rwyRef),
            TestIds.rwyEnd to setOf(rwyRef),
            TestIds.holdShort to setOf(twyRef),
            TestIds.apron to setOf(twyRef),
            TestIds.standPoint to setOf(stnRef),
            TestIds.upwind to setOf(cktRef),
            TestIds.crosswind to setOf(cktRef),
            TestIds.downwind to setOf(cktRef),
            TestIds.base to setOf(cktRef),
            TestIds.finalApproach to setOf(cktRef),
        ),
        holdingPointsByRunway = mapOf(
            TestIds.runway09 to setOf(TestIds.holdShort),
        ),
        circuitLegsByPoint = mapOf(
            TestIds.rwyThreshold to setOf(LegName.FINAL, LegName.UPWIND),
            TestIds.upwind to setOf(LegName.UPWIND),
            TestIds.crosswind to setOf(LegName.CROSSWIND),
            TestIds.downwind to setOf(LegName.DOWNWIND),
            TestIds.base to setOf(LegName.BASE),
            TestIds.finalApproach to setOf(LegName.FINAL),
        ),
    )
}

fun aircraftAt(
    id: AircraftId,
    point: PointId,
    worldIndex: WorldIndex,
    onGround: Boolean = true,
    goal: PilotGoal? = PilotGoal.DEPART,
    flightRules: FlightRules? = FlightRules.VFR,
    humanPiloted: Boolean = false,
): AircraftObservation = AircraftObservation(
    id = id,
    callsign = Callsign(id.value),
    position = point,
    entities = worldIndex.entitiesByPoint[point] ?: emptySet(),
    altitude = null,
    speed = null,
    onGround = onGround,
    flightRules = flightRules,
    pilotGoal = goal,
    humanPiloted = humanPiloted,
)

fun towerView(
    aircraft: Map<AircraftId, AircraftObservation>,
    time: SimTime = SimTime.ofSeconds(0),
    responsibilities: Set<AircraftId> = aircraft.keys,
    receivedMessages: List<ReceivedMessage> = emptyList(),
    weather: WeatherObservation? = null,
    runways: Map<RunwayId, RunwayObservation> = mapOf(
        TestIds.runway09 to RunwayObservation(TestIds.runway09, RunwayStatus.CLEAR, emptySet())
    ),
    activeClearances: Map<ClearanceId, ClearanceSummary> = emptyMap(),
    worldIndex: WorldIndex = testWorldIndex(),
): ControllerView = ControllerView(
    time = time,
    controllerId = TestIds.controller,
    role = RoleName.TOWER,
    aerodromeId = TestIds.aerodrome,
    responsibilities = responsibilities,
    aircraft = aircraft,
    runways = runways,
    activeClearances = activeClearances,
    receivedMessages = receivedMessages,
    weather = weather,
    pendingInboundHandoffs = emptyList(),
    worldIndex = worldIndex,
)

/** Minimal AviationWorld for controller tests. Structural stub — not validated. */
fun testWorld(): AviationWorld = AviationWorld(
    geometry = PhysicalGeometry(
        points = testWorldIndex().positions,
        segments = emptyMap(), // segments not needed for controller-level tests
    ),
    fixes = emptyMap(),
    aerodromes = mapOf(TestIds.aerodrome to Aerodrome(
        icao = TestIds.aerodrome,
        elevation = Feet(0),
        magneticVariation = Degrees(0.0),
        transitionAltitude = Level.AltitudeFeet(5000).getOrElse { error(it) },
        roles = mapOf(
            RoleName.TOWER to AerodromeRole(
                name = RoleName.TOWER,
                authorities = setOf(AuthorityGrant(AuthorityEntityType.RUNWAY, setOf(AuthorityOperation.TAKEOFF, AuthorityOperation.LAND))),
                frequency = Frequency.unsafe("118.500"),
            ),
            RoleName.GROUND to AerodromeRole(
                name = RoleName.GROUND,
                authorities = setOf(AuthorityGrant(AuthorityEntityType.TAXIWAY, setOf(AuthorityOperation.TAXI))),
                frequency = Frequency.unsafe("121.700"),
            ),
            RoleName.APPROACH to AerodromeRole(
                name = RoleName.APPROACH,
                authorities = setOf(AuthorityGrant(AuthorityEntityType.AIRSPACE_VOLUME, setOf(AuthorityOperation.APPROACH_CLEARANCE))),
                frequency = Frequency.unsafe("120.500"),
            ),
        ),
        runways = mapOf(TestIds.runway09 to Runway(
            id = TestIds.runway09,
            path = Path(listOf(TestIds.rwyThreshold, TestIds.rwyMid, TestIds.rwyEnd)),
            threshold = TestIds.rwyThreshold,
        )),
        taxiways = mapOf(TestIds.taxiwayA to Taxiway(
            id = TestIds.taxiwayA,
            name = "Alpha",
            path = Path(listOf(TestIds.apron, TestIds.holdShort)),
            holdingPoints = listOf(HoldingPoint(
                point = TestIds.holdShort,
                type = HoldingPointType.CAT_A,
                runway = TestIds.runway09,
            )),
        )),
        stands = mapOf(TestIds.stand1 to Stand(
            id = TestIds.stand1,
            name = "Stand 1",
            point = TestIds.standPoint,
        )),
        circuits = mapOf(TestIds.circuit to CircuitProcedure(
            id = TestIds.circuit,
            runway = TestIds.runway09,
            direction = CircuitDirection.LEFT_HAND,
            legs = listOf(
                CircuitLeg(LegName.UPWIND, Path(listOf(TestIds.rwyThreshold, TestIds.upwind))),
                CircuitLeg(LegName.CROSSWIND, Path(listOf(TestIds.upwind, TestIds.crosswind))),
                CircuitLeg(LegName.DOWNWIND, Path(listOf(TestIds.crosswind, TestIds.downwind))),
                CircuitLeg(LegName.BASE, Path(listOf(TestIds.downwind, TestIds.base))),
                CircuitLeg(LegName.FINAL, Path(listOf(TestIds.base, TestIds.finalApproach, TestIds.rwyThreshold))),
            ),
            altitude = Level.AltitudeFeet(1000).getOrElse { error(it) },
            goAroundPath = Path(listOf(TestIds.rwyThreshold, TestIds.upwind)),
        )),
    )),
    airways = emptyMap(),
    vfrRoutes = emptyMap(),
    airspace = emptyMap(),
    firs = emptyMap(),
)

fun readyForDepartureMessage(aircraft: AircraftId): ReceivedMessage =
    ReceivedMessage.Clear(aircraft, Report(listOf(ReportEvent.Ready)))

fun positionReportMessage(aircraft: AircraftId, event: ReportEvent): ReceivedMessage =
    ReceivedMessage.Clear(aircraft, Report(listOf(event)))

fun goAroundMessage(aircraft: AircraftId): ReceivedMessage =
    ReceivedMessage.Clear(aircraft, Report(listOf(ReportEvent.GoingAround)))

/** Convenience: call controllerDecide with the test world. */
fun testControllerDecide(
    view: ControllerView,
    beliefs: BeliefState,
    world: AviationWorld = testWorld(),
): ControllerDecisionResult = controllerDecide(view, beliefs, world)
