---
title: Sticky-witness guard swap can regress response-shape rules sharing the trigger
date: "2026-05-09"
track: bug
category: build-errors
module: controller/procedure/TowerDeparture.kt
tags: [bdi-guards, sticky-witness, refactor-discipline, fn-8]
problem_type: build-error
symptoms: DEP-HOLD-IMC re-fires every cycle while VMC stays bad after PilotReady→PilotReadyDuringCommitment swap
root_cause: Shared private val DepartureTrigger covered both response-shape and slot-grant rules; sticky semantics break response-shape
resolution_type: fix
---

## Problem

When refactoring a shared guard atom from single-cycle event to sticky
commitment-witness state, the swap may silently regress *response-shape*
rules that share the same atom. Single-cycle `PilotReady` was the
trigger for both `DEP-LUAW` (runway-slot-grant rule, needed sticky)
AND `DEP-HOLD-IMC` (response-shape rule that instructs Hold Position
ONCE in response to a Ready report when weather is below VMC).
Replacing the shared `DepartureTrigger` private val with the sticky
`PilotReadyDuringCommitment` made `DEP-HOLD-IMC` re-fire every cycle
while VMC stayed bad — saturating the frequency with redundant Hold
Position instructions.

## What Didn't Work

Single shared `private val DepartureTrigger = PilotReadyDuringCommitment`
applied uniformly to all rules that previously used `PilotReady`.
Compiled and passed targeted G1/G0/G2 tests because none of them
exercise the IMC-hold path. Codex review caught the latent regression
by reading the rule semantics.

## Solution

Split the trigger into two semantically distinct flavours:
- `DepartureTrigger = PilotReady` (single-cycle) for response-shape
  rules that fire once in response to the Ready report (`DEP-HOLD-IMC`).
- `RunwaySlotTrigger = PilotReadyDuringCommitment` (sticky) for
  runway-slot-grant rules that gate on the runway becoming available
  (`DEP-LUAW`, `DEP-LUAW-COND`).

Code in `controller/procedure/TowerDeparture.kt:60-80` documents the
split with semantic-distinction commentary.

## Prevention

When introducing a sticky-witness BDI guard alongside an existing
single-cycle event guard, **audit every call site of the existing
guard** before swapping. Categorize each rule as either:
- *Response-shape*: fires once in response to the event → keep
  single-cycle.
- *Slot-grant / state-readiness*: gates on a downstream state
  becoming available → switch to sticky witness.

Naming convention: keep distinct names (`PilotReady` vs
`PilotReadyDuringCommitment`) and **don't share a single trigger
private val** across rules of different shape — the val name hides
the semantic distinction. Either inline the guard at each rule site
or split into two named triggers (`DepartureTrigger` vs
`RunwaySlotTrigger`).
