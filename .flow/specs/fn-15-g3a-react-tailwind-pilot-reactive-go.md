# G3a-react-tailwind — pilot-reactive go-around on POH tailwind limit exceeded

## Overview

Sibling of fn-14 (G3a-react crosswind, shipped). fn-14 added the first **pilot-side reactive recognition driven by world weather** — pilot reads wind, computes crosswind component against runway heading, decides GA without ATC instruction when POH demonstrated crosswind is exceeded. fn-15 adds the **same recognition shape for the POH tailwind limit**. Single AI aircraft at LOWG (C172, max-tailwind = 10 kt — see Decision #3), mission `CircuitTraining(outcomes = listOf(FullStop))`. On final, world authors wind shift to tailwind > 10 kt against active runway. Pilot's recognition fires; `applyTailwindGoAround` produces the GA effect (Tick A mirrors fn-14's `applyCrosswindGoAround`: `route = None`, `phase = Final` retained, subtree replacement via `isCircuitLike`, `Report(GoingAround)` transmitted); existing `GA-PRE-CLEAR` / `GA-POST-CLEAR` interrupts (trigger-agnostic — fire on any `Report(GoingAround)`) regress the commitment; world wind returns within limit before recovery final; aircraft lands.

**Doctrinal severity varies per aircraft type** (load-bearing for KDoc / commit message / RegulationDatabase scope — codex round-1 closure):
- **C172**: the current Cessna 172S/172R POH does NOT publish an explicit hard tailwind limitation. The 10 kt value used by this sim is the **FAA AFH industry-standard advisory** for light singles (FAA-H-8083-3C Chapter 9). Field is named `maxTailwindKnots` but framed in KDoc as "demonstrated / advisory operating maximum" for the C172 leaf — same modelling rationale as fn-14's crosswind (AFH Common Error #1: a competent VFR pilot **does** go around when the advisory is exceeded, even though the value is not a formal certification limit).
- **B738**: the Boeing 737-800 FCOM Limitations §1 publishes 15 kt steady tailwind on dry runway as a **hard limitation** (Limitations section, no exception). For the B738 leaf, the doctrinal anchor IS a hard limitation.

The type-asymmetry is real-world load-bearing. The codebase honours it via KDoc per-leaf; the field type is shared (typed `Knots`) and the recognition predicate is identical (component > limit), but the doctrinal anchor cited differs per-leaf. **No generic "POH = hard limitation" framing.** Per `feedback_reality_anchored.md`: reality is the existence proof; pretending all POHs are uniform would soften the deferment.

## Boundaries / non-goals

- **Out: gust evaluation against tailwind limit.** POH "max tailwind" is **steady-state** where published. Peak-gust reasoning is separate (same shape as the crosswind gust deferment). Filed as `D-PASS-g3a-react-tailwind-gust-evaluation`.
- **Out: multi-aircraft pilot-reactive tailwind.** Single-aircraft case in v1; world wind affects all aircraft on the runway — multiple simultaneous tailwind GAs needs sequencing. Sibling of fn-14's deferment. Filed as `D-PASS-g3a-react-multi-aircraft-tailwind`.
- **Out: cross-aerodrome G3b-react-tailwind.** LJMB or other; same machinery, fixture variation. Filed as `D-PASS-g3b-react-cross-aerodrome-tailwind`.
- **Out: combined crosswind + tailwind decision.** Real PICs evaluate the resultant wind vector against the type's envelope; v1 evaluates each axis **independently** (two separate predicates, two separate events, two distinct event leaves). Filed as `D-PASS-g3a-react-combined-wind-vector`.
- **Out: ATIS-cadence tailwind sensing.** v1 sources wind from world weather observation (real-time visual/instrument sensing path) via the existing `PilotInput.weatherByAerodrome` channel from fn-14. Sibling of fn-14's deferment. Filed as `D-PASS-g3a-react-tailwind-atis-cadence`.
- **Out: runway-condition / displaced-threshold / pressure-altitude / temperature corrections to the POH tailwind limit.** Real-world manufacturer values sometimes vary with conditions; v1 uses the typed POH/AFH constant only. Filed as `D-PASS-g3a-react-tailwind-condition-corrections`.
- **Out: variable wind (`VRB`) handling against tailwind limit.** Same shape as fn-14's `D-PASS-g3a-react-vrb-handling`; v1 treats `Wind` direction as always defined.
- **Out: wind state migration to `Aerodrome.weather`.** v1 uses the existing `SimState.weatherByAerodrome` flat-map shape inherited from fn-14. Migration is a separate refactor pass (carried by fn-16). This epic does NOT block on it. Filed as `D-PASS-wind-state-migrate-to-aerodrome` (already filed by fn-14).
- **Out: pilot personal-minimums layer below the typed tailwind value.** Real PICs apply margins; v1 fires recognition at the typed value directly. Filed as `D-PASS-g3a-react-tailwind-personal-minimums`.
- **Out: `Wind.gustKnots` semantics for the tailwind branch.** v1 reads `Wind.speedKnots` only; the gust field on `Wind` is ignored by the recognition (same as fn-14 crosswind). Covered by the gust-evaluation deferment above.
- **Out: generic "POH-as-law" `RegulationDatabase` entries.** Codex round-1 review: manufacturer-published values are not regulations; per-aircraft sources stay in `AircraftType` KDoc, not as `RegulationCategory.LAW` entries. Only verifiable procedural/regulatory anchors (AFH guidance, ICAO procedural law) land in `RegulationDatabase`. Filed as resolved-in-plan, not deferred.

## Strategy Alignment

Active tracks served by this plan:

- **Runtime simulator** — extends the pilot-reactive sensing surface added by fn-14 with a second independent POH-derived recognition axis. After fn-15: **five reactive-GA paths** covered at sim-test level:
  1. Self-initiated DA-without-clearance (existing pre-fn-11)
  2. Pilot-trained mission (fn-11; `CircuitOutcome.GoAround`)
  3. ATC-mandated obstruction (fn-12; `ARR-GO-AROUND-RUNWAY-OBSTRUCTED`)
  4. Pilot-reactive POH crosswind (fn-14)
  5. **Pilot-reactive tailwind (this epic)**
  Plus CONTINUE APPROACH (fn-13) as the fourth non-GA reactive path. Total: five-covered reactive-GA + CA decision space.

## Decision context

### 1. Distinct `PilotEvent.TailwindLimitExceeded` leaf vs sharing `CrosswindLimitExceeded` (high confidence — distinct)

**Decided: distinct leaf.** Add a new sealed leaf adjacent to `CrosswindLimitExceeded`:

```kotlin
data class TailwindLimitExceeded(
    override val aircraft: AircraftId,
    val componentKnots: Double,    // tailwind magnitude (positive); the recognition site converts the
                                   // signed projection to magnitude before constructing the event
    val limitKnots: Int,
    val runway: RunwayId,
) : PilotEvent
```

**Why distinct, not shared with crosswind**:
- **Trace separation** — pilot trace, ledger, log files, future telemetry must distinguish "tailwind exceedance" from "crosswind exceedance" without re-deriving from `(componentKnots, limitKnots)`. A shared leaf with an `axis: WindAxis` discriminator would obscure the doctrinal distinction at the type level.
- **Per-type doctrinal anchor distinction** — crosswind is "demonstrated" per AC 23-8B regardless of type; tailwind doctrine **varies per type** (advisory for C172 per AFH, hard limitation for B738 per FCOM). Distinct leaf surfaces the asymmetry at the recognition site.
- **Branch independence** — crosswind branch in `derivePilotEvent` gates on `aircraft.phase is Final` AND `currentStep ∈ {FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}`. Tailwind branch uses the **same** step-set today, but doctrinal divergence is foreseeable. Distinct leaves let each branch evolve independently without renegotiating a shared shape.
- **Future test ergonomics** — `tx.events.any { it is PilotEvent.TailwindLimitExceeded }` reads more naturally than `tx.events.any { it is PilotEvent.WindLimitExceeded && it.axis is WindAxis.Tailwind }`.

Exhaustiveness audit: every existing exhaustive `when (event: PilotEvent)` site needs a new arm (no `else`). After fn-14's audit, the call sites are enumerated; adding the third leaf is mechanical.

### 2. Distinct `applyTailwindGoAround` applier vs reusing `applyCrosswindGoAround` (high confidence — distinct)

**Decided: distinct applier function.** Adjacent to `applyCrosswindGoAround` in `Pilot.kt`. **Body identical** to `applyCrosswindGoAround` (Tick A intent: `route = None`, `phase = Final` retained, `targetSpeedMps = climbSpeedMps`, `targetAltitudeM = circuitPattern.altitudeAglM`; mission update: `replaceChild { isCircuitLike }` + `resetForGoAround(now)`; transmissions: `listOf(Report(listOf(ReportEvent.GoingAround)))`).

**Why distinct, not reused**:
- **Trace readability** — stack traces / log breadcrumbs show `applyTailwindGoAround` distinctly. A single shared applier (e.g. `applyWindLimitGoAround(event: PilotEvent)` accepting both leaves) loses the at-a-glance distinction.
- **Future-proofing for doctrine divergence** — if tailwind doctrine diverges per-type (e.g. distinct climb-gradient target for jet-class hard-limit tailwind GAs), the body of `applyTailwindGoAround` can move without touching the crosswind path. Shared body would either silently change crosswind (rejected) or require parameterising the divergence (over-abstraction).
- **KDoc cross-references both as siblings** — each function's KDoc cites the other so any future refactor that wants to factor a shared core helper is explicit, not stealthy.

**Anti-decision** (mirrors fn-14 anti-decision): do NOT extract a shared `applyReactiveWindGoAround(event: PilotEvent, ...)` core helper. The two paths today have identical bodies but distinct doctrinal anchors; sharing the body would silently couple them, rejected per `feedback_no_corners.md` ("no silent workarounds").

### 3. Per-leaf `maxTailwindKnots` values — verify at task time; reality-anchored per-type framing (codex round-1 closure)

**Decided: typed-Knots field per leaf; per-leaf doctrinal anchor; honest per-type framing.**

- **`C172`**: `maxTailwindKnots = Knots.unsafe(10)`. **No explicit hard tailwind limitation is published in the current Cessna 172S NAV III / 172R POH** (verified pre-plan; the POH §2 "Operating Limitations" addresses crosswind only, with the demonstrated-not-limitation framing). The 10 kt value is the **FAA AFH industry-standard advisory** for light single-engine GA (FAA-H-8083-3C Chapter 9 frames tailwind landings as high-risk, with 10 kt the common industry operating advisory).
  - **Per-leaf KDoc** explicitly says: "Cessna POH does not publish a hard tailwind limitation; 10 kt is the AFH-derived operating advisory consumed by the pilot's reactive recognition. Modelling rationale: a competent VFR pilot goes around when the advisory is exceeded, mirroring the fn-14 crosswind modelling decision (AC 23-8B's demonstrated value is similarly performance information, but a competent pilot treats it as the trigger). Personal-minimums judgement layer filed as `D-PASS-g3a-react-tailwind-personal-minimums`."
- **`B738`**: `maxTailwindKnots = Knots.unsafe(15)`. **Boeing 737-800 FCOM Limitations §1 publishes a 15 kt steady tailwind limit on dry runway** — this IS a hard limitation in the certification/operations sense (Limitations section, no exception).
  - **Per-leaf KDoc** explicitly says: "Boeing 737-800 FCOM Limitations §1: 15 kt steady tailwind on dry runway. Hard operational limitation. Verify edition at task time."

**Field-level KDoc** (sealed-class level) documents the per-type doctrinal severity asymmetry:
> "Maximum tailwind component the type's operating handbook (POH/FCOM) or industry guidance recognises as the operational maximum. **Doctrinal severity varies per type** — for some types (e.g. C172 light single) the POH does not publish an explicit tailwind limit and the value used here is the FAA AFH industry-standard advisory; for others (e.g. B738 narrow-body twinjet) the FCOM publishes a hard limitation. Per-leaf KDocs cite the source. The pilot's reactive-GA recognition fires on exceedance regardless of doctrinal severity — modelling a competent pilot's go-around decision."

This framing is **reality-anchored** (per `feedback_reality_anchored.md`): the codebase reflects that aircraft types differ in published guidance, rather than pretending all POHs are uniform. No `RegulationDatabase` entry conflates the two regimes into "POH = law".

**Reuses `Knots` positive-only smart type** (same shape as `maxCrosswindKnots`). Both values are ≥ 1 kt; `0 kt` "no tailwind allowed" is operationally nonsensical (would force GA on dead headwind + no headwind margin). The positive-only invariant is sound.

### 4. Tailwind component math — signed projection, separate pure helper (high confidence)

**Decided.** New pure function `tailwindComponentKnots(...)` in the **same file** as `crosswindComponentKnots`. **File rename**: `pilot/.../observe/Crosswind.kt` → `pilot/.../observe/WindComponents.kt`. Both helpers colocated; package unchanged. The rename is a single-file move + import-update across pilot test + production sites (fn-14's `Crosswind.kt` consumers: `PilotEvent.kt`, `CrosswindHelperTest.kt`, `PilotEventCrosswindTest.kt`). Trivial mechanical refactor; no behavior change.

**Why rename rather than new file**: the two helpers share inputs (`windFromMagnetic`, `windSpeedKnots`, `runwayHeadingMagnetic`), share doctrinal frame (Magnetic FROM-degrees per FAA AIM §7-1-12.d.3), and share the same KDoc warning paragraph (True-vs-Magnetic pitfall). Splitting them across two files duplicates the warning and obscures the symmetry. Single-file home is the obvious shape — and follows `feedback_pass_scope.md` ("bigger chunks").

**Tailwind component formula** (per FAA AIM §7-1-12 + ICAO Annex 3; signed projection along the runway centerline):

```
θ_signed = ((windFromMagnetic − runwayHeadingMagnetic + 540) mod 360) − 180     # same θ as crosswind, wrapped to [−180, 180]
headwindComponent = cos(θ_signed × π / 180) × windSpeedKnots                    # signed: + headwind, − tailwind
tailwindComponent = max(0.0, −headwindComponent)                                # magnitude; 0 when no tailwind
```

**Recognition predicate fires when**: `tailwindComponent > maxTailwindKnots.value.toDouble()` (strict `>`, mirrors crosswind branch).

**Return type — Double, no truncation** (same rationale as `crosswindComponentKnots`). A `10.4 kt` tailwind against a `10 kt` limit must fire; truncating would silently mask. `0.0` is a valid value (dead headwind / pure crosswind / calm) and the function never persists the value as `Knots`.

**Symmetry vs crosswind helper**:
- `crosswindComponentKnots` returns the **magnitude** of the lateral component (`|sin(θ)| × speed`). Left/right symmetric; the POH limit is symmetric.
- `tailwindComponentKnots` returns the **magnitude** of the tailwind axis only (`max(0, −cos(θ) × speed)`). Headwind direction is NOT included — a 20 kt headwind is "0 kt tailwind", not "−20 kt tailwind". The headwind side has no operational limit (headwind is always desirable for takeoff/landing); the operational asymmetry justifies the function returning a magnitude rather than a signed value.

**KDoc must cite**:
- Single-reference-frame contract (both inputs Magnetic FROM-degrees; same warning as crosswind).
- Sign convention: positive output = tailwind exists, zero = headwind or pure crosswind.
- Examples: dead headwind → 0.0; 90° crosswind → 0.0 (no tailwind); 180° tailwind → full speed; 135° quartering tail-cross at 20 kt → 20 × cos(45°) ≈ 14.14 kt tailwind component.
- True-vs-Magnetic pitfall (lift from crosswind helper's existing KDoc).
- Cross-reference `crosswindComponentKnots` as sibling.

### 5. Recognition predicate ordering when DA, crosswind, AND tailwind all apply same tick (high confidence)

**Decided ordering: DA → tailwind → crosswind.** Within `derivePilotEvent`, the branch evaluation order pins which event surfaces when multiple predicates are simultaneously true. Rationale:

- **DA first**: lowest-altitude / hardest-stop trigger (CAP 413 §4.55 decision-altitude discipline). Already pinned by fn-14; unchanged.
- **Tailwind second** (NEW position): tailwind is doctrinally a **hard limitation** on the type where the FCOM publishes one (e.g. B738); on the type where it's advisory (e.g. C172), the trigger still represents the doctrinally-graver of the two wind-axis trips (tailwind affects touchdown energy, runway remaining, and go-around margin — physically stronger constraint than crosswind which is a control-authority constraint). The surfaced event carries the more severe trigger when both fire.
- **Crosswind third**: unchanged from fn-14, just moved down one position.

**`derivePilotEvent` body shape** (after fn-15):
```
deriveDecisionAltitudeEvent(...) ?: deriveTailwindEvent(...) ?: deriveCrosswindEvent(...)
```

Each branch is independent (no shared early returns; mirror fn-14's split). When all three would fire (low + on-final + uncleared + tailwind > limit + crosswind > limit), DA wins; when only tailwind + crosswind fire (on-final + cleared + both winds exceeded), tailwind wins.

**Pinned by a new ordering test row** in the pilot event derivation suite: `PilotEventTailwindTest.kt` includes a "both tailwind and crosswind exceed — tailwind fires" assertion. Pin location chosen at task time (mirror fn-14's "DA + crosswind both apply — DA wins" precedent location).

### 6. Sim test wind authoring — direct tailwind, two-transition pattern (high confidence)

**Decided.** Test fixture authors `state.weatherByAerodrome[lowg]` shift via `onAfterEvent` hook (same mechanism as fn-14). Two `onAfterEvent` registrations with one-shot guards `var tailwindAuthored = false` / `var tailwindClearedToLimit = false`.

**Wind direction math** for the trigger shift:
- `runwayHeading = lowgRunwayId.headingDegreesMagnetic()!!` (e.g. runway 35C → heading 350°M).
- Tailwind direction = `(runwayHeading + 180) mod 360`. Aviation-display convention: prefer `360` over `0` for due-North runs. The codebase's `Wind.invoke` **accepts** `directionDegrees in 0..360` (verified at `Instruction.kt`; both `0` and `360` are valid inputs); the `(rawDir + 180) mod 360`-then-clamp-`0→360` normalisation here is for **display/convention consistency** with the aviation FROM-360-spelling, not because `Wind` rejects `0`.
- For runway 350°M: tailwind direction = `(350 + 180) mod 360 = 170°M`.
- Wind speed: `15 kt` (well above C172's 10 kt advisory tailwind limit, leaving margin against any tuning the verify pass adjusts).

**Initial wind**: 10 kt headwind from runway heading (`directionDegrees = runwayHeading`, `speedKnots = 10`) — zero tailwind, zero crosswind. Same as fn-14's initial setup.

**First transition** (post-`ClearedToLand` to exercise `GA-POST-CLEAR`):
- Predicate: commitment stage ∈ `{LandingClearanceIssued, AwaitLandedObserved}` AND `aircraft.phase is Final` (durable; mirrors fn-14's pattern).
- Authors: wind `directionDegrees = tailwindDirection, speedKnots = 15` (pure tailwind, 15 kt > 10 kt limit).
- Set `tailwindAuthored = true`.

**Second transition** (recovery enablement):
- Predicate: `Report(GoingAround)` has been transmitted AND aircraft back on downwind (circuit 2 setup) AND `!tailwindClearedToLimit`.
- Authors: wind back to `(runwayHeading, 10)` — pure 10 kt headwind, zero tailwind.
- Set `tailwindClearedToLimit = true`.

**Three-layer pin pattern** (same shape as fn-14 R12):
- **Layer 1** — causal partial-order: wind-shift cycle ≤ `Report(GoingAround)` cycle ≤ commitment regression cycle < wind-return cycle < recovery `ClearedToLand` cycle < `Report(RunwayVacated)` cycle. Same-cycle: `≤` on `SimTime.millis` + mint-id sequence tiebreak; strict `<` across cycles.
- **Layer 2** — sticky-witness regression: commitment regresses from `{LandingClearanceIssued, AwaitLandedObserved}` to `TowerArrivalStage.AwaitDownwind` via `GA-POST-CLEAR`. Sticky witnesses reset per fn-8.3.
- **Layer 3** — kinematic non-event: no `LandingRoll` phase / `TouchdownDetected` between wind-shift and wind-recovery cycles.

**Forbidden** (per `feedback_world_only_test_triggers.md`): no `PilotEvent.TailwindLimitExceeded` injection, no direct `PilotInput.weatherByAerodrome` mutation outside sim wiring, no `mission` mutation bypassing recognition→apply.

**No event-count pin in sim test** — that pin lives in pilot-side unit tests (per fn-14's codex review issue #8 closure; mirror that discipline).

### 7. Controller-side: no behavior changes (high confidence — same as fn-14)

**Decided.** `GA-PRE-CLEAR` and `GA-POST-CLEAR` `ProcedureInterrupt`s already fire on any `Report(GoingAround)` regardless of trigger source — they are trigger-agnostic by design. Pilot transmits `Report(GoingAround)`; controller regresses the commitment to `AwaitDownwind`; recovery clearance issued; aircraft lands. No new controller rule, no new event, no new instruction. Zero compile-impact (unlike fn-14's `WindReport` relocation, which is already shipped).

### 8. RegulationDatabase entries — narrow scope, no fictitious legal authority (codex round-1 closure)

**Decided.** Only verifiable doctrinal/procedural anchors land in `RegulationDatabase`. Manufacturer-published values (`AircraftType.maxTailwindKnots`) stay in `AircraftType` KDoc with per-leaf POH/FCOM citation — NOT in `RegulationDatabase`.

**Two new entries** (revised from the original three after codex round-1):

- **`FAA_AFH_CH9_TAILWIND_RISK`** — FAA-H-8083-3C Ch 9, "high-risk operations" framing: tailwind landings increase touchdown distance and reduce go-around margin. **Category: `GUIDANCE`** (FAA AFH is an advisory handbook, not legal authority — same category as fn-14's `FAA_AFH_CH9_CROSSWIND_ERRORS`). Modelling anchor: the pilot's reactive GA on tailwind exceedance for the C172 advisory regime.
- **`ICAO4444_7_11_6_REDUCED_RUNWAY_TAILWIND`** — ICAO Doc 4444 §7.11.6 specifically cites a `5 kt` tailwind component limit for **reduced runway separation minima**. **Existing document key**: `document = "ICAO_4444"` (consistent with the existing `ICAO4444_*` entries; codex round-1 caught the proposed `ICAO_DOC_4444` mismatch with the established convention). **Distinct symbol name** `ICAO4444_7_11_6_REDUCED_RUNWAY_TAILWIND` because `ICAO4444_7_11` already exists for "Post-landing taxi" (verified — `RegulationDatabase.kt:212`). **Category: `GUIDANCE`** — peer doctrinal anchor for completeness; **scope is distinct from POH performance** (separation minima ≠ aircraft-type tailwind limit). KDoc explicitly notes the scope difference so the entry is not mistaken for the trigger anchor.

**Removed**: the previously-proposed `POH_TAILWIND_LIMITATION_GENERAL` (`category = LAW`) — codex round-1 review correctly flagged this as fictitious legal authority. Manufacturer POH/FCOM is not regulation; per-aircraft sources stay in `AircraftType` KDoc, full stop.

**Conditional FAR/CS cert entry**: 14 CFR §23.51 / §25.105 / CS-25.105 address **takeoff performance** certification; they do not specifically standardise a tailwind limitation. **No FAR/CS tailwind cert entry is added** in v1 (the codebase's reality-anchored discipline forbids inventing one). If a future doctrinal scout uncovers a directly-relevant FAR/CS clause, fn-15 can add it via a follow-up pass.

### 9. No-refire / hysteresis (high confidence — same shape as fn-14)

**Decided.** Hysteresis comes via the mission-tree rewrite — once `applyTailwindGoAround` fires, the active subtree becomes `CircuitAfterGoAround` and `mission.currentStep` is no longer in `WIND_REACTIVE_ELIGIBLE_STEPS` (renamed from fn-14's `CROSSWIND_ELIGIBLE_STEPS` per Decision #10). No new witness, no flag.

If wind stays past tailwind limit on the recovery circuit's final → recognition fires again → second GA. Pinned by a hysteresis test `PilotTailwindHysteresisTest.kt`: two consecutive decision cycles with tailwind > limit → first emits event + rewrites tree; second emits zero events.

### 10. Eligible-step set: shared symbol between branches (high confidence)

**Decided.** fn-14 introduced `private val CROSSWIND_ELIGIBLE_STEPS: Set<MissionStep>` at `PilotEvent.kt:173-178`. **Rename to `WIND_REACTIVE_ELIGIBLE_STEPS`** and share between crosswind + tailwind branches. Same set today: `{FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}`. KDoc updated to explain the symbol is shared between the two POH/AFH-reactive branches; if future doctrine diverges (e.g. tailwind eligible from base), the rename can split back into two sets.

Sharing the symbol is the project's `feedback_pass_scope.md` discipline ("bigger chunks, fold nice-to-haves into the closing pass"). Splitting later is cheap; introducing the rename now (when the second consumer lands) is the right moment.

### 11. Mission-shape guard reuse (high confidence)

**Decided.** fn-14's crosswind branch added a `activeCompoundName.isCircuitLike()` guard (per codex round-1 review) — fail-closed if the active compound is not rewritable by `applyCrosswindGoAround`. **Tailwind branch reuses the identical guard** — same shape, same applier rewrite predicate, same fail-closed rationale. Extract to a shared private helper `private fun isReactiveGoAroundEligible(mission: PilotMission): Boolean` in `PilotEvent.kt` so both branches read identically. Single-line extraction; no behavior change.

### 12. Firewall + wiring — zero change (high confidence)

**Decided.** Sensor channel unchanged. `PilotInput.weatherByAerodrome` and `PilotWiring.buildPilotInput`'s projection ship in fn-14. The tailwind branch reads the same `WindReport` projection via the same `windForMission` helper. No new firewall surface, no new wiring code. `FirewallPilotInputTest`'s canonical-constructor allowlist + reflection-based property scan stay unchanged.

This is a clean "additive within existing surface" pass — the firewall widening was the deliberate architectural change in fn-14, not here.

## Acceptance

- **R1:** `AircraftType.maxTailwindKnots: Knots` field added on sealed surface and every leaf. C172 = `Knots.unsafe(10)` (AFH-derived advisory; KDoc EXPLICITLY states the C172 POH does not publish a hard tailwind limit and frames the value as the FAA AFH Ch 9 industry-standard advisory). B738 = `Knots.unsafe(15)` (FCOM Limitations §1 hard-limitation; KDoc cites the source and labels as hard limit). Reuse existing `Knots` positive-only type from `Instruction.kt:80`. `AircraftTypeSpec.kt` invariant tests updated with `maxTailwindKnots > 0` row. **Field-level KDoc** documents the per-type doctrinal severity asymmetry (advisory for some, hard limitation for others), cross-references `maxCrosswindKnots`, and pins that the modelling rationale (a competent pilot goes around on advisory exceedance) mirrors fn-14's crosswind reasoning. **No generic "POH = hard limit" framing**.
- **R2:** File `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/Crosswind.kt` **renamed** to `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/WindComponents.kt`. Both `crosswindComponentKnots` and the new `tailwindComponentKnots` live in this single file. All import sites updated (grep-verified at task time). Zero behavior change in the rename step (verified by running `:pilot:jvmTest` after the rename, before adding the new function).
- **R3:** `tailwindComponentKnots(windFromMagnetic: Int, windSpeedKnots: Int, runwayHeadingMagnetic: Int): Double` added in `WindComponents.kt`. Returns Double, no truncation. Sign convention: positive magnitude when tailwind exists, `0.0` when headwind or pure crosswind. KDoc cites: (a) same-reference-frame contract (Magnetic FROM-degrees), (b) True-vs-Magnetic pitfall (cross-reference crosswind helper's KDoc paragraph), (c) FAA AIM §7-1-12.d.3 cite, (d) examples (dead headwind = 0.0; 90° crosswind = 0.0; 180° tailwind = full speed; 135° quartering tail-cross 20 kt ≈ 14.14). Unit tests in new `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/observe/TailwindHelperTest.kt` cover: dead headwind = 0.0; pure 90° crosswind = 0.0; 180° dead tailwind 20 kt = 20.0; 135° quartering 20 kt ≈ 14.14 ± 0.01; wraparound (wind 180° vs runway 360°, i.e. tailwind on runway 36); zero-speed = 0.0; small angles near headwind (89° crosswind: 0.0; 91° quartering: small positive); strict `>` comparison boundary at the C172 10 kt limit (10.0 → no event, 10.0001 → event — verified at the recognition-test layer).
- **R4:** `PilotEvent.TailwindLimitExceeded(override val aircraft: AircraftId, val componentKnots: Double, val limitKnots: Int, val runway: RunwayId) : PilotEvent` leaf added at `pilot/.../observe/PilotEvent.kt` adjacent to `CrosswindLimitExceeded`. Every exhaustive `when (event: PilotEvent)` site audited and an explicit no-op arm added (NO `else`). Sites today after fn-14: `Pilot.kt:178-192` (the precedence ladder). Audit pass at task time; the leaf set after this change is 4 (`DecisionAltitudeWithoutClearance`, `AtcGoAroundOnFinal`, `CrosswindLimitExceeded`, `TailwindLimitExceeded`).
- **R5:** `derivePilotEvent` extended with a **third independent branch** (`deriveTailwindEvent`). Branches are independent — no shared early returns. Ordering per Decision #5: `deriveDecisionAltitudeEvent → deriveTailwindEvent → deriveCrosswindEvent`. The branch reads `aircraft.type.maxTailwindKnots` inside (no separate `aircraftType` parameter — mirrors fn-14's discipline; `AircraftState` already carries `type`). Signature **unchanged from fn-14**: still `derivePilotEvent(aircraft, mission, weather: WindReport?)`. Same weather channel.
- **R6:** Shared mission-shape eligibility helper `private fun isReactiveGoAroundEligible(mission: PilotMission): Boolean` extracted in `PilotEvent.kt` (per Decision #11). Both `deriveCrosswindEvent` and `deriveTailwindEvent` call it. Behaviour identical to fn-14's inline `isCircuitLike` guard. Unit-test pinned: a Transit-arrival mission shape returns null from both branches.
- **R7:** Shared eligible-step set renamed `CROSSWIND_ELIGIBLE_STEPS → WIND_REACTIVE_ELIGIBLE_STEPS` (per Decision #10), KDoc updated to explain the shared symbol; both branches read it. Behaviour unchanged; this is a name-only refactor folded into this pass.
- **R8:** `applyTailwindGoAround(event: PilotEvent.TailwindLimitExceeded, mission, aircraft, now: SimTime)` added in `Pilot.kt` adjacent to `applyCrosswindGoAround`. Body identical to `applyCrosswindGoAround` (Tick A intent + subtree replacement + `resetForGoAround(now)` + `Report(GoingAround)` transmission). KDoc explicitly cross-references `applyCrosswindGoAround` as sibling and documents the anti-decision (no shared core helper). `applyCrosswindGoAround` and `applySelfInitiatedGoAround` bodies **unchanged**.
- **R9:** `pilotDecide` precedence ladder extended (`Pilot.kt:178-192`) — `TailwindLimitExceeded → applyTailwindGoAround`. The `when` arms within `pilotDecide` are written **in the dispatch order DA → tailwind → crosswind** mirroring the `derivePilotEvent` branch order (codex round-1 cosmetic consistency fix; dispatch is functionally order-independent since only one event surfaces per call, but the visual ordering aligns with the documented evaluation ordering for reader clarity). Position mirrors crosswind dispatch (both leaves flow through the self-initiated arm).
- **R10:** Pilot-side unit tests in new files (mirror fn-14 shape):
  - `pilot/.../observe/TailwindHelperTest.kt` — pure math (R3 cases).
  - `pilot/.../observe/PilotEventTailwindTest.kt` — recognition matrix:
    - fires when on final + weather present + tailwind > limit
    - no-fire when tailwind ≤ limit (boundary at exactly 10.0 → null, just above → event)
    - no-fire when phase not Final
    - no-fire when step not in `WIND_REACTIVE_ELIGIBLE_STEPS`
    - no-fire when weather is null / `NotReported` (fail-closed)
    - no-fire when `mission.activeRunway` is `None`
    - no-fire when `runway.headingDegreesMagnetic()` returns null
    - no-fire when active compound is not `isCircuitLike` (Transit-arrival shape; via shared eligibility helper)
    - **ordering pin: when both `TailwindLimitExceeded` AND `CrosswindLimitExceeded` apply same tick, tailwind fires** (Decision #5)
    - independent of `mission.hasClearance` (fires at FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND regardless of clearance state)
  - `pilot/.../PilotTailwindGoAroundTest.kt` — `applyTailwindGoAround` post-conditions: `route = None`, `phase = Final` retained, mission tree replaced (subtree to `CircuitAfterGoAround` via `isCircuitLike`), `mission.hasClearance` cleared via `resetForGoAround(now)`, `Report(GoingAround)` transmitted. **TouchAndGo variant**: starting mission contains `CompoundTask(TaskName.TouchAndGo, ...)`; tailwind GA must rewrite that subtree the same way it rewrites `Circuit` (precedent: fn-14's `PilotCrosswindGoAroundTest`).
  - `pilot/.../PilotTailwindHysteresisTest.kt` — two-cycle: first cycle with tailwind > limit emits exactly one `TailwindLimitExceeded` AND rewrites mission tree; second cycle with same wind state emits zero events because `currentStep` is no longer in `WIND_REACTIVE_ELIGIBLE_STEPS`.
  - `pilot/.../PilotTailwindTickATickBTest.kt` — Tick A → Tick B integration through `pilotDecide`: Tick A produces `route=None, phase=Final, Report(GoingAround)`; Tick B planner builds GA route via `planCircuitTrainedGoAround`'s Circuit-mode special-case (load-bearing reuse pin, sibling of fn-14's `PilotCrosswindTickATickBTest`).
  - **Regression check**: existing `CrosswindLimitExceededSpec`, `PilotCrosswindGoAroundTest`, `PilotCrosswindHysteresisTest`, `PilotCrosswindTickATickBTest`, `SelfInitiatedGoAroundResponseSpec`, `PilotAtcInitiatedGoAroundSpec` all stay GREEN unchanged.
- **R11:** Sim test `G3aPilotReactiveTailwindTest.kt` at `sim/src/jvmTest/.../sim/`:
  - Single-aircraft LOWG, mission `CircuitTraining(outcomes = listOf(FullStop))`, aircraft type `C172` (10 kt advisory tailwind from R1; pin matches R1).
  - World authors **two transitions** via `onAfterEvent` (per Decision #6):
    1. Initial → 15 kt tailwind (direction `(runwayHeading + 180) mod 360`, normalised to `360` for display-convention consistency when result is `0` — `Wind.invoke` accepts both, the normalisation is cosmetic). Predicate: commitment stage ∈ `{LandingClearanceIssued, AwaitLandedObserved}` AND `aircraft.phase is Final`. One-shot `var tailwindAuthored = false`.
    2. 15 kt tailwind → 10 kt headwind (back to initial direction = `runwayHeading`). Predicate: `Report(GoingAround)` has been transmitted AND aircraft back on downwind AND `!tailwindClearedToLimit`. One-shot `var tailwindClearedToLimit = false`.
  - Authorship predicate validated (FAIL LOUDLY if preconditions don't hold within some sim tick — test setup error, not retry-loop).
  - **Three-layer pin pattern** (per Decision #6):
    - Layer 1 (causal partial-order): wind-shift cycle `≤` `Report(GoingAround)` cycle `≤` commitment regression cycle `<` wind-return cycle `<` recovery `ClearedToLand` cycle `<` `Report(RunwayVacated)` cycle. Same-cycle: `<=` on `SimTime.millis` + mint-id sequence tiebreak; strict `<` across cycles.
    - Layer 2 (sticky-witness regression): commitment regresses from `{LandingClearanceIssued, AwaitLandedObserved}` to `TowerArrivalStage.AwaitDownwind` via `GA-POST-CLEAR` (NOT `Immediate` — same as fn-14). Sticky witnesses reset per fn-8.3.
    - Layer 3 (kinematic non-event): no `LandingRoll` phase between wind-shift and wind-recovery cycles; exactly one `TouchdownDetected` after wind returns within limit.
  - Vacate-coordination closure pin per fn-8.3 R7.
  - Time band ±15% on observed wall (calibrate first GREEN; expected ~1300-1500 sim seconds matching fn-14's profile).
  - **World-state weather transition pin**: assert exactly two `weatherTransitions(lowg)` entries (the two authored shifts). **Reuse fn-14's `weatherTransitions` extractor in `SimTraceQueries.kt`** — already aerodrome-keyed, no controllerId.
  - **Transmission shape pins** (exact protocol surface): `tx is Report && ReportEvent.GoingAround in tx.events` (NOT `tx is Report.GoingAround`); `tx is Report && ReportEvent.RunwayVacated in tx.events`. `Wind.unsafe(...)` for fixture wind (NOT `Wind(...)` — primary constructor is private). Stage names exact: `LandingClearanceIssued`, `AwaitLandedObserved`, `AwaitDownwind`.
  - **No event-count pin on `PilotEvent.TailwindLimitExceeded`** in the sim test (per fn-14 codex review issue #8) — that pin lives in `PilotTailwindHysteresisTest` (R10).
- **R12:** Cross-reference doc updates (mirror fn-14 R13's site list):
  - `AGENTS.md` § Golden tests — add G3a-react-tailwind bullet (9 tests total).
  - `STRATEGY.md` § Runtime simulator track — note the second pilot-reactive POH/AFH recognition axis; quintuple-covered reactive-GA decision space.
  - `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` § Resolution — add fn-15 closure paragraph for the five-path reactive-GA coverage; surface the per-type doctrinal severity asymmetry as a deliberate modelling choice.
  - `wiki/domain/aviation-world.md` — extend the `AircraftType` section with `maxTailwindKnots` field; doctrinal note on per-type severity (advisory vs hard limitation).
  - `pilot/.../Pilot.kt` inline comment block at pilotDecide — update "four GA paths share `pilotDecide`'s fork point" → "five GA paths".
  - `pilot/.../observe/PilotEvent.kt` file-level KDoc — leaf-count "3 leaves" → "4 leaves"; add `TailwindLimitExceeded` entry with per-type doctrinal-severity note.
  - `protocol/.../AircraftType.kt` file-level KDoc + per-leaf KDocs — `maxTailwindKnots` per-leaf source citation (POH §2 not-published for C172 + AFH advisory framing; FCOM §1 for B738 hard limit).
  - `sim/.../testing/Fixtures.kt` LOWG provenance — add G3a-react-tailwind consumer.
  - Sibling test class docstrings — `@see G3aPilotReactiveTailwindTest` cross-ref on: `LowgGoldenTest`, `G1TwoAircraftCircuitsTest`, `G1TwoAircraftMinimalSpec`, `G2CrossAerodromeVfrTest`, `G3aPilotTrainedGoAroundTest`, `G3aRunwayObstructionTest`, `G3aRunwayObstructionContinueApproachTest`, **`G3aPilotReactiveCrosswindTest`** (the sibling that just shipped — bidirectional cross-reference).
- **R13:** RegulationDatabase entries added at `protocol/.../RegulationDatabase.kt` (per Decision #8 — narrow scope after codex round-1):
  - `FAA_AFH_CH9_TAILWIND_RISK` — FAA-H-8083-3C Ch 9 high-risk operations framing for tailwind landings. `category = GUIDANCE`. `document = "FAA_AFH"`.
  - `ICAO4444_7_11_6_REDUCED_RUNWAY_TAILWIND` — ICAO Doc 4444 §7.11.6 (5 kt tailwind for reduced runway separation). `category = GUIDANCE`. `document = "ICAO_4444"` (consistent with existing `ICAO4444_*` entries; symbol name distinct from existing `ICAO4444_7_11` for "Post-landing taxi"). KDoc explicitly notes scope difference (separation, not POH performance) — peer doctrinal anchor for completeness.
  - **NOT added**: `POH_TAILWIND_LIMITATION_GENERAL` (codex round-1: manufacturer values are not regulations; per-aircraft sources stay in `AircraftType` KDoc).
  - **NOT added**: FAR/CS tailwind cert entry (no specific tailwind certification clause is verifiable in §23.51 / §25.105 / CS-25.105; reality-anchored discipline rejects inventing one).
- **R14:** Full verify GREEN: `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` exits 0. **All nine golden tests** (G0 / G1 / G1-min / G2 / G3a-trained / G3a-obstruction / G3a-continue / G3a-react-crosswind / **G3a-react-tailwind**) GREEN. detekt baseline unchanged.
  - **fn-15.1 verify**: eight existing goldens stay GREEN (the eighth, fn-14's crosswind, MUST stay GREEN — the `WindComponents.kt` rename + shared `isReactiveGoAroundEligible` extraction + `WIND_REACTIVE_ELIGIBLE_STEPS` rename are zero-behaviour-change refactors). All new pilot unit tests GREEN.
  - **fn-15.2 verify**: all nine goldens GREEN. `G3aPilotReactiveTailwindTest` GREEN.

## Strategy drift flagged for review

_(none — plan aligns with Runtime simulator track and is a clean additive sibling to fn-14. The per-type doctrinal severity distinction (advisory vs hard limitation) is the reality-anchoring depth this pass surfaces — without fictitious-legal-authority `RegulationDatabase` entries after codex round-1.)_

## Quick commands

```bash
./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
./gradlew :sim:jvmTest --tests "xyz.easiersaid.twr.sim.G3aPilotReactiveTailwindTest"
./gradlew :pilot:jvmTest --tests "xyz.easiersaid.twr.pilot.observe.PilotEventTailwindTest"
./gradlew :pilot:jvmTest --tests "xyz.easiersaid.twr.pilot.observe.TailwindHelperTest"
./gradlew :pilot:jvmTest --tests "xyz.easiersaid.twr.pilot.PilotTailwindHysteresisTest"
```

## Approach

### Two-task split

1. **Task .1 — Foundation:** `AircraftType.maxTailwindKnots: Knots` field + per-leaf source-cited values (C172 = AFH advisory framing; B738 = FCOM hard limit framing); `Crosswind.kt → WindComponents.kt` rename + import-update; `tailwindComponentKnots(...)` helper; `PilotEvent.TailwindLimitExceeded` leaf; `WIND_REACTIVE_ELIGIBLE_STEPS` rename of fn-14's `CROSSWIND_ELIGIBLE_STEPS`; shared `isReactiveGoAroundEligible` extraction; third independent branch in `derivePilotEvent` (ordering: DA → tailwind → crosswind); `applyTailwindGoAround` distinct applier (identical body to `applyCrosswindGoAround`, cross-referenced via KDoc); `pilotDecide` precedence extension (4-leaf exhaustive `when`, written in DA → tailwind → crosswind dispatch order); pilot-side unit tests (helper, recognition, applier, hysteresis, TouchAndGo variant, Tick A→B integration); two `RegulationDatabase` entries (AFH guidance + ICAO Doc 4444 §7.11.6 guidance). Existing eight goldens stay GREEN.
2. **Task .2 — Sim test + cross-references:** `G3aPilotReactiveTailwindTest.kt` end-to-end at LOWG (single C172, two-transition wind authorship: initial 10 kt headwind → 15 kt tailwind via post-clearance one-shot → return to 10 kt headwind via `Report(GoingAround)`-gated one-shot → recovery landing). Three-layer pins (causal, sticky-witness, kinematic non-event). 9 doc-update edits (1 mid-AGENTS update, 1 STRATEGY entry, 1 wiki design-decision paragraph, 1 wiki/domain section, 1 inline Pilot.kt comment block, 1 PilotEvent.kt file KDoc, 1 AircraftType.kt KDoc, 1 Fixtures.kt provenance, 8 sibling test docstrings — counts mirror fn-14.2 site list). Closes the epic.

### Reuse points (file:line refs — sibling-of-fn-14 inventory)

| Surface | fn-14 reuse (no change) | fn-15 new code |
|---------|-------------------------|----------------|
| `Knots` value class | `protocol/.../Instruction.kt:80` | Reuse for `maxTailwindKnots` (always ≥ 1) |
| `Wind` data class + KDoc convention pin | `protocol/.../Instruction.kt:123-160` | Unchanged. `Wind.invoke` accepts `directionDegrees in 0..360` (both `0` and `360` valid). |
| `WindReport` sealed interface | `protocol/.../WindReport.kt` (lifted by fn-14) | Unchanged |
| `PilotInput.weatherByAerodrome` | `pilot/.../PilotInput.kt` (fn-14) | Unchanged |
| `PilotWiring.buildPilotInput` projection | `sim/.../PilotWiring.kt` (fn-14) | Unchanged |
| `windForMission` aerodrome resolver | `pilot/.../Pilot.kt:287` (fn-14) | Unchanged |
| `RunwayId.headingDegreesMagnetic()` | `protocol/.../RunwayHeading.kt` (fn-14) | Unchanged |
| `crosswindComponentKnots(...)` | `pilot/.../observe/Crosswind.kt` (fn-14) | **File renamed** to `WindComponents.kt`; helper colocated |
| `AircraftType` sealed registry | `protocol/.../AircraftType.kt:44-309` | Add `maxTailwindKnots: Knots` field + per-leaf values |
| `AircraftType.maxCrosswindKnots` | fn-14 field | Sibling pattern; KDoc cross-references the per-type doctrinal severity asymmetry |
| `PilotEvent` sealed | `pilot/.../observe/PilotEvent.kt` (fn-14: 3 leaves) | Add `TailwindLimitExceeded` leaf (→ 4 leaves) |
| `CROSSWIND_ELIGIBLE_STEPS` const | `pilot/.../observe/PilotEvent.kt:173-178` (fn-14) | **Renamed** to `WIND_REACTIVE_ELIGIBLE_STEPS`; both branches share |
| `deriveCrosswindEvent` shape | `pilot/.../observe/PilotEvent.kt:280-328` (fn-14) | Mirror shape: `deriveTailwindEvent` independent branch |
| `isCircuitLike` mission guard | `pilot/.../observe/PilotEvent.kt:302-303` (fn-14 inline) | **Extract** to shared `isReactiveGoAroundEligible` helper |
| `derivePilotEvent` body | `pilot/.../observe/PilotEvent.kt:225-236` (fn-14: 2 branches) | Add third branch; ordering DA→tailwind→crosswind |
| `applyCrosswindGoAround` body | `pilot/.../Pilot.kt:870-913` (fn-14) | Mirror as `applyTailwindGoAround` (identical body, distinct function) |
| `pilotDecide` precedence | `pilot/.../Pilot.kt:178-192` (fn-14: 4-arm `when`) | Add `TailwindLimitExceeded → applyTailwindGoAround` arm (→ 5 arms; dispatch order matches branch order) |
| `GoAroundResult` type | `pilot/.../Pilot.kt:751-755` (fn-14) | Unchanged |
| `resetForGoAround(now)` | `PilotMission.kt:336` | Unchanged |
| `isCircuitLike()` predicate | `PilotMission.kt:790` | Unchanged |
| Controller `GA-PRE-CLEAR`/`GA-POST-CLEAR` | `TowerArrival.kt:378-392` | Unchanged (trigger-agnostic) |
| `weatherTransitions(aerodromeId)` extractor | `sim/.../testing/SimTraceQueries.kt` (fn-14) | Unchanged |
| `onAfterEvent` test hook | `sim/.../testing/runUntilWithStateTrace` (fn-14) | Unchanged |
| `commitmentStageTransitions` extractor | `sim/.../testing/SimTraceQueries.kt` (fn-8) | Unchanged |
| GA test pattern | `CrosswindLimitExceededSpec` / `PilotCrosswindGoAroundTest` (fn-14) | Mirror as `PilotEventTailwindTest` / `PilotTailwindGoAroundTest` |
| Sim test pattern | `G3aPilotReactiveCrosswindTest` (fn-14) | Mirror as `G3aPilotReactiveTailwindTest` |
| RegulationDatabase shape | `protocol/.../RegulationDatabase.kt` (fn-14: 4 entries) | Add 2 entries (Decision #8 — codex-tightened scope) |
| `ICAO4444_7_11` existing entry | `protocol/.../RegulationDatabase.kt:212` ("Post-landing taxi") | Untouched; new entry `ICAO4444_7_11_6_REDUCED_RUNWAY_TAILWIND` distinct symbol |

## Test notes

### Sim test (Task .2)

Follows the **three-layer pin pattern** (per fn-11.2 / fn-12.3 / fn-14.2):

- **Layer 1 (causal partial-order)** — decision-cycle pins via `findEmittingCycleMs` mint-id walk. Same-cycle events use `<=` on `SimTime.millis` plus mint-id sequence tiebreak; strict `<` only across cycles:
  ```
  Weather_shift.decisionTime
      <= Report(GoingAround).decisionTime               // same-cycle OK
      <= Stage_regression(LandingClearanceIssued|AwaitLandedObserved → AwaitDownwind).time
      <  Weather_return.decisionTime
      <  ClearedToLand_recovery.decisionTime
      <  Report(RunwayVacated).decisionTime
  ```
- **Layer 2 (sticky-witness regression)** — exactly one stage transition `<from-stage> → AwaitDownwind` via `GA-POST-CLEAR`. Sticky-witness reset per fn-8.3.
- **Layer 3 (kinematic non-event)** — no `LandingRoll` phase before `Report(GoingAround)`; aircraft does NOT touch down on the GA'd approach.

**World-weather transition pin**: reuse fn-14's `weatherTransitions(aerodromeId)` extractor. Assert exactly two transitions: wind crosses past tailwind limit (triggers GA); wind returns to headwind (enables recovery). No controller-belief weather slice (weather lives at world-state per fn-14's doctrine).

**No event-count pin in sim test** (per fn-14 codex review issue #8) — that pin lives in `PilotTailwindHysteresisTest`.

**Vacate-coordination closure pin** (fn-8.3 R7-style): no leftover ledger entries after vacate.

**Time band ±15%** on observed wall (calibrate first GREEN; expected ~1300-1500 sim seconds for single circuit + GA + recovery circuit — sibling profile to fn-14).

**Exact protocol shapes** (per `feedback_no_corners.md` discipline):
- `tx is Report && ReportEvent.GoingAround in tx.events` (NOT `tx is Report.GoingAround` — `Report` is a single class, `GoingAround` is a `ReportEvent` leaf).
- `tx is Report && ReportEvent.RunwayVacated in tx.events`.
- `Wind.unsafe(directionDegrees, speedKnots)` for fixture construction (primary constructor is private; `.unsafe` is the compile-time-known convention). `Wind.invoke` accepts `directionDegrees in 0..360`; both `0` and `360` validate — the `(rawDir + 180) mod 360`-then-clamp-`0→360` normalisation in the test is for display/convention consistency.
- Stage names exact: `LandingClearanceIssued`, `AwaitLandedObserved`, `AwaitDownwind` (under `TowerArrivalStage`).
- `Knots.unsafe(N)` for compile-time-known limit constants.

### Pilot-side unit tests (Task .1)

- **Boundary discipline on the strict `>` predicate**: assert at exactly the limit value (component = limit → null) and just above (component = limit + small epsilon → event). Mirror fn-14's discipline; do not introduce a soft tolerance.
- **Recognition matrix**: every fail-closed input enumerated (weather null, weather NotReported, runway parse fail, activeRunway None, phase wrong, step wrong, active compound not isCircuitLike).
- **Tick A → Tick B integration** through `pilotDecide` (not direct applier call) — protects the load-bearing reuse assumption that the existing planner picks up the GA route on the next tick.
- **TouchAndGo variant** — verifies the `isCircuitLike` predicate covers T&G the same way it covers Circuit / CircuitAfterGoAround.
- **Regression**: every fn-14 spec stays GREEN unchanged (the `Crosswind.kt → WindComponents.kt` rename + `WIND_REACTIVE_ELIGIBLE_STEPS` rename + `isReactiveGoAroundEligible` extraction are zero-behaviour-change).
- **Both-axis ordering pin**: when both tailwind and crosswind would fire same tick, tailwind surfaces (Decision #5).

## Review considerations

### FP / type-safety axis
- New `PilotEvent.TailwindLimitExceeded` leaf forces explicit no-op arms in every existing exhaustive `when` (no `else`). Audit cost is mechanical after fn-14; the only consumer site is `Pilot.kt:178-192`.
- `tailwindComponentKnots` is pure; total over its input domain; returns Double (no truncation).
- `Knots.unsafe(N)` reuse — positive-only invariant prevents `Knots(0)` (which would be doctrinally nonsensical for tailwind).
- Fail-closed semantics on the recognition predicate: every null input → no event → no GA (same discipline as fn-14).
- `applyTailwindGoAround` distinct from `applyCrosswindGoAround` (anti-decision pinned). Future doctrinal divergence (e.g. tailwind-specific climb gradient for jet-class) extends the body of `applyTailwindGoAround` only.
- Shared eligibility helper `isReactiveGoAroundEligible` keeps both branches in sync (single point of change if mission-shape predicate widens).
- The `Crosswind.kt → WindComponents.kt` rename + shared eligible-step constant + shared eligibility helper are explicit consolidation moves per `feedback_pass_scope.md` ("bigger chunks").
- Branch ordering DA → tailwind → crosswind is a deliberate doctrinal decision (Decision #5), not an accident of code arrangement — pinned by unit test. `pilotDecide`'s `when` arm order matches for reader clarity (codex round-1 cosmetic fix).
- **No fictitious legal authority in `RegulationDatabase`** (codex round-1 closure). Manufacturer values stay in `AircraftType` KDoc; only verifiable doctrinal/procedural anchors (AFH guidance, ICAO procedural law) land in the regulation database.

### Test architecture axis
- Three-layer pin pattern (sibling of fn-14 R12).
- Pilot-side unit tests cover full discriminator matrix incl. mission-shape eligibility, recognition-ordering between tailwind and crosswind, and boundary at the strict `>` predicate.
- World-only test trigger discipline (per `feedback_world_only_test_triggers.md`).
- Regression: every fn-14 spec stays GREEN unchanged; the rename + shared-symbol refactors are zero-behaviour-change.
- Both-axis ordering pin lives in pilot unit tests (sim test does not depend on the ordering — only one axis is exercised at a time).
- Event-count pin lives in pilot unit tests (`PilotTailwindHysteresisTest`), not sim test (per fn-14 codex issue #8 discipline).

### Impact axis
- Zero firewall change — `PilotInput.weatherByAerodrome` widens in fn-14, nothing here.
- Zero wiring change — `PilotWiring.buildPilotInput` unchanged.
- `AircraftType` field addition: sealed `data object` leaves mean no constructor-call-site churn (singletons carry their values internally; mirrors fn-14's `maxCrosswindKnots` add).
- `derivePilotEvent` signature **unchanged** (already accepts `weather: WindReport?` from fn-14) — no call-site churn at `Pilot.kt:178`.
- `pilotDecide` exhaustive `when` adds one arm — single call-site change, audit-grade.
- `Crosswind.kt → WindComponents.kt` rename touches: `Crosswind.kt` itself (rename); import statements in `PilotEvent.kt`, `CrosswindHelperTest.kt`, `PilotEventCrosswindTest.kt` (grep verified). Zero semantic change.
- `CROSSWIND_ELIGIBLE_STEPS → WIND_REACTIVE_ELIGIBLE_STEPS` rename touches `PilotEvent.kt` only (private symbol). Zero impact outside the file.
- Controller-side: zero change. Compile-impact: zero (no public shape change).
- Migration cost: lowest of any fn-1x epic — pure additive sibling.

### Operational axis
- Determinism: tailwind helper is pure; per-tick recognition is pure (same as crosswind).
- Tick-rate independence: predicate evaluates against current world wind state at each tick.
- Replay / observability: new `TailwindLimitExceeded` event appears in pilot trace ledger distinct from crosswind event.
- Performance: O(1) per-tick predicate evaluation; negligible.
- No PRNG, no async IO.
- Doctrine drift surface: POH/FCOM values can change per edition — verification at task time is part of the implementation discipline; cited per-leaf KDoc surfaces the source clearly, and the per-type severity framing prevents conflating advisory with hard limitation.

## Early proof point

**Task fn-15.1** validates recognition + GA effect via pilot-side unit tests (no sim harness). If the predicate misfires (sign error in cos, Magnetic frame mix-up, boundary at limit), the unit tests catch it before .2's sim test runs. Mirrors fn-14's early proof point at .1.

## References

### Doctrinal
- **Cessna 172S NAV III / 172R POH §2 (Operating Limitations)** — Cessna POH does NOT publish an explicit hard tailwind limitation (verified pre-plan). C172 leaf cites the absence + uses AFH-derived advisory.
- **Boeing 737-800 FCOM Limitations §1** — 15 kt steady tailwind on dry runway (hard limitation). Verify edition at task time.
- **FAA Airplane Flying Handbook (FAA-H-8083-3C) Chapter 9** — tailwind landing high-risk operations; the industry-standard advisory anchor for the C172 leaf.
- **ICAO Doc 4444 §7.11.6** — 5 kt tailwind for reduced runway separation minima (peer doctrine; scope distinct from POH performance).
- **ICAO Annex 6 Part II §2.4** — PIC final authority (anchor for autonomous GA transmission). Already in `RegulationDatabase` via fn-14.
- **CAP 413 §4.66** (Ed 24 — formerly §4.67 in Ed 23, renumbered per fn-17.1) / **ICAO Doc 4444 §12.3.4.18** — pilot-initiated GA phraseology (no ATC permission needed). Already in `RegulationDatabase` via fn-14.
- **FAA AIM §7-1-12.d.3** — wind reference frame (Magnetic for ATC voice; same anchor as fn-14). Already in `RegulationDatabase`.

### Codebase prior art
- **fn-14** (G3a-react crosswind) — sibling epic; the entire reusable machinery (wind sensing channel, weather projection, `windForMission`, `RunwayId.headingDegreesMagnetic`, `WIND_REACTIVE_ELIGIBLE_STEPS`, three-layer pin pattern, `applyCrosswindGoAround` body shape, world-only test trigger discipline, GA-PRE-CLEAR / GA-POST-CLEAR interrupts being trigger-agnostic) ships in fn-14 and is reused here.
- **fn-12.2** (Pilot ATC-initiated GA) — reactive-GA Tick A pattern (`route = None`, `phase = Final` retained). Sibling shape; both `applyCrosswindGoAround` and `applyTailwindGoAround` mirror this.
- **fn-11** (G3a-trained) — `CircuitOutcome` ADT + `planMission` compiler; the trained-GA Tick A is the same intent shape.
- **fn-8** (G1) — commitment-lifecycle GA-PRE-CLEAR/GA-POST-CLEAR interrupts (reused unchanged; trigger-agnostic).
- **fn-5** (G2) — sim test harness pattern.

### Memory
- `feedback_world_only_test_triggers.md` — test authors wind state, not pilot decision.
- `feedback_firewall_principle.md` — no firewall widening this pass (fn-14 already widened; this is additive within the existing surface).
- `feedback_reality_anchored.md` — POH/FCOM values are real published manufacturer data; doctrinal severity varies per type; pretending all are uniform would soften the deferment.
- `feedback_no_corners.md` — fail-closed throughout; no silent workarounds; strict `>` boundary discipline; **no fictitious legal authority in `RegulationDatabase`**.
- `feedback_pass_scope.md` — `WindComponents.kt` rename + shared symbol + shared helper folded into this pass (bigger chunks).
- `feedback_review_discipline.md` — three-agent plan review + post-impl review; never cut ceremony for "small" passes.
- `feedback_plans_review_aware.md` — Review considerations addressed inline (FP, tests, impact, ops).
- `feedback_impact_assessment.md` — plan addresses impact inline; agent does not auto-run impact agent.
- `feedback_draft_revisions.md` — single rewrite per review fold; small Edits are for production code.
- `sim-test-pins-must-compare-against-2026-05-10` — decision-cycle time discipline; same-cycle uses `<=`.
- `ga-path-precedence-reorder-when-adding-2026-05-10` — re-derive precedence when adding GA paths (Decision #5: DA → tailwind → crosswind).
- `project_rich_world_domain.md` — wind state SHOULD live on `Aerodrome.weather`; deferred (carried by fn-16).
- `project_phase5_6_status.md` — Phase 5+6 status; the trilogy + react closure pattern.
- `feedback_llm_boundaries.md` — typed boundaries; pure-inside (recognition is pure derivation).

### External (verify at task time)
- [Cessna 172S NAV III POH](https://cessna.txtav.com/) — Section 2 Operating Limitations (current edition: no published hard tailwind component limit; advisory derived from AFH).
- [Boeing 737-800 FCOM Limitations](https://boeing.com/) — §1 hard limitations including 15 kt tailwind on dry runway.
- [FAA AFH Ch 9](https://www.faa.gov/sites/faa.gov/files/regulations_policies/handbooks_manuals/aviation/airplane_handbook/10_afh_ch9.pdf) — crosswind + tailwind landing techniques.
- [ICAO Doc 4444 §7.11.6](https://store.icao.int/) — reduced runway separation minima.
- [FAA AIM §7-1-12](https://www.faa.gov/air_traffic/publications/atpubs/aim_html/) — wind reference frame.

## Deferments register

Deferments from this epic. The user maintains the register at `~/.claude/plans/pilot-firewall.md` § Deferments register; these are filed there:

- **`D-PASS-g3a-react-tailwind-gust-evaluation`** — gust-peak evaluation against POH tailwind limit. Mirror of fn-14's `D-PASS-g3a-react-gust-evaluation`. v1 reads steady-state `Wind.speedKnots` only.
- **`D-PASS-g3a-react-multi-aircraft-tailwind`** — multiple aircraft on same runway when wind shifts to tailwind; sequencing of simultaneous GAs. Sibling of fn-14's multi-aircraft deferment.
- **`D-PASS-g3b-react-cross-aerodrome-tailwind`** — same scenario at LJMB or other aerodrome. Fixture variation; reuses all machinery.
- **`D-PASS-g3a-react-combined-wind-vector`** — combined crosswind + tailwind decision (resultant evaluation, weakest-link). Real PICs evaluate the vector; v1 evaluates each axis independently.
- **`D-PASS-g3a-react-tailwind-atis-cadence`** — wind via ATIS broadcast (slower, coarser cadence) for the tailwind branch. v1 reuses fn-14's world-truth weather observation path.
- **`D-PASS-g3a-react-tailwind-condition-corrections`** — runway-condition / displaced-threshold / pressure-altitude / temperature corrections to POH max tailwind. v1 uses POH/AFH constant.
- **`D-PASS-g3a-react-tailwind-personal-minimums`** — pilot judgement layer (PIC margin below typed value). Sibling of fn-14's personal-minimums deferment.

## Closures

- **G3a-react second axis lands.** First reactive-GA epic (fn-14) established the pattern with crosswind; this epic proves the pattern generalises by adding the tailwind axis with zero firewall change, zero wiring change, zero controller change. The per-type doctrinal severity asymmetry (advisory for C172 / hard limitation for B738) is captured in KDoc; the code shape is symmetric.
- **`AircraftType` POH-derived data accretes.** `maxCrosswindKnots` (fn-14) + `maxTailwindKnots` (this epic). Future POH/FCOM-derived reactive triggers (density altitude, weight limits, temperature limits) follow the same shape: one typed field per limit, one independent branch in `derivePilotEvent`, one distinct applier sibling, one event leaf.
- **Five-path reactive-GA coverage at sim-test level.** Self-initiated DA + trained + ATC-obstruction + crosswind + tailwind. CONTINUE APPROACH (fn-13) as the non-GA peer.
- **Per-type doctrinal severity asymmetry becomes first-class in the codebase.** Carried via KDoc + `AircraftType` per-leaf source citations + (deliberately narrow) `RegulationDatabase` entries; the codebase no longer pretends uniform POH severity.
- **`RegulationDatabase` discipline tightened.** Codex round-1 closure: manufacturer values are not regulations; `RegulationDatabase` is reserved for verifiable doctrinal/procedural anchors (AFH guidance, ICAO procedural law). Per-aircraft sources stay in `AircraftType` KDoc.

## Requirement coverage

| Req | Description | Task(s) |
|-----|-------------|---------|
| R1  | `AircraftType.maxTailwindKnots: Knots` field + per-leaf source-cited values + per-type severity framing in KDoc | fn-15.1 |
| R2  | `Crosswind.kt → WindComponents.kt` rename + import-update | fn-15.1 |
| R3  | `tailwindComponentKnots(...): Double` pure helper + unit tests | fn-15.1 |
| R4  | `PilotEvent.TailwindLimitExceeded` leaf + exhaustiveness audit | fn-15.1 |
| R5  | `derivePilotEvent` third independent branch (DA → tailwind → crosswind ordering) | fn-15.1 |
| R6  | Shared `isReactiveGoAroundEligible` mission-shape helper extraction | fn-15.1 |
| R7  | `CROSSWIND_ELIGIBLE_STEPS → WIND_REACTIVE_ELIGIBLE_STEPS` rename + shared between branches | fn-15.1 |
| R8  | `applyTailwindGoAround` distinct applier (identical body to `applyCrosswindGoAround`; cross-ref KDoc) | fn-15.1 |
| R9  | `pilotDecide` precedence extension (5-arm exhaustive `when` written in DA → tailwind → crosswind dispatch order) | fn-15.1 |
| R10 | Pilot-side unit tests (helper, recognition, applier, hysteresis, TouchAndGo variant, Tick A→B integration, ordering pin, regression of fn-14 specs) | fn-15.1 |
| R11 | `G3aPilotReactiveTailwindTest.kt` sim test (observable behaviour only; three-layer pins) | fn-15.2 |
| R12 | Cross-reference doc updates (9 repo-internal sites + 8 sibling test docstrings) | fn-15.2 |
| R13 | RegulationDatabase entries (AFH guidance + ICAO Doc 4444 §7.11.6 guidance; codex-tightened scope) | fn-15.1 |
| R14 | Full verify GREEN (9 goldens) | fn-15.1, fn-15.2 |

## Done summary

_(filled per task during implementation)_

## Evidence

_(filled per task during implementation)_
