import CertifiedAtc.GreenfieldAirspaceCurrentShape
import CertifiedAtc.GreenfieldRouteBearingCompound

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape compound closure for the persistent current Kotlin airspace-
clearance families.

This widens the single-step airspace slice just one notch:

- one leading persistent airspace-clearance primary
- zero or more immediate adjunct tails already understood by the current
  greenfield engine

It remains intentionally narrow:

- `ClearedToEnterControlZone`
- `SpecialVfrClearance`

`RemainOutsideControlledAirspace` stays in the single-step slice for now,
because its current completion/lifecycle semantics are looser.
-/

inductive PersistentAirspaceCurrentShapePrimary : AtcInstruction → Prop
  | enterZone
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      {route : Option RouteSpec}
      {levelRestriction : Option Level} :
      PersistentAirspaceCurrentShapePrimary
        (.clearedToEnterControlZone target airspace route levelRestriction)
  | specialVfr
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      {route : Option RouteSpec}
      {levelRestriction : Option Level} :
      PersistentAirspaceCurrentShapePrimary
        (.specialVfrClearance target airspace route levelRestriction)

theorem persistentAirspaceCurrentShapePrimary_needsNoSpecificResolution
    {instruction : AtcInstruction}
    (hPrimary : PersistentAirspaceCurrentShapePrimary instruction) :
    instructionNeedsSpecificResolution instruction = false := by
  cases hPrimary <;> simp [instructionNeedsSpecificResolution]

theorem persistentAirspaceCurrentShapePrimary_persistentTiming
    {instruction : AtcInstruction}
    (hPrimary : PersistentAirspaceCurrentShapePrimary instruction) :
    instructionTiming? instruction = some .persistent := by
  cases hPrimary <;> simp [instructionTiming?]

theorem persistentAirspaceCurrentShapePrimary_routeDomain
    {instruction : AtcInstruction}
    (hPrimary : PersistentAirspaceCurrentShapePrimary instruction) :
    instructionDomain? instruction = some .route := by
  cases hPrimary <;> simp [instructionDomain?]

theorem persistentAirspaceCurrentShapePrimary_persistentCompletion
    {instruction : AtcInstruction}
    (hPrimary : PersistentAirspaceCurrentShapePrimary instruction) :
    instructionCompletionCategory? instruction = some .persistent := by
  cases hPrimary <;> simp [instructionCompletionCategory?]

theorem persistentAirspaceCurrentShapePrimary_not_wrappedConditional
    {instruction : AtcInstruction}
    (hPrimary : PersistentAirspaceCurrentShapePrimary instruction) :
    anyWrappedConditionalStep [instruction] = false := by
  cases hPrimary <;> simp [anyWrappedConditionalStep]

def GreenfieldAirspaceCompoundReady
    (world : RouteBearingScopedAviationWorld)
    (primary : AtcInstruction)
    (tail : List AtcInstruction) : Prop :=
  PersistentAirspaceCurrentShapePrimary primary ∧
    ∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction

theorem anyWrappedConditionalStep_false_of_airspaceCompoundReady
    {world : RouteBearingScopedAviationWorld}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hReady : GreenfieldAirspaceCompoundReady world primary tail) :
    anyWrappedConditionalStep (primary :: tail) = false := by
  rcases hReady with ⟨hPrimary, hTail⟩
  have hPrimaryClear :
      anyWrappedConditionalStep [primary] = false :=
    persistentAirspaceCurrentShapePrimary_not_wrappedConditional hPrimary
  have hTailClear :
      anyWrappedConditionalStep tail = false :=
    anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts hTail
  cases hPrimary <;>
    simp [anyWrappedConditionalStep] at hPrimaryClear ⊢
  all_goals exact hTailClear

def greenfieldAirspaceCompoundInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | instruction =>
      match greenfieldAirspaceInstructionRequiredAuthorityGrant? instruction with
      | some grant => some grant
      | none => routeBearingCompoundInstructionRequiredAuthorityGrant? instruction

def greenfieldAirspaceCompoundInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match greenfieldAirspaceCompoundInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def greenfieldAirspaceCompoundInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      greenfieldAirspaceCompoundInstructionIssuerAuthorized view controller instruction &&
        greenfieldAirspaceCompoundInstructionsIssuerAuthorized view controller tail

def GreenfieldAirspaceCompoundWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps,
    match greenfieldAirspaceCompoundInstructionRequiredAuthorityGrant? instruction with
    | none => True
    | some grant =>
        WorldControllerHasGrant world.toScopedAviationWorld controller grant

theorem greenfieldAirspaceCompoundInstructionIssuerAuthorized_eq_true_of_unmapped
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instruction : AtcInstruction}
    (hUnmapped : greenfieldAirspaceCompoundInstructionRequiredAuthorityGrant? instruction = none) :
    greenfieldAirspaceCompoundInstructionIssuerAuthorized view controller instruction = true := by
  simp [greenfieldAirspaceCompoundInstructionIssuerAuthorized, hUnmapped]

theorem greenfieldAirspaceCompoundInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : RouteBearingScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMapped : greenfieldAirspaceCompoundInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world.toScopedAviationWorld controller grant) :
    greenfieldAirspaceCompoundInstructionIssuerAuthorized
      (extractRouteBearingCompileView world)
      controller
      instruction = true := by
  simp [greenfieldAirspaceCompoundInstructionIssuerAuthorized, hMapped]
  change
    controllerHasAuthorityGrant
      (extractCompileView world.toScopedAviationWorld)
      controller
      grant = true
  exact
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf.baseWellFormed
      hGrant

theorem greenfieldAirspaceCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : RouteBearingScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      RouteBearingExtractionWellFormed world →
      GreenfieldAirspaceCompoundWorldAuthorized world controller steps →
        greenfieldAirspaceCompoundInstructionsIssuerAuthorized
          (extractRouteBearingCompileView world)
          controller
          steps = true := by
  intro world controller steps hWf hAuth
  induction steps with
  | nil =>
      simp [greenfieldAirspaceCompoundInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHeadAuth := hAuth head (by simp)
      have hTailAuth : GreenfieldAirspaceCompoundWorldAuthorized world controller tail := by
        intro instruction hMem
        exact hAuth instruction (by simp [hMem])
      cases hGrant : greenfieldAirspaceCompoundInstructionRequiredAuthorityGrant? head with
      | none =>
          simp [greenfieldAirspaceCompoundInstructionsIssuerAuthorized,
            greenfieldAirspaceCompoundInstructionIssuerAuthorized_eq_true_of_unmapped hGrant,
            ih hTailAuth]
      | some grant =>
          have hHeadGrant :
              WorldControllerHasGrant world.toScopedAviationWorld controller grant := by
            simpa [hGrant] using hHeadAuth
          have hHeadOk :
              greenfieldAirspaceCompoundInstructionIssuerAuthorized
                (extractRouteBearingCompileView world)
                controller
                head = true :=
            greenfieldAirspaceCompoundInstructionIssuerAuthorized_eq_true_of_worldAuthorized
              (world := world)
              (controller := controller)
              (instruction := head)
              (grant := grant)
              hWf
              hGrant
              hHeadGrant
          simp [greenfieldAirspaceCompoundInstructionsIssuerAuthorized, hHeadOk, ih hTailAuth]

inductive GreenfieldAirspaceCompoundCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | enterZone
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      {route : Option RouteSpec}
      {levelRestriction : Option Level}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps :
        content.steps =
          .clearedToEnterControlZone target airspace route levelRestriction :: tail)
      (hReady :
        GreenfieldAirspaceCompoundReady
          world
          (.clearedToEnterControlZone target airspace route levelRestriction)
          tail)
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      GreenfieldAirspaceCompoundCurrentShapeIssuable world clearance
  | specialVfr
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      {route : Option RouteSpec}
      {levelRestriction : Option Level}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps :
        content.steps =
          .specialVfrClearance target airspace route levelRestriction :: tail)
      (hReady :
        GreenfieldAirspaceCompoundReady
          world
          (.specialVfrClearance target airspace route levelRestriction)
          tail)
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      GreenfieldAirspaceCompoundCurrentShapeIssuable world clearance

theorem resolvesGreenfieldAirspaceCompoundClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hReady : GreenfieldAirspaceCompoundReady world primary tail)
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  have hWrapped :
      anyWrappedConditionalStep (primary :: tail) = false :=
    anyWrappedConditionalStep_false_of_airspaceCompoundReady hReady
  rcases hReady with ⟨hPrimary, hTailReady⟩
  rcases resolvesIndexedPlainInstruction
      (world := world)
      (state := initialState)
      (fallbackDomain := .route)
      (index := 0)
      (instruction := primary)
      (persistentAirspaceCurrentShapePrimary_needsNoSpecificResolution hPrimary) with
      ⟨primaryStep, hPrimaryStep⟩
  rcases resolvesRouteBearingImmediateAdjunctTail_of_ready
      (world := world)
      (state := initialState)
      (fallbackDomain := .route)
      (start := 1)
      (tail := tail)
      hTailReady with
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
          primary
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
          (0, primary) :: enumerateFrom 1 tail := by
      simp [hContent, hSteps, structuredInstructions, contentInstructions, indexedSteps, enumerateFrom]
    simpa [hIndexed] using
      ResolvesSteps.cons
        (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
        (state := initialState)
        (nextState := initialState)
        (finalState := initialState)
        (fallbackDomain := clearance.domain)
        (index := 0)
        (instruction := primary)
        (step := primaryStep)
        (tail := enumerateFrom 1 tail)
        (resolvedTail := resolvedTail)
        hPrimaryStep'
        hResolvedTail'

theorem GreenfieldAirspaceCompoundAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hReady : GreenfieldAirspaceCompoundReady world primary tail)
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
  rcases resolvesGreenfieldAirspaceCompoundClearance_of_ready
      (world := world)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (primary := primary)
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

theorem GreenfieldAirspaceCompoundCurrentShapeIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hReady : GreenfieldAirspaceCompoundReady world primary tail)
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none)
    (hAuthority :
      GreenfieldAirspaceCompoundWorldAuthorized
        world
        clearance.issuedBy
        (primary :: tail)) :
    ∃ resolved,
      greenfieldAirspaceCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (primary :: tail) = true ∧
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      greenfieldAirspaceCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (primary :: tail) = true :=
    greenfieldAirspaceCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := primary :: tail)
      hWf
      hAuthority
  rcases GreenfieldAirspaceCompoundAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (primary := primary)
      (tail := tail)
      hReach
      hFresh
      hContent
      hSteps
      hReady
      hDomain
      hCondition with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedEnterZoneEstablished : ResolvedClearance :=
  { source :=
      { id := "CLR-ENTER-CTR-EST"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .clearedToEnterControlZone "TEST123" "CTR-1" none none
              , .maintainAltitudeUntilEstablished
                  "TEST123"
                  (.altitudeFeet 2000)
                  .localiser ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 70
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.clearedToEnterControlZone "TEST123" "CTR-1" none none)
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

def sampleManagedResolvedEnterZoneEstablished : ManagedResolvedClearance :=
  { resolved := sampleResolvedEnterZoneEstablished }

def sampleEnterZoneEstablishedObservation : CompletionObservation :=
  { establishedApproachComponents := UniqueSet.singleton .localiser }

def sampleResolvedSpecialVfrContact : ResolvedClearance :=
  { source :=
      { id := "CLR-SVFR-CONTACT"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .specialVfrClearance "TEST123" "CTR-1" none none
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 71
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.specialVfrClearance "TEST123" "CTR-1" none none)
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .route
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedSpecialVfrContact : ManagedResolvedClearance :=
  { resolved := sampleResolvedSpecialVfrContact }

def sampleManagedResolvedSpecialVfrContactSuppressed : ManagedResolvedClearance :=
  { resolved := sampleResolvedSpecialVfrContact
    suppressedDomains := UniqueSet.singleton .frequency }

def sampleResolvedIncomingTowerContactForSpecialVfr : ResolvedClearance :=
  { source :=
      { id := "CLR-SVFR-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 72
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

theorem sampleResolvedEnterZoneEstablished_requiredCompletionStepIndices :
    sampleManagedResolvedEnterZoneEstablished.requiredCompletionStepIndices = [1] := by
  native_decide

theorem sampleResolvedEnterZoneEstablished_completes_after_adjunct_completion :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedEnterZoneEstablished
        sampleEnterZoneEstablishedObservation
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem sampleResolvedEnterZoneEstablished_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedEnterZoneEstablished]
        sampleEnterZoneEstablishedObservation
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-ENTER-CTR-EST"] := by
  native_decide

theorem sampleSpecialVfrContact_frequencySupersession_preserves_primary :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSpecialVfrContact]
        sampleResolvedIncomingTowerContactForSpecialVfr
    resolvedClearanceIds admitted.clearances = ["CLR-SVFR-CONTACT", "CLR-SVFR-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] ∧
      resolvedClearanceIds admitted.partiallySuperseded = ["CLR-SVFR-CONTACT"] ∧
      findResolvedById admitted.clearances "CLR-SVFR-CONTACT" =
        some (sampleManagedResolvedSpecialVfrContact.suppress
          (UniqueSet.singleton .frequency)) := by
  native_decide

theorem sampleSpecialVfrContact_frequencySupersession_reconcile_transitions_to_terminal :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedSpecialVfrContact]
        sampleResolvedIncomingTowerContactForSpecialVfr
    let reconciliation :=
      reconcileResolvedClearances
        admission.clearances
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-SVFR-FREQ"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-SVFR-CONTACT"] := by
  native_decide

end Greenfield
end CertifiedAtc
