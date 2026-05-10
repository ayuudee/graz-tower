---
satisfies: [R5, R6, R7, R8, R9b, R10]
---

## Description

Author the `G3aPilotTrainedGoAroundTest` golden test and land the cross-
reference doc updates that follow once G3a goes green. Mirrors `LowgGoldenTest`
shape (single `@Test` method, fixture-driven, single behavioral narrative).
Plus the time-band tightening per fn-8.3 spec decision #11 inheritance.

**Size:** M (estimated ~400-500 lines test code; mirrors G0; the underlying
infrastructure from fn-11.1 carries the structural complexity).

**Files:**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotTrainedGoAroundTest.kt`
  (NEW, ~400-500 lines)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt`
  (audit at task time: extend existing `Fixtures.LOWG` with the new goal, OR
  add `Fixtures.LOWG_TRAINED_GO_AROUND` if goal authoring is distinct enough)
- `AGENTS.md` (`## Golden tests` section: heading + new G3a bullet alongside
  G0/G1/G1-minimal/G2)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LowgGoldenTest.kt`
  (`@see G3aPilotTrainedGoAroundTest` cross-reference)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt`
  (G3a sibling reference — KDoc has no `@see` block currently; mirror existing
  prose form)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftMinimalSpec.kt`
  (`@see G3aPilotTrainedGoAroundTest`)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt`
  (sibling-tests prose update)
- `wiki/design-decisions/2026-04-21-ifr-pilot-route-planner.md` (table row at
  line 7)
- `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md`
  (resolution note: gap closed by G3a)
- `docs/design/ifr-pilot-route-planner-plan.md` (line 141 stub if relevant)

## Approach

### 1. Test structure (mirror of G0 / `LowgGoldenTest`)

Follow `LowgGoldenTest.kt:39-...` line-for-line. Anchors:

- **Class KDoc** mirrors G0/G1/G2 prose. Names sibling tests; describes what
  G3a distinctively pins (single-aircraft trained GA outcome — the mission-
  tree-fork-at-compile-time pattern).
- **Single `@Test` method** named e.g. `pilot trains a go-around on circuit 1, lands on circuit 2`.
- **Sections**: load fixture → declare aircraft + mission → SimState.initial →
  seed initialEvents → runUntilWithStateTrace → outcome pins → causal pins →
  sticky-witness pins (Option A — stage regression) → kinematic non-event pin
  → time band → diagnostic preamble.

### 2. Fixture + state

Per pass-1 plan-review finding (fn-8 era): `LoadedFixture` carries
`world, worldIndex, controllers, initialEvents` only — NOT `flightPlans` /
`weatherByAerodrome`. Test reads via the `Fixtures.LOWG` (or new
`Fixtures.LOWG_TRAINED_GO_AROUND`) object directly.

```kotlin
val fixture = Fixtures.LOWG  // OR LOWG_TRAINED_GO_AROUND if distinct
val loaded = fixture.load().getOrElse { fail("...") }
val aircraftId = AircraftId("OE-ABC")
val mission = createMission(
    goal = HighLevelGoal.CircuitTraining(
        outcomes = listOf(CircuitOutcome.GoAround, CircuitOutcome.FullStop)
    ),
    startPhase = PilotPhase.AtStand,
    time = SimTime.ZERO,
    filedPlan = fixture.flightPlans[aircraftId]
        ?: fail("missing flight plan for $aircraftId"),
)

// Aircraft constructed with type yielding WakeCategory.L (C172 etc.) per
// fn-8.3 spec convention. Wake category not load-bearing for single aircraft
// but maintains the firewall-doctrine pattern.
val aircraft = AircraftState(
    id = aircraftId,
    type = AircraftType.C172,
    pilotMission = mission,
    positionPoint = fixture.standPointId,
    // ... other fields ...
)

// Weather REQUIRED for runway-bearing aerodromes (per fn-8.3 spec
// pass-2 plan-review finding #5):
val initial = SimState.initial(
    seed = 42L,
    world = loaded.world,
    worldIndex = loaded.worldIndex,
    aircraft = listOf(aircraft),
    controllers = loaded.controllers.values.toList(),
    weatherByAerodrome = mapOf(lowg to fixture.weather),
)
```

### 3. Initial events

Standard G0-style event sequencing — flight-plan filing at `SimTime.ZERO`,
ATIS issued, pilot decision tick, controller cycle, physics tick.

### 4. Outcome pins (single aircraft)

```kotlin
val final = finalState.aircraft.getValue(aircraftId)

check(final.pilotMission?.isComplete == true) { "mission incomplete\n$journey" }
check(final.altitudeM == 0.0) { ... }
check(final.phase == PilotPhase.Parked || final.phase == PilotPhase.AtStand) { ... }

val standPoints = loaded.world.aerodromes
    .getValue(lowg).stands.values.map { it.point }.toSet()
check(final.positionPoint in standPoints) { ... }
```

### 5. Causal pins — three-layer pattern (the load-bearing assertions)

Per practice-scout's three-layer pattern. Single aircraft means no multi-
aircraft serialization invariants; pins target the GA shape itself.

#### Layer 1 — Causal partial-order on transmission events

```kotlin
// Find the Report(GoingAround) record:
val goingAroundRecord = records
    .firstOrNull { rec -> /* ReportEvent.GoingAround && from = aircraftId */ }
    ?: fail("Pilot never transmitted Report(GoingAround) — trained GA didn't fire.\n$journey")

// Find FIRST ClearedToLand AFTER Report(GoingAround) — this is the
// circuit-2 clearance. Per pass-9 plan-review finding #6: `lastOrNull()`
// over all `ClearedToLand` could pick a reissue (controller may reissue
// after readback escalation) rather than the first circuit-2 clearance.
val landingClearance = records
    .filter { rec -> /* ClearedToLand instruction targeting aircraftId */ }
    .firstOrNull { it.time > goingAroundRecord.time }
    ?: fail("No ClearedToLand received for circuit 2 attempt " +
            "(after Report(GoingAround)).\n$journey")

// Find Report(RunwayVacated) — the canonical landing-complete observable:
val vacatedRecord = records
    .firstOrNull { rec -> /* ReportEvent.RunwayVacated && from = aircraftId */ }
    ?: fail("Aircraft never reported RunwayVacated.\n$journey")

// Causal chain: goingAround ≺ ClearedToLand(circuit 2) ≺ RunwayVacated.
check(goingAroundRecord.time < landingClearance.time &&
      landingClearance.time < vacatedRecord.time) {
    "Causal chain violated: Report(GoingAround) (${goingAroundRecord.time}) " +
    "< ClearedToLand (${landingClearance.time}) " +
    "< RunwayVacated (${vacatedRecord.time}).\n$journey"
}
```

#### Layer 2 — Sticky-witness on the regression path (Option A)

Per fn-8.3's Option A model: commitment SURVIVES the GA; stage REGRESSES.
Per pass-3 plan-review finding #2 — the regression source for trained GA is
**post-clearance** (`GA-POST-CLEAR` at `TowerArrival.kt:148-156`), NOT
pre-clearance: real-ATC + the codebase's `ARR-LAND` rule issues
`ClearedToLand` proactively once the pilot reports Downwind/Base/Final, so
the trained pilot RECEIVES landing clearance BEFORE going around at
short-final. Regression source: `LandingClearanceIssued` or
`AwaitLandedObserved` → `AwaitDownwind`. Sticky witnesses RESET on
regression. The same commitment lifetime spans fork → rejoin → land.

```kotlin
// Use existing harness query:
val stageTransitions = trace.commitmentStageTransitions(aircraftId, towerControllerId)

// Find the regression: a post-clearance transition (LandingClearanceIssued
// or AwaitLandedObserved) back to AwaitDownwind. There should be exactly one
// in the run (the trained GA).
//
// Per pass-3 plan-review finding #2 (correcting pass-1 finding #2) +
// pass-5 finding #3: real-ATC + the codebase's ARR-LAND rule issues
// ClearedToLand proactively once HasReportedPositionCall is satisfied.
// The trained-GA compound reports REPORT_DOWNWIND + REPORT_BASE — that's
// sufficient (HasReportedPositionCall accepts any subset of Downwind/
// Base/Final per fn-8.3's sticky witness `observedReportsDuringCommitment`).
// No REPORT_FINAL needed: the new FLY_FINAL_TO_SHORT_FINAL replaces the
// standard FLY_FINAL+REPORT_FINAL+AWAIT_LANDING_CLEARANCE+LAND sequence.
// Therefore the pilot RECEIVES ClearedToLand BEFORE going around at
// short-final. The trained GA is POST-clearance, fires GA-POST-CLEAR
// at TowerArrival.kt:148-156. Regression source = LandingClearanceIssued
// or AwaitLandedObserved.
//
// Per pass-1 plan-review finding #3: the typed name is `TowerArrivalStage.X`
// (not bare `Stage.X`).
val postClearStages = setOf(
    TowerArrivalStage.LandingClearanceIssued,
    TowerArrivalStage.AwaitLandedObserved,
)
val regression = stageTransitions
    .firstOrNull { t ->
        val from = t.from.fold({ null }) { it } ?: return@firstOrNull false
        val to = t.to.fold({ null }) { it } ?: return@firstOrNull false
        from in postClearStages &&
        to == TowerArrivalStage.AwaitDownwind
    }
    ?: fail("Expected stage regression {LandingClearanceIssued | AwaitLandedObserved} → " +
            "AwaitDownwind on trained GA but none observed.\n$journey")

// At the trace cursor BEFORE the regression, witnesses MAY have been set.
// AT the cursor AFTER the regression, witnesses MUST be reset.
val stateAfter = trace.stateAtOrBefore(regression.after.time)
val commitmentAfter = stateAfter.beliefs[towerControllerId]
    ?.commitments?.get(aircraftId)
    ?: fail("Commitment missing after regression\n$journey")

check(!commitmentAfter.touchedDownDuringCommitment) {
    "touchedDownDuringCommitment should be reset post-GA-regression; " +
    "got ${commitmentAfter.touchedDownDuringCommitment}.\n$journey"
}
check(commitmentAfter.observedReportsDuringCommitment.isEmpty()) {
    "observedReportsDuringCommitment should be reset post-GA-regression; " +
    "got ${commitmentAfter.observedReportsDuringCommitment}.\n$journey"
}
```

(Note: if user picks **Option B** at plan-review time, this layer reshapes:
assert circuit-1 commitment is *absent* from `BeliefState.commitments` after
the GA report; a *new* commitment is *present* after the rejoin downwind.)

#### Layer 3 — Kinematic non-event ("did not touch down on circuit 1")

```kotlin
// Per pass-9 plan-review finding #7: use transition.after.time (the time
// the new phase is observed), not before.time (the time the prior phase
// was observed). For a transition INTO LandingRoll, after.time is when
// LandingRoll first appears.
val phaseTransitions = trace.transitionsOf { st ->
    st.aircraft[aircraftId]?.phase
}.map { it.to to it.after.time }

// No state where phase == LandingRoll exists BEFORE the Report(GoingAround):
val landingRollBeforeGA = phaseTransitions.any { (phase, time) ->
    phase == PilotPhase.LandingRoll && time < goingAroundRecord.time
}
check(!landingRollBeforeGA) {
    "Aircraft entered LandingRoll before going around — circuit 1 should " +
    "not have touched down.\n$journey"
}
```

### 6. Forced-trigger pin (R8 analogue from fn-8 era)

The bare-minimum pin that detects "trained GA didn't actually happen" — already
covered by Layer 1's `goingAroundRecord ?: fail(...)`. If the pilot never
transmits `GoingAround`, the test fails loud. No separate assertion needed.

### 7. Time band

**First-implementation acceptance**: 30-min generous ceiling (G0-style, slight
inflation for the GA detour). After first green, capture observed wall in
`## Evidence` and tighten to ±15% band — both iterations done in fn-11.2.

```kotlin
val until = SimTime.ZERO + SimDuration.ofMillis(30 * 60 * 1000L)
val finalState = runUntilWithStateTrace(initial, initialEvents, until)
// ... assertions ...

// Post-first-green: tighten to ±15% around observed wall. Update test +
// capture both iterations in `## Evidence`.
```

### 8. Diagnostic preamble

Mirror G0/G1's debug block — print pilot journey, commitment-stage transitions,
mission-step transitions, position-point transitions, responsibility transitions
for the single aircraft. Critical for debugging when (not if) the trained-GA
fork-point shifts.

### 9. Cross-reference doc updates

- **`AGENTS.md`** § Golden tests:
  - Heading: `## Golden tests (G0, G1, G1 minimal, G2, G3a)`.
  - New bullet entry after G2 (after line 191 in current file). Mirror
    G2's format.

- **`LowgGoldenTest.kt:51-54`** — add `@see G3aPilotTrainedGoAroundTest` to
  the existing `@see` block.

- **`G1TwoAircraftCircuitsTest.kt:42-83`** — add G3a to the prose sibling
  list (KDoc has no formal `@see` block; mirror existing form).

- **`G1TwoAircraftMinimalSpec.kt:63`** — add `@see G3aPilotTrainedGoAroundTest`
  to the existing `@see` block.

- **`G2CrossAerodromeVfrTest.kt:48-83`** — add G3a to the "Sibling tests"
  inline list.

- **`Fixtures.kt:18-47`** — if a new fixture (`LOWG_TRAINED_GO_AROUND`) is
  authored, add an entry in the per-fixture provenance block. If reusing
  `Fixtures.LOWG`, add a note to the LOWG entry cross-referencing G3a.

- **`pilot/.../PilotMission.kt`** — if fn-11.1 didn't fully cover the KDoc
  rewrites for the sealed-interface and `planMission`, finish them here.
  (Should be done in fn-11.1, but verify.)

- **`wiki/design-decisions/2026-04-21-ifr-pilot-route-planner.md:7`** —
  table row referencing `HighLevelGoal.CircuitTraining` updated to name the
  new `outcomes`-list shape.

- **`wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md`**
  — append a resolution note: "Closed by fn-11 (G3a). Test: `G3aPilotTrainedGoAroundTest.kt`."

- **`docs/design/ifr-pilot-route-planner-plan.md:141`** — `planMission` stub
  comment updated if it still references the old shape.

## Investigation targets

**Required** (read before coding):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LowgGoldenTest.kt`
  (canonical mirror; read top-to-bottom before writing G3a — the structural
  template).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt`
  (multi-witness, time-band, state-trace pattern; mirror at single-aircraft
  scale).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/SimTraceQueries.kt`
  (`commitmentStageTransitions`, `responsibilityTransitions`,
  `missionStepTransitions`, `positionPointTransitions`, `transitionsOf`,
  `stateAtOrBefore`).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/TransmissionRecord.kt`
  (`firstControllerInstructionOf<T>(aircraftId)`, `firstPilotReportOf<T>`).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt`
  (current `Fixtures.LOWG` shape; `LOWG_TWO_AIRCRAFT` as a new-fixture-author
  precedent if G3a needs its own fixture).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/BeliefState.kt:188+`
  (`SeparationAssessment`, `Commitment` shape — for sticky-witness reads in
  Layer 2 pin).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Commitment.kt:101, 123, 157`
  (sticky-witness fields).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/Controller.kt:609-661`
  (`advanceCommittedStages` + `isStageRegression` + witness reset on
  regression — the load-bearing Option A invariant).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt:140-156`
  (`GA-PRE-CLEAR` and `GA-POST-CLEAR` — wired to `targetStage = AwaitDownwind`).

**Optional** (reference as needed):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt`
  (multi-controller staging; not relevant for single-aircraft G3a but useful
  KDoc/sibling-list reference).
- `pilot/src/commonTest/kotlin/.../observe/SelfInitiatedGoAroundResponseSpec.kt`
  (pre-existing pilot-side reactive-GA spec; useful contract reference for
  the response stage that trained-GA also goes through).

## Key context

- **The trained-GA fork is at compile time** — `planMission` reads the
  `outcomes` list and produces a static mission tree with the GA branch
  baked in. The pilot follows the tree at runtime; no recognition-stage
  trigger fires.
- **The Layer-2 sticky-witness pin assumes Option A (stage regression).**
  If user picks Option B at plan-review time, this layer reshapes; flagged in
  fn-11 epic spec § Decision context #3.
- **Wake category lives on `AircraftState.type`**, not `FiledPlan` (per
  fn-8.3's pass-2 plan-review finding #6). Single aircraft so wake-rule pin
  isn't load-bearing here, but maintain the convention.
- **Weather is REQUIRED for runway-bearing aerodromes** — `SimState.initial`
  rejects empty weather maps for LOWG. Author weather via `fixture.weather`
  per fn-8.3 convention.
- **Stand membership uses `stands.values.map { it.point }.toSet()`**,
  mirroring `LowgGoldenTest.kt:427-430`. NOT `stands.keys`.
- **Pre-existing flake** `:migration:jvmTest >
  LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport`
  remains out of scope; capture verbatim if observed.

## Acceptance

- [ ] `G3aPilotTrainedGoAroundTest.kt` exists at
      `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/`.
- [ ] Single `@Test` method, fixture-driven, mirror-of-G0 structure.
- [ ] Goal authored as
      `HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.GoAround,
      CircuitOutcome.FullStop))`.
- [ ] Aircraft constructed with `AircraftType.C172` (or whatever yields
      WakeCategory.L).
- [ ] Weather authored for LOWG.
- [ ] Outcome pins: aircraft completes mission, parked at stand point.
- [ ] Layer 1 (causal): `Report(GoingAround).time < ClearedToLand(circuit 2).time
      < Report(RunwayVacated).time`. Reads pilot transmissions via
      `firstPilotReportOf` per fn-8.3 instruction-vs-observation discipline.
- [ ] Layer 2 (sticky-witness, Option A — stage regression): exactly one
      stage regression observed (`from in
      setOf(TowerArrivalStage.LandingClearanceIssued,
      TowerArrivalStage.AwaitLandedObserved) →
      TowerArrivalStage.AwaitDownwind`, via `GA-POST-CLEAR`); post-regression
      sticky witnesses are reset (`touchedDownDuringCommitment == false`,
      `observedReportsDuringCommitment.isEmpty()`).
- [ ] **Radio-delivery prerequisite for `GA-POST-CLEAR`** (per pass-9
      plan-review finding #3 + pass-10 finding #3): the test asserts
      Layer 2's regression-pin AFTER first asserting that the pilot's
      `Report(GoingAround)` transmission was actually delivered to /
      processed by the controller. Phrase in terms of available trace
      observables — the simplest pin is that the records collection
      contains the pilot's `Report(GoingAround)` directed at / received
      by the tower's radio (e.g. `rec.recipient` includes
      `lowgTower.id`, OR — if the records' shape doesn't surface
      recipient — assert at least that the regression timing is AFTER
      the GoingAround record's time). If a new trace helper is needed
      (e.g. `transmissionsReceivedBy(controllerId)`), specify it
      explicitly in the test rather than referencing
      `tower.beliefs.events` (controller events are transient in
      `controllerDecide`, not persisted as a queryable surface).
- [ ] Causal pin (post-clearance): `ClearedToLand(circuit 1).time <
      Report(GoingAround).time` — clearance arrives before the trained GA
      fires (per pass-3 plan-review finding #2; distinguishes trained-GA-
      post-clearance from a hypothetical pre-clearance variant).
- [ ] **R7 vacate-coordination closure pin** (per pass-12 plan-review
      finding #2): after the second circuit's full-stop landing,
      `finalState.beliefs[towerControllerId]?.coordinations?.get(aircraftId)`
      contains NO pending `AfterLandingVacateVia` / `BacktrackRunway`
      coordination — i.e. the vacate-readback closed the coordination
      ledger entry per fn-8.3's discipline. Mission-completed pin alone
      doesn't catch a lingering coordination.
- [ ] Layer 3 (kinematic non-event): no `phase == LandingRoll` state observed
      *before* `Report(GoingAround).time`.
- [ ] Test runs green.
- [ ] Time band evidence (per pass-7 plan-review finding #4 — explicit
      evidence format + pass-12 finding #3 — KDoc requirement):
      - **`## Evidence` records**: initial observed completion wall (in
        ms) from the first green run; chosen center; computed lower/upper
        bounds (center × 0.85 / 1.15); final rerun command + result after
        tightening.
      - **`G3aPilotTrainedGoAroundTest` KDoc** (per epic R8 + pass-12
        finding #3 — the test class itself documents the time band):
        observed completion wall, computed ±15% bounds, and one-line
        rationale (mirroring `G1TwoAircraftCircuitsTest.kt:511-540`'s
        format).
      - First-impl uses 30-min generous ceiling; after first green,
        observed wall captured and test tightened to ±15% band
      — both iterations done in fn-11.2.
- [ ] G0 (`LowgGoldenTest`), G1 (`G1TwoAircraftCircuitsTest`), G1 minimal
      (`G1TwoAircraftMinimalSpec`), G2 (`G2CrossAerodromeVfrTest`) stay green.
- [ ] **Verification command (per epic R9 + pass-2 plan-review finding #4):**
      `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest
      :protocol:jvmTest detekt` exits 0. fn-11.2 is pilot/sim closure;
      `:migration:jvmTest` is NOT required. If migration is run anyway, the
      ONLY acceptable failing test is the exact pre-existing flake
      `xyz.easiersaid.twr.migration.world.LjmbWorldCandidateValidationTest >
      writesLjmbCurrentCoreValidationReport()` (any other migration failure
      blocks fn-11.2).
- [ ] `./gradlew detekt` baseline unchanged.
- [ ] `AGENTS.md` § Golden tests heading + new G3a bullet land.
- [ ] `LowgGoldenTest.kt` `@see G3aPilotTrainedGoAroundTest` added.
- [ ] `G1TwoAircraftCircuitsTest.kt` sibling list adds G3a.
- [ ] `G1TwoAircraftMinimalSpec.kt` `@see G3aPilotTrainedGoAroundTest` added.
- [ ] `G2CrossAerodromeVfrTest.kt` sibling-tests prose adds G3a.
- [ ] `wiki/design-decisions/2026-04-21-ifr-pilot-route-planner.md` table row
      updated for new constructor shape.
- [ ] `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md`
      adds resolution note: gap closed by G3a.

## Done summary
G3aPilotTrainedGoAroundTest landed: single AI aircraft at LOWG flies CircuitTraining(outcomes=[GoAround, FullStop]) — circuit 1 trained-GA at short-final, circuit 2 full-stop. Three-layer pin pattern (causal partial-order + GA-POST-CLEAR sticky-witness regression + kinematic non-event), R7 vacate-coordination closure pin, time band tightened to ±15% of 1393 s observed wall. Cross-reference doc updates landed (AGENTS.md, four golden test KDocs, Fixtures.kt, two wiki design-decisions). All five golden tests (G0/G1/G1-min/G2/G3a) green; codex SHIP after one round of style-consistency fix. Closes the go-around integration-test gap from wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md.
## Evidence
- Commits: 3bb775e, a2c6219b81a278895d380174f42606fd63fb7717
- Tests: ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest :protocol:jvmTest detekt
- PRs: