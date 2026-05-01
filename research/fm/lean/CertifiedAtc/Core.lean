namespace CertifiedAtc

abbrev EntityId := String
abbrev AgentId := String
abbrev ClearanceId := String
abbrev CertificateId := String
abbrev RunwayId := String
abbrev SurfaceNodeId := String
abbrev SurfaceSegmentId := String
abbrev AirNodeId := String
abbrev AirEdgeId := String
abbrev AirJunctionId := String
abbrev AltitudeBandId := String
abbrev SeparationRuleId := String
abbrev Frequency := String
abbrev AerodromeId := String

structure LongitudinalMark where
  permille : Nat
  deriving DecidableEq, Repr

inductive Mode
  | normal
  | degraded (reason : String)
  | emergency (kind : String)
  deriving DecidableEq, Repr

inductive OperationalDomain
  | runwayGround
  | surfaceControl
  | airControl
  | coordination
  deriving DecidableEq, Repr

inductive CertificationKernel
  | runway
  | surface
  | airPath
  | separation
  deriving DecidableEq, Repr

inductive Ownership
  | runwayOwned
  | surfaceOwned
  | airOwned
  | joint (left : OperationalDomain) (right : OperationalDomain)
  | pendingTransfer (fromDomain : OperationalDomain) (toDomain : OperationalDomain)
  deriving DecidableEq, Repr

inductive ClearanceStatus
  | issued
  | readbackPending
  | conditionPending
  | active
  | superseded
  | completed
  | cancelled
  deriving DecidableEq, Repr

inductive AirspaceClass
  | g
  | e
  | d
  | c
  deriving DecidableEq, Repr

inductive CircuitDirection
  | left
  | right
  deriving DecidableEq, Repr

inductive JoinType
  | downwind
  | base
  | straightIn
  deriving DecidableEq, Repr

inductive OrbitDirection
  | left
  | right
  deriving DecidableEq, Repr

inductive Command
  | startupApproved (target : EntityId)
  | holdPosition (target : EntityId)
  | holdShortOf (target : EntityId) (runway : RunwayId)
  | taxiTo (target : EntityId) (route : List SurfaceNodeId) (destination : SurfaceNodeId)
  | crossRunway (target : EntityId) (runway : RunwayId)
  | backtrackRunway (target : EntityId) (runway : RunwayId)
  | lineUpAndWait (target : EntityId) (runway : RunwayId)
  | clearedForTakeoff (target : EntityId) (runway : RunwayId)
  | clearedToLand (target : EntityId) (runway : RunwayId)
  | clearedTouchAndGo (target : EntityId) (runway : RunwayId)
  | goAround (target : EntityId)
  | joinCircuit (target : EntityId) (direction : CircuitDirection) (joinType : JoinType)
      (runway : Option RunwayId := none)
  | orbit (target : EntityId) (direction : OrbitDirection)
  | extendDownwind (target : EntityId)
  | reportDownwind (target : EntityId)
  | reportFinal (target : EntityId)
  | continueApproach (target : EntityId)
  | proceed (target : EntityId)
  | contactFrequency (target : EntityId) (controller : AgentId)
      (frequency : Option Frequency := none)
  | monitorFrequency (target : EntityId) (controller : AgentId)
      (frequency : Option Frequency := none)
  | clearedTo (target : EntityId) (destination : AerodromeId)
      (route : List AirNodeId) (altitude : Int)
  | reduceSpeedTo (target : EntityId) (maxSpeedKt : Nat)
  | climbTo (target : EntityId) (altitude : Int)
  | descendTo (target : EntityId) (altitude : Int)
  | clearedApproach (target : EntityId) (runway : RunwayId) (approachType : String)
  | squawkCode (target : EntityId) (code : Nat)
  | crossControlledAirspace (target : EntityId) (airspaceClass : AirspaceClass)
  | holdAt (target : EntityId) (fix : AirNodeId) (direction : OrbitDirection)
  deriving DecidableEq, Repr

def commandTarget : Command → EntityId
  | .startupApproved target => target
  | .holdPosition target => target
  | .holdShortOf target _ => target
  | .taxiTo target _ _ => target
  | .crossRunway target _ => target
  | .backtrackRunway target _ => target
  | .lineUpAndWait target _ => target
  | .clearedForTakeoff target _ => target
  | .clearedToLand target _ => target
  | .clearedTouchAndGo target _ => target
  | .goAround target => target
  | .joinCircuit target _ _ _ => target
  | .orbit target _ => target
  | .extendDownwind target => target
  | .reportDownwind target => target
  | .reportFinal target => target
  | .continueApproach target => target
  | .proceed target => target
  | .contactFrequency target _ _ => target
  | .monitorFrequency target _ _ => target
  | .clearedTo target _ _ _ => target
  | .reduceSpeedTo target _ => target
  | .climbTo target _ => target
  | .descendTo target _ => target
  | .clearedApproach target _ _ => target
  | .squawkCode target _ => target
  | .crossControlledAirspace target _ => target
  | .holdAt target _ _ => target

structure CommandProposal where
  proposer : AgentId
  command : Command
  rationale : String := ""
  deriving DecidableEq, Repr

structure KernelCertificate where
  id : CertificateId
  kernel : CertificationKernel
  subject : EntityId
  issuedAtTick : Nat
  assumptions : List String := []
  deriving DecidableEq, Repr

structure ActiveClearance where
  id : ClearanceId
  command : Command
  certificates : List CertificateId
  status : ClearanceStatus
  dependsOn : List ClearanceId := []
  deriving DecidableEq, Repr

structure Footprint where
  runways : List RunwayId := []
  surfaceSegments : List SurfaceSegmentId := []
  airEdges : List AirEdgeId := []
  entities : List EntityId := []
  dependencies : List ClearanceId := []
  ownershipTags : List String := []
  modeTags : List String := []
  deriving DecidableEq, Repr

end CertifiedAtc
