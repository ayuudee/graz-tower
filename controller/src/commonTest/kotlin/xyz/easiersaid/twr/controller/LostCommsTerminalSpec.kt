package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.CoordinationState
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.controller.observe.ReadbackTimeoutPolicy
import xyz.easiersaid.twr.controller.observe.escalateOverdueCoordinations
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pass 9 (D-AUDIT.2) — `LostCommsDeclared` is a fixed point under
 * [escalateOverdueCoordinations] (reflexivity), and
 * [ReadbackTimeoutPolicy.Default] field values are pinned to their
 * doctrine-anchored constants.
 */
class LostCommsTerminalSpec {

    @Test
    fun `LostCommsDeclared is a fixed point under escalateOverdueCoordinations`() {
        val ac = AircraftId("OE-ABC")
        val rwy = RunwayId("16C")
        val declaredAt = SimTime.ZERO
        val coord = OutstandingCoordination(
            aircraft = ac,
            dispatch = xyz.easiersaid.twr.controller.bdi.Dispatch.Direct(HoldShortOf(target = ac, runway = rwy)),
            certificationEvidence = testCertificationEvidence(),
            expectedReadback = emptySet(),
            issuedAt = SimTime.ZERO,
            state = CoordinationState.LostCommsDeclared(declaredAt = declaredAt, emittedBlindAt = null),
        )
        val b = BeliefState.EMPTY.copy(coordinations = mapOf(ac to listOf(coord)))
        // Advance an arbitrary time and assert the terminal state is preserved.
        val advanced = b.escalateOverdueCoordinations(SimTime.ZERO + SimDuration.ofSeconds(10_000), ReadbackTimeoutPolicy.Default)
        val s = advanced.coordinations.getValue(ac).single().state
        check(s is CoordinationState.LostCommsDeclared) { "LostCommsDeclared must remain terminal, got $s" }
        check(s.declaredAt == declaredAt) { "declaredAt must not be re-anchored, got ${s.declaredAt}" }
    }

    /**
     * Pass 9 post-impl test-review Add-4: pin `ReadbackTimeoutPolicy.Default`
     * field values against the doctrine-anchored constants documented in
     * `Readback.kt`. Without this, a typo lowering `lostCommsAfter` from
     * 300 s to 30 s would not fail any test — the constants are doctrine,
     * not free parameters.
     *
     * Sources cited in Readback.kt's `Default` companion KDoc:
     * - `queryAfter = 10 s`: ICAO Doc 4444 §4.5.7.5.3; NATS MATS Part 1 §1.3; Eurocontrol HUM.ET1.ST05.
     * - `reissueAfter = 30 s`: ~30 s "I SAY AGAIN" doctrine.
     * - `reissueInterval = 20 s`: between subsequent re-emits.
     * - `lostCommsAfter = 5 min`: ~5 min on working freq before lost-comms.
     * - `maxReissueAttempts = 3`: standard before lost-comms.
     */
    @Test
    fun `ReadbackTimeoutPolicy Default values match documented doctrine`() {
        val d = ReadbackTimeoutPolicy.Default
        assertEquals(SimDuration.ofSeconds(10), d.queryAfter, "queryAfter must be 10 s (Doc 4444 §4.5.7.5.3 / NATS MATS / Eurocontrol)")
        assertEquals(SimDuration.ofSeconds(30), d.reissueAfter, "reissueAfter must be 30 s (\"I SAY AGAIN\" doctrine)")
        assertEquals(SimDuration.ofSeconds(20), d.reissueInterval, "reissueInterval must be 20 s")
        assertEquals(SimDuration.ofSeconds(300), d.lostCommsAfter, "lostCommsAfter must be 5 min (operational doctrine)")
        assertEquals(3, d.maxReissueAttempts, "maxReissueAttempts must be 3 (standard before lost-comms)")
    }
}
