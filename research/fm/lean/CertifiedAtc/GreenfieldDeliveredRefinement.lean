import CertifiedAtc.ScopedSafety
import CertifiedAtc.ScopedModes
import CertifiedAtc.GreenfieldGroundMovementDeliveredCurrentShape
import CertifiedAtc.GreenfieldRouteControlDeliveredCurrentShape
import CertifiedAtc.GreenfieldAirModifierCurrentShape
import CertifiedAtc.GreenfieldRouteAdjacentAuthority
import CertifiedAtc.GreenfieldRouteBearingCurrentShape
import CertifiedAtc.GreenfieldRouteBearingAdmission
import CertifiedAtc.GreenfieldRouteBearingCompound
import CertifiedAtc.GreenfieldAirspaceWorldBackedDeliveredCurrentShape
import CertifiedAtc.GreenfieldRadioCurrentShape
import CertifiedAtc.GreenfieldTransponderDeliveredCurrentShape
import CertifiedAtc.GreenfieldCommunicationsDeliveredCurrentShape
import CertifiedAtc.GreenfieldRunwayDeliveredCurrentShape
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
  | runwayDelivered
  | runwayExpanded
  deriving DecidableEq, Repr

def deliveredBranchClosureKind : DeliveredBranch → DeliveredClosureKind
  | .scopedCore => .scopedCoreComplete
  | .groundMovement => .worldBackedComplete
  | .routeBearing => .worldBackedComplete
  | .routeAdjacent => .currentShapeComplete
  | .airspaceWorldBacked => .worldBackedComplete
  | .routeControl => .currentShapeComplete
  | .airModifier => .currentShapeComplete
  | .radio => .currentShapeComplete
  | .transponder => .currentShapeComplete
  | .communications => .currentShapeComplete
  | .runwayDelivered => .currentShapeComplete
  | .runwayExpanded => .currentShapeComplete

@[simp] theorem deliveredBranchClosureKind_scopedCore :
    deliveredBranchClosureKind .scopedCore = .scopedCoreComplete := rfl
@[simp] theorem deliveredBranchClosureKind_groundMovement :
    deliveredBranchClosureKind .groundMovement = .worldBackedComplete := rfl
@[simp] theorem deliveredBranchClosureKind_routeBearing :
    deliveredBranchClosureKind .routeBearing = .worldBackedComplete := rfl
@[simp] theorem deliveredBranchClosureKind_routeAdjacent :
    deliveredBranchClosureKind .routeAdjacent = .currentShapeComplete := rfl
@[simp] theorem deliveredBranchClosureKind_airspaceWorldBacked :
    deliveredBranchClosureKind .airspaceWorldBacked = .worldBackedComplete := rfl
@[simp] theorem deliveredBranchClosureKind_routeControl :
    deliveredBranchClosureKind .routeControl = .currentShapeComplete := rfl
@[simp] theorem deliveredBranchClosureKind_airModifier :
    deliveredBranchClosureKind .airModifier = .currentShapeComplete := rfl
@[simp] theorem deliveredBranchClosureKind_radio :
    deliveredBranchClosureKind .radio = .currentShapeComplete := rfl
@[simp] theorem deliveredBranchClosureKind_transponder :
    deliveredBranchClosureKind .transponder = .currentShapeComplete := rfl
@[simp] theorem deliveredBranchClosureKind_communications :
    deliveredBranchClosureKind .communications = .currentShapeComplete := rfl
@[simp] theorem deliveredBranchClosureKind_runwayDelivered :
    deliveredBranchClosureKind .runwayDelivered = .currentShapeComplete := rfl
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
  @GreenfieldRouteAdjacentCurrentShapeReachableIssuanceTheorem

abbrev RouteAdjacentDeliveredAuthorizedRefinementTheorem :=
  @RouteAdjacentAuthorityCurrentShapeIssuanceTheorem

abbrev AirspaceWorldBackedDeliveredReachableRefinementTheorem :=
  @GreenfieldAirspaceWorldBackedDeliveredCurrentShapeReachableIssuanceTheorem

abbrev AirspaceWorldBackedDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldAirspaceWorldBackedDeliveredCurrentShapeAuthorizedIssuanceTheorem

abbrev RouteControlDeliveredReachableRefinementTheorem :=
  @GreenfieldRouteControlDeliveredCurrentShapeReachableIssuanceTheorem

abbrev RouteControlDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldRouteControlDeliveredCurrentShapeAuthorizedIssuanceTheorem

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

abbrev CommunicationsDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldCommunicationsDeliveredCurrentShapeAuthorizedIssuanceTheorem

abbrev RunwayDeliveredReachableRefinementTheorem :=
  @GreenfieldRunwayDeliveredCurrentShapeReachableIssuanceTheorem

abbrev RunwayDeliveredAuthorizedRefinementTheorem :=
  @GreenfieldRunwayDeliveredCurrentShapeAuthorizedIssuanceTheorem

abbrev RunwayExpandedReachableRefinementTheorem :=
  @GreenfieldRunwayExpandedCurrentShapeReachableIssuanceTheorem

end Greenfield
end CertifiedAtc
