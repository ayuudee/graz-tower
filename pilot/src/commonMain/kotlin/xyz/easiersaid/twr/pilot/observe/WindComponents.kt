/**
 * Pure helpers for computing the **crosswind** and **tailwind** components
 * of a steady-state wind against a runway heading.
 *
 * fn-14.1 introduced [crosswindComponentKnots]; fn-15.1 colocates
 * [tailwindComponentKnots] in the same file (single-file rename from
 * `Crosswind.kt`; package unchanged so FQN imports remain valid). Both
 * helpers share:
 *  - the same single-reference-frame contract — both inputs must be in
 *    **Magnetic FROM-degrees** (FAA AIM §7-1-12.d.3; ICAO Annex 14 §5.2
 *    for runway designators);
 *  - the same `+540 mod 360 −180` signed-angle normalisation (handles
 *    the 360°/0° wraparound and small-runway-large-wind cases);
 *  - the same True-vs-Magnetic pitfall warning (mixing a True wind from
 *    METAR with a Magnetic runway heading silently injects a declination
 *    error — ~5° in central Europe turns a 14 kt crosswind into a 15.9
 *    kt crosswind near the limit; the single-frame contract is the fix).
 *
 * Operational asymmetry: a `Wind` decomposes against the runway centreline
 * into a headwind/tailwind axis (signed: `+headwind` / `−tailwind`) and a
 * crosswind axis (left/right). Operationally, headwind has no POH-published
 * upper limit (it is always desirable for takeoff/landing); the limits live
 * on the **tailwind** half of that axis and on the (symmetric) crosswind
 * axis. Both helpers therefore return non-negative magnitudes — never
 * signed.
 */
package xyz.easiersaid.twr.pilot.observe

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
 *
 * Cross-reference [tailwindComponentKnots] — sibling helper colocated in
 * this file; same frame contract, complementary axis (signed projection
 * along runway centreline, returning the tailwind-side magnitude).
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

/**
 * fn-15.1 (G3a-react-tailwind R3) — compute the **tailwind component**
 * (knots) of a steady-state wind against a runway heading. Sibling of
 * [crosswindComponentKnots]; same inputs, same reference-frame contract,
 * complementary axis (signed projection along runway centreline, then
 * the tailwind-side magnitude).
 *
 * **Inputs** (all in **the same reference frame — Magnetic FROM-degrees**;
 * see [xyz.easiersaid.twr.protocol.Wind] KDoc):
 *  - [windFromMagnetic] — wind FROM direction, `0..360`. `360` is the
 *    aviation-convention spelling of due North (`Wind.invoke` accepts
 *    both `0` and `360`).
 *  - [windSpeedKnots] — steady-state wind speed in knots; non-negative.
 *    Zero is a valid input (calm wind → tailwind 0.0).
 *  - [runwayHeadingMagnetic] — runway magnetic heading, typically the
 *    return value of [xyz.easiersaid.twr.protocol.headingDegreesMagnetic].
 *    Pre-validated to `10..360` by that helper; this function does not
 *    re-validate.
 *
 * **Formula** (FAA AIM §7-1-12, ICAO Annex 3):
 * ```
 *     θ_signed       = ((windFromMagnetic − runwayHeadingMagnetic + 540) mod 360) − 180
 *     headwindSigned = cos(θ_signed × π / 180) × windSpeedKnots   // + headwind, − tailwind
 *     tailwind       = max(0.0, −headwindSigned)                  // magnitude; 0 when no tailwind
 * ```
 * The `+540 mod 360 −180` wraparound normalises the angle into
 * `[−180, 180]` (same as [crosswindComponentKnots]; identical numerical
 * behaviour at the wraparound).
 *
 * **Return type — Double, no truncation**: a `10.4 kt` tailwind against
 * a `10 kt` POH/AFH advisory must fire the recognition; truncating to
 * `10` would silently mask the exceedance. Callers compare to
 * `aircraftType.maxTailwindKnots.value.toDouble()` with strict `>`.
 * `0.0` is a valid value (dead headwind / calm / pure crosswind) and
 * the computed value is never persisted — it is only compared against
 * the limit and embedded into a trace event.
 *
 * **Doctrine pitfall pinned in KDoc** (per practice-scout): both inputs
 * MUST be in Magnetic FROM-degrees. Mixing a True wind (METAR) with a
 * Magnetic runway heading produces silent off-by-declination errors —
 * same warning as [crosswindComponentKnots], same single-frame fix.
 *
 * **Operational sign convention** — tailwind is the **magnitude** of the
 * headwind axis when it goes negative:
 *  - dead headwind (wind direction == runway heading) → 0.0 (positive
 *    headwind → tailwind is zero, not "−speed").
 *  - pure 90° crosswind (wind ⊥ runway) → 0.0 (cos(90°) = 0; neither
 *    headwind nor tailwind exists on the axis).
 *  - dead tailwind (180° from runway), 20 kt → 20.0 (full speed).
 *  - 135° quartering tail-cross at 20 kt → 20 × cos(45°) ≈ 14.142.
 *  - wraparound: wind 180°M vs runway 36 (heading 360°) — dead tailwind →
 *    full speed (the wraparound is benign on the cosine axis).
 *  - zero wind speed → 0.0 regardless of direction.
 *
 * **Why magnitude, not signed**: the headwind direction has no
 * operational upper limit (always desirable for takeoff/landing) — only
 * the **tailwind** side has a POH/AFH/FCOM limit per
 * [xyz.easiersaid.twr.protocol.AircraftType.maxTailwindKnots]. Returning
 * a signed value would force every caller to `max(0.0, …)` it before
 * comparing. The asymmetry is operational, not numerical.
 */
fun tailwindComponentKnots(
    windFromMagnetic: Int,
    windSpeedKnots: Int,
    runwayHeadingMagnetic: Int,
): Double {
    val rawDelta = ((windFromMagnetic - runwayHeadingMagnetic) % 360 + 360) % 360
    val signed = if (rawDelta > 180) rawDelta - 360 else rawDelta
    val radians = signed * PI / 180.0
    val headwindSigned = cos(radians) * windSpeedKnots.toDouble()
    return maxOf(0.0, -headwindSigned)
}
