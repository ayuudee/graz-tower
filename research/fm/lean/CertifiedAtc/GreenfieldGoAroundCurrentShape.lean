import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldPlainCurrentShape
import CertifiedAtc.GreenfieldLandingCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `GoAround`.

This is intentionally a small single-step slice:

- source-level single-step issuance on the current greenfield boundary
- conservative `(runway, goAround)` authority
- explicit current lifecycle behavior for the active case
- no conditional staging, matching current Kotlin metadata
- one runway supersession consequence and one non-supersession regression

This module does not widen the broader runway family. It closes the already
modeled `GoAround` path on the current runtime boundary.
-/

def currentShapeGoAroundAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .runway
    operation := .goAround }

inductive GoAroundCurrentShapeIssuable :
    StructuredClearance → Prop
  | active
      {clearance : StructuredClearance}
      {target : AircraftId}
      (hContent : clearance.content = .single (.goAround target))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      GoAroundCurrentShapeIssuable clearance

theorem GoAroundCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GoAroundCurrentShapeIssuable clearance) :
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
          (instruction := .goAround _)
          (fallbackDomain := .runway)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition])
          hContent
          hDomain

theorem GoAroundCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GoAroundCurrentShapeIssuable clearance)
    (hAuthority :
      WorldControllerHasGrant
        compileWorld
        clearance.issuedBy
        currentShapeGoAroundAuthorityGrant) :
    ∃ resolved,
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeGoAroundAuthorityGrant = true ∧
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
        currentShapeGoAroundAuthorityGrant = true :=
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf
      hAuthority
  rcases GoAroundCurrentShapeIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleGoAroundCurrentShape : ResolvedClearance :=
  { source :=
      { id := "CLR-GA-CS"
        aircraft := "TEST123"
        content := .single (.goAround "TEST123")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 80
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.goAround "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleGoAroundCurrentShape : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleGoAroundCurrentShape }

def sampleResolvedIncomingTowerContactForGoAround : ResolvedClearance :=
  { source :=
      { id := "CLR-GA-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 81
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

theorem singleGoAround_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleGoAroundCurrentShape
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem singleGoAround_reconcile_stays_active :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleGoAroundCurrentShape]
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-GA-CS"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = [] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleGoAround :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleGoAroundCurrentShape]
        sampleResolvedIncomingTowerContactForGoAround
    resolvedClearanceIds admitted.clearances = ["CLR-GA-CS", "CLR-GA-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem singleGoAround_fully_supersedes_singleLanding :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleLanding]
        sampleResolvedSingleGoAroundCurrentShape
    resolvedClearanceIds admitted.clearances = ["CLR-GA-CS"] ∧
      resolvedClearanceIds admitted.fullySuperseded = ["CLR-LAND"] := by
  native_decide

end Greenfield
end CertifiedAtc
