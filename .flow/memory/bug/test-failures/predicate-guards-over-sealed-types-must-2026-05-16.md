---
title: Predicate guards over sealed types must use exhaustive enum enumeration in tests
date: "2026-05-16"
track: bug
category: test-failures
module: pilot/src/commonTest/.../IsAbortTakeoffEligibleSpec.kt + sibling guard specs
tags: [fn-28, fn-28.9, testing, exhaustive-enumeration, enum-coverage, guard-discipline, codex-impl-review, negative-space]
problem_type: test-failure
symptoms: "Hand-maintained negative-case lists missed enum values FLY_FINAL_TO_SHORT_FINAL, FLY_STAR, AWAITING_ATC_INSTRUCTION"
root_cause: Categorized hand-maintained lists drift from the enum as new values land; categorization is reader-useful but contract is the enum itself
resolution_type: fix
related_to: [bug/test-failures/beliefstate-active-window-pin-use-2026-05-16, bug/test-failures/compound-predicate-test-assertions-2026-05-11, bug/test-failures/dynamic-injection-sim-tests-must-gate-2026-05-16, bug/test-failures/inherited-sim-test-gate-semantics-may-2026-05-11, bug/test-failures/r9-style-allowlist-guards-must-key-by-2026-05-09]
---

## Problem

`IsAbortTakeoffEligibleSpec`'s "is NOT eligible" rows were
hand-maintained lists per category (pre-taxi, airborne, arrival,
terminal). The lists were missing several enum values:
`FLY_FINAL_TO_SHORT_FINAL`, `FLY_STAR`, `AWAITING_ATC_INSTRUCTION`.
A future broadening of `isAbortTakeoffEligible` to add ONE of those
steps would silently pass the guard tests — the negative-space
contract is incomplete by hand-maintenance.

Same shape in `PilotEventAbortTakeoffTest`'s gate 4 negative row.

## What Didn't Work

Maintaining the negative-space as separate categorized lists
(pre-taxi / airborne / arrival / terminal). The categories help
reader comprehension but the per-category lists drift from the
enum as new values land. The categorization is a humanly-useful
ABSTRACTION; the contract is the ENUM, and the test must speak the
contract's vocabulary.

## Solution

Replace the categorized hand-maintained lists with a single
exhaustive loop over `MissionStep.entries`:

```kotlin
val eligible = setOf(
    MissionStep.AWAIT_TAKEOFF_CLEARANCE,
    MissionStep.FLY_DEPARTURE,
)
for (step in MissionStep.entries) {
    val expected = step in eligible
    val actual = isAbortTakeoffEligible(missionWithStep(step))
    check(actual == expected) { ... }
}
```

The eligible-set declaration mirrors the function body line-for-line;
the loop covers the negative space by construction. Adding a new
MissionStep enum value automatically flows through (the new step
will be evaluated against the eligible set and either appear in it or
fail closed).

See `pilot/src/commonTest/.../IsAbortTakeoffEligibleSpec.kt` for the
exemplar implementation; sibling pattern lives in
`PilotEventAbortTakeoffTest`'s "gate 4" enumeration row.

## Prevention

For any predicate that gates on a sealed-type or enum input
(`MissionStep`, `CompletionMode`, `PilotPhase`, etc.), the negative-
space test MUST use exhaustive enumeration:

1. Use `EnumType.entries` (Kotlin 1.9+) or `EnumType.values()`
   (Kotlin 1.8 fallback) to iterate.
2. Declare the positive set as a `setOf(...)` constant that mirrors
   the function body (one source of truth).
3. Assert each value's expected result matches the function's
   actual result.

This is the same "expand the condition space, don't carve
exceptions" pattern that pins boundary semantics in sibling specs
(e.g. fn-14.1's `WIND_REACTIVE_ELIGIBLE_STEPS` test enumerates
every MissionStep against the membership predicate).

Anti-pattern markers (failure modes the hand-maintained list
silently allows):
- Adding a new enum value without updating the test.
- Adding a new function-body branch without updating the test.
- Renaming an enum value (compile errors catch this for both, but
  semantic drift between categorization-by-narrative and actual
  enum partitioning isn't caught).
