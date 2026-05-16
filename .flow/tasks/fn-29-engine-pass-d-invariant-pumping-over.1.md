---
satisfies: [R1, R2, R3, R4, R5, R6]
---

## Description

Add invariant-pump harness to runUntil; apply to 9 existing goldens with 1-line additions each. Depends on fn-26.

**Size:** S-M (~250 LOC, 1 new file + 9 small golden edits).

**Files**: CREATE `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/InvariantPump.kt`. MODIFY the 9 sim golden test files. READ fn-26's invariant predicates + `RunUntil.kt:119`.

## Approach

1. Baseline + full verify.
2. Write InvariantPump.kt: variant of runUntilWithStateTrace that accepts `invariants: List<(SimEvent, SimState) -> Unit>`, asserts each at every event-boundary.
3. Define invariant list (totality, monotonicity, determinism-sample, runway-certifier preservation).
4. Apply to 9 goldens with 1-line addition. Bespoke pins unchanged.
5. Run full sim test. If any golden fails the pump invariants, file follow-up epic per violation; don't skip the invariant.
6. Verify (two-invocation).
7. flowctl done with interpolated values.

## Acceptance

- [ ] R1-R6 per epic spec.

## Key context

- Depends on fn-26 for invariant definitions.
- M (invariants) small; N (events per golden) bounded; total runtime add <30s.
- If a golden fails: file a follow-up bug epic; don't paper.
- Pre-existing dirty state MUST NOT be staged.

## Done summary

_(filled by flowctl done)_

## Evidence

_(filled by flowctl done)_
