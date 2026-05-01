import CertifiedAtc.GreenfieldAirspaceDeliveredCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape compound closure for the remaining current Kotlin airspace-
clearance family.

This widens the already-delivered airspace surface one more notch by adding the
missing narrow compound slice for `RemainOutsideControlledAirspace`:

- one leading `RemainOutsideControlledAirspace`
- zero or more immediate adjunct tails already understood by the current
  greenfield engine

With this module, all three current airspace-clearance families now have a
single-step slice plus a first narrow compound slice on the greenfield
boundary.
-/

def RemainOutsideAirspaceCompoundReady
    (world : RouteBearingScopedAviationWorld)
    (tail : List AtcInstruction) : Prop :=
  ∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction

inductive RemainOutsideAirspaceCompoundCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps :
        content.steps =
          .remainOutsideControlledAirspace target airspace :: tail)
      (hReady : RemainOutsideAirspaceCompoundReady world tail)
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      RemainOutsideAirspaceCompoundCurrentShapeIssuable world clearance

theorem resolvesRemainOutsideAirspaceCompoundClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {target : AircraftId}
    {airspace : AirspaceVolumeId}
    {tail : List AtcInstruction}
    (hContent : clearance.content = .compound content)
    (hSteps :
      content.steps =
        .remainOutsideControlledAirspace target airspace :: tail)
    (hReady : RemainOutsideAirspaceCompoundReady world tail)
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  have hTailWrapped :
      anyWrappedConditionalStep tail = false :=
    anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts hReady
  have hWrapped :
      anyWrappedConditionalStep
        (.remainOutsideControlledAirspace target airspace :: tail) = false := by
    simp [anyWrappedConditionalStep, hTailWrapped]
  rcases resolvesIndexedPlainInstruction
      (world := world)
      (state := initialState)
      (fallbackDomain := .route)
      (index := 0)
      (instruction := .remainOutsideControlledAirspace target airspace)
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
          (.remainOutsideControlledAirspace target airspace)
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
          (0, .remainOutsideControlledAirspace target airspace) ::
            enumerateFrom 1 tail := by
      simp [hContent, hSteps, structuredInstructions, contentInstructions, indexedSteps, enumerateFrom]
    simpa [hIndexed] using
      ResolvesSteps.cons
        (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
        (state := initialState)
        (nextState := initialState)
        (finalState := initialState)
        (fallbackDomain := clearance.domain)
        (index := 0)
        (instruction := .remainOutsideControlledAirspace target airspace)
        (step := primaryStep)
        (tail := enumerateFrom 1 tail)
        (resolvedTail := resolvedTail)
        hPrimaryStep'
        hResolvedTail'

theorem RemainOutsideAirspaceCompoundCurrentShapeIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : RemainOutsideAirspaceCompoundCurrentShapeIssuable world clearance) :
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
  rename_i content target airspace tail hContent hSteps hReady hDomain hCondition
  rcases resolvesRemainOutsideAirspaceCompoundClearance_of_ready
      (world := world)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (target := target)
      (airspace := airspace)
      (tail := tail)
      hContent
      hSteps
      hReady
      hDomain
      hCondition with
      ⟨resolved, hResolve⟩
  have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
    simpa [hResolve.sourceEq] using hFresh
  exact ⟨resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

theorem RemainOutsideAirspaceCompoundCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : RemainOutsideAirspaceCompoundCurrentShapeIssuable world clearance)
    (hAuthority :
      GreenfieldAirspaceCompoundWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ resolved,
      greenfieldAirspaceCompoundInstructionsIssuerAuthorized
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
      greenfieldAirspaceCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    greenfieldAirspaceCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases RemainOutsideAirspaceCompoundCurrentShapeIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedRemainOutsideContactCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-ROCA-COMP"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .remainOutsideControlledAirspace "TEST123" "CTR-1"
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 70
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.remainOutsideControlledAirspace "TEST123" "CTR-1")
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedRemainOutsideContactCompound : ManagedResolvedClearance :=
  { resolved := sampleResolvedRemainOutsideContactCompound }

theorem remainOutsideContactCompound_tail_completion_keeps_primary_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedRemainOutsideContactCompound
        { currentRole := some .tower }
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem incomingControlZoneClearance_partiallySupersedes_remainOutsideContactCompound :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedRemainOutsideContactCompound]
        sampleResolvedClearedToEnterControlZone
    resolvedClearanceIds admission.clearances = ["CLR-ROCA-COMP", "CLR-ENTER-CTR"] ∧
      resolvedClearanceIds admission.fullySuperseded = [] ∧
      resolvedClearanceIds admission.partiallySuperseded = ["CLR-ROCA-COMP"] ∧
      findResolvedById admission.clearances "CLR-ROCA-COMP" =
        some (sampleManagedResolvedRemainOutsideContactCompound.suppress
          (UniqueSet.singleton .route)) := by
  native_decide

end Greenfield
end CertifiedAtc
