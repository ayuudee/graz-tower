---
satisfies: [R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R13, R14]
---

# fn-15.1 — Foundation: tailwind reactive-GA recognition + WindComponents.kt consolidation

## Description

Foundation pass for G3a-react-tailwind. Sibling of fn-14.1. Adds `AircraftType.maxTailwindKnots` (with **per-type doctrinal severity framing** — advisory for C172 via FAA AFH, hard limitation for B738 via FCOM), renames `Crosswind.kt → WindComponents.kt` colocating the new `tailwindComponentKnots` helper, introduces `PilotEvent.TailwindLimitExceeded` with a third independent branch in `derivePilotEvent` (ordering: DA → tailwind → crosswind), adds the distinct `applyTailwindGoAround` applier (identical body to `applyCrosswindGoAround`, cross-referenced via KDoc), wires the new event into `pilotDecide`'s precedence ladder, extracts the shared mission-shape eligibility helper, renames `CROSSWIND_ELIGIBLE_STEPS → WIND_REACTIVE_ELIGIBLE_STEPS`, adds two `RegulationDatabase` entries (codex-tightened from the original three — no fictitious legal authority). Covers R1–R10 and R13; the sim integration test ships in fn-15.2. Every fn-14 spec stays GREEN unchanged.

## Problem

After fn-14, the pilot can recognise a POH crosswind exceedance via world-weather sensing. There is no equivalent recognition for the tailwind axis — the second pilot-reactive recognition axis, where doctrinal severity varies per aircraft type (advisory for light singles like C172 where POH does not publish a hard limit; hard limitation for jets like B738 where FCOM publishes one). This task lays the typed datum + helper + recognition + applier + tests for the tailwind path, reusing every shipped fn-14 channel (`PilotInput.weatherByAerodrome`, `PilotWiring` projection, `windForMission`, `RunwayId.headingDegreesMagnetic`, `GA-PRE-CLEAR`/`GA-POST-CLEAR` interrupts) and surfaces the per-type doctrinal severity asymmetry in KDoc.

## Files (read or modify)

- **READ**
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt:80` — `Knots` positive-only smart type (reuse for `maxTailwindKnots`).
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt:123-160` — `Wind.directionDegrees` range (`0..360`; both `0` and `360` valid per validator).
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/AircraftType.kt:44-309` — sealed registry shape; the post-fn-14 leaf KDoc + `maxCrosswindKnots` precedent shape.
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/WindReport.kt` — lifted by fn-14; consumed unchanged.
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RunwayHeading.kt` — fn-14's `headingDegreesMagnetic()` extension; unchanged.
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationDatabase.kt:212` — `ICAO4444_7_11` existing entry ("Post-landing taxi"); new entry uses distinct symbol name `ICAO4444_7_11_6_REDUCED_RUNWAY_TAILWIND` with `document = "ICAO_4444"` consistent with the established convention.
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationDatabase.kt:480-572` — fn-14's four crosswind entries; mirror shape for two new tailwind entries (codex round-1 tightened from three).
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/Crosswind.kt` — to be **renamed** to `WindComponents.kt`. Existing `crosswindComponentKnots` body and KDoc unchanged in place.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt:1-329` — sealed `PilotEvent`, `derivePilotEvent`, `deriveDecisionAltitudeEvent`, `deriveCrosswindEvent`, `CROSSWIND_ELIGIBLE_STEPS`.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:115-251` — `pilotDecide` precedence ladder, fn-14's 4-arm `when (val pilotEvent = derivePilotEvent(...))`.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:287-302` — `windForMission` (unchanged).
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:770-913` — `applySelfInitiatedGoAround` (DA path; unchanged), `applyCrosswindGoAround` (fn-14; mirror its body shape for the new applier).
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:336,790` — `resetForGoAround(now)` and `TaskName.isCircuitLike()` (reused unchanged).
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/observe/CrosswindHelperTest.kt` — fn-14's pure-math test pattern; mirror as `TailwindHelperTest`.
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEventCrosswindTest.kt` — fn-14's recognition-matrix test pattern; mirror as `PilotEventTailwindTest`.
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotCrosswindGoAroundTest.kt` — fn-14's applier test (incl. TouchAndGo variant); mirror as `PilotTailwindGoAroundTest`.
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotCrosswindHysteresisTest.kt` — fn-14's two-cycle suppression test; mirror as `PilotTailwindHysteresisTest`.
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotCrosswindTickATickBTest.kt` — fn-14's Tick A→B integration test; mirror as `PilotTailwindTickATickBTest`.
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/AircraftTypeSpec.kt` — invariant rows; add `maxTailwindKnots > 0` row.

- **RENAME**
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/Crosswind.kt` → `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/WindComponents.kt`. **File-level KDoc** updated to describe both helpers as siblings; **package and existing `crosswindComponentKnots` declaration unchanged** (the rename is filesystem-level only, no semantic move). Update imports in:
    - Same-package references in `PilotEvent.kt` and test files do not need import changes (same package). FQN imports (`import xyz.easiersaid.twr.pilot.observe.crosswindComponentKnots`) remain valid because package unchanged.
    - **Verify at task time** with `grep -rn "import xyz.easiersaid.twr.pilot.observe.crosswindComponentKnots\|from.*Crosswind\\.kt"` — confirm no path-based imports.

- **MODIFY**
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/AircraftType.kt` — add abstract `val maxTailwindKnots: Knots` field on sealed surface. Update both leaves with per-type doctrinal framing per R1. **C172 KDoc**: "Cessna 172S NAV III / 172R POH §2 does NOT publish an explicit hard tailwind limitation. The `Knots.unsafe(10)` value is the FAA AFH Ch 9 industry-standard advisory operating maximum for light singles. Modelling: a competent VFR pilot goes around when the advisory is exceeded — mirroring fn-14 crosswind modelling (AC 23-8B's demonstrated value is similarly performance information, but treated as the trigger). Personal-minimums layer filed as D-PASS." **B738 KDoc**: "Boeing 737-800 FCOM Limitations §1: 15 kt steady tailwind on dry runway. Hard operational limitation. Verify edition at task time." Field-level KDoc documents per-type doctrinal severity asymmetry + cross-references `maxCrosswindKnots`.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/WindComponents.kt` (post-rename) — add `tailwindComponentKnots(...)` pure helper. KDoc per R3 (frame contract, sign convention, examples, True-vs-Magnetic pitfall cross-ref, FAA AIM cite, cross-ref `crosswindComponentKnots`).
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt`:
    - Rename `CROSSWIND_ELIGIBLE_STEPS → WIND_REACTIVE_ELIGIBLE_STEPS` (per R7). Update KDoc explaining the shared symbol between branches.
    - Extract `isCircuitLike` mission-shape guard from `deriveCrosswindEvent` to `private fun isReactiveGoAroundEligible(mission: PilotMission): Boolean` (per R6). Update `deriveCrosswindEvent` to call it; the new `deriveTailwindEvent` will call it too.
    - Add `data class TailwindLimitExceeded(override val aircraft: AircraftId, val componentKnots: Double, val limitKnots: Int, val runway: RunwayId) : PilotEvent` leaf (per R4). KDoc cites: per-type doctrinal severity asymmetry (advisory vs hard limit); pure-derivation axis (same axis as `CrosswindLimitExceeded`); trigger predicate (independent of crosswind branch); per-leaf source cross-reference (C172 AFH advisory vs B738 FCOM hard limit); sim coverage `[xyz.easiersaid.twr.sim.G3aPilotReactiveTailwindTest]`.
    - Add `private fun deriveTailwindEvent(aircraft, mission, weather)` mirroring `deriveCrosswindEvent` (per R5). Body: phase=Final guard → step guard via `WIND_REACTIVE_ELIGIBLE_STEPS` → mission-shape guard via `isReactiveGoAroundEligible` → weather guard → activeRunway guard → runway-heading parse guard → compute `tailwindComponentKnots(...)` → strict `>` against `aircraft.type.maxTailwindKnots.value.toDouble()` → emit `TailwindLimitExceeded(aircraft.id, component, limit, runway)`.
    - Update `derivePilotEvent` body to chain three branches: `deriveDecisionAltitudeEvent(...) ?: deriveTailwindEvent(...) ?: deriveCrosswindEvent(...)`. KDoc updated to enumerate the three-branch ordering and the doctrinal rationale.
    - File-level KDoc: "Current leaf set (3 leaves)" → "Current leaf set (4 leaves)". Add `TailwindLimitExceeded` entry with per-type severity note.
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt`:
    - Add `applyTailwindGoAround(event: PilotEvent.TailwindLimitExceeded, mission, aircraft, now: SimTime): GoAroundResult` adjacent to `applyCrosswindGoAround`. Body **identical** to `applyCrosswindGoAround`: `replaceChild { isCircuitLike }` + `resetForGoAround(now)` + Tick A intent (`route=None, phase=Final retained, targetSpeedMps=climbSpeedMps, targetAltitudeM=circuitPattern.altitudeAglM`) + `Report(GoingAround)` transmission. KDoc cross-references `applyCrosswindGoAround` as sibling, documents per-type doctrinal severity asymmetry, and documents the anti-decision (no shared core helper).
    - Extend `pilotDecide`'s `when (val pilotEvent = derivePilotEvent(...))` block (lines 178-192 after fn-14) with a fifth arm. **Arms written in DA → tailwind → crosswind dispatch order** mirroring `derivePilotEvent`'s branch order (codex round-1 cosmetic fix; dispatch is functionally order-independent since only one event surfaces per call, but the visual ordering aligns with the documented evaluation ordering):
      ```kotlin
      when (val pilotEvent = derivePilotEvent(aircraft, cognitive.updatedMission, weather)) {
          is PilotEvent.DecisionAltitudeWithoutClearance ->
              applySelfInitiatedGoAround(pilotEvent, cognitive.updatedMission, aircraft, input.now)
          is PilotEvent.TailwindLimitExceeded ->
              applyTailwindGoAround(pilotEvent, cognitive.updatedMission, aircraft, input.now)
          is PilotEvent.CrosswindLimitExceeded ->
              applyCrosswindGoAround(pilotEvent, cognitive.updatedMission, aircraft, input.now)
          is PilotEvent.AtcGoAroundOnFinal, null -> null
      }
      ```
    - Audit the surrounding inline comment block (lines 99-128, 165-175) to reflect five GA paths.
  - `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationDatabase.kt` — add **two** entries per R13 (codex-tightened from original three):
    - `FAA_AFH_CH9_TAILWIND_RISK` — FAA-H-8083-3C Ch 9 high-risk operations framing for tailwind landings. `category = GUIDANCE`. `document = "FAA_AFH"`. Sibling of `FAA_AFH_CH9_CROSSWIND_ERRORS` (fn-14).
    - `ICAO4444_7_11_6_REDUCED_RUNWAY_TAILWIND` — ICAO Doc 4444 §7.11.6 (5 kt tailwind for reduced runway separation). `category = GUIDANCE`. `document = "ICAO_4444"` (consistent with existing `ICAO4444_*` entries; verified at `RegulationDatabase.kt:84-230`). **Symbol name must be `ICAO4444_7_11_6_REDUCED_RUNWAY_TAILWIND`** because `ICAO4444_7_11` already exists for "Post-landing taxi" (verified at line 212). KDoc explicitly notes scope difference (separation minima ≠ POH performance; peer doctrinal anchor for completeness, not the trigger anchor).
    - **NOT added**: `POH_TAILWIND_LIMITATION_GENERAL` (codex round-1 closure — manufacturer values are not regulations).
    - **NOT added**: FAR/CS tailwind cert entry (no specific tailwind certification clause is verifiable in §23.51 / §25.105 / CS-25.105 at this scope; reality-anchored discipline rejects inventing one).

- **NEW TESTS**
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/observe/TailwindHelperTest.kt` — pure math (R3 cases): dead headwind = 0.0; pure 90° crosswind = 0.0; 180° dead tailwind 20 kt = 20.0; 135° quartering 20 kt ≈ 14.14 (±0.01); wraparound (wind 180° vs runway 360°); zero-speed = 0.0; small angles near headwind (89° crosswind → 0.0; 91° quartering → small positive); strict `>` boundary at 10 kt limit (component = 10.0 → no event at the recognition layer; component = 10.0001 → event).
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEventTailwindTest.kt` — full recognition discriminator matrix (R10 cases including ordering pin: when both `TailwindLimitExceeded` and `CrosswindLimitExceeded` apply same tick, tailwind fires; DA fires before both).
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotTailwindGoAroundTest.kt` — applier post-conditions (`route=None`, `phase=Final` retained, mission tree replaced, `resetForGoAround(now)` called, `Report(GoingAround)` transmitted) + TouchAndGo variant.
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotTailwindHysteresisTest.kt` — two-cycle: first cycle with tailwind > limit emits exactly one event + rewrites tree; second cycle emits zero events.
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotTailwindTickATickBTest.kt` — Tick A → Tick B integration through `pilotDecide` (not direct applier call). Tick A: tailwind event on final → `route=None, phase=Final, Report(GoingAround)`. Tick B: planner builds GA route per `planCircuitTrainedGoAround`'s Circuit-mode special-case (load-bearing reuse pin).
  - Extend `AircraftTypeSpec.kt` with `maxTailwindKnots > 0` row.

## Approach (numbered Steps)

### Step 1 — Rename `Crosswind.kt → WindComponents.kt`

Filesystem-level rename. File-level KDoc updated to describe both helpers as siblings:
```
/**
 * Pure helpers for computing the crosswind and tailwind components
 * of a steady-state wind against a runway heading. fn-14.1 introduced
 * `crosswindComponentKnots`; fn-15.1 colocates `tailwindComponentKnots`.
 * Both helpers share the same single-reference-frame contract
 * (Magnetic FROM-degrees) and the same True-vs-Magnetic pitfall warning.
 * ...
 */
```
Verify `:pilot:jvmTest` GREEN after the rename, BEFORE adding the new helper. This isolates the rename's "zero behaviour change" claim.

### Step 2 — Add `tailwindComponentKnots` pure helper

Append after `crosswindComponentKnots` in `WindComponents.kt`:

```kotlin
fun tailwindComponentKnots(
    windFromMagnetic: Int,
    windSpeedKnots: Int,
    runwayHeadingMagnetic: Int,
): Double {
    val rawDelta = ((windFromMagnetic - runwayHeadingMagnetic) % 360 + 360) % 360
    val signed = if (rawDelta > 180) rawDelta - 360 else rawDelta
    val radians = signed * PI / 180.0
    val headwindComponent = cos(radians) * windSpeedKnots.toDouble()
    return maxOf(0.0, -headwindComponent)
}
```

KDoc per R3. Run `:pilot:jvmTest --tests "*TailwindHelperTest*"` GREEN after Step 4 (when the test is in place).

### Step 3 — Add `maxTailwindKnots` to `AircraftType` sealed surface (per-type framing)

Edit `AircraftType.kt`:
- Add abstract `val maxTailwindKnots: Knots` to the constructor parameter list.
- **C172**: `Knots.unsafe(10)`. Per-leaf KDoc:
  ```
  Pre-plan source verification: the Cessna 172S NAV III / 172R POH §2 Operating
  Limitations does NOT publish an explicit hard tailwind component limitation
  (POH §2 addresses crosswind only — 15 kt demonstrated, not a limitation).
  The 10 kt value used here is the FAA AFH Ch 9 (FAA-H-8083-3C) industry-
  standard advisory operating maximum for light singles; the AFH frames
  tailwind landings as high-risk operations and 10 kt is the common
  operating advisory. Modelling: a competent VFR pilot goes around when
  the advisory is exceeded — same rationale as fn-14's crosswind modelling
  (AC 23-8B's demonstrated value is similarly performance information,
  but a competent pilot treats it as the trigger). Personal-minimums
  judgement layer filed as `D-PASS-g3a-react-tailwind-personal-minimums`.
  ```
- **B738**: `Knots.unsafe(15)`. Per-leaf KDoc:
  ```
  Boeing 737-800 FCOM Limitations §1: 15 kt steady tailwind component on
  dry runway. Hard operational limitation (Limitations section, no
  exception). Verify edition at task time.
  ```
- **Field-level KDoc** (sealed-class level) documents per-type doctrinal severity asymmetry:
  ```
  Maximum tailwind component the type's operating handbook (POH/FCOM) or
  industry guidance recognises as the operational maximum.
  
  Doctrinal severity varies per type — for some types (e.g. C172 light
  single) the POH does not publish an explicit tailwind limit and the
  value used here is the FAA AFH industry-standard advisory; for others
  (e.g. B738 narrow-body twinjet) the FCOM publishes a hard limitation.
  Per-leaf KDocs cite the source. The pilot's reactive-GA recognition
  fires on exceedance regardless of doctrinal severity — modelling a
  competent pilot's go-around decision.
  
  Reuses [Knots] positive-only smart type. End-to-end sim coverage:
  [xyz.easiersaid.twr.sim.G3aPilotReactiveTailwindTest] (fn-15.2). Pilot-
  side unit coverage: [xyz.easiersaid.twr.pilot.observe.PilotEventTailwindTest]
  + [xyz.easiersaid.twr.pilot.PilotTailwindHysteresisTest] (fn-15.1).
  ```
- `AircraftTypeSpec` invariant row added: `maxTailwindKnots > 0` (positive-only invariant comes for free via `Knots`; this is a redundant guard that surfaces a regression loudly).

### Step 4 — Rename `CROSSWIND_ELIGIBLE_STEPS → WIND_REACTIVE_ELIGIBLE_STEPS`

In `PilotEvent.kt`:
- Rename the `private val` constant.
- Update KDoc explaining the shared symbol between crosswind and tailwind branches.
- Update existing references inside `deriveCrosswindEvent`.

Verify `:pilot:jvmTest` GREEN — zero behaviour change.

### Step 5 — Extract `isReactiveGoAroundEligible` helper

In `PilotEvent.kt`, lift fn-14's inline `mission.root.activeCompound()?.name?.isCircuitLike()` guard to a private function:

```kotlin
/**
 * fn-15.1: shared mission-shape guard for the reactive-GA recognition
 * branches (crosswind, tailwind). Fail-closed if the active compound
 * is not rewritable by `applyCrosswindGoAround` / `applyTailwindGoAround`'s
 * `replaceChild { isCircuitLike }` predicate. The Transit-arrival mission
 * shape (FINAL primitive directly under Transit compound) cannot be
 * rewritten by `isCircuitLike`-keyed replacement; firing recognition
 * without rewrite would emit `Report(GoingAround)` while leaving the
 * step in the eligible set, causing re-fire every tick.
 *
 * Multi-aerodrome / Transit-arrival reactive recognition is filed as
 * `D-PASS-g3b-react-cross-aerodrome-crosswind` (fn-14) and
 * `D-PASS-g3b-react-cross-aerodrome-tailwind` (fn-15).
 */
private fun isReactiveGoAroundEligible(mission: PilotMission): Boolean {
    val activeCompoundName = mission.root.activeCompound()?.name ?: return false
    return activeCompoundName.isCircuitLike()
}
```

Update `deriveCrosswindEvent` to call this helper. Verify `:pilot:jvmTest` GREEN — zero behaviour change.

### Step 6 — Add `PilotEvent.TailwindLimitExceeded` leaf

Add the data class adjacent to `CrosswindLimitExceeded` in the sealed interface. KDoc per R4. File-level KDoc updated to enumerate 4 leaves.

### Step 7 — Add `deriveTailwindEvent` branch

Add the private branch function in `PilotEvent.kt` adjacent to `deriveCrosswindEvent`. Body mirrors `deriveCrosswindEvent` exactly except for:
- Tailwind helper: `tailwindComponentKnots(...)` instead of `crosswindComponentKnots(...)`.
- Limit field: `aircraft.type.maxTailwindKnots` instead of `aircraft.type.maxCrosswindKnots`.
- Event constructor: `TailwindLimitExceeded(...)` instead of `CrosswindLimitExceeded(...)`.
- KDoc: cite the per-type doctrinal severity (advisory for C172 / hard limit for B738) + FAA AFH Ch 9 high-risk-operations framing.

Mark the branch with `@Suppress("ReturnCount")` matching fn-14's pattern.

### Step 8 — Update `derivePilotEvent` ordering

Replace the body with:
```kotlin
fun derivePilotEvent(
    aircraft: AircraftState,
    mission: PilotMission,
    weather: WindReport?,
): PilotEvent? =
    deriveDecisionAltitudeEvent(aircraft, mission)
        ?: deriveTailwindEvent(aircraft, mission, weather)
        ?: deriveCrosswindEvent(aircraft, mission, weather)
```

Update `derivePilotEvent`'s KDoc to enumerate the three-branch ordering (DA → tailwind → crosswind) and the doctrinal rationale (epic Decision #5):
- DA first — lowest-altitude / hardest-stop trigger (CAP 413 §4.55).
- Tailwind second — physically stronger constraint (touchdown energy, runway remaining, go-around margin); on jet-class types like B738 doctrinally a hard limitation per FCOM Limitations §1.
- Crosswind third — control-authority constraint; demonstrated performance per AC 23-8B (judgement-zone).

When all three predicates simultaneously hold, DA wins; when only tailwind + crosswind hold, tailwind wins; when only crosswind, crosswind. Pinned by ordering tests in fn-15.1.

### Step 9 — Add `applyTailwindGoAround` applier

Add the function in `Pilot.kt` adjacent to `applyCrosswindGoAround`. Body **identical** to `applyCrosswindGoAround`'s body:

```kotlin
@Suppress("UnusedParameter")
internal fun applyTailwindGoAround(
    event: xyz.easiersaid.twr.pilot.observe.PilotEvent.TailwindLimitExceeded,
    mission: PilotMission,
    aircraft: AircraftState,
    now: SimTime,
): GoAroundResult {
    val gaTask = if (mission.navigationMode.getOrNull() is NavigationMode.Instrument) ifrGoAroundTask()
        else goAroundTask()
    val newRoot = mission.root.replaceChild(
        predicate = { it is CompoundTask && !it.isComplete && it.name.isCircuitLike() },
        replacement = CompoundTask(TaskName.CircuitAfterGoAround, listOf(
            gaTask,
            circuitTask(),
        )),
    )
    val updatedMission = mission.resetForGoAround(now).copy(root = newRoot)

    return GoAroundResult(
        intent = PilotIntent(
            targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
            phase = PilotPhase.Final,
            route = PilotRoute.None,
            targetAltitudeM = aircraft.type.circuitPattern.altitudeAglM,
        ),
        mission = updatedMission,
        transmissions = listOf(Report(listOf(ReportEvent.GoingAround))),
    )
}
```

**KDoc** cross-references `applyCrosswindGoAround` as sibling, cites per-type doctrinal severity asymmetry (C172 AFH advisory / B738 FCOM hard limit), documents the anti-decision (no shared core helper — distinct functions for trace readability + future doctrine divergence). Cite FAA AFH Ch 9, ICAO Annex 6 Part II §2.4 (already in `RegulationDatabase` from fn-14).

**Do NOT modify** `applyCrosswindGoAround` or `applySelfInitiatedGoAround` bodies.

### Step 10 — Wire applier into `pilotDecide` (dispatch order: DA → tailwind → crosswind)

Extend the `when` block at `Pilot.kt:178-192`. **Arm order matches `derivePilotEvent` branch order** (codex round-1 cosmetic fix; functionally only one event surfaces per call but visual ordering aids readers):

```kotlin
when (val pilotEvent = xyz.easiersaid.twr.pilot.observe.derivePilotEvent(
    aircraft, cognitive.updatedMission, weather,
)) {
    is xyz.easiersaid.twr.pilot.observe.PilotEvent.DecisionAltitudeWithoutClearance ->
        applySelfInitiatedGoAround(pilotEvent, cognitive.updatedMission, aircraft, input.now)
    is xyz.easiersaid.twr.pilot.observe.PilotEvent.TailwindLimitExceeded ->
        applyTailwindGoAround(pilotEvent, cognitive.updatedMission, aircraft, input.now)
    is xyz.easiersaid.twr.pilot.observe.PilotEvent.CrosswindLimitExceeded ->
        applyCrosswindGoAround(pilotEvent, cognitive.updatedMission, aircraft, input.now)
    is xyz.easiersaid.twr.pilot.observe.PilotEvent.AtcGoAroundOnFinal, null -> null
}
```

Update the surrounding inline comment block at lines 99-128 (the "FOUR GA paths share pilotDecide's fork point" enumeration) to "FIVE GA paths". Lines 165-175 (the "Self-initiated path 3 / fn-14.1" comment) updated to reflect the third leaf in `derivePilotEvent`.

### Step 11 — Add RegulationDatabase entries (codex-tightened to two)

In `protocol/.../RegulationDatabase.kt`, after fn-14's four entries:

```kotlin
/**
 * fn-15.1 (R13): FAA AFH Ch 9 — tailwind landing high-risk operations.
 * Sibling of [FAA_AFH_CH9_CROSSWIND_ERRORS] (fn-14). The principle is the
 * AFH-derived industry-standard advisory anchor for the C172 leaf's
 * `maxTailwindKnots = 10 kt` (per AircraftType.kt KDoc).
 */
val FAA_AFH_CH9_TAILWIND_RISK = RegulationRef(
    document = "FAA_AFH", edition = "FAA-H-8083-3C (2021)", section = "Ch 9 (Tailwind landing)",
    title = "Tailwind landing — high-risk operation",
    principle = "Tailwind landings increase touchdown distance and reduce go-around margin; the operation " +
        "is classified as high-risk; the AFH-derived industry-standard advisory is the modelling anchor " +
        "for pilot reactive go-around when no explicit POH limitation is published",
    category = RegulationCategory.GUIDANCE,
)

/**
 * fn-15.1 (R13): ICAO Doc 4444 §7.11.6 — 5 kt tailwind component limit
 * for reduced runway separation minima. Peer doctrinal anchor for
 * completeness; SCOPE IS DISTINCT from per-aircraft POH/FCOM tailwind
 * limitation (separation minima ≠ aircraft-type tailwind limit). Cited
 * here so that the codebase's tailwind doctrine surface is complete.
 * Symbol name distinct from existing [ICAO4444_7_11] (Post-landing
 * taxi) at line 212.
 */
val ICAO4444_7_11_6_REDUCED_RUNWAY_TAILWIND = RegulationRef(
    document = "ICAO_4444", edition = RegulationRef.ICAO_4444_EDITION, section = "§7.11.6",
    title = "Tailwind 5 kt limit for reduced runway separation minima",
    principle = "Reduced runway separation minima shall not apply when the tailwind component exceeds 5 kt; " +
        "peer doctrinal anchor (separation scope, distinct from POH performance)",
    category = RegulationCategory.GUIDANCE,
)
```

**NOT added** (per codex round-1):
- `POH_TAILWIND_LIMITATION_GENERAL` — manufacturer values are not regulations; per-aircraft sources stay in `AircraftType` KDoc.
- FAR/CS tailwind cert entry — §23.51 / §25.105 / CS-25.105 address takeoff performance broadly, not specifically tailwind certification; no inventing a citation.

### Step 12 — Pilot-side unit tests

Author the five new test files per R10:
- `TailwindHelperTest` (pure math).
- `PilotEventTailwindTest` (recognition matrix; ordering pin between tailwind and crosswind).
- `PilotTailwindGoAroundTest` (applier post-conditions; TouchAndGo variant).
- `PilotTailwindHysteresisTest` (two-cycle suppression via tree rewrite).
- `PilotTailwindTickATickBTest` (integration through `pilotDecide`).

Extend `AircraftTypeSpec` with the `maxTailwindKnots > 0` invariant row.

**Regression check**: run the existing fn-14 spec suite (`CrosswindLimitExceededSpec`, `PilotCrosswindGoAroundTest`, `PilotCrosswindHysteresisTest`, `PilotCrosswindTickATickBTest`, `PilotEventCrosswindTest`, `CrosswindHelperTest`, `SelfInitiatedGoAroundResponseSpec`, `PilotAtcInitiatedGoAroundSpec`) UNCHANGED. The `WindComponents.kt` rename + shared-symbol refactors are zero-behaviour-change.

### Step 13 — Spec-coverage verify

`./gradlew :pilot:jvmTest :protocol:allTests :controller:jvmTest :core:allTests :sim:jvmTest detekt` exits 0. **All eight existing goldens** (G0 / G1 / G1-min / G2 / G3a-trained / G3a-obstruction / G3a-continue / G3a-react-crosswind) stay GREEN. New pilot-side spec files GREEN. detekt baseline unchanged.

## Investigation targets

- **Pre-plan verification done**: Cessna 172S NAV III / 172R POH §2 does NOT publish a hard tailwind limitation. The C172 leaf's `Knots.unsafe(10)` value is AFH-advisory-derived. KDoc explicitly says so.
- **Verify B738 FCOM §1 tailwind value at task time.** Boeing 737-800 FCOM Limitations is the canonical source for 15 kt steady tailwind on dry runway. If the task-time verification finds a different edition value, update both R1 and the C172 sim-test wind speed (R11) proportionally to preserve the test's "above-the-limit" margin.
- **Verify `crosswindComponentKnots` imports** outside `observe/` package — if any file imports the symbol via its qualified path (Crosswind.kt-keyed), the rename keeps it valid (FQN unchanged because package is unchanged), but a grep at task time confirms.
- **Confirm `mission.root.activeCompound()` signature** — fn-14's inline call returns `CompoundTask?`. Lifting to a helper requires the same nullable handling.
- **Confirm `TaskName.isCircuitLike()` predicate** at `PilotMission.kt:790` covers `Circuit`, `CircuitAfterGoAround`, and `TouchAndGo`. fn-14 verified this; the new tailwind applier reuses the predicate.

## Key context

- **Per-type doctrinal severity is real-world load-bearing.** C172 POH does not publish a hard tailwind limit; the 10 kt value is AFH-advisory. B738 FCOM publishes a 15 kt hard limit. The codebase honours the asymmetry via per-leaf KDoc; the field type is shared (`Knots`), the recognition predicate is identical, but the doctrinal anchor cited differs. **No generic "POH = hard limit" framing** (codex round-1 closure).
- **`RegulationDatabase` is for verifiable doctrinal/procedural anchors only.** Manufacturer values are not regulations — they live in `AircraftType` KDoc.
- **Branch ordering is doctrinally motivated (epic Decision #5).** DA → tailwind → crosswind. Not arbitrary — pinned by ordering test. **`pilotDecide`'s `when` arm order matches** for reader clarity.
- **Zero firewall change.** `PilotInput` is unchanged. The reactive sensing channel widened in fn-14; this pass is additive within the existing surface.
- **`tailwindComponentKnots` returns magnitude, not signed value.** Headwind is "0 kt tailwind"; the headwind axis has no operational limit. Asymmetry is operational.
- **Strict `>` boundary discipline.** Match fn-14's crosswind branch — at exactly the limit value, no event; just above, event. No soft tolerance.
- **Anti-decision pinned in KDoc.** No shared `applyReactiveWindGoAround` core helper. Distinct functions for trace readability + doctrine-divergence future-proofing.
- **Shared `WIND_REACTIVE_ELIGIBLE_STEPS` + `isReactiveGoAroundEligible`** are explicit consolidation moves per `feedback_pass_scope.md`. The rename from the crosswind-specific names is a deliberate "second-consumer landed, time to share" moment.
- **Every fn-14 spec stays GREEN unchanged.** The rename + shared-symbol refactors are zero-behaviour-change; regression is the gate.

## Acceptance

- [ ] **R1** — `AircraftType.maxTailwindKnots: Knots` on sealed surface and every leaf. C172 = `Knots.unsafe(10)` (POH §2 does NOT publish a hard tailwind limit — KDoc explicitly states this; value framed as FAA AFH Ch 9 industry-standard advisory). B738 = `Knots.unsafe(15)` (FCOM Limitations §1 hard limitation, edition verified at task time). Field-level KDoc documents per-type doctrinal severity asymmetry + cross-references `maxCrosswindKnots`. **No generic "POH = hard limit" framing.** `AircraftTypeSpec` updated with `maxTailwindKnots > 0` row.
- [ ] **R2** — `pilot/.../observe/Crosswind.kt` renamed to `pilot/.../observe/WindComponents.kt`. File-level KDoc updated to describe both helpers as siblings. Imports across the codebase still resolve (package unchanged). `:pilot:jvmTest` GREEN immediately after the rename, before any new code.
- [ ] **R3** — `tailwindComponentKnots(...): Double` in `WindComponents.kt`. Returns magnitude (positive when tailwind exists, `0.0` when headwind or pure crosswind). KDoc cites: single-frame contract, sign convention, examples, FAA AIM §7-1-12.d.3, True-vs-Magnetic pitfall (cross-ref crosswind helper), cross-references `crosswindComponentKnots`. Unit tests in `TailwindHelperTest.kt` cover R3's case list including boundary tests.
- [ ] **R4** — `PilotEvent.TailwindLimitExceeded(override val aircraft: AircraftId, val componentKnots: Double, val limitKnots: Int, val runway: RunwayId)` added. KDoc cites per-type doctrinal severity asymmetry + pure-derivation axis + per-leaf source. All exhaustive `when (event: PilotEvent)` sites updated with explicit arm (no `else`). File-level KDoc enumerates 4 leaves.
- [ ] **R5** — `derivePilotEvent` body becomes `deriveDecisionAltitudeEvent(...) ?: deriveTailwindEvent(...) ?: deriveCrosswindEvent(...)`. Branches independent — no shared early returns. KDoc enumerates the ordering rationale (DA = hardest stop, tailwind = physically stronger / hard limit per-type, crosswind = control-authority demonstrated).
- [ ] **R6** — `private fun isReactiveGoAroundEligible(mission)` extracted in `PilotEvent.kt`; `deriveCrosswindEvent` updated to call it; `deriveTailwindEvent` calls it too. Behaviour identical to fn-14's inline `isCircuitLike` guard.
- [ ] **R7** — `CROSSWIND_ELIGIBLE_STEPS` renamed to `WIND_REACTIVE_ELIGIBLE_STEPS`. KDoc updated to describe the shared symbol. Both branches read it.
- [ ] **R8** — `applyTailwindGoAround` exists adjacent to `applyCrosswindGoAround` with identical body shape (Tick A intent, subtree replacement via `isCircuitLike`, `resetForGoAround(now)`, `Report(GoingAround)` transmission). KDoc cross-references `applyCrosswindGoAround` and documents the anti-decision. `applyCrosswindGoAround` and `applySelfInitiatedGoAround` bodies unchanged.
- [ ] **R9** — `pilotDecide`'s `when` extended to dispatch `TailwindLimitExceeded → applyTailwindGoAround(...)`. **Arms written in DA → tailwind → crosswind dispatch order** matching `derivePilotEvent` branch order. Surrounding inline comment block updated to "FIVE GA paths".
- [ ] **R10** — Five new pilot-side test files (`TailwindHelperTest`, `PilotEventTailwindTest`, `PilotTailwindGoAroundTest`, `PilotTailwindHysteresisTest`, `PilotTailwindTickATickBTest`) GREEN. Ordering pin: when both `TailwindLimitExceeded` AND `CrosswindLimitExceeded` apply same tick, tailwind fires. Regression check: every fn-14 pilot spec stays GREEN UNCHANGED.
- [ ] **R13** — Two `RegulationDatabase` entries added (codex-tightened):
  - `FAA_AFH_CH9_TAILWIND_RISK` (`category = GUIDANCE`, `document = "FAA_AFH"`).
  - `ICAO4444_7_11_6_REDUCED_RUNWAY_TAILWIND` (`category = GUIDANCE`, `document = "ICAO_4444"` consistent with established convention; symbol name distinct from existing `ICAO4444_7_11`).
  - NOT added: `POH_TAILWIND_LIMITATION_GENERAL` (per codex round-1).
  - NOT added: FAR/CS tailwind cert entry (no specific verifiable clause).
- [ ] **R14** (foundation slice) — `./gradlew :pilot:jvmTest :protocol:allTests :controller:jvmTest :core:allTests :sim:jvmTest detekt` exits 0. Eight existing goldens (G0/G1/G1-min/G2/G3a-trained/G3a-obstruction/G3a-continue/G3a-react-crosswind) stay GREEN. detekt baseline unchanged.

## Notes for fn-15.2

Sim integration test (`G3aPilotReactiveTailwindTest`) + doc cross-references ride on this foundation. fn-15.2's pins are observable behaviour only — `Report(GoingAround)`, commitment stage regression (`LandingClearanceIssued`/`AwaitLandedObserved` → `AwaitDownwind`), no touchdown, recovery landing. Event-counting / hysteresis pins live in fn-15.1 unit tests (R10).

## Done summary

_(filled during implementation)_

## Evidence

_(filled during implementation)_
