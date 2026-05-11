# G3a — single-aircraft pilot-trained VFR go-around as circuit-training outcome

## Overview

Land a `G3aPilotTrainedGoAroundTest` golden test — single AI aircraft at LOWG flies
a circuit-training mission where one circuit is **explicitly authored** as a
go-around (the pilot's training instructor's plan dictates it). Pilot reaches
short-final, transitions to GoAround per the planned outcome, climbs runway-
heading, re-enters downwind, completes a successful landing on the next planned
circuit.

Reframes `HighLevelGoal.CircuitTraining(circuits: Int, fullStopOnLast: Boolean)`
to `HighLevelGoal.CircuitTraining(outcomes: List<CircuitOutcome>)` — sealed
hierarchy `TouchAndGo / FullStop / GoAround`. Migrates 25+ call sites across
G0/G1/G1-minimal/G2 and pilot/sim test suites in the closing pass per
`feedback_pass_scope.md`.

This is the next golden up from G1 (multi-aircraft circuits) along the
**training-outcome complexity** axis — exercising a non-landing circuit
outcome that closes the gap left by `wiki/design-decisions/2026-04-22-root-
cause-go-around-and-totality.md`'s open ask: "Any mission type that supports
go-around must have a go-around integration test before merge."

## Quick commands

```bash
# R9a/R9b verification (matches task acceptance) — exits 0 when fn-11
# regression set + new G3a are green:
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest :protocol:jvmTest detekt --console=plain

# Smoke subset (NOT sufficient for acceptance; for fast iteration only):
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :sim:jvmTest \
    --tests "xyz.easiersaid.twr.sim.G3aPilotTrainedGoAroundTest" \
    --tests "xyz.easiersaid.twr.sim.LowgGoldenTest" \
    --tests "xyz.easiersaid.twr.sim.G1TwoAircraftCircuitsTest" \
    --tests "xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest" \
    --console=plain
```

## Boundaries / non-goals

- **Out: G3a-react (pilot-reactive go-around triggered by world wind state).** Adds
  `AircraftType.maxCrosswindKnots` POH-derived field + pilot wind-limit reasoning.
  Separate scenario; reuses the mission-tree fork built here. Filed as
  `D-PASS-g3a-react-crosswind-trigger`.
- **Out: G3a-obstruction (ATC-mandated go-around triggered by world-modeled
  runway obstruction).** Adds typed `RunwayObstruction` surface + tower-visual
  sensing channel + reactive ATC rule. First scenario in the codebase to wire
  tower-visual sensing as a real channel; foundational for future
  collision-avoidance / surface-incursion / FOD scenarios. Separate scenario.
  Filed as `D-PASS-g3a-obstruction-runway-state`.
- **Out: explicit instructor agent surface.** `Instructor` agent that authors
  `CircuitOutcome` per circuit, possibly with mid-flight amendments ("instructor
  sees student fumble, calls 'go around now'"). Same firewall doctrine:
  instructor is upstream-of-pilot world authorship. Filed as
  `D-PASS-instructor-agent-surface`.
- **Out: IFR missed-approach machinery.** `D-AUDIT.M2 — Missed-approach hold-loop
  compiler hardcoded to LOWG_GBG_MISSED_HOLD` is IFR-specific. G3a is VFR;
  explicitly does NOT engage `ifrGoAroundTask()` or the published-MA-procedure path.
- **Out: balked-landing scenarios.** Pilot briefly touches down, then aborts.
  Different doctrine path; out of fn-11 scope.
- **Out: ATC-issued GoAround/BreakOff** (`Intervention.GoAround → BreakOff/GoAround`
  in `SeparationEngine.kt:258-281`). The controller-side reactive intervention
  exists; it is not the trigger for this scenario. Per CAP 413 §4.67, pilot-
  initiated and ATC-initiated GA share phraseology body but differ in trigger.
  ATC-initiated is exercised by `SelfInitiatedGoAroundResponseSpec`'s
  pre-existing rows.

## Decision context

### 1. Type-shape: REPLACE, not layer (high confidence)

`HighLevelGoal.CircuitTraining(circuits: Int, fullStopOnLast: Boolean)` →
`HighLevelGoal.CircuitTraining(outcomes: List<CircuitOutcome>)`. `CircuitOutcome`
is sealed: `data object TouchAndGo / FullStop / GoAround`. List length determines
circuit count.

**Mapping rule** (per pass-4 plan-review finding #1 — verified by grep,
no existing call site uses `fullStopOnLast = false`):
- old `(circuits=N, fullStopOnLast=true)` ≡ new
  `List(N - 1) { TouchAndGo } + listOf(FullStop)` (i.e. N-1 touch-and-go
  followed by a final full-stop). Satisfies the new terminal-`FullStop`
  invariant by construction.
- old `(circuits=N, fullStopOnLast=false)` — **NO MAPPING**. No existing
  call sites use this combination (verified via grep
  `fullStopOnLast\s*=\s*false`). Such a session would terminate airborne,
  which violates the new terminal-`FullStop` invariant. If a future scenario
  needs an airborne-terminal mission, it warrants a different mission shape
  (not `CircuitTraining`).

`HighLevelGoal` is pilot-internal — never crosses the firewall (per
`PilotMission.kt:11-14`). No protocol/serialization consumers. Direct replace +
per-fixture migration is the lossless path. No default-arg shim, no
companion-factory facade — those soft-edge workarounds violate
`feedback_no_corners.md`.

**Init invariants** (per pass-3 plan-review finding #4 — terminal-outcome
contract):
- `require(outcomes.isNotEmpty()) { ... }` (replaces the existing
  `require(circuits > 0)`).
- `require(outcomes.last() == CircuitOutcome.FullStop) { ... }` — the
  mission must terminate with a full-stop landing. Otherwise `planMission`'s
  `groundArrivalTask()` (which expects "runway-vacated → taxi-in" behavior
  starting from on-ground) would be appended after a still-airborne aircraft
  (post-TouchAndGo or post-GoAround), creating a silent wedge state.

The terminal-FullStop invariant is doctrinally faithful to circuit training
(every training session ends parked). If a future scenario needs non-
full-stop terminal outcomes (e.g. "fly N circuits then divert"), that
warrants a new mission shape, not a relaxation of the invariant.

Migration scope: 25 call sites grep'd across `pilot/commonTest`, `sim/jvmTest`,
`pilot/commonMain` (`PilotMission.kt:18, 514–522`, `Pilot.kt:541`,
`PilotCognitive.kt:850`). All use `circuits = N, fullStopOnLast = true|false`.

### 2. Fork point: short-final altitude, statically encoded (judgment-call)

Per docs-scout findings: doctrinally there is no single "committed to land"
altitude. CAP 413 / ICAO 4444 give no specific number. FAA AFH cites stabilised-
approach gates: 500 ft AAE VMC (must be stabilised), 300 ft AGL "immediate
go-around" reactive gate. The existing `DECISION_ALTITUDE_M = 100.0` in
`pilot/observe/PilotEvent.kt:48` (~330 ft AGL) is the project's reactive trigger.

For trained GA, the fork is **statically encoded at compile time** in the
mission tree, not driven by an altitude trigger:

- `circuitTask()` (`PilotMission.kt:427`) for `FullStop` — flies all the way to
  LAND.
- `touchAndGoCircuitTask()` (`PilotMission.kt:570`) for `TouchAndGo` — LAND then
  FLY_DEPARTURE.
- **NEW** `plannedGoAroundCircuitTask()` for `GoAround` — flies the standard
  pattern through downwind / base BUT a NEW primitive step
  `MissionStep.FLY_FINAL_TO_SHORT_FINAL` (with `CompletionMode.PHYSICAL`)
  REPLACES the standard `MissionStep.FLY_FINAL`; then the existing
  `goAroundTask()` subtree runs (`CompoundTask(TaskName.GoAround,
  [PrimitiveTask(GOING_AROUND, REPORTED)])`); then the *next* outcome's
  compound takes over via the standard list-walk (mirroring the reactive
  `Pilot.kt:541-551` shape, but using `TaskName.Circuit` for the trained-
  GA outer compound — per pass-8 plan-review finding #2,
  `TaskName.CircuitAfterGoAround` implies post-GA state and would mis-
  signal to other `TaskName` consumers (`toFlightStrip`, `isCircuitLike`,
  route arms). The trained-GA outer is structurally a Circuit until the
  nested goAroundTask inner subtree fires).

**Concrete extension scope (per pass-1 plan-review finding #1 — the route
model has one `targetAltitudeM` per route, not per-leg, so this is real new
surface, not "audit at task time").**

The trained-GA fork requires a NEW `MissionStep.FLY_FINAL_TO_SHORT_FINAL`
primitive. Two viable implementation paths — pick at task time:

- **Option α — completion-checker arm** (recommended): the new step uses the
  SAME route as `FLY_FINAL` (route planner aliases it; no new arm in
  `buildAirborneRoute`). Its completion check ALSO advances when
  `aircraft.altitudeM <= DECISION_ALTITUDE_M` (~100m / ~330 ft AGL, matching
  the project's reactive trigger anchor at `pilot/observe/PilotEvent.kt:48`).
  Purely a step-completion extension. Smallest blast radius — likely one new
  `MissionStep` enum value + one arm in the step-completion checker.
- **Option β — route extension**: `FLY_FINAL_TO_SHORT_FINAL` builds a route
  that terminates at the short-final waypoint (not the threshold), and
  standard PHYSICAL completion fires there. Requires new route-planner arm +
  geometric short-final point identification. More invasive.

**Recommendation: Option α.** Reuses existing FINAL routing; reuses the
existing `DECISION_ALTITUDE_M` doctrinal anchor. Matches the project's
established "below this altitude on final = decide" pattern from the reactive
trigger.

**Abort criterion (per `feedback_pass_scope.md` discipline):** if either
option's blast radius exceeds ~10 sites or breaks any existing test, STOP
fn-11.1 and split the route/step extension into a separate task before
attempting the trained-GA compiler arm.

**Route invalidation + reactive-special-case alignment** (per pass-2
plan-review findings #1 + #2 — both load-bearing corrections):

- **`CompoundTask.activeCompound()` is one-level only** (verified at
  `PilotMission.kt:603-612` — *"the active (leftmost incomplete) compound
  task at the top level"*). It does NOT descend into nested compounds. So
  during the trained-GA `GOING_AROUND` step, `mission.root.activeCompound()?.name`
  is `TaskName.Circuit` (the outer wrapper, per pass-8 finding #2), NOT
  `TaskName.GoAround` (the inner). My pass-1 sketch was wrong; route-
  planner's `TaskName.GoAround` arm at `PilotRoutePlanner.kt:190, 248` will
  NOT fire for the trained tree based on `activeCompound()` alone.
- **The reactive flow works via the special-case at `Pilot.kt:349-370`:**
  `cur == null && phase is Final && step == FLY_DEPARTURE` → call
  `buildGoAroundRoute` directly. The `cur == null` precondition is the key:
  in the reactive flow, the kinematic route becomes null somewhere in the
  GOING_AROUND → FLY_DEPARTURE transition (likely consumed by the kinematic
  engine on `Climbing` phase, OR cleared by `applySelfInitiatedGoAround`
  side-effects). The special-case then triggers GA-route building.
- **For TRAINED GA, the route is NOT null at GOING_AROUND — it's still the
  old final route built for `FLY_FINAL_TO_SHORT_FINAL`.** Without explicit
  route invalidation, the special-case won't fire, the aircraft will
  continue toward the threshold on the old route, and the trained GA fails.

**Required: TWO route-handling extensions (per pass-5 finding #1 + pass-6
critical finding #1)** — fn-11.1's route work is bigger than initially
sketched. The pilot state model has no mission-owned route field; only
`PilotOutput.intent.route` can affect the next state.

**(A) Route invalidation output contract (per pass-5 plan-review finding
#1 + pass-7 finding #2 — explicit hook):**

> **When the effective mission step advances from
> `MissionStep.FLY_FINAL_TO_SHORT_FINAL` to `MissionStep.GOING_AROUND`,
> the `PilotOutput.intent.route` for that tick MUST be
> `PilotRoute.None` (the codebase's no-route variant — investigate
> `PilotRoute` sealed type at task time to find the exact case name).**

**Hook location + API shape** (per pass-7 finding #2 + pass-9 finding #1
+ pass-10 critical finding #1 — phase timing matters):

The mechanical mirror is `applySelfInitiatedGoAround` (`Pilot.kt:541-563`)
which returns a `GoAroundResult { intent: PilotIntent, mission:
PilotMission, transmissions: List<Transmission> }`. **Recommended shape
for trained-GA: define a sibling `PlannedGoAroundResult` (or extend
`GoAroundResult`) with the same fields, returned from a new
`applyPlannedGoAround(mission, aircraft, now)` helper invoked when the
mission step transitions from `FLY_FINAL_TO_SHORT_FINAL` to
`GOING_AROUND`.**

**Critical phase-timing constraint (per pass-10 critical finding #1):**

The route-clear intent on the trained-GA transition tick MUST keep
`phase = PilotPhase.Final` (NOT `Climbing`). Reason: the Circuit-mode
special-case (Required B above) checks `aircraft.phase is PilotPhase.Final
&& kinematicRoute !is PilotRoute.Airborne`. If the trained-GA tick sets
phase=Climbing, the next tick's `aircraft.phase` will be Climbing, the
Circuit-mode special-case won't fire, and the planner will build a
NORMAL circuit route (not GA path).

The reactive flow at `Pilot.kt:541-551` produces `intent.phase = Climbing`,
but the Visual special-case at `Pilot.kt:349-370` reads
`aircraft.phase` (the observed state from the previous tick's applied
intent) — for reactive, that's `Final` (the aircraft was on final at
the moment the GA fired, intent gets applied next tick). The same
timing window is what trained-GA exploits:

- **Trained-GA tick A** (FLY_FINAL_TO_SHORT_FINAL completes): emit
  `PilotIntent(phase = PilotPhase.Final, route = PilotRoute.None, ...)`.
  Pilot transmits `Report(GoingAround)`.
- **Trained-GA tick B** (next tick, GOING_AROUND completes, step advances
  to FLY_DEPARTURE): `aircraft.phase` is still Final (tick A's intent
  carried Final). `cur` is null (tick A cleared route). Circuit-mode
  special-case fires. `buildGoAroundRoute` builds the GA path. Intent
  for tick B sets `phase = Climbing`.

**Acceptance pin** (added per pass-10 critical finding): the fn-11.1
pilot-side test must drive the **two-tick sequence**:
- Tick A: assert intent has `phase == Final`, `route == PilotRoute.None`,
  `transmissions` includes `Report(GoingAround)`.
- Tick B: assert intent has `phase == Climbing`, `route == <built from
  CircuitProcedure.goAroundPath>`.

Alternative API shape: add an optional `intentOverride: PilotIntent?` to
`CognitiveDecision`. Same phase-timing constraint applies — the override
on tick A keeps phase=Final.

Implementer picks one of these two shapes at task time based on which
fits the existing pilotDecide flow more cleanly. Document the chosen
shape in fn-11.1's evidence with an explicit file/function reference.

This makes `cur == null` at the next planning cycle.

**(B) Circuit-mode planRoute special-case (per pass-6 critical finding
#1 + pass-7 finding #1 — discriminator):**

The reactive flow's `Pilot.kt:349-370` special-case lives in
`planVisualRoute`. **`HighLevelGoal.CircuitTraining` derives
`NavigationMode.Circuit`, NOT `NavigationMode.Visual`** (per
`PilotRoutePlanner.kt:765-770`). So the trained-GA flow goes through
Circuit-mode routing, where existing `buildCircuitModeRoute` for
`TaskName.Circuit` builds a NORMAL circuit pattern. Without
a Circuit-mode special-case, route invalidation alone causes the next
`FLY_DEPARTURE` planning cycle to bootstrap a normal circuit route.

> **Add a Circuit-mode go-around special-case in `planRoute` (or
> `planCircuitDeparture`):** when `mode is NavigationMode.Circuit && step
> == MissionStep.FLY_DEPARTURE && aircraft.phase is PilotPhase.Final &&
> kinematicRoute !is PilotRoute.Airborne`, call
> `buildGoAroundRoute(mode.runway, world, aircraft.type,
> CircuitLookup.ById(mode.procedure))` and return `PilotIntent(phase =
> Climbing, route = <gaRoute>, ...)`. Mirrors the Visual-mode special-case
> at `Pilot.kt:349-370`.

**Discriminator concern (per pass-7 plan-review finding #1):** the
predicate `Final + no airborne route + FLY_DEPARTURE` could in theory
match unusual states beyond trained-GA (e.g. odd kinematic-route
exhaustion mid-circuit). The Visual-mode predecessor at `Pilot.kt:349-370`
has the same theoretical ambiguity but is safe in practice because the
only path that matches is post-GA route-invalidation. For Circuit-mode
the same safety holds: ordinary circuit-training FLY_DEPARTURE happens
on-ground (phase != Final). To make the safety explicit:

- **Required regression test in fn-11.1**: pilot-side test that an
  ordinary single-circuit-training `FullStop` mission's FLY_DEPARTURE
  step does NOT hit the Circuit-mode GA special-case. The test asserts
  the FLY_DEPARTURE on circuit 1 (the very first one, from the ground)
  produces a normal climb-out route, NOT a GA route.

If the implementer wants a stronger discriminator (e.g. inspect the
active compound's child predecessor — if the just-completed sibling is
`CompoundTask(TaskName.GoAround, ...)`, then post-GA), that's an
acceptable enhancement. But the Final + no-route + FLY_DEPARTURE
conjunction is the canonical signal mirroring Visual-mode.

**Acceptance pin:** the pilot-side compile-time test below MUST drive a
Circuit-mode trained-GA mission (NOT Visual-mode) and assert the next-tick
route is built from `CircuitProcedure.goAroundPath`, not a normal circuit
pattern.

**If the exact hook for emitting this `PilotOutput.intent.route =
PilotRoute.None` is not obvious at task time** (e.g. the existing pilot
decide-cycle doesn't have a clean "step transition" hook for this), STOP
and split fn-11.1 into:
- **fn-11.1a (investigation task):** find the right hook, prototype, validate.
- **fn-11.1b (migration + builder + new step):** the rest of fn-11.1.
This avoids architecture-discovery mid-task per `feedback_pass_scope.md`.

**Acceptance pin** (R-new — covered by R3 effectively, but call out
explicitly here): after `Report(GoingAround)` is observed in the records,
the next-tick airborne route is built from `CircuitProcedure.goAroundPath`,
NOT the runway threshold. Pilot-side compile-time test (see below) drives
the trained-GA mission to `GOING_AROUND` step and asserts the next tick's
`PilotIntent` matches this contract.

**Pilot-side compile-time test** (was: assert active-compound shape; now:
assert route invalidation + GA-route built):
- Add a pilot-side unit test in `pilot/commonTest` that drives a trained-GA
  mission through to `GOING_AROUND` step, then asserts the tick after
  GOING_AROUND completes produces `PilotIntent(phase = Climbing, route =
  <a route built from CircuitProcedure.goAroundPath>, ...)`.
- This catches the route-invalidation contract directly. Mirrors
  `SelfInitiatedGoAroundResponseSpec`'s reactive-side row format.

The trigger is the **mission tree's compiled shape** (instructor's
`CircuitOutcome.GoAround` authorship at fixture time), NOT a sensor-driven
recognition path.

### 3. Commitment lifecycle: Option A — stage regression (RECOMMENDED, codebase-aligned, REVERSAL of interview answer)

**Critical reversal note** (per practice-scout pass-1 finding — Major):
the interview-time answer was "close current commitment + form new on rejoin
downwind". I made that recommendation without scout context. Scouts have
surfaced that the post-fn-8.3 codebase architecture is
**stage-regression-with-witness-reset**, NOT close-and-new:

- `CommitmentReconciliation.kt:279`: *"Go-around is a defined regression path
  (AwaitApproach/AwaitLandedObserved → AwaitDownwind)"*.
- `Controller.kt:609-661`: `advanceCommittedStages` + `isStageRegression` reset
  sticky witnesses (`touchedDownDuringCommitment`, `pilotReadyDuringCommitment`,
  `observedReportsDuringCommitment`) on regression.
- `controller/.../procedure/TowerArrival.kt:140-156`: `GA-PRE-CLEAR` and
  `GA-POST-CLEAR` interrupts already wire `GoAroundEvent` →
  `targetStage = AwaitDownwind`.
- fn-8.3 commits `1b1d83a → 5f1bf90` ratified this architecture via codex SHIP
  on pass 5.

**Recommendation: Option A (stage regression, codebase-aligned).** The
commitment lifecycle survives the GA; the stage backtracks to `AwaitDownwind`;
sticky witnesses reset on regression. G3a's test pin asserts witnesses on the
*same* commitment at close-time (one continuous lifetime spanning fork → rejoin
→ land). No re-architecture of fn-8.3 needed.

**Option B (close-and-new — interview answer)** would require:
- Re-wiring `Controller.kt:621-626` regression detector to be a close-emitter.
- Re-wiring `GA-POST-CLEAR` rule to close + emit.
- A new commitment-formation arm at `ARR-JOIN-CIRCUIT` (the
  `RunwayAssignment.kt:240` "Land → JoinCircuit" comment hints at this shape).
- Re-baselining fn-8.3's witness-reset paths.

This is multi-pass scope and rolls back fn-8.3's just-ratified architecture.

**Plan defaults to Option A and flags the reversal for user ratification at
plan-review time.** If the user prefers Option B, switching is multi-task scope
and changes fn-11's task split.

### 4. World-only test triggers principle (per `feedback_world_only_test_triggers.md`)

The trigger is the pilot's `HighLevelGoal.CircuitTraining(outcomes = ...)`
authored at fixture time — instructor-side authorship of training intent. NOT
an event injection, NOT a hardcoded rule, NOT direct mutation of pilot belief.
The mission-tree compiler reads the goal at `createMission` and forks the tree
accordingly; from that point on, the pilot decides autonomously per existing
reasoning.

## Strategy Alignment

_No active strategy track served — review for drift._

(STRATEGY.md is not present at planning time; husk-vs-presence per
`flowctl strategy status` reports `sections_filled = 0`.)

## Phraseology citations (verified verbatim from `research/txt/`)

- **CAP 413 §4.67** (UK CAA Radiotelephony Manual):
  *"In the event of missed approach being initiated by the pilot, the phrase
  'going around' shall be used."*
  Verified at `research/txt/cap413-aerodrome-chapter.txt:1082-1087`.
- **CAP 413 §4.68**: Controller acknowledges with `"<callsign>, Roger"`.
  Verified at `research/txt/cap413-aerodrome-chapter.txt:1088-1090`.
- **CAP 413 §4.66**: VFR aircraft is to continue into the normal traffic circuit
  unless instructions are issued to the contrary. Verified at
  `research/txt/cap413-aerodrome-chapter.txt:1075-1080`.
- **ICAO Doc 4444 §12.3.4.18** (PANS-ATM Aerodrome Control phraseology):
  pilot transmission `*GOING AROUND.`, controller direction
  `GO AROUND;`. Verified at
  `research/txt/icao4444-extracted.txt:15233-15244`.

## Approach

The work splits into **(1) the migration + compiler change** and **(2) the
golden test + cross-references**. Per `feedback_pass_scope.md`, fold the
typed-surface migration of all 25+ call sites into fn-11.1's closing pass.

### Foundation (fn-11.1)

1. **`CircuitOutcome` sealed type** at the right layer (mirror existing
   `HighLevelGoal` placement at `pilot/.../PilotMission.kt:15-22`):
   ```kotlin
   sealed interface CircuitOutcome {
       data object TouchAndGo : CircuitOutcome
       data object FullStop : CircuitOutcome
       data object GoAround : CircuitOutcome
   }
   ```
2. **`HighLevelGoal.CircuitTraining`** replaces the existing constructor:
   ```kotlin
   data class CircuitTraining(val outcomes: List<CircuitOutcome>) : HighLevelGoal {
       init {
           require(outcomes.isNotEmpty()) { "..." }
           require(outcomes.last() == CircuitOutcome.FullStop) { "..." }
       }
   }
   ```
3. **`planMission` compiler** (`PilotMission.kt:514-522`) — replace the
   `(circuits, fullStopOnLast)` builder loop with an `outcomes.map { outcome ->
   when (outcome) { ... } }`. Three arms: `TouchAndGo` →
   `touchAndGoCircuitTask()`, `FullStop` → `circuitTask()`, `GoAround` → NEW
   `plannedGoAroundCircuitTask()`.
4. **`plannedGoAroundCircuitTask()`** — new builder mirroring the structure of
   the existing reactive subtree-replacement (`Pilot.kt:541-551`'s
   `goAroundTask() + circuitTask()`), but compiled statically (no runtime
   replacement). Per pass-2 plan-review findings #1 + #2, the implementation
   is **Option α with explicit route invalidation** (no longer "audit at
   task time"):
   - Fly standard pattern legs (FLY_DEPARTURE → FLY_DOWNWIND → REPORT_DOWNWIND
     → AWAIT_SEQUENCING → FLY_BASE → REPORT_BASE).
   - **Final leg uses NEW `MissionStep.FLY_FINAL_TO_SHORT_FINAL` primitive**
     (replacing `FLY_FINAL`). Completion-checker arm: completes when
     `aircraft.altitudeM <= DECISION_ALTITUDE_M` (~100m, mirroring
     `pilot/observe/PilotEvent.kt:48`). Route is the same as `FLY_FINAL`'s
     (route-planner aliases the new step). See Decision Context #2 for the
     two implementation options + Option α recommendation.
   - **On `FLY_FINAL_TO_SHORT_FINAL` completion, the kinematic airborne
     route MUST be invalidated** (cleared / set null) — see Decision Context
     #2. Without route invalidation, the existing reactive special-case at
     `Pilot.kt:349-370` (which builds the GA route) cannot fire, and the
     aircraft continues to the threshold instead of going around.
   - Append existing `goAroundTask()` subtree
     (`CompoundTask(TaskName.GoAround, [PrimitiveTask(GOING_AROUND, REPORTED)])`
     — uses `CircuitProcedure.goAroundPath` via `buildGoAroundRoute` once
     the special-case fires post-route-invalidation).
   - The next outcome's compound takes over via the standard list-walk.
5. **Migrate 25+ call sites** to the new constructor shape. Mostly mechanical;
   no change in behavior for existing tests since
   `CircuitOutcome.{TouchAndGo, FullStop}` map cleanly from the old Bool.
6. **`isCircuitLike()`** at `PilotMission.kt:594-600` is total over `TaskName`.
   If `plannedGoAroundCircuitTask()` introduces a new `TaskName` leaf
   (e.g. `CircuitTrainingPlannedGoAround`), the predicate must add an arm —
   no fallthrough per `feedback_no_corners.md`.
7. **`resetForGoAround` field-naming convention** (`PilotMission.kt:212-239`)
   requires every mission field be named in the function. If new mission state
   tracks "which outcome is current", the reset semantics need a recorded
   decision in this function.

### Test + cross-references (fn-11.2)

8. **`G3aPilotTrainedGoAroundTest.kt`** at
   `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/`. Single `@Test` mirroring
   `LowgGoldenTest.kt`'s shape. Fixture: `Fixtures.LOWG` extended with the new
   goal, OR new `Fixtures.LOWG_TRAINED_GO_AROUND` if the goal authoring is
   distinct enough. Goal: `CircuitTraining(outcomes = listOf(GoAround,
   FullStop))`. Pins per Acceptance below.
9. **Cross-reference doc updates**:
   - `AGENTS.md` § Golden tests — heading becomes `(G0, G1, G1 minimal, G2,
     G3a)`; new G3a bullet after G2 entry (lines 134-195).
   - `LowgGoldenTest.kt:51-54` — add `@see G3aPilotTrainedGoAroundTest`.
   - `G1TwoAircraftCircuitsTest.kt:42-83` — add G3a to sibling list (KDoc has
     no `@see` block currently; mirror existing prose form).
   - `G1TwoAircraftMinimalSpec.kt:63` — add `@see G3aPilotTrainedGoAroundTest`.
   - `G2CrossAerodromeVfrTest.kt:48-83` — add G3a to "Sibling tests" inline list.
   - `Fixtures.kt:18-47` — add G3a entry in object KDoc per-fixture provenance
     block (if a new fixture lands).
   - `pilot/.../PilotMission.kt:8-14` — sealed-interface KDoc full re-write to
     describe `CircuitOutcome` semantics.
   - `pilot/.../PilotMission.kt:486` — `planMission` KDoc updated for the new
     branching arm.
   - `wiki/design-decisions/2026-04-21-ifr-pilot-route-planner.md:7` — table
     row referencing `HighLevelGoal.CircuitTraining` updated.
   - `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` —
     resolution note: gap closed by G3a.
   - `docs/design/ifr-pilot-route-planner-plan.md:141` — `planMission` stub
     comment if relevant.
10. **Time-band tightening** (per fn-8.3 spec decision #11 inheritance):
    first-implementation uses the 30-min generous ceiling; after first green,
    capture observed wall in `## Evidence` and tighten to ±15% band — both
    iterations done in fn-11.2.

### Pattern reuse (DO NOT DUPLICATE)

- `pilot/.../PilotMission.kt:474-483` — `goAroundTask()` (VFR) and
  `ifrGoAroundTask()` (IFR) production builders. Reuse, don't duplicate.
- `pilot/.../Pilot.kt:509-563` — `GoAroundResult` + `applySelfInitiatedGoAround`
  produce mission-update + transmission + `Climbing` intent. **Reuse the
  response stage** for any pilot-side reactive paths; the **recognition stage
  is what changes for G3a-trained** (planned outcome at compile time vs
  decision-altitude trigger at runtime).
- `pilot/.../PilotRoutePlanner.kt:455-484` — `buildGoAroundRoute` consumes
  `CircuitProcedure.goAroundPath`. Reused.
- `protocol/.../Instruction.kt:937-957` — `ReportEvent.GoingAround` already
  exists at line 948. **No new typed surface for the pilot transmission.**
- `controller/.../bdi/Guard.kt:681-686` — `GoAroundEvent` guard. Reused for
  `GA-PRE-CLEAR` / `GA-POST-CLEAR` rules already wired.
- `controller/.../Controller.kt:609-661` — `advanceCommittedStages` +
  `isStageRegression` machinery already resets sticky witnesses on regression.
  **Reused — no re-architecture.**
- `sim/.../testing/SimTraceQueries.kt:124` — `commitmentStageTransitions(aircraft,
  controller)` query used to assert the regression path.
- `pilot/.../observe/PilotEvent.kt` — sealed `PilotEvent` recognition channel.
  G3a-trained does NOT add a new leaf here — the mission tree's static
  compilation IS the trigger; no new recognition surface.

## Risks / dependencies

- **Hard dep: fn-8** (done) — per-aircraft RNG, fixture infrastructure, sticky
  witnesses + regression-reset machinery, SimTrace queries. Without fn-8 the
  test infrastructure doesn't exist.
- **Soft dep: fn-6** (done) — `AircraftObservation.coords: Position` for any
  altitude/distance reads at the short-final fork point.
- **Soft dep: fn-7** (done) — `Doctrine.IcaoAnnex11` constants + per-aerodrome
  `ctrApproximationRadius`. G3a is single-aerodrome at LOWG; relevant if any
  CTR boundary check fires during the GA trajectory.
- **Risk: Option A vs Option B architectural reversal.** The interview-time
  answer was Option B (close + new commitment). Plan defaults to Option A
  (stage regression — codebase-aligned post fn-8.3) and flags for user
  ratification. If user picks Option B, fn-11 scope grows materially: re-wires
  `Controller.kt:621-626`, `GA-POST-CLEAR`, witness-reset paths; rolls back
  fn-8.3's just-ratified architecture. Multi-task scope.
- **Risk: route invalidation on FLY_FINAL_TO_SHORT_FINAL completion**
  (per pass-2 plan-review finding #2). The trained-GA flow REQUIRES the
  kinematic route to be cleared so the existing reactive special-case at
  `Pilot.kt:349-370` can fire. Implementer audits where to wire the
  route-clear (step-completion arm for the new step OR `pilotDecide`
  step-advancement OR mirror `applySelfInitiatedGoAround`'s side-effects).
  If the route-clear logic isn't trivially wired (>~5 sites), STOP and
  split per `feedback_pass_scope.md` discipline.
- **Risk: new `MissionStep` enum value blast radius** (per pass-2
  plan-review finding #5 — `MissionStep` is an enum, not sealed; many
  consumers use sets, not exhaustive `when`). New-MissionStep audit must
  cover at minimum: `Pilot.kt`'s `airborneSteps` gate, `skipCompletedSteps`,
  route-planner step-step gating, transmission/completion maps, any
  set-membership checks. If extension is large (>~10 sites), STOP and split.
- **Risk: 25+ call-site migration.** Mechanical but must be exhaustive. Detekt
  + compile errors will surface most; explicit grep-pass at task time confirms
  none missed.
- **Risk: G0/G1/G1-minimal/G2 stability** — if migration touches the existing
  golden tests' goal-construction lines (it does — `LowgGoldenTest.kt:77`,
  `G1TwoAircraftCircuitsTest.kt:177,184`, etc.), care needed that the
  mapped-`CircuitOutcome` form produces byte-identical mission trees. Per
  practice-scout: "old `(circuits=2, fullStopOnLast=true)` ≡ new
  `[TouchAndGo, FullStop]`". Verify by green test run.
- **Risk: pre-existing `:migration:jvmTest` flake** —
  `LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport`. Out
  of fn-11 scope; if it fires, capture verbatim per the established convention.
- **Risk: pilot-side `applySelfInitiatedGoAround` vs trained-GA path
  conflation.** Reactive helper is for `PilotEvent.DecisionAltitudeWithoutClearance`
  triggers. Trained GA is plan-driven from the goal. Per practice-scout
  anti-pattern #6, separate code paths. Practice-scout +
  `feedback_world_only_test_triggers.md` are explicit on this.

## Acceptance

- **R1:** `HighLevelGoal.CircuitTraining(outcomes: List<CircuitOutcome>)`
  exists with `CircuitOutcome` sealed hierarchy (`TouchAndGo / FullStop /
  GoAround`). The old `(circuits: Int, fullStopOnLast: Boolean)` constructor
  is REMOVED, not deprecated. `init` requires:
  - `outcomes.isNotEmpty()`,
  - `outcomes.last() == CircuitOutcome.FullStop` (per pass-3 plan-review
    finding #4 — terminal outcome must be FullStop or `groundArrivalTask`
    silently wedges).
- **R2:** All 25+ existing call sites of `HighLevelGoal.CircuitTraining(...)`
  migrated to the new shape. Old G0 = `listOf(FullStop)`; old G1 =
  `listOf(TouchAndGo, FullStop)`; etc. No default-arg shim, no companion
  factory.
- **R3:** `planMission`'s `CircuitTraining` arm (currently
  `PilotMission.kt:514-522`) compiles `outcomes.map { outcome -> when (outcome)
  { ... } }` into per-circuit `CompoundTask`s. Three arms:
  `TouchAndGo` → existing `touchAndGoCircuitTask()`, `FullStop` → existing
  `circuitTask()`, `GoAround` → NEW `plannedGoAroundCircuitTask()`.
- **R4:** `plannedGoAroundCircuitTask()` builder lands. Composition: pattern
  legs (downwind / base / final-to-short-final) + existing `goAroundTask()`
  subtree + handoff to next outcome via the list-walk. Per pass-9 plan-
  review finding #5: the builder reuses `goAroundTask()` (the mission-tree
  level reuse); the post-`GOING_AROUND` route planner reuses
  `buildGoAroundRoute()` / `CircuitProcedure.goAroundPath` (the routing-
  level reuse — invoked via the Circuit-mode special-case described in
  Decision Context #2 (B), NOT inside the builder). NO duplication.
- **R5:** New `G3aPilotTrainedGoAroundTest.kt` exists at
  `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/`. Mirror of
  `LowgGoldenTest`'s shape. Goal: `CircuitTraining(outcomes = listOf(GoAround,
  FullStop))`.
- **R6:** Test pins (per practice-scout's three-layer pattern):
  - **Causal**: `Report(GoingAround)` transmission appears in the records
    *before* any `ClearedToLand` for circuit 2 *and before* the final landing
    `Report(RunwayVacated)` event.
  - **Sticky witness**: at the close of circuit 1's commitment lifetime
    (read via `SimTrace.commitmentStageTransitions`), Option A's stage-
    regression invariants hold.

    **Regression source (per pass-3 plan-review finding #2 + pass-4
    finding #3 + pass-8 finding #1 — `CircuitIntent` semantics):** real-
    ATC + the codebase's `ARR-LAND` rule fires `ClearedToLand` proactively
    once the pilot reports Downwind with `CircuitIntent.FULL_STOP`,
    `HasReportedPositionCall` is satisfied, the runway is clear, etc.
    `TowerArrival.kt`'s ARR-LAND rule gates on the pilot's reported
    circuit intent: `ClearedToLand` for FULL_STOP, `ClearedTouchAndGo` for
    TOUCH_AND_GO. The current `CircuitIntent` enum has only
    `TOUCH_AND_GO` / `FULL_STOP` (null otherwise) — no `GO_AROUND` value.

    **For trained `CircuitOutcome.GoAround`, the pilot reports
    `Report(Downwind, intent = CircuitIntent.FULL_STOP)`** at downwind —
    the pilot does NOT signal "I'm going around" until short-final. This
    mirrors real-ATC training: the trainee announces a normal full-stop
    intent, the controller clears to land, then the trainee goes around
    at short-final per the training plan (instructor's pre-arranged
    exercise). The decision to go around is private to the pilot's
    mission-tree shape; the radio carries the *intended* outcome at
    downwind, then the *actual* go-around announcement at short-final
    per CAP 413 §4.67.

    Trained-GA compound REPORTS Downwind + Base normally
    (`REPORT_DOWNWIND` with FULL_STOP intent + `REPORT_BASE`). Sufficient
    to satisfy `HasReportedPositionCall`. The controller issues
    `ClearedToLand` for circuit 1 during downwind/base/final, BEFORE the
    pilot's `Report(GoingAround)` fires at short-final. The trained GA is
    therefore POST-clearance: `GA-POST-CLEAR` (`TowerArrival.kt:148-156`)
    fires from `LandingClearanceIssued` or `AwaitLandedObserved` →
    `AwaitDownwind`.

    **Implementation pin (R-new):** the pilot's `Report(Downwind)` for
    a trained-GA circuit carries `CircuitIntent.FULL_STOP`. fn-11.1
    must update the pilot transmission logic to emit FULL_STOP for the
    GoAround outcome (same as for the FullStop outcome). The
    distinction between FullStop-actual-landing and GoAround-trained
    lives in the mission tree's continuation, not in the radio
    transmission's intent field. NO new `CircuitIntent` enum value
    needed (the existing FULL_STOP suffices).

    **Pin shape:** stage transition `from in
    setOf(TowerArrivalStage.LandingClearanceIssued,
    TowerArrivalStage.AwaitLandedObserved) → TowerArrivalStage.AwaitDownwind`
    observed exactly once during circuit 1. Per pass-1 plan-review
    finding #3, names are `TowerArrivalStage.X` (not bare `Stage.X`).

    **Radio-delivery prerequisite (per pass-9 plan-review finding #3):**
    `GA-POST-CLEAR` regression depends on `GoAroundEvent` which fires on
    received `Report(GoingAround)`. If the pilot's transmission is
    stepped-on / delayed, the controller doesn't see it and won't
    regress. Test must explicitly pin: **`Report(GoingAround)` must be
    received and processed by the controller before the regression-pin
    check.** Per pass-13 plan-review finding #3: assert delivery via
    available trace observables — the simplest pin is that the records
    collection contains the pilot's `Report(GoingAround)` directed at /
    received by the tower, OR (if records' shape doesn't surface
    recipient cleanly) that the regression-pin's stage-transition time
    is AFTER the `Report(GoingAround)` record's time. Controller events
    are transient inside `controllerDecide`, NOT a persisted queryable
    surface — do NOT pin against `tower.beliefs.events`. Single-
    aircraft scenarios have minimal radio contention; false-positive
    failure is unlikely but not impossible.

    **Causal pin** (added per pass-3 plan-review finding #2 — pin the
    actual pre/post-clear path): `ClearedToLand(circuit 1).time <
    Report(GoingAround).time` — i.e. clearance arrives before the trained
    GA fires. Distinguishes the trained-GA-post-clearance scenario from
    a hypothetical pre-clearance variant.

    **Post-regression sticky witnesses are reset**:
    `touchedDownDuringCommitment == false`,
    `observedReportsDuringCommitment.isEmpty()` (per fn-8.3's reset
    machinery at `Controller.kt:629-638`).

    (If the user picks Option B post plan-review, this pin reshape:
    assert circuit-1 commitment is *absent* from `BeliefState.commitments`
    after the GA report; a new commitment is *present* after the rejoin
    downwind.)
  - **Kinematic non-event**: no state in the trace where `phase ==
    PilotPhase.LandingRoll` for the aircraft *before* the `Report(GoingAround)`
    event time — the GA prevents touchdown on circuit 1.
- **R7:** Aircraft completes circuit 2 with full-stop landing per the second
  outcome in the list. Vacate readback closes its coordination per fn-8.3's
  discipline (coordination entry absent from `BeliefState.coordinations` after
  correct readback).
- **R8:** Time band tightened to ±15% around observed wall (first-impl uses
  30-min generous ceiling; tighten in fn-11.2). Per pass-1 plan-review
  finding #5: the test KDoc must explicitly state the observed completion
  time, the chosen ±15% bounds, and the rationale (mirroring G1's pattern at
  `G1TwoAircraftCircuitsTest.kt:511-540`). Observed wall + computed bounds
  also captured in fn-11.2's `## Evidence`.
- **R9a (legacy regression — fn-11.1):** No regression on existing suites
  post-migration. Verification command: `./gradlew :sim:jvmTest :pilot:jvmTest
  :controller:jvmTest :core:jvmTest :protocol:jvmTest detekt` must exit 0.
  G0 (`LowgGoldenTest`), G1 (`G1TwoAircraftCircuitsTest`), G1 minimal
  (`G1TwoAircraftMinimalSpec`), G2 (`G2CrossAerodromeVfrTest`) all stay green.
  Detekt baseline unchanged. fn-11 is a pilot/sim closure; `:migration:jvmTest`
  is NOT required. If migration is run anyway, the only acceptable failing
  test is `xyz.easiersaid.twr.migration.world.LjmbWorldCandidateValidationTest >
  writesLjmbCurrentCoreValidationReport()` (any other migration failure
  blocks fn-11.1).
- **R9b (full acceptance — fn-11.2):** New `G3aPilotTrainedGoAroundTest`
  green PLUS R9a's legacy-regression set still green. Per pass-5 plan-review
  finding #2: fn-11.1 alone cannot satisfy R9 because G3a doesn't exist
  yet; R9 is split across both tasks.
- **R10:** Cross-reference doc updates landed: AGENTS.md § Golden tests adds
  G3a row; LowgGoldenTest / G1TwoAircraftCircuitsTest / G1TwoAircraftMinimalSpec
  / G2CrossAerodromeVfrTest sibling references add G3a; Fixtures.kt KDoc
  entry; PilotMission.kt sealed-interface + planMission KDocs updated;
  wiki/design-decisions/2026-04-21 + 2026-04-22 entries updated.

## Review considerations

Per `feedback_plans_review_aware.md`. All four axes addressed inline.

### FP / type-safety

- `CircuitOutcome` is a sealed hierarchy; `when` over it is exhaustive at
  compile time. Three case `data object` leaves (no payload yet — payload like
  per-circuit altitude target can be added later without breaking call sites).
- `outcomes: List<CircuitOutcome>` replaces `(Int, Bool)` — the new shape is
  strictly more expressive (lossless mapping of the old shape).
- `init { require(outcomes.isNotEmpty()); require(outcomes.last() ==
  CircuitOutcome.FullStop) }` enforces the non-degenerate case + terminal
  invariant at construction (mirrors the old `circuits > 0` and adds the
  silent-wedge guard per pass-3 plan-review finding #4).
- No new `expect/actual` pairs — `HighLevelGoal` lives in `commonMain`; sealed
  hierarchy stays there. KMP-clean.
- `isCircuitLike()` totality over `TaskName` preserved per
  `feedback_no_corners.md`.
- `resetForGoAround` field-naming discipline preserved; if new mission fields
  added, named.

### Test architecture

- Three-layer pin pattern (causal partial-order + sticky-witness + kinematic
  non-event) catches different failure shapes. Per practice-scout: single-
  witness tests are an anti-pattern.
- Trained GA is **distinct code path** from reactive GA per
  `feedback_world_only_test_triggers.md`. Tests pin that `Report(GoingAround)`
  is sent. Per pass-2 plan-review finding #6, the "byte-identical controller
  response" claim is dropped from acceptance — it would require an explicit
  controller-acknowledgment assertion, but the controller-side code today
  doesn't actively transmit a `Roger` for pilot-elected GA (it only updates
  `BeliefState.circuitIntent` per `BeliefFoldSpec.kt:52` and clears
  responsibility, both passive). Asserting on a Roger transmission would
  require new ATC-side rules out of scope for fn-11. Per practice-scout:
  "the controller doesn't peek at `HighLevelGoal.outcomes`" — the firewall-
  integrity property holds STRUCTURALLY (the controller has no access to the
  goal), regardless of whether we add an additional run-time pin for it.
- G0 / G1 / G1-minimal / G2 stability is the load-bearing regression risk;
  per-fixture migration must yield byte-identical mission trees for the
  existing scenarios.
- Test trigger is `HighLevelGoal` authorship at fixture time — never an
  injected event, never a rigged decision. Validates
  `feedback_world_only_test_triggers.md` for the trained-outcome pattern.

### Impact

- 25+ call-site migration is mechanical but exhaustive. Compile errors will
  surface most; explicit grep-pass at task time confirms none missed.
- `HighLevelGoal` is pilot-internal (`PilotMission.kt:11-14` says "never
  crosses the firewall"). No protocol / serialization / cross-aerodrome
  consumers. Migration blast radius is bounded.
- New test file (~400-500 lines mirroring G0's shape).
- Doc updates in 9-10 files.
- Pilot mission compiler (`planMission` arm) — single function changed; one
  new task builder added (`plannedGoAroundCircuitTask`).
- Route planner — possible per-leg altitude-termination extension if not
  already supported. Audit at task time; STOP if extension grows beyond ~10
  call sites.
- Controller side: zero changes. fn-8.3's sticky-witness machinery + GA
  regression rules already handle the trained GA shape correctly.
- World model: zero changes.

### Operational correctness

- Per CAP 413 §4.66/§4.67/§4.68 (verbatim verified) and ICAO Doc 4444
  §12.3.4.18: pilot-initiated GA phraseology is exactly "<callsign>, going
  around"; controller acknowledges with "<callsign>, Roger". Default re-entry
  is "into the normal traffic circuit" per CAP 413 §4.66 — no specific vector
  required from controller. The pilot's training plan dictates the re-entry
  trajectory (climb runway-heading → re-join downwind), consistent with §4.66.
- Per `feedback_reality_anchored.md`: model real ATC. Trained GA is real-world
  flight-school doctrine (FAA AFH Ch.9: "any approach or landing may result
  in a go-around"). Doesn't soften the existing reactive GA; complements it.
- Per `feedback_world_only_test_triggers.md`: the test trigger is
  instructor-authored intent (the `outcomes` list), not a sensor event. The
  pilot's mission compiler reads the goal at `createMission` and fork the tree
  statically. From there, the pilot decides autonomously per existing
  reasoning. Controller has no peek into the goal — firewall integrity holds
  structurally (the controller has no field on its inputs that surfaces
  `outcomes`). Per pass-2 plan-review finding #6, no run-time
  byte-identical-response assertion is shipped (would require new ATC-side
  rules for pilot-elected GA acknowledgment which the codebase doesn't have
  today; out of fn-11 scope).
- Stage-regression model (Option A, recommended) is doctrinally faithful: in
  real ATC the "approach phase regresses" interpretation matches CAP 413
  §4.66 ("continue into the normal traffic circuit") — the aircraft is *still*
  the controller's responsibility, *still* in the same approach commitment
  conceptually, just at an earlier stage.

## Early proof point

Task `fn-11-...1` lands the `CircuitOutcome` ADT + the pilot mission compiler
fork + the 25+ call-site migration. If the mission compiler fork can't be
expressed cleanly (e.g. short-final altitude termination requires major route-
planner refactor), STOP and re-evaluate the fork-point design before fn-11.2
starts.

If R3 / R4 land cleanly, fn-11.2's golden test should be a structural mirror
of `LowgGoldenTest` with no architectural surprises.

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | `CircuitOutcome` sealed hierarchy + new `CircuitTraining(outcomes)` constructor | fn-11-...1 | — |
| R2  | 25+ call-site migration, no shim | fn-11-...1 | — |
| R3  | `planMission` arm walks `outcomes.map { ... }` with three branches | fn-11-...1 | — |
| R4  | `plannedGoAroundCircuitTask()` builder lands; reuses `goAroundTask()` + `goAroundPath` | fn-11-...1 | — |
| R5  | `G3aPilotTrainedGoAroundTest.kt` exists; mirrors G0 shape | fn-11-...2 | — |
| R6  | Three-layer pins (causal + sticky-witness + kinematic non-event) | fn-11-...2 | — |
| R7  | Circuit 2 lands cleanly; coordination closes per fn-8.3 discipline | fn-11-...2 | — |
| R8  | Time band ±15% post-first-green; observed wall in evidence | fn-11-...2 | — |
| R9a | Legacy regression: G0/G1/G1-minimal/G2 + non-migration suites + detekt baseline stay green post-migration (per pass-5 plan-review finding #2 — split from R9) | fn-11-...1 | — |
| R9b | Full fn-11 acceptance including new G3a green | fn-11-...2 | — |
| R10 | Cross-reference doc updates land | fn-11-...2 | — |

## Deferments register

**Filing venue (consistent with fn-7 / fn-8 closure):** entries are filed in
`~/.claude/plans/pilot-firewall.md` § Deferments register, per the user's
persistent memory `reference_audit_registers.md`. `.plan` is NOT updated under
fn-11.

Forward-looking entries (not acceptance criteria):

- **`D-PASS-g3a-react-crosswind-trigger`** — pilot-reactive go-around triggered
  by world wind state shifting past aircraft POH crosswind limit on final.
  Adds `AircraftType.maxCrosswindKnots` POH-derived field + pilot wind-limit
  reasoning. Reuses fn-11's mission-tree fork. Surfaces PIC-overrides-clearance
  doctrine in the codebase. Future scenario.
- **`D-PASS-g3a-obstruction-runway-state`** — ATC-mandated go-around triggered
  by world-modeled runway obstruction. Adds typed `RunwayObstruction` surface
  on the world model + tower-visual sensing channel from world to controller's
  `BeliefState` + reactive ATC rule. First scenario in the codebase to wire
  tower-visual sensing as a real channel; foundational for all future
  collision-avoidance / surface-incursion / FOD scenarios. Future scenario.
- **`D-PASS-instructor-agent-surface`** — explicit instructor agent that
  authors `CircuitOutcome` per circuit, possibly with mid-flight amendments
  ("instructor sees student fumble, calls 'go around now'"). Same firewall
  doctrine: instructor is upstream-of-pilot world authorship. Useful future
  shape; not load-bearing for fn-11.

Closures:

- **The "Any mission type that supports go-around must have a go-around
  integration test before merge" gap** filed in
  `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` is
  closed by fn-11 via R5/R6. Document the closure in the design-decisions wiki.

## Errata

- 2026-05-11 (fn-17): CAP 413 §-cites in this spec were authored
  against the then-current Edition 23 numbering. Per fn-17.1's
  primary-source verification (artifact:
  `wiki/data-sources/cap413-edition-24-capture.md`; CAA PDF SHA
  `c620cda9b6bdbe8e9ed51b258e4df2f6e3edc839226e53ee2b591cb696a966ac`),
  Ed 24 (effective 2026-07-01) maps as follows: §4.65 (ATC-initiated
  GA) → §4.64; §4.66 (VFR-continue) → §4.65; §4.67 (pilot-initiated
  GA) → §4.66; §4.68 (military) → §4.67. Current-doctrine citations
  live in `protocol/.../RegulationDatabase.kt` (Ed 24-coherent
  post-fn-17.1); this spec's prose is preserved as-is for historical
  fidelity.
