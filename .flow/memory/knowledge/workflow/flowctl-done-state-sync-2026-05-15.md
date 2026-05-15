---
title: "flowctl-done state-sync: post-done sweep before re-invoking impl-review"
date: "2026-05-15"
track: knowledge
category: workflow
module: .flow/tasks
tags: [flow-next, flowctl, flowctl-done, impl-review, codex, evidence-block, task-state-synchronization, review-loop, multi-round, fn-18]
applies_when: Completing a long-spec flow-next task with REVIEW_MODE != none and a codex backend — specifically, the post-`flowctl done` window before re-invoking impl-review. Also applies when the task md carries `## BLOCKED:` / `## Done summary` / `## Evidence` sections that the worker pre-populated before `flowctl done`.
related_to: [bug/build-errors/long-spec-flow-next-tasks-review-loop-2026-05-13]
---

## The rule

`flowctl done` has **side effects on the task spec markdown** that the
worker contract does not implicitly account for. After invoking
`flowctl done` on a long-spec task, run a **post-done
state-synchronization sweep** BEFORE re-invoking codex impl-review:

1. Clear any stale `## BLOCKED:` / `## NEEDS_WORK:` narrative from the
   task md (carried over from a pre-reset worker state).
2. Confirm `## Done summary` carries the substantive description (not
   the flowctl placeholder).
3. Populate `## Evidence` with actual values (commits, test results) —
   not literal `<from base-sha.txt>` placeholder templates.
4. Verify `.json status` field is `done` (not the worker's earlier
   `in_progress` / `todo` — the static def file is a snapshot; live state
   lives in `.git/flow-state`).
5. Internal-consistency check: counts in the Done summary match counts
   in Evidence; commit SHAs in Done summary appear in Evidence.

Otherwise codex impl-review will cycle on a cascade of artifact mismatches
— each fix unblocks the next, eating 3–5 review rounds.

## Why it bites

`bug/build-errors/long-spec-flow-next-tasks-review-loop-2026-05-13.md`:
fn-18.2 burned **5 codex impl-review rounds** on the same long-spec task,
each surfacing a different artifact mismatch:

1. **Round 1:** stale BLOCKED narrative at the bottom of the task md
   (left over from a pre-`flowctl done` worker-block state).
2. **Round 2:** placeholder `## Done summary` / empty `## Evidence` block
   while commits already say "shipped."
3. **Round 3:** on-disk task `.json` file shows `status: "todo"` while
   `flowctl show --json` reports `status: "done"`.
4. **Round 4:** `flowctl done` strips the worker-authored detailed Done
   summary and replaces it with the canonical `--summary-file` content;
   the Evidence block gets reset to empty placeholders.
5. **Round 5:** Set-A cardinality (53) ≠ OPEN (32) + closed-from-pf list
   size (which equals docs/deferments.md archive size, not |Set A
   closed| = 21) — internal-consistency drift between sections that were
   written at different times.

The generator: `flowctl done` REPLACES `## Done summary` with the
`--summary-file` content and RESETS `## Evidence` to empty placeholders.
Pre-populating these sections before `flowctl done` is lost work. The
correct sequence puts the evidence backfill AFTER `flowctl done` runs.

The reviewer cannot see `.git/flow-state` — only the diff. The diff
must embed everything needed for self-consistency: structured locked
inventory, set-boundary cardinalities, MIGRATED header text, per-test
R-criterion roll-ups. Embedding the same content in both the flowctl
evidence JSON AND the task md `## Evidence` block is **intentional
redundancy**, not duplication.

## When this applies

- **Long-spec flow-next tasks** (those with `## BLOCKED:` / `## Evidence`
  / `## Done summary` sections in the task md AND non-trivial diff
  content).
- **Multi-round impl-review tasks** where REVIEW_MODE != none and the
  reviewer is codex (codex sees the diff, not flowctl state).
- **Any task where the worker invoked `flowctl done` mid-cycle** (e.g.
  before a final review round to verify the post-done diff is
  internally coherent).

## The working sequence

For long-spec tasks with REVIEW_MODE != none:

1. **Write implementation + commit.** Don't pre-populate `## Done summary`
   or `## Evidence` in the task md before `flowctl done`.
2. **Run impl-review; iterate on code/spec findings** until the
   implementation is clean. Use scoped-diff base (`BASE_COMMIT..HEAD`)
   so the reviewer sees only this task's changes.
3. **Run `flowctl done` with comprehensive `--summary-file` and
   `--evidence-json`.** Compute concrete values BEFORE writing the
   evidence JSON — no literal `<placeholder>` strings (this rule's own
   `flow-next` write-back of placeholder templates is the trap).
4. **Post-done state-synchronization sweep:**
   - Clear stale `## BLOCKED:` / `## NEEDS_WORK:` narrative from the
     task md.
   - Re-populate the `## Evidence` block in the task md with the
     structured content (`flowctl done` resets it to placeholders;
     copy the structured evidence from your evidence JSON into the
     task md `## Evidence` section).
   - Confirm `flowctl show <task-id> --json | jq .status` == `"done"`.
   - Internal-consistency check: counts in `## Done summary` match
     counts in `## Evidence`; commit SHAs in `## Done summary` appear
     in `## Evidence`.
5. **Final impl-review round** to verify the post-sync diff is internally
   coherent.

## Forward-applicable checklist

Before re-invoking codex impl-review after `flowctl done`:

1. **No `<placeholder>` strings in evidence.** Grep the task md and the
   evidence JSON for literal `<...>` placeholder fragments. Compute
   concrete values via shell interpolation (`base_sha="$(cat
   "$TMPDIR/<task-base-sha>.txt")"` etc.).
2. **No stale `## BLOCKED:` narrative.** The task md should reflect the
   final shipped state; if a `## BLOCKED:` section is still present,
   the reviewer will see "task is blocked" alongside "task is done" and
   cycle on the contradiction.
3. **`## Evidence` populated, not empty.** `flowctl done` resets this
   block; backfill from the structured evidence JSON.
4. **`.json status` matches live state.** `flowctl show <task-id> --json
   | jq .status` should be `"done"`. The on-disk static `.json` is a
   snapshot; if you've edited it manually, confirm it matches.
5. **Internal-consistency math.** Set cardinalities, file counts, commit
   SHA references — all must agree across the task md, the docs, and
   the evidence JSON. The diff is the reviewer's only ground truth.

## Anti-pattern

> Re-invoking codex impl-review after `flowctl done` without running the
> sync sweep produces 3-5 rounds of cascading artifact-mismatch findings.
> Each round fixes one mismatch and reveals the next. Pre-allocate the
> sync sweep before the review re-invocation; do not let it eat
> reviewer rounds.

## Cross-references

- Source capture (kept as authoritative event record):
  `.flow/memory/bug/build-errors/long-spec-flow-next-tasks-review-loop-2026-05-13.md`
- Related workflow shape: `recognitionapply-pipelines-need-mission` (the
  scoped-diff-base discipline for multi-task epics).
