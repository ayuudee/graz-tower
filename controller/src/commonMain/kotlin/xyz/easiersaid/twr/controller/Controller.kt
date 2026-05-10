@file:Suppress("TooManyFunctions") // Controller.kt is the pipeline orchestration root —
// every stage (advanceCommittedStages, applyCommittedOutputWitnesses,
// applySupersessionCleanup callers, readback validation, escalation,
// reactive interventions, handoff re-issue, etc.) is a small focused
// function. Extracting them into separate files would fragment the
// pipeline's single-cycle narrative.

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
import xyz.easiersaid.twr.controller.certify.ActionCertifier
import xyz.easiersaid.twr.controller.certify.CertificationContext
import xyz.easiersaid.twr.controller.certify.KotlinRuntimeKernelCertifiers
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
import xyz.easiersaid.twr.controller.bdi.Stage
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.bdi.TowerDepartureStage
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.classifyReadback
import xyz.easiersaid.twr.controller.observe.coordinationEscalationOutputs
import xyz.easiersaid.twr.controller.observe.escalateOverdueCoordinations
import xyz.easiersaid.twr.controller.observe.markCoordinationEscalationsEmitted
import xyz.easiersaid.twr.controller.observe.withRecentRadio
import xyz.easiersaid.twr.controller.observe.withCircuitIntentEvents
import xyz.easiersaid.twr.controller.observe.withRunwayObstructionEvents
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
import xyz.easiersaid.twr.protocol.RunwayObstructionInformation
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
    // fn-12 (R3c): event-assembly concatenates world-state-derived events
    // (sim's world-diff producer per cycle) with radio-derived events. The
    // world events are folded into BeliefState BEFORE the radio events
    // would consult that slice — though current radio-folds don't read
    // runwayObstructions, so the ordering is structural rather than
    // load-bearing. World events cover only the per-controller scoped
    // runway set (see ControllerView.worldEvents KDoc).
    val events: List<ControllerEvent> = view.worldEvents + deriveEventsFromMessages(view.receivedMessages)

    val contactedAircraft = events.contactedAircraft()

    val beliefs = updateBeliefs(previousBeliefs, view)
        .escalateOverdueCoordinations(view.time)
        .withContactMarked(contactedAircraft)
        .withLocEstablished(events)
        .withExpectedAtisLetter(view)
        .withActiveRunway(view)
        .withRecentRadio(events, view.time)
        .withCircuitIntentEvents(events)
        .withRunwayObstructionEvents(events)
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
            val reconciled = reconcileObservedStages(b, view.worldIndex, events)
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
    val certificationContext = CertificationContext(view, beliefs, world, view.time)
    val arbitration = arbitrate(runs, beliefs, view, certificationContext)
    val outputs = arbitration.outputs
    val committedAircraft = arbitration.committedAircraft
    val companions = deriveCompanionOutputs(outputs, runs)

    // Phase 6b Phase B: reactive safety net — catch separation concerns not addressed by procedures.
    val reactiveIntv = reactiveInterventions(beliefs, outputs)
    val reactiveOutputs = emitReactiveOutputs(reactiveIntv, beliefs)
    val reactiveInstructs = reactiveOutputs.filterIsInstance<ControllerOutput.Instruct>()

    val stageAdvancedBeliefs = advanceCommittedStages(beliefs, runs, committedAircraft)
    // fn-13.1 (R6): set committed-output-only witnesses for rules that
    // either advance with nextStage = null (cannot be hosted inside
    // advanceCommittedStages) or whose witness semantics are independent
    // of stage progression. Walks only `committedAircraft` runs — the
    // failure-mode suppression is structurally impossible.
    val witnessedBeliefs = applyCommittedOutputWitnesses(stageAdvancedBeliefs, runs, committedAircraft)

    // Supersession cleanup: procedural outputs first, then reactive outputs.
    val proceduralInstructions = outputs.map { it.target to it.instruction }
    val reactiveInstructions = reactiveInstructs.map { it.target to it.instruction }
    val afterProceduralSupersession = applySupersessionCleanup(witnessedBeliefs, proceduralInstructions)
    val afterReactiveSupersession = applySupersessionCleanup(afterProceduralSupersession, reactiveInstructions)

    val (responses, afterValidation) = validatedReadbackResponses(view, afterReactiveSupersession)

    // Pass 15 (D-AUDIT.8 closure): scan received `InitialContact`
    // messages for ATIS-letter mismatches against `expectedAtisLetter`
    // for the controller's aerodrome. Emit `CurrentInformationIs`
    // advisory directly (no procedure rule — same shape as readback
    // validation: deterministic response in the radio-receive flow).
    // Per ICAO Annex 11 §4.3.6: advisory transmission, no readback
    // obligation; pilot acknowledges silently and obtains the current
    // ATIS on a separate frequency.
    val atisAdvisories = atisLetterMismatchAdvisories(view, beliefs)

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

    // Pass 12 (D-PF.9): re-issue ContactFrequency for missed handoffs.
    // Sim's sweep wrote the escalation; the projection on view surfaces
    // it; this function emits a fresh ContactFrequency and bumps
    // `handoffReissuedAt` to dampen per-cycle re-emission.
    //
    // Re-issued ContactFrequency goes through `recordCoordinations`
    // below — it IS a fresh issuance (the original was confirmed via the
    // pilot's readback; what's missing is the comms transition to the
    // new freq, not the readback). The handoff escalation continues or
    // resolves via the sim's normal flow.
    val (handoffReissueOutputs, afterHandoffMark) = missedHandoffReissueOutputs(view, afterEscalationMark)
    val handoffReissueInstructs = handoffReissueOutputs.filterIsInstance<ControllerOutput.Instruct>()
    // Pass 12 post-impl F.2: re-issued ContactFrequency supersedes the
    // original (now-escalated) ContactFrequency coordination. Without
    // this cleanup, the original keeps emitting TransmittingBlind from
    // its LostCommsDeclared lifecycle WHILE the re-issue path is also
    // active — double-bothering the pilot. The supersession relation
    // (`SUPERSESSION_RELATIONS` ContactFrequency-vs-ContactFrequency
    // ABANDON) handles the cleanup; we just need to invoke it on the
    // freshly-emitted handoff re-issue instructions.
    val handoffReissueInstructions = handoffReissueInstructs.map { it.target to it.instruction }
    val afterHandoffSupersession = applySupersessionCleanup(afterHandoffMark, handoffReissueInstructions)

    val allInstructs = outputs + reactiveInstructs + handoffReissueInstructs
    val finalBeliefs = afterHandoffSupersession
        .recordCoordinations(allInstructs, view.time)

    return ControllerDecisionResult(
        outputs = outputs + reactiveOutputs + companions + responses + atisAdvisories + escalationOutputs + handoffReissueOutputs,
        updatedBeliefs = finalBeliefs,
        trace = OverallDecisionTrace(
            controllerId = view.controllerId, time = view.time,
            actionsConsidered = runs.size, actionsCommitted = outputs.size,
            skippedActions = skipped + arbitration.skipped,
        ),
    )
}

// ── Pipeline stages ──────────────────────────────────────────────────

private fun List<ControllerEvent>.contactedAircraft(): Set<AircraftId> =
    mapNotNull { event ->
        // fn-12 (R2): explicit per-leaf coverage — the prior `else -> null`
        // would silently swallow newly-added leaves. Per the no-corners rule,
        // every ControllerEvent leaf gets an explicit arm. Non-contact-bearing
        // leaves (intent / sequencing / world-state events) return null.
        when (event) {
            is ControllerEvent.ReadyForDepartureReceived -> event.aircraft
            is ControllerEvent.InitialContactReceived -> event.aircraft
            is ControllerEvent.ReadbackReceived -> event.aircraft
            is ControllerEvent.PositionReported -> event.aircraft
            // Other leaves do NOT count as a fresh radio contact for the
            // contacted-this-cycle witness. StartupRequested / TaxiRequested
            // / GoAroundDetected / ResponsibilityTaken / UnableReceived /
            // TrafficInSightReceived / PilotRequestReceived /
            // CircuitIntentReported / AircraftArrivalCommitted are emitted
            // alongside an InitialContact / Request / Acknowledge / Report
            // that already covers the contact, OR they're internal
            // controller-state transitions (ResponsibilityTaken).
            // RunwayObstructionDetected / RunwayObstructionCleared are
            // runway-scoped world events — no aircraft to mark.
            is ControllerEvent.StartupRequested,
            is ControllerEvent.TaxiRequested,
            is ControllerEvent.GoAroundDetected,
            is ControllerEvent.ResponsibilityTaken,
            is ControllerEvent.UnableReceived,
            is ControllerEvent.TrafficInSightReceived,
            is ControllerEvent.PilotRequestReceived,
            is ControllerEvent.CircuitIntentReported,
            is ControllerEvent.AircraftArrivalCommitted,
            is ControllerEvent.RunwayObstructionDetected,
            is ControllerEvent.RunwayObstructionCleared -> null
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
    if (!isRunwayCommandingRole || !needsRunwaySelection) return this
    // Pass 15 (D-AUDIT.7 / .8 fold-in): operational priority — the
    // supervisor's published ATIS is the authoritative source for
    // runway-in-use; the wind-derived selection is the *recommendation*
    // a controller would compute when no supervisor decision exists
    // (Doc 4444 §7.2). The fallback chain mirrors that semantic, with
    // the side benefit that tests pre-Pass-15 (no AtisIssued event in
    // the fixture) continue to derive the same active runway via wind.
    val atisPrimary = view.atis[view.aerodromeId]?.configuration?.primary
    val activeRunwaySelection = atisPrimary
        ?: selectRunwayIntoWind(
            view.runways.keys,
            // Controller has no weather observation at all → treat as
            // "no report yet"; selectRunwayIntoWind returns null and
            // active runway stays unset.
            view.weather?.wind ?: WindReport.NotReported,
        )
    return copy(activeRunway = activeRunwaySelection)
}

/**
 * Pass 15 (D-AUDIT.8 closure): fold `expectedAtisLetter` from
 * `view.atis`. The controller's expected letter for each aerodrome
 * tracks the latest published ATIS. Single-write site enforced by
 * `FirewallBeliefWriteTest`.
 */
private fun BeliefState.withExpectedAtisLetter(view: ControllerView): BeliefState {
    if (view.atis.isEmpty() && expectedAtisLetter.isEmpty()) return this
    val next = view.atis.mapValues { (_, atis) -> atis.letter }
    return if (next == expectedAtisLetter) this else copy(expectedAtisLetter = next)
}

/**
 * Pass 15 (D-AUDIT.8 closure): scan received `InitialContact`
 * messages for ATIS-letter mismatches against the controller's
 * expected letter. Emit `CurrentInformationIs` advisory per ICAO
 * Annex 11 §4.3.6 (advisory transmission; no readback obligation).
 *
 * Mismatch criteria: pilot's `InitialContact.atisCode` is non-null
 * AND differs from `beliefs.expectedAtisLetter[view.aerodromeId]`.
 * A null `atisCode` is the legacy shape (pre-ATIS-availability) —
 * not a mismatch. A null expected letter (no ATIS published) — not
 * a mismatch (the controller can't compare).
 */
private fun atisLetterMismatchAdvisories(
    view: ControllerView,
    beliefs: BeliefState,
): List<ControllerOutput> {
    val expected = beliefs.expectedAtisLetter[view.aerodromeId] ?: return emptyList()
    return view.receivedMessages.mapNotNull { msg ->
        val ic = msg.transmission as? xyz.easiersaid.twr.protocol.InitialContact ?: return@mapNotNull null
        val received = ic.atisCode ?: return@mapNotNull null
        if (received == expected) return@mapNotNull null
        ControllerOutput.Respond(
            target = msg.aircraft,
            response = xyz.easiersaid.twr.protocol.CurrentInformationIs(
                target = msg.aircraft,
                letter = expected,
            ),
            trace = DecisionTrace(
                ruleId = "ATIS-LETTER-MISMATCH",
                description = "Pilot acknowledged information $received; current is $expected",
                regulations = listOf(
                    xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO_ANNEX_11_4_3,
                ),
            ),
        )
    }
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
    events: List<xyz.easiersaid.twr.controller.observe.ControllerEvent> = emptyList(),
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
                // fn-8.3 Phase 2 (B3): sticky pilot-Ready witness. The
                // pilot's single-cycle Report(Ready) gets retained on the
                // commitment so `DEP-LUAW` can fire later when the runway
                // becomes available — even if many cycles after the Ready
                // report. Once true, never cleared during the commitment.
                val readyNow = events.any {
                    it is xyz.easiersaid.twr.controller.observe.ControllerEvent.ReadyForDepartureReceived &&
                        it.aircraft == acId
                }
                val readyFlag = cleared.pilotReadyDuringCommitment || readyNow
                val baseStage = if (reconciled.stage != stage) {
                    cleared.copy(stage = reconciled.stage, lastTransition = reconciled.transition)
                } else cleared
                if (readyFlag != baseStage.pilotReadyDuringCommitment) {
                    baseStage.copy(pilotReadyDuringCommitment = readyFlag)
                } else baseStage
            }
            CommitmentKind.TOWER_ARRIVAL ->
                reconcileTowerArrival(cleared, ac, acId, worldIndex, events)
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
 * Reconcile a [TOWER_ARRIVAL][CommitmentKind.TOWER_ARRIVAL] commitment
 * against an aircraft observation + this cycle's events. Extracted from
 * [reconcileObservedStages] to keep that function within detekt's
 * cyclomatic-complexity budget; the body is otherwise unchanged.
 *
 * Updates three things:
 *  - **Stage** via [reconcileArrivalStage] on the aircraft's classified
 *    arrival position.
 *  - **`touchedDownDuringCommitment`** sticky witness (B2): set when the
 *    aircraft is at a `RunwayRef` AND `onGround`.
 *  - **`observedReportsDuringCommitment`** sticky witness (B5-α): unions
 *    every `PositionReported` event for this aircraft this cycle.
 *
 * Returns [cleared] unchanged when the stage is not a
 * [TowerArrivalStage] (defensive — the type system encodes the
 * invariant, but the cast guards a future kind reuse).
 */
private fun reconcileTowerArrival(
    cleared: Commitment,
    ac: AircraftObservation,
    acId: AircraftId,
    worldIndex: xyz.easiersaid.twr.core.world.WorldIndex,
    events: List<xyz.easiersaid.twr.controller.observe.ControllerEvent>,
): Commitment {
    val stage = cleared.stage as? TowerArrivalStage ?: return cleared
    val position = classifyArrivalPosition(ac, worldIndex)
    val reconciled = reconcileArrivalStage(stage, position)
    // fn-8.3 Phase 2 (B2): sticky witness for genuine touchdown during
    // this commitment lifetime.
    val touchedDownNow = ac.entities.any { it is xyz.easiersaid.twr.core.world.EntityRef.RunwayRef } &&
        ac.onGround
    val touchdownFlag = cleared.touchedDownDuringCommitment || touchedDownNow
    // fn-8.3 Phase 4 (B5-α): sticky witness recording every
    // PositionReported event during this commitment lifetime.
    val reportsThisCycle = events.asSequence()
        .filterIsInstance<xyz.easiersaid.twr.controller.observe.ControllerEvent.PositionReported>()
        .filter { it.aircraft == acId }
        .map { it.event }
        .toSet()
    val reportsFlag = if (reportsThisCycle.isEmpty()) {
        cleared.observedReportsDuringCommitment
    } else {
        cleared.observedReportsDuringCommitment + reportsThisCycle
    }
    val baseStage = if (reconciled.stage != stage) {
        cleared.copy(stage = reconciled.stage, lastTransition = reconciled.transition)
    } else cleared
    val withTouchdown = if (touchdownFlag != baseStage.touchedDownDuringCommitment) {
        baseStage.copy(touchedDownDuringCommitment = touchdownFlag)
    } else baseStage
    val withReports = if (reportsFlag != withTouchdown.observedReportsDuringCommitment) {
        withTouchdown.copy(observedReportsDuringCommitment = reportsFlag)
    } else withTouchdown
    // fn-12 (R7-no-refire — re-arm hook): clear the
    // obstruction-GA-issued-this-attempt witness on the next Downwind
    // report from this aircraft on this commitment. After the witness
    // clears, a fresh obstruction can drive a fresh GA on the recovery
    // approach. The clear happens in the same event-processing tick as
    // the Downwind report folds, so the next cycle's rule evaluation
    // sees the cleared witness.
    //
    // fn-13.1 (R6 — re-arm hook extension): the CONTINUE APPROACH
    // witness shares the same lifecycle (commitment-attempt-scoped,
    // re-armed on next Downwind report). Clear BOTH witnesses on the
    // same trigger so the fresh recovery approach can drive both a fresh
    // obstruction GA and a fresh CONTINUE APPROACH if the obstruction
    // pattern recurs.
    val downwindReportedThisCycle = events.any { ev ->
        ev is xyz.easiersaid.twr.controller.observe.ControllerEvent.PositionReported &&
            ev.aircraft == acId &&
            ev.event is xyz.easiersaid.twr.protocol.ReportEvent.Downwind
    }
    val needsObstructionGaRearm =
        downwindReportedThisCycle && withReports.obstructionGoAroundIssuedThisAttempt
    val needsContinueApproachRearm =
        downwindReportedThisCycle && withReports.continueApproachIssuedThisAttempt
    return when {
        needsObstructionGaRearm && needsContinueApproachRearm -> withReports.copy(
            obstructionGoAroundIssuedThisAttempt = false,
            continueApproachIssuedThisAttempt = false,
        )
        needsObstructionGaRearm -> withReports.copy(obstructionGoAroundIssuedThisAttempt = false)
        needsContinueApproachRearm -> withReports.copy(continueApproachIssuedThisAttempt = false)
        else -> withReports
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
    val skipped: List<SkippedAction> = emptyList(),
)

private data class ArbitrationResult(
    val outputs: List<ControllerOutput.Instruct>,
    val committedAircraft: Set<AircraftId>,
    val skipped: List<SkippedAction>,
)

private fun arbitrate(
    runs: List<ProcedureRun>,
    beliefs: BeliefState,
    view: ControllerView,
    certificationContext: CertificationContext,
): ArbitrationResult {
    val certifier = ActionCertifier(KotlinRuntimeKernelCertifiers)
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

        val enrichedAction = enrichAction(action, view.weather)
        val certified = when (val certification = certifier.certify(enrichedAction, certificationContext)) {
            is Either.Left -> {
                val failure = certification.value
                return@fold state.copy(
                    skipped = state.skipped + SkippedAction(
                        aircraft = action.aircraft,
                        reason = "${run.commitment.kind}/${run.commitment.stage.name}: certification rejected",
                        ruleTraces = listOf("${result.trace.ruleId}: ${failure.describe()}"),
                    ),
                )
            }
            is Either.Right -> certification.value
        }

        val instruct = ControllerOutput.Instruct.fromCertified(
            certified = certified,
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
    return ArbitrationResult(final.outputs, final.committed, final.skipped)
}

private fun xyz.easiersaid.twr.controller.certify.CertificationFailure.describe(): String =
    when (this) {
        is xyz.easiersaid.twr.controller.certify.CertificationFailure.UnsupportedInstruction ->
            "unsupported instruction ${instruction::class.simpleName}"
        is xyz.easiersaid.twr.controller.certify.CertificationFailure.MissingAircraft ->
            "missing aircraft $aircraft"
        is xyz.easiersaid.twr.controller.certify.CertificationFailure.StaleSnapshot ->
            "stale snapshot observedAt=$observedAt decisionAt=$decisionAt"
        is xyz.easiersaid.twr.controller.certify.CertificationFailure.KernelRejected ->
            "$requirement rejected: $reason"
        is xyz.easiersaid.twr.controller.certify.CertificationFailure.CompatibilityRejected ->
            "compatibility rejected: $reason"
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
    // fn-8.3 Phase 4 (B5-α): detect stage regression (e.g. go-around
    // backtracking from `LandingClearanceIssued` / `AwaitLandedObserved` to
    // `AwaitDownwind` per `GA-POST-CLEAR`). When the procedure's
    // `nextStage` has a strictly smaller ordinal than the current stage,
    // reset the commitment-scoped sticky witnesses so the aircraft re-
    // earns their `true` value on the post-regression circuit. Forward
    // advances keep the existing witness state. Mirrors fresh-commitment
    // formation (where `createCommitment` constructs with defaults).
    val regressed = isStageRegression(current.stage, result.nextStage)
    val advanced = current.copy(
        stage = result.nextStage,
        formedAt = result.stampReadyAt ?: current.formedAt,
    )
    val resetAdvanced = if (regressed) advanced.copy(
        touchedDownDuringCommitment = false,
        pilotReadyDuringCommitment = false,
        observedReportsDuringCommitment = emptySet(),
    ) else advanced
    // fn-13.1: obstruction-witness setting was previously inlined here
    // (fn-12 R7-no-refire); it now lives in [applyCommittedOutputWitnesses]
    // because the new `ARR-CONTINUE-APPROACH-OBSTRUCTION` rule has
    // `nextStage = null` and `advanceCommittedStages` early-returns when
    // `nextStage` is null (line above) — the witness pass would never run
    // for CONTINUE APPROACH if it stayed here. Both obstruction-class
    // witnesses (GA + CA) are set in the same dedicated pass after
    // `advanceCommittedStages` returns, walking only committed runs.
    acc.copy(
        commitments = acc.commitments + (run.aircraft to resetAdvanced),
    )
}

/**
 * fn-13.1 (R6 — witness-set timing): set commitment-scoped sticky
 * witnesses for committed-output rules whose witnesses are not gated by
 * stage advancement.
 *
 * Two cases today:
 *  - `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` (fn-12) → sets
 *    `obstructionGoAroundIssuedThisAttempt = true`. Has `nextStage =
 *    AwaitDownwind` so [advanceCommittedStages] sees it, but the witness
 *    semantics are independent of stage advancement (the witness re-arms
 *    on `Report(Downwind)`, not on stage transitions).
 *  - `ARR-CONTINUE-APPROACH-OBSTRUCTION` (fn-13.1) → sets
 *    `continueApproachIssuedThisAttempt = true`. Has `nextStage = null`
 *    so [advanceCommittedStages] cannot host the witness set
 *    (early-returns when nextStage is null).
 *
 * **Set-only-on-committed-output discipline**: walks only runs where the
 * aircraft appears in `committedAircraft` — by construction, candidates
 * that lost arbitration OR failed certification are NOT in this set, so
 * failed-arbitration suppression of legitimate next-cycle firings is
 * structurally impossible.
 *
 * Runs AFTER [advanceCommittedStages] so the witness writes layer on top
 * of any stage transitions; runs BEFORE supersession + readback validation
 * + escalation so downstream stages see the updated witness state.
 */
private fun applyCommittedOutputWitnesses(
    beliefs: BeliefState,
    runs: List<ProcedureRun>,
    committedAircraft: Set<AircraftId>,
): BeliefState = runs.fold(beliefs) { acc, run ->
    if (run.aircraft !in committedAircraft) return@fold acc
    val ruleId = run.result.trace.ruleId
    val current = acc.commitments[run.aircraft] ?: return@fold acc
    val updated = when (ruleId) {
        "ARR-GO-AROUND-RUNWAY-OBSTRUCTED" ->
            // Setting AFTER `advanceCommittedStages`'s regression-reset (which
            // resets touched-down / pilot-ready / observed-reports) ensures
            // the witness survives — the obstruction witness is
            // approach-attempt-scoped and is meaningful on the regressed
            // commitment.
            current.copy(obstructionGoAroundIssuedThisAttempt = true)
        "ARR-CONTINUE-APPROACH-OBSTRUCTION" ->
            // nextStage = null on this rule → commitment stage unchanged →
            // the witness is the only suppression mechanism preventing the
            // rule from re-firing every cycle while both predicates persist.
            current.copy(continueApproachIssuedThisAttempt = true)
        else -> return@fold acc
    }
    acc.copy(commitments = acc.commitments + (run.aircraft to updated))
}

/**
 * fn-8.3 Phase 4 (B5-α): detect a backward stage transition along a
 * stage hierarchy that defines a forward-only ordinal (e.g. go-around
 * regressing `LandingClearanceIssued`/`AwaitLandedObserved` -> `AwaitDownwind`).
 * Returns true when [next]'s ordinal is strictly less than [current]'s
 * within the same hierarchy. Stages that don't share an ordinal (e.g.
 * cross-hierarchy substitutions) are conservatively treated as
 * non-regressions — this function only fires for the documented
 * regression paths in [TowerArrivalStage] / [TowerDepartureStage] /
 * [GroundDepartureStage] / [GroundArrivalStage].
 */
private fun isStageRegression(current: Stage, next: Stage): Boolean = when {
    current is TowerArrivalStage && next is TowerArrivalStage -> next.ordinal < current.ordinal
    current is TowerDepartureStage && next is TowerDepartureStage -> next.ordinal < current.ordinal
    current is GroundDepartureStage && next is GroundDepartureStage -> next.ordinal < current.ordinal
    current is GroundArrivalStage && next is GroundArrivalStage -> next.ordinal < current.ordinal
    else -> false
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
            companions.add(ControllerOutput.Instruct.fromAdministrative(
                instruction = NumberInSequence.unsafe(output.target, seq.number, seq.behindTraffic),
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

        // fn-12 (R8): obstruction-info companion. Mirrors the trafficInfo
        // block above. Emitted alongside the dispatched `GoAround` or
        // `ContinueApproach` instruction when the rule populated
        // `obstructionInfo` (per ICAO §7.4.1.4.1(c) + §8.9.6.1.8 — reason
        // on radio is MUST for GA; CAP 413 §4.55-4.56 + ICAO 4444
        // §12.3.4.16(d) for CONTINUE APPROACH).
        //
        // fn-13.1 (R3 — companion trace regs split): when the action
        // populates `info.companionTraceRegs`, use those refs instead of
        // the fn-12 GA defaults. CONTINUE APPROACH must NOT cite
        // `CAP413_4_65` (missed-approach phraseology) or `ICAO4444_7_4_1_4_1`
        // (post-clearance GA mandate). The fallback preserves fn-12's GA
        // companion behaviour (regression check: GA companion still cites
        // §7.4.1.4.1, §8.9.6.1.8, §4.65).
        action?.obstructionInfo?.let { info ->
            val regulations = info.companionTraceRegs ?: listOf(
                RegulationDatabase.ICAO4444_7_4_1_4_1,
                RegulationDatabase.ICAO4444_8_9_6_1_8,
                RegulationDatabase.CAP413_4_65,
            )
            // fn-13.1 (R3 — codex round 2 finding): description must NOT
            // hardcode both §7.4.1.4.1(c) and §12.3.4.16(d) because they
            // are doctrinally exclusive paths (post-clearance GA vs
            // pre-clearance CONTINUE APPROACH). Read the action's
            // `companionTraceDescription` when provided; fall back to the
            // GA description (preserves fn-12's behaviour unchanged).
            val description = info.companionTraceDescription
                ?: "Inform aircraft of runway obstruction per ICAO 4444 §7.4.1.4.1(c)"
            companions.add(ControllerOutput.Respond(
                target = output.target,
                response = RunwayObstructionInformation(
                    target = output.target,
                    runway = info.runway,
                    clearsAt = info.clearsAt,
                ),
                trace = DecisionTrace(
                    ruleId = "OBSTRUCTION-INFO",
                    description = description,
                    regulations = regulations,
                ),
            ))
        }

        companions
    }
}

/** Enrich instruction with wind/QNH based on instruction type (CAP 413 pattern from TWR1). */
private fun enrichAction(
    action: xyz.easiersaid.twr.controller.bdi.ProposedAction,
    weather: WeatherObservation?,
): xyz.easiersaid.twr.controller.bdi.ProposedAction {
    // No weather observation at all → don't enrich. WeatherObservation.wind
    // itself is non-nullable (sealed `WindReport`), so the only null path
    // here is the outer optional.
    val report = weather?.wind ?: return action
    val wind = when (report) {
        is WindReport.NotReported -> return action
        is WindReport.Available -> report.wind
    }
    val enriched = when (val instr = action.instruction) {
        is ClearedForTakeoff -> instr.copy(surfaceWind = wind)
        is ClearedToLand -> instr.copy(surfaceWind = wind)
        is ClearedTouchAndGo -> instr.copy(surfaceWind = wind)
        else -> return action
    }
    val newDispatch = when (val d = action.dispatch) {
        is Dispatch.Direct -> Dispatch.Direct(enriched)
        is Dispatch.Conditional -> Dispatch.Conditional(enriched, d.condition)
    }
    return action.copy(dispatch = newDispatch)
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
    // Pass 12 (D-AUDIT.2.E): pilot's correct readback at ANY pre-supersession
    // state clears the coordination — Querying / Reissued / LostCommsDeclared
    // were the controller's internal escalation postures, not a lockout.
    // Pre-Pass-12 the filter was `is Issued`, which silently dropped late
    // readbacks after escalation AND had a latent bug: acceptReadback writes
    // `remaining = coords.filterIndexed` (the filtered list), which DESTROYED
    // unfiltered entries. Removing the filter both fixes the bug and lets
    // late readbacks resolve escalated entries.
    val coords = state.beliefs.coordinations[msg.aircraft] ?: return state
    if (coords.isEmpty()) return state

    // Prefer CORRECT matches. Otherwise find the most recent Incorrect so we
    // correct against the instruction the pilot likely intended.
    //
    // G2 closure: close *every* coordination whose verdict is Correct, not
    // only the most recent. A `NoPendingReadback`-gated fire-and-forget rule
    // (e.g. `DEP-CROSS-AERODROME-RELEASE`) deliberately re-fires on
    // escalation (Issued → Querying), creating duplicate identical
    // coordinations — same target, same instruction shape. The pilot's
    // single readback satisfies all of them; closing only the latest leaves
    // earlier identical coords orphaned, where they then chain through
    // COORD-QUERY → COORD-REISSUE → COORD-BLIND for nothing (the pilot is
    // already on the destination's frequency by then). Closing every
    // CORRECT match collapses the orphan chain.
    //
    // Safety: matchReadback returns Correct only when the readback is
    // structurally consistent with the instruction; if a future rule pair
    // issues two different RST instructions whose readbacks differ, only
    // the matching coord(s) close — the non-matching one stays open.
    val classified = coords.mapIndexed { i, c -> i to classifyReadback(c.readbackInstruction, readback) }
    val correctIdxs = classified.filter { it.second is ReadbackVerdict.Correct }.map { it.first }
    if (correctIdxs.isNotEmpty()) return acceptReadback(msg.aircraft, coords, correctIdxs, state)

    val correctionTarget = classified.lastOrNull { it.second is ReadbackVerdict.Incorrect }
        ?: return state
    return correctReadback(msg.aircraft, coords, correctionTarget, state)
}

/**
 * Accept correct readback(s): mark the coordination(s) CONFIRMED, advance the
 * commitment stage if any confirmed coord carries an `advanceToStage`.
 *
 * Closes every coord in [correctIdxs] (a single readback can satisfy multiple
 * duplicate coords — see callsite rationale). Emits a single `READBACK-CORRECT`
 * response (the controller acknowledges the readback once; multiple internal
 * coords being closed is a bookkeeping detail, not on-air phraseology).
 *
 * Stage advancement uses the most-recent (last-by-index) coord's
 * `advanceToStage` if any is non-null. Multiple coords with conflicting
 * advanceToStage values are unrepresentable in current code paths
 * (duplicates from `NoPendingReadback` re-fire are identical, including
 * `advanceToStage`); if a future doctrine introduces conflicting
 * advancements we'll need to disambiguate explicitly.
 */
private fun acceptReadback(
    aircraft: AircraftId,
    coords: List<OutstandingCoordination>,
    correctIdxs: List<Int>,
    state: ReadbackFoldState,
): ReadbackFoldState {
    val confirmed = correctIdxs.map { coords[it] }
    val response = ControllerOutput.Respond(
        target = aircraft,
        response = ReadBackCorrect(aircraft),
        trace = DecisionTrace(
            ruleId = "READBACK-CORRECT",
            description = "Confirm validated readback",
            regulations = listOf(RegulationDatabase.ICAO9432_READBACK),
        ),
    )
    // Mark the coordinations CONFIRMED and remove them from the active list.
    // Pass 12 (D-AUDIT.2.E follow-on): remove by *identity* against the
    // ORIGINAL coordinations list, not by index in `coords`. Pre-Pass-12
    // `coords` was the filter-narrowed list (Issued-only); writing back
    // `coords.filterIndexed` would silently destroy unfiltered entries
    // (e.g. Querying/Reissued) — a real bug masked by Pass 12's widened
    // filter.
    val confirmedSet = confirmed.toSet()
    val originalCoords = state.beliefs.coordinations[aircraft] ?: emptyList()
    val remaining = originalCoords.filter { it !in confirmedSet }
    val allCoords = state.beliefs.coordinations.toMutableMap()
    if (remaining.isEmpty()) allCoords.remove(aircraft)
    else allCoords[aircraft] = remaining
    var beliefs = state.beliefs.copy(coordinations = allCoords)

    // If any confirmed coord carries a stage advancement, apply the most
    // recent one. (Duplicate coords share the same advanceToStage today.)
    val advanceToStage = confirmed.lastOrNull { it.advanceToStage != null }?.advanceToStage
    if (advanceToStage != null) {
        val commitment = beliefs.commitments[aircraft]
        if (commitment != null) {
            beliefs = beliefs.copy(
                commitments = beliefs.commitments + (aircraft to commitment.copy(stage = advanceToStage)),
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
    val correction = ReadbackCorrection(aircraft, coords[idx].readbackInstruction, (verdict as ReadbackVerdict.Incorrect).defects)
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

/**
 * Pass 12 (D-PF.9): re-issue [ContactFrequency] for any missed handoff
 * the sim has just escalated. Reads the strip-shaped projection on
 * [ControllerView.outgoingMissedHandoffs] and emits a fresh handoff
 * instruction per just-escalated entry. Per-cycle dampening uses the
 * `since` timestamp on the notice + the new
 * [BeliefState.handoffReissuedAt] slice.
 */
internal fun missedHandoffReissueOutputs(
    view: ControllerView,
    beliefs: BeliefState,
): Pair<List<ControllerOutput>, BeliefState> {
    if (view.outgoingMissedHandoffs.isEmpty()) {
        return emptyList<ControllerOutput>() to beliefs
    }
    val outputs = mutableListOf<ControllerOutput>()
    val updatedReissues = beliefs.handoffReissuedAt.toMutableMap()
    for ((aircraft, notice) in view.outgoingMissedHandoffs) {
        val lastReissued = updatedReissues[aircraft]
        if (lastReissued != null && lastReissued >= notice.since) continue
        outputs += ControllerOutput.Instruct.fromMissedHandoffReissue(
            instruction = xyz.easiersaid.twr.protocol.ContactFrequency(
                target = aircraft,
                role = notice.targetRole,
                frequency = notice.targetFrequency,
            ),
            urgency = xyz.easiersaid.twr.protocol.Urgency.TIME_SENSITIVE,
            trace = DecisionTrace(
                ruleId = "HANDOFF-REISSUE",
                description = "Re-issue ContactFrequency after missed handoff (Doc 4444 §10.1; ~2 min sim doctrine)",
                regulations = listOf(
                    RegulationDatabase.ICAO4444_10_1,
                    RegulationDatabase.ICAO9432_FREQUENCY_CHANGE,
                ),
            ),
        )
        updatedReissues[aircraft] = notice.since
    }
    val newBeliefs = beliefs.copy(handoffReissuedAt = updatedReissues)
    return outputs to newBeliefs
}
