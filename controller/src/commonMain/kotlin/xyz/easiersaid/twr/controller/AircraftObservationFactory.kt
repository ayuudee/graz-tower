package xyz.easiersaid.twr.controller

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
 */
fun AircraftObservation.Companion.from(
    id: AircraftId,
    callsign: Callsign,
    position: PointId,
    altitude: Level?,
    groundSpeed: Knots?,
    onGround: Boolean,
    wakeCategory: WakeCategory?,
    icaoTypeDesignator: IcaoTypeDesignator?,
    worldIndex: WorldIndex,
): AircraftObservation {
    val entities = worldIndex.entitiesByPoint[position] ?: emptySet()
    return AircraftObservation(
        id = id,
        callsign = callsign,
        position = position,
        entities = entities,
        altitude = altitude,
        speed = null,
        groundSpeed = groundSpeed,
        onGround = onGround,
        wakeCategory = wakeCategory,
        icaoTypeDesignator = icaoTypeDesignator,
    )
}
