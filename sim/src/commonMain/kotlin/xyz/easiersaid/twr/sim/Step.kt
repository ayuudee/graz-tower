@file:Suppress("TooManyFunctions") // step handlers + radio helpers

package xyz.easiersaid.twr.sim

import arrow.core.NonEmptyList
import arrow.core.Some
import arrow.core.getOrElse
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CIRCUIT_ALTITUDE_M
import xyz.easiersaid.twr.pilot.PilotConstants
import xyz.easiersaid.twr.pilot.buildReadback
import xyz.easiersaid.twr.pilot.processControllerResponse
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.pilot.PilotInput
import xyz.easiersaid.twr.pilot.PilotIntent
import xyz.easiersaid.twr.pilot.PilotMission
import xyz.easiersaid.twr.pilot.PilotOutput
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.PilotRoute
import xyz.easiersaid.twr.pilot.RoutingError
import xyz.easiersaid.twr.pilot.buildVisualDepartureRoute
import xyz.easiersaid.twr.pilot.pilotDecide
import xyz.easiersaid.twr.pilot.processInstruction
import xyz.easiersaid.twr.pilot.updateAfterTransmission
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.ReceivedMessage
import xyz.easiersaid.twr.controller.controllerDecide
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeInstruction
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AfterPassingLevelClimbTo
import xyz.easiersaid.twr.protocol.AfterPassingLevelDescendTo
import xyz.easiersaid.twr.protocol.AirTaxiTo
import xyz.easiersaid.twr.protocol.AvoidLevel
import xyz.easiersaid.twr.protocol.BreakOff
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ClearedLowApproach
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ClearedToEnterControlZone
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
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
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.FollowTraffic
import xyz.easiersaid.twr.protocol.GiveWayToTraffic
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.HoldAt
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.HoldPositionCancelTakeoff
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.IncreaseSpeedTo
import xyz.easiersaid.twr.protocol.InterceptLocaliser
import xyz.easiersaid.twr.protocol.JoinAirway
import xyz.easiersaid.twr.protocol.JoinCircuit
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
import xyz.easiersaid.twr.protocol.ReportIntentions
import xyz.easiersaid.twr.protocol.ReportTrafficInSight
import xyz.easiersaid.twr.protocol.ReportWhen
import xyz.easiersaid.twr.protocol.ResumeNormalSpeed
import xyz.easiersaid.twr.protocol.ResumeOwnNavigation
import xyz.easiersaid.twr.protocol.RouteAsFiled
import xyz.easiersaid.twr.protocol.RunwayInUseAdvisory
import xyz.easiersaid.twr.protocol.SetPressure
import xyz.easiersaid.twr.protocol.SetSquawk
import xyz.easiersaid.twr.protocol.SpecialVfrClearance
import xyz.easiersaid.twr.protocol.SquawkIdent
import xyz.easiersaid.twr.protocol.SquawkNormal
import xyz.easiersaid.twr.protocol.SquawkStandby
import xyz.easiersaid.twr.protocol.StartupApproved
import xyz.easiersaid.twr.protocol.StopClimbAt
import xyz.easiersaid.twr.protocol.StopDescentAt
import xyz.easiersaid.twr.protocol.StopImmediately
import xyz.easiersaid.twr.protocol.StopSquawk
import xyz.easiersaid.twr.protocol.StopTurn
import xyz.easiersaid.twr.protocol.TakeoffImmediatelyOrHoldShort
import xyz.easiersaid.twr.protocol.TakeoffImmediatelyOrVacateRunway
import xyz.easiersaid.twr.protocol.TaxiIntoHoldingBay
import xyz.easiersaid.twr.protocol.TaxiTo
import xyz.easiersaid.twr.protocol.TaxiViaRunway
import xyz.easiersaid.twr.protocol.TaxiWithCaution
import xyz.easiersaid.twr.protocol.TransitionLevelIssuance
import xyz.easiersaid.twr.protocol.TurnBase
import xyz.easiersaid.twr.protocol.TurnByDegrees
import xyz.easiersaid.twr.protocol.TurnHeading
import xyz.easiersaid.twr.protocol.VacateRunway
import xyz.easiersaid.twr.protocol.WhenAbleProceedDirect
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ApproachInstruction
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.AvoidArea
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.CancelClearance
import xyz.easiersaid.twr.protocol.Clearance
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Disregard
import xyz.easiersaid.twr.protocol.EmergencyInstruction
import xyz.easiersaid.twr.protocol.FrequencyInstruction
import xyz.easiersaid.twr.protocol.GroundInstruction
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.LevelInstruction
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.ReadbackElement
import xyz.easiersaid.twr.protocol.RemainOutsideControlledAirspace
import xyz.easiersaid.twr.protocol.ReportInstruction
import xyz.easiersaid.twr.protocol.RouteInstruction
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.RunwayInstruction
import xyz.easiersaid.twr.protocol.SequencingInstruction
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimpleElement
import xyz.easiersaid.twr.protocol.SpeedInstruction
import xyz.easiersaid.twr.protocol.SurveillanceInstruction
import xyz.easiersaid.twr.protocol.VectorInstruction

/** Default 1 Hz physics-integration cadence. */
val PHYSICS_TICK_INTERVAL: SimDuration = SimDuration.ofMillis(1000)

/** Default 500 ms controller decision cadence. */
val CONTROLLER_CYCLE_INTERVAL: SimDuration = SimDuration.ofMillis(500)

/**
 * The engine's one and only state transition.
 *
 * Pure: given the same `(state, event)` pair, always returns the same
 * `(state', emissions)`. Never touches the event queue — emissions are
 * returned as a list and the driver enqueues them.
 *
 * Determinism contract:
 *   - Emitted events are assigned monotonic [SimEvent.seq] values drawn
 *     from [SimState.seq] and returned pre-sorted by `(time, source, seq)`.
 *   - [SimState.now] advances to the event's time (events never fire in
 *     the past).
 *   - Anything non-deterministic must draw from [SimState.rng] and thread
 *     the new generator back into state.
 */
fun step(state: SimState, event: SimEvent): Pair<SimState, List<SimEvent>> {
    require(event.time >= state.now) {
        "event from the past: event.time=${event.time.millis} < state.now=${state.now.millis}"
    }
    val atTime = state.copy(now = event.time)

    return when (event) {
        is SimEvent.PhysicsTick -> handlePhysicsTick(atTime, event)
        is SimEvent.PilotDecisionTick -> handlePilotTick(atTime, event)
        is SimEvent.ControllerCycle -> handleControllerTick(atTime, event)
        is SimEvent.Spawn -> handleSpawn(atTime, event)
        is SimEvent.TransmissionStart -> handleTransmissionStart(atTime, event)
        is SimEvent.TransmissionEnd -> handleTransmissionEnd(atTime, event)
        is SimEvent.PilotProcessingComplete -> handlePilotProcessingComplete(atTime, event)
    }
}

private fun handlePhysicsTick(
    state: SimState,
    event: SimEvent.PhysicsTick,
): Pair<SimState, List<SimEvent>> {
    val dtSeconds = PHYSICS_TICK_INTERVAL.millis / MS_PER_SECOND
    val advanced = state.aircraft.entries.fold(
        LinkedHashMap<AircraftId, AircraftState>(state.aircraft.size),
    ) { acc, (id, ac) ->
        acc.apply { put(id, advanceKinematics(ac, state.worldIndex, dtSeconds)) }
    }
    val next = SimEvent.PhysicsTick(event.time + PHYSICS_TICK_INTERVAL)
    return state.copy(aircraft = advanced).emit(listOf(next))
}

/** Earliest moment at or after [earliest] when [frequency] is clear of in-flight transmissions. */
private fun pilotFrequencyFreeFrom(
    state: SimState,
    frequency: xyz.easiersaid.twr.protocol.Frequency,
    earliest: xyz.easiersaid.twr.protocol.SimTime,
): xyz.easiersaid.twr.protocol.SimTime {
    val blocking = state.inFlightTransmissions.values
        .filter { it.frequency == frequency && it.endsAt > earliest }
        .maxByOrNull { it.endsAt.millis }
    return blocking?.endsAt ?: earliest
}

@Suppress("NestedBlockDepth")
private fun handlePilotTick(
    state: SimState,
    event: SimEvent.PilotDecisionTick,
): Pair<SimState, List<SimEvent>> {
    val ac = state.aircraft[event.aircraftId]
        ?: return state to emptyList()

    // One pilot, one brain, one decision.
    //
    // The pilot reads only PilotInput — own kinematic state, world geometry,
    // and the clock. It does NOT see `state.beliefs` or `state.controllers`.
    // The previous lookup that read `state.beliefs[…].commitments[…].runway`
    // was a firewall leak (controller→pilot back-channel) and is gone. The
    // pilot's runway is on `mission.activeRunway`, populated by
    // `processInstruction` from each radio-derived runway source.
    //
    // pilotDecide returns Either<RoutingError, PilotOutput>. Routing errors
    // are data-integrity defects in the world or route planner — they should
    // not happen in a healthy run. We surface them as `error()` so the test
    // hard-fails with the actual cause rather than continuing with a silently
    // frozen aircraft and an indistinguishable "stationary" final state. The
    // previous swallow-and-freeze shape lost the error; the no-corners rule
    // says diagnostic loss is rot. When the typed diagnostic channel lands
    // (memory: feedback_diagnostics), this becomes a Writer/emitter call.
    val input = buildPilotInput(state, event.aircraftId) ?: return state to emptyList()
    val decision = pilotDecide(input).fold(
        ifLeft = { err -> error("Pilot routing error for ${event.aircraftId}: $err") },
        ifRight = { it },
    )

    // Apply intent to aircraft state.
    var updated = ac.copy(
        targetSpeedMps = decision.intent.targetSpeedMps,
        phase = decision.intent.phase,
        route = decision.intent.route,
        targetAltitudeM = decision.intent.targetAltitudeM,
    )

    // Update mission with report tracking. The controller's view of the
    // aircraft's intent is derived on the controller side from radio
    // transmissions and the flight strip — never copied through here.
    val updatedMission = decision.updatedMission
    if (updatedMission != null) {
        var mission: PilotMission = updatedMission
        val priorStep = mission.currentTask?.step
        // Mark the priorStep as "transmitted" if any transmission fired
        // this tick (the per-step first-tick transmission was for that
        // step). [stepTransmission] reads `lastTransmittedStep` so the
        // same transmission doesn't fire again on a later tick of the
        // same step.
        if (decision.transmissions.isNotEmpty() && priorStep != null) {
            mission = mission.copy(lastTransmittedStep = Some(priorStep))
        }
        for (tx in decision.transmissions) {
            mission = updateAfterTransmission(mission, tx)
        }
        // If a transmission completed the current step (e.g. Report(Ready)
        // marks REPORT_READY complete via updateAfterReport), refresh
        // stepEnteredAt so the new step starts a fresh phase-local timer.
        val newStep = mission.currentTask?.step
        if (newStep != null && newStep != priorStep) {
            mission = mission.copy(stepEnteredAt = state.now)
        }
        updated = updated.copy(pilotMission = mission)
    }

    // Transmit through the radio pipeline — symmetric with controller transmissions.
    var resultState = state
    val commEvents = mutableListOf<SimEvent>()
    if (decision.transmissions.isNotEmpty()) {
        val ctrl = resultState.controllers.values.firstOrNull { event.aircraftId in it.responsibilities }
        if (ctrl != null) {
            var txState = resultState
            var nextFreeAt = state.now
            for (tx in decision.transmissions) {
                val proposedStart = maxOf(nextFreeAt, pilotFrequencyFreeFrom(txState, ctrl.frequency, nextFreeAt))
                val utterance = Utterance.FromPilot(tx)
                val (withId, txId) = txState.mintTransmissionId()
                txState = withId
                val ift = InFlightTransmission(
                    id = txId,
                    speaker = SpeakerRef.Pilot(event.aircraftId),
                    receiver = ReceiverRef.Controller(ctrl.id),
                    frequency = ctrl.frequency,
                    utterance = utterance,
                    startedAt = proposedStart,
                    endsAt = proposedStart + utteranceDuration(utterance),
                )
                commEvents.add(SimEvent.TransmissionStart(time = ift.startedAt, transmission = ift))
                nextFreeAt = ift.endsAt
            }
            resultState = txState
        }
    }

    val aircraft = LinkedHashMap(resultState.aircraft).apply { put(event.aircraftId, updated) }
    val next = SimEvent.PilotDecisionTick(
        time = event.time + PilotConstants.PILOT_DECISION_INTERVAL,
        aircraftId = event.aircraftId,
    )
    return resultState.copy(aircraft = aircraft).emit(commEvents + next)
}

private fun handleControllerTick(
    state: SimState,
    event: SimEvent.ControllerCycle,
): Pair<SimState, List<SimEvent>> {
    // If the controller has been de-registered (e.g. responsibility transfer
    // cleared it in a later slice), drop the tick rather than fail loudly —
    // the queue is expected to carry stale self-schedules.
    state.controllers[event.controllerId] ?: return state to emptyList()

    val view = buildControllerView(state, event.controllerId)
    val prior = state.beliefs[event.controllerId] ?: BeliefState.EMPTY
    val decision = controllerDecide(view, prior, state.world)

    // Each message in the view corresponds to an inbox entry that must be
    // consumed exactly once. Clear the controller's inbox before applying
    // outputs so nothing double-delivers on a later cycle.
    val inboxCleared = state.copy(
        controllerInbox = state.controllerInbox - event.controllerId,
    )

    val (afterOutputs, commEvents) = applyControllerOutputs(
        inboxCleared,
        event.controllerId,
        decision.outputs,
    )
    val withBeliefs = afterOutputs.copy(
        beliefs = afterOutputs.beliefs + (event.controllerId to decision.updatedBeliefs),
    )

    val next = SimEvent.ControllerCycle(
        time = event.time + CONTROLLER_CYCLE_INTERVAL,
        controllerId = event.controllerId,
    )
    return withBeliefs.emit(commEvents + next)
}

private fun handleSpawn(
    state: SimState,
    event: SimEvent.Spawn,
): Pair<SimState, List<SimEvent>> {
    val ac = event.aircraft
    // Mirror SimState.initial's invariants — a Spawn event must satisfy the
    // same ones the smart constructor enforces. Without this, an event-time
    // Spawn could overwrite an existing aircraft id or land an aircraft
    // pointed at a waypoint outside the index (the kinematics layer would
    // then silently freeze it). Both are wiring defects, not states the
    // simulation should accept silently.
    if (ac.id in state.aircraft) {
        error("handleSpawn: duplicate aircraft id ${ac.id} — already in state.aircraft")
    }
    if (ac.positionPoint !in state.worldIndex.positions) {
        error(
            "handleSpawn: aircraft ${ac.id} positionPoint ${ac.positionPoint} " +
                "is not in worldIndex.positions; the kinematics layer would freeze it.",
        )
    }
    val aircraft = LinkedHashMap(state.aircraft).apply { put(ac.id, ac) }
    val firstPilotTick = SimEvent.PilotDecisionTick(event.time, ac.id)
    return state.copy(aircraft = aircraft).emit(listOf(firstPilotTick))
}

// ── Comms handlers ──────────────────────────────────────────────────

/**
 * Bring a new transmission on the air. Mark any overlapping in-flight
 * transmission on the same frequency (and the new one) as stepped-on; the
 * [SimEvent.TransmissionEnd] handler is where step-on becomes a delivery
 * decision.
 */
private fun handleTransmissionStart(
    state: SimState,
    event: SimEvent.TransmissionStart,
): Pair<SimState, List<SimEvent>> {
    val incoming = event.transmission
    val overlaps = state.inFlightTransmissions.values.filter { existing ->
        existing.frequency == incoming.frequency &&
            existing.startedAt <= incoming.endsAt &&
            incoming.startedAt <= existing.endsAt
    }
    val steppedOn = overlaps.isNotEmpty()
    val flippedExisting = overlaps.associate { it.id to it.copy(steppedOn = true) }

    val withIncoming = incoming.copy(steppedOn = steppedOn)
    val inFlight = state.inFlightTransmissions + flippedExisting + (withIncoming.id to withIncoming)

    val end = SimEvent.TransmissionEnd(time = withIncoming.endsAt, transmissionId = withIncoming.id)
    return state.copy(inFlightTransmissions = inFlight).emit(listOf(end))
}

/**
 * Finish a transmission: if nothing stepped on it, deliver to the receiver.
 *   - Pilot receiver ⇒ schedule [SimEvent.PilotProcessingComplete] after the
 *     cognitive delay.
 *   - Controller receiver ⇒ append to [SimState.controllerInbox]; the next
 *     [SimEvent.ControllerCycle] will surface it in the view.
 */
private fun handleTransmissionEnd(
    state: SimState,
    event: SimEvent.TransmissionEnd,
): Pair<SimState, List<SimEvent>> {
    val tx = state.inFlightTransmissions[event.transmissionId]
        ?: return state to emptyList()
    val withoutTx = state.copy(inFlightTransmissions = state.inFlightTransmissions - tx.id)

    if (tx.steppedOn) return withoutTx to emptyList()

    return when (val receiver = tx.receiver) {
        is ReceiverRef.Pilot -> {
            val processingAt = tx.endsAt + CommsConstants.PILOT_COGNITIVE_DELAY
            val completion = SimEvent.PilotProcessingComplete(
                time = processingAt,
                aircraftId = receiver.aircraftId,
                utterance = tx.utterance,
            )
            withoutTx.emit(listOf(completion))
        }
        is ReceiverRef.Controller -> {
            val msg = receivedMessageFrom(tx) ?: return withoutTx to emptyList()
            // Party-line broadcast: deliver to ALL controllers on this
            // frequency, not just the addressed receiver. Real radio works
            // this way — anyone on the freq hears the call, not just the
            // station being addressed. The "addressed" notion only governs
            // who is expected to respond, not who hears.
            //
            // Why this matters: the controller's belief fold ingests typed
            // events from radio observation. Without broadcast, a non-
            // responsible controller on the same freq would never see the
            // pilot's reports, and any cross-controller belief flow (e.g. a
            // GROUND controller knowing the aircraft is circuit traffic
            // because it overheard the pilot's Downwind to TOWER) would
            // require a sim-side fudge.
            //
            // For G0/G1 (LOWG GROUND + TOWER share 118.200), this is the
            // mechanism by which both controllers' belief states stay
            // synchronised on radio observations. For G2 (different freqs),
            // each freq has its own party-line — the receiving aerodrome's
            // controllers won't see the sending aerodrome's transmissions,
            // which is correct (they're on different radios).
            //
            // Side-effects: every controller on the freq accumulates events
            // for aircraft they don't own. `reconcileCommitments` only acts
            // on aircraft in `responsibilities`, so the extra belief state
            // is harmless. The architectural firewall test
            // `FirewallBeliefWriteTest` is unaffected — writes are still
            // gated by `Observe.kt`'s typed fold.
            val recipients = withoutTx.controllers.values
                .filter { it.frequency == tx.frequency }
                .map { it.id }
            val nextInbox = recipients.fold(withoutTx.controllerInbox) { acc, ctrlId ->
                acc + (ctrlId to (acc[ctrlId].orEmpty() + msg))
            }
            // If the delivered transmission is the pilot's [InitialContact],
            // mark the pilot's mission as contacted on the new frequency.
            // This is the moment the pilot is "actually talking to the new
            // controller" — earlier instants (TransmissionStart, scheduled
            // time, processing-complete) are too eager and let downstream
            // step transmissions fire while the InitialContact is still in
            // flight (causing step-on cascades on the same frequency).
            val pilotInitialContactSpeaker = (msg as? ReceivedMessage.Clear)?.let {
                if (it.transmission is InitialContact) it.aircraft else null
            }
            val withMissionAcked = if (pilotInitialContactSpeaker != null) {
                val ac = withoutTx.aircraft[pilotInitialContactSpeaker]
                val mission = ac?.pilotMission
                if (ac != null && mission != null && !mission.contactedOnFrequency) {
                    val updatedAc = ac.copy(
                        pilotMission = mission.copy(contactedOnFrequency = true),
                    )
                    withoutTx.copy(
                        aircraft = LinkedHashMap(withoutTx.aircraft).apply {
                            put(pilotInitialContactSpeaker, updatedAc)
                        },
                    )
                } else withoutTx
            } else withoutTx
            withMissionAcked.copy(controllerInbox = nextInbox) to emptyList()
        }
    }
}

private fun receivedMessageFrom(tx: InFlightTransmission): ReceivedMessage? {
    val utterance = tx.utterance as? Utterance.FromPilot ?: return null
    val speaker = tx.speaker as? SpeakerRef.Pilot ?: return null
    return ReceivedMessage.Clear(
        aircraft = speaker.aircraftId,
        transmission = utterance.transmission,
    )
}

/**
 * Pilot has finished processing a delivered controller transmission. Dispatch
 * by [ControllerOutput] variant: [ControllerOutput.Instruct] applies world
 * effects + processes the mission state + schedules readback;
 * [ControllerOutput.Respond] runs the pilot-side response handler from
 * Pass 3 (`processControllerResponse`) and schedules any returned
 * transmission (e.g. a corrected readback after [ReadbackCorrection]).
 */
private fun handlePilotProcessingComplete(
    state: SimState,
    event: SimEvent.PilotProcessingComplete,
): Pair<SimState, List<SimEvent>> {
    val ac = state.aircraft[event.aircraftId] ?: return state to emptyList()
    val fromController = event.utterance as? Utterance.FromController
        ?: return state to emptyList()
    return when (val output = fromController.output) {
        is ControllerOutput.Instruct -> handleInstructFromController(state, ac, output, event.time)
        is ControllerOutput.Respond -> handleRespondFromController(state, ac, output, event.time)
    }
}

private fun handleInstructFromController(
    state: SimState,
    ac: AircraftState,
    instruct: ControllerOutput.Instruct,
    eventTime: SimTime,
): Pair<SimState, List<SimEvent>> {
    // Resolve the receiving controller BEFORE applying the instruction. For
    // [ContactFrequency] the apply step transfers responsibility to a new role,
    // but the readback ("ground one-two-one-eight, ALPHA") is spoken on the
    // *current* frequency to the *current* controller — the pilot only switches
    // after they've read back. Looking up the responsible controller post-apply
    // would send the readback to the new (silent) role and free the old
    // frequency for the new controller to step on the pilot.
    val controller = responsibleController(state, ac.id)
        ?: return state to emptyList()
    var afterApply = applyPilotHeardInstruction(state, ac, instruct.instruction)
    // Cognitive pilot: update mission based on received instruction.
    val missionAc = afterApply.aircraft[ac.id]
    val priorMission = missionAc?.pilotMission
    if (missionAc != null && priorMission != null) {
        val updatedMission = processInstruction(instruct.instruction, priorMission, state.now, state.worldIndex)
        val updatedAc = missionAc.copy(pilotMission = updatedMission)
        afterApply = afterApply.copy(aircraft = LinkedHashMap(afterApply.aircraft).apply { put(ac.id, updatedAc) })
    }
    val readback = buildReadback(instruct.instruction).getOrNull()
        ?: return afterApply to emptyList()

    val utterance = Utterance.FromPilot(readback)
    val (withReadbackId, readbackTxId) = afterApply.mintTransmissionId()
    // Listen-before-talk: the readback must not start while the controller
    // (or anyone else) is still transmitting on the same frequency. Without
    // this, a ReadBackCorrect queued behind the original instruction will
    // overlap the readback, causing both to be stepped-on and the readback
    // to never reach the controller.
    val earliestReadback = eventTime + CommsConstants.PILOT_READBACK_PREP
    val readbackStartAt = maxOf(
        earliestReadback,
        pilotFrequencyFreeFrom(withReadbackId, controller.frequency, earliestReadback),
    )
    val readbackTx = InFlightTransmission(
        id = readbackTxId,
        speaker = SpeakerRef.Pilot(ac.id),
        receiver = ReceiverRef.Controller(controller.id),
        frequency = controller.frequency,
        utterance = utterance,
        startedAt = readbackStartAt,
        endsAt = readbackStartAt + utteranceDuration(utterance),
    )
    val readbackStart = SimEvent.TransmissionStart(time = readbackTx.startedAt, transmission = readbackTx)

    // After a ContactFrequency handoff the pilot switches radios and makes
    // an initial call on the new frequency. That InitialContact is what lets
    // the new controller mark the aircraft as [commitment.contacted]; without
    // it, rules guarded by [ContactEstablished] (e.g. DEP-LUAW) can never
    // fire because the new controller never hears from the pilot directly —
    // the readback goes to the *old* controller on the *old* frequency.
    val cf = instruct.instruction as? ContactFrequency
    val newController = cf?.let { responsibleController(withReadbackId, ac.id) }
        ?.takeIf { it.id != controller.id }
    val (afterIc, icEvents) = if (cf != null && newController != null) {
        val (withIcId, icTxId) = withReadbackId.mintTransmissionId()
        val icUtterance = Utterance.FromPilot(InitialContact(stationCalled = cf.role))
        val icStartAt = readbackTx.endsAt + CommsConstants.PILOT_FREQ_SWITCH_DELAY
        val icTx = InFlightTransmission(
            id = icTxId,
            speaker = SpeakerRef.Pilot(ac.id),
            receiver = ReceiverRef.Controller(newController.id),
            frequency = newController.frequency,
            utterance = icUtterance,
            startedAt = icStartAt,
            endsAt = icStartAt + utteranceDuration(icUtterance),
        )
        withIcId to listOf(SimEvent.TransmissionStart(time = icTx.startedAt, transmission = icTx))
    } else withReadbackId to emptyList()
    return afterIc.emit(listOf(readbackStart) + icEvents)
}

/**
 * Pass 3 — pilot reaction to a [ControllerOutput.Respond]. Calls the
 * pilot-cognitive [processControllerResponse] and schedules the returned
 * transmission (if any) as a fresh [InFlightTransmission] on the current
 * frequency. Listen-before-talk and `PILOT_READBACK_PREP` mirror the
 * Instruct path's discipline.
 *
 * Today only [ReadbackCorrection] produces a non-`None` transmission
 * (a corrected readback for `correction.correct`); the other 11
 * `ControllerResponse` leaves are cognitive-only and emit nothing.
 */
private fun handleRespondFromController(
    state: SimState,
    ac: AircraftState,
    respond: ControllerOutput.Respond,
    eventTime: SimTime,
): Pair<SimState, List<SimEvent>> {
    val controller = responsibleController(state, ac.id)
        ?: return state to emptyList()
    val priorMission = ac.pilotMission ?: return state to emptyList()
    val reaction = processControllerResponse(respond.response, priorMission)

    // Functional update of mission: pilot's planning state may have changed
    // (today no Respond leaf changes it, but the contract is in place).
    val updatedAc = ac.copy(pilotMission = reaction.mission)
    val afterMission = state.copy(
        aircraft = LinkedHashMap(state.aircraft).apply { put(ac.id, updatedAc) },
    )

    // No transmission to schedule — pilot reaction was silent.
    val tx = reaction.transmission.getOrNull() ?: return afterMission to emptyList()

    // Schedule the pilot-side transmission (e.g. corrected readback).
    val (withTxId, txId) = afterMission.mintTransmissionId()
    val utterance = Utterance.FromPilot(tx)
    val earliestStart = eventTime + CommsConstants.PILOT_READBACK_PREP
    val startAt = maxOf(
        earliestStart,
        pilotFrequencyFreeFrom(withTxId, controller.frequency, earliestStart),
    )
    val inflight = InFlightTransmission(
        id = txId,
        speaker = SpeakerRef.Pilot(ac.id),
        receiver = ReceiverRef.Controller(controller.id),
        frequency = controller.frequency,
        utterance = utterance,
        startedAt = startAt,
        endsAt = startAt + utteranceDuration(utterance),
    )
    return withTxId.emit(listOf(SimEvent.TransmissionStart(time = inflight.startedAt, transmission = inflight)))
}

/**
 * Apply the world-side effect of an instruction the pilot has just heard.
 *
 * Instructions with a sim-side effect:
 *   - [TaxiTo] (4d) — writes a ground route.
 *   - [LineUpAndWait] (4e-A) — writes a short ground route from the current
 *     holding short to the runway threshold; the pilot enters [PilotPhase.LinedUp]
 *     on arrival, awaiting takeoff clearance.
 *   - [ClearedForTakeoff] (4e-A) — writes a departure route (upwind → crosswind)
 *     and transitions the pilot to [PilotPhase.TakeoffRoll].
 *   - [AfterLandingVacateVia] (4e-B) — writes a taxi route from the exit point
 *     toward runway edge; pilot transitions to [PilotPhase.Vacating].
 *   - [ContactFrequency] (4e-A) — transfers responsibility for [ac] from the
 *     current controller to the controller at the target role / frequency.
 *
 * **Per-leaf exhaustive over [AtcInstruction].** Every concrete leaf is matched
 * explicitly — never via a sealed-category arm (`is GroundInstruction -> state`).
 * Category absorption is the diamond-hierarchy hazard: many leaves implement
 * multiple sealed sub-interfaces, so the first matching arm wins by source
 * order, and a new leaf added to one of the absorbed categories silently slides
 * through with no-op behaviour. ExhaustivenessTest (pilot/jvmTest) prevents
 * regression to category-arm absorption. Adding a new [AtcInstruction] subtype
 * here is a compile error.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod") // ~98-arm exhaustive when over the AtcInstruction sealed hierarchy
private fun applyPilotHeardInstruction(
    state: SimState,
    ac: AircraftState,
    instruction: AtcInstruction,
): SimState = when (instruction) {
    // Leaf types with sim-side effects.
    is TaxiTo -> applyTaxiTo(state, ac, instruction)
    is LineUpAndWait -> applyLineUpAndWait(state, ac, instruction)
    is ClearedForTakeoff -> applyClearedForTakeoff(state, ac, instruction)
    is AfterLandingVacateVia -> applyAfterLandingVacateVia(state, ac, instruction)
    is BacktrackRunway -> applyBacktrackRunway(state, ac, instruction)
    is ContactFrequency -> applyContactFrequency(state, ac, instruction)
    // No-op leaves — pilot acknowledges, sim world does not change.
    is AfterPassingLevelClimbTo -> state
    is AfterPassingLevelDescendTo -> state
    is AirTaxiTo -> state
    is AvoidArea -> state
    is AvoidLevel -> state
    is BreakOff -> state
    is CancelClearance -> state
    is ClearedApproach -> state
    is ClearedLowApproach -> state
    is ClearedTo -> state
    is ClearedToEnterControlZone -> state
    is ClearedToLand -> state
    is ClearedTouchAndGo -> state
    is ClearedVisualApproach -> state
    is ClimbTo -> state
    is CommenceApproachAt -> state
    is ConditionalClearance -> state
    is ConfirmSquawk -> state
    is ContinueApproach -> state
    is ContinuePresentHeading -> state
    is CrossRunway -> state
    is DescendTo -> state
    is DescendWhenReady -> state
    is Disregard -> state
    is DivertTo -> state
    is ExpediteClimb -> state
    is ExpediteDescend -> state
    is ExpediteTaxi -> state
    is ExtendDownwind -> state
    is FlyHeading -> state
    is FollowTraffic -> state
    is GiveWayToTraffic -> state
    is GoAround -> state
    is HoldAt -> state
    is HoldPosition -> state
    is HoldPositionCancelTakeoff -> state
    is HoldShortOf -> state
    is IncreaseSpeedTo -> state
    is InterceptLocaliser -> state
    is JoinAirway -> state
    is JoinCircuit -> state
    is LeaveHoldProceedDirect -> state
    is MaintainAltitudeUntilEstablished -> state
    is MaintainAtOrAbove -> state
    is MaintainAtOrBelow -> state
    is MaintainLevel -> state
    is MaintainSpeed -> state
    is MaintainVisualSeparation -> state
    is MakeAnotherCircuit -> state
    is MakeLongApproach -> state
    is MakeShortApproach -> state
    is MinimumCleanSpeed -> state
    is MonitorFrequency -> state
    is NumberInSequence -> state
    is Orbit -> state
    is ProceedDirect -> state
    is PushbackApproved -> state
    is PushbackFace -> state
    is ReduceSpeedTo -> state
    is ReduceTaxiSpeed -> state
    is RejoinSidAt -> state
    is RemainOutsideControlledAirspace -> state
    is ReportIntentions -> state
    is ReportTrafficInSight -> state
    is ReportWhen -> state
    is ResumeNormalSpeed -> state
    is ResumeOwnNavigation -> state
    is RouteAsFiled -> state
    is RunwayInUseAdvisory -> state
    is SetPressure -> state
    is SetSquawk -> state
    is SpecialVfrClearance -> state
    is SquawkIdent -> state
    is SquawkNormal -> state
    is SquawkStandby -> state
    is StartupApproved -> state
    is StopClimbAt -> state
    is StopDescentAt -> state
    is StopImmediately -> state
    is StopSquawk -> state
    is StopTurn -> state
    is TakeoffImmediatelyOrHoldShort -> state
    is TakeoffImmediatelyOrVacateRunway -> state
    is TaxiIntoHoldingBay -> state
    is TaxiViaRunway -> state
    is TaxiWithCaution -> state
    is TransitionLevelIssuance -> state
    is TurnBase -> state
    is TurnByDegrees -> state
    is TurnHeading -> state
    is VacateRunway -> state
    is WhenAbleProceedDirect -> state
}

private fun applyTaxiTo(state: SimState, ac: AircraftState, instruction: TaxiTo): SimState {
    val path = instruction.via + instruction.destination
    val waypoints = NonEmptyList(path.first(), path.drop(1))
    val arrivalPhase = deriveArrivalPhase(state.worldIndex, instruction.destination)
    val updated = ac.copy(route = PilotRoute.Ground(waypoints = waypoints, arrivalPhase = arrivalPhase))
    val aircraft = LinkedHashMap(state.aircraft).apply { put(ac.id, updated) }
    return state.copy(aircraft = aircraft)
}

/**
 * "Line up" is a ground move from the current holding short to the nearest
 * runway threshold point. The pilot's ground-route follower will snap to the
 * threshold and settle in [PilotPhase.LinedUp] on arrival — then wait.
 */
private fun applyLineUpAndWait(
    state: SimState,
    ac: AircraftState,
    instruction: LineUpAndWait,
): SimState {
    val threshold = runwayThreshold(state.world, instruction.runway) ?: return state
    val waypoints = NonEmptyList(threshold, emptyList())
    val route = PilotRoute.Ground(waypoints = waypoints, arrivalPhase = PilotPhase.LinedUp)
    val updated = ac.copy(route = route)
    val aircraft = LinkedHashMap(state.aircraft).apply { put(ac.id, updated) }
    return state.copy(aircraft = aircraft)
}

/**
 * Build a departure route from the active runway through upwind and crosswind
 * circuit points. Used by [applyClearedForTakeoff] (initial takeoff).
 *
 * Delegates to [buildVisualDepartureRoute] in the route planner. Returns null
 * on any routing error (backward-compatible with the instruction-effect layer).
 */
internal fun buildDepartureRoute(
    world: xyz.easiersaid.twr.core.world.AviationWorld,
    runwayId: RunwayId,
): PilotRoute.Airborne? = buildVisualDepartureRoute(runwayId, world).getOrNull()

/**
 * "Cleared for takeoff" at the threshold: switch to a departure route and
 * transition to [PilotPhase.TakeoffRoll].
 */
private fun applyClearedForTakeoff(
    state: SimState,
    ac: AircraftState,
    instruction: ClearedForTakeoff,
): SimState {
    val route = buildDepartureRoute(state.world, instruction.runway)
        ?: return state
    val updated = ac.copy(
        phase = PilotPhase.TakeoffRoll,
        route = route,
        targetSpeedMps = PilotConstants.CLIMB_SPEED_MPS,
        targetAltitudeM = CIRCUIT_ALTITUDE_M,
    )
    val aircraft = LinkedHashMap(state.aircraft).apply { put(ac.id, updated) }
    return state.copy(aircraft = aircraft)
}

/**
 * "Backtrack runway": pilot taxis along the runway to a holding point that
 * exits onto a taxiway. For G0 / single-runway airfields the controller may
 * issue [BacktrackRunway] in lieu of [AfterLandingVacateVia] when there is
 * no specific exit taxiway near the landing position. The sim treats both
 * the same way: write a ground route off the runway. The destination is
 * either [BacktrackRunway.vacateAt] when explicit, or the nearest holding
 * point for the runway, or the runway threshold itself as a degenerate
 * fallback.
 */
private fun applyBacktrackRunway(
    state: SimState,
    ac: AircraftState,
    instruction: BacktrackRunway,
): SimState {
    val destination = instruction.vacateAt
        ?: state.worldIndex.holdingPointsByRunway[instruction.runway]?.firstOrNull()
        ?: return state
    if (state.worldIndex.positions[destination] == null) return state
    val waypoints = NonEmptyList(destination, emptyList())
    val route = PilotRoute.Ground(waypoints = waypoints, arrivalPhase = PilotPhase.ClearOfRunway)
    val updated = ac.copy(route = route)
    val aircraft = LinkedHashMap(state.aircraft).apply { put(ac.id, updated) }
    return state.copy(aircraft = aircraft)
}

/**
 * "After landing, vacate via [exit]": the pilot was in [PilotPhase.LandingRoll],
 * stopped on the runway. The sim writes a fresh ground route to the assigned
 * runway exit; the pilot agent sees the new route and transitions to
 * [PilotPhase.Vacating], then settles into [PilotPhase.ClearOfRunway] on
 * arrival. Ground will pick up the aircraft from there with a separate taxi
 * instruction.
 *
 * No-op on unknown exit: a malformed instruction doesn't wedge the runway —
 * the aircraft simply stays in LandingRoll until the controller reissues.
 */
private fun applyAfterLandingVacateVia(
    state: SimState,
    ac: AircraftState,
    instruction: AfterLandingVacateVia,
): SimState {
    if (state.worldIndex.positions[instruction.exit] == null) return state
    val waypoints = NonEmptyList(instruction.exit, emptyList())
    val route = PilotRoute.Ground(waypoints = waypoints, arrivalPhase = PilotPhase.ClearOfRunway)
    val updated = ac.copy(route = route)
    val aircraft = LinkedHashMap(state.aircraft).apply { put(ac.id, updated) }
    return state.copy(aircraft = aircraft)
}

/**
 * Apply a `ContactFrequency` instruction: transfer responsibility for [ac] from
 * the current owning controller to the controller at the same aerodrome with
 * the target role.
 *
 * Both unhappy branches are loud failures. They correspond to invariant
 * violations the simulation cannot recover from gracefully:
 *
 *  - **No current owner.** A `ContactFrequency` is being processed for an
 *    aircraft that no controller holds. Either responsibility was stripped
 *    earlier without a corresponding handoff, or the inbox was fed an
 *    instruction for an unowned aircraft. Either way it is a wiring defect.
 *  - **No target controller.** The issuing controller asked to hand over to a
 *    role that is not staffed at this aerodrome. That is a world-config
 *    defect — a procedure rule should never emit `ContactFrequency(role=X)`
 *    when `X` has no controller modelled for the source aerodrome.
 *
 * The previous lenient form returned `state` on either branch, silently
 * leaving the aircraft on the old controller while the pilot dutifully
 * switched frequencies — a wedge that surfaced only as "the simulation hangs."
 * The no-corners rule applies: surface the defect at the call site.
 */
private fun applyContactFrequency(
    state: SimState,
    ac: AircraftState,
    instruction: ContactFrequency,
): SimState {
    val current = state.controllers.values.firstOrNull { ac.id in it.responsibilities }
        ?: error(
            "applyContactFrequency: no controller currently holds ${ac.id}; " +
                "instruction ${instruction.role} can't be processed. " +
                "This is a wiring defect — responsibility was stripped without a handoff.",
        )
    val target = state.controllers.values
        .firstOrNull { it.aerodromeId == current.aerodromeId && it.role == instruction.role }
        ?: error(
            "applyContactFrequency: target ${instruction.role} not staffed at ${current.aerodromeId}; " +
                "current owner is ${current.id}. " +
                "This is a world-config defect — the issuing rule should not emit ContactFrequency " +
                "for a role that has no controller modelled at this aerodrome.",
        )
    val controllersMap = LinkedHashMap(state.controllers)
    controllersMap[current.id] = current.copy(responsibilities = current.responsibilities - ac.id)
    controllersMap[target.id] = target.copy(responsibilities = target.responsibilities + ac.id)
    return state.copy(controllers = controllersMap)
}

private fun runwayThreshold(
    world: xyz.easiersaid.twr.core.world.AviationWorld,
    runwayId: RunwayId,
): PointId? {
    val aerodrome = world.aerodromes.values.firstOrNull { it.runways.containsKey(runwayId) } ?: return null
    return aerodrome.runways[runwayId]?.threshold
}

private fun deriveArrivalPhase(worldIndex: WorldIndex, destination: PointId): PilotPhase {
    val entities = worldIndex.entitiesByPoint[destination] ?: emptySet()
    if (entities.any { it is EntityRef.StandRef }) return PilotPhase.Parked
    return PilotPhase.HoldingShort
}

/**
 * Advance one aircraft by [dtSeconds] toward the first waypoint of its current
 * route. Speed is set to its target (instantaneous response — aerodynamic
 * acceleration lands with per-aircraft-type performance) and altitude is
 * integrated toward [AircraftState.targetAltitudeM] at [PilotConstants.CLIMB_RATE_MPS].
 *
 * Route-following is identical for [PilotRoute.Ground] and [PilotRoute.Airborne]:
 * both supply a [PointId] sequence; the pilot decides which phase goes with
 * each waypoint. No route ⇒ the aircraft holds position; altitude still
 * tracks its target so the airborne arrival phase (e.g. [PilotPhase.Crosswind])
 * can settle at circuit height.
 *
 * Uses [StrictMath.hypot] for cross-JVM bit-reproducibility — a small thing
 * today, but necessary for byte-identical replay later.
 */
private fun advanceKinematics(
    ac: AircraftState,
    worldIndex: WorldIndex,
    dtSeconds: Double,
): AircraftState {
    val speed = ac.targetSpeedMps
    val headPoint = when (val r = ac.route) {
        is PilotRoute.Ground -> r.waypoints.head
        is PilotRoute.Airborne -> r.waypoints.head
        PilotRoute.None -> null
    }
    val altitude = advanceAltitude(ac.altitudeM, ac.targetAltitudeM, dtSeconds)

    if (speed <= 0.0 || headPoint == null) {
        return ac.copy(speedMps = speed, altitudeM = altitude)
    }

    val headPos = worldIndex.positions[headPoint]
        ?: return ac.copy(speedMps = speed, altitudeM = altitude)

    val dx = headPos.xMeters - ac.position.xMeters
    val dy = headPos.yMeters - ac.position.yMeters
    val distance = StrictMath.hypot(dx, dy)
    val step = speed * dtSeconds

    val snapped = distance <= step || distance == 0.0
    val newPos = if (snapped) {
        ac.position.copyXY(headPos.xMeters, headPos.yMeters)
    } else {
        val ratio = step / distance
        ac.position.copyXY(
            ac.position.xMeters + dx * ratio,
            ac.position.yMeters + dy * ratio,
        )
    }
    // Advance graph-level position whenever the aircraft is within
    // [PilotConstants.WAYPOINT_RADIUS_M] of the head waypoint — the same radius
    // the pilot uses to pop waypoints. Aligning the two guarantees
    // `positionPoint` never skips a waypoint the pilot visits, so the
    // controller's point-indexed guards (AtHoldingPoint, AtStand, OnRunway,
    // OnCircuitLeg) see every leg. Without this, a pilot popping at dist ≤ 5 m
    // while physics hasn't yet snapped (dist ≤ step, typically 33 m) leaves
    // positionPoint pinned to the previous waypoint forever.
    val ddx = headPos.xMeters - newPos.xMeters
    val ddy = headPos.yMeters - newPos.yMeters
    val distanceAfter = StrictMath.hypot(ddx, ddy)
    val newPositionPoint =
        if (distanceAfter <= PilotConstants.WAYPOINT_RADIUS_M) headPoint else ac.positionPoint
    return ac.copy(
        position = newPos,
        positionPoint = newPositionPoint,
        speedMps = speed,
        altitudeM = altitude,
    )
}

/**
 * Integrate altitude toward [target] at [PilotConstants.CLIMB_RATE_MPS]. A
 * simple constant-rate tracker is enough for 4e-A — vertical performance
 * curves live in the aircraft-type slice later.
 */
private fun advanceAltitude(current: Double, target: Double, dtSeconds: Double): Double {
    val delta = target - current
    if (delta == 0.0) return current
    val maxStep = PilotConstants.CLIMB_RATE_MPS * dtSeconds
    val step = if (delta > 0.0) minOf(delta, maxStep) else maxOf(delta, -maxStep)
    return current + step
}

private fun Position.copyXY(x: Double, y: Double): Position =
    copy(xMeters = x, yMeters = y)

private const val MS_PER_SECOND: Double = 1000.0

/**
 * Stamp emitted events with fresh sequence numbers, bump [SimState.seq], and
 * return them sorted by the canonical `(time, source, seq)` ordering.
 *
 * The fold here is the single place seq is advanced. All emission paths
 * funnel through it so the invariant holds.
 */
internal fun SimState.emit(events: List<SimEvent>): Pair<SimState, List<SimEvent>> {
    if (events.isEmpty()) return this to emptyList()
    val (newSeq, stamped) = events.fold(seq to emptyList<SimEvent>()) { (s, acc), ev ->
        val nextSeq = s + 1
        nextSeq to (acc + ev.withSeq(nextSeq))
    }
    val sorted = stamped.sortedWith(EVENT_ORDER)
    return copy(seq = newSeq) to sorted
}

