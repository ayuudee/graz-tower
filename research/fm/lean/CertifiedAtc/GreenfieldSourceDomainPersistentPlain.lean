import CertifiedAtc.GreenfieldRouteBearingCompound

namespace CertifiedAtc
namespace Greenfield

/--
Reusable helper for current-shape instructions whose domain is supplied by the
source clearance rather than by instruction metadata.

This matches the current Kotlin/Lean treatment of families such as
`ExtendDownwind` and `Orbit`:

- plain resolved step
- persistent timing / completion
- no instruction-layer domain
- resolved execution domain supplied by the source clearance

The helper below widens that pattern to a narrow compound surface:
one such primary step plus zero or more already-understood immediate adjuncts.
-/

inductive SourceDomainSuppliedPersistentPlainInstruction :
    AtcInstruction → Prop
  | extendDownwind {target : AircraftId} :
      SourceDomainSuppliedPersistentPlainInstruction (.extendDownwind target)
  | orbit {target : AircraftId} {direction : OrbitDirection} :
      SourceDomainSuppliedPersistentPlainInstruction (.orbit target direction)

theorem sourceDomainSuppliedPersistentPlainInstruction_needsNoSpecificResolution
    {instruction : AtcInstruction}
    (hPrimary : SourceDomainSuppliedPersistentPlainInstruction instruction) :
    instructionNeedsSpecificResolution instruction = false := by
  cases hPrimary <;> simp [instructionNeedsSpecificResolution]

theorem sourceDomainSuppliedPersistentPlainInstruction_persistentTiming
    {instruction : AtcInstruction}
    (hPrimary : SourceDomainSuppliedPersistentPlainInstruction instruction) :
    instructionTiming? instruction = some .persistent := by
  cases hPrimary <;> simp [instructionTiming?]

theorem sourceDomainSuppliedPersistentPlainInstruction_domainless
    {instruction : AtcInstruction}
    (hPrimary : SourceDomainSuppliedPersistentPlainInstruction instruction) :
    instructionDomain? instruction = none := by
  cases hPrimary <;> simp [instructionDomain?]

theorem sourceDomainSuppliedPersistentPlainInstruction_persistentCompletion
    {instruction : AtcInstruction}
    (hPrimary : SourceDomainSuppliedPersistentPlainInstruction instruction) :
    instructionCompletionCategory? instruction = some .persistent := by
  cases hPrimary <;> simp [instructionCompletionCategory?]

theorem sourceDomainSuppliedPersistentPlainInstruction_not_wrappedConditional
    {instruction : AtcInstruction}
    (hPrimary : SourceDomainSuppliedPersistentPlainInstruction instruction) :
    anyWrappedConditionalStep [instruction] = false := by
  cases hPrimary <;> simp [anyWrappedConditionalStep]

def SourceDomainSuppliedPersistentPlainCompoundReady
    (world : RouteBearingScopedAviationWorld)
    (primary : AtcInstruction)
    (tail : List AtcInstruction) : Prop :=
  SourceDomainSuppliedPersistentPlainInstruction primary ∧
    ∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction

theorem anyWrappedConditionalStep_false_of_sourceDomainSuppliedPersistentPlainCompoundReady
    {world : RouteBearingScopedAviationWorld}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hReady : SourceDomainSuppliedPersistentPlainCompoundReady world primary tail) :
    anyWrappedConditionalStep (primary :: tail) = false := by
  rcases hReady with ⟨hPrimary, hTail⟩
  have hPrimaryClear :
      anyWrappedConditionalStep [primary] = false :=
    sourceDomainSuppliedPersistentPlainInstruction_not_wrappedConditional hPrimary
  have hTailClear :
      anyWrappedConditionalStep tail = false :=
    anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts hTail
  cases hPrimary <;>
    simp [anyWrappedConditionalStep] at hPrimaryClear ⊢
  all_goals exact hTailClear

theorem resolvesSourceDomainSuppliedPersistentPlainCompoundClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    {sourceDomain : ClearanceDomain}
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hReady : SourceDomainSuppliedPersistentPlainCompoundReady world primary tail)
    (hDomain : clearance.domain = sourceDomain)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  have hWrapped :
      anyWrappedConditionalStep (primary :: tail) = false :=
    anyWrappedConditionalStep_false_of_sourceDomainSuppliedPersistentPlainCompoundReady hReady
  rcases hReady with ⟨hPrimary, hTailReady⟩
  rcases resolvesIndexedPlainInstruction
      (world := world)
      (state := initialState)
      (fallbackDomain := sourceDomain)
      (index := 0)
      (instruction := primary)
      (sourceDomainSuppliedPersistentPlainInstruction_needsNoSpecificResolution hPrimary) with
      ⟨primaryStep, hPrimaryStep⟩
  rcases resolvesRouteBearingImmediateAdjunctTail_of_ready
      (world := world)
      (state := initialState)
      (fallbackDomain := sourceDomain)
      (start := 1)
      (tail := tail)
      hTailReady with
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

theorem SourceDomainSuppliedPersistentPlainCompoundAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    {sourceDomain : ClearanceDomain}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hReady : SourceDomainSuppliedPersistentPlainCompoundReady world primary tail)
    (hDomain : clearance.domain = sourceDomain)
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
  rcases resolvesSourceDomainSuppliedPersistentPlainCompoundClearance_of_ready
      (world := world)
      (initialState := initialState)
      (clearance := clearance)
      (content := content)
      (primary := primary)
      (tail := tail)
      (sourceDomain := sourceDomain)
      hContent
      hSteps
      hReady
      hDomain
      hCondition with
      ⟨resolved, hResolve⟩
  have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
    simpa [hResolve.sourceEq] using hFresh
  refine ⟨resolved, hResolve, ?_⟩
  exact ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve

end Greenfield
end CertifiedAtc
