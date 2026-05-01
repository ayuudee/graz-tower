import CertifiedAtc.GreenfieldContinueApproach
import CertifiedAtc.GreenfieldRouteBearingCompound

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape compound closure for `ContinueApproach`.

This widens the already-closed single-step slice just one notch:

- one leading `ContinueApproach`
- zero or more immediate adjunct instructions already understood by the
  current greenfield engine

This module itself does not add new authority claims. The primary instruction
still resolves as a plain route-domain step, and the tail reuses the existing
immediate-adjunct surface from the route-bearing compound layer. Current-shape
authority closure for the delivered Phase B surface now lives separately in
`GreenfieldRouteAdjacentAuthority`.
-/

def ContinueApproachCompoundReady
    (world : RouteBearingScopedAviationWorld)
    (tail : List AtcInstruction) : Prop :=
  ∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction

inductive ContinueApproachCompoundCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {target : AircraftId}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps : content.steps = .continueApproach target :: tail)
      (hReady : ContinueApproachCompoundReady world tail)
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      ContinueApproachCompoundCurrentShapeIssuable world clearance

theorem resolvesContinueApproachCompoundClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {target : AircraftId}
    {tail : List AtcInstruction}
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = .continueApproach target :: tail)
    (hReady : ContinueApproachCompoundReady world tail)
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  have hTailWrapped : anyWrappedConditionalStep tail = false :=
    anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts hReady
  have hWrapped :
      anyWrappedConditionalStep (.continueApproach target :: tail) = false := by
    simp [anyWrappedConditionalStep, hTailWrapped]
  rcases resolvesIndexedPlainInstruction
      (world := world)
      (state := initialState)
      (fallbackDomain := .route)
      (index := 0)
      (instruction := .continueApproach target)
      (by simp [instructionNeedsSpecificResolution]) with
      ⟨primaryStep, hPrimaryStep⟩
  rcases resolvesRouteBearingImmediateAdjunctTail_of_ready
      (world := world)
      (state := initialState)
      (fallbackDomain := .route)
      (start := 1)
      (tail := tail)
      hReady with
      ⟨resolvedTail, hResolvedTail⟩
  refine ⟨{ source := clearance, steps := primaryStep :: resolvedTail }, ?_⟩
  refine ⟨?_, rfl, ?_⟩
  · simp [normalizeConditionalEnvelope, hContent, hCondition, hSteps, hWrapped]
  · have hPrimaryStep' :
        ResolvesIndexedStep
          (RouteBearingScopedAviationWorld.toResolutionWorld world)
          initialState
          clearance.domain
          0
          (.continueApproach target)
          primaryStep
          initialState := by
        simpa [hDomain] using hPrimaryStep
    have hResolvedTail' :
        ResolvesSteps
          (RouteBearingScopedAviationWorld.toResolutionWorld world)
          initialState
          clearance.domain
          (enumerateFrom 1 tail)
          resolvedTail
          initialState := by
      simpa [hDomain] using hResolvedTail
    have hIndexed :
        indexedSteps (structuredInstructions clearance) =
          (0, .continueApproach target) :: enumerateFrom 1 tail := by
      simp [hContent, hSteps, structuredInstructions, contentInstructions, indexedSteps, enumerateFrom]
    simpa [hIndexed] using
      ResolvesSteps.cons
        (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
        (state := initialState)
        (nextState := initialState)
        (finalState := initialState)
        (fallbackDomain := clearance.domain)
        (index := 0)
        (instruction := .continueApproach target)
        (step := primaryStep)
        (tail := enumerateFrom 1 tail)
        (resolvedTail := resolvedTail)
        hPrimaryStep'
        hResolvedTail'

theorem ContinueApproachCompoundAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {target : AircraftId}
    {tail : List AtcInstruction}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = .continueApproach target :: tail)
    (hReady : ContinueApproachCompoundReady world tail)
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  rcases resolvesContinueApproachCompoundClearance_of_ready
      (world := world)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (target := target)
      (tail := tail)
      hContent
      hSteps
      hReady
      hDomain
      hCondition with
      ⟨resolved, hResolve⟩
  have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
    simpa [hResolve.sourceEq] using hFresh
  refine ⟨resolved, hResolve, ?_⟩
  exact ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve

theorem ContinueApproachCompoundCurrentShapeIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : ContinueApproachCompoundCurrentShapeIssuable world clearance) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable
  rename_i content target tail hContent hSteps hReady hDomain hCondition
  exact
    ContinueApproachCompoundAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (target := target)
      (tail := tail)
      hReach
      hFresh
      hContent
      hSteps
      hReady
      hDomain
      hCondition

def sampleResolvedContinueApproachEstablished : ResolvedClearance :=
  { source :=
      { id := "CLR-CONT-APP-EST"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .continueApproach "TEST123"
              , .maintainAltitudeUntilEstablished
                  "TEST123"
                  (.altitudeFeet 2000)
                  .localiser ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 50
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.continueApproach "TEST123")
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .route
          (.maintainAltitudeUntilEstablished
            "TEST123"
            (.altitudeFeet 2000)
            .localiser)
          .plain
          rfl ] }

def sampleManagedResolvedContinueApproachEstablished : ManagedResolvedClearance :=
  { resolved := sampleResolvedContinueApproachEstablished }

def sampleContinueApproachEstablishedObservation : CompletionObservation :=
  { establishedApproachComponents := UniqueSet.singleton .localiser }

def sampleResolvedContinueApproachContact : ResolvedClearance :=
  { source :=
      { id := "CLR-CONT-APP-CONTACT"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .continueApproach "TEST123"
              , .contactFrequency "TEST123" .approach none ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 51
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.continueApproach "TEST123")
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .route
          (.contactFrequency "TEST123" .approach none)
          (.frequencyChange { roleName := .approach, instructedFrequency := none })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedContinueApproachContact : ManagedResolvedClearance :=
  { resolved := sampleResolvedContinueApproachContact }

def sampleContinueApproachContactObservation : CompletionObservation :=
  { currentRole := some .approach }

def sampleResolvedIncomingTowerContactForContinueApproach : ResolvedClearance :=
  { source :=
      { id := "CLR-CONT-APP-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 52
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

theorem sampleResolvedContinueApproachEstablished_requiredCompletionStepIndices :
    sampleManagedResolvedContinueApproachEstablished.requiredCompletionStepIndices = [0, 1] := by
  native_decide

theorem sampleResolvedContinueApproachEstablished_remains_active_after_adjunct_completion :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedContinueApproachEstablished
        sampleContinueApproachEstablishedObservation
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem sampleResolvedContinueApproachEstablished_reconcile_stays_active :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedContinueApproachEstablished]
        sampleContinueApproachEstablishedObservation
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-CONT-APP-EST"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = [] := by
  native_decide

theorem sampleContinueApproachContact_frequencySupersession_preserves_primary :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedContinueApproachContact]
        sampleResolvedIncomingTowerContactForContinueApproach
    resolvedClearanceIds admission.clearances = ["CLR-CONT-APP-CONTACT", "CLR-CONT-APP-FREQ"] ∧
      resolvedClearanceIds admission.fullySuperseded = [] ∧
      resolvedClearanceIds admission.partiallySuperseded = ["CLR-CONT-APP-CONTACT"] ∧
      findResolvedById admission.clearances "CLR-CONT-APP-CONTACT" =
        some (sampleManagedResolvedContinueApproachContact.suppress
          (UniqueSet.singleton .frequency)) := by
  native_decide

theorem sampleContinueApproachContact_frequencySupersession_reconcile_stays_active :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedContinueApproachContact]
        sampleResolvedIncomingTowerContactForContinueApproach
    let reconciliation :=
      reconcileResolvedClearances
        admission.clearances
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-CONT-APP-CONTACT", "CLR-CONT-APP-FREQ"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = [] := by
  native_decide

theorem sampleContinueApproachEstablished_goAround_fullySupersedes :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedContinueApproachEstablished]
        sampleResolvedIncomingGoAround
    resolvedClearanceIds admission.clearances = ["CLR-GO-AROUND"] ∧
      resolvedClearanceIds admission.fullySuperseded = ["CLR-CONT-APP-EST"] ∧
      resolvedClearanceIds admission.partiallySuperseded = [] := by
  native_decide

end Greenfield
end CertifiedAtc
