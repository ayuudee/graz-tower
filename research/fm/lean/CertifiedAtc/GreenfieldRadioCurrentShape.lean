import CertifiedAtc.RouteBearingResolutionBridge
import CertifiedAtc.GreenfieldReachability

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape closure for the radio instruction family.

This is a deliberately small, fully packaged slice for:

- `ContactFrequency`
- `MonitorFrequency`

The current runtime already has explicit resolution and completion semantics
for both, so this module closes the same surface on the greenfield Lean side:

- source-level single-step admission
- conservative radio-role authority
- explicit and implicit frequency resolution
- current lifecycle and supersession regressions
-/

def RadioCurrentShapeInstructionReady
    (world : RouteBearingScopedAviationWorld) :
    AtcInstruction → Prop
  | .contactFrequency _ _ (some _) => True
  | .contactFrequency _ role none =>
      ∃ frequency,
        (RouteBearingScopedAviationWorld.toResolutionWorld world).roleFrequency role frequency
  | .monitorFrequency _ _ (some _) => True
  | .monitorFrequency _ role none =>
      ∃ frequency,
        (RouteBearingScopedAviationWorld.toResolutionWorld world).roleFrequency role frequency
  | _ => False

def radioCurrentShapeInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | .contactFrequency _ _ _ =>
      some { entityType := .radioRole, operation := .contact }
  | .monitorFrequency _ _ _ =>
      some { entityType := .radioRole, operation := .monitor }
  | _ => none

def radioCurrentShapeInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match radioCurrentShapeInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def radioCurrentShapeInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      radioCurrentShapeInstructionIssuerAuthorized view controller instruction &&
        radioCurrentShapeInstructionsIssuerAuthorized view controller tail

def RadioCurrentShapeWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps,
    match radioCurrentShapeInstructionRequiredAuthorityGrant? instruction with
    | none => True
    | some grant =>
        WorldControllerHasGrant world.toScopedAviationWorld controller grant

@[simp] theorem radioCurrentShapeInstructionRequiredAuthorityGrant?_contact
    (target : AircraftId)
    (role : RoleName)
    (frequency : Option Frequency) :
    radioCurrentShapeInstructionRequiredAuthorityGrant?
        (.contactFrequency target role frequency) =
      some { entityType := .radioRole, operation := .contact } := by
  simp [radioCurrentShapeInstructionRequiredAuthorityGrant?]

@[simp] theorem radioCurrentShapeInstructionRequiredAuthorityGrant?_monitor
    (target : AircraftId)
    (role : RoleName)
    (frequency : Option Frequency) :
    radioCurrentShapeInstructionRequiredAuthorityGrant?
        (.monitorFrequency target role frequency) =
      some { entityType := .radioRole, operation := .monitor } := by
  simp [radioCurrentShapeInstructionRequiredAuthorityGrant?]

theorem radioCurrentShapeInstructionIssuerAuthorized_eq_true_of_unmapped
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instruction : AtcInstruction}
    (hUnmapped : radioCurrentShapeInstructionRequiredAuthorityGrant? instruction = none) :
    radioCurrentShapeInstructionIssuerAuthorized view controller instruction = true := by
  simp [radioCurrentShapeInstructionIssuerAuthorized, hUnmapped]

theorem radioCurrentShapeInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : RouteBearingScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMapped : radioCurrentShapeInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world.toScopedAviationWorld controller grant) :
    radioCurrentShapeInstructionIssuerAuthorized
      (extractRouteBearingCompileView world)
      controller
      instruction = true := by
  simp [radioCurrentShapeInstructionIssuerAuthorized, hMapped]
  change
    controllerHasAuthorityGrant
      (extractCompileView world.toScopedAviationWorld)
      controller
      grant = true
  exact
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf.baseWellFormed
      hGrant

theorem radioCurrentShapeInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : RouteBearingScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      RouteBearingExtractionWellFormed world →
      RadioCurrentShapeWorldAuthorized world controller steps →
        radioCurrentShapeInstructionsIssuerAuthorized
          (extractRouteBearingCompileView world)
          controller
          steps = true := by
  intro world controller steps hWf hAuth
  induction steps with
  | nil =>
      simp [radioCurrentShapeInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHeadAuth := hAuth head (by simp)
      have hTailAuth : RadioCurrentShapeWorldAuthorized world controller tail := by
        intro instruction hMem
        exact hAuth instruction (by simp [hMem])
      cases hGrant : radioCurrentShapeInstructionRequiredAuthorityGrant? head with
      | none =>
          simp [radioCurrentShapeInstructionsIssuerAuthorized,
            radioCurrentShapeInstructionIssuerAuthorized_eq_true_of_unmapped hGrant,
            ih hTailAuth]
      | some grant =>
          have hHeadGrant :
              WorldControllerHasGrant world.toScopedAviationWorld controller grant := by
            simpa [hGrant] using hHeadAuth
          have hHeadOk :
              radioCurrentShapeInstructionIssuerAuthorized
                (extractRouteBearingCompileView world)
                controller
                head = true :=
            radioCurrentShapeInstructionIssuerAuthorized_eq_true_of_worldAuthorized
              (world := world)
              (controller := controller)
              (instruction := head)
              (grant := grant)
              hWf
              hGrant
              hHeadGrant
          simp [radioCurrentShapeInstructionsIssuerAuthorized, hHeadOk, ih hTailAuth]

inductive GreenfieldRadioCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | contact
      {clearance : StructuredClearance}
      {target : AircraftId}
      {role : RoleName}
      {frequency : Option Frequency}
      (hContent : clearance.content = .single (.contactFrequency target role frequency))
      (hReady :
        RadioCurrentShapeInstructionReady
          world
          (.contactFrequency target role frequency))
      (hDomain : clearance.domain = .frequency)
      (hCondition : clearance.condition = none) :
      GreenfieldRadioCurrentShapeIssuable world clearance
  | monitor
      {clearance : StructuredClearance}
      {target : AircraftId}
      {role : RoleName}
      {frequency : Option Frequency}
      (hContent : clearance.content = .single (.monitorFrequency target role frequency))
      (hReady :
        RadioCurrentShapeInstructionReady
          world
          (.monitorFrequency target role frequency))
      (hDomain : clearance.domain = .frequency)
      (hCondition : clearance.condition = none) :
      GreenfieldRadioCurrentShapeIssuable world clearance

def singletonResolvedRadioClearance
    (clearance : StructuredClearance)
    (instruction : AtcInstruction)
    (role : RoleName)
    (frequency : Frequency)
    (hCompatible :
      resolutionCompatible
        (.frequencyChange { roleName := role, instructedFrequency := some frequency })
        instruction = true) :
    ResolvedClearance :=
  { source := clearance
    steps :=
      [ compileResolvedStep
          0
          .frequency
          instruction
          (.frequencyChange { roleName := role, instructedFrequency := some frequency })
          hCompatible ] }

theorem resolvesSingleContactFrequencyClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {target : AircraftId}
    {role : RoleName}
    {frequency : Option Frequency}
    (hContent : clearance.content = .single (.contactFrequency target role frequency))
    (hReady :
      RadioCurrentShapeInstructionReady
        world
        (.contactFrequency target role frequency))
    (hDomain : clearance.domain = .frequency)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  cases hFreq : frequency with
  | none =>
      have hReady' :
          ∃ resolvedFrequency,
            (RouteBearingScopedAviationWorld.toResolutionWorld world).roleFrequency
              role
              resolvedFrequency := by
        simpa [RadioCurrentShapeInstructionReady, hFreq] using hReady
      rcases hReady' with ⟨resolvedFrequency, hWorldFrequency⟩
      let resolved :=
        singletonResolvedRadioClearance
          clearance
          (.contactFrequency target role none)
          role
          resolvedFrequency
          (by simp [resolutionCompatible])
      refine ⟨resolved, ?_⟩
      refine ⟨?_, rfl, ?_⟩
      · simp [normalizeConditionalEnvelope, hContent, hCondition]
      · have hStep :
            ResolvesIndexedStep
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              .frequency
              0
              (.contactFrequency target role none)
              (compileResolvedStep
                0
                .frequency
                (.contactFrequency target role none)
                (.frequencyChange { roleName := role, instructedFrequency := some resolvedFrequency })
                (by simp [resolutionCompatible]))
              initialState := by
          apply ResolvesIndexedStep.contactFrequencyImplicit
          exact hWorldFrequency
        have hSteps :
            ResolvesSteps
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              .frequency
              [(0, .contactFrequency target role none)]
              (singletonResolvedRadioClearance
                clearance
                (.contactFrequency target role none)
                role
                resolvedFrequency
                (by simp [resolutionCompatible])).steps
              initialState := by
          apply ResolvesSteps.cons
          · simpa [singletonResolvedRadioClearance]
              using hStep
          · simpa using
              (ResolvesSteps.nil
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                (.frequency : ClearanceDomain))
        simpa [resolved, singletonResolvedRadioClearance, structuredInstructions,
          contentInstructions, indexedSteps, enumerateFrom, hContent, hDomain, hFreq]
          using hSteps
  | some explicitFrequency =>
      let resolved :=
        singletonResolvedRadioClearance
          clearance
          (.contactFrequency target role (some explicitFrequency))
          role
          explicitFrequency
          (by simp [resolutionCompatible])
      refine ⟨resolved, ?_⟩
      refine ⟨?_, rfl, ?_⟩
      · simp [normalizeConditionalEnvelope, hContent, hCondition]
      · have hStep :
            ResolvesIndexedStep
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              .frequency
              0
              (.contactFrequency target role (some explicitFrequency))
              (compileResolvedStep
                0
                .frequency
                (.contactFrequency target role (some explicitFrequency))
                (.frequencyChange { roleName := role, instructedFrequency := some explicitFrequency })
                (by simp [resolutionCompatible]))
              initialState := by
          apply ResolvesIndexedStep.contactFrequencyExplicit
        have hSteps :
            ResolvesSteps
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              .frequency
              [(0, .contactFrequency target role (some explicitFrequency))]
              (singletonResolvedRadioClearance
                clearance
                (.contactFrequency target role (some explicitFrequency))
                role
                explicitFrequency
                (by simp [resolutionCompatible])).steps
              initialState := by
          apply ResolvesSteps.cons
          · simpa [singletonResolvedRadioClearance]
              using hStep
          · simpa using
              (ResolvesSteps.nil
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                (.frequency : ClearanceDomain))
        simpa [resolved, singletonResolvedRadioClearance, structuredInstructions,
          contentInstructions, indexedSteps, enumerateFrom, hContent, hDomain, hFreq]
          using hSteps

theorem resolvesSingleMonitorFrequencyClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {target : AircraftId}
    {role : RoleName}
    {frequency : Option Frequency}
    (hContent : clearance.content = .single (.monitorFrequency target role frequency))
    (hReady :
      RadioCurrentShapeInstructionReady
        world
        (.monitorFrequency target role frequency))
    (hDomain : clearance.domain = .frequency)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  cases hFreq : frequency with
  | none =>
      have hReady' :
          ∃ resolvedFrequency,
            (RouteBearingScopedAviationWorld.toResolutionWorld world).roleFrequency
              role
              resolvedFrequency := by
        simpa [RadioCurrentShapeInstructionReady, hFreq] using hReady
      rcases hReady' with ⟨resolvedFrequency, hWorldFrequency⟩
      let resolved :=
        singletonResolvedRadioClearance
          clearance
          (.monitorFrequency target role none)
          role
          resolvedFrequency
          (by simp [resolutionCompatible])
      refine ⟨resolved, ?_⟩
      refine ⟨?_, rfl, ?_⟩
      · simp [normalizeConditionalEnvelope, hContent, hCondition]
      · have hStep :
            ResolvesIndexedStep
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              .frequency
              0
              (.monitorFrequency target role none)
              (compileResolvedStep
                0
                .frequency
                (.monitorFrequency target role none)
                (.frequencyChange { roleName := role, instructedFrequency := some resolvedFrequency })
                (by simp [resolutionCompatible]))
              initialState := by
          apply ResolvesIndexedStep.monitorFrequencyImplicit
          exact hWorldFrequency
        have hSteps :
            ResolvesSteps
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              .frequency
              [(0, .monitorFrequency target role none)]
              (singletonResolvedRadioClearance
                clearance
                (.monitorFrequency target role none)
                role
                resolvedFrequency
                (by simp [resolutionCompatible])).steps
              initialState := by
          apply ResolvesSteps.cons
          · simpa [singletonResolvedRadioClearance]
              using hStep
          · simpa using
              (ResolvesSteps.nil
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                (.frequency : ClearanceDomain))
        simpa [resolved, singletonResolvedRadioClearance, structuredInstructions,
          contentInstructions, indexedSteps, enumerateFrom, hContent, hDomain, hFreq]
          using hSteps
  | some explicitFrequency =>
      let resolved :=
        singletonResolvedRadioClearance
          clearance
          (.monitorFrequency target role (some explicitFrequency))
          role
          explicitFrequency
          (by simp [resolutionCompatible])
      refine ⟨resolved, ?_⟩
      refine ⟨?_, rfl, ?_⟩
      · simp [normalizeConditionalEnvelope, hContent, hCondition]
      · have hStep :
            ResolvesIndexedStep
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              .frequency
              0
              (.monitorFrequency target role (some explicitFrequency))
              (compileResolvedStep
                0
                .frequency
                (.monitorFrequency target role (some explicitFrequency))
                (.frequencyChange { roleName := role, instructedFrequency := some explicitFrequency })
                (by simp [resolutionCompatible]))
              initialState := by
          apply ResolvesIndexedStep.monitorFrequencyExplicit
        have hSteps :
            ResolvesSteps
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              .frequency
              [(0, .monitorFrequency target role (some explicitFrequency))]
              (singletonResolvedRadioClearance
                clearance
                (.monitorFrequency target role (some explicitFrequency))
                role
                explicitFrequency
                (by simp [resolutionCompatible])).steps
              initialState := by
          apply ResolvesSteps.cons
          · simpa [singletonResolvedRadioClearance]
              using hStep
          · simpa using
              (ResolvesSteps.nil
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                (.frequency : ClearanceDomain))
        simpa [resolved, singletonResolvedRadioClearance, structuredInstructions,
          contentInstructions, indexedSteps, enumerateFrom, hContent, hDomain, hFreq]
          using hSteps

theorem GreenfieldRadioCurrentShapeAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRadioCurrentShapeIssuable world clearance) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable with
  | contact hContent hReady hDomain hCondition =>
      rcases resolvesSingleContactFrequencyClearance_of_ready
          (world := world)
          (initialState := initialState)
          (clearance := clearance)
          hContent
          hReady
          hDomain
          hCondition with
          ⟨resolved, hResolve⟩
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [hResolve.sourceEq] using hFresh
      exact ⟨resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  | monitor hContent hReady hDomain hCondition =>
      rcases resolvesSingleMonitorFrequencyClearance_of_ready
          (world := world)
          (initialState := initialState)
          (clearance := clearance)
          hContent
          hReady
          hDomain
          hCondition with
          ⟨resolved, hResolve⟩
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [hResolve.sourceEq] using hFresh
      exact ⟨resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

theorem GreenfieldRadioCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRadioCurrentShapeIssuable world clearance)
    (hAuthority :
      RadioCurrentShapeWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ resolved,
      radioCurrentShapeInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true ∧
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      radioCurrentShapeInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    radioCurrentShapeInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GreenfieldRadioCurrentShapeAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedRadioSingleContactFrequency : ResolvedClearance :=
  { source :=
      { id := "CLR-CONTACT"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 80
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedRadioSingleContactFrequency : ManagedResolvedClearance :=
  { resolved := sampleResolvedRadioSingleContactFrequency }

def sampleResolvedRadioSingleMonitorFrequency : ResolvedClearance :=
  { source :=
      { id := "CLR-MONITOR"
        aircraft := "TEST123"
        content := .single (.monitorFrequency "TEST123" .approach (some "129.550"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 81
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.monitorFrequency "TEST123" .approach (some "129.550"))
          (.frequencyChange { roleName := .approach, instructedFrequency := some "129.550" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedRadioSingleMonitorFrequency : ManagedResolvedClearance :=
  { resolved := sampleResolvedRadioSingleMonitorFrequency }

def sampleResolvedRadioIncomingTowerContact : ResolvedClearance :=
  { source :=
      { id := "CLR-CONTACT-NEW"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.700"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 82
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.700"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.700" })
          (by simp [resolutionCompatible]) ] }

theorem singleContactFrequency_completes_on_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedRadioSingleContactFrequency
        { currentRole := some .tower }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleMonitorFrequency_completes_on_monitor :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedRadioSingleMonitorFrequency
        { currentRole := some .approach }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleContactFrequency_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedRadioSingleContactFrequency]
        { currentRole := some .tower }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-CONTACT"] := by
  native_decide

theorem incomingContactFrequency_fully_supersedes_singleMonitorFrequency :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedRadioSingleMonitorFrequency]
        sampleResolvedRadioIncomingTowerContact
    resolvedClearanceIds admitted.clearances = ["CLR-CONTACT-NEW"] ∧
      resolvedClearanceIds admitted.fullySuperseded = ["CLR-MONITOR"] := by
  native_decide

end Greenfield
end CertifiedAtc
