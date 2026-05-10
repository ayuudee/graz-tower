# 2026-04-16: Transmission Reception Architecture

## Context

The controller currently consumes `ReceivedMessage(aircraft, transmission)` — a tuple pairing an aircraft identity with a structured `PilotTransmission`. Every transmission reaches the controller intact, fully parsed, and attributed. This is adequate while pilots are AI emitters producing correctly-typed structured messages.

Two future realities break this model:

1. **Human pilots via voice.** Free-form radiotelephony arrives as audio. Converting it into structured `PilotTransmission` values is an NLU problem that requires context (beliefs, pending clearances, speaker profile, aerodrome state). The work is best done by an LLM behaving as a **stateful interpreter**, not a pure parser. It is asynchronous. It is nondeterministic. It can fail or produce low-confidence outputs.

2. **Real radio channel behaviour.** Multiple simultaneous transmitters on one frequency produce real effects: callsign heard but content lost (stepped transmission), complete heterodyne with no identity (garbled), voice activity detected before content is interpreted. TWR1 modelled this physically in `RadioChannel` / `resolveChannel` (file refs below). The model is correct and will be needed for realism when multiple human operators share a frequency.

This document decides the architectural boundary between these two concerns and the controller, and the forward-compatible event shape that accommodates both without future rework.

## Decision Summary

Treat the path from utterance to controller-input as **three composable layers**:

1. **Physics layer** — resolves concurrent transmissions on a frequency into per-listener reception outcomes. Deterministic. Synchronous. TWR1-derived.
2. **Interpretation layer** — converts audio (if applicable) into structured typed transmissions with an assessment. Stateful. Nondeterministic. Asynchronous. LLM-backed when voice is involved; bypassed when the speaker already emits structured values (AI pilots).
3. **Controller layer** — consumes a single rich event type that expresses both reception outcomes and interpretation outcomes uniformly. Pure. Synchronous. Ignorant of audio, LLMs, and channel physics.

Build only the controller layer now. Design the event type today so both other layers slot in later without reshaping controller code.

---

## 1. Three-layer Pipeline

```
N concurrent utterances on a frequency
  │
  ▼  Physics layer
  ┌───────────────────────────────────────────────┐
  │ resolveChannel(channel, tick) →               │
  │   Clear(speaker, tx) | Stepped(speaker) |     │
  │   Garbled | Silent                            │
  └───────────────────────────────────────────────┘
  │
  ▼  Interpretation layer  (only for Clear with raw audio)
  ┌───────────────────────────────────────────────┐
  │ interpret(audio, ParserContext) →             │
  │   Confident(tx) | Uncertain(tx, c) |          │
  │   Discrepant(tx, issues) | Unintelligible     │
  └───────────────────────────────────────────────┘
  │
  ▼  Controller layer
  ┌───────────────────────────────────────────────┐
  │ PilotUtterance — unified reception event      │
  │ controllerDecide(view, beliefs, world) stays  │
  │   pure and synchronous                        │
  └───────────────────────────────────────────────┘
```

The **physics layer** is orthogonal to interpretation. It decides what the listener *hears*; interpretation decides what the listener *understands*. They compose: stepped and garbled skip interpretation entirely (no content to interpret); clear passes through to interpretation if raw audio is present, or straight through if already structured.

The **controller layer** never sees audio, never blocks on an LLM, never reasons about channel timing. It receives fully-resolved events with timestamps and assessment flags.

## 2. The Unified Event Taxonomy

The controller's typed `ControllerEvent` channel admits **two source classes**:
1. **Radio-derived events** — derived from a `PilotUtterance` after physics +
   interpretation resolves it. Most variants (`InitialContactReceived`,
   `ReadbackReceived`, `PositionReported`, `GoAroundDetected`, etc.) live here.
2. **World-state-derived events** — emitted directly by the sim's per-cycle
   world-diff producer when a world-model field changes value. fn-12 added
   the first two leaves of this class: `RunwayObstructionDetected` (on a
   runway's `obstruction` field transitioning `None → Some(...)`) and
   `RunwayObstructionCleared` (on `Some → None` via the expiry pass). They
   carry no `AircraftId` payload — the producer is per-controller-scoped, so
   each controller sees only events for runways within its `aerodromeId`.

Both source classes are typed, both are pure, both flow through the same
`ControllerView.worldEvents` and `BeliefState` fold pipeline. The firewall
contract widens to "the controller receives **typed events from world or
radio**, never raw audio, never blocking calls".

Collapsing both layers' outputs by the controller's response behaviour yields five cases:

| Case | Cause | Controller response |
|------|-------|---------------------|
| Actionable, known speaker | Clear + Confident, or Clear + Discrepant with matching-pending | Normal processing; if Discrepant, issue correction ("negative, runway zero nine") |
| Un-actionable, known speaker | Stepped (physics), or Unintelligible (parser), or Uncertain below threshold | "[callsign], say again" |
| Un-actionable, unknown speaker | Garbled (physics) | "Station calling [unit], say again your callsign" |
| Timeout | No utterance within `maxReadbackAge` of an issued instruction | "[callsign], readback?" |
| Voice activity without resolution yet | VAD fired, parser still running | Suspend timeout nudges; do not transmit over pilot |

The controller's logic is shaped by these five cases regardless of whether a degradation came from physics or interpretation. Unifying both layers' outputs into one sum type is therefore load-bearing.

## 3. Proposed Controller Input Type

To be introduced when the first of {voice parsing, radio physics, multi-pilot scenarios} arrives. Not implemented now.

```kotlin
sealed interface PilotUtterance {
    val voiceAt: SimTime   // authoritative clock — when the pilot started speaking

    // Known speaker, content we can act on (possibly with assessment flags).
    data class Intelligible(
        val aircraft: AircraftId,
        override val voiceAt: SimTime,
        val transmission: PilotTransmission,
        val quality: Quality,
    ) : PilotUtterance

    // Known speaker, content not recoverable. Covers Stepped and parser-Unintelligible.
    data class UnintelligibleFrom(
        val aircraft: AircraftId,
        override val voiceAt: SimTime,
    ) : PilotUtterance

    // Unknown speaker. Covers Garbled and similar.
    data class UnintelligibleAnonymous(
        override val voiceAt: SimTime,
    ) : PilotUtterance
}

sealed interface Quality {
    data object Confident : Quality
    data class Uncertain(val confidence: Float) : Quality
    data class Discrepant(val issues: List<ReadbackIssue>) : Quality
}
```

For now, `ReceivedMessage` stays as-is. When we widen, every existing test-supplied message maps trivially to `Intelligible(…, quality = Confident)`.

## 4. Asynchrony and the Clock

The interpretation layer is async. The controller's pipeline is synchronous. We reconcile via **two events per utterance**:

- **`VoiceActivityDetected(aircraft?, voiceAt)`** — emitted instantly when the pilot begins transmitting. Cheap to detect. Load-bearing for two behaviours:
  1. Suspends readback timeout nudges while interpretation is in flight.
  2. Prevents the controller from transmitting over an in-progress pilot call.
- **`PilotUtterance(…, voiceAt, parsedAt)`** — emitted when the interpretation layer has produced a result. May arrive seconds after `voiceAt`.

**Voice-time, not parse-time, is the authoritative clock.** All ordering, timeout, and pending-readback matching uses `voiceAt`. If utterance A (voice at T=1s) parses slowly and utterance B (voice at T=2s) parses fast, the controller's ingress step must sort B after A regardless of arrival order. Otherwise readback validation picks the wrong pending clearance and invariants break.

**Parser context is snapshotted at voice-time, not parse-time.** The interpretation layer gets a frozen view of beliefs (pending clearances, active runway, callsign registry, speaker profile, recent controller transmissions) captured at the moment VAD fired. Otherwise a clearance issued during the parse flight time can confuse interpretation.

## 5. Interpretation Layer Design Rules

Load-bearing rules that belong in the parser's own design doc when it is built. Captured here so they don't get lost:

### Recognition and assessment must stay separated

The interpretation layer must internally distinguish two reasoning modes:

- **Recognition** — "what did they most likely say, given the audio and context?"
- **Assessment** — "does what they said match what was expected, given pending state?"

If these bleed together, the LLM will gaslight itself into hearing what it was primed to hear. The fix is prompt-architectural: recognition should look at audio and acoustic context first; assessment is a second pass that compares recognition output against pending state. A wrong readback must be surfaced as `Discrepant`, not silently corrected into `Confident`.

### Structured output is the constraint on hallucination

Use tool-use / JSON-schema mode to constrain the LLM's output to the `PilotTransmission` ADT. Temperature 0. Small, tightly-scoped context. These choices remove most of the hallucination surface.

### Determinism is walled off to the parser

Controller tests emit `Confident` directly; no LLM in controller tests. Parser has its own golden tests with canned audio → expected outputs. DES/integration tests use a scripted parser that plays back pre-recorded outputs at pre-determined delays.

### Cost is manageable with caching

Each transmission is one LLM call. With small models (Haiku-class) and prompt caching on the slow-changing context (aerodrome, recent speech, registry), cost is bounded. Not a gating concern at expected simulation scales.

## 6. Physics Layer — What to Carry Forward from TWR1

TWR1 has a complete, working physics-of-radio model worth preserving when we need it. Key references:

- `/twr/protocol/src/commonMain/kotlin/dev/twr/protocol/types/Radio.kt` — transmission types and word-count / duration metadata.
- `/twr/protocol/src/commonMain/kotlin/dev/twr/protocol/types/ReceivedTransmission.kt` — the `Clear | Stepped | Garbled` sum type with rationale comments.
- `/twr/core/src/commonMain/kotlin/dev/twr/core/model/RadioChannel.kt` — per-frequency state and active-transmission tracking.
- `/twr/core/src/commonMain/kotlin/dev/twr/core/logic/RadioChannelProcessor.kt` — the `resolveChannel` function that classifies overlaps.
- `/twr/core/src/commonMain/kotlin/dev/twr/core/model/RadioWorkQueue.kt` and `RadioWorkQueueProcessor.kt` — workload-based processing delays with safety-critical bypass (GoingAround / Unable skip the queue).
- `/twr/core/src/commonTest/.../RadioChannelTest.kt` — overlap scenarios.
- `/twr/harness/src/commonTest/.../RadioInteractionTest.kt` — integration tests.

Carry-forward principles:

- **ICAO speaking rate (120 wpm) as the duration baseline.** Per-transmission-type default word counts.
- **Callsign-before-content cut-off.** Classifies Stepped (callsign heard before overlap began) vs Garbled (overlap began during callsign).
- **Safety-critical bypass in work queues.** `GoingAround` and `Unable` skip processing delays and reach the controller immediately.
- **Per-frequency isolation.** Transmissions on different frequencies do not interfere. Null-frequency broadcasts are always Clear to all listeners.

## 7. Scope: What We Build Now

**Now (alongside the Bug B fix):**

- `pendingReadbacks: Map<AircraftId, List<PendingReadback>>` in `BeliefState`, where `PendingReadback = (instruction, issuedAt: SimTime)`.
- Pipeline step after arbitration that records outgoing `Instruct` outputs into `pendingReadbacks`.
- Readback validator: on incoming `Readback`, match safety-critical atoms (runway / frequency / level / squawk) against the most recent matching pending instruction. Emit `ReadBackCorrect` on full match + pop. Silence on mismatch or no-pending.
- GC: drop pending readbacks older than `maxReadbackAge`.
- `ReceivedMessage` keeps its current shape. No `PilotUtterance` yet.

**Later (as needs arrive):**

- Physics layer when we simulate multiple concurrent transmitters (multi-pilot scenarios or multi-controller exercises).
- Interpretation layer when we accept human voice input.
- `PilotUtterance` widening when either of the above arrives. Existing tests migrate by wrapping.
- `VoiceActivityDetected` / two-event model when the interpretation layer's latency becomes observable.
- Speaker profile accumulation and speaker-adaptation features once there's a stable parser.

**Deferred and out of scope even for future work right now:**

- Compound readback across multiple concurrent clearances.
- Readback correction mid-transmission ("land two seven, correction, zero nine").
- "Roger" substitution for required readbacks (non-compliant but common).
- Partial callsign suffixes.

These live entirely in the interpretation layer when we get there.

## 8. Consequences

**Good:**

- Controller stays pure, synchronous, deterministic, easy to test.
- All NLU complexity is confined to one module behind a typed boundary.
- All channel-physics complexity is confined to another module behind the same boundary.
- AI-pilot path and human-pilot path converge on one controller input type.
- The hard research (speaker adaptation, style handling, noise, partial reception) is walled off and can be iterated on without touching controller correctness.
- Training value rises: `Discrepant(…, WrongRunway(expected=09, heard=27))` is a far richer signal than a silent "say again."

**Acceptable costs:**

- Two events per utterance (VAD + Parsed) when the interpretation layer arrives. Minor ingress complexity.
- Voice-time vs parse-time clock discipline. Needs a sort on ingress.
- Parser context snapshotting at voice-time. Small copy per utterance.

**Not addressed by this decision:**

- Multi-controller coordination on a shared frequency (area, approach, tower sharing).
- TCAS / ACAS voice alerts as a separate source.
- Non-speech audio events (mic clicks, squawk-ident acknowledgments).

These sit outside the scope and can be added as additional event variants without structural change.

## 9. References

- Controller architecture: `wiki/design-decisions/2026-04-15-controller-architecture.md`.
- TWR1 radio model files listed in §6.
- Controller module handoff: `docs/design/controller-handoff.md`.

---

## Appendix A — v1 landing report (2026-04-17)

Status of every item from §7 "Scope: What We Build Now" after the Phase 4
vertical landed. Deferred items that surfaced during the four-agent review
are cross-referenced to their entry in `docs/design/atc-agent-review-tracker.md`.

### Landed in Phase 4

| §7 item | Landed as | File |
|---------|-----------|------|
| `pendingReadbacks: Map<AircraftId, List<PendingReadback>>` | `BeliefState.pendingReadbacks` with `PendingReadback(instruction, issuedAt)` | `controller/observe/BeliefState.kt`, `observe/Readback.kt` |
| Pipeline step recording outgoing `Instruct` after arbitration | `BeliefState.recordPendingReadbacks(outputs, time)` invoked in `controllerDecide` after readback validation | `Controller.kt:65` |
| Readback validator matching safety-critical atoms | `classifyReadback` returns a three-state `ReadbackVerdict` (`Correct` / `Incorrect(defects)`) instead of the originally-scoped binary match; `validatedReadbackResponses` handles pop-on-match and correction-emission | `observe/Readback.kt` |
| GC of pending readbacks older than `maxReadbackAge` | `gcOldPendingReadbacks(time)` runs at cycle start with `MAX_READBACK_AGE = 30s` | `observe/Readback.kt:27`, `Controller.kt:34` |
| `ReceivedMessage` keeps its current shape; no `PilotUtterance` yet | Unchanged; tests construct `ReceivedMessage.Clear(aircraft, transmission)` directly | `protocol/Instruction.kt` |

### Overshoot beyond §7 scope

These landed in Phase 4 but were not called out in the original §7:

- **Three-state verdict.** The proposal said "Emit `ReadBackCorrect` on full match + pop. Silence on mismatch or no-pending." The Phase 4 implementation emits a structured correction (`ReadbackCorrection` response) on `Incorrect`, so a pilot who reads back the wrong runway gets "negative, …" instead of silence. This raised training value meaningfully; the correction is the signal that the classifier caught the defect. Silence is reserved for no-pending.
- **`AtomDefect` algebra.** Every `Incorrect` verdict carries a non-empty ordered list of defects (`MissingAtom`, `WrongAtom`, `MissingCondition`, `WrongCondition`). The §5 `Discrepant(…, issues)` sketch called for this shape; Phase 4 wires the data end-to-end but the controller currently flattens it to `ReadbackCorrectionKind`. Preserving the full defect list on the wire is tracked as P4-D17.
- **Conditional readback recursion.** `AllOf`/`AnyOf`-style conditional clearances (e.g. `BehindTrafficCondition`, `AfterDepartureCondition`) are now recognised in readbacks and contribute their own defects.
- **Mandatory hold-position atom.** `HoldingAcknowledgementReadback` was added as a required atom for `HoldPosition` / `HoldPositionCancelTakeoff` (CAP 413 §4.46, ICAO 4444 §12.3.1). Without it the classifier could not tell "pilot silent" from "pilot said roger" — both dangerous.
- **Sim-side step-on.** `ControllerWiring.frequencyFreeFrom` looks at `SimState.inFlightTransmissions` to schedule controller transmissions behind any overlapping in-flight pilot call. This is a proto-physics layer in the sim, not the controller; it lets the departure/arrival verticals exercise overlap realistically without the full physics layer this doc proposes.

### Explicitly deferred from §7 ("Later" and "Deferred")

| Item | Tracker |
|------|---------|
| Physics layer — `resolveChannel` / `Stepped` / `Garbled` classification inside the controller reception model | P4-D16 (only the sim-side step-on exists) |
| Interpretation layer (LLM-backed parser, VAD, voice-time/parse-time clock) | P4-D16, P4-D19 |
| `PilotUtterance` sum type + widening of `ReceivedMessage` | P4-D16 |
| `VoiceActivityDetected` two-event model | P4-D16 |
| `ReadbackCorrection` carrying the full `AtomDefect` list (currently flattens to `ReadbackCorrectionKind`) | P4-D17 |
| "Vacate left/right when able" — needs optional direction + `WhenAble` modifier on `AfterLandingVacateVia` | P4-D18 |
| "Stand by" / workload-deferral response from controller | P4-D19 |
| Compound readback across multiple concurrent clearances | out of scope — belongs in interpretation layer |
| Readback mid-correction ("land two seven, correction, zero nine") | out of scope — belongs in interpretation layer |
| "Roger" substitution for required readbacks | out of scope — belongs in interpretation layer |
| Partial callsign suffixes | out of scope — belongs in interpretation layer |

### Load-bearing invariants for future widening

Written here so they survive the next refactor:

- **Cycle-start GC, cycle-end record.** Pending readbacks are aged out at the top of each cycle so rule guards like `NoPendingReadback` see an already-GC'd register. New pending entries are appended after arbitration. Inverting this order would let a rule fire against a pending it also created in the same cycle.
- **Three-state verdict is intentional.** Two-state (match/no-match) is simpler but loses the correction signal. Downgrading to two-state would lose training value and regress on CAP 413 §1.5.6 / ICAO 4444 §12.3.2.
- **`voiceAt` will replace `issuedAt`-ordering only.** When the interpretation layer arrives, `PendingReadback` ordering must pivot from `issuedAt` (controller clock, what we have now) to pilot `voiceAt` for readback matching. The current `issuedAt` field is future-compatible with that change — it just needs a sibling field on the readback side. Do not swap `issuedAt` out for `voiceAt`; they answer different questions ("when did the controller say it" vs "when did the pilot start talking").
- **`AtomicReadback` is the controller's semantic unit.** Phraseology fidelity (digit-by-digit wording, "zero niner" vs "zero nine") belongs entirely in the interpretation layer. The controller operates on structural `AtomicReadback` values and must never string-compare transmissions.

### Exit criteria for promoting the proposal out of "proposed"

Treat §3–§5 of this doc as binding only after all of these have landed:

1. Multi-pilot sim scenario with concurrent transmissions (requires the physics layer).
2. Audio-in input path (requires the interpretation layer).
3. `PilotUtterance` widening with migration of all existing `ReceivedMessage` call sites.

Until then, §3–§5 are forward-compatible sketches, not contracts.
