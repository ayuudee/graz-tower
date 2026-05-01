import CertifiedAtc.GreenfieldRouteBearingCompound

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRouteBearingCurrentShape` packages the current end-to-end
route-bearing greenfield issuance surface.

Earlier modules prove the single-step and compound cases separately. This
module turns that into one source-level predicate over `StructuredClearance`
plus one end-to-end theorem:

- the clearance is in the currently supported Phase A route-bearing surface
- extracted-world well-formedness, authority, and resolution readiness hold
  for that source clearance
- therefore there exists a resolved clearance that can be admitted into the
  reachable resolved set

This is the cleanest current-shape closure point for the current graph-backed
published-procedure model before any future widening past the
one-primary-plus-immediate-adjunct compound surface.
-/

inductive GreenfieldRouteBearingCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | single
      {clearance : StructuredClearance}
      {instruction : AtcInstruction}
      (hContent : clearance.content = .single instruction)
      (hIssued : GreenfieldRouteBearingAdmissibleInstruction instruction)
      (hReady : RouteBearingInstructionResolutionReady world instruction)
      (hDomain : clearance.domain = greenfieldRouteBearingAdmissibleDomain instruction)
      (hCondition : clearance.condition = none)
      (hAuthority :
        match greenfieldRouteBearingRequiredAuthorityGrant? instruction with
        | none => True
        | some grant =>
            WorldControllerHasGrant world.toScopedAviationWorld clearance.issuedBy grant) :
      GreenfieldRouteBearingCurrentShapeIssuable world clearance
  | compound
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {primary : AtcInstruction}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps : content.steps = primary :: tail)
      (hReady : GreenfieldRouteBearingCompoundReady world primary tail)
      (hDomain : clearance.domain = greenfieldRouteBearingAdmissibleDomain primary)
      (hCondition : clearance.condition = none)
      (hAuthority :
        RouteBearingCompoundWorldAuthorized world clearance.issuedBy (primary :: tail)) :
      GreenfieldRouteBearingCurrentShapeIssuable world clearance

theorem GreenfieldRouteBearingCurrentShapeReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRouteBearingCurrentShapeIssuable world clearance) :
    ∃ finalState, ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable
  case single instruction hContent hIssued hReady hDomain hCondition hAuthority =>
      rcases GreenfieldRouteBearingCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          (instruction := instruction)
          hWf
          hReach
          hFresh
          hIssued
          hReady
          hContent
          hDomain
          hCondition
          hAuthority with ⟨finalState, resolved, _, hResolve, hReachResolved⟩
      exact ⟨finalState, resolved, hResolve, hReachResolved⟩
  case compound content primary tail hContent hSteps hReady hDomain hCondition hAuthority =>
      rcases GreenfieldRouteBearingCompoundCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          (content := content)
          (primary := primary)
          (tail := tail)
          hWf
          hReach
          hFresh
          hContent
          hSteps
          hReady
          hDomain
          hCondition
          hAuthority with ⟨finalState, resolved, _, hResolve, hReachResolved⟩
      exact ⟨finalState, resolved, hResolve, hReachResolved⟩

end Greenfield
end CertifiedAtc
