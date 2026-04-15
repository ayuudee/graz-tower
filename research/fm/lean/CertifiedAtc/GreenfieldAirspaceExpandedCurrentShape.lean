import CertifiedAtc.GreenfieldAirspaceExpandedCompound

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldAirspaceExpandedCurrentShape` packages the broadened current-shape
airspace-clearance surface behind one source-level theorem boundary.

The phase-4 delivered slice is:

- single-step `RemainOutsideControlledAirspace`
- single-step `ClearedToEnterControlZone`
- single-step `SpecialVfrClearance`
- a first narrow compound slice for all three families

This means the whole current Kotlin airspace-clearance family now has a
single-step slice plus a first narrow compound slice on the greenfield
boundary.
-/

inductive GreenfieldAirspaceExpandedCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | delivered
      {clearance : StructuredClearance}
      (hIssuable : GreenfieldAirspaceDeliveredCurrentShapeIssuable world clearance) :
      GreenfieldAirspaceExpandedCurrentShapeIssuable world clearance
  | remainOutsideCompound
      {clearance : StructuredClearance}
      (hIssuable : RemainOutsideAirspaceCompoundCurrentShapeIssuable world clearance) :
      GreenfieldAirspaceExpandedCurrentShapeIssuable world clearance

abbrev GreenfieldAirspaceExpandedCurrentShapeWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  GreenfieldAirspaceCompoundWorldAuthorized world controller steps

theorem GreenfieldAirspaceExpandedCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldAirspaceExpandedCurrentShapeIssuable world clearance)
    (hAuthority :
      GreenfieldAirspaceExpandedCurrentShapeWorldAuthorized
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
  cases hIssuable with
  | delivered hDelivered =>
      exact
        GreenfieldAirspaceDeliveredCurrentShapeAuthorizedIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hWf
          hReach
          hFresh
          hDelivered
          hAuthority
  | remainOutsideCompound hRemainOutside =>
      exact
        RemainOutsideAirspaceCompoundCurrentShapeAuthorizedIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hWf
          hReach
          hFresh
          hRemainOutside
          hAuthority

end Greenfield
end CertifiedAtc
