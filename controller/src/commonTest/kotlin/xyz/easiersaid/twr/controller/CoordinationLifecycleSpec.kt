package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.CoordinationState
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.controller.observe.ReadbackTimeoutPolicy
import xyz.easiersaid.twr.controller.observe.escalateOverdueCoordinations
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test

/**
 * Pass 9 (D-AUDIT.2) — coordination ledger lifecycle spec.
 *
 * Each row pins one transition the integration test cannot localise.
 * Cross-product cells rejected as scaffold; only reachable transitions
 * are tested. Tests use a short-window `ReadbackTimeoutPolicy` so they
 * don't sit at real-time clock for minutes — production reads
 * [ReadbackTimeoutPolicy.Default].
 */
class CoordinationLifecycleSpec {

    private val ac = AircraftId("OE-ABC")
    private val rwy = RunwayId("16C")
    private val instruction: AtcInstruction = HoldShortOf(target = ac, runway = rwy)
    private val t0 = SimTime.ZERO
    private val policy = ReadbackTimeoutPolicy(
        queryAfter = SimDuration.ofSeconds(1),
        reissueAfter = SimDuration.ofSeconds(2),
        reissueInterval = SimDuration.ofSeconds(1),
        lostCommsAfter = SimDuration.ofSeconds(60),
        maxReissueAttempts = 3,
    )

    private fun beliefsWith(state: CoordinationState, issuedAt: SimTime = t0): BeliefState {
        val coord = OutstandingCoordination(
            aircraft = ac,
            instruction = instruction,
            expectedReadback = emptySet(),
            issuedAt = issuedAt,
            state = state,
        )
        return BeliefState.EMPTY.copy(coordinations = mapOf(ac to listOf(coord)))
    }

    private fun stateOf(b: BeliefState): CoordinationState = b.coordinations.getValue(ac).single().state

    @Test
    fun `Issued advances to Querying after queryAfter`() {
        val b = beliefsWith(CoordinationState.Issued)
        val advanced = b.escalateOverdueCoordinations(t0 + policy.queryAfter + SimDuration.ofMillis(1), policy)
        val s = stateOf(advanced)
        check(s is CoordinationState.Querying) { "expected Querying, got $s" }
        check(s.emittedAt == null) { "expected emittedAt = null on transition, got ${s.emittedAt}" }
    }

    @Test
    fun `Querying advances to Reissued(1) after reissueAfter - queryAfter`() {
        val queriedAt = t0 + policy.queryAfter + SimDuration.ofMillis(1)
        val b = beliefsWith(CoordinationState.Querying(queriedAt = queriedAt, emittedAt = null))
        val gap = policy.reissueAfter - policy.queryAfter
        val advanced = b.escalateOverdueCoordinations(queriedAt + gap + SimDuration.ofMillis(1), policy)
        val s = stateOf(advanced)
        check(s is CoordinationState.Reissued) { "expected Reissued, got $s" }
        check(s.attemptCount == 1) { "expected attemptCount = 1, got ${s.attemptCount}" }
    }

    @Test
    fun `Reissued(N) advances to Reissued(N+1) after reissueInterval`() {
        val reissuedAt = t0 + policy.reissueAfter
        val b = beliefsWith(
            CoordinationState.Reissued(reissuedAt = reissuedAt, attemptCount = 1, emittedAt = null),
        )
        val advanced = b.escalateOverdueCoordinations(reissuedAt + policy.reissueInterval + SimDuration.ofMillis(1), policy)
        val s = stateOf(advanced)
        check(s is CoordinationState.Reissued) { "expected Reissued, got $s" }
        check(s.attemptCount == 2) { "expected attemptCount = 2, got ${s.attemptCount}" }
    }

    @Test
    fun `Reissued(MAX) advances to LostCommsDeclared`() {
        val reissuedAt = t0 + policy.reissueAfter
        // attemptCount already at MAX — the next fold should declare lost-comms
        // regardless of further elapsed time.
        val b = beliefsWith(
            CoordinationState.Reissued(reissuedAt = reissuedAt, attemptCount = policy.maxReissueAttempts, emittedAt = reissuedAt),
        )
        val advanced = b.escalateOverdueCoordinations(reissuedAt + policy.reissueInterval + SimDuration.ofMillis(1), policy)
        val s = stateOf(advanced)
        check(s is CoordinationState.LostCommsDeclared) { "expected LostCommsDeclared, got $s" }
    }

    @Test
    fun `LostCommsDeclared is terminal — stays unchanged on further folds`() {
        val declaredAt = t0 + policy.lostCommsAfter
        val b = beliefsWith(CoordinationState.LostCommsDeclared(declaredAt = declaredAt))
        val advanced = b.escalateOverdueCoordinations(declaredAt + SimDuration.ofSeconds(60), policy)
        // Note: `BeliefState.escalateOverdueCoordinations` returns the same
        // instance when no transitions occur (referential identity check
        // inside the fold). Relax to value-equality.
        val s = stateOf(advanced)
        check(s is CoordinationState.LostCommsDeclared) { "expected LostCommsDeclared (terminal), got $s" }
        check(s.declaredAt == declaredAt) { "expected declaredAt unchanged, got ${s.declaredAt}" }
    }

    // Three "removes ..." rows cut per Pass 9 post-impl test-review M.3:
    // they tested `Map.minus` (Kotlin stdlib), not production code. Real
    // removal lives in `Controller.kt`'s acceptReadback (filters
    // `is Issued` only) and `Supersession.kt`'s applySupersessionCleanup;
    // both are exercised by integration tests, not by simulating
    // `b.copy(coordinations - ac)` here.
    //
    // The "removes Reissued" row also asserted behaviour acceptReadback
    // does not exhibit (only Issued entries are accepted), exposing a
    // real coverage gap for "late readback after escalation" — filed as
    // **D-AUDIT.2.E-FOLLOWUP** (production semantics for late-readback
    // resolution of an escalated coordination).
}
