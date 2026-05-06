package xyz.easiersaid.twr.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * Pass 15 (D-AUDIT.7 closure) — `RunwayConfiguration` data invariants
 * and `primary` projection.
 *
 * Doctrine: ICAO Doc 4444 §7.2 (runway-in-use selection).
 */
class RunwayConfigurationSpec {

    @Test
    fun `init throws when both arrivals and departures are empty per Doc 4444 sec7dot2`() {
        assertFails("RunwayConfiguration must have at least one active runway") {
            RunwayConfiguration(arrivals = emptyList(), departures = emptyList())
        }
    }

    @Test
    fun `init throws on duplicate runway IDs in arrivals or departures`() {
        val r16C = RunwayId("16C")
        assertFails("arrivals must not contain duplicates") {
            RunwayConfiguration(arrivals = listOf(r16C, r16C), departures = emptyList())
        }
        assertFails("departures must not contain duplicates") {
            RunwayConfiguration(arrivals = emptyList(), departures = listOf(r16C, r16C))
        }
    }

    @Test
    fun `primary returns first arrival, falls back to first departure for arrivals-only and departures-only configs`() {
        val r16C = RunwayId("16C")
        val r16L = RunwayId("16L")

        // Both populated → first arrival.
        val both = RunwayConfiguration(arrivals = listOf(r16L, r16C), departures = listOf(r16C))
        assertEquals(r16L, both.primary, "primary reads first arrival")

        // Departures-only (LVP / contamination scenario).
        val depOnly = RunwayConfiguration(arrivals = emptyList(), departures = listOf(r16C))
        assertEquals(r16C, depOnly.primary, "OR-invariant: departures-only config falls back per Doc 4444 §7.2")

        // Arrivals-only.
        val arrOnly = RunwayConfiguration(arrivals = listOf(r16L), departures = emptyList())
        assertEquals(r16L, arrOnly.primary, "OR-invariant: arrivals-only config")
    }

    @Test
    fun `active is the distinct union of arrivals and departures preserving order`() {
        val r16C = RunwayId("16C")
        val r16L = RunwayId("16L")
        val r16R = RunwayId("16R")
        val cfg = RunwayConfiguration(
            arrivals = listOf(r16L, r16C),
            departures = listOf(r16C, r16R),
        )
        assertEquals(listOf(r16L, r16C, r16R), cfg.active, "active preserves arrivals-first ordering, dedup")
    }
}
