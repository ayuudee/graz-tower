import CertifiedAtc.RouteBearingExtraction
import CertifiedAtc.GreenfieldResolution

namespace CertifiedAtc
namespace Greenfield

/--
`RouteBearingResolutionBridge` connects the first widened procedure-bearing
extraction world to the current greenfield resolved-execution boundary.

This bridge is intentionally honest and partial:

- `ClearedTo` is bridged only at the currently modeled resolved fact:
  clearance-limit fix -> clearance-limit point
- `HoldAt` is bridged for published holds
- `ClearedApproach` is bridged for non-circling approaches

`JoinCircuit` is not bridged here yet, because the extracted circuit source
still does not carry explicit join-entry support facts.
-/

def RouteBearingScopedAviationWorld.toConcreteResolutionWorld
    (world : RouteBearingScopedAviationWorld) : ConcreteResolutionWorld :=
  { fixPoints :=
      world.fixes.map (fun fix => (fix.id, fix.point))
    holdingPatterns :=
      world.holdingPatterns.map fun hold =>
        { hold := .published hold.fix
          pattern := hold.id
          fix := hold.fix }
    approaches :=
      world.approaches.map fun approach =>
        { approachType := approach.kind
          runway := approach.runway
          circlingRunway := none
          approach := approach.id } }

def RouteBearingScopedAviationWorld.toResolutionWorld
    (world : RouteBearingScopedAviationWorld) : ResolutionWorld :=
  ConcreteResolutionWorld.toResolutionWorld
    (RouteBearingScopedAviationWorld.toConcreteResolutionWorld world)

theorem RouteBearingScopedAviationWorld.mem_fixPoint_of_mem
    {world : RouteBearingScopedAviationWorld}
    {fix : ScopedFixSource}
    (hMem : fix ∈ world.fixes) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).fixPoint fix.id fix.point := by
  have hMap :
      (fix.id, fix.point) ∈
        (RouteBearingScopedAviationWorld.toConcreteResolutionWorld world).fixPoints := by
    exact List.mem_map.mpr ⟨fix, hMem, rfl⟩
  exact
    ConcreteResolutionWorld.mem_fixPoint
      (world := RouteBearingScopedAviationWorld.toConcreteResolutionWorld world)
      hMap

theorem RouteBearingScopedAviationWorld.mem_publishedHoldingPattern_of_mem
    {world : RouteBearingScopedAviationWorld}
    {hold : ScopedHoldingPatternSource}
    (hMem : hold ∈ world.holdingPatterns) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).holdingPatternFor
      (.published hold.fix) hold.id hold.fix := by
  have hMap :
      { hold := .published hold.fix, pattern := hold.id, fix := hold.fix } ∈
        (RouteBearingScopedAviationWorld.toConcreteResolutionWorld world).holdingPatterns := by
    exact List.mem_map.mpr ⟨hold, hMem, rfl⟩
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld,
    RouteBearingScopedAviationWorld.toConcreteResolutionWorld,
    ConcreteResolutionWorld.toResolutionWorld] using hMap

theorem RouteBearingScopedAviationWorld.mem_nonCirclingApproach_of_mem
    {world : RouteBearingScopedAviationWorld}
    {approach : ScopedApproachSource}
    (hMem : approach ∈ world.approaches) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).approachFor
      approach.kind approach.runway none approach.id := by
  have hMap :
      { approachType := approach.kind
        runway := approach.runway
        circlingRunway := none
        approach := approach.id } ∈
        (RouteBearingScopedAviationWorld.toConcreteResolutionWorld world).approaches := by
    exact List.mem_map.mpr ⟨approach, hMem, rfl⟩
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld,
    RouteBearingScopedAviationWorld.toConcreteResolutionWorld,
    ConcreteResolutionWorld.toResolutionWorld] using hMap

def RouteBearingInstructionResolutionReady
    (world : RouteBearingScopedAviationWorld) : AtcInstruction → Prop
  | .clearedTo _ clearanceLimit _ =>
      ∃ fix ∈ world.fixes, fix.id = clearanceLimit
  | .holdAt _ (.published fixId) _ =>
      ∃ hold ∈ world.holdingPatterns, hold.fix = fixId
  | .clearedApproach _ approachType runway none =>
      ∃ approach ∈ world.approaches,
        approach.kind = approachType ∧ approach.runway = runway
  | _ => False

def singletonResolvedClearance
    (clearance : StructuredClearance)
    (step : ResolvedStep) : ResolvedClearance :=
  { source := clearance
    steps := [step] }

theorem resolvesSingleInstructionClearance
    {world : ResolutionWorld}
    {initialState finalState : ResolutionState}
    {instruction : AtcInstruction}
    {step : ResolvedStep}
    {clearance : StructuredClearance}
    (hNormalized : normalizeConditionalEnvelope clearance = .ok clearance)
    (hContent : clearance.content = .single instruction)
    (hDomain : clearance.domain = step.domain)
    (hStep : ResolvesIndexedStep world initialState clearance.domain 0 instruction step finalState) :
    ResolvesClearance
      world
      initialState
      clearance
      (singletonResolvedClearance clearance step)
      finalState := by
  refine ⟨hNormalized, rfl, ?_⟩
  have hSteps :
      ResolvesSteps world initialState clearance.domain [(0, instruction)] [step] finalState := by
    apply ResolvesSteps.cons
    · exact hStep
    · simpa using ResolvesSteps.nil world finalState clearance.domain
  simpa [singletonResolvedClearance, structuredInstructions, contentInstructions,
    indexedSteps, enumerateFrom, hContent, hDomain] using hSteps

theorem resolvesIndexedClearedTo_of_ready
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {index : Nat}
    {target : AircraftId}
    {clearanceLimit : FixId}
    {route : Option RouteSpec}
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.clearedTo target clearanceLimit route)) :
    ∃ step,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        .route
        index
        (.clearedTo target clearanceLimit route)
        step
        state := by
  rcases hReady with ⟨fix, hMem, hFixEq⟩
  subst clearanceLimit
  refine ⟨compileResolvedStep
      index
      .route
      (.clearedTo target fix.id route)
      (.route { clearanceLimitFix := fix.id, clearanceLimitPoint := fix.point })
      (by simp [resolutionCompatible]), ?_⟩
  apply ResolvesIndexedStep.route
  exact RouteBearingScopedAviationWorld.mem_fixPoint_of_mem (world := world) hMem

theorem resolvesIndexedPublishedHoldAt_of_ready
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {index : Nat}
    {target : AircraftId}
    {fixId : FixId}
    {efc : Option String}
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.holdAt target (.published fixId) efc)) :
    ∃ step,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        .route
        index
        (.holdAt target (.published fixId) efc)
        step
        state := by
  rcases hReady with ⟨hold, hMem, hFixEq⟩
  subst fixId
  refine ⟨compileResolvedStep
      index
      .route
      (.holdAt target (.published hold.fix) efc)
      (.holding { holdingPattern := hold.id, fix := hold.fix })
      (by simp [resolutionCompatible]), ?_⟩
  apply ResolvesIndexedStep.holding
  exact
    RouteBearingScopedAviationWorld.mem_publishedHoldingPattern_of_mem
      (world := world) hMem

theorem resolvesIndexedNonCirclingClearedApproach_of_ready
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {index : Nat}
    {target : AircraftId}
    {approachType : ApproachType}
    {runway : RunwayId}
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.clearedApproach target approachType runway none)) :
    ∃ step,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        .route
        index
        (.clearedApproach target approachType runway none)
        step
        state := by
  rcases hReady with ⟨approach, hMem, hKindEq, hRunwayEq⟩
  subst approachType
  subst runway
  refine ⟨compileResolvedStep
      index
      .route
      (.clearedApproach target approach.kind approach.runway none)
      (.approach { approach := approach.id, runway := approach.runway })
      (by simp [resolutionCompatible]), ?_⟩
  apply ResolvesIndexedStep.approach
  exact
    RouteBearingScopedAviationWorld.mem_nonCirclingApproach_of_mem
      (world := world) hMem

theorem resolvesSingleClearedToClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {target : AircraftId}
    {clearanceLimit : FixId}
    {route : Option RouteSpec}
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.clearedTo target clearanceLimit route))
    (hContent :
      clearance.content = .single (.clearedTo target clearanceLimit route))
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  rcases resolvesIndexedClearedTo_of_ready (world := world) (state := initialState)
      (index := 0) hReady with ⟨step, hStep⟩
  have hStepDomain : step.domain = .route := by
    cases hStep <;> simp [compileResolvedStep, instructionDomain?]
  have hDomainStep : clearance.domain = step.domain := by
    simpa [hStepDomain] using hDomain
  have hStepAtDomain :
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance.domain
        0
        (.clearedTo target clearanceLimit route)
        step
        initialState := by
    simpa [hDomain] using hStep
  have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
    cases clearance with
    | mk id aircraft content domain issuedBy issuedAt status condition =>
        dsimp at hContent hCondition
        subst content
        subst condition
        simp [normalizeConditionalEnvelope, instructionMayBeConditional]
  refine ⟨singletonResolvedClearance clearance step, ?_⟩
  exact resolvesSingleInstructionClearance
    hNormalized
    hContent
    hDomainStep
    hStepAtDomain

theorem resolvesSinglePublishedHoldAtClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {target : AircraftId}
    {fixId : FixId}
    {efc : Option String}
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.holdAt target (.published fixId) efc))
    (hContent :
      clearance.content = .single (.holdAt target (.published fixId) efc))
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  rcases resolvesIndexedPublishedHoldAt_of_ready (world := world) (state := initialState)
      (index := 0) hReady with ⟨step, hStep⟩
  have hStepDomain : step.domain = .route := by
    cases hStep <;> simp [compileResolvedStep, instructionDomain?]
  have hDomainStep : clearance.domain = step.domain := by
    simpa [hStepDomain] using hDomain
  have hStepAtDomain :
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance.domain
        0
        (.holdAt target (.published fixId) efc)
        step
        initialState := by
    simpa [hDomain] using hStep
  have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
    cases clearance with
    | mk id aircraft content domain issuedBy issuedAt status condition =>
        dsimp at hContent hCondition
        subst content
        subst condition
        simp [normalizeConditionalEnvelope, instructionMayBeConditional]
  refine ⟨singletonResolvedClearance clearance step, ?_⟩
  exact resolvesSingleInstructionClearance
    hNormalized
    hContent
    hDomainStep
    hStepAtDomain

theorem resolvesSingleNonCirclingClearedApproachClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {target : AircraftId}
    {approachType : ApproachType}
    {runway : RunwayId}
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.clearedApproach target approachType runway none))
    (hContent :
      clearance.content = .single (.clearedApproach target approachType runway none))
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  rcases resolvesIndexedNonCirclingClearedApproach_of_ready
      (world := world) (state := initialState) (index := 0) hReady with ⟨step, hStep⟩
  have hStepDomain : step.domain = .route := by
    cases hStep <;> simp [compileResolvedStep, instructionDomain?]
  have hDomainStep : clearance.domain = step.domain := by
    simpa [hStepDomain] using hDomain
  have hStepAtDomain :
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance.domain
        0
        (.clearedApproach target approachType runway none)
        step
        initialState := by
    simpa [hDomain] using hStep
  have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
    cases clearance with
    | mk id aircraft content domain issuedBy issuedAt status condition =>
        dsimp at hContent hCondition
        subst content
        subst condition
        simp [normalizeConditionalEnvelope, instructionMayBeConditional]
  refine ⟨singletonResolvedClearance clearance step, ?_⟩
  exact resolvesSingleInstructionClearance
    hNormalized
    hContent
    hDomainStep
    hStepAtDomain

end Greenfield
end CertifiedAtc
