import CertifiedAtc.GreenfieldModel

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldResolved` is the proof-side execution boundary that sits between the
greenfield instruction model and lifecycle/completion reasoning.

It mirrors the Kotlin `ResolvedStep` / `ResolvedClearance` shape for the
completion-relevant subset: steps can carry concrete destination points,
runway-transition facts, resolved radio roles/frequencies, and resolved circuit
join information. This closes the biggest semantic gap in the previous Lean
work, where completion was still reasoning over raw instructions.
-/

structure ResolvedTaxiRoute where
  destination : PointId
  path : List PointId := []
  deriving DecidableEq, Repr

structure ResolvedHoldingPoint where
  runway : RunwayId
  point : PointId
  deriving DecidableEq, Repr

structure ResolvedRunwayCrossing where
  runway : RunwayId
  crossingPoint : PointId
  deriving DecidableEq, Repr

structure ResolvedBacktrack where
  runway : RunwayId
  farEndPoint : PointId
  deriving DecidableEq, Repr

structure ResolvedRunwayOperation where
  runway : RunwayId
  thresholdPoint : PointId
  pathPoints : List PointId := []
  deriving DecidableEq, Repr

structure ResolvedRouteClearance where
  clearanceLimitFix : FixId
  clearanceLimitPoint : PointId
  routePoints : List PointId := []
  clearanceLimitHoldingPattern : Option HoldingPatternId := none
  deriving DecidableEq, Repr

structure ResolvedHoldingInstruction where
  holdingPattern : HoldingPatternId
  fix : FixId
  fixPoint : PointId
  loopPoints : List PointId := []
  deriving DecidableEq, Repr

structure ResolvedApproachClearance where
  approach : ApproachId
  runway : RunwayId
  waypointPoints : List PointId := []
  thresholdPoint : PointId
  missedApproachPoints : List PointId := []
  missedApproachHoldingPattern : HoldingPatternId
  deriving DecidableEq, Repr

inductive ResolvedPublishedHandoffAction
  | contact
  | monitor
  deriving DecidableEq, Repr

inductive ResolvedPublishedHandoffPoint
  | holdingPoint (point : PointId)
  | boundaryFix (fix : FixId)
  | airborne
  deriving DecidableEq, Repr

structure ResolvedPublishedHandoff where
  fromRole : RoleName
  toRole : RoleName
  action : ResolvedPublishedHandoffAction
  location : ResolvedPublishedHandoffPoint
  deriving DecidableEq, Repr

structure ResolvedRoleFrequency where
  roleName : RoleName
  instructedFrequency : Option Frequency := none
  publishedHandoff : Option ResolvedPublishedHandoff := none
  deriving DecidableEq, Repr

structure ResolvedDirectFix where
  fix : FixId
  point : PointId
  deriving DecidableEq, Repr

structure ResolvedAirwayJoin where
  airway : AirwayId
  joinFix : FixId
  joinPoint : PointId
  deriving DecidableEq, Repr

structure ResolvedCircuitJoin where
  circuit : CircuitProcedureId
  altitude : Level
  entryPoint : PointId
  entryPathPoints : List PointId := []
  circuitPoints : List PointId := []
  deriving DecidableEq, Repr

structure ResolvedAirspaceInstruction where
  airspace : AirspaceVolumeId
  points : List PointId
  routePoints : List PointId := []
  entryTransitions : List (PointId × PointId) := []
  exitTransitions : List (PointId × PointId) := []
  deriving DecidableEq, Repr

inductive ResolvedVectorKind
  | flyHeading
  | turnHeading
  | continuePresentHeading
  | turnByDegrees
  deriving DecidableEq, Repr

structure ResolvedVectorInstruction where
  kind : ResolvedVectorKind
  targetHeadingDegreesMagnetic : Option Nat := none
  turnDirection : Option TurnDirection := none
  turnDegrees : Option Nat := none
  capturedHeadingDegreesMagnetic : Option Nat := none
  deriving DecidableEq, Repr

def airspaceRouteInsidePoints
    (routePoints : List PointId)
    (airspacePoints : List PointId) : List PointId :=
  routePoints.filter (fun point => point ∈ airspacePoints)

def airspaceRouteEntryTransitions
    (routePoints : List PointId)
    (airspacePoints : List PointId) : List (PointId × PointId) :=
  routePoints.zip routePoints.tail |>.filterMap fun
    | (fromPoint, toPoint) =>
        if fromPoint ∉ airspacePoints && toPoint ∈ airspacePoints then
          some (fromPoint, toPoint)
        else
          none

def airspaceRouteExitTransitions
    (routePoints : List PointId)
    (airspacePoints : List PointId) : List (PointId × PointId) :=
  routePoints.zip routePoints.tail |>.filterMap fun
    | (fromPoint, toPoint) =>
        if fromPoint ∈ airspacePoints && toPoint ∉ airspacePoints then
          some (fromPoint, toPoint)
        else
          none

def airspaceRouteTouches
    (routePoints : List PointId)
    (airspacePoints : List PointId) : Bool :=
  !((airspaceRouteInsidePoints routePoints airspacePoints).isEmpty &&
    (airspaceRouteEntryTransitions routePoints airspacePoints).isEmpty &&
    (airspaceRouteExitTransitions routePoints airspacePoints).isEmpty)

inductive ResolvedPayload
  | taxi (route : ResolvedTaxiRoute)
  | holdShort (holdingPoint : ResolvedHoldingPoint)
  | crossing (crossing : ResolvedRunwayCrossing)
  | backtrack (backtrack : ResolvedBacktrack)
  | runwayOperation (operation : ResolvedRunwayOperation)
  | route (clearance : ResolvedRouteClearance)
  | holding (holding : ResolvedHoldingInstruction)
  | approach (approach : ResolvedApproachClearance)
  | frequencyChange (frequency : ResolvedRoleFrequency)
  | directFix (fix : ResolvedDirectFix)
  | airwayJoin (join : ResolvedAirwayJoin)
  | circuitJoin (circuit : ResolvedCircuitJoin)
  | airspace (airspace : ResolvedAirspaceInstruction)
  | vector (vector : ResolvedVectorInstruction)
  | plain
  deriving DecidableEq, Repr

def instructionNeedsSpecificResolution : AtcInstruction → Bool
  | .taxiTo _ _ _ => true
  | .holdShortOf _ _ => true
  | .crossRunway _ _ => true
  | .backtrackRunway _ _ => true
  | .clearedTo _ _ _ => true
  | .holdAt _ _ _ => true
  | .clearedApproach _ _ _ _ => true
  | .contactFrequency _ _ _ => true
  | .monitorFrequency _ _ _ => true
  | .proceedDirect _ _ => true
  | .leaveHoldProceedDirect _ _ => true
  | .whenAbleProceedDirect _ _ => true
  | .rejoinSidAt _ _ => true
  | .joinAirway _ _ _ => true
  | .joinCircuit _ _ _ _ => true
  | .flyHeading _ _ => true
  | .turnHeading _ _ _ => true
  | .continuePresentHeading _ => true
  | .turnByDegrees _ _ _ => true
  | _ => false

def resolutionCompatible : ResolvedPayload → AtcInstruction → Bool
  | .taxi _, .taxiTo _ _ _ => true
  | .holdShort _, .holdShortOf _ _ => true
  | .crossing _, .crossRunway _ _ => true
  | .backtrack _, .backtrackRunway _ _ => true
  | .runwayOperation _, .lineUpAndWait _ _ => true
  | .runwayOperation _, .clearedForTakeoff _ _ => true
  | .runwayOperation _, .clearedToLand _ _ => true
  | .runwayOperation _, .clearedTouchAndGo _ _ => true
  | .runwayOperation _, .clearedLowApproach _ _ => true
  | .runwayOperation _, .goAround _ => true
  | .route _, .clearedTo _ _ _ => true
  | .holding _, .holdAt _ _ _ => true
  | .approach _, .clearedApproach _ _ _ _ => true
  | .frequencyChange _, .contactFrequency _ _ _ => true
  | .frequencyChange _, .monitorFrequency _ _ _ => true
  | .airspace _, .clearedToEnterControlZone _ _ _ _ => true
  | .airspace _, .remainOutsideControlledAirspace _ _ => true
  | .airspace _, .specialVfrClearance _ _ _ _ => true
  | .directFix _, .proceedDirect _ _ => true
  | .directFix _, .leaveHoldProceedDirect _ _ => true
  | .directFix _, .whenAbleProceedDirect _ _ => true
  | .directFix _, .rejoinSidAt _ _ => true
  | .airwayJoin _, .joinAirway _ _ _ => true
  | .circuitJoin _, .joinCircuit _ _ _ _ => true
  | .vector _, .flyHeading _ _ => true
  | .vector _, .turnHeading _ _ _ => true
  | .vector _, .continuePresentHeading _ => true
  | .vector _, .turnByDegrees _ _ _ => true
  | .plain, instruction => !(instructionNeedsSpecificResolution instruction)
  | _, _ => false

structure ResolvedStep where
  index : Nat
  instruction : AtcInstruction
  domain : ClearanceDomain
  timing : Option InstructionTiming
  completionCategory : Option CompletionCategory
  payload : ResolvedPayload
  deriving DecidableEq, Repr

def ResolvedStep.isCompatible (step : ResolvedStep) : Bool :=
  resolutionCompatible step.payload step.instruction

def compileResolvedStep
    (index : Nat)
    (fallbackDomain : ClearanceDomain)
    (instruction : AtcInstruction)
    (payload : ResolvedPayload)
    (_hCompatible : resolutionCompatible payload instruction = true) :
    ResolvedStep :=
  { index := index
    instruction := instruction
    domain := (instructionDomain? instruction).getD fallbackDomain
    timing := instructionTiming? instruction
    completionCategory := instructionCompletionCategory? instruction
    payload := payload }

theorem compileResolvedStep_matches
    (index : Nat)
    (fallbackDomain : ClearanceDomain)
    (instruction : AtcInstruction)
    (payload : ResolvedPayload)
    (hCompatible : resolutionCompatible payload instruction = true) :
    resolutionCompatible
        (compileResolvedStep index fallbackDomain instruction payload hCompatible).payload
        (compileResolvedStep index fallbackDomain instruction payload hCompatible).instruction = true := by
  simpa [compileResolvedStep] using hCompatible

structure ResolvedClearance where
  source : StructuredClearance
  steps : List ResolvedStep
  deriving DecidableEq, Repr

def ResolvedClearance.allStepsCompatible (clearance : ResolvedClearance) : Bool :=
  clearance.steps.all ResolvedStep.isCompatible

def ResolvedClearance.completedSteps (clearance : ResolvedClearance) : UniqueSet Nat :=
  match clearance.source.content with
  | .single _ => {}
  | .compound content => content.completedSteps

def ResolvedClearance.immediateSteps (clearance : ResolvedClearance) : List ResolvedStep :=
  clearance.steps.filter (fun step => step.timing = some .immediate)

def ResolvedClearance.sequentialSteps (clearance : ResolvedClearance) : List ResolvedStep :=
  clearance.steps.filter (fun step => step.timing = some .sequential)

def ResolvedClearance.persistentSteps (clearance : ResolvedClearance) : List ResolvedStep :=
  clearance.steps.filter (fun step => step.timing = some .persistent)

def ResolvedClearance.nextSequentialStep? (clearance : ResolvedClearance) :
    Option ResolvedStep :=
  clearance.sequentialSteps.find? (fun step => step.index ∉ clearance.completedSteps)

def ResolvedClearance.stepDomains (clearance : ResolvedClearance) : UniqueSet ClearanceDomain :=
  UniqueSet.ofList (clearance.steps.map ResolvedStep.domain)

def ResolvedClearance.supersedesDomains (clearance : ResolvedClearance) : UniqueSet ClearanceDomain :=
  UniqueSet.ofList <|
    clearance.steps.foldr
      (fun step acc => instructionSupersedesIn step.instruction ++ acc)
      []

def ResolvedClearance.effectiveSteps
    (clearance : ResolvedClearance)
    (suppressedDomains : UniqueSet ClearanceDomain := {}) :
    List ResolvedStep :=
  clearance.steps.filter (fun step => step.domain ∉ suppressedDomains)

def ResolvedClearance.requiredCompletionStepIndices
    (clearance : ResolvedClearance)
    (suppressedDomains : UniqueSet ClearanceDomain := {}) :
    List Nat :=
  clearance.steps.filterMap fun step =>
    if step.domain ∈ suppressedDomains then
      none
    else if step.completionCategory = some .persistent then
      none
    else
      some step.index

def ResolvedClearance.withSource
    (clearance : ResolvedClearance)
    (source : StructuredClearance) :
    ResolvedClearance :=
  { clearance with source := source }

@[simp] theorem ResolvedClearance.withSource_allStepsCompatible
    (clearance : ResolvedClearance)
    (source : StructuredClearance) :
    (clearance.withSource source).allStepsCompatible = clearance.allStepsCompatible := by
  rfl

end Greenfield
end CertifiedAtc
