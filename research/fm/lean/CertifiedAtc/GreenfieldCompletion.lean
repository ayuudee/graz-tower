import CertifiedAtc.GreenfieldLifecycle
import CertifiedAtc.GreenfieldResolved

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldCompletion` evaluates structured observations against resolved
clearance steps.

This replaces the previous raw-instruction completion seam. Raw instruction
matching remains only for the families whose Kotlin semantics are still generic
after resolution (level, speed, transponder, radio, and a few runway cases).
Movement and procedure completion now runs against resolved step payloads.
-/

structure CompletionObservation where
  position : Option PointId := none
  activeCircuits : UniqueSet CircuitProcedureId := {}
  reachedFixes : UniqueSet FixId := {}
  onGround : Bool := false
  runwayTransitions : UniqueSet RunwayId := {}
  activeRunways : UniqueSet RunwayId := {}
  airspaceTransitions : UniqueSet AirspaceVolumeId := {}
  activeAirspaces : UniqueSet AirspaceVolumeId := {}
  establishedApproachComponents : UniqueSet ApproachComponent := {}
  currentRole : Option RoleName := none
  currentFrequency : Option Frequency := none
  lastContactRole : Option RoleName := none
  transponderCode : Option Squawk := none
  transponderMode : Option TransponderMode := none
  transponderIdentActive : Bool := false
  altitude : Option Level := none
  speed : Option Speed := none
  deriving DecidableEq, Repr

def runwayTransitionComplete
    (runway : RunwayId)
    (observation : CompletionObservation) :
    CompletionResult :=
  if runway ∈ observation.runwayTransitions && runway ∉ observation.activeRunways then
    .complete
  else
    .notComplete

def airspaceInside
    (airspace : ResolvedAirspaceInstruction)
    (observation : CompletionObservation) : Bool :=
  airspace.airspace ∈ observation.activeAirspaces ||
    match observation.position with
    | some point => point ∈ airspace.points
    | none => false

def airspaceEntered
    (airspace : ResolvedAirspaceInstruction)
    (observation : CompletionObservation) : Bool :=
  airspace.airspace ∈ observation.airspaceTransitions &&
    airspaceInside airspace observation

def airspaceExited
    (airspace : ResolvedAirspaceInstruction)
    (observation : CompletionObservation) : Bool :=
  airspace.airspace ∈ observation.airspaceTransitions &&
    !(airspaceInside airspace observation)

def comparableFeet : Level → Int
  | .flightLevel fl => Int.ofNat fl * 100
  | .altitudeFeet feet => feet
  | .heightFeet feet => feet

def levelAtOrAbove (current : Option Level) (target : Level) : Bool :=
  match current with
  | some current => comparableFeet current >= comparableFeet target
  | none => false

def levelAtOrBelow (current : Option Level) (target : Level) : Bool :=
  match current with
  | some current => comparableFeet current <= comparableFeet target
  | none => false

def levelMatches (current : Option Level) (target : Level) : Bool :=
  match current with
  | some current => comparableFeet current = comparableFeet target
  | none => false

def comparableSpeed? : Speed → Speed → Option (Int × Int)
  | .inKnots current, .inKnots target => some (Int.ofNat current, Int.ofNat target)
  | .inMachPermille current, .inMachPermille target =>
      some (Int.ofNat current, Int.ofNat target)
  | _, _ => none

def speedMatches (current : Option Speed) (target : Speed) : Bool :=
  match current with
  | some current =>
      match comparableSpeed? current target with
      | some (current, target) => current = target
      | none => false
  | none => false

def speedAtOrBelow (current : Option Speed) (target : Speed) : Bool :=
  match current with
  | some current =>
      match comparableSpeed? current target with
      | some (current, target) => current <= target
      | none => false
  | none => false

def speedAtOrAbove (current : Option Speed) (target : Speed) : Bool :=
  match current with
  | some current =>
      match comparableSpeed? current target with
      | some (current, target) => current >= target
      | none => false
  | none => false

/--
`observedInstructionCompletion?` is intentionally limited to instruction
families whose completion remains generic after resolution. Families that now
depend on resolved step payloads are handled in `observedResolvedStepCompletion?`.
-/
def observedInstructionCompletion?
    (instruction : AtcInstruction)
    (observation : CompletionObservation) :
    Option CompletionResult :=
  match instruction with
  | .clearedForTakeoff _ _ =>
      some <| if observation.onGround then .notComplete else .complete
  | .clearedToLand _ runway =>
      some <| runwayTransitionComplete runway observation
  | .clearedTouchAndGo _ runway =>
      some <| if !observation.onGround && runway ∈ observation.runwayTransitions then .complete else .notComplete
  | .clearedLowApproach _ runway =>
      some <| if !observation.onGround && runway ∈ observation.runwayTransitions &&
          runway ∉ observation.activeRunways then .complete else .notComplete
  | .afterLandingVacateVia _ exitPoint =>
      some <| if observation.position = some exitPoint then .complete else .notComplete
  | .climbTo _ level =>
      some <| if levelAtOrAbove observation.altitude level then .complete else .notComplete
  | .descendTo _ level =>
      some <| if levelAtOrBelow observation.altitude level then .complete else .notComplete
  | .expediteClimb _ level =>
      some <| if levelAtOrAbove observation.altitude level then .complete else .notComplete
  | .expediteDescend _ level =>
      some <| if levelAtOrBelow observation.altitude level then .complete else .notComplete
  | .maintainLevel _ level =>
      some <| if levelMatches observation.altitude level then .complete else .notComplete
  | .stopClimbAt _ level =>
      some <| if levelMatches observation.altitude level then .complete else .notComplete
  | .stopDescentAt _ level =>
      some <| if levelMatches observation.altitude level then .complete else .notComplete
  | .maintainAtOrAbove _ level =>
      some <| if levelAtOrAbove observation.altitude level then .complete else .notComplete
  | .maintainAtOrBelow _ level =>
      some <| if levelAtOrBelow observation.altitude level then .complete else .notComplete
  | .afterPassingLevelClimbTo _ _ climbTo =>
      some <| if levelAtOrAbove observation.altitude climbTo then .complete else .notComplete
  | .afterPassingLevelDescendTo _ _ descendTo =>
      some <| if levelAtOrBelow observation.altitude descendTo then .complete else .notComplete
  | .maintainAltitudeUntilEstablished _ _ component =>
      some <| if component ∈ observation.establishedApproachComponents then .complete else .notComplete
  | .maintainSpeed _ speed =>
      some <| if speedMatches observation.speed speed then .complete else .notComplete
  | .reduceSpeedTo _ speed =>
      some <| if speedAtOrBelow observation.speed speed then .complete else .notComplete
  | .increaseSpeedTo _ speed =>
      some <| if speedAtOrAbove observation.speed speed then .complete else .notComplete
  | .confirmSquawk _ squawk =>
      some <| if observation.transponderCode = some squawk then .complete else .notComplete
  | .squawkIdent _ =>
      some <| if observation.transponderIdentActive then .complete else .notComplete
  | .squawkStandby _ =>
      some <| if observation.transponderMode = some .standby then .complete else .notComplete
  | .squawkNormal _ mode =>
      some <| if observation.transponderMode = some mode then .complete else .notComplete
  | .stopSquawk _ mode =>
      some <| if observation.transponderMode ≠ some mode then .complete else .notComplete
  | .contactFrequency _ role frequency =>
      some <|
        if observation.currentRole = some role ||
            observation.lastContactRole = some role ||
            (match frequency with
            | some frequency => observation.currentFrequency = some frequency
            | none => false) then
          .complete
        else
          .notComplete
  | .monitorFrequency _ role frequency =>
      some <|
        if observation.currentRole = some role ||
            observation.lastContactRole = some role ||
            (match frequency with
            | some frequency => observation.currentFrequency = some frequency
            | none => false) then
          .complete
        else
          .notComplete
  | _ => none

def observedResolvedStepCompletion?
    (observation : CompletionObservation)
    (step : ResolvedStep) :
    Option CompletionResult :=
  if !step.isCompatible then
    none
  else
    match step.payload with
    | .taxi route =>
        some <| if observation.position = some route.destination then .complete else .notComplete
    | .holdShort _ =>
        some .notApplicable
    | .crossing crossing =>
        some <| runwayTransitionComplete crossing.runway observation
    | .backtrack backtrack =>
        some <| if observation.position = some backtrack.farEndPoint then .complete else .notComplete
    | .route clearance =>
        some <| if observation.position = some clearance.clearanceLimitPoint then .complete else .notComplete
    | .holding _ =>
        some .notApplicable
    | .approach _ =>
        observedInstructionCompletion? step.instruction observation
    | .frequencyChange frequency =>
        some <|
          if observation.currentRole = some frequency.roleName ||
              observation.lastContactRole = some frequency.roleName ||
              (match frequency.instructedFrequency with
              | some frequency => observation.currentFrequency = some frequency
              | none => false) then
            .complete
          else
            .notComplete
    | .directFix fix =>
        some <| if observation.position = some fix.point then .complete else .notComplete
    | .airwayJoin join =>
        some <| if observation.position = some join.joinPoint then .complete else .notComplete
    | .circuitJoin circuit =>
        some <|
          if circuit.circuit ∈ observation.activeCircuits &&
              observation.altitude = some circuit.altitude then
            .complete
          else
            .notComplete
    | .airspace airspace =>
        match step.instruction with
        | .remainOutsideControlledAirspace _ _ =>
            some <|
              if airspaceInside airspace observation || airspaceEntered airspace observation then
                .notComplete
              else
                .notApplicable
        | .clearedToEnterControlZone _ _ _ _ =>
            some .notApplicable
        | .specialVfrClearance _ _ _ _ =>
            some .notApplicable
        | _ =>
            none
    | .plain =>
        observedInstructionCompletion? step.instruction observation

def completedIndicesFromObservation
    (clearance : ResolvedClearance)
    (observation : CompletionObservation) :
    UniqueSet Nat :=
  UniqueSet.ofList <|
    clearance.steps.filterMap fun step =>
      match observedResolvedStepCompletion? observation step with
      | some .complete => some step.index
      | _ => none

theorem observedInstructionCompletion_contactFrequency_by_role
    (aircraft : AircraftId)
    (role : RoleName)
    (observation : CompletionObservation)
    (hRole : observation.currentRole = some role) :
    observedInstructionCompletion? (.contactFrequency aircraft role none) observation = some .complete := by
  cases observation
  simp at hRole
  cases hRole
  simp [observedInstructionCompletion?]

def sampleResolvedRouteFrequency : ResolvedClearance :=
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
          (.frequencyChange { roleName := .approach, instructedFrequency := none })
          (by native_decide) ] }

def sampleResolvedRouteFrequencyObservation : CompletionObservation :=
  { position := some "P-HOLD" }

example :
    completedIndicesFromObservation
        sampleResolvedRouteFrequency
        sampleResolvedRouteFrequencyObservation =
      UniqueSet.singleton 0 := by
  native_decide

def sampleResolvedCircuitJoin : ResolvedStep :=
  compileResolvedStep
    0
    .runway
    (.joinCircuit "TEST123" .leftHand .downwind (some "27"))
    (.circuitJoin { circuit := "CIRCUIT-27-LH", altitude := .altitudeFeet 1200 })
    (by native_decide)

def sampleResolvedCircuitObservation : CompletionObservation :=
  { activeCircuits := UniqueSet.singleton "CIRCUIT-27-LH"
    altitude := some (.altitudeFeet 1200) }

example :
    observedResolvedStepCompletion? sampleResolvedCircuitObservation sampleResolvedCircuitJoin =
      some .complete := by
  native_decide

def sampleResolvedBacktrack : ResolvedStep :=
  compileResolvedStep
    0
    .ground
    (.backtrackRunway "TEST123" "27")
    (.backtrack { runway := "27", farEndPoint := "RWY27-FAR" })
    (by native_decide)

def sampleResolvedBacktrackObservation : CompletionObservation :=
  { position := some "RWY27-FAR" }

example :
    observedResolvedStepCompletion? sampleResolvedBacktrackObservation sampleResolvedBacktrack =
      some .complete := by
  native_decide

def sampleResolvedRemainOutsideAirspace : ResolvedStep :=
  compileResolvedStep
    0
    .route
    (.remainOutsideControlledAirspace "TEST123" "CTR-1")
    (.airspace { airspace := "CTR-1", points := ["P-IN-CTR"] })
    (by native_decide)

def sampleResolvedRemainOutsideAirspaceOutsideObservation : CompletionObservation :=
  { position := some "P-OUTSIDE" }

def sampleResolvedRemainOutsideAirspaceInsideObservation : CompletionObservation :=
  { position := some "P-IN-CTR" }

example :
    observedResolvedStepCompletion?
      sampleResolvedRemainOutsideAirspaceOutsideObservation
      sampleResolvedRemainOutsideAirspace = some .notApplicable := by
  native_decide

example :
    observedResolvedStepCompletion?
      sampleResolvedRemainOutsideAirspaceInsideObservation
      sampleResolvedRemainOutsideAirspace = some .notComplete := by
  native_decide

end Greenfield
end CertifiedAtc
