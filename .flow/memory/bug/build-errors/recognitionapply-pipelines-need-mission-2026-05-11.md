---
title: Recognition+apply pipelines need mission-shape agreement at the derivation site
date: "2026-05-11"
track: bug
category: build-errors
module: pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt
tags: [fn-14, pilot, recognition-apply, fail-closed, mission-shape, go-around]
problem_type: build-error
symptoms: Recognition fires on mission shapes the apply silently no-ops; hysteresis breaks; transmission emitted without effect
root_cause: Derivation site reused aerodrome-resolution helper that accepts more goal shapes than the apply's subtree-rewrite predicate supports
resolution_type: fix
related_to: [bug/build-errors/ga-path-precedence-reorder-when-adding-2026-05-10]
---

## Problem

When extending a recognition→apply pipeline with a new pure-derivation
event leaf (axis 1 of `PilotEvent`), it is not enough to mirror the
existing `windForMission`/`atisLetterForCallInbound` aerodrome-resolution
shape and the existing `applySelfInitiatedGoAround` subtree-rewrite
shape. The two have **different domain coverage**:

- `windForMission` resolves wind for any `HighLevelGoal` that carries
  a destination, including `Transit`.
- `applyCrosswindGoAround`'s `replaceChild { isCircuitLike }` predicate
  matches ONLY `Circuit`/`CircuitAfterGoAround`/`TouchAndGo` subtrees
  — not `Transit` compounds (where in twr2 the arrival primitives like
  `FLY_FINAL` are direct children of the `Transit` compound, with no
  intermediate Circuit wrapper).

The mismatch made the recognition fire on Transit-arrival missions
while the apply silently rewrote nothing. The pilot would emit
`Report(GoingAround)` (the transmission line is unconditional in the
apply body), but `currentStep` would remain in the crosswind-eligible
step set, so the event would re-fire every tick — hysteresis broken,
controller-side commitment regression triggered but pilot never
actually goes around.

Codex round-1 review caught this as a MAJOR finding before the sim
integration test (fn-14.2) would have surfaced it.

## What Didn't Work

Initial implementation assumed "if windForMission resolves AND there's
an active runway AND the step is eligible, the recognition is sound."
This implicitly relied on the trained-GA + ATC-reactive precedent
where the mission shape was always CircuitTraining > Circuit (mission
shape was a coincidence of the test fixtures, not a guarded
invariant). Real production missions include `Transit` shapes too,
where the assumption fails.

## Solution

Add an explicit **mission-shape guard** at the derivation site —
mirror the applier's predicate to prevent the recognition firing on
mission trees the applier cannot rewrite:

```kotlin
val activeCompoundName = mission.root.activeCompound()?.name
if (activeCompoundName == null || !activeCompoundName.isCircuitLike()) return null
```

The recognition and apply now agree on which mission shapes they
support; recognition fails closed on unsupported shapes rather than
firing into a no-op apply.

Also fixed in same pass: `RunwayId.headingDegreesMagnetic()` previously
accepted a single-char designator (`"5"` → 50) instead of failing
closed on incomplete designators (ICAO Annex 14 requires two digits).
Same class of finding: the parser silently accepted partial input
rather than refusing it.

## Prevention

When extending a recognition→apply pipeline:

1. **List the apply's preconditions explicitly** (here: the subtree
   predicate, the reset target, the transmission). Each becomes a
   gate the recognition must enforce too — otherwise the recognition
   can fire into an effective no-op.

2. **Audit cross-goal aerodrome resolution helpers** (here:
   `windForMission` mirrors `atisLetterForCallInbound`) to see what
   shapes they accept. The helper resolving a key doesn't mean the
   downstream apply supports that shape.

3. **Add a Transit-shape negative test row** alongside the positive
   Circuit-shape rows in the derivation matrix. Once the parametric
   "this mission shape doesn't have a recovery subtree" gate exists,
   the test forces it to be considered for every new event leaf.

4. **Fail closed on partial parse**: when a typed value is composed
   of multiple required parts (here: two-digit Annex 14 designator),
   require all parts. Single-char tolerance hides typos that should
   surface loudly. Pin the negative test row in the parse helper.
