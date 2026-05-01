import CertifiedAtc.RouteBearingResolutionBridge
import CertifiedAtc.GreenfieldReachability
import CertifiedAtc.GreenfieldExecution

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldRouteAdjacentWorldBackedCurrentShape` closes the delivered
route-adjacent family on the current explicit approach/circuit model.

Unlike the older current-shape Phase B modules, this layer resolves each
instruction against concrete current state plus published-world facts:

- `ContinueApproach` requires a current published approach and resolves to its
  concrete waypoint / threshold facts
- `ExtendDownwind` requires a current published circuit with a published
  extended-downwind path and carries the published off-ramp paths too
- `Orbit` requires a current published circuit plus a current orbit point and
  resolves to the published orbit loop for that point/direction pair

Lifecycle stays intentionally conservative on the current engine:

- `ContinueApproach` remains active
- `ExtendDownwind` and `Orbit` remain persistent
- a `GoAround` still fully supersedes the active `ContinueApproach` slice
- frequency instructions still do not supersede the single-step persistent
  circuit slices
-/

def routeAdjacentWorldBackedFallbackDomain : AtcInstruction → ClearanceDomain
  | .continueApproach _ => .route
  | .extendDownwind _ => .runway
  | .orbit _ _ => .runway
  | _ => .route

def GreenfieldRouteAdjacentWorldBackedReady
    (world : RouteBearingScopedAviationWorld)
    (state : ResolutionState) :
    AtcInstruction → Prop
  | .continueApproach _ =>
      ∃ approach waypointPoints thresholdPoint,
        state.currentApproach = some approach ∧
          (RouteBearingScopedAviationWorld.toResolutionWorld world).approachWaypoints
            approach
            waypointPoints ∧
          (RouteBearingScopedAviationWorld.toResolutionWorld world).approachThreshold
            approach
            thresholdPoint
  | .extendDownwind _ =>
      ∃ circuit extendedPathPoints offRampPoints,
        state.currentCircuit = some circuit ∧
          (RouteBearingScopedAviationWorld.toResolutionWorld world).circuitExtendedDownwind
            circuit
            extendedPathPoints
            offRampPoints
  | .orbit _ direction =>
      ∃ circuit orbitPoint loopPoints,
        state.currentCircuit = some circuit ∧
          state.currentPoint = some orbitPoint ∧
          (RouteBearingScopedAviationWorld.toResolutionWorld world).circuitOrbit
            circuit
            orbitPoint
            direction
            loopPoints
  | _ => False

inductive GreenfieldRouteAdjacentWorldBackedIssuable
    (world : RouteBearingScopedAviationWorld)
    (initialState : ResolutionState) :
    StructuredClearance → Prop
  | continueApproach
      {clearance : StructuredClearance}
      {target : AircraftId}
      (hContent : clearance.content = .single (.continueApproach target))
      (hDomain : clearance.domain = .route)
      (hCondition : clearance.condition = none)
      (hReady :
        GreenfieldRouteAdjacentWorldBackedReady
          world
          initialState
          (.continueApproach target)) :
      GreenfieldRouteAdjacentWorldBackedIssuable world initialState clearance
  | extendDownwind
      {clearance : StructuredClearance}
      {target : AircraftId}
      (hContent : clearance.content = .single (.extendDownwind target))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none)
      (hReady :
        GreenfieldRouteAdjacentWorldBackedReady
          world
          initialState
          (.extendDownwind target)) :
      GreenfieldRouteAdjacentWorldBackedIssuable world initialState clearance
  | orbit
      {clearance : StructuredClearance}
      {target : AircraftId}
      {direction : OrbitDirection}
      (hContent : clearance.content = .single (.orbit target direction))
      (hDomain : clearance.domain = .runway)
      (hCondition : clearance.condition = none)
      (hReady :
        GreenfieldRouteAdjacentWorldBackedReady
          world
          initialState
          (.orbit target direction)) :
      GreenfieldRouteAdjacentWorldBackedIssuable world initialState clearance

theorem resolvesIndexedRouteAdjacentWorldBackedStep_of_ready
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {instruction : AtcInstruction}
    (hReady : GreenfieldRouteAdjacentWorldBackedReady world state instruction) :
    ∃ finalState step,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        (routeAdjacentWorldBackedFallbackDomain instruction)
        0
        instruction
        step
        finalState := by
  cases instruction with
  | continueApproach target =>
      rcases hReady with ⟨approach, waypointPoints, thresholdPoint, hCurrentApproach, hWaypoints, hThreshold⟩
      let step :=
        compileResolvedStep
          0
          .route
          (.continueApproach target)
          (.continueApproach
            { approach := approach
              waypointPoints := waypointPoints
              thresholdPoint := thresholdPoint })
          (by simp [resolutionCompatible])
      refine ⟨{ state with currentApproach := some approach }, step, ?_⟩
      simpa [routeAdjacentWorldBackedFallbackDomain, step] using
        (ResolvesIndexedStep.continueApproach
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .route)
          (index := 0)
          (target := target)
          (approach := approach)
          (waypointPoints := waypointPoints)
          (thresholdPoint := thresholdPoint)
          (state := state)
          hCurrentApproach
          hWaypoints
          hThreshold)
  | extendDownwind target =>
      rcases hReady with ⟨circuit, extendedPathPoints, offRampPoints, hCurrentCircuit, hExtendedDownwind⟩
      let step :=
        compileResolvedStep
          0
          .runway
          (.extendDownwind target)
          (.extendDownwind
            { circuit := circuit
              extendedPathPoints := extendedPathPoints
              offRampPoints := offRampPoints })
          (by simp [resolutionCompatible])
      refine ⟨{ state with currentCircuit := some circuit }, step, ?_⟩
      simpa [routeAdjacentWorldBackedFallbackDomain, step] using
        (ResolvesIndexedStep.extendDownwind
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .runway)
          (index := 0)
          (target := target)
          (circuit := circuit)
          (extendedPathPoints := extendedPathPoints)
          (offRampPoints := offRampPoints)
          (state := state)
          hCurrentCircuit
          hExtendedDownwind)
  | orbit target direction =>
      rcases hReady with ⟨circuit, orbitPoint, loopPoints, hCurrentCircuit, hCurrentPoint, hOrbit⟩
      let step :=
        compileResolvedStep
          0
          .runway
          (.orbit target direction)
          (.orbit
            { circuit := circuit
              orbitPoint := orbitPoint
              direction := direction
              loopPoints := loopPoints })
          (by simp [resolutionCompatible])
      refine ⟨{ state with currentPoint := some orbitPoint, currentCircuit := some circuit }, step, ?_⟩
      simpa [routeAdjacentWorldBackedFallbackDomain, step] using
        (ResolvesIndexedStep.orbit
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (fallbackDomain := .runway)
          (index := 0)
          (target := target)
          (direction := direction)
          (circuit := circuit)
          (orbitPoint := orbitPoint)
          (loopPoints := loopPoints)
          (state := state)
          hCurrentCircuit
          hCurrentPoint
          hOrbit)
  | _ =>
      cases hReady

theorem GreenfieldRouteAdjacentWorldBackedAdmissionSoundnessTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable :
      GreenfieldRouteAdjacentWorldBackedIssuable world initialState clearance) :
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
      rename_i target hContent hDomain hCondition hReady
      rcases resolvesIndexedRouteAdjacentWorldBackedStep_of_ready
          (world := world)
          (state := initialState)
          (instruction := .continueApproach target)
          hReady with ⟨finalState, step, hStep⟩
      let resolved := singletonResolvedClearance clearance step
      have hStepDomain : step.domain = .route := by
        cases hStep <;> simp [compileResolvedStep, instructionDomain?,
          routeAdjacentWorldBackedFallbackDomain] at *
      have hStepAtDomain :
          ResolvesIndexedStep
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance.domain
            0
            (.continueApproach target)
            step
            finalState := by
        simpa [hDomain, routeAdjacentWorldBackedFallbackDomain] using hStep
      have hResolve :
          ResolvesClearance
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance
            resolved
            finalState := by
        exact
          resolvesSingleInstructionClearance
            (by simp [normalizeConditionalEnvelope, hContent, hCondition])
            hContent
            (by simpa [hStepDomain] using hDomain)
            hStepAtDomain
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [resolved] using hFresh
      exact
        ⟨finalState, resolved, hResolve,
          ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  | extendDownwind =>
      rename_i target hContent hDomain hCondition hReady
      rcases resolvesIndexedRouteAdjacentWorldBackedStep_of_ready
          (world := world)
          (state := initialState)
          (instruction := .extendDownwind target)
          hReady with ⟨finalState, step, hStep⟩
      let resolved := singletonResolvedClearance clearance step
      have hStepDomain : step.domain = .runway := by
        cases hStep <;> simp [compileResolvedStep, instructionDomain?,
          routeAdjacentWorldBackedFallbackDomain] at *
      have hStepAtDomain :
          ResolvesIndexedStep
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance.domain
            0
            (.extendDownwind target)
            step
            finalState := by
        simpa [hDomain, routeAdjacentWorldBackedFallbackDomain] using hStep
      have hResolve :
          ResolvesClearance
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance
            resolved
            finalState := by
        exact
          resolvesSingleInstructionClearance
            (by simp [normalizeConditionalEnvelope, hContent, hCondition])
            hContent
            (by simpa [hStepDomain] using hDomain)
            hStepAtDomain
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [resolved] using hFresh
      exact
        ⟨finalState, resolved, hResolve,
          ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩
  | orbit =>
      rename_i target direction hContent hDomain hCondition hReady
      rcases resolvesIndexedRouteAdjacentWorldBackedStep_of_ready
          (world := world)
          (state := initialState)
          (instruction := .orbit target direction)
          hReady with ⟨finalState, step, hStep⟩
      let resolved := singletonResolvedClearance clearance step
      have hStepDomain : step.domain = .runway := by
        cases hStep <;> simp [compileResolvedStep, instructionDomain?,
          routeAdjacentWorldBackedFallbackDomain] at *
      have hStepAtDomain :
          ResolvesIndexedStep
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance.domain
            0
            (.orbit target direction)
            step
            finalState := by
        simpa [hDomain, routeAdjacentWorldBackedFallbackDomain] using hStep
      have hResolve :
          ResolvesClearance
            (RouteBearingScopedAviationWorld.toResolutionWorld world)
            initialState
            clearance
            resolved
            finalState := by
        exact
          resolvesSingleInstructionClearance
            (by simp [normalizeConditionalEnvelope, hContent, hCondition])
            hContent
            (by simpa [hStepDomain] using hDomain)
            hStepAtDomain
      have hFreshResolved : resolved.source.id ∉ resolvedClearanceIds existing := by
        simpa [resolved] using hFresh
      exact
        ⟨finalState, resolved, hResolve,
          ReachableResolvedSet.admit_of_resolved hReach hFreshResolved hResolve⟩

def sampleResolvedSingleContinueApproachWorldBacked : ResolvedClearance :=
  { source :=
      { id := "CLR-CONT-APP-WB"
        aircraft := "TEST123"
        content := .single (.continueApproach "TEST123")
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 60
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
          (by native_decide) ] }

def sampleManagedResolvedSingleContinueApproachWorldBacked : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleContinueApproachWorldBacked }

def sampleResolvedSingleExtendDownwindWorldBacked : ResolvedClearance :=
  { source :=
      { id := "CLR-EXT-DW-WB"
        aircraft := "TEST123"
        content := .single (.extendDownwind "TEST123")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 61
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
          (by native_decide) ] }

def sampleManagedResolvedSingleExtendDownwindWorldBacked : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleExtendDownwindWorldBacked }

def sampleResolvedSingleOrbitWorldBacked : ResolvedClearance :=
  { source :=
      { id := "CLR-ORBIT-WB"
        aircraft := "TEST123"
        content := .single (.orbit "TEST123" .left)
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 62
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
          (by native_decide) ] }

def sampleManagedResolvedSingleOrbitWorldBacked : ManagedResolvedClearance :=
  { resolved := sampleResolvedSingleOrbitWorldBacked }

def sampleResolvedIncomingRouteAdjacentFrequency : ResolvedClearance :=
  { source :=
      { id := "CLR-RA-FREQ"
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
          (by native_decide) ] }

def sampleResolvedIncomingGoAroundForContinueApproach : ResolvedClearance :=
  { source :=
      { id := "CLR-RA-GA"
        aircraft := "TEST123"
        content := .single (.goAround "TEST123")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 64
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .runway
          (.goAround "TEST123")
          (.runwayOperation { runway := "27", thresholdPoint := "RWY27" })
          (by native_decide) ] }

theorem singleContinueApproachWorldBacked_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleContinueApproachWorldBacked
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem goAround_fully_supersedes_singleContinueApproachWorldBacked :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleContinueApproachWorldBacked]
        sampleResolvedIncomingGoAroundForContinueApproach
    resolvedClearanceIds admitted.clearances = ["CLR-RA-GA"] ∧
      resolvedClearanceIds admitted.fullySuperseded = ["CLR-CONT-APP-WB"] := by
  native_decide

theorem singleExtendDownwindWorldBacked_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleExtendDownwindWorldBacked
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleExtendDownwindWorldBacked :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleExtendDownwindWorldBacked]
        sampleResolvedIncomingRouteAdjacentFrequency
    resolvedClearanceIds admitted.clearances = ["CLR-EXT-DW-WB", "CLR-RA-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

theorem singleOrbitWorldBacked_remains_active :
    let evaluation :=
      evaluateResolvedCompletion
        sampleManagedResolvedSingleOrbitWorldBacked
        {}
    evaluation.updated.status = .active ∧
      evaluation.newlyCompletedSteps = ({} : UniqueSet Nat) := by
  native_decide

theorem incomingFrequency_does_not_supersede_singleOrbitWorldBacked :
    let admitted :=
      admitResolvedClearance
        [sampleManagedResolvedSingleOrbitWorldBacked]
        sampleResolvedIncomingRouteAdjacentFrequency
    resolvedClearanceIds admitted.clearances = ["CLR-ORBIT-WB", "CLR-RA-FREQ"] ∧
      resolvedClearanceIds admitted.fullySuperseded = [] := by
  native_decide

end Greenfield
end CertifiedAtc
