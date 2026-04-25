@file:Suppress("TooManyFunctions") // step handlers + radio helpers

package xyz.easiersaid.twr.sim

import arrow.core.NonEmptyList
import arrow.core.getOrElse
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.ReceivedMessage
import xyz.easiersaid.twr.controller.controllerDecide
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeInstruction
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ApproachInstruction
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.Clearance
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.EmergencyInstruction
import xyz.easiersaid.twr.protocol.FrequencyInstruction
import xyz.easiersaid.twr.protocol.GroundInstruction
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.LevelInstruction
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.ReadbackElement
import xyz.easiersaid.twr.protocol.ReportInstruction
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RouteInstruction
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.RunwayInstruction
import xyz.easiersaid.twr.protocol.SequencingInstruction
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimpleElement
import xyz.easiersaid.twr.protocol.SpeedInstruction
import xyz.easiersaid.twr.protocol.SurveillanceInstruction
import xyz.easiersaid.twr.protocol.TaxiTo
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
    // Derive active runway from beliefs (any controller that has a commitment for this aircraft).
    val runway = state.beliefs.values
        .flatMap { it.commitments.entries }
        .firstOrNull { it.key == ac.id }?.value?.runway
    val decision = unifiedPilotDecide(
        aircraft = ac,
        worldIndex = state.worldIndex,
        now = state.now,
        world = state.world,
        activeRunway = runway,
        airspaceTriggers = state.airspaceTriggers,
        simStateForTriggers = state,
    )

    // Apply intent to aircraft state.
    var updated = ac.copy(
        targetSpeedMps = decision.intent.targetSpeedMps,
        phase = decision.intent.phase,
        route = decision.intent.route,
        targetAltitudeM = decision.intent.targetAltitudeM,
    )

    // Pilot self-initiated frequency change (G1.5/G1.6 bridge). When the
    // cognitive predicate fires, transfer responsibility to the trigger's
    // target controller AND reset the mission's `contactedOnFrequency` flag
    // so the next decide cycle's `stepTransmission` emits a fresh
    // [InitialContact] on the new frequency. The transmission is routed by
    // the regular pilot-transmission path below — by then, the new
    // controller holds the responsibility.
    var resultState = state
    val mutatedMissionFromContact: PilotMission? = decision.selfInitiatedContact.fold(
        ifEmpty = { null },
        ifSome = { trigger ->
            resultState = transferResponsibility(
                state = resultState,
                ac = ac.id,
                toAerodrome = trigger.targetAerodrome,
                toRole = trigger.targetRole,
            ).getOrElse {
                // The cognitive predicate gated on the trigger's target
                // existing in the simulation's controllers; if we hit
                // TargetUnresolved, the test fixture / trigger table is
                // misaligned with the controllers list. Loud fail.
                error(
                    "pilotInitiatedContact target unresolved: " +
                        "${trigger.targetAerodrome}/${trigger.targetRole} not in controllers. " +
                        "Trigger table and controllers must agree.",
                )
            }
            // Reset contactedOnFrequency so the next stepTransmission emits
            // InitialContact on the new frequency (the existing cognitive
            // emission path handles the actual InitialContact construction).
            decision.updatedMission?.copy(contactedOnFrequency = false)
        },
    )

    // Update mission (with report tracking) and derive controller-visible pilotGoal.
    val rawMission: PilotMission? = mutatedMissionFromContact ?: decision.updatedMission
    if (rawMission != null) {
        var mission: PilotMission = rawMission
        for (tx in decision.transmissions) {
            mission = updateAfterTransmission(mission, tx)
        }
        updated = updated.copy(
            pilotMission = mission,
            pilotGoal = derivePilotGoal(mission),
        )
    }

    // Transmit through the radio pipeline — symmetric with controller transmissions.
    // After a pilot-initiated contact above, [resultState] reflects the new
    // controller holding the aircraft, so the routing-by-responsibility lookup
    // below picks the new (target) controller and the transmission lands on
    // the new frequency.
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
            val existing = withoutTx.controllerInbox[receiver.id].orEmpty()
            val nextInbox = withoutTx.controllerInbox + (receiver.id to (existing + msg))
            withoutTx.copy(controllerInbox = nextInbox) to emptyList()
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
 * Pilot has finished processing a delivered controller transmission. Apply
 * the effect to aircraft state (for 4d only [TaxiTo] has a world-side
 * effect), then schedule the readback transmission after a short prep delay
 * if the instruction demands one.
 */
private fun handlePilotProcessingComplete(
    state: SimState,
    event: SimEvent.PilotProcessingComplete,
): Pair<SimState, List<SimEvent>> {
    val ac = state.aircraft[event.aircraftId] ?: return state to emptyList()
    val fromController = event.utterance as? Utterance.FromController
        ?: return state to emptyList()
    val instruct = fromController.output as? ControllerOutput.Instruct
        ?: return state to emptyList()

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
    if (missionAc?.pilotMission != null) {
        val updatedMission = processInstruction(instruct.instruction, missionAc.pilotMission, state.now)
        val updatedAc = missionAc.copy(pilotMission = updatedMission)
        afterApply = afterApply.copy(aircraft = LinkedHashMap(afterApply.aircraft).apply { put(ac.id, updatedAc) })
    }
    val readback = buildReadback(instruct.instruction)
        ?: return afterApply to emptyList()

    val utterance = Utterance.FromPilot(readback)
    val (withReadbackId, readbackTxId) = afterApply.mintTransmissionId()
    // Listen-before-talk: the readback must not start while the controller
    // (or anyone else) is still transmitting on the same frequency. Without
    // this, a ReadBackCorrect queued behind the original instruction will
    // overlap the readback, causing both to be stepped-on and the readback
    // to never reach the controller.
    val earliestReadback = event.time + CommsConstants.PILOT_READBACK_PREP
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
 * **On the `else` branch.** [AtcInstruction] is a sealed hierarchy with ~60
 * data classes; a full exhaustive match is infeasible while most instruction
 * categories have no Phase-4 sim effect. The default is therefore an explicit
 * opt-in: the pilot still builds and transmits a readback via [buildReadback],
 * but the sim world does not change. Every new instruction *with* a sim-side
 * effect must be added to the [when] above — if you add a new concrete
 * instruction and forget to wire it, the pilot will correctly acknowledge it
 * but nothing will happen physically. That is the intended semantics until
 * each instruction family gets its own slice.
 */
private fun applyPilotHeardInstruction(
    state: SimState,
    ac: AircraftState,
    instruction: AtcInstruction,
): SimState = when (instruction) {
    // Leaf types with sim-side effects — matched first.
    is TaxiTo -> applyTaxiTo(state, ac, instruction)
    is LineUpAndWait -> applyLineUpAndWait(state, ac, instruction)
    is ClearedForTakeoff -> applyClearedForTakeoff(state, ac, instruction)
    is AfterLandingVacateVia -> applyAfterLandingVacateVia(state, ac, instruction)
    is ContactFrequency -> applyContactFrequency(state, ac, instruction)
    // Category interfaces — remaining members have no Phase-4 sim effect.
    // Leaf types with effects (above) are matched first; these catch the rest.
    is Clearance -> state
    is GroundInstruction -> state
    is RunwayInstruction -> state
    is RouteInstruction -> state
    is VectorInstruction -> state
    is LevelInstruction -> state
    is SpeedInstruction -> state
    is ApproachInstruction -> state
    is ReportInstruction -> state
    is FrequencyInstruction -> state
    is SurveillanceInstruction -> state
    is SequencingInstruction -> state
    is AerodromeInstruction -> state
    is EmergencyInstruction -> state
    // Uncategorised instructions (SetPressure, CancelClearance, Disregard, etc.)
    // that implement AtcInstruction directly without an intermediate interface.
    else -> state
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
    worldIndex: WorldIndex,
    runwayId: RunwayId,
): PilotRoute.Airborne? = buildVisualDepartureRoute(runwayId, world, worldIndex).getOrNull()

/**
 * "Cleared for takeoff" at the threshold: switch to a departure route and
 * transition to [PilotPhase.TakeoffRoll].
 */
private fun applyClearedForTakeoff(
    state: SimState,
    ac: AircraftState,
    instruction: ClearedForTakeoff,
): SimState {
    val route = buildDepartureRoute(state.world, state.worldIndex, instruction.runway)
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
 * Typed errors raised by [transferResponsibility].
 */
sealed interface TransferError {
    /** No controller at [aerodrome] holds the [role]; the strict transfer
     *  cannot resolve a target. Callers that legitimately want to release
     *  responsibility into the void use [releaseResponsibility] instead. */
    data class TargetUnresolved(
        val aerodrome: xyz.easiersaid.twr.protocol.AerodromeId,
        val role: RoleName,
    ) : TransferError
}

/**
 * Strict responsibility transfer. Removes [ac] from any controller currently
 * holding it (regardless of aerodrome — supports cross-aerodrome pilot-
 * initiated transfers) and adds [ac] to the controller at [toAerodrome] with
 * [toRole]. Returns [TransferError.TargetUnresolved] if no such controller
 * exists.
 *
 * For the explicit "drop into the void" case (e.g. LOWG → FIS where no FIS
 * controller is modelled), call [releaseResponsibility]. Distinguishing the
 * two is intentional: a typo'd `RoleName` should not silently strip
 * responsibility (round-3 impact-review finding). When the caller really
 * means "no target," they say so.
 */
internal fun transferResponsibility(
    state: SimState,
    ac: AircraftId,
    toAerodrome: xyz.easiersaid.twr.protocol.AerodromeId,
    toRole: RoleName,
): arrow.core.Either<TransferError, SimState> {
    val target = state.controllers.values
        .firstOrNull { it.aerodromeId == toAerodrome && it.role == toRole }
        ?: return arrow.core.Either.Left(TransferError.TargetUnresolved(toAerodrome, toRole))
    val controllersMap = LinkedHashMap(state.controllers)
    state.controllers.values.firstOrNull { ac in it.responsibilities }?.let { current ->
        controllersMap[current.id] = current.copy(responsibilities = current.responsibilities - ac)
    }
    controllersMap[target.id] = target.copy(responsibilities = target.responsibilities + ac)
    return arrow.core.Either.Right(state.copy(controllers = controllersMap))
}

/**
 * Explicit drop: removes [ac] from any controller currently holding it,
 * leaving the aircraft unmanaged. Used for the "release into the void" case
 * — e.g. LOWG TWR sending `ContactFrequency` to a FIS frequency on which no
 * controller is modelled.
 *
 * This is total: there is no failure case. The aircraft was either owned (now
 * released) or already unowned (no-op).
 */
internal fun releaseResponsibility(
    state: SimState,
    ac: AircraftId,
): SimState {
    val current = state.controllers.values.firstOrNull { ac in it.responsibilities }
        ?: return state
    val controllersMap = LinkedHashMap(state.controllers)
    controllersMap[current.id] = current.copy(responsibilities = current.responsibilities - ac)
    return state.copy(controllers = controllersMap)
}

/**
 * Roles for which an unresolved `ContactFrequency` target is interpreted
 * as "release the aircraft into the void" rather than an error. Currently
 * only [RoleName.AFIS] — the closest model for a flight-information-service
 * station that the simulation does not have a controller for (e.g. Wien
 * Information on the LOWG → LJMB outbound transit). All other roles are
 * "should resolve to a target controller"; failure to do so is a defect.
 */
private val RELEASABLE_ROLES: Set<RoleName> = setOf(RoleName.AFIS)

/**
 * Controller-initiated responsibility transfer triggered by a `ContactFrequency`
 * instruction. Same-aerodrome by construction: the target controller is at the
 * current owner's aerodrome with the requested role. If no target controller
 * exists for `(currentAerodrome, instruction.role)`:
 *  - If [instruction.role] is in [RELEASABLE_ROLES], release the aircraft
 *    explicitly (round-3 impact-review fix: was previously a silent fall-
 *    through that masked typo'd or stale-enum role values).
 *  - Otherwise, [error] — the controller asked for a target that isn't
 *    modelled and isn't a documented release intent.
 *
 * Returns the state unchanged if no controller currently holds [ac].
 */
private fun applyContactFrequency(
    state: SimState,
    ac: AircraftState,
    instruction: ContactFrequency,
): SimState {
    val current = state.controllers.values.firstOrNull { ac.id in it.responsibilities }
        ?: return state  // No current owner — defined no-op (no aircraft to transfer).
    return transferResponsibility(
        state = state,
        ac = ac.id,
        toAerodrome = current.aerodromeId,
        toRole = instruction.role,
    ).fold(
        ifLeft = { err ->
            if (instruction.role in RELEASABLE_ROLES) {
                releaseResponsibility(state, ac.id)
            } else {
                error(
                    "applyContactFrequency: target ${instruction.role} not resolved at " +
                        "${current.aerodromeId} and not in RELEASABLE_ROLES. " +
                        "Caller should target an existing role or use AFIS for a deliberate release. " +
                        "Underlying: $err",
                )
            }
        },
        ifRight = { it },
    )
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

private fun buildReadback(instruction: AtcInstruction): Readback? {
    val atoms = xyz.easiersaid.twr.controller.observe.requiredReadbackAtoms(instruction)
    if (atoms.isEmpty()) return null
    val elements = atoms.map<_, ReadbackElement> { SimpleElement(it) }
    return Readback(elements = elements)
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

