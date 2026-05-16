---
title: Grep-enforceable token prohibitions + @Suppress in test helpers
date: "2026-05-16"
track: bug
category: build-errors
module: sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/
tags: [fn-28, fn-28.7, sim-golden, grep-enforcement, detekt, suppress-discipline, acceptance-criteria, codex-impl-review, long-method, refactor-discipline]
problem_type: build-error
symptoms: "Codex review flags (a) literal forbidden-token in KDoc/archive prose despite the token's presence being mechanically prohibited by spec, and (b) @Suppress(\"LongMethod\") on a private helper called by an @Test method (AGENTS.md forbids @Suppress as a silent workaround)"
root_cause: "Spec prose that explains 'no TOKEN' includes the literal TOKEN; detekt's LongMethod ignoreAnnotated:['Test'] does NOT cover private helpers called by @Test methods"
resolution_type: fix
related_to: [bug/build-errors/ga-path-precedence-reorder-when-adding-2026-05-10, bug/build-errors/recognitionapply-pipelines-need-mission-2026-05-11, bug/build-errors/sticky-witness-guard-swap-can-regress-2026-05-09]
---

## Problem

When a task spec contains a grep-enforceable "no `TOKEN` anywhere in test/spec" acceptance criterion, an author easily includes the literal token inside KDoc / archive prose that EXPLAINS the prohibition ("NO `TOKEN` placeholder appears"). The criterion is mechanically broken even though the token is only in prose.

Sibling pattern: `@Suppress("LongMethod")` on a refactor-borderline helper trips AGENTS.md's "Silent workarounds — `@Suppress` are forbidden" commandment. Detekt's `LongMethod` has `ignoreAnnotated: ['Test']`, so the rule fires on `private fun` helpers that the @Test method delegates to — exactly the shape that long sim-goldens take.

## What Didn't Work

(a) Writing `NO \`TOKEN\` appears anywhere` in KDoc / archive prose as a self-documenting prohibition: codex correctly flagged the literal token as a grep violation.

(b) Inlining a 600-line scenario body and putting `@Suppress("LongMethod")` on the helper "because G2/G3a tests are similarly long": the G2/G3a tests get away with their length because the body lives inside the `@Test`-annotated public method (covered by `ignoreAnnotated: ['Test']`), NOT a private helper.

## Solution

(a) Grep-enforceable prohibitions: rephrase prose to avoid the literal token. "destination-GA placeholder enum/string" works as a synonym; sweep the test KDoc, all archive entries, and any `.plan` notes. Verify with `grep -n TOKEN <changed-files>` returning zero matches.

(b) When a sim-golden's shared body grows beyond the `LongMethod` threshold, extract focused private helpers (each well under 95 lines) bundled by a typed `ScenarioContext` data class + a mutable `ScenarioHookState` for per-run observers. Reference impl: `sim/.../G3bCrossAerodromeReactiveTest.kt` — split into setup / build-state / build-events / make-hook / observe-radio / print-diagnostics / assert-{named-witness,weather-transitions,causal-ordering,commitment-regression,kinematic-no-event,r22-shape,filing-distribution}. The orchestration shell stays ~70 lines (delegations + one inline check).

## Prevention

- Pre-commit grep: when a task's acceptance criteria include a "no TOKEN anywhere" clause, add a `grep -n TOKEN <changed-files>` step to the impl checklist before commit. If grep returns matches inside the task's diff (excluding the spec file that AUTHORED the prohibition), reword.
- Refactor before suppressing: when a private helper crosses `LongMethod` (95) in tests, refactor into 4-8 focused sub-helpers bundled by a typed context, NOT `@Suppress("LongMethod")`. The split is straightforward when the body already has comment-block phase markers (each `// ── ... ────` block is a natural helper boundary).
- Detekt config awareness: `ignoreAnnotated: ['Test']` only covers `@Test`-annotated methods. Private helpers called by `@Test` methods are NOT covered; treat them as ordinary code subject to the rule.
