import CertifiedAtc.ScopedGreenfield
import CertifiedAtc.ScopedSeparation

namespace CertifiedAtc
namespace Greenfield

/--
`ScopedIssuance` is the final Milestone 5 top layer for `Safety-complete (N₀)`.

It does not rebuild orchestration. It adds the thinnest honest bridge from the
scoped greenfield instruction surface into the older theorem-bearing atomic
certified-path layer, then packages the top-layer theorems we still need:

- routing completeness
- plan-instantiation correctness
- peer-coverage soundness
- compatibility narrowness
- authority-gated issuance
- non-bypass
- issuance soundness

The bridge is intentionally partial. If a greenfield instruction cannot be
faithfully carried into the older atomic command surface, it stays out of the
scoped claim instead of being coerced.
-/

def bridgeableLevelToLegacyAltitudeFt? : Level → Option Int
  | .altitudeFeet feet => some feet
  | .flightLevel fl => some (Int.ofNat (fl * 100))
  | .heightFeet _ => none

def bridgeableSpeedToLegacyKnots? : Speed → Option Nat
  | .inKnots knots => some knots
  | .inMachPermille _ => none

def scopedAtomicCommand? : AtcInstruction → Option Command
  | .taxiTo target destination via =>
      some (.taxiTo target via destination)
  | .holdShortOf target runway =>
      some (.holdShortOf target runway)
  | .crossRunway target runway =>
      some (.crossRunway target runway)
  | .lineUpAndWait target runway =>
      some (.lineUpAndWait target runway)
  | .clearedForTakeoff target runway =>
      some (.clearedForTakeoff target runway)
  | .clearedToLand target runway =>
      some (.clearedToLand target runway)
  | .clearedTouchAndGo target runway =>
      some (.clearedTouchAndGo target runway)
  | .goAround target =>
      some (.goAround target)
  | .reportDownwind target =>
      some (.reportDownwind target)
  | .reportFinal target =>
      some (.reportFinal target)
  | .proceed target =>
      some (.proceed target)
  | .setSquawk target code =>
      some (.squawkCode target code)
  | .reduceSpeedTo target speed =>
      match bridgeableSpeedToLegacyKnots? speed with
      | some knots => some (.reduceSpeedTo target knots)
      | none => none
  | .climbTo target level =>
      match bridgeableLevelToLegacyAltitudeFt? level with
      | some altitude => some (.climbTo target altitude)
      | none => none
  | .descendTo target level =>
      match bridgeableLevelToLegacyAltitudeFt? level with
      | some altitude => some (.descendTo target altitude)
      | none => none
  | _ => none

def scopedCommandProposal
    (issuer : ControllerId)
    (instruction : AtcInstruction) :
    Option CommandProposal :=
  match scopedAtomicCommand? instruction with
  | some command =>
      some { proposer := issuer, command := command }
  | none =>
      none

def ScopedSeparationCertifiedInstruction : AtcInstruction → Prop
  | .clearedForTakeoff _ _ => True
  | .clearedToLand _ _ => True
  | .clearedTouchAndGo _ _ => True
  | .goAround _ => True
  | .reduceSpeedTo _ (.inKnots _) => True
  | _ => False

def scopedInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match scopedInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

theorem scopedCertifiedInstruction_hasAtomicBridge
    {instruction : AtcInstruction}
    (hScoped : ScopedCertifiedInstruction instruction) :
    ∃ command, scopedAtomicCommand? instruction = some command := by
  cases instruction with
  | taxiTo target destination via =>
      exact ⟨.taxiTo target via destination, rfl⟩
  | holdShortOf target runway =>
      exact ⟨.holdShortOf target runway, rfl⟩
  | crossRunway target runway =>
      exact ⟨.crossRunway target runway, rfl⟩
  | lineUpAndWait target runway =>
      exact ⟨.lineUpAndWait target runway, rfl⟩
  | clearedForTakeoff target runway =>
      exact ⟨.clearedForTakeoff target runway, rfl⟩
  | clearedToLand target runway =>
      exact ⟨.clearedToLand target runway, rfl⟩
  | clearedTouchAndGo target runway =>
      exact ⟨.clearedTouchAndGo target runway, rfl⟩
  | goAround target =>
      exact ⟨.goAround target, rfl⟩
  | reduceSpeedTo target speed =>
      cases speed with
      | inKnots knots =>
          exact ⟨.reduceSpeedTo target knots, rfl⟩
      | inMachPermille _ =>
          cases hScoped
  | climbTo target level =>
      cases hScoped
  | descendTo target level =>
      cases hScoped
  | _ =>
      cases hScoped

theorem scopedAtomicCommand_target_preserved
    {instruction : AtcInstruction}
    {command : Command}
    (hTranslate : scopedAtomicCommand? instruction = some command) :
    commandTarget command = instructionTarget instruction := by
  cases instruction with
  | taxiTo target destination via =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | holdShortOf target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | crossRunway target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | lineUpAndWait target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | clearedForTakeoff target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | clearedToLand target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | clearedTouchAndGo target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | goAround target =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | reportDownwind target =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | reportFinal target =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | proceed target =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | setSquawk target code =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [commandTarget, instructionTarget]
  | reduceSpeedTo target speed =>
      cases speed with
      | inKnots knots =>
          simp [scopedAtomicCommand?, bridgeableSpeedToLegacyKnots?] at hTranslate
          cases hTranslate
          simp [commandTarget, instructionTarget]
      | inMachPermille _ =>
          simp [scopedAtomicCommand?, bridgeableSpeedToLegacyKnots?] at hTranslate
  | climbTo target level =>
      cases level with
      | altitudeFeet feet =>
          simp [scopedAtomicCommand?, bridgeableLevelToLegacyAltitudeFt?] at hTranslate
          cases hTranslate
          simp [commandTarget, instructionTarget]
      | heightFeet _ =>
          simp [scopedAtomicCommand?, bridgeableLevelToLegacyAltitudeFt?] at hTranslate
      | flightLevel fl =>
          simp [scopedAtomicCommand?, bridgeableLevelToLegacyAltitudeFt?] at hTranslate
          cases hTranslate
          simp [commandTarget, instructionTarget]
  | descendTo target level =>
      cases level with
      | altitudeFeet feet =>
          simp [scopedAtomicCommand?, bridgeableLevelToLegacyAltitudeFt?] at hTranslate
          cases hTranslate
          simp [commandTarget, instructionTarget]
      | heightFeet _ =>
          simp [scopedAtomicCommand?, bridgeableLevelToLegacyAltitudeFt?] at hTranslate
      | flightLevel fl =>
          simp [scopedAtomicCommand?, bridgeableLevelToLegacyAltitudeFt?] at hTranslate
          cases hTranslate
          simp [commandTarget, instructionTarget]
  | _ =>
      simp [scopedAtomicCommand?] at hTranslate

theorem scopedCommandProposal_command_eq_and_proposer
    {issuer : ControllerId}
    {instruction : AtcInstruction}
    {proposal : CommandProposal}
    (hProposal : scopedCommandProposal issuer instruction = some proposal) :
    scopedAtomicCommand? instruction = some proposal.command ∧
      proposal.proposer = issuer := by
  unfold scopedCommandProposal at hProposal
  cases hCommand : scopedAtomicCommand? instruction with
  | none =>
      simp [hCommand] at hProposal
  | some command =>
      simp [hCommand] at hProposal
      cases hProposal
      simp

theorem scopedSafetySeparationCommand_of_instruction
    {instruction : AtcInstruction}
    {command : Command}
    (hSeparation : ScopedSeparationCertifiedInstruction instruction)
    (hTranslate : scopedAtomicCommand? instruction = some command) :
    ScopedSafetySeparationCommand command := by
  cases instruction with
  | clearedForTakeoff target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [ScopedSafetySeparationCommand]
  | clearedToLand target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [ScopedSafetySeparationCommand]
  | clearedTouchAndGo target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [ScopedSafetySeparationCommand]
  | goAround target =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [ScopedSafetySeparationCommand]
  | reduceSpeedTo target speed =>
      cases speed with
      | inKnots knots =>
          simp [scopedAtomicCommand?, bridgeableSpeedToLegacyKnots?] at hTranslate
          cases hTranslate
          simp [ScopedSafetySeparationCommand]
      | inMachPermille _ =>
          cases hSeparation
  | climbTo target level =>
      cases hSeparation
  | descendTo target level =>
      cases hSeparation
  | _ =>
      cases hSeparation

theorem scopedInstructionIssuerAuthorized_eq_true_of_unmapped
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instruction : AtcInstruction}
    (hUnmapped : scopedInstructionRequiredAuthorityGrant? instruction = none) :
    scopedInstructionIssuerAuthorized view controller instruction = true := by
  simp [scopedInstructionIssuerAuthorized, hUnmapped]

theorem ScopedAuthorityGatedIssuanceTheorem
    {world : ScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : ScopedExtractionWellFormed world)
    (hMapped : scopedInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world controller grant) :
    scopedInstructionIssuerAuthorized
        (extractCompileView world)
        controller
        instruction = true := by
  simp [scopedInstructionIssuerAuthorized, hMapped,
    controllerHasAuthorityGrant_of_worldControllerHasGrant hWf hGrant]

theorem ScopedRoutingCompletenessTheorem :
  ∀ {instruction : AtcInstruction} {command : Command},
    ScopedCertifiedInstruction instruction →
    scopedAtomicCommand? instruction = some command →
      let template := compile_command (classOf command)
      template.certifiedPathDefined = true ∧
        template.compatibility = true ∧
        ((template.runway = true) ∨ (template.surface = true) ∨
          (template.air = true) ∨ (template.separation = true)) := by
  intro instruction command hScoped hTranslate
  cases instruction with
  | taxiTo target destination via =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [compile_command, profile, classOf]
  | holdShortOf target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [compile_command, profile, classOf]
  | crossRunway target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [compile_command, profile, classOf]
  | lineUpAndWait target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [compile_command, profile, classOf]
  | clearedForTakeoff target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [compile_command, profile, classOf]
  | clearedToLand target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [compile_command, profile, classOf]
  | clearedTouchAndGo target runway =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [compile_command, profile, classOf]
  | goAround target =>
      simp [scopedAtomicCommand?] at hTranslate
      cases hTranslate
      simp [compile_command, profile, classOf]
  | reduceSpeedTo target speed =>
      cases speed with
      | inKnots knots =>
          simp [scopedAtomicCommand?, bridgeableSpeedToLegacyKnots?] at hTranslate
          cases hTranslate
          simp [compile_command, profile, classOf]
      | inMachPermille _ =>
          cases hScoped
  | climbTo target level =>
      cases hScoped
  | descendTo target level =>
      cases hScoped
  | _ =>
      cases hScoped

theorem ScopedPlanInstantiationCorrectnessTheorem :
  ∀ {world : ScopedAviationWorld}
    {state : OrchestrationState}
    {issuer : ControllerId}
    {instruction : AtcInstruction}
    {proposal : CommandProposal}
    {plan : CertificationPlan},
    scopedCommandProposal issuer instruction = some proposal →
    instantiate_plan (extractOrchestrationEnv world) state proposal = Except.ok plan →
      PlanMatchesTemplate (compile_command (classOf proposal.command)) plan := by
  intro world state issuer instruction proposal plan _ hPlan
  exact PlanInstantiationTheorem (extractOrchestrationEnv world) state proposal plan hPlan

theorem ScopedPeerCoverageSoundnessTheorem :
  ∀ {world : ScopedAviationWorld}
    {state : OrchestrationState}
    {issuer : ControllerId}
    {instruction : AtcInstruction}
    {proposal : CommandProposal}
    {plan : CertificationPlan}
    {peer : EntityId},
    scopedCommandProposal issuer instruction = some proposal →
    ScopedSeparationCertifiedInstruction instruction →
    instantiate_plan (extractOrchestrationEnv world) state proposal = Except.ok plan →
    PotentiallyConflictingPeer
      (extractOrchestrationEnv world)
      state
      (instructionTarget instruction)
      peer →
      ScenarioCoversPeer plan (instructionTarget instruction) peer := by
  intro world state issuer instruction proposal plan peer hProposal hSeparation hPlan hPeer
  have hBridge := scopedCommandProposal_command_eq_and_proposer hProposal
  have hTranslate : scopedAtomicCommand? instruction = some proposal.command := hBridge.1
  have hTarget :
      commandTarget proposal.command = instructionTarget instruction :=
    scopedAtomicCommand_target_preserved hTranslate
  have hScopedCommand :
      ScopedSafetySeparationCommand proposal.command :=
    scopedSafetySeparationCommand_of_instruction hSeparation hTranslate
  have hPeerCommand :
      PotentiallyConflictingPeer
        (extractOrchestrationEnv world)
        state
        (commandTarget proposal.command)
        peer := by
    simpa [hTarget] using hPeer
  have hCover :=
    ScopedSeparationCoverageTheorem
      (extractOrchestrationEnv world)
      state
      proposal
      plan
      peer
      hPlan
      hScopedCommand
      hPeerCommand
  simpa [hTarget] using hCover

theorem ScopedCompatibilityNarrownessTheorem :
    NarrowCompatibilityOnly compatibility_check :=
  CompatibilityNarrownessTheorem

theorem ScopedNonBypassTheorem :
  ∀ {world : ScopedAviationWorld}
    {state : OrchestrationState}
    {issuer : ControllerId}
    {instruction : AtcInstruction}
    {proposal : CommandProposal}
    {issued : IssuedRecord},
    scopedCommandProposal issuer instruction = some proposal →
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

theorem ScopedCertifiedIssuanceSoundnessTheorem :
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
    ScopedExtractionWellFormed world →
    ReachableResolvedSet existing →
    resolved.source.id ∉ resolvedClearanceIds existing →
    ResolvesClearance resolutionWorld initialState clearance resolved finalState →
    ScopedSafetyStructuredClearance clearance →
    instruction ∈ structuredFrontierInstructions clearance →
    ScopedCertifiedInstruction instruction →
    scopedCommandProposal clearance.issuedBy instruction = some proposal →
    (∀ grant,
      scopedInstructionRequiredAuthorityGrant? instruction = some grant →
        WorldControllerHasGrant world clearance.issuedBy grant) →
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
        scopedInstructionIssuerAuthorized
          (extractCompileView world)
          clearance.issuedBy
          instruction = true ∧
        ReachableResolvedSet
          (admitResolvedClearance existing resolved).clearances := by
  intro world state existing resolutionWorld initialState finalState clearance resolved instruction proposal
    issued hWf hReach hFresh hResolve _ _ hScoped hProposal hAuthority hIssued
  have hNonBypass :=
    ScopedNonBypassTheorem
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
    ScopedPlanInstantiationCorrectnessTheorem
      (world := world)
      (state := state)
      (issuer := clearance.issuedBy)
      (instruction := instruction)
      (proposal := proposal)
      (plan := plan)
      hProposal
      hPlan
  have hAuthorized :
      scopedInstructionIssuerAuthorized
        (extractCompileView world)
        clearance.issuedBy
        instruction = true := by
    cases hMapped : scopedInstructionRequiredAuthorityGrant? instruction with
    | none =>
        exact scopedInstructionIssuerAuthorized_eq_true_of_unmapped hMapped
    | some grant =>
        exact
          ScopedAuthorityGatedIssuanceTheorem
            hWf
            hMapped
            (hAuthority grant hMapped)
  exact
    ⟨plan, approvals, hPlan, hPlanMatches, hBundle, hCompat, hAuthorized,
      scopedResolvedAdmission_reachable_of_resolved hReach hFresh hResolve⟩

end Greenfield
end CertifiedAtc
