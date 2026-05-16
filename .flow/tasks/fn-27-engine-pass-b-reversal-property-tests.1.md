---
satisfies: [R1, R2, R3, R4, R5, R6, R7, R8]
---

## Description

Reversal property tests. Depends on fn-26 (Kotest + EngineGenerators).

**Size:** S-M (~200 LOC, 1 new file).

**Files**: CREATE `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/ReversalPropertyTest.kt`. READ: protocol/Instruction.kt + fn-12/13/15/17 specs for the reversal-pair inventory.

## Approach

1. **Baseline** (Step 0): git rev-parse HEAD; full verify green.
2. **Inventory** (R2): enumerate documented reversal pairs from protocol/Instruction.kt + fn-12/13/15/17 specs. Record in evidence.
3. **Write ReversalPropertyTest.kt** (R1, R3): one property per pair. Use EngineGenerators from fn-26.
4. **Document class-specific exceptions** (R4): inline in each property's body. Audit-field exclusion list per class.
5. **Run** (R5): if any pair fails, file follow-up epic; don't paper.
6. **Tune bounded runtime** (R6): 500 iterations default, total ≤45s.
7. **Verify** (R7): two-invocation full verify.
8. **flowctl done** with interpolated values (per fn-22 R6 state-sync discipline).

## Acceptance

- [ ] R1-R8 per epic spec.

## Key context

- Depends on fn-26 for Kotest + EngineGenerators.
- Reversal equivalence excludes `(now, seq, audit_history_fields)` — verify the exclusion list per class doesn't hide real defects.
- Pre-existing dirty state MUST NOT be staged.

## Done summary
PARKED 2026-05-16: planning defect (listed reversal-pair SimEvents do not exist). Intent absorbed by fn-29 trace-invariant pumping + fn-26 step-property forward symmetry. No code written; no tests authored.
## Evidence
- Commits:
- Tests:
- PRs: