package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.PilotTransmission
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
    val kinematicIntent = DefaultPilot.decide(view)

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
    // doesn't have yet, the route planner builds one.
    val plannedIntent = planRouteIfNeeded(
        effectiveMission, aircraft, world, worldIndex, activeRunway,
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
 * If the current task needs an airborne route the pilot doesn't yet have,
 * build one via [buildAirborneRoute] and return an intent that applies it.
 * Returns null when no route action is needed (ground task, route already
 * correct, or navigation mode unknown).
 *
 * Fires for:
 * - **Circuit mode + FLY_DEPARTURE**: T&G lift-off or route upgrade.
 * - **Any airborne task with no route**: go-around, arrival circuit join, etc.
 */
private fun planRouteIfNeeded(
    mission: PilotMission?,
    aircraft: AircraftState,
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

    // Any airborne step where the pilot has no route: build one from the planner.
    // This covers go-around (pilot needs circuit route after climbing), arrival
    // circuit join, and any future case where the pilot is airborne without a route.
    val airborneSteps = setOf(
        MissionStep.FLY_DOWNWIND, MissionStep.FLY_BASE, MissionStep.FLY_FINAL,
        MissionStep.FLY_DEPARTURE, MissionStep.FLY_SID, MissionStep.FLY_EN_ROUTE,
        MissionStep.FLY_STAR, MissionStep.FLY_APPROACH, MissionStep.FLY_MISSED_APPROACH,
    )
    if (step !in airborneSteps) return null
    if (aircraft.route !is PilotRoute.None) return null // already has a route

    val taskName = mission.root.activeCompound()?.name ?: return null
    val route = buildAirborneRoute(mode, taskName, w, worldIndex)
        .getOrElse { return null }

    return PilotIntent(
        targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) PilotConstants.CLIMB_SPEED_MPS
            else PilotConstants.APPROACH_SPEED_MPS,
        phase = aircraft.phase, // maintain current phase
        route = route,
        targetAltitudeM = route.targetAltitudeM,
    )
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
    // autonomously. No AWAITING_ATC_INSTRUCTION — the pilot decided, not ATC.
    // ATC-instructed go-arounds (via processInstruction) use the full task with
    // AWAITING_ATC_INSTRUCTION because ATC may have specific re-sequencing.
    val gaTask = if (mission.navigationMode is NavigationMode.Instrument) {
        ifrGoAroundTask()
    } else {
        // Self-initiated VFR: just GOING_AROUND (report), then immediately
        // re-enter circuit. The goAroundTask() includes AWAITING_ATC_INSTRUCTION
        // which blocks the pilot; for self-initiated, use a minimal task.
        CompoundTask(TaskName.GoAround, listOf(
            PrimitiveTask(MissionStep.GOING_AROUND, CompletionMode.REPORTED),
        ))
    }
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
