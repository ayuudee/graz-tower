import CertifiedAtc.GreenfieldRouteBearing
import CertifiedAtc.GreenfieldReachability
import CertifiedAtc.ScopedExtraction

namespace CertifiedAtc
namespace Greenfield

/--
Legacy-bridge issuance surface for the route-bearing widening.

This module intentionally covers only the route-bearing families that the older
atomic certified path can already carry honestly:

- `ClearedApproach`
- `JoinCircuit`

`ClearedTo` and `HoldAt` are deliberately excluded here. They already have real
resolved semantics in the current greenfield/runtime model, but the older
atomic issuance path still lacks an honest local act story for them.
-/

def legacyApproachTypeString : ApproachType → String
  | .ils => "ILS"
  | .loc => "LOC"
  | .rnav => "RNAV"
  | .rnp => "RNP"
  | .vor => "VOR"
  | .ndb => "NDB"
  | .sra => "SRA"
  | .visual => "VISUAL"
  | .par => "PAR"

def legacyCircuitDirection :
    Greenfield.CircuitDirection → CertifiedAtc.CircuitDirection
  | .leftHand => .left
  | .rightHand => .right

def legacyJoinType? : Greenfield.JoinType → Option CertifiedAtc.JoinType
  | .downwind => some .downwind
  | .base => some .base
  | .straightIn => some .straightIn
  | _ => none

def BridgeableRouteBearingIssuanceInstruction : AtcInstruction → Prop
  | .clearedApproach _ _ _ _ => True
  | .joinCircuit _ _ joinType _ =>
      match legacyJoinType? joinType with
      | some _ => True
      | none => False
  | _ => False

def bridgeableRouteBearingAtomicCommand? : AtcInstruction → Option Command
  | .clearedApproach target approachType runway _ =>
      some (.clearedApproach target runway (legacyApproachTypeString approachType))
  | .joinCircuit target direction joinType runway =>
      match legacyJoinType? joinType with
      | some joinType =>
          some (.joinCircuit target (legacyCircuitDirection direction) joinType runway)
      | none =>
          none
  | _ => none

def bridgeableRouteBearingCommandProposal
    (issuer : ControllerId)
    (instruction : AtcInstruction) :
    Option CommandProposal :=
  match bridgeableRouteBearingAtomicCommand? instruction with
  | some command =>
      some { proposer := issuer, command := command }
  | none =>
      none

def BridgeableRouteBearingSeparationInstruction : AtcInstruction → Prop
  | .clearedApproach _ _ _ _ => True
  | _ => False

theorem bridgeableRouteBearingInstruction_hasAtomicBridge
    {instruction : AtcInstruction}
    (hBridgeable : BridgeableRouteBearingIssuanceInstruction instruction) :
    ∃ command, bridgeableRouteBearingAtomicCommand? instruction = some command := by
  cases instruction with
  | clearedApproach target approachType runway circlingRunway =>
      exact ⟨.clearedApproach target runway (legacyApproachTypeString approachType), rfl⟩
  | joinCircuit target direction joinType runway =>
      cases hJoin : legacyJoinType? joinType with
      | none =>
          simp [BridgeableRouteBearingIssuanceInstruction, hJoin] at hBridgeable
      | some legacyJoin =>
          exact ⟨.joinCircuit target (legacyCircuitDirection direction) legacyJoin runway,
            by simp [bridgeableRouteBearingAtomicCommand?, hJoin]⟩
  | _ =>
      cases hBridgeable

theorem bridgeableRouteBearingAtomicCommand_target_preserved
    {instruction : AtcInstruction}
    {command : Command}
    (hTranslate : bridgeableRouteBearingAtomicCommand? instruction = some command) :
    commandTarget command = instructionTarget instruction := by
  cases instruction with
  | clearedApproach target approachType runway circlingRunway =>
      simp [bridgeableRouteBearingAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | joinCircuit target direction joinType runway =>
      cases hJoin : legacyJoinType? joinType with
      | none =>
          simp [bridgeableRouteBearingAtomicCommand?, hJoin] at hTranslate
      | some legacyJoin =>
          simp [bridgeableRouteBearingAtomicCommand?, hJoin] at hTranslate
          cases hTranslate
          simp [commandTarget, instructionTarget]
  | _ =>
      simp [bridgeableRouteBearingAtomicCommand?] at hTranslate

theorem bridgeableRouteBearingCommandProposal_command_eq_and_proposer
    {issuer : ControllerId}
    {instruction : AtcInstruction}
    {proposal : CommandProposal}
    (hProposal : bridgeableRouteBearingCommandProposal issuer instruction = some proposal) :
    bridgeableRouteBearingAtomicCommand? instruction = some proposal.command ∧
      proposal.proposer = issuer := by
  unfold bridgeableRouteBearingCommandProposal at hProposal
  cases hCommand : bridgeableRouteBearingAtomicCommand? instruction with
  | none =>
      simp [hCommand] at hProposal
  | some command =>
      simp [hCommand] at hProposal
      cases hProposal
      simp

theorem bridgeableRouteBearingSeparationInstruction_hasSeparationPlan
    {instruction : AtcInstruction}
    {command : Command}
    (hSeparation : BridgeableRouteBearingSeparationInstruction instruction)
    (hTranslate : bridgeableRouteBearingAtomicCommand? instruction = some command) :
    (compile_command (classOf command)).separation = true := by
  cases instruction with
  | clearedApproach target approachType runway circlingRunway =>
      simp [bridgeableRouteBearingAtomicCommand?] at hTranslate
      cases hTranslate
      simp [compile_command, profile, classOf]
  | _ =>
      cases hSeparation

theorem BridgeableRouteBearingRoutingCompletenessTheorem :
  ∀ {instruction : AtcInstruction} {command : Command},
    BridgeableRouteBearingIssuanceInstruction instruction →
    bridgeableRouteBearingAtomicCommand? instruction = some command →
      let template := compile_command (classOf command)
      template.certifiedPathDefined = true ∧
        template.compatibility = true ∧
        ((template.runway = true) ∨ (template.surface = true) ∨
          (template.air = true) ∨ (template.separation = true)) := by
  intro instruction command hBridgeable hTranslate
  cases instruction with
  | clearedApproach target approachType runway circlingRunway =>
      simp [bridgeableRouteBearingAtomicCommand?] at hTranslate
      cases hTranslate
      simp [compile_command, profile, classOf]
  | joinCircuit target direction joinType runway =>
      cases hJoin : legacyJoinType? joinType with
      | none =>
          simp [BridgeableRouteBearingIssuanceInstruction, hJoin] at hBridgeable
      | some legacyJoin =>
          simp [bridgeableRouteBearingAtomicCommand?, hJoin] at hTranslate
          cases hTranslate
          simp [compile_command, profile, classOf]
  | _ =>
      cases hBridgeable

theorem BridgeableRouteBearingPlanInstantiationCorrectnessTheorem :
  ∀ {world : ScopedAviationWorld}
    {state : OrchestrationState}
    {issuer : ControllerId}
    {instruction : AtcInstruction}
    {proposal : CommandProposal}
    {plan : CertificationPlan},
    bridgeableRouteBearingCommandProposal issuer instruction = some proposal →
    instantiate_plan (extractOrchestrationEnv world) state proposal = Except.ok plan →
      PlanMatchesTemplate (compile_command (classOf proposal.command)) plan := by
  intro world state issuer instruction proposal plan _ hPlan
  exact PlanInstantiationTheorem (extractOrchestrationEnv world) state proposal plan hPlan

theorem BridgeableRouteBearingPeerCoverageSoundnessTheorem :
  ∀ {world : ScopedAviationWorld}
    {state : OrchestrationState}
    {issuer : ControllerId}
    {instruction : AtcInstruction}
    {proposal : CommandProposal}
    {plan : CertificationPlan}
    {peer : EntityId},
    bridgeableRouteBearingCommandProposal issuer instruction = some proposal →
    BridgeableRouteBearingSeparationInstruction instruction →
    instantiate_plan (extractOrchestrationEnv world) state proposal = Except.ok plan →
    PotentiallyConflictingPeer
      (extractOrchestrationEnv world)
      state
      (instructionTarget instruction)
      peer →
      ScenarioCoversPeer plan (instructionTarget instruction) peer := by
  intro world state issuer instruction proposal plan peer hProposal hSeparation hPlan hPeer
  have hBridge :=
    bridgeableRouteBearingCommandProposal_command_eq_and_proposer hProposal
  have hTranslate :
      bridgeableRouteBearingAtomicCommand? instruction = some proposal.command := hBridge.1
  have hTarget :
      commandTarget proposal.command = instructionTarget instruction :=
    bridgeableRouteBearingAtomicCommand_target_preserved hTranslate
  have hPlanSep :
      (compile_command (classOf proposal.command)).separation = true :=
    bridgeableRouteBearingSeparationInstruction_hasSeparationPlan hSeparation hTranslate
  have hPeerCommand :
      PotentiallyConflictingPeer
        (extractOrchestrationEnv world)
        state
        (commandTarget proposal.command)
        peer := by
    simpa [hTarget] using hPeer
  have hCover :=
    SeparationCoverageTheorem
      (extractOrchestrationEnv world)
      state
      proposal
      plan
      peer
      hPlan
      hPlanSep
      hPeerCommand
  simpa [hTarget] using hCover

theorem BridgeableRouteBearingNonBypassTheorem :
  ∀ {world : ScopedAviationWorld}
    {state : OrchestrationState}
    {issuer : ControllerId}
    {instruction : AtcInstruction}
    {proposal : CommandProposal}
    {issued : IssuedRecord},
    bridgeableRouteBearingCommandProposal issuer instruction = some proposal →
    issue_command (extractOrchestrationEnv world) state proposal = .issued issued →
      ∃ plan approvals,
        instantiate_plan (extractOrchestrationEnv world) state proposal = Except.ok plan ∧
        ApprovalsSatisfyPlan
          (extractOrchestrationEnv world)
          state
          plan
          approvals ∧
        compatibility_check
          { mode := state.mode
            activeSet := state.activeSet
            approvals := approvals } = .compatible := by
  intro world state issuer instruction proposal issued _ hIssued
  exact NonBypassTheorem (extractOrchestrationEnv world) state proposal issued hIssued

theorem BridgeableRouteBearingIssuanceSoundnessTheorem :
  ∀ {world : ScopedAviationWorld}
    {state : OrchestrationState}
    {existing : List ManagedResolvedClearance}
    {resolutionWorld : ResolutionWorld}
    {initialState finalState : ResolutionState}
    {clearance : StructuredClearance}
    {resolved : ResolvedClearance}
    {instruction : AtcInstruction}
    {proposal : CommandProposal}
    {issued : IssuedRecord},
    ReachableResolvedSet existing →
    resolved.source.id ∉ resolvedClearanceIds existing →
    ResolvesClearance resolutionWorld initialState clearance resolved finalState →
    instruction ∈ structuredFrontierInstructions clearance →
    BridgeableRouteBearingIssuanceInstruction instruction →
    bridgeableRouteBearingCommandProposal clearance.issuedBy instruction = some proposal →
    issue_command (extractOrchestrationEnv world) state proposal = .issued issued →
      ∃ plan approvals,
        instantiate_plan (extractOrchestrationEnv world) state proposal = Except.ok plan ∧
        PlanMatchesTemplate (compile_command (classOf proposal.command)) plan ∧
        ApprovalsSatisfyPlan
          (extractOrchestrationEnv world)
          state
          plan
          approvals ∧
        compatibility_check
          { mode := state.mode
            activeSet := state.activeSet
            approvals := approvals } = .compatible ∧
        ReachableResolvedSet
          (admitResolvedClearance existing resolved).clearances := by
  intro world state existing resolutionWorld initialState finalState clearance resolved instruction proposal
    issued hReach hFresh hResolve _ hBridgeable hProposal hIssued
  have hNonBypass :=
    BridgeableRouteBearingNonBypassTheorem
      (world := world)
      (state := state)
      (issuer := clearance.issuedBy)
      (instruction := instruction)
      (proposal := proposal)
      (issued := issued)
      hProposal
      hIssued
  rcases hNonBypass with ⟨plan, approvals, hPlan, hBundle, hCompat⟩
  have hPlanMatches :=
    BridgeableRouteBearingPlanInstantiationCorrectnessTheorem
      (world := world)
      (state := state)
      (issuer := clearance.issuedBy)
      (instruction := instruction)
      (proposal := proposal)
      (plan := plan)
      hProposal
      hPlan
  exact
    ⟨plan, approvals, hPlan, hPlanMatches, hBundle, hCompat,
      ReachableResolvedSet.admit_of_resolved hReach hFresh hResolve⟩

end Greenfield
end CertifiedAtc
