import CertifiedAtc.CommandCatalog
import CertifiedAtc.RunwayKernel
import CertifiedAtc.SurfaceKernel
import CertifiedAtc.AirKernel
import CertifiedAtc.SeparationChecker

namespace CertifiedAtc

structure ActiveSet where
  clearances : List ActiveClearance := []
  footprints : List (ClearanceId × Footprint) := []
  deriving DecidableEq, Repr

structure OrchestrationState where
  tick : Nat
  mode : Mode
  ownership : List (EntityId × Ownership) := []
  runway : RunwayState
  surface : SurfaceState
  air : AirState
  activeSet : ActiveSet
  deriving DecidableEq, Repr

structure OrchestrationEnv where
  runwayEnv : RunwayKernelEnv
  surfaceGraph : SurfaceGraph
  airGraph : AirGraph
  deriving DecidableEq, Repr

structure CertificationPlan where
  runway : List RunwayProposal := []
  surface : List SurfaceProposal := []
  air : List AirProposal := []
  separation : List SeparationScenario := []
  deriving DecidableEq, Repr

structure ApprovalBundle where
  runway : List RunwayApproval := []
  surface : List SurfaceApproval := []
  air : List AirApproval := []
  separation : List SeparationWitness := []
  deriving DecidableEq, Repr

inductive CompileError
  | unsupportedCommandClass (cls : CommandClass)
  | malformedCommand (detail : String)
  | unauthorizedIssuer (issuer : AgentId) (detail : String)
  | missingRunwayReference (aircraft : EntityId)
  | missingSurfaceContext (aircraft : EntityId)
  | missingAirContext (aircraft : EntityId)
  | missingPeerSelectionContext (aircraft : EntityId)
  deriving DecidableEq, Repr

structure CompatibilityInput where
  mode : Mode
  activeSet : ActiveSet
  approvals : ApprovalBundle
  deriving DecidableEq, Repr

inductive CompatibilityRejectReason
  | footprintConflict (detail : String)
  | dependencyConflict (detail : String)
  | ownershipConflict (detail : String)
  | modeConflict (detail : String)
  deriving DecidableEq, Repr

inductive CompatibilityDecision
  | compatible
  | incompatible (reason : CompatibilityRejectReason)
  deriving DecidableEq, Repr

structure IssuedRecord where
  clearance : ActiveClearance
  newState : OrchestrationState
  deriving DecidableEq, Repr

inductive IssueRejectReason
  | compileFailed (err : CompileError)
  | runwayRejected (reason : RunwayRejectReason)
  | surfaceRejected (reason : SurfaceRejectReason)
  | airRejected (reason : AirRejectReason)
  | separationRejected (reason : SeparationViolation)
  | incompatible (reason : CompatibilityRejectReason)
  deriving DecidableEq, Repr

inductive IssueResult
  | issued (value : IssuedRecord)
  | rejected (reasons : List IssueRejectReason)
  deriving DecidableEq, Repr

abbrev plan_shape_sig : Type := CommandClass → PlanTemplate
abbrev instantiate_plan_sig : Type :=
  OrchestrationEnv → OrchestrationState → CommandProposal → Except CompileError CertificationPlan
abbrev compatibility_check_sig : Type := CompatibilityInput → CompatibilityDecision
abbrev issue_command_sig : Type := OrchestrationEnv → OrchestrationState → CommandProposal → IssueResult

def compile_command : plan_shape_sig := fun cls => (profile cls).plan

def lookupRunwayCommitment : List RunwayCommitment → EntityId → Option RunwayCommitment
  | [], _ => none
  | commitment :: tail, target =>
      if commitment.aircraft = target then
        some commitment
      else
        lookupRunwayCommitment tail target

def findAltitudeBandForAltitude :
    List AltitudeBand → Int → Option AltitudeBand
  | [], _ => none
  | band :: tail, altitude =>
      if band.lowerFt ≤ altitude ∧ altitude ≤ band.upperFt then
        some band
      else
        findAltitudeBandForAltitude tail altitude

def commandPhaseTag : Command → String → String
  | .clearedForTakeoff _ _, _ => "takeoff-commitment"
  | .clearedToLand _ _, _ => "landing-commitment"
  | .clearedTouchAndGo _ _, _ => "touch-and-go"
  | .goAround _, _ => "missed-approach"
  | .extendDownwind _, _ => "extended-downwind"
  | .continueApproach _, _ => "approach-continued"
  | .clearedApproach _ runway approachType, _ => s!"cleared-approach:{runway}:{approachType}"
  | .crossControlledAirspace _ _, _ => "controlled-airspace-crossing"
  | _, current => current

def commandSeparationRuleId : CommandClass → SeparationRuleId
  | .clearedForTakeoff => "joint-takeoff"
  | .clearedToLand => "joint-landing"
  | .clearedTouchAndGo => "joint-touch-and-go"
  | .goAround => "joint-go-around"
  | .extendDownwind => "extend-downwind"
  | .continueApproach => "continue-approach"
  | .reduceSpeedTo => "reduce-speed-to"
  | .climbTo => "climb-to"
  | .descendTo => "descend-to"
  | .clearedApproach => "cleared-approach"
  | .crossControlledAirspace => "cross-controlled-airspace"
  | _ => "unsupported-separation-command"

def commandSeparationRule (command : Command) : SeparationRule :=
  { id := commandSeparationRuleId (classOf command)
    minLongitudinalPermille := 1
    minVerticalFt := 500
    description := "Current structural separation check" }

def commandSubjectAfter
    (bands : List AltitudeBand)
    (command : Command)
    (subjectBefore : SeparationEntityState) :
    Except CompileError SeparationEntityState :=
  match command with
  | .reduceSpeedTo _ maxSpeedKt =>
      .ok { subjectBefore with speedMaxKt := maxSpeedKt }
  | .climbTo _ altitude =>
      match findAltitudeBandForAltitude bands altitude with
      | none =>
          .error (.malformedCommand s!"no altitude band contains {altitude} ft")
      | some band =>
          .ok
            { subjectBefore with
                lowerAltFt := band.lowerFt
                upperAltFt := band.upperFt }
  | .descendTo _ altitude =>
      match findAltitudeBandForAltitude bands altitude with
      | none =>
          .error (.malformedCommand s!"no altitude band contains {altitude} ft")
      | some band =>
          .ok
            { subjectBefore with
                lowerAltFt := band.lowerFt
                upperAltFt := band.upperFt }
  | _ =>
      .ok { subjectBefore with phaseTag := commandPhaseTag command subjectBefore.phaseTag }

def mkCommandSeparationScenario
    (command : Command)
    (subjectBefore : SeparationEntityState)
    (subjectAfter : SeparationEntityState)
    (peer : SeparationEntityState) :
    SeparationScenario :=
  { subjectBefore := subjectBefore
    subjectAfter := subjectAfter
    peer := peer
    rule := commandSeparationRule command
    horizonSeconds := H_sep }

def buildPlanWithAirAndSeparation
    (graph : AirGraph)
    (state : OrchestrationState)
    (command : Command)
    (runway : List RunwayProposal)
    (airProposal : AirProposal) :
    Except CompileError CertificationPlan :=
  let subjectBefore :=
    toSeparationEntityState graph airProposal.aircraft airProposal.state
  match commandSubjectAfter graph.altitudeBands command subjectBefore with
  | .error err => .error err
  | .ok subjectAfter =>
      .ok
        { runway := runway
          air := [airProposal]
          separation :=
            (selectSeparationPeers graph state.air airProposal.aircraft).map
              (mkCommandSeparationScenario command subjectBefore subjectAfter) }

def buildPlanWithAirOnly
    (airProposal : AirProposal) :
    Except CompileError CertificationPlan :=
  .ok { air := [airProposal] }

def buildPlanWithSurfaceOnly
    (surfaceProposal : SurfaceProposal) :
    Except CompileError CertificationPlan :=
  .ok { surface := [surfaceProposal] }

def buildPlanWithRunwayAndSurface
    (runwayProposal : RunwayProposal)
    (surfaceProposal : SurfaceProposal) :
    Except CompileError CertificationPlan :=
  .ok
    { runway := [runwayProposal]
      surface := [surfaceProposal] }

def findHoldPointForRunway
    (graph : SurfaceGraph)
    (segment : SurfaceSegmentId)
    (runway : RunwayId) : Option SurfaceHoldPoint :=
  let rec go : List SurfaceHoldPoint → Option SurfaceHoldPoint
    | [] => none
    | holdPoint :: tail =>
        if holdPoint.segment = segment ∧ holdPoint.runway = runway then
          some holdPoint
        else
          go tail
  go graph.holdPoints

def findProtectedSuccessorForRunway
    (graph : SurfaceGraph)
    (segment : SurfaceSegmentId)
    (runway : RunwayId) : Option SurfaceSegmentId :=
  let rec go : List (SurfaceSegmentId × SurfaceSegmentId) → Option SurfaceSegmentId
    | [] => none
    | edge :: tail =>
        if edge.1 = segment ∧ protectedRunway graph edge.2 = some runway then
          some edge.2
        else
          go tail
  go graph.adjacency

def taxiRouteNodes
    (route : List SurfaceNodeId)
    (destination : SurfaceNodeId) : List SurfaceNodeId :=
  match route.reverse with
  | [] => [destination]
  | last :: _ =>
      if last = destination then
        route
      else
        route ++ [destination]

def findSurfaceSegmentFromTo
    (graph : SurfaceGraph)
    (fromNode toNode : SurfaceNodeId) : Option SurfaceSegment :=
  let rec go : List SurfaceSegment → Option SurfaceSegment
    | [] => none
    | segment :: tail =>
        if segment.fromNode = fromNode ∧ segment.toNode = toNode then
          some segment
        else
          go tail
  go graph.segments

def projectSurfaceNodeRouteFrom
    (graph : SurfaceGraph)
    (startNode : SurfaceNodeId) :
    List SurfaceNodeId → Option (List SurfaceSegmentId)
  | [] => some []
  | nextNode :: tail =>
      match findSurfaceSegmentFromTo graph startNode nextNode with
      | none => none
      | some segment =>
          match projectSurfaceNodeRouteFrom graph nextNode tail with
          | none => none
          | some remaining => some (segment.id :: remaining)

def projectTaxiRouteSegments
    (graph : SurfaceGraph)
    (position : SurfacePosition)
    (route : List SurfaceNodeId)
    (destination : SurfaceNodeId) : Option (List SurfaceSegmentId) :=
  match findSegment graph position.segment with
  | none => none
  | some currentSegment =>
      let nodes := taxiRouteNodes route destination
      match projectSurfaceNodeRouteFrom graph currentSegment.fromNode nodes with
      | some (first :: remaining) =>
          if first = currentSegment.id then
            some remaining
          else
            projectSurfaceNodeRouteFrom graph currentSegment.toNode nodes
      | some [] =>
          some []
      | none =>
          projectSurfaceNodeRouteFrom graph currentSegment.toNode nodes

def instantiate_plan : instantiate_plan_sig := fun env state proposal =>
  let bands := env.airGraph.altitudeBands
  match proposal.command with
  | .holdShortOf target runway =>
      match lookupPosition state.surface.positions target with
      | none => .error (.missingSurfaceContext target)
      | some position =>
          match findHoldPointForRunway env.surfaceGraph position.segment runway with
          | none =>
              .error
                (.malformedCommand
                  s!"no hold point on segment {position.segment} protects runway {runway}")
          | some holdPoint =>
              buildPlanWithSurfaceOnly
                { aircraft := target
                  position := position
                  movement := .holdAt holdPoint.id }
  | .crossRunway target runway =>
      match lookupPosition state.surface.positions target with
      | none => .error (.missingSurfaceContext target)
      | some position =>
          match findHoldPointForRunway env.surfaceGraph position.segment runway with
          | none =>
              .error
                (.malformedCommand
                  s!"no hold point on segment {position.segment} protects runway {runway}")
          | some _holdPoint =>
              match findProtectedSuccessorForRunway env.surfaceGraph position.segment runway with
              | none =>
                  .error
                    (.malformedCommand
                      s!"no protected crossing segment from {position.segment} enters runway {runway}")
              | some nextSegment =>
                  buildPlanWithRunwayAndSurface
                    (.acquire
                      { runway := runway
                        aircraft := target
                        kind := .protectedForCrossing })
                    { aircraft := target
                      position := position
                      movement := .moveToNext nextSegment }
  | .lineUpAndWait target runway =>
      match lookupPosition state.surface.positions target with
      | none => .error (.missingSurfaceContext target)
      | some position =>
          match findHoldPointForRunway env.surfaceGraph position.segment runway with
          | none =>
              .error
                (.malformedCommand
                  s!"no hold point on segment {position.segment} protects runway {runway}")
          | some _holdPoint =>
              match findProtectedSuccessorForRunway env.surfaceGraph position.segment runway with
              | none =>
                  .error
                    (.malformedCommand
                      s!"no protected entry segment from {position.segment} enters runway {runway}")
              | some nextSegment =>
                  buildPlanWithRunwayAndSurface
                    (.acquire
                      { runway := runway
                        aircraft := target
                        kind := .lineUpAndWait })
                    { aircraft := target
                      position := position
                      movement := .moveToNext nextSegment }
  | .taxiTo target route destination =>
      match lookupPosition state.surface.positions target with
      | none => .error (.missingSurfaceContext target)
      | some position =>
          match projectTaxiRouteSegments env.surfaceGraph position route destination with
          | none =>
              .error
                (.malformedCommand
                  s!"cannot project taxi route for {target} from {position.segment} to {destination}")
          | some [] =>
              .error
                (.malformedCommand
                  s!"taxi route for {target} does not extend beyond current segment {position.segment}")
          | some segments =>
              buildPlanWithSurfaceOnly
                { aircraft := target
                  position := position
                  movement := .reserveRoute segments }
  | .clearedForTakeoff target runway =>
      match lookupAirborneState state.air.aircraft target with
      | none => .error (.missingAirContext target)
      | some airState =>
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            proposal.command
            [.acquire
              { runway := runway
                aircraft := target
                kind := .occupiedTakeoffRoll }]
            { aircraft := target
              state := airState
              act := .activatePath airState.edge }
  | .clearedToLand target runway =>
      match lookupAirborneState state.air.aircraft target with
      | none => .error (.missingAirContext target)
      | some airState =>
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            proposal.command
            [.acquire
              { runway := runway
                aircraft := target
                kind := .reservedForLanding }]
            { aircraft := target
              state := airState
              act := .continueOnEdge }
  | .clearedTouchAndGo target runway =>
      match lookupAirborneState state.air.aircraft target with
      | none => .error (.missingAirContext target)
      | some airState =>
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            proposal.command
            [.acquire
              { runway := runway
                aircraft := target
                kind := .reservedForLanding }]
            { aircraft := target
              state := airState
              act := .continueOnEdge }
  | .goAround target =>
      match lookupAirborneState state.air.aircraft target,
          lookupRunwayCommitment state.runway.commitments target with
      | none, _ => .error (.missingAirContext target)
      | _, none => .error (.missingRunwayReference target)
      | some airState, some commitment =>
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            proposal.command
            [.release commitment]
            { aircraft := target
              state := airState
              act := .activateMissedApproach airState.edge }
  | .joinCircuit target _direction _joinType _runway =>
      match lookupAirborneState state.air.aircraft target with
      | none => .error (.missingAirContext target)
      | some airState =>
          buildPlanWithAirOnly
            { aircraft := target
              state := airState
              act := .activatePath airState.edge }
  | .extendDownwind target =>
      match lookupAirborneState state.air.aircraft target with
      | none => .error (.missingAirContext target)
      | some airState =>
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            proposal.command
            []
            { aircraft := target
              state := airState
              act := .continueOnEdge }
  | .continueApproach target =>
      match lookupAirborneState state.air.aircraft target with
      | none => .error (.missingAirContext target)
      | some airState =>
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            proposal.command
            []
            { aircraft := target
              state := airState
              act := .continueOnEdge }
  | .reduceSpeedTo target maxSpeedKt =>
      match lookupAirborneState state.air.aircraft target with
      | none => .error (.missingAirContext target)
      | some airState =>
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            proposal.command
            []
            { aircraft := target
              state := airState
              act := .reduceSpeedMax maxSpeedKt }
  | .climbTo target altitude =>
      match lookupAirborneState state.air.aircraft target with
      | none => .error (.missingAirContext target)
      | some airState =>
          match findAltitudeBandForAltitude bands altitude with
          | none =>
              .error (.malformedCommand s!"no altitude band contains {altitude} ft")
          | some band =>
              buildPlanWithAirAndSeparation
                env.airGraph
                state
                proposal.command
                []
                { aircraft := target
                  state := airState
                  act := .changeAltitudeBand band.id }
  | .descendTo target altitude =>
      match lookupAirborneState state.air.aircraft target with
      | none => .error (.missingAirContext target)
      | some airState =>
          match findAltitudeBandForAltitude bands altitude with
          | none =>
              .error (.malformedCommand s!"no altitude band contains {altitude} ft")
          | some band =>
              buildPlanWithAirAndSeparation
                env.airGraph
                state
                proposal.command
                []
                { aircraft := target
                  state := airState
                  act := .changeAltitudeBand band.id }
  | .clearedApproach target _runway _approachType =>
      match lookupAirborneState state.air.aircraft target with
      | none => .error (.missingAirContext target)
      | some airState =>
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            proposal.command
            []
            { aircraft := target
              state := airState
              act := .continueOnEdge }
  | .crossControlledAirspace target _airspaceClass =>
      match lookupAirborneState state.air.aircraft target with
      | none => .error (.missingAirContext target)
      | some airState =>
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            proposal.command
            []
            { aircraft := target
              state := airState
              act := .continueOnEdge }
  | _ => .error (.unsupportedCommandClass (classOf proposal.command))

def PlanMatchesTemplate (template : PlanTemplate) (plan : CertificationPlan) : Prop :=
  (template.certifiedPathDefined = false → plan = {}) ∧
    (template.runway = false → plan.runway = []) ∧
    (template.surface = false → plan.surface = []) ∧
    (template.air = false → plan.air = []) ∧
    (template.separation = false → plan.separation = []) ∧
    (template.runway = true → plan.runway ≠ []) ∧
    (template.surface = true → plan.surface ≠ []) ∧
    (template.air = true → plan.air ≠ [])

def PotentiallyConflictingPeer
    (env : OrchestrationEnv) (state : OrchestrationState)
    (subject : EntityId) (peer : EntityId) : Prop :=
  ∃ peerState,
    peerState ∈ selectSeparationPeers env.airGraph state.air subject ∧
    peerState.aircraft = peer

def ScenarioCoversPeer (plan : CertificationPlan) (subject : EntityId) (peer : EntityId) : Prop :=
  ∃ scenario,
    scenario ∈ plan.separation ∧
    scenario.subjectBefore.aircraft = subject ∧
    scenario.peer.aircraft = peer

def footprintAppend (left right : Footprint) : Footprint :=
  { runways := left.runways ++ right.runways
    surfaceSegments := left.surfaceSegments ++ right.surfaceSegments
    airEdges := left.airEdges ++ right.airEdges
    entities := left.entities ++ right.entities
    dependencies := left.dependencies ++ right.dependencies
    ownershipTags := left.ownershipTags ++ right.ownershipTags
    modeTags := left.modeTags ++ right.modeTags }

def mergeFootprints (footprints : List Footprint) : Footprint :=
  footprints.foldl footprintAppend {}

def bundleFootprint (bundle : ApprovalBundle) : Footprint :=
  mergeFootprints
    ((bundle.runway.map fun approval => approval.effect.footprint) ++
      (bundle.surface.map fun approval => approval.effect.footprint) ++
      (bundle.air.map fun approval => approval.effect.footprint))

def anyShared [DecidableEq α] (left right : List α) : Bool :=
  left.any fun item => item ∈ right

def footprintsOverlap (left right : Footprint) : Bool :=
  anyShared left.runways right.runways ||
    anyShared left.surfaceSegments right.surfaceSegments ||
    anyShared left.airEdges right.airEdges ||
    anyShared left.entities right.entities

def dependencySatisfied (active : List (ClearanceId × Footprint)) (dep : ClearanceId) : Bool :=
  active.any fun entry => entry.1 = dep

def dependenciesSatisfied (active : List (ClearanceId × Footprint)) (candidate : Footprint) : Bool :=
  candidate.dependencies.all (dependencySatisfied active)

structure CompatibilityShape where
  mode : Mode
  activeFootprints : List (ClearanceId × Footprint)
  candidate : Footprint
  deriving DecidableEq, Repr

def compatibilityShapeOf (input : CompatibilityInput) : CompatibilityShape :=
  { mode := input.mode
    activeFootprints := input.activeSet.footprints
    candidate := bundleFootprint input.approvals }

def narrowCompatibilityDecision (shape : CompatibilityShape) : CompatibilityDecision :=
  match shape.mode with
  | .normal =>
      if dependenciesSatisfied shape.activeFootprints shape.candidate then
        if shape.activeFootprints.any
            (fun entry => footprintsOverlap entry.2 shape.candidate) then
          .incompatible (.footprintConflict "candidate footprint overlaps the active set")
        else
          .compatible
      else
        .incompatible (.dependencyConflict "candidate depends on an inactive predecessor")
  | .degraded reason =>
      .incompatible (.modeConflict s!"milestone-2 orchestration does not admit degraded mode: {reason}")
  | .emergency kind =>
      .incompatible (.modeConflict s!"milestone-2 orchestration does not admit emergency mode: {kind}")

def compatibility_check : compatibility_check_sig := fun input =>
  narrowCompatibilityDecision (compatibilityShapeOf input)

def NarrowCompatibilityOnly (check : compatibility_check_sig) : Prop :=
  ∃ narrow : CompatibilityShape → CompatibilityDecision,
    ∀ input, check input = narrow (compatibilityShapeOf input)

def collectRunwayApprovals (env : RunwayKernelEnv) :
    RunwayState → List RunwayProposal → Except IssueRejectReason (List RunwayApproval × RunwayState)
  | state, [] => .ok ([], state)
  | state, proposal :: tail =>
      match runway_certify env state proposal with
      | .approved approval =>
          match collectRunwayApprovals env approval.successor tail with
          | .ok (approvals, successor) => .ok (approval :: approvals, successor)
          | .error err => .error err
      | .rejected reason => .error (.runwayRejected reason)

noncomputable def collectSurfaceApprovals (graph : SurfaceGraph) :
    SurfaceState → List SurfaceProposal → Except IssueRejectReason (List SurfaceApproval × SurfaceState)
  | state, [] => .ok ([], state)
  | state, proposal :: tail =>
      match surface_certify graph state proposal with
      | .approved approval =>
          match collectSurfaceApprovals graph approval.successor tail with
          | .ok (approvals, successor) => .ok (approval :: approvals, successor)
          | .error err => .error err
      | .rejected reason => .error (.surfaceRejected reason)

noncomputable def collectAirApprovals (graph : AirGraph) :
    AirState → List AirProposal → Except IssueRejectReason (List AirApproval × AirState)
  | state, [] => .ok ([], state)
  | state, proposal :: tail =>
      match air_certify graph state proposal with
      | .approved approval =>
          match collectAirApprovals graph approval.successor tail with
          | .ok (approvals, successor) => .ok (approval :: approvals, successor)
          | .error err => .error err
      | .rejected reason => .error (.airRejected reason)

noncomputable def collectSeparationWitnesses :
    List SeparationScenario → Except IssueRejectReason (List SeparationWitness)
  | [] => .ok []
  | scenario :: tail =>
      match separation_check scenario with
      | .safe witness =>
          match collectSeparationWitnesses tail with
          | .ok witnesses => .ok (witness :: witnesses)
          | .error err => .error err
      | .unsafeResult violation => .error (.separationRejected violation)

structure CollectedApprovals where
  approvals : ApprovalBundle
  runwaySuccessor : RunwayState
  surfaceSuccessor : SurfaceState
  airSuccessor : AirState
  deriving DecidableEq, Repr

noncomputable def collectApprovalBundle
    (env : OrchestrationEnv) (state : OrchestrationState)
    (plan : CertificationPlan) :
    Except (List IssueRejectReason) CollectedApprovals :=
  match collectRunwayApprovals env.runwayEnv state.runway plan.runway with
  | .error err => .error [err]
  | .ok (runwayApprovals, runwaySuccessor) =>
      match collectSurfaceApprovals env.surfaceGraph state.surface plan.surface with
      | .error err => .error [err]
      | .ok (surfaceApprovals, surfaceSuccessor) =>
          match collectAirApprovals env.airGraph state.air plan.air with
          | .error err => .error [err]
          | .ok (airApprovals, airSuccessor) =>
              match collectSeparationWitnesses plan.separation with
              | .error err => .error [err]
              | .ok separationWitnesses =>
                  .ok
                    { approvals :=
                        { runway := runwayApprovals
                          surface := surfaceApprovals
                          air := airApprovals
                          separation := separationWitnesses }
                      runwaySuccessor := runwaySuccessor
                      surfaceSuccessor := surfaceSuccessor
                      airSuccessor := airSuccessor }

def ApprovalsSatisfyPlan
    (env : OrchestrationEnv) (state : OrchestrationState)
    (plan : CertificationPlan) (approvals : ApprovalBundle) : Prop :=
  ∃ collected,
    collectApprovalBundle env state plan = .ok collected ∧
    collected.approvals = approvals

def approvalCertificateIds (approvals : ApprovalBundle) : List CertificateId :=
  (approvals.runway.map fun approval => approval.certificate.id) ++
    (approvals.surface.map fun approval => approval.certificate.id) ++
    (approvals.air.map fun approval => approval.certificate.id)

def mkIssuedClearance
    (state : OrchestrationState)
    (proposal : CommandProposal)
    (approvals : ApprovalBundle) : ActiveClearance :=
  { id := s!"clearance:{state.tick}:{commandTarget proposal.command}"
    command := proposal.command
    certificates := approvalCertificateIds approvals
    status := (commandProfile proposal.command).lifecycle.entryStatus
    dependsOn := (bundleFootprint approvals).dependencies }

def InterfaceInv (state : OrchestrationState) : Prop :=
  state.activeSet.clearances.length = state.activeSet.footprints.length

def NominalAssumptions (_env : OrchestrationEnv) (state : OrchestrationState) : Prop :=
  state.mode = .normal

structure CertifiedPath where
  plan : CertificationPlan
  collected : CollectedApprovals
  deriving DecidableEq, Repr

noncomputable def executeCertifiedPath
    (env : OrchestrationEnv) (state : OrchestrationState)
    (proposal : CommandProposal) :
    Except (List IssueRejectReason) CertifiedPath :=
  match instantiate_plan env state proposal with
  | .error err => .error [.compileFailed err]
  | .ok plan =>
      match collectApprovalBundle env state plan with
      | .error reasons => .error reasons
      | .ok collected =>
          match compatibility_check
              { mode := state.mode
                activeSet := state.activeSet
                approvals := collected.approvals } with
          | .compatible =>
              .ok { plan := plan, collected := collected }
          | .incompatible reason =>
              .error [.incompatible reason]

noncomputable def issue_command : issue_command_sig := fun env state proposal =>
  match executeCertifiedPath env state proposal with
  | .error reasons => .rejected reasons
  | .ok certified =>
      let clearance := mkIssuedClearance state proposal certified.collected.approvals
      let newState :=
        { tick := state.tick + 1
          mode := state.mode
          ownership := state.ownership
          runway := certified.collected.runwaySuccessor
          surface := certified.collected.surfaceSuccessor
          air := certified.collected.airSuccessor
          activeSet :=
            { clearances := clearance :: state.activeSet.clearances
              footprints :=
                (clearance.id, bundleFootprint certified.collected.approvals) ::
                  state.activeSet.footprints } }
      .issued
        { clearance := clearance
          newState := newState }

theorem RoutingCompletenessTheorem :
  ∀ cls,
    let template := compile_command cls
    template.certifiedPathDefined = true →
       ((template.runway = true) ∨ (template.surface = true) ∨
       (template.air = true) ∨ (template.separation = true)) := by
  intro cls
  cases cls <;> simp [compile_command, profile]

theorem buildPlanWithAirAndSeparation_ok
    {graph : AirGraph}
    {state : OrchestrationState}
    {command : Command}
    {runway : List RunwayProposal}
    {airProposal : AirProposal}
    {plan : CertificationPlan} :
    buildPlanWithAirAndSeparation graph state command runway airProposal = .ok plan →
      ∃ subjectAfter,
        commandSubjectAfter
          graph.altitudeBands
          command
          (toSeparationEntityState graph airProposal.aircraft airProposal.state) =
            .ok subjectAfter ∧
        plan =
          { runway := runway
            air := [airProposal]
            separation :=
              (selectSeparationPeers graph state.air airProposal.aircraft).map
                (mkCommandSeparationScenario
                  command
                  (toSeparationEntityState graph airProposal.aircraft airProposal.state)
                  subjectAfter) } := by
  let subjectBefore :=
    toSeparationEntityState graph airProposal.aircraft airProposal.state
  intro hPlan
  unfold buildPlanWithAirAndSeparation at hPlan
  dsimp [subjectBefore] at hPlan
  cases hAfter :
      commandSubjectAfter
        graph.altitudeBands
        command
        subjectBefore with
  | error err =>
      simp [hAfter, subjectBefore] at hPlan
  | ok subjectAfter =>
      simp [hAfter, subjectBefore] at hPlan
      cases hPlan
      refine ⟨subjectAfter, ?_, rfl⟩
      simp [subjectBefore] at hAfter ⊢

theorem buildPlanWithAirOnly_ok
    {airProposal : AirProposal}
    {plan : CertificationPlan} :
    buildPlanWithAirOnly airProposal = .ok plan →
      plan = { air := [airProposal] } := by
  intro hPlan
  simp [buildPlanWithAirOnly] at hPlan
  cases hPlan
  rfl

theorem buildPlanWithSurfaceOnly_ok
    {surfaceProposal : SurfaceProposal}
    {plan : CertificationPlan} :
    buildPlanWithSurfaceOnly surfaceProposal = .ok plan →
      plan = { surface := [surfaceProposal] } := by
  intro hPlan
  simp [buildPlanWithSurfaceOnly] at hPlan
  cases hPlan
  rfl

theorem buildPlanWithRunwayAndSurface_ok
    {runwayProposal : RunwayProposal}
    {surfaceProposal : SurfaceProposal}
    {plan : CertificationPlan} :
    buildPlanWithRunwayAndSurface runwayProposal surfaceProposal = .ok plan →
      plan =
        { runway := [runwayProposal]
          surface := [surfaceProposal] } := by
  intro hPlan
  simp [buildPlanWithRunwayAndSurface] at hPlan
  cases hPlan
  rfl

theorem PlanInstantiationTheorem :
  ∀ env state proposal plan,
    instantiate_plan env state proposal = Except.ok plan →
      PlanMatchesTemplate (compile_command (classOf proposal.command)) plan := by
  intro env state proposal plan hPlan
  cases proposal with
  | mk proposer command rationale =>
      cases command with
      | holdShortOf target runway =>
          cases hPosition : lookupPosition state.surface.positions target with
          | none =>
              simp [instantiate_plan, hPosition] at hPlan
          | some position =>
              cases hHoldPoint : findHoldPointForRunway env.surfaceGraph position.segment runway with
              | none =>
                  simp [instantiate_plan, hPosition, hHoldPoint] at hPlan
              | some holdPoint =>
                  simp [instantiate_plan, hPosition, hHoldPoint] at hPlan
                  rw [buildPlanWithSurfaceOnly_ok hPlan]
                  simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | crossRunway target runway =>
          cases hPosition : lookupPosition state.surface.positions target with
          | none =>
              simp [instantiate_plan, hPosition] at hPlan
          | some position =>
              cases hHoldPoint : findHoldPointForRunway env.surfaceGraph position.segment runway with
              | none =>
                  simp [instantiate_plan, hPosition, hHoldPoint] at hPlan
              | some holdPoint =>
                  cases hNext : findProtectedSuccessorForRunway env.surfaceGraph position.segment runway with
                  | none =>
                      simp [instantiate_plan, hPosition, hHoldPoint, hNext] at hPlan
                  | some nextSegment =>
                      simp [instantiate_plan, hPosition, hHoldPoint, hNext] at hPlan
                      rw [buildPlanWithRunwayAndSurface_ok hPlan]
                      simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | lineUpAndWait target runway =>
          cases hPosition : lookupPosition state.surface.positions target with
          | none =>
              simp [instantiate_plan, hPosition] at hPlan
          | some position =>
              cases hHoldPoint : findHoldPointForRunway env.surfaceGraph position.segment runway with
              | none =>
                  simp [instantiate_plan, hPosition, hHoldPoint] at hPlan
              | some holdPoint =>
                  cases hNext : findProtectedSuccessorForRunway env.surfaceGraph position.segment runway with
                  | none =>
                      simp [instantiate_plan, hPosition, hHoldPoint, hNext] at hPlan
                  | some nextSegment =>
                      simp [instantiate_plan, hPosition, hHoldPoint, hNext] at hPlan
                      rw [buildPlanWithRunwayAndSurface_ok hPlan]
                      simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | taxiTo target route destination =>
          cases hPosition : lookupPosition state.surface.positions target with
          | none =>
              simp [instantiate_plan, hPosition] at hPlan
          | some position =>
              cases hRoute : projectTaxiRouteSegments env.surfaceGraph position route destination with
              | none =>
                  simp [instantiate_plan, hPosition, hRoute] at hPlan
              | some segments =>
                  cases segments with
                  | nil =>
                      simp [instantiate_plan, hPosition, hRoute] at hPlan
                  | cons head tail =>
                      simp [instantiate_plan, hPosition, hRoute] at hPlan
                      rw [buildPlanWithSurfaceOnly_ok hPlan]
                      simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | clearedForTakeoff target runway =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              simp [instantiate_plan, hAir] at hPlan
              rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨_, _, rfl⟩
              simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | clearedToLand target runway =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              simp [instantiate_plan, hAir] at hPlan
              rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨_, _, rfl⟩
              simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | clearedTouchAndGo target runway =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              simp [instantiate_plan, hAir] at hPlan
              rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨_, _, rfl⟩
              simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | goAround target =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              cases hRunway : lookupRunwayCommitment state.runway.commitments target with
              | none =>
                  simp [instantiate_plan, hAir, hRunway] at hPlan
              | some commitment =>
                  simp [instantiate_plan, hAir, hRunway] at hPlan
                  rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨_, _, rfl⟩
                  simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | joinCircuit target direction joinType runway =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              simp [instantiate_plan, hAir] at hPlan
              rw [buildPlanWithAirOnly_ok hPlan]
              simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | extendDownwind target =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              simp [instantiate_plan, hAir] at hPlan
              rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨_, _, rfl⟩
              simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | continueApproach target =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              simp [instantiate_plan, hAir] at hPlan
              rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨_, _, rfl⟩
              simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | reduceSpeedTo target maxSpeedKt =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              simp [instantiate_plan, hAir] at hPlan
              rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨_, _, rfl⟩
              simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | climbTo target altitude =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              cases hBand : findAltitudeBandForAltitude env.airGraph.altitudeBands altitude with
              | none =>
                  simp [instantiate_plan, hAir, hBand] at hPlan
              | some band =>
                  simp [instantiate_plan, hAir, hBand] at hPlan
                  rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨_, _, rfl⟩
                  simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | descendTo target altitude =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              cases hBand : findAltitudeBandForAltitude env.airGraph.altitudeBands altitude with
              | none =>
                  simp [instantiate_plan, hAir, hBand] at hPlan
              | some band =>
                  simp [instantiate_plan, hAir, hBand] at hPlan
                  rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨_, _, rfl⟩
                  simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | clearedApproach target runway approachType =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              simp [instantiate_plan, hAir] at hPlan
              rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨_, _, rfl⟩
              simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | crossControlledAirspace target airspaceClass =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              simp [instantiate_plan, hAir] at hPlan
              rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨_, _, rfl⟩
              simp [PlanMatchesTemplate, classOf, compile_command, profile]
      | _ =>
          simp [instantiate_plan] at hPlan

theorem scenarioCoversSelectedPeer
    {graph : AirGraph} {state : OrchestrationState}
    {subject : EntityId} {peerState : SeparationEntityState}
    {command : Command} {subjectBefore subjectAfter : SeparationEntityState}
    (hSubject : subjectBefore.aircraft = subject)
    (hPeer : peerState ∈ selectSeparationPeers graph state.air subject) :
    ScenarioCoversPeer
      { separation := (selectSeparationPeers graph state.air subject).map
          (mkCommandSeparationScenario command subjectBefore subjectAfter) }
      subject
      peerState.aircraft := by
  refine ⟨mkCommandSeparationScenario command subjectBefore subjectAfter peerState, ?_⟩
  constructor
  · exact List.mem_map.mpr ⟨peerState, hPeer, rfl⟩
  · constructor
    · simpa [mkCommandSeparationScenario] using hSubject
    · simp [mkCommandSeparationScenario]

theorem buildPlanWithAirAndSeparation_coversPeer
    {graph : AirGraph}
    {state : OrchestrationState}
    {command : Command}
    {runway : List RunwayProposal}
    {airProposal : AirProposal}
    {plan : CertificationPlan}
    {peerState : SeparationEntityState}
    (hPlan :
      buildPlanWithAirAndSeparation graph state command runway airProposal = .ok plan)
    (hSelected : peerState ∈ selectSeparationPeers graph state.air airProposal.aircraft) :
    ScenarioCoversPeer plan airProposal.aircraft peerState.aircraft := by
  rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨subjectAfter, _, rfl⟩
  exact
    scenarioCoversSelectedPeer
      (graph := graph)
      (subject := airProposal.aircraft)
      (command := command)
      (subjectBefore := toSeparationEntityState graph airProposal.aircraft airProposal.state)
      (subjectAfter := subjectAfter)
      (by simp [toSeparationEntityState])
      hSelected

theorem SeparationCoverageTheorem :
    ∀ env state proposal plan peer,
      instantiate_plan env state proposal = Except.ok plan →
      (compile_command (classOf proposal.command)).separation = true →
      PotentiallyConflictingPeer env state (commandTarget proposal.command) peer →
        ScenarioCoversPeer plan (commandTarget proposal.command) peer := by
  intro env state proposal plan peer hPlan hSeparation hPeer
  cases proposal with
  | mk proposer command rationale =>
      cases command with
      | holdShortOf target runway =>
          cases hPosition : lookupPosition state.surface.positions target with
          | none =>
              simp [instantiate_plan, hPosition] at hPlan
          | some position =>
              cases hHoldPoint : findHoldPointForRunway env.surfaceGraph position.segment runway with
              | none =>
                  simp [instantiate_plan, hPosition, hHoldPoint] at hPlan
              | some holdPoint =>
                  simp [classOf, compile_command, profile] at hSeparation
      | crossRunway target runway =>
          cases hPosition : lookupPosition state.surface.positions target with
          | none =>
              simp [instantiate_plan, hPosition] at hPlan
          | some position =>
              cases hHoldPoint : findHoldPointForRunway env.surfaceGraph position.segment runway with
              | none =>
                  simp [instantiate_plan, hPosition, hHoldPoint] at hPlan
              | some holdPoint =>
                  cases hNext : findProtectedSuccessorForRunway env.surfaceGraph position.segment runway with
                  | none =>
                      simp [instantiate_plan, hPosition, hHoldPoint, hNext] at hPlan
                  | some nextSegment =>
                      simp [classOf, compile_command, profile] at hSeparation
      | lineUpAndWait target runway =>
          cases hPosition : lookupPosition state.surface.positions target with
          | none =>
              simp [instantiate_plan, hPosition] at hPlan
          | some position =>
              cases hHoldPoint : findHoldPointForRunway env.surfaceGraph position.segment runway with
              | none =>
                  simp [instantiate_plan, hPosition, hHoldPoint] at hPlan
              | some holdPoint =>
                  cases hNext : findProtectedSuccessorForRunway env.surfaceGraph position.segment runway with
                  | none =>
                      simp [instantiate_plan, hPosition, hHoldPoint, hNext] at hPlan
                  | some nextSegment =>
                      simp [classOf, compile_command, profile] at hSeparation
      | taxiTo target route destination =>
          cases hPosition : lookupPosition state.surface.positions target with
          | none =>
              simp [instantiate_plan, hPosition] at hPlan
          | some position =>
              cases hRoute : projectTaxiRouteSegments env.surfaceGraph position route destination with
              | none =>
                  simp [instantiate_plan, hPosition, hRoute] at hPlan
              | some segments =>
                  cases segments with
                  | nil =>
                      simp [instantiate_plan, hPosition, hRoute] at hPlan
                  | cons head tail =>
                      simp [classOf, compile_command, profile] at hSeparation
      | clearedForTakeoff target runway =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              rcases hPeer with ⟨peerState, hSelected, hPeerAircraft⟩
              simp [instantiate_plan, hAir] at hPlan
              have hCover :=
                buildPlanWithAirAndSeparation_coversPeer
                  (graph := env.airGraph)
                  (state := state)
                  (command := .clearedForTakeoff target runway)
                  (runway := [.acquire
                    { runway := runway
                      aircraft := target
                      kind := .occupiedTakeoffRoll }])
                  (airProposal := { aircraft := target, state := airState, act := .activatePath airState.edge })
                  (peerState := peerState)
                  hPlan
                  hSelected
              simpa [hPeerAircraft] using hCover
      | clearedToLand target runway =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              rcases hPeer with ⟨peerState, hSelected, hPeerAircraft⟩
              simp [instantiate_plan, hAir] at hPlan
              have hCover :=
                buildPlanWithAirAndSeparation_coversPeer
                  (graph := env.airGraph)
                  (state := state)
                  (command := .clearedToLand target runway)
                  (runway := [.acquire
                    { runway := runway
                      aircraft := target
                      kind := .reservedForLanding }])
                  (airProposal := { aircraft := target, state := airState, act := .continueOnEdge })
                  (peerState := peerState)
                  hPlan
                  hSelected
              simpa [hPeerAircraft] using hCover
      | clearedTouchAndGo target runway =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              rcases hPeer with ⟨peerState, hSelected, hPeerAircraft⟩
              simp [instantiate_plan, hAir] at hPlan
              have hCover :=
                buildPlanWithAirAndSeparation_coversPeer
                  (graph := env.airGraph)
                  (state := state)
                  (command := .clearedTouchAndGo target runway)
                  (runway := [.acquire
                    { runway := runway
                      aircraft := target
                      kind := .reservedForLanding }])
                  (airProposal := { aircraft := target, state := airState, act := .continueOnEdge })
                  (peerState := peerState)
                  hPlan
                  hSelected
              simpa [hPeerAircraft] using hCover
      | goAround target =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              cases hRunway : lookupRunwayCommitment state.runway.commitments target with
              | none =>
                  simp [instantiate_plan, hAir, hRunway] at hPlan
              | some commitment =>
                  rcases hPeer with ⟨peerState, hSelected, hPeerAircraft⟩
                  simp [instantiate_plan, hAir, hRunway] at hPlan
                  have hCover :=
                    buildPlanWithAirAndSeparation_coversPeer
                      (graph := env.airGraph)
                      (state := state)
                      (command := .goAround target)
                      (runway := [.release commitment])
                      (airProposal := { aircraft := target, state := airState, act := .activateMissedApproach airState.edge })
                      (peerState := peerState)
                      hPlan
                      hSelected
                  simpa [hPeerAircraft] using hCover
      | joinCircuit target direction joinType runway =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              simp [classOf, compile_command, profile] at hSeparation
      | extendDownwind target =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              rcases hPeer with ⟨peerState, hSelected, hPeerAircraft⟩
              simp [instantiate_plan, hAir] at hPlan
              have hCover :=
                buildPlanWithAirAndSeparation_coversPeer
                  (graph := env.airGraph)
                  (state := state)
                  (command := .extendDownwind target)
                  (runway := [])
                  (airProposal := { aircraft := target, state := airState, act := .continueOnEdge })
                  (peerState := peerState)
                  hPlan
                  hSelected
              simpa [hPeerAircraft] using hCover
      | continueApproach target =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              rcases hPeer with ⟨peerState, hSelected, hPeerAircraft⟩
              simp [instantiate_plan, hAir] at hPlan
              have hCover :=
                buildPlanWithAirAndSeparation_coversPeer
                  (graph := env.airGraph)
                  (state := state)
                  (command := .continueApproach target)
                  (runway := [])
                  (airProposal := { aircraft := target, state := airState, act := .continueOnEdge })
                  (peerState := peerState)
                  hPlan
                  hSelected
              simpa [hPeerAircraft] using hCover
      | reduceSpeedTo target maxSpeedKt =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              rcases hPeer with ⟨peerState, hSelected, hPeerAircraft⟩
              simp [instantiate_plan, hAir] at hPlan
              have hCover :=
                buildPlanWithAirAndSeparation_coversPeer
                  (graph := env.airGraph)
                  (state := state)
                  (command := .reduceSpeedTo target maxSpeedKt)
                  (runway := [])
                  (airProposal := { aircraft := target, state := airState, act := .reduceSpeedMax maxSpeedKt })
                  (peerState := peerState)
                  hPlan
                  hSelected
              simpa [hPeerAircraft] using hCover
      | climbTo target altitude =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              cases hBand : findAltitudeBandForAltitude env.airGraph.altitudeBands altitude with
              | none =>
                  simp [instantiate_plan, hAir, hBand] at hPlan
              | some band =>
                  rcases hPeer with ⟨peerState, hSelected, hPeerAircraft⟩
                  simp [instantiate_plan, hAir, hBand] at hPlan
                  have hCover :=
                    buildPlanWithAirAndSeparation_coversPeer
                      (graph := env.airGraph)
                      (state := state)
                      (command := .climbTo target altitude)
                      (runway := [])
                      (airProposal := { aircraft := target, state := airState, act := .changeAltitudeBand band.id })
                      (peerState := peerState)
                      hPlan
                      hSelected
                  simpa [hPeerAircraft] using hCover
      | descendTo target altitude =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              cases hBand : findAltitudeBandForAltitude env.airGraph.altitudeBands altitude with
              | none =>
                  simp [instantiate_plan, hAir, hBand] at hPlan
              | some band =>
                  rcases hPeer with ⟨peerState, hSelected, hPeerAircraft⟩
                  simp [instantiate_plan, hAir, hBand] at hPlan
                  have hCover :=
                    buildPlanWithAirAndSeparation_coversPeer
                      (graph := env.airGraph)
                      (state := state)
                      (command := .descendTo target altitude)
                      (runway := [])
                      (airProposal := { aircraft := target, state := airState, act := .changeAltitudeBand band.id })
                      (peerState := peerState)
                      hPlan
                      hSelected
                  simpa [hPeerAircraft] using hCover
      | clearedApproach target runway approachType =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              rcases hPeer with ⟨peerState, hSelected, hPeerAircraft⟩
              simp [instantiate_plan, hAir] at hPlan
              have hCover :=
                buildPlanWithAirAndSeparation_coversPeer
                  (graph := env.airGraph)
                  (state := state)
                  (command := .clearedApproach target runway approachType)
                  (runway := [])
                  (airProposal := { aircraft := target, state := airState, act := .continueOnEdge })
                  (peerState := peerState)
                  hPlan
                  hSelected
              simpa [hPeerAircraft] using hCover
      | crossControlledAirspace target airspaceClass =>
          cases hAir : lookupAirborneState state.air.aircraft target with
          | none =>
              simp [instantiate_plan, hAir] at hPlan
          | some airState =>
              rcases hPeer with ⟨peerState, hSelected, hPeerAircraft⟩
              simp [instantiate_plan, hAir] at hPlan
              have hCover :=
                buildPlanWithAirAndSeparation_coversPeer
                  (graph := env.airGraph)
                  (state := state)
                  (command := .crossControlledAirspace target airspaceClass)
                  (runway := [])
                  (airProposal := { aircraft := target, state := airState, act := .continueOnEdge })
                  (peerState := peerState)
                  hPlan
                  hSelected
              simpa [hPeerAircraft] using hCover
      | _ =>
          simp [instantiate_plan] at hPlan

theorem CompatibilityNarrownessTheorem :
  NarrowCompatibilityOnly compatibility_check := by
  refine ⟨narrowCompatibilityDecision, ?_⟩
  intro input
  rfl

theorem executeCertifiedPath_ok
    {env : OrchestrationEnv} {state : OrchestrationState}
    {proposal : CommandProposal} {certified : CertifiedPath} :
    executeCertifiedPath env state proposal = .ok certified →
      instantiate_plan env state proposal = .ok certified.plan ∧
      ApprovalsSatisfyPlan env state certified.plan certified.collected.approvals ∧
      compatibility_check
        { mode := state.mode
          activeSet := state.activeSet
          approvals := certified.collected.approvals } = .compatible := by
  intro hPath
  unfold executeCertifiedPath at hPath
  cases hPlan : instantiate_plan env state proposal with
  | error err =>
      simp [hPlan] at hPath
  | ok plan =>
      cases hCollected : collectApprovalBundle env state plan with
      | error reasons =>
          simp [hPlan, hCollected] at hPath
      | ok collected =>
          cases hCompat :
              compatibility_check
                { mode := state.mode
                  activeSet := state.activeSet
                  approvals := collected.approvals } with
          | compatible =>
              simp [hPlan, hCollected, hCompat] at hPath
              cases hPath
              constructor
              · simp
              constructor
              · exact ⟨collected, hCollected, rfl⟩
              · simp [hCompat]
          | incompatible reason =>
              simp [hPlan, hCollected, hCompat] at hPath

/--
Canonical orchestration theorem for the split architecture.

The local kernel soundness theorems are separate. The orchestration theorem says
only that issuance is impossible unless:

1. the command class compiled to its required certification plan,
2. every required local approval succeeded,
3. the narrow compatibility check accepted the resulting approval bundle.
-/
def CanonicalTopLevelTheorem : Prop :=
  ∀ env state proposal issued,
    NominalAssumptions env state →
    InterfaceInv state →
    issue_command env state proposal = .issued issued →
      ∃ plan approvals,
        instantiate_plan env state proposal = Except.ok plan ∧
        PlanMatchesTemplate (compile_command (classOf proposal.command)) plan ∧
        ApprovalsSatisfyPlan env state plan approvals ∧
        compatibility_check
          { mode := state.mode, activeSet := state.activeSet, approvals := approvals } = .compatible ∧
        InterfaceInv issued.newState

theorem NonBypassTheorem :
  ∀ env state proposal issued,
    issue_command env state proposal = .issued issued →
      ∃ plan approvals,
        instantiate_plan env state proposal = Except.ok plan ∧
        ApprovalsSatisfyPlan env state plan approvals ∧
        compatibility_check
          { mode := state.mode
            activeSet := state.activeSet
            approvals := approvals } = .compatible := by
  intro env state proposal issued hIssued
  unfold issue_command at hIssued
  cases hPath : executeCertifiedPath env state proposal with
  | error reasons =>
      simp [hPath] at hIssued
  | ok certified =>
      simp [hPath] at hIssued
      rcases executeCertifiedPath_ok hPath with ⟨hPlan, hBundle, hCompat⟩
      exact ⟨certified.plan, certified.collected.approvals, hPlan, hBundle, hCompat⟩

end CertifiedAtc
