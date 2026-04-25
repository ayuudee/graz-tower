package xyz.easiersaid.twr.sim

import arrow.core.Either
import arrow.core.left
import arrow.core.right
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
) {
    companion object {
        /**
         * Validation errors raised by the [initial] smart constructor.
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
        }

        /**
         * Validating constructor. Refuses worlds where any aerodrome with
         * runways has no [WeatherObservation] entry.
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
    }
}
