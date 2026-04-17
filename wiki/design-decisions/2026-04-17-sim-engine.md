# 2026-04-17: Simulation Engine

## Context

Phase 4 of the controller-handoff plan: build the DES engine that wires the controller (already a pure `ControllerView -> ControllerDecision`) and a future pilot agent into a running simulation. Closes the stand→taxi→depart→circuit→land loop.

The hybrid physics-tick + DES shape was established in the 2026-04-15 controller-architecture doc (§3). That section argued for a pure fold `(SimState, SimEvent) -> (SimState, List<SimEvent>)` with `(time, sourceAgent, seq)` ordering, seeded PRNG, and retirement of TWR1's tick-model patches (`instantKinematics`, `radioChannelResolution`, next-tick clearance deferral). This doc decides the concrete shape.

Three parallel research passes (this codebase, TWR1 at `../twr`, DES literature) informed the decisions. Key references: FoundationDB / Antithesis VOPR, TigerBeetle VOPR, sled, helins/dsim.cljc, NARSIM / ESCAPE / ACES professional ATC simulators, Lutz 2022 (NASA NTRS 20220007116) for per-domain readback timing, Cardosi DOT/FAA for per-utterance transmission durations, SimPy conditional-event patterns.

## Decision Summary

A new `:sim` Gradle module. Pure fold `step(SimState, SimEvent) -> Pair<SimState, List<SimEvent>>` with zero mutable state. Events carry `(time, source, seq)` for a total ordering; `seq` is monotonic inside `SimState` and bumped by `step` itself. Seeded seed-splittable PRNG threaded through state. Priority queue lives in the driver only, behind an `EventQueue` interface so a persistent implementation can be swapped in without touching `step`. StrictMath for cross-machine reproducibility. Pilot is a separate agent from day one (AI + human session, following TWR1's split). Conditional events register predicted crossing times rather than polling per physics tick.

---

## 1. Module: `:sim`

New Gradle module `:sim`, multiplatform (jvm target first), depending on `:protocol`, `:core`, `:controller`, and `arrow-core`. It is the only module allowed to import `java.util.PriorityQueue`, and only inside the driver.

Rationale: keeping the sim engine separate from `:controller` means the controller stays a pure decision function with no implicit knowledge of how it is driven. It also gives us a clean seam to replace the driver later (CLI runner, test harness, live flight playback) without forking the engine.

---

## 2. Pure step, queue-agnostic

```kotlin
fun step(state: SimState, event: SimEvent): Pair<SimState, List<SimEvent>>
```

`step` is total, deterministic, and **never touches the queue**. It returns emitted events sorted by `(time, source, seq)` so the driver never needs the comparator itself for correctness — only for interleaving emissions from different steps.

Everything non-deterministic (radio jitter, cognitive delay, wind gust) is drawn from `SimState.rng` via seed-splitting: every sampling site returns `(value, newRandom)`, so two runs with the same seed produce byte-identical traces.

Rationale: this is the pattern that FoundationDB and TigerBeetle found actually works for rigorous determinism testing. Trying to keep the queue pure as well gains us nothing — `step` already is — and costs us either a persistent heap (slow) or log-n rebuilds per event (slower). See §4.

---

## 3. SimState shape

```kotlin
data class SimState(
    val now: SimTime,
    val seq: Long,                                    // monotonic event sequencer
    val rng: SimRandom,                               // seed-splittable PRNG
    val aircraft: LinkedHashMap<AircraftId, AircraftState>,
    val beliefs: Map<ControllerId, BeliefState>,      // per-controller belief state
    val world: AviationWorld,
)
```

- `now` advances only when an event is dequeued; never inside `step`.
- `seq` lives in state and is bumped by `step` every time an event is emitted. This guarantees each event has a unique `(time, source, seq)` triple even across replays.
- `LinkedHashMap` for aircraft preserves insertion order for deterministic iteration. No `HashMap` or `Set.iterator` inside `step`.
- `beliefs` is keyed by controller so Slice 4c can run multiple controllers (GND + TWR) simultaneously.

Rationale for `seq` inside state: putting it in the driver means replay requires replaying driver state too. Inside `SimState` the seq is part of the trace and replay is a pure fold over events.

---

## 4. Event queue: isolated, swap-ready

```kotlin
interface EventQueue {
    fun enqueue(event: SimEvent)
    fun dequeueMin(): SimEvent?
    val isEmpty: Boolean
}
```

This interface is consumed only by `Driver.runUntil`. `step` never sees it. The default implementation wraps `java.util.PriorityQueue<SimEvent>` with

```kotlin
Comparator
    .comparingLong<SimEvent> { it.time.millis }
    .thenComparing { a, b -> a.source.compareTo(b.source) }
    .thenComparingLong { it.seq }
```

A future persistent pairing-heap implementation slots in behind the same interface. The swap is a one-line driver construction change.

What "isolated" really means here:
- `step` returns `List<SimEvent>` and never references the queue type.
- The driver is the only consumer; there is no static dependency on `java.util` elsewhere in `:sim`.
- If the driver's loop becomes a pure fold over `(SimState, EventQueue)` later, the swap is still a driver-only change — because the queue interface is all the outside world sees.

Rationale: `java.util.PriorityQueue` is O(log n) enqueue/dequeue, well-tested, and the DES literature agrees that a mutable heap in an otherwise pure engine is the pragmatic default (sled, FoundationDB, TigerBeetle all do this). A persistent PQ is available if we need time-travel debugging of the queue itself; we don't yet.

---

## 5. Events

Sealed hierarchy with a common envelope:

```kotlin
sealed interface SimEvent {
    val time: SimTime
    val source: AgentId
    val seq: Long
}
```

Slice 4a variants:
- `PhysicsTick(time, source=System, seq)` — self-scheduling at 1 Hz. Updates `AircraftState` kinematics, evaluates any registered condition-crossings.
- `ControllerCycle(time, source=Controller(id), seq, controllerId)` — self-scheduling at the controller's cadence (default 500 ms). Calls `controllerDecide`, enqueues follow-on events for emitted outputs (transmission, handoff). Slice 4a emits no outputs yet.
- `Spawn(time, source=System, seq, aircraft, at)` — introduces an aircraft into the world mid-run.

Slice 4d adds: `TransmissionStart`, `TransmissionEnd`, `PilotProcessingComplete`, `ReadbackStart`, `ReadbackEnd`, `ControllerConfirmation`. Slice 4e adds: `ConditionCrossing` (fired by the physics layer when a registered predicate becomes true, e.g. "aircraft crosses holding point"), `HandoffAccepted`.

---

## 6. PRNG

```kotlin
@JvmInline value class SimRandom(private val state: Long) {
    fun nextLong(): Pair<Long, SimRandom>
    fun nextDouble(): Pair<Double, SimRandom>
    fun split(tag: String): SimRandom            // deterministic child via tag-hash mix
}
```

Splitting uses a simple hash mix (SplitMix64 or similar) of `state ^ hash(tag)`. Every agent that needs randomness holds a child RNG seeded from its id, so an AircraftId's jitter stream is stable regardless of how many other aircraft are active.

Rationale: per-agent seed streams are the only way to keep "aircraft X got a 2.3 s readback delay" reproducible when aircraft Y spawns before it in a different run order.

StrictMath is used for kinematics; `kotlin.math` on JVM delegates to `Math` which can differ across JITs. `StrictMath` is bit-reproducible across machines — important for byte-identical traces.

---

## 7. Pilot as a separate agent from day one

TWR1 split pilots into an AI `PilotAgent` (autonomous decisions) and a human `PilotSession` (command queue fed by UI). We keep that split here. Slice 4a only scaffolds the interface; Slice 4b implements the AI agent; human sessions come in Phase 5.

```kotlin
interface PilotAgent {
    fun decide(view: PilotView, state: PilotState): PilotDecision
}
```

Pilot is invoked via a `PilotDecisionTick` event the pilot itself self-schedules — same cadence model as the controller.

Rationale: entangling pilot logic with the physics tick (as TWR1 initially did) makes transmission timing tests require whole-tick ticks. Separate events let radio timing be modelled with millisecond precision independently of physics.

---

## 8. Conditional events register crossing times, not poll

For predicates like "aircraft passes holding point" or "aircraft enters downwind box", the condition registers a predicted crossing time with the physics layer. The physics step then only needs to check registered predicates at the registered time (or cancel if state changed). No O(aircraft × conditions) per-tick polling.

Follows SimPy's `Event.when` pattern. Adopted in Slice 4e.

---

## 9. Retirements

This engine replaces TWR1's compensatory patches:
- `TickNumber` → `SimTime` (already in protocol).
- `instantKinematics` flag → not needed; tests pick their own event sequences.
- `radioChannelResolution` flag → not needed; frequency is a shared resource modelled by overlapping `Transmission*` events in Slice 4d.
- Next-tick clearance activation → replaced by explicit `ClearanceActivated` event in Slice 4d.
- Oracle 50-tick forward sim → deferred; analytical layer + certifiers will replace it.

---

## 10. Slice plan

**4a — skeleton fold (this slice)**
- `:sim` module, `SimEvent` sealed hierarchy (`PhysicsTick`, `ControllerCycle`, `Spawn`), `SimState`, `SimRandom`, pure `step`, `EventQueue` interface + mutable default, `runUntil` driver.
- Three tests: determinism (same seed → same terminal state), self-scheduling (physics tick schedules next), mid-run spawn.
- No controller / pilot wiring yet — `ControllerCycle` is a no-op placeholder.

**4b — pilot agent**
- `PilotAgent` interface, AI implementation (route following, report triggers), `PilotDecisionTick` event.

**4c — controller↔pilot wiring (departure slice)**
- Plug `controllerDecide` into `ControllerCycle`. Translate `ControllerOutput` into events. Minimal end-to-end: stand → taxi request → taxi instruction → hold short → line up → cleared for takeoff → airborne.

**4d — radio timing**
- `TransmissionStart/End`, cognitive delay, readback timing per domain (Lutz 2022: tower 0.44 s, approach 0.67 s, centre 0.94 s). Utterance durations via Cardosi tables. Frequency as shared resource — overlapping transmissions become natural step-on events.

**4e — conditional events + full loop**
- Predicted-crossing registration, ground↔tower handoff orchestration, arrival + circuit. Closes stand→taxi→depart→circuit→land.

---

## 11. Testing strategy

Every test is an event trace. Given seed + event sequence, assert state. Given two runs with the same seed, assert byte-identical traces. Slice 4a's determinism test is the anchor: any future change that breaks byte-identical replay for a fixed seed trips it immediately, and we debug before we merge.

Sorting emitted events inside `step` (by `time, source, seq`) is the hygiene that makes this work. Without it, two valid implementations of `step` can disagree on emission order and the trace diverges even for a "correct" change.
