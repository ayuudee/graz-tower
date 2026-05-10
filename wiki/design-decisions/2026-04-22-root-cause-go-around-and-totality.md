# 2026-04-22: Root Cause Analysis — Go-Around Cluster and Totality Violations

## Context

An ultra review of the pilot route planner found four critical bugs in the go-around path (C1-C4) and five totality violations (F1-F5). All were fixed in commit `c947de0`. This note captures the root causes so the same class of bug doesn't recur.

## The bugs

**Go-around cluster:**
- C1: `hasClearance` not reset after go-around — pilot retains stale landing clearance on subsequent circuit
- C2: IFR go-around uses VFR `goAroundTask()` — pilot doesn't fly published missed approach
- C3: GOING_AROUND transmission dead code — pilot goes around silently, ATC never hears the call
- C4: `replaceChild` matches completed subtrees — go-around on second circuit corrupts first circuit's tree

**Totality violations:**
- F1-F4: `error()` in `derivePilotGoal`, `updateAfterTransmission`, `updateAfterReport`, `skipCompletedSteps` for states that were reachable but not exercised by tests
- F5: `applyFplAmendment` silently discards `AmendmentError` via `.getOrNull()`

## Root Cause 1: Mission state mixes tree structure with flat side-state

The HTN tree tracks what the pilot is doing. But `hasClearance`, `lastReportedLeg`, `activeConstraints`, `routeOverride`, `contactedOnFrequency`, `reportedVacated` track the context of what they're doing. These 6+ fields live as flat booleans/nullables on `PilotMission`, outside the tree.

On the forward path (startup → takeoff → circuit → land), this works: context accumulates and remains valid as steps advance. Go-around is the only **reversal** — the tree is replaced, invalidating approach-phase context. The go-around handler must manually reset every contextual field. Each field was added independently for its own feature. No mechanism forces them to be treated as a unit.

**Symptom**: C1 (hasClearance not reset). C4 is a variant — the tree replacement was also incomplete.

**Fix direction**: Group mission context by phase. An `ApproachContext?` record that is set on approach entry and nulled atomically on go-around. New fields added to approach context automatically get reset. "Shotgun surgery" becomes impossible because the go-around handler nulls one record, not N fields.

## Root Cause 2: The cognitive loop has an undocumented ordering contract

`pilotCognitiveDecide` runs: (1) advance completed steps, (2) generate transmissions for current step. If `isReportComplete` returns `true`, the step completes in phase 1 before phase 2 runs. The transmission is never generated.

`REPORT_READY` avoids this by returning `false` from `isReportComplete` and completing via the transmission trigger pattern. `GOING_AROUND` returned `true` because the developer wanted immediate advancement. The intent was "go around instantly" — the effect was "go around silently."

**Symptom**: C3 (dead transmission).

**Why this is easy to get wrong**: A step's completion behavior is declared in three separate places: `CompletionMode` on the `PrimitiveTask`, `isReportComplete` in the cognitive layer, and `stepTransmission` in a third function. These three declarations must be mutually consistent, but nothing enforces consistency. A REPORTED step where `isReportComplete` returns `true` is a contradiction — the step claims to be report-gated but auto-completes — and nothing catches it.

**Fix direction**: Co-locate completion and transmission declarations. A REPORTED step should carry its completion predicate and its transmission together, so contradictions are structurally impossible.

## Root Cause 3: Forward-path-first development without reversal analysis

Every feature was built and tested for the happy path. Go-around was bolted on after. But the forward path accumulated implicit assumptions:
- `hasClearance = true` means "on approach" (set by ClearedToLand, never unset)
- The first matching `TaskName.Circuit` is the active one (true with one circuit)
- `isReportComplete(GOING_AROUND) = true` means "advance immediately" (wrong in the loop)
- IFR and VFR go-arounds need different task trees (but only one was wired)

Each assumption is reasonable on the forward path. Go-around violates all of them.

**Symptom**: All of C1-C4.

**Why the golden test didn't catch it**: The golden test (2-circuit T&G stand-to-stand) completes both circuits normally. No go-around is triggered. Go-around was tested only at the unit level — "does the subtree get replaced?" — not end-to-end.

**Fix direction**: Any mission type that supports go-around must have a go-around integration test before merge. The test must exercise the full reversal: approach → go-around → re-enter circuit → approach again → land. This catches C1 (stale clearance), C3 (missing transmission), C4 (wrong subtree), and C2 (wrong task tree) in a single test.

## Root Cause 4: `error()` for reachable states

Commandment 8 ("dead programs tell no lies") says: `error()` is correct for provably impossible states. F1-F5 used `error()` for states that were merely **unused**, not impossible:
- `Emergency` transmissions are defined in the protocol. A well-typed caller CAN construct one.
- `TcasRa` reports are defined in the sealed hierarchy. They CAN appear.
- `CircuitTraining` as an active compound is structurally unlikely but not type-prevented.
- Mid-departure phases are valid `PilotPhase` variants.

The developer's mental model was "we'll never see these because no test creates them." But the type system says they're valid. The mismatch between "what the type allows" and "what the developer expects" is where false `error()` sneaks in.

**Fix direction**: The commandment 8 test — "could a well-typed caller construct this input?" If yes, handle it. If you want it to be impossible, make it unrepresentable in the types.

## Red team critique (2026-04-22)

The initial root causes were challenged. Key corrections:

**RC1 (flat side-state) was partially wrong.** "Group by phase" doesn't cleanly partition the fields — `activeConstraints` and `routeOverride` span phases. An `ApproachContext?` record would be a lie for cross-phase fields and worsen `copy()` ergonomics at every call site. The honest fix is simpler: a `resetForGoAround()` function with an exhaustive test asserting it covers every `PilotMission` field. When a new field is added, the test forces a conscious decision about go-around behavior.

**RC2 (undocumented ordering) was inflated.** The three-place pattern works fine for REPORT_READY. The bug was a single wrong boolean, not an architectural flaw. The real question — why was it set wrong — was never asked. The answer: AI pattern-matching on similar-looking steps without tracing the loop invariant.

**RC3 (forward-path-first) was a tautology.** "We built the forward path first" is not a root cause. The structural answer: make reversal-sensitive state visible at the type level — e.g., a clearance token scoped to the tree node, so subtree replacement automatically discards it.

**Commandments 3 and 8 contradicted each other.** C3 said `error()` for "shouldn't happen." C8 said `error()` only for provably impossible states. The developer followed C3 correctly. C3 has been updated to defer to C8: `error()` means "impossible at the type level," `Left(NotYetImplemented)` means "possible but deferred."

**Missing root cause: AI code generation.** These bugs have a characteristic AI signature — locally correct, globally blind. The go-around handler was pattern-matched from the forward-path `copy()` without auditing which fields to reset. `isReportComplete(GOING_AROUND) = true` was pattern-matched from similar steps without tracing the loop ordering. The fix for AI-generated code: adversarial review must specifically target reversal invariants and global state interactions, not just local correctness.

## Revised actionable changes

| # | Change | Addresses |
|---|--------|-----------|
| 1 | `resetForGoAround()` function + exhaustive field test | Flat side-state |
| 2 | Commandment 3 updated to defer to Commandment 8 | Commandment conflict |
| 3 | Go-around integration test for every mission type | No reversal test |
| 4 | Adversarial review checklist: "what state does this reversal invalidate?" | AI pattern-matching |
| 5 | Consider: clearance token scoped to tree node (auto-discarded on subtree replacement) | Structural fix for reversal state |

## Resolution (2026-05-10)

Closed by **fn-11** (`G3a — pilot-trained VFR go-around as circuit-training
outcome`). The fn-11 epic landed:

- **fn-11.1** — typed `CircuitOutcome` ADT (`TouchAndGo / FullStop /
  GoAround`) replacing `(circuits: Int, fullStopOnLast: Boolean)`. The
  `planMission` compiler walks the `outcomes` list with an exhaustive
  sealed `when`; the `GoAround` outcome compiles into a static
  `plannedGoAroundCircuitTask()` subtree. Tick A (`applyPlannedGoAround`)
  emits `phase=Final + route=PilotRoute.None + Report(GoingAround) +
  resetForGoAround(now)`; Tick B's Circuit-mode `planRoute` special-case
  builds the published GA path via `buildGoAroundRoute`. 23-call-site
  migration; G0/G1/G1-minimal/G2 all stay green.
- **fn-11.2** — sim-level golden test
  `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotTrainedGoAroundTest.kt`.
  Exercises the full reversal — approach → trained GA at short-final →
  re-enter circuit → approach again → land — per the actionable change
  #3 above. Three-layer pin pattern (causal partial-order +
  sticky-witness regression via `GA-POST-CLEAR` +
  kinematic non-event) plus the R7 vacate-coordination closure pin.

The "Any mission type that supports go-around must have a go-around
integration test before merge" gap is closed: `CircuitTraining` with the
`GoAround` outcome is exercised end-to-end. Future mission types that
support go-around inherit the same requirement.

**fn-12 (2026-05-10)** extends the closure to the **third reactive-GA
path** — ATC-instructed go-around triggered by a world-state runway
obstruction. The epic landed:

- **fn-12.1** — typed `RunwayObstruction(clearsAt: SimTime)` rich-domain
  field on `Runway.obstruction`; per-cycle world expiry pass + per-
  controller world-diff producer emitting
  `ControllerEvent.RunwayObstructionDetected` / `Cleared` (the first
  world-state-derived event source class — see
  `2026-04-16-transmission-reception-architecture.md` § Unified Event
  Taxonomy); `BeliefState.runwayObstructions` fold; `RunwayObstructed`
  guard + `Not(RunwayObstructed)` pre-clearance gate on
  `LandingConditions`; reactive `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule
  across the three on-final stages with `Immediate` advancement; companion
  `RunwayObstructionInformation` transmission (reason on radio per ICAO
  §7.4.1.4.1(c) + §8.9.6.1.8 — mandatory).
- **fn-12.2** — pilot-side ATC-initiated reactive GA in Circuit-mode via
  `PilotMission.pendingAtcGoAroundFrom: Option<MissionStep>` flag set by
  `handleGoAround` BEFORE its tree rewrite and consumed by `pilotDecide`'s
  `recognizeAtcInitiatedGoAround` + `applyAtcInitiatedGoAround` arm
  (intent-only Tick A; Tick B reuses fn-11.1's `planCircuitTrainedGoAround`
  — zero new route code).
- **fn-12.3** — sim-level golden test
  `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionTest.kt`.
  World-only test trigger via `runUntilWithStateTrace`'s `onAfterEvent`
  hook (one-shot authorship guarded against the `clearsAt` immutability
  invariant). Three-layer pin pattern extended with separated decision-
  cycle / transmission-start timestamps (`GoAround.txStart <
  RunwayObstructionInformation.txStart` after radio serialization).

The three reactive-GA paths are now triple-covered:
1. **Self-initiated** — pilot decides to GA without instruction (fn-10
   era; pilot-side `derivePilotEvent` → `applySelfInitiatedGoAround`).
2. **Pilot-trained** — instructor authors `CircuitOutcome.GoAround` in
   the mission goal; the mission compiler forks the tree statically;
   pilot follows the plan autonomously (G3a / fn-11).
3. **ATC-instructed-obstruction** — controller's reactive
   `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule fires off a world-state-derived
   `RunwayObstructionDetected` event; pilot's `pendingAtcGoAroundFrom`
   flag-driven recognition arm consumes the resulting
   `Instruction.GoAround` and produces a circuit-mode reactive GA
   (G3a-obstruction / fn-12).

Each path has dedicated unit-test coverage at the controller / pilot
level **and** a sim-level golden test exercising the composition. The
"reversal completeness" obligation is now load-bearing across the full
reactive-GA surface.
