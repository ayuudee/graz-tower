---
satisfies: [R1, R2, R3, R4, R9a]
---

## Description

Foundation pass for G3a-trained. Lands the typed-surface migration and the
pilot mission-tree compiler change that the golden test (fn-11.2) builds on.

This is the **early proof point** for fn-11. If the mission compiler fork can't
be expressed cleanly (e.g. short-final altitude termination requires a major
route-planner refactor exceeding ~10 sites), STOP and re-evaluate the fork-
point design before fn-11.2 starts.

**Size:** M
**Files** (per pass-8 plan-review finding #3 — added missing files
implementer needs):
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt`
  (`HighLevelGoal.CircuitTraining` constructor replacement; new
  `CircuitOutcome` sealed type; `planMission` arm rewrite;
  `plannedGoAroundCircuitTask()` builder; new `MissionStep.FLY_FINAL_TO_SHORT_FINAL`
  enum value; `isCircuitLike()` arm if any new `TaskName` leaf; KDoc
  updates on sealed-interface and `planMission`).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt`
  (Circuit-mode go-around route special-case in `planRoute` /
  `planCircuitDeparture`; route-clear emission for the
  FLY_FINAL_TO_SHORT_FINAL → GOING_AROUND step transition; mirror to
  `applySelfInitiatedGoAround` for trained-GA result shape).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt`
  (or wherever step-completion checking lives — find by grepping
  `CompletionMode.PHYSICAL` arms and step-transition logic).
  Step-completion arm extension for `FLY_FINAL_TO_SHORT_FINAL`
  altitude predicate; pilot transmission emission for
  `Report(Downwind, intent = CircuitIntent.FULL_STOP)` for trained-GA
  outcome (per pass-8 plan-review finding #1).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotRoutePlanner.kt`
  (no per-leg altitude support needed — Option α uses step-completion;
  but route-planner aliasing for `FLY_FINAL_TO_SHORT_FINAL` may live
  here if it's wired through).
- 25+ call-site migration (full list under "Migration call sites" below)

## Approach

### 1. `CircuitOutcome` sealed hierarchy

Add to `PilotMission.kt` (or a sibling file if the package convention favors
splitting). Three case `data object` leaves; payloads can be added later
without breaking call sites:

```kotlin
sealed interface CircuitOutcome {
    data object TouchAndGo : CircuitOutcome
    data object FullStop : CircuitOutcome
    data object GoAround : CircuitOutcome
}
```

### 2. `HighLevelGoal.CircuitTraining` constructor replacement

`PilotMission.kt:18` — replace:
```kotlin
data class CircuitTraining(val circuits: Int, val fullStopOnLast: Boolean = true) : HighLevelGoal {
    init { require(circuits > 0) { "CircuitTraining requires at least 1 circuit" } }
}
```
with:
```kotlin
data class CircuitTraining(val outcomes: List<CircuitOutcome>) : HighLevelGoal {
    init {
        require(outcomes.isNotEmpty()) { "CircuitTraining requires at least 1 outcome" }
        // Per pass-3 plan-review finding #4: terminal outcome must be FullStop.
        // Otherwise planMission appends groundArrivalTask() after a still-airborne
        // aircraft (post-TouchAndGo / post-GoAround), causing a silent wedge.
        require(outcomes.last() == CircuitOutcome.FullStop) {
            "CircuitTraining must terminate with FullStop (got ${outcomes.last()})"
        }
    }
}
```

No default-arg shim. No companion-factory facade. No secondary constructor.

### 3. `planMission` arm rewrite

`PilotMission.kt:514-522` — replace the index-driven `(1..goal.circuits).map`
loop with `goal.outcomes.map { ... }`:

- `TouchAndGo` → existing `touchAndGoCircuitTask()` (`PilotMission.kt:570`).
- `FullStop` → existing `circuitTask()` (`PilotMission.kt:427`).
- `GoAround` → NEW `plannedGoAroundCircuitTask()` (see step 4).

Sealed `when` over `CircuitOutcome` is exhaustive; no `else` branch.

### 4. `plannedGoAroundCircuitTask()` builder + new `MissionStep.FLY_FINAL_TO_SHORT_FINAL` primitive

**Per pass-1 plan-review finding #1**: the route model has one
`targetAltitudeM` per route, NOT per-leg. There's no existing primitive that
completes "final, but only down to short-final altitude". Confirmed via grep:
`MissionStep.FLY_FINAL` (line 434, line 577) uses `CompletionMode.PHYSICAL`
which completes when the route's terminal waypoint is reached (the runway
threshold).

A new `MissionStep.FLY_FINAL_TO_SHORT_FINAL` primitive is required.

#### Implementation paths (pick at task time)

- **Option α — completion-checker arm (RECOMMENDED).** Add the new step;
  route-planner aliases its routing to `FLY_FINAL` (no new arm in
  `buildAirborneRoute`); the step-completion checker gets a new arm.
  Per pass-9 plan-review finding #2 — completion is **altitude/phase
  gated only**, NOT inheriting threshold-completion (which would let the
  trained GA fire after touchdown if altitude logic miss-fires):
  - Step completes when:
    `aircraft.phase is PilotPhase.Final && aircraft.altitudeM <=
    DECISION_ALTITUDE_M && aircraft.phase !in setOf(PilotPhase.LandingRoll,
    PilotPhase.Vacating, /* + any other on-ground/clear phases */)`.
  - Does NOT fire on threshold-reached / route-terminal-waypoint-reached.
  - Smallest blast radius vs Option β.
- **Option β — route extension.** New step builds a route terminating at
  the short-final waypoint (geometrically computed on the FINAL leg).
  Standard PHYSICAL completion fires there. Requires a new
  `buildAirborneRoute` arm + short-final point identification logic. More
  invasive.

**Recommendation: Option α.** Implementer audits the step-completion checker
location (probably `pilot/.../PilotCognitive.kt` or sibling — find by
grepping for `CompletionMode.PHYSICAL` checks). Add the alt-threshold arm.

**Abort criterion:** if either option's blast radius > ~10 sites OR breaks
any existing test (G0/G1/G1-minimal/G2 by extension, since those don't
exercise the new step but compile-time changes ripple), STOP fn-11.1 and
split the route/step extension into a separate task before attempting the
trained-GA compiler arm.

#### Compound shape

```kotlin
fun plannedGoAroundCircuitTask(): CompoundTask = CompoundTask(
    TaskName.Circuit,  // per pass-8 plan-review finding #2 — use Circuit
    // (not CircuitAfterGoAround which implies post-GA state). The trained
    // pilot is flying a CIRCUIT that ends in a GA, structurally a Circuit
    // until the GoAround inner subtree fires. Other consumers
    // (toFlightStrip, isCircuitLike, route arms) see it as a regular
    // circuit. The Circuit-mode planRoute special-case (Required B above)
    // handles the GA route after invalidation, NOT the TaskName arm.
    listOf(
        // Standard pattern legs up to base + final entry:
        PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.FLY_DOWNWIND, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.REPORT_DOWNWIND, CompletionMode.REPORTED),
        PrimitiveTask(MissionStep.AWAIT_SEQUENCING, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.FLY_BASE, CompletionMode.PHYSICAL),
        PrimitiveTask(MissionStep.REPORT_BASE, CompletionMode.REPORTED),
        // NEW step — replaces standard FLY_FINAL, completes at short-final
        // altitude (~100m / DECISION_ALTITUDE_M), NOT at the runway threshold:
        PrimitiveTask(MissionStep.FLY_FINAL_TO_SHORT_FINAL, CompletionMode.PHYSICAL),
        // No REPORT_FINAL: by the time the aircraft reaches short-final
        // (~100m AGL), `HasReportedPositionCall` is already satisfied by the
        // earlier REPORT_DOWNWIND + REPORT_BASE transmissions (per fn-8.3's
        // sticky witness — `observedReportsDuringCommitment` accumulates).
        // The controller's `ARR-LAND` rule can therefore issue `ClearedToLand`
        // proactively during downwind/base, satisfying the GA-POST-CLEAR
        // regression-source pin in R6 / fn-11.2 Layer 2 without needing a
        // FINAL-leg report. Per pass-4 plan-review finding #3.
        // GA wrapped — same shape as reactive applySelfInitiatedGoAround:
        goAroundTask(),  // CompoundTask(TaskName.GoAround, [GOING_AROUND, REPORTED])
    )
)
```

(Sketch only — implementer audits exact step list against
`touchAndGoCircuitTask()` at `PilotMission.kt:570-585` to ensure the standard
pattern legs match.)

#### Route invalidation + reactive special-case alignment (per pass-2 plan-review findings #1+#2 + pass-3 plan-review findings #1+#3)

**`CompoundTask.activeCompound()` is one-level only** (verified at
`PilotMission.kt:603-612`). It does NOT descend into nested compounds. So
during the trained-GA `GOING_AROUND` step,
`mission.root.activeCompound()?.name == TaskName.Circuit` (the
outer wrapper, NOT `TaskName.GoAround`). The route planner's
`TaskName.GoAround` arm at `PilotRoutePlanner.kt:190, 248` will NOT fire on
this signal alone for the trained tree.

**The reactive flow works via a different mechanism**: the special-case at
`Pilot.kt:349-370` (`cur == null && phase is Final && step ==
FLY_DEPARTURE`) calls `buildGoAroundRoute` directly. The `cur == null`
precondition is the key — `applySelfInitiatedGoAround` clears the
kinematic route as a side effect (or it's consumed during the
GOING_AROUND → FLY_DEPARTURE transition).

**For trained GA, the route is NOT null at GOING_AROUND** — it's the old
final route built for `FLY_FINAL_TO_SHORT_FINAL`. Without explicit route
invalidation, the special-case won't fire, the aircraft will continue
toward the threshold on the old route, and the trained GA fails.

**Required (A) — route invalidation output contract**
(per pass-3 plan-review finding #3 + pass-5 finding #1 + pass-6 finding
#2 + pass-12 finding #1 — route MUST clear on Tick A, not later):
- The tick that advances the mission step from
  `MissionStep.FLY_FINAL_TO_SHORT_FINAL` to `MissionStep.GOING_AROUND`
  (Tick A) MUST emit:
  - `PilotOutput.intent.phase == PilotPhase.Final` (NOT Climbing — see
    Decision Context #2 phase-timing constraint).
  - `PilotOutput.intent.route == PilotRoute.None` (the codebase's
    no-route variant — investigate `PilotRoute` sealed type at task time
    to confirm the exact case name).
  - `PilotOutput.transmissions` includes `Report(GoingAround)`.
- The aircraft state model has no mission-owned route field — only
  `PilotOutput.intent.route` can drive the next aircraft state. Do NOT
  try to clear `AircraftState.route` directly or `PilotMission`-level
  state — those are not the right slice.
- **Do NOT defer the route-clear to "the following GOING_AROUND
  completion tick"** — that's too late. Tick A clears; Tick B's planner
  sees `cur == null + phase Final + step FLY_DEPARTURE` and fires the
  Circuit-mode special-case.

**Required (A.1) — `resetForGoAround` invocation on Tick A**
(per pass-13 plan-review finding #2):

The reactive flow's `applySelfInitiatedGoAround` calls
`mission.resetForGoAround(now)` (`Pilot.kt:557`) to clear go-around
phase-local mission state: `hasClearance`, `activeConstraints`,
`routeOverride`, `altitudeRestrictionM`, etc. Without this reset,
stale state leaks into the next circuit:
- Stale `hasClearance == true` would suppress the no-clearance reactive
  GA on circuit 2 (if applicable).
- Stale `altitudeRestrictionM` could cap the next circuit's pattern
  altitude.
- Stale `activeConstraints` could carry forward invalidated ATC
  instructions.

**Trained-GA Tick A must apply the same `resetForGoAround(now)`
semantics** (or equivalent named-field reset preserving the static
mission root). This is a sibling concern to the route-clear (Required
A) and route-planner special-case (Required B); together they restore
the mission state for the next circuit attempt.

**Acceptance pin (added)**: pilot-side unit test asserts that after
Tick A:
- `mission.hasClearance == false`
- `mission.activeConstraints` is empty (or whatever the cleared
  shape is — investigate `resetForGoAround`'s named-field block at
  `PilotMission.kt:212-239`)
- `mission.altitudeRestrictionM` is None (or default)
- The static mission root structure is unchanged (Tick A does NOT
  call `replaceChild` like `applySelfInitiatedGoAround` does — the
  trained tree was already compiled with the GA at the right place;
  no subtree replacement needed).

**Required (B) — Circuit-mode go-around route special-case**
(per pass-6 critical plan-review finding #1):

`HighLevelGoal.CircuitTraining` derives `NavigationMode.Circuit`, NOT
`NavigationMode.Visual` (per `PilotRoutePlanner.kt:765-770`). The reactive
flow's special-case at `Pilot.kt:349-370` lives in `planVisualRoute` —
the trained-GA Circuit-mode flow does NOT hit it. Instead, after route
invalidation, the planner bootstraps `buildCircuitModeRoute` for
`TaskName.Circuit` → normal circuit pattern (NOT the GA
path).

Add a Circuit-mode go-around special-case in `planRoute` (likely before
`planCircuitDeparture` at `Pilot.kt:228+`):

> When `mode is NavigationMode.Circuit && step == MissionStep.FLY_DEPARTURE
> && aircraft.phase is PilotPhase.Final && kinematicRoute !is
> PilotRoute.Airborne`, call `buildGoAroundRoute(mode.runway, world,
> aircraft.type, CircuitLookup.ById(mode.procedure))` and return
> `PilotIntent(phase = Climbing, route = <gaRoute>, targetAltitudeM =
> aircraft.type.circuitPattern.altitudeAglM, ...)`. Mirrors the Visual-mode
> special-case for compound rule consistency.

**Pilot-side compile-time test (replaces the stale `activeCompound()`
assertion)**:

Drive a trained-GA mission through to `GOING_AROUND` step. Assert the tick
after `GOING_AROUND` completes produces `PilotIntent(phase = Climbing,
route = <built from CircuitProcedure.goAroundPath>, ...)`. Mirror
`SelfInitiatedGoAroundResponseSpec`'s reactive-side row format. Lives in
`pilot/commonTest`. DO NOT use `activeCompound()` for this assertion —
that signal returns the outer wrapper, not the inner GoAround compound.

**Pin the OBSERVABLE outcome (route built from goAroundPath), not the
intermediate task-tree shape.**

DO NOT duplicate `goAroundTask()` / `buildGoAroundRoute` logic. Reuse.

### 5. `TaskName` + `isCircuitLike()` totality

If `plannedGoAroundCircuitTask()` introduces a new `TaskName` leaf (e.g.
`CircuitTrainingPlannedGoAround`), the `isCircuitLike()` predicate at
`PilotMission.kt:594-600` must add an arm. No fallthrough — per
`feedback_no_corners.md`, sealed `when` with a new branch must be handled or
`error()`. If it's possible to reuse an existing `TaskName` leaf
use `TaskName.Circuit` for the trained-GA outer compound (per pass-8
plan-review finding #2 + pass-13 finding #1 — DO NOT reuse
`CircuitAfterGoAround`, which would mis-signal post-GA state to
`toFlightStrip` / `isCircuitLike` / route arms before the GA has
actually fired).

### 6. `resetForGoAround` field-naming convention

`PilotMission.kt:212-239` — `resetForGoAround(now)` requires every mission
field be named in the function (reset or preserved-with-comment). If the
migration adds new mission state for tracking which `CircuitOutcome` is
current (e.g. an outcome-iterator for the static plan), add it to the
function's name-block-or-reset rule with a documented decision.

### 7. Migration call sites (25+ verified by repo-scout)

**sim/src/jvmTest/:**
- `LowgGoldenTest.kt:77` — `circuits = 1, fullStopOnLast = true` → `outcomes = listOf(CircuitOutcome.FullStop)`
- `G1TwoAircraftCircuitsTest.kt:177, 184` — `circuits = 2, fullStopOnLast = true` → `outcomes = listOf(TouchAndGo, FullStop)` × 2
- `G1TwoAircraftMinimalSpec.kt:92, 99` — `circuits = 1` → `outcomes = listOf(FullStop)` × 2 (verify default-arg behavior preserved)
- `G1B4ClosurePinSpec.kt:83, 88` — `circuits = 2` → `outcomes = listOf(TouchAndGo, FullStop)` × 2
- `G1ClosureDiveTest.kt:62, 68, 234, 239, 470, 475` — six call sites; `circuits = 2` → `outcomes = listOf(TouchAndGo, FullStop)`
- `ReadbackCorrectionRoundTripTest.kt:105` — `circuits = 1`
- `testing/SimTraceSmokeSpec.kt:177` — `circuits = 1` (only `circuits` arg, default-arg `fullStopOnLast = true` so → `outcomes = listOf(FullStop)`)

**pilot/src/commonTest/:**
- `IsPhysicallyCompleteFlyDepartureSpec.kt:172`
- `ProcessInstructionMissionStateSpec.kt:72`
- `ProcessControllerResponseSpec.kt:38`
- `ProcessInstructionRunwayDerivationSpec.kt:56`
- `AtisLetterPropagationSpec.kt:111`
- `AtisLetterForCallInboundSpec.kt:116, 154`
- `observe/SelfInitiatedGoAroundResponseSpec.kt:81`
- `observe/PilotEventDerivationSpec.kt:63`

Test-name strings containing `"circuits = 1"` or `"fullStopOnLast = true"`
should also be updated to reflect the new `outcomes` shape — readers diff
test names against test bodies.

Mapping rule (per pass-4 plan-review finding #1 + pass-5 finding #4 —
verified by grep, no existing call site uses `fullStopOnLast = false`):
- old `(circuits = N, fullStopOnLast = true)` ≡ new
  `List(N - 1) { CircuitOutcome.TouchAndGo } + listOf(CircuitOutcome.FullStop)`
- old `(circuits = N, fullStopOnLast = false)` — **NO MAPPING**. No
  existing call sites use this combination (verified in fn-11.1's grep
  pass). Such a session would terminate airborne, violating the new
  terminal-`FullStop` invariant. **If grep at task time finds any such
  call site, STOP and re-scope** — the migration must not silently fail
  the new invariant.
- old `(circuits = 1, fullStopOnLast = true)` ≡ new `listOf(FullStop)`

### 8. KDoc updates

- `PilotMission.kt:8-14` — sealed-interface KDoc full re-write to describe
  `CircuitOutcome` semantics + the per-circuit branching shape.
- `PilotMission.kt:486` — `planMission` KDoc updated for the new branching
  arm.
- `PilotMission.kt:18` (the `CircuitTraining` data class) — KDoc on the new
  shape; note that the old `(circuits, fullStopOnLast)` form is removed and
  the migration mapping above.

## Investigation targets

**Required** (read before coding):
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:8-22`
  (sealed `HighLevelGoal` + `CircuitTraining`).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:427-438`
  (`circuitTask()` builder).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:474-485`
  (`goAroundTask()` + `ifrGoAroundTask()`).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:486-567`
  (`planMission` and the `CircuitTraining` arm specifically).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:570-585`
  (`touchAndGoCircuitTask()` builder).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:594-600`
  (`isCircuitLike()` totality predicate).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:212-239`
  (`resetForGoAround` field-naming convention).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:509-563`
  (`GoAroundResult` + `applySelfInitiatedGoAround` — the reactive subtree-
  replacement pattern; reuse the response stage; do NOT call this from the
  trained-GA path per practice-scout anti-pattern #6).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotRoutePlanner.kt:455-484`
  (`buildGoAroundRoute` — consumes `CircuitProcedure.goAroundPath`; reused).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotRoutePlanner.kt`
  (general — find `buildAirborneRoute` and check for per-leg altitude
  termination support; if absent, extension scope).
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/ProcedureAndAirspaceModel.kt:216`
  (`CircuitProcedure.goAroundPath` field — the published per-circuit GA path).
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt:937-957`
  (`ReportEvent.GoingAround` — already exists; nothing to add).

**Optional** (reference as needed):
- `pilot/src/commonTest/kotlin/.../observe/SelfInitiatedGoAroundResponseSpec.kt`
  (existing pilot-side reactive-GA spec — tests pin the response stage; useful
  contract reference).
- `pilot/src/commonMain/kotlin/.../observe/PilotEvent.kt:30-89`
  (recognition-vs-response separation; G3a-trained does NOT add a new event
  leaf — the trigger is the static mission tree).

## Key context

- **The trained-GA path is fully encoded in the mission tree at `createMission`
  time.** The pilot follows the tree. No runtime subtree replacement, no new
  recognition path. This is the architectural difference from
  `applySelfInitiatedGoAround` (reactive, runtime-driven).
- **Wake-rule integration**: a single-aircraft scenario doesn't exercise
  separation assessments. fn-8.3's `WakeRule` ADT exists but is not load-
  bearing for fn-11.
- **G0 / G1 / G1-minimal / G2 byte-stability is the load-bearing regression
  risk** for the migration. The mapping is lossless; the existing test
  scenarios should produce byte-identical mission trees with the new shape.
  Verify by green test run.
- **Kotlin sealed `when` exhaustiveness** is the safety net for the
  `CircuitOutcome` arms across all consumers. Compiler errors will surface
  any missed call site.
- **`Pilot.kt:541-551`'s `applySelfInitiatedGoAround` shape** is the reactive
  reference. Trained GA's compile-time fork is structurally similar but driven
  by the `outcomes` list, not by a `PilotEvent`. Per practice-scout
  anti-pattern #6: separate code paths.

## Acceptance

- [ ] `CircuitOutcome` sealed hierarchy lands with three `data object` cases:
      `TouchAndGo`, `FullStop`, `GoAround`. Lives next to `HighLevelGoal` in
      `PilotMission.kt` (or a sibling file per package convention).
- [ ] `HighLevelGoal.CircuitTraining(circuits: Int, fullStopOnLast: Boolean)`
      is REPLACED by `HighLevelGoal.CircuitTraining(outcomes: List<CircuitOutcome>)`.
      `init` requires `outcomes.isNotEmpty()` AND `outcomes.last() ==
      CircuitOutcome.FullStop` (per pass-3 plan-review finding #4 — terminal
      outcome must be FullStop to avoid silent wedge in `groundArrivalTask`).
- [ ] `planMission`'s `CircuitTraining` arm walks `outcomes.map { outcome ->
      when (outcome) { ... } }` exhaustively. Three branches: `TouchAndGo` →
      existing `touchAndGoCircuitTask()`, `FullStop` → existing `circuitTask()`,
      `GoAround` → new `plannedGoAroundCircuitTask()`.
- [ ] New `MissionStep.FLY_FINAL_TO_SHORT_FINAL` enum value lands.
      **New-MissionStep audit captured in evidence** (per pass-2 plan-review
      finding #5 — `MissionStep` is an enum, not sealed; many consumers use
      sets, not exhaustive `when`). Audit covers at minimum:
      - `Pilot.kt` `airborneSteps` gate (lines 237, 259 — set-membership).
      - `skipCompletedSteps` (in `PilotMission.kt`).
      - **`PilotCognitive.handleLandingClearance` (per pass-10 plan-review
        finding #2)** — currently marks `FLY_FINAL` and related steps
        complete on receiving `ClearedToLand`. **MUST NOT alias
        `FLY_FINAL_TO_SHORT_FINAL` into that completion list** — if
        aliased, the pilot would skip the short-final altitude trigger
        and proceed to `GOING_AROUND` immediately on clearance receipt.
        Decision recorded in evidence: do not complete
        `FLY_FINAL_TO_SHORT_FINAL` from landing clearance.
      - Route-planner step gating (any `when (step) { ... }` over MissionStep
        in `Pilot.kt` planVisualRoute / sibling planners — including the
        special-case at `Pilot.kt:349-370`).
      - Step-completion checker (the `CompletionMode.PHYSICAL` arm — Option
        α extends this with the altitude predicate; find by grepping
        `CompletionMode.PHYSICAL` in pilot/.../).
      - Transmission/completion maps (any `Map<MissionStep, ...>` lookups).
      - Set-membership checks across the codebase (grep for
        `MissionStep\.` and `setOf(MissionStep` patterns).
      Each site listed in evidence with a one-line decision (extended /
      no-op / N/A).
- [ ] **Route invalidation contract (A)** lands per pass-5+pass-6 plan-
      review findings: the tick advancing past `FLY_FINAL_TO_SHORT_FINAL`
      to `GOING_AROUND` emits `PilotOutput.intent.route = PilotRoute.None`
      (codebase's no-route variant). NO clearing of `AircraftState.route`
      directly or `PilotMission`-level state. Implementer documents the
      exact emission site in evidence.
- [ ] **`resetForGoAround` invocation on Tick A (A.1)** lands per pass-13
      plan-review finding #2: trained-GA Tick A applies
      `mission.resetForGoAround(now)` (or equivalent named-field reset)
      to clear `hasClearance`, `activeConstraints`, `routeOverride`,
      `altitudeRestrictionM`, etc. Static mission root preserved (no
      subtree replacement). Pilot-side unit test asserts post-Tick-A
      `hasClearance == false` and other phase-local state is cleared.
- [ ] **Circuit-mode go-around route special-case (B)** lands per pass-6
      critical plan-review finding: `planRoute` (or `planCircuitDeparture`)
      handles `mode is NavigationMode.Circuit && step ==
      MissionStep.FLY_DEPARTURE && aircraft.phase is PilotPhase.Final &&
      kinematicRoute !is PilotRoute.Airborne` by calling
      `buildGoAroundRoute(mode.runway, world, aircraft.type,
      CircuitLookup.ById(mode.procedure))` and returning
      `PilotIntent(phase = Climbing, route = <gaRoute>, ...)`. Mirrors
      Visual-mode's special-case at `Pilot.kt:349-370`.
- [ ] `plannedGoAroundCircuitTask()` builder lands. Reuses `goAroundTask()` +
      `CircuitProcedure.goAroundPath`. Uses NEW
      `MissionStep.FLY_FINAL_TO_SHORT_FINAL` primitive (not the existing
      `FLY_FINAL`). STOP if route/step extension blast radius exceeds ~10
      sites.
- [ ] **Pilot-side unit/integration test — TWO-TICK sequence** (per
      pass-2 plan-review finding #1 + pass-6 critical finding #1 +
      pass-7 finding #3 + pass-10 critical finding #1 + pass-11 finding
      #1 — phase timing pinned per-tick):
      - **Test fixture source** (per pass-11 plan-review finding #2):
        `pilot/commonTest` cannot import `sim/jvmTest`'s `Fixtures.LOWG`
        (module boundary). Build a minimal `AviationWorld` + `WorldIndex`
        inline in the test using existing pilot-domain types (runway,
        `CircuitProcedure` with non-empty `goAroundPath`). If a sibling
        pilot-test already builds an inline-world fixture (e.g.
        `SelfInitiatedGoAroundResponseSpec` if it constructs its own
        world), reuse that pattern. If the test genuinely requires
        full-world fixtures, place this test in a sim/jvmTest source
        set that has access to `Fixtures.LOWG` instead — but flag the
        module-boundary decision in evidence.
      - **Tick A** (FLY_FINAL_TO_SHORT_FINAL completes by altitude;
        step advances to GOING_AROUND): `pilotDecide` returns
        `PilotIntent(phase == PilotPhase.Final, route ==
        PilotRoute.None, ...)` AND `transmissions` includes
        `Report(GoingAround)`. **CRITICAL: phase MUST be Final, NOT
        Climbing** — Climbing on tick A breaks tick B's special-case
        predicate.
      - **Tick B** (next tick; step is FLY_DEPARTURE; previous
        intent's Final phase is now `aircraft.phase`): `pilotDecide`
        returns `PilotIntent(phase == PilotPhase.Climbing, route ==
        <PilotRoute.Airborne built from CircuitProcedure.goAroundPath>,
        targetAltitudeM == aircraft.type.circuitPattern.altitudeAglM,
        ...)`. Confirms the Circuit-mode special-case fired and built
        the GA route (NOT a normal circuit pattern).
      - Mirrors `SelfInitiatedGoAroundResponseSpec`'s reactive-side row
        format.
- [ ] **Circuit-mode discriminator regression test** (per pass-7 plan-
      review finding #1): pilot-side unit/integration test that drives
      an ORDINARY single-circuit `FullStop` mission with
      `NavigationMode.Circuit`. Asserts the FLY_DEPARTURE on circuit 1
      (from the ground, before any approach) produces a NORMAL climb-out
      route, NOT a GA route. Confirms the Circuit-mode GA special-case's
      predicate (`Final + no airborne route + FLY_DEPARTURE`) doesn't
      false-positive on ordinary takeoff.
- [ ] **`ClearedToLand` does NOT advance `FLY_FINAL_TO_SHORT_FINAL` step**
      (per pass-10 plan-review finding #2): pilot-side unit test that
      receives a `ClearedToLand` instruction during the FLY_FINAL_TO_SHORT_FINAL
      step (well before short-final altitude). Asserts the mission's
      current step REMAINS `FLY_FINAL_TO_SHORT_FINAL` (NOT advanced to
      `GOING_AROUND`); `hasClearance` is set on the mission state; the
      altitude/phase gate continues to control the step's completion.
- [ ] **Trained-GA `Report(Downwind)` carries `CircuitIntent.FULL_STOP`**
      (per pass-9 plan-review finding #4): pilot-side unit test that
      compiles a trained-GA mission and drives it through to the
      `REPORT_DOWNWIND` step. Asserts the pilot transmission carries
      `Report(Downwind, intent = CircuitIntent.FULL_STOP)` (NOT
      `TOUCH_AND_GO`, NOT `null`). Even though `deriveCircuitIntent()`
      currently infers FULL_STOP from `TaskName.Circuit` (which the
      trained-GA outer compound uses), this test pins the radio-boundary
      behavior so a future TaskName refactor doesn't silently change
      it.
- [ ] `isCircuitLike()` totality preserved (new arm if a new `TaskName` leaf
      is added).
- [ ] `resetForGoAround` named-field convention preserved (new mission state
      added to the function if any).
- [ ] All 25+ call sites of `HighLevelGoal.CircuitTraining(...)` migrated to
      the new shape. Lossless mapping per spec.
- [ ] No default-arg shim. No secondary constructor. No companion-factory facade.
- [ ] G0 (`LowgGoldenTest`), G1 (`G1TwoAircraftCircuitsTest`), G1 minimal
      (`G1TwoAircraftMinimalSpec`), G2 (`G2CrossAerodromeVfrTest`) all stay
      green post-migration.
- [ ] **Verification command (per epic R9 + pass-2 plan-review finding #4):**
      `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest
      :protocol:jvmTest detekt` exits 0. fn-11.1 is pilot/sim closure;
      `:migration:jvmTest` is NOT required. If migration is run anyway, the
      ONLY acceptable failing test is the exact pre-existing flake
      `xyz.easiersaid.twr.migration.world.LjmbWorldCandidateValidationTest >
      writesLjmbCurrentCoreValidationReport()` (any other migration failure
      blocks fn-11.1).
- [ ] `./gradlew detekt` baseline unchanged.
- [ ] KDocs updated: sealed-interface `HighLevelGoal`, `CircuitTraining` data
      class, `planMission`. Old `(circuits, fullStopOnLast)` shape no longer
      referenced in any KDoc.

## Done summary

## Evidence
