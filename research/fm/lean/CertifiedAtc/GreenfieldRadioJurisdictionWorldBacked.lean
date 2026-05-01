import CertifiedAtc.GreenfieldRadioCurrentShape
import CertifiedAtc.GreenfieldReachability

namespace CertifiedAtc
namespace Greenfield

/--
World-backed published-handoff widening for radio instructions.

This sits above the delivered immediate radio model and adds one concrete
coordination/jurisdiction layer from the runtime:

- published handoff steps between roles
- issue-time issuer-role context
- completion that requires both the radio action and the published handoff
  condition

It stays deliberately bounded:

- no richer controller-jurisdiction matrix than the existing role grants
- no broader multi-unit coordination workflow
- no surveillance automation beyond the delivered transponder branch
-/

def PublishedHandoffRadioInstructionReady
    (world : RouteBearingScopedAviationWorld)
    (initialState : ResolutionState) :
    AtcInstruction → Prop
  | .contactFrequency _ role (some _) =>
      ∃ fromRole handoffPoint,
        initialState.currentRole = some fromRole ∧
          (RouteBearingScopedAviationWorld.toResolutionWorld world).publishedHandoff
            fromRole
            role
            .contact
            handoffPoint
  | .contactFrequency _ role none =>
      ∃ fromRole handoffPoint frequency,
        initialState.currentRole = some fromRole ∧
          (RouteBearingScopedAviationWorld.toResolutionWorld world).roleFrequency role frequency ∧
          (RouteBearingScopedAviationWorld.toResolutionWorld world).publishedHandoff
            fromRole
            role
            .contact
            handoffPoint
  | .monitorFrequency _ role (some _) =>
      ∃ fromRole handoffPoint,
        initialState.currentRole = some fromRole ∧
          (RouteBearingScopedAviationWorld.toResolutionWorld world).publishedHandoff
            fromRole
            role
            .monitor
            handoffPoint
  | .monitorFrequency _ role none =>
      ∃ fromRole handoffPoint frequency,
        initialState.currentRole = some fromRole ∧
          (RouteBearingScopedAviationWorld.toResolutionWorld world).roleFrequency role frequency ∧
          (RouteBearingScopedAviationWorld.toResolutionWorld world).publishedHandoff
            fromRole
            role
            .monitor
            handoffPoint
  | _ => False

inductive GreenfieldRadioJurisdictionWorldBackedIssuable
    (world : RouteBearingScopedAviationWorld)
    (initialState : ResolutionState) :
    StructuredClearance → Prop
  | contact
      {clearance : StructuredClearance}
      {target : AircraftId}
      {role : RoleName}
      {frequency : Option Frequency}
      (hContent : clearance.content = .single (.contactFrequency target role frequency))
      (hReady :
        PublishedHandoffRadioInstructionReady
          world
          initialState
          (.contactFrequency target role frequency))
      (hDomain : clearance.domain = .frequency)
      (hCondition : clearance.condition = none) :
      GreenfieldRadioJurisdictionWorldBackedIssuable world initialState clearance
  | monitor
      {clearance : StructuredClearance}
      {target : AircraftId}
      {role : RoleName}
      {frequency : Option Frequency}
      (hContent : clearance.content = .single (.monitorFrequency target role frequency))
      (hReady :
        PublishedHandoffRadioInstructionReady
          world
          initialState
          (.monitorFrequency target role frequency))
      (hDomain : clearance.domain = .frequency)
      (hCondition : clearance.condition = none) :
      GreenfieldRadioJurisdictionWorldBackedIssuable world initialState clearance

def singletonResolvedPublishedRadioClearance
    (clearance : StructuredClearance)
    (instruction : AtcInstruction)
    (role : RoleName)
    (frequency : Frequency)
    (fromRole : RoleName)
    (action : ResolvedPublishedHandoffAction)
    (handoffPoint : ResolvedPublishedHandoffPoint)
    (hCompatible :
      resolutionCompatible
        (.frequencyChange
          { roleName := role
            instructedFrequency := some frequency
            publishedHandoff :=
              some
                { fromRole := fromRole
                  toRole := role
                  action := action
                  location := handoffPoint } })
        instruction = true) :
    ResolvedClearance :=
  { source := clearance
    steps :=
      [ compileResolvedStep
          0
          .frequency
          instruction
          (.frequencyChange
            { roleName := role
              instructedFrequency := some frequency
              publishedHandoff :=
                some
                  { fromRole := fromRole
                    toRole := role
                    action := action
                    location := handoffPoint } })
          hCompatible ] }

theorem resolvesSingleContactPublishedHandoffClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {target : AircraftId}
    {role : RoleName}
    {frequency : Option Frequency}
    (hContent : clearance.content = .single (.contactFrequency target role frequency))
    (hReady :
      PublishedHandoffRadioInstructionReady
        world
        initialState
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
          ∃ fromRole handoffPoint resolvedFrequency,
            initialState.currentRole = some fromRole ∧
              (RouteBearingScopedAviationWorld.toResolutionWorld world).roleFrequency
                role
                resolvedFrequency ∧
              (RouteBearingScopedAviationWorld.toResolutionWorld world).publishedHandoff
                fromRole
                role
                .contact
                handoffPoint := by
        simpa [PublishedHandoffRadioInstructionReady, hFreq] using hReady
      rcases hReady' with ⟨fromRole, handoffPoint, resolvedFrequency, hRole, hWorldFrequency, hHandoff⟩
      let resolved :=
        singletonResolvedPublishedRadioClearance
          clearance
          (.contactFrequency target role none)
          role
          resolvedFrequency
          fromRole
          .contact
          handoffPoint
          (by simp [resolutionCompatible])
      refine ⟨resolved, ?_⟩
      apply resolvesSingleInstructionClearance
      · simp [normalizeConditionalEnvelope, hContent, hCondition]
      · exact hContent
      · exact hDomain
      · simpa [hDomain, resolved, singletonResolvedPublishedRadioClearance, hFreq] using
          (ResolvesIndexedStep.contactFrequencyImplicitPublished
            (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
            (fallbackDomain := .frequency)
            (index := 0)
            (target := target)
            (fromRole := fromRole)
            (role := role)
            (frequency := resolvedFrequency)
            (handoffPoint := handoffPoint)
            (state := initialState)
            hWorldFrequency
            hRole
            hHandoff)
  | some explicitFrequency =>
      have hReady' :
          ∃ fromRole handoffPoint,
            initialState.currentRole = some fromRole ∧
              (RouteBearingScopedAviationWorld.toResolutionWorld world).publishedHandoff
                fromRole
                role
                .contact
                handoffPoint := by
        simpa [PublishedHandoffRadioInstructionReady, hFreq] using hReady
      rcases hReady' with ⟨fromRole, handoffPoint, hRole, hHandoff⟩
      let resolved :=
        singletonResolvedPublishedRadioClearance
          clearance
          (.contactFrequency target role (some explicitFrequency))
          role
          explicitFrequency
          fromRole
          .contact
          handoffPoint
          (by simp [resolutionCompatible])
      refine ⟨resolved, ?_⟩
      apply resolvesSingleInstructionClearance
      · simp [normalizeConditionalEnvelope, hContent, hCondition]
      · exact hContent
      · exact hDomain
      · simpa [hDomain, resolved, singletonResolvedPublishedRadioClearance, hFreq] using
          (ResolvesIndexedStep.contactFrequencyExplicitPublished
            (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
            (fallbackDomain := .frequency)
            (index := 0)
            (target := target)
            (fromRole := fromRole)
            (role := role)
            (frequency := explicitFrequency)
            (handoffPoint := handoffPoint)
            (state := initialState)
            hRole
            hHandoff)

theorem resolvesSingleMonitorPublishedHandoffClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {target : AircraftId}
    {role : RoleName}
    {frequency : Option Frequency}
    (hContent : clearance.content = .single (.monitorFrequency target role frequency))
    (hReady :
      PublishedHandoffRadioInstructionReady
        world
        initialState
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
          ∃ fromRole handoffPoint resolvedFrequency,
            initialState.currentRole = some fromRole ∧
              (RouteBearingScopedAviationWorld.toResolutionWorld world).roleFrequency
                role
                resolvedFrequency ∧
              (RouteBearingScopedAviationWorld.toResolutionWorld world).publishedHandoff
                fromRole
                role
                .monitor
                handoffPoint := by
        simpa [PublishedHandoffRadioInstructionReady, hFreq] using hReady
      rcases hReady' with ⟨fromRole, handoffPoint, resolvedFrequency, hRole, hWorldFrequency, hHandoff⟩
      let resolved :=
        singletonResolvedPublishedRadioClearance
          clearance
          (.monitorFrequency target role none)
          role
          resolvedFrequency
          fromRole
          .monitor
          handoffPoint
          (by simp [resolutionCompatible])
      refine ⟨resolved, ?_⟩
      apply resolvesSingleInstructionClearance
      · simp [normalizeConditionalEnvelope, hContent, hCondition]
      · exact hContent
      · exact hDomain
      · simpa [hDomain, resolved, singletonResolvedPublishedRadioClearance, hFreq] using
          (ResolvesIndexedStep.monitorFrequencyImplicitPublished
            (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
            (fallbackDomain := .frequency)
            (index := 0)
            (target := target)
            (fromRole := fromRole)
            (role := role)
            (frequency := resolvedFrequency)
            (handoffPoint := handoffPoint)
            (state := initialState)
            hWorldFrequency
            hRole
            hHandoff)
  | some explicitFrequency =>
      have hReady' :
          ∃ fromRole handoffPoint,
            initialState.currentRole = some fromRole ∧
              (RouteBearingScopedAviationWorld.toResolutionWorld world).publishedHandoff
                fromRole
                role
                .monitor
                handoffPoint := by
        simpa [PublishedHandoffRadioInstructionReady, hFreq] using hReady
      rcases hReady' with ⟨fromRole, handoffPoint, hRole, hHandoff⟩
      let resolved :=
        singletonResolvedPublishedRadioClearance
          clearance
          (.monitorFrequency target role (some explicitFrequency))
          role
          explicitFrequency
          fromRole
          .monitor
          handoffPoint
          (by simp [resolutionCompatible])
      refine ⟨resolved, ?_⟩
      apply resolvesSingleInstructionClearance
      · simp [normalizeConditionalEnvelope, hContent, hCondition]
      · exact hContent
      · exact hDomain
      · simpa [hDomain, resolved, singletonResolvedPublishedRadioClearance, hFreq] using
          (ResolvesIndexedStep.monitorFrequencyExplicitPublished
            (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
            (fallbackDomain := .frequency)
            (index := 0)
            (target := target)
            (fromRole := fromRole)
            (role := role)
            (frequency := explicitFrequency)
            (handoffPoint := handoffPoint)
            (state := initialState)
            hRole
            hHandoff)

theorem GreenfieldRadioJurisdictionWorldBackedReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRadioJurisdictionWorldBackedIssuable world initialState clearance) :
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
      rcases resolvesSingleContactPublishedHandoffClearance_of_ready
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
      rcases resolvesSingleMonitorPublishedHandoffClearance_of_ready
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

theorem GreenfieldRadioJurisdictionWorldBackedAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRadioJurisdictionWorldBackedIssuable world initialState clearance)
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
  have hAuthChecked :
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
  rcases GreenfieldRadioJurisdictionWorldBackedReachableIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachResolved⟩
  exact ⟨resolved, hAuthChecked, hResolve, hReachResolved⟩

def samplePublishedHoldingPointContact : ResolvedClearance :=
  { source :=
      { id := "CLR-RADIO-HOLD"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 1
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange
            { roleName := .tower
              instructedFrequency := some "118.500"
              publishedHandoff :=
                some
                  { fromRole := .ground
                    toRole := .tower
                    action := .contact
                    location := .holdingPoint "P-HOLD" } })
          (by native_decide) ] }

def samplePublishedHoldingPointContactBeforeObservation : CompletionObservation :=
  { position := some "P-APRON"
    currentRole := some .tower
    currentFrequency := some "118.500"
    lastContactRole := some .tower
    onGround := true }

def samplePublishedHoldingPointContactAfterObservation : CompletionObservation :=
  { position := some "P-HOLD"
    currentRole := some .tower
    currentFrequency := some "118.500"
    lastContactRole := some .tower
    onGround := true }

def samplePublishedBoundaryFixMonitor : ResolvedClearance :=
  { source :=
      { id := "CLR-RADIO-BOUNDARY"
        aircraft := "TEST123"
        content := .single (.monitorFrequency "TEST123" .approach (some "120.100"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 1
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.monitorFrequency "TEST123" .approach (some "120.100"))
          (.frequencyChange
            { roleName := .approach
              instructedFrequency := some "120.100"
              publishedHandoff :=
                some
                  { fromRole := .tower
                    toRole := .approach
                    action := .monitor
                    location := .boundaryFix "HOLD" } })
          (by native_decide) ] }

def samplePublishedBoundaryFixObservation : CompletionObservation :=
  { position := some "P-HOLD"
    reachedFixes := UniqueSet.singleton "HOLD"
    currentRole := some .approach
    currentFrequency := some "120.100"
    lastContactRole := some .approach
    onGround := false }

def samplePublishedContactCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-RADIO-COMPOUND"
        aircraft := "TEST123"
        content :=
          .compound
            { steps :=
                [ .setSquawk "TEST123" 4672,
                  .contactFrequency "TEST123" .tower (some "118.500") ]
              completedSteps := {} }
        domain := .squawk
        issuedBy := "CTRL-1"
        issuedAt := 1
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .squawk
          (.setSquawk "TEST123" 4672)
          .plain
          (by native_decide),
        compileResolvedStep
          1
          .squawk
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange
            { roleName := .tower
              instructedFrequency := some "118.500"
              publishedHandoff :=
                some
                  { fromRole := .ground
                    toRole := .tower
                    action := .contact
                    location := .holdingPoint "P-HOLD" } })
          (by native_decide) ] }

def sampleManagedPublishedHoldingPointContact : ManagedResolvedClearance :=
  { resolved := samplePublishedHoldingPointContact }

def sampleManagedPublishedBoundaryFixMonitor : ManagedResolvedClearance :=
  { resolved := samplePublishedBoundaryFixMonitor }

def sampleManagedPublishedContactCompound : ManagedResolvedClearance :=
  { resolved := samplePublishedContactCompound }

theorem publishedHoldingPointContact_stays_active_before_handoff_point :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedPublishedHoldingPointContact
        samplePublishedHoldingPointContactBeforeObservation
    evaluation.updated.status = .active := by
  native_decide

theorem publishedHoldingPointContact_completes_at_handoff_point :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedPublishedHoldingPointContact
        samplePublishedHoldingPointContactAfterObservation
    evaluation.updated.status = .completed := by
  native_decide

theorem publishedBoundaryFixMonitor_completes_at_boundary_fix :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedPublishedBoundaryFixMonitor
        samplePublishedBoundaryFixObservation
    evaluation.updated.status = .completed := by
  native_decide

theorem publishedContactCompound_waits_for_handoff_point :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedPublishedContactCompound
        samplePublishedHoldingPointContactBeforeObservation
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem publishedContactCompound_completes_after_handoff_point :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedPublishedContactCompound
        samplePublishedHoldingPointContactAfterObservation
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.ofList [0, 1] := by
  native_decide

end Greenfield
end CertifiedAtc
