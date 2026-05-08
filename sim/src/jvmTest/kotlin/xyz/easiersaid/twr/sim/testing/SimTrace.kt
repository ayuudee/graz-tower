package xyz.easiersaid.twr.sim.testing

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.SimEvent
import xyz.easiersaid.twr.sim.SimState

/**
 * Materialized history of a sim run: the [initial] state plus the
 * `(event, resulting-state)` pairs in time order.
 *
 * The sim is a pure `(SimState, SimEvent) → SimState` step function;
 * [SimTrace] is the corresponding finite history. Used as both a
 * debugging aid (cursor-based navigation, transition extraction) and
 * a test-oracle vocabulary (property-shaped queries over the run).
 *
 * Construct via [SimTrace.from] (the boundary helper that wraps
 * `runUntilWithStateTrace`'s output) or directly via the primary
 * constructor when steps are already in `TraceStep` form.
 *
 * Invariants (enforced at construction):
 *  - **Monotonic time**: each step's `state.now >= prior step's state.now`
 *    (or `initial.now` for step 0). The sim's event scheduler is monotone;
 *    a non-monotonic trace is a programming error in the runner.
 *  - **Size cap (100,000 entries)**: defensive — long sim runs that
 *    accumulate more than this many state-transitioning events are almost
 *    certainly stuck in a tight loop. 100k entries × 500ms cycles ≈ 13.9
 *    hours of sim time; real runs are far below.
 */
data class SimTrace(
    val initial: SimState,
    val steps: List<TraceStep>,
) {
    init {
        var prev = initial.now
        for ((i, s) in steps.withIndex()) {
            require(s.time >= prev) {
                "TraceStep $i has time ${s.time.millis}ms < prior ${prev.millis}ms — non-monotonic"
            }
            prev = s.time
        }
        require(steps.size < SIZE_CAP) {
            "SimTrace size ${steps.size} exceeds $SIZE_CAP cap — sim run may be stuck in a loop"
        }
    }

    /** Convenience accessor: the final state, or [initial] when no steps. */
    val finalState: SimState get() = steps.lastOrNull()?.state ?: initial

    /** Total sim-time span covered by the trace. */
    val span: SimDuration get() = finalState.now - initial.now

    val size: Int get() = steps.size

    /** Cursor at the initial state (before any event). */
    fun cursor(): TraceCursor = TraceCursor(this, 0)

    companion object {
        /** Defensive memory cap on trace size. See KDoc for rationale. */
        const val SIZE_CAP: Int = 100_000

        /**
         * Boundary helper. Wraps the materialized event-state stream from
         * `runUntilWithStateTrace`. Use this at the test's runner-call site:
         *
         * ```kotlin
         * val (final, records, trace) = runUntilWithStateTrace(...)
         * // `trace` is already a [SimTrace] with the new return shape.
         * ```
         *
         * The factory exists for backward-compat with callers that hold
         * the old `List<Pair<SimEvent, SimState>>` shape; new code uses
         * the `StateTraceResult` returned by the runner directly.
         */
        fun from(initial: SimState, steps: List<Pair<SimEvent, SimState>>): SimTrace =
            SimTrace(initial, steps.map { (e, s) -> TraceStep(e, s) })
    }
}

/** One step in a [SimTrace]: the [event] that fired plus the [state] it produced. */
data class TraceStep(val event: SimEvent, val state: SimState) {
    val time: SimTime get() = state.now
}

/**
 * Position pointer over a [SimTrace] (a zipper). `index = 0` is the
 * initial state (no event yet); `index ∈ [1..steps.size]` is the state
 * after the Nth event.
 *
 * Cursor identity is local to the [trace] field — a cursor obtained
 * from one [SimTrace] is invalid against another (including sub-traces
 * produced by [SimTrace.slice]). The `init` block validates index range
 * at construction; mismatched-trace use is caught by the next operation
 * via the same `require`.
 *
 * Returns [Option] for absent neighbours (`forward`/`backward` at trace
 * ends) and `Option<SimEvent>` for [event] (`None` at index 0). The
 * project's Arrow convention applies throughout — no nullable test
 * boundaries.
 */
data class TraceCursor(val trace: SimTrace, val index: Int) {
    init {
        require(index in 0..trace.steps.size) {
            "TraceCursor index $index out of range [0, ${trace.steps.size}]"
        }
    }

    /** Current state at the cursor. */
    val state: SimState
        get() = if (index == 0) trace.initial else trace.steps[index - 1].state

    /**
     * Event that produced [state]. `None` at index 0 (the initial state
     * preceded all events).
     */
    val event: Option<SimEvent>
        get() = if (index == 0) None else Some(trace.steps[index - 1].event)

    val time: SimTime get() = state.now

    /** Cursor at the next state, or [None] if this is the trace's final cursor. */
    fun forward(): Option<TraceCursor> =
        if (index < trace.steps.size) Some(copy(index = index + 1)) else None

    /** Cursor at the prior state, or [None] if this is the initial cursor. */
    fun backward(): Option<TraceCursor> =
        if (index > 0) Some(copy(index = index - 1)) else None

    /**
     * First cursor at-or-after this one whose state satisfies [predicate].
     * Returns [Some(this)] if the current state already satisfies; [None]
     * if no cursor in `[this..end]` satisfies.
     */
    fun firstAtOrAfter(predicate: (SimState) -> Boolean): Option<TraceCursor> {
        var i = index
        while (i <= trace.steps.size) {
            val c = TraceCursor(trace, i)
            if (predicate(c.state)) return Some(c)
            i++
        }
        return None
    }

    /**
     * Last cursor at-or-before this one whose state satisfies [predicate].
     * Returns [None] if no cursor in `[0..this]` satisfies.
     */
    fun lastAtOrBefore(predicate: (SimState) -> Boolean): Option<TraceCursor> {
        var i = index
        while (i >= 0) {
            val c = TraceCursor(trace, i)
            if (predicate(c.state)) return Some(c)
            i--
        }
        return None
    }
}

/**
 * Property transition over a [SimTrace]: the cursors before and after
 * `extract(state)` changed value, plus the values themselves.
 *
 * Generic primitive — specialised transitions
 * ([ResponsibilityTransition], [CommitmentStageTransition],
 * [MissionStepTransition]) are derivations layered on top.
 *
 * Equality: uses Kotlin's structural `==` (which is what every protocol
 * value class in this codebase implements via `data class`).
 */
data class Transition<T>(
    /** Value held in [before]'s state. */
    val from: T,
    /** Value held in [after]'s state. */
    val to: T,
    /** Last cursor where the property held value [from]. */
    val before: TraceCursor,
    /** First cursor where the property became [to]. */
    val after: TraceCursor,
)
