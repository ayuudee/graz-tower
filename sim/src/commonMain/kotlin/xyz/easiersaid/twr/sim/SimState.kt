package xyz.easiersaid.twr.sim

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.controller.ReceivedMessage
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.RunwayObstruction
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.RunwayId
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
    /**
     * fn-8.1: per-aircraft splittable PRNG state, seeded once per aircraft
     * via [SimRandom.split] keyed by `AircraftId.value` in [initial] (and
     * mid-sim by [xyz.easiersaid.twr.sim.handleSpawn]).
     *
     * **Determinism contract.** Order-of-dispatch invariance for same
     * aircraft IDs: swapping the within-tick scheduling order of aircraft
     * A and B leaves each aircraft's draws unchanged. (Each id has its own
     * stream, independent of the parent's future advances.) Changing an
     * aircraft's id intentionally produces a different child stream — the
     * key *is* the id.
     *
     * **Invariant**: every key in [aircraft] has a matching entry here.
     * [initial] enforces this at construction; spawn/despawn paths must
     * preserve it. Helpers [aircraftRng] / [withAircraftRng] are the only
     * call-site shape for read/write — they fail loud on missing-entry.
     *
     * The shared [rng] field above is preserved for non-aircraft-scoped
     * randomness (weather, ATIS letter rotation, anything not keyed by
     * aircraft).
     */
    val rngByAircraft: Map<AircraftId, SimRandom>,
    val aircraft: LinkedHashMap<AircraftId, AircraftState>,
    val controllers: Map<ControllerId, ControllerSpec>,
    val beliefs: Map<ControllerId, BeliefState>,
    val world: AviationWorld,
    val worldIndex: WorldIndex,
    val weatherByAerodrome: Map<AerodromeId, WeatherObservation>,
    val inFlightTransmissions: Map<TransmissionId, InFlightTransmission> = emptyMap(),
    val nextTransmissionId: Long = 0L,
    val controllerInbox: Map<ControllerId, List<ReceivedMessage>> = emptyMap(),
    /**
     * Pass 9 (D-AUDIT.2 / Phase 9.B): re-fire dampening for
     * [sweepHandoffTimeouts]. Last `MissedHandoffDetected` emission time
     * per [HandoffEscalationKey]. The sweep produces at most one event
     * per key per `MISSED_HANDOFF_TIMEOUT` window.
     *
     * **Single writer**: `sweepHandoffTimeouts` (sets the entry on emit).
     * **Clear sites**:
     *  - [applyTwoWayCommsEstablished] — Watching → Owned, sender's
     *    HandingOff entry removed; clear the matching key.
     */
    val handoffEscalations: Map<HandoffEscalationKey, SimTime> = emptyMap(),
    /**
     * Pass 15 (D-AUDIT.8 closure): per-aerodrome ATIS broadcast. Set
     * by [SimEvent.AtisIssued] handler; read by [ControllerWiring]
     * for [ControllerView.atis] projection and by [PilotInput] for
     * lazy ATIS letter read at first contact.
     *
     * **Single writer**: `Step.handleAtisIssued`.
     * **Multi-reader**: projection layer only — rule layer in
     * `:controller/commonMain` reads the projected `view.atis`, not
     * this field directly.
     */
    val atisByAerodrome: Map<xyz.easiersaid.twr.protocol.AerodromeId, xyz.easiersaid.twr.protocol.Atis> = emptyMap(),
    /**
     * fn-8.3 Phase 3 (B4 closure): per-aircraft "radio not free until" timestamp.
     *
     * Tracks the `endsAt` of the aircraft's most recently emitted pilot
     * transmission so the **next** pilot tick can defer a fresh transmission
     * until the radio is free, rather than re-using the same `proposedStart`
     * the prior tick computed (a stale read of [inFlightTransmissions] before
     * the prior tick's [SimEvent.TransmissionStart] has been processed).
     *
     * Real-ATC parallel: a pilot whose PTT is still active will not begin a
     * new transmission until they release the previous one. The audio panel
     * tells them they're transmitting. Without this field, two consecutive
     * pilot ticks (1s apart) generated by the same aircraft can both compute
     * `proposedStart = freeAt` against the SAME stale state — the prior
     * tick's ift hasn't been applied to [inFlightTransmissions] yet because
     * its `TransmissionStart` event sits in the queue at the deferred
     * `startedAt` time. The two transmissions then collide (same start, same
     * frequency) and both get marked stepped-on; the radio carries silence
     * instead of two sequential reports.
     *
     * **Single writer**: [Step.handlePilotTick] (set after each emission).
     * Other pilot-emission paths (Step.kt:1132 readback, line 1156
     * InitialContact, line 1210 respond-correction) emit at most one tx per
     * call and gate on [pilotFrequencyFreeFrom] which already reflects state
     * at their call time, so they don't need the per-aircraft tracker.
     */
    val pilotRadioFreeAt: Map<AircraftId, SimTime> = emptyMap(),
    /**
     * fn-12 (R3b): per-controller snapshot of the obstructions visible to
     * that controller AS OF the prior controller cycle. Updated at the
     * end of each controller cycle (after the world-diff producer runs)
     * to reflect that controller's currently-known obstruction state for
     * the next cycle's diff.
     *
     * Per-controller (not per-aerodrome) because the diff is per-cycle
     * per-controller and a controller's "what did I see last cycle" is
     * its own state. Empty initially (no prior cycle); populated lazily
     * as the world authors obstructions and the diff producer runs.
     *
     * Only obstruction-state-bearing runways have entries (null
     * obstructions are filtered out at snapshot time — the diff
     * producer treats absent keys as `None`, which is structurally the
     * same).
     */
    val priorObstructionsByController: Map<ControllerId, Map<RunwayId, RunwayObstruction>> = emptyMap(),
) {
    companion object {
        /**
         * Validation errors raised by the [initial] smart constructor. Each
         * variant names a specific invariant the rest of the simulation relies
         * on. The constructor refuses to build a state that violates any of
         * them — silent defaults at construction time are exactly the kind of
         * "looks fine, hangs at runtime" failure mode the no-corners rule
         * forbids.
         */
        sealed interface InitError {
            /**
             * The world has at least one [AerodromeId] with runways but no
             * weather entry. Without weather the controller's runway-into-wind
             * selection silently picks a default; smart-constructor refuses
             * to create such a state.
             */
            data class MissingWeatherForRunwayAerodrome(
                val aerodromeId: AerodromeId,
            ) : InitError

            /**
             * Two aircraft share the same [AircraftId]. The
             * `LinkedHashMap.put` fold would silently overwrite, leaving the
             * caller with a state containing the *second* aircraft of the pair.
             */
            data class DuplicateAircraftId(val id: AircraftId) : InitError

            /**
             * An aircraft's [AircraftState.positionPoint] is not in
             * [WorldIndex.positions]. The kinematics layer would tolerate this
             * by freezing the aircraft (`worldIndex.positions[headPoint] ?:
             * return ac.copy(...)`), masking a bad spawn fixture.
             */
            data class AircraftPositionPointNotInIndex(
                val aircraftId: AircraftId,
                val point: xyz.easiersaid.twr.protocol.PointId,
            ) : InitError

            /**
             * Two controllers share the same [ControllerId]. As with aircraft,
             * `associateBy` would silently overwrite.
             */
            data class DuplicateControllerId(val id: ControllerId) : InitError

            /**
             * A controller's [ControllerSpec.aerodromeId] is not present in
             * [AviationWorld.aerodromes]. `applyContactFrequency` looks up
             * controllers by `(aerodromeId, role)`; an off-world controller
             * silently never receives handoffs.
             */
            data class ControllerAerodromeNotInWorld(
                val controllerId: ControllerId,
                val aerodromeId: AerodromeId,
            ) : InitError

            /**
             * A controller has an aircraft in its [ControllerSpec.responsibilities]
             * that is not in the [aircraft] list. Belief reconciliation would
             * carry a dangling commitment forever.
             */
            data class ResponsibilityForUnknownAircraft(
                val controllerId: ControllerId,
                val aircraftId: AircraftId,
            ) : InitError
        }

        /**
         * Validating constructor. Each [InitError] variant in turn:
         *  - every runway-bearing aerodrome has a [WeatherObservation],
         *  - every [AircraftId] in [aircraft] is unique,
         *  - every aircraft's `positionPoint` is in `worldIndex.positions`,
         *  - every [ControllerId] in [controllers] is unique,
         *  - every controller's aerodrome exists in [world],
         *  - every aircraft a controller claims responsibility for exists in [aircraft].
         *
         * Tests that genuinely don't simulate weather pass an explicit
         * `WeatherObservation(wind = WindReport.NotReported, qnh = null, visibility = null)`
         * for each runway-bearing aerodrome.
         */
        fun initial(
            seed: Long,
            world: AviationWorld = AviationWorld(),
            worldIndex: WorldIndex = WorldIndex(),
            aircraft: List<AircraftState> = emptyList(),
            controllers: List<ControllerSpec> = emptyList(),
            weatherByAerodrome: Map<AerodromeId, WeatherObservation>,
        ): Either<InitError, SimState> {
            for ((aerodromeId, aerodrome) in world.aerodromes) {
                if (aerodrome.runways.isNotEmpty() && aerodromeId !in weatherByAerodrome) {
                    return InitError.MissingWeatherForRunwayAerodrome(aerodromeId).left()
                }
            }
            val aircraftIds = mutableSetOf<AircraftId>()
            for (ac in aircraft) {
                if (!aircraftIds.add(ac.id)) {
                    return InitError.DuplicateAircraftId(ac.id).left()
                }
                if (ac.positionPoint !in worldIndex.positions) {
                    return InitError.AircraftPositionPointNotInIndex(ac.id, ac.positionPoint).left()
                }
            }
            validateControllers(controllers, world, aircraftIds)?.let { return it.left() }
            // fn-8.1: seed per-aircraft RNG state. Sort by AircraftId.value
            // ascending so the seeding pass is deterministic regardless of
            // the caller's input list order. Each child stream is split from
            // the same `rootRng` keyed by id; SimRandom.split does not
            // advance the parent, so all per-aircraft streams are independent.
            val rootRng = SimRandom.ofSeed(seed)
            val rngByAircraft = aircraft
                .sortedBy { it.id.value }
                .associate { it.id to rootRng.split(it.id.value) }
            return SimState(
                now = SimTime.ZERO,
                seq = 0L,
                rng = rootRng,
                rngByAircraft = rngByAircraft,
                aircraft = LinkedHashMap<AircraftId, AircraftState>().apply {
                    aircraft.forEach { put(it.id, it) }
                },
                controllers = controllers.associateBy { it.id },
                beliefs = emptyMap(),
                world = world,
                worldIndex = worldIndex,
                weatherByAerodrome = weatherByAerodrome,
                inFlightTransmissions = emptyMap(),
                nextTransmissionId = 0L,
                controllerInbox = emptyMap(),
            ).right()
        }

        /**
         * Per-controller validation extracted out of [initial] so the latter
         * stays under the detekt return-count limit. Returns the first error
         * encountered (if any), or null if every controller is well-formed.
         */
        private fun validateControllers(
            controllers: List<ControllerSpec>,
            world: AviationWorld,
            aircraftIds: Set<AircraftId>,
        ): InitError? {
            val controllerIds = mutableSetOf<ControllerId>()
            for (controller in controllers) {
                if (!controllerIds.add(controller.id)) {
                    return InitError.DuplicateControllerId(controller.id)
                }
                if (controller.aerodromeId !in world.aerodromes) {
                    return InitError.ControllerAerodromeNotInWorld(controller.id, controller.aerodromeId)
                }
                for (responsibilityId in controller.responsibilities.keys) {
                    if (responsibilityId !in aircraftIds) {
                        return InitError.ResponsibilityForUnknownAircraft(controller.id, responsibilityId)
                    }
                }
            }
            return null
        }
    }
}

/**
 * Pass 9 (D-AUDIT.2 / Phase 9.B): identity of a (sender, aircraft) handoff-
 * escalation tracking pair. `sender` is the stable identity — the
 * receiving controller (`Watching.target`) can shift between coordination
 * cancellations; the sender's `HandingOff` persists until two-way comms
 * resolves.
 *
 * Typed key (not `Pair<ControllerId, AircraftId>`) — Pass 7 review pushed
 * back on untyped pair-as-domain-relationship for `responsibilities`;
 * don't pay it again.
 */
data class HandoffEscalationKey(
    val sender: ControllerId,
    val aircraft: AircraftId,
)

/**
 * fn-8.1: read the per-aircraft RNG. Fails loud on missing-entry rather
 * than returning a default — every aircraft in [SimState.aircraft] is
 * required by invariant to have a [SimState.rngByAircraft] entry. A miss
 * is a wiring defect (init / spawn path didn't seed) and the test should
 * hard-fail with the actual cause.
 */
fun SimState.aircraftRng(id: AircraftId): SimRandom =
    rngByAircraft[id]
        ?: error(
            "aircraftRng: $id has no RNG entry — invariant violated. " +
                "Every key in state.aircraft must have a matching " +
                "rngByAircraft entry. Check SimState.initial / handleSpawn.",
        )

/**
 * fn-8.1: write back the per-aircraft RNG after a sampling site advances
 * the child stream. Mirrors `state.copy(rng = newRng)` shape for the
 * shared RNG, but keyed by aircraft.
 *
 * Returns a fresh [SimState] with the entry replaced. Adding a new key
 * (i.e. mid-sim spawn) is also valid — [handleSpawn] uses this shape.
 */
fun SimState.withAircraftRng(id: AircraftId, newRng: SimRandom): SimState =
    copy(rngByAircraft = rngByAircraft + (id to newRng))
