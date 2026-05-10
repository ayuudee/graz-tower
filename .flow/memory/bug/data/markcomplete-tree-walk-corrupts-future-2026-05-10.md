---
title: markComplete tree-walk corrupts future outcomes when active compound lacks step
date: "2026-05-10"
track: bug
category: data
module: pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt
tags: [htn, mission-tree, markComplete, future-state-corruption, circuit-outcome]
problem_type: data
symptoms: ClearedToLand during trained-GA marks recovery FullStop circuit's FLY_FINAL complete before that circuit becomes active
root_cause: "CompoundTask.markComplete walks depth-first marking the FIRST incomplete instance globally; when the active outcome's compound lacks the named step, walk steps past it and corrupts a future outcome"
resolution_type: fix
---

## Problem

`CompoundTask.markComplete(step)` walks the tree depth-first and marks
the **first incomplete instance** of a step. When called per-step in a
fold (e.g. `handleLandingClearance` marking
`[AWAIT_SEQUENCING, FLY_BASE, FLY_FINAL, AWAIT_LANDING_CLEARANCE]`), the
walk silently steps **past** the active outcome's compound when that
compound's step vocabulary doesn't include the step — and marks the
SAME-NAMED primitive in a future outcome's compound, corrupting state
that hasn't become active yet.

For the trained-GA `plannedGoAroundCircuitTask`, this manifested as:
the active outcome's `Circuit` compound carries
`FLY_FINAL_TO_SHORT_FINAL` (NOT `FLY_FINAL`); a `ClearedToLand` arriving
during that step would have `markComplete(FLY_FINAL)` walk past the
trained-GA compound entirely and mark the recovery `FullStop` outcome's
`FLY_FINAL` — corrupting the next circuit before the pilot ever flew it.

## What Didn't Work

- First attempt: just remove `FLY_FINAL_TO_SHORT_FINAL` from
  `handleLandingClearance`'s `stepsToMark` list. This prevented the new
  step from being skipped on clearance, but didn't address the
  underlying problem that `markComplete` corrupts future state for
  ANY step the active outcome doesn't carry.

## Solution

Introduce a **scoped** `markCompleteInActiveCompound(root, steps)`
helper that:
- Finds the leftmost-incomplete top-level child of root.
- If it's a compound, scope marking to that compound only (steps the
  active compound doesn't carry are no-ops).
- If it's a primitive (e.g. Transit's flat-shape root with FLY_BASE
  / FLY_FINAL / etc as direct children of root), fall back to root-
  level marking — root IS the smallest enclosing compound for those
  primitives.

Site: `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt`
`handleLandingClearance` + sibling `markCompleteInActiveCompound` helper.

## Prevention

When using `markComplete` (or any tree-walker that finds "first
incomplete instance" globally), check whether the tree has multiple
copies of the target step name across sibling outcomes. If yes, either:

1. Scope the walk to the active outcome explicitly (preferred).
2. Use a tighter predicate that includes the active-outcome boundary.
3. If a single mark-call really IS meant to walk the whole tree
   (e.g. resetForGoAround's structural reset), document why explicitly.

Future-circuit-preservation tests are the canonical regression pin:
when an instruction triggers step-marking, assert the
not-yet-active outcomes' equivalents are still INCOMPLETE.

Related: any time a sealed `MissionStep` enum has a "variant by
outcome" pattern (FLY_FINAL vs FLY_FINAL_TO_SHORT_FINAL), the global
markComplete is fragile by construction.
