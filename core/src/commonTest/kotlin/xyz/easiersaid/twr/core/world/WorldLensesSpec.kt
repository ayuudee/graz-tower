package xyz.easiersaid.twr.core.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport

/**
 * fn-16 (R3): unit tests for [updateAerodrome] — the single-aerodrome
 * lens helper added alongside [Aerodrome.weather].
 *
 * Three cases pin the contract:
 *  1. Updates an existing aerodrome with the transform's output.
 *  2. Returns the input world unchanged when the id is absent (no-op).
 *  3. Returns the input world unchanged (identity-equal) when the
 *     transform itself is identity — preserves structural sharing.
 */
class WorldLensesSpec {

    private val lowg = AerodromeId("LOWG")
    private val ljmb = AerodromeId("LJMB")

    private fun aerodrome(id: AerodromeId): Aerodrome =
        Aerodrome(
            icao = id,
            elevation = Feet(1115),
            magneticVariation = Degrees(0.0),
            transitionAltitude = Level.AltitudeFeet.unsafe(5000),
        )

    private val sampleWeather = WeatherObservation(
        wind = WindReport.Available(Wind.unsafe(directionDegrees = 90, speedKnots = 10)),
        qnh = null,
        visibility = null,
    )

    @Test
    fun updateAerodromeAppliesTransformToExistingId() {
        val world = AviationWorld(aerodromes = mapOf(lowg to aerodrome(lowg)))
        val updated = world.updateAerodrome(lowg) { it.copy(weather = sampleWeather) }
        assertEquals(sampleWeather, updated.aerodromes[lowg]?.weather)
    }

    @Test
    fun updateAerodromeIsNoOpForAbsentId() {
        val world = AviationWorld(aerodromes = mapOf(lowg to aerodrome(lowg)))
        val updated = world.updateAerodrome(ljmb) { it.copy(weather = sampleWeather) }
        // Referentially equal — no rebuild happened.
        assertSame(world, updated)
    }

    @Test
    fun updateAerodromeIsNoOpForIdentityTransform() {
        val world = AviationWorld(aerodromes = mapOf(lowg to aerodrome(lowg)))
        val updated = world.updateAerodrome(lowg) { it }
        // Identity transform returns the same Aerodrome instance, so the
        // lens short-circuits without rebuilding the world map.
        assertSame(world, updated)
    }
}
