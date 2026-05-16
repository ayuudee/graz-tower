package xyz.easiersaid.twr.protocol

/**
 * Outside air temperature (OAT) typed datum, fn-28.1
 * (G3a-react-density-altitude foundation A).
 *
 * Stored as **degrees Celsius** (the unit used by ATIS broadcasts per ICAO
 * Annex 11 §4.3.6.h and by performance charts in light-GA POHs). The value
 * is a [Double] because OAT is reported with decimal precision on ATIS in
 * many jurisdictions (e.g. METAR `M03/M07` integer, but performance
 * computations interpolate to one decimal) and because the
 * density-altitude formula
 *
 *     da_ft = pressure_altitude_ft + 120 * (oat_c - isa_temp_c)
 *
 * compounds rounding error if OAT is truncated to an integer.
 *
 * **Smart-constructor invariant**: the value must be finite and within a
 * physically plausible atmospheric range for surface OAT
 * (`-90.0 ≤ celsius ≤ 60.0`). The bounds are loose:
 *
 *  - Vostok Station (Antarctica) record low: −89.2 °C (1983).
 *  - Furnace Creek (Death Valley) record high: 56.7 °C (1913).
 *
 * Out-of-range values indicate a sensor or fixture defect, not real OAT;
 * the smart constructor surfaces those as `Left(reason)` so callers can
 * choose between `unsafe` (test fixtures with concrete numeric literals)
 * and `invoke` (loader paths that may receive malformed input).
 *
 * **Consumers** (fn-28 trajectory):
 *  - fn-28.1: [xyz.easiersaid.twr.core.world.WeatherObservation.oat] —
 *    appended AFTER `visibility` with default `null`; `:sim` projects the
 *    concrete value into the typed [DensityAltitudeInput] when both OAT
 *    and QNH are non-null on the aerodrome's weather entry.
 *  - fn-28.1: [Atis] OAT slot (audit/extend per ICAO Annex 11 §4.3.6.h).
 *  - fn-28.1: `DensityAltitudeInput.oat` in `:pilot` — typed projection
 *    that crosses the pilot firewall.
 *  - fn-28.2: `computeDensityAltitudeFeet(input)` consumes `oat.celsius`
 *    directly (no further conversion).
 *
 * **Pattern**: smart-constructor with `invoke` (Either) and `unsafe`
 * (throws), mirroring sibling typed units [Knots] / [Mach] /
 * [PressureSetting.QnhHpa]. New constructions inside trusted code paths
 * (fixtures, computed-from-fixture ISA values) use `unsafe`; loader paths
 * use `invoke` so malformed input surfaces as `Either.Left`.
 *
 * **Doctrine**:
 *  - ICAO Annex 11 §4.3.6.h (ATIS broadcast content — air temperature).
 *  - ICAO Doc 4444 §4.5.5 (ATIS content; equivalent OAT list).
 *  - FAA AC 61-107B §3-1 (density-altitude operating considerations —
 *    consumed in fn-28.2 via `AircraftType.maxDensityAltitudeFt`).
 */
@ConsistentCopyVisibility
data class Temperature private constructor(val celsius: Double) {
    companion object {
        /** Inclusive lower bound for surface OAT (°C) — Vostok 1983 record low. */
        const val MIN_CELSIUS: Double = -90.0

        /** Inclusive upper bound for surface OAT (°C) — Death Valley 1913 record high. */
        const val MAX_CELSIUS: Double = 60.0

        operator fun invoke(celsius: Double): arrow.core.Either<String, Temperature> =
            when {
                !celsius.isFinite() ->
                    arrow.core.Either.Left("Temperature must be finite: $celsius")
                celsius !in MIN_CELSIUS..MAX_CELSIUS ->
                    arrow.core.Either.Left(
                        "Temperature must be in [$MIN_CELSIUS, $MAX_CELSIUS] °C: $celsius"
                    )
                else -> arrow.core.Either.Right(Temperature(celsius))
            }

        /**
         * Trusted-call-site variant — for test fixtures and other
         * compile-time-literal call sites. Throws on the same invariant
         * the [invoke] form surfaces as `Either.Left`.
         */
        fun celsius(value: Double): Temperature =
            invoke(value).fold({ error(it) }, { it })
    }
}
