---
satisfies: [R9, R10, R11]
---

## Description

Sim-level golden test + cross-reference doc updates. Closes the epic.

The test exercises the entire CONTINUE APPROACH path: world authors short-TTL obstruction in aircraft's `AwaitApproach` stage → sim diff emits `Detected` → controller folds → `ObstructionClearsInTime` predicate evaluates true (5s clearance + 10s margin ≪ 30-60s ETA) → `ARR-CONTINUE-APPROACH-OBSTRUCTION` fires → controller issues `Instruction.ContinueApproach(RUNWAY_OBSTRUCTED)` + companion `RunwayObstructionInformation` → pilot continues current mission (NO read-back transmission per `D-PASS-continue-approach-pilot-readback`; OutstandingCoordination ledger entry later superseded by `ClearedToLand`) → obstruction expires → `RunwayObstructed` becomes false → existing `ARR-LAND` rule fires `ClearedToLand` (and supersession cleans the stale ContinueApproach coordination) → aircraft reads back landing clearance, lands, vacates.

**Size:** M. One new sim-test file + ~10 doc-update Edits.

**Files:**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionContinueApproachTest.kt` (NEW) — sim-level golden test
- `AGENTS.md` — § Golden tests, add G3a-obstruction-continue-approach (7 tests total)
- `STRATEGY.md` — § Runtime simulator track, note quadruple-covered approach decision space
- `wiki/design-decisions/2026-04-15-controller-architecture.md` — add Practice D: ContinueApproach (obstruction-clears-in-time)
- `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` — add fn-13 closure subsection
- `wiki/design-decisions/2026-04-16-transmission-reception-architecture.md` — note CONTINUE APPROACH as obstruction-aware fourth path
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt` file-level KDoc — distinguish `ObstructionClearsInTime` from `RunwayObstructed` (third guard predicate)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/Event.kt` KDoc — note CONTINUE APPROACH bypasses event taxonomy (no new ControllerEvent)
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt` `ContinueApproach` KDoc — update with `RUNWAY_OBSTRUCTED` reason citation
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt:27-48` — LOWG provenance, add `G3aRunwayObstructionContinueApproachTest` consumer
- Test class docstrings (G0/G1/G1-min/G2/G3a-trained/G3a-obstruction) — `@see G3aRunwayObstructionContinueApproachTest` cross-ref

## Approach

### Step 1: fixture + world authorship

Use `Fixtures.LOWG` unchanged. Mission goal: `HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop))` — single planned circuit.

**World-authoring trigger**: at sim tick `T_obs`, author `runway.obstruction = RunwayObstruction(clearsAt = T_obs + 5.seconds)` via the `onAfterEvent` hook in `runUntilWithStateTrace` (the hook fn-12.3's `RunUntil.kt` added).

**Authorship predicate (refined per codex iter 2)** — must satisfy ALL of:
- commitment stage is `AwaitApproach` (verify via belief state inspection)
- BEFORE `ClearedToLand` issued (no ClearedToLand coordination in tower's coordinations ledger)
- aircraft is on final geometry (`OnCircuitLeg(LegName.FINAL)` would fire, OR distance-to-threshold < ~5 km — verify the rule's guard `AnyOf(OnApproach, OnCircuitLeg(FINAL))` evaluates true at authorship time)
- aircraft has `groundSpeed` set (otherwise `ObstructionClearsInTime` fails closed)
- predicate-eligibility check: `(5s + 10s margin) ≤ distanceToThreshold / groundSpeed` — i.e., aircraft must be far enough from threshold that the 15s budget fits within ETA

Without all of these holding, authoring a 5s-TTL obstruction is dead trigger: obstruction clears before `ObstructionClearsInTime` ever evaluates true. Sim test must validate authorship preconditions; if they don't hold by some sim tick before the aircraft reaches a too-close-to-threshold point, FAIL LOUDLY (test setup error, not a behavioural failure).

One-shot guard per fn-12.3 pattern:
```kotlin
var obstructionAuthored = false
onAfterEvent = { event, state ->
    if (!obstructionAuthored && aircraftIsInAwaitApproach(state)) {
        obstructionAuthored = true
        state.copy(world = state.world.withRunwayObstruction(rwy, RunwayObstruction(clearsAt = state.now + 5.seconds)))
    } else state
}
```

**Validate the predicate triggers**: at `T_obs`, aircraft groundSpeed should be ~70-80 kt (light single on final), distance ~2 km. ETA ≈ 50s. `5s + 10s margin = 15s` ≪ 50s → `ObstructionClearsInTime` holds → CONTINUE APPROACH fires.

### Step 2: three-layer pin pattern

Mirror `G3aRunwayObstructionTest.kt` (fn-12.3) structure. Use `findEmittingCycleMs` decision-cycle helper (fn-12.3's `nextTransmissionId` mint-id walk) for decision-cycle pins.

**Layer 1 (decision-cycle)** — controller decision/output time:
```
RunwayObstructionDetected.decisionTime
    <= ContinueApproach.decisionTime                       // rule fires this cycle
    == RunwayObstructionInformation.decisionTime           // SAME cycle (companion)
    < RunwayObstructionCleared.decisionTime                // world expiry → diff → event
    < ClearedToLand.decisionTime                           // ARR-LAND fires (pre-clearance gate ungates)
    < Report(RunwayVacated).decisionTime
// NO Report(ContinueApproach) — pilot readback transmission out-of-scope (D-PASS-continue-approach-pilot-readback)
```

**Layer 1 (radio-transmission)** — `txStart` time:
```
ContinueApproach.txStart < RunwayObstructionInformation.txStart   // serialized on same frequency (strict <)
ClearedToLand.txStart                                              // re-issued (after obstruction clear)
                        < Report(RunwayVacated).txStart
// NO Report(ContinueApproach) — empty readback per InstructionReadback.kt:115
// Report(ClearedToLand) MAY or MAY NOT have a transmission depending on Instruction.ClearedToLand's readback atoms — verify at task time
```

**Layer 2 (stage NON-regression)**:
- Commitment's `stage`/`kind`/`runway` stay unchanged across the CONTINUE APPROACH cycle. `continueApproachIssuedThisAttempt` flips false → true (the only commitment field that changes). Other sticky witnesses (`touchedDownDuringCommitment`, `pilotReadyDuringCommitment`, `observedReportsDuringCommitment`, `obstructionGoAroundIssuedThisAttempt`) remain unchanged. **Do NOT pin whole-commitment equality** — that would conflict with the witness flip.
- After obstruction clears + `ClearedToLand` re-issued + readback, stage advances normally: `AwaitApproach → LandingClearanceIssued → AwaitLandedObserved`.
- **No `Stage_regression(* → AwaitDownwind)` event in the trace** — distinct from fn-12.3 (which DOES have that regression). The absence of regression is itself a pin: assert no commitment regression transitions during the obstruction window.

**Layer 3 (kinematic non-event)**:
- No `Climbing` phase in the aircraft phase trace at any point.
- Aircraft phase sequence: `... Final → LandingRoll → Vacating → AtStand`. NO go-around path.

### Step 3: per-controller event scoping pin

Per fn-12 Decision #3 (events per-controller scoped via `view.aerodromeId`). Assert exactly one `None → Some` transition in the TOWER controller's `BeliefState.runwayObstructions[runway]` slice via `runwayObstructionTransitions(tower.id, rwy)` extractor from fn-12.1's `SimTraceQueries.kt`. **NOT** a raw `worldEvents` count — the belief-slice transition is the observable surface the existing fn-12.3 sim test uses.

### Step 4: vacate-coordination closure pin (R7-style from fn-8.3)

After aircraft vacates: no leftover `AfterLandingVacateVia` / `BacktrackRunway` entries in tower's coordinations ledger.

### Step 5: companion + reason pin

- `Instruction.ContinueApproach` emitted with `reason = ContinueApproachReason.RUNWAY_OBSTRUCTED`.
- `RunwayObstructionInformation` companion emitted in same decision cycle, serialized after the primary transmission.
- Companion's `DecisionTrace.regulations` cites exactly `CAP413_4_55, CAP413_4_56, ICAO4444_12_3_4_16, ICAO4444_8_9_6_1_8` (per fn-13.1 Step 4). **Explicit absence assertion**: `CAP413_4_65` is NOT cited (missed-approach is wrong for CONTINUE APPROACH). **Explicit absence assertion**: `ICAO4444_7_4_1_4_1` is NOT cited (post-clearance-only mandate).

### Step 6: time band ±15%

Calibrate observed wall at first GREEN run. Expected ~1200-1400 sim seconds (single circuit FullStop + brief CONTINUE APPROACH delay; should be slightly LONGER than G0's LowgGoldenTest which is the same shape without obstruction). KDoc the observed wall + computed bounds + rationale.

### Step 7: cross-reference doc updates (R10)

Per docs-gap-scout findings:

1. **`AGENTS.md`** § Golden tests — heading update to "7 tests" or list G0/G1/G1-min/G2/G3a-trained/G3a-obstruction/G3a-obstruction-continue-approach. Add 7th bullet describing the `ObstructionClearsInTime` / `ARR-CONTINUE-APPROACH-OBSTRUCTION` path. Cross-ref G3a-obstruction as the companion (obstruction-GA) test for the "predicate fails" branch.

2. **`STRATEGY.md`** § Runtime simulator track — update "triple-covered" reactive-GA language. Note that the approach decision space is now quadruple-covered: clear (ARR-LAND), continue-traffic (existing ARR-CONTINUE), continue-obstruction (new), go-around (fn-12).

3. **`wiki/design-decisions/2026-04-15-controller-architecture.md`** — around line 40 (Practice A/B/C list): add Practice D: ContinueApproach (obstruction expected to clear in time, `Urgency.TIME_SENSITIVE`). Note priority ordering at `AwaitApproach` and the `ObstructionClearsInTime` guard.

4. **`wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md`** — extend fn-12's closure block: add fn-13 subsection noting this as the fourth reactive path (a deliberate non-GA ATC decision per CAP 413 §4.55-4.56 + ICAO §12.3.4.16). Extend the numbered list from three GA paths to "three GA paths + one CONTINUE APPROACH path." Note `ARR-CONTINUE-APPROACH-OBSTRUCTION` priority-ordered against `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` at `AwaitApproach` via mutually-exclusive guards.

5. **`wiki/design-decisions/2026-04-16-transmission-reception-architecture.md`** — table row around line 257: note `Instruction.ContinueApproach` is now used by two distinct rules (existing `ARR-CONTINUE` for traffic, new `ARR-CONTINUE-APPROACH-OBSTRUCTION` for obstruction). `ContinueApproachReason` enum extended with `RUNWAY_OBSTRUCTED`.

6. **`controller/.../procedure/TowerArrival.kt`** file-level KDoc — distinguish three guard predicates at `AwaitApproach`: `RunwayPhysicallyClear` (occupancy), `RunwayObstructed` (declared obstruction), `ObstructionClearsInTime` (kinematic predicate over obstruction). Note three-way priority ordering: `ARR-CONTINUE-APPROACH-OBSTRUCTION` before `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` (mutually exclusive by guards) before `ARR-GO-AROUND` (the generic GA).

7. **`controller/.../observe/Event.kt`** KDoc — note CONTINUE APPROACH is a controller output (`Instruction.ContinueApproach`), not a `ControllerEvent`. No new event source class introduced by fn-13.

8. **`protocol/.../Instruction.kt`** `ContinueApproach` class KDoc (line 854 area) — update to cite all four CAP 413 reasons including the new `RUNWAY_OBSTRUCTED`. Reference fn-13's `ARR-CONTINUE-APPROACH-OBSTRUCTION` as a consumer.

9. **`sim/.../testing/Fixtures.kt:27-48`** LOWG provenance — add `G3a-obstruction-continue-approach (G3aRunwayObstructionContinueApproachTest)` as fourth consumer of `Fixtures.LOWG`.

10. **Sibling test class docstrings**:
    - `LowgGoldenTest.kt` — add `@see G3aRunwayObstructionContinueApproachTest`
    - `G1TwoAircraftCircuitsTest.kt` — sibling list addition
    - `G1TwoAircraftMinimalSpec.kt` — `@see G3aRunwayObstructionContinueApproachTest`
    - `G2CrossAerodromeVfrTest.kt` — sibling list addition
    - `G3aPilotTrainedGoAroundTest.kt` — `@see G3aRunwayObstructionContinueApproachTest`
    - `G3aRunwayObstructionTest.kt` — `@see G3aRunwayObstructionContinueApproachTest` as companion (predicate-fails branch)

### Step 8: full verify (R11)

`./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` exits 0. **All seven golden tests** (G0 / G1 / G1-min / G2 / G3a-trained / G3a-obstruction / G3a-obstruction-continue-approach) GREEN. detekt baseline unchanged.

## Investigation targets

**Required**:
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionTest.kt` — full structure of canonical mirror (fn-12.3); `findEmittingCycleMs` helper
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/RunUntil.kt` — `onAfterEvent` hook signature (fn-12.3 added)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/SimTraceQueries.kt` — `runwayObstructionTransitions` extractor (fn-12.1)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt:139-156` — `GA-POST-CLEAR` interrupt (does NOT fire for CONTINUE APPROACH path; verify by code)

**Optional**:
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/BeliefState.kt` — `commitments` slice (for stage transition pin)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/CommitmentReconciliation.kt` — sticky-witness reset semantics
- fn-13.1's `ObstructionContinueApproachSpec.kt` — so sim test does NOT redundantly pin controller-internal state

## Key context

- **Sim-level pins observable behaviour primarily** — radio transmissions, observable phase transitions, world-state-change events. Allowed inspections of controller-internal state: `BeliefState.commitments` (stage non-regression), `BeliefState.runwayObstructions` (fold verification), coordinations ledger (vacate-closure). The new `continueApproachIssuedThisAttempt` witness is checked by fn-13.1's unit tests; sim test relies on those.
- **Stage NON-regression** is the key behavioral signature distinguishing CONTINUE APPROACH from GO AROUND in the sim trace.
- **The companion + reason pin** is per ICAO §12.3.4.16(d) + §8.9.6.1.8 (reason-on-radio convention for CONTINUE APPROACH; §7.4.1.4.1(c) is post-clearance-only and EXCLUDED). The `ContinueApproach` instruction's `reason = RUNWAY_OBSTRUCTED` field + the `RunwayObstructionInformation` companion both carry the info.
- **Decision-cycle pins use `findEmittingCycleMs`** (`nextTransmissionId` mint-id walk), per `sim-test-pins-must-compare-against-2026-05-10` memory.
- **fn-12.3's `G3aRunwayObstructionTest` must remain GREEN** — same fixture, longer obstruction TTL + post-clearance authorship exercises the GA path. Two complementary tests on the same fixture.

## Acceptance

- [ ] R9: `G3aRunwayObstructionContinueApproachTest.kt` exists. Single-aircraft LOWG, `outcomes = listOf(FullStop)`. World authors `runway.obstruction = RunwayObstruction(clearsAt = T_obs + 5.seconds)` at sim time `T_obs > T_AwaitApproach_entry AND T_obs < T_ClearedToLand` (pre-clearance pin). Three-layer pin pattern with decision-cycle timestamps (per memory). Layer 2 pins stage NON-regression. Vacate-coordination closure pin. Time band ±15%.
- [ ] R10: All cross-reference doc updates landed per Step 7 enumerated list.
- [ ] R11: `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` exits 0. **All seven golden tests** (G0 / G1 / G1-min / G2 / G3a-trained / G3a-obstruction / G3a-obstruction-continue-approach) GREEN. detekt baseline unchanged.
- [ ] No event injection in test fixture — verified by grep over the test for `ControllerEvent.RunwayObstructionDetected` (only allowed in trace-extraction assertions, not in fixture authorship).
- [ ] No belief-state mutation in test fixture — verified by grep.
- [ ] Per-controller event scoping pin: assert exactly one `None → Some` transition in TOWER controller's `BeliefState.runwayObstructions[runway]` slice (via `runwayObstructionTransitions(tower.id, rwy)` extractor from fn-12.1's `SimTraceQueries.kt`), NOT a raw global `worldEvents` count.
- [ ] Companion same-cycle pin: `GoAround`/`ContinueApproach` and `RunwayObstructionInformation` same `decisionCycleId`, `txStart` strictly ordered `primary < companion`.

## Done summary
fn-13.2 ships the closing piece of the fn-13 epic: a sim-level golden
test `G3aRunwayObstructionContinueApproachTest` exercising the
pre-clearance CONTINUE APPROACH ladder middle state per CAP 413
§4.55-4.56 + ICAO Doc 4444 §12.3.4.16(d), plus a fn-13.1 controller-
side fix that codex round-3 surfaced (witness re-arm hook firing on
the same Downwind report that caused the AwaitApproach entry).

The sim test composes fn-13.1's `ObstructionClearsInTime` guard +
`ARR-CONTINUE-APPROACH-OBSTRUCTION` rule + `Instruction.ContinueApproach
(RUNWAY_OBSTRUCTED)` + `RunwayObstructionInformation` companion into a
single deterministic run pinning the entire pre-clearance stack —
world author → sim diff/event → controller belief fold →
`ObstructionClearsInTime` predicate evaluation → CA rule fires + same-
decision-cycle companion → no `nextStage` (commitment stays at
`AwaitApproach`) → witness `continueApproachIssuedThisAttempt = true`
→ obstruction expires → `Not(RunwayObstructed)` gate ungates → ARR-LAND
fires `ClearedToLand` → land + vacate.

Three-layer pin pattern with **stage NON-regression** as the KEY
behavioural signature distinguishing CA from GA: CA has `nextStage =
null`; the commitment stays at `AwaitApproach` across the CA decision
cycle; only `continueApproachIssuedThisAttempt` flips on the
commitment; the four other sticky witnesses are unchanged; the absence
of `<from-stage> → AwaitDownwind` regression is itself a load-bearing
pin. Companion regs pin asserts exactly `CAP413_4_55, CAP413_4_56,
ICAO4444_12_3_4_16, ICAO4444_8_9_6_1_8` with explicit absence
assertions for `CAP413_4_65` and `ICAO4444_7_4_1_4_1` (the GA
companion's wrong-path refs). Exactly-one-CA + exactly-one-companion
pins catch duplicate fires at the sim level. No-GoAround absence pin
proves mutual exclusion at `AwaitApproach`. No-pilot-readback absence
pin proves CA's empty `requiredReadbackAtoms` discipline. Supersession
pin proves the `ClearedToLand → ContinueApproach` edge closes the
stale CA coordination on normal-success. Time band ±15% around the
observed wall 896 s (~14.9 sim minutes) — materially shorter than
G3a-obstruction's GA test (~1399 s) because the CA path adds no
recovery circuit.

World-only test trigger via `runUntilWithStateTrace`'s `onAfterEvent`
hook with one-shot `var obstructionAuthored` guard authoring
`runway.obstruction = RunwayObstruction(clearsAt = now + 5.seconds)`
at the FIRST post-event state where ALL five preconditions hold
simultaneously (mirroring the CA rule's guard exactly): commitment
stage = `AwaitApproach`, no `ClearedToLand` coordination, on FINAL
leg label OR distance ≤ 5000m, `speedMps > 0`, and `(5s + 10s margin)
≤ distance/groundSpeed`. If preconditions never align, the test fails
LOUDLY per the spec's R9 acceptance. No `ControllerEvent` injection,
no `BeliefState` mutation. Decision-cycle pins via
`nextTransmissionId` mint-id walk per the
`sim-test-pins-must-compare-against-2026-05-10` memory.

**Codex review converged in three rounds:**

- Round 1 (NEEDS_WORK → 5s TTL): initial implementation used a 20-
  second TTL + loose authorship predicate (stage + airborne only),
  drifting from the spec's R9 acceptance. Fixed by restoring the
  strict five-precondition predicate (mirroring the CA rule's guard)
  with explicit fail-loud validation.

- Round 2 (NEEDS_WORK → 5s sweep): five cross-reference docs still
  said "20-second TTL". Doc-only sweep across
  `G3aRunwayObstructionContinueApproachTest.kt`, `LowgGoldenTest.kt`,
  `G3aRunwayObstructionTest.kt`, `Fixtures.kt`, and the
  totality-and-go-around wiki design decision.

- Round 3 (NEEDS_WORK → witness re-arm gate): the sim test was
  emitting two ContinueApproach + two companion transmissions on the
  same approach attempt. Investigation traced the cause to fn-13.1's
  re-arm hook firing on the SAME Downwind report that originally
  caused the AwaitApproach commitment to form. The fix gates the re-
  arm on the commitment's stage being `AwaitDownwind` (so the late-
  arriving original Downwind on `AwaitApproach` doesn't reset the
  witness; only a fresh recovery-circuit Downwind on `AwaitDownwind`
  does). The recovery-circuit re-arm behaviour is preserved.
  Controller-level Pin 6b regression test added to
  `ObstructionContinueApproachSpec`. Memory entry captured at
  `bug/runtime-errors/fn-131-ca-witness-re-arm-fires-on-late-2026-05-10`
  documenting the bug class for future witness-re-arm hook designs.

- Round 4 SHIP.

Cross-reference doc updates land the seventh golden test across:
`AGENTS.md` (G3a-obstruction-continue-approach bullet + 7-test
heading), `STRATEGY.md` (quadruple-covered approach decision space),
`wiki/design-decisions/2026-04-15-controller-architecture.md`
(Practice D: ContinueApproach + three-way priority ordering), `wiki/
design-decisions/2026-04-22-root-cause-go-around-and-totality.md`
(fn-13 closure subsection), `wiki/design-decisions/2026-04-16-
transmission-reception-architecture.md` (two ContinueApproach
consumers + `RUNWAY_OBSTRUCTED` enum extension), `TowerArrival.kt`
file-level KDoc (three guard predicates + three-way priority + fn-13
Boundary #1), `Event.kt` KDoc (CA is a controller output — no new
event source class), `Instruction.kt ContinueApproach` class KDoc
(four reason families + empty-atoms note), `Fixtures.kt` LOWG
provenance (fourth consumer), and `@see` cross-refs on five sibling
test class docstrings.

All seven golden tests (G0 / G1 / G1-min / G2 / G3a-trained /
G3a-obstruction / G3a-obstruction-continue-approach) GREEN. detekt
baseline unchanged. fn-13 epic closed.
## Evidence
- Commits: 5033b9cd996254119c5a86ec822d27bea731e083, 5ef5c480f76bed61b183a8f4a715320342982dd2, b53e810ca9f68cd29328f4779d3ffd24ae0cea75, f33cd546bc47b822f89252faa2711096b9dadc43
- Tests: ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
- PRs: