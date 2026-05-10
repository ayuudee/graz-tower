---
satisfies: [R10, R11, R12]
---

## Description

Sim-level golden test for G3a-obstruction + cross-reference doc updates. Closes the epic.

The test exercises the entire stack: world authors `runway.obstruction` at the right tick → sim diff producer emits `Detected` → controller folds → reactive rule fires → controller issues `protocol.GoAround` + companion obstruction-info transmission → pilot's new `AtcGoAroundOnFinal` recognition + Tick A + Tick B path executes → aircraft GAs, re-enters circuit → world clears obstruction at `clearsAt` → sim emits `Cleared` → circuit 2's final issues normal `ClearedToLand` → aircraft lands.

Mirrors `G3aPilotTrainedGoAroundTest.kt` (fn-11.2) in structure. Three-layer pin pattern (causal partial-order + sticky-witness regression + kinematic non-event) per the test-architecture discipline established in fn-11.

**Size:** M. One new sim-test file (~400-500 lines mirroring G3a-trained's ~575-line shape) + ~10 doc-update Edits.

**Files:**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionTest.kt` (NEW) — sim-level golden test
- `AGENTS.md` — § Golden tests, add G3a-obstruction bullet
- `STRATEGY.md` — § Runtime simulator track, update live-vertical accounting
- `wiki/design-decisions/2026-04-15-controller-architecture.md:149-154` — add `obstruction` to enumerated `Runway` fields
- `wiki/domain/aviation-world.md:36-48` — § Aerodrome, add `Runway.obstruction` note
- `wiki/design-decisions/2026-04-16-transmission-reception-architecture.md` (around line 58) — add note on world-state-derived event source class (G3a-obstruction is first instance)
- `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` (Resolution section, line 93+) — add three-path GA coverage note
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/Event.kt` — KDoc note on the two world-state-derived event leaves (radio-derived vs world-derived event source class distinction)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt` (file-level KDoc, around line 80) — distinguish physical-occupancy from declared-obstruction guard arms
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt:27-48` — LOWG provenance, add G3a-obstruction (`G3aRunwayObstructionTest`) as consumer
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LowgGoldenTest.kt` — class docstring, `@see G3aRunwayObstructionTest` cross-ref
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt` — sibling list adds G3a-obstruction
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftMinimalSpec.kt` — `@see G3aRunwayObstructionTest`
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt` — sibling prose adds G3a-obstruction
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotTrainedGoAroundTest.kt` — class docstring sibling cross-ref to G3a-obstruction

## Approach

### Step 1: fixture + world authorship

Use `Fixtures.LOWG` unchanged. Mission goal: `HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop))` — **single planned circuit**. The recovery circuit is provided automatically by `handleGoAround`'s tree rewrite (`CircuitAfterGoAround = [goAroundTask(), circuitTask()]`); no second outcome is needed. **No `GoAround` outcome in the list** — this is reactive ATC GA, not pilot-trained.

(Two `FullStop` outcomes would produce three landing attempts: GA + recovery-FullStop + remaining-FullStop, which is not the intended scenario.)

The world-authoring trigger: at sim tick `T_obs` (when aircraft is on circuit-1 final, post-`ClearedToLand`), mutate the world state so the runway carries `obstruction = RunwayObstruction(clearsAt = T_obs + 60.seconds)`. Use the per-tick mutation hook in the sim harness (verify the exact API at `runUntilWithStateTrace` — used at `G3aPilotTrainedGoAroundTest:237`).

The mutation site must be **world-only** (per `feedback_world_only_test_triggers.md`). Conceptually:
```kotlin
runUntilWithStateTrace(
    fixture = LOWG,
    mission = ...,
    var obstructionAuthored = false  // one-shot guard — must be set exactly once
    onTick = { state ->
        if (!obstructionAuthored && aircraftIsOnFinalWithClearance(state)) {
            obstructionAuthored = true
            // mutate world: state.copy(world = state.world.withRunwayObstruction(rwy, RunwayObstruction(clearsAt = state.now + 60.seconds)))
        } else state  // unchanged
    }
)
```

The exact mutation API is fn-12.1's call. Whatever shape Task .1 settled on (e.g., `world.aerodromes[lowg].copy(runways = runways + (rwyId to runway.copy(obstruction = ...)))`), the test uses that.

**The test must NOT** directly emit `ControllerEvent.RunwayObstructionDetected`, mutate `BeliefState.runwayObstructions`, or otherwise bypass the world → sim-diff → controller-fold pipeline.

**One-shot authorship is mandatory.** The `onTick` callback writes `runway.obstruction = Some(...)` exactly once (the `var obstructionAuthored = false` guard above). Re-writing `Some → Some(new clearsAt)` on subsequent ticks would refresh `clearsAt` and violate the immutability invariant (epic Decision #4). **Pin (per-controller scoped)**: assert exactly one `RunwayObstructionDetected` event in the trace **for the LOWG TOWER controller's `worldEvents` stream** (NOT a global trace count — events are per-controller scoped per Decision #3, and TOWER + GROUND may both receive scoped events for the same runway). Identify the relevant controller (likely TOWER per the arrival rule that fires) and scope the pin to that controller's events.

**Defense-in-depth via diff producer.** Per Task .1's Step 10 (world-diff producer), the producer throws an `IllegalStateException` (via `check(...)`) if it ever sees `Some(old) → Some(new clearsAt)`. So even if a future fixture or helper accidentally refreshes `clearsAt`, the diff producer fails loudly rather than silently emitting no event. The test guard above is the first line of defense; the diff-producer `check` is the second.

### Step 2: three-layer pin pattern

Mirror `G3aPilotTrainedGoAroundTest:147-572` shape. Use the same trace-zipper extraction helpers; lift them to a shared test util if they're test-class-private but useful here.

**Layer 1 (causal partial-order)** — observable times only (no internal-state "supersession" pins):
```
**Decision-cycle pins** (controller decision/output time):
```
RunwayObstructionDetected.decisionTime
    <= GoAround.decisionTime                                       // rule fires in cycle that sees RunwayObstructed=true
    == Stage_regression(LandingClearanceIssued|AwaitLandedObserved → AwaitDownwind).time   // SAME tick, Immediate advancement
RunwayObstructionCleared.decisionTime
    < ClearedToLand_recovery.decisionTime                          // pre-clearance gate ungates
```

**Radio-transmission pins** (transmission-start time, after controller latency + frequency queuing):
```
GoAround.txStart
    < RunwayObstructionInformation.txStart                         // serialized on same frequency by applyControllerOutputs
    < Report(GoingAround).txStart                                  // pilot reads back later
    < Report(RunwayVacated).txStart                                // recovery circuit lands + vacates
```

**Same controller-output cycle** for `GoAround` + `RunwayObstructionInformation` — both emitted by the same `deriveCompanionOutputs` invocation in the same controller decision cycle, but **transmission-start times are NOT equal**. `applyControllerOutputs` serializes outputs on the same frequency: each subsequent transmission starts at the previous transmission's `endsAt`. Pin shape: same `decisionCycleId` (or equivalent grouping) for both, AND `GoAround.txStart < RunwayObstructionInformation.txStart`. Do NOT pin same `txStart` or one-tick spacing.

The `Detected.decisionTime <= GoAround.decisionTime` pin distinguishes the obstruction-driven trigger from any other GA path. Stage regression happens at GoAround **decision cycle time**, NOT at any radio-transmission time and NOT when `Report(GoingAround)` arrives.

**Layer 2 (sticky-witness regression — at GoAround decision-cycle time via Immediate advancement, NOT via GA-POST-CLEAR interrupt):**
- Exactly one stage transition `<from-stage> → AwaitDownwind` at the GoAround **decision-cycle time** (NOT transmission-start time, NOT `Report(GoingAround)` receive time). `<from-stage>` is either `LandingClearanceIssued` (obstruction appeared before readback) or `AwaitLandedObserved` (obstruction appeared post-readback / pre-touchdown).
- The transition is driven by the new rule's `AdvancementPolicy.Immediate` + `nextStage = AwaitDownwind`. The existing `GA-POST-CLEAR` interrupt at `TowerArrival.kt:148-156` does **NOT** fire for this path — by the time `Report(GoingAround)` arrives, the stage is already `AwaitDownwind` (its `fromStages = LandingClearanceIssued` no longer matches).
- Pin assertion: `Stage_regression.time == GoAround.decisionTime` (same tick).
- Post-regression: sticky-witness reset (per fn-8.3) — `touchedDownDuringCommitment`, `pilotReadyDuringCommitment`, `observedReportsDuringCommitment` all reset on the original commitment. The reset triggers on stage transition (verified against existing `ARR-GO-AROUND-CLEARANCE-ISSUED` rule which has the same `Immediate + AwaitDownwind` shape).
- Note: regression time **<= `Report(GoingAround).txStart`** (regression at decision-cycle; pilot's report arrives later via radio delivery + reaction time). The two pins use distinct trace-extraction helpers — do NOT collapse them into one chain.

**Layer 3 (kinematic non-event):**
- No `LandingRoll` or `Vacating` phase in the aircraft phase trace **before** `Report(GoingAround)` is recorded — proves the aircraft did NOT touch down on circuit 1.

### Step 3a: from-stage tolerance + supersession deferred to controller-level

The sim test's `T_obs > T_ClearedToLand` constraint pins post-clearance, but does NOT pin which post-clearance from-stage the rule fires from — depending on radio queue and tick cadence, the actual fire-stage may be `LandingClearanceIssued` (pre-readback) or `AwaitLandedObserved` (post-readback). Both are valid; the sim test asserts only "regression `<from-stage> → AwaitDownwind` happened from one of the two post-clearance stages."

**Stale-readback supersession is NOT pinned in the sim test** — that's a controller-level invariant covered by fn-12.1's R7-supersession acceptance (synthetic stale-readback injection across all coordination states). The sim test relies on those unit tests; here it just verifies "no leftover landing-class pending coordination after vacate" (the existing fn-8.3 R7-style closure pin).

### Step 3: vacate-coordination closure pin (R7-style from fn-8.3)

After circuit 2 vacates: no leftover `AfterLandingVacateVia` / `BacktrackRunway` entries in the tower's coordinations ledger for the aircraft. Mirror the closure pin shape from `G3aPilotTrainedGoAroundTest:508-535`.

### Step 4: time band ±15%

Calibrate observed wall at first GREEN run (likely 1300-1500 sim seconds — a 2-circuit + 60s obstruction wait scenario). KDoc the observed wall + computed bounds + rationale in the test class header, mirroring `G3aPilotTrainedGoAroundTest`'s pattern. Pin: `observedWallSeconds in (lowerBound..upperBound)`.

### Step 5: companion transmission pin

Per epic R8 + Decision #9 (ICAO §7.4.1.4.1(c) — reason mandatory): pin that alongside the `GoAround` instruction, a companion `RunwayObstructionInformation` transmission is emitted by the same `deriveCompanionOutputs` invocation in the same controller decision/output cycle. Pin shape:
- **Same controller decision/output cycle** (same `decisionCycleId` or equivalent grouping in trace).
- **Serialized radio order**: `GoAround.txStart < RunwayObstructionInformation.txStart` (`applyControllerOutputs` queues outputs sequentially on the same frequency; each starts at the previous transmission's `endsAt`).
- **Do NOT pin same `txStart` time or one-tick spacing** — that would over-pin and likely false-fail.
- Companion's `DecisionTrace` carries explicit regulation refs (per Task .1 R8-companion-trace acceptance).

### Step 6: cross-reference doc updates (R11)

Per docs-gap-scout findings (epic spec R11 enumerated list):

- `AGENTS.md` § Golden tests: add a sixth bullet for G3a-obstruction. Format: test class name, scenario description (single aircraft, world-authored obstruction on runway, ATC-issued GA), what it pins, doctrinal cite (ICAO §7.4.1.4.1).
- `STRATEGY.md` § Runtime simulator track: update live-vertical accounting to reflect that the reactive-GA path is now triple-covered (self-initiated + pilot-trained + ATC-instructed-obstruction). The phrase "G0 and G2 are the golden anchors; IFR wiring (IFR-1..6) and approach sequencing are the next live verticals" should be revised — G3a-obstruction is now closed, so it's part of the anchored set.
- `wiki/design-decisions/2026-04-15-controller-architecture.md:149-154` (Runway management section, "The entity-referenced world model supports this..."): add `obstruction` to the enumerated Runway fields list.
- `wiki/domain/aviation-world.md:36-48` (§ Aerodrome): one-sentence addition that `Runway` carries an optional `obstruction: RunwayObstruction?` field representing a declared physical obstruction the controller's belief tracks.
- `wiki/design-decisions/2026-04-16-transmission-reception-architecture.md` (around line 58, the Unified Event Taxonomy section): note that `ControllerEvent` admits a second source class — world-model state changes injected directly by sim — and that G3a-obstruction is the first instance. This widens the firewall contract: the controller now receives events from two sources (radio + world-state-derived), both typed, both pure.
- `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` (Resolution section): add a paragraph that G3a-obstruction closes a further gap — ATC-issued reactive go-around due to world-state obstruction — completing the three-path coverage (self-initiated, pilot-trained, ATC-instructed-obstruction).
- `controller/.../observe/Event.kt` KDoc: add a comment on the two new variants explaining they are world-model-injected events (not derived from pilot radio) and therefore exempt from `aircraftIdOf` / `intentFromRadio` aircraft-extraction (or expand those tests to handle the null-aircraft case).
- `controller/.../procedure/TowerArrival.kt` file-level KDoc (around line 80): the existing `ARR-GO-AROUND` and `ARR-GO-AROUND-CLEARANCE-ISSUED` rules guard on `Not(RunwayPhysicallyClear)`. The new `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule guards on a different predicate (`RunwayObstructed`). Document the distinction: physical-occupancy (aircraft on runway) vs declared-obstruction (world-state event).
- `sim/.../testing/Fixtures.kt` LOWG provenance comment: add G3a-obstruction (`G3aRunwayObstructionTest`) as a consumer with a one-line description matching the existing G3a (trained) entry.
- `LowgGoldenTest`, `G1TwoAircraftCircuitsTest`, `G1TwoAircraftMinimalSpec`, `G2CrossAerodromeVfrTest`, `G3aPilotTrainedGoAroundTest` class docstrings: add `@see G3aRunwayObstructionTest` (or sibling-list addition) per fn-11.2 pattern.

### Step 7: full verify (R12)

`./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` exits 0. All six golden tests (G0 / G1 / G1-min / G2 / G3a-trained / **G3a-obstruction**) GREEN. detekt baseline unchanged.

## Investigation targets

**Required** (read before coding):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotTrainedGoAroundTest.kt:147-572` — full structure of the canonical mirror test
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt:27-79` — LOWG fixture loader
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixture.kt:287-366` — `Fixture.load` shape
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt:139-156` — `GA-POST-CLEAR` interrupt (the canonical regression path)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt` — vacate-coordination closure pattern (fn-8.3 R7 shape)

**Optional** (reference as needed):
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/BeliefState.kt:23-153` — for snapshot inspection in pins
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/CommitmentLifecycle.kt` (or sibling) — for sticky-witness reset semantics
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotAtcInitiatedGoAroundSpec.kt` — fn-12.2's pilot-side tests (so the sim test does NOT redundantly pin pilot-internal state)

## Key context

- **The sim-level test pins observable behaviour at the sim-output boundary** primarily — radio transmissions, observable phase transitions, world-state-change events. **Allowed inspections** of controller-internal state that the test does need: `BeliefState.commitments` (for stage transitions), `BeliefState.runwayObstructions` (for fold verification), and the coordinations ledger (for vacate-closure pin per fn-8.3 R7-style). These are observable in the trace harness as belief snapshots / ledger state per cycle. Pilot-internal state (mission tree, `pendingAtcGoAroundFrom` flag, etc.) remains the job of fn-12.2's `PilotAtcInitiatedGoAroundSpec`. The sim test is end-to-end with controller-state inspections allowed where the trace harness exposes them.
- **Three-layer pin pattern** is the established discipline from fn-11.2. Causal + sticky-witness + kinematic non-event together prove the right behaviour without overfitting on intermediate state.
- **Time band ±15%** is the project pattern (fn-8.3-era). Calibrate at first GREEN; do not pin the exact wall.
- **World mutation API** comes from fn-12.1. The test consumes it; do not redesign it here.
- **`onTick` mutation hook**: verify the exact harness API. `G3aPilotTrainedGoAroundTest:237` uses `runUntilWithStateTrace`. The world-mutation injection point is whatever the harness provides — likely a per-tick callback that returns `SimState`. If the harness doesn't provide a clean injection point, fold the addition into Task .1 retroactively rather than working around it.

## Acceptance

- [ ] R10: `G3aRunwayObstructionTest.kt` exists in `sim/src/jvmTest/`. Single-aircraft LOWG fixture, mission goal `CircuitTraining(listOf(FullStop))` — single planned circuit; recovery circuit provided by `handleGoAround` tree rewrite. World authors `runway.obstruction = RunwayObstruction(clearsAt = T_obs + 60.seconds)` at sim time `T_obs` chosen such that `T_obs > T_ClearedToLand` (post-clearance pin per Boundaries). Three-layer pin pattern with **separated timestamps** (decision-cycle for stage transitions; transmission-record-start for radio order). Layer 2 pins regression at GoAround **decision-cycle** time (NOT transmission-start, NOT `Report(GoingAround)` time). Vacate-coordination closure pin per fn-8.3. Time band ±15% on observed wall. Companion-transmission pin (`RunwayObstructionInformation` emitted in same controller-output cycle as `GoAround`). World-only test trigger.
- [ ] R11: All cross-reference doc updates landed per the docs-gap-scout enumerated list above. AGENTS.md, STRATEGY.md, wiki design-decisions × 3, controller KDocs × 2, Fixtures.kt provenance, sibling test class docstrings × 5.
- [ ] R12: `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` exits 0. All six golden tests (G0 / G1 / G1-min / G2 / G3a-trained / G3a-obstruction) GREEN. detekt baseline unchanged.
- [ ] No event injection in the test fixture — verify by grep over the test for `ControllerEvent.RunwayObstructionDetected` (only allowed in trace-extraction assertions, not in fixture authorship).
- [ ] No belief-state mutation in the test fixture — verify by grep over the test for `BeliefState` mutation (only allowed in snapshot inspection for pins).

## Done summary
fn-12.3 ships the closing piece of the fn-12 epic: a sim-level golden
test `G3aRunwayObstructionTest` exercising the end-to-end ATC-instructed
reactive go-around path on a world-authored runway obstruction at LOWG,
plus the cross-reference doc updates that anchor the new path across
AGENTS / STRATEGY / wiki / controller KDocs / fixture provenance / and
five sibling test class docstrings. The test composes fn-12.1's typed
RunwayObstruction surface + sim wiring + reactive controller rule with
fn-12.2's pilot-side `pendingAtcGoAroundFrom` recognition arm into a
single deterministic run pinning the entire stack — world author → sim
expiry/diff/event → controller belief fold → reactive
`ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule with `Immediate` advancement +
companion `RunwayObstructionInformation` transmission → pilot Tick A/B
recovery → re-entry → recovery landing → vacate.

Three-layer pin pattern extended with **separated decision-cycle vs
transmission-start timestamps**: decision-cycle ordering is asserted via
a `nextTransmissionId`-counter walk over the trace (cycle that minted
the txId is the originating cycle), independently of the radio
serialization timing on the same frequency. Same-decision-cycle pin for
`GoAround` + `RunwayObstructionInformation` (rejecting later standalone
companion responses), Layer 1a decision-cycle ordering for
`Detected/Cleared` belief transitions vs `GoAround` / `ClearedToLand
(recovery)` decision-cycle times, Layer 2 sticky-witness regression
equality `Stage_regression.time == GoAround.decisionTime` (`Immediate`
advancement, not `GA-POST-CLEAR` interrupt), full three-witness reset
(`touchedDown`, `pilotReady`, `observedReports`),
`obstructionGoAroundIssuedThisAttempt` no-refire witness, Layer 3
kinematic non-event (no `LandingRoll` / `Vacating` before
`Report(GoingAround)`), per-controller event scoping pin (exactly one
`None → Some` and one `Some → None` in TOWER's `runwayObstructions[16C]`
belief slice), companion-content pin (runway + clearsAt match authored),
obstruction-lifetime pin (`clearedMs >= clearsAtMs` — catches a
hypothetical regression that expires the obstruction immediately after
detection), R7 vacate-coordination closure on the recovery landing,
time band ±15% (observed wall 1_399_000 ms ≈ 23.3 sim minutes).

World-only test trigger (per `feedback_world_only_test_triggers.md`):
test authors `runway.obstruction` one-shot via a new optional
`onAfterEvent: (SimEvent, SimState) -> SimState` hook added to
`runUntilWithStateTrace`. `var obstructionAuthored` guard preserves the
`clearsAt` immutability invariant; defense-in-depth via the sim's
existing world-diff producer `check(...)`. No `ControllerEvent`
injection, no `BeliefState` mutation.

Codex review converged in four rounds: round 1 added same-cycle
companion pin + regression equality (and revealed that the
`inFlightTransmissions` proxy is wrong — switched to `nextTransmissionId`
counter walk); round 2 swept Layer 1a from tx-start to decision-cycle
comparisons + STRATEGY parity with AGENTS; round 3 refined Event.kt's
new taxonomy to three source classes (radio-derived aircraft-scoped /
controller-state-derived aircraft-scoped / world-state-derived no-
aircraft); round 4 added `clearsAt` lifetime semantics + `pilotReady`
witness reset coverage. Round 5 SHIP.

Memory entry captured at `bug/test-failures/sim-test-pins-must-compare-
against-2026-05-10` on the decision-cycle vs tx-start distinction —
will help future sim tests reach for the helper pattern up-front.

All six golden tests (G0 / G1 / G1-min / G2 / G3a-trained / G3a-obstruction)
GREEN. detekt clean. fn-12 epic closed.
## Evidence
- Commits: c5ef817, d6ecc11, 34c7e58, be96725, 02e8d7d
- Tests: ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
- PRs: