import CertifiedAtc.GroundMovementResolutionBridge
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape / current-model closure for the delivered ground-movement branch.

This branch is intentionally mixed and explicit:

- `TaxiTo`, `HoldShortOf`, and `CrossRunway` are world-backed on the current
  graph-backed ground model
- `HoldPosition` remains current-shape plain-step semantics on the current
  engine
- the compound layer is a first narrow sequential ground trace over those same
  instructions

This mirrors the Kotlin runtime boundary honestly instead of pretending all four
families share the same resolution depth today.
-/

def currentShapeTaxiAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .taxiway
    operation := .taxi }

def currentShapeHoldPositionAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .taxiway
    operation := .taxi }

def currentShapeHoldShortAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .runway
    operation := .cross }

def currentShapeCrossRunwayAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .runway
    operation := .cross }

def groundMovementInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | .taxiTo _ _ _ => some currentShapeTaxiAuthorityGrant
  | .holdPosition _ => some currentShapeHoldPositionAuthorityGrant
  | .holdShortOf _ _ => some currentShapeHoldShortAuthorityGrant
  | .crossRunway _ _ => some currentShapeCrossRunwayAuthorityGrant
  | _ => none

def groundMovementInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match groundMovementInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def groundMovementInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      groundMovementInstructionIssuerAuthorized view controller instruction &&
        groundMovementInstructionsIssuerAuthorized view controller tail

def GroundMovementWorldAuthorized
    (world : GroundMovementScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps,
    match groundMovementInstructionRequiredAuthorityGrant? instruction with
    | none => True
    | some grant =>
        WorldControllerHasGrant world.toScopedAviationWorld controller grant

theorem groundMovementInstructionIssuerAuthorized_eq_true_of_unmapped
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instruction : AtcInstruction}
    (hUnmapped : groundMovementInstructionRequiredAuthorityGrant? instruction = none) :
    groundMovementInstructionIssuerAuthorized view controller instruction = true := by
  simp [groundMovementInstructionIssuerAuthorized, hUnmapped]

theorem groundMovementInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : GroundMovementScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : GroundMovementExtractionWellFormed world)
    (hMapped : groundMovementInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world.toScopedAviationWorld controller grant) :
    groundMovementInstructionIssuerAuthorized
      (extractCompileView world.toScopedAviationWorld)
      controller
      instruction = true := by
  simp [groundMovementInstructionIssuerAuthorized, hMapped]
  exact
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf.baseWellFormed
      hGrant

theorem groundMovementInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : GroundMovementScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      GroundMovementExtractionWellFormed world →
      GroundMovementWorldAuthorized world controller steps →
        groundMovementInstructionsIssuerAuthorized
          (extractCompileView world.toScopedAviationWorld)
          controller
          steps = true := by
  intro world controller steps hWf hAuth
  induction steps with
  | nil =>
      simp [groundMovementInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHeadAuth := hAuth head (by simp)
      have hTailAuth : GroundMovementWorldAuthorized world controller tail := by
        intro instruction hMem
        exact hAuth instruction (by simp [hMem])
      cases hGrant : groundMovementInstructionRequiredAuthorityGrant? head with
      | none =>
          simp [groundMovementInstructionsIssuerAuthorized,
            groundMovementInstructionIssuerAuthorized_eq_true_of_unmapped hGrant,
            ih hTailAuth]
      | some grant =>
          have hHeadGrant :
              WorldControllerHasGrant world.toScopedAviationWorld controller grant := by
            simpa [hGrant] using hHeadAuth
          have hHeadOk :
              groundMovementInstructionIssuerAuthorized
                (extractCompileView world.toScopedAviationWorld)
                controller
                head = true :=
            groundMovementInstructionIssuerAuthorized_eq_true_of_worldAuthorized
              (world := world)
              (controller := controller)
              (instruction := head)
              (grant := grant)
              hWf
              hGrant
              hHeadGrant
          simp [groundMovementInstructionsIssuerAuthorized, hHeadOk, ih hTailAuth]

inductive GroundMovementTraceReady
    (world : GroundMovementScopedAviationWorld) :
    ResolutionState →
    Nat →
    List AtcInstruction →
    List ResolvedStep →
    ResolutionState →
    Prop
  | nil
      (state : ResolutionState)
      (index : Nat) :
      GroundMovementTraceReady world state index [] [] state
  | taxi
      (index : Nat)
      (target : AircraftId)
      (start destination : PointId)
      (via path : List PointId)
      (tail : List AtcInstruction)
      (resolvedTail : List ResolvedStep)
      (finalState : ResolutionState)
      (hRoute :
        (GroundMovementScopedAviationWorld.toResolutionWorld world).taxiRoute
          start
          destination
          path)
      (hTail :
        GroundMovementTraceReady
          world
          { currentPoint := some destination }
          (index + 1)
          tail
          resolvedTail
          finalState) :
      GroundMovementTraceReady
        world
        { currentPoint := some start }
        index
        (.taxiTo target destination via :: tail)
        (compileResolvedStep
          index
          .ground
          (.taxiTo target destination via)
          (.taxi { destination := destination, path := path })
          (by simp [resolutionCompatible]) :: resolvedTail)
        finalState
  | holdShort
      (state : ResolutionState)
      (index : Nat)
      (target : AircraftId)
      (runway : RunwayId)
      (point : PointId)
      (tail : List AtcInstruction)
      (resolvedTail : List ResolvedStep)
      (finalState : ResolutionState)
      (hPoint :
        (GroundMovementScopedAviationWorld.toResolutionWorld world).holdingPointForRunway
          runway
          point)
      (hTail :
        GroundMovementTraceReady
          world
          { currentPoint := some point }
          (index + 1)
          tail
          resolvedTail
          finalState) :
      GroundMovementTraceReady
        world
        state
        index
        (.holdShortOf target runway :: tail)
        (compileResolvedStep
          index
          .ground
          (.holdShortOf target runway)
          (.holdShort { runway := runway, point := point })
          (by simp [resolutionCompatible]) :: resolvedTail)
        finalState
  | crossing
      (state : ResolutionState)
      (index : Nat)
      (target : AircraftId)
      (runway : RunwayId)
      (point : PointId)
      (tail : List AtcInstruction)
      (resolvedTail : List ResolvedStep)
      (finalState : ResolutionState)
      (hPoint :
        (GroundMovementScopedAviationWorld.toResolutionWorld world).crossingPointForRunway
          runway
          point)
      (hTail :
        GroundMovementTraceReady
          world
          { currentPoint := some point }
          (index + 1)
          tail
          resolvedTail
          finalState) :
      GroundMovementTraceReady
        world
        state
        index
        (.crossRunway target runway :: tail)
        (compileResolvedStep
          index
          .ground
          (.crossRunway target runway)
          (.crossing { runway := runway, crossingPoint := point })
          (by simp [resolutionCompatible]) :: resolvedTail)
        finalState
  | holdPosition
      (state : ResolutionState)
      (index : Nat)
      (target : AircraftId)
      (tail : List AtcInstruction)
      (resolvedTail : List ResolvedStep)
      (finalState : ResolutionState)
      (hTail :
        GroundMovementTraceReady
          world
          state
          (index + 1)
          tail
          resolvedTail
          finalState) :
      GroundMovementTraceReady
        world
        state
        index
        (.holdPosition target :: tail)
        (compiledPlainResolvedStep
          index
          .ground
          (.holdPosition target)
          (by simp [instructionNeedsSpecificResolution]) :: resolvedTail)
        finalState

theorem GroundMovementTraceReady.anyWrappedConditionalStep_false :
    ∀ {world : GroundMovementScopedAviationWorld}
      {state : ResolutionState}
      {index : Nat}
      {steps : List AtcInstruction}
      {resolved : List ResolvedStep}
      {finalState : ResolutionState},
      GroundMovementTraceReady world state index steps resolved finalState →
        anyWrappedConditionalStep steps = false := by
  intro world state index steps resolved finalState hReady
  induction hReady <;> simp [anyWrappedConditionalStep, *]

theorem GroundMovementTraceReady.resolvesSteps :
    ∀ {world : GroundMovementScopedAviationWorld}
      {state : ResolutionState}
      {index : Nat}
      {steps : List AtcInstruction}
      {resolved : List ResolvedStep}
      {finalState : ResolutionState},
      GroundMovementTraceReady world state index steps resolved finalState →
        ResolvesSteps
          (GroundMovementScopedAviationWorld.toResolutionWorld world)
          state
          .ground
          (enumerateFrom index steps)
          resolved
          finalState := by
  intro world state index steps resolved finalState hReady
  induction hReady with
  | nil state index =>
      simpa [enumerateFrom] using
        (ResolvesSteps.nil
          (GroundMovementScopedAviationWorld.toResolutionWorld world)
          state
          .ground)
  | taxi index target start destination via path tail resolvedTail finalState hRoute hTail ih =>
      have hStep :
          ResolvesIndexedStep
            (GroundMovementScopedAviationWorld.toResolutionWorld world)
            { currentPoint := some start }
            .ground
            index
            (.taxiTo target destination via)
            (compileResolvedStep
              index
              .ground
              (.taxiTo target destination via)
              (.taxi { destination := destination, path := path })
              (by simp [resolutionCompatible]))
            { currentPoint := some destination } :=
        ResolvesIndexedStep.taxi
          (GroundMovementScopedAviationWorld.toResolutionWorld world)
          .ground
          index
          target
          start
          destination
          via
          path
          hRoute
      simpa [enumerateFrom] using
        (ResolvesSteps.cons
          (world := GroundMovementScopedAviationWorld.toResolutionWorld world)
          (state := { currentPoint := some start })
          (nextState := { currentPoint := some destination })
          (finalState := finalState)
          (fallbackDomain := .ground)
          (index := index)
          (instruction := .taxiTo target destination via)
          (step := compileResolvedStep
            index
            .ground
            (.taxiTo target destination via)
            (.taxi { destination := destination, path := path })
            (by simp [resolutionCompatible]))
          (tail := enumerateFrom (index + 1) tail)
          (resolvedTail := resolvedTail)
          hStep
          ih)
  | holdShort state index target runway point tail resolvedTail finalState hPoint hTail ih =>
      have hStep :
          ResolvesIndexedStep
            (GroundMovementScopedAviationWorld.toResolutionWorld world)
            state
            .ground
            index
            (.holdShortOf target runway)
            (compileResolvedStep
              index
              .ground
              (.holdShortOf target runway)
              (.holdShort { runway := runway, point := point })
              (by simp [resolutionCompatible]))
            { currentPoint := some point } :=
        ResolvesIndexedStep.holdShort
          (GroundMovementScopedAviationWorld.toResolutionWorld world)
          .ground
          index
          target
          runway
          point
          state
          hPoint
      simpa [enumerateFrom] using
        (ResolvesSteps.cons
          (world := GroundMovementScopedAviationWorld.toResolutionWorld world)
          (state := state)
          (nextState := { currentPoint := some point })
          (finalState := finalState)
          (fallbackDomain := .ground)
          (index := index)
          (instruction := .holdShortOf target runway)
          (step := compileResolvedStep
            index
            .ground
            (.holdShortOf target runway)
            (.holdShort { runway := runway, point := point })
            (by simp [resolutionCompatible]))
          (tail := enumerateFrom (index + 1) tail)
          (resolvedTail := resolvedTail)
          hStep
          ih)
  | crossing state index target runway point tail resolvedTail finalState hPoint hTail ih =>
      have hStep :
          ResolvesIndexedStep
            (GroundMovementScopedAviationWorld.toResolutionWorld world)
            state
            .ground
            index
            (.crossRunway target runway)
            (compileResolvedStep
              index
              .ground
              (.crossRunway target runway)
              (.crossing { runway := runway, crossingPoint := point })
              (by simp [resolutionCompatible]))
            { currentPoint := some point } :=
        ResolvesIndexedStep.crossing
          (GroundMovementScopedAviationWorld.toResolutionWorld world)
          .ground
          index
          target
          runway
          point
          state
          hPoint
      simpa [enumerateFrom] using
        (ResolvesSteps.cons
          (world := GroundMovementScopedAviationWorld.toResolutionWorld world)
          (state := state)
          (nextState := { currentPoint := some point })
          (finalState := finalState)
          (fallbackDomain := .ground)
          (index := index)
          (instruction := .crossRunway target runway)
          (step := compileResolvedStep
            index
            .ground
            (.crossRunway target runway)
            (.crossing { runway := runway, crossingPoint := point })
            (by simp [resolutionCompatible]))
          (tail := enumerateFrom (index + 1) tail)
          (resolvedTail := resolvedTail)
          hStep
          ih)
  | holdPosition state index target tail resolvedTail finalState hTail ih =>
      have hStep :
          ResolvesIndexedStep
            (GroundMovementScopedAviationWorld.toResolutionWorld world)
            state
            .ground
            index
            (.holdPosition target)
            (compiledPlainResolvedStep
              index
              .ground
              (.holdPosition target)
              (by simp [instructionNeedsSpecificResolution]))
            state := by
        simpa [compiledPlainResolvedStep] using
          (ResolvesIndexedStep.plain
            (GroundMovementScopedAviationWorld.toResolutionWorld world)
            .ground
            index
            (.holdPosition target)
            state
            (by simp [instructionNeedsSpecificResolution]))
      simpa [enumerateFrom] using
        (ResolvesSteps.cons
          (world := GroundMovementScopedAviationWorld.toResolutionWorld world)
          (state := state)
          (nextState := state)
          (finalState := finalState)
          (fallbackDomain := .ground)
          (index := index)
          (instruction := .holdPosition target)
          (step := compiledPlainResolvedStep
            index
            .ground
            (.holdPosition target)
            (by simp [instructionNeedsSpecificResolution]))
          (tail := enumerateFrom (index + 1) tail)
          (resolvedTail := resolvedTail)
          hStep
          ih)

theorem normalizeConditionalEnvelope_ok_of_groundSingleReady
    {world : GroundMovementScopedAviationWorld}
    {initialState finalState : ResolutionState}
    {clearance : StructuredClearance}
    {instruction : AtcInstruction}
    {resolved : List ResolvedStep}
    (hContent : clearance.content = .single instruction)
    (hReady :
      GroundMovementTraceReady world initialState 0 [instruction] resolved finalState)
    (hCondition : clearance.condition = none) :
    normalizeConditionalEnvelope clearance = .ok clearance := by
  cases hReady <;>
    simp [normalizeConditionalEnvelope, hContent, hCondition, instructionMayBeConditional]

theorem normalizeConditionalEnvelope_ok_of_groundCompoundReady
    {world : GroundMovementScopedAviationWorld}
    {initialState finalState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {resolved : List ResolvedStep}
    (hContent : clearance.content = .compound content)
    (hReady :
      GroundMovementTraceReady world initialState 0 content.steps resolved finalState)
    (hCondition : clearance.condition = none) :
    normalizeConditionalEnvelope clearance = .ok clearance := by
  have hWrapped : anyWrappedConditionalStep content.steps = false :=
    GroundMovementTraceReady.anyWrappedConditionalStep_false hReady
  simp [normalizeConditionalEnvelope, hContent, hCondition, hWrapped]

inductive GroundMovementSingleCurrentShapeIssuable
    (world : GroundMovementScopedAviationWorld)
    (initialState : ResolutionState) :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {instruction : AtcInstruction}
      {resolved : List ResolvedStep}
      {finalState : ResolutionState}
      (hContent : clearance.content = .single instruction)
      (hReady :
        GroundMovementTraceReady world initialState 0 [instruction] resolved finalState)
      (hDomain : clearance.domain = .ground)
      (hCondition : clearance.condition = none) :
      GroundMovementSingleCurrentShapeIssuable world initialState clearance

inductive GroundMovementCompoundCurrentShapeIssuable
    (world : GroundMovementScopedAviationWorld)
    (initialState : ResolutionState) :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {resolved : List ResolvedStep}
      {finalState : ResolutionState}
      (hContent : clearance.content = .compound content)
      (hReady :
        GroundMovementTraceReady world initialState 0 content.steps resolved finalState)
      (hDomain : clearance.domain = .ground)
      (hCondition : clearance.condition = none) :
      GroundMovementCompoundCurrentShapeIssuable world initialState clearance

theorem GroundMovementSingleCurrentShapeIssuanceTheorem
    {world : GroundMovementScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GroundMovementSingleCurrentShapeIssuable world initialState clearance) :
    ∃ finalState, ∃ resolved,
      ResolvesClearance
        (GroundMovementScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable
  rename_i instruction resolvedSteps finalState hContent hReady hDomain hCondition
  let resolved : ResolvedClearance :=
    { source := clearance
      steps := resolvedSteps }
  have hResolve :
      ResolvesClearance
        (GroundMovementScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState := by
    refine ⟨?_, rfl, ?_⟩
    · exact
        normalizeConditionalEnvelope_ok_of_groundSingleReady
          (world := world)
          (initialState := initialState)
          (finalState := finalState)
          (clearance := clearance)
          (instruction := instruction)
          (resolved := resolvedSteps)
          hContent
          hReady
          hCondition
    · have hSteps :
          ResolvesSteps
            (GroundMovementScopedAviationWorld.toResolutionWorld world)
            initialState
            .ground
            (enumerateFrom 0 [instruction])
            resolvedSteps
            finalState :=
        GroundMovementTraceReady.resolvesSteps hReady
      simpa [resolved, hContent, hDomain, structuredInstructions, contentInstructions,
        indexedSteps, enumerateFrom] using hSteps
  have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
    simpa [resolved] using hFresh
  exact ⟨finalState, resolved, hResolve,
    ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

theorem GroundMovementCompoundCurrentShapeIssuanceTheorem
    {world : GroundMovementScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GroundMovementCompoundCurrentShapeIssuable world initialState clearance) :
    ∃ finalState, ∃ resolved,
      ResolvesClearance
        (GroundMovementScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable
  rename_i content resolvedSteps finalState hContent hReady hDomain hCondition
  let resolved : ResolvedClearance :=
    { source := clearance
      steps := resolvedSteps }
  have hResolve :
      ResolvesClearance
        (GroundMovementScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState := by
    refine ⟨?_, rfl, ?_⟩
    · exact
        normalizeConditionalEnvelope_ok_of_groundCompoundReady
          (world := world)
          (initialState := initialState)
          (finalState := finalState)
          (clearance := clearance)
          (content := content)
          (resolved := resolvedSteps)
          hContent
          hReady
          hCondition
    · have hSteps :
          ResolvesSteps
            (GroundMovementScopedAviationWorld.toResolutionWorld world)
            initialState
            .ground
            (enumerateFrom 0 content.steps)
            resolvedSteps
            finalState :=
        GroundMovementTraceReady.resolvesSteps hReady
      simpa [resolved, hContent, hDomain, structuredInstructions, contentInstructions,
        indexedSteps, enumerateFrom] using hSteps
  have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
    simpa [resolved] using hFresh
  exact ⟨finalState, resolved, hResolve,
    ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

theorem GroundMovementSingleCurrentShapeAuthorizedIssuanceTheorem
    {world : GroundMovementScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : GroundMovementExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GroundMovementSingleCurrentShapeIssuable world initialState clearance)
    (hAuthority :
      GroundMovementWorldAuthorized world clearance.issuedBy (structuredInstructions clearance)) :
    ∃ finalState, ∃ resolved,
      groundMovementInstructionsIssuerAuthorized
        (extractCompileView world.toScopedAviationWorld)
        clearance.issuedBy
        (structuredInstructions clearance) = true ∧
      ResolvesClearance
        (GroundMovementScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      groundMovementInstructionsIssuerAuthorized
        (extractCompileView world.toScopedAviationWorld)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    groundMovementInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GroundMovementSingleCurrentShapeIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨finalState, resolved, hResolve, hReachable⟩
  exact ⟨finalState, resolved, hAuthorized, hResolve, hReachable⟩

theorem GroundMovementCompoundCurrentShapeAuthorizedIssuanceTheorem
    {world : GroundMovementScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : GroundMovementExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GroundMovementCompoundCurrentShapeIssuable world initialState clearance)
    (hAuthority :
      GroundMovementWorldAuthorized world clearance.issuedBy (structuredInstructions clearance)) :
    ∃ finalState, ∃ resolved,
      groundMovementInstructionsIssuerAuthorized
        (extractCompileView world.toScopedAviationWorld)
        clearance.issuedBy
        (structuredInstructions clearance) = true ∧
      ResolvesClearance
        (GroundMovementScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      groundMovementInstructionsIssuerAuthorized
        (extractCompileView world.toScopedAviationWorld)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    groundMovementInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GroundMovementCompoundCurrentShapeIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨finalState, resolved, hResolve, hReachable⟩
  exact ⟨finalState, resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleTaxi : ResolvedClearance :=
  { source :=
      { id := "CLR-TAXI-CS"
        aircraft := "TEST123"
        content := .single (.taxiTo "TEST123" "HP-27" ["APRON-JCT"])
        domain := .ground
        issuedBy := "CTRL-1"
        issuedAt := 100
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .ground
          (.taxiTo "TEST123" "HP-27" ["APRON-JCT"])
          (.taxi { destination := "HP-27", path := ["STAND", "APRON-JCT", "HP-09", "RWY-X", "HP-27"] })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedSingleTaxi : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleTaxi }

def sampleResolvedSingleHoldShort : ResolvedClearance :=
  { source :=
      { id := "CLR-HOLD-SHORT-CS"
        aircraft := "TEST123"
        content := .single (.holdShortOf "TEST123" "RWY-27")
        domain := .ground
        issuedBy := "CTRL-1"
        issuedAt := 101
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .ground
          (.holdShortOf "TEST123" "RWY-27")
          (.holdShort { runway := "RWY-27", point := "HP-27" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedSingleHoldShort : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleHoldShort }

def sampleResolvedSingleCrossRunway : ResolvedClearance :=
  { source :=
      { id := "CLR-CROSS-CS"
        aircraft := "TEST123"
        content := .single (.crossRunway "TEST123" "RWY-09")
        domain := .ground
        issuedBy := "CTRL-1"
        issuedAt := 102
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .ground
          (.crossRunway "TEST123" "RWY-09")
          (.crossing { runway := "RWY-09", crossingPoint := "RWY-X" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedSingleCrossRunway : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleCrossRunway }

def sampleResolvedSingleHoldPosition : ResolvedClearance :=
  { source :=
      { id := "CLR-HOLD-POS-CS"
        aircraft := "TEST123"
        content := .single (.holdPosition "TEST123")
        domain := .ground
        issuedBy := "CTRL-1"
        issuedAt := 103
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .ground
          (.holdPosition "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleHoldPosition : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleHoldPosition }

def sampleResolvedIncomingTowerContactForGround : ResolvedClearance :=
  { source :=
      { id := "CLR-GROUND-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 104
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleResolvedTaxiCrossHoldShortCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-TAXI-CROSS-HOLD"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .taxiTo "TEST123" "HP-27" ["APRON-JCT"]
              , .crossRunway "TEST123" "RWY-09"
              , .holdShortOf "TEST123" "RWY-27" ] }
        domain := .ground
        issuedBy := "CTRL-1"
        issuedAt := 105
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .ground
          (.taxiTo "TEST123" "HP-27" ["APRON-JCT"])
          (.taxi { destination := "HP-27", path := ["STAND", "APRON-JCT", "HP-09", "RWY-X", "HP-27"] })
          (by simp [resolutionCompatible])
      , compileResolvedStep
          1
          .ground
          (.crossRunway "TEST123" "RWY-09")
          (.crossing { runway := "RWY-09", crossingPoint := "RWY-X" })
          (by simp [resolutionCompatible])
      , compileResolvedStep
          2
          .ground
          (.holdShortOf "TEST123" "RWY-27")
          (.holdShort { runway := "RWY-27", point := "HP-27" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedTaxiCrossHoldShortCompound : ManagedResolvedClearance :=
  { resolved := sampleResolvedTaxiCrossHoldShortCompound }

theorem singleTaxi_completes_at_destination :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleTaxi
        { position := some "HP-27" }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleTaxi_reconcile_transitions_to_terminal :
    let completed :=
      (evaluateResolvedCompletion
        sampleManagedResolvedSingleTaxi
        { position := some "HP-27" }).updated
    let reconciliation :=
      reconcileResolvedClearances
        [completed]
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-TAXI-CS"] := by
  native_decide

theorem singleHoldShort_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleHoldShort
        { position := some "HP-27" }
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleHoldShort :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleHoldShort]
        sampleResolvedIncomingTowerContactForGround
    resolvedClearanceIds admitted.clearances = ["CLR-HOLD-SHORT-CS", "CLR-GROUND-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem singleCrossRunway_completes_on_runway_transition :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleCrossRunway
        { runwayTransitions := UniqueSet.singleton "RWY-09" }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleCrossRunway_reconcile_transitions_to_terminal :
    let completed :=
      (evaluateResolvedCompletion
        sampleManagedResolvedSingleCrossRunway
        { runwayTransitions := UniqueSet.singleton "RWY-09" }).updated
    let reconciliation :=
      reconcileResolvedClearances
        [completed]
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-CROSS-CS"] := by
  native_decide

theorem singleHoldPosition_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleHoldPosition
        { position := some "APRON-JCT" }
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleHoldPosition :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleHoldPosition]
        sampleResolvedIncomingTowerContactForGround
    resolvedClearanceIds admitted.clearances = ["CLR-HOLD-POS-CS", "CLR-GROUND-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem taxiCrossHoldShort_compound_completes_on_destination_and_transition :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedTaxiCrossHoldShortCompound
        { position := some "HP-27"
          runwayTransitions := UniqueSet.singleton "RWY-09" }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.ofList [0, 1] := by
  native_decide

end Greenfield
end CertifiedAtc
