package xyz.easiersaid.twr.controller

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import xyz.easiersaid.twr.controller.assess.Feasibility
import xyz.easiersaid.twr.controller.observe.AdvancementPolicy
import xyz.easiersaid.twr.controller.observe.CoordinationState
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.controller.assess.assessSeparation
import xyz.easiersaid.twr.controller.assess.emitReactiveOutputs
import xyz.easiersaid.twr.controller.assess.reactiveInterventions
import xyz.easiersaid.twr.controller.assess.checkFeasibility
import xyz.easiersaid.twr.controller.assess.selectRunwayIntoWind
import xyz.easiersaid.twr.controller.bdi.applySupersessionCleanup
import xyz.easiersaid.twr.controller.assess.updateArrivalSequence
import xyz.easiersaid.twr.controller.assess.updateRunwayDuty
import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.controller.bdi.executeProcedure
import xyz.easiersaid.twr.controller.bdi.reconcileCommitments
import xyz.easiersaid.twr.controller.bdi.traceRuleFailures
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.bdi.GroundArrivalStage
import xyz.easiersaid.twr.controller.bdi.GroundDepartureStage
import xyz.easiersaid.twr.controller.bdi.OperatorContext
import xyz.easiersaid.twr.controller.bdi.OperatorResult
import xyz.easiersaid.twr.controller.bdi.ProcedureSpec
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.bdi.TowerDepartureStage
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.classifyReadback
import xyz.easiersaid.twr.controller.observe.coordinationEscalationOutputs
import xyz.easiersaid.twr.controller.observe.escalateOverdueCoordinations
import xyz.easiersaid.twr.controller.observe.markCoordinationEscalationsEmitted
import xyz.easiersaid.twr.controller.observe.withRecentRadio
import xyz.easiersaid.twr.controller.observe.withCircuitIntentEvents
import xyz.easiersaid.twr.controller.observe.deriveEventsFromMessages
import xyz.easiersaid.twr.controller.observe.recordCoordinations
import xyz.easiersaid.twr.controller.observe.updateBeliefs
import xyz.easiersaid.twr.controller.observe.ControllerEvent
import xyz.easiersaid.twr.controller.observe.ReadbackVerdict
import xyz.easiersaid.twr.controller.observe.SeparationConcern
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.NumberInSequence
import xyz.easiersaid.twr.protocol.ReadBackCorrect
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.ReadbackCorrection
import xyz.easiersaid.twr.protocol.kind
import xyz.easiersaid.twr.protocol.RegulationDatabase
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TrafficInformation
import xyz.easiersaid.twr.protocol.Urgency
import xyz.easiersaid.twr.controller.procedure.approachArrivalProcedure
import xyz.easiersaid.twr.controller.procedure.classifyArrivalPosition
import xyz.easiersaid.twr.controller.procedure.classifyDeparturePosition
import xyz.easiersaid.twr.controller.procedure.classifyGroundPosition
import xyz.easiersaid.twr.controller.procedure.groundTaxiProcedure
import xyz.easiersaid.twr.controller.procedure.reconcileArrivalStage
import xyz.easiersaid.twr.controller.procedure.reconcileDepartureStage
import xyz.easiersaid.twr.controller.procedure.reconcileGroundArrivalStage
import xyz.easiersaid.twr.controller.procedure.reconcileGroundDepartureStage
import xyz.easiersaid.twr.controller.procedure.towerArrivalProcedure
import xyz.easiersaid.twr.controller.procedure.towerDepartureProcedure
import xyz.easiersaid.twr.core.world.AviationWorld

private val PROCEDURES: Map<CommitmentKind, ProcedureSpec> by lazy {
    listOf(
        towerDepartureProcedure(), towerArrivalProcedure(), groundTaxiProcedure(),
        approachArrivalProcedure(),
    ).associateBy { it.kind }
}

/**
 * Controller decision function. Pure: (View, State) -> (Output, State).
 */
fun controllerDecide(view: ControllerView, previousBeliefs: BeliefState, world: AviationWorld): ControllerDecisionResult {
    val events = deriveEventsFromMessages(view.receivedMessages)

    val contactedAircraft = events.contactedAircraft()

    val beliefs = updateBeliefs(previousBeliefs, view)
        .escalateOverdueCoordinations(view.time)
        .withContactMarked(contactedAircraft)
        .withLocEstablished(events)
        .withActiveRunway(view)
        .withRecentRadio(events, view.time)
        .withCircuitIntentEvents(events)
        .let { b ->
            val commitments = reconcileCommitments(
                existing = b.commitments, role = view.role, aircraft = b.trackedAircraft,
                responsibilities = view.responsibilities, activeRunway = b.activeRunway,
                time = view.time, worldIndex = view.worldIndex,
                flightStripIntents = view.flightStripIntents,
                recentRadio = b.recentRadio, circuitIntent = b.circuitIntent,
                contactedThisCycle = contactedAircraft,
            )
            b.copy(commitments = commitments)
        }
        .let { b ->
            // Observation-driven stage reconciliation: advance commitment stages
            // to match what the controller actually observes. Runs after
            // reconcileCommitments (which creates/prunes) and before procedure
            // execution (which evaluates rules against the reconciled stage).
            val reconciled = reconcileObservedStages(b, view.worldIndex)
            b.copy(commitments = reconciled)
        }
        .let { b ->
            if (view.role == RoleName.TOWER || view.role == RoleName.APPROACH) {
                val sequence = updateArrivalSequence(b.arrivalSequence, b.activeRunway, b, view.worldIndex)
                b.copy(arrivalSequence = sequence)
            } else b
        }
        .let { b ->
            // Phase 6b Phase A: early separation assessment → beliefs (with hysteresis).
            val assessments = assessSeparation(b, view.worldIndex)
            val updatedConcerns = updateRecentConcerns(b.recentConcerns, assessments, view.time)
            b.copy(separationAssessments = assessments, recentConcerns = updatedConcerns)
        }
        .let { b ->
            if (view.role == RoleName.TOWER) {
                val duty = updateRunwayDuty(
                    b.runwayDuty, b.activeRunway, b, b.commitments,
                    events, view.time, view.worldIndex, b.arrivalSequence,
                )
                b.copy(runwayDuty = duty)
            } else b
        }

    val ctx = OperatorContext(view, beliefs, events, world)
    val (runs, skipped) = executeAllProcedures(view.responsibilities, beliefs, ctx)
    val (rawOutputs, committedAircraft) = arbitrate(runs, beliefs, view)
    val outputs = rawOutputs.map { enrichInstruction(it, view.weather) }
    val companions = deriveCompanionOutputs(rawOutputs, runs)

    // Phase 6b Phase B: reactive safety net — catch separation concerns not addressed by procedures.
    val reactiveIntv = reactiveInterventions(beliefs, outputs)
    val reactiveOutputs = emitReactiveOutputs(reactiveIntv, beliefs)
    val reactiveInstructs = reactiveOutputs.filterIsInstance<ControllerOutput.Instruct>()

    val stageAdvancedBeliefs = advanceCommittedStages(beliefs, runs, committedAircraft)

    // Supersession cleanup: procedural outputs first, then reactive outputs.
    val proceduralInstructions = outputs.map { it.target to it.instruction }
    val reactiveInstructions = reactiveInstructs.map { it.target to it.instruction }
    val afterProceduralSupersession = applySupersessionCleanup(stageAdvancedBeliefs, proceduralInstructions)
    val afterReactiveSupersession = applySupersessionCleanup(afterProceduralSupersession, reactiveInstructions)

    val (responses, afterValidation) = validatedReadbackResponses(view, afterReactiveSupersession)

    // Pass 9 (D-AUDIT.2): emit escalation outputs (Confirm / re-issue) for
    // coordinations that just-transitioned this cycle. The escalation
    // states were advanced earlier in the pipeline by
    // `escalateOverdueCoordinations`; here we read the just-transitioned
    // entries (emittedAt == null), emit the operational output, then mark
    // emittedAt so the next cycle does not re-fire until the next state
    // advance.
    //
    // Escalation Instructs (re-emissions) are NOT passed to
    // `recordCoordinations` — the original coordination already exists in
    // the ledger and is now in Reissued state. Recording the re-emission
    // would create a duplicate entry for the same instruction. The
    // re-issue goes out on the wire; the *original* coordination ledger
    // entry remains the lifecycle anchor.
    val escalationOutputs = coordinationEscalationOutputs(afterValidation, view.time)
    val afterEscalationMark = afterValidation.markCoordinationEscalationsEmitted(view.time)

    val allInstructs = outputs + reactiveInstructs
    val finalBeliefs = afterEscalationMark
        .recordCoordinations(allInstructs, view.time)

    return ControllerDecisionResult(
        outputs = outputs + reactiveOutputs + companions + responses + escalationOutputs,
        updatedBeliefs = finalBeliefs,
        trace = OverallDecisionTrace(
            controllerId = view.controllerId, time = view.time,
            actionsConsidered = runs.size, actionsCommitted = outputs.size,
            skippedActions = skipped,
        ),
    )
}

// ── Pipeline stages ──────────────────────────────────────────────────

private fun List<ControllerEvent>.contactedAircraft(): Set<AircraftId> =
    mapNotNull { event ->
        when (event) {
            is ControllerEvent.ReadyForDepartureReceived -> event.aircraft
            is ControllerEvent.InitialContactReceived -> event.aircraft
            is ControllerEvent.ReadbackReceived -> event.aircraft
            is ControllerEvent.PositionReported -> event.aircraft
            else -> null
        }
    }.toSet()

private fun BeliefState.withContactMarked(contacted: Set<AircraftId>): BeliefState =
    copy(commitments = commitments.mapValues { (acId, c) ->
        if (acId in contacted && !c.contacted) c.copy(contacted = true) else c
    })

/**
 * Update recent concerns for hysteresis. Concern can only escalate freely;
 * de-escalation requires [BeliefState.CONCERN_COOLDOWN_MS] to have elapsed.
 */
private fun updateRecentConcerns(
    existing: Map<AircraftId, xyz.easiersaid.twr.controller.observe.RecentConcern>,
    assessments: List<xyz.easiersaid.twr.controller.observe.SeparationAssessment>,
    now: SimTime,
): Map<AircraftId, xyz.easiersaid.twr.controller.observe.RecentConcern> {
    val updated = existing.toMutableMap()
    @Suppress("LoopWithTooManyJumpStatements") // hysteresis logic with cooldown — explicit
    // continue/break paths are clearer than chained collection ops.
    for (assessment in assessments) {
        val follower = assessment.other
        val newConcern = assessment.concern
        val old = updated[follower]
        if (old == null) {
            updated[follower] = xyz.easiersaid.twr.controller.observe.RecentConcern(newConcern, now)
        } else if (newConcern is SeparationConcern.Severity && old.concern is SeparationConcern.Severity) {
            val newLevel = (newConcern as SeparationConcern.Severity).level
            val oldLevel = (old.concern as SeparationConcern.Severity).level
            if (newLevel >= oldLevel) {
                // Escalation: update immediately.
                updated[follower] = xyz.easiersaid.twr.controller.observe.RecentConcern(newConcern, now)
            } else if ((now.millis - old.since.millis) >= BeliefState.CONCERN_COOLDOWN_MS) {
                // De-escalation after cooldown: allow.
                updated[follower] = xyz.easiersaid.twr.controller.observe.RecentConcern(newConcern, now)
            }
            // Else: within cooldown, keep old concern (hysteresis applied in assessSeparation).
        } else {
            updated[follower] = xyz.easiersaid.twr.controller.observe.RecentConcern(newConcern, now)
        }
    }
    // Prune entries for aircraft no longer in any assessment.
    val activeFollowers = assessments.map { it.other }.toSet()
    updated.keys.retainAll(activeFollowers)
    return updated
}

private fun BeliefState.withLocEstablished(events: List<ControllerEvent>): BeliefState {
    val newlyEstablished = events.mapNotNull { event ->
        if (event is ControllerEvent.PositionReported && event.event is ReportEvent.EstablishedLocaliser)
            event.aircraft else null
    }.toSet()
    return if (newlyEstablished.isEmpty()) this
    else copy(establishedLocaliser = establishedLocaliser + newlyEstablished)
}

private fun BeliefState.withActiveRunway(view: ControllerView): BeliefState {
    val isRunwayCommandingRole = view.role == RoleName.TOWER || view.role == RoleName.GROUND
    val needsRunwaySelection = activeRunway == null || activeRunway !in view.runways
    return if (isRunwayCommandingRole && needsRunwaySelection)
        copy(activeRunway = selectRunwayIntoWind(
            view.runways.keys,
            // Controller has no weather observation at all → treat as
            // "no report yet"; selectRunwayIntoWind returns null and
            // active runway stays unset.
            view.weather?.wind ?: WindReport.NotReported,
        ))
    else this
}

/**
 * Observation-driven stage reconciliation. For each active commitment,
 * classify the aircraft's observed position and reconcile the stage.
 *
 * Observation always wins: if the aircraft is somewhere ahead of the
 * expected stage, the stage advances. This prevents commitments from
 * getting stuck when readbacks are lost or pilots act out of sequence.
 *
 * Currently wired for TOWER_DEPARTURE only. Arrival and ground procedures
 * will be added as their reconciliation functions are implemented.
 */
private fun reconcileObservedStages(
    beliefs: BeliefState,
    worldIndex: xyz.easiersaid.twr.core.world.WorldIndex,
): Map<AircraftId, Commitment> {
    if (beliefs.commitments.isEmpty()) return beliefs.commitments
    return beliefs.commitments.mapValues { (acId, commitment) ->
        // Clear the single-cycle transition flag from the previous cycle.
        val cleared = if (commitment.lastTransition != null) commitment.copy(lastTransition = null) else commitment
        if (cleared.isComplete) return@mapValues cleared
        val ac = beliefs.trackedAircraft[acId] ?: return@mapValues cleared
        when (cleared.kind) {
            CommitmentKind.TOWER_DEPARTURE -> {
                val stage = cleared.stage as? TowerDepartureStage
                    ?: return@mapValues cleared
                val position = classifyDeparturePosition(ac, worldIndex)
                val reconciled = reconcileDepartureStage(stage, position)
                if (reconciled.stage != stage) {
                    cleared.copy(stage = reconciled.stage, lastTransition = reconciled.transition)
                } else cleared
            }
            CommitmentKind.TOWER_ARRIVAL -> {
                val stage = cleared.stage as? TowerArrivalStage
                    ?: return@mapValues cleared
                val position = classifyArrivalPosition(ac, worldIndex)
                val reconciled = reconcileArrivalStage(stage, position)
                if (reconciled.stage != stage) {
                    cleared.copy(stage = reconciled.stage, lastTransition = reconciled.transition)
                } else cleared
            }
            CommitmentKind.GROUND_TAXI -> {
                val activeRunway = beliefs.activeRunway
                val position = classifyGroundPosition(ac, activeRunway, worldIndex)
                when (val stage = cleared.stage) {
                    is GroundDepartureStage -> {
                        val reconciled = reconcileGroundDepartureStage(stage, position)
                        if (reconciled.stage != stage) cleared.copy(stage = reconciled.stage, lastTransition = reconciled.transition)
                        else cleared
                    }
                    is GroundArrivalStage -> {
                        val reconciled = reconcileGroundArrivalStage(stage, position)
                        if (reconciled.stage != stage) cleared.copy(stage = reconciled.stage, lastTransition = reconciled.transition)
                        else cleared
                    }
                    else -> cleared
                }
            }
            else -> cleared
        }
    }
}

/**
 * One aircraft's procedure-cycle outcome: the commitment that the rule fired against
 * and the [OperatorResult] it produced. Carrying the commitment along removes the
 * partial-function lookup that [arbitrate] and [advanceCommittedStages] would
 * otherwise need against [BeliefState.commitments].
 */
private data class ProcedureRun(
    val aircraft: AircraftId,
    val commitment: Commitment,
    val result: OperatorResult,
)

private fun executeProcedures(
    responsibilities: Set<AircraftId>,
    beliefs: BeliefState,
    ctx: OperatorContext,
): Pair<List<ProcedureRun>, List<SkippedAction>> {
    // Each responsibility produces a Right(run) when a rule fired, or a Left(skipped)
    // describing why nothing fired. Aircraft with no tracked state / no procedure for
    // their commitment kind are filtered out entirely.
    val attempts: List<Either<SkippedAction, ProcedureRun>> =
        responsibilities.mapNotNull { acId -> attemptProcedure(acId, beliefs, ctx) }
    val runs = attempts.mapNotNull { it.getOrNull() }
    val skipped = attempts.mapNotNull { it.leftOrNull() }
    return runs to skipped
}

private fun attemptProcedure(
    acId: AircraftId,
    beliefs: BeliefState,
    ctx: OperatorContext,
): Either<SkippedAction, ProcedureRun>? {
    val commitment = beliefs.commitments[acId] ?: return null
    if (commitment.isComplete) return null
    val ac = beliefs.trackedAircraft[acId] ?: return null
    val spec = PROCEDURES[commitment.kind] ?: return null

    val outcome = executeProcedure(spec, commitment, ac, ctx)
    val result = outcome.result
    return if (result != null) {
        ProcedureRun(acId, commitment, result).right()
    } else {
        val guardTraces = traceRuleFailures(spec, commitment, ac, ctx)
        val resolutionLines = outcome.actionFailures
            .map { "${it.ruleId}: action resolution failed — ${it.reason}" }
        val guardLines = guardTraces
            .map { "${it.ruleId}: passed=${it.guardPassed} ${it.failures}" }
        SkippedAction(
            acId, "${commitment.kind}/${commitment.stage.name}: no rule fired",
            resolutionLines + guardLines,
        ).left()
    }
}

// Alias to avoid name clash with bdi.executeProcedure
private fun executeAllProcedures(
    responsibilities: Set<AircraftId>,
    beliefs: BeliefState,
    ctx: OperatorContext,
) = executeProcedures(responsibilities, beliefs, ctx)

/**
 * Fold state for [arbitrate]. Tracks committed outputs, which aircraft they went to,
 * and which non-SAFETY urgencies have already fired this cycle (SAFETY bypasses the
 * one-per-urgency budget).
 */
private data class ArbState(
    val outputs: List<ControllerOutput.Instruct> = emptyList(),
    val committed: Set<AircraftId> = emptySet(),
    val committedByUrgency: Set<Urgency> = emptySet(),
)

private fun arbitrate(
    runs: List<ProcedureRun>,
    beliefs: BeliefState,
    view: ControllerView,
): Pair<List<ControllerOutput.Instruct>, Set<AircraftId>> {
    val sorted = runs.sortedWith(
        compareBy<ProcedureRun> { it.result.urgency.ordinal }
            .thenBy { it.commitment.formedAt.millis }
    )
    val final = sorted.fold(ArbState()) { state, run ->
        val result = run.result
        val action = result.action ?: return@fold state
        val isSafety = result.urgency == Urgency.SAFETY
        // SAFETY: unlimited. Other urgencies: one per level per cycle.
        if (!isSafety && result.urgency in state.committedByUrgency) return@fold state

        // Feasibility check: is this instruction coherent for the aircraft's state?
        // SAFETY-urgency outputs bypass feasibility — they must not be suppressed.
        if (!isSafety) {
            val ac = beliefs.trackedAircraft[action.aircraft]
            if (ac != null) {
                val feasibility = checkFeasibility(action.instruction, ac, view, beliefs)
                if (feasibility is Feasibility.Infeasible) return@fold state
            }
        }

        val instruct = ControllerOutput.Instruct(
            target = action.aircraft, dispatch = action.dispatch,
            obligation = null,
            urgency = result.urgency, trace = result.trace,
            advanceToStage = result.nextStage,
            advancementPolicy = result.advancementPolicy,
            readbackAdvancesToStage = result.readbackAdvancesToStage,
        )
        state.copy(
            outputs = state.outputs + instruct,
            committed = state.committed + run.aircraft,
            committedByUrgency = if (isSafety) state.committedByUrgency
                else state.committedByUrgency + result.urgency,
        )
    }
    return final.outputs to final.committed
}

/**
 * Advance committed stages for rules with [AdvancementPolicy.Immediate].
 *
 * Readback-gated advancement is handled separately by [readbackAdvancesToStage]
 * on the coordination ledger — the readback validator applies it when confirmed.
 */
private fun advanceCommittedStages(
    beliefs: BeliefState,
    runs: List<ProcedureRun>,
    committedAircraft: Set<AircraftId>,
): BeliefState = runs.fold(beliefs) { acc, run ->
    val result = run.result
    if (result.nextStage == null) return@fold acc
    val wasCommitted = run.aircraft in committedAircraft
    val isStageOnlyAdvance = result.action == null
    if (!wasCommitted && !isStageOnlyAdvance) return@fold acc

    val current = acc.commitments[run.aircraft] ?: run.commitment
    acc.copy(
        commitments = acc.commitments + (run.aircraft to current.copy(
            stage = result.nextStage,
            formedAt = result.stampReadyAt ?: current.formedAt,
        ))
    )
}

/** Emit companion outputs for sequence info and traffic info alongside committed instructions. */
private fun deriveCompanionOutputs(
    outputs: List<ControllerOutput.Instruct>,
    runs: List<ProcedureRun>,
): List<ControllerOutput> {
    val actionsByAircraft = runs.associate { run -> run.aircraft to run.result.action }
    return outputs.flatMap { output ->
        val action = actionsByAircraft[output.target]
        val companions = mutableListOf<ControllerOutput>()

        action?.sequenceInfo?.let { seq ->
            companions.add(ControllerOutput.Instruct(
                target = output.target,
                dispatch = Dispatch.Direct(
                    NumberInSequence.unsafe(output.target, seq.number, seq.behindTraffic)
                ),
                urgency = Urgency.INFORMATIONAL,
                trace = DecisionTrace(
                    ruleId = "SEQ-INFO", description = "Number ${seq.number} in sequence",
                    regulations = listOf(RegulationDatabase.ICAO4444_7_10),
                ),
            ))
        }

        action?.trafficInfo?.let { info ->
            companions.add(ControllerOutput.Respond(
                target = output.target,
                response = TrafficInformation(
                    target = output.target,
                    traffic = info.traffic,
                    clockPosition = info.clockPosition,
                    distanceNm = info.distanceNm,
                    level = info.level,
                    movement = info.movement,
                ),
                trace = DecisionTrace(
                    ruleId = "TRAFFIC-INFO", description = info.description,
                    regulations = listOf(RegulationDatabase.ICAO4444_7_10),
                ),
            ))
        }

        companions
    }
}

/** Enrich instruction with wind/QNH based on instruction type (CAP 413 pattern from TWR1). */
private fun enrichInstruction(
    output: ControllerOutput.Instruct,
    weather: WeatherObservation?,
): ControllerOutput.Instruct {
    // No weather observation at all → don't enrich. WeatherObservation.wind
    // itself is non-nullable (sealed `WindReport`), so the only null path
    // here is the outer optional.
    val report = weather?.wind ?: return output
    val wind = when (report) {
        is WindReport.NotReported -> return output
        is WindReport.Available -> report.wind
    }
    val enriched = when (val instr = output.instruction) {
        is ClearedForTakeoff -> instr.copy(surfaceWind = wind)
        is ClearedToLand -> instr.copy(surfaceWind = wind)
        is ClearedTouchAndGo -> instr.copy(surfaceWind = wind)
        else -> return output
    }
    val newDispatch = when (val d = output.dispatch) {
        is Dispatch.Direct -> Dispatch.Direct(enriched)
        is Dispatch.Conditional -> Dispatch.Conditional(enriched, d.condition)
    }
    return output.copy(dispatch = newDispatch)
}

/**
 * Validate incoming readbacks against pending instructions.
 *
 * Four-state [ReadbackVerdict] model, per ICAO Doc 4444 §12.3.2 / CAP 413 §1.5.6:
 *   • CORRECT  → emit `ReadBackCorrect`, pop the matched pending entry.
 *   • INCORRECT → emit `ReadbackCorrection`, keep pending (pilot owes correct readback).
 *   • MISSING  → coordination escalates via [escalateOverdueCoordinations] (Pass 9 D-AUDIT.2):
 *                may re-issue or emit "say again" at discretion. Not produced here.
 *   • REFUSED  → pilot "unable"; pop pending, do NOT activate, route to re-sequencing.
 *                Produced by PilotRequest.Unable processing, not by readback classification.
 *
 * This function handles CORRECT and INCORRECT only (classification of actual Readback
 * transmissions). MISSING and REFUSED arrive via separate paths.
 *
 * When multiple pendings are outstanding, we prefer a CORRECT match first (most-recent
 * wins); otherwise the most recent same-kind pending is the one we issue a correction
 * against (it's the one the pilot was almost certainly reading back).
 */
/** Fold accumulator: responses emitted so far, and the threaded belief state. */
private data class ReadbackFoldState(
    val responses: List<ControllerOutput.Respond>,
    val beliefs: BeliefState,
)

private fun validatedReadbackResponses(
    view: ControllerView,
    beliefs: BeliefState,
): Pair<List<ControllerOutput.Respond>, BeliefState> {
    val final = view.receivedMessages.fold(ReadbackFoldState(emptyList(), beliefs)) { state, msg ->
        processReadback(msg, state)
    }
    return final.responses to final.beliefs
}

@Suppress("ReturnCount") // guard-clause pattern with coordination matching
private fun processReadback(
    msg: ReceivedMessage,
    state: ReadbackFoldState,
): ReadbackFoldState {
    val readback = msg.transmission as? Readback ?: return state
    // Coordinations survive past responsibility transfer (see Observe.kt comment). A readback
    // that arrives after the aircraft left responsibilities (e.g. after ContactFrequency hands
    // off) can still be confirmed here — the coordination is the authoritative gate, not the
    // responsibilities set. The readback's receiver (ReceiverRef.Controller) already ensures
    // only the intended controller sees this message; the coordination check below is sufficient.
    val coords = state.beliefs.coordinations[msg.aircraft]?.filter {
        it.state is CoordinationState.Issued
    } ?: return state
    if (coords.isEmpty()) return state

    // Prefer a CORRECT match (most recent wins). Otherwise find the most recent
    // Incorrect so we correct against the instruction the pilot likely intended.
    val classified = coords.mapIndexed { i, c -> i to classifyReadback(c.instruction, readback) }
    val correctIdx = classified.lastOrNull { it.second is ReadbackVerdict.Correct }?.first
    if (correctIdx != null) return acceptReadback(msg.aircraft, coords, correctIdx, state)

    val correctionTarget = classified.lastOrNull { it.second is ReadbackVerdict.Incorrect }
        ?: return state
    return correctReadback(msg.aircraft, coords, correctionTarget, state)
}

/**
 * Accept a correct readback: mark the coordination CONFIRMED, advance the
 * commitment stage if the coordination carries an advanceToStage.
 */
private fun acceptReadback(
    aircraft: AircraftId,
    coords: List<OutstandingCoordination>,
    correctIdx: Int,
    state: ReadbackFoldState,
): ReadbackFoldState {
    val confirmed = coords[correctIdx]
    val response = ControllerOutput.Respond(
        target = aircraft,
        response = ReadBackCorrect(aircraft),
        trace = DecisionTrace(
            ruleId = "READBACK-CORRECT",
            description = "Confirm validated readback",
            regulations = listOf(RegulationDatabase.ICAO9432_READBACK),
        ),
    )
    // Mark the coordination CONFIRMED and remove it from the active list.
    val remaining = coords.filterIndexed { i, _ -> i != correctIdx }
    val allCoords = state.beliefs.coordinations.toMutableMap()
    if (remaining.isEmpty()) allCoords.remove(aircraft)
    else allCoords[aircraft] = remaining
    var beliefs = state.beliefs.copy(coordinations = allCoords)

    // If the coordination carries a stage advancement, apply it now.
    if (confirmed.advanceToStage != null) {
        val commitment = beliefs.commitments[aircraft]
        if (commitment != null) {
            beliefs = beliefs.copy(
                commitments = beliefs.commitments + (aircraft to commitment.copy(stage = confirmed.advanceToStage)),
            )
        }
    }

    return ReadbackFoldState(
        responses = state.responses + response,
        beliefs = beliefs,
    )
}

private fun correctReadback(
    aircraft: AircraftId,
    coords: List<OutstandingCoordination>,
    target: Pair<Int, ReadbackVerdict>,
    state: ReadbackFoldState,
): ReadbackFoldState {
    val (idx, verdict) = target
    val correction = ReadbackCorrection(aircraft, coords[idx].instruction, (verdict as ReadbackVerdict.Incorrect).defects)
    val response = ControllerOutput.Respond(
        target = aircraft,
        response = correction,
        trace = DecisionTrace(
            ruleId = "READBACK-CORRECTION",
            description = "Negative — re-transmit correct ${correction.kind.name.lowercase().replace('_', ' ')}",
            regulations = listOf(
                RegulationDatabase.ICAO9432_READBACK,
                RegulationDatabase.ICAO4444_12_3_2,
            ),
        ),
    )
    // Pending stays — the pilot still owes a correct readback.
    return state.copy(responses = state.responses + response)
}
