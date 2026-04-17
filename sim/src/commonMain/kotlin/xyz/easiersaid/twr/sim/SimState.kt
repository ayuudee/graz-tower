package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.ReceivedMessage
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.SimTime

/**
 * Immutable simulation state snapshot.
 *
 * Everything the fold `(SimState, SimEvent) -> (SimState, List<SimEvent>)`
 * reads or writes lives here:
 *
 *   - [now] advances only when an event is dequeued.
 *   - [seq] is the monotonic event-sequence counter. [step] bumps it for
 *     every emitted event so the `(time, source, seq)` ordering is unique
 *     across an entire run.
 *   - [rng] is the seeded PRNG, threaded forward through each draw.
 *   - [aircraft] is stored in a [LinkedHashMap] so iteration order is
 *     deterministic (no HashMap iteration inside [step]).
 *   - [beliefs] is per-controller; slice 4a leaves this empty, slice 4c
 *     populates it as `controllerDecide` runs.
 *   - [world] and [worldIndex] are the immutable aviation world and its
 *     derived index; both are fixed for a run.
 */
data class SimState(
    val now: SimTime,
    val seq: Long,
    val rng: SimRandom,
    val aircraft: LinkedHashMap<AircraftId, AircraftState>,
    val controllers: Map<ControllerId, ControllerSpec>,
    val beliefs: Map<ControllerId, BeliefState>,
    val world: AviationWorld,
    val worldIndex: WorldIndex,
    val inFlightTransmissions: Map<TransmissionId, InFlightTransmission> = emptyMap(),
    val nextTransmissionId: Long = 0L,
    val controllerInbox: Map<ControllerId, List<ReceivedMessage>> = emptyMap(),
) {
    companion object {
        fun initial(
            seed: Long,
            world: AviationWorld = AviationWorld(),
            worldIndex: WorldIndex = WorldIndex(),
            aircraft: List<AircraftState> = emptyList(),
            controllers: List<ControllerSpec> = emptyList(),
        ): SimState = SimState(
            now = SimTime.ZERO,
            seq = 0L,
            rng = SimRandom.ofSeed(seed),
            aircraft = LinkedHashMap<AircraftId, AircraftState>().apply {
                aircraft.forEach { put(it.id, it) }
            },
            controllers = controllers.associateBy { it.id },
            beliefs = emptyMap(),
            world = world,
            worldIndex = worldIndex,
            inFlightTransmissions = emptyMap(),
            nextTransmissionId = 0L,
            controllerInbox = emptyMap(),
        )
    }
}
