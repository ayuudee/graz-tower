package xyz.easiersaid.twr.controller.bdi

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.controller.fromTestPoint
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Doctrine
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
 * fn-7 (R6): rule shape moved to `data object` and the threshold lives on
 * [Aerodrome.ctrApproximationRadius] (read at evaluate time) instead of
 * the rule's constructor argument. The first three rows preserve the
 * original 22 224 m (12 NM) test geometry by **explicitly authoring**
 * `ctrApproximationRadius = Meters.fromNauticalMiles(12)` on the test
 * fixture aerodrome — this is a deliberate spec edit to keep the
 * pre-fn-7 rows' invariants intact, not a fixture migration to chase
 * the new field. A 4th row pins the primary-constructor default at
 * `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM` (= 9 260 m) by constructing the
 * fixture without an explicit `ctrApproximationRadius` argument.
 *
 * Rule-agnostic: tests `OutsideAerodromeRadius` directly, not
 * `DEP-CROSS-AERODROME-RELEASE` / `DEP-RADAR-SERVICE-TERMINATED`. Both call
 * sites pick up identical kinematic semantics; one spec suffices.
 *
 * **Geometry.** ARP proxy is the lex-first runway threshold
 * (`Guard.kt:OutsideAerodromeRadius`). Tests place that threshold at the
 * origin so the ring radius is the only geometric variable. The first
 * three rows ring is `Meters.fromNauticalMiles(12)` = 22 224 m exactly;
 * the fourth row's ring is `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`
 * = `Meters.fromNauticalMiles(5)` = 9 260 m exactly.
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

    /** 12 NM = 22 224 m exactly. First three rows. */
    private val ringRadiusMeters = 22_224.0

    /**
     * 5 NM = 9 260 m exactly. Fourth-row ring — the
     * `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM` primary-constructor default.
     */
    private val floorRingRadiusMeters = 9_260.0

    private val worldIndex = WorldIndex(
        positions = mapOf(
            thresholdPoint to arpPosition,
            depEnd to Position(xMeters = 3_000.0, yMeters = 0.0),
        ),
    )

    /**
     * Test fixture aerodrome — first three rows preserve the pre-fn-7
     * 22 224 m ring by explicitly authoring 12 NM. (Without this the
     * primary-constructor default kicks in at 5 NM and the rows'
     * geometric invariants would silently shift.)
     */
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
        ctrApproximationRadius = Meters.fromNauticalMiles(12),
    )

    /**
     * Fourth-row fixture — no explicit `ctrApproximationRadius` argument,
     * so the primary-constructor default ([Doctrine.IcaoAnnex11.CTR_FLOOR_5NM])
     * resolves the ring at 5 NM (9 260 m). Pins the default-resolution path.
     */
    private val aerodromeAtIcaoFloor = Aerodrome(
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
        // No ctrApproximationRadius — primary-constructor default
        // (Doctrine.IcaoAnnex11.CTR_FLOOR_5NM) resolves to 5 NM.
    )

    private val populatedWorld = AviationWorld(aerodromes = mapOf(aerodromeId to aerodrome))
    private val floorWorld = AviationWorld(aerodromes = mapOf(aerodromeId to aerodromeAtIcaoFloor))

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
        check(!OutsideAerodromeRadius.evaluate(ac, commitment, ctxWith(populatedWorld, ac))) {
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
        check(OutsideAerodromeRadius.evaluate(ac, commitment, ctxWith(populatedWorld, ac))) {
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
        val emptyWorld = AviationWorld() // no aerodromes
        check(!OutsideAerodromeRadius.evaluate(ac, commitment, ctxWith(emptyWorld, ac))) {
            "OutsideAerodromeRadius must fail closed when the aerodrome cannot be resolved"
        }
    }

    @Test
    fun `evaluate uses the ICAO Annex 11 5 NM floor when no per-aerodrome radius is authored`() {
        // Pins the primary-constructor default
        // (Doctrine.IcaoAnnex11.CTR_FLOOR_5NM = 5 NM = 9 260 m). Build
        // coords just past the 5 NM ring; the rule must fire.
        check(Doctrine.IcaoAnnex11.CTR_FLOOR_5NM.value == floorRingRadiusMeters) {
            "Test pin assumption: CTR_FLOOR_5NM must be exactly 9 260 m " +
                "(was ${Doctrine.IcaoAnnex11.CTR_FLOOR_5NM.value})"
        }
        val outsideFloorRing = Position(xMeters = floorRingRadiusMeters + 100.0, yMeters = 0.0)
        val ac = AircraftObservation.fromTestPoint(
            point = depEnd,
            worldIndex = worldIndex,
            id = aircraft,
            callsign = Callsign("OEABC"),
            coordsOverride = outsideFloorRing,
        )
        check(OutsideAerodromeRadius.evaluate(ac, commitment, ctxWith(floorWorld, ac))) {
            "OutsideAerodromeRadius should fire at 9 360 m (100 m outside the 5 NM " +
                "default ring) when the aerodrome carries no per-aerodrome radius authoring " +
                "(primary-constructor default = Doctrine.IcaoAnnex11.CTR_FLOOR_5NM)"
        }

        // And conversely — inside the 5 NM default ring, the rule must NOT
        // fire (would have fired under the old 12 NM hardcode if the
        // default-resolution path were broken).
        val insideFloorRing = Position(xMeters = floorRingRadiusMeters - 100.0, yMeters = 0.0)
        val acInside = AircraftObservation.fromTestPoint(
            point = depEnd,
            worldIndex = worldIndex,
            id = aircraft,
            callsign = Callsign("OEABC"),
            coordsOverride = insideFloorRing,
        )
        check(!OutsideAerodromeRadius.evaluate(acInside, commitment, ctxWith(floorWorld, acInside))) {
            "OutsideAerodromeRadius should be false at 9 160 m (100 m inside the 5 NM " +
                "default ring); a regression to the pre-fn-7 12 NM hardcode would over-fire here"
        }
    }
}
