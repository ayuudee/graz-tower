package xyz.easiersaid.twr.pilot.observe

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * fn-15.1 (G3a-react-tailwind R3) — pure-math pins for [tailwindComponentKnots].
 *
 * Sibling of fn-14.1's `CrosswindHelperTest`. Doctrine: FAA AIM §7-1-12.d.3
 * (Magnetic FROM-degrees reference frame for ATC-broadcast winds); ICAO Annex
 * 14 §5.2 (runway designators are Magnetic by convention). Both inputs share
 * a single reference frame in v1; True/Magnetic conversion is out of scope.
 *
 * Pins cover the documented examples in the helper's KDoc plus operational
 * boundary rows:
 *  - dead headwind (0°) — 0.0 component
 *  - pure 90° crosswind — 0.0 (no tailwind on the centreline axis)
 *  - dead tailwind (180°) — full wind speed
 *  - 135° quartering tail-cross at 20 kt — 20 × cos(45°) ≈ 14.142
 *  - small angles near headwind (89° / 91°): 89° crosswind = 0.0 (slight
 *    headwind side; tailwind axis returns 0); 91° quartering = small
 *    positive tailwind.
 *  - wraparound: wind 180°M vs runway 36 (heading 360°) — dead tailwind →
 *    full speed (the wraparound is benign on the cosine axis).
 *  - zero wind speed — 0.0 regardless of direction.
 *  - strict `>` boundary at the 10 kt advisory: component = 10.0 → no
 *    event at the recognition layer (pinned in PilotEventTailwindTest);
 *    here we just confirm the helper produces exactly 10.0 on the
 *    boundary fixture.
 */
class TailwindHelperTest {

    private val tolerance = 1e-9

    @Test
    fun `dead headwind — wind direction equals runway heading — yields zero tailwind`() {
        // Wind 270° from, runway 27 (heading 270°). Positive headwind →
        // tailwind axis returns 0.
        assertEquals(
            0.0,
            tailwindComponentKnots(windFromMagnetic = 270, windSpeedKnots = 15, runwayHeadingMagnetic = 270),
            tolerance,
            "dead headwind: cos(0°) × speed positive → max(0, -speed) = 0",
        )
    }

    @Test
    fun `pure 90 degree crosswind — wind perpendicular to runway — yields zero tailwind`() {
        // Wind 360°/North, runway 27 (heading 270°). Relative 90° →
        // cos(90°) = 0 → headwindSigned = 0 → tailwind = max(0, 0) = 0.
        // Crosswind axis is full speed; tailwind axis is zero — the
        // operational asymmetry the helper captures.
        assertEquals(
            0.0,
            tailwindComponentKnots(windFromMagnetic = 360, windSpeedKnots = 20, runwayHeadingMagnetic = 270),
            tolerance,
            "pure 90° crosswind: cos(90°) = 0 → tailwind 0; crosswind axis is full speed",
        )
    }

    @Test
    fun `dead tailwind — wind opposite runway heading — yields full wind speed`() {
        // Wind 090° from (i.e. from the east), runway 27 (heading 270°).
        // Relative 180° → cos(180°) = −1 → headwindSigned = −20 →
        // tailwind = max(0, 20) = 20.
        assertEquals(
            20.0,
            tailwindComponentKnots(windFromMagnetic = 90, windSpeedKnots = 20, runwayHeadingMagnetic = 270),
            tolerance,
            "dead tailwind (180° from runway): cos(180°) = −1 → tailwind = full speed",
        )
    }

    @Test
    fun `135 degree quartering tail-cross at 20 kt yields cos45 times speed`() {
        // Wind 135° from (from the south-east), runway 27 (heading 270°).
        // Relative angle = 135 − 270 = −135° → cos(−135°) = −√2/2.
        // headwindSigned = −√2/2 × 20 ≈ −14.142 → tailwind ≈ 14.142.
        val component = tailwindComponentKnots(
            windFromMagnetic = 135, windSpeedKnots = 20, runwayHeadingMagnetic = 270,
        )
        // 20 × cos(45°) ≈ 20 × 0.7071068 ≈ 14.142136
        assertTrue(
            abs(component - 14.14213562) < 1e-6,
            "135° quartering tailwind: |cos(45°)| × 20 ≈ 14.142, got $component",
        )
    }

    @Test
    fun `small angle near headwind — 89 degree wind yields zero tailwind`() {
        // Wind 359° from, runway 27 (heading 270°). Relative = 359 − 270
        // = 89°. cos(89°) > 0 → headwindSigned > 0 → tailwind = 0. The
        // tiny headwind component clamps to 0 on the tailwind axis.
        val component = tailwindComponentKnots(
            windFromMagnetic = 359, windSpeedKnots = 20, runwayHeadingMagnetic = 270,
        )
        assertEquals(
            0.0,
            component,
            tolerance,
            "89° from runway: slight headwind → tailwind axis returns 0 (max(0, −cos × speed))",
        )
    }

    @Test
    fun `small angle past 90 — 91 degree wind yields small positive tailwind`() {
        // Wind 091° from, runway 27 (heading 270°). Relative = 091 − 270
        // = −179°. Wrapped via +540 mod 360 −180 → 181° − 360 = −179°
        // (already in [−180, 180]). cos(−179°) ≈ −0.9998. tailwind ≈
        // 20 × 0.9998 ≈ 19.997. (This is "near-dead-tailwind"; near 91°
        // *signed* relative the formula resolves the other end of the
        // axis — the symmetric near-headwind case is 89° above.)
        // For a near-90° tailwind we need wind aligned roughly opposite
        // the runway: 271° from (≈ tail). Relative = 271 − 270 = 1° →
        // cos(1°) > 0 → still headwind → 0. So 91° "just past
        // perpendicular" on the tailwind side requires wind direction
        // around 360° (perpendicular) shifted *towards* opposite: 001°.
        // Relative = 001 − 270 = −269° → +540 mod 360 −180 = 91° →
        // cos(91°) ≈ −0.01745 → headwindSigned ≈ −0.349 → tailwind ≈
        // 0.349 (small positive).
        val component = tailwindComponentKnots(
            windFromMagnetic = 1, windSpeedKnots = 20, runwayHeadingMagnetic = 270,
        )
        assertTrue(
            component > 0.0 && component < 1.0,
            "91° past perpendicular on tailwind side: small positive tailwind; got $component",
        )
    }

    @Test
    fun `wraparound — wind 180 M vs runway 36 (heading 360) — yields dead tailwind`() {
        // Wind 180° from (from the south), runway 36 (heading 360°).
        // Naive subtraction: 180 − 360 = −180 → cos(−180°) = −1 →
        // tailwind = 20. Wraparound: same magnitude (cos is symmetric
        // at the ±180 boundary). Pin both ways: dead tailwind = full
        // speed regardless of which side of the wraparound the
        // helper resolves to.
        val component = tailwindComponentKnots(
            windFromMagnetic = 180, windSpeedKnots = 20, runwayHeadingMagnetic = 360,
        )
        assertEquals(
            20.0,
            component,
            1e-9,
            "wraparound 180° vs runway 36 (heading 360°): dead tailwind → full speed",
        )
    }

    @Test
    fun `zero wind speed yields zero tailwind regardless of direction`() {
        // Calm air. Tailwind is 0.0 across any relative angle.
        for (windDirection in listOf(0, 45, 90, 180, 270, 315, 360)) {
            assertEquals(
                0.0,
                tailwindComponentKnots(
                    windFromMagnetic = windDirection,
                    windSpeedKnots = 0,
                    runwayHeadingMagnetic = 270,
                ),
                tolerance,
                "zero wind speed × cos(any) = 0; direction $windDirection",
            )
        }
    }

    @Test
    fun `boundary — wind 10 kt direct tailwind against C172 10 kt advisory — exact 10dot0`() {
        // Wind 090° from (east), runway 27 (heading 270°), 10 kt. Dead
        // tailwind → component = 10.0 exactly. Recognition layer uses
        // strict `>` against 10 kt → no event at exactly the boundary
        // (pinned at PilotEventTailwindTest). Here we pin the helper's
        // contract: at the boundary the helper returns precisely 10.0
        // (no rounding / no truncation drift).
        val component = tailwindComponentKnots(
            windFromMagnetic = 90, windSpeedKnots = 10, runwayHeadingMagnetic = 270,
        )
        assertEquals(
            10.0,
            component,
            tolerance,
            "boundary: 10 kt dead tailwind → exact 10.0 component; strict `>` boundary in recognition",
        )
    }
}
