package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.InstructorInput
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.testing.toInitialEvents
import xyz.easiersaid.twr.sim.testing.withFixtureBaseSeqAdvanced

/**
 * fn-28.8 (G0 abort-takeoff foundation R12 / round-4 Minor 1): non-overlap
 * audit between pre-stamped instructor-channel events and driver-emitted
 * events.
 *
 * **Round-4 Minor 1 contract**: when a test fixture pre-stamps initial
 * events via `toInitialEvents(baseSeq)` and threads them into the sim,
 * the fixture builder must advance `SimState.seq` to `baseSeq + emittedCount`.
 * Subsequent driver-emitted events (every call to `state.emit(...)`) draw
 * fresh seqs strictly greater than the pre-stamped instructor-channel range.
 *
 * The pin: a regression that forgets the seq-advance would either:
 *  - cause `emit()` to re-stamp seqs that collide with the instructor
 *    events (under canonical `(time, source, seq)` ordering, the second
 *    event with the same triple is structurally ambiguous), OR
 *  - silently re-use seq values that violate the "fresh, monotonic per
 *    SimState" invariant the queue depends on.
 *
 * This spec drives the contract end-to-end via the public helpers
 * (`toInitialEvents` + `withFixtureBaseSeqAdvanced`) over a representative
 * fixture-builder shape.
 */
class InitialEventsSeqAuditSpec {

    private val aircraftId = AircraftId("OE-ABC")
    private val standPoint = PointId("STAND_A")

    private val worldIndex = WorldIndex(
        positions = mapOf(standPoint to Position(xMeters = 0.0, yMeters = 0.0)),
    )

    private fun freshState(): SimState = SimState.initial(
        seed = 42L,
        worldIndex = worldIndex,
        aircraft = listOf(
            AircraftState(
                id = aircraftId,
                callsign = Callsign("OE-ABC"),
                position = worldIndex.positions.getValue(standPoint),
                positionPoint = standPoint,
            ),
        ),
        weatherByAerodrome = emptyMap<AerodromeId, xyz.easiersaid.twr.core.world.WeatherObservation>(),
    ).getOrElse { error("SimState.initial rejected fixture: $it") }

    @Test
    fun `toInitialEvents stamps seqs monotonically starting at baseSeq plus one`() {
        val briefings = listOf<InstructorInput>(
            InstructorInput.EngineFailureAt(aircraftId, SimTime(60_000)),
            InstructorInput.EngineFailureAt(AircraftId("OE-DEF"), SimTime(120_000)),
        )
        val result = briefings.toInitialEvents(baseSeq = 0L)
        val seqs = result.events.map { it.seq }
        assertEquals(listOf(1L, 2L), seqs, "seqs start at baseSeq+1 and increment monotonically")
        assertEquals(2L, result.nextSeq, "nextSeq equals baseSeq + emittedCount")
    }

    @Test
    fun `toInitialEvents with non-zero baseSeq honours the offset`() {
        val briefings = listOf<InstructorInput>(
            InstructorInput.EngineFailureAt(aircraftId, SimTime(60_000)),
        )
        val result = briefings.toInitialEvents(baseSeq = 10L)
        assertEquals(listOf(11L), result.events.map { it.seq })
        assertEquals(11L, result.nextSeq)
    }

    @Test
    fun `withFixtureBaseSeqAdvanced raises SimState seq past the pre-stamped range`() {
        val state = freshState()
        val briefings = listOf<InstructorInput>(
            InstructorInput.EngineFailureAt(aircraftId, SimTime(60_000)),
            InstructorInput.EngineFailureAt(aircraftId, SimTime(180_000)),
        )
        val result = briefings.toInitialEvents(baseSeq = state.seq)
        val advanced = state.withFixtureBaseSeqAdvanced(result.nextSeq)
        assertEquals(
            result.nextSeq, advanced.seq,
            "fixture builder advances SimState.seq to baseSeq + emittedCount",
        )
    }

    @Test
    fun `driver-emitted events draw seqs strictly greater than every pre-stamped event seq`() {
        // The round-4 Minor 1 non-overlap audit: after advancing
        // SimState.seq, the next emit() call assigns seqs strictly greater
        // than every pre-stamped instructor-channel event.
        val state = freshState()
        val briefings = listOf<InstructorInput>(
            InstructorInput.EngineFailureAt(aircraftId, SimTime(60_000)),
            InstructorInput.EngineFailureAt(aircraftId, SimTime(180_000)),
        )
        val result = briefings.toInitialEvents(baseSeq = state.seq)
        val advanced = state.withFixtureBaseSeqAdvanced(result.nextSeq)

        // Drive a representative driver-emitted event through emit().
        val driverEvent = SimEvent.PhysicsTick(time = SimTime(0))
        val (afterEmit, emitted) = advanced.emit(listOf(driverEvent))
        val driverSeqs = emitted.map { it.seq }

        val maxPreStamped = result.events.maxOf { it.seq }
        assertTrue(
            driverSeqs.all { it > maxPreStamped },
            "every driver-emitted seq ($driverSeqs) must be strictly greater than the " +
                "pre-stamped instructor-channel maximum ($maxPreStamped)",
        )
        assertEquals(
            advanced.seq + driverSeqs.size, afterEmit.seq,
            "post-emit SimState.seq accounts for all emitted events",
        )
    }

    @Test
    fun `withFixtureBaseSeqAdvanced is a no-op when nextSeq is not greater than current seq`() {
        // Idempotence / safety: calling the advance helper with a value
        // less than or equal to the current seq must NOT regress the
        // counter. (E.g. an empty briefing list returns nextSeq = baseSeq,
        // which equals the current seq — the helper must preserve, not
        // overwrite.)
        val state = freshState() // seq = 0
        val advanced = state.withFixtureBaseSeqAdvanced(nextSeq = 0L)
        assertEquals(state.seq, advanced.seq, "no-op when nextSeq == seq")
        val regressed = state.withFixtureBaseSeqAdvanced(nextSeq = -5L)
        assertEquals(state.seq, regressed.seq, "no-op when nextSeq < seq (never regress)")
    }

    @Test
    fun `empty briefings list produces empty events and nextSeq equal to baseSeq`() {
        val empty = emptyList<InstructorInput>().toInitialEvents(baseSeq = 7L)
        assertTrue(empty.events.isEmpty(), "empty briefings → empty events")
        assertEquals(7L, empty.nextSeq, "empty briefings → nextSeq == baseSeq")
    }

    @Test
    fun `EngineFailureAt maps to EngineFailure with source AgentId System (round-2 — no Instructor variant)`() {
        // Architectural pin: the round-2 decision to NOT introduce
        // AgentId.Instructor means EngineFailure events emit with
        // `source = AgentId.System`. A regression that introduced
        // AgentId.Instructor would surface here.
        val result = listOf<InstructorInput>(
            InstructorInput.EngineFailureAt(aircraftId, SimTime(60_000)),
        ).toInitialEvents(baseSeq = 0L)
        val event = result.events.single() as SimEvent.EngineFailure
        assertEquals(AgentId.System, event.source, "EngineFailure source must be AgentId.System")
        assertEquals(aircraftId, event.aircraftId)
        assertEquals(SimTime(60_000), event.time)
    }
}
