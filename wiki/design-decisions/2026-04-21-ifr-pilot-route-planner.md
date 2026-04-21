# 2026-04-21: IFR Pilot Route Planner (Instrument Navigation Mode)

## Context

The pilot route planner (`PilotRoutePlanner.kt`) ships with two implemented navigation modes:

- **Circuit** — full circuit pattern, go-around, T&G departure. Used by `HighLevelGoal.CircuitTraining`.
- **Visual** — VFR departure climb-out, arrival circuit join (from DOWNWIND), go-around. Used by Departure, Arrival, Transit goals.

Both dispatch on `NavigationMode x TaskName` with exhaustive sealed matching. The `Instrument` mode is defined in the sealed hierarchy but every cell returns `Left(NotYetImplemented)`.

This document designs the IFR route planner — the `NavigationMode.Instrument` implementation — so that future work can implement it against a reviewed spec without re-designing the framework.

### What already exists

The codebase has rich IFR infrastructure that the route planner can consume:

| Concept | Type | Location |
|---------|------|----------|
| SID (departure procedure) | `Sid(id, name, runway, waypoints, transitions)` | `ProcedureAndAirspaceModel.kt:110` |
| STAR (arrival procedure) | `Star(id, name, waypoints, transitions)` | `:122` |
| Instrument approach | `InstrumentApproach(id, name, type, runway, waypoints, minimumAltitude, missedApproach)` | `:153` |
| Missed approach | `MissedApproachProcedure(waypoints, holdAt)` | `:144` |
| Holding pattern | `HoldingPattern(id, fix, inboundCourse, turnDirection, loop, ...)` | `:231` |
| Waypoint with constraints | `Waypoint(point, name, altitudeConstraint?, speedConstraint?)` | `:60` |
| Altitude constraints | `AltitudeConstraint` sealed: At, AtOrAbove, AtOrBelow, Between | `:40` |
| Speed constraints | `SpeedConstraint` sealed: same variants | `:50` |
| Approach types | `ApproachType` enum: ILS, LOC, RNAV, RNP, VOR, NDB, SRA, PAR | `Instruction.kt:264` |
| Route specification | `RouteSpec` sealed: Direct, Via, Airway, ViaSid, ViaStar, ViaRoute | `:316` |
| IFR clearance | `ClearedTo(target, clearanceLimit, route?)` | `:600` |
| Approach clearance | `ClearedApproach(target, approachType, runway, circlingRunway?)` | `:791` |
| Level instructions | `ClimbTo`, `DescendTo`, `MaintainLevel`, etc. | `:699` |
| Vectoring | `FlyHeading`, `TurnHeading`, `InterceptLocaliser` | `:645` |
| Hold | `HoldAt(target, hold, expectFurtherClearanceAt?)` | `:630` |

**No FlightPlan data class exists.** This is the first thing to build.

## Design

### Principle: The FPL is the pilot's route

An IFR pilot's route comes from their filed flight plan, as amended by ATC clearance. The pilot doesn't pathfind — they follow what they filed and what ATC cleared. The key difference from VFR:

- **VFR pilot** decides their route (finds a path through VFR routes in the world).
- **IFR pilot** proposes a route (files FPL), ATC clears it (possibly amended), pilot follows the clearance.

ATC can amend the route at any time: re-route, vectors, direct-to, hold, different approach. The pilot's navigation plan updates to reflect each amendment.

### Data model: FlightPlan

```kotlin
data class FlightPlan(
    val departureAerodrome: AerodromeId,
    val departureRunway: RunwayId?,        // assigned by ATC or null if not yet known
    val sid: SidId?,                        // assigned by ATC
    val enRouteWaypoints: List<Waypoint>,   // filed route waypoints
    val star: StarId?,                      // assigned by ATC
    val approach: ApproachId?,              // assigned by ATC
    val arrivalAerodrome: AerodromeId,
    val arrivalRunway: RunwayId?,           // assigned by ATC
    val cruisingLevel: Level,
    val clearanceLimit: FixId?,            // extent of current ATC clearance
)
```

This lives in the `protocol` module (it's shared between pilot and controller). The pilot files it; ATC amends it via clearance instructions.

The `NavigationMode.Instrument` carries a `FlightPlan` instead of its current inline fields:

```kotlin
data class Instrument(val fpl: FlightPlan) : NavigationMode
```

### Route building: segment extraction from FPL

Each `TaskName` maps to a segment of the FPL:

| TaskName | FPL Segment | Source Data |
|----------|------------|-------------|
| `Depart` | Departure climb-out | `Aerodrome.sids[fpl.sid].waypoints` for the assigned runway, falling back to visual departure if no SID |
| `Transit` | En-route | `fpl.enRouteWaypoints` up to `fpl.clearanceLimit` |
| `Arrive` | STAR + approach | `Aerodrome.stars[fpl.star].waypoints` concatenated with `Aerodrome.approaches[fpl.approach].waypoints` |
| `ArrivalJoin` | Initial approach fix | First waypoint of the assigned approach (or STAR terminus). The pilot holds/sequences here until cleared for approach. |
| `GoAround` | Missed approach | `InstrumentApproach.missedApproach.waypoints` → hold at `missedApproach.holdAt` |
| `Circuit` | N/A | `InvalidCombination` — IFR does not fly VFR circuits |
| Ground tasks | N/A | `InvalidCombination` — ground routes are ATC-driven |

### Altitude and speed constraints

Current `PilotRoute.Airborne` carries a single `targetAltitudeM: Double`. IFR routes need per-waypoint altitude and speed constraints. Two options:

**Option A: Enrich PilotRoute.Airborne**
```kotlin
data class Airborne(
    val waypoints: NonEmptyList<PointId>,
    val constraints: Map<PointId, WaypointConstraints>,  // new
    val targetAltitudeM: Double,
    val arrivalPhase: PilotPhase,
)

data class WaypointConstraints(
    val altitude: AltitudeConstraint? = null,
    val speed: SpeedConstraint? = null,
)
```

The kinematic layer (`DefaultPilot.onAirborneLeg`) checks constraints at each waypoint pop and adjusts target altitude/speed. VFR routes have no constraints (empty map), so backward-compatible.

**Option B: Separate IFR route type**
```kotlin
data class InstrumentRoute(
    val waypoints: NonEmptyList<Waypoint>,  // Waypoint already has constraints
    val arrivalPhase: PilotPhase,
) : PilotRoute
```

Option A is preferred — it keeps one airborne route type and adds constraint support incrementally. The `Waypoint` type from the world model already carries `AltitudeConstraint` and `SpeedConstraint`, so the route builder just copies them from the procedure definition.

### ATC amendments as navigation plan mutations

When ATC amends the route, the pilot's `NavigationMode.Instrument(fpl)` updates:

| ATC Instruction | FPL Mutation |
|----------------|-------------|
| `ClearedTo(clearanceLimit, route?)` | Set `fpl.clearanceLimit`; if `route` is `ViaSid(sid)`, set `fpl.sid`; if `ViaStar(star)`, set `fpl.star` |
| `ClearedApproach(type, runway)` | Set `fpl.approach` (look up by type + runway); set `fpl.arrivalRunway` |
| `ProceedDirect(fix)` | Truncate `fpl.enRouteWaypoints` from current position to `fix` |
| `HoldAt(hold)` | Not an FPL mutation — this is a route override (see below) |
| `FlyHeading(heading)` | Not an FPL mutation — this is a route override (see below) |
| `ClimbTo` / `DescendTo` | Not an FPL mutation — these are level instructions applied to the current constraint |

FPL mutations happen in `processInstruction` in `PilotCognitive.kt`, updating `mission.navigationMode`.

### Vectors and holds: route overrides, not FPL mutations

The ATC review established that radar vectors (`FlyHeading`) and holding (`HoldAt`) are **route replacements**, not constraints on route-following. They temporarily suspend the pilot's FPL-based route:

```kotlin
sealed interface RouteOverride {
    data class Vectoring(val heading: Heading) : RouteOverride
    data class Holding(val pattern: HoldingPatternId) : RouteOverride
}
```

When a `RouteOverride` is active, the pilot follows the override (fly heading / fly hold loop) instead of the FPL route. When ATC terminates the override ("resume own navigation" / "leave hold, proceed direct"), the pilot resumes FPL-based routing.

`RouteOverride` lives on `PilotMission` alongside `activeConstraints`. The distinction:
- **`ActiveConstraint`**: modifies how the pilot follows their route (extend downwind = don't turn base yet, speed restriction = fly slower). The route itself is unchanged.
- **`RouteOverride`**: replaces the route entirely. The pilot flies headings or hold loops instead of waypoints. The FPL route is suspended, not modified.

### Missed approach procedure

`GoAround` for Instrument mode follows the published missed approach:

1. Look up `InstrumentApproach.missedApproach` from the FPL's assigned approach.
2. Build route from missed approach waypoints.
3. Terminal state: hold at `missedApproach.holdAt` until ATC issues further clearance.

The hold at the end of the missed approach is NOT a `RouteOverride` — it's part of the published procedure. The pilot enters the hold automatically and maintains it until ATC re-clears. This is a `AWAITING_ATC_INSTRUCTION` mission step.

### PilotRoute.Airborne evolution

The route type gains constraint support but stays one type:

```kotlin
data class Airborne(
    val waypoints: NonEmptyList<PointId>,
    val targetAltitudeM: Double,
    val arrivalPhase: PilotPhase,
    val waypointConstraints: Map<PointId, WaypointConstraints> = emptyMap(),
)
```

The `waypointConstraints` map is empty for VFR routes (zero cost, full backward compatibility). For IFR routes, the builder populates it from the procedure waypoints. The kinematic layer checks it at each waypoint:

```kotlin
// In DefaultPilot.onAirborneLeg, after popping a waypoint:
val constraint = route.waypointConstraints[poppedWaypoint]
if (constraint?.altitude != null) {
    targetAltitudeM = resolveAltitudeConstraint(constraint.altitude)
}
if (constraint?.speed != null) {
    targetSpeedMps = resolveSpeedConstraint(constraint.speed)
}
```

### Mission step mapping for IFR

IFR flights need new mission steps that don't exist in the VFR-centric `MissionStep` enum:

| Phase | New Step | Completion |
|-------|----------|------------|
| Departure | `FLY_SID` | PHYSICAL: aircraft passes last SID waypoint |
| En-route | `FLY_EN_ROUTE` | PHYSICAL: aircraft reaches clearance limit |
| STAR | `FLY_STAR` | PHYSICAL: aircraft reaches last STAR waypoint |
| Approach | `FLY_APPROACH` | PHYSICAL: aircraft reaches threshold (or decision altitude for missed) |
| Missed | `FLY_MISSED_APPROACH` | PHYSICAL: aircraft enters missed approach hold |

These steps live in `MissionStep` enum. The `planMission` function gets a new decomposition for IFR:

```kotlin
is HighLevelGoal.Departure -> when (flightRules) {
    VFR -> /* existing */
    IFR -> CompoundTask(TaskName.Depart, listOf(
        groundDepartureTask(humanPiloted),
        PrimitiveTask(MissionStep.FLY_SID, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.FLY_EN_ROUTE, CompletionMode.PHYSICAL),
    ))
}
```

The `TaskName` sealed hierarchy does NOT need new variants — `Depart`, `Arrive`, `Transit` already exist and work for IFR. The `NavigationMode.Instrument` on the mission determines which route builder runs.

### HighLevelGoal evolution

`HighLevelGoal` gains a `flightRules` field or the IFR-specific data:

```kotlin
sealed interface HighLevelGoal {
    data class Departure(
        val destination: AerodromeId? = null,
        val fpl: FlightPlan? = null,  // non-null for IFR
    ) : HighLevelGoal
    // ...
}
```

Or, more cleanly, flight rules are on the `NavigationMode` (which they already are — `Instrument` vs `Visual`). The `HighLevelGoal` doesn't need to know IFR/VFR; it just says "depart to X." The `NavigationMode` decides how.

### Existing test infrastructure impact

IFR tests need a world with SIDs, STARs, and approaches defined. Currently only FullCircuitTest and DepartureVerticalTest have circuit procedures. A new IFR test fixture would include:

```kotlin
val sid = Sid(
    id = SidId("SID-09"),
    name = "DEPARTURE ONE ALPHA",
    runway = runwayId,
    waypoints = listOf(Waypoint(exitFix, altitudeConstraint = AtOrAbove(FL040))),
)
val star = Star(
    id = StarId("STAR-09"),
    name = "ARRIVAL ONE BRAVO",
    waypoints = listOf(Waypoint(iaf, altitudeConstraint = AtOrBelow(FL060))),
)
val approach = InstrumentApproach(
    id = ApproachId("ILS-09"),
    name = "ILS Runway 09",
    type = ApproachType.ILS,
    runway = runwayId,
    waypoints = listOf(Waypoint(iaf), Waypoint(faf), Waypoint(threshold)),
    minimumAltitude = ApproachMinimum(MinimumType.DECISION_ALTITUDE, Level.AltitudeFeet.unsafe(200)),
    missedApproach = MissedApproachProcedure(
        waypoints = listOf(Waypoint(mapFix)),
        holdAt = holdId,
    ),
)
```

### Testing strategy

Same three layers as VFR, extended for IFR cells:

**Layer 1 — per-cell assertions:**
- `Instrument × Depart`: route follows SID waypoints in order, constraints populated
- `Instrument × Transit`: route follows en-route waypoints up to clearance limit
- `Instrument × Arrive`: route follows STAR → approach waypoints, ends at threshold
- `Instrument × GoAround`: route follows missed approach procedure, ends at hold fix
- `Instrument × ArrivalJoin`: route to IAF (or null if not yet cleared)

**Layer 2 — truth table update:**
All Instrument cells change from NYI to RIGHT (for implemented) or INVALID (for structurally impossible). The truth table test breaks if any cell is missed.

**Property tests:**
- Every waypoint in an IFR route has a corresponding position in the world index
- Altitude constraints are monotonically descending on approach routes
- The clearance limit is respected (no waypoints past it)

## Implementation Phases

### IFR-1: FlightPlan data type + NavigationMode.Instrument simplification

- Define `FlightPlan` in `protocol` module
- Replace `NavigationMode.Instrument`'s inline fields with `fpl: FlightPlan`
- Update truth table test (constructor change only)
- No new route building — all cells still return Left

### IFR-2: SID departure

- Implement `Instrument × Depart` in `buildAirborneRoute`
- Extract SID waypoints from `Aerodrome.sids[fpl.sid]`
- Fall back to visual departure if no SID assigned
- Add `FLY_SID` mission step
- Add per-waypoint altitude/speed constraints to `PilotRoute.Airborne`
- Kinematic layer reads constraints at waypoint pop

### IFR-3: STAR + approach

- Implement `Instrument × Arrive` and `Instrument × ArrivalJoin`
- Extract STAR waypoints, concatenate with approach waypoints
- Add `FLY_STAR`, `FLY_APPROACH` mission steps
- Approach minimum altitude gates go-around decision in `applyCognitiveOverrides`

### IFR-4: Missed approach

- Implement `Instrument × GoAround`
- Extract missed approach procedure from approach definition
- Terminal hold at missed approach hold fix
- Add `FLY_MISSED_APPROACH` mission step

### IFR-5: Route overrides (vectors + hold)

- Define `RouteOverride` sealed type on `PilotMission`
- `FlyHeading` → `Vectoring` override
- `HoldAt` → `Holding` override
- `ResumeOwnNavigation` / `LeaveHoldProceedDirect` → clear override
- Kinematic layer checks override before route following

### IFR-6: FPL amendments

- `processInstruction` updates `fpl` on clearance instructions
- `ClearedTo` updates clearance limit and route
- `ProceedDirect` truncates en-route waypoints
- `ClearedApproach` sets approach assignment
- Route planner re-derives route on next pilot tick

## Review findings

Three-agent review (ATC, FP, Test) produced the following findings. All accepted into the design.

### FlightPlan type design (FP review, ATC review)

**Use a clearance-state progression instead of nullable fields.** The six nullable fields form an implicit state machine. A `ClearanceState` sealed hierarchy makes illegal states unrepresentable:

```kotlin
sealed interface ClearanceState {
    /** Filed but not yet cleared by ATC. */
    data object Uncleaned : ClearanceState
    /** Cleared with a route and clearance limit. */
    data class EnRouteClearance(
        val clearanceLimit: FixId,
        val sid: SidId?,
        val star: StarId?,
    ) : ClearanceState
    /** Cleared for approach — approach and arrival runway are non-null. */
    data class ApproachClearance(
        val clearanceLimit: FixId,
        val sid: SidId?,
        val star: StarId?,
        val approach: ApproachId,
        val arrivalRunway: RunwayId,
    ) : ClearanceState
}
```

**Add alternate aerodrome** (`alternateAerodrome: AerodromeId?`) — IFR flights must file one. Drives the missed approach hold-vs-divert decision.

**Separate requested vs cleared level** — `cruisingLevel` is what the pilot filed. ATC may clear a different level. Cleared level lives on clearance state or `ActiveConstraint`.

### FPL amendment function (FP review)

**Define `amendFpl` as a total pure function** before implementing IFR-6:

```kotlin
fun amendFpl(fpl: FlightPlan, instruction: AtcInstruction): Either<AmendmentError, FlightPlan>
```

Writing the exhaustive `when` over instruction types will discover which field combinations are legal, shaping the `ClearanceState` type. This function is the most important missing piece — without it, amendment logic scatters across `processInstruction`.

### STAR-to-approach transition gap (ATC review)

The STAR terminus and approach IAF may be different fixes. ATC fills the gap with vectors or "proceed direct IAF." The route builder must handle this: if the last STAR waypoint is not the first approach waypoint, insert a direct segment between them. Acknowledge this explicitly in IFR-3.

### Waypoint constraints approach (FP review)

The `waypointConstraints: Map<PointId, WaypointConstraints>` on `PilotRoute.Airborne` is pragmatic but dishonest for VFR (always-empty map). Mitigation: add a construction-time invariant check that VFR route builders never populate the map. Alternatively, accept the pragmatic choice and document the convention.

### MissionStep enum growth risk (FP review)

Adding 5 `MissionStep` entries won't break `allTaskNames()` (that's `TaskName`, not `MissionStep`). But `skipCompletedSteps` in `PilotMission.kt` uses manually-constructed sets — the compiler won't warn about missing new steps. Either convert `MissionStep` to sealed or add a test asserting every value appears in at least one task tree.

### Additional test layers for IFR (Test review)

The VFR three-layer pattern is necessary but not sufficient. IFR needs:

- **Layer 5: FPL amendment sequence tests** — verify chains of `ClearedTo` → `ProceedDirect` → `ClearedApproach` produce correct cumulative FPL state.
- **Layer 6: Route override lifecycle tests** — vector assigned → heading flown → resume own nav → FPL route restored. At least one integration test through the sim tick loop.
- **Richer second fixture** — multiple SIDs per runway, SID with transitions, multi-waypoint missed approach.

### Additional property tests (Test review)

Beyond the three proposed properties, add:
- SID waypoints are a prefix of the departure route
- STAR waypoints appear in published order
- No waypoint appears twice (acyclicity, except holds)
- Missed approach terminates at the published hold fix
- Route override clears cleanly (post-resume route = fresh FPL derivation)
- Altitude constraint descent is approach-segment-only (STARs can have step-climbs)

### Operational concepts to flag (ATC review)

Not blocking but should be noted for the relevant IFR phase:
- **Transition altitude/level** — affects altitude constraint interpretation (IFR-3)
- **Expected approach** — ATC tells traffic "expect ILS 09" before formal clearance; not a clearance, sets pilot expectation (IFR-3)
- **Speed control on approach** — ATC speed instructions are `ActiveConstraint` candidates, not route overrides (IFR-5)
- **Visual approach after IFR** — "cleared visual approach" cancels the instrument procedure (scope boundary)
- **IFR-3 needs minimal IFR-6** — approach assignment via `ClearedApproach` is needed to test STAR+approach. Either hardcode in FPL for IFR-3 tests or bring a thin slice of IFR-6 forward.

## Scope boundary

This design covers the pilot's route planning for IFR. It does NOT cover:

- **Controller IFR procedures** — approach sequencing, vectoring decisions, separation. These are controller-side concerns (Phase 5/6 territory).
- **FPL filing and negotiation** — the sim spawns aircraft with pre-built FPLs. No CFMU/slot allocation.
- **CPDLC / datalink** — all clearances are voice (radio transmission model).
- **Performance-based navigation** — RNP/RNAV path computation. The route builder follows published waypoints; it doesn't compute curved segments.
- **CAT II/III approach automation** — the pilot follows waypoints to threshold; autoland is out of scope.
- **Visual approach after IFR** — "cleared visual approach" cancels the instrument procedure and reverts to visual navigation. Operationally common in good weather; design-wise it's a mode transition from Instrument to Visual.
