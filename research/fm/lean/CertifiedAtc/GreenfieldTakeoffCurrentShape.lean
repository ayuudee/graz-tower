import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `ClearedForTakeoff`.

This is intentionally a small single-step slice:

- source-level single-step issuance on the current greenfield boundary
- conservative `(runway, takeoff)` authority
- explicit current completion behavior once airborne
- explicit current conditional staging/activation behavior
- a runway/frequency supersession regression surface

This module does not widen the broader runway family. It closes only the
already-modeled takeoff-clearance seam on the current runtime boundary.
-/

def currentShapeTakeoffAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .runway
    operation := .takeoff }

inductive TakeoffCurrentShapeIssuable :
    StructuredClearance → Prop
  | active
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      (hContent : clearance.content = .single (.clearedForTakeoff target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      TakeoffCurrentShapeIssuable clearance
  | conditional
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      {predicate : ConditionalPredicate}
      (hContent : clearance.content = .single (.clearedForTakeoff target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = some predicate) :
      TakeoffCurrentShapeIssuable clearance

theorem TakeoffCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : TakeoffCurrentShapeIssuable clearance) :
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
          (instruction := .clearedForTakeoff _ _)
          (fallbackDomain := .runway)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition])
          hContent
          hDomain
  | conditional hContent hDomain hCondition =>
      exact
        plainCurrentShapeAdmissionSoundnessTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          (instruction := .clearedForTakeoff _ _)
          (fallbackDomain := .runway)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition, instructionMayBeConditional])
          hContent
          hDomain

theorem TakeoffCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : TakeoffCurrentShapeIssuable clearance)
    (hAuthority :
      WorldControllerHasGrant
        compileWorld
        clearance.issuedBy
        currentShapeTakeoffAuthorityGrant) :
    ∃ resolved,
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeTakeoffAuthorityGrant = true ∧
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
        currentShapeTakeoffAuthorityGrant = true :=
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf
      hAuthority
  rcases TakeoffCurrentShapeIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleTakeoff : ResolvedClearance :=
  { source :=
      { id := "CLR-TO"
        aircraft := "TEST123"
        content := .single (.clearedForTakeoff "TEST123" "RWY-09")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 40
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedForTakeoff "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleTakeoff : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleTakeoff }

def sampleResolvedIncomingTowerContactForTakeoff : ResolvedClearance :=
  { source :=
      { id := "CLR-TO-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 41
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleResolvedConditionalTakeoff : ResolvedClearance :=
  { source :=
      { id := "COND-TO-CS"
        aircraft := "TEST123"
        content := .single (.clearedForTakeoff "TEST123" "RWY-09")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 42
        status := .issued
        condition := some (.afterTraffic (.byDescription "departing 737") .departing) }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedForTakeoff "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution]) ] }

theorem singleTakeoff_completes_on_airborne :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleTakeoff
        { onGround := false }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleTakeoff_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleTakeoff]
        { onGround := false }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-TO"] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleTakeoff :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleTakeoff]
        sampleResolvedIncomingTowerContactForTakeoff
    resolvedClearanceIds admitted.clearances = ["CLR-TO", "CLR-TO-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem conditionalTakeoff_admission_stages_pending :
    let admitted :=
      admitResolvedClearance
        []
        sampleResolvedConditionalTakeoff
    admitted.incoming.status = .conditionPending ∧
      resolvedClearanceIds admitted.clearances = ["COND-TO-CS"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem conditionalTakeoff_activates_when_condition_true :
    let staged :=
      (admitResolvedClearance [] sampleResolvedConditionalTakeoff).clearances
    let reconciliation :=
      reconcileResolvedClearances
        staged
        {}
        (fun _ _ => true)
    activatedResolvedIds reconciliation.activatedClearances = ["COND-TO-CS"] ∧
      resolvedClearanceIds reconciliation.clearances = ["COND-TO-CS"] := by
  native_decide

end Greenfield
end CertifiedAtc
