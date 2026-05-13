# G3a-obstruction — single-aircraft ATC-mandated VFR go-around on world-modeled runway obstruction

## Overview

Single AI aircraft at LOWG flies a circuit-training mission with `outcomes = listOf(CircuitOutcome.FullStop)` (a single planned circuit, no `GoAround` outcome — this is **reactive ATC-mandated** GA, not pilot-trained). On the planned circuit's final, post-`ClearedToLand`, the world authors `runway.obstruction = RunwayObstruction(clearsAt = T_obs + 60s)`. The sim's per-cycle expiry pass nulls `runway.obstruction` when `now >= clearsAt`. The sim's per-cycle world-diff producer compares prior-vs-current `runway.obstruction` per controller-scoped runway and feeds the resulting `ControllerEvent.RunwayObstructionDetected/Cleared` into that controller's `view.worldEvents`. Controller folds into `BeliefState.runwayObstructions`. Reactive ATC rule fires (aircraft on final + runway in `runwayObstructions` → issue `protocol.GoAround` + companion `RunwayObstructionInformation` transmission). Pilot reacts via new Circuit-mode reactive-GA path (flag-on-mission recognition + Tick A/B). `handleGoAround` replaces the active circuit with `CircuitAfterGoAround = [goAroundTask(), circuitTask()]` — the recovery circuit is provided automatically by the tree rewrite. Aircraft GAs, climbs runway heading, re-enters downwind via the recovery `circuitTask`. Obstruction expires; `Cleared` event lands; controller's belief drops the runway. Recovery circuit's final issues a normal `ClearedToLand` (pre-clearance gate ungated), aircraft lands.

**Note on outcomes count.** The single `FullStop` outcome is correct: `handleGoAround` provides the recovery circuit by replacing the active outcome with `[goAroundTask(), circuitTask()]`. Two `FullStop` outcomes would produce three landing attempts (GA + recovery-FullStop + remaining-FullStop), which is not the intended scenario.

**The novelty:** this is the first scenario in the codebase to wire a **world-state-derived sensing channel** (modality-agnostic — could be tower-visual, pilot radio, surface sensor, ground inspection) feeding `ControllerEvent` events. Foundational for future surface-incursion / FOD / wildlife / leader-not-vacated scenarios.

## Boundaries / non-goals

- **Out: opaque-obstruction kind discriminator.** v1 ships `RunwayObstruction(clearsAt: SimTime)` with no kind variant. Disciplined YAGNI. Filed as `D-PASS-g3a-obstruction-kind-variants`.
- **Out: `CONTINUE APPROACH` doctrinal middle state** (CAP 413 §4.55 / §4.56 / ICAO Doc 4444 §12.3.4.16(d)). v1 collapses to a two-state ladder (clear / GA). Filed as `D-PASS-g3a-obstruction-continue-approach`.
- **Out: belief-vs-truth divergence.** v1: `clearsAt` is a single value the world holds; controller's belief equals world truth at all times (modulo one-tick event-fold latency, which is the canonical pure-fold pattern). Filed as `D-PASS-g3a-obstruction-belief-divergence`.
- **Out: aircraft-in-circuit orbit/hold.** v1 reactive scope is on-final GA only. Orbit/hold primitive does not exist in VFR yet. Filed as `D-PASS-g3a-obstruction-orbit-hold`.
- **Out: pilot-radio sensing modality.** Channel is deliberately modality-agnostic; sim is the bundled "tower senses" surface. Filed as `D-PASS-g3a-obstruction-pilot-report`.
- **Out: typed obstruction kinds.** Each variant (Vehicle, Aircraft, Debris, Wildlife, SurfaceContamination) requires its own authoring shape. Filed as a per-variant suffix of the canonical D-PASS-g3a-obstruction-kind-variants ID.
- **Out: leader-not-vacated as a typed obstruction.** Couples to multi-aircraft sequencing. Filed as `D-PASS-g3a-obstruction-leader-not-vacated`.
- **Out: explicit instructor agent surface.** Filed in fn-11 as `D-PASS-instructor-agent-surface`.
- (Pre-clearance on-final reactive case is now IN scope — see Decision #7. The `AwaitApproach` rule covers it.)

## Strategy Alignment

Active tracks served by this plan:

- **Runtime simulator** — adds the first world-state-derived sensing channel into `ControllerEvent`. Foundational for the next live verticals (surface-incursion, FOD, leader-not-vacated, wildlife). Closes the third reactive-GA path (after self-initiated and pilot-trained), completing the GA coverage trio at the sim-test level.

## Decision context

### 1. Type shape: opaque `RunwayObstruction(clearsAt: SimTime)` — no kind discriminator (high confidence)

The data class carries only `clearsAt: SimTime` (deadline timestamp). The codebase canonical instant type is `protocol.SimTime` (a value class wrapping `Long` millis at `protocol/.../SimTime.kt:15`); no `SimInstant` exists. Rationale per practice-scout: TTL-style countdowns couple to tick frequency and break under fast-forward; deadline timestamps are tick-rate-independent and monotonic.

Kind discriminator deliberately omitted (per user interview): *"I think just obstructed, but probably also put TTL or some such on it. I think it's better that it's not necessarily known what it is, until we have a way to use that information."* YAGNI: variants land when there's behavioural reason to discriminate.

```kotlin
data class RunwayObstruction(val clearsAt: SimTime)
```

No `sealed class` ceremony per Effective Kotlin Item 39. **Lives in `core.world` module** (next to `Runway`); the protocol-side companion transmission carries primitive fields only (`runway: RunwayId, clearsAt: SimTime`) — see Decision #9.

### 2. World state location: rich-domain on `Runway` (high confidence — locked in interview)

`Runway.obstruction: RunwayObstruction?` on the entity, NOT `World.runwayObstructions: Map<RunwayId, RunwayObstruction>` at world root.

Per `project_rich_world_domain.md` (saved 2026-05-10 from interview): time-varying state lives on the entity it concerns. Sets precedent for `Runway.surfaceContamination`, `Runway.lighting`, etc. Single production constructor: `migration/.../WorldCandidateLoader.kt:148`. `Runway` data class at `core/.../world/WorldModel.kt:151-161` adds one optional field.

### 3. Channel-agnostic event surface — per-controller scoping (high confidence)

Sim emits `ControllerEvent.RunwayObstructionDetected(runway: RunwayId, obstruction: RunwayObstruction)` and `RunwayObstructionCleared(runway: RunwayId)` events. Modality (tower-visual / pilot radio / surface sensor / ground inspection) deliberately unbound in v1.

**Aerodrome scoping.** `RunwayId` at `protocol/.../Instruction.kt:16` is a local-scope value class. Per-controller filtering is the discipline:

- Each controller has `ControllerView.aerodromeId` (existing).
- The sim's per-cycle world-diff producer iterates `state.world.aerodromes[view.aerodromeId].runways` *per controller view* and emits events into that view's worldEvents stream.
- Each `view.worldEvents` is intrinsically scoped to that controller's aerodrome — no AerodromeId payload qualification on the event leaf.
- Document the invariant inline in the producer.
- `BeliefState.runwayObstructions: Map<RunwayId, RunwayObstruction>` is per-controller (BeliefState has been per-controller since fn-1 era), so the slice is intrinsically aerodrome-scoped.

This is the **first world-state-derived `ControllerEvent` channel** in the codebase. New `ControllerView.worldEvents: List<ControllerEvent>` field (default empty), populated by sim wiring. Controller assembles events as `view.worldEvents + deriveEventsFromMessages(view.receivedMessages)`.

**Edge-only emission discipline.** Events fire on monotonic transitions (`None → Some` / `Some → None`). Persistence emits no event.

### 4. World expiry mechanism + diff snapshot threading (high confidence — added per codex iteration 1; threading clarified per iteration 4)

Sim has a per-cycle **expiry pass** that walks `state.world.aerodromes[*].runways[*].obstruction` and nulls any `obstruction` whose `clearsAt <= now`. Runs **before** the world-diff producer in the same cycle.

**Sequence per cycle (the prior snapshot threads through the cycle's `SimState`-prev / `SimState`-current pair):**

1. Sim cycle entry. Inputs: `priorState: SimState` (post-prior-cycle state), `now: SimTime`.
2. **Test-fixture mutation** (if any) — test fixture's `onTick` callback mutates `state.world` (e.g., authors a runway obstruction at the right tick). Produces `mutatedState`.
3. **World expiry pass:** for each `runway.obstruction` where `clearsAt <= now`, mutate `runway.obstruction = null`. Pure function: `expireRunwayObstructions(mutatedState, now) → expiredState`.
4. **Diff producer (per-controller):** for each controller view being built, computes:
   - `priorObstructions: Map<RunwayId, RunwayObstruction?>` from `priorState.world.aerodromes[view.aerodromeId].runways`
   - `currentObstructions` from `expiredState.world.aerodromes[view.aerodromeId].runways`
   - Edge-only diff (`None → Some`, `Some → None`) → emits events into `view.worldEvents`.
   - **Persistence emits no event** (`Some → Some` even with same `clearsAt` — covered by Decision #4 invariant: `clearsAt` is immutable for an obstruction lifetime).
5. Controller views constructed with populated `worldEvents`.
6. Controllers fold → BeliefState; rules evaluate; outputs emitted.

**The prior snapshot lives in the cycle's `priorState: SimState`.** The sim cycle keeps a reference to the prior cycle's state — verify against existing sim cycle plumbing at task time. If the cycle does NOT already retain prior state, threading through requires either (a) per-controller prior-obstruction-snapshot field on `SimState`, or (b) an explicit `(priorState, currentState)` pair input to `buildControllerView`. Pick whichever is structurally cleaner; either works.

**`clearsAt` is immutable for an obstruction lifetime (invariant).** Once `runway.obstruction = Some(RunwayObstruction(clearsAt = T))` is set, no code path mutates the inner `clearsAt`. The only allowed mutations are `None → Some(new)` (initial set) and `Some → None` (expiry pass nulls it). This keeps the edge-only diff complete; no `Some(old) → Some(new clearsAt)` case exists. Document the invariant at `RunwayObstruction`'s KDoc and at the world-mutation API (test fixture authoring helpers).

The expiry-then-diff ordering ensures the `Cleared` event fires the cycle the obstruction expires.

### 5. BeliefState fold + reactive rule pipeline (high confidence)

Add `BeliefState.runwayObstructions: Map<RunwayId, RunwayObstruction>` slice (sibling to `circuitIntent` at `BeliefState.kt:113`). Single-write fold `withRunwayObstructionEvents(events)` at `Observe.kt`, mirrors `withCircuitIntentEvents` at lines 80-103. Wired into the pipeline at `Controller.kt:97-98`.

`FirewallBeliefWriteTest` enforces single-write; `BeliefFoldSpec` enforces total event-leaf coverage.

### 6. Estimate fidelity: estimated == actual in v1 (locked in interview)

World holds one `clearsAt: SimTime`. Detected event payload's `obstruction.clearsAt` equals world's value. `Cleared` fires when world's expiry pass nulls the obstruction. One-tick latency between expiry and `Cleared` landing in BeliefState is the canonical pure-fold pattern, not divergence.

### 7. Reactive scope: on-final GA only — pre-clear gate + post-clear rule with immediate stage regression (high confidence — refined per codex iteration 2)

v1 ships the **post-clearance reactive rule** AND the **on-final-pre-clearance reactive rule**, plus the **pre-clearance landing gate**. v1 does NOT ship in-circuit orbit/hold (deferred `D-PASS-g3a-obstruction-orbit-hold`).

**Three reactive sites for the obstruction GA rule** (per `feedback_no_corners.md` — incomplete state where aircraft continues to threshold with obstruction unhandled is unacceptable):

1. **`stageRules[AwaitApproach]`** — covers on-final aircraft pre-clearance. If obstruction appears while aircraft is on final but before `ClearedToLand` is issued, this rule fires.
2. **`stageRules[LandingClearanceIssued]`** — covers post-clearance, pre-readback.
3. **`stageRules[AwaitLandedObserved]`** — covers post-readback, pre-touchdown.

Each rule instance is identical (same guard, action, `nextStage`); only the `stageRules` map placement differs. The three placements together cover the entire on-final window from "aircraft on approach" through to "aircraft about to touch down."

The `Not(RunwayObstructed)` term in `LandingConditions` is the **defensive layer** — even with the `AwaitApproach`-stage rule, the gate prevents `ClearedToLand` from being issued onto an obstructed runway during the cycle the rule fires. Belt-and-suspenders.

**Pre-clearance gate (one-line addition, defensive):** extend `LandingConditions` at `controller/.../procedure/TowerArrival.kt:91-133` with a new `Not(RunwayObstructed)` term. Prevents `ClearedToLand` from being issued onto an obstructed runway. `LandingConditions` is reused by `ARR-LAND` (line 334), `ARR-LAND-TNG` (line 352), `ARR-LAND-REISSUE` (line 422+) — all three transitively gate.

**Reactive rule with three-stage placement:** new `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` `AtcRule` added to THREE `stageRules` blocks: `stageRules[AwaitApproach]` (pre-clearance on-final), `stageRules[LandingClearanceIssued]` (post-clearance, pre-readback), `stageRules[AwaitLandedObserved]` (post-readback, pre-touchdown). Together they cover the entire on-final window. NO `fromStages` field — `AtcRule` at `controller/.../bdi/Procedure.kt:27-55` has none; stage membership comes from `stageRules` placement. Without coverage on all three stages, an obstruction in some window would slip through unhandled.

```kotlin
AtcRule(
    id = "ARR-GO-AROUND-RUNWAY-OBSTRUCTED",
    description = "Instruct go-around — runway obstructed during approach",
    regulations = listOf(RegulationDatabase.ICAO4444_7_4_1_4_1, RegulationDatabase.ICAO4444_8_9_6_1_8, RegulationDatabase.CAP413_4_65),  // exact constants — Task .1 R7 acceptance: these must exist in RegulationDatabase by end of fn-12.1; verify and add missing entries
    guard = AllOf(listOf(
        AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))),
        RunwayObstructed,
        Not(ObstructionGoAroundAlreadyIssuedThisAttempt),  // no-refire guard; witness is set ONLY after committed/certified output
    )),
    action = ObstructionGoAroundAction,  // sibling action — populates obstructionInfo per Decision #9
    nextStage = TowerArrivalStage.AwaitDownwind,
    urgency = Urgency.SAFETY,
    advancementPolicy = AdvancementPolicy.Immediate,
)
```

**Regression timing — at GoAround issuance, not at `Report(GoingAround)` receipt.** Per `AdvancementPolicy.Immediate`, the rule's `nextStage = AwaitDownwind` advances the commitment in the same tick the rule fires. By the time the pilot's `Report(GoingAround)` arrives, the stage is already `AwaitDownwind`, so the existing `GA-POST-CLEAR` `ProcedureInterrupt` at `TowerArrival.kt:148-156` does NOT re-fire (its `fromStages` is `LandingClearanceIssued`). For ATC-issued obstruction GA, **the rule's immediate advancement IS the regression**. The `GA-POST-CLEAR` interrupt only covers self-initiated GA cases (no controller rule fired). Once the rule fires from either `LandingClearanceIssued` or `AwaitLandedObserved`, the commitment moves to `AwaitDownwind` so the rule cannot re-fire for the same commitment.

**Pending landing-class coordination supersession.** When the obstruction GA fires from `LandingClearanceIssued`, the controller has issued a `ClearedToLand` (or `ClearedTouchAndGo`) instruction whose readback may still arrive after the regression. Without explicit supersession, `acceptReadback(...)` could apply the coordination's `readbackAdvancesToStage = AwaitLandedObserved` and move the commitment back out of `AwaitDownwind` — undoing the GA regression. Task .1 must add explicit supersession cleanup: when `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` fires, abandon any pending landing-class coordination for the same aircraft (mark superseded / drop from coordinations ledger) and ensure subsequent stale landing readbacks do NOT advance the commitment. Test pin (Task .3): "no landing-class pending coordination after obstruction GA" plus a stale-readback-doesn't-regress-stage regression check.

The existing `ARR-GO-AROUND-CLEARANCE-ISSUED` rule at `TowerArrival.kt:385-397` has the same `Immediate + nextStage = AwaitDownwind` shape — precedent and sticky-witness reset machinery (per fn-8.3) are already exercised on `Immediate` advancement in that path.

**Test pin update (Task .3 Layer 2):** regression time IS the GoAround issuance time. The pin shape: "exactly one stage transition `LandingClearanceIssued → AwaitDownwind` at the GoAround issuance tick." Sticky-witness reset (`touchedDownDuringCommitment`, `pilotReadyDuringCommitment`, `observedReportsDuringCommitment`) verified at the same tick.

**`RunwayObstructed` guard atom — `data object`, not parameterized.** Mirrors `RunwayPhysicallyClear` at `controller/.../bdi/Guard.kt` (verified shape: `data object RunwayPhysicallyClear : RuleGuard` with `evaluate(ac, commitment, ctx)` deriving runway from `commitment.runway ?: ctx.beliefs.activeRunway`):

```kotlin
data object RunwayObstructed : RuleGuard {
    override val failureMessage = "Runway is declared obstructed"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        val runway = commitment.runway ?: ctx.beliefs.activeRunway ?: return false
        return ctx.beliefs.runwayObstructions.containsKey(runway)
    }
}
```

`Map.containsKey(runway)` is the correct check (the slice value is a single nullable `RunwayObstruction` — Map-membership is the existence check). `RunwayObstructed` is *distinct from* `RunwayPhysicallyClear` (which reads `runwayBeliefs[runway].status` for *physical occupancy by aircraft*). Document the distinction in `TowerArrival.kt` KDoc.

### 8. Pilot-side reactive ATC-GA via mission-state flag (high confidence — refined per codex iteration 2)

Context-scout verified: existing pilot machinery does NOT cover `Instruction.GoAround` arriving while a Circuit-mode aircraft is on FLY_FINAL / REPORT_FINAL / AWAIT_LANDING_CLEARANCE. Aircraft falls through to the normal airborne-step branch and keeps flying its old final-leg route. **Bug today** (latent — no scenario exercises this path).

The naive "preStep + currentStep" recognition predicate in `pilotDecide` does **not** work: `processInstruction(GoAround)` runs in `pilotCognitiveDecide` and rewrites the mission tree before the next `pilotDecide` cycle captures `preStep`. By that next cycle, `preStep` is already `GOING_AROUND`. Codex iteration 2 verified this by trace.

**Solution: `pendingAtcGoAroundFrom: Option<MissionStep>` flag in mission state.** `handleGoAround` records the pre-rewrite step as a flag in the new mission shape; the next `pilotDecide` cycle reads-and-clears the flag and applies Tick A intent override. Robust regardless of cycle ordering.

- **Mission state field:** add `pendingAtcGoAroundFrom: Option<MissionStep> = None` to `PilotMission` data class. Set inside `handleGoAround` at `PilotCognitive.kt:944-955` to `Some(originalStep)` where `originalStep = mission.currentTask?.step` BEFORE the rewrite. The set happens in the same call that rewrites the tree.
- **Recognition lives in `pilotDecide`** at `Pilot.kt:117-122` area, mirroring fn-11.1's factoring. Predicate reads from the flag (post-cognitive mission state):
  - `mission.pendingAtcGoAroundFrom is Some` AND
  - `mission.pendingAtcGoAroundFrom.getOrNull() ∈ {FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}` (defensive — matches the steps where the flag is meaningful; **`LAND` is included** because `handleLandingClearance` marks `AWAIT_LANDING_CLEARANCE` complete after `ClearedToLand`, so by the time a post-clearance obstruction GA arrives, `currentTask.step` is `LAND`, not `AWAIT_LANDING_CLEARANCE`) AND
  - the active navigation mode is `Circuit` — but **NOT via `mission.navigationMode.getOrNull()` alone**. Per codex iteration 7: `createMission(...)` defaults `navigationMode = None` and `planRoute(...)` derives `NavigationMode.Circuit` locally from `mission.activeRunway + world` via `deriveNavigationMode(...)`. The recognition helper signature must therefore be `isEffectiveCircuitMode(mission: PilotMission, world: AviationWorld): Boolean` (the helper takes `world` because the underlying `deriveNavigationMode` does). Alternatively use a tree-shape discriminator: walk `mission.activeTask` and recognize `CircuitAfterGoAround` (the post-`handleGoAround` shape) as Circuit-equivalent. Pick ONE at task time and use it consistently across `planRoute` extraction, the recognition predicate, and tests. AND
  - `aircraft.phase is PilotPhase.Final`
- **Tick A apply** (`applyAtcInitiatedGoAround`): returns `AtcGoAroundResult(intent: PilotIntent, mission: PilotMission)` mirroring fn-11.1's `PlannedGoAroundResult` shape. The intent carries `route = PilotRoute.None`, `phase = PilotPhase.Final` (retained). The mission has `pendingAtcGoAroundFrom = None` (flag cleared). **Does NOT call `mission.resetForGoAround(now)`** — `handleGoAround` already did.
- **Flag lifetime — single-cycle, cleared on every `pilotDecide` inspection.** The flag must NOT linger across cycles. Two-layer defense:
  - (a) `handleGoAround` sets the flag ONLY when `originalStep ∈ {FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}` (otherwise leaves `None`). LAND is included because `handleLandingClearance` advances `AWAIT_LANDING_CLEARANCE → LAND` after `ClearedToLand`. This is the eligibility-precheck.
  - (b) The recognition arm in `pilotDecide` clears the flag **on every cycle that inspects it**, regardless of whether the discriminator fires. If the flag is `Some` but discriminator fails (e.g. non-Circuit mode, non-Final phase), `pilotDecide` clears the flag and falls through to the normal path. This prevents the flag lingering and incorrectly firing later (e.g. if the aircraft transitions to phase=Final mid-circuit later through some other path).
  - **`resetForGoAround` interaction:** the `resetForGoAround` function (called by `handleGoAround` at PilotCognitive.kt) does NOT touch the flag — `handleGoAround` is the unique site that sets the flag, and the set happens after the reset call (or in a sequence that preserves the new flag value). Document the reset-vs-flag interaction in `resetForGoAround` KDoc explicitly.
- **`PilotEvent.AtcGoAroundOnFinal(override val aircraft: AircraftId)` leaf** added to `PilotEvent.kt:30-45`. The `override` modifier is required — `PilotEvent` sealed leaves expose `aircraft: AircraftId` via the sealed interface contract (verify against existing leaves like `DecisionAltitudeWithoutClearance`). Constructed at the recognition site in `pilotDecide` from the flag's value.
- **Tick B is FREE — reused.** Existing `isCircuitTrainedGoAroundTickB` predicate at `Pilot.kt:367-373` matches once Tick A clears the route and pins phase. `planCircuitTrainedGoAround` at `Pilot.kt:384-405` builds the GA route via `buildGoAroundRoute(...)`. **Zero new route-planning code.** If the predicate's step check is more restrictive than ATC-reactive needs (verify at task time), extend additively — do NOT introduce a sibling predicate.
- **Discriminator from trained-GA:** trained-GA's recognition uses the natural primitive transition `preStep == FLY_FINAL_TO_SHORT_FINAL && currentStep == GOING_AROUND` captured within `pilotDecide`. ATC-reactive recognition reads the `pendingAtcGoAroundFrom` flag. Trained-GA never sets the flag (no `processInstruction(GoAround)` happens in trained-GA's natural flow — the trained mission tree authors GA via outcomes list). Mutually exclusive.

**Why not move route-invalidation into `processInstruction` directly?** Codex offered this. Rejected because `processInstruction` returns `PilotMission` (not aircraft state), so route invalidation would require either (a) extending the return shape to include intent overrides — significant cross-cutting change — or (b) calling intent-mutation from inside cognitive layer, breaking the cognitive/intent ownership split. The flag-on-mission approach localizes the change to one new field + one set site + one read site.

### 9. Reason-on-radio: companion transmission via `obstructionInfo` carrier — protocol primitives only (high confidence — refined per codex iteration 2)

Per ICAO Doc 4444 §7.4.1.4.1(c) — "in all cases inform the aircraft of the runway incursion or obstruction" — and §8.9.6.1.8 — "in all such cases, the reason for the instruction or the advice should be given to the pilot" — the reason is **MUST**, not optional.

`protocol.GoAround` at `Instruction.kt:605-609` carries `(target, level?, heading?)` — no reason field. **Do NOT extend `GoAround`** (would touch every GA call site).

**Carrier shape.** `deriveCompanionOutputs` at `controller/.../Controller.kt:707` is the canonical companion-emission point. Currently reads `action.sequenceInfo` and `action.trafficInfo` from `ProposedAction` (at `controller/.../bdi/Action.kt:86-90`). **Add a third field:**

```kotlin
data class ProposedAction(
    val dispatch: Dispatch,
    val sequenceInfo: SequenceInfo? = null,
    val trafficInfo: TrafficInfo? = null,
    val obstructionInfo: ObstructionInfo? = null,  // NEW
)
```

`data class ObstructionInfo(val runway: RunwayId, val clearsAt: SimTime)` — colocate with `ProposedAction` in `controller/.../bdi/`. **Carries primitives only**, NOT `core.world.RunwayObstruction` — see protocol-cycle resolution below.

**Protocol-cycle resolution.** Codex iteration 2 caught a critical: `protocol` cannot import `core` (would create a cycle since `core` imports `protocol`). The protocol-side response transmission must carry primitives only.

```kotlin
// In protocol module (sibling to TrafficInformation at protocol/.../Instruction.kt:1283)
data class RunwayObstructionInformation(
    override val target: AircraftId,
    val runway: RunwayId,
    val clearsAt: SimTime,
) : ControllerResponse
```

The `override val target: AircraftId` modifier is required — `ControllerResponse` exposes `target` via the sealed interface contract (verify against `TrafficInformation`'s shape).

`ControllerResponse` is the sealed interface at `protocol/.../Instruction.kt:412`. Adding a new leaf requires updating exhaustive consumers (see R8 acceptance — `processControllerResponse` at `pilot/.../PilotCognitive.kt:200`, phraseology rendering, utterance-duration calc).

**Wiring.** `ObstructionGoAroundAction` populates `ProposedAction.obstructionInfo` when constructed for the new rule. The action returns `Either<ActionResolutionFailure, ProposedAction>` (per project typed-error style — codex iteration 2 minor finding):
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

`deriveCompanionOutputs` at `Controller.kt:707+` extended with a third companion block reading `action.obstructionInfo` and emitting a `ControllerOutput.Respond` with `RunwayObstructionInformation(target, runway, clearsAt)`, mirroring the existing `trafficInfo → TrafficInformation` block at `Controller.kt:730`.

This reuses the established companion-output pattern. The new rule produces a `ProposedAction` with both `dispatch = GoAround(...)` and `obstructionInfo = ObstructionInfo(runway, clearsAt)`; `deriveCompanionOutputs` does the rest.

### 10. Test trigger: world-only authorship (locked — `feedback_world_only_test_triggers.md`)

The sim test author authors `state.world.aerodromes[lowg].copy(runways = ... obstruction = RunwayObstruction(clearsAt = ...))` at the right sim tick (when aircraft is on circuit-1's final). The world's expiry pass nulls it when `clearsAt <= now`; sim's per-cycle diff producer turns the world-state transitions into `ControllerEvent`s; controller folds; reactive rule fires; pilot reacts.

**Forbidden:** direct injection of `ControllerEvent.RunwayObstructionDetected`, manual mutation of `BeliefState.runwayObstructions`, or any path that bypasses the world → expiry → diff → event → fold → rule pipeline.

## Acceptance

- **R1:** `RunwayObstruction(clearsAt: SimTime)` data class added in `core/src/commonMain/.../world/`. Imports `protocol.SimTime`. NO sealed class. Document `clearsAt` immutability invariant in KDoc (Decision #4). Field `Runway.obstruction: RunwayObstruction? = null` added at `WorldModel.kt:151-161`. Production constructor site at `migration/.../WorldCandidateLoader.kt:148` compiles unchanged (default-null). **All `Runway(` construction sites audited** (grep'd) — each constructor call must explicitly handle the new field (omitted args default to null per Kotlin data-class semantics). Note: `Runway.copy(...)` calls preserve the current `obstruction` value when omitted (Kotlin data-class semantics), so copy sites don't need explicit handling unless they intentionally reset. Risk concentrated at constructor sites. `WorldConstructionTest` passes unchanged.
- **R2:** New `ControllerEvent.RunwayObstructionDetected(runway: RunwayId, obstruction: RunwayObstruction)` and `RunwayObstructionCleared(runway: RunwayId)` leaves added at `controller/.../observe/Event.kt:38-91`. NO `AerodromeId` payload — scoping is per-controller (Decision #3). All exhaustive `when` sites on `ControllerEvent` updated with explicit no-op arms. `BeliefFoldSpec` updated.
- **R3a:** Per-cycle world expiry pass added in `sim/src/commonMain/.../`. Pure function. Walks `state.world.aerodromes[*].runways[*].obstruction`; nulls any where `clearsAt <= now`. Returns updated `SimState`. Wired into the sim cycle BEFORE the diff producer.
- **R3b:** Per-controller world-diff producer added at `sim/.../ControllerWiring.kt`. For each controller view: iterates `state.world.aerodromes[view.aerodromeId].runways`; compares prior vs current; emits `Detected`/`Cleared` into `view.worldEvents`. Edge-only. Per-controller scoping invariant documented inline.
- **R3c:** `ControllerView.worldEvents: List<ControllerEvent> = emptyList()` field added. Event-assembly at `Controller.kt:87` concats `view.worldEvents + deriveEventsFromMessages(view.receivedMessages)`.
- **R4:** `BeliefState.runwayObstructions: Map<RunwayId, RunwayObstruction>` slice added. `withRunwayObstructionEvents(events)` fold added at `Observe.kt`, single-write. Wired into pipeline at `Controller.kt:97-98`. `FirewallBeliefWriteTest` passes unchanged.
- **R5:** `RunwayObstructed` guard atom at `controller/.../bdi/Guard.kt`, **`data object` (not parameterized)**, mirrors `RunwayPhysicallyClear`. Derives runway from `commitment.runway ?: ctx.beliefs.activeRunway`. Reads via `ctx.beliefs.runwayObstructions.containsKey(runway)`.
- **R6:** Pre-clearance gate: `LandingConditions` at `TowerArrival.kt:91-133` extended with `Not(RunwayObstructed)` term.
- **R7:** Reactive rule with three-stage placement: new `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` `AtcRule` added to **THREE** `stageRules` blocks: `stageRules[AwaitApproach]`, `stageRules[LandingClearanceIssued]`, `stageRules[AwaitLandedObserved]`. Covers the entire on-final window (pre-clearance, post-clearance pre-readback, post-readback pre-touchdown). NO `fromStages` field. Guard `AllOf(AnyOf(OnApproach, OnCircuitLeg(LegName.FINAL)), RunwayObstructed, Not(ObstructionGoAroundAlreadyIssuedThisAttempt))` — the no-refire witness guard is part of the rule's own guard expression. Action: `ObstructionGoAroundAction` populating `obstructionInfo`. `nextStage = AwaitDownwind`. `urgency = SAFETY`. `advancementPolicy = Immediate`. **No-refire suppression**: approach-attempt-scoped witness `obstructionGoAroundIssuedThisAttempt: Boolean` on `Commitment`, set when rule fires, re-armed on next `Report(Downwind)` arrival or commitment replacement. **Regression at issuance via `Immediate` advancement; `GA-POST-CLEAR` interrupt does NOT fire for this path**. Stage-progression alone is insufficient to prevent re-fire — the witness is the actual no-refire mechanism since reconciliation may re-advance the aircraft back through eligible stages.
- **R8:** Companion obstruction-info transmission via established `deriveCompanionOutputs` pattern.
  - `obstructionInfo: ObstructionInfo? = null` field added to `ProposedAction` at `controller/.../bdi/Action.kt:86-90`.
  - `data class ObstructionInfo(val runway: RunwayId, val clearsAt: SimTime)` added — primitives only, NO `core.world.RunwayObstruction` reference.
  - `data class RunwayObstructionInformation(override val target: AircraftId, val runway: RunwayId, val clearsAt: SimTime) : ControllerResponse` added at `protocol/.../Instruction.kt`, sibling to `TrafficInformation` at line 1283. **`override val target` is required** — `ControllerResponse` inherits `target: AircraftId` from `ControllerTransmission`.
  - `deriveCompanionOutputs` extended with third block reading `action.obstructionInfo` and emitting `ControllerOutput.Respond(target, RunwayObstructionInformation(...))`, mirroring `trafficInfo → TrafficInformation` at `Controller.kt:730`.
  - `ObstructionGoAroundAction` returns `Either<ActionResolutionFailure, ProposedAction>` (typed error path — no `!!`).
  - **Exhaustiveness updates** for the new `ControllerResponse` leaf:
    - `processControllerResponse` at `pilot/.../PilotCognitive.kt:200` — add explicit no-op arm (or defined response — pilot has no behavior to take on the obstruction info; the GA reaction is driven by the `Instruction.GoAround` separately).
    - Phraseology rendering / utterance-duration calculation — wherever `ControllerResponse` is rendered to RT phraseology or utterance-time-cost is computed, add an arm. Grep for sealed-when sites on `ControllerResponse`.
    - Protocol exhaustiveness tests — add coverage rows.
- **R9a:** `PilotEvent.AtcGoAroundOnFinal(aircraft: AircraftId)` leaf added at `PilotEvent.kt:30-45`. Recognition lives in `pilotDecide`, NOT in `derivePilotEvent`. Recognition uses **`mission.pendingAtcGoAroundFrom: Option<MissionStep>` flag** (set in `handleGoAround` before tree rewrite). Predicate: flag is `Some(<on-final step>)` AND **active navigation mode is `Circuit`** via effective-mode derivation (same logic `planRoute` uses — e.g. `mission.activeRunway + deriveNavigationMode(...)` — OR recognize the post-`handleGoAround` `CircuitAfterGoAround` tree shape; do NOT gate on `mission.navigationMode.getOrNull()` alone, which may be `None` for normal circuit-training missions) AND `aircraft.phase is PilotPhase.Final`.
- **R9b:** `PilotMission.pendingAtcGoAroundFrom: Option<MissionStep> = None` field added. `handleGoAround` at `PilotCognitive.kt:944-955` extended to capture `originalStep = mission.currentTask?.step` and set `pendingAtcGoAroundFrom = Some(originalStep)` when the original step is in `{FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}` (otherwise leave `None`). **LAND is in the eligible set** because `handleLandingClearance` marks `AWAIT_LANDING_CLEARANCE` complete after `ClearedToLand`; by the time a post-clearance obstruction GA arrives, `currentTask.step` is `LAND`, not `AWAIT_LANDING_CLEARANCE`. `applyAtcInitiatedGoAround(...)` function added to `Pilot.kt` sibling to `applyPlannedGoAround`. **Returns `AtcGoAroundResult(intent: PilotIntent, mission: PilotMission)`** mirroring `PlannedGoAroundResult` shape — produces a `PilotIntent` with `route = PilotRoute.None`, `phase = PilotPhase.Final`. NOT an updated `AircraftState`. Mission has `pendingAtcGoAroundFrom = None` (cleared). **Does NOT call `mission.resetForGoAround(now)`**. **Flag clears on every `pilotDecide` inspection** — even when the discriminator fails (non-Circuit, non-Final, etc.), `pilotDecide` clears the flag and falls through. Two-layer defense: handleGoAround sets only on eligible step; pilotDecide clears on every inspection. Tick B reuses existing predicate + planner. `resetForGoAround` does NOT touch the flag (verify and document in KDoc).
- **R9c:** Pilot-side unit tests `PilotAtcInitiatedGoAroundSpec.kt`. Pins: `handleGoAround` sets the flag with the right step value; Tick A consumes the flag (clears it) AND produces intent override; Tick B GA-route construction; recognition discriminator (mode/phase/flag-value matrix); mutual exclusivity with trained-GA (trained-GA never sets the flag); future-circuit non-corruption.
- **R10:** Sim test `G3aRunwayObstructionTest.kt`. Single-aircraft LOWG, `outcomes = listOf(CircuitOutcome.FullStop)`. World authors `runway.obstruction = RunwayObstruction(clearsAt = T_obs + 60s)` at sim time `T_obs > T_ClearedToLand`. Three-layer pin pattern; **companion-transmission pin uses same controller decision/output cycle and serialized radio order `GoAround.txStart < RunwayObstructionInformation.txStart` — NOT one-tick spacing or same `txStart`** (utterance duration may exceed one tick). Vacate-coordination closure pin per fn-8.3. Time band ±15%.
  - Layer 1 pins use **separated timestamps** — controller decision-cycle time for stage-regression pins; transmission-record-start time for radio-order pins. Pin shapes: regression-vs-decision-time same tick; transmission ordering `GoAround.txStart < RunwayObstructionInformation.txStart` (same controller decision/output cycle, serialized on same frequency by `applyControllerOutputs`). **Do NOT pin one-tick spacing or same `txStart`** — utterance duration may exceed one tick.
  - Layer 2 pins regression at GoAround **decision-cycle** time (NOT transmission-start time and NOT `Report(GoingAround)` time, because regression is `Immediate` per Decision #7).
  Vacate-coordination closure pin per fn-8.3. Time band ±15%.
- **R11:** Cross-reference doc updates per docs-gap-scout findings (see fn-12.3 task spec).
- **R12:** `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt` exits 0 (`:allTests` for protocol + core to cover any non-JVM KMP targets that may exist; `jvmTest` for sim + pilot + controller which are JVM-only at present — confirm KMP target set at task time and adjust). All six golden tests (G0/G1/G1-min/G2/G3a-trained/G3a-obstruction) GREEN. detekt baseline unchanged.

## Strategy drift flagged for review

_(none — plan aligns with Runtime simulator track.)_

## Quick commands

```bash
./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
./gradlew :sim:jvmTest --tests "xyz.easiersaid.twr.sim.G3aRunwayObstructionTest"
./gradlew :pilot:jvmTest --tests "xyz.easiersaid.twr.pilot.PilotAtcInitiatedGoAroundSpec"
```

## Approach

### Three-task split (mirrors fn-11)

1. **Task .1 — Foundation.** typed `RunwayObstruction` surface + world expiry pass + per-controller world-diff producer + `ControllerView.worldEvents` field + controller fold + `RunwayObstructed` guard + pre-clearance gate + post-clearance reactive rule + `obstructionInfo` carrier + `RunwayObstructionInformation` protocol leaf + `processControllerResponse`/phraseology/utterance-duration exhaustiveness arms + companion emission. Existing G0-G3a goldens stay green.
2. **Task .2 — Pilot-side reactive ATC-GA via mission-state flag.** `PilotMission.pendingAtcGoAroundFrom` field + `handleGoAround` flag-set + recognition in `pilotDecide` + `applyAtcInitiatedGoAround` intent-only Tick A (clears flag) + pilot-side unit tests. Surfaces and fixes the latent Circuit-mode reactive-GA bug.
3. **Task .3 — Sim test + cross-references + doc updates.** End-to-end `G3aRunwayObstructionTest` with three-layer pins; Layer 2 pins regression at GoAround issuance. Cross-ref docstring updates. Closes the epic.

### Reuse points (file:line refs)

| Surface | Reuse | New code |
|---------|-------|----------|
| Runway data class | `core/.../WorldModel.kt:151-161` | Add `obstruction: RunwayObstruction? = null` |
| Production loader | `migration/.../WorldCandidateLoader.kt:148` | Default-null preserves call site |
| ControllerEvent surface | `controller/.../observe/Event.kt:38-91` | Add 2 leaves (no AerodromeId payload) |
| ControllerView | `controller/.../ControllerTypes.kt` | Add `worldEvents: List<ControllerEvent> = emptyList()` |
| BeliefState fold pattern | `Observe.kt:80-103` `withCircuitIntentEvents` | Mirror as `withRunwayObstructionEvents` |
| Controller pipeline | `Controller.kt:87-98` | Concat `view.worldEvents`; append new fold |
| Sim-side projection | `sim/.../ControllerWiring.kt:147,187-203` | World-diff producer (per-controller) |
| World expiry | NEW | Per-cycle pure mutation `runway.obstruction = null when clearsAt <= now` |
| LandingConditions | `TowerArrival.kt:91-133` | Add `Not(RunwayObstructed)` |
| AtcRule pattern | `TowerArrival.kt:385-397` `ARR-GO-AROUND-CLEARANCE-ISSUED` | New rule in `stageRules[LandingClearanceIssued]` |
| Action carrier | `controller/.../bdi/Action.kt:86-90` `ProposedAction` | Add `obstructionInfo: ObstructionInfo?` field |
| Companion emission | `controller/.../Controller.kt:707-740` `deriveCompanionOutputs` | Third companion block reading `action.obstructionInfo` |
| Companion transmission protocol | `protocol/.../Instruction.kt:1283` `TrafficInformation` (sibling shape) | `RunwayObstructionInformation(target, runway, clearsAt)` — primitives only |
| Action shape | `controller/.../bdi/Action.kt:191-194` `GoAroundAction` | Sibling `ObstructionGoAroundAction` returning `Either<ActionResolutionFailure, ProposedAction>` |
| GA-POST-CLEAR interrupt | `TowerArrival.kt:148-156` | NOT fired for this path (Immediate advancement → already AwaitDownwind by Report(GoingAround) time) |
| Pilot Tick A pattern | `Pilot.kt:714-729` `applyPlannedGoAround` | Mirror as `applyAtcInitiatedGoAround` (intent-only — does NOT call `resetForGoAround`) |
| Pilot Tick B planner | `Pilot.kt:367-373` predicate + `Pilot.kt:384-405` `planCircuitTrainedGoAround` | Reused as-is — zero new route-planning code |
| Pilot recognition | `pilotDecide` at `Pilot.kt:117-122` (trained-GA fork) | New ATC-reactive arm reading `mission.pendingAtcGoAroundFrom` flag |
| Mission state flag | `PilotMission.kt` (data class — verify line) | Add `pendingAtcGoAroundFrom: Option<MissionStep> = None` field |
| handleGoAround | `pilot/.../PilotCognitive.kt:944-955` | Extend to capture+stamp `pendingAtcGoAroundFrom` |
| processControllerResponse | `pilot/.../PilotCognitive.kt:200` | Add no-op arm for `RunwayObstructionInformation` |
| Phraseology / utterance-duration | (verify sites — grep on `is ControllerResponse` / `when (.*ControllerResponse)` in protocol/) | Add arms for new leaf |
| markComplete scoping | `pilot/.../PilotCognitive.kt` `markCompleteInActiveCompound` (fn-11.1) | Reused — prevents future-circuit corruption |
| Sim test harness | `sim/.../G3aPilotTrainedGoAroundTest.kt` (fn-11.2) | Mirror structure + 3-layer pin pattern |

## Test notes

The sim test (Task .3) follows the **three-layer pin pattern** from fn-11.2:

- **Layer 1 (causal partial-order)** — observable times, with **separated decision-cycle vs transmission-start timestamps**:

  **Decision-cycle pins** (controller decision/output cycle time):
  ```
  RunwayObstructionDetected.decisionTime
      <= GoAround_decision.time                                // rule fires in cycle that sees RunwayObstructed=true
      == Stage_regression(LandingClearanceIssued|AwaitLandedObserved → AwaitDownwind).time  // same tick (Immediate)
  RunwayObstructionCleared.decisionTime
      < ClearedToLand(c2).decisionTime                         // pre-clearance gate ungates
  ```

  **Radio-transmission pins** (transmission-start time, after controller latency + queuing):
  ```
  GoAround.txStart
      < RunwayObstructionInformation.txStart                   // serialized on same frequency by applyControllerOutputs (queued companion, strict <)
      < Report(GoingAround).txStart                            // pilot reads back later (radio delivery + reaction)
      < Report(RunwayVacated).txStart                          // recovery circuit lands + vacates
  ```

  These are NOT identical times — `applyControllerOutputs` schedules transmissions after controller latency, while stage regression happens in controller state at decision time. The pin shapes use distinct trace-extraction helpers for decisionTime vs txStart.
- **Layer 2 (sticky-witness regression)** — exactly one stage transition `<from-stage> → AwaitDownwind` at the **GoAround decision-cycle time** (NOT transmission-start time, NOT `Report(GoingAround)` time — Decision #7). `<from-stage>` is `LandingClearanceIssued` or `AwaitLandedObserved` depending on whether obstruction appeared pre-readback or post-readback. Post-regression: `touchedDownDuringCommitment`, `pilotReadyDuringCommitment`, `observedReportsDuringCommitment` all reset on the original commitment. `GA-POST-CLEAR` interrupt does NOT fire (its `fromStages` is no longer matched).
- **Layer 3 (kinematic non-event):** no `LandingRoll` or `Vacating` phase in the aircraft phase trace **before** `Report(GoingAround)` — proves the aircraft did NOT touch down on circuit 1.

**Vacate-coordination closure pin** (per fn-8.3): after circuit 2 vacates, no leftover ledger entries.

**Time band ±15%** on observed wall.

## Review considerations

### FP / type-safety axis
- Sealed-type discipline: explicit no-op arms on every existing exhaustive `when` over `ControllerEvent` and `ControllerResponse`.
- `withRunwayObstructionEvents` lists every `ControllerEvent` leaf with explicit handler.
- Pure-fold ordering: world-diff producer is pure (snapshot prev vs current → emit events).
- Typed-error: `ObstructionGoAroundAction` returns `Either<ActionResolutionFailure, ProposedAction>` — no `!!`.
- Per-controller scoping invariant: documented in the world-diff producer.
- Cognitive/intent ownership split: `handleGoAround` (cognitive) sets the flag; `applyAtcInitiatedGoAround` (intent) consumes the flag and produces route+phase override.

### Test architecture axis
- Three-layer pin pattern. Layer 2 pins regression at GoAround issuance.
- Pilot-side unit tests with full discriminator matrix.
- World-only test triggers.
- Time band ±15%.
- All five existing goldens stay GREEN.

### Impact axis
- New `Runway.obstruction` field read by world-diff producer (one new call site).
- New `BeliefState.runwayObstructions` slice read by `RunwayObstructed` guard (one new call site).
- New `ControllerResponse` leaf forces exhaustiveness updates in `processControllerResponse`, phraseology, utterance-duration. Bounded surface.
- Migration cost: 1 production constructor site + ~2 test constructor sites. Default-null preserves all existing call sites.
- Reversibility: if v2 needs kind variants, `RunwayObstruction` becomes a sealed class with current shape as one variant. Existing call sites compile unchanged.
- Lean / FM: no certifier changes for v1.

### Operational axis
- Determinism: world-diff + expiry pass are pure.
- Tick-rate independence: `clearsAt: SimTime` is tick-rate-independent.
- Replay / observability: new `ControllerEvent` leaves appear in existing event-trace harness.
- Performance: O(1) Map lookup; one slice; one new fold step + one expiry pass + one diff per cycle.

## Early proof point

Task fn-12.1 validates the foundation pipeline (world → expiry → diff → events → fold → reactive rule → `protocol.GoAround` issuance + `RunwayObstructionInformation` companion). If it fails — specifically if the world-diff producer or the fold-then-decide ordering doesn't work — re-evaluate Decision #3 or #5 before .2.

## References

### Doctrinal
- **CAP 413 Edition 23 (or 24, effective 2024-03-28) §4.53** — clearance cancellation pattern
- **CAP 413 §4.55 / §4.56** — `CONTINUE APPROACH` middle state (deferred)
- **CAP 413 §4.65** — Missed Approach phraseology
- **CAP 413 §4.66** — VFR aircraft "to continue into the normal traffic circuit"
- **CAP 413 §4.67** — pilot-initiated GA
- **ICAO Doc 4444 §7.4.1.4.1** — load-bearing authority. Note: "Animals and flocks of birds may constitute an obstruction with regard to runway operations."
- **ICAO Doc 4444 §7.10.2** — clearance-to-land timing
- **ICAO Doc 4444 §8.9.6.1.8** — reason given to pilot is required
- **ICAO Doc 4444 §12.3.4.18** — minimal phraseology `GO AROUND` / `GOING AROUND`
- **ICAO Doc 4444 §12.3.4.16(d)** — `CONTINUE APPROACH` phraseology

### Codebase prior art
- **fn-8** — `SeparationEngine` + reactive intervention + sticky-witness machinery + commitment-lifecycle stage regression. Reused.
- **fn-11** — `CircuitOutcome` ADT + `applyPlannedGoAround` Tick A + `planCircuitTrainedGoAround` Tick B + three-layer sim-test pin pattern. Reused.
- **fn-5** — single-aircraft sim test harness pattern.

### Memory
- `feedback_world_only_test_triggers.md` — test trigger via world authoring
- `feedback_firewall_principle.md` — controller learns only via radio/sensor/visual/FlightStrip
- `feedback_reality_anchored.md` — model real CAP 413/ICAO 4444 doctrine; deferments unflinching
- `feedback_pass_scope.md` — fold typed surface + sensing channel + reactive rule into one closing pass
- `feedback_plans_review_aware.md` — Review considerations addressed inline
- `feedback_no_corners.md` — CONTINUE APPROACH clearly deferred
- `project_rich_world_domain.md` (saved 2026-05-10) — time-varying state on entity
- `feedback_no_permission_asking.md` — full-agency execution
- `feedback_review_discipline.md` — full plan-review + post-impl-review ceremony

### External
- [SKYbrary — Go-Around](https://skybrary.aero/articles/go-around)
- [SKYbrary — CAP 413 Radiotelephony Manual (Edition 23)](https://skybrary.aero/bookshelf/cap-413-radiotelephony-manual-edition-23)
- [Event-driven.io — Idempotent command handling](https://event-driven.io/en/idempotent_command_handling/)
- [Effective Kotlin — Item 39: Use sealed classes for restricted hierarchies](https://kt.academy/article/ek-sealed-classes)

## Deferments register

- **`D-PASS-g3a-obstruction-kind-variants`** — sealed-type extension of `RunwayObstruction`; refactor when second variant lands.
- **`D-PASS-g3a-obstruction-continue-approach`** — three-state ATC ladder per CAP 413 §4.55-4.56 + ICAO §12.3.4.16(d).
- **`D-PASS-g3a-obstruction-belief-divergence`** — controller's believed clearance time drifts from world's actual.
- **`D-PASS-g3a-obstruction-orbit-hold`** — aircraft-in-circuit → orbit/hold instruction. New ATC primitive.
- **`D-PASS-g3a-obstruction-pilot-report`** — pilot-radio sensing modality.
- **`D-PASS-g3a-obstruction-leader-not-vacated`** — couples to multi-aircraft sequencing.
- **`D-PASS-g3a-obstruction-flicker-debounce`** — physics-layer debounce when sensor noise model lands.
- **`D-PASS-g3a-obstruction-aerodrome-payload`** — promote `RunwayObstructionDetected/Cleared` to carry `AerodromeId` if a future scenario requires cross-aerodrome event routing.
- **`D-PASS-g3a-obstruction-clearsAt-update`** — if a future scenario needs to extend or shorten an obstruction's `clearsAt` while it persists, lift the v1 immutability invariant and add `Some(old) → Some(new)` event emission. Currently `clearsAt` is immutable for an obstruction lifetime.

## Closures

- **Three-path GA coverage** complete at sim-test level: self-initiated + pilot-trained + ATC-instructed-obstruction.
- **Latent Circuit-mode reactive-GA bug** surfaced and fixed by Task .2.

## Requirement coverage

| Req | Description | Task(s) |
|-----|-------------|---------|
| R1  | `RunwayObstruction(clearsAt: SimTime)` + `Runway.obstruction` field + production loader | fn-12.1 |
| R2  | `ControllerEvent.RunwayObstructionDetected/Cleared` leaves (no AerodromeId payload) | fn-12.1 |
| R3a | Per-cycle world expiry pass | fn-12.1 |
| R3b | Per-controller per-cycle world-diff producer | fn-12.1 |
| R3c | `ControllerView.worldEvents` field + event-assembly concat | fn-12.1 |
| R4  | `BeliefState.runwayObstructions` slice + fold + pipeline wiring | fn-12.1 |
| R5  | `RunwayObstructed` guard `data object` | fn-12.1 |
| R6  | Pre-clearance gate: `Not(RunwayObstructed)` in `LandingConditions` | fn-12.1 |
| R7  | Reactive rule placed in 3 stages (AwaitApproach, LandingClearanceIssued, AwaitLandedObserved) with `Immediate` advancement + approach-attempt-scoped no-refire witness + supersession | fn-12.1 |
| R8  | `obstructionInfo` carrier + `RunwayObstructionInformation` protocol leaf + `processControllerResponse`/phraseology/utterance arms + Either-typed action | fn-12.1 |
| R9a | `PilotEvent.AtcGoAroundOnFinal` + recognition via flag in `pilotDecide` | fn-12.2 |
| R9b | `PilotMission.pendingAtcGoAroundFrom` field + `handleGoAround` set + `applyAtcInitiatedGoAround` consume | fn-12.2 |
| R9c | `PilotAtcInitiatedGoAroundSpec.kt` pilot-side unit tests | fn-12.2 |
| R10 | `G3aRunwayObstructionTest.kt` sim test (3-layer pins, regression at issuance, vacate closure, time band) | fn-12.3 |
| R11 | Cross-reference doc updates | fn-12.3 |
| R12 | Full verify GREEN | fn-12.1, fn-12.2, fn-12.3 |

## Done summary

_(filled per task during implementation)_

## Evidence

_(filled per task during implementation)_

## Errata

- 2026-05-11 (fn-17): CAP 413 §-cites in this spec were authored
  against the then-current Edition 23 numbering. Per fn-17.1's
  primary-source verification (artifact:
  `wiki/data-sources/cap413-edition-24-capture.md`; CAA PDF SHA
  `c620cda9b6bdbe8e9ed51b258e4df2f6e3edc839226e53ee2b591cb696a966ac`),
  Ed 24 (effective 2026-07-01) maps as follows for the sections this
  spec cites: §4.53 (cancellation of issued landing clearance) →
  §4.52; §4.55 (continue approach — runway obstructed at final) →
  §4.54; §4.56 (CONTINUE APPROACH is not a landing clearance) →
  §4.55; §4.65 (ATC-initiated GA / missed-approach phraseology) →
  §4.64; §4.66 (VFR-continue) → §4.65; §4.67 (pilot-initiated GA) →
  §4.66; §4.68 (military) → §4.67. Current-doctrine citations live in
  `protocol/.../RegulationDatabase.kt` (Ed 24-coherent post-fn-17.1);
  this spec's prose is preserved as-is for historical fidelity.
