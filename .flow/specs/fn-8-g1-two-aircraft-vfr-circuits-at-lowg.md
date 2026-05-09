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
- **Out: RECAT-EU wake-category model.** The current code uses ICAO
  Doc 4444 wake categories (`WakeCategory.{J, H, M, L}`); RECAT-EU
  (CAT A–F) is a separate model not present in the repo. G1 names
  rules in ICAO terms (per pass-1 plan-review finding #2). RECAT-EU
  adoption is a separate scoped epic; filed as `D-PASS-recat-eu-wake`.
- **Out: per-plan filing-time offset on `Fixture`.** The current
  `Fixture.load()` emits one `FlightPlanFiled` per `flightPlans` entry
  at `SimTime.ZERO`; there is no per-plan filing time. G1 forces the
  conflict via a **mission-start offset** (B's first
  `PilotDecisionTick` is delayed; both plans are filed at zero). Per-
  plan filing time is a separate scoped change; filed as
  `D-PASS-fixture-per-plan-filing-time`.

## Decision context

**Multi-agent determinism strategy** — practice-scout flagged this as
the canonical determinism bug for multi-aircraft sims. The
implementation must guarantee: **swapping the order in which aircraft
are dispatched within a tick does not change either aircraft's
random draws** (per pass-1 plan-review findings #1 + #10). Three
options:

1. **Per-aircraft persisted RNG stream in `SimState`** *(this epic —
   selected)*. Add `rngByAircraft: Map<AircraftId, SimRandom>` to
   `SimState`. Each aircraft's stream is seeded once at state
   construction time (in `SimState.initial(...)` for fixture
   aircraft and `Step.handleSpawn(...)` for mid-sim spawns; per
   pass-3 plan-review finding #4 — `SimState.initial` and
   `handleSpawn`, not "fixture load") via the existing
   `SimRandom.split(aircraftId.value)`
   (already present at `SimRandom.kt:40`; we do **not** add a new
   `derive` alias). Per-aircraft draws thread that aircraft's RNG
   forward via the existing `(value, newRandom)` contract,
   updating `rngByAircraft[aircraftId]` after each draw. Order
   independence holds because each aircraft's stream advances
   independently of the others.
2. Single shared PRNG with strict total ordering by callsign (rejected:
   shifting decision order shifts draws → brittle pin shape, and
   `SimRandom.split(tag)` returning a fresh child from the same parent
   on every call would re-seed the child each tick — repeated draws).
3. Externalize all decisions to deterministic functions, no PRNG
   (rejected: too aggressive a refactor; some pilot decisions
   legitimately want randomness).

**Critical contract for option 1 (pass-1 plan-review finding #1 +
pass-4 plan-review finding #1):**
`SimRandom.split(tag)` returns a child whose state is derived from the
**current parent state** XOR the tag hash. Calling
`state.rng.split(id.value)` on every tick — without persisting the
child — would re-seed the child to the same value each tick (parent
hasn't advanced for that aircraft) and produce repeated draws. Per-
aircraft RNG must therefore be **persisted in `SimState` and advanced
on each draw**.

**Seeding sites (single canonical policy):**
- Initial aircraft: `SimState.initial(seed, aircraft, ...)` seeds
  `rngByAircraft` for every aircraft in the constructor list.
- Mid-sim spawns: `Step.handleSpawn(...)` seeds the new aircraft's
  entry from the current `state.rng` (or canonical parent stream
  documented at audit time).

No fixture-load-time seeding (fixture load doesn't have the sim
seed). No lazy first-tick seeding (would diverge from
`SimState.initial`'s contract that all `state.aircraft` keys have
matching `rngByAircraft` entries).

The shared `SimState.rng` remains for non-aircraft-scoped randomness.

**Wake-rule observable shape** — practice-scout finding #4: the pin
should be "rule was evaluated, returned no constraint" (vs "no extra
spacing observed"). Drives the `SeparationEngine.assessSeparation`
return type to carry a structured `WakeRule` field naming the
canonical Doc 4444 §5.8 cell that fired. **Naming uses the existing
ICAO `WakeCategory.{J, H, M, L}` model** (per pass-1 plan-review
finding #2 — RECAT-EU CAT A–F is not in the repo). Two C172s are both
`WakeCategory.L` → no additional wake minimum applies, so the rule
case is `WakeRule.IcaoNoAdditionalWakeMinimum` (or similar
naming). The earlier-draft `RecatFFNoMin` naming is replaced.

**Units stay in nautical miles** (per pass-1 plan-review finding #3).
The current separation model (`SeparationEngine.assessSeparation`,
`WakeSeparationMinima`) uses `Double` NM throughout
(`requiredSeparationNm`, `wake.distanceNm`, `RADAR_MINIMUM_NM`).
`WakeRule` does **not** introduce `Meters` for the wake minimum;
either reuse existing NM fields or add `wakeMinimumNm: Double?` if a
new field is genuinely needed. No unit-conversion churn.

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

**Conflict-resolution pin: state-window observable**, not mission-step
inspection (per pass-1 plan-review findings #5 + #6). The earlier
draft proposed "find `ExtendDownwind(B)` record, check A's mission
step at that moment" — but transmission records carry transmission
start times while the state trace advances on events; there's no
direct cursor link. And mission-step strings may not map cleanly to
"finalish". Replacement shape:

- Locate `ExtendDownwind(B)` record (transmission record).
- Use a SimTrace query `stateAtOrBefore(time)` (extend
  `SimTraceQueries.kt` if absent — minimal helper) to find the state
  immediately before that transmission.
- Assert A's observable is in a *finalish* family using stable
  observables: A's `PilotPhase` (e.g., `Final`, `Landing`) **or** A's
  `positionPoint` is on the runway final approach segment **or** A is
  ahead of B in the controller's arrival sequence. Pin against
  whichever of these surfaces is most stable in the existing model
  (implementer audits at task time and picks one; documents the
  choice).

**Conflict authoring** — gap-analyst #9. Author B's mission-start
offset to deliberately put B on downwind when A is on base / final,
forcing `ARR-EXTEND` to fire for B. The exact offset comes from
empirical tuning once the test runs once; the **causal pin**
(`extendDownwind(B) WHILE A.observable in finalish-family`) is what
the test asserts, not the offset. The offset is a delayed
`PilotDecisionTick` for B in `initialEvents`, **not** a per-plan
filing time (per pass-1 plan-review finding #4 — `Fixture.load()`
files all plans at `SimTime.ZERO` today). Both plans are filed at
zero; B's mission tick is delayed.

**Stand pair** — gap-analyst #11. Use two GA-cluster stand points
authored in `cad/airports/rendered/lowg/world-candidate.json`. The
fixture KDoc cites the **world-candidate authored GA stand points**
(per pass-1 plan-review finding #11 — without an AIP transcription
the wiki would otherwise carry a vague AIP claim). Implementer picks
two adjacent stand points and documents the choice; if a specific
LOWG AIP citation can be added at task time without speculation,
include it, otherwise stay on the world-candidate authoring
provenance.

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
   established). Two distinct LOWG stand points (world-candidate
   authored).

2. **Per-aircraft RNG state on `SimState`** — add
   `val rngByAircraft: Map<AircraftId, SimRandom>` to `SimState`
   (`sim/.../SimState.kt:38` is the existing `rng` field). Initialize
   in **two places** (per pass-2 plan-review finding #1):
   - `SimState.initial(seed, aircraft, ...)` seeds entries for every
     aircraft passed in.
   - `Step.handleSpawn(...)` (`Step.kt:786`) adds an entry for the
     newly-spawned aircraft, deriving from the current `state.rng`
     (or whichever parent stream is the policy-canonical source —
     implementer picks at audit time and documents).

   Add an invariant that every key in `state.aircraft` has a matching
   key in `state.rngByAircraft` — assert at `aircraftRng(id)` access
   time so a missing entry surfaces with a clear error rather than
   the default `getValue` NoSuchElement.

   Update on each draw via the existing `(value, newRandom)` thread-
   forward contract. The shared `SimState.rng` is preserved for
   non-aircraft-scoped randomness (e.g., weather, ATIS letter
   rotation).
   - **Helper**: a small extension or method on `SimState` like
     `aircraftRng(id: AircraftId): SimRandom` and a
     `withAircraftRng(id, newRng): SimState` updater. The pilot-tick
     dispatcher in `Step.kt` reads `state.aircraftRng(id)`, threads
     forward, and updates state via `withAircraftRng`.
   - **Determinism contract (pass-1 plan-review finding #10):** the
     property is "swapping the order in which aircraft are dispatched
     within a tick does not change either aircraft's draws" — same
     aircraft IDs, different scheduling order. **Not** "changing an
     aircraft ID leaves draws unchanged" (that contradicts the
     per-aircraft-keyed split).
   - **Note on `SimRandom.split`**: `SimRandom.kt:40` already exists.
     We do **not** add a new `derive` alias.

3. **`SeparationAssessment` ADT extension** — extend the structured
   return shape. **`SeparationAssessment` lives in
   `controller/observe/BeliefState.kt:188-...`**, not
   `SeparationEngine.kt` (per pass-2 plan-review finding #4). The
   field-add happens there. The wake-rule extraction logic stays in
   `SeparationEngine.kt` / `WakeSeparation.kt`.

   Keep all existing fields on `SeparationAssessment` untouched
   (current shape: `aircraft: AircraftId`, `other: AircraftId`,
   `requiredSeparationNm`, `currentSeparationNm`, etc.). Add
   `wakeRule: WakeRule`:

   ```kotlin
   sealed interface WakeRule {
       /** ICAO Doc 4444 §5.8 fallback row: pair has no entry in
        *  ICAO_WAKE_TABLE, so no wake supplement applies (radar
        *  minimum only). E.g. L→L. */
       data class IcaoNoAdditionalWakeMinimum(
           val leader: WakeCategory,
           val follower: WakeCategory,
       ) : WakeRule
       /** ICAO_WAKE_TABLE row hit: explicit leader/follower with
        *  wake supplement in NM. E.g. J→J (6.0 NM), H→H (4.0 NM),
        *  J→L (8.0 NM). */
       data class IcaoLeaderFollower(
           val leader: WakeCategory,
           val follower: WakeCategory,
           val wakeMinimumNm: Double,
       ) : WakeRule
       /** Wake category absent / unknown for one or both aircraft. */
       data object UnknownCategory : WakeRule
   }
   ```

   **Classifier (per pass-2 plan-review finding #3 — important):**
   the `ICAO_WAKE_TABLE` (`WakeSeparation.kt:28`) has rows for J→J,
   J→H, J→M, J→L, H→H, H→M, H→L, M→L. **It does NOT have an L→L row,
   so L→L hits the fallback path.** "Same category = no additional
   minimum" is therefore false for J→J / H→H — those are explicit
   table rows. The wake-rule extraction logic is:
   1. Either category null/unknown → `WakeRule.UnknownCategory`.
   2. `(leader, follower)` matches a row in `ICAO_WAKE_TABLE` →
      `WakeRule.IcaoLeaderFollower(leader, follower, distanceNm)`.
      (J→J, H→H both go here with their explicit minima.)
   3. Otherwise (fallback, no row matched, e.g. L→L, M→M) →
      `WakeRule.IcaoNoAdditionalWakeMinimum(leader = leader,
      follower = follower)`. The case carries BOTH categories so the
      diagnostic is useful for any fallback pair (not just same-
      category cases like L→L; per pass-3 plan-review finding #1
      — pairs like L→M, L→H also hit this fallback because no
      leader-L rows exist in the table at all).

   Naming uses the ICAO J/H/M/L model. No `Meters`; NM throughout
   (per pass-1 finding #3).

   **Audit blast radius BEFORE writing the change**: grep
   `assessSeparation\|SeparationAssessment` across the codebase. If
   > 10 call sites need updating, STOP and split this work into its
   own task (per Risks register).

4. **Event-ordering audit for simultaneous `PilotDecisionTick`s**
   (re-framed per pass-2 plan-review finding #2). `Step.kt` processes
   one `SimEvent` at a time; ordering for simultaneous pilot ticks is
   owned by `EventQueue` / the `EVENT_ORDER` priority constants and
   the per-event `seq` assignment, **not** a "dispatcher loop"
   (there's no `SimState.advance` in this codebase). Audit:
   - Find the event-comparator / `EVENT_ORDER` / `seq`-assignment
     site (likely in `EventQueue.kt` or similar).
   - Confirm that two `PilotDecisionTick` events at the same `time`
     for different aircraft sort deterministically (e.g. by
     `aircraftId.value` ascending, or by insertion seq, or by some
     other total order).
   - If the order is non-deterministic, fix at the comparator site.

   With per-aircraft RNG (R2), within-tick ordering matters less for
   *randomness* (each aircraft's stream is independent). But
   determinism of *non-RNG* state mutations (event-emission order,
   side-effect ordering) still depends on a stable total order over
   simultaneous events.

### Test itself

5. **`G1TwoAircraftCircuitsTest.kt`** at
   `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt`.
   Single `@Test` method mirroring `LowgGoldenTest` (491 lines for one
   aircraft → expect ~600-700 lines for two). Structure:

   - Load fixture: `val loaded = Fixtures.LOWG_TWO_AIRCRAFT.load()...`.
     **Per pass-1 plan-review finding #8:** `LoadedFixture` carries
     `world, worldIndex, controllers, initialEvents` only — it does
     **not** expose `flightPlans` or `weatherByAerodrome`. Test reads
     for those go via the `Fixtures.LOWG_TWO_AIRCRAFT` object directly
     (e.g. `Fixtures.LOWG_TWO_AIRCRAFT.flightPlans[aircraftAId]`).
   - Build aircraft A + B with `createMission(HighLevelGoal.CircuitTraining(
     circuits = 2, fullStopOnLast = true), ...)` × 2.
   - **Author B's mission-start offset** (per pass-1 plan-review
     finding #4 — not a filing offset): both plans are filed at
     `SimTime.ZERO` via `loaded.initialEvents`, but B's first
     `PilotDecisionTick` is added separately at e.g.
     `SimTime.ofMinutes(2)`. Document this is **mission-start**, not
     **filing**.
   - `SimState.initial(seed = 42L, aircraft = listOf(aircraftA,
     aircraftB), ...)` — initial state seeds `rngByAircraft` for both
     aircraft.
   - `runUntilWithStateTrace(initial, initialEvents, until = 90 * 60 *
     1000L)` (G2's wall ceiling — generous, will measure actual).
   - **Diagnostic preamble**: per-aircraft journey + per-aircraft trace
     queries (`responsibilityTransitions(aircraftA.id)`,
     `missionStepTransitions(aircraftB.id)`, etc — all already
     aircraft-scoped per repo-scout finding #8).
   - **Outcome pins** (per aircraft): both reach Parked; both at stand
     **points** (per pass-1 plan-review finding #7 — `stands.values
     .map { it.point }.toSet()`, mirroring `LowgGoldenTest.kt:427-430`,
     **not** `stands.keys`); both missions complete.
   - **Causal pins** (decision context): the 5 partial-orders listed
     above. The conflict-resolution pin uses the state-window query
     (per pass-1 plan-review findings #5 + #6) — not a direct
     record→state cursor.
   - **Wake-rule pin**: `firstSeparationAssessment(B, leader = A).wakeRule
     is WakeRule.IcaoNoAdditionalWakeMinimum && ...leader ==
     WakeCategory.L && ...follower == WakeCategory.L` — pins the
     rule was *evaluated*, not just that no extra spacing was
     observed. Naming per pass-1 plan-review finding #2; the case
     carries both leader and follower per pass-3 plan-review
     finding #1.
   - **Time band**: capture observed wall once on first green; pin a
     band ±15% per practice-scout finding #5. The 90-minute ceiling
     above is the **first-implementation generous bound**; the ±15%
     band is the **post-first-green tightened pin**, not a same-pass
     dual standard (per pass-1 plan-review finding #12). Both
     iterations are part of fn-8.2 (acceptance: "tight band recorded
     in evidence").

6. **Cross-references** — when G1 lands, update:
   - `AGENTS.md` `## Golden tests` section (add G1 bullet alongside
     G0/G2).
   - `LowgGoldenTest.kt` class docstring (`@see` G1).
   - `G2CrossAerodromeVfrTest.kt` class docstring (currently mentions
     G0 only — add G1 as the multi-aircraft sibling).
   - `Fixtures.kt` KDoc (add `LOWG_TWO_AIRCRAFT` paragraph naming the
     world-candidate-authored GA stand points; cite a specific AIP
     section only if the implementer can do so without speculation).

### Pattern reuse

- `LowgGoldenTest` (`sim/src/jvmTest/.../LowgGoldenTest.kt`, 491 lines)
  — line-for-line mirror of structure (fixture load → build state → drive
  → assertions). Specifically `LowgGoldenTest.kt:427-430` for the
  stand-point membership pattern (`stands.values.map { it.point }
  .toSet()`).
- `G2CrossAerodromeVfrTest` — mirror for multi-controller staging via
  `controllerAt(aerodromeId, role)` and the SimTrace harness usage.
- `SimTrace` queries (`positionPointTransitions`,
  `missionStepTransitions`, `responsibilityTransitions`,
  `commitmentStageTransitions`) all take `AircraftId` (per repo-scout
  finding #8) — work for multi-aircraft out of the box.
- `Fixture.flightPlans: Map<AircraftId, FiledPlan>` already supports
  multiple entries; `Fixture.load()` sorts by `AircraftId.value` for
  determinism (per repo-scout). Both plans get a single
  `FlightPlanFiled` event at `SimTime.ZERO` — there is no per-plan
  filing time today (per pass-1 plan-review finding #4).
- `SimRandom.split(tag)` at `SimRandom.kt:40` is the **existing**
  splittable-RNG helper; the per-aircraft RNG model uses it directly,
  no new aliases.

## Risks / dependencies

- **No hard deps.** `fn-7` (CTR-radius retune) is unrelated — circuit
  traffic doesn't reach the CTR boundary. fn-6 / fn-7 / fn-5 are all
  done; G1 is independent.
- **Risk: discovery cost.** First multi-aircraft test in the repo —
  bugs in the harness (PRNG threading, tick ordering, sequencing rule
  wiring) may surface only under G1's load. Per `feedback_pass_scope`,
  fold typed wins into G1's closing pass; file larger surprises as
  deferments. If a structural bug emerges (e.g. controller can't
  reason about two aircraft in the same `responsibilities` map),
  STOP and re-evaluate.
- **Risk: brittle conflict authoring.** B's mission-start offset is
  empirical. If circuit timing shifts due to a kinematic refactor,
  the conflict may stop occurring; the test becomes a "nothing
  interesting happened" green. Mitigation: pin the test's
  *forced-conflict invariant*: `extendDownwind(B)` MUST be observed
  during the run. If absent, the test fails with "B never had to
  extend — conflict authoring shifted."
- **Risk: pre-existing flake** `:migration:jvmTest >
  LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport`
  — out of G1 scope; ignore.
- **Risk: `SeparationAssessment` extension blast radius.** If
  `assessSeparation` is widely consumed, the `wakeRule` field
  addition touches more sites than expected. The non-breaking shape
  (additive field) keeps existing readers unchanged; if the wake-rule
  extraction inside `assessSeparation` itself requires deep refactor
  of `WakeSeparation.kt`'s lookup logic, scope-shift to (a) keep
  `wakeRule` derivation behind a small adapter and file the
  WakeSeparation cleanup as a follow-up, or (b) split fn-8.1 into
  two: ADT extension as its own task, then the rest.
- **Risk: per-aircraft RNG threading regression.** Adding
  `rngByAircraft` to `SimState` and threading it through pilot ticks
  in `Step.kt` is a new path; the existing single-aircraft golden
  (G0 / `LowgGoldenTest`) must stay byte-identical at the trace
  level (or close to it). If the addition shifts G0's existing
  pinned values (e.g. readback delay seeded differently), STOP and
  reconcile — either re-baseline G0's pins as part of fn-8.1 or
  adjust the seeding strategy so G0's single-aircraft RNG path is
  invariant.

## Acceptance

- **R1:** `Fixtures.LOWG_TWO_AIRCRAFT` exists alongside `Fixtures.LOWG`
  and `Fixtures.LOWG_LJMB_VFR`. Carries `startPoints: Map<AircraftId,
  PointId>` (two distinct LOWG stand points authored in
  `world-candidate.json`) plus `flightPlans` for both aircraft. KDoc
  cites the chosen stand points and names the world-candidate
  authoring as the source (no speculative AIP claims).
- **R2:** `SimState` carries `rngByAircraft: Map<AircraftId, SimRandom>`,
  seeded once per aircraft via `SimRandom.split(aircraftId.value)`.
  Pilot decision-tick handlers thread per-aircraft RNG forward via the
  existing `(value, newRandom)` contract. Determinism evidence:
  swapping the within-tick scheduling order of aircraft does not
  change either aircraft's draws (same aircraft IDs throughout). G0's
  trace is byte-identical or has its single-aircraft pins re-baselined
  as part of fn-8.1.
- **R3:** `SeparationAssessment` (defined in
  `controller/observe/BeliefState.kt:188`) carries a new
  `wakeRule: WakeRule` field. `WakeRule` is a sealed hierarchy with
  cases `IcaoNoAdditionalWakeMinimum(leader, follower)` (fallback
  path — pair not in `ICAO_WAKE_TABLE`; carries both categories so
  it remains diagnosable for any fallback pair, not just same-
  category cases),
  `IcaoLeaderFollower(leader, follower, wakeMinimumNm: Double)`
  (explicit table row), and `UnknownCategory` (null category).
  Classifier follows the three-step logic in §Approach 3 (null →
  Unknown; table hit → LeaderFollower; fallback (any non-listed
  pair) → NoAdditionalWakeMinimum).
  Names use ICAO `WakeCategory.{J, H, M, L}`; no `Meters`; existing
  NM fields preserved.
- **R4:** Event-ordering audit (re-framed per pass-2 plan-review
  finding #2): the comparator / `EVENT_ORDER` / `seq` assignment
  totally-orders simultaneous `PilotDecisionTick` events for different
  aircraft. Audited and confirmed; if not, fixed at the comparator
  site (NOT a non-existent "dispatcher").
- **R5:** New `G1TwoAircraftCircuitsTest` at
  `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt`
  exists. Mirror of `LowgGoldenTest` shape: single `@Test` method,
  fixture-driven, single behavioural narrative.
- **R6:** Test pins the 5 causal partial-orders from §Decision context.
  The conflict-resolution pin (#3) uses a state-window observable
  via `stateAtOrBefore(time)` (or whichever SimTrace query is added /
  reused) and asserts on stable observables (`PilotPhase` family or
  `positionPoint` membership), not mission-step strings.
- **R7:** Test pins the wake-rule cell that fired:
  `firstSeparationAssessment(aircraft = A, other = B).wakeRule is
  WakeRule.IcaoNoAdditionalWakeMinimum` AND
  `.leader == WakeCategory.L` AND `.follower == WakeCategory.L`.
  Two C172s are both Light; L→L is not in `ICAO_WAKE_TABLE`, so the
  assessment hits the fallback classifier. Pinning leader+follower
  (not just a single category) keeps the pin useful for non-same-
  category fallback pairs in future scenarios (per pass-3 plan-
  review finding #1). Fields on `SeparationAssessment` are
  `aircraft` / `other` (not `leader` / `follower` — see pass-2
  plan-review finding #7).
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
  `LOWG_TWO_AIRCRAFT`. Cited stand-point source = world-candidate
  authoring (no speculative AIP claims).

## Review considerations

Per `feedback_plans_review_aware.md`. Each axis addressed inline so
silence is impossible (per pass-1 plan-review finding #9).

### FP / type-safety

- `WakeRule` is a sealed interface with explicit case classes. Pattern
  matches over it are exhaustive at compile time.
- `SeparationAssessment` extension is additive (`wakeRule` field added
  alongside existing NM fields); existing readers are not broken.
- Per-aircraft RNG state lives on `SimState` as a typed `Map<AircraftId,
  SimRandom>`; `SimRandom` is already a `value class` with the
  `(value, newRandom)` thread-forward contract — no leakage to mutable
  state.
- `Fixture` extension carries `startPoints: Map<AircraftId, PointId>?`
  alongside existing `standPointId: PointId`. Optional field with
  default null preserves existing fixtures (`LOWG`, `LOWG_LJMB_VFR`)
  without modification.

### Test architecture

- `G1TwoAircraftCircuitsTest` is a single `@Test` method mirroring
  G0's shape — single behavioural narrative driven by a fixture.
- Causal partial-order pins (5 of them) are robust to controller-
  policy refactors that shift exact timing. Tick-number pins are
  forbidden.
- Forced-conflict invariant (R8) catches the "test went dull" failure
  mode: if circuit timing shifts and B never has to extend, the test
  fails loud rather than silently going green.
- Wake-rule pin (R7) is "rule was evaluated, returned the
  doctrinally-correct cell" — not "no extra spacing observed". Pins
  the **causal observable** from the engine, not the absence of an
  effect.
- Determinism evidence for R2 is captured in fn-8.1 evidence (per-
  aircraft draw stability under within-tick scheduling order
  changes); not a per-commit test.
- No scaffold tests added per `feedback_testing_philosophy.md`.

### Impact

- `SimState` gains one new field (`rngByAircraft`); `SimState.initial`
  and the pilot-tick dispatcher in `Step.kt` are touched. Single-
  aircraft trace stability (G0) is the load-bearing regression risk;
  R2 acceptance includes byte-identical-or-rebaselined G0 evidence.
- `SeparationAssessment` gains `wakeRule: WakeRule` — additive, non-
  breaking. Blast-radius audit BEFORE writing the change; > 10 call
  sites trigger a task split.
- `Fixture` gains optional `startPoints` field — fixtures `LOWG` and
  `LOWG_LJMB_VFR` unchanged.
- Two new world-candidate stand points referenced (read-only).
- Doc cross-references in 4 files.

### Operational correctness

- Per-aircraft RNG state ensures determinism is robust to within-tick
  scheduling-order changes — a real-world refactor risk that's only
  exposed by multi-aircraft scenarios.
- `WakeRule.IcaoNoAdditionalWakeMinimum(leader = L, follower = L)` is the
  doctrinally-correct cell for two C172s under ICAO Doc 4444 §5.8;
  G1 pins that cell explicitly so future refactors of
  `WakeSeparation.kt` (e.g. adding RECAT-EU or fail-closed handling
  of `UnknownCategory`) preserve the L→L semantics.
- Mission-start offset (not filing offset) for B keeps the
  `Fixture.load()` contract intact while still authoring the
  conflict. If per-plan filing time becomes needed for another
  scenario, it's filed cleanly as `D-PASS-fixture-per-plan-filing-
  time` rather than crammed into fn-8.

## Early proof point

Task `fn-8.1` lands the foundation (fixture + per-aircraft RNG state +
separation ADT + tick-ordering audit). If `SeparationAssessment`
extension has hidden blast radius (touches > ~10 sites), STOP and
split. If per-aircraft RNG threading shifts G0's existing trace
pins (the regression risk), STOP and reconcile — either re-baseline
G0's pins as part of fn-8.1 or adjust seeding so G0's single-
aircraft path is invariant. fn-8.2 builds on a known-good
foundation; it should be the test authorship pass with no
architectural surprises.

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | `Fixtures.LOWG_TWO_AIRCRAFT` with `startPoints` map | fn-8.1 | — |
| R2  | Per-aircraft `rngByAircraft` state on `SimState` (using existing `SimRandom.split`) | fn-8.1 | — |
| R3  | `SeparationAssessment.wakeRule: WakeRule` ADT (ICAO names, NM units) | fn-8.1 | — |
| R4  | Tick ordering audit + fix if needed | fn-8.1 | — |
| R5  | `G1TwoAircraftCircuitsTest` exists, mirrors G0 shape | fn-8.2 | — |
| R6  | 5 causal partial-order pins (state-window for conflict-resolution) | fn-8.2 | — |
| R7  | Wake-rule pin: `IcaoNoAdditionalWakeMinimum(leader = L, follower = L)` | fn-8.2 | — |
| R8  | `extendDownwind(B)` observation pin (forced-conflict invariant) | fn-8.2 | — |
| R9  | No regression in existing test suites + detekt | fn-8.1, fn-8.2 | — |
| R10 | Cross-reference doc updates | fn-8.2 | — |

## Deferments register

**Filing venue (consistent with fn-7 closure):** entries are filed
in `~/.claude/plans/pilot-firewall.md` § Deferments register, per
the user's persistent memory `reference_audit_registers.md` which
explicitly states *"D-AUDIT/D-PF/D-PASS items live in
~/.claude/plans/pilot-firewall.md § Deferments register, not in the
project repo"*. Codex pass-3 plan-review noted that project
instructions name `.plan` as the canonical backlog; the user's
memory is the authoritative override for D-PASS-* items because:
(a) `.plan` uses A* / M* / G* (epic-shaped backlog) namespacing,
not D-PASS-*; (b) fn-7 closed using this same venue convention with
no follow-up correction. Detail bodies live below in this section;
register pointers go to `pilot-firewall.md`. `.plan` is **NOT**
updated under fn-8 (same convention as fn-7).

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
- **`D-PASS-recat-eu-wake`** — RECAT-EU wake-category model (CAT A–F)
  alongside the existing ICAO Doc 4444 J/H/M/L. The current code
  uses the ICAO model only; RECAT-EU is a separate scoped model
  change. Future epic if/when needed.
- **`D-PASS-fixture-per-plan-filing-time`** — extend `Fixture` /
  `MultiAerodromeFixture` to carry per-plan filing time so different
  aircraft can be filed at different `SimTime`s. Today all plans are
  filed at `SimTime.ZERO` and conflicts must be authored via
  mission-start offset (delayed `PilotDecisionTick`). Future pass if
  needed for a scenario where filing-vs-mission-start distinction is
  load-bearing.
