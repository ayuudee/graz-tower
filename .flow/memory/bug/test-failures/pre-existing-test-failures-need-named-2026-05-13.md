---
title: "Pre-existing test failures need named register entries, not per-task narrative c"
date: "2026-05-13"
track: bug
category: test-failures
module: docs/deferments.md
tags: [fn-16, fn-16.2, deferment-register, r12-acceptance, impl-review, narrative-vs-spec, d-world.2]
problem_type: test-failure
symptoms: codex impl-review NEEDS_WORK on R12 'build green' when spec command includes a pre-existing failure
root_cause: narrative carve-out in evidence doesn't change what the acceptance criterion contractually demands
resolution_type: fix
---

## Problem
fn-16.2's codex impl-review round 2 issued NEEDS_WORK on R12: the task claimed "build verified green" but the full spec command (`./gradlew ... :migration:allTests detekt`) actually fails. The failure is a pre-existing `:migration:jvmTest` failure (`LjmbWorldCandidateValidationTest`) unrelated to fn-16 — same shape observed across fn-5/6.2/9.2/11.2/16.1 — but every prior task documented the carve-out only as narrative evidence ("pre-existing, unrelated, out of scope"). Codex correctly observed: the task's R12 acceptance criterion enumerates the full command, and narrative carve-outs in evidence do not change what the acceptance criterion demands. SHIP cannot land while the evidence claim ("R12 met") contradicts the observed result ("BUILD FAILED").

## What Didn't Work
Round 1's framing was: "the failure is pre-existing per fn-16.1's evidence; fn-16.2 follows the same exclusion; the failure was observed at HEAD `dbf4c8d` before any fn-16.2 edit landed." This factually-correct narrative explanation still failed review because:
1. The R12 acceptance bullet literally said `./gradlew ... :migration:allTests detekt` must `exit 0`.
2. Narrative carve-outs are reviewer-grade explanation, not spec-grade contracts.
3. The carve-out had been re-derived across 5+ tasks (fn-5/6.2/9.2/11.2/16.1) with no shared canonical entry — high evidence of "this needs a structural fix, not another narrative."

## Solution
Path: promote the standing narrative carve-out to a named deferment register entry. Two-step fix in `docs/deferments.md` + task spec:
1. Filed `D-WORLD.2 — LjmbWorldCandidateValidationTest pre-existing failure` as `Status: blocked` with the four-field contract (Why / Pinned at / Blocked on / Closes by) per `docs/deferments-CONVENTION.md`. `Blocked on:` cites the LJMB-candidate-authoring pass that will resolve the validator's expectations.
2. Flipped the R12 acceptance checkbox from `[ ]` to `[~]` (partial) with a cross-reference to `D-WORLD.2`. Done summary + evidence narrative updated to call out R12 PARTIAL.

After landing, codex round 3 SHIP'd with: `R12: deferred — fn-16-relevant targets are green; full command is blocked only by pre-existing D-WORLD.2, now filed in docs/deferments.md.` Classification flipped from `1 introduced` to `0 introduced, 1 pre_existing` — the reviewer correctly demoted the failure to pre-existing once the named deferment exists.

## Prevention
**Promote cross-epic narrative carve-outs into named register entries.** When the same "pre-existing, unrelated" exclusion shows up in 3+ task evidence sections, that's evidence of a structural register gap, not a per-task narrative concern. The four-bucket model in `docs/deferments-CONVENTION.md` exists for exactly this — a named entry with `Status: blocked` + `Blocked on:` lets future epics cite a single deferment instead of re-deriving the rationale.

Detection rule for future planning passes: when an epic's R12 (or similar full-build acceptance) inherits a "full gradle build must pass" shape AND the project has any known-failing test on `main`, the planning pass should file the known failure as a deferment FIRST, then write the R12 acceptance to either (a) exclude the deferment's target task, or (b) accept `[~] partial — blocked by <deferment-id>` as a SHIP-eligible state. This pattern is now structurally anchored at `D-WORLD.2`; future epics inheriting the standard R12 shape can opt into the partial-with-D-WORLD.2 framing without re-deriving.
