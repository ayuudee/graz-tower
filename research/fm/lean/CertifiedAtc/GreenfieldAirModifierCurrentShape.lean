import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape closure for the remaining stable plain modifier/runtime family.

This module closes the part of the Kotlin surface whose lifecycle is already
explicit and which does not need additional world-resolution theory:

- level instructions
- speed instructions
- `SetPressure`
- `CancelClearance`

The authority story is intentionally conservative at the current type-level
granularity:

- level instructions -> `(airspaceVolume, altitude)`
- speed instructions -> `(airspaceVolume, speed)`
- pressure/admin immediates -> `(airspaceVolume, information)`
-/

def AirModifierCurrentShapeInstruction : AtcInstruction → Prop
  | .climbTo _ _ => True
  | .descendTo _ _ => True
  | .descendWhenReady _ _ => True
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
  | .avoidLevel _ _ => True
  | .maintainSpeed _ _ => True
  | .reduceSpeedTo _ _ => True
  | .increaseSpeedTo _ _ => True
  | .minimumCleanSpeed _ => True
  | .resumeNormalSpeed _ => True
  | .setPressure _ _ => True
  | .cancelClearance _ => True
  | _ => False

def airModifierCurrentShapeInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | .climbTo _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .descendTo _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .descendWhenReady _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .expediteClimb _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .expediteDescend _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .maintainLevel _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .stopClimbAt _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .stopDescentAt _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .maintainAtOrAbove _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .maintainAtOrBelow _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .afterPassingLevelClimbTo _ _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .afterPassingLevelDescendTo _ _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .maintainAltitudeUntilEstablished _ _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .avoidLevel _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .maintainSpeed _ _ =>
      some { entityType := .airspaceVolume, operation := .speed }
  | .reduceSpeedTo _ _ =>
      some { entityType := .airspaceVolume, operation := .speed }
  | .increaseSpeedTo _ _ =>
      some { entityType := .airspaceVolume, operation := .speed }
  | .minimumCleanSpeed _ =>
      some { entityType := .airspaceVolume, operation := .speed }
  | .resumeNormalSpeed _ =>
      some { entityType := .airspaceVolume, operation := .speed }
  | .setPressure _ _ =>
      some { entityType := .airspaceVolume, operation := .information }
  | .cancelClearance _ =>
      some { entityType := .airspaceVolume, operation := .information }
  | _ => none

def airModifierCurrentShapeInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match airModifierCurrentShapeInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def airModifierCurrentShapeInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      airModifierCurrentShapeInstructionIssuerAuthorized view controller instruction &&
        airModifierCurrentShapeInstructionsIssuerAuthorized view controller tail

def GreenfieldAirModifierCurrentShapeWorldAuthorized
    (world : ScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps,
    match airModifierCurrentShapeInstructionRequiredAuthorityGrant? instruction with
    | none => True
    | some grant => WorldControllerHasGrant world controller grant

def GreenfieldAirModifierCurrentShapeIssuable
    (clearance : StructuredClearance) : Prop :=
  match clearance.content with
  | .single instruction =>
      AirModifierCurrentShapeInstruction instruction ∧
        clearance.condition = none ∧
        match instructionDomain? instruction with
        | some domain => clearance.domain = domain
        | none => True
  | .compound _ => False

theorem airModifierCurrentShapeInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : ScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : ScopedExtractionWellFormed world)
    (hMapped : airModifierCurrentShapeInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world controller grant) :
    airModifierCurrentShapeInstructionIssuerAuthorized
      (extractCompileView world)
      controller
      instruction = true := by
  simp [airModifierCurrentShapeInstructionIssuerAuthorized, hMapped]
  exact controllerHasAuthorityGrant_of_worldControllerHasGrant hWf hGrant

theorem airModifierCurrentShapeInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : ScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      ScopedExtractionWellFormed world →
      GreenfieldAirModifierCurrentShapeWorldAuthorized world controller steps →
        airModifierCurrentShapeInstructionsIssuerAuthorized
          (extractCompileView world)
          controller
          steps = true := by
  intro world controller steps hWf hAuth
  induction steps with
  | nil =>
      simp [airModifierCurrentShapeInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHeadAuth := hAuth head (by simp)
      have hTailAuth : GreenfieldAirModifierCurrentShapeWorldAuthorized world controller tail := by
        intro instruction hMem
        exact hAuth instruction (by simp [hMem])
      cases hGrant : airModifierCurrentShapeInstructionRequiredAuthorityGrant? head with
      | none =>
          simp [airModifierCurrentShapeInstructionsIssuerAuthorized,
            airModifierCurrentShapeInstructionIssuerAuthorized, hGrant, ih hTailAuth]
      | some grant =>
          have hHeadGrant : WorldControllerHasGrant world controller grant := by
            simpa [hGrant] using hHeadAuth
          have hHeadOk :
              airModifierCurrentShapeInstructionIssuerAuthorized
                (extractCompileView world)
                controller
                head = true :=
            airModifierCurrentShapeInstructionIssuerAuthorized_eq_true_of_worldAuthorized
              (world := world)
              (controller := controller)
              (instruction := head)
              (grant := grant)
              hWf
              hGrant
              hHeadGrant
          simp [airModifierCurrentShapeInstructionsIssuerAuthorized, hHeadOk, ih hTailAuth]

theorem GreenfieldAirModifierCurrentShapeReachableIssuanceTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldAirModifierCurrentShapeIssuable clearance) :
    ∃ resolved,
      ResolvesClearance
        world
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hContent : clearance.content with
  | single instruction =>
      simp [GreenfieldAirModifierCurrentShapeIssuable, hContent] at hIssuable
      rcases hIssuable with ⟨hInstruction, hCondition, hDomain⟩
      have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
        cases instruction <;>
          simp [AirModifierCurrentShapeInstruction, normalizeConditionalEnvelope, hContent, hCondition] at hInstruction ⊢
      exact
        plainCurrentShapeAdmissionSoundnessTheorem_autoDomain
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          (instruction := instruction)
          hReach
          hFresh
          (by
            cases instruction <;>
              simp [AirModifierCurrentShapeInstruction, instructionNeedsSpecificResolution] at hInstruction ⊢)
          hNormalized
          hContent
          hDomain
  | compound content =>
      simp [GreenfieldAirModifierCurrentShapeIssuable, hContent] at hIssuable

theorem GreenfieldAirModifierCurrentShapeAuthorizedIssuanceTheorem
    {resolutionWorld : ResolutionWorld}
    {compileWorld : ScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : ScopedExtractionWellFormed compileWorld)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldAirModifierCurrentShapeIssuable clearance)
    (hAuthority :
      GreenfieldAirModifierCurrentShapeWorldAuthorized
        compileWorld
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ resolved,
      airModifierCurrentShapeInstructionsIssuerAuthorized
        (extractCompileView compileWorld)
        clearance.issuedBy
        (structuredInstructions clearance) = true ∧
      ResolvesClearance
        resolutionWorld
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      airModifierCurrentShapeInstructionsIssuerAuthorized
        (extractCompileView compileWorld)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    airModifierCurrentShapeInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      hWf
      hAuthority
  rcases GreenfieldAirModifierCurrentShapeReachableIssuanceTheorem
      (world := resolutionWorld)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedSingleClimbTo : ResolvedClearance :=
  { source :=
      { id := "CLR-CLIMB"
        aircraft := "TEST123"
        content := .single (.climbTo "TEST123" (.altitudeFeet 3000))
        domain := .level
        issuedBy := "CTRL-1"
        issuedAt := 100
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .level
          (.climbTo "TEST123" (.altitudeFeet 3000))
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleClimbTo : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleClimbTo }

def sampleResolvedSingleMaintainLevel : ResolvedClearance :=
  { source :=
      { id := "CLR-MAINTAIN-LVL"
        aircraft := "TEST123"
        content := .single (.maintainLevel "TEST123" (.altitudeFeet 2500))
        domain := .level
        issuedBy := "CTRL-1"
        issuedAt := 101
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .level
          (.maintainLevel "TEST123" (.altitudeFeet 2500))
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleMaintainLevel : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleMaintainLevel }

def sampleResolvedSingleMaintainSpeed : ResolvedClearance :=
  { source :=
      { id := "CLR-MAINTAIN-SPD"
        aircraft := "TEST123"
        content := .single (.maintainSpeed "TEST123" (.inKnots 180))
        domain := .speed
        issuedBy := "CTRL-1"
        issuedAt := 102
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .speed
          (.maintainSpeed "TEST123" (.inKnots 180))
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleMaintainSpeed : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleMaintainSpeed }

def sampleResolvedSingleMinimumCleanSpeed : ResolvedClearance :=
  { source :=
      { id := "CLR-MIN-CLEAN"
        aircraft := "TEST123"
        content := .single (.minimumCleanSpeed "TEST123")
        domain := .speed
        issuedBy := "CTRL-1"
        issuedAt := 103
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .speed
          (.minimumCleanSpeed "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleMinimumCleanSpeed : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleMinimumCleanSpeed }

def sampleResolvedSingleSetPressure : ResolvedClearance :=
  { source :=
      { id := "CLR-QNH"
        aircraft := "TEST123"
        content := .single (.setPressure "TEST123" (.qnhHpa 1013))
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 104
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.setPressure "TEST123" (.qnhHpa 1013))
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleSetPressure : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleSetPressure }

def sampleResolvedSingleCancelClearance : ResolvedClearance :=
  { source :=
      { id := "CLR-CANCEL"
        aircraft := "TEST123"
        content := .single (.cancelClearance "TEST123")
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 105
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.cancelClearance "TEST123")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSingleCancelClearance : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleCancelClearance }

def sampleResolvedIncomingTowerContactForAirModifier : ResolvedClearance :=
  { source :=
      { id := "CLR-MOD-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 106
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

theorem singleClimbTo_completes_when_target_reached :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleClimbTo
        { altitude := some (.altitudeFeet 3200) }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleMaintainLevel_completes_when_matching :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleMaintainLevel
        { altitude := some (.altitudeFeet 2500) }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleMaintainSpeed_completes_when_matching :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleMaintainSpeed
        { speed := some (.inKnots 180) }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleMinimumCleanSpeed_remains_active_under_current_engine :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleMinimumCleanSpeed
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = {} ∧
      evaluation.stepResults.length = 1 := by
  native_decide

theorem singleSetPressure_completes_on_activation :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleSetPressure
        {}
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem singleCancelClearance_completes_on_activation :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleCancelClearance
        {}
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 0 := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleClimbTo :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleClimbTo]
        sampleResolvedIncomingTowerContactForAirModifier
    resolvedClearanceIds admitted.clearances = ["CLR-CLIMB", "CLR-MOD-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

end Greenfield
end CertifiedAtc
