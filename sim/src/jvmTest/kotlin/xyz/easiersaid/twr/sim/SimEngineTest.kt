package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.WindReport
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
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
 * Slice 4c acceptance bar.
 *
 * A ground controller is given responsibility for an AI aircraft sitting on
 * a stand with no route, and must, on its own, decide-taxi-apply-pilot-drive
 * the aircraft to the runway holding point — ending in
 * [PilotPhase.HoldingShort]. Every piece of the slice is load-bearing:
 *
 *   - Spawn + Pilot scheduling (from 4b) — still works.
 *   - [handleControllerTick] actually invoking [controllerDecide] and
 *     threading beliefs forward (the 4a/4b stub did neither).
 *   - [buildControllerView] projecting a usable view from ground-truth state.
 *   - [applyControllerOutputs] translating a [TaxiTo] into a [PilotRoute.Ground].
 *   - [advanceKinematics] snapping `positionPoint` so the controller's
 *     point-indexed guards see arrival at the holding point.
 *
 * Running the scenario twice with the same seed and asserting identical
 * terminal state is the determinism check.
 */
class SimEngineTest {

    private object TestPoints {
        val stand: PointId = PointId("STAND-1")
        val hold: PointId = PointId("HOLD-SHORT-09")
        val thresholdA: PointId = PointId("THR-09")
        val thresholdB: PointId = PointId("THR-27")
        val standPos: Position = Position(xMeters = 0.0, yMeters = 0.0)
        val holdPos: Position = Position(xMeters = 100.0, yMeters = 0.0)
        val thrAPos: Position = Position(xMeters = 100.0, yMeters = 20.0)
        val thrBPos: Position = Position(xMeters = 1000.0, yMeters = 20.0)
    }

    private val aerodromeId = AerodromeId("TEST")
    private val runwayId = RunwayId("09")
    private val standId = StandId("STAND-1")
    private val groundControllerId = ControllerId("GND")
    private val alphaId = AircraftId("ALPHA")

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
            ),
        ),
    )

    private val worldIndex = WorldIndex(
        positions = mapOf(
            TestPoints.stand to TestPoints.standPos,
            TestPoints.hold to TestPoints.holdPos,
            TestPoints.thresholdA to TestPoints.thrAPos,
            TestPoints.thresholdB to TestPoints.thrBPos,
        ),
        adjacency = mapOf(
            TestPoints.stand to setOf(TestPoints.hold),
            TestPoints.hold to setOf(TestPoints.stand),
        ),
        entitiesByPoint = mapOf(
            TestPoints.stand to setOf(EntityRef.StandRef(standId)),
            TestPoints.thresholdA to setOf(EntityRef.RunwayRef(runwayId)),
            TestPoints.thresholdB to setOf(EntityRef.RunwayRef(runwayId)),
        ),
        holdingPointsByRunway = mapOf(runwayId to setOf(TestPoints.hold)),
    )

    private fun alpha() = AircraftState(
        id = alphaId,
        callsign = Callsign("ALPHA"),
        position = TestPoints.standPos,
        positionPoint = TestPoints.stand,
        pilotGoal = PilotGoal.DEPART,
        humanPiloted = false,
        // No route at spawn — the controller must issue TaxiTo for the
        // aircraft to move. This is the 4c assertion.
        route = PilotRoute.None,
        phase = PilotPhase.AtStand,
    )

    private val groundController = ControllerSpec(
        id = groundControllerId,
        role = RoleName.GROUND,
        aerodromeId = aerodromeId,
        frequency = Frequency.unsafe("121.800"),
        responsibilities = setOf(alphaId),
    )

    private fun runScenario(): SimState = runUntil(
        initial = SimState.initial(
            seed = 42L,
            world = world,
            worldIndex = worldIndex,
            controllers = listOf(groundController),
            weatherByAerodrome = world.aerodromes.keys.associateWith {
                WeatherObservation(wind = WindReport.Available(xyz.easiersaid.twr.protocol.Wind.unsafe(directionDegrees = 90, speedKnots = 5)), qnh = null, visibility = null)
            },
        ).getOrElse { error("SimEngineTest setup invalid: $it") },
        initialEvents = listOf(
            SimEvent.PhysicsTick(SimTime.ZERO),
            SimEvent.Spawn(SimTime.ZERO, alpha()),
            SimEvent.ControllerCycle(SimTime.ZERO, groundControllerId),
        ),
        until = SimTime.ofSeconds(120),
    )

    @Test
    fun `controller-driven taxi — ground issues TaxiTo and aircraft reaches holding short`() {
        val run1 = runScenario()
        val run2 = runScenario()

        assertEquals(run1, run2, "Two runs with the same seed must produce identical state")

        val alpha = run1.aircraft[alphaId]!!
        assertEquals(PilotPhase.HoldingShort, alpha.phase,
            "Aircraft must transition to HoldingShort on reaching the holding point")
        assertEquals(PilotRoute.None, alpha.route,
            "Route must be cleared once the arrival phase is reached")
        assertEquals(TestPoints.hold, alpha.positionPoint,
            "Graph-level position must snap to the holding point when the aircraft arrives")
        assertEquals(TestPoints.holdPos.xMeters, alpha.position.xMeters, 1e-9,
            "Aircraft must be at the holding point's x-coordinate")
        assertEquals(TestPoints.holdPos.yMeters, alpha.position.yMeters, 1e-9,
            "Aircraft must be at the holding point's y-coordinate")
        assertEquals(0.0, alpha.targetSpeedMps,
            "Aircraft must have commanded zero speed in the arrival phase")

        // Comms round-trip acceptance: the controller heard the readback and
        // cleared its pending list. Inbox is drained in the cycle that sees it.
        val groundBeliefs = run1.beliefs[groundControllerId]
        assertTrue(
            groundBeliefs?.pendingReadbacks?.get(alphaId).isNullOrEmpty(),
            "Ground controller must have matched the pilot's readback and cleared pendingReadbacks",
        )
        assertTrue(
            run1.inFlightTransmissions.isEmpty(),
            "All transmissions must have cleared the air by the end of the scenario",
        )
    }
}
