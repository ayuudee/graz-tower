package xyz.easiersaid.twr.core.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.Temperature
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport

/**
 * fn-28.1 (G3a-react-density-altitude foundation A): core-side type
 * tests for the [WeatherObservation] reshape — the new [oat] field is
 * appended AFTER [WeatherObservation.visibility] with default `null`,
 * preserving every existing positional constructor call site.
 *
 * Test scope (per task spec):
 *  - Shape: default-null preserves the pre-fn-28.1 `(wind, qnh, visibility)`
 *    positional construction.
 *  - Shape: concrete [Temperature] flows through to the field.
 *  - Type test: [Temperature] smart-constructor invariant fail-closes on
 *    NaN / infinite / out-of-range values (lives in
 *    `protocol/src/commonTest/.../TemperatureSpec.kt` for module locality).
 *
 * The end-to-end projection + fail-closed shape (OAT/QNH null → no
 * `DensityAltitudeInput` map entry) lives in `:sim`'s
 * `ProjectionDensityAltitudeInputSpec` per the task's test split
 * (`:core` for type shape; `:sim`/`:pilot` for projection + firewall).
 */
class WeatherObservationSpec {

    @Test
    fun `oat defaults to null when not supplied — positional constructor preserved`() {
        // Pre-fn-28.1 positional shape: (wind, qnh, visibility).
        // The new `oat` field with default-null must not break this call site.
        val observation = WeatherObservation(
            WindReport.Available(Wind.unsafe(directionDegrees = 160, speedKnots = 8)),
            null,
            null,
        )
        assertNull(observation.oat, "positional constructor must preserve default-null oat")
    }

    @Test
    fun `concrete oat flows through to the field`() {
        val oat = Temperature.celsius(12.79)
        val observation = WeatherObservation(
            wind = WindReport.Available(Wind.unsafe(directionDegrees = 160, speedKnots = 8)),
            qnh = PressureSetting.QnhHpa.unsafe(1013),
            visibility = null,
            oat = oat,
        )
        assertNotNull(observation.oat)
        assertEquals(12.79, observation.oat?.celsius)
    }

    @Test
    fun `wind qnh visibility unchanged by oat extension`() {
        val wind = WindReport.Available(Wind.unsafe(directionDegrees = 270, speedKnots = 5))
        val qnh = PressureSetting.QnhHpa.unsafe(1020)
        val observation = WeatherObservation(
            wind = wind,
            qnh = qnh,
            visibility = 10,
            oat = Temperature.celsius(20.0),
        )
        assertEquals(wind, observation.wind)
        assertEquals(qnh, observation.qnh)
        assertEquals(10, observation.visibility)
    }
}
