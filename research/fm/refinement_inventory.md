# Refinement / Drift-Control Inventory

Last updated: April 15, 2026

This file is the authoritative branch-by-branch refinement map for the delivered
FM surface.

Use it together with
[parity_inventory.md](/home/andrew/dev/projects/twr2/research/fm/parity_inventory.md):

- `parity_inventory.md` says **what is closed**
- this file says **where that closure is enforced**

The enforcement boundary for a delivered branch is only considered closed when
all of the following are true:

- the Kotlin runtime boundary is explicit
- a tracked Kotlin test fails loudly on drift
- the Lean theorem boundary is explicit
- the central Lean registry in
  [GreenfieldDeliveredRefinement.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean)
  points at the load-bearing theorem surface
- the status/docs are updated

## Delivered Branches

| Branch | Status | Kotlin boundary | Kotlin drift enforcement | Lean boundary | Lean registry surface |
| --- | --- | --- | --- | --- | --- |
| Scoped nominal/full-brief core | `SCOPED_CORE_COMPLETE` | scoped protocol/core FM boundary | integration-style FM/Lean build only | `ScopedGreenfield`, `ScopedIssuance`, `ScopedSafety`, `ScopedModes` | `ScopedCoreReachableSafetyRefinementTheorem`, `ScopedCoreFullBriefRefinementTheorem` |
| Ground movement delivered branch | `WORLD_BACKED_COMPLETE` | `InstructionResolution.kt`, `ClearanceResolution.kt`, `CompletionEvaluation.kt` | `GroundMovementCurrentShapeTest.kt`, `GroundMovementProgressionTest.kt`, `DeliveredMetadataParityTest.kt` | `GroundMovementResolutionBridge`, `GreenfieldGroundMovementDeliveredCurrentShape` | `GroundMovementDeliveredReachableRefinementTheorem`, `GroundMovementDeliveredAuthorizedRefinementTheorem` |
| Route-bearing Phase A | `WORLD_BACKED_COMPLETE` | `InstructionResolution.kt`, `ClearanceResolution.kt`, `CompletionEvaluation.kt` | `ResolvedClearanceTest.kt`, `CompletionEvaluationTest.kt`, `DeliveredMetadataParityTest.kt` | `RouteBearingResolutionBridge`, `GreenfieldRouteBearingCurrentShape`, `GreenfieldRouteBearingLifecycle` | `RouteBearingDeliveredReachableRefinementTheorem`, `RouteBearingDeliveredSingleAuthorizedRefinementTheorem`, `RouteBearingDeliveredCompoundAuthorizedRefinementTheorem` |
| Route-adjacent Phase B | `CURRENT_SHAPE_COMPLETE` | plain-step execution in `ResolvedClearance.kt`, `CompletionEvaluation.kt`, `SupersessionEngine.kt` | `RouteAdjacentCurrentShapeTest.kt`, `DeliveredMetadataParityTest.kt` | `GreenfieldRouteAdjacentCurrentShape`, `GreenfieldRouteAdjacentAuthority` | `RouteAdjacentDeliveredReachableRefinementTheorem`, `RouteAdjacentDeliveredAuthorizedRefinementTheorem` |
| World-backed airspace family | `WORLD_BACKED_COMPLETE` | `InstructionResolution.kt`, `ClearanceResolution.kt`, `CompletionEvaluation.kt` | `ResolvedClearanceTest.kt`, `CompletionEvaluationTest.kt`, `ActiveClearanceEngineTest.kt`, `DeliveredMetadataParityTest.kt` | `GreenfieldAirspaceWorldBackedDeliveredCurrentShape` | `AirspaceWorldBackedDeliveredReachableRefinementTheorem`, `AirspaceWorldBackedDeliveredAuthorizedRefinementTheorem` |
| Route/vector control delivered surface, including the first narrow immediate-adjunct compound layer and `TurnByDegrees` on the explicit observed-turn-progress model | `CURRENT_SHAPE_COMPLETE` | `ResolvedClearance.kt`, `CompletionEvaluation.kt`, `InstructionRules.kt` | `StableRuntimeCurrentShapeTest.kt`, `RouteControlCurrentShapeTest.kt`, `DeliveredMetadataParityTest.kt` | `GreenfieldRouteControlDeliveredCurrentShape` | `GreenfieldRouteControlDeliveredCurrentShapeReachableIssuanceTheorem`, `GreenfieldRouteControlDeliveredCurrentShapeAuthorizedIssuanceTheorem` |
| Air modifier/admin stable subset | `CURRENT_SHAPE_COMPLETE` | `CompletionEvaluation.kt`, `InstructionRules.kt` | `StableRuntimeCurrentShapeTest.kt`, `DeliveredMetadataParityTest.kt` | `GreenfieldAirModifierCurrentShape` | `AirModifierDeliveredReachableRefinementTheorem`, `AirModifierDeliveredAuthorizedRefinementTheorem` |
| Radio family | `CURRENT_SHAPE_COMPLETE` | `CompletionEvaluation.kt`, radio state in `StructuredClearance.kt` | `DeliveredMetadataParityTest.kt` | `GreenfieldRadioCurrentShape` | `RadioDeliveredReachableRefinementTheorem`, `RadioDeliveredAuthorizedRefinementTheorem` |
| Transponder family | `CURRENT_SHAPE_COMPLETE` | `CompletionEvaluation.kt` | `DeliveredMetadataParityTest.kt` | `GreenfieldTransponderDeliveredCurrentShape` | `TransponderDeliveredReachableRefinementTheorem`, `TransponderDeliveredAuthorizedRefinementTheorem` |
| Communications/surveillance compound layer | `CURRENT_SHAPE_COMPLETE` | `ResolvedClearance.kt`, `CompletionEvaluation.kt`, `ActiveClearanceEngine.kt` | `CompletionEvaluationTest.kt`, `DeliveredMetadataParityTest.kt` | `GreenfieldCommunicationsDeliveredCurrentShape` | `CommunicationsDeliveredAuthorizedRefinementTheorem` |
| Runway delivered family | `CURRENT_SHAPE_COMPLETE` | `CompletionEvaluation.kt`, `ActiveClearanceEngine.kt` | `CompletionEvaluationTest.kt`, `DeliveredMetadataParityTest.kt` | `GreenfieldRunwayDeliveredCurrentShape` | `RunwayDeliveredReachableRefinementTheorem`, `RunwayDeliveredAuthorizedRefinementTheorem` |
| Broadened runway family | `CURRENT_SHAPE_COMPLETE` | `ResolvedClearance.kt`, `CompletionEvaluation.kt`, `ActiveClearanceEngine.kt` | `CompletionEvaluationTest.kt`, `DeliveredMetadataParityTest.kt` | `GreenfieldRunwayExpandedCurrentShape` | `RunwayExpandedReachableRefinementTheorem` |

## Drift Gates

Before widening a delivered branch further:

1. update [parity_inventory.md](/home/andrew/dev/projects/twr2/research/fm/parity_inventory.md) if the branch status changes
2. update this file if the enforcing Kotlin test file or Lean theorem boundary changes
3. update
   [GreenfieldDeliveredRefinement.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean)
   if the top-level theorem aliases change
4. rerun both verification stacks:
   - `./gradlew build`
   - `nix-shell -p lean4 --run 'cd research/fm/lean && lake build CertifiedAtc'`

## Honest Boundary

This inventory does **not** claim full automated proof/runtime extraction from
Kotlin into Lean.

What it does claim is narrower and useful:

- every delivered branch now has an explicit Kotlin runtime anchor
- every delivered branch now has tracked Kotlin drift tests in committed paths
- every delivered branch now has an explicit Lean theorem anchor
- the FM docs can point at one registry module instead of hand-waving across
  many modules
