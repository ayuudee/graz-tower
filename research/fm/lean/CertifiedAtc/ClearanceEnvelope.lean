import CertifiedAtc.Interfaces

namespace CertifiedAtc

abbrev PointId := String
abbrev TaxiwayId := String
abbrev CircuitProcedureId := String
abbrev RoleName := String
abbrev FixId := String
abbrev SidId := String
abbrev AirwayId := String
abbrev StarId := String
abbrev VfrRouteId := String
abbrev HoldingPatternId := String
abbrev ApproachId := String

inductive ProcedureRef
  | viaSid (sid : SidId)
  | viaAirway (airway : AirwayId)
  | viaStar (star : StarId)
  | viaRoute (route : VfrRouteId)
  | direct (fix : FixId)
  deriving DecidableEq, Repr

inductive ClearanceInstruction
  | startupApproved (target : EntityId)
  | holdPosition (target : EntityId)
  | holdShortOf (target : EntityId) (runway : RunwayId)
  | taxiVia (target : EntityId) (taxiways : List TaxiwayId) (destination : PointId)
  | crossRunway (target : EntityId) (runway : RunwayId)
  | backtrackRunway (target : EntityId) (runway : RunwayId)
  | lineUpAndWait (target : EntityId) (runway : RunwayId)
  | clearedForTakeoff (target : EntityId) (runway : RunwayId)
  | clearedToLand (target : EntityId) (runway : RunwayId)
  | clearedTouchAndGo (target : EntityId) (runway : RunwayId)
  | goAround (target : EntityId)
  | joinCircuit (target : EntityId) (circuit : CircuitProcedureId) (joinType : JoinType)
  | orbit (target : EntityId) (direction : OrbitDirection)
  | extendDownwind (target : EntityId)
  | reportDownwind (target : EntityId)
  | reportFinal (target : EntityId)
  | continueApproach (target : EntityId)
  | proceed (target : EntityId)
  | contactFrequency (target : EntityId) (role : RoleName) (frequency : Frequency)
  | monitorFrequency (target : EntityId) (role : RoleName) (frequency : Frequency)
  | clearedTo (target : EntityId) (destination : AerodromeId) (via : ProcedureRef)
      (limit : Option FixId := none) (altitude : Int)
  | reduceSpeedTo (target : EntityId) (maxSpeedKt : Nat)
  | climbTo (target : EntityId) (altitude : Int)
  | descendTo (target : EntityId) (altitude : Int)
  | clearedApproach (target : EntityId) (approach : ApproachId)
  | squawkCode (target : EntityId) (code : Nat)
  | crossControlledAirspace (target : EntityId) (airspaceClass : AirspaceClass)
  | holdAt (target : EntityId) (hold : HoldingPatternId)
  deriving DecidableEq, Repr

def clearanceInstructionTarget : ClearanceInstruction → EntityId
  | .startupApproved target => target
  | .holdPosition target => target
  | .holdShortOf target _ => target
  | .taxiVia target _ _ => target
  | .crossRunway target _ => target
  | .backtrackRunway target _ => target
  | .lineUpAndWait target _ => target
  | .clearedForTakeoff target _ => target
  | .clearedToLand target _ => target
  | .clearedTouchAndGo target _ => target
  | .goAround target => target
  | .joinCircuit target _ _ => target
  | .orbit target _ => target
  | .extendDownwind target => target
  | .reportDownwind target => target
  | .reportFinal target => target
  | .continueApproach target => target
  | .proceed target => target
  | .contactFrequency target _ _ => target
  | .monitorFrequency target _ _ => target
  | .clearedTo target _ _ _ _ => target
  | .reduceSpeedTo target _ => target
  | .climbTo target _ => target
  | .descendTo target _ => target
  | .clearedApproach target _ => target
  | .squawkCode target _ => target
  | .crossControlledAirspace target _ => target
  | .holdAt target _ => target

inductive InstructionStepTiming
  | sequential
  | immediate
  | standalone
  deriving DecidableEq, Repr

def instructionStepTiming : ClearanceInstruction → InstructionStepTiming
  | .taxiVia _ _ _ => .sequential
  | .crossRunway _ _ => .sequential
  | .holdShortOf _ _ => .sequential
  | .backtrackRunway _ _ => .sequential
  | .lineUpAndWait _ _ => .sequential
  | .climbTo _ _ => .immediate
  | .descendTo _ _ => .immediate
  | .reduceSpeedTo _ _ => .immediate
  | .squawkCode _ _ => .immediate
  | .contactFrequency _ _ _ => .immediate
  | .monitorFrequency _ _ _ => .immediate
  | _ => .standalone

inductive CompoundTiming
  | sequential
  | immediate
  deriving DecidableEq, Repr

def instructionCompoundTiming? (instruction : ClearanceInstruction) :
    Option CompoundTiming :=
  match instructionStepTiming instruction with
  | .sequential => some .sequential
  | .immediate => some .immediate
  | .standalone => none

theorem instructionCompoundTiming?_none_iff_standalone
    (instruction : ClearanceInstruction) :
    instructionCompoundTiming? instruction = none ↔
      instructionStepTiming instruction = .standalone := by
  cases hTiming : instructionStepTiming instruction <;>
    simp [instructionCompoundTiming?, hTiming]

theorem instructionCompoundTiming?_isSome_iff_not_standalone
    (instruction : ClearanceInstruction) :
    (∃ timing, instructionCompoundTiming? instruction = some timing) ↔
      instructionStepTiming instruction ≠ .standalone := by
  cases hTiming : instructionStepTiming instruction <;>
    simp [instructionCompoundTiming?, hTiming]

structure CompoundClearanceContent where
  immediateSteps : List ClearanceInstruction := []
  sequentialSteps : List ClearanceInstruction := []
  nextSequential : Nat := 0
  deriving DecidableEq, Repr

def CompoundClearanceWellFormed (content : CompoundClearanceContent) : Prop :=
  (∀ instruction ∈ content.immediateSteps,
      instructionCompoundTiming? instruction = some .immediate) ∧
    (∀ instruction ∈ content.sequentialSteps,
      instructionCompoundTiming? instruction = some .sequential) ∧
    content.nextSequential ≤ content.sequentialSteps.length

def listIndex? {α : Type} : List α → Nat → Option α
  | [], _ => none
  | head :: _, 0 => some head
  | _ :: tail, n + 1 => listIndex? tail n

theorem listIndex?_eq_none_of_length_le {α : Type} :
    ∀ (items : List α) (index : Nat), items.length ≤ index → listIndex? items index = none
  | [], _, _ => rfl
  | _ :: _, 0, h => by cases h
  | _ :: tail, index + 1, h => by
      simpa [listIndex?] using
        listIndex?_eq_none_of_length_le tail index (Nat.le_of_succ_le_succ h)

theorem listIndex?_eq_some_implies_mem {α : Type} :
    ∀ {items : List α} {index : Nat} {value : α},
      listIndex? items index = some value → value ∈ items
  | [], _, _, h => by simp [listIndex?] at h
  | head :: _, 0, value, h => by
      simp [listIndex?] at h
      cases h
      simp
  | _ :: tail, index + 1, value, h => by
      simp [listIndex?] at h
      simp [listIndex?_eq_some_implies_mem h]

def activeSequentialStep? (content : CompoundClearanceContent) : Option ClearanceInstruction :=
  listIndex? content.sequentialSteps content.nextSequential

def frontierInstructions (content : CompoundClearanceContent) : List ClearanceInstruction :=
  match activeSequentialStep? content with
  | none => content.immediateSteps
  | some step => content.immediateSteps ++ [step]

def advanceSequentialStep
    (content : CompoundClearanceContent) : CompoundClearanceContent :=
  if _h : content.nextSequential < content.sequentialSteps.length then
    { content with nextSequential := content.nextSequential + 1 }
  else
    content

inductive StepCompletionObservation
  | reachedPoint (point : PointId)
  | crossedRunway (runway : RunwayId)
  | holdingShortOf (runway : RunwayId)
  | backtrackCompleted (runway : RunwayId)
  | linedUpOnRunway (runway : RunwayId)
  deriving DecidableEq, Repr

def instructionSatisfiedByObservation :
    ClearanceInstruction → StepCompletionObservation → Bool
  | .taxiVia _ _ destination, .reachedPoint point => decide (point = destination)
  | .crossRunway _ runway, .crossedRunway observed => decide (observed = runway)
  | .holdShortOf _ runway, .holdingShortOf observed => decide (observed = runway)
  | .backtrackRunway _ runway, .backtrackCompleted observed => decide (observed = runway)
  | .lineUpAndWait _ runway, .linedUpOnRunway observed => decide (observed = runway)
  | _, _ => false

def advanceSequentialStepOnObservation
    (content : CompoundClearanceContent)
    (observation : StepCompletionObservation) : CompoundClearanceContent :=
  match activeSequentialStep? content with
  | none => content
  | some step =>
      if instructionSatisfiedByObservation step observation then
        advanceSequentialStep content
      else
        content

inductive ClearanceContentView
  | single (instruction : ClearanceInstruction)
  | compound (content : CompoundClearanceContent)
  deriving DecidableEq, Repr

structure StructuredClearance where
  id : ClearanceId
  aircraft : EntityId
  content : ClearanceContentView
  issuedBy : AgentId
  issuedAt : Nat
  status : ClearanceStatus
  dependsOn : List ClearanceId := []
  deriving DecidableEq, Repr

structure CompileRunwayExitView where
  point : PointId
  taxiway : TaxiwayId
  deriving DecidableEq, Repr

structure CompileRunwayView where
  id : RunwayId
  path : List PointId
  pathSegments : List SurfaceSegmentId
  threshold : PointId
  departureEnd : PointId
  exits : List CompileRunwayExitView := []
  deriving DecidableEq, Repr

structure CompileHoldingPointView where
  runway : RunwayId
  point : PointId
  entrySegment : Option SurfaceSegmentId := none
  deriving DecidableEq, Repr

structure CompileTaxiwayView where
  id : TaxiwayId
  path : List PointId
  directedSegments : List SurfaceSegmentId
  holdingPoints : List CompileHoldingPointView := []
  bidirectional : Bool := true
  deriving DecidableEq, Repr

structure CompileCircuitLegView where
  name : String
  startPoint : PointId
  to : PointId
  edges : List AirEdgeId
  deriving DecidableEq, Repr

structure CompileReportingPointView where
  legName : String
  point : PointId
  deriving DecidableEq, Repr

structure CompileOffRampView where
  path : List PointId
  edges : List AirEdgeId
  deriving DecidableEq, Repr

structure CompileExtendedDownwindView where
  path : List PointId
  edges : List AirEdgeId
  offRamps : List CompileOffRampView := []
  deriving DecidableEq, Repr

structure CompileCircuitProcedureView where
  id : CircuitProcedureId
  runway : RunwayId
  direction : CircuitDirection
  legs : List CompileCircuitLegView
  altitudeFt : Int
  reportingPoints : List CompileReportingPointView := []
  extendedDownwind : Option CompileExtendedDownwindView := none
  deriving DecidableEq, Repr

structure CompileHoldingPatternView where
  id : HoldingPatternId
  fix : FixId
  fixPoint : PointId
  inboundCourseDegrees : Int
  turnDirection : OrbitDirection
  path : List PointId
  edges : List AirEdgeId
  altitudeFt : Int
  stackSeparationFt : Option Int := none
  deriving DecidableEq, Repr

structure CompileWaypointView where
  point : PointId
  name : Option String := none
  altitudeMinFt : Option Int := none
  altitudeMaxFt : Option Int := none
  speedMaxKt : Option Nat := none
  deriving DecidableEq, Repr

structure CompileMissedApproachView where
  path : List CompileWaypointView
  holdAt : Option HoldingPatternId := none
  deriving DecidableEq, Repr

structure CompileApproachView where
  id : ApproachId
  runway : RunwayId
  kind : String
  waypoints : List CompileWaypointView
  threshold : PointId
  missedApproach : CompileMissedApproachView
  deriving DecidableEq, Repr

structure CompileSidView where
  id : SidId
  runway : RunwayId
  waypoints : List CompileWaypointView
  connectsTo : Option AirwayId := none
  deriving DecidableEq, Repr

structure CompileAirwayView where
  id : AirwayId
  waypoints : List CompileWaypointView
  bidirectional : Bool
  deriving DecidableEq, Repr

structure CompileStarView where
  id : StarId
  waypoints : List CompileWaypointView
  connectsTo : Option ApproachId := none
  deriving DecidableEq, Repr

structure CompileVfrRouteView where
  id : VfrRouteId
  waypoints : List CompileWaypointView
  airspaceClass : AirspaceClass
  deriving DecidableEq, Repr

structure CompileFixView where
  id : FixId
  point : PointId
  name : String
  deriving DecidableEq, Repr

structure CompileRoleFrequencyView where
  role : RoleName
  frequency : Frequency
  deriving DecidableEq, Repr

structure CompileHandoffView where
  fromRole : RoleName
  toRole : RoleName
  atPoint : Option PointId := none
  deriving DecidableEq, Repr

inductive CompileAuthorityEntityType
  | runway
  | taxiway
  | stand
  | apron
  | circuitProcedure
  | holdingPattern
  | instrumentApproach
  | sid
  | star
  | airway
  | vfrRoute
  | fix
  | airspaceVolume
  | radioRole
  deriving DecidableEq, Repr

inductive CompileAuthorityOperation
  | startup
  | taxi
  | cross
  | backtrack
  | lineUp
  | takeoff
  | land
  | goAround
  | lowApproach
  | touchAndGo
  | circuit
  | sequence
  | hold
  | routeClearance
  | approachClearance
  | altitude
  | speed
  | squawk
  | contact
  | monitor
  | airspaceTransit
  | information
  deriving DecidableEq, Repr

structure CompileAuthorityGrantView where
  entityType : CompileAuthorityEntityType
  operation : CompileAuthorityOperation
  deriving DecidableEq, Repr

structure CompileRoleAuthorityView where
  role : RoleName
  grants : List CompileAuthorityGrantView := []
  deriving DecidableEq, Repr

structure CompileControllerRoleAssignmentView where
  controller : AgentId
  roles : List RoleName := []
  deriving DecidableEq, Repr

structure ClearanceCompileView where
  runways : List CompileRunwayView := []
  taxiways : List CompileTaxiwayView := []
  circuits : List CompileCircuitProcedureView := []
  holdingPatterns : List CompileHoldingPatternView := []
  approaches : List CompileApproachView := []
  sids : List CompileSidView := []
  airways : List CompileAirwayView := []
  stars : List CompileStarView := []
  vfrRoutes : List CompileVfrRouteView := []
  fixes : List CompileFixView := []
  roles : List CompileRoleFrequencyView := []
  handoffs : List CompileHandoffView := []
  roleAuthorities : List CompileRoleAuthorityView := []
  controllerRoles : List CompileControllerRoleAssignmentView := []
  deriving DecidableEq, Repr

structure CompiledInstructionPlan where
  instruction : ClearanceInstruction
  plan : CertificationPlan
  deriving DecidableEq, Repr

structure CompiledFrontier where
  immediate : List CompiledInstructionPlan := []
  sequential : Option CompiledInstructionPlan := none
  deriving DecidableEq, Repr

def compiledFrontierInstructions (frontier : CompiledFrontier) : List ClearanceInstruction :=
  frontier.immediate.map (fun entry => entry.instruction) ++
    match frontier.sequential with
    | none => []
    | some entry => [entry.instruction]

def clearanceContentFrontierInstructions : ClearanceContentView → List ClearanceInstruction
  | .single instruction => [instruction]
  | .compound content => frontierInstructions content

abbrev compile_clearance_instruction_sig : Type :=
  ClearanceCompileView → OrchestrationEnv → OrchestrationState →
    ClearanceInstruction → Except CompileError CertificationPlan

abbrev compile_frontier_sig : Type :=
  ClearanceCompileView → OrchestrationEnv → OrchestrationState →
    CompoundClearanceContent → Except CompileError CompiledFrontier

abbrev compile_clearance_instruction_as_issuer_sig : Type :=
  ClearanceCompileView → OrchestrationEnv → OrchestrationState →
    AgentId → ClearanceInstruction → Except CompileError CertificationPlan

abbrev compile_frontier_as_issuer_sig : Type :=
  ClearanceCompileView → OrchestrationEnv → OrchestrationState →
    AgentId → CompoundClearanceContent → Except CompileError CompiledFrontier

abbrev compile_clearance_content_frontier_sig : Type :=
  ClearanceCompileView → OrchestrationEnv → OrchestrationState →
    ClearanceContentView → Except CompileError CompiledFrontier

abbrev compile_clearance_content_frontier_as_issuer_sig : Type :=
  ClearanceCompileView → OrchestrationEnv → OrchestrationState →
    AgentId → ClearanceContentView → Except CompileError CompiledFrontier

abbrev compile_structured_clearance_frontier_sig : Type :=
  ClearanceCompileView → OrchestrationEnv → OrchestrationState →
    StructuredClearance → Except CompileError CompiledFrontier

abbrev compile_structured_clearance_frontier_as_issuer_sig : Type :=
  ClearanceCompileView → OrchestrationEnv → OrchestrationState →
    StructuredClearance → Except CompileError CompiledFrontier

def CompiledFrontierMatches (content : CompoundClearanceContent) (frontier : CompiledFrontier) : Prop :=
  frontierInstructions content = compiledFrontierInstructions frontier

def ClearanceContentFrontierMatches
    (content : ClearanceContentView) (frontier : CompiledFrontier) : Prop :=
  clearanceContentFrontierInstructions content = compiledFrontierInstructions frontier

def StructuredClearanceFrontierMatches
    (clearance : StructuredClearance) (frontier : CompiledFrontier) : Prop :=
  clearanceContentFrontierInstructions clearance.content =
    compiledFrontierInstructions frontier

def compiledClearanceProposer : AgentId := "clearance-envelope"

def mkCompiledCommandProposal (command : Command) : CommandProposal :=
  { proposer := compiledClearanceProposer
    command := command }

def findCompileTaxiway (view : ClearanceCompileView) (taxiwayId : TaxiwayId) :
    Option CompileTaxiwayView :=
  let rec go : List CompileTaxiwayView → Option CompileTaxiwayView
    | [] => none
    | taxiway :: tail =>
        if taxiway.id = taxiwayId then
          some taxiway
        else
          go tail
  go view.taxiways

def findCompileCircuit
    (view : ClearanceCompileView) (circuitId : CircuitProcedureId) :
    Option CompileCircuitProcedureView :=
  let rec go : List CompileCircuitProcedureView → Option CompileCircuitProcedureView
    | [] => none
    | circuit :: tail =>
        if circuit.id = circuitId then
          some circuit
        else
          go tail
  go view.circuits

def findCompileHoldingPattern
    (view : ClearanceCompileView) (holdId : HoldingPatternId) :
    Option CompileHoldingPatternView :=
  let rec go : List CompileHoldingPatternView → Option CompileHoldingPatternView
    | [] => none
    | hold :: tail =>
        if hold.id = holdId then
          some hold
        else
          go tail
  go view.holdingPatterns

def findCompileApproach
    (view : ClearanceCompileView) (approachId : ApproachId) :
    Option CompileApproachView :=
  let rec go : List CompileApproachView → Option CompileApproachView
    | [] => none
    | approach :: tail =>
        if approach.id = approachId then
          some approach
        else
          go tail
  go view.approaches

def findCompileSid (view : ClearanceCompileView) (sidId : SidId) :
    Option CompileSidView :=
  let rec go : List CompileSidView → Option CompileSidView
    | [] => none
    | sid :: tail =>
        if sid.id = sidId then
          some sid
        else
          go tail
  go view.sids

def findCompileAirway (view : ClearanceCompileView) (airwayId : AirwayId) :
    Option CompileAirwayView :=
  let rec go : List CompileAirwayView → Option CompileAirwayView
    | [] => none
    | airway :: tail =>
        if airway.id = airwayId then
          some airway
        else
          go tail
  go view.airways

def findCompileStar (view : ClearanceCompileView) (starId : StarId) :
    Option CompileStarView :=
  let rec go : List CompileStarView → Option CompileStarView
    | [] => none
    | star :: tail =>
        if star.id = starId then
          some star
        else
          go tail
  go view.stars

def findCompileVfrRoute
    (view : ClearanceCompileView) (routeId : VfrRouteId) :
    Option CompileVfrRouteView :=
  let rec go : List CompileVfrRouteView → Option CompileVfrRouteView
    | [] => none
    | route :: tail =>
        if route.id = routeId then
          some route
        else
          go tail
  go view.vfrRoutes

def findCompileFix (view : ClearanceCompileView) (fixId : FixId) :
    Option CompileFixView :=
  let rec go : List CompileFixView → Option CompileFixView
    | [] => none
    | fix :: tail =>
        if fix.id = fixId then
          some fix
        else
          go tail
  go view.fixes

def findCompileRoleAuthority
    (view : ClearanceCompileView) (role : RoleName) :
    Option CompileRoleAuthorityView :=
  let rec go : List CompileRoleAuthorityView → Option CompileRoleAuthorityView
    | [] => none
    | authority :: tail =>
        if authority.role = role then
          some authority
        else
          go tail
  go view.roleAuthorities

def findCompileControllerRoles
    (view : ClearanceCompileView) (controller : AgentId) :
    Option CompileControllerRoleAssignmentView :=
  let rec go : List CompileControllerRoleAssignmentView →
      Option CompileControllerRoleAssignmentView
    | [] => none
    | assignment :: tail =>
        if assignment.controller = controller then
          some assignment
        else
          go tail
  go view.controllerRoles

def roleAuthorityHasGrant
    (authority : CompileRoleAuthorityView)
    (grant : CompileAuthorityGrantView) : Bool :=
  authority.grants.any (fun existing => existing = grant)

def controllerHasAuthorityGrant
    (view : ClearanceCompileView)
    (controller : AgentId)
    (grant : CompileAuthorityGrantView) : Bool :=
  match findCompileControllerRoles view controller with
  | none => false
  | some assignment =>
      assignment.roles.any (fun role =>
        match findCompileRoleAuthority view role with
        | none => false
        | some authority => roleAuthorityHasGrant authority grant)

def instructionRequiredAuthorityGrant? :
    ClearanceInstruction → Option CompileAuthorityGrantView
  | .taxiVia _ _ _ =>
      some { entityType := .taxiway, operation := .taxi }
  | .crossRunway _ _ =>
      some { entityType := .runway, operation := .cross }
  | .backtrackRunway _ _ =>
      some { entityType := .runway, operation := .backtrack }
  | .lineUpAndWait _ _ =>
      some { entityType := .runway, operation := .lineUp }
  | .clearedForTakeoff _ _ =>
      some { entityType := .runway, operation := .takeoff }
  | .clearedToLand _ _ =>
      some { entityType := .runway, operation := .land }
  | .clearedTouchAndGo _ _ =>
      some { entityType := .runway, operation := .touchAndGo }
  | .goAround _ =>
      some { entityType := .runway, operation := .goAround }
  | .joinCircuit _ _ _ =>
      some { entityType := .circuitProcedure, operation := .circuit }
  | .contactFrequency _ _ _ =>
      some { entityType := .radioRole, operation := .contact }
  | .monitorFrequency _ _ _ =>
      some { entityType := .radioRole, operation := .monitor }
  | .clearedApproach _ _ =>
      some { entityType := .instrumentApproach, operation := .approachClearance }
  | .holdAt _ _ =>
      some { entityType := .holdingPattern, operation := .hold }
  | _ => none

def instructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : ClearanceInstruction) : Bool :=
  match instructionRequiredAuthorityGrant? instruction with
  | none => false
  | some grant => controllerHasAuthorityGrant view controller grant

def instructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List ClearanceInstruction → Bool
  | [] => true
  | instruction :: tail =>
      instructionIssuerAuthorized view controller instruction &&
        instructionsIssuerAuthorized view controller tail

def compoundClearanceInstructions
    (content : CompoundClearanceContent) : List ClearanceInstruction :=
  content.immediateSteps ++ content.sequentialSteps

def compoundClearanceIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (content : CompoundClearanceContent) : Bool :=
  instructionsIssuerAuthorized view controller (compoundClearanceInstructions content)

def clearanceContentInstructions : ClearanceContentView → List ClearanceInstruction
  | .single instruction => [instruction]
  | .compound content => compoundClearanceInstructions content

def clearanceContentIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) : ClearanceContentView → Bool
  | .single instruction =>
      instructionIssuerAuthorized view controller instruction
  | .compound content =>
      compoundClearanceIssuerAuthorized view controller content

def structuredClearanceIssuerAuthorized
    (view : ClearanceCompileView)
    (clearance : StructuredClearance) : Bool :=
  clearanceContentIssuerAuthorized view clearance.issuedBy clearance.content

def compoundClearanceFrontierIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (content : CompoundClearanceContent) : Bool :=
  instructionsIssuerAuthorized view controller (frontierInstructions content)

def clearanceContentFrontierIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) : ClearanceContentView → Bool
  | .single instruction =>
      instructionIssuerAuthorized view controller instruction
  | .compound content =>
      compoundClearanceFrontierIssuerAuthorized view controller content

def structuredClearanceFrontierIssuerAuthorized
    (view : ClearanceCompileView)
    (clearance : StructuredClearance) : Bool :=
  clearanceContentFrontierIssuerAuthorized view clearance.issuedBy clearance.content

theorem controllerHasAuthorityGrant_eq_false_of_no_controllerRoles
    {view : ClearanceCompileView}
    {controller : AgentId}
    {grant : CompileAuthorityGrantView}
    (hMissing : findCompileControllerRoles view controller = none) :
    controllerHasAuthorityGrant view controller grant = false := by
  simp [controllerHasAuthorityGrant, hMissing]

theorem instructionIssuerAuthorized_eq_false_of_unmapped
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instruction : ClearanceInstruction}
    (hGrant : instructionRequiredAuthorityGrant? instruction = none) :
    instructionIssuerAuthorized view controller instruction = false := by
  simp [instructionIssuerAuthorized, hGrant]

theorem instructionIssuerAuthorized_eq_controllerHasAuthorityGrant_of_mapped
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instruction : ClearanceInstruction}
    {grant : CompileAuthorityGrantView}
    (hGrant : instructionRequiredAuthorityGrant? instruction = some grant) :
    instructionIssuerAuthorized view controller instruction =
      controllerHasAuthorityGrant view controller grant := by
  simp [instructionIssuerAuthorized, hGrant]

theorem instructionIssuerAuthorized_eq_false_of_no_controllerRoles
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instruction : ClearanceInstruction}
    {grant : CompileAuthorityGrantView}
    (hGrant : instructionRequiredAuthorityGrant? instruction = some grant)
    (hMissing : findCompileControllerRoles view controller = none) :
    instructionIssuerAuthorized view controller instruction = false := by
  simp [instructionIssuerAuthorized, hGrant, controllerHasAuthorityGrant, hMissing]

theorem instructionsIssuerAuthorized_append
    (view : ClearanceCompileView)
    (controller : AgentId)
    (left right : List ClearanceInstruction) :
    instructionsIssuerAuthorized view controller (left ++ right) =
      (instructionsIssuerAuthorized view controller left &&
        instructionsIssuerAuthorized view controller right) := by
  induction left with
  | nil =>
      simp [instructionsIssuerAuthorized]
  | cons instruction tail ih =>
      simp [instructionsIssuerAuthorized, ih, Bool.and_assoc]

theorem compoundClearanceIssuerAuthorized_eq_split
    (view : ClearanceCompileView)
    (controller : AgentId)
    (content : CompoundClearanceContent) :
    compoundClearanceIssuerAuthorized view controller content =
      (instructionsIssuerAuthorized view controller content.immediateSteps &&
        instructionsIssuerAuthorized view controller content.sequentialSteps) := by
  simp [compoundClearanceIssuerAuthorized, compoundClearanceInstructions,
    instructionsIssuerAuthorized_append]

theorem structuredClearanceIssuerAuthorized_single_eq
    {view : ClearanceCompileView}
    {clearance : StructuredClearance}
    {instruction : ClearanceInstruction}
    (hContent : clearance.content = .single instruction) :
    structuredClearanceIssuerAuthorized view clearance =
      instructionIssuerAuthorized view clearance.issuedBy instruction := by
  simp [structuredClearanceIssuerAuthorized, clearanceContentIssuerAuthorized, hContent]

theorem structuredClearanceIssuerAuthorized_compound_eq
    {view : ClearanceCompileView}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    (hContent : clearance.content = .compound content) :
    structuredClearanceIssuerAuthorized view clearance =
      compoundClearanceIssuerAuthorized view clearance.issuedBy content := by
  simp [structuredClearanceIssuerAuthorized, clearanceContentIssuerAuthorized,
    compoundClearanceIssuerAuthorized, hContent]

theorem compoundClearanceFrontierIssuerAuthorized_eq_immediate_of_no_active
    {view : ClearanceCompileView}
    {controller : AgentId}
    {content : CompoundClearanceContent}
    (hNone : activeSequentialStep? content = none) :
    compoundClearanceFrontierIssuerAuthorized view controller content =
      instructionsIssuerAuthorized view controller content.immediateSteps := by
  simp [compoundClearanceFrontierIssuerAuthorized, frontierInstructions, hNone]

theorem compoundClearanceFrontierIssuerAuthorized_eq_immediate_and_active
    {view : ClearanceCompileView}
    {controller : AgentId}
    {content : CompoundClearanceContent}
    {step : ClearanceInstruction}
    (hActive : activeSequentialStep? content = some step) :
    compoundClearanceFrontierIssuerAuthorized view controller content =
      (instructionsIssuerAuthorized view controller content.immediateSteps &&
        instructionIssuerAuthorized view controller step) := by
  simp [compoundClearanceFrontierIssuerAuthorized, frontierInstructions, hActive,
    instructionsIssuerAuthorized_append, instructionsIssuerAuthorized]

theorem structuredClearanceFrontierIssuerAuthorized_single_eq
    {view : ClearanceCompileView}
    {clearance : StructuredClearance}
    {instruction : ClearanceInstruction}
    (hContent : clearance.content = .single instruction) :
    structuredClearanceFrontierIssuerAuthorized view clearance =
      instructionIssuerAuthorized view clearance.issuedBy instruction := by
  simp [structuredClearanceFrontierIssuerAuthorized,
    clearanceContentFrontierIssuerAuthorized, hContent]

theorem structuredClearanceFrontierIssuerAuthorized_compound_eq
    {view : ClearanceCompileView}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    (hContent : clearance.content = .compound content) :
    structuredClearanceFrontierIssuerAuthorized view clearance =
      compoundClearanceFrontierIssuerAuthorized view clearance.issuedBy content := by
  simp [structuredClearanceFrontierIssuerAuthorized,
    clearanceContentFrontierIssuerAuthorized, hContent]

theorem instructionsIssuerAuthorized_eq_true_of_mem
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instructions : List ClearanceInstruction}
    {instruction : ClearanceInstruction}
    (hAuthorized : instructionsIssuerAuthorized view controller instructions = true)
    (hMem : instruction ∈ instructions) :
    instructionIssuerAuthorized view controller instruction = true := by
  induction instructions with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp [instructionsIssuerAuthorized] at hAuthorized
      rcases hAuthorized with ⟨hHead, hTail⟩
      simp at hMem
      cases hMem with
      | inl hEq =>
          simpa [hEq] using hHead
      | inr hTailMem =>
          exact ih hTail hTailMem

theorem activeSequentialStep?_eq_some_implies_mem
    {content : CompoundClearanceContent}
    {step : ClearanceInstruction}
    (hActive : activeSequentialStep? content = some step) :
    step ∈ content.sequentialSteps := by
  exact listIndex?_eq_some_implies_mem hActive

theorem activeSequentialStep?_eq_some_implies_nextSequential_lt_length
    {content : CompoundClearanceContent}
    {step : ClearanceInstruction}
    (hActive : activeSequentialStep? content = some step) :
    content.nextSequential < content.sequentialSteps.length := by
  unfold activeSequentialStep? at hActive
  by_cases hLt : content.nextSequential < content.sequentialSteps.length
  · exact hLt
  · have hNone :
        listIndex? content.sequentialSteps content.nextSequential = none := by
      exact
        listIndex?_eq_none_of_length_le
          content.sequentialSteps content.nextSequential (Nat.le_of_not_gt hLt)
    rw [hNone] at hActive
    cases hActive

theorem compoundClearanceIssuerAuthorized_implies_frontier
    {view : ClearanceCompileView}
    {controller : AgentId}
    {content : CompoundClearanceContent}
    (hAuthorized : compoundClearanceIssuerAuthorized view controller content = true) :
    compoundClearanceFrontierIssuerAuthorized view controller content = true := by
  rw [compoundClearanceIssuerAuthorized_eq_split] at hAuthorized
  simp at hAuthorized
  rcases hAuthorized with ⟨hImmediate, hSequential⟩
  cases hActive : activeSequentialStep? content with
  | none =>
      rw [compoundClearanceFrontierIssuerAuthorized_eq_immediate_of_no_active hActive]
      exact hImmediate
  | some step =>
      rw [compoundClearanceFrontierIssuerAuthorized_eq_immediate_and_active hActive]
      have hStep :
          instructionIssuerAuthorized view controller step = true := by
        apply instructionsIssuerAuthorized_eq_true_of_mem
        · exact hSequential
        · exact activeSequentialStep?_eq_some_implies_mem hActive
      simp [hImmediate, hStep]

theorem structuredClearanceIssuerAuthorized_implies_frontier
    {view : ClearanceCompileView}
    {clearance : StructuredClearance}
    (hAuthorized : structuredClearanceIssuerAuthorized view clearance = true) :
    structuredClearanceFrontierIssuerAuthorized view clearance = true := by
  cases hContent : clearance.content with
  | single instruction =>
      rw [structuredClearanceIssuerAuthorized_single_eq hContent] at hAuthorized
      rw [structuredClearanceFrontierIssuerAuthorized_single_eq hContent]
      exact hAuthorized
  | compound content =>
      rw [structuredClearanceIssuerAuthorized_compound_eq hContent] at hAuthorized
      rw [structuredClearanceFrontierIssuerAuthorized_compound_eq hContent]
      exact compoundClearanceIssuerAuthorized_implies_frontier hAuthorized

def appendPointPath (left right : List PointId) : List PointId :=
  match left.reverse, right with
  | [], _ => right
  | _, [] => left
  | last :: _, first :: rest =>
      if last = first then
        left ++ rest
      else
        left ++ right

def compileTaxiwayPointPath
    (view : ClearanceCompileView) :
    List TaxiwayId → Except CompileError (List PointId)
  | [] => .error (.malformedCommand "taxiVia requires at least one taxiway")
  | taxiwayId :: tail =>
      match findCompileTaxiway view taxiwayId with
      | none => .error (.malformedCommand s!"unknown taxiway {taxiwayId}")
      | some taxiway =>
          match tail with
          | [] => .ok taxiway.path
          | _ =>
              match compileTaxiwayPointPath view tail with
              | .error err => .error err
              | .ok tailPath => .ok (appendPointPath taxiway.path tailPath)

def dropLeadingPoint : List PointId → List PointId
  | [] => []
  | _ :: tail => tail

def routePointsOfWaypoints (waypoints : List CompileWaypointView) : List AirNodeId :=
  waypoints.map (fun waypoint => waypoint.point)

def truncateRouteAtPoint
    (route : List AirNodeId) (point : AirNodeId) :
    Option (List AirNodeId) :=
  let rec go (remaining : List AirNodeId) (prefixRev : List AirNodeId) :
      Option (List AirNodeId) :=
    match remaining with
    | [] => none
    | node :: tail =>
        let prefixRev' := node :: prefixRev
        if node = point then
          some prefixRev'.reverse
        else
          go tail prefixRev'
  go route []

def applyRouteLimit
    (view : ClearanceCompileView)
    (route : List AirNodeId)
    (limit : Option FixId) :
    Except CompileError (List AirNodeId) :=
  match limit with
  | none => .ok route
  | some fixId =>
      match findCompileFix view fixId with
      | none => .error (.malformedCommand s!"unknown clearance-limit fix {fixId}")
      | some fix =>
          match truncateRouteAtPoint route fix.point with
          | none =>
              .error
                (.malformedCommand
                  s!"clearance-limit fix {fixId} is not on the compiled route")
          | some truncated => .ok truncated

def compileProcedureRoute
    (view : ClearanceCompileView)
    (via : ProcedureRef)
    (limit : Option FixId) :
    Except CompileError (List AirNodeId) :=
  let baseRoute : Except CompileError (List AirNodeId) :=
    match via with
    | .viaSid sidId =>
        match findCompileSid view sidId with
        | none => .error (.malformedCommand s!"unknown SID {sidId}")
        | some sid => .ok (routePointsOfWaypoints sid.waypoints)
    | .viaAirway airwayId =>
        match findCompileAirway view airwayId with
        | none => .error (.malformedCommand s!"unknown airway {airwayId}")
        | some airway => .ok (routePointsOfWaypoints airway.waypoints)
    | .viaStar starId =>
        match findCompileStar view starId with
        | none => .error (.malformedCommand s!"unknown STAR {starId}")
        | some star => .ok (routePointsOfWaypoints star.waypoints)
    | .viaRoute routeId =>
        match findCompileVfrRoute view routeId with
        | none => .error (.malformedCommand s!"unknown VFR route {routeId}")
        | some route => .ok (routePointsOfWaypoints route.waypoints)
    | .direct fixId =>
        match findCompileFix view fixId with
        | none => .error (.malformedCommand s!"unknown direct-to fix {fixId}")
        | some fix => .ok [fix.point]
  match baseRoute with
  | .error err => .error err
  | .ok [] => .error (.malformedCommand "compiled route is empty")
  | .ok route => applyRouteLimit view route limit

def compileClearanceCommand
    (view : ClearanceCompileView)
    (instruction : ClearanceInstruction) :
    Except CompileError Command :=
  match instruction with
  | .startupApproved target => .ok (.startupApproved target)
  | .holdPosition target => .ok (.holdPosition target)
  | .holdShortOf target runway => .ok (.holdShortOf target runway)
  | .taxiVia target taxiways destination =>
      match compileTaxiwayPointPath view taxiways with
      | .error err => .error err
      | .ok pointPath =>
          .ok (.taxiTo target (dropLeadingPoint pointPath) destination)
  | .crossRunway target runway => .ok (.crossRunway target runway)
  | .backtrackRunway target runway => .ok (.backtrackRunway target runway)
  | .lineUpAndWait target runway => .ok (.lineUpAndWait target runway)
  | .clearedForTakeoff target runway => .ok (.clearedForTakeoff target runway)
  | .clearedToLand target runway => .ok (.clearedToLand target runway)
  | .clearedTouchAndGo target runway => .ok (.clearedTouchAndGo target runway)
  | .goAround target => .ok (.goAround target)
  | .joinCircuit target circuitId joinType =>
      match findCompileCircuit view circuitId with
      | none => .error (.malformedCommand s!"unknown circuit procedure {circuitId}")
      | some circuit =>
          .ok (.joinCircuit target circuit.direction joinType (some circuit.runway))
  | .orbit target direction => .ok (.orbit target direction)
  | .extendDownwind target => .ok (.extendDownwind target)
  | .reportDownwind target => .ok (.reportDownwind target)
  | .reportFinal target => .ok (.reportFinal target)
  | .continueApproach target => .ok (.continueApproach target)
  | .proceed target => .ok (.proceed target)
  | .contactFrequency target role frequency =>
      .ok (.contactFrequency target role (some frequency))
  | .monitorFrequency target role frequency =>
      .ok (.monitorFrequency target role (some frequency))
  | .clearedTo target destination via limit altitude =>
      match compileProcedureRoute view via limit with
      | .error err => .error err
      | .ok route =>
          .ok (.clearedTo target destination route altitude)
  | .reduceSpeedTo target maxSpeedKt => .ok (.reduceSpeedTo target maxSpeedKt)
  | .climbTo target altitude => .ok (.climbTo target altitude)
  | .descendTo target altitude => .ok (.descendTo target altitude)
  | .clearedApproach target approachId =>
      match findCompileApproach view approachId with
      | none => .error (.malformedCommand s!"unknown approach {approachId}")
      | some approach =>
          .ok (.clearedApproach target approach.runway approach.kind)
  | .squawkCode target code => .ok (.squawkCode target code)
  | .crossControlledAirspace target airspaceClass =>
      .ok (.crossControlledAirspace target airspaceClass)
  | .holdAt target holdId =>
      match findCompileHoldingPattern view holdId with
      | none => .error (.malformedCommand s!"unknown holding pattern {holdId}")
      | some hold =>
          .ok (.holdAt target hold.fixPoint hold.turnDirection)

def compile_clearance_instruction : compile_clearance_instruction_sig := fun view env state instruction =>
  match compileClearanceCommand view instruction with
  | .error err => .error err
  | .ok command =>
      instantiate_plan env state (mkCompiledCommandProposal command)

def compile_clearance_instruction_as_issuer :
    compile_clearance_instruction_as_issuer_sig := fun view env state issuer instruction =>
  if instructionIssuerAuthorized view issuer instruction then
    compile_clearance_instruction view env state instruction
  else
    .error
      (.unauthorizedIssuer issuer
        s!"issuer {issuer} lacks proof-side authority for {repr instruction}")

def compileInstructionList
    (view : ClearanceCompileView)
    (env : OrchestrationEnv)
    (state : OrchestrationState) :
    List ClearanceInstruction → Except CompileError (List CompiledInstructionPlan)
  | [] => .ok []
  | instruction :: tail =>
      match compile_clearance_instruction view env state instruction with
      | .error err => .error err
      | .ok plan =>
          match compileInstructionList view env state tail with
          | .error err => .error err
          | .ok compiledTail =>
              .ok ({ instruction := instruction, plan := plan } :: compiledTail)

def compileInstructionListAsIssuer
    (view : ClearanceCompileView)
    (env : OrchestrationEnv)
    (state : OrchestrationState)
    (issuer : AgentId) :
    List ClearanceInstruction → Except CompileError (List CompiledInstructionPlan)
  | [] => .ok []
  | instruction :: tail =>
      match compile_clearance_instruction_as_issuer view env state issuer instruction with
      | .error err => .error err
      | .ok plan =>
          match compileInstructionListAsIssuer view env state issuer tail with
          | .error err => .error err
          | .ok compiledTail =>
              .ok ({ instruction := instruction, plan := plan } :: compiledTail)

def compile_frontier : compile_frontier_sig := fun view env state content =>
  match compileInstructionList view env state content.immediateSteps with
  | .error err => .error err
  | .ok immediate =>
      match activeSequentialStep? content with
      | none =>
          .ok { immediate := immediate
                sequential := none }
      | some step =>
          match compile_clearance_instruction view env state step with
          | .error err => .error err
          | .ok plan =>
              .ok
                { immediate := immediate
                  sequential := some { instruction := step, plan := plan } }

def compile_frontier_as_issuer : compile_frontier_as_issuer_sig := fun view env state issuer content =>
  match compileInstructionListAsIssuer view env state issuer content.immediateSteps with
  | .error err => .error err
  | .ok immediate =>
      match activeSequentialStep? content with
      | none =>
          .ok { immediate := immediate
                sequential := none }
      | some step =>
          match compile_clearance_instruction_as_issuer view env state issuer step with
          | .error err => .error err
          | .ok plan =>
              .ok
                { immediate := immediate
                  sequential := some { instruction := step, plan := plan } }

def compile_clearance_content_frontier :
    compile_clearance_content_frontier_sig := fun view env state content =>
  match content with
  | .single instruction =>
      match compile_clearance_instruction view env state instruction with
      | .error err => .error err
      | .ok plan =>
          .ok
            { immediate := [{ instruction := instruction, plan := plan }]
              sequential := none }
  | .compound compound =>
      compile_frontier view env state compound

def compile_clearance_content_frontier_as_issuer :
    compile_clearance_content_frontier_as_issuer_sig := fun view env state issuer content =>
  match content with
  | .single instruction =>
      match compile_clearance_instruction_as_issuer view env state issuer instruction with
      | .error err => .error err
      | .ok plan =>
          .ok
            { immediate := [{ instruction := instruction, plan := plan }]
              sequential := none }
  | .compound compound =>
      compile_frontier_as_issuer view env state issuer compound

def compile_structured_clearance_frontier :
    compile_structured_clearance_frontier_sig := fun view env state clearance =>
  compile_clearance_content_frontier view env state clearance.content

def compile_structured_clearance_frontier_as_issuer :
    compile_structured_clearance_frontier_as_issuer_sig := fun view env state clearance =>
  compile_clearance_content_frontier_as_issuer view env state clearance.issuedBy clearance.content

theorem frontierInstructions_eq_immediate_of_no_sequential
    {content : CompoundClearanceContent}
    (hNone : activeSequentialStep? content = none) :
    frontierInstructions content = content.immediateSteps := by
  simp [frontierInstructions, hNone]

theorem frontierInstructions_length_bound
    (content : CompoundClearanceContent) :
    content.immediateSteps.length ≤ (frontierInstructions content).length := by
  unfold frontierInstructions
  cases activeSequentialStep? content <;> simp

theorem advanceSequentialStep_never_retreats
    (content : CompoundClearanceContent) :
    content.nextSequential ≤ (advanceSequentialStep content).nextSequential := by
  by_cases h : content.nextSequential < content.sequentialSteps.length
  · simp [advanceSequentialStep, h]
  · simp [advanceSequentialStep, h]

theorem advanceSequentialStep_advances_by_at_most_one
    (content : CompoundClearanceContent) :
    (advanceSequentialStep content).nextSequential ≤ content.nextSequential + 1 := by
  by_cases h : content.nextSequential < content.sequentialSteps.length
  · simp [advanceSequentialStep, h]
  · simp [advanceSequentialStep, h]

theorem advanceSequentialStep_preserves_index_bound
    {content : CompoundClearanceContent}
    (hBound : content.nextSequential ≤ content.sequentialSteps.length) :
    (advanceSequentialStep content).nextSequential ≤ content.sequentialSteps.length := by
  by_cases h : content.nextSequential < content.sequentialSteps.length
  · simp [advanceSequentialStep, h]
    exact Nat.succ_le_of_lt h
  · simp [advanceSequentialStep, h]
    exact hBound

theorem advanceSequentialStep_preserves_immediateSteps
    (content : CompoundClearanceContent) :
    (advanceSequentialStep content).immediateSteps = content.immediateSteps := by
  by_cases h : content.nextSequential < content.sequentialSteps.length
  · simp [advanceSequentialStep, h]
  · simp [advanceSequentialStep, h]

theorem advanceSequentialStep_preserves_sequentialSteps
    (content : CompoundClearanceContent) :
    (advanceSequentialStep content).sequentialSteps = content.sequentialSteps := by
  by_cases h : content.nextSequential < content.sequentialSteps.length
  · simp [advanceSequentialStep, h]
  · simp [advanceSequentialStep, h]

theorem advanceSequentialStep_preservesWellFormed
    {content : CompoundClearanceContent} :
    CompoundClearanceWellFormed content →
      CompoundClearanceWellFormed (advanceSequentialStep content) := by
  intro hWellFormed
  rcases hWellFormed with ⟨hImmediate, hSequential, hBound⟩
  refine ⟨?_, ?_, ?_⟩
  · intro instruction hMem
    have hMem' : instruction ∈ content.immediateSteps := by
      simpa [advanceSequentialStep_preserves_immediateSteps content] using hMem
    exact hImmediate instruction hMem'
  · intro instruction hMem
    have hMem' : instruction ∈ content.sequentialSteps := by
      simpa [advanceSequentialStep_preserves_sequentialSteps content] using hMem
    exact hSequential instruction hMem'
  · simpa [advanceSequentialStep_preserves_sequentialSteps content] using
      advanceSequentialStep_preserves_index_bound hBound

theorem activeSequentialStep_after_advance_eq_nextIndex
    (content : CompoundClearanceContent) :
    activeSequentialStep? (advanceSequentialStep content) =
      listIndex? content.sequentialSteps (content.nextSequential + 1) := by
  by_cases h : content.nextSequential < content.sequentialSteps.length
  · simp [activeSequentialStep?, advanceSequentialStep, h]
  · have hCurrentNone :
      listIndex? content.sequentialSteps content.nextSequential = none := by
        exact
          listIndex?_eq_none_of_length_le
            content.sequentialSteps content.nextSequential (Nat.le_of_not_gt h)
    have hNextNone :
      listIndex? content.sequentialSteps (content.nextSequential + 1) = none := by
        exact
          listIndex?_eq_none_of_length_le
            content.sequentialSteps (content.nextSequential + 1)
            (Nat.le_trans (Nat.le_of_not_gt h) (Nat.le_succ _))
    simp [activeSequentialStep?, advanceSequentialStep, h, hCurrentNone, hNextNone]

theorem frontierInstructions_after_advance
    (content : CompoundClearanceContent) :
    frontierInstructions (advanceSequentialStep content) =
      match listIndex? content.sequentialSteps (content.nextSequential + 1) with
      | none => content.immediateSteps
      | some step => content.immediateSteps ++ [step] := by
  simp [frontierInstructions, activeSequentialStep_after_advance_eq_nextIndex,
    advanceSequentialStep_preserves_immediateSteps]

theorem advanceSequentialStep_nextSequential_eq_succ_of_active
    {content : CompoundClearanceContent}
    {step : ClearanceInstruction}
    (hActive : activeSequentialStep? content = some step) :
    (advanceSequentialStep content).nextSequential = content.nextSequential + 1 := by
  have hLt := activeSequentialStep?_eq_some_implies_nextSequential_lt_length hActive
  simp [advanceSequentialStep, hLt]

theorem advanceSequentialStepOnObservation_eq_self_of_no_active
    {content : CompoundClearanceContent}
    {observation : StepCompletionObservation}
    (hNone : activeSequentialStep? content = none) :
    advanceSequentialStepOnObservation content observation = content := by
  simp [advanceSequentialStepOnObservation, hNone]

theorem advanceSequentialStepOnObservation_eq_advance_of_satisfied
    {content : CompoundClearanceContent}
    {observation : StepCompletionObservation}
    {step : ClearanceInstruction}
    (hActive : activeSequentialStep? content = some step)
    (hSatisfied : instructionSatisfiedByObservation step observation = true) :
    advanceSequentialStepOnObservation content observation = advanceSequentialStep content := by
  simp [advanceSequentialStepOnObservation, hActive, hSatisfied]

theorem advanceSequentialStepOnObservation_eq_self_of_unsatisfied
    {content : CompoundClearanceContent}
    {observation : StepCompletionObservation}
    {step : ClearanceInstruction}
    (hActive : activeSequentialStep? content = some step)
    (hUnsatisfied : instructionSatisfiedByObservation step observation = false) :
    advanceSequentialStepOnObservation content observation = content := by
  simp [advanceSequentialStepOnObservation, hActive, hUnsatisfied]

theorem advanceSequentialStepOnObservation_nextSequential_eq_self_of_no_active
    {content : CompoundClearanceContent}
    {observation : StepCompletionObservation}
    (hNone : activeSequentialStep? content = none) :
    (advanceSequentialStepOnObservation content observation).nextSequential =
      content.nextSequential := by
  rw [advanceSequentialStepOnObservation_eq_self_of_no_active hNone]

theorem advanceSequentialStepOnObservation_nextSequential_eq_self_of_unsatisfied
    {content : CompoundClearanceContent}
    {observation : StepCompletionObservation}
    {step : ClearanceInstruction}
    (hActive : activeSequentialStep? content = some step)
    (hUnsatisfied : instructionSatisfiedByObservation step observation = false) :
    (advanceSequentialStepOnObservation content observation).nextSequential =
      content.nextSequential := by
  rw [advanceSequentialStepOnObservation_eq_self_of_unsatisfied hActive hUnsatisfied]

theorem advanceSequentialStepOnObservation_nextSequential_eq_succ_of_satisfied
    {content : CompoundClearanceContent}
    {observation : StepCompletionObservation}
    {step : ClearanceInstruction}
    (hActive : activeSequentialStep? content = some step)
    (hSatisfied : instructionSatisfiedByObservation step observation = true) :
    (advanceSequentialStepOnObservation content observation).nextSequential =
      content.nextSequential + 1 := by
  rw [advanceSequentialStepOnObservation_eq_advance_of_satisfied hActive hSatisfied]
  exact advanceSequentialStep_nextSequential_eq_succ_of_active hActive

theorem advanceSequentialStepOnObservation_never_retreats
    (content : CompoundClearanceContent)
    (observation : StepCompletionObservation) :
    content.nextSequential ≤ (advanceSequentialStepOnObservation content observation).nextSequential := by
  cases hActive : activeSequentialStep? content with
  | none =>
      rw [advanceSequentialStepOnObservation_eq_self_of_no_active hActive]
      exact Nat.le_refl _
  | some step =>
      cases hSatisfied : instructionSatisfiedByObservation step observation with
      | false =>
          rw [advanceSequentialStepOnObservation_eq_self_of_unsatisfied hActive hSatisfied]
          exact Nat.le_refl _
      | true =>
          rw [advanceSequentialStepOnObservation_eq_advance_of_satisfied hActive hSatisfied]
          exact advanceSequentialStep_never_retreats content

theorem advanceSequentialStepOnObservation_advances_by_at_most_one
    (content : CompoundClearanceContent)
    (observation : StepCompletionObservation) :
    (advanceSequentialStepOnObservation content observation).nextSequential ≤
      content.nextSequential + 1 := by
  cases hActive : activeSequentialStep? content with
  | none =>
      rw [advanceSequentialStepOnObservation_eq_self_of_no_active hActive]
      simp
  | some step =>
      cases hSatisfied : instructionSatisfiedByObservation step observation with
      | false =>
          rw [advanceSequentialStepOnObservation_eq_self_of_unsatisfied hActive hSatisfied]
          simp
      | true =>
          rw [advanceSequentialStepOnObservation_eq_advance_of_satisfied hActive hSatisfied]
          exact advanceSequentialStep_advances_by_at_most_one content

theorem advanceSequentialStepOnObservation_preservesWellFormed
    {content : CompoundClearanceContent}
    {observation : StepCompletionObservation} :
    CompoundClearanceWellFormed content →
      CompoundClearanceWellFormed (advanceSequentialStepOnObservation content observation) := by
  intro hWellFormed
  cases hActive : activeSequentialStep? content with
  | none =>
      rw [advanceSequentialStepOnObservation_eq_self_of_no_active hActive]
      exact hWellFormed
  | some step =>
      cases hSatisfied : instructionSatisfiedByObservation step observation with
      | false =>
          rw [advanceSequentialStepOnObservation_eq_self_of_unsatisfied hActive hSatisfied]
          exact hWellFormed
      | true =>
          rw [advanceSequentialStepOnObservation_eq_advance_of_satisfied hActive hSatisfied]
          exact advanceSequentialStep_preservesWellFormed hWellFormed

theorem activeSequentialStep_after_satisfied_observation_eq_nextIndex
    {content : CompoundClearanceContent}
    {observation : StepCompletionObservation}
    {step : ClearanceInstruction}
    (hActive : activeSequentialStep? content = some step)
    (hSatisfied : instructionSatisfiedByObservation step observation = true) :
    activeSequentialStep? (advanceSequentialStepOnObservation content observation) =
      listIndex? content.sequentialSteps (content.nextSequential + 1) := by
  rw [advanceSequentialStepOnObservation_eq_advance_of_satisfied hActive hSatisfied]
  exact activeSequentialStep_after_advance_eq_nextIndex content

theorem frontierInstructions_after_satisfied_observation
    {content : CompoundClearanceContent}
    {observation : StepCompletionObservation}
    {step : ClearanceInstruction}
    (hActive : activeSequentialStep? content = some step)
    (hSatisfied : instructionSatisfiedByObservation step observation = true) :
    frontierInstructions (advanceSequentialStepOnObservation content observation) =
      match listIndex? content.sequentialSteps (content.nextSequential + 1) with
      | none => content.immediateSteps
      | some nextStep => content.immediateSteps ++ [nextStep] := by
  rw [advanceSequentialStepOnObservation_eq_advance_of_satisfied hActive hSatisfied]
  exact frontierInstructions_after_advance content

theorem frontierInstructions_after_unsatisfied_observation
    {content : CompoundClearanceContent}
    {observation : StepCompletionObservation}
    {step : ClearanceInstruction}
    (hActive : activeSequentialStep? content = some step)
    (hUnsatisfied : instructionSatisfiedByObservation step observation = false) :
    frontierInstructions (advanceSequentialStepOnObservation content observation) =
      frontierInstructions content := by
  rw [advanceSequentialStepOnObservation_eq_self_of_unsatisfied hActive hUnsatisfied]

theorem frontierInstructions_after_no_active_observation
    {content : CompoundClearanceContent}
    {observation : StepCompletionObservation}
    (hNone : activeSequentialStep? content = none) :
    frontierInstructions (advanceSequentialStepOnObservation content observation) =
      frontierInstructions content := by
  rw [advanceSequentialStepOnObservation_eq_self_of_no_active hNone]

theorem advanceSequentialStepOnObservation_no_skipping
    (content : CompoundClearanceContent)
    (observation : StepCompletionObservation) :
    (advanceSequentialStepOnObservation content observation).nextSequential =
        content.nextSequential ∨
      (advanceSequentialStepOnObservation content observation).nextSequential =
        content.nextSequential + 1 := by
  cases hActive : activeSequentialStep? content with
  | none =>
      left
      exact advanceSequentialStepOnObservation_nextSequential_eq_self_of_no_active hActive
  | some step =>
      cases hSatisfied : instructionSatisfiedByObservation step observation with
      | false =>
          left
          exact
            advanceSequentialStepOnObservation_nextSequential_eq_self_of_unsatisfied
              hActive hSatisfied
      | true =>
          right
          exact
            advanceSequentialStepOnObservation_nextSequential_eq_succ_of_satisfied
              hActive hSatisfied

theorem advanceSequentialStepOnObservation_frontier_preserved_or_shifted
    (content : CompoundClearanceContent)
    (observation : StepCompletionObservation) :
    frontierInstructions (advanceSequentialStepOnObservation content observation) =
        frontierInstructions content ∨
      frontierInstructions (advanceSequentialStepOnObservation content observation) =
        match listIndex? content.sequentialSteps (content.nextSequential + 1) with
        | none => content.immediateSteps
        | some nextStep => content.immediateSteps ++ [nextStep] := by
  cases hActive : activeSequentialStep? content with
  | none =>
      left
      exact frontierInstructions_after_no_active_observation hActive
  | some step =>
      cases hSatisfied : instructionSatisfiedByObservation step observation with
      | false =>
          left
          exact frontierInstructions_after_unsatisfied_observation hActive hSatisfied
      | true =>
          right
          exact frontierInstructions_after_satisfied_observation hActive hSatisfied

theorem advanceSequentialStepOnObservation_movementEnvelope
    {content : CompoundClearanceContent}
    {observation : StepCompletionObservation}
    (hWellFormed : CompoundClearanceWellFormed content) :
    CompoundClearanceWellFormed (advanceSequentialStepOnObservation content observation) ∧
      ((advanceSequentialStepOnObservation content observation).nextSequential =
          content.nextSequential ∨
        (advanceSequentialStepOnObservation content observation).nextSequential =
          content.nextSequential + 1) ∧
      (frontierInstructions (advanceSequentialStepOnObservation content observation) =
          frontierInstructions content ∨
        frontierInstructions (advanceSequentialStepOnObservation content observation) =
          match listIndex? content.sequentialSteps (content.nextSequential + 1) with
          | none => content.immediateSteps
          | some nextStep => content.immediateSteps ++ [nextStep]) := by
  refine ⟨?_, ?_, ?_⟩
  · exact advanceSequentialStepOnObservation_preservesWellFormed hWellFormed
  · exact advanceSequentialStepOnObservation_no_skipping content observation
  · exact advanceSequentialStepOnObservation_frontier_preserved_or_shifted content observation

theorem compile_clearance_instruction_as_issuer_eq_error_of_unauthorized
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {issuer : AgentId}
    {instruction : ClearanceInstruction}
    (hUnauthorized : instructionIssuerAuthorized view issuer instruction = false) :
    compile_clearance_instruction_as_issuer view env state issuer instruction =
      .error
        (.unauthorizedIssuer issuer
          s!"issuer {issuer} lacks proof-side authority for {repr instruction}") := by
  simp [compile_clearance_instruction_as_issuer, hUnauthorized]

theorem compile_clearance_instruction_as_issuer_eq_compile_of_authorized
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {issuer : AgentId}
    {instruction : ClearanceInstruction}
    (hAuthorized : instructionIssuerAuthorized view issuer instruction = true) :
    compile_clearance_instruction_as_issuer view env state issuer instruction =
      compile_clearance_instruction view env state instruction := by
  simp [compile_clearance_instruction_as_issuer, hAuthorized]

theorem compile_clearance_instruction_as_issuer_ok_implies_authorized
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {issuer : AgentId}
    {instruction : ClearanceInstruction}
    {plan : CertificationPlan} :
    compile_clearance_instruction_as_issuer view env state issuer instruction = .ok plan →
      instructionIssuerAuthorized view issuer instruction = true := by
  intro hCompiled
  cases hAuthorized : instructionIssuerAuthorized view issuer instruction with
  | false =>
      rw [compile_clearance_instruction_as_issuer_eq_error_of_unauthorized hAuthorized] at hCompiled
      cases hCompiled
  | true =>
      rfl

theorem compileInstructionList_ok_preservesInstructions
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {instructions : List ClearanceInstruction}
    {compiled : List CompiledInstructionPlan} :
    compileInstructionList view env state instructions = .ok compiled →
      compiled.map (fun entry => entry.instruction) = instructions := by
  intro hCompiled
  induction instructions generalizing compiled with
  | nil =>
      simp [compileInstructionList] at hCompiled
      cases hCompiled
      simp
  | cons instruction tail ih =>
      unfold compileInstructionList at hCompiled
      cases hPlan : compile_clearance_instruction view env state instruction with
      | error err =>
          simp [hPlan] at hCompiled
      | ok plan =>
          cases hTail : compileInstructionList view env state tail with
          | error err =>
              simp [hPlan, hTail] at hCompiled
          | ok compiledTail =>
              simp [hPlan, hTail] at hCompiled
              cases hCompiled
              simp [ih hTail]

theorem compileInstructionListAsIssuer_ok_preservesInstructions
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {issuer : AgentId}
    {instructions : List ClearanceInstruction}
    {compiled : List CompiledInstructionPlan} :
    compileInstructionListAsIssuer view env state issuer instructions = .ok compiled →
      compiled.map (fun entry => entry.instruction) = instructions := by
  intro hCompiled
  induction instructions generalizing compiled with
  | nil =>
      simp [compileInstructionListAsIssuer] at hCompiled
      cases hCompiled
      simp
  | cons instruction tail ih =>
      unfold compileInstructionListAsIssuer at hCompiled
      cases hPlan : compile_clearance_instruction_as_issuer view env state issuer instruction with
      | error err =>
          simp [hPlan] at hCompiled
      | ok plan =>
          cases hTail : compileInstructionListAsIssuer view env state issuer tail with
          | error err =>
              simp [hPlan, hTail] at hCompiled
          | ok compiledTail =>
              simp [hPlan, hTail] at hCompiled
              cases hCompiled
              simp [ih hTail]

theorem compileInstructionListAsIssuer_ok_implies_authorized
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {issuer : AgentId}
    {instructions : List ClearanceInstruction}
    {compiled : List CompiledInstructionPlan} :
    compileInstructionListAsIssuer view env state issuer instructions = .ok compiled →
      instructionsIssuerAuthorized view issuer instructions = true := by
  intro hCompiled
  induction instructions generalizing compiled with
  | nil =>
      simp [instructionsIssuerAuthorized]
  | cons instruction tail ih =>
      unfold compileInstructionListAsIssuer at hCompiled
      cases hPlan : compile_clearance_instruction_as_issuer view env state issuer instruction with
      | error err =>
          simp [hPlan] at hCompiled
      | ok plan =>
          cases hTail : compileInstructionListAsIssuer view env state issuer tail with
          | error err =>
              simp [hPlan, hTail] at hCompiled
          | ok compiledTail =>
              simp [hPlan, hTail] at hCompiled
              cases hCompiled
              have hInstruction :=
                compile_clearance_instruction_as_issuer_ok_implies_authorized
                  (view := view) (env := env) (state := state)
                  (issuer := issuer) (instruction := instruction) (plan := plan) hPlan
              have hTailAuthorized := ih hTail
              simp [instructionsIssuerAuthorized, hInstruction, hTailAuthorized]

theorem compileInstructionListAsIssuer_eq_compileInstructionList_of_authorized
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {issuer : AgentId}
    {instructions : List ClearanceInstruction}
    (hAuthorized : instructionsIssuerAuthorized view issuer instructions = true) :
    compileInstructionListAsIssuer view env state issuer instructions =
      compileInstructionList view env state instructions := by
  induction instructions with
  | nil =>
      simp [compileInstructionListAsIssuer, compileInstructionList]
  | cons instruction tail ih =>
      simp [instructionsIssuerAuthorized] at hAuthorized
      rcases hAuthorized with ⟨hInstruction, hTail⟩
      simp [compileInstructionListAsIssuer, compileInstructionList,
        compile_clearance_instruction_as_issuer_eq_compile_of_authorized hInstruction,
        ih hTail]

theorem compile_frontier_ok_matches
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {content : CompoundClearanceContent}
    {frontier : CompiledFrontier} :
    compile_frontier view env state content = .ok frontier →
      CompiledFrontierMatches content frontier := by
  intro hFrontier
  unfold compile_frontier at hFrontier
  cases hImmediate : compileInstructionList view env state content.immediateSteps with
  | error err =>
      simp [hImmediate] at hFrontier
  | ok immediate =>
      cases hSequential : activeSequentialStep? content with
      | none =>
          simp [hImmediate, hSequential] at hFrontier
          cases hFrontier
          have hImmediateInstructions :=
            compileInstructionList_ok_preservesInstructions
              (view := view) (env := env) (state := state) (compiled := immediate) hImmediate
          simp [CompiledFrontierMatches, frontierInstructions, compiledFrontierInstructions,
            hSequential, hImmediateInstructions]
      | some step =>
          cases hPlan : compile_clearance_instruction view env state step with
          | error err =>
              simp [hImmediate, hSequential, hPlan] at hFrontier
          | ok plan =>
              simp [hImmediate, hSequential, hPlan] at hFrontier
              cases hFrontier
              have hImmediateInstructions :=
                compileInstructionList_ok_preservesInstructions
                  (view := view) (env := env) (state := state) (compiled := immediate) hImmediate
              simp [CompiledFrontierMatches, frontierInstructions, compiledFrontierInstructions,
                hSequential, hImmediateInstructions]

theorem compile_frontier_as_issuer_eq_compile_frontier_of_authorized
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {issuer : AgentId}
    {content : CompoundClearanceContent}
    (hAuthorized : compoundClearanceFrontierIssuerAuthorized view issuer content = true) :
    compile_frontier_as_issuer view env state issuer content =
      compile_frontier view env state content := by
  cases hActive : activeSequentialStep? content with
  | none =>
      rw [compoundClearanceFrontierIssuerAuthorized_eq_immediate_of_no_active hActive] at hAuthorized
      simp [compile_frontier_as_issuer, compile_frontier, hActive,
        compileInstructionListAsIssuer_eq_compileInstructionList_of_authorized hAuthorized]
  | some step =>
      rw [compoundClearanceFrontierIssuerAuthorized_eq_immediate_and_active hActive] at hAuthorized
      simp at hAuthorized
      rcases hAuthorized with ⟨hImmediate, hStep⟩
      simp [compile_frontier_as_issuer, compile_frontier, hActive,
        compileInstructionListAsIssuer_eq_compileInstructionList_of_authorized hImmediate,
        compile_clearance_instruction_as_issuer_eq_compile_of_authorized hStep]

theorem compile_frontier_as_issuer_ok_implies_authorized
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {issuer : AgentId}
    {content : CompoundClearanceContent}
    {frontier : CompiledFrontier} :
    compile_frontier_as_issuer view env state issuer content = .ok frontier →
      compoundClearanceFrontierIssuerAuthorized view issuer content = true := by
  intro hFrontier
  unfold compile_frontier_as_issuer at hFrontier
  cases hImmediate : compileInstructionListAsIssuer view env state issuer content.immediateSteps with
  | error err =>
      simp [hImmediate] at hFrontier
  | ok immediate =>
      have hImmediateAuthorized :=
        compileInstructionListAsIssuer_ok_implies_authorized
          (view := view) (env := env) (state := state)
          (issuer := issuer) (instructions := content.immediateSteps)
          (compiled := immediate) hImmediate
      cases hSequential : activeSequentialStep? content with
      | none =>
          rw [compoundClearanceFrontierIssuerAuthorized_eq_immediate_of_no_active hSequential]
          exact hImmediateAuthorized
      | some step =>
          cases hPlan : compile_clearance_instruction_as_issuer view env state issuer step with
          | error err =>
              simp [hImmediate, hSequential, hPlan] at hFrontier
          | ok plan =>
              have hStepAuthorized :=
                compile_clearance_instruction_as_issuer_ok_implies_authorized
                  (view := view) (env := env) (state := state)
                  (issuer := issuer) (instruction := step) (plan := plan) hPlan
              rw [compoundClearanceFrontierIssuerAuthorized_eq_immediate_and_active hSequential]
              simp [hImmediateAuthorized, hStepAuthorized]

theorem compile_frontier_as_issuer_ok_matches
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {issuer : AgentId}
    {content : CompoundClearanceContent}
    {frontier : CompiledFrontier} :
    compile_frontier_as_issuer view env state issuer content = .ok frontier →
      CompiledFrontierMatches content frontier := by
  intro hFrontier
  unfold compile_frontier_as_issuer at hFrontier
  cases hImmediate : compileInstructionListAsIssuer view env state issuer content.immediateSteps with
  | error err =>
      simp [hImmediate] at hFrontier
  | ok immediate =>
      cases hSequential : activeSequentialStep? content with
      | none =>
          simp [hImmediate, hSequential] at hFrontier
          cases hFrontier
          have hImmediateInstructions :=
            compileInstructionListAsIssuer_ok_preservesInstructions
              (view := view) (env := env) (state := state)
              (issuer := issuer) (compiled := immediate) hImmediate
          simp [CompiledFrontierMatches, frontierInstructions, compiledFrontierInstructions,
            hSequential, hImmediateInstructions]
      | some step =>
          cases hPlan : compile_clearance_instruction_as_issuer view env state issuer step with
          | error err =>
              simp [hImmediate, hSequential, hPlan] at hFrontier
          | ok plan =>
              simp [hImmediate, hSequential, hPlan] at hFrontier
              cases hFrontier
              have hImmediateInstructions :=
                compileInstructionListAsIssuer_ok_preservesInstructions
                  (view := view) (env := env) (state := state)
                  (issuer := issuer) (compiled := immediate) hImmediate
              simp [CompiledFrontierMatches, frontierInstructions, compiledFrontierInstructions,
                hSequential, hImmediateInstructions]

theorem compile_clearance_content_frontier_ok_matches
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {content : ClearanceContentView}
    {frontier : CompiledFrontier} :
    compile_clearance_content_frontier view env state content = .ok frontier →
      ClearanceContentFrontierMatches content frontier := by
  intro hFrontier
  cases content with
  | single instruction =>
      unfold compile_clearance_content_frontier at hFrontier
      cases hPlan : compile_clearance_instruction view env state instruction with
      | error err =>
          simp [hPlan] at hFrontier
      | ok plan =>
          simp [hPlan] at hFrontier
          cases hFrontier
          simp [ClearanceContentFrontierMatches, clearanceContentFrontierInstructions,
            compiledFrontierInstructions]
  | compound compound =>
      simpa [compile_clearance_content_frontier, ClearanceContentFrontierMatches,
        clearanceContentFrontierInstructions] using
        (compile_frontier_ok_matches
          (view := view) (env := env) (state := state)
          (content := compound) (frontier := frontier) hFrontier)

theorem compile_clearance_content_frontier_as_issuer_eq_compile_clearance_content_frontier_of_authorized
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {issuer : AgentId}
    {content : ClearanceContentView}
    (hAuthorized : clearanceContentFrontierIssuerAuthorized view issuer content = true) :
    compile_clearance_content_frontier_as_issuer view env state issuer content =
      compile_clearance_content_frontier view env state content := by
  cases content with
  | single instruction =>
      have hCompile :=
        compile_clearance_instruction_as_issuer_eq_compile_of_authorized
          (view := view) (env := env) (state := state)
          (issuer := issuer) (instruction := instruction) hAuthorized
      simp [compile_clearance_content_frontier_as_issuer, compile_clearance_content_frontier,
        hCompile]
  | compound compound =>
      simpa [clearanceContentFrontierIssuerAuthorized,
        compile_clearance_content_frontier_as_issuer,
        compile_clearance_content_frontier] using
        (compile_frontier_as_issuer_eq_compile_frontier_of_authorized
          (view := view) (env := env) (state := state)
          (issuer := issuer) (content := compound) hAuthorized)

theorem compile_clearance_content_frontier_as_issuer_ok_matches
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {issuer : AgentId}
    {content : ClearanceContentView}
    {frontier : CompiledFrontier} :
    compile_clearance_content_frontier_as_issuer view env state issuer content = .ok frontier →
      ClearanceContentFrontierMatches content frontier := by
  intro hFrontier
  cases content with
  | single instruction =>
      unfold compile_clearance_content_frontier_as_issuer at hFrontier
      cases hPlan : compile_clearance_instruction_as_issuer view env state issuer instruction with
      | error err =>
          simp [hPlan] at hFrontier
      | ok plan =>
          simp [hPlan] at hFrontier
          cases hFrontier
          simp [ClearanceContentFrontierMatches, clearanceContentFrontierInstructions,
            compiledFrontierInstructions]
  | compound compound =>
      simpa [compile_clearance_content_frontier_as_issuer,
        ClearanceContentFrontierMatches, clearanceContentFrontierInstructions] using
        (compile_frontier_as_issuer_ok_matches
          (view := view) (env := env) (state := state)
          (issuer := issuer) (content := compound) (frontier := frontier) hFrontier)

theorem compile_clearance_content_frontier_as_issuer_ok_implies_authorized
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {issuer : AgentId}
    {content : ClearanceContentView}
    {frontier : CompiledFrontier} :
    compile_clearance_content_frontier_as_issuer view env state issuer content = .ok frontier →
      clearanceContentFrontierIssuerAuthorized view issuer content = true := by
  intro hFrontier
  cases content with
  | single instruction =>
      unfold compile_clearance_content_frontier_as_issuer at hFrontier
      cases hPlan : compile_clearance_instruction_as_issuer view env state issuer instruction with
      | error err =>
          simp [hPlan] at hFrontier
      | ok plan =>
          simpa [clearanceContentFrontierIssuerAuthorized] using
            (compile_clearance_instruction_as_issuer_ok_implies_authorized
              (view := view) (env := env) (state := state)
              (issuer := issuer) (instruction := instruction) (plan := plan) hPlan)
  | compound compound =>
      simpa [compile_clearance_content_frontier_as_issuer,
        clearanceContentFrontierIssuerAuthorized] using
        (compile_frontier_as_issuer_ok_implies_authorized
          (view := view) (env := env) (state := state)
          (issuer := issuer) (content := compound) (frontier := frontier) hFrontier)

theorem compile_structured_clearance_frontier_ok_matches
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {clearance : StructuredClearance}
    {frontier : CompiledFrontier} :
    compile_structured_clearance_frontier view env state clearance = .ok frontier →
      StructuredClearanceFrontierMatches clearance frontier := by
  intro hFrontier
  simpa [compile_structured_clearance_frontier, StructuredClearanceFrontierMatches] using
    (compile_clearance_content_frontier_ok_matches
      (view := view) (env := env) (state := state)
      (content := clearance.content) (frontier := frontier) hFrontier)

theorem compile_structured_clearance_frontier_as_issuer_eq_compile_structured_clearance_frontier_of_authorized
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {clearance : StructuredClearance}
    (hAuthorized : structuredClearanceFrontierIssuerAuthorized view clearance = true) :
    compile_structured_clearance_frontier_as_issuer view env state clearance =
      compile_structured_clearance_frontier view env state clearance := by
  simpa [compile_structured_clearance_frontier_as_issuer,
    compile_structured_clearance_frontier, structuredClearanceFrontierIssuerAuthorized] using
    (compile_clearance_content_frontier_as_issuer_eq_compile_clearance_content_frontier_of_authorized
      (view := view) (env := env) (state := state)
      (issuer := clearance.issuedBy) (content := clearance.content) hAuthorized)

theorem compile_structured_clearance_frontier_as_issuer_ok_matches
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {clearance : StructuredClearance}
    {frontier : CompiledFrontier} :
    compile_structured_clearance_frontier_as_issuer view env state clearance = .ok frontier →
      StructuredClearanceFrontierMatches clearance frontier := by
  intro hFrontier
  simpa [compile_structured_clearance_frontier_as_issuer, StructuredClearanceFrontierMatches] using
    (compile_clearance_content_frontier_as_issuer_ok_matches
      (view := view) (env := env) (state := state)
      (issuer := clearance.issuedBy) (content := clearance.content)
      (frontier := frontier) hFrontier)

theorem compile_structured_clearance_frontier_as_issuer_ok_implies_authorized
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {clearance : StructuredClearance}
    {frontier : CompiledFrontier} :
    compile_structured_clearance_frontier_as_issuer view env state clearance = .ok frontier →
      structuredClearanceFrontierIssuerAuthorized view clearance = true := by
  intro hFrontier
  simpa [compile_structured_clearance_frontier_as_issuer,
    structuredClearanceFrontierIssuerAuthorized] using
    (compile_clearance_content_frontier_as_issuer_ok_implies_authorized
      (view := view) (env := env) (state := state)
      (issuer := clearance.issuedBy) (content := clearance.content)
      (frontier := frontier) hFrontier)

theorem compile_structured_clearance_frontier_as_issuer_ok_authorized_and_matches
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {clearance : StructuredClearance}
    {frontier : CompiledFrontier} :
    compile_structured_clearance_frontier_as_issuer view env state clearance = .ok frontier →
      structuredClearanceFrontierIssuerAuthorized view clearance = true ∧
        StructuredClearanceFrontierMatches clearance frontier := by
  intro hFrontier
  exact
    ⟨compile_structured_clearance_frontier_as_issuer_ok_implies_authorized
        (view := view) (env := env) (state := state)
        (clearance := clearance) (frontier := frontier) hFrontier,
      compile_structured_clearance_frontier_as_issuer_ok_matches
        (view := view) (env := env) (state := state)
        (clearance := clearance) (frontier := frontier) hFrontier⟩

theorem compile_structured_clearance_frontier_as_issuer_ok_compound_movementEnvelope
    {view : ClearanceCompileView}
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {clearance : StructuredClearance}
    {content : CompoundClearanceContent}
    {frontier : CompiledFrontier}
    {observation : StepCompletionObservation}
    (hContent : clearance.content = .compound content)
    (hFrontier : compile_structured_clearance_frontier_as_issuer view env state clearance = .ok frontier)
    (hWellFormed : CompoundClearanceWellFormed content) :
    structuredClearanceFrontierIssuerAuthorized view clearance = true ∧
      CompiledFrontierMatches content frontier ∧
      CompoundClearanceWellFormed (advanceSequentialStepOnObservation content observation) ∧
      ((advanceSequentialStepOnObservation content observation).nextSequential =
          content.nextSequential ∨
        (advanceSequentialStepOnObservation content observation).nextSequential =
          content.nextSequential + 1) ∧
      (frontierInstructions (advanceSequentialStepOnObservation content observation) =
          frontierInstructions content ∨
        frontierInstructions (advanceSequentialStepOnObservation content observation) =
          match listIndex? content.sequentialSteps (content.nextSequential + 1) with
          | none => content.immediateSteps
          | some nextStep => content.immediateSteps ++ [nextStep]) := by
  have hCompileBoundary :=
    compile_structured_clearance_frontier_as_issuer_ok_authorized_and_matches
      (view := view) (env := env) (state := state)
      (clearance := clearance) (frontier := frontier) hFrontier
  rcases hCompileBoundary with ⟨hAuthorized, hStructuredMatches⟩
  have hMatches : CompiledFrontierMatches content frontier := by
    simpa [hContent, StructuredClearanceFrontierMatches, CompiledFrontierMatches,
      clearanceContentFrontierInstructions] using hStructuredMatches
  rcases
      advanceSequentialStepOnObservation_movementEnvelope
        (content := content) (observation := observation) hWellFormed with
    ⟨hWellFormedNext, hNoSkipping, hFrontierEffect⟩
  exact ⟨hAuthorized, hMatches, hWellFormedNext, hNoSkipping, hFrontierEffect⟩

end CertifiedAtc
