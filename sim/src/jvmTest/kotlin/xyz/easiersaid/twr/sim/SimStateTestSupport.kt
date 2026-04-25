package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.WindReport
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId

/**
 * Test-only helper: invoke [SimState.initial] and unwrap, failing the
 * test loudly with the typed [SimState.Companion.InitError] message.
 *
 * Production code uses [SimState.initial] directly and propagates
 * `Either<InitError, SimState>`. Tests use this helper so the
 * `getOrElse { error(...) }` pattern is not duplicated at every fixture.
 */
internal fun requireSimState(
    seed: Long,
    world: AviationWorld = AviationWorld(),
    worldIndex: WorldIndex = WorldIndex(),
    aircraft: List<AircraftState> = emptyList(),
    controllers: List<ControllerSpec> = emptyList(),
    weatherByAerodrome: Map<AerodromeId, WeatherObservation>,
): SimState = SimState.initial(
    seed = seed,
    world = world,
    worldIndex = worldIndex,
    aircraft = aircraft,
    controllers = controllers,
    weatherByAerodrome = weatherByAerodrome,
).getOrElse { error("Test fixture invalid: $it") }

/**
 * Test-only convenience: empty `WeatherObservation(null, null, null)` for
 * every aerodrome in the world. For tests that don't simulate weather but
 * have runway-bearing aerodromes (so the smart constructor refuses an
 * empty map).
 */
internal fun AviationWorld.unobservedWeather(): Map<AerodromeId, WeatherObservation> =
    aerodromes.keys.associateWith { WeatherObservation(wind = WindReport.NotReported, qnh = null, visibility = null) }
