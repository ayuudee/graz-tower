---
satisfies: [R2]
---

## Description

Resolve 2 pilot negative-case test failures. **Reviewer Round 1 surfaced that the original diagnosis was wrong** — `isDensityAltitudeDeclineEligible` ALREADY rejects the airborne MissionSteps the test iterates (FLY_DEPARTURE, FLY_DOWNWIND, FLY_FINAL, AWAIT_LANDING_CLEARANCE). So the recognition firing in these negative tests is NOT density-altitude decline. The likely culprit is the **earlier `deriveDecisionAltitudeEvent` branch** in `derivePilotEvent` — it fires for steps like `AWAIT_LANDING_CLEARANCE` / `FLY_FINAL` / `FLY_BASE` / `REPORT_FINAL` / `REPORT_BASE` when aircraft is at low altitude with no clearance.

Failing tests:
1. `PilotEventDensityAltitudeTest.kt:187` — `"does NOT fire on airborne steps — mission-shape guard rejects"`. Iterates airborne MissionSteps with default `aircraft()` helper (likely altitudeM=0 / ground-phase, no clearance) → `deriveDecisionAltitudeEvent` fires for the matching steps → top-level `derivePilotEvent` returns non-null → assertion fails.
2. `PilotEventAbortTakeoffTest.kt:248` — iterates `MissionStep.entries`, skips eligible rows, sets `engineRunning=false + speedMps=belowRotationSpeed + phase=PilotPhase.TakeoffRoll`. Same class of bug: the earlier DA-without-clearance branch can fire for some non-abort-eligible steps before abort recognition is reached.

**Size:** M (2 test files; investigation + fix; both are test-fixture issues per reviewer analysis)
**Files (expected):**
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEventDensityAltitudeTest.kt` (lines 175-200) — refine fixture so earlier branches don't fire
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEventAbortTakeoffTest.kt` (lines 237-260) — same
- NO production-code change expected (reviewer's analysis: gates are correct; tests are mis-fixtured)

## Approach

**Step 1 — identify the actual fired event class.** For each failing test row, print the returned `PilotEvent` (or `null`) before asserting. Confirm whether it's `DecisionAltitudeWithoutClearance`, `DensityAltitudeDecline`, `AbortTakeoff`, or another leaf. The reviewer's prediction: `DecisionAltitudeWithoutClearance` for both.

**Step 2 — fix path A (test fixture): if reviewer prediction holds.**
- DA test L187: neutralize the earlier DA-without-clearance branch for these rows. Options:
  - (a) Set `altitudeM > DECISION_ALTITUDE_M` (e.g., 1000m) so `deriveDecisionAltitudeEvent`'s altitude predicate fails.
  - (b) Set `mission.activeRunway \!= null` AND `mission.hasClearance` so the "no clearance" predicate fails.
  - (c) Split the test into mission-shape rows that cannot trigger the earlier branch (e.g., test airborne mission shapes ONLY with mission shapes excluded from the on-approach set: FLY_DEPARTURE, FLY_DOWNWIND).
- Abort test L248: same options. Likely path (a) — set altitudeM > DECISION_ALTITUDE_M when constructing the negative-row aircraft.
- KDoc on the test pins the cross-branch dependency so a future contract drift surfaces.

**Step 3 — fix path B (production gate): only if Step 1 returns an actual unexpected event type.**
- E.g., if abort recognition is genuinely firing on a non-eligible MissionStep, tighten `isAbortTakeoffEligible` in `PilotMission.kt`.
- If DA-decline recognition is genuinely firing on airborne steps, audit `isDensityAltitudeDeclineEligible`.
- Update the test's KDoc to capture the contract change.

**Decision discipline**: prefer fixture fix (Step 2). Only consider gate fix (Step 3) if Step 1's evidence shows the gate is wrong.

## Investigation targets

**Required**:
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt:580-630` — `derivePilotEvent` branch chain
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt:817-870` — `deriveDecisionAltitudeEvent` gate (on-approach + altitudeM + no-clearance)
- `pilot/src/commonTest/.../PilotEventDensityAltitudeTest.kt:175-200` — failing rows
- `pilot/src/commonTest/.../PilotEventAbortTakeoffTest.kt:237-260` — failing rows
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt` — `isDensityAltitudeDeclineEligible` + `isAbortTakeoffEligible`

**Optional**:
- `pilot/src/commonTest/.../PilotEventDensityAltitudeTest.kt:195-220` — sibling phase-guard test (sets `phase = PilotPhase.Final` — pattern reference)

## Key context

- **Reviewer's diagnosis trumps original spec**: the earlier DA-without-clearance branch is the likely culprit, NOT a DA-decline / abort gate hole.
- **Branch order in `derivePilotEvent`**: DA-without-clearance → DA-decline → AbortTakeoff → Tailwind → Crosswind. Negative-case tests for ANY leaf must neutralize ALL earlier leaves' triggers.
- **Test-side fix is preferred** (minimum production-code risk). Document via KDoc.
- **No `@Suppress` / `@Disabled`**.
- Pre-existing-failure-register: if a third pilot test reveals during full-verify (R4), it likely shares the same root cause — fold into this task.

## Acceptance

- [ ] Step 1 evidence captured in `## Resolved during implementation`: actual `PilotEvent` returned by each failing row's `derivePilotEvent` call
- [ ] Per Step 2 (or Step 3 if warranted by Step 1 evidence): both failing tests pass
- [ ] Test KDocs document the cross-branch dependency (so future contract drift surfaces)
- [ ] If gate fix: positive-case tests for the touched branch still pass; no regression in fn-28.2 / fn-28.9 goldens
- [ ] No `@Suppress` / `@Disabled` added
- [ ] `gradle :pilot:jvmTest --tests "*PilotEvent*Test*" --offline --no-daemon` GREEN
- [ ] **Commit the changes** (per plan-review R2 Major 2): explicit `git add` of the touched files (both pilot test files + conditional `PilotMission.kt` / `PilotEvent.kt` if Step 3 gate fix taken) → `git commit -m "fn-32.3: green out 2 pilot negative-case tests"`. NO `git add -A`. Commit BEFORE `flowctl done` so the close-out diff scope is clean.

## Review considerations

- **FP / type safety**: minimal — at most a guard tightening in `PilotMission.kt` (gate fix path B); no sealed-hierarchy changes
- **Test architecture**: branch-order cross-dependency made explicit via test KDoc + assertion of returned event class
- **Impact**: test-only (Step 2 path); minimal production diff (Step 3 path, only if necessary)
- **Operational ATC correctness**: doctrine preserved either way — the branch-precedence chain in `derivePilotEvent` is unchanged

## Done summary

_(filled by `flowctl done` at task close)_

## Evidence

_(filled by `flowctl done` at task close)_
