package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.PilotGoal
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
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AuthorityEntityType
import xyz.easiersaid.twr.protocol.AuthorityOperation
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.StandId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Slice 4e-A acceptance bar — the first airborne vertical.
 *
 * End-to-end: an AI aircraft spawns at a stand with pilot goal DEPART and no
 * route. Three controllers (ground, tower, approach) must:
 *
 *   1. Ground decides to taxi the aircraft to the holding point.
 *   2. Ground hands off to tower at the holding point (ContactFrequency →
 *      responsibility transfer).
 *   3. Tower issues LineUpAndWait; aircraft taxis onto the runway threshold.
 *   4. Tower issues ClearedForTakeoff; aircraft accelerates, rotates, climbs.
 *   5. Tower observes the aircraft airborne on the upwind → crosswind legs and
 *      hands off to approach.
 *
 * The assertion is on the final state: the aircraft is airborne in crosswind,
 * at circuit altitude, and now owned by the approach controller — none of
 * which is possible without every 4e-A piece working (new phases, departure
 * route, vertical integration, LineUp/ClearedForTakeoff/ContactFrequency
 * effects, per-role handoff).
 */
class DepartureVerticalTest {

    private val aerodromeId = AerodromeId("TEST")
    private val runwayId = RunwayId("09")
    private val standId = StandId("STAND-1")

    private object TestPoints {
        val stand: PointId = PointId("STAND-1")
        val hold: PointId = PointId("HOLD-SHORT-09")
        val thresholdA: PointId = PointId("THR-09")
        val thresholdB: PointId = PointId("THR-27")
        val upwind: PointId = PointId("UPWIND-1")
        val crosswind: PointId = PointId("CROSSWIND-1")

        val standPos: Position = Position(xMeters = 0.0, yMeters = 0.0)
        val holdPos: Position = Position(xMeters = 100.0, yMeters = 0.0)
        val thrAPos: Position = Position(xMeters = 100.0, yMeters = 20.0)
        val thrBPos: Position = Position(xMeters = 1000.0, yMeters = 20.0)
        val upwindPos: Position = Position(xMeters = 1500.0, yMeters = 20.0)
        val crosswindPos: Position = Position(xMeters = 1500.0, yMeters = 500.0)
    }

    private val groundControllerId = ControllerId("GND")
    private val towerControllerId = ControllerId("TWR")
    private val approachControllerId = ControllerId("APP")
    private val alphaId = AircraftId("ALPHA")

    private val groundFrequency = Frequency.unsafe("121.800")
    private val towerFrequency = Frequency.unsafe("118.100")
    private val approachFrequency = Frequency.unsafe("125.300")

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
                    ),
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
            TestPoints.hold to TestPoints.holdPos,
            TestPoints.thresholdA to TestPoints.thrAPos,
            TestPoints.thresholdB to TestPoints.thrBPos,
            TestPoints.upwind to TestPoints.upwindPos,
            TestPoints.crosswind to TestPoints.crosswindPos,
        ),
        adjacency = mapOf(
            TestPoints.stand to setOf(TestPoints.hold),
            TestPoints.hold to setOf(TestPoints.stand, TestPoints.thresholdA),
            TestPoints.thresholdA to setOf(TestPoints.hold, TestPoints.thresholdB),
        ),
        entitiesByPoint = mapOf(
            TestPoints.stand to setOf(EntityRef.StandRef(standId)),
            TestPoints.thresholdA to setOf(EntityRef.RunwayRef(runwayId)),
            TestPoints.thresholdB to setOf(EntityRef.RunwayRef(runwayId)),
        ),
        holdingPointsByRunway = mapOf(runwayId to setOf(TestPoints.hold)),
        circuitLegsByPoint = mapOf(
            TestPoints.upwind to setOf(LegName.UPWIND),
            TestPoints.crosswind to setOf(LegName.CROSSWIND),
        ),
    )

    private fun alpha() = AircraftState(
        id = alphaId,
        callsign = Callsign("ALPHA"),
        position = TestPoints.standPos,
        positionPoint = TestPoints.stand,
        pilotGoal = PilotGoal.DEPART,
        humanPiloted = false,
        route = PilotRoute.None,
        phase = PilotPhase.AtStand,
    )

    private val groundController = ControllerSpec(
        id = groundControllerId,
        role = RoleName.GROUND,
        aerodromeId = aerodromeId,
        frequency = groundFrequency,
        responsibilities = setOf(alphaId),
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
        responsibilities = emptySet(),
    )

    private fun runScenario(): SimState = runUntil(
        initial = SimState.initial(
            seed = 42L,
            world = world,
            worldIndex = worldIndex,
            controllers = listOf(groundController, towerController, approachController),
        ),
        initialEvents = listOf(
            SimEvent.PhysicsTick(SimTime.ZERO),
            SimEvent.Spawn(SimTime.ZERO, alpha()),
            SimEvent.ControllerCycle(SimTime.ZERO, groundControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, towerControllerId),
            SimEvent.ControllerCycle(SimTime.ZERO, approachControllerId),
        ),
        until = SimTime.ofSeconds(300),
    )

    @Test
    fun `ground→tower→approach — aircraft climbs out to crosswind and is owned by approach`() {
        val run1 = runScenario()
        val run2 = runScenario()

        assertEquals(run1, run2, "Two runs with the same seed must produce identical state")

        val alpha = run1.aircraft[alphaId]!!
        assertEquals(
            PilotPhase.Crosswind, alpha.phase,
            "Aircraft must settle in Crosswind after the upwind/crosswind climb-out",
        )
        assertTrue(
            alpha.altitudeM > 100.0,
            "Aircraft must be well airborne (>100 m) by the time crosswind is reached; was ${alpha.altitudeM}",
        )
        assertEquals(
            TestPoints.crosswind, alpha.positionPoint,
            "Graph-level position snaps to the final crosswind waypoint on arrival",
        )

        // Responsibility transfer assertions — the 4e-A handoff plumbing.
        val gnd = run1.controllers[groundControllerId]!!
        val twr = run1.controllers[towerControllerId]!!
        val app = run1.controllers[approachControllerId]!!
        assertTrue(alphaId !in gnd.responsibilities, "Ground released ALPHA at holding-point handoff")
        assertTrue(alphaId !in twr.responsibilities, "Tower released ALPHA at upwind/crosswind handoff")
        assertTrue(alphaId in app.responsibilities, "Approach owns ALPHA after the tower handoff")

        // Comms quiescence — no stale transmissions or undelivered readbacks.
        assertTrue(
            run1.inFlightTransmissions.isEmpty(),
            "All transmissions must have cleared the air by the end of the scenario",
        )
    }
}
