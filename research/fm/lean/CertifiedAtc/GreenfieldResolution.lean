import CertifiedAtc.GreenfieldResolved

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldResolution` is the proof-side world-to-resolved relation.

Unlike the Kotlin runtime, this module does not try to execute a concrete graph
algorithm. Instead it states the facts a world must provide for instruction
resolution to be valid, and relates those facts to resolved steps and resolved
clearances. This lets the FM side reason about resolved execution without
pretending the resolved layer is manually assembled data.
-/

structure ResolutionWorld where
  taxiRoute : PointId → PointId → List PointId → Prop
  holdingPointForRunway : RunwayId → PointId → Prop
  crossingPointForRunway : RunwayId → PointId → Prop
  farEndPointForRunway : RunwayId → PointId → Prop
  fixPoint : FixId → PointId → Prop
  routeSpecPoints : RouteSpec → List PointId → Prop
  holdingPatternFor : HoldSpec → HoldingPatternId → FixId → Prop
  approachFor : ApproachType → RunwayId → Option RunwayId → ApproachId → Prop
  roleFrequency : RoleName → Frequency → Prop
  circuitJoin : CircuitDirection → JoinType → Option RunwayId → CircuitProcedureId → Level → Prop
  airspaceVolume : AirspaceVolumeId → List PointId → Prop

structure ConcreteTaxiRoute where
  start : PointId
  destination : PointId
  path : List PointId
  deriving DecidableEq, Repr

structure ConcreteHoldingPatternBinding where
  hold : HoldSpec
  pattern : HoldingPatternId
  fix : FixId
  deriving DecidableEq, Repr

structure ConcreteApproachBinding where
  approachType : ApproachType
  runway : RunwayId
  circlingRunway : Option RunwayId
  approach : ApproachId
  deriving DecidableEq, Repr

structure ConcreteCircuitJoinBinding where
  direction : CircuitDirection
  joinType : JoinType
  runway : Option RunwayId
  circuit : CircuitProcedureId
  altitude : Level
  deriving DecidableEq, Repr

structure ConcreteAirspaceVolumeBinding where
  airspace : AirspaceVolumeId
  points : List PointId
  deriving DecidableEq, Repr

structure ConcreteResolutionWorld where
  taxiRoutes : List ConcreteTaxiRoute := []
  runwayHoldingPoints : List (RunwayId × PointId) := []
  runwayCrossingPoints : List (RunwayId × PointId) := []
  runwayFarEnds : List (RunwayId × PointId) := []
  fixPoints : List (FixId × PointId) := []
  holdingPatterns : List ConcreteHoldingPatternBinding := []
  approaches : List ConcreteApproachBinding := []
  roleFrequencies : List (RoleName × Frequency) := []
  circuitJoins : List ConcreteCircuitJoinBinding := []
  airspaceVolumes : List ConcreteAirspaceVolumeBinding := []
  deriving Repr

def ConcreteResolutionWorld.toResolutionWorld
    (world : ConcreteResolutionWorld) : ResolutionWorld :=
  { taxiRoute := fun start destination path =>
      { start := start, destination := destination, path := path } ∈ world.taxiRoutes
    holdingPointForRunway := fun runway point =>
      (runway, point) ∈ world.runwayHoldingPoints
    crossingPointForRunway := fun runway point =>
      (runway, point) ∈ world.runwayCrossingPoints
    farEndPointForRunway := fun runway point =>
      (runway, point) ∈ world.runwayFarEnds
    fixPoint := fun fix point =>
      (fix, point) ∈ world.fixPoints
    routeSpecPoints := fun _ _ => False
    holdingPatternFor := fun hold pattern fix =>
      { hold := hold, pattern := pattern, fix := fix } ∈ world.holdingPatterns
    approachFor := fun approachType runway circlingRunway approach =>
      { approachType := approachType, runway := runway, circlingRunway := circlingRunway, approach := approach } ∈ world.approaches
    roleFrequency := fun role frequency =>
      (role, frequency) ∈ world.roleFrequencies
    circuitJoin := fun direction joinType runway circuit altitude =>
      { direction := direction, joinType := joinType, runway := runway, circuit := circuit, altitude := altitude } ∈ world.circuitJoins
    airspaceVolume := fun airspace points =>
      { airspace := airspace, points := points } ∈ world.airspaceVolumes }

theorem ConcreteResolutionWorld.mem_taxiRoute
    {world : ConcreteResolutionWorld}
    {start destination : PointId}
    {path : List PointId}
    (hMem : { start := start, destination := destination, path := path } ∈ world.taxiRoutes) :
    world.toResolutionWorld.taxiRoute start destination path := by
  simpa [ConcreteResolutionWorld.toResolutionWorld] using hMem

theorem ConcreteResolutionWorld.mem_fixPoint
    {world : ConcreteResolutionWorld}
    {fix : FixId}
    {point : PointId}
    (hMem : (fix, point) ∈ world.fixPoints) :
    world.toResolutionWorld.fixPoint fix point := by
  simpa [ConcreteResolutionWorld.toResolutionWorld] using hMem

theorem ConcreteResolutionWorld.mem_roleFrequency
    {world : ConcreteResolutionWorld}
    {role : RoleName}
    {frequency : Frequency}
    (hMem : (role, frequency) ∈ world.roleFrequencies) :
    world.toResolutionWorld.roleFrequency role frequency := by
  simpa [ConcreteResolutionWorld.toResolutionWorld] using hMem

theorem ConcreteResolutionWorld.mem_circuitJoin
    {world : ConcreteResolutionWorld}
    {direction : CircuitDirection}
    {joinType : JoinType}
    {runway : Option RunwayId}
    {circuit : CircuitProcedureId}
    {altitude : Level}
    (hMem :
      { direction := direction, joinType := joinType, runway := runway, circuit := circuit, altitude := altitude } ∈
        world.circuitJoins) :
    world.toResolutionWorld.circuitJoin direction joinType runway circuit altitude := by
  simpa [ConcreteResolutionWorld.toResolutionWorld] using hMem

theorem ConcreteResolutionWorld.mem_airspaceVolume
    {world : ConcreteResolutionWorld}
    {airspace : AirspaceVolumeId}
    {points : List PointId}
    (hMem : { airspace := airspace, points := points } ∈ world.airspaceVolumes) :
    world.toResolutionWorld.airspaceVolume airspace points := by
  simpa [ConcreteResolutionWorld.toResolutionWorld] using hMem

structure ResolutionState where
  currentPoint : Option PointId := none
  deriving DecidableEq, Repr

inductive ResolvesIndexedStep :
    ResolutionWorld →
    ResolutionState →
    ClearanceDomain →
    Nat →
    AtcInstruction →
    ResolvedStep →
    ResolutionState →
    Prop
  | taxi
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (start destination : PointId)
      (via path : List PointId)
      (hRoute : world.taxiRoute start destination path) :
      ResolvesIndexedStep
        world
        { currentPoint := some start }
        fallbackDomain
        index
        (.taxiTo target destination via)
        (compileResolvedStep
          index
          fallbackDomain
          (.taxiTo target destination via)
          (.taxi { destination := destination, path := path })
          (by simp [resolutionCompatible]))
        { currentPoint := some destination }
  | holdShort
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (runway : RunwayId)
      (point : PointId)
      (state : ResolutionState)
      (hPoint : world.holdingPointForRunway runway point) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.holdShortOf target runway)
        (compileResolvedStep
          index
          fallbackDomain
          (.holdShortOf target runway)
          (.holdShort { runway := runway, point := point })
          (by simp [resolutionCompatible]))
        { currentPoint := some point }
  | crossing
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (runway : RunwayId)
      (point : PointId)
      (state : ResolutionState)
      (hPoint : world.crossingPointForRunway runway point) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.crossRunway target runway)
        (compileResolvedStep
          index
          fallbackDomain
          (.crossRunway target runway)
          (.crossing { runway := runway, crossingPoint := point })
          (by simp [resolutionCompatible]))
        { currentPoint := some point }
  | backtrack
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (runway : RunwayId)
      (farEndPoint : PointId)
      (state : ResolutionState)
      (hFarEnd : world.farEndPointForRunway runway farEndPoint) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.backtrackRunway target runway)
        (compileResolvedStep
          index
          fallbackDomain
          (.backtrackRunway target runway)
          (.backtrack { runway := runway, farEndPoint := farEndPoint })
          (by simp [resolutionCompatible]))
        { currentPoint := some farEndPoint }
  | route
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (clearanceLimitFix : FixId)
      (route : Option RouteSpec)
      (point : PointId)
      (state : ResolutionState)
      (hPoint : world.fixPoint clearanceLimitFix point) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.clearedTo target clearanceLimitFix route)
        (compileResolvedStep
          index
          fallbackDomain
          (.clearedTo target clearanceLimitFix route)
          (.route { clearanceLimitFix := clearanceLimitFix, clearanceLimitPoint := point })
          (by simp [resolutionCompatible]))
        state
  | holding
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (hold : HoldSpec)
      (efc : Option String)
      (pattern : HoldingPatternId)
      (fix : FixId)
      (state : ResolutionState)
      (hPattern : world.holdingPatternFor hold pattern fix) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.holdAt target hold efc)
        (compileResolvedStep
          index
          fallbackDomain
          (.holdAt target hold efc)
          (.holding { holdingPattern := pattern, fix := fix })
          (by simp [resolutionCompatible]))
        state
  | approach
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (approachType : ApproachType)
      (runway : RunwayId)
      (circlingRunway : Option RunwayId)
      (approach : ApproachId)
      (state : ResolutionState)
      (hApproach : world.approachFor approachType runway circlingRunway approach) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.clearedApproach target approachType runway circlingRunway)
        (compileResolvedStep
          index
          fallbackDomain
          (.clearedApproach target approachType runway circlingRunway)
          (.approach { approach := approach, runway := runway })
          (by simp [resolutionCompatible]))
        state
  | contactFrequencyExplicit
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (role : RoleName)
      (frequency : Frequency)
      (state : ResolutionState) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.contactFrequency target role (some frequency))
        (compileResolvedStep
          index
          fallbackDomain
          (.contactFrequency target role (some frequency))
          (.frequencyChange { roleName := role, instructedFrequency := some frequency })
          (by simp [resolutionCompatible]))
        state
  | contactFrequencyImplicit
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (role : RoleName)
      (frequency : Frequency)
      (state : ResolutionState)
      (hFrequency : world.roleFrequency role frequency) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.contactFrequency target role none)
        (compileResolvedStep
          index
          fallbackDomain
          (.contactFrequency target role none)
          (.frequencyChange { roleName := role, instructedFrequency := some frequency })
          (by simp [resolutionCompatible]))
        state
  | remainOutsideControlledAirspace
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (airspace : AirspaceVolumeId)
      (points : List PointId)
      (state : ResolutionState)
      (hAirspace : world.airspaceVolume airspace points) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.remainOutsideControlledAirspace target airspace)
        (compileResolvedStep
          index
          fallbackDomain
          (.remainOutsideControlledAirspace target airspace)
          (.airspace
            { airspace := airspace
              points := points
              routePoints := []
              entryTransitions := []
              exitTransitions := [] })
          (by simp [resolutionCompatible]))
        state
  | clearedToEnterControlZone
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (airspace : AirspaceVolumeId)
      (route : Option RouteSpec)
      (levelRestriction : Option Level)
      (points : List PointId)
      (routePoints : List PointId)
      (entryTransitions : List (PointId × PointId))
      (exitTransitions : List (PointId × PointId))
      (state : ResolutionState)
      (hAirspace : world.airspaceVolume airspace points)
      (hRoute :
        match route with
        | some routeSpec =>
            world.routeSpecPoints routeSpec routePoints ∧
              entryTransitions = airspaceRouteEntryTransitions routePoints points ∧
              exitTransitions = airspaceRouteExitTransitions routePoints points
        | none =>
            routePoints = [] ∧ entryTransitions = [] ∧ exitTransitions = []) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.clearedToEnterControlZone target airspace route levelRestriction)
        (compileResolvedStep
          index
          fallbackDomain
          (.clearedToEnterControlZone target airspace route levelRestriction)
          (.airspace
            { airspace := airspace
              points := points
              routePoints := routePoints
              entryTransitions := entryTransitions
              exitTransitions := exitTransitions })
          (by simp [resolutionCompatible]))
        state
  | specialVfrClearance
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (airspace : AirspaceVolumeId)
      (route : Option RouteSpec)
      (levelRestriction : Option Level)
      (points : List PointId)
      (routePoints : List PointId)
      (entryTransitions : List (PointId × PointId))
      (exitTransitions : List (PointId × PointId))
      (state : ResolutionState)
      (hAirspace : world.airspaceVolume airspace points)
      (hRoute :
        match route with
        | some routeSpec =>
            world.routeSpecPoints routeSpec routePoints ∧
              entryTransitions = airspaceRouteEntryTransitions routePoints points ∧
              exitTransitions = airspaceRouteExitTransitions routePoints points
        | none =>
            routePoints = [] ∧ entryTransitions = [] ∧ exitTransitions = []) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.specialVfrClearance target airspace route levelRestriction)
        (compileResolvedStep
          index
          fallbackDomain
          (.specialVfrClearance target airspace route levelRestriction)
          (.airspace
            { airspace := airspace
              points := points
              routePoints := routePoints
              entryTransitions := entryTransitions
              exitTransitions := exitTransitions })
          (by simp [resolutionCompatible]))
        state
  | monitorFrequencyExplicit
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (role : RoleName)
      (frequency : Frequency)
      (state : ResolutionState) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.monitorFrequency target role (some frequency))
        (compileResolvedStep
          index
          fallbackDomain
          (.monitorFrequency target role (some frequency))
          (.frequencyChange { roleName := role, instructedFrequency := some frequency })
          (by simp [resolutionCompatible]))
        state
  | monitorFrequencyImplicit
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (role : RoleName)
      (frequency : Frequency)
      (state : ResolutionState)
      (hFrequency : world.roleFrequency role frequency) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.monitorFrequency target role none)
        (compileResolvedStep
          index
          fallbackDomain
          (.monitorFrequency target role none)
          (.frequencyChange { roleName := role, instructedFrequency := some frequency })
          (by simp [resolutionCompatible]))
        state
  | proceedDirect
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (fix : FixId)
      (point : PointId)
      (state : ResolutionState)
      (hPoint : world.fixPoint fix point) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.proceedDirect target fix)
        (compileResolvedStep
          index
          fallbackDomain
          (.proceedDirect target fix)
          (.directFix { fix := fix, point := point })
          (by simp [resolutionCompatible]))
        state
  | leaveHoldProceedDirect
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (fix : FixId)
      (point : PointId)
      (state : ResolutionState)
      (hPoint : world.fixPoint fix point) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.leaveHoldProceedDirect target fix)
        (compileResolvedStep
          index
          fallbackDomain
          (.leaveHoldProceedDirect target fix)
          (.directFix { fix := fix, point := point })
          (by simp [resolutionCompatible]))
        state
  | whenAbleProceedDirect
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (fix : FixId)
      (point : PointId)
      (state : ResolutionState)
      (hPoint : world.fixPoint fix point) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.whenAbleProceedDirect target fix)
        (compileResolvedStep
          index
          fallbackDomain
          (.whenAbleProceedDirect target fix)
          (.directFix { fix := fix, point := point })
          (by simp [resolutionCompatible]))
        state
  | rejoinSidAt
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (fix : FixId)
      (point : PointId)
      (state : ResolutionState)
      (hPoint : world.fixPoint fix point) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.rejoinSidAt target fix)
        (compileResolvedStep
          index
          fallbackDomain
          (.rejoinSidAt target fix)
          (.directFix { fix := fix, point := point })
          (by simp [resolutionCompatible]))
        state
  | joinAirway
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (airway : AirwayId)
      (joinFix : FixId)
      (joinPoint : PointId)
      (state : ResolutionState)
      (hPoint : world.fixPoint joinFix joinPoint) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.joinAirway target airway joinFix)
        (compileResolvedStep
          index
          fallbackDomain
          (.joinAirway target airway joinFix)
          (.airwayJoin { airway := airway, joinFix := joinFix, joinPoint := joinPoint })
          (by simp [resolutionCompatible]))
        state
  | joinCircuit
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (direction : CircuitDirection)
      (joinType : JoinType)
      (runway : Option RunwayId)
      (circuit : CircuitProcedureId)
      (altitude : Level)
      (state : ResolutionState)
      (hCircuit : world.circuitJoin direction joinType runway circuit altitude) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.joinCircuit target direction joinType runway)
        (compileResolvedStep
          index
          fallbackDomain
          (.joinCircuit target direction joinType runway)
          (.circuitJoin { circuit := circuit, altitude := altitude })
          (by simp [resolutionCompatible]))
        state
  | plain
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (instruction : AtcInstruction)
      (state : ResolutionState)
      (hPlain : instructionNeedsSpecificResolution instruction = false) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        instruction
        (compileResolvedStep
          index
          fallbackDomain
          instruction
          .plain
          (by simp [resolutionCompatible, hPlain]))
        state

inductive ResolvesSteps :
    ResolutionWorld →
    ResolutionState →
    ClearanceDomain →
    List (Nat × AtcInstruction) →
    List ResolvedStep →
    ResolutionState →
    Prop
  | nil
      (world : ResolutionWorld)
      (state : ResolutionState)
      (fallbackDomain : ClearanceDomain) :
      ResolvesSteps world state fallbackDomain [] [] state
  | cons
      (world : ResolutionWorld)
      (state nextState finalState : ResolutionState)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (instruction : AtcInstruction)
      (step : ResolvedStep)
      (tail : List (Nat × AtcInstruction))
      (resolvedTail : List ResolvedStep)
      (hStep : ResolvesIndexedStep world state fallbackDomain index instruction step nextState)
      (hTail : ResolvesSteps world nextState fallbackDomain tail resolvedTail finalState) :
      ResolvesSteps
        world
        state
        fallbackDomain
        ((index, instruction) :: tail)
        (step :: resolvedTail)
        finalState

structure ResolvesClearance
    (world : ResolutionWorld)
    (initialState : ResolutionState)
    (clearance : StructuredClearance)
    (resolved : ResolvedClearance)
    (finalState : ResolutionState) : Prop where
  normalized : normalizeConditionalEnvelope clearance = .ok clearance
  sourceEq : resolved.source = clearance
  steps :
    ResolvesSteps
      world
      initialState
      clearance.domain
      (indexedSteps (structuredInstructions clearance))
      resolved.steps
      finalState

theorem resolvesIndexedStep_compatible
    {world : ResolutionWorld}
    {state nextState : ResolutionState}
    {fallbackDomain : ClearanceDomain}
    {index : Nat}
    {instruction : AtcInstruction}
    {step : ResolvedStep} :
    ResolvesIndexedStep world state fallbackDomain index instruction step nextState →
      step.isCompatible = true := by
  intro h
  cases h <;> simp [ResolvedStep.isCompatible, compileResolvedStep_matches]

theorem resolvesSteps_allCompatible
    {world : ResolutionWorld}
    {state finalState : ResolutionState}
    {fallbackDomain : ClearanceDomain}
    {indexed : List (Nat × AtcInstruction)}
    {steps : List ResolvedStep} :
    ResolvesSteps world state fallbackDomain indexed steps finalState →
      steps.all ResolvedStep.isCompatible = true := by
  intro h
  induction h with
  | nil =>
      simp
  | cons _ _ _ _ _ _ _ _ _ hStep hTail ih =>
      simp [resolvesIndexedStep_compatible hStep, ih]

theorem resolvesSteps_length
    {world : ResolutionWorld}
    {state finalState : ResolutionState}
    {fallbackDomain : ClearanceDomain}
    {indexed : List (Nat × AtcInstruction)}
    {steps : List ResolvedStep} :
    ResolvesSteps world state fallbackDomain indexed steps finalState →
      indexed.length = steps.length := by
  intro h
  induction h with
  | nil =>
      simp
  | cons _ _ _ _ _ _ _ _ _ _ hTail ih =>
      simp [ih]

theorem resolvesClearance_allStepsCompatible
    {world : ResolutionWorld}
    {initialState finalState : ResolutionState}
    {clearance : StructuredClearance}
    {resolved : ResolvedClearance}
    (hResolve : ResolvesClearance world initialState clearance resolved finalState) :
    resolved.allStepsCompatible = true := by
  rcases hResolve with ⟨_, _, hSteps⟩
  simpa [ResolvedClearance.allStepsCompatible] using resolvesSteps_allCompatible hSteps

theorem enumerateFrom_length
    {α : Type}
    (start : Nat)
    (steps : List α) :
    (enumerateFrom start steps).length = steps.length := by
  induction steps generalizing start with
  | nil =>
      simp [enumerateFrom]
  | cons head tail ih =>
      simp [enumerateFrom, ih]

theorem resolvesClearance_stepCount_matches
    {world : ResolutionWorld}
    {initialState finalState : ResolutionState}
    {clearance : StructuredClearance}
    {resolved : ResolvedClearance}
    (hResolve : ResolvesClearance world initialState clearance resolved finalState) :
    (structuredInstructions clearance).length = resolved.steps.length := by
  rcases hResolve with ⟨_, _, hSteps⟩
  have hLen := resolvesSteps_length hSteps
  simpa [indexedSteps, enumerateFrom_length] using hLen

theorem resolvesClearance_completedSteps_preserved
    {world : ResolutionWorld}
    {initialState finalState : ResolutionState}
    {clearance : StructuredClearance}
    {resolved : ResolvedClearance}
    (hResolve : ResolvesClearance world initialState clearance resolved finalState) :
    resolved.completedSteps =
      match clearance.content with
      | .single _ => {}
      | .compound content => content.completedSteps := by
  rcases hResolve with ⟨_, hSource, _⟩
  cases resolved
  simp [ResolvedClearance.completedSteps] at hSource ⊢
  cases clearance <;> cases hSource <;> rfl

def sampleConcreteResolutionWorld : ConcreteResolutionWorld :=
  { taxiRoutes :=
      [{ start := "A1", destination := "HP-27", path := ["A1", "A2", "HP-27"] }]
    runwayHoldingPoints :=
      [("27", "HP-27")]
    runwayCrossingPoints :=
      [("27", "X-27")]
    runwayFarEnds :=
      [("27", "RWY27-FAR")]
    fixPoints :=
      [("HOLD", "P-HOLD"), ("JOIN", "P-JOIN")]
    holdingPatterns :=
      [{ hold := .published "HOLD", pattern := "HOLD-PTN", fix := "HOLD" }]
    approaches :=
      [{ approachType := .ils, runway := "27", circlingRunway := none, approach := "ILS27" }]
    roleFrequencies :=
      [(.approach, "129.550")]
    circuitJoins :=
      [{ direction := .leftHand, joinType := .downwind, runway := some "27",
         circuit := "CIRCUIT-27-LH", altitude := .altitudeFeet 1200 }] }

def sampleResolutionWorld : ResolutionWorld :=
  sampleConcreteResolutionWorld.toResolutionWorld

def sampleResolvedRouteFrequencyFromWorld : ResolvedClearance :=
  { source :=
      { id := "CLR-ROUTE-FREQ"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .clearedTo "TEST123" "HOLD" (some (.viaSid "SID1"))
              , .contactFrequency "TEST123" .approach none ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 1
        status := .active
        condition := none }
    steps :=
      [ compileResolvedStep
          0
          .route
          (.clearedTo "TEST123" "HOLD" (some (.viaSid "SID1")))
          (.route { clearanceLimitFix := "HOLD", clearanceLimitPoint := "P-HOLD" })
          (by native_decide)
      , compileResolvedStep
          1
          .route
          (.contactFrequency "TEST123" .approach none)
          (.frequencyChange { roleName := .approach, instructedFrequency := some "129.550" })
          (by native_decide) ] }

example :
    ResolvesClearance
      sampleResolutionWorld
      {}
      sampleResolvedRouteFrequencyFromWorld.source
      sampleResolvedRouteFrequencyFromWorld
      {} := by
  refine ⟨?_, rfl, ?_⟩
  · simp [sampleResolvedRouteFrequencyFromWorld, normalizeConditionalEnvelope,
      anyWrappedConditionalStep, allStepsMayBeConditional]
  · apply ResolvesSteps.cons
    · apply ResolvesIndexedStep.route
      exact sampleConcreteResolutionWorld.mem_fixPoint (by simp [sampleConcreteResolutionWorld])
    · apply ResolvesSteps.cons
      · apply ResolvesIndexedStep.contactFrequencyImplicit
        exact sampleConcreteResolutionWorld.mem_roleFrequency (by simp [sampleConcreteResolutionWorld])
      · simpa using ResolvesSteps.nil sampleResolutionWorld {} .route

example :
    sampleResolutionWorld.fixPoint "HOLD" "P-HOLD" := by
  exact sampleConcreteResolutionWorld.mem_fixPoint (by simp [sampleConcreteResolutionWorld])

example :
    sampleResolutionWorld.roleFrequency .approach "129.550" := by
  exact sampleConcreteResolutionWorld.mem_roleFrequency (by simp [sampleConcreteResolutionWorld])

end Greenfield
end CertifiedAtc
