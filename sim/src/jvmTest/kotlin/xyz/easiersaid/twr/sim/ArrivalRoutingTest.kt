package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.WindReport
import xyz.easiersaid.twr.core.world.*
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Arrival routing integration test — verifies the route planner gives
 * arriving aircraft a circuit join route automatically.
 *
 * Previously, arrivals relied on manually-constructed routes in test
 * fixtures. Now, planRouteIfNeeded builds the route from the circuit
 * procedure when the pilot is airborne with no route.
 */
class ArrivalRoutingTest {

    private val aerodromeId = AerodromeId("TEST")
    private val runwayId = RunwayId("09")
    private val standId = StandId("STAND-1")
    private val circuitId = CircuitProcedureId("CCT-09L")

    private object Pts {
        val stand = PointId("STAND-1")
        val apron = PointId("APRON-1")
        val hold = PointId("HOLD-SHORT-09")
        val thrA = PointId("THR-09")
        val thrB = PointId("THR-27")
        val rwyExit = PointId("EXIT-A")
        val upwind = PointId("UPWIND-1")
        val crosswind = PointId("CROSSWIND-1")
        val downwind = PointId("DOWNWIND-1")
        val base = PointId("BASE-1")
        val finalPt = PointId("FINAL-1")
    }

    private val positions = mapOf(
        Pts.stand to Position(-200.0, 100.0),
        Pts.apron to Position(-80.0, 60.0),
        Pts.hold to Position(-30.0, 30.0),
        Pts.thrA to Position(0.0, 0.0),
        Pts.thrB to Position(900.0, 0.0),
        Pts.rwyExit to Position(50.0, -30.0),
        Pts.upwind to Position(1200.0, 0.0),
        Pts.crosswind to Position(1200.0, 400.0),
        Pts.downwind to Position(0.0, 400.0),
        Pts.base to Position(-200.0, 250.0),
        Pts.finalPt to Position(-200.0, 0.0),
    )

    private val groundControllerId = ControllerId("GND")
    private val towerControllerId = ControllerId("TWR")
    private val approachControllerId = ControllerId("APP")
    private val alphaId = AircraftId("ALPHA")

    private val groundFrequency = Frequency.unsafe("121.800")
    private val towerFrequency = Frequency.unsafe("118.100")
    private val approachFrequency = Frequency.unsafe("125.300")

    private val world = AviationWorld(
        aerodromes = mapOf(
            aerodromeId to Aerodrome(
                icao = aerodromeId,
                elevation = Feet(0),
                magneticVariation = Degrees(0.0),
                transitionAltitude = Level.AltitudeFeet.unsafe(3000),
                runways = mapOf(
                    runwayId to Runway(
                        id = runwayId,
                        path = Path(listOf(Pts.thrA, Pts.thrB)),
                        threshold = Pts.thrA,
                        exits = listOf(RunwayExit(Pts.rwyExit, TaxiwayId("A"))),
                    ),
                ),
                stands = mapOf(
                    standId to Stand(id = standId, name = "Stand 1", point = Pts.stand),
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
                roles = mapOf(
                    RoleName.GROUND to AerodromeRole(
                        name = RoleName.GROUND,
                        authorities = setOf(AuthorityGrant(AuthorityEntityType.TAXIWAY, setOf(AuthorityOperation.TAXI))),
                        frequency = groundFrequency,
                    ),
                    RoleName.TOWER to AerodromeRole(
                        name = RoleName.TOWER,
                        authorities = setOf(AuthorityGrant(AuthorityEntityType.RUNWAY, setOf(
                            AuthorityOperation.LINE_UP, AuthorityOperation.TAKEOFF, AuthorityOperation.LAND,
                        ))),
                        frequency = towerFrequency,
                    ),
                    RoleName.APPROACH to AerodromeRole(
                        name = RoleName.APPROACH,
                        authorities = setOf(AuthorityGrant(AuthorityEntityType.AIRSPACE_VOLUME, setOf(AuthorityOperation.AIRSPACE_TRANSIT))),
                        frequency = approachFrequency,
                    ),
                ),
            ),
        ),
    )

    private val worldIndex = WorldIndex(
        positions = positions,
        adjacency = mapOf(
            Pts.stand to setOf(Pts.apron),
            Pts.apron to setOf(Pts.stand, Pts.hold, Pts.rwyExit),
            Pts.hold to setOf(Pts.apron, Pts.thrA),
            Pts.rwyExit to setOf(Pts.apron, Pts.thrA),
            Pts.thrA to setOf(Pts.hold, Pts.rwyExit, Pts.thrB),
        ),
        entitiesByPoint = mapOf(
            Pts.stand to setOf(EntityRef.StandRef(standId)),
            Pts.apron to setOf(EntityRef.TaxiwayRef(TaxiwayId("A"))),
            Pts.hold to setOf(EntityRef.TaxiwayRef(TaxiwayId("A"))),
            Pts.rwyExit to setOf(EntityRef.TaxiwayRef(TaxiwayId("A"))),
            Pts.thrA to setOf(EntityRef.RunwayRef(runwayId), EntityRef.CircuitProcedureRef(circuitId)),
            Pts.thrB to setOf(EntityRef.RunwayRef(runwayId)),
            Pts.upwind to setOf(EntityRef.CircuitProcedureRef(circuitId)),
            Pts.crosswind to setOf(EntityRef.CircuitProcedureRef(circuitId)),
            Pts.downwind to setOf(EntityRef.CircuitProcedureRef(circuitId)),
            Pts.base to setOf(EntityRef.CircuitProcedureRef(circuitId)),
            Pts.finalPt to setOf(EntityRef.CircuitProcedureRef(circuitId)),
        ),
        holdingPointsByRunway = mapOf(runwayId to setOf(Pts.hold)),
        circuitLegsByPoint = mapOf(
            Pts.thrA to setOf(LegName.UPWIND, LegName.FINAL),
            Pts.upwind to setOf(LegName.UPWIND),
            Pts.crosswind to setOf(LegName.CROSSWIND),
            Pts.downwind to setOf(LegName.DOWNWIND),
            Pts.base to setOf(LegName.BASE),
            Pts.finalPt to setOf(LegName.FINAL),
        ),
        thresholdByRunway = mapOf(runwayId to Pts.thrA),
    )

    /**
     * Aircraft spawns on downwind with no route and an Arrival mission.
     * The route planner should automatically build a circuit join route.
     */
    private fun alphaOnDownwind() = AircraftState(
        id = alphaId,
        callsign = Callsign("ALPHA"),
        position = positions[Pts.downwind]!!,
        positionPoint = Pts.downwind,
        pilotGoal = PilotGoal.ARRIVE,
        humanPiloted = false,
        route = PilotRoute.None, // NO route — planner must build one
        phase = PilotPhase.Downwind,
        altitudeM = CIRCUIT_ALTITUDE_M,
        targetAltitudeM = CIRCUIT_ALTITUDE_M,
        speedMps = PilotConstants.APPROACH_SPEED_MPS,
        targetSpeedMps = PilotConstants.APPROACH_SPEED_MPS,
        pilotMission = createMission(
            goal = HighLevelGoal.Arrival(),
            startPhase = PilotPhase.Downwind,
            time = SimTime.ZERO,
            humanPiloted = false,
        ).copy(activeRunway = runwayId),
    )

    @Test
    fun `arrival on downwind with no route gets circuit join route and lands`() {
        val initial = SimState.initial(
            seed = 42L, world = world, worldIndex = worldIndex,
            controllers = listOf(
                ControllerSpec(groundControllerId, RoleName.GROUND, aerodromeId, groundFrequency, emptySet()),
                ControllerSpec(towerControllerId, RoleName.TOWER, aerodromeId, towerFrequency, setOf(alphaId)),
                ControllerSpec(approachControllerId, RoleName.APPROACH, aerodromeId, approachFrequency, emptySet()),
            ),
            weatherByAerodrome = world.aerodromes.keys.associateWith {
                WeatherObservation(wind = WindReport.Available(xyz.easiersaid.twr.protocol.Wind.unsafe(directionDegrees = 90, speedKnots = 5)), qnh = null, visibility = null)
            },
        ).getOrElse { error("ArrivalRouting downwind setup invalid: $it") }
        val events = listOf(
            SimEvent.PhysicsTick(SimTime.ZERO),
            SimEvent.Spawn(SimTime.ZERO, alphaOnDownwind()),
            SimEvent.ControllerCycle(SimTime.ZERO, groundControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, towerControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, approachControllerId),
        )

        // After 3 seconds: the pilot should have a route (no longer PilotRoute.None).
        val after3s = runUntil(initial, events, SimTime.ofSeconds(3))
        val ac3 = after3s.aircraft[alphaId]!!
        assertNotEquals(PilotRoute.None, ac3.route,
            "Pilot should have a route after planRouteIfNeeded fires")
        assertTrue(ac3.route is PilotRoute.Airborne,
            "Route should be Airborne, was ${ac3.route::class.simpleName}")

        // Run to completion: the pilot should land and park.
        val result = runUntil(initial, events, SimTime.ofSeconds(900))
        val acFinal = result.aircraft[alphaId]!!

        assertEquals(PilotPhase.Parked, acFinal.phase,
            "Arrival must park. Actual: phase=${acFinal.phase} point=${acFinal.positionPoint} " +
            "alt=${"%.0f".format(acFinal.altitudeM)} step=${acFinal.pilotMission?.currentTask?.step}")
        assertEquals(Pts.stand, acFinal.positionPoint,
            "Arrival must return to stand")
    }

    /**
     * E3 — StopClimbAt drives the pilot's targetAltitudeM kinematically.
     *
     * Aircraft spawns on downwind with a StopClimbAt restriction at 600 ft (≈ 183 m),
     * which is below CIRCUIT_ALTITUDE_M (300 m). After the route is built, the pilot's
     * route targetAltitudeM must be capped at the restriction, not at circuit altitude.
     */
    @Test
    fun `StopClimbAt restricts pilot targetAltitudeM to restricted level`() {
        // 600 ft ≈ 182.88 m — below CIRCUIT_ALTITUDE_M (300 m).
        val restrictionFt = 600
        val restrictionM = restrictionFt * 0.3048

        val baseMission = createMission(
            goal = HighLevelGoal.Arrival(),
            startPhase = PilotPhase.Downwind,
            time = SimTime.ZERO,
            humanPiloted = false,
        ).copy(activeRunway = runwayId)

        // Process StopClimbAt: stores altitudeRestrictionM on the mission.
        val restrictedMission = processInstruction(
            StopClimbAt(alphaId, Level.AltitudeFeet.unsafe(restrictionFt)),
            baseMission,
            SimTime.ZERO,
        )
        assertEquals(restrictionM, restrictedMission.altitudeRestrictionM,
            "processInstruction(StopClimbAt) should store restriction in altitudeRestrictionM")

        val aircraft = AircraftState(
            id = alphaId,
            callsign = Callsign("ALPHA"),
            position = positions[Pts.downwind]!!,
            positionPoint = Pts.downwind,
            pilotGoal = PilotGoal.ARRIVE,
            humanPiloted = false,
            route = PilotRoute.None,
            phase = PilotPhase.Downwind,
            altitudeM = 150.0,
            targetAltitudeM = 150.0,
            speedMps = PilotConstants.APPROACH_SPEED_MPS,
            targetSpeedMps = PilotConstants.APPROACH_SPEED_MPS,
            pilotMission = restrictedMission,
        )

        val initial = SimState.initial(
            seed = 42L, world = world, worldIndex = worldIndex,
            controllers = listOf(
                ControllerSpec(groundControllerId, RoleName.GROUND, aerodromeId, groundFrequency, emptySet()),
                ControllerSpec(towerControllerId, RoleName.TOWER, aerodromeId, towerFrequency, setOf(alphaId)),
                ControllerSpec(approachControllerId, RoleName.APPROACH, aerodromeId, approachFrequency, emptySet()),
            ),
            weatherByAerodrome = world.aerodromes.keys.associateWith {
                WeatherObservation(wind = WindReport.Available(xyz.easiersaid.twr.protocol.Wind.unsafe(directionDegrees = 90, speedKnots = 5)), qnh = null, visibility = null)
            },
        ).getOrElse { error("ArrivalRouting restricted setup invalid: $it") }
        val events = listOf(
            SimEvent.PhysicsTick(SimTime.ZERO),
            SimEvent.Spawn(SimTime.ZERO, aircraft),
            SimEvent.ControllerCycle(SimTime.ZERO, groundControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, towerControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, approachControllerId),
        )

        // After 3 seconds the planner has fired. Verify route is capped at restriction.
        val after3s = runUntil(initial, events, SimTime.ofSeconds(3))
        val ac = after3s.aircraft[alphaId]!!
        val route = ac.route as? PilotRoute.Airborne
        assertNotEquals(null, route, "Aircraft should have an airborne route after 3s")
        assertTrue(
            kotlin.math.abs(route!!.targetAltitudeM - restrictionM) < 1.0,
            "Route targetAltitudeM should be capped at restriction (${restrictionM}m), " +
            "not circuit altitude (${CIRCUIT_ALTITUDE_M}m). Actual: ${route.targetAltitudeM}")
        assertTrue(ac.targetAltitudeM <= restrictionM + 1.0,
            "Aircraft targetAltitudeM should not exceed restriction. Actual: ${ac.targetAltitudeM}")
    }
}
