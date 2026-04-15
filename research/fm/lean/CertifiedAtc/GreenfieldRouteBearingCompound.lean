import CertifiedAtc.GreenfieldRouteBearingAdmission

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape compound route-bearing surface.

This module widens the current-shape route-bearing proof story from single
clearances to a narrow but useful compound family:

- one leading route-bearing instruction from the widened Phase A surface
- zero or more immediate adjunct instructions

That matches the current Kotlin design intent for compounds such as:

- `ClearedTo` + climb / squawk / frequency
- `ClearedApproach` + `MaintainAltitudeUntilEstablished`

while staying honest about the current model:

- the leading route-bearing step is still the only specifically resolved
  procedure-bearing step
- adjunct steps either resolve through the existing role-frequency seam or are
  plain immediate instructions
- the result is a whole-clearance resolved admission theorem, not a legacy
  atomic-bridge theorem
- world-backed `ClearedApproach` completion is now modeled in the execution
  layer; this compound module is about admission and structure, while the
  lifecycle consequences of landing / missed-approach-hold events live in
  `GreenfieldRouteBearingLifecycle`
-/

def RouteBearingImmediateAdjunctReady
    (world : RouteBearingScopedAviationWorld) :
    AtcInstruction → Prop
  | .climbTo _ _ => True
  | .descendTo _ _ => True
  | .expediteClimb _ _ => True
  | .expediteDescend _ _ => True
  | .maintainLevel _ _ => True
  | .stopClimbAt _ _ => True
  | .stopDescentAt _ _ => True
  | .maintainAtOrAbove _ _ => True
  | .maintainAtOrBelow _ _ => True
  | .afterPassingLevelClimbTo _ _ _ => True
  | .afterPassingLevelDescendTo _ _ _ => True
  | .maintainAltitudeUntilEstablished _ _ _ => True
  | .maintainSpeed _ _ => True
  | .reduceSpeedTo _ _ => True
  | .increaseSpeedTo _ _ => True
  | .setSquawk _ _ => True
  | .confirmSquawk _ _ => True
  | .squawkIdent _ => True
  | .squawkStandby _ => True
  | .squawkNormal _ _ => True
  | .stopSquawk _ _ => True
  | .setPressure _ _ => True
  | .contactFrequency _ _ (some _) => True
  | .contactFrequency _ role none =>
      ∃ frequency,
        (RouteBearingScopedAviationWorld.toResolutionWorld world).roleFrequency role frequency
  | .monitorFrequency _ _ (some _) => True
  | .monitorFrequency _ role none =>
      ∃ frequency,
        (RouteBearingScopedAviationWorld.toResolutionWorld world).roleFrequency role frequency
  | _ => False

def GreenfieldRouteBearingCompoundReady
    (world : RouteBearingScopedAviationWorld)
    (primary : AtcInstruction)
    (tail : List AtcInstruction) : Prop :=
  GreenfieldRouteBearingAdmissibleInstruction primary ∧
    RouteBearingInstructionResolutionReady world primary ∧
    ∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction

def routeBearingCompoundInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | instruction =>
      match greenfieldRouteBearingRequiredAuthorityGrant? instruction with
      | some grant => some grant
      | none =>
          match instruction with
          | .climbTo _ _ => some { entityType := .airspaceVolume, operation := .altitude }
          | .descendTo _ _ => some { entityType := .airspaceVolume, operation := .altitude }
          | .expediteClimb _ _ => some { entityType := .airspaceVolume, operation := .altitude }
          | .expediteDescend _ _ => some { entityType := .airspaceVolume, operation := .altitude }
          | .maintainLevel _ _ => some { entityType := .airspaceVolume, operation := .altitude }
          | .stopClimbAt _ _ => some { entityType := .airspaceVolume, operation := .altitude }
          | .stopDescentAt _ _ => some { entityType := .airspaceVolume, operation := .altitude }
          | .maintainAtOrAbove _ _ => some { entityType := .airspaceVolume, operation := .altitude }
          | .maintainAtOrBelow _ _ => some { entityType := .airspaceVolume, operation := .altitude }
          | .afterPassingLevelClimbTo _ _ _ =>
              some { entityType := .airspaceVolume, operation := .altitude }
          | .afterPassingLevelDescendTo _ _ _ =>
              some { entityType := .airspaceVolume, operation := .altitude }
          | .maintainAltitudeUntilEstablished _ _ _ =>
              some { entityType := .airspaceVolume, operation := .altitude }
          | .maintainSpeed _ _ => some { entityType := .airspaceVolume, operation := .speed }
          | .reduceSpeedTo _ _ => some { entityType := .airspaceVolume, operation := .speed }
          | .increaseSpeedTo _ _ => some { entityType := .airspaceVolume, operation := .speed }
          | .minimumCleanSpeed _ => some { entityType := .airspaceVolume, operation := .speed }
          | .resumeNormalSpeed _ => some { entityType := .airspaceVolume, operation := .speed }
          | .setSquawk _ _ => some { entityType := .radioRole, operation := .squawk }
          | .confirmSquawk _ _ => some { entityType := .radioRole, operation := .squawk }
          | .squawkIdent _ => some { entityType := .radioRole, operation := .squawk }
          | .squawkStandby _ => some { entityType := .radioRole, operation := .squawk }
          | .squawkNormal _ _ => some { entityType := .radioRole, operation := .squawk }
          | .stopSquawk _ _ => some { entityType := .radioRole, operation := .squawk }
          | .contactFrequency _ _ _ =>
              some { entityType := .radioRole, operation := .contact }
          | .monitorFrequency _ _ _ =>
              some { entityType := .radioRole, operation := .monitor }
          | _ => none

def routeBearingCompoundInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match routeBearingCompoundInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def routeBearingCompoundInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      routeBearingCompoundInstructionIssuerAuthorized view controller instruction &&
        routeBearingCompoundInstructionsIssuerAuthorized view controller tail

def RouteBearingCompoundWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps,
    match routeBearingCompoundInstructionRequiredAuthorityGrant? instruction with
    | none => True
    | some grant =>
        WorldControllerHasGrant world.toScopedAviationWorld controller grant

theorem routeBearingImmediateAdjunctReady_immediate
    {world : RouteBearingScopedAviationWorld}
    {instruction : AtcInstruction}
    (hReady : RouteBearingImmediateAdjunctReady world instruction) :
    instructionFrontierTiming instruction = .immediate := by
  cases instruction <;>
    simp [RouteBearingImmediateAdjunctReady, instructionFrontierTiming] at hReady ⊢

theorem routeBearingImmediateAdjunctReady_not_wrappedConditional
    {world : RouteBearingScopedAviationWorld}
    {instruction : AtcInstruction}
    (hReady : RouteBearingImmediateAdjunctReady world instruction) :
    anyWrappedConditionalStep [instruction] = false := by
  cases instruction <;>
    simp [RouteBearingImmediateAdjunctReady, anyWrappedConditionalStep] at hReady ⊢

theorem routeBearingPrimary_not_wrappedConditional
    {instruction : AtcInstruction}
    (hPrimary : GreenfieldRouteBearingAdmissibleInstruction instruction) :
    anyWrappedConditionalStep [instruction] = false := by
  cases instruction <;>
    simp [GreenfieldRouteBearingAdmissibleInstruction, anyWrappedConditionalStep] at hPrimary ⊢

theorem anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts :
    ∀ {world : RouteBearingScopedAviationWorld} {steps : List AtcInstruction},
      (∀ instruction ∈ steps, RouteBearingImmediateAdjunctReady world instruction) →
        anyWrappedConditionalStep steps = false := by
  intro world steps hReady
  induction steps with
  | nil =>
      simp [anyWrappedConditionalStep]
  | cons head tail ih =>
      have hHead : RouteBearingImmediateAdjunctReady world head := hReady head (by simp)
      have hTail : ∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction := by
        intro instruction hMem
        exact hReady instruction (by simp [hMem])
      cases head <;>
        simp [RouteBearingImmediateAdjunctReady, anyWrappedConditionalStep] at hHead ⊢
      all_goals exact ih hTail

theorem anyWrappedConditionalStep_false_of_routeBearingCompoundReady
    {world : RouteBearingScopedAviationWorld}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hReady : GreenfieldRouteBearingCompoundReady world primary tail) :
    anyWrappedConditionalStep (primary :: tail) = false := by
  rcases hReady with ⟨hPrimary, _, hTail⟩
  have hPrimaryClear : anyWrappedConditionalStep [primary] = false :=
    routeBearingPrimary_not_wrappedConditional hPrimary
  have hTailClear : anyWrappedConditionalStep tail = false :=
    anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts hTail
  cases primary <;>
    simp [GreenfieldRouteBearingAdmissibleInstruction, anyWrappedConditionalStep] at hPrimary hPrimaryClear ⊢
  all_goals exact hTailClear

theorem routeBearingCompoundInstructionIssuerAuthorized_eq_true_of_unmapped
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instruction : AtcInstruction}
    (hUnmapped : routeBearingCompoundInstructionRequiredAuthorityGrant? instruction = none) :
    routeBearingCompoundInstructionIssuerAuthorized view controller instruction = true := by
  simp [routeBearingCompoundInstructionIssuerAuthorized, hUnmapped]

theorem routeBearingCompoundInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : RouteBearingScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMapped : routeBearingCompoundInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world.toScopedAviationWorld controller grant) :
    routeBearingCompoundInstructionIssuerAuthorized
      (extractRouteBearingCompileView world)
      controller
      instruction = true := by
  simp [routeBearingCompoundInstructionIssuerAuthorized, hMapped]
  change
    controllerHasAuthorityGrant
      (extractCompileView world.toScopedAviationWorld)
      controller
      grant = true
  exact
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf.baseWellFormed
      hGrant

theorem routeBearingCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : RouteBearingScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      RouteBearingExtractionWellFormed world →
      RouteBearingCompoundWorldAuthorized world controller steps →
        routeBearingCompoundInstructionsIssuerAuthorized
          (extractRouteBearingCompileView world)
          controller
          steps = true := by
  intro world controller steps hWf hAuth
  induction steps with
  | nil =>
      simp [routeBearingCompoundInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHeadAuth := hAuth head (by simp)
      have hTailAuth : RouteBearingCompoundWorldAuthorized world controller tail := by
        intro instruction hMem
        exact hAuth instruction (by simp [hMem])
      cases hGrant : routeBearingCompoundInstructionRequiredAuthorityGrant? head with
      | none =>
          simp [routeBearingCompoundInstructionsIssuerAuthorized,
            routeBearingCompoundInstructionIssuerAuthorized_eq_true_of_unmapped hGrant,
            ih hTailAuth]
      | some grant =>
          have hHeadGrant :
              WorldControllerHasGrant world.toScopedAviationWorld controller grant := by
            simpa [hGrant] using hHeadAuth
          have hHeadOk :
              routeBearingCompoundInstructionIssuerAuthorized
                (extractRouteBearingCompileView world)
                controller
                head = true :=
            routeBearingCompoundInstructionIssuerAuthorized_eq_true_of_worldAuthorized
              (world := world)
              (controller := controller)
              (instruction := head)
              hWf
              hGrant
              hHeadGrant
          simp [routeBearingCompoundInstructionsIssuerAuthorized, hHeadOk, ih hTailAuth]

theorem resolvesIndexedPlainInstruction
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {fallbackDomain : ClearanceDomain}
    {index : Nat}
    {instruction : AtcInstruction}
    (hPlain : instructionNeedsSpecificResolution instruction = false) :
    ∃ step,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        fallbackDomain
        index
        instruction
        step
        state := by
  refine ⟨compileResolvedStep
      index
      fallbackDomain
      instruction
      .plain
      (by simp [resolutionCompatible, hPlain]), ?_⟩
  exact
    ResolvesIndexedStep.plain
      (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
      (fallbackDomain := fallbackDomain)
      (index := index)
      (instruction := instruction)
      (state := state)
      hPlain

theorem resolvesIndexedGreenfieldRouteBearingStep_of_ready
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {index : Nat}
    {instruction : AtcInstruction}
    (hWf : RouteBearingExtractionWellFormed world)
    (hPrimary : GreenfieldRouteBearingAdmissibleInstruction instruction)
    (hReady : RouteBearingInstructionResolutionReady world instruction) :
    ∃ step,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        (greenfieldRouteBearingAdmissibleDomain instruction)
        index
        instruction
        step
        state := by
  cases instruction with
  | clearedTo target clearanceLimit route =>
      simpa [greenfieldRouteBearingAdmissibleDomain] using
        resolvesIndexedClearedTo_of_ready
          (world := world)
          (state := state)
          (index := index)
          (target := target)
          (clearanceLimit := clearanceLimit)
          (route := route)
          hWf
          hReady
  | holdAt target hold efc =>
      cases hold with
      | published fixId =>
          simpa [greenfieldRouteBearingAdmissibleDomain] using
            resolvesIndexedPublishedHoldAt_of_ready
              (world := world)
              (state := state)
              (index := index)
              (target := target)
              (fixId := fixId)
              (efc := efc)
              hReady
      | inboundTrack fix degrees direction legTime legDistance =>
          cases hPrimary
  | clearedApproach target approachType runway circlingRunway =>
      cases circlingRunway with
      | none =>
          simpa [greenfieldRouteBearingAdmissibleDomain] using
            resolvesIndexedNonCirclingClearedApproach_of_ready
              (world := world)
              (state := state)
              (index := index)
              (target := target)
              (approachType := approachType)
              (runway := runway)
              hWf
              hReady
      | some circling =>
          cases hPrimary
  | joinCircuit target direction joinType runway =>
      simpa [greenfieldRouteBearingAdmissibleDomain] using
        resolvesIndexedJoinCircuit_of_ready
          (world := world)
          (state := state)
          (index := index)
          (target := target)
          (direction := direction)
          (joinType := joinType)
          (runway := runway)
          hWf
          hReady
  | _ =>
      cases hPrimary

theorem resolvesIndexedRouteBearingImmediateAdjunct_of_ready
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {fallbackDomain : ClearanceDomain}
    {index : Nat}
    {instruction : AtcInstruction}
    (hReady : RouteBearingImmediateAdjunctReady world instruction) :
    ∃ step,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        fallbackDomain
        index
        instruction
        step
        state := by
  cases instruction with
  | climbTo target level =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .climbTo target level)
          (by simp [instructionNeedsSpecificResolution])
  | descendTo target level =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .descendTo target level)
          (by simp [instructionNeedsSpecificResolution])
  | expediteClimb target level =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .expediteClimb target level)
          (by simp [instructionNeedsSpecificResolution])
  | expediteDescend target level =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .expediteDescend target level)
          (by simp [instructionNeedsSpecificResolution])
  | maintainLevel target level =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .maintainLevel target level)
          (by simp [instructionNeedsSpecificResolution])
  | stopClimbAt target level =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .stopClimbAt target level)
          (by simp [instructionNeedsSpecificResolution])
  | stopDescentAt target level =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .stopDescentAt target level)
          (by simp [instructionNeedsSpecificResolution])
  | maintainAtOrAbove target minimumLevel =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .maintainAtOrAbove target minimumLevel)
          (by simp [instructionNeedsSpecificResolution])
  | maintainAtOrBelow target maximumLevel =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .maintainAtOrBelow target maximumLevel)
          (by simp [instructionNeedsSpecificResolution])
  | afterPassingLevelClimbTo target afterPassing climbTo =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .afterPassingLevelClimbTo target afterPassing climbTo)
          (by simp [instructionNeedsSpecificResolution])
  | afterPassingLevelDescendTo target afterPassing descendTo =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .afterPassingLevelDescendTo target afterPassing descendTo)
          (by simp [instructionNeedsSpecificResolution])
  | maintainAltitudeUntilEstablished target level on =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .maintainAltitudeUntilEstablished target level on)
          (by simp [instructionNeedsSpecificResolution])
  | maintainSpeed target speed =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .maintainSpeed target speed)
          (by simp [instructionNeedsSpecificResolution])
  | reduceSpeedTo target speed =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .reduceSpeedTo target speed)
          (by simp [instructionNeedsSpecificResolution])
  | increaseSpeedTo target speed =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .increaseSpeedTo target speed)
          (by simp [instructionNeedsSpecificResolution])
  | setSquawk target code =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .setSquawk target code)
          (by simp [instructionNeedsSpecificResolution])
  | confirmSquawk target code =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .confirmSquawk target code)
          (by simp [instructionNeedsSpecificResolution])
  | squawkIdent target =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .squawkIdent target)
          (by simp [instructionNeedsSpecificResolution])
  | squawkStandby target =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .squawkStandby target)
          (by simp [instructionNeedsSpecificResolution])
  | squawkNormal target mode =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .squawkNormal target mode)
          (by simp [instructionNeedsSpecificResolution])
  | stopSquawk target mode =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .stopSquawk target mode)
          (by simp [instructionNeedsSpecificResolution])
  | setPressure target pressure =>
      exact
        resolvesIndexedPlainInstruction
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := index)
          (instruction := .setPressure target pressure)
          (by simp [instructionNeedsSpecificResolution])
  | contactFrequency target role frequency =>
      cases frequency with
      | none =>
          rcases hReady with ⟨resolvedFrequency, hFrequency⟩
          refine ⟨compileResolvedStep
              index
              fallbackDomain
              (.contactFrequency target role none)
              (.frequencyChange { roleName := role, instructedFrequency := some resolvedFrequency })
              (by simp [resolutionCompatible]), ?_⟩
          apply ResolvesIndexedStep.contactFrequencyImplicit
          exact hFrequency
      | some frequency =>
          refine ⟨compileResolvedStep
              index
              fallbackDomain
              (.contactFrequency target role (some frequency))
              (.frequencyChange { roleName := role, instructedFrequency := some frequency })
              (by simp [resolutionCompatible]), ?_⟩
          apply ResolvesIndexedStep.contactFrequencyExplicit
  | monitorFrequency target role frequency =>
      cases frequency with
      | none =>
          rcases hReady with ⟨resolvedFrequency, hFrequency⟩
          refine ⟨compileResolvedStep
              index
              fallbackDomain
              (.monitorFrequency target role none)
              (.frequencyChange { roleName := role, instructedFrequency := some resolvedFrequency })
              (by simp [resolutionCompatible]), ?_⟩
          apply ResolvesIndexedStep.monitorFrequencyImplicit
          exact hFrequency
      | some frequency =>
          refine ⟨compileResolvedStep
              index
              fallbackDomain
              (.monitorFrequency target role (some frequency))
              (.frequencyChange { roleName := role, instructedFrequency := some frequency })
              (by simp [resolutionCompatible]), ?_⟩
          apply ResolvesIndexedStep.monitorFrequencyExplicit
  | _ =>
      cases hReady

theorem resolvesRouteBearingImmediateAdjunctTail_of_ready :
    ∀ {world : RouteBearingScopedAviationWorld}
      {state : ResolutionState}
      {fallbackDomain : ClearanceDomain}
      {start : Nat}
      {tail : List AtcInstruction},
      (∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction) →
        ∃ resolvedTail,
          ResolvesSteps
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            state
            fallbackDomain
            (enumerateFrom start tail)
            resolvedTail
            state := by
  intro world state fallbackDomain start tail hReady
  induction tail generalizing start with
  | nil =>
      refine ⟨[], ?_⟩
      simpa [enumerateFrom] using
        ResolvesSteps.nil
          (RouteBearingScopedAviationWorld.toResolutionWorld world)
          state
          fallbackDomain
  | cons head tail ih =>
      have hHead : RouteBearingImmediateAdjunctReady world head := hReady head (by simp)
      have hTail : ∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction := by
        intro instruction hMem
        exact hReady instruction (by simp [hMem])
      rcases resolvesIndexedRouteBearingImmediateAdjunct_of_ready
          (world := world)
          (state := state)
          (fallbackDomain := fallbackDomain)
          (index := start)
          (instruction := head)
          hHead with ⟨step, hStep⟩
      rcases ih (start := start + 1) hTail with ⟨resolvedTail, hResolvedTail⟩
      refine ⟨step :: resolvedTail, ?_⟩
      simpa [enumerateFrom] using
        ResolvesSteps.cons
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (state := state)
          (nextState := state)
          (finalState := state)
          (fallbackDomain := fallbackDomain)
          (index := start)
          (instruction := head)
          (step := step)
          (tail := enumerateFrom (start + 1) tail)
          (resolvedTail := resolvedTail)
          hStep
          hResolvedTail

theorem resolvesGreenfieldRouteBearingCompoundClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hWf : RouteBearingExtractionWellFormed world)
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hReady : GreenfieldRouteBearingCompoundReady world primary tail)
    (hDomain : clearance.domain = greenfieldRouteBearingAdmissibleDomain primary)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  have hWrapped : anyWrappedConditionalStep (primary :: tail) = false :=
    anyWrappedConditionalStep_false_of_routeBearingCompoundReady hReady
  rcases hReady with ⟨hPrimary, hPrimaryReady, hTailReady⟩
  rcases resolvesIndexedGreenfieldRouteBearingStep_of_ready
      (world := world)
      (state := initialState)
      (index := 0)
      (instruction := primary)
      hWf
      hPrimary
      hPrimaryReady with ⟨primaryStep, hPrimaryStep⟩
  rcases resolvesRouteBearingImmediateAdjunctTail_of_ready
      (world := world)
      (state := initialState)
      (fallbackDomain := greenfieldRouteBearingAdmissibleDomain primary)
      (start := 1)
      (tail := tail)
      hTailReady with ⟨resolvedTail, hResolvedTail⟩
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

theorem GreenfieldRouteBearingCompoundAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hReady : GreenfieldRouteBearingCompoundReady world primary tail)
    (hDomain : clearance.domain = greenfieldRouteBearingAdmissibleDomain primary)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  rcases resolvesGreenfieldRouteBearingCompoundClearance_of_ready
      (world := world)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (primary := primary)
      (tail := tail)
      hWf
      hContent
      hSteps
      hReady
      hDomain
      hCondition with ⟨resolved, hResolve⟩
  have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
    simpa [hResolve.sourceEq] using hFresh
  refine ⟨resolved, hResolve, ?_⟩
  exact ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve

theorem GreenfieldRouteBearingCompoundCurrentShapeIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hReady : GreenfieldRouteBearingCompoundReady world primary tail)
    (hDomain : clearance.domain = greenfieldRouteBearingAdmissibleDomain primary)
    (hCondition : clearance.condition = none)
    (hAuthority :
      RouteBearingCompoundWorldAuthorized world clearance.issuedBy (primary :: tail)) :
    ∃ resolved,
      routeBearingCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (primary :: tail) = true ∧
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      routeBearingCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (primary :: tail) = true :=
    routeBearingCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := primary :: tail)
      hWf
      hAuthority
  rcases GreenfieldRouteBearingCompoundAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (primary := primary)
      (tail := tail)
      hWf
      hReach
      hFresh
      hContent
      hSteps
      hReady
      hDomain
      hCondition with ⟨resolved, hResolve, hReachResolved⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachResolved⟩

end Greenfield
end CertifiedAtc
