import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `SquawkStandby`.

This is intentionally a small single-step slice:

- source-level single-step issuance on the current greenfield boundary
- conservative `(radioRole, squawk)` authority
- explicit current self-completing behavior on standby-mode observation
- no conditional staging, matching current Kotlin metadata
- one non-supersession regression against an incoming frequency instruction

This module does not widen the broader transponder/surveillance family. It
closes only the already-modeled `SquawkStandby` path on the current runtime
boundary.
-/

def currentShapeSquawkStandbyAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .radioRole
    operation := .squawk }

inductive SquawkStandbyCurrentShapeIssuable :
    StructuredClearance → Prop
  | active
      {clearance : StructuredClearance}
      {target : AircraftId}
      (hContent : clearance.content = .single (.squawkStandby target))
      (hDomain : clearance.domain = .squawk)
      (hCondition : clearance.condition = none) :
      SquawkStandbyCurrentShapeIssuable clearance

theorem SquawkStandbyCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : SquawkStandbyCurrentShapeIssuable clearance) :
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
          (instruction := .squawkStandby _)
          (fallbackDomain := .squawk)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition])
          hContent
          hDomain

theorem SquawkStandbyCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : SquawkStandbyCurrentShapeIssuable clearance)
    (hAuthority :
      WorldControllerHasGrant
        compileWorld
        clearance.issuedBy
        currentShapeSquawkStandbyAuthorityGrant) :
    ∃ resolved,
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeSquawkStandbyAuthorityGrant = true ∧
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
        currentShapeSquawkStandbyAuthorityGrant = true :=
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf
      hAuthority
  rcases SquawkStandbyCurrentShapeIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleSquawkStandby : ResolvedClearance :=
  { source :=
      { id := "CLR-SQSBY"
        aircraft := "TEST123"
        content := .single (.squawkStandby "TEST123")
        domain := .squawk
        issuedBy := "CTRL-1"
        issuedAt := 96
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .squawk
          (.squawkStandby "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleSquawkStandby : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleSquawkStandby }

def sampleResolvedIncomingTowerContactForSquawkStandby : ResolvedClearance :=
  { source :=
      { id := "CLR-SQSBY-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 97
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

theorem singleSquawkStandby_completes_on_standby_mode :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleSquawkStandby
        { transponderMode := some .standby }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleSquawkStandby_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleSquawkStandby]
        { transponderMode := some .standby }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-SQSBY"] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleSquawkStandby :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleSquawkStandby]
        sampleResolvedIncomingTowerContactForSquawkStandby
    resolvedClearanceIds admitted.clearances = ["CLR-SQSBY", "CLR-SQSBY-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

end Greenfield
end CertifiedAtc
