package xyz.easiersaid.twr.pilot.observe

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Compute the crosswind component (knots) of a steady-state wind
 * against a runway heading.
 *
 * **Inputs** (all in **the same reference frame — Magnetic FROM-degrees**;
 * see [xyz.easiersaid.twr.protocol.Wind] KDoc):
 *  - [windFromMagnetic] — wind FROM direction, `0..360`. `360` is
 *    accepted as the aviation-convention spelling of due North.
 *  - [windSpeedKnots] — steady-state wind speed in knots; non-negative.
 *    Zero is a valid input (calm wind → crosswind 0.0).
 *  - [runwayHeadingMagnetic] — runway magnetic heading, typically the
 *    return value of [xyz.easiersaid.twr.protocol.headingDegreesMagnetic].
 *    Pre-validated to `10..360` by that helper; this function does not
 *    re-validate.
 *
 * **Formula** (FAA AIM §7-1-12, ICAO Annex 3):
 * ```
 *     θ_signed = ((windFromMagnetic − runwayHeadingMagnetic + 540) mod 360) − 180
 *     crosswind = |sin(θ_signed × π / 180)| × windSpeedKnots
 * ```
 * The `+540 mod 360 −180` wraparound normalises the angle into
 * `[−180, 180]` so that `350° vs 010°` (relative angle −20°, not 340°)
 * is handled correctly. `|sin|` collapses the left/right symmetry —
 * v1 treats left and right crosswind equivalently (POH limit is
 * symmetric).
 *
 * **Return type — Double, no truncation**: a crosswind of `15.9 kt`
 * against a `15 kt` POH limit must fire the recognition; truncating to
 * `15` would silently mask the exceedance. Callers compare to
 * `aircraftType.maxCrosswindKnots.value.toDouble()`. The positive-only
 * [xyz.easiersaid.twr.protocol.Knots] type is **not** appropriate here
 * because (a) `0.0` is a valid value (dead headwind / calm) and (b) the
 * computed value is never persisted — it is only compared against the
 * limit and embedded into a trace event.
 *
 * **Doctrine pitfall pinned in KDoc** (per practice-scout): both inputs
 * MUST be in Magnetic FROM-degrees. Mixing a True wind (METAR) with a
 * Magnetic runway heading produces silent off-by-declination errors —
 * e.g. ~5° declination in central Europe turns a 14 kt crosswind into
 * a 15.9 kt crosswind near the limit. The single-frame contract is the
 * fix.
 *
 * **Examples**:
 *  - dead headwind (wind direction == runway heading) → 0.0
 *  - dead tailwind (180° from runway) → 0.0 (sin(180°) = 0)
 *  - pure crosswind (90° from runway) → full speed
 *  - 45° from runway, 20 kt → 20 × |sin(45°)| ≈ 14.14
 *  - wraparound: wind 350°M, runway 010° (heading 10°), 20 kt → 20 × |sin(−20°)| ≈ 6.84
 *  - zero wind speed → 0.0 regardless of direction
 */
fun crosswindComponentKnots(
    windFromMagnetic: Int,
    windSpeedKnots: Int,
    runwayHeadingMagnetic: Int,
): Double {
    val rawDelta = ((windFromMagnetic - runwayHeadingMagnetic) % 360 + 360) % 360
    val signed = if (rawDelta > 180) rawDelta - 360 else rawDelta
    val radians = signed * PI / 180.0
    return abs(sin(radians)) * windSpeedKnots.toDouble()
}
