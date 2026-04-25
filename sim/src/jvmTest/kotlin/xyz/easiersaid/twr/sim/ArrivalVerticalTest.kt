package xyz.easiersaid.twr.sim

import arrow.core.NonEmptyList
import arrow.core.getOrElse
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AerodromeRole
import xyz.easiersaid.twr.core.world.AuthorityGrant
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.RunwayExit
import xyz.easiersaid.twr.core.world.Stand
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AuthorityEntityType
import xyz.easiersaid.twr.protocol.AuthorityOperation
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.StandId
import xyz.easiersaid.twr.protocol.TaxiwayId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Slice 4e-B acceptance bar — the arrival vertical.
 *
 * End-to-end: an AI aircraft spawns airborne on the downwind leg with pilot
 * goal ARRIVE, owned by approach. Three controllers (approach, tower, ground)
 * must sequence the arrival through:
 *
 *   1. Approach sees the aircraft on downwind and hands off to tower.
 *   2. Tower advances the arrival through AwaitDownwind → AwaitApproach, clears
 *      to land when the aircraft reaches the final leg, and observes the
 *      touchdown.
 *   3. Tower issues AfterLandingVacateVia for the runway exit; the pilot
 *      taxis off the runway.
 *   4. Tower hands off to ground once the aircraft is clear of the runway.
 *   5. Ground taxis the aircraft to its stand and the commitment completes.
 *
 * The assertion is on the final state: the aircraft is parked at the stand,
 * altitude 0, speed 0, approach and tower both released — none of which is
 * possible without every 4e-B piece working (new arrival phases, airborne →
 * ground altitude gating, [AfterLandingVacateVia] effect, tower/ground
 * arrival procedures, responsibility handoffs).
 */
class ArrivalVerticalTest {

    private val aerodromeId = AerodromeId("TEST")
    private val runwayId = RunwayId("09")
    private val standId = StandId("STAND-1")
    private val taxiwayId = TaxiwayId("A")
    private val circuitId = CircuitProcedureId("CCT-09L")

    private object TestPoints {
        val stand: PointId = PointId("STAND-1")
        val apron: PointId = PointId("APRON-1")
        val thresholdA: PointId = PointId("THR-09")
        val thresholdB: PointId = PointId("THR-27")
        val rwyExit: PointId = PointId("EXIT-A")
        val downwind: PointId = PointId("DOWNWIND-1")
        val base: PointId = PointId("BASE-1")
        val finalPt: PointId = PointId("FINAL-1")

        val standPos: Position = Position(xMeters = -200.0, yMeters = 100.0)
        val apronPos: Position = Position(xMeters = -80.0, yMeters = 60.0)
        val thrAPos: Position = Position(xMeters = 0.0, yMeters = 0.0)
        val thrBPos: Position = Position(xMeters = 900.0, yMeters = 0.0)
        val rwyExitPos: Position = Position(xMeters = 50.0, yMeters = -30.0)
        val downwindPos: Position = Position(xMeters = 0.0, yMeters = 300.0)
        val basePos: Position = Position(xMeters = -200.0, yMeters = 200.0)
        val finalPos: Position = Position(xMeters = -200.0, yMeters = 0.0)
    }

    private val groundControllerId = ControllerId("GND")
    private val towerControllerId = ControllerId("TWR")
    private val approachControllerId = ControllerId("APP")
    private val alphaId = AircraftId("ALPHA")

    private val groundFrequency = Frequency.unsafe("121.800")
    private val towerFrequency = Frequency.unsafe("118.100")
    private val approachFrequency = Frequency.unsafe("125.300")

    /** Pattern altitude for the test arrival — kept small so the descent
     *  completes well within the scenario time budget. */
    private val patternAltitudeM = 150.0

    private val world: AviationWorld = AviationWorld(
        aerodromes = mapOf(
            aerodromeId to Aerodrome(
                icao = aerodromeId,
                elevation = Feet(0),
                magneticVariation = Degrees(0.0),
                transitionAltitude = Level.AltitudeFeet.unsafe(3000),
                runways = mapOf(
                    runwayId to Runway(
                        id = runwayId,
                        path = Path(listOf(TestPoints.thresholdA, TestPoints.thresholdB)),
                        threshold = TestPoints.thresholdA,
                        exits = listOf(RunwayExit(TestPoints.rwyExit, taxiwayId)),
                    ),
                ),
                stands = mapOf(
                    standId to Stand(id = standId, name = "Stand 1", point = TestPoints.stand),
                ),
                roles = mapOf(
                    RoleName.GROUND to AerodromeRole(
                        name = RoleName.GROUND,
                        authorities = setOf(
                            AuthorityGrant(AuthorityEntityType.TAXIWAY, setOf(AuthorityOperation.TAXI)),
                        ),
                        frequency = groundFrequency,
                    ),
                    RoleName.TOWER to AerodromeRole(
                        name = RoleName.TOWER,
                        authorities = setOf(
                            AuthorityGrant(AuthorityEntityType.RUNWAY, setOf(
                                AuthorityOperation.LINE_UP,
                                AuthorityOperation.TAKEOFF,
                                AuthorityOperation.LAND,
                            )),
                        ),
                        frequency = towerFrequency,
                    ),
                    RoleName.APPROACH to AerodromeRole(
                        name = RoleName.APPROACH,
                        authorities = setOf(
                            AuthorityGrant(AuthorityEntityType.AIRSPACE_VOLUME, setOf(AuthorityOperation.AIRSPACE_TRANSIT)),
                        ),
                        frequency = approachFrequency,
                    ),
                ),
            ),
        ),
    )

    private val worldIndex = WorldIndex(
        positions = mapOf(
            TestPoints.stand to TestPoints.standPos,
            TestPoints.apron to TestPoints.apronPos,
            TestPoints.thresholdA to TestPoints.thrAPos,
            TestPoints.thresholdB to TestPoints.thrBPos,
            TestPoints.rwyExit to TestPoints.rwyExitPos,
            TestPoints.downwind to TestPoints.downwindPos,
            TestPoints.base to TestPoints.basePos,
            TestPoints.finalPt to TestPoints.finalPos,
        ),
        adjacency = mapOf(
            TestPoints.stand to setOf(TestPoints.apron),
            TestPoints.apron to setOf(TestPoints.stand, TestPoints.rwyExit),
            TestPoints.rwyExit to setOf(TestPoints.apron, TestPoints.thresholdA),
            TestPoints.thresholdA to setOf(TestPoints.rwyExit, TestPoints.thresholdB),
        ),
        entitiesByPoint = mapOf(
            TestPoints.stand to setOf(EntityRef.StandRef(standId)),
            TestPoints.thresholdA to setOf(EntityRef.RunwayRef(runwayId)),
            TestPoints.thresholdB to setOf(EntityRef.RunwayRef(runwayId)),
            // rwyExit is a taxiway intersection with the runway — once the aircraft
            // is past it, it's off the runway. Must NOT carry RunwayRef or the
            // `Not(OnRunway)` guards on vacate/handoff never fire.
            TestPoints.rwyExit to setOf(EntityRef.TaxiwayRef(taxiwayId)),
            // Circuit legs carry CircuitProcedureRef so the tower-arrival
            // `InCircuit` guard fires and the AI proactive advance kicks in.
            TestPoints.downwind to setOf(EntityRef.CircuitProcedureRef(circuitId)),
            TestPoints.base to setOf(EntityRef.CircuitProcedureRef(circuitId)),
            TestPoints.finalPt to setOf(EntityRef.CircuitProcedureRef(circuitId)),
        ),
        circuitLegsByPoint = mapOf(
            TestPoints.downwind to setOf(LegName.DOWNWIND),
            TestPoints.base to setOf(LegName.BASE),
            TestPoints.finalPt to setOf(LegName.FINAL),
        ),
    )

    private fun alpha() = AircraftState(
        id = alphaId,
        callsign = Callsign("ALPHA"),
        position = TestPoints.downwindPos,
        positionPoint = TestPoints.downwind,
        altitudeM = patternAltitudeM,
        targetAltitudeM = patternAltitudeM,
        speedMps = PilotConstants.CLIMB_SPEED_MPS,
        targetSpeedMps = PilotConstants.CLIMB_SPEED_MPS,
        phase = PilotPhase.Downwind,
        route = PilotRoute.Airborne(
            waypoints = NonEmptyList(TestPoints.base, listOf(TestPoints.finalPt, TestPoints.thresholdA)),
            targetAltitudeM = patternAltitudeM,
            arrivalPhase = PilotPhase.LandingRoll,
        ),
        pilotGoal = PilotGoal.ARRIVE,
        humanPiloted = false,
    )

    private val groundController = ControllerSpec(
        id = groundControllerId,
        role = RoleName.GROUND,
        aerodromeId = aerodromeId,
        frequency = groundFrequency,
        responsibilities = emptySet(),
    )

    private val towerController = ControllerSpec(
        id = towerControllerId,
        role = RoleName.TOWER,
        aerodromeId = aerodromeId,
        frequency = towerFrequency,
        responsibilities = emptySet(),
    )

    private val approachController = ControllerSpec(
        id = approachControllerId,
        role = RoleName.APPROACH,
        aerodromeId = aerodromeId,
        frequency = approachFrequency,
        responsibilities = setOf(alphaId),
    )

    private fun runScenario(): SimState = runUntil(
        initial = SimState.initial(
            seed = 42L,
            world = world,
            worldIndex = worldIndex,
            controllers = listOf(groundController, towerController, approachController),
            weatherByAerodrome = world.aerodromes.keys.associateWith {
                WeatherObservation(wind = null, qnh = null, visibility = null)
            },
        ).getOrElse { error("ArrivalVertical setup invalid: $it") },
        initialEvents = listOf(
            SimEvent.PhysicsTick(SimTime.ZERO),
            SimEvent.Spawn(SimTime.ZERO, alpha()),
            SimEvent.ControllerCycle(SimTime.ZERO, groundControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, towerControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, approachControllerId),
        ),
        until = SimTime.ofSeconds(600),
    )

    @Test
    fun `approach→tower→ground — aircraft lands, vacates, and parks`() {
        val run1 = runScenario()
        val run2 = runScenario()

        assertEquals(run1, run2, "Two runs with the same seed must produce identical state")

        val alpha = run1.aircraft[alphaId]!!
        assertEquals(
            PilotPhase.Parked, alpha.phase,
            "Aircraft must be parked at the stand at the end of the scenario",
        )
        assertEquals(
            TestPoints.stand, alpha.positionPoint,
            "Aircraft must have reached the stand waypoint",
        )
        assertEquals(
            0.0, alpha.altitudeM,
            "Aircraft must be on the ground (altitude 0) after touchdown and taxi",
        )
        assertEquals(
            0.0, alpha.speedMps,
            "Aircraft must be stopped at the stand",
        )

        // Responsibility transfer assertions — the 4e-B handoff plumbing.
        val app = run1.controllers[approachControllerId]!!
        val twr = run1.controllers[towerControllerId]!!
        assertTrue(alphaId !in app.responsibilities, "Approach released ALPHA at the downwind handoff")
        assertTrue(alphaId !in twr.responsibilities, "Tower released ALPHA after vacating the runway")

        // Comms quiescence — no stale transmissions or undelivered readbacks.
        assertTrue(
            run1.inFlightTransmissions.isEmpty(),
            "All transmissions must have cleared the air by the end of the scenario",
        )
    }
}
