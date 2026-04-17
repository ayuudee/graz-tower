package xyz.easiersaid.twr.sim

import arrow.core.NonEmptyList
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.SimTime

/**
 * Everything the pilot agent sees at a decision tick.
 *
 * Pure in — the agent's decision is a function of this value alone.
 */
data class PilotView(
    val now: SimTime,
    val aircraft: AircraftState,
    val worldIndex: WorldIndex,
)

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
    fun decide(view: PilotView): PilotIntent
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

    override fun decide(view: PilotView): PilotIntent {
        val ac = view.aircraft
        return when (ac.phase) {
            PilotPhase.AtStand -> onAtStand(ac)
            PilotPhase.Taxiing -> onTaxiing(ac, view.worldIndex)
            PilotPhase.LinedUp -> onLinedUp(ac)
            PilotPhase.TakeoffRoll -> onTakeoffRoll(ac, view.worldIndex)
            PilotPhase.Climbing, PilotPhase.Crosswind,
            PilotPhase.Downwind, PilotPhase.Base, PilotPhase.Final ->
                onAirborneLeg(ac, view.worldIndex)
            PilotPhase.HoldingShort -> onHoldingShort(ac)
            PilotPhase.LandingRoll -> onLandingRoll(ac)
            PilotPhase.Vacating -> onVacating(ac, view.worldIndex)
            PilotPhase.ClearOfRunway -> onClearOfRunway(ac)
            PilotPhase.Parked -> idle(ac)
        }
    }

    private fun onAtStand(ac: AircraftState): PilotIntent = when (val r = ac.route) {
        is PilotRoute.Ground -> PilotIntent(
            targetSpeedMps = PilotConstants.TAXI_TARGET_SPEED_MPS,
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
            targetSpeedMps = PilotConstants.TAXI_TARGET_SPEED_MPS,
            phase = PilotPhase.Taxiing,
            route = r,
            targetAltitudeM = 0.0,
        )
        PilotRoute.None, is PilotRoute.Airborne -> idle(ac)
    }

    private fun onTaxiing(ac: AircraftState, worldIndex: WorldIndex): PilotIntent {
        val route = ac.route as? PilotRoute.Ground
            ?: return PilotIntent(0.0, PilotPhase.AtStand, PilotRoute.None) // defensive reset
        val head = route.waypoints.head
        val headPos = worldIndex.positions[head]
            ?: error("Waypoint $head not present in WorldIndex.positions")

        val dx = headPos.xMeters - ac.position.xMeters
        val dy = headPos.yMeters - ac.position.yMeters
        val dist = StrictMath.hypot(dx, dy)
        if (dist > PilotConstants.WAYPOINT_RADIUS_M) {
            // Still en route — keep taxiing toward the same first waypoint.
            return PilotIntent(PilotConstants.TAXI_TARGET_SPEED_MPS, PilotPhase.Taxiing, route)
        }

        // Waypoint reached — pop it.
        val remaining = route.waypoints.tail
        return if (remaining.isEmpty()) {
            // Final waypoint reached — settle into the route's arrival phase.
            PilotIntent(0.0, route.arrivalPhase, PilotRoute.None)
        } else {
            val nextRoute = PilotRoute.Ground(
                waypoints = NonEmptyList(remaining.first(), remaining.drop(1)),
                arrivalPhase = route.arrivalPhase,
            )
            PilotIntent(PilotConstants.TAXI_TARGET_SPEED_MPS, PilotPhase.Taxiing, nextRoute)
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
        if (ac.speedMps >= PilotConstants.ROTATION_SPEED_MPS) {
            val phase = phaseForAirborneLeg(route.waypoints.head, worldIndex, default = PilotPhase.Climbing)
            return PilotIntent(
                targetSpeedMps = PilotConstants.CLIMB_SPEED_MPS,
                phase = phase,
                route = route,
                targetAltitudeM = route.targetAltitudeM,
            )
        }
        // Still accelerating along the runway centreline.
        return PilotIntent(
            targetSpeedMps = PilotConstants.CLIMB_SPEED_MPS,
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
    private fun onAirborneLeg(ac: AircraftState, worldIndex: WorldIndex): PilotIntent {
        // Route finished — hold the airborne arrival phase at circuit altitude.
        // Without this, the next tick would flip to AtStand because the sealed
        // `when` in decide() has no "airborne idle" branch of its own.
        val route = ac.route as? PilotRoute.Airborne ?: return PilotIntent(
            targetSpeedMps = airborneCruiseSpeed(ac.phase),
            phase = ac.phase,
            route = PilotRoute.None,
            targetAltitudeM = ac.targetAltitudeM,
        )
        val head = route.waypoints.head
        val headPos = worldIndex.positions[head]
            ?: error("Waypoint $head not present in WorldIndex.positions")
        val dx = headPos.xMeters - ac.position.xMeters
        val dy = headPos.yMeters - ac.position.yMeters
        val dist = StrictMath.hypot(dx, dy)
        if (dist > PilotConstants.WAYPOINT_RADIUS_M) {
            return PilotIntent(
                targetSpeedMps = airborneCruiseSpeed(ac.phase),
                phase = ac.phase,
                route = route,
                targetAltitudeM = route.targetAltitudeM,
            )
        }
        val remaining = route.waypoints.tail
        return if (remaining.isEmpty()) {
            // Last leg complete. If the arrival phase is a ground phase, gate
            // on altitude so the descent on final actually has to happen.
            val terminalIsGround = isGroundArrivalPhase(route.arrivalPhase)
            val stillAirborne = ac.altitudeM > PilotConstants.GROUND_TOLERANCE_M
            if (terminalIsGround && stillAirborne) {
                PilotIntent(
                    targetSpeedMps = airborneCruiseSpeed(ac.phase),
                    phase = ac.phase,
                    route = route, // keep route so advanceKinematics still drives toward head
                    targetAltitudeM = 0.0, // committed to ground: command descent
                )
            } else {
                PilotIntent(
                    targetSpeedMps = airborneCruiseSpeed(route.arrivalPhase),
                    phase = route.arrivalPhase,
                    route = PilotRoute.None,
                    targetAltitudeM = if (terminalIsGround) 0.0 else route.targetAltitudeM,
                )
            }
        } else {
            val nextHead = remaining.first()
            val nextPhase = phaseForAirborneLeg(nextHead, worldIndex, default = ac.phase)
            val nextRoute = PilotRoute.Airborne(
                waypoints = NonEmptyList(nextHead, remaining.drop(1)),
                targetAltitudeM = route.targetAltitudeM,
                arrivalPhase = route.arrivalPhase,
            )
            PilotIntent(
                targetSpeedMps = airborneCruiseSpeed(nextPhase),
                phase = nextPhase,
                route = nextRoute,
                targetAltitudeM = route.targetAltitudeM,
            )
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
            targetSpeedMps = PilotConstants.TAXI_TARGET_SPEED_MPS,
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
    private fun onVacating(ac: AircraftState, worldIndex: WorldIndex): PilotIntent {
        val route = ac.route as? PilotRoute.Ground ?: return PilotIntent(
            targetSpeedMps = 0.0,
            phase = PilotPhase.ClearOfRunway,
            route = PilotRoute.None,
            targetAltitudeM = 0.0,
        )
        val head = route.waypoints.head
        val headPos = worldIndex.positions[head]
            ?: error("Waypoint $head not present in WorldIndex.positions")
        val dx = headPos.xMeters - ac.position.xMeters
        val dy = headPos.yMeters - ac.position.yMeters
        val dist = StrictMath.hypot(dx, dy)
        if (dist > PilotConstants.WAYPOINT_RADIUS_M) {
            return PilotIntent(PilotConstants.TAXI_TARGET_SPEED_MPS, PilotPhase.Vacating, route)
        }
        val remaining = route.waypoints.tail
        return if (remaining.isEmpty()) {
            PilotIntent(0.0, route.arrivalPhase, PilotRoute.None)
        } else {
            val nextRoute = PilotRoute.Ground(
                waypoints = NonEmptyList(remaining.first(), remaining.drop(1)),
                arrivalPhase = route.arrivalPhase,
            )
            PilotIntent(PilotConstants.TAXI_TARGET_SPEED_MPS, PilotPhase.Vacating, nextRoute)
        }
    }

    /**
     * Clear of the runway, idle, waiting for ground's taxi-to-stand. The
     * moment the controller writes a [PilotRoute.Ground] (via [TaxiTo]), the
     * pilot starts taxiing.
     */
    private fun onClearOfRunway(ac: AircraftState): PilotIntent = when (val r = ac.route) {
        is PilotRoute.Ground -> PilotIntent(
            targetSpeedMps = PilotConstants.TAXI_TARGET_SPEED_MPS,
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

/** Approach speed on base/final; cruise climb-speed on upwind/crosswind/downwind. */
private fun airborneCruiseSpeed(phase: PilotPhase): Double = when (phase) {
    PilotPhase.Base, PilotPhase.Final -> PilotConstants.APPROACH_SPEED_MPS
    else -> PilotConstants.CLIMB_SPEED_MPS
}

private fun isGroundArrivalPhase(phase: PilotPhase): Boolean = when (phase) {
    PilotPhase.LandingRoll, PilotPhase.Vacating, PilotPhase.ClearOfRunway,
    PilotPhase.Parked, PilotPhase.AtStand, PilotPhase.HoldingShort,
    PilotPhase.Taxiing, PilotPhase.LinedUp, PilotPhase.TakeoffRoll -> true
    PilotPhase.Climbing, PilotPhase.Crosswind,
    PilotPhase.Downwind, PilotPhase.Base, PilotPhase.Final -> false
}
