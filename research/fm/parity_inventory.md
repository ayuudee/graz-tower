# Kotlin / Lean Parity Inventory

Last updated: April 15, 2026

This file freezes the current Kotlin-to-Lean parity boundary for the delivered
FM surface. It is the authoritative inventory for:

- what is closed
- what model that closure is relative to
- what remains intentionally open
- where drift must be checked before widening further

Use it together with
[refinement_inventory.md](/home/andrew/dev/projects/twr2/research/fm/refinement_inventory.md):

- this file says **what status each delivered branch has**
- `refinement_inventory.md` says **where that status is enforced**

## Status Classes

- `SCOPED_CORE_COMPLETE`
  The scoped nominal/full-brief theorem programme is closed.
- `CURRENT_SHAPE_COMPLETE`
  The delivered Kotlin semantics are mirrored on the greenfield Lean boundary
  for the current model, without adding new world-resolution theory.
- `WORLD_BACKED_COMPLETE`
  The delivered Kotlin semantics are mirrored on a world-backed Lean boundary
  for the current explicit model.
- `INTENTIONALLY_OPEN`
  Not yet closed. This is deliberate, not an undocumented gap.

## Delivered Families

| Family | Kotlin boundary | Lean/FM boundary | Status | Load-bearing drift seams |
| --- | --- | --- | --- | --- |
| Scoped nominal + full-brief core | `protocol/*`, `core/*`, scoped FM interfaces | `ScopedGreenfield`, `ScopedIssuance`, `ScopedSafety`, `ScopedModes` | `SCOPED_CORE_COMPLETE` | Keep scoped command classification, authority assumptions, and separation packaging aligned. |
| Route-bearing Phase A: `ClearedTo`, published `HoldAt`, non-circling `ClearedApproach`, `JoinCircuit` | `InstructionResolution.kt`, `ClearanceResolution.kt`, `CompletionEvaluation.kt` | `RouteBearingResolutionBridge`, `GreenfieldRouteBearingCurrentShape`, `GreenfieldRouteBearingLifecycle` | `WORLD_BACKED_COMPLETE` | Published-procedure progress/completion is closed only for the current graph-backed published-procedure model. |
| Route-adjacent Phase B: `ContinueApproach`, `ExtendDownwind`, `Orbit` | `InstructionRules.kt`, current plain-step execution surface | `GreenfieldRouteAdjacentCurrentShape`, `GreenfieldRouteAdjacentAuthority` | `CURRENT_SHAPE_COMPLETE` | `ExtendDownwind` / `Orbit` still depend on the source-domain-supplied convention for metadata-domain-less persistent steps. |
| Airspace family: `RemainOutsideControlledAirspace`, `ClearedToEnterControlZone`, `SpecialVfrClearance` | `InstructionResolution.kt`, `ClearanceResolution.kt`, `CompletionEvaluation.kt` | `GreenfieldAirspaceWorldBackedDeliveredCurrentShape` | `WORLD_BACKED_COMPLETE` | Closed only for the current graph-backed point-set + transition model; richer polygonal/continuous geometry remains open. |
| Ground movement core: `TaxiTo`, `HoldShortOf`, `CrossRunway` | `InstructionResolution.kt`, `ClearanceResolution.kt`, `CompletionEvaluation.kt` | `GroundMovementResolutionBridge`, `GreenfieldGroundMovementDeliveredCurrentShape` | `WORLD_BACKED_COMPLETE` | Closed only for the current graph-backed taxi-route + holding-point + crossing-point model. |
| `HoldPosition` | `InstructionRules.kt`, plain-step execution in `ResolvedClearance.kt` / `CompletionEvaluation.kt` | `GreenfieldGroundMovementCurrentShape`, `GreenfieldGroundMovementDeliveredCurrentShape` | `CURRENT_SHAPE_COMPLETE` | Conservative `(taxiway, taxi)` authority and plain persistent semantics must stay aligned with the runtime. |
| Route/vector control stable subset: `ProceedDirect`, `LeaveHoldProceedDirect`, `WhenAbleProceedDirect`, `RejoinSidAt`, `JoinAirway`, `ResumeOwnNavigation`, `RouteAsFiled`, `FlyHeading`, `TurnHeading`, `ContinuePresentHeading`, `StopTurn`, `InterceptLocaliser` | `ResolvedClearance.kt`, `CompletionEvaluation.kt`, current route-control rules in `InstructionRules.kt` | `GreenfieldRouteControlCurrentShape` | `CURRENT_SHAPE_COMPLETE` | Direct-fix / airway-join completion is closed on the current published-world model; plain heading controls remain current-shape only. |
| Air modifier / admin stable subset: level, speed, pressure, and cancel-clearance instructions | `CompletionEvaluation.kt`, current metadata in `InstructionRules.kt` | `GreenfieldAirModifierCurrentShape` | `CURRENT_SHAPE_COMPLETE` | Conservative `airspaceVolume` authority is frozen at the current type-level granularity; persistent speed/level compliance remains current-engine specific. |
| Radio family: `ContactFrequency`, `MonitorFrequency` | `CompletionEvaluation.kt`, radio state in `StructuredClearance.kt` | `GreenfieldRadioCurrentShape` | `CURRENT_SHAPE_COMPLETE` | Role-vs-explicit-frequency completion must remain aligned. |
| Transponder family: `SetSquawk`, `ConfirmSquawk`, `SquawkIdent`, `SquawkStandby`, `SquawkNormal`, `StopSquawk` | `CompletionEvaluation.kt` | `GreenfieldTransponderDeliveredCurrentShape` | `CURRENT_SHAPE_COMPLETE` | Conservative `(radioRole, squawk)` authority must stay aligned with runtime semantics. |
| Mixed communications / surveillance narrow compound layer | compound runtime semantics in `ResolvedClearance.kt`, `CompletionEvaluation.kt`, `ActiveClearanceEngine.kt` | `GreenfieldCommunicationsDeliveredCurrentShape` | `CURRENT_SHAPE_COMPLETE` | Only the first narrow mixed compound layer is closed. Broader coordination semantics remain open. |
| Runway-operation family: `LineUpAndWait`, `ClearedForTakeoff`, `ClearedToLand`, `ClearedTouchAndGo`, `ClearedLowApproach`, `GoAround` | `CompletionEvaluation.kt`, active-set behavior in `ActiveClearanceEngine.kt` | `GreenfieldRunwayDeliveredCurrentShape` | `CURRENT_SHAPE_COMPLETE` | Conditional-capable runway metadata must stay aligned across Kotlin and Lean. |
| `BacktrackRunway` plus broadened current-shape runway package | resolved far-end behavior in `ResolvedClearance.kt`, `CompletionEvaluation.kt` | `GreenfieldBacktrackCurrentShape`, `GreenfieldRunwayExpandedCurrentShape` | `CURRENT_SHAPE_COMPLETE` | Broader ground-movement semantics are not part of this closure. |

## Intentionally Open Branches

| Branch | Current state | Why it is still open |
| --- | --- | --- |
| Broader communications / surveillance semantics | `INTENTIONALLY_OPEN` | The first narrow mixed compound layer is closed, but richer coordination/surveillance semantics are not. |
| Deeper route-bearing beyond the current graph-backed published-procedure model | `INTENTIONALLY_OPEN` | The current model is closed; richer execution and refinement is a new branch. |
| Richer airspace beyond the current graph-backed point-set + transition model | `INTENTIONALLY_OPEN` | The current model is closed; richer geometric/polygonal semantics is a new branch. |
| `TurnByDegrees` and richer heading-progress manoeuvre semantics | `INTENTIONALLY_OPEN` | The current engine has no heading-progress observation, so this family is not honest to classify as stable-runtime complete yet. |
| Richer operational mode semantics | `INTENTIONALLY_OPEN` | Scoped full-brief is closed conservatively, but richer operational detail remains optional. |

## Drift-Control Rules

When a delivered family changes, update this file if any of the following
changes:

1. `InstructionRules.kt` metadata:
   domain, timing, conditional capability, completion category, or
   supersession behavior.
2. Authority mapping:
   `AuthorityModel.kt`, [instruction_authority_contract.md](/home/andrew/dev/projects/twr2/research/fm/instruction_authority_contract.md), or the corresponding Lean authority package.
3. Runtime lifecycle:
   resolution, completion, or active-set consequences in `core`.
4. Proof boundary:
   the family moves between `CURRENT_SHAPE_COMPLETE`,
   `WORLD_BACKED_COMPLETE`, and `INTENTIONALLY_OPEN`.

No family should change status to a stronger class unless all of the following
are true:

- the Kotlin runtime semantics are explicit
- the Lean boundary matches them
- the relevant FM status docs are updated
- verification has passed for the touched boundary

## Recommended Next Branch

With parity now frozen, the delivered broader ground/surface branch closed, and
the remaining stable-runtime families moved over, there is no new automatic
low-risk widening branch of exactly the same shape.

The next deliberate choices are:

- keep
  [refinement_inventory.md](/home/andrew/dev/projects/twr2/research/fm/refinement_inventory.md)
  and
  [GreenfieldDeliveredRefinement.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean)
  current whenever a delivered branch changes
- broader communications / surveillance semantics, or
- the next genuinely semantic research branch beyond the current models
