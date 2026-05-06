package xyz.easiersaid.twr.pilot

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import xyz.easiersaid.twr.core.world.AltitudeConstraint
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.SpeedConstraint
import xyz.easiersaid.twr.core.world.WorldIndex

/**
 * What the pilot wants to happen next.
 *
 * Full-state intent (not delta-shaped) — the pilot returns the desired
 * [targetSpeedMps], [phase], and [route], and [step] applies them verbatim.
 * This keeps the decision function total: there's no "leave unchanged"
 * option that can hide a missing case in the pilot's `when`.
 */
data class PilotIntent(
    val targetSpeedMps: Double,
    val phase: PilotPhase,
    val route: PilotRoute,
    /** Commanded altitude; pilot is responsible for holding or climbing toward this value. */
    val targetAltitudeM: Double = 0.0,
)

/**
 * A pilot agent — pure `(view) -> intent`.
 *
 * TWR1 ran pilots inline with the physics tick; TWR2 runs them as their own
 * self-scheduling [SimEvent.PilotDecisionTick] events so radio timing and
 * per-aircraft cadence can diverge from the physics clock.
 */
fun interface PilotAgent {
    fun decide(input: PilotInput): Either<RoutingError, PilotIntent>
}

/**
 * Default AI pilot for slice 4b.
 *
 * Route-following only:
 *   - [PilotPhase.AtStand] + a [PilotRoute.Ground] route → start taxiing.
 *   - [PilotPhase.Taxiing] → advance along the route; on reaching the last
 *     waypoint, transition to the route's [PilotRoute.Ground.arrivalPhase]
 *     and stop.
 *   - Terminal phases → stay put.
 *
 * The pilot, not the physics integrator, is responsible for popping waypoints
 * and transitioning phases. Physics only moves the aircraft toward the route's
 * first waypoint.
 */
object DefaultPilot : PilotAgent {

    override fun decide(input: PilotInput): Either<RoutingError, PilotIntent> {
        val ac = input.aircraft
        return when (ac.phase) {
            PilotPhase.AtStand -> onAtStand(ac).right()
            PilotPhase.Taxiing -> onTaxiing(ac, input.worldIndex)
            PilotPhase.LinedUp -> onLinedUp(ac).right()
            PilotPhase.TakeoffRoll -> onTakeoffRoll(ac, input.worldIndex).right()
            PilotPhase.Climbing, PilotPhase.Crosswind,
            PilotPhase.Downwind, PilotPhase.Base, PilotPhase.Final ->
                onAirborneLeg(ac, input.worldIndex)
            PilotPhase.HoldingShort -> onHoldingShort(ac).right()
            PilotPhase.LandingRoll -> onLandingRoll(ac).right()
            PilotPhase.Vacating -> onVacating(ac, input.worldIndex)
            PilotPhase.ClearOfRunway -> onClearOfRunway(ac).right()
            PilotPhase.Parked -> idle(ac).right()
        }
    }

    private fun onAtStand(ac: AircraftState): PilotIntent = when (val r = ac.route) {
        is PilotRoute.Ground -> PilotIntent(
            targetSpeedMps = ac.type.kinematics.taxiSpeedMps,
            phase = PilotPhase.Taxiing,
            route = r,
            targetAltitudeM = 0.0,
        )
        PilotRoute.None, is PilotRoute.Airborne -> idle(ac)
    }

    /**
     * Holding short of the runway. When tower issues LineUpAndWait the sim
     * writes a fresh [PilotRoute.Ground] to the runway threshold; the pilot
     * has to notice that and start rolling again. No route ⇒ stay put.
     */
    private fun onHoldingShort(ac: AircraftState): PilotIntent = when (val r = ac.route) {
        is PilotRoute.Ground -> PilotIntent(
            targetSpeedMps = ac.type.kinematics.taxiSpeedMps,
            phase = PilotPhase.Taxiing,
            route = r,
            targetAltitudeM = 0.0,
        )
        PilotRoute.None, is PilotRoute.Airborne -> idle(ac)
    }

    private fun onTaxiing(ac: AircraftState, worldIndex: WorldIndex): Either<RoutingError, PilotIntent> {
        val route = ac.route as? PilotRoute.Ground
            ?: return PilotIntent(0.0, PilotPhase.AtStand, PilotRoute.None).right() // defensive reset
        val head = route.waypoints.head
        val headPos = worldIndex.positions[head]
            ?: return RoutingError.WaypointNotInIndex(head).left()

        val dx = headPos.xMeters - ac.position.xMeters
        val dy = headPos.yMeters - ac.position.yMeters
        val dist = StrictMath.hypot(dx, dy)
        if (dist > ac.type.kinematics.waypointRadiusM) {
            // Still en route — keep taxiing toward the same first waypoint.
            return PilotIntent(ac.type.kinematics.taxiSpeedMps, PilotPhase.Taxiing, route).right()
        }

        // Waypoint reached — pop it.
        val remaining = route.waypoints.tail
        return if (remaining.isEmpty()) {
            // Final waypoint reached — settle into the route's arrival phase.
            PilotIntent(0.0, route.arrivalPhase, PilotRoute.None).right()
        } else {
            val nextRoute = PilotRoute.Ground(
                waypoints = NonEmptyList(remaining.first(), remaining.drop(1)),
                arrivalPhase = route.arrivalPhase,
            )
            PilotIntent(ac.type.kinematics.taxiSpeedMps, PilotPhase.Taxiing, nextRoute).right()
        }
    }

    /**
     * Lined up on the runway, awaiting takeoff clearance. Zero speed; the
     * [applyPilotHeardInstruction] layer swaps the route to [PilotRoute.Airborne]
     * and the phase to [PilotPhase.TakeoffRoll] on [ClearedForTakeoff] receipt.
     */
    private fun onLinedUp(ac: AircraftState): PilotIntent =
        PilotIntent(targetSpeedMps = 0.0, phase = PilotPhase.LinedUp, route = ac.route)

    /**
     * Accelerating down the runway. Once speed crosses the rotation threshold
     * the pilot transitions to [PilotPhase.Climbing] and commands the target
     * altitude from the departure route.
     */
    private fun onTakeoffRoll(ac: AircraftState, worldIndex: WorldIndex): PilotIntent {
        val route = ac.route as? PilotRoute.Airborne
            ?: return PilotIntent(0.0, PilotPhase.AtStand, PilotRoute.None)
        if (ac.speedMps >= ac.type.kinematics.rotationSpeedMps) {
            val phase = phaseForAirborneLeg(route.waypoints.head, worldIndex, default = PilotPhase.Climbing)
            return PilotIntent(
                targetSpeedMps = ac.type.kinematics.climbSpeedMps,
                phase = phase,
                route = route,
                targetAltitudeM = route.targetAltitudeM,
            )
        }
        // Still accelerating along the runway centreline.
        return PilotIntent(
            targetSpeedMps = ac.type.kinematics.climbSpeedMps,
            phase = PilotPhase.TakeoffRoll,
            route = route,
            targetAltitudeM = 0.0,
        )
    }

    /**
     * Flying an airborne leg — follow the head waypoint; on arrival pop it and
     * re-derive phase from the next leg's metadata. When the final waypoint is
     * reached, settle into the route's [PilotRoute.Airborne.arrivalPhase].
     *
     * If the terminal arrival phase is a ground phase (e.g. [PilotPhase.LandingRoll]
     * for a full-stop arrival), the transition is gated on altitude: the pilot
     * cannot flip to LandingRoll while still at circuit height. Until the
     * aircraft descends within [PilotConstants.GROUND_TOLERANCE_M] of the
     * ground, the phase stays on Final and the route stays in place so the
     * physics tick keeps descending toward target altitude 0.
     */
    private fun onAirborneLeg(ac: AircraftState, worldIndex: WorldIndex): Either<RoutingError, PilotIntent> {
        // Route finished — hold the airborne arrival phase at circuit altitude.
        // Without this, the next tick would flip to AtStand because the sealed
        // `when` in decide() has no "airborne idle" branch of its own.
        val route = ac.route as? PilotRoute.Airborne ?: return PilotIntent(
            targetSpeedMps = airborneCruiseSpeed(ac.phase, ac.type.kinematics),
            phase = ac.phase,
            route = PilotRoute.None,
            targetAltitudeM = ac.targetAltitudeM,
        ).right()
        val head = route.waypoints.head
        val headPos = worldIndex.positions[head]
            ?: return RoutingError.WaypointNotInIndex(head).left()
        val dx = headPos.xMeters - ac.position.xMeters
        val dy = headPos.yMeters - ac.position.yMeters
        val dist = StrictMath.hypot(dx, dy)
        if (dist > ac.type.kinematics.waypointRadiusM) {
            return PilotIntent(
                targetSpeedMps = airborneCruiseSpeed(ac.phase, ac.type.kinematics),
                phase = ac.phase,
                route = route,
                targetAltitudeM = route.targetAltitudeM,
            ).right()
        }

        // Waypoint reached — apply per-waypoint constraints from the popped waypoint.
        val constraint = route.waypointConstraints[head]
        val constrainedAltitude = constraint?.altitude?.let { resolveAltConstraint(it) }
            ?: route.targetAltitudeM
        val constrainedSpeed = constraint?.speed?.let { resolveSpdConstraint(it) }

        val remaining = route.waypoints.tail
        return if (remaining.isEmpty()) {
            val terminalIsGround = isGroundArrivalPhase(route.arrivalPhase)
            val stillAirborne = ac.altitudeM > PilotConstants.GROUND_TOLERANCE_M
            if (terminalIsGround && stillAirborne) {
                PilotIntent(
                    targetSpeedMps = constrainedSpeed ?: airborneCruiseSpeed(ac.phase, ac.type.kinematics),
                    phase = ac.phase,
                    route = route,
                    targetAltitudeM = 0.0, // committed to ground: command descent
                ).right()
            } else {
                PilotIntent(
                    targetSpeedMps = constrainedSpeed ?: airborneCruiseSpeed(route.arrivalPhase, ac.type.kinematics),
                    phase = route.arrivalPhase,
                    route = PilotRoute.None,
                    targetAltitudeM = if (terminalIsGround) 0.0 else constrainedAltitude,
                ).right()
            }
        } else {
            val nextHead = remaining.first()
            val nextPhase = phaseForAirborneLeg(nextHead, worldIndex, default = ac.phase)
            val nextRoute = PilotRoute.Airborne(
                waypoints = NonEmptyList(nextHead, remaining.drop(1)),
                targetAltitudeM = constrainedAltitude,
                arrivalPhase = route.arrivalPhase,
                waypointConstraints = route.waypointConstraints,
            )
            PilotIntent(
                targetSpeedMps = constrainedSpeed ?: airborneCruiseSpeed(nextPhase, ac.type.kinematics),
                phase = nextPhase,
                route = nextRoute,
                targetAltitudeM = constrainedAltitude,
            ).right()
        }
    }

    /**
     * Rolling out on the runway after touchdown. Slow to a stop and stay put
     * until tower issues an [xyz.easiersaid.twr.protocol.AfterLandingVacateVia]
     * (or equivalent) — [applyPilotHeardInstruction] writes a fresh ground
     * route off the runway, the pilot notices it, and rolls again.
     */
    private fun onLandingRoll(ac: AircraftState): PilotIntent = when (val r = ac.route) {
        is PilotRoute.Ground -> PilotIntent(
            targetSpeedMps = ac.type.kinematics.taxiSpeedMps,
            phase = PilotPhase.Vacating,
            route = r,
            targetAltitudeM = 0.0,
        )
        PilotRoute.None, is PilotRoute.Airborne -> PilotIntent(
            targetSpeedMps = 0.0,
            phase = PilotPhase.LandingRoll,
            route = PilotRoute.None, // drop any stale airborne route after touchdown
            targetAltitudeM = 0.0,
        )
    }

    /**
     * Taxiing off the runway toward the assigned vacate point. Identical
     * mechanics to [onTaxiing], but on route completion the pilot settles
     * into [PilotPhase.ClearOfRunway] (via the route's arrivalPhase).
     */
    private fun onVacating(ac: AircraftState, worldIndex: WorldIndex): Either<RoutingError, PilotIntent> {
        val route = ac.route as? PilotRoute.Ground ?: return PilotIntent(
            targetSpeedMps = 0.0,
            phase = PilotPhase.ClearOfRunway,
            route = PilotRoute.None,
            targetAltitudeM = 0.0,
        ).right()
        val head = route.waypoints.head
        val headPos = worldIndex.positions[head]
            ?: return RoutingError.WaypointNotInIndex(head).left()
        val dx = headPos.xMeters - ac.position.xMeters
        val dy = headPos.yMeters - ac.position.yMeters
        val dist = StrictMath.hypot(dx, dy)
        if (dist > ac.type.kinematics.waypointRadiusM) {
            return PilotIntent(ac.type.kinematics.taxiSpeedMps, PilotPhase.Vacating, route).right()
        }
        val remaining = route.waypoints.tail
        return if (remaining.isEmpty()) {
            PilotIntent(0.0, route.arrivalPhase, PilotRoute.None).right()
        } else {
            val nextRoute = PilotRoute.Ground(
                waypoints = NonEmptyList(remaining.first(), remaining.drop(1)),
                arrivalPhase = route.arrivalPhase,
            )
            PilotIntent(ac.type.kinematics.taxiSpeedMps, PilotPhase.Vacating, nextRoute).right()
        }
    }

    /**
     * Clear of the runway, idle, waiting for ground's taxi-to-stand. The
     * moment the controller writes a [PilotRoute.Ground] (via [TaxiTo]), the
     * pilot starts taxiing.
     */
    private fun onClearOfRunway(ac: AircraftState): PilotIntent = when (val r = ac.route) {
        is PilotRoute.Ground -> PilotIntent(
            targetSpeedMps = ac.type.kinematics.taxiSpeedMps,
            phase = PilotPhase.Taxiing,
            route = r,
            targetAltitudeM = 0.0,
        )
        PilotRoute.None, is PilotRoute.Airborne -> idle(ac)
    }

}

private fun idle(ac: AircraftState): PilotIntent =
    PilotIntent(
        targetSpeedMps = 0.0,
        phase = ac.phase,
        route = ac.route,
        targetAltitudeM = ac.targetAltitudeM,
    )

/**
 * Derive the pilot phase from a waypoint's circuit-leg metadata. The
 * controller's `OnCircuitLeg` guards key off the same index, so this is
 * the single place the "which leg am I on" question is answered.
 */
private fun phaseForAirborneLeg(
    waypoint: xyz.easiersaid.twr.protocol.PointId,
    worldIndex: WorldIndex,
    default: PilotPhase,
): PilotPhase {
    val legs = worldIndex.circuitLegsByPoint[waypoint].orEmpty()
    return when {
        LegName.FINAL in legs -> PilotPhase.Final
        LegName.BASE in legs -> PilotPhase.Base
        LegName.DOWNWIND in legs -> PilotPhase.Downwind
        LegName.CROSSWIND in legs -> PilotPhase.Crosswind
        LegName.UPWIND in legs -> PilotPhase.Climbing
        else -> default
    }
}

/**
 * Approach speed on base/final; cruise climb-speed on upwind/crosswind/downwind.
 *
 * Pass 10 (D-AUDIT.4): per-type speeds via [AircraftType.Kinematics].
 */
private fun airborneCruiseSpeed(
    phase: PilotPhase,
    kinematics: xyz.easiersaid.twr.protocol.AircraftType.Kinematics,
): Double = when (phase) {
    PilotPhase.Base, PilotPhase.Final -> kinematics.approachSpeedMps
    else -> kinematics.climbSpeedMps
}

private fun isGroundArrivalPhase(phase: PilotPhase): Boolean = when (phase) {
    PilotPhase.LandingRoll, PilotPhase.Vacating, PilotPhase.ClearOfRunway,
    PilotPhase.Parked, PilotPhase.AtStand, PilotPhase.HoldingShort,
    PilotPhase.Taxiing, PilotPhase.LinedUp, PilotPhase.TakeoffRoll -> true
    PilotPhase.Climbing, PilotPhase.Crosswind,
    PilotPhase.Downwind, PilotPhase.Base, PilotPhase.Final -> false
}

/** Resolve an altitude constraint to a target altitude in meters. */
private fun resolveAltConstraint(c: AltitudeConstraint): Double = when (c) {
    is AltitudeConstraint.At -> lvlToM(c.level)
    is AltitudeConstraint.AtOrAbove -> lvlToM(c.minimum)
    is AltitudeConstraint.AtOrBelow -> lvlToM(c.maximum)
    is AltitudeConstraint.Between -> lvlToM(c.minimum)
}

/** Resolve a speed constraint to a target speed in m/s. */
private fun resolveSpdConstraint(c: SpeedConstraint): Double = when (c) {
    is SpeedConstraint.At -> spdToMps(c.speed)
    is SpeedConstraint.AtOrAbove -> spdToMps(c.minimum)
    is SpeedConstraint.AtOrBelow -> spdToMps(c.maximum)
    is SpeedConstraint.Between -> spdToMps(c.minimum)
}

private fun lvlToM(level: xyz.easiersaid.twr.protocol.Level): Double = when (level) {
    is xyz.easiersaid.twr.protocol.Level.FlightLevel -> level.fl * 30.48
    is xyz.easiersaid.twr.protocol.Level.AltitudeFeet -> level.feet * 0.3048
    is xyz.easiersaid.twr.protocol.Level.HeightFeet -> level.feet * 0.3048
}

private fun spdToMps(speed: xyz.easiersaid.twr.protocol.Speed): Double = when (speed) {
    is xyz.easiersaid.twr.protocol.Speed.InKnots -> speed.knots.value * 0.51444
    is xyz.easiersaid.twr.protocol.Speed.InMach -> speed.mach.value * 340.3
}
