import CertifiedAtc.GreenfieldRunwayCompound

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRunwayExpandedCurrentShape` packages the broadened runway-family
current-shape surface behind one source-level theorem boundary.

The phase-3 delivered slice is:

- single-step `BacktrackRunway`
- single-step runway-operation family:
  `LineUpAndWait`, `ClearedForTakeoff`, `ClearedToLand`,
  `ClearedTouchAndGo`, `ClearedLowApproach`, and `GoAround`
- the first narrow current-shape compound slice for the delivered
  runway-operation family

This module intentionally packages the reachable resolved-admission boundary.
It does not invent a new cross-cutting runway-family authority layer; the
frozen conservative authority story still lives in the constituent runway
modules and the compound layer itself.
-/

inductive GreenfieldRunwayExpandedCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | delivered
      {clearance : StructuredClearance}
      (hIssuable : GreenfieldRunwayDeliveredCurrentShapeIssuable clearance) :
      GreenfieldRunwayExpandedCurrentShapeIssuable world clearance
  | backtrack
      {clearance : StructuredClearance}
      (hIssuable :
        BacktrackCurrentShapeIssuable
          (RouteBearingScopedAviationWorld.toResolutionWorld world)
          clearance) :
      GreenfieldRunwayExpandedCurrentShapeIssuable world clearance
  | compound
      {clearance : StructuredClearance}
      (hIssuable : RunwayCompoundCurrentShapeIssuable world clearance) :
      GreenfieldRunwayExpandedCurrentShapeIssuable world clearance

theorem GreenfieldRunwayExpandedCurrentShapeReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRunwayExpandedCurrentShapeIssuable world clearance) :
    ∃ finalState, ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable with
  | delivered hDelivered =>
      rcases GreenfieldRunwayDeliveredCurrentShapeReachableIssuanceTheorem
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hDelivered with
          ⟨resolved, hResolve, hReachable⟩
      exact ⟨initialState, resolved, hResolve, hReachable⟩
  | backtrack hBacktrack =>
      rcases BacktrackCurrentShapeIssuanceTheorem
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hBacktrack with
          ⟨farEndPoint, resolved, hResolve, hReachable⟩
      exact ⟨{ currentPoint := some farEndPoint }, resolved, hResolve, hReachable⟩
  | compound hCompound =>
      rcases RunwayCompoundCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hCompound with
          ⟨resolved, hResolve, hReachable⟩
      exact ⟨initialState, resolved, hResolve, hReachable⟩

end Greenfield
end CertifiedAtc
