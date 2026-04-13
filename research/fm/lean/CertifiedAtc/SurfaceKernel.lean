import CertifiedAtc.Core

namespace CertifiedAtc

open Classical

structure SurfaceSegment where
  id : SurfaceSegmentId
  fromNode : SurfaceNodeId
  toNode : SurfaceNodeId
  protectedBy : Option RunwayId := none
  deriving DecidableEq, Repr

structure SurfaceHoldPoint where
  id : String
  segment : SurfaceSegmentId
  runway : RunwayId
  deriving DecidableEq, Repr

structure SurfaceGraph where
  nodes : List SurfaceNodeId
  segments : List SurfaceSegment
  adjacency : List (SurfaceSegmentId × SurfaceSegmentId)
  holdPoints : List SurfaceHoldPoint
  deriving DecidableEq, Repr

structure SurfaceReservation where
  aircraft : EntityId
  segment : SurfaceSegmentId
  deriving DecidableEq, Repr

structure SurfaceAuthorization where
  aircraft : EntityId
  runway : RunwayId
  deriving DecidableEq, Repr

structure SurfacePosition where
  segment : SurfaceSegmentId
  longitudinal : LongitudinalMark
  deriving DecidableEq, Repr

inductive SurfaceMovement
  | holdAt (holdPoint : String)
  | moveToNext (next : SurfaceSegmentId)
  | reserveRoute (segments : List SurfaceSegmentId)
  | releaseSegment (segment : SurfaceSegmentId)
  deriving DecidableEq, Repr

structure SurfaceProposal where
  aircraft : EntityId
  position : SurfacePosition
  movement : SurfaceMovement
  deriving DecidableEq, Repr

structure SurfaceState where
  reservations : List SurfaceReservation
  authorizations : List SurfaceAuthorization := []
  positions : List (EntityId × SurfacePosition) := []
  deriving DecidableEq, Repr

structure SurfaceEffect where
  reservationsAdded : List SurfaceReservation := []
  reservationsRemoved : List SurfaceReservation := []
  positionsUpdated : List (EntityId × SurfacePosition) := []
  footprint : Footprint := {}
  deriving DecidableEq, Repr

structure SurfaceApproval where
  certificate : KernelCertificate
  effect : SurfaceEffect
  successor : SurfaceState
  deriving DecidableEq, Repr

inductive SurfaceRejectReason
  | unknownSegment (segment : SurfaceSegmentId)
  | nonAdjacentMove (fromSeg : SurfaceSegmentId) (toSeg : SurfaceSegmentId)
  | conflictingReservation (segment : SurfaceSegmentId)
  | missingProtectedAuthorization (runway : RunwayId)
  | malformedProposal (detail : String)
  deriving DecidableEq, Repr

inductive SurfaceDecision
  | approved (value : SurfaceApproval)
  | rejected (reason : SurfaceRejectReason)
  deriving DecidableEq, Repr

abbrev surface_certify_sig : Type := SurfaceGraph → SurfaceState → SurfaceProposal → SurfaceDecision

def findSegment (graph : SurfaceGraph) :
    SurfaceSegmentId → Option SurfaceSegment
  | id =>
      let rec go : List SurfaceSegment → Option SurfaceSegment
        | [] => none
        | seg :: tail =>
            if seg.id = id then
              some seg
            else
              go tail
      go graph.segments

def findHoldPoint (graph : SurfaceGraph) :
    String → Option SurfaceHoldPoint
  | holdPointId =>
      let rec go : List SurfaceHoldPoint → Option SurfaceHoldPoint
        | [] => none
        | holdPoint :: tail =>
            if holdPoint.id = holdPointId then
              some holdPoint
            else
              go tail
      go graph.holdPoints

def protectedRunway (graph : SurfaceGraph)
    (segment : SurfaceSegmentId) : Option RunwayId :=
  match findSegment graph segment with
  | some seg => seg.protectedBy
  | none => none

def lookupPosition :
    List (EntityId × SurfacePosition) → EntityId → Option SurfacePosition
  | [], _ => none
  | (aircraft, position) :: tail, target =>
      if aircraft = target then
        some position
      else
        lookupPosition tail target

def reservationFor
    (aircraft : EntityId) (segment : SurfaceSegmentId) : SurfaceReservation :=
  { aircraft := aircraft, segment := segment }

def mkReservations
    (aircraft : EntityId) : List SurfaceSegmentId → List SurfaceReservation
  | [] => []
  | segment :: tail => reservationFor aircraft segment :: mkReservations aircraft tail

def removeReservation
    (target : SurfaceReservation) : List SurfaceReservation → List SurfaceReservation
  | [] => []
  | reservation :: tail =>
      if reservation = target then
        removeReservation target tail
      else
        reservation :: removeReservation target tail

def removePositions
    (aircraft : EntityId) : List (EntityId × SurfacePosition) → List (EntityId × SurfacePosition)
  | [] => []
  | (entryAircraft, position) :: tail =>
      if entryAircraft = aircraft then
        removePositions aircraft tail
      else
        (entryAircraft, position) :: removePositions aircraft tail

def replacePosition
    (aircraft : EntityId) (position : SurfacePosition)
    (positions : List (EntityId × SurfacePosition)) :
    List (EntityId × SurfacePosition) :=
  (aircraft, position) :: removePositions aircraft positions

def moveSuccessorPosition (segment : SurfaceSegmentId) : SurfacePosition :=
  { segment := segment
    longitudinal := { permille := 0 } }

def referencedSegments (proposal : SurfaceProposal) : List SurfaceSegmentId :=
  let current := proposal.position.segment
  match proposal.movement with
  | .holdAt _ => [current]
  | .moveToNext next => [current, next]
  | .reserveRoute segments => current :: segments
  | .releaseSegment segment => [current, segment]

def AllSegmentsKnown (graph : SurfaceGraph) :
    List SurfaceSegmentId → Prop
  | [] => True
  | segment :: tail =>
      findSegment graph segment ≠ none ∧
        AllSegmentsKnown graph tail

theorem allSegmentsKnown_head
    {graph : SurfaceGraph} {segment : SurfaceSegmentId}
    {tail : List SurfaceSegmentId} :
    AllSegmentsKnown graph (segment :: tail) →
      findSegment graph segment ≠ none := by
  intro hKnown
  exact hKnown.1

theorem allSegmentsKnown_tail
    {graph : SurfaceGraph} {segment : SurfaceSegmentId}
    {tail : List SurfaceSegmentId} :
    AllSegmentsKnown graph (segment :: tail) →
      AllSegmentsKnown graph tail := by
  simp [AllSegmentsKnown]

def RouteChain (graph : SurfaceGraph)
    (start : SurfaceSegmentId) :
    List SurfaceSegmentId → Prop
  | [] => True
  | segment :: tail =>
      (start, segment) ∈ graph.adjacency ∧
        RouteChain graph segment tail

def ProtectedEntryAuthorized
    (graph : SurfaceGraph) (state : SurfaceState)
    (aircraft : EntityId) :
    List SurfaceSegmentId → Prop
  | [] => True
  | segment :: tail =>
      (match protectedRunway graph segment with
      | some runway =>
          { aircraft := aircraft, runway := runway } ∈ state.authorizations
      | none => True) ∧
        ProtectedEntryAuthorized graph state aircraft tail

def SegmentAvailable
    (state : SurfaceState) (aircraft : EntityId)
    (segment : SurfaceSegmentId) : Prop :=
  ∀ reservation ∈ state.reservations,
    reservation.segment = segment →
      reservation.aircraft = aircraft

def ReservationsAvailable
    (state : SurfaceState) (aircraft : EntityId) :
    List SurfaceSegmentId → Prop
  | [] => True
  | segment :: tail =>
      SegmentAvailable state aircraft segment ∧
        ReservationsAvailable state aircraft tail

def HoldPointMatches
    (graph : SurfaceGraph)
    (current : SurfaceSegmentId) (holdPointId : String) : Prop :=
  match findHoldPoint graph holdPointId with
  | some holdPoint => holdPoint.segment = current
  | none => False

def hasProtectedSuccessor
    (graph : SurfaceGraph) (segment : SurfaceSegmentId)
    (runway : RunwayId) :
    List (SurfaceSegmentId × SurfaceSegmentId) → Prop
  | [] => False
  | edge :: tail =>
      (edge.1 = segment ∧ protectedRunway graph edge.2 = some runway) ∨
        hasProtectedSuccessor graph segment runway tail

def HoldPointProtectsRunway
    (graph : SurfaceGraph) (holdPoint : SurfaceHoldPoint) : Prop :=
  findSegment graph holdPoint.segment ≠ none ∧
    hasProtectedSuccessor graph holdPoint.segment holdPoint.runway graph.adjacency

def SegmentsUseKnownNodes
    (graph : SurfaceGraph) : List SurfaceSegment → Prop
  | [] => True
  | segment :: tail =>
      segment.fromNode ∈ graph.nodes ∧
        segment.toNode ∈ graph.nodes ∧
        SegmentsUseKnownNodes graph tail

def AdjacencyConsistent
    (graph : SurfaceGraph) :
    List (SurfaceSegmentId × SurfaceSegmentId) → Prop
  | [] => True
  | edge :: tail =>
      match findSegment graph edge.1, findSegment graph edge.2 with
      | some left, some right =>
          left.toNode = right.fromNode ∧
            AdjacencyConsistent graph tail
      | _, _ => False

def HoldPointsProtected
    (graph : SurfaceGraph) : List SurfaceHoldPoint → Prop
  | [] => True
  | holdPoint :: tail =>
      HoldPointProtectsRunway graph holdPoint ∧
        HoldPointsProtected graph tail

def SurfaceWellFormed (graph : SurfaceGraph) : Prop :=
  graph.nodes.Nodup ∧
    (graph.segments.map SurfaceSegment.id).Nodup ∧
    (graph.holdPoints.map SurfaceHoldPoint.id).Nodup ∧
    SegmentsUseKnownNodes graph graph.segments ∧
    AdjacencyConsistent graph graph.adjacency ∧
    HoldPointsProtected graph graph.holdPoints

def ReservationsKnown
    (graph : SurfaceGraph) : List SurfaceReservation → Prop
  | [] => True
  | reservation :: tail =>
      findSegment graph reservation.segment ≠ none ∧
        ReservationsKnown graph tail

def PositionsKnown
    (graph : SurfaceGraph) :
    List (EntityId × SurfacePosition) → Prop
  | [] => True
  | (_, position) :: tail =>
      findSegment graph position.segment ≠ none ∧
        PositionsKnown graph tail

def SurfaceInv (graph : SurfaceGraph) (state : SurfaceState) : Prop :=
  ReservationsKnown graph state.reservations ∧
    PositionsKnown graph state.positions

def moveFootprint
    (aircraft : EntityId)
    (segments : List SurfaceSegmentId) : Footprint :=
  { surfaceSegments := segments
    entities := [aircraft] }

def surfaceProposalEffect : SurfaceProposal → SurfaceEffect
  | { aircraft := aircraft, position := position, movement := .holdAt _ } =>
      { footprint := moveFootprint aircraft [position.segment] }
  | { aircraft := aircraft, position := position, movement := .moveToNext next } =>
      { reservationsAdded := [reservationFor aircraft next]
        reservationsRemoved := [reservationFor aircraft position.segment]
        positionsUpdated := [(aircraft, moveSuccessorPosition next)]
        footprint := moveFootprint aircraft [position.segment, next] }
  | { aircraft := aircraft, movement := .reserveRoute segments, .. } =>
      { reservationsAdded := mkReservations aircraft segments
        footprint := moveFootprint aircraft segments }
  | { aircraft := aircraft, movement := .releaseSegment segment, .. } =>
      { reservationsRemoved := [reservationFor aircraft segment]
        footprint := moveFootprint aircraft [segment] }

def applySurfaceProposal : SurfaceState → SurfaceProposal → SurfaceState
  | state, { aircraft := _, movement := .holdAt _, .. } => state
  | state, { aircraft := aircraft, position := position, movement := .moveToNext next } =>
      { reservations :=
          reservationFor aircraft next ::
            removeReservation (reservationFor aircraft position.segment) state.reservations
        authorizations := state.authorizations
        positions :=
          replacePosition aircraft (moveSuccessorPosition next) state.positions }
  | state, { aircraft := aircraft, movement := .reserveRoute segments, .. } =>
      { reservations := mkReservations aircraft segments ++ state.reservations
        authorizations := state.authorizations
        positions := state.positions }
  | state, { aircraft := aircraft, movement := .releaseSegment segment, .. } =>
      { reservations := removeReservation (reservationFor aircraft segment) state.reservations
        authorizations := state.authorizations
        positions := state.positions }

def mkSurfaceCertificate (state : SurfaceState)
    (proposal : SurfaceProposal) : KernelCertificate :=
  { id := s!"surface:{state.reservations.length}:{proposal.aircraft}:{proposal.position.segment}"
    kernel := .surface
    subject := proposal.aircraft
    issuedAtTick := state.reservations.length + state.positions.length
    assumptions := ["graph-local", "protected-entry-explicit"] }

def mkSurfaceApproval (state : SurfaceState)
    (proposal : SurfaceProposal) : SurfaceApproval :=
  { certificate := mkSurfaceCertificate state proposal
    effect := surfaceProposalEffect proposal
    successor := applySurfaceProposal state proposal }

def SurfaceLocalOk
    (graph : SurfaceGraph) (state : SurfaceState)
    (proposal : SurfaceProposal) : Prop :=
  lookupPosition state.positions proposal.aircraft = some proposal.position ∧
    AllSegmentsKnown graph (referencedSegments proposal) ∧
    match proposal.movement with
    | .holdAt holdPoint =>
        HoldPointMatches graph proposal.position.segment holdPoint
    | .moveToNext next =>
        RouteChain graph proposal.position.segment [next] ∧
          ProtectedEntryAuthorized graph state proposal.aircraft [next] ∧
          ReservationsAvailable state proposal.aircraft [next]
    | .reserveRoute segments =>
        segments ≠ [] ∧
          RouteChain graph proposal.position.segment segments ∧
          ProtectedEntryAuthorized graph state proposal.aircraft segments ∧
          ReservationsAvailable state proposal.aircraft segments
    | .releaseSegment segment =>
        reservationFor proposal.aircraft segment ∈ state.reservations ∧
          segment ≠ proposal.position.segment

def SurfaceCertificateSound
    (graph : SurfaceGraph) (state : SurfaceState)
    (proposal : SurfaceProposal) (approval : SurfaceApproval) : Prop :=
  approval.certificate.kernel = .surface ∧
    approval.certificate.subject = proposal.aircraft ∧
    approval.certificate.issuedAtTick =
      state.reservations.length + state.positions.length ∧
    SurfaceLocalOk graph state proposal ∧
    approval.effect = surfaceProposalEffect proposal ∧
    approval.successor = applySurfaceProposal state proposal

def firstUnknownSegment (graph : SurfaceGraph) :
    List SurfaceSegmentId → SurfaceSegmentId
  | [] => ""
  | segment :: tail =>
      if findSegment graph segment = none then
        segment
      else
        firstUnknownSegment graph tail

def firstIllegalTransition
    (graph : SurfaceGraph) (start : SurfaceSegmentId) :
    List SurfaceSegmentId → SurfaceSegmentId × SurfaceSegmentId
  | [] => (start, start)
  | segment :: tail =>
      if (start, segment) ∈ graph.adjacency then
        firstIllegalTransition graph segment tail
      else
        (start, segment)

def firstUnauthorizedRunway
    (graph : SurfaceGraph) (state : SurfaceState)
    (aircraft : EntityId) :
    List SurfaceSegmentId → RunwayId
  | [] => ""
  | segment :: tail =>
      match protectedRunway graph segment with
      | some runway =>
          if { aircraft := aircraft, runway := runway } ∈ state.authorizations then
            firstUnauthorizedRunway graph state aircraft tail
          else
            runway
      | none =>
          firstUnauthorizedRunway graph state aircraft tail

noncomputable def firstConflictingSegment
    (state : SurfaceState) (aircraft : EntityId) :
    List SurfaceSegmentId → SurfaceSegmentId
  | [] => ""
  | segment :: tail =>
      if SegmentAvailable state aircraft segment then
        firstConflictingSegment state aircraft tail
      else
        segment

noncomputable def surface_certify : surface_certify_sig := fun graph state proposal =>
  if _hCurrent : lookupPosition state.positions proposal.aircraft = some proposal.position then
    if _hKnown : AllSegmentsKnown graph (referencedSegments proposal) then
      match proposal.movement with
      | .holdAt holdPoint =>
          if _hHold : HoldPointMatches graph proposal.position.segment holdPoint then
            .approved (mkSurfaceApproval state proposal)
          else
            .rejected
              (.malformedProposal
                s!"aircraft {proposal.aircraft} is not at hold point {holdPoint}")
      | .moveToNext next =>
          if _hRoute : RouteChain graph proposal.position.segment [next] then
            if _hAuth : ProtectedEntryAuthorized graph state proposal.aircraft [next] then
              if _hAvailable : ReservationsAvailable state proposal.aircraft [next] then
                .approved (mkSurfaceApproval state proposal)
              else
                .rejected
                  (.conflictingReservation
                    (firstConflictingSegment state proposal.aircraft [next]))
            else
              .rejected
                (.missingProtectedAuthorization
                  (firstUnauthorizedRunway graph state proposal.aircraft [next]))
          else
            let bad := firstIllegalTransition graph proposal.position.segment [next]
            .rejected (.nonAdjacentMove bad.1 bad.2)
      | .reserveRoute segments =>
          match segments with
          | [] =>
              .rejected (.malformedProposal "cannot reserve empty surface route")
          | _ :: _ =>
              if _hRoute : RouteChain graph proposal.position.segment segments then
                if _hAuth : ProtectedEntryAuthorized graph state proposal.aircraft segments then
                  if _hAvailable : ReservationsAvailable state proposal.aircraft segments then
                    .approved (mkSurfaceApproval state proposal)
                  else
                    .rejected
                      (.conflictingReservation
                        (firstConflictingSegment state proposal.aircraft segments))
                else
                  .rejected
                    (.missingProtectedAuthorization
                      (firstUnauthorizedRunway graph state proposal.aircraft segments))
              else
                let bad := firstIllegalTransition graph proposal.position.segment segments
                .rejected (.nonAdjacentMove bad.1 bad.2)
      | .releaseSegment segment =>
          if _hHeld : reservationFor proposal.aircraft segment ∈ state.reservations then
            if _hNotCurrent : segment ≠ proposal.position.segment then
              .approved (mkSurfaceApproval state proposal)
            else
              .rejected
                (.malformedProposal
                  s!"cannot release occupied surface segment {segment}")
          else
            .rejected
              (.malformedProposal
                s!"aircraft {proposal.aircraft} does not hold reservation on {segment}")
    else
      .rejected
        (.unknownSegment
          (firstUnknownSegment graph (referencedSegments proposal)))
  else
    .rejected
      (.malformedProposal
        s!"stale or missing surface position for {proposal.aircraft}")

theorem mem_of_mem_removeReservation
    {target member : SurfaceReservation} :
    ∀ {reservations : List SurfaceReservation},
      member ∈ removeReservation target reservations →
        member ∈ reservations := by
  intro reservations hMem
  induction reservations with
  | nil =>
      cases hMem
  | cons reservation tail ih =>
      by_cases hEq : reservation = target
      · simp [removeReservation, hEq] at hMem
        simpa using Or.inr (ih hMem)
      · simp [removeReservation, hEq] at hMem
        cases hMem with
        | inl hHead =>
            simp [hHead]
        | inr hTail =>
            simpa using Or.inr (ih hTail)

theorem mem_removeReservation_of_ne
    {target member : SurfaceReservation} :
    ∀ {reservations : List SurfaceReservation},
      member ∈ reservations →
        member ≠ target →
          member ∈ removeReservation target reservations := by
  intro reservations
  induction reservations with
  | nil =>
      intro hMem
      cases hMem
  | cons reservation tail ih =>
      intro hMem hNe
      by_cases hEq : reservation = target
      · have hMemTail : member ∈ tail := by
          simp [hEq] at hMem
          cases hMem with
          | inl hMemberEq =>
              exact False.elim (hNe hMemberEq)
          | inr hTail =>
              exact hTail
        simp [removeReservation, hEq]
        exact ih hMemTail hNe
      · simp [removeReservation, hEq]
        have hMem' : member = reservation ∨ member ∈ tail := by
          simpa using hMem
        cases hMem' with
        | inl hHead =>
            subst member
            simp
        | inr hTail =>
            exact Or.inr (ih hTail hNe)

theorem ReservationsKnown_remove
    {graph : SurfaceGraph} {target : SurfaceReservation} :
    ∀ {reservations : List SurfaceReservation},
      ReservationsKnown graph reservations →
        ReservationsKnown graph (removeReservation target reservations) := by
  intro reservations hKnown
  induction reservations with
  | nil =>
      simp [removeReservation, ReservationsKnown]
  | cons reservation tail ih =>
      by_cases hEq : reservation = target
      · simp [removeReservation, hEq, ReservationsKnown] at hKnown ⊢
        exact ih hKnown.2
      · simp [removeReservation, hEq, ReservationsKnown] at hKnown ⊢
        exact ⟨hKnown.1, ih hKnown.2⟩

theorem reservationsKnown_mkReservations
    {graph : SurfaceGraph} {aircraft : EntityId} :
    ∀ {segments : List SurfaceSegmentId},
      AllSegmentsKnown graph segments →
        ReservationsKnown graph (mkReservations aircraft segments) := by
  intro segments hKnown
  induction segments with
  | nil =>
      simp [mkReservations, ReservationsKnown]
  | cons segment tail ih =>
      simp [AllSegmentsKnown] at hKnown
      exact ⟨hKnown.1, ih hKnown.2⟩

theorem ReservationsKnown_append
    {graph : SurfaceGraph} :
    ∀ {left right : List SurfaceReservation},
      ReservationsKnown graph left →
        ReservationsKnown graph right →
          ReservationsKnown graph (left ++ right) := by
  intro left right hLeft hRight
  induction left generalizing right with
  | nil =>
      simpa [ReservationsKnown] using hRight
  | cons reservation tail ih =>
      simp [ReservationsKnown] at hLeft ⊢
      exact ⟨hLeft.1, ih hLeft.2 hRight⟩

theorem PositionsKnown_removePositions
    {graph : SurfaceGraph} {aircraft : EntityId} :
    ∀ {positions : List (EntityId × SurfacePosition)},
      PositionsKnown graph positions →
        PositionsKnown graph (removePositions aircraft positions) := by
  intro positions hKnown
  induction positions with
  | nil =>
      simp [removePositions, PositionsKnown]
  | cons entry tail ih =>
      by_cases hEq : entry.1 = aircraft
      · simp [removePositions, hEq, PositionsKnown] at hKnown ⊢
        exact ih hKnown.2
      · simp [removePositions, hEq, PositionsKnown] at hKnown ⊢
        exact ⟨hKnown.1, ih hKnown.2⟩

theorem PositionsKnown_replacePosition
    {graph : SurfaceGraph} {aircraft : EntityId}
    {position : SurfacePosition}
    {positions : List (EntityId × SurfacePosition)}
    (hSegmentKnown : findSegment graph position.segment ≠ none)
    (hKnown : PositionsKnown graph positions) :
    PositionsKnown graph (replacePosition aircraft position positions) := by
  simp [replacePosition, PositionsKnown, hSegmentKnown]
  exact PositionsKnown_removePositions hKnown

theorem mkSurfaceApproval_sound
    {graph : SurfaceGraph} {state : SurfaceState}
    {proposal : SurfaceProposal}
    (hLocal : SurfaceLocalOk graph state proposal) :
    SurfaceCertificateSound graph state proposal (mkSurfaceApproval state proposal) := by
  simp [SurfaceCertificateSound, mkSurfaceApproval, mkSurfaceCertificate, hLocal]

theorem hold_preserves_inv
    {graph : SurfaceGraph} {state : SurfaceState}
    {aircraft : EntityId} {position : SurfacePosition}
    {holdPoint : String}
    (hInv : SurfaceInv graph state) :
    SurfaceInv graph
      (applySurfaceProposal state
        { aircraft := aircraft
          position := position
          movement := .holdAt holdPoint }) := by
  simpa [applySurfaceProposal] using hInv

theorem move_preserves_inv
    {graph : SurfaceGraph} {state : SurfaceState}
    {aircraft : EntityId} {position : SurfacePosition}
    {next : SurfaceSegmentId}
    (hInv : SurfaceInv graph state)
    (hNextKnown : findSegment graph next ≠ none) :
    SurfaceInv graph
      (applySurfaceProposal state
        { aircraft := aircraft
          position := position
          movement := .moveToNext next }) := by
  rcases hInv with ⟨hReservationsKnown, hPositionsKnown⟩
  constructor
  · exact ⟨hNextKnown, ReservationsKnown_remove hReservationsKnown⟩
  · exact PositionsKnown_replacePosition hNextKnown hPositionsKnown

theorem reserve_preserves_inv
    {graph : SurfaceGraph} {state : SurfaceState}
    {aircraft : EntityId} {position : SurfacePosition}
    {segments : List SurfaceSegmentId}
    (hInv : SurfaceInv graph state)
    (hKnown : AllSegmentsKnown graph segments) :
    SurfaceInv graph
      (applySurfaceProposal state
        { aircraft := aircraft
          position := position
          movement := .reserveRoute segments }) := by
  rcases hInv with ⟨hReservationsKnown, hPositionsKnown⟩
  constructor
  · exact ReservationsKnown_append
      (reservationsKnown_mkReservations hKnown)
      hReservationsKnown
  · simpa [applySurfaceProposal] using hPositionsKnown

theorem releaseSegment_preserves_inv
    {graph : SurfaceGraph} {state : SurfaceState}
    {aircraft : EntityId} {position : SurfacePosition}
    {segment : SurfaceSegmentId}
    (hInv : SurfaceInv graph state) :
    SurfaceInv graph
      (applySurfaceProposal state
        { aircraft := aircraft
          position := position
          movement := .releaseSegment segment }) := by
  rcases hInv with ⟨hReservationsKnown, hPositionsKnown⟩
  constructor
  · simpa [applySurfaceProposal] using
      ReservationsKnown_remove
        (target := reservationFor aircraft segment)
        hReservationsKnown
  · simpa [applySurfaceProposal] using hPositionsKnown

/--
The surface kernel is now a concrete local checker over a directed surface graph,
surface reservations, and explicit protected-entry authorizations.
-/
theorem SurfaceKernelSoundnessTheorem :
  ∀ graph state proposal approval,
    SurfaceWellFormed graph →
    SurfaceInv graph state →
    surface_certify graph state proposal = .approved approval →
      SurfaceCertificateSound graph state proposal approval ∧
      SurfaceInv graph approval.successor := by
  intro graph state proposal approval _hWellFormed hInv hApproved
  cases proposal with
  | mk aircraft position movement =>
      cases movement with
      | holdAt holdPoint =>
          unfold surface_certify at hApproved
          by_cases hCurrent : lookupPosition state.positions aircraft = some position
          · by_cases hKnown : AllSegmentsKnown graph [position.segment]
            · by_cases hHold : HoldPointMatches graph position.segment holdPoint
              · simp [referencedSegments, hCurrent, hKnown, hHold] at hApproved
                cases hApproved
                constructor
                ·
                  apply mkSurfaceApproval_sound
                  simp [SurfaceLocalOk, referencedSegments, hCurrent, hKnown, hHold]
                · exact hold_preserves_inv (aircraft := aircraft) (position := position) (holdPoint := holdPoint) hInv
              · simp [referencedSegments, hCurrent, hKnown, hHold] at hApproved
            · simp [referencedSegments, hCurrent, hKnown] at hApproved
          · simp [hCurrent] at hApproved
      | moveToNext next =>
          unfold surface_certify at hApproved
          by_cases hCurrent : lookupPosition state.positions aircraft = some position
          · by_cases hKnown : AllSegmentsKnown graph [position.segment, next]
            · by_cases hRoute : RouteChain graph position.segment [next]
              · by_cases hAuth : ProtectedEntryAuthorized graph state aircraft [next]
                · by_cases hAvailable : ReservationsAvailable state aircraft [next]
                  · simp [referencedSegments, hCurrent, hKnown, hRoute, hAuth, hAvailable] at hApproved
                    cases hApproved
                    have hTailKnown : AllSegmentsKnown graph [next] := allSegmentsKnown_tail hKnown
                    have hNextKnown : findSegment graph next ≠ none := allSegmentsKnown_head hTailKnown
                    constructor
                    ·
                      apply mkSurfaceApproval_sound
                      simp [SurfaceLocalOk, referencedSegments, hCurrent, hKnown, hRoute, hAuth, hAvailable]
                    · exact move_preserves_inv hInv hNextKnown
                  · simp [referencedSegments, hCurrent, hKnown, hRoute, hAuth, hAvailable] at hApproved
                · simp [referencedSegments, hCurrent, hKnown, hRoute, hAuth] at hApproved
              · simp [referencedSegments, hCurrent, hKnown, hRoute] at hApproved
            · simp [referencedSegments, hCurrent, hKnown] at hApproved
          · simp [hCurrent] at hApproved
      | reserveRoute segments =>
          unfold surface_certify at hApproved
          by_cases hCurrent : lookupPosition state.positions aircraft = some position
          · by_cases hKnown : AllSegmentsKnown graph (position.segment :: segments)
            · cases segments with
              | nil =>
                  simp [referencedSegments, hCurrent, hKnown] at hApproved
              | cons first tail =>
                  by_cases hRoute : RouteChain graph position.segment (first :: tail)
                  · by_cases hAuth :
                      ProtectedEntryAuthorized graph state aircraft (first :: tail)
                    · by_cases hAvailable :
                        ReservationsAvailable state aircraft (first :: tail)
                      · simp [referencedSegments, hCurrent, hKnown, hRoute, hAuth, hAvailable] at hApproved
                        cases hApproved
                        have hRouteKnown :
                            AllSegmentsKnown graph (first :: tail) := allSegmentsKnown_tail hKnown
                        constructor
                        ·
                          apply mkSurfaceApproval_sound
                          simp [SurfaceLocalOk, referencedSegments, hCurrent, hKnown, hRoute, hAuth, hAvailable]
                        · exact reserve_preserves_inv (aircraft := aircraft) (position := position) hInv hRouteKnown
                      · simp [referencedSegments, hCurrent, hKnown, hRoute, hAuth, hAvailable] at hApproved
                    · simp [referencedSegments, hCurrent, hKnown, hRoute, hAuth] at hApproved
                  · simp [referencedSegments, hCurrent, hKnown, hRoute] at hApproved
            · simp [referencedSegments, hCurrent, hKnown] at hApproved
          · simp [hCurrent] at hApproved
      | releaseSegment segment =>
          unfold surface_certify at hApproved
          by_cases hCurrent : lookupPosition state.positions aircraft = some position
          · by_cases hKnown : AllSegmentsKnown graph [position.segment, segment]
            · by_cases hHeld : reservationFor aircraft segment ∈ state.reservations
              · by_cases hNotCurrent : segment ≠ position.segment
                · simp [referencedSegments, hCurrent, hKnown, hHeld, hNotCurrent] at hApproved
                  cases hApproved
                  constructor
                  ·
                    apply mkSurfaceApproval_sound
                    simp [SurfaceLocalOk, referencedSegments, hCurrent, hKnown, hHeld, hNotCurrent]
                  · exact
                      releaseSegment_preserves_inv
                        (aircraft := aircraft)
                        (position := position)
                        (segment := segment)
                        hInv
                · simp [referencedSegments, hCurrent, hKnown, hHeld, hNotCurrent] at hApproved
              · simp [referencedSegments, hCurrent, hKnown, hHeld] at hApproved
            · simp [referencedSegments, hCurrent, hKnown] at hApproved
          · simp [hCurrent] at hApproved

def TestAerodromeSurfaceGraph : SurfaceGraph :=
  { nodes := ["stand1", "apron_end", "a1_hold", "rwy09_threshold", "rwy27_end"]
    segments :=
      [ { id := "stand1_apron"
          fromNode := "stand1"
          toNode := "apron_end" }
        ,
        { id := "taxiway_a"
          fromNode := "apron_end"
          toNode := "a1_hold" }
        ,
        { id := "hold_a1"
          fromNode := "a1_hold"
          toNode := "rwy09_threshold"
          protectedBy := some "09" }
        ,
        { id := "exit_taxiway"
          fromNode := "rwy27_end"
          toNode := "apron_end" } ]
    adjacency :=
      [ ("stand1_apron", "taxiway_a"),
        ("taxiway_a", "hold_a1") ]
    holdPoints :=
      [ { id := "A1"
          segment := "taxiway_a"
          runway := "09" } ] }

def TestAerodromeSurfaceState : SurfaceState :=
  { reservations := [reservationFor "AC1" "taxiway_a"]
    authorizations := [{ aircraft := "AC1", runway := "09" }]
    positions :=
      [ ("AC1",
          { segment := "taxiway_a"
            longitudinal := { permille := 900 } }) ] }

def TestAerodromeProtectedEntryProposal : SurfaceProposal :=
  { aircraft := "AC1"
    position :=
      { segment := "taxiway_a"
        longitudinal := { permille := 900 } }
    movement := .moveToNext "hold_a1" }

theorem TestAerodromeFindStand1Apron :
    findSegment TestAerodromeSurfaceGraph "stand1_apron" =
      some
        { id := "stand1_apron"
          fromNode := "stand1"
          toNode := "apron_end" } := by
  decide

theorem TestAerodromeFindTaxiwayA :
    findSegment TestAerodromeSurfaceGraph "taxiway_a" =
      some
        { id := "taxiway_a"
          fromNode := "apron_end"
          toNode := "a1_hold" } := by
  decide

theorem TestAerodromeFindHoldA1 :
    findSegment TestAerodromeSurfaceGraph "hold_a1" =
      some
        { id := "hold_a1"
          fromNode := "a1_hold"
          toNode := "rwy09_threshold"
          protectedBy := some "09" } := by
  decide

theorem TestAerodromeTaxiwayAKnown :
    findSegment TestAerodromeSurfaceGraph "taxiway_a" ≠ none := by
  simp [TestAerodromeFindTaxiwayA]

theorem TestAerodromeHoldA1Known :
    findSegment TestAerodromeSurfaceGraph "hold_a1" ≠ none := by
  simp [TestAerodromeFindHoldA1]

theorem TestAerodromeProtectedRunwayHoldA1 :
    protectedRunway TestAerodromeSurfaceGraph "hold_a1" = some "09" := by
  simp [protectedRunway, TestAerodromeFindHoldA1]

theorem TestAerodromeSurfaceGraph_wellFormed :
    SurfaceWellFormed TestAerodromeSurfaceGraph := by
  unfold SurfaceWellFormed
  refine ⟨?_, ?_, ?_, ?_, ?_, ?_⟩
  · simp [TestAerodromeSurfaceGraph]
  · simp [TestAerodromeSurfaceGraph]
  · simp [TestAerodromeSurfaceGraph]
  · simp [SegmentsUseKnownNodes, TestAerodromeSurfaceGraph]
  · unfold AdjacencyConsistent
    simp [TestAerodromeSurfaceGraph]
    have hStand := TestAerodromeFindStand1Apron
    have hTaxi := TestAerodromeFindTaxiwayA
    have hHold := TestAerodromeFindHoldA1
    simp [TestAerodromeSurfaceGraph] at hStand hTaxi hHold
    rw [hStand, hTaxi]
    constructor
    · rfl
    · unfold AdjacencyConsistent
      rw [hTaxi, hHold]
      simp [AdjacencyConsistent]
  · unfold HoldPointsProtected HoldPointProtectsRunway hasProtectedSuccessor
    constructor
    · constructor
      · exact TestAerodromeTaxiwayAKnown
      · right
        left
        constructor
        · rfl
        · exact TestAerodromeProtectedRunwayHoldA1
    · simp [HoldPointsProtected]

theorem TestAerodromeSurfaceState_inv :
    SurfaceInv TestAerodromeSurfaceGraph TestAerodromeSurfaceState := by
  constructor
  · refine ⟨?_, ?_⟩
    · simpa [TestAerodromeSurfaceState, reservationFor] using
        TestAerodromeTaxiwayAKnown
    · simp [ReservationsKnown]
  · refine ⟨?_, ?_⟩
    · simpa [TestAerodromeSurfaceState] using TestAerodromeTaxiwayAKnown
    · simp [PositionsKnown]

theorem TestAerodromeProtectedEntryApproved :
    surface_certify
        TestAerodromeSurfaceGraph
        TestAerodromeSurfaceState
        TestAerodromeProtectedEntryProposal =
      .approved
        (mkSurfaceApproval
          TestAerodromeSurfaceState
          TestAerodromeProtectedEntryProposal) := by
  have hCurrent :
      lookupPosition
          TestAerodromeSurfaceState.positions
          TestAerodromeProtectedEntryProposal.aircraft =
        some TestAerodromeProtectedEntryProposal.position := by
    simp [TestAerodromeSurfaceState, TestAerodromeProtectedEntryProposal, lookupPosition]
  have hKnown :
      AllSegmentsKnown TestAerodromeSurfaceGraph
        (referencedSegments TestAerodromeProtectedEntryProposal) := by
    unfold AllSegmentsKnown referencedSegments
    constructor
    · simpa [TestAerodromeProtectedEntryProposal] using TestAerodromeTaxiwayAKnown
    · constructor
      · simpa [TestAerodromeProtectedEntryProposal] using TestAerodromeHoldA1Known
      · simp [AllSegmentsKnown]
  have hRoute :
      RouteChain TestAerodromeSurfaceGraph
        TestAerodromeProtectedEntryProposal.position.segment ["hold_a1"] := by
    simp [RouteChain, TestAerodromeProtectedEntryProposal, TestAerodromeSurfaceGraph]
  have hProtected :
      protectedRunway TestAerodromeSurfaceGraph "hold_a1" = some "09" := by
    exact TestAerodromeProtectedRunwayHoldA1
  have hAuth :
      ProtectedEntryAuthorized TestAerodromeSurfaceGraph TestAerodromeSurfaceState
        TestAerodromeProtectedEntryProposal.aircraft ["hold_a1"] := by
    simp [ProtectedEntryAuthorized, hProtected, TestAerodromeSurfaceState,
      TestAerodromeProtectedEntryProposal]
  have hAvailable :
      ReservationsAvailable
        TestAerodromeSurfaceState
        TestAerodromeProtectedEntryProposal.aircraft
        ["hold_a1"] := by
    constructor
    · intro reservation hReservation hSegment
      simp [TestAerodromeSurfaceState, reservationFor] at hReservation
      rcases hReservation with rfl
      simp at hSegment
    · simp [ReservationsAvailable]
  have hRouteConcrete :
      RouteChain TestAerodromeSurfaceGraph "taxiway_a" ["hold_a1"] := by
    simpa [TestAerodromeProtectedEntryProposal] using hRoute
  have hAuthConcrete :
      ProtectedEntryAuthorized
        TestAerodromeSurfaceGraph
        TestAerodromeSurfaceState
        "AC1"
        ["hold_a1"] := by
    simpa [TestAerodromeProtectedEntryProposal] using hAuth
  have hAvailableConcrete :
      ReservationsAvailable TestAerodromeSurfaceState "AC1" ["hold_a1"] := by
    simpa [TestAerodromeProtectedEntryProposal] using hAvailable
  unfold surface_certify
  rw [dif_pos hCurrent, dif_pos hKnown]
  simp [TestAerodromeProtectedEntryProposal]
  rw [if_pos hRouteConcrete, if_pos hAuthConcrete, if_pos hAvailableConcrete]

end CertifiedAtc
