package xyz.easiersaid.twr.sim

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.controller.ReceivedMessage
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
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
            return SimState(
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
