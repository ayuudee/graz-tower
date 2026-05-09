---
satisfies: [R5, R6, R7, R8, R9, R10]
---

## Description

Author the `G1TwoAircraftCircuitsTest` golden test. Mirror of
`LowgGoldenTest`'s shape (single `@Test` method, fixture-driven, single
behavioural narrative) but with two AI aircraft running in parallel.
Plus the cross-reference doc updates that follow once G1 lands.

**Size:** M (estimated ~600-700 lines of test code; the underlying
infra from fn-8.1 carries the structural complexity).

**Files:**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt`
  (new, ~600-700 lines)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/SimTraceQueries.kt`
  (small extension if `stateAtOrBefore(time: SimTime)` query absent —
  see Approach §5)
- `AGENTS.md` (`## Golden tests` section: add G1 bullet alongside G0/G2)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LowgGoldenTest.kt`
  (class docstring `@see` cross-reference to G1)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt`
  (class docstring add G1 sibling note)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt`
  (KDoc paragraph for `LOWG_TWO_AIRCRAFT`; cite world-candidate
  authoring, no speculative AIP claim)

## Approach

### 1. Test structure (mirror of LowgGoldenTest)

Follow `LowgGoldenTest.kt:39-491` line-for-line. Anchors:

- **Class KDoc** mirrors G0 + G2 prose. Names sibling tests (G0
  single-aircraft, G2 cross-aerodrome) and what G1 distinctively pins
  (single-runway sequencing, two-aircraft conflict resolution, wake-
  rule evaluation).
- **Single `@Test` method** named `two AI aircraft fly two circuits each at LOWG`
  (or similar). The run is the test; assertions read from records +
  trace.
- **Sections**: load fixture → declare aircraft + missions → SimState.initial
  → seed initialEvents → runUntilWithStateTrace → outcome pins → causal
  pins → wake-rule pin → forced-conflict invariant → time band →
  diagnostic preamble (per-aircraft journey + per-aircraft trace
  queries).

### 2. Fixture + state

**Per pass-1 plan-review finding #8:** `LoadedFixture` carries
`world, worldIndex, controllers, initialEvents` only — **not**
`flightPlans` or `weatherByAerodrome`. Test reads for those go via
the `Fixtures.LOWG_TWO_AIRCRAFT` object directly:

```kotlin
val fixture = Fixtures.LOWG_TWO_AIRCRAFT
val loaded = fixture.load().getOrElse { fail("...") }
val aircraftAId = AircraftId("OE-ABC")
val aircraftBId = AircraftId("OE-DEF")
// (or whatever pair the fixture seeds; document choice in test)
val missionA = createMission(
    goal = HighLevelGoal.CircuitTraining(circuits = 2, fullStopOnLast = true),
    startPhase = PilotPhase.AtStand,
    time = SimTime.ZERO,
    filedPlan = fixture.flightPlans[aircraftAId]   // via fixture, not loaded
        ?: fail("LOWG_TWO_AIRCRAFT missing flight plan for $aircraftAId"),
)
val missionB = createMission(... same shape with B's filedPlan ...)

// Build aircraft. WAKE CATEGORY LIVES HERE, not on FiledPlan
// (per pass-2 plan-review finding #6). Both A and B are
// C172/Light so the G1 wake-rule pin (R7) holds — set the type
// explicitly at construction:
val aircraftA = AircraftState(
    id = aircraftAId,
    type = AircraftType.C172,    // → WakeCategory.L
    pilotMission = missionA,
    positionPoint = fixture.requiredStartPoints().getValue(aircraftAId),
    // ... existing fields ...
)
val aircraftB = AircraftState(
    id = aircraftBId,
    type = AircraftType.C172,    // → WakeCategory.L
    pilotMission = missionB,
    positionPoint = fixture.requiredStartPoints().getValue(aircraftBId),
    // ... existing fields ...
)
// (Implementer audits the exact AircraftState shape and the
// type → WakeCategory mapping at task time. The contract: both
// aircraft must end up as WakeCategory.L for R7 to hold.)

// Weather: REQUIRED for runway-bearing aerodromes — `SimState.initial`
// rejects empty weather maps for any runway-bearing aerodrome (per
// pass-2 plan-review finding #5). LOWG has runways, so author weather:
val initial = SimState.initial(
    seed = 42L,
    world = loaded.world,
    worldIndex = loaded.worldIndex,
    aircraft = listOf(aircraftA, aircraftB),
    controllers = loaded.controllers.values.toList(),
    weatherByAerodrome = mapOf(lowg to fixture.weather),
    // ... other fields per existing SimState.initial signature ...
)
```

`SimState.initial` (post-fn-8.1) seeds `rngByAircraft` for both
aircraft via `SimRandom.split(id.value)`.

(`fixture.weather` field — implementer audits the
`Fixtures.LOWG_TWO_AIRCRAFT` shape post-fn-8.1; existing fixtures
expose weather authoring via either a single `weather:
WeatherObservation` field or a per-aerodrome map. Match the existing
pattern.)

### 3. Conflict authoring (deliberate — mission-start offset, NOT filing offset)

Per pass-1 plan-review finding #4: `Fixture.load()` files all plans
at `SimTime.ZERO` and there is no per-plan filing time. Author the
conflict via a **mission-start offset** — both plans are filed at
zero, but B's first `PilotDecisionTick` is delayed:

```kotlin
val initialEvents = loaded.initialEvents + listOf(
    SimEvent.AtisIssued(time = SimTime.ZERO, aerodrome = lowg, atis = lowgAtis),
    // A starts immediately:
    SimEvent.PilotDecisionTick(time = SimTime.ZERO, aircraftId = aircraftAId),
    // B's mission start is delayed by 2 minutes (empirical offset to
    // force the conflict — pin the *causal invariant*, not this
    // number; see Forced-conflict invariant §6):
    SimEvent.PilotDecisionTick(time = SimTime.ofMinutes(2), aircraftId = aircraftBId),
    SimEvent.PhysicsTick(time = SimTime.ZERO),
    SimEvent.ControllerCycle(time = SimTime.ZERO, controllerId = lowgGround.id),
    SimEvent.ControllerCycle(time = SimTime.ZERO, controllerId = lowgTower.id),
)
```

**Critical naming**: this is a **mission-start offset**, not a
**filing offset**. Both plans are filed at `SimTime.ZERO` (via
`loaded.initialEvents` from `Fixture.load()`); B's strip is owned by
the controller from time zero, but B's pilot doesn't begin acting
until the 2-minute offset. Document this distinction in the test's
KDoc — future refactors that touch fixture filing-time semantics
should leave G1's conflict authoring intact (or surface a clean
break).

### 4. Outcome pins (per aircraft)

Per pass-1 plan-review finding #7: stand-membership check uses
`stands.values.map { it.point }.toSet()`, mirroring
`LowgGoldenTest.kt:427-430`. **Not** `stands.keys` (those are stand
IDs, while `AircraftState.positionPoint` is a `PointId`).

```kotlin
val finalA = finalState.aircraft.getValue(aircraftAId)
val finalB = finalState.aircraft.getValue(aircraftBId)

check(finalA.pilotMission?.isComplete == true) { "A mission incomplete\n$journey" }
check(finalB.pilotMission?.isComplete == true) { "B mission incomplete\n$journey" }
check(finalA.altitudeM == 0.0) { ... }
check(finalB.altitudeM == 0.0) { ... }
check(finalA.phase == PilotPhase.Parked || finalA.phase == PilotPhase.AtStand) { ... }
check(finalB.phase == PilotPhase.Parked || finalB.phase == PilotPhase.AtStand) { ... }

val standPoints = loaded.world.aerodromes
    .getValue(lowg).stands.values.map { it.point }.toSet()
check(finalA.positionPoint in standPoints) { ... }
check(finalB.positionPoint in standPoints) { ... }
```

### 5. Causal partial-order pins (the load-bearing assertions)

Per gap analyst #7 + practice-scout #5. Five pins, each
`event-X precedes event-Y` shape:

1. **Taxi clearance order**: `firstControllerInstructionOf<TaxiToHoldingPoint>(aircraftAId).time
   < firstControllerInstructionOf<TaxiToHoldingPoint>(aircraftBId).time`.
2. **Single-runway gate (B holds while A departs)**:
   `firstControllerInstructionOf<ClearedForTakeoff>(aircraftAId).time
   < firstControllerInstructionOf<LineUpAndWait>(aircraftBId).time`
   (or `< firstControllerInstructionOf<ClearedForTakeoff>(aircraftBId)`).
3. **Conflict resolution: `extendDownwind(B) ≺ touchdown(A) ≺ turnBase(B)`** —
   the load-bearing G1 invariant per epic R6. This is a **three-event
   chain** (per pass-4 plan-review finding #2 — earlier draft was a
   weaker single-window pin):
   - `extendDownwind(B)` happens
   - then later `touchdown(A)` happens (A lands)
   - then later `turnBase(B)` happens (B turns base for the next/
     final circuit; the extension paid off — B is now sequencing
     behind A's completed landing)

   Per pass-1 plan-review findings #5 + #6, observables are pinned
   via record/state queries, not mission-step strings. Use
   `rec.time` for record cursors (`TransmissionRecord` exposes
   `time: SimTime` at line 29, not `transmissionStart` — per pass-2
   plan-review finding #8):

   ```kotlin
   // (a) Find the ExtendDownwind record for B:
   val extendBRecord = records
       .firstOrNull { rec -> /* ExtendDownwind && target == aircraftBId */ }
       ?: fail("B never extended downwind — forced-conflict invariant " +
               "absent (R8 fires).\n$journey")

   // (b) Find A's touchdown event/observable. Touchdown is a state
   //     transition observable in the trace, not a controller
   //     instruction. Implementer audits at task time for the
   //     stable observable. Plausible options:
   //       - PilotPhase transition: A's phase enters Landing /
   //         post-Landing (e.g. AfterLandingRoll, Vacate).
   //       - positionPoint transition: A's positionPoint reaches a
   //         runway-touchdown-zone point.
   //       - flight phase: A's altitudeM transitions to 0.0 and
   //         A is on the runway segment.
   //     Pin whichever the existing trace surfaces stably:
   val touchdownAEvent = trace.firstStateWhere { st ->
       val a = st.aircraft.getValue(aircraftAId)
       /* a is in landing/post-landing observable family */
   } ?: fail("A never touched down before timeout.\n$journey")

   // (c) Find B's turnBase. Either an instruction (TurnBase /
   //     similar) or a state observable (PilotPhase.Base entry).
   //     Audit at task time; document which surface used.
   val turnBaseBEvent = trace.firstStateWhere { st ->
       val b = st.aircraft.getValue(aircraftBId)
       /* b's phase is Base for the relevant circuit */
   } ?: fail("B never turned base after A's touchdown.\n$journey")

   // (d) Assert the three-event chain in time:
   check(extendBRecord.time < touchdownAEvent.time &&
         touchdownAEvent.time < turnBaseBEvent.time) {
       "Conflict-resolution chain violated. Expected: " +
       "extendDownwind(B).time (${extendBRecord.time}) " +
       "< touchdown(A).time (${touchdownAEvent.time}) " +
       "< turnBase(B).time (${turnBaseBEvent.time}).\n$journey"
   }
   ```

   **Implementation note for SimTrace queries:** if
   `firstStateWhere(predicate)` and/or `stateAtOrBefore(t)` don't
   exist in `SimTraceQueries.kt`, add them as small extensions —
   a few lines each. Don't gold-plate. Document the chosen
   touchdown / turnBase observable family in a comment so future
   readers see the doctrine trail (e.g. "touchdown defined as
   `PilotPhase.AfterLandingRoll` first appearance, since the
   `Landing` phase is transient and may be missed by the trace's
   sampling cadence" — implementer fills in the actual rationale).

4. **Final-circuit landing order**: `cleared-land(A_final_circuit).time
   < cleared-land(B_final_circuit).time` (assumes A lands before B
   given offset).
5. **Both reach stands**: terminal positionPoint in stand-points set
   (covered by Outcome pins above; this is the "closure"
   partial-order).

### 6. Wake-rule pin (R7)

Per practice-scout #4 + pass-1 plan-review finding #2 + pass-2
plan-review finding #7. Two C172s are both `WakeCategory.L`; L→L is
not in `ICAO_WAKE_TABLE` (no row at `WakeSeparation.kt:28-...`), so
the classifier hits the fallback path → cell is
`IcaoNoAdditionalWakeMinimum(leader = L, follower = L)`.

**Field names: `aircraft` and `other`** (per pass-2 plan-review
finding #7 — the `SeparationAssessment` data class at
`BeliefState.kt:188-190` uses `aircraft` / `other`, NOT
`leader` / `follower`):

```kotlin
val firstAssessment = beliefs.separationAssessments
    .filter { it.aircraft == aircraftAId && it.other == aircraftBId }
    .first()  // or via SimTrace query if more idiomatic
val rule = firstAssessment.wakeRule
check(rule is WakeRule.IcaoNoAdditionalWakeMinimum &&
      rule.leader == WakeCategory.L &&
      rule.follower == WakeCategory.L) {
    "Wake assessment for A vs B should be L→L (no entry in " +
    "ICAO_WAKE_TABLE → fallback to no additional minimum); got $rule.\n$journey"
}
```

(Implementer: confirm the exact path to `separationAssessments` —
it's on `BeliefState` at `BeliefState.kt:74`. May be queryable via
SimTrace; the implementer picks whichever access pattern is most
idiomatic in existing tests.)

### 7. Forced-conflict invariant (R8)

The bare-minimum pin that detects "test got dull":

```kotlin
val extendInstructions = records.filter { rec ->
    val out = (rec.utterance as? Utterance.FromController)?.output
        as? ControllerOutput.Instruct ?: return@filter false
    val instr = (out.dispatch as? Dispatch.Direct)?.instruction
    instr is ExtendDownwind && out.target == aircraftBId
}
check(extendInstructions.isNotEmpty()) {
    "Forced-conflict invariant violated: B never had to extend downwind. " +
    "Either circuit timing shifted (refactor needed?), or the offset is wrong. " +
    "Adjust B's start offset until extend-downwind fires.\n$journey"
}
```

### 8. Time band (per pass-1 plan-review finding #12)

**First-implementation acceptance**: generous 90-min ceiling
(matches G2). No tightened band yet.

**Post-first-green**: capture observed wall in `## Evidence`; pin a
band ±15% in the test. Both iterations are part of fn-8.2 — the test
does not ship with only the generous bound; tightening is part of
the same task's acceptance.

```kotlin
// First implementation:
val until = SimTime.ZERO + SimDuration.ofMillis(90 * 60 * 1000L)
val finalState = runUntilWithStateTrace(initial, initialEvents, until)
// ... assertions ...

// After first green: tighten to ±15% band around observed wall.
// Update the test with the tightened band and capture both
// iterations in `## Evidence`.
```

### 9. Diagnostic preamble

Mirror G2's debug block — print per-aircraft journey + responsibility
transitions + position-point transitions + mission-step transitions for
both aircraft. Critical for debugging when (not if) the conflict
authoring shifts.

### 10. Cross-reference doc updates

- **`AGENTS.md`** `## Golden tests` section: add a third bullet
  describing G1.
- **`LowgGoldenTest.kt`** class docstring: add `@see G1TwoAircraftCircuitsTest`
  cross-reference.
- **`G2CrossAerodromeVfrTest.kt`** class docstring: currently mentions
  G0; add a sentence noting G1 as the multi-aircraft sibling.
- **`Fixtures.kt`** `object Fixtures` KDoc: add a paragraph on
  `LOWG_TWO_AIRCRAFT` (chosen stand point IDs + cite world-candidate
  authoring as source — no speculative AIP claim per pass-1 plan-
  review finding #11).

## Investigation targets

**Required** (read before coding):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LowgGoldenTest.kt`
  (491 lines — the canonical mirror; read top-to-bottom before writing
  G1; specifically lines 427-430 for the
  `stands.values.map { it.point }.toSet()` pattern).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt`
  (multi-controller staging + SimTrace harness usage patterns;
  per-aircraft debug block at lines ~270-330).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/SimTraceQueries.kt`
  (per-aircraft trace queries — `responsibilityTransitions`,
  `missionStepTransitions`, `positionPointTransitions`, etc.; check
  if a `stateAtOrBefore(t)` query exists, add if not — see Approach
  §5).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixture.kt`
  (`LoadedFixture` carrier — exposes `world, worldIndex, controllers,
  initialEvents` only; reads for `flightPlans` go via the
  `Fixtures.LOWG_TWO_AIRCRAFT` object directly per pass-1 finding #8).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/TransmissionRecord.kt`
  (`firstControllerInstructionOf<T>(aircraftId)` helper).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/SeparationEngine.kt`
  (where `SeparationAssessment.wakeRule` lives post fn-8.1).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/WakeSeparation.kt`
  (`WakeCategory.{J, H, M, L}` — ICAO model, NM units).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/BeliefState.kt`
  (find `separationAssessments` field).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt`
  (`HighLevelGoal.CircuitTraining` shape — confirm `circuits = 2,
  fullStopOnLast = true` is the right call).
- `.flow/specs/fn-8-g1-two-aircraft-vfr-circuits-at-lowg.md`
  (epic spec — re-read for the partial-order pin shapes + decision
  context).

**Optional** (reference as needed):
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt`
  (`ARR-EXTEND` rule — confirm it issues `ExtendDownwind` instruction).

## Key context

- **The forced-conflict invariant (R8) is load-bearing.** Without it,
  the test could go green by accident if circuit timing shifts and B
  never has to extend. Always pin it.
- **Don't pin tick numbers.** Per practice-scout #5, partial-order
  pins (event X precedes event Y) are stable; tick-count pins drift.
- **The conflict-resolution pin (#3) uses a state-window observable**,
  not a record→state cursor on mission-step strings. Add the small
  `stateAtOrBefore(t)` SimTrace helper if absent. Pin against
  `PilotPhase` family or `positionPoint` membership — whichever is
  most stable.
- **Stand membership uses `stands.values.map { it.point }.toSet()`**,
  mirroring `LowgGoldenTest.kt:427-430`. **Not** `stands.keys`.
- **`LoadedFixture` doesn't expose `flightPlans` / `weatherByAerodrome`.**
  Read those via the `Fixtures.LOWG_TWO_AIRCRAFT` object directly.
- **B's mission-start offset is empirical** and is a `PilotDecisionTick`
  delay, not a per-plan filing time. Don't pin the offset value.
- **Wake-rule pin uses ICAO `WakeCategory.L`** — both aircraft are
  C172/Light per the **aircraft construction** in this task (R7
  set at `AircraftState(... type = AircraftType.C172 ...)` in
  fn-8.2 §2 — wake category is NOT on `FiledPlan` and is NOT in
  the fn-8.1 fixture).
- **Per-aircraft debug block** is the diagnostic pattern for multi-
  aircraft tests. Split journey by aircraftId; print per-aircraft
  state trace summaries.
- **Pre-existing flake** `:migration:jvmTest >
  LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport`
  — out of fn-8 scope; ignore.

## Acceptance

- [ ] `G1TwoAircraftCircuitsTest.kt` exists at
      `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt`.
- [ ] Single `@Test` method, fixture-driven, mirror-of-G0 structure.
- [ ] Loads `Fixtures.LOWG_TWO_AIRCRAFT` (from fn-8.1); reads
      `flightPlans` via the fixture object, not via `loaded`.
- [ ] Builds two aircraft with `HighLevelGoal.CircuitTraining(circuits = 2,
      fullStopOnLast = true)` each, B's mission-start offset (delayed
      `PilotDecisionTick`) authoring the conflict.
- [ ] Outcome pins per aircraft: both reach Parked, both at stand
      **points** (`stands.values.map { it.point }.toSet()`), both
      missions complete.
- [ ] All 5 causal partial-order pins from §Approach 5 are present
      and pass. The conflict-resolution pin (#3) uses
      the **3-event chain** `extendDownwind(B).time
      < touchdown(A).time < turnBase(B).time` (per epic R6).
      State-window helpers (`stateAtOrBefore` / `firstStateWhere`)
      are the mechanism for deriving touchdown / turnBase
      observables; the chain inequality is the assertion. Pin
      against
      stable observables (`PilotPhase` family or `positionPoint`).
- [ ] `SimTraceQueries.stateAtOrBefore(t)` (or equivalent) exists
      — added as a small extension if absent.
- [ ] Wake-rule pin (R7): `firstAssessment.wakeRule is
      WakeRule.IcaoNoAdditionalWakeMinimum && .leader ==
      WakeCategory.L && .follower == WakeCategory.L`. Filtered by
      `aircraft` / `other` field names (not `leader` / `follower`
      on the `SeparationAssessment` data class — those are inside
      the `WakeRule` case).
- [ ] Both `aircraftA` and `aircraftB` are constructed with
      `AircraftType.C172` (or whatever yields `WakeCategory.L`) so
      the wake-rule pin holds. Wake category lives on
      `AircraftState`, not on `FiledPlan`.
- [ ] Forced-conflict invariant pin (R8): `extendDownwind(B)` is
      observed during the run; test fails loud if not.
- [ ] Per-aircraft debug block in the diagnostic preamble (per-aircraft
      journey + responsibility / mission-step / position-point
      transitions).
- [ ] Time band: first-implementation uses 90-min generous ceiling;
      after first green, observed wall captured in `## Evidence` and
      the test tightened to ±15% band — both iterations done in
      fn-8.2 (per pass-1 plan-review finding #12).
- [ ] Test runs green.
- [ ] `LowgGoldenTest`, `G2CrossAerodromeVfrTest` stay green.
- [ ] Full test suite stays green; `./gradlew detekt` baseline unchanged.
- [ ] `AGENTS.md` `## Golden tests` section lists G1 alongside G0/G2
      with a one-line description.
- [ ] `LowgGoldenTest.kt` class docstring `@see` cross-reference to
      `G1TwoAircraftCircuitsTest`.
- [ ] `G2CrossAerodromeVfrTest.kt` class docstring mentions G1 as the
      multi-aircraft sibling.
- [ ] `Fixtures.kt` KDoc paragraph on `LOWG_TWO_AIRCRAFT` (chosen
      stand points + world-candidate authoring as source; no
      speculative AIP claim).

## Done summary

## Evidence
