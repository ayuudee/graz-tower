# fn-27 — Engine pass B: reversal property tests **[PARKED 2026-05-16 — planning defect]**

## PARKED — premise does not hold against the codebase

This epic was scoped to introduce **per-event reversal property tests** — for each `SimEvent` family, exercise the forward path and assert the inverse event undoes the state mutation. The plan-review phase (2026-05-16) caught the fundamental defect:

**The listed reversal pairs (`WeatherShift` ↔ inverse, `RunwayObstructed` / `RunwayCleared`, `LandingClearanceIssued` / `LandingClearanceCancelled`, etc.) do not exist as `SimEvent` subtypes.**

`SimEvent` declarations (`sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimEvent.kt`) enumerate: `PhysicsTick`, `PilotDecisionTick`, `ControllerCycle`, `Spawn`, `TransmissionStart`, `TransmissionEnd`, `PilotProcessingComplete`, `MissedHandoffDetected`, `FlightPlanFiled`, `AtisIssued`. **None are symmetric pairs.** The cancel/clear-style reversibility this epic envisioned is not modeled at the event layer — it's satisfied at the *trace* layer per existing golden tests (fn-12 obstruction-clears-in-time, fn-15 tailwind recovery, etc.).

## Redirect — intent absorbed into fn-29

The reversal-symmetry intent is **not lost**:

- **fn-29 (Engine pass D — invariant pumping over state trace)** already extends `runUntilWithStateTrace` with per-event-boundary invariants. The reversibility intuition lives there as a *trace invariant* (e.g., "state.weatherByAerodrome at tick N matches state.weatherByAerodrome at tick M for any (N, M) where no `AtisIssued`/weather-shift event occurred between them").
- **fn-26 (Engine pass A — step-function property)** already includes `step(s, e) == step(s, e)` (determinism) and `step(s, e).newState.now >= state.now` (monotonicity). The forward-direction symmetric properties are covered.

Per-event reversal as a *separate* test type would require adding inverse `SimEvent` subtypes that don't exist (and shouldn't be added speculatively for testing alone — that's reshape-engine-to-fit-test, which AGENTS.md forbids).

## Decision

**Park fn-27.** No tasks were authored; no code was written. The slot in the engine-pass-B-C-D sequence is retired. fn-26 + fn-28 + fn-29 carry the engine-solidity pass.

If a future scenario genuinely needs symmetric event pairs (e.g., a real ATC operational pattern like `ClearanceIssued` / `ClearanceCancelled` becomes load-bearing for some controller test), file a fresh epic with a real codebase grounding — don't unpark this one.

## References

- fn-26 spec — step-function property (determinism + monotonicity covers forward symmetric)
- fn-29 spec — invariant pumping over state trace (trace-layer reversibility lives here)
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimEvent.kt` — authoritative SimEvent enumeration (no symmetric pairs)
- AGENTS.md — "don't reshape engine to fit test"
