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
        thresholdByRunway = mapOf(
            TestIds.runway09 to TestIds.rwyThreshold,
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
    heading: Heading? = null,
    groundSpeed: Knots? = null,
    wakeCategory: WakeCategory? = WakeCategory.L,
): AircraftObservation = AircraftObservation(
    id = id,
    callsign = Callsign(id.value),
    position = point,
    entities = worldIndex.entitiesByPoint[point] ?: emptySet(),
    altitude = null,
    speed = null,
    heading = heading,
    groundSpeed = groundSpeed,
    onGround = onGround,
    flightRules = flightRules,
    pilotGoal = goal,
    humanPiloted = humanPiloted,
    wakeCategory = wakeCategory,
)

fun towerView(
    aircraft: Map<AircraftId, AircraftObservation>,
    time: SimTime = SimTime.ofSeconds(0),
    responsibilities: Set<AircraftId> = aircraft.keys,
    receivedMessages: List<ReceivedMessage> = emptyList(),
    // Default east wind (5 kt) so runway 09 (test-fixture's only runway) is
    // selected by `selectRunwayIntoWind` and downstream sequencing works.
    // Tests that need "no weather report" pass null explicitly.
    weather: WeatherObservation? = WeatherObservation(
        wind = WindReport.Available(xyz.easiersaid.twr.protocol.Wind.unsafe(directionDegrees = 90, speedKnots = 5)),
        qnh = null,
        visibility = null,
    ),
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

// ── Second aerodrome: right-hand circuit (flushes single-field assumptions) ──

object TestIdsB {
    val aerodrome = AerodromeId("LOWI")
    val controller = ControllerId("TWR-2")
    val runway08 = RunwayId("08")
    val taxiwayB = TaxiwayId("B")
    val circuit = CircuitProcedureId("08-RH")

    val rwyThreshold = PointId("B_RWY08_THR")
    val rwyMid = PointId("B_RWY08_MID")
    val rwyEnd = PointId("B_RWY08_END")
    val holdShort = PointId("B_HOLD_SHORT_08")
    val apron = PointId("B_APRON")
    val upwind = PointId("B_CKT_UPWIND")
    val crosswind = PointId("B_CKT_CROSSWIND")
    val downwind = PointId("B_CKT_DOWNWIND")
    val base = PointId("B_CKT_BASE")
    val finalApproach = PointId("B_CKT_FINAL")
    val stand1 = StandId("B_S1")
    val standPoint = PointId("B_STAND_1")
}

/** Right-hand circuit world. Mirrors testWorld() but with opposite circuit direction. */
fun testWorldB(): AviationWorld = AviationWorld(
    geometry = PhysicalGeometry(
        points = mapOf(
            TestIdsB.rwyThreshold to Position(0.0, 0.0),
            TestIdsB.rwyMid to Position(900.0, 0.0),
            TestIdsB.rwyEnd to Position(1800.0, 0.0),
            TestIdsB.holdShort to Position(-30.0, -50.0),
            TestIdsB.apron to Position(-100.0, -100.0),
            TestIdsB.standPoint to Position(-150.0, -100.0),
            TestIdsB.upwind to Position(1800.0, -300.0),
            TestIdsB.crosswind to Position(1800.0, -600.0),
            TestIdsB.downwind to Position(0.0, -600.0),
            TestIdsB.base to Position(-200.0, -300.0),
            TestIdsB.finalApproach to Position(-200.0, 0.0),
        ),
        segments = emptyMap(),
    ),
    fixes = emptyMap(),
    aerodromes = mapOf(TestIdsB.aerodrome to Aerodrome(
        icao = TestIdsB.aerodrome,
        elevation = Feet(0),
        magneticVariation = Degrees(0.0),
        transitionAltitude = Level.AltitudeFeet(5000).getOrElse { error(it) },
        roles = mapOf(
            RoleName.TOWER to AerodromeRole(
                name = RoleName.TOWER,
                authorities = setOf(AuthorityGrant(AuthorityEntityType.RUNWAY, setOf(AuthorityOperation.TAKEOFF, AuthorityOperation.LAND))),
                frequency = Frequency.unsafe("119.100"),
            ),
        ),
        runways = mapOf(TestIdsB.runway08 to Runway(
            id = TestIdsB.runway08,
            path = Path(listOf(TestIdsB.rwyThreshold, TestIdsB.rwyMid, TestIdsB.rwyEnd)),
            threshold = TestIdsB.rwyThreshold,
        )),
        taxiways = mapOf(TestIdsB.taxiwayB to Taxiway(
            id = TestIdsB.taxiwayB,
            name = "Bravo",
            path = Path(listOf(TestIdsB.apron, TestIdsB.holdShort)),
            holdingPoints = listOf(HoldingPoint(
                point = TestIdsB.holdShort,
                type = HoldingPointType.CAT_A,
                runway = TestIdsB.runway08,
            )),
        )),
        stands = mapOf(TestIdsB.stand1 to Stand(
            id = TestIdsB.stand1,
            name = "Stand 1",
            point = TestIdsB.standPoint,
        )),
        circuits = mapOf(TestIdsB.circuit to CircuitProcedure(
            id = TestIdsB.circuit,
            runway = TestIdsB.runway08,
            direction = CircuitDirection.RIGHT_HAND,
            legs = listOf(
                CircuitLeg(LegName.UPWIND, Path(listOf(TestIdsB.rwyThreshold, TestIdsB.upwind))),
                CircuitLeg(LegName.CROSSWIND, Path(listOf(TestIdsB.upwind, TestIdsB.crosswind))),
                CircuitLeg(LegName.DOWNWIND, Path(listOf(TestIdsB.crosswind, TestIdsB.downwind))),
                CircuitLeg(LegName.BASE, Path(listOf(TestIdsB.downwind, TestIdsB.base))),
                CircuitLeg(LegName.FINAL, Path(listOf(TestIdsB.base, TestIdsB.finalApproach, TestIdsB.rwyThreshold))),
            ),
            altitude = Level.AltitudeFeet(1000).getOrElse { error(it) },
            goAroundPath = Path(listOf(TestIdsB.rwyThreshold, TestIdsB.upwind)),
        )),
    )),
    airways = emptyMap(),
    vfrRoutes = emptyMap(),
    airspace = emptyMap(),
    firs = emptyMap(),
)

// ── NM-scale test world for separation testing ──────────────────────

object NmIds {
    val aerodrome = AerodromeId("EGSEP")
    val controller = ControllerId("TWR-SEP")
    val runway09 = RunwayId("09")
    val circuit = CircuitProcedureId("09-LH-NM")

    // Positions in metres. 1 NM = 1852m.
    val rwyThreshold = PointId("NM_THR")
    val rwyEnd = PointId("NM_END")
    val holdShort = PointId("NM_HOLD")
    val upwind = PointId("NM_UPWIND")
    val crosswind = PointId("NM_CROSSWIND")
    val downwind = PointId("NM_DOWNWIND")
    val base = PointId("NM_BASE")
    val finalApproach = PointId("NM_FINAL") // ~2nm from threshold
    val longFinal = PointId("NM_LONG_FINAL") // ~5nm from threshold
}

/** WorldIndex with NM-scale distances for separation tests. Circuit ~12nm total. */
fun testWorldIndexNmScale(): WorldIndex {
    val rwyRef = EntityRef.RunwayRef(NmIds.runway09)
    val cktRef = EntityRef.CircuitProcedureRef(NmIds.circuit)
    return WorldIndex(
        positions = mapOf(
            NmIds.rwyThreshold to Position(0.0, 0.0),
            NmIds.rwyEnd to Position(2000.0, 0.0),
            NmIds.holdShort to Position(-100.0, 200.0),
            NmIds.upwind to Position(2000.0, 5556.0),       // 3nm lateral
            NmIds.crosswind to Position(9260.0, 5556.0),    // 5nm along upwind+crosswind
            NmIds.downwind to Position(9260.0, 0.0),         // 3nm lateral from runway
            NmIds.base to Position(-3704.0, 5556.0),         // 3nm lateral, 2nm before threshold
            NmIds.finalApproach to Position(-3704.0, 0.0),   // 2nm from threshold
            NmIds.longFinal to Position(-9260.0, 0.0),       // 5nm from threshold
        ),
        adjacency = mapOf(
            NmIds.rwyThreshold to setOf(NmIds.rwyEnd, NmIds.holdShort, NmIds.finalApproach),
            NmIds.rwyEnd to setOf(NmIds.rwyThreshold, NmIds.upwind),
            NmIds.holdShort to setOf(NmIds.rwyThreshold),
            NmIds.upwind to setOf(NmIds.rwyEnd, NmIds.crosswind),
            NmIds.crosswind to setOf(NmIds.upwind, NmIds.downwind),
            NmIds.downwind to setOf(NmIds.crosswind, NmIds.base),
            NmIds.base to setOf(NmIds.downwind, NmIds.finalApproach),
            NmIds.finalApproach to setOf(NmIds.base, NmIds.rwyThreshold, NmIds.longFinal),
            NmIds.longFinal to setOf(NmIds.finalApproach),
        ),
        entitiesByPoint = mapOf(
            NmIds.rwyThreshold to setOf(rwyRef, cktRef),
            NmIds.rwyEnd to setOf(rwyRef),
            NmIds.upwind to setOf(cktRef),
            NmIds.crosswind to setOf(cktRef),
            NmIds.downwind to setOf(cktRef),
            NmIds.base to setOf(cktRef),
            NmIds.finalApproach to setOf(cktRef),
            NmIds.longFinal to setOf(cktRef),
        ),
        holdingPointsByRunway = mapOf(NmIds.runway09 to setOf(NmIds.holdShort)),
        circuitLegsByPoint = mapOf(
            NmIds.rwyThreshold to setOf(LegName.FINAL, LegName.UPWIND),
            NmIds.upwind to setOf(LegName.UPWIND),
            NmIds.crosswind to setOf(LegName.CROSSWIND),
            NmIds.downwind to setOf(LegName.DOWNWIND),
            NmIds.base to setOf(LegName.BASE),
            NmIds.finalApproach to setOf(LegName.FINAL),
            NmIds.longFinal to setOf(LegName.FINAL),
        ),
        thresholdByRunway = mapOf(NmIds.runway09 to NmIds.rwyThreshold),
    )
}

/** Create an airborne arrival at an NM-scale point with realistic speed/wake. */
fun arrivalAtNm(
    id: AircraftId,
    point: PointId,
    worldIndex: WorldIndex = testWorldIndexNmScale(),
    groundSpeedKt: Int = 120,
    wakeCategory: WakeCategory = WakeCategory.L,
): AircraftObservation = aircraftAt(
    id, point, worldIndex,
    onGround = false,
    goal = PilotGoal.ARRIVE,
    groundSpeed = if (groundSpeedKt > 0) Knots.unsafe(groundSpeedKt) else null,
    wakeCategory = wakeCategory,
)

fun readyForDepartureMessage(aircraft: AircraftId): ReceivedMessage =
    ReceivedMessage.Clear(aircraft, Report(listOf(ReportEvent.Ready)))

fun positionReportMessage(aircraft: AircraftId, event: ReportEvent): ReceivedMessage =
    ReceivedMessage.Clear(aircraft, Report(listOf(event)))

fun goAroundMessage(aircraft: AircraftId): ReceivedMessage =
    ReceivedMessage.Clear(aircraft, Report(listOf(ReportEvent.GoingAround)))

/**
 * Build a correct readback message for an instruction.
 * Used in tests where readback-gated rules need readback delivery.
 */
fun readbackFor(output: ControllerOutput.Instruct): ReceivedMessage {
    val atoms = xyz.easiersaid.twr.controller.observe.requiredReadbackAtoms(output.instruction)
    val elements = atoms.map { SimpleElement(it) }
    return ReceivedMessage.Clear(output.target, Readback(elements))
}

/** Extract Instruct outputs from a decision result. */
fun ControllerDecisionResult.instructs(): List<ControllerOutput.Instruct> =
    outputs.filterIsInstance<ControllerOutput.Instruct>()

/** Convenience: call controllerDecide with the test world. */
fun testControllerDecide(
    view: ControllerView,
    beliefs: BeliefState,
    world: AviationWorld = testWorld(),
): ControllerDecisionResult = controllerDecide(view, beliefs, world)
