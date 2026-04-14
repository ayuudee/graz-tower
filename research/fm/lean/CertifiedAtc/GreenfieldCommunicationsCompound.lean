import CertifiedAtc.GreenfieldRadioCurrentShape
import CertifiedAtc.GreenfieldTransponderDeliveredCurrentShape
import CertifiedAtc.GreenfieldRouteBearingCompound

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape compound closure for the delivered communications/surveillance
surface.

This widens the already-delivered single-step radio and transponder slices
through one narrow, fully packaged compound shape:

- one leading communication/surveillance instruction from the delivered
  current-shape families
- zero or more immediate adjunct tails from those same delivered families

It stays deliberately narrow:

- no new world-resolution theory
- no new authority families
- no broader coordination semantics

The point is to close the first mixed radio/transponder compound seam on the
same current execution boundary the runtime already uses.
-/

def CommunicationsCurrentShapeInstructionReady
    (world : RouteBearingScopedAviationWorld) :
    AtcInstruction → Prop
  | .contactFrequency target role frequency =>
      RadioCurrentShapeInstructionReady
        world
        (.contactFrequency target role frequency)
  | .monitorFrequency target role frequency =>
      RadioCurrentShapeInstructionReady
        world
        (.monitorFrequency target role frequency)
  | .setSquawk _ _ => True
  | .confirmSquawk _ _ => True
  | .squawkIdent _ => True
  | .squawkStandby _ => True
  | .squawkNormal _ _ => True
  | .stopSquawk _ _ => True
  | _ => False

theorem communicationsCurrentShapeInstructionReady_implies_routeBearingImmediateAdjunctReady
    {world : RouteBearingScopedAviationWorld}
    {instruction : AtcInstruction}
    (hReady : CommunicationsCurrentShapeInstructionReady world instruction) :
    RouteBearingImmediateAdjunctReady world instruction := by
  cases instruction with
  | contactFrequency target role frequency =>
      cases frequency with
      | none =>
          exact hReady
      | some frequency =>
          simp [CommunicationsCurrentShapeInstructionReady,
            RadioCurrentShapeInstructionReady,
            RouteBearingImmediateAdjunctReady] at hReady ⊢
  | monitorFrequency target role frequency =>
      cases frequency with
      | none =>
          exact hReady
      | some frequency =>
          simp [CommunicationsCurrentShapeInstructionReady,
            RadioCurrentShapeInstructionReady,
            RouteBearingImmediateAdjunctReady] at hReady ⊢
  | setSquawk target code =>
      simp [CommunicationsCurrentShapeInstructionReady, RouteBearingImmediateAdjunctReady] at hReady ⊢
  | confirmSquawk target code =>
      simp [CommunicationsCurrentShapeInstructionReady, RouteBearingImmediateAdjunctReady] at hReady ⊢
  | squawkIdent target =>
      simp [CommunicationsCurrentShapeInstructionReady, RouteBearingImmediateAdjunctReady] at hReady ⊢
  | squawkStandby target =>
      simp [CommunicationsCurrentShapeInstructionReady, RouteBearingImmediateAdjunctReady] at hReady ⊢
  | squawkNormal target mode =>
      simp [CommunicationsCurrentShapeInstructionReady, RouteBearingImmediateAdjunctReady] at hReady ⊢
  | stopSquawk target mode =>
      simp [CommunicationsCurrentShapeInstructionReady, RouteBearingImmediateAdjunctReady] at hReady ⊢
  | _ =>
      cases hReady

def CommunicationsCompoundCurrentShapeReady
    (world : RouteBearingScopedAviationWorld)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps, CommunicationsCurrentShapeInstructionReady world instruction

theorem anyWrappedConditionalStep_false_of_communicationsCurrentShapeReady :
    ∀ {world : RouteBearingScopedAviationWorld} {steps : List AtcInstruction},
      CommunicationsCompoundCurrentShapeReady world steps →
        anyWrappedConditionalStep steps = false := by
  intro world steps hReady
  apply anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts
  intro instruction hMem
  exact
    communicationsCurrentShapeInstructionReady_implies_routeBearingImmediateAdjunctReady
      (hReady instruction hMem)

abbrev communicationsCompoundInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView :=
  routeBearingCompoundInstructionRequiredAuthorityGrant?

abbrev communicationsCompoundInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  routeBearingCompoundInstructionIssuerAuthorized view controller instruction

abbrev communicationsCompoundInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool :=
  routeBearingCompoundInstructionsIssuerAuthorized view controller

abbrev CommunicationsCompoundWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  RouteBearingCompoundWorldAuthorized world controller steps

inductive GreenfieldCommunicationsCompoundCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {primary : AtcInstruction}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps : content.steps = primary :: tail)
      (hPrimary : CommunicationsCurrentShapeInstructionReady world primary)
      (hTail : ∀ instruction ∈ tail, CommunicationsCurrentShapeInstructionReady world instruction)
      (hPrimaryDomain : instructionDomain? primary = some clearance.domain)
      (hCondition : clearance.condition = none) :
      GreenfieldCommunicationsCompoundCurrentShapeIssuable world clearance

theorem resolvesGreenfieldCommunicationsCompoundClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hPrimary : CommunicationsCurrentShapeInstructionReady world primary)
    (hTail : ∀ instruction ∈ tail, CommunicationsCurrentShapeInstructionReady world instruction)
    (_hPrimaryDomain : instructionDomain? primary = some clearance.domain)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  have hAllReady :
      CommunicationsCompoundCurrentShapeReady world (primary :: tail) := by
    intro instruction hMem
    simp at hMem
    rcases hMem with rfl | hTailMem
    · exact hPrimary
    · exact hTail instruction hTailMem
  have hAllRoute :
      ∀ instruction ∈ primary :: tail, RouteBearingImmediateAdjunctReady world instruction := by
    intro instruction hMem
    exact
      communicationsCurrentShapeInstructionReady_implies_routeBearingImmediateAdjunctReady
        (hAllReady instruction hMem)
  have hWrapped : anyWrappedConditionalStep (primary :: tail) = false :=
    anyWrappedConditionalStep_false_of_communicationsCurrentShapeReady hAllReady
  rcases resolvesRouteBearingImmediateAdjunctTail_of_ready
      (world := world)
      (state := initialState)
      (fallbackDomain := clearance.domain)
      (start := 0)
      (tail := primary :: tail)
      hAllRoute with
      ⟨resolvedSteps, hResolvedSteps⟩
  refine ⟨{ source := clearance, steps := resolvedSteps }, ?_⟩
  refine ⟨?_, rfl, ?_⟩
  · simp [normalizeConditionalEnvelope, hContent, hCondition, hSteps, hWrapped]
  · have hIndexed :
        indexedSteps (structuredInstructions clearance) = enumerateFrom 0 (primary :: tail) := by
      simp [hContent, hSteps, structuredInstructions, contentInstructions, indexedSteps, enumerateFrom]
    simpa [hIndexed] using hResolvedSteps

theorem GreenfieldCommunicationsCompoundCurrentShapeIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldCommunicationsCompoundCurrentShapeIssuable world clearance) :
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
  rename_i content primary tail hContent hSteps hPrimary hTail hPrimaryDomain hCondition
  rcases resolvesGreenfieldCommunicationsCompoundClearance_of_ready
      (world := world)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (primary := primary)
      (tail := tail)
      hContent
      hSteps
      hPrimary
      hTail
      hPrimaryDomain
      hCondition with
      ⟨resolved, hResolve⟩
  have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
    simpa [hResolve.sourceEq] using hFresh
  exact ⟨resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

theorem GreenfieldCommunicationsCompoundCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldCommunicationsCompoundCurrentShapeIssuable world clearance)
    (hAuthority :
      CommunicationsCompoundWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ resolved,
      communicationsCompoundInstructionsIssuerAuthorized
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
      communicationsCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    routeBearingCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GreenfieldCommunicationsCompoundCurrentShapeIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSetSquawkContactCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-SQK-CONTACT"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .setSquawk "TEST123" 4672
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .squawk
        issuedBy := "CTRL-1"
        issuedAt := 100
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .squawk
          (.setSquawk "TEST123" 4672)
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .squawk
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedSetSquawkContactCompound : ManagedResolvedClearance :=
  { resolved := sampleResolvedSetSquawkContactCompound }

def sampleResolvedContactConfirmSquawkCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-CONTACT-CONFIRM"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .contactFrequency "TEST123" .tower (some "118.500")
              , .confirmSquawk "TEST123" 4672 ] }
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 101
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible])
      , compiledPlainResolvedStep
          1
          .frequency
          (.confirmSquawk "TEST123" 4672)
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedContactConfirmSquawkCompound : ManagedResolvedClearance :=
  { resolved := sampleResolvedContactConfirmSquawkCompound }

def sampleResolvedIncomingTowerContactReplacement : ResolvedClearance :=
  { source :=
      { id := "CLR-CONTACT-NEW"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.700"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 102
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.700"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.700" })
          (by simp [resolutionCompatible]) ] }

def sampleResolvedIncomingSetSquawkReplacement : ResolvedClearance :=
  { source :=
      { id := "CLR-SQK-NEW"
        aircraft := "TEST123"
        content := .single (.setSquawk "TEST123" 7000)
        domain := .squawk
        issuedBy := "CTRL-1"
        issuedAt := 103
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .squawk
          (.setSquawk "TEST123" 7000)
          (by simp [instructionNeedsSpecificResolution]) ] }

theorem setSquawkContact_compound_stays_active_until_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSetSquawkContactCompound
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem setSquawkContact_compound_completes_on_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSetSquawkContactCompound
        { currentRole := some .tower }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.ofList [0, 1] := by
  native_decide

theorem incomingFrequency_partially_supersedes_setSquawkContact_compound :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSetSquawkContactCompound]
        sampleResolvedIncomingTowerContactReplacement
    resolvedClearanceIds admitted.clearances = ["CLR-SQK-CONTACT", "CLR-CONTACT-NEW"] ∧
      resolvedClearanceIds admitted.partiallySuperseded = ["CLR-SQK-CONTACT"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem setSquawkContact_compound_terminalizes_after_frequency_supersession :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSetSquawkContactCompound]
        sampleResolvedIncomingTowerContactReplacement
    let reconciliation :=
      reconcileResolvedClearances admitted.clearances {} (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-CONTACT-NEW"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-SQK-CONTACT"] := by
  native_decide

theorem contactConfirmSquawk_compound_stays_active_until_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedContactConfirmSquawkCompound
        { transponderCode := some 4672 }
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem incomingSquawk_partially_supersedes_contactConfirmSquawk_compound :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedContactConfirmSquawkCompound]
        sampleResolvedIncomingSetSquawkReplacement
    resolvedClearanceIds admitted.clearances = ["CLR-CONTACT-CONFIRM", "CLR-SQK-NEW"] ∧
      resolvedClearanceIds admitted.partiallySuperseded = ["CLR-CONTACT-CONFIRM"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem contactConfirmSquawk_compound_completes_on_contact_after_tail_suppressed :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedContactConfirmSquawkCompound]
        sampleResolvedIncomingSetSquawkReplacement
    let reconciliation :=
      reconcileResolvedClearances
        admitted.clearances
        { currentRole := some .tower }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances =
        ["CLR-CONTACT-CONFIRM", "CLR-SQK-NEW"] := by
  native_decide

end Greenfield
end CertifiedAtc
