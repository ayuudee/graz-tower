import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `ClearedLowApproach`.

This is intentionally a small single-step slice:

- source-level single-step issuance on the current greenfield boundary
- conservative `(runway, lowApproach)` authority
- explicit current completion behavior after runway transition and runway exit
- explicit current conditional staging/activation behavior
- one runway supersession consequence plus one non-supersession regression
-/

def currentShapeLowApproachAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .runway
    operation := .lowApproach }

inductive LowApproachCurrentShapeIssuable :
    StructuredClearance → Prop
  | active
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      (hContent : clearance.content = .single (.clearedLowApproach target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      LowApproachCurrentShapeIssuable clearance
  | conditional
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      {predicate : ConditionalPredicate}
      (hContent : clearance.content = .single (.clearedLowApproach target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = some predicate) :
      LowApproachCurrentShapeIssuable clearance

theorem LowApproachCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : LowApproachCurrentShapeIssuable clearance) :
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
          (instruction := .clearedLowApproach _ _)
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
          (instruction := .clearedLowApproach _ _)
          (fallbackDomain := .runway)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition, instructionMayBeConditional])
          hContent
          hDomain

theorem LowApproachCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : LowApproachCurrentShapeIssuable clearance)
    (hAuthority :
      WorldControllerHasGrant
        compileWorld
        clearance.issuedBy
        currentShapeLowApproachAuthorityGrant) :
    ∃ resolved,
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeLowApproachAuthorityGrant = true ∧
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
        currentShapeLowApproachAuthorityGrant = true :=
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf
      hAuthority
  rcases LowApproachCurrentShapeIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleLowApproach : ResolvedClearance :=
  { source :=
      { id := "CLR-LA"
        aircraft := "TEST123"
        content := .single (.clearedLowApproach "TEST123" "RWY-09")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 70
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedLowApproach "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleLowApproach : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleLowApproach }

def sampleResolvedIncomingTowerContactForLowApproach : ResolvedClearance :=
  { source :=
      { id := "CLR-LA-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 71
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleResolvedIncomingGoAroundForLowApproach : ResolvedClearance :=
  { source :=
      { id := "CLR-LA-GA"
        aircraft := "TEST123"
        content := .single (.goAround "TEST123")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 72
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.goAround "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleResolvedConditionalLowApproach : ResolvedClearance :=
  { source :=
      { id := "COND-LA-CS"
        aircraft := "TEST123"
        content := .single (.clearedLowApproach "TEST123" "RWY-09")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 73
        status := .issued
        condition := some (.afterTraffic (.byDescription "departing 737") .departing) }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedLowApproach "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution]) ] }

theorem singleLowApproach_completes_on_runway_transition_and_exit :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleLowApproach
        { onGround := false
          runwayTransitions := UniqueSet.singleton "RWY-09" }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleLowApproach_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleLowApproach]
        { onGround := false
          runwayTransitions := UniqueSet.singleton "RWY-09" }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-LA"] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleLowApproach :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleLowApproach]
        sampleResolvedIncomingTowerContactForLowApproach
    resolvedClearanceIds admitted.clearances = ["CLR-LA", "CLR-LA-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem goAround_fully_supersedes_singleLowApproach :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleLowApproach]
        sampleResolvedIncomingGoAroundForLowApproach
    resolvedClearanceIds admitted.clearances = ["CLR-LA-GA"] ∧
      resolvedClearanceIds admitted.fullySuperseded = ["CLR-LA"] := by
  native_decide

theorem conditionalLowApproach_admission_stages_pending :
    let admitted :=
      admitResolvedClearance
        []
        sampleResolvedConditionalLowApproach
    admitted.incoming.status = .conditionPending ∧
      resolvedClearanceIds admitted.clearances = ["COND-LA-CS"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem conditionalLowApproach_activates_when_condition_true :
    let staged :=
      (admitResolvedClearance [] sampleResolvedConditionalLowApproach).clearances
    let reconciliation :=
      reconcileResolvedClearances
        staged
        {}
        (fun _ _ => true)
    activatedResolvedIds reconciliation.activatedClearances = ["COND-LA-CS"] ∧
      resolvedClearanceIds reconciliation.clearances = ["COND-LA-CS"] := by
  native_decide

end Greenfield
end CertifiedAtc
