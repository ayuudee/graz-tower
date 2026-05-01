# IFR Pilot Route Planner — Implementation Plan

Source design: `wiki/design-decisions/2026-04-21-ifr-pilot-route-planner.md`

## Phase ordering and dependencies

```
IFR-0  Types + amendFpl + test infrastructure
  │
  ├──► IFR-1  Waypoint constraints on PilotRoute.Airborne
  │      │
  │      ├──► IFR-2  SID departure
  │      │      │
  │      │      └──► IFR-3  STAR + approach (needs minimal amendFpl from IFR-0)
  │      │             │
  │      │             └──► IFR-4  Missed approach
  │      │
  │      └──► IFR-5  Route overrides (vectors + hold) — independent of IFR-2/3/4
  │
  └──► IFR-6  Full FPL amendments in processInstruction (needs IFR-0 amendFpl)
```

IFR-0 is the foundation: types, test fixtures, and the `amendFpl` function. Everything else builds on it. IFR-5 (route overrides) is independent of the departure/approach chain and can be developed in parallel.

---

## IFR-0: Types, amendFpl, and test infrastructure

The FP review's strongest recommendation: define `amendFpl` before implementing route building, because writing its exhaustive `when` will shape the `FlightPlan` and `ClearanceState` types.

### 0a. FlightPlan + ClearanceState in protocol module

```kotlin
// protocol/src/commonMain/kotlin/.../FlightPlan.kt

data class FlightPlan(
    val departureAerodrome: AerodromeId,
    val arrivalAerodrome: AerodromeId,
    val alternateAerodrome: AerodromeId? = null,
    val requestedLevel: Level,
    val enRouteWaypoints: List<Waypoint>,
    val clearance: ClearanceState = ClearanceState.Uncleaned,
)

sealed interface ClearanceState {
    data object Uncleaned : ClearanceState

    data class EnRouteClearance(
        val clearanceLimit: FixId,
        val departureRunway: RunwayId,
        val sid: SidId? = null,
        val star: StarId? = null,
        val clearedLevel: Level? = null,
    ) : ClearanceState

    data class ApproachClearance(
        val clearanceLimit: FixId,
        val departureRunway: RunwayId,
        val sid: SidId? = null,
        val star: StarId? = null,
        val clearedLevel: Level? = null,
        val approach: ApproachId,
        val arrivalRunway: RunwayId,
    ) : ClearanceState
}
```

Key decisions from reviews:
- `ClearanceState` sealed hierarchy makes illegal states unrepresentable (FP review)
- `alternateAerodrome` for hold-vs-divert decisions (ATC review)
- `requestedLevel` vs `clearedLevel` separated (ATC review)
- `enRouteWaypoints` on the FPL itself (filed route), not on clearance state (clearance may amend but the filed route is the baseline)

### 0b. NavigationMode.Instrument simplification

Replace the current inline fields with `fpl: FlightPlan`:

```kotlin
data class Instrument(val fpl: FlightPlan) : NavigationMode
```

Update the truth table test constructor. All cells still return Left.

### 0c. amendFpl — total pure function

```kotlin
fun amendFpl(
    fpl: FlightPlan,
    instruction: AtcInstruction,
): Either<AmendmentError, FlightPlan>
```

Exhaustive `when` over `AtcInstruction` sealed hierarchy. Most instructions are no-ops (return `fpl.right()`). The interesting cases:

| Instruction | Amendment |
|-------------|-----------|
| `ClearedTo(clearanceLimit, route?)` | Advance `ClearanceState` to `EnRouteClearance`. If `route` is `ViaSid`, set `sid`. If `ViaStar`, set `star`. Set `clearanceLimit`. |
| `ClearedApproach(type, runway)` | Advance `ClearanceState` to `ApproachClearance`. Set `approach` and `arrivalRunway`. |
| `ProceedDirect(fix)` | Truncate `enRouteWaypoints` — drop all waypoints before `fix`, keep `fix` onward. |
| `ClimbTo(level)` / `DescendTo(level)` | Update `clearedLevel` on clearance state. |
| Everything else | `fpl.right()` — no FPL effect. |

```kotlin
sealed interface AmendmentError {
    data class NotCleared(val instruction: AtcInstruction) : AmendmentError
    data class FixNotOnRoute(val fix: FixId) : AmendmentError
    data class InvalidTransition(val from: ClearanceState, val instruction: AtcInstruction) : AmendmentError
}
```

### 0d. MissionStep additions

Add 5 new entries to `MissionStep` enum:

```kotlin
enum class MissionStep {
    // ... existing ...
    // IFR airborne
    FLY_SID, FLY_EN_ROUTE, FLY_STAR, FLY_APPROACH, FLY_MISSED_APPROACH,
}
```

Add a test that every `MissionStep` value appears in at least one task tree construction (guards `skipCompletedSteps`).

### 0e. IFR mission decomposition in planMission

```kotlin
fun planMission(goal: HighLevelGoal, humanPiloted: Boolean = true, ifr: Boolean = false): CompoundTask = when (goal) {
    is HighLevelGoal.Departure -> if (!ifr) /* existing */ else CompoundTask(TaskName.Depart, listOf(
        groundDepartureTask(humanPiloted),
        PrimitiveTask(MissionStep.FLY_SID, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.FLY_EN_ROUTE, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
    ))
    is HighLevelGoal.Arrival -> if (!ifr) /* existing */ else CompoundTask(TaskName.Arrive, listOf(
        PrimitiveTask(MissionStep.FLY_STAR, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.FLY_APPROACH, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.LAND, CompletionMode.PHYSICAL),
        groundArrivalTask(),
    ))
    // CircuitTraining is always VFR
    // Transit uses the same Depart decomposition
}
```

Note: `ifr` is derived from `NavigationMode` at the call site, not stored on `HighLevelGoal`. The goal says WHAT (depart to X), the mode says HOW (IFR/VFR).

### 0f. Test infrastructure

**IFR test fixture — minimal world:**

```kotlin
// Points
val depEnd = PointId("DEP-END")
val sidWp1 = PointId("SID-WP1")
val sidExit = PointId("SID-EXIT")
val enRouteWp = PointId("ENRTE-1")
val starEntry = PointId("STAR-ENTRY")
val iaf = PointId("IAF")
val faf = PointId("FAF")
val mapFix = PointId("MAP-FIX")

// Procedures
val sid = Sid(id = SidId("SID-09"), name = "DEP 1A", runway = runwayId,
    waypoints = listOf(
        Waypoint(sidWp1, altitudeConstraint = AltitudeConstraint.AtOrAbove(Level.AltitudeFeet.unsafe(2000))),
        Waypoint(sidExit, altitudeConstraint = AltitudeConstraint.AtOrAbove(Level.AltitudeFeet.unsafe(4000))),
    ))

val star = Star(id = StarId("STAR-09"), name = "ARR 1B",
    waypoints = listOf(
        Waypoint(starEntry, altitudeConstraint = AltitudeConstraint.AtOrBelow(Level.AltitudeFeet.unsafe(6000))),
        Waypoint(iaf, altitudeConstraint = AltitudeConstraint.AtOrBelow(Level.AltitudeFeet.unsafe(3000))),
    ))

val ilsApproach = InstrumentApproach(
    id = ApproachId("ILS-09"), name = "ILS RWY 09", type = ApproachType.ILS,
    runway = runwayId,
    waypoints = listOf(Waypoint(iaf), Waypoint(faf), Waypoint(threshold)),
    minimumAltitude = ApproachMinimum(MinimumType.DECISION_ALTITUDE, Level.AltitudeFeet.unsafe(200)),
    missedApproach = MissedApproachProcedure(
        waypoints = listOf(Waypoint(mapFix), Waypoint(sidWp1)),  // multi-waypoint per test review
        holdAt = holdId,
    ))
```

**IFR test fixture — richer world (for selection logic):**

Second fixture adds: two SIDs per runway (SID-09A, SID-09B), SID with a transition, STAR with a transition. Tests the lookup/selection logic.

### 0f. Tests for IFR-0

| Test | What it verifies |
|------|------------------|
| `FlightPlan construction` | Filed FPL with Uncleaned state has correct defaults |
| `ClearanceState.Uncleaned → EnRouteClearance` | `amendFpl` with `ClearedTo` advances state |
| `EnRouteClearance → ApproachClearance` | `amendFpl` with `ClearedApproach` advances state |
| `amendFpl with ProceedDirect` | Truncates `enRouteWaypoints`, fix must be on route |
| `amendFpl with unrelated instruction` | Returns fpl unchanged |
| `amendFpl on Uncleaned with ClearedApproach` | Returns `InvalidTransition` (can't go to approach without en-route clearance) |
| `MissionStep coverage` | Every MissionStep value appears in at least one task tree returned by planMission (guards skipCompletedSteps) |
| `IFR planMission Departure` | Produces GroundDeparture → FLY_SID → FLY_EN_ROUTE → SHUTDOWN |
| `IFR planMission Arrival` | Produces FLY_STAR → FLY_APPROACH → LAND → GroundArrival |
| `Truth table unchanged` | NavigationMode.Instrument constructor change doesn't break existing truth table |

---

## IFR-1: Waypoint constraints on PilotRoute.Airborne

### 1a. Add `waypointConstraints` field

```kotlin
data class Airborne(
    val waypoints: NonEmptyList<PointId>,
    val targetAltitudeM: Double,
    val arrivalPhase: PilotPhase,
    val waypointConstraints: Map<PointId, WaypointConstraints> = emptyMap(),
)

data class WaypointConstraints(
    val altitude: AltitudeConstraint? = null,
    val speed: SpeedConstraint? = null,
)
```

Default empty map — VFR routes unchanged. Add construction-time check in VFR route builders: `check(waypointConstraints.isEmpty())` — or simply never pass constraints (the default handles it).

### 1b. Kinematic layer reads constraints

In `DefaultPilot.onAirborneLeg`, after popping a waypoint:

```kotlin
val constraint = (ac.route as? PilotRoute.Airborne)?.waypointConstraints?.get(poppedWaypoint)
// Resolve altitude constraint → update targetAltitudeM
// Resolve speed constraint → update targetSpeedMps
```

`resolveAltitudeConstraint` maps the sealed `AltitudeConstraint` to a target altitude in meters. This is a pure function.

### 1c. Tests

| Test | What it verifies |
|------|------------------|
| `VFR route has empty constraints` | All existing VFR route builders produce routes with `waypointConstraints.isEmpty()` |
| `resolveAltitudeConstraint At` | `At(FL040)` → 4000ft in meters |
| `resolveAltitudeConstraint AtOrAbove` | `AtOrAbove(FL040)` → at least 4000ft (clamp up) |
| `resolveAltitudeConstraint AtOrBelow` | `AtOrBelow(FL060)` → at most 6000ft (clamp down) |
| `resolveSpeedConstraint` | Same pattern for speed |
| `onAirborneLeg applies constraint at waypoint pop` | Integration: aircraft passing a constrained waypoint adjusts altitude/speed |
| Existing VFR tests unchanged | Golden test, departure/arrival verticals still pass |

---

## IFR-2: SID departure

### 2a. Implement `Instrument × Depart`

In `buildInstrumentModeRoute`, change `Depart` from `NotYetImplemented` to:

```kotlin
is TaskName.Depart -> buildSidDepartureRoute(mode.fpl, world, worldIndex)
```

`buildSidDepartureRoute`:
1. Look up `Aerodrome.sids[fpl.clearance.sid]` for the departure runway
2. Extract waypoints in published order
3. Map `Waypoint.altitudeConstraint` / `speedConstraint` → `waypointConstraints`
4. Route: departure end → SID waypoints (with constraints)
5. `arrivalPhase = PilotPhase.Climbing` (aircraft is climbing out)
6. If no SID assigned, fall back to `buildVisualDepartureRoute`

### 2b. Wire planRouteIfNeeded for IFR departures

Expand `planRouteIfNeeded` to fire for Instrument mode + FLY_SID step:
- Check aircraft is on takeoff roll or climbing
- Build route via `buildAirborneRoute(Instrument, Depart, ...)`
- Apply as intent

### 2c. isPhysicallyComplete for FLY_SID

```kotlin
MissionStep.FLY_SID -> {
    // Complete when aircraft has passed the last SID waypoint.
    // Use positionPoint proximity to the SID exit fix.
    aircraft.positionPoint == lastSidWaypoint
}
```

### 2d. Tests

| Test | What it verifies |
|------|------------------|
| `Instrument × Depart with SID` | Route follows SID waypoints in published order |
| `SID route has altitude constraints` | `waypointConstraints` populated from SID waypoints |
| `SID route has speed constraints` | Where SID defines them |
| `SID waypoints are prefix of departure route` | Property test |
| `No SID assigned falls back to visual departure` | Route = departure end → upwind → crosswind |
| `SID not found returns ProcedureNotFound` | Error path |
| `FLY_SID completes at last SID waypoint` | Mission advancement |
| `Truth table Instrument × Depart → RIGHT` | Update from NYI |
| `IFR departure end-to-end` | Integration: spawn at stand → taxi → takeoff → climb SID → en-route altitude |

---

## IFR-3: STAR + approach

Note: IFR-3 tests hardcode the approach in the FPL (via `ApproachClearance` state) rather than depending on `processInstruction` from IFR-6.

### 3a. Implement `Instrument × Arrive`

```kotlin
is TaskName.Arrive -> buildStarApproachRoute(mode.fpl, world, worldIndex)
```

`buildStarApproachRoute`:
1. Look up STAR from `Aerodrome.stars[fpl.clearance.star]`
2. Look up approach from `Aerodrome.approaches[fpl.clearance.approach]`
3. **STAR-to-approach gap**: if last STAR waypoint != first approach waypoint, insert a direct segment (ATC review finding)
4. Concatenate: STAR waypoints → (gap if needed) → approach waypoints → threshold
5. Map constraints from both procedures
6. `arrivalPhase = PilotPhase.LandingRoll`

### 3b. Implement `Instrument × ArrivalJoin`

The pilot is inbound, has STAR assigned but hasn't started the approach. Route to first STAR waypoint (or IAF if no STAR).

### 3c. isPhysicallyComplete for FLY_STAR and FLY_APPROACH

- `FLY_STAR` completes when aircraft reaches last STAR waypoint (or IAF)
- `FLY_APPROACH` completes when aircraft reaches threshold OR descends below decision altitude without visual contact (triggers go-around)

### 3d. Approach minimum altitude in applyCognitiveOverrides

The existing go-around override (below decision altitude without clearance) needs to also fire for IFR approach based on `InstrumentApproach.minimumAltitude`. Note: transition altitude affects interpretation (ATC review flag).

### 3e. Tests

| Test | What it verifies |
|------|------------------|
| `Instrument × Arrive with STAR + approach` | Route follows STAR → approach in order |
| `STAR-to-approach gap handled` | When last STAR wp != first approach wp, direct segment inserted |
| `Approach route has descending altitude constraints` | Altitude constraints decrease toward threshold |
| `STAR waypoints in published order` | Property test |
| `No waypoint appears twice` | Acyclicity property test |
| `Altitude descent is approach-segment-only` | STARs may have step-climbs — property test narrowed |
| `FLY_STAR completes at STAR terminus` | Mission advancement |
| `FLY_APPROACH completes at threshold` | Mission advancement |
| `Instrument × ArrivalJoin routes to IAF` | Route ends at IAF |
| `Missing STAR returns ProcedureNotFound` | Error path |
| `Missing approach returns ProcedureNotFound` | Error path |
| `Truth table Instrument × Arrive → RIGHT` | Update from NYI |
| `IFR arrival end-to-end` | Integration: spawn en-route → STAR → approach → land → taxi → stand |

---

## IFR-4: Missed approach

### 4a. Implement `Instrument × GoAround`

```kotlin
is TaskName.GoAround -> buildMissedApproachRoute(mode.fpl, world, worldIndex)
```

`buildMissedApproachRoute`:
1. Look up approach from FPL clearance
2. Extract `missedApproach.waypoints`
3. Look up `HoldingPattern` from `missedApproach.holdAt`
4. Route: missed approach waypoints → hold fix
5. `arrivalPhase` = a new holding phase, or reuse `PilotPhase.Climbing` (the pilot climbs on missed approach)

### 4b. isPhysicallyComplete for FLY_MISSED_APPROACH

Completes when aircraft reaches the hold fix. The subsequent `AWAITING_ATC_INSTRUCTION` step keeps the pilot in the hold until ATC re-clears.

### 4c. Hold at missed approach fix

The hold is part of the published procedure, NOT a `RouteOverride`. The pilot enters the hold pattern (`HoldingPattern.loop`) automatically. The kinematic layer flies the loop repeatedly until the mission step advances (ATC re-clears via further instruction).

This requires the kinematic layer to support hold loop flying: when at the hold fix with no next waypoint in the route, fly the `loop` path. This is new kinematic behavior.

### 4d. Tests

| Test | What it verifies |
|------|------------------|
| `Instrument × GoAround` | Route follows missed approach waypoints → hold fix |
| `Missed approach terminates at published hold fix` | Property test |
| `Multi-waypoint missed approach` | Route includes all waypoints (uses richer fixture) |
| `FLY_MISSED_APPROACH completes at hold fix` | Mission advancement |
| `Truth table Instrument × GoAround → RIGHT` | Update from NYI |
| `IFR go-around lifecycle` | Integration: approach → go-around → missed approach → hold |

---

## IFR-5: Route overrides (vectors + hold)

Independent of IFR-2/3/4 — can develop in parallel.

### 5a. RouteOverride sealed type on PilotMission

```kotlin
sealed interface RouteOverride {
    data class Vectoring(val heading: Heading) : RouteOverride
    data class Holding(val pattern: HoldingPatternId) : RouteOverride
}
```

Add `routeOverride: RouteOverride? = null` to `PilotMission`.

### 5b. processInstruction sets/clears overrides

| Instruction | Effect |
|-------------|--------|
| `FlyHeading(heading)` | `routeOverride = Vectoring(heading)` |
| `TurnHeading(direction, heading)` | `routeOverride = Vectoring(heading)` |
| `HoldAt(hold)` | `routeOverride = Holding(hold.fix)` |
| `ResumeOwnNavigation` | `routeOverride = null` |
| `LeaveHoldProceedDirect(fix)` | `routeOverride = null` + `amendFpl(ProceedDirect(fix))` |

### 5c. Kinematic layer checks override before route following

In `unifiedPilotDecide` / `planRouteIfNeeded`:
- If `routeOverride` is active, the pilot follows the override instead of the FPL route
- `Vectoring(heading)` → pilot flies the heading (new kinematic: maintain heading + current altitude)
- `Holding(pattern)` → pilot flies the hold loop from `HoldingPattern.loop`

### 5d. Speed control on approach as ActiveConstraint (ATC review flag)

ATC speed instructions (`ReduceSpeedTo`, `MaintainSpeed`) on approach are `ActiveConstraint.SpeedRestriction` candidates, not route overrides. Already defined in `ActiveConstraint` — just need the `processInstruction` wiring and kinematic override.

### 5e. Tests

| Test | What it verifies |
|------|------------------|
| `FlyHeading sets Vectoring override` | processInstruction stores override |
| `ResumeOwnNavigation clears override` | routeOverride returns to null |
| `Vectoring override suspends route following` | Pilot flies heading, not waypoints |
| `Post-resume route = fresh FPL derivation` | Property test: route matches what you'd get from a clean buildAirborneRoute |
| `HoldAt sets Holding override` | processInstruction stores override |
| `LeaveHoldProceedDirect clears override + amends FPL` | Both effects applied |
| `Override lifecycle integration` | Sim tick: waypoint following → vector assigned → heading flown → resume → waypoint following resumes |
| `Speed restriction on approach` | ReduceSpeedTo sets ActiveConstraint, kinematic respects it |

---

## IFR-6: Full FPL amendments in processInstruction

### 6a. Wire amendFpl into processInstruction

In `processInstruction`, for IFR missions:

```kotlin
val amended = amendFpl(mission.fpl, instruction)
    .getOrElse { return mission } // amendment doesn't apply — no effect
val updatedMode = NavigationMode.Instrument(amended)
mission.copy(navigationMode = updatedMode)
```

This handles all clearance-type instructions. The route planner automatically picks up the amended FPL on the next pilot tick.

### 6b. ClearanceState transition validation

The `amendFpl` function enforces progression:
- `Uncleaned` → `EnRouteClearance` (via `ClearedTo`)
- `EnRouteClearance` → `ApproachClearance` (via `ClearedApproach`)
- `ApproachClearance` → `ApproachClearance` (re-clearance for different approach)
- `Uncleaned` → `ApproachClearance` = `InvalidTransition`

### 6c. Tests — amendment sequences (Layer 5)

| Test | What it verifies |
|------|------------------|
| `ClearedTo → ProceedDirect → ClearedApproach` | Cumulative FPL state correct after chain |
| `Re-route: ClearedTo with new route` | `enRouteWaypoints` or `sid` updated |
| `ClearedApproach without prior en-route clearance` | Returns `InvalidTransition` |
| `ProceedDirect to fix not on route` | Returns `FixNotOnRoute` |
| `Double ClearedApproach (re-clear)` | Second approach replaces first |
| `Amendment sequence end-to-end` | Integration: aircraft receives clearances over time, FPL evolves, route planner produces correct routes at each stage |

---

## Cross-cutting: test architecture summary

| Layer | Scope | Count (est.) | Location |
|-------|-------|-------------|----------|
| **1. Per-cell unit** | `buildAirborneRoute` for each Instrument × TaskName | ~8 | `PilotRoutePlannerTest.kt` |
| **2. Truth table** | All 44 cells explicit (update Instrument cells from NYI to RIGHT/INVALID) | 1 parameterized | `PilotRoutePlannerTest.kt` |
| **3. Error paths** | ProcedureNotFound, STAR-approach gap, missing clearance | ~6 | `PilotRoutePlannerTest.kt` |
| **4. Constraint resolution** | `resolveAltitudeConstraint`, `resolveSpeedConstraint`, kinematic application | ~6 | `PilotAgentTest.kt` (new) |
| **5. FPL amendment sequences** | `amendFpl` unit + multi-step chains | ~8 | `FlightPlanTest.kt` (new) |
| **6. Route override lifecycle** | Override set/clear/resume, kinematic behavior | ~8 | `RouteOverrideTest.kt` (new) |
| **7. Property tests** | SID-is-prefix, STAR-in-order, no-duplicates, descent-approach-only, override-clears-clean, missed-terminates-at-hold | ~6 | `PilotRoutePlannerTest.kt` |
| **8. Integration** | IFR departure e2e, IFR arrival e2e, IFR go-around lifecycle, amendment-driven route change | ~4 | `IfrIntegrationTest.kt` (new) |
| **9. MissionStep coverage** | Every MissionStep in at least one task tree | 1 | `PilotCognitiveTest.kt` |
| **Total** | | ~48 new tests | |

### Test fixture strategy

| Fixture | Contents | Used by |
|---------|----------|---------|
| **Minimal IFR** | 1 SID, 1 STAR, 1 ILS approach, 1 missed approach (2 waypoints), 1 holding pattern | Layers 1-4, 7, 8 |
| **Rich IFR** | 2 SIDs per runway, 1 SID with transition, STAR-approach gap (different fixes), circling approach | Layer 3 selection logic, gap handling |
| **Override** | World with hold loop geometry, runway + en-route waypoints | Layer 6 |

### Key test invariants (property tests)

These run on every route produced by `buildAirborneRoute` for Instrument mode:

1. **SID waypoints are a prefix of the departure route** — the SID is the beginning, not interleaved
2. **STAR waypoints appear in published order** — no reordering
3. **No waypoint appears twice** — acyclicity (except in hold loops)
4. **Missed approach terminates at the published hold fix** — last waypoint = holdAt
5. **Route override clears cleanly** — post-resume route equals fresh `buildAirborneRoute` derivation
6. **Altitude descent is approach-segment-only** — STAR waypoints may have step-climbs; only approach segment has monotonic descent
7. **Clearance limit respected** — no en-route waypoints past `clearanceLimit` fix
8. **Every waypoint in the route exists in `worldIndex.positions`** — the kinematic layer would crash otherwise

---

## Operational flags per phase (from ATC review)

| Phase | Flag | Impact |
|-------|------|--------|
| IFR-1 | Altitude constraint resolution needs transition altitude awareness | `resolveAltitudeConstraint` should accept aerodrome transition altitude to convert between QNH and STD |
| IFR-3 | STAR-to-approach gap is common | Route builder must handle last STAR wp != first approach wp |
| IFR-3 | "Expected approach" is not a clearance | Don't route based on expected approach; wait for `ClearedApproach` |
| IFR-3 | IFR-3 tests need approach assignment | Hardcode `ApproachClearance` in test FPL rather than depending on IFR-6 |
| IFR-5 | Speed control is ActiveConstraint not RouteOverride | `ReduceSpeedTo` / `MaintainSpeed` → `ActiveConstraint.SpeedRestriction` |
| IFR-5 | "Visual approach after IFR" cancels instrument procedure | Mode transition Instrument → Visual; scope boundary for now |

---

## Verification

Each phase:
```bash
./gradlew :sim:cleanJvmTest :sim:jvmTest :controller:cleanJvmTest :controller:jvmTest
```

Plus at IFR-0:
```bash
./gradlew :protocol:cleanJvmTest :protocol:jvmTest  # FlightPlan lives in protocol
```

Golden test (2-circuit T&G stand-to-stand) must pass at every phase — VFR behavior unchanged.
