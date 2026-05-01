package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId

/**
 * Pass 5 (D-AUDIT.1 closure): the **only public construction path** for
 * [AircraftObservation]. The primary constructor is `internal` to enforce
 * that every observation goes through this factory, which derives entities
 * controller-side from the world index — never copied from a sim-injected
 * field.
 *
 * Sim wiring code (`:sim/ControllerWiring.kt::buildControllerView`) calls
 * `AircraftObservation.from(...)`. Sim cannot construct an observation
 * directly; the architectural firewall is enforced at the type level.
 *
 * The factory is declared as a `Companion` extension to keep the
 * call surface natural (`AircraftObservation.from(...)`) without needing
 * the data class itself to host the function.
 */
fun AircraftObservation.Companion.from(
    id: AircraftId,
    callsign: Callsign,
    position: PointId,
    altitude: Level?,
    groundSpeed: Knots?,
    onGround: Boolean,
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
    )
}
