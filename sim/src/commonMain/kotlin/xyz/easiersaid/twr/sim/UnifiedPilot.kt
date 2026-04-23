package xyz.easiersaid.twr.sim

import arrow.core.None
import arrow.core.Some
import arrow.core.getOrElse
import arrow.core.toOption
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime

/**
 * Unified pilot decision: one brain, one output.
 *
 * Combines kinematic intent (speed, altitude, phase, route) with cognitive
 * decisions (transmissions, mission advancement). The cognitive layer can
 * override kinematic intent when the mission requires it (e.g., go around
 * at decision altitude without clearance).
 *
 * The physical layer ([DefaultPilot]) computes what the aircraft would do
 * kinematically. The cognitive layer ([pilotCognitiveDecide]) advances the
 * mission and generates transmissions. This function merges both, with
 * cognitive overrides taking precedence.
 */
data class UnifiedPilotDecision(
    val intent: PilotIntent,
    val transmissions: List<PilotTransmission>,
    val updatedMission: PilotMission?,
    /**
     * Populated when [DefaultPilot.decide] returns a [RoutingError.WaypointNotInIndex] —
     * a data integrity defect in the world (route references a waypoint absent from
     * [WorldIndex.positions]). On error the aircraft is frozen in place; callers can
     * log or assert on this field. Null on all normal ticks.
     */
    val routingError: RoutingError? = null,
)

/** Decision altitude threshold — below this without clearance → go around. */
private const val DECISION_ALTITUDE_M = 100.0

/**
 * One pilot, one brain, one decision.
 *
 * If the aircraft has no mission, falls back to pure kinematic pilot (legacy).
 * If the aircraft has a mission, the cognitive layer drives everything:
 * - Kinematics are computed by DefaultPilot as a baseline
 * - Cognitive layer can override (go-around when no clearance, hold position when extending)
 * - Transmissions come from the cognitive layer only
 */
fun unifiedPilotDecide(
    aircraft: AircraftState,
    worldIndex: WorldIndex,
    now: SimTime,
    world: AviationWorld? = null,
    activeRunway: RunwayId? = null,
): UnifiedPilotDecision {
    val view = PilotView(now, aircraft, worldIndex)
    val kinematicResult = DefaultPilot.decide(view)
    val kinematicIntent = kinematicResult.getOrElse { err ->
        // Waypoint absent from WorldIndex — data integrity defect in the world.
        // Freeze the aircraft and surface the error for callers to log/assert on.
        return UnifiedPilotDecision(
            intent = PilotIntent(0.0, aircraft.phase, aircraft.route, aircraft.targetAltitudeM),
            transmissions = emptyList(),
            updatedMission = aircraft.pilotMission,
            routingError = err,
        )
    }

    val mission = aircraft.pilotMission
    if (mission == null || mission.isComplete) {
        return UnifiedPilotDecision(kinematicIntent, emptyList(), mission)
    }

    // Cognitive layer: advance mission, generate transmissions.
    val cognitive = pilotCognitiveDecide(aircraft, mission, worldIndex, now)

    // Self-initiated go-around: if the pilot is at decision altitude without
    // clearance, trigger a full go-around (mission update + transmission + climb).
    // This must run BEFORE route planning and kinematic overrides.
    val goAround = checkSelfInitiatedGoAround(cognitive.updatedMission, aircraft, now)
    val effectiveMission = goAround?.mission ?: cognitive.updatedMission
    val goAroundTransmissions = goAround?.transmissions ?: emptyList()

    // Plan execution: if the current task needs an airborne route the pilot
    // doesn't have yet, the route planner builds one. Pass the kinematic route
    // so the Visual-mode planner can compare against the post-pop state and avoid
    // regressing waypoints already consumed by DefaultPilot.
    val plannedIntent = planRoute(
        effectiveMission, aircraft, kinematicIntent.route, world, worldIndex, activeRunway,
    )
    val finalIntent = plannedIntent
        ?: goAround?.intent
        ?: applyCognitiveOverrides(kinematicIntent, effectiveMission, aircraft)

    return UnifiedPilotDecision(
        finalIntent,
        cognitive.transmissions + goAroundTransmissions,
        effectiveMission,
    )
}

/**
 * Derive the pilot's airborne route from mission state and current position.
 *
 * **Aviate, navigate, communicate**: the pilot re-derives their route every tick.
 * For Visual mode this is continuous — any change to mission state (restriction,
 * join leg, step advancement) is reflected immediately without explicit route
 * invalidation. A stability check suppresses intent updates when the derived route
 * is identical to the one currently being flown.
 *
 * For non-Visual modes (Circuit, Instrument) the one-shot behaviour is preserved
 * until IFR wiring lands — routes are built once and not rebuilt mid-flight.
 *
 * Fires for:
 * - **Circuit mode + FLY_DEPARTURE**: T&G lift-off or route upgrade (always).
 * - **Visual mode, any airborne step**: continuous re-derivation from position.
 * - **Other modes, airborne step, no route**: one-shot bootstrap.
 */
private fun planRoute(
    mission: PilotMission?,
    aircraft: AircraftState,
    kinematicRoute: PilotRoute,
    world: AviationWorld?,
    worldIndex: WorldIndex,
    activeRunway: RunwayId?,
): PilotIntent? {
    mission ?: return null
    val step = mission.currentTask?.step ?: return null
    val w = world ?: return null
    val rwy = activeRunway ?: return null
    val mode = mission.navigationMode
        ?: deriveNavigationMode(mission.goal, rwy, w).getOrElse { return null }

    // FLY_DEPARTURE in Circuit mode: T&G lift-off or short-route upgrade.
    if (step == MissionStep.FLY_DEPARTURE && mode is NavigationMode.Circuit) {
        val dep = planCircuitDeparture(mission, aircraft, mode, w, worldIndex)
        if (dep != null) return dep
        // Fall through: pilot may be climbing after go-around with FLY_DEPARTURE
        // as the next step but not in LandingRoll/TakeoffRoll phase.
    }

    val airborneSteps = setOf(
        // Flying steps
        MissionStep.FLY_DOWNWIND, MissionStep.FLY_BASE, MissionStep.FLY_FINAL,
        MissionStep.FLY_DEPARTURE, MissionStep.FLY_SID, MissionStep.FLY_EN_ROUTE,
        MissionStep.FLY_STAR, MissionStep.FLY_APPROACH, MissionStep.FLY_MISSED_APPROACH,
        // Airborne report/sequencing steps — pilot needs a route while transmitting
        MissionStep.REPORT_DOWNWIND, MissionStep.REPORT_BASE, MissionStep.REPORT_FINAL,
        MissionStep.AWAIT_SEQUENCING, MissionStep.AWAIT_LANDING_CLEARANCE,
        MissionStep.LAND,
    )
    if (step !in airborneSteps) return null

    val taskName = mission.root.activeCompound()?.name ?: return null

    return when (mode) {
        // Visual mode: continuous re-derivation from mission state + kinematic position.
        is NavigationMode.Visual -> planVisualRoute(mode, taskName, mission, aircraft, kinematicRoute, w, worldIndex)

        // Non-Visual: one-shot bootstrap. Route is built once and not rebuilt mid-flight.
        // IFR continuous planning is deferred until IFR missions land.
        else -> {
            if (aircraft.route !is PilotRoute.None) return null
            val route = buildAirborneRoute(mode, taskName, w, worldIndex)
                .getOrElse { return null }
            PilotIntent(
                targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) PilotConstants.CLIMB_SPEED_MPS
                    else PilotConstants.APPROACH_SPEED_MPS,
                phase = aircraft.phase,
                route = route,
                targetAltitudeM = route.targetAltitudeM,
            )
        }
    }
}

/**
 * Continuous route derivation for Visual mode.
 *
 * **Aviate, navigate, communicate**: the pilot re-derives their route every tick.
 * Any change to mission state (ATC join instruction, altitude restriction) is reflected
 * immediately without explicit route invalidation.
 *
 * **Stability**: we compare against [kinematicRoute] (the DefaultPilot's post-pop route),
 * not [AircraftState.route] (the pre-pop route). This prevents two failure modes:
 * - Regression: after DefaultPilot pops DOWNWIND-1, aircraft.route = [BASE-1, ...]. If we
 *   compare against aircraft.route, the position-derived [DOWNWIND-1, ...] looks different
 *   and we'd steer the aircraft back to DOWNWIND-1.
 * - Over-planning: kinematic is already past the ATC-instructed join point — suffix check
 *   suppresses the re-plan and lets the kinematic pilot continue forward.
 *
 * **Join leg priority**: ATC instruction ([PilotMission.joinLeg]) takes precedence. When
 * the instruction is not set, leg is derived from [kinematicRoute]'s head waypoint (where
 * the pilot is heading next), falling back to [AircraftState.positionPoint] for bootstrap
 * (no route yet).
 */
private fun planVisualRoute(
    mode: NavigationMode.Visual,
    taskName: TaskName,
    mission: PilotMission,
    aircraft: AircraftState,
    kinematicRoute: PilotRoute,
    world: AviationWorld,
    worldIndex: WorldIndex,
): PilotIntent? {
    val step = mission.currentTask?.step

    // ── Bootstrap with no kinematic route ─────────────────────────────────────
    // When the kinematic pilot has no route (either initial spawn or just completed
    // the last waypoint), determine whether to bootstrap a new route.
    val cur = kinematicRoute as? PilotRoute.Airborne ?: run {
        // LAND step + Final phase: kinematic just committed to landing (last waypoint consumed,
        // LandingRoll intent issued). Don't bootstrap — would interrupt the landing flare.
        if (aircraft.phase is PilotPhase.Final && step == MissionStep.LAND) return null

        // FLY_DEPARTURE step + Final phase: GOING_AROUND just reported, step advanced to
        // FLY_DEPARTURE but aircraft is still on final. Build the go-around climb route
        // (go-around path + full circuit) so the pilot immediately climbs out.
        if (aircraft.phase is PilotPhase.Final && step == MissionStep.FLY_DEPARTURE) {
            val gaRoute = buildGoAroundRoute(mode.runway, world, worldIndex).getOrElse { return null }
            val gaAlt = mission.altitudeRestrictionM?.let { minOf(CIRCUIT_ALTITUDE_M, it) } ?: CIRCUIT_ALTITUDE_M
            return PilotIntent(
                targetSpeedMps = PilotConstants.CLIMB_SPEED_MPS,
                phase = PilotPhase.Climbing,
                route = gaRoute.copy(targetAltitudeM = gaAlt),
                targetAltitudeM = gaAlt,
            )
        }

        // Normal bootstrap: derive and return the circuit route for the current step.
        // (Computes route below; falls through to the return at the end of the run block.)
        null  // sentinel: proceed to route derivation below
    }

    // ── Derive the route from mission state + kinematic position ───────────────
    // Use kinematic route head (where we're heading) rather than positionPoint (where we've
    // been) to avoid re-inserting waypoints the DefaultPilot already consumed.
    val lookupPoint = (kinematicRoute as? PilotRoute.Airborne)?.waypoints?.head
        ?: aircraft.positionPoint
    val derivedLeg = deriveCurrentCircuitLeg(lookupPoint, worldIndex).toOption()
    // ATC join instruction takes priority over position-derived leg.
    // Exhaustive when — Option is sealed (Some | None); no silent else branch.
    val joinLeg = when (val jl = mission.joinLeg) {
        is Some -> jl
        is None -> derivedLeg
    }

    val route = buildVisualModeRoute(mode, taskName, world, worldIndex, joinLeg)
        .getOrElse { return null }

    // Apply ATC altitude restriction (E3): StopClimbAt caps targetAltitudeM so the
    // pilot levels off at the restricted altitude rather than climbing to circuit altitude.
    val targetAlt = mission.altitudeRestrictionM
        ?.let { minOf(route.targetAltitudeM, it) }
        ?: route.targetAltitudeM

    // ── Bootstrap completion (no prior kinematic route) ────────────────────────
    if (cur == null) {
        return PilotIntent(
            targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) PilotConstants.CLIMB_SPEED_MPS
                else PilotConstants.APPROACH_SPEED_MPS,
            phase = aircraft.phase,
            route = route.copy(targetAltitudeM = targetAlt),
            targetAltitudeM = targetAlt,
        )
    }

    // ── Stability: compare against kinematic route (post-pop), not aircraft.route ──
    // Exact match: nothing has changed.
    if (cur.waypoints == route.waypoints && cur.targetAltitudeM == targetAlt && cur.arrivalPhase == route.arrivalPhase) return null

    // Kinematic is already a suffix of the derived route: the aircraft has progressed
    // past the derived join point. Don't regress waypoints — but altitude may still change.
    val newWps = route.waypoints.toList()
    val curWps = cur.waypoints.toList()
    val kinematicAlreadyAhead = newWps.size > curWps.size && newWps.takeLast(curWps.size) == curWps
    if (kinematicAlreadyAhead) {
        // On the last waypoint (committed to landing or final waypoint): always suppress.
        // Prevents rebuilding from the threshold point (which maps to both FINAL and UPWIND)
        // and disrupting the kinematic pilot's final descent commitment.
        if (cur.waypoints.tail.isEmpty()) return null
        // Earlier waypoints: suppress if nothing changed; update altitude in-place if it did.
        if (cur.targetAltitudeM == targetAlt && cur.arrivalPhase == route.arrivalPhase) return null
        return PilotIntent(
            targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) PilotConstants.CLIMB_SPEED_MPS
                else PilotConstants.APPROACH_SPEED_MPS,
            phase = aircraft.phase,
            route = cur.copy(targetAltitudeM = targetAlt),
            targetAltitudeM = targetAlt,
        )
    }

    return PilotIntent(
        targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) PilotConstants.CLIMB_SPEED_MPS
            else PilotConstants.APPROACH_SPEED_MPS,
        phase = aircraft.phase,
        route = route.copy(targetAltitudeM = targetAlt),
        targetAltitudeM = targetAlt,
    )
}

/**
 * Derive the circuit leg the aircraft is currently on from its WorldIndex position.
 *
 * Returns null when the aircraft is not at a mapped circuit waypoint (e.g., on the
 * apron, inbound from outside the circuit). The caller falls back to [PilotMission.joinLeg].
 *
 * Priority: FINAL > BASE > DOWNWIND > CROSSWIND > UPWIND.
 * FINAL takes priority because the threshold PointId maps to both FINAL and UPWIND;
 * when on approach to land, FINAL is the correct interpretation. Aircraft departing
 * from threshold are handled by [planCircuitDeparture] before this function is called.
 */
private fun deriveCurrentCircuitLeg(position: PointId, worldIndex: WorldIndex): LegName? {
    val legs = worldIndex.circuitLegsByPoint[position]
    if (legs.isNullOrEmpty()) return null
    val priority = listOf(LegName.FINAL, LegName.BASE, LegName.DOWNWIND, LegName.CROSSWIND, LegName.UPWIND)
    return priority.firstOrNull { it in legs }
}

/** Circuit-mode FLY_DEPARTURE: T&G lift-off or short-route upgrade to full circuit. */
private fun planCircuitDeparture(
    mission: PilotMission,
    aircraft: AircraftState,
    mode: NavigationMode.Circuit,
    world: AviationWorld,
    worldIndex: WorldIndex,
): PilotIntent? {
    when (aircraft.phase) {
        is PilotPhase.LandingRoll -> Unit
        is PilotPhase.TakeoffRoll -> {
            val current = aircraft.route as? PilotRoute.Airborne ?: return null
            if (current.arrivalPhase is PilotPhase.LandingRoll) return null // already upgraded
        }
        else -> return null
    }

    val taskName = mission.root.activeCompound()?.name ?: return null
    val route = buildAirborneRoute(mode, taskName, world, worldIndex)
        .getOrElse { return null }

    return PilotIntent(
        targetSpeedMps = PilotConstants.CLIMB_SPEED_MPS,
        phase = PilotPhase.TakeoffRoll,
        route = route,
        targetAltitudeM = CIRCUIT_ALTITUDE_M,
    )
}

/** Result of a self-initiated go-around check. */
private data class GoAroundResult(
    val intent: PilotIntent,
    val mission: PilotMission,
    val transmissions: List<PilotTransmission>,
)

/**
 * Check if the pilot should self-initiate a go-around (decision altitude
 * without clearance). If so, returns the full go-around effect: mission
 * update (subtree replacement + resetForGoAround), GoingAround transmission,
 * and climbing intent. Returns null if no go-around is needed.
 *
 * This is the pilot's DECISION to go around — distinct from an ATC-instructed
 * go-around (which arrives via processInstruction). Both produce the same
 * mission-level effect (subtree replacement + state reset).
 */
private fun checkSelfInitiatedGoAround(
    mission: PilotMission,
    aircraft: AircraftState,
    now: SimTime,
): GoAroundResult? {
    val currentStep = mission.currentTask?.step ?: return null

    // Only fire on approach steps without clearance, at or below decision altitude.
    val onApproach = currentStep == MissionStep.AWAIT_LANDING_CLEARANCE ||
        currentStep == MissionStep.REPORT_FINAL || currentStep == MissionStep.FLY_FINAL ||
        currentStep == MissionStep.FLY_BASE || currentStep == MissionStep.REPORT_BASE
    if (!onApproach || mission.hasClearance) return null
    if (aircraft.altitudeM !in 0.01..DECISION_ALTITUDE_M) return null
    if (aircraft.phase is PilotPhase.LandingRoll || aircraft.phase is PilotPhase.Vacating) return null
    // Don't re-fire if already going around (step advanced to GOING_AROUND or beyond).
    if (currentStep == MissionStep.GOING_AROUND || currentStep == MissionStep.AWAITING_ATC_INSTRUCTION) return null

    // Full go-around: mission update + transmission + climbing intent.
    // Self-initiated go-around: pilot reports going around and re-enters circuit
    // VFR go-arounds are autonomous — pilot re-enters circuit after GOING_AROUND.
    // IFR uses a separate task that includes FLY_MISSED_APPROACH + AWAITING_ATC_INSTRUCTION.
    val gaTask = if (mission.navigationMode is NavigationMode.Instrument) ifrGoAroundTask()
        else goAroundTask()
    val newRoot = mission.root.replaceChild(
        predicate = { it is CompoundTask && !it.isComplete &&
            (it.name is TaskName.Circuit || it.name is TaskName.CircuitAfterGoAround) },
        replacement = CompoundTask(TaskName.CircuitAfterGoAround, listOf(
            gaTask,
            circuitTask(),
        )),
    )
    val updatedMission = mission.resetForGoAround(now).copy(root = newRoot)

    return GoAroundResult(
        intent = PilotIntent(
            targetSpeedMps = PilotConstants.CLIMB_SPEED_MPS,
            phase = PilotPhase.Climbing,
            route = aircraft.route, // keep current route until planner builds new one
            targetAltitudeM = CIRCUIT_ALTITUDE_M,
        ),
        mission = updatedMission,
        transmissions = listOf(Report(listOf(ReportEvent.GoingAround))),
    )
}

/**
 * Apply cognitive overrides to kinematic intent.
 *
 * The cognitive layer IS the pilot. When the mission state conflicts with
 * what the kinematics want to do, the cognitive decision wins.
 */
private fun applyCognitiveOverrides(
    kinematic: PilotIntent,
    mission: PilotMission,
    aircraft: AircraftState,
): PilotIntent {
    val currentStep = mission.currentTask?.step ?: return kinematic

    // Go-around override moved to checkSelfInitiatedGoAround (updates mission + transmits).

    // Override: ExtendingDownwind constraint → maintain downwind heading, don't turn base.
    if (ActiveConstraint.ExtendingDownwind in mission.activeConstraints) {
        if (kinematic.phase is PilotPhase.Base) {
            // Don't turn base — stay on downwind.
            return kinematic.copy(phase = PilotPhase.Downwind)
        }
    }

    return kinematic
}
