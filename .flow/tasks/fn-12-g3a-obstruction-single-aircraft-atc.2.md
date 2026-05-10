---
satisfies: [R9a, R9b, R9c, R12]
---

## Description

Pilot-side reactive ATC-GA in Circuit-mode via **`pendingAtcGoAroundFrom: Option<MissionStep>` flag in mission state**. Surfaces and fixes a latent Circuit-mode reactive-GA bug.

**Why a flag, not a recognition predicate**: the naive "preStep + currentStep" predicate doesn't work because by the time the cycle that handles the ATC GA instruction runs, the mission tree has already been rewritten by `processInstruction(GoAround)` → `handleGoAround` (which lives in `PilotCognitive.kt:689,944-955`). Whether `processInstruction` is called from inside `pilotCognitiveDecide`'s sequence or as a separate dispatch is an implementation detail (codex iter 12 noted these may not be the same call chain on all paths) — what matters is the **temporal relationship**: by the time recognition runs in any cycle of `pilotDecide`, the mission's currentTask has already been moved past the original on-final step. The flag captures the original step at the moment `handleGoAround` rewrites the tree, regardless of which cycle or which call chain.

**Fix**: `handleGoAround` at `PilotCognitive.kt:944-955` records the pre-rewrite step into a new `PilotMission.pendingAtcGoAroundFrom: Option<MissionStep>` field. Subsequent `pilotDecide` invocations read-and-clear the flag and apply Tick A intent override (`route = None`, `phase = Final` retained). Tick B reuses fn-11.1's `isCircuitTrainedGoAroundTickB` predicate + `planCircuitTrainedGoAround` planner verbatim — **zero new route-planning code**.

**Precedence vs other GA paths in `pilotDecide`** (must be deterministic). **Implementation step**: the existing `pilotDecide` at `Pilot.kt:117-122` runs `derivePilotEvent(...)` (self-initiated path) BEFORE planned trained-GA detection. The new ordering must be:

1. **Trained-GA recognition** (existing fn-11.1 path) — `preStep == FLY_FINAL_TO_SHORT_FINAL && currentStep == GOING_AROUND`. If matches, fires `applyPlannedGoAround` and short-circuits.
2. **ATC-reactive recognition** — checks `mission.pendingAtcGoAroundFrom` flag (post-cognitive mission state). If flag is `Some(...)` AND discriminator passes, fires `applyAtcInitiatedGoAround` and clears the flag. If flag is `Some(...)` AND discriminator fails, flag is cleared anyway (defensive).
3. **Self-initiated `derivePilotEvent`** (existing — produces `DecisionAltitudeWithoutClearance`) runs only when neither trained-GA nor ATC-reactive have fired.

**Exact intended control flow** (per codex iter 18 — embedded `Pilot.kt` shows `derivePilotEvent` runs BEFORE planned-GA in current code; the refactor needs to be precise):

After `pilotCognitiveDecide(aircraft, mission, ...) → (postAircraft, postMission)`:

1. **Trained-GA candidate**: if `preStep == FLY_FINAL_TO_SHORT_FINAL && currentStep == GOING_AROUND`, build a `PlannedGoAroundResult` via `applyPlannedGoAround(...)`. Capture as `trainedResult: PlannedGoAroundResult?`.
2. **ATC-reactive candidate**: if `trainedResult == null` AND `postMission.pendingAtcGoAroundFrom is Some` AND discriminator passes, build `AtcGoAroundResult` via `applyAtcInitiatedGoAround(...)`. If flag is `Some` but discriminator fails, clear the flag (defensive). Capture as `atcResult: AtcGoAroundResult?`.
3. **Self-initiated path** (existing — UNCHANGED): if both `trainedResult` and `atcResult` are null, invoke `derivePilotEvent(postAircraft, postMission)` as before. The existing self-initiated trigger logic, mission state used by route planning, and `effectiveMission` selection all run UNCHANGED at the same tick they currently run.
4. **Composition**: thread the chosen result (`trainedResult ?: atcResult ?: ... self-initiated path`) into route planning + final intent.

**Behavioral preservation pin (R9c)**: the existing `SelfInitiatedGoAroundResponseSpec` test — same trigger tick, same `Report(GoingAround)`, same route/mission reset. The factoring is **additive** for trained/ATC paths; self-initiated runs at its original site when neither fires.

**Acceptance pin (R9c)**: existing `SelfInitiatedGoAroundResponseSpec` test passes UNCHANGED — same trigger tick, same `Report(GoingAround)`, same route/mission reset. The reordering is purely additive: trained-GA and ATC-reactive recognitions are checked first; if neither matches, self-initiated runs at its original site. The `mission.hasClearance` gate at `PilotEvent.kt:78` already excludes the post-clearance state where the ATC-reactive path fires.

The Tick A apply function is **intent-only** — it does NOT call `mission.resetForGoAround(now)` because `handleGoAround` already did.

**Size:** M. Touches 4 pilot-side files + 1 new test file.

**Files:**
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt` — add `pendingAtcGoAroundFrom: Option<MissionStep> = None` field to `PilotMission` data class
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt:30-45` — add `AtcGoAroundOnFinal(aircraft: AircraftId)` sealed leaf (constructed at the recognition site in `pilotDecide`)
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt:944-955` — extend `handleGoAround` to capture `originalStep = mission.currentTask?.step` BEFORE the rewrite and stamp `pendingAtcGoAroundFrom = Some(originalStep)` into the new mission state when the original step is on-final
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:117-122 area` — add ATC-issued reactive GA recognition arm reading `mission.pendingAtcGoAroundFrom`. Predicate fires `applyAtcInitiatedGoAround(...)`.
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt` — add `applyAtcInitiatedGoAround` function sibling to `applyPlannedGoAround` at lines 714-729. Intent-only override + clears the flag.
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotAtcInitiatedGoAroundSpec.kt` (NEW) — pilot-side unit tests

## Approach

### Step 1: typed event leaf (R9a)
Add to `PilotEvent.kt:30-45`:
```kotlin
sealed interface PilotEvent {
    // ...existing leaves...
    data class AtcGoAroundOnFinal(override val aircraft: AircraftId) : PilotEvent
}
```

The leaf carries `AircraftId`. No additional payload. **Constructed at the recognition site in `pilotDecide`**, NOT in `derivePilotEvent`.

### Step 2: PilotMission flag field (R9b part)
Add a new field to `PilotMission`:
```kotlin
val pendingAtcGoAroundFrom: Option<MissionStep> = None,
```
Default-`None` preserves all existing call sites. The flag's lifecycle:
- **Set by** `handleGoAround` in `PilotCognitive.kt:944-955` when ATC GA processing rewrites the mission tree.
- **Read by** the recognition arm in `pilotDecide` to decide whether to fire `applyAtcInitiatedGoAround`.
- **Cleared by** `applyAtcInitiatedGoAround` (returns updated mission with `pendingAtcGoAroundFrom = None`) — consumed.

Document the field in KDoc as a transient signaling state with single-cycle lifetime.

### Step 3: handleGoAround extension (R9b)
Modify `handleGoAround` at `PilotCognitive.kt:944-955`:
```kotlin
private fun handleGoAround(mission: PilotMission, now: SimTime): PilotMission {
    val originalStep = mission.currentTask?.step
    val pendingFlag = if (originalStep in setOf(
        MissionStep.FLY_FINAL,
        MissionStep.REPORT_FINAL,
        MissionStep.AWAIT_LANDING_CLEARANCE,
        MissionStep.LAND,  // post-ClearedToLand state — handleLandingClearance advances AWAIT_LANDING_CLEARANCE → LAND
    )) Some(originalStep!!) else None
    // ... existing tree rewrite to CircuitAfterGoAround ...
    // ... existing resetForGoAround(now) ...
    return /* rewritten mission */ .copy(pendingAtcGoAroundFrom = pendingFlag)
}
```

The flag is only set when the GA arrives during one of the four eligible on-final steps where Circuit-mode reactive GA is meaningful. Other steps (e.g., the GA arrives during FLY_DOWNWIND because of an earlier reactive separation conflict) leave the flag `None` — those paths use existing machinery (Visual-mode special-case at `Pilot.kt:451-472`, etc.).

### Step 4: recognition in pilotDecide (R9a)
**Critical**: do NOT extend `derivePilotEvent`. Recognition lives in `pilotDecide`.

In `pilotDecide` at `Pilot.kt:117-122` area (next to existing trained-GA recognition):
```kotlin
// existing trained-GA recognition (from fn-11.1) ...
val plannedGoAroundResult = if (preStep == MissionStep.FLY_FINAL_TO_SHORT_FINAL && currentStep == MissionStep.GOING_AROUND) {
    applyPlannedGoAround(...)
} else null

// NEW: ATC-issued reactive recognition via mission flag
val atcGoAroundResult = if (
    plannedGoAroundResult == null &&  // mutual exclusion (trained-GA wins if both somehow apply)
    newMission.pendingAtcGoAroundFrom is Some &&
    newMission.pendingAtcGoAroundFrom.getOrNull() in setOf(
        MissionStep.FLY_FINAL,
        MissionStep.REPORT_FINAL,
        MissionStep.AWAIT_LANDING_CLEARANCE,
        MissionStep.LAND,
    ) &&
    isEffectiveCircuitMode(newMission, world) &&  // effective-mode derivation; signature includes world per planRoute pattern
    aircraft.phase is PilotPhase.Final
) {
    val event = PilotEvent.AtcGoAroundOnFinal(aircraft.id)
    // emit event to trace ledger if applicable
    applyAtcInitiatedGoAround(aircraft, newMission, ...)
} else null

// Use whichever fired, or fall through to normal pilotDecide path
```

Verify the exact existing fork structure at `Pilot.kt:117-122` and integrate parallel-style. Use post-cognitive `newMission` (not pre-cognitive `mission`) because the flag is set inside `pilotCognitiveDecide → processInstruction → handleGoAround`.

**`isEffectiveCircuitMode(mission, world)` helper.** `PilotMission.navigationMode` is `Option<NavigationMode>` (at `PilotMission.kt:94`) but `createMission(...)` defaults it to `None` and `planRoute(...)` derives `NavigationMode.Circuit` from `mission.activeRunway + world` via `deriveNavigationMode(...)`. So gating on `mission.navigationMode.getOrNull()` alone fails for normal circuit-training missions.

Choose ONE of these as the canonical predicate:

- (a) **Reuse the same logic `planRoute` uses** — extract `isEffectiveCircuitMode(mission: PilotMission, world: AviationWorld): Boolean` helper that calls `deriveNavigationMode(mission.activeRunway, world, ...)` (or the equivalent — verify at task time). Use the helper in both `planRoute` and the new recognition predicate. **Helper signature MUST take `world`** — `deriveNavigationMode` needs it.
- (b) **Recognize the post-`handleGoAround` `CircuitAfterGoAround` tree shape** by walking `mission.activeTask`. No `world` parameter needed.

Pick at task time. Whichever is chosen, use it consistently across `planRoute` extraction, the recognition predicate, and the test fixtures. Document the choice in code KDoc and in the task's `## Done summary`.

### Step 5: Tick A response — intent-only + flag-clear (R9b)
Add `applyAtcInitiatedGoAround` to `Pilot.kt`, sibling to `applyPlannedGoAround` at lines 714-729. **Returns `AtcGoAroundResult(intent: PilotIntent, mission: PilotMission)`** mirroring `PlannedGoAroundResult` shape — produces a `PilotIntent`, NOT an updated `AircraftState` (verified against `applyPlannedGoAround`'s actual return).

```kotlin
data class AtcGoAroundResult(
    val intent: PilotIntent,
    val mission: PilotMission,
)

fun applyAtcInitiatedGoAround(
    aircraft: AircraftState,
    mission: PilotMission,
    // other params matching applyPlannedGoAround — verify exact signature
): AtcGoAroundResult {
    val newIntent = PilotIntent(
        route = PilotRoute.None,
        phase = PilotPhase.Final,  // retained — Tick B's predicate requires phase=Final
        // any other intent fields the trained sibling sets — match applyPlannedGoAround exactly
    )
    val newMission = mission.copy(pendingAtcGoAroundFrom = None)  // consume the flag
    return AtcGoAroundResult(intent = newIntent, mission = newMission)
}
```

**Intent-only — no `mission.resetForGoAround(now)` call.** `handleGoAround` already did it. The Tick A apply function:
1. Builds a `PilotIntent` with `route = None` and `phase = Final` retained
2. Clears the flag (consumes it)
3. Returns `(intent, mission)` — the calling site applies the intent to the aircraft state via the existing intent-application machinery (whatever fn-11.1's `applyPlannedGoAround` consumer does).

**Verify the existing `PlannedGoAroundResult` shape at task time** — match its return type and field names exactly. If `PlannedGoAroundResult` carries different fields (e.g. only intent without mission, or mission as a separate return), conform to that pattern.

**Flag-clear-on-every-`pilotDecide`-inspection** (codex iter 3 finding). The recognition arm in `pilotDecide` MUST clear the flag even when the discriminator fails — otherwise the flag lingers and could fire incorrectly later (e.g. if aircraft transitions to phase=Final mid-circuit through some other path):

```kotlin
// In pilotDecide, after pilotCognitiveDecide returns newMission:
val (atcResult, missionAfterAtcCheck) = if (newMission.pendingAtcGoAroundFrom is Some) {
    if (/* full discriminator: step in {FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND} && Circuit-mode && phase=Final */) {
        val r = applyAtcInitiatedGoAround(aircraft, newMission, ...)
        Pair(r, r.mission)
    } else {
        // Discriminator failed — but still CLEAR THE FLAG (defensive — don't let it linger)
        Pair(null, newMission.copy(pendingAtcGoAroundFrom = None))
    }
} else Pair(null, newMission)
```

Both branches clear the flag. Two-layer defense: `handleGoAround` only sets when on-final-eligible (Step 3), AND `pilotDecide` clears on every inspection (here).

`resetForGoAround` does NOT touch the flag — `handleGoAround` is the unique site that sets the flag, and the set sequencing preserves the new value past any `resetForGoAround` call. Document this in `resetForGoAround` KDoc.

### Step 6: Tick B is FREE — reused (R9b)
Post-Tick-A:
- Active step is `MissionStep.GOING_AROUND` (mission-tree rewrite by `processInstruction(Instruction.GoAround)`).
- `phase is PilotPhase.Final` (pinned by Tick A).
- `kinematicRoute is PilotRoute.None` (cleared by Tick A).
- `pendingAtcGoAroundFrom = None` (cleared by Tick A).

The existing `isCircuitTrainedGoAroundTickB` predicate at `Pilot.kt:367-373` matches once the GOING_AROUND REPORTED step completes (via `Report(GoingAround)` transmission) and the next step `FLY_DEPARTURE` becomes active. Same as trained-GA.

**Verify at task time:** the predicate's exact step check. If it requires `step == FLY_DEPARTURE` and that's reached after the GOING_AROUND REPORTED step completes, the predicate fires correctly.

If the predicate's step check is more restrictive than the ATC-reactive flow needs: extend additively — admit the ATC path with one new branch. Do NOT introduce a sibling predicate.

`planCircuitTrainedGoAround` at `Pilot.kt:384-405` builds the GA route. **Zero new route-planning code.**

### Step 7: pilot-side unit tests (R9c)
New file `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotAtcInitiatedGoAroundSpec.kt`. Mirror `PlannedGoAroundSpec.kt` shape from fn-11.1.

**Test phrasing through public seams** (per codex iter 7): `handleGoAround` is private; tests exercise it via `processInstruction(Instruction.GoAround, ...)` which is the public entry point. The Tick A apply produces `PilotOutput.intent`, NOT a mutated `AircraftState` — assertions are on `PilotOutput.intent.route`, `PilotOutput.intent.phase`, and `updatedMission.pendingAtcGoAroundFrom`. Use whatever test API fn-11.1's `PlannedGoAroundSpec.kt` uses for analogous assertions.

**Required pins:**
1. **`handleGoAround` sets the flag with the right step value (exercised via `processInstruction(GoAround)`):**
   - Seed mission with `currentTask.step = FLY_FINAL`.
   - Run `processInstruction(Instruction.GoAround)` → `handleGoAround`.
   - Assert post-call: `mission.pendingAtcGoAroundFrom == Some(MissionStep.FLY_FINAL)`. AND mission-tree rewritten to `CircuitAfterGoAround`. AND `resetForGoAround` markers visible.
2. **Flag NOT set when GA arrives during non-on-final steps:**
   - Seed mission with `currentTask.step = FLY_DOWNWIND` (or any non-on-final step).
   - Run `handleGoAround`.
   - Assert: `mission.pendingAtcGoAroundFrom == None`.
3. **Tick A consumes the flag AND produces intent override:**
   - Seed aircraft with `route = PilotRoute.Airborne(<final-leg>)` + `phase = Final` + post-`handleGoAround` mission (flag set).
   - Run `pilotDecide` (or directly call `applyAtcInitiatedGoAround`).
   - Assert post-Tick-A: `aircraft.route is PilotRoute.None`, `aircraft.phase is PilotPhase.Final` (NOT Climbing), `mission.pendingAtcGoAroundFrom == None` (cleared).
   - Verify mission state from `handleGoAround`'s `resetForGoAround` is preserved (no double-reset).
4. **Tick B GA-route construction:** post-Tick-A, run the next decision tick. Assert `planCircuitTrainedGoAround` fires (route is the GA route, NOT None and NOT the original final route).
5. **Recognition discriminator** — must NOT fire for:
   - Genuinely non-Circuit mode (`mission.navigationMode = Some(NavigationMode.Visual)` or active mission tree is non-Circuit shape). Note: `mission.navigationMode = None` with a circuit-training mission tree (the normal LOWG case) **MUST FIRE** — pin separately as a positive case (e.g. "navigationMode=None + circuit-training tree-shape + on-final flag → fires").
   - Non-Final phase (LandingRoll, Climbing, Vacating)
   - Flag is `None` (no ATC GA arrived)
   - Flag's value is outside `{FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}` (defensive)
6. **Mutual exclusivity with trained-GA:** if both somehow apply (trained-GA's predicate matches AND flag is set), trained-GA's `applyPlannedGoAround` fires first — verify ordering. (Edge case; in normal operation trained-GA's `processInstruction(GoAround)` doesn't run, so the flag stays `None`.)
7. **Future-circuit non-corruption:** if the ATC-GA happens on circuit 1 of a 2-outcome mission, circuit 2's mission state is preserved. Verify `markCompleteInActiveCompound` (from fn-11.1) is used wherever step-marking happens.

### Step 8: regression + verify (R12 partial)
Run `./gradlew :pilot:jvmTest`. New `PilotAtcInitiatedGoAroundSpec` GREEN; existing `PlannedGoAroundSpec` GREEN; existing `SelfInitiatedGoAroundResponseSpec` GREEN. Run full goldens — all five existing GREEN. detekt clean.

## Investigation targets

**Required**:
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:94` — `navigationMode: Option<NavigationMode>` location
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt:30-89` — sealed interface + `derivePilotEvent` (signature does NOT include `previousStep`)
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:117-122` — fork point in `pilotDecide` (where `preStep` is captured)
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:367-405` — `isCircuitTrainedGoAroundTickB` + `planCircuitTrainedGoAround`
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:451-472` — Visual-mode reactive special-case (must NOT collide)
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:714-729` — `applyPlannedGoAround` (sibling/mirror)
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt:689,944-955` — `processInstruction(GoAround)` and `handleGoAround`. **Verify `handleGoAround` calls `mission.resetForGoAround(now)`.**
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:271-293` — `resetForGoAround`
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PlannedGoAroundSpec.kt` — fn-11.1's pilot-side tests

**Optional**:
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:634-665` — `applySelfInitiatedGoAround`

## Key context

- **`pendingAtcGoAroundFrom: Option<MissionStep>`** is a transient signaling state with single-cycle lifetime. Set by `handleGoAround` in cycle N; consumed by `applyAtcInitiatedGoAround` in cycle N+1 (or same cycle if `pilotCognitiveDecide` runs before recognition in `pilotDecide`).
- **Recognition lives in `pilotDecide`**, not `derivePilotEvent`. Same axis as fn-11.1.
- **Effective navigation mode discriminator: `isEffectiveCircuitMode(mission)` helper, NOT `mission.navigationMode.getOrNull()`.** The stored field is often `None` for normal circuit-training missions because `planRoute` derives `Circuit` locally. The helper wraps the same derivation `planRoute` uses (locate at task time and extract).
- **`applyAtcInitiatedGoAround` is intent-only.** Does NOT call `mission.resetForGoAround(now)`.
- **Tick B is fully reused** — no new route-planning code.
- **Mutual exclusivity with trained-GA**: trained-GA's natural flow never sets the flag (no `processInstruction(GoAround)`). Recognition order in `pilotDecide` checks trained-GA first as a defensive measure.

## Acceptance

- [ ] R9a (KDoc): update `PilotEvent.kt`'s file/interface KDoc to document the new leaf. Currently the KDoc says the event channel has one leaf and `derivePilotEvent` returns at most `DecisionAltitudeWithoutClearance`. Add a short note that `AtcGoAroundOnFinal` is recognized in `pilotDecide` (NOT in `derivePilotEvent`) — the recognition site is a separate axis from `derivePilotEvent`'s self-initiated event derivation. Distinguish "self-initiated events derived from aircraft+mission state" (`derivePilotEvent`) from "post-cognitive flag-driven events constructed at decision time" (`pilotDecide` recognition).
- [ ] R9a: `PilotEvent.AtcGoAroundOnFinal(aircraft: AircraftId)` leaf added at `PilotEvent.kt:30-45`. Recognition lives in `pilotDecide` at `Pilot.kt:117-122` area, NOT in `derivePilotEvent`. Predicate reads `mission.pendingAtcGoAroundFrom: Option<MissionStep>` flag (post-cognitive mission). Predicate body: flag is `Some(<step>)` where step is in `{FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}`, AND **active navigation mode is `Circuit` via effective-mode derivation** (same logic `planRoute` uses — `mission.activeRunway + deriveNavigationMode(...)` or `CircuitAfterGoAround` tree shape recognition; do NOT gate on `mission.navigationMode.getOrNull()` alone — it is often `None` for normal circuit-training missions), AND `aircraft.phase is PilotPhase.Final`.
- [ ] R9b (mission state): `PilotMission.pendingAtcGoAroundFrom: Option<MissionStep> = None` field added. Default-`None` preserves existing call sites. **`PilotMission.copy(...)` audit**: Kotlin `copy` preserves omitted fields, so the flag could unintentionally survive through unrelated mission updates (`processInstruction`, `updateAfterTransmission`, route-planning mission returns, `handleLandingClearance`, etc.). Audit every `mission.copy(...)` call site and every helper that returns a new `PilotMission`. For each: decide explicitly whether `pendingAtcGoAroundFrom` should preserve, clear, or be unaffected. The single-cycle lifetime contract requires preserve-by-default with explicit clear-points only in `handleGoAround` (set) and `applyAtcInitiatedGoAround` / `pilotDecide` discriminator-fail (clear). Document the audit findings in the task's `## Done summary`.
- [ ] R9a-pilotEvent-exhaustiveness: adding `PilotEvent.AtcGoAroundOnFinal` widens the sealed type. Audit every exhaustive `when (event: PilotEvent)` site (grep `is PilotEvent` and `when (` over `PilotEvent`-typed bindings) and add explicit no-op (or appropriate handler) arms. NO `else` clauses — totality discipline. Likely sites: trace formatters, telemetry, any audit-trail consumer.
- [ ] R9b (handleGoAround set): `handleGoAround` at `PilotCognitive.kt:944-955` extended to capture `originalStep = mission.currentTask?.step` BEFORE the rewrite and stamp `pendingAtcGoAroundFrom = Some(originalStep)` when the original step is in `{FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}`. Otherwise leaves flag `None`.
- [ ] R9b (apply consume): `applyAtcInitiatedGoAround` function added to `Pilot.kt`, sibling to `applyPlannedGoAround`. **Returns `AtcGoAroundResult(intent: PilotIntent, mission: PilotMission)`** mirroring `PlannedGoAroundResult` shape — produces a `PilotIntent` (NOT an updated `AircraftState`). Tick A intent: `route = PilotRoute.None`, `phase = Final` retained. **Clears the flag**: `mission.copy(pendingAtcGoAroundFrom = None)`. **Does NOT call `mission.resetForGoAround(now)`** (handleGoAround already did). Wired into `pilotDecide` parallel to `applyPlannedGoAround` invocation.
- [ ] R9b (flag-clear on every inspection): `pilotDecide`'s ATC-reactive recognition arm clears `mission.pendingAtcGoAroundFrom` on every inspection regardless of whether the discriminator fires. If discriminator fails (non-Circuit, non-Final, etc.), the flag is still cleared via `mission.copy(pendingAtcGoAroundFrom = None)` and `pilotDecide` falls through to the normal path. Two-layer defense: `handleGoAround` sets only on eligible steps; `pilotDecide` clears on every cycle. Verify via discriminator-fail test pin (R9c).
- [ ] R9b (resetForGoAround interaction): `resetForGoAround` does NOT touch the flag. Document in `resetForGoAround` KDoc that the flag is set by `handleGoAround` and consumed by `pilotDecide`; reset does not interact with it.
- [ ] R9b (Tick B reuse): existing `isCircuitTrainedGoAroundTickB` predicate at `Pilot.kt:367-373` fires for the ATC-reactive path post-Tick-A. `planCircuitTrainedGoAround` at `Pilot.kt:384-405` builds the GA route. **Zero new route-planning code.** If predicate's step check is restrictive, extend additively.
- [ ] R9c: `PilotAtcInitiatedGoAroundSpec.kt` new file. Pins: handleGoAround flag-set behavior across the on-final eligible step set `{FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}` (set when on-final, None otherwise); **dedicated `currentTask.step == LAND` pin** — seed mission with step=LAND (post-`handleLandingClearance` state), `phase = Final`, route airborne-final, run `processInstruction(GoAround)` → `pilotDecide`; assert Tick A fires (route=None, phase=Final), flag cleared, GA route built; Tick A flag-consume + intent override (no double-reset); Tick B GA-route construction (reused planner); recognition discriminator across (mode, phase, flag-value) matrix; **flag-clear-on-discriminator-fail** (seed `pendingAtcGoAroundFrom = Some(...)` + non-Circuit mode; run `pilotDecide`; assert flag is cleared even though apply did not fire); mutual exclusivity with trained-GA; future-circuit non-corruption.
- [ ] R12 (partial): `./gradlew :pilot:jvmTest` GREEN. All five existing goldens (G0/G1/G1-min/G2/G3a-trained) STAY GREEN. detekt clean. New `PilotAtcInitiatedGoAroundSpec` GREEN.
- [ ] No collision with Visual-mode reactive special-case at `Pilot.kt:451-472` — verify via discriminator test.
- [ ] No collision with trained-GA path — verify via mutual-exclusivity test (recognition ordering).
- [ ] `applyAtcInitiatedGoAround` does NOT call `mission.resetForGoAround(now)` — verified by code inspection AND by no-double-reset pin.
- [ ] Recognition lives in `pilotDecide`, not `derivePilotEvent` — verified by code inspection.
- [ ] `pendingAtcGoAroundFrom` field has single-cycle lifetime — set in `handleGoAround`, consumed in `applyAtcInitiatedGoAround`, never lingers.

## Done summary
fn-12.2 ships pilot-side ATC-initiated reactive go-around in Circuit-mode via
`PilotMission.pendingAtcGoAroundFrom: Option<MissionStep>` flag on the mission
state, set by `handleGoAround` BEFORE its tree rewrite and consumed by
`pilotDecide`'s recognition arm via `recognizeAtcInitiatedGoAround` →
`applyAtcInitiatedGoAround` (intent-only Tick A: `route=PilotRoute.None`,
`phase=PilotPhase.Final`). Tick B reuses fn-11.1's `isCircuitTrainedGoAroundTickB`
predicate + `planCircuitTrainedGoAround` planner (zero new route code).
`PilotEvent.AtcGoAroundOnFinal` typed event leaf added (parallel to
`DecisionAltitudeWithoutClearance`); `isEffectiveCircuitMode(mission, world)`
helper reuses `deriveNavigationMode` (option (a)) to handle the normal
`navigationMode=None` LOWG case. GA-path precedence in `pilotDecide`:
trained-GA → ATC-reactive (gated on trained-GA-null) → self-initiated
(unchanged, only when neither prior fired); single-cycle flag-clear invariant
enforced via post-fold unconditional `.copy(pendingAtcGoAroundFrom = None)`.
20 new pins in PilotAtcInitiatedGoAroundSpec; SelfInitiatedGoAroundResponseSpec
GREEN unchanged; all 5 existing goldens GREEN; detekt clean. Three codex
review rounds → SHIP (precedence reorder, dead-event surfacing, ATC-recognition
gating + KDoc refresh).
## Evidence
- Commits: 399fc70, 748a066, 3d9b4ab, 5ecc651
- Tests: ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
- PRs: