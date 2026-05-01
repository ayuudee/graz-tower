import CertifiedAtc.GreenfieldRouteControlDeliveredCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRouteControlWorldBackedDeliveredCurrentShape` re-exports the
delivered route/vector-control branch on the honest current explicit model.

Why this is stronger than the older current-shape label:

- direct-fix and airway-join instructions already resolve against concrete
  published-world facts
- heading/vector instructions already resolve through explicit vector payloads
  with issue-time heading capture where the runtime needs it
- `TurnByDegrees` completion is already closed on explicit observed-turn-
  progress state

So the delivered branch is now best read as world-backed on the current
published-fix/airway + explicit-vector-state model, even though richer
heading-hold semantics remain open.
-/

abbrev GreenfieldRouteControlWorldBackedDeliveredCurrentShapeIssuable :=
  GreenfieldRouteControlDeliveredCurrentShapeIssuable

abbrev GreenfieldRouteControlWorldBackedDeliveredCurrentShapeWorldAuthorized :=
  GreenfieldRouteControlDeliveredCurrentShapeWorldAuthorized

theorem GreenfieldRouteControlWorldBackedDeliveredCurrentShapeReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldRouteControlWorldBackedDeliveredCurrentShapeIssuable
        world
        initialState
        clearance) :
    ∃ finalState, ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  exact
    GreenfieldRouteControlDeliveredCurrentShapeReachableIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable

theorem GreenfieldRouteControlWorldBackedDeliveredCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldRouteControlWorldBackedDeliveredCurrentShapeIssuable
        world
        initialState
        clearance)
    (hAuthority :
      GreenfieldRouteControlWorldBackedDeliveredCurrentShapeWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ finalState, ∃ resolved,
      routeControlCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true ∧
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  exact
    GreenfieldRouteControlDeliveredCurrentShapeAuthorizedIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hWf
      hReach
      hFresh
      hIssuable
      hAuthority

end Greenfield
end CertifiedAtc
