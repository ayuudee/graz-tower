package xyz.easiersaid.twr.pilot

import kotlin.math.roundToInt
import xyz.easiersaid.twr.protocol.Feet
import xyz.easiersaid.twr.protocol.PressureSetting

/**
 * fn-28.2 (G3a-react-density-altitude R17): pure-function density-altitude
 * computation. **Residency in `:pilot`** (round-5 Major 1): both this
 * formula AND its input type [DensityAltitudeInput] live in `:pilot` to
 * avoid a cyclic dependency — `:protocol` does NOT depend on `:pilot`, so
 * placing the formula in `:protocol` while [DensityAltitudeInput] stays
 * in `:pilot` would force the typed shape to migrate too, and other
 * `:pilot`-only consumers (e.g. the firewall projection) would lose the
 * structural locality. The formula's reads are typed-units only
 * ([Feet] / [PressureSetting] / [xyz.easiersaid.twr.protocol.Temperature])
 * — `:protocol` exports every type used here.
 *
 * **Formula** (FAA AC 61-107B §3-1 / "Density Altitude" technique, the
 * standard pilot's-rule-of-thumb formula taught in training):
 *
 * ```
 * pressure_altitude_ft = field_elevation_ft + (1013.25 - qnh_hPa) * 30
 * isa_temperature_c    = 15.0 - (field_elevation_ft / 1000.0) * 1.98
 * density_altitude_ft  = pressure_altitude_ft + 120 * (oat_c - isa_temperature_c)
 * ```
 *
 * **Constants** (named for review readability; values are doctrine-anchored):
 *  - 1013.25 hPa — ISA standard pressure (mean sea level, dry air at 15°C).
 *  - 30 ft/hPa — the standard "altimeter setting → pressure altitude"
 *    conversion factor (≈ 27 ft/hPa true, rounded to 30 for the pilot's
 *    rule-of-thumb formula).
 *  - 15°C — ISA standard temperature at sea level.
 *  - 1.98 °C/1000 ft — ISA temperature lapse rate (≈ 2°C per 1000 ft).
 *  - 120 ft/°C — the standard "ISA deviation → density altitude correction"
 *    coefficient (light-GA pilot's rule of thumb).
 *
 * **Rounding**: integer feet at the boundary. The internal computation is
 * Double; [kotlin.math.roundToInt] rounds half-away-from-zero (positive
 * boundary values round up: 4999.5 → 5000), matching the pilot's
 * pre-flight chart-reading rounding. [Feet.unsafe] then wraps the
 * Int — [Feet]'s smart-constructor invariant (`value >= 0`) holds for
 * surface DA values; only edge-case extreme negative-DA scenarios
 * (high-altitude high-pressure cold-front, where DA falls below MSL)
 * would surface a negative value — `roundToInt` produces 0 for such
 * cases via the clamp below, fail-closed.
 *
 * **Edge-case clamp** (fail-closed at the formula boundary, not the
 * recognition site): negative DA values are clamped to 0 ft. A real
 * pilot does not "decline departure due to negative DA" — the
 * operational concern is high DA, low DA is performance margin. The
 * clamp keeps [Feet]'s `value >= 0` invariant satisfied without
 * surfacing an exception to callers. Recognition gates on
 * `da > maxDensityAltitudeFt`, so a clamped 0 ft never trips the
 * decline trigger.
 *
 * **Consumer** (fn-28.2 / fn-28.3): the pilot's reactive DA-decline
 * recognition branch — `deriveDensityAltitudeEvent` in
 * `:pilot/observe/PilotEvent.kt` — calls this function with the
 * resolved [DensityAltitudeInput] and compares the result against
 * `aircraft.type.maxDensityAltitudeFt`. The fn-28.3 sim-golden asserts
 * against the function's output (not against a hand-computed prose value)
 * — single source of truth for the DA computation.
 *
 * **Pure**: no side effects, no time dependency, no I/O. Same inputs →
 * same output, every call.
 *
 * **Doctrine**: FAA AC 61-107B §3-1 (high-DA operating considerations —
 * the modelling anchor); FAA Pilot's Handbook of Aeronautical Knowledge
 * (FAA-H-8083-25C) Ch 4 (atmosphere — ISA constants); FAA AFH Ch 11
 * (high-DA takeoff performance). Per-type doctrinal severity asymmetry
 * (C172 = 5000 ft threshold; B738 = no threshold) lives in
 * [xyz.easiersaid.twr.protocol.AircraftType.maxDensityAltitudeFt] KDoc;
 * the formula itself is type-agnostic.
 */
fun computeDensityAltitudeFeet(input: DensityAltitudeInput): Feet {
    val fieldElevationFt: Double = input.fieldElevation.value.toDouble()
    val oatCelsius: Double = input.oat.celsius

    // QNH narrowing: v1 supports the `QnhHpa` leaf only. `Standard`
    // (29.92 in / 1013.25 hPa) and `QfeHpa` are unsupported — fail-closed
    // at the formula site by clamping QNH to ISA (1013.25), which makes
    // the pressure-altitude term collapse to `field_elevation_ft` exactly.
    // This is the operationally correct behaviour for `Standard` (the
    // standard altimeter setting IS ISA); `QfeHpa` v1 behaviour is
    // intentionally weakened (filed as deferment — QFE is rare in
    // VFR-light-GA training scenarios fn-28 targets).
    val qnhHpa: Double = when (val qnh = input.qnh) {
        is PressureSetting.QnhHpa -> qnh.value.toDouble()
        is PressureSetting.QfeHpa -> ISA_PRESSURE_HPA  // fail-closed: collapse to ISA (v1 simplification)
        is PressureSetting.Standard -> ISA_PRESSURE_HPA
    }

    val pressureAltitudeFt = fieldElevationFt + (ISA_PRESSURE_HPA - qnhHpa) * FT_PER_HPA
    val isaTemperatureC = ISA_TEMP_CELSIUS_SL - (fieldElevationFt / 1000.0) * ISA_LAPSE_RATE_CELSIUS_PER_KFT
    val densityAltitudeFt = pressureAltitudeFt + DA_FT_PER_ISA_DEVIATION_C * (oatCelsius - isaTemperatureC)

    val rounded = densityAltitudeFt.roundToInt()
    // Edge-case clamp: surface negative DA → 0 ft. Real-world negative DA
    // (high-pressure cold-front aerodromes) is performance margin, not a
    // decline trigger; clamping keeps [Feet.value >= 0] without exception.
    return Feet.unsafe(if (rounded < 0) 0 else rounded)
}

/** ISA standard pressure at mean sea level (dry air at 15°C). */
internal const val ISA_PRESSURE_HPA: Double = 1013.25

/** Standard altimeter-setting → pressure-altitude conversion factor (rule of thumb). */
internal const val FT_PER_HPA: Double = 30.0

/** ISA standard temperature at sea level (°C). */
internal const val ISA_TEMP_CELSIUS_SL: Double = 15.0

/** ISA temperature lapse rate (°C per 1000 ft). */
internal const val ISA_LAPSE_RATE_CELSIUS_PER_KFT: Double = 1.98

/** DA correction coefficient (ft per °C of ISA deviation) — light-GA rule of thumb. */
internal const val DA_FT_PER_ISA_DEVIATION_C: Double = 120.0
