package xyz.easiersaid.twr.protocol

import arrow.core.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * fn-28.1 (G3a-react-density-altitude foundation A): smart-constructor
 * invariant tests for the [Temperature] typed datum.
 *
 * Test scope (per task spec — type tests for the new typed-unit):
 *  - `invoke` returns Right for finite, in-range values.
 *  - `invoke` returns Left for NaN / infinite values.
 *  - `invoke` returns Left for values outside `[MIN_CELSIUS, MAX_CELSIUS]`.
 *  - `celsius(unsafe)` returns the value for valid inputs; throws on
 *    invalid inputs.
 *  - Inclusive boundary behaviour at MIN/MAX.
 *
 * The `:core` [xyz.easiersaid.twr.core.world.WeatherObservation] field
 * reshape (oat default-null + concrete-value flow-through) is in
 * `WeatherObservationSpec`. The projection + fail-closed shape lives in
 * `:sim`'s `ProjectionDensityAltitudeInputSpec`.
 */
class TemperatureSpec {

    @Test
    fun `invoke returns Right for in-range positive value`() {
        val result = Temperature(20.0)
        assertTrue(result is Either.Right, "in-range value must return Right")
        assertEquals(20.0, result.value.celsius)
    }

    @Test
    fun `invoke returns Right for in-range negative value`() {
        val result = Temperature(-40.0)
        assertTrue(result is Either.Right)
        assertEquals(-40.0, result.value.celsius)
    }

    @Test
    fun `invoke accepts inclusive MIN_CELSIUS boundary`() {
        val result = Temperature(Temperature.MIN_CELSIUS)
        assertTrue(result is Either.Right, "MIN_CELSIUS must be inclusive")
    }

    @Test
    fun `invoke accepts inclusive MAX_CELSIUS boundary`() {
        val result = Temperature(Temperature.MAX_CELSIUS)
        assertTrue(result is Either.Right, "MAX_CELSIUS must be inclusive")
    }

    @Test
    fun `invoke returns Left for below MIN_CELSIUS`() {
        val result = Temperature(Temperature.MIN_CELSIUS - 0.1)
        assertTrue(result is Either.Left)
    }

    @Test
    fun `invoke returns Left for above MAX_CELSIUS`() {
        val result = Temperature(Temperature.MAX_CELSIUS + 0.1)
        assertTrue(result is Either.Left)
    }

    @Test
    fun `invoke returns Left for NaN`() {
        val result = Temperature(Double.NaN)
        assertTrue(result is Either.Left, "NaN must fail finiteness check")
    }

    @Test
    fun `invoke returns Left for positive infinity`() {
        val result = Temperature(Double.POSITIVE_INFINITY)
        assertTrue(result is Either.Left)
    }

    @Test
    fun `invoke returns Left for negative infinity`() {
        val result = Temperature(Double.NEGATIVE_INFINITY)
        assertTrue(result is Either.Left)
    }

    @Test
    fun `celsius unsafe returns the value for valid inputs`() {
        val temp = Temperature.celsius(12.79)
        assertEquals(12.79, temp.celsius)
    }

    @Test
    fun `celsius unsafe throws for out-of-range value`() {
        assertFailsWith<IllegalStateException> {
            Temperature.celsius(Temperature.MAX_CELSIUS + 1.0)
        }
    }

    @Test
    fun `celsius unsafe throws for NaN`() {
        assertFailsWith<IllegalStateException> {
            Temperature.celsius(Double.NaN)
        }
    }
}
