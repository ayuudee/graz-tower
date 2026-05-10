package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.IcaoTypeDesignator
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.WakeCategory

/**
 * fn-6.1 — co-located test helper that derives [AircraftObservation.coords]
 * from the world index, so test fixtures cannot diverge between the snap
 * field [AircraftObservation.position] and the kinematic field
 * [AircraftObservation.coords].
 *
 * **Why this exists.** Direct construction with a placeholder
 * `Position(0.0, 0.0)` would let `coords` drift far from the ARP-anchored
 * snap point, making geometric guards (e.g. `OutsideAerodromeRadius`) fire
 * spuriously where the production-side `worldIndex.positions[ac.position]`
 * lookup previously returned false on unknown PointIds. Fixture-level
 * mitigation per the fn-6 deferment register
 * (`D-PASS-fn6-snap-derived` covers production divergence in a future pass).
 *
 * **Scope.** Test-only helper. Lives in `commonTest`; do NOT add to
 * `commonMain`. Production callers go through
 * [AircraftObservation.Companion.from] with both `coords` and `position` as
 * free arguments.
 *
 * **Sentinel exemption.** The `FirewallObservationTest` canonical-constructor
 * allowlist deliberately uses a recognisable sentinel (not derived from a
 * world index) and so does NOT call this helper.
 */
fun AircraftObservation.Companion.fromTestPoint(
    point: PointId,
    worldIndex: WorldIndex,
    id: AircraftId = AircraftId("OE-ABC"),
    callsign: Callsign = Callsign("OEABC"),
    altitude: Level? = null,
    groundSpeed: Knots? = null,
    onGround: Boolean = false,
    wakeCategory: WakeCategory? = null,
    icaoTypeDesignator: IcaoTypeDesignator? = null,
    coordsOverride: Position? = null,
): AircraftObservation {
    val coords = coordsOverride
        ?: worldIndex.positions[point]
        ?: error(
            "AircraftObservation.fromTestPoint: point $point not in worldIndex.positions; " +
                "either add the point to the index or pass coordsOverride if a divergent " +
                "kinematic position is intentional (document why inline).",
        )
    return AircraftObservation.from(
        id = id,
        callsign = callsign,
        position = point,
        coords = coords,
        altitude = altitude,
        groundSpeed = groundSpeed,
        onGround = onGround,
        wakeCategory = wakeCategory,
        icaoTypeDesignator = icaoTypeDesignator,
        worldIndex = worldIndex,
    )
}
