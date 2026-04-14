import CertifiedAtc.GreenfieldCommunicationsCompound

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldCommunicationsDeliveredCurrentShape` packages the delivered phase-2
communications/surveillance surface behind one source-level theorem boundary.

The delivered slice is:

- single-step radio (`ContactFrequency`, `MonitorFrequency`)
- single-step transponder/surveillance
- first narrow mixed radio/transponder compounds over those delivered families

This keeps the current widening honest:

- no new world-resolution theory
- no broader coordination semantics
- no new authority families

It packages only the now-delivered current-shape communications/surveillance
surface so later widening can start from one clean closure point.
-/

inductive GreenfieldCommunicationsDeliveredCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | radioSingle
      {clearance : StructuredClearance}
      (hIssuable : GreenfieldRadioCurrentShapeIssuable world clearance) :
      GreenfieldCommunicationsDeliveredCurrentShapeIssuable world clearance
  | transponderSingle
      {clearance : StructuredClearance}
      (hIssuable : GreenfieldTransponderDeliveredCurrentShapeIssuable clearance) :
      GreenfieldCommunicationsDeliveredCurrentShapeIssuable world clearance
  | compound
      {clearance : StructuredClearance}
      (hIssuable : GreenfieldCommunicationsCompoundCurrentShapeIssuable world clearance) :
      GreenfieldCommunicationsDeliveredCurrentShapeIssuable world clearance

abbrev GreenfieldCommunicationsDeliveredCurrentShapeWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  CommunicationsCompoundWorldAuthorized world controller steps

theorem GreenfieldCommunicationsDeliveredCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldCommunicationsDeliveredCurrentShapeIssuable world clearance)
    (hAuthority :
      GreenfieldCommunicationsDeliveredCurrentShapeWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ resolved,
      communicationsCompoundInstructionsIssuerAuthorized
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
      communicationsCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    routeBearingCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  cases hIssuable with
  | radioSingle hRadio =>
      rcases GreenfieldRadioCurrentShapeAdmissionSoundnessTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hRadio with
          ⟨resolved, hResolve, hReachable⟩
      exact ⟨resolved, hAuthorized, hResolve, hReachable⟩
  | transponderSingle hTransponder =>
      rcases GreenfieldTransponderDeliveredCurrentShapeReachableIssuanceTheorem
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hTransponder with
          ⟨resolved, hResolve, hReachable⟩
      exact ⟨resolved, hAuthorized, hResolve, hReachable⟩
  | compound hCompound =>
      rcases GreenfieldCommunicationsCompoundCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hCompound with
          ⟨resolved, hResolve, hReachable⟩
      exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

end Greenfield
end CertifiedAtc
