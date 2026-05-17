---
satisfies: [R1]
---

## Description

Green the 3 failing `GoAroundSequencingSpec` tests. Reviewer Round 1 surfaced two refinements to the original diagnosis:

1. **The "round-trip" test (L383) ALREADY has both AC_A + AC_B in `previous.commitments`, `view.responsibilities`, and `view.aircraft`**. So "add AC_A" is not the fix for that test — it's already there. The root cause for that test is something else (likely the same `SeparationConcernAbove` empty-assessment block, but the cause of empty-assessments is geometry / arrival-sequence state, not aircraft count).
2. **PT_FINAL ↔ PT_DOWNWIND is only ~1000m (~0.54 NM)**. Typical ATC separation comfort thresholds are 3+ NM (IFR) or 1500m+ (VFR circuit). Adding AC_A at PT_FINAL with AC_B at PT_DOWNWIND may produce an assessment but it likely won't be `COMFORTABLE` — it might be `MONITORING` or `INTERVENTION`. The `Not(SeparationConcernAbove(INTERVENTION))` guard would still fail.

Failing tests:
- `ARR-EXTEND-FOR-GA fires from same-cycle GoAroundDetected fold (round-trip)` (L383) — note this test ALREADY has both aircraft
- `ARR-TURN-BASE fires once GA belief clears via pattern-rejoin (concrete cancel-output)` (L447)
- `ARR-TURN-BASE fires once GA belief clears via 60s timeout` (L476)

**Size:** S-M (1 test file; assertion-driven test refinement; no production code change)
**Files:**
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/GoAroundSequencingSpec.kt` — refine the 3 failing tests' fixtures; assert observed separation assessment state

## Approach

**Step 1 — diagnose actual state per test:**
- For each failing test, run with debug-println of `result.updatedBeliefs.separationAssessments` + `result.updatedBeliefs.arrivalSequence` + `result.trace.skippedActions[*].ruleTraces`.
- Identify per-test: (a) is there an assessment at all? (b) what severity? (c) which guard component fails?

**Step 2 — pick fix per finding:**
- **If assessments are empty**: arrival-sequence has <2 entries. Cause: missing arrival sequence seed in fixture, OR `updateArrivalSequence` doesn't populate for these stages. Seed `arrivalSequence.slots` explicitly in `previous` beliefs OR ensure AC_A's commitment is at a stage that updateArrivalSequence accepts.
- **If assessments are non-empty but severity ≥ INTERVENTION**: geometry too tight. **AC_A is the leader** (closer to threshold, on PT_FINAL — the GA-going aircraft, since GA happens on final); **AC_B is the trailing follower** (on PT_DOWNWIND). `SeparationEngine.assessPair` treats the lower-distance arrival-sequence slot as leader and computes `followerDist - leaderDist`. To get a `COMFORTABLE` margin without inverting the leader/follower semantics: **extend `TEST_INDEX` with a further-out downwind point** (e.g., `PT_LONG_DOWNWIND` at ~3000-5000m from threshold) AND move AC_B to that point — keeping AC_A as the leader on PT_FINAL. AC_B must still be on a `LegName.DOWNWIND` point so `OnCircuitLeg(DOWNWIND)` in `ARR-TURN-BASE`'s guard still passes. Alternative: seed `arrivalSequence.slots` explicitly with the chosen distances (AC_A < AC_B); confirm `assessSeparation` doesn't overwrite if the underlying observation positions are consistent. **NEVER move AC_A further out than AC_B** — that flips the scenario from "trailing aircraft sequenced behind GA" to "GA aircraft behind trailing aircraft" which is meaningless.

**Step 3 — assertion contract:**
- Add a pre-action assertion: `result.updatedBeliefs.separationAssessments.any { it concerns AC_B && it.concern == COMFORTABLE }`. Surface this as a positive R1 precondition for ARR-TURN-BASE firing.

**Step 4 — verify positive path:**
- ARR-TURN-BASE fires with correct ruleId.
- No regression in the 14 currently-green tests.

## Investigation targets

**Required**:
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/GoAroundSequencingSpec.kt:329-367` — existing 2-aircraft fixture pattern
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/GoAroundSequencingSpec.kt:543-564` — companion object (AC_A, AC_B, TEST_INDEX, points)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt:923-935` — `SeparationConcernAbove` fail-conservative (read-only)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/SeparationEngine.kt:25-47` — `assessSeparation` n<2 empty + comfort calculation (read-only)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/Controller.kt:147-157` — `updateArrivalSequence` + `assessSeparation` in the cycle

**Optional**:
- `controller/src/commonTest/.../CertifiedControllerActionSpec.kt` — sibling multi-aircraft pattern

## Key context

- **NO controller production code change.** Fixture-side fix only. The `SeparationConcernAbove` fail-conservative semantics is doctrinally defensible (1-aircraft = can't assess); changing it is out of scope.
- **Round-trip test already has 2 aircraft** — diagnosis-first per Step 1; do not blindly add AC_A.
- **Geometry matters + leader/follower semantics**: 1000m ≈ 0.54 NM is probably too tight. AC_A = leader at PT_FINAL (closer to threshold), AC_B = follower (further out) — never invert. Move AC_B further out via a new `PT_LONG_DOWNWIND` point if needed; confirm via Step 1's debug output before adopting.
- **arrivalSequence may be empty** even with 2 aircraft if the seed / reconcile didn't populate slots. Seed `previous.arrivalSequence` directly if needed.
- **No `@Suppress` / `@Disabled`** to silence failures.

## Acceptance

- [ ] 3 failing tests pass: round-trip ARR-EXTEND-FOR-GA, pattern-rejoin ARR-TURN-BASE, 60s-timeout ARR-TURN-BASE
- [ ] Each test asserts the EXPECTED separation-assessment state (positive precondition for ARR-TURN-BASE): `result.updatedBeliefs.separationAssessments` contains a COMFORTABLE entry for AC_B (or shapes equivalent per Step 1 diagnosis)
- [ ] All 14 currently-green tests in `GoAroundSequencingSpec` STILL green
- [ ] No change to controller production code (`TowerArrival.kt`, `Guard.kt`, `Observe.kt`, `Controller.kt`, `SeparationEngine.kt`)
- [ ] `## Resolved during implementation` records the per-test Step 1 diagnosis findings (which guard failed; what assessment state existed; what fixture seed was the fix)
- [ ] `gradle :controller:jvmTest --tests "*GoAroundSequencingSpec*" --offline --no-daemon` GREEN
- [ ] **Commit the test file change** (per plan-review R2 Major 2): explicit `git add controller/src/commonTest/.../GoAroundSequencingSpec.kt` (and any new fixture-helper file if added) → `git commit -m "fn-32.2: fixture refactor — green out 3 GoAroundSequencingSpec tests"`. Do NOT use `git add -A`. Commit BEFORE invoking `flowctl done` so the close-out hook surfaces the right diff scope.

## Review considerations

- **FP / type safety**: test-only changes; no Arrow `Either` / sealed-hierarchy edits
- **Test architecture**: assertion-first contract (assess the observed state, then assert the rule output). Surfaces actual guard chain to the test, not just the final outcome.
- **Impact**: scoped to one test file
- **Operational ATC correctness**: N/A — fixture refinement, not behavior change. Doctrine of `SeparationConcernAbove` empty = conservative is preserved.

## Done summary

_(filled by `flowctl done` at task close)_

## Evidence

_(filled by `flowctl done` at task close)_
