package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AfterPassingLevelClimbTo
import xyz.easiersaid.twr.protocol.AfterPassingLevelDescendTo
import xyz.easiersaid.twr.protocol.AirTaxiTo
import xyz.easiersaid.twr.protocol.AvoidArea
import xyz.easiersaid.twr.protocol.AvoidLevel
import xyz.easiersaid.twr.protocol.CancelClearance
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ClearedLowApproach
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ClearedToEnterControlZone
import xyz.easiersaid.twr.protocol.ClearedVisualApproach
import xyz.easiersaid.twr.protocol.ClimbTo
import xyz.easiersaid.twr.protocol.CommenceApproachAt
import xyz.easiersaid.twr.protocol.ConditionalClearance
import xyz.easiersaid.twr.protocol.ConfirmSquawk
import xyz.easiersaid.twr.protocol.ContinueApproach
import xyz.easiersaid.twr.protocol.ContinuePresentHeading
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.DescendTo
import xyz.easiersaid.twr.protocol.DescendWhenReady
import xyz.easiersaid.twr.protocol.DivertTo
import xyz.easiersaid.twr.protocol.ExpediteClimb
import xyz.easiersaid.twr.protocol.ExpediteDescend
import xyz.easiersaid.twr.protocol.ExpediteTaxi
import xyz.easiersaid.twr.protocol.FollowTraffic
import xyz.easiersaid.twr.protocol.GiveWayToTraffic
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.HoldPositionCancelTakeoff
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.IncreaseSpeedTo
import xyz.easiersaid.twr.protocol.JoinAirway
import xyz.easiersaid.twr.protocol.LeaveHoldProceedDirect
import xyz.easiersaid.twr.protocol.MaintainAltitudeUntilEstablished
import xyz.easiersaid.twr.protocol.MaintainAtOrAbove
import xyz.easiersaid.twr.protocol.MaintainAtOrBelow
import xyz.easiersaid.twr.protocol.MaintainLevel
import xyz.easiersaid.twr.protocol.MaintainSpeed
import xyz.easiersaid.twr.protocol.MaintainVisualSeparation
import xyz.easiersaid.twr.protocol.MakeAnotherCircuit
import xyz.easiersaid.twr.protocol.MakeLongApproach
import xyz.easiersaid.twr.protocol.MakeShortApproach
import xyz.easiersaid.twr.protocol.MinimumCleanSpeed
import xyz.easiersaid.twr.protocol.MonitorFrequency
import xyz.easiersaid.twr.protocol.NumberInSequence
import xyz.easiersaid.twr.protocol.Orbit
import xyz.easiersaid.twr.protocol.ProceedDirect
import xyz.easiersaid.twr.protocol.PushbackApproved
import xyz.easiersaid.twr.protocol.PushbackFace
import xyz.easiersaid.twr.protocol.ReduceSpeedTo
import xyz.easiersaid.twr.protocol.ReduceTaxiSpeed
import xyz.easiersaid.twr.protocol.RejoinSidAt
import xyz.easiersaid.twr.protocol.RemainOutsideControlledAirspace
import xyz.easiersaid.twr.protocol.ReportIntentions
import xyz.easiersaid.twr.protocol.ReportTrafficInSight
import xyz.easiersaid.twr.protocol.ReportWhen
import xyz.easiersaid.twr.protocol.ResumeNormalSpeed
import xyz.easiersaid.twr.protocol.RouteAsFiled
import xyz.easiersaid.twr.protocol.RunwayInUseAdvisory
import xyz.easiersaid.twr.protocol.SetPressure
import xyz.easiersaid.twr.protocol.RadarServiceTerminated
import xyz.easiersaid.twr.protocol.SetSquawk
import xyz.easiersaid.twr.protocol.SpecialVfrClearance
import xyz.easiersaid.twr.protocol.SquawkIdent
import xyz.easiersaid.twr.protocol.SquawkNormal
import xyz.easiersaid.twr.protocol.SquawkStandby
import xyz.easiersaid.twr.protocol.StopDescentAt
import xyz.easiersaid.twr.protocol.StopImmediately
import xyz.easiersaid.twr.protocol.StopSquawk
import xyz.easiersaid.twr.protocol.TakeoffImmediatelyOrHoldShort
import xyz.easiersaid.twr.protocol.TakeoffImmediatelyOrVacateRunway
import xyz.easiersaid.twr.protocol.TaxiIntoHoldingBay
import xyz.easiersaid.twr.protocol.TaxiViaRunway
import xyz.easiersaid.twr.protocol.TaxiWithCaution
import xyz.easiersaid.twr.protocol.TransitionLevelIssuance
import xyz.easiersaid.twr.protocol.TurnByDegrees
import xyz.easiersaid.twr.protocol.VacateRunway
import xyz.easiersaid.twr.protocol.WhenAbleProceedDirect
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.BreakOff
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.applyPrecedence
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
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TaxiToStand
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.getOrElse
import xyz.easiersaid.twr.protocol.JoinCircuit
import xyz.easiersaid.twr.protocol.JoinType
import xyz.easiersaid.twr.protocol.TurnBase
import xyz.easiersaid.twr.protocol.StopClimbAt
import xyz.easiersaid.twr.protocol.ReadbackElement
import xyz.easiersaid.twr.protocol.SimpleElement
import xyz.easiersaid.twr.protocol.requiredReadbackAtoms
import xyz.easiersaid.twr.protocol.AcknowledgeEmergency
import xyz.easiersaid.twr.protocol.CautionWakeTurbulence
import xyz.easiersaid.twr.protocol.ControllerResponse
import xyz.easiersaid.twr.protocol.CurrentInformationIs
import xyz.easiersaid.twr.protocol.ExpectApproach
import xyz.easiersaid.twr.protocol.ExpectVectors
import xyz.easiersaid.twr.protocol.Identified
import xyz.easiersaid.twr.protocol.NotIdentified
import xyz.easiersaid.twr.protocol.RadarContact
import xyz.easiersaid.twr.protocol.ConfirmInstruction
import xyz.easiersaid.twr.protocol.ReadBackCorrect
import xyz.easiersaid.twr.protocol.ReadbackCorrection
import xyz.easiersaid.twr.protocol.TransmittingBlind
import xyz.easiersaid.twr.protocol.Standby
import xyz.easiersaid.twr.protocol.RunwayObstructionInformation
import xyz.easiersaid.twr.protocol.TrafficInformation

/**
 * Build a [Readback] for the given [instruction]. Pilot-cognitive function:
 * decides what atoms the pilot reads back (deferred to the protocol-level
 * [requiredReadbackAtoms] mapping). Returns [None] when the instruction has
 * no readback obligation.
 *
 * Moved from `Step.kt` to the pilot layer in Pass 3 — readback content is a
 * cognitive decision; sim's role is delivery, not cognition.
 */
fun buildReadback(instruction: AtcInstruction): Option<Readback> {
    val atoms = requiredReadbackAtoms(instruction)
    if (atoms.isEmpty()) return None
    val elements = atoms.map<_, ReadbackElement> { SimpleElement(it) }
    return Some(Readback(elements = elements))
}

// ── ControllerResponse handling (Pass 3) ────────────────────────────

/**
 * Outcome of [processControllerResponse]: a possibly-updated [PilotMission]
 * and an optional [PilotTransmission] to send back (e.g. a corrected readback
 * for a [ReadbackCorrection]).
 */
data class ResponseReaction(
    val mission: PilotMission,
    val transmission: Option<PilotTransmission> = None,
) {
    companion object {
        /** Convenience: mission unchanged, no transmission. */
        fun silent(mission: PilotMission): ResponseReaction = ResponseReaction(mission, None)
    }
}

/**
 * Pilot-side reaction to a [ControllerResponse]. Per-leaf exhaustive over
 * the sealed hierarchy — every leaf has an explicit arm. New leaves added
 * to the protocol must have their pilot reaction decided here; the
 * compiler enforces it. `ExhaustivenessTest` (pilot/jvmTest) is the
 * anti-regression contract against future category-arm absorption.
 *
 * Today only [ReadbackCorrection] has a non-trivial reaction (retransmit
 * the corrected readback per ICAO 4444 §12.3.2). The other 11 leaves are
 * cognitive-only — the pilot's situational awareness updates but no
 * [PilotMission] field captures these in the current model.
 * [PilotMission] is the planner's state; pilot beliefs about traffic,
 * weather hints, and expectation cues live elsewhere (today, ambient).
 */
@Suppress("CyclomaticComplexMethod") // intrinsic to ControllerResponse's leaf count
fun processControllerResponse(
    response: ControllerResponse,
    mission: PilotMission,
): ResponseReaction = when (response) {
    is ReadBackCorrect -> ResponseReaction.silent(mission)
    is ReadbackCorrection -> handleReadbackCorrection(response, mission)
    // Pass 9 (D-AUDIT.2): controller asks the pilot to confirm a prior
    // instruction. The pilot's mission already encodes the instruction's
    // intent (processed when first received); the response is a
    // verification round-trip — re-emit the readback for the named
    // instruction. Same shape as ReadbackCorrection's pattern.
    is ConfirmInstruction -> ResponseReaction(mission = mission, transmission = buildReadback(response.instruction))
    // Pass 12 (D-AUDIT.2.A): controller has declared lost-comms internally.
    // "TRANSMITTING BLIND" is one-way; the pilot doesn't read back. If the
    // pilot can hear, they comply silently with the embedded instruction.
    is TransmittingBlind -> ResponseReaction.silent(mission)
    is Standby -> ResponseReaction.silent(mission)
    is Identified -> ResponseReaction.silent(mission)
    is NotIdentified -> ResponseReaction.silent(mission)
    is RadarContact -> ResponseReaction.silent(mission)
    is AcknowledgeEmergency -> ResponseReaction.silent(mission)
    is TrafficInformation -> ResponseReaction.silent(mission)
    // fn-12 (R8): obstruction-info companion. The pilot's actionable input
    // is the separate `Instruction.GoAround` the controller dispatches
    // alongside this transmission — the GA reaction is driven there. The
    // obstruction-info itself is cognitive-only (situational awareness;
    // no PilotMission field captures it today, mirroring TrafficInformation).
    is RunwayObstructionInformation -> ResponseReaction.silent(mission)
    is CautionWakeTurbulence -> ResponseReaction.silent(mission)
    is ExpectApproach -> ResponseReaction.silent(mission)
    is ExpectVectors -> ResponseReaction.silent(mission)
    // Pass 15 (D-AUDIT.8): controller advisory that the pilot's
    // acknowledged ATIS letter is stale. Per ICAO Annex 11 §4.3.6 the
    // pilot acknowledges silently and obtains the current ATIS on a
    // separate frequency — no readback obligation, no mission-state
    // change. The cognitive layer's situational awareness updates
    // ambiently (the pilot now knows their information is stale).
    is CurrentInformationIs -> ResponseReaction.silent(mission)
}

private fun handleReadbackCorrection(
    correction: ReadbackCorrection,
    mission: PilotMission,
): ResponseReaction {
    // Per ICAO 4444 §12.3.2 / CAP 413 §1.5.6: the controller said
    // "NEGATIVE, I SAY AGAIN, …" with the corrected instruction. The pilot
    // re-reads the correct atoms back. The mission's understanding of the
    // instruction was already set by the original processInstruction call;
    // the correction is a verification round-trip, not a re-execution.
    return ResponseReaction(mission = mission, transmission = buildReadback(correction.correct))
}

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
    atisByAerodrome: Map<xyz.easiersaid.twr.protocol.AerodromeId, xyz.easiersaid.twr.protocol.Atis> = emptyMap(),
): CognitiveDecision {
    if (mission.isComplete) return CognitiveDecision(emptyList(), mission)
    if (mission.currentTask == null) return CognitiveDecision(emptyList(), mission)

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
        val tx = stepTransmission(aircraft, updated, currentAfterAdvance.step, now, atisByAerodrome)
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
    // TIMED steps complete on time elapsed for everyone — same treatment for AI
    // and human pilots. Pass 13 (D-AUDIT.3 closure): per-type duration on
    // [AircraftType.runUpDurationMs]. C172 = 60 s (POH §4); B738 = 600 s
    // (FCOM NP cold-start sequence). RUN_UP_CHECKS is the only TIMED step
    // today; per-step lookup if more land is filed as D-AUDIT.3.II-FOLLOWUP.
    CompletionMode.TIMED -> (now.millis - mission.stepEnteredAt.millis) > aircraft.type.runUpDurationMs
    CompletionMode.INSTRUCTION_GATED -> false // only completed by processInstruction
    CompletionMode.REPORTED -> isReportComplete(mission, task.step)
    CompletionMode.PHYSICAL -> isPhysicallyComplete(aircraft, mission, task.step, worldIndex)
}

private fun isReportComplete(mission: PilotMission, step: MissionStep): Boolean = when (step) {
    MissionStep.REPORT_DOWNWIND -> mission.lastReportedLeg == Some(LegName.DOWNWIND)
    MissionStep.REPORT_BASE -> mission.lastReportedLeg == Some(LegName.BASE)
    MissionStep.REPORT_FINAL -> mission.lastReportedLeg == Some(LegName.FINAL)
    MissionStep.REPORT_READY -> false // completes after transmitting — handled via transmission trigger
    MissionStep.REPORT_RUNWAY_VACATED -> mission.reportedVacated
    MissionStep.CALL_INBOUND -> mission.contactedOnFrequency
    MissionStep.GOING_AROUND -> false // completes after transmitting — same pattern as REPORT_READY
    // Steps that should never reach isReportComplete (wrong CompletionMode).
    MissionStep.REQUEST_STARTUP, MissionStep.AWAIT_STARTUP_APPROVAL, MissionStep.REQUEST_TAXI,
    MissionStep.TAXI_TO_HOLDING, MissionStep.RUN_UP_CHECKS, MissionStep.AWAIT_LINE_UP,
    MissionStep.AWAIT_TAKEOFF_CLEARANCE, MissionStep.FLY_DEPARTURE, MissionStep.FLY_DOWNWIND,
    MissionStep.AWAIT_SEQUENCING, MissionStep.FLY_BASE, MissionStep.FLY_FINAL,
    MissionStep.FLY_FINAL_TO_SHORT_FINAL,
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
            // G2 Phase C: cross-aerodrome Transit completes FLY_DEPARTURE when
            // the aircraft has reached the destination's published contact REP
            // (resolved by planRoute and stored on mission.transitContactRep).
            // Structural Option equality matches the codebase pattern (cf.
            // mission.lastReportedLeg == Some(LegName.X)). None == Some(_) is
            // false, so a tick-1 mission with unresolved transitContactRep
            // does NOT prematurely complete regardless of aircraft.positionPoint.
            val transitAtRep = mission.goal is HighLevelGoal.Transit &&
                mission.transitContactRep == Some(aircraft.positionPoint)
            LegName.DOWNWIND in legs ||
                (isDeparting && aircraft.phase is PilotPhase.Climbing) ||
                transitAtRep
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
        // fn-11.1 (G3a-trained): the trained-GA final leg completes when
        // the aircraft crosses short-final altitude (DECISION_ALTITUDE_M,
        // ~100 m / ~330 ft AGL — mirrors `pilot/observe/PilotEvent.kt:48`).
        // Phase-gated to PilotPhase.Final: this excludes the
        // LandingRoll/Vacating/ClearOfRunway phases by construction (sealed
        // PilotPhase hierarchy — the `is Final` check disjointly excludes
        // every other phase). Trained GA must NOT fire after touchdown
        // (that would model a balked landing — out of fn-11 scope per the
        // epic's boundaries section); the Final-only gate is the structural
        // enforcement.
        MissionStep.FLY_FINAL_TO_SHORT_FINAL -> {
            aircraft.phase is PilotPhase.Final &&
                aircraft.altitudeM <= xyz.easiersaid.twr.pilot.observe.DECISION_ALTITUDE_M
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

/**
 * The pilot's per-circuit intent for the current downwind transmission,
 * derived from the active compound in the mission tree:
 *  - [TaskName.Circuit] → FULL_STOP (this is a full-stop circuit)
 *  - [TaskName.TouchAndGo] → TOUCH_AND_GO
 *  - [TaskName.CircuitAfterGoAround] → FULL_STOP if the inner Circuit
 *    subtask is active; null during the GoAround subtask itself (the
 *    pilot doesn't broadcast circuit intent during a missed approach).
 *  - any other compound → null (pilot is not yet flying a circuit).
 *
 * The pilot's tree is the source of truth for the pilot's per-circuit
 * decision. The transmission carries it. The controller's belief is the
 * controller's view of it. Three layers, one direction. The previous
 * implementation read [derivePilotGoal] which was a controller-facing
 * type — a leak in the firewall direction.
 */
private fun deriveCircuitIntent(mission: PilotMission): CircuitIntent? {
    val ac = mission.root.activeCompound() ?: return null
    return when (ac.name) {
        is TaskName.Circuit -> CircuitIntent.FULL_STOP
        is TaskName.TouchAndGo -> CircuitIntent.TOUCH_AND_GO
        is TaskName.CircuitAfterGoAround -> {
            val inner = ac.activeCompound()
            // During the GoAround subtask itself: don't broadcast intent (silent recovery).
            // After GoAround completes (inner Circuit active): full-stop on the rejoined circuit.
            if (inner?.name is TaskName.Circuit) CircuitIntent.FULL_STOP else null
        }
        // Non-circuit task variants — pilot is not flying a per-circuit decision.
        is TaskName.Depart -> null
        is TaskName.Arrive -> null
        is TaskName.Transit -> null
        is TaskName.GroundDeparture -> null
        is TaskName.GroundArrival -> null
        is TaskName.CircuitTraining -> null
        is TaskName.ArrivalJoin -> null
        is TaskName.GoAround -> null
    }
}

/**
 * G2 (D-AUDIT.8.IV-FOLLOWUP closure): derive "which aerodrome am I calling
 * at first contact?" from mission goal.
 *
 * Approach: read the destination aerodrome from the mission's [HighLevelGoal].
 * For [HighLevelGoal.Transit], the call at CALL_INBOUND is to the destination
 * aerodrome. For other goals (Departure, Arrival, CircuitTraining) the call
 * target is single-aerodrome — fall back to the singleton ATIS lookup that
 * worked pre-G2.
 *
 * G2 (Phase C tightening): when the goal-derived destination is null AND
 * `atisByAerodrome.size > 1`, the singleton fallback would silently drop to
 * null and the pilot's [InitialContact] would carry `atisCode = null` — a
 * wiring-defect class. The multi-entry-non-Transit path now `error()`s
 * loudly with a rich diagnostic. Single-entry maps still resolve via the
 * `singleOrNull` semantics (preserves G0 behaviour: LOWG circuit training
 * publishes one ATIS, the pilot reads letter `'A'`). Future scope filed
 * as **D-G2.8** — typed split into `atisLetterForTransit` /
 * `atisLetterForSingleAerodrome` helpers makes the multi-entry-non-Transit
 * combination unrepresentable at the type level.
 */
private fun atisLetterForCallInbound(
    mission: PilotMission,
    atisByAerodrome: Map<xyz.easiersaid.twr.protocol.AerodromeId, xyz.easiersaid.twr.protocol.Atis>,
): Char? {
    val targetAerodrome: xyz.easiersaid.twr.protocol.AerodromeId? = when (val g = mission.goal) {
        is HighLevelGoal.Transit -> g.destination
        is HighLevelGoal.Departure -> g.destination
        is HighLevelGoal.Arrival, is HighLevelGoal.CircuitTraining -> null
    }
    if (targetAerodrome != null) return atisByAerodrome[targetAerodrome]?.letter
    return when (atisByAerodrome.size) {
        0 -> null
        1 -> atisByAerodrome.values.single().letter
        else -> error(
            "atisLetterForCallInbound: cannot resolve ATIS for ${mission.goal::class.simpleName} " +
                "with goal-derived destination = null and multiple aerodrome ATIS entries " +
                "(${atisByAerodrome.keys}). The pilot's InitialContact would silently carry " +
                "atisCode = null. Either widen [HighLevelGoal] to carry the call-target aerodrome " +
                "for non-Transit goals, or restrict atisByAerodrome to the single relevant entry. " +
                "D-G2.8 records the typed-split future scope."
        )
    }
}

@Suppress("CyclomaticComplexMethod")
private fun stepTransmission(
    aircraft: AircraftState,
    mission: PilotMission,
    step: MissionStep,
    now: SimTime,
    atisByAerodrome: Map<xyz.easiersaid.twr.protocol.AerodromeId, xyz.easiersaid.twr.protocol.Atis> = emptyMap(),
): PilotTransmission? {
    // Fire the per-step "first-tick" transmission exactly once per step
    // entry, regardless of pilot tick timing. Tracked via
    // [PilotMission.lastTransmittedStep] which the call site updates to the
    // current step after a transmission is emitted. Replaces a previous
    // `(now - stepEnteredAt) < window` check that double-fired on adjacent
    // pilot ticks (causing a step-on cascade with the controller's reply).
    val isFirstTick = mission.lastTransmittedStep != Some(step)

    return when (step) {
    MissionStep.REQUEST_STARTUP -> if (isFirstTick) Request(RequestStartup()) else null
    MissionStep.REQUEST_TAXI -> if (isFirstTick) Request(RequestTaxi()) else null
    MissionStep.REPORT_READY -> if (isFirstTick) Report(listOf(ReportEvent.Ready)) else null
    MissionStep.CALL_INBOUND -> if (isFirstTick) InitialContact(
        // Pass 7 (D-AUDIT.5): the role to call is whoever the pilot was
        // most recently told to contact (e.g. GROUND after the post-vacate
        // handoff). Falls back to TOWER for the original arrival case
        // where the pilot's first call after spawning is to Tower.
        //
        // **Note (Pass 7 post-impl Impact-O.1)**: the sim-side responsibility
        // flip does NOT require this step — `applyTwoWayCommsEstablished`
        // fires on any pilot transmission to a Watching controller per
        // ICAO Doc 4444 §10.1.1 (two-way comms established by receiving
        // station's acknowledgement, no specific phrase required). This
        // step exists for phraseology realism — when the pilot's mission
        // tree dictates a dedicated initial-contact transmission. The
        // sim's transition machinery is independent of whether the pilot's
        // HTN tree has a CALL_INBOUND step at every handoff edge; even
        // tasks that omit it (e.g. groundArrivalTask) flip correctly when
        // the pilot's first frequency-side transmission arrives.
        stationCalled = mission.pendingInitialContactRole.getOrElse { xyz.easiersaid.twr.protocol.RoleName.TOWER },
        // Pass 15 (D-AUDIT.8 closure): the pilot reads the current
        // ATIS letter at the moment of first contact and embeds it in
        // the transmission per ICAO Annex 11 §4.3.6.
        //
        // G2 (D-AUDIT.8.IV-FOLLOWUP closure): multi-aerodrome ATIS
        // resolution. The pilot derives "which aerodrome am I calling
        // for the first time?" from mission goal — for a `Transit`
        // mission, the destination aerodrome is the call target at
        // CALL_INBOUND. For circuit / single-aerodrome missions the
        // destination is null and the lookup falls back to the
        // single published ATIS, preserving Pass 15 behaviour.
        atisCode = atisLetterForCallInbound(mission, atisByAerodrome),
    ) else null
    MissionStep.REPORT_DOWNWIND ->
        if (mission.lastReportedLeg != Some(LegName.DOWNWIND)) {
            // CAP 413 para 4.50/4.51: qualify downwind with circuit intent.
            // Drives intent off the pilot's active compound (their actual
            // plan for this circuit) — not via derivePilotGoal, which used
            // to cross the controller-facing PilotGoal type.
            Report(listOf(ReportEvent.Downwind(deriveCircuitIntent(mission))), mission.activeRunway.getOrNull()?.runway)
        } else null
    MissionStep.REPORT_BASE ->
        if (mission.lastReportedLeg != Some(LegName.BASE)) {
            Report(listOf(ReportEvent.Base), mission.activeRunway.getOrNull()?.runway)
        } else null
    MissionStep.REPORT_FINAL ->
        if (mission.lastReportedLeg != Some(LegName.FINAL)) {
            Report(listOf(ReportEvent.Final), mission.activeRunway.getOrNull()?.runway)
        } else null
    MissionStep.AWAIT_LANDING_CLEARANCE -> {
        val elapsed = now.millis - mission.stepEnteredAt.millis
        // Escalation: query controller after 15s with no clearance.
        // Real pilot would say "[callsign], final, request landing clearance" — not repeat the position report.
        if (elapsed in 15_000..16_500) Report(listOf(ReportEvent.Final)) else null
    }
    MissionStep.AWAIT_LINE_UP -> {
        // The previous REPORT_READY transmission was likely wasted (it went
        // to ground while the GND→TWR handoff was in flight; ground doesn't
        // sequence runway operations). Once the pilot has completed
        // [InitialContact] with the new controller (contactedOnFrequency =
        // true) and the per-step first-tick gate hasn't fired yet, transmit
        // a "ready" call to the new controller. DEP-LUAW's PilotReady
        // guard fires the same cycle.
        //
        // Real-world parallel: "Tower, OE-ABC, holding short 16C, ready"
        // (or just "ready" once initial contact has been established).
        if (isFirstTick && mission.contactedOnFrequency) Report(listOf(ReportEvent.Ready)) else null
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
    MissionStep.AWAIT_TAKEOFF_CLEARANCE,
    MissionStep.FLY_DEPARTURE,
    MissionStep.FLY_DOWNWIND,
    MissionStep.AWAIT_SEQUENCING,
    MissionStep.FLY_BASE,
    MissionStep.FLY_FINAL,
    // fn-11.1: trained-GA short-final descent leg has no pilot-initiated
    // transmission of its own — Report(GoingAround) fires from the
    // GOING_AROUND step that follows, per CAP 413 §4.66 (Ed 24 — formerly
    // §4.67 in Ed 23, renumbered per fn-17.1). The
    // REPORT_DOWNWIND + REPORT_BASE steps already covered the position-
    // call obligation for this circuit.
    MissionStep.FLY_FINAL_TO_SHORT_FINAL,
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
 *
 * Phase C of the pilot-firewall plan: every runway-bearing instruction also
 * updates [PilotMission.activeRunway] via [updateActiveRunwayFromInstruction].
 * The pilot's runway comes from radio alone — never from a peek at the
 * controller's commitment ledger.
 *
 * **Per-leaf exhaustive over [AtcInstruction]** (Pass 1, Item 1b). The outer
 * `when (instruction)` enumerates every concrete leaf; per-leaf step-aware
 * decisions live inside each arm or in a focused private helper. Adding a new
 * [AtcInstruction] subtype is a compile error — replaces the previous
 * condition-based catch-all that could not be compiler-exhaustive.
 *
 * Smart-cast through `step in setOf(...)` does propagate on the `instruction`
 * receiver because the `is X` discriminator runs first; `step` is the parameter
 * being matched against the set, not the receiver.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod") // intrinsic to the 98-leaf AtcInstruction sealed hierarchy
fun processInstruction(
    instruction: AtcInstruction,
    mission: PilotMission,
    now: SimTime,
    worldIndex: WorldIndex,
): PilotMission {
    val task = mission.currentTask ?: return mission
    val step = task.step

    val updated: PilotMission = when (instruction) {
        // ── Step-completion via mission-step matching ───────────────────
        is StartupApproved -> if (step == MissionStep.AWAIT_STARTUP_APPROVAL)
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now) else mission

        is TaxiToHoldingPoint -> if (step in TAXI_TO_STEPS)
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now) else mission

        is TaxiToStand -> if (step in TAXI_TO_STEPS)
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now) else mission

        is LineUpAndWait -> if (step == MissionStep.AWAIT_LINE_UP)
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now) else mission

        is ClearedForTakeoff -> if (step == MissionStep.AWAIT_TAKEOFF_CLEARANCE || step == MissionStep.AWAIT_LINE_UP)
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now) else mission

        // BacktrackRunway is the single-runway-airfield equivalent of AfterLandingVacateVia.
        is AfterLandingVacateVia -> if (step == MissionStep.AWAIT_VACATE_INSTRUCTION)
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now) else mission

        is BacktrackRunway -> if (step == MissionStep.AWAIT_VACATE_INSTRUCTION)
            mission.copy(root = mission.root.markComplete(step), stepEnteredAt = now) else mission

        is JoinCircuit -> if (step == MissionStep.AWAIT_JOINING_INSTRUCTIONS)
            mission.copy(
                root = mission.root.markComplete(step),
                joinLeg = Some(instruction.joinType.toCircuitLeg()),
                stepEnteredAt = now,
            ) else mission

        // ── Constraint manipulation (step-agnostic) ─────────────────────
        is ExtendDownwind ->
            mission.copy(activeConstraints = mission.activeConstraints + ActiveConstraint.ExtendingDownwind)

        is TurnBase ->
            mission.copy(activeConstraints = mission.activeConstraints - ActiveConstraint.ExtendingDownwind)

        is ClearedToLand -> handleLandingClearance(mission, now)
        is ClearedTouchAndGo -> handleLandingClearance(mission, now)
        is GoAround -> handleGoAround(mission, now)
        is BreakOff -> handleGoAround(mission, now)

        is ContactFrequency ->
            // Crossing onto a new frequency: the pilot has not yet been heard
            // by the new controller. Reset both the contact-established flag
            // (so InitialContact transmits on the next pilot tick) and the
            // first-tick gate (so any subsequent step-driven transmission on
            // the new frequency — e.g., a fresh Report(Ready) at AWAIT_LINE_UP
            // after a GND→TWR handoff — is not suppressed by a stale
            // lastTransmittedStep value left over from the old frequency).
            //
            // Pass 7 (D-AUDIT.5): also remember the role the pilot was told
            // to contact, so the next CALL_INBOUND emission targets that
            // role rather than the hardcoded TOWER. Real-world parallel:
            // the controller said "OE-ABC, contact ground 121.9" — the
            // pilot calls "Ground, OE-ABC" next, not "Tower."
            //
            // Pass 7 post-impl Impact-O.2: latest-wins semantic on
            // `pendingInitialContactRole`. Two ContactFrequency in quick
            // succession overwrite the field silently. Operationally
            // defensible (real ATC may amend an in-flight handoff
            // explicitly: "OE-ABC, disregard ground, contact tower") and
            // matches ICAO clarification semantics. The lost first-role
            // is not currently audited; if a future pass surfaces handoff-
            // overwrite anomalies (peer to D-PF.2's runway-assignment
            // anomalies), this is the trigger site.
            mission.copy(
                contactedOnFrequency = false,
                lastTransmittedStep = None,
                pendingInitialContactRole = Some(instruction.role),
            )

        is RadarServiceTerminated ->
            // Pass 7 (D-PF.7): boundary release. Pilot acknowledges (the
            // readback is generated separately via InstructionReadback's
            // squawk-readback rule) and clears their contact state. No
            // pendingInitialContactRole — there's no successor controller.
            mission.copy(
                contactedOnFrequency = false,
                lastTransmittedStep = None,
                pendingInitialContactRole = None,
            )

        // ── Route overrides: vectors / holds suspend FPL-based routing ──
        is FlyHeading ->
            mission.copy(routeOverride = Some(RouteOverride.Vectoring(instruction.heading)))
        is TurnHeading ->
            mission.copy(routeOverride = Some(RouteOverride.Vectoring(instruction.heading)))
        is StopTurn -> instruction.rollOutHeading
            ?.let { heading -> mission.copy(routeOverride = Some(RouteOverride.Vectoring(heading))) }
            ?: mission
        is InterceptLocaliser ->
            mission.copy(routeOverride = None) // localiser capture ends vectoring
        is HoldAt ->
            mission.copy(routeOverride = Some(RouteOverride.Holding(instruction.hold)))
        is ResumeOwnNavigation ->
            mission.copy(routeOverride = None)

        is StopClimbAt ->
            mission.copy(altitudeRestrictionM = Some(levelToMeters(instruction.level)))

        is Disregard -> mission.copy(
            activeConstraints = mission.activeConstraints - ActiveConstraint.ExtendingDownwind,
            routeOverride = None,
        )

        // ── No-op leaves ─ pilot acknowledges, mission state unchanged.
        // An instruction arriving when the mission is at an irrelevant step is normal
        // (e.g., TaxiTo at FLY_DEPARTURE). The contract is "apply if relevant, else
        // ignore." Per-leaf no-op arms make a new AtcInstruction subtype compile-fail.
        is AfterPassingLevelClimbTo -> mission
        is AfterPassingLevelDescendTo -> mission
        is AirTaxiTo -> mission
        is AvoidArea -> mission
        is AvoidLevel -> mission
        is CancelClearance -> mission
        is ClearedApproach -> mission
        is ClearedLowApproach -> mission
        is ClearedTo -> mission
        is ClearedToEnterControlZone -> mission
        is ClearedVisualApproach -> mission
        is ClimbTo -> mission
        is CommenceApproachAt -> mission
        is ConditionalClearance -> mission
        is ConfirmSquawk -> mission
        is ContinueApproach -> mission
        is ContinuePresentHeading -> mission
        is CrossRunway -> mission
        is DescendTo -> mission
        is DescendWhenReady -> mission
        is DivertTo -> mission
        is ExpediteClimb -> mission
        is ExpediteDescend -> mission
        is ExpediteTaxi -> mission
        is FollowTraffic -> mission
        is GiveWayToTraffic -> mission
        is HoldPosition -> mission
        is HoldPositionCancelTakeoff -> mission
        is HoldShortOf -> mission
        is IncreaseSpeedTo -> mission
        is JoinAirway -> mission
        is LeaveHoldProceedDirect -> mission
        is MaintainAltitudeUntilEstablished -> mission
        is MaintainAtOrAbove -> mission
        is MaintainAtOrBelow -> mission
        is MaintainLevel -> mission
        is MaintainSpeed -> mission
        is MaintainVisualSeparation -> mission
        is MakeAnotherCircuit -> mission
        is MakeLongApproach -> mission
        is MakeShortApproach -> mission
        is MinimumCleanSpeed -> mission
        is MonitorFrequency -> mission
        is NumberInSequence -> mission
        is Orbit -> mission
        is ProceedDirect -> mission
        is PushbackApproved -> mission
        is PushbackFace -> mission
        is ReduceSpeedTo -> mission
        is ReduceTaxiSpeed -> mission
        is RejoinSidAt -> mission
        is RemainOutsideControlledAirspace -> mission
        is ReportIntentions -> mission
        is ReportTrafficInSight -> mission
        is ReportWhen -> mission
        is ResumeNormalSpeed -> mission
        is RouteAsFiled -> mission
        is RunwayInUseAdvisory -> mission
        is SetPressure -> mission
        is SetSquawk -> mission
        is SpecialVfrClearance -> mission
        is SquawkIdent -> mission
        is SquawkNormal -> mission
        is SquawkStandby -> mission
        is StopDescentAt -> mission
        is StopImmediately -> mission
        is StopSquawk -> mission
        is TakeoffImmediatelyOrHoldShort -> mission
        is TakeoffImmediatelyOrVacateRunway -> mission
        is TaxiIntoHoldingBay -> mission
        is TaxiViaRunway -> mission
        is TaxiWithCaution -> mission
        is TransitionLevelIssuance -> mission
        is TurnByDegrees -> mission
        is VacateRunway -> mission
        is WhenAbleProceedDirect -> mission
    }
    return updated
        .let { result -> applyFplAmendment(result, instruction) }
        .let { result -> updateActiveRunwayFromInstruction(result, instruction, worldIndex) }
}

/**
 * Steps where a [TaxiTo] instruction completes the current step. The set spans
 * the request-taxi (departure), vacate-after-landing, and taxi-to-stand stages.
 * AWAIT_JOINING_INSTRUCTIONS is included because ground controller may use
 * [TaxiTo] in lieu of [JoinCircuit] in some scenarios.
 */
private val TAXI_TO_STEPS = setOf(
    MissionStep.REQUEST_TAXI,
    MissionStep.AWAIT_VACATE_INSTRUCTION,
    MissionStep.TAXI_TO_STAND,
    MissionStep.AWAIT_JOINING_INSTRUCTIONS,
)

/**
 * [ClearedToLand] / [ClearedTouchAndGo]: mark sequencing and flying steps complete,
 * but NOT position reports — the pilot should still report turning base and final
 * even with an early clearance, so the controller maintains situational awareness.
 *
 * fn-11.1 (G3a-trained): [MissionStep.FLY_FINAL_TO_SHORT_FINAL] is **deliberately
 * absent** from `stepsToMark`. The trained-GA short-final descent leg is altitude-
 * gated, NOT clearance-gated — the pilot continues their planned go-around even
 * after receiving `ClearedToLand`. If aliased here, clearance receipt would
 * skip the trained step and advance straight to `GOING_AROUND`, defeating the
 * trained-GA fork. Per the task's new-MissionStep audit pin (Acceptance R3
 * audit, sub-bullet `handleLandingClearance`).
 *
 * fn-11.1 (codex re-review round 4): step-marking is **scoped to the active
 * top-level compound** to prevent future-circuit corruption. `markComplete`
 * normally walks past completed compounds and marks the FIRST incomplete
 * instance of a step anywhere in the tree. For a trained-GA mission's active
 * `Circuit` compound (which has no `FLY_FINAL` — it has
 * `FLY_FINAL_TO_SHORT_FINAL` instead), `markComplete(FLY_FINAL)` would
 * silently mark the recovery `FullStop` outcome's `FLY_FINAL` in the NEXT
 * circuit, corrupting that future state. Scoping to the active compound
 * makes ClearedToLand a no-op for steps the active circuit doesn't have,
 * which is the correct semantic for trained-GA: the controller's clearance
 * is recorded (`hasClearance=true`) but it can't fast-forward steps the
 * pilot's plan doesn't include.
 */
private fun handleLandingClearance(mission: PilotMission, now: SimTime): PilotMission {
    val stepsToMark = listOf(
        MissionStep.AWAIT_SEQUENCING, MissionStep.FLY_BASE,
        MissionStep.FLY_FINAL, MissionStep.AWAIT_LANDING_CLEARANCE,
    )
    val newRoot = markCompleteInActiveCompound(mission.root, stepsToMark)
    return mission.copy(hasClearance = true, root = newRoot, stepEnteredAt = now)
}

/**
 * Like [CompoundTask.markComplete] but scoped to the active sub-tree of
 * [root]. Marks every step in [steps] that appears within the leftmost-
 * incomplete top-level child compound; steps the active compound doesn't
 * carry are no-ops (NOT marked further along the tree).
 *
 * **Scoping semantics**:
 *  - If the leftmost incomplete top-level child is a *compound* (e.g. an
 *    active circuit in `CircuitTraining`'s outcome list, or
 *    `groundDepartureTask` in `Transit`), scope to that compound.
 *  - If the leftmost incomplete top-level child is a *primitive* (e.g.
 *    Transit's flat `FLY_DEPARTURE`/`FLY_DOWNWIND`/.../`LAND` primitives
 *    that are direct children of the root), scope to the **root** itself
 *    — the root IS the smallest enclosing compound for those primitives.
 *
 * fn-11.1 (codex re-review round 4): replaces a per-step
 * `root.markComplete` fold whose tree-walk could step past the active
 * compound and corrupt future circuits when the active compound's step
 * vocabulary differs from the marked steps. Critical for trained-GA: the
 * active `Circuit` compound has no `FLY_FINAL` (it carries
 * `FLY_FINAL_TO_SHORT_FINAL`), so a naive `root.markComplete(FLY_FINAL)`
 * would walk past and mark the recovery `FullStop` circuit's `FLY_FINAL`,
 * corrupting future state. The Transit-flat-primitives case continues to
 * work because the scope falls back to root when the active child is a
 * primitive.
 */
private fun markCompleteInActiveCompound(
    root: CompoundTask,
    steps: List<MissionStep>,
): CompoundTask {
    val activeChildIndex = root.children.indexOfFirst { !it.isComplete }
    if (activeChildIndex < 0) return root
    return when (val activeChild = root.children[activeChildIndex]) {
        is CompoundTask -> {
            val updatedActive = steps.fold(activeChild) { task, step -> task.markComplete(step) }
            if (updatedActive == activeChild) return root
            val newChildren = root.children.toMutableList()
            newChildren[activeChildIndex] = updatedActive
            root.copy(children = newChildren.toList())
        }
        // Active child is a primitive — root IS the smallest enclosing
        // compound. Fall back to the original semantics: mark the first
        // incomplete instance of each step across root's children. The
        // step-walk only crosses outcome boundaries when the active outcome
        // truly lacks that step shape (e.g. Transit's flat primitives).
        is PrimitiveTask -> steps.fold(root) { task, step -> task.markComplete(step) }
    }
}

/**
 * [GoAround] / [BreakOff]: replace the active (incomplete) circuit compound
 * with GO_AROUND + fresh CIRCUIT. Matches Circuit, CircuitAfterGoAround, and
 * TouchAndGo — all circuit pattern variants.
 *
 * fn-12.2 (G3a-obstruction): captures the pre-rewrite `currentTask?.step`
 * BEFORE the tree rewrite and stamps it onto the new mission via
 * [PilotMission.pendingAtcGoAroundFrom] when the original step is in the
 * on-final eligible set `{FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE,
 * LAND}`. Otherwise leaves the flag [None] — non-on-final paths (e.g. GA
 * arriving during downwind from a separation conflict) use the existing
 * Visual-mode reactive special-case at `Pilot.kt:planVisualRoute`. The
 * recognition arm in `pilotDecide` reads this flag (post-cognitive mission
 * state) to decide whether to fire `applyAtcInitiatedGoAround`.
 *
 * **Eligible-step set rationale**: by the time a post-`ClearedToLand`
 * obstruction GA arrives, [handleLandingClearance] has already advanced
 * `AWAIT_LANDING_CLEARANCE → LAND`, so `currentTask.step` is `LAND`, not
 * `AWAIT_LANDING_CLEARANCE`. Including `LAND` keeps the post-clearance
 * obstruction-GA case covered. `FLY_FINAL` and `REPORT_FINAL` cover the
 * on-final pre-clearance window.
 *
 * **Flag-set sequencing**: `resetForGoAround(now)` does NOT touch
 * `pendingAtcGoAroundFrom` (per its KDoc). The flag is stamped onto the
 * `.copy(root = ...)` result AFTER the reset, so the new value survives.
 */
private fun handleGoAround(mission: PilotMission, now: SimTime): PilotMission {
    // Capture the pre-rewrite step BEFORE replacing the tree — once
    // replaceChild runs, the active leaf is GOING_AROUND and the original
    // on-final step is unrecoverable.
    val originalStep = mission.currentTask?.step
    val pendingFlag: Option<MissionStep> = if (originalStep != null && originalStep in ATC_GO_AROUND_ELIGIBLE_STEPS) {
        Some(originalStep)
    } else {
        None
    }

    val gaTask = if (mission.navigationMode.getOrNull() is NavigationMode.Instrument) ifrGoAroundTask()
        else goAroundTask()
    val newRoot = mission.root.replaceChild(
        predicate = { it is CompoundTask && !it.isComplete && it.name.isCircuitLike() },
        replacement = CompoundTask(TaskName.CircuitAfterGoAround, listOf(
            gaTask,
            circuitTask(),
        )),
    )
    return mission.resetForGoAround(now).copy(
        root = newRoot,
        pendingAtcGoAroundFrom = pendingFlag,
    )
}

/**
 * fn-12.2 (G3a-obstruction): on-final step set where ATC-issued reactive
 * GA recognition is meaningful. `LAND` is included because
 * [handleLandingClearance] advances `AWAIT_LANDING_CLEARANCE → LAND` after
 * `ClearedToLand` — by the time a post-clearance obstruction GA arrives,
 * `currentTask.step` is `LAND`. Shared by [handleGoAround] (set-site) and
 * `Pilot.kt`'s `pilotDecide` recognition arm (read-and-clear-site) so the
 * two stay in sync.
 */
internal val ATC_GO_AROUND_ELIGIBLE_STEPS: Set<MissionStep> = setOf(
    MissionStep.FLY_FINAL,
    MissionStep.REPORT_FINAL,
    MissionStep.AWAIT_LANDING_CLEARANCE,
    MissionStep.LAND,
)

/**
 * Phase C of the pilot-firewall plan: extract the runway from a radio-derived
 * instruction and write it onto [PilotMission.activeRunway]. The pilot's
 * runway comes from radio alone — there is no controller-state read.
 *
 * The legitimate radio sources are enumerated below. Instructions that
 * carry no runway leave `activeRunway` unchanged. Last-write-wins is the
 * documented overwrite policy; a sealed [RunwayAssignmentSource] discriminator
 * with explicit precedence rules is the long-term shape, recorded as
 * deferment **D-PF.2**.
 *
 * Multi-runway holding-point ambiguity (D-PF.6): when a [TaxiTo]'s
 * destination is a holding point that serves more than one runway, we keep
 * the existing `activeRunway` if it is still in the candidate set, otherwise
 * pick deterministically. The clean fix is for [TaxiTo] to carry an
 * explicit `runway: RunwayId` field; that lands when D-PF.6 lands.
 */
/**
 * One-line dispatcher. Per Pass 2 (D-PF.4 closure), the per-leaf runway
 * extraction lives in [runwayFromInstruction] — total over the
 * [AtcInstruction] sealed hierarchy.
 *
 * Pass 5 (D-PF.2 closure): [runwayFromInstruction] returns
 * `Option<RunwayAssignment>` (runway + source). Updates apply
 * [applyPrecedence] so anomalous orderings are flagged. The pilot's job
 * is to obey the latest controller statement, so anomalies do not reject
 * the new assignment — `anomaly.new` is applied. The anomaly itself is
 * retained on [PilotMission.recentAnomalies] (bounded ring) so Pass 7's
 * coordination ledger can consume it without re-deriving the history.
 */
private fun updateActiveRunwayFromInstruction(
    mission: PilotMission,
    instruction: AtcInstruction,
    worldIndex: WorldIndex,
): PilotMission =
    runwayFromInstruction(instruction, mission.activeRunway, worldIndex)
        .fold({ mission }, { newAssignment ->
            applyPrecedence(mission.activeRunway, newAssignment)
                .fold(
                    { anomaly ->
                        val ring = (mission.recentAnomalies + anomaly)
                            .takeLast(PilotMission.MAX_ANOMALY_HISTORY)
                        mission.copy(activeRunway = Some(anomaly.new), recentAnomalies = ring)
                    },
                    { resolved -> mission.copy(activeRunway = Some(resolved)) },
                )
        })

/**
 * Per-leaf runway extraction over [AtcInstruction]. Total: every leaf maps
 * to exactly one of `None | Some(runway)`. The 7 runway-bearing leaves
 * (TaxiTo, LineUpAndWait, ClearedForTakeoff, ClearedToLand, ClearedTouchAndGo,
 * BacktrackRunway, AfterLandingVacateVia) carry the controller's runway
 * statement; every other leaf returns [None] (the radio carries no runway
 * information, so [PilotMission.activeRunway] is unchanged).
 *
 * `priorRunway` is consulted only by the [TaxiTo] arm's multi-runway
 * disambiguator (D-PF.6 will replace it with an explicit runway field).
 *
 * `ExhaustivenessTest` (pilot/jvmTest) is the proof obligation: every
 * concrete leaf appears as `is X -> ...`, no category-arm absorption.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "UnusedParameter")
// Intrinsic to the 98-leaf AtcInstruction sealed hierarchy; priorRunway/worldIndex
// remain for D-PF.6 signature stability until the multi-runway disambiguator is gone.
private fun runwayFromInstruction(
    instruction: AtcInstruction,
    priorRunway: Option<RunwayAssignment<RunwayAssignmentSource>>,
    worldIndex: WorldIndex,
): Option<RunwayAssignment<RunwayAssignmentSource.Radio>> = when (instruction) {
    // Pass 6 (D-PF.6 closure): runway is now an explicit field on
    // TaxiToHoldingPoint (no inference); TaxiToStand carries no runway.
    is TaxiToHoldingPoint -> Some(RunwayAssignment(instruction.runway, RunwayAssignmentSource.Radio.TaxiClearance))
    is TaxiToStand -> None
    is LineUpAndWait -> Some(RunwayAssignment(instruction.runway, RunwayAssignmentSource.Radio.LineUp))
    is ClearedForTakeoff -> Some(RunwayAssignment(instruction.runway, RunwayAssignmentSource.Radio.Takeoff))
    is ClearedToLand -> Some(RunwayAssignment(instruction.runway, RunwayAssignmentSource.Radio.Land))
    is ClearedTouchAndGo -> Some(RunwayAssignment(instruction.runway, RunwayAssignmentSource.Radio.TouchAndGo))
    is BacktrackRunway -> Some(RunwayAssignment(instruction.runway, RunwayAssignmentSource.Radio.Backtrack))
    // AfterLandingVacateVia carries the *exit* point but not the runway —
    // the runway is implicit (the one the aircraft just landed on, already
    // set on `mission.activeRunway` from the prior ClearedToLand / T&G).
    // Don't overwrite — the existing value is correct.
    is AfterLandingVacateVia -> None
    // Per-leaf no-op arms for every other AtcInstruction leaf. ExhaustivenessTest
    // (pilot/jvmTest) prevents regression to category-arm absorption.
    is AfterPassingLevelClimbTo -> None
    is AfterPassingLevelDescendTo -> None
    is AirTaxiTo -> None
    is AvoidArea -> None
    is AvoidLevel -> None
    is BreakOff -> None
    is CancelClearance -> None
    is ClearedApproach -> None
    is ClearedLowApproach -> None
    is ClearedTo -> None
    is ClearedToEnterControlZone -> None
    is ClearedVisualApproach -> None
    is ClimbTo -> None
    is CommenceApproachAt -> None
    is ConditionalClearance -> None
    is ConfirmSquawk -> None
    is ContactFrequency -> None
    is ContinueApproach -> None
    is ContinuePresentHeading -> None
    is CrossRunway -> None
    is DescendTo -> None
    is DescendWhenReady -> None
    is Disregard -> None
    is DivertTo -> None
    is ExpediteClimb -> None
    is ExpediteDescend -> None
    is ExpediteTaxi -> None
    is ExtendDownwind -> None
    is FlyHeading -> None
    is FollowTraffic -> None
    is GiveWayToTraffic -> None
    is GoAround -> None
    is HoldAt -> None
    is HoldPosition -> None
    is HoldPositionCancelTakeoff -> None
    is HoldShortOf -> None
    is IncreaseSpeedTo -> None
    is InterceptLocaliser -> None
    is JoinAirway -> None
    // G2 closure: a JoinCircuit's runway field (when present — it's nullable
    // for backwards compatibility with rule sites that don't set it) is the
    // canonical radio source for the destination-aerodrome runway during
    // cross-aerodrome arrival. Without this propagation, mission.activeRunway
    // remains stuck at the LOWG departure runway across the cruise and the
    // pilot's pattern routing fixes onto the wrong aerodrome's circuit.
    is JoinCircuit -> instruction.runway?.let {
        Some(RunwayAssignment(it, RunwayAssignmentSource.Radio.JoinCircuit))
    } ?: None
    is LeaveHoldProceedDirect -> None
    is MaintainAltitudeUntilEstablished -> None
    is MaintainAtOrAbove -> None
    is MaintainAtOrBelow -> None
    is MaintainLevel -> None
    is MaintainSpeed -> None
    is MaintainVisualSeparation -> None
    is MakeAnotherCircuit -> None
    is MakeLongApproach -> None
    is MakeShortApproach -> None
    is MinimumCleanSpeed -> None
    is MonitorFrequency -> None
    is NumberInSequence -> None
    is Orbit -> None
    is ProceedDirect -> None
    is PushbackApproved -> None
    is PushbackFace -> None
    is ReduceSpeedTo -> None
    is ReduceTaxiSpeed -> None
    is RejoinSidAt -> None
    is RemainOutsideControlledAirspace -> None
    is ReportIntentions -> None
    is ReportTrafficInSight -> None
    is ReportWhen -> None
    is ResumeNormalSpeed -> None
    is ResumeOwnNavigation -> None
    is RouteAsFiled -> None
    is RunwayInUseAdvisory -> None
    is SetPressure -> None
    is SetSquawk -> None
    is SpecialVfrClearance -> None
    is SquawkIdent -> None
    is SquawkNormal -> None
    is SquawkStandby -> None
    is StartupApproved -> None
    is StopClimbAt -> None
    is StopDescentAt -> None
    is StopImmediately -> None
    is StopSquawk -> None
    // Pass 7 (D-PF.7): boundary release carries no runway.
    is RadarServiceTerminated -> None
    is StopTurn -> None
    is TakeoffImmediatelyOrHoldShort -> None
    is TakeoffImmediatelyOrVacateRunway -> None
    is TaxiIntoHoldingBay -> None
    // TaxiViaRunway carries an explicit runway: per Pass 6 D-PF.6 / Impact M.1,
    // the controller's verbal runway statement propagates to activeRunway with
    // source TaxiClearance — same shape as TaxiToHoldingPoint.
    is TaxiViaRunway -> Some(RunwayAssignment(instruction.runway, RunwayAssignmentSource.Radio.TaxiClearance))
    is TaxiWithCaution -> None
    is TransitionLevelIssuance -> None
    is TurnBase -> None
    is TurnByDegrees -> None
    is TurnHeading -> None
    is VacateRunway -> None
    is WhenAbleProceedDirect -> None
}

// Pass 6 (D-PF.6 closure): the multi-runway holding-point disambiguator
// `taxiToRunway(instruction, priorRunway, worldIndex)` deletes. The runway
// is now an explicit field on [TaxiToHoldingPoint] — pilot reads it
// directly, no inference, no precedence. Real ATC says
// "taxi to A4 runway 16L"; the disambiguator was the model's apology for
// omitting half the instruction.

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
    val mode = mission.navigationMode.getOrNull() as? NavigationMode.Instrument ?: return mission
    val amended = amendFpl(mode.fpl, instruction).getOrNull() ?: return mission
    if (amended == mode.fpl) return mission // no change
    return mission.copy(navigationMode = Some(NavigationMode.Instrument(amended)))
}

/** Update lastReportedLeg after a position report transmission. */
/** Update mission after any pilot transmission. */
fun updateAfterTransmission(mission: PilotMission, tx: PilotTransmission): PilotMission = when (tx) {
    is Report -> tx.events.fold(mission) { m, evt -> updateAfterReport(m, evt) }
    is InitialContact -> mission.copy(
        // G2 Phase H post-impl impact-M1 (extended): the pilot's
        // `contactedOnFrequency` flip does NOT happen here on
        // transmit. Per ICAO Doc 4444 §10.1.1, two-way communication
        // is established when the receiving station ACKNOWLEDGES
        // receipt — not when the pilot speaks. The flip happens on
        // the receive-side path (`Step.kt:handleTransmissionEnd`)
        // and is gated on the receiving controller actually being
        // in `Watching` or `knownStrips` state for this aircraft.
        // Pre-fix, the pilot side flipped unconditionally on
        // transmit, which advanced the mission past CALL_INBOUND
        // even when the InitialContact landed on the wrong
        // controller (e.g., the cross-aerodrome race at the
        // destination's REP where the autonomous contact reached
        // the still-Owning departure tower instead of the
        // destination tower). With this gate, mission progression
        // becomes monotonic: CALL_INBOUND advances only when the
        // contact actually landed on a controller waiting for it.
        //
        // Pass 7 (D-AUDIT.5): clear the pending-role so a future
        // CALL_INBOUND step (e.g. a re-handoff later) reads None and
        // either falls back to TOWER or — better — reads a freshly-set
        // pendingInitialContactRole from a subsequent ContactFrequency.
        pendingInitialContactRole = None,
    )
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
    is ReportEvent.Downwind -> mission.copy(lastReportedLeg = Some(LegName.DOWNWIND))
    is ReportEvent.Base -> mission.copy(lastReportedLeg = Some(LegName.BASE))
    is ReportEvent.Final -> mission.copy(lastReportedLeg = Some(LegName.FINAL))
    is ReportEvent.RunwayVacated -> mission.copy(lastReportedLeg = None, reportedVacated = true)
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
