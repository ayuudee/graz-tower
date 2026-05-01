import CertifiedAtc.ScopedSafety
import CertifiedAtc.ScopedModes
import CertifiedAtc.GreenfieldGroundMovementDeliveredCurrentShape
import CertifiedAtc.GreenfieldRouteControlWorldBackedDeliveredCurrentShape
import CertifiedAtc.GreenfieldAirModifierCurrentShape
import CertifiedAtc.GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShape
import CertifiedAtc.GreenfieldRouteBearingCurrentShape
import CertifiedAtc.GreenfieldRouteBearingAdmission
import CertifiedAtc.GreenfieldRouteBearingCompound
import CertifiedAtc.GreenfieldAirspaceWorldBackedDeliveredCurrentShape
import CertifiedAtc.GreenfieldRadioCurrentShape
import CertifiedAtc.GreenfieldTransponderDeliveredCurrentShape
import CertifiedAtc.GreenfieldCommunicationsExpandedCurrentShape
import CertifiedAtc.GreenfieldCommunicationsJurisdictionDeliveredCurrentShape
import CertifiedAtc.GreenfieldRunwayWorldBackedDeliveredCurrentShape
import CertifiedAtc.GreenfieldRunwayExpandedCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldDeliveredRefinement` is the proof-side registry for the delivered
Kotlin ↔ Lean parity boundary.

This module does not add new semantics. Its job is narrower:

- classify the delivered branches by closure kind
- point at the load-bearing theorem surfaces for each delivered branch
- give the FM docs one Lean module to cite when discussing refinement/drift
  enforcement instead of forcing future agents to rediscover the right module
  set by search

It stays intentionally explicit about branches whose top-level theorem surface
is still split between reachability and authority packaging.
-/

inductive DeliveredClosureKind where
  | scopedCoreComplete
  | currentShapeComplete
  | worldBackedComplete
  deriving DecidableEq, Repr

inductive DeliveredBranch where
  | scopedCore
  | groundMovement
  | routeBearing
  | routeAdjacent
  | airspaceWorldBacked
  | routeControl
  | airModifier
  | radio
  | transponder
  | communications
  | communicationsJurisdiction
  | runwayDelivered
  | runwayExpanded
  deriving DecidableEq, Repr

def deliveredBranchClosureKind : DeliveredBranch → DeliveredClosureKind
  | .scopedCore => .scopedCoreComplete
  | .groundMovement => .worldBackedComplete
  | .routeBearing => .worldBackedComplete
  | .routeAdjacent => .worldBackedComplete
  | .airspaceWorldBacked => .worldBackedComplete
  | .routeControl => .worldBackedComplete
  | .airModifier => .currentShapeComplete
  | .radio => .currentShapeComplete
  | .transponder => .currentShapeComplete
  | .communications => .currentShapeComplete
  | .communicationsJurisdiction => .worldBackedComplete
  | .runwayDelivered => .worldBackedComplete
  | .runwayExpanded => .currentShapeComplete

@[simp] theorem deliveredBranchClosureKind_scopedCore :
    deliveredBranchClosureKind .scopedCore = .scopedCoreComplete := rfl
@[simp] theorem deliveredBranchClosureKind_groundMovement :
    deliveredBranchClosureKind .groundMovement = .worldBackedComplete := rfl
@[simp] theorem deliveredBranchClosureKind_routeBearing :
    deliveredBranchClosureKind .routeBearing = .worldBackedComplete := rfl
@[simp] theorem deliveredBranchClosureKind_routeAdjacent :
    deliveredBranchClosureKind .routeAdjacent = .worldBackedComplete := rfl
@[simp] theorem deliveredBranchClosureKind_airspaceWorldBacked :
    deliveredBranchClosureKind .airspaceWorldBacked = .worldBackedComplete := rfl
@[simp] theorem deliveredBranchClosureKind_routeControl :
    deliveredBranchClosureKind .routeControl = .worldBackedComplete := rfl
@[simp] theorem deliveredBranchClosureKind_airModifier :
    deliveredBranchClosureKind .airModifier = .currentShapeComplete := rfl
@[simp] theorem deliveredBranchClosureKind_radio :
    deliveredBranchClosureKind .radio = .currentShapeComplete := rfl
@[simp] theorem deliveredBranchClosureKind_transponder :
    deliveredBranchClosureKind .transponder = .currentShapeComplete := rfl
@[simp] theorem deliveredBranchClosureKind_communications :
    deliveredBranchClosureKind .communications = .currentShapeComplete := rfl
@[simp] theorem deliveredBranchClosureKind_communicationsJurisdiction :
    deliveredBranchClosureKind .communicationsJurisdiction = .worldBackedComplete := rfl
@[simp] theorem deliveredBranchClosureKind_runwayDelivered :
    deliveredBranchClosureKind .runwayDelivered = .worldBackedComplete := rfl
@[simp] theorem deliveredBranchClosureKind_runwayExpanded :
    deliveredBranchClosureKind .runwayExpanded = .currentShapeComplete := rfl

abbrev ScopedCoreReachableSafetyRefinementTheorem :=
  @ScopedReachableSafetyTheorem

abbrev ScopedCoreFullBriefRefinementTheorem :=
  @FullBriefFallbackTheorem

abbrev GroundMovementDeliveredReachableRefinementTheorem :=
  @GroundMovementDeliveredCurrentShapeReachableIssuanceTheorem

abbrev GroundMovementDeliveredAuthorizedRefinementTheorem :=
  @GroundMovementDeliveredCurrentShapeAuthorizedIssuanceTheorem

abbrev RouteBearingDeliveredReachableRefinementTheorem :=
  @GreenfieldRouteBearingCurrentShapeReachableIssuanceTheorem

abbrev RouteBearingDeliveredSingleAuthorizedRefinementTheorem :=
  @GreenfieldRouteBearingCurrentShapeIssuanceTheorem

abbrev RouteBearingDeliveredCompoundAuthorizedRefinementTheorem :=
  @GreenfieldRouteBearingCompoundCurrentShapeIssuanceTheorem

abbrev RouteAdjacentDeliveredReachableRefinementTheorem :=
  @GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShapeReachableIssuanceTheorem

abbrev RouteAdjacentDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldRouteAdjacentWorldBackedDeliveredCurrentShapeAuthorizedIssuanceTheorem

abbrev AirspaceWorldBackedDeliveredReachableRefinementTheorem :=
  @GreenfieldAirspaceWorldBackedDeliveredCurrentShapeReachableIssuanceTheorem

abbrev AirspaceWorldBackedDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldAirspaceWorldBackedDeliveredCurrentShapeAuthorizedIssuanceTheorem

abbrev RouteControlDeliveredReachableRefinementTheorem :=
  @GreenfieldRouteControlWorldBackedDeliveredCurrentShapeReachableIssuanceTheorem

abbrev RouteControlDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldRouteControlWorldBackedDeliveredCurrentShapeAuthorizedIssuanceTheorem

abbrev AirModifierDeliveredReachableRefinementTheorem :=
  @GreenfieldAirModifierCurrentShapeReachableIssuanceTheorem

abbrev AirModifierDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldAirModifierCurrentShapeAuthorizedIssuanceTheorem

abbrev RadioDeliveredReachableRefinementTheorem :=
  @GreenfieldRadioCurrentShapeAdmissionSoundnessTheorem

abbrev RadioDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldRadioCurrentShapeAuthorizedIssuanceTheorem

abbrev TransponderDeliveredReachableRefinementTheorem :=
  @GreenfieldTransponderDeliveredCurrentShapeReachableIssuanceTheorem

abbrev TransponderDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldTransponderDeliveredCurrentShapeAuthorizedIssuanceTheorem

abbrev CommunicationsDeliveredReachableRefinementTheorem :=
  @GreenfieldCommunicationsExpandedCurrentShapeReachableIssuanceTheorem

abbrev CommunicationsDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldCommunicationsExpandedCurrentShapeAuthorizedIssuanceTheorem

abbrev CommunicationsJurisdictionDeliveredReachableRefinementTheorem :=
  @GreenfieldCommunicationsJurisdictionDeliveredCurrentShapeReachableIssuanceTheorem

abbrev CommunicationsJurisdictionDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldCommunicationsJurisdictionDeliveredCurrentShapeAuthorizedIssuanceTheorem

abbrev RunwayDeliveredReachableRefinementTheorem :=
  @GreenfieldRunwayWorldBackedDeliveredCurrentShapeReachableIssuanceTheorem

abbrev RunwayDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldRunwayWorldBackedDeliveredCurrentShapeAuthorizedIssuanceTheorem

abbrev RunwayExpandedReachableRefinementTheorem :=
  @GreenfieldRunwayExpandedCurrentShapeReachableIssuanceTheorem

end Greenfield
end CertifiedAtc
