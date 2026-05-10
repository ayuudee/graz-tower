---
title: fn-13.1 CA witness re-arm fires on late Downwind on AwaitApproach commitment
date: "2026-05-10"
track: bug
category: runtime-errors
module: controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/Controller.kt
tags: [fn-13, fn-13.2, bdi-guards, witness-discipline, re-arm-hook, radio-latency, stage-gate]
problem_type: runtime-error
symptoms: Duplicate ContinueApproach + companion emissions on same approach attempt
root_cause: Downwind re-arm hook unconditional; late-arriving Downwind on AwaitApproach clears CA witness
resolution_type: fix
related_to: [bug/runtime-errors/specialized-rules-witness-does-not-gate-2026-05-10]
---

## Problem

fn-13.1's CONTINUE APPROACH witness re-arm hook fires on ANY `Report(Downwind)` event arriving in the controller's cycle events list, regardless of which stage the commitment is in. This causes a duplicate-fire bug in the sim G3a-obstruction-continue-approach scenario:

1. The aircraft completes departure (TOWER_DEPARTURE → Complete at takeoff).
2. `reconcileCommitments` forms a fresh TOWER_ARRIVAL commitment directly at AwaitApproach (skips AwaitDownwind because the aircraft is already an airborne circuit-traffic arrival).
3. The pilot transmits `Report(Downwind)` at sim time T.
4. The world hook authors a runway obstruction.
5. The CA rule fires at cycle T+0.5s on the AwaitApproach commitment, sets `continueApproachIssuedThisAttempt = true`.
6. The pilot's `Report(Downwind)` from step 3 finishes its radio transmission and is delivered to the controller's inbox at T+2s (after radio physics + utterance duration).
7. The controller cycle at T+2s receives the Downwind report in events. The re-arm hook in `reconcileTowerArrival` fires: `downwindReportedThisCycle && witness=true` → clears witness to false.
8. On that same cycle, the CA rule's guard re-evaluates: witness=false → guard passes → CA fires AGAIN. Duplicate CA instruction + duplicate `RunwayObstructionInformation` companion are emitted.

The CA path is uniquely affected because its rule has `nextStage = null` (no stage regression on fire). The obstruction-GA witness shares the same hook design but its rule has `nextStage = AwaitDownwind`, so the regressed stage prevents the GA rule from re-firing even when the witness is cleared.

## What Didn't Work

- Pinning "exactly one CA + companion" in the sim test without fixing the controller-side bug — the test would correctly catch the regression but the underlying production bug remains.
- Resetting the CA witness on stage regression in `advanceCommittedStages` — doesn't address the bug, because the CA path has no stage regression. The witness gets cleared by the late Downwind report, not by a regression.

## Solution

Gate the witness re-arm in `reconcileTowerArrival` on the commitment's stage being `AwaitDownwind`:

```kotlin
val stageAllowsRearm = withReports.stage == TowerArrivalStage.AwaitDownwind
val needsContinueApproachRearm =
    downwindReportedThisCycle && stageAllowsRearm &&
        withReports.continueApproachIssuedThisAttempt
```

The intent: re-arm fires only when the commitment is genuinely in a "post-regression / recovery-downwind / pre-AwaitApproach-entry" window. A late-arriving Downwind report on an AwaitApproach commitment is the original Downwind report that already caused the AwaitApproach transition — re-arming on it would clear the witness on the SAME approach attempt, defeating the no-refire discipline.

The recovery-circuit re-arm behaviour is preserved: after a GA fires (regressing the stage from AwaitApproach/LandingClearanceIssued/AwaitLandedObserved to AwaitDownwind), the pilot transmits Downwind from the recovery circuit. That Downwind arrives at the controller while the commitment is in AwaitDownwind → gate passes → witness cleared. On the next cycle, the stage advances back to AwaitApproach with witness=false, and a fresh obstruction can drive a fresh CA.

Symmetric for the obstruction-GA witness (`obstructionGoAroundIssuedThisAttempt`) — same gate, same recovery path.

Regression test: `ObstructionContinueApproachSpec.re-arm gate — late-arriving Downwind on AwaitApproach must NOT clear the CA witness`. Pins both the witness state and the rule-fire absence.

Sim-level pin: `G3aRunwayObstructionContinueApproachTest` asserts exactly one CA instruction + one companion, catching duplicate fires.

## Prevention

When designing witness re-arm hooks for stage-immutable rules (rules with `nextStage = null`), the re-arm trigger must distinguish "report from the CURRENT commitment attempt" vs "report from a NEW commitment attempt". For stage-mutating rules (e.g. obstruction-GA with `nextStage = AwaitDownwind`), the regressed stage acts as a natural gate. For stage-immutable rules, the gate must be encoded explicitly — either on stage being a pre-AwaitApproach value, on `formedAt` recency, or on a commitment-attempt-id.

Single-aircraft scenarios where the commitment is formed DIRECTLY at AwaitApproach (skipping AwaitDownwind) are the worst-case trigger for this class of bug — the radio latency between the pilot's outbound transmission and the controller's inbox delivery exceeds the cycle gap between commitment formation and rule fire. Multi-aircraft scenarios where the commitment passes through AwaitDownwind first don't surface the bug because the Downwind report is delivered while the stage is AwaitDownwind.

Future witness-re-arm hooks should be paired with a sim-level golden test that exercises the radio-latency vs witness-set-timing interaction.
