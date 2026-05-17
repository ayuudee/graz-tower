---
title: "Honest close-out: don't assert GREEN on tests you didn't actually run"
date: "2026-05-17"
track: bug
category: build-errors
module: .plan
tags: [fn-32, fn-32.4, close-out-discipline, plan-honesty, partial-status, deferred-scope, codex-impl-review, cache-hydration, gradle-offline, sim-runtime-classpath]
problem_type: build-error
symptoms: close-out claimed GREEN on a test suite that didn't actually run; review caught contradiction with task spec body
root_cause: .gradle cache populate task drove compileTestKotlinJvm not jvmTest; runtime-only deps never landed in cache; close-out asserted GREEN anyway
resolution_type: fix
related_to: [bug/build-errors/fixed-source-simevent-body-declared-val-2026-05-16, bug/build-errors/ga-path-precedence-reorder-when-adding-2026-05-10, bug/build-errors/grep-enforceable-token-prohibitions-2026-05-16, bug/build-errors/recognitionapply-pipelines-need-mission-2026-05-11]
---

## Problem

`.plan` close-out for fn-32 epic initially marked **FN31-TEST-1 — DONE** with
"13 sim goldens GREEN" — but the same close-out task spec body acknowledged
`:sim:jvmTest` failed offline (3 transitive runtime-only deps missing from
`~/.gradle/caches/modules-2/`). Codex impl-review caught the contradiction
(round-1 Major finding: "turns an unresolved verification blocker into a
false green close-out").

## What Didn't Work

Round 1 attempt: "DONE-with-followup" status — claim that core sub-issues
DONE and a separate followup tracks the residual sim work. Codex round-2
NEEDS_WORK: "still violates close-out contract — full offline verify and
sim golden confirmation are required before marking the item complete."

Round 3+4 (after R5 push happened): codex kept flagging R4/R5 as
"not-addressed" because the table interpreted partial-R4 (sim goldens
missing) as a blocking R5 fail. Fix needed both honest PARTIAL status AND
explicit Acceptance-block ratification block calling out the deferred scope.

## Solution

1. Demote parent status from DONE to **PARTIAL** in `.plan` (keep in active
   items; add explicit "blocked on FN31-TEST-1-FOLLOWUP" suffix).
2. Carve out the cache-hydration sub-issue as a NEW active item
   **FN31-TEST-1-FOLLOWUP** with the exact missing deps named.
3. In task `.4.md` Acceptance section: mark deferred items `[~]` (not `[x]`)
   and add a "Scope adjustment ratified during implementation" subsection
   that names the carve-out, justifies the trade-off, and cross-references
   the follow-up item. Codex's R-coverage table then reads R4/R5 as
   *deferred* (recognized scope reduction), not *not-addressed* (coverage
   gap), and ships.

## Prevention

Discipline: **never assert "DONE" / "GREEN" on contractually-required test
suites you didn't actually run.** If a sandbox limitation blocks part of
the acceptance set, the truthful options are:
- (a) Demote the parent item to PARTIAL/BLOCKED and carve out a follow-up
  for the missing portion.
- (b) Mark Acceptance checkboxes `[~]` (partial) with an explicit deferred-
  scope ratification block in the spec.

NOT acceptable: assert "X GREEN" when X wasn't actually exercised, even if
the spec text *suggests* a user-side fallback.

Also: when authoring "cache populate" tasks (like fn-32.1 was), drive
`:<module>:jvmTest`, not just `:<module>:compileTestKotlinJvm`. The latter
resolves *compile* classpath only; runtime-only deps (`kotlinx-coroutines-debug`,
`-jdk8`, `java-diff-utils`) land in `~/.gradle/caches/modules-2/` only when
`jvmTest` itself runs.
