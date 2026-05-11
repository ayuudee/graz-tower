---
title: Compound-predicate test assertions short-circuit vacuously on partial-state inpu
date: "2026-05-11"
track: bug
category: test-failures
module: pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotTailwindGoAroundTest.kt
tags: [fn-15, fn-15.1, testing, assertion-discipline, copy-paste, subtree-rewrite, codex-impl-review, scope-per-diff]
problem_type: test-failure
symptoms: Test passes despite the subtree rewrite leaving the old compound in the tree alongside the new one — codex flagged the assertion as ineffective
root_cause: Compound predicate 'old && count(new) == 0' short-circuits when both remain; copy-pasted fn-14 pattern carried the flaw forward and codex held the new sibling to a higher per-diff bar
resolution_type: fix
related_to: [bug/test-failures/r9-style-allowlist-guards-must-key-by-2026-05-09]
---

## Problem

When mirroring a fn-14 test pattern (`PilotCrosswindGoAroundTest`) into a
sibling test for fn-15 (`PilotTailwindGoAroundTest`), I copied the existing
"after rewrite, the TouchAndGo subtree has been replaced" assertion verbatim:

```kotlin
assertFalse(
    compounds.any { it is TaskName.TouchAndGo && (compounds.count { c -> c is TaskName.CircuitAfterGoAround } == 0) },
    "after rewrite, the TouchAndGo subtree has been replaced (not left alongside CircuitAfterGoAround)",
)
```

This predicate passes **vacuously** when BOTH the old `TouchAndGo` AND a new
`CircuitAfterGoAround` remain in the tree — the `count CAGA == 0` clause
short-circuits when the rewrite added a CAGA. The test "passes" but
doesn't actually prove the rewrite happened. Codex round-1 caught it as a
Minor / introduced finding because the test came in with this PR's diff
(even though the bug originated in fn-14.1 and was copy-pasted forward).

## What Didn't Work

Mirroring fn-14.1's `PilotCrosswindGoAroundTest` line-for-line without
re-reasoning each assertion. The original assertion in fn-14.1 had the
same flaw, but because fn-14.1's review didn't catch it, the reviewer
correctly held the new copy to the higher bar — review scope is
per-diff, so any test asserted in the current PR is on the hook.

## Solution

Replace the compound predicate with a direct absence assertion:

```kotlin
assertFalse(
    compounds.any { it is TaskName.TouchAndGo },
    "after rewrite, the TouchAndGo subtree has been replaced (no TouchAndGo compound remains)",
)
```

Combined with the existing positive `assertTrue(CircuitAfterGoAround in
compounds)`, this proves both halves: CAGA was added AND TouchAndGo
was removed. Did NOT touch fn-14.1's identical-shape line in
`PilotCrosswindGoAroundTest` — that's outside this task's scope; the
discipline pinned in
`bug/build-errors/recognitionapply-pipelines-need-mission-2026-05-11`'s
"Update 2026-05-11" note applies in reverse here (do not touch pre-existing
code unless the task spec calls for it, even if you spot a bug).

## Prevention

When copying an existing test pattern into a sibling test:

1. **Read every assertion's failure mode**, not just the assertion
   shape. For each assertion, ask: "what's the minimal mutation that
   makes this pass while the code is broken?" If the answer is "the
   compound predicate trivially short-circuits when X holds," the
   assertion needs to be split into independent assertions on each
   half of the contract.

2. **Direct absence assertions beat compound predicates with `count`
   side-channels.** `count CAGA == 0` mixed into a `TouchAndGo &&
   ...` predicate is a code-smell — the two halves should be
   asserted independently:
   - `assertTrue(TaskName.CircuitAfterGoAround in compounds)` — added.
   - `assertFalse(any { it is TaskName.TouchAndGo })` — removed.
   The test now fails loudly on either half regressing.

3. **Pre-existing identical patterns are NOT in scope for the
   current task** unless the spec calls for them. Codex's per-diff
   classification (introduced vs pre_existing) is the right
   discipline; we fix what we ship in this PR and leave fn-14.1's
   identical row to either (a) a future audit pass or (b) the
   inevitable copy-paste audit that catches both at once. Don't
   widen scope to "fix every place this shape appears" without
   a typed deferment or follow-up task.
