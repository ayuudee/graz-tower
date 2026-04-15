import CertifiedAtc.GreenfieldRouteControlCurrentShape
import CertifiedAtc.GreenfieldRouteBearingCompound

namespace CertifiedAtc
namespace Greenfield

/--
First narrow current-shape compound widening for the delivered route/vector
control surface.

This keeps the same conservative rule used elsewhere in the greenfield
widening programme:

- one leading route/vector-control instruction from the delivered family
- zero or more immediate adjunct tails already understood by the current
  engine

It does not add new world theory. It just closes the first compound seam on
top of the already-delivered single-step route/vector boundary.
-/

def RouteControlCompoundTailReady
    (world : RouteBearingScopedAviationWorld)
    (tail : List AtcInstruction) : Prop :=
  ∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction

theorem anyWrappedConditionalStep_false_of_routeControlCompoundTailReady
    {world : RouteBearingScopedAviationWorld}
    {tail : List AtcInstruction}
    (hReady : RouteControlCompoundTailReady world tail) :
    anyWrappedConditionalStep tail = false :=
  anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts hReady

def routeControlCompoundInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | instruction =>
      match routeControlCurrentShapeInstructionRequiredAuthorityGrant? instruction with
      | some grant => some grant
      | none => routeBearingCompoundInstructionRequiredAuthorityGrant? instruction

def routeControlCompoundInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match routeControlCompoundInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def routeControlCompoundInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      routeControlCompoundInstructionIssuerAuthorized view controller instruction &&
        routeControlCompoundInstructionsIssuerAuthorized view controller tail

def RouteControlCompoundWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps,
    match routeControlCompoundInstructionRequiredAuthorityGrant? instruction with
    | none => True
    | some grant =>
        WorldControllerHasGrant world.toScopedAviationWorld controller grant

theorem routeControlCompoundInstructionIssuerAuthorized_eq_true_of_unmapped
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instruction : AtcInstruction}
    (hUnmapped : routeControlCompoundInstructionRequiredAuthorityGrant? instruction = none) :
    routeControlCompoundInstructionIssuerAuthorized view controller instruction = true := by
  simp [routeControlCompoundInstructionIssuerAuthorized, hUnmapped]

theorem routeControlCompoundInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : RouteBearingScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMapped : routeControlCompoundInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world.toScopedAviationWorld controller grant) :
    routeControlCompoundInstructionIssuerAuthorized
      (extractRouteBearingCompileView world)
      controller
      instruction = true := by
  simp [routeControlCompoundInstructionIssuerAuthorized, hMapped]
  change
    controllerHasAuthorityGrant
      (extractCompileView world.toScopedAviationWorld)
      controller
      grant = true
  exact
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf.baseWellFormed
      hGrant

theorem routeControlCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : RouteBearingScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      RouteBearingExtractionWellFormed world →
      RouteControlCompoundWorldAuthorized world controller steps →
        routeControlCompoundInstructionsIssuerAuthorized
          (extractRouteBearingCompileView world)
          controller
          steps = true := by
  intro world controller steps hWf hAuth
  induction steps with
  | nil =>
      simp [routeControlCompoundInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHeadAuth := hAuth head (by simp)
      have hTailAuth : RouteControlCompoundWorldAuthorized world controller tail := by
        intro instruction hMem
        exact hAuth instruction (by simp [hMem])
      cases hGrant : routeControlCompoundInstructionRequiredAuthorityGrant? head with
      | none =>
          simp [routeControlCompoundInstructionsIssuerAuthorized,
            routeControlCompoundInstructionIssuerAuthorized_eq_true_of_unmapped hGrant,
            ih hTailAuth]
      | some grant =>
          have hHeadGrant :
              WorldControllerHasGrant
                world.toScopedAviationWorld
                controller
                grant := by
            simpa [hGrant] using hHeadAuth
          have hHeadOk :
              routeControlCompoundInstructionIssuerAuthorized
                (extractRouteBearingCompileView world)
                controller
                head = true :=
            routeControlCompoundInstructionIssuerAuthorized_eq_true_of_worldAuthorized
              (world := world)
              (controller := controller)
              (instruction := head)
              (grant := grant)
              hWf
              hGrant
              hHeadGrant
          simp [routeControlCompoundInstructionsIssuerAuthorized, hHeadOk, ih hTailAuth]

inductive GreenfieldRouteControlCompoundCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld)
    (initialState : ResolutionState) :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {primary : AtcInstruction}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps : content.steps = primary :: tail)
      (hPrimary : RouteControlCurrentShapeInstruction primary)
      (hPrimaryReady : RouteControlCurrentShapeInstructionReady world primary)
      (hStateReady : RouteControlCurrentShapeStateReady initialState primary)
      (hTail : RouteControlCompoundTailReady world tail)
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      GreenfieldRouteControlCompoundCurrentShapeIssuable world initialState clearance

theorem resolvesRouteControlPrimary_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {primary : AtcInstruction}
    (hPrimary : RouteControlCurrentShapeInstruction primary)
    (hPrimaryReady : RouteControlCurrentShapeInstructionReady world primary)
    (hStateReady : RouteControlCurrentShapeStateReady initialState primary) :
    ∃ finalState primaryStep,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        .route
        0
        primary
        primaryStep
        finalState := by
  cases primary with
  | proceedDirect target fix =>
      rcases hPrimaryReady with ⟨point, hPoint⟩
      let step :=
        compileResolvedStep
          0
          .route
          (.proceedDirect target fix)
          (.directFix { fix := fix, point := point })
          (by simp [resolutionCompatible])
      refine ⟨initialState, step, ?_⟩
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
  | leaveHoldProceedDirect target fix =>
      rcases hPrimaryReady with ⟨point, hPoint⟩
      let step :=
        compileResolvedStep
          0
          .route
          (.leaveHoldProceedDirect target fix)
          (.directFix { fix := fix, point := point })
          (by simp [resolutionCompatible])
      refine ⟨initialState, step, ?_⟩
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
  | whenAbleProceedDirect target fix =>
      rcases hPrimaryReady with ⟨point, hPoint⟩
      let step :=
        compileResolvedStep
          0
          .route
          (.whenAbleProceedDirect target fix)
          (.directFix { fix := fix, point := point })
          (by simp [resolutionCompatible])
      refine ⟨initialState, step, ?_⟩
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
  | rejoinSidAt target fix =>
      rcases hPrimaryReady with ⟨point, hPoint⟩
      let step :=
        compileResolvedStep
          0
          .route
          (.rejoinSidAt target fix)
          (.directFix { fix := fix, point := point })
          (by simp [resolutionCompatible])
      refine ⟨initialState, step, ?_⟩
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
  | joinAirway target airway joinFix =>
      rcases hPrimaryReady with ⟨joinPoint, hPoint, hAirway⟩
      let step :=
        compileResolvedStep
          0
          .route
          (.joinAirway target airway joinFix)
          (.airwayJoin { airway := airway, joinFix := joinFix, joinPoint := joinPoint })
          (by simp [resolutionCompatible])
      refine ⟨initialState, step, ?_⟩
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
  | resumeOwnNavigation target =>
      rcases resolvesIndexedPlainInstruction
          (world := world)
          (state := initialState)
          (fallbackDomain := .route)
          (index := 0)
          (instruction := .resumeOwnNavigation target)
          (by simp [instructionNeedsSpecificResolution]) with
          ⟨step, hStep⟩
      exact ⟨initialState, step, by simpa using hStep⟩
  | routeAsFiled target =>
      rcases resolvesIndexedPlainInstruction
          (world := world)
          (state := initialState)
          (fallbackDomain := .route)
          (index := 0)
          (instruction := .routeAsFiled target)
          (by simp [instructionNeedsSpecificResolution]) with
          ⟨step, hStep⟩
      exact ⟨initialState, step, by simpa using hStep⟩
  | flyHeading target heading =>
      let vector : ResolvedVectorInstruction :=
        { kind := .flyHeading
          targetHeadingDegreesMagnetic := some heading }
      let step :=
        compileResolvedStep
          0
          .route
          (.flyHeading target heading)
          (.vector vector)
          (by simp [resolutionCompatible])
      refine ⟨{ initialState with currentHeadingDegreesMagnetic := some heading }, step, ?_⟩
      simpa [step, vector] using
        (ResolvesIndexedStep.flyHeading
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .route)
          (index := 0)
          (target := target)
          (headingDegreesMagnetic := heading)
          (state := initialState))
  | turnHeading target direction heading =>
      let vector : ResolvedVectorInstruction :=
        { kind := .turnHeading
          targetHeadingDegreesMagnetic := some heading
          turnDirection := some direction }
      let step :=
        compileResolvedStep
          0
          .route
          (.turnHeading target direction heading)
          (.vector vector)
          (by simp [resolutionCompatible])
      refine ⟨{ initialState with currentHeadingDegreesMagnetic := some heading }, step, ?_⟩
      simpa [step, vector] using
        (ResolvesIndexedStep.turnHeading
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .route)
          (index := 0)
          (target := target)
          (turnDirection := direction)
          (headingDegreesMagnetic := heading)
          (state := initialState))
  | turnByDegrees target direction degrees =>
      rcases hStateReady with ⟨currentHeading, hCurrentHeading⟩
      have hInitialHeadingState :
          { initialState with currentHeadingDegreesMagnetic := some currentHeading } = initialState := by
        cases initialState
        cases hCurrentHeading
        rfl
      let vector : ResolvedVectorInstruction :=
        { kind := .turnByDegrees
          targetHeadingDegreesMagnetic := some (turnedHeadingDegrees currentHeading direction degrees)
          turnDirection := some direction
          turnDegrees := some degrees
          capturedHeadingDegreesMagnetic := some currentHeading }
      let step :=
        compileResolvedStep
          0
          .route
          (.turnByDegrees target direction degrees)
          (.vector vector)
          (by simp [resolutionCompatible])
      refine ⟨
        { initialState with
            currentHeadingDegreesMagnetic := some (turnedHeadingDegrees currentHeading direction degrees) },
        step,
        ?_⟩
      simpa [step, vector, hCurrentHeading, hInitialHeadingState] using
        (ResolvesIndexedStep.turnByDegrees
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .route)
          (index := 0)
          (target := target)
          (turnDirection := direction)
          (degrees := degrees)
          (headingDegreesMagnetic := currentHeading)
          (currentPoint := initialState.currentPoint))
  | continuePresentHeading target =>
      rcases hStateReady with ⟨currentHeading, hCurrentHeading⟩
      have hInitialHeadingState :
          { initialState with currentHeadingDegreesMagnetic := some currentHeading } = initialState := by
        cases initialState
        cases hCurrentHeading
        rfl
      let vector : ResolvedVectorInstruction :=
        { kind := .continuePresentHeading
          targetHeadingDegreesMagnetic := some currentHeading
          capturedHeadingDegreesMagnetic := some currentHeading }
      let step :=
        compileResolvedStep
          0
          .route
          (.continuePresentHeading target)
          (.vector vector)
          (by simp [resolutionCompatible])
      refine ⟨{ initialState with currentHeadingDegreesMagnetic := some currentHeading }, step, ?_⟩
      simpa [step, vector, hCurrentHeading, hInitialHeadingState] using
        (ResolvesIndexedStep.continuePresentHeading
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .route)
          (index := 0)
          (target := target)
          (headingDegreesMagnetic := currentHeading)
          (currentPoint := initialState.currentPoint))
  | stopTurn target =>
      rcases resolvesIndexedPlainInstruction
          (world := world)
          (state := initialState)
          (fallbackDomain := .route)
          (index := 0)
          (instruction := .stopTurn target)
          (by simp [instructionNeedsSpecificResolution]) with
          ⟨step, hStep⟩
      exact ⟨initialState, step, by simpa using hStep⟩
  | interceptLocaliser target =>
      rcases resolvesIndexedPlainInstruction
          (world := world)
          (state := initialState)
          (fallbackDomain := .route)
          (index := 0)
          (instruction := .interceptLocaliser target)
          (by simp [instructionNeedsSpecificResolution]) with
          ⟨step, hStep⟩
      exact ⟨initialState, step, by simpa using hStep⟩
  | _ =>
      cases hPrimary

theorem resolvesRouteControlCompoundClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hPrimary : RouteControlCurrentShapeInstruction primary)
    (hPrimaryReady : RouteControlCurrentShapeInstructionReady world primary)
    (hStateReady : RouteControlCurrentShapeStateReady initialState primary)
    (hTail : RouteControlCompoundTailReady world tail)
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ finalState resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState := by
  have hTailWrapped : anyWrappedConditionalStep tail = false :=
    anyWrappedConditionalStep_false_of_routeControlCompoundTailReady hTail
  have hWrapped :
      anyWrappedConditionalStep (primary :: tail) = false := by
    cases primary <;> try simp [anyWrappedConditionalStep, hTailWrapped] at hPrimary ⊢
    case conditionalClearance =>
      cases hPrimary
  rcases resolvesRouteControlPrimary_of_ready
      (world := world)
      (initialState := initialState)
      (primary := primary)
      hPrimary
      hPrimaryReady
      hStateReady with
      ⟨finalState, primaryStep, hPrimaryStep⟩
  rcases resolvesRouteBearingImmediateAdjunctTail_of_ready
      (world := world)
      (state := finalState)
      (fallbackDomain := .route)
      (start := 1)
      (tail := tail)
      hTail with
      ⟨resolvedTail, hResolvedTail⟩
  refine ⟨finalState, { source := clearance, steps := primaryStep :: resolvedTail }, ?_⟩
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
          finalState := by
      simpa [hDomain] using hPrimaryStep
    have hResolvedTail' :
        ResolvesSteps
          (RouteBearingScopedAviationWorld.toResolutionWorld world)
          finalState
          clearance.domain
          (enumerateFrom 1 tail)
          resolvedTail
          finalState := by
      simpa [hDomain] using hResolvedTail
    have hIndexed :
        indexedSteps (structuredInstructions clearance) =
          (0, primary) :: enumerateFrom 1 tail := by
      simp [hContent, hSteps, structuredInstructions, contentInstructions, indexedSteps, enumerateFrom]
    simpa [hIndexed] using
      ResolvesSteps.cons
        (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
        (state := initialState)
        (nextState := finalState)
        (finalState := finalState)
        (fallbackDomain := clearance.domain)
        (index := 0)
        (instruction := primary)
        (step := primaryStep)
        (tail := enumerateFrom 1 tail)
        (resolvedTail := resolvedTail)
        hPrimaryStep'
        hResolvedTail'

theorem GreenfieldRouteControlCompoundCurrentShapeReachableIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRouteControlCompoundCurrentShapeIssuable world initialState clearance) :
    ∃ finalState resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable
  rename_i content primary tail hContent hSteps hPrimary hPrimaryReady hStateReady hTail hDomain hCondition
  rcases resolvesRouteControlCompoundClearance_of_ready
      (world := world)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (primary := primary)
      (tail := tail)
      hContent
      hSteps
      hPrimary
      hPrimaryReady
      hStateReady
      hTail
      hDomain
      hCondition with
      ⟨finalState, resolved, hResolve⟩
  have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
    simpa [hResolve.sourceEq] using hFresh
  exact ⟨finalState, resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

theorem GreenfieldRouteControlCompoundCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldRouteControlCompoundCurrentShapeIssuable world initialState clearance)
    (hAuthority :
      RouteControlCompoundWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ finalState resolved,
      routeControlCompoundInstructionsIssuerAuthorized
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
      routeControlCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    routeControlCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GreenfieldRouteControlCompoundCurrentShapeReachableIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨finalState, resolved, hResolve, hReachable⟩
  exact ⟨finalState, resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedTurnByDegreesContact : ResolvedClearance :=
  { source :=
      { id := "CLR-TURN-DEG-CONTACT"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .turnByDegrees "TEST123" .left 90
              , .contactFrequency "TEST123" .approach none ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 70
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .route
          (.turnByDegrees "TEST123" .left 90)
          (.vector
            { kind := .turnByDegrees
              targetHeadingDegreesMagnetic := some 45
              turnDirection := some .left
              turnDegrees := some 90
              capturedHeadingDegreesMagnetic := some 135 })
          (by simp [resolutionCompatible])
      , compileResolvedStep
          1
          .route
          (.contactFrequency "TEST123" .approach none)
          (.frequencyChange { roleName := .approach, instructedFrequency := none })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedTurnByDegreesContact : ManagedResolvedClearance :=
  { resolved := sampleResolvedTurnByDegreesContact }

def sampleTurnByDegreesContactTailOnlyObservation : CompletionObservation :=
  { currentRole := some .approach }

def sampleTurnByDegreesContactCompleteObservation : CompletionObservation :=
  { currentRole := some .approach
    observedTurnDirection := some .left
    observedTurnDegrees := some 90 }

def sampleResolvedIncomingTowerContactForTurnByDegrees : ResolvedClearance :=
  { source :=
      { id := "CLR-TURN-DEG-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 71
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleResolvedProceedDirectContact : ResolvedClearance :=
  { source :=
      { id := "CLR-PD-CONTACT"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .proceedDirect "TEST123" "HOLD"
              , .contactFrequency "TEST123" .tower none ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 72
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .route
          (.proceedDirect "TEST123" "HOLD")
          (.directFix { fix := "HOLD", point := "FIX-HOLD" })
          (by simp [resolutionCompatible])
      , compileResolvedStep
          1
          .route
          (.contactFrequency "TEST123" .tower none)
          (.frequencyChange { roleName := .tower, instructedFrequency := none })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedProceedDirectContact : ManagedResolvedClearance :=
  { resolved := sampleResolvedProceedDirectContact }

def sampleProceedDirectContactObservation : CompletionObservation :=
  { currentRole := some .tower
    position := some "FIX-HOLD" }

theorem sampleTurnByDegreesContact_tail_only_completion_keeps_primary_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedTurnByDegreesContact
        sampleTurnByDegreesContactTailOnlyObservation
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem sampleTurnByDegreesContact_completes_on_turn_and_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedTurnByDegreesContact
        sampleTurnByDegreesContactCompleteObservation
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.ofList [0, 1] := by
  native_decide

theorem sampleTurnByDegreesContact_frequencySupersession_preserves_primary :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedTurnByDegreesContact]
        sampleResolvedIncomingTowerContactForTurnByDegrees
    resolvedClearanceIds admission.clearances = ["CLR-TURN-DEG-CONTACT", "CLR-TURN-DEG-FREQ"] ∧
      resolvedClearanceIds admission.fullySuperseded = [] ∧
      resolvedClearanceIds admission.partiallySuperseded = ["CLR-TURN-DEG-CONTACT"] ∧
      findResolvedById admission.clearances "CLR-TURN-DEG-CONTACT" =
        some (sampleManagedResolvedTurnByDegreesContact.suppress
          (UniqueSet.singleton .frequency)) := by
  native_decide

theorem sampleProceedDirectContact_completes_at_fix_after_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedProceedDirectContact
        sampleProceedDirectContactObservation
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.ofList [0, 1] := by
  native_decide

end Greenfield
end CertifiedAtc
