# Restore broad sim JVM suite

## Goal & Context

Close `.plan` item `SIM-RED-1`: broad `:sim:jvmTest` is red in reactive/emergency goldens. The known failing classes from FN43/FN44 verification are:

- `G0AbortTakeoffEngineFailureTest`
- `G3aPilotReactiveMultiAircraftTest`
- `G3bCrossAerodromeReactiveTest`

The goal is to restore the broad sim JVM suite without weakening golden-test standards. If a golden expectation is obsolete, replace it with an explicit design decision and a better assertion. If behaviour is wrong, fix behaviour.

## Architecture & Data Models

Start with reproduction and failure taxonomy. Treat these as likely related to reactive/emergency state-machine paths, but do not assume a shared root cause until the traces prove it.

Important invariants:

- golden tests must remain high-level and believable;
- no `@Disabled`, skip lists, broad tolerances, or silent expectation deletion;
- no catch-all `else` or untyped `error()` for type-valid states;
- mission rewrites and go-around/abort reversals must fully reset state.

## API Contracts

No new public API is expected. Changes may touch sim, pilot, controller, or test helpers if required by the real failure. Any new test helper must preserve the current golden pattern: fixture-driven scenario, deterministic event run, assertions over observed run output.

## Edge Cases & Constraints

- The worktree already has unrelated dirty files: `.flow/memory/MEMORY.md`, `AGENTS.md`, `.flow/memory/knowledge/...`, and `gradlew-nix`. Do not touch or commit them.
- Run Gradle through `nix-shell --run`.
- If fixing one failure changes another reactive golden, inspect the shared state-machine implication rather than patching assertions independently.

## Acceptance Criteria

- [ ] Focused failing classes pass.
- [ ] Broad `nix-shell --run './gradlew :sim:jvmTest'` passes.
- [ ] `nix-shell --run './gradlew detekt'` passes.
- [ ] `.plan` item `SIM-RED-1` is marked done or removed with evidence.
- [ ] Any obsolete expectation is documented in wiki/design decision or `.plan`, not silently changed.
- [ ] Flow completion review records root cause and why the fix is not a corner cut.

## Boundaries

In scope: restoring broad sim JVM test health and closing `SIM-RED-1`.

Out of scope: adding new source-mapped evidence features, fixing unrelated non-sim modules, or broad refactors not required by the failing tests.

## Decision Context

This comes before more source-mapped scaling because a red broad sim suite contaminates confidence in every later review. Prefer behavioural fixes over test rewrites. Prefer narrow, trace-backed changes over architectural churn.

## Review considerations

**FP / type safety:** Exhaustive branches only. No catch-all `else`. New state variants require total handlers.

**Test architecture:** Golden expectations remain high-level. If a test changes, it should still assert the real operational story, not implementation trivia.

**Impact:** Reactive go-around and abort-takeoff paths are shared state-machine code. Audit interaction with mission reset, commitment closure, phase transitions, and controller beliefs.

**Operational correctness:** Emergency/abnormal behaviour must remain source-backed where ATC/pilot procedure is asserted. Do not invent phraseology or controller doctrine to satisfy a test.
