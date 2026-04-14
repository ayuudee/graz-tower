import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `ClearedTouchAndGo`.

This is intentionally a small single-step slice:

- source-level single-step issuance on the current greenfield boundary
- conservative `(runway, touchAndGo)` authority
- explicit current completion behavior after runway transition airborne
- explicit current conditional staging/activation behavior
- one runway supersession consequence plus one non-supersession regression
-/

def currentShapeTouchAndGoAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .runway
    operation := .touchAndGo }

inductive TouchAndGoCurrentShapeIssuable :
    StructuredClearance → Prop
  | active
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      (hContent : clearance.content = .single (.clearedTouchAndGo target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      TouchAndGoCurrentShapeIssuable clearance
  | conditional
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      {predicate : ConditionalPredicate}
      (hContent : clearance.content = .single (.clearedTouchAndGo target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = some predicate) :
      TouchAndGoCurrentShapeIssuable clearance

theorem TouchAndGoCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : TouchAndGoCurrentShapeIssuable clearance) :
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
          (instruction := .clearedTouchAndGo _ _)
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
          (instruction := .clearedTouchAndGo _ _)
          (fallbackDomain := .runway)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition, instructionMayBeConditional])
          hContent
          hDomain

theorem TouchAndGoCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : TouchAndGoCurrentShapeIssuable clearance)
    (hAuthority :
      WorldControllerHasGrant
        compileWorld
        clearance.issuedBy
        currentShapeTouchAndGoAuthorityGrant) :
    ∃ resolved,
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeTouchAndGoAuthorityGrant = true ∧
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
        currentShapeTouchAndGoAuthorityGrant = true :=
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf
      hAuthority
  rcases TouchAndGoCurrentShapeIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleTouchAndGo : ResolvedClearance :=
  { source :=
      { id := "CLR-TNG"
        aircraft := "TEST123"
        content := .single (.clearedTouchAndGo "TEST123" "RWY-09")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 60
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedTouchAndGo "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleTouchAndGo : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleTouchAndGo }

def sampleResolvedIncomingTowerContactForTouchAndGo : ResolvedClearance :=
  { source :=
      { id := "CLR-TNG-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 61
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleResolvedIncomingGoAroundForTouchAndGo : ResolvedClearance :=
  { source :=
      { id := "CLR-TNG-GA"
        aircraft := "TEST123"
        content := .single (.goAround "TEST123")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 62
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.goAround "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleResolvedConditionalTouchAndGo : ResolvedClearance :=
  { source :=
      { id := "COND-TNG-CS"
        aircraft := "TEST123"
        content := .single (.clearedTouchAndGo "TEST123" "RWY-09")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 63
        status := .issued
        condition := some (.afterTraffic (.byDescription "landing 737") .landing) }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedTouchAndGo "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution]) ] }

theorem singleTouchAndGo_completes_on_runway_transition_airborne :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleTouchAndGo
        { onGround := false
          runwayTransitions := UniqueSet.singleton "RWY-09"
          activeRunways := UniqueSet.singleton "RWY-09" }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleTouchAndGo_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleTouchAndGo]
        { onGround := false
          runwayTransitions := UniqueSet.singleton "RWY-09"
          activeRunways := UniqueSet.singleton "RWY-09" }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-TNG"] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleTouchAndGo :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleTouchAndGo]
        sampleResolvedIncomingTowerContactForTouchAndGo
    resolvedClearanceIds admitted.clearances = ["CLR-TNG", "CLR-TNG-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem goAround_fully_supersedes_singleTouchAndGo :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleTouchAndGo]
        sampleResolvedIncomingGoAroundForTouchAndGo
    resolvedClearanceIds admitted.clearances = ["CLR-TNG-GA"] ∧
      resolvedClearanceIds admitted.fullySuperseded = ["CLR-TNG"] := by
  native_decide

theorem conditionalTouchAndGo_admission_stages_pending :
    let admitted :=
      admitResolvedClearance
        []
        sampleResolvedConditionalTouchAndGo
    admitted.incoming.status = .conditionPending ∧
      resolvedClearanceIds admitted.clearances = ["COND-TNG-CS"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem conditionalTouchAndGo_activates_when_condition_true :
    let staged :=
      (admitResolvedClearance [] sampleResolvedConditionalTouchAndGo).clearances
    let reconciliation :=
      reconcileResolvedClearances
        staged
        {}
        (fun _ _ => true)
    activatedResolvedIds reconciliation.activatedClearances = ["COND-TNG-CS"] ∧
      resolvedClearanceIds reconciliation.clearances = ["COND-TNG-CS"] := by
  native_decide

end Greenfield
end CertifiedAtc
