# fn-26 — Engine pass A: step-function property tests

## Overview

The sim engine is `step(state: SimState, event: SimEvent) → (SimState, List<SimEvent>)`: pure, deterministic, seeded PRNG (fn-11). It's currently exercised by 9 hand-authored goldens — each a narrow scenario. Property-based testing closes the long tail: assert engine-level invariants over randomly generated `(state, event)` pairs.

This epic adds Kotest-property generators and property tests for `step()` and its companions (`runUntil`, event-loop primitives), focusing on **engine-level invariants** that hold across every valid input:

- **Totality**: `step()` never throws. Every valid `(state, event)` produces a `(newState, emitted)` pair. Per `feedback_no_corners.md` discipline — engines fail loud at boundaries, not silently mid-call.
- **Monotonicity**: `newState.now >= state.now`; `newState.seq > state.seq` when events were emitted.
- **Determinism**: `step(s, e) == step(s, e)` (same input twice = same output twice).
- **Invariant preservation per certifier surface**: after every event, the state still satisfies the certifier preconditions on the surface the event affects (runway / surface / air / separation). Uses the Kotlin-side certifier shims at `controller/.../certify/` as the oracle.

Kotest-property generators (`kotest-property` 5.9.1 is in `libs.versions.toml`; this epic activates it) produce arbitrary `(state, event)` pairs from constrained shapes — seeded from valid worlds (LOWG candidate JSON) to keep generation tractable.

## Boundaries / non-goals

- **Out: testing scenario-level correctness.** Property tests cover engine invariants; per-scenario "the controller correctly issues GA on obstruction" is the goldens' job.
- **Out: from-scratch world generation.** Generators seed from existing world candidates (LOWG); generating valid worlds from scratch is a separate (much bigger) investment.
- **Out: coverage-guided fuzzing.** Random sampling only in v1. AFL-style instrumentation comes after if random sampling stops finding bugs.
- **Out: shrinking custom data classes.** Use Kotest's stdlib shrinkers; custom shrinkers for `SimState` are deferred (Kotest does the obvious things for ints/strings/lists; complex shrinkers would land in a follow-up).
- **Out: extending the Lean certifiers.** This epic CONSUMES the existing 4 certifiers as oracles; extending them is the FM track's own work.

## Strategy Alignment

Active tracks served by this plan:
- **Runtime simulator** — fills a known gap: the engine has no property tests. Per the strategy's "every state transition has its reversal tested before its forward path ships" claim, this epic plus fn-27 (reversal properties) makes the claim structural rather than per-golden.
- **FM / Lean proof program** — the FM stream is closed at Contract v1 per `research/fm/PROJECT_STATUS.md`. The 4 certifiers are now CONSUMED by Kotlin-side property tests, which is exactly the contract's "future safety work should move upward into the controller, which can use the certified kernels and delivered theorem registry as guardrails."

## Decision context

**Why Kotest-property over alternatives**: `kotest-property` is already a declared dependency (`libs.versions.toml` 5.9.1; not yet activated in any test). Adding a new framework just for this would be wasteful. Kotest-property provides arbs (generators), shrinking, and JUnit5 integration. Sibling tests in `controller/.../certify/RunwayKernelBoundarySpec.kt` already exist as the "boundary spec" pattern; property tests follow that shape.

**Why seed from world candidates instead of generating worlds from scratch**: generating valid `AviationWorld` instances requires geometry validation, role staffing, role-to-controller mappings, etc. The LOWG world-candidate.json already passes validation; using it as a seed lets generators focus on `(SimState, SimEvent)` permutations rather than world correctness. The G2 cross-aerodrome golden (fn-5) also uses the loader; LJMB is available as a second world if multi-aerodrome generation is needed.

**Why 4 invariant classes (totality, monotonicity, determinism, certifier-preservation)**: these are the engine-level properties that should hold universally. Per-scenario invariants (e.g. "controller issues GA when wind exceeds limit") are too specific for property tests — those are goldens. The chosen 4 are bug-finders, not behavior-specifiers.

## Acceptance

- **R1:** New file `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/StepPropertyTest.kt` — Kotest spec class with 4+ property tests covering totality, monotonicity, determinism, and per-event-class invariant preservation (where the affected certifier surface has a Kotlin shim).
- **R2:** Generators (`Arb<SimState>`, `Arb<SimEvent>`, `Arb<Pair<SimState, SimEvent>>`) seeded from the existing LOWG world candidate. Generators are deterministic given a Kotest seed.
- **R3:** Totality property: `step(state, event)` never throws for any generated `(state, event)`. Failing pairs surface with the (seed, generated input) for reproducibility.
- **R4:** Monotonicity property: after `step()`, `newState.now >= state.now` AND `newState.seq > state.seq` (when emitted events is non-empty) OR `newState.seq == state.seq` (no-op event).
- **R5:** Determinism property: `step(s, e) == step(s, e)` — same inputs produce equal outputs (relies on `SimState` and `SimEvent` having structural equality).
- **R6:** Certifier-preservation property: for each event class that affects a certifier surface (runway / surface / air / separation), assert that the new state still passes the relevant Kotlin-side certifier shim (currently `RunwayKernel.kt`; expand as more shims land). At minimum: runway certifier invariants hold post-event.
- **R7:** Test runs are bounded — ≤500 generated inputs per property by default; can crank to 10k via system property for nightly runs. Test execution stays under 30 seconds for the default count.
- **R8:** `build.gradle.kts` for `:sim` adds `kotest` (engine + runner + property + assertions) to `jvmTest` dependencies. Kotest JUnit5 runner registered.
- **R9:** Full verify GREEN: `./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests :migration:allTests detekt --offline --no-daemon` exits 0. Nine sim goldens GREEN. Detekt baseline unchanged. New property tests pass with default seed (or document any seed-sensitive failures as in-scope bugs).
- **R10:** Diff scope: ~2-3 new files (`StepPropertyTest.kt` + possibly `EngineGenerators.kt` extracted helper) + 1 modified `sim/build.gradle.kts`. Total ≤5 files, ≤400 LOC.

## Early proof point

If R3 (totality) or R6 (certifier preservation) fails on a default seed run, that's a real latent bug in the engine — surface immediately. Per the strategy's "AI-generated code is locally correct and globally blind", property tests exist exactly to find these. Bugs caught at this stage become their own follow-up epics; they don't block fn-26's SHIP.

## Quick commands

```bash
# Pre-task baseline
git rev-parse HEAD > $TMPDIR/fn-26-1-base-sha.txt
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
    --offline --no-daemon 2>&1 | tee $TMPDIR/fn-26-1-base.log

# Targeted property test run
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :sim:jvmTest --tests "*StepPropertyTest*" --offline --no-daemon

# Post-task verify
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests :migration:allTests detekt \
    --offline --no-daemon
```

## Requirement coverage

| Req | Description | Task(s) |
|-----|-------------|---------|
| R1  | StepPropertyTest.kt with 4+ properties | fn-26-engine-pass-a-step-function-property.1 |
| R2  | Generators seeded from LOWG candidate | fn-26-engine-pass-a-step-function-property.1 |
| R3  | Totality | fn-26-engine-pass-a-step-function-property.1 |
| R4  | Monotonicity | fn-26-engine-pass-a-step-function-property.1 |
| R5  | Determinism | fn-26-engine-pass-a-step-function-property.1 |
| R6  | Certifier preservation (runway shim at minimum) | fn-26-engine-pass-a-step-function-property.1 |
| R7  | Bounded test runtime (≤500 inputs / ≤30s default) | fn-26-engine-pass-a-step-function-property.1 |
| R8  | Kotest dependencies in :sim build.gradle.kts | fn-26-engine-pass-a-step-function-property.1 |
| R9  | Full verify green | fn-26-engine-pass-a-step-function-property.1 |
| R10 | Diff ≤5 files / ≤400 LOC | fn-26-engine-pass-a-step-function-property.1 |

## Review considerations

- **FP / type safety**: property tests must respect the existing engine type signatures. No `\!\!`, no `@Suppress`. Generators that produce invalid states (e.g. `SimState` with broken invariants) defeat the test purpose; constrain generators to states the engine should accept. **Reviewer focus**: confirm `Arb<SimState>` doesn't generate malformed instances that mask real engine bugs as "property test invalid input."
- **Test architecture**: Kotest spec-style with `StringSpec` or `FunSpec`. JUnit5 runner. Properties exercise the engine in isolation from goldens. **Reviewer focus**: confirm test runtime stays bounded per R7; if a property test runs 30s+ at 500 inputs, generator complexity is too high.
- **Impact**: scoped to `:sim/jvmTest`. No production code change. **Reviewer focus**: confirm `:sim/commonMain` untouched.
- **Operational ATC correctness / applicability**: not directly — invariant assertions are engine-level. The certifier-preservation property does check ATC-relevant invariants (runway invariants), but doesn't claim controller-decision correctness. **Reviewer focus**: confirm R6 assertions match the certifier shim's actual claims (don't fabricate stronger invariants than the kernel proves).

## References

- `sim/src/jvmMain/kotlin/xyz/easiersaid/twr/sim/Driver.kt` — `runUntil` event loop
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/Step.kt` — the `step()` function (line 1+)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/certify/RunwayKernel.kt` — Kotlin shim for runway certifier (the R6 oracle)
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/certify/RunwayKernelBoundarySpec.kt` — boundary spec pattern this test mirrors
- `cad/airports/rendered/lowg/world-candidate.json` — seed world for generators
- `research/fm/certified_runtime_contract_v1.md` — the 4 certifiers + their checked theorems (oracle inventory)
- `research/fm/certifier_interfaces.md` — Lean signatures (the Kotlin shims mirror)
- `gradle/libs.versions.toml` — kotest 5.9.1 (already declared; not yet activated in any test)
- `.flow/memory/knowledge/best-practices/test-pin-discipline-2026-05-15.md` — test-pin discipline (mint-id, post-state observables) that property tests should also respect
