# Controller Module: Handoff Document

Last updated: 2026-04-16 (evening — Bugs A/B/C all fixed; 24 controller tests passing)

## What exists

### New modules and files this session created

**Protocol** (3 new files, no existing files modified):
- `protocol/.../SimTime.kt` — `SimTime(millis: Long)`, `SimDuration(millis: Long)`. Integer-only factory methods. Coexists with `TickNumber` (not yet migrated).
- `protocol/.../RegulationModel.kt` — `ObligationId`, `ObligationStrength`, `ObligationSource`, `RegulationCategory`, `RegulationRef` (with derived `authority` display name from `document` ID), `Urgency`.
- `protocol/.../RegulationDatabase.kt` — 39 regulation entries covering SERA, ICAO Annex 2/11, Doc 4444/9432/9870, CAP 413.

**Core** (2 existing files modified):
- `core/.../world/WorldModel.kt` — added `holdingPointsByRunway` and `circuitLegsByPoint` to `WorldIndex`.
- `core/.../world/WorldIndexBuilders.kt` — added `deriveHoldingPointsByRunway()` and `deriveCircuitLegsByPoint()` builder functions.

**Controller** (new module, 15 source + 5 test files, ~1900 + ~660 lines):
- `controller/build.gradle.kts` — depends on `:protocol`, `:core`, arrow-core
- `settings.gradle.kts` — includes `:controller`

Source files:
- `Controller.kt` — main pipeline `controllerDecide(view, beliefs, world)`. FP style with threaded state.
- `ControllerTypes.kt` — `ControllerView`, `AircraftObservation`, `RunwayObservation`, `ReceivedMessage`, `ControllerOutput` (Instruct/Respond/InitiateHandoff), `DecisionTrace`, `ControllerDecisionResult`.
- `bdi/Commitment.kt` — `CommitmentKind` (compositional: role × traffic type), `Commitment` with stage tracking.
- `bdi/CommitmentReconciliation.kt` — `reconcileCommitments()`, service kind determination, stage constants.
- `bdi/Guard.kt` — `RuleGuard` sealed interface (entity-aware), `OperatorContext`, ~25 guard types with `failureMessage` for training traces.
- `bdi/Action.kt` — `RuleAction` returning `Either`, ~15 concrete actions including `ConditionalLineUpAction`, `VacateAction`, `TaxiToHoldingAction`, `TaxiToStandAction`. Also `SequenceInfo`, `TrafficInfo` companion types on `ProposedAction`. Utility functions: `resolveTrafficRef`, `findRoute` (BFS), `nearestPoint`, `deriveSequenceInfo`, `findRelevantTraffic`.
- `bdi/Procedure.kt` — `ProcedureSpec`, `AtcRule`, `ProcedureInterrupt`, `StageExpectation`.
- `bdi/ProcedureExecutor.kt` — `executeProcedure()`, `traceRuleFailures()` with human-readable messages.
- `observe/BeliefState.kt` — persistent state carried between cycles.
- `observe/Observe.kt` — `updateBeliefs()` with perfect-sensor model.
- `observe/Event.kt` — `ControllerEvent` sealed interface, `deriveEventsFromMessages()` dispatching on `PilotTransmission` subtypes.
- `assess/RunwayAssessment.kt` — `RunwayDutyState`, `updateRunwayDuty()` (release/enqueue/grant), `selectRunwayIntoWind()`.
- `procedure/TowerDeparture.kt` — AwaitReady → AwaitLineUpObserved → AwaitTakeoffObserved → Complete. Includes runway incursion, IMC hold, LUAW, conditional LUAW, takeoff, cancel takeoff, handoff.
- `procedure/TowerArrival.kt` — AwaitDownwind → AwaitApproach → AwaitLandedObserved → AwaitVacated → Complete. Includes go-around interrupts, controller-initiated go-around, extend downwind, turn base, clear to land/touch-and-go, continue approach, vacate, handoff.
- `procedure/GroundTaxi.kt` — departure (AwaitTaxiRequest → AwaitAtHolding → Complete) and arrival (TaxiToStand → AwaitParked → Complete).

Test files (14 tests total, all passing):
- `TestFixtures.kt` — minimal `WorldIndex`, `AviationWorld` with one aerodrome/runway/taxiway/circuit/stand, helper functions.
- `TowerDepartureTest.kt` — full departure sequence + regulation trace assertion.
- `TowerArrivalTest.kt` — arrival sequence, touch-and-go, go-around reset.
- `TowerSequencingTest.kt` — arrival priority, weather hold, human pilot without/with ready report.
- `GroundTaxiTest.kt` — taxi to holding, handoff, regulation traces, negative cases.

### Design documents
- `wiki/design-decisions/2026-04-15-controller-architecture.md` — comprehensive architecture (regulation-first, BDI, hybrid DES, compositional procedures, certifier gates).
- `docs/design/controller-issue-tracker.md` — 35-item tracker, 31 resolved, 4 future scope remaining (plus 3 bugs below).

---

## 3 open bugs (from Codex adversarial review) — all FIXED 2026-04-16

Fix order delivered: **C → B → A**. All three fixes landed with regression tests; full controller suite (24 tests) is green.

Summary of what shipped:
- **Bug C**: `findRoute` now returns `List<PointId>?` — actions surface a typed failure when no taxi route exists instead of emitting a degenerate "taxi to yourself". Regression test in `GroundTaxiTest` builds a disconnected graph and asserts no instruction is emitted.
- **Bug B**: `BeliefState.pendingReadbacks: Map<AircraftId, List<PendingReadback>>` with `MAX_READBACK_AGE = 30s`, matched against safety-critical atoms only. Outgoing instructions record pending entries post-arbitration; incoming readbacks match the most-recent pending (line-up before takeoff resolves correctly); unmatched readbacks produce silence; stale entries GC by age. Regression tests in `ReadbackValidationTest` cover the five cases.
- **Bug A**: Three coordinated changes —
  1. `RunwayAssessment.kt` release logic now uses a `holderReachedRunway` flag so an arrival on short final is never wrongly released (still airborne, hasn't touched yet). Once the holder has been observed on a runway point, off-runway triggers release (covers normal vacate, touch-and-go, late go-around without report).
  2. Priority preemption: a queued arrival on base/final preempts a departure still at the hold-short line; the departure is re-queued behind the arrival.
  3. `TowerArrival.kt` gains `ARR-TNG-AIRBORNE` rule at `AWAIT_LANDED_OBSERVED` (guard: `PilotGoalIs(TOUCH_AND_GO) + Airborne` → `STAGE_COMPLETE`) so a touch-and-go completes as soon as the aircraft lifts off; `ARR-VACATE` / `ARR-VACATE-HANDOFF` are guarded with `Not(PilotGoalIs(TOUCH_AND_GO))` so no bogus vacate is issued. `CommitmentReconciliation` now prunes completed commitments *before* service-kind determination so the next circuit's arrival commitment can form in the same cycle the T&G completes.

The original scoping notes and code sketches below are preserved for historical context.

---

### Bug A: Runway duty releases arrival while airborne [CRITICAL]

**File**: `controller/.../assess/RunwayAssessment.kt:48-58`

**Problem**: Release logic at line 53 does `!ac.onGround → release`. For departures this is correct (airborne = gone). For arrivals it's wrong — an arrival granted the runway while on final is still airborne until touchdown. Next cycle releases them, and a departure can be granted the same runway.

**Proposed fix**: Make release depend on `state.operation`:
```kotlin
val shouldRelease = when {
    ac == null -> true
    state.operation == RunwayOperation.DEPARTURE && !ac.onGround -> true
    state.operation == RunwayOperation.ARRIVAL &&
        ac.onGround && ac.entities.none { it is EntityRef.RunwayRef } -> true
    events.any { it is ControllerEvent.GoAroundDetected && it.aircraft == holder } -> true
    (state.operation == RunwayOperation.CROSSING || state.operation == RunwayOperation.BACKTRACK) &&
        ac.entities.none { it is EntityRef.RunwayRef } -> true
    else -> false
}
```

Arrivals stay reserved while airborne, release on ground+off-runway or go-around.

**Additional requirements agreed 2026-04-16:**

1. **Holder-commitment prune.** Also release if `holder !in commitments.keys`. Current code prunes the queue but not the holder; a terminated arrival commitment could otherwise leave the holder stuck. Defensive, cheap.

2. **Touch-and-go is NOT acceptable as deferred.** The original "commitment transitions handle it" was wrong. `TowerArrival.AWAIT_LANDED_OBSERVED` has only two rules: `ARR-VACATE` (OnRunway+OnGround → issue Vacate) and `ARR-VACATE-HANDOFF` (OnGround+!OnRunway → handoff). A T&G aircraft matches neither cleanly — it gets told to vacate during roll-out, then becomes airborne (neither rule matches), and is stuck forever. Fix requires two changes:
   - **Runway duty side**: on `ClearedTouchAndGo` action issuance, flip `RunwayDutyState.operation` from ARRIVAL to DEPARTURE. Then airborne → release (DEPARTURE rules), and the aircraft is re-enqueued as ARRIVAL when it's back on base/final.
   - **Procedure side**: add a T&G-specific stage path — either a new `AWAIT_TNG_ROLL → AWAIT_AIRBORNE → AWAIT_DOWNWIND` chain inside TowerArrival, or transition the commitment back to `AWAIT_DOWNWIND` directly on airborne detection. Either way, no Vacate instruction emitted for T&G.

3. **Regression tests**: (a) arrival on final with queued departure — departure must NOT get runway access while arrival is still airborne; (b) late go-around from airborne arrival → runway released; (c) touch-and-go executed to airborne → runway released, commitment back in AWAIT_DOWNWIND, no spurious Vacate issued.

### Bug B: Readback confirmed without validation [HIGH]

**Files**: `controller/.../Controller.kt:223-234` and `controller/.../observe/Event.kt:30`

**Problem**: `ReadbackReceived` event discards the readback content (carries only `AircraftId`). The controller confirms every readback as correct without checking what was read back or whether any clearance is pending. Wrong readbacks get positively reinforced.

**Scope agreed 2026-04-16** (informed by transmission-reception architecture decision — see `wiki/design-decisions/2026-04-16-transmission-reception-architecture.md`):

This bug sits at the boundary where the future LLM interpreter will one day live. Rather than patch it narrowly and re-do the work later, we do the controller-side piece properly now and leave clean hooks for the interpretation layer.

**Do now:**

1. **`BeliefState.pendingReadbacks: Map<AircraftId, List<PendingReadback>>`** where:
   ```kotlin
   data class PendingReadback(
       val instruction: AtcInstruction,
       val issuedAt: SimTime,
   )
   ```
   Time is load-bearing for timeouts, ordering, GC, and future parser context snapshots.

2. **Pipeline step after arbitration**: record every outgoing `Instruct` into `pendingReadbacks` keyed by target aircraft.

3. **Preserve readback content in the event**:
   ```kotlin
   data class ReadbackReceived(val aircraft: AircraftId, val readback: Readback) : ControllerEvent
   ```

4. **Atom-level validator**. Walk the `Readback.elements` and match safety-critical atoms against the most recent pending instruction for that aircraft:
   - `ClearedForTakeoff` / `ClearedToLand` / `ClearedTouchAndGo` / `LineUp` / `CrossRunway` / `Backtrack` → runway match required via the corresponding `*Readback` atom.
   - `ContactFrequency` → `FrequencyReadback` match required.
   - Level / heading / squawk instructions → corresponding atom match required.
   - Other instructions → any readback accepted (relaxed for v1).
   
   On full match: emit `ReadBackCorrect`, pop the entry. On mismatch or no pending: silence (silence = "say again" in ATC RT convention).

5. **GC**: drop pending readbacks older than a `maxReadbackAge` constant. Suggested default: 30s sim-time (long enough for realistic RT lag + LLM latency when it arrives; short enough that stale entries don't accumulate).

**Explicitly NOT doing now (deferred to the interpretation layer when it is built):**

- Compound readbacks covering multiple instructions at once.
- Mid-transmission corrections ("land two seven, correction, zero nine").
- "Roger" substitution for required readbacks.
- Partial / out-of-order element handling.
- Speaker profile / style adaptation.
- Confidence-scored readbacks.
- Timeout nudge transmissions ("[callsign], readback?").

These all belong in the future LLM parser, which runs before the controller sees the event. See the design doc for the full boundary.

**Tests**: (a) correct readback clears pending and emits `ReadBackCorrect`; (b) wrong-runway readback emits nothing and leaves pending in place; (c) readback with no pending emits nothing; (d) pending entry GC'd after `maxReadbackAge`; (e) multiple in-flight clearances — readback matches the most recent matching one.

### Bug C: Taxi routing failure silent [MEDIUM]

**File**: `controller/.../bdi/Action.kt:213-245`

**Problem**: `findRoute()` returns `emptyList()` for both "from == to" (legitimate direct route) and BFS failure (no path). Callers pass the result into `TaxiTo` regardless, producing bogus movement instructions on disconnected graphs.

**Proposed fix**: Return `List<PointId>?` — null for no path, empty for direct:
```kotlin
private fun findRoute(from: PointId, to: PointId, worldIndex: WorldIndex): List<PointId>? {
    if (from == to) return emptyList()
    // ... BFS ...
    return null // no path found
}
```
Callers: `val via = findRoute(...) ?: return ActionResolutionFailure("No taxi route to $destination").left()`

Nullable rather than `Either<NoRouteError, List<PointId>>` because it's a private helper; callers already sit in `Either` and a unit-like `Left` type adds no information.

**Tests**: (a) valid route returns path; (b) from==to returns empty via, caller emits no-op TaxiTo to current position (or separate guard prevents this — see note below); (c) disconnected graph → `ActionResolutionFailure`.

**Separate concern noted but not in scope for this fix**: when `ac.position` is already a holding point or stand, callers issue `TaxiTo(destination=current, via=[])` — a taxi to self. Not a routing bug, but should be suppressed by a guard upstream. Captured in the issue tracker as future scope.

---

## What remains (project plan)

### Immediate next (bugs above)
Fix bugs A, B, C with tests.

### Phase 4: Simulation engine
Build the DES engine that wires controller + future pilot into a running simulation:
- `(SimState, SimEvent) -> Pair<SimState, List<SimEvent>>` pure fold
- Physics tick (advance positions along routes), controller decision events, aircraft spawn
- Multi-controller orchestration (ground + tower handoff)
- Closes the loop: spawn at stand → taxi → depart → circuit → land

Design is documented in `wiki/design-decisions/2026-04-15-controller-architecture.md` section 3 and the plan file. Key: the controller function `controllerDecide(view, beliefs, world)` is timing-model-agnostic. The simulation wraps it.

### Phase 5: Approach procedures
- Approach arrival (vectors/directs, approach clearance, handoff to tower)
- Approach transit (contact, release)
- Area transit (handoff to approach)

### Phase 6: Formal integration
- Certifier view extraction from `AviationWorld`
- `ActionCertifier` implementation bridging to Lean kernels (4 kernels: runway, surface, air-path, separation)
- Runtime validation for concerns not yet covered by proofs

### Future scope (tier 6 from issue tracker)
27. IFR ground flow (startup, clearance delivery, pushback)
28. Readback verification — partially addressed by Bug B fix
29. Runway crossing authorisation during taxi
30. Special VFR provisions
31. Departure instructions after takeoff
32. Base turn / report base instruction
33. Belief-delta event derivation (state-change detection without pilot report)
34. Reactive safety layer (proactive conflict detection)
35. Pin ICAO Doc 4444 edition number

---

## Key architecture decisions

1. **Regulation-first**: every `AtcRule` carries `regulations: List<RegulationRef>`. `DecisionTrace` propagates them. Training product can always answer "why did the controller do X?"

2. **Entity-aware guards**: position is `PointId + Set<EntityRef>`, not `AircraftPhase` enum. `WorldIndex` provides `entitiesByPoint`, `holdingPointsByRunway`, `circuitLegsByPoint`.

3. **Controller is a pure function**: `(ControllerView, BeliefState, AviationWorld) -> ControllerDecisionResult`. Caller threads beliefs. No hidden state.

4. **ControllerView lives in controller module**, not protocol. The controller defines its own boundary. No `Any` types.

5. **Pipeline is FP**: threaded state via extension functions and `let` chains. `advanceCommittedStages` uses `fold`. Enrichment (wind, companions) is a separate pipeline step after arbitration.

6. **Arbitration**: one action per urgency level per cycle. SAFETY unlimited, one TIME_SENSITIVE, one PROGRESSION, one INFORMATIONAL.

7. **Companion outputs** (sequence info, traffic info) are optional fields on `ProposedAction`, emitted by the pipeline alongside the primary instruction. Pragmatic — generalise to a pipeline enrichment step if we hit 5+ companion types.

8. **`AviationWorld` on `OperatorContext`**, not `ControllerView`. View is observations; context is world knowledge for resolution.

9. **TWR1 at `../twr`** is the predecessor project with 1100 tests, working controller/pilot/harness. Reference for porting patterns. Key files documented in memory at `reference_twr1_codebase.md`.

## How to run

```bash
./gradlew :controller:jvmTest    # 14 tests, all should pass
./gradlew :core:jvmTest          # existing core tests (1 pre-existing FM parity failure unrelated to controller work)
./gradlew :protocol:compileKotlinJvm :core:compileKotlinJvm :controller:compileKotlinJvm  # compile all three
```

## User preferences (from memory)

- Scala cats/Haskell background. Use Arrow, pure functions, totality, immutability.
- Anti-corruption layers, deep modules, ports and adapters.
- Test standards: prefer system/integration tests over unit tests. Types over unit tests. Test hard orchestration code.
- ATC review agents must get clean contexts (no prior-fix priming) to avoid confirmation bias.
- Build wide but solid core first. Don't paint into corners by not tackling enough up front.
- Don't build things not needed. Use Lean certifier work as it should be as reliable as it gets.
