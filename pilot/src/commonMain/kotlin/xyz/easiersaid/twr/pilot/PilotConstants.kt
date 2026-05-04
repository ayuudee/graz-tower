package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.protocol.SimDuration

/**
 * Globally-uniform cadence and tolerance constants.
 *
 * Pass 10 (D-AUDIT.4): per-type kinematic fields (climb speed, approach
 * speed, etc.) moved to
 * [xyz.easiersaid.twr.protocol.AircraftType.Kinematics]. Surviving
 * constants here are the ones genuinely uniform across aircraft types
 * (geometric capture radius, sensor-threshold altitudes, pilot decision
 * cadence — none of which a B738 needs to differ from a C172).
 */
object PilotConstants {
    /**
     * How close the aircraft must be to a waypoint before the pilot pops it.
     * Geometric, not aircraft-specific.
     */
    const val WAYPOINT_RADIUS_M: Double = 5.0

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
