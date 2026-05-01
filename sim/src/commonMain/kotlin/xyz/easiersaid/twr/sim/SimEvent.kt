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
}
