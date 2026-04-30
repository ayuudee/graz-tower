package xyz.easiersaid.twr.sim.testing

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Pass 4 — fixture sanity. Per Test review M1, only LJMB has a sanity row;
 * G0 (`LowgGoldenTest`) covers LOWG end-to-end so a separate "LOWG loads
 * cleanly" test would be scaffold.
 *
 * The LJMB row is the floor not the ceiling: when Pass 8/11 introduces an
 * LJMB-driving integration test, this row becomes redundant and is dropped.
 */
class FixtureSanityTest {

    @Test
    fun `LJMB fixture loads cleanly and validates`() {
        val loaded = Fixtures.LJMB.load().getOrElse {
            fail("LJMB fixture failed to load: $it")
        }
        val violations = loaded.validate(Fixtures.LJMB)
        assertEquals(emptyList(), violations,
            "LJMB fixture violated sanity: $violations")
    }
}
