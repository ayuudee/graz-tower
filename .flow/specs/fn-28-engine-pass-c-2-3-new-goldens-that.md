# fn-28 — Engine pass C: 4 new goldens + foundation extensions — REVISED post plan-review round 5

## Round-5 Fixes (locked atop round-4)

- **`CompletionMode.NON_COMPLETING`** (Critical 1): existing `CompletionMode` enum has only `PHYSICAL / REPORTED / INSTRUCTION_GATED / TIMED / INSTANT`. Round-5 adds `NON_COMPLETING` with full 4-consumer audit (same surfaces as `MissionStep.DECLINE_DEPARTURE` / `ABORTED`: `isPhysicallyComplete` returns false; `skipCompletedSteps` does NOT skip past; transmission-logic emits nothing; `planRoute` no-op/at-rest). Task .2 lands the new mode + audit. DECLINE_DEPARTURE + ABORTED use `CompletionMode.NON_COMPLETING`. **R20** added.
- **No `GA_AT_DEST`** anywhere, including task .7 (Critical 2): task .7 spec uses "existing GA TaskNodes chosen in .6" everywhere; explicit acceptance: no `GA_AT_DEST` enum value or placeholder appears in test/spec.
- **DA formula + DensityAltitudeInput module = `:pilot`** (Major 1): both live in `:pilot` to avoid cyclic dep with `:protocol`. `computeDensityAltitudeFeet` home is `pilot/.../DensityAltitudeFormula.kt`. Constants like `ISA_TEMP_CELSIUS = 15.0` + `LAPSE_RATE_CELSIUS_PER_FT = 1.98 / 1000.0` live in the same file.
- **`AircraftType.maxDensityAltitudeFt: Feet?` nullable** (Major 2): C172 = `Feet.unsafe(5000)` per FAA AC 61-107B §3-1 (light GA training threshold); B738 = `null` (DA decline is a light-GA concept; jets have flat-rated thrust + high-altitude design — no light-GA DA decline). KDoc explains the applicability semantic. Pilot DA recognition gates on `aircraft.type.maxDensityAltitudeFt?.let { da > it } ?: false`. Future heavy-turbine performance modeling lives in a sibling field.
- **Abort brief time pinned to event observation** (Minor 1): the positive sim golden's `EngineFailureAt(t)` is computed relative to observed events — specifically, after `ClearedForTakeoff` is processed and before the first physics tick that would set `speedMps >= rotationSpeedMps`. .9 acceptance includes precondition assertion: "the instructor brief fires at a sim time AFTER `ClearedForTakeoff` is in the trace AND BEFORE the first `PhysicsTick` that would advance speed past `rotationSpeedMps`". KDoc documents the brief-time computation.

Round 1-4 fixes carry forward unchanged.

## Impl-time decisions deferred to worker (round-13 cleanup)

These items the reviewer flagged as worth specifying further; per the project's `## Resolved during implementation` discipline, the worker documents the chosen path in the task's resolution section rather than locking spec-time. They're not blockers — they're impl-detail conventions:

- **.6 / .7 wording**: recognition fires through widened `derive*Event` eligibility disjunction (added in .6 acceptance); apply dispatches through the existing applier fork. .7's KDoc should reflect this rather than implying recognition happens inside appliers.
- **.8 / .9 `EngineFailureAt` dual-path**: .8's `toInitialEvents(baseSeq)` helper supports the fixture-time path; .9's test-setup-time path needs a dynamic enqueue API (likely `EventQueue.insertAt(time, event)` or similar). .8 documents which API .9 will use; .9 adds the path or extends if needed.
- **.1 elevation source fail-closed**: projection requires `Aerodrome.elevation` convertible to `Feet`; missing or unconvertible elevation omits the DA input entry. .1's projection-fail-closed test extends to cover the elevation source.

## R20 (NEW round 5, refined rounds 7 + 15)

`CompletionMode.NON_COMPLETING` mode added. **Ownership split** (round-15 Minor 1 clarification): the `CompletionMode.NON_COMPLETING` dispatch audit (the new enum value + its CompletionMode-driven consumer arms) lands in .2. MissionStep-specific consumer arms land with their respective enum additions: `DECLINE_DEPARTURE` MissionStep + its arms in .2; `ABORTED` MissionStep + its arms in .8. .2 does NOT pre-emptively add ABORTED arms (would not compile before .8).

**CORRECT consumer sites** (round-7 Major 1 fix):
- `CompletionMode.NON_COMPLETING -> false` in `PilotCognitive.isStepComplete` (the actual `CompletionMode` dispatch site)
- `stepTransmission(missionStep)` in `PilotCognitive.kt`: handles `MissionStep.DECLINE_DEPARTURE` / `ABORTED` → emits nothing (this is a `MissionStep`-driven site, not CompletionMode-driven; audit lives here for the new MissionSteps' R15 audit)
- `skipCompletedSteps(...)` (locate file via grep): does NOT skip past `DECLINE_DEPARTURE` / `ABORTED` MissionSteps
- `planRoute(...)` in `Pilot.kt`: at-rest / no-op for `DECLINE_DEPARTURE` / `ABORTED` MissionSteps
- `isPhysicallyComplete(missionStep)` (locate file): audit only if needed — it consumes `MissionStep`, not `CompletionMode`; default-arm behavior likely already correct for new non-completing MissionSteps but explicit comment confirms

Audit-grep at .2 impl time: `grep -rE 'when.*CompletionMode|CompletionMode\.'` across the project — every match enumerated in `## Resolved during implementation`. Same grep for `when.*MissionStep` to cover R15 (DECLINE_DEPARTURE) and R15 (ABORTED in .8).

## R21 (NEW round 6)

**Final `derivePilotEvent` branch order**, fully-named to disambiguate "DA" (decision-altitude vs density-altitude):

```
DecisionAltitudeWithoutClearance → DensityAltitudeDecline → AbortTakeoff → TailwindLimitExceeded → CrosswindLimitExceeded
```

The new branches (DensityAltitudeDecline, AbortTakeoff) slot between the existing DecisionAltitudeWithoutClearance and existing Tailwind/Crosswind. KDoc on `derivePilotEvent` documents the final order and rationale.

## R22 (NEW round 6, refined round 7)

**Transit-arrival GA continuation TaskNode suffix locked to `[goAroundTask(), circuitTask(), groundArrivalTask()]`** (round-7 Major 2 fix — includes the post-recovery `groundArrivalTask()` so the recovery landing completes normally, NOT terminal at LAND). Specifically: the Transit-arrival mission's flat task-list has its suffix from the active arrival primitive replaced with `[goAroundTask(), circuitTask(), groundArrivalTask()]` — preserves the full recovery-landing-and-taxi continuation that the existing Transit mission has after its original LAND.

The G3b sim test in .7 asserts the suffix-replacement happened (sticky-witness on mission-tree shape) + the kinematic non-event "aircraft never lands at LJMB **within the test's time window**" — the time window ends after the GA decision fires + the mission tree shows the GA + circuit subtree active (before the recovery landing would have time to complete). NOT a contradiction with R22's full continuation: in production, the recovery lands eventually; in the test, the window is bounded.

If the existing `goAroundTask()` / `circuitTask()` / `groundArrivalTask()` helpers don't exist by those exact names, .6's `## Resolved during implementation` documents the actual helper names + sequence; the suffix shape (GA-then-circuit-then-ground-arrival) is the contract.

## R23 (NEW round 6, refined round 7)

**Multi-aircraft GA-state belief** lives in **`BeliefState.goAroundInProgressByRunway: Map<RunwayId, GoAroundInProgress>`** (round-7 Major 3 fix — persistent state in `BeliefState`, NOT `ControllerView` which is rebuilt per cycle). `GoAroundInProgress` is a typed record carrying `(aircraftId: AircraftId, setAtTime: SimTime)`. `ControllerView` derives the current value via projection if needed.

**Lifecycle** (round-7 Major 4 fix — drop unsafe positionPoint-vacate clause):
- **SET**: on `Report(GoingAround)` reception. Aircraft is on final at the moment of GA (per the wind-reactive precondition); positionPoint may already not be on runway proper.
- **CLEARED** on observable (any of):
  - (a) pattern-rejoin transmission from the tracked aircraft: `Report(Downwind)` / `Report(Final)` / `Report(Base)`, signaling the aircraft has rejoined the pattern post-GA;
  - (b) explicit GA-complete report (if a future fn-28 sibling defines one; out-of-scope for v1);
  - (c) deterministic 60s timeout from `setAtTime`.

**Tie-breaking** (round-7 Minor 1 fix — coherent rule): **first-writer-wins until cleared**. When a GA report arrives for a runway that already has an active belief entry, the new report is IGNORED until the existing entry clears. This is deterministic + simple.

Tests: GA report → ExtendDownwind fires once (no per-cycle refire while belief is active); pattern-rejoin transmission → belief clears → ExtendDownwind cancelled via supersession or explicit cancel; 60s timeout → belief clears → ExtendDownwind cancelled.

## R24 (NEW round 6)

**`Feet` typed-units residency**: confirmed/required to live in `:protocol` (likely already exists alongside `Knots`). If missing, .1 adds it before `DensityAltitudeInput` in `:pilot` and `AircraftType.maxDensityAltitudeFt` in `:protocol` can both depend on it. .1's acceptance includes the audit.

---

# fn-28 — Engine pass C: 4 new goldens + foundation extensions — REVISED post plan-review round 4

## Overview

(Unchanged scope from prior versions; only the foundation-primitive signatures + named guards + Transit GA intent + DA formula refined in round 4.)

The 4 goldens:
1. **G3a-react-density-altitude** — apron-stay decline via `replaceFromActivePrimitive([PrimitiveTask(DECLINE_DEPARTURE, …)])`.
2. **G3a-react-multi-aircraft** — A declares GA; B receives ExtendDownwind.
3. **G3b-cross-aerodrome-react** — Transit-arrival GA via the new guard + suffix-replace primitive; suffix uses **existing GA MissionSteps** (no new enum value); intent matches existing reactive-GA Tick A (`targetSpeedMps = climbSpeedMps`, `phase = Final`, `route = None`, target altitude = pattern altitude).
4. **G0-VFR-abort-takeoff** — pre-rotation engine failure; abort via `replaceFromActivePrimitive([PrimitiveTask(ABORTED, …)])` + instant-speed engine-off clamp.

Round-4 locked decisions:

- **Suffix-replace primitive signature** (Critical 1 / R13): `CompoundTask.replaceFromActivePrimitive(newSuffix: List<TaskNode>): CompoundTask`. Takes full `TaskNode` (sealed parent of `PrimitiveTask` + `CompoundTask`) so callers construct primitives with the required `CompletionMode` + can include nested compounds in the suffix. The primitive flattens nested-vs-flat trees by walking to the active primitive in the rendered task-list and replacing the suffix from that anchor; if the active position is inside a nested compound, the rewrite happens at that compound's level (replace from active-primitive-onward within the compound, leaving outer parents intact). Documented semantics + unit test covering nested + flat shapes.
- **No `GA_AT_DEST` enum value** (Critical 2 / R15 corollary): Transit-arrival GA continuation uses **existing GA MissionSteps** (the steps that today's circuit-GA flow uses — looked up at impl time; likely `GO_AROUND`/`CLIMB_OUT`/etc.). The only new MissionStep values are `DECLINE_DEPARTURE` (DA) + `ABORTED` (abort). R15's audit checklist applies only to those two.
- **Two split eligibility guards** (Major 3): `isDensityAltitudeDeclineEligible(mission: PilotMission)` for DA decline (pre-taxi shapes); `isAbortTakeoffEligible(mission: PilotMission)` for abort (takeoff-roll shapes). NOT a single shared guard — mission positions are incompatible. Each guard ships in its respective task (.2 + .9). KDoc lists matched shapes per guard.
- **Transit GA intent** (Major 2): aligns with existing reactive-GA Tick A — `targetSpeedMps = aircraft.type.kinematics.climbSpeedMps`, `phase = Final`, `route = None`, `targetAltitudeM = pattern_altitude`. NOT `targetSpeedMps = 0` (that's DA + abort). Documented in .6.
- **Transit-arrival dispatch** (Major 1): existing `applyCrosswindGoAround` / `applyTailwindGoAround` are extended to detect Transit-arrival mission shape via `isTransitArrivalReactiveGoAroundEligible(aircraft, mission)` and dispatch to the suffix-replace path (instead of their current circuit-only rewrite). Single-applier dispatch fork; no new event leaves; no new pilotDecide branches.
- **DA computation as named pure function** (Major 4): `fun computeDensityAltitudeFeet(input: DensityAltitudeInput): Feet` lives at a documented home (likely `:protocol` or `:pilot` per project structure — chosen at impl time). Formula: `pressure_altitude_ft = fieldElevation_ft + (1013.25 - qnh_hPa) * 30; isa_temp_celsius = 15.0 - (fieldElevation_ft / 1000.0) * 1.98; density_altitude_ft = pressure_altitude_ft + 120 * (oat_celsius - isa_temp_celsius)`. Rounding: integer ft (use Double internally; round at boundary). Field elevation source: `Aerodrome.elevation` from world data. Missing data (oat or qnh null): caller doesn't see a `DensityAltitudeInput` entry in projection (fail-closed at the construction site per .1). Numerical test asserts against the pure function's output, not against prose.
- **Abort "on-runway" gate** (Major 5): `aircraft.phase == PilotPhase.TakeoffRoll` is the v1 runway proxy. No `WorldIndex` lookup. KDoc explains.
- **Initial-events seq policy** (Minor 1 round 4): the `toInitialEvents(baseSeq)` helper pre-stamps seq monotonically; fixture builder MUST advance `SimState.seq` to `baseSeq + emittedCount` so subsequent driver-emitted events get non-overlapping seq numbers. .8 acceptance includes the seq-range audit.
- **Fixture only injects `EngineFailureAt`** (Minor 2 round 3 carried): pilot+controller flow handles taxi/lineup/takeoff normally.

**Closure tally: 6 archive flips, 0 new deferments, net –6 (unchanged).**

## Boundaries / non-goals

(All round-3 outs carried, plus round-4:)
- **Out: `MissionStep.GA_AT_DEST` or any new GA-related enum value.** Transit GA uses existing GA MissionSteps.
- **Out: bounded-integrator kinematics.** Instant-speed model + engine-off clamp only.
- **Out: bounded-integrator deceleration constants.** No `abortDecelMs2`.
- **Out: WorldIndex passing into `derivePilotEvent`.** `PilotPhase.TakeoffRoll` is the v1 runway proxy for abort.
- **Out: shared eligibility guard between DA decline + abort.** Two separate guards.

## Strategy Alignment

(Unchanged.)

## Decision context (rounds 1+2+3+4 fixes locked)

Round 4 fixes:
- `replaceFromActivePrimitive(newSuffix: List<TaskNode>)` — full TaskNode list (Critical 1).
- Drop `GA_AT_DEST`; Transit GA uses existing GA MissionSteps (Critical 2).
- Existing GA appliers extended with Transit dispatch fork (Major 1).
- Transit GA intent = climb-speed (Major 2).
- Two split guards `isDensityAltitudeDeclineEligible` + `isAbortTakeoffEligible` (Major 3).
- DA computation as named pure function with documented formula + rounding + missing-data behavior (Major 4).
- Abort "on-runway" = `PilotPhase.TakeoffRoll` (Major 5).
- Initial-events seq policy: fixture advances SimState.seq past pre-stamped events (Minor 1 round 4).

Round 3 fixes carried: cognitive-transmission suppression; `DECLINE_DEPARTURE` + `ABORTED` MissionSteps; instant-speed clamp; Transit guard signature `(aircraft, mission)`; concrete fixture QNH; abort fixture injects only `EngineFailureAt`.

Round 2 fixes carried: typed DA projection + signature; AgentId.System; no wake event; no RegDB for POH; soft R8.

Round 1 fixes carried: WeatherObservation OAT; task-tree rewrite vs rewind; multi-aircraft reframing; deferment net –6; G3b drops SID; R8 raised.

## Resolved via Codebase

(Carried + new round-4 entries:)
- `CompoundTask` task tree: `TaskNode` is the sealed parent of `PrimitiveTask` + `CompoundTask`; `PrimitiveTask` requires `CompletionMode` per primitive. Suffix-replace primitive uses `List<TaskNode>` accordingly.
- Transit mission is "flat" only at the arrival-primitives level (within the Transit compound, the arrival primitives are direct children); nested-vs-flat is mixed at the outer mission shape. Suffix-replace targets the rendered active position regardless.
- Existing `applyCrosswindGoAround` / `applyTailwindGoAround` are circuit-only today. Round-4 Major 1 fix extends them with a Transit-shape dispatch fork.
- Existing reactive-GA Tick A intent: `targetSpeedMps = aircraft.type.kinematics.climbSpeedMps`, `phase = Final`, `route = None`, target altitude = pattern altitude. fn-28 Transit GA mirrors.
- `derivePilotEvent` receives aircraft + mission + weather; no `WorldIndex` parameter. Round-4 Major 5: `aircraft.phase == PilotPhase.TakeoffRoll` is the v1 on-runway proxy.
- `LOWG` elevation ≈ 1115 ft per AGENTS.md / world data. ISA at 1115 ft ≈ 12.79°C. "ISA+35°C" at LOWG = 47.79°C (concrete numeric for fixture, not prose).

## Citation triples

(Unchanged.)

## Edge cases

(Unchanged.)

## Open Questions

- Transit GA continuation MissionStep sequence: looked up at impl time from existing GA flow (`applyPlannedGoAround`-style steps). Resolved in .6's `## Resolved during implementation`.
- ~~`computeDensityAltitudeFeet` home module~~ — RESOLVED round-5: locked to `:pilot` (see round-5 fixes block).
- `PilotDecisionTick` latency post-EngineFailure: observed at .9 impl time; if insufficient, .9 may emit explicit `PilotDecisionTick` (NOT `PilotProcessingComplete`).

## Acceptance

- **R1:** Interview + plan-review rounds 1+2+3+4 fixes locked.
- **R2:** 9 tasks per revised plan (see Decision context for round-4 updates).
- **R3-R12:** carry forward unchanged.
- **R13:** `replaceFromActivePrimitive(newSuffix: List<TaskNode>): CompoundTask` is the SOLE **fn-28-introduced** task-tree rewrite primitive (round-11 Major 2 fix). The existing codebase `replaceChild` mechanism (used by existing GA appliers' circuit-only rewrite branch) is UNCHANGED and continues to apply on the non-Transit dispatch path within `applyCrosswindGoAround` / `applyTailwindGoAround`. fn-28's three new call sites — DA decline (`[PrimitiveTask(DECLINE_DEPARTURE, NON_COMPLETING)]`), abort (`[PrimitiveTask(ABORTED, NON_COMPLETING)]`), Transit GA suffix (`[goAroundTask(), circuitTask(), groundArrivalTask()]`) — all use the new primitive. NO retroactive migration of existing GA appliers' `replaceChild` branches. Direct unit test + nested + flat integration tests.
- **R14:** Cognitive-transmission suppression on DA decline + abort apply.
- **R15:** Only `DECLINE_DEPARTURE` + `ABORTED` are new MissionStep values. Consumer audit at `isPhysicallyComplete`, `skipCompletedSteps`, transmission-logic, `planRoute` for BOTH.
- **R16** (NEW round 4): Two split eligibility guards — `isDensityAltitudeDeclineEligible(mission)` for DA (pre-taxi shapes); `isAbortTakeoffEligible(mission)` for abort (takeoff-roll shapes). NOT a shared guard.
- **R17** (NEW round 4): `computeDensityAltitudeFeet(input: DensityAltitudeInput): Feet` named pure function with documented formula + integer-ft rounding + fail-closed contract. .2 lands the function; .3 + tests assert against its output.
- **R18** (NEW round 4): Transit-arrival GA dispatch via extended `applyCrosswindGoAround` / `applyTailwindGoAround` that check `isTransitArrivalReactiveGoAroundEligible(aircraft, mission)` and choose suffix-replace path. NO new event leaves; NO new pilotDecide branches.
- **R19** (NEW round 4): Transit-arrival GA apply intent aligns with existing Tick A (`targetSpeedMps = climbSpeedMps`, `phase = Final`, `route = None`, target altitude = pattern altitude). NOT `targetSpeedMps = 0`.

## Early proof point

Task .2 lands `replaceFromActivePrimitive(List<TaskNode>)` + `MissionStep.DECLINE_DEPARTURE` + `computeDensityAltitudeFeet` named function + `isDensityAltitudeDeclineEligible` guard + cognitive-suppression mechanism. If .2 fails plan-review or impl-review, re-evaluate before heavier work.

## Quick commands

(Unchanged.)

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | Rounds 1-4 lock | (pre-task) | — |
| R2-DA | (3 tasks) | .1, .2, .3 | — |
| R2-MULTI | (2 tasks) | .4, .5 | — |
| R2-G3B | (2 tasks) | .6, .7 | — |
| R2-ABORT | (2 tasks) | .8, .9 | — |
| R2a-R2e | per-golden | (each) | — |
| R3  | lineup + AGENTS/STRATEGY/.plan | .9 | — |
| R4  | per-task verify | (each) | — |
| R5  | citations | (each) | — |
| R6  | LIFTED | (cross) | — |
| R7  | full verify + audits | (each) | — |
| R8  | soft trigger ≤55/≤5500 | (across) | — |
| R9  | decision record + no Emergency<T> | (cross) | — |
| R10 | open Qs in task | (each) | — |
| R11 | fn-29 scope-update | — | Deferred fn-29 |
| R12 | engine-off clamp | .8 verified .9 | — |
| R13 | `replaceFromActivePrimitive(List<TaskNode>)` sole fn-28-introduced suffix-rewrite primitive; existing `replaceChild` branches exempt | .2 reused .6 .9 | — |
| R14 | cognitive suppression | .2 + .9 | — |
| R15 | DECLINE_DEPARTURE + ABORTED audit | .2 (DECLINE_DEPARTURE) + .8 (ABORTED) + .9 (consumer) | — |
| R16 | split eligibility guards | .2 + .9 | — |
| R17 | `computeDensityAltitudeFeet` named pure function | .2 used by .3 | — |
| R18 | Transit dispatch via extended appliers | .6 verified .7 | — |
| R19 | Transit GA Tick-A-aligned intent | .6 verified .7 | — |
| R20 | CompletionMode.NON_COMPLETING with grep audit | .2 + .8 + .9 | — |
| R21 | derivePilotEvent branch order fully-named | .2 (DA) + .9 (abort) | — |
| R22 | Transit GA suffix = `[goAroundTask(), circuitTask(), groundArrivalTask()]` | .6 verified .7 | — |
| R23 | Controller goAroundInProgressByRunway belief lifecycle | .4 verified .5 | — |
| R24 | `Feet` typed-units residency in `:protocol` | .1 | — |

## Review considerations

(Carried + round-4 additions:)
- **R13 primitive type generality**: `List<TaskNode>` permits primitive + nested compound in suffix. Reviewer focus: any caller that passes raw `MissionStep` instead of `PrimitiveTask` is a bug.
- **R16 guard split**: NO shared guard between DA + abort. Reviewer focus: any cross-reference between the two guards is a bug.
- **R17 DA formula**: numerical assertions in tests come from the named pure function. Reviewer focus: any prose-DA assertion is a bug.
- **R18 dispatch**: existing GA appliers carry the Transit fork. Reviewer focus: new event leaves or new pilotDecide branches outside the appliers are a bug.
- **R19 Transit GA intent**: `targetSpeedMps = climbSpeedMps`, NOT 0. Reviewer focus: any zero-target in Transit GA apply is a bug.

## References

(Unchanged + round-4 receipt.)
