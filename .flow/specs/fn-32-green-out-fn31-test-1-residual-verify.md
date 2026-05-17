# fn-32 — Green out FN31-TEST-1 residual verify-pass failures

## Conversation Evidence

Verbatim user turns from the 2026-05-17 session that produced this capture:

> "Let's get ./gradlew working. Why does it not currently work for you? It must be the sandbox." [user, 2026-05-17]
> "But it should all be configured in nix." [user, 2026-05-17]
> "I agree. Re (2) check .plan item FN31-TEST-1" [user, 2026-05-17]

(Earlier user direction in the same session: "Continue with the planning. Get it all squared away, reviewed, etc, then start on impl" — established the verify-then-push intent.)

Load-bearing predecessor doc: `.plan` FN31-TEST-1 entry (updated this session with the full root-cause analysis + 2026-05-17 verify-pass progress).

## Goal & Context [user / paraphrase]

Close the 3 remaining verify-pass test categories surfaced by today's gradle run so the engine-pass branch (45 commits ahead of `origin/main`) is green and pushable. The branch carries fn-26 (engine-pass A — step-function property tests), fn-28 (engine-pass C — 4 new sim goldens + foundations), and assorted memory captures. Without these fixes the branch is functionally complete but cannot be merged without test failures.

The 2026-05-17 verify-pass was the first real gradle run against the fn-28 work — workers had previously been blocked by no-JDK in their sandbox environments and SHIPPED tasks via codex static review of the scoped diff. Today's verify (working `./gradlew` via the nix-shell formula in commit `d32b8b8`) found 5 issue categories; 2 are fixed inline this session, 3 remain.

## Architecture & Data Models [paraphrase]

Three independent surfaces, no shared dependencies between them:

### (1) `GoAroundSequencingSpec` test fixture (controller commonTest)

3 failing tests:
- `ARR-EXTEND-FOR-GA fires from same-cycle GoAroundDetected fold (round-trip)`
- `ARR-TURN-BASE fires once GA belief clears via pattern-rejoin (concrete cancel-output)`
- `ARR-TURN-BASE fires once GA belief clears via 60s timeout`

Initial hypothesis (verified via debug-println in this session, see `.plan` FN31-TEST-1): `Guard.SeparationConcernAbove(INTERVENTION)` is fail-conservative — returns `true` when `ctx.beliefs.separationAssessments` is empty (commented "no assessments = assume concern, be conservative"). `assessSeparation` at `controller/.../assess/SeparationEngine.kt:30` returns empty for `arrivals.size < 2`.

**Plan-review R1 surfaced two refinements**: (a) the round-trip test (L383) ALREADY has both AC_A + AC_B; "add AC_A" doesn't fix that test — investigate why arrivalSequence has < 2 slots, OR what guard component fails. (b) PT_FINAL ↔ PT_DOWNWIND is ~1000m ≈ 0.54 NM — likely NOT `COMFORTABLE` per the SeparationEngine's comfort threshold. Even with 2 aircraft, the assessment may be `INTERVENTION`+, still blocking `ARR-TURN-BASE`.

**Fix shape (per task .2 Step 1)**: diagnose actual state per failing test before refining the fixture. For each failing test, capture `separationAssessments` + `arrivalSequence.slots` + the per-guard `ruleTraces` post `controllerDecide`. Then refine geometry while preserving leader/follower semantics — **AC_A is leader at PT_FINAL** (the GA-going aircraft, closer to threshold); **AC_B is follower** on a downwind point. To produce a `COMFORTABLE` margin without inverting: extend `TEST_INDEX` with a further-out downwind point (e.g., `PT_LONG_DOWNWIND` ~3000-5000m from threshold) and move AC_B there. Never move AC_A further out than AC_B. Assert positive precondition: `result.updatedBeliefs.separationAssessments.any { concerns AC_B && concern == COMFORTABLE }` BEFORE asserting rule output.

### (2) Pilot negative-case test failures (pilot commonTest)

Two assertions:
- `PilotEventAbortTakeoffTest.kt:248` — assertion-failed error.
- `PilotEventDensityAltitudeTest.kt:187` — test name `"does NOT fire on airborne steps — mission-shape guard rejects"`; the top-level `derivePilotEvent` returns non-null when the test expects null.

**Plan-review R1 corrected the diagnosis**: `isDensityAltitudeDeclineEligible` ALREADY rejects FLY_DEPARTURE / FLY_DOWNWIND / FLY_FINAL / AWAIT_LANDING_CLEARANCE. The recognition firing is NOT DA-decline — it's the **earlier `deriveDecisionAltitudeEvent` branch** in `derivePilotEvent`, which fires for on-approach steps (AWAIT_LANDING_CLEARANCE / FLY_FINAL / FLY_BASE / REPORT_FINAL / REPORT_BASE) with low altitude + no clearance. The test uses `aircraft = aircraft()` (default ground-phase / altitudeM=0 / no clearance), so the earlier DA-without-clearance branch fires for the on-approach airborne steps.

**Fix path (per task .3 Step 1)**: identify the actual `PilotEvent` returned. If `DecisionAltitudeWithoutClearance`, refine the test fixture (raise altitude > DECISION_ALTITUDE_M, OR set `mission.hasClearance`, OR split the test into mission-shape rows that exclude the on-approach set). Production gate fix ONLY if Step 1 evidence shows a real `DensityAltitudeDecline` / `AbortTakeoff` event firing on a non-eligible MissionStep.

### (3) kotest 5.9.1 not in user's `~/.gradle/caches`

`:sim:compileTestKotlinJvm` fails offline with `No cached version of io.kotest:kotest-framework-engine:5.9.1 available for offline mode`. fn-26.1's worker fetched kotest via `curl` into a sandbox-local Maven layout (`$TMPDIR/local-maven`) — that layout is not persisted to the user's real cache.

Fix: one-time `./gradlew :sim:compileTestKotlinJvm` outside `--offline` populates `~/.gradle/caches/modules-2/files-2.1/io.kotest/`. Thereafter `--offline` works.

## Edge Cases & Constraints [inferred]

- The 14 currently-green tests in `GoAroundSequencingSpec` (including the negative-case `ARR-TURN-BASE blocked while GA active` which passes via the same fail-conservative quirk that breaks the positive cases) must remain green after the fixture refactor. Any fixture change must not disturb the AC_B-only test paths; where AC_A is already present (the round-trip test), diagnose `arrivalSequence` / geometry state rather than adding another aircraft.
- Both pilot tests assert NEGATIVE cases. Investigation could conclude the test is wrong (deliberate contract change) — in that case, update the test rather than the gate. Document either way.
- The kotest fetch needs network egress; agent sandbox blocks it but the user's normal terminal has internet. Single command, no automation needed.

## Acceptance Criteria

- **R1** [user / paraphrase, revised per plan-review R1]: All 17 `GoAroundSequencingSpec` tests green via diagnosis-first test-fixture refinement (task .2 Step 1) — capture actual `separationAssessments` + `arrivalSequence` + `ruleTraces` per failing test, then refine fixture (extend `TEST_INDEX` with further-out point if geometry too tight, seed `arrivalSequence` directly if slots empty, OR adjust per observed state). Each fixed test asserts the positive precondition (`separationAssessments` shows COMFORTABLE for AC_B) BEFORE asserting rule output. NO change to controller production code. The 2 already-fixed bugs (rule placement moved AwaitApproach → AwaitDownwind; detekt regression on `pilotDecide`) stay in their committed state.

- **R2** [paraphrase, revised per plan-review R1]: Both pilot tests resolved with documented root cause (task .3 Step 1 evidence-first):
  - First identify the actual `PilotEvent` returned by each failing row's `derivePilotEvent` call. Reviewer's prediction: `DecisionAltitudeWithoutClearance` (the earlier branch leaks for on-approach steps with low altitude + no clearance — `isDensityAltitudeDeclineEligible` already correctly rejects the airborne steps the L187 test iterates).
  - Test-fixture fix (preferred): neutralize the earlier branch (raise altitudeM > DECISION_ALTITUDE_M, set `hasClearance`, OR exclude on-approach mission shapes from the negative-row enumeration).
  - Gate fix (only if Step 1 evidence proves a real `DensityAltitudeDecline` / `AbortTakeoff` event firing on a non-eligible step): tighten the corresponding eligibility predicate. The test KDocs document the cross-branch dependency either way.
  - Both `PilotEventAbortTakeoffTest.kt:248` + `PilotEventDensityAltitudeTest.kt:187` green.

- **R3** [user]: User runs `./gradlew :sim:compileTestKotlinJvm` ONCE outside `--offline` (in their normal terminal, not the agent sandbox) to populate kotest 5.9.1 in `~/.gradle/caches/modules-2`. Thereafter the offline gradle formula from `d32b8b8` works for `:sim:jvmTest` too.

- **R4** [paraphrase, command-form clarified per plan-review R1 Minor]: Full verify GREEN. Two valid invocations:
  - **User's normal terminal** (project wrapper, post-kotest fetch): `./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt --offline --no-daemon` + `./gradlew :migration:allTests --offline --no-daemon`.
  - **Agent sandbox** (Nix-provided gradle — `./gradlew` can't extract wrapper under sandbox-restricted `~/.gradle/wrapper/dists/`): use the formula at task .4's Approach Step 5 (`GRADLE_USER_HOME=$HOME/.cache/gradle GRADLE_RO_DEP_CACHE=$HOME/.gradle/caches TMPDIR=$TMPDIR _JAVA_OPTIONS=… nix … develop -c gradle …`).
  - Both invocations resolve to gradle 8.14.4. 13 sim goldens + all property tests + all unit tests pass; detekt clean.

- **R5** [paraphrase]: 45+ commits ahead of `origin/main` pushed once R1–R4 green. Branch ready for PR / further work.

- **R6** [inferred]: `.plan` FN31-TEST-1 entry updated to mark all sub-issues green (or moved to a "completed" section per the user's `.plan` convention).

## Boundaries — Out of Scope [inferred]

- **Changing `SeparationConcernAbove`'s fail-conservative semantics.** The empty-assessment → conservative behavior may be doctrinally correct for production (with one aircraft in flight you can't assess pair separation, so guard against turning that aircraft toward a possibly-occupied runway). Fixture-side fix is preferred. If the doctrine itself needs reexamination, file a follow-up.

- **Investigating ARR-EXTEND, ARR-LAND, ARR-DOWNWIND-ACK for similar stage-placement issues.** These rules also have stage-block placement under `TowerArrival.kt`. Existing sim goldens (G1, G2, G3a) pass with their current placement, so no test-failure evidence exists yet. If a sim golden regression surfaces during R4 full-verify, file a follow-up; do NOT speculatively move them.

- **Extending `fn-31` (cited rule-to-test exploration spike).** That epic is research-scoped (mapping rule citations to test coverage). This work is a verification follow-up — green out the residual failures FN31's verification run found. Separate epic, separate scope.

- **Bumping kotest beyond 5.9.1** or migrating away from kotest entirely. v1 keeps what fn-26.1 landed.

## Decision Context [inferred + paraphrase]

User's stated priority order (2026-05-17 session): "(1) → (2) → (3) → (4) → push" — install JDK / make gradle work, then green out remaining failures, push.

Pre-fix sequence within this epic:
1. **R3 first** — single online command unblocks the whole `:sim` test suite. If kotest fetch reveals network/repo issues, that's a separate blocker.
2. **R1 next** — clearest fix path (fixture refactor, no production code change). Bounded scope.
3. **R2 third** — investigation-required; root cause unknown. May expand if a deeper recognition-gate issue surfaces.
4. **R4 verify** — full re-run to confirm no regression elsewhere.
5. **R5 push** — final step.

The 2 already-fixed work (detekt refactor in `PilotDispatch.kt`, rule placement in `TowerArrival.kt`, backtick fix in `EngineOffKinematicClampSpec.kt`) is committed in `d32b8b8` + the latest commit and forms the baseline this epic builds on. No revert / reapply needed.

The agent investigation in this session is the load-bearing evidence — `.plan` FN31-TEST-1's 2026-05-17 update captures the verbatim root-cause findings with file/line refs.

## References [paraphrase]

- `.plan` FN31-TEST-1 — full root-cause analysis (load-bearing predecessor; updated this session)
- Commit `d32b8b8` — fn-28 gradle verify pass: 2 fixes (detekt + rule-placement) + 3 follow-up issues filed
- Latest commit — verify-pass cleanup: remove duplicate deferment, update FN31-TEST-1, fix backtick `;`
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/GoAroundSequencingSpec.kt` — 3 failing tests' source
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt:923-935` — `SeparationConcernAbove` fail-conservative implementation
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/SeparationEngine.kt:25-47` — `assessSeparation` empty-for-n<2 return
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEventAbortTakeoffTest.kt:248` — first pilot failure
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEventDensityAltitudeTest.kt:187` — second pilot failure
- Nix-shell + sandbox formula (from `d32b8b8` commit message):
  ```
  GRADLE_USER_HOME=$HOME/.cache/gradle GRADLE_RO_DEP_CACHE=$HOME/.gradle/caches \
  TMPDIR=$TMPDIR _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  nix --extra-experimental-features 'nix-command flakes' develop \
    --no-write-lock-file -c gradle <args>
  ```
- `fn-31-cited-rule-to-test-exploration-spike` — sibling research epic (open; this work is verify follow-up, NOT an extension of fn-31)

## Review considerations [added during plan-review R1 per reviewer Major 5]

- **FP / type safety**: no sealed-hierarchy / Arrow `Either` changes expected; the conditional gate-fix path in .3 (if invoked) would add a `MissionStep` arm to an existing predicate — type-safe by construction.
- **Test architecture**: the work flips two existing test fixtures from "blind assertion" to "assert observed state THEN assert rule output". Pattern: pre-assert the actual `separationAssessments` / returned `PilotEvent` before asserting the consequent behavior. Surfaces branch-precedence + guard-chain dependencies that the original test design hid.
- **Impact**: scoped to 2 test files (`GoAroundSequencingSpec.kt` + the 2 pilot tests). Conditional production-code touch in .3 only if reviewer's diagnosis flips at impl-time (gate fix instead of fixture fix). No expected source change to `Guard.kt`, `SeparationEngine.kt`, `Controller.kt`, `Observe.kt`, `TowerArrival.kt`, or any `:sim` golden test.
- **Operational ATC correctness**: doctrine preserved either way — `SeparationConcernAbove` fail-conservative stays; `derivePilotEvent` branch-precedence order stays; `MissionStep` eligibility predicates stay (unless Step 3 fix path in .3 is taken, which would tighten a gate with a documented contract change). The fix-path priority (test fixture first, gate change only on evidence) keeps the production-code surface minimal.
- **Reviewer focus** when impl-review lands: any production-code edit needs justification via Step 1 evidence in `## Resolved during implementation`. Pure test-side fixes get a lighter touch.

## Requirement coverage

| Req | Description | Task | Gap justification |
|-----|-------------|------|-------------------|
| R1  | 17 `GoAroundSequencingSpec` tests green via 2-aircraft fixture + assessment assertion | fn-32...2 | — |
| R2  | 2 pilot negative-case tests resolved with documented root cause | fn-32...3 | — |
| R3  | `:sim:jvmTest` runs offline after one-time online kotest fetch | fn-32...1 | — |
| R4  | Full verify GREEN (13 sim goldens + all units + detekt + migration) | fn-32...4 | — |
| R5  | 45+ commits pushed | fn-32...4 | — |
| R6  | `.plan` FN31-TEST-1 marked complete | fn-32...4 | — |

## Suggested next step

After plan-review SHIP, run `/flow-next:work fn-32-green-out-fn31-test-1-residual-verify` to execute the 4 tasks in dependency order (.1 + .2 + .3 in parallel — no inter-deps — then .4 close-out).
