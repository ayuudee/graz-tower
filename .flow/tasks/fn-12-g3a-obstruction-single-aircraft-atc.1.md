---
satisfies: [R1, R2, R3a, R3b, R3c, R4, R5, R6, R7, R8, R12]
---

## Description

Foundation pass. Lands the typed `RunwayObstruction` world surface, the per-cycle world expiry pass, the per-controller world-diff producer, the new `ControllerView.worldEvents` field, the controller-side fold + `data object` guard + pre-clearance gate + post-clearance reactive rule (with `Immediate` regression), and the companion obstruction-info transmission via the canonical `deriveCompanionOutputs` carrier path with primitives-only protocol payload.

This task is structurally large but **architecturally narrow** — every surface has a single existing structural mirror. Existing G0/G1/G1-min/G2/G3a-trained sim tests stay GREEN throughout (default-null obstruction; new `ControllerEvent` leaves are no-ops in existing fixtures; `Not(RunwayObstructed)` guard vacuously true).

**Size:** L → split rejected per `feedback_pass_scope.md`. Sequential S tasks touching coupled code → one large pass. Acceptable.

**Files:**
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:151-161` — `Runway`, add `obstruction: RunwayObstruction? = null`
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/RunwayObstruction.kt` (NEW) — `data class RunwayObstruction(val clearsAt: SimTime)`. Imports `protocol.SimTime`. NO sealed class.
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateLoader.kt:148` — Runway constructor preserved (default-null)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/Event.kt:38-91` — add `RunwayObstructionDetected(runway, obstruction)` + `RunwayObstructionCleared(runway)` leaves. NO `AerodromeId` payload. Update `aircraftIdOf` and any other exhaustive `when` with no-op arms.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt` — add `worldEvents: List<ControllerEvent> = emptyList()` field to `ControllerView`
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/BeliefState.kt:113 area` — add `runwayObstructions: Map<RunwayId, RunwayObstruction>` slice
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/Observe.kt:80-103` — add `withRunwayObstructionEvents` fold, mirror `withCircuitIntentEvents`
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/Controller.kt:87-98,707-740` — concat `view.worldEvents`; append fold; extend `deriveCompanionOutputs` with `obstructionInfo` block
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt` — add `data object RunwayObstructed : RuleGuard` mirroring `RunwayPhysicallyClear` (parameterless; derives runway from commitment/context)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt:91-133` — extend `LandingConditions` with `Not(RunwayObstructed)`
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt:385-397 area` — add `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` to **THREE** `stageRules` blocks: `stageRules[AwaitApproach]` (pre-clearance on-final), `stageRules[LandingClearanceIssued]` (post-clearance, pre-readback), `stageRules[AwaitLandedObserved]` (post-readback, pre-touchdown). Covers entire on-final window. NO `fromStages` field.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Action.kt:86-90` — add `obstructionInfo: ObstructionInfo? = null` to `ProposedAction` data class **AND** to the convenience constructor `fun ProposedAction(instruction, sequenceInfo, trafficInfo, obstructionInfo)` (mirror the existing convenience signature; pass through to primary constructor)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Action.kt` — add `data class ObstructionInfo(val runway: RunwayId, val clearsAt: SimTime)` (primitives only — NO `core.world.RunwayObstruction` to avoid protocol-cycle in callers)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Action.kt` — add `ObstructionGoAroundAction : RuleAction` returning `Either<ActionResolutionFailure, ProposedAction>` (typed error path; no `!!`)
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt:1283 area` — add `data class RunwayObstructionInformation(override val target: AircraftId, val runway: RunwayId, val clearsAt: SimTime) : ControllerResponse`. Sibling to `TrafficInformation`. **`override val target` required** — `ControllerResponse` inherits `target: AircraftId` from `ControllerTransmission`. **Primitives only** — protocol cannot import `core.world.RunwayObstruction` (cycle).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt:200` — `processControllerResponse`: add explicit no-op arm for `RunwayObstructionInformation` (pilot has no behavior to take on the obstruction info; the GA reaction is driven by the `Instruction.GoAround` separately)
- All other `when (response: ControllerResponse)` exhaustive sites in protocol/ + pilot/ + controller/ + sim/ — add no-op arms for `RunwayObstructionInformation`. Grep `is TrafficInformation` and `when` over `ControllerResponse` to find them.
- Phraseology / utterance-duration: locate the rendering path (likely `protocol/.../Phraseology.kt` or sibling — verify) and add an arm for `RunwayObstructionInformation` (either render a doctrinal phrase like "RUNWAY OBSTRUCTED" or note the leaf doesn't render an utterance — verify against the existing `TrafficInformation` rendering).
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/.../<sim-cycle file>` — per-cycle world **expiry pass**: pure function nulls `runway.obstruction` where `clearsAt <= now`. Returns updated `SimState`.
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/ControllerWiring.kt` — per-controller world-diff producer; populates `view.worldEvents` post-expiry per cycle.
- `controller/src/commonTest/kotlin/.../BeliefFoldSpec.kt` — total-leaf-coverage test rows for the two new ControllerEvent leaves
- `controller/src/commonTest/kotlin/.../FirewallBeliefWriteTest.kt` — verify single-write site
- Other exhaustive `when (event: ControllerEvent)` sites: add no-op arms

## Approach

### Step 1: typed surface + Runway field + loader migration + Runway-construction audit (R1)
Define `RunwayObstruction(clearsAt: SimTime)` colocated with `WorldModel.kt`. Use `xyz.easiersaid.twr.protocol.SimTime` directly (canonical type at `protocol/.../SimTime.kt:15`; NO `SimInstant`). NO sealed class. Document the **`clearsAt` immutability invariant** in KDoc: once an obstruction is set on a runway, the inner `clearsAt` is immutable for the lifetime of the obstruction; the only allowed transitions are `None → Some(new)` (initial set) and `Some → None` (expiry pass nulls it). The diff producer relies on this invariant to remain edge-only.

Add `obstruction: RunwayObstruction? = null` to `Runway`. Default-null preserves existing call sites at `WorldCandidateLoader.kt:148`.

**Audit every `Runway(` construction site and every `Runway.copy(` usage site.** A simple grep is unreliable for Kotlin multi-line calls. Use ripgrep with case-sensitive matching plus manual review:
```bash
rg "Runway\(" --glob '*.kt'                    # all Runway constructor invocations
rg "\.copy\(" --glob '*.kt' -l | xargs rg -l "Runway"  # files containing both .copy( and Runway, then manual review
```
Rely on **manual audit** as the primary mechanism — Kotlin trailing-default params do NOT force constructor call sites to update (positional sites with explicit defaults pass; named-arg sites silently default the new field to null). The compiler does NOT enforce that audit. The audit grep is the primary check; cross-reference each hit and verify the omission is intentional. Add a smoke test that constructs a Runway with `obstruction = RunwayObstruction(clearsAt = ...)` and verifies it round-trips through the loader/codepath as a secondary integration check.
Kotlin data-class semantics:
- `Runway(...)` constructor — omitted args use the constructor default (`obstruction = null`). New constructor sites that don't pass `obstruction` will null it.
- `runway.copy(...)` — omitted args **preserve the current property value**, NOT the constructor default. Copies don't drop obstruction unless explicitly set to null.

Audit checks each site: constructor calls must explicitly opt in/out of `obstruction`; copy calls inherently preserve it. The risk is concentrated at constructor call sites (only one production site exists at `WorldCandidateLoader.kt:148`). Test fixtures may construct Runways too — audit those.

`core:jvmTest` (`WorldConstructionTest`) passes unchanged.

### Step 2: ControllerEvent leaves + ControllerView.worldEvents + event assembly (R2, R3c)
Add the two new leaves to the `ControllerEvent` sealed interface. Find every `when` on `ControllerEvent` (grep `is ControllerEvent` and `when (` in controller/ + sim/) and add explicit no-op arms.

Add `worldEvents: List<ControllerEvent> = emptyList()` to `ControllerView` at `ControllerTypes.kt`. Default-empty preserves all existing call sites.

In `Controller.kt` near line 87, change event-assembly:
```kotlin
val events: List<ControllerEvent> = view.worldEvents + deriveEventsFromMessages(view.receivedMessages)
```
World-derived events first, so they fold into BeliefState before radio-derived events that might reference the same belief slice. Verify ordering doesn't break existing tests; if it does, document the rationale.

### Step 3: BeliefState slice + fold (R4)
Add slice to `BeliefState.kt` next to `circuitIntent`. Add `withRunwayObstructionEvents` to `Observe.kt` mirroring `withCircuitIntentEvents`:
- Per-leaf `when` with explicit no-op arms
- `Detected → acc + (runway to obstruction)`
- `Cleared → acc - runway`
- Identity-equality short-circuit

Wire into `Controller.kt:97-98` after `withCircuitIntentEvents`. `FirewallBeliefWriteTest` passes. Add `BeliefFoldSpec` row.

### Step 4: Guard + LandingConditions extension (R5, R6)
Add `data object RunwayObstructed` to `controller/.../bdi/Guard.kt`, mirroring `RunwayPhysicallyClear`:
```kotlin
data object RunwayObstructed : RuleGuard {
    override val failureMessage = "Runway is declared obstructed"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        val runway = commitment.runway ?: ctx.beliefs.activeRunway ?: return false
        return ctx.beliefs.runwayObstructions.containsKey(runway)
    }
}
```
**Parameterless `data object`**, NOT a parameterized `RunwayObstructed(runway)`. Derives runway from commitment/context per the existing pattern.

Extend `LandingConditions` at `TowerArrival.kt:91-133`:
```kotlin
val LandingConditions = AllOf(listOf(
    // ...existing terms...
    Not(RunwayObstructed),  // NEW
))
```

Single addition gates `ARR-LAND`, `ARR-LAND-TNG`, `ARR-LAND-REISSUE` transitively.

### Step 5: ProposedAction carrier + ObstructionInfo + RunwayObstructionInformation protocol leaf (R8 part)
Extend `ProposedAction` at `Action.kt:86-90`:
```kotlin
data class ProposedAction(
    val dispatch: Dispatch,
    val sequenceInfo: SequenceInfo? = null,
    val trafficInfo: TrafficInfo? = null,
    val obstructionInfo: ObstructionInfo? = null,  // NEW
)
```

**Also extend the convenience constructor** at `Action.kt` (the secondary constructor `fun ProposedAction(instruction, sequenceInfo, trafficInfo)` that wraps `Dispatch.Direct`):
```kotlin
fun ProposedAction(
    instruction: AtcInstruction,
    sequenceInfo: SequenceInfo? = null,
    trafficInfo: TrafficInfo? = null,
    obstructionInfo: ObstructionInfo? = null,  // NEW — must mirror primary
): ProposedAction = ProposedAction(
    dispatch = Dispatch.Direct(instruction),
    sequenceInfo = sequenceInfo,
    trafficInfo = trafficInfo,
    obstructionInfo = obstructionInfo,  // NEW
)
```

Without widening the convenience constructor, `ObstructionGoAroundAction`'s `ProposedAction(instruction = ..., obstructionInfo = ...)` call will not compile.

Add `data class ObstructionInfo(val runway: RunwayId, val clearsAt: SimTime)` in same module. **Primitives only** — protocol cannot import `core.world.RunwayObstruction` without creating a cycle (core imports protocol).

Add to `protocol/.../Instruction.kt:1283 area`, sibling to `TrafficInformation`:
```kotlin
data class RunwayObstructionInformation(
    override val target: AircraftId,  // override required — ControllerResponse exposes `target` via the sealed interface
    val runway: RunwayId,
    val clearsAt: SimTime,
) : ControllerResponse
```

The `override val target` modifier is required — verify against `TrafficInformation`'s shape (it likely has the same `override val target` declaration).

### Step 6: ControllerResponse exhaustiveness — processControllerResponse + phraseology + utterance-duration (R8)
Find every exhaustive `when` over `ControllerResponse`:
1. `pilot/.../PilotCognitive.kt:200` — `processControllerResponse`. Add no-op arm: pilot has no behavior to take on the obstruction info; the GA instruction (`Instruction.GoAround`) is the actionable input.
2. Phraseology rendering (verify location — likely `protocol/.../Phraseology.kt` or sibling). Either render a doctrinal phrase like `"RUNWAY OBSTRUCTED"` or define the leaf as non-rendering (mirror whatever `TrafficInformation`'s pattern is). The companion transmission must be observable in trace (per epic R10's Layer 1 `Information.RunwayObstruction.time` pin).
3. Utterance-duration calculation (where transmission length-cost is computed for radio queuing). Verify by grepping `is TrafficInformation` in protocol/ + controller/. Add an arm.
4. Protocol exhaustiveness tests (if any exist) — add coverage rows.

Grep tactic:
```bash
grep -rn "is TrafficInformation\|when.*ControllerResponse\|ControllerResponse ->" --include="*.kt" .
```
Each hit requires an arm.

### Step 7: ObstructionGoAroundAction + reactive rule (R7, R8 final)
Add `ObstructionGoAroundAction : RuleAction` returning `Either<ActionResolutionFailure, ProposedAction>`:
```kotlin
data object ObstructionGoAroundAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Either<ActionResolutionFailure, ProposedAction> {
        val runway = commitment.runway ?: ctx.beliefs.activeRunway
            ?: return ActionResolutionFailure("ObstructionGoAroundAction: no runway in commitment or active beliefs").left()
        val obstruction = ctx.beliefs.runwayObstructions[runway]
            ?: return ActionResolutionFailure("ObstructionGoAroundAction: runway $runway not in runwayObstructions").left()
        return ProposedAction(
            instruction = GoAround(target = ac.id),
            obstructionInfo = ObstructionInfo(runway, obstruction.clearsAt),
        ).right()
    }
}
```

Verify the exact `RuleAction` signature against existing actions at `Action.kt:191-194` (`GoAroundAction`); match the `resolve` return type and parameter list.

Add `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` to **three** `stageRules` blocks at `TowerArrival.kt`: `stageRules[AwaitApproach]`, `stageRules[LandingClearanceIssued]`, `stageRules[AwaitLandedObserved]`. Together they cover the entire on-final window: pre-clearance (`AwaitApproach`), post-clearance pre-readback (`LandingClearanceIssued`), post-readback pre-touchdown (`AwaitLandedObserved`). The same `AtcRule` instance is reused across all three stage lists (or use a builder helper) — the rule is identical:

```kotlin
AtcRule(
    id = "ARR-GO-AROUND-RUNWAY-OBSTRUCTED",
    description = "Instruct go-around — runway obstructed during approach",
    regulations = listOf(RegulationDatabase.ICAO4444_7_4_1_4_1, RegulationDatabase.ICAO4444_8_9_6_1_8, RegulationDatabase.CAP413_4_65),
    guard = AllOf(listOf(
        AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))),
        RunwayObstructed,
        Not(ObstructionGoAroundAlreadyIssuedThisAttempt),  // no-refire guard
    )),
    action = ObstructionGoAroundAction,
    nextStage = TowerArrivalStage.AwaitDownwind,  // Immediate advancement — regression at issuance
    urgency = Urgency.SAFETY,
    advancementPolicy = AdvancementPolicy.Immediate,
)
```

**Regression timing semantics:** by `Immediate` advancement, the commitment moves to `AwaitDownwind` in the same tick the rule fires (regardless of which of the three stages it fires from). The existing `GA-POST-CLEAR` interrupt at `TowerArrival.kt:148-156` does NOT fire for this path (its `fromStages` no longer matches by the time `Report(GoingAround)` arrives — stage is already `AwaitDownwind`). Sticky-witness reset (per fn-8.3) is wired to fire on stage transitions; verify it triggers on `Immediate` advancement (the existing `ARR-GO-AROUND-CLEARANCE-ISSUED` rule has the same shape, so the precedent is exercised). **Re-fire prevention is via the `obstructionGoAroundIssuedThisAttempt` witness, NOT stage progression alone** — see R7-no-refire acceptance. Reconciliation can re-advance an aircraft back through eligible stages while obstruction persists; only the witness reliably suppresses re-fire.

### Step 8: deriveCompanionOutputs extension (R8 final)
Extend `deriveCompanionOutputs` at `Controller.kt:707+` with a third companion block, mirroring the existing `trafficInfo → TrafficInformation` block at line 730:
```kotlin
action?.obstructionInfo?.let { info ->
    companions.add(ControllerOutput.Respond(
        target = output.target,
        response = RunwayObstructionInformation(
            target = output.target,
            runway = info.runway,
            clearsAt = info.clearsAt,
        ),
        // urgency, trace per existing pattern — mirror trafficInfo block
    ))
}
```

The new rule produces `protocol.GoAround` (the dispatched instruction) AND a companion `RunwayObstructionInformation` transmission in the same controller-output cycle. Per ICAO §7.4.1.4.1(c).

### Step 9: World expiry pass (R3a)
Add a per-cycle pure function in `sim/src/commonMain/.../`:
```kotlin
fun expireRunwayObstructions(state: SimState, now: SimTime): SimState {
    // For each aerodrome, for each runway: if obstruction != null && obstruction.clearsAt <= now,
    // produce a copy with obstruction = null. Return updated SimState.
    // Pure — no side effects, no PRNG.
}
```
Wire into the sim cycle so it runs once per cycle, **before** the world-diff producer.

### Step 10: Per-controller world-diff producer (R3b)
Extend `sim/src/commonMain/.../ControllerWiring.kt`. For each controller view being constructed. **Document the runway-set invariant**: runway membership in `aerodrome.runways` is static across a sim run. Diff iterates over `current` keys only; if a future scenario allows runways to be added/removed at runtime, the key set must change to `prior.keys + current.keys` and the diff must handle removal + addition explicitly. For v1, the static-runway-set assumption holds — document it inline.

```kotlin
fun runwayObstructionEvents(
    aerodromeId: AerodromeId,
    prior: Map<RunwayId, RunwayObstruction?>,  // prior-cycle snapshot per runway (current-runway-set keys only)
    current: World,                             // post-expiry current world
): List<ControllerEvent> {
    val currentRunways = current.aerodromes[aerodromeId]?.runways ?: emptyMap()
    // Invariant: runway-set membership is static across a sim run.
    // If a future scenario violates this, change the iteration key-set to (prior.keys + current.keys)
    // and emit Cleared events for removed runways that were obstructed.
    return currentRunways.flatMap { (id, runway) ->
        val priorObs = prior[id]
        val currentObs = runway.obstruction
        when {
            priorObs == null && currentObs != null -> listOf(ControllerEvent.RunwayObstructionDetected(id, currentObs))
            priorObs != null && currentObs == null -> listOf(ControllerEvent.RunwayObstructionCleared(id))
            priorObs != null && currentObs != null -> {
                // Per Decision #4 invariant: clearsAt is immutable for an obstruction lifetime — should never see Some(old) → Some(new).
                // Defensive check: if the values differ, throw — invariant violation upstream.
                check(priorObs == currentObs) {
                    "Invariant violation: RunwayObstruction.clearsAt mutated mid-lifetime on runway $id (prior=$priorObs, current=$currentObs). " +
                    "Test fixtures must use one-shot authorship; world-state mutations must null first before re-setting."
                }  // `check` (IllegalStateException) — state-invariant violation, not arg validation
                emptyList()
            }
            else -> emptyList()  // None → None
        }
    }
}
```

Document the per-controller scoping invariant inline:
> Events in `view.worldEvents` reference only `RunwayId`s within `view.aerodromeId`'s runway set. Cross-aerodrome routing is not supported in v1; promote `RunwayObstructionDetected/Cleared` to carry `AerodromeId` if a future scenario requires cross-aerodrome event delivery.

Each controller view's `worldEvents` populated by this producer per cycle.

### Step 11: smoke verification — existing goldens stay GREEN (R12 partial)
Run `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt`. Expected: all five existing goldens (G0/G1/G1-min/G2/G3a-trained) GREEN. detekt baseline unchanged.

If any golden fails: real regression. Diagnose at root cause. **Do not skip-list. Do not soften.**

## Investigation targets

**Required**:
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/SimTime.kt:15` — canonical instant type
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt:412,1283` — `ControllerResponse` sealed interface; `TrafficInformation` shape
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:151-161` — `Runway`
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/Event.kt:38-91` — `ControllerEvent`
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/Observe.kt:80-103` — `withCircuitIntentEvents` (canonical mirror)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/Controller.kt:87-98,707-740` — event assembly + `deriveCompanionOutputs`
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt` — `ControllerView`
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt:91-133,148-156,385-397` — `LandingConditions`, `GA-POST-CLEAR`, `ARR-GO-AROUND-CLEARANCE-ISSUED`
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Procedure.kt:27-55` — `AtcRule` (NO `fromStages`)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Action.kt:86-90,191-194` — `ProposedAction`, `GoAroundAction`
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/SeparationEngine.kt:264-327` — companion `TrafficInformation` pattern
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt` — `RunwayPhysicallyClear` (sibling pattern; verify exact `RuleGuard` interface)
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt:200` — `processControllerResponse` (must accept new leaf)
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/ControllerWiring.kt:147,187-203` — projection pattern

**Optional**:
- `controller/src/commonTest/kotlin/.../BeliefFoldSpec.kt` — total-coverage test pattern
- `controller/src/commonTest/kotlin/.../FirewallBeliefWriteTest.kt` — single-write architectural test

## Key context

- **`SimTime` is canonical** (value class wrapping `Long` millis).
- **`AtcRule` has NO `fromStages` field.** Stage membership via `stageRules` map.
- **`ProposedAction` carries info via data fields** read by `deriveCompanionOutputs`.
- **`ControllerResponse` exhaustiveness** triggers updates in `processControllerResponse`, phraseology rendering, utterance-duration calc.
- **Protocol-cycle constraint**: `protocol` cannot import `core.world.RunwayObstruction`. Companion transmission carries primitives only (`runway: RunwayId, clearsAt: SimTime`).
- **`RunwayObstructed` is `data object`**, not parameterized. Derives runway from commitment/context.
- **Regression at issuance** (Immediate advancement); `GA-POST-CLEAR` interrupt does NOT fire for this path.
- **Action returns `Either<ActionResolutionFailure, ProposedAction>`** — typed-error path; no `!!`.
- **`ControllerView.aerodromeId`** scopes the world-diff producer per controller.
- **Existing `protocol.GoAround` MUST NOT be extended.** All new info via companion path.

## Acceptance

- [ ] R1: `RunwayObstruction(clearsAt: SimTime)` data class added at `core/.../world/`. Imports `protocol.SimTime`. NO sealed class. KDoc documents `clearsAt` immutability invariant. `Runway.obstruction: RunwayObstruction? = null` field added. **All `Runway(...)` constructor sites manually audited** (per codex iter 12 — compiler does NOT catch positional or named sites with new trailing-default param; they silently default to null). For each site, decide explicitly whether `obstruction = null` is correct (most production constructions; world loader; test fixtures that don't model obstructions) or `obstruction = RunwayObstruction(clearsAt = ...)` is needed (test fixtures that author obstructions). **`copy(...)` sites**: preserve current `obstruction` value unless explicitly set — Kotlin data-class semantics; no audit needed. Production loader call site at `WorldCandidateLoader.kt:148` is the only known production constructor site. `core:allTests` passes unchanged.
- [ ] R2: `ControllerEvent.RunwayObstructionDetected(runway: RunwayId, obstruction: RunwayObstruction)` and `RunwayObstructionCleared(runway: RunwayId)` leaves added at `Event.kt:38-91`. NO `AerodromeId` payload. All exhaustive `when` sites updated with explicit no-op arms. **Replace any existing `else` arms in `ControllerEvent` `when` sites with explicit per-leaf arms** — current `Controller.kt` has `contactedAircraft()` (or sibling) using `else -> null` which would silently swallow new leaves. Per the no-catch-all discipline, every `when` over `ControllerEvent` must list every leaf explicitly. Audit by grep `when (.*ControllerEvent)` and `is ControllerEvent` and replace `else` arms. `aircraftIdOf` returns `null` for the new leaves (or whatever convention existing system-event leaves use). `BeliefFoldSpec` updated.
- [ ] R3a: World expiry pass added in `sim/src/commonMain/.../`. Pure function. Walks `state.world.aerodromes[*].runways[*].obstruction`; nulls any where `clearsAt <= now`. Returns updated `SimState`. Wired into the sim cycle BEFORE the diff producer.
- [ ] R3b: Per-controller world-diff producer added at `sim/.../ControllerWiring.kt`. Per controller view: iterates `state.world.aerodromes[view.aerodromeId].runways`; compares prior vs current; emits `Detected`/`Cleared` into `view.worldEvents`. Edge-only. Per-controller scoping invariant documented inline. **Prior-snapshot threading API explicit**: `buildControllerView` takes `(priorState: SimState, currentState: SimState)` parameter pair (or equivalently the sim cycle keeps `priorState` accessible at view-build time). Whichever API is chosen, document it in the view-construction signature and verify the cycle plumbing flows the prior snapshot. If existing `buildControllerView` signature is `(state, controllerId)`, widen to `(priorState, currentState, controllerId)` — single touchpoint.
- [ ] R3c: `ControllerView.worldEvents: List<ControllerEvent> = emptyList()` field added **as the final constructor parameter** (append after `atis` or whatever the current last field is — verify by reading `ControllerTypes.kt`). Append-at-end avoids positional-arg call-site churn. Event-assembly at `Controller.kt:87` concats `view.worldEvents + deriveEventsFromMessages(view.receivedMessages)`.
- [ ] R3-observability: world-derived `ControllerEvent`s (`RunwayObstructionDetected`, `RunwayObstructionCleared`) are recorded in the sim trace used by golden tests. Verify the existing trace harness captures `view.worldEvents` (or the equivalent post-fold belief snapshots showing `runwayObstructions` slice transitions). If not, extend the trace harness as part of this task. Without observability, Task .3's pins on `RunwayObstructionDetected.decisionTime` / `RunwayObstructionCleared.decisionTime` cannot be implemented.
- [ ] R4: `BeliefState.runwayObstructions` slice + `withRunwayObstructionEvents` fold + pipeline wiring at `Controller.kt:97-98`. `FirewallBeliefWriteTest` passes unchanged.
- [ ] R5: `data object RunwayObstructed : RuleGuard` added at `controller/.../bdi/Guard.kt`. Parameterless. Derives runway from `commitment.runway ?: ctx.beliefs.activeRunway`. Reads `ctx.beliefs.runwayObstructions.containsKey(runway)`.
- [ ] R6: `LandingConditions` at `TowerArrival.kt:91-133` extended with `Not(RunwayObstructed)`.
- [ ] R7: `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` AtcRule added to **THREE** `stageRules` blocks: `stageRules[AwaitApproach]` (pre-clearance on-final coverage), `stageRules[LandingClearanceIssued]` (post-clearance, pre-readback), `stageRules[AwaitLandedObserved]` (post-readback, pre-touchdown). Together they cover the entire on-final window. **Priority within stage lists**: insert the obstruction rule BEFORE broader generic GA rules (`ARR-GO-AROUND`, `ARR-CONTINUE`, `ARR-GO-AROUND-CLEARANCE-ISSUED`) so that when the runway is BOTH obstructed AND physically-not-clear (or access-not-granted), the obstruction-specific rule wins and the obstruction-info companion is emitted (per ICAO §7.4.1.4.1(c) — reason-on-radio is mandatory). Without priority placement, a broader rule with `Not(RunwayPhysicallyClear)` could fire first and emit a generic GA without the obstruction reason. NO `fromStages` field. Guard `AllOf(AnyOf(OnApproach, OnCircuitLeg(LegName.FINAL)), RunwayObstructed, Not(ObstructionGoAroundAlreadyIssuedThisAttempt))` — the no-refire witness guard is part of the rule's own guard expression. Action `ObstructionGoAroundAction`. `nextStage = AwaitDownwind`. `urgency = SAFETY`. `advancementPolicy = Immediate`. **Stage progression alone is INSUFFICIENT for re-fire prevention** — reconciliation may re-advance the aircraft back through eligible stages while obstruction persists. The `obstructionGoAroundIssuedThisAttempt` witness (R7-no-refire) is the actual no-refire mechanism. **Regulation refs explicit, not placeholders**: `regulations = listOf(RegulationDatabase.ICAO4444_7_4_1_4_1, RegulationDatabase.ICAO4444_8_9_6_1_8, RegulationDatabase.CAP413_4_65)` — verify entries exist in `RegulationDatabase` and add missing entries if needed.
- [ ] R7-stage-coverage: controller-level unit tests for each of the three stages. For each `from-stage ∈ {AwaitApproach, LandingClearanceIssued, AwaitLandedObserved}`, seed a controller state with that stage active + runway obstruction in belief; assert the rule fires; assert commitment regresses to `AwaitDownwind`; assert obstruction-info companion emitted. The sim test in fn-12.3 covers the post-clearance case end-to-end; these unit tests cover the other two stages plus structurally isolate each stage's rule firing.
- [ ] R7-no-refire: the rule must NOT re-fire while the obstruction persists, even if reconciliation/other rules re-advance the aircraft back toward `AwaitApproach` mid-GA-execution. With a 60s obstruction, repeated `GoAround` + companion transmissions on every rule-evaluation cycle would be a real bug.
  - **Architecture clarification (per codex iter 14)**: `advanceCommittedStages` mutates the existing commitment's stage; it does NOT replace the commitment. A naive "commitment-lifetime" witness would suppress legitimate later obstruction GA on a recovery approach if the existing commitment persists.
  - **Suppression mechanism — approach-attempt-scoped witness**:
    - **Set (committed-output path only)**: set `obstructionGoAroundIssuedThisAttempt: Boolean` on the commitment **only after arbitration + certification have accepted the `GoAround` output**, NOT when the rule produces a candidate action. The witness update belongs in the committed-output path (alongside `advanceCommittedStages`), not in `executeProcedure`'s candidate-emit step. If the rule's candidate loses arbitration or fails certification, the witness MUST NOT be set — otherwise the controller would suppress the real obstruction GA on the next cycle. Implementation note: extend `OperatorResult` (or sibling) with a witness-update effect that's applied conditionally on commit, OR handle this rule's id specifically in the post-`advanceCommittedStages` mutation step.
    - **Suppression guard**: rule's guard becomes `AllOf(<existing>, Not(ObstructionGoAroundAlreadyIssuedThisAttempt))`.
    - **Re-arm**: clear the witness on **the next downwind report** (`Report(Downwind)` arrival from the same aircraft on the same commitment) OR on **commitment replacement** (whichever comes first).
    - **Concrete re-arm site**: extend `reconcileTowerArrival` (`controller/.../observe/CommitmentReconciliation.kt` or sibling — verify location at task time) — when processing `PositionReported` events for the aircraft and one is `ReportEvent.Downwind`, clear `obstructionGoAroundIssuedThisAttempt` on the commitment. This is the canonical fold site where commitment-state mutates on incoming pilot reports. The reconciliation also already updates `observedReportsDuringCommitment` and similar witnesses on the same fold path — add this new clear alongside.
    - **Test for re-arm timing**: assert the witness reset happens BEFORE the next approach's obstruction guard evaluates (i.e., the reset is in the same event-processing tick as the `Report(Downwind)` fold, not deferred to a later cycle).
  - **Mechanism for setting witness from rule** (per codex iter 14 — current `AtcRule` only supports `nextStage`, not arbitrary commitment mutation): extend `RuleAction` (or sibling action-result type) with an optional `commitmentWitnessUpdate: (Commitment) -> Commitment` field, OR handle this specific rule by id in `advanceCommittedStages` (a small dispatch on rule id when applying the stage transition). Pick at task time. Document the chosen mechanism inline.
  - **Commitment field audit**: adding `obstructionGoAroundIssuedThisAttempt` to `Commitment` requires auditing every `Commitment.copy(...)` and constructor site (per fn-8.3 sticky-witness pattern's discipline). Default-`false` preserves existing call sites; copies preserve unless explicitly set.
  - **Test pins** (controller-level, in fn-12.1): (1) seed obstruction + post-first-fire state; advance ticks while obstruction persists and aircraft on-approach (BEFORE downwind report); assert exactly ONE `GoAround` emission across the window. (2) Re-arm pin: post-first-fire, simulate the aircraft progressing through GA + downwind report; seed obstruction again; assert the rule fires (witness was re-armed on downwind report). (3) Commitment-replacement re-arm: when commitment is replaced (next circuit), seed obstruction; assert the rule fires.
- [ ] R7-supersession: when `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` fires from `LandingClearanceIssued`, the controller has issued a `ClearedToLand` (or `ClearedTouchAndGo`) coordination whose readback may still arrive. **Explicit supersession contract on TWO surfaces**: the coordinations ledger AND the active/issued-clearance state.
  - **Coordinations ledger** (concrete contract): the supersession relation `(GoAround) supersedes (ClearedToLand, ClearedTouchAndGo)` must be encoded in the table consumed by `applySupersessionCleanup(stageAdvancedBeliefs, proceduralInstructions)` at `Controller.kt`. The cleanup path runs BEFORE `validatedReadbackResponses`, so a stale landing readback arriving after GA finds no matching pending coordination and is dropped. **Coordination-state coverage**: across every active coordination state that `processReadback` can later accept — `Issued`, `Querying`, `Reissued`, `LostCommsDeclared`.
  - **Issued clearances on BeliefState** (concrete contract): the controller's `BeliefState.issuedClearances` (the mutable belief-state slice; `ControllerView.activeClearances` is the read-only view-projection of this slice) must drop landing-class clearances for the aircraft on `GoAround` emission. The same `applySupersessionCleanup` path that handles coordinations also mutates `issuedClearances` — verify by reading the function body. If it's a separate mutation call site, extend it. The pin: after the rule fires, `belief.issuedClearances[aircraftId].filter { it is ClearedToLand || it is ClearedTouchAndGo }` is empty.
  - **Investigation step (mandatory)**: open `Controller.kt`, locate `applySupersessionCleanup`'s implementation. Identify (a) the supersession-relation table, (b) the coordinations-ledger mutation path, (c) the `issuedClearances` mutation path. Verify the `GoAround` → `ClearedToLand`/`ClearedTouchAndGo` entries exist (the existing `ARR-GO-AROUND-CLEARANCE-ISSUED` rule emits the same `GoAround` instruction, so likely encoded). If absent on either ledger or issued-clearances path, add.
  - **Add controller-level regression tests in fn-12.1**: (1) for each coordination state in `{Issued, Querying, Reissued, LostCommsDeclared}`, seed `LandingClearanceIssued` commitment + obstruction; fire; deliver stale `ClearedToLand` readback; assert commitment stays in `AwaitDownwind`. (2) Active-clearance pin: after rule fires, no active `ClearedToLand` / `ClearedTouchAndGo` clearance remains for the aircraft in `issuedClearances` / `activeClearances`. (3) Subsequent rules gated on `NoActiveInstruction` evaluate correctly post-GA (no stale clearance blocks them).
- [ ] R8-companion-trace: `RunwayObstructionInformation` companion's `DecisionTrace` carries the same regulation refs as the rule (`ICAO4444_7_4_1_4_1`, `ICAO4444_8_9_6_1_8`, `CAP413_4_65`) plus a stable rule ID (e.g. `"OBSTRUCTION-INFO"`) and description (e.g. `"Inform aircraft of runway obstruction per ICAO 4444 §7.4.1.4.1(c)"`). The companion's trace must be machine-readable to the same standard as the GA instruction's trace.
- [ ] R8-companion-onfrequency: `RunwayObstructionInformation` is rendered AND scheduled as an on-frequency controller transmission, identical to how `TrafficInformation` is rendered+scheduled. Verify by code-trace at task time that `ControllerOutput.Respond` of this type lands in the same radio-scheduling path (transmission queue, frequency targeting, `txStart`/`txEnd` timestamps) as `TrafficInformation`. The sim trace must record `RunwayObstructionInformation`'s transmission start/end so Task .3 can pin radio order against `GoAround`'s transmission.
- [ ] R8 (carrier + protocol):
  - `obstructionInfo: ObstructionInfo? = null` field on `ProposedAction`.
  - `data class ObstructionInfo(val runway: RunwayId, val clearsAt: SimTime)` added (primitives only).
  - `data class RunwayObstructionInformation(override val target: AircraftId, val runway: RunwayId, val clearsAt: SimTime) : ControllerResponse` added at `protocol/.../Instruction.kt`. **`override val target` required** — inherited from `ControllerTransmission`.
  - `ObstructionGoAroundAction` returns `Either<ActionResolutionFailure, ProposedAction>` — no `!!`.
  - `deriveCompanionOutputs` extended with third block emitting `RunwayObstructionInformation` companion.
- [ ] R8 (exhaustiveness):
  - `processControllerResponse` at `PilotCognitive.kt:200` has explicit no-op arm for `RunwayObstructionInformation`.
  - All other `when (response: ControllerResponse)` sites in protocol/ / pilot/ / controller/ / sim/ have arms.
  - Phraseology rendering site (verify location; likely `protocol/.../Phraseology.kt` or sibling) has an arm — either renders `"RUNWAY OBSTRUCTED"` doctrinal phrase or follows whatever convention `TrafficInformation` uses.
  - Utterance-duration calc site (where transmission length-cost is computed) has an arm.
- [ ] R12 (partial): `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` exits 0. All five existing goldens GREEN. detekt baseline unchanged.
- [ ] No `controllerDecide` reads from `state.world` — verify by grep + build-graph.
- [ ] No `else` clauses in any new exhaustive `when` site — totality discipline.
- [ ] World-diff producer's per-controller scoping invariant is documented inline.
- [ ] No protocol → core import — verified by build (would error). `RunwayObstructionInformation` carries primitives only.

## Done summary
Foundation pass for G3a-obstruction reactive ATC go-around (fn-12 epic). Lands the typed `RunwayObstruction` world surface, per-cycle world expiry pass, per-controller world-diff producer, `ControllerView.worldEvents`, controller fold + `RunwayObstructed` data-object guard, pre-clearance landing gate, three-stage `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` reactive rule with `Immediate` regression and approach-attempt-scoped no-refire witness, supersession across coordinations + `issuedClearances`, primitives-only `RunwayObstructionInformation` companion via `deriveCompanionOutputs`, full controller-level regression test coverage (3-stage + no-refire + supersession + companion-trace), and trace-side `runwayObstructionTransitions` extractor for fn-12.3 observability. All five existing G0/G1/G1-min/G2/G3a-trained goldens stay GREEN. Codex impl-review SHIP after one round (NEEDS_WORK → SHIP via additional R1 round-trip smoke + R3-observability extractor + R7 commitment-replacement / stale-readback pins).
## Evidence
- Commits: 76ad84dffd12979683f480d9a806430cc937f814, d4136d375fca18cc8a64ffdd4515b8797854bbf3
- Tests: ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
- PRs: