# fn-28 — Engine pass C: 2-3 new goldens that close the most deferments

## Overview

The 9 existing sim goldens (LowgGoldenTest, G1TwoAircraftCircuitsTest, G1TwoAircraftMinimalSpec, G2CrossAerodromeVfrTest, G3aPilotTrainedGoAroundTest, G3aRunwayObstructionTest, G3aRunwayObstructionContinueApproachTest, G3aPilotReactiveCrosswindTest, G3aPilotReactiveTailwindTest) anchor specific scenarios. 98 deferments in `docs/deferments.md` enumerate cases NOT covered. This epic adds 2-3 new goldens chosen to **close the most deferments per golden** — direct leverage on the deferment register.

**Candidate set** (per planning-time survey of `docs/deferments.md`):

1. **G3a-react-multi-aircraft** — 2 aircraft on same runway, wind shifts to crosswind (or tailwind) limit, pilot-reactive GA fires for one OR both with serialization. **Closes**: `D-PASS-g3a-react-multi-aircraft-crosswind`, `D-PASS-g3a-react-multi-aircraft-tailwind`, partial closure on `D-PASS-g3a-react-wind-variability-dynamics`, partial closure on `D-AUDIT.7.II-FOLLOWUP` (mixed-mode parallel) if scenarios exercise that path. **Direct: 2 deferments, partial: 2+**. Highest single-golden leverage on the g3a-react cluster (15 deferments total).

2. **G3b-cross-aerodrome-react** — Single aircraft transits LOWG → LJMB; pilot-reactive GA fires at LJMB on crosswind exceedance. **Closes**: `D-PASS-g3b-react-cross-aerodrome-crosswind`, `D-PASS-g3b-react-cross-aerodrome-tailwind`, revives the abandoned G1 cross-aerodrome surface (per `.plan` Golden tests section). **Direct: 2 deferments + 1 .plan unblock**. Highest cross-aerodrome leverage.

3. **G3a-react-density-altitude** — Third pilot-reactive POH axis after crosswind (fn-14) and tailwind (fn-15). DA exceedance triggers reactive GA. **Closes**: `D-PASS-g3a-react-other-poh-triggers`. Pattern-mirror; cheapest to author. **Direct: 1 deferment, partial: 1**. Lowest leverage of the three; easiest to land.

**Alternative**: instead of #3, one of the D-AUDIT.9 emergencies (abort takeoff, fuel exhaustion, icing deviation) — closes 1 deferment but exercises engine code paths NO existing golden touches; higher "find latent bugs" probability.

**Final selection deferred to interview** — the user picks 2 or 3 from the above + alternatives.

## Boundaries / non-goals

- **Out: building more than 3 goldens.** Cap is 3 to keep the epic bounded.
- **Out: revisiting closed deferments.** Only OPEN entries in `docs/deferments.md` count toward the closure tally.
- **Out: invariant-pumping each new golden.** fn-29 (epic D — invariant pumping over existing goldens) extends to the new goldens once they land.
- **Out: changing engine semantics to make a golden pass.** If a golden surfaces a real engine bug, file as a follow-up epic; don't reshape the engine to fit the test.

## Strategy Alignment

- **Runtime simulator** — direct deferment-register-driven coverage extension. The 9 → ~12 goldens stay aligned with the strategy's "regulation-grounded ATC simulator" claim by closing named gaps rather than adding speculative scenarios.

## Decision context

**Why golden-per-task split**: 1 task per golden keeps each implementation bounded (~1-2 weeks worker time), reviewable in isolation. fn-15 took 2 tasks (foundation + sim integration); each candidate golden here follows the same shape — task .M.1 = foundation if needed, task .M.2 = sim integration. For density-altitude (#3), the foundation may already exist (fn-14 + fn-15 established the reactive-GA machinery); single task possible.

**Why interview before plan-review**: the candidate set has real choice (multi-aircraft vs cross-aerodrome vs DA vs emergency). User judgment picks the 2-3 best fits given their priorities (deferment closure count vs engine-coverage breadth vs implementation effort).

## Acceptance

- **R1:** **Interview pass** (REQUIRED before task creation): user selects 2-3 goldens from the candidate set. Selection recorded in epic spec's `## Decision context` section as a per-plan-review-round annotation.
- **R2:** Per selected golden, one task that:
  - **R2a:** Writes the golden test file at `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/<GoldenName>Test.kt`
  - **R2b:** Authors the scenario fixture (extends `Fixtures.kt` if needed; new helper otherwise)
  - **R2c:** Implements the three-layer pin pattern (causal partial-order, sticky-witness regression, kinematic non-event) per fn-14.2 / fn-15.2 precedent
  - **R2d:** Uses world-only test triggers per `feedback_world_only_test_triggers.md` discipline
  - **R2e:** Closes the named deferment(s) per `docs/deferments-CONVENTION.md` §8 archive flip
- **R3:** New golden is added to the `:sim:jvmTest` golden lineup (currently 9; becomes 11-12 after this epic).
- **R4:** Per-golden full verify: `./gradlew :sim:jvmTest --tests "*<GoldenName>Test*" --offline --no-daemon` passes. Full verify (R7) confirms no regression elsewhere.
- **R5:** Each new golden carries KDoc citing the closed deferments + the regulation source (POH / FCOM / ICAO / etc.) per `feedback_citation_discipline.md`.
- **R6:** No production-code changes unless absolutely required (real engine bug surfaced by the golden). Real bugs filed as follow-up epics; don't reshape engine to fit test.
- **R7:** Full verify GREEN; nine sim goldens GREEN (the existing 9 don't regress); detekt unchanged.
- **R8:** Diff scope: per golden, ~2 new files (test + fixture extension) + 1 deferments.md archive flip. Total across 2-3 goldens: ≤8 files, ≤800 LOC.

## Early proof point

Each new golden landing → flip its named deferment to Archive. If the golden lands but the deferment can't be archived (because the golden doesn't actually cover the deferment's contract), the candidate selection was wrong — re-evaluate during interview, don't paper.

## Quick commands

```bash
# Targeted golden run per task
./gradlew :sim:jvmTest --tests "*<GoldenName>Test*" --offline --no-daemon

# Full verify
./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests :migration:allTests detekt --offline --no-daemon
```

## Requirement coverage

| Req | Description | Task(s) |
|-----|-------------|---------|
| R1  | Interview-driven golden selection | (pre-task — user input) |
| R2  | Per-golden task with R2a-R2e structure | fn-28-engine-pass-c-2-3-new-goldens-that.N (one per selected golden) |
| R3  | Golden added to lineup | (per task) |
| R4  | Targeted + full verify per task | (per task) |
| R5  | KDoc citation discipline | (per task) |
| R6  | No production code changes (or file follow-up if real bug) | (per task) |
| R7  | Full verify green; existing 9 don't regress | (per task) |
| R8  | Diff scope ≤8 files / ≤800 LOC total | (across all tasks) |

## Review considerations

- **FP / type safety**: goldens use the same totality discipline (Arrow `Either`, no `\!\!`). **Reviewer focus**: confirm test code doesn't introduce shortcuts.
- **Test architecture**: three-layer pin pattern (per fn-14.2 / fn-15.2 / fn-19 precedents — causal partial-order + sticky-witness + kinematic non-event). **Reviewer focus**: each golden's pin layers match the pattern.
- **Impact**: scoped to :sim/jvmTest + docs/deferments.md.
- **Operational ATC correctness / applicability**: each golden anchors a regulation-grounded scenario. **Reviewer focus**: KDoc cites the right doctrinal source.

## References

- fn-14 spec — G3a-react crosswind (pattern for fn-28 candidate #1)
- fn-15 spec — G3a-react tailwind (sibling pattern for #1 and #3)
- fn-5 spec — G2 cross-aerodrome (pattern for #2)
- `.plan` Golden tests section — abandoned G1 cross-aerodrome surface (#2 revives)
- `docs/deferments.md` — 98 active entries; the candidate set's closure targets
- `.flow/memory/knowledge/best-practices/test-pin-discipline-2026-05-15.md` — three-layer pin pattern + mint-id discipline
- `.flow/memory/knowledge/best-practices/inherited-gate-semantics-2026-05-15.md` — copy-pasted gate semantics must be re-validated per axis
- `.flow/memory/knowledge/conventions/rich-world-domain-2026-05-15.md` — entity-field principle (Aerodrome.weather feeds the pilot-reactive recognition)
