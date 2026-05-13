---
title: "Long-spec flow-next tasks: review-loop cycles on spec-md artifact mismatches"
date: "2026-05-13"
track: bug
category: build-errors
module: .flow/tasks/fn-18-deferment-register-reorganization-four.2.md
tags: [fn-18, flow-next, impl-review, codex, evidence-block, task-state-synchronization, review-loop, multi-round]
problem_type: build-error
symptoms: "codex impl-review cycles NEEDS_WORK across 5 rounds on the same long-spec task, each round surfacing a different artifact mismatch (stale BLOCKED narrative, empty Evidence block, .json status=todo, set-cardinality inconsistency)"
root_cause: "flowctl done has side effects on the task spec md (replaces Done summary, resets Evidence); static task .json doesn't track runtime status; worker contract doesn't account for the post-done state-synchronization round needed for codex to see a self-consistent diff"
resolution_type: fix
related_to: [bug/build-errors/recognitionapply-pipelines-need-mission-2026-05-11]
---

## Problem
On long-spec flow-next tasks where `flowctl done` writes its own canonical summary
into the task spec md AND the prior-worker block-narrative leaks into the same
file, the impl-review backend (codex) cycles repeatedly on "spec md state doesn't
match implementation state." Each round surfaces a different artifact mismatch:

1. Round 1: stale BLOCKED narrative at the bottom of the task md.
2. Round 2: placeholder `## Done summary` / empty `## Evidence` block while
   commits say "shipped."
3. Round 3: on-disk task .json file shows `status: "todo"` while
   `flowctl show --json` reports `status: "done"` (the static def file is the
   initial-state snapshot; live state lives in `.git/flow-state`).
4. Round 4: `flowctl done` strips the worker-authored detailed Done summary
   and replaces it with the canonical summary-file content; the Evidence block
   gets reset to empty placeholders.
5. Round 5: Set-A cardinality (53) ≠ OPEN (32) + closed-from-pf list size
   (which equals docs/deferments.md archive size, not |Set A closed| = 21).

## What Didn't Work
- Running `flowctl done` first and assuming the static .json file would reflect
  live state. It does NOT — the static def file is the initial-state snapshot;
  flowctl runtime reads from `.git/flow-state`.
- Trying to embed comprehensive evidence in the task md before `flowctl done`.
  flowctl done REPLACES the `## Done summary` section with the `--summary-file`
  content and RESETS the `## Evidence` block to empty placeholders. Re-populate
  Evidence AFTER `flowctl done` runs.

## Solution
The worker contract has an implicit chicken-and-egg on long-spec tasks where
review correctness depends on artifacts that `flowctl done` produces. The
working sequence:

1. Write implementation + commit.
2. Run impl-review; iterate on code/spec findings.
3. Once code is clean, run `flowctl done` with comprehensive summary + evidence
   JSON.
4. Manually flip the static task .json `status` to `done` and re-populate the
   `## Evidence` block in the task md with the structured content (it gets
   reset by flowctl done). Commit this as a "post-done evidence backfill."
5. Final impl-review round to verify the diff is internally consistent.

The codex reviewer will keep finding artifact mismatches until the diff is
internally coherent — meaning the task md, the static def .json, the docs,
and the production code all tell the same story.

For long-spec tasks: pre-allocate 5+ review rounds in the worker budget.

## Prevention
- **Worker contract clarification**: when REVIEW_MODE \!= none on long-spec
  tasks, plan for: (a) initial review round to validate code/docs; (b) `flowctl
  done` invocation; (c) static-state synchronization round (.json status flip,
  Evidence block re-populate); (d) final review round to SHIP.
- **Codex visibility**: the reviewer cannot see `.git/flow-state` — only the
  diff. The diff MUST embed everything needed for self-consistency including
  the structured locked inventory, set-boundary cardinalities, MIGRATED header
  text, and per-test R16 value-flow roll-up. Embedding the same content in
  both the flowctl evidence JSON AND the task md `## Evidence` block is
  intentional redundancy, not duplication.
- **Set-cardinality math**: |Set A| should equal |OPEN| + |closed-in-set-A|.
  The closed-in-set-A count may exceed the docs/archive count by the number
  of unanchored closed entries (no inline refs, no orphan test) — document
  those explicitly as Decision #3 scope-exclusions.
- **flowctl done overwrites**: don't pre-populate Done summary or Evidence
  in the task md before `flowctl done`. Use --summary-file for the canonical
  summary; backfill Evidence after.
