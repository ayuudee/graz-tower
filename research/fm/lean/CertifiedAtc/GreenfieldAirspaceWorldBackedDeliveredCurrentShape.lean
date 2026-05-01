import CertifiedAtc.GreenfieldAirspaceWorldBackedCompound

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldAirspaceWorldBackedDeliveredCurrentShape` packages the delivered
world-backed airspace surface behind one source-level theorem boundary.

This slice now includes:

- the world-backed single-step airspace surface
- the first narrow world-backed compound layer

It remains intentionally conservative. The layer is world-backed because the
primary airspace instruction resolves against a concrete `AirspaceVolume`, but
it still does not claim boundary-crossing or broader world-backed compound
airspace semantics.
-/

inductive GreenfieldAirspaceWorldBackedDeliveredCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | single
      {clearance : StructuredClearance}
      (hIssuable : GreenfieldAirspaceWorldBackedIssuable world clearance) :
      GreenfieldAirspaceWorldBackedDeliveredCurrentShapeIssuable world clearance
  | compound
      {clearance : StructuredClearance}
      (hIssuable : GreenfieldAirspaceWorldBackedCompoundIssuable world clearance) :
      GreenfieldAirspaceWorldBackedDeliveredCurrentShapeIssuable world clearance

abbrev GreenfieldAirspaceWorldBackedDeliveredCurrentShapeWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  GreenfieldAirspaceCompoundWorldAuthorized world controller steps

theorem GreenfieldAirspaceWorldBackedDeliveredCurrentShapeReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldAirspaceWorldBackedDeliveredCurrentShapeIssuable world clearance) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable with
  | single hSingle =>
      exact
        GreenfieldAirspaceWorldBackedAdmissionSoundnessTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | compound hCompound =>
      exact
        GreenfieldAirspaceWorldBackedCompoundAdmissionSoundnessTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hCompound

theorem GreenfieldAirspaceWorldBackedDeliveredCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldAirspaceWorldBackedDeliveredCurrentShapeIssuable world clearance)
    (hAuthority :
      GreenfieldAirspaceWorldBackedDeliveredCurrentShapeWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ resolved,
      greenfieldAirspaceCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true ∧
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      greenfieldAirspaceCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    greenfieldAirspaceCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GreenfieldAirspaceWorldBackedDeliveredCurrentShapeReachableIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

end Greenfield
end CertifiedAtc
