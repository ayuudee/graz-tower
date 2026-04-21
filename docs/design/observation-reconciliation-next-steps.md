# Observation Reconciliation — Architectural Next Steps

Three design notes for work that extends the observation-reconciled commitment
machine. Each section is self-contained and can be executed independently.

## 1. Exhaustive Condition-Space Permutation Testing

### Problem

The reconciliation functions are total over Stage × Position (compiler-enforced,
exhaustive property tests covering 108 cells). But the action selection in
`act()` — which lives in ProcedureSpec/AtcRule — is tested by targeted scenario
tests, not by exhaustive enumeration of the condition space.

For departure `TowerDepartureStage.AwaitLineUpObserved`, the relevant conditions
include: OnRunway, OnGround, WeatherPermitsVfr, RunwayAccessGranted,
RunwayPhysicallyClear — 5 booleans = 32 combinations. Across all stages and all
procedures, the condition space is larger but still tractable.

### Approach

For each procedure stage, enumerate the relevant guard conditions and test every
combination against the rule list. The test checks two things:

1. **Exactly one rule fires or no rule fires** — no ambiguous overlap where two
   rules' guards both pass (rule priority resolves this, but the test documents
   which rule wins for each combination).

2. **Safety priority**: if a SAFETY-urgency rule's guard is met, it always wins
   regardless of other conditions.

### Implementation

Use a data class per stage that carries the relevant boolean conditions:

```kotlin
data class AwaitReadyConditions(
    val pilotReady: Boolean,
    val contacted: Boolean,
    val weatherVfr: Boolean,
    val runwayAccess: Boolean,
    val runwayClear: Boolean,
    val trafficOnFinal: Boolean,
    val onRunway: Boolean,  // incursion case
)
```

Enumerate all 2^N combinations. For each, build the corresponding
AircraftObservation + OperatorContext, call `executeProcedure`, and record
which rule (if any) fired. Assert:

- SAFETY rules fire when their guard conditions are met, regardless of other flags.
- No two non-SAFETY rules fire for the same condition set (rule ordering is deterministic,
  but the test documents the priority resolution).
- The union of all firing conditions covers the full space (no blind spots where the
  controller should act but doesn't).

### Key files

- `controller/src/commonMain/.../procedure/TowerDeparture.kt` — departure rules
- `controller/src/commonMain/.../procedure/TowerArrival.kt` — arrival rules
- `controller/src/commonMain/.../bdi/ProcedureExecutor.kt` — `executeProcedure`
- `controller/src/commonTest/.../TestFixtures.kt` — `aircraftAt`, `towerView`, `testControllerDecide`

### Scale

Departure has 5 stages. Per-stage condition spaces range from ~5 booleans (32
combinations) to ~8 booleans (256). Total across all departure stages: ~500-800
combinations. Arrival is similar. Ground is smaller. All run in milliseconds.

---

## 2. DFA-as-Specification Conformance

### Problem

The reconciliation functions define "what stage should the commitment be in given
the observed position." The ProcedureSpec rules define "what action to take at
each stage." These are two representations of the same procedure — the
reconciliation is the specification, the rules are the implementation.

Currently, conformance between them is tested end-to-end (the sim integration
tests run the full pipeline). But there's no direct test that says: "for every
stage the reconciliation function can produce, there exists at least one rule
in the ProcedureSpec that handles it."

### Approach

A conformance test per procedure that verifies:

1. **Stage coverage**: every non-Complete stage in the reconciliation function's
   output range has at least one rule in the ProcedureSpec's stageRules map.

2. **Reachability**: every stage in the ProcedureSpec's stageRules map is
   reachable from the initial stage via some sequence of reconciliation
   transitions. No dead stages.

3. **Transition coherence**: if the reconciliation function can transition from
   stage A to stage B (via some position observation), and the ProcedureSpec has
   a rule at stage A with `nextStage = B`, then the reconciliation and the rule
   agree on what B means.

### Implementation

```kotlin
@Test
fun `every reconciliation output stage has procedure rules`() {
    val spec = towerDepartureProcedure()
    val allOutputStages = allStages.flatMap { stage ->
        allPositions.map { pos -> reconcileDepartureStage(stage, pos).stage }
    }.toSet()
    for (stage in allOutputStages) {
        if (stage.isComplete) continue
        assertTrue(stage in spec.stageRules,
            "Reconciliation can produce $stage but no rules handle it")
    }
}
```

For reachability, build the transition graph from the reconciliation function
(nodes = stages, edges = position observations that trigger a transition) and
verify every stage is reachable from the initial stage.

### Key files

- `controller/src/commonMain/.../procedure/DepartureReconciliation.kt`
- `controller/src/commonMain/.../procedure/ArrivalReconciliation.kt`
- `controller/src/commonMain/.../procedure/GroundReconciliation.kt`
- `controller/src/commonMain/.../procedure/TowerDeparture.kt` (ProcedureSpec)
- `controller/src/commonMain/.../procedure/TowerArrival.kt`
- `controller/src/commonMain/.../procedure/GroundTaxi.kt`

### Edge case

The reconciliation can produce stages that the ProcedureSpec intentionally has
no rules for (e.g., TakeoffClearanceIssued with only safety/re-issue rules,
no "normal progression" rule). This is valid — the commitment waits at that
stage for readback or observation. The conformance test should accept "at least
one rule OR the stage is a waiting stage with a re-issue mechanism."

---

## 3. Lean Verification of Reconciliation Properties

### Problem

The reconciliation functions have structural properties that are currently
checked by exhaustive Kotlin tests:

- **Monotonicity**: `reconcile(stage, pos).stage.ordinal >= stage.ordinal`
  (except named go-around regressions)
- **Terminality**: `reconcile(Complete, pos) == Complete` for all positions
- **Position-phase correspondence**: certain positions always advance to at
  least a certain stage (airborne → at least AwaitTakeoffObserved)

These tests provide "poor man's FV" but are runtime checks. The properties
could be proven in Lean, making them axiomatic for the rest of the system.

### Approach

Model the reconciliation function in Lean 4 as a pure function on finite
enumerated types (stages and positions). Prove the properties as theorems.

```lean
inductive DepartureStage where
  | awaitReady | awaitLineUp | takeoffIssued | awaitTakeoff | complete
  deriving DecidableEq, Repr

inductive DeparturePosition where
  | atHolding | onRunway | onRunwayRolling | airborneOverRunway | onClimbout | elsewhere
  deriving DecidableEq, Repr

def reconcile (s : DepartureStage) (p : DeparturePosition) : DepartureStage :=
  match s, p with
  | .awaitReady, .atHolding => .awaitReady
  | .awaitReady, .onRunway => .awaitLineUp
  -- ... (total match, Lean forces all cases)

theorem monotone (s : DepartureStage) (p : DeparturePosition) :
    (reconcile s p).ordinal >= s.ordinal := by
  cases s <;> cases p <;> simp [reconcile, DepartureStage.ordinal]
```

For arrivals, the go-around regression requires a modified monotonicity
theorem with named exceptions:

```lean
theorem monotone_except_goaround (s : TowerArrivalStage) (p : ArrivalPosition) :
    (reconcile s p).ordinal >= s.ordinal ∨ isGoAroundRegression s p := by
  ...
```

### Integration with existing Lean work

The project has a `research/fm/lean/CertifiedAtc.lean` file with existing
formal work. The reconciliation proofs would live alongside it. The connection
to the Kotlin implementation is by inspection — the Lean model mirrors the
Kotlin reconciliation functions, and the Kotlin exhaustive tests serve as
a conformance check between the two representations.

Future: a code generator that produces the Kotlin reconciliation function from
the Lean specification (or vice versa) would eliminate the conformance gap.

### Key files

- `research/fm/lean/CertifiedAtc.lean` — existing Lean FM work
- `research/fm/lean/CertifiedAtc/GreenfieldObservationHelpers.lean` — observation helpers
- `controller/src/commonMain/.../procedure/DepartureReconciliation.kt` — Kotlin source of truth
- `controller/src/commonMain/.../procedure/ArrivalReconciliation.kt`

### Properties to prove

1. Monotonicity (departure): ∀ s p, reconcile(s, p).ordinal ≥ s.ordinal
2. Monotonicity with exceptions (arrival): ∀ s p, reconcile(s, p).ordinal ≥ s.ordinal ∨ goAround(s, p)
3. Terminality: ∀ p, reconcile(Complete, p) = Complete
4. Airborne correspondence: ∀ s p, isAirborne(p) → reconcile(s, p).ordinal ≥ awaitTakeoffObserved.ordinal (departure)
5. OnRunway correspondence: ∀ s p, isOnRunway(p) → reconcile(s, p).ordinal ≥ awaitLandedObserved.ordinal (arrival, for non-Complete s)

These are all decidable over finite enumerations — `decide` tactic or `cases` + `simp` should close them.
