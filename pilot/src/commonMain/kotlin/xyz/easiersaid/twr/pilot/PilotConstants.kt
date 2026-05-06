package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.protocol.SimDuration

/**
 * Globally-uniform cadence and tolerance constants.
 *
 * Pass 10 (D-AUDIT.4): per-type kinematic fields (climb speed, approach
 * speed, etc.) moved to
 * [xyz.easiersaid.twr.protocol.AircraftType.Kinematics].
 *
 * Pass 13 (D-AUDIT.4.D-FOLLOWUP): waypoint capture radius moved to
 * [xyz.easiersaid.twr.protocol.AircraftType.Kinematics.waypointRadiusM]
 * — a jet at 130 m/s × 1 s tick traverses a 130 m physics step, so the
 * capture radius needs to scale with cruise speed. Surviving constants
 * here are the ones genuinely uniform across aircraft types
 * (sensor-threshold altitudes, pilot decision cadence — none of which a
 * B738 needs to differ from a C172).
 */
object PilotConstants {
    /** Altitude at which the aircraft is considered airborne for phase transitions. */
    const val AIRBORNE_ALTITUDE_THRESHOLD_M: Double = 1.0

    /**
     * Altitude tolerance below which a landing aircraft is considered to have
     * touched down. Guards the airborne-to-ground phase handover.
     */
    const val GROUND_TOLERANCE_M: Double = 1.0

    /** How often the pilot agent runs its decision cycle. */
    val PILOT_DECISION_INTERVAL: SimDuration = SimDuration.ofMillis(1000)
}
