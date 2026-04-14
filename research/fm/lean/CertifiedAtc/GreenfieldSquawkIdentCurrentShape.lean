import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `SquawkIdent`.

This is intentionally a small single-step slice:

- source-level single-step issuance on the current greenfield boundary
- conservative `(radioRole, squawk)` authority
- explicit current self-completing behavior on ident activation
- no conditional staging, matching current Kotlin metadata
- one non-supersession regression against an incoming frequency instruction

This module does not widen the broader transponder/surveillance family. It
closes only the already-modeled `SquawkIdent` path on the current runtime
boundary.
-/

def currentShapeSquawkIdentAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .radioRole
    operation := .squawk }

inductive SquawkIdentCurrentShapeIssuable :
    StructuredClearance → Prop
  | active
      {clearance : StructuredClearance}
      {target : AircraftId}
      (hContent : clearance.content = .single (.squawkIdent target))
      (hDomain : clearance.domain = .squawk)
      (hCondition : clearance.condition = none) :
      SquawkIdentCurrentShapeIssuable clearance

theorem SquawkIdentCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : SquawkIdentCurrentShapeIssuable clearance) :
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
          (instruction := .squawkIdent _)
          (fallbackDomain := .squawk)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition])
          hContent
          hDomain

theorem SquawkIdentCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : SquawkIdentCurrentShapeIssuable clearance)
    (hAuthority :
      WorldControllerHasGrant
        compileWorld
        clearance.issuedBy
        currentShapeSquawkIdentAuthorityGrant) :
    ∃ resolved,
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeSquawkIdentAuthorityGrant = true ∧
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
        currentShapeSquawkIdentAuthorityGrant = true :=
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf
      hAuthority
  rcases SquawkIdentCurrentShapeIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleSquawkIdent : ResolvedClearance :=
  { source :=
      { id := "CLR-SQID"
        aircraft := "TEST123"
        content := .single (.squawkIdent "TEST123")
        domain := .squawk
        issuedBy := "CTRL-1"
        issuedAt := 94
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .squawk
          (.squawkIdent "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleSquawkIdent : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleSquawkIdent }

def sampleResolvedIncomingTowerContactForSquawkIdent : ResolvedClearance :=
  { source :=
      { id := "CLR-SQID-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 95
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

theorem singleSquawkIdent_completes_on_ident_activation :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleSquawkIdent
        { transponderIdentActive := true }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleSquawkIdent_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleSquawkIdent]
        { transponderIdentActive := true }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-SQID"] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleSquawkIdent :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleSquawkIdent]
        sampleResolvedIncomingTowerContactForSquawkIdent
    resolvedClearanceIds admitted.clearances = ["CLR-SQID", "CLR-SQID-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

end Greenfield
end CertifiedAtc
