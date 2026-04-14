import CertifiedAtc.RouteBearingResolutionBridge
import CertifiedAtc.GreenfieldRouteBearing
import CertifiedAtc.GreenfieldReachability

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRouteBearingAdmission` packages the first honest route-bearing
admission surface above the resolved-execution boundary.

This module is intentionally not a legacy-bridge issuing layer. It proves a
current-shape greenfield story for the bridged route-bearing subset:

- `ClearedTo`
- published `HoldAt`
- non-circling `ClearedApproach`
- `JoinCircuit`

For that subset, the widened extracted world now yields resolved clearances,
those clearances can be admitted into the reachable resolved set, and the
authority-gated side of the story is explicit against the widened compile view.
-/

def GreenfieldRouteBearingAdmissibleInstruction : AtcInstruction → Prop
  | .clearedTo _ _ _ => True
  | .holdAt _ (.published _) _ => True
  | .clearedApproach _ _ _ none => True
  | .joinCircuit _ _ _ _ => True
  | _ => False

def greenfieldRouteBearingAdmissibleDomain : AtcInstruction → ClearanceDomain
  | .joinCircuit _ _ _ _ => .runway
  | _ => .route

def greenfieldRouteBearingRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | .clearedTo _ _ _ =>
      none
  | .holdAt _ (.published _) _ =>
      some { entityType := .holdingPattern, operation := .hold }
  | .clearedApproach _ _ _ none =>
      some { entityType := .instrumentApproach, operation := .approachClearance }
  | .joinCircuit _ _ _ _ =>
      some { entityType := .circuitProcedure, operation := .circuit }
  | _ =>
      none

def greenfieldRouteBearingIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match greenfieldRouteBearingRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

theorem greenfieldRouteBearingIssuerAuthorized_eq_true_of_unmapped
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instruction : AtcInstruction}
    (hUnmapped : greenfieldRouteBearingRequiredAuthorityGrant? instruction = none) :
    greenfieldRouteBearingIssuerAuthorized view controller instruction = true := by
  simp [greenfieldRouteBearingIssuerAuthorized, hUnmapped]

theorem GreenfieldRouteBearingAuthorityGatedAdmissionTheorem
    {world : RouteBearingScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMapped : greenfieldRouteBearingRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world.toScopedAviationWorld controller grant) :
    greenfieldRouteBearingIssuerAuthorized
        (extractRouteBearingCompileView world)
        controller
        instruction = true := by
  simp [greenfieldRouteBearingIssuerAuthorized, hMapped]
  change
    controllerHasAuthorityGrant
      (extractCompileView world.toScopedAviationWorld)
      controller
      grant = true
  exact
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf.baseWellFormed
      hGrant

theorem resolvesSingleGreenfieldRouteBearingClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {instruction : AtcInstruction}
    (hIssued : GreenfieldRouteBearingAdmissibleInstruction instruction)
    (hReady : RouteBearingInstructionResolutionReady world instruction)
    (hContent : clearance.content = .single instruction)
    (hDomain : clearance.domain = greenfieldRouteBearingAdmissibleDomain instruction)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  cases instruction with
  | clearedTo target clearanceLimit route =>
      exact resolvesSingleClearedToClearance_of_ready
        (world := world)
        (initialState := initialState)
        (clearance := clearance)
        (target := target)
        (clearanceLimit := clearanceLimit)
        (route := route)
        hReady
        hContent
        (by simpa [greenfieldRouteBearingAdmissibleDomain] using hDomain)
        hCondition
  | holdAt target hold efc =>
      cases hold with
      | published fixId =>
          exact resolvesSinglePublishedHoldAtClearance_of_ready
            (world := world)
            (initialState := initialState)
            (clearance := clearance)
            (target := target)
            (fixId := fixId)
            (efc := efc)
            hReady
            hContent
            (by simpa [greenfieldRouteBearingAdmissibleDomain] using hDomain)
            hCondition
      | inboundTrack fixId inboundDegreesMagnetic turnDirection legTime legDistance =>
          cases hIssued
  | clearedApproach target approachType runway circlingRunway =>
      cases circlingRunway with
      | none =>
          exact resolvesSingleNonCirclingClearedApproachClearance_of_ready
            (world := world)
            (initialState := initialState)
            (clearance := clearance)
            (target := target)
            (approachType := approachType)
            (runway := runway)
            hReady
            hContent
            (by simpa [greenfieldRouteBearingAdmissibleDomain] using hDomain)
            hCondition
      | some circlingRunway =>
          cases hIssued
  | joinCircuit target direction joinType runway =>
      exact resolvesSingleJoinCircuitClearance_of_ready
        (world := world)
        (initialState := initialState)
        (clearance := clearance)
        (target := target)
        (direction := direction)
        (joinType := joinType)
        (runway := runway)
        hReady
        hContent
        (by simpa [greenfieldRouteBearingAdmissibleDomain] using hDomain)
        hCondition
  | _ =>
      cases hIssued

theorem GreenfieldRouteBearingAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {instruction : AtcInstruction}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssued : GreenfieldRouteBearingAdmissibleInstruction instruction)
    (hReady : RouteBearingInstructionResolutionReady world instruction)
    (hContent : clearance.content = .single instruction)
    (hDomain : clearance.domain = greenfieldRouteBearingAdmissibleDomain instruction)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  rcases resolvesSingleGreenfieldRouteBearingClearance_of_ready
      (world := world)
      (initialState := initialState)
      (clearance := clearance)
      (instruction := instruction)
      hIssued
      hReady
      hContent
      hDomain
      hCondition with ⟨resolved, hResolve⟩
  have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
    simpa [hResolve.sourceEq] using hFresh
  refine ⟨resolved, hResolve, ?_⟩
  exact ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve

theorem GreenfieldRouteBearingCurrentShapeIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {instruction : AtcInstruction}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssued : GreenfieldRouteBearingAdmissibleInstruction instruction)
    (hReady : RouteBearingInstructionResolutionReady world instruction)
    (hContent : clearance.content = .single instruction)
    (hDomain : clearance.domain = greenfieldRouteBearingAdmissibleDomain instruction)
    (hCondition : clearance.condition = none)
    (hAuthority :
      match greenfieldRouteBearingRequiredAuthorityGrant? instruction with
      | none => True
      | some grant =>
          WorldControllerHasGrant world.toScopedAviationWorld clearance.issuedBy grant) :
    ∃ resolved,
      greenfieldRouteBearingIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        instruction = true ∧
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      greenfieldRouteBearingIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        instruction = true := by
    cases hMapped : greenfieldRouteBearingRequiredAuthorityGrant? instruction with
    | none =>
        exact greenfieldRouteBearingIssuerAuthorized_eq_true_of_unmapped hMapped
    | some grant =>
        exact
          GreenfieldRouteBearingAuthorityGatedAdmissionTheorem
            (world := world)
            (controller := clearance.issuedBy)
            (instruction := instruction)
            (grant := grant)
            hWf
            hMapped
            (by simpa [hMapped] using hAuthority)
  rcases GreenfieldRouteBearingAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      (instruction := instruction)
      hReach
      hFresh
      hIssued
      hReady
      hContent
      hDomain
      hCondition with ⟨resolved, hResolve, hReachResolved⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachResolved⟩

end Greenfield
end CertifiedAtc
