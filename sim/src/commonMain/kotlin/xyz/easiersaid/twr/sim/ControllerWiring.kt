package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.controller.RunwayObservation
import xyz.easiersaid.twr.controller.RunwayStatus
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime

/**
 * Anti-corruption layer between [SimState] (ground truth) and
 * [ControllerView] (what the controller is told).
 *
 * Split into two halves:
 *   - [buildControllerView] — projects a view from state for one controller.
 *   - [applyControllerOutputs] — turns the controller's outputs into comms
 *     events. Since slice 4d, no output mutates aircraft state directly:
 *     every [ControllerOutput] is packaged as a [SimEvent.TransmissionStart]
 *     so the effect of the instruction is only felt after the pilot has heard
 *     and processed it.
 *
 * Both sides are pure. The controller module owns its own beliefs; the sim
 * only threads the returned [xyz.easiersaid.twr.controller.observe.BeliefState]
 * forward through [SimState.beliefs].
 */

/**
 * Build the controller's view of the world at [SimState.now].
 *
 * Slice 4d scope: ground controllers only, weather/clearances empty. Received
 * messages are drained from [SimState.controllerInbox] for this controller —
 * the caller is expected to clear the inbox in the same fold iteration that
 * consumes the view so each message is seen exactly once.
 */
fun buildControllerView(state: SimState, controllerId: ControllerId): ControllerView {
    val spec = requireNotNull(state.controllers[controllerId]) {
        "Controller $controllerId not registered in SimState"
    }
    val observations = spec.responsibilities
        .mapNotNull { id -> state.aircraft[id]?.let { it.id to toObservation(it, state) } }
        .toMap()
    return ControllerView(
        time = state.now,
        controllerId = controllerId,
        role = spec.role,
        aerodromeId = spec.aerodromeId,
        responsibilities = spec.responsibilities,
        aircraft = observations,
        runways = deriveRunwayObservations(state, spec.aerodromeId),
        activeClearances = emptyMap(),
        receivedMessages = state.controllerInbox[controllerId].orEmpty(),
        weather = state.weatherByAerodrome[spec.aerodromeId],
        pendingInboundHandoffs = emptyList(),
        worldIndex = state.worldIndex,
    )
}

/**
 * Project one aircraft into the controller's observation.
 *
 * Populates altitude (metres → feet AGL) and groundSpeed (m/s → knots).
 * Heading and type description still null until the respective slices.
 */
private fun toObservation(ac: AircraftState, state: SimState): AircraftObservation {
    val entities = state.worldIndex.entitiesByPoint[ac.positionPoint] ?: emptySet()
    val groundSpeedKt = if (ac.speedMps > 0) {
        val kt = (ac.speedMps * 3600.0 / 1852.0).toInt()
        if (kt > 0) xyz.easiersaid.twr.protocol.Knots.unsafe(kt) else null
    } else null
    return AircraftObservation(
        id = ac.id,
        callsign = ac.callsign,
        position = ac.positionPoint,
        entities = entities,
        altitude = toAltitudeFeet(ac.altitudeM),
        speed = null,
        groundSpeed = groundSpeedKt,
        onGround = isGroundPhase(ac.phase),
        flightRules = null,
        pilotGoal = ac.pilotGoal,
        humanPiloted = ac.humanPiloted,
        typeDescription = null,
    )
}

private const val METRES_PER_FOOT: Double = 0.3048

private fun toAltitudeFeet(altitudeM: Double): Level.AltitudeFeet? {
    if (altitudeM <= 0.0) return null
    val feet = (altitudeM / METRES_PER_FOOT).toInt()
    return Level.AltitudeFeet.unsafe(feet.coerceAtLeast(0))
}

private fun isGroundPhase(phase: PilotPhase): Boolean = when (phase) {
    PilotPhase.AtStand, PilotPhase.Taxiing,
    PilotPhase.HoldingShort, PilotPhase.LinedUp,
    PilotPhase.TakeoffRoll, PilotPhase.Parked,
    PilotPhase.LandingRoll, PilotPhase.Vacating, PilotPhase.ClearOfRunway -> true
    PilotPhase.Climbing, PilotPhase.Crosswind,
    PilotPhase.Downwind, PilotPhase.Base, PilotPhase.Final -> false
}

private fun deriveRunwayObservations(
    state: SimState,
    aerodromeId: AerodromeId,
): Map<RunwayId, RunwayObservation> {
    val aerodrome = state.world.aerodromes[aerodromeId] ?: return emptyMap()
    return aerodrome.runways.keys.associateWith { rwyId ->
        val occupants = state.aircraft.values
            .filter { ac ->
                state.worldIndex.entitiesByPoint[ac.positionPoint]
                    ?.any { it is EntityRef.RunwayRef && it.id == rwyId } == true
            }
            .map { it.id }
            .toSet()
        val status = if (occupants.isEmpty()) RunwayStatus.CLEAR else RunwayStatus.OCCUPIED_DEPARTURE
        RunwayObservation(id = rwyId, status = status, occupants = occupants)
    }
}

/**
 * Package one controller's cycle outputs as comms emissions.
 *
 * Every output becomes a [SimEvent.TransmissionStart]: the speaker is the
 * controller, the listener is the targeted pilot, the frequency is the
 * controller's assigned [ControllerSpec.frequency]. The start time is
 * `view.time + reply latency` (per-role, from [CommsConstants]), giving the
 * realistic "controller sees pilot request / commitment → pauses → presses
 * PTT" gap. Duration comes from [utteranceDuration].
 *
 * Crucially, nothing in the ground-truth aircraft state changes here. The
 * effect of a [ControllerOutput.Instruct] is deferred until the pilot's
 * [SimEvent.PilotProcessingComplete] fires — see [Step].
 */
fun applyControllerOutputs(
    state: SimState,
    controllerId: ControllerId,
    outputs: List<ControllerOutput>,
): Pair<SimState, List<SimEvent>> {
    if (outputs.isEmpty()) return state to emptyList()
    val spec = state.controllers[controllerId] ?: return state to emptyList()
    val latency = CommsConstants.CONTROLLER_REPLY_LATENCY[spec.role]
        ?: CommsConstants.DEFAULT_CONTROLLER_REPLY_LATENCY
    val earliestStart = state.now + latency

    // Hold the PTT until the frequency is clear. A professional controller listens
    // before keying the mic; modelling that here avoids deterministic step-on
    // cascades (e.g., issuing a takeoff clearance while the pilot's line-up
    // readback is still finishing). Each output shifts the start time past the
    // previous transmission's end, so a burst of outputs in one cycle still
    // serialises on the air.
    val (finalState, _, starts) = outputs.fold(
        Triple(state, earliestStart, emptyList<SimEvent.TransmissionStart>())
    ) { (st, nextFreeAt, acc), out ->
        val proposedStart = maxOf(nextFreeAt, frequencyFreeFrom(st, spec.frequency, nextFreeAt))
        val (nextState, tx) = buildTransmission(st, spec, out, proposedStart)
            ?: return@fold Triple(st, nextFreeAt, acc)
        Triple(
            nextState,
            tx.endsAt, // subsequent outputs from the same cycle queue behind this one
            acc + SimEvent.TransmissionStart(time = tx.startedAt, transmission = tx),
        )
    }
    return finalState to starts
}

/**
 * Earliest moment [earliest] or later at which the controller can key the mic
 * on [frequency] without stepping on an already-scheduled transmission.
 *
 * Conservatively waits past any still-live transmission — i.e. any tx whose
 * `endsAt > earliest`, whether or not it is already on-air at `earliest`. The
 * previous version required `startedAt <= earliest`, which let the controller
 * key up at `earliest` even when a scheduled pilot transmission starting at
 * `earliest + δ` would still overlap our new utterance. That produced
 * avoidable step-ons; listening before transmitting is the whole point.
 *
 * Over-eager in one narrow case: if a scheduled tx starts and ends within a
 * larger gap between two blocking tx, we skip the gap. Fine for Phase 4 — the
 * cost is a small amount of wasted airtime, never a step-on.
 */
private fun frequencyFreeFrom(
    state: SimState,
    frequency: xyz.easiersaid.twr.protocol.Frequency,
    earliest: SimTime,
): SimTime {
    val latestEnd = state.inFlightTransmissions.values
        .filter { it.frequency == frequency && it.endsAt > earliest }
        .maxOfOrNull { it.endsAt.millis }
        ?: return earliest
    return SimTime.ofMillis(latestEnd)
}

/**
 * Mint an [InFlightTransmission] for [output] from [spec] at [startAt].
 * Returns null for outputs whose target aircraft is unknown — we refuse to
 * transmit into the void rather than emit an event with no receiver.
 */
private fun buildTransmission(
    state: SimState,
    spec: ControllerSpec,
    output: ControllerOutput,
    startAt: SimTime,
): Pair<SimState, InFlightTransmission>? {
    val target = targetOf(output) ?: return null
    if (state.aircraft[target] == null) return null
    val utterance = Utterance.FromController(output)
    val (nextState, id) = state.mintTransmissionId()
    val tx = InFlightTransmission(
        id = id,
        speaker = SpeakerRef.Controller(spec.id),
        receiver = ReceiverRef.Pilot(target),
        frequency = spec.frequency,
        utterance = utterance,
        startedAt = startAt,
        endsAt = startAt + utteranceDuration(utterance),
    )
    return nextState to tx
}

private fun targetOf(output: ControllerOutput) = when (output) {
    is ControllerOutput.Instruct -> output.target
    is ControllerOutput.Respond -> output.target
    is ControllerOutput.InitiateHandoff -> output.aircraft
}

internal fun SimState.mintTransmissionId(): Pair<SimState, TransmissionId> {
    val id = TransmissionId(nextTransmissionId)
    return copy(nextTransmissionId = nextTransmissionId + 1) to id
}
