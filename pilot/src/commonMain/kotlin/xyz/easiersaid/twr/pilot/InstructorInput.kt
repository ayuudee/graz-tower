package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.SimTime

/**
 * fn-28.8 (G0 abort-takeoff foundation R12): typed instructor input.
 *
 * The instructor channel is the test-scaffolding seam through which sim-level
 * emergencies enter as cockpit-side observations rather than world-side hooks
 * — same shape as the existing `CircuitOutcome.GoAround` instructor briefing
 * for fn-11.1's trained-GA plan. The pilot firewall stays clean: the
 * engine-failure (and future D-AUDIT.9.IV fuel-exhaustion / .V icing) entries
 * are cockpit inputs, never world-side state smuggled through a back-channel.
 *
 * See memory:
 * `knowledge/decisions/instructor-channel-causation-for-sim-2026-05-16` for
 * the architectural decision; this file is the typed API surface.
 *
 * **Firewall discipline** — the field set on every leaf MUST be a
 * cockpit-side, instructor-authored briefing input:
 *  - `aircraftId` — which cockpit the briefing addresses (test fixture
 *    selects which aircraft is briefed; cf. `CircuitOutcome.GoAround` which
 *    is per-circuit, not global).
 *  - `time` — when the briefing fires (the deterministic event-time the
 *    fixture authored, NOT a stochastic per-tick failure model).
 *
 * **No world-state reachability**: no `Aerodrome`, no `WorldIndex`, no
 * controller-side type, no `AircraftState` (which would smuggle ground-truth
 * physics state past the firewall) — every field is either a domain
 * identifier or a scalar time. `FirewallInstructorInputTest` enforces this
 * via reflection over the public fields of every leaf.
 *
 * **No `Emergency<T>` supertype** (round scope — out of scope for fn-28.8):
 * future leaves (D-AUDIT.9.IV fuel exhaustion, .V icing, divert) land as
 * sibling `InstructorInput` subtypes — the typed-input pattern, not a
 * parametric `Emergency<T>` super-shape.
 *
 * **No `AgentId.Instructor` variant** (round scope — out of scope for
 * fn-28.8): the corresponding `SimEvent.EngineFailure` keeps
 * `source = AgentId.System` (emergency events sort with other
 * system-emitted events; the instructor channel is testing scaffolding,
 * not an in-sim agent class).
 */
sealed interface InstructorInput {

    /** Domain identifier — which cockpit this briefing addresses. */
    val aircraftId: AircraftId

    /** When this briefing fires (event-time the fixture authored). */
    val time: SimTime

    /**
     * fn-28.8 (R12 engine-failure foundation): instruct the named aircraft's
     * engine to fail at [time]. Translated to a pre-stamped
     * `SimEvent.EngineFailure(aircraftId, time, seq, source = AgentId.System)`
     * by the fixture helper
     * `xyz.easiersaid.twr.sim.testing.toInitialEvents(baseSeq)`. The sim's
     * `handleEngineFailure` is the unique consumer of the resulting event.
     *
     * **v1 semantics** (single-aircraft, single-event): the test fixture
     * authors at most one `EngineFailureAt` per aircraft; the sim's
     * `handleEngineFailure` is idempotent on a second fire (already-failed
     * aircraft: no-op). Future stochastic-failure models would land here as
     * a new fixture authoring shape — the typed-input interface stays.
     *
     * **Doctrine**: POH §3.3 (engine-failure-on-takeoff) — referenced for
     * pilot-side abort-recognition reasoning in fn-28.9, not modelled via
     * RegDB at this task (out of fn-28.8 scope).
     */
    data class EngineFailureAt(
        override val aircraftId: AircraftId,
        override val time: SimTime,
    ) : InstructorInput
}
