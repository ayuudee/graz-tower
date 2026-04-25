---
status: v2.1 — round-2 residuals applied; ready to execute
date: 2026-04-24
ticket: G1 (.plan)
scope: outbound only — LOWG → LJMB (return leg deferred to G1b)
revision: 2.1
---

# G1 — Cross-aerodrome VFR outbound (LOWG → LJMB)

A single VFR aircraft departs LOWG (RWY 16C, southbound), retunes from LOWG
TWR to a Wien-Information FIS frequency on `ContactFrequency` instruction,
crosses the **TMA Maribor** boundary at the published entry waypoint
**PETOV**, self-initiates contact with MARIBOR APPROACH, is then handed by
APP to MARIBOR TOWER on the **CTR Maribor** boundary at REP **MN1**, joins
RWY 14 right base via the MN1→MN2 corridor (right-hand circuits), and lands.
Three controllers (LOWG_TWR, LJMB_APP, LJMB_TWR) coexist in a merged
`AviationWorld`.

Two distinct frequency-transition modes are exercised:

- **Controller-initiated handoff** at LOWG zone exit (existing machinery —
  `ContactFrequency` to FIS, with the FIS frequency unobserved by any
  simulated controller) and at LJMB CTR boundary APP→TWR (same-aerodrome,
  existing machinery).
- **Pilot-initiated contact** at the TMA Maribor boundary (FIS→APP) — the
  *only* genuinely new path, used because no FIS controller exists in the
  simulation to transfer the aircraft.

This is the proof that the multi-aerodrome scaffold (M7 in `.plan`) carries
real traffic end-to-end.

---

## 0. Goal and non-goals

**In scope (G1 outbound):**

- One aircraft, VFR, southbound; route via published reporting points
  (PETOV for TMA-Maribor entry, MN1→MN2 corridor for CTR-Maribor entry).
- Wind forced from the south so `selectRunwayIntoWind` picks 16C at LOWG
  and 14 at LJMB. RWY 14 has right-hand circuits per
  `cad/airports/ljmb.manifest.json` — join is **right base** or right
  downwind from MN-corridor.
- LOWG→FIS: controller-initiated `ContactFrequency` at zone exit; FIS
  segment has no simulated controller.
- FIS→APP (TMA boundary): pilot-initiated contact; new architectural path.
- APP→TWR (CTR boundary): controller-initiated, same-aerodrome, existing
  machinery.
- ATIS is *static* per aerodrome (LOWG_W, LJMB_A); pilot populates
  `InitialContact.atisCode`.

**Explicitly out of scope (deferred — tracked in `.plan`):**

- Return leg (LJMB → LOWG); G1b.
- IFR, special VFR, wake turbulence, LVPs.
- RT phraseology layer; transmissions are typed structures only.
- Multi-traffic / approach sequencing.
- `FrequencyChangeRequest` from pilot to controller before LOWG zone exit
  — modelled as v1 simplification (LOWG_TWR proactively issues
  `ContactFrequency` at zone exit; pilot does not request first).
  Tracked as **G1-DEF-1** in the deferral list (§9).

**Documented v1 simplifications** (review-1 surfaced these; called out
explicitly so they are not silent corners-cut):

- **S1.** "FIS segment" is *uncontrolled by simulation*, not uncontrolled
  airspace. Real flights work Wien Information / Graz Information here.
  Modelled as no controller present; pilot transmissions during the
  segment go nowhere.
- **S2.** APP/TWR positions at LJMB are split. Real LJMB combines them
  routinely. The split is the architectural proof of three-controller
  coexistence; combined operation is a future variation.
- **S3.** Static ATIS letter per run; no time-varying ATIS update.
- **S4.** Pilot does not pre-request `FrequencyChangeRequest` at LOWG
  zone exit (G1-DEF-1).

---

## 1. Research synthesis (what already exists)

| Concern | State of codebase | File / line |
|---|---|---|
| Multi-aerodrome `AviationWorld` | Already keyed by `Map<AerodromeId, Aerodrome>` | `core/.../AviationWorld.kt` |
| Multi-aerodrome merge | `WorldCandidateLoader.mergeAviationWorlds` (first-wins) | `migration/.../WorldCandidateLoader.kt` |
| Multi-aerodrome scaffold proven | LOWG_TWR + LJMB_TWR coexist, scope-isolated views | `sim/.../MultiAerodromeWorldTest.kt` |
| `ControllerSpec.aerodromeId` | Already exists, `buildControllerView` reads it | `sim/.../ControllerSpec.kt`, `ControllerWiring.kt:51` |
| Active runway from wind | `selectRunwayIntoWind` implemented | `controller/.../assess/RunwayAssessment.kt:323` |
| `ControllerView.weather` | Field exists; `buildControllerView` currently sets `null` | `controller/.../ControllerTypes.kt:20`, `ControllerWiring.kt:57` |
| Controller-initiated handoff | `ContactFrequency` → `FrequencyReadback` → `InitiateHandoff` → `PendingHandoff`; **target lookup is aerodrome-scoped** | `controller/.../Controller.kt`, `Step.kt:548-573` |
| `applyContactFrequency` aerodrome-scoping | `findRoleController(state, current.aerodromeId, instruction.role)` — same-aerodrome only | `Step.kt:557` |
| `handlePilotTick` transmission gating | Routes only to controllers with the aircraft in `responsibilities`; drops transmissions if none observe | `Step.kt:147-171` |
| `HighLevelGoal` variants | 4 exist: `Departure`, `Arrival`, `CircuitTraining`, `Transit` | `sim/.../PilotMission.kt:13-20` |
| Pilot route planner | Visual mode supports `Depart` and `Transit` separately; `FLY_DEPARTURE` ends at rotation, not zone exit | `sim/.../PilotRoutePlanner.kt` |
| `InitialContact` typed shape | Already carries `stationCalled, aircraftType?, position?, level?, atisCode?, intention?` — all nullable | `protocol/.../PilotTransmission.kt:179-186` |
| `Wind` type | `Wind(direction: Heading, speedKnots: Knots, ...)` | `protocol/.../Instruction.kt:124` |
| LJMB TMA entries (APPROACH, beforeEntry) | GOLVA, MUREG, **PETOV**, OBUTI, NIDLO, TISKO, DIMLO, MS2, MS3, ME3, ME4, MW1 | `cad/airports/ljmb.manifest.json` |
| LJMB CTR REPs (TOWER, beforeEntry) | OSMOT, LAPNA, **MN1**, **MN2**, ME2, MS1, IRLIX, ME1, MIRSO | `cad/airports/ljmb.manifest.json` |
| LJMB RWY 14 circuit direction | `northDirection: "RIGHT_HAND"` | `cad/airports/ljmb.manifest.json` |

**Three findings from review-1 that change the architecture:**

1. The protocol's `InitialContact` already has the fields phraseology
   review (B8) asked about (position, level, intention). G1 only needs
   to *populate* them — no protocol change.
2. `applyContactFrequency` is aerodrome-scoped (impact-review B1).
   Cross-aerodrome handoff cannot reuse it without refactor.
3. `handlePilotTick` drops pilot transmissions when no controller observes
   (impact-review B2). Pilot-initiated contact in the FIS segment cannot
   work without first granting target-side responsibility.

---

## 2. Architecture decisions

### A1 — `HighLevelGoal.VfrCrossAerodromeTransit`

```kotlin
data class VfrCrossAerodromeTransit(
    val from: AerodromeId,
    val to: AerodromeId,
    val tmaEntry: FixId,        // PETOV for LOWG→LJMB outbound
    val ctrEntry: FixId,        // MN1 for the MN-corridor join to RWY 14
    val joinLeg: LegName,       // RIGHT_BASE for RWY 14 from NW
) : HighLevelGoal
```

The two-tier boundary structure (TMA entry, then CTR entry) is **not**
collapsed into a single waypoint. Single-waypoint mode in v1 of the plan
was wrong (atc-general B3): the manifest's `entryExitPointRefs` lists are
disjoint by purpose.

HTN decomposition:

```
VfrCrossAerodromeTransit(LOWG, LJMB, PETOV, MN1, RIGHT_BASE)
└── DepartureAt(LOWG)              → existing departure subtree, ends at zone exit
└── EnRoute(via=[PETOV])           → primitive FLY_EN_ROUTE; transition target = TMA boundary
└── ArrivalAt(LJMB, via=[MN1, MN2], joinLeg=RIGHT_BASE) → existing arrival subtree adapted
```

Transit waypoints (PETOV, MN1, MN2) are *route waypoints*, not goal
parameters; the *goal* parameters are `tmaEntry` and `ctrEntry` so the
planner knows which boundary each waypoint anchors.

The existing `Transit(destination?)` is **not** repurposed (it's
single-aerodrome zone-exit per A6 fix in `.plan`).

**Totality audit checklist (M-FP-3, mandatory before G1.3 code commit):**

```bash
grep -rn "is HighLevelGoal\.\|when (mission\.goal)\|when (goal)" \
    sim/src/commonMain/ controller/src/commonMain/
```

Every match must be either (a) `when`-as-expression with no `else ->`, or
(b) explicitly amended for the new variant. Result of the grep is logged
in the G1.3 commit message. No new variant lands without the audit.

### A1.1 — Sealed `RouteTransition` for FLY_EN_ROUTE → FLY_ARRIVAL

Per FP review N2, the en-route → arrival edge is typed:

```kotlin
sealed interface RouteTransition {
    data class DepartureToEnRoute(val zoneExitPoint: PointId) : RouteTransition
    data class EnRouteToArrival(val tmaEntryPoint: FixId) : RouteTransition
    data class ArrivalToCircuit(val joinLeg: LegName) : RouteTransition
}
```

`PilotMission.routeTransitions: List<RouteTransition>` is computed at
mission creation from the goal parameters. The pilot consults the head
of the list at each tick; on completion the transition pops and the
phase advances. No `else ->` dispatch on phase changes anywhere.

### A2 — `PilotAirspace.kt` — stateless typed helpers

Per OQ2: stateless pure functions, no caching layer.

```kotlin
// sim/src/commonMain/.../PilotAirspace.kt

object PilotAirspace {

    sealed interface AirspaceLookupError {
        object NoAirspaceData : AirspaceLookupError      // world has no volumes
        object PointOutsideAllVolumes : AirspaceLookupError
    }

    sealed interface BoundaryError {
        object NoAirspaceData : BoundaryError
        object NoCrossingWithinHorizon : BoundaryError
    }

    /**
     * The volume containing [point], by class precedence (A < B < C < ...).
     * Distinguishes "world has no airspace" from "point outside all volumes."
     */
    fun currentVolume(world: AviationWorld, point: Point): Either<AirspaceLookupError, AirspaceVolume>

    /**
     * Next boundary crossing along [heading] from [from], up to [horizonM] metres.
     */
    fun nextBoundaryCrossing(
        world: AviationWorld,
        from: Point,
        heading: Heading,
        horizonM: Double,
    ): Either<BoundaryError, BoundaryCrossing>

    /**
     * If the published rules require a frequency change at this point, return it.
     * `None` means no rule fires here. This is *not* an error — most points have no trigger.
     */
    fun frequencyChangeTriggerAt(
        world: AviationWorld,
        point: Point,
        currentFrequency: Frequency,
    ): Option<FrequencyChangeTrigger>
}
```

`Either` for "did the lookup actually succeed?" / "Option" for "was a
trigger published at this point?" — the type distinguishes the cases the
review (FP M2) flagged.

### A3 — Three transition modes (review-1 reframing)

**A3a — LOWG → FIS (controller-initiated, existing path).**
At LOWG zone-exit predicate, LOWG_TWR emits
`ContactFrequency(freq = WIEN_INFO, role = RoleName.FIS)`. Pilot reads
back, retunes. `applyContactFrequency` looks up a controller with role
FIS at LOWG; finds none; result: LOWG_TWR drops responsibility, no
controller picks up. The aircraft is now in the "no observer" state.

**Wording precision (impact-2 fix):** the existing function at
`Step.kt:560-563` already handles `targetId == null` as drop-and-don't-
add. There is no latent `error()`. The G1 change is to **make the
existing no-target branch load-bearing** — currently it is dead code,
because no production caller ever issues `ContactFrequency` to a role
that doesn't resolve. G1.5 also adjusts the early-return at
`Step.kt:555` so an aircraft that is *already* uncontrolled (FIS state)
can still receive a `ContactFrequency`-like primitive call from the
pilot-initiated path. Signature after G1.5:

```kotlin
private fun applyContactFrequency(
    state: SimState,
    aircraftId: AircraftId,
    instruction: ContactFrequency,
): Either<TransferError, SimState>
```

Internally it now delegates to `transferResponsibility`. Existing
non-error callers continue to see `Right(state')`.

**Target-aerodrome decision (impact-1 fix):** for the controller-
initiated path, the target aerodrome is implicit-from-current-owner
(`current.aerodromeId`). `ContactFrequency` is **not** extended to
carry a target aerodrome — that would propagate the cross-aerodrome
concern through every controller-side call site for no benefit, since
controller-initiated handoff is always same-aerodrome by construction.
The pilot-initiated path supplies its own target aerodrome explicitly.

**A3b — FIS → APP (pilot-initiated, the only genuinely new path).**
A new cognitive rule in `PilotCognitive.kt` fires when:

1. Pilot is airborne.
2. `PilotAirspace.frequencyChangeTriggerAt(...)` returns
   `Some(FixedTrigger(targetRole = APPROACH, targetAerodrome = LJMB,
   targetFreq = LJMB_APP_FREQ))` — derived from the manifest's
   `contactRequirement` table.
3. Mission is `VfrCrossAerodromeTransit`.
4. Aircraft is **outside** TMA Maribor right now (the *before-entry*
   constraint).
5. **Aircraft is currently uncontrolled** (impact-3 fix) — no controller
   in `state.controllers` has the aircraft in `responsibilities`. This
   prevents the rule firing while LOWG_TWR or any other controller still
   owns the aircraft (e.g. before LOWG zone exit completes), which the
   geometric "outside TMA" gate alone admits.

Effect: pilot emits `InitialContact` on the new frequency at the next
tick. To make `handlePilotTick` route the transmission (B2), a new sim
primitive is invoked *before* the transmission emits:

```kotlin
// sim/src/commonMain/.../Step.kt
fun transferResponsibility(
    state: SimState,
    aircraftId: AircraftId,
    fromAerodrome: AerodromeId?,   // null for "currently uncontrolled"
    toAerodrome: AerodromeId,
    role: RoleName,
): Either<TransferError, SimState>
```

`transferResponsibility` is the primitive both controller-initiated
handoff (extracted from current `applyContactFrequency`) and pilot-initiated
contact (new) call. It takes explicit aerodromes — no implicit lookup of
"current owner's aerodrome." The cross-aerodrome case is now expressible.

**A3c — APP → TWR (controller-initiated, same-aerodrome, existing path).**
At LJMB CTR boundary predicate, LJMB_APP emits
`ContactFrequency(freq = LJMB_TWR_FREQ, role = TOWER)`. Existing same-
aerodrome handoff machinery works as-is; no architectural change.

**Reversal coverage** (Commandment "reversal before forward"):

- A3b: pilot transiently re-exits TMA → trigger fires again with
  previous frequency; LJMB_APP responsibility drops back to "no
  observer." Test: `boundary re-cross drops APP responsibility`.
- A3c: same as existing controller-handoff reversal.

### A4 — Cross-aerodrome route planning

Extend Visual-mode dispatch in `PilotRoutePlanner`:

- New branch: `goal is VfrCrossAerodromeTransit`.
- Route shape: `[ground path] ++ [departure runway centerline → zone exit]
  ++ [direct to TMA entry waypoint] ++ [TMA-entry → CTR-entry corridor]
  ++ [CTR-entry → joinLeg → final → threshold]`.
- Right-hand circuits at LJMB are read from
  `cad/airports/ljmb.manifest.json` `circuitProjection` — `joinLeg =
  RIGHT_BASE` is supplied by the goal, not inferred.
- Typed errors:
  - `RoutingError.MissingTransitWaypoint(ident)`
  - `RoutingError.NoEntrySequenceForRunway(aerodrome, runway)`
  - `RoutingError.NoArrivalRunway(aerodrome)` (FP advisory N1) — when
    weather-driven runway selection is unavailable at the destination.

### A5 — Wind in `ControllerView` via validated `weatherByAerodrome`

```kotlin
data class SimState(
    // existing fields ...
    val weatherByAerodrome: Map<AerodromeId, WeatherObservation>,
)
```

**No data-class default** (FP M3 fix): the `= emptyMap()` default is
removed so direct constructor / `.copy()` callers must supply weather
explicitly. Existing test sites that construct `SimState` directly fail
to compile until they pass the field; this is intentional — review of
each site is the audit.

`WeatherObservation.wind: Wind?` remains nullable; its semantics are
the controller's: "no wind information available." `selectRunwayIntoWind`
is unchanged in signature (returns `RunwayId?`); a null wind continues
to return null, and downstream controller logic that requires an active
runway already handles "no active runway yet" by deferring instruction
issuance. **Tracked as G1-DEF-7** for typed-absence treatment.

Validation lives in the smart constructor (FP M3 fix):

```kotlin
companion object {
    sealed interface SimStateInitError {
        data class MissingWeatherForAerodromeWithRunways(
            val aerodromeId: AerodromeId,
        ) : SimStateInitError
    }

    fun initial(
        seed: Long,
        world: AviationWorld,
        worldIndex: WorldIndex,
        aircraft: List<AircraftState>,
        controllers: List<ControllerSpec>,
        weatherByAerodrome: Map<AerodromeId, WeatherObservation>,
    ): Either<SimStateInitError, SimState>
}
```

`SimState.initial(...)` returns `Either<SimStateInitError, SimState>` and
rejects worlds where any aerodrome with at least one runway has no
weather entry. Tests that don't care about weather pass an explicit
`WeatherObservation(wind = null, ...)` — the field is optional within
the observation, but its **presence in the map** is mandatory for any
runway-bearing aerodrome. This converts the silent fallback into a
loud constructor failure.

`buildControllerView` reads from the map; controllers without weather
observe `weather = null` *only if* their aerodrome's `WeatherObservation`
has `wind = null`. There is no path where a controller sees `weather =
null` because its aerodrome was never seeded. Commandment 1 satisfied.

**`SimState.copy(...)` audit (FP N3):** ~20 sites in `Step.kt`; field is
fixture-set, never mutated by step logic; default-empty preserves
existing tests but tests using `initial(...)` get the validation.

### A6 — Test fixture `G1OutboundLowgToLjmbTest.kt`

Phases P0–P12 (one extra over v1: TMA entry and CTR entry are now
distinct phases per atc-general B3). Each phase asserts a precondition
and then `runUntil(predicate)` with a step budget.

- **P0** — World loaded, three controllers seated, weather seeded
  (south wind for both aerodromes; `SimState.initial(...)` returns
  Right). `selectRunwayIntoWind` picks 16C at LOWG, 14 at LJMB.
- **P1** — Aircraft spawned at LOWG holding point; mission =
  `VfrCrossAerodromeTransit(LOWG, LJMB, PETOV, MN1, RIGHT_BASE)`;
  planner emits route.
- **P2** — Initial contact LOWG_TWR, departure clearance, takeoff
  clearance read back.
- **P3** — Airborne, climbing on departure track; LOWG_TWR observes the
  aircraft.
- **P4** — At LOWG zone exit predicate, LOWG_TWR issues
  `ContactFrequency(WIEN_INFO, FIS)`; pilot reads back, retunes;
  LOWG_TWR drops responsibility; no controller now observes.
- **P5** — En-route FIS segment, no controller observes; pilot
  transmissions during this segment go nowhere (asserted: inboxes
  remain empty).
- **P6 (TMA)** — Approaching PETOV from north, **before crossing the
  TMA boundary** (asserted by `currentVolume` returning a volume with
  class < TMA Maribor's class), pilot self-initiates
  `PilotMessage(callsign, [InitialContact(stationCalled = APPROACH,
  position = "north of PETOV", level = current cruise level,
  intention = ARRIVAL, atisCode = 'A')])` (phraseology fix:
  `InitialContact` is wrapped in a `PilotMessage` envelope carrying
  the aircraft callsign per `PilotTransmission.kt:334`).
  `transferResponsibility` plants APP responsibility on the aircraft
  *before* the transmission is queued; `handlePilotTick` routes the
  InitialContact to LJMB_APP's inbox.
- **P7 (TMA inside)** — Aircraft has crossed TMA boundary; LJMB_APP
  issues join instructions for RWY 14 via MN-corridor (right base —
  see G1-DEF-6 for the working-assumption flag). **APP round-trip
  must complete before P8 becomes eligible** (impact-4 fix): the
  P7→P8 predicate guard requires LJMB_APP to have observed the
  aircraft for at least one full decide cycle, issued at least one
  join instruction, and recorded its readback. This prevents the
  "APP issues TWR handoff before pilot's InitialContact is processed"
  race.
- **P8 (CTR)** — Predicate is "5 minutes flight-time before MN1"
  (atc-general B-OPS-6 fix; the manifest's 5-minute lead time is the
  *pilot→TWR contact-establishment* obligation, so APP must issue the
  handoff *before* MN1 to give the pilot the published lead). LJMB_APP
  issues `ContactFrequency(LJMB_TWR_FREQ, TOWER)`; pilot reads back;
  existing same-aerodrome handoff transfers responsibility to
  LJMB_TWR.
- **P9** — LJMB_TWR issues landing clearance; readback recorded.
- **P10** — Touchdown on RWY 14; LJMB_TWR issues
  `AfterLandingVacateVia(target, exit = TWY_X, whenAble = false)`;
  pilot reads back.
- **P11** — After vacate, LJMB_TWR issues
  `TaxiTo(stand, via = [taxiway-list])` (phraseology fix: vacate +
  taxi-to are distinct typed instructions per `Instruction.kt:460` /
  `:597`); pilot reads back; aircraft taxis to apron.
- **P12** — Aircraft at stand; mission complete.

**Cross-cutting invariants (test-review M7/M8):**

- **I1.** At each tick, *at most one* controller's `responsibilities`
  contains the aircraft.
- **I2.** **Monotone responsibility history** — once LOWG_TWR drops
  responsibility (P4 onwards), `lowgView.aircraft` is empty for the
  *rest of the run*. Once LJMB_APP drops responsibility (P8 onwards),
  `ljmbAppView.aircraft` is empty for the rest of the run. This is the
  "no leakage across aerodromes" guarantee.
- **I3.** Every `controllerDecide` instruction's `aerodromeId` matches
  the calling controller's spec aerodrome (test-review M7 — replaces
  the weak "no null" invariant).
- **I4.** No `RoutingError` returned by any pilot decision.
- **I5.** `aircraft.currentFrequency` matches some controller's
  frequency or is the FIS frequency (no controller).

---

## 3. Open questions — resolved

- **OQ1** — Decompose to existing primitives rather than introducing new
  primitive task types? → **Agree.**
- **OQ2** — Stateful or stateless `PilotAirspace`? → **Stateless.**
- **OQ3** — Static per-aerodrome weather sufficient for G1? → **Sufficient.**
- **OQ4** — Three controllers, no Vienna FIS controller? → **Yes.**

---

## 4. Build sequence (~26h, +2h over v1 for the architectural primitive)

### G1.0 — Citation verification + manifest enrichment (1h, **DONE**)

Two distinct deliverables, separated for honesty:

- **Citation verification (done):** §6.4 / §6.5 citations corpus-verified
  against `research/txt/` extracts. Multiple round-1+2 citations turned
  out to be wrong against actual section text and were corrected.
  Annex 11 source is not in the corpus; Annex 11 entries flagged.
- **Manifest enrichment (done):** `cad/airports/{lowg,ljmb}.manifest.json`
  gained a `frequencies` block sourced from X-Plane apt.dat. The data is
  now *available* in the manifest for downstream consumers, replacing
  the v1 plan's hand-typed `G1Frequencies.kt` constants file (test-review
  M4 fix). LOWG `towerRadarVfr` is named explicitly to avoid spawning a
  phantom `RoleName.APPROACH` (atc-general round-2 finding); the runway
  designator `16C/34C` carries a `designatorNote` flagging it as project-
  internal disambiguation against the grass strips.
- **Manifest *consumption* (deferred to G1.6):** the spot-check tests
  (G1.1 `WindActiveRunwayTest`) hand-construct frequencies they don't
  semantically depend on. The integration test (G1.6
  `G1OutboundLowgToLjmbTest`) is where manifest-driven frequency
  consumption actually pays off — the test cannot be written without
  it. A typed `AerodromeFrequencies` decoder lands then (G1-DEF-8).

### G1.1 — Wind threading + smart constructor (3h)

- Add `weatherByAerodrome` to `SimState`.
- Convert `SimState.initial(...)` to the validated `Either`-returning
  smart constructor (A5).
- `buildControllerView` reads from the map.
- `WindActiveRunwayTest`: south wind → 16C / 14; **plus reverse case**
  north wind → 34C / 32 (test-review fix). End-to-end through
  `selectRunwayIntoWind`.

### G1.2 — `PilotAirspace` helpers (3h)

- `sim/src/commonMain/.../PilotAirspace.kt` per A2.
- Pure unit tests in `PilotAirspaceTest.kt`:
  - **Single load-bearing test:** frequency-change trigger fires at the
    documented LOWG → FIS → TMA Maribor boundaries with the *correct
    target frequency and role* (test-review M5: drop the structural
    monotonicity test; drop the "boundary returns Some" structural
    test).

### G1.3 — `VfrCrossAerodromeTransit` mission goal (4h)

- New `HighLevelGoal.VfrCrossAerodromeTransit` per A1.
- HTN decomposition in `PilotMission.create(...)`.
- `derivePilotGoal` exhaustive on the new variant (visible-behaviour
  test only — TRANSIT en route, ARRIVE near destination — matching
  `CircuitAfterGoAround` style; no shape-only decomposition test per
  test-review M6).
- **Mandatory totality audit checklist (A1) executed and logged before
  this phase commits.**
- **Mandatory impact-agent re-invocation** per
  `feedback_impact_assessment.md`.

### G1.4 — Cross-aerodrome route planning (3h)

- Extend `PilotRoutePlanner.buildVisualModeRoute` per A4.
- New `RoutingError` variants typed as `Either<RoutingError, Route>`.
- Tests:
  - LOWG→LJMB via PETOV → MN1 → MN2 → RWY 14 right base — expected
    waypoint sequence ending at the LJMB threshold.
  - Missing TMA entry waypoint → `MissingTransitWaypoint` (no
    `error()`).

### G1.5 — `transferResponsibility` primitive + pilot-initiated contact (5h)

- Extract `transferResponsibility(from, to, role)` primitive from
  `applyContactFrequency` (impact B1).
- Refactor controller-initiated path to call the primitive (target
  aerodrome = `current.aerodromeId` per A3a target-aerodrome decision).
- Make `applyContactFrequency` exercise its existing no-target branch
  (drop responsibility, do not add to any controller — A3a wording
  fix). Adjust the `Step.kt:555` early-return so an aircraft that is
  already uncontrolled (FIS state) can still receive primitive calls
  from the pilot-initiated path.
- New `pilotInitiatedContact` cognitive rule in `PilotCognitive.kt`
  (A3b). Uses `transferResponsibility` to plant target responsibility
  *before* `handlePilotTick` routes the transmission (impact B2 fix).
  Guard set: airborne + outside TMA + mission is
  `VfrCrossAerodromeTransit` + **aircraft is currently uncontrolled**
  (impact-3 fix).
- **Naming:** `pilotInitiatedContact` (impact A3 advisory) — not
  `selfHandoff`; this is unilateral contact establishment, not a
  handoff protocol.
- Tests:
  - Boundary trigger fires at TMA entry; `transferResponsibility`
    plants APP responsibility before transmit; APP inbox receives
    `InitialContact`.
  - Reversal: aircraft transiently re-exits TMA before APP responds →
    trigger fires for previous frequency; APP responsibility drops back.
  - Negative: no firing on the ground, in circuit training, or while
    any controller still has the aircraft in `responsibilities`
    (the "currently uncontrolled" guard).
  - LOWG zone-exit `ContactFrequency` to FIS exercises the
    no-target-controller branch cleanly; no `error()`.

### G1.6 — Integration test fixture (6h)

- `sim/src/jvmTest/.../g1/G1OutboundLowgToLjmbTest.kt` per A6.
- Phases P0–P12 with predicate-driven `runUntil`.
- Cross-cutting invariants I1–I5 checked at every phase boundary.

### G1.7 — Self-assessment + plan refresh (1h)

- Run principal-agent self-assessment.
- Update `.plan` G1 entry to **DONE**.
- Promote G1-DEF-1 (`FrequencyChangeRequest` modelling) to a tracked
  `.plan` item.

---

## 5. Code change list per file

| File | Change kind | Notes |
|---|---|---|
| `sim/src/commonMain/.../SimState.kt` | extend | `weatherByAerodrome` field; `initial(...)` → smart constructor |
| `sim/src/commonMain/.../ControllerWiring.kt` | extend | thread weather into view |
| `sim/src/commonMain/.../PilotMission.kt` | extend | `HighLevelGoal.VfrCrossAerodromeTransit` + HTN decomposition |
| `sim/src/commonMain/.../PilotAirspace.kt` | new | stateless typed helpers |
| `sim/src/commonMain/.../PilotCognitive.kt` | extend | `pilotInitiatedContact` rule |
| `sim/src/commonMain/.../PilotRoutePlanner.kt` | extend | cross-aerodrome Visual route |
| `sim/src/commonMain/.../Step.kt` | refactor | extract `transferResponsibility`; make `applyContactFrequency` total over absent target |
| `sim/src/commonMain/.../UnifiedPilot.kt` | extend | `derivePilotGoal` exhaustive |
| `protocol/.../*.kt` | none | existing types sufficient (G1-DEF-1 deferred) |
| `controller/.../*.kt` | none | controller side unchanged |
| `sim/src/commonTest/.../PilotAirspaceTest.kt` | new | one trigger test |
| `sim/src/commonTest/.../PilotRoutePlannerTest.kt` | extend | cross-aerodrome cases |
| `sim/src/jvmTest/.../g1/WindActiveRunwayTest.kt` | new | south + north wind |
| `sim/src/jvmTest/.../g1/G1OutboundLowgToLjmbTest.kt` | new | integration |

---

## 6. Review considerations (Commandment 9, with corrected citations)

### 6.1 FP / type safety

- **Totality audit checklist staged before G1.3** (FP M3 fix); no `else
  ->` slips past compiler.
- `PilotAirspace` distinguishes "lookup error" (Either) from "no rule
  fires here" (Option) — FP M2 fix.
- `transferResponsibility` returns `Either<TransferError, SimState>` —
  FP M1 fix; no `null` in the new pathway.
- `weatherByAerodrome` validated at construction — FP M3 fix; no silent
  fallback.
- `applyContactFrequency` total over absent target controller — replaces
  any latent `error("target not found")` with a defined success branch.
- New `RoutingError` variants are typed; no `error()` calls added.
- New-field audit: ~20 `SimState.copy(...)` sites in `Step.kt`; all are
  step-fold mutations and none touch `weatherByAerodrome` (fixture-set,
  immutable through the run).

### 6.2 Test architecture

- **Integration test is the only load-bearing artefact** (test-review).
  Unit tests are scaffolding around it.
- **No structural-only tests** — every kept test exercises a behavioural
  property (test-review M5/M6 trims applied).
- **Monotone responsibility-history invariant (I2)** is the load-bearing
  anti-leakage assertion across the multi-aerodrome run.
- **Reversal coverage:** APP/TMA re-exit test in G1.5; pilot-initiated
  contact reversal in G1.5; controller-initiated handoff reversal
  inherited from existing tests.
- **Phase-boundary invariants** in the integration test give condition-
  space coverage along the trajectory.

### 6.3 Impact

- `transferResponsibility` extraction is a refactor of existing logic;
  controller-initiated path remains observable as before.
- `applyContactFrequency` totality change: existing callers *never*
  passed a target that didn't resolve, so the new "no target" branch
  is currently dead — until G1's LOWG→FIS instruction. Document and
  test both old (resolved-target) and new (unresolved-target) paths.
- New `SimState` field; smart-constructor change; `.copy()` audit
  resolved (~20 sites, all step-fold).
- Mission decomposition has no inverse — divert paths are out of scope
  (G1b risk).
- LOWG must be loaded first in the merge (impact A4 advisory) — fixture
  asserts merge ordering.
- **Mandatory impact-agent re-invocation before G1.3 and G1.5.**

### 6.4 Operational correctness — corrected citations (corpus-verified, round-3)

All citations below are verified against the actual extracts in
`research/txt/` (SERA, ICAO Doc 4444 16th ed., ICAO Doc 9432, CAP 413).
Annex 11 source text is **not** in the corpus, so Annex 11 citations
are flagged as locally-unverified.

- **VFR rules:** SERA (Commission Implementing Regulation (EU) No
  923/2012) **SERA.5005** "Visual flight rules" — VFR minima and
  conditions (verified at `sera-923-2012-extracted.txt:1682`). The
  obligation for VFR flights in Class B/C/D to comply with Section 8
  (ATC clearances) is at **SERA.5005(h)**. Combined with **SERA.8001(b)**
  / **SERA.8005(b)** for clearance-issuance and adherence.
  (SERA.5005(c) was previously cited but governs VFR-at-night, not
  VFR in controlled airspace.)
- **Airspace classification (Class C/D two-way comms):** **SERA.6001
  "Classification of airspaces"** (verified at
  `sera-923-2012-extracted.txt:1831`); Class C and D require
  continuous two-way radio and ATC clearance for VFR per (c)/(d).
  SERA.6001 cross-references **ICAO Annex 11, Appendix 4** as ICAO
  origin (locally unverified — not in extract corpus).
- **Inter-unit coordination across the FIR boundary:** **ICAO Doc 4444
  (PANS-ATM, 16th ed., Amdt 9, 2016) §10.1 "Coordination between ATS
  units"** — load-bearing paragraphs **§10.1.2.2 (Transfer of control)**
  and **§10.1.2.4 (Transfer of communication)** at
  `icao4444-extracted.txt:11740, 11790`. (§10.2 is "Coordination in
  respect of the provision of flight information service and alerting
  service," not transfer of control — earlier-round citation was
  wrong.) Cross-reference: **ICAO Annex 11 §3.6** (locally unverified).
  FIS is advisory, not coordinating, so the cross-FIR gap has no
  controller-to-controller transfer obligation.
- **Communication failure (out of scope for nominal G1):** **SERA.8035**
  "Communication failures."
- **Communication failure:** **SERA.8035 "Communications"** at
  `sera-923-2012-extracted.txt:2142` — covers continuous voice watch and
  references ICAO comms-failure procedures in (b). (Out of scope for
  nominal G1.)
- **Runway-into-wind selection:** ICAO Doc 4444, 16th ed., **§7.2
  "Selection of runway-in-use"** — §7.2.1 defines runway-in-use; **§7.2.2
  states the most-nearly-aligned-into-wind preference**. Verified at
  `icao4444-extracted.txt:8389`. (Earlier-round "§7.3.1.1" was wrong;
  §7.3 is "Initial call to aerodrome control tower," a different topic.)
- **ATIS pilot acknowledgement on initial contact:** Direct EU citation
  is **SERA.9010(a)(2)(i)** at `sera-923-2012-extracted.txt:2206`. ICAO
  origin lies in Annex 11 §4.3 "Operation of ATIS" (locally unverified
  — not in corpus). Doc 4444 references for ATIS appear at **§4.5.7.5.1(c)**
  (transition levels via ATIS, `icao4444-extracted.txt:3413`) and §11.4.3
  (flight information messages). (Earlier-round "§4.3.6.1(g)" does not
  exist; §4.3 in Doc 4444 is "Division of responsibility for control
  between ATC units," ending at §4.3.5.)
- **Pilot self-initiated frequency change at TMA boundary:** combination
  of SERA.5005(h) (VFR in controlled airspace requires Section 8
  compliance), SERA.6001 (airspace classification), and Annex 11
  Appendix 4 (ICAO origin) — all demand contact be established *before*
  boundary penetration; the absence of a transferring unit is consistent
  with a FIS-to-controlled transition.

**Local corpus verification status:** SERA 923/2012, ICAO Doc 4444 (16th
ed.), ICAO Doc 9432, and CAP 413 are all in `research/txt/` and the
above citations are corpus-verified to the line numbers shown. Annex 11
is **not** in the corpus; the three Annex 11 citations remain unverified
locally. **G1.0 deliverable:** all corpus-available citations are
verified to specific extract lines; Annex 11 entries flagged for next
external verification opportunity (parked, not blocking G1).

### 6.5 Phraseology

- `InitialContact` typed shape (`stationCalled, aircraftType?, position?,
  level?, atisCode?, intention?`) covers CAP 413 initial-contact content
  per **CAP 413 §2.19–§2.30** (callsigns and initial calls) and
  **§2.47–§2.48** (continuation of communications). G1 *populates* the
  optional fields rather than relying on defaults — phraseology B8
  satisfied at the protocol layer. (Earlier-round "§2.41–2.46" was
  wrong: those paragraphs cover military/SAR callsigns, not initial
  contact.)
- Doc 9432 reference: **Doc 9432 Chapter 2** as a whole, with
  **§2.8 "Communications"** carrying §2.8.1 (establishment and
  continuation) and §2.8.2 (transfer of communications). (Earlier-round
  "§2.7" is "Call signs," not general RT.)
- Frequency-leaving courtesy calls and "vacate next available" are not
  modelled (atc-phraseology nice-to-have); tracked as G1-DEF-2 / G1-DEF-3.

---

## 7. Self-assessment (principal-agent criteria)

1. **Totality** — A1 totality audit staged as a hard gate; A2 typed
   errors distinguish lookup from absence; `transferResponsibility`
   typed; smart-constructor `Either`. No `error()` introduced.
2. **Reversal completeness** — pilot-initiated contact has explicit
   re-exit reversal test (G1.5); controller-initiated handoff reversal
   inherited; mission decomposition has no inverse (out of scope, G1b
   risk).
3. **Interaction coverage** — integration test traces all four agents
   across all three transition modes (LOWG→FIS, FIS→APP, APP→TWR).
4. **Test coverage for known features** — every feature in §5 has a
   test in §4; no scaffold tests retained.
5. **New-field audit** — `weatherByAerodrome` is fixture-set, never
   mutated by step-fold; ~20 `.copy(SimState)` sites checked.
6. **Operational correctness** — citations §6.4 corpus-verified at
   G1.0 against `research/txt/` extracts (multiple round-1+2 citations
   were still wrong against actual section text and were corrected
   again). Annex 11 not in corpus; entries flagged for next external
   verification opportunity.
7. **Error handling honesty** — every new error path is typed
   (`Either<...>`); `applyContactFrequency` totality fix removes a
   latent `error()` risk.

---

## 8. Risks

- **R1 — `transferResponsibility` extraction must preserve existing
  handoff semantics.** Mitigation: refactor in G1.5 keeps existing
  tests as the regression net; new tests cover only the *new* paths
  (no-target, pilot-initiated).
- **R2 — Pilot-initiated contact races controller-initiated handoff.**
  If LJMB_APP issues `ContactFrequency` to TWR while pilot is still
  contacting APP for the first time, ordering matters. Mitigation: the
  pilot-initiated rule fires only when the aircraft is *outside* TMA
  Maribor (before-entry constraint); APP cannot issue a TWR handoff to
  an aircraft it doesn't yet observe.
- **R3 — `applyContactFrequency` "no target" branch is dead until G1
  ships.** Mitigation: G1.5 adds a unit test for the no-target path
  before any production use; the branch is observable from day one.
- **R4 — `selectRunwayIntoWind` returns `null` when wind is null.** A5
  smart constructor guarantees `WeatherObservation` is present per
  aerodrome but `wind` itself can be null. If a test passes wind=null
  the runway selection silently returns null; downstream nil chain
  proceeds. Mitigation: G1.1's `WindActiveRunwayTest` asserts the
  *behavioural* contract (south wind → 16C / 14); a separate "no
  wind" assertion is left to controller-layer hardening (out of G1).
- **R5 — LOWG must load first in the merge.** Mitigation: G1.6 fixture
  asserts merge ordering; first-wins on shared enroute fixes is
  documented (M6 in `.plan`).
- **R6 — RT phraseology gap on FIS frequency may surprise.** Pilot
  emits `InitialContact` to a FIS frequency that no simulated
  controller observes. The transmission is dropped by `handlePilotTick`
  (existing behaviour). Mitigation: P5 explicitly asserts inboxes are
  empty across the FIS segment; this is a documented v1 simplification
  (S1).
- **R7 — Step budget in `runUntil`.** Per phase: 10 minutes simulated
  time, conservative; on timeout the test reports which predicate
  failed.
- **R8 — Variants of `HighLevelGoal` exhaustiveness audit may surface
  `else ->` branches that compile silently.** Mitigation: A1 totality
  checklist is a hard gate; G1.3 cannot land without the grep result
  in the commit message.

---

## 9. Deferrals tracked from G1 design

To be promoted to `.plan` after G1 lands:

- **G1-DEF-1** — Wire existing `RequestFrequencyChange(frequency: Frequency?)`
  (already in `PilotTransmission.kt:214`) into a pilot cognitive rule
  for controlled-field zone exits. v1 has LOWG_TWR proactively issue
  `ContactFrequency`; the realistic flow is pilot requests first,
  controller approves. The protocol type already exists; this is a
  cognitive-rule + controller-response addition. (Phraseology round-2
  reframe: not "missing protocol type.")
- **G1-DEF-2** — `FrequencyLeaving` courtesy transmission for
  uncontrolled-airspace exits. Type does not exist in
  `PilotTransmission.kt`; phraseology nice-to-have.
- **G1-DEF-3** — Upgrade `AfterLandingVacateVia.whenAble: Boolean`
  (`Instruction.kt:597`) to a tri-state enum
  `{NOW, WHEN_ABLE, NEXT_AVAILABLE}` per CAP 413 §4.62. (Phraseology
  round-2 reframe: the boolean exists; the gap is its expressiveness.)
- **G1-DEF-4** — APP/TWR combined-position operation at LJMB. Real LJMB
  routinely combines positions; v1 split is an architectural
  simplification (S2).
- **G1-DEF-5** — Time-varying ATIS / runway change mid-run (S3).
- **G1-DEF-6** — `joinLeg = RIGHT_BASE` for RWY 14 from MN-corridor is
  a working assumption pending Jepp-19-1-derived authoring of the
  MN2→circuit-join endpoint. Manifest line 189 explicitly notes the
  endpoint is not yet authored. (atc-general B-OPS-7 fix.) Right base
  is geometrically consistent with right-hand circuits + NW arrival
  but is not yet derived from a published source.
- **G1-DEF-7 (PRE-G1.6 MUST-FIX)** — `WeatherObservation.wind: Wind?`
  typed-absence treatment. Currently `selectRunwayIntoWind` returns
  null when wind is null; downstream controller logic defers
  instruction issuance but the absence is implicit. **Promoted from
  open-ended deferral to pre-G1.6 must-fix per round-3 FP review:**
  G1.1 has now made the controller observably fail to pick a runway
  when `wind = null`, so the integration test will hang at P0 unless
  this is tightened. Convert to a typed
  `WeatherObservation.wind: Either<NoWindReport, Wind>` (or sealed
  `WindReport`) before G1.6 lands; the call sites are still fresh
  from G1.1, so the rewrite is small and isolated. Doc-comment on
  `controller/.../ControllerTypes.kt:WeatherObservation.wind` points
  here.
- **G1-DEF-8** — Typed `AerodromeFrequencies(tower, towerRadarVfr?,
  approach?, atis?, source: String)` parser at the migration
  boundary. The manifest currently carries free-form
  `frequencies: { tower, approach|towerRadarVfr, atis?, source, note? }`
  with schema drift between LOWG (has atis) and LJMB (no atis). Lift
  to a typed wrapper at parse time; lands when G1.6 first consumes
  the values. (impact round-3 finding.)
- **G1-DEF-9** — LOWG has no discrete APPROACH unit. The manifest now
  names the field `towerRadarVfr` (not `approach`) and the `note`
  field warns against spawning `RoleName.APPROACH`, but the
  underlying simulator does not enforce this — a future controller
  spec authored from `frequencies.approach` (which now no-keys at
  LOWG) would silently fail. Track an enum-driven role-frequency
  binding once cross-aerodrome flows mature beyond G1. (atc-general
  round-3 finding.)
- **G1-DEF-11 — DONE.** Multi-aerodrome geometric frame collision
  resolved. `mergeAviationWorlds` now reprojects each airport's local
  Cartesian frame into a single shared frame at parse time. Global
  origin = arithmetic mean of all reference points (deterministic, not
  order-dependent). Each airport's positions translated by the (Δx, Δy)
  ENU offset from global origin to that airport's reference. Point-in-
  polygon and distance queries in the merged world now use coherent
  coordinates. `Aerodrome.referencePoint: LatLon?` carries the
  airport's geographic origin; for now the loader looks up known ICAO
  codes (LOWG, LJMB) from a hardcoded table — the migration pipeline
  propagating lat/lon through `world-candidate.json` is a follow-up
  (`G1-DEF-17`). Single-airport `toWorld` unchanged. Pinned by the
  "merged world has coherent geometry" test in
  `sim/src/jvmTest/.../g1/PilotAirspaceTest.kt`.
- **G1-DEF-10 (post-G1)** — Test ergonomics: `requireSimState(...)`
  helper at `sim/src/jvmTest/.../SimStateTestSupport.kt` already
  exists; existing tests still use ad-hoc `.getOrElse { error("...") }`
  patterns inline. Migrate them to the helper as a passing-by-the-
  area cleanup. Not blocking. (FP / impact round-3 finding.)
- **G1-DEF-12** — LOWG VFR exit lanes are not modelled. The current
  `buildCrossAerodromeTransitRoute` uses circuit upwind/crosswind legs
  as a proxy departure, which routes the aircraft east (toward Graz
  city) before turning back south to PETOV — a clumsy dogleg. Real
  LOWG departures use published VFR exit lanes (KAINBACH, ST PETER,
  etc.) per the AIP/VAC charts. Authoring those exit lanes into the
  source-aerodrome `Aerodrome` data and dispatching to them in the
  transit route lands post-G1. (atc-general round-3 finding.)
- **G1-DEF-13 (PRE-G1.6 NICE-TO-HAVE) — DONE in v2.2** — corridor
  REPs (e.g. MN2 between MN1 and the right-base join for LJMB RWY 14)
  are now first-class on `HighLevelGoal.VfrCrossAerodromeTransit.
  ctrCorridorWaypoints`. The arrival-join route runs `tmaEntry →
  ctrEntry → corridor REPs → join leg`. Skipping a published REP
  was operationally wrong (atc-general round-3 finding, fixed).
- **G1-DEF-14** — `LegName.BASE` does not encode left/right
  handedness; right-base vs. left-base is a circuit-procedure
  attribute (`circuit.northDirection`). For LJMB RWY 14 the
  manifest's right-hand circuit means `joinLeg = BASE` resolves to
  right base by coincidence of authoring, not by guarantee. A
  `Circuit` mission at a left-hand airport would silently mean left
  base. Surface `circuit.northDirection` into the join logic post-
  G1; for now the test fixture must explicitly verify the airport's
  circuit direction matches the join semantics. (atc-general round-3
  finding.)
- **G1-DEF-15** — `RouteContext` sealed type to collapse `joinLeg:
  Option<LegName>` and `crossAerodrome: Option<CrossAerodromeContext>`
  in `buildVisualModeRoute`. Each caller currently does its own
  goal-discrimination dispatch in `UnifiedPilot`. Lift to a single
  typed context derived from the mission, eliminating two `Option`
  parameters and giving one place to add future contexts (divert,
  holding). (impact round-3 finding.)
- **G1-DEF-16** — `routeTransitions` typed phase index on
  `PilotMission` (originally A1.1, dropped from G1.3 as redundant).
  Round-3 impact review reinstated this: `derivePilotGoal` reverse-
  engineering phase from `TaskName` is the symptom; the cause is
  that primitives don't carry enough phase context, so phase is
  re-encoded as compound wrappers. This will recur for divert (an
  `EnRoute` primitive that suddenly needs an alternate compound),
  missed approach with re-route, and any future multi-phase
  mission. The structural fix is `PilotMission.routeTransitions:
  List<RouteTransition>` as the single source of truth for "what
  phase am I in," consumed by `derivePilotGoal`. The current
  compound-wrapping (G1.3) is a beachhead, not the long-term shape.
- **G1-DEF-17** — Migration pipeline propagation of airport
  `referencePoint` (lat/lon) through `world-candidate.json`. G1-DEF-11
  is currently closed via a hardcoded ICAO→LatLon table in
  `WorldCandidateLoader`; the long-term shape is for the structured-
  airport-package pipeline to publish each airport's reference point
  alongside its geometry, and the loader to read it from there.
  Blocks adding new airports without a Kotlin code change.

---

## 10. Cross-references

- `.plan` → G1 entry, depends on M7 (DONE).
- `sim/src/jvmTest/.../MultiAerodromeWorldTest.kt` — scaffold built on.
- `sim/src/jvmTest/.../FullCircuitTest.kt` — structural template.
- `cad/airports/lowg.manifest.json`, `cad/airports/ljmb.manifest.json` —
  authoritative for frequencies, runways, REPs, circuit direction.
- `data/charts/LJMB/LJMB.pdf` — Jepp 19-1 / 19-2 (cross-checked against
  the manifest, which is the authored source).

---

## 11. Sign-off path (v2)

1. This v2 is reviewed by **impact, atc-general, atc-law** at minimum
   (the three reviews that found blockers in v1). FP, test, phraseology
   re-review optional (changes are tightening rather than reshaping).
2. v2 review feedback is consolidated into a "review-2 responses"
   appendix.
3. After user green-light, G1.0 begins (manifest-frequency wiring +
   citation verification). Each phase ends with a check-in at the
   self-assessment criteria.
