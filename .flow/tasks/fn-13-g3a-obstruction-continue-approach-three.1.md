---
satisfies: [R1, R2, R3, R4, R5, R6, R7, R8, R11]
---

## Description

Foundation pass. Adds the `ObstructionClearsInTime` guard + `ContinueApproachReason.RUNWAY_OBSTRUCTED` + `ObstructionContinueApproachAction` + new `ARR-CONTINUE-APPROACH-OBSTRUCTION` rule in `stageRules[AwaitApproach]` (priority-ordered before fn-12's `obstructionGoAroundRule` at the same stage), narrows the existing GA rule's `AwaitApproach`-stage guard for mutual exclusion, adds the `continueApproachIssuedThisAttempt` witness with re-arm hook, lands the RegulationDatabase entries, and adds controller-level unit tests.

**Scope is narrow** — context-scout confirmed the entire CONTINUE APPROACH machinery already exists (protocol leaf, action, rule, pilot routing, readback). This task makes the existing surface fire on the obstruction-clears-in-time predicate.

**Size:** M. Touches controller/protocol surfaces only. No pilot changes. No sim wiring changes.

**Files:**
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt` — add `data object ObstructionClearsInTime : RuleGuard` (sibling to `RunwayObstructed` at line 547-553). Also add `data object ContinueApproachAlreadyIssuedThisAttempt : RuleGuard` reading the new witness.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt` — `OBSTRUCTION_CLEAR_SAFETY_MARGIN_S = 10` named constant (sibling to other module-level constants).
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt:854-879` — add `ContinueApproachReason.RUNWAY_OBSTRUCTED` enum variant. **Audit ALL exhaustive `when (reason: ContinueApproachReason)` sites** (grep `ContinueApproachReason` and `when (` over reason-typed bindings) and add explicit arms; NO `else` clauses.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Action.kt` — add `ObstructionContinueApproachAction : RuleAction` (sibling to fn-12's `ObstructionGoAroundAction`). Returns `Either<ActionResolutionFailure, ProposedAction>`. Populates `ProposedAction.obstructionInfo = ObstructionInfo(runway, clearsAt)` and sets the `Instruction.ContinueApproach(target, reason = ContinueApproachReason.RUNWAY_OBSTRUCTED)`.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Action.kt:366-384` — `inferContinueApproachReason` **UNCHANGED** (signature lacks `Commitment`; the new action sets `RUNWAY_OBSTRUCTED` directly). Reference only.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt` — add new rule `ARR-CONTINUE-APPROACH-OBSTRUCTION` to `stageRules[AwaitApproach]` block. **Priority-ordered BEFORE `obstructionGoAroundRule`** in the list (verify selection-policy uses list order; if not, document priority enforcement).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt:211-227` — **narrow `obstructionGoAroundRule`'s `AwaitApproach`-stage usage**. The rule object is shared across 3 stage placements (per fn-12). Need to either (a) factor a stage-specific variant where `AwaitApproach`-placement gets `Not(ObstructionClearsInTime)` added to its guard; or (b) add `Not(ObstructionClearsInTime)` to the shared rule's guard (which would also affect `LandingClearanceIssued` and `AwaitLandedObserved` — which is wrong per Boundary #1). Pick (a). Two `AtcRule` instances (or a builder helper): `obstructionGoAroundRuleAwaitApproach` (with `Not(ObstructionClearsInTime)`) and `obstructionGoAroundRulePostClearance` (original shape).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Commitment.kt` — add `continueApproachIssuedThisAttempt: Boolean = false` field on `Commitment` (sibling to `obstructionGoAroundIssuedThisAttempt`). Default-false preserves existing call sites. **Audit `Commitment.copy(...)` sites** if any explicitly set the existing witness — verify the new field is also handled appropriately.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/Controller.kt` — extend the committed-output post-arbitration witness-set logic (the one fn-12 added for `obstructionGoAroundIssuedThisAttempt`) to also set `continueApproachIssuedThisAttempt` when `ARR-CONTINUE-APPROACH-OBSTRUCTION` fires AND survives arbitration/certification.
- **Supersession source file** (locate at task time — `applySupersessionCleanup` is imported by `Controller.kt` but defined elsewhere; likely `controller/.../Supersession.kt` or sibling per fn-12.1's directory): edit the supersession-relation table to add `GoAround supersedes ContinueApproach` AND `ClearedToLand supersedes ContinueApproach` AND `ClearedTouchAndGo supersedes ContinueApproach` entries. Add the file path to this Files list once located.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/CommitmentReconciliation.kt` (or wherever `reconcileTowerArrival` lives — verify location; fn-12 added the `Report(Downwind)` re-arm hook for the GA witness here) — extend the same re-arm hook to ALSO clear `continueApproachIssuedThisAttempt` on `Report(Downwind)` arrival.
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationDatabase.kt` — add `CAP413_4_53`, `CAP413_4_56`, `ICAO4444_12_3_4_16` entries. **Upgrade `CAP413_4_55`** in place (existing placeholder at line 374; tighten principle per docs-scout's recommended text: "When the runway is obstructed at or after the 4 NM final report but is expected to be available in good time for a safe landing, the controller delays landing clearance and instructs CONTINUE APPROACH; pilot reads back").
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/ObstructionContinueApproachSpec.kt` (NEW) — controller-level regression tests (see Approach Step 8).

## Approach

### Step 1: `ObstructionClearsInTime` guard (R1)

Add `data object ObstructionClearsInTime : RuleGuard` at `Guard.kt`, sibling to `RunwayObstructed`:

- Derive `runway` from `commitment.runway ?: ctx.beliefs.activeRunway` (fail-closed false if neither).
- Derive `obstruction` from `ctx.beliefs.runwayObstructions[runway]` (fail-closed false if absent).
- Derive `groundSpeed` from `ac.groundSpeed` (nullable `Knots?` per `ControllerTypes.kt:223`; fail-closed false if missing OR if `groundSpeed.value.isFinite() == false` OR if `groundSpeed.value <= 0.0` — division by zero would yield infinite ETA and falsely satisfy the predicate per codex iter 7). Add R8 pin: zero / non-finite groundSpeed → predicate false → GA wins.
- Derive `distanceToThreshold` via **`ac.coords` (continuous surveillance position)** and `ctx.worldIndex.thresholdByRunway[runway]` — Euclidean distance in metres. **Do NOT use `ctx.worldIndex.positions[ac.position]`** (graph-snapped point can mislead the predicate per codex iter 5 — snap error of even tens of metres could flip the clears-in-time decision unsafely). The threshold is a fixed runway endpoint and `thresholdByRunway` lookup is acceptable. Fail-closed false if `ac.coords` is null or threshold lookup misses. Add a controller test where `ac.position` and `ac.coords` diverge enough to change the predicate (validates we're using `coords`, not the snapped point).
- Predicate (all in milliseconds — `ctx.time` is `SimTime` with `.millis` accessor per codex iter 2; convert ETA to ms):
  - `groundSpeedMps = groundSpeed.value * 1852.0 / 3600.0` (knots → m/s; `Knots` exposes `.value`, NOT `.toKnots()` per codex iter 4)
  - `etaMs = (distanceToThresholdM / groundSpeedMps * 1000.0).toLong()` (m / (m/s) = s → ms)
  - Test: `(obstruction.clearsAt.millis - ctx.time.millis) + OBSTRUCTION_CLEAR_SAFETY_MARGIN_MS <= etaMs`

`OBSTRUCTION_CLEAR_SAFETY_MARGIN_S = 10` named constant; derive `OBSTRUCTION_CLEAR_SAFETY_MARGIN_MS = 10_000L` (or use Kotlin's `10.seconds.inWholeMilliseconds`). Confirm `OperatorContext` field is `time: SimTime` (codex iter 2 said `ctx.time`, not `ctx.now`); verify at task time.

**Fail-closed direction**: any missing input → false → GA rule wins (its narrowed guard's `Not(ObstructionClearsInTime)` evaluates true). Conservative.

### Step 2: `ContinueApproachReason.RUNWAY_OBSTRUCTED` (R2)

Extend enum at `Instruction.kt:854-879`. Audit exhaustive sites:
```bash
grep -rn "ContinueApproachReason" --include="*.kt" .
grep -rn "is ContinueApproachReason\|when (.*ContinueApproachReason" --include="*.kt" .
```
Likely sites: `inferContinueApproachReason` (in `Action.kt`), phraseology rendering (in protocol/), utterance-duration calc, trace formatters. Add explicit arms for `RUNWAY_OBSTRUCTED`; NO `else`.

**Phraseology rendering decision** (R6-phraseology-rendering in epic spec): CAP 413 §4.55 says "may or may not explain why" — verbalizing the reason in the primary `ContinueApproach` transmission is optional. **Decision: leave verbal reason OUT of the primary instruction** (matches fn-12's pattern; the companion `RunwayObstructionInformation` carries the obstruction info per ICAO §12.3.4.16(d) reason-on-radio convention for pre-clearance CONTINUE APPROACH). Render `RUNWAY_OBSTRUCTED` as a no-suffix (or pass-through) arm — same as `RUNWAY_ACCESS_PENDING`'s existing rendering. Verify by reading existing arm at task time and mirror.

This prevents duplicate reason phraseology between primary instruction + companion transmission.

For phraseology rendering specifically: the rendered RT phrase per CAP 413 §4.55 is `(callsign), CONTINUE APPROACH, [reason — optional per §4.56 ("may or may not explain why")]`. For RUNWAY_OBSTRUCTED, the existing companion `RunwayObstructionInformation` transmission carries the reason; the `CONTINUE APPROACH` instruction itself MAY omit the reason verbally. Choose at task time — mirror how `RUNWAY_ACCESS_PENDING` is rendered.

### Step 3: `inferContinueApproachReason` — NOT extended (per codex iter 2)

Per codex iter 2 finding: `inferContinueApproachReason(ac, ctx)` has signature `(AircraftObservation, OperatorContext)` — no `Commitment` parameter. Extending this function to detect `RunwayObstructed` would require either changing its signature (touches every call site) OR reading the obstruction belief without commitment scope (loses per-aircraft context).

**Decision**: do NOT extend `inferContinueApproachReason`. The new `ObstructionContinueApproachAction` constructs `Instruction.ContinueApproach(target = ac.id, reason = ContinueApproachReason.RUNWAY_OBSTRUCTED)` directly — sets the reason inline in the action rather than via the inferred helper. This is cleaner: the action knows it fired because of an obstruction (the rule's guard said so).

The existing `ContinueApproachAction` (for traffic-driven CONTINUE APPROACH at the existing `ARR-CONTINUE` rule) continues to use `inferContinueApproachReason` unchanged.

### Step 4: `ObstructionContinueApproachAction` + companion-trace split (R3)

Sibling to `ObstructionGoAroundAction` at `Action.kt`. Mirror its structure:
- Returns `Either<ActionResolutionFailure, ProposedAction>`.
- Resolves `runway = commitment.runway ?: ctx.beliefs.activeRunway` (return `.left()` if neither).
- Reads `obstruction = ctx.beliefs.runwayObstructions[runway]` (return `.left()` if absent — race condition).
- Builds `instruction = Instruction.ContinueApproach(target = ac.id, reason = ContinueApproachReason.RUNWAY_OBSTRUCTED)`.
- Returns `ProposedAction(instruction, obstructionInfo = ObstructionInfo(runway, obstruction.clearsAt, companionTraceRegs = continueApproachTraceRegs)).right()`.

**Companion-trace-regs split** (per codex iter 2): `deriveCompanionOutputs` at `Controller.kt:817-835` currently hardcodes companion trace regs to `ICAO4444_7_4_1_4_1, ICAO4444_8_9_6_1_8, CAP413_4_65` — appropriate for fn-12's GA case, **wrong** for CONTINUE APPROACH (`CAP413_4_65` is missed-approach phraseology). Implementation:

- Extend `ObstructionInfo` (at `controller/.../bdi/Action.kt`) with optional `companionTraceRegs: List<RegulationRef>? = null` field. Default-null preserves fn-12's call site.
- Extend `deriveCompanionOutputs` to read `action.obstructionInfo?.companionTraceRegs ?: <fn-12's hardcoded defaults>`. Fallback preserves backward compatibility for the GA path.
- The new action populates `companionTraceRegs = listOf(RegulationDatabase.CAP413_4_55, RegulationDatabase.CAP413_4_56, RegulationDatabase.ICAO4444_12_3_4_16, RegulationDatabase.ICAO4444_8_9_6_1_8)`. **Both `CAP413_4_65` AND `ICAO4444_7_4_1_4_1` EXCLUDED** — `4_65` is missed-approach phraseology (wrong); `7_4_1_4_1` is the post-clearance obstruction/GA mandate (wrong, this is pre-clearance CONTINUE APPROACH).
- `ObstructionGoAroundAction` (fn-12's existing action) is **UNCHANGED** — leaves `companionTraceRegs = null`, falling back to fn-12's hardcoded GA defaults.

Controller-level test (Step 9 pin #13): assert ContinueApproach companion's `DecisionTrace.regulations` cites `CAP413_4_55, CAP413_4_56, ICAO4444_12_3_4_16` AND does NOT cite `CAP413_4_65`.

### Step 5: New rule `ARR-CONTINUE-APPROACH-OBSTRUCTION` (R4)

Add to `stageRules[AwaitApproach]` block at `TowerArrival.kt`, **inserted BEFORE the existing `obstructionGoAroundRule` placement** in the list (priority-ordering). Mirror the shape of existing rules; named entries per fn-12 convention.

Guard: `AllOf(listOf(AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))), RunwayObstructed, ObstructionClearsInTime, Not(ObstructionGoAroundAlreadyIssuedThisAttempt), Not(ContinueApproachAlreadyIssuedThisAttempt)))`.

Action: `ObstructionContinueApproachAction`.

`nextStage = null` (no advancement; rule self-gates via witness). Verify the `AtcRule` shape admits null `nextStage` — fn-12's `obstructionGoAroundRule` always advances; if `nextStage` is required non-null, find a sibling rule that doesn't advance (e.g., the existing `ARR-CONTINUE` rule at line 462-475 — verify its `nextStage`).

`urgency = Urgency.TIME_SENSITIVE` — matches the existing `ARR-CONTINUE` rule (per codex iter 5: arbitration has a one-per-urgency budget; `PROGRESSION` would let CONTINUE APPROACH be delayed behind routine progression work, but it's on final and time-sensitive). CONTINUE APPROACH is doctrinally non-emergency (`SAFETY` is wrong) but IS time-critical.

`regulations = listOf(RegulationDatabase.CAP413_4_55, RegulationDatabase.CAP413_4_56, RegulationDatabase.ICAO4444_12_3_4_16, RegulationDatabase.ICAO4444_8_9_6_1_8)`. (First three are new entries from R7; `8_9_6_1_8` reuses fn-12 existing entry. `ICAO4444_7_4_1_4_1` is post-clearance-only, EXCLUDED from pre-clearance CONTINUE APPROACH per codex iter 7.)

### Step 6: Narrow `obstructionGoAroundRule` at `AwaitApproach` (R5)

The existing rule object at `TowerArrival.kt:211-227` is reused across 3 stages. Factor two rule objects: `obstructionGoAroundRuleAwaitApproach` (guard includes `Not(ObstructionClearsInTime)`) and `obstructionGoAroundRulePostClearance` (original guard). Use the former in `stageRules[AwaitApproach]`; use the latter in `stageRules[LandingClearanceIssued]` and `stageRules[AwaitLandedObserved]`.

**Both variants MUST share `id = "ARR-GO-AROUND-RUNWAY-OBSTRUCTED"`** — fn-12's `obstructionGoAroundIssuedThisAttempt` witness-setting logic likely keys on rule id. Investigation step: locate the witness-set call site in `Controller.kt` (fn-12's R7-no-refire implementation); verify whether it pattern-matches on `id` or on action-type (`ObstructionGoAroundAction`). If id-based, keep both variants with shared id (legal — different stages can share id). If action-type-based, ids can differ; pick the cleaner shape. Document inline.

Document why two rule objects exist (post-clearance is always GA per fn-13 Boundary #1).

### Step 6b: Supersession extension — TWO new entries (R5-supersession-extension)

Extend fn-12's supersession-relation table (at the `applySupersessionCleanup` path in `Controller.kt`) with TWO new entries:

1. **`GoAround supersedes ContinueApproach`** — for the escalation path (CONTINUE APPROACH issued → predicate flips false → GA fires; stale ContinueApproach coordination must drop).
2. **`ClearedToLand supersedes ContinueApproach` AND `ClearedTouchAndGo supersedes ContinueApproach`** — for the **normal success path** (obstruction clears, fresh landing clearance issued via existing `ARR-LAND` rule). Without these, the stale ContinueApproach coordination persists in the ledger after landing, potentially misfiring or blocking subsequent rule firings via `NoPendingReadback` gates.

Investigation: locate fn-12's `applySupersessionCleanup` extension (added `GoAround → ClearedToLand/ClearedTouchAndGo`). Add both new entries to the same relation. Apply across all coordination states `{Issued, Querying, Reissued, LostCommsDeclared}` consistent with fn-12 R7-supersession coverage.

R8 supersession pins (Step 9 pins #11 + #12): assert no stale `ContinueApproach` coordination after GA AND no stale `ContinueApproach` coordination after `ClearedToLand` (normal success path).

### Step 7: Witness on `Commitment` + re-arm hook (R6)

**Witness set timing — separate pass required.** Codex iter 2 finding: `advanceCommittedStages()` returns early when `result.nextStage == null` (which is the case for the new rule). Cannot reuse fn-12's witness-set path naively. Options:
- (a) Move the witness-set logic BEFORE the `nextStage == null` early-return in `advanceCommittedStages`.
- (b) Add a separate `applyCommittedOutputWitnesses(...)` pass over committed runs after `advanceCommittedStages` returns, walking each committed `ProcedureRun` and setting witnesses based on action type (or rule id).

Pick (b) — keeps `advanceCommittedStages` semantically clean. The new pass:
- Iterates committed `ProcedureRun`s from the current cycle.
- For each: identify the rule id / action; if matches `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` → set `obstructionGoAroundIssuedThisAttempt = true` on the commitment; if matches `ARR-CONTINUE-APPROACH-OBSTRUCTION` → set `continueApproachIssuedThisAttempt = true`.
- Refactor fn-12's existing witness-set logic into this new pass (it's currently inside `advanceCommittedStages`'s normal-advancement path; move it out).

This refactors fn-12 R7-no-refire's implementation slightly. Document the refactor in the commit message; existing fn-12 tests must still pass.


Add `continueApproachIssuedThisAttempt: Boolean = false` to `Commitment` at `controller/.../bdi/Commitment.kt`. Default-false preserves existing call sites; `copy(...)` preserves unless explicitly set.

Add `data object ContinueApproachAlreadyIssuedThisAttempt : RuleGuard` at `Guard.kt`, mirroring `ObstructionGoAroundAlreadyIssuedThisAttempt`. Reads `commitment.continueApproachIssuedThisAttempt`.

Extend the committed-output witness-set logic in `Controller.kt` (the same path fn-12 added for the GA witness) to set `continueApproachIssuedThisAttempt = true` when `ARR-CONTINUE-APPROACH-OBSTRUCTION` survives arbitration/certification. The witness is set ONLY for committed output (post-arbitration); failed-arbitration candidates do NOT set the witness.

Extend the re-arm hook in `reconcileTowerArrival` (at the `Report(Downwind)` fold site that fn-12 added for the GA witness) to ALSO clear `continueApproachIssuedThisAttempt`. Both witnesses re-arm on the same trigger (next downwind report on same commitment, or commitment replacement). Same lifecycle.

### Step 8: RegulationDatabase entries (R7)

Add to `protocol/.../RegulationDatabase.kt`:

```kotlin
val CAP413_4_53 = RegulationRef(
    document = "CAP_413", edition = "27th ed. (2023)", section = "§4.53",
    title = "Cancellation of issued landing clearance",
    principle = "Where a controller cancels an issued landing clearance but expects re-issue in good time " +
        "for a safe landing, the reason should be given if time permits; phraseology is " +
        "CONTINUE APPROACH, CANCEL LANDING CLEARANCE (reason), ACKNOWLEDGE with pilot readback",
    category = RegulationCategory.PHRASEOLOGY,
)

val CAP413_4_56 = RegulationRef(
    document = "CAP_413", edition = "27th ed. (2023)", section = "§4.56",
    title = "CONTINUE APPROACH is not a landing clearance",
    principle = "The instruction CONTINUE APPROACH is not an invitation to land; the pilot must wait " +
        "for landing clearance or initiate a missed approach",
    category = RegulationCategory.PHRASEOLOGY,
)

val ICAO4444_12_3_4_16 = RegulationRef(
    document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§12.3.4.16",
    title = "Landing clearance phraseology — approach instructions",
    principle = "CONTINUE APPROACH [PREPARE FOR POSSIBLE GO AROUND] is the approach-instruction phraseology " +
        "used when landing clearance is delayed; it is not a landing clearance",
    category = RegulationCategory.PHRASEOLOGY,
)
```

**Upgrade `CAP413_4_55`** in place at line 374:

```kotlin
val CAP413_4_55 = RegulationRef(
    document = "CAP_413", edition = "27th ed. (2023)", section = "§4.55",
    title = "Continue approach — runway obstructed at final",
    principle = "When the runway is obstructed at or after the 4 NM final report but is expected to be " +
        "available in good time for a safe landing, the controller delays landing clearance and " +
        "instructs CONTINUE APPROACH; pilot reads back",
    category = RegulationCategory.GUIDANCE,
)
```

Verify `RegulationRef.ICAO_4444_EDITION` constant exists and is `"17th ed. (2024)"` (per docs-scout finding at `RegulationModel.kt:46-48`).

### Step 9: Controller-level unit tests (R8)

New file `controller/src/commonTest/kotlin/.../ObstructionContinueApproachSpec.kt`. Mirror `ObstructionGoAroundSpec.kt` (fn-12.1) structure. Pins:

1. **Rule fires when predicate holds**: seed `AwaitApproach` commitment + `RunwayObstructed` belief with `clearsAt = now + 5s`, aircraft on final with `groundSpeed = 80 kt`, distance = 2 km. ETA ≈ 50s; `5s + 10s margin = 15s` ≪ 50s. Run rule evaluation; assert `ARR-CONTINUE-APPROACH-OBSTRUCTION` fires; `ContinueApproach` instruction emitted; `RunwayObstructionInformation` companion emitted; stage stays `AwaitApproach`.
2. **Rule does NOT fire when predicate fails (clears-too-late)**: same fixture but `clearsAt = now + 100s`; assert CONTINUE APPROACH does NOT fire; `obstructionGoAroundRuleAwaitApproach` fires instead (mutual exclusion).
3. **Rule does NOT fire when groundSpeed missing/zero/non-finite**: nullable / 0.0 / NaN / Infinity → predicate fail-closed false → GA fires (per codex iter 7 finite-positive guard).
4. **Rule does NOT fire when threshold unknown**: missing threshold point (`thresholdByRunway[runway]` lookup misses) → fail-closed false → GA fires. Note: `ac.coords` is non-null by type (`AircraftObservation.coords` is non-nullable per `ControllerTypes.kt` shape); only non-finite coordinate values trigger fail-closed if constructible via test helpers. `ac.position` (graph-snapped point) is NOT used by the predicate.
4b. **Snap-vs-coords divergence pin**: seed `ac.position` (graph-snapped) and `ac.coords` (continuous) far enough apart that the predicate result differs depending on which is used. Assert the guard uses `coords` (CONTINUE APPROACH fires) and would have given the wrong answer with the snapped point.
5. **Witness suppression**: after first CONTINUE APPROACH fires, advance ticks while obstruction persists and predicate still true; assert exactly ONE CONTINUE APPROACH emission (witness blocks re-fire).
6. **Re-arm on Report(Downwind)**: after CONTINUE APPROACH fires, simulate `Report(Downwind)` for the same aircraft on the same commitment; assert witness clears; subsequent obstruction (re-set in a fresh approach) fires CONTINUE APPROACH again.
7. **Escalation to GA + supersession**: CONTINUE APPROACH issued → predicate becomes false next tick (obstruction.clearsAt slipped or aircraft ETA shrunk) → GA rule fires (its `Not(ObstructionClearsInTime)` now true; GA witness not set; CONTINUE APPROACH witness set but doesn't block GA rule). **AND** the stale `ContinueApproach` coordination is superseded by the new `GoAround` per Step 6b's supersession extension — assert no `ContinueApproach` coordination remains in `BeliefState.coordinations` for the aircraft after GA fires.
11. **Normal-success supersession**: CONTINUE APPROACH issued → obstruction clears → `ARR-LAND` fires `ClearedToLand` → assert no stale `ContinueApproach` coordination remains in the ledger (`ClearedToLand supersedes ContinueApproach` per Step 6b's two-entry extension). Same pin shape across `ClearedTouchAndGo` if the test fixture exercises it.
12. **Pre-existing `ARR-CONTINUE` rule UNCHANGED**: seed a non-obstruction CONTINUE APPROACH trigger (e.g., `Not(RunwayAccessGranted)` with no obstruction in belief); assert the existing `ARR-CONTINUE` rule fires unchanged; the new rule does NOT fire (its guard requires `RunwayObstructed`); reason is whatever `inferContinueApproachReason` returns (NOT `RUNWAY_OBSTRUCTED`).
13. **Companion-trace regs split**: when the new rule fires, the emitted `RunwayObstructionInformation` companion's `DecisionTrace.regulations` cites `CAP413_4_55, CAP413_4_56, ICAO4444_12_3_4_16, ICAO4444_8_9_6_1_8` AND does **NOT** cite `CAP413_4_65` (missed-approach). For fn-12's existing GA path, the companion still cites `CAP413_4_65` (regression check — fn-12's existing companion-trace behaviour unchanged).
8. **Commitment-state preservation**: stage unchanged across CONTINUE APPROACH (NOT regressed to `AwaitDownwind` like GA). Sticky witnesses (touchedDown, pilotReady, observedReports) untouched.
9. **Reason populated**: emitted `ContinueApproach` instruction has `reason = ContinueApproachReason.RUNWAY_OBSTRUCTED`.
10. **No effect at `LandingClearanceIssued` / `AwaitLandedObserved`**: seed commitment at one of these stages with same obstruction belief; assert the new rule does NOT fire (it's not registered in those stage lists); fn-12's GA rule (unchanged for post-clearance) fires.

### Step 10: regression + verify (R11 partial)

Run `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt`. Expected: all six existing goldens (G0/G1/G1-min/G2/G3a-trained/G3a-obstruction) GREEN. detekt baseline unchanged. New `ObstructionContinueApproachSpec` GREEN.

**fn-12.3's `G3aRunwayObstructionTest` must STAY GREEN**: it uses `clearsAt = T_obs + 60.seconds` post-clearance. The narrowing of `obstructionGoAroundRule` is `AwaitApproach`-stage only; fn-12.3's test fires from `LandingClearanceIssued` / `AwaitLandedObserved`, which are unchanged. Verify.

## Investigation targets

**Required**:
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt:547-553` — `RunwayObstructed` (sibling pattern)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt:641-653` — `WithinDistanceOfThreshold` (kinematic estimation pattern)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt:211-227` — `obstructionGoAroundRule` (narrow at AwaitApproach)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt:462-475` — existing `ARR-CONTINUE` rule (sibling shape; verify nextStage = null and Urgency)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Action.kt:366-384` — `inferContinueApproachReason`
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Action.kt` — `ObstructionGoAroundAction` (sibling pattern for new action)
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt:854-879` — `ContinueApproach` + `ContinueApproachReason` enum
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationDatabase.kt:374` — existing `CAP413_4_55` placeholder (upgrade in place)
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationModel.kt:46-48` — `ICAO_4444_EDITION` constant
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Commitment.kt` — `obstructionGoAroundIssuedThisAttempt` field (sibling pattern for new witness)
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/ObstructionGoAroundSpec.kt` — controller-level test pattern (sibling)

**Optional**:
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/CommitmentReconciliation.kt` — `reconcileTowerArrival` (Report(Downwind) re-arm hook from fn-12)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/Controller.kt` — committed-output witness-set logic (fn-12 R7-no-refire)

## Key context

- The `ContinueApproachReason` enum extension widens a sealed type — exhaustive `when` sites must add arms; NO `else`.
- The new rule's `nextStage = null` is unusual; verify against existing `ARR-CONTINUE` rule (which has `readbackAdvancesToStage = null` and `nextStage = null` — it's a "stay-in-stage with self-gate" rule).
- The narrowing of `obstructionGoAroundRule` is **only at `AwaitApproach` stage** — post-clearance stages (`LandingClearanceIssued`, `AwaitLandedObserved`) are UNCHANGED per Boundary #1. Two rule objects, one per category.
- The witness must be set on COMMITTED OUTPUT (post-arbitration+certification), not at rule-fire time. Mirror fn-12's mechanism.
- Reuse the `RunwayObstructionInformation` companion verbatim — `deriveCompanionOutputs` at `Controller.kt:817-835` is instruction-agnostic.

## Acceptance

- [ ] R1: `ObstructionClearsInTime` parameterless `data object` added at `Guard.kt`. Predicate: `(clearsAt.millis - ctx.time.millis) + OBSTRUCTION_CLEAR_SAFETY_MARGIN_MS <= (distanceToThresholdM / (groundSpeed.value * 1852.0 / 3600.0) * 1000.0).toLong()`. Uses `ac.coords` (continuous surveillance position) for aircraft, `ctx.worldIndex.thresholdByRunway[runway]` for threshold — **NOT** `worldIndex.positions[ac.position]` (snap-error risk). Fail-closed on any missing input. `OBSTRUCTION_CLEAR_SAFETY_MARGIN_S = 10` named constant.
- [ ] R2: `ContinueApproachReason.RUNWAY_OBSTRUCTED` enum variant added. All exhaustive `when (reason: ContinueApproachReason)` sites updated with explicit arms; NO `else`. `inferContinueApproachReason` is **UNCHANGED** (signature lacks `Commitment`); the new `ObstructionContinueApproachAction` sets `RUNWAY_OBSTRUCTED` directly. The existing `ContinueApproachAction` continues to use the helper unchanged.
- [ ] R3: `ObstructionContinueApproachAction : RuleAction` added at `Action.kt`. Returns `Either<ActionResolutionFailure, ProposedAction>`. Populates `obstructionInfo` so existing `deriveCompanionOutputs` emits `RunwayObstructionInformation` companion. **Companion-trace-regs split**: `ObstructionInfo` extended with optional `companionTraceRegs: List<RegulationRef>? = null` field (default-null preserves fn-12's call site). `deriveCompanionOutputs` reads `action.obstructionInfo?.companionTraceRegs ?: <fn-12's hardcoded GA defaults>`. New action populates `companionTraceRegs = listOf(CAP413_4_55, CAP413_4_56, ICAO4444_12_3_4_16, ICAO4444_8_9_6_1_8)` — **`CAP413_4_65` EXCLUDED** (missed-approach phraseology, wrong for CONTINUE APPROACH). fn-12's existing `ObstructionGoAroundAction` UNCHANGED (uses null → fallback → existing GA companion regs preserved).
- [ ] R4: New `ARR-CONTINUE-APPROACH-OBSTRUCTION` rule added to `stageRules[AwaitApproach]`, priority-ordered BEFORE `obstructionGoAroundRule`. Guard `AllOf(AnyOf(OnApproach, OnCircuitLeg(LegName.FINAL)), RunwayObstructed, ObstructionClearsInTime, Not(ObstructionGoAroundAlreadyIssuedThisAttempt), Not(ContinueApproachAlreadyIssuedThisAttempt))`. Action `ObstructionContinueApproachAction`. `nextStage = null`. **`urgency = TIME_SENSITIVE`** (matches existing `ARR-CONTINUE` rule; arbitration's one-per-urgency budget would delay a `PROGRESSION` instruction behind routine progression work, which is wrong for on-final). Regulations: `CAP413_4_55`, `CAP413_4_56`, `ICAO4444_12_3_4_16`, `ICAO4444_8_9_6_1_8`.
- [ ] R5: `obstructionGoAroundRule` AT `AwaitApproach` ONLY narrowed with `Not(ObstructionClearsInTime)` (factor two rule objects: `obstructionGoAroundRuleAwaitApproach` + `obstructionGoAroundRulePostClearance`). `LandingClearanceIssued` and `AwaitLandedObserved` placements use the original (unchanged) shape.
- [ ] R6: `continueApproachIssuedThisAttempt: Boolean = false` field on `Commitment`. `ContinueApproachAlreadyIssuedThisAttempt` guard atom reads it. Set on committed-output (post-arbitration). Re-armed on `Report(Downwind)` in `reconcileTowerArrival` AND on commitment replacement.
- [ ] R7: New `RegulationDatabase` entries added: `CAP413_4_53`, `CAP413_4_56`, `ICAO4444_12_3_4_16`. **`CAP413_4_55` upgraded in place** (existing placeholder principle tightened).
- [ ] R8: `ObstructionContinueApproachSpec.kt` new file. **13 pins** per Step 9, including:
  - Rule fires when predicate holds (positive case)
  - Rule does NOT fire when predicate fails / clears-too-late / groundSpeed missing / distance unknown (negative + fail-closed cases)
  - Witness suppression (no re-fire while predicate persists)
  - Re-arm on `Report(Downwind)`
  - Escalation to GA + supersession of stale `ContinueApproach` coordination by `GoAround`
  - **Normal-success supersession**: CONTINUE APPROACH issued → obstruction clears → `ARR-LAND` fires `ClearedToLand` → assert no stale `ContinueApproach` coordination remains in the ledger (`ClearedToLand supersedes ContinueApproach` per Step 6b's two-entry extension). Same pin shape across `ClearedTouchAndGo`.
  - Pre-existing `ARR-CONTINUE` rule UNCHANGED (non-obstruction CONTINUE APPROACH trigger fires existing rule; new rule does NOT fire)
  - Reason populated as `RUNWAY_OBSTRUCTED` (set directly by action, not via `inferContinueApproachReason`)
  - Commitment-state preservation (stage NOT regressed)
  - No effect at `LandingClearanceIssued` / `AwaitLandedObserved` (rule not registered in those stages)
  - **Companion-trace regs split**: emitted `RunwayObstructionInformation` companion's `DecisionTrace.regulations` cites `CAP413_4_55, CAP413_4_56, ICAO4444_12_3_4_16, ICAO4444_8_9_6_1_8` AND does **NOT** cite `CAP413_4_65` (missed-approach). Regression check: fn-12's existing GA companion still cites `CAP413_4_65` (unchanged).
  Fixture helpers for `AircraftObservation` extended to seed `groundSpeed: Knots?` (per `ControllerTypes.kt:223`; fn-12.1's `AircraftObservation.fromTestPoint(...)` does not pass speed — add `withGroundSpeed(...)` builder). Without groundSpeed, predicate is fail-closed false and CONTINUE APPROACH never fires.
- [ ] R11 (partial): `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` exits 0. All six existing goldens (G0/G1/G1-min/G2/G3a-trained/G3a-obstruction) GREEN. fn-12.3's `G3aRunwayObstructionTest` specifically stays GREEN (verify narrowing didn't affect post-clearance path). New `ObstructionContinueApproachSpec` GREEN. detekt baseline unchanged.
- [ ] No `else` clauses introduced in any new `when (reason: ContinueApproachReason)` site — totality discipline.
- [ ] Witness set timing: verified by code inspection (the new `applyCommittedOutputWitnesses(...)` pass at Step 7 walks ONLY committed `ProcedureRun`s — by construction, failed-arbitration candidates do not appear in the committed run list and thus cannot set the witness). Step 9's pin #5 (witness suppression) implicitly validates that the witness IS set on the successful path; the no-set-on-failure case is structurally guaranteed by the new pass's input domain (committed runs only).

## Done summary
Added the three-state obstruction-handling ladder's middle state (CAP 413 §4.55-4.56 / ICAO Doc 4444 §12.3.4.16(d)): when the runway is obstructed but expected to clear in time, the controller now delays landing clearance via CONTINUE APPROACH rather than immediately firing GA. Implementation adds an `ObstructionClearsInTime` guard, an `ObstructionContinueApproachAction`, a new `ARR-CONTINUE-APPROACH-OBSTRUCTION` rule at `AwaitApproach` (priority-placed before the existing GA rule), narrows the fn-12 obstruction GA rule with `Not(ObstructionClearsInTime)` at the AwaitApproach stage only (post-clearance unchanged per Boundary #1), adds the `continueApproachIssuedThisAttempt` commitment witness with shared re-arm lifecycle, extends supersession with three new entries (GA→CA, ClearedToLand→CA, ClearedTouchAndGo→CA), and lands four regulation refs (`CAP413_4_53`/`_4_55` upgraded/`_4_56`/`ICAO4444_12_3_4_16`). Three codex rounds caught: (1) instruction-specific companion description, (2) existing `ARR-CONTINUE` re-fire race after coordination escalation, (3) stale `CAP413_4_55` citation on the non-obstruction rule. All 6 pre-existing goldens stay GREEN; new `ObstructionContinueApproachSpec` adds 17 pins covering all 13 task-spec acceptance pins plus the codex round-2 escalation regression.
## Evidence
- Commits: b09aae2, 2db399c, dde66ac, fa6477a, 03df1b2
- Tests: ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
- PRs: