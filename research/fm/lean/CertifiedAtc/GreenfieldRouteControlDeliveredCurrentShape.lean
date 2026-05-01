import CertifiedAtc.GreenfieldRouteControlCompound

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRouteControlDeliveredCurrentShape` packages the delivered
route/vector-control surface behind one source-level theorem boundary.

This slice now includes:

- the delivered single-step route/vector-control surface
- the first narrow compound layer with one leading route/vector-control step
  plus immediate adjunct tails

It remains current-shape rather than world-backed in the broad sense. The
point is to close the delivered route/vector surface on the execution boundary
the runtime currently exposes.
-/

inductive GreenfieldRouteControlDeliveredCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld)
    (initialState : ResolutionState) :
    StructuredClearance → Prop
  | single
      {clearance : StructuredClearance}
      (hIssuable : GreenfieldRouteControlCurrentShapeIssuable world initialState clearance) :
      GreenfieldRouteControlDeliveredCurrentShapeIssuable world initialState clearance
  | compound
      {clearance : StructuredClearance}
      (hIssuable : GreenfieldRouteControlCompoundCurrentShapeIssuable world initialState clearance) :
      GreenfieldRouteControlDeliveredCurrentShapeIssuable world initialState clearance

abbrev GreenfieldRouteControlDeliveredCurrentShapeWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  RouteControlCompoundWorldAuthorized world controller steps

theorem GreenfieldRouteControlDeliveredCurrentShapeReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldRouteControlDeliveredCurrentShapeIssuable world initialState clearance) :
    ∃ finalState resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable with
  | single hSingle =>
      exact
        GreenfieldRouteControlCurrentShapeReachableIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | compound hCompound =>
      exact
        GreenfieldRouteControlCompoundCurrentShapeReachableIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hCompound

theorem GreenfieldRouteControlDeliveredCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldRouteControlDeliveredCurrentShapeIssuable world initialState clearance)
    (hAuthority :
      GreenfieldRouteControlDeliveredCurrentShapeWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ finalState resolved,
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
  have hAuthorized :
      routeControlCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    routeControlCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GreenfieldRouteControlDeliveredCurrentShapeReachableIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨finalState, resolved, hResolve, hReachable⟩
  exact ⟨finalState, resolved, hAuthorized, hResolve, hReachable⟩

end Greenfield
end CertifiedAtc
