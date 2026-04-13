import CertifiedAtc.ScopedIssuance

namespace CertifiedAtc
namespace Greenfield

structure ScopedSafetyWorldWellFormed
    (world : ScopedAviationWorld) : Prop where
  extraction : ScopedExtractionWellFormed world
  runway : RunwayWellFormed world.runwayKernel
  surface : SurfaceWellFormed world.surfaceGraph
  air : AirWellFormed world.airGraph

def ScopedOrchestrationInv
    (world : ScopedAviationWorld)
    (state : OrchestrationState) : Prop :=
  NominalAssumptions (extractOrchestrationEnv world) state ∧
    RunwayInv world.runwayKernel state.runway ∧
    SurfaceInv world.surfaceGraph state.surface ∧
    AirInv world.airGraph state.air ∧
    InterfaceInv state

inductive SeparationWitnessesSound :
    List SeparationScenario → List SeparationWitness → Prop
  | nil :
      SeparationWitnessesSound [] []
  | cons
      {scenario : SeparationScenario}
      {witness : SeparationWitness}
      {scenarios : List SeparationScenario}
      {witnesses : List SeparationWitness}
      (head : SeparationWitnessSound scenario witness)
      (tail : SeparationWitnessesSound scenarios witnesses) :
      SeparationWitnessesSound (scenario :: scenarios) (witness :: witnesses)

def SeparationApprovalsSound
    (plan : CertificationPlan)
    (approvals : ApprovalBundle) : Prop :=
  SeparationWitnessesSound plan.separation approvals.separation

theorem SeparationWitnessesSound.exists_witness_of_mem
    {scenarios : List SeparationScenario}
    {witnesses : List SeparationWitness}
    {scenario : SeparationScenario}
    (hSound : SeparationWitnessesSound scenarios witnesses)
    (hMem : scenario ∈ scenarios) :
    ∃ witness, witness ∈ witnesses ∧ SeparationWitnessSound scenario witness := by
  induction hSound generalizing scenario with
  | nil =>
      cases hMem
  | @cons headScenario headWitness tailScenarios tailWitnesses hHead hTail ih =>
      simp at hMem
      rcases hMem with rfl | hTailMem
      · exact ⟨headWitness, by simp [hHead]⟩
      · rcases ih hTailMem with ⟨witness, hWitnessMem, hWitnessSound⟩
        exact ⟨witness, by simp [hWitnessMem], hWitnessSound⟩

theorem collectRunwayApprovals_preserves_inv :
    ∀ {env : RunwayKernelEnv}
      {state : RunwayState}
      {proposals : List RunwayProposal}
      {result : List RunwayApproval × RunwayState},
      RunwayWellFormed env →
      RunwayInv env state →
      collectRunwayApprovals env state proposals = .ok result →
        RunwayInv env result.2 := by
  intro env state proposals result
  induction proposals generalizing state result with
  | nil =>
      intro hWf hInv hCollect
      simp [collectRunwayApprovals] at hCollect
      cases hCollect
      simpa using hInv
  | cons proposal tail ih =>
      intro hWf hInv hCollect
      unfold collectRunwayApprovals at hCollect
      cases hCert : runway_certify env state proposal with
      | rejected reason =>
          simp [hCert] at hCollect
      | approved approval =>
          cases hRest : collectRunwayApprovals env approval.successor tail with
          | error err =>
              simp [hCert, hRest] at hCollect
          | ok next =>
              rcases
                RunwayKernelMilestone1Theorem
                  env
                  state
                  proposal
                  approval
                  hWf
                  hInv
                  hCert with
                ⟨_, hNextInv⟩
              have hTailInv : RunwayInv env next.2 :=
                ih (state := approval.successor) (result := next) hWf hNextInv hRest
              simp [hCert, hRest] at hCollect
              cases hCollect
              simpa using hTailInv

theorem collectSurfaceApprovals_preserves_inv :
    ∀ {graph : SurfaceGraph}
      {state : SurfaceState}
      {proposals : List SurfaceProposal}
      {result : List SurfaceApproval × SurfaceState},
      SurfaceWellFormed graph →
      SurfaceInv graph state →
      collectSurfaceApprovals graph state proposals = .ok result →
        SurfaceInv graph result.2 := by
  intro graph state proposals result
  induction proposals generalizing state result with
  | nil =>
      intro hWf hInv hCollect
      simp [collectSurfaceApprovals] at hCollect
      cases hCollect
      simpa using hInv
  | cons proposal tail ih =>
      intro hWf hInv hCollect
      unfold collectSurfaceApprovals at hCollect
      cases hCert : surface_certify graph state proposal with
      | rejected reason =>
          simp [hCert] at hCollect
      | approved approval =>
          cases hRest : collectSurfaceApprovals graph approval.successor tail with
          | error err =>
              simp [hCert, hRest] at hCollect
          | ok next =>
              rcases
                SurfaceKernelSoundnessTheorem
                  graph
                  state
                  proposal
                  approval
                  hWf
                  hInv
                  hCert with
                ⟨_, hNextInv⟩
              have hTailInv : SurfaceInv graph next.2 :=
                ih (state := approval.successor) (result := next) hWf hNextInv hRest
              simp [hCert, hRest] at hCollect
              cases hCollect
              simpa using hTailInv

theorem collectAirApprovals_preserves_inv :
    ∀ {graph : AirGraph}
      {state : AirState}
      {proposals : List AirProposal}
      {result : List AirApproval × AirState},
      AirWellFormed graph →
      AirInv graph state →
      collectAirApprovals graph state proposals = .ok result →
        AirInv graph result.2 := by
  intro graph state proposals result
  induction proposals generalizing state result with
  | nil =>
      intro hWf hInv hCollect
      simp [collectAirApprovals] at hCollect
      cases hCollect
      simpa using hInv
  | cons proposal tail ih =>
      intro hWf hInv hCollect
      unfold collectAirApprovals at hCollect
      cases hCert : air_certify graph state proposal with
      | rejected reason =>
          simp [hCert] at hCollect
      | approved approval =>
          cases hRest : collectAirApprovals graph approval.successor tail with
          | error err =>
              simp [hCert, hRest] at hCollect
          | ok next =>
              rcases
                AirKernelSoundnessTheorem
                  graph
                  state
                  proposal
                  approval
                  hWf
                  hInv
                  hCert with
                ⟨_, hNextInv⟩
              have hTailInv : AirInv graph next.2 :=
                ih (state := approval.successor) (result := next) hWf hNextInv hRest
              simp [hCert, hRest] at hCollect
              cases hCollect
              simpa using hTailInv

theorem issue_command_issued_preserves_nominalAssumptions
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {proposal : CommandProposal}
    {issued : IssuedRecord}
    (hNominal : NominalAssumptions env state)
    (hIssued : issue_command env state proposal = .issued issued) :
    NominalAssumptions env issued.newState := by
  unfold NominalAssumptions at hNominal ⊢
  unfold issue_command at hIssued
  cases hPath : executeCertifiedPath env state proposal with
  | error reasons =>
      simp [hPath] at hIssued
  | ok certified =>
      simp [hPath] at hIssued
      cases hIssued
      simpa using hNominal

theorem issue_command_issued_preserves_InterfaceInv
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {proposal : CommandProposal}
    {issued : IssuedRecord}
    (hInv : InterfaceInv state)
    (hIssued : issue_command env state proposal = .issued issued) :
    InterfaceInv issued.newState := by
  unfold issue_command at hIssued
  cases hPath : executeCertifiedPath env state proposal with
  | error reasons =>
      simp [hPath] at hIssued
  | ok certified =>
      simp [hPath] at hIssued
      cases hIssued
      simp [InterfaceInv] at hInv ⊢
      simp [hInv]

theorem collectApprovalBundle_preserves_componentInvs
    {world : ScopedAviationWorld}
    {state : OrchestrationState}
    {plan : CertificationPlan}
    {collected : CollectedApprovals}
    (hWf : ScopedSafetyWorldWellFormed world)
    (hInv : ScopedOrchestrationInv world state)
    (hCollected :
      collectApprovalBundle
        (extractOrchestrationEnv world)
        state
        plan = .ok collected) :
    RunwayInv world.runwayKernel collected.runwaySuccessor ∧
      SurfaceInv world.surfaceGraph collected.surfaceSuccessor ∧
      AirInv world.airGraph collected.airSuccessor := by
  rcases hInv with ⟨_, hRunwayInv, hSurfaceInv, hAirInv, _⟩
  unfold collectApprovalBundle at hCollected
  cases hRunway :
      collectRunwayApprovals
        world.runwayKernel
        state.runway
        plan.runway with
  | error err =>
      simp [extractOrchestrationEnv, hRunway] at hCollected
  | ok runwayResult =>
      cases runwayResult with
      | mk runwayApprovals runwaySuccessor =>
          cases hSurface :
              collectSurfaceApprovals
                world.surfaceGraph
                state.surface
                plan.surface with
          | error err =>
              simp [extractOrchestrationEnv, hRunway, hSurface] at hCollected
          | ok surfaceResult =>
              cases surfaceResult with
              | mk surfaceApprovals surfaceSuccessor =>
                  cases hAir :
                      collectAirApprovals
                        world.airGraph
                        state.air
                        plan.air with
                  | error err =>
                      simp [extractOrchestrationEnv, hRunway, hSurface, hAir] at hCollected
                  | ok airResult =>
                      cases airResult with
                      | mk airApprovals airSuccessor =>
                          cases hSep : collectSeparationWitnesses plan.separation with
                          | error err =>
                              simp [extractOrchestrationEnv, hRunway, hSurface, hAir, hSep] at hCollected
                          | ok witnesses =>
                              simp [extractOrchestrationEnv, hRunway, hSurface, hAir, hSep] at hCollected
                              cases hCollected
                              constructor
                              · exact
                                  collectRunwayApprovals_preserves_inv
                                    hWf.runway
                                    hRunwayInv
                                    hRunway
                              · constructor
                                · exact
                                    collectSurfaceApprovals_preserves_inv
                                      hWf.surface
                                      hSurfaceInv
                                      hSurface
                                · exact
                                    collectAirApprovals_preserves_inv
                                      hWf.air
                                      hAirInv
                                      hAir

theorem issue_command_preserves_ScopedOrchestrationInv
    {world : ScopedAviationWorld}
    {state : OrchestrationState}
    {proposal : CommandProposal}
    {issued : IssuedRecord}
    (hWf : ScopedSafetyWorldWellFormed world)
    (hInv : ScopedOrchestrationInv world state)
    (hIssued :
      issue_command
        (extractOrchestrationEnv world)
        state
        proposal = .issued issued) :
    ScopedOrchestrationInv world issued.newState := by
  rcases hInv with ⟨hNominal0, hRunwayInv, hSurfaceInv, hAirInv, hInterface0⟩
  have hNominal :=
    issue_command_issued_preserves_nominalAssumptions
      (env := extractOrchestrationEnv world)
      (hNominal := hNominal0)
      hIssued
  have hInterface :=
    issue_command_issued_preserves_InterfaceInv
      (env := extractOrchestrationEnv world)
      (hInv := hInterface0)
      hIssued
  unfold issue_command at hIssued
  cases hPath : executeCertifiedPath (extractOrchestrationEnv world) state proposal with
  | error reasons =>
      simp [hPath] at hIssued
  | ok certified =>
      simp [hPath] at hIssued
      have hCollected :
          collectApprovalBundle
            (extractOrchestrationEnv world)
            state
            certified.plan = .ok certified.collected := by
        unfold executeCertifiedPath at hPath
        cases hPlan :
            instantiate_plan
              (extractOrchestrationEnv world)
              state
              proposal with
        | error err =>
            simp [hPlan] at hPath
        | ok plan =>
            cases hBundle :
                collectApprovalBundle
                  (extractOrchestrationEnv world)
                  state
                  plan with
            | error errs =>
                simp [hPlan, hBundle] at hPath
            | ok collected =>
                cases hCompat :
                    compatibility_check
                      { mode := state.mode
                        activeSet := state.activeSet
                        approvals := collected.approvals } with
                | compatible =>
                    simp [hPlan, hBundle, hCompat] at hPath
                    cases hPath
                    simpa using hBundle
                | incompatible reason =>
                    simp [hPlan, hBundle, hCompat] at hPath
      have hComponents :=
        collectApprovalBundle_preserves_componentInvs
          hWf
          ⟨hNominal0, hRunwayInv, hSurfaceInv, hAirInv, hInterface0⟩
          hCollected
      cases hIssued
      exact ⟨hNominal, hComponents.1, hComponents.2.1, hComponents.2.2, hInterface⟩

theorem separation_check_safe_wellFormed
    {scenario : SeparationScenario}
    {witness : SeparationWitness}
    (hCheck : separation_check scenario = .safe witness) :
    SeparationScenarioWellFormed scenario := by
  unfold separation_check at hCheck
  by_cases hFormed : SeparationScenarioWellFormed scenario
  · exact hFormed
  · simp [hFormed] at hCheck

theorem collectSeparationWitnesses_sound :
    ∀ {scenarios : List SeparationScenario}
      {witnesses : List SeparationWitness},
      collectSeparationWitnesses scenarios = .ok witnesses →
        SeparationWitnessesSound scenarios witnesses := by
  intro scenarios witnesses hCollect
  induction scenarios generalizing witnesses with
  | nil =>
      simp [collectSeparationWitnesses] at hCollect
      cases hCollect
      exact SeparationWitnessesSound.nil
  | cons scenario tail ih =>
      unfold collectSeparationWitnesses at hCollect
      cases hCheck : separation_check scenario with
      | unsafeResult violation =>
          simp [hCheck] at hCollect
      | safe witness =>
          cases hTail : collectSeparationWitnesses tail with
          | error err =>
              simp [hCheck, hTail] at hCollect
          | ok tailWitnesses =>
              simp [hCheck, hTail] at hCollect
              cases hCollect
              exact
                SeparationWitnessesSound.cons
                  (SeparationCheckerSoundnessTheorem
                    scenario
                    witness
                    (separation_check_safe_wellFormed hCheck)
                    hCheck)
                  (ih hTail)

theorem collectApprovalBundle_separationSound
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {plan : CertificationPlan}
    {collected : CollectedApprovals}
    (hCollected : collectApprovalBundle env state plan = .ok collected) :
    SeparationApprovalsSound plan collected.approvals := by
  unfold SeparationApprovalsSound
  unfold collectApprovalBundle at hCollected
  cases hRunway : collectRunwayApprovals env.runwayEnv state.runway plan.runway with
  | error err =>
      simp [hRunway] at hCollected
  | ok runwayResult =>
      cases hSurface : collectSurfaceApprovals env.surfaceGraph state.surface plan.surface with
      | error err =>
          simp [hRunway, hSurface] at hCollected
      | ok surfaceResult =>
          cases hAir : collectAirApprovals env.airGraph state.air plan.air with
          | error err =>
              simp [hRunway, hSurface, hAir] at hCollected
          | ok airResult =>
              cases hSep : collectSeparationWitnesses plan.separation with
              | error err =>
                  simp [hRunway, hSurface, hAir, hSep] at hCollected
              | ok witnesses =>
                  simp [hRunway, hSurface, hAir, hSep] at hCollected
                  cases hCollected
                  simpa using collectSeparationWitnesses_sound hSep

theorem approvalsSatisfyPlan_separationSound
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {plan : CertificationPlan}
    {approvals : ApprovalBundle}
    (hApprovals : ApprovalsSatisfyPlan env state plan approvals) :
    SeparationApprovalsSound plan approvals := by
  rcases hApprovals with ⟨collected, hCollected, hEq⟩
  cases hEq
  exact collectApprovalBundle_separationSound hCollected

theorem ScopedSeparationIssuanceSafetyTheorem :
    ∀ {world : ScopedAviationWorld}
      {state : OrchestrationState}
      {issuer : ControllerId}
      {instruction : AtcInstruction}
      {proposal : CommandProposal}
      {issued : IssuedRecord},
      scopedCommandProposal issuer instruction = some proposal →
      ScopedSeparationCertifiedInstruction instruction →
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
              approvals := approvals } = .compatible ∧
          (∀ peer,
            PotentiallyConflictingPeer
              (extractOrchestrationEnv world)
              state
              (instructionTarget instruction)
              peer →
              ∃ scenario witness,
                scenario ∈ plan.separation ∧
                witness ∈ approvals.separation ∧
                scenario.subjectBefore.aircraft = instructionTarget instruction ∧
                scenario.peer.aircraft = peer ∧
                SeparationWitnessSound scenario witness) := by
  intro world state issuer instruction proposal issued hProposal hSep hIssued
  rcases
    ScopedNonBypassTheorem
      (world := world)
      (state := state)
      (issuer := issuer)
      (instruction := instruction)
      (proposal := proposal)
      (issued := issued)
      hProposal
      hIssued with
    ⟨plan, approvals, hPlan, hApprovals, hCompat⟩
  have hApprovalSound := approvalsSatisfyPlan_separationSound hApprovals
  refine ⟨plan, approvals, hPlan, hApprovals, hCompat, ?_⟩
  intro peer hPeer
  rcases
    ScopedPeerCoverageSoundnessTheorem
      (world := world)
      (state := state)
      (issuer := issuer)
      (instruction := instruction)
      (proposal := proposal)
      (plan := plan)
      (peer := peer)
      hProposal
      hSep
      hPlan
      hPeer with
    ⟨scenario, hScenarioMem, hSubject, hPeerAircraft⟩
  rcases
    SeparationWitnessesSound.exists_witness_of_mem
      hApprovalSound
      hScenarioMem with
    ⟨witness, hWitnessMem, hWitnessSound⟩
  exact
    ⟨scenario, witness, hScenarioMem, hWitnessMem, hSubject, hPeerAircraft, hWitnessSound⟩

structure ScopedIssueStep
    (world : ScopedAviationWorld)
    (resolutionWorld : ResolutionWorld)
    (state : OrchestrationState)
    (existing : List ManagedResolvedClearance)
    (nextState : OrchestrationState)
    (nextResolved : List ManagedResolvedClearance) where
  clearance : StructuredClearance
  initialResolutionState : ResolutionState
  finalResolutionState : ResolutionState
  resolved : ResolvedClearance
  instruction : AtcInstruction
  proposal : CommandProposal
  issued : IssuedRecord
  safety : ScopedSafetyStructuredClearance clearance
  frontier : instruction ∈ structuredFrontierInstructions clearance
  certified : ScopedCertifiedInstruction instruction
  commandProposal :
    scopedCommandProposal clearance.issuedBy instruction = some proposal
  fresh : resolved.source.id ∉ resolvedClearanceIds existing
  resolves :
    ResolvesClearance
      resolutionWorld
      initialResolutionState
      clearance
      resolved
      finalResolutionState
  authority :
    ∀ grant,
      scopedInstructionRequiredAuthorityGrant? instruction = some grant →
        WorldControllerHasGrant world clearance.issuedBy grant
  issue :
    issue_command
      (extractOrchestrationEnv world)
      state
      proposal = .issued issued
  nextStateEq : nextState = issued.newState
  nextResolvedEq :
    nextResolved = (admitResolvedClearance existing resolved).clearances

theorem ScopedIssueStep_preservesResolvedReachability
    {world : ScopedAviationWorld}
    {resolutionWorld : ResolutionWorld}
    {state : OrchestrationState}
    {existing nextResolved : List ManagedResolvedClearance}
    {nextState : OrchestrationState}
    (hWf : ScopedSafetyWorldWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hStep :
      ScopedIssueStep world resolutionWorld state existing nextState nextResolved) :
    ReachableResolvedSet nextResolved := by
  rcases
    ScopedCertifiedIssuanceSoundnessTheorem
      (world := world)
      (state := state)
      (existing := existing)
      (resolutionWorld := resolutionWorld)
      (initialState := hStep.initialResolutionState)
      (finalState := hStep.finalResolutionState)
      (clearance := hStep.clearance)
      (resolved := hStep.resolved)
      (instruction := hStep.instruction)
      (proposal := hStep.proposal)
      (issued := hStep.issued)
      hWf.extraction
      hReach
      hStep.fresh
      hStep.resolves
      hStep.safety
      hStep.frontier
      hStep.certified
      hStep.commandProposal
      hStep.authority
      hStep.issue with
    ⟨_, _, _, _, _, _, _, hResolvedReach⟩
  simpa [hStep.nextResolvedEq] using hResolvedReach

theorem ScopedIssueStep_preservesOrchestrationInv
    {world : ScopedAviationWorld}
    {resolutionWorld : ResolutionWorld}
    {state : OrchestrationState}
    {existing nextResolved : List ManagedResolvedClearance}
    {nextState : OrchestrationState}
    (hWf : ScopedSafetyWorldWellFormed world)
    (hInv : ScopedOrchestrationInv world state)
    (hStep :
      ScopedIssueStep world resolutionWorld state existing nextState nextResolved) :
    ScopedOrchestrationInv world nextState := by
  simpa [hStep.nextStateEq] using
    issue_command_preserves_ScopedOrchestrationInv
      hWf
      hInv
      hStep.issue

inductive ReachableScopedIssuedState
    (world : ScopedAviationWorld)
    (resolutionWorld : ResolutionWorld) :
    OrchestrationState → List ManagedResolvedClearance → Prop
  | base
      {state : OrchestrationState}
      {resolved : List ManagedResolvedClearance}
      (hInv : ScopedOrchestrationInv world state)
      (hResolved : ReachableResolvedSet resolved) :
      ReachableScopedIssuedState world resolutionWorld state resolved
  | step
      {state nextState : OrchestrationState}
      {existing nextResolved : List ManagedResolvedClearance}
      (hReach : ReachableScopedIssuedState world resolutionWorld state existing)
      (hStep :
        ScopedIssueStep world resolutionWorld state existing nextState nextResolved) :
      ReachableScopedIssuedState world resolutionWorld nextState nextResolved

theorem ReachableScopedIssuedState.safety
    {world : ScopedAviationWorld}
    {resolutionWorld : ResolutionWorld}
    {state : OrchestrationState}
    {resolved : List ManagedResolvedClearance}
    (hWf : ScopedSafetyWorldWellFormed world)
    (hReach : ReachableScopedIssuedState world resolutionWorld state resolved) :
    ScopedOrchestrationInv world state ∧
      ReachableResolvedSet resolved := by
  induction hReach with
  | base hInv hResolved =>
      exact ⟨hInv, hResolved⟩
  | step hPrev hStep ih =>
      exact
        ⟨ScopedIssueStep_preservesOrchestrationInv hWf ih.1 hStep,
          ScopedIssueStep_preservesResolvedReachability hWf ih.2 hStep⟩

theorem ScopedReachableSafetyTheorem
    {world : ScopedAviationWorld}
    {resolutionWorld : ResolutionWorld}
    {state : OrchestrationState}
    {resolved : List ManagedResolvedClearance}
    (hWf : ScopedSafetyWorldWellFormed world)
    (hReach : ReachableScopedIssuedState world resolutionWorld state resolved) :
    ScopedOrchestrationInv world state ∧
      ReachableResolvedSet resolved :=
  ReachableScopedIssuedState.safety hWf hReach

theorem ScopedIssueStepSeparationSoundTheorem
    {world : ScopedAviationWorld}
    {resolutionWorld : ResolutionWorld}
    {state nextState : OrchestrationState}
    {existing nextResolved : List ManagedResolvedClearance}
    (hStep :
      ScopedIssueStep world resolutionWorld state existing nextState nextResolved)
    (hSep : ScopedSeparationCertifiedInstruction hStep.instruction) :
    ∃ plan approvals,
      instantiate_plan
        (extractOrchestrationEnv world)
        state
        hStep.proposal = Except.ok plan ∧
      ApprovalsSatisfyPlan
        (extractOrchestrationEnv world)
        state
        plan
        approvals ∧
      compatibility_check
        { mode := state.mode
          activeSet := state.activeSet
          approvals := approvals } = .compatible ∧
      (∀ peer,
        PotentiallyConflictingPeer
          (extractOrchestrationEnv world)
          state
          (instructionTarget hStep.instruction)
          peer →
          ∃ scenario witness,
            scenario ∈ plan.separation ∧
            witness ∈ approvals.separation ∧
            scenario.subjectBefore.aircraft = instructionTarget hStep.instruction ∧
            scenario.peer.aircraft = peer ∧
            SeparationWitnessSound scenario witness) := by
  exact
    ScopedSeparationIssuanceSafetyTheorem
      (world := world)
      (state := state)
      (issuer := hStep.clearance.issuedBy)
      (instruction := hStep.instruction)
      (proposal := hStep.proposal)
      (issued := hStep.issued)
      hStep.commandProposal
      hSep
      hStep.issue

end Greenfield
end CertifiedAtc
