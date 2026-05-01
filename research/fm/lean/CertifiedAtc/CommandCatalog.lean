import CertifiedAtc.Core

namespace CertifiedAtc

inductive CommandClass
  | startupApproved
  | holdPosition
  | holdShortOf
  | taxiTo
  | crossRunway
  | backtrackRunway
  | lineUpAndWait
  | clearedForTakeoff
  | clearedToLand
  | clearedTouchAndGo
  | goAround
  | joinCircuit
  | orbit
  | extendDownwind
  | reportDownwind
  | reportFinal
  | continueApproach
  | proceed
  | contactFrequency
  | monitorFrequency
  | clearedTo
  | reduceSpeedTo
  | climbTo
  | descendTo
  | clearedApproach
  | squawkCode
  | crossControlledAirspace
  | holdAt
  deriving DecidableEq, Repr

inductive ParameterKind
  | entityId
  | runwayId
  | surfaceRoute
  | surfaceNode
  | airRoute
  | airNode
  | controllerId
  | frequency
  | altitudeFt
  | speedKt
  | destinationAerodrome
  | circuitDirection
  | joinType
  | orbitDirection
  | approachType
  | squawkCode
  | airspaceClass
  deriving DecidableEq, Repr

structure ParameterSpec where
  name : String
  kind : ParameterKind
  optional : Bool := false
  deriving DecidableEq, Repr

macro_rules
  | `(⟨$name:str, $kind:term⟩) =>
      `({ name := $name, kind := $kind : ParameterSpec })
  | `(⟨$name:str, $kind:term, true⟩) =>
      `({ name := $name, kind := $kind, optional := true : ParameterSpec })

inductive PreconditionTag
  | targetExists
  | targetTracked
  | targetOnSurface
  | targetInAir
  | runwayExists
  | routeDefined
  | destinationReachable
  | atHoldPoint
  | protectedEntryAuthorized
  | runwayCommitmentAvailable
  | departureReadyPhase
  | approachEligiblePhase
  | branchAvailable
  | altitudeTransitionLegal
  | speedReductionLegal
  | controllerKnown
  | frequencyResolvable
  | missedApproachAvailable
  | ownershipCompatible
  | modeCompatible
  deriving DecidableEq, Repr

inductive LifecycleClass
  | acknowledgement
  | advisory
  | movementAuthority
  | runwayCommitment
  | routeCommitment
  | coordinationTransfer
  deriving DecidableEq, Repr

inductive CompletionTrigger
  | immediate
  | onReadback
  | onSatisfaction
  | onAirborne
  | onRunwayVacated
  | onFrequencyTransfer
  | onReplacement
  deriving DecidableEq, Repr

structure LifecycleSpec where
  cls : LifecycleClass
  entryStatus : ClearanceStatus
  nonTerminalStatuses : List ClearanceStatus
  terminalStatuses : List ClearanceStatus
  readbackRequired : Bool
  completion : CompletionTrigger
  deriving DecidableEq, Repr

structure PlanTemplate where
  certifiedPathDefined : Bool
  runway : Bool := false
  surface : Bool := false
  air : Bool := false
  separation : Bool := false
  compatibility : Bool := false
  joint : Bool := false
  deriving DecidableEq, Repr

structure CommandProfile where
  cls : CommandClass
  owner : OperationalDomain
  parameters : List ParameterSpec
  preconditions : List PreconditionTag
  lifecycle : LifecycleSpec
  plan : PlanTemplate
  deriving DecidableEq, Repr

def classOf : Command → CommandClass
  | .startupApproved _ => .startupApproved
  | .holdPosition _ => .holdPosition
  | .holdShortOf _ _ => .holdShortOf
  | .taxiTo _ _ _ => .taxiTo
  | .crossRunway _ _ => .crossRunway
  | .backtrackRunway _ _ => .backtrackRunway
  | .lineUpAndWait _ _ => .lineUpAndWait
  | .clearedForTakeoff _ _ => .clearedForTakeoff
  | .clearedToLand _ _ => .clearedToLand
  | .clearedTouchAndGo _ _ => .clearedTouchAndGo
  | .goAround _ => .goAround
  | .joinCircuit _ _ _ _ => .joinCircuit
  | .orbit _ _ => .orbit
  | .extendDownwind _ => .extendDownwind
  | .reportDownwind _ => .reportDownwind
  | .reportFinal _ => .reportFinal
  | .continueApproach _ => .continueApproach
  | .proceed _ => .proceed
  | .contactFrequency _ _ _ => .contactFrequency
  | .monitorFrequency _ _ _ => .monitorFrequency
  | .clearedTo _ _ _ _ => .clearedTo
  | .reduceSpeedTo _ _ => .reduceSpeedTo
  | .climbTo _ _ => .climbTo
  | .descendTo _ _ => .descendTo
  | .clearedApproach _ _ _ => .clearedApproach
  | .squawkCode _ _ => .squawkCode
  | .crossControlledAirspace _ _ => .crossControlledAirspace
  | .holdAt _ _ _ => .holdAt

def standardTerminalStates : List ClearanceStatus :=
  [.superseded, .completed, .cancelled]

def advisoryLifecycle : LifecycleSpec :=
  { cls := .advisory
    entryStatus := .issued
    nonTerminalStatuses := [.issued]
    terminalStatuses := [.completed, .cancelled]
    readbackRequired := false
    completion := .onReplacement }

def profile : CommandClass → CommandProfile
  | .startupApproved =>
      { cls := .startupApproved
        owner := .surfaceControl
        parameters := [⟨"target", .entityId⟩]
        preconditions := [.targetExists, .targetTracked, .targetOnSurface, .modeCompatible]
        lifecycle :=
          { cls := .acknowledgement
            entryStatus := .issued
            nonTerminalStatuses := [.issued]
            terminalStatuses := [.completed]
            readbackRequired := false
            completion := .immediate }
        plan := { certifiedPathDefined := false } }
  | .holdPosition =>
      { cls := .holdPosition
        owner := .surfaceControl
        parameters := [⟨"target", .entityId⟩]
        preconditions := [.targetExists, .targetTracked, .targetOnSurface,
          .ownershipCompatible, .modeCompatible]
        lifecycle :=
          { cls := .movementAuthority
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onReplacement }
        plan := { certifiedPathDefined := true, surface := true, compatibility := true } }
  | .holdShortOf =>
      { cls := .holdShortOf
        owner := .surfaceControl
        parameters := [⟨"target", .entityId⟩, ⟨"runway", .runwayId⟩]
        preconditions := [.targetExists, .targetTracked, .targetOnSurface, .atHoldPoint,
          .ownershipCompatible, .modeCompatible]
        lifecycle :=
          { cls := .movementAuthority
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onReplacement }
        plan := { certifiedPathDefined := true, surface := true, compatibility := true } }
  | .taxiTo =>
      { cls := .taxiTo
        owner := .surfaceControl
        parameters := [⟨"target", .entityId⟩, ⟨"route", .surfaceRoute⟩,
          ⟨"destination", .surfaceNode⟩]
        preconditions := [.targetExists, .targetTracked, .targetOnSurface,
          .routeDefined, .destinationReachable, .ownershipCompatible, .modeCompatible]
        lifecycle :=
          { cls := .movementAuthority
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onSatisfaction }
        plan := { certifiedPathDefined := true, surface := true, compatibility := true } }
  | .crossRunway =>
      { cls := .crossRunway
        owner := .runwayGround
        parameters := [⟨"target", .entityId⟩, ⟨"runway", .runwayId⟩]
        preconditions := [.targetExists, .targetTracked, .targetOnSurface,
          .runwayExists, .atHoldPoint, .modeCompatible]
        lifecycle :=
          { cls := .runwayCommitment
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onSatisfaction }
        plan := { certifiedPathDefined := true, runway := true, surface := true, compatibility := true, joint := true } }
  | .backtrackRunway =>
      { cls := .backtrackRunway
        owner := .runwayGround
        parameters := [⟨"target", .entityId⟩, ⟨"runway", .runwayId⟩]
        preconditions := [.targetExists, .targetTracked, .targetOnSurface,
          .runwayExists, .modeCompatible]
        lifecycle :=
          { cls := .runwayCommitment
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onSatisfaction }
        plan := { certifiedPathDefined := true, runway := true, surface := true, compatibility := true, joint := true } }
  | .lineUpAndWait =>
      { cls := .lineUpAndWait
        owner := .runwayGround
        parameters := [⟨"target", .entityId⟩, ⟨"runway", .runwayId⟩]
        preconditions := [.targetExists, .targetTracked, .targetOnSurface,
          .runwayExists, .atHoldPoint, .modeCompatible]
        lifecycle :=
          { cls := .runwayCommitment
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onReplacement }
        plan := { certifiedPathDefined := true, runway := true, surface := true, compatibility := true, joint := true } }
  | .clearedForTakeoff =>
      { cls := .clearedForTakeoff
        owner := .runwayGround
        parameters := [⟨"target", .entityId⟩, ⟨"runway", .runwayId⟩]
        preconditions := [.targetExists, .targetTracked, .targetOnSurface,
          .runwayExists, .departureReadyPhase, .ownershipCompatible, .modeCompatible]
        lifecycle :=
          { cls := .runwayCommitment
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onAirborne }
        plan := { certifiedPathDefined := true, runway := true, air := true, separation := true, compatibility := true, joint := true } }
  | .clearedToLand =>
      { cls := .clearedToLand
        owner := .airControl
        parameters := [⟨"target", .entityId⟩, ⟨"runway", .runwayId⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .runwayExists, .approachEligiblePhase, .missedApproachAvailable,
          .ownershipCompatible, .modeCompatible]
        lifecycle :=
          { cls := .runwayCommitment
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onRunwayVacated }
        plan := { certifiedPathDefined := true, runway := true, air := true, separation := true, compatibility := true, joint := true } }
  | .clearedTouchAndGo =>
      { cls := .clearedTouchAndGo
        owner := .airControl
        parameters := [⟨"target", .entityId⟩, ⟨"runway", .runwayId⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .runwayExists, .approachEligiblePhase, .missedApproachAvailable,
          .ownershipCompatible, .modeCompatible]
        lifecycle :=
          { cls := .runwayCommitment
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onAirborne }
        plan := { certifiedPathDefined := true, runway := true, air := true, separation := true, compatibility := true, joint := true } }
  | .goAround =>
      { cls := .goAround
        owner := .airControl
        parameters := [⟨"target", .entityId⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .missedApproachAvailable, .ownershipCompatible, .modeCompatible]
        lifecycle :=
          { cls := .routeCommitment
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .immediate }
        plan := { certifiedPathDefined := true, runway := true, air := true, separation := true, compatibility := true, joint := true } }
  | .joinCircuit =>
      { cls := .joinCircuit
        owner := .airControl
        parameters := [⟨"target", .entityId⟩, ⟨"direction", .circuitDirection⟩,
          ⟨"joinType", .joinType⟩, ⟨"runway", .runwayId, true⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .branchAvailable, .modeCompatible]
        lifecycle := advisoryLifecycle
        plan := { certifiedPathDefined := true, air := true, compatibility := true } }
  | .orbit =>
      { cls := .orbit
        owner := .airControl
        parameters := [⟨"target", .entityId⟩, ⟨"direction", .orbitDirection⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .branchAvailable, .modeCompatible]
        lifecycle := advisoryLifecycle
        plan := { certifiedPathDefined := true, air := true, separation := true, compatibility := true } }
  | .extendDownwind =>
      { cls := .extendDownwind
        owner := .airControl
        parameters := [⟨"target", .entityId⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .approachEligiblePhase, .modeCompatible]
        lifecycle := advisoryLifecycle
        plan := { certifiedPathDefined := true, air := true, separation := true, compatibility := true } }
  | .reportDownwind =>
      { cls := .reportDownwind
        owner := .airControl
        parameters := [⟨"target", .entityId⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir, .modeCompatible]
        lifecycle :=
          { cls := .advisory
            entryStatus := .issued
            nonTerminalStatuses := [.issued]
            terminalStatuses := [.completed]
            readbackRequired := false
            completion := .immediate }
        plan := { certifiedPathDefined := false } }
  | .reportFinal =>
      { cls := .reportFinal
        owner := .airControl
        parameters := [⟨"target", .entityId⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir, .modeCompatible]
        lifecycle :=
          { cls := .advisory
            entryStatus := .issued
            nonTerminalStatuses := [.issued]
            terminalStatuses := [.completed]
            readbackRequired := false
            completion := .immediate }
        plan := { certifiedPathDefined := false } }
  | .continueApproach =>
      { cls := .continueApproach
        owner := .airControl
        parameters := [⟨"target", .entityId⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .approachEligiblePhase, .modeCompatible]
        lifecycle := advisoryLifecycle
        plan := { certifiedPathDefined := true, air := true, separation := true, compatibility := true } }
  | .proceed =>
      { cls := .proceed
        owner := .airControl
        parameters := [⟨"target", .entityId⟩]
        preconditions := [.targetExists, .targetTracked, .modeCompatible]
        lifecycle := advisoryLifecycle
        plan := { certifiedPathDefined := false } }
  | .contactFrequency =>
      { cls := .contactFrequency
        owner := .coordination
        parameters := [⟨"target", .entityId⟩, ⟨"controller", .controllerId⟩,
          ⟨"frequency", .frequency, true⟩]
        preconditions := [.targetExists, .targetTracked, .controllerKnown,
          .frequencyResolvable, .modeCompatible]
        lifecycle :=
          { cls := .coordinationTransfer
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := [.completed, .cancelled]
            readbackRequired := true
            completion := .onFrequencyTransfer }
        plan := { certifiedPathDefined := false } }
  | .monitorFrequency =>
      { cls := .monitorFrequency
        owner := .coordination
        parameters := [⟨"target", .entityId⟩, ⟨"controller", .controllerId⟩,
          ⟨"frequency", .frequency, true⟩]
        preconditions := [.targetExists, .targetTracked, .controllerKnown,
          .frequencyResolvable, .modeCompatible]
        lifecycle :=
          { cls := .coordinationTransfer
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := [.completed, .cancelled]
            readbackRequired := true
            completion := .onFrequencyTransfer }
        plan := { certifiedPathDefined := false } }
  | .clearedTo =>
      { cls := .clearedTo
        owner := .airControl
        parameters := [⟨"target", .entityId⟩, ⟨"destination", .destinationAerodrome⟩,
          ⟨"route", .airRoute⟩, ⟨"altitude", .altitudeFt⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .routeDefined, .destinationReachable, .altitudeTransitionLegal,
          .modeCompatible]
        lifecycle :=
          { cls := .routeCommitment
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active, .conditionPending]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onReplacement }
        plan := { certifiedPathDefined := true, air := true, separation := true, compatibility := true } }
  | .reduceSpeedTo =>
      { cls := .reduceSpeedTo
        owner := .airControl
        parameters := [⟨"target", .entityId⟩, ⟨"maxSpeed", .speedKt⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .speedReductionLegal, .modeCompatible]
        lifecycle :=
          { cls := .routeCommitment
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onSatisfaction }
        plan := { certifiedPathDefined := true, air := true, separation := true, compatibility := true } }
  | .climbTo =>
      { cls := .climbTo
        owner := .airControl
        parameters := [⟨"target", .entityId⟩, ⟨"altitude", .altitudeFt⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .altitudeTransitionLegal, .modeCompatible]
        lifecycle :=
          { cls := .routeCommitment
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onSatisfaction }
        plan := { certifiedPathDefined := true, air := true, separation := true, compatibility := true } }
  | .descendTo =>
      { cls := .descendTo
        owner := .airControl
        parameters := [⟨"target", .entityId⟩, ⟨"altitude", .altitudeFt⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .altitudeTransitionLegal, .modeCompatible]
        lifecycle :=
          { cls := .routeCommitment
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onSatisfaction }
        plan := { certifiedPathDefined := true, air := true, separation := true, compatibility := true } }
  | .clearedApproach =>
      { cls := .clearedApproach
        owner := .airControl
        parameters := [⟨"target", .entityId⟩, ⟨"runway", .runwayId⟩, ⟨"approachType", .approachType⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .runwayExists, .approachEligiblePhase, .modeCompatible]
        lifecycle :=
          { cls := .routeCommitment
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := true
            completion := .onReplacement }
        plan := { certifiedPathDefined := true, air := true, separation := true, compatibility := true } }
  | .squawkCode =>
      { cls := .squawkCode
        owner := .coordination
        parameters := [⟨"target", .entityId⟩, ⟨"code", .squawkCode⟩]
        preconditions := [.targetExists, .targetTracked, .modeCompatible]
        lifecycle :=
          { cls := .acknowledgement
            entryStatus := .readbackPending
            nonTerminalStatuses := [.readbackPending]
            terminalStatuses := [.completed, .cancelled]
            readbackRequired := true
            completion := .onReadback }
        plan := { certifiedPathDefined := false } }
  | .crossControlledAirspace =>
      { cls := .crossControlledAirspace
        owner := .airControl
        parameters := [⟨"target", .entityId⟩, ⟨"airspaceClass", .airspaceClass⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir, .modeCompatible]
        lifecycle :=
          { cls := .routeCommitment
            entryStatus := .issued
            nonTerminalStatuses := [.issued, .active]
            terminalStatuses := standardTerminalStates
            readbackRequired := false
            completion := .onReplacement }
        plan := { certifiedPathDefined := true, air := true, separation := true, compatibility := true } }
  | .holdAt =>
      { cls := .holdAt
        owner := .airControl
        parameters := [⟨"target", .entityId⟩, ⟨"fix", .airNode⟩, ⟨"direction", .orbitDirection⟩]
        preconditions := [.targetExists, .targetTracked, .targetInAir,
          .branchAvailable, .modeCompatible]
        lifecycle := advisoryLifecycle
        plan := { certifiedPathDefined := true, air := true, separation := true, compatibility := true } }

def commandCatalog : List CommandProfile :=
  [ .startupApproved, .holdPosition, .holdShortOf, .taxiTo, .crossRunway, .backtrackRunway,
    .lineUpAndWait, .clearedForTakeoff, .clearedToLand, .clearedTouchAndGo, .goAround,
    .joinCircuit, .orbit, .extendDownwind, .reportDownwind, .reportFinal,
    .continueApproach, .proceed, .contactFrequency, .monitorFrequency, .clearedTo,
    .reduceSpeedTo,
    .climbTo, .descendTo, .clearedApproach, .squawkCode, .crossControlledAirspace,
    .holdAt ].map profile

def commandProfile (cmd : Command) : CommandProfile :=
  profile (classOf cmd)

def commandPlan (cmd : Command) : PlanTemplate :=
  (commandProfile cmd).plan

def milestone2JointCommand : Command → Prop
  | .clearedForTakeoff _ _ => True
  | .clearedToLand _ _ => True
  | .goAround _ => True
  | _ => False

end CertifiedAtc
