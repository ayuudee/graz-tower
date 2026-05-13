---
satisfies: [R12, R13, R15]
---

# fn-14.2 — Sim integration test + doc cross-references for G3a-react

## Description

Sim integration test (`G3aPilotReactiveCrosswindTest`) at LOWG + cross-reference doc updates per epic R13. Closes the G3a trilogy (G3a-trained / G3a-obstruction-ATC / G3a-continue / G3a-react). Covers R12 (sim test), R13 (10 doc sites + 7 sibling test class docstrings), and R15 (epic-level full verify).

## Problem

fn-14.1 lays every primitive but only proves them in pilot-side unit tests. The epic's golden-path acceptance is a sim test at LOWG where the world authors a wind shift on final, the pilot recognises the POH crosswind exceedance, transmits `Report(GoingAround)`, executes the GA, re-enters circuit, and lands when wind returns within limits. This task writes that integration test and updates every doctrinal cross-reference site so the new scenario is visible in the project's documentation surface.

Per codex plan review issue #8: this task does **not** add event-tracing instrumentation. Event-count pins live in fn-14.1 pilot unit tests; sim test asserts only externally observable behavior (transmissions, commitment stage regression, no touchdown, recovery landing).

## Files (read or modify)

- **READ**
  - `sim/src/commonTest/kotlin/.../RunUntil.kt` (or wherever `onAfterEvent` lives — grep `onAfterEvent` to locate; codex flagged the path as not-found in initial review).
  - fn-12.3's test class (likely `G3aRunwayObstructionTest.kt`) — two-transition world-only test trigger pattern.
  - fn-11.3's test class (likely `G3aPilotTrainedGoAroundTest.kt`) — single-aircraft LOWG circuit-training setup.
  - `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimState.kt:68` — `weatherByAerodrome`.
  - `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt:378-392` — GA-PRE-CLEAR / GA-POST-CLEAR (read only).
  - `controller/src/.../TowerArrivalStage.kt` (or wherever stages live) — confirm stage names: `LandingClearanceIssued`, `AwaitLandedObserved`, `AwaitDownwind`.

- **MODIFY (test file)**
  - `sim/src/jvmTest/kotlin/.../G3aPilotReactiveCrosswindTest.kt` — new test class. **JVM-only test** (mirrors fn-12.3 `G3aRunwayObstructionTest`, fn-13.2 `G3aRunwayObstructionContinueApproachTest` location). May read shared harness helpers from `commonTest/`, but the class itself lives under `jvmTest/`. One test method, LOWG, single C172. Two world-state transitions via `onAfterEvent` (see Approach).

- **MODIFY (doc cross-references per epic R13 — 10 sites)**
  - `AGENTS.md` § Golden tests — add G3a-react bullet (8 tests total).
  - `STRATEGY.md` § Runtime simulator track — quadruple-covered approach decision space + complete G3a trilogy note.
  - `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` § Resolution — add fn-14 closure paragraph for the four-path reactive-GA coverage.
  - `wiki/design-decisions/2026-04-15-controller-architecture.md` — note four-path reactive-GA surface (no controller behavior change; pilot-side; compile-impact for `WindReport` relocation only).
  - `wiki/domain/aviation-world.md` — add `AircraftType` section documenting `maxCrosswindKnots` POH-derived field.
  - `pilot/.../Pilot.kt` inline comment block at lines 99-150 — update three-path enumeration to four.
  - `pilot/.../observe/PilotEvent.kt` file-level KDoc — update "Current leaf set" count to 3 leaves; add `CrosswindLimitExceeded` entry with POH citation.
  - `protocol/.../AircraftType.kt` file-level KDoc + per-leaf KDocs — `maxCrosswindKnots` POH source citation.
  - `sim/.../testing/Fixtures.kt` LOWG provenance — add G3a-react consumer.
  - Sibling test class docstrings (LowgGoldenTest, G1TwoAircraftCircuitsTest, G1TwoAircraftMinimalSpec, G2CrossAerodromeVfrTest, G3aPilotTrainedGoAroundTest, G3aRunwayObstructionTest, G3aRunwayObstructionContinueApproachTest) — `@see G3aPilotReactiveCrosswindTest` cross-ref.

- **MODIFY (auxiliary — optional, repo-external)**
  - `~/.claude/plans/pilot-firewall.md` — Deferments register: mark `(legacy-register-anchor)` (or equivalent) as closed by fn-14 with commit SHA. **Optional / non-acceptance**: this file lives outside the repo and cannot be validated from CI. Treat as best-effort housekeeping; the load-bearing closure record is the flow epic + git commit history.

## Approach (numbered Steps)

### Step 1 — Locate test infrastructure files

`grep -rn "onAfterEvent\|findEmittingCycleMs" sim/src/commonTest` to confirm exact paths. Read fn-12.3's two-transition test trigger pattern. Read fn-11.3's circuit-training fixture. Confirm C172 `maxCrosswindKnots = 15` from fn-14.1.

### Step 2 — Build the LOWG fixture

Single C172 aircraft. `HighLevelGoal.CircuitTraining(outcomes = listOf(FullStop))` — one circuit; the test extends to circuit 2 only because the GA forces re-entry. Initial weather: wind 10 kt from runway heading (zero crosswind component). `ClearedToLand` issued before the wind shift (exercises GA-POST-CLEAR).

### Step 3 — Author the wind shift via `onAfterEvent` (post-clearance pattern)

**Trigger predicate must accommodate post-clearance mission state.** After `handleLandingClearance` fires, `FLY_FINAL` and `AWAIT_LANDING_CLEARANCE` are marked complete; current step is typically `LAND`. Trigger on **either** of two equivalent surfaces (whichever is cleanest to read in the sim test):
- **Controller commitment stage** = `LandingClearanceIssued` OR `AwaitLandedObserved` AND `aircraft.phase is Final` (durable; reads commitment state)
- **Mission step** ∈ `{FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}` AND `aircraft.phase is Final` (mirror the pilot-side recognition's step-set)

Two `onAfterEvent` registrations:

1. **Wind-exceeds-limit (one-shot guard `var crosswindAuthored = false`):**
   - When predicate above holds AND `!crosswindAuthored`
   - Mutate `simState.weatherByAerodrome[LOWG]` to a `WeatherObservation` whose `wind` is `WindReport.Available(Wind.unsafe(directionDegrees = normalisedDir, speedKnots = 20))`. **Use `Wind.unsafe(...)`** — `Wind`'s primary constructor is private; the `operator fun invoke(...)` companion returns `Either<String, Wind>` and `.unsafe(...)` is the convention for compile-time-known-valid fixture constants (mirrors `Knots.unsafe` pattern). `normalisedDir` = `((runwayHeading + 90) % 360).let { if (it == 0) 360 else it }` — `% 360` normalizes for runway headings ≥ 270° (e.g. runway 35C at 350°M + 90 would otherwise overflow to 440°); map result `0` back to `360` per aviation-display convention if `Wind` rejects `0`. 20 kt direct crosswind exceeds C172's 15-kt limit.
   - Set `crosswindAuthored = true`

2. **Wind-returns-within-limit (one-shot guard `var crosswindClearedToLimit = false`):**
   - When `Report(GoingAround)` has been transmitted AND aircraft back on downwind (circuit 2 setup) AND `!crosswindClearedToLimit`
   - Reset weather to initial 10-kt headwind
   - Set `crosswindClearedToLimit = true`

Both authors are **world-only**. Pilot reads new wind via `PilotInput.weatherByAerodrome` on the next decision cycle (fn-14.1 wiring) and `derivePilotEvent` fires `CrosswindLimitExceeded` independently.

### Step 4 — Three-layer pins

- **Causal partial-order:** `pilotTransmissions.count { tx -> tx is Report && ReportEvent.GoingAround in tx.events } == 1` between the wind-shift cycle and the wind-recovery cycle. (Note: `GoingAround` is a `ReportEvent` inside `Report.events`, NOT a `Report` subtype.) Anchor "between" via `findEmittingCycleMs` mint-id walk. **Use `<=` on `SimTime.millis` plus sequence-number tiebreak** for same-decision-cycle events — strict `<` on millis can be flaky when the world mutation and pilot response land on the same tick. Where ordering matters within a cycle, assert via mint-id sequence rather than wall-clock strict ordering.
- **Sticky-witness regression (use exact stage names):** controller's commitment regresses from `LandingClearanceIssued` (or `AwaitLandedObserved` depending on stage at wind-shift time) to `TowerArrivalStage.AwaitDownwind` via the `GA-POST-CLEAR` interrupt. Read commitment stage, not message log. Sticky witnesses (per fn-8.3): `touchedDownDuringCommitment`, `pilotReadyDuringCommitment`, `observedReportsDuringCommitment` all reset on regression.
- **Kinematic non-event:** no `LandingRoll` phase / `TouchdownDetected` event between the wind-shift cycle and the wind-recovery cycle.
- **Recovery:** exactly one `TouchdownDetected` after the wind returns within limit; aircraft x,y near the runway threshold; vacate transmission present (`tx is Report && ReportEvent.RunwayVacated in tx.events` or whatever the actual `ReportEvent` leaf name is — verify at task time).

### Step 5 — World-weather transitions pin (no controller belief slice)

Pin on `SimState.weatherByAerodrome` transitions by aerodrome — this is the world-truth surface. **Do NOT add a controller-belief weather slice** — weather lives at `ControllerView.weather` / `SimState.weatherByAerodrome` already, not as a `BeliefState.weatherTransitions` projection (unlike runway obstructions in fn-12, which DO have a belief-state projection because controller reaction is belief-gated). The crosswind GA is pilot-side; weather flows pilot ← `SimState.weatherByAerodrome` via `PilotWiring`. No controller observability scope expansion is required.

If `SimTraceQueries.kt` lacks a world-state weather extractor, add `weatherTransitions(aerodromeId: AerodromeId)` (aerodrome-keyed only, no controllerId). Assert exactly two transitions: (1) wind crosses past limit (triggers GA), (2) wind returns within limits (enables recovery landing).

### Step 6 — Doc cross-references (10 epic R13 sites)

For each site listed in Files / MODIFY (doc cross-references) above, edit the file:

- `AGENTS.md`, `STRATEGY.md` — surface the eighth golden; mention four-path reactive-GA coverage.
- `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` — add Resolution paragraph for fn-14.
- `wiki/design-decisions/2026-04-15-controller-architecture.md` — note four-path reactive-GA surface (pilot-side novelty; controller compile-impact only).
- `wiki/domain/aviation-world.md` — new `AircraftType` section with POH-derived `maxCrosswindKnots`.
- `pilot/.../Pilot.kt:99-150` inline comment — three-path → four-path enumeration.
- `pilot/.../observe/PilotEvent.kt` file-level KDoc — leaf-count update + new entry.
- `protocol/.../AircraftType.kt` file + leaf KDocs — `maxCrosswindKnots` POH source.
- `sim/.../testing/Fixtures.kt` LOWG provenance — add G3a-react consumer.
- Seven sibling test class docstrings — `@see G3aPilotReactiveCrosswindTest`.

Plus the auxiliary `~/.claude/plans/pilot-firewall.md` deferment register entry flip.

### Step 7 — Verify

`./gradlew :sim:jvmTest --tests "*G3aPilotReactiveCrosswindTest*"` green. Then `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` — all eight goldens green; detekt baseline unchanged. **Closes R15** at the epic level.

If the GA interrupt path regresses for an unexpected reason, root-cause (do NOT widen tolerances).

## Investigation targets

- Confirm exact path of `onAfterEvent` / `findEmittingCycleMs`. Scout reported `RunUntil.kt` path but codex flagged not-found.
- Confirm fn-12.3's test class name and copy its two-transition structure.
- Confirm fn-11.3's LOWG fixture pattern.
- Confirm controller stage names: `LandingClearanceIssued`, `AwaitLandedObserved`, `TowerArrivalStage.AwaitDownwind`.
- Identify the auxiliary deferment register entry (likely `(legacy-register-anchor)` per fn-11 deferments).

## Key context

- **World-only test triggers** — author wind state via `onAfterEvent`; do NOT rig pilot decisions or inject events bypassing the firewall.
- **Three-layer pin pattern** — causal + sticky-witness + kinematic non-event. Pin on controller commitment stage (durable), not message logs (fragile).
- **Use exact stage names** — `LandingClearanceIssued` / `AwaitLandedObserved` → `TowerArrivalStage.AwaitDownwind`. Do NOT use imprecise names like "ClearedToLand → Inbound".
- **Decision-cycle timestamps** — `findEmittingCycleMs` mint-id walk, not `txStart`.
- **Two-transition pattern** — wind exceeds limit, then returns. Recovery landing in circuit 2.
- **Trigger predicate must accommodate `LAND` step** — post-`ClearedToLand` the current step is typically `LAND`; or pin on commitment stage instead.
- **No event-count pin in sim test** — that pin lives in fn-14.1 unit tests.
- **No controller behavior changes** — GA-POST-CLEAR is trigger-agnostic. If the sim test fails because the interrupt does not fire, the bug is in fn-14.1 wiring.

## Acceptance

- [ ] **R12** — `G3aPilotReactiveCrosswindTest` exists at LOWG, single C172, `CircuitTraining(outcomes = listOf(FullStop))`. World authors wind shift past 15-kt limit (predicate accommodates post-clearance `LAND` step OR commitment stage `LandingClearanceIssued`/`AwaitLandedObserved`); pilot transmits `Report(GoingAround)`; commitment regresses to `TowerArrivalStage.AwaitDownwind` via GA-POST-CLEAR; aircraft does not touch down. World returns wind to within limit before circuit 2 final; aircraft lands on circuit 2 with vacate transmission.
- [ ] **R13** — All 10 epic R13 repo-internal sites updated (AGENTS.md, STRATEGY.md, two wiki design-decisions docs, wiki/domain/aviation-world.md, Pilot.kt inline comment, PilotEvent.kt KDoc, AircraftType.kt KDoc, Fixtures.kt provenance, seven sibling test class docstrings). Auxiliary repo-external `~/.claude/plans/pilot-firewall.md` flip is best-effort, not load-bearing for acceptance.
- [ ] **R15** (epic-level closure) — Full verify green: `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` exits 0. All eight golden tests (G0 / G1 / G1-min / G2 / G3a-trained / G3a-obstruction / G3a-continue / G3a-react) GREEN. detekt baseline unchanged.

## Notes

Once shipped, the G3a trilogy + react closes (G3a-trained / G3a-obstruction-ATC / G3a-continue / G3a-react). Sibling deferments (multi-aircraft, cross-aerodrome G3b-react, tailwind/gust variants, ATIS-cadence sensing) remain in the deferments register and become candidate epics in their own right.

## Done summary
Ships G3a-react closure (4th reactive-GA path, closing the G3a trilogy): new sim integration test `G3aPilotReactiveCrosswindTest` at LOWG (single C172, two-transition world-weather authorship: 20 kt direct crosswind then return to 10 kt headwind once `Report(GoingAround)` transmitted + aircraft off final) with three-layer pins (causal partial-order, `GA-POST-CLEAR` sticky-witness regression, kinematic non-event) + aerodrome-keyed `weatherTransitions` extractor + recovery + R7 vacate-coordination closure. All 10 epic R13 doc cross-reference sites + 7 sibling test class docstrings updated. R15 full-verify GREEN with all eight golden tests passing; detekt baseline unchanged. First-GREEN wall ~1333s, ±15% band locked. Codex impl-review SHIP on round 2 (round 1 NEEDS_WORK was a scoped-diff visibility issue on R13 doc sites first authored in fn-14.1 — addressed inline by sharpening file KDocs with the new sim-test anchor; memory entry captured).
## Evidence
- Commits: 8468fa401d20f9b81b8c80c2e58c34b75de10f70, cb96c27d4dbf237a8075afa56a0b33b1b7675e2e
- Tests: ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt, ./gradlew :sim:jvmTest --tests 'xyz.easiersaid.twr.sim.G3aPilotReactiveCrosswindTest'
- PRs: