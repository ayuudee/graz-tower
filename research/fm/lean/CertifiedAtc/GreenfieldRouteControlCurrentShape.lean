import CertifiedAtc.RouteBearingResolutionBridge
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape closure for the remaining stable route/vector control family.

This closes the instructions whose current Kotlin semantics are already
explicit and do not require broader route-bearing research:

- direct-fix instructions with explicit point completion
- `JoinAirway` with explicit join-fix-on-airway resolution
- plain route-control instructions whose current behavior is on-activation,
  persistent, or localiser-capture based
-/

def RouteControlCurrentShapeInstruction : AtcInstruction → Prop
  | .proceedDirect _ _ => True
  | .leaveHoldProceedDirect _ _ => True
  | .whenAbleProceedDirect _ _ => True
  | .rejoinSidAt _ _ => True
  | .joinAirway _ _ _ => True
  | .resumeOwnNavigation _ => True
  | .routeAsFiled _ => True
  | .flyHeading _ _ => True
  | .turnHeading _ _ => True
  | .continuePresentHeading _ => True
  | .stopTurn _ => True
  | .interceptLocaliser _ => True
  | _ => False

def RouteControlCurrentShapeInstructionReady
    (world : RouteBearingScopedAviationWorld) :
    AtcInstruction → Prop
  | .proceedDirect _ fix =>
      ∃ point, (RouteBearingScopedAviationWorld.toResolutionWorld world).fixPoint fix point
  | .leaveHoldProceedDirect _ fix =>
      ∃ point, (RouteBearingScopedAviationWorld.toResolutionWorld world).fixPoint fix point
  | .whenAbleProceedDirect _ fix =>
      ∃ point, (RouteBearingScopedAviationWorld.toResolutionWorld world).fixPoint fix point
  | .rejoinSidAt _ fix =>
      ∃ point, (RouteBearingScopedAviationWorld.toResolutionWorld world).fixPoint fix point
  | .joinAirway _ airway joinFix =>
      ∃ point,
        (RouteBearingScopedAviationWorld.toResolutionWorld world).fixPoint joinFix point ∧
          (RouteBearingScopedAviationWorld.toResolutionWorld world).airwayPoint airway point
  | .resumeOwnNavigation _ => True
  | .routeAsFiled _ => True
  | .flyHeading _ _ => True
  | .turnHeading _ _ => True
  | .continuePresentHeading _ => True
  | .stopTurn _ => True
  | .interceptLocaliser _ => True
  | _ => False

def routeControlCurrentShapeInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | .proceedDirect _ _ =>
      some { entityType := .fix, operation := .routeClearance }
  | .leaveHoldProceedDirect _ _ =>
      some { entityType := .fix, operation := .routeClearance }
  | .whenAbleProceedDirect _ _ =>
      some { entityType := .fix, operation := .routeClearance }
  | .rejoinSidAt _ _ =>
      some { entityType := .fix, operation := .routeClearance }
  | .joinAirway _ _ _ =>
      some { entityType := .airway, operation := .routeClearance }
  | .resumeOwnNavigation _ =>
      some { entityType := .airspaceVolume, operation := .routeClearance }
  | .routeAsFiled _ =>
      some { entityType := .airspaceVolume, operation := .routeClearance }
  | .flyHeading _ _ =>
      some { entityType := .airspaceVolume, operation := .routeClearance }
  | .turnHeading _ _ =>
      some { entityType := .airspaceVolume, operation := .routeClearance }
  | .continuePresentHeading _ =>
      some { entityType := .airspaceVolume, operation := .routeClearance }
  | .stopTurn _ =>
      some { entityType := .airspaceVolume, operation := .routeClearance }
  | .interceptLocaliser _ =>
      some { entityType := .airspaceVolume, operation := .routeClearance }
  | _ => none

def routeControlCurrentShapeInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match routeControlCurrentShapeInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def routeControlCurrentShapeInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      routeControlCurrentShapeInstructionIssuerAuthorized view controller instruction &&
        routeControlCurrentShapeInstructionsIssuerAuthorized view controller tail

def GreenfieldRouteControlCurrentShapeWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps,
    match routeControlCurrentShapeInstructionRequiredAuthorityGrant? instruction with
    | none => True
    | some grant =>
        WorldControllerHasGrant world.toScopedAviationWorld controller grant

def GreenfieldRouteControlCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld)
    (clearance : StructuredClearance) : Prop :=
  match clearance.content with
  | .single instruction =>
      RouteControlCurrentShapeInstruction instruction ∧
        RouteControlCurrentShapeInstructionReady world instruction ∧
        clearance.condition = none ∧
        clearance.domain = .route
  | .compound _ => False

def singletonResolvedDirectFixClearance
    (clearance : StructuredClearance)
    (instruction : AtcInstruction)
    (fix : FixId)
    (point : PointId)
    (hCompatible :
      resolutionCompatible
        (.directFix { fix := fix, point := point })
        instruction = true) :
    ResolvedClearance :=
  { source := clearance
    steps :=
      [ compileResolvedStep
          0
          .route
          instruction
          (.directFix { fix := fix, point := point })
          hCompatible ] }

def singletonResolvedAirwayJoinClearance
    (clearance : StructuredClearance)
    (target : AircraftId)
    (airway : AirwayId)
    (joinFix : FixId)
    (joinPoint : PointId) :
    ResolvedClearance :=
  { source := clearance
    steps :=
      [ compileResolvedStep
          0
          .route
          (.joinAirway target airway joinFix)
          (.airwayJoin { airway := airway, joinFix := joinFix, joinPoint := joinPoint })
          (by simp [resolutionCompatible]) ] }

theorem routeControlCurrentShapeInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : RouteBearingScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMapped : routeControlCurrentShapeInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world.toScopedAviationWorld controller grant) :
    routeControlCurrentShapeInstructionIssuerAuthorized
      (extractRouteBearingCompileView world)
      controller
      instruction = true := by
  simp [routeControlCurrentShapeInstructionIssuerAuthorized, hMapped]
  change
    controllerHasAuthorityGrant
      (extractCompileView world.toScopedAviationWorld)
      controller
      grant = true
  exact
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf.baseWellFormed
      hGrant

theorem routeControlCurrentShapeInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : RouteBearingScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      RouteBearingExtractionWellFormed world →
      GreenfieldRouteControlCurrentShapeWorldAuthorized world controller steps →
        routeControlCurrentShapeInstructionsIssuerAuthorized
          (extractRouteBearingCompileView world)
          controller
          steps = true := by
  intro world controller steps hWf hAuth
  induction steps with
  | nil =>
      simp [routeControlCurrentShapeInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHeadAuth := hAuth head (by simp)
      have hTailAuth : GreenfieldRouteControlCurrentShapeWorldAuthorized world controller tail := by
        intro instruction hMem
        exact hAuth instruction (by simp [hMem])
      cases hGrant : routeControlCurrentShapeInstructionRequiredAuthorityGrant? head with
      | none =>
          simp [routeControlCurrentShapeInstructionsIssuerAuthorized,
            routeControlCurrentShapeInstructionIssuerAuthorized, hGrant, ih hTailAuth]
      | some grant =>
          have hHeadGrant :
              WorldControllerHasGrant world.toScopedAviationWorld controller grant := by
            simpa [hGrant] using hHeadAuth
          have hHeadOk :
              routeControlCurrentShapeInstructionIssuerAuthorized
                (extractRouteBearingCompileView world)
                controller
                head = true :=
            routeControlCurrentShapeInstructionIssuerAuthorized_eq_true_of_worldAuthorized
              (world := world)
              (controller := controller)
              (instruction := head)
              (grant := grant)
              hWf
              hGrant
              hHeadGrant
          simp [routeControlCurrentShapeInstructionsIssuerAuthorized, hHeadOk, ih hTailAuth]

theorem GreenfieldRouteControlCurrentShapeReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRouteControlCurrentShapeIssuable world clearance) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hContent : clearance.content with
  | single instruction =>
      simp [GreenfieldRouteControlCurrentShapeIssuable, hContent] at hIssuable
      rcases hIssuable with ⟨hInstruction, hReady, hCondition, hDomain⟩
      cases instruction with
      | proceedDirect target fix =>
          rcases hReady with ⟨point, hPoint⟩
          let step :=
            compileResolvedStep
              0
              .route
              (.proceedDirect target fix)
              (.directFix { fix := fix, point := point })
              (by simp [resolutionCompatible])
          have hStep :
              ResolvesIndexedStep
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                .route
                0
                (.proceedDirect target fix)
                step
                initialState := by
            simpa [step] using
              (ResolvesIndexedStep.proceedDirect
                (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
                (fallbackDomain := .route)
                (index := 0)
                (target := target)
                (fix := fix)
                (point := point)
                (state := initialState)
                hPoint)
          have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
            simp [normalizeConditionalEnvelope, hContent, hCondition]
          let resolved := singletonResolvedClearance clearance step
          have hStepDomain : step.domain = .route := by
            simp [step, compileResolvedStep, instructionDomain?]
          have hResolve :
              ResolvesClearance
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                clearance
                resolved
                initialState := by
            exact
              resolvesSingleInstructionClearance
                hNormalized
                hContent
                (by simpa [hStepDomain] using hDomain)
                (by simpa [hDomain] using hStep)
          have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
            simpa [resolved] using hFresh
          exact ⟨resolved, hResolve,
            ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
      | leaveHoldProceedDirect target fix =>
          rcases hReady with ⟨point, hPoint⟩
          let step :=
            compileResolvedStep
              0
              .route
              (.leaveHoldProceedDirect target fix)
              (.directFix { fix := fix, point := point })
              (by simp [resolutionCompatible])
          have hStep :
              ResolvesIndexedStep
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                .route
                0
                (.leaveHoldProceedDirect target fix)
                step
                initialState := by
            simpa [step] using
              (ResolvesIndexedStep.leaveHoldProceedDirect
                (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
                (fallbackDomain := .route)
                (index := 0)
                (target := target)
                (fix := fix)
                (point := point)
                (state := initialState)
                hPoint)
          have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
            simp [normalizeConditionalEnvelope, hContent, hCondition]
          let resolved := singletonResolvedClearance clearance step
          have hStepDomain : step.domain = .route := by
            simp [step, compileResolvedStep, instructionDomain?]
          have hResolve :
              ResolvesClearance
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                clearance
                resolved
                initialState := by
            exact
              resolvesSingleInstructionClearance
                hNormalized
                hContent
                (by simpa [hStepDomain] using hDomain)
                (by simpa [hDomain] using hStep)
          have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
            simpa [resolved] using hFresh
          exact ⟨resolved, hResolve,
            ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
      | whenAbleProceedDirect target fix =>
          rcases hReady with ⟨point, hPoint⟩
          let step :=
            compileResolvedStep
              0
              .route
              (.whenAbleProceedDirect target fix)
              (.directFix { fix := fix, point := point })
              (by simp [resolutionCompatible])
          have hStep :
              ResolvesIndexedStep
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                .route
                0
                (.whenAbleProceedDirect target fix)
                step
                initialState := by
            simpa [step] using
              (ResolvesIndexedStep.whenAbleProceedDirect
                (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
                (fallbackDomain := .route)
                (index := 0)
                (target := target)
                (fix := fix)
                (point := point)
                (state := initialState)
                hPoint)
          have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
            simp [normalizeConditionalEnvelope, hContent, hCondition]
          let resolved := singletonResolvedClearance clearance step
          have hStepDomain : step.domain = .route := by
            simp [step, compileResolvedStep, instructionDomain?]
          have hResolve :
              ResolvesClearance
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                clearance
                resolved
                initialState := by
            exact
              resolvesSingleInstructionClearance
                hNormalized
                hContent
                (by simpa [hStepDomain] using hDomain)
                (by simpa [hDomain] using hStep)
          have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
            simpa [resolved] using hFresh
          exact ⟨resolved, hResolve,
            ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
      | rejoinSidAt target fix =>
          rcases hReady with ⟨point, hPoint⟩
          let step :=
            compileResolvedStep
              0
              .route
              (.rejoinSidAt target fix)
              (.directFix { fix := fix, point := point })
              (by simp [resolutionCompatible])
          have hStep :
              ResolvesIndexedStep
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                .route
                0
                (.rejoinSidAt target fix)
                step
                initialState := by
            simpa [step] using
              (ResolvesIndexedStep.rejoinSidAt
                (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
                (fallbackDomain := .route)
                (index := 0)
                (target := target)
                (fix := fix)
                (point := point)
                (state := initialState)
                hPoint)
          have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
            simp [normalizeConditionalEnvelope, hContent, hCondition]
          let resolved := singletonResolvedClearance clearance step
          have hStepDomain : step.domain = .route := by
            simp [step, compileResolvedStep, instructionDomain?]
          have hResolve :
              ResolvesClearance
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                clearance
                resolved
                initialState := by
            exact
              resolvesSingleInstructionClearance
                hNormalized
                hContent
                (by simpa [hStepDomain] using hDomain)
                (by simpa [hDomain] using hStep)
          have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
            simpa [resolved] using hFresh
          exact ⟨resolved, hResolve,
            ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
      | joinAirway target airway joinFix =>
          rcases hReady with ⟨joinPoint, hPoint, hAirway⟩
          let step :=
            compileResolvedStep
              0
              .route
              (.joinAirway target airway joinFix)
              (.airwayJoin { airway := airway, joinFix := joinFix, joinPoint := joinPoint })
              (by simp [resolutionCompatible])
          have hStep :
              ResolvesIndexedStep
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                .route
                0
                (.joinAirway target airway joinFix)
                step
                initialState := by
            simpa [step] using
              (ResolvesIndexedStep.joinAirway
                (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
                (fallbackDomain := .route)
                (index := 0)
                (target := target)
                (airway := airway)
                (joinFix := joinFix)
                (joinPoint := joinPoint)
                (state := initialState)
                hPoint
                hAirway)
          have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
            simp [normalizeConditionalEnvelope, hContent, hCondition]
          let resolved := singletonResolvedClearance clearance step
          have hStepDomain : step.domain = .route := by
            simp [step, compileResolvedStep, instructionDomain?]
          have hResolve :
              ResolvesClearance
                (RouteBearingScopedAviationWorld.toResolutionWorld world)
                initialState
                clearance
                resolved
                initialState := by
            exact
              resolvesSingleInstructionClearance
                hNormalized
                hContent
                (by simpa [hStepDomain] using hDomain)
                (by simpa [hDomain] using hStep)
          have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
            simpa [resolved] using hFresh
          exact ⟨resolved, hResolve,
            ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
      | resumeOwnNavigation target =>
          exact
            plainCurrentShapeAdmissionSoundnessTheorem_autoDomain
              (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
              (existing := existing)
              (initialState := initialState)
              (clearance := clearance)
              (instruction := .resumeOwnNavigation target)
              hReach
              hFresh
              (by simp [instructionNeedsSpecificResolution])
              (by simp [normalizeConditionalEnvelope, hContent, hCondition])
              hContent
              (by simpa using hDomain)
      | routeAsFiled target =>
          exact
            plainCurrentShapeAdmissionSoundnessTheorem_autoDomain
              (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
              (existing := existing)
              (initialState := initialState)
              (clearance := clearance)
              (instruction := .routeAsFiled target)
              hReach
              hFresh
              (by simp [instructionNeedsSpecificResolution])
              (by simp [normalizeConditionalEnvelope, hContent, hCondition])
              hContent
              (by simpa using hDomain)
      | flyHeading target heading =>
          exact
            plainCurrentShapeAdmissionSoundnessTheorem_autoDomain
              (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
              (existing := existing)
              (initialState := initialState)
              (clearance := clearance)
              (instruction := .flyHeading target heading)
              hReach
              hFresh
              (by simp [instructionNeedsSpecificResolution])
              (by simp [normalizeConditionalEnvelope, hContent, hCondition])
              hContent
              (by simpa using hDomain)
      | turnHeading target heading =>
          exact
            plainCurrentShapeAdmissionSoundnessTheorem_autoDomain
              (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
              (existing := existing)
              (initialState := initialState)
              (clearance := clearance)
              (instruction := .turnHeading target heading)
              hReach
              hFresh
              (by simp [instructionNeedsSpecificResolution])
              (by simp [normalizeConditionalEnvelope, hContent, hCondition])
              hContent
              (by simpa using hDomain)
      | continuePresentHeading target =>
          exact
            plainCurrentShapeAdmissionSoundnessTheorem_autoDomain
              (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
              (existing := existing)
              (initialState := initialState)
              (clearance := clearance)
              (instruction := .continuePresentHeading target)
              hReach
              hFresh
              (by simp [instructionNeedsSpecificResolution])
              (by simp [normalizeConditionalEnvelope, hContent, hCondition])
              hContent
              (by simpa using hDomain)
      | stopTurn target =>
          exact
            plainCurrentShapeAdmissionSoundnessTheorem_autoDomain
              (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
              (existing := existing)
              (initialState := initialState)
              (clearance := clearance)
              (instruction := .stopTurn target)
              hReach
              hFresh
              (by simp [instructionNeedsSpecificResolution])
              (by simp [normalizeConditionalEnvelope, hContent, hCondition])
              hContent
              (by simpa using hDomain)
      | interceptLocaliser target =>
          exact
            plainCurrentShapeAdmissionSoundnessTheorem_autoDomain
              (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
              (existing := existing)
              (initialState := initialState)
              (clearance := clearance)
              (instruction := .interceptLocaliser target)
              hReach
              hFresh
              (by simp [instructionNeedsSpecificResolution])
              (by simp [normalizeConditionalEnvelope, hContent, hCondition])
              hContent
              (by simpa using hDomain)
      | _ =>
          cases hInstruction
  | compound content =>
      simp [GreenfieldRouteControlCurrentShapeIssuable, hContent] at hIssuable

theorem GreenfieldRouteControlCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRouteControlCurrentShapeIssuable world clearance)
    (hAuthority :
      GreenfieldRouteControlCurrentShapeWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ resolved,
      routeControlCurrentShapeInstructionsIssuerAuthorized
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
      routeControlCurrentShapeInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    routeControlCurrentShapeInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      hWf
      hAuthority
  rcases GreenfieldRouteControlCurrentShapeReachableIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleProceedDirect : ResolvedClearance :=
  { source :=
      { id := "CLR-PROC-DCT"
        aircraft := "TEST123"
        content := .single (.proceedDirect "TEST123" "HOLD")
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 110
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .route
          (.proceedDirect "TEST123" "HOLD")
          (.directFix { fix := "HOLD", point := "P-HOLD" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedSingleProceedDirect : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleProceedDirect }

def sampleResolvedSingleResumeOwnNavigation : ResolvedClearance :=
  { source :=
      { id := "CLR-RON"
        aircraft := "TEST123"
        content := .single (.resumeOwnNavigation "TEST123")
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 111
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.resumeOwnNavigation "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleResumeOwnNavigation : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleResumeOwnNavigation }

def sampleResolvedSingleFlyHeading : ResolvedClearance :=
  { source :=
      { id := "CLR-HDG"
        aircraft := "TEST123"
        content := .single (.flyHeading "TEST123" 270)
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 112
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.flyHeading "TEST123" 270)
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleFlyHeading : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleFlyHeading }

def sampleResolvedSingleInterceptLocaliser : ResolvedClearance :=
  { source :=
      { id := "CLR-LOC"
        aircraft := "TEST123"
        content := .single (.interceptLocaliser "TEST123")
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 113
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.interceptLocaliser "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleInterceptLocaliser : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleInterceptLocaliser }

def sampleResolvedIncomingTowerContactForRouteControl : ResolvedClearance :=
  { source :=
      { id := "CLR-ROUTE-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 114
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleResolvedIncomingGoAroundForRouteControl : ResolvedClearance :=
  { source :=
      { id := "CLR-ROUTE-GA"
        aircraft := "TEST123"
        content := .single (.goAround "TEST123")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 115
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.goAround "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

theorem singleProceedDirect_completes_at_resolved_fix :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleProceedDirect
        { position := some "P-HOLD" }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleResumeOwnNavigation_completes_on_activation :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleResumeOwnNavigation
        {}
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleFlyHeading_remains_active_under_current_engine :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleFlyHeading
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = {} ∧
      evaluation.stepResults.length = 1 := by
  native_decide

theorem singleInterceptLocaliser_completes_on_capture :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleInterceptLocaliser
        { establishedApproachComponents := UniqueSet.singleton .localiser }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleFlyHeading :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleFlyHeading]
        sampleResolvedIncomingTowerContactForRouteControl
    resolvedClearanceIds admitted.clearances = ["CLR-HDG", "CLR-ROUTE-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem incomingGoAround_fully_supersedes_singleProceedDirect :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleProceedDirect]
        sampleResolvedIncomingGoAroundForRouteControl
    resolvedClearanceIds admitted.clearances = ["CLR-ROUTE-GA"] ∧
      resolvedClearanceIds admitted.fullySuperseded = ["CLR-PROC-DCT"] := by
  native_decide

end Greenfield
end CertifiedAtc
