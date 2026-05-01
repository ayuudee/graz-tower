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
  runwayPath : RunwayId → List PointId → Prop
  runwayThreshold : RunwayId → PointId → Prop
  fixPoint : FixId → PointId → Prop
  airwayPoint : AirwayId → PointId → Prop
  routeSpecPoints : RouteSpec → List PointId → Prop
  routeClearancePoints : RouteSpec → FixId → List PointId → Prop
  holdingPatternFor : HoldSpec → HoldingPatternId → FixId → Prop
  holdingPatternLoop : HoldingPatternId → List PointId → Prop
  approachFor : ApproachType → RunwayId → Option RunwayId → ApproachId → Prop
  approachWaypoints : ApproachId → List PointId → Prop
  approachThreshold : ApproachId → PointId → Prop
  approachMissedApproach : ApproachId → HoldingPatternId → List PointId → Prop
  roleFrequency : RoleName → Frequency → Prop
  publishedHandoff :
    RoleName → RoleName → ResolvedPublishedHandoffAction → ResolvedPublishedHandoffPoint → Prop
  circuitJoin : CircuitDirection → JoinType → Option RunwayId → CircuitProcedureId → Level → Prop
  circuitJoinEntry : CircuitProcedureId → JoinType → PointId → List PointId → Prop
  circuitProcedurePoints : CircuitProcedureId → List PointId → Prop
  circuitExtendedDownwind : CircuitProcedureId → List PointId → List (List PointId) → Prop
  circuitOrbit : CircuitProcedureId → PointId → OrbitDirection → List PointId → Prop
  airspaceVolume : AirspaceVolumeId → List PointId → Prop

structure ConcreteTaxiRoute where
  start : PointId
  destination : PointId
  path : List PointId
  deriving DecidableEq, Repr

structure ConcreteRunwayBinding where
  runway : RunwayId
  path : List PointId
  threshold : PointId
  deriving DecidableEq, Repr

structure ConcreteHoldingPatternBinding where
  hold : HoldSpec
  pattern : HoldingPatternId
  fix : FixId
  deriving DecidableEq, Repr

structure ConcreteHoldingPatternLoopBinding where
  pattern : HoldingPatternId
  points : List PointId
  deriving DecidableEq, Repr

structure ConcreteApproachBinding where
  approachType : ApproachType
  runway : RunwayId
  circlingRunway : Option RunwayId
  approach : ApproachId
  deriving DecidableEq, Repr

structure ConcreteApproachWorldBinding where
  approach : ApproachId
  waypoints : List PointId
  threshold : PointId
  missedApproachHold : HoldingPatternId
  missedApproachPoints : List PointId
  deriving DecidableEq, Repr

structure ConcreteRouteClearanceBinding where
  route : RouteSpec
  clearanceLimit : FixId
  points : List PointId
  deriving DecidableEq, Repr

structure ConcreteAirwayPointBinding where
  airway : AirwayId
  point : PointId
  deriving DecidableEq, Repr

structure ConcreteCircuitJoinBinding where
  direction : CircuitDirection
  joinType : JoinType
  runway : Option RunwayId
  circuit : CircuitProcedureId
  altitude : Level
  deriving DecidableEq, Repr

structure ConcreteCircuitJoinPathBinding where
  circuit : CircuitProcedureId
  joinType : JoinType
  entryPoint : PointId
  entryPathPoints : List PointId
  circuitPoints : List PointId
  deriving DecidableEq, Repr

structure ConcreteCircuitExtendedDownwindBinding where
  circuit : CircuitProcedureId
  pathPoints : List PointId
  offRampPoints : List (List PointId)
  deriving DecidableEq, Repr

structure ConcreteCircuitOrbitBinding where
  circuit : CircuitProcedureId
  orbitPoint : PointId
  direction : OrbitDirection
  loopPoints : List PointId
  deriving DecidableEq, Repr

structure ConcreteAirspaceVolumeBinding where
  airspace : AirspaceVolumeId
  points : List PointId
  deriving DecidableEq, Repr

structure ConcretePublishedHandoffBinding where
  fromRole : RoleName
  toRole : RoleName
  action : ResolvedPublishedHandoffAction
  location : ResolvedPublishedHandoffPoint
  deriving DecidableEq, Repr

structure ConcreteResolutionWorld where
  taxiRoutes : List ConcreteTaxiRoute := []
  runwayHoldingPoints : List (RunwayId × PointId) := []
  runwayCrossingPoints : List (RunwayId × PointId) := []
  runwayFarEnds : List (RunwayId × PointId) := []
  runways : List ConcreteRunwayBinding := []
  fixPoints : List (FixId × PointId) := []
  airwayPoints : List ConcreteAirwayPointBinding := []
  routeClearanceBindings : List ConcreteRouteClearanceBinding := []
  holdingPatterns : List ConcreteHoldingPatternBinding := []
  holdingPatternLoops : List ConcreteHoldingPatternLoopBinding := []
  approaches : List ConcreteApproachBinding := []
  approachWorlds : List ConcreteApproachWorldBinding := []
  roleFrequencies : List (RoleName × Frequency) := []
  publishedHandoffs : List ConcretePublishedHandoffBinding := []
  circuitJoins : List ConcreteCircuitJoinBinding := []
  circuitJoinPaths : List ConcreteCircuitJoinPathBinding := []
  circuitExtendedDownwinds : List ConcreteCircuitExtendedDownwindBinding := []
  circuitOrbits : List ConcreteCircuitOrbitBinding := []
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
    runwayPath := fun runway path =>
      ∃ binding ∈ world.runways,
        binding.runway = runway ∧
        binding.path = path
    runwayThreshold := fun runway threshold =>
      ∃ binding ∈ world.runways,
        binding.runway = runway ∧
        binding.threshold = threshold
    fixPoint := fun fix point =>
      (fix, point) ∈ world.fixPoints
    airwayPoint := fun airway point =>
      { airway := airway, point := point } ∈ world.airwayPoints
    routeSpecPoints := fun _ _ => False
    routeClearancePoints := fun route clearanceLimit points =>
      { route := route, clearanceLimit := clearanceLimit, points := points } ∈ world.routeClearanceBindings
    holdingPatternFor := fun hold pattern fix =>
      { hold := hold, pattern := pattern, fix := fix } ∈ world.holdingPatterns
    holdingPatternLoop := fun pattern points =>
      { pattern := pattern, points := points } ∈ world.holdingPatternLoops
    approachFor := fun approachType runway circlingRunway approach =>
      { approachType := approachType, runway := runway, circlingRunway := circlingRunway, approach := approach } ∈ world.approaches
    approachWaypoints := fun approach points =>
      ∃ binding ∈ world.approachWorlds,
        binding.approach = approach ∧
        binding.waypoints = points
    approachThreshold := fun approach threshold =>
      ∃ binding ∈ world.approachWorlds,
        binding.approach = approach ∧
        binding.threshold = threshold
    approachMissedApproach := fun approach holdingPattern points =>
      ∃ binding ∈ world.approachWorlds,
        binding.approach = approach ∧
        binding.missedApproachHold = holdingPattern ∧
        binding.missedApproachPoints = points
    roleFrequency := fun role frequency =>
      (role, frequency) ∈ world.roleFrequencies
    publishedHandoff := fun fromRole toRole action location =>
      { fromRole := fromRole, toRole := toRole, action := action, location := location } ∈ world.publishedHandoffs
    circuitJoin := fun direction joinType runway circuit altitude =>
      { direction := direction, joinType := joinType, runway := runway, circuit := circuit, altitude := altitude } ∈ world.circuitJoins
    circuitJoinEntry := fun circuit joinType entryPoint entryPathPoints =>
      ∃ binding ∈ world.circuitJoinPaths,
        binding.circuit = circuit ∧
        binding.joinType = joinType ∧
        binding.entryPoint = entryPoint ∧
        binding.entryPathPoints = entryPathPoints
    circuitProcedurePoints := fun circuit points =>
      ∃ binding ∈ world.circuitJoinPaths,
        binding.circuit = circuit ∧
        binding.circuitPoints = points
    circuitExtendedDownwind := fun circuit pathPoints offRampPoints =>
      { circuit := circuit, pathPoints := pathPoints, offRampPoints := offRampPoints } ∈
        world.circuitExtendedDownwinds
    circuitOrbit := fun circuit orbitPoint direction loopPoints =>
      { circuit := circuit, orbitPoint := orbitPoint, direction := direction, loopPoints := loopPoints } ∈
        world.circuitOrbits
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

theorem ConcreteResolutionWorld.mem_runwayPath
    {world : ConcreteResolutionWorld}
    {runway : RunwayId}
    {path : List PointId}
    (hMem :
      ∃ binding ∈ world.runways,
        binding.runway = runway ∧
        binding.path = path) :
    world.toResolutionWorld.runwayPath runway path := by
  simpa [ConcreteResolutionWorld.toResolutionWorld] using hMem

theorem ConcreteResolutionWorld.mem_runwayThreshold
    {world : ConcreteResolutionWorld}
    {runway : RunwayId}
    {threshold : PointId}
    (hMem :
      ∃ binding ∈ world.runways,
        binding.runway = runway ∧
        binding.threshold = threshold) :
    world.toResolutionWorld.runwayThreshold runway threshold := by
  simpa [ConcreteResolutionWorld.toResolutionWorld] using hMem

theorem ConcreteResolutionWorld.mem_airwayPoint
    {world : ConcreteResolutionWorld}
    {airway : AirwayId}
    {point : PointId}
    (hMem : { airway := airway, point := point } ∈ world.airwayPoints) :
    world.toResolutionWorld.airwayPoint airway point := by
  simpa [ConcreteResolutionWorld.toResolutionWorld] using hMem

theorem ConcreteResolutionWorld.mem_roleFrequency
    {world : ConcreteResolutionWorld}
    {role : RoleName}
    {frequency : Frequency}
    (hMem : (role, frequency) ∈ world.roleFrequencies) :
    world.toResolutionWorld.roleFrequency role frequency := by
  simpa [ConcreteResolutionWorld.toResolutionWorld] using hMem

theorem ConcreteResolutionWorld.mem_publishedHandoff
    {world : ConcreteResolutionWorld}
    {fromRole toRole : RoleName}
    {action : ResolvedPublishedHandoffAction}
    {location : ResolvedPublishedHandoffPoint}
    (hMem :
      { fromRole := fromRole, toRole := toRole, action := action, location := location } ∈
        world.publishedHandoffs) :
    world.toResolutionWorld.publishedHandoff fromRole toRole action location := by
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

theorem ConcreteResolutionWorld.mem_circuitExtendedDownwind
    {world : ConcreteResolutionWorld}
    {circuit : CircuitProcedureId}
    {pathPoints : List PointId}
    {offRampPoints : List (List PointId)}
    (hMem :
      { circuit := circuit, pathPoints := pathPoints, offRampPoints := offRampPoints } ∈
        world.circuitExtendedDownwinds) :
    world.toResolutionWorld.circuitExtendedDownwind circuit pathPoints offRampPoints := by
  simpa [ConcreteResolutionWorld.toResolutionWorld] using hMem

theorem ConcreteResolutionWorld.mem_circuitOrbit
    {world : ConcreteResolutionWorld}
    {circuit : CircuitProcedureId}
    {orbitPoint : PointId}
    {direction : OrbitDirection}
    {loopPoints : List PointId}
    (hMem :
      { circuit := circuit, orbitPoint := orbitPoint, direction := direction, loopPoints := loopPoints } ∈
        world.circuitOrbits) :
    world.toResolutionWorld.circuitOrbit circuit orbitPoint direction loopPoints := by
  simpa [ConcreteResolutionWorld.toResolutionWorld] using hMem

theorem ConcreteResolutionWorld.mem_airspaceVolume
    {world : ConcreteResolutionWorld}
    {airspace : AirspaceVolumeId}
    {points : List PointId}
    (hMem : { airspace := airspace, points := points } ∈ world.airspaceVolumes) :
    world.toResolutionWorld.airspaceVolume airspace points := by
  simpa [ConcreteResolutionWorld.toResolutionWorld] using hMem

def turnedHeadingDegrees (current : Nat) (direction : TurnDirection) (degrees : Nat) : Nat :=
  let currentInt := Int.ofNat current
  let degreesInt := Int.ofNat degrees
  let raw :=
    match direction with
    | .left => currentInt - degreesInt
    | .right => currentInt + degreesInt
  Int.toNat ((((raw - 1) % 360) + 360) % 360 + 1)

structure ResolutionState where
  currentPoint : Option PointId := none
  currentHeadingDegreesMagnetic : Option Nat := none
  currentRole : Option RoleName := none
  currentFix : Option FixId := none
  currentRunway : Option RunwayId := none
  currentApproach : Option ApproachId := none
  currentCircuit : Option CircuitProcedureId := none
  onGround : Option Bool := none
  deriving DecidableEq, Repr

def publishedHandoffPointMatchesState
    (point : ResolvedPublishedHandoffPoint)
    (state : ResolutionState) : Prop :=
  match point with
  | .holdingPoint holdingPoint => state.currentPoint = some holdingPoint
  | .boundaryFix fix => state.currentFix = some fix
  | .airborne => state.onGround = some false

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
        { currentPoint := some farEndPoint
          currentRunway := some runway }
  | lineUpAndWait
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (runway : RunwayId)
      (thresholdPoint : PointId)
      (pathPoints : List PointId)
      (state : ResolutionState)
      (hPath : world.runwayPath runway pathPoints)
      (hThreshold : world.runwayThreshold runway thresholdPoint) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.lineUpAndWait target runway)
        (compileResolvedStep
          index
          fallbackDomain
          (.lineUpAndWait target runway)
          (.runwayOperation
            { runway := runway
              thresholdPoint := thresholdPoint
              pathPoints := pathPoints })
          (by simp [resolutionCompatible]))
        { state with currentRunway := some runway }
  | clearedForTakeoff
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (runway : RunwayId)
      (thresholdPoint : PointId)
      (pathPoints : List PointId)
      (state : ResolutionState)
      (hPath : world.runwayPath runway pathPoints)
      (hThreshold : world.runwayThreshold runway thresholdPoint) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.clearedForTakeoff target runway)
        (compileResolvedStep
          index
          fallbackDomain
          (.clearedForTakeoff target runway)
          (.runwayOperation
            { runway := runway
              thresholdPoint := thresholdPoint
              pathPoints := pathPoints })
          (by simp [resolutionCompatible]))
        { state with currentRunway := some runway }
  | clearedToLand
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (runway : RunwayId)
      (thresholdPoint : PointId)
      (pathPoints : List PointId)
      (state : ResolutionState)
      (hPath : world.runwayPath runway pathPoints)
      (hThreshold : world.runwayThreshold runway thresholdPoint) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.clearedToLand target runway)
        (compileResolvedStep
          index
          fallbackDomain
          (.clearedToLand target runway)
          (.runwayOperation
            { runway := runway
              thresholdPoint := thresholdPoint
              pathPoints := pathPoints })
          (by simp [resolutionCompatible]))
        { state with currentRunway := some runway }
  | clearedTouchAndGo
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (runway : RunwayId)
      (thresholdPoint : PointId)
      (pathPoints : List PointId)
      (state : ResolutionState)
      (hPath : world.runwayPath runway pathPoints)
      (hThreshold : world.runwayThreshold runway thresholdPoint) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.clearedTouchAndGo target runway)
        (compileResolvedStep
          index
          fallbackDomain
          (.clearedTouchAndGo target runway)
          (.runwayOperation
            { runway := runway
              thresholdPoint := thresholdPoint
              pathPoints := pathPoints })
          (by simp [resolutionCompatible]))
        { state with currentRunway := some runway }
  | clearedLowApproach
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (runway : RunwayId)
      (thresholdPoint : PointId)
      (pathPoints : List PointId)
      (state : ResolutionState)
      (hPath : world.runwayPath runway pathPoints)
      (hThreshold : world.runwayThreshold runway thresholdPoint) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.clearedLowApproach target runway)
        (compileResolvedStep
          index
          fallbackDomain
          (.clearedLowApproach target runway)
          (.runwayOperation
            { runway := runway
              thresholdPoint := thresholdPoint
              pathPoints := pathPoints })
          (by simp [resolutionCompatible]))
        { state with currentRunway := some runway }
  | goAround
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (runway : RunwayId)
      (thresholdPoint : PointId)
      (pathPoints : List PointId)
      (state : ResolutionState)
      (hCurrentRunway : state.currentRunway = some runway)
      (hPath : world.runwayPath runway pathPoints)
      (hThreshold : world.runwayThreshold runway thresholdPoint) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.goAround target)
        (compileResolvedStep
          index
          fallbackDomain
          (.goAround target)
          (.runwayOperation
            { runway := runway
              thresholdPoint := thresholdPoint
              pathPoints := pathPoints })
          (by simp [resolutionCompatible]))
        { state with currentRunway := some runway }
  | flyHeading
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (headingDegreesMagnetic : Nat)
      (state : ResolutionState) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.flyHeading target headingDegreesMagnetic)
        (compileResolvedStep
          index
          fallbackDomain
          (.flyHeading target headingDegreesMagnetic)
          (.vector
            { kind := .flyHeading
              targetHeadingDegreesMagnetic := some headingDegreesMagnetic })
          (by simp [resolutionCompatible]))
        { state with currentHeadingDegreesMagnetic := some headingDegreesMagnetic }
  | turnHeading
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (turnDirection : TurnDirection)
      (headingDegreesMagnetic : Nat)
      (state : ResolutionState) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.turnHeading target turnDirection headingDegreesMagnetic)
        (compileResolvedStep
          index
          fallbackDomain
          (.turnHeading target turnDirection headingDegreesMagnetic)
          (.vector
            { kind := .turnHeading
              targetHeadingDegreesMagnetic := some headingDegreesMagnetic
              turnDirection := some turnDirection })
          (by simp [resolutionCompatible]))
        { state with currentHeadingDegreesMagnetic := some headingDegreesMagnetic }
  | continuePresentHeading
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (headingDegreesMagnetic : Nat)
      (state : ResolutionState)
      (hHeading : state.currentHeadingDegreesMagnetic = some headingDegreesMagnetic) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.continuePresentHeading target)
        (compileResolvedStep
          index
          fallbackDomain
          (.continuePresentHeading target)
          (.vector
            { kind := .continuePresentHeading
              targetHeadingDegreesMagnetic := some headingDegreesMagnetic
              capturedHeadingDegreesMagnetic := some headingDegreesMagnetic })
          (by simp [resolutionCompatible]))
        { state with currentHeadingDegreesMagnetic := some headingDegreesMagnetic }
  | turnByDegrees
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (turnDirection : TurnDirection)
      (degrees : Nat)
      (headingDegreesMagnetic : Nat)
      (state : ResolutionState)
      (hHeading : state.currentHeadingDegreesMagnetic = some headingDegreesMagnetic) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.turnByDegrees target turnDirection degrees)
        (compileResolvedStep
          index
          fallbackDomain
          (.turnByDegrees target turnDirection degrees)
          (.vector
            { kind := .turnByDegrees
              targetHeadingDegreesMagnetic := some (turnedHeadingDegrees headingDegreesMagnetic turnDirection degrees)
              turnDirection := some turnDirection
              turnDegrees := some degrees
              capturedHeadingDegreesMagnetic := some headingDegreesMagnetic })
          (by simp [resolutionCompatible]))
        { state with
            currentHeadingDegreesMagnetic := some (turnedHeadingDegrees headingDegreesMagnetic turnDirection degrees) }
  | route
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (clearanceLimitFix : FixId)
      (route : Option RouteSpec)
      (point : PointId)
      (routePoints : List PointId)
      (clearanceLimitHoldingPattern : Option HoldingPatternId)
      (state : ResolutionState)
      (hPoint : world.fixPoint clearanceLimitFix point)
      (hRoutePoints :
        match route with
        | some routeSpec => world.routeClearancePoints routeSpec clearanceLimitFix routePoints
        | none => routePoints = [point])
      (hHolding :
        match clearanceLimitHoldingPattern with
        | some holdingPattern =>
            world.holdingPatternFor (.published clearanceLimitFix) holdingPattern clearanceLimitFix
        | none => True) :
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
          (.route
            { clearanceLimitFix := clearanceLimitFix
              clearanceLimitPoint := point
              routePoints := routePoints
              clearanceLimitHoldingPattern := clearanceLimitHoldingPattern })
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
      (fixPoint : PointId)
      (loopPoints : List PointId)
      (state : ResolutionState)
      (hPattern : world.holdingPatternFor hold pattern fix)
      (hFixPoint : world.fixPoint fix fixPoint)
      (hLoop : world.holdingPatternLoop pattern loopPoints) :
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
          (.holding
            { holdingPattern := pattern
              fix := fix
              fixPoint := fixPoint
              loopPoints := loopPoints })
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
      (waypointPoints : List PointId)
      (thresholdPoint : PointId)
      (missedApproachHoldingPattern : HoldingPatternId)
      (missedApproachPoints : List PointId)
      (state : ResolutionState)
      (hApproach : world.approachFor approachType runway circlingRunway approach)
      (hWaypoints : world.approachWaypoints approach waypointPoints)
      (hThreshold : world.approachThreshold approach thresholdPoint)
      (hMissed : world.approachMissedApproach approach missedApproachHoldingPattern missedApproachPoints) :
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
          (.approach
            { approach := approach
              runway := runway
              waypointPoints := waypointPoints
              thresholdPoint := thresholdPoint
              missedApproachPoints := missedApproachPoints
              missedApproachHoldingPattern := missedApproachHoldingPattern })
          (by simp [resolutionCompatible]))
        { state with currentApproach := some approach }
  | continueApproach
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (approach : ApproachId)
      (waypointPoints : List PointId)
      (thresholdPoint : PointId)
      (state : ResolutionState)
      (hCurrentApproach : state.currentApproach = some approach)
      (hWaypoints : world.approachWaypoints approach waypointPoints)
      (hThreshold : world.approachThreshold approach thresholdPoint) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.continueApproach target)
        (compileResolvedStep
          index
          fallbackDomain
          (.continueApproach target)
          (.continueApproach
            { approach := approach
              waypointPoints := waypointPoints
              thresholdPoint := thresholdPoint })
          (by simp [resolutionCompatible]))
        { state with currentApproach := some approach }
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
  | contactFrequencyExplicitPublished
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (fromRole role : RoleName)
      (frequency : Frequency)
      (handoffPoint : ResolvedPublishedHandoffPoint)
      (state : ResolutionState)
      (hRole : state.currentRole = some fromRole)
      (hHandoff : world.publishedHandoff fromRole role .contact handoffPoint) :
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
          (.frequencyChange
            { roleName := role
              instructedFrequency := some frequency
              publishedHandoff :=
                some
                  { fromRole := fromRole
                    toRole := role
                    action := .contact
                    location := handoffPoint } })
          (by simp [resolutionCompatible]))
        state
  | contactFrequencyImplicitPublished
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (fromRole role : RoleName)
      (frequency : Frequency)
      (handoffPoint : ResolvedPublishedHandoffPoint)
      (state : ResolutionState)
      (hFrequency : world.roleFrequency role frequency)
      (hRole : state.currentRole = some fromRole)
      (hHandoff : world.publishedHandoff fromRole role .contact handoffPoint) :
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
          (.frequencyChange
            { roleName := role
              instructedFrequency := some frequency
              publishedHandoff :=
                some
                  { fromRole := fromRole
                    toRole := role
                    action := .contact
                    location := handoffPoint } })
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
  | monitorFrequencyExplicitPublished
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (fromRole role : RoleName)
      (frequency : Frequency)
      (handoffPoint : ResolvedPublishedHandoffPoint)
      (state : ResolutionState)
      (hRole : state.currentRole = some fromRole)
      (hHandoff : world.publishedHandoff fromRole role .monitor handoffPoint) :
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
          (.frequencyChange
            { roleName := role
              instructedFrequency := some frequency
              publishedHandoff :=
                some
                  { fromRole := fromRole
                    toRole := role
                    action := .monitor
                    location := handoffPoint } })
          (by simp [resolutionCompatible]))
        state
  | monitorFrequencyImplicitPublished
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (fromRole role : RoleName)
      (frequency : Frequency)
      (handoffPoint : ResolvedPublishedHandoffPoint)
      (state : ResolutionState)
      (hFrequency : world.roleFrequency role frequency)
      (hRole : state.currentRole = some fromRole)
      (hHandoff : world.publishedHandoff fromRole role .monitor handoffPoint) :
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
          (.frequencyChange
            { roleName := role
              instructedFrequency := some frequency
              publishedHandoff :=
                some
                  { fromRole := fromRole
                    toRole := role
                    action := .monitor
                    location := handoffPoint } })
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
      (hPoint : world.fixPoint joinFix joinPoint)
      (hAirway : world.airwayPoint airway joinPoint) :
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
      (entryPoint : PointId)
      (entryPathPoints : List PointId)
      (circuitPoints : List PointId)
      (state : ResolutionState)
      (hCircuit : world.circuitJoin direction joinType runway circuit altitude)
      (hEntry : world.circuitJoinEntry circuit joinType entryPoint entryPathPoints)
      (hPoints : world.circuitProcedurePoints circuit circuitPoints) :
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
          (.circuitJoin
            { circuit := circuit
              altitude := altitude
              entryPoint := entryPoint
              entryPathPoints := entryPathPoints
              circuitPoints := circuitPoints })
          (by simp [resolutionCompatible]))
        { state with currentCircuit := some circuit }
  | extendDownwind
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (circuit : CircuitProcedureId)
      (extendedPathPoints : List PointId)
      (offRampPoints : List (List PointId))
      (state : ResolutionState)
      (hCurrentCircuit : state.currentCircuit = some circuit)
      (hExtendedDownwind :
        world.circuitExtendedDownwind circuit extendedPathPoints offRampPoints) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.extendDownwind target)
        (compileResolvedStep
          index
          fallbackDomain
          (.extendDownwind target)
          (.extendDownwind
            { circuit := circuit
              extendedPathPoints := extendedPathPoints
              offRampPoints := offRampPoints })
          (by simp [resolutionCompatible]))
        { state with currentCircuit := some circuit }
  | orbit
      (world : ResolutionWorld)
      (fallbackDomain : ClearanceDomain)
      (index : Nat)
      (target : AircraftId)
      (direction : OrbitDirection)
      (circuit : CircuitProcedureId)
      (orbitPoint : PointId)
      (loopPoints : List PointId)
      (state : ResolutionState)
      (hCurrentCircuit : state.currentCircuit = some circuit)
      (hCurrentPoint : state.currentPoint = some orbitPoint)
      (hOrbit : world.circuitOrbit circuit orbitPoint direction loopPoints) :
      ResolvesIndexedStep
        world
        state
        fallbackDomain
        index
        (.orbit target direction)
        (compileResolvedStep
          index
          fallbackDomain
          (.orbit target direction)
          (.orbit
            { circuit := circuit
              orbitPoint := orbitPoint
              direction := direction
              loopPoints := loopPoints })
          (by simp [resolutionCompatible]))
        { state with currentPoint := some orbitPoint, currentCircuit := some circuit }
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
    routeClearanceBindings :=
      [{ route := .viaSid "SID1", clearanceLimit := "HOLD", points := ["RWY27", "SID-EXIT", "P-HOLD"] }]
    holdingPatterns :=
      [{ hold := .published "HOLD", pattern := "HOLD-PTN", fix := "HOLD" }]
    holdingPatternLoops :=
      [{ pattern := "HOLD-PTN", points := ["P-HOLD", "P-HOLD-1", "P-HOLD-2", "P-HOLD"] }]
    approaches :=
      [{ approachType := .ils, runway := "27", circlingRunway := none, approach := "ILS27" }]
    approachWorlds :=
      [{ approach := "ILS27"
         waypoints := ["IAF-27", "FAF-27", "RWY27"]
         threshold := "RWY27"
         missedApproachHold := "HOLD-PTN"
         missedApproachPoints := ["RWY27", "MA-1", "P-HOLD"] }]
    roleFrequencies :=
      [(.approach, "129.550")]
    circuitJoins :=
      [{ direction := .leftHand, joinType := .downwind, runway := some "27",
         circuit := "CIRCUIT-27-LH", altitude := .altitudeFeet 1200 }]
    circuitJoinPaths :=
      [{ circuit := "CIRCUIT-27-LH"
         joinType := .downwind
         entryPoint := "CROSSWIND"
         entryPathPoints := ["JOIN-ENTRY", "CROSSWIND"]
         circuitPoints := ["RWY27", "UPWIND", "CROSSWIND", "DOWNWIND", "BASE", "RWY27"] }] }

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
          (.route
            { clearanceLimitFix := "HOLD"
              clearanceLimitPoint := "P-HOLD"
              routePoints := ["RWY27", "SID-EXIT", "P-HOLD"]
              clearanceLimitHoldingPattern := some "HOLD-PTN" })
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
      · exact sampleConcreteResolutionWorld.mem_fixPoint (by simp [sampleConcreteResolutionWorld])
      · simp [sampleResolutionWorld, sampleConcreteResolutionWorld, ConcreteResolutionWorld.toResolutionWorld]
      · simp [sampleResolutionWorld, sampleConcreteResolutionWorld, ConcreteResolutionWorld.toResolutionWorld]
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
