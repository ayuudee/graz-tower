# fn-29 — Engine pass D: invariant pumping over existing 9 goldens

## Overview

The 9 existing sim goldens each assert their bespoke scenario invariants ("aircraft lands and vacates", "GA fires within window", etc.) but DON'T assert the universal engine invariants (`step()` is total at every tick, certifier preconditions hold throughout, no impossible state transitions, etc.). This epic re-runs each existing golden with an **invariant-pump harness** that asserts the universal invariants at EVERY tick — not just at the test's bespoke checkpoints.

Pattern: extend `runUntilWithStateTrace` (or add a new variant) that accepts a list of invariant predicates and asserts each at every event-boundary. When fn-26 lands the engine-level invariants (totality, monotonicity, determinism, certifier-preservation), those same predicates apply here too — fn-29 reuses them.

Result: every existing golden now ALSO proves that the engine maintains its universal invariants across the scenario's entire timeline. If a golden's scenario exercises an invariant violation that the golden's bespoke pins don't catch, this epic finds it.

## Boundaries / non-goals

- **Out: changing the goldens' bespoke assertions.** Pump invariants are added ON TOP of the existing pins, not as a replacement.
- **Out: writing new goldens.** fn-28 handles that.
- **Out: invariant generation.** This epic CONSUMES the invariants fn-26 establishes; doesn't define new ones.
- **Out: instrumenting `step()` for invariant runtime checks.** Pump-time-only assertions. Engine stays unchanged.

## Strategy Alignment

- **Runtime simulator** — the existing 9 goldens become stronger evidence for the strategy's "regulation-grounded ATC simulator" claim by proving engine invariants hold throughout each scenario's runtime, not just at the bespoke checkpoints.

## Decision context

**Why pump invariants on existing goldens vs only on new ones**: existing goldens have already been carefully authored to exercise realistic scenarios. The invariants likely already hold (else the goldens wouldn't pass). The win is **proving** they hold across the full timeline, not just at the bespoke pin points. If an invariant doesn't hold at some intermediate tick, that's a real latent bug.

**Why a harness, not per-golden edits**: extending `runUntilWithStateTrace` to take an invariant list means every golden gains the coverage with a one-line change (pass the invariant list). Per-golden edits would 9× the work.

## Acceptance

- **R1:** New file `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/InvariantPump.kt` — extension of `runUntilWithStateTrace` (or a new sibling fn) that accepts `invariants: List<(SimEvent, SimState) -> Unit>` and applies each at every event-boundary. Failures surface with `(tick, invariant_name, state_snapshot)`.
- **R2:** Each of the 9 existing goldens has a 1-line addition: pass the invariant list to the run-until call. The bespoke assertions stay unchanged.
- **R3:** Invariant list at minimum: (a) totality (the engine didn't throw — `step()` returned), (b) monotonicity (`now >= prev_now`, `seq >= prev_seq`), (c) determinism (re-run a sample of (state, event) pairs from the trace and assert same output — sampled, not full), (d) runway-certifier preservation (use the Kotlin shim from `controller/.../certify/RunwayKernel.kt`). Surface-area invariants and air-path invariants follow if their Kotlin shims exist.
- **R4:** If a golden FAILS the pump invariants (one or more invariants violated at some intermediate tick), surface as a real bug. File a follow-up epic per violated invariant; do NOT skip the invariant to make the golden pass.
- **R5:** Full verify GREEN; nine sim goldens GREEN (with pump invariants asserted); detekt unchanged.
- **R6:** Diff scope: 1 new file (`InvariantPump.kt`) + 9 modified golden tests (one-line each). Total ≤10 files, ≤300 LOC.

## Dependencies

This epic depends on **fn-26-engine-pass-a-step-function-property** for the invariant definitions (totality, monotonicity, determinism, certifier-preservation). Sequence: fn-26 → fn-27 (or parallel) → fn-29.

## Review considerations

- **FP / type safety**: invariant predicates are `(SimEvent, SimState) -> Unit` (throwing on failure). Pure functions; no state mutation. **Reviewer focus**: confirm invariant predicates don't accidentally close over mutable state.
- **Test architecture**: pump-harness extends existing `runUntilWithStateTrace` shape. Pump happens per-event-boundary; performance is O(N events × M invariants). **Reviewer focus**: M small (4-6); N bounded per golden; total runtime should add <30s to the full sim test pass.
- **Impact**: scoped to :sim/jvmTest.
- **Operational ATC correctness / applicability**: pump invariants check engine-level correctness across regulation-grounded scenarios. **Reviewer focus**: confirm each invariant's failure-message names the regulation or doctrinal claim it's defending.

## References

- fn-26 (depends on) — establishes the invariant definitions
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/RunUntil.kt:119` — `runUntilWithStateTrace` (the extension point)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/SimTraceQueries.kt` — trace-query helpers
- The 9 existing goldens: LowgGoldenTest, G1TwoAircraftCircuitsTest, G1TwoAircraftMinimalSpec, G2CrossAerodromeVfrTest, G3aPilotTrainedGoAroundTest, G3aRunwayObstructionTest, G3aRunwayObstructionContinueApproachTest, G3aPilotReactiveCrosswindTest, G3aPilotReactiveTailwindTest
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/certify/RunwayKernel.kt` — runway certifier shim (R3 oracle)
