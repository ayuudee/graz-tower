package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.WindReport
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.nextAtisLetter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * Pass 15 (D-AUDIT.8 closure) — Atis data invariants and AtisIssued
 * handler contract.
 *
 * Doctrine: ICAO Annex 11 §4.3 (ATIS service); Doc 4444 §4.5.5
 * (broadcast content).
 */
class AtisSpec {

    private val LOWG = AerodromeId("LOWG")
    private val r16C = RunwayId("16C")
    private val now0 = SimTime.ofMillis(0)
    private fun cfg() = RunwayConfiguration(arrivals = listOf(r16C), departures = listOf(r16C))
    private fun atis(letter: Char) = Atis(
        letter = letter,
        aerodrome = LOWG,
        configuration = cfg(),
        wind = Wind.unsafe(160, 8),
        qnh = null,
        visibility = null,
        generatedAt = now0,
    )

    @Test
    fun `Atis init rejects letters outside A to Z`() {
        assertFails("ATIS letter must be A..Z") {
            atis('a')
        }
        assertFails("ATIS letter must be A..Z") {
            atis('1')
        }
    }

    @Test
    fun `nextAtisLetter wraps Z to A per ICAO Annex 11 sec4dot3 convention`() {
        assertEquals('B', nextAtisLetter('A'))
        assertEquals('A', nextAtisLetter('Z'), "Z→A wrap is canonical rotation")
    }

    @Test
    fun `AtisIssued handler stores under aerodrome and is idempotent on byte-equal re-issue`() {
        val state = SimState(
            now = now0,
            seq = 0L,
            rng = SimRandom(0L),
            rngByAircraft = emptyMap(),
            aircraft = LinkedHashMap(),
            controllers = emptyMap(),
            beliefs = emptyMap(),
            world = AviationWorld(),
            worldIndex = WorldIndex(),
            weatherByAerodrome = emptyMap(),
        )
        val event = SimEvent.AtisIssued(time = now0, aerodrome = LOWG, atis = atis('A'))
        val (next, emitted) = step(state, event)
        assertEquals(emptyList(), emitted, "AtisIssued emits no follow-up events")
        assertEquals(atis('A'), next.atisByAerodrome[LOWG], "stored under aerodrome key")

        // Idempotent on byte-equal re-issue.
        val (next2, _) = step(next, event)
        assertEquals(next, next2, "byte-equal re-issue is a no-op")

        // Letter advance: handler unconditionally updates (no rotation invariant).
        val event2 = SimEvent.AtisIssued(time = now0, aerodrome = LOWG, atis = atis('C'))
        val (next3, _) = step(next, event2)
        assertEquals(
            'C',
            next3.atisByAerodrome[LOWG]?.letter,
            "Annex 11 §4.3: letter advances unconditionally — supervisor-driven skips are normal",
        )
    }
}
