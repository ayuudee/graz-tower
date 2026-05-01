import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `SquawkNormal`.

This is intentionally a small single-step slice:

- source-level single-step issuance on the current greenfield boundary
- conservative `(radioRole, squawk)` authority
- explicit current self-completing behavior on matching transponder mode
- no conditional staging, matching current Kotlin metadata
- one non-supersession regression against an incoming frequency instruction

This module does not widen the broader transponder/surveillance family. It
closes only the already-modeled `SquawkNormal` path on the current runtime
boundary.
-/

def currentShapeSquawkNormalAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .radioRole
    operation := .squawk }

inductive SquawkNormalCurrentShapeIssuable :
    StructuredClearance → Prop
  | active
      {clearance : StructuredClearance}
      {target : AircraftId}
      {mode : TransponderMode}
      (hContent : clearance.content = .single (.squawkNormal target mode))
      (hDomain : clearance.domain = .squawk)
      (hCondition : clearance.condition = none) :
      SquawkNormalCurrentShapeIssuable clearance

theorem SquawkNormalCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : SquawkNormalCurrentShapeIssuable clearance) :
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
  | active hContent hDomain hCondition =>
      exact
        plainCurrentShapeAdmissionSoundnessTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          (instruction := .squawkNormal _ _)
          (fallbackDomain := .squawk)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition])
          hContent
          hDomain

theorem SquawkNormalCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : SquawkNormalCurrentShapeIssuable clearance)
    (hAuthority :
      WorldControllerHasGrant
        compileWorld
        clearance.issuedBy
        currentShapeSquawkNormalAuthorityGrant) :
    ∃ resolved,
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeSquawkNormalAuthorityGrant = true ∧
      ResolvesClearance
        resolutionWorld
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeSquawkNormalAuthorityGrant = true :=
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf
      hAuthority
  rcases SquawkNormalCurrentShapeIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleSquawkNormal : ResolvedClearance :=
  { source :=
      { id := "CLR-SQNORM"
        aircraft := "TEST123"
        content := .single (.squawkNormal "TEST123" .normal)
        domain := .squawk
        issuedBy := "CTRL-1"
        issuedAt := 98
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .squawk
          (.squawkNormal "TEST123" .normal)
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleSquawkNormal : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleSquawkNormal }

def sampleResolvedIncomingTowerContactForSquawkNormal : ResolvedClearance :=
  { source :=
      { id := "CLR-SQNORM-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 99
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

theorem singleSquawkNormal_completes_on_matching_mode :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleSquawkNormal
        { transponderMode := some .normal }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleSquawkNormal_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleSquawkNormal]
        { transponderMode := some .normal }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-SQNORM"] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleSquawkNormal :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleSquawkNormal]
        sampleResolvedIncomingTowerContactForSquawkNormal
    resolvedClearanceIds admitted.clearances = ["CLR-SQNORM", "CLR-SQNORM-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

end Greenfield
end CertifiedAtc
