---
title: Recognition+apply pipelines need mission-shape agreement at the derivation site
date: "2026-05-11"
track: bug
category: build-errors
module: pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt
tags: [fn-14, pilot, recognition-apply, fail-closed, mission-shape, go-around, fn-14.2, impl-review, scoped-diff, r13-doc-closure, multi-task-epic, fn-28, fn-28.2, phase-guard, cross-path-contract, focused-seam, codex-impl-review, sealed-when-exhaustiveness, density-altitude]
problem_type: build-error
symptoms: Recognition fires on mission shapes the apply silently no-ops; hysteresis breaks; transmission emitted without effect
root_cause: Derivation site reused aerodrome-resolution helper that accepts more goal shapes than the apply's subtree-rewrite predicate supports
resolution_type: fix
last_updated: "2026-05-16"
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

## Update 2026-05-11

## Problem

Codex impl-review is scoped to the task's BASE_COMMIT..HEAD diff. When
a spec's doc-update list mentions files that were already updated in
an earlier task within the same epic (e.g. fn-14.1's foundation pass
touched PilotEvent.kt + AircraftType.kt KDocs for the new
`maxCrosswindKnots` field), the fn-14.2 cross-reference closure pass
that ALSO lists those files in its R13 contract will see codex flag
them as missing — because the substantive edits aren't visible in
fn-14.2's diff range.

The earlier task's edits are correct; the spec contract is met in
the working tree. But the review verdict is per-diff, not per-tree,
so the codex review legitimately can't confirm the closure.

## What Didn't Work

Skipping the cross-reference docs in fn-14.2 because "fn-14.1 already
did them" — codex correctly NEEDS_WORK'd because the doc-closure
fingerprint isn't visible in the fn-14.2 commit.

## Solution

When the closing task in an epic owns a "10-cross-reference-sites"
R13-style contract, the closing task MUST land *some* visible edit on
every contracted file, even if the substantive content was authored
earlier. Two safe patterns:

1. Append the new sim-test or sibling anchor to the file KDoc as
   "End-to-end coverage" / "Sibling tests" cross-link — visibly in
   the closing task's diff. This is the cleanest hook.
2. Sharpen the prior KDoc with an explicit cite that the prior edit
   left implicit (e.g. fold a doctrine source citation that fn-14.1
   handled via inline-only comments into the file-level KDoc).

Codex round-2 then SHIPs because each R13 site has a visible edit
in the closing task's diff range. The substantive content was
authored once (in the foundation task); the closing task just
makes that content visible in its own diff range for the
review-scope contract.

## Prevention

When planning the closing task of an epic with an R13-style cross-
reference contract:

1. **Cross-check the contract against earlier tasks' diffs**. If a
   file in the contract was already touched by an earlier task in
   the epic, plan an EXPLICIT additional edit on that file in the
   closing task — typically a forward-link to the sim test or
   sibling anchor the closing task adds.

2. **Surface the closing task's contribution in every contracted
   file**. Even doc-only "cross-link" edits count — codex doesn't
   blink at "End-to-end coverage: <new test path>" appended to a
   file KDoc, but it WILL flag the absence of any edit at all.

3. **Don't argue with the review-scope contract**. The reviewer
   reviews a diff; the diff is the artifact. "It's already there
   from fn-14.1" is correct but doesn't satisfy the review-scope
   requirement. Adjust the diff, not the review's framing.

## Update 2026-05-16

## Problem
When extending a recognition→apply pipeline with a new pure-derivation
event leaf (here: density-altitude decline as a pilot-side reactive
recognition axis), two adjacent failure modes surfaced in successive
codex impl-review rounds:

1. **Recognition gates only on mission step, not on aircraft physical
   phase.** A type-valid but desynced state — e.g. `mission.currentTask
   = REQUEST_TAXI` paired with `aircraft.phase = Final` (a future
   regression that wired the mission state independently of physical
   phase) — would fire the recognition AND force a terminal mission-
   tree rewrite on an aircraft physically in flight. The mission-shape
   guard alone is not sufficient.

2. **Code contracts that span multiple PilotOutput return paths cannot
   be tested end-to-end if the natural firing conditions are mutually
   exclusive across paths.** The round-13 contract for cognitive-
   suppression required suppression to apply BEFORE every `PilotOutput`
   construction site (`PlanRouteOutcome.Plan` AND `Skip`). But the
   recognition's gates (pre-taxi mission shape + pre-taxi physical
   phase) are structurally disjoint from `planRoute`'s `Plan` branch
   gates (airborne steps + activeRunway / Transit-cruise context). A
   "DA-decline fires AND Plan branch fires simultaneously" fixture is
   impossible to construct.

## What Didn't Work

**Phase-guard miss**: initial implementation gated recognition on the
mission step only (`isDensityAltitudeDeclineEligible` ← step in
{REQUEST_TAXI, TAXI_TO_HOLDING}). Aircraft phase was inspected nowhere
in the derivation site. Codex round-1 caught the desync hazard.

**Plan-path test attempts**: first round-2 fix tried to exercise the
Plan branch via an end-to-end pilotDecide test on a Transit-cruise
fixture, with a `getOrElse { return@Test }` early-out. Two problems:
(a) `return@Test` is not valid Kotlin — `@Test` is an annotation, not
a lambda label — so the test failed to compile; (b) even when it
"worked", the assertion `targetSpeedMps >= 0.0` did not distinguish
Plan from Skip and could pass vacuously.

## Solution

**Phase-guard addition** at the derivation site:
```kotlin
val phaseOk = when (aircraft.phase) {
    is PilotPhase.AtStand, is PilotPhase.Parked,
    is PilotPhase.HoldingShort, is PilotPhase.Taxiing -> true
    is PilotPhase.LinedUp, is PilotPhase.TakeoffRoll,
    is PilotPhase.Climbing, is PilotPhase.Crosswind,
    is PilotPhase.Downwind, is PilotPhase.Base, is PilotPhase.Final,
    is PilotPhase.LandingRoll, is PilotPhase.Vacating,
    is PilotPhase.ClearOfRunway -> false
}
if (\!phaseOk) return null
```
Exhaustive sealed-PilotPhase `when` (Kotlin's sealed-class
exhaustiveness check catches any future phase addition at compile
time). Recognition+apply agreement: both sites use the same physical-
phase + mission-shape gate sets. Negative tests cover airborne phases
+ runway-active phases with eligible mission steps.

**Focused-seam refactor for cross-path contracts**: extract the
suppression logic to a named internal helper:
```kotlin
internal fun applyCognitiveSuppression(
    transmissions: List<PilotTransmission>,
    suppressSameTickCognitive: Boolean,
): List<PilotTransmission> =
    if (suppressSameTickCognitive) emptyList() else transmissions
```
Both `pilotDecide` return branches call this helper once (via
`effectiveCognitiveTransmissions`). Unit tests on the helper directly
cover the suppression logic; the structural code-share in
`pilotDecide` guarantees Plan/Skip parity by construction. No need
for an impossible "all paths fire simultaneously" fixture.

`pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt`
(phase guard); `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:1394-1422`
(focused-seam helper).

## Prevention

When extending a recognition→apply pipeline with a new pure-derivation
event leaf:

1. **List every state axis the apply mutates**, not just the mission
   tree. If the apply rewrites the mission tree, but the apply's
   effect (apron-terminal at-rest intent, climbing-out intent, etc.)
   is only operationally coherent for specific physical phases, the
   recognition site MUST gate on physical phase too. The mission tree
   alone can desync with physical phase via a future regression.

2. **Sealed-when on the typed phase enumeration** at the derivation
   site — not an `if/else` chain. The exhaustiveness check forces
   every PilotPhase to be classified explicitly; future phase
   additions surface at compile time and require deliberate
   review of the recognition gate.

3. **When a code contract spans multiple return paths**, ask: "can
   both paths' firing conditions be satisfied simultaneously?" If no,
   end-to-end testing of the parity is impossible; refactor the
   shared logic into a named internal helper and unit-test the
   helper directly. The structural code-share in the call sites
   guarantees the parity contract by construction.

4. **Don't use `return@<annotation-name>`** — `@Test` and other
   annotations are NOT lambda labels. To early-return from a test
   function, use a guard clause or restructure the test. Kotlin's
   `return` without a label returns from the nearest enclosing
   function (the test method itself), not from a lambda.
