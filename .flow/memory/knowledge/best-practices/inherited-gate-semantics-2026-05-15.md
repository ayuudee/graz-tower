---
title: "Inherited sim-test gates: re-validate semantics per axis, not just syntax"
date: "2026-05-15"
track: knowledge
category: best-practices
module: sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim
tags: [sim-test, gate-semantics, multi-task-epic, copy-paste, recovery-pattern, radio-observables, fn-14, fn-15]
applies_when: Mirroring an existing sim-test gate from a sibling axis (crosswind → tailwind, approach-side → departure-side, runway-A-fn-N.X → runway-B-fn-N.Y), or adding a new sim-test gate that depends on a transition during a GA, missed approach, or any recovery flow.
related_to: [bug/test-failures/inherited-sim-test-gate-semantics-may-2026-05-11]
---

## The rule

When copy-pasting a sim-test gate from a sibling axis (e.g. crosswind →
tailwind, runway-A → runway-B, fn-N.X → fn-N.Y), the gate's **semantic
meaning** may not transfer even when the **syntactic pattern** is
identical. The sibling test may be passing because the gate's semantics
coincide with the property under test on its axis by accident, not by
design. The same gate copied verbatim can fire prematurely (or not at all)
on the new axis.

Re-validate gate semantics against the new axis's expected timeline before
declaring "mirrored the sibling test."

## Why it bites

`bug/test-failures/inherited-sim-test-gate-semantics-may-2026-05-11.md`:
fn-14.2's crosswind G3a-react sim test gated transition-2 (wind returns
to within advisory) on "off-final + post-`Report(GoingAround)` transmitted."
On the crosswind axis, "off-final" coincides with "back on downwind" for
the recovery circuit by accident: the controller's
`reconcileAwaitDownwind` advances commitment stage as the aircraft
physically re-enters downwind, and the GA's downwind re-entry on the
crosswind axis happens to land within the off-final window the gate uses.

Mirroring this verbatim into fn-15.2's tailwind sibling shipped a test
that compiled GREEN and passed every pin — but codex review showed the
wind cleared at the GA-POST-CLEAR regression moment, only 2-3 seconds
after the GA transmission. The exceedance window was **vacuously narrow**
(GA climbout only), and the recovery pin "aircraft lands within the
advisory" trivially passed because wind had cleared 240+ seconds before
recovery final.

Two intermediate attempts also failed:

- **Tighten to `AwaitDownwind` OR `AwaitApproach` commitment stage:**
  failed silently because `reconcileAwaitDownwind` advances
  `AwaitDownwind → AwaitApproach` on transient `ArrivalPosition.OnBase`
  / `OnFinal` observations during the GA climbout (Tick A's
  Final/Climbing/Final dance). Even `AwaitApproach` fires within 3.5
  seconds of regression.
- **Tighten to `AwaitApproach` only:** still fires too early — commitment
  stage advances on the controller's position observation during climbout,
  not on a real downwind report.

The fix: watch the radio for the post-GA recovery-circuit
`Report(events=[Downwind(...)])` transmission. The pilot transmits the
downwind position only when **physically** re-entering downwind. Flag the
first post-`Report(GoingAround)` downwind report and gate transition-2 on
the flag alone — no commitment-stage involvement.

Result: tailwind cleared at 1005940ms vs round-1's 761500ms (240+ second
wider exceedance window covering the entire recovery downwind transit).

## The radio-observable preference

A second-order rule emerges:

> **Prefer radio observables over commitment-stage observables for "the
> pilot did X" verification.** Radio observables only fire on pilot
> decision cycles; commitment-stage observables can fire on controller
> cycle ticks reacting to transient kinematic phase windows.

`Report(events=[Downwind])` is the load-bearing pilot observable for
"aircraft is physically on downwind." `TowerArrivalStage.AwaitApproach`
is a controller-side reaction that can fire on transient phase windows
during a GA climbout. They are NOT interchangeable.

This generalizes: any test gate that depends on "the pilot reached
state X" should reach for the pilot transmission record, not a
controller-side commitment stage that happens to advance on the same
trigger.

## When this applies

- **Mirroring a sim test from a sibling axis** (crosswind → tailwind,
  approach-side → departure-side, runway-A-fn-N.X → runway-B-fn-N.Y).
- **Adding a new sim test** that gates on a transition during a GA, missed
  approach, or any recovery flow.
- **Refactoring an existing sim test** where the gate's transport (radio
  vs commitment-stage vs phase) changes shape.

## Forward-applicable checklist

Before SHIP on a mirrored sim test:

1. **Probe the exceedance window:** print `<event> authored at: <ms>`
   and `<event> cleared at: <ms>` to the test log. Verify the window
   covers the recovery pattern, not just the regression moment. A
   transition-2 that fires within seconds of transition-1 on the
   recovery axis is suspect.
2. **Trace the gate's semantic intent:** for each gate clause, ask
   "what physical condition does this CLAUSE prove? Does that condition
   hold on the new axis by design or by accident?" If by accident,
   replace with a radio observable that proves it by design.
3. **Don't trust the sibling's gate as authoritative:** the sibling's
   review may have been less deep. Per-diff review holds the new sibling
   to the higher bar; fix the new copy at minimum (the original sibling
   may be a separate task or a follow-up audit).
4. **Default to radio observables for "the pilot did X":** start with
   `firstPilotReportOf<X>` or `Report(events=[X])`. Reach for
   commitment-stage only with an explicit architectural justification.

## Cross-references

- Test-pin discipline (post-state-vs-intent generalization):
  `knowledge/best-practices/test-pin-discipline-2026-05-15.md`
- Source capture (kept as authoritative event record):
  `.flow/memory/bug/test-failures/inherited-sim-test-gate-semantics-may-2026-05-11.md`
