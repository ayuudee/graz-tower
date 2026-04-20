package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.BreakOff
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.Request
import xyz.easiersaid.twr.protocol.RequestStartup
import xyz.easiersaid.twr.protocol.RequestTaxi
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.StartupApproved
import xyz.easiersaid.twr.protocol.TaxiTo
import xyz.easiersaid.twr.protocol.TurnBase

/**
 * Pilot cognitive decision function.
 *
 * Sits above the physical pilot (DefaultPilot). Decides what to SAY and when to
 * advance the mission. The physical pilot handles kinematic intent (speed, altitude,
 * waypoints). The cognitive pilot handles communication and step progression.
 *
 * Pure: (AircraftState, PilotMission, WorldIndex, SimTime) → (transmissions, updated mission)
 */
data class CognitiveDecision(
    val transmissions: List<PilotTransmission>,
    val updatedMission: PilotMission,
)

/**
 * Main cognitive decision: check step advancement, generate transmissions, process constraints.
 */
fun pilotCognitiveDecide(
    aircraft: AircraftState,
    mission: PilotMission,
    worldIndex: WorldIndex,
    now: SimTime,
): CognitiveDecision {
    if (mission.isComplete) return CognitiveDecision(emptyList(), mission)

    val step = mission.currentStep ?: return CognitiveDecision(emptyList(), mission)
    val transmissions = mutableListOf<PilotTransmission>()

    // Check if current step is complete → advance.
    val (advanced, advanceTransmissions) = advanceIfComplete(aircraft, mission, worldIndex, now)
    transmissions.addAll(advanceTransmissions)

    // Generate step-driven transmissions for the CURRENT step (after advancement).
    val currentStep = advanced.currentStep
    if (currentStep != null) {
        val stepTx = stepTransmission(aircraft, advanced, currentStep, worldIndex, now)
        if (stepTx != null) transmissions.add(stepTx)
    }

    return CognitiveDecision(transmissions, advanced)
}

// ── Step advancement ─────────────────────────────────────────────────

/**
 * Check if the current step's completion condition is met. If so, advance and
 * check the next step too (multiple steps can complete in one cycle — e.g.,
 * RunUp checks are instant for AI, AwaitLineUp completes if LUAW already received).
 */
@Suppress("LoopWithTooManyJumpStatements") // multi-step advancement naturally breaks/continues
private fun advanceIfComplete(
    aircraft: AircraftState,
    mission: PilotMission,
    worldIndex: WorldIndex,
    now: SimTime,
): Pair<PilotMission, List<PilotTransmission>> {
    var current = mission
    val transmissions = mutableListOf<PilotTransmission>()
    var safetyCounter = 0

    while (!current.isComplete && safetyCounter < 5) {
        val step = current.currentStep ?: break
        if (!isStepComplete(aircraft, current, step, worldIndex, now)) break

        // Step completed — advance.
        current = current.copy(
            currentStepIndex = current.currentStepIndex + 1,
            stepEnteredAt = now,
            // Clear constraints that don't carry across steps.
            activeConstraints = current.activeConstraints.filter {
                it is ActiveConstraint.SpeedRestriction // speed persists
            }.toSet(),
        )
        safetyCounter++
    }
    return current to transmissions
}

/** Is this step's completion condition met? */
@Suppress("CyclomaticComplexMethod")
private fun isStepComplete(
    aircraft: AircraftState,
    mission: PilotMission,
    step: MissionStep,
    worldIndex: WorldIndex,
    now: SimTime,
): Boolean = when (step) {
    MissionStep.REQUEST_STARTUP -> false // completes when approval received (instruction processing)
    MissionStep.AWAIT_STARTUP_APPROVAL -> false // instruction-driven
    MissionStep.REQUEST_TAXI -> false // completes when TaxiTo received
    MissionStep.TAXI_TO_HOLDING -> aircraft.phase is PilotPhase.HoldingShort
    MissionStep.RUN_UP_CHECKS -> {
        // AI: instant. Human: real dwell time.
        !aircraft.humanPiloted || (now.millis - mission.stepEnteredAt.millis > 10_000)
    }
    MissionStep.REPORT_READY -> false // completes after transmitting (see stepTransmission)
    MissionStep.AWAIT_LINE_UP -> aircraft.phase is PilotPhase.LinedUp
    MissionStep.AWAIT_TAKEOFF_CLEARANCE -> aircraft.phase is PilotPhase.TakeoffRoll || aircraft.phase is PilotPhase.Climbing
    MissionStep.FLY_DEPARTURE -> {
        // Complete when on downwind (circuit) or when leaving controlled airspace (depart).
        val legs = worldIndex.circuitLegsByPoint[aircraft.positionPoint] ?: emptySet()
        val isDeparting = mission.goal == xyz.easiersaid.twr.controller.PilotGoal.DEPART
        LegName.DOWNWIND in legs || (isDeparting && aircraft.phase is PilotPhase.Climbing)
    }
    MissionStep.FLY_DOWNWIND -> {
        val legs = worldIndex.circuitLegsByPoint[aircraft.positionPoint] ?: emptySet()
        LegName.DOWNWIND in legs // stay until we're established on downwind
    }
    MissionStep.REPORT_DOWNWIND -> mission.lastReportedLeg == LegName.DOWNWIND
    MissionStep.AWAIT_SEQUENCING -> {
        // Complete when: no extend constraint active AND aircraft is past downwind.
        val extending = ActiveConstraint.ExtendingDownwind in mission.activeConstraints
        val legs = worldIndex.circuitLegsByPoint[aircraft.positionPoint] ?: emptySet()
        val pastDownwind = LegName.BASE in legs || LegName.FINAL in legs
        !extending && pastDownwind
    }
    MissionStep.FLY_BASE -> {
        val legs = worldIndex.circuitLegsByPoint[aircraft.positionPoint] ?: emptySet()
        LegName.FINAL in legs
    }
    MissionStep.FLY_FINAL -> {
        // Always advance — landing clearance check is in AWAIT_LANDING_CLEARANCE.
        val legs = worldIndex.circuitLegsByPoint[aircraft.positionPoint] ?: emptySet()
        LegName.FINAL in legs
    }
    MissionStep.AWAIT_LANDING_CLEARANCE -> false // instruction-driven or escalation
    MissionStep.LAND -> aircraft.phase is PilotPhase.Vacating || aircraft.phase is PilotPhase.ClearOfRunway
    MissionStep.REPORT_RUNWAY_VACATED -> {
        // Complete after transmitting runway-vacated report.
        val isOff = aircraft.phase is PilotPhase.ClearOfRunway || aircraft.phase is PilotPhase.Taxiing
        isOff && mission.lastReportedLeg == null // cleared after reporting
    }
    MissionStep.AWAIT_VACATE_INSTRUCTION -> false // instruction-driven
    MissionStep.TAXI_TO_STAND -> aircraft.phase is PilotPhase.Parked
    MissionStep.SHUTDOWN -> true // terminal
    MissionStep.GOING_AROUND -> false // transitions to AWAITING_ATC_INSTRUCTION
    MissionStep.AWAITING_ATC_INSTRUCTION -> false // instruction-driven
}

// ── Transmission generation ──────────────────────────────────────────

/** Generate the transmission for entering or being in the current step (if any). */
@Suppress("CyclomaticComplexMethod", "UnusedParameter")
private fun stepTransmission(
    aircraft: AircraftState,
    mission: PilotMission,
    step: MissionStep,
    worldIndex: WorldIndex,
    now: SimTime,
): PilotTransmission? = when (step) {
    MissionStep.REQUEST_STARTUP -> Request(RequestStartup())
    MissionStep.REQUEST_TAXI -> Request(RequestTaxi())
    MissionStep.REPORT_READY -> Report(listOf(ReportEvent.Ready))
    MissionStep.REPORT_DOWNWIND -> {
        if (mission.lastReportedLeg != LegName.DOWNWIND) {
            Report(listOf(ReportEvent.Downwind))
        } else null
    }
    MissionStep.FLY_BASE -> {
        // Report base if not already reported.
        if (mission.lastReportedLeg != LegName.BASE) {
            Report(listOf(ReportEvent.Base))
        } else null
    }
    MissionStep.FLY_FINAL -> {
        // Report final if not already reported.
        if (mission.lastReportedLeg != LegName.FINAL) {
            Report(listOf(ReportEvent.Final))
        } else null
    }
    MissionStep.AWAIT_LANDING_CLEARANCE -> {
        // Escalation: query at threshold. Go-around handled separately.
        val elapsed = now.millis - mission.stepEnteredAt.millis
        if (elapsed > 15_000) { // 15s without clearance — query
            Report(listOf(ReportEvent.Final)) // re-report final as a prompt
        } else null
    }
    MissionStep.REPORT_RUNWAY_VACATED -> {
        val isOff = aircraft.phase is PilotPhase.ClearOfRunway || aircraft.phase is PilotPhase.Taxiing
        if (isOff) Report(listOf(ReportEvent.RunwayVacated)) else null
    }
    MissionStep.GOING_AROUND -> Report(listOf(ReportEvent.GoingAround))
    // Steps that don't generate transmissions.
    else -> null
}

// ── Instruction processing ───────────────────────────────────────────

/**
 * Process an ATC instruction received by the pilot. Updates the mission state.
 * Called from Step.kt when the pilot hears an instruction.
 */
@Suppress("CyclomaticComplexMethod") // instruction dispatch — one branch per instruction type
fun processInstruction(
    instruction: AtcInstruction,
    mission: PilotMission,
    now: SimTime,
): PilotMission {
    val step = mission.currentStep ?: return mission
    return when {
        // Startup approval → advance past AWAIT_STARTUP_APPROVAL.
        instruction is StartupApproved && step == MissionStep.AWAIT_STARTUP_APPROVAL ->
            mission.copy(currentStepIndex = mission.currentStepIndex + 1, stepEnteredAt = now)

        // TaxiTo → advance past REQUEST_TAXI.
        instruction is TaxiTo && step == MissionStep.REQUEST_TAXI ->
            mission.copy(currentStepIndex = mission.currentStepIndex + 1, stepEnteredAt = now)

        // LineUpAndWait → advance past AWAIT_LINE_UP.
        instruction is LineUpAndWait && step == MissionStep.AWAIT_LINE_UP ->
            mission.copy(currentStepIndex = mission.currentStepIndex + 1, stepEnteredAt = now)

        // ClearedForTakeoff → advance past AWAIT_TAKEOFF_CLEARANCE.
        instruction is ClearedForTakeoff &&
            (step == MissionStep.AWAIT_TAKEOFF_CLEARANCE || step == MissionStep.AWAIT_LINE_UP) -> {
            val depIdx = mission.steps.indexOf(MissionStep.FLY_DEPARTURE)
            mission.copy(currentStepIndex = depIdx.coerceAtLeast(mission.currentStepIndex + 1), stepEnteredAt = now)
        }

        // ExtendDownwind → add constraint, stay on downwind.
        instruction is ExtendDownwind ->
            mission.copy(activeConstraints = mission.activeConstraints + ActiveConstraint.ExtendingDownwind)

        // TurnBase → remove extend constraint, resume base turn.
        instruction is TurnBase ->
            mission.copy(activeConstraints = mission.activeConstraints - ActiveConstraint.ExtendingDownwind)

        // ClearedToLand → advance past AWAIT_LANDING_CLEARANCE.
        instruction is ClearedToLand && step == MissionStep.AWAIT_LANDING_CLEARANCE ->
            mission.copy(currentStepIndex = mission.currentStepIndex + 1, stepEnteredAt = now)

        // ClearedTouchAndGo → same as ClearedToLand for mission advancement.
        instruction is ClearedTouchAndGo && step == MissionStep.AWAIT_LANDING_CLEARANCE ->
            mission.copy(currentStepIndex = mission.currentStepIndex + 1, stepEnteredAt = now)

        // AfterLandingVacateVia → advance past AWAIT_VACATE_INSTRUCTION.
        instruction is AfterLandingVacateVia && step == MissionStep.AWAIT_VACATE_INSTRUCTION ->
            mission.copy(currentStepIndex = mission.currentStepIndex + 1, stepEnteredAt = now)

        // TaxiTo (post-landing) → advance past AWAIT_VACATE_INSTRUCTION or TAXI_TO_STAND entry.
        instruction is TaxiTo &&
            (step == MissionStep.AWAIT_VACATE_INSTRUCTION || step == MissionStep.TAXI_TO_STAND) -> {
            val taxiIdx = mission.steps.indexOf(MissionStep.TAXI_TO_STAND)
            mission.copy(currentStepIndex = taxiIdx.coerceAtLeast(mission.currentStepIndex), stepEnteredAt = now)
        }

        // GoAround → interrupt to GOING_AROUND state.
        instruction is GoAround -> goAroundMission(mission, now)
        instruction is BreakOff -> goAroundMission(mission, now)

        // ContactFrequency → reset contact flag.
        instruction is ContactFrequency ->
            mission.copy(contactedOnFrequency = false)

        else -> mission
    }
}

/** Transition mission to go-around state. Pilot notifies tower, then awaits instruction. */
private fun goAroundMission(mission: PilotMission, now: SimTime): PilotMission {
    // Insert GOING_AROUND + AWAITING_ATC_INSTRUCTION at current position,
    // then append remaining circuit steps for the re-sequence.
    val circuitSteps = listOf(
        MissionStep.GOING_AROUND,
        MissionStep.AWAITING_ATC_INSTRUCTION,
        MissionStep.FLY_DEPARTURE,
        MissionStep.FLY_DOWNWIND,
        MissionStep.REPORT_DOWNWIND,
        MissionStep.AWAIT_SEQUENCING,
        MissionStep.FLY_BASE,
        MissionStep.FLY_FINAL,
        MissionStep.AWAIT_LANDING_CLEARANCE,
        MissionStep.LAND,
        MissionStep.REPORT_RUNWAY_VACATED,
        MissionStep.AWAIT_VACATE_INSTRUCTION,
        MissionStep.TAXI_TO_STAND,
        MissionStep.SHUTDOWN,
    )
    return mission.copy(
        steps = circuitSteps,
        currentStepIndex = 0,
        stepEnteredAt = now,
        activeConstraints = emptySet(),
        lastReportedLeg = null,
    )
}

/**
 * Update lastReportedLeg after a position report transmission.
 */
fun updateAfterReport(mission: PilotMission, event: ReportEvent): PilotMission = when (event) {
    is ReportEvent.Downwind -> mission.copy(lastReportedLeg = LegName.DOWNWIND)
    is ReportEvent.Base -> mission.copy(lastReportedLeg = LegName.BASE)
    is ReportEvent.Final -> mission.copy(lastReportedLeg = LegName.FINAL)
    is ReportEvent.RunwayVacated -> mission.copy(lastReportedLeg = null) // clear for next circuit
    else -> mission
}
