---
title: GA-path precedence reorder when adding ATC-reactive path to pilotDecide
date: "2026-05-10"
track: bug
category: build-errors
module: pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt
tags: [fn-12.2, pilot, go-around, precedence, fork-point]
problem_type: build-error
symptoms: ATC-reactive Tick A intent masked by self-initiated when both triggers active
root_cause: derivePilotEvent ran before ATC recognition; intent precedence chain not reordered
resolution_type: fix
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
