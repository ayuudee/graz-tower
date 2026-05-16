package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.PilotIntent
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.SimTime

/**
 * Simulation event — the unit of discrete-event progression.
 *
 * Every event carries a deterministic ordering triple `(time, source, seq)`:
 *   - [time]: when the event fires.
 *   - [source]: which agent produced it — see [AgentId].
 *   - [seq]: monotonic per-run counter; assigned by [step] when the event is
 *            emitted. Distinguishes events that share a time and source.
 *
 * The queue orders on this triple and [step] sorts its emissions on the same
 * triple, so two runs with the same seed and initial events replay to byte-
 * identical state.
 */
sealed interface SimEvent {
    val time: SimTime
    val source: AgentId
    val seq: Long

    /**
     * Physics integration step. Self-schedules the next tick at a fixed cadence
     * (1 Hz). Each tick advances every aircraft's kinematics toward the first
     * waypoint of its current route.
     */
    data class PhysicsTick(
        override val time: SimTime,
        override val seq: Long = 0,
    ) : SimEvent {
        override val source: AgentId = AgentId.System
    }

    /**
     * Controller decision cycle for one controller. Self-schedules the next
     * cycle at the controller's cadence. Slice 4a/4b stub: re-enqueues itself
     * only. Slice 4c wires [xyz.easiersaid.twr.controller.controllerDecide].
     */
    data class ControllerCycle(
        override val time: SimTime,
        val controllerId: ControllerId,
        override val seq: Long = 0,
    ) : SimEvent {
        override val source: AgentId get() = AgentId.Controller(controllerId)
    }

    /**
     * Pilot decision cycle for one aircraft. Self-schedules. Invokes the
     * default pilot agent on the aircraft's current state and applies the
     * returned [PilotIntent].
     */
    data class PilotDecisionTick(
        override val time: SimTime,
        val aircraftId: AircraftId,
        override val seq: Long = 0,
    ) : SimEvent {
        override val source: AgentId get() = AgentId.Pilot(aircraftId)
    }

    /**
     * Introduce a new aircraft into the simulation. Emits the first
     * [PilotDecisionTick] so the pilot starts running on the same instant it
     * enters the world.
     */
    data class Spawn(
        override val time: SimTime,
        val aircraft: AircraftState,
        override val seq: Long = 0,
    ) : SimEvent {
        override val source: AgentId = AgentId.System
    }

    /**
     * A transmission has just gone on-air. Creation site: the moment the
     * speaker keys the mic — [time] equals the transmission's [startedAt].
     *
     * Overlap detection is done inside the handler: any currently-in-flight
     * transmission on the same [Frequency] whose window intersects
     * `[startedAt, endsAt]` has both records flipped to stepped-on. The
     * handler also schedules a matching [TransmissionEnd].
     */
    data class TransmissionStart(
        override val time: SimTime,
        val transmission: InFlightTransmission,
        override val seq: Long = 0,
    ) : SimEvent {
        override val source: AgentId get() = transmission.speaker.toAgentId()
    }

    /**
     * A transmission has just left the air. If [InFlightTransmission.steppedOn]
     * is false at this point the utterance is delivered to the receiver (pilot
     * cognitive processing scheduled, or deposited in the controller inbox).
     * Otherwise the transmission is discarded — garbled content, no effect.
     */
    data class TransmissionEnd(
        override val time: SimTime,
        val transmissionId: TransmissionId,
        override val seq: Long = 0,
    ) : SimEvent {
        override val source: AgentId = AgentId.System
    }

    /**
     * Fired after [CommsConstants.PILOT_COGNITIVE_DELAY] has elapsed since the
     * pilot finished hearing a non-stepped-on controller transmission. This
     * is where the pilot actually acts on the instruction (route/phase
     * updates) and kicks off the readback transmission after a short prep.
     */
    data class PilotProcessingComplete(
        override val time: SimTime,
        val aircraftId: AircraftId,
        val utterance: Utterance,
        override val seq: Long = 0,
    ) : SimEvent {
        override val source: AgentId get() = AgentId.Pilot(aircraftId)
    }

    /**
     * Pass 11 (D-AUDIT.6): an aircraft's filed plan reached the strip
     * board. Closes the "spawn is filing" gap — the strip exists at this
     * moment; the aircraft may or may not be physically present yet
     * (Spawn event fires separately, at engine start time).
     *
     * Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13): [recipient]
     * carries an explicit aerodrome via [AftnAddress] so cross-aerodrome
     * destination strips can route correctly. Routing fan-out (one
     * filed plan → N events) is the producer's job via
     * [xyz.easiersaid.twr.sim.AftnRouting.routeFiledPlan]; the handler
     * processes one recipient per event.
     *
     * Handler dispatches via [AftnDestination.classify]:
     *  - **Departure side** (`recipient.aerodromeId == plan.departureAerodrome`):
     *    aircraft becomes `Owned` by the recipient.
     *  - **Arrival side** (`recipient.aerodromeId == plan.destinationAerodrome`):
     *    plan is added to the recipient's `knownStrips` (no responsibility).
     */
    data class FlightPlanFiled(
        override val time: SimTime,
        val aircraft: AircraftId,
        val plan: xyz.easiersaid.twr.protocol.FiledPlan,
        /**
         * AFTN address of the controller bay receiving this strip copy.
         * Pass 14: `AftnAddress(aerodromeId, role)` makes cross-aerodrome
         * destination strips representable. **No default** — every
         * emitter specifies.
         */
        val recipient: xyz.easiersaid.twr.protocol.AftnAddress,
        override val seq: Long = 0,
    ) : SimEvent {
        override val source: AgentId = AgentId.System
    }

    /**
     * Pass 9 (D-AUDIT.2 / Phase 9.B): operational signal that a peer
     * handoff has aged past [MISSED_HANDOFF_TIMEOUT] without two-way
     * comms being established. Per ICAO Doc 4444 §10.1.2 the responsibility
     * state does NOT roll back — the transferring controller still owns
     * the aircraft. This event is purely diagnostic; controller-side
     * reactive consumption (re-issuing `ContactFrequency`) is deferred to
     * **D-PF.9**.
     *
     * No handler in `step()` — the event is emitted by
     * [sweepHandoffTimeouts] for queue-stream observers (the integration
     * test reads the event log). Same shape as [Spawn]: system-emitted,
     * no behavioural state-change handler.
     */
    data class MissedHandoffDetected(
        override val time: SimTime,
        val aircraft: AircraftId,
        val sender: ControllerId,
        val target: ControllerId,
        val handoffSince: SimTime,
        override val seq: Long = 0,
    ) : SimEvent {
        override val source: AgentId = AgentId.System
    }

    /**
     * Pass 15 (D-AUDIT.8 closure): an ATIS broadcast was published for
     * [aerodrome]. Handler stores under [SimState.atisByAerodrome];
     * idempotent on byte-equal re-issue. **No letter-rotation
     * invariant** — real ATIS rotation has wraps and skips that a
     * strict A→B→C check would falsely reject.
     *
     * **Doctrine**: ICAO Annex 11 §4.3 (ATIS service); Doc 4444 §4.5.5.
     */
    data class AtisIssued(
        override val time: SimTime,
        val aerodrome: xyz.easiersaid.twr.protocol.AerodromeId,
        val atis: xyz.easiersaid.twr.protocol.Atis,
        override val seq: Long = 0,
    ) : SimEvent {
        override val source: AgentId = AgentId.System
    }

    /**
     * fn-28.8 (G0 abort-takeoff foundation R12): the engine on [aircraftId]
     * has failed. Handler in `Step.kt::handleEngineFailure` sets
     * `AircraftState.engineRunning = false`; the next [PhysicsTick] applies
     * the engine-off clamp in `advanceKinematics` so the aircraft can decel
     * (target ≤ current) but cannot accel (target > current is blocked).
     *
     * **Instructor-channel causation** (memory:
     * `knowledge/decisions/instructor-channel-causation-for-sim-2026-05-16`):
     * test fixtures author engine-failure scenarios via the typed instructor
     * input `InstructorInput.EngineFailureAt(aircraftId, time)`, translated
     * to a pre-stamped `SimEvent.EngineFailure` via the fixture helper
     * `toInitialEvents(baseSeq)`. The instructor channel keeps the pilot
     * firewall clean: the failure observation enters the pilot's
     * decision branch (fn-28.9) as a cockpit input, NOT a world hook.
     *
     * [source] is fixed to [AgentId.System] (round-2: no
     * `AgentId.Instructor` variant introduced; emergency events sort with
     * other system-emitted events).
     *
     * **No synthetic wake event** (round-2 Major 4): the handler does NOT
     * emit a `PilotDecisionTick` of its own. The pilot's regular tick
     * cadence picks the engine-failure event up on the next scheduled
     * pilot tick (via the instructor-channel observation seam in fn-28.9);
     * a synthetic wake-up would couple sim event-production to pilot
     * decision-cadence — the same coupling the firewall plan deletes
     * elsewhere.
     *
     * **No severity enum, no `Emergency<T>` supertype** (round scope —
     * out of scope for fn-28.8): the v1 model is "engine has failed";
     * future ICING / FUEL_EXHAUSTION events land as sibling SimEvent
     * subtypes the same way, not via a parametric supertype.
     *
     * **Doctrine**: POH §3.3 (engine-failure-on-takeoff procedure) —
     * referenced for pilot-side branch reasoning in fn-28.9, not
     * modelled via RegDB at this task.
     */
    data class EngineFailure(
        override val time: SimTime,
        val aircraftId: AircraftId,
        override val seq: Long = 0,
    ) : SimEvent {
        // Body declaration (NOT a constructor parameter) so callers cannot
        // override [source]: emergency events are System-sourced by
        // architectural decision (round-2: no `AgentId.Instructor` variant
        // introduced). The instructor channel is test scaffolding; the
        // resulting sim event sorts with other system-emitted events.
        // Mirrors the same shape used by [PhysicsTick], [Spawn],
        // [TransmissionEnd], [MissedHandoffDetected], [FlightPlanFiled],
        // [AtisIssued] (all fixed-System events have `source` in the body,
        // not in the constructor).
        override val source: AgentId = AgentId.System
    }
}

private fun SpeakerRef.toAgentId(): AgentId = when (this) {
    is SpeakerRef.Pilot -> AgentId.Pilot(aircraftId)
    is SpeakerRef.Controller -> AgentId.Controller(id)
}

/**
 * Stamp [seq] onto an event. Used by [step] so every emission gets a fresh,
 * monotonic sequence number drawn from [SimState.seq].
 */
internal fun SimEvent.withSeq(s: Long): SimEvent = when (this) {
    is SimEvent.PhysicsTick -> copy(seq = s)
    is SimEvent.ControllerCycle -> copy(seq = s)
    is SimEvent.PilotDecisionTick -> copy(seq = s)
    is SimEvent.Spawn -> copy(seq = s)
    is SimEvent.TransmissionStart -> copy(seq = s)
    is SimEvent.TransmissionEnd -> copy(seq = s)
    is SimEvent.PilotProcessingComplete -> copy(seq = s)
    is SimEvent.MissedHandoffDetected -> copy(seq = s)
    is SimEvent.FlightPlanFiled -> copy(seq = s)
    is SimEvent.AtisIssued -> copy(seq = s)
    // fn-28.8 (R12): engine-failure events get the standard seq stamp.
    // Authored via the instructor channel (`InstructorInput.EngineFailureAt`
    // → fixture helper `toInitialEvents`) or, in future, by an in-sim
    // failure model — both go through `emit`, which calls `withSeq` here.
    is SimEvent.EngineFailure -> copy(seq = s)
}
