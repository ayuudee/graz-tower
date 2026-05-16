---
satisfies: [R2-G3B, R6, R7, R10, R13, R18, R19, R22]
---

## Description

Cross-aerodrome foundation: TWO problems.

**(a) Weather projection FIX-OR-AUDIT** — no new deferments.

**(b) Transit-arrival reactive-GA recognition + dispatch + apply** — round-4 Critical 2, Major 1, Major 2 fixes:
- Add `isTransitArrivalReactiveGoAroundEligible(aircraft: AircraftState, mission: PilotMission): Boolean` named guard (round-3 Major 3 + R18 trigger).
- **EXTEND BOTH recognition AND apply sides** (round-12 Major 1 fix):
  - **Recognition** (`deriveCrosswindEvent` + `deriveTailwindEvent`): widen the mission-eligibility check from `isReactiveGoAroundEligible(mission)` (circuit-only) to a disjunctive check: `isReactiveGoAroundEligible(mission) || isTransitArrivalReactiveGoAroundEligible(aircraft, mission)`. Without this widening, Transit-arrival aircraft fail recognition BEFORE applier dispatch ever runs — the original spec missed this.
  - **Apply** (`applyCrosswindGoAround` + `applyTailwindGoAround`): Transit dispatch fork (round-4 Major 1 / R18): when `isTransitArrivalReactiveGoAroundEligible(aircraft, mission)` holds, dispatch to suffix-replace via `replaceFromActivePrimitive(newSuffix)`; else-branch is existing circuit-only `replaceChild` rewrite (unchanged).
  - **Recognition+apply pipeline agreement**: BOTH sides consult the SAME disjunctive eligibility. Recognition firing without apply matching (or vice versa) is the canonical failure mode (per `recognitionapply-pipelines-need-mission-2026-05-11`).
- **NO new event leaves; NO new pilotDecide branches; NO new applier function.**
- **Suffix = existing GA MissionSteps** (round-4 Critical 2): drop `GA_AT_DEST`. The Transit GA continuation uses the same MissionSteps that today's circuit-GA flow uses (looked up at impl time from `applyPlannedGoAround` / existing GA infrastructure). KDoc names the chosen continuation in `## Resolved during implementation`.
- **Transit GA intent** (round-4 Major 2 / R19): aligns with existing Tick A — `targetSpeedMps = aircraft.type.kinematics.climbSpeedMps`, `phase = Final`, `route = None`, target altitude = pattern altitude. NOT `targetSpeedMps = 0`.

**Size:** L
**Files:**
- `sim/.../PilotWiring.kt` (or sibling) — projection-gate adjustment if (a) requires fix
- `pilot/.../PilotInput.kt:71-99` — KDoc audit
- `pilot/.../Pilot.kt` (or shared pilot-package file) — `isTransitArrivalReactiveGoAroundEligible(aircraft, mission)` named guard (round-10 Minor 2 — guard's home is `Pilot.kt` since it's apply-path-used by `applyCrosswindGoAround` / `applyTailwindGoAround`. NOT `PilotEvent.kt` where it would create layering churn. Visibility: `internal`)
- `pilot/.../Pilot.kt` — EXTEND `applyCrosswindGoAround` + `applyTailwindGoAround` with Transit dispatch fork using suffix-replace; intent uses climbSpeedMps + phase Final + route None + pattern altitude
- Tests: guard; Transit dispatch (recognition fires + applier chooses suffix-replace + intent matches Tick A); no-refire

## Approach

**(a)** Investigate projection; fix-or-audit; no new deferment.

**(b)**:
- **Guard signature** takes `(aircraft, mission)` — round-11 Major 1 corrects the predicate to be data-honest with available fields: checks (1) `mission.goal is Transit` (mission shape carries Transit-goal type); (2) active arrival primitive in the flat task-list (mission state); (3) `mission.activeRunway` resolved from the filed plan's `destinationAerodrome`-runway pairing (mission state); (4) `aircraft.phase == PilotPhase.Final` (aircraft state). NO geometric "at destination aerodrome" check — Transit + active-arrival-primitive + Final-phase is the v1 proxy. KDoc explains the proxy choice + cites round-11 Major 1 fix.
- **Dispatch via existing appliers** (R18): in `applyCrosswindGoAround` (and tailwind sibling), early-check: `if (isTransitArrivalReactiveGoAroundEligible(aircraft, mission)) return transitArrivalSuffixReplace(...)`. The else-branch is the existing circuit-only rewrite. Single dispatch fork; no new applier or event leaf.
- **Suffix = existing GA TaskNodes**: at impl time, locate the canonical GA continuation in the existing pilot-trained / reactive GA code paths. Build the same `List<TaskNode>` and pass to `replaceFromActivePrimitive`.
- **Intent** (R19): `targetSpeedMps = aircraft.type.kinematics.climbSpeedMps`, `phase = Final`, `route = None`, target altitude = `aircraft.type.patternAltitudeM` (or sibling — look up at impl time).
- **No-refire**: after apply, mission's active primitive is the first GA continuation step — guard's mission-shape check no longer matches Transit-arrival; recognition does NOT fire next tick. Unit test verifies.

## Investigation targets

- Task .2 outputs: `replaceFromActivePrimitive(List<TaskNode>)` primitive
- `pilot/.../PilotInput.kt:71-99`
- `sim/.../PilotWiring.kt`
- `pilot/.../observe/PilotEvent.kt:431-434` — existing `isReactiveGoAroundEligible`
- `pilot/.../Pilot.kt` — `applyCrosswindGoAround:896`, `applyTailwindGoAround:1017`, `applyPlannedGoAround:1110` (for the GA continuation TaskNode pattern)
- `pilot/.../PilotMission.kt` — Transit mission shape

## Key context

- **R18**: existing appliers carry Transit dispatch fork. NO new event leaves; NO new appliers.
- **R19**: Transit GA intent matches existing Tick A. NOT zero target speed.
- **Critical 2 fix**: no `GA_AT_DEST`. Use existing GA MissionSteps.
- **R13**: suffix-replace uses `List<TaskNode>`.
- **No new deferments** (epic R8).

## Acceptance

- [ ] `## Resolved during implementation`: (a) projection finding; (b) Transit GA continuation TaskNodes chosen (from existing GA flow); intent values (climbSpeedMps + Final + None + patternAltitude)
- [ ] Projection-timing audit OR fix landed (no new deferment)
- [ ] `isTransitArrivalReactiveGoAroundEligible(aircraft, mission)` named guard + unit test
- [ ] **`deriveCrosswindEvent` + `deriveTailwindEvent` widened** (round-12 Major 1): mission-eligibility check is now disjunctive `isReactiveGoAroundEligible(mission) || isTransitArrivalReactiveGoAroundEligible(aircraft, mission)`. KDoc explains. Unit tests cover both branches matching + only-circuit + only-transit + neither
- [ ] `applyCrosswindGoAround` + `applyTailwindGoAround` EXTENDED with Transit dispatch fork using `replaceFromActivePrimitive(listOf(goAroundTask(), circuitTask(), groundArrivalTask()))` (R22 / round-6 Major 4 — suffix locked to existing codebase helpers; if names differ, `## Resolved during implementation` documents)
- [ ] Transit GA intent uses `climbSpeedMps + Final + None + patternAltitude` (R19); NOT `targetSpeedMps = 0`
- [ ] **Transit branches call `resetForGoAround(now)` before/with `replaceFromActivePrimitive(...)`** (round-16 Major 2): Transit dispatch in `applyCrosswindGoAround` / `applyTailwindGoAround` must reset stale approach state (hasClearance, constraints, route overrides, joinLeg, lastReportedLeg, lastTransmittedStep) via the existing `resetForGoAround` helper or audited equivalent. Test asserts the reset fields are cleared after Transit GA apply
- [ ] No-refire unit test: post-apply mission shape no longer matches guard
- [ ] **`planRoute` Transit-cruise discriminator** (round-14 Major 1): after Transit suffix replacement + `GOING_AROUND` completion, the recovery `circuitTask()` starts at `FLY_DEPARTURE` while `mission.goal` is still `Transit`. Current code routes via `planTransitCruise(...)` — WRONG for recovery. Update `Pilot.kt::planRoute` to discriminate: treat `Transit + FLY_DEPARTURE` as cruise ONLY when active primitive is the original flat Transit departure primitive (NOT when `root.activeCompound()?.name` is `Circuit` / `CircuitAfterGoAround` / `GoAround`). Unit test: post-suffix-replacement + GOING_AROUND complete + FLY_DEPARTURE → plans recovery circuit/GA path, NOT `planTransitCruise`
- [ ] NO new event leaves; NO new pilotDecide branches; NO `GA_AT_DEST` enum
- [ ] NO new deferment filed
- [ ] `./gradlew :pilot:jvmTest :sim:jvmTest detekt --offline --no-daemon` GREEN

## Resolved during implementation

**(a) Weather projection — AUDIT, no FIX needed (no new deferment)**

The current projection in `sim/.../PilotWiring.kt::buildPilotInput` is correct
as-is. Both `weatherByAerodrome` and `densityAltitudeInputsByAerodrome` are
projected via `state.world.aerodromes.mapNotNull { (id, a) -> ... }` —
publishing the wind / DA-input slice for EVERY aerodrome whose weather has
the relevant required fields (oat + qnh + elevation for DA; wind for wind).
Geographic gating (an "ARP-circular" gate around the aircraft) is **not
needed** because the consumer helpers already fail closed on multi-aerodrome
ambiguity:

- `windForMission(mission, weatherByAerodrome)`: when the mission's goal
  carries a `destination` (Transit / Departure), the helper keys the lookup
  on the destination aerodrome. When the goal doesn't carry one (Arrival /
  CircuitTraining), the helper uses a singleton fallback (only when the map
  has exactly one entry; ≥2 entries → null). Multi-aerodrome scenarios with
  ambiguous goal keys return null — recognition fails closed.
- `densityAltitudeInputForMission(mission, densityAltitudeInputsByAerodrome)`:
  the post-round-9 helper keys on `mission.filedPlan?.departureAerodrome`
  first; falls back to singleton-only resolution. Multi-aerodrome scenarios
  without a filed plan return null — recognition fails closed.

The projection therefore carries every available aerodrome's data, and the
goal-keyed / filed-plan-keyed lookup at the recognition site is the
authoritative narrowing. A tighter ARP-circular projection gate would be
defensible (real pilots only know about nearby aerodromes), but it's
upstream-redundant with the existing mission-shape narrowing AND would risk
masking foundation defects in multi-aerodrome scenarios (the recognition
helpers' fail-closed paths would never exercise with the geographic gate
intercepting first). **No projection change in fn-28.6; no new deferment
filed.** The pilot firewall's `PilotInput.weatherByAerodrome` /
`PilotInput.densityAltitudeInputsByAerodrome` KDoc continues to document the
slice — no doc change required (the slice contract is unchanged).

**(b) Transit GA continuation TaskNodes — `[goAroundTask(), circuitTask(),
groundArrivalTask()]` per R22**

Located in `pilot/.../PilotMission.kt`:
- `goAroundTask()` — defined at line 793; produces `CompoundTask(GoAround,
  [PrimitiveTask(GOING_AROUND, REPORTED)])`. VFR-only — the IFR sibling is
  `ifrGoAroundTask()` at line 798.
- `circuitTask()` — defined at line 746; produces `CompoundTask(Circuit,
  [FLY_DEPARTURE → FLY_DOWNWIND → REPORT_DOWNWIND → AWAIT_SEQUENCING →
  FLY_BASE → REPORT_BASE → FLY_FINAL → REPORT_FINAL →
  AWAIT_LANDING_CLEARANCE → LAND])`.
- `groundArrivalTask()` — defined at line 775; produces
  `CompoundTask(GroundArrival, [REPORT_RUNWAY_VACATED →
  AWAIT_VACATE_INSTRUCTION → TAXI_TO_STAND → SHUTDOWN])`.

The suffix produces a full recovery-landing-and-taxi continuation: the
aircraft goes around, re-enters a circuit, lands, and taxies to stand. No
new MissionStep enum values introduced (no `GA_AT_DEST` — closed by epic
R8 / round-4 Critical 2). All three helper names existed pre-fn-28 and
match the R22 contract verbatim.

**Transit GA Tick A intent** (R19 / round-4 Major 2):
- `targetSpeedMps = aircraft.type.kinematics.climbSpeedMps` (NOT 0).
- `phase = PilotPhase.Final` (retained — Tick B's planRoute special case
  builds the GA climb route from "Final + no airborne route + FLY_DEPARTURE").
- `route = PilotRoute.None` (invalidates kinematic route toward the no-longer-
  landable runway).
- `targetAltitudeM = aircraft.type.circuitPattern.altitudeAglM` (pattern
  altitude — mirrors all circuit-only reactive-GA Tick A intent shapes).

**`resetForGoAround` placement** (round-16 Major 2): called BEFORE the suffix
rewrite in `applyTransitArrivalReactiveGoAround`. Mirrors `handleGoAround`'s
sequencing (`mission.resetForGoAround(now).copy(root = newRoot, ...)`).
Every approach-side mutation is cleared: `hasClearance`, `activeConstraints`,
`routeOverride`, `altitudeRestrictionM`, `joinLeg`, `lastReportedLeg`,
`lastTransmittedStep`.

**`planRoute` Transit-cruise discriminator** (R14 / round-14 Major 1):
added at the top of `Pilot.kt::planRoute`. The pre-fix `if (step ==
FLY_DEPARTURE && mission.goal is Transit) return planTransitCruise(...)`
unconditionally dispatched to cruise for any Transit + FLY_DEPARTURE
state. After the suffix replacement, the active leaf becomes
`circuitTask()`'s first primitive (FLY_DEPARTURE), but inside a nested
`Circuit` compound — the cruise dispatch would send the aircraft to the
destination's published REP at cruise altitude (WRONG; the aircraft is
on a circuit recovery). The fix gates on `mission.root.activeCompound()?.name`:
cruise dispatches ONLY when the active inner compound is null (i.e. the
FLY_DEPARTURE is a direct primitive child of the Transit compound — the
original pre-GA cruise shape). Recovery FLY_DEPARTURE (active compound =
`Circuit` / `CircuitAfterGoAround` / `GoAround`) falls through to the
Visual-mode + Circuit-mode planner logic, which already handles the GA
climb path.

**Recognition+apply pipeline agreement** (round-12 Major 1):
`deriveCrosswindEvent` + `deriveTailwindEvent` widened from
`isReactiveGoAroundEligible(mission)` (circuit-only) to disjunctive
`isReactiveGoAroundEligible(mission) ||
isTransitArrivalReactiveGoAroundEligible(aircraft, mission)`. The apply
appliers carry the matching dispatch fork. Per the pin
`bug/build-errors/recognitionapply-pipelines-need-mission-2026-05-11`,
both sides must agree to avoid the canonical recognition-fires-but-apply-
silently-no-ops failure mode.

## Done summary

_(filled by `flowctl done` at task close)_

## Evidence

_(filled by `flowctl done` at task close)_
