import CertifiedAtc.Core

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldModel` is the Kotlin-aligned Lean boundary for the current
greenfield protocol and clearance lifecycle shape.

It deliberately does not replace `ClearanceEnvelope.lean`, which still owns
the older compiler/theorem surface above the atomic command layer. This module
exists so the proof side can reason directly about the runtime model that now
exists in Kotlin: `steps + completedSteps`, envelope-level conditions,
explicit lifecycle timing/categories, and the split between runtime timing and
proof-frontier timing.

Today it covers the clearance/lifecycle-relevant instruction subset rather
than every controller response or runtime structure.
-/
abbrev AircraftId := EntityId
abbrev ControllerId := AgentId
abbrev TickNumber := Nat
abbrev Callsign := String
abbrev PointId := String
abbrev FixId := String
abbrev SidId := String
abbrev StarId := String
abbrev AirwayId := String
abbrev VfrRouteId := String
abbrev HoldingPatternId := String
abbrev ApproachId := String
abbrev Frequency := CertifiedAtc.Frequency
abbrev Squawk := Nat
abbrev Minutes := Nat
abbrev DmeDistanceNm := Nat

inductive RoleName
  | clearanceDelivery
  | ground
  | tower
  | approach
  | departure
  | areaControl
  | afis
  deriving DecidableEq, Repr

inductive JoinType
  | straightIn
  | base
  | downwind
  | crosswind
  | midDownwind
  | overhead
  | longFinal
  deriving DecidableEq, Repr

inductive ApproachType
  | ils
  | loc
  | rnav
  | rnp
  | vor
  | ndb
  | sra
  | visual
  | par
  deriving DecidableEq, Repr

inductive ApproachComponent
  | localiser
  | glidepath
  deriving DecidableEq, Repr

inductive TurnDirection
  | left
  | right
  deriving DecidableEq, Repr

inductive OrbitDirection
  | left
  | right
  deriving DecidableEq, Repr

inductive CircuitDirection
  | leftHand
  | rightHand
  deriving DecidableEq, Repr

inductive TransponderMode
  | charlie
  | standby
  | normal
  deriving DecidableEq, Repr

inductive TrafficAction
  | landing
  | departing
  | passing
  | crossing
  deriving DecidableEq, Repr

inductive TrafficRef
  | byCallsign (callsign : Callsign)
  | byDescription (text : String)
  | sequenceNumber (number : Nat)
  deriving DecidableEq, Repr

inductive ConditionalPredicate
  | afterTraffic (traffic : TrafficRef) (action : TrafficAction)
  | behindTraffic (traffic : TrafficRef)
  deriving DecidableEq, Repr

inductive Level
  | altitudeFeet (feet : Int)
  | heightFeet (feet : Int)
  | flightLevel (fl : Nat)
  deriving DecidableEq, Repr

inductive Speed
  | inKnots (knots : Nat)
  | inMachPermille (permille : Nat)
  deriving DecidableEq, Repr

inductive PressureSetting
  | qnhHpa (value : Nat)
  | qfeHpa (value : Nat)
  | standard
  deriving DecidableEq, Repr

inductive RouteSpec
  | direct (fix : FixId)
  | via (fixes : List FixId)
  | airway (airway : AirwayId) (exitFix : FixId)
  | viaSid (sid : SidId)
  | viaStar (star : StarId)
  | viaRoute (route : VfrRouteId)
  deriving DecidableEq, Repr

inductive HoldSpec
  | published (fix : FixId)
  | inboundTrack (fix : FixId) (inboundDegreesMagnetic : Nat)
      (turnDirection : TurnDirection)
      (legTime : Option Minutes := none)
      (legDistance : Option DmeDistanceNm := none)
  deriving DecidableEq, Repr

inductive AtcInstruction
  | startupApproved (target : AircraftId)
  | pushbackApproved (target : AircraftId)
  | taxiTo (target : AircraftId) (destination : PointId) (via : List PointId := [])
  | holdPosition (target : AircraftId)
  | holdShortOf (target : AircraftId) (runway : RunwayId)
  | crossRunway (target : AircraftId) (runway : RunwayId)
  | backtrackRunway (target : AircraftId) (runway : RunwayId)
  | lineUpAndWait (target : AircraftId) (runway : RunwayId)
  | clearedForTakeoff (target : AircraftId) (runway : RunwayId)
  | clearedToLand (target : AircraftId) (runway : RunwayId)
  | clearedTouchAndGo (target : AircraftId) (runway : RunwayId)
  | clearedLowApproach (target : AircraftId) (runway : RunwayId)
  | goAround (target : AircraftId)
  | afterLandingVacateVia (target : AircraftId) (exit : PointId)
  | clearedTo (target : AircraftId) (clearanceLimit : FixId)
      (route : Option RouteSpec := none)
  | proceedDirect (target : AircraftId) (fix : FixId)
  | resumeOwnNavigation (target : AircraftId)
  | routeAsFiled (target : AircraftId)
  | joinAirway (target : AircraftId) (airway : AirwayId) (joinFix : FixId)
  | rejoinSidAt (target : AircraftId) (fix : FixId)
  | holdAt (target : AircraftId) (hold : HoldSpec)
      (expectFurtherClearanceAt : Option String := none)
  | leaveHoldProceedDirect (target : AircraftId) (fix : FixId)
  | whenAbleProceedDirect (target : AircraftId) (fix : FixId)
  | climbTo (target : AircraftId) (level : Level)
  | descendTo (target : AircraftId) (level : Level)
  | expediteClimb (target : AircraftId) (level : Level)
  | expediteDescend (target : AircraftId) (level : Level)
  | maintainLevel (target : AircraftId) (level : Level)
  | stopClimbAt (target : AircraftId) (level : Level)
  | stopDescentAt (target : AircraftId) (level : Level)
  | maintainAtOrAbove (target : AircraftId) (minimumLevel : Level)
  | maintainAtOrBelow (target : AircraftId) (maximumLevel : Level)
  | afterPassingLevelClimbTo (target : AircraftId) (afterPassing : Level) (climbTo : Level)
  | afterPassingLevelDescendTo (target : AircraftId) (afterPassing : Level) (descendTo : Level)
  | maintainAltitudeUntilEstablished (target : AircraftId) (level : Level)
      (on : ApproachComponent)
  | maintainSpeed (target : AircraftId) (speed : Speed)
  | reduceSpeedTo (target : AircraftId) (speed : Speed)
  | increaseSpeedTo (target : AircraftId) (speed : Speed)
  | minimumCleanSpeed (target : AircraftId)
  | resumeNormalSpeed (target : AircraftId)
  | clearedApproach (target : AircraftId) (approachType : ApproachType)
      (runway : RunwayId) (circlingRunway : Option RunwayId := none)
  | joinCircuit (target : AircraftId) (circuitDirection : CircuitDirection)
      (joinType : JoinType) (runway : Option RunwayId := none)
  | continueApproach (target : AircraftId)
  | extendDownwind (target : AircraftId)
  | orbit (target : AircraftId) (direction : OrbitDirection)
  | contactFrequency (target : AircraftId) (role : RoleName)
      (frequency : Option Frequency := none)
  | monitorFrequency (target : AircraftId) (role : RoleName)
      (frequency : Option Frequency := none)
  | setSquawk (target : AircraftId) (code : Squawk)
  | confirmSquawk (target : AircraftId) (code : Squawk)
  | squawkIdent (target : AircraftId)
  | squawkStandby (target : AircraftId)
  | squawkNormal (target : AircraftId) (mode : TransponderMode)
  | stopSquawk (target : AircraftId) (mode : TransponderMode)
  | setPressure (target : AircraftId) (pressure : PressureSetting)
  | conditionalClearance (target : AircraftId) (condition : ConditionalPredicate)
      (instruction : AtcInstruction)
  deriving DecidableEq, Repr

def instructionTarget : AtcInstruction → AircraftId
  | .startupApproved target => target
  | .pushbackApproved target => target
  | .taxiTo target _ _ => target
  | .holdPosition target => target
  | .holdShortOf target _ => target
  | .crossRunway target _ => target
  | .backtrackRunway target _ => target
  | .lineUpAndWait target _ => target
  | .clearedForTakeoff target _ => target
  | .clearedToLand target _ => target
  | .clearedTouchAndGo target _ => target
  | .clearedLowApproach target _ => target
  | .goAround target => target
  | .afterLandingVacateVia target _ => target
  | .clearedTo target _ _ => target
  | .proceedDirect target _ => target
  | .resumeOwnNavigation target => target
  | .routeAsFiled target => target
  | .joinAirway target _ _ => target
  | .rejoinSidAt target _ => target
  | .holdAt target _ _ => target
  | .leaveHoldProceedDirect target _ => target
  | .whenAbleProceedDirect target _ => target
  | .climbTo target _ => target
  | .descendTo target _ => target
  | .expediteClimb target _ => target
  | .expediteDescend target _ => target
  | .maintainLevel target _ => target
  | .stopClimbAt target _ => target
  | .stopDescentAt target _ => target
  | .maintainAtOrAbove target _ => target
  | .maintainAtOrBelow target _ => target
  | .afterPassingLevelClimbTo target _ _ => target
  | .afterPassingLevelDescendTo target _ _ => target
  | .maintainAltitudeUntilEstablished target _ _ => target
  | .maintainSpeed target _ => target
  | .reduceSpeedTo target _ => target
  | .increaseSpeedTo target _ => target
  | .minimumCleanSpeed target => target
  | .resumeNormalSpeed target => target
  | .clearedApproach target _ _ _ => target
  | .joinCircuit target _ _ _ => target
  | .continueApproach target => target
  | .extendDownwind target => target
  | .orbit target _ => target
  | .contactFrequency target _ _ => target
  | .monitorFrequency target _ _ => target
  | .setSquawk target _ => target
  | .confirmSquawk target _ => target
  | .squawkIdent target => target
  | .squawkStandby target => target
  | .squawkNormal target _ => target
  | .stopSquawk target _ => target
  | .setPressure target _ => target
  | .conditionalClearance target _ _ => target

inductive ClearanceDomain
  | ground
  | runway
  | route
  | level
  | speed
  | squawk
  | frequency
  deriving DecidableEq, Repr

inductive InstructionTiming
  | sequential
  | immediate
  | persistent
  deriving DecidableEq, Repr

inductive CompletionCategory
  | selfCompleting
  | onActivation
  | externalEvent
  | persistent
  deriving DecidableEq, Repr

inductive FrontierTiming
  | movement
  | immediate
  | standalone
  deriving DecidableEq, Repr

def instructionTiming? : AtcInstruction → Option InstructionTiming
  | .conditionalClearance _ _ instruction => instructionTiming? instruction
  | .taxiTo _ _ _ => some .sequential
  | .crossRunway _ _ => some .sequential
  | .backtrackRunway _ _ => some .sequential
  | .setSquawk _ _ => some .immediate
  | .confirmSquawk _ _ => some .immediate
  | .squawkIdent _ => some .immediate
  | .squawkStandby _ => some .immediate
  | .squawkNormal _ _ => some .immediate
  | .stopSquawk _ _ => some .immediate
  | .setPressure _ _ => some .immediate
  | .climbTo _ _ => some .immediate
  | .descendTo _ _ => some .immediate
  | .expediteClimb _ _ => some .immediate
  | .expediteDescend _ _ => some .immediate
  | .maintainLevel _ _ => some .immediate
  | .stopClimbAt _ _ => some .immediate
  | .stopDescentAt _ _ => some .immediate
  | .maintainAtOrAbove _ _ => some .immediate
  | .maintainAtOrBelow _ _ => some .immediate
  | .afterPassingLevelClimbTo _ _ _ => some .immediate
  | .afterPassingLevelDescendTo _ _ _ => some .immediate
  | .maintainAltitudeUntilEstablished _ _ _ => some .immediate
  | .maintainSpeed _ _ => some .immediate
  | .reduceSpeedTo _ _ => some .immediate
  | .increaseSpeedTo _ _ => some .immediate
  | .minimumCleanSpeed _ => some .immediate
  | .resumeNormalSpeed _ => some .immediate
  | .contactFrequency _ _ _ => some .immediate
  | .monitorFrequency _ _ _ => some .immediate
  | .holdPosition _ => some .persistent
  | .holdShortOf _ _ => some .persistent
  | .lineUpAndWait _ _ => some .persistent
  | .orbit _ _ => some .persistent
  | .extendDownwind _ => some .persistent
  | .holdAt _ _ _ => some .persistent
  | _ => none

def instructionDomain? : AtcInstruction → Option ClearanceDomain
  | .conditionalClearance _ _ instruction => instructionDomain? instruction
  | .startupApproved _ => some .ground
  | .pushbackApproved _ => some .ground
  | .taxiTo _ _ _ => some .ground
  | .holdPosition _ => some .ground
  | .holdShortOf _ _ => some .ground
  | .crossRunway _ _ => some .ground
  | .backtrackRunway _ _ => some .ground
  | .lineUpAndWait _ _ => some .runway
  | .clearedForTakeoff _ _ => some .runway
  | .clearedToLand _ _ => some .runway
  | .clearedTouchAndGo _ _ => some .runway
  | .clearedLowApproach _ _ => some .runway
  | .goAround _ => some .runway
  | .afterLandingVacateVia _ _ => some .runway
  | .clearedTo _ _ _ => some .route
  | .proceedDirect _ _ => some .route
  | .resumeOwnNavigation _ => some .route
  | .routeAsFiled _ => some .route
  | .joinAirway _ _ _ => some .route
  | .rejoinSidAt _ _ => some .route
  | .holdAt _ _ _ => some .route
  | .leaveHoldProceedDirect _ _ => some .route
  | .whenAbleProceedDirect _ _ => some .route
  | .clearedApproach _ _ _ _ => some .route
  | .joinCircuit _ _ _ _ => some .runway
  | .continueApproach _ => some .route
  | .extendDownwind _ => some .runway
  | .orbit _ _ => some .runway
  | .climbTo _ _ => some .level
  | .descendTo _ _ => some .level
  | .expediteClimb _ _ => some .level
  | .expediteDescend _ _ => some .level
  | .maintainLevel _ _ => some .level
  | .stopClimbAt _ _ => some .level
  | .stopDescentAt _ _ => some .level
  | .maintainAtOrAbove _ _ => some .level
  | .maintainAtOrBelow _ _ => some .level
  | .afterPassingLevelClimbTo _ _ _ => some .level
  | .afterPassingLevelDescendTo _ _ _ => some .level
  | .maintainAltitudeUntilEstablished _ _ _ => some .level
  | .maintainSpeed _ _ => some .speed
  | .reduceSpeedTo _ _ => some .speed
  | .increaseSpeedTo _ _ => some .speed
  | .minimumCleanSpeed _ => some .speed
  | .resumeNormalSpeed _ => some .speed
  | .contactFrequency _ _ _ => some .frequency
  | .monitorFrequency _ _ _ => some .frequency
  | .setSquawk _ _ => some .squawk
  | .confirmSquawk _ _ => some .squawk
  | .squawkIdent _ => some .squawk
  | .squawkStandby _ => some .squawk
  | .squawkNormal _ _ => some .squawk
  | .stopSquawk _ _ => some .squawk
  | .setPressure _ _ => none

def instructionSupersedesIn : AtcInstruction → List ClearanceDomain
  | .conditionalClearance _ _ instruction => instructionSupersedesIn instruction
  | .goAround _ => [.runway, .route, .level]
  | .clearedApproach _ _ _ _ => [.route, .level]
  | instruction =>
      match instructionDomain? instruction with
      | none => []
      | some domain => [domain]

def instructionCompletionCategory? : AtcInstruction → Option CompletionCategory
  | .conditionalClearance _ _ instruction => instructionCompletionCategory? instruction
  | .taxiTo _ _ _ => some .selfCompleting
  | .crossRunway _ _ => some .selfCompleting
  | .backtrackRunway _ _ => some .selfCompleting
  | .clearedForTakeoff _ _ => some .selfCompleting
  | .clearedToLand _ _ => some .selfCompleting
  | .clearedTouchAndGo _ _ => some .selfCompleting
  | .clearedLowApproach _ _ => some .selfCompleting
  | .afterLandingVacateVia _ _ => some .selfCompleting
  | .clearedTo _ _ _ => some .selfCompleting
  | .proceedDirect _ _ => some .selfCompleting
  | .joinAirway _ _ _ => some .selfCompleting
  | .rejoinSidAt _ _ => some .selfCompleting
  | .leaveHoldProceedDirect _ _ => some .selfCompleting
  | .whenAbleProceedDirect _ _ => some .selfCompleting
  | .climbTo _ _ => some .selfCompleting
  | .descendTo _ _ => some .selfCompleting
  | .expediteClimb _ _ => some .selfCompleting
  | .expediteDescend _ _ => some .selfCompleting
  | .maintainLevel _ _ => some .selfCompleting
  | .stopClimbAt _ _ => some .selfCompleting
  | .stopDescentAt _ _ => some .selfCompleting
  | .maintainAtOrAbove _ _ => some .selfCompleting
  | .maintainAtOrBelow _ _ => some .selfCompleting
  | .afterPassingLevelClimbTo _ _ _ => some .selfCompleting
  | .afterPassingLevelDescendTo _ _ _ => some .selfCompleting
  | .maintainAltitudeUntilEstablished _ _ _ => some .selfCompleting
  | .maintainSpeed _ _ => some .selfCompleting
  | .reduceSpeedTo _ _ => some .selfCompleting
  | .increaseSpeedTo _ _ => some .selfCompleting
  | .confirmSquawk _ _ => some .selfCompleting
  | .squawkIdent _ => some .selfCompleting
  | .squawkStandby _ => some .selfCompleting
  | .squawkNormal _ _ => some .selfCompleting
  | .stopSquawk _ _ => some .selfCompleting
  | .joinCircuit _ _ _ _ => some .selfCompleting
  | .setSquawk _ _ => some .onActivation
  | .setPressure _ _ => some .onActivation
  | .resumeOwnNavigation _ => some .onActivation
  | .routeAsFiled _ => some .onActivation
  | .minimumCleanSpeed _ => some .onActivation
  | .resumeNormalSpeed _ => some .onActivation
  | .contactFrequency _ _ _ => some .externalEvent
  | .monitorFrequency _ _ _ => some .externalEvent
  | .holdPosition _ => some .persistent
  | .holdShortOf _ _ => some .persistent
  | .lineUpAndWait _ _ => some .persistent
  | .orbit _ _ => some .persistent
  | .extendDownwind _ => some .persistent
  | .holdAt _ _ _ => some .persistent
  | _ => none

def instructionMayBeConditional : AtcInstruction → Bool
  | .conditionalClearance _ _ instruction => instructionMayBeConditional instruction
  | .pushbackApproved _ => true
  | .taxiTo _ _ _ => true
  | .holdShortOf _ _ => true
  | .crossRunway _ _ => true
  | .backtrackRunway _ _ => true
  | .lineUpAndWait _ _ => true
  | _ => false

def instructionFrontierTiming : AtcInstruction → FrontierTiming
  | .taxiTo _ _ _ => .movement
  | .crossRunway _ _ => .movement
  | .holdShortOf _ _ => .movement
  | .backtrackRunway _ _ => .movement
  | .lineUpAndWait _ _ => .movement
  | .climbTo _ _ => .immediate
  | .descendTo _ _ => .immediate
  | .expediteClimb _ _ => .immediate
  | .expediteDescend _ _ => .immediate
  | .maintainLevel _ _ => .immediate
  | .stopClimbAt _ _ => .immediate
  | .stopDescentAt _ _ => .immediate
  | .maintainAtOrAbove _ _ => .immediate
  | .maintainAtOrBelow _ _ => .immediate
  | .afterPassingLevelClimbTo _ _ _ => .immediate
  | .afterPassingLevelDescendTo _ _ _ => .immediate
  | .maintainAltitudeUntilEstablished _ _ _ => .immediate
  | .maintainSpeed _ _ => .immediate
  | .reduceSpeedTo _ _ => .immediate
  | .increaseSpeedTo _ _ => .immediate
  | .setSquawk _ _ => .immediate
  | .confirmSquawk _ _ => .immediate
  | .squawkIdent _ => .immediate
  | .squawkStandby _ => .immediate
  | .squawkNormal _ _ => .immediate
  | .stopSquawk _ _ => .immediate
  | .setPressure _ _ => .immediate
  | .contactFrequency _ _ _ => .immediate
  | .monitorFrequency _ _ _ => .immediate
  | .conditionalClearance _ _ instruction => instructionFrontierTiming instruction
  | _ => .standalone

def frontierTimingRefinesRuntimeTiming (instruction : AtcInstruction) : Prop :=
  match instructionFrontierTiming instruction, instructionTiming? instruction with
  | .movement, some .sequential => True
  | .movement, some .persistent => True
  | .immediate, some .immediate => True
  | .standalone, _ => True
  | _, _ => False

structure CompoundClearanceContent where
  steps : List AtcInstruction
  completedSteps : List Nat := []
  deriving DecidableEq, Repr

inductive ClearanceContent
  | single (instruction : AtcInstruction)
  | compound (content : CompoundClearanceContent)
  deriving DecidableEq, Repr

structure StructuredClearance where
  id : ClearanceId
  aircraft : AircraftId
  content : ClearanceContent
  domain : ClearanceDomain
  issuedBy : ControllerId
  issuedAt : TickNumber
  status : CertifiedAtc.ClearanceStatus
  condition : Option ConditionalPredicate := none
  deriving DecidableEq, Repr

inductive NormalizeError
  | multipleConditions
  | conditionalInstructionNotAllowed
  | conditionalStepNotSupported
  deriving DecidableEq, Repr

def addCompletedStep (completedSteps : List Nat) (index : Nat) : List Nat :=
  if index ∈ completedSteps then completedSteps else completedSteps ++ [index]

def enumerateFrom {α : Type} : Nat → List α → List (Nat × α)
  | _, [] => []
  | index, head :: tail => (index, head) :: enumerateFrom (index + 1) tail

def indexedSteps {α : Type} (steps : List α) : List (Nat × α) :=
  enumerateFrom 0 steps

def contentInstructions : ClearanceContent → List AtcInstruction
  | .single instruction => [instruction]
  | .compound content => content.steps

def structuredInstructions (clearance : StructuredClearance) : List AtcInstruction :=
  contentInstructions clearance.content

def activeMovementStepFrom
    (completedSteps : List Nat) :
    List (Nat × AtcInstruction) → Option (Nat × AtcInstruction)
  | [] => none
  | (index, instruction) :: tail =>
      match instructionFrontierTiming instruction with
      | .movement =>
          if index ∈ completedSteps then
            activeMovementStepFrom completedSteps tail
          else
            some (index, instruction)
      | _ =>
          activeMovementStepFrom completedSteps tail

def immediateFrontierSteps (steps : List AtcInstruction) : List AtcInstruction :=
  steps.filter (fun instruction => instructionFrontierTiming instruction = .immediate)

def activeMovementStep? (content : CompoundClearanceContent) :
    Option (Nat × AtcInstruction) :=
  activeMovementStepFrom content.completedSteps (indexedSteps content.steps)

def frontierInstructions : ClearanceContent → List AtcInstruction
  | .single instruction => [instruction]
  | .compound content =>
      match activeMovementStep? content with
      | none => immediateFrontierSteps content.steps
      | some (_, instruction) => immediateFrontierSteps content.steps ++ [instruction]

def structuredFrontierInstructions (clearance : StructuredClearance) :
    List AtcInstruction :=
  frontierInstructions clearance.content

def contentDomains : ClearanceContent → List ClearanceDomain
  | .single instruction =>
      match instructionDomain? instruction with
      | some domain => [domain]
      | none => []
  | .compound content =>
      (content.steps.filterMap instructionDomain?).eraseDups

def contentSupersedesDomains : ClearanceContent → List ClearanceDomain
  | .single instruction => (instructionSupersedesIn instruction).eraseDups
  | .compound content =>
      (content.steps.foldr (fun instruction acc => instructionSupersedesIn instruction ++ acc) []).eraseDups

def completedStep (content : CompoundClearanceContent) (index : Nat) : Prop :=
  index ∈ content.completedSteps

def markStepCompleted (content : CompoundClearanceContent) (index : Nat) :
    CompoundClearanceContent :=
  { content with completedSteps := addCompletedStep content.completedSteps index }

def anyWrappedConditionalStep : List AtcInstruction → Bool
  | [] => false
  | .conditionalClearance _ _ _ :: _ => true
  | _ :: tail => anyWrappedConditionalStep tail

def allStepsMayBeConditional : List AtcInstruction → Bool
  | [] => true
  | step :: tail =>
      instructionMayBeConditional step && allStepsMayBeConditional tail

def normalizeConditionalEnvelope
    (clearance : StructuredClearance) :
    Except NormalizeError StructuredClearance :=
  match clearance.content with
  | .single (.conditionalClearance _ condition instruction) =>
      if clearance.condition.isSome && clearance.condition ≠ some condition then
        .error .multipleConditions
      else if instructionMayBeConditional instruction then
        .ok
          { clearance with
              content := .single instruction
              condition := match clearance.condition with
                | some existing => some existing
                | none => some condition }
      else
        .error .conditionalInstructionNotAllowed
  | .single instruction =>
      if clearance.condition.isSome && !(instructionMayBeConditional instruction) then
        .error .conditionalInstructionNotAllowed
      else
        .ok clearance
  | .compound content =>
      if anyWrappedConditionalStep content.steps then
        .error .conditionalStepNotSupported
      else if clearance.condition.isSome && !(allStepsMayBeConditional content.steps) then
        .error .conditionalInstructionNotAllowed
      else
        .ok clearance

theorem mem_addCompletedStep_self
    (completedSteps : List Nat)
    (index : Nat) :
    index ∈ addCompletedStep completedSteps index := by
  by_cases h : index ∈ completedSteps
  · simp [addCompletedStep, h]
  · simp [addCompletedStep, h]

theorem mem_addCompletedStep_of_mem
    {completedSteps : List Nat}
    {existing index : Nat}
    (hExisting : existing ∈ completedSteps) :
    existing ∈ addCompletedStep completedSteps index := by
  by_cases h : index ∈ completedSteps
  · simp [addCompletedStep, h, hExisting]
  · simp [addCompletedStep, h, hExisting]

theorem frontierInstructions_single
    (instruction : AtcInstruction) :
    frontierInstructions (.single instruction) = [instruction] := rfl

theorem frontierTimingRefinesRuntimeTiming_holds
    (instruction : AtcInstruction) :
    frontierTimingRefinesRuntimeTiming instruction := by
  induction instruction with
  | conditionalClearance target condition instruction ih =>
      simpa [frontierTimingRefinesRuntimeTiming, instructionFrontierTiming, instructionTiming?] using ih
  | _ =>
      simp [frontierTimingRefinesRuntimeTiming, instructionFrontierTiming, instructionTiming?]

theorem addCompletedStep_idempotent
    (completedSteps : List Nat)
    (index : Nat) :
    addCompletedStep (addCompletedStep completedSteps index) index =
      addCompletedStep completedSteps index := by
  by_cases h : index ∈ completedSteps
  · simp [addCompletedStep, h]
  · simp [addCompletedStep, h]

theorem markStepCompleted_marks_completed
    (content : CompoundClearanceContent)
    (index : Nat) :
    completedStep (markStepCompleted content index) index := by
  simp [completedStep, markStepCompleted, mem_addCompletedStep_self]

theorem normalizeConditionalEnvelope_single_conditional_unwraps
    (clearance : StructuredClearance)
    (target : AircraftId)
    (condition : ConditionalPredicate)
    (instruction : AtcInstruction)
    (hCondition : clearance.condition = none)
    (hAllowed : instructionMayBeConditional instruction = true) :
    normalizeConditionalEnvelope
        { clearance with content := .single (.conditionalClearance target condition instruction) } =
      .ok
        { clearance with
            content := .single instruction
            condition := some condition } := by
  cases clearance
  simp at hCondition
  cases hCondition
  simp [normalizeConditionalEnvelope, hAllowed]

theorem normalizeConditionalEnvelope_rejects_wrapped_compound_step
    (clearance : StructuredClearance)
    (content : CompoundClearanceContent)
    (hWrapped : anyWrappedConditionalStep content.steps = true) :
    normalizeConditionalEnvelope { clearance with content := .compound content } =
      .error .conditionalStepNotSupported := by
  cases clearance
  simp [normalizeConditionalEnvelope, hWrapped]

end Greenfield
end CertifiedAtc
