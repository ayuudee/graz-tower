# G1 two-aircraft VFR circuits at LOWG

## Overview

Land a `G1TwoAircraftCircuitsTest` golden test mirroring `LowgGoldenTest`'s
shape (single `@Test` method, fixture-driven, journey + state-trace
assertions) but with **two** AI aircraft running in parallel: each starts
at a different LOWG stand, taxis out, takes off, flies ~2 circuits
(touch-and-go + full-stop on last), then taxis back to a stand.
Exercises ATC sequencing on a single runway with two aircraft in the
pattern simultaneously: departure/arrival deconfliction, extend-downwind
spacing, wake-rule evaluation, and conflict resolution.

This is the next golden up from G0 (`LowgGoldenTest`, single-aircraft
circuit training). G2 (`G2CrossAerodromeVfrTest`) is the cross-aerodrome
sibling; G1 sits alongside it as the **multi-aircraft same-aerodrome**
sibling. No prior multi-aircraft sim-level test exists in the repo —
G1 is the first.

## Quick commands

```bash
# Build + run the load-bearing tests
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :sim:jvmTest \
    --tests "xyz.easiersaid.twr.sim.G1TwoAircraftCircuitsTest" \
    --tests "xyz.easiersaid.twr.sim.LowgGoldenTest" \
    --tests "xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest" \
    --console=plain

# Full check (must stay green)
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest :protocol:jvmTest --console=plain

# Detekt baseline preserved
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew detekt --console=plain
```

## Boundaries / non-goals

- **Out: `ARR-NUMBER-IN-SEQUENCE` rule plumbing.** The
  `NumberInSequence` instruction type exists (`Instruction.kt:979`) but
  no `AtcRule` issues it. Per practice-scout's "pin causal observable,
  not phraseology string" principle, G1 doesn't require this rule —
  the doctrinally-stable observable is `ARR-EXTEND` firing for B
  while A is on FINAL, not the literal `NUMBER 2 IN SEQUENCE`
  transmission. Filed as `D-PASS-arr-number-in-sequence`.
- **Out: `ARR-ORBIT` rule plumbing.** `OrbitHold` is in the
  `InterventionSelection` ladder but no rule fires it. At 2-circuit
  cadence with deliberate offset (see Approach), `PathExtension` /
  `ARR-EXTEND` should suffice. If a tick selects `OrbitHold` and finds
  no rule, the firewall test catches it loud. Filed as
  `D-PASS-arr-orbit`.
- **Out: per-callsign diagnostics tooling** (writer-style /
  suspend-emitter trace channels per `project_diagnostics_direction`
  memory entry). G1 ships with plain `assertX` pins; the diagnostic
  tooling is the **next** pass triggered by G1 working. Per
  `feedback_pass_scope`, don't bundle.
- **Out: 3+ aircraft** scenarios. Two is the minimum interesting case
  for sequencing; three+ is its own scope.
- **Out: deliberate go-around** scenario. Two-aircraft full-stop +
  touch-and-go pattern stresses spacing without forcing an actual
  abort; go-around is its own golden test if needed.
- **Out: visual separation handover** (CAP 413's "report traffic in
  sight, maintain visual separation"). Pilot-applied visual separation
  needs new pilot-side decision logic and a typed event; out of scope.
  G1 stays on controller-applied separation only.

## Decision context

**Multi-agent determinism strategy** — practice-scout flagged this as
the canonical determinism bug for multi-aircraft sims. Three options:

1. **Per-aircraft sub-PRNG via `SimRandom.derive(label)`** *(this epic
   — selected)*. Each pilot gets `seed.derive("OE-ABC")` /
   `seed.derive("OE-DEF")`. Refactors that swap aircraft tick order
   don't change either pilot's PRNG draws. Per-aircraft sub-PRNG is
   non-negotiable per practice-scout finding #3.
2. Single shared PRNG with strict total ordering by callsign (rejected:
   shifting decision order shifts draws → brittle pin shape).
3. Externalize all decisions to deterministic functions, no PRNG
   (rejected: too aggressive a refactor; some pilot decisions
   legitimately want randomness).

**Wake-rule observable shape** — practice-scout finding #4: the pin
should be "rule was evaluated, returned no constraint" (vs "no extra
spacing observed"). Drives the `SeparationEngine.assessSeparation`
return type to be a structured `SeparationAssessment(rule: WakeRule,
minimum: Distance?, ...)` ADT. Inside `WakeRule`: a sealed hierarchy
naming the canonical Doc 4444 §5.8 / RECAT-EU cells, including
`RecatFFNoMin` for CAT F → CAT F (the case G1 exercises).

**Pin shape: causal partial-orders, not tick numbers** — practice-scout
finding #5 + gap-analyst #7. The G1 test pins:

- `taxiClearance(A) ≺ taxiClearance(B)` (start-of-day sequencing)
- `cleared-takeoff(A) ≺ lineUpAndWait(B)` (single-runway gate)
- `extendDownwind(B) ≺ touchdown(A) ≺ turnBase(B)` (the conflict
  resolution pin — the load-bearing G1 invariant)
- `cleared-land(A_final_circuit) ≺ cleared-land(B_final_circuit)`
  (terminal sequencing)
- Both aircraft taxi-to-stand at end (closure)

Each pin is a partial-order over events from the trace, not a
tick-number. Robust to controller-policy refactors that shift exact
timing.

**Conflict authoring** — gap-analyst #9. Author B's takeoff offset to
deliberately put B on downwind when A is on base / final, forcing
`ARR-EXTEND` to fire for B. The exact offset comes from empirical
tuning once the test runs once; the **causal pin** (`extendDownwind(B)
WHILE A.phase == FINAL`) is what the test asserts, not the offset.

**Stand pair** — gap-analyst #11. Use two GA-cluster stand IDs from
`cad/airports/rendered/lowg/world-candidate.json`. LOWG's stand 38
cluster (per docs-scout LOWG specifics) is the canonical GA parking;
pick two adjacent stands so taxi sequencing is visible. Implementer
chooses; document the choice in `Fixtures.LOWG_TWO_AIRCRAFT` KDoc.

## Approach

The work splits cleanly into **foundation** (typed value wins + fixture
infra; gap-analyst's blocking gaps #1, #4, #5, #6) and **the test
itself** (mirror of LowgGoldenTest with two-aircraft narrative). Per
`feedback_pass_scope`, fold the typed wins into the foundation task
rather than spawning follow-ups.

### Foundation surface

1. **`Fixtures.LOWG_TWO_AIRCRAFT`** — new fixture object alongside
   existing `LOWG` and `LOWG_LJMB_VFR`. Carries `startPoints:
   Map<AircraftId, PointId>` (option a — practice-scout's canonical
   shape, mirrors `flightPlans: Map<AircraftId, FiledPlan>` already
   established). Two distinct LOWG stand IDs.

2. **`SimRandom.derive(label: String): SimRandom`** — deterministic
   sub-PRNG factory. Uses a salt-based split (e.g. `SimRandom(seed XOR
   hash(label))` or similar canonical pattern). If `SimRandom` already
   exposes `derive`, no change. Threaded through pilot decision-tick
   handler so each `AircraftId` gets `state.rng.derive(id.value)`.

3. **`SeparationAssessment` ADT** — extend (or introduce) the structured
   return shape on `SeparationEngine.assessSeparation`. Sketch:
   ```
   sealed interface WakeRule {
       data object RecatFFNoMin : WakeRule
       data class RecatLeaderFollower(val leader: WakeCategory, val follower: WakeCategory, val minimum: Meters) : WakeRule
       // … other cells from Doc 4444 §5.8
   }
   data class SeparationAssessment(
       val rule: WakeRule,
       val minimum: Meters?,
       // … existing fields
   )
   ```
   Audit current return type; if it's `Boolean` / `Distance?`, this is
   the refactor. If a half-formed ADT exists, this is an extension.

4. **Tick ordering audit** — confirm `SimState.advance` (or whatever
   processes the per-cycle pilot ticks) total-orders pilot decisions by
   `AircraftId.value`. `Fixture.load()` already sorts (per repo-scout);
   verify the cycle dispatcher does too. If not, fix with a sortedBy
   pass.

### Test itself

5. **`G1TwoAircraftCircuitsTest.kt`** at
   `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt`.
   Single `@Test` method mirroring `LowgGoldenTest` (491 lines for one
   aircraft → expect ~600-700 lines for two). Structure:

   - Load fixture: `Fixtures.LOWG_TWO_AIRCRAFT.load()`.
   - Build aircraft A + B with `createMission(HighLevelGoal.CircuitTraining(
     circuits = 2, fullStopOnLast = true), ...)` × 2.
   - Author B's mission start time / fligthplan offset to deliberately
     force the conflict (gap-analyst #9). Empirical — pin causal order,
     not exact offset.
   - `SimState.initial(seed = 42L, aircraft = listOf(aircraftA,
     aircraftB), ...)`.
   - `runUntilWithStateTrace(initial, initialEvents, until = 90 * 60 *
     1000L)` (G2's wall ceiling — generous, will measure actual).
   - **Diagnostic preamble**: per-aircraft journey + per-aircraft trace
     queries (`responsibilityTransitions(aircraftA.id)`,
     `missionStepTransitions(aircraftB.id)`, etc — all already
     aircraft-scoped per repo-scout finding #8).
   - **Outcome pins** (per aircraft): both reach Parked; both at stand
     points; both missions complete.
   - **Causal pins** (decision context): the 5 partial-orders listed
     above.
   - **Wake-rule pin**: `firstSeparationAssessment(B, leader = A).rule
     == WakeRule.RecatFFNoMin` — pins the rule was *evaluated*, not
     just that no extra spacing was observed.
   - **Time band**: capture observed wall once, pin a band ±15% per
     practice-scout finding #5.

6. **Cross-references** — when G1 lands, update:
   - `AGENTS.md` `## Golden tests` section (add G1 bullet alongside
     G0/G2).
   - `LowgGoldenTest.kt` class docstring (`@see` G1).
   - `G2CrossAerodromeVfrTest.kt` class docstring (currently mentions
     G0 only — add G1 as the multi-aircraft sibling).
   - `Fixtures.kt` KDoc (add `LOWG_TWO_AIRCRAFT` paragraph).

### Pattern reuse

- `LowgGoldenTest` (`sim/src/jvmTest/.../LowgGoldenTest.kt`, 491 lines)
  — line-for-line mirror of structure (fixture load → build state → drive
  → assertions).
- `G2CrossAerodromeVfrTest` — mirror for multi-controller staging via
  `controllerAt(aerodromeId, role)` and the SimTrace harness usage.
- `SimTrace` queries (`positionPointTransitions`,
  `missionStepTransitions`, `responsibilityTransitions`,
  `commitmentStageTransitions`) all take `AircraftId` (per repo-scout
  finding #8) — work for multi-aircraft out of the box.
- `Fixture.flightPlans: Map<AircraftId, FiledPlan>` already supports
  multiple entries; `Fixture.load()` sorts by `AircraftId.value` for
  determinism.

## Risks / dependencies

- **No hard deps.** `fn-7` (CTR-radius retune) is unrelated — circuit
  traffic doesn't reach the CTR boundary. fn-6 / fn-7 / fn-5 are all
  done; G1 is independent.
- **Risk: discovery cost.** First multi-aircraft test in the repo —
  bugs in the harness (PRNG, tick ordering, sequencing rule wiring) may
  surface only under G1's load. Per `feedback_pass_scope`, fold typed
  wins into G1's closing pass; file larger surprises as deferments. If
  a structural bug emerges (e.g. controller can't reason about two
  aircraft in the same `responsibilities` map), STOP and re-evaluate.
- **Risk: brittle conflict authoring.** B's takeoff-offset is empirical.
  If circuit timing shifts due to a kinematic refactor, the conflict
  may stop occurring; the test becomes a "nothing interesting happened"
  green. Mitigation: pin the test's *forced-conflict invariant*:
  `extendDownwind(B)` MUST be observed during the run. If absent, the
  test fails with "B never had to extend — conflict authoring shifted."
- **Risk: pre-existing flake** `:migration:jvmTest >
  LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport`
  — out of G1 scope; ignore.
- **Risk: wake-rule ADT refactor blast radius.** If `SeparationAssessment`
  is widely consumed, the refactor touches more sites than expected.
  If so, scope-shift to (a) keep both old + new return shapes during
  G1, file the cleanup as a follow-up, or (b) split G1 task .1 into
  two: ADT extension as its own task, then test.

## Acceptance

- **R1:** `Fixtures.LOWG_TWO_AIRCRAFT` exists alongside `Fixtures.LOWG`
  and `Fixtures.LOWG_LJMB_VFR`. Carries `startPoints: Map<AircraftId,
  PointId>` (two distinct LOWG stands) plus `flightPlans` for both
  aircraft. KDoc cites the chosen stand IDs + AIP source.
- **R2:** `SimRandom.derive(label: String): SimRandom` exists (or
  pre-exists) and is used in pilot decision-tick handlers so each
  aircraft has its own deterministic sub-PRNG. Pin: swapping aircraft
  ID order in fixture seeding does not change either pilot's tick
  outcomes (regression test or evidence in commit message).
- **R3:** `SeparationEngine.assessSeparation` returns a structured
  `SeparationAssessment` with a `rule: WakeRule` field. `WakeRule` is
  a sealed hierarchy with at minimum a `RecatFFNoMin` case.
- **R4:** `SimState.advance` (or the cycle dispatcher) total-orders
  pilot decisions by `AircraftId.value`. Audited and confirmed; if
  not, fixed.
- **R5:** New `G1TwoAircraftCircuitsTest` at
  `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt`
  exists. Mirror of `LowgGoldenTest` shape: single `@Test` method,
  fixture-driven, single behavioural narrative.
- **R6:** Test pins the 5 causal partial-orders from §Decision context:
  taxi-clearance ordering, line-up-vs-takeoff gate, extend-downwind
  conflict resolution, terminal landing sequencing, both-reach-stand
  closure.
- **R7:** Test pins `firstSeparationAssessment(B, leader = A).rule ==
  WakeRule.RecatFFNoMin` (wake-rule-was-evaluated shape per
  practice-scout #4).
- **R8:** Test pins **`extendDownwind(B)` is observed during the run**
  — if absent, the test fails with a "conflict authoring shifted"
  message. Captures the test's intent: G1 must witness the conflict-
  resolution code path at least once.
- **R9:** No regression: `LowgGoldenTest`, `G2CrossAerodromeVfrTest`,
  all sim/jvm tests, all pilot/controller/core/protocol suites stay
  green. `./gradlew detekt` baseline unchanged.
- **R10:** Cross-reference docs updated: `AGENTS.md` lists G1 in
  `## Golden tests`; `LowgGoldenTest` + `G2CrossAerodromeVfrTest`
  docstrings cross-ref G1; `Fixtures.kt` KDoc names
  `LOWG_TWO_AIRCRAFT`.

## Early proof point

Task `fn-8.1` lands the foundation (fixture + PRNG-derive + separation
ADT + tick-ordering audit). If `SeparationAssessment` ADT refactor has
hidden blast radius (touches > ~10 sites), STOP and split. If
`SimRandom.derive` reveals a deeper PRNG-determinism issue (e.g. the
sim has multiple RNGs that aren't aircraft-scoped), STOP and re-plan.
fn-8.2 builds on a known-good foundation; it should be the test
authorship pass with no architectural surprises.

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | `Fixtures.LOWG_TWO_AIRCRAFT` with `startPoints` map | fn-8.1 | — |
| R2  | `SimRandom.derive(label)` per-aircraft sub-PRNG | fn-8.1 | — |
| R3  | `SeparationAssessment` + `WakeRule` ADT | fn-8.1 | — |
| R4  | Tick ordering audit + fix if needed | fn-8.1 | — |
| R5  | `G1TwoAircraftCircuitsTest` exists, mirrors G0 shape | fn-8.2 | — |
| R6  | 5 causal partial-order pins | fn-8.2 | — |
| R7  | Wake-rule pin: `RecatFFNoMin` | fn-8.2 | — |
| R8  | `extendDownwind(B)` observation pin (forced-conflict invariant) | fn-8.2 | — |
| R9  | No regression in existing test suites + detekt | fn-8.1, fn-8.2 | — |
| R10 | Cross-reference doc updates | fn-8.2 | — |

## Deferments register

Forward-looking entries (not acceptance criteria):

- **`D-PASS-arr-number-in-sequence`** — wire `ARR-NUMBER-IN-SEQUENCE`
  rule to fire `NumberInSequence` instruction when arrival sequence
  engine assigns a stableNumber > 1. ICAO Doc 4444 §7.10 / Ch.12
  doctrine. G1 doesn't require it (causal-observable pin); future pass
  adds the doctrine-correct phraseology emission.
- **`D-PASS-arr-orbit`** — wire `ARR-ORBIT` rule to fire `Orbit`
  instruction when intervention selection picks `OrbitHold`. CAP 413 /
  IVAO doctrine. Future pass.
- **`D-PASS-g1-diagnostics`** — per-callsign writer-style diagnostic
  channels per `project_diagnostics_direction`. G1 ships with plain
  asserts; this golden test is the trigger for the diagnostic pass per
  memory.
- **`D-PASS-visual-separation-handover`** — pilot-applied visual
  separation per CAP 413 ("traffic in sight, maintain visual
  separation"). Needs new pilot-side decision logic + typed event.
  Out of G1 scope; future epic if/when needed.
- **`D-PASS-three-or-more-aircraft`** — 3+ aircraft in pattern stresses
  scaling of sequencing rules; G1 is two-aircraft minimum case.
