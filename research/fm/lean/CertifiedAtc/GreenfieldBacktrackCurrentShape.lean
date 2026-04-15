import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldReachability

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape greenfield closure for `BacktrackRunway`.

This is intentionally a small single-step slice:

- source-level single-step issuance on the current greenfield boundary
- conservative type-level runway/backtrack authority
- resolved completion against the runway far-end point
- explicit current lifecycle behavior: completion at the far end, then
  terminalization on reconciliation

This module does not widen the broader ground-movement family. It closes only
the already-modeled `BacktrackRunway` seam where Kotlin and Lean both have a
resolved far-end-point execution story.
-/

def currentShapeBacktrackAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .runway
    operation := .backtrack }

def BacktrackCurrentShapeReady
    (world : ResolutionWorld) :
    AtcInstruction → Prop
  | .backtrackRunway _ runway =>
      ∃ farEndPoint, world.farEndPointForRunway runway farEndPoint
  | _ => False

inductive BacktrackCurrentShapeIssuable
    (world : ResolutionWorld) :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      (hContent : clearance.content = .single (.backtrackRunway target runway))
      (hReady : BacktrackCurrentShapeReady world (.backtrackRunway target runway))
      (hDomain : clearance.domain = .ground)
      (hCondition : clearance.condition = none) :
      BacktrackCurrentShapeIssuable world clearance

def singletonResolvedBacktrackClearance
    (clearance : StructuredClearance)
    (target : AircraftId)
    (runway : RunwayId)
    (farEndPoint : PointId) :
    ResolvedClearance :=
  { source := clearance
    steps :=
      [ compileResolvedStep
          0
          .ground
          (.backtrackRunway target runway)
          (.backtrack { runway := runway, farEndPoint := farEndPoint })
          (by simp [resolutionCompatible]) ] }

theorem resolvesSingleBacktrackClearance_of_ready
    {world : ResolutionWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {target : AircraftId}
    {runway : RunwayId}
    (hContent : clearance.content = .single (.backtrackRunway target runway))
    (hReady : BacktrackCurrentShapeReady world (.backtrackRunway target runway))
    (hDomain : clearance.domain = .ground)
    (hCondition : clearance.condition = none) :
    ∃ farEndPoint, ∃ resolved,
      ResolvesClearance
        world
        initialState
        clearance
        resolved
        { currentPoint := some farEndPoint, currentRunway := some runway } := by
  rcases hReady with ⟨farEndPoint, hFarEnd⟩
  let resolved :=
    singletonResolvedBacktrackClearance clearance target runway farEndPoint
  refine ⟨farEndPoint, resolved, ?_⟩
  refine ⟨?_, rfl, ?_⟩
  · simp [normalizeConditionalEnvelope, hContent, hCondition]
  · have hStep :
        ResolvesIndexedStep
          world
          initialState
          clearance.domain
          0
          (.backtrackRunway target runway)
          (compileResolvedStep
            0
            .ground
            (.backtrackRunway target runway)
            (.backtrack { runway := runway, farEndPoint := farEndPoint })
            (by simp [resolutionCompatible]))
          { currentPoint := some farEndPoint, currentRunway := some runway } := by
      simpa [hDomain] using
        (ResolvesIndexedStep.backtrack
          world
          .ground
          0
          target
          runway
          farEndPoint
          initialState
          hFarEnd)
    have hSteps :
        ResolvesSteps
          world
          initialState
          clearance.domain
          [(0, .backtrackRunway target runway)]
          resolved.steps
          { currentPoint := some farEndPoint, currentRunway := some runway } := by
      apply ResolvesSteps.cons
      · simpa [resolved, singletonResolvedBacktrackClearance] using hStep
      · simpa using
          (ResolvesSteps.nil
            world
            ({ currentPoint := some farEndPoint, currentRunway := some runway } : ResolutionState)
            clearance.domain)
    simpa [resolved, singletonResolvedBacktrackClearance, structuredInstructions,
      contentInstructions, indexedSteps, enumerateFrom, hContent, hDomain]
      using hSteps

theorem BacktrackCurrentShapeIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : BacktrackCurrentShapeIssuable world clearance) :
    ∃ runway, ∃ farEndPoint, ∃ resolved,
      ResolvesClearance
        world
        initialState
        clearance
        resolved
        { currentPoint := some farEndPoint, currentRunway := some runway } ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable
  rename_i target runway hContent hReady hDomain hCondition
  rcases resolvesSingleBacktrackClearance_of_ready
      (world := world)
      (initialState := initialState)
      (clearance := clearance)
      (target := target)
      (runway := runway)
      hContent
      hReady
      hDomain
      hCondition with
      ⟨farEndPoint, resolved, hResolve⟩
  have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
    simpa [hResolve.sourceEq] using hFresh
  exact
    ⟨runway, farEndPoint, resolved, hResolve,
      ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

theorem BacktrackCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : BacktrackCurrentShapeIssuable resolutionWorld clearance)
    (hAuthority :
      WorldControllerHasGrant
        compileWorld
        clearance.issuedBy
        currentShapeBacktrackAuthorityGrant) :
    ∃ runway, ∃ farEndPoint, ∃ resolved,
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeBacktrackAuthorityGrant = true ∧
      ResolvesClearance
        resolutionWorld
        initialState
        clearance
        resolved
        { currentPoint := some farEndPoint, currentRunway := some runway } ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      controllerHasAuthorityGrant
        (extractCompileView compileWorld)
        clearance.issuedBy
        currentShapeBacktrackAuthorityGrant = true :=
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf
      hAuthority
  rcases BacktrackCurrentShapeIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨runway, farEndPoint, resolved, hResolve, hReachable⟩
  exact ⟨runway, farEndPoint, resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleBacktrack : ResolvedClearance :=
  { source :=
      { id := "CLR-BACKTRACK"
        aircraft := "TEST123"
        content := .single (.backtrackRunway "TEST123" "27")
        domain := .ground
        issuedBy := "CTRL-1"
        issuedAt := 90
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .ground
          (.backtrackRunway "TEST123" "27")
          (.backtrack { runway := "27", farEndPoint := "RWY27-FAR" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedSingleBacktrack : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleBacktrack }

def sampleResolvedIncomingTowerContactForBacktrack : ResolvedClearance :=
  { source :=
      { id := "CLR-BACKTRACK-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 91
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

theorem singleBacktrack_completes_at_far_end :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleBacktrack
        { position := some "RWY27-FAR" }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleBacktrack_completes_on_traversed_far_end_progress :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleBacktrack
        { position := some "RWY-X"
          traversedGroundPoints := UniqueSet.singleton "RWY27-FAR"
          onGround := true }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleBacktrack_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleBacktrack]
        { position := some "RWY27-FAR" }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-BACKTRACK"] := by
  native_decide

theorem singleBacktrack_reconcile_progress_completion_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedSingleBacktrack]
        { position := some "RWY-X"
          traversedGroundPoints := UniqueSet.singleton "RWY27-FAR"
          onGround := true }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-BACKTRACK"] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleBacktrack :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleBacktrack]
        sampleResolvedIncomingTowerContactForBacktrack
    resolvedClearanceIds admitted.clearances = ["CLR-BACKTRACK", "CLR-BACKTRACK-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

end Greenfield
end CertifiedAtc
