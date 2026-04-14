import CertifiedAtc.RouteBearingResolutionBridge
import CertifiedAtc.GreenfieldPlainCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape closure for the current Kotlin airspace-clearance family.

This is intentionally a small single-step slice over the current runtime
surface:

- `RemainOutsideControlledAirspace`
- `ClearedToEnterControlZone`
- `SpecialVfrClearance`

The present Kotlin/Lean model does not yet resolve concrete airspace-entry
paths or zone-boundary crossing observations for these families, so this
module keeps the semantics honest:

- they resolve as plain route-domain steps
- `ClearedToEnterControlZone` and `SpecialVfrClearance` inherit their current
  persistent runtime status
- `RemainOutsideControlledAirspace` remains active because completion is
  intentionally unmodeled
- authority is conservative and type-level: `airspaceVolume / airspaceTransit`
-/

def currentShapeAirspaceAuthorityGrant : CompileAuthorityGrantView :=
  { entityType := .airspaceVolume
    operation := .airspaceTransit }

def GreenfieldAirspaceCurrentShapeInstruction : AtcInstruction → Prop
  | .remainOutsideControlledAirspace _ _ => True
  | .clearedToEnterControlZone _ _ _ _ => True
  | .specialVfrClearance _ _ _ _ => True
  | _ => False

def greenfieldAirspaceInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | .remainOutsideControlledAirspace _ _ => some currentShapeAirspaceAuthorityGrant
  | .clearedToEnterControlZone _ _ _ _ => some currentShapeAirspaceAuthorityGrant
  | .specialVfrClearance _ _ _ _ => some currentShapeAirspaceAuthorityGrant
  | _ => none

def greenfieldAirspaceInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match greenfieldAirspaceInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def greenfieldAirspaceInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      greenfieldAirspaceInstructionIssuerAuthorized view controller instruction &&
        greenfieldAirspaceInstructionsIssuerAuthorized view controller tail

@[simp] theorem greenfieldAirspaceInstructionRequiredAuthorityGrant?_remainOutside
    (target : AircraftId)
    (airspace : AirspaceVolumeId) :
    greenfieldAirspaceInstructionRequiredAuthorityGrant?
        (.remainOutsideControlledAirspace target airspace) =
      some currentShapeAirspaceAuthorityGrant := by
  simp [greenfieldAirspaceInstructionRequiredAuthorityGrant?,
    currentShapeAirspaceAuthorityGrant]

@[simp] theorem greenfieldAirspaceInstructionRequiredAuthorityGrant?_enterZone
    (target : AircraftId)
    (airspace : AirspaceVolumeId)
    (route : Option RouteSpec)
    (levelRestriction : Option Level) :
    greenfieldAirspaceInstructionRequiredAuthorityGrant?
        (.clearedToEnterControlZone target airspace route levelRestriction) =
      some currentShapeAirspaceAuthorityGrant := by
  simp [greenfieldAirspaceInstructionRequiredAuthorityGrant?,
    currentShapeAirspaceAuthorityGrant]

@[simp] theorem greenfieldAirspaceInstructionRequiredAuthorityGrant?_specialVfr
    (target : AircraftId)
    (airspace : AirspaceVolumeId)
    (route : Option RouteSpec)
    (levelRestriction : Option Level) :
    greenfieldAirspaceInstructionRequiredAuthorityGrant?
        (.specialVfrClearance target airspace route levelRestriction) =
      some currentShapeAirspaceAuthorityGrant := by
  simp [greenfieldAirspaceInstructionRequiredAuthorityGrant?,
    currentShapeAirspaceAuthorityGrant]

theorem greenfieldAirspaceInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : RouteBearingScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    (hWf : RouteBearingExtractionWellFormed world)
    (hAirspace : GreenfieldAirspaceCurrentShapeInstruction instruction)
    (hGrant :
      WorldControllerHasGrant
        world.toScopedAviationWorld
        controller
        currentShapeAirspaceAuthorityGrant) :
    greenfieldAirspaceInstructionIssuerAuthorized
      (extractRouteBearingCompileView world)
      controller
      instruction = true := by
  cases instruction <;>
    simp [GreenfieldAirspaceCurrentShapeInstruction] at hAirspace ⊢
  all_goals
    change
      controllerHasAuthorityGrant
        (extractCompileView world.toScopedAviationWorld)
        controller
        currentShapeAirspaceAuthorityGrant = true
    exact
      controllerHasAuthorityGrant_of_worldControllerHasGrant
        hWf.baseWellFormed
        hGrant

theorem greenfieldAirspaceInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : RouteBearingScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      RouteBearingExtractionWellFormed world →
      (∀ instruction ∈ steps, GreenfieldAirspaceCurrentShapeInstruction instruction) →
      WorldControllerHasGrant
        world.toScopedAviationWorld
        controller
        currentShapeAirspaceAuthorityGrant →
        greenfieldAirspaceInstructionsIssuerAuthorized
          (extractRouteBearingCompileView world)
          controller
          steps = true := by
  intro world controller steps hWf hAirspace hGrant
  induction steps with
  | nil =>
      simp [greenfieldAirspaceInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHead : GreenfieldAirspaceCurrentShapeInstruction head := hAirspace head (by simp)
      have hTail :
          ∀ instruction ∈ tail, GreenfieldAirspaceCurrentShapeInstruction instruction := by
        intro instruction hMem
        exact hAirspace instruction (by simp [hMem])
      have hHeadOk :
          greenfieldAirspaceInstructionIssuerAuthorized
            (extractRouteBearingCompileView world)
            controller
            head = true :=
        greenfieldAirspaceInstructionIssuerAuthorized_eq_true_of_worldAuthorized
          (world := world)
          (controller := controller)
          (instruction := head)
          hWf
          hHead
          hGrant
      simp [greenfieldAirspaceInstructionsIssuerAuthorized, hHeadOk, ih hTail]

inductive GreenfieldAirspaceCurrentShapeIssuable :
    StructuredClearance → Prop
  | remainOutside
      {clearance : StructuredClearance}
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      (hContent : clearance.content =
        .single (.remainOutsideControlledAirspace target airspace))
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      GreenfieldAirspaceCurrentShapeIssuable clearance
  | enterZone
      {clearance : StructuredClearance}
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      {route : Option RouteSpec}
      {levelRestriction : Option Level}
      (hContent : clearance.content =
        .single (.clearedToEnterControlZone target airspace route levelRestriction))
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      GreenfieldAirspaceCurrentShapeIssuable clearance
  | specialVfr
      {clearance : StructuredClearance}
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      {route : Option RouteSpec}
      {levelRestriction : Option Level}
      (hContent : clearance.content =
        .single (.specialVfrClearance target airspace route levelRestriction))
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      GreenfieldAirspaceCurrentShapeIssuable clearance

theorem GreenfieldAirspaceCurrentShapeAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldAirspaceCurrentShapeIssuable clearance) :
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
  | remainOutside hContent hDomain hCondition =>
      exact
        plainCurrentShapeAdmissionSoundnessTheorem
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          (instruction := .remainOutsideControlledAirspace _ _)
          (fallbackDomain := .route)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition])
          hContent
          hDomain
  | enterZone hContent hDomain hCondition =>
      exact
        plainCurrentShapeAdmissionSoundnessTheorem
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          (instruction := .clearedToEnterControlZone _ _ _ _)
          (fallbackDomain := .route)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition])
          hContent
          hDomain
  | specialVfr hContent hDomain hCondition =>
      exact
        plainCurrentShapeAdmissionSoundnessTheorem
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          (instruction := .specialVfrClearance _ _ _ _)
          (fallbackDomain := .route)
          hReach
          hFresh
          (by simp [instructionNeedsSpecificResolution])
          (by simp [normalizeConditionalEnvelope, hContent, hCondition])
          hContent
          hDomain

theorem GreenfieldAirspaceCurrentShapeIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldAirspaceCurrentShapeIssuable clearance)
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
    | remainOutside hContent hDomain hCondition =>
        refine greenfieldAirspaceInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
            (world := world)
            (controller := clearance.issuedBy)
            (steps := structuredInstructions clearance)
            hWf
            (by
              simp [structuredInstructions, contentInstructions, hContent,
                GreenfieldAirspaceCurrentShapeInstruction])
            hAuthority
    | enterZone hContent hDomain hCondition =>
        refine greenfieldAirspaceInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
            (world := world)
            (controller := clearance.issuedBy)
            (steps := structuredInstructions clearance)
            hWf
            (by
              simp [structuredInstructions, contentInstructions, hContent,
                GreenfieldAirspaceCurrentShapeInstruction])
            hAuthority
    | specialVfr hContent hDomain hCondition =>
        refine greenfieldAirspaceInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
            (world := world)
            (controller := clearance.issuedBy)
            (steps := structuredInstructions clearance)
            hWf
            (by
              simp [structuredInstructions, contentInstructions, hContent,
                GreenfieldAirspaceCurrentShapeInstruction])
            hAuthority
  rcases GreenfieldAirspaceCurrentShapeAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleResolvedRemainOutsideControlledAirspace : ResolvedClearance :=
  { source :=
      { id := "CLR-ROCA"
        aircraft := "TEST123"
        content := .single (.remainOutsideControlledAirspace "TEST123" "CTR-1")
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 60
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.remainOutsideControlledAirspace "TEST123" "CTR-1")
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedRemainOutsideControlledAirspace : ManagedResolvedClearance :=
  { resolved := sampleResolvedRemainOutsideControlledAirspace }

def sampleResolvedClearedToEnterControlZone : ResolvedClearance :=
  { source :=
      { id := "CLR-ENTER-CTR"
        aircraft := "TEST123"
        content := .single (.clearedToEnterControlZone "TEST123" "CTR-1" none none)
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 61
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.clearedToEnterControlZone "TEST123" "CTR-1" none none)
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedClearedToEnterControlZone : ManagedResolvedClearance :=
  { resolved := sampleResolvedClearedToEnterControlZone }

def sampleResolvedSpecialVfrClearance : ResolvedClearance :=
  { source :=
      { id := "CLR-SVFR"
        aircraft := "TEST123"
        content := .single (.specialVfrClearance "TEST123" "CTR-1" none none)
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 62
        status := .active
        condition := none }
    steps :=
      [ compiledPlainResolvedStep
          0
          .route
          (.specialVfrClearance "TEST123" "CTR-1" none none)
          (by simp [instructionNeedsSpecificResolution]) ] }

def sampleManagedResolvedSpecialVfrClearance : ManagedResolvedClearance :=
  { resolved := sampleResolvedSpecialVfrClearance }

def sampleResolvedIncomingTowerContactForAirspace : ResolvedClearance :=
  { source :=
      { id := "CLR-AIRSPACE-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .tower (some "118.500"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 63
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

theorem singleRemainOutsideControlledAirspace_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedRemainOutsideControlledAirspace
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem singleClearedToEnterControlZone_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedClearedToEnterControlZone
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem singleSpecialVfrClearance_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSpecialVfrClearance
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem incomingControlZoneClearance_fullySupersedes_singleRemainOutsideControlledAirspace :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedRemainOutsideControlledAirspace]
        sampleResolvedClearedToEnterControlZone
    resolvedClearanceIds admitted.clearances = ["CLR-ENTER-CTR"] ∧
      resolvedClearanceIds admitted.fullySuperseded = ["CLR-ROCA"] := by
  native_decide

theorem incomingSpecialVfrClearance_fullySupersedes_singleClearedToEnterControlZone :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedClearedToEnterControlZone]
        sampleResolvedSpecialVfrClearance
    resolvedClearanceIds admitted.clearances = ["CLR-SVFR"] ∧
      resolvedClearanceIds admitted.fullySuperseded = ["CLR-ENTER-CTR"] := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleSpecialVfrClearance :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSpecialVfrClearance]
        sampleResolvedIncomingTowerContactForAirspace
    resolvedClearanceIds admitted.clearances = ["CLR-SVFR", "CLR-AIRSPACE-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

end Greenfield
end CertifiedAtc
