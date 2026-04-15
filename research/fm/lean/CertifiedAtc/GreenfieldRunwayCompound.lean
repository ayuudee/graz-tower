import CertifiedAtc.GreenfieldRunwayDeliveredCurrentShape
import CertifiedAtc.GreenfieldBacktrackCurrentShape
import CertifiedAtc.GreenfieldRouteBearingCompound

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape compound closure for the delivered runway-operation family.

This widens the already-delivered single-step runway-operation surface through
one narrow compound shape:

- one leading runway-operation primary from the delivered family
- zero or more immediate adjunct tails already understood by the current
  greenfield engine

It stays deliberately narrow:

- no new world-resolution theory
- no backtrack compounds
- no broader ground-movement package

The point is to close the first honest compound seam for the current delivered
runway-operation family without pretending the whole runway family is now
solved.
-/

inductive RunwayCompoundCurrentShapePrimary : AtcInstruction → Prop
  | lineUpAndWait
      {target : AircraftId}
      {runway : RunwayId} :
      RunwayCompoundCurrentShapePrimary (.lineUpAndWait target runway)
  | takeoff
      {target : AircraftId}
      {runway : RunwayId} :
      RunwayCompoundCurrentShapePrimary (.clearedForTakeoff target runway)
  | landing
      {target : AircraftId}
      {runway : RunwayId} :
      RunwayCompoundCurrentShapePrimary (.clearedToLand target runway)
  | touchAndGo
      {target : AircraftId}
      {runway : RunwayId} :
      RunwayCompoundCurrentShapePrimary (.clearedTouchAndGo target runway)
  | lowApproach
      {target : AircraftId}
      {runway : RunwayId} :
      RunwayCompoundCurrentShapePrimary (.clearedLowApproach target runway)
  | goAround
      {target : AircraftId} :
      RunwayCompoundCurrentShapePrimary (.goAround target)

theorem runwayCompoundCurrentShapePrimary_needsNoSpecificResolution
    {instruction : AtcInstruction}
    (hPrimary : RunwayCompoundCurrentShapePrimary instruction) :
    instructionNeedsSpecificResolution instruction = false := by
  cases hPrimary <;> simp [instructionNeedsSpecificResolution]

theorem runwayCompoundCurrentShapePrimary_not_wrappedConditional
    {instruction : AtcInstruction}
    (hPrimary : RunwayCompoundCurrentShapePrimary instruction) :
    anyWrappedConditionalStep [instruction] = false := by
  cases hPrimary <;> simp [anyWrappedConditionalStep]

def RunwayCompoundCurrentShapeReady
    (world : RouteBearingScopedAviationWorld)
    (primary : AtcInstruction)
    (tail : List AtcInstruction) : Prop :=
  RunwayCompoundCurrentShapePrimary primary ∧
    ∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction

theorem anyWrappedConditionalStep_false_of_runwayCompoundReady
    {world : RouteBearingScopedAviationWorld}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hReady : RunwayCompoundCurrentShapeReady world primary tail) :
    anyWrappedConditionalStep (primary :: tail) = false := by
  rcases hReady with ⟨hPrimary, hTail⟩
  have hPrimaryClear :
      anyWrappedConditionalStep [primary] = false :=
    runwayCompoundCurrentShapePrimary_not_wrappedConditional hPrimary
  have hTailClear :
      anyWrappedConditionalStep tail = false :=
    anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts hTail
  cases hPrimary <;>
    simp [anyWrappedConditionalStep] at hPrimaryClear ⊢
  all_goals exact hTailClear

def runwayCompoundInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | .lineUpAndWait _ _ => some currentShapeLineUpAndWaitAuthorityGrant
  | .clearedForTakeoff _ _ => some currentShapeTakeoffAuthorityGrant
  | .clearedToLand _ _ => some currentShapeLandingAuthorityGrant
  | .clearedTouchAndGo _ _ => some currentShapeTouchAndGoAuthorityGrant
  | .clearedLowApproach _ _ => some currentShapeLowApproachAuthorityGrant
  | .goAround _ => some currentShapeGoAroundAuthorityGrant
  | instruction => routeBearingCompoundInstructionRequiredAuthorityGrant? instruction

def runwayCompoundInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match runwayCompoundInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def runwayCompoundInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      runwayCompoundInstructionIssuerAuthorized view controller instruction &&
        runwayCompoundInstructionsIssuerAuthorized view controller tail

def RunwayCompoundWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps,
    match runwayCompoundInstructionRequiredAuthorityGrant? instruction with
    | none => True
    | some grant =>
        WorldControllerHasGrant world.toScopedAviationWorld controller grant

theorem runwayCompoundInstructionIssuerAuthorized_eq_true_of_unmapped
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instruction : AtcInstruction}
    (hUnmapped : runwayCompoundInstructionRequiredAuthorityGrant? instruction = none) :
    runwayCompoundInstructionIssuerAuthorized view controller instruction = true := by
  simp [runwayCompoundInstructionIssuerAuthorized, hUnmapped]

theorem runwayCompoundInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : RouteBearingScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMapped : runwayCompoundInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world.toScopedAviationWorld controller grant) :
    runwayCompoundInstructionIssuerAuthorized
      (extractRouteBearingCompileView world)
      controller
      instruction = true := by
  simp [runwayCompoundInstructionIssuerAuthorized, hMapped]
  change
    controllerHasAuthorityGrant
      (extractCompileView world.toScopedAviationWorld)
      controller
      grant = true
  exact
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf.baseWellFormed
      hGrant

theorem runwayCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : RouteBearingScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      RouteBearingExtractionWellFormed world →
      RunwayCompoundWorldAuthorized world controller steps →
        runwayCompoundInstructionsIssuerAuthorized
          (extractRouteBearingCompileView world)
          controller
          steps = true := by
  intro world controller steps hWf hAuth
  induction steps with
  | nil =>
      simp [runwayCompoundInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHeadAuth := hAuth head (by simp)
      have hTailAuth : RunwayCompoundWorldAuthorized world controller tail := by
        intro instruction hMem
        exact hAuth instruction (by simp [hMem])
      cases hGrant : runwayCompoundInstructionRequiredAuthorityGrant? head with
      | none =>
          simp [runwayCompoundInstructionsIssuerAuthorized,
            runwayCompoundInstructionIssuerAuthorized_eq_true_of_unmapped hGrant,
            ih hTailAuth]
      | some grant =>
          have hHeadGrant :
              WorldControllerHasGrant world.toScopedAviationWorld controller grant := by
            simpa [hGrant] using hHeadAuth
          have hHeadOk :
              runwayCompoundInstructionIssuerAuthorized
                (extractRouteBearingCompileView world)
                controller
                head = true :=
            runwayCompoundInstructionIssuerAuthorized_eq_true_of_worldAuthorized
              (world := world)
              (controller := controller)
              (instruction := head)
              (grant := grant)
              hWf
              hGrant
              hHeadGrant
          simp [runwayCompoundInstructionsIssuerAuthorized, hHeadOk, ih hTailAuth]

inductive RunwayCompoundCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | mk
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {primary : AtcInstruction}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps : content.steps = primary :: tail)
      (hReady : RunwayCompoundCurrentShapeReady world primary tail)
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      RunwayCompoundCurrentShapeIssuable world clearance

theorem resolvesRunwayCompoundCurrentShapeClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hReady : RunwayCompoundCurrentShapeReady world primary tail)
    (hDomain : clearance.domain = .runway)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  rcases hReady with ⟨hPrimary, hTail⟩
  have hWrapped :
      anyWrappedConditionalStep (primary :: tail) = false :=
    anyWrappedConditionalStep_false_of_runwayCompoundReady
      (world := world)
      (primary := primary)
      (tail := tail)
      ⟨hPrimary, hTail⟩
  rcases resolvesIndexedPlainInstruction
      (world := world)
      (state := initialState)
      (fallbackDomain := .runway)
      (index := 0)
      (instruction := primary)
      (runwayCompoundCurrentShapePrimary_needsNoSpecificResolution hPrimary) with
      ⟨primaryStep, hPrimaryStep⟩
  rcases resolvesRouteBearingImmediateAdjunctTail_of_ready
      (world := world)
      (state := initialState)
      (fallbackDomain := .runway)
      (start := 1)
      (tail := tail)
      hTail with
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
          primary
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
          (0, primary) :: enumerateFrom 1 tail := by
      simp [hContent, hSteps, structuredInstructions, contentInstructions, indexedSteps, enumerateFrom]
    simpa [hIndexed] using
      ResolvesSteps.cons
        (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
        (state := initialState)
        (nextState := initialState)
        (finalState := initialState)
        (fallbackDomain := clearance.domain)
        (index := 0)
        (instruction := primary)
        (step := primaryStep)
        (tail := enumerateFrom 1 tail)
        (resolvedTail := resolvedTail)
        hPrimaryStep'
        hResolvedTail'

theorem RunwayCompoundCurrentShapeIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : RunwayCompoundCurrentShapeIssuable world clearance) :
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
  rename_i content primary tail hContent hSteps hReady hDomain hCondition
  rcases resolvesRunwayCompoundCurrentShapeClearance_of_ready
      (world := world)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (primary := primary)
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

theorem RunwayCompoundCurrentShapeAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : RunwayCompoundCurrentShapeIssuable world clearance)
    (hAuthority :
      RunwayCompoundWorldAuthorized world clearance.issuedBy (structuredInstructions clearance)) :
    ∃ resolved,
      runwayCompoundInstructionsIssuerAuthorized
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
      runwayCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    runwayCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases RunwayCompoundCurrentShapeIssuanceTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedLineUpAndWaitContactCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-LUP-COMP"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .lineUpAndWait "TEST123" "RWY-09"
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 100
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.lineUpAndWait "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedLineUpAndWaitContactCompound : ManagedResolvedClearance :=
  { resolved := sampleResolvedLineUpAndWaitContactCompound }

def sampleResolvedTakeoffContactCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-TO-COMP"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .clearedForTakeoff "TEST123" "RWY-09"
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 101
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedForTakeoff "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedTakeoffContactCompound : ManagedResolvedClearance :=
  { resolved := sampleResolvedTakeoffContactCompound }

def sampleResolvedLandingContactCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-LAND-COMP"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .clearedToLand "TEST123" "RWY-09"
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 102
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedToLand "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedLandingContactCompound : ManagedResolvedClearance :=
  { resolved := sampleResolvedLandingContactCompound }

def sampleResolvedTouchAndGoContactCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-TNG-COMP"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .clearedTouchAndGo "TEST123" "RWY-09"
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 103
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedTouchAndGo "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedTouchAndGoContactCompound : ManagedResolvedClearance :=
  { resolved := sampleResolvedTouchAndGoContactCompound }

def sampleResolvedLowApproachContactCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-LA-COMP"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .clearedLowApproach "TEST123" "RWY-09"
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 104
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.clearedLowApproach "TEST123" "RWY-09")
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedLowApproachContactCompound : ManagedResolvedClearance :=
  { resolved := sampleResolvedLowApproachContactCompound }

def sampleResolvedGoAroundContactCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-GA-COMP"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .goAround "TEST123"
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 105
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .runway
          (.goAround "TEST123")
          (by simp [instructionNeedsSpecificResolution])
      , compileResolvedStep
          1
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedResolvedGoAroundContactCompound : ManagedResolvedClearance :=
  { resolved := sampleResolvedGoAroundContactCompound }

theorem lineUpAndWaitContactCompound_completes_on_tail_completion :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedLineUpAndWaitContactCompound
        { currentRole := some .tower }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem lineUpAndWaitContactCompound_reconcile_transitions_to_terminal :
    let reconciliation :=
      reconcileResolvedClearances
        [sampleManagedResolvedLineUpAndWaitContactCompound]
        { currentRole := some .tower }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = [] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-LUP-COMP"] := by
  native_decide

theorem takeoffContactCompound_completes_on_airborne_and_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedTakeoffContactCompound
        { onGround := false
          currentRole := some .tower }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.ofList [0, 1] := by
  native_decide

theorem landingContactCompound_completes_on_runway_vacation_and_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedLandingContactCompound
        { runwayTransitions := UniqueSet.singleton "RWY-09"
          currentRole := some .tower }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.ofList [0, 1] := by
  native_decide

theorem touchAndGoContactCompound_completes_on_transition_airborne_and_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedTouchAndGoContactCompound
        { onGround := false
          runwayTransitions := UniqueSet.singleton "RWY-09"
          activeRunways := UniqueSet.singleton "RWY-09"
          currentRole := some .tower }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.ofList [0, 1] := by
  native_decide

theorem lowApproachContactCompound_completes_on_transition_exit_and_contact :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedLowApproachContactCompound
        { onGround := false
          runwayTransitions := UniqueSet.singleton "RWY-09"
          currentRole := some .tower }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.ofList [0, 1] := by
  native_decide

theorem goAroundContactCompound_tail_completion_keeps_primary_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedGoAroundContactCompound
        { currentRole := some .tower }
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem lineUpAndWaitContact_frequencySupersession_preserves_primary :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedLineUpAndWaitContactCompound]
        sampleResolvedIncomingTowerContactForLineUpAndWait
    resolvedClearanceIds admission.clearances = ["CLR-LUP-COMP", "CLR-LUP-FREQ"] ∧
      resolvedClearanceIds admission.fullySuperseded = [] ∧
      resolvedClearanceIds admission.partiallySuperseded = ["CLR-LUP-COMP"] ∧
      findResolvedById admission.clearances "CLR-LUP-COMP" =
        some (sampleManagedResolvedLineUpAndWaitContactCompound.suppress
          (UniqueSet.singleton .frequency)) := by
  native_decide

theorem lineUpAndWaitContact_frequencySupersession_reconcile_transitions_to_terminal :
    let admission :=
      admitResolvedClearance
        [sampleManagedResolvedLineUpAndWaitContactCompound]
        sampleResolvedIncomingTowerContactForLineUpAndWait
    let reconciliation :=
      reconcileResolvedClearances
        admission.clearances
        {}
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-LUP-FREQ"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-LUP-COMP"] := by
  native_decide

end Greenfield
end CertifiedAtc
