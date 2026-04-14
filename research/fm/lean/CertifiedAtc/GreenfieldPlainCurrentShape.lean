import CertifiedAtc.GreenfieldReachability

namespace CertifiedAtc
namespace Greenfield

/--
Reusable current-shape helper for single-step greenfield instructions that do
not require specific world-backed resolution.

These instructions still enter the resolved execution engine, but they do so as
plain resolved steps whose semantics come entirely from the current protocol
metadata plus the active-clearance engine.
-/

def compiledPlainResolvedStep
    (index : Nat)
    (fallbackDomain : ClearanceDomain)
    (instruction : AtcInstruction)
    (hPlain : instructionNeedsSpecificResolution instruction = false) :
    ResolvedStep :=
  compileResolvedStep
    index
    fallbackDomain
    instruction
    .plain
    (by simp [resolutionCompatible, hPlain])

def singletonPlainResolvedClearance
    (clearance : StructuredClearance)
    (instruction : AtcInstruction)
    (fallbackDomain : ClearanceDomain)
    (hPlain : instructionNeedsSpecificResolution instruction = false) :
    ResolvedClearance :=
  { source := clearance
    steps := [compiledPlainResolvedStep 0 fallbackDomain instruction hPlain] }

theorem resolvesSinglePlainClearance_of_ready
    {world : ResolutionWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {instruction : AtcInstruction}
    {fallbackDomain : ClearanceDomain}
    (hPlain : instructionNeedsSpecificResolution instruction = false)
    (hNormalized : normalizeConditionalEnvelope clearance = .ok clearance)
    (hContent : clearance.content = .single instruction)
    (hDomain : clearance.domain = fallbackDomain) :
    ResolvesClearance
      world
      initialState
      clearance
      (singletonPlainResolvedClearance clearance instruction fallbackDomain hPlain)
      initialState := by
  refine ⟨hNormalized, rfl, ?_⟩
  have hStep :
      ResolvesIndexedStep
        world
        initialState
        fallbackDomain
        0
        instruction
        (compiledPlainResolvedStep 0 fallbackDomain instruction hPlain)
        initialState := by
    simpa [compiledPlainResolvedStep] using
      (ResolvesIndexedStep.plain world fallbackDomain 0 instruction initialState hPlain)
  have hSteps :
      ResolvesSteps
        world
        initialState
        fallbackDomain
        [(0, instruction)]
        [compiledPlainResolvedStep 0 fallbackDomain instruction hPlain]
        initialState := by
    apply ResolvesSteps.cons
    · exact hStep
    · simpa using ResolvesSteps.nil world initialState fallbackDomain
  simpa [singletonPlainResolvedClearance, structuredInstructions, contentInstructions,
    indexedSteps, enumerateFrom, hContent, hDomain] using hSteps

theorem plainCurrentShapeAdmissionSoundnessTheorem
    {world : ResolutionWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {instruction : AtcInstruction}
    {fallbackDomain : ClearanceDomain}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hPlain : instructionNeedsSpecificResolution instruction = false)
    (hNormalized : normalizeConditionalEnvelope clearance = .ok clearance)
    (hContent : clearance.content = .single instruction)
    (hDomain : clearance.domain = fallbackDomain) :
    ∃ resolved,
      ResolvesClearance
        world
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  let resolved :=
    singletonPlainResolvedClearance clearance instruction fallbackDomain hPlain
  have hResolve :
      ResolvesClearance
        world
        initialState
        clearance
        resolved
        initialState :=
    resolvesSinglePlainClearance_of_ready
      (world := world)
      (initialState := initialState)
      (clearance := clearance)
      (instruction := instruction)
      (fallbackDomain := fallbackDomain)
      hPlain
      hNormalized
      hContent
      hDomain
  have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
    simpa [resolved] using hFresh
  refine ⟨resolved, hResolve, ?_⟩
  exact ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve

end Greenfield
end CertifiedAtc
