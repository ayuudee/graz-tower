package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.WakeCategory

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
    /**
     * Kinematic position of the radar return, projected directly from
     * [AircraftState.position] (the sim's continuous Cartesian truth) — the
     * primary-surveillance equivalent the controller's geometric guards
     * (e.g. `OutsideAerodromeRadius`) need.
     *
     * Distinct from [position], which is the same return projected onto the
     * published-fix graph (`AircraftState.positionPoint`) for chart-anchored
     * consumers (route-progress, entity membership). Two fields because
     * airspace-boundary semantics need geometry and graph-progress semantics
     * need fix identity.
     *
     * Doctrine: ICAO Annex 11 §6 / Doc 4444 §8 — surveillance returns are
     * positional. The "snap to nearest published fix" projection is a
     * sim-internal artefact of the world graph, not a real radar feature.
     */
    val coords: Position,
    val altitude: Level.AltitudeFeet?,
    val groundSpeed: Knots?,
    val onGround: Boolean,
    /**
     * Wake turbulence category — the strip-board / Mode S derived facet
     * of [xyz.easiersaid.twr.protocol.AircraftType]. Pass 10 (D-AUDIT.4):
     * populated from `state.type.wakeCategory` for the first time.
     * Stays nullable — `null` means "no surveillance-derived wake"
     * (transponder failure or Mode-S not equipped, modelled in a future
     * pass).
     */
    val wakeCategory: WakeCategory?,
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
        // fn-6.1 (R1): primary-surveillance kinematic position projected
        // directly from the receiver's continuous Cartesian field. Bare
        // identifier `position` resolves to AircraftState.position — the
        // kinematic field — distinct from the existing `positionPoint` snap
        // read above. The FirewallSensorReadingTest forbidden-name list does
        // not include `position` (sensor-observable).
        coords = position,
        altitude = toAltitudeFeet(altitudeM),
        groundSpeed = gsKt,
        onGround = isGroundFromPhysics(altitudeM),
        // Pass 10 (D-AUDIT.4): wake category projected from the aircraft's
        // type. The strip-board / Mode S surface is the radio/sensor
        // analogue — not a pilot-internal field.
        wakeCategory = type.wakeCategory,
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
