import CertifiedAtc.GreenfieldSetSquawkCurrentShape
import CertifiedAtc.GreenfieldConfirmSquawkCurrentShape
import CertifiedAtc.GreenfieldSquawkIdentCurrentShape
import CertifiedAtc.GreenfieldSquawkStandbyCurrentShape
import CertifiedAtc.GreenfieldSquawkNormalCurrentShape
import CertifiedAtc.GreenfieldStopSquawkCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldTransponderDeliveredCurrentShape` packages the delivered
current-shape transponder surface behind one source-level theorem boundary.

The delivered slice is:

- single-step `SetSquawk`
- single-step `ConfirmSquawk`
- single-step `SquawkIdent`
- single-step `SquawkStandby`
- single-step `SquawkNormal`
- single-step `StopSquawk`

This module does not widen the broader surveillance family. It packages the
already-delivered single-step current-shape slices so later widening can start
from one honest closure point.
-/

def GreenfieldTransponderDeliveredCurrentShapeInstruction : AtcInstruction → Prop
  | .setSquawk _ _ => True
  | .confirmSquawk _ _ => True
  | .squawkIdent _ => True
  | .squawkStandby _ => True
  | .squawkNormal _ _ => True
  | .stopSquawk _ _ => True
  | _ => False

def transponderDeliveredCurrentShapeInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | .setSquawk _ _ => some currentShapeSetSquawkAuthorityGrant
  | .confirmSquawk _ _ => some currentShapeConfirmSquawkAuthorityGrant
  | .squawkIdent _ => some currentShapeSquawkIdentAuthorityGrant
  | .squawkStandby _ => some currentShapeSquawkStandbyAuthorityGrant
  | .squawkNormal _ _ => some currentShapeSquawkNormalAuthorityGrant
  | .stopSquawk _ _ => some currentShapeStopSquawkAuthorityGrant
  | _ => none

def transponderDeliveredCurrentShapeInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match transponderDeliveredCurrentShapeInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def transponderDeliveredCurrentShapeInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      transponderDeliveredCurrentShapeInstructionIssuerAuthorized view controller instruction &&
        transponderDeliveredCurrentShapeInstructionsIssuerAuthorized view controller tail

def GreenfieldTransponderDeliveredCurrentShapeWorldAuthorized
    (world : ScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps,
    match transponderDeliveredCurrentShapeInstructionRequiredAuthorityGrant? instruction with
    | none => True
    | some grant => WorldControllerHasGrant world controller grant

theorem transponderDeliveredCurrentShapeInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : ScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : ScopedExtractionWellFormed world)
    (hMapped : transponderDeliveredCurrentShapeInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world controller grant) :
    transponderDeliveredCurrentShapeInstructionIssuerAuthorized
      (extractCompileView world)
      controller
      instruction = true := by
  simp [transponderDeliveredCurrentShapeInstructionIssuerAuthorized, hMapped]
  exact controllerHasAuthorityGrant_of_worldControllerHasGrant hWf hGrant

theorem transponderDeliveredCurrentShapeInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : ScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      ScopedExtractionWellFormed world →
      GreenfieldTransponderDeliveredCurrentShapeWorldAuthorized world controller steps →
        transponderDeliveredCurrentShapeInstructionsIssuerAuthorized
          (extractCompileView world)
          controller
          steps = true := by
  intro world controller steps hWf hAuth
  induction steps with
  | nil =>
      simp [transponderDeliveredCurrentShapeInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHeadAuth := hAuth head (by simp)
      have hTailAuth : GreenfieldTransponderDeliveredCurrentShapeWorldAuthorized world controller tail := by
        intro instruction hMem
        exact hAuth instruction (by simp [hMem])
      cases hGrant : transponderDeliveredCurrentShapeInstructionRequiredAuthorityGrant? head with
      | none =>
          simp [transponderDeliveredCurrentShapeInstructionsIssuerAuthorized,
            transponderDeliveredCurrentShapeInstructionIssuerAuthorized, hGrant, ih hTailAuth]
      | some grant =>
          have hHeadGrant : WorldControllerHasGrant world controller grant := by
            simpa [hGrant] using hHeadAuth
          have hHeadOk :
              transponderDeliveredCurrentShapeInstructionIssuerAuthorized
                (extractCompileView world)
                controller
                head = true :=
            transponderDeliveredCurrentShapeInstructionIssuerAuthorized_eq_true_of_worldAuthorized
              (world := world)
              (controller := controller)
              (instruction := head)
              (grant := grant)
              hWf
              hGrant
              hHeadGrant
          simp [transponderDeliveredCurrentShapeInstructionsIssuerAuthorized, hHeadOk, ih hTailAuth]

inductive GreenfieldTransponderDeliveredCurrentShapeIssuable :
    StructuredClearance → Prop
  | setSquawk
      {clearance : StructuredClearance}
      (hIssuable : SetSquawkCurrentShapeIssuable clearance) :
      GreenfieldTransponderDeliveredCurrentShapeIssuable clearance
  | confirmSquawk
      {clearance : StructuredClearance}
      (hIssuable : ConfirmSquawkCurrentShapeIssuable clearance) :
      GreenfieldTransponderDeliveredCurrentShapeIssuable clearance
  | squawkIdent
      {clearance : StructuredClearance}
      (hIssuable : SquawkIdentCurrentShapeIssuable clearance) :
      GreenfieldTransponderDeliveredCurrentShapeIssuable clearance
  | squawkStandby
      {clearance : StructuredClearance}
      (hIssuable : SquawkStandbyCurrentShapeIssuable clearance) :
      GreenfieldTransponderDeliveredCurrentShapeIssuable clearance
  | squawkNormal
      {clearance : StructuredClearance}
      (hIssuable : SquawkNormalCurrentShapeIssuable clearance) :
      GreenfieldTransponderDeliveredCurrentShapeIssuable clearance
  | stopSquawk
      {clearance : StructuredClearance}
      (hIssuable : StopSquawkCurrentShapeIssuable clearance) :
      GreenfieldTransponderDeliveredCurrentShapeIssuable clearance

theorem GreenfieldTransponderDeliveredCurrentShapeReachableIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldTransponderDeliveredCurrentShapeIssuable clearance) :
    ∃ resolved,
      ResolvesClearance
        world
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable with
  | setSquawk hSingle =>
      exact
        SetSquawkCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | confirmSquawk hSingle =>
      exact
        ConfirmSquawkCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | squawkIdent hSingle =>
      exact
        SquawkIdentCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | squawkStandby hSingle =>
      exact
        SquawkStandbyCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | squawkNormal hSingle =>
      exact
        SquawkNormalCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | stopSquawk hSingle =>
      exact
        StopSquawkCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle

theorem GreenfieldTransponderDeliveredCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldTransponderDeliveredCurrentShapeIssuable clearance)
    (hAuthority :
      GreenfieldTransponderDeliveredCurrentShapeWorldAuthorized
        compileWorld
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ resolved,
      transponderDeliveredCurrentShapeInstructionsIssuerAuthorized
        (extractCompileView compileWorld)
        clearance.issuedBy
        (structuredInstructions clearance) = true ∧
      ResolvesClearance
        resolutionWorld
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      transponderDeliveredCurrentShapeInstructionsIssuerAuthorized
        (extractCompileView compileWorld)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    transponderDeliveredCurrentShapeInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := compileWorld)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GreenfieldTransponderDeliveredCurrentShapeReachableIssuanceTheorem
      (world := resolutionWorld)
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
