---
title: GA-path precedence reorder when adding ATC-reactive path to pilotDecide
date: "2026-05-10"
track: bug
category: build-errors
module: pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt
tags: [fn-12.2, pilot, go-around, precedence, fork-point, fn-28, fn-28.1, density-altitude, helper-shape-mirror, codex-impl-review, sibling-helper, goal-resolution]
problem_type: build-error
symptoms: ATC-reactive Tick A intent masked by self-initiated when both triggers active
root_cause: derivePilotEvent ran before ATC recognition; intent precedence chain not reordered
resolution_type: fix
last_updated: "2026-05-16"
---

## Problem

When introducing a new GA path (ATC-reactive) to `pilotDecide` alongside the
existing self-initiated path (`derivePilotEvent` →
`applySelfInitiatedGoAround`) and trained-GA path
(`applyPlannedGoAround`), the original ordering left
`derivePilotEvent` running BEFORE the new ATC-reactive recognition.

Result: a mission with `pendingAtcGoAroundFrom = Some(REPORT_FINAL)` on
REPORT_FINAL with no clearance at decision altitude would fire
`DecisionAltitudeWithoutClearance` first; the elvis-chain in `Skip` arm
then preferred `goAround?.intent` over `atcGoAroundOutcome?.intent` —
producing the self-initiated response instead of the ATC-issued Tick A
response.

## What Didn't Work

- Initial implementation kept `derivePilotEvent` at line 104 (its
  pre-fn-12 location) and added the ATC-reactive recognition AFTER it.
- The intent precedence chain in the `Skip` arm preferred `goAround`
  first.
- Both arms checked `null`-guards on each other but didn't enforce a
  proper chain — `atcGoAroundOutcome` ran always (necessary for flag
  clear), but `goAround` had no guard against `atcGoAroundOutcome.intent`.

## Solution

Reorder `pilotDecide` to match spec R9c:
1. **Trained-GA first** — `preStep == FLY_FINAL_TO_SHORT_FINAL && currentStep == GOING_AROUND`.
2. **ATC-reactive next** — only when `plannedGoAround == null` (short-circuit per spec).
3. **Self-initiated last** — only when `plannedGoAround == null && atcGoAroundOutcome?.intent == null`.

Mirror in the intent-precedence chain (`Skip` arm):
`plannedGoAround?.intent ?: atcGoAroundOutcome?.intent ?: goAround?.intent ?: ...`

Single-cycle flag-clear invariant survives via post-fold unconditional
`.copy(pendingAtcGoAroundFrom = None)` on whichever path's mission won.

`pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:104-159`

## Prevention

When adding a new path to a fork-point function with multiple existing
paths:
- Re-derive precedence from the spec FIRST; do not just append.
- Audit ALL elvis chains and short-circuit guards to ensure ordering matches.
- Write a "both-trigger-active" pin that distinguishes the new path from
  each of the existing paths via observable side effects (intent shape,
  mission state delta) — this catches precedence regressions immediately.
- For paths with similar intent shapes (e.g. trained-GA and ATC-reactive
  both produce `phase=Final, route=None`), discriminate via reset-sensitive
  sentinel state: trained-GA calls `resetForGoAround` and clears
  `hasClearance` etc.; ATC-reactive does NOT, so seeded `hasClearance=true`
  survives only the latter.

## Update 2026-05-16

## Problem
A new helper that resolves "the relevant aerodrome for this pilot decision" copy-pasted the goal-by-goal lookup shape from a similar sibling helper (`windForMission` → `densityAltitudeInputForMission`) without re-validating that the **mission-side** semantic of "destination aerodrome" matched the **decision-side** semantic of the new recognition axis. For density-altitude decline (a pre-taxi, apron-side decision), the relevant aerodrome is where the pilot is AT, not where they are going TO. Mirroring `g.destination` for `Departure` / `Transit` would silently pick the wrong aerodrome for multi-aerodrome scenarios — and the fail-closed singleton-fallback would mask the defect in single-aerodrome test fixtures.

## What Didn't Work
"Sibling helper shape mirror" without a semantic re-check at each branch. `windForMission` resolves wind for a flying-on-final crosswind/tailwind recognition — the relevant aerodrome IS the destination (the runway being approached). DA decline is the inverse — the relevant aerodrome is the apron, not the destination. The two helpers look identical structurally but represent opposite decision-side concerns.

## Solution
Drop the goal-keyed lookup entirely for the DA helper. All four `HighLevelGoal` branches resolve to `null` explicit-key lookup; the singleton-fallback covers the single-aerodrome golden's path. Multi-aerodrome DA is filed as `D-PASS-g3b-react-density-altitude` for a future plan-review decision between three possible departure-aerodrome sources (new goal field / `aircraft.positionPoint` lookup / `FiledPlan` thread). Critical regression-pin test asserts that `Departure(destination=ljmb)` with a multi-aerodrome map returns `null`, NOT `ljmb`'s DA input.

## Prevention
When adding a new "X for mission" helper, ask explicitly: "is the decision-side concern (where the pilot is recognising X) the same as the mission-side concern (where the goal is going)?" If they differ, the goal-keyed lookup is wrong; either thread a different aerodrome source OR fail closed and file a deferment. Don't structurally mirror an existing helper without re-validating the semantic at each `when` arm. Pre-commit checklist for sibling-helper additions:
- Does each goal branch resolve to the aerodrome the decision actually concerns?
- Is the singleton-fallback semantically correct, or just structurally convenient?
- Does the test suite include a multi-aerodrome scenario that would catch a destination/departure mix-up?
