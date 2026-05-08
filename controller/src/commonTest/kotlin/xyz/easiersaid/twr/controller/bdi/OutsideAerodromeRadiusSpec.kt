package xyz.easiersaid.twr.controller.bdi

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.controller.fromTestPoint
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.Meters
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test

/**
 * fn-6.2 (R3): empirical pin for `OutsideAerodromeRadius.evaluate` reading
 * the kinematic [AircraftObservation.coords] field rather than the
 * snap-derived `worldIndex.positions[ac.position]` lookup.
 *
 * Rule-agnostic: tests `OutsideAerodromeRadius` directly, not
 * `DEP-CROSS-AERODROME-RELEASE` / `DEP-RADAR-SERVICE-TERMINATED`. Both call
 * sites pick up identical kinematic semantics; one spec suffices.
 *
 * **Geometry.** ARP proxy is the lex-first runway threshold
 * (`Guard.kt:OutsideAerodromeRadius`). Tests place that threshold at the
 * origin so the ring radius is the only geometric variable. Ring is
 * `Meters.fromNauticalMiles(12)` = 22 224 m exactly.
 *
 * **Why `coordsOverride` is the load-bearing mechanism.** The
 * `fromTestPoint` helper defaults `coords` to `worldIndex.positions[point]`
 * (the snap point). To exercise the kinematic-vs-snap divergence, the test
 * threads a `coordsOverride` that deliberately differs from the snap
 * derivation. Without override, `coords == positions[point]` and the test
 * could not distinguish the two read paths.
 */
class OutsideAerodromeRadiusSpec {

    private val aircraft = AircraftId("OE-ABC")
    private val aerodromeId = AerodromeId("LOWG")
    private val runwayId = RunwayId("16C")
    private val thresholdPoint = PointId("THR")
    private val depEnd = PointId("DEP")

    /** ARP proxy = lex-first runway threshold; placed at origin. */
    private val arpPosition = Position(xMeters = 0.0, yMeters = 0.0)

    /** 12 NM = 22 224 m exactly. */
    private val ringRadiusMeters = 22_224.0

    private val worldIndex = WorldIndex(
        positions = mapOf(
            thresholdPoint to arpPosition,
            depEnd to Position(xMeters = 3_000.0, yMeters = 0.0),
        ),
    )

    private val aerodrome = Aerodrome(
        icao = aerodromeId,
        elevation = Feet(0),
        magneticVariation = Degrees(0.0),
        transitionAltitude = Level.AltitudeFeet.unsafe(5000),
        runways = mapOf(
            runwayId to Runway(
                id = runwayId,
                path = Path(listOf(thresholdPoint, depEnd)),
                threshold = thresholdPoint,
            ),
        ),
    )

    private val populatedWorld = AviationWorld(aerodromes = mapOf(aerodromeId to aerodrome))

    private val commitment = Commitment(
        aircraft = aircraft,
        kind = CommitmentKind.TOWER_DEPARTURE,
        stage = TowerDepartureStage.AwaitTakeoffObserved,
        formedAt = SimTime.ZERO,
    )

    private fun ctxWith(world: AviationWorld, ac: AircraftObservation): OperatorContext = OperatorContext(
        view = ControllerView(
            time = SimTime.ZERO,
            controllerId = ControllerId("TEST_TWR"),
            role = RoleName.TOWER,
            aerodromeId = aerodromeId,
            responsibilities = setOf(aircraft),
            aircraft = mapOf(aircraft to ac),
            runways = emptyMap(),
            activeClearances = emptyMap(),
            receivedMessages = emptyList(),
            weather = null,
            worldIndex = worldIndex,
        ),
        beliefs = BeliefState.EMPTY,
        events = emptyList(),
        world = world,
    )

    @Test
    fun `evaluate returns false 100m inside the configured ring`() {
        // 100 m inside the 12 NM ring — coords differ from the threshold
        // snap (placed at depEnd by the test's positionPoint choice) on
        // purpose: the override is the kinematic read the rule must consult.
        val insideRing = Position(xMeters = ringRadiusMeters - 100.0, yMeters = 0.0)
        val ac = AircraftObservation.fromTestPoint(
            point = depEnd,
            worldIndex = worldIndex,
            id = aircraft,
            callsign = Callsign("OEABC"),
            coordsOverride = insideRing,
        )
        val guard = OutsideAerodromeRadius(Meters.fromNauticalMiles(12))
        check(!guard.evaluate(ac, commitment, ctxWith(populatedWorld, ac))) {
            "OutsideAerodromeRadius should be false at 22 124 m (100 m inside the 22 224 m ring)"
        }
    }

    @Test
    fun `evaluate returns true 100m outside the configured ring`() {
        // 100 m outside the 12 NM ring — coords clearly past the boundary.
        val outsideRing = Position(xMeters = ringRadiusMeters + 100.0, yMeters = 0.0)
        val ac = AircraftObservation.fromTestPoint(
            point = depEnd,
            worldIndex = worldIndex,
            id = aircraft,
            callsign = Callsign("OEABC"),
            coordsOverride = outsideRing,
        )
        val guard = OutsideAerodromeRadius(Meters.fromNauticalMiles(12))
        check(guard.evaluate(ac, commitment, ctxWith(populatedWorld, ac))) {
            "OutsideAerodromeRadius should be true at 22 324 m (100 m outside the 22 224 m ring)"
        }
    }

    @Test
    fun `evaluate returns false when ARP cannot be resolved`() {
        // World has no aerodrome at the controller's aerodromeId — the
        // first `?: return false` defensive fall-through fires. Pins the
        // failure-closed semantics: if we can't resolve the ARP, we do not
        // release (under-fires the boundary release rather than firing
        // inside controlled airspace, which would be regulatorily wrong).
        val outsideRing = Position(xMeters = ringRadiusMeters + 100.0, yMeters = 0.0)
        val ac = AircraftObservation.fromTestPoint(
            point = depEnd,
            worldIndex = worldIndex,
            id = aircraft,
            callsign = Callsign("OEABC"),
            coordsOverride = outsideRing,
        )
        val guard = OutsideAerodromeRadius(Meters.fromNauticalMiles(12))
        val emptyWorld = AviationWorld() // no aerodromes
        check(!guard.evaluate(ac, commitment, ctxWith(emptyWorld, ac))) {
            "OutsideAerodromeRadius must fail closed when the aerodrome cannot be resolved"
        }
    }
}
