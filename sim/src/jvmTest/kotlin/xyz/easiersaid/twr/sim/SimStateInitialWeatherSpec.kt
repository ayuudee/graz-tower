package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.WeatherObservation
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport

/**
 * fn-16 (R5b-test + R5a regression + R4 happy-path): focused tests for
 * the two boundaries [SimState.initial] adds in fn-16 — the new
 * `WeatherForUnknownAerodrome` pre-fold check and the existing
 * `MissingWeatherForRunwayAerodrome` invariant, post-fold.
 *
 * Three cases:
 *  1. **R5b loud-failure**: a weather key absent from `world.aerodromes`
 *     returns [SimState.Companion.InitError.WeatherForUnknownAerodrome].
 *     Without this check, `updateAerodrome`'s no-op-on-absent-id
 *     semantics would silently drop the observation.
 *  2. **R5a regression**: a runway-bearing aerodrome whose weather is
 *     missing from the fold returns
 *     [SimState.Companion.InitError.MissingWeatherForRunwayAerodrome].
 *     Confirms the predicate's source-migration (map → entity) didn't
 *     break the existing invariant.
 *  3. **R4 happy path**: matching world + weather map produces a
 *     `SimState.right()` whose `world.aerodromes[id].weather ==
 *     observation` for every entry. Confirms the fold lands weather
 *     on the entity.
 */
class SimStateInitialWeatherSpec {

    private val lowg = AerodromeId("LOWG")
    private val ljmb = AerodromeId("LJMB")

    private val sampleWeather = WeatherObservation(
        wind = WindReport.Available(Wind.unsafe(directionDegrees = 90, speedKnots = 10)),
        qnh = null,
        visibility = null,
    )

    /** Minimal aerodrome with no runways — clears R5a vacuously. */
    private fun emptyAerodrome(id: AerodromeId): Aerodrome =
        Aerodrome(
            icao = id,
            elevation = Feet(1115),
            magneticVariation = Degrees(0.0),
            transitionAltitude = Level.AltitudeFeet.unsafe(5000),
        )

    /** Aerodrome with one runway — triggers R5a when weather is absent. */
    private fun runwayBearingAerodrome(id: AerodromeId): Aerodrome {
        val rwyId = RunwayId("${id.value}_RWY")
        val pA = PointId("${id.value}_PA")
        val pB = PointId("${id.value}_PB")
        return Aerodrome(
            icao = id,
            elevation = Feet(1115),
            magneticVariation = Degrees(0.0),
            transitionAltitude = Level.AltitudeFeet.unsafe(5000),
            runways = mapOf(
                rwyId to Runway(
                    id = rwyId,
                    path = Path(listOf(pA, pB)),
                    threshold = pA,
                ),
            ),
        )
    }

    @Test
    fun rejectsWeatherKeyAbsentFromWorld() {
        // World has LOWG (no runways) — passes R5a vacuously.
        val world = AviationWorld(aerodromes = mapOf(lowg to emptyAerodrome(lowg)))
        // Weather map carries a typo'd id (LJMB) that's not in the world.
        val typoId = ljmb
        val result = SimState.initial(
            seed = 42L,
            world = world,
            weatherByAerodrome = mapOf(typoId to sampleWeather),
        )
        val err = result.swap().getOrNull()
            ?: error("Expected WeatherForUnknownAerodrome.left() but got right: $result")
        assertEquals(
            SimState.Companion.InitError.WeatherForUnknownAerodrome(typoId),
            err,
            "fn-16 R5b: pre-fold check must surface the typo'd weather key",
        )
    }

    @Test
    fun rejectsRunwayBearingAerodromeWithoutWeather() {
        // World has one runway-bearing aerodrome; weather map is empty —
        // post-fold R5a check fires.
        val world = AviationWorld(aerodromes = mapOf(lowg to runwayBearingAerodrome(lowg)))
        val result = SimState.initial(
            seed = 42L,
            world = world,
            weatherByAerodrome = emptyMap(),
        )
        val err = result.swap().getOrNull()
            ?: error("Expected MissingWeatherForRunwayAerodrome.left() but got right: $result")
        assertEquals(
            SimState.Companion.InitError.MissingWeatherForRunwayAerodrome(lowg),
            err,
            "fn-16 R5a: post-fold check on aerodrome.weather == null preserves the invariant",
        )
    }

    @Test
    fun foldsWeatherOntoAerodromeOnHappyPath() {
        // Two aerodromes, one with runways (needs weather), one without
        // (no weather needed). Both get weather from the map — the
        // fold lands both observations on the entity.
        val world = AviationWorld(
            aerodromes = mapOf(
                lowg to runwayBearingAerodrome(lowg),
                ljmb to emptyAerodrome(ljmb),
            ),
        )
        val lowgWeather = sampleWeather
        val ljmbWeather = WeatherObservation(
            wind = WindReport.Available(Wind.unsafe(directionDegrees = 270, speedKnots = 5)),
            qnh = null,
            visibility = null,
        )
        val result = SimState.initial(
            seed = 42L,
            world = world,
            weatherByAerodrome = mapOf(lowg to lowgWeather, ljmb to ljmbWeather),
        )
        val state = result.getOrNull()
            ?: error("Expected SimState.right() but got left: $result")
        // R4: fold lands weather on the entity.
        assertEquals(
            lowgWeather,
            state.world.aerodromes[lowg]?.weather,
            "fn-16 R4: fold must set aerodrome.weather for LOWG",
        )
        assertEquals(
            ljmbWeather,
            state.world.aerodromes[ljmb]?.weather,
            "fn-16 R4: fold must set aerodrome.weather for LJMB",
        )
        // Sanity: every entry in the input map resolved on the world.
        assertTrue(
            state.world.aerodromes.values.all { it.weather != null },
            "every aerodrome in this fixture had a weather entry — none should be null post-fold",
        )
    }
}
