---
satisfies: [R2-ABORT, R2a, R2b, R2c, R2d, R2e, R3, R4, R5, R6, R7, R9, R10, R12, R13, R14, R15, R16, R20, R21]
---

## Description

Pilot abort branch + sim golden + epic-close ritual. Round-5 refinements integrated.

- **`isAbortTakeoffEligible(mission: PilotMission)`** — named guard for abort (R16); takeoff-roll mission shapes. Distinct from .2's `isDensityAltitudeDeclineEligible`.
- **Pilot abort recognition** (`deriveAbortTakeoffEvent`): 4-check gate (engineRunning=false; speed<rotationSpeedMps; phase==TakeoffRoll; isAbortTakeoffEligible(mission)). Branch placement: AFTER DA, BEFORE tailwind/crosswind.
- **Pilot abort apply** (`applyAbortTakeoff`): `mission.replaceFromActivePrimitive(listOf(PrimitiveTask(MissionStep.ABORTED, CompletionMode.NON_COMPLETING)))` (R13 + R20 — ABORTED uses the new `NON_COMPLETING` mode from .2); sets `targetSpeedMps = 0`; returns `suppressSameTickCognitive = true` (R14).
- **Instant-speed engine-off clamp** (R12 from .8): aircraft instantly stops same tick.
- **Sim golden** — 2 scenarios:
  - **Positive (pre-rotation)** — instructor brief time **pinned relative to observed events** (round-5 Minor 1): brief fires at sim time AFTER `ClearedForTakeoff` is in the trace AND BEFORE the first `PhysicsTick` that would advance `speedMps >= rotationSpeedMps`. KDoc documents brief-time computation. Precondition assertion: trace shows `ClearedForTakeoff` + `aircraft.speedMps < rotationSpeedMps` at the instant `EngineFailure` fires. After EngineFailure: engineRunning=false → next PilotDecisionTick → recognition fires → apply rewrites to `[ABORTED]` → instant-speed stops aircraft same tick. Three-layer pin.
  - **Negative (after-rotation)** — same flow, EngineFailureAt(t=post-rotation, speedMps>=rotationSpeedMps). Abort gate FAILS on speed check. Test ASSERTS gate did not fire; ENDS (round-2 Major 7). No further ticks.
- Archive flip for `D-AUDIT.9.III-FOLLOWUP`.
- Epic-close: STRATEGY.md, AGENTS.md, .plan.

**Size:** L
**Files:**
- `pilot/.../observe/PilotEvent.kt` — `isAbortTakeoffEligible` named guard + `deriveAbortTakeoffEvent`
- `pilot/.../Pilot.kt` — `applyAbortTakeoff`
- Tests: guard (matched+unmatched); recognition gates; cognitive-suppression unit; on-runway-proxy test
- `sim/src/jvmTest/.../G0AbortTakeoffEngineFailureTest.kt` (new)
- `sim/src/jvmTest/.../Fixtures.kt` — `LOWG_ABORT_TAKEOFF_PRE_VR` + `POST_VR` variants: ONLY EngineFailureAt injected via instructor; pilot+controller flow handles taxi/lineup/takeoff
- `docs/deferments.md` — archive flip
- `STRATEGY.md` — 9→13; sextuple-reactive; first emergency; last_updated bump
- `AGENTS.md` — "Nine..." → "Thirteen..." + KDoc descriptions for 4 new anchors
- `.plan` — fn-28 closure stub

## Approach

- **`isAbortTakeoffEligible`** (R16): mission-shape guard for takeoff-roll departure shapes. NOT shared with DA's guard.
- **Recognition gate (4 checks)**: engineRunning=false; speed<rotationSpeedMps; phase==TakeoffRoll (round-4 Major 5 v1 runway proxy); isAbortTakeoffEligible(mission).
- **Apply**: `replaceFromActivePrimitive([PrimitiveTask(ABORTED, CompletionMode.NON_COMPLETING)])` (R13 + R20) + targetSpeedMps=0 + suppressSameTickCognitive=true (R14).
- **Positive scenario brief time** (round-5 Minor 1, refined round-11 Major 3): instructor's `EngineFailureAt(t)` is **computed at TEST SETUP TIME, NOT fixture-build time**. Test setup runs the sim until it observes `ClearedForTakeoff` in the trace (which sets `targetSpeedMps = climbSpeedMps`); test then injects `EngineFailureAt(t = ClearedForTakeoff_time + 1ms)` via the instructor-channel helper from .8 BEFORE running the next `PhysicsTick`. Fixture is static base data; test setup is the dynamic-trace-observer. Precondition assertion: `aircraft.speedMps < rotationSpeedMps` at the EngineFailure tick.
- **Negative scenario**: brief time AFTER the PhysicsTick that crossed rotation speed. Aircraft normally rotates, climbs briefly, then EngineFailure fires after `speedMps >= rotationSpeedMps`. Abort gate fails on speed. Test asserts; ends.

## Approach (epic-close)

(Unchanged from prior version.)

## Investigation targets

- Task .8 outputs (engineRunning, EngineFailure, clamp, ABORTED, InstructorInput, helper, NON_COMPLETING)
- Task .2 outputs (replaceFromActivePrimitive, cognitive-suppression, named-guard pattern)
- `pilot/.../observe/PilotEvent.kt:360-440`
- `pilot/.../Pilot.kt:195-1200`
- `protocol/.../AircraftType.kt:186` (rotationSpeedMps)
- `pilot/.../AircraftState.kt` (PilotPhase incl. TakeoffRoll)
- `sim/.../G3aPilotReactiveTailwindTest.kt:47-310`
- Existing `ClearedForTakeoff` transmission/event handling — for the brief-time pin
- `STRATEGY.md` / `AGENTS.md` / `.plan`
- `docs/deferments-CONVENTION.md:314-340`
- `.flow/memory/knowledge/decisions/instructor-channel-causation-for-sim-2026-05-16.md`

## Key context

- **R16**: split guard; R12: clamp; R13: suffix-replace; R14: cognitive-suppression; R15: ABORTED audit (in .8); R20: NON_COMPLETING (in .2; ABORTED uses).
- **Round-4 Major 5**: PilotPhase.TakeoffRoll is v1 on-runway proxy.
- **Round-5 Minor 1**: brief time pinned relative to ClearedForTakeoff + first PhysicsTick.
- **Round-3 Minor 2**: fixture only injects EngineFailureAt; normal flow runs.
- **Round-2 Major 7**: negative ends after gate-assertion.

## Acceptance

- [ ] `isAbortTakeoffEligible(mission)` named guard + unit test
- [ ] `deriveAbortTakeoffEvent`: 4-check gate
- [ ] `applyAbortTakeoff`: `replaceFromActivePrimitive([PrimitiveTask(ABORTED, NON_COMPLETING)])`; targetSpeedMps=0; suppressSameTickCognitive=true
- [ ] **`PilotEvent.AbortTakeoff` sealed leaf added** (round-15 Major 1 + round-16 Major 4 — `time` field would require `derivePilotEvent` signature change): fields needed for trace/debugging from data ALREADY available to `derivePilotEvent`: `aircraftId`, `speedAtFailure` (read from `aircraft.speedMps`). NO `time` field unless `derivePilotEvent` signature is extended to accept `now: SimTime` (which is out of scope for fn-28 — sibling events don't carry `time` either). `pilotDecide`'s exhaustive `when` dispatches it. Final R21 order in `derivePilotEvent` finalized: `DecisionAltitudeWithoutClearance → DensityAltitudeDecline → AbortTakeoff → TailwindLimitExceeded → CrosswindLimitExceeded`. KDoc on `derivePilotEvent` updated
- [ ] **Cognitive-suppression covers ALL `pilotDecide` return paths for abort** (round-15 Major 2 — mirror .2's contract): abort apply's `suppressSameTickCognitive=true` flag must filter `cognitive.transmissions` BEFORE every `PilotOutput` construction site — `PlanRouteOutcome.Plan` AND `Skip` AND error/fallback paths. Unit test exercises every return path with abort recognition firing
- [ ] Unit tests: positive; negative speed≥VR; negative engineRunning=true; negative wrong phase
- [ ] Cognitive-suppression unit: zero transmissions emitted same tick
- [ ] `G0AbortTakeoffEngineFailureTest.kt` with exhaustive KDoc + 2 `@Test` methods
- [ ] **Positive scenario brief time pinned relative to `ClearedForTakeoff` + first `PhysicsTick`** (round-5 Minor 1); precondition assertion: `aircraft.speedMps < rotationSpeedMps` at EngineFailure tick. **Brief time computation happens at TEST SETUP, NOT fixture-load time** (round-6 Minor 3): the test runs a short setup phase observing the trace until `ClearedForTakeoff` is processed, then injects `EngineFailureAt(t)` into the event queue (or via instructor-channel helper from .8) before the next PhysicsTick. Fixture object is static; test setup is the dynamic-trace-observer
- [ ] `deriveAbortTakeoffEvent` slots in final `derivePilotEvent` order `DecisionAltitudeWithoutClearance → DensityAltitudeDecline → AbortTakeoff → TailwindLimitExceeded → CrosswindLimitExceeded` (R21)
- [ ] Positive: three-layer pin; instant-stop same tick; never airborne; zero cognitive transmissions (R14)
- [ ] Negative: ends after gate-assertion; no further ticks; KDoc documents
- [ ] `Fixtures.LOWG_ABORT_TAKEOFF_PRE_VR` + `POST_VR`: **base scenario data ONLY** (round-7 Minor 3): the fixture provides world + initial Spawn + mission tree etc. The `EngineFailureAt(t)` event is INJECTED DYNAMICALLY during the test's setup phase (test observes trace until `ClearedForTakeoff` is processed, then injects via instructor-channel helper from .8). Fixture is static-data-only; test setup is the dynamic-trace-observer. Pilot+controller flow handles taxi/lineup/takeoff naturally
- [ ] `docs/deferments.md` archive flip per §8
- [ ] STRATEGY.md refreshed
- [ ] AGENTS.md refreshed
- [ ] .plan closure stub
- [ ] Targeted GREEN; full verify GREEN; 13 sim goldens
- [ ] `## Resolved during implementation`: brief time computation + VR threshold + open Qs

## Done summary

_(filled by `flowctl done` at task close)_

## Evidence

_(filled by `flowctl done` at task close)_
