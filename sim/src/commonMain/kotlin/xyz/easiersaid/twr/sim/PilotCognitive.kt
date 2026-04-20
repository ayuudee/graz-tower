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

data class CognitiveDecision(
    val transmissions: List<PilotTransmission>,
    val updatedMission: PilotMission,
)

/**
 * Main cognitive decision: advance the HTN, generate transmissions.
 */
@Suppress("LoopWithTooManyJumpStatements")
fun pilotCognitiveDecide(
    aircraft: AircraftState,
    mission: PilotMission,
    worldIndex: WorldIndex,
    now: SimTime,
): CognitiveDecision {
    if (mission.isComplete) return CognitiveDecision(emptyList(), mission)
    val task = mission.currentTask ?: return CognitiveDecision(emptyList(), mission)

    val transmissions = mutableListOf<PilotTransmission>()

    // Advance completed steps in the tree.
    var updated = mission
    var safety = 0
    while (!updated.isComplete && safety < 5) {
        val current = updated.currentTask ?: break
        if (!isStepComplete(aircraft, updated, current, worldIndex, now)) break
        updated = updated.copy(root = updated.root.markComplete(current.step), stepEnteredAt = now)
        safety++
    }

    // Generate transmission for current step.
    val currentAfterAdvance = updated.currentTask
    if (currentAfterAdvance != null) {
        val tx = stepTransmission(aircraft, updated, currentAfterAdvance.step, now)
        if (tx != null) transmissions.add(tx)
    }

    return CognitiveDecision(transmissions, updated)
}

// ── Step completion ──────────────────────────────────────────────────

@Suppress("CyclomaticComplexMethod")
private fun isStepComplete(
    aircraft: AircraftState,
    mission: PilotMission,
    task: PrimitiveTask,
    worldIndex: WorldIndex,
    now: SimTime,
): Boolean = when (task.completionMode) {
    CompletionMode.INSTANT -> true
    CompletionMode.TIMED -> !aircraft.humanPiloted || (now.millis - mission.stepEnteredAt.millis > 10_000)
    CompletionMode.INSTRUCTION_GATED -> false // only completed by processInstruction
    CompletionMode.REPORTED -> isReportComplete(mission, task.step)
    CompletionMode.PHYSICAL -> isPhysicallyComplete(aircraft, mission, task.step, worldIndex)
}

private fun isReportComplete(mission: PilotMission, step: MissionStep): Boolean = when (step) {
    MissionStep.REPORT_DOWNWIND -> mission.lastReportedLeg == LegName.DOWNWIND
    MissionStep.REPORT_BASE -> mission.lastReportedLeg == LegName.BASE
    MissionStep.REPORT_FINAL -> mission.lastReportedLeg == LegName.FINAL
    MissionStep.REPORT_READY -> false // completes after transmitting — handled via transmission trigger
    MissionStep.REPORT_RUNWAY_VACATED -> {
        val isOff = aircraft@ run {
            // Can't check aircraft here — we don't have it. Use lastReportedLeg as proxy.
            mission.lastReportedLeg == null // cleared after runway-vacated report
        }
        isOff
    }
    MissionStep.CALL_INBOUND -> mission.contactedOnFrequency
    MissionStep.GOING_AROUND -> true // reported immediately, advance to AWAITING_ATC_INSTRUCTION
    else -> false
}

@Suppress("CyclomaticComplexMethod")
private fun isPhysicallyComplete(
    aircraft: AircraftState,
    mission: PilotMission,
    step: MissionStep,
    worldIndex: WorldIndex,
): Boolean {
    val legs = worldIndex.circuitLegsByPoint[aircraft.positionPoint] ?: emptySet()
    return when (step) {
        MissionStep.TAXI_TO_HOLDING -> aircraft.phase is PilotPhase.HoldingShort
        MissionStep.FLY_DEPARTURE -> {
            val isDeparting = mission.goal == xyz.easiersaid.twr.controller.PilotGoal.DEPART
            LegName.DOWNWIND in legs || (isDeparting && aircraft.phase is PilotPhase.Climbing)
        }
        MissionStep.FLY_DOWNWIND -> LegName.DOWNWIND in legs
        MissionStep.AWAIT_SEQUENCING -> {
            val extending = ActiveConstraint.ExtendingDownwind in mission.activeConstraints
            !extending && (LegName.BASE in legs || LegName.FINAL in legs)
        }
        MissionStep.FLY_BASE -> LegName.FINAL in legs
        MissionStep.FLY_FINAL -> {
            // Not a no-op: complete only when past a meaningful distance on final.
            // Use phase as proxy — aircraft must be physically on Final phase.
            LegName.FINAL in legs && aircraft.phase is PilotPhase.Final
        }
        MissionStep.LAND -> aircraft.phase is PilotPhase.Vacating || aircraft.phase is PilotPhase.ClearOfRunway
        MissionStep.TAXI_TO_STAND -> aircraft.phase is PilotPhase.Parked
        else -> false
    }
}

// ── Transmission generation ──────────────────────────────────────────

@Suppress("CyclomaticComplexMethod")
private fun stepTransmission(
    aircraft: AircraftState,
    mission: PilotMission,
    step: MissionStep,
    now: SimTime,
): PilotTransmission? = when (step) {
    MissionStep.REQUEST_STARTUP -> Request(RequestStartup())
    MissionStep.REQUEST_TAXI -> Request(RequestTaxi())
    MissionStep.REPORT_READY -> Report(listOf(ReportEvent.Ready))
    MissionStep.CALL_INBOUND -> Report(listOf(ReportEvent.Downwind)) // inbound call as downwind report for now
    MissionStep.REPORT_DOWNWIND -> if (mission.lastReportedLeg != LegName.DOWNWIND) Report(listOf(ReportEvent.Downwind)) else null
    MissionStep.REPORT_BASE -> if (mission.lastReportedLeg != LegName.BASE) Report(listOf(ReportEvent.Base)) else null
    MissionStep.REPORT_FINAL -> if (mission.lastReportedLeg != LegName.FINAL) Report(listOf(ReportEvent.Final)) else null
    MissionStep.AWAIT_LANDING_CLEARANCE -> {
        val elapsed = now.millis - mission.stepEnteredAt.millis
        if (elapsed > 15_000) Report(listOf(ReportEvent.Final)) else null // escalation prompt
    }
    MissionStep.REPORT_RUNWAY_VACATED -> {
        val isOff = aircraft.phase is PilotPhase.ClearOfRunway || aircraft.phase is PilotPhase.Taxiing
        if (isOff) Report(listOf(ReportEvent.RunwayVacated)) else null
    }
    MissionStep.GOING_AROUND -> Report(listOf(ReportEvent.GoingAround))
    else -> null
}

// ── Instruction processing ───────────────────────────────────────────

/**
 * Process an ATC instruction. Updates the mission by marking steps complete
 * or modifying constraints. Uses step identity (name), not index arithmetic.
 */
@Suppress("CyclomaticComplexMethod")
fun processInstruction(
    instruction: AtcInstruction,
    mission: PilotMission,
    now: SimTime,
): PilotMission {
    val task = mission.currentTask ?: return mission
    val step = task.step

    return when {
        instruction is StartupApproved && step == MissionStep.AWAIT_STARTUP_APPROVAL ->
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now)

        instruction is TaxiTo && step == MissionStep.REQUEST_TAXI ->
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now)

        instruction is LineUpAndWait && step == MissionStep.AWAIT_LINE_UP ->
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now)

        instruction is ClearedForTakeoff && (step == MissionStep.AWAIT_TAKEOFF_CLEARANCE || step == MissionStep.AWAIT_LINE_UP) ->
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now)

        instruction is ExtendDownwind ->
            mission.copy(activeConstraints = mission.activeConstraints + ActiveConstraint.ExtendingDownwind)

        instruction is TurnBase ->
            mission.copy(activeConstraints = mission.activeConstraints - ActiveConstraint.ExtendingDownwind)

        instruction is ClearedToLand || instruction is ClearedTouchAndGo -> {
            // Landing clearance can arrive at any point during the approach sequence.
            // Mark all approach steps up to AWAIT_LANDING_CLEARANCE as complete.
            var root = mission.root
            for (s in listOf(
                MissionStep.AWAIT_SEQUENCING, MissionStep.FLY_BASE, MissionStep.REPORT_BASE,
                MissionStep.FLY_FINAL, MissionStep.REPORT_FINAL, MissionStep.AWAIT_LANDING_CLEARANCE,
            )) {
                root = root.markComplete(s)
            }
            mission.copy(root = root, stepEnteredAt = now)
        }

        instruction is AfterLandingVacateVia && step == MissionStep.AWAIT_VACATE_INSTRUCTION ->
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now)

        instruction is TaxiTo && (step == MissionStep.AWAIT_VACATE_INSTRUCTION || step == MissionStep.TAXI_TO_STAND) ->
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now)

        // Go-around: replace the CIRCUIT compound with GO_AROUND + fresh CIRCUIT.
        instruction is GoAround || instruction is BreakOff -> {
            val newRoot = mission.root.replaceChild(
                predicate = { it is CompoundTask && it.name == "CIRCUIT" },
                replacement = CompoundTask("CIRCUIT_AFTER_GA", listOf(
                    goAroundTask(),
                    circuitTask(), // single source of truth — reuses the same decomposition
                )),
            )
            mission.copy(
                root = newRoot, stepEnteredAt = now,
                activeConstraints = emptySet(), lastReportedLeg = null,
            )
        }

        // Joining instructions for arriving aircraft.
        instruction is TaxiTo && step == MissionStep.AWAIT_JOINING_INSTRUCTIONS ->
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now)

        instruction is ContactFrequency ->
            mission.copy(contactedOnFrequency = false)

        else -> mission
    }
}

/** Update lastReportedLeg after a position report transmission. */
fun updateAfterReport(mission: PilotMission, event: ReportEvent): PilotMission = when (event) {
    is ReportEvent.Downwind -> mission.copy(lastReportedLeg = LegName.DOWNWIND)
    is ReportEvent.Base -> mission.copy(lastReportedLeg = LegName.BASE)
    is ReportEvent.Final -> mission.copy(lastReportedLeg = LegName.FINAL)
    is ReportEvent.RunwayVacated -> mission.copy(lastReportedLeg = null)
    is ReportEvent.Ready -> mission.copy(root = mission.root.markComplete(MissionStep.REPORT_READY))
    else -> mission
}
