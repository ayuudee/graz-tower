---
title: "Sim test pins must compare against decision-cycle time, not tx-start"
date: "2026-05-10"
track: bug
category: test-failures
module: sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionTest.kt
tags: [fn-12.3, sim-pin, controller-decide, decision-cycle, tx-start, radio-serialization, test-discipline]
problem_type: test-failure
symptoms: Tx-time-based pins false-pass for cycles whose outputs serialize past the next CONTROLLER_CYCLE_INTERVAL
root_cause: applyControllerOutputs serializes one cycle's outputs on the same frequency; multiple txStarts from one cycle can straddle multiple ControllerCycle event windows
resolution_type: fix
related_to: [bug/test-failures/tests-must-anchor-on-observed-post-2026-05-09]
---

## Problem

Sim-level golden tests pinning controller decisions easily conflate
**transmission-start time** (when the radio packet hits the air) with
**decision-cycle time** (when `controllerDecide` actually emitted the
output). `applyControllerOutputs` serializes multiple cycle outputs on
the same frequency (each subsequent tx starts at the prior tx's `endsAt`),
so two outputs from the SAME `controllerDecide` invocation can have
transmission starts separated by several seconds — often straddling the
next `CONTROLLER_CYCLE_INTERVAL`.

Comparing belief-slice transitions, stage regressions, or causal partial
orders against `txStart` times opens a hole: a queued transmission could
start after a later belief transition while its decision was made too
early, and the pin would falsely pass.

## What Didn't Work

- Walking the trace for the **most recent `ControllerCycle` event at-or-
  before `txStart`** — fails when serialization pushes `txStart` past the
  next cycle interval (the prior cycle is the right answer; the most
  recent cycle is the wrong one).
- Checking `state.inFlightTransmissions` membership — the transmission
  isn't added to `inFlightTransmissions` until its `SimEvent.
  TransmissionStart` event fires later; the originating `ControllerCycle`
  step's post-state does NOT contain the txId.

## Solution

Use the `nextTransmissionId` counter as the decision-cycle witness.
`SimState.mintTransmissionId()` is called inside `applyControllerOutputs`
during the same `controllerDecide` invocation that emits the output,
and the resulting state has `nextTransmissionId` bumped past the new id.
The originating cycle is therefore the unique step satisfying:

  pre.nextTransmissionId <= txId.value < post.nextTransmissionId

AND `event is SimEvent.ControllerCycle(controller)`. That step's
`event.time` is the canonical decision-cycle time.

Once that's available, separate Layer 1a (decision-cycle ordering) from
Layer 1b (radio-transmission ordering) and assert each against its
correct comparand:

- `Detected.decisionTime <= GoAround.decisionTime` (LHS = belief slice
  transition time; RHS = cycle event.time, NOT txStart)
- `Stage_regression.time == GoAround.decisionTime` (equality, not <=)
- `Cleared.decisionTime < ClearedToLand(recovery).decisionTime`
  (NOT `< ClearedToLand(recovery).txStart`)

Also: same-cycle pin for companion transmissions —
`goAroundDecisionCycleMs == companionDecisionCycleMs` — proves
`deriveCompanionOutputs` emitted both in one cycle, not a later
standalone companion response.

## Prevention

When a sim-level golden test needs to pin "the controller decision
behaved X way", use the decision-cycle time (mint-id walk), not the
transmission-start time. The two diverge whenever cycle outputs
serialize past the next `CONTROLLER_CYCLE_INTERVAL`. The
`extractTransmissionId(trace, record)` + `findEmittingCycleMs(trace,
controller, txId)` helper pattern in `G3aRunwayObstructionTest.kt` is
the canonical template.

Codex review caught this twice in fn-12.3 (rounds 1 + 2) — once for
the same-cycle pin, once for Layer 1a belief-vs-cycle ordering. Future
sim tests that pin controller decisions should reach for the helper
pattern up-front rather than after a review round-trip.
