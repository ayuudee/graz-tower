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

/**
 * Pass 9 (D-AUDIT.2) — `LostCommsDeclared` is a fixed point under
 * [escalateOverdueCoordinations]. Reflexivity check; pins terminality
 * structurally without scaffold-bloat.
 */
class LostCommsTerminalSpec {

    @Test
    fun `LostCommsDeclared is a fixed point under escalateOverdueCoordinations`() {
        val ac = AircraftId("OE-ABC")
        val rwy = RunwayId("16C")
        val declaredAt = SimTime.ZERO
        val coord = OutstandingCoordination(
            aircraft = ac,
            instruction = HoldShortOf(target = ac, runway = rwy),
            expectedReadback = emptySet(),
            issuedAt = SimTime.ZERO,
            state = CoordinationState.LostCommsDeclared(declaredAt = declaredAt),
        )
        val b = BeliefState.EMPTY.copy(coordinations = mapOf(ac to listOf(coord)))
        // Advance an arbitrary time and assert the terminal state is preserved.
        val advanced = b.escalateOverdueCoordinations(SimTime.ZERO + SimDuration.ofSeconds(10_000), ReadbackTimeoutPolicy.Default)
        val s = advanced.coordinations.getValue(ac).single().state
        check(s is CoordinationState.LostCommsDeclared) { "LostCommsDeclared must remain terminal, got $s" }
        check(s.declaredAt == declaredAt) { "declaredAt must not be re-anchored, got ${s.declaredAt}" }
    }
}
