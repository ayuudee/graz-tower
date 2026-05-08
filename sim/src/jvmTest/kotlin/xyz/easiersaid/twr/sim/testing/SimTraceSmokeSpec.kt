package xyz.easiersaid.twr.sim.testing

import arrow.core.getOrElse
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.SimEvent
import xyz.easiersaid.twr.sim.SimState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Smoke spec for the [SimTrace] zipper + queries (per Phase I plan-stage
 * test-review M1 — "real-job tests only, no scaffold").
 *
 * Drives a real but tiny sim run (LOWG fixture, ~10 sim seconds) and
 * exercises the zipper invariants, transition extraction, and rule-firing
 * helpers. The tiny run doubles as a "sim still runs end-to-end" check —
 * if `runUntilWithStateTrace` breaks, this fails alongside the real
 * golden tests rather than as scaffold-only diagnostics.
 *
 * **Forbidden patterns**: no hand-built `SimState` / `SimEvent` literals
 * — we use the real fixture loader. The point of testing this harness is
 * confidence that the queries work against the actual sim shape.
 */
class SimTraceSmokeSpec {

    @Test
    fun `cursor forward and backward at trace boundaries`() {
        val (_, _, trace) = runTinyLowgRun()

        // Initial cursor: index 0, no prior event, backward = None.
        val initial = trace.cursor()
        assertEquals(0, initial.index)
        assertTrue(initial.event.isNone(), "initial cursor's event must be None")
        assertTrue(initial.backward().isNone(), "backward at index 0 must be None")

        // Walk to end, verify forward = None at the terminal cursor.
        var cur = initial
        var steps = 0
        while (true) {
            val next = cur.forward().getOrNull() ?: break
            cur = next
            steps++
        }
        assertEquals(trace.size, steps, "walking forward from initial covers all steps")
        assertTrue(cur.forward().isNone(), "forward at final cursor must be None")
    }

    @Test
    fun `firstWhere with no match returns None and with match returns earliest`() {
        val (_, _, trace) = runTinyLowgRun()

        // No-match predicate.
        val nope = trace.firstWhere { false }
        assertTrue(nope.isNone(), "firstWhere(false) must return None")

        // Always-true: returns initial cursor (index 0).
        val always = trace.firstWhere { true }.getOrNull()
        assertNotNull(always, "firstWhere(true) must return Some")
        assertEquals(0, always.index, "always-true must return earliest (initial) cursor")
    }

    @Test
    fun `transitionsOf returns empty for constant property`() {
        val (_, _, trace) = runTinyLowgRun()

        // The aircraft id never changes during the run (it's a runtime fact, not a state property).
        // For a truly constant extract, transitionsOf returns empty.
        val constants = trace.transitionsOf { 42 }
        assertEquals(emptyList(), constants, "constant property has zero transitions")
    }

    @Test
    fun `transitionsOf returns at least one transition for time advancement`() {
        val (_, _, trace) = runTinyLowgRun()

        // sim time advances over the run (it's a deterministic monotone).
        val timeTransitions = trace.transitionsOf { it.now.millis }
        assertTrue(
            timeTransitions.isNotEmpty(),
            "sim time must advance during a non-trivial run; got ${timeTransitions.size} transitions",
        )
        // Each transition has a strictly later `to` time than `from`.
        for (t in timeTransitions) {
            assertTrue(t.to > t.from, "time transition must advance (got ${t.from} → ${t.to})")
        }
    }

    @Test
    fun `responsibilityTransitions for absent aircraft is empty`() {
        val (_, _, trace) = runTinyLowgRun()

        val unknownAircraft = AircraftId("UNKNOWN-XYZ")
        val transitions = trace.responsibilityTransitions(unknownAircraft)
        assertEquals(
            emptyList(),
            transitions,
            "responsibilityTransitions for an aircraft never in the trace must be empty",
        )
    }

    @Test
    fun `slice initial equals start state and span is included range`() {
        val (_, _, trace) = runTinyLowgRun()

        // Take a non-trivial slice (skip the first 2 events; take 3 more).
        val firstStep = trace.cursor().forward().getOrNull()
            ?: fail("trace too short: no first step")
        val secondStep = firstStep.forward().getOrNull()
            ?: fail("trace too short: no second step")
        // End cursor at index 5 (or end of trace if shorter).
        val endIndex = minOf(5, trace.size)
        val endCursor = TraceCursor(trace, endIndex)

        val slice = trace.slice(secondStep, endCursor)
        assertEquals(
            secondStep.state,
            slice.initial,
            "slice's initial state equals the start cursor's state",
        )
        assertEquals(
            endIndex - secondStep.index,
            slice.size,
            "slice size equals end.index - start.index",
        )
    }

    @Test
    fun `eventsBetween produces start-exclusive end-inclusive event list`() {
        val (_, _, trace) = runTinyLowgRun()

        // events between index 0 (exclusive) and index 3 (inclusive) = 3 events.
        val end = TraceCursor(trace, minOf(3, trace.size))
        val events = trace.eventsBetween(trace.cursor(), end)
        assertEquals(end.index, events.size, "eventsBetween returns end.index events")
    }

    @Test
    fun `monotonic-time invariant holds for runUntilWithStateTrace output`() {
        val (_, _, trace) = runTinyLowgRun()

        // SimTrace's `init` enforces this; if construction succeeds we know
        // it holds. Pin it explicitly so a future runner regression that
        // emits non-monotonic events fails at construction with a clear
        // message rather than producing weird query results.
        var prev = trace.initial.now
        for ((i, s) in trace.steps.withIndex()) {
            assertTrue(
                s.time >= prev,
                "step $i time ${s.time.millis} must be >= prior ${prev.millis}",
            )
            prev = s.time
        }
    }

    // ── Tiny real sim run ───────────────────────────────────────────────

    /**
     * Drive the LOWG fixture for ~10 sim seconds. Long enough for several
     * controller cycles + a pilot transmission; short enough that the
     * spec runs fast.
     */
    private fun runTinyLowgRun(): Triple<SimState, List<TransmissionRecord>, SimTrace> {
        val loaded = Fixtures.LOWG.load().getOrElse { fail("LOWG fixture failed: $it") }
        val now = SimTime.ZERO
        val aircraftId = AircraftId("OE-ABC")
        val mission = createMission(
            goal = HighLevelGoal.CircuitTraining(circuits = 1),
            startPhase = PilotPhase.AtStand,
            time = now,
        )
        val aircraft = AircraftState(
            id = aircraftId,
            callsign = Callsign("OEABC"),
            position = loaded.world.geometry.points.getValue(Fixtures.LOWG.standPointId),
            positionPoint = Fixtures.LOWG.standPointId,
            phase = PilotPhase.AtStand,
            pilotMission = mission,
        )
        val initial = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraft),
            controllers = loaded.controllers.values.toList(),
            weatherByAerodrome = mapOf(loaded.world.aerodromes.keys.first() to Fixtures.LOWG.weather),
        ).getOrElse { error("SimState.initial: $it") }
        val until = SimTime.ZERO + SimDuration.ofMillis(10_000L)
        val initialEvents = loaded.initialEvents + listOf(
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
            SimEvent.PhysicsTick(time = now),
        ) + loaded.controllers.keys.map { ctrlId ->
            SimEvent.ControllerCycle(time = now, controllerId = ctrlId)
        }
        val result = runUntilWithStateTrace(initial, initialEvents, until)
        return Triple(result.finalState, result.records, result.trace)
    }
}
