# fn-27 — Engine pass B: reversal property tests

## Overview

Per `STRATEGY.md`: "every state transition has its reversal tested before its forward path ships." Today reversal coverage is per-golden (e.g. fn-12's runway-obstruction sets `obstruction \!= null`, then sets it back; fn-15's tailwind shift authors a 2-transition world). A property-test layer makes the reversal claim **structural across event classes**, not just per-scenario.

For event classes that have documented reversals (per fn-12 / fn-15 / fn-16 / fn-17 era), generate forward + reverse pairs and assert state equivalence (modulo `now`, `seq`, audit fields). Documented pairs include:

- `RunwayObstructed` ↔ `RunwayObstructionCleared`
- `WeatherShift(to_X)` ↔ `WeatherShift(back_to_baseline)`
- `ClearedTo<X>` ↔ `ClearedTo<Cancel>` (when cancellation is a real protocol act, NOT silent revocation)
- `LandingClearanceIssued` ↔ `LandingClearanceCancelled` (per fn-13 G3a-obstruction continue-approach flow — clearance is cancelled by the GA-on-obstruction interrupt)
- `TakeoffClearanceIssued` ↔ `TakeoffClearanceCancelled` (when modeled — verify against `protocol/.../Instruction.kt`)
- `FlightPlanFiled` ↔ `FlightPlanCancelled` (if modeled — verify)

This epic uses the same Kotest-property infrastructure fn-26 establishes (epic dependency).

## Boundaries / non-goals

- **Out: introducing new reversal events.** Only test classes that already have documented reversal semantics in the protocol. If a class needs a reversal it doesn't have today (e.g. `Backtrack` doesn't have a `BacktrackCancelled`), that's a separate epic.
- **Out: structural-vs-narrative invariants.** Reversal is reduced to: "state-after-forward-then-reverse == state-before-forward, modulo `now`/`seq`/audit." If full structural equivalence isn't reachable for a class (e.g. some commitments leave traces), document it as a class-specific exception with the rationale.
- **Out: full timeline reversibility.** Single-forward + single-reverse pair only. Multi-step reversibility (e.g. takeoff cleared → backtrack → cancel both) is a future epic.

## Strategy Alignment

- **Runtime simulator** — directly cashes the strategy's "reversal tested before forward" claim at the engine level.

## Acceptance

- **R1:** New file `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/ReversalPropertyTest.kt` — Kotest spec with one property test per reversal-pair class identified at task start.
- **R2:** Inventory step: at task start, enumerate the documented reversal pairs (read `protocol/.../Instruction.kt` + the relevant goldens' KDoc — fn-12, fn-13, fn-15, fn-17). Record the count + identities in evidence; the property test count matches.
- **R3:** Each property: generate a `(state, forwardEvent)` pair; apply forward; apply reverse; assert state equivalence modulo `(now, seq, audit_history_fields)`. Use the same EngineGenerators from fn-26.
- **R4:** Class-specific exceptions documented inline (e.g. "WeatherShift reversal leaves trace in `state.weatherTransitionsLog` — comparison excludes that field"). No silent equivalence-weakening.
- **R5:** If any reversal property fails for a class that SHOULD reverse cleanly, surface as evidence + file a follow-up epic; don't paper over.
- **R6:** Bounded runtime: ≤500 iterations per property; total reversal-test execution ≤45 seconds.
- **R7:** Full verify GREEN; nine sim goldens GREEN; detekt unchanged.
- **R8:** Diff scope: 1 new test file + 0 production-code changes. Total ≤2 files, ≤300 LOC.

## Dependencies

This epic depends on **fn-26-engine-pass-a-step-function-property** for Kotest setup + EngineGenerators. Sequence: fn-26 → fn-27.

## Review considerations

- **FP / type safety**: reversal equivalence requires `SimState` to have structural equality. Verify at task start. **Reviewer focus**: confirm comparison excludes the right fields (now/seq/audit) and doesn't accidentally compare reference identity.
- **Test architecture**: same Kotest spec pattern as fn-26. **Reviewer focus**: confirm reversal-pair inventory is accurate and exhaustive within the in-scope event classes.
- **Impact**: scoped to :sim/jvmTest.
- **Operational ATC correctness / applicability**: directly tests an ATC-correctness claim (reversible state transitions). **Reviewer focus**: confirm the "audit field exclusion list" doesn't accidentally hide a real reversal defect.

## References

- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt` — instruction + reversal sealed types
- fn-12 spec (`Runway.obstruction` migration; documented obstruction-set + clear reversal)
- fn-15 spec (G3a-react tailwind; documented 2-transition wind shift)
- fn-13 spec (G3a continue-approach; documented landing-clearance cancel)
- `.flow/memory/knowledge/conventions/rich-world-domain-2026-05-15.md` — rich-world-domain entity-field principle
- `STRATEGY.md` — "every state transition has its reversal tested before its forward path ships"
