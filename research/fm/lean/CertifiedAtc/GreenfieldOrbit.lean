import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `Orbit`.

Like `ExtendDownwind`, `Orbit` is currently a plain persistent step whose
instruction-layer metadata carries no domain, so the resolved execution domain
comes from the source clearance. This module closes the current-shape
single-step story and makes the present persistent-only compound consequence
explicit.
-/

inductive OrbitCurrentShapeIssuable :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {target : AircraftId}
      {direction : OrbitDirection}
      (hContent : clearance.content = .single (.orbit target direction))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      OrbitCurrentShapeIssuable clearance

theorem OrbitCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : OrbitCurrentShapeIssuable clearance) :
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
  rename_i target direction hContent hDomain hCondition
  exact
    plainCurrentShapeAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      (instruction := .orbit target direction)
      (fallbackDomain := .runway)
      hReach
      hFresh
      (by simp [instructionNeedsSpecificResolution])
      (by simp [normalizeConditionalEnvelope, hContent, hCondition])
      hContent
      hDomain

def sampleResolvedSingleOrbit : ResolvedClearance :=
  { source :=
      { id := "CLR-ORBIT"
        aircraft := "TEST123"
        content := .single (.orbit "TEST123" .left)
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 40
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.orbit "TEST123" .left)
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleOrbit : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleOrbit }

def sampleResolvedIncomingOrbitContact : ResolvedClearance :=
  { source :=
      { id := "CLR-ORBIT-FREQ"
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

def sampleResolvedOrbitContact : ResolvedClearance :=
  { source :=
      { id := "CLR-ORBIT-CONTACT"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .orbit "TEST123" .left
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 42
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.orbit "TEST123" .left)
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .runway
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedOrbitContactSuppressed : ManagedResolvedClearance :=
  { resolved := sampleResolvedOrbitContact
    suppressedDomains := UniqueSet.singleton .frequency }

theorem singleOrbit_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleOrbit
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem singleOrbit_reconcile_stays_active :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleOrbit]
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-ORBIT"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = [] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleOrbit :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleOrbit]
        sampleResolvedIncomingOrbitContact
    resolvedClearanceIds admitted.clearances = ["CLR-ORBIT", "CLR-ORBIT-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem suppressedOrbitContact_requiredCompletionStepIndices_nil :
    sampleManagedResolvedOrbitContactSuppressed.requiredCompletionStepIndices = [] := by
  native_decide

theorem suppressedOrbitContact_completes_on_current_engine :
    (evaluateResolvedCompletion
      sampleManagedResolvedOrbitContactSuppressed
      {}).updated.status = .completed := by
  native_decide

end Greenfield
end CertifiedAtc
