package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.SimDuration

/**
 * Kinematic and decision-cadence constants for the default pilot agent.
 *
 * Kept in one place so the order-of-magnitude tuning is visible at a glance.
 * These are the "reasonable defaults" for a light GA aircraft; per-type
 * overrides land with the slice that introduces aircraft-type performance.
 */
object PilotConstants {
    /**
     * How close the aircraft must be to a waypoint before the pilot pops it.
     * Equal to roughly one taxi-speed stride at the pilot decision cadence —
     * big enough that we don't overshoot, small enough that "arrived" is
     * spatially meaningful.
     */
    const val WAYPOINT_RADIUS_M: Double = 5.0

    /**
     * Taxi target speed — ~20 knots, typical for a light GA aircraft on a
     * straight taxiway.
     */
    const val TAXI_TARGET_SPEED_MPS: Double = 10.0

    /**
     * Rotation speed — speed at which a takeoff roll transitions to airborne
     * climb. ~55 knots for light GA. 4e-A is not aerodynamic; the pilot
     * switches phase when ground speed crosses this threshold.
     */
    const val ROTATION_SPEED_MPS: Double = 28.0

    /**
     * Climb-out true airspeed — sustained airborne cruise through upwind,
     * crosswind, downwind. ~80 knots. Headings are not integrated in 4e-A;
     * the aircraft travels toward the head waypoint at this speed.
     */
    const val CLIMB_SPEED_MPS: Double = 40.0

    /**
     * Approach true airspeed — commanded on base and final so the aircraft
     * descends and slows ahead of the runway rather than punching straight
     * through the threshold at cruise speed. ~65 knots for light GA.
     */
    const val APPROACH_SPEED_MPS: Double = 33.0

    /**
     * Vertical rate during climb — ~600 ft/min. Scalar target; altitude is
     * integrated to [AircraftState.targetAltitudeM] at this rate.
     */
    const val CLIMB_RATE_MPS: Double = 3.0

    /** Altitude at which the aircraft is considered airborne for phase transitions. */
    const val AIRBORNE_ALTITUDE_THRESHOLD_M: Double = 1.0

    /**
     * Altitude tolerance below which a landing aircraft is considered to have
     * touched down. Guards the airborne-to-ground phase handover so a pilot
     * reaching the final waypoint at pattern altitude can't skip the descent
     * and flip directly into [PilotPhase.LandingRoll].
     */
    const val GROUND_TOLERANCE_M: Double = 1.0

    /** How often the pilot agent runs its decision cycle. */
    val PILOT_DECISION_INTERVAL: SimDuration = SimDuration.ofMillis(1000)
}
