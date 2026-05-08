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
- `AGENTS.md` (`## Golden tests` section: add G1 bullet alongside G0/G2)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LowgGoldenTest.kt`
  (class docstring `@see` cross-reference to G1)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt`
  (class docstring add G1 sibling note)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt`
  (KDoc paragraph for `LOWG_TWO_AIRCRAFT`)

## Approach

### 1. Test structure (mirror of LowgGoldenTest)

Follow `LowgGoldenTest.kt:39-491` line-for-line. Anchors:

- **Class KDoc** mirrors G0 + G2 prose. Names sibling tests (G0 single-
  aircraft, G2 cross-aerodrome) and what G1 distinctively pins
  (single-runway sequencing, two-aircraft conflict resolution, wake-
  rule evaluation).
- **Single `@Test` method** named `two AI aircraft fly two circuits each at LOWG`
  (or similar). The run is the test; assertions read from records +
  trace.
- **Sections**: load fixture → declare aircraft + missions → SimState.initial
  → seed initialEvents → runUntilWithStateTrace → outcome pins → causal
  pins → wake-rule pin → forced-conflict invariant → time band →
  diagnostic preamble (per-aircraft journey + per-aircraft trace queries).

### 2. Fixture + state

```
val loaded = Fixtures.LOWG_TWO_AIRCRAFT.load().getOrElse { fail("...") }
val aircraftAId = AircraftId("OE-ABC")
val aircraftBId = AircraftId("OE-DEF")
// (or whatever pair the LOWG_TWO_AIRCRAFT fixture seeds; document choice)
val missionA = createMission(
    goal = HighLevelGoal.CircuitTraining(circuits = 2, fullStopOnLast = true),
    startPhase = PilotPhase.AtStand,
    time = SimTime.ZERO,
    filedPlan = loaded.flightPlans[aircraftAId],
)
val missionB = createMission(... same shape with B's filedPlan ...)
// etc.
val initial = SimState.initial(
    seed = 42L,
    world = loaded.world,
    worldIndex = loaded.worldIndex,
    aircraft = listOf(aircraftA, aircraftB),
    controllers = loaded.controllers.values.toList(),
    weatherByAerodrome = loaded.weatherByAerodrome,
)
```

### 3. Conflict authoring (deliberate)

Author B's start time / filed plan offset to deliberately put B on
downwind when A is on base/final, forcing `ARR-EXTEND` for B. This is
the load-bearing scenario the test exists to exercise.

**Mechanism**: filedPlan offset is the simplest. Aircraft A files at
`SimTime.ZERO`; B files at e.g. `SimTime.ofMinutes(2)` so B is ~2 min
behind A's mission progression. The exact offset is empirical — pin the
**causal invariant** ("`extendDownwind(B)` observed during the run"),
not the offset itself. If the offset shifts due to a kinematic
refactor, the invariant catches it loud.

Initial events:

```
val initialEvents = loaded.initialEvents + listOf(
    SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = lowgAtis),
    SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftAId),
    SimEvent.PilotDecisionTick(time = SimTime.ofMinutes(2), aircraftId = aircraftBId),
    SimEvent.PhysicsTick(time = now),
    SimEvent.ControllerCycle(time = now, controllerId = lowgGround.id),
    SimEvent.ControllerCycle(time = now, controllerId = lowgTower.id),
)
```

### 4. Outcome pins (per aircraft)

```
val finalA = finalState.aircraft.getValue(aircraftAId)
val finalB = finalState.aircraft.getValue(aircraftBId)

check(finalA.pilotMission?.isComplete == true) { "A mission incomplete\n$journey" }
check(finalB.pilotMission?.isComplete == true) { "B mission incomplete\n$journey" }
check(finalA.altitudeM == 0.0) { ... }
check(finalB.altitudeM == 0.0) { ... }
check(finalA.phase == PilotPhase.Parked || finalA.phase == PilotPhase.AtStand) { ... }
check(finalB.phase == PilotPhase.Parked || finalB.phase == PilotPhase.AtStand) { ... }
// each aircraft at a known LOWG stand point
check(finalA.positionPoint in loaded.world.aerodromes[lowg].stands.keys) { ... }
check(finalB.positionPoint in loaded.world.aerodromes[lowg].stands.keys) { ... }
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
3. **Conflict resolution (B extends downwind while A is on final)**:
   - Find `ExtendDownwind(aircraftBId)` instruction.
   - At that moment, A's mission step is `FLY_FINAL` (or `REPORT_FINAL`,
     or `LAND` — pin the *family* not the specific step).
   - Use `trace.firstWhere { st -> ... ExtendDownwind seen ... }` then
     check `st.aircraft[aircraftAId].pilotMission.currentTask.step in
     finalishSteps`.
4. **Final-circuit landing order**: `cleared-land(A_final_circuit).time
   < cleared-land(B_final_circuit).time` (assumes A lands before B
   given offset).
5. **Both reach stands**: terminal positionPoint in stands set
   (covered by Outcome pins above; this is the "closure" partial-order).

### 6. Wake-rule pin (R7)

Per practice-scout #4, the wake-rule-was-evaluated shape:

```
val firstAssessment = beliefs.separationAssessments
    .filter { it.leader == aircraftAId && it.follower == aircraftBId }
    .first()  // or via SimTrace query if more idiomatic
check(firstAssessment.rule == WakeRule.RecatFFNoMin) {
    "Wake assessment for B-following-A should be CAT F → CAT F (no min); got " +
        "${firstAssessment.rule}.\n$journey"
}
```

(Implementer: confirm the exact path to `separationAssessments` —
it's on `BeliefState` per repo-scout finding `SeparationConcernAbove`
guard. May be queryable via SimTrace.)

### 7. Forced-conflict invariant (R8)

The bare-minimum pin that detects "test got dull":

```
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

### 8. Time band

Capture observed wall on first green; pin a band ±15% per
practice-scout finding #5. Initially use a generous 90-min ceiling
(matches G2). Tighten in evidence comment.

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
  `LOWG_TWO_AIRCRAFT` (chosen stand IDs + AIP source).

## Investigation targets

**Required** (read before coding):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LowgGoldenTest.kt`
  (491 lines — the canonical mirror; read top-to-bottom before writing
  G1).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt`
  (multi-controller staging + SimTrace harness usage patterns;
  per-aircraft debug block at lines ~270-330).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/SimTraceQueries.kt`
  (per-aircraft trace queries — `responsibilityTransitions`,
  `missionStepTransitions`, `positionPointTransitions`, etc.).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/TransmissionRecord.kt`
  (`firstControllerInstructionOf<T>(aircraftId)` helper).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/SeparationEngine.kt`
  (where `SeparationAssessment` lives post fn-8.1).
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
- **Don't pin tick numbers.** Per practice-scout #5, partial-order pins
  (event X precedes event Y) are stable; tick-count pins drift.
- **Per-aircraft debug block** is the diagnostic pattern for multi-
  aircraft tests. Split journey by aircraftId; print per-aircraft state
  trace summaries. Without this, debug becomes "which one of two is
  misbehaving?"
- **Pre-existing flake** `:migration:jvmTest >
  LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport`
  — out of fn-8 scope; ignore.
- **B's start offset is empirical.** Don't pin it. The forced-conflict
  invariant catches the case where the offset stops producing a
  conflict.
- **Wake-rule pin (R7)** assumes both aircraft are CAT F (Light /
  C172-equivalent). If `Fixtures.LOWG_TWO_AIRCRAFT` files different
  aircraft types in the flight plans, the rule cell may not be
  `RecatFFNoMin`. Document the type choice in the fixture KDoc.

## Acceptance

- [ ] `G1TwoAircraftCircuitsTest.kt` exists at
      `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt`.
- [ ] Single `@Test` method, fixture-driven, mirror-of-G0 structure.
- [ ] Loads `Fixtures.LOWG_TWO_AIRCRAFT` (from fn-8.1).
- [ ] Builds two aircraft with `HighLevelGoal.CircuitTraining(circuits = 2,
      fullStopOnLast = true)` each, B offset to force the conflict.
- [ ] Outcome pins per aircraft: both reach Parked, both at stand,
      both missions complete.
- [ ] All 5 causal partial-order pins from §Approach 5 are present
      and pass.
- [ ] Wake-rule pin (R7): `firstAssessment.rule == WakeRule.RecatFFNoMin`.
- [ ] Forced-conflict invariant pin (R8): `extendDownwind(B)` is
      observed during the run; test fails loud if not.
- [ ] Per-aircraft debug block in the diagnostic preamble (per-aircraft
      journey + responsibility / mission-step / position-point
      transitions).
- [ ] Time band: observed wall captured in `## Evidence`; pin within
      ±15% (or generous initial 90-min ceiling).
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
      stand IDs + AIP citation).

## Done summary

## Evidence
