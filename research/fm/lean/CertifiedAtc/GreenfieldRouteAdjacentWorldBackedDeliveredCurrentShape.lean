import CertifiedAtc.GreenfieldRouteAdjacentWorldBackedCompound
import CertifiedAtc.GreenfieldRouteAdjacentWorldBackedAuthority

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShape` packages the
delivered route-adjacent surface behind one source-level theorem boundary on
the current explicit approach/circuit model.

This slice includes:

- the world-backed single-step route-adjacent family
- the first narrow world-backed compound layer over immediate adjuncts
- the already-frozen conservative type-level authority mapping from
  `GreenfieldRouteAdjacentWorldBackedAuthority`
-/

inductive GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld)
    (initialState : ResolutionState) :
    StructuredClearance → Prop
  | single
      {clearance : StructuredClearance}
      (hIssuable :
        GreenfieldRouteAdjacentWorldBackedIssuable world initialState clearance) :
      GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShapeIssuable
        world
        initialState
        clearance
  | compound
      {clearance : StructuredClearance}
      (hIssuable :
        GreenfieldRouteAdjacentWorldBackedCompoundIssuable world initialState clearance) :
      GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShapeIssuable
        world
        initialState
        clearance

abbrev GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShapeWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  RouteAdjacentWorldAuthorized world controller steps

theorem GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShapeReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShapeIssuable
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
  cases hIssuable with
  | single hSingle =>
      exact
        GreenfieldRouteAdjacentWorldBackedAdmissionSoundnessTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | compound hCompound =>
      exact
        GreenfieldRouteAdjacentWorldBackedCompoundAdmissionSoundnessTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hCompound

theorem GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShapeIssuable
        world
        initialState
        clearance)
    (hAuthority :
      GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShapeWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ finalState, ∃ resolved,
      routeAdjacentInstructionsIssuerAuthorized
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
      routeAdjacentInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    routeAdjacentInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShapeReachableIssuanceTheorem
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
