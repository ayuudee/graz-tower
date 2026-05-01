package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId

/**
 * Sim-side projection of an [AircraftState] into the fields a controller's
 * radar/visual systems can actually observe.
 *
 * Together with [FlightStrip], [SensorReading] is the firewall between pilot
 * and ATC. The architectural rule is: **no [AircraftState] field flows into
 * a controller-side decision except by passing through [SensorReading],
 * [FlightStrip], or a radio-derived [xyz.easiersaid.twr.controller.observe.ControllerEvent]**.
 *
 * Adding a field here MUST correspond to a real-world sensor or visual cue
 * (radar position, secondary surveillance altitude, surface-movement radar,
 * tower visual, etc.). Anything that requires reading the pilot's mind
 * belongs on the controller's belief state, populated from a typed event.
 *
 * The single production site is [toSensorReading]. The architectural test
 * `FirewallSensorProducerTest` enforces this — adding another producer
 * fails the build.
 */
data class SensorReading(
    val id: AircraftId,
    val callsign: Callsign,
    val position: PointId,
    val altitude: Level.AltitudeFeet?,
    val groundSpeed: Knots?,
    val onGround: Boolean,
)

/**
 * Threshold below which an aircraft is "on the ground" from a sensor
 * standpoint. A real tower's surface-movement radar / visual judgment uses
 * a similar coarse cut. Tunable; tighten when wake-related thresholds are
 * authored.
 */
private const val GROUND_ALTITUDE_THRESHOLD_M: Double = 0.5

/**
 * The sole [AircraftState] → [SensorReading] projection. Reads only
 * sensor-observable members of [AircraftState]:
 *  - identity (id, callsign) — radar transponder
 *  - graph-snapped position (positionPoint) — primary radar / surface radar
 *  - altitudeM, speedMps — Mode S / surface radar
 *
 * It does NOT read [AircraftState.pilotMission], [AircraftState.phase],
 * [AircraftState.pilotGoal], [AircraftState.humanPiloted], or any other
 * pilot-internal state. The architectural test `FirewallSensorReadingTest`
 * enforces this.
 *
 * Pass 5 (D-AUDIT.1 closure): does not derive position-derived
 * [xyz.easiersaid.twr.core.world.EntityRef]s. Real radar gives the controller
 * a position; the controller looks up what's at that position via their own
 * world model. Entity derivation now happens controller-side at view
 * construction (`AircraftObservation.from(reading, worldIndex)`).
 */
internal fun AircraftState.toSensorReading(@Suppress("UNUSED_PARAMETER") state: SimState): SensorReading {
    val gsKt = if (speedMps > 0) {
        val kt = (speedMps * 3600.0 / 1852.0).toInt()
        if (kt > 0) Knots.unsafe(kt) else null
    } else null
    return SensorReading(
        id = id,
        callsign = callsign,
        position = positionPoint,
        altitude = toAltitudeFeet(altitudeM),
        groundSpeed = gsKt,
        onGround = isGroundFromPhysics(altitudeM),
    )
}

/**
 * Sensor-only ground detection: altitude near zero. Replaces the previous
 * `isGroundPhase(ac.phase)` which read the pilot-internal phase enum
 * (Climbing, Crosswind, Final…) — a firewall leak.
 */
private fun isGroundFromPhysics(altitudeM: Double): Boolean =
    altitudeM <= GROUND_ALTITUDE_THRESHOLD_M

private const val METRES_PER_FOOT: Double = 0.3048

private fun toAltitudeFeet(altitudeM: Double): Level.AltitudeFeet? {
    if (altitudeM <= 0.0) return null
    val feet = (altitudeM / METRES_PER_FOOT).toInt()
    return Level.AltitudeFeet.unsafe(feet.coerceAtLeast(0))
}
