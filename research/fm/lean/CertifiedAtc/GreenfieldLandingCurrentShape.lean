import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `ClearedToLand`.

This is intentionally a small single-step slice:

- source-level single-step issuance on the current greenfield boundary
- conservative `(runway, land)` authority
- explicit current completion behavior on runway vacation
- explicit current conditional staging/activation behavior
- one runway supersession consequence plus one non-supersession regression

This module does not widen the broader runway family. It closes only the
already-modeled landing-clearance seam on the current runtime boundary.
-/

def currentShapeLandingAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .runway
    operation := .land }

inductive LandingCurrentShapeIssuable :
    StructuredClearance → Prop
  | active
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      (hContent : clearance.content = .single (.clearedToLand target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      LandingCurrentShapeIssuable clearance
  | conditional
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      {predicate : ConditionalPredicate}
      (hContent : clearance.content = .single (.clearedToLand target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = some predicate) :
      LandingCurrentShapeIssuable clearance

theorem LandingCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : LandingCurrentShapeIssuable clearance) :
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
          (instruction := .clearedToLand _ _)
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
          (instruction := .clearedToLand _ _)
          (fallbackDomain := .runway)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition, instructionMayBeConditional])
          hContent
          hDomain

theorem LandingCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : LandingCurrentShapeIssuable clearance)
    (hAuthority :
      WorldControllerHasGrant
        compileWorld
        clearance.issuedBy
        currentShapeLandingAuthorityGrant) :
    ∃ resolved,
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeLandingAuthorityGrant = true ∧
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
        currentShapeLandingAuthorityGrant = true :=
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf
      hAuthority
  rcases LandingCurrentShapeIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleLanding : ResolvedClearance :=
  { source :=
      { id := "CLR-LAND"
        aircraft := "TEST123"
        content := .single (.clearedToLand "TEST123" "RWY-09")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 50
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedToLand "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleLanding : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleLanding }

def sampleResolvedIncomingTowerContactForLanding : ResolvedClearance :=
  { source :=
      { id := "CLR-LAND-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 51
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleResolvedIncomingGoAroundForLanding : ResolvedClearance :=
  { source :=
      { id := "CLR-LAND-GA"
        aircraft := "TEST123"
        content := .single (.goAround "TEST123")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 52
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.goAround "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleResolvedConditionalLanding : ResolvedClearance :=
  { source :=
      { id := "COND-LAND-CS"
        aircraft := "TEST123"
        content := .single (.clearedToLand "TEST123" "RWY-09")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 53
        status := .issued
        condition := some (.afterTraffic (.byDescription "departing 737") .departing) }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedToLand "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution]) ] }

theorem singleLanding_completes_on_runway_vacation :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleLanding
        { runwayTransitions := UniqueSet.singleton "RWY-09" }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleLanding_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleLanding]
        { runwayTransitions := UniqueSet.singleton "RWY-09" }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-LAND"] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleLanding :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleLanding]
        sampleResolvedIncomingTowerContactForLanding
    resolvedClearanceIds admitted.clearances = ["CLR-LAND", "CLR-LAND-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem goAround_fully_supersedes_singleLanding :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleLanding]
        sampleResolvedIncomingGoAroundForLanding
    resolvedClearanceIds admitted.clearances = ["CLR-LAND-GA"] ∧
      resolvedClearanceIds admitted.fullySuperseded = ["CLR-LAND"] := by
  native_decide

theorem conditionalLanding_admission_stages_pending :
    let admitted :=
      admitResolvedClearance
        []
        sampleResolvedConditionalLanding
    admitted.incoming.status = .conditionPending ∧
      resolvedClearanceIds admitted.clearances = ["COND-LAND-CS"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem conditionalLanding_activates_when_condition_true :
    let staged :=
      (admitResolvedClearance [] sampleResolvedConditionalLanding).clearances
    let reconciliation :=
      reconcileResolvedClearances
        staged
        {}
        (fun _ _ => true)
    activatedResolvedIds reconciliation.activatedClearances = ["COND-LAND-CS"] ∧
      resolvedClearanceIds reconciliation.clearances = ["COND-LAND-CS"] := by
  native_decide

end Greenfield
end CertifiedAtc
