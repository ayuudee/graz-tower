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
import xyz.easiersaid.twr.controller.bdi.*
import xyz.easiersaid.twr.controller.observe.*
import xyz.easiersaid.twr.controller.procedure.approachArrivalProcedure
import xyz.easiersaid.twr.controller.procedure.groundTaxiProcedure
import xyz.easiersaid.twr.controller.procedure.towerArrivalProcedure
import xyz.easiersaid.twr.controller.procedure.towerDepartureProcedure
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.protocol.*

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
    val events = deriveEventsFromMessages(view.receivedMessages) +
        view.pendingInboundHandoffs.map { ControllerEvent.HandoffOffered(it.aircraft, it.from) }

    val contactedAircraft = events.contactedAircraft()

    val beliefs = updateBeliefs(previousBeliefs, view)
        .gcOldCoordinations(view.time)
        .withContactMarked(contactedAircraft)
        .withLocEstablished(events)
        .withActiveRunway(view)
        .let { b ->
            val commitments = reconcileCommitments(
                existing = b.commitments, role = view.role, aircraft = b.trackedAircraft,
                responsibilities = view.responsibilities, activeRunway = b.activeRunway,
                time = view.time, worldIndex = view.worldIndex, contactedThisCycle = contactedAircraft,
            )
            b.copy(commitments = commitments)
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
    val allInstructs = outputs + reactiveInstructs
    val finalBeliefs = afterValidation
        .recordCoordinations(allInstructs, view.time)

    return ControllerDecisionResult(
        outputs = outputs + reactiveOutputs + companions + responses,
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

private fun BeliefState.withActiveRunway(view: ControllerView): BeliefState =
    if ((view.role == RoleName.TOWER || view.role == RoleName.GROUND) &&
        (activeRunway == null || activeRunway !in view.runways))
        copy(activeRunway = selectRunwayIntoWind(view.runways.keys, view.weather?.wind))
    else this

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
 * Advance committed stages — but only for rules with [AdvancementPolicy.Immediate].
 *
 * For [AdvancementPolicy.OnReadbackConfirmed], the stage advancement is deferred
 * to the readback validation pipeline. The coordination ledger carries the
 * advanceToStage; the readback validator applies it when confirmed.
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

    // OnReadbackConfirmed: stage advancement deferred to readback validation.
    if (result.advancementPolicy is AdvancementPolicy.OnReadbackConfirmed) return@fold acc

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
    val wind = weather?.wind ?: return output
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
 *   • MISSING  → pending ages out via GC ([gcOldPendingReadbacks]); after TTL, controller
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
        processReadback(msg, view.responsibilities, state)
    }
    return final.responses to final.beliefs
}

@Suppress("ReturnCount") // guard-clause pattern with coordination matching
private fun processReadback(
    msg: ReceivedMessage,
    responsibilities: Set<AircraftId>,
    state: ReadbackFoldState,
): ReadbackFoldState {
    val readback = msg.transmission as? Readback ?: return state
    if (msg.aircraft !in responsibilities) return state
    val coords = state.beliefs.coordinations[msg.aircraft]?.filter {
        it.state == CoordinationState.ISSUED || it.state == CoordinationState.QUERYING
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
    val kind = if ((verdict as ReadbackVerdict.Incorrect).hasWrongValue)
        ReadbackCorrectionKind.INCORRECT_ATOM else ReadbackCorrectionKind.MISSING_ATOM
    val response = ControllerOutput.Respond(
        target = aircraft,
        response = ReadbackCorrection(aircraft, coords[idx].instruction, kind),
        trace = DecisionTrace(
            ruleId = "READBACK-CORRECTION",
            description = "Negative — re-transmit correct ${kind.name.lowercase().replace('_', ' ')}",
            regulations = listOf(
                RegulationDatabase.ICAO9432_READBACK,
                RegulationDatabase.ICAO4444_12_3_2,
            ),
        ),
    )
    // Pending stays — the pilot still owes a correct readback.
    return state.copy(responses = state.responses + response)
}
