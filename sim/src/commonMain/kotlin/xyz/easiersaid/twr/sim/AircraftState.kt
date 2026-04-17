package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId

/**
 * Ground-truth aircraft state as the simulation knows it.
 *
 * Distinct from [xyz.easiersaid.twr.controller.AircraftObservation] (the
 * controller's view) because the sim holds reality and the controller may
 * only have a partial or delayed picture of it.
 *
 * Two position representations are carried side-by-side:
 *   - [position] is the Cartesian ground truth; physics integrates it.
 *   - [positionPoint] is the graph-level "nearest named point" that the
 *     controller sees in an [xyz.easiersaid.twr.controller.AircraftObservation].
 *     Kinematics snaps it forward when the aircraft lands on a waypoint.
 *     Keeping a discrete graph position alongside continuous coordinates is
 *     how the controller's point-indexed guards (AtHoldingPoint, AtStand,
 *     OnRunway) keep working without continuous geometry queries.
 *
 * Kinematics otherwise are the minimum needed for ground operations
 * (slice 4b/4c): scalar current [speedMps] and pilot-commanded
 * [targetSpeedMps]. Headings are not explicit — while on a
 * [PilotRoute.Ground] the aircraft always heads toward the first remaining
 * waypoint. Airborne kinematics (heading, altitude, vertical rate as
 * controllable intent) land with the slice that introduces circuit and
 * approach following.
 */
data class AircraftState(
    val id: AircraftId,
    val callsign: Callsign,
    val position: Position,
    val positionPoint: PointId,
    val speedMps: Double = 0.0,
    val targetSpeedMps: Double = 0.0,
    /** Geometric altitude AGL (the sim's only vertical axis; 4e-A uses metres, scalar). */
    val altitudeM: Double = 0.0,
    /** Target altitude the pilot is climbing/descending toward; 0.0 ⇒ ground. */
    val targetAltitudeM: Double = 0.0,
    val phase: PilotPhase = PilotPhase.AtStand,
    val route: PilotRoute = PilotRoute.None,
    val pilotGoal: PilotGoal,
    val humanPiloted: Boolean = false,
)
