package xyz.easiersaid.twr.pilot.observe

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * fn-14.1 (G3a-react R6) — pure-math pins for [crosswindComponentKnots].
 *
 * Doctrine: FAA AIM §7-1-12.d.3 (Magnetic FROM-degrees reference frame
 * for ATC-broadcast winds); ICAO Annex 14 §5.2 (runway designators are
 * Magnetic by convention). Both inputs share a single reference frame
 * in v1; True/Magnetic conversion is out of scope.
 *
 * Pins cover the documented examples in the helper's KDoc plus a
 * negative-input wraparound row:
 *  - dead headwind (0°) — 0.0 component
 *  - dead tailwind (180°) — 0.0 (sin(180°) = 0)
 *  - pure crosswind (90°) — full wind speed
 *  - 45° — 1/sqrt(2) ≈ 0.7071 × speed
 *  - wraparound: 350° wind vs 010° runway (relative 340° → −20°)
 *  - 360°-North wraparound: 360° wind ≡ 0° wind against runway 36
 *  - zero wind speed — 0.0 regardless of direction
 */
class CrosswindHelperTest {

    private val tolerance = 1e-9

    @Test
    fun `dead headwind — wind direction equals runway heading — yields zero crosswind`() {
        // Wind 270° from, runway 27 (heading 270°). Crab is straight on
        // the nose; crosswind 0.0.
        assertEquals(
            0.0,
            crosswindComponentKnots(windFromMagnetic = 270, windSpeedKnots = 15, runwayHeadingMagnetic = 270),
            tolerance,
            "dead headwind: relative angle 0° → sin(0°) × speed = 0",
        )
    }

    @Test
    fun `dead tailwind — wind direction opposite runway — yields zero crosswind`() {
        // Wind 090° (from), runway 27 (heading 270°). Tailwind, but
        // crosswind is 0 (sin(180°) = 0). v1 ships only crosswind; the
        // tailwind limit is `D-PASS-g3a-react-tailwind-limit`.
        assertEquals(
            0.0,
            crosswindComponentKnots(windFromMagnetic = 90, windSpeedKnots = 15, runwayHeadingMagnetic = 270),
            tolerance,
            "dead tailwind: relative angle 180° → sin(180°) × speed = 0; tailwind limit deferred",
        )
    }

    @Test
    fun `pure crosswind — wind perpendicular to runway — yields full wind speed`() {
        // Wind 360°/North, runway 27 (heading 270°). Relative 90°.
        assertEquals(
            20.0,
            crosswindComponentKnots(windFromMagnetic = 360, windSpeedKnots = 20, runwayHeadingMagnetic = 270),
            tolerance,
            "pure 90° crosswind: |sin(90°)| × speed = full wind speed",
        )
    }

    @Test
    fun `45 degree wind component is 1 over sqrt 2 of wind speed`() {
        // Wind 315° from (from the north-west), runway 27. Relative
        // angle 315 − 270 = 45°.
        val component = crosswindComponentKnots(
            windFromMagnetic = 315, windSpeedKnots = 20, runwayHeadingMagnetic = 270,
        )
        // 20 × sin(45°) ≈ 20 × 0.7071068 ≈ 14.142136
        assertTrue(
            abs(component - 14.14213562) < 1e-6,
            "45° crosswind: |sin(45°)| × 20 ≈ 14.142, got $component",
        )
    }

    @Test
    fun `wraparound — 350 degree wind vs 010 degree runway — handled correctly`() {
        // Naive subtraction (350 − 10 = 340) would project sin(340°)
        // = −0.342; the helper's `+540 mod 360 −180` wraparound
        // normalises to −20°, |sin(−20°)| ≈ 0.342. Both give the same
        // magnitude here, but the wraparound also avoids the bug for
        // small-runway-large-wind cases (e.g. 359° wind vs 001°
        // runway). Pin both magnitudes: the helper produces |sin(−20°)|
        // × 20 ≈ 6.840.
        val component = crosswindComponentKnots(
            windFromMagnetic = 350, windSpeedKnots = 20, runwayHeadingMagnetic = 10,
        )
        assertTrue(
            abs(component - 20.0 * kotlin.math.sin(20.0 * kotlin.math.PI / 180.0)) < 1e-6,
            "wraparound 350° vs 010° (heading 10°): |sin(−20°)| × 20 ≈ 6.840, got $component",
        )
    }

    @Test
    fun `360 degree wind equals 0 degree wind under wraparound — aviation convention`() {
        // Wind 360°M is the spelling of due North in the ATIS/ATC
        // voice convention. Against runway 36 (heading 360°), the
        // helper should produce a dead-headwind crosswind = 0.0. A
        // regression that treated 360° as 360° literally without
        // normalising would compute sin(0°) anyway — but the row
        // pins the contract regardless.
        assertEquals(
            0.0,
            crosswindComponentKnots(windFromMagnetic = 360, windSpeedKnots = 12, runwayHeadingMagnetic = 360),
            tolerance,
            "wind 360°M vs runway 36 (heading 360°): relative 0° → 0.0",
        )
    }

    @Test
    fun `zero wind speed yields zero crosswind regardless of direction`() {
        // Calm air. Crosswind is 0.0 across any relative angle.
        for (windDirection in listOf(0, 45, 90, 180, 270, 315, 360)) {
            assertEquals(
                0.0,
                crosswindComponentKnots(
                    windFromMagnetic = windDirection,
                    windSpeedKnots = 0,
                    runwayHeadingMagnetic = 270,
                ),
                tolerance,
                "zero wind speed × |sin(any)| = 0; direction $windDirection",
            )
        }
    }
}
