---
satisfies: [R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R14, R15]
---

# fn-14.1 — Foundation: POH crosswind limit + pilot wind sensing + crosswind-triggered self-initiated GA

## Description

Foundation pass for G3a-react. Adds POH-derived `AircraftType.maxCrosswindKnots`, lifts `WindReport` to `:protocol`, widens `PilotInput` with `weatherByAerodrome`, adds the crosswind-component helper + `RunwayId.headingDegreesMagnetic`, introduces `PilotEvent.CrosswindLimitExceeded` with split-branch `derivePilotEvent`, and adds the distinct `applyCrosswindGoAround` applier (mirrors fn-12.2 reactive Tick A pattern). Covers R1–R11 and R14; the sim integration test ships in fn-14.2.

## Problem

Aircraft has no POH-derived crosswind limit field. Pilot has no wind-state sensing surface. No crosswind-component helper. No pilot event for "crosswind exceeds limit". No applier for crosswind-triggered self-initiated GA. This task lays every protocol/pilot/sim primitive G3a-react needs **except** the sim integration test (deferred to .2).

## Files (read or modify)

- **READ**
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt` — confirm existing `Knots` (line 80, positive-only smart type) and `Wind` (line 123) shapes.
  - `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt:267-279` — `WindReport` sealed interface (moves to :protocol).
  - `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/RunwayAssessment.kt:402-409` — runway-heading parsing pattern.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotInput.kt:35-57` — firewall-restricted; widen.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt:478-500` — `atisLetterForCallInbound` singleton-fallback pattern; mirror for wind lookup.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt:57-92,120-137` — sealed + `derivePilotEvent`. **Note `PilotEvent` requires `override val aircraft: AircraftId`.**
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:687-718` — `applySelfInitiatedGoAround` (kept unchanged; new applier mirrors its **subtree-replacement + reset** pattern but with reactive Tick A intent).
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:767-782` — `applyPlannedGoAround` (Tick A intent shape: `phase = Final` retained, `route = None`; mirror this intent shape).
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:336` — `resetForGoAround(now: SimTime)`; this MUST be called.
  - `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimState.kt:68` — `weatherByAerodrome: Map<AerodromeId, WeatherObservation>`.
  - `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/PilotWiring.kt:24-37` — `buildPilotInput`.
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/AircraftType.kt:44-77` — sealed registry.
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationDatabase.kt` — entry shape.
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/FirewallPilotInputTest.kt:31-54` — canonical-constructor allowlist.

- **MODIFY**
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/AircraftType.kt` — add `maxCrosswindKnots: Knots` field on `AircraftType` sealed surface and every leaf.
  - `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt` — **move** `WindReport` sealed (lines 267-279) to `:protocol`; update consumer imports. `WeatherObservation` (line 286) **stays in `:controller`**.
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/WindReport.kt` — new file housing the moved sealed type.
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RunwayId.kt` (or wherever `RunwayId` lives) — add extension `fun RunwayId.headingDegreesMagnetic(): Int?` (Int? — null on parse failure; pilot recognition fails closed).
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotInput.kt` — add `weatherByAerodrome: Map<AerodromeId, WindReport> = emptyMap()` field. Update canonical-constructor allowlist.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt` — add `internal fun windForMission(mission, weatherByAerodrome): WindReport?` colocated with `pilotDecide`'s call site (NOT `private` in `PilotCognitive.kt` — `Pilot.kt` must be able to call it). Mirrors `atisLetterForCallInbound`'s goal-peek + singleton-fallback contract.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/Crosswind.kt` — new file with pure helper `crosswindComponentKnots(windFromMagnetic: Int, windSpeedKnots: Int, runwayHeadingMagnetic: Int): Double`.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt` — add `data class CrosswindLimitExceeded(override val aircraft: AircraftId, val componentKnots: Double, val limitKnots: Int, val runway: RunwayId) : PilotEvent` (epic R8 shape — includes `override val aircraft` AND `runway`). Split `derivePilotEvent` into independent branches.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt` — add `applyCrosswindGoAround(event, mission, aircraft, now: SimTime)` with **explicit `now` parameter** and **explicit `resetForGoAround(now)` call** (see Step 9). Keep `applySelfInitiatedGoAround` (DA path) unchanged.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt` (pilotDecide) — wire new event into precedence ladder via the self-initiated arm.
  - `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/PilotWiring.kt` — pass `weatherByAerodrome` projection (extract `wind` from `WeatherObservation`) into `buildPilotInput`.
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationDatabase.kt` — add **four** entries matching epic R14: `FAA_AFH_CH9_CROSSWIND_ERRORS`, `FAA_FAR_23_233_CROSSWIND_CERT`, `ICAO_ANNEX_6_PII_2_4_PIC`, `FAA_AIM_7_1_12_WIND_MAGNETIC`.

- **NEW TESTS**
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/observe/CrosswindHelperTest.kt` — pure math: dead-headwind = 0.0, pure crosswind = full speed, 45° ≈ 0.707 × speed, wraparound (e.g. wind 350° vs runway 010°), zero wind speed = 0.0.
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEventCrosswindTest.kt` — `derivePilotEvent` crosswind branch matrix: fires on final + weather present + crosswind > limit; null on not-on-final, weather null/NotReported, runway-parse-fail, wind-within-limit; clearance-stage-irrelevant (fires at FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND); ordering pin (DA fires before crosswind when both apply).
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotCrosswindGoAroundTest.kt` — `applyCrosswindGoAround` post-conditions: `route = None`, `phase = Final` retained, mission tree replaced (subtree replacement to `CircuitAfterGoAround` using `isCircuitLike` predicate), `mission.hasClearance` cleared via `resetForGoAround`, `Report(GoingAround)` transmitted. Distinct from `applySelfInitiatedGoAround` (DA path leaves `phase = Climbing` and retains route). **Include a TouchAndGo variant**: starting mission tree contains `CompoundTask(TaskName.TouchAndGo, ...)`; crosswind GA must rewrite that subtree the same way it rewrites `Circuit`.
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotCrosswindHysteresisTest.kt` — two-cycle: first decision cycle with crosswind > limit emits exactly one `CrosswindLimitExceeded` AND rewrites mission tree; second cycle with same crosswind state emits zero events because `currentStep` is no longer in recognition step-set.
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotCrosswindTickATickBTest.kt` — **Tick A → Tick B integration through `pilotDecide` (not direct applier call).** Tick A: crosswind event on final → `pilotDecide` produces `route=None, phase=Final, Report(GoingAround)`. Apply the report + advance one decision cycle. Tick B: planner builds the GA route per `planCircuitTrainedGoAround`'s Circuit-mode `FLY_DEPARTURE + Final + no route` special-case. This protects the load-bearing reuse assumption that the existing planner picks up the GA route on the next tick — direct applier-postcondition tests don't exercise that path.
  - Extend `FirewallPilotInputTest` — add `weatherByAerodrome = emptyMap()` to the canonical-constructor block. **Plus** a new reflection-based assertion in the same test method (or a sibling test): use Kotlin reflection (`PilotInput::class.memberProperties`) to enumerate every public property name on `PilotInput`, compare against a hard-coded `allowedFields` set (currently 6: `aircraft, worldIndex, world, now, atisByAerodrome, weatherByAerodrome`), and fail the test if the sets differ. This catches future defaulted fields slipping past the canonical block; the canonical block alone is insufficient because defaulted args don't force inclusion at call sites.

## Approach (numbered Steps)

### Step 1 — Move `WindReport` to `:protocol`

Cut sealed `WindReport` from `ControllerTypes.kt:267-279`. Paste into new file `protocol/.../WindReport.kt`. `WeatherObservation` in ControllerTypes stays; its `wind: WindReport` field resolves via protocol import. Grep `WindReport` across all modules; update package imports module-wide. Compile-impact only — zero behavior change.

### Step 2 — Add `RunwayId.headingDegreesMagnetic(): Int?`

Mirror `RunwayAssessment.kt:402-409`'s pattern. Return `Int?` — `null` when first two chars are not parseable.

```kotlin
fun RunwayId.headingDegreesMagnetic(): Int? =
    value.take(2).toIntOrNull()?.takeIf { it in 1..36 }?.let { it * 10 }
```

**Validate the parsed designator is in `1..36`** (real runway numbers are 01..36). Out-of-range and non-numeric prefixes return null; pilot recognition fails closed.

Unit test: `27 → 270`; `36L → 360`; `01R → 010`; `00 → null`; `37 → null`; `99 → null`; `HX → null`; empty → `null`.

### Step 3 — Add `AircraftType.maxCrosswindKnots: Knots`

Edit sealed `AircraftType` declaration: add abstract `val maxCrosswindKnots: Knots`. Update every leaf:

- `C172` → `Knots.unsafe(15)` (POH 15 kt demonstrated)
- `B738` → `Knots.unsafe(33)` (per FCOM dry-runway)
- Any other leaves: pick conservative POH-derived values; document in KDoc.

Reuse existing `Knots` positive-smart type. Use `.unsafe(...)` for compile-time-known constants.

### Step 4 — Add `weatherByAerodrome` to `PilotInput`

Edit `PilotInput.kt:35-57`. Add:
```kotlin
val weatherByAerodrome: Map<AerodromeId, WindReport> = emptyMap(),
```
Update `FirewallPilotInputTest`'s canonical-constructor allowlist.

Wire in `PilotWiring.kt:24-37`: `buildPilotInput` projects `simState.weatherByAerodrome.mapValues { (_, obs) -> obs.wind }` into the new field. **Satisfies R4.**

### Step 5 — Add `crosswindComponentKnots` helper

New file `pilot/.../observe/Crosswind.kt`. Pure function:

```kotlin
fun crosswindComponentKnots(
    windFromMagnetic: Int,        // 0..360, wind FROM direction (magnetic; aviation convention allows 360 == North)
    windSpeedKnots: Int,
    runwayHeadingMagnetic: Int,   // 0..360 (RunwayId.headingDegreesMagnetic() returns up to 360 for runway 36)
): Double
```

Math: relative angle `θ = ((windFromMagnetic - runwayHeadingMagnetic + 540) mod 360) - 180`; component `= |sin(θ_radians)| × windSpeedKnots`. The `mod 360` wraparound handles both 0 and 360 correctly. **Return Double — no truncation.**

### Step 6 — Add `PilotEvent.CrosswindLimitExceeded` and split `derivePilotEvent`

Add leaf **matching epic R8 exactly**:
```kotlin
data class CrosswindLimitExceeded(
    override val aircraft: AircraftId,
    val componentKnots: Double,
    val limitKnots: Int,
    val runway: RunwayId,
) : PilotEvent
```

Add explicit no-op arms to every existing exhaustive `when (event: PilotEvent)` site (no `else`).

Extend signature: `derivePilotEvent(aircraft, mission, weather: WindReport?): PilotEvent?`. Read `aircraft.type.maxCrosswindKnots` inside (do NOT take `aircraftType` as a separate parameter — `AircraftState` already carries `type`; duplicating the parameter would allow tests to pass mismatched type vs aircraft and produce impossible runtime behavior).

**Split into independent branches — no shared early returns.**

- **DA branch (existing):** unchanged.
- **Crosswind branch (new):**
  1. `aircraft.phase is Final`
  2. mission `currentStep ∈ {FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}` — NOT clearance-gated
  3. `weather is WindReport.Available`
  4. `mission.activeRunway` is `Some`
  5. `runway.headingDegreesMagnetic() != null`
  6. compute crosswind via Step 5
  7. component > `aircraft.type.maxCrosswindKnots.value.toDouble()` → emit `CrosswindLimitExceeded(aircraft.id, component, limit, runway)`

Branch-evaluation order: DA first (lower altitude takes priority), then crosswind.

### Step 7 — Helper `windForMission` in `Pilot.kt`

**Colocate in `Pilot.kt`** (NOT private in `PilotCognitive.kt` — pilotDecide must call it; top-level `private` is file-private).

```kotlin
internal fun windForMission(
    mission: PilotMission,
    weatherByAerodrome: Map<AerodromeId, WindReport>,
): WindReport?
```

Mirror `atisLetterForCallInbound`'s **actual treatment** of each goal kind:
- `Transit.destination` → key lookup
- `Departure.destination` → key lookup (rarely relevant — pilot wouldn't be on final approach during Departure)
- `Arrival` → **null fallback** (DO NOT use `Arrival.from`; that's the origin, not the landing aerodrome — same treatment as existing `atisLetterForCallInbound`)
- `CircuitTraining` → null fallback

Then singleton-fallback (**fail-closed on ambiguity**): `size == 1 → singleton.value`, `size == 0 → null`, `size > 1 → null` (return null; recognition fails closed). Unlike `atisLetterForCallInbound` which `error()`s on multi-aerodrome ambiguity (loud-failure mode is appropriate for ATIS letter resolution because that lookup is sender-aware), the wind lookup is per-tick during pilot decision — erroring would crash unrelated multi-aerodrome scenarios. Returning null is the safer fail-closed choice; multi-aerodrome G3b-react is filed as a separate deferment.

### Step 8 — Wire `derivePilotEvent` call site

In `Pilot.kt` (`pilotDecide` at line 150-157), resolve `weather = windForMission(mission, input.weatherByAerodrome)` and pass into the extended-signature call: `derivePilotEvent(aircraft, mission, weather)`. The aircraft type is read inside `derivePilotEvent` via `aircraft.type.maxCrosswindKnots`.

### Step 9 — Add `applyCrosswindGoAround` (with `now: SimTime` + explicit reset)

New function in `Pilot.kt` adjacent to `applySelfInitiatedGoAround`. Signature:

```kotlin
internal fun applyCrosswindGoAround(
    event: PilotEvent.CrosswindLimitExceeded,
    mission: PilotMission,
    aircraft: AircraftState,
    now: SimTime,
): GoAroundResult
```

**Body** — combine `applySelfInitiatedGoAround`'s subtree-replacement pattern (Pilot.kt:696-706) with `applyPlannedGoAround`'s reactive Tick A intent shape (Pilot.kt:773-781):

```kotlin
val gaTask = if (mission.navigationMode.getOrNull() is NavigationMode.Instrument) ifrGoAroundTask()
    else goAroundTask()
val newRoot = mission.root.replaceChild(
    predicate = { it is CompoundTask && !it.isComplete && it.name.isCircuitLike() },
    replacement = CompoundTask(TaskName.CircuitAfterGoAround, listOf(gaTask, circuitTask())),
)
// CRITICAL: resetForGoAround clears hasClearance, activeConstraints, routeOverride, etc.
val updatedMission = mission.resetForGoAround(now).copy(root = newRoot)

return GoAroundResult(
    intent = PilotIntent(
        targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
        phase = PilotPhase.Final,                                  // retain Final (NOT Climbing) — reactive pattern
        route = PilotRoute.None,                                   // invalidate (NOT retain) — reactive pattern
        targetAltitudeM = aircraft.type.circuitPattern.altitudeAglM,
    ),
    mission = updatedMission,
    transmissions = listOf(Report(listOf(ReportEvent.GoingAround))),
)
```

**Notes**:
- There is no `planCircuitAfterGoAround` helper in the codebase — the existing `applySelfInitiatedGoAround` performs the subtree replacement inline. Mirror that inline pattern; do NOT invent a helper.
- Use `TaskName.isCircuitLike()` (defined at `PilotMission.kt:790`) so the predicate matches `Circuit`, `CircuitAfterGoAround`, **and `TouchAndGo`**. A crosswind exceedance during a TouchAndGo circuit must rewrite the active T&G subtree the same way `handleGoAround` does (`PilotCognitive.kt:986` precedent).

KDoc cross-references: cite `applySelfInitiatedGoAround` (DA path — distinct because phase=Climbing, retains route) and `applyPlannedGoAround` (trained path — uses same reactive intent shape but does NOT replace subtree because trained tree is compile-arranged). G3a-react's situation is the **third reactive shape**: reactive intent (mirrors trained) + subtree replacement (mirrors self-initiated).

**Do NOT modify `applySelfInitiatedGoAround` body** — DA path's `phase = Climbing, route = aircraft.route` semantics stay exactly as today.

### Step 10 — Wire applier into `pilotDecide`

In `pilotDecide`, route `CrosswindLimitExceeded` through the self-initiated arm (mirror DA precedence). Invoke `applyCrosswindGoAround(event, mission, aircraft, now)`. Hysteresis comes for free — `resetForGoAround` + subtree rewrite take `currentStep` out of the crosswind recognition step-set.

### Step 11 — Add RegulationDatabase entries (epic R14 alignment — four entries)

- `FAA_AFH_CH9_CROSSWIND_ERRORS` — FAA-H-8083-3C Ch 9, "Common Errors #1" (attempting landing in crosswinds exceeding max demonstrated).
- `FAA_FAR_23_233_CROSSWIND_CERT` — 14 CFR §23.233(a) (pre-Amendment 64), 0.2 VSO certification floor.
- `ICAO_ANNEX_6_PII_2_4_PIC` — ICAO Annex 6 Part II §2.4 PIC final authority (or Part I §4.5.1 if scope is broader; verify at task time).
- `FAA_AIM_7_1_12_WIND_MAGNETIC` — AIM §7-1-12.d.3 ATC-voice winds in Magnetic degrees.

Mirror existing entry shape — `id`, `source`, `title`, `principle`, KDoc citing call sites.

### Step 12 — Spec-coverage tests (R-IDs)

Add tests for R1, R2, R3, R4, R5, R6, R8, R9, R10, R11, R14, R15. R7 (Wind KDoc) is documentation only — no test needed.

## Investigation targets

- Confirm `Knots.unsafe` companion exists at `Instruction.kt:86`. ✅ Verified pre-task.
- Verify `applyReactiveGoAround` (fn-12.2) location — if it lives in `Pilot.kt` and is shape-compatible with `applyCrosswindGoAround`, prefer factoring a single body. Otherwise inline-mirror with KDoc cross-reference.
- Confirm `mission.activeRunway.fold({ null }, { it.runway })` returns `RunwayId`. ✅ Per RunwayAssignment.kt:84.
- Verify `aircraft.type: AircraftType` field path on `AircraftState`.
- Confirm `pilotDecide`'s precedence ladder shape at `Pilot.kt:115-148`.
- Confirm `WindReport.Available.wind` field is the `Wind` in Instruction.kt:123.

## Key context

- **`PilotEvent` requires `override val aircraft: AircraftId`** — every leaf must declare this. Epic R8 signature is canonical.
- **`Knots` is positive-only** — reuse for `maxCrosswindKnots` (always ≥ 1). Compute crosswind in Double.
- **`RunwayId.headingDegreesMagnetic(): Int?`** — null on parse failure; recognition fails closed.
- **`WeatherObservation` stays in `:controller`** — pilot only sees `WindReport` projection.
- **`derivePilotEvent` branches MUST be independent** — DA's gates don't apply to crosswind.
- **`applyCrosswindGoAround`** — distinct from `applySelfInitiatedGoAround`: subtree replacement IS done (unlike trained-GA), `phase = Final` retained + `route = None` (unlike DA self-initiated). The `now: SimTime` parameter is required for `resetForGoAround(now)`.
- **No `planCircuitAfterGoAround` helper exists** — mirror the inline subtree-replacement pattern from `applySelfInitiatedGoAround:696-705`.
- **`windForMission` must live in `Pilot.kt`** — top-level `private` is file-private; `pilotDecide` calls it.
- **`windForMission` for Arrival → null fallback**, not `Arrival.from` (origin). Mirrors `atisLetterForCallInbound` exactly.
- **No controller behavior changes.** Compile-impact only on the controller for `WindReport` package migration.
- **Hysteresis via mission-tree rewrite** — no new witness.

## Acceptance

- [ ] **R1** — `AircraftType.maxCrosswindKnots: Knots` on sealed surface and every leaf. C172 = `Knots.unsafe(15)`, B738 = `Knots.unsafe(33)` (or POH-correct alternates documented). `AircraftTypeSpec` updated with `maxCrosswindKnots > 0` row.
- [ ] **R2** — `WindReport` sealed lives in `:protocol`. All consumer imports updated. `WeatherObservation` stays in `:controller`. Behavior unchanged.
- [ ] **R3** — `PilotInput.weatherByAerodrome: Map<AerodromeId, WindReport>` added. `FirewallPilotInputTest` green.
- [ ] **R4** — `buildPilotInput` projects `obs.wind` into `weatherByAerodrome` field.
- [ ] **R5** — `RunwayId.headingDegreesMagnetic(): Int?` returns null on parse failure; pilot recognition fails closed.
- [ ] **R6** — `crosswindComponentKnots(...): Double` returns Double; unit tests cover dead-headwind=0.0, pure-crosswind=full speed, 45°≈0.707×speed, wraparound, zero-speed=0.0.
- [ ] **R7** — `Wind.directionDegrees` KDoc updated to pin Magnetic FROM-degrees convention with FAA AIM cite.
- [ ] **R8** — `PilotEvent.CrosswindLimitExceeded(override val aircraft: AircraftId, val componentKnots: Double, val limitKnots: Int, val runway: RunwayId)` added. All exhaustive `when` sites updated with explicit no-op arms.
- [ ] **R9** — `derivePilotEvent` signature extended; DA and crosswind branches independent. Crosswind fires regardless of `mission.hasClearance` for steps in `{FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}`. Unit tests cover not-on-final, weather-null/NotReported, runway-parse-fail, wind-within-limit (null); crosswind-exceeded returns event. Ordering pin: DA fires first when both apply.
- [ ] **R10** — `applyCrosswindGoAround(event, mission, aircraft, now: SimTime)` exists distinct from `applySelfInitiatedGoAround`. Post-conditions: subtree replacement to `CircuitAfterGoAround`, `mission.resetForGoAround(now)` called (clears `hasClearance` + constraints), `route = None`, `phase = Final` retained, transmits `Report(GoingAround)`. `applySelfInitiatedGoAround` body unchanged.
- [ ] **R11** — `pilotDecide` wires `CrosswindLimitExceeded` through self-initiated arm to `applyCrosswindGoAround`. `PilotCrosswindHysteresisTest`: first cycle emits event + rewrites tree; second cycle with same wind state emits zero events.
- [ ] **R14** — Four `RegulationDatabase` entries added: `FAA_AFH_CH9_CROSSWIND_ERRORS`, `FAA_FAR_23_233_CROSSWIND_CERT`, `ICAO_ANNEX_6_PII_2_4_PIC`, `FAA_AIM_7_1_12_WIND_MAGNETIC`.
- [ ] **R15** (foundation slice) — `./gradlew :pilot:jvmTest :protocol:allTests :controller:jvmTest :core:allTests :sim:jvmTest detekt` exits 0. Seven existing goldens (G0/G1/G1-min/G2/G3a-trained/G3a-obstruction/G3a-continue) stay green. `SelfInitiatedGoAroundResponseSpec` unchanged. detekt baseline unchanged.

## Notes for fn-14.2

Sim integration test (`G3aPilotReactiveCrosswindTest`) + doc cross-references ride on this foundation. fn-14.2's pins are observable behavior only — `Report(GoingAround)`, commitment stage regression (`LandingClearanceIssued`/`AwaitLandedObserved` → `AwaitDownwind`), no touchdown, recovery landing. Event-counting / hysteresis pins live in fn-14.1 unit tests (R11).

## Done summary

_(filled by worker on completion)_

## Evidence

_(filled by worker on completion)_
