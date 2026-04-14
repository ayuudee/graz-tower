import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `ExtendDownwind`.

This is intentionally a single-step slice. The runtime today treats
`ExtendDownwind` as:

- a plain resolved step
- persistent for single-step lifecycle purposes
- metadata-domain-less at the instruction layer, with the resolved execution
  domain supplied by the source clearance

This module closes the current-shape single-step story and makes the present
compound caveat explicit: once all non-persistent adjunct domains are
suppressed, a compound with only persistent steps remaining terminals on the
current engine.
-/

inductive ExtendDownwindCurrentShapeIssuable :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {target : AircraftId}
      (hContent : clearance.content = .single (.extendDownwind target))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      ExtendDownwindCurrentShapeIssuable clearance

theorem ExtendDownwindCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : ExtendDownwindCurrentShapeIssuable clearance) :
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
      (instruction := .extendDownwind target)
      (fallbackDomain := .runway)
      hReach
      hFresh
      (by simp [instructionNeedsSpecificResolution])
      (by simp [normalizeConditionalEnvelope, hContent, hCondition])
      hContent
      hDomain

def sampleResolvedSingleExtendDownwind : ResolvedClearance :=
  { source :=
      { id := "CLR-EXT-DW"
        aircraft := "TEST123"
        content := .single (.extendDownwind "TEST123")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 30
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.extendDownwind "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleExtendDownwind : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleExtendDownwind }

def sampleResolvedIncomingTowerContact : ResolvedClearance :=
  { source :=
      { id := "CLR-EXT-DW-FREQ"
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

def sampleResolvedExtendDownwindContact : ResolvedClearance :=
  { source :=
      { id := "CLR-EXT-DW-CONTACT"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .extendDownwind "TEST123"
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 32
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.extendDownwind "TEST123")
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .runway
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedExtendDownwindContactSuppressed : ManagedResolvedClearance :=
  { resolved := sampleResolvedExtendDownwindContact
    suppressedDomains := UniqueSet.singleton .frequency }

theorem singleExtendDownwind_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleExtendDownwind
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem singleExtendDownwind_reconcile_stays_active :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleExtendDownwind]
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-EXT-DW"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = [] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleExtendDownwind :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleExtendDownwind]
        sampleResolvedIncomingTowerContact
    resolvedClearanceIds admitted.clearances = ["CLR-EXT-DW", "CLR-EXT-DW-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem suppressedExtendDownwindContact_requiredCompletionStepIndices_nil :
    sampleManagedResolvedExtendDownwindContactSuppressed.requiredCompletionStepIndices = [] := by
  native_decide

theorem suppressedExtendDownwindContact_completes_on_current_engine :
    (evaluateResolvedCompletion
      sampleManagedResolvedExtendDownwindContactSuppressed
      {}).updated.status = .completed := by
  native_decide

end Greenfield
end CertifiedAtc
