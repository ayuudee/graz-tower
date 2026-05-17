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

Root cause (verified via debug-println in this session, see `.plan` FN31-TEST-1): `Guard.SeparationConcernAbove(INTERVENTION)` is fail-conservative — returns `true` when `ctx.beliefs.separationAssessments` is empty (commented "no assessments = assume concern, be conservative"). The test fixtures populate only AC_B; `assessSeparation` at `controller/.../assess/SeparationEngine.kt:30` returns empty for `arrivals.size < 2`. `Not(SeparationConcernAbove(INTERVENTION))` then evaluates `Not(true) = false` and `ARR-TURN-BASE`'s guard fails — even though there is no actual separation concern (only one aircraft tracked).

Fix shape: add AC_A (the GA-going aircraft, already referenced in the fixture's `goAroundInProgressByRunway` entry) as a second tracked aircraft in `baseView`'s `aircraft` map + a sibling `commitment(AC_A, runway=RWY)` in `baseBeliefs.commitments`. AC_A's `point` should be PT_FINAL or a sibling point so the pair separates cleanly on the leg. `assessSeparation` then generates a real `COMFORTABLE`-severity assessment; `SeparationConcernAbove(INTERVENTION)` returns false; the `Not(...)` passes; `ARR-TURN-BASE` fires.

### (2) Pilot negative-case test failures (pilot commonTest)

Two assertions:
- `PilotEventAbortTakeoffTest.kt:248` — assertion-failed error; specific assertion not yet investigated.
- `PilotEventDensityAltitudeTest.kt:187` — test name `"does NOT fire on airborne steps — mission-shape guard rejects"`; the recognition is FIRING when it should not (mission-shape guard is supposed to reject airborne steps but isn't).

Fix path TBD: either tighten the recognition gate (so the negative-case correctly does not fire) or update the test if a deliberate contract change is the right answer. The fn-28.2 / fn-28.9 work introduced these branches; the most likely root cause is a missing phase / step gate in `deriveDensityAltitudeEvent` / `deriveAbortTakeoffEvent`.

### (3) kotest 5.9.1 not in user's `~/.gradle/caches`

`:sim:compileTestKotlinJvm` fails offline with `No cached version of io.kotest:kotest-framework-engine:5.9.1 available for offline mode`. fn-26.1's worker fetched kotest via `curl` into a sandbox-local Maven layout (`$TMPDIR/local-maven`) — that layout is not persisted to the user's real cache.

Fix: one-time `./gradlew :sim:compileTestKotlinJvm` outside `--offline` populates `~/.gradle/caches/modules-2/files-2.1/io.kotest/`. Thereafter `--offline` works.

## Edge Cases & Constraints [inferred]

- The 14 currently-green tests in `GoAroundSequencingSpec` (including the negative-case `ARR-TURN-BASE blocked while GA active` which passes via the same fail-conservative quirk that breaks the positive cases) must remain green after the fixture refactor. The fixture change must add AC_A WITHOUT disturbing the AC_B-only test paths.
- Both pilot tests assert NEGATIVE cases. Investigation could conclude the test is wrong (deliberate contract change) — in that case, update the test rather than the gate. Document either way.
- The kotest fetch needs network egress; agent sandbox blocks it but the user's normal terminal has internet. Single command, no automation needed.

## Acceptance Criteria

- **R1** [user / paraphrase]: All 17 `GoAroundSequencingSpec` tests green via test-fixture refactor — add AC_A (the GA-going aircraft, with sibling commitment + position) so `assessSeparation` produces a real `COMFORTABLE` assessment instead of empty. NO change to controller production code (`TowerArrival.kt`, `Guard.kt`, `Observe.kt`, `Controller.kt`). The 2 already-fixed bugs (rule placement moved AwaitApproach → AwaitDownwind; detekt regression on `pilotDecide`) stay in their committed state.

- **R2** [paraphrase]: Both pilot tests resolved with documented root cause:
  - `PilotEventAbortTakeoffTest.kt:248` — investigate assertion, identify gate / contract drift, fix.
  - `PilotEventDensityAltitudeTest.kt:187` ("does NOT fire on airborne steps — mission-shape guard rejects") — recognition is firing on airborne steps when it should not; tighten phase / step gate in `deriveDensityAltitudeEvent` OR update test if contract change deliberate.
  - Both green.

- **R3** [user]: User runs `./gradlew :sim:compileTestKotlinJvm` ONCE outside `--offline` (in their normal terminal, not the agent sandbox) to populate kotest 5.9.1 in `~/.gradle/caches/modules-2`. Thereafter the offline gradle formula from `d32b8b8` works for `:sim:jvmTest` too.

- **R4** [paraphrase]: Full verify GREEN — `./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt --offline --no-daemon` + `./gradlew :migration:allTests --offline --no-daemon`. 13 sim goldens + all property tests + all unit tests pass; detekt clean.

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

## Suggested next step

Run `/flow-next:plan fn-32-green-out-fn31-test-1-residual-verify` to add per-R-ID tasks (likely 3 — one per fix surface: controller fixture, pilot tests, kotest fetch + final-verify), or `/flow-next:interview fn-32-green-out-fn31-test-1-residual-verify` to refine acceptance + edge cases before planning.
