package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.PilotTransmission
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
): UnifiedPilotDecision {
    val view = PilotView(now, aircraft, worldIndex)
    val kinematicIntent = DefaultPilot.decide(view)

    val mission = aircraft.pilotMission
    if (mission == null || mission.isComplete) {
        // No mission — pure kinematic pilot (legacy path).
        return UnifiedPilotDecision(kinematicIntent, emptyList(), mission)
    }

    // Cognitive layer: advance mission, generate transmissions.
    val cognitive = pilotCognitiveDecide(aircraft, mission, worldIndex, now)

    // Apply cognitive overrides to kinematic intent.
    val finalIntent = applyCognitiveOverrides(kinematicIntent, cognitive.updatedMission, aircraft)

    return UnifiedPilotDecision(finalIntent, cognitive.transmissions, cognitive.updatedMission)
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

    // Override 1: No landing clearance at decision altitude → go around.
    // The physical layer would descend to landing; the cognitive layer knows
    // we don't have clearance. The pilot decides: go around.
    // Go around at any approach step below decision altitude without clearance.
    // The hasClearance flag is set by processInstruction when ClearedToLand received.
    val onApproach = currentStep == MissionStep.AWAIT_LANDING_CLEARANCE ||
        currentStep == MissionStep.REPORT_FINAL || currentStep == MissionStep.FLY_FINAL ||
        currentStep == MissionStep.FLY_BASE || currentStep == MissionStep.REPORT_BASE
    val awaitingClearance = onApproach && !mission.hasClearance
    val belowDecisionAlt = aircraft.altitudeM in 0.01..DECISION_ALTITUDE_M
    val notYetLanded = aircraft.phase !is PilotPhase.LandingRoll && aircraft.phase !is PilotPhase.Vacating
    if (awaitingClearance) {
        if (belowDecisionAlt && notYetLanded) {
            return kinematic.copy(
                targetAltitudeM = PilotConstants.CLIMB_SPEED_MPS * 10, // climb to pattern altitude
                targetSpeedMps = PilotConstants.CLIMB_SPEED_MPS,
                phase = PilotPhase.Climbing,
            )
        }
    }

    // Override 2: ExtendingDownwind constraint → maintain downwind heading, don't turn base.
    if (ActiveConstraint.ExtendingDownwind in mission.activeConstraints) {
        if (kinematic.phase is PilotPhase.Base) {
            // Don't turn base — stay on downwind.
            return kinematic.copy(phase = PilotPhase.Downwind)
        }
    }

    return kinematic
}
