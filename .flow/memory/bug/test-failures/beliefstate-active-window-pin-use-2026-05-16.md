---
title: "BeliefState-active-window pin: use SET/CLEAR cursors, not first downstream emiss"
date: "2026-05-16"
track: bug
category: test-failures
module: sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveMultiAircraftTest.kt
tags: [fn-28, fn-28.5, sim-test, gate-semantics, belief-state-walk, same-cycle-pin, controller-cycle, codex-impl-review, multi-round-fix, upper-bound-discipline]
problem_type: test-failure
symptoms: "Sim test 'while belief X is active, Y must not fire' pin used first downstream emission as upper bound (too narrow); same-cycle cancel-output pin used 'at-or-after' (too loose); both passed green under current code but would not catch late-cycle regressions."
root_cause: Upper-bound-of-active-window approximated via downstream emission timestamp instead of walking the BeliefState slice directly to find the actual CLEAR cursor; same-cycle assertion used pure ordering instead of CONTROLLER_CYCLE_INTERVAL window.
resolution_type: fix
related_to: [bug/test-failures/compound-predicate-test-assertions-2026-05-11, bug/test-failures/inherited-sim-test-gate-semantics-may-2026-05-11]
---

## Problem

When pinning "while belief X is active, Y must not fire" in a sim
test, the upper bound of the active-window matters as much as the
lower bound. Using the FIRST observable downstream emission as the
upper bound is structurally too narrow: the rule that produced the
upstream-belief side-effect can fire repeatedly while the belief is
active, so the first emission sits early in the active window, not
at its boundary. A regression that emits a forbidden Y instruction
several cycles into the active window — but still before the
belief clears — slips past such a narrow gate.

Concretely on fn-28.5's multi-aircraft G3a-react sim golden: the
"no `TurnBase(B)` while go-around belief is active on the runway"
pin used the first post-GA `ExtendDownwind(B)` record as the upper
bound. `ARR-EXTEND-FOR-GA` fires once-per-cycle while the belief
is set, so its first emission is at the START of the GA-active
window, not the end. A regression where `ARR-TURN-BASE`'s
`Not(GoAroundInProgressOnRunway)` guard mis-evaluated could emit
`TurnBase(B)` two cycles later while the belief was still live,
and the test would pass green.

A second related shape: a "same-cycle cancel-output contract" pin
that asserts "TurnBase(B) fires after recovery Report(Downwind)"
is structurally too loose. The contract is "TurnBase(B) fires in
the SAME controller cycle the belief clears" — not "at any later
time."

## What Didn't Work

**Round-1 attempt** (upper bound = first downstream emission):
```kotlin
val gaWindowUpper = bExtendDownwindAfterGa.first().time.millis
val bTurnBaseInWindow = bTurnBaseRecords.filter { it.time.millis
    > aGoingAroundMs && it.time.millis < gaWindowUpper }
check(bTurnBaseInWindow.isEmpty()) { ... }
```
Test passed under the current implementation but would not catch a
late-cycle TurnBase regression — the GA-active window extends far
past the first ExtendDownwind. Codex caught this as a Major.

**Round-1 attempt** (same-cycle = any later time):
```kotlin
val bTurnBaseAfterRecovery = bTurnBaseRecords.firstOrNull {
    it.time.millis >= aRecoveryDownwindMs } ?: fail(...)
```
Same defect class — proves "a TurnBase eventually fires," not "the
TurnBase fires same-cycle as the belief-clear." Codex caught this
too.

## Solution

**Walk the BeliefState slice directly** to identify the SET and
CLEAR cursors of the active belief — the proper boundaries of the
active window:

```kotlin
val gaBeliefSetCursor = trace.firstWhere { st ->
    st.beliefs[towerId]?.goAroundInProgressByRunway?.containsKey(rwy) == true
}
val gaBeliefClearCursor: TraceCursor? =
    gaBeliefSetCursor.fold<TraceCursor?>({ null }) { setCursor ->
        trace.firstWhere { st ->
            st.now.millis > setCursor.time.millis &&
                st.beliefs[towerId]?.goAroundInProgressByRunway
                    ?.containsKey(rwy) != true
        }.fold<TraceCursor?>({ null }) { it }
    }
```

Then:

1. **Upper bound for "no Y during X-active":** use
   `gaBeliefClearCursor?.time?.millis ?: end-of-trace`. Full GA-
   active window is now correctly pinned.

2. **Same-cycle assertion:** assert the downstream emission lands
   within `[clearCursor.time, clearCursor.time +
   CONTROLLER_CYCLE_INTERVAL)`. `CONTROLLER_CYCLE_INTERVAL` is a
   500ms constant in `sim/Step.kt:166`; same-cycle ordering is
   anchored on the cycle wall clock per the
   `findEmittingCycleMs` mint-id walk semantic
   (`bug/test-failures/sim-test-pins-must-compare-against-2026-05-10`).

3. **Defense-in-depth:** assert the clear-cursor's time
   STRICTLY POSTDATES the triggering pilot transmission. This
   rules out the 60s-timeout clear path firing instead of the
   intended pattern-rejoin clear path.

The shared belief-walk doubles as a sticky-witness pin for both
scenarios (Layer-3 kinematic non-event + Scenario-3 cancel-
output).

## Prevention

When pinning a "while X is active, Y must not fire" gate:

1. **Walk the X belief's lifecycle on the BeliefState slice
   directly** — the SET and CLEAR cursors are the authoritative
   boundaries. Do not approximate the active window with a
   downstream emission's timestamp.

2. **For "same-cycle Z" contracts, pin against the cycle wall
   clock**, not against arbitrary "at-or-after" relationships.
   `CONTROLLER_CYCLE_INTERVAL` (500ms) defines the per-cycle
   window — assert the emission lands within
   `[cycleCursor.time, cycleCursor.time + cycleInterval)`.

3. **Default to BeliefState-slice walks over emission-time
   walks** when the property under test is keyed on a belief
   slot. Emissions surface the belief's INTENT effect; the
   slice itself surfaces the lifecycle directly.

4. **Always ask: "what is the latest possible event in this
   active window?"** before picking an upper bound. If the
   answer is "later than my candidate upper bound," the gate
   is too narrow.

Sibling discipline:
`knowledge/best-practices/inherited-gate-semantics-2026-05-15` —
re-validate inherited semantics per axis; this captures the
SET/CLEAR-cursor discipline for belief-driven active-window
gates as a complementary lesson.
