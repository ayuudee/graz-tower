import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `SetSquawk`.

This is intentionally a small single-step slice:

- source-level single-step issuance on the current greenfield boundary
- conservative `(radioRole, squawk)` authority
- explicit current on-activation completion behavior
- no conditional staging, matching current Kotlin metadata
- one non-supersession regression against an incoming frequency instruction

This module does not widen the broader transponder/surveillance family. It
closes only the already-modeled `SetSquawk` path on the current runtime
boundary.
-/

def currentShapeSetSquawkAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .radioRole
    operation := .squawk }

inductive SetSquawkCurrentShapeIssuable :
    StructuredClearance → Prop
  | active
      {clearance : StructuredClearance}
      {target : AircraftId}
      {code : Squawk}
      (hContent : clearance.content = .single (.setSquawk target code))
      (hDomain : clearance.domain = .squawk)
      (hCondition : clearance.condition = none) :
      SetSquawkCurrentShapeIssuable clearance

theorem SetSquawkCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : SetSquawkCurrentShapeIssuable clearance) :
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
          (instruction := .setSquawk _ _)
          (fallbackDomain := .squawk)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition])
          hContent
          hDomain

theorem SetSquawkCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : SetSquawkCurrentShapeIssuable clearance)
    (hAuthority :
      WorldControllerHasGrant
        compileWorld
        clearance.issuedBy
        currentShapeSetSquawkAuthorityGrant) :
    ∃ resolved,
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeSetSquawkAuthorityGrant = true ∧
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
        currentShapeSetSquawkAuthorityGrant = true :=
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf
      hAuthority
  rcases SetSquawkCurrentShapeIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleSetSquawk : ResolvedClearance :=
  { source :=
      { id := "CLR-SQK"
        aircraft := "TEST123"
        content := .single (.setSquawk "TEST123" 4672)
        domain := .squawk
        issuedBy := "CTRL-1"
        issuedAt := 90
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .squawk
          (.setSquawk "TEST123" 4672)
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleSetSquawk : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleSetSquawk }

def sampleResolvedIncomingTowerContactForSetSquawk : ResolvedClearance :=
  { source :=
      { id := "CLR-SQK-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 91
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

theorem singleSetSquawk_completes_on_activation :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleSetSquawk
        {}
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleSetSquawk_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleSetSquawk]
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-SQK"] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleSetSquawk :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleSetSquawk]
        sampleResolvedIncomingTowerContactForSetSquawk
    resolvedClearanceIds admitted.clearances = ["CLR-SQK", "CLR-SQK-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

end Greenfield
end CertifiedAtc
