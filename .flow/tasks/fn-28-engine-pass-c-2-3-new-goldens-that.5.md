---
satisfies: [R2-MULTI, R2a, R2b, R2c, R2d, R2e, R3, R4, R5, R7]
---

## Description

Sim golden test for the reframed multi-aircraft scenario. Two C172s at LOWG: aircraft A (`OEABC`) on final, aircraft B (`OEDEF`) on downwind. Wind shifts past A's POH crosswind limit → A declares pilot-reactive GA via existing crosswind branch → controller observes `Report(GoingAround)` → emits `ExtendDownwind` to B (per task .4). A completes go-around pattern and issues `Report(Downwind)` (pattern-rejoin per R23 lifecycle — round-8 Major 3 fix; NOT runway-vacate) → controller's `goAroundInProgressByRunway` belief clears → next sequencing instruction (`TurnBase` or `ClearedToLand`) supersedes ExtendDownwind; B resumes normal flow. Sibling scenario: same shape on the tailwind axis. Wind-shift-back recovery: standalone scenario in either axis. Three scenarios in one file. Archive flips for `D-PASS-g3a-react-multi-aircraft-crosswind` + `-tailwind`.

**Size:** M-L (test + fixture + 2 archive flips; 3 scenarios in one file)
**Files (expected):**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveMultiAircraftTest.kt` (new, ~1100-1400 LOC, exhaustive KDoc, 3 `@Test` methods)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt` — extend `LOWG_TWO_AIRCRAFT` or add `LOWG_TWO_AIRCRAFT_REACTIVE` variant; provenance entry
- `docs/deferments.md` — 2 archive flips per §8

## Approach

- **Pattern parents**: `G1ClosureDiveTest.kt` for multi-aircraft setup (OEABC + OEDEF callsigns; iterative state perturbation). `G3aPilotReactiveCrosswindTest.kt` / `G3aPilotReactiveTailwindTest.kt` for single-aircraft reactive shape. This test combines: A follows the single-aircraft reactive pattern; B is purely controller-sequenced.
- **Scenario 1 — Crosswind GA + extend-downwind**:
  - Pre: A on final, B on downwind; wind shifts past crosswind limit.
  - A's existing crosswind branch fires → A declares Report(GoingAround) → controller emits ExtendDownwind to B (per .4 foundation).
  - Three-layer pin:
    - L1 causal partial-order: `weatherTransitions` (wind shift) → A's `commitmentStageTransitions` (regression on GA) → controller emits ExtendDownwind to B in subsequent tick.
    - L2 sticky-witness regression: A's mission step shows GA transition (consistent with existing G3a-react-crosswind shape); B's mission state shows ExtendDownwind acknowledgement.
    - L3 kinematic non-event: B NEVER turns base in scenario where ExtendDownwind is active; assert absence of base-turn position-point transitions.
- **Scenario 2 — Tailwind GA + extend-downwind**: same shape, tailwind axis on A.
- **Scenario 3 — GA-recovery / belief-clear**: wind shifts past crosswind; A's GA fires + ExtendDownwind to B emitted. A completes go-around pattern and issues `Report(Downwind)` (pattern-rejoin per R23 lifecycle). Controller's `goAroundInProgressByRunway` belief clears. Next instruction (`TurnBase` to B) supersedes ExtendDownwind per `Supersession.kt`; B resumes turn-base sequence. Assert: belief clear → ExtendDownwind no longer active → B's mission step transitions to turn-base normally. **NO runway-vacate clause** (round-7 Major 4 / round-8 Major 3).
- **Inherited-gate-semantics audit**: scenarios 1+2 inherit A's reactive-GA gates from sibling crosswind/tailwind goldens; B's downwind-phase + post-extend-downwind gates are NEW. Audit comment required (per `inherited-gate-semantics-2026-05-15.md`).
- **Total-order assertion**: with both aircraft in the same tick, the simulation must resolve in deterministic order per the existing engine's `EVENT_ORDER` contract: **`(time, source, seq)` via `SimEvent.seq`** (round-8 Minor 1 fix — NOT `(tick, aircraftId, eventKind ordinal)`; that was a misstatement). Test asserts events fire in the engine's canonical order. Optional shuffled-input variant: run the scenario with reversed spawn order; assert equal final state.

## Investigation targets

**Required**:
- Task .4 outputs: controller `Report(GoingAround)` observation + ExtendDownwind emission + supersession cancel-flow
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1ClosureDiveTest.kt` — multi-aircraft setup pattern
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveCrosswindTest.kt` (823 LOC) — A-side reactive shape
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveTailwindTest.kt` (1032 LOC) — A-side reactive shape (tailwind axis)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt:28-88` — provenance + LOWG_TWO_AIRCRAFT
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Supersession.kt:11-64` — supersession table (cancel-via-TurnBase)
- `.flow/memory/knowledge/best-practices/inherited-gate-semantics-2026-05-15.md`
- `.flow/memory/bug/test-failures/inherited-sim-test-gate-semantics-may-2026-05-11.md`

**Optional**:
- Task .4's `GoAroundSequencingSpec.kt` unit test — sim-level integration should match

## Key context

- **Multi-aircraft model is asymmetric**: A is the wind-reactive declarer; B is the sequenced one. B's pilot recognition does NOT fire on wind (final-phase guard rejects downwind-phase aircraft). This is the model the deferment text actually describes — "Multiple aircraft on same runway when wind shifts" — which the GA + sequencing scenario covers.
- **Wind-shift-back scenario** is about CONTROLLER state, not pilot. A's GA completes; wind has cleared; controller's TurnBase to B supersedes ExtendDownwind via existing Supersession.kt rules. Test asserts the supersession path.
- **Archive flip text** for both deferments must cite this task ID + the specific scenario covering the axis. `Closed by` notes the reframing: "GA + ATC sequencing, not simultaneous pilot recognition (per plan-review-round-1)".
- **File size**: largest of the 4 goldens (3 scenarios + 2 axes). Budget ~1400 LOC.
- **Snapshot bloat avoidance** (practice-scout #1): assert on targeted projections (responsibilityTransitions, commitmentStageTransitions, transmissionsOf), NOT full state-tree comparison.

## Acceptance

- [ ] `G3aPilotReactiveMultiAircraftTest.kt` lands with exhaustive KDoc + 3 `@Test` methods (crosswind GA+sequencing, tailwind GA+sequencing, wind-shift-back recovery)
- [ ] Three-layer pin pattern per scenario
- [ ] Total-order assertion uses engine's existing `EVENT_ORDER` (`(time, source, seq)` via `SimEvent.seq`); optional shuffled-input variant asserts equal final state
- [ ] Scenarios 1+2: A's existing reactive-GA fires (unchanged from G3a-react-crosswind/tailwind); B receives ExtendDownwind from controller; B does NOT have a GA decision
- [ ] Scenario 3: A completes GA pattern + `Report(Downwind)` pattern-rejoin clears `goAroundInProgressByRunway` belief; controller emits `TurnBase` to B in the same cycle the belief clears (per .4's concrete cancel-output contract, round-10 Major 2); B resumes turn-base. NO runway-vacate (round-8 Major 3)
- [ ] **Scenario setup precondition**: A's ARR-LAND commitment exists in `BeliefState` BEFORE the wind shift triggers A's GA (round-10 Major 3 — ensures `resolveGoAroundRunway` succeeds via the primary commitment path, not the fallback). Test setup explicitly observes the commitment in the trace before triggering the wind perturbation.
- [ ] `Fixtures.kt` extended with multi-aircraft reactive variant + provenance entry
- [ ] `docs/deferments.md` archive flips for `D-PASS-g3a-react-multi-aircraft-crosswind` + `-tailwind` per §8; Closed-by cites the reframed model
- [ ] Targeted: `./gradlew :sim:jvmTest --tests "*G3aPilotReactiveMultiAircraftTest*" --offline --no-daemon` GREEN
- [ ] Full verify GREEN; 11 sim goldens (9 existing + DA + multi) GREEN
- [ ] Inherited-gate-semantics audit comments present (A's gates inherited; B's downwind gates new)

## Done summary

_(filled by `flowctl done` at task close)_

## Evidence

_(filled by `flowctl done` at task close)_
