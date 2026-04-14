import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `ContinueApproach`.

This is intentionally a small slice:

- source-level single-step issuance on the current greenfield boundary
- current execution behavior: it remains active because completion is
  intentionally unmodeled
- one operational supersession consequence: `GoAround` fully supersedes it

Authority mapping is still intentionally unresolved for this family.
-/

inductive ContinueApproachCurrentShapeIssuable :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {target : AircraftId}
      (hContent : clearance.content = .single (.continueApproach target))
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      ContinueApproachCurrentShapeIssuable clearance

theorem ContinueApproachCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : ContinueApproachCurrentShapeIssuable clearance) :
    ∃ resolved,
      ResolvesClearance
        world
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable
  rename_i target hContent hDomain hCondition
  exact
    plainCurrentShapeAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      (instruction := .continueApproach target)
      (fallbackDomain := .route)
      hReach
      hFresh
      (by simp [instructionNeedsSpecificResolution])
      (by simp [normalizeConditionalEnvelope, hContent, hCondition])
      hContent
      hDomain

def sampleResolvedSingleContinueApproach : ResolvedClearance :=
  { source :=
      { id := "CLR-CONT-APP"
        aircraft := "TEST123"
        content := .single (.continueApproach "TEST123")
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 20
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.continueApproach "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleContinueApproach : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleContinueApproach }

def sampleResolvedIncomingGoAround : ResolvedClearance :=
  { source :=
      { id := "CLR-GO-AROUND"
        aircraft := "TEST123"
        content := .single (.goAround "TEST123")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 21
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.goAround "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

theorem singleContinueApproach_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleContinueApproach
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem singleContinueApproach_reconcile_stays_active :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleContinueApproach]
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-CONT-APP"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = [] := by
  native_decide

theorem goAround_fully_supersedes_singleContinueApproach :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleContinueApproach]
        sampleResolvedIncomingGoAround
    resolvedClearanceIds admitted.clearances = ["CLR-GO-AROUND"] ∧
      resolvedClearanceIds admitted.fullySuperseded = ["CLR-CONT-APP"] := by
  native_decide

end Greenfield
end CertifiedAtc
