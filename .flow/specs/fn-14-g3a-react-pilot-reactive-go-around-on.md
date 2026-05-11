# G3a-react — pilot-reactive go-around on POH crosswind limit exceeded

## Overview

Closes the G3a trilogy. Three reactive-GA paths shipped so far: pilot-trained-mission (fn-11), ATC-mandated-obstruction (fn-12), ATC-mandated-CONTINUE-APPROACH (fn-13). This epic adds the fourth: **pilot-reactive autonomous GA when the world's wind state exceeds the aircraft's POH-derived maximum demonstrated crosswind component**. The pilot reads wind, computes crosswind component against runway heading, decides to GA without ATC instruction, transmits `Report(GoingAround)`, executes the GA via a new pilot-reactive applier (mirroring fn-12.2's pattern), re-enters circuit, and lands when the wind returns within limits.

The novelty is **the first pilot-side reactive recognition driven by world weather**. fn-11 (mission-authored), fn-12 (ATC-issued instruction received), fn-13 (ATC-issued instruction received) all had recognition triggered by mission state or received instruction. G3a-react adds recognition triggered by **world state directly observed via a new pilot sensing channel**. POH limits become first-class typed data on `AircraftType`.

**Scenario:** single AI aircraft at LOWG (C172, POH crosswind = 15 kt), mission `CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop))`. On final, world authors wind shift past C172's 15 kt limit (e.g. wind from 270°M at 20 kt against runway 35C [heading 350°M] → crosswind component = 20 × |sin(270−350)| = 20 × sin(80°) ≈ 19.7 kt > 15 kt). Pilot's recognition fires; `applyCrosswindGoAround` produces the GA effect (Tick A mirrors fn-12.2's reactive pattern: `route = None`, `phase = Final` retained, mission tree → `CircuitAfterGoAround`, transmit `Report(GoingAround)`); existing `GA-PRE-CLEAR` / `GA-POST-CLEAR` interrupts at controller commit-lifecycle handle regression; aircraft GAs, re-enters circuit; world wind returns within limits before the recovery final; aircraft lands.

## Boundaries / non-goals

- **Out: tailwind limit.** POH typically lists `maxTailwindKnots` too (often 10 kt). v1 ships only crosswind. Filed as `D-PASS-g3a-react-tailwind-limit`.
- **Out: gust-component evaluation.** POH "demonstrated crosswind" is **steady-state**. Gust component requires additional reasoning (peak-gust vs averaged). v1 reads steady-state only (`Wind.speedKnots`, ignoring `Wind.gustKnots`). Filed as `D-PASS-g3a-react-gust-evaluation`.
- **Out: wind variability during approach (gust-driven dynamics).** v1 evaluates steady-state at each tick; no temporal averaging or trend reasoning. Filed as `D-PASS-g3a-react-wind-variability-dynamics`.
- **Out: multi-aircraft pilot-reactive crosswind.** Single-aircraft case in v1. The world wind affects all aircraft on that runway — multiple aircraft might GA simultaneously, requiring sequencing handling. Filed as `D-PASS-g3a-react-multi-aircraft-crosswind`.
- **Out: cross-aerodrome G3b-react.** Different aerodrome (LJMB) with different wind state. Reuses the entire fn-14 machinery; just a fixture variation. Filed as `D-PASS-g3b-react-cross-aerodrome-crosswind`.
- **Out: other POH-derived reactive triggers.** Density-altitude, temperature, weight limits, etc. Each is its own POH field + recognition predicate. v1 ships only crosswind. Filed as `D-PASS-g3a-react-other-poh-triggers`.
- **Out: pilot judgement / personal minimums layer.** Real PICs apply margins below the POH demo value based on experience, fatigue, runway condition, etc. v1 uses POH demo value as the trigger directly. Filed as `D-PASS-g3a-react-personal-minimums`.
- **Out: ATIS-broadcast-only wind sensing.** v1 sources wind from world weather observation (real-time visual/instrument modelling). Receiving wind via ATIS broadcast cadence (slower, coarser) is a different sensing path. Filed as `D-PASS-g3a-react-atis-cadence-sensing`.
- **Out: wind migration from `SimState.weatherByAerodrome` to `Aerodrome.weather`.** v1 uses the existing flat-map shape (pre-existing from before `project_rich_world_domain.md` was filed). Migration to the entity-on-aerodrome shape is a separate refactor pass affecting many call sites. Filed as `D-PASS-wind-state-migrate-to-aerodrome`.
- **Out: CAP 413 Edition 24 §4.65/§4.66 renumbering.** docs-scout caught Edition 24 renumbered §4.66→§4.65 (VFR-continue) and §4.67→§4.66 (pilot-initiated GA). fn-11/fn-12 cite the older numbering. Edition reconciliation is a separate cleanup pass. Filed as `D-PASS-cap413-edition-24-reconciliation`.

## Strategy Alignment

Active tracks served by this plan:

- **Runtime simulator** — closes the G3a trilogy at the sim-test level. Four reactive-GA paths now covered:
  1. Self-initiated (existing pre-fn-11; decision-altitude-without-clearance)
  2. Pilot-trained mission (fn-11; CircuitOutcome.GoAround)
  3. ATC-mandated obstruction (fn-12; ARR-GO-AROUND-RUNWAY-OBSTRUCTED rule)
  4. **Pilot-reactive POH crosswind (this epic)**
  Plus CONTINUE APPROACH as the fourth non-GA reactive path (fn-13). Total: quadruple-covered reactive-GA + CA decision space.

## Decision context

### 1. Pilot wind sensing — widen `PilotInput` with `WindReport` projection (high confidence per context-scout)

**Decided.** Add `weatherByAerodrome: Map<AerodromeId, WindReport>` to `PilotInput` at `pilot/.../PilotInput.kt:35-57`. The pilot reads world wind via this new field; the sim wires it from `SimState.weatherByAerodrome` (already exists, of type `Map<AerodromeId, WeatherObservation>`) via `buildPilotInput` at `sim/.../PilotWiring.kt:24-37` — sim projects `obs.wind` into the new field.

**Why `WindReport` not `WeatherObservation`:** `WeatherObservation` (line 286 of ControllerTypes) carries `wind: WindReport, qnh: PressureSetting?, visibility: Int?`. Pilot only needs the wind projection for crosswind recognition. Pressure and visibility are controller-domain. Smaller firewall surface; `WeatherObservation` stays in `:controller`.

**`WindReport` location**: currently lives in `:controller` (`ControllerTypes.kt:267-279` — `sealed interface WindReport { Available(wind: Wind) | NotReported }`). `:pilot` cannot import `:controller`. **Move `WindReport` to `:protocol`** (small refactor; the sealed shape is meaningful for any agent that reads weather). Existing `:controller` consumers continue using it via the protocol import — compile-impact only.

This is a **firewall widening** — `FirewallPilotInputTest` at `pilot/.../FirewallPilotInputTest.kt:31-54` must be updated with the new canonical-constructor entry. Justification per `feedback_firewall_principle.md`: real pilots read wind via windsock + ASI crosscheck + instrument + ATIS (multiple channels). World-weather-via-`PilotInput` models the visual/instrument sensing path. The firewall test's role is to ensure new fields are deliberate and documented; this addition is.

**Aerodrome-key resolution**: a new internal helper `windForMission(mission, weatherByAerodrome): WindReport?` colocated in `Pilot.kt` (so `pilotDecide` can call it — top-level `private` is file-private) mirrors `atisLetterForCallInbound` (`PilotCognitive.kt:478-500`) in *shape*: goal-peek (`Transit.destination`, `Departure.destination`) for key lookup, `Arrival` and `CircuitTraining` → singleton fallback (mirroring the existing helper, NOT using `Arrival.from` which is the origin). **Differs from `atisLetterForCallInbound` in one place**: returns `null` on multi-aerodrome ambiguity rather than `error()` — the crosswind lookup runs every pilot decision cycle and must not crash unrelated multi-aerodrome scenarios. Multi-aerodrome G3b-react is the deferred sibling.

Alternative considered + rejected: read `Atis.wind` from existing `PilotInput.atisByAerodrome` (no firewall change). Rejected because ATIS cadence is coarse (broadcast cycles every 30-60 min in real ops, fewer ticks in sim) — wind shifts within an ATIS letter are invisible. The G3a-react scenario requires perceiving the shift; ATIS-only would miss it. ATIS-cadence sensing filed as deferment `D-PASS-g3a-react-atis-cadence-sensing`.

### 2. `AircraftType.maxCrosswindKnots: Knots` POH-derived field (high confidence)

**Decided.** Extend the sealed `AircraftType` class at `protocol/.../AircraftType.kt:44-77` with a new abstract field `maxCrosswindKnots: Knots`. Update both `data object` leaves (`C172`, `B738`) with POH-cited values:

- **`C172`**: `maxCrosswindKnots = Knots.unsafe(15)` — POH (Cessna 172S NAV III, current): *"Maximum demonstrated crosswind velocity is 15 knots (not a limitation)"*. KDoc cites POH Section 2 + edition.
- **`B738`**: `maxCrosswindKnots = Knots.unsafe(33)` — Boeing 737-800 FCOM: 33 kt steady crosswind limit (dry/grooved). KDoc cites FCOM.

Other POH values from docs-scout (for future leaves or test fixtures): Cherokee/PA-28 = 17 kt, Cirrus SR22 = 20 kt, Diamond DA40 = 25 kt.

**`Knots` reuse**: `Knots` already exists at `protocol/.../Instruction.kt:80` as a private-constructor positive-smart type (`> 0`). Every POH crosswind limit is ≥ 1 kt — reuse the existing type. Use `Knots.unsafe(N)` for compile-time-known constants (matches existing pattern).

**Doctrinal note** (per docs-scout + practice-scout): POH "demonstrated crosswind" is **performance information, NOT a limitation** in the certification sense (14 CFR §23.233, AC 23-8B). FAA AFH Chapter 9 lists "attempting a landing in crosswinds that exceed the airplane's maximum demonstrated crosswind component" as **Common Error #1** for crosswind approaches. Modelling decision: in this sim, a competent VFR pilot **does** GA when reported crosswind exceeds the type's demonstrated value. This is the correct *modelling* choice even though it overstates real-world strictness (real PICs sometimes attempt and succeed beyond). The deferment register has `D-PASS-g3a-react-personal-minimums` covering the judgement layer.

### 3. Crosswind-component helper + RunwayId heading — fail-closed parse, Double-precision compare (high confidence)

**Decided.** Two new pure functions:

- `RunwayId.headingDegreesMagnetic(): Int?` extension in `:protocol`. Returns `Int?` — `null` when first two chars are not parseable OR the parsed value is outside `1..36` (real runway designators). Pattern from `RunwayAssessment.kt:402-409` lifted to a typed helper with range validation. **Pilot recognition fails closed on null** (no silent default-to-zero, no silent acceptance of `00`/`37`/`99`). No corners cut.

- `crosswindComponentKnots(windFromMagnetic: Int, windSpeedKnots: Int, runwayHeadingMagnetic: Int): Double` pure function in `:pilot/observe/Crosswind.kt`. Formula (per practice-scout, FAA AIM, ICAO Annex 3):
  ```
  θ = ((windFromMagnetic - runwayHeadingMagnetic + 540) mod 360) - 180   // wrap to [-180, 180]
  crosswind = |sin(θ_radians)| × windSpeedKnots
  ```
  **Return Double — no truncation.** A crosswind of `15.9` kt against a 15 kt limit must fire the recognition; truncating to `15` would silently mask the exceedance. Caller compares to `aircraftType.maxCrosswindKnots.value.toDouble()`. The existing positive-only `Knots` type is not appropriate here — `0.0` is a valid crosswind (dead headwind), and we never persist the value as a typed `Knots`; we only compare it.

  Same-reference-frame requirement (both Magnetic): document loudly in KDoc per practice-scout's True-vs-Magnetic pitfall.

**Wind direction convention** (pinned per docs-scout + FAA AIM §7-1-12.d.3):
- ATIS / ATC voice broadcasts: **Magnetic, FROM**
- METAR / TAF / printed: **True, FROM**
- Runway numbers: **Magnetic**
- twr2's `Wind.directionDegrees` (in `protocol/.../Instruction.kt:123-147`): assume **Magnetic** (matches the ATIS/ATC sensing path). Document this in `Wind`'s KDoc as a v1 pin.

**VRB handling**: docs-scout flagged that METAR `VRB` (variable direction) is used when speed ≤ 6 kt or direction shifts > 60° in low wind. v1 treats `Wind` direction as always defined (no VRB flag in current type). If a future scenario needs VRB, add `Wind.variable: Boolean` field + handle as crosswind=0 (the operational reality at those speeds). Filed as `D-PASS-g3a-react-vrb-handling`.

### 4. `PilotEvent.CrosswindLimitExceeded` leaf + split `derivePilotEvent` branches (high confidence)

**Decided.** Add new sealed leaf:
```kotlin
data class CrosswindLimitExceeded(
    override val aircraft: AircraftId,
    val componentKnots: Double,
    val limitKnots: Int,
    val runway: RunwayId,
) : PilotEvent
```
`componentKnots` is `Double` (precise computed value); `limitKnots` is `Int` (the POH value). Runway included for trace readability.

**Recognition lives in `derivePilotEvent`**, NOT `pilotDecide` flag-driven path. The trigger is a kinematic predicate over `(aircraft, mission, world weather, runway)` — a pure derivation, no instruction-processing involved. Mirrors the existing `DecisionAltitudeWithoutClearance` recognition shape (same axis).

**Signature change**: extend `derivePilotEvent(aircraft, mission) → derivePilotEvent(aircraft, mission, weather: WindReport?)`. Read `aircraft.type.maxCrosswindKnots` inside the crosswind branch (do NOT take `aircraftType` as a separate parameter — `AircraftState` already carries `type`; duplicating it would allow tests to pass mismatched type vs aircraft and produce impossible runtime behavior). Verify all call sites at task time; the only caller is `pilotDecide` at `Pilot.kt:150-157` per context-scout.

**Branches MUST be independent — no shared early returns.** The existing DA branch has DA-specific gates (`!onApproach || mission.hasClearance → return null`); the crosswind branch does **not** apply those gates (FAA AFH Ch 9: pilot has authority for crosswind GA regardless of clearance state). Each event derived with its own guard set:

- **DA branch (existing):** unchanged — fires when `onApproach && !mission.hasClearance && altitude <= DECISION_ALTITUDE_M` (inclusive boundary, matching existing `aircraft.altitudeM > DECISION_ALTITUDE_M → null` behavior). Preserve the inclusive `<=` exactly.
- **Crosswind branch (new):**
  1. `aircraft.phase is PilotPhase.Final` (only on final, not Climbing/Cruise)
  2. mission `currentStep ∈ {FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}` — crosswind is NOT clearance-gated
  3. `weather is WindReport.Available` (null/`NotReported` → fail closed)
  4. `mission.activeRunway` is `Some`
  5. `runway.runway.headingDegreesMagnetic() != null` (parse failure → fail closed per Decision #3)
  6. compute crosswind via the helper
  7. `component > aircraft.type.maxCrosswindKnots.value.toDouble()` → emit `CrosswindLimitExceeded(aircraft.id, component, limit, runway)`

Branch-evaluation order within `derivePilotEvent`: DA first (lower altitude takes priority), then crosswind. Mutually exclusive in practice — DA fires below clearance height while uncleared; crosswind fires while on final.

**Fail-closed direction**: any missing input → no event → no GA fire. Conservative.

### 5. `applyCrosswindGoAround` — distinct applier mirroring fn-12.2 reactive pattern (high confidence)

**Decided.** Add a new applier `applyCrosswindGoAround(event, mission, aircraft, now)` adjacent to `applySelfInitiatedGoAround` at `Pilot.kt:687-718`. **Body mirrors fn-12.2's reactive-GA Tick A pattern**:

- `route = PilotRoute.None`
- `phase` retained as `Final` (NOT Climbing — preserves reactive-GA semantics; the regression-to-Climbing happens via mission-tree rewrite + `goAroundTask` execution)
- mission tree subtree-replaced **inline** (mirror `applySelfInitiatedGoAround:696-705`'s pattern; use `TaskName.isCircuitLike()` predicate at `PilotMission.kt:790` so the rewrite covers `Circuit`, `CircuitAfterGoAround`, **and `TouchAndGo`**). There is NO `planCircuitAfterGoAround` helper — replicate the inline shape.
- `mission.resetForGoAround(now)` — clears `hasClearance`, `activeConstraints`, etc. The `now: SimTime` parameter is part of the applier signature.
- transmit `Report(GoingAround)`

**Do NOT modify `applySelfInitiatedGoAround`** — its DA-triggered behavior (`phase = Climbing`, retain route) stays exactly as today. The two appliers produce **different Tick A effects** because the aerodynamic state at trigger time differs: DA-without-clearance fires near the runway threshold with the aircraft fully configured for landing (transition to Climbing makes sense); crosswind-exceeded can fire higher up on final where retaining `Final` phase + relying on the mission-tree-driven `goAroundTask` for the kinematic transition is more honest.

**Anti-decision**: do NOT extract a shared `applyPilotInitiatedGoAround` core helper. The two paths differ in their Tick A intent; a shared body would either silently change the DA path's behavior (rejected per codex review) or require parameterising the effect (over-abstraction for two call sites). Keep them as distinct functions; cross-reference via KDoc.

If `applyReactiveGoAround` (fn-12.2 ATC-issued path) exists pilot-side and is shape-compatible with `applyCrosswindGoAround`, prefer factoring a single body and have both reactive-GA callers invoke it. Verify at task time.

### 6. `pilotDecide` precedence — append fourth path (high confidence)

**Decided.** Existing three-way precedence at `Pilot.kt:115-148` (after fn-11/fn-12.2): trained-GA → ATC-reactive (flag-driven) → self-initiated (via `derivePilotEvent`). G3a-react extends `derivePilotEvent` to also recognise `CrosswindLimitExceeded` — meaning **the new event flows through the existing self-initiated arm**. Precedence becomes:

1. Trained-GA recognition (existing fn-11.1 path; `preStep == FLY_FINAL_TO_SHORT_FINAL && currentStep == GOING_AROUND`)
2. ATC-reactive flag (existing fn-12.2 path; `mission.pendingAtcGoAroundFrom is Some`)
3. **Self-initiated `derivePilotEvent`** (existing path, NOW returning either `DecisionAltitudeWithoutClearance` OR new `CrosswindLimitExceeded`)

Within #3, the new event dispatches to `applyCrosswindGoAround`; the existing event dispatches to `applySelfInitiatedGoAround`.

**Precedence rationale (per `ga-path-precedence-reorder-when-adding-2026-05-10` memory)**: trained-GA wins if mission tree authored the GA (highest priority — explicit pilot intent). ATC-reactive wins next if flag is set (ATC instruction received). Self-initiated last, including both DA-without-clearance AND crosswind triggers. If multiple self-initiated triggers fire same tick (e.g., crosswind AND DA-without-clearance), `derivePilotEvent`'s within-function ordering (DA first) decides. Add explicit ordering test.

### 7. Controller-side: no behavior changes; compile-impact only for `WindReport` relocation (high confidence — context-scout verified)

**Decided.** No controller behavior changes. `GA-PRE-CLEAR` and `GA-POST-CLEAR` `ProcedureInterrupt`s at `TowerArrival.kt:378-392` already fire on `guard = GoAroundEvent`, which derives from `ControllerEvent.GoAroundDetected`, which derives from any `Report(GoingAround)` regardless of trigger source. The controller sees the pilot's GA transmission and regresses the commitment from `LandingClearanceIssued`/`AwaitLandedObserved` to `AwaitDownwind` automatically. No new rule, no new event, no new instruction.

**Compile-impact only** for the controller: when `WindReport` moves from `:controller` to `:protocol` (Decision #1), existing `:controller` consumers re-import. `WeatherObservation` stays in `:controller`. No semantic change.

The pilot autonomously transmits `Report(GoingAround)` without requesting ATC permission (per CAP 413 §4.67 / ICAO Doc 4444 §12.3.4.18 — pilot has standalone phraseology authority).

### 8. No-refire / hysteresis (high confidence — refined per practice-scout)

**Decided.** v1 hysteresis: **one GA decision per "approach attempt"** — once `applyCrosswindGoAround` fires, the mission tree rewrites to `CircuitAfterGoAround`. The recognition predicate gates on `mission.currentStep ∈ {FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}`; after the rewrite, the current step is outside this set, so `derivePilotEvent` returns null for crosswind on subsequent decision cycles **regardless of whether wind still exceeds limit**.

No new witness on Commitment, no flag on `PilotMission`. The mission-tree rewrite IS the suppression — distinct from fn-12.2 (where `pendingAtcGoAroundFrom` flag was needed because the ATC instruction arrived asynchronously) and fn-13.1 (where `continueApproachIssuedThisAttempt` witness was needed because CONTINUE APPROACH does NOT rewrite the mission tree).

If wind stays past limit on the recovery circuit's final → recognition fires again on that next attempt → second GA. Practice-scout's "bouncing at the limit" pitfall is mitigated by: (a) the mission-tree rewrite IS hard hysteresis per attempt; (b) per-tick eval evaluates fresh wind each tick — within one attempt, multiple "true" ticks before the first GA are folded by step-set predicate gate. Hysteresis test in fn-14.1: two consecutive decision cycles with crosswind > limit → first emits event + rewrites tree; second emits zero events.

### 9. Sim test trigger — world-only authorship (locked per `feedback_world_only_test_triggers.md`)

**Decided.** Test fixture authors `state.weatherByAerodrome[lowg]` shift via `onAfterEvent` hook (fn-12.3's `RunUntil.kt`-equivalent mechanism). One-shot guard pattern from fn-12.3 / fn-13.2. Authorship predicate:

- aircraft is on final geometry (`OnApproach` OR `OnCircuitLeg(FINAL)`)
- AFTER `ClearedToLand` issued (exercises the GA-POST-CLEAR interrupt path, matching G3a-trained / G3a-obstruction shape)
- new wind state has crosswind component > C172's 15 kt against active runway

Mid-scenario, world wind returns within limits (second authored shift) before recovery circuit's final, so the aircraft can land. One-shot guards `var crosswindAuthored = false; var crosswindClearedToLimit = false` cover both transitions.

**Forbidden** (per `feedback_world_only_test_triggers.md`): no `PilotEvent.CrosswindLimitExceeded` injection, no direct `PilotInput.weatherByAerodrome` mutation outside the sim wiring, no `mission` mutation bypassing the recognition → apply pipeline.

### 10. Sim test pin discipline (per `sim-test-pins-must-compare-against-2026-05-10` memory)

**Decided.** Decision-cycle pins use `findEmittingCycleMs` / `nextTransmissionId` mint-id walk from fn-12.3, NOT `txStart`. World-state weather transitions via aerodrome-keyed extractor `weatherTransitions(aerodromeId)` over `SimState.weatherByAerodrome` (add to `SimTraceQueries.kt` if not present). **NOT** a controller-belief-slice projection — weather lives at `ControllerView.weather` / world state; unlike runway obstructions, controller reaction is not belief-gated on weather. Same-cycle ordering: `≤` on `SimTime.millis` with mint-id sequence tiebreak; strict `<` only across cycles.

**No event-count pins in the sim test.** Per codex review issue #8, "exactly ONE `PilotEvent.CrosswindLimitExceeded`" pins live in fn-14.1 pilot unit tests (where the pilot trace surface exists naturally). The sim test asserts only externally observable behavior: wind shift transition, `Report(GoingAround)` transmission, commitment regression, no touchdown, recovery landing.

## Acceptance

- **R1:** `AircraftType.maxCrosswindKnots: Knots` field added on sealed surface and every leaf. C172 = `Knots.unsafe(15)` with POH cite; B738 = `Knots.unsafe(33)` with FCOM cite. Reuse existing `Knots` positive-smart type from `Instruction.kt:80`. `AircraftTypeSpec.kt:88-148` invariant tests updated with `maxCrosswindKnots > 0` row.
- **R2:** `WindReport` sealed interface moved from `:controller` (`ControllerTypes.kt:267-279`) to new `:protocol/WindReport.kt`. All `:controller`, `:sim`, test consumers re-import. `WeatherObservation` stays in `:controller`. Behavior unchanged.
- **R3:** `PilotInput.weatherByAerodrome: Map<AerodromeId, WindReport> = emptyMap()` field added at `pilot/.../PilotInput.kt:35-57`. Default-empty preserves existing call sites. `FirewallPilotInputTest` updated with the new canonical-constructor entry + justification comment.
- **R4:** `sim/.../PilotWiring.kt:24-37` `buildPilotInput` extended to project `state.weatherByAerodrome.mapValues { (_, obs) -> obs.wind }` into the new field. Existing call sites unchanged (additive).
- **R5:** `RunwayId.headingDegreesMagnetic(): Int?` extension function added in `:protocol`. Returns null on parse failure OR when designator is outside `1..36`. Pilot recognition fails closed on null. Unit tests cover `27 → 270`, `36L → 360`, `01R → 010`, `00 → null`, `37 → null`, `99 → null`, `HX → null`, empty → null.
- **R6:** `crosswindComponentKnots(windFromMagnetic: Int, windSpeedKnots: Int, runwayHeadingMagnetic: Int): Double` pure function added in `:pilot/observe/Crosswind.kt`. Returns Double, no truncation. KDoc: same-reference-frame requirement (both Magnetic), True-vs-Magnetic pitfall warning, FAA AIM §7-1-12.d.3 cite. Unit tests cover dead-headwind = 0.0, pure-crosswind = full speed, 45° ≈ 0.707 × speed, wraparound (350° vs 010°), zero-speed = 0.0.
- **R7:** `Wind.directionDegrees` KDoc updated at `protocol/.../Instruction.kt:123-147` to explicitly pin **Magnetic FROM-degrees**. True-vs-Magnetic convention warning. Cite FAA AIM §7-1-12.d.3.
- **R8:** `PilotEvent.CrosswindLimitExceeded(override val aircraft: AircraftId, val componentKnots: Double, val limitKnots: Int, val runway: RunwayId) : PilotEvent` leaf added at `pilot/.../observe/PilotEvent.kt:57-92`. Audit all exhaustive `when (event: PilotEvent)` sites — add explicit arms (NO `else`).
- **R9:** `derivePilotEvent` signature extended at `pilot/.../observe/PilotEvent.kt:120-137`:
  ```
  derivePilotEvent(aircraft, mission) → derivePilotEvent(aircraft, mission, weather: WindReport?)
  ```
  Reads `aircraft.type.maxCrosswindKnots` inside (no separate `aircraftType` parameter — avoids test-only mismatched-type scenarios). **Branches independent — no shared early returns.** DA branch unchanged; new crosswind branch fires regardless of `mission.hasClearance` for steps in `{FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}`. Unit tests cover not-on-final, weather-null, weather-NotReported, runway-parse-fail, wind-within-limit (all return null); crosswind-exceeded returns event. Call site at `Pilot.kt:150-157` updated to pass `weather = windForMission(mission, input.weatherByAerodrome)`.
- **R10:** `applyCrosswindGoAround(event: PilotEvent.CrosswindLimitExceeded, mission, aircraft, now: SimTime)` added in `Pilot.kt` adjacent to `applySelfInitiatedGoAround`. Body mirrors fn-12.2 reactive-GA Tick A intent (`route = None`, `phase = Final` retained) combined with the inline subtree-replacement pattern from `applySelfInitiatedGoAround:696-705`, using `TaskName.isCircuitLike()` so the rewrite supports `TouchAndGo` in addition to `Circuit`/`CircuitAfterGoAround`. Calls `mission.resetForGoAround(now)` to clear `hasClearance` + constraints. Transmits `Report(GoingAround)`. `applySelfInitiatedGoAround` body **unchanged**.
- **R11:** Pilot-side unit tests in new `pilot/.../observe/CrosswindLimitExceededSpec.kt` (mirror `SelfInitiatedGoAroundResponseSpec.kt` shape) + extension to `PilotEventDerivationSpec.kt`:
  - Recognition fires when `crosswindComponent > maxCrosswindKnots` AND aircraft on final
  - Recognition does NOT fire when crosswind ≤ limit
  - Recognition does NOT fire when aircraft not on final
  - Recognition does NOT fire when weather is null / NotReported (fail-closed)
  - Recognition does NOT fire when runway heading parse fails (fail-closed)
  - Recognition uses Magnetic frame (boundary test with wind 360° vs 0° wraparound)
  - Tick A produces correct GA effect (route=None, phase=Final retained, Report(GoingAround) emitted)
  - Mission tree rewritten to `CircuitAfterGoAround`
  - Existing `SelfInitiatedGoAroundResponseSpec` passes UNCHANGED (regression check)
  - Recognition ordering pin: when BOTH `DecisionAltitudeWithoutClearance` AND `CrosswindLimitExceeded` apply same tick, DA fires first (existing precedence)
  - **Hysteresis test (`PilotCrosswindHysteresisTest`)**: first decision cycle with crosswind > limit emits exactly one `CrosswindLimitExceeded` AND rewrites mission tree; second cycle with same crosswind state emits zero events because step no longer in recognition set.
- **R12:** Sim test `G3aPilotReactiveCrosswindTest.kt` at `sim/src/jvmTest/.../sim/`:
  - Single-aircraft LOWG, mission `CircuitTraining(outcomes = listOf(FullStop))`, aircraft type `C172` (15 kt POH crosswind)
  - World authors wind shift past 15 kt crosswind component via `onAfterEvent` hook + one-shot guard at sim time `T_wind` (when aircraft on final, post-`ClearedToLand` to match G3a-trained / G3a-obstruction shape)
  - Authorship predicate validated (FAIL LOUDLY if preconditions don't hold by some sim tick — test setup error)
  - World authors wind RETURN within limits before recovery circuit's final (second one-shot)
  - Three-layer pin pattern with decision-cycle timestamps (per `sim-test-pins-must-compare-against-2026-05-10`):
    - Layer 1 (causal partial-order): wind-shift event time ≤ `Report(GoingAround)` decisionTime ≤ commitment regression to `AwaitDownwind` < recovery `ClearedToLand` < `Report(RunwayVacated)`. Use `≤` on `SimTime.millis` plus mint-id sequence for same-cycle events; strict `<` only across cycles.
    - Layer 2 (sticky-witness regression): commitment regresses from `LandingClearanceIssued` (or `AwaitLandedObserved` depending on stage at wind-shift time) to `AwaitDownwind` via `GA-POST-CLEAR` interrupt
    - Layer 3 (kinematic non-event): no `LandingRoll` phase before `Report(GoingAround)`; aircraft does NOT touch down on the GA'd approach
  - Vacate-coordination closure pin per fn-8.3
  - Time band ±15% on observed wall
  - World-state weather transition pin: `SimState.weatherByAerodrome` transitions for the active aerodrome. Add `weatherTransitions(aerodromeId)` extractor to `SimTraceQueries.kt` if not present. **Aerodrome-keyed only — NO controller-belief-slice projection** (weather lives at `ControllerView.weather`, not as a `BeliefState` projection; the GA is pilot-side, so controller observability does not need expansion).
  - **No event-count pin** on `PilotEvent.CrosswindLimitExceeded` in this sim test — that pin lives in fn-14.1's pilot unit tests (per codex review issue #8).
- **R13:** Cross-reference doc updates per docs-gap-scout findings:
  - `AGENTS.md` § Golden tests — add G3a-react bullet (8 tests total)
  - `STRATEGY.md` § Runtime simulator track — quadruple-covered approach decision space + complete G3a trilogy note
  - `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` § Resolution — add fn-14 closure paragraph for the four-path reactive-GA coverage
  - `wiki/design-decisions/2026-04-15-controller-architecture.md` — note four-path reactive-GA surface (no controller behavior change; this is pilot-side; compile-impact for `WindReport` relocation only)
  - `wiki/domain/aviation-world.md` — add `AircraftType` section documenting `maxCrosswindKnots` POH-derived field
  - `pilot/.../Pilot.kt` inline comment block at lines 99-150 — update three-path enumeration to four
  - `pilot/.../observe/PilotEvent.kt` file-level KDoc — update "Current leaf set (2 leaves)" to 3 leaves; add `CrosswindLimitExceeded` entry with POH citation
  - `protocol/.../AircraftType.kt` file-level KDoc + per-leaf KDocs — `maxCrosswindKnots` POH source citation
  - `sim/.../testing/Fixtures.kt` LOWG provenance — add G3a-react consumer
  - Sibling test class docstrings (LowgGoldenTest, G1TwoAircraftCircuitsTest, G1TwoAircraftMinimalSpec, G2CrossAerodromeVfrTest, G3aPilotTrainedGoAroundTest, G3aRunwayObstructionTest, G3aRunwayObstructionContinueApproachTest) — `@see G3aPilotReactiveCrosswindTest` cross-ref
- **R14:** RegulationDatabase entries added at `protocol/.../RegulationDatabase.kt`:
  - `FAA_AFH_CH9_CROSSWIND_ERRORS` — FAA-H-8083-3C Ch 9, "Common Errors #1" (attempting landing in crosswinds exceeding max demonstrated)
  - `FAA_FAR_23_233_CROSSWIND_CERT` — 14 CFR §23.233(a) (pre-Amendment 64), 0.2 VSO certification floor
  - `ICAO_ANNEX_6_PII_2_4_PIC` — ICAO Annex 6 Part II §2.4 PIC final authority (or Part I §4.5.1 if scope is broader)
  - `FAA_AIM_7_1_12_WIND_MAGNETIC` — AIM §7-1-12.d.3 ATC-voice winds in Magnetic degrees
- **R15:** `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` exits 0. **All eight golden tests** (G0 / G1 / G1-min / G2 / G3a-trained / G3a-obstruction / G3a-obstruction-continue-approach / G3a-react) GREEN. detekt baseline unchanged.
  - **fn-14.1 verify**: seven existing goldens STAY GREEN. New `CrosswindLimitExceededSpec` + `PilotCrosswindHysteresisTest` + `AircraftTypeSpec` updates GREEN. `SelfInitiatedGoAroundResponseSpec` UNCHANGED (regression check).
  - **fn-14.2 verify**: all eight goldens GREEN. `G3aPilotReactiveCrosswindTest` GREEN.

## Strategy drift flagged for review

_(none — plan aligns with Runtime simulator track and closes the G3a trilogy.)_

## Quick commands

```bash
./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
./gradlew :sim:jvmTest --tests "xyz.easiersaid.twr.sim.G3aPilotReactiveCrosswindTest"
./gradlew :pilot:jvmTest --tests "xyz.easiersaid.twr.pilot.observe.CrosswindLimitExceededSpec"
```

## Approach

### Two-task split

1. **Task .1 — Foundation:** `AircraftType.maxCrosswindKnots` (reusing existing `Knots`) + `WindReport` migration to `:protocol` + `PilotInput.weatherByAerodrome: Map<AerodromeId, WindReport>` widening + `FirewallPilotInputTest` update + `PilotWiring` wiring + `RunwayId.headingDegreesMagnetic(): Int?` + `crosswindComponentKnots(...): Double` helper + `Wind` KDoc convention pin + `PilotEvent.CrosswindLimitExceeded` leaf + `derivePilotEvent` split branches + `windForMission` aerodrome-key helper + `applyCrosswindGoAround` distinct applier + pilot-side unit tests + hysteresis test + RegulationDatabase entries. Existing G0-G3a goldens + fn-12/fn-13 sim tests stay GREEN.
2. **Task .2 — Sim test + cross-references:** `G3aPilotReactiveCrosswindTest.kt` end-to-end + 10 doc-update edits + 7 sibling test class docstrings + closes the epic.

### Reuse points (file:line refs)

| Surface | Reuse | New code |
|---------|-------|----------|
| `AircraftType` sealed registry | `protocol/.../AircraftType.kt:44-268` (exists) | Add `maxCrosswindKnots: Knots` field + leaf values |
| `Knots` value class | `protocol/.../Instruction.kt:80` (exists, positive-only) | Reuse for `maxCrosswindKnots` (always ≥ 1) |
| `Wind` data class | `protocol/.../Instruction.kt:123-147` (exists) | KDoc convention pin only |
| `WindReport` sealed interface | `controller/.../ControllerTypes.kt:267-279` (exists) | Move to `:protocol` |
| `WeatherObservation` | `controller/.../ControllerTypes.kt:286` (exists) | Stays in `:controller`; pilot uses only `WindReport` projection |
| `PilotInput` | `pilot/.../PilotInput.kt:35-57` (exists) | Add `weatherByAerodrome: Map<AerodromeId, WindReport>` field |
| `FirewallPilotInputTest` | `pilot/.../FirewallPilotInputTest.kt:31-54` (exists) | Update canonical-constructor allowlist |
| `buildPilotInput` | `sim/.../PilotWiring.kt:24-37` (exists) | Thread `weatherByAerodrome` (project wind only) |
| `SimState.weatherByAerodrome` | `sim/.../SimState.kt:68` (exists, world-truth) | Read by PilotWiring (no shape change) |
| Runway heading parse | `RunwayAssessment.kt:402-409` (existing pattern) | Lift to typed `RunwayId.headingDegreesMagnetic(): Int?` (fail-closed) |
| Crosswind math | NEW | `crosswindComponentKnots(...): Double` pure function |
| Aerodrome-key resolution | `PilotCognitive.kt:478-500` `atisLetterForCallInbound` pattern | Mirror as `windForMission` private helper |
| `PilotEvent` sealed | `pilot/.../observe/PilotEvent.kt:57-92` (exists) | Add `CrosswindLimitExceeded` leaf |
| `derivePilotEvent` | `pilot/.../observe/PilotEvent.kt:120-137` (exists) | Extend signature + add independent crosswind branch |
| `applySelfInitiatedGoAround` (DA) | `pilot/.../Pilot.kt:687-718` (exists) | UNCHANGED — distinct from new applier |
| `applyCrosswindGoAround` (new) | NEW (mirrors fn-12.2 reactive pattern) | Distinct applier; `phase = Final` retained, `route = None` |
| `pilotDecide` precedence | `pilot/.../Pilot.kt:115-148` (exists) | New event flows through self-initiated arm; no precedence restructure |
| Controller-side reaction | `controller/.../procedure/TowerArrival.kt:378-392` (`GA-PRE-CLEAR`/`GA-POST-CLEAR`) | UNCHANGED behavior (trigger-agnostic); compile-impact only for `WindReport` re-import |
| Sim test harness | `G3aRunwayObstructionTest.kt` (fn-12.3) + `G3aRunwayObstructionContinueApproachTest.kt` (fn-13.2) | Mirror structure |
| World wind mutation | `SimState.weatherByAerodrome` flat map | Trivial `st.copy(weatherByAerodrome = ...)` mutation per onAfterEvent tick |
| Trace extractor | `SimTraceQueries.kt` (fn-12.1's `runwayObstructionTransitions`) | Add `weatherTransitions(aerodromeId)` (aerodrome-keyed only; NO controllerId — world-state surface, not controller belief slice) |
| GA test pattern | `SelfInitiatedGoAroundResponseSpec.kt` | Mirror as `CrosswindLimitExceededSpec.kt` |
| Regulation pattern | `RegulationDatabase.kt` (fn-12, fn-13 entries) | Add FAA AFH / FAR 23.233 / Annex 6 / AIM entries |

## Test notes

The sim test (Task .2) follows the **three-layer pin pattern** from fn-11.2 / fn-12.3 / fn-13.2:

- **Layer 1 (causal partial-order)** — decision-cycle pins via `findEmittingCycleMs` mint-id walk. Same-cycle events use `≤` on `SimTime.millis` plus mint-id sequence tiebreak; strict `<` only across cycles:
  ```
  Weather_shift.decisionTime
      ≤ Report(GoingAround).decisionTime                     // same-cycle OK: pilot recognises + transmits the tick wind updates
      ≤ Stage_regression(LandingClearanceIssued|AwaitLandedObserved → AwaitDownwind).time  // GA-POST-CLEAR fires on Report(GoingAround)
      < Weather_return.decisionTime                          // separate cycle: world wind returns within limits
      < ClearedToLand_recovery.decisionTime                  // separate cycle: pre-clearance gate ungates
      < Report(RunwayVacated).decisionTime
  ```
- **Layer 2 (sticky-witness regression)** — exactly one stage transition `<from-stage> → AwaitDownwind` via `GA-POST-CLEAR` (fn-8 mechanism). Sticky-witness reset (per fn-8.3): `touchedDownDuringCommitment`, `pilotReadyDuringCommitment`, `observedReportsDuringCommitment` all reset on regression.
- **Layer 3 (kinematic non-event)** — no `LandingRoll` phase before `Report(GoingAround)`; aircraft did NOT touch down on the GA'd approach.

**World-weather transition pin**: extend `SimTraceQueries.kt` with `weatherTransitions(aerodromeId: AerodromeId)` extractor (aerodrome-keyed only; NO controllerId) over `SimState.weatherByAerodrome`. Assert exactly two transitions: (1) wind crosses past limit (triggers GA), (2) wind returns within limits (enables recovery landing). No controller-belief weather slice — weather lives at world-state / `ControllerView.weather` already.

**No event-count pin in sim test** (per codex review issue #8) — that pin lives in fn-14.1 pilot unit tests.

**Vacate-coordination closure pin** (fn-8.3 R7-style): no leftover ledger entries after vacate.

**Time band ±15%** on observed wall (calibrate first GREEN; expected ~1300-1500 sim seconds for single circuit + GA + recovery circuit).

## Review considerations

### FP / type-safety axis
- New `PilotEvent` leaf requires explicit no-op arms in every existing exhaustive `when` (no `else`).
- `WindReport` migration is move-only (no shape change); existing exhaustiveness preserved.
- `crosswindComponentKnots` is pure; total over its input domain (angle is always normalisable); returns Double.
- `Knots` value class is type-safe (no raw int leaks for the POH limit field).
- `RunwayId.headingDegreesMagnetic(): Int?` fails closed on parse failure; pilot recognition treats null as no-event.
- Fail-closed semantics on the recognition predicate: any null input → no event → no GA.
- `applyCrosswindGoAround` is distinct from `applySelfInitiatedGoAround` (different Tick A semantics; no shared core helper — over-abstraction rejected per codex review).

### Test architecture axis
- Three-layer pin pattern with decision-cycle timestamps.
- Pilot-side unit tests cover full discriminator matrix incl. wind frame-of-reference (Magnetic vs True boundary test) and parse-failure fail-closed.
- World-only test trigger discipline.
- Regression: `SelfInitiatedGoAroundResponseSpec` passes UNCHANGED.
- Recognition ordering: when both DA-without-clearance AND CrosswindLimitExceeded apply same tick, DA fires first (pinned).
- Hysteresis: two-cycle test confirms tree rewrite suppresses re-fire same attempt.
- Event-count pin lives in pilot unit tests, not sim test (per codex review issue #8).

### Impact axis
- New `PilotInput` field is a firewall widening — `FirewallPilotInputTest` is the gate; updating it is a deliberate architectural change documented in commit message. Pilot only receives `WindReport` projection — smaller surface than full `WeatherObservation`.
- `WindReport` move from `:controller` to `:protocol` is small (the sealed shape doesn't carry implementation; existing controller consumers re-import). `WeatherObservation` stays in `:controller`.
- `AircraftType` field addition: sealed `data object` leaves mean no constructor-call-site churn (singletons carry their values internally).
- Pilot precedence in `pilotDecide` is unchanged (the new event flows through the existing self-initiated arm).
- Controller-side: no behavior changes; compile-impact only for `WindReport` re-import.
- Migration cost: `derivePilotEvent` signature change touches only one call site (verified by context-scout).

### Operational axis
- Determinism: crosswind helper is pure; per-tick recognition is pure.
- Tick-rate independence: predicate evaluates against current world wind state at each tick.
- Replay / observability: new `CrosswindLimitExceeded` event appears in pilot trace ledger.
- Performance: O(1) per-tick predicate evaluation; negligible.
- No PRNG, no async IO.

## Early proof point

**Task fn-14.1** validates the recognition + GA effect path via pilot-side unit tests (without sim harness). If the predicate misfires (e.g., Magnetic/True frame mix-up, sign error in sin, truncation regression), the unit tests catch it before .2's sim test runs.

## References

### Doctrinal
- **CAP 413 §4.66** / §4.67 (existing fn-11/fn-12 cite; verify against Edition 23.1 vs 24 numbering at task time — see `D-PASS-cap413-edition-24-reconciliation`) — pilot-initiated GA phraseology `(callsign), GOING AROUND`
- **ICAO Doc 4444 §12.3.4.18** — `GO AROUND` / `GOING AROUND` minimal phraseology (pilot has standalone authority; no ATC permission needed)
- **FAA AFH (FAA-H-8083-3C) Chapter 9** — Common Error #1: attempting landing in crosswinds exceeding max demonstrated component
- **14 CFR §23.233(a)** (pre-Amendment 64) — `0.2 VSO` certification floor for crosswind demonstration
- **FAA AC 23-8B** — compliance guidance for §23.233; "performance information, not a limitation"
- **ICAO Annex 6 Part II §2.4** — PIC final authority (general aviation operations)
- **FAA AIM §7-1-12.d.3** — ATC-voice winds reported in Magnetic degrees; METAR in True
- **POH citations**: Cessna 172S NAV III (`Knots(15)`, "not a limitation"); Boeing 737-800 FCOM (`Knots(33)`)

### Codebase prior art
- **fn-11** (G3a-trained) — `CircuitOutcome` ADT, `applyPlannedGoAround` Tick A pattern, `planMission` compiler arm. Reused.
- **fn-12** (G3a-obstruction) — Pilot-side flag-driven recognition pattern (NOT used here; G3a-react uses pure derivation via `derivePilotEvent`). `RunwayObstructionInformation` companion pattern (NOT used; pilot self-transmits without ATC instruction). Three-layer sim-test pins (REUSED).
- **fn-12.2** (Pilot ATC-initiated GA) — Reactive-GA Tick A pattern (`route = None`, `phase = Final` retained). **`applyCrosswindGoAround` mirrors this pattern**; if `applyReactiveGoAround` is pilot-side and shape-compatible, prefer a shared body (verify at task time).
- **fn-13** (CONTINUE APPROACH) — `ObstructionClearsInTime` guard pattern (kinematic predicate). G3a-react's `crosswindComponentKnots` is a similar pure helper.
- **fn-8** (G1) — Commitment-lifecycle GA-PRE-CLEAR/GA-POST-CLEAR interrupts (REUSED unchanged; trigger-agnostic).
- **fn-5** (G2) — Sim test harness pattern.

### Memory
- `feedback_world_only_test_triggers.md` — test authors wind state, not pilot decision
- `feedback_firewall_principle.md` — PilotInput widening is a deliberate firewall change; pilot receives `WindReport` not full `WeatherObservation`
- `feedback_reality_anchored.md` — POH limits are real POH values, not invented
- `feedback_no_corners.md` — fail-closed parse on `headingDegreesMagnetic`; no silent default-to-zero
- `feedback_pass_scope.md` — bundle field + helpers + recognition + apply + tests in one closing pass
- `project_rich_world_domain.md` — wind state SHOULD live on Aerodrome.weather (deferred: `D-PASS-wind-state-migrate-to-aerodrome`)
- `sim-test-pins-must-compare-against-2026-05-10` — decision-cycle time discipline
- `ga-path-precedence-reorder-when-adding-2026-05-10` — re-derive precedence when adding GA paths
- `feedback_plans_review_aware.md` — Review considerations addressed inline

### External (practice-scout sourced)
- [FAA Airplane Flying Handbook Ch 9](https://www.faa.gov/sites/faa.gov/files/regulations_policies/handbooks_manuals/aviation/airplane_handbook/10_afh_ch9.pdf) — Common Error #1
- [Boldmethod: How Maximum Demonstrated Crosswind Is Calculated](https://www.boldmethod.com/learn-to-fly/maneuvers/how-maximum-demonstrated-crosswind-is-calculated-ga-aircraft/) — performance information framing
- [SKYbrary — Wind Velocity Reporting](https://skybrary.aero/articles/wind-velocity-reporting) — direction convention
- [FAA AIM §7-1-12](https://www.faa.gov/air_traffic/publications/atpubs/aim_html/) — wind reference frame

## Deferments register

Deferments from this epic file in `~/.claude/plans/pilot-firewall.md` § Deferments register:

- **`D-PASS-g3a-react-tailwind-limit`** — POH tailwind limit (often 10 kt). `AircraftType.maxTailwindKnots` field. Pilot recognition. Separate from crosswind; doctrinally a hard limitation (not just demonstrated).
- **`D-PASS-g3a-react-gust-evaluation`** — gust-peak evaluation against POH limit. Practice-scout flagged this as a real-pilot consideration; v1 reads steady-state only.
- **`D-PASS-g3a-react-wind-variability-dynamics`** — temporal averaging / trend reasoning across ticks. Real ATC + pilots use sustained-wind reasoning; v1 evaluates per-tick.
- **`D-PASS-g3a-react-multi-aircraft-crosswind`** — multiple aircraft on same runway when wind shifts; sequencing of simultaneous GAs. Requires coordination logic.
- **`D-PASS-g3b-react-cross-aerodrome-crosswind`** — same scenario at LJMB or other aerodrome. Fixture variation; reuses all machinery.
- **`D-PASS-g3a-react-other-poh-triggers`** — density altitude, temperature, weight limits — each its own POH field + recognition.
- **`D-PASS-g3a-react-personal-minimums`** — pilot judgement layer (PIC margin below POH demo value). Realistic but adds complexity.
- **`D-PASS-g3a-react-atis-cadence-sensing`** — wind via ATIS broadcast (slower, coarser cadence). v1 uses world-truth weather observation.
- **`D-PASS-g3a-react-vrb-handling`** — `Wind.variable: Boolean` field for VRB direction; crosswind=0 in v1.
- **`D-PASS-wind-state-migrate-to-aerodrome`** — migrate `SimState.weatherByAerodrome` flat map to `Aerodrome.weather` per `project_rich_world_domain.md`. Affects many call sites; v1 keeps existing shape.
- **`D-PASS-cap413-edition-24-reconciliation`** — Edition 24 renumbered §4.66→§4.65 (VFR-continue) and §4.67→§4.66 (pilot-initiated GA). fn-11/fn-12 cite older numbering. Reconciliation pass.

## Closures

- **G3a trilogy complete at sim-test level.** Four reactive-GA paths: self-initiated (existing), pilot-trained (fn-11), ATC-mandated-obstruction (fn-12), pilot-reactive-crosswind (this epic). Plus CONTINUE APPROACH (fn-13) as the fourth non-GA reactive path.
- **First pilot-side reactive recognition driven by world weather.** Establishes the pattern for future POH-derived reactive triggers (tailwind, density altitude, etc. — deferred siblings).
- **First POH-derived typed data on `AircraftType`.** `maxCrosswindKnots` precedent; future POH fields follow the same shape.
- **`WindReport` lifted to `:protocol`** — small refactor, useful for any agent that reads weather.

## Requirement coverage

| Req | Description | Task(s) |
|-----|-------------|---------|
| R1  | `AircraftType.maxCrosswindKnots: Knots` + POH-cited leaf values + invariant tests | fn-14.1 |
| R2  | `WindReport` moved from `:controller` to `:protocol` | fn-14.1 |
| R3  | `PilotInput.weatherByAerodrome: Map<AerodromeId, WindReport>` field + firewall test update | fn-14.1 |
| R4  | `buildPilotInput` wiring extension (project `obs.wind` only) | fn-14.1 |
| R5  | `RunwayId.headingDegreesMagnetic(): Int?` extension (fail-closed) | fn-14.1 |
| R6  | `crosswindComponentKnots(...): Double` pure function + unit tests | fn-14.1 |
| R7  | `Wind.directionDegrees` KDoc Magnetic convention pin | fn-14.1 |
| R8  | `PilotEvent.CrosswindLimitExceeded` leaf + exhaustiveness | fn-14.1 |
| R9  | `derivePilotEvent` extension with **independent** branches + recognition predicate | fn-14.1 |
| R10 | `applyCrosswindGoAround` distinct applier (mirrors fn-12.2 reactive pattern; DA path unchanged) | fn-14.1 |
| R11 | Pilot-side unit tests + hysteresis test | fn-14.1 |
| R12 | `G3aPilotReactiveCrosswindTest.kt` sim test (observable behavior only) | fn-14.2 |
| R13 | Cross-reference doc updates | fn-14.2 |
| R14 | RegulationDatabase entries (FAA AFH, FAR 23.233, Annex 6, AIM) | fn-14.1 |
| R15 | Full verify GREEN (8 goldens) | fn-14.1, fn-14.2 |

## Done summary

_(filled per task during implementation)_

## Evidence

_(filled per task during implementation)_
