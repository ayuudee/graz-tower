package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.PilotGoal
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
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.Disregard
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.HoldAt
import xyz.easiersaid.twr.protocol.InterceptLocaliser
import xyz.easiersaid.twr.protocol.StopTurn
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.Acknowledge
import xyz.easiersaid.twr.protocol.CancelEmergency
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.protocol.amendFpl
import xyz.easiersaid.twr.protocol.Confirm
import xyz.easiersaid.twr.protocol.Emergency
import xyz.easiersaid.twr.protocol.NegativeContact
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.ResumeOwnNavigation
import xyz.easiersaid.twr.protocol.TurnHeading
import xyz.easiersaid.twr.protocol.Request
import xyz.easiersaid.twr.protocol.SayAgain
import xyz.easiersaid.twr.protocol.TrafficInSight
import xyz.easiersaid.twr.protocol.RequestStartup
import xyz.easiersaid.twr.protocol.RequestTaxi
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.StartupApproved
import xyz.easiersaid.twr.protocol.TaxiTo
import arrow.core.Some
import xyz.easiersaid.twr.protocol.JoinCircuit
import xyz.easiersaid.twr.protocol.JoinType
import xyz.easiersaid.twr.protocol.TurnBase
import xyz.easiersaid.twr.protocol.StopClimbAt

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
    MissionStep.REPORT_RUNWAY_VACATED -> mission.reportedVacated
    MissionStep.CALL_INBOUND -> mission.contactedOnFrequency
    MissionStep.GOING_AROUND -> false // completes after transmitting — same pattern as REPORT_READY
    // Steps that should never reach isReportComplete (wrong CompletionMode).
    MissionStep.REQUEST_STARTUP, MissionStep.AWAIT_STARTUP_APPROVAL, MissionStep.REQUEST_TAXI,
    MissionStep.TAXI_TO_HOLDING, MissionStep.RUN_UP_CHECKS, MissionStep.AWAIT_LINE_UP,
    MissionStep.AWAIT_TAKEOFF_CLEARANCE, MissionStep.FLY_DEPARTURE, MissionStep.FLY_DOWNWIND,
    MissionStep.AWAIT_SEQUENCING, MissionStep.FLY_BASE, MissionStep.FLY_FINAL,
    MissionStep.AWAIT_LANDING_CLEARANCE, MissionStep.LAND, MissionStep.AWAIT_VACATE_INSTRUCTION,
    MissionStep.TAXI_TO_STAND, MissionStep.SHUTDOWN, MissionStep.AWAIT_JOINING_INSTRUCTIONS,
    MissionStep.AWAITING_ATC_INSTRUCTION,
    MissionStep.FLY_SID, MissionStep.FLY_EN_ROUTE, MissionStep.FLY_STAR,
    MissionStep.FLY_APPROACH, MissionStep.FLY_MISSED_APPROACH -> false
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
            val isDeparting = mission.goal is HighLevelGoal.Departure
            LegName.DOWNWIND in legs || (isDeparting && aircraft.phase is PilotPhase.Climbing)
        }
        MissionStep.FLY_DOWNWIND -> LegName.DOWNWIND in legs
        MissionStep.AWAIT_SEQUENCING -> {
            val extending = ActiveConstraint.ExtendingDownwind in mission.activeConstraints
            !extending && (LegName.BASE in legs || LegName.FINAL in legs)
        }
        MissionStep.FLY_BASE -> LegName.FINAL in legs
        MissionStep.FLY_FINAL -> {
            LegName.FINAL in legs && aircraft.phase is PilotPhase.Final
        }
        MissionStep.LAND -> aircraft.phase is PilotPhase.LandingRoll ||
            aircraft.phase is PilotPhase.Vacating || aircraft.phase is PilotPhase.ClearOfRunway
        MissionStep.TAXI_TO_STAND -> aircraft.phase is PilotPhase.Parked
        // IFR steps — completion logic lands with IFR-2/3/4.
        MissionStep.FLY_SID,
        MissionStep.FLY_EN_ROUTE,
        MissionStep.FLY_STAR,
        MissionStep.FLY_APPROACH,
        MissionStep.FLY_MISSED_APPROACH -> false
        // Steps that should never reach isPhysicallyComplete (wrong CompletionMode).
        MissionStep.REQUEST_STARTUP,
        MissionStep.AWAIT_STARTUP_APPROVAL,
        MissionStep.REQUEST_TAXI,
        MissionStep.RUN_UP_CHECKS,
        MissionStep.REPORT_READY,
        MissionStep.AWAIT_LINE_UP,
        MissionStep.AWAIT_TAKEOFF_CLEARANCE,
        MissionStep.REPORT_DOWNWIND,
        MissionStep.REPORT_BASE,
        MissionStep.REPORT_FINAL,
        MissionStep.AWAIT_LANDING_CLEARANCE,
        MissionStep.REPORT_RUNWAY_VACATED,
        MissionStep.AWAIT_VACATE_INSTRUCTION,
        MissionStep.SHUTDOWN,
        MissionStep.CALL_INBOUND,
        MissionStep.AWAIT_JOINING_INSTRUCTIONS,
        MissionStep.GOING_AROUND,
        MissionStep.AWAITING_ATC_INSTRUCTION -> false
    }
}

// ── Transmission generation ──────────────────────────────────────────

@Suppress("CyclomaticComplexMethod")
private fun stepTransmission(
    aircraft: AircraftState,
    mission: PilotMission,
    step: MissionStep,
    now: SimTime,
): PilotTransmission? {
    // Guard: only transmit once per step entry. Prevents flooding the frequency
    // with repeated requests/reports on every tick.
    val isFirstTick = (now.millis - mission.stepEnteredAt.millis) < 1500 // within first 1.5s of step

    return when (step) {
    MissionStep.REQUEST_STARTUP -> if (isFirstTick) Request(RequestStartup()) else null
    MissionStep.REQUEST_TAXI -> if (isFirstTick) Request(RequestTaxi()) else null
    MissionStep.REPORT_READY -> if (isFirstTick) Report(listOf(ReportEvent.Ready)) else null
    MissionStep.CALL_INBOUND -> if (isFirstTick) InitialContact(stationCalled = xyz.easiersaid.twr.protocol.RoleName.TOWER) else null
    MissionStep.REPORT_DOWNWIND ->
        if (mission.lastReportedLeg != LegName.DOWNWIND) {
            // CAP 413 para 4.50/4.51: qualify downwind with circuit intent.
            val intent = when (derivePilotGoal(mission)) {
                PilotGoal.TOUCH_AND_GO -> CircuitIntent.TOUCH_AND_GO
                PilotGoal.ARRIVE -> CircuitIntent.FULL_STOP
                PilotGoal.DEPART, PilotGoal.TRANSIT -> null
            }
            Report(listOf(ReportEvent.Downwind(intent)), mission.activeRunway)
        } else null
    MissionStep.REPORT_BASE ->
        if (mission.lastReportedLeg != LegName.BASE) Report(listOf(ReportEvent.Base), mission.activeRunway) else null
    MissionStep.REPORT_FINAL ->
        if (mission.lastReportedLeg != LegName.FINAL) Report(listOf(ReportEvent.Final), mission.activeRunway) else null
    MissionStep.AWAIT_LANDING_CLEARANCE -> {
        val elapsed = now.millis - mission.stepEnteredAt.millis
        // Escalation: query controller after 15s with no clearance.
        // Real pilot would say "[callsign], final, request landing clearance" — not repeat the position report.
        if (elapsed in 15_000..16_500) Report(listOf(ReportEvent.Final)) else null
    }
    MissionStep.REPORT_RUNWAY_VACATED -> {
        val isOff = aircraft.phase is PilotPhase.ClearOfRunway || aircraft.phase is PilotPhase.Taxiing
        // Real pilot combines: "runway vacated, request taxi to stand"
        if (isOff && isFirstTick) Report(listOf(ReportEvent.RunwayVacated)) else null
    }
    MissionStep.GOING_AROUND -> if (isFirstTick) Report(listOf(ReportEvent.GoingAround)) else null
    // Steps with no pilot-initiated transmission.
    MissionStep.AWAIT_STARTUP_APPROVAL,
    MissionStep.TAXI_TO_HOLDING,
    MissionStep.RUN_UP_CHECKS,
    MissionStep.AWAIT_LINE_UP,
    MissionStep.AWAIT_TAKEOFF_CLEARANCE,
    MissionStep.FLY_DEPARTURE,
    MissionStep.FLY_DOWNWIND,
    MissionStep.AWAIT_SEQUENCING,
    MissionStep.FLY_BASE,
    MissionStep.FLY_FINAL,
    MissionStep.LAND,
    MissionStep.AWAIT_VACATE_INSTRUCTION,
    MissionStep.TAXI_TO_STAND,
    MissionStep.SHUTDOWN,
    MissionStep.AWAIT_JOINING_INSTRUCTIONS,
    MissionStep.AWAITING_ATC_INSTRUCTION,
    // IFR steps — transmissions land with IFR implementation.
    MissionStep.FLY_SID,
    MissionStep.FLY_EN_ROUTE,
    MissionStep.FLY_STAR,
    MissionStep.FLY_APPROACH,
    MissionStep.FLY_MISSED_APPROACH -> null
}
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
            val withClearance = mission.copy(hasClearance = true)
            var root = withClearance.root
            // Mark sequencing and flying steps complete, but NOT position reports.
            // The pilot should still report turning base and final even with an early clearance,
            // so the controller maintains situational awareness.
            val stepsToMark = listOf(
                MissionStep.AWAIT_SEQUENCING, MissionStep.FLY_BASE,
                MissionStep.FLY_FINAL, MissionStep.AWAIT_LANDING_CLEARANCE,
            )
            for (s in stepsToMark) { root = root.markComplete(s) }
            withClearance.copy(root = root, stepEnteredAt = now)
        }

        instruction is AfterLandingVacateVia && step == MissionStep.AWAIT_VACATE_INSTRUCTION ->
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now)

        instruction is TaxiTo && (step == MissionStep.AWAIT_VACATE_INSTRUCTION || step == MissionStep.TAXI_TO_STAND) ->
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now)

        // Go-around: replace the active (incomplete) circuit compound with GO_AROUND + fresh CIRCUIT.
        // Matches Circuit, CircuitAfterGoAround, and TouchAndGo — all circuit pattern variants.
        instruction is GoAround || instruction is BreakOff -> {
            val gaTask = if (mission.navigationMode is NavigationMode.Instrument) ifrGoAroundTask()
                else goAroundTask()
            val newRoot = mission.root.replaceChild(
                predicate = { it is CompoundTask && !it.isComplete && it.name.isCircuitLike() },
                replacement = CompoundTask(TaskName.CircuitAfterGoAround, listOf(
                    gaTask,
                    circuitTask(),
                )),
            )
            mission.resetForGoAround(now).copy(root = newRoot)
        }

        // JoinCircuit: ATC instructs the joining leg for the arriving aircraft.
        // Marks the AWAIT_JOINING_INSTRUCTIONS step complete and stores the join leg
        // so planRouteIfNeeded can build the circuit route from the correct point (A12).
        instruction is JoinCircuit && step == MissionStep.AWAIT_JOINING_INSTRUCTIONS ->
            mission.copy(
                root = mission.root.markComplete(step),
                joinLeg = Some(instruction.joinType.toCircuitLeg()),
                stepEnteredAt = now,
            )

        // TaxiTo while awaiting joining: also completes AWAIT_JOINING_INSTRUCTIONS
        // (ground controller may use TaxiTo rather than JoinCircuit in some scenarios).
        instruction is TaxiTo && step == MissionStep.AWAIT_JOINING_INSTRUCTIONS ->
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now)

        instruction is ContactFrequency ->
            mission.copy(contactedOnFrequency = false)

        // Route overrides: vectors and holds temporarily suspend FPL-based routing.
        instruction is FlyHeading ->
            mission.copy(routeOverride = RouteOverride.Vectoring(instruction.heading))
        instruction is TurnHeading ->
            mission.copy(routeOverride = RouteOverride.Vectoring(instruction.heading))
        instruction is StopTurn && instruction.rollOutHeading != null -> {
            val heading = instruction.rollOutHeading!!
            mission.copy(routeOverride = RouteOverride.Vectoring(heading))
        }
        instruction is InterceptLocaliser ->
            mission.copy(routeOverride = null) // localiser capture ends vectoring
        instruction is HoldAt ->
            mission.copy(routeOverride = RouteOverride.Holding(instruction.hold))
        instruction is ResumeOwnNavigation ->
            mission.copy(routeOverride = null)

        // ATC altitude restrictions: cap the pilot's climb at the instructed level.
        // The continuous route planner reads altitudeRestrictionM and caps targetAltitudeM.
        instruction is StopClimbAt ->
            mission.copy(altitudeRestrictionM = levelToMeters(instruction.level))

        // Disregard: undo the most recent constraint/override.
        instruction is Disregard -> mission.copy(
            activeConstraints = mission.activeConstraints - ActiveConstraint.ExtendingDownwind,
            routeOverride = null,
        )

        // Intentional catch-all: an instruction arriving when the mission is at an
        // irrelevant step is normal (e.g., TaxiTo at FLY_DEPARTURE). The contract is
        // "apply if relevant to the current step, otherwise ignore." This differs from
        // updateAfterReport where every event type must be consciously handled. A
        // condition-based when cannot be compiler-exhaustive over ~40 AtcInstruction
        // subtypes × ~20 MissionSteps, so this else is the designed default.
        else -> mission
    }.let { result -> applyFplAmendment(result, instruction) }
}

/**
 * For IFR missions, apply FPL amendments from ATC instructions.
 *
 * Called after the main processInstruction logic. If the mission has a
 * [NavigationMode.Instrument], runs [amendFpl] and updates the navigation
 * mode. Non-IFR missions are returned unchanged. Amendment errors are
 * silently ignored (the instruction may not be an FPL-relevant instruction).
 */
private fun applyFplAmendment(
    mission: PilotMission,
    instruction: AtcInstruction,
): PilotMission {
    val mode = mission.navigationMode as? NavigationMode.Instrument ?: return mission
    val amended = amendFpl(mode.fpl, instruction).getOrNull() ?: return mission
    if (amended == mode.fpl) return mission // no change
    return mission.copy(navigationMode = NavigationMode.Instrument(amended))
}

/** Update lastReportedLeg after a position report transmission. */
/** Update mission after any pilot transmission. */
fun updateAfterTransmission(mission: PilotMission, tx: PilotTransmission): PilotMission = when (tx) {
    is Report -> tx.events.fold(mission) { m, evt -> updateAfterReport(m, evt) }
    is InitialContact -> mission.copy(contactedOnFrequency = true)
    // Readbacks, acknowledgements, requests, and comms management — no mission effect.
    is Readback,
    is Request,
    is Acknowledge,
    is TrafficInSight,
    is NegativeContact,
    is SayAgain,
    is Confirm -> mission
    // Emergency state changes — no mission effect until emergency handling is implemented.
    is Emergency,
    is CancelEmergency -> mission
}

/**
 * Map an ATC-issued [JoinType] to the circuit [LegName] where the route should start.
 *
 * This determines where [buildCircuitFromLeg] begins the pilot's circuit route.
 * The mapping is exhaustive with no fallback so adding a new [JoinType] variant
 * forces a conscious decision.
 *
 * Sources: CAP 413 Ch. 2 (overhead join), ICAO Doc 4444 (straight-in / final approach).
 */
fun JoinType.toCircuitLeg(): xyz.easiersaid.twr.core.world.LegName = when (this) {
    JoinType.DOWNWIND, JoinType.MID_DOWNWIND -> xyz.easiersaid.twr.core.world.LegName.DOWNWIND
    JoinType.BASE -> xyz.easiersaid.twr.core.world.LegName.BASE
    JoinType.STRAIGHT_IN, JoinType.LONG_FINAL -> xyz.easiersaid.twr.core.world.LegName.FINAL
    JoinType.CROSSWIND -> xyz.easiersaid.twr.core.world.LegName.CROSSWIND
    // Overhead join: aircraft crosses overhead, descends on dead side, joins at downwind.
    // The circuit route starts at DOWNWIND — that is where ATC next sequences the aircraft.
    JoinType.OVERHEAD -> xyz.easiersaid.twr.core.world.LegName.DOWNWIND
}

fun updateAfterReport(mission: PilotMission, event: ReportEvent): PilotMission = when (event) {
    is ReportEvent.Downwind -> mission.copy(lastReportedLeg = LegName.DOWNWIND)
    is ReportEvent.Base -> mission.copy(lastReportedLeg = LegName.BASE)
    is ReportEvent.Final -> mission.copy(lastReportedLeg = LegName.FINAL)
    is ReportEvent.RunwayVacated -> mission.copy(lastReportedLeg = null, reportedVacated = true)
    is ReportEvent.Ready -> mission.copy(root = mission.root.markComplete(MissionStep.REPORT_READY))
    // Position/state reports — no mission effect (pilot's position is tracked by physics).
    is ReportEvent.LongFinal,
    is ReportEvent.Airborne,
    is ReportEvent.Established,
    is ReportEvent.EstablishedLocaliser,
    is ReportEvent.EstablishedGlidepath,
    is ReportEvent.VisualWithField,
    is ReportEvent.EstablishedInHold,
    is ReportEvent.PassingLevel,
    is ReportEvent.LeavingLevel,
    is ReportEvent.DistanceDme,
    is ReportEvent.OverFix -> mission
    // Go-around: mark the GOING_AROUND step complete (same trigger pattern as REPORT_READY).
    // Mission replanning (subtree replacement) happens in processInstruction(GoAround).
    is ReportEvent.GoingAround -> mission.copy(root = mission.root.markComplete(MissionStep.GOING_AROUND))
    // Safety/urgency reports — no mission effect until emergency handling is implemented.
    is ReportEvent.TcasRa,
    is ReportEvent.MinimumFuel -> mission
}
