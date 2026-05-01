import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `ConfirmSquawk`.

This is intentionally a small single-step slice:

- source-level single-step issuance on the current greenfield boundary
- conservative `(radioRole, squawk)` authority
- explicit current self-completing behavior on matching transponder code
- no conditional staging, matching current Kotlin metadata
- one non-supersession regression against an incoming frequency instruction

This module does not widen the broader transponder/surveillance family. It
closes only the already-modeled `ConfirmSquawk` path on the current runtime
boundary.
-/

def currentShapeConfirmSquawkAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .radioRole
    operation := .squawk }

inductive ConfirmSquawkCurrentShapeIssuable :
    StructuredClearance → Prop
  | active
      {clearance : StructuredClearance}
      {target : AircraftId}
      {code : Squawk}
      (hContent : clearance.content = .single (.confirmSquawk target code))
      (hDomain : clearance.domain = .squawk)
      (hCondition : clearance.condition = none) :
      ConfirmSquawkCurrentShapeIssuable clearance

theorem ConfirmSquawkCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : ConfirmSquawkCurrentShapeIssuable clearance) :
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
          (instruction := .confirmSquawk _ _)
          (fallbackDomain := .squawk)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition])
          hContent
          hDomain

theorem ConfirmSquawkCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : ConfirmSquawkCurrentShapeIssuable clearance)
    (hAuthority :
      WorldControllerHasGrant
        compileWorld
        clearance.issuedBy
        currentShapeConfirmSquawkAuthorityGrant) :
    ∃ resolved,
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeConfirmSquawkAuthorityGrant = true ∧
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
        currentShapeConfirmSquawkAuthorityGrant = true :=
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf
      hAuthority
  rcases ConfirmSquawkCurrentShapeIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleConfirmSquawk : ResolvedClearance :=
  { source :=
      { id := "CLR-CSQK"
        aircraft := "TEST123"
        content := .single (.confirmSquawk "TEST123" 4672)
        domain := .squawk
        issuedBy := "CTRL-1"
        issuedAt := 92
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .squawk
          (.confirmSquawk "TEST123" 4672)
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleConfirmSquawk : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleConfirmSquawk }

def sampleResolvedIncomingTowerContactForConfirmSquawk : ResolvedClearance :=
  { source :=
      { id := "CLR-CSQK-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 93
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

theorem singleConfirmSquawk_completes_on_matching_code :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleConfirmSquawk
        { transponderCode := some 4672 }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleConfirmSquawk_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleConfirmSquawk]
        { transponderCode := some 4672 }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-CSQK"] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleConfirmSquawk :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleConfirmSquawk]
        sampleResolvedIncomingTowerContactForConfirmSquawk
    resolvedClearanceIds admitted.clearances = ["CLR-CSQK", "CLR-CSQK-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

end Greenfield
end CertifiedAtc
