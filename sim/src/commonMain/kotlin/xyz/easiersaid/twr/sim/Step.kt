@file:Suppress("TooManyFunctions") // step handlers + radio helpers

package xyz.easiersaid.twr.sim

import arrow.core.NonEmptyList
import arrow.core.Some
import arrow.core.getOrElse
import xyz.easiersaid.twr.pilot.AircraftState
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
import xyz.easiersaid.twr.pilot.world.toPilotView
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
import xyz.easiersaid.twr.protocol.RadarServiceTerminated
import xyz.easiersaid.twr.protocol.HandoffTarget
import xyz.easiersaid.twr.protocol.ResponsibilityState
import xyz.easiersaid.twr.protocol.RoleName
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
import xyz.easiersaid.twr.protocol.TaxiClearance
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TaxiToStand
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
import xyz.easiersaid.twr.protocol.Frequency
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
 * Pass 9 (D-AUDIT.2 / Phase 9.B): missed-handoff chase threshold. After
 * this elapses with the aircraft still in `HandingOff(Peer)` (no two-way
 * comms with target), [sweepHandoffTimeouts] emits
 * [SimEvent.MissedHandoffDetected]. Per ICAO Doc 4444 §10.1.2 the state
 * does NOT roll back — responsibility persists with the transferring
 * controller until two-way comms is established.
 *
 * **Doctrine**, not regulation: ~2 min from NATS MATS Part 1 §2.1 /
 * Eurocontrol OPS manuals. Not codified by ICAO.
 */
val MISSED_HANDOFF_TIMEOUT: SimDuration = SimDuration.ofSeconds(120)

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

    val (next, emitted) = when (event) {
        is SimEvent.PhysicsTick -> handlePhysicsTick(atTime, event)
        is SimEvent.PilotDecisionTick -> handlePilotTick(atTime, event)
        is SimEvent.ControllerCycle -> handleControllerTick(atTime, event)
        is SimEvent.Spawn -> handleSpawn(atTime, event)
        is SimEvent.TransmissionStart -> handleTransmissionStart(atTime, event)
        is SimEvent.TransmissionEnd -> handleTransmissionEnd(atTime, event)
        is SimEvent.PilotProcessingComplete -> handlePilotProcessingComplete(atTime, event)
        // Pass 9 (D-AUDIT.2 / Phase 9.B): MissedHandoffDetected has no
        // handler-state-change effect — it is a system-emitted operational
        // signal recorded by the integration test reading the event log.
        // Same shape as Spawn for system-emitted events with no
        // behavioural state change.
        is SimEvent.MissedHandoffDetected -> atTime to emptyList()
        // Pass 11 (D-AUDIT.6): FlightPlanFiled distributes responsibility
        // to the recipient controller as the strip arrives at the board.
        is SimEvent.FlightPlanFiled -> handleFlightPlanFiled(atTime, event)
        // Pass 15 (D-AUDIT.8): ATIS broadcast publication. Handler
        // stores under state.atisByAerodrome with idempotence on
        // byte-equal re-issue and unconditional update otherwise
        // (no letter-rotation invariant — real ATIS rotation has
        // wraps/skips).
        is SimEvent.AtisIssued -> handleAtisIssued(atTime, event)
        // fn-28.8 (R12 engine-failure foundation): flip the aircraft's
        // ground-truth `engineRunning` field to false. NO synthetic
        // wake event (round-2 Major 4): the pilot's regular tick cadence
        // picks the event up on its next scheduled PilotDecisionTick;
        // synthetic wake-ups would couple sim event-production to pilot
        // decision-cadence — the firewall plan deletes that coupling.
        is SimEvent.EngineFailure -> handleEngineFailure(atTime, event)
    }
    // Pass 7 (D-AUDIT.5 + Impact-O.1 / FP-M.3): cross-controller `Owned`
    // invariant. After every step, no two controllers may simultaneously
    // own the same aircraft. A regression that produced two-Owned would
    // surface here immediately, not silently. Spec test
    // `ResponsibilityInvariantSpec` exercises the throw with a hand-
    // constructed two-Owned state.
    assertResponsibilityInvariant(next)
    return next to emitted
}

/**
 * Pass 7 (D-AUDIT.5) cross-controller invariants — checked after every
 * `step()`.
 *
 * Three structural rules the per-controller `Map<AircraftId,
 * ResponsibilityState>` cannot enforce on its own:
 *
 *  1. **At most one `Owned`** per aircraft (cross-controller).
 *  2. **`HandingOff(Peer(target))` ↔ `Watching(from=current)` pairing**:
 *     if controller `current` has `HandingOff(Peer(target))` for
 *     aircraft X, then controller `target` must have
 *     `Watching(from=current.id)` for X — and vice versa. A regression
 *     that updates one side but not the other ships through invariant
 *     #1 silently.
 *  3. (Implicit) `Watching(from)` references a real controller. If
 *     `from` is not in `state.controllers`, the pairing check fires.
 *
 * Violations throw `IllegalStateException` with a clear diagnostic
 * naming the conflict.
 *
 * **No-self-handoff is unrepresentable, not invariant-enforced** (Pass 7
 * post-impl re-review FP-M-new.1): a single controller cannot
 * `HandingOff(Peer(self))` for one aircraft, because `responsibilities`
 * is `Map<AircraftId, ResponsibilityState>` — there is one state per
 * aircraft, not one per (aircraft, peer-relationship). The data shape
 * makes the case unrepresentable. **A future refactor that flattens
 * responsibility to `Map<(ControllerId, AircraftId), ResponsibilityState>`
 * (e.g. for diagnostic indexing) re-introduces the gap loudly** —
 * extend this invariant with a `senderId == target.controllerId` check
 * at that point.
 */
internal fun assertResponsibilityInvariant(state: SimState) {
    // (1) at most one Owned per aircraft
    val ownerOf = mutableMapOf<AircraftId, ControllerId>()
    for (spec in state.controllers.values) {
        for ((acId, _) in spec.responsibilities.filterValues { it is ResponsibilityState.Owned }) {
            val existing = ownerOf[acId]
            check(existing == null) {
                "RESPONSIBILITY INVARIANT VIOLATION (Owned): aircraft $acId is Owned by both " +
                    "$existing and ${spec.id} simultaneously. Pass 7 (D-AUDIT.5) requires " +
                    "at most one Owned per aircraft across all controllers."
            }
            ownerOf[acId] = spec.id
        }
    }
    // (2) HandingOff(Peer(target)) ↔ Watching(from=current) pairing
    for (handoff in peerHandoffCandidates(state)) {
        val sender = handoff.sender
        val acId = handoff.aircraft
        val target = handoff.target
        val targetSpec = state.controllers[target.controllerId]
        check(targetSpec != null) {
            "RESPONSIBILITY INVARIANT VIOLATION (pairing): controller ${sender.id} has " +
                "HandingOff(Peer(${target.controllerId})) for aircraft $acId, but no controller " +
                "${target.controllerId} exists in state.controllers. Wiring defect."
        }
        val targetState = targetSpec.responsibilities[acId]
        check(targetState is ResponsibilityState.Watching) {
            "RESPONSIBILITY INVARIANT VIOLATION (pairing): controller ${sender.id} has " +
                "HandingOff(Peer(${target.controllerId})) for aircraft $acId, but " +
                "${target.controllerId}'s state for that aircraft is $targetState (expected " +
                "Watching(from=${sender.id})). One side updated, the other skipped."
        }
        check(targetState.from == sender.id) {
            "RESPONSIBILITY INVARIANT VIOLATION (pairing): controller ${sender.id} has " +
                "HandingOff(Peer(${target.controllerId})) for aircraft $acId, but " +
                "${target.controllerId}.responsibilities[$acId] is Watching(from=${targetState.from}) " +
                "(expected from=${sender.id})."
        }
    }
    // (3) Watching(from) references a real controller. (Mirror direction:
    // every Watching has a matching HandingOff(Peer) on the named sender.)
    for (receiver in state.controllers.values) {
        for ((acId, st) in receiver.responsibilities.watchingEntries()) {
            val senderSpec = state.controllers[st.from]
            check(senderSpec != null) {
                "RESPONSIBILITY INVARIANT VIOLATION (pairing): controller ${receiver.id} has " +
                    "Watching(from=${st.from}) for aircraft $acId, but no controller ${st.from} " +
                    "exists in state.controllers. Wiring defect."
            }
            val senderState = senderSpec.responsibilities[acId]
            val ok = senderState is ResponsibilityState.HandingOff &&
                senderState.target is HandoffTarget.Peer &&
                (senderState.target as HandoffTarget.Peer).controllerId == receiver.id
            check(ok) {
                "RESPONSIBILITY INVARIANT VIOLATION (pairing): controller ${receiver.id} has " +
                    "Watching(from=${st.from}) for aircraft $acId, but ${st.from}'s state for " +
                    "that aircraft is $senderState (expected HandingOff(Peer(${receiver.id}))). " +
                    "One side updated, the other skipped."
            }
        }
    }
}

private data class PeerHandoffCandidate(
    val sender: ControllerSpec,
    val aircraft: AircraftId,
    val state: ResponsibilityState.HandingOff,
    val target: HandoffTarget.Peer,
)

private data class WatchingResponsibility(
    val aircraft: AircraftId,
    val state: ResponsibilityState.Watching,
)

private fun peerHandoffCandidates(state: SimState): List<PeerHandoffCandidate> =
    state.controllers.values.flatMap { sender ->
        sender.responsibilities.mapNotNull { (aircraft, responsibility) ->
            val handoff = responsibility as? ResponsibilityState.HandingOff
            val target = handoff?.target as? HandoffTarget.Peer
            if (handoff != null && target != null) {
                PeerHandoffCandidate(sender, aircraft, handoff, target)
            } else {
                null
            }
        }
    }

private fun Map<AircraftId, ResponsibilityState>.watchingEntries(): List<WatchingResponsibility> =
    entries.mapNotNull { entry ->
        val watching = entry.value as? ResponsibilityState.Watching
        watching?.let { WatchingResponsibility(entry.key, it) }
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
    val (afterSweep, sweepEvents) = sweepHandoffTimeouts(state.copy(aircraft = advanced))
    return afterSweep.emit(sweepEvents + next)
}

/**
 * Pass 9 (D-AUDIT.2 / Phase 9.B): detect HandingOff(Peer) ↔ Watching pairs
 * that have aged past [MISSED_HANDOFF_TIMEOUT] without resolution. Per
 * ICAO §10.1.2 the state does NOT roll back — this function only emits
 * [SimEvent.MissedHandoffDetected] for operational visibility.
 *
 * Re-fire policy: at most one event per [HandoffEscalationKey] per
 * [MISSED_HANDOFF_TIMEOUT] window. The sim tracks `lastEscalatedAt` on
 * [SimState.handoffEscalations]; subsequent firings use
 * `max(handoffSince, lastEscalatedAt)` as the elapsed-from anchor.
 *
 * **Cadence** (call sites): invoked only from [handlePhysicsTick] and
 * [handleControllerTick]. Comms-event handlers (transmission start/end,
 * pilot processing) do not invoke the sweep — the 120 s timeout has no
 * need for sub-second sampling, and an O(controllers × aircraft) walk on
 * a hot path is wasteful.
 *
 * **Different effect class than `assertResponsibilityInvariant`**: that
 * function is pure-assertion (called every step). The sweep is mutate +
 * emit + observe; it gets a narrower call-site policy.
 *
 * **Single-producer enforcement** — `FirewallMissedHandoffSweepProducerTest`
 * asserts exactly one site in `:sim/commonMain` produces a
 * `MissedHandoffDetected` event.
 */
internal fun sweepHandoffTimeouts(state: SimState): Pair<SimState, List<SimEvent>> {
    val now = state.now
    val emitted = mutableListOf<SimEvent.MissedHandoffDetected>()
    val updatedEscalations = state.handoffEscalations.toMutableMap()
    for (handoff in peerHandoffCandidates(state)) {
        val key = HandoffEscalationKey(sender = handoff.sender.id, aircraft = handoff.aircraft)
        val anchor = updatedEscalations[key] ?: handoff.state.since
        if ((now - anchor) > MISSED_HANDOFF_TIMEOUT) {
            emitted += SimEvent.MissedHandoffDetected(
                time = now,
                aircraft = handoff.aircraft,
                sender = handoff.sender.id,
                target = handoff.target.controllerId,
                handoffSince = handoff.state.since,
            )
            updatedEscalations[key] = now
        }
    }
    if (emitted.isEmpty()) return state to emptyList()
    return state.copy(handoffEscalations = updatedEscalations) to emitted
}

/** Earliest moment at or after [earliest] when [frequency] is clear of in-flight transmissions. */
private fun pilotFrequencyFreeFrom(
    state: SimState,
    frequency: Frequency,
    earliest: SimTime,
): SimTime {
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

    // fn-8.1 (R2): advance the per-aircraft RNG stream once per pilot tick
    // and persist it. Threading the stream through the tick handler makes
    // the order-of-dispatch invariance contract load-bearing — two pilot
    // ticks at the same `time` for different aircraft draw on independent
    // streams, so swapping their dispatch order produces identical
    // per-aircraft post-tick states. Today the draw is a single `nextLong()`
    // (no consumer yet — pilotDecide is total in PilotInput); future
    // sampling sites (readback delay jitter, scan-rate jitter) will read
    // and re-thread the stream the same way.
    //
    // Calls SimState.aircraftRng (loud-fail on missing entry) — every
    // aircraft in state.aircraft must have a matching rngByAircraft entry
    // by SimState.initial / handleSpawn invariant.
    val acRng = state.aircraftRng(event.aircraftId)
    val (_, advancedRng) = acRng.nextLong()

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
        // G2 Phase F (cross-aerodrome wire layer): fall back to `knownStrips`
        // when no controller has the aircraft in `responsibilities`. This is
        // exactly the cross-aerodrome autonomous-contact case: between LOWG's
        // boundary release and LJMB_TWR's `applyTwoWayCommsEstablished` flip,
        // no controller has the aircraft in responsibilities — but LJMB_TWR
        // has it in knownStrips from Pass 14 filing distribution. Without
        // this fallback, the pilot's autonomous InitialContact at the
        // procedure REP would be silently dropped.
        //
        // Single-aerodrome flows (G0) are unaffected: some controller always
        // has the aircraft in responsibilities after the first taxi clearance,
        // so the responsibilities-search wins and the knownStrips fallback
        // never fires.
        //
        // G2 Phase H (knownStrips disambiguation, closes F-impact M1):
        // when multiple controllers hold the strip, filter by the aircraft's
        // filed onward destination so the pilot's transmission goes to the
        // *destination's* tower. The filter uses the same
        // `filedDestinationAerodrome()` projection the FlightStrip layer
        // uses, ensuring strip board and wire layer agree on what "the
        // destination of this flight" means. `check(size <= 1)` is the
        // failure-loud invariant per `feedback_no_corners` — silent
        // first-wins on a Map walk would be a corner-cut shape.
        // Wire-layer route preference: the controller that currently OWNS
        // the aircraft. `Watching` and `HandingOff(target)` are transitional
        // states; for `HandingOff(Released)` specifically (cross-aerodrome
        // boundary release), the prior controller no longer wants the
        // aircraft, and the wire layer must fall through to `knownStrips`
        // so the pilot's next transmission reaches the destination tower
        // rather than going back to the released controller.
        val ctrl = resultState.controllers.values.firstOrNull {
            it.responsibilities[event.aircraftId] is xyz.easiersaid.twr.protocol.ResponsibilityState.Owned
        }
            ?: run {
                val destinationAerodrome = resultState.aircraft[event.aircraftId]
                    ?.pilotMission?.goal.filedDestinationAerodrome()
                val knownStripCandidates = resultState.controllers.values
                    .filter { event.aircraftId in it.knownStrips }
                    .let { all ->
                        if (destinationAerodrome != null) all.filter { it.aerodromeId == destinationAerodrome }
                        else all
                    }
                check(knownStripCandidates.size <= 1) {
                    "Ambiguous knownStrip controllers for ${event.aircraftId} after destination filter " +
                        "(destination=$destinationAerodrome): ${knownStripCandidates.map { it.id }}"
                }
                knownStripCandidates.firstOrNull()
            }
        if (ctrl != null) {
            var txState = resultState
            // fn-8.3 Phase 3 (B4 closure): seed `nextFreeAt` with the per-
            // aircraft `pilotRadioFreeAt` floor. Pre-fix, two consecutive
            // pilot ticks (1s apart) on the same aircraft both computed
            // proposedStart against `state.inFlightTransmissions` — but the
            // prior tick's emitted [TransmissionStart] sits in the queue at
            // its deferred `startedAt` and isn't yet visible. Both ticks
            // selected the same `proposedStart`, the two transmissions
            // collided on the same frequency at the same instant, and both
            // got marked stepped-on. The first-circuit Downwind report
            // (carrying CircuitIntent — the only signal the controller
            // has for circuit traffic) and the immediately-following Base
            // both vanished, leaving `circuitIntent[B]` unset and
            // `DEP-CIRCUIT-COMPLETE` permanently false.
            //
            // The reality-anchored model: a pilot whose own PTT is still
            // active will not begin a new transmission. The audio panel
            // signals "transmitting." Persisting `endsAt` per-aircraft and
            // reading it back here matches that real-cockpit fact.
            var nextFreeAt = maxOf(state.now, state.pilotRadioFreeAt[event.aircraftId] ?: state.now)
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
            // fn-8.3 Phase 3 (B4 closure): persist the new radio-free-at
            // for the next pilot tick to honour.
            //
            // fn-8.3 Phase 3 round 1 (codex review fix): write-back uses
            // `maxOf(existingFloor, nextFreeAt)` so this path can never
            // regress the per-aircraft floor below an already-tracked
            // future busy-until established by a sibling emission path
            // (readback / InitialContact / respond-correction).
            val existingFloor = txState.pilotRadioFreeAt[event.aircraftId] ?: nextFreeAt
            resultState = txState.copy(
                pilotRadioFreeAt = txState.pilotRadioFreeAt +
                    (event.aircraftId to maxOf(existingFloor, nextFreeAt)),
            )
        }
    }

    val aircraft = LinkedHashMap(resultState.aircraft).apply { put(event.aircraftId, updated) }
    val next = SimEvent.PilotDecisionTick(
        time = event.time + PilotConstants.PILOT_DECISION_INTERVAL,
        aircraftId = event.aircraftId,
    )
    // fn-8.1 (R2): persist the advanced per-aircraft RNG stream alongside
    // the other tick-derived state mutations. Symmetric with how shared-rng
    // sampling sites would call `state.copy(rng = newRng)` — but keyed by
    // aircraft so other aircraft's streams stay byte-stable.
    return resultState
        .copy(aircraft = aircraft)
        .withAircraftRng(event.aircraftId, advancedRng)
        .emit(commEvents + next)
}

private fun handleControllerTick(
    state: SimState,
    event: SimEvent.ControllerCycle,
): Pair<SimState, List<SimEvent>> {
    // If the controller has been de-registered (e.g. responsibility transfer
    // cleared it in a later slice), drop the tick rather than fail loudly —
    // the queue is expected to carry stale self-schedules.
    val spec = state.controllers[event.controllerId] ?: return state to emptyList()

    // fn-12 (R3a): world expiry pass — null any obstruction past clearsAt.
    // Runs BEFORE the world-diff producer so the Cleared event fires the
    // cycle the obstruction expires. Pure: no PRNG, no side effects.
    val expiredState = expireRunwayObstructions(state, state.now)

    // fn-12 (R3b): per-controller world-diff producer — compute
    // RunwayObstructionDetected/Cleared events for this controller's
    // aerodrome by comparing the prior-cycle obstruction snapshot
    // against the current (post-expiry) world. The event list is
    // delivered to the controller via `view.worldEvents`; the
    // controller's fold writes BeliefState.runwayObstructions.
    val priorObs = expiredState.priorObstructionsByController[event.controllerId] ?: emptyMap()
    val worldEvents = runwayObstructionEvents(spec.aerodromeId, priorObs, expiredState.world)

    val view = buildControllerView(expiredState, event.controllerId)
        .copy(worldEvents = worldEvents)
    val prior = expiredState.beliefs[event.controllerId] ?: BeliefState.EMPTY
    val decision = controllerDecide(view, prior, expiredState.world)

    // Each message in the view corresponds to an inbox entry that must be
    // consumed exactly once. Clear the controller's inbox before applying
    // outputs so nothing double-delivers on a later cycle.
    val inboxCleared = expiredState.copy(
        controllerInbox = expiredState.controllerInbox - event.controllerId,
    )

    val (afterOutputs, commEvents) = applyControllerOutputs(
        inboxCleared,
        event.controllerId,
        decision.outputs,
    )
    // fn-12 (R3b): persist this controller's post-cycle obstruction
    // snapshot for the next cycle's diff. The snapshot is taken from
    // the post-expiry world (the same world the diff producer just
    // consumed), so the next cycle's diff sees the correct prior state.
    val postCycleSnapshot = obstructionsSnapshot(spec.aerodromeId, afterOutputs.world)
    val withBeliefs = afterOutputs.copy(
        beliefs = afterOutputs.beliefs + (event.controllerId to decision.updatedBeliefs),
        priorObstructionsByController = afterOutputs.priorObstructionsByController +
            (event.controllerId to postCycleSnapshot),
    )

    val next = SimEvent.ControllerCycle(
        time = event.time + CONTROLLER_CYCLE_INTERVAL,
        controllerId = event.controllerId,
    )
    val (afterSweep, sweepEvents) = sweepHandoffTimeouts(withBeliefs)
    return afterSweep.emit(commEvents + sweepEvents + next)
}

/**
 * Pass 11 (D-AUDIT.6): distribute responsibility when a filed plan
 * reaches the strip board.
 *
 * Locates the controller staffing `event.recipient` at
 * `event.plan.departureAerodrome` and adds the aircraft as
 * [ResponsibilityState.Owned] since `event.time`.
 *
 * Errors loudly on:
 *  - no controller at the aerodrome staffing the recipient role (wiring
 *    defect — the fixture or scenario emitted FlightPlanFiled targeting
 *    an unstaffed role);
 *  - existing `Owned` state at a different time (refiling would silently
 *    re-anchor the timestamp — `since` is doctrine-load-bearing per
 *    Pass 7);
 *  - existing non-`Owned` state (refiling cannot silently roll back
 *    transfer state — Pass-9 invariant precedent).
 *
 * Idempotent at same-time-same-state: re-firing the same event in the
 * same cycle is a no-op (matches `MissedHandoffEventSpec`'s byte-equal
 * idempotency pattern).
 */
private fun handleFlightPlanFiled(
    state: SimState,
    event: SimEvent.FlightPlanFiled,
): Pair<SimState, List<SimEvent>> {
    val recipient = state.controllers.values
        .firstOrNull {
            it.aerodromeId == event.recipient.aerodromeId && it.role == event.recipient.role
        }
        ?: run {
            val staffedAtAerodrome = state.controllers.values
                .filter { it.aerodromeId == event.recipient.aerodromeId }
                .map { it.role }
                .toSet()
            error(
                "handleFlightPlanFiled: no controller staffed at " +
                    "${event.recipient.aerodromeId} for role ${event.recipient.role}. " +
                    "Staffed roles at this aerodrome: $staffedAtAerodrome. " +
                    "Wiring defect — fixture or scenario emitted FlightPlanFiled targeting " +
                    "an unstaffed role.",
            )
        }

    // Pass 14: dispatch via sealed AftnDestination (Departure | Arrival).
    // The classifier surfaces routing-table defects (Left) as wiring errors;
    // Right-side flow continues via sealed when below.
    val destination = xyz.easiersaid.twr.protocol.AftnDestination
        .classify(event.recipient, event.plan)
        .fold(
            ifLeft = { unreachable ->
                error(
                    "handleFlightPlanFiled: AFTN address ${unreachable.recipient} matches " +
                        "neither departure (${event.plan.departureAerodrome}) nor destination " +
                        "of plan ${unreachable.plan}. Routing-table defect — emitter computed " +
                        "a recipient that the aircraft will never reach via this filed plan.",
                )
            },
            ifRight = { it },
        )

    return when (destination) {
        xyz.easiersaid.twr.protocol.AftnDestination.Departure ->
            applyDepartureFiling(state, event, recipient)
        xyz.easiersaid.twr.protocol.AftnDestination.Arrival ->
            applyArrivalFiling(state, event, recipient)
    }
}

/**
 * Pass 14 departure-side filing: aircraft becomes `Owned` by the
 * recipient at the plan's departure aerodrome (Pass 11 semantics
 * preserved verbatim).
 */
private fun applyDepartureFiling(
    state: SimState,
    event: SimEvent.FlightPlanFiled,
    recipient: ControllerSpec,
): Pair<SimState, List<SimEvent>> {
    val existing = recipient.responsibilities[event.aircraft]
    if (existing is xyz.easiersaid.twr.protocol.ResponsibilityState.Owned) {
        if (existing.since == event.time) return state to emptyList()
        error(
            "handleFlightPlanFiled: aircraft ${event.aircraft} is already Owned by " +
                "${recipient.id} since ${existing.since} (≠ ${event.time}); refiling at a " +
                "different time would silently re-anchor the Owned timestamp.",
        )
    }
    if (existing != null) {
        // Per Pass 7 (D-AUDIT.5): HandingOff/Watching are mid-transfer
        // states that filing must not silently overwrite — would roll
        // back the cross-controller invariant.
        val stateName = when (existing) {
            is xyz.easiersaid.twr.protocol.ResponsibilityState.HandingOff -> "HandingOff"
            is xyz.easiersaid.twr.protocol.ResponsibilityState.Watching -> "Watching"
            is xyz.easiersaid.twr.protocol.ResponsibilityState.Owned -> "Owned" // unreachable
        }
        error(
            "handleFlightPlanFiled: aircraft ${event.aircraft} is in $stateName on " +
                "${recipient.id} ($existing); refiling cannot silently roll back transfer " +
                "state — would violate the Pass-7 cross-controller invariant.",
        )
    }
    val updated = recipient.copy(
        responsibilities = recipient.responsibilities +
            (event.aircraft to xyz.easiersaid.twr.protocol.ResponsibilityState.Owned(event.time)),
    )
    val controllers = LinkedHashMap(state.controllers).apply { put(updated.id, updated) }
    return state.copy(controllers = controllers) to emptyList()
}

/**
 * Pass 14 arrival-side filing: a destination tower receives the strip
 * via AFTN before the aircraft physically appears. Stored in
 * `knownStrips` (no responsibility — strip ≠ responsibility).
 *
 * Errors loudly on:
 *  - existing `responsibilities` entry (would violate the disjointness
 *    invariant on `ControllerSpec.init`);
 *  - existing `knownStrips` entry with a *different* `FiledPlan`
 *    (refile-with-amended-plan is a defect — strip-update-on-amendment
 *    is **D-AUDIT.6.C-FOLLOWUP**).
 *
 * Idempotent on byte-identical refile (same time, same plan).
 */
private fun applyArrivalFiling(
    state: SimState,
    event: SimEvent.FlightPlanFiled,
    recipient: ControllerSpec,
): Pair<SimState, List<SimEvent>> {
    if (event.aircraft in recipient.responsibilities) {
        error(
            "handleFlightPlanFiled (arrival side): aircraft ${event.aircraft} is in " +
                "${recipient.id}.responsibilities; cross-aerodrome filing must not " +
                "overlap responsibility (the disjointness invariant).",
        )
    }
    val existingStrip = recipient.knownStrips[event.aircraft]
    if (existingStrip != null) {
        if (existingStrip == event.plan) return state to emptyList() // idempotent
        error(
            "handleFlightPlanFiled (arrival side): aircraft ${event.aircraft} already " +
                "has a knownStrip on ${recipient.id} with a different plan. " +
                "Per ICAO Doc 4444 §11.4 (FPL amendment via CHG message), a real ATC " +
                "system propagates strip updates to the destination via a separate " +
                "amendment flow. TWR2 deferment D-AUDIT.6.C-FOLLOWUP tracks this; " +
                "today refile must be byte-identical or fail loudly. " +
                "Existing: $existingStrip; new: ${event.plan}.",
        )
    }
    val updated = recipient.copy(knownStrips = recipient.knownStrips + (event.aircraft to event.plan))
    val controllers = LinkedHashMap(state.controllers).apply { put(updated.id, updated) }
    return state.copy(controllers = controllers) to emptyList()
}

/**
 * Pass 15 (D-AUDIT.8 closure) — ATIS broadcast handler.
 *
 * Stores the latest ATIS for [event.aerodrome] in
 * [SimState.atisByAerodrome]. Idempotent on byte-equal re-issue
 * (data-class equality on the [Atis]); unconditional update
 * otherwise.
 *
 * **No letter-rotation invariant**: real ATIS rotation includes
 * supervisor-driven skips (regenerate a fresh report on weather/
 * runway change without strict A→B→C ordering) and the canonical
 * Z→A wrap. A strict next-letter check would falsely reject real
 * fixtures. The pure helper [xyz.easiersaid.twr.protocol.nextAtisLetter]
 * is available for callers that want to advance canonically.
 *
 * **Doctrine**: ICAO Annex 11 §4.3 (ATIS service); Doc 4444 §4.5.5
 * (broadcast content).
 */
private fun handleAtisIssued(
    state: SimState,
    event: SimEvent.AtisIssued,
): Pair<SimState, List<SimEvent>> {
    require(event.atis.aerodrome == event.aerodrome) {
        "AtisIssued: event.aerodrome (${event.aerodrome}) must match " +
            "event.atis.aerodrome (${event.atis.aerodrome})"
    }
    val existing = state.atisByAerodrome[event.aerodrome]
    if (existing == event.atis) return state to emptyList()
    val next = state.atisByAerodrome + (event.aerodrome to event.atis)
    return state.copy(atisByAerodrome = next) to emptyList()
}

/**
 * fn-28.8 (G0 abort-takeoff foundation R12): engine-failure handler.
 *
 * Flips `AircraftState.engineRunning` to `false` for the targeted aircraft.
 * The next [SimEvent.PhysicsTick] applies the engine-off clamp in
 * `advanceKinematics`: when `engineRunning == false`, the integrator's
 * working speed is bounded by `min(targetSpeedMps, currentSpeedMps)` —
 * deceleration is allowed (pilot can command brakes / aero drag stops the
 * roll), acceleration is blocked (no engine thrust to accelerate).
 *
 * **No synthetic wake event** (round-2 Major 4 decision): the handler
 * emits an empty event list. The pilot's existing per-aircraft
 * [SimEvent.PilotDecisionTick] cadence picks the engine-failure
 * observation up on the next scheduled tick (via the cockpit-side
 * instructor-channel observation seam landing in fn-28.9). Emitting a
 * synthetic `PilotDecisionTick` here would couple sim event-production
 * to pilot decision-cadence — the same coupling the firewall plan
 * deletes elsewhere.
 *
 * **Missing aircraft is a defect** (mirrors the [handlePilotTick]
 * defensive shape): if the event targets an aircraft no longer in
 * `state.aircraft` (e.g. event scheduled at fixture init time but
 * aircraft never spawned, or de-spawned mid-sim), no state change
 * fires. Today the fixture-load layer's `StartPointWithoutFlightPlan`
 * + `FlightPlanMissingStartPoint` violations catch this at fixture
 * authoring time, so the only path here is a no-op return.
 */
private fun handleEngineFailure(
    state: SimState,
    event: SimEvent.EngineFailure,
): Pair<SimState, List<SimEvent>> {
    val ac = state.aircraft[event.aircraftId] ?: return state to emptyList()
    if (!ac.engineRunning) return state to emptyList()
    val updated = ac.copy(engineRunning = false)
    val aircraft = LinkedHashMap(state.aircraft).apply { put(event.aircraftId, updated) }
    return state.copy(aircraft = aircraft) to emptyList()
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
    // fn-8.1: seed the per-aircraft RNG entry for the newly-spawned aircraft.
    // Without this the SimState invariant "every key in state.aircraft has
    // a matching rngByAircraft entry" would break the moment a Spawn event
    // fires, and the first pilot tick would hit aircraftRng's loud error.
    // Splitting from the shared `state.rng` keyed by `ac.id.value` mirrors
    // the seeding shape in SimState.initial.
    val firstPilotTick = SimEvent.PilotDecisionTick(event.time, ac.id)
    val seeded = state.copy(aircraft = aircraft).withAircraftRng(ac.id, state.rng.split(ac.id.value))
    return seeded.emit(listOf(firstPilotTick))
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
        is ReceiverRef.Controller -> handleControllerTransmissionEnd(withoutTx, tx)
    }
}

private fun handleControllerTransmissionEnd(
    state: SimState,
    tx: InFlightTransmission,
): Pair<SimState, List<SimEvent>> {
    val msg = receivedMessageFrom(tx) ?: return state to emptyList()
    val nextInbox = controllerInboxAfterBroadcast(state, tx.frequency, msg)
    val withMissionAcked = applyInitialContactLanding(state, tx.frequency, msg)
    return withMissionAcked.copy(controllerInbox = nextInbox) to emptyList()
}

private fun controllerInboxAfterBroadcast(
    state: SimState,
    frequency: Frequency,
    msg: ReceivedMessage,
): Map<ControllerId, List<ReceivedMessage>> {
    val recipients = state.controllers.values
        .filter { it.frequency == frequency }
        .map { it.id }
    return recipients.fold(state.controllerInbox) { acc, ctrlId ->
        acc + (ctrlId to (acc[ctrlId].orEmpty() + msg))
    }
}

private fun applyInitialContactLanding(
    state: SimState,
    frequency: Frequency,
    msg: ReceivedMessage,
): SimState {
    val clear = msg as? ReceivedMessage.Clear ?: return state
    val receivingControllerId = receivingControllerForInbound(state, frequency, clear.aircraft) ?: return state
    val withFlippedMission = flipMissionContactedOnInitialContact(state, clear)
    val receivingRole = withFlippedMission.controllers[receivingControllerId]?.role
    val acAfter = withFlippedMission.aircraft[clear.aircraft]
    return if (receivingRole != null && acAfter != null) {
        applyTwoWayCommsEstablished(withFlippedMission, acAfter, receivingRole)
    } else {
        withFlippedMission
    }
}

private fun receivingControllerForInbound(
    state: SimState,
    frequency: Frequency,
    aircraft: AircraftId,
): ControllerId? {
    val candidates = state.controllers.values.filter { it.frequency == frequency }
    val watchingCandidates = candidates.filter {
        it.responsibilities[aircraft] is ResponsibilityState.Watching
    }
    val knownStripCandidates = candidates.filter { aircraft in it.knownStrips }
    check(watchingCandidates.size <= 1) {
        "Ambiguous Watching candidates on frequency $frequency for $aircraft: " +
            "${watchingCandidates.map { it.id }} — at most one controller may be " +
            "Watching an aircraft on a given frequency."
    }
    check(knownStripCandidates.size <= 1) {
        "Ambiguous knownStrip candidates on frequency $frequency for $aircraft: " +
            "${knownStripCandidates.map { it.id }} — at most one controller per " +
            "frequency may hold the filed strip for an aircraft."
    }
    return watchingCandidates.firstOrNull()?.id ?: knownStripCandidates.firstOrNull()?.id
}

private fun flipMissionContactedOnInitialContact(
    state: SimState,
    clear: ReceivedMessage.Clear,
): SimState {
    val ac = state.aircraft[clear.aircraft] ?: return state
    val mission = ac.pilotMission ?: return state
    if (mission.contactedOnFrequency) return state
    if (clear.transmission !is InitialContact) return state
    val updatedAc = ac.copy(pilotMission = mission.copy(contactedOnFrequency = true))
    return state.copy(
        aircraft = LinkedHashMap(state.aircraft).apply {
            put(clear.aircraft, updatedAc)
        },
    )
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
    //
    // fn-8.3 Phase 3 round 1 (codex review fix): the per-aircraft
    // [SimState.pilotRadioFreeAt] floor must also gate the readback's
    // earliest start. Otherwise a prior pilot tick that scheduled a future
    // transmission (queued at its deferred `startedAt`, not yet processed
    // by `handleTransmissionStart`) is invisible to `pilotFrequencyFreeFrom`
    // here, and the readback can overlap with it. Same-aircraft race; the
    // [pilotRadioFreeAt] tracker exists precisely so this race is closed.
    val earliestReadback = eventTime + CommsConstants.PILOT_READBACK_PREP
    val pilotRadioFloor = afterApply.pilotRadioFreeAt[ac.id] ?: earliestReadback
    val readbackStartAt = maxOf(
        earliestReadback,
        pilotRadioFloor,
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
    val (afterIc, icEvents, finalRadioFreeAt) = if (cf != null && newController != null) {
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
        Triple(
            withIcId,
            listOf(SimEvent.TransmissionStart(time = icTx.startedAt, transmission = icTx)),
            icTx.endsAt,
        )
    } else Triple(withReadbackId, emptyList(), readbackTx.endsAt)
    // fn-8.3 Phase 3 (B4 closure): persist the per-aircraft radio-free-at
    // floor so the next pilot tick honours it. Symmetric with the same
    // discipline on `handlePilotTick`. See SimState.pilotRadioFreeAt KDoc
    // for doctrine.
    //
    // fn-8.3 Phase 3 round 1 (codex review fix): write-back uses
    // `maxOf(existingFloor, finalRadioFreeAt)` so a freshly-emitted
    // transmission with a larger `endsAt` cannot regress the floor below
    // an already-tracked future busy-until. The non-monotonic write was a
    // race when a prior path stamped a far-future floor and this readback
    // path overwrote it with a nearer value.
    val existingFloor = afterIc.pilotRadioFreeAt[ac.id] ?: finalRadioFreeAt
    val withRadioFreeAt = afterIc.copy(
        pilotRadioFreeAt = afterIc.pilotRadioFreeAt + (ac.id to maxOf(existingFloor, finalRadioFreeAt)),
    )
    return withRadioFreeAt.emit(listOf(readbackStart) + icEvents)
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
    // fn-8.3 Phase 3 round 1 (codex review fix): consult the per-aircraft
    // [SimState.pilotRadioFreeAt] floor when computing `startAt`, symmetric
    // with the readback path. Without it, a corrected readback can collide
    // with a prior queued (not-yet-applied) transmission from the same
    // aircraft.
    val pilotRadioFloor = afterMission.pilotRadioFreeAt[ac.id] ?: earliestStart
    val startAt = maxOf(
        earliestStart,
        pilotRadioFloor,
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
    // fn-8.3 Phase 3 (B4 closure): persist the per-aircraft radio-free-at
    // floor for the corrected-readback path. See SimState.pilotRadioFreeAt
    // KDoc for doctrine.
    //
    // fn-8.3 Phase 3 round 1 (codex review fix): write-back uses
    // `maxOf(existingFloor, inflight.endsAt)` so a freshly-emitted
    // transmission can never regress the per-aircraft floor below an
    // already-tracked future busy-until.
    val existingFloor = withTxId.pilotRadioFreeAt[ac.id] ?: inflight.endsAt
    val withRadioFreeAt = withTxId.copy(
        pilotRadioFreeAt = withTxId.pilotRadioFreeAt + (ac.id to maxOf(existingFloor, inflight.endsAt)),
    )
    return withRadioFreeAt.emit(listOf(SimEvent.TransmissionStart(time = inflight.startedAt, transmission = inflight)))
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
    is TaxiToHoldingPoint -> applyTaxiTo(state, ac, instruction)
    is TaxiToStand -> applyTaxiTo(state, ac, instruction)
    is LineUpAndWait -> applyLineUpAndWait(state, ac, instruction)
    is ClearedForTakeoff -> applyClearedForTakeoff(state, ac, instruction)
    is AfterLandingVacateVia -> applyAfterLandingVacateVia(state, ac, instruction)
    is BacktrackRunway -> applyBacktrackRunway(state, ac, instruction)
    is ContactFrequency -> applyContactFrequency(state, ac, instruction)
    is RadarServiceTerminated -> applyRadarServiceTerminated(state, ac, instruction)
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

private fun applyTaxiTo(state: SimState, ac: AircraftState, instruction: TaxiClearance): SimState {
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
    aircraftType: xyz.easiersaid.twr.protocol.AircraftType,
): PilotRoute.Airborne? =
    // fn-24: project to PilotAviationWorld at the call site —
    // `buildVisualDepartureRoute` migrated to the pilot-firewall
    // typed projection (R7). The sim retains its `AviationWorld`
    // signature; the projection is local to this helper's caller path.
    buildVisualDepartureRoute(runwayId, world.toPilotView(), aircraftType).getOrNull()

/**
 * "Cleared for takeoff" at the threshold: switch to a departure route and
 * transition to [PilotPhase.TakeoffRoll].
 */
private fun applyClearedForTakeoff(
    state: SimState,
    ac: AircraftState,
    instruction: ClearedForTakeoff,
): SimState {
    val route = buildDepartureRoute(state.world, instruction.runway, ac.type)
        ?: return state
    val updated = ac.copy(
        phase = PilotPhase.TakeoffRoll,
        route = route,
        targetSpeedMps = ac.type.kinematics.climbSpeedMps,
        targetAltitudeM = ac.type.circuitPattern.altitudeAglM,
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
/**
 * Pass 7 post-impl (FP-S.3): single source of truth for "find the controller
 * that currently owns this aircraft." Both [applyContactFrequency] and
 * [applyRadarServiceTerminated] need this; the duplication is now extracted.
 *
 * Impact-M.2: the filter is `is Owned` explicitly. `Map.contains` would
 * also match a `Watching` peer mid-handoff, who cannot legally hand off
 * an aircraft they don't yet own.
 */
private fun requireOwner(state: SimState, ac: AircraftState, instructionLabel: String): ControllerSpec =
    state.controllers.values.firstOrNull { spec ->
        spec.responsibilities[ac.id] is ResponsibilityState.Owned
    } ?: error(
        "$instructionLabel: no controller currently OWNS ${ac.id}; instruction can't be processed. " +
            "Wiring defect — responsibility was stripped without a handoff.",
    )

internal fun applyContactFrequency(
    state: SimState,
    ac: AircraftState,
    instruction: ContactFrequency,
): SimState {
    // Pass 7 (D-AUDIT.5): the typed responsibility state machine. On
    // ContactFrequency, the current controller transitions Owned →
    // HandingOff(Peer(target)) and the target controller adds Watching(from).
    // The pilot's first transmission to the new controller is what completes
    // the transfer (applyTwoWayCommsEstablished) — until then, current still
    // legally owns the aircraft per ICAO Doc 4444 §10.1.
    val current = requireOwner(state, ac, "applyContactFrequency(${instruction.role})")
    val target = state.controllers.values
        .firstOrNull { it.aerodromeId == current.aerodromeId && it.role == instruction.role }
        ?: error(
            "applyContactFrequency: target ${instruction.role} not staffed at ${current.aerodromeId}; " +
                "current owner is ${current.id}. " +
                "World-config defect — the issuing rule should not emit ContactFrequency " +
                "for a role that has no controller modelled at this aerodrome.",
        )
    val now = state.now
    val controllersMap = LinkedHashMap(state.controllers)
    controllersMap[current.id] = current.copy(
        responsibilities = current.responsibilities + (ac.id to ResponsibilityState.HandingOff(
            target = HandoffTarget.Peer(target.id),
            since = now,
        )),
    )
    // Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13): if the target
    // had a prior strip in knownStrips (e.g. a destination tower that
    // received the AFTN strip for this aircraft hours before the handoff),
    // the entry moves out of knownStrips as the responsibility state
    // machine first reaches Watching. The disjointness invariant on
    // ControllerSpec.init enforces that an aircraft is never in both at
    // once — without this cleanup, the require would fail.
    //
    // **Strengthening over Pass 7's silent overwrite** (post-impl impact
    // S1): pre-Pass-14 the `target.responsibilities + (ac to Watching)`
    // would silently overwrite any pre-existing entry on `target` for the
    // same aircraft. With the disjointness invariant, a latent
    // pre-existing `knownStrips[ac]` now surfaces as a require() failure
    // rather than a silent state-machine roll-back. This is an
    // improvement, not a regression — the invariant catches what Pass 7
    // would have hidden.
    //
    // Pass 14 single-recipient-per-side contract: destination side is at
    // most one recipient (TOWER, falling back to APPROACH). When multi-
    // destination-recipient routing lands (a future pass adding APPROACH
    // alongside TOWER), this cleanup invariant must be revisited because
    // multiple destination controllers could each hold a copy.
    controllersMap[target.id] = target.copy(
        responsibilities = target.responsibilities + (ac.id to ResponsibilityState.Watching(
            from = current.id,
            since = now,
        )),
        knownStrips = target.knownStrips - ac.id,
    )
    return state.copy(controllers = controllersMap)
}

/**
 * Pass 7 (D-PF.7): the alternative to [applyContactFrequency] when no
 * successor is staffed. Issues `RadarServiceTerminated`; current goes to
 * `HandingOff(Released)`; pilot's readback (handled by the standard
 * readback flow) drops the entry from `responsibilities`. No peer
 * `Watching` is created — the aircraft is leaving the controlled-airspace
 * system.
 *
 * **Note on the instruction's `squawk`/`suggestedFrequency` fields**
 * (Impact-O.3 fold-in): both are *consumed at the readback level* —
 * `InstructionReadback.kt` produces a `SquawkReadback(squawk)` atom for
 * the readback when squawk is `Some`. There is no `AircraftState.squawk`
 * field today (it lives on a future Annex 10 / D-AUDIT.4 surface), so
 * the sim-side propagation of squawk-on-receipt is out of scope for
 * Pass 7. The action carrying the field is correct: it documents the
 * instruction's content; the readback layer enforces the pilot
 * acknowledges it. If a future engineer wires `AircraftState.squawk`,
 * this function gets one extra arm.
 */
internal fun applyRadarServiceTerminated(
    state: SimState,
    ac: AircraftState,
    @Suppress("UNUSED_PARAMETER") instruction: RadarServiceTerminated,
): SimState {
    // G2 closure: drop the sending controller's responsibility outright. A
    // boundary release is unilateral — when the controller says "radar
    // service terminated," they are *done* with the aircraft. The pilot's
    // squawk-readback is an acknowledgment, not a precondition. Pre-G2
    // closure this transitioned to `HandingOff(Released)` and waited for
    // [applyBoundaryReleaseReadback] (called from `handleTransmissionEnd`'s
    // pilot-tx-end branch) to flip to absent, but that left a 2-3 s window
    // (cognitive delay + readback travel) during which the pilot might
    // already have switched frequency and made first contact at the
    // destination — violating the R5 pre-contact snapshot pin: at the
    // moment before the destination contact, no LOWG controller may hold
    // *any* responsibility entry (Owned, HandingOff, or Watching).
    //
    // Idempotency: if no controller currently owns the aircraft, the
    // release has already applied — typical when COORD-REISSUE fires after
    // the original sender has lost ownership and the pilot processes the
    // re-issued RST much later. Without the gate, `requireOwner` would
    // find the *current* owner (e.g. the cross-aerodrome destination
    // tower) and wrongly drop THEM, collapsing the destination's
    // commitment and stranding the pilot at AWAIT_LANDING_CLEARANCE.
    //
    // Safe semantics: an aircraft can only be released once per
    // responsibility lifecycle; subsequent RST deliveries are stale.
    val owner = state.controllers.values.firstOrNull { spec ->
        spec.responsibilities[ac.id] is ResponsibilityState.Owned
    } ?: return state
    val controllersMap = LinkedHashMap(state.controllers)
    controllersMap[owner.id] = owner.copy(responsibilities = owner.responsibilities - ac.id)
    return state.copy(controllers = controllersMap)
}

/**
 * Pass 7 (D-AUDIT.5 closure step 2): two-way communications established.
 * Per ICAO Doc 4444 §10.1.1: *"Two-way communication shall be considered
 * to have been established when the receiving station has been positively
 * identified and acknowledges receipt..."* — the model treats *any* pilot
 * transmission to a `Watching` controller as the established-comms event.
 * The receiving controller (currently `Watching`) becomes `Owned`; the
 * sending controller (currently `HandingOff(Peer(target))`) drops the
 * aircraft.
 *
 * **Name vs trigger**: this function is named for the *event* (two-way
 * comms established), not for the *phrase* that triggers it. Real ATC
 * phraseology combines initial contact and report into one transmission
 * ("Tower, OE-ABC, holding short 16C, ready"); the call site dispatches
 * on any pilot transmission, not specifically on `Utterance.FromPilot(InitialContact)`.
 *
 * Note: this is the *sim-side* dispatch, not via
 * `ControllerEvent.InitialContactReceived` (which is the controller-side
 * observable). Both fire from the same pilot transmission but for
 * different concerns — the sim flips the responsibility map; the
 * controller updates its beliefs. Firewall pattern preserved.
 *
 * Boundary release does NOT come through this path — [applyRadarServiceTerminated]
 * drops the sending controller's responsibility unilaterally on the pilot's
 * RST processing tick (G2 closure: previously transitioned to
 * `HandingOff(Released)` and waited for the readback, but the resulting
 * 2-3 s window let the pilot already make destination contact while the
 * sender still held a responsibility entry — violating the R5
 * pre-contact snapshot).
 */
internal fun applyTwoWayCommsEstablished(
    state: SimState,
    ac: AircraftState,
    stationCalled: RoleName,
): SimState {
    // Find the controller this aircraft is establishing two-way comms with.
    // Two distinct cases:
    //
    // 1. Intra-aerodrome handoff (Pass 7): a controller is `Watching` the
    //    aircraft, having been transitioned by a prior `applyContactFrequency`.
    //    The receiver flips Watching → Owned; the sender's HandingOff entry
    //    is dropped.
    // 2. Cross-aerodrome arrival (G2 Phase E): a controller has the aircraft
    //    in `knownStrips` from a Pass 14 filing, but no `Watching` state
    //    exists because no peer-handoff was issued (LOWG can't hand off to
    //    LJMB; the pilot self-contacts on entering destination airspace).
    //    The receiver moves the strip from knownStrips into responsibilities
    //    as Owned; no sender update.
    //
    // Receiver lookup matches stationCalled with both states. The two are
    // mutually exclusive in practice: an aircraft is Watching by at most one
    // controller and is in knownStrips of at most one controller.
    val target = state.controllers.values.firstOrNull { spec ->
        spec.role == stationCalled &&
            (spec.responsibilities[ac.id] is ResponsibilityState.Watching ||
                ac.id in spec.knownStrips)
    } ?: error(
        "applyTwoWayCommsEstablished: pilot transmission to role $stationCalled for aircraft ${ac.id} " +
            "found no receiver. The aircraft is in neither responsibilities (Watching expected) nor " +
            "knownStrips (filing-distributed expected) of any $stationCalled controller. Either the " +
            "filing did not route through, a prior handoff did not establish Watching, or the dispatch " +
            "in handleTransmissionEnd's receiver-search disagreed with this lookup."
    )
    val isWatchingPath = target.responsibilities[ac.id] is ResponsibilityState.Watching
    val controllersMap = LinkedHashMap(state.controllers)
    if (isWatchingPath) {
        // Pass 7 path. Receiver Watching → Owned; drop sender's HandingOff entry.
        // Pass 7 post-impl FP-P-new.1: a missing sender is a paired-state
        // violation — the receiver Watching state references a non-existent
        // HandingOff(Peer). assertResponsibilityInvariant would fire on this,
        // but catching it loudly here gives a more direct stack trace.
        val sender = state.controllers.values.firstOrNull { spec ->
            val r = spec.responsibilities[ac.id]
            r is ResponsibilityState.HandingOff &&
                r.target is HandoffTarget.Peer &&
                (r.target as HandoffTarget.Peer).controllerId == target.id
        } ?: error(
            "applyTwoWayCommsEstablished: receiver ${target.id} is Watching ${ac.id} but no controller " +
                "has HandingOff(Peer(${target.id})) for that aircraft. Paired-state violation — " +
                "applyContactFrequency must transition both sides atomically.",
        )
        controllersMap[target.id] = target.copy(
            responsibilities = target.responsibilities + (ac.id to ResponsibilityState.Owned(state.now)),
        )
        controllersMap[sender.id] = sender.copy(
            responsibilities = sender.responsibilities - ac.id,
        )
        // Pass 9 (D-AUDIT.2 / Phase 9.B): handoff resolved — clear any
        // missed-handoff escalation tracking for the (sender, aircraft) pair.
        val escalations = state.handoffEscalations - HandoffEscalationKey(sender = sender.id, aircraft = ac.id)
        return state.copy(controllers = controllersMap, handoffEscalations = escalations)
    } else {
        // G2 Phase E: cross-aerodrome arrival path. Aircraft was in target's
        // knownStrips from filing; flip into responsibilities as Owned and
        // drop the strip. No sender to update — there was no peer-handoff.
        controllersMap[target.id] = target.copy(
            responsibilities = target.responsibilities + (ac.id to ResponsibilityState.Owned(state.now)),
            knownStrips = target.knownStrips - ac.id,
        )
        return state.copy(controllers = controllersMap)
    }
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
 * integrated toward [AircraftState.targetAltitudeM] at the per-type
 * [xyz.easiersaid.twr.protocol.AircraftType.Kinematics.climbRateMps]
 * (Pass 10 D-AUDIT.4).
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
    // fn-28.8 (R12 engine-off instant-speed clamp / round-3 Major 2 contract):
    // when `engineRunning == false`, the integrator's working speed is bounded
    // by `min(targetSpeedMps, currentSpeedMps)`. Deceleration is allowed (pilot
    // can command brakes / aero drag stops the roll: target < current); accel
    // is blocked (no engine thrust to accelerate beyond current: target > current
    // is clamped). When engine is running the working speed is `targetSpeedMps`
    // verbatim — pre-fn-28.8 behaviour preserved for the on-engine path.
    //
    // **Instant-speed model** (not a per-tick decel rate): v1 of the abort
    // model accepts that engine-off transitions snap to the clamped speed in
    // a single tick. A future fn-28 task may refine to per-tick decel via
    // `AircraftType.abortDecelMs2` — explicitly excluded from fn-28.8's scope
    // (the task spec's NOT-in-scope list lists `abortDecelMs2`).
    val speed = if (ac.engineRunning) {
        ac.targetSpeedMps
    } else {
        minOf(ac.targetSpeedMps, ac.speedMps)
    }
    val headPoint = when (val r = ac.route) {
        is PilotRoute.Ground -> r.waypoints.head
        is PilotRoute.Airborne -> r.waypoints.head
        PilotRoute.None -> null
    }
    val altitude = advanceAltitude(ac.altitudeM, ac.targetAltitudeM, dtSeconds, ac.type.kinematics.climbRateMps)

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
    // Advance graph-level position whenever the aircraft is within the
    // pilot's [AircraftType.Kinematics.waypointRadiusM] of the head
    // waypoint — the same radius the pilot uses to pop waypoints.
    // Aligning the two guarantees `positionPoint` never skips a waypoint
    // the pilot visits, so the controller's point-indexed guards
    // (AtHoldingPoint, AtStand, OnRunway, OnCircuitLeg) see every leg.
    // Pass 13 (D-AUDIT.4.D-FOLLOWUP): per-type radius scales with cruise
    // speed (jet ticks 130 m/step at climb).
    val ddx = headPos.xMeters - newPos.xMeters
    val ddy = headPos.yMeters - newPos.yMeters
    val distanceAfter = StrictMath.hypot(ddx, ddy)
    val newPositionPoint =
        if (distanceAfter <= ac.type.kinematics.waypointRadiusM) headPoint else ac.positionPoint
    return ac.copy(
        position = newPos,
        positionPoint = newPositionPoint,
        speedMps = speed,
        altitudeM = altitude,
    )
}

/**
 * Integrate altitude toward [target] at [climbRateMps]. Pass 10 (D-AUDIT.4):
 * the climb rate now comes from the aircraft's [AircraftType.kinematics]
 * rather than a global constant.
 */
private fun advanceAltitude(current: Double, target: Double, dtSeconds: Double, climbRateMps: Double): Double {
    val delta = target - current
    if (delta == 0.0) return current
    val maxStep = climbRateMps * dtSeconds
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
