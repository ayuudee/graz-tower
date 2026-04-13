import CertifiedAtc.Core

namespace CertifiedAtc

open Classical

structure AltitudeBand where
  id : AltitudeBandId
  lowerFt : Int
  upperFt : Int
  deriving DecidableEq, Repr

inductive AltitudeState
  | atBand (band : AltitudeBandId)
  | transitioning (fromBand : AltitudeBandId) (toBand : AltitudeBandId)
      (lowerFt : Int) (upperFt : Int)
  deriving DecidableEq, Repr

structure AirEdge where
  id : AirEdgeId
  fromNode : AirNodeId
  toNode : AirNodeId
  airspaceClass : AirspaceClass
  separationTrack : String := ""
  deriving DecidableEq, Repr

structure GuardPoint where
  fromEdge : AirEdgeId
  toEdge : AirEdgeId
  atMark : LongitudinalMark
  deriving DecidableEq, Repr

structure AirJunction where
  id : AirJunctionId
  incoming : List AirEdgeId
  outgoing : List AirEdgeId
  deriving DecidableEq, Repr

structure AirGraph where
  nodes : List AirNodeId
  edges : List AirEdge
  branches : List (AirEdgeId × AirEdgeId)
  junctions : List AirJunction
  guardPoints : List GuardPoint
  altitudeBands : List AltitudeBand
  deriving DecidableEq, Repr

structure AirReservation where
  aircraft : EntityId
  edge : AirEdgeId
  deriving DecidableEq, Repr

structure JunctionReservation where
  aircraft : EntityId
  junction : AirJunctionId
  deriving DecidableEq, Repr

structure AirborneState where
  edge : AirEdgeId
  longitudinal : LongitudinalMark
  speedMinKt : Nat
  speedMaxKt : Nat
  altitude : AltitudeState
  phaseTag : String := ""
  deriving DecidableEq, Repr

inductive AirAct
  | continueOnEdge
  | reduceSpeedMax (targetMaxKt : Nat)
  | takeBranch (next : AirEdgeId)
  | changeAltitudeBand (target : AltitudeBandId)
  | reserveJunction (junction : AirJunctionId)
  | activatePath (firstEdge : AirEdgeId)
  | activateMissedApproach (firstEdge : AirEdgeId)
  deriving DecidableEq, Repr

structure AirProposal where
  aircraft : EntityId
  state : AirborneState
  act : AirAct
  deriving DecidableEq, Repr

structure AirState where
  reservations : List AirReservation := []
  junctionReservations : List JunctionReservation := []
  aircraft : List (EntityId × AirborneState) := []
  deriving DecidableEq, Repr

structure AirEffect where
  reservationsAdded : List AirReservation := []
  junctionsAdded : List JunctionReservation := []
  aircraftUpdated : List (EntityId × AirborneState) := []
  footprint : Footprint := {}
  deriving DecidableEq, Repr

structure AirApproval where
  certificate : KernelCertificate
  effect : AirEffect
  successor : AirState
  deriving DecidableEq, Repr

inductive AirRejectReason
  | unknownEdge (edge : AirEdgeId)
  | illegalSpeedReduction (detail : String)
  | illegalBranch (fromEdge : AirEdgeId) (toEdge : AirEdgeId)
  | missingGuardReservation (fromEdge : AirEdgeId) (toEdge : AirEdgeId)
  | conflictingEdgeReservation (edge : AirEdgeId)
  | conflictingJunctionReservation (junction : AirJunctionId)
  | illegalAltitudeTransition (detail : String)
  | malformedProposal (detail : String)
  deriving DecidableEq, Repr

inductive AirDecision
  | approved (value : AirApproval)
  | rejected (reason : AirRejectReason)
  deriving DecidableEq, Repr

abbrev air_certify_sig : Type := AirGraph → AirState → AirProposal → AirDecision

def findAirEdge (graph : AirGraph) : AirEdgeId → Option AirEdge
  | id =>
      let rec go : List AirEdge → Option AirEdge
        | [] => none
        | edge :: tail =>
            if edge.id = id then
              some edge
            else
              go tail
      go graph.edges

def findAirJunction (graph : AirGraph) : AirJunctionId → Option AirJunction
  | id =>
      let rec go : List AirJunction → Option AirJunction
        | [] => none
        | junction :: tail =>
            if junction.id = id then
              some junction
            else
              go tail
      go graph.junctions

def findAltitudeBand (graph : AirGraph) : AltitudeBandId → Option AltitudeBand
  | id =>
      let rec go : List AltitudeBand → Option AltitudeBand
        | [] => none
        | band :: tail =>
            if band.id = id then
              some band
            else
              go tail
      go graph.altitudeBands

def lookupAirborneState :
    List (EntityId × AirborneState) → EntityId → Option AirborneState
  | [], _ => none
  | (aircraft, state) :: tail, target =>
      if aircraft = target then
        some state
      else
        lookupAirborneState tail target

def airReservationFor (aircraft : EntityId) (edge : AirEdgeId) : AirReservation :=
  { aircraft := aircraft, edge := edge }

def junctionReservationFor
    (aircraft : EntityId) (junction : AirJunctionId) : JunctionReservation :=
  { aircraft := aircraft, junction := junction }

def referencedAirEdges (proposal : AirProposal) : List AirEdgeId :=
  let current := proposal.state.edge
  match proposal.act with
  | .continueOnEdge => [current]
  | .reduceSpeedMax _ => [current]
  | .takeBranch next => [current, next]
  | .changeAltitudeBand _ => [current]
  | .reserveJunction _ => [current]
  | .activatePath firstEdge => [current, firstEdge]
  | .activateMissedApproach firstEdge => [current, firstEdge]

def AllAirEdgesKnown (graph : AirGraph) : List AirEdgeId → Prop
  | [] => True
  | edge :: tail =>
      findAirEdge graph edge ≠ none ∧
        AllAirEdgesKnown graph tail

theorem allAirEdgesKnown_head
    {graph : AirGraph} {edge : AirEdgeId}
    {tail : List AirEdgeId} :
    AllAirEdgesKnown graph (edge :: tail) →
      findAirEdge graph edge ≠ none := by
  intro hKnown
  exact hKnown.1

theorem allAirEdgesKnown_tail
    {graph : AirGraph} {edge : AirEdgeId}
    {tail : List AirEdgeId} :
    AllAirEdgesKnown graph (edge :: tail) →
      AllAirEdgesKnown graph tail := by
  simp [AllAirEdgesKnown]

def BranchLegal (graph : AirGraph) (fromEdge toEdge : AirEdgeId) : Prop :=
  (fromEdge, toEdge) ∈ graph.branches

def ActivationRouteLegal (graph : AirGraph)
    (current firstEdge : AirEdgeId) : Prop :=
  firstEdge = current ∨ BranchLegal graph current firstEdge

def JunctionReachable (graph : AirGraph)
    (current : AirEdgeId) (junction : AirJunctionId) : Prop :=
  match findAirJunction graph junction with
  | some value => current ∈ value.incoming
  | none => False

def JunctionSupportsTransition (graph : AirGraph)
    (junction : AirJunctionId) (fromEdge toEdge : AirEdgeId) : Prop :=
  match findAirJunction graph junction with
  | some value =>
      fromEdge ∈ value.incoming ∧
        toEdge ∈ value.outgoing
  | none => False

def GuardPointsGuardTransition
    (fromEdge toEdge : AirEdgeId) : List GuardPoint → Prop
  | [] => False
  | guard :: tail =>
      (guard.fromEdge = fromEdge ∧ guard.toEdge = toEdge) ∨
        GuardPointsGuardTransition fromEdge toEdge tail

def GuardedTransition (graph : AirGraph)
    (fromEdge toEdge : AirEdgeId) : Prop :=
  GuardPointsGuardTransition fromEdge toEdge graph.guardPoints

def TransitionReservationHeld
    (graph : AirGraph) (aircraft : EntityId)
    (fromEdge toEdge : AirEdgeId) : List JunctionReservation → Prop
  | [] => False
  | reservation :: tail =>
      (reservation.aircraft = aircraft ∧
        JunctionSupportsTransition graph reservation.junction fromEdge toEdge) ∨
        TransitionReservationHeld graph aircraft fromEdge toEdge tail

def GuardReservationHeld
    (graph : AirGraph) (state : AirState)
    (aircraft : EntityId) (fromEdge toEdge : AirEdgeId) : Prop :=
  if GuardedTransition graph fromEdge toEdge then
    TransitionReservationHeld graph aircraft fromEdge toEdge
      state.junctionReservations
  else
    True

def ReservationsAllowEdge
    (aircraft : EntityId) (edge : AirEdgeId) :
    List AirReservation → Prop
  | [] => True
  | reservation :: tail =>
      (reservation.edge = edge → reservation.aircraft = aircraft) ∧
        ReservationsAllowEdge aircraft edge tail

def EdgeAvailable
    (state : AirState) (aircraft : EntityId)
    (edge : AirEdgeId) : Prop :=
  ReservationsAllowEdge aircraft edge state.reservations

def ReservationsAllowJunction
    (aircraft : EntityId) (junction : AirJunctionId) :
    List JunctionReservation → Prop
  | [] => True
  | reservation :: tail =>
      (reservation.junction = junction → reservation.aircraft = aircraft) ∧
        ReservationsAllowJunction aircraft junction tail

def JunctionAvailable
    (state : AirState) (aircraft : EntityId)
    (junction : AirJunctionId) : Prop :=
  ReservationsAllowJunction aircraft junction state.junctionReservations

def AltitudeStateKnown (graph : AirGraph) : AltitudeState → Prop
  | .atBand band =>
      findAltitudeBand graph band ≠ none
  | .transitioning fromBand toBand _ _ =>
      findAltitudeBand graph fromBand ≠ none ∧
        findAltitudeBand graph toBand ≠ none

def AltitudeTransitionLegal
    (graph : AirGraph) (altitude : AltitudeState)
    (target : AltitudeBandId) : Prop :=
  findAltitudeBand graph target ≠ none ∧
    match altitude with
    | .atBand band =>
        findAltitudeBand graph band ≠ none ∧
          band ≠ target
    | .transitioning fromBand toBand _ _ =>
        findAltitudeBand graph fromBand ≠ none ∧
          findAltitudeBand graph toBand ≠ none ∧
          toBand ≠ target

def AirEdgesUseKnownNodes (graph : AirGraph) : List AirEdge → Prop
  | [] => True
  | edge :: tail =>
      edge.fromNode ∈ graph.nodes ∧
        edge.toNode ∈ graph.nodes ∧
        AirEdgesUseKnownNodes graph tail

def BranchesConsistent (graph : AirGraph) :
    List (AirEdgeId × AirEdgeId) → Prop
  | [] => True
  | branch :: tail =>
      match findAirEdge graph branch.1, findAirEdge graph branch.2 with
      | some fromEdge, some toEdge =>
          fromEdge.toNode = toEdge.fromNode ∧
            BranchesConsistent graph tail
      | _, _ => False

def JunctionReferencesKnownEdges
    (graph : AirGraph) (junction : AirJunction) : Prop :=
  AllAirEdgesKnown graph junction.incoming ∧
    AllAirEdgesKnown graph junction.outgoing

def JunctionsUseKnownEdges (graph : AirGraph) : List AirJunction → Prop
  | [] => True
  | junction :: tail =>
      JunctionReferencesKnownEdges graph junction ∧
        JunctionsUseKnownEdges graph tail

def GuardPointsUseKnownEdges (graph : AirGraph) : List GuardPoint → Prop
  | [] => True
  | guard :: tail =>
      findAirEdge graph guard.fromEdge ≠ none ∧
        findAirEdge graph guard.toEdge ≠ none ∧
        BranchLegal graph guard.fromEdge guard.toEdge ∧
        GuardPointsUseKnownEdges graph tail

def AirWellFormed (graph : AirGraph) : Prop :=
  graph.nodes.Nodup ∧
    (graph.edges.map AirEdge.id).Nodup ∧
    (graph.junctions.map AirJunction.id).Nodup ∧
    (graph.altitudeBands.map AltitudeBand.id).Nodup ∧
    AirEdgesUseKnownNodes graph graph.edges ∧
    BranchesConsistent graph graph.branches ∧
    JunctionsUseKnownEdges graph graph.junctions ∧
    GuardPointsUseKnownEdges graph graph.guardPoints

def AirReservationsKnown (graph : AirGraph) : List AirReservation → Prop
  | [] => True
  | reservation :: tail =>
      findAirEdge graph reservation.edge ≠ none ∧
        AirReservationsKnown graph tail

def JunctionReservationsKnown (graph : AirGraph) :
    List JunctionReservation → Prop
  | [] => True
  | reservation :: tail =>
      findAirJunction graph reservation.junction ≠ none ∧
        JunctionReservationsKnown graph tail

def AirborneStatesKnown (graph : AirGraph) :
    List (EntityId × AirborneState) → Prop
  | [] => True
  | (_, state) :: tail =>
      findAirEdge graph state.edge ≠ none ∧
        AltitudeStateKnown graph state.altitude ∧
        AirborneStatesKnown graph tail

def AirInv (graph : AirGraph) (state : AirState) : Prop :=
  AirReservationsKnown graph state.reservations ∧
    JunctionReservationsKnown graph state.junctionReservations ∧
    AirborneStatesKnown graph state.aircraft

def airFootprint (aircraft : EntityId)
    (edges : List AirEdgeId) : Footprint :=
  { airEdges := edges
    entities := [aircraft] }

def bandWindow (graph : AirGraph) (target : AltitudeBandId) : Int × Int :=
  match findAltitudeBand graph target with
  | some band => (band.lowerFt, band.upperFt)
  | none => (0, 0)

def transitionAltitudeState
    (graph : AirGraph) (current : AltitudeState)
    (target : AltitudeBandId) : AltitudeState :=
  let (lower, upper) := bandWindow graph target
  match current with
  | .atBand band =>
      .transitioning band target lower upper
  | .transitioning fromBand _ _ _ =>
      .transitioning fromBand target lower upper

def speedReducedState
    (current : AirborneState) (targetMaxKt : Nat) : AirborneState :=
  { current with
      speedMaxKt := targetMaxKt }

def SpeedReductionLegal
    (state : AirborneState) (targetMaxKt : Nat) : Prop :=
  state.speedMinKt ≤ targetMaxKt ∧
    targetMaxKt < state.speedMaxKt

def branchSuccessorState
    (current : AirborneState) (next : AirEdgeId) : AirborneState :=
  { current with
      edge := next
      longitudinal := { permille := 0 } }

def activatePathSuccessorState
    (current : AirborneState) (firstEdge : AirEdgeId) : AirborneState :=
  { current with
      edge := firstEdge
      longitudinal :=
        if firstEdge = current.edge then
          current.longitudinal
        else
          { permille := 0 }
      phaseTag := "path-active" }

def activateMissedApproachSuccessorState
    (current : AirborneState) (firstEdge : AirEdgeId) : AirborneState :=
  { current with
      edge := firstEdge
      longitudinal :=
        if firstEdge = current.edge then
          current.longitudinal
        else
          { permille := 0 }
      phaseTag := "missed-approach" }

def airProposalEffect (graph : AirGraph) : AirProposal → AirEffect
  | { aircraft := aircraft, state := state, act := .continueOnEdge } =>
      { footprint := airFootprint aircraft [state.edge] }
  | { aircraft := aircraft, state := state, act := .reduceSpeedMax targetMaxKt } =>
      { aircraftUpdated := [(aircraft, speedReducedState state targetMaxKt)]
        footprint := airFootprint aircraft [state.edge] }
  | { aircraft := aircraft, state := state, act := .takeBranch next } =>
      { reservationsAdded := [airReservationFor aircraft next]
        aircraftUpdated := [(aircraft, branchSuccessorState state next)]
        footprint := airFootprint aircraft [state.edge, next] }
  | { aircraft := aircraft, state := state, act := .changeAltitudeBand target } =>
      { aircraftUpdated := [(aircraft, { state with altitude := transitionAltitudeState graph state.altitude target })]
        footprint := airFootprint aircraft [state.edge] }
  | { aircraft := aircraft, state := state, act := .reserveJunction junction } =>
      { junctionsAdded := [junctionReservationFor aircraft junction]
        footprint := airFootprint aircraft [state.edge] }
  | { aircraft := aircraft, state := state, act := .activatePath firstEdge } =>
      { reservationsAdded := [airReservationFor aircraft firstEdge]
        aircraftUpdated := [(aircraft, activatePathSuccessorState state firstEdge)]
        footprint := airFootprint aircraft [firstEdge] }
  | { aircraft := aircraft, state := state, act := .activateMissedApproach firstEdge } =>
      { reservationsAdded := [airReservationFor aircraft firstEdge]
        aircraftUpdated := [(aircraft, activateMissedApproachSuccessorState state firstEdge)]
        footprint := airFootprint aircraft [firstEdge] }

def applyAirProposal (graph : AirGraph) :
    AirState → AirProposal → AirState
  | state, { aircraft := _, act := .continueOnEdge, .. } =>
      state
  | state, { aircraft := aircraft, state := current, act := .reduceSpeedMax targetMaxKt } =>
      { reservations := state.reservations
        junctionReservations := state.junctionReservations
        aircraft :=
          (aircraft, speedReducedState current targetMaxKt) :: state.aircraft }
  | state, { aircraft := aircraft, state := current, act := .takeBranch next } =>
      { reservations := airReservationFor aircraft next :: state.reservations
        junctionReservations := state.junctionReservations
        aircraft := (aircraft, branchSuccessorState current next) :: state.aircraft }
  | state, { aircraft := aircraft, state := current, act := .changeAltitudeBand target } =>
      { reservations := state.reservations
        junctionReservations := state.junctionReservations
        aircraft :=
          (aircraft, { current with altitude := transitionAltitudeState graph current.altitude target }) ::
            state.aircraft }
  | state, { aircraft := aircraft, act := .reserveJunction junction, .. } =>
      { reservations := state.reservations
        junctionReservations :=
          junctionReservationFor aircraft junction :: state.junctionReservations
        aircraft := state.aircraft }
  | state, { aircraft := aircraft, state := current, act := .activatePath firstEdge } =>
      { reservations := airReservationFor aircraft firstEdge :: state.reservations
        junctionReservations := state.junctionReservations
        aircraft := (aircraft, activatePathSuccessorState current firstEdge) :: state.aircraft }
  | state, { aircraft := aircraft, state := current, act := .activateMissedApproach firstEdge } =>
      { reservations := airReservationFor aircraft firstEdge :: state.reservations
        junctionReservations := state.junctionReservations
        aircraft :=
          (aircraft, activateMissedApproachSuccessorState current firstEdge) ::
            state.aircraft }

def mkAirCertificate (state : AirState)
    (proposal : AirProposal) : KernelCertificate :=
  { id := s!"air:{state.reservations.length}:{proposal.aircraft}:{proposal.state.edge}"
    kernel := .airPath
    subject := proposal.aircraft
    issuedAtTick :=
      state.reservations.length +
        state.junctionReservations.length +
        state.aircraft.length
    assumptions := ["graph-local", "guard-reservation-explicit"] }

def mkAirApproval (graph : AirGraph)
    (state : AirState) (proposal : AirProposal) : AirApproval :=
  { certificate := mkAirCertificate state proposal
    effect := airProposalEffect graph proposal
    successor := applyAirProposal graph state proposal }

def AirLocalOk
    (graph : AirGraph) (state : AirState)
    (proposal : AirProposal) : Prop :=
  lookupAirborneState state.aircraft proposal.aircraft = some proposal.state ∧
    AllAirEdgesKnown graph (referencedAirEdges proposal) ∧
    match proposal.act with
    | .continueOnEdge =>
        EdgeAvailable state proposal.aircraft proposal.state.edge
    | .reduceSpeedMax targetMaxKt =>
        EdgeAvailable state proposal.aircraft proposal.state.edge ∧
          SpeedReductionLegal proposal.state targetMaxKt
    | .takeBranch next =>
        BranchLegal graph proposal.state.edge next ∧
          GuardReservationHeld graph state proposal.aircraft proposal.state.edge next ∧
          EdgeAvailable state proposal.aircraft next
    | .changeAltitudeBand target =>
        AltitudeTransitionLegal graph proposal.state.altitude target
    | .reserveJunction junction =>
        JunctionReachable graph proposal.state.edge junction ∧
          JunctionAvailable state proposal.aircraft junction
    | .activatePath firstEdge =>
        ActivationRouteLegal graph proposal.state.edge firstEdge ∧
          EdgeAvailable state proposal.aircraft firstEdge
    | .activateMissedApproach firstEdge =>
        ActivationRouteLegal graph proposal.state.edge firstEdge ∧
          EdgeAvailable state proposal.aircraft firstEdge

def AirCertificateSound
    (graph : AirGraph) (state : AirState)
    (proposal : AirProposal) (approval : AirApproval) : Prop :=
  approval.certificate.kernel = .airPath ∧
    approval.certificate.subject = proposal.aircraft ∧
    approval.certificate.issuedAtTick =
      state.reservations.length +
        state.junctionReservations.length +
        state.aircraft.length ∧
    AirLocalOk graph state proposal ∧
    approval.effect = airProposalEffect graph proposal ∧
    approval.successor = applyAirProposal graph state proposal

def firstUnknownAirEdge (graph : AirGraph) : List AirEdgeId → AirEdgeId
  | [] => ""
  | edge :: tail =>
      if findAirEdge graph edge = none then
        edge
      else
        firstUnknownAirEdge graph tail

noncomputable def air_certify : air_certify_sig := fun graph state proposal =>
  if _ : lookupAirborneState state.aircraft proposal.aircraft = some proposal.state then
    if _ : AllAirEdgesKnown graph (referencedAirEdges proposal) then
      match proposal.act with
      | .continueOnEdge =>
          if _ : EdgeAvailable state proposal.aircraft proposal.state.edge then
            .approved (mkAirApproval graph state proposal)
          else
            .rejected (.conflictingEdgeReservation proposal.state.edge)
      | .reduceSpeedMax targetMaxKt =>
          if _ : EdgeAvailable state proposal.aircraft proposal.state.edge then
            if _ : SpeedReductionLegal proposal.state targetMaxKt then
              .approved (mkAirApproval graph state proposal)
            else
              .rejected
                (.illegalSpeedReduction
                  s!"aircraft {proposal.aircraft} cannot reduce speed max to {targetMaxKt}")
          else
            .rejected (.conflictingEdgeReservation proposal.state.edge)
      | .takeBranch next =>
          if _ : BranchLegal graph proposal.state.edge next then
            if _ : GuardReservationHeld graph state proposal.aircraft proposal.state.edge next then
              if _ : EdgeAvailable state proposal.aircraft next then
                .approved (mkAirApproval graph state proposal)
              else
                .rejected (.conflictingEdgeReservation next)
            else
              .rejected (.missingGuardReservation proposal.state.edge next)
          else
            .rejected (.illegalBranch proposal.state.edge next)
      | .changeAltitudeBand target =>
          if _ : AltitudeTransitionLegal graph proposal.state.altitude target then
            .approved (mkAirApproval graph state proposal)
          else
            .rejected
              (.illegalAltitudeTransition
                s!"aircraft {proposal.aircraft} cannot change altitude to {target}")
      | .reserveJunction junction =>
          if _ : JunctionReachable graph proposal.state.edge junction then
            if _ : JunctionAvailable state proposal.aircraft junction then
              .approved (mkAirApproval graph state proposal)
            else
              .rejected (.conflictingJunctionReservation junction)
          else
            .rejected
              (.malformedProposal
                s!"junction {junction} is not reachable from {proposal.state.edge}")
      | .activatePath firstEdge =>
          if _ : ActivationRouteLegal graph proposal.state.edge firstEdge then
            if _ : EdgeAvailable state proposal.aircraft firstEdge then
              .approved (mkAirApproval graph state proposal)
            else
              .rejected (.conflictingEdgeReservation firstEdge)
          else
            .rejected (.illegalBranch proposal.state.edge firstEdge)
      | .activateMissedApproach firstEdge =>
          if _ : ActivationRouteLegal graph proposal.state.edge firstEdge then
            if _ : EdgeAvailable state proposal.aircraft firstEdge then
              .approved (mkAirApproval graph state proposal)
            else
              .rejected (.conflictingEdgeReservation firstEdge)
          else
            .rejected (.illegalBranch proposal.state.edge firstEdge)
    else
      .rejected (.unknownEdge (firstUnknownAirEdge graph (referencedAirEdges proposal)))
  else
    .rejected
      (.malformedProposal
        s!"stale or missing airborne state for {proposal.aircraft}")

theorem lookupAirborneState_known
    {graph : AirGraph} {aircraft : EntityId}
    {targetState : AirborneState} :
    ∀ {states : List (EntityId × AirborneState)},
      AirborneStatesKnown graph states →
        lookupAirborneState states aircraft = some targetState →
          findAirEdge graph targetState.edge ≠ none ∧
            AltitudeStateKnown graph targetState.altitude := by
  intro states hKnown hLookup
  induction states with
  | nil =>
      simp [lookupAirborneState] at hLookup
  | cons entry tail ih =>
      simp [AirborneStatesKnown] at hKnown
      by_cases hEq : entry.1 = aircraft
      · simp [lookupAirborneState, hEq] at hLookup
        cases hLookup
        exact ⟨hKnown.1, hKnown.2.1⟩
      · simp [lookupAirborneState, hEq] at hLookup
        exact ih hKnown.2.2 hLookup

theorem altitudeTransitionLegal_target_known
    {graph : AirGraph} {altitude : AltitudeState}
    {target : AltitudeBandId} :
    AltitudeTransitionLegal graph altitude target →
      findAltitudeBand graph target ≠ none := by
  intro hLegal
  exact hLegal.1

theorem junctionReachable_known
    {graph : AirGraph} {edge : AirEdgeId}
    {junction : AirJunctionId} :
    JunctionReachable graph edge junction →
      findAirJunction graph junction ≠ none := by
  unfold JunctionReachable
  cases hFind : findAirJunction graph junction with
  | none =>
      simp
  | some value =>
      intro _
      simp

theorem transitionAltitudeState_known
    {graph : AirGraph} {current : AltitudeState}
    {target : AltitudeBandId}
    (hCurrent : AltitudeStateKnown graph current)
    (hTarget : findAltitudeBand graph target ≠ none) :
    AltitudeStateKnown graph (transitionAltitudeState graph current target) := by
  cases current with
  | atBand band =>
      exact ⟨hCurrent, hTarget⟩
  | transitioning fromBand toBand lower upper =>
      exact ⟨hCurrent.1, hTarget⟩

theorem mkAirApproval_sound
    {graph : AirGraph} {state : AirState}
    {proposal : AirProposal}
    (hLocal : AirLocalOk graph state proposal) :
    AirCertificateSound graph state proposal (mkAirApproval graph state proposal) := by
  simp [AirCertificateSound, mkAirApproval, mkAirCertificate, hLocal]

theorem continue_preserves_inv
    {graph : AirGraph} {state : AirState}
    {aircraft : EntityId} {airState : AirborneState}
    (hInv : AirInv graph state) :
    AirInv graph
      (applyAirProposal graph state
        { aircraft := aircraft
          state := airState
          act := .continueOnEdge }) := by
  simpa [applyAirProposal] using hInv

theorem reduceSpeed_preserves_inv
    {graph : AirGraph} {state : AirState}
    {aircraft : EntityId} {airState : AirborneState}
    {targetMaxKt : Nat}
    (hInv : AirInv graph state)
    (hCurrent : lookupAirborneState state.aircraft aircraft = some airState) :
    AirInv graph
      (applyAirProposal graph state
        { aircraft := aircraft
          state := airState
          act := .reduceSpeedMax targetMaxKt }) := by
  rcases hInv with ⟨hReservationsKnown, hJunctionKnown, hAircraftKnown⟩
  have hCurrentKnown := lookupAirborneState_known hAircraftKnown hCurrent
  constructor
  · simpa [applyAirProposal] using hReservationsKnown
  · constructor
    · simpa [applyAirProposal] using hJunctionKnown
    · simp [applyAirProposal, AirborneStatesKnown, speedReducedState,
        hCurrentKnown.1, hCurrentKnown.2, hAircraftKnown]

theorem takeBranch_preserves_inv
    {graph : AirGraph} {state : AirState}
    {aircraft : EntityId} {airState : AirborneState}
    {next : AirEdgeId}
    (hInv : AirInv graph state)
    (hCurrent : lookupAirborneState state.aircraft aircraft = some airState)
    (hNextKnown : findAirEdge graph next ≠ none) :
    AirInv graph
      (applyAirProposal graph state
        { aircraft := aircraft
          state := airState
          act := .takeBranch next }) := by
  rcases hInv with ⟨hReservationsKnown, hJunctionKnown, hAircraftKnown⟩
  have hCurrentKnown := lookupAirborneState_known hAircraftKnown hCurrent
  constructor
  · exact ⟨hNextKnown, hReservationsKnown⟩
  · constructor
    · simpa [applyAirProposal] using hJunctionKnown
    · simp [applyAirProposal, AirborneStatesKnown, branchSuccessorState,
        hNextKnown, hCurrentKnown.2, hAircraftKnown]

theorem reserveJunction_preserves_inv
    {graph : AirGraph} {state : AirState}
    {aircraft : EntityId} {airState : AirborneState}
    {junction : AirJunctionId}
    (hInv : AirInv graph state)
    (hReachable : JunctionReachable graph airState.edge junction) :
  AirInv graph
      (applyAirProposal graph state
        { aircraft := aircraft
          state := airState
          act := .reserveJunction junction }) := by
  rcases hInv with ⟨hReservationsKnown, hJunctionReservationsKnown, hAircraftKnown⟩
  have hJunctionKnown : findAirJunction graph junction ≠ none :=
    junctionReachable_known hReachable
  constructor
  · simpa [applyAirProposal] using hReservationsKnown
  · constructor
    · exact ⟨hJunctionKnown, hJunctionReservationsKnown⟩
    · simpa [applyAirProposal] using hAircraftKnown

theorem changeAltitude_preserves_inv
    {graph : AirGraph} {state : AirState}
    {aircraft : EntityId} {airState : AirborneState}
    {target : AltitudeBandId}
    (hInv : AirInv graph state)
    (hCurrent : lookupAirborneState state.aircraft aircraft = some airState)
    (hAltitude : AltitudeTransitionLegal graph airState.altitude target) :
    AirInv graph
      (applyAirProposal graph state
        { aircraft := aircraft
          state := airState
          act := .changeAltitudeBand target }) := by
  rcases hInv with ⟨hReservationsKnown, hJunctionKnown, hAircraftKnown⟩
  have hCurrentKnown := lookupAirborneState_known hAircraftKnown hCurrent
  have hTargetKnown : findAltitudeBand graph target ≠ none :=
    altitudeTransitionLegal_target_known hAltitude
  have hAltitudeKnown :
      AltitudeStateKnown graph
        (transitionAltitudeState graph airState.altitude target) :=
    transitionAltitudeState_known hCurrentKnown.2 hTargetKnown
  constructor
  · simpa [applyAirProposal] using hReservationsKnown
  · constructor
    · simpa [applyAirProposal] using hJunctionKnown
    · simp [applyAirProposal, AirborneStatesKnown, hCurrentKnown.1,
        hAltitudeKnown, hAircraftKnown]

theorem activatePath_preserves_inv
    {graph : AirGraph} {state : AirState}
    {aircraft : EntityId} {airState : AirborneState}
    {firstEdge : AirEdgeId}
    (hInv : AirInv graph state)
    (hCurrent : lookupAirborneState state.aircraft aircraft = some airState)
    (hFirstKnown : findAirEdge graph firstEdge ≠ none) :
    AirInv graph
      (applyAirProposal graph state
        { aircraft := aircraft
          state := airState
          act := .activatePath firstEdge }) := by
  rcases hInv with ⟨hReservationsKnown, hJunctionKnown, hAircraftKnown⟩
  have hCurrentKnown := lookupAirborneState_known hAircraftKnown hCurrent
  constructor
  · exact ⟨hFirstKnown, hReservationsKnown⟩
  · constructor
    · simpa [applyAirProposal] using hJunctionKnown
    · simp [applyAirProposal, AirborneStatesKnown, activatePathSuccessorState,
        hFirstKnown, hCurrentKnown.2, hAircraftKnown]

theorem activateMissedApproach_preserves_inv
    {graph : AirGraph} {state : AirState}
    {aircraft : EntityId} {airState : AirborneState}
    {firstEdge : AirEdgeId}
    (hInv : AirInv graph state)
    (hCurrent : lookupAirborneState state.aircraft aircraft = some airState)
    (hFirstKnown : findAirEdge graph firstEdge ≠ none) :
    AirInv graph
      (applyAirProposal graph state
        { aircraft := aircraft
          state := airState
          act := .activateMissedApproach firstEdge }) := by
  rcases hInv with ⟨hReservationsKnown, hJunctionKnown, hAircraftKnown⟩
  have hCurrentKnown := lookupAirborneState_known hAircraftKnown hCurrent
  constructor
  · exact ⟨hFirstKnown, hReservationsKnown⟩
  · constructor
    · simpa [applyAirProposal] using hJunctionKnown
    · simp [applyAirProposal, AirborneStatesKnown,
        activateMissedApproachSuccessorState,
        hFirstKnown, hCurrentKnown.2, hAircraftKnown]

/--
The air kernel is now a concrete local checker over an airborne graph,
guarded branch transitions, altitude-band transitions, explicit
junction reservations, and speed-bound reductions.
-/
theorem AirKernelSoundnessTheorem :
  ∀ graph state proposal approval,
    AirWellFormed graph →
    AirInv graph state →
    air_certify graph state proposal = .approved approval →
      AirCertificateSound graph state proposal approval ∧
      AirInv graph approval.successor := by
  intro graph state proposal approval _hWellFormed hInv hApproved
  cases proposal with
  | mk aircraft airState act =>
      cases act with
      | continueOnEdge =>
          unfold air_certify at hApproved
          by_cases hCurrent : lookupAirborneState state.aircraft aircraft = some airState
          · by_cases hKnown : AllAirEdgesKnown graph [airState.edge]
            · by_cases hAvailable : EdgeAvailable state aircraft airState.edge
              · simp [referencedAirEdges, hCurrent, hKnown, hAvailable] at hApproved
                cases hApproved
                constructor
                ·
                  apply mkAirApproval_sound
                  simp [AirLocalOk, referencedAirEdges, hCurrent, hKnown, hAvailable]
                · exact continue_preserves_inv
                    (aircraft := aircraft) (airState := airState) hInv
              · simp [referencedAirEdges, hCurrent, hKnown, hAvailable] at hApproved
            · simp [referencedAirEdges, hCurrent, hKnown] at hApproved
          · simp [hCurrent] at hApproved
      | reduceSpeedMax targetMaxKt =>
          unfold air_certify at hApproved
          by_cases hCurrent : lookupAirborneState state.aircraft aircraft = some airState
          · by_cases hKnown : AllAirEdgesKnown graph [airState.edge]
            · by_cases hAvailable : EdgeAvailable state aircraft airState.edge
              · by_cases hSpeed : SpeedReductionLegal airState targetMaxKt
                · simp [referencedAirEdges, hCurrent, hKnown, hAvailable, hSpeed] at hApproved
                  cases hApproved
                  constructor
                  ·
                    apply mkAirApproval_sound
                    simp [AirLocalOk, referencedAirEdges, hCurrent, hKnown,
                      hAvailable, hSpeed]
                  · exact reduceSpeed_preserves_inv hInv hCurrent
                · simp [referencedAirEdges, hCurrent, hKnown, hAvailable, hSpeed] at hApproved
              · simp [referencedAirEdges, hCurrent, hKnown, hAvailable] at hApproved
            · simp [referencedAirEdges, hCurrent, hKnown] at hApproved
          · simp [hCurrent] at hApproved
      | takeBranch next =>
          unfold air_certify at hApproved
          by_cases hCurrent : lookupAirborneState state.aircraft aircraft = some airState
          · by_cases hKnown : AllAirEdgesKnown graph [airState.edge, next]
            · by_cases hBranch : BranchLegal graph airState.edge next
              · by_cases hGuard : GuardReservationHeld graph state aircraft airState.edge next
                · by_cases hAvailable : EdgeAvailable state aircraft next
                  · simp [referencedAirEdges, hCurrent, hKnown, hBranch, hGuard, hAvailable] at hApproved
                    cases hApproved
                    have hNextKnown : findAirEdge graph next ≠ none :=
                      allAirEdgesKnown_head (allAirEdgesKnown_tail hKnown)
                    constructor
                    ·
                      apply mkAirApproval_sound
                      simp [AirLocalOk, referencedAirEdges, hCurrent, hKnown,
                        hBranch, hGuard, hAvailable]
                    · exact takeBranch_preserves_inv hInv hCurrent hNextKnown
                  · simp [referencedAirEdges, hCurrent, hKnown, hBranch, hGuard, hAvailable] at hApproved
                · simp [referencedAirEdges, hCurrent, hKnown, hBranch, hGuard] at hApproved
              · simp [referencedAirEdges, hCurrent, hKnown, hBranch] at hApproved
            · simp [referencedAirEdges, hCurrent, hKnown] at hApproved
          · simp [hCurrent] at hApproved
      | changeAltitudeBand target =>
          unfold air_certify at hApproved
          by_cases hCurrent : lookupAirborneState state.aircraft aircraft = some airState
          · by_cases hKnown : AllAirEdgesKnown graph [airState.edge]
            · by_cases hAltitude : AltitudeTransitionLegal graph airState.altitude target
              · simp [referencedAirEdges, hCurrent, hKnown, hAltitude] at hApproved
                cases hApproved
                constructor
                ·
                  apply mkAirApproval_sound
                  simp [AirLocalOk, referencedAirEdges, hCurrent, hKnown, hAltitude]
                · exact changeAltitude_preserves_inv hInv hCurrent hAltitude
              · simp [referencedAirEdges, hCurrent, hKnown, hAltitude] at hApproved
            · simp [referencedAirEdges, hCurrent, hKnown] at hApproved
          · simp [hCurrent] at hApproved
      | reserveJunction junction =>
          unfold air_certify at hApproved
          by_cases hCurrent : lookupAirborneState state.aircraft aircraft = some airState
          · by_cases hKnown : AllAirEdgesKnown graph [airState.edge]
            · by_cases hReachable : JunctionReachable graph airState.edge junction
              · by_cases hAvailable : JunctionAvailable state aircraft junction
                · simp [referencedAirEdges, hCurrent, hKnown, hReachable, hAvailable] at hApproved
                  cases hApproved
                  constructor
                  ·
                    apply mkAirApproval_sound
                    simp [AirLocalOk, referencedAirEdges, hCurrent, hKnown,
                      hReachable, hAvailable]
                  · exact reserveJunction_preserves_inv hInv hReachable
                · simp [referencedAirEdges, hCurrent, hKnown, hReachable, hAvailable] at hApproved
              · simp [referencedAirEdges, hCurrent, hKnown, hReachable] at hApproved
            · simp [referencedAirEdges, hCurrent, hKnown] at hApproved
          · simp [hCurrent] at hApproved
      | activatePath firstEdge =>
          unfold air_certify at hApproved
          by_cases hCurrent : lookupAirborneState state.aircraft aircraft = some airState
          · by_cases hKnown : AllAirEdgesKnown graph [airState.edge, firstEdge]
            · by_cases hRoute : ActivationRouteLegal graph airState.edge firstEdge
              · by_cases hAvailable : EdgeAvailable state aircraft firstEdge
                · simp [referencedAirEdges, hCurrent, hKnown, hRoute, hAvailable] at hApproved
                  cases hApproved
                  have hFirstKnown : findAirEdge graph firstEdge ≠ none :=
                    allAirEdgesKnown_head (allAirEdgesKnown_tail hKnown)
                  constructor
                  ·
                    apply mkAirApproval_sound
                    simp [AirLocalOk, referencedAirEdges, hCurrent, hKnown,
                      hRoute, hAvailable]
                  · exact activatePath_preserves_inv hInv hCurrent hFirstKnown
                · simp [referencedAirEdges, hCurrent, hKnown, hRoute, hAvailable] at hApproved
              · simp [referencedAirEdges, hCurrent, hKnown, hRoute] at hApproved
            · simp [referencedAirEdges, hCurrent, hKnown] at hApproved
          · simp [hCurrent] at hApproved
      | activateMissedApproach firstEdge =>
          unfold air_certify at hApproved
          by_cases hCurrent : lookupAirborneState state.aircraft aircraft = some airState
          · by_cases hKnown : AllAirEdgesKnown graph [airState.edge, firstEdge]
            · by_cases hRoute : ActivationRouteLegal graph airState.edge firstEdge
              · by_cases hAvailable : EdgeAvailable state aircraft firstEdge
                · simp [referencedAirEdges, hCurrent, hKnown, hRoute, hAvailable] at hApproved
                  cases hApproved
                  have hFirstKnown : findAirEdge graph firstEdge ≠ none :=
                    allAirEdgesKnown_head (allAirEdgesKnown_tail hKnown)
                  constructor
                  ·
                    apply mkAirApproval_sound
                    simp [AirLocalOk, referencedAirEdges, hCurrent, hKnown,
                      hRoute, hAvailable]
                  · exact activateMissedApproach_preserves_inv hInv hCurrent hFirstKnown
                · simp [referencedAirEdges, hCurrent, hKnown, hRoute, hAvailable] at hApproved
              · simp [referencedAirEdges, hCurrent, hKnown, hRoute] at hApproved
            · simp [referencedAirEdges, hCurrent, hKnown] at hApproved
          · simp [hCurrent] at hApproved

def TestAirGraph : AirGraph :=
  { nodes := ["APP", "FINAL", "MISSED"]
    edges :=
      [ { id := "app_final"
          fromNode := "APP"
          toNode := "FINAL"
          airspaceClass := .d
          separationTrack := "final-approach" }
      , { id := "final_missed"
          fromNode := "FINAL"
          toNode := "MISSED"
          airspaceClass := .d
          separationTrack := "missed-approach" } ]
    branches := [("app_final", "final_missed")]
    junctions :=
      [ { id := "J_FINAL"
          incoming := ["app_final"]
          outgoing := ["final_missed"] } ]
    guardPoints :=
      [ { fromEdge := "app_final"
          toEdge := "final_missed"
          atMark := { permille := 900 } } ]
    altitudeBands :=
      [ { id := "circuit"
          lowerFt := 1000
          upperFt := 1500 }
      , { id := "missed"
          lowerFt := 2000
          upperFt := 3000 } ] }

def TestAirState : AirState :=
  { reservations := [airReservationFor "AC1" "app_final"]
    junctionReservations := [junctionReservationFor "AC1" "J_FINAL"]
    aircraft :=
      [ ("AC1",
          { edge := "app_final"
            longitudinal := { permille := 850 }
            speedMinKt := 70
            speedMaxKt := 90
            altitude := .atBand "circuit"
            phaseTag := "final" }) ] }

def TestAirBranchProposal : AirProposal :=
  { aircraft := "AC1"
    state :=
      { edge := "app_final"
        longitudinal := { permille := 850 }
        speedMinKt := 70
        speedMaxKt := 90
        altitude := .atBand "circuit"
        phaseTag := "final" }
    act := .takeBranch "final_missed" }

def TestAirSpeedReductionProposal : AirProposal :=
  { aircraft := "AC1"
    state :=
      { edge := "app_final"
        longitudinal := { permille := 850 }
        speedMinKt := 70
        speedMaxKt := 90
        altitude := .atBand "circuit"
        phaseTag := "final" }
    act := .reduceSpeedMax 70 }

end CertifiedAtc
