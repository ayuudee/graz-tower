import CertifiedAtc.RouteBearingResolutionBridge
import CertifiedAtc.GreenfieldLineUpAndWaitCurrentShape
import CertifiedAtc.GreenfieldTakeoffCurrentShape
import CertifiedAtc.GreenfieldLandingCurrentShape
import CertifiedAtc.GreenfieldTouchAndGoCurrentShape
import CertifiedAtc.GreenfieldLowApproachCurrentShape
import CertifiedAtc.GreenfieldGoAroundCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRunwayWorldBackedCurrentShape` upgrades the delivered runway
operation family from plain/current-shape closure to explicit world-backed
resolution on the current published-runway graph.

The current model is still intentionally narrow:

- each runway operation resolves against a concrete published runway path and
  threshold
- `GoAround` is world-backed only when the current runway is already known in
  the resolution state
- lifecycle consequences still come from the current completion/execution
  engine; this module is about honest single-step resolution and issuance
-/

def GreenfieldRunwayWorldBackedKnownRunway
    (world : RouteBearingScopedAviationWorld)
    (runwayId : RunwayId) : Prop :=
  ∃ runway ∈ world.toScopedAviationWorld.runways, runway.id = runwayId

def compiledWorldBackedRunwayStep
    (index : Nat)
    (fallbackDomain : ClearanceDomain)
    (instruction : AtcInstruction)
    (runway : RunwayId)
    (thresholdPoint : PointId)
    (pathPoints : List PointId)
    (hCompatible :
      resolutionCompatible
        (.runwayOperation
          { runway := runway
            thresholdPoint := thresholdPoint
            pathPoints := pathPoints })
        instruction = true) :
    ResolvedStep :=
  compileResolvedStep
    index
    fallbackDomain
    instruction
    (.runwayOperation
      { runway := runway
        thresholdPoint := thresholdPoint
        pathPoints := pathPoints })
    hCompatible

def runwayWorldBackedInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | .lineUpAndWait _ _ => some currentShapeLineUpAndWaitAuthorityGrant
  | .clearedForTakeoff _ _ => some currentShapeTakeoffAuthorityGrant
  | .clearedToLand _ _ => some currentShapeLandingAuthorityGrant
  | .clearedTouchAndGo _ _ => some currentShapeTouchAndGoAuthorityGrant
  | .clearedLowApproach _ _ => some currentShapeLowApproachAuthorityGrant
  | .goAround _ => some currentShapeGoAroundAuthorityGrant
  | _ => none

def runwayWorldBackedInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match runwayWorldBackedInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def runwayWorldBackedInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      runwayWorldBackedInstructionIssuerAuthorized view controller instruction &&
        runwayWorldBackedInstructionsIssuerAuthorized view controller tail

def GreenfieldRunwayWorldBackedWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps,
    match runwayWorldBackedInstructionRequiredAuthorityGrant? instruction with
    | none => True
    | some grant => WorldControllerHasGrant world.toScopedAviationWorld controller grant

def GreenfieldRunwayWorldBackedPrimaryReady
    (world : RouteBearingScopedAviationWorld)
    (state : ResolutionState) :
    AtcInstruction → Prop
  | .lineUpAndWait _ runway => GreenfieldRunwayWorldBackedKnownRunway world runway
  | .clearedForTakeoff _ runway => GreenfieldRunwayWorldBackedKnownRunway world runway
  | .clearedToLand _ runway => GreenfieldRunwayWorldBackedKnownRunway world runway
  | .clearedTouchAndGo _ runway => GreenfieldRunwayWorldBackedKnownRunway world runway
  | .clearedLowApproach _ runway => GreenfieldRunwayWorldBackedKnownRunway world runway
  | .goAround _ =>
      ∃ runway,
        state.currentRunway = some runway ∧
          GreenfieldRunwayWorldBackedKnownRunway world runway
  | _ => False

theorem runwayWorldBackedInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : RouteBearingScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMapped : runwayWorldBackedInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world.toScopedAviationWorld controller grant) :
    runwayWorldBackedInstructionIssuerAuthorized
      (extractRouteBearingCompileView world)
      controller
      instruction = true := by
  simp [runwayWorldBackedInstructionIssuerAuthorized, hMapped]
  change
    controllerHasAuthorityGrant
      (extractCompileView world.toScopedAviationWorld)
      controller
      grant = true
  exact
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf.baseWellFormed
      hGrant

theorem runwayWorldBackedInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : RouteBearingScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      RouteBearingExtractionWellFormed world →
      GreenfieldRunwayWorldBackedWorldAuthorized world controller steps →
        runwayWorldBackedInstructionsIssuerAuthorized
          (extractRouteBearingCompileView world)
          controller
          steps = true := by
  intro world controller steps hWf hAuth
  induction steps with
  | nil =>
      simp [runwayWorldBackedInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHeadAuth := hAuth head (by simp)
      have hTailAuth : GreenfieldRunwayWorldBackedWorldAuthorized world controller tail := by
        intro instruction hMem
        exact hAuth instruction (by simp [hMem])
      cases hGrant : runwayWorldBackedInstructionRequiredAuthorityGrant? head with
      | none =>
          simp [runwayWorldBackedInstructionsIssuerAuthorized,
            runwayWorldBackedInstructionIssuerAuthorized, hGrant, ih hTailAuth]
      | some grant =>
          have hHeadGrant :
              WorldControllerHasGrant world.toScopedAviationWorld controller grant := by
            simpa [hGrant] using hHeadAuth
          have hHeadOk :
              runwayWorldBackedInstructionIssuerAuthorized
                (extractRouteBearingCompileView world)
                controller
                head = true :=
            runwayWorldBackedInstructionIssuerAuthorized_eq_true_of_worldAuthorized
              (world := world)
              (controller := controller)
              (instruction := head)
              (grant := grant)
              hWf
              hGrant
              hHeadGrant
          simp [runwayWorldBackedInstructionsIssuerAuthorized, hHeadOk, ih hTailAuth]

theorem greenfieldRunwayWorldBackedPrimary_not_wrappedConditional
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {instruction : AtcInstruction}
    (hReady : GreenfieldRunwayWorldBackedPrimaryReady world state instruction) :
    anyWrappedConditionalStep [instruction] = false := by
  cases instruction <;>
    simp [GreenfieldRunwayWorldBackedPrimaryReady, anyWrappedConditionalStep] at hReady ⊢

theorem resolvesIndexedWorldBackedRunwayInstruction_of_ready
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {index : Nat}
    {instruction : AtcInstruction}
    (hReady : GreenfieldRunwayWorldBackedPrimaryReady world state instruction) :
    ∃ step, ∃ nextState,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        .runway
        index
        instruction
        step
        nextState := by
  cases instruction with
  | lineUpAndWait target runway =>
      rcases hReady with ⟨source, hMem, hId⟩
      refine ⟨compiledWorldBackedRunwayStep
          index
          .runway
          (.lineUpAndWait target runway)
          source.id
          source.threshold
          source.path
          (by simp [resolutionCompatible]),
          { state with currentRunway := some source.id }, ?_⟩
      simpa [compiledWorldBackedRunwayStep, hId] using
        (ResolvesIndexedStep.lineUpAndWait
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .runway)
          (index := index)
          (target := target)
          (runway := source.id)
          (thresholdPoint := source.threshold)
          (pathPoints := source.path)
          (state := state)
          (hPath := RouteBearingScopedAviationWorld.mem_runwayPath_of_mem (world := world) hMem)
          (hThreshold := RouteBearingScopedAviationWorld.mem_runwayThreshold_of_mem (world := world) hMem))
  | clearedForTakeoff target runway =>
      rcases hReady with ⟨source, hMem, hId⟩
      refine ⟨compiledWorldBackedRunwayStep
          index
          .runway
          (.clearedForTakeoff target runway)
          source.id
          source.threshold
          source.path
          (by simp [resolutionCompatible]),
          { state with currentRunway := some source.id }, ?_⟩
      simpa [compiledWorldBackedRunwayStep, hId] using
        (ResolvesIndexedStep.clearedForTakeoff
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .runway)
          (index := index)
          (target := target)
          (runway := source.id)
          (thresholdPoint := source.threshold)
          (pathPoints := source.path)
          (state := state)
          (hPath := RouteBearingScopedAviationWorld.mem_runwayPath_of_mem (world := world) hMem)
          (hThreshold := RouteBearingScopedAviationWorld.mem_runwayThreshold_of_mem (world := world) hMem))
  | clearedToLand target runway =>
      rcases hReady with ⟨source, hMem, hId⟩
      refine ⟨compiledWorldBackedRunwayStep
          index
          .runway
          (.clearedToLand target runway)
          source.id
          source.threshold
          source.path
          (by simp [resolutionCompatible]),
          { state with currentRunway := some source.id }, ?_⟩
      simpa [compiledWorldBackedRunwayStep, hId] using
        (ResolvesIndexedStep.clearedToLand
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .runway)
          (index := index)
          (target := target)
          (runway := source.id)
          (thresholdPoint := source.threshold)
          (pathPoints := source.path)
          (state := state)
          (hPath := RouteBearingScopedAviationWorld.mem_runwayPath_of_mem (world := world) hMem)
          (hThreshold := RouteBearingScopedAviationWorld.mem_runwayThreshold_of_mem (world := world) hMem))
  | clearedTouchAndGo target runway =>
      rcases hReady with ⟨source, hMem, hId⟩
      refine ⟨compiledWorldBackedRunwayStep
          index
          .runway
          (.clearedTouchAndGo target runway)
          source.id
          source.threshold
          source.path
          (by simp [resolutionCompatible]),
          { state with currentRunway := some source.id }, ?_⟩
      simpa [compiledWorldBackedRunwayStep, hId] using
        (ResolvesIndexedStep.clearedTouchAndGo
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .runway)
          (index := index)
          (target := target)
          (runway := source.id)
          (thresholdPoint := source.threshold)
          (pathPoints := source.path)
          (state := state)
          (hPath := RouteBearingScopedAviationWorld.mem_runwayPath_of_mem (world := world) hMem)
          (hThreshold := RouteBearingScopedAviationWorld.mem_runwayThreshold_of_mem (world := world) hMem))
  | clearedLowApproach target runway =>
      rcases hReady with ⟨source, hMem, hId⟩
      refine ⟨compiledWorldBackedRunwayStep
          index
          .runway
          (.clearedLowApproach target runway)
          source.id
          source.threshold
          source.path
          (by simp [resolutionCompatible]),
          { state with currentRunway := some source.id }, ?_⟩
      simpa [compiledWorldBackedRunwayStep, hId] using
        (ResolvesIndexedStep.clearedLowApproach
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .runway)
          (index := index)
          (target := target)
          (runway := source.id)
          (thresholdPoint := source.threshold)
          (pathPoints := source.path)
          (state := state)
          (hPath := RouteBearingScopedAviationWorld.mem_runwayPath_of_mem (world := world) hMem)
          (hThreshold := RouteBearingScopedAviationWorld.mem_runwayThreshold_of_mem (world := world) hMem))
  | goAround target =>
      rcases hReady with ⟨runway, hCurrentRunway, ⟨source, hMem, hId⟩⟩
      refine ⟨compiledWorldBackedRunwayStep
          index
          .runway
          (.goAround target)
          source.id
          source.threshold
          source.path
          (by simp [resolutionCompatible]),
          { state with currentRunway := some source.id }, ?_⟩
      simpa [compiledWorldBackedRunwayStep, hId] using
        (ResolvesIndexedStep.goAround
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .runway)
          (index := index)
          (target := target)
          (runway := source.id)
          (thresholdPoint := source.threshold)
          (pathPoints := source.path)
          (state := state)
          (hCurrentRunway := by simpa [hId] using hCurrentRunway)
          (hPath := RouteBearingScopedAviationWorld.mem_runwayPath_of_mem (world := world) hMem)
          (hThreshold := RouteBearingScopedAviationWorld.mem_runwayThreshold_of_mem (world := world) hMem))
  | _ =>
      cases hReady

inductive GreenfieldRunwayWorldBackedIssuable
    (world : RouteBearingScopedAviationWorld)
    (initialState : ResolutionState) :
    StructuredClearance → Prop
  | lineUpAndWait
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      (hContent : clearance.content = .single (.lineUpAndWait target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none)
      (hReady :
        GreenfieldRunwayWorldBackedPrimaryReady
          world
          initialState
          (.lineUpAndWait target runway)) :
      GreenfieldRunwayWorldBackedIssuable world initialState clearance
  | takeoff
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      (hContent : clearance.content = .single (.clearedForTakeoff target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none)
      (hReady :
        GreenfieldRunwayWorldBackedPrimaryReady
          world
          initialState
          (.clearedForTakeoff target runway)) :
      GreenfieldRunwayWorldBackedIssuable world initialState clearance
  | landing
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      (hContent : clearance.content = .single (.clearedToLand target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none)
      (hReady :
        GreenfieldRunwayWorldBackedPrimaryReady
          world
          initialState
          (.clearedToLand target runway)) :
      GreenfieldRunwayWorldBackedIssuable world initialState clearance
  | touchAndGo
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      (hContent : clearance.content = .single (.clearedTouchAndGo target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none)
      (hReady :
        GreenfieldRunwayWorldBackedPrimaryReady
          world
          initialState
          (.clearedTouchAndGo target runway)) :
      GreenfieldRunwayWorldBackedIssuable world initialState clearance
  | lowApproach
      {clearance : StructuredClearance}
      {target : AircraftId}
      {runway : RunwayId}
      (hContent : clearance.content = .single (.clearedLowApproach target runway))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none)
      (hReady :
        GreenfieldRunwayWorldBackedPrimaryReady
          world
          initialState
          (.clearedLowApproach target runway)) :
      GreenfieldRunwayWorldBackedIssuable world initialState clearance
  | goAround
      {clearance : StructuredClearance}
      {target : AircraftId}
      (hContent : clearance.content = .single (.goAround target))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none)
      (hReady :
        GreenfieldRunwayWorldBackedPrimaryReady
          world
          initialState
          (.goAround target)) :
      GreenfieldRunwayWorldBackedIssuable world initialState clearance

theorem resolvesSingleWorldBackedRunwayClearance_of_step
    {world : RouteBearingScopedAviationWorld}
    {initialState finalState : ResolutionState}
    {clearance : StructuredClearance}
    {instruction : AtcInstruction}
    {step : ResolvedStep}
    (hNormalized : normalizeConditionalEnvelope clearance = .ok clearance)
    (hContent : clearance.content = .single instruction)
    (hDomainStep : clearance.domain = step.domain)
    (hStep :
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance.domain
        0
        instruction
        step
        finalState) :
    ResolvesClearance
      (RouteBearingScopedAviationWorld.toResolutionWorld world)
      initialState
      clearance
      (singletonResolvedClearance clearance step)
      finalState := by
  exact
    resolvesSingleInstructionClearance
      (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
      (initialState := initialState)
      (finalState := finalState)
      (instruction := instruction)
      (step := step)
      (clearance := clearance)
      hNormalized
      hContent
      hDomainStep
      hStep

theorem GreenfieldRunwayWorldBackedAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRunwayWorldBackedIssuable world initialState clearance) :
    ∃ finalState, ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable
  case lineUpAndWait target runway hContent hDomain hCondition hReady =>
      rcases resolvesIndexedWorldBackedRunwayInstruction_of_ready
          (world := world)
          (state := initialState)
          (index := 0)
          (instruction := .lineUpAndWait target runway)
          hReady with
          ⟨step, finalState, hStep⟩
      let resolved := singletonResolvedClearance clearance step
      have hResolve :
          ResolvesClearance
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance
            resolved
            finalState := by
        have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
          simp [normalizeConditionalEnvelope, hContent, hCondition]
        have hStepDomain : step.domain = .runway := by
          cases hStep <;> simp [compileResolvedStep, instructionDomain?]
        have hDomainStep : clearance.domain = step.domain := by
          simpa [hStepDomain] using hDomain
        have hStepAtDomain :
            ResolvesIndexedStep
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              clearance.domain
              0
              (.lineUpAndWait target runway)
              step
              finalState := by
          simpa [hDomain] using hStep
        exact
          resolvesSingleWorldBackedRunwayClearance_of_step
            (world := world)
            (clearance := clearance)
            (instruction := .lineUpAndWait target runway)
            hNormalized
            hContent
            hDomainStep
            hStepAtDomain
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [resolved] using hFresh
      exact ⟨finalState, resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  case takeoff target runway hContent hDomain hCondition hReady =>
      rcases resolvesIndexedWorldBackedRunwayInstruction_of_ready
          (world := world)
          (state := initialState)
          (index := 0)
          (instruction := .clearedForTakeoff target runway)
          hReady with
          ⟨step, finalState, hStep⟩
      let resolved := singletonResolvedClearance clearance step
      have hResolve :
          ResolvesClearance
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance
            resolved
            finalState := by
        have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
          simp [normalizeConditionalEnvelope, hContent, hCondition]
        have hStepDomain : step.domain = .runway := by
          cases hStep <;> simp [compileResolvedStep, instructionDomain?]
        have hDomainStep : clearance.domain = step.domain := by
          simpa [hStepDomain] using hDomain
        have hStepAtDomain :
            ResolvesIndexedStep
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              clearance.domain
              0
              (.clearedForTakeoff target runway)
              step
              finalState := by
          simpa [hDomain] using hStep
        exact
          resolvesSingleWorldBackedRunwayClearance_of_step
            (world := world)
            (clearance := clearance)
            (instruction := .clearedForTakeoff target runway)
            hNormalized
            hContent
            hDomainStep
            hStepAtDomain
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [resolved] using hFresh
      exact ⟨finalState, resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  case landing target runway hContent hDomain hCondition hReady =>
      rcases resolvesIndexedWorldBackedRunwayInstruction_of_ready
          (world := world)
          (state := initialState)
          (index := 0)
          (instruction := .clearedToLand target runway)
          hReady with
          ⟨step, finalState, hStep⟩
      let resolved := singletonResolvedClearance clearance step
      have hResolve :
          ResolvesClearance
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance
            resolved
            finalState := by
        have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
          simp [normalizeConditionalEnvelope, hContent, hCondition]
        have hStepDomain : step.domain = .runway := by
          cases hStep <;> simp [compileResolvedStep, instructionDomain?]
        have hDomainStep : clearance.domain = step.domain := by
          simpa [hStepDomain] using hDomain
        have hStepAtDomain :
            ResolvesIndexedStep
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              clearance.domain
              0
              (.clearedToLand target runway)
              step
              finalState := by
          simpa [hDomain] using hStep
        exact
          resolvesSingleWorldBackedRunwayClearance_of_step
            (world := world)
            (clearance := clearance)
            (instruction := .clearedToLand target runway)
            hNormalized
            hContent
            hDomainStep
            hStepAtDomain
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [resolved] using hFresh
      exact ⟨finalState, resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  case touchAndGo target runway hContent hDomain hCondition hReady =>
      rcases resolvesIndexedWorldBackedRunwayInstruction_of_ready
          (world := world)
          (state := initialState)
          (index := 0)
          (instruction := .clearedTouchAndGo target runway)
          hReady with
          ⟨step, finalState, hStep⟩
      let resolved := singletonResolvedClearance clearance step
      have hResolve :
          ResolvesClearance
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance
            resolved
            finalState := by
        have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
          simp [normalizeConditionalEnvelope, hContent, hCondition]
        have hStepDomain : step.domain = .runway := by
          cases hStep <;> simp [compileResolvedStep, instructionDomain?]
        have hDomainStep : clearance.domain = step.domain := by
          simpa [hStepDomain] using hDomain
        have hStepAtDomain :
            ResolvesIndexedStep
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              clearance.domain
              0
              (.clearedTouchAndGo target runway)
              step
              finalState := by
          simpa [hDomain] using hStep
        exact
          resolvesSingleWorldBackedRunwayClearance_of_step
            (world := world)
            (clearance := clearance)
            (instruction := .clearedTouchAndGo target runway)
            hNormalized
            hContent
            hDomainStep
            hStepAtDomain
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [resolved] using hFresh
      exact ⟨finalState, resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  case lowApproach target runway hContent hDomain hCondition hReady =>
      rcases resolvesIndexedWorldBackedRunwayInstruction_of_ready
          (world := world)
          (state := initialState)
          (index := 0)
          (instruction := .clearedLowApproach target runway)
          hReady with
          ⟨step, finalState, hStep⟩
      let resolved := singletonResolvedClearance clearance step
      have hResolve :
          ResolvesClearance
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance
            resolved
            finalState := by
        have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
          simp [normalizeConditionalEnvelope, hContent, hCondition]
        have hStepDomain : step.domain = .runway := by
          cases hStep <;> simp [compileResolvedStep, instructionDomain?]
        have hDomainStep : clearance.domain = step.domain := by
          simpa [hStepDomain] using hDomain
        have hStepAtDomain :
            ResolvesIndexedStep
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              clearance.domain
              0
              (.clearedLowApproach target runway)
              step
              finalState := by
          simpa [hDomain] using hStep
        exact
          resolvesSingleWorldBackedRunwayClearance_of_step
            (world := world)
            (clearance := clearance)
            (instruction := .clearedLowApproach target runway)
            hNormalized
            hContent
            hDomainStep
            hStepAtDomain
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [resolved] using hFresh
      exact ⟨finalState, resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  case goAround target hContent hDomain hCondition hReady =>
      rcases resolvesIndexedWorldBackedRunwayInstruction_of_ready
          (world := world)
          (state := initialState)
          (index := 0)
          (instruction := .goAround target)
          hReady with
          ⟨step, finalState, hStep⟩
      let resolved := singletonResolvedClearance clearance step
      have hResolve :
          ResolvesClearance
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance
            resolved
            finalState := by
        have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
          simp [normalizeConditionalEnvelope, hContent, hCondition]
        have hStepDomain : step.domain = .runway := by
          cases hStep <;> simp [compileResolvedStep, instructionDomain?]
        have hDomainStep : clearance.domain = step.domain := by
          simpa [hStepDomain] using hDomain
        have hStepAtDomain :
            ResolvesIndexedStep
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              clearance.domain
              0
              (.goAround target)
              step
              finalState := by
          simpa [hDomain] using hStep
        exact
          resolvesSingleWorldBackedRunwayClearance_of_step
            (world := world)
            (clearance := clearance)
            (instruction := .goAround target)
            hNormalized
            hContent
            hDomainStep
            hStepAtDomain
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [resolved] using hFresh
      exact ⟨finalState, resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

theorem GreenfieldRunwayWorldBackedAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRunwayWorldBackedIssuable world initialState clearance)
    (hAuthority :
      GreenfieldRunwayWorldBackedWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ finalState, ∃ resolved,
      runwayWorldBackedInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true ∧
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      runwayWorldBackedInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    runwayWorldBackedInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GreenfieldRunwayWorldBackedAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨finalState, resolved, hResolve, hReachable⟩
  exact ⟨finalState, resolved, hAuthorized, hResolve, hReachable⟩

end Greenfield
end CertifiedAtc
