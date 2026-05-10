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
 * Pass 5 (D-AUDIT.1 closure): the **only public construction path** for
 * [AircraftObservation]. The primary constructor is `internal` to enforce
 * that every observation goes through this factory, which derives entities
 * controller-side from the world index — never copied from a sim-injected
 * field.
 *
 * Pass 10 (D-AUDIT.4): factory now propagates [wakeCategory] (sensor-
 * derived) and [icaoTypeDesignator] (strip-derived). Pre-Pass-10 the
 * factory dropped wakeCategory at the boundary even though `SensorReading`
 * carried it — that bug is fixed here.
 *
 * fn-6.1 (R1): factory now threads [coords] (kinematic position from
 * primary surveillance) alongside the existing snap-projected [position].
 * See [AircraftObservation]'s "Position vs. coords" KDoc paragraph.
 */
data class AircraftObservationInput(
    val id: AircraftId,
    val callsign: Callsign,
    val position: PointId,
    val coords: Position,
    val altitude: Level?,
    val groundSpeed: Knots?,
    val onGround: Boolean,
    val wakeCategory: WakeCategory?,
    val icaoTypeDesignator: IcaoTypeDesignator?,
)

fun AircraftObservation.Companion.from(
    input: AircraftObservationInput,
    worldIndex: WorldIndex,
): AircraftObservation {
    val entities = worldIndex.entitiesByPoint[input.position] ?: emptySet()
    return AircraftObservation(
        id = input.id,
        callsign = input.callsign,
        position = input.position,
        coords = input.coords,
        entities = entities,
        altitude = input.altitude,
        speed = null,
        groundSpeed = input.groundSpeed,
        onGround = input.onGround,
        wakeCategory = input.wakeCategory,
        icaoTypeDesignator = input.icaoTypeDesignator,
    )
}
