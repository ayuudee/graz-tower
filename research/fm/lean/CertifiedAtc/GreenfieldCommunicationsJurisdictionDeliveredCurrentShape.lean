import CertifiedAtc.GreenfieldCommunicationsExpandedCurrentShape
import CertifiedAtc.GreenfieldRadioJurisdictionWorldBacked

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldCommunicationsJurisdictionDeliveredCurrentShape` packages the
delivered communications/surveillance surface together with the first
world-backed published-handoff radio widening.

This closes the current model for:

- the already-delivered immediate radio/transponder surface
- world-backed single-step published-handoff `ContactFrequency`
- world-backed single-step published-handoff `MonitorFrequency`
- execution-layer mixed radio/transponder consequences under published handoff

It stays intentionally bounded:

- published handoffs are still the only coordination/jurisdiction fact
- transponder behavior remains on the delivered current immediate model
- no broader controller-jurisdiction matrix is claimed here
-/

inductive GreenfieldCommunicationsJurisdictionDeliveredCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld)
    (initialState : ResolutionState) :
    StructuredClearance → Prop
  | delivered
      {clearance : StructuredClearance}
      (hIssuable : GreenfieldCommunicationsExpandedCurrentShapeIssuable world clearance) :
      GreenfieldCommunicationsJurisdictionDeliveredCurrentShapeIssuable
        world
        initialState
        clearance
  | publishedHandoffRadio
      {clearance : StructuredClearance}
      (hIssuable :
        GreenfieldRadioJurisdictionWorldBackedIssuable
          world
          initialState
          clearance) :
      GreenfieldCommunicationsJurisdictionDeliveredCurrentShapeIssuable
        world
        initialState
        clearance

abbrev GreenfieldCommunicationsJurisdictionDeliveredCurrentShapeWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  CommunicationsCompoundWorldAuthorized world controller steps

theorem GreenfieldCommunicationsJurisdictionDeliveredCurrentShapeReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldCommunicationsJurisdictionDeliveredCurrentShapeIssuable
        world
        initialState
        clearance) :
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
  | delivered hDelivered =>
      exact
        GreenfieldCommunicationsExpandedCurrentShapeReachableIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hDelivered
  | publishedHandoffRadio hRadio =>
      exact
        GreenfieldRadioJurisdictionWorldBackedReachableIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hRadio

theorem GreenfieldCommunicationsJurisdictionDeliveredCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldCommunicationsJurisdictionDeliveredCurrentShapeIssuable
        world
        initialState
        clearance)
    (hAuthority :
      GreenfieldCommunicationsJurisdictionDeliveredCurrentShapeWorldAuthorized
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
  rcases GreenfieldCommunicationsJurisdictionDeliveredCurrentShapeReachableIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachResolved⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachResolved⟩

end Greenfield
end CertifiedAtc
