import CertifiedAtc.GreenfieldRunwayWorldBackedCompound

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRunwayWorldBackedDeliveredCurrentShape` packages the delivered
world-backed runway-operation surface behind one source-level theorem boundary.

This slice includes:

- the world-backed single-step runway-operation family
- the first narrow world-backed compound layer over immediate adjuncts

It is intentionally narrower than the older `GreenfieldRunwayExpanded...`
package: this module is about the runway-operation family itself, not every
runway-adjacent instruction.
-/

inductive GreenfieldRunwayWorldBackedDeliveredCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld)
    (initialState : ResolutionState) :
    StructuredClearance → Prop
  | single
      {clearance : StructuredClearance}
      (hIssuable :
        GreenfieldRunwayWorldBackedIssuable world initialState clearance) :
      GreenfieldRunwayWorldBackedDeliveredCurrentShapeIssuable world initialState clearance
  | compound
      {clearance : StructuredClearance}
      (hIssuable :
        GreenfieldRunwayWorldBackedCompoundIssuable world initialState clearance) :
      GreenfieldRunwayWorldBackedDeliveredCurrentShapeIssuable world initialState clearance

abbrev GreenfieldRunwayWorldBackedDeliveredCurrentShapeWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  GreenfieldRunwayWorldBackedCompoundWorldAuthorized world controller steps

theorem GreenfieldRunwayWorldBackedDeliveredCurrentShapeReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldRunwayWorldBackedDeliveredCurrentShapeIssuable
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
        GreenfieldRunwayWorldBackedAdmissionSoundnessTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | compound hCompound =>
      exact
        GreenfieldRunwayWorldBackedCompoundAdmissionSoundnessTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hCompound

theorem GreenfieldRunwayWorldBackedDeliveredCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldRunwayWorldBackedDeliveredCurrentShapeIssuable
        world
        initialState
        clearance)
    (hAuthority :
      GreenfieldRunwayWorldBackedDeliveredCurrentShapeWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ finalState, ∃ resolved,
      runwayCompoundInstructionsIssuerAuthorized
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
      runwayCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    runwayCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GreenfieldRunwayWorldBackedDeliveredCurrentShapeReachableIssuanceTheorem
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
