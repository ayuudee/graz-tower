import CertifiedAtc.GreenfieldAirspaceCompound

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldAirspaceDeliveredCurrentShape` packages the delivered current-shape
airspace-clearance surface behind one source-level theorem boundary.

The delivered slice is:

- single-step `RemainOutsideControlledAirspace`
- single-step `ClearedToEnterControlZone`
- single-step `SpecialVfrClearance`
- the first narrow compound slice for persistent primaries
  `ClearedToEnterControlZone` and `SpecialVfrClearance`

This module does not claim richer world-backed airspace-entry semantics than
the current runtime model actually has. It packages the already-delivered
single-step and narrow-compound slices so later widening can start from one
honest closure point.
-/

inductive GreenfieldAirspaceDeliveredCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | single
      {clearance : StructuredClearance}
      (hIssuable : GreenfieldAirspaceCurrentShapeIssuable clearance) :
      GreenfieldAirspaceDeliveredCurrentShapeIssuable world clearance
  | compound
      {clearance : StructuredClearance}
      (hIssuable : GreenfieldAirspaceCompoundCurrentShapeIssuable world clearance) :
      GreenfieldAirspaceDeliveredCurrentShapeIssuable world clearance

abbrev GreenfieldAirspaceDeliveredCurrentShapeWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  GreenfieldAirspaceCompoundWorldAuthorized world controller steps

theorem GreenfieldAirspaceDeliveredCurrentShapeReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldAirspaceDeliveredCurrentShapeIssuable world clearance) :
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
        GreenfieldAirspaceCurrentShapeAdmissionSoundnessTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | compound hCompound =>
      cases hCompound with
      | enterZone hContent hSteps hReady hDomain hCondition =>
          exact
            GreenfieldAirspaceCompoundAdmissionSoundnessTheorem
              (world := world)
              (existing := existing)
              (initialState := initialState)
              (clearance := clearance)
              (content := _)
              (primary := .clearedToEnterControlZone _ _ _ _)
              (tail := _)
              hReach
              hFresh
              hContent
              hSteps
              hReady
              hDomain
              hCondition
      | specialVfr hContent hSteps hReady hDomain hCondition =>
          exact
            GreenfieldAirspaceCompoundAdmissionSoundnessTheorem
              (world := world)
              (existing := existing)
              (initialState := initialState)
              (clearance := clearance)
              (content := _)
              (primary := .specialVfrClearance _ _ _ _)
              (tail := _)
              hReach
              hFresh
              hContent
              hSteps
              hReady
              hDomain
              hCondition

theorem GreenfieldAirspaceDeliveredCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldAirspaceDeliveredCurrentShapeIssuable world clearance)
    (hAuthority :
      GreenfieldAirspaceDeliveredCurrentShapeWorldAuthorized
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
  rcases GreenfieldAirspaceDeliveredCurrentShapeReachableIssuanceTheorem
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
