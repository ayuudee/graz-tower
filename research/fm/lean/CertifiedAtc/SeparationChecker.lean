import CertifiedAtc.CommandCatalog
import CertifiedAtc.AirKernel

namespace CertifiedAtc

def H_sep : Nat := 120

structure SeparationEntityState where
  aircraft : EntityId
  trackId : String
  longitudinal : LongitudinalMark
  speedMinKt : Nat
  speedMaxKt : Nat
  lowerAltFt : Int
  upperAltFt : Int
  phaseTag : String := ""
  deriving DecidableEq, Repr

structure SeparationRule where
  id : SeparationRuleId
  minLongitudinalPermille : Nat
  minVerticalFt : Nat
  description : String := ""
  deriving DecidableEq, Repr

structure SeparationScenario where
  subjectBefore : SeparationEntityState
  subjectAfter : SeparationEntityState
  peer : SeparationEntityState
  rule : SeparationRule
  horizonSeconds : Nat
  deriving DecidableEq, Repr

structure SeparationWitness where
  checkedRule : SeparationRuleId
  horizonSeconds : Nat
  deriving DecidableEq, Repr

structure SeparationViolation where
  checkedRule : SeparationRuleId
  detail : String
  deriving DecidableEq, Repr

inductive SeparationDecision
  | safe (witness : SeparationWitness)
  | unsafeResult (violation : SeparationViolation)
  deriving DecidableEq, Repr

abbrev separation_check_sig : Type := SeparationScenario → SeparationDecision

def lookupAltitudeBand : List AltitudeBand → AltitudeBandId → Option AltitudeBand
  | [], _ => none
  | band :: tail, target =>
      if band.id = target then
        some band
      else
        lookupAltitudeBand tail target

def altitudeWindow (bands : List AltitudeBand) : AltitudeState → Int × Int
  | .atBand bandId =>
      match lookupAltitudeBand bands bandId with
      | some band => (band.lowerFt, band.upperFt)
      | none => (0, 0)
  | .transitioning _ _ lower upper => (lower, upper)

def separationTrackFor (graph : AirGraph) (edge : AirEdgeId) : String :=
  match findAirEdge graph edge with
  | some edgeInfo =>
      if edgeInfo.separationTrack = "" then
        s!"edge:{edge}"
      else
        edgeInfo.separationTrack
  | none => s!"edge:{edge}"

def toSeparationEntityState
    (graph : AirGraph) (aircraft : EntityId) (airState : AirborneState) :
    SeparationEntityState :=
  let (lowerAlt, upperAlt) := altitudeWindow graph.altitudeBands airState.altitude
  { aircraft := aircraft
    trackId := separationTrackFor graph airState.edge
    longitudinal := airState.longitudinal
    speedMinKt := airState.speedMinKt
    speedMaxKt := airState.speedMaxKt
    lowerAltFt := lowerAlt
    upperAltFt := upperAlt
    phaseTag := airState.phaseTag }

def selectSeparationPeers
    (graph : AirGraph) (state : AirState) (subject : EntityId) :
    List SeparationEntityState :=
  let rec go : List (EntityId × AirborneState) → List SeparationEntityState
    | [] => []
    | (entity, airState) :: tail =>
        if entity = subject then
          go tail
        else
          toSeparationEntityState graph entity airState :: go tail
  go state.aircraft

def SeparationRelevantCommand (command : Command) : Prop :=
  (commandPlan command).separation = true

def NonSeparationRelevantCommand (command : Command) : Prop :=
  (commandPlan command).separation = false

def ConcreteNeutralAirborneCommand : Command → Prop
  | .reportDownwind _ => True
  | .reportFinal _ => True
  | .proceed _ => True
  | .contactFrequency _ _ _ => True
  | .monitorFrequency _ _ _ => True
  | .squawkCode _ _ => True
  | _ => False

def SeparationEntityStateWellFormed (entity : SeparationEntityState) : Prop :=
  entity.speedMinKt ≤ entity.speedMaxKt ∧
    entity.lowerAltFt ≤ entity.upperAltFt

def SeparationScenarioWellFormed (scenario : SeparationScenario) : Prop :=
  SeparationEntityStateWellFormed scenario.subjectBefore ∧
    SeparationEntityStateWellFormed scenario.subjectAfter ∧
    SeparationEntityStateWellFormed scenario.peer ∧
    scenario.subjectBefore.aircraft = scenario.subjectAfter.aircraft ∧
    scenario.subjectBefore.trackId ≠ "" ∧
    scenario.subjectAfter.trackId ≠ "" ∧
    scenario.peer.trackId ≠ "" ∧
    scenario.subjectBefore.aircraft ≠ scenario.peer.aircraft

def longitudinalGapPermille
    (subject peer : SeparationEntityState) : Nat :=
  if subject.longitudinal.permille ≤ peer.longitudinal.permille then
    peer.longitudinal.permille - subject.longitudinal.permille
  else
    subject.longitudinal.permille - peer.longitudinal.permille

def verticalGapFt
    (subject peer : SeparationEntityState) : Nat :=
  if subject.upperAltFt < peer.lowerAltFt then
    Int.natAbs (peer.lowerAltFt - subject.upperAltFt)
  else if peer.upperAltFt < subject.lowerAltFt then
    Int.natAbs (subject.lowerAltFt - peer.upperAltFt)
  else
    0

def LongitudinalRuleSatisfied (scenario : SeparationScenario) : Prop :=
  scenario.subjectAfter.trackId = scenario.peer.trackId ∧
    scenario.rule.minLongitudinalPermille ≤
      longitudinalGapPermille scenario.subjectAfter scenario.peer

def VerticalRuleSatisfied (scenario : SeparationScenario) : Prop :=
  scenario.rule.minVerticalFt ≤ verticalGapFt scenario.subjectAfter scenario.peer

def PairwiseSeparated (scenario : SeparationScenario) : Prop :=
  VerticalRuleSatisfied scenario ∨ LongitudinalRuleSatisfied scenario

def SeparationNeutralTransition
    (subjectBefore subjectAfter peer : SeparationEntityState) : Prop :=
  subjectBefore.aircraft = subjectAfter.aircraft ∧
    subjectBefore.trackId = subjectAfter.trackId ∧
    subjectBefore.phaseTag = subjectAfter.phaseTag ∧
    longitudinalGapPermille subjectBefore peer ≤
      longitudinalGapPermille subjectAfter peer ∧
    verticalGapFt subjectBefore peer ≤
      verticalGapFt subjectAfter peer

instance (entity : SeparationEntityState) :
    Decidable (SeparationEntityStateWellFormed entity) := by
  unfold SeparationEntityStateWellFormed
  infer_instance

instance (scenario : SeparationScenario) :
    Decidable (SeparationScenarioWellFormed scenario) := by
  unfold SeparationScenarioWellFormed
  infer_instance

instance (scenario : SeparationScenario) :
    Decidable (LongitudinalRuleSatisfied scenario) := by
  unfold LongitudinalRuleSatisfied
  infer_instance

instance (scenario : SeparationScenario) :
    Decidable (VerticalRuleSatisfied scenario) := by
  unfold VerticalRuleSatisfied
  infer_instance

instance (scenario : SeparationScenario) :
    Decidable (PairwiseSeparated scenario) := by
  unfold PairwiseSeparated
  infer_instance

def SeparationWitnessSound
    (scenario : SeparationScenario) (witness : SeparationWitness) : Prop :=
  witness.checkedRule = scenario.rule.id ∧
    witness.horizonSeconds = scenario.horizonSeconds ∧
    PairwiseSeparated scenario

def mkSeparationWitness (scenario : SeparationScenario) : SeparationWitness :=
  { checkedRule := scenario.rule.id
    horizonSeconds := scenario.horizonSeconds }

def separation_check : separation_check_sig := fun scenario =>
  if _ : SeparationScenarioWellFormed scenario then
    if _ : VerticalRuleSatisfied scenario then
      .safe (mkSeparationWitness scenario)
    else if _ : LongitudinalRuleSatisfied scenario then
      .safe (mkSeparationWitness scenario)
    else
      .unsafeResult
        { checkedRule := scenario.rule.id
          detail := "pairwise minima violated" }
  else
    .unsafeResult
      { checkedRule := scenario.rule.id
        detail := "malformed separation scenario" }

inductive SeparationBoundaryCase
    (command : Command)
    (baselineSubject : SeparationEntityState)
    (scenario : SeparationScenario) : Prop
  | certifiedRelevant (witness : SeparationWitness) :
      SeparationRelevantCommand command →
      SeparationScenarioWellFormed scenario →
      separation_check scenario = .safe witness →
        SeparationBoundaryCase command baselineSubject scenario
  | neutralIrrelevant :
      NonSeparationRelevantCommand command →
      scenario.subjectBefore = baselineSubject →
      SeparationNeutralTransition baselineSubject scenario.subjectAfter scenario.peer →
        SeparationBoundaryCase command baselineSubject scenario

def separationBaselineScenario (scenario : SeparationScenario) : SeparationScenario :=
  { subjectBefore := scenario.subjectBefore
    subjectAfter := scenario.subjectBefore
    peer := scenario.peer
    rule := scenario.rule
    horizonSeconds := scenario.horizonSeconds }

inductive SeparationContinuationKind
  | continueCurrentPath
  | holdCurrentPath
  | reduceSpeed
  | reservedBranchChoice
  | recoveryPath
  deriving DecidableEq, Repr

structure SeparationContinuation where
  kind : SeparationContinuationKind
  scenario : SeparationScenario
  deriving DecidableEq, Repr

def Viable_sep (continuations : List SeparationContinuation) : Prop :=
  ∃ continuation ∈ continuations,
    ∃ witness,
      SeparationScenarioWellFormed continuation.scenario ∧
        separation_check continuation.scenario = .safe witness

def outgoingBranches (graph : AirGraph) (edge : AirEdgeId) : List AirEdgeId :=
  graph.branches.foldr
    (fun branch tail =>
      if branch.1 = edge then
        branch.2 :: tail
      else
        tail)
    []

def recoveryTargets (graph : AirGraph) (state : AirborneState) : List AirEdgeId :=
  state.edge :: outgoingBranches graph state.edge

def speedReductionTarget (state : AirborneState) : Option Nat :=
  if state.speedMinKt < state.speedMaxKt then
    some state.speedMinKt
  else
    none

def proposalSuccessorAirborneState
    (graph : AirGraph) (state : AirState) (proposal : AirProposal) : AirborneState :=
  match lookupAirborneState (applyAirProposal graph state proposal).aircraft proposal.aircraft with
  | some successorState => successorState
  | none => proposal.state

def mkAirProposalSeparationScenario
    (graph : AirGraph)
    (state : AirState)
    (proposal : AirProposal)
    (peer : SeparationEntityState)
    (rule : SeparationRule) :
    SeparationScenario :=
  { subjectBefore :=
      toSeparationEntityState graph proposal.aircraft proposal.state
    subjectAfter :=
      toSeparationEntityState
        graph
        proposal.aircraft
        (proposalSuccessorAirborneState graph state proposal)
    peer := peer
    rule := rule
    horizonSeconds := H_sep }

noncomputable def approvedContinuationForProposal
    (graph : AirGraph)
    (state : AirState)
    (proposal : AirProposal)
    (peer : SeparationEntityState)
    (rule : SeparationRule)
    (kind : SeparationContinuationKind) :
    Option SeparationContinuation :=
  match air_certify graph state proposal with
  | .approved _ =>
      some
        { kind := kind
          scenario := mkAirProposalSeparationScenario graph state proposal peer rule }
  | .rejected _ => none

noncomputable def continueCurrentPathContinuation
    (graph : AirGraph)
    (state : AirState)
    (aircraft : EntityId)
    (peer : SeparationEntityState)
    (rule : SeparationRule) :
    Option SeparationContinuation :=
  match lookupAirborneState state.aircraft aircraft with
  | none => none
  | some airState =>
      approvedContinuationForProposal
        graph
        state
        { aircraft := aircraft
          state := airState
          act := .continueOnEdge }
        peer
        rule
        .continueCurrentPath

noncomputable def holdCurrentPathContinuation
    (graph : AirGraph)
    (state : AirState)
    (aircraft : EntityId)
    (peer : SeparationEntityState)
    (rule : SeparationRule) :
    Option SeparationContinuation :=
  match lookupAirborneState state.aircraft aircraft with
  | none => none
  | some airState =>
      approvedContinuationForProposal
        graph
        state
        { aircraft := aircraft
          state := airState
          act := .continueOnEdge }
        peer
        rule
        .holdCurrentPath

noncomputable def reservedBranchChoiceContinuations
    (graph : AirGraph)
    (state : AirState)
    (aircraft : EntityId)
    (peer : SeparationEntityState)
    (rule : SeparationRule) :
    List SeparationContinuation :=
  match lookupAirborneState state.aircraft aircraft with
  | none => []
  | some airState =>
      (outgoingBranches graph airState.edge).filterMap fun next =>
        approvedContinuationForProposal
          graph
          state
          { aircraft := aircraft
            state := airState
            act := .takeBranch next }
          peer
          rule
          .reservedBranchChoice

noncomputable def reduceSpeedContinuations
    (graph : AirGraph)
    (state : AirState)
    (aircraft : EntityId)
    (peer : SeparationEntityState)
    (rule : SeparationRule) :
    List SeparationContinuation :=
  match lookupAirborneState state.aircraft aircraft with
  | none => []
  | some airState =>
      match speedReductionTarget airState with
      | none => []
      | some targetMaxKt =>
          match approvedContinuationForProposal
              graph
              state
              { aircraft := aircraft
                state := airState
                act := .reduceSpeedMax targetMaxKt }
              peer
              rule
              .reduceSpeed with
          | some continuation => [continuation]
          | none => []

noncomputable def recoveryPathContinuations
    (graph : AirGraph)
    (state : AirState)
    (aircraft : EntityId)
    (peer : SeparationEntityState)
    (rule : SeparationRule) :
    List SeparationContinuation :=
  match lookupAirborneState state.aircraft aircraft with
  | none => []
  | some airState =>
      (recoveryTargets graph airState).filterMap fun next =>
        approvedContinuationForProposal
          graph
          state
          { aircraft := aircraft
            state := airState
            act := .activateMissedApproach next }
          peer
          rule
          .recoveryPath

noncomputable def approvedPairwiseContinuations
    (graph : AirGraph)
    (state : AirState)
    (aircraft : EntityId)
    (peer : SeparationEntityState)
    (rule : SeparationRule) :
    List SeparationContinuation :=
  (match continueCurrentPathContinuation graph state aircraft peer rule with
    | some continuation => [continuation]
    | none => []) ++
    (match holdCurrentPathContinuation graph state aircraft peer rule with
    | some continuation => [continuation]
    | none => []) ++
    reduceSpeedContinuations graph state aircraft peer rule ++
    reservedBranchChoiceContinuations graph state aircraft peer rule ++
    recoveryPathContinuations graph state aircraft peer rule

def SeparationEntityOperationalEq
    (left right : SeparationEntityState) : Prop :=
  left.aircraft = right.aircraft ∧
    left.trackId = right.trackId ∧
    left.longitudinal = right.longitudinal ∧
    left.speedMinKt = right.speedMinKt ∧
    left.speedMaxKt = right.speedMaxKt ∧
    left.lowerAltFt = right.lowerAltFt ∧
    left.upperAltFt = right.upperAltFt

theorem separationEntityOperationalEq_refl
    {entity : SeparationEntityState} :
    SeparationEntityOperationalEq entity entity := by
  simp [SeparationEntityOperationalEq]

theorem separationEntityOperationalEq_phaseTag_update_right
    {left baseline : SeparationEntityState}
    {phaseTag : String}
    (hEq : SeparationEntityOperationalEq left baseline) :
    SeparationEntityOperationalEq left { baseline with phaseTag := phaseTag } := by
  simpa [SeparationEntityOperationalEq] using hEq

theorem operationalEq_activatePathSuccessor_sameEdge
    {graph : AirGraph}
    {aircraft : EntityId}
    {airState : AirborneState} :
    SeparationEntityOperationalEq
      (toSeparationEntityState graph aircraft (activatePathSuccessorState airState airState.edge))
      (toSeparationEntityState graph aircraft airState) := by
  simp [SeparationEntityOperationalEq, toSeparationEntityState,
    activatePathSuccessorState, altitudeWindow]

theorem operationalEq_activateMissedApproachSuccessor_sameEdge
    {graph : AirGraph}
    {aircraft : EntityId}
    {airState : AirborneState} :
    SeparationEntityOperationalEq
      (toSeparationEntityState graph aircraft
        (activateMissedApproachSuccessorState airState airState.edge))
      (toSeparationEntityState graph aircraft airState) := by
  simp [SeparationEntityOperationalEq, toSeparationEntityState,
    activateMissedApproachSuccessorState, altitudeWindow]

theorem operationalEq_speedReducedState
    {graph : AirGraph}
    {aircraft : EntityId}
    {airState : AirborneState}
    {targetMaxKt : Nat} :
    SeparationEntityOperationalEq
      (toSeparationEntityState graph aircraft (speedReducedState airState targetMaxKt))
      { toSeparationEntityState graph aircraft airState with
          speedMaxKt := targetMaxKt } := by
  simp [SeparationEntityOperationalEq, toSeparationEntityState,
    speedReducedState, altitudeWindow]

theorem selfScenarioWellFormed_of_operationalEq_subjectAfter
    {subjectBefore subjectAfter peer : SeparationEntityState}
    {rule : SeparationRule}
    {horizonSeconds : Nat}
    {subject : SeparationEntityState}
    (hEq : SeparationEntityOperationalEq subject subjectAfter)
    (hWell :
      SeparationScenarioWellFormed
        { subjectBefore := subjectBefore
          subjectAfter := subjectAfter
          peer := peer
          rule := rule
          horizonSeconds := horizonSeconds }) :
    SeparationScenarioWellFormed
      { subjectBefore := subject
        subjectAfter := subject
        peer := peer
        rule := rule
        horizonSeconds := horizonSeconds } := by
  rcases hEq with
    ⟨hAircraft, hTrack, _hLongitudinal, hSpeedMin, hSpeedMax, hLower, hUpper⟩
  rcases hWell with
    ⟨_hBeforeWf, hAfterWf, hPeerWf, hAircraftEq, _hBeforeTrack, hAfterTrack, hPeerTrack, hAircraftNe⟩
  have hAfterAircraftNe : subjectAfter.aircraft ≠ peer.aircraft := by
    intro hEqPeer
    apply hAircraftNe
    exact hAircraftEq.trans hEqPeer
  constructor
  · simpa [SeparationEntityStateWellFormed, hSpeedMin, hSpeedMax, hLower, hUpper] using hAfterWf
  · constructor
    · simpa [SeparationEntityStateWellFormed, hSpeedMin, hSpeedMax, hLower, hUpper] using hAfterWf
    · constructor
      · exact hPeerWf
      · constructor
        · rfl
        · constructor
          · simpa [hTrack] using hAfterTrack
          · constructor
            · simpa [hTrack] using hAfterTrack
            · constructor
              · simpa using hPeerTrack
              · simpa [hAircraft] using hAfterAircraftNe

theorem selfScenarioPairwise_of_operationalEq_subjectAfter
    {subjectBefore subjectAfter peer : SeparationEntityState}
    {rule : SeparationRule}
    {horizonSeconds : Nat}
    {subject : SeparationEntityState}
    (hEq : SeparationEntityOperationalEq subject subjectAfter)
    (hPair :
      PairwiseSeparated
        { subjectBefore := subjectBefore
          subjectAfter := subjectAfter
          peer := peer
          rule := rule
          horizonSeconds := horizonSeconds }) :
    PairwiseSeparated
      { subjectBefore := subject
        subjectAfter := subject
        peer := peer
        rule := rule
        horizonSeconds := horizonSeconds } := by
  rcases hEq with
    ⟨_hAircraft, hTrack, hLongitudinal, _hSpeedMin, _hSpeedMax, hLower, hUpper⟩
  cases hPair with
  | inl hVertical =>
      left
      unfold VerticalRuleSatisfied at hVertical ⊢
      simpa [verticalGapFt, hLower, hUpper] using hVertical
  | inr hLongitudinalSatisfied =>
      right
      rcases hLongitudinalSatisfied with ⟨hSameTrack, hGap⟩
      constructor
      · exact hTrack.trans hSameTrack
      · simpa [longitudinalGapPermille, hLongitudinal] using hGap

theorem continueCurrentPathContinuation_of_available_state
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {airState : AirborneState}
    {peer : SeparationEntityState}
    {rule : SeparationRule} :
    AirInv graph state →
    lookupAirborneState state.aircraft aircraft = some airState →
    EdgeAvailable state aircraft airState.edge →
      continueCurrentPathContinuation graph state aircraft peer rule =
        some
          { kind := .continueCurrentPath
            scenario :=
              mkAirProposalSeparationScenario
                graph
                state
                { aircraft := aircraft
                  state := airState
                  act := .continueOnEdge }
                peer
                rule } := by
  intro hInv hLookup hAvailable
  rcases hInv with ⟨_hReservationsKnown, _hJunctionKnown, hAircraftKnown⟩
  have hCurrentKnown :=
    lookupAirborneState_known
      (graph := graph)
      (aircraft := aircraft)
      (targetState := airState)
      hAircraftKnown
      hLookup
  have hKnown : AllAirEdgesKnown graph [airState.edge] := by
    simp [AllAirEdgesKnown, hCurrentKnown.1]
  unfold continueCurrentPathContinuation
  simp [hLookup, approvedContinuationForProposal, air_certify, referencedAirEdges, hKnown, hAvailable]

inductive ContinueCurrentPathCapableAct : AirAct → Prop
  | continueOnEdge :
      ContinueCurrentPathCapableAct .continueOnEdge
  | reduceSpeedMax {targetMaxKt : Nat} :
      ContinueCurrentPathCapableAct (.reduceSpeedMax targetMaxKt)
  | activatePath {firstEdge : AirEdgeId} :
      ContinueCurrentPathCapableAct (.activatePath firstEdge)
  | activateMissedApproach {firstEdge : AirEdgeId} :
      ContinueCurrentPathCapableAct (.activateMissedApproach firstEdge)

theorem continueCurrentPathContinuation_of_capableApproval
    {graph : AirGraph}
    {state : AirState}
    {proposal : AirProposal}
    {approval : AirApproval}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    (hWf : AirWellFormed graph)
    (hInv : AirInv graph state)
    (hApproved : air_certify graph state proposal = .approved approval)
    (hCapable : ContinueCurrentPathCapableAct proposal.act) :
    ∃ successorAirState,
      lookupAirborneState approval.successor.aircraft proposal.aircraft = some successorAirState ∧
      continueCurrentPathContinuation graph approval.successor proposal.aircraft peer rule =
        some
          { kind := .continueCurrentPath
            scenario :=
              mkAirProposalSeparationScenario
                graph
                approval.successor
                { aircraft := proposal.aircraft
                  state := successorAirState
                  act := .continueOnEdge }
                peer
                rule } := by
  have hSoundInv :=
    AirKernelSoundnessTheorem
      graph
      state
      proposal
      approval
      hWf
      hInv
      hApproved
  rcases hSoundInv with ⟨hSound, hSuccessorInv⟩
  rcases hSound with ⟨_hKernel, _hSubject, _hTick, hLocal, _hEffect, hSuccessor⟩
  cases proposal with
  | mk aircraft airState act =>
      cases act with
      | continueOnEdge =>
          rcases hCapable with _hCapable
          rcases hLocal with ⟨hLookup, _hKnown, hAvailable⟩
          have hSuccessorInv' :
              AirInv graph
                (applyAirProposal
                  graph
                  state
                  { aircraft := aircraft
                    state := airState
                    act := .continueOnEdge }) := by
            simpa [hSuccessor] using hSuccessorInv
          refine ⟨airState, ?_, ?_⟩
          · simpa [hSuccessor] using hLookup
          · rw [hSuccessor]
            exact
              continueCurrentPathContinuation_of_available_state
                hSuccessorInv'
                hLookup
                hAvailable
      | reduceSpeedMax targetMaxKt =>
          rcases hCapable with _hCapable
          rcases hLocal with ⟨hLookup, _hKnown, hAvailable, _hSpeedLegal⟩
          have hSuccessorInv' :
              AirInv graph
                (applyAirProposal
                  graph
                  state
                  { aircraft := aircraft
                    state := airState
                    act := .reduceSpeedMax targetMaxKt }) := by
            simpa [hSuccessor] using hSuccessorInv
          let successorAirState := speedReducedState airState targetMaxKt
          refine ⟨successorAirState, ?_, ?_⟩
          · rw [hSuccessor]
            simp [applyAirProposal, successorAirState, lookupAirborneState, speedReducedState]
          · have hLookupSucc :
                lookupAirborneState
                  (applyAirProposal
                    graph
                    state
                    { aircraft := aircraft
                      state := airState
                      act := .reduceSpeedMax targetMaxKt }).aircraft
                  aircraft = some successorAirState := by
                simp [applyAirProposal, successorAirState, lookupAirborneState, speedReducedState]
            have hAvailableSucc :
                EdgeAvailable
                  (applyAirProposal
                    graph
                    state
                    { aircraft := aircraft
                      state := airState
                      act := .reduceSpeedMax targetMaxKt })
                  aircraft
                  successorAirState.edge := by
                simpa [EdgeAvailable, applyAirProposal, successorAirState, speedReducedState] using hAvailable
            rw [hSuccessor]
            exact
              continueCurrentPathContinuation_of_available_state
                hSuccessorInv'
                hLookupSucc
                hAvailableSucc
      | takeBranch _ =>
          cases hCapable
      | changeAltitudeBand _ =>
          cases hCapable
      | reserveJunction _ =>
          cases hCapable
      | activatePath firstEdge =>
          rcases hCapable with _hCapable
          rcases hLocal with ⟨hLookup, _hKnown, _hRoute, hAvailable⟩
          have hSuccessorInv' :
              AirInv graph
                (applyAirProposal
                  graph
                  state
                  { aircraft := aircraft
                    state := airState
                    act := .activatePath firstEdge }) := by
            simpa [hSuccessor] using hSuccessorInv
          let successorAirState := activatePathSuccessorState airState firstEdge
          refine ⟨successorAirState, ?_, ?_⟩
          · rw [hSuccessor]
            simp [applyAirProposal, successorAirState, activatePathSuccessorState, lookupAirborneState]
          · have hLookupSucc :
                lookupAirborneState
                  (applyAirProposal
                    graph
                    state
                    { aircraft := aircraft
                      state := airState
                      act := .activatePath firstEdge }).aircraft
                  aircraft = some successorAirState := by
                simp [applyAirProposal, successorAirState, activatePathSuccessorState, lookupAirborneState]
            have hAvailableSucc :
                EdgeAvailable
                  (applyAirProposal
                    graph
                    state
                    { aircraft := aircraft
                      state := airState
                      act := .activatePath firstEdge })
                  aircraft
                  successorAirState.edge := by
                simpa [EdgeAvailable, ReservationsAllowEdge, applyAirProposal, airReservationFor,
                  successorAirState, activatePathSuccessorState] using hAvailable
            rw [hSuccessor]
            exact
              continueCurrentPathContinuation_of_available_state
                hSuccessorInv'
                hLookupSucc
                hAvailableSucc
      | activateMissedApproach firstEdge =>
          rcases hCapable with _hCapable
          rcases hLocal with ⟨hLookup, _hKnown, _hRoute, hAvailable⟩
          have hSuccessorInv' :
              AirInv graph
                (applyAirProposal
                  graph
                  state
                  { aircraft := aircraft
                    state := airState
                    act := .activateMissedApproach firstEdge }) := by
            simpa [hSuccessor] using hSuccessorInv
          let successorAirState := activateMissedApproachSuccessorState airState firstEdge
          refine ⟨successorAirState, ?_, ?_⟩
          · rw [hSuccessor]
            simp [applyAirProposal, successorAirState, activateMissedApproachSuccessorState,
              lookupAirborneState]
          · have hLookupSucc :
                lookupAirborneState
                  (applyAirProposal
                    graph
                    state
                    { aircraft := aircraft
                      state := airState
                      act := .activateMissedApproach firstEdge }).aircraft
                  aircraft = some successorAirState := by
                simp [applyAirProposal, successorAirState, activateMissedApproachSuccessorState,
                  lookupAirborneState]
            have hAvailableSucc :
                EdgeAvailable
                  (applyAirProposal
                    graph
                    state
                    { aircraft := aircraft
                      state := airState
                      act := .activateMissedApproach firstEdge })
                  aircraft
                  successorAirState.edge := by
                simpa [EdgeAvailable, ReservationsAllowEdge, applyAirProposal, airReservationFor,
                  successorAirState, activateMissedApproachSuccessorState] using hAvailable
            rw [hSuccessor]
            exact
              continueCurrentPathContinuation_of_available_state
                hSuccessorInv'
                hLookupSucc
                hAvailableSucc

theorem mkSeparationWitness_sound
    {scenario : SeparationScenario} :
    PairwiseSeparated scenario →
      SeparationWitnessSound scenario (mkSeparationWitness scenario) := by
  intro hSeparated
  simp [SeparationWitnessSound, mkSeparationWitness, hSeparated]

theorem separation_check_safe_pairwise
    {scenario : SeparationScenario} {witness : SeparationWitness} :
    SeparationScenarioWellFormed scenario →
    separation_check scenario = .safe witness →
      witness = mkSeparationWitness scenario ∧
        PairwiseSeparated scenario := by
  intro hFormed hCheck
  by_cases hVertical : VerticalRuleSatisfied scenario
  · have hEval :
        separation_check scenario = .safe (mkSeparationWitness scenario) := by
        simp [separation_check, hFormed, hVertical]
    rw [hEval] at hCheck
    cases hCheck
    exact ⟨rfl, Or.inl hVertical⟩
  · by_cases hLongitudinal : LongitudinalRuleSatisfied scenario
    · have hEval :
          separation_check scenario = .safe (mkSeparationWitness scenario) := by
          simp [separation_check, hFormed, hVertical, hLongitudinal]
      rw [hEval] at hCheck
      cases hCheck
      exact ⟨rfl, Or.inr hLongitudinal⟩
    · have hReject :
          separation_check scenario =
            .unsafeResult
              { checkedRule := scenario.rule.id
                detail := "pairwise minima violated" } := by
          simp [separation_check, hFormed, hVertical, hLongitudinal]
      rw [hReject] at hCheck
      cases hCheck

theorem separation_check_safe_wellFormed
    {scenario : SeparationScenario}
    {witness : SeparationWitness}
    (hCheck : separation_check scenario = .safe witness) :
    SeparationScenarioWellFormed scenario := by
  unfold separation_check at hCheck
  by_cases hFormed : SeparationScenarioWellFormed scenario
  · exact hFormed
  · simp [hFormed] at hCheck

theorem SeparationCheckerSoundnessTheorem :
    ∀ scenario witness,
      SeparationScenarioWellFormed scenario →
      separation_check scenario = .safe witness →
        SeparationWitnessSound scenario witness := by
  intro scenario witness hFormed hCheck
  rcases separation_check_safe_pairwise hFormed hCheck with
    ⟨rfl, hSeparated⟩
  exact mkSeparationWitness_sound hSeparated

theorem separation_check_safe_of_pairwise
    {scenario : SeparationScenario} :
    SeparationScenarioWellFormed scenario →
    PairwiseSeparated scenario →
      separation_check scenario = .safe (mkSeparationWitness scenario) := by
  intro hFormed hSeparated
  by_cases hVertical : VerticalRuleSatisfied scenario
  · simp [separation_check, hFormed, hVertical]
  · cases hSeparated with
    | inl hVertical' =>
        contradiction
    | inr hLongitudinal =>
        simp [separation_check, hFormed, hVertical, hLongitudinal]

theorem concreteNeutralAirborneCommand_nonSeparationRelevant
    {command : Command} :
    ConcreteNeutralAirborneCommand command →
      NonSeparationRelevantCommand command := by
  intro hNeutral
  cases command <;> simp [ConcreteNeutralAirborneCommand,
    NonSeparationRelevantCommand, commandPlan, commandProfile, classOf, profile] at hNeutral ⊢

theorem separationNeutralTransition_preserves_pairwise
    {subjectBefore subjectAfter peer : SeparationEntityState}
    {rule : SeparationRule} {horizonSeconds : Nat} :
    SeparationNeutralTransition subjectBefore subjectAfter peer →
    PairwiseSeparated
      { subjectBefore := subjectBefore
        subjectAfter := subjectBefore
        peer := peer
        rule := rule
        horizonSeconds := horizonSeconds } →
      PairwiseSeparated
        { subjectBefore := subjectBefore
          subjectAfter := subjectAfter
          peer := peer
          rule := rule
          horizonSeconds := horizonSeconds } := by
  intro hNeutral hSeparated
  rcases hNeutral with ⟨_, hTrack, _, hLongGap, hVerticalGap⟩
  cases hSeparated with
  | inl hVertical =>
      left
      unfold VerticalRuleSatisfied at hVertical ⊢
      exact Nat.le_trans hVertical hVerticalGap
  | inr hLongitudinal =>
      right
      unfold LongitudinalRuleSatisfied at hLongitudinal ⊢
      rcases hLongitudinal with ⟨hSameTrack, hMinGap⟩
      constructor
      · exact hTrack.symm.trans hSameTrack
      · exact Nat.le_trans hMinGap hLongGap

theorem separationBaselineScenario_preserves_pairwise
    {scenario : SeparationScenario} :
    SeparationNeutralTransition
      scenario.subjectBefore
      scenario.subjectAfter
      scenario.peer →
    PairwiseSeparated (separationBaselineScenario scenario) →
      PairwiseSeparated scenario := by
  intro hNeutral hBaseline
  exact
    separationNeutralTransition_preserves_pairwise
      hNeutral
      (by simpa [separationBaselineScenario] using hBaseline)

theorem SeparationBoundarySufficiencyTheorem
    {command : Command}
    {baselineSubject : SeparationEntityState}
    {scenario : SeparationScenario} :
    PairwiseSeparated
      { subjectBefore := baselineSubject
        subjectAfter := baselineSubject
        peer := scenario.peer
        rule := scenario.rule
        horizonSeconds := scenario.horizonSeconds } →
    SeparationBoundaryCase command baselineSubject scenario →
      PairwiseSeparated scenario := by
  intro hBaseline hCase
  cases hCase with
  | certifiedRelevant witness _ hFormed hCheck =>
      exact (SeparationCheckerSoundnessTheorem scenario witness hFormed hCheck).2.2
  | neutralIrrelevant _ hBefore hNeutral =>
      cases hBefore
      exact separationNeutralTransition_preserves_pairwise hNeutral hBaseline

theorem concreteNeutralAirborneCommand_boundaryCase
    {command : Command}
    {subject : SeparationEntityState}
    {peer : SeparationEntityState}
    {rule : SeparationRule} :
    ConcreteNeutralAirborneCommand command →
      SeparationBoundaryCase
        command
        subject
        { subjectBefore := subject
          subjectAfter := subject
          peer := peer
          rule := rule
          horizonSeconds := H_sep } := by
  intro hNeutral
  apply SeparationBoundaryCase.neutralIrrelevant
  · exact concreteNeutralAirborneCommand_nonSeparationRelevant hNeutral
  · rfl
  · simp [SeparationNeutralTransition]

theorem concreteNeutralAirborneCommand_preserves_pairwise
    {command : Command}
    {subject : SeparationEntityState}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    (hNeutral : ConcreteNeutralAirborneCommand command)
    (hBaseline :
      PairwiseSeparated
        { subjectBefore := subject
          subjectAfter := subject
          peer := peer
          rule := rule
          horizonSeconds := H_sep }) :
    PairwiseSeparated
      { subjectBefore := subject
        subjectAfter := subject
        peer := peer
        rule := rule
        horizonSeconds := H_sep } := by
  exact
    SeparationBoundarySufficiencyTheorem
      hBaseline
      (concreteNeutralAirborneCommand_boundaryCase hNeutral)

theorem viable_sep_of_safe_continuation
    {continuation : SeparationContinuation}
    {continuations : List SeparationContinuation}
    {witness : SeparationWitness} :
    continuation ∈ continuations →
    SeparationScenarioWellFormed continuation.scenario →
    separation_check continuation.scenario = .safe witness →
      Viable_sep continuations := by
  intro hMember hFormed hCheck
  exact ⟨continuation, hMember, witness, hFormed, hCheck⟩

theorem viable_sep_of_pairwise_continuation
    {continuation : SeparationContinuation}
    {continuations : List SeparationContinuation} :
    continuation ∈ continuations →
    SeparationScenarioWellFormed continuation.scenario →
    PairwiseSeparated continuation.scenario →
      Viable_sep continuations := by
  intro hMember hFormed hPairwise
  exact
    viable_sep_of_safe_continuation
      hMember
      hFormed
      (separation_check_safe_of_pairwise hFormed hPairwise)

theorem viable_sep_has_pairwise_continuation
    {continuations : List SeparationContinuation} :
    Viable_sep continuations →
      ∃ continuation ∈ continuations, PairwiseSeparated continuation.scenario := by
  intro hViable
  rcases hViable with ⟨continuation, hMember, witness, hFormed, hCheck⟩
  exact ⟨continuation, hMember,
    (SeparationCheckerSoundnessTheorem continuation.scenario witness hFormed hCheck).2.2⟩

theorem continueCurrentPathContinuation_mem_approvedPairwiseContinuations
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation} :
    continueCurrentPathContinuation graph state aircraft peer rule = some continuation →
      continuation ∈ approvedPairwiseContinuations graph state aircraft peer rule := by
  intro hContinue
  unfold approvedPairwiseContinuations
  rw [hContinue]
  simp

theorem holdCurrentPathContinuation_mem_approvedPairwiseContinuations
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation} :
    holdCurrentPathContinuation graph state aircraft peer rule = some continuation →
      continuation ∈ approvedPairwiseContinuations graph state aircraft peer rule := by
  intro hHold
  unfold approvedPairwiseContinuations
  rw [hHold]
  simp

theorem reduceSpeedContinuations_mem_approvedPairwiseContinuations
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation} :
    continuation ∈ reduceSpeedContinuations graph state aircraft peer rule →
      continuation ∈ approvedPairwiseContinuations graph state aircraft peer rule := by
  intro hReduce
  unfold approvedPairwiseContinuations
  simp [hReduce]

theorem reservedBranchChoiceContinuations_mem_approvedPairwiseContinuations
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation} :
    continuation ∈ reservedBranchChoiceContinuations graph state aircraft peer rule →
      continuation ∈ approvedPairwiseContinuations graph state aircraft peer rule := by
  intro hBranch
  unfold approvedPairwiseContinuations
  simp [hBranch]

theorem recoveryPathContinuations_mem_approvedPairwiseContinuations
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation} :
    continuation ∈ recoveryPathContinuations graph state aircraft peer rule →
      continuation ∈ approvedPairwiseContinuations graph state aircraft peer rule := by
  intro hRecovery
  unfold approvedPairwiseContinuations
  simp [hRecovery]

theorem continueCurrentPathContinuation_neutral
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation} :
    continueCurrentPathContinuation graph state aircraft peer rule = some continuation →
      SeparationNeutralTransition
        continuation.scenario.subjectBefore
        continuation.scenario.subjectAfter
        continuation.scenario.peer := by
  intro hContinue
  unfold continueCurrentPathContinuation at hContinue
  cases hLookup : lookupAirborneState state.aircraft aircraft with
  | none =>
      simp [hLookup] at hContinue
  | some airState =>
      unfold approvedContinuationForProposal at hContinue
      cases hCert :
          air_certify
            graph
            state
            { aircraft := aircraft
              state := airState
              act := .continueOnEdge } with
      | rejected _ =>
          simp [hLookup, hCert] at hContinue
      | approved _ =>
          simp [hLookup, hCert] at hContinue
          cases hContinue
          simp [SeparationNeutralTransition, mkAirProposalSeparationScenario,
            proposalSuccessorAirborneState, applyAirProposal, hLookup]

theorem holdCurrentPathContinuation_neutral
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation} :
    holdCurrentPathContinuation graph state aircraft peer rule = some continuation →
      SeparationNeutralTransition
        continuation.scenario.subjectBefore
        continuation.scenario.subjectAfter
        continuation.scenario.peer := by
  intro hHold
  unfold holdCurrentPathContinuation at hHold
  cases hLookup : lookupAirborneState state.aircraft aircraft with
  | none =>
      simp [hLookup] at hHold
  | some airState =>
      unfold approvedContinuationForProposal at hHold
      cases hCert :
          air_certify
            graph
            state
            { aircraft := aircraft
              state := airState
              act := .continueOnEdge } with
      | rejected _ =>
          simp [hLookup, hCert] at hHold
      | approved _ =>
          simp [hLookup, hCert] at hHold
          cases hHold
          simp [SeparationNeutralTransition, mkAirProposalSeparationScenario,
            proposalSuccessorAirborneState, applyAirProposal, hLookup]

theorem reduceSpeedContinuation_neutral
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation} :
    continuation ∈ reduceSpeedContinuations graph state aircraft peer rule →
      SeparationNeutralTransition
        continuation.scenario.subjectBefore
        continuation.scenario.subjectAfter
        continuation.scenario.peer := by
  intro hReduce
  unfold reduceSpeedContinuations at hReduce
  cases hLookup : lookupAirborneState state.aircraft aircraft with
  | none =>
      simp [hLookup] at hReduce
  | some airState =>
      cases hTarget : speedReductionTarget airState with
      | none =>
          simp [hLookup, hTarget] at hReduce
      | some targetMaxKt =>
          unfold approvedContinuationForProposal at hReduce
          cases hCert :
              air_certify
                graph
                state
                { aircraft := aircraft
                  state := airState
                  act := .reduceSpeedMax targetMaxKt } with
          | rejected _ =>
              simp [hLookup, hTarget, hCert] at hReduce
          | approved _ =>
              simp [hLookup, hTarget, hCert] at hReduce
              cases hReduce
              have hSuccessor :
                  proposalSuccessorAirborneState
                    graph
                    state
                    { aircraft := aircraft
                      state := airState
                      act := .reduceSpeedMax targetMaxKt } =
                    speedReducedState airState targetMaxKt := by
                simp [proposalSuccessorAirborneState, applyAirProposal,
                  lookupAirborneState, speedReducedState]
              simp [SeparationNeutralTransition, mkAirProposalSeparationScenario,
                hSuccessor, speedReducedState, toSeparationEntityState,
                altitudeWindow, longitudinalGapPermille, verticalGapFt]

theorem viable_sep_of_continueCurrentPathContinuation
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation}
    {witness : SeparationWitness} :
    continueCurrentPathContinuation graph state aircraft peer rule = some continuation →
    SeparationScenarioWellFormed continuation.scenario →
    separation_check continuation.scenario = .safe witness →
      Viable_sep (approvedPairwiseContinuations graph state aircraft peer rule) := by
  intro hContinue hFormed hCheck
  exact
    viable_sep_of_safe_continuation
      (continueCurrentPathContinuation_mem_approvedPairwiseContinuations hContinue)
      hFormed
      hCheck

theorem viable_sep_of_continueCurrentPathContinuation_baseline
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation} :
    continueCurrentPathContinuation graph state aircraft peer rule = some continuation →
    SeparationScenarioWellFormed continuation.scenario →
    PairwiseSeparated (separationBaselineScenario continuation.scenario) →
      Viable_sep (approvedPairwiseContinuations graph state aircraft peer rule) := by
  intro hContinue hFormed hBaseline
  exact
    viable_sep_of_pairwise_continuation
      (continueCurrentPathContinuation_mem_approvedPairwiseContinuations hContinue)
      hFormed
      (separationBaselineScenario_preserves_pairwise
        (continueCurrentPathContinuation_neutral hContinue)
        hBaseline)

theorem viable_sep_of_capableApproval_equivIssuedScenario
    {graph : AirGraph}
    {state : AirState}
    {proposal : AirProposal}
    {approval : AirApproval}
    {issuedScenario : SeparationScenario}
    (hWf : AirWellFormed graph)
    (hInv : AirInv graph state)
    (hApproved : air_certify graph state proposal = .approved approval)
    (hCapable : ContinueCurrentPathCapableAct proposal.act)
    (hWell : SeparationScenarioWellFormed issuedScenario)
    (hPairwise : PairwiseSeparated issuedScenario)
    (hEq :
      ∀ {successorAirState},
        lookupAirborneState approval.successor.aircraft proposal.aircraft = some successorAirState →
          SeparationEntityOperationalEq
            (toSeparationEntityState graph proposal.aircraft successorAirState)
            issuedScenario.subjectAfter) :
    Viable_sep
      (approvedPairwiseContinuations
        graph
        approval.successor
        proposal.aircraft
        issuedScenario.peer
        issuedScenario.rule) := by
  rcases
    continueCurrentPathContinuation_of_capableApproval
      (peer := issuedScenario.peer)
      (rule := issuedScenario.rule)
      hWf
      hInv
      hApproved
      hCapable with
    ⟨successorAirState, hLookupSucc, hContinue⟩
  have hOperationalEq :
      SeparationEntityOperationalEq
        (toSeparationEntityState graph proposal.aircraft successorAirState)
        issuedScenario.subjectAfter :=
    hEq hLookupSucc
  have hContinueWell :
      SeparationScenarioWellFormed
        { subjectBefore :=
            toSeparationEntityState graph proposal.aircraft successorAirState
          subjectAfter :=
            toSeparationEntityState graph proposal.aircraft successorAirState
          peer := issuedScenario.peer
          rule := issuedScenario.rule
          horizonSeconds := H_sep } :=
    selfScenarioWellFormed_of_operationalEq_subjectAfter
      hOperationalEq
      hWell
  have hContinuePairwise :
      PairwiseSeparated
        { subjectBefore :=
            toSeparationEntityState graph proposal.aircraft successorAirState
          subjectAfter :=
            toSeparationEntityState graph proposal.aircraft successorAirState
          peer := issuedScenario.peer
          rule := issuedScenario.rule
          horizonSeconds := H_sep } :=
    selfScenarioPairwise_of_operationalEq_subjectAfter
      (subjectBefore := issuedScenario.subjectBefore)
      hOperationalEq
      hPairwise
  exact
    viable_sep_of_continueCurrentPathContinuation_baseline
      hContinue
      (by
        simpa [mkAirProposalSeparationScenario, proposalSuccessorAirborneState,
          applyAirProposal, hLookupSucc] using hContinueWell)
      (by
        simpa [separationBaselineScenario, mkAirProposalSeparationScenario,
          proposalSuccessorAirborneState, applyAirProposal, hLookupSucc] using hContinuePairwise)

theorem viable_sep_of_holdCurrentPathContinuation
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation}
    {witness : SeparationWitness} :
    holdCurrentPathContinuation graph state aircraft peer rule = some continuation →
    SeparationScenarioWellFormed continuation.scenario →
    separation_check continuation.scenario = .safe witness →
      Viable_sep (approvedPairwiseContinuations graph state aircraft peer rule) := by
  intro hHold hFormed hCheck
  exact
    viable_sep_of_safe_continuation
      (holdCurrentPathContinuation_mem_approvedPairwiseContinuations hHold)
      hFormed
      hCheck

theorem viable_sep_of_holdCurrentPathContinuation_baseline
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation} :
    holdCurrentPathContinuation graph state aircraft peer rule = some continuation →
    SeparationScenarioWellFormed continuation.scenario →
    PairwiseSeparated (separationBaselineScenario continuation.scenario) →
      Viable_sep (approvedPairwiseContinuations graph state aircraft peer rule) := by
  intro hHold hFormed hBaseline
  exact
    viable_sep_of_pairwise_continuation
      (holdCurrentPathContinuation_mem_approvedPairwiseContinuations hHold)
      hFormed
      (separationBaselineScenario_preserves_pairwise
        (holdCurrentPathContinuation_neutral hHold)
        hBaseline)

theorem viable_sep_of_reduceSpeedContinuation
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation}
    {witness : SeparationWitness} :
    continuation ∈ reduceSpeedContinuations graph state aircraft peer rule →
    SeparationScenarioWellFormed continuation.scenario →
    separation_check continuation.scenario = .safe witness →
      Viable_sep (approvedPairwiseContinuations graph state aircraft peer rule) := by
  intro hReduce hFormed hCheck
  exact
    viable_sep_of_safe_continuation
      (reduceSpeedContinuations_mem_approvedPairwiseContinuations hReduce)
      hFormed
      hCheck

theorem viable_sep_of_reduceSpeedContinuation_baseline
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation} :
    continuation ∈ reduceSpeedContinuations graph state aircraft peer rule →
    SeparationScenarioWellFormed continuation.scenario →
    PairwiseSeparated (separationBaselineScenario continuation.scenario) →
      Viable_sep (approvedPairwiseContinuations graph state aircraft peer rule) := by
  intro hReduce hFormed hBaseline
  exact
    viable_sep_of_pairwise_continuation
      (reduceSpeedContinuations_mem_approvedPairwiseContinuations hReduce)
      hFormed
      (separationBaselineScenario_preserves_pairwise
        (reduceSpeedContinuation_neutral hReduce)
        hBaseline)

theorem viable_sep_of_reservedBranchChoiceContinuation
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation}
    {witness : SeparationWitness} :
    continuation ∈ reservedBranchChoiceContinuations graph state aircraft peer rule →
    SeparationScenarioWellFormed continuation.scenario →
    separation_check continuation.scenario = .safe witness →
      Viable_sep (approvedPairwiseContinuations graph state aircraft peer rule) := by
  intro hBranch hFormed hCheck
  exact
    viable_sep_of_safe_continuation
      (reservedBranchChoiceContinuations_mem_approvedPairwiseContinuations hBranch)
      hFormed
      hCheck

theorem viable_sep_of_recoveryPathContinuation
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    {continuation : SeparationContinuation}
    {witness : SeparationWitness} :
    continuation ∈ recoveryPathContinuations graph state aircraft peer rule →
    SeparationScenarioWellFormed continuation.scenario →
    separation_check continuation.scenario = .safe witness →
      Viable_sep (approvedPairwiseContinuations graph state aircraft peer rule) := by
  intro hRecovery hFormed hCheck
  exact
    viable_sep_of_safe_continuation
      (recoveryPathContinuations_mem_approvedPairwiseContinuations hRecovery)
      hFormed
      hCheck

end CertifiedAtc
