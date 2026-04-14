import CertifiedAtc.GreenfieldContinueApproachCompound
import CertifiedAtc.GreenfieldExtendDownwindCompound
import CertifiedAtc.GreenfieldOrbitCompound

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRouteAdjacentCurrentShape` packages the delivered Phase B
route-adjacent surface behind one source-level theorem boundary.

This package remains intentionally authority-light even though the delivered
Phase B surface now also has a separate current-shape authority layer in
`GreenfieldRouteAdjacentAuthority`. What this module packages is the current
reusable execution-side claim:

- the source clearance lies in the presently delivered Phase B surface
- therefore there exists a resolved clearance that admits into
  `ReachableResolvedSet`

That gives the widening track one honest execution-side closure point for the
whole delivered Phase B set before any later widening beyond the current
slices.
-/

inductive GreenfieldRouteAdjacentCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | continueApproachSingle
      {clearance : StructuredClearance}
      (hIssuable : ContinueApproachCurrentShapeIssuable clearance) :
      GreenfieldRouteAdjacentCurrentShapeIssuable world clearance
  | continueApproachCompound
      {clearance : StructuredClearance}
      (hIssuable : ContinueApproachCompoundCurrentShapeIssuable world clearance) :
      GreenfieldRouteAdjacentCurrentShapeIssuable world clearance
  | extendDownwindSingle
      {clearance : StructuredClearance}
      (hIssuable : ExtendDownwindCurrentShapeIssuable clearance) :
      GreenfieldRouteAdjacentCurrentShapeIssuable world clearance
  | extendDownwindCompound
      {clearance : StructuredClearance}
      (hIssuable : ExtendDownwindCompoundCurrentShapeIssuable world clearance) :
      GreenfieldRouteAdjacentCurrentShapeIssuable world clearance
  | orbitSingle
      {clearance : StructuredClearance}
      (hIssuable : OrbitCurrentShapeIssuable clearance) :
      GreenfieldRouteAdjacentCurrentShapeIssuable world clearance
  | orbitCompound
      {clearance : StructuredClearance}
      (hIssuable : OrbitCompoundCurrentShapeIssuable world clearance) :
      GreenfieldRouteAdjacentCurrentShapeIssuable world clearance

theorem GreenfieldRouteAdjacentCurrentShapeReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRouteAdjacentCurrentShapeIssuable world clearance) :
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
  | continueApproachSingle hIssuable =>
      exact
        ContinueApproachCurrentShapeIssuanceTheorem
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hIssuable
  | continueApproachCompound hIssuable =>
      exact
        ContinueApproachCompoundCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hIssuable
  | extendDownwindSingle hIssuable =>
      exact
        ExtendDownwindCurrentShapeIssuanceTheorem
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hIssuable
  | extendDownwindCompound hIssuable =>
      exact
        ExtendDownwindCompoundCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hIssuable
  | orbitSingle hIssuable =>
      exact
        OrbitCurrentShapeIssuanceTheorem
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hIssuable
  | orbitCompound hIssuable =>
      exact
        OrbitCompoundCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hIssuable

end Greenfield
end CertifiedAtc
