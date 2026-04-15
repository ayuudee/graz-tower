import CertifiedAtc.GreenfieldAirspaceWorldBackedCurrentShape
import CertifiedAtc.GreenfieldAirspaceExpandedCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldAirspaceWorldBackedCompound` widens the new world-backed airspace
single-step boundary through the first honest narrow compound slice.

The scope is intentionally small:

- one leading world-backed airspace primary
- zero or more immediate adjunct tails already understood by the current
  greenfield engine

This makes the current engine consequences theorem-bearing on top of concrete
airspace-volume resolution, instead of leaving compounds only on the older
plain/current-shape airspace boundary.
-/

def GreenfieldAirspaceWorldBackedCompoundReady
    (world : RouteBearingScopedAviationWorld)
    (primary : AtcInstruction)
    (tail : List AtcInstruction) : Prop :=
  GreenfieldAirspaceWorldBackedReady world primary ∧
    ∀ instruction ∈ tail, RouteBearingImmediateAdjunctReady world instruction

inductive GreenfieldAirspaceWorldBackedCompoundIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | remainOutside
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps :
        content.steps =
          .remainOutsideControlledAirspace target airspace :: tail)
      (hReady :
        GreenfieldAirspaceWorldBackedCompoundReady
          world
          (.remainOutsideControlledAirspace target airspace)
          tail)
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      GreenfieldAirspaceWorldBackedCompoundIssuable world clearance
  | enterZone
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      {route : Option RouteSpec}
      {levelRestriction : Option Level}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps :
        content.steps =
          .clearedToEnterControlZone target airspace route levelRestriction :: tail)
      (hReady :
        GreenfieldAirspaceWorldBackedCompoundReady
          world
          (.clearedToEnterControlZone target airspace route levelRestriction)
          tail)
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      GreenfieldAirspaceWorldBackedCompoundIssuable world clearance
  | specialVfr
      {clearance : StructuredClearance}
      {content : CompoundClearanceContent}
      {target : AircraftId}
      {airspace : AirspaceVolumeId}
      {route : Option RouteSpec}
      {levelRestriction : Option Level}
      {tail : List AtcInstruction}
      (hContent : clearance.content = .compound content)
      (hSteps :
        content.steps =
          .specialVfrClearance target airspace route levelRestriction :: tail)
      (hReady :
        GreenfieldAirspaceWorldBackedCompoundReady
          world
          (.specialVfrClearance target airspace route levelRestriction)
          tail)
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none) :
      GreenfieldAirspaceWorldBackedCompoundIssuable world clearance

theorem resolvesWorldBackedRemainOutsideAirspaceCompoundClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {target : AircraftId}
    {airspace : AirspaceVolumeId}
    {tail : List AtcInstruction}
    (hContent : clearance.content = .compound content)
    (hSteps :
      content.steps =
        .remainOutsideControlledAirspace target airspace :: tail)
    (hReady :
      GreenfieldAirspaceWorldBackedCompoundReady
        world
        (.remainOutsideControlledAirspace target airspace)
        tail)
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  rcases hReady with ⟨hPrimaryReady, hTailReady⟩
  rcases hPrimaryReady with ⟨volume, hMem, hId⟩
  have hTailWrapped :
      anyWrappedConditionalStep tail = false :=
    anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts hTailReady
  have hWrapped :
      anyWrappedConditionalStep
        (.remainOutsideControlledAirspace target airspace :: tail) = false := by
    simp [anyWrappedConditionalStep, hTailWrapped]
  rcases resolvesRouteBearingImmediateAdjunctTail_of_ready
      (world := world)
      (state := initialState)
      (fallbackDomain := .route)
      (start := 1)
      (tail := tail)
      hTailReady with
      ⟨resolvedTail, hResolvedTail⟩
  let primaryStep :=
    compiledWorldBackedAirspaceStepNoRoute
      0
      .route
      (.remainOutsideControlledAirspace target airspace)
      volume.id
      volume.points
      (by simp [resolutionCompatible])
  refine ⟨{ source := clearance, steps := primaryStep :: resolvedTail }, ?_⟩
  refine ⟨?_, rfl, ?_⟩
  · simp [normalizeConditionalEnvelope, hContent, hCondition, hSteps, hWrapped]
  · have hPrimaryStep :
        ResolvesIndexedStep
          (RouteBearingScopedAviationWorld.toResolutionWorld world)
          initialState
          clearance.domain
          0
          (.remainOutsideControlledAirspace target airspace)
          primaryStep
          initialState := by
      simpa [hDomain, primaryStep, compiledWorldBackedAirspaceStepNoRoute, compiledWorldBackedAirspaceStep, hId] using
        (ResolvesIndexedStep.remainOutsideControlledAirspace
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .route)
          (index := 0)
          (target := target)
          (airspace := volume.id)
          (points := volume.points)
          (state := initialState)
          (hAirspace := RouteBearingScopedAviationWorld.mem_airspaceVolume_of_mem hMem))
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
          (0, .remainOutsideControlledAirspace target airspace) ::
            enumerateFrom 1 tail := by
      simp [hContent, hSteps, structuredInstructions, contentInstructions, indexedSteps, enumerateFrom]
    simpa [hIndexed] using
      ResolvesSteps.cons
        (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
        (state := initialState)
        (nextState := initialState)
        (finalState := initialState)
        (fallbackDomain := clearance.domain)
        (index := 0)
        (instruction := .remainOutsideControlledAirspace target airspace)
        (step := primaryStep)
        (tail := enumerateFrom 1 tail)
        (resolvedTail := resolvedTail)
        hPrimaryStep
        hResolvedTail'

theorem resolvesWorldBackedEnterZoneAirspaceCompoundClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {target : AircraftId}
    {airspace : AirspaceVolumeId}
    {route : Option RouteSpec}
    {levelRestriction : Option Level}
    {tail : List AtcInstruction}
    (hContent : clearance.content = .compound content)
    (hSteps :
      content.steps =
        .clearedToEnterControlZone target airspace route levelRestriction :: tail)
    (hReady :
      GreenfieldAirspaceWorldBackedCompoundReady
        world
        (.clearedToEnterControlZone target airspace route levelRestriction)
        tail)
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  rcases hReady with ⟨hPrimaryReady, hTailReady⟩
  rcases hPrimaryReady with ⟨volume, hMem, hId, hInteraction⟩
  cases hRoutePts : worldBackedAirspaceRouteInteraction? world volume route with
  | none =>
      simp [hRoutePts] at hInteraction
  | some triple =>
      rcases triple with ⟨routePoints, entryTransitions, exitTransitions⟩
      have hTailWrapped :
          anyWrappedConditionalStep tail = false :=
        anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts hTailReady
      have hWrapped :
          anyWrappedConditionalStep
            (.clearedToEnterControlZone target airspace route levelRestriction :: tail) = false := by
        simp [anyWrappedConditionalStep, hTailWrapped]
      rcases resolvesRouteBearingImmediateAdjunctTail_of_ready
          (world := world)
          (state := initialState)
          (fallbackDomain := .route)
          (start := 1)
          (tail := tail)
          hTailReady with
          ⟨resolvedTail, hResolvedTail⟩
      let primaryStep :=
        compiledWorldBackedAirspaceStep
          0
          .route
          (.clearedToEnterControlZone target airspace route levelRestriction)
          volume.id
          volume.points
          routePoints
          entryTransitions
          exitTransitions
          (by simp [resolutionCompatible])
      refine ⟨{ source := clearance, steps := primaryStep :: resolvedTail }, ?_⟩
      refine ⟨?_, rfl, ?_⟩
      · simp [normalizeConditionalEnvelope, hContent, hCondition, hSteps, hWrapped]
      · have hPrimaryStep :
            ResolvesIndexedStep
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              clearance.domain
              0
              (.clearedToEnterControlZone target airspace route levelRestriction)
              primaryStep
              initialState := by
          simpa [hDomain, primaryStep, compiledWorldBackedAirspaceStep, hId] using
            (ResolvesIndexedStep.clearedToEnterControlZone
              (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
              (fallbackDomain := .route)
              (index := 0)
              (target := target)
              (airspace := volume.id)
              (route := route)
              (levelRestriction := levelRestriction)
              (points := volume.points)
              (routePoints := routePoints)
              (entryTransitions := entryTransitions)
              (exitTransitions := exitTransitions)
              (state := initialState)
              (hAirspace := RouteBearingScopedAviationWorld.mem_airspaceVolume_of_mem hMem)
              (hRoute := by
                cases route with
                | none =>
                    have hNoRoute :
                        routePoints = [] ∧ entryTransitions = [] ∧ exitTransitions = [] := by
                      simpa [worldBackedAirspaceRouteInteraction?] using hRoutePts
                    exact hNoRoute
                | some routeSpec =>
                    cases hSpec : routeBearingRouteSpecPoints? world routeSpec with
                    | none =>
                        simp [worldBackedAirspaceRouteInteraction?, hSpec] at hRoutePts
                    | some specPoints =>
                        have hTouch : airspaceRouteTouches specPoints volume.points = true := by
                          by_cases hTouch' : airspaceRouteTouches specPoints volume.points = true
                          · exact hTouch'
                          · simp [worldBackedAirspaceRouteInteraction?, hSpec, hTouch'] at hRoutePts
                        have hResolved :
                            some
                                (specPoints,
                                  airspaceRouteEntryTransitions specPoints volume.points,
                                  airspaceRouteExitTransitions specPoints volume.points) =
                              some (routePoints, entryTransitions, exitTransitions) := by
                          simpa [worldBackedAirspaceRouteInteraction?, hSpec, hTouch] using hRoutePts
                        have hTuple :
                            (specPoints,
                              airspaceRouteEntryTransitions specPoints volume.points,
                              airspaceRouteExitTransitions specPoints volume.points) =
                            (routePoints, entryTransitions, exitTransitions) :=
                          Option.some.inj hResolved
                        cases hTuple
                        exact ⟨
                          RouteBearingScopedAviationWorld.routeSpecPoints_of_eq_some hSpec,
                          rfl,
                          rfl⟩))
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
              (0, .clearedToEnterControlZone target airspace route levelRestriction) ::
                enumerateFrom 1 tail := by
          simp [hContent, hSteps, structuredInstructions, contentInstructions, indexedSteps, enumerateFrom]
        simpa [hIndexed] using
          ResolvesSteps.cons
            (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
            (state := initialState)
            (nextState := initialState)
            (finalState := initialState)
            (fallbackDomain := clearance.domain)
            (index := 0)
            (instruction := .clearedToEnterControlZone target airspace route levelRestriction)
            (step := primaryStep)
            (tail := enumerateFrom 1 tail)
            (resolvedTail := resolvedTail)
            hPrimaryStep
            hResolvedTail'

theorem resolvesWorldBackedSpecialVfrAirspaceCompoundClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {target : AircraftId}
    {airspace : AirspaceVolumeId}
    {route : Option RouteSpec}
    {levelRestriction : Option Level}
    {tail : List AtcInstruction}
    (hContent : clearance.content = .compound content)
    (hSteps :
      content.steps =
        .specialVfrClearance target airspace route levelRestriction :: tail)
    (hReady :
      GreenfieldAirspaceWorldBackedCompoundReady
        world
        (.specialVfrClearance target airspace route levelRestriction)
        tail)
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  rcases hReady with ⟨hPrimaryReady, hTailReady⟩
  rcases hPrimaryReady with ⟨volume, hMem, hId, hInteraction⟩
  cases hRoutePts : worldBackedAirspaceRouteInteraction? world volume route with
  | none =>
      simp [hRoutePts] at hInteraction
  | some triple =>
      rcases triple with ⟨routePoints, entryTransitions, exitTransitions⟩
      have hTailWrapped :
          anyWrappedConditionalStep tail = false :=
        anyWrappedConditionalStep_false_of_routeBearingImmediateAdjuncts hTailReady
      have hWrapped :
          anyWrappedConditionalStep
            (.specialVfrClearance target airspace route levelRestriction :: tail) = false := by
        simp [anyWrappedConditionalStep, hTailWrapped]
      rcases resolvesRouteBearingImmediateAdjunctTail_of_ready
          (world := world)
          (state := initialState)
          (fallbackDomain := .route)
          (start := 1)
          (tail := tail)
          hTailReady with
          ⟨resolvedTail, hResolvedTail⟩
      let primaryStep :=
        compiledWorldBackedAirspaceStep
          0
          .route
          (.specialVfrClearance target airspace route levelRestriction)
          volume.id
          volume.points
          routePoints
          entryTransitions
          exitTransitions
          (by simp [resolutionCompatible])
      refine ⟨{ source := clearance, steps := primaryStep :: resolvedTail }, ?_⟩
      refine ⟨?_, rfl, ?_⟩
      · simp [normalizeConditionalEnvelope, hContent, hCondition, hSteps, hWrapped]
      · have hPrimaryStep :
            ResolvesIndexedStep
              (RouteBearingScopedAviationWorld.toResolutionWorld world)
              initialState
              clearance.domain
              0
              (.specialVfrClearance target airspace route levelRestriction)
              primaryStep
              initialState := by
          simpa [hDomain, primaryStep, compiledWorldBackedAirspaceStep, hId] using
            (ResolvesIndexedStep.specialVfrClearance
              (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
              (fallbackDomain := .route)
              (index := 0)
              (target := target)
              (airspace := volume.id)
              (route := route)
              (levelRestriction := levelRestriction)
              (points := volume.points)
              (routePoints := routePoints)
              (entryTransitions := entryTransitions)
              (exitTransitions := exitTransitions)
              (state := initialState)
              (hAirspace := RouteBearingScopedAviationWorld.mem_airspaceVolume_of_mem hMem)
              (hRoute := by
                cases route with
                | none =>
                    have hNoRoute :
                        routePoints = [] ∧ entryTransitions = [] ∧ exitTransitions = [] := by
                      simpa [worldBackedAirspaceRouteInteraction?] using hRoutePts
                    exact hNoRoute
                | some routeSpec =>
                    cases hSpec : routeBearingRouteSpecPoints? world routeSpec with
                    | none =>
                        simp [worldBackedAirspaceRouteInteraction?, hSpec] at hRoutePts
                    | some specPoints =>
                        have hTouch : airspaceRouteTouches specPoints volume.points = true := by
                          by_cases hTouch' : airspaceRouteTouches specPoints volume.points = true
                          · exact hTouch'
                          · simp [worldBackedAirspaceRouteInteraction?, hSpec, hTouch'] at hRoutePts
                        have hResolved :
                            some
                                (specPoints,
                                  airspaceRouteEntryTransitions specPoints volume.points,
                                  airspaceRouteExitTransitions specPoints volume.points) =
                              some (routePoints, entryTransitions, exitTransitions) := by
                          simpa [worldBackedAirspaceRouteInteraction?, hSpec, hTouch] using hRoutePts
                        have hTuple :
                            (specPoints,
                              airspaceRouteEntryTransitions specPoints volume.points,
                              airspaceRouteExitTransitions specPoints volume.points) =
                            (routePoints, entryTransitions, exitTransitions) :=
                          Option.some.inj hResolved
                        cases hTuple
                        exact ⟨
                          RouteBearingScopedAviationWorld.routeSpecPoints_of_eq_some hSpec,
                          rfl,
                          rfl⟩))
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
              (0, .specialVfrClearance target airspace route levelRestriction) ::
                enumerateFrom 1 tail := by
          simp [hContent, hSteps, structuredInstructions, contentInstructions, indexedSteps, enumerateFrom]
        simpa [hIndexed] using
          ResolvesSteps.cons
            (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
            (state := initialState)
            (nextState := initialState)
            (finalState := initialState)
            (fallbackDomain := clearance.domain)
            (index := 0)
            (instruction := .specialVfrClearance target airspace route levelRestriction)
            (step := primaryStep)
            (tail := enumerateFrom 1 tail)
            (resolvedTail := resolvedTail)
            hPrimaryStep
            hResolvedTail'

theorem GreenfieldAirspaceWorldBackedCompoundAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldAirspaceWorldBackedCompoundIssuable world clearance) :
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
  | remainOutside hContent hSteps hReady hDomain hCondition =>
      rcases resolvesWorldBackedRemainOutsideAirspaceCompoundClearance_of_ready
          (world := world)
          (initialState := initialState)
          (clearance := clearance)
          (content := _)
          (target := _)
          (airspace := _)
          (tail := _)
          hContent
          hSteps
          hReady
          hDomain
          hCondition with
          ⟨resolved, hResolve⟩
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [hResolve.sourceEq] using hFresh
      exact ⟨resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  | enterZone hContent hSteps hReady hDomain hCondition =>
      rcases resolvesWorldBackedEnterZoneAirspaceCompoundClearance_of_ready
          (world := world)
          (initialState := initialState)
          (clearance := clearance)
          (content := _)
          (target := _)
          (airspace := _)
          (route := _)
          (levelRestriction := _)
          (tail := _)
          hContent
          hSteps
          hReady
          hDomain
          hCondition with
          ⟨resolved, hResolve⟩
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [hResolve.sourceEq] using hFresh
      exact ⟨resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  | specialVfr hContent hSteps hReady hDomain hCondition =>
      rcases resolvesWorldBackedSpecialVfrAirspaceCompoundClearance_of_ready
          (world := world)
          (initialState := initialState)
          (clearance := clearance)
          (content := _)
          (target := _)
          (airspace := _)
          (route := _)
          (levelRestriction := _)
          (tail := _)
          hContent
          hSteps
          hReady
          hDomain
          hCondition with
          ⟨resolved, hResolve⟩
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [hResolve.sourceEq] using hFresh
      exact ⟨resolved, hResolve, ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

theorem GreenfieldAirspaceWorldBackedCompoundAuthorizedIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : GreenfieldAirspaceWorldBackedCompoundIssuable world clearance)
    (hAuthority :
      GreenfieldAirspaceCompoundWorldAuthorized
        world
        clearance.issuedBy
        (structuredInstructions clearance)) :
    ∃ resolved,
      greenfieldAirspaceCompoundInstructionsIssuerAuthorized
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
      greenfieldAirspaceCompoundInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    greenfieldAirspaceCompoundInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  rcases GreenfieldAirspaceWorldBackedCompoundAdmissionSoundnessTheorem
      (world := world)
      (existing := existing)
      (initialState := initialState)
      (clearance := clearance)
      hReach
      hFresh
      hIssuable with
      ⟨resolved, hResolve, hReachable⟩
  exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

def sampleWorldBackedRemainOutsideContactCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-WB-ROCA-COMP"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .remainOutsideControlledAirspace "TEST123" "CTR-1"
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 80
        status := .active
        condition := none }
    steps :=
      [ compiledWorldBackedAirspaceStepNoRoute
          0
          .route
          (.remainOutsideControlledAirspace "TEST123" "CTR-1")
          "CTR-1"
          ["P-IN-CTR"]
          (by simp [resolutionCompatible])
      , compileResolvedStep
          1
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedWorldBackedRemainOutsideContactCompound : ManagedResolvedClearance :=
  { resolved := sampleWorldBackedRemainOutsideContactCompound }

def sampleWorldBackedEnterZoneContactCompound : ResolvedClearance :=
  { source :=
      { id := "CLR-WB-ENTER-COMP"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .clearedToEnterControlZone "TEST123" "CTR-1" none none
              , .contactFrequency "TEST123" .tower (some "118.500") ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 81
        status := .active
        condition := none }
    steps :=
      [ compiledWorldBackedAirspaceStepNoRoute
          0
          .route
          (.clearedToEnterControlZone "TEST123" "CTR-1" none none)
          "CTR-1"
          ["P-IN-CTR"]
          (by simp [resolutionCompatible])
      , compileResolvedStep
          1
          .frequency
          (.contactFrequency "TEST123" .tower (some "118.500"))
          (.frequencyChange { roleName := .tower, instructedFrequency := some "118.500" })
          (by simp [resolutionCompatible]) ] }

def sampleManagedWorldBackedEnterZoneContactCompound : ManagedResolvedClearance :=
  { resolved := sampleWorldBackedEnterZoneContactCompound }

def sampleResolvedIncomingApproachContactForWorldBackedAirspace : ResolvedClearance :=
  { source :=
      { id := "CLR-WB-FREQ"
        aircraft := "TEST123"
        content := .single (.contactFrequency "TEST123" .approach (some "120.100"))
        domain := .frequency
        issuedBy := "CTRL-1"
        issuedAt := 82
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .frequency
          (.contactFrequency "TEST123" .approach (some "120.100"))
          (.frequencyChange { roleName := .approach, instructedFrequency := some "120.100" })
          (by simp [resolutionCompatible]) ] }

theorem worldBackedRemainOutsideContactCompound_tail_completion_keeps_primary_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedWorldBackedRemainOutsideContactCompound
        { position := some "P-OUTSIDE"
          currentRole := some .tower }
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem worldBackedRemainOutsideContactCompound_exitTransition_and_tail_completion_keeps_primary_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedWorldBackedRemainOutsideContactCompound
        { position := some "P-OUTSIDE"
          airspaceTransitions := UniqueSet.singleton "CTR-1"
          currentRole := some .tower }
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem worldBackedEnterZoneContactCompound_tail_completion_completes_clearance :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedWorldBackedEnterZoneContactCompound
        { position := some "P-IN-CTR"
          currentRole := some .tower }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem worldBackedEnterZoneContactCompound_entry_transition_and_tail_completion_completes_clearance :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedWorldBackedEnterZoneContactCompound
        { position := some "P-IN-CTR"
          activeAirspaces := UniqueSet.singleton "CTR-1"
          airspaceTransitions := UniqueSet.singleton "CTR-1"
          currentRole := some .tower }
    evaluation.updated.status = .completed ∧
      evaluation.newlyCompletedSteps = UniqueSet.singleton 1 := by
  native_decide

theorem worldBackedRemainOutsideContact_frequencySupersession_preserves_primary :
    let admission :=
      admitResolvedClearance
        [sampleManagedWorldBackedRemainOutsideContactCompound]
        sampleResolvedIncomingApproachContactForWorldBackedAirspace
    resolvedClearanceIds admission.clearances = ["CLR-WB-ROCA-COMP", "CLR-WB-FREQ"] ∧
      resolvedClearanceIds admission.fullySuperseded = [] ∧
      resolvedClearanceIds admission.partiallySuperseded = ["CLR-WB-ROCA-COMP"] ∧
      findResolvedById admission.clearances "CLR-WB-ROCA-COMP" =
        some (sampleManagedWorldBackedRemainOutsideContactCompound.suppress
          (UniqueSet.singleton .frequency)) := by
  native_decide

theorem worldBackedEnterZoneContact_frequencySupersession_reconcile_transitions_to_terminal :
    let admission :=
      admitResolvedClearance
        [sampleManagedWorldBackedEnterZoneContactCompound]
        sampleResolvedIncomingApproachContactForWorldBackedAirspace
    let reconciliation :=
      reconcileResolvedClearances
        admission.clearances
        { position := some "P-IN-CTR" }
        (fun _ _ => false)
    resolvedClearanceIds reconciliation.clearances = ["CLR-WB-FREQ"] ∧
      resolvedClearanceIds reconciliation.terminalClearances = ["CLR-WB-ENTER-COMP"] := by
  native_decide

end Greenfield
end CertifiedAtc
