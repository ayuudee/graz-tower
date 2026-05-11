---
satisfies: [R11, R12, R14]
---

# fn-15.2 — Sim integration test + doc cross-references for G3a-react-tailwind

## Description

Sim integration test (`G3aPilotReactiveTailwindTest`) at LOWG + cross-reference doc updates per epic R12. Sibling of fn-14.2; adds the ninth golden test. Covers R11 (sim test), R12 (9 repo-internal sites + 8 sibling test class docstrings), and R14 (epic-level full verify).

## Problem

fn-15.1 lays every primitive but only proves them via pilot-side unit tests. The epic's golden-path acceptance is an end-to-end sim test at LOWG where the world authors a tailwind shift on final, the pilot recognises the tailwind exceedance, transmits `Report(GoingAround)`, executes the GA via `applyTailwindGoAround`, re-enters circuit, and lands when wind returns to headwind. This task writes that integration test and updates every doctrinal cross-reference site so the new scenario is visible in the project's documentation surface.

The C172 leaf scenario models the **AFH-advisory regime** (codex round-1 closure — Cessna 172S/172R POH does not publish a hard tailwind limit; the 10 kt value used in this sim is the FAA AFH industry-standard advisory). The doctrinal anchor surfaced in cross-reference docs distinguishes per-type severity (advisory for C172 / hard limit for B738 per FCOM).

Per fn-14 codex review issue #8 closure (carried forward): this task does **not** add event-tracing instrumentation. Event-count pins live in fn-15.1 pilot unit tests; sim test asserts only externally observable behaviour (transmissions, commitment stage regression, no touchdown, recovery landing).

## Files (read or modify)

- **READ**
  - `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveCrosswindTest.kt` — fn-14.2 sibling test. Mirror structure: docstring shape, fixture, two-transition wind authorship, three-layer pins, recovery landing. The new test mirrors this end-to-end.
  - `sim/src/commonTest/kotlin/xyz/easiersaid/twr/sim/testing/SimTraceQueries.kt` — fn-14's `weatherTransitions(aerodromeId)` extractor. Reused unchanged.
  - `sim/src/commonTest/kotlin/xyz/easiersaid/twr/sim/testing/RunUntil*.kt` (or `runUntilWithStateTrace`-defining file) — `onAfterEvent` hook (fn-14 surface). Reused unchanged.
  - `sim/src/commonTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt` — LOWG fixture; reused with minor provenance KDoc update.
  - `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt` — `GA-PRE-CLEAR` / `GA-POST-CLEAR` interrupts (read only; trigger-agnostic, unchanged).
  - `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/TowerArrivalStage.kt` — stage names (`LandingClearanceIssued`, `AwaitLandedObserved`, `AwaitDownwind`); exact spellings.
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt:123-160` — `Wind.invoke` validator range (`directionDegrees in 0..360`; both `0` and `360` valid).

- **MODIFY (test file — new)**
  - `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveTailwindTest.kt` — new test class. JVM-only test (mirrors fn-14.2 location). One test method, LOWG, single C172. Two world-state transitions via `onAfterEvent` (per epic Decision #6).

- **MODIFY (doc cross-references per epic R12 — 9 sites + 8 sibling test docstrings)**
  - `AGENTS.md` § Golden tests — add G3a-react-tailwind bullet (9 tests total).
  - `STRATEGY.md` § Runtime simulator track — note the second pilot-reactive POH/AFH recognition axis; quintuple-covered reactive-GA decision space.
  - `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` § Resolution — add fn-15 closure paragraph for five-path reactive-GA coverage; surface the **per-type doctrinal severity asymmetry** (advisory for C172 / hard limit for B738) as a deliberate modelling choice.
  - `wiki/domain/aviation-world.md` — extend the `AircraftType` section (added in fn-14.2) with `maxTailwindKnots` field; doctrinal note on per-type severity (advisory vs hard limitation); cross-reference `maxCrosswindKnots` for sibling pattern.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt` inline comment block — update "FOUR GA paths" → "FIVE GA paths" (lines 98 area), update path enumeration accordingly. (Some inline-comment updates may overlap with fn-15.1 Step 10; the .2 pass ensures every "four" reference in inline comments is updated to "five".)
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt` file-level KDoc — update "Current leaf set (3 leaves)" → "Current leaf set (4 leaves)"; add `TailwindLimitExceeded` entry with per-type severity note. (Overlap with fn-15.1 Step 6; .2 ensures the count is exact.)
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/AircraftType.kt` file-level KDoc + per-leaf KDocs — `maxTailwindKnots` per-leaf source citation (C172 = POH-absent + AFH-advisory framing; B738 = FCOM §1 hard limit). (Overlap with fn-15.1 Step 3; .2 ensures completeness.)
  - `sim/src/commonTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt` LOWG provenance — add G3a-react-tailwind consumer to the comment block.
  - Sibling test class docstrings (`@see G3aPilotReactiveTailwindTest` cross-ref): `LowgGoldenTest`, `G1TwoAircraftCircuitsTest`, `G1TwoAircraftMinimalSpec`, `G2CrossAerodromeVfrTest`, `G3aPilotTrainedGoAroundTest`, `G3aRunwayObstructionTest`, `G3aRunwayObstructionContinueApproachTest`, **`G3aPilotReactiveCrosswindTest`** (the immediate sibling — bidirectional cross-reference).

- **MODIFY (auxiliary — optional, repo-external)**
  - `~/.claude/plans/pilot-firewall.md` Deferments register — file the seven fn-15 deferments listed in the epic spec § Deferments register, mark `D-PASS-g3a-react-tailwind-limit` (filed by fn-14) as closed by this epic with commit SHA. **Optional / non-acceptance**: this file lives outside the repo. Best-effort housekeeping; the load-bearing closure record is the flow epic + git commit history.

## Approach (numbered Steps)

### Step 1 — Confirm test infrastructure presence

`grep -rn "onAfterEvent\|findEmittingCycleMs\|weatherTransitions\|runUntilWithStateTrace" sim/src/commonTest` confirms fn-14's harness surfaces exist. Read fn-14.2's `G3aPilotReactiveCrosswindTest.kt` end-to-end to internalise the two-transition + three-layer pin pattern. Confirm C172 `maxTailwindKnots` from fn-15.1 (= 10 kt AFH-advisory).

### Step 2 — Build the LOWG fixture

Single C172 aircraft. `HighLevelGoal.CircuitTraining(outcomes = listOf(FullStop))` — one circuit; GA forces re-entry, recovery on circuit 2. Initial weather:
```
Wind.unsafe(directionDegrees = runwayHeading, speedKnots = 10)
```
Pure 10 kt headwind — zero tailwind, zero crosswind (mirror fn-14.2's initial wind shape). `ClearedToLand` issued before the wind shift (exercises GA-POST-CLEAR).

`runwayHeading` is resolved via `lowgRunwayId.headingDegreesMagnetic()` (e.g. runway `35C` → 350°M).

### Step 3 — Author the wind shift via `onAfterEvent` (post-clearance pattern)

**Trigger predicate** (mirror fn-14.2's pattern at `G3aPilotReactiveCrosswindTest.kt:758-775` `isPostClearanceLanding(...)` helper):
- Controller commitment stage ∈ `{TowerArrivalStage.LandingClearanceIssued, TowerArrivalStage.AwaitLandedObserved}` AND
- `aircraft.phase is PilotPhase.Final`

Two `onAfterEvent` registrations:

1. **Tailwind-exceeds-limit** (one-shot `var tailwindAuthored = false`):
   - When predicate above holds AND `!tailwindAuthored`
   - Compute `tailwindDirection = (runwayHeading + 180) mod 360`. Aviation-display convention: prefer `360` over `0` for due-North runs, so when the modulo result is `0` clamp to `360`. `Wind.invoke` **accepts** `directionDegrees in 0..360` (verified at `Instruction.kt`; both `0` and `360` validate) — the normalisation is for **display/convention consistency** with the aviation FROM-360-spelling, not because `Wind` rejects `0`.
   - Mutate `simState.weatherByAerodrome[LOWG]` to:
     ```
     WeatherObservation(
         wind = WindReport.Available(
             Wind.unsafe(directionDegrees = tailwindDirection, speedKnots = 15)
         ),
         qnh = <preserve existing>,
         visibility = <preserve existing>,
     )
     ```
     `15 kt` pure tailwind (well above C172's 10 kt AFH-advisory tailwind value from fn-15.1 R1; the C172 POH itself does not publish a hard tailwind limitation, so the AFH advisory is the modelling anchor and the 5-kt margin guards against any per-edition adjustment).
   - Set `tailwindAuthored = true`.

2. **Tailwind-clears-to-limit** (one-shot `var tailwindClearedToLimit = false`):
   - When `Report(GoingAround)` has been transmitted (recognise via `tx is Report && ReportEvent.GoingAround in tx.events`) AND aircraft back on downwind (e.g. controller commitment stage ∈ `{AwaitDownwind, AwaitBaseTurn}` after the regression) AND `!tailwindClearedToLimit`
   - Reset wind to initial:
     ```
     Wind.unsafe(directionDegrees = runwayHeading, speedKnots = 10)
     ```
   - Set `tailwindClearedToLimit = true`.

Both authors are **world-only**. Pilot reads new wind via `PilotInput.weatherByAerodrome` on the next decision cycle (fn-14 wiring); `derivePilotEvent`'s tailwind branch fires `TailwindLimitExceeded` independently.

### Step 4 — Three-layer pins

**Layer 1 — causal partial-order**:
- Exactly one `Report(GoingAround)` transmission. Use:
  ```
  records.filter { tx -> tx is Report && ReportEvent.GoingAround in tx.events }
  ```
  NOT `tx is Report.GoingAround` (Report is a single class; GoingAround is a `ReportEvent` leaf inside `Report.events`).
- Anchor causal ordering via `findEmittingCycleMs` mint-id walk:
  ```
  wind_shift.decisionTime <= Report(GoingAround).decisionTime <= commitment_regression.decisionTime
      < wind_return.decisionTime < ClearedToLand_recovery.decisionTime < Report(RunwayVacated).decisionTime
  ```
  Same-cycle uses `<=` on `SimTime.millis` plus mint-id sequence tiebreak (per `sim-test-pins-must-compare-against-2026-05-10` memory). Strict `<` across cycles.

**Layer 2 — sticky-witness regression**:
- Read `commitmentStageTransitions` from the trace (fn-8's extractor).
- Assert exactly one stage transition from `{LandingClearanceIssued, AwaitLandedObserved}` to `TowerArrivalStage.AwaitDownwind` via `GA-POST-CLEAR` (not `Immediate` — mirrors fn-14's pattern; the trigger is a pilot-emitted `Report(GoingAround)`, not an ATC instruction).
- Sticky witnesses (per fn-8.3): `touchedDownDuringCommitment`, `pilotReadyDuringCommitment`, `observedReportsDuringCommitment` all reset on regression.

**Layer 3 — kinematic non-event**:
- No `LandingRoll` phase / `TouchdownDetected` event between the wind-shift cycle and the wind-recovery cycle.
- Exactly one `TouchdownDetected` after the wind returns within limit.
- Aircraft x,y near the runway threshold after touchdown.
- Vacate transmission present (`tx is Report && ReportEvent.RunwayVacated in tx.events`).

### Step 5 — World-weather transitions pin

Pin `SimState.weatherByAerodrome` transitions for the active aerodrome via fn-14's `weatherTransitions(aerodromeId: AerodromeId)` extractor. Assert exactly two transitions:
1. Wind crosses past tailwind limit (10 kt headwind → 15 kt tailwind).
2. Wind returns within limit (15 kt tailwind → 10 kt headwind).

**Do NOT add a controller-belief weather slice** — weather lives at world-state, the GA is pilot-side, controller observability is not the surface under test (mirror fn-14.2's discipline).

### Step 6 — Doc cross-references (9 sites + 8 sibling test docstrings)

For each site in **Files / MODIFY (doc cross-references)**, edit per epic R12:

- `AGENTS.md` § Golden tests — append G3a-react-tailwind bullet. Update "8 tests" → "9 tests" count if present.
- `STRATEGY.md` § Runtime simulator track — note the quintuple-covered reactive-GA decision space (DA, trained, ATC-obstruction, crosswind, tailwind) + closure of the second pilot-reactive recognition axis.
- `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` § Resolution — append fn-15 closure paragraph: five-path reactive-GA coverage; **per-type doctrinal severity asymmetry** (C172 AFH advisory / B738 FCOM hard limit) surfaced in code via distinct event leaf + distinct applier function. Surface that `RegulationDatabase` does NOT carry manufacturer values (codex round-1 closure).
- `wiki/domain/aviation-world.md` — extend the `AircraftType` section with `maxTailwindKnots` field; doctrinal note on per-type severity (advisory vs hard limitation); cross-reference `maxCrosswindKnots` for the sibling pattern.
- `pilot/.../Pilot.kt` inline comment block — update "FOUR GA paths share `pilotDecide`'s fork point" → "FIVE GA paths"; enumerate the fifth path (tailwind, distinct applier `applyTailwindGoAround`).
- `pilot/.../observe/PilotEvent.kt` file-level KDoc — leaf-count "3 leaves" → "4 leaves"; add `TailwindLimitExceeded` entry with per-type severity note.
- `protocol/.../AircraftType.kt` file-level KDoc + per-leaf KDocs — `maxTailwindKnots` per-leaf source citation (C172 = POH §2 absent + FAA AFH Ch 9 advisory; B738 = FCOM §1 hard limit).
- `sim/.../testing/Fixtures.kt` LOWG provenance — add G3a-react-tailwind consumer.
- Eight sibling test class docstrings — `@see G3aPilotReactiveTailwindTest` cross-ref. Add to:
  - `sim/src/jvmTest/.../LowgGoldenTest.kt`
  - `sim/src/jvmTest/.../G1TwoAircraftCircuitsTest.kt`
  - `sim/src/jvmTest/.../G1TwoAircraftMinimalSpec.kt`
  - `sim/src/jvmTest/.../G2CrossAerodromeVfrTest.kt`
  - `sim/src/jvmTest/.../G3aPilotTrainedGoAroundTest.kt`
  - `sim/src/jvmTest/.../G3aRunwayObstructionTest.kt`
  - `sim/src/jvmTest/.../G3aRunwayObstructionContinueApproachTest.kt`
  - `sim/src/jvmTest/.../G3aPilotReactiveCrosswindTest.kt` (bidirectional sibling cross-ref).

Plus the auxiliary `~/.claude/plans/pilot-firewall.md` deferment register update (optional / non-acceptance).

### Step 7 — Verify

`./gradlew :sim:jvmTest --tests "*G3aPilotReactiveTailwindTest*"` GREEN. Then `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` — all **nine** goldens GREEN; detekt baseline unchanged. **Closes R14** at the epic level.

If the GA interrupt path regresses for an unexpected reason, root-cause (do NOT widen tolerances). Per `feedback_no_corners.md`.

## Investigation targets

- Confirm exact path of `onAfterEvent` / `findEmittingCycleMs` / `runUntilWithStateTrace` / `weatherTransitions(aerodromeId)` — fn-14.2 verified these; spot-check at task time.
- Confirm controller stage names: `TowerArrivalStage.LandingClearanceIssued`, `TowerArrivalStage.AwaitLandedObserved`, `TowerArrivalStage.AwaitDownwind`. Use exact names.
- Confirm `WeatherObservation` constructor shape — fn-14.2 authors via `WeatherObservation(wind = WindReport.Available(Wind.unsafe(...)), ...)`. Preserve the qnh + visibility fields from the prior observation.
- Identify the auxiliary deferment register entry (if `D-PASS-g3a-react-tailwind-limit` was filed by fn-14, mark closed; otherwise file new).

## Key context

- **World-only test triggers** — author wind state via `onAfterEvent`; do NOT rig pilot decisions or inject events bypassing the firewall. Mirror fn-14.2's discipline.
- **Three-layer pin pattern** — causal + sticky-witness + kinematic non-event. Pin on controller commitment stage (durable), not message logs (fragile).
- **Use exact stage names** — `LandingClearanceIssued` / `AwaitLandedObserved` → `TowerArrivalStage.AwaitDownwind`. No imprecise names.
- **Decision-cycle timestamps** — `findEmittingCycleMs` mint-id walk, not `txStart`. Same-cycle `<=`; cross-cycle strict `<`.
- **Two-transition pattern** — wind exceeds tailwind limit (15 kt tailwind), then returns to headwind (10 kt). Recovery landing in circuit 2.
- **`Wind.unsafe(...)` for fixture construction** — primary constructor is private. `Wind(...)` won't compile. **`Wind.invoke` accepts both `0` and `360` for `directionDegrees`**; the `(rawDir + 180) mod 360`-then-clamp-`0→360` normalisation in this test is for display/convention consistency, not validator-driven.
- **`tx is Report && ReportEvent.GoingAround in tx.events`** — NOT `tx is Report.GoingAround`. Report is a single class; GoingAround is a `ReportEvent` leaf inside `Report.events`.
- **No event-count pin in sim test** — that pin lives in fn-15.1 unit tests.
- **No controller behavior changes** — GA-POST-CLEAR is trigger-agnostic; fn-14 verified this on the crosswind trigger; fn-15 reuses the unchanged surface. If the sim test fails because the interrupt does not fire, the bug is in fn-15.1 wiring (the pilot's `Report(GoingAround)` transmission shape must match exactly).
- **Recovery wind direction**: returning to `runwayHeading` (10 kt headwind) MUST take the wind below `maxTailwindKnots` AND below `maxCrosswindKnots` (a headwind has zero crosswind component). Both reactive predicates fail-closed; the aircraft can recover.
- **Per-type doctrinal severity asymmetry** (codex round-1 closure): the C172 sim test models the AFH-advisory regime (POH does not publish a hard limit). The doctrinal anchor surfaced in cross-reference docs distinguishes this from the B738 FCOM hard-limit regime. Cross-reference text MUST NOT conflate the two regimes into "POH = hard limit".

## Acceptance

- [ ] **R11** — `G3aPilotReactiveTailwindTest` exists at LOWG, single C172, `CircuitTraining(outcomes = listOf(FullStop))`. World authors **two transitions**:
  1. Initial 10 kt headwind → 15 kt tailwind (direction `(runwayHeading + 180) mod 360`, normalised to `360` for display-convention consistency when result is `0` — `Wind.invoke` accepts both `0` and `360`) via post-clearance one-shot (predicate: commitment stage ∈ `{LandingClearanceIssued, AwaitLandedObserved}` AND `aircraft.phase is Final`).
  2. 15 kt tailwind → 10 kt headwind via `Report(GoingAround)`-gated one-shot once aircraft back on downwind.
  
  Three-layer pins assert: pilot transmits `Report(GoingAround)`; commitment regresses to `TowerArrivalStage.AwaitDownwind` via `GA-POST-CLEAR`; aircraft does not touch down on the GA'd approach. Recovery: exactly one `TouchdownDetected` after wind returns; vacate transmission present. Vacate-coordination closure pin (fn-8.3 R7). Time band ±15% on observed wall.
- [ ] **R12** — All 9 epic R12 repo-internal sites updated (AGENTS.md, STRATEGY.md, wiki/design-decisions/2026-04-22, wiki/domain/aviation-world.md, Pilot.kt inline comment, PilotEvent.kt KDoc, AircraftType.kt KDoc, Fixtures.kt provenance, 8 sibling test class docstrings including bidirectional cross-ref to `G3aPilotReactiveCrosswindTest`). Doc text surfaces **per-type doctrinal severity asymmetry** (C172 AFH advisory / B738 FCOM hard limit) per codex round-1 closure. Auxiliary repo-external `~/.claude/plans/pilot-firewall.md` update is best-effort, not load-bearing for acceptance.
- [ ] **R14** (epic-level closure) — Full verify GREEN: `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` exits 0. All nine golden tests GREEN. detekt baseline unchanged.

## Notes

Once shipped, the G3a-react second axis closes (POH/AFH crosswind + POH/AFH tailwind, both pilot-reactive). Sibling deferments (multi-aircraft, cross-aerodrome, gust, combined-vector, ATIS-cadence) remain in the deferments register and become candidate epics in their own right. The per-type doctrinal severity asymmetry (advisory vs hard limitation) becomes a first-class surface in the codebase — the precedent for future POH/FCOM-derived reactive triggers (density altitude, weight limits, temperature) to follow.

## Done summary
fn-15.2 Sim integration test + doc cross-references for G3a-react-tailwind shipped: new `G3aPilotReactiveTailwindTest` (ninth golden) at LOWG with single C172, two-transition world-only wind authorship (10 kt headwind → 15 kt pure tailwind, then back to headwind via post-GA recovery-Downwind report gate — strictly tighter than the fn-14.2 crosswind sibling's off-final gate per codex round-2/3 review). Three-layer pin pattern (causal partial-order + sticky-witness regression via `GA-POST-CLEAR` + kinematic non-event) + recovery touchdown pin (R11) + recovery-clearance safety pin + R7 vacate-coordination closure + ±15% time band. R12 doc cross-references updated across 9 sites (AGENTS.md, STRATEGY.md, wiki/design-decisions/2026-04-22, wiki/domain/aviation-world.md, Fixtures.kt provenance) + 8 sibling test class docstrings (bidirectional cross-ref to G3aPilotReactiveCrosswindTest). Codex impl-review SHIP after three rounds (transition-2 gate tightening + R11 touchdown pin + AGENTS count + Layer 1 tx-start documentation). All nine goldens GREEN; detekt baseline unchanged; per-type doctrinal severity asymmetry (C172 AFH-advisory tailwind vs B738 FCOM hard-limit tailwind) surfaced as a first-class modelling distinction. R11 + R12 + R14 closed at the epic level.
## Evidence
- Commits: eed7b2820bbb9676f73c2c5bbcca97e88e1a9aa7, 77710c750cb1f8a8df3b1057dd0a17ce4ed8a2c5, 767f4625f4e3eb5c9c6ce6e4f9bd5e16a23a3c2e, 6454d7301f916f29c65a5a39cbc77e489d062b9d
- Tests: ./gradlew --offline --no-daemon :sim:jvmTest --tests *G3aPilotReactiveTailwindTest* --rerun-tasks, ./gradlew --offline --no-daemon :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
- PRs: