---
title: "Pre-existing test failures need named deferment-register entries, not carve-outs"
date: "2026-05-15"
track: knowledge
category: best-practices
module: docs/deferments.md
tags: [deferment-register, r12-acceptance, impl-review, narrative-vs-spec, cross-epic-pattern, fn-16, fn-19]
applies_when: Authoring an epic spec whose R-criteria inherit a "full gradle build must pass" shape AND the project has any known-failing test on `main`, OR reviewing a task's evidence that includes a "pre-existing, unrelated" carve-out paragraph that has appeared in 2+ prior epic spec evidence sections.
related_to: [bug/test-failures/pre-existing-test-failures-need-named-2026-05-13]
---

## The rule

A test failure that is "known pre-existing, unrelated, out of scope" should
be promoted to a **named entry in `docs/deferments.md`** (e.g.
`D-WORLD.N`) **after the second cross-epic occurrence**. Narrative
carve-outs in task evidence are reviewer-grade explanation; the named
register entry is the spec-grade contract that lets future tasks accept
`[~] partial — blocked by <deferment-id>` as a SHIP-eligible state.

Carve-out narrative propagating across 3+ epics is a smell, not a pattern.

## Why it bites

`bug/test-failures/pre-existing-test-failures-need-named-2026-05-13.md`:
fn-16.2's codex impl-review round 2 NEEDS_WORK on R12: the task claimed
"build verified green" but the full spec command
(`./gradlew ... :migration:allTests detekt`) actually fails. The failure
was `:migration:jvmTest LjmbWorldCandidateValidationTest`, a pre-existing
failure unrelated to fn-16 — observed at HEAD `dbf4c8d` before any
fn-16.2 edit landed.

Same failure had been carved out across **5+ epics** (fn-5, fn-6.2, fn-9.2,
fn-11.2, fn-16.1) with no shared canonical entry. Every prior task
documented the carve-out only as narrative evidence.

Codex's observation was correct and structural:

1. The R12 acceptance bullet literally said `./gradlew ... :migration:allTests
   detekt` must `exit 0`.
2. Narrative carve-outs are reviewer-grade explanation, not spec-grade
   contracts.
3. The carve-out had been re-derived 5+ times with no shared anchor —
   evidence of a structural register gap.

Fn-16.2's fix (two-step):

1. File `D-WORLD.2 — LjmbWorldCandidateValidationTest pre-existing failure`
   in `docs/deferments.md` as `Status: blocked` with the four-field
   contract (Why / Pinned at / Blocked on / Closes by) per
   `docs/deferments-CONVENTION.md`.
2. Flip the R12 acceptance checkbox from `[ ]` to `[~]` (partial) with a
   cross-reference to `D-WORLD.2`. Done summary + evidence narrative call
   out R12 PARTIAL.

Codex round-3 then SHIP'd: classification flipped from `1 introduced` to
`0 introduced, 1 pre_existing` — the reviewer correctly demoted the
failure to pre-existing once the named deferment existed.

The fn-19 epic later closed `D-WORLD.2` end-to-end. The named register
made the closure scope discoverable.

## When this applies

- **Authoring an epic spec** whose R-criteria inherit a "full gradle build
  must pass" shape AND the project has any known-failing test on `main`.
- **Reviewing a task's evidence** that includes a "pre-existing, unrelated"
  carve-out paragraph. Search the last 5 epic spec evidence sections for
  the same failure name — if it appears 2+ times, file the register
  entry before the third occurrence.
- **Planning-pass triage** when a target task's spec command is broader
  than the task's actual scope.

## Forward-applicable checklist

When a known-failing test is being skipped for the second time in a new
epic's R-acceptance evidence:

1. **File the named deferment FIRST.** Use the four-field contract
   from `docs/deferments-CONVENTION.md`:
   - **Why:** one-paragraph explanation of what the failure is and why it
     pre-exists this epic.
   - **Pinned at:** SHA where the failure was first observed pre-existing.
   - **Blocked on:** the future epic / task / verification that resolves it.
   - **Closes by:** the criterion under which the deferment becomes a
     closed entry (e.g. "when D-WORLD-pass-X lands" or "when target
     validator's expectations are re-verified against the locked corpus").
2. **Then write the R-acceptance** in either of two SHIP-eligible shapes:
   - **(a) Exclude:** narrow the R-criterion's spec command to skip the
     deferred target. The carve-out is now in the spec command itself,
     not in the narrative.
   - **(b) Partial:** accept `[~] partial — blocked by <deferment-id>`
     as a SHIP-eligible state, with the deferment-id cross-referenced.
3. **Do not repeat the carve-out as narrative-only.** If you're about to
   write "pre-existing, unrelated, out of scope" in evidence and the
   same failure has been carved out before, stop and file the register
   entry. The reviewer will catch this on round 2 anyway.

Detection rule for future planning passes: at plan-review time, grep
`docs/deferments.md` for any failure named in the task's R-evidence. If
the failure isn't in the register but has been mentioned in 2+ prior
epic evidence sections (search `.flow/specs/*.md` and `.flow/tasks/*.md`),
queue a planning-pass deferment-file task before R-implementation.

## Cross-references

- Convention spec: `docs/deferments-CONVENTION.md` (the four-field contract)
- Closed deferment: `D-WORLD.2` (filed by fn-16.2, closed by fn-19)
- Source capture (kept as authoritative event record):
  `.flow/memory/bug/test-failures/pre-existing-test-failures-need-named-2026-05-13.md`
