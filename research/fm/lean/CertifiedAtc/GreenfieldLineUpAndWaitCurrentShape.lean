import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `LineUpAndWait`.

This is intentionally a small single-step slice:

- source-level single-step issuance on the current greenfield boundary
- conservative `(runway, lineUp)` authority
- explicit current lifecycle behavior for the active case
- explicit current conditional staging/activation behavior
- one runway supersession consequence and one non-supersession regression

This module does not widen the broader runway family. It closes the already
modeled `LineUpAndWait` path on the current runtime boundary.
-/

def currentShapeLineUpAndWaitAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .runway
    operation := .lineUp }

inductive LineUpAndWaitCurrentShapeIssuable :
    StructuredClearance → Prop
  | active
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      (hContent : clearance.content = .single (.lineUpAndWait target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      LineUpAndWaitCurrentShapeIssuable clearance
  | conditional
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      {predicate : ConditionalPredicate}
      (hContent : clearance.content = .single (.lineUpAndWait target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = some predicate) :
      LineUpAndWaitCurrentShapeIssuable clearance

theorem LineUpAndWaitCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : LineUpAndWaitCurrentShapeIssuable clearance) :
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
          (instruction := .lineUpAndWait _ _)
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
          (instruction := .lineUpAndWait _ _)
          (fallbackDomain := .runway)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition, instructionMayBeConditional])
          hContent
          hDomain

theorem LineUpAndWaitCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : LineUpAndWaitCurrentShapeIssuable clearance)
    (hAuthority :
      WorldControllerHasGrant
        compileWorld
        clearance.issuedBy
        currentShapeLineUpAndWaitAuthorityGrant) :
    ∃ resolved,
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeLineUpAndWaitAuthorityGrant = true ∧
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
        currentShapeLineUpAndWaitAuthorityGrant = true :=
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf
      hAuthority
  rcases LineUpAndWaitCurrentShapeIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleLineUpAndWait : ResolvedClearance :=
  { source :=
      { id := "CLR-LUP"
        aircraft := "TEST123"
        content := .single (.lineUpAndWait "TEST123" "RWY-09")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 30
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.lineUpAndWait "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleLineUpAndWait : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleLineUpAndWait }

def sampleResolvedIncomingTowerContactForLineUpAndWait : ResolvedClearance :=
  { source :=
      { id := "CLR-LUP-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 31
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleResolvedIncomingTakeoffForLineUpAndWait : ResolvedClearance :=
  { source :=
      { id := "CLR-LUP-TO"
        aircraft := "TEST123"
        content := .single (.clearedForTakeoff "TEST123" "RWY-09")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 32
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedForTakeoff "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleResolvedConditionalLineUpAndWait : ResolvedClearance :=
  { source :=
      { id := "COND-LUP-CS"
        aircraft := "TEST123"
        content := .single (.lineUpAndWait "TEST123" "RWY-09")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 33
        status := .issued
        condition := some (.afterTraffic (.byDescription "landing 737") .landing) }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.lineUpAndWait "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution]) ] }

theorem singleLineUpAndWait_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleLineUpAndWait
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem singleLineUpAndWait_reconcile_stays_active :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleLineUpAndWait]
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-LUP"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = [] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleLineUpAndWait :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleLineUpAndWait]
        sampleResolvedIncomingTowerContactForLineUpAndWait
    resolvedClearanceIds admitted.clearances = ["CLR-LUP", "CLR-LUP-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem takeoff_fully_supersedes_singleLineUpAndWait :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleLineUpAndWait]
        sampleResolvedIncomingTakeoffForLineUpAndWait
    resolvedClearanceIds admitted.clearances = ["CLR-LUP-TO"] ∧
      resolvedClearanceIds admitted.fullySuperseded = ["CLR-LUP"] := by
  native_decide

theorem conditionalLineUpAndWait_admission_stages_pending :
    let admitted :=
      admitResolvedClearance
        []
        sampleResolvedConditionalLineUpAndWait
    admitted.incoming.status = .conditionPending ∧
      resolvedClearanceIds admitted.clearances = ["COND-LUP-CS"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem conditionalLineUpAndWait_activates_when_condition_true :
    let staged :=
      (admitResolvedClearance [] sampleResolvedConditionalLineUpAndWait).clearances
    let reconciliation :=
      reconcileResolvedClearances
        staged
        {}
        (fun _ _ => true)
    activatedResolvedIds reconciliation.activatedClearances = ["COND-LUP-CS"] ∧
      resolvedClearanceIds reconciliation.clearances = ["COND-LUP-CS"] := by
  native_decide

end Greenfield
end CertifiedAtc
