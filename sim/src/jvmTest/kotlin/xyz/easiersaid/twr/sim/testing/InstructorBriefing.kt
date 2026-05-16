package xyz.easiersaid.twr.sim.testing

import xyz.easiersaid.twr.pilot.InstructorInput
import xyz.easiersaid.twr.sim.SimEvent
import xyz.easiersaid.twr.sim.SimState

/**
 * fn-28.8 (G0 abort-takeoff foundation R12 / round-4 Minor 1): translate
 * instructor-channel briefings to pre-stamped sim events.
 *
 * Fixtures author emergency scenarios via the typed
 * [xyz.easiersaid.twr.pilot.InstructorInput] sealed hierarchy. This helper
 * is the deterministic, test-scaffolding translator from `InstructorInput`
 * to `SimEvent` shape, ready to be threaded into the test driver.
 *
 * **Pre-stamped seq + caller-side `SimState.seq` advancement** (round-4
 * Minor 1): the helper assigns monotonic sequence numbers starting at
 * [baseSeq] + 1, so the returned events sort deterministically against
 * each other under the canonical `(time, source, seq)` ordering. The
 * caller is responsible for then advancing `SimState.seq` past
 * `baseSeq + emittedCount` (via [withFixtureBaseSeqAdvanced]) so the
 * sim's `emit()` path never re-uses a seq from the instructor-channel
 * range — preventing overlap with driver-emitted events.
 *
 * **Non-overlap audit** (`InitialEventsSeqAuditSpec`): the audit-test
 * spec pins the round-4 contract: after the fixture builder applies
 * [withFixtureBaseSeqAdvanced], the next driver-emitted event's seq is
 * strictly greater than every pre-stamped instructor-channel event's seq.
 *
 * **EngineFailureAt → EngineFailure mapping**: `source = AgentId.System`
 * (round-2: no `AgentId.Instructor` variant introduced — the instructor
 * channel is test scaffolding, not an in-sim agent class). The mapping
 * is exhaustive over the [InstructorInput] sealed hierarchy; future
 * leaves (D-AUDIT.9.IV fuel exhaustion, .V icing, divert) extend the
 * `when` here.
 */
data class InstructorBriefingResult(
    val events: List<SimEvent>,
    /**
     * The next seq value the sim should use. Equal to `baseSeq + events.size`.
     * Threaded back into `SimState` via [withFixtureBaseSeqAdvanced] so the
     * driver's `emit()` calls start emitting at a seq strictly greater than
     * every pre-stamped instructor-channel event.
     */
    val nextSeq: Long,
)

/**
 * Translate a list of [InstructorInput] briefings to pre-stamped
 * [SimEvent]s. Seqs run monotonically starting at `baseSeq + 1`.
 *
 * Determinism contract: the input list's order is preserved; equal-time
 * briefings sort by their position in the input list (the seq is the
 * tiebreaker). Tests author the order they want; the helper does NOT
 * sort by time/aircraft/anything.
 */
fun List<InstructorInput>.toInitialEvents(baseSeq: Long): InstructorBriefingResult {
    val events = mapIndexed { index, input ->
        val seq = baseSeq + index + 1
        when (input) {
            // `source` is fixed to AgentId.System in EngineFailure's body —
            // not a constructor parameter (round-1 review fix: prevents
            // callers from mis-authoring a Pilot/Controller-sourced
            // engine-failure event).
            is InstructorInput.EngineFailureAt -> SimEvent.EngineFailure(
                time = input.time,
                aircraftId = input.aircraftId,
                seq = seq,
            )
        }
    }
    return InstructorBriefingResult(events = events, nextSeq = baseSeq + size)
}

/**
 * Advance [SimState.seq] past the pre-stamped instructor-channel events so
 * the sim's `emit()` calls never re-issue an already-used seq. Returns a
 * fresh [SimState] with `seq = nextSeq`.
 *
 * **Idempotence on no-op**: when [nextSeq] is less than or equal to the
 * current `state.seq` (e.g. no instructor briefings were authored, or the
 * caller already advanced separately), the returned state has its `seq`
 * preserved — never regresses the counter. The helper is total / safe to
 * call unconditionally.
 */
fun SimState.withFixtureBaseSeqAdvanced(nextSeq: Long): SimState =
    if (nextSeq > seq) copy(seq = nextSeq) else this
