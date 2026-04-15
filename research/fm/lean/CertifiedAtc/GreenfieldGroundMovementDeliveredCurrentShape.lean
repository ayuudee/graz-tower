import CertifiedAtc.GreenfieldGroundMovementCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldGroundMovementDeliveredCurrentShape` packages the delivered broader
ground/surface movement branch behind one theorem boundary.

The delivered slice is:

- world-backed `TaxiTo`
- world-backed `HoldShortOf`
- world-backed `CrossRunway`
- current-shape `HoldPosition`
- the first narrow sequential compound layer over those same instructions

This matches the current runtime honestly: three families already have explicit
graph-backed resolution, while `HoldPosition` still rides the plain persistent
execution path.
-/

inductive GroundMovementDeliveredCurrentShapeIssuable
    (world : GroundMovementScopedAviationWorld)
    (initialState : ResolutionState) :
    StructuredClearance → Prop
  | single
      {clearance : StructuredClearance}
      (hIssuable : GroundMovementSingleCurrentShapeIssuable world initialState clearance) :
      GroundMovementDeliveredCurrentShapeIssuable world initialState clearance
  | compound
      {clearance : StructuredClearance}
      (hIssuable : GroundMovementCompoundCurrentShapeIssuable world initialState clearance) :
      GroundMovementDeliveredCurrentShapeIssuable world initialState clearance

abbrev GroundMovementDeliveredCurrentShapeWorldAuthorized
    (world : GroundMovementScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  GroundMovementWorldAuthorized world controller steps

theorem GroundMovementDeliveredCurrentShapeReachableIssuanceTheorem
    {world : GroundMovementScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GroundMovementDeliveredCurrentShapeIssuable world initialState clearance) :
    ∃ finalState, ∃ resolved,
      ResolvesClearance
        (GroundMovementScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable with
  | single hSingle =>
      exact
        GroundMovementSingleCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | compound hCompound =>
      exact
        GroundMovementCompoundCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hCompound

theorem GroundMovementDeliveredCurrentShapeAuthorizedIssuanceTheorem
    {world : GroundMovementScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : GroundMovementExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GroundMovementDeliveredCurrentShapeIssuable world initialState clearance)
    (hAuthority :
      GroundMovementDeliveredCurrentShapeWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ finalState, ∃ resolved,
      groundMovementInstructionsIssuerAuthorized
        (extractCompileView world.toScopedAviationWorld)
        clearance.issuedBy
        (structuredInstructions clearance) = true ∧
      ResolvesClearance
        (GroundMovementScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable with
  | single hSingle =>
      exact
        GroundMovementSingleCurrentShapeAuthorizedIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hWf
          hReach
          hFresh
          hSingle
          hAuthority
  | compound hCompound =>
      exact
        GroundMovementCompoundCurrentShapeAuthorizedIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hWf
          hReach
          hFresh
          hCompound
          hAuthority

end Greenfield
end CertifiedAtc
