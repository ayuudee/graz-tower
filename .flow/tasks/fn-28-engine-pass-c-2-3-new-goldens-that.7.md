---
satisfies: [R2-G3B, R2a, R2b, R2c, R2d, R2e, R3, R4, R5, R7, R13, R18, R19, R22]
---

## Description

G3b sim golden. C172 LOWG → LJMB; on arrival approach at LJMB, wind exceeds POH crosswind (scenario 1) or tailwind (scenario 2) limit; pilot recognizes via .6's `isTransitArrivalReactiveGoAroundEligible(aircraft, mission)`; apply rewrites mission via `replaceFromActivePrimitive(existingGATaskNodes)` (where `existingGATaskNodes` is the GA continuation looked up from existing GA flow per .6 / round-5 Critical 2 fix). **NO `GA_AT_DEST` enum value or placeholder appears anywhere in test/spec.** VFR; no SID. Three-layer pin. Archive flips for `D-PASS-g3b-react-cross-aerodrome-crosswind` + `-tailwind`.

**Size:** M
**Files:**
- `sim/src/jvmTest/.../G3bCrossAerodromeReactiveTest.kt` (new, exhaustive KDoc, 2 `@Test` methods)
- `sim/src/jvmTest/.../Fixtures.kt` — `LOWG_TO_LJMB_REACTIVE` variant; concrete LJMB weather including OAT + QNH; provenance entry
- `docs/deferments.md` — 2 archive flips
- `.plan` — abandoned G1 cross-aerodrome surface revived

## Approach

- Pattern: G2 cross-aerodrome transit + G3a-react reactive — fusion.
- FPL: `destinationAerodrome = LJMB`; no SID; VFR.
- LJMB runway 14 / 32 geometry.
- Three-layer pin per scenario:
  - L1 causal: LOWG departure → enroute → LJMB TMA entry (per .6) → LJMB weather observation → recognition (via Transit-arrival guard inside existing applier dispatch fork) → apply (suffix-replace with existing GA TaskNodes per .6).
  - L2 sticky-witness: Transit mission's task-list suffix replaced with the existing GA TaskNodes (whatever the .6 resolution chose); **assertion explicitly references the TaskNode types resolved by .6, NOT a `GA_AT_DEST` placeholder**.
  - L3 kinematic non-event: **within the test's bounded time window**, aircraft never lands at LJMB in GA scenario. **Window stop condition** (round-10 Minor 3): test runs UNTIL (a) the post-GA pilot decision tick has applied the suffix replacement via `replaceFromActivePrimitive` AND (b) the trace contains the GA-pattern transmission (named witness — e.g., the `goAroundTask()` primitive's emitted transmission, or pilot's mission step transition into the GA segment). Test stops at that named witness; downstream recovery landing is out-of-window. NO contradiction with R22's full continuation — in production the recovery eventually lands; in the test, the window is bounded by the named witness.
- Transit GA intent matches existing Tick A: `targetSpeedMps = climbSpeedMps`, `phase = Final`, `route = None`, target altitude = pattern altitude (R19).
- Inherited-gate-semantics audit comments.

## Investigation targets

- Task .6 outputs (Transit GA continuation TaskNodes, dispatch fork in existing appliers, intent values)
- `sim/.../G2CrossAerodromeVfrTest.kt`
- `sim/.../AftnRoutingSpec.kt`
- `sim/.../G3aPilotReactiveCrosswindTest.kt` + `Tailwind`
- LJMB world data
- Existing pilot-trained-GA flow (for the canonical GA continuation TaskNodes — looked up in .6, referenced here)

## Key context

- Depends on .6 (projection + Transit-arrival guard + existing-GA-TaskNode suffix continuation).
- **No `GA_AT_DEST`** anywhere. Test references the resolved-by-.6 TaskNode types.
- No SID.
- CAP 413 §1.10.3 cite (home/away agnostic).
- `.plan` revival note.

## Acceptance

- [ ] `G3bCrossAerodromeReactiveTest.kt` with exhaustive KDoc + 2 `@Test` methods (crosswind, tailwind)
- [ ] Three-layer pin per scenario
- [ ] FPL: `destinationAerodrome = LJMB`; no SID; KDoc explains
- [ ] LJMB runway 14 or 32 geometry chosen
- [ ] Pilot observes LJMB weather per .6 resolution
- [ ] Recognition fires through .6's widened `deriveCrosswindEvent` / `deriveTailwindEvent` disjunctive eligibility (round-16 Major 1 — recognition is in `derive*Event`, NOT inside appliers); apply dispatches through `applyCrosswindGoAround` / `applyTailwindGoAround` Transit fork using the same `isTransitArrivalReactiveGoAroundEligible(aircraft, mission)` guard
- [ ] Apply rewrites via `replaceFromActivePrimitive(existingGATaskNodes)` where `existingGATaskNodes` is the .6-resolved GA continuation (NOT `GA_AT_DEST`)
- [ ] **Test/spec contains NO `GA_AT_DEST` enum value or placeholder string** (round-5 Critical 2)
- [ ] Transit GA intent: `climbSpeedMps + Final + None + patternAltitude` (R19)
- [ ] No-refire verified at sim level
- [ ] `Fixtures.LOWG_TO_LJMB_REACTIVE` with concrete OAT + QNH; provenance entry
- [ ] `docs/deferments.md` archive flips for both axes
- [ ] `.plan` revival note
- [ ] Targeted: `./gradlew :sim:jvmTest --tests "*G3bCrossAerodromeReactiveTest*" --offline --no-daemon` GREEN
- [ ] Full verify GREEN; 12 sim goldens
- [ ] Inherited-gate-semantics audit comments

## Done summary

_(filled by `flowctl done` at task close)_

## Evidence

_(filled by `flowctl done` at task close)_
