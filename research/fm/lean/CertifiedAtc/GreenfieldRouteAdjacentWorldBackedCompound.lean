import CertifiedAtc.GreenfieldRouteAdjacentWorldBackedCurrentShape
import CertifiedAtc.GreenfieldRouteBearingCompound

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRouteAdjacentWorldBackedCompound` widens the route-adjacent branch
through the first honest narrow compound layer on the current approach/circuit
model.

Scope:

- one leading world-backed route-adjacent primary
- zero or more immediate adjunct tails already understood by the current
  greenfield engine

This is the world-backed analogue of the earlier current-shape Phase B
compound layer, but now the primary route-adjacent step resolves against
concrete current-approach / current-circuit facts and may update the
resolution state before the adjunct tail is interpreted.
-/

def GreenfieldRouteAdjacentWorldBackedCompoundReady
    (world : RouteBearingScopedAviationWorld)
    (initialState : ResolutionState)
    (primary : AtcInstruction)
    (tail : List AtcInstruction) : Prop :=
  GreenfieldRouteAdjacentWorldBackedReady world initialState primary ∧
    ∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction

inductive GreenfieldRouteAdjacentWorldBackedCompoundIssuable
    (world : RouteBearingScopedAviationWorld)
    (initialState : ResolutionState) :
    StructuredClearance → Prop
  | continueApproach
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {target : AircraftId}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps : content.steps = .continueApproach target :: tail)
      (hReady :
        GreenfieldRouteAdjacentWorldBackedCompoundReady
          world
          initialState
          (.continueApproach target)
          tail)
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      GreenfieldRouteAdjacentWorldBackedCompoundIssuable world initialState clearance
  | extendDownwind
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {target : AircraftId}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps : content.steps = .extendDownwind target :: tail)
      (hReady :
        GreenfieldRouteAdjacentWorldBackedCompoundReady
          world
          initialState
          (.extendDownwind target)
          tail)
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      GreenfieldRouteAdjacentWorldBackedCompoundIssuable world initialState clearance
  | orbit
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {target : AircraftId}
      {direction : OrbitDirection}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps : content.steps = .orbit target direction :: tail)
      (hReady :
        GreenfieldRouteAdjacentWorldBackedCompoundReady
          world
          initialState
          (.orbit target direction)
          tail)
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none) :
      GreenfieldRouteAdjacentWorldBackedCompoundIssuable world initialState clearance

theorem resolvesIndexedRouteAdjacentWorldBackedPrimary_of_ready
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {primary : AtcInstruction}
    (hReady : GreenfieldRouteAdjacentWorldBackedReady world state primary) :
    ∃ finalState primaryStep,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        (routeAdjacentWorldBackedFallbackDomain primary)
        0
        primary
        primaryStep
        finalState := by
  exact resolvesIndexedRouteAdjacentWorldBackedStep_of_ready (world := world) (state := state) hReady

theorem resolvesRouteAdjacentWorldBackedCompoundClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {primary : AtcInstruction}
    {tail : List AtcInstruction}
    (hContent : clearance.content = .compound content)
    (hSteps : content.steps = primary :: tail)
    (hReady :
      GreenfieldRouteAdjacentWorldBackedCompoundReady world initialState primary tail)
    (hDomain : clearance.domain = routeAdjacentWorldBackedFallbackDomain primary)
    (hCondition : clearance.condition = none) :
    ∃ finalState, ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState := by
  rcases hReady with ⟨hPrimaryReady, hTailReady⟩
  have hTailWrapped :
      anyWrappedConditionalStep tail = false :=
    anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts hTailReady
  have hWrapped : anyWrappedConditionalStep (primary :: tail) = false := by
    cases primary <;> simp [anyWrappedConditionalStep, hTailWrapped, GreenfieldRouteAdjacentWorldBackedReady] at hPrimaryReady ⊢
  rcases resolvesIndexedRouteAdjacentWorldBackedPrimary_of_ready
      (world := world)
      (state := initialState)
      (primary := primary)
      hPrimaryReady with
      ⟨primaryFinalState, primaryStep, hPrimaryStep⟩
  rcases resolvesRouteBearingImmediateAdjunctTail_of_ready
      (world := world)
      (state := primaryFinalState)
      (fallbackDomain := routeAdjacentWorldBackedFallbackDomain primary)
      (start := 1)
      (tail := tail)
      hTailReady with
      ⟨resolvedTail, hResolvedTail⟩
  refine ⟨primaryFinalState, { source := clearance, steps := primaryStep :: resolvedTail }, ?_⟩
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
          primaryFinalState := by
      simpa [hDomain] using hPrimaryStep
    have hResolvedTail' :
        ResolvesSteps
          (RouteBearingScopedAviationWorld.toResolutionWorld world)
          primaryFinalState
          clearance.domain
          (enumerateFrom 1 tail)
          resolvedTail
          primaryFinalState := by
      simpa [hDomain] using hResolvedTail
    have hIndexed :
        indexedSteps (structuredInstructions clearance) =
          (0, primary) :: enumerateFrom 1 tail := by
      simp [hContent, hSteps, structuredInstructions, contentInstructions, indexedSteps, enumerateFrom]
    simpa [hIndexed] using
      ResolvesSteps.cons
        (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
        (state := initialState)
        (nextState := primaryFinalState)
        (finalState := primaryFinalState)
        (fallbackDomain := clearance.domain)
        (index := 0)
        (instruction := primary)
        (step := primaryStep)
        (tail := enumerateFrom 1 tail)
        (resolvedTail := resolvedTail)
        hPrimaryStep'
        hResolvedTail'

theorem GreenfieldRouteAdjacentWorldBackedCompoundAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldRouteAdjacentWorldBackedCompoundIssuable world initialState clearance) :
    ∃ finalState, ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        finalState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  cases hIssuable with
  | continueApproach =>
      rename_i content target tail hContent hSteps hReady hDomain hCondition
      rcases resolvesRouteAdjacentWorldBackedCompoundClearance_of_ready
          (world := world)
          (initialState := initialState)
          (clearance := clearance)
          (content := content)
          (primary := .continueApproach target)
          (tail := tail)
          hContent
          hSteps
          hReady
          (by simpa [routeAdjacentWorldBackedFallbackDomain] using hDomain)
          hCondition with
          ⟨finalState, resolved, hResolve⟩
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [hResolve.sourceEq] using hFresh
      exact ⟨finalState, resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  | extendDownwind =>
      rename_i content target tail hContent hSteps hReady hDomain hCondition
      rcases resolvesRouteAdjacentWorldBackedCompoundClearance_of_ready
          (world := world)
          (initialState := initialState)
          (clearance := clearance)
          (content := content)
          (primary := .extendDownwind target)
          (tail := tail)
          hContent
          hSteps
          hReady
          (by simpa [routeAdjacentWorldBackedFallbackDomain] using hDomain)
          hCondition with
          ⟨finalState, resolved, hResolve⟩
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [hResolve.sourceEq] using hFresh
      exact ⟨finalState, resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  | orbit =>
      rename_i content target direction tail hContent hSteps hReady hDomain hCondition
      rcases resolvesRouteAdjacentWorldBackedCompoundClearance_of_ready
          (world := world)
          (initialState := initialState)
          (clearance := clearance)
          (content := content)
          (primary := .orbit target direction)
          (tail := tail)
          hContent
          hSteps
          hReady
          (by simpa [routeAdjacentWorldBackedFallbackDomain] using hDomain)
          hCondition with
          ⟨finalState, resolved, hResolve⟩
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [hResolve.sourceEq] using hFresh
      exact ⟨finalState, resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

def sampleResolvedWorldBackedContinueApproachEstablished : ResolvedClearance :=
  { source :=
      { id := "CLR-CONT-APP-WB-EST"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .continueApproach "TEST123"
              , .maintainAltitudeUntilEstablished
                  "TEST123"
                  (.altitudeFeet 2000)
                  .localiser ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 70
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .route
          (.continueApproach "TEST123")
          (.continueApproach
            { approach := "ILS27"
              waypointPoints := ["IAF-27", "FAF-27", "RWY27"]
              thresholdPoint := "RWY27" })
          (by native_decide)
      , compileResolvedStep
          1
          .route
          (.maintainAltitudeUntilEstablished
            "TEST123"
            (.altitudeFeet 2000)
            .localiser)
          .plain
          rfl ] }

def sampleManagedResolvedWorldBackedContinueApproachEstablished : ManagedResolvedClearance :=
  { resolved := sampleResolvedWorldBackedContinueApproachEstablished }

def sampleResolvedWorldBackedExtendDownwindContact : ResolvedClearance :=
  { source :=
      { id := "CLR-EXT-DW-WB-CONTACT"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .extendDownwind "TEST123"
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 71
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .runway
          (.extendDownwind "TEST123")
          (.extendDownwind
            { circuit := "CIRCUIT-27-LH"
              extendedPathPoints := ["DOWNWIND", "DOWNWIND-EXT"]
              offRampPoints := [["DOWNWIND-EXT", "BASE"]] })
          (by native_decide)
      , compileResolvedStep
          1
          .runway
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by native_decide) ] }

def sampleManagedResolvedWorldBackedExtendDownwindContactSuppressed : ManagedResolvedClearance :=
  { resolved := sampleResolvedWorldBackedExtendDownwindContact
    suppressedDomains := UniqueSet.singleton .frequency }

def sampleResolvedWorldBackedOrbitContact : ResolvedClearance :=
  { source :=
      { id := "CLR-ORBIT-WB-CONTACT"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .orbit "TEST123" .left
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 72
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .runway
          (.orbit "TEST123" .left)
          (.orbit
            { circuit := "CIRCUIT-27-LH"
              orbitPoint := "DOWNWIND"
              direction := .left
              loopPoints := ["DOWNWIND", "ORBIT-NORTH", "ORBIT-SOUTH", "DOWNWIND"] })
          (by native_decide)
      , compileResolvedStep
          1
          .runway
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by native_decide) ] }

def sampleManagedResolvedWorldBackedOrbitContactSuppressed : ManagedResolvedClearance :=
  { resolved := sampleResolvedWorldBackedOrbitContact
    suppressedDomains := UniqueSet.singleton .frequency }

def sampleWorldBackedContinueApproachEstablishedObservation : CompletionObservation :=
  { establishedApproachComponents := UniqueSet.singleton .localiser }

theorem worldBackedContinueApproachCompound_stays_active_after_adjunct_completion :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedWorldBackedContinueApproachEstablished
        sampleWorldBackedContinueApproachEstablishedObservation
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem suppressedWorldBackedExtendDownwindContact_completes_on_current_engine :
    (evaluateResolvedCompletion
      sampleManagedResolvedWorldBackedExtendDownwindContactSuppressed
      {}).updated.status = .completed := by
  native_decide

theorem suppressedWorldBackedOrbitContact_completes_on_current_engine :
    (evaluateResolvedCompletion
      sampleManagedResolvedWorldBackedOrbitContactSuppressed
      {}).updated.status = .completed := by
  native_decide

end Greenfield
end CertifiedAtc
