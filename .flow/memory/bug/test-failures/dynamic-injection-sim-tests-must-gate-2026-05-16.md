---
title: "Dynamic-injection sim tests must gate on post-step state, not radio observables"
date: "2026-05-16"
track: bug
category: test-failures
module: sim/src/jvmTest/.../G0AbortTakeoffEngineFailureTest.kt + sim/.../RunUntil.kt
tags: [fn-28, fn-28.9, sim-testing, dynamic-injection, cognitive-delay, instructor-channel, codex-impl-review, gating-pattern]
problem_type: test-failure
symptoms: Sim test passed abort assertions but did NOT model the documented 'engine failure during takeoff roll' contract
root_cause: "Hook gated on ClearedForTakeoff TransmissionStart, but pilot processes clearance only after PILOT_COGNITIVE_DELAY → phase/step not yet transitioned"
resolution_type: fix
related_to: [bug/test-failures/beliefstate-active-window-pin-use-2026-05-16, bug/test-failures/compound-predicate-test-assertions-2026-05-11, bug/test-failures/inherited-sim-test-gate-semantics-may-2026-05-11, bug/test-failures/tests-must-anchor-on-observed-post-2026-05-09]
---

## Problem

In `G0AbortTakeoffEngineFailureTest` positive scenario, the dynamic
`SimEvent.EngineFailure` injection was gated on observing
`ClearedForTakeoff` `TransmissionStart` in the trace. The test passed
its abort assertions but did NOT model the documented scenario.

The issue: `TransmissionStart` fires BEFORE the pilot processes the
clearance. The processing pipeline is:

  TransmissionStart → TransmissionEnd → PILOT_COGNITIVE_DELAY →
  PilotProcessingComplete → handlePilotProcessingComplete →
  PilotAgent.processClearedForTakeoff (sets phase = TakeoffRoll +
  mission step = FLY_DEPARTURE)

So when the hook fires at `TransmissionStart`, `aircraft.phase` is
still `LinedUp` and `mission.currentTask.step` is still
`AWAIT_TAKEOFF_CLEARANCE`. The injected EngineFailure fires before
the abort gate's 3 externally-observable preconditions
(`phase == TakeoffRoll`, `step == FLY_DEPARTURE`,
`speedMps < rotationSpeedMps`) hold. The recognition fails closed at
that point; what eventually fires (if anything) is not the documented
"engine failure during takeoff roll" scenario.

## What Didn't Work

Gating on a radio-observable proxy (the clearance transmission start)
under the assumption that "ClearedForTakeoff fires → pilot is in
takeoff roll" — that assumption skips the cognitive-delay processing
pipeline that turns the radio event into a state transition.

## Solution

Gate on the POST-STEP `SimState` snapshot where the abort
recognition's externally-observable preconditions actually hold,
NOT on the radio-side trigger that PRECEDES the state transition:

```kotlin
val ac = st.aircraft[aircraftId] ?: return@hook ...
val activeStep = ac.pilotMission?.currentTask?.step
val phaseOk = ac.phase == PilotPhase.TakeoffRoll
val stepOk = activeStep == MissionStep.FLY_DEPARTURE
val speedOk = ac.speedMps < rotationSpeedMps
if (!(phaseOk && stepOk && speedOk)) {
    return@hook EventInjection(state = st, inject = emptyList())
}
// Inject EngineFailure 1ms later (sits before next PhysicsTick).
```

Also record a precondition-snapshot string at injection time and
assert it in the test body — pins the scenario to the documented
contract rather than just "injection happened".

See `sim/src/jvmTest/.../G0AbortTakeoffEngineFailureTest.kt` for the
exemplar implementation.

## Prevention

When writing dynamic-injection sim tests that drive off pilot
observations, **always gate on post-step state** (the projected
result of the processing pipeline), not on the radio-side trigger.
Specifically:

1. Radio events (`TransmissionStart`, `TransmissionEnd`) are inputs to
   the pilot's cognitive pipeline, not state transitions themselves.
2. State transitions (`phase`, `currentTask.step`,
   `targetSpeedMps`) become visible on `SimState` only AFTER
   `PilotProcessingComplete` for the relevant instruction has been
   processed.
3. The injection hook receives the post-step `SimState` —
   `runUntilWithStateTraceAndInjection` calls the hook AFTER each
   step's `step(state, event)` + `onAfterEvent` chain.
4. Assert the precondition snapshot at injection time in the test
   body so a future regression that re-introduces a radio-side gate
   surfaces immediately with a diagnostic delta.

This is the same shape as fn-14.1's
`aircraftIsOnFinalWithLandingClearance` helper which reads
`(aircraft.phase, beliefState.commitment.stage)` AFTER processing,
not the `ClearedToLand` transmission itself.
