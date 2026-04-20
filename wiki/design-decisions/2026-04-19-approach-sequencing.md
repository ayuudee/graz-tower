# 2026-04-19: Approach Sequencing (Phase 5)

## Context

Phase 4 landed a clean tower first-slice: obligation-driven BDI pipeline (observe → reconcile → updateRunwayDuty → executeProcedures → arbitrate → advanceStages → validateReadbacks → recordPending), three-state ReadbackVerdict, RunwayDutyState with arrival preemption per ICAO 4444 §7.10, compound ProposedAction with SequenceInfo/TrafficInfo companions, pending-readback lifecycle with three-state verdict, and conditional line-up.

Phase 5 adds approach sequencing — the layer that manages arrival spacing upstream of the runway and coordinates the APP↔TWR handoff. Resolves controller-issue-tracker items #39 (APP→TWR handoff point) and #40 (approach sequencing: number-N, vectors, speed, essential traffic).

### Design constraints

1. The Phase 5 substrate must admit STARs, RNP/RNAV approaches, parallel/dependent runway ops, LVP, and CAT II/III **without refactor** — even though Phase 5 builds none of those.
2. Pure IO, totality, immutability — Arrow/FP discipline throughout.
3. APP is a second controller *instance* with its own BeliefState from day one, communicating with TWR only via typed coordination messages. The Phase 4 pipeline already keys beliefs by ControllerId in SimState.
4. Doc 4444 17th edition (2024) is the pinned regulatory authority. Citation triples (doc, edition, section) on every rule.

### Research inputs

- TWR1 atc-issues.md: 48 logged issues, especially #22 (instruction feasibility), #23 (ExtendDownwind deadlock), #17 (authority overloaded), #30 (agents lack common sense).
- ATC general agent: 11+1 invariants including slot-based sequence, typed coordination, pilot-belief-as-state.
- ATC law agent: obligation-type separation, citation triples, class-of-airspace matrix.
- ATC phraseology agent: Instruction as sum type, readback-requirements function, conditional clearance as wrapper, proactive pilot reports as separate sum type.
- Staff engineer review: 5c depends on 5b (not just 5a), guards ≠ feasibility, stable-number is stateful, ReadbackVerdict needs a fourth state (Refused), sequence derivation is the scope risk.
- ATC operational review: FollowTarget with acquisition state, gate taxonomy too coarse, essential traffic is a separate-timed obligation, vectoring verbs required, "spacing reached" not directly observable, transfer of comms vs transfer of control separable.

## Decision Summary

Six sub-phases in strict dependency order. Build the ADT and obligation substrate first (5a), then the arrival sequence state (5b), then behaviour and coordination together (5c + 5d, 5d.14 parallel with 5b), feasibility gates (5e), verification throughout (5f).

APP runs as a separate controller instance sharing only the OLDI coordination protocol with TWR.

---

## 1. Foundations — 5a (refactor, no new behaviour)

### 1.1 Instruction ADT expansion

Expand from the current concrete instruction classes to a proper sum type covering the full vectoring/sequencing toolkit. New constructors required for Phase 5 (even if only APP emits some initially):

| Constructor | Role | Readback required | Source |
|-------------|------|-------------------|--------|
| `VectorHeading(heading, resumeOwnNav: Boolean)` | APP | Yes | Doc 4444 §8.6.1 |
| `DescendTo(level)` | APP | Yes | Doc 4444 §6.3.2 |
| `MaintainLevel(level)` | APP | Yes | Doc 4444 §6.3.2 |
| `AdjustSpeed(constraint)` | APP/TWR | Yes | Doc 4444 §6.5.5 |
| `MaintainSpeed(speed)` | APP/TWR | Yes | Doc 4444 §6.5.5 |
| `DirectTo(fix)` | APP | Yes | Doc 4444 §8.6 |
| `ClearedApproach(type, runway)` | APP | Yes | Doc 4444 §6.5.3 |
| `NumberInSequence(n, followTarget)` | APP/TWR | Yes (acknowledgement) | Doc 4444 §6.5.2 |
| `TurnBase` | TWR | Yes | Doc 4444 §7.10 |
| `ExtendDownwind(reason)` | TWR | Yes | Doc 4444 §7.10 |
| `Orbit(direction)` | APP/TWR | Yes | Doc 4444 §6.5 |
| `EssentialTrafficInfo(traffic)` | APP/TWR | No (readback) | Doc 4444 §6.5.3 |
| `Cancel(ref)` / `Disregard` | APP/TWR | Yes (acknowledgement) | Doc 4444 §6.3 |
| `BreakOff` | TWR | Yes | Doc 4444 §6.5.4 |

Two derived pure total functions registered at the ADT level:
- `readbackRequirements(i: Instruction): ReadbackRequirements` — determines which atoms must be read back. Function of the instruction, total, no external state.
- `feasibility(i: Instruction, view: ControllerView, beliefs: BeliefState): Feasible | Infeasible(reason)` — is this instruction coherent for this aircraft's current state? Distinct from `RuleGuard` (§1.5).

### 1.2 Conditional clearance as wrapper

```
ConditionalClearance(subject: Subject, condition: Condition, clearance: Instruction)
```

`Subject` is a sealed type restricting which kinds of conditions are legal. Linate-class defects (wrong subject type) become unrepresentable at the type level.

### 1.3 Citation triples

Every `AtcRule` and `Regulation` carries `(doc: RegulationDoc, edition: String, section: String)`. Doc 4444 17th ed. (2024) pinned as primary authority — resolves tracker #35.

### 1.4 Obligation-type discriminator

Tag on `Commitment`, not a type split:

```kotlin
enum class ObligationType {
    SEPARATION,     // prescribed minima, wake, runway buffer
    SEQUENCING,     // number-in-sequence, follow-target, spacing
    TRAFFIC_INFO,   // essential traffic, aerodrome traffic
    CLEARANCE,      // approach, landing, takeoff, taxi
    PROCEDURAL,     // report position, call again, contact frequency
}
```

Split into five Commitment subtypes deferred until a field exists that is meaningful for exactly one kind (likely `spacingTarget` for SEQUENCING in Phase 6).

### 1.5 Guards vs feasibility — distinct concepts

- **RuleGuard**: "should this rule fire now?" — precondition on the *rule*, selects which rule fires from the procedure spec. Lives in procedure definitions. Existing guards retained.
- **Feasibility**: "is this instruction coherent for this aircraft's state at all?" — property of the *instruction*, checked by the arbitrator post-selection, pre-emission. Lives on the ADT. `Infeasible(reason)` logged for debugging.

These are complementary, not alternatives. Do **not** convert existing guards to feasibility predicates — add feasibility as an additional layer.

### 1.6 ReadbackVerdict: two → four states

The current `ReadbackVerdict` is a two-branch sealed interface (`Correct | Incorrect`). Widen to four:

```kotlin
sealed interface ReadbackVerdict {
    data object Correct : ReadbackVerdict
    data class Incorrect(val defects: Nel<AtomDefect>) : ReadbackVerdict
    data object Missing : ReadbackVerdict
    data class Refused(val reason: RefusalReason) : ReadbackVerdict
}
```

`Refused` covers pilot "unable" responses, which are not readback defects but goal-state changes. Required immediately for 5c speed control ("unable due turbulence").

**processReadback routing** (load-bearing — must be specified before implementation):
- `Correct` → pop pending, activate clearance. (Existing behaviour.)
- `Incorrect` → pop pending, emit correction, re-issue. (Existing behaviour.)
- `Missing` → pending ages out via GC; after TTL, emit "say again" or re-issue at controller's discretion. No immediate pop.
- `Refused` → pop pending, do NOT activate clearance. Route to re-sequencing: the refusing aircraft's commitment enters a `NeedsReplan` state that the obligation engine picks up next cycle. For speed control: controller falls back to next-least-disruptive intervention per §3.1 hierarchy.

### 1.7 Outstanding-reports queue

Promote "pilot owes a report" to first-class belief state:

```kotlin
data class OutstandingReport(
    val aircraft: AircraftId,
    val expected: ExpectedEvent,    // ReportBase, ReportFinal, Report4Miles, etc.
    val issuedAt: SimTime,
)
```

Keyed by `(aircraft, expected)` in BeliefState. Consumed by the reconciliation phase when the matching pilot report arrives. Age-based GC for stale entries.

### 1.8 Belief decay

Position, leg, and speed beliefs carry a `lastUpdated: SimTime` and a configurable TTL. Stale beliefs degrade to `Unknown`, which triggers `feasibility` degraded-mode paths and forces the controller to request position reports rather than issuing blind instructions.

### 1.9 Pilot requests and "unable"

Expand `ReceivedMessage` / `ControllerEvent` derivation to handle:

```kotlin
sealed interface PilotRequest {
    data class RequestShortApproach(val aircraft: AircraftId) : PilotRequest
    data class RequestVisualApproach(val aircraft: AircraftId) : PilotRequest
    data class RequestRightBase(val aircraft: AircraftId) : PilotRequest
    data class RequestOrbit(val aircraft: AircraftId) : PilotRequest
    data class Unable(val aircraft: AircraftId, val instruction: InstructionRef, val reason: String?) : PilotRequest
}
```

Phase 5 implements `Unable` and `RequestVisualApproach`; others are documented constructors for Phase 6+.

### 1.10 Instruction rescission

`Cancel(ref: InstructionRef)` and `Disregard` as first-class instructions. Required for compounding sequencing — "disregard extend downwind, turn base" is the natural corrective.

### 1.11 Second aerodrome config test

Add a read-only test aerodrome with opposite-hand circuit (e.g. LOWI-like right-hand, vs LOWG left-hand) to flush single-field assumptions from the model. No new scenarios — just the config and a compilation/wiring test.

---

## 2. Arrival Sequence State — 5b (structural)

### 2.1 ArrivalSequence as first-class state

```kotlin
data class ArrivalSequence(
    val runway: RunwayId,
    val slots: List<ArrivalSlot>,
)

data class ArrivalSlot(
    val aircraft: AircraftId,
    val stableNumber: Int,
    val followTarget: FollowTarget?,
    val targetETAthreshold: SimTime?,
    val spacingAheadSeconds: Double?,
    val gate: ArrivalGate,
    val approachMode: ApproachMode,
)

sealed interface ArrivalGate {
    // Circuit-based gates
    data class Downwind(val phase: DownwindPhase) : ArrivalGate  // ABEAM, LATE
    data class BaseTurn(val phase: BaseTurnPhase) : ArrivalGate  // INITIATED, ROLLING_OUT
    // Final approach gates
    data class Final(val phase: FinalPhase) : ArrivalGate        // INTERCEPT, FOUR_NM, FAF, INSIDE_FAF
    data object LocaliserEstablished : ArrivalGate               // orthogonal to distance
    // Pre-sequence
    data object Inbound : ArrivalGate                            // known to APP, not yet in pattern
}

enum class ApproachMode { VISUAL, ILS, RNAV }  // mode, not gate — separate dimension

data class FollowTarget(
    val aircraft: AircraftId,
    val acquisitionState: AcquisitionState,
)

enum class AcquisitionState {
    NOT_ISSUED,               // number assigned but "following" not yet issued
    ISSUED,                   // "follow the Cessna on base" issued
    TRAFFIC_IN_SIGHT,         // pilot reports "traffic in sight" — controller still owns separation
    VISUAL_SEPARATION_APPLIED,// controller has judged geometry suitable and accepted reduced standard (Doc 4444 §5.11)
    LOST,                     // pilot reports "traffic lost" / belief decayed
}
```

**FollowTarget with acquisition state** is the operationally critical piece. "Number 3, follow the Cessna on base" is a sequence instruction — it does NOT by itself delegate separation. The lifecycle is:

1. `NOT_ISSUED` → `ISSUED`: controller tells pilot to follow traffic.
2. `ISSUED` → `TRAFFIC_IN_SIGHT`: pilot reports "traffic in sight". Controller still owns separation at this point.
3. `TRAFFIC_IN_SIGHT` → `VISUAL_SEPARATION_APPLIED`: controller judges geometry suitable and decides to apply visual separation (Doc 4444 §5.11) or delegate own-separation (Doc 4444 §8.9.4). This is a **controller decision**, not an automatic consequence of the pilot report. If geometry is unsuitable the controller retains prescribed separation despite the pilot having traffic in sight.
4. `VISUAL_SEPARATION_APPLIED` → `LOST`: pilot reports "traffic lost" or belief decays. Controller must immediately re-assume separation responsibility.

This is a Commitment lifecycle, not a display number.

### 2.2 Stable number semantics

The `stableNumber` is a **controller contract**: the number told to the pilot is the number they keep unless explicitly re-sequenced. Re-sequencing is exceptional and triggers re-emission.

Re-sequence triggers (exhaustive):
- **Go-around**: going-around aircraft drops out; all trailing numbers shift down. Cascading re-emission required.
- **Priority/emergency insertion**: trailing numbers shift up. Re-emission.
- **Pilot unable**: unable aircraft exits sequence; trailing shift down.
- **Runway change**: exceptional. Not modelled in Phase 5 (flagged as explicit exclusion).

Re-sequence **does not** trigger for:
- Spacing growing or shrinking naturally.
- FollowTarget changing gate (Cessna was on base, now on final — same Cessna, same number).

Re-sequence detection is a **stateful operation in belief update** — compare previous `stableNumber` per aircraft against newly derived. Cannot live in stateless companion derivation. This explicitly replaces the current `deriveCompanionOutputs` path for sequence info.

### 2.3 Sequence derivation

Pure function from beliefs (position, leg, speed, circuit/approach definition) → targetETAthreshold per aircraft.

Signal sources: aircraft position on circuit/approach path, ground speed, distance to threshold, preceding aircraft's position. Degraded mode when speed/heading missing (belief decay from §1.8 triggers this — return `null` for targetETA, force controller to maintain own-separation without estimated spacing).

**Prerequisite**: `AircraftObservation` (ControllerTypes.kt) currently has no `heading` or `groundSpeed` fields. These must be added in 5a (or early 5b) before any ETA derivation is possible — even the distance-only increment needs speed to convert distance to time.

**This is the highest-risk scope item in Phase 5.** Build incrementally: distance-only first (pure geometry), then speed-corrected ETA, then closure-rate refinement.

### 2.4 Projection to RunwayDutyState

Arrivals stop self-enqueuing in `enqueuePhase` (RunwayAssessment.kt). The existing three-phase transformer (release → enqueue → grant) is preserved, but arrival entries are projected from `ArrivalSequence` into the duty queue *before* `enqueuePhase` runs — only one enqueuer per aircraft class.

DEPARTURE / CROSSING / BACKTRACK enqueuing untouched. This gives two sources: `ArrivalSequence` for arrivals, `enqueuePhase` for everything else — with the duty queue as the merged projection.

**Migration must be atomic.** The current `queueEntryFor` (RunwayAssessment.kt) has a single `when` block covering both ARRIVAL and DEPARTURE. The TOWER_ARRIVAL branch must be deleted in the same commit that wires the ArrivalSequence projection, or an aircraft appears twice in the queue during incremental migration. Approach: split `enqueuePhase` into `enqueueArrivals` (replaced by projection) and `enqueueNonArrivals` (retained). Single atomic commit.

### 2.5 NumberInSequence as first-class instruction

`NumberInSequence(n, followTarget)` is readback-bearing (pilot must acknowledge). Emitted once per aircraft at sequence assignment, re-emitted on re-sequence only. Enters `pendingReadbacks` — unlike the current `SequenceInfo` companion which does not.

**Protocol module change required**: `requiredReadbackAtoms` currently returns `emptySet()` for sequence-related types. A new `ReadbackAtom` variant (e.g. `SequenceAcknowledgement(n)`) must be added to the protocol module. This is not just wiring — it extends the readback atom algebra.

---

## 3. Sequencing Behaviours — 5c (after 5a + 5b)

### 3.0 Supersession semantics (prerequisite for 3.1–3.4)

**Must be designed and landed before any compounding sequencing instruction.** Supersession acts on two separate stores: `issuedClearances` (checked by `NoActiveInstruction`) and `pendingReadbacks` (checked by `NoPendingReadback`). Both must be handled.

```kotlin
data class SupersessionRelation(
    val superseding: KClass<out Instruction>,
    val superseded: KClass<out Instruction>,
    val pendingReadbackPolicy: PendingReadbackPolicy,  // ABANDON or ABSORB
)

enum class PendingReadbackPolicy {
    ABANDON,  // superseded instruction's pending readback is GC'd (e.g. TurnBase supersedes ExtendDownwind)
    ABSORB,   // superseding instruction inherits the readback requirement (e.g. a speed update)
}
```

The `NoActiveInstruction` + `InstructionMatcher` seam (Guard.kt:247–252) is reused — supersession is a predicate on this seam, not a parallel mechanism. When a superseding instruction fires, it terminates the superseded instruction's active clearance and disposes of or absorbs its pending readback per policy.

### 3.1 ExtendDownwind v2

Completion predicate: **controller judges spacing adequate**, modelled as an explicit belief update driven by geometry (closure rate, lead position, spacing delta) — NOT a threshold crossing.

The twr1 deadlock (issue #23) arose from `NoActiveInstruction` blocking re-fire while the instruction could never complete. The fix has two parts:
1. Completion is a controller belief update (actively decided), not a passive state-transition gate.
2. **Instruction supersession** (§3.0): `TurnBase` explicitly supersedes `ExtendDownwind` with `ABANDON` policy. `TurnBase` can fire while `ExtendDownwind` is still active — it doesn't *conflict*; it *resolves*.

### 3.2 TurnBase

Explicit instruction — not inferred from position reports. Already half-landed via `ARR-TURN-BASE` (tracker item #4). Promote the action type, register a readback atom, add supersession of `ExtendDownwind`. Secretly trivial.

### 3.3 Speed control

`AdjustSpeed` / `MaintainSpeed` with feasibility check against aircraft-type speed bands. For APP: standard ladder 250kt/25nm → 210kt/15nm → 180kt/8nm → 160kt/4nm baked into APP feasibility as parameterised constraints, even if trivially applied in Phase 5. This prevents late-compress scenarios.

### 3.4 Orbit / 360

`Orbit(direction)` — the APP-side equivalent of extend-downwind for when heading/speed/vector options are exhausted or when the delay is too large for in-trail spacing alone. Readback-bearing.

### 3.5 Essential traffic as separate obligation

**Not a companion payload.** Essential traffic information (Doc 4444 §6.5.3, §11.4.2.1) is mandatory when prescribed separation is not applied — classically VFR vs IFR in Class D, or reduced-separation authorisations.

Modelled as its own obligation with:
- **Trigger**: applicable separation standard not met or not applied.
- **Timing**: before the conflicting manoeuvre, not stapled to the sequence number. Separate transmission.
- **Expiry**: when the conflict geometry is no longer applicable — not a boolean flip. The same pair of aircraft can generate multiple essential traffic obligations as conflict geometry evolves (converging → overtaking, new conflicting aircraft appears). Expiry predicate is a function of conflict geometry, re-evaluated per cycle.
- **Symmetric**: essential traffic to departures about arriving traffic (the inverse flow most plans forget — Doc 4444 §6.5.3 doesn't distinguish direction).

### 3.6 Break-off vs go-around

Distinct obligation owners:
- `BreakOff`: ATC-initiated (controller judges situation unsafe, tells pilot to discontinue approach). Controller instruction.
- `GoAround`: pilot-initiated (pilot judges approach unstable or runway not clear). Pilot report.

Both enter the same go-around path physically, but they create different obligation chains. `BreakOff` creates a missed-approach obligation per Doc 4444 §6.5.4 (published procedure or alternative instructions). `GoAround` creates a pilot-reported state change that the controller must acknowledge and re-sequence around.

### 3.7 Missed approach

Go-around mid-sequence triggers:
1. Going-around aircraft follows published missed approach procedure (or alternative instructions if issued).
2. Cascade re-sequence: all trailing `stableNumber` values shift. Re-emission per §2.2.
3. FollowTarget references to the going-around aircraft become `LOST` for any aircraft that had it as follow target.

Phase 5 scenario coverage: 3-arrival sequence with go-around of #2.

---

## 4. APP↔TWR Coordination — 5d

### 4.1 Typed coordination messages (OLDI-style)

Distinct from pilot-facing transmissions. These are controller-to-controller state deltas:

```kotlin
sealed interface CoordinationMessage {
    val from: ControllerId
    val to: ControllerId
    val aircraft: AircraftId
    val time: SimTime

    // Transfer of control (Doc 4444 §6.3)
    data class TransferOfControl(...) : CoordinationMessage
    // Transfer of communication (Doc 4444 §10.1) — separable from control
    data class TransferOfCommunication(...) : CoordinationMessage
    // Approach clearance issued (APP → TWR notification)
    data class ApproachClearanceIssued(val approach: ApproachType, ...) : CoordinationMessage
    // Sequence information (APP → TWR)
    data class SequenceUpdate(val sequence: ArrivalSequence, ...) : CoordinationMessage
    // Speed restriction still in effect (APP → TWR)
    data class SpeedRestriction(val constraint: SpeedConstraint, ...) : CoordinationMessage
    // Release (TWR → APP, for departures into approach airspace)
    data class DepartureRelease(...) : CoordinationMessage
}
```

**Transfer of communication vs transfer of control** are explicitly separate (Doc 4444 §10.1 vs §6.3). Communication transfer = "contact tower 118.5". Control transfer = "tower now owns separation". These can happen at different times; LVP/reduced-sep scenarios depend on this distinction.

### 4.2 Handoff gate

Not a hardcoded distance. Configurable per aerodrome role as a **Letter of Agreement parameter**:

```kotlin
data class HandoffGate(
    val condition: HandoffCondition,  // LOCALISER_ESTABLISHED, VISUAL_PATTERN_JOIN, DISTANCE_FROM_THRESHOLD
    val fix: PointId?,                // optional: handoff fix
    val maxDistance: NauticalMiles?,   // e.g. 4-6 NM for Class D tower with TMA approach
)
```

Realistic gate: `localiserEstablished AND insideHandoffFix` — per Doc 9426 Part II on Letters of Agreement. At 8nm the aircraft may not yet be established and APP still needs speed control to build the gap.

### 4.3 Role split

- **APP** owns: approach clearance, sequencing (ArrivalSequence management), speed control, vectoring, essential traffic to arrivals, handoff initiation.
- **TWR** owns: landing clearance, runway duty management, surface wind on landing/takeoff (already landed — tracker #3, pipeline enrichment), essential traffic to departures, vacate, go-around acknowledgement.

APP and TWR are separate controller instances with separate BeliefState, running through the same pipeline independently. Each sees the other's coordination messages as `ReceivedMessage` on the next cycle. Shared state = zero. This prevents the single-writer invariant violation that second-role-on-same-process would create.

---

## 5. Feasibility Gates — 5e (rides on 5a)

### 5.1 Arbitrator pre-emission check

After rule selection, before emission: `feasibility(instruction, view, beliefs)` → `Feasible | Infeasible(reason)`. Infeasible instructions are rejected and logged with the reason string. This prevents the twr1 #22 class of bugs (physically impossible instructions).

### 5.2 Distance gate for landing clearance

Tracker #38: no minimum final distance for landing clearance. Becomes a feasibility predicate on `ClearedToLand`: `aircraftInsideFAF OR (visualApproach AND runwayInSight)`. Existing `RuleGuard` on `ARR-LAND` retained — it controls when the rule fires; feasibility controls whether the *instruction* is coherent.

### 5.3 Downwind-only gate for TurnBase

Tracker #37: ARR-TURN-BASE fires from base leg (should only fire from downwind). Becomes a feasibility predicate on `TurnBase`: `aircraft.currentLeg == DOWNWIND`. Again: existing guard retained, feasibility added.

---

## 6. Verification — 5f (continuous)

### 6.1 Harness scenarios

| Scenario | What it tests |
|----------|---------------|
| 3-arrival sequence, long downwind | Stable number, follow-target acquisition, spacing derivation |
| 4-arrival with insertion (priority) | Re-sequence cascade, re-emission, number stability for non-affected |
| Go-around of #2 mid-sequence | Missed approach, cascade renumber, follow-target LOST, re-sequence |
| Departure between arrivals | Spacing pressure, essential traffic (inverse flow: to departure about arrival) |
| Conditional line-up behind #3 arrival | Follow-target acquisition gating the clearance |
| APP→TWR handoff | Communication vs control transfer, handoff gate config |
| Pilot "unable" on speed | ReadbackVerdict.Refused, re-sequence |
| FollowTarget geometry rejection | Two arrivals, lead on late downwind (poor geometry), follower reports "traffic in sight" — controller must NOT apply visual separation (Doc 4444 §5.11 negative path) |
| Supersession ABANDON path | ExtendDownwind issued, TurnBase supersedes before readback — pendingReadbacks contains only TurnBase |

### 6.2 Two-controller test infrastructure

The existing `towerView()` / `testControllerDecide()` fixtures support single-controller scenarios only. Testing APP+TWR requires a **new test harness layer** — this is a sub-phase deliverable, not a single test:

1. Second `ControllerView` factory: `approachView()` with `role = APPROACH`, own `ControllerId`.
2. Coordination message router: routes `CoordinationMessage` outputs from one controller's `ControllerDecisionResult` into the other's `receivedMessages` on the next cycle.
3. `testWorld()` extension: approach fixes and airspace volume (currently absent — `testWorld()` has no `airspace` entries).

This infra must land before the APP→TWR handoff scenario (and before 5d.15–16).

### 6.3 Arrival-path test migration

The four existing `RunwayDutyQueueTest` tests exercise arrival enqueuing through `queueEntryFor`'s TOWER_ARRIVAL branch. The atomic migration (§2.4) deletes that branch. These tests are not scaffold — they test real ICAO 4444 §7.10 contracts (arrival priority, preemption, FIFO). They must be **rewritten in the same commit** to exercise the new ArrivalSequence projection path.

**Hidden dependency**: ArrivalSequence fixture machinery (slot construction, gate detection) must be built *before* the atomic migration commit, not alongside it. The migration commit wires the projection AND rewrites the tests in one shot.

### 6.4 Feasibility verification strategy

Feasibility (§1.5) is tested **through the pipeline scenarios**, not through isolated predicate unit tests. The assertion is: infeasible instructions never appear in `ControllerDecisionResult.outputs`. This avoids scaffold tests while ensuring the feasibility layer is exercised end-to-end.

### 6.5 Citation audit

Test asserts every `AtcRule` in the registry carries a non-null `(doc, edition, section)` triple with `doc` and `edition` matching a known whitelist. Catches orphaned rules.

### 6.6 Clean-context agent reviews

ATC law, general, and phraseology agents review Phase 5 output in clean contexts (no prior-fix priming, per feedback memory on agent review process). One review per sub-phase completion, not just at the end.

---

## Dependency graph

```
PIN DOC 4444 EDITION (prerequisite)
       |
      5a (foundations — refactor, no new behaviour)
      / \
    5b   5d.14 (coordination message ADT only)
     |     |
  5c.0  5d.15-16 (handoff gate, role split — needs 5b gates)
  (supersession — prerequisite for all compounding instructions)
     |
  5c.1-7 (sequencing behaviours — after 5a + 5b + 5c.0)
     \   /
      5e (feasibility gates — rides on 5a, validated against 5c)
       |
      5f (continuous, final scenario audit after 5e)
```

5b and 5d.14 can run in parallel after 5a.
5c.0 (supersession) requires 5a only — can start as soon as 5a lands.
5c.1–7 require 5a + 5b (spacing predicates) + 5c.0 (supersession semantics).
5d.15-16 requires 5b (gate taxonomy).
5e can start after 5a (feasibility ADT) but validates against 5c (instruction inventory).

---

## Explicitly excluded (Phase 6+)

| Item | Why deferred | Tracker |
|------|-------------|---------|
| Wake separation arithmetic (H/M/L/J + RECAT-EU) | Safety layer | #41, #34 |
| Parallel / dependent runway operations | Single-runway first | — |
| RNP/RNAV approach types beyond single ILS/visual | Instruction ADT admits them; no procedure logic yet | — |
| LVP, CAT II/III | Predicate modifier on existing rules, not parallel rule set | — |
| STAR / terminal area | Need APP fully operational first | — |
| Holding / stack management | Distinct from arrival sequencing | §2 architecture doc |
| Runway change mid-sequence | Rare, catastrophic if modelled as renumber | — |
| Workload/processing-speed model | Reserve hook in feasibility; build in Phase 6 | — |
| Belief-delta event derivation (general) | §1.7 covers reports; broader substrate is Phase 6 | #33 |
| Structured correction on incorrect readback | Three-state landed; structured correction deferred | #28 |

---

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Sequence derivation (§2.3) scope explosion | HIGH | HIGH | Build incrementally: distance-only → speed-corrected → closure-rate. Ship distance-only first. AircraftObservation must gain heading/groundSpeed in 5a. |
| Supersession relation (§3.0) interaction with pending-readback lifecycle | MEDIUM | HIGH | Supersession design (§3.0) is now a sequenced prerequisite within 5c, landed before any compounding instruction. ABANDON vs ABSORB policy per relation. |
| ReadbackVerdict 2→4 widening breaks exhaustive `when` matches | MEDIUM | MEDIUM | Specify processReadback routing for all four variants (§1.6) before widening. Routing for Missing/Refused is load-bearing design. |
| ArrivalSequence↔enqueuePhase dual-writer during migration | MEDIUM | HIGH | Atomic migration: delete TOWER_ARRIVAL branch of queueEntryFor in same commit as projection wiring (§2.4). Split enqueuePhase into arrival/non-arrival sub-phases. |
| Two-controller belief isolation under test | MEDIUM | MEDIUM | Dedicated integration tests with APP+TWR both running. Shared state = zero is a test assertion, not just a design claim. |
| FollowTarget TRAFFIC_IN_SIGHT → VISUAL_SEPARATION_APPLIED controller decision | MEDIUM | MEDIUM | Explicit geometry-suitability check in the controller — not automatic on pilot report. Doc 4444 §5.11 compliance. |
| Two-controller test infrastructure undercosted | HIGH | MEDIUM | Acknowledged as sub-phase deliverable (§6.2), not a single test. Approach view factory + coordination router + testWorld airspace extension. Must land before 5d.15–16. |
| Arrival-path test rewrite blocked on fixture machinery | MEDIUM | HIGH | ArrivalSequence fixture helpers must exist before atomic migration commit (§6.3). Hidden dependency on 5b fixture work preceding 5b production code. |
| Citation triple completeness | LOW | MEDIUM | Automated audit (§6.5). Fail CI on orphaned rules. |
| Gate taxonomy churn | MEDIUM | LOW | Gates are a sealed type; additions are source-compatible. Over-specify now rather than under-specify. |

---

## Review log

| Pass | Reviewer | Key findings | Resolution |
|------|----------|-------------|------------|
| 1 | Staff engineer | 5c depends on 5b (re-order); guards ≠ feasibility; stable number is stateful; ReadbackVerdict needs Refused; sequence derivation is scope risk; APP must be separate process; missing: rescission, unable, belief decay | All incorporated in v1→v2 revision |
| 1 | ATC general | FollowTarget needs acquisition state; gate taxonomy too coarse; essential traffic is separate obligation; vectoring verbs required; "spacing reached" not observable; transfer comm ≠ control | All incorporated in v1→v2 revision |
| 2 | Staff engineer | ReadbackVerdict is 2-branch not 3 (corrected); processReadback routing unspecified (specified §1.6); dual-writer on ArrivalSequence→duty migration (atomic migration §2.4); supersession must be sequenced first in 5c (§3.0 prerequisite); NumberInSequence needs new ReadbackAtom; AircraftObservation lacks heading/groundSpeed | All incorporated in v2→v3 revision |
| 2 | ATC general | ACQUIRED ≠ own-separation delegated — need TRAFFIC_IN_SIGHT/VISUAL_SEPARATION_APPLIED split (§2.1 corrected, Doc 4444 §5.11); essential traffic expiry is geometry function not boolean (§3.5 corrected); surface wind on landing (already tracker #3, cross-referenced §4.3) | All incorporated in v2→v3 revision |
| 3 | Test architecture | FollowTarget §5.11 negative path untested — added scenario (§6.1); two-controller test infra undercosted — promoted to sub-phase deliverable (§6.2); arrival-path tests break on atomic migration — fixture dependency documented (§6.3); feasibility must be pipeline-tested not unit-tested (§6.4); supersession ABANDON testable without mocks — added scenario (§6.1) | All incorporated in v3→v4 revision |
