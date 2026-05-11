---
title: Inherited sim-test gate semantics may not transfer across recognition axes
date: "2026-05-11"
track: bug
category: test-failures
module: sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveTailwindTest.kt
tags: [fn-15, fn-15.2, sim-test, recovery-gate, radio-observable, commitment-stage, gate-semantics, codex-impl-review, multi-round-fix]
problem_type: test-failure
symptoms: "Sim test passed every assertion but exceedance window was vacuously narrow; wind cleared at GA-POST-CLEAR regression moment instead of after recovery downwind re-entry, making R11's 'aircraft back on downwind' intent trivially true"
root_cause: "Copy-pasted fn-14.2 crosswind sibling's off-final gate without re-validating that 'off-final' means 'back on downwind' on the new axis; controller's reconcileAwaitDownwind advances commitment stage on transient OnBase/OnFinal observations during GA climbout, so commitment-stage gates fire BEFORE the aircraft physically re-enters the recovery pattern"
resolution_type: fix
related_to: [bug/test-failures/compound-predicate-test-assertions-2026-05-11]
---

## Problem
The fn-14.2 G3a-react-crosswind sim test established a two-transition world-weather authorship pattern with the second transition gated on `Report(GoingAround)` transmitted + aircraft `phase \!= Final`. Mirroring this gate verbatim in fn-15.2 (tailwind sibling) shipped a test that compiled GREEN and passed every pin — but codex review showed the wind cleared at the GA-POST-CLEAR regression moment, only 2-3 seconds after the GA transmission, BEFORE the aircraft physically re-entered the recovery pattern.

The intent of "wind returns to within advisory once the aircraft is back on downwind" was never validated by the test. The exceedance window was vacuously narrow (the GA climbout window only), and the recovery pin "aircraft lands within the advisory" trivially passed because the wind had cleared 240+ seconds before the recovery final.

## What Didn't Work

**Round-1 attempt** (commitment-stage gate): tighten the gate to require `TowerArrivalStage.AwaitDownwind` OR `AwaitApproach`. Failed silently — the controller's `reconcileAwaitDownwind` advances `AwaitDownwind → AwaitApproach` on `ArrivalPosition.OnBase/OnFinal` observation during the GA climbout (the aircraft's brief Final/Climbing/Final dance during the applier's Tick A), so even `AwaitApproach` fires within 3.5 seconds of regression — still no real downwind re-entry.

**Round-2 attempt** (tighten to `AwaitApproach` only): codex round-2 review caught this — the commitment stage advances on the controller's position observation during climbout, NOT on a real downwind report. The gate still fires too early.

## Solution

**Round-3 final** (`sim/.../G3aPilotReactiveTailwindTest.kt:443-460`): watch the radio for the post-GA recovery-circuit `Report(events=[Downwind(...)])` transmission. The pilot transmits the downwind position only when **physically** re-entering downwind on the recovery circuit. The flag `recoveryDownwindReportedFlag[0]` is set on the first post-GoingAround Downwind report. Transition-2 gates on this flag alone — no need for off-final / commitment-stage gates.

Result: tailwind cleared at 1005940ms vs round-1's 761500ms (240+ second wider exceedance window covering the entire recovery downwind transit), recovery touchdown still at 1300000ms, mission completes at 1397000ms within the ±15% time band.

## Prevention

When mirroring a sim-test gate from a sibling test in a multi-task epic:
1. **Don't rely on the sibling's gate being semantically correct** — fn-14.2's `off-final` gate happens to work for the crosswind axis because there's no per-type doctrinal severity difference, and codex didn't review the fn-14.2 test under as deep an eye. The same gate copied verbatim is semantically wrong for tailwind.
2. **Probe the actual exceedance window in the test trace** — print `Tailwind authored at: <ms>` and `Tailwind cleared at: <ms>` and verify the window covers the recovery pattern, not just the regression. A test that fires transition-2 within seconds of transition-1 is suspect.
3. **Prefer radio observables over commitment-stage observables for "the pilot did X" verification** — `Report(events=[Downwind])` is the load-bearing pilot observable; `TowerArrivalStage.AwaitApproach` is a controller-side reaction to position kinematics that can fire on transient phase windows. Radio observables only fire on pilot decision cycles; commitment-stage observables can fire on controller cycle ticks.

When inheriting fn-14.2's crosswind sim test discipline for fn-15.2 (tailwind sibling) without re-validating the gate semantics, the "wind returns once aircraft is off-final" gate fires at the GA-POST-CLEAR regression moment (~3 seconds after GA transmission), leaving the exceedance window vacuously narrow and the recovery pin trivially passing.
