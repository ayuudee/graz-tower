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

theorem SeparationCheckerSoundnessTheorem :
    ∀ scenario witness,
      SeparationScenarioWellFormed scenario →
      separation_check scenario = .safe witness →
        SeparationWitnessSound scenario witness := by
  intro scenario witness hFormed hCheck
  rcases separation_check_safe_pairwise hFormed hCheck with
    ⟨rfl, hSeparated⟩
  exact mkSeparationWitness_sound hSeparated

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

end CertifiedAtc
