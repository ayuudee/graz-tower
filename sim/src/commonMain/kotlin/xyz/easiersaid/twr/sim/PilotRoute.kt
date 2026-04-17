package xyz.easiersaid.twr.sim

import arrow.core.NonEmptyList
import xyz.easiersaid.twr.protocol.PointId

/**
 * What the pilot is currently following.
 *
 * Sealed sum, not a flat waypoint list, because TWR1's single `List<NodeId>`
 * needed an invisible "null the route to enter circuit mode" switch — the
 * navigation mode was implicit in the segment sequence. Here the mode is the
 * type: [Ground] means "follow this taxi path", a future `Circuit` variant
 * will mean "follow this pattern geometry", and [None] means "no active
 * navigation — sit still".
 *
 * Slice 4b ships only [None] and [Ground]. Air-route, circuit, and
 * approach-track variants are added in the slices that need them.
 */
sealed interface PilotRoute {

    data object None : PilotRoute

    /**
     * A taxi path over the world's path-graph. [waypoints] are the remaining
     * nodes to reach, in order. The pilot pops the head once the aircraft is
     * within [PilotConstants.WAYPOINT_RADIUS_M] of it.
     *
     * [arrivalPhase] is the phase to transition to when the last waypoint is
     * reached — e.g. [PilotPhase.HoldingShort] for a departure taxi to a
     * holding point, [PilotPhase.Parked] for an arrival taxi to a stand. It
     * rides with the route so the pilot does not need to pattern-match on
     * destination entity type to know what to do.
     */
    data class Ground(
        val waypoints: NonEmptyList<PointId>,
        val arrivalPhase: PilotPhase,
    ) : PilotRoute

    /**
     * Airborne route: follow [waypoints] in XY while tracking [targetAltitudeM].
     * The aircraft's phase is updated from the leg metadata of the head waypoint
     * on each pilot tick (see [DefaultPilot]) so that the controller's
     * `OnCircuitLeg` guards see UPWIND / CROSSWIND / DOWNWIND / BASE / FINAL at
     * the right points without the sim guessing direction from geometry.
     *
     * Used for both climb-out (4e-A: upwind → crosswind) and the arrival legs
     * (4e-B: downwind → base → final → landing-roll). [arrivalPhase] is the
     * terminal phase when the last waypoint is reached — [PilotPhase.Crosswind]
     * at the top of the climb for departures, [PilotPhase.LandingRoll] for a
     * full-stop arrival.
     */
    data class Airborne(
        val waypoints: NonEmptyList<PointId>,
        val targetAltitudeM: Double,
        val arrivalPhase: PilotPhase,
    ) : PilotRoute
}
