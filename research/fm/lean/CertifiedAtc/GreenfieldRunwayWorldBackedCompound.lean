import CertifiedAtc.GreenfieldRunwayWorldBackedCurrentShape
import CertifiedAtc.GreenfieldRunwayCompound

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRunwayWorldBackedCompound` widens the new world-backed runway
single-step boundary through the first honest narrow compound slice.

The scope is intentionally small:

- one leading world-backed runway-operation primary
- zero or more immediate adjunct tails already understood by the current engine

This keeps the runtime/Lean story aligned: the primary step now resolves
against a concrete runway path/threshold, while the adjunct tails remain on the
existing immediate-adjunct boundary.
-/

def GreenfieldRunwayWorldBackedCompoundReady
    (world : RouteBearingScopedAviationWorld)
    (initialState : ResolutionState)
    (primary : AtcInstruction)
    (tail : List AtcInstruction) : Prop :=
  GreenfieldRunwayWorldBackedPrimaryReady world initialState primary ∧
    ∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction

theorem anyWrappedConditionalStep_false_of_runwayWorldBackedCompoundReady
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hReady :
      GreenfieldRunwayWorldBackedCompoundReady
        world
        initialState
        primary
        tail) :
    anyWrappedConditionalStep (primary :: tail) = false := by
  rcases hReady with ⟨hPrimary, hTail⟩
  have hPrimaryClear :
      anyWrappedConditionalStep [primary] = false :=
    greenfieldRunwayWorldBackedPrimary_not_wrappedConditional hPrimary
  have hTailClear :
      anyWrappedConditionalStep tail = false :=
    anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts hTail
  cases primary <;>
    simp [anyWrappedConditionalStep] at hPrimaryClear ⊢
  all_goals exact hTailClear

inductive GreenfieldRunwayWorldBackedCompoundIssuable
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
      (hReady :
        GreenfieldRunwayWorldBackedCompoundReady
          world
          initialState
          primary
          tail)
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      GreenfieldRunwayWorldBackedCompoundIssuable world initialState clearance

theorem resolvesWorldBackedRunwayCompoundClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hReady :
      GreenfieldRunwayWorldBackedCompoundReady
        world
        initialState
        primary
        tail)
    (hDomain : clearance.domain = .runway)
    (hCondition : clearance.condition = none) :
    ∃ finalState, ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState := by
  have hWrapped :
      anyWrappedConditionalStep (primary :: tail) = false :=
    anyWrappedConditionalStep_false_of_runwayWorldBackedCompoundReady hReady
  rcases resolvesIndexedWorldBackedRunwayInstruction_of_ready
      (world := world)
      (state := initialState)
      (index := 0)
      (instruction := primary)
      hReady.1 with
      ⟨primaryStep, nextState, hPrimaryStep⟩
  rcases resolvesRouteBearingImmediateAdjunctTail_of_ready
      (world := world)
      (state := nextState)
      (fallbackDomain := .runway)
      (start := 1)
      (tail := tail)
      hReady.2 with
      ⟨resolvedTail, hResolvedTail⟩
  refine ⟨nextState, { source := clearance, steps := primaryStep :: resolvedTail }, ?_⟩
  refine ⟨?_, rfl, ?_⟩
  · simp [normalizeConditionalEnvelope, hContent, hCondition, hSteps, hWrapped]
  · have hResolvedTail' :
        ResolvesSteps
          (RouteBearingScopedAviationWorld.toResolutionWorld world)
          nextState
          clearance.domain
          (enumerateFrom 1 tail)
          resolvedTail
          nextState := by
      simpa [hDomain] using hResolvedTail
    have hPrimaryStep' :
        ResolvesIndexedStep
          (RouteBearingScopedAviationWorld.toResolutionWorld world)
          initialState
          clearance.domain
          0
          primary
          primaryStep
          nextState := by
      simpa [hDomain] using hPrimaryStep
    have hIndexed :
        indexedSteps (structuredInstructions clearance) =
          (0, primary) :: enumerateFrom 1 tail := by
      simp [hContent, hSteps, structuredInstructions, contentInstructions,
        indexedSteps, enumerateFrom]
    simpa [hIndexed] using
      ResolvesSteps.cons
        (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
        (state := initialState)
        (nextState := nextState)
        (finalState := nextState)
        (fallbackDomain := clearance.domain)
        (index := 0)
        (instruction := primary)
        (step := primaryStep)
        (tail := enumerateFrom 1 tail)
        (resolvedTail := resolvedTail)
        hPrimaryStep'
        hResolvedTail'

abbrev GreenfieldRunwayWorldBackedCompoundWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  RunwayCompoundWorldAuthorized world controller steps

theorem GreenfieldRunwayWorldBackedCompoundAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldRunwayWorldBackedCompoundIssuable world initialState clearance) :
    ∃ finalState, ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable
  case mk content primary tail hContent hSteps hReady hDomain hCondition =>
    rcases resolvesWorldBackedRunwayCompoundClearance_of_ready
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
        ⟨finalState, resolved, hResolve⟩
    have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
      simpa [hResolve.sourceEq] using hFresh
    exact ⟨finalState, resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

theorem GreenfieldRunwayWorldBackedCompoundAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldRunwayWorldBackedCompoundIssuable world initialState clearance)
    (hAuthority :
      GreenfieldRunwayWorldBackedCompoundWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ finalState, ∃ resolved,
      runwayCompoundInstructionsIssuerAuthorized
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
  rcases GreenfieldRunwayWorldBackedCompoundAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨finalState, resolved, hResolve, hReachable⟩
  exact ⟨finalState, resolved, hAuthorized, hResolve, hReachable⟩

end Greenfield
end CertifiedAtc
