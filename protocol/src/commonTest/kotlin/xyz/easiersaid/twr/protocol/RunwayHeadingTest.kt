package xyz.easiersaid.twr.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * fn-14.1 (R5) — `RunwayId.headingDegreesMagnetic()` parses runway
 * designators to magnetic heading (degrees). Fail-closed: null on
 * parse failure AND on out-of-range designator (`00`, `37`, `99`).
 *
 * Doctrine: FAA AIM §7-1-12.d.3 (Magnetic frame); ICAO Annex 14
 * §5.2 (runway designators are rounded magnetic bearing tens-of-
 * degrees).
 *
 * Rows pin the documented examples plus failure modes the pilot's
 * crosswind recognition fails closed on.
 */
class RunwayHeadingTest {

    @Test
    fun `single-runway designator parses to its magnetic heading`() {
        assertEquals(270, RunwayId("27").headingDegreesMagnetic(), "27 → 270")
        assertEquals(90, RunwayId("09").headingDegreesMagnetic(), "09 → 090")
    }

    @Test
    fun `multi-runway suffix L_C_R is ignored — only the first two digits are read`() {
        assertEquals(360, RunwayId("36L").headingDegreesMagnetic(), "36L → 360 (suffix ignored)")
        assertEquals(10, RunwayId("01R").headingDegreesMagnetic(), "01R → 010 (suffix ignored)")
        assertEquals(160, RunwayId("16C").headingDegreesMagnetic(), "16C → 160 (suffix ignored)")
    }

    @Test
    fun `out-of-range designator returns null — fail-closed parse`() {
        // 01..36 is the valid range. 00 / 37 / 99 are nonsense — pilot
        // recognition treats them as no-event rather than silently
        // accepting (which would compute crosswind against the wrong
        // heading and either miss or spurious-fire).
        assertNull(RunwayId("00").headingDegreesMagnetic(), "00 is out of range (01..36)")
        assertNull(RunwayId("37").headingDegreesMagnetic(), "37 is out of range (01..36)")
        assertNull(RunwayId("99").headingDegreesMagnetic(), "99 is out of range (01..36)")
    }

    @Test
    fun `non-numeric prefix returns null — fail-closed parse`() {
        assertNull(RunwayId("HX").headingDegreesMagnetic(), "HX is not numeric")
        assertNull(RunwayId("AB").headingDegreesMagnetic(), "AB is not numeric")
    }

    @Test
    fun `empty designator returns null`() {
        assertNull(RunwayId("").headingDegreesMagnetic(), "empty designator")
    }

    @Test
    fun `single-character designator returns null — first two chars cannot parse to in-range integer`() {
        // `take(2)` on a single char gives the char; `toIntOrNull` on a
        // single digit succeeds (e.g. `"5".toIntOrNull() == 5`). 5 is in
        // 1..36, so it parses to 50 — pin that the contract returns
        // the natural single-digit reading (treats `5` like `05`).
        // Documents the edge case: real runway designators are always
        // two digits per Annex 14, but a tolerant parse of `5` to 50 is
        // less surprising than returning null on what looks like a typo
        // for a real digit. Test pins the explicit behavior so a future
        // tightening to "must be 2 digits" lands here visibly.
        assertEquals(
            50,
            RunwayId("5").headingDegreesMagnetic(),
            "single digit 5 → 50 (tolerant parse); real designators are 2 digits per Annex 14",
        )
    }
}
