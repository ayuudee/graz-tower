import CertifiedAtc.GreenfieldAirspaceCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldAirspaceWorldBackedCurrentShape` is the first honest world-backed
airspace layer above the existing current-shape airspace package.

Unlike `GreenfieldAirspaceCurrentShape`, this module resolves each airspace
instruction against a concrete airspace volume from the widened source world
and carries that volume's point set into the resolved payload. Completion still
remains intentionally conservative:

- `RemainOutsideControlledAirspace` does not self-complete, but it can now
  observe a concrete violation when the aircraft is at a point inside the
  resolved volume.
- `ClearedToEnterControlZone` and `SpecialVfrClearance` remain active
  permissions; they do not self-complete on volume entry.
-/

def GreenfieldAirspaceWorldBackedReady
    (world : RouteBearingScopedAviationWorld) : AtcInstruction → Prop
  | .remainOutsideControlledAirspace _ airspace =>
      ∃ volume ∈ world.airspaceVolumes, volume.id = airspace
  | .clearedToEnterControlZone _ airspace _ _ =>
      ∃ volume ∈ world.airspaceVolumes, volume.id = airspace
  | .specialVfrClearance _ airspace _ _ =>
      ∃ volume ∈ world.airspaceVolumes, volume.id = airspace
  | _ => False

def compiledWorldBackedAirspaceStep
    (index : Nat)
    (fallbackDomain : ClearanceDomain)
    (instruction : AtcInstruction)
    (airspace : AirspaceVolumeId)
    (points : List PointId)
    (hCompatible :
      resolutionCompatible
        (.airspace { airspace := airspace, points := points })
        instruction = true) :
    ResolvedStep :=
  compileResolvedStep
    index
    fallbackDomain
    instruction
    (.airspace { airspace := airspace, points := points })
    hCompatible

inductive GreenfieldAirspaceWorldBackedIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | remainOutside
      {clearance : StructuredClearance}
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      (hContent : clearance.content =
        .single (.remainOutsideControlledAirspace target airspace))
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none)
      (hReady :
        GreenfieldAirspaceWorldBackedReady
          world
          (.remainOutsideControlledAirspace target airspace)) :
      GreenfieldAirspaceWorldBackedIssuable world clearance
  | enterZone
      {clearance : StructuredClearance}
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      {route : Option RouteSpec}
      {levelRestriction : Option Level}
      (hContent : clearance.content =
        .single (.clearedToEnterControlZone target airspace route levelRestriction))
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none)
      (hReady :
        GreenfieldAirspaceWorldBackedReady
          world
          (.clearedToEnterControlZone target airspace route levelRestriction)) :
      GreenfieldAirspaceWorldBackedIssuable world clearance
  | specialVfr
      {clearance : StructuredClearance}
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      {route : Option RouteSpec}
      {levelRestriction : Option Level}
      (hContent : clearance.content =
        .single (.specialVfrClearance target airspace route levelRestriction))
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none)
      (hReady :
        GreenfieldAirspaceWorldBackedReady
          world
          (.specialVfrClearance target airspace route levelRestriction)) :
      GreenfieldAirspaceWorldBackedIssuable world clearance

theorem GreenfieldAirspaceWorldBackedAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldAirspaceWorldBackedIssuable world clearance) :
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
  case remainOutside target airspace hContent hDomain hCondition hReady =>
      rcases hReady with ⟨volume, hMem, hId⟩
      let resolved : ResolvedClearance :=
        singletonResolvedClearance
          clearance
          (compiledWorldBackedAirspaceStep
            0
            .route
            (.remainOutsideControlledAirspace target airspace)
            volume.id
            volume.points
            (by simp [resolutionCompatible]))
      have hResolve :
          ResolvesClearance
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance
            resolved
            initialState := by
        refine resolvesSingleInstructionClearance ?_ hContent hDomain ?_
        · simp [normalizeConditionalEnvelope, hContent, hCondition]
        · simpa [hDomain, resolved, singletonResolvedClearance, compiledWorldBackedAirspaceStep, hId] using
          (ResolvesIndexedStep.remainOutsideControlledAirspace
            (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
            (fallbackDomain := .route)
            (index := 0)
            (target := target)
            (airspace := volume.id)
            (points := volume.points)
            (state := initialState)
            (hAirspace := RouteBearingScopedAviationWorld.mem_airspaceVolume_of_mem hMem))
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [resolved] using hFresh
      exact ⟨resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  case enterZone target airspace route levelRestriction hContent hDomain hCondition hReady =>
      rcases hReady with ⟨volume, hMem, hId⟩
      let resolved : ResolvedClearance :=
        singletonResolvedClearance
          clearance
          (compiledWorldBackedAirspaceStep
            0
            .route
            (.clearedToEnterControlZone target airspace route levelRestriction)
            volume.id
            volume.points
            (by simp [resolutionCompatible]))
      have hResolve :
          ResolvesClearance
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance
            resolved
            initialState := by
        refine resolvesSingleInstructionClearance ?_ hContent hDomain ?_
        · simp [normalizeConditionalEnvelope, hContent, hCondition]
        · simpa [hDomain, resolved, singletonResolvedClearance, compiledWorldBackedAirspaceStep, hId] using
          (ResolvesIndexedStep.clearedToEnterControlZone
            (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
            (fallbackDomain := .route)
            (index := 0)
            (target := target)
            (airspace := volume.id)
            (route := route)
            (levelRestriction := levelRestriction)
            (points := volume.points)
            (state := initialState)
            (hAirspace := RouteBearingScopedAviationWorld.mem_airspaceVolume_of_mem hMem))
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [resolved] using hFresh
      exact ⟨resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  case specialVfr target airspace route levelRestriction hContent hDomain hCondition hReady =>
      rcases hReady with ⟨volume, hMem, hId⟩
      let resolved : ResolvedClearance :=
        singletonResolvedClearance
          clearance
          (compiledWorldBackedAirspaceStep
            0
            .route
            (.specialVfrClearance target airspace route levelRestriction)
            volume.id
            volume.points
            (by simp [resolutionCompatible]))
      have hResolve :
          ResolvesClearance
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance
            resolved
            initialState := by
        refine resolvesSingleInstructionClearance ?_ hContent hDomain ?_
        · simp [normalizeConditionalEnvelope, hContent, hCondition]
        · simpa [hDomain, resolved, singletonResolvedClearance, compiledWorldBackedAirspaceStep, hId] using
          (ResolvesIndexedStep.specialVfrClearance
            (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
            (fallbackDomain := .route)
            (index := 0)
            (target := target)
            (airspace := volume.id)
            (route := route)
            (levelRestriction := levelRestriction)
            (points := volume.points)
            (state := initialState)
            (hAirspace := RouteBearingScopedAviationWorld.mem_airspaceVolume_of_mem hMem))
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [resolved] using hFresh
      exact ⟨resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

theorem GreenfieldAirspaceWorldBackedAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldAirspaceWorldBackedIssuable world clearance)
    (hAuthority :
      WorldControllerHasGrant
        world.toScopedAviationWorld
        clearance.issuedBy
        currentShapeAirspaceAuthorityGrant) :
    ∃ resolved,
      greenfieldAirspaceInstructionsIssuerAuthorized
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
      greenfieldAirspaceInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true := by
    cases hIssuable with
    | remainOutside hContent _ _ _ =>
        exact
          greenfieldAirspaceInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
            (world := world)
            (controller := clearance.issuedBy)
            (steps := structuredInstructions clearance)
            hWf
            (by
              simp [structuredInstructions, contentInstructions, hContent,
                GreenfieldAirspaceCurrentShapeInstruction])
            hAuthority
    | enterZone hContent _ _ _ =>
        exact
          greenfieldAirspaceInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
            (world := world)
            (controller := clearance.issuedBy)
            (steps := structuredInstructions clearance)
            hWf
            (by
              simp [structuredInstructions, contentInstructions, hContent,
                GreenfieldAirspaceCurrentShapeInstruction])
            hAuthority
    | specialVfr hContent _ _ _ =>
        exact
          greenfieldAirspaceInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
            (world := world)
            (controller := clearance.issuedBy)
            (steps := structuredInstructions clearance)
            hWf
            (by
              simp [structuredInstructions, contentInstructions, hContent,
                GreenfieldAirspaceCurrentShapeInstruction])
            hAuthority
  rcases GreenfieldAirspaceWorldBackedAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleWorldBackedRemainOutsideResolved : ResolvedClearance :=
  { source :=
      { id := "CLR-WB-ROCA"
        aircraft := "TEST123"
        content := .single (.remainOutsideControlledAirspace "TEST123" "CTR-1")
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 70
        status := .active
        condition := none }
    steps :=
      [ compiledWorldBackedAirspaceStep
          0
          .route
          (.remainOutsideControlledAirspace "TEST123" "CTR-1")
          "CTR-1"
          ["P-IN-CTR"]
          (by simp [resolutionCompatible]) ] }

def sampleManagedWorldBackedRemainOutsideResolved : ManagedResolvedClearance :=
  { resolved := sampleWorldBackedRemainOutsideResolved }

def sampleWorldBackedEnterZoneResolved : ResolvedClearance :=
  { source :=
      { id := "CLR-WB-ENTER"
        aircraft := "TEST123"
        content := .single (.clearedToEnterControlZone "TEST123" "CTR-1" none none)
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 71
        status := .active
        condition := none }
    steps :=
      [ compiledWorldBackedAirspaceStep
          0
          .route
          (.clearedToEnterControlZone "TEST123" "CTR-1" none none)
          "CTR-1"
          ["P-IN-CTR"]
          (by simp [resolutionCompatible]) ] }

def sampleManagedWorldBackedEnterZoneResolved : ManagedResolvedClearance :=
  { resolved := sampleWorldBackedEnterZoneResolved }

theorem worldBackedRemainOutside_outsideVolume_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedWorldBackedRemainOutsideResolved
        { position := some "P-OUTSIDE" }
    evaluation.updated.status = .active ∧
      evaluation.stepResults.map (fun step => step.result) = [.notApplicable] := by
  native_decide

theorem worldBackedRemainOutside_insideVolume_observes_violation :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedWorldBackedRemainOutsideResolved
        { position := some "P-IN-CTR" }
    evaluation.updated.status = .active ∧
      evaluation.stepResults.map (fun step => step.result) = [.notComplete] := by
  native_decide

theorem worldBackedRemainOutside_entryTransition_observes_violation :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedWorldBackedRemainOutsideResolved
        { position := some "P-IN-CTR"
          activeAirspaces := UniqueSet.singleton "CTR-1"
          airspaceTransitions := UniqueSet.singleton "CTR-1" }
    evaluation.updated.status = .active ∧
      evaluation.stepResults.map (fun step => step.result) = [.notComplete] := by
  native_decide

theorem worldBackedRemainOutside_exitTransition_is_not_violation :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedWorldBackedRemainOutsideResolved
        { position := some "P-OUTSIDE"
          airspaceTransitions := UniqueSet.singleton "CTR-1" }
    evaluation.updated.status = .active ∧
      evaluation.stepResults.map (fun step => step.result) = [.notApplicable] := by
  native_decide

theorem worldBackedEnterZone_insideVolume_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedWorldBackedEnterZoneResolved
        { position := some "P-IN-CTR" }
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem worldBackedEnterZone_entryTransition_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedWorldBackedEnterZoneResolved
        { position := some "P-IN-CTR"
          activeAirspaces := UniqueSet.singleton "CTR-1"
          airspaceTransitions := UniqueSet.singleton "CTR-1" }
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem incomingWorldBackedControlZoneClearance_fullySupersedes_worldBackedRemainOutside :
    let admitted :=
      admitResolvedClearance
        [sampleManagedWorldBackedRemainOutsideResolved]
        sampleWorldBackedEnterZoneResolved
    resolvedClearanceIds admitted.clearances = ["CLR-WB-ENTER"] ∧
      resolvedClearanceIds admitted.fullySuperseded = ["CLR-WB-ROCA"] := by
  native_decide

end Greenfield
end CertifiedAtc
