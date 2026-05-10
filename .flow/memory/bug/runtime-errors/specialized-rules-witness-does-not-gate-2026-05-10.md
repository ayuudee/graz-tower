---
title: Specialized rule's witness does not gate existing rule sharing the same instruct
date: "2026-05-10"
track: bug
category: runtime-errors
module: controller/procedure/TowerArrival.kt
tags: [bdi-guards, rule-arbitration, witness-discipline, coordination-escalation, supersession, fn-13]
problem_type: runtime-error
symptoms: Existing rule re-emits an instruction leaf after new specialized rule fires; coordination ledger escalates past Issued; NoPendingReadback matcher's suppression dissolves
root_cause: Specialized rule's commitment-scoped witness only gates the specialized rule; existing rule needs an independent domain-discriminator guard (e.g. Not(RunwayObstructed)) to stay out of the specialized rule's territory
resolution_type: fix
---

## Problem

When adding a new ATC rule that emits the same `AtcInstruction` leaf type
as an existing rule (e.g. `ContinueApproach` from both
`ARR-CONTINUE-APPROACH-OBSTRUCTION` and the existing `ARR-CONTINUE`), the
new rule's witness (`continueApproachIssuedThisAttempt`) only gates the
NEW rule's re-firing. The existing rule's eligibility is unchanged
unless it is also gated against the new rule's domain. Race shape:

1. New rule fires, sets its witness, emits coordination.
2. Coordination escalates Issued → Querying → Reissued → LostCommsDeclared.
3. `NoPendingReadback(<InstructionType>)` matcher's suppression depends on
   coordination state — for instructions with empty `requiredReadbackAtoms`
   (like `ContinueApproach`), the matcher's gate becomes ineffective once
   the coordination escalates.
4. The OLD rule's guard becomes eligible (its other arms — `Not(RunwayAccessGranted)`
   etc. — commonly hold in the obstruction window).
5. OLD rule fires AGAIN, emitting the same instruction LEAF with the WRONG
   reason / companion (the new rule's structured reason was lost).

## What Didn't Work

Relying on the new rule's `<NewRule>AlreadyIssuedThisAttempt` witness
alone, assuming `NoPendingReadback(<InstructionType>)` on the existing
rule would prevent re-fire. The matcher's coordination-state semantics
do not survive escalation for instructions without readback atoms.

## Solution

When the new rule's domain is a strict subset of the existing rule's
(e.g. obstruction-driven CONTINUE APPROACH is a subset of all
delayed-clearance CONTINUE APPROACH cases), add a domain-discriminator
guard to the EXISTING rule (e.g. `Not(RunwayObstructed)` on the
non-obstruction `ARR-CONTINUE` rule). The discriminator must be:
- Structurally simple (no shared mutable state with the new rule).
- Doctrinally clean (the existing rule MUST NOT fire when the new
  rule's domain applies — different reason / different companion).

When both rules cite an upgraded regulation, ensure the citation only
appears on the rule whose semantics actually match the regulation's
upgraded principle. Otherwise the trace will carry false citations
(another finding in the same review cycle).

## Prevention

When introducing a specialized rule that emits an existing
instruction leaf type, audit:
- The existing rule's guard for a domain discriminator against the new
  rule's trigger (here: `RunwayObstructed`).
- Whether `NoPendingReadback(<InstructionType>)` is the only coordination-
  state suppression and whether the matcher remains effective across
  coordination escalation.
- Whether any regulation citation shared between rules still matches
  the doctrinal scope of each rule after the upgrade.

Test pattern: construct the exact escalation race state (new rule's
witness set, new rule's coordination escalated past `Issued`, the
existing rule's other arms passing) and assert the existing rule's
guard evaluates FALSE. Direct guard-level assertion is more reliable
than going through `controllerDecide` because arbitration and other
competing rules may mask the regression.
