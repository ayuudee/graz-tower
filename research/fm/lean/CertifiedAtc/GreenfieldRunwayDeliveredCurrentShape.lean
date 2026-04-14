import CertifiedAtc.GreenfieldLineUpAndWaitCurrentShape
import CertifiedAtc.GreenfieldTakeoffCurrentShape
import CertifiedAtc.GreenfieldLandingCurrentShape
import CertifiedAtc.GreenfieldTouchAndGoCurrentShape
import CertifiedAtc.GreenfieldLowApproachCurrentShape
import CertifiedAtc.GreenfieldGoAroundCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRunwayDeliveredCurrentShape` packages the delivered current-shape
runway-operation surface behind one source-level theorem boundary.

The delivered slice is:

- single-step `LineUpAndWait`
- single-step `ClearedForTakeoff`
- single-step `ClearedToLand`
- single-step `ClearedTouchAndGo`
- single-step `ClearedLowApproach`
- single-step `GoAround`

This module does not widen the broader runway family. It packages the already-
delivered single-step current-shape slices so later widening can start from one
honest closure point.
-/

def GreenfieldRunwayDeliveredCurrentShapeInstruction : AtcInstruction → Prop
  | .lineUpAndWait _ _ => True
  | .clearedForTakeoff _ _ => True
  | .clearedToLand _ _ => True
  | .clearedTouchAndGo _ _ => True
  | .clearedLowApproach _ _ => True
  | .goAround _ => True
  | _ => False

def runwayDeliveredCurrentShapeInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | .lineUpAndWait _ _ => some currentShapeLineUpAndWaitAuthorityGrant
  | .clearedForTakeoff _ _ => some currentShapeTakeoffAuthorityGrant
  | .clearedToLand _ _ => some currentShapeLandingAuthorityGrant
  | .clearedTouchAndGo _ _ => some currentShapeTouchAndGoAuthorityGrant
  | .clearedLowApproach _ _ => some currentShapeLowApproachAuthorityGrant
  | .goAround _ => some currentShapeGoAroundAuthorityGrant
  | _ => none

def runwayDeliveredCurrentShapeInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match runwayDeliveredCurrentShapeInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def runwayDeliveredCurrentShapeInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      runwayDeliveredCurrentShapeInstructionIssuerAuthorized view controller instruction &&
        runwayDeliveredCurrentShapeInstructionsIssuerAuthorized view controller tail

def GreenfieldRunwayDeliveredCurrentShapeWorldAuthorized
    (world : ScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps,
    match runwayDeliveredCurrentShapeInstructionRequiredAuthorityGrant? instruction with
    | none => True
    | some grant => WorldControllerHasGrant world controller grant

theorem runwayDeliveredCurrentShapeInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : ScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : ScopedExtractionWellFormed world)
    (hMapped : runwayDeliveredCurrentShapeInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world controller grant) :
    runwayDeliveredCurrentShapeInstructionIssuerAuthorized
      (extractCompileView world)
      controller
      instruction = true := by
  simp [runwayDeliveredCurrentShapeInstructionIssuerAuthorized, hMapped]
  exact controllerHasAuthorityGrant_of_worldControllerHasGrant hWf hGrant

theorem runwayDeliveredCurrentShapeInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : ScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      ScopedExtractionWellFormed world →
      GreenfieldRunwayDeliveredCurrentShapeWorldAuthorized world controller steps →
        runwayDeliveredCurrentShapeInstructionsIssuerAuthorized
          (extractCompileView world)
          controller
          steps = true := by
  intro world controller steps hWf hAuth
  induction steps with
  | nil =>
      simp [runwayDeliveredCurrentShapeInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHeadAuth := hAuth head (by simp)
      have hTailAuth : GreenfieldRunwayDeliveredCurrentShapeWorldAuthorized world controller tail := by
        intro instruction hMem
        exact hAuth instruction (by simp [hMem])
      cases hGrant : runwayDeliveredCurrentShapeInstructionRequiredAuthorityGrant? head with
      | none =>
          simp [runwayDeliveredCurrentShapeInstructionsIssuerAuthorized,
            runwayDeliveredCurrentShapeInstructionIssuerAuthorized, hGrant, ih hTailAuth]
      | some grant =>
          have hHeadGrant : WorldControllerHasGrant world controller grant := by
            simpa [hGrant] using hHeadAuth
          have hHeadOk :
              runwayDeliveredCurrentShapeInstructionIssuerAuthorized
                (extractCompileView world)
                controller
                head = true :=
            runwayDeliveredCurrentShapeInstructionIssuerAuthorized_eq_true_of_worldAuthorized
              (world := world)
              (controller := controller)
              (instruction := head)
              (grant := grant)
              hWf
              hGrant
              hHeadGrant
          simp [runwayDeliveredCurrentShapeInstructionsIssuerAuthorized, hHeadOk, ih hTailAuth]

inductive GreenfieldRunwayDeliveredCurrentShapeIssuable :
    StructuredClearance → Prop
  | lineUpAndWait
      {clearance : StructuredClearance}
      (hIssuable : LineUpAndWaitCurrentShapeIssuable clearance) :
      GreenfieldRunwayDeliveredCurrentShapeIssuable clearance
  | takeoff
      {clearance : StructuredClearance}
      (hIssuable : TakeoffCurrentShapeIssuable clearance) :
      GreenfieldRunwayDeliveredCurrentShapeIssuable clearance
  | landing
      {clearance : StructuredClearance}
      (hIssuable : LandingCurrentShapeIssuable clearance) :
      GreenfieldRunwayDeliveredCurrentShapeIssuable clearance
  | touchAndGo
      {clearance : StructuredClearance}
      (hIssuable : TouchAndGoCurrentShapeIssuable clearance) :
      GreenfieldRunwayDeliveredCurrentShapeIssuable clearance
  | lowApproach
      {clearance : StructuredClearance}
      (hIssuable : LowApproachCurrentShapeIssuable clearance) :
      GreenfieldRunwayDeliveredCurrentShapeIssuable clearance
  | goAround
      {clearance : StructuredClearance}
      (hIssuable : GoAroundCurrentShapeIssuable clearance) :
      GreenfieldRunwayDeliveredCurrentShapeIssuable clearance

theorem GreenfieldRunwayDeliveredCurrentShapeReachableIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRunwayDeliveredCurrentShapeIssuable clearance) :
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
  | lineUpAndWait hSingle =>
      exact
        LineUpAndWaitCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | takeoff hSingle =>
      exact
        TakeoffCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | landing hSingle =>
      exact
        LandingCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | touchAndGo hSingle =>
      exact
        TouchAndGoCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | lowApproach hSingle =>
      exact
        LowApproachCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle
  | goAround hSingle =>
      exact
        GoAroundCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle

theorem GreenfieldRunwayDeliveredCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRunwayDeliveredCurrentShapeIssuable clearance)
    (hAuthority :
      GreenfieldRunwayDeliveredCurrentShapeWorldAuthorized
        compileWorld
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ resolved,
      runwayDeliveredCurrentShapeInstructionsIssuerAuthorized
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
      runwayDeliveredCurrentShapeInstructionsIssuerAuthorized
        (extractCompileView compileWorld)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    runwayDeliveredCurrentShapeInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := compileWorld)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GreenfieldRunwayDeliveredCurrentShapeReachableIssuanceTheorem
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
