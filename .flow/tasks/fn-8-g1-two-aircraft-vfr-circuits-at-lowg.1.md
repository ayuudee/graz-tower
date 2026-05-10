---
satisfies: [R1, R2, R3, R4, R9]
---

## Description

Foundation pass for G1: lands the four blocking infra changes the
two-aircraft test depends on. Per `feedback_pass_scope`, fold all
typed-value wins into this single foundation task rather than spawning
follow-ups.

This is the **early proof point** for fn-8: if the `SeparationAssessment`
extension has hidden blast radius (touches > ~10 sites) or per-
aircraft RNG threading shifts G0's existing trace pins (regression
risk), STOP and reconcile before fn-8.2 starts.

**Size:** M
**Files:**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt`
  (new `LOWG_TWO_AIRCRAFT` constant + supporting Fixture extension if
  needed)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixture.kt`
  (extend with optional `startPoints: Map<AircraftId, PointId>?`)
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimState.kt`
  (add `rngByAircraft: Map<AircraftId, SimRandom>` + helpers)
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/Step.kt` or
  whatever dispatches pilot decision ticks (read/update per-aircraft
  RNG via the new SimState helpers)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/BeliefState.kt`
  (`SeparationAssessment` lives here at line 188 — the `wakeRule:
  WakeRule` field is added on this data class; per pass-2 plan-review
  finding #4 — NOT in SeparationEngine.kt as the earlier draft said)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/SeparationEngine.kt`
  + sibling `WakeSeparation.kt` (the extraction logic that derives
  the `WakeRule` cell when populating `SeparationAssessment`; ICAO
  category names, NM units)
- Possible call-site sweep wherever `assessSeparation` /
  `SeparationAssessment` is consumed (audit blast radius first; if
  > 10 sites, escalate per Risks)

**Files NOT edited** (verified by grep — no migration expected):
- `sim/.../testing/Fixtures.kt`'s existing `LOWG` and `LOWG_LJMB_VFR`
  constants (the new `startPoints` field is optional / nullable).

## Approach

### 1. `LOWG_TWO_AIRCRAFT` fixture

Add a new `Fixtures.LOWG_TWO_AIRCRAFT` object alongside existing
`Fixtures.LOWG` and `Fixtures.LOWG_LJMB_VFR` in
`sim/src/jvmTest/kotlin/.../testing/Fixtures.kt`.

Carries:
- Two distinct `PointId`s for the two aircraft's start stands.
  Implementer picks two adjacent GA stand points from the
  world-candidate JSON (e.g. `LOWG_STAND_1_POINT` + a sibling).
  Document the choice in KDoc citing the **world-candidate authoring**
  as source — NOT a speculative AIP claim (per pass-1 plan-review
  finding #11).
- `flightPlans: Map<AircraftId, FiledPlan>` with two entries:
  aircraft A and B, both VFR LOWG → LOWG (circuit training).
  **Wake category lives on `AircraftState` / aircraft observation,
  NOT on `FiledPlan`** (per pass-2 plan-review finding #6 —
  `FiledPlan.Vfr` carries departure / destination / runway / intent
  only). The C172/`WakeCategory.L` requirement is therefore enforced
  at **aircraft construction time** in fn-8.2 (not in this fixture's
  `flightPlans` map). Document in this fixture's KDoc that the
  intended pairing is two Light-category aircraft, with the actual
  type/wake-category set when fn-8.2 builds `aircraftA` and
  `aircraftB`.
- `startPoints: Map<AircraftId, PointId>` — the new shape.

**Two design options for the `Fixture` shape:**
- (a) Extend the existing `Fixture` class to optionally carry
  `startPoints: Map<AircraftId, PointId>?` alongside
  `standPointId: PointId`. Existing fixtures (`LOWG`, `LOWG_LJMB_VFR`)
  set `startPoints = null`; the new fixture sets it.
- (b) Have `Fixture` carry only `startPoints: Map<AircraftId, PointId>`
  and refactor the existing fixtures to use the map shape with one
  entry.

**Recommended: (a)** — minimal blast radius on existing fixtures;
existing `LOWG` and `LOWG_LJMB_VFR` semantics unchanged. Document the
asymmetry in `Fixture` KDoc.

**Helper for non-null access (per pass-4 plan-review finding #3):**
multi-aircraft tests need a non-null `startPoints` map; nullable
field + `getValue` would not compile. Add a small helper on
`Fixture`:
```kotlin
/** For multi-aircraft fixtures: returns the non-null startPoints
 *  map. Throws with a clear error if the fixture didn't author
 *  per-aircraft start points (i.e. it's a single-aircraft fixture
 *  using `standPointId` instead). */
fun Fixture.requiredStartPoints(): Map<AircraftId, PointId> =
    startPoints
        ?: error("Fixture for ${aerodromeId} (${candidatePath}) has no " +
                 "startPoints — this is a " +
                 "single-aircraft fixture (use standPointId). " +
                 "Multi-aircraft tests need a fixture authoring per-" +
                 "aircraft startPoints.")
```
Multi-aircraft tests call `fixture.requiredStartPoints()
.getValue(id)`; single-aircraft tests continue to use
`fixture.standPointId`. The helper makes the failure mode loud at
the call site rather than `null.getValue(...)` NPE.

**Validate `startPoints` in `LoadedFixture.validate(...)`** (extend
existing validation). Today validation only checks `standPointId`
via `FixtureViolation.StandPointMissing`; extend with new
`FixtureViolation` leaves to cover the new failure modes (per
pass-5 plan-review):
- `FixtureViolation.StartPointWithoutFlightPlan(aircraftId,
  pointId)` — `startPoints` has an entry whose `aircraftId` isn't
  in `flightPlans.keys`.
- `FixtureViolation.FlightPlanMissingStartPoint(aircraftId)` —
  `flightPlans` has an entry without a corresponding `startPoints`
  entry (only fires when `startPoints != null`).
- `FixtureViolation.DuplicateStartPoint(pointId, aircraftIds)` —
  two aircraft authored at the same start `pointId`.
- `FixtureViolation.StartPointMissing(aircraftId, pointId)` —
  authored `pointId` doesn't exist in the loaded world's
  `PhysicalGeometry.points`.

When `startPoints != null`, validation runs all four checks. When
`startPoints == null`, only the existing `StandPointMissing` check
applies (single-aircraft fixtures unchanged).

This catches authoring bugs at fixture-load time rather than at
test-run time.

### 2. Per-aircraft RNG state on `SimState`

Per pass-1 plan-review finding #1: `SimRandom.split(tag)` already
exists at `SimRandom.kt:40` (KDoc explicitly addresses the "two
independent streams from same parent at same moment" case). The
issue with the earlier draft was state threading: calling
`state.rng.split(id.value)` on every tick — without persisting the
child RNG — re-seeds the child each tick (parent hasn't advanced for
that aircraft) and produces repeated draws.

The fix: **persist per-aircraft RNG state in `SimState`.**

Add to `SimState`:
```kotlin
data class SimState(
    // ... existing fields ...
    val rng: SimRandom,                                  // already exists
    val rngByAircraft: Map<AircraftId, SimRandom>,       // NEW
    // ...
)
```

Update `SimState.initial(seed, ...)` to seed `rngByAircraft` once per
aircraft using the existing splittable RNG:
```kotlin
val rootRng = SimRandom.ofSeed(seed)
val perAircraft = aircraft
    .sortedBy { it.id.value }
    .associate { it.id to rootRng.split(it.id.value) }
return SimState(
    // ...
    rng = rootRng,
    rngByAircraft = perAircraft,
    // ...
)
```

**Spawn-path seeding (pass-2 plan-review finding #1).** The existing
`Step.handleSpawn(...)` at `Step.kt:786` adds aircraft to
`state.aircraft` mid-sim. Without spawn-path seeding, those aircraft
would have no `rngByAircraft` entry and the helper below would fail
with a missing-key error on first pilot tick. Update `handleSpawn` to:
- Derive a fresh per-aircraft RNG via `state.rng.split(ac.id.value)`
  (or the canonical parent stream — implementer audits and documents).
- Add it to `rngByAircraft` in the returned state.
- Preserve the existing duplicate-id guard (line 798).

Add small helpers on `SimState` (or a sibling extension file):
```kotlin
fun SimState.aircraftRng(id: AircraftId): SimRandom =
    rngByAircraft[id]
        ?: error("aircraftRng: $id has no RNG entry — invariant violated. " +
                 "Every key in state.aircraft must have a matching " +
                 "rngByAircraft entry. Check SimState.initial / handleSpawn.")

fun SimState.withAircraftRng(id: AircraftId, newRng: SimRandom): SimState =
    copy(rngByAircraft = rngByAircraft + (id to newRng))
```

The explicit error in `aircraftRng` makes the missing-entry case loud
rather than relying on `Map.getValue`'s default `NoSuchElementException`.

In the pilot-tick dispatcher (likely
`Step.kt:handlePilotProcessingComplete` or
`Step.kt:handlePilotDecisionTick` — implementer audits at task time):
- Read `state.aircraftRng(ac.id)` instead of `state.rng`.
- Thread the returned `newRandom` back via
  `state.withAircraftRng(ac.id, newRandom)` instead of
  `state.copy(rng = newRandom)`.
- The shared `state.rng` is preserved for non-aircraft-scoped
  randomness (weather, ATIS letter rotation, anything not keyed by
  aircraft).

**Determinism contract (R2 + pass-1 plan-review finding #10):** the
property is **order-of-dispatch invariance for same aircraft IDs** —
swapping the within-tick scheduling order of aircraft A and B leaves
each aircraft's draws unchanged. **NOT** "changing an aircraft's ID
leaves draws unchanged" (that contradicts the per-aircraft-keyed
split).

**Evidence to capture in `## Evidence`:**
- Run G0 with the new threading; capture trace.
- Confirm trace is byte-identical to pre-fn-8.1 G0 trace, OR re-baseline
  G0's pinned values (e.g. observed wall, readback delays) as part of
  fn-8.1 with explicit re-baseline rationale.
- Run a synthetic two-aircraft micro-scenario twice with the same
  aircraft IDs but different within-tick dispatch order; confirm each
  aircraft's PRNG draws are identical across runs.

**Don't gold-plate the helpers.** No new `derive` alias. No splittable-
RNG redesign. Just: per-aircraft state on SimState + 2 small helpers
+ pilot-tick threading change.

### 3. `SeparationAssessment` + `WakeRule` ADT (ICAO names, NM units)

`SeparationAssessment` is defined at
`controller/src/commonMain/kotlin/.../observe/BeliefState.kt:188`
(per pass-2 plan-review finding #4 — NOT in SeparationEngine.kt).
Current shape:
```kotlin
data class SeparationAssessment(
    val aircraft: AircraftId,        // not "leader"
    val other: AircraftId,           // not "follower"
    // ... NM-typed fields ...
)
```
The extension is **additive** — keep all existing fields including
the `aircraft` / `other` field names, add `wakeRule`:

```kotlin
sealed interface WakeRule {
    /** ICAO Doc 4444 §5.8 fallback: pair has no entry in
     *  ICAO_WAKE_TABLE — radar minimum applies, no wake supplement.
     *  Covers any non-listed pair (e.g. L→L, L→M, M→M, etc.) — not
     *  only same-category cases.
     */
    data class IcaoNoAdditionalWakeMinimum(
        val leader: WakeCategory,
        val follower: WakeCategory,
    ) : WakeRule

    /** ICAO Doc 4444 §5.8 leader/follower with explicit wake supplement. */
    data class IcaoLeaderFollower(
        val leader: WakeCategory,
        val follower: WakeCategory,
        val wakeMinimumNm: Double,
    ) : WakeRule

    /** Wake category absent / unknown — engine fails closed. */
    data object UnknownCategory : WakeRule
}

data class SeparationAssessment(
    // ... ALL existing fields preserved (requiredSeparationNm,
    //     currentSeparationNm, etc.) ...
    val wakeRule: WakeRule,
)
```

**Critical (per pass-1 plan-review findings #2 + #3):**
- **Naming uses ICAO `WakeCategory.{J, H, M, L}`**, not RECAT-EU
  (CAT A–F). The existing `WakeCategory` at
  `WakeSeparation.kt:30` carries J/H/M/L; the earlier draft's
  `RecatFFNoMin` is wrong against the actual model.
- **Units stay NM throughout.** No `Meters`. The existing fields
  `requiredSeparationNm` (line 96 of SeparationEngine.kt),
  `wake.distanceNm` (line 66), and the `WakeSeparationMinima` rows
  in `WakeSeparation.kt:30-...` all use `Double` NM. `WakeRule`
  follows suit.
- **No unit-conversion churn.** Existing readers ignore the new
  `wakeRule` field; there's no API break.

**Audit blast radius BEFORE writing the change**: grep
`assessSeparation\|SeparationAssessment` across the codebase. If
> 10 call sites need updating, STOP and split this work into its
own task (per Risks register on the epic).

**Wake-rule extraction logic (per pass-2 plan-review finding #3 —
critical):** the `ICAO_WAKE_TABLE` at `WakeSeparation.kt:28-...` has
8 rows: J→J, J→H, J→M, J→L, H→H, H→M, H→L, M→L. **It does NOT have
an L→L row, nor M→M.** The extraction logic must follow this
classifier inside `assessSeparation` (or a small helper):

```kotlin
fun classifyWakeRule(leader: WakeCategory?, follower: WakeCategory?): WakeRule = when {
    leader == null || follower == null -> WakeRule.UnknownCategory
    else -> {
        val tableHit = ICAO_WAKE_TABLE.firstOrNull {
            it.leader == leader && it.follower == follower
        }
        when (tableHit) {
            null -> WakeRule.IcaoNoAdditionalWakeMinimum(leader = leader, follower = follower)
            else -> WakeRule.IcaoLeaderFollower(
                leader = leader,
                follower = follower,
                wakeMinimumNm = tableHit.distanceNm,
            )
        }
    }
}
```

Three concrete examples:
- L→L: not in table → `IcaoNoAdditionalWakeMinimum(leader = L, follower = L)`.
- L→M: not in table (no leader-L row exists in `ICAO_WAKE_TABLE`) →
  `IcaoNoAdditionalWakeMinimum(leader = L, follower = M)`. The
  fallback covers ALL non-listed pairs, not just same-category;
  preserving both leader and follower in the case is what makes this
  diagnosable for non-L→L scenarios outside G1 (per pass-3 plan-
  review finding #1).
- J→J: in table at line 30 (6.0 NM) → `IcaoLeaderFollower(J, J, 6.0)`.
- H→H: in table at line 35 (4.0 NM) → `IcaoLeaderFollower(H, H, 4.0)`.

**"Same category = no additional minimum" is FALSE for J→J and H→H.**
Those have explicit table rows with concrete minima. Only fallback
pairs (L→L, M→M, and any non-listed pair) are
`IcaoNoAdditionalWakeMinimum` (general fallback, covers any non-
listed pair).

The classifier accepts nullable categories so unknown-aircraft-type
cases produce `UnknownCategory` rather than crashing.

### 4. Event-ordering audit (re-framed per pass-2 plan-review finding #2)

The earlier draft framed this as "audit `SimState.advance` /
dispatcher" — but `Step.kt` processes one `SimEvent` at a time;
ordering for simultaneous pilot ticks is owned by the **event queue
comparator** / `EVENT_ORDER` priority constants / per-event `seq`
assignment, **not** a dispatcher loop.

Audit:
- Find the event-comparator site (likely in `EventQueue.kt` or in
  `SimEvent.kt` — search for `EVENT_ORDER`, `compareBy`, or `seq`).
- Confirm two `PilotDecisionTick` events at the same `time` for
  different aircraft sort deterministically. Plausible total orders:
  by `aircraftId.value` ascending; by event-insertion `seq`; by
  some explicit priority constant.
- If the order is non-deterministic (e.g. relies on iteration order
  of an unsorted collection), fix at the comparator site.

If `Fixture.load()` already sorts `flightPlans.entries.sortedBy
{ it.key.value }` (per repo-scout) and the resulting
`FlightPlanFiled` events go into a queue with a stable comparator,
the chain is already deterministic. Capture the evidence in `## Evidence`
(file path + comparator citation).

With per-aircraft RNG (R2), within-tick ordering matters less for
*randomness* (each aircraft's stream is independent). But
determinism of *non-RNG* state mutations (event-emission order,
side-effect ordering) still depends on a stable total order.

## Investigation targets

**Required** (read before coding):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixture.kt`
  (current `Fixture` shape + `LoadedFixture` carrier — note that
  `LoadedFixture` exposes `world, worldIndex, controllers,
  initialEvents` only, NOT `flightPlans` or `weatherByAerodrome`;
  fn-8.2 test code reads via the `Fixtures.LOWG_TWO_AIRCRAFT` object
  directly per pass-1 plan-review finding #8).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt`
  (existing `LOWG` + `LOWG_LJMB_VFR` patterns to mirror).
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimRandom.kt`
  (current PRNG shape — `SimRandom.split(tag)` already exists at
  line 40; we use it directly, no new aliases).
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimState.kt`
  (current `rng: SimRandom` field at line 38; `rngByAircraft` is
  added alongside).
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/Step.kt`
  (find pilot-tick dispatch site; thread per-aircraft RNG via
  the new SimState helpers).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/SeparationEngine.kt`
  (assess return type — `requiredSeparationNm` at line 96,
  `wake.distanceNm` at line 66 — units stay NM).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/WakeSeparation.kt`
  (`WakeCategory` enum + `WakeSeparationMinima` rows; ICAO J/H/M/L).
- `cad/airports/rendered/lowg/world-candidate.json:1240+`
  (LOWG_STAND_* point IDs — pick two adjacent GA stand points).

**Optional** (reference as needed):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LowgGoldenTest.kt`
  (single-aircraft golden — see how `Fixtures.LOWG.standPointId` is
  consumed; mirror for two; line 427-430 for the
  `stands.values.map { it.point }` pattern).

## Key context

- **`SimRandom.split` already exists.** Don't add a `derive` alias.
- **Per-aircraft RNG must be PERSISTED in `SimState`.** Calling
  `split(id)` per tick without persistence re-seeds the child each
  tick → repeated draws (the bug pass-1 plan-review finding #1
  surfaced).
- **Don't break existing tests on the SeparationAssessment extension.**
  `wakeRule` is an additive field; existing readers ignore it.
- **The fixture extension is "option a" by default** (extend `Fixture`
  with optional `startPoints`). If during implementation it becomes
  obvious that a clean shape requires a deeper refactor, surface it in
  task evidence and either revisit (b) or file as a follow-up.
- **G0 trace stability is load-bearing.** With per-aircraft RNG
  threading, the single-aircraft case must produce a byte-identical
  trace OR have its pinned values re-baselined as part of fn-8.1
  with explicit rationale.

## Acceptance

- [ ] `Fixtures.LOWG_TWO_AIRCRAFT` exists with two distinct stand
      `PointId`s, two `flightPlans` entries (VFR LOWG circuit
      training; wake category is NOT on `FiledPlan` and is set at
      aircraft construction in fn-8.2 — this fixture only carries
      flight plans), and a documented stand-
      pair choice in KDoc citing the world-candidate authoring as
      source (no speculative AIP claim).
- [ ] `Fixture` shape supports the multi-aircraft start-points pattern
      via optional `startPoints: Map<AircraftId, PointId>?`. Existing
      `LOWG` / `LOWG_LJMB_VFR` constants unchanged. Helper
      `Fixture.requiredStartPoints(): Map<AircraftId, PointId>` exists
      for multi-aircraft tests; single-aircraft tests continue using
      `standPointId`.
- [ ] `LoadedFixture.validate(...)` extended with new
      `FixtureViolation` leaves (`StartPointWithoutFlightPlan`,
      `FlightPlanMissingStartPoint`, `DuplicateStartPoint`,
      `StartPointMissing`) firing only when `startPoints != null`.
      Catches authoring bugs at load time. Existing single-aircraft
      validation (`StandPointMissing`) unchanged.
- [ ] `SimState.rngByAircraft: Map<AircraftId, SimRandom>` exists,
      seeded once per aircraft via `SimRandom.split(id.value)` in
      `SimState.initial`. Pilot decision-tick handlers thread per-
      aircraft RNG forward via `state.withAircraftRng(id, newRng)`.
- [ ] Determinism evidence captured in `## Evidence`:
      - G0 trace is byte-identical post-fn-8.1, OR G0's pinned values
        re-baselined as part of fn-8.1 with explicit rationale.
      - Synthetic two-aircraft micro-scenario: swapping within-tick
        dispatch order leaves each aircraft's draws unchanged (same
        IDs throughout).
- [ ] `SeparationAssessment` (in `controller/observe/BeliefState.kt:188`)
      carries a `wakeRule: WakeRule` field. `WakeRule` is a sealed
      hierarchy with cases `IcaoNoAdditionalWakeMinimum(leader,
      follower)` (fallback path covering ALL non-listed pairs,
      e.g. L→L, L→M; carries both categories per pass-3 plan-review
      finding #1),
      `IcaoLeaderFollower(leader, follower, wakeMinimumNm: Double)`
      (explicit `ICAO_WAKE_TABLE` row hit, e.g. J→J / H→H), and
      `UnknownCategory` (null category). Classifier follows the
      three-step null/table-hit/fallback logic from §Approach 3.
      Names use ICAO `J/H/M/L`; no RECAT-EU naming; no `Meters` (NM
      units throughout).
- [ ] All existing call sites of `assessSeparation` /
      `SeparationAssessment` build and run green (no broken
      consumers).
- [ ] Event-ordering audit captured in `## Evidence`: comparator /
      `EVENT_ORDER` / `seq` site that totally orders simultaneous
      `PilotDecisionTick` events for different aircraft. Cite the
      file + line. If a fix was needed, named in evidence.
- [ ] `LowgGoldenTest`, `G2CrossAerodromeVfrTest` stay green.
- [ ] Full test suite stays green; `./gradlew detekt` baseline unchanged.

## Done summary
fn-8.1 lands the four blocking foundations for G1: per-aircraft splittable PRNG (SimState.rngByAircraft + helpers + threaded through handlePilotTick and handleSpawn), WakeRule ADT populated additively on SeparationAssessment, Fixtures.LOWG_TWO_AIRCRAFT with Fixture.startPoints + four new validation leaves, and the tick-ordering audit (no comparator fix needed — EVENT_ORDER already total-orders simultaneous PilotDecisionTicks via AgentId.Pilot.sortKey). G0+G2 stay byte-stable; full test suite green; codex impl-review SHIP after one NEEDS_WORK→SHIP cycle that surfaced (and fixed) symbolic-vs-load-bearing threading.
## Evidence
- Commits: 357bd84bc8e7bb694bca2dddf40a92e7a0025e1e, 52ed706720869e58861d45938bf46b78687a6a27, c3b5308bcc847c36f11b6c17338522da2143d6f6
- Tests: ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest :protocol:jvmTest, ./gradlew :sim:jvmTest --tests xyz.easiersaid.twr.sim.LowgGoldenTest --tests xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest, ./gradlew :sim:jvmTest --tests xyz.easiersaid.twr.sim.PerAircraftRngSpec, ./gradlew :sim:jvmTest --tests xyz.easiersaid.twr.sim.testing.FixtureLoadSpec, ./gradlew :controller:jvmTest --tests xyz.easiersaid.twr.controller.assess.WakeRuleClassifierSpec
- PRs: