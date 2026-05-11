# G3a-obstruction-continue-approach — three-state ATC ladder per CAP 413 §4.55-4.56

## Overview

fn-12 shipped a two-state obstruction-handling ladder (clear / GO AROUND). CAP 413 §4.55-4.56 + ICAO Doc 4444 §12.3.4.16(d) define a three-state ladder: when the runway is obstructed at final but **expected to be available in good time for a safe landing**, the controller delays landing clearance via `CONTINUE APPROACH` rather than firing GA. This epic closes the `D-PASS-g3a-obstruction-continue-approach` deferment filed in fn-12.

**Scope is narrow.** Context-scout revealed that the protocol leaf (`Instruction.ContinueApproach`), action (`ContinueApproachAction`), existing rule (`ARR-CONTINUE` at `TowerArrival.kt:462-475`), pilot routing (`is ContinueApproach -> mission` no-op at `PilotCognitive.kt:782`), readback machinery, `ContinueApproachReason` enum, and reason-inference logic all already exist. The gap is that the existing `ARR-CONTINUE` rule guards on `Not(RunwayAccessGranted)` or `Not(RunwayPhysicallyClear)` — **NOT** on `RunwayObstructed`. When an obstruction appears, fn-12's `obstructionGoAroundRule` (SAFETY urgency) wins arbitration and immediately issues GA. v1 silently elides the doctrinal middle state.

The fix is a new `ObstructionClearsInTime` guard atom + a new rule `ARR-CONTINUE-APPROACH-OBSTRUCTION` gated on `RunwayObstructed AND ObstructionClearsInTime`, plus narrowing the existing `obstructionGoAroundRule` guard with `Not(ObstructionClearsInTime)` for mutual exclusion.

**Scenario:** single AI aircraft at LOWG, mission `CircuitTraining(outcomes = listOf(FullStop))`. On final, world authors a **short-TTL** obstruction (e.g. `clearsAt = T_obs + 5s`). With CONTINUE APPROACH wired: `ObstructionClearsInTime` evaluates true (5s + 10s safety margin = 15s ≤ ETA-to-threshold); controller issues `ContinueApproach` + companion `RunwayObstructionInformation`; pilot continues current mission unchanged (no read-back transmission; OutstandingCoordination later superseded — see Boundary deferment `D-PASS-continue-approach-pilot-readback`); obstruction expires; `RunwayObstructed` becomes false; pre-clearance gate ungates; existing `ARR-LAND` rule fires `ClearedToLand`; aircraft reads back landing clearance, lands, vacates.

## Boundaries / non-goals

- **Out: post-clearance cancellation variant** (CAP 413 §4.53 — `CONTINUE APPROACH, CANCEL LANDING CLEARANCE`). v1 ships only the **pre-clearance** CONTINUE APPROACH path (`AwaitApproach` stage). The post-clearance case where obstruction appears AFTER `ClearedToLand` issued continues to fire GA via fn-12's `obstructionGoAroundRule` in `LandingClearanceIssued`/`AwaitLandedObserved`. Filed as `D-PASS-g3a-continue-approach-cancel-clearance`.
- **Out: in-circuit (downwind/base) CONTINUE APPROACH.** When aircraft is on downwind/base (not final) and obstruction appears, no rule fires. Aircraft completes the circuit; by the time it's on final the obstruction has either cleared (no rule needed) or it's still there (the new rule re-evaluates `ObstructionClearsInTime` based on then-current state). Operationally this means the aircraft may "burn" an approach attempt before getting CONTINUE APPROACH on final. Filed as `D-PASS-g3a-continue-approach-in-circuit` (overlaps with `D-PASS-g3a-obstruction-orbit-hold`).
- **Out: `POSSIBLE GO AROUND` borderline-band variant** (ICAO §12.3.4.16(d) bracketed form). Practice-scout's recommended "10s base + 10s borderline = `[POSSIBLE GO AROUND]` appended" — adding this variant requires extending the `Instruction.ContinueApproach` body (or carrying it via the companion) and is doctrinally distinct phraseology. Filed as `D-PASS-g3a-continue-approach-possible-ga-variant`.
- **Out: subjective-judgment refinement.** v1's `ObstructionClearsInTime` predicate is a simple kinematic estimate (distance-to-threshold / groundspeed) with fixed `safetyMargin = 10s`. Real ATC's "experience" includes wind, configuration, traffic congestion. Filed as `D-PASS-g3a-continue-approach-subjective-judgment`.
- **Out: multi-aircraft CONTINUE APPROACH** (sequencing). Single-aircraft case in v1. Filed as `D-PASS-g3a-continue-approach-sequencing`.
- **Out: RegulationDatabase transcription-drift fixes** caught by docs-scout: `ICAO4444_8_9_6_1_8` says "shall" but source says "should"; `ICAO4444_7_10_2` titled "Go-around instruction" but source is "Clearance to land". Filed as `D-PASS-regdb-transcription-drift`.
- **Out: pilot read-back transmission for `Instruction.ContinueApproach`.** `InstructionReadback.kt:115` returns `emptySet()` for `ContinueApproach`; `buildReadback()` returns `None` for empty atoms — existing `ARR-CONTINUE` rule (fn-12 predecessor) has no pilot read-back transmission today. CAP 413 §4.55 doctrinally requires `Continue approach, G-CD` pilot read-back; v1 of fn-13 aligns with the existing in-codebase pattern (no transmission; OutstandingCoordination ledger tracks instruction; witness suppresses re-fire). Adding a real read-back atom is a separate cross-rule fix affecting BOTH fn-12's existing `ARR-CONTINUE` and fn-13's new rule. Filed as `D-PASS-continue-approach-pilot-readback`.

## Strategy Alignment

Active tracks served by this plan:

- **Runtime simulator** — closes the silent-doctrine-elision of CAP 413's three-state ATC ladder (the un-deferred deferment from fn-12). Adds the fourth reactive path at `AwaitApproach` (after self-initiated GA, pilot-trained GA, ATC-instructed-obstruction GA). Triple-covered reactive-GA + ATC-instructed-CONTINUE-APPROACH = quadruple-covered approach decision space.

## Decision context

### 1. Re-use existing machinery — narrow scope (high confidence — context-scout finding)

The CONTINUE APPROACH surface already exists end-to-end. v1 of this epic does NOT add:
- A new protocol leaf — `Instruction.ContinueApproach(target, reason)` exists at `Instruction.kt:854-879`
- A new pilot reaction — `PilotCognitive.kt:782` already routes to `mission` no-op (correct: pilot continues current path)
- A new readback shape — `InstructionReadback.kt:113-123` returns `emptySet()` (matches CAP 413 §4.55 plain echo)
- A new Coordination type — `OutstandingCoordination` is uniform across instructions; `readbackAdvancesToStage = null` on the rule keeps stage at `AwaitApproach`
- A new `ControllerEvent` leaf — CONTINUE APPROACH is a controller output, not an event

The novelty is the **guard** and the **rule placement**.

### 2. `ObstructionClearsInTime` guard atom — parameterless `data object` (high confidence)

Mirrors `RunwayObstructed` at `Guard.kt:547-553`. Reads:
- `runway` from `commitment.runway ?: ctx.beliefs.activeRunway`
- `obstruction` from `ctx.beliefs.runwayObstructions[runway]` — returns `false` (fail-closed) if no obstruction
- `aircraft.groundSpeed` (nullable `Knots?`) — returns `false` if missing
- `ac.coords` (continuous surveillance position — NOT `worldIndex.positions[ac.position]` which is graph-snapped and can mislead the predicate) and `ctx.worldIndex.thresholdByRunway[runway]` — for distance-to-threshold. Per codex iter 5: snap error from graph-position lookup could make an unsafe "clears-in-time" decision; use the continuous surveillance position.
- `ctx.time: SimTime` (codex iter 2 verified `OperatorContext` field name is `time`, NOT `now`)

Predicate (all in milliseconds; verified knots→m/s via `groundSpeed.value * 1852.0 / 3600.0` per codex iter 3): `(obstruction.clearsAt.millis - ctx.time.millis) + OBSTRUCTION_CLEAR_SAFETY_MARGIN_MS <= (distanceToThresholdM / (groundSpeed.value * 1852.0 / 3600.0) * 1000.0).toLong()`.

**Constants** (named, not inline):
- `OBSTRUCTION_CLEAR_SAFETY_MARGIN_S = 10` (practice-scout: smallest defensible round value > pilot reaction time + radio latency budget; interpolated from FAA cat I/II separation distances and stable-approach gates since CAP 413 / ICAO 4444 give no quantitative threshold)

**Fail-closed semantics**: any missing input → `false` → GA wins. Conservative direction (defaults to safer GA when uncertain).

### 3. Rule placement — `AwaitApproach` only in v1 (high confidence)

The new `ARR-CONTINUE-APPROACH-OBSTRUCTION` rule lives in `stageRules[AwaitApproach]` ONLY in v1. Rationale:
- Pre-clearance (in `AwaitApproach`): controller hasn't issued `ClearedToLand` yet. CONTINUE APPROACH is the canonical doctrine per CAP 413 §4.55. Aircraft continues approach.
- Post-clearance (`LandingClearanceIssued`/`AwaitLandedObserved`): doctrine is CAP 413 §4.53 — cancel landing clearance via `CONTINUE APPROACH, CANCEL LANDING CLEARANCE`. This is a **different phraseology** (the cancel-clearance variant) and a different operational scenario. Deferred as `D-PASS-g3a-continue-approach-cancel-clearance`.

Fn-12's `obstructionGoAroundRule` continues to fire at `LandingClearanceIssued` and `AwaitLandedObserved` stages unchanged. Only its `AwaitApproach`-stage instance gets the `Not(ObstructionClearsInTime)` guard narrowing.

### 4. Mutual exclusion via guard narrowing (high confidence)

The new rule's guard: `AllOf(AnyOf(OnApproach, OnCircuitLeg(FINAL)), RunwayObstructed, ObstructionClearsInTime, Not(ObstructionGoAroundAlreadyIssuedThisAttempt), Not(ContinueApproachAlreadyIssuedThisAttempt))`.

Fn-12's `obstructionGoAroundRule` at `AwaitApproach` is narrowed: add `Not(ObstructionClearsInTime)` to its guard. The two rules become **mutually exclusive by guard construction**; both can coexist in the same stage list.

The `obstructionGoAroundRule` at `LandingClearanceIssued` and `AwaitLandedObserved` stages is **NOT** narrowed — v1 keeps post-clearance obstruction → GA (per Boundary #1).

### 5. No-refire witness for CONTINUE APPROACH (high confidence)

Sibling to fn-12's `obstructionGoAroundIssuedThisAttempt`: add `continueApproachIssuedThisAttempt: Boolean` on `Commitment`. Set on committed-output (post-arbitration). Re-armed on next `Report(Downwind)` in `reconcileTowerArrival` OR on commitment replacement. Same lifecycle as fn-12's GA witness.

**Why a separate witness** vs reusing the GA witness: the controller may issue CONTINUE APPROACH, the obstruction may persist longer than expected, and the controller should then escalate to GA. If we reused the GA witness, the second-stage GA would be suppressed. Separate witness allows the natural transition `CONTINUE APPROACH → re-evaluate next tick → either persist CONTINUE APPROACH (witness still set, NoPendingReadback gates re-fire) or escalate to GA (different witness, GA rule's guard becomes true when `Not(ObstructionClearsInTime)` flips)`.

**Alternative considered and rejected**: relying on `NoPendingReadback(instructionOfType<ContinueApproach>())` alone (the mechanism existing `ARR-CONTINUE` uses). Rejected because the readback resolves quickly; subsequent ticks would re-fire CONTINUE APPROACH if `ObstructionClearsInTime` still holds, causing one instruction per re-evaluation tick. The witness re-arms only on downwind report or commitment replacement, providing approach-attempt-scoped suppression.

### 6. `ContinueApproachReason.RUNWAY_OBSTRUCTED` enum extension (high confidence)

Extend `ContinueApproachReason` (at `Instruction.kt:854-879`) with new variant `RUNWAY_OBSTRUCTED`. **`inferContinueApproachReason` UNCHANGED** — per codex iter 2/3: its signature `(ac, ctx)` lacks `Commitment`, and extending would require touching every call site. Instead, the new `ObstructionContinueApproachAction` constructs `Instruction.ContinueApproach(target, reason = ContinueApproachReason.RUNWAY_OBSTRUCTED)` directly. The existing `ContinueApproachAction` (for the traffic-driven `ARR-CONTINUE` rule) continues to use `inferContinueApproachReason` unchanged. **Audit exhaustive `when` over `ContinueApproachReason`** (grep `is ContinueApproachReason` and `when (` over the enum) and add explicit arms; NO `else` clauses.

### 7. Companion `RunwayObstructionInformation` reuse + trace regs split (high confidence — refined per codex iter 2)

Per ICAO Doc 4444 §7.4.1.4.1(c) — reason on radio is mandatory (post-clearance). For the pre-clearance CONTINUE APPROACH path, ICAO §12.3.4.16(d) + CAP 413 §4.55 govern; CAP 413 §4.65 (missed-approach phraseology, hardcoded into fn-12's companion trace) is **wrong for CONTINUE APPROACH**.

The new action populates `ProposedAction.obstructionInfo = ObstructionInfo(runway, clearsAt)`, and `deriveCompanionOutputs` at `Controller.kt:817-835` emits `RunwayObstructionInformation` alongside the `ContinueApproach` instruction. **Companion-trace regulation refs must vary by primary instruction**:

- For `Instruction.GoAround` (fn-12's path): companion trace regs `ICAO4444_7_4_1_4_1, ICAO4444_8_9_6_1_8, CAP413_4_65` (unchanged from fn-12)
- For `Instruction.ContinueApproach` (this epic's path): companion trace regs `CAP413_4_55, CAP413_4_56, ICAO4444_12_3_4_16, ICAO4444_8_9_6_1_8` (CAP 413 §4.65 AND ICAO §7.4.1.4.1 BOTH EXCLUDED — see iter 7 finding)

**Implementation**: extend `ObstructionInfo` carrier with an optional `companionTraceRegs: List<RegulationRef>` field (or sibling) populated by the action. `deriveCompanionOutputs` reads it; if null, fall back to fn-12's hardcoded defaults (which works for the GA case). The new action sets explicit `companionTraceRegs` for CONTINUE APPROACH. Controller-level test (R8) asserts the ContinueApproach companion does NOT cite `CAP413_4_65`.

Either:
- (a) Extend existing `ContinueApproachAction` to optionally populate `obstructionInfo` based on whether `RunwayObstructed` holds at action-resolve time, OR
- (b) Add a sibling `ObstructionContinueApproachAction` for the obstruction-specific rule (mirrors fn-12's `ObstructionGoAroundAction` pattern).

Pick (b) at task time — cleaner separation, matches fn-12's pattern, easier trace-disambiguation. The existing `ContinueApproachAction` continues to handle the `Not(RunwayAccessGranted)` / `Not(RunwayPhysicallyClear)` cases without modification.

### 8. Action returns `Either<ActionResolutionFailure, ProposedAction>` (high confidence)

Typed-error path per fn-12 R8 convention. If obstruction is somehow missing at action-resolve time (race condition between guard evaluation and action invocation), return `ActionResolutionFailure(...).left()`. No `!!`.

### 9. Sim test scenario timing (high confidence)

Author obstruction with `clearsAt = T_obs + 5.seconds` where `T_obs` is during the aircraft's `AwaitApproach` stage. ETA-to-threshold at that point should be ~30-60s (aircraft on long final). `5s + 10s margin = 15s` ≪ ETA → `ObstructionClearsInTime` holds → CONTINUE APPROACH fires.

By contrast, fn-12.3's `G3aRunwayObstructionTest` uses `clearsAt = T_obs + 60.seconds` and authors AFTER `ClearedToLand` (in `LandingClearanceIssued` stage), so it exercises the GA path. The two tests are complementary — same fixture, different timing produces different doctrinal responses.

### 10. Pin discipline — same as fn-12.3 (high confidence per memory)

Per `sim-test-pins-must-compare-against-2026-05-10`: use decision-cycle time (via `nextTransmissionId` mint-id walk / `findEmittingCycleMs` helper from fn-12.3) for all controller-decision invariant pins; NOT transmission-start time. The fn-12.3 helpers are reusable.

## Acceptance

- **R1:** `ObstructionClearsInTime` guard atom added at `controller/.../bdi/Guard.kt`, parameterless `data object` mirroring `RunwayObstructed`. Reads `commitment.runway`, `beliefs.runwayObstructions[runway].clearsAt`, `ac.groundSpeed`, **`ac.coords` (continuous surveillance position, NOT `worldIndex.positions[ac.position]` graph-snapped)**, threshold coordinates from `ctx.worldIndex.thresholdByRunway[runway]`, `ctx.time`. Predicate: `(clearsAt.millis - ctx.time.millis) + OBSTRUCTION_CLEAR_SAFETY_MARGIN_MS <= (distanceToThresholdMetres / (groundSpeed.value * 1852.0 / 3600.0) * 1000.0).toLong()`. Fail-closed on any missing input. Constants `OBSTRUCTION_CLEAR_SAFETY_MARGIN_S = 10` named, not inline. R8 controller test must include the **snap-vs-coords divergence pin** — seed `ac.position` (graph-snapped) and `ac.coords` (continuous) far enough apart to flip the predicate; assert the guard uses `coords`.
- **R2:** `ContinueApproachReason.RUNWAY_OBSTRUCTED` enum variant added at `Instruction.kt:854-879`. **`inferContinueApproachReason` UNCHANGED** — the new `ObstructionContinueApproachAction` sets the reason directly (signature `(ac, ctx)` lacks `Commitment` to detect the obstruction case). All exhaustive `when (reason: ContinueApproachReason)` sites updated with explicit arms for `RUNWAY_OBSTRUCTED` (NO `else`).
- **R3:** `ObstructionContinueApproachAction : RuleAction` added at `controller/.../bdi/Action.kt`, sibling to `ObstructionGoAroundAction`. Returns `Either<ActionResolutionFailure, ProposedAction>`. Populates `ProposedAction.obstructionInfo = ObstructionInfo(runway, clearsAt)` so the existing `deriveCompanionOutputs` emits `RunwayObstructionInformation` companion.
- **R4:** New rule `ARR-CONTINUE-APPROACH-OBSTRUCTION` added to `stageRules[AwaitApproach]` block in `TowerArrival.kt`. Priority-ordered BEFORE `obstructionGoAroundRule` (the existing AwaitApproach instance of fn-12's GA rule). Guard: `AllOf(AnyOf(OnApproach, OnCircuitLeg(FINAL)), RunwayObstructed, ObstructionClearsInTime, Not(ObstructionGoAroundAlreadyIssuedThisAttempt), Not(ContinueApproachAlreadyIssuedThisAttempt))`. Action: `ObstructionContinueApproachAction`. `nextStage = null` (no advancement; rule self-gates via the witness — `ContinueApproachAlreadyIssuedThisAttempt` is the canonical no-refire gate, NOT `NoPendingReadback`). `urgency = Urgency.TIME_SENSITIVE` (matches existing `ARR-CONTINUE` rule). `regulations = listOf(CAP413_4_55, CAP413_4_56, ICAO4444_12_3_4_16, ICAO4444_8_9_6_1_8)`.
- **R5:** Existing `obstructionGoAroundRule` (at `TowerArrival.kt:211-227`) guard narrowed: add `Not(ObstructionClearsInTime)` term — **only when used in `stageRules[AwaitApproach]`**. The same rule's `LandingClearanceIssued` and `AwaitLandedObserved` placements are UNCHANGED (post-clearance always GA in v1 per Boundary #1). Implementation: factor two rule objects `obstructionGoAroundRuleAwaitApproach` (with `Not(ObstructionClearsInTime)` in guard) and `obstructionGoAroundRulePostClearance` (original guard). **Both variants MUST keep `id = "ARR-GO-AROUND-RUNWAY-OBSTRUCTED"`** because fn-12's `obstructionGoAroundIssuedThisAttempt` witness-setting logic in `advanceCommittedStages` (or wherever fn-12 placed it) may key on the rule id. Verify at task time and either keep the shared id OR refactor the witness-key to a constant if id-collision causes test/log ambiguity.
- **R5-supersession-extension:** extend fn-12's supersession-relation table to encode TWO new entries:
  - `GoAround supersedes ContinueApproach` — for the escalation path (CONTINUE APPROACH issued → predicate flips false → GA fires).
  - `ClearedToLand supersedes ContinueApproach` AND `ClearedTouchAndGo supersedes ContinueApproach` — for the **normal success path** (obstruction clears, fresh landing clearance issued). Without these, the stale `ContinueApproach` coordination persists in the ledger and could misfire on readback or block subsequent rule firings via `NoPendingReadback` gates.
  Apply across all coordination states `{Issued, Querying, Reissued, LostCommsDeclared}` consistent with fn-12 R7-supersession coverage. Add to the same `applySupersessionCleanup` path that fn-12 R7 extended. Controller-level tests in R8 cover BOTH paths (escalation supersession AND normal-success supersession).
- **R6:** Approach-attempt-scoped witness `continueApproachIssuedThisAttempt: Boolean` on `Commitment`, sibling to `obstructionGoAroundIssuedThisAttempt`. Set on committed-output (post-arbitration+certification). Re-armed on next `Report(Downwind)` in `reconcileTowerArrival` OR on commitment replacement. New guard atom `ContinueApproachAlreadyIssuedThisAttempt` reads the witness.
- **R7:** New `RegulationDatabase` entries:
  - `CAP413_4_53` — cancellation of issued landing clearance phraseology (note: deferred operational use — entry added for future-proofing per fn-12 convention)
  - `CAP413_4_56` — CONTINUE APPROACH is not a landing clearance
  - `ICAO4444_12_3_4_16` — landing clearance approach-instruction phraseology block (CONTINUE APPROACH [POSSIBLE GO AROUND])
  - `CAP413_4_55` — **upgrade in place** (existing placeholder principle is light; tighten per docs-scout's recommended principle text). Title: "Continue approach — runway obstructed at final". Edition: CAP_413 27th ed. (2023).
- **R6-phraseology-rendering:** audit phraseology rendering and utterance-duration calc for `ContinueApproachReason.RUNWAY_OBSTRUCTED`. CAP 413 §4.55 says "may or may not explain why" — verbalizing the reason in the primary `ContinueApproach` transmission is optional. Decision: **leave the verbal reason OUT of the primary instruction** (matches fn-12's pattern: companion `RunwayObstructionInformation` carries the obstruction info). Verify by grepping `is ContinueApproachReason` sites and adding explicit no-op or "no-suffix" arm for `RUNWAY_OBSTRUCTED`. This avoids duplicate reason phraseology between primary + companion.
- **R8:** Controller-level unit tests in new `controller/src/commonTest/.../ObstructionContinueApproachSpec.kt`:
  - Rule fires when `RunwayObstructed AND ObstructionClearsInTime` AND `Not(ObstructionGoAroundAlreadyIssuedThisAttempt) AND Not(ContinueApproachAlreadyIssuedThisAttempt)`.
  - Companion `RunwayObstructionInformation` emitted in same controller decision cycle.
  - No-refire pin: re-evaluate rule on subsequent tick while obstruction persists; assert ONE CONTINUE APPROACH emission across the window (witness suppression).
  - Re-arm pin: simulate `Report(Downwind)` after CONTINUE APPROACH; assert witness clears.
  - Mutual exclusion pin: when `Not(ObstructionClearsInTime)`, GA rule fires; CONTINUE APPROACH rule does not.
  - Escalation pin: CONTINUE APPROACH issued → obstruction persists past predicate threshold → next tick fires GA (different witness; GA witness was never set).
  - Fail-closed pin: missing groundSpeed → predicate returns false → GA wins. Also pin: zero or non-finite groundSpeed → false → GA wins (per codex iter 7).
  - Snap-vs-coords divergence pin: `ac.position` (graph-snapped) and `ac.coords` (continuous) far enough apart to flip the predicate; assert the guard uses `coords` not the snapped point.
- **R9:** Sim test `G3aRunwayObstructionContinueApproachTest.kt` (new file). Single-aircraft LOWG, `outcomes = listOf(CircuitOutcome.FullStop)`. World authors `runway.obstruction = RunwayObstruction(clearsAt = T_obs + 5.seconds)` at `T_obs` chosen such that aircraft is in `AwaitApproach` stage with ETA-to-threshold > 15s (ensure predicate holds). Three-layer pin pattern with **decision-cycle timestamps** (per `sim-test-pins-must-compare-against-2026-05-10`):
  - Layer 1 (decision-cycle): `RunwayObstructionDetected.decisionTime <= ContinueApproach.decisionTime == /* same cycle */ RunwayObstructionInformation.decisionTime; RunwayObstructionCleared.decisionTime < ClearedToLand.decisionTime`.
  - Layer 1 (radio): `ContinueApproach.txStart < RunwayObstructionInformation.txStart < ... < ClearedToLand.txStart < Report(RunwayVacated).txStart`. **No `Report(ContinueApproach)` pin** — pilot read-back transmission for `ContinueApproach` is out of scope per Boundary deferment `D-PASS-continue-approach-pilot-readback` (existing `InstructionReadback.kt:115` returns `emptySet()` so no readback transmission is emitted). The OutstandingCoordination ledger entry is still tracked for supersession purposes; pin its lifecycle via belief-state inspection, NOT via a phantom Report-transmission pin.
  - Layer 2 (stage): commitment's `stage`, `kind`, and `runway` stay unchanged across the CONTINUE APPROACH cycle (NO regression to `AwaitDownwind` — distinguished from fn-12.3's regression behaviour). `continueApproachIssuedThisAttempt` witness flips from `false` to `true` (this is the only commitment field that changes). Unrelated sticky witnesses (`touchedDownDuringCommitment`, `pilotReadyDuringCommitment`, `observedReportsDuringCommitment`, `obstructionGoAroundIssuedThisAttempt`) remain unchanged.
  - Layer 3 (kinematic non-event): no `Climbing` phase; aircraft continues approach to threshold via the normal landing path (NO go-around).
  - One-shot obstruction authorship guard; assert exactly ONE `None → Some` transition in TOWER controller's `BeliefState.runwayObstructions[runway]` slice (uses the existing belief-slice trace surface, NOT per-controller `worldEvents` raw stream — align with fn-12.3's trace helper conventions). Equivalently, exactly one `RunwayObstructionDetected` event lands in the controller's fold across the test window.
  - Vacate-coordination closure pin per fn-8.3 R7-style after landing.
  - Time band ±15% on observed wall.
- **R10:** Cross-reference doc updates per docs-gap-scout findings:
  - `AGENTS.md` § Golden tests — add G3a-obstruction-continue-approach (7 tests total)
  - `STRATEGY.md` § Runtime simulator track — note quadruple-covered approach decision space
  - `wiki/design-decisions/2026-04-15-controller-architecture.md` — add Practice D: ContinueApproach (obstruction-clears-in-time) as fourth reactive practice
  - `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` — add fn-13 closure subsection
  - `wiki/design-decisions/2026-04-16-transmission-reception-architecture.md` — note CONTINUE APPROACH as the obstruction-aware fourth path
  - `controller/.../procedure/TowerArrival.kt` file-level KDoc — distinguish `ObstructionClearsInTime` from `RunwayObstructed` (third guard predicate)
  - `controller/.../observe/Event.kt` KDoc — note CONTINUE APPROACH bypasses the event taxonomy (no new ControllerEvent class)
  - `protocol/.../Instruction.kt` `ContinueApproach` KDoc — update with `RUNWAY_OBSTRUCTED` reason citation
  - `sim/.../testing/Fixtures.kt` LOWG provenance — add `G3aRunwayObstructionContinueApproachTest` consumer
  - Test class docstrings (G0/G1/G1-min/G2/G3a-trained/G3a-obstruction) — `@see G3aRunwayObstructionContinueApproachTest` cross-ref
- **R11:** `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` exits 0. detekt baseline unchanged.
  - **fn-13.1 verify:** six existing goldens (G0/G1/G1-min/G2/G3a-trained/G3a-obstruction) STAY GREEN. New `ObstructionContinueApproachSpec` controller-level tests GREEN. fn-12.3's `G3aRunwayObstructionTest` specifically verified — narrowing of `obstructionGoAroundRule` is `AwaitApproach`-only and must NOT affect post-clearance GA path.
  - **fn-13.2 verify:** all seven golden tests (the six above PLUS new G3aRunwayObstructionContinueApproachTest) GREEN. Full verify includes the new sim test.

## Strategy drift flagged for review

_(none — plan aligns with Runtime simulator track and closes fn-12 deferment.)_

## Quick commands

```bash
./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
./gradlew :sim:jvmTest --tests "xyz.easiersaid.twr.sim.G3aRunwayObstructionContinueApproachTest"
./gradlew :controller:jvmTest --tests "xyz.easiersaid.twr.controller.ObstructionContinueApproachSpec"
```

## Approach

### Two-task split

1. **Task .1 — Foundation:** guard atom + reason enum extension + new action + new rule + obstruction-GA rule narrowing + supersession extension (GA → ContinueApproach AND ClearedToLand/ClearedTouchAndGo → ContinueApproach) + companion-trace-regs split (ObstructionInfo carries per-instruction trace regs) + no-refire witness + RegulationDatabase entries + controller-level unit tests. **`ContinueApproachReason` exhaustiveness audit** (NEW enum variant requires arms everywhere); `inferContinueApproachReason` is UNCHANGED. Existing G0-G3a goldens + fn-12.3 G3a-obstruction stay GREEN throughout (no test fixture has short-TTL obstruction in `AwaitApproach` window).
2. **Task .2 — Sim test + cross-references:** `G3aRunwayObstructionContinueApproachTest.kt` end-to-end with three-layer pin pattern + per-controller scoping + time band. Cross-ref doc updates. Closes the epic.

### Reuse points (file:line refs)

| Surface | Reuse | New code |
|---------|-------|----------|
| `Instruction.ContinueApproach` | `protocol/.../Instruction.kt:854-879` (exists) | Add `RUNWAY_OBSTRUCTED` enum variant to `ContinueApproachReason` |
| `ContinueApproachAction` | `controller/.../bdi/Action.kt` (exists) | New sibling `ObstructionContinueApproachAction` |
| Existing `ARR-CONTINUE` rule | `TowerArrival.kt:462-475` (exists, unchanged) | NEW `ARR-CONTINUE-APPROACH-OBSTRUCTION` rule sibling |
| `obstructionGoAroundRule` | `TowerArrival.kt:211-227` (exists) | Narrow `AwaitApproach`-stage guard with `Not(ObstructionClearsInTime)` |
| Guard pattern | `RunwayObstructed` at `Guard.kt:547-553` (exists) | New `ObstructionClearsInTime` sibling |
| Companion pattern | `RunwayObstructionInformation` + `ObstructionInfo` (fn-12, exists) | Reused as-is; new action populates `obstructionInfo` |
| `deriveCompanionOutputs` | `Controller.kt:817-835` (exists) | Reused as-is (instruction-agnostic) |
| Pilot routing | `PilotCognitive.kt:782` no-op (exists, correct) | NO change — pilot continues current path |
| Readback machinery | `InstructionReadback.kt:113-123` `emptySet()` (exists) | NO change |
| Coordination ledger | `OutstandingCoordination` uniform (exists) | NO change |
| Witness pattern | `obstructionGoAroundIssuedThisAttempt` on `Commitment` (fn-12) | New sibling `continueApproachIssuedThisAttempt` |
| Re-arm hook | `reconcileTowerArrival` `Report(Downwind)` (fn-12) | Extend to also clear new witness |
| `inferContinueApproachReason` | `Action.kt:366-384` (exists, UNCHANGED) | Reason set directly by `ObstructionContinueApproachAction`, not via the inference helper |
| Sim test harness | `G3aRunwayObstructionTest.kt` (fn-12.3) | Mirror; reuse `findEmittingCycleMs` decision-cycle helper |
| Kinematic estimation | `WithinDistanceOfThreshold` pattern at `Guard.kt:641-653` | Reuse threshold lookup only; aircraft position from `ac.coords` (continuous), NOT `worldIndex.positions[ac.position]` (graph-snapped). Divide by `groundSpeed.value * 1852/3600` with finite+positive guard. |

## Test notes

The sim test (Task .2) follows the **three-layer pin pattern** from fn-11.2 / fn-12.3, with **decision-cycle timestamps** per `sim-test-pins-must-compare-against-2026-05-10`:

- **Layer 1 (decision-cycle pins)** — observable times:
  ```
  RunwayObstructionDetected.decisionTime
      <= ContinueApproach.decisionTime                       // rule fires this cycle
      == RunwayObstructionInformation.decisionTime           // SAME cycle (companion)
  // NO Report(ContinueApproach) — readback transmission out of scope (D-PASS-continue-approach-pilot-readback)
      < RunwayObstructionCleared.decisionTime                // world expiry → diff → event
      < ClearedToLand.decisionTime                           // pre-clearance gate ungates; ARR-LAND fires
      < Report(RunwayVacated).decisionTime
  ```
- **Layer 1 (radio-transmission pins)** — `txStart` time, after controller latency + frequency queuing:
  ```
  ContinueApproach.txStart < RunwayObstructionInformation.txStart < ... < ClearedToLand.txStart
  ```
  Strict `<` between primary + companion (`applyControllerOutputs` serializes on same frequency). Do NOT pin equality or one-tick spacing.
- **Layer 2 (stage NON-regression)**: commitment's `stage`/`kind`/`runway` STAY unchanged across the CONTINUE APPROACH cycle. `continueApproachIssuedThisAttempt` witness flips from false to true (this is the only commitment field that changes). Other sticky witnesses unchanged. **Distinct from fn-12.3's regression behavior** — that's the key behavioral signature of CONTINUE APPROACH vs GO AROUND. After obstruction clears + `ClearedToLand` re-issued + readback, stage advances normally `AwaitApproach → LandingClearanceIssued → AwaitLandedObserved`.
- **Layer 3 (kinematic non-event)**: no `Climbing` phase in the aircraft phase trace at any point. Aircraft continues approach to threshold via normal landing path. NO go-around.

**Vacate-coordination closure pin** (fn-8.3 R7-style): after vacate, no leftover ledger entries.

**Time band ±15%** on observed wall (calibrate at first GREEN — likely 1200-1400s for a single-circuit FullStop with brief CONTINUE APPROACH delay).

## Review considerations

### FP / type-safety axis
- `ContinueApproachReason` enum extension — audit all `when` sites for exhaustiveness; no `else` arms.
- `ObstructionClearsInTime` is parameterless `data object`; fail-closed semantics on missing inputs keep `AllOf` total.
- `ObstructionContinueApproachAction` returns `Either<ActionResolutionFailure, ProposedAction>` — typed-error.
- Named constants for `OBSTRUCTION_CLEAR_SAFETY_MARGIN_S`; no magic numbers.

### Test architecture axis
- Three-layer pin pattern with decision-cycle timestamps (per memory).
- Controller-level unit pins for guard predicate, rule firing, witness suppression, escalation, fail-closed.
- Sim-level end-to-end (Task .2).
- World-only test trigger (short-TTL obstruction authored via `onAfterEvent` hook from fn-12.3).
- All seven goldens stay GREEN after both tasks.

### Impact axis
- New guard atom: one new call site (`Guard.kt`).
- New rule: one new entry in `stageRules[AwaitApproach]`.
- Narrow existing GA rule: surgical guard addition; backward-compatible because v1's `ObstructionClearsInTime` defaults false on fail-closed and existing fixtures don't supply the inputs needed for it to evaluate true.
- New witness on `Commitment`: requires audit of `Commitment.copy(...)` sites; default-false preserves existing call sites.
- No protocol-cycle risk (no new protocol leaves).
- No pilot-side changes (CONTINUE APPROACH is no-op on mission, already wired).

### Operational axis
- Determinism: guard predicate is pure (snapshot reads); no PRNG.
- Tick-rate independence: predicate uses `SimTime` math; works at any tick rate.
- Replay / observability: existing trace machinery captures rule-fire + companion + readback.
- Performance: O(1) Map lookup; one new guard evaluation per controller cycle.

## Early proof point

**Task fn-13.1** validates the predicate + rule firing + companion emission via controller-level unit tests. If `ObstructionClearsInTime` misbehaves (e.g., fail-closed direction inverted, kinematic math off, witness not suppressing re-fire), Task .2's sim test will fail in ways traceable to .1's units. Re-evaluate Decision #2 (predicate shape) or #5 (witness lifecycle) if .1 fails.

## References

### Doctrinal
- **CAP 413 §4.53** — cancellation of issued landing clearance (deferred operational use)
- **CAP 413 §4.55** — runway obstructed at final, controller delays clearance via CONTINUE APPROACH; 4 NM trigger
- **CAP 413 §4.56** — CONTINUE APPROACH is NOT a landing clearance
- **ICAO Doc 4444 §7.10.2** — landing-clearance timing, "reasonable assurance that separation will exist when aircraft crosses runway threshold"
- **ICAO Doc 4444 §12.3.4.16(d)** — CONTINUE APPROACH [PREPARE FOR POSSIBLE GO AROUND] phraseology
- **ICAO Doc 4444 §8.9.6.1.7** — landing clearance "should normally be passed before 2 NM from touchdown" (radar approach; informs hard deadline)
- **ICAO Doc 4444 §7.4.1.4.1** — post-clearance obstruction → GA (already wired in fn-12)
- **ICAO Doc 4444 §8.9.6.1.8** — reason for instruction should be given to pilot

### Codebase prior art
- **fn-12** (G3a-obstruction) — entire obstruction surface; rules, witness, supersession, companion. Reused wholesale.
- **fn-8** (G1) — commitment lifecycle + sticky-witness reset.
- **fn-11** (G3a-trained) — `markCompleteInActiveCompound` (not needed here since CONTINUE APPROACH is a mission no-op).
- **fn-5** (G2) — sim test harness.

### Memory
- `sim-test-pins-must-compare-against-2026-05-10` — decision-cycle vs txStart pin discipline
- `ga-path-precedence-reorder-when-adding-2026-05-10` — not directly applicable (no pilot path changes)
- `feedback_no_corners.md` — this epic IS the un-deferment; same rule applies to any sub-gaps
- `feedback_reality_anchored.md` — CONTINUE APPROACH is the real-ATC middle state; v1 elision was rot
- `feedback_world_only_test_triggers.md` — test authors short-TTL obstruction in world state
- `project_rich_world_domain.md` — obstruction state shape locked from fn-12
- `feedback_pass_scope.md` — fold companion + witness + RegulationDatabase entries into one closing pass
- `feedback_plans_review_aware.md` — Review considerations addressed inline

### External (practice-scout sourced)
- [SKYbrary — Go-Around Decision Making](https://skybrary.aero/articles/go-around-decision-making)
- [SKYbrary — Stabilised Approach tutorial](https://skybrary.aero/tutorials/stabilised-approach)
- [FAA JO 7110.65 §3-10 Arrival Procedures](https://www.faa.gov/air_traffic/publications/atpubs/atc_html/chap3_section_10.html) — "reasonable assurance" doctrine
- [Flight Safety Foundation — Go-Around Decision-Making](https://flightsafety.org/wp-content/uploads/2017/03/Go-around-study_final.pdf)

## Deferments register

Deferments from this epic file in `~/.claude/plans/pilot-firewall.md` § Deferments register:

- **`D-PASS-g3a-continue-approach-cancel-clearance`** — post-clearance CANCEL LANDING CLEARANCE variant (CAP 413 §4.53). When obstruction appears AFTER `ClearedToLand` issued, doctrine is `CONTINUE APPROACH, CANCEL LANDING CLEARANCE (reason), ACKNOWLEDGE`. v1 keeps the post-clearance GA path. Future epic adds the cancel-clearance variant at `LandingClearanceIssued` stage with supersession of the active `ClearedToLand`.
- **`D-PASS-g3a-continue-approach-in-circuit`** — aircraft on downwind/base when obstruction appears. v1 has no rule; aircraft completes circuit. Overlaps with `D-PASS-g3a-obstruction-orbit-hold` from fn-12.
- **`D-PASS-g3a-continue-approach-possible-ga-variant`** — `POSSIBLE GO AROUND` borderline-band variant per ICAO §12.3.4.16(d) bracketed form. Requires extending `Instruction.ContinueApproach` body (or companion payload) and adding the borderline-band predicate (practice-scout's 10s base + 10s band recommendation).
- **`D-PASS-g3a-continue-approach-subjective-judgment`** — refinement of `ObstructionClearsInTime` predicate beyond simple kinematic estimate (wind, configuration, traffic congestion).
- **`D-PASS-g3a-continue-approach-sequencing`** — multi-aircraft CONTINUE APPROACH with sequencing.
- **`D-PASS-regdb-transcription-drift`** — RegulationDatabase transcription drifts caught by docs-scout: `ICAO4444_8_9_6_1_8` says "shall" but source says "should"; `ICAO4444_7_10_2` title is "Go-around instruction" but source is "Clearance to land". Out of scope for this epic; file as separate cleanup pass.

## Closures

- **CAP 413 three-state ATC ladder** complete (pre-clearance window): clear → CONTINUE APPROACH (this epic) → GO AROUND (fn-12). Pre-clearance window only; post-clearance cancellation deferred.
- **`D-PASS-g3a-obstruction-continue-approach`** from fn-12 is closed (this epic is the un-deferment).
- **Four reactive paths** at `AwaitApproach`: clear (ARR-LAND), continue-via-traffic (existing ARR-CONTINUE), continue-via-obstruction (this epic), go-around (fn-12).

## Requirement coverage

| Req | Description | Task(s) |
|-----|-------------|---------|
| R1  | `ObstructionClearsInTime` guard atom | fn-13.1 |
| R2  | `ContinueApproachReason.RUNWAY_OBSTRUCTED` enum + exhaustiveness audit (`inferContinueApproachReason` UNCHANGED — action sets reason directly) | fn-13.1 |
| R3  | `ObstructionContinueApproachAction` returning `Either<...>` + companion population | fn-13.1 |
| R4  | New `ARR-CONTINUE-APPROACH-OBSTRUCTION` rule in `stageRules[AwaitApproach]` | fn-13.1 |
| R5  | `obstructionGoAroundRule` `AwaitApproach`-stage guard narrowing with `Not(ObstructionClearsInTime)` | fn-13.1 |
| R6  | `continueApproachIssuedThisAttempt` witness + re-arm hook | fn-13.1 |
| R7  | RegulationDatabase entries (CAP413_4_53, CAP413_4_55 upgrade, CAP413_4_56, ICAO4444_12_3_4_16) | fn-13.1 |
| R8  | `ObstructionContinueApproachSpec.kt` controller-level unit tests | fn-13.1 |
| R9  | `G3aRunwayObstructionContinueApproachTest.kt` sim test | fn-13.2 |
| R10 | Cross-reference doc updates | fn-13.2 |
| R11 | Full verify GREEN (7 goldens) | fn-13.1, fn-13.2 |

## Done summary

_(filled per task during implementation)_

## Evidence

_(filled per task during implementation)_

## Errata

- 2026-05-11 (fn-17): CAP 413 §-cites in this spec were authored
  against the then-current Edition 23 numbering. Per fn-17.1's
  primary-source verification (artifact:
  `wiki/data-sources/cap413-edition-24-capture.md`; CAA PDF SHA
  `c620cda9b6bdbe8e9ed51b258e4df2f6e3edc839226e53ee2b591cb696a966ac`),
  Ed 24 (effective 2026-07-01) maps as follows for the sections this
  spec cites: §4.53 (cancellation of issued landing clearance) →
  §4.52; §4.55 (continue approach — runway obstructed at final) →
  §4.54; §4.56 (CONTINUE APPROACH is not a landing clearance) →
  §4.55; §4.65 (ATC-initiated GA / missed-approach phraseology) →
  §4.64. Current-doctrine citations live in
  `protocol/.../RegulationDatabase.kt` (Ed 24-coherent post-fn-17.1);
  this spec's prose is preserved as-is for historical fidelity.
