import CertifiedAtc.Interfaces

namespace CertifiedAtc

/--
Exact airborne separation scope for `Safety-complete (N₀)`.

This is intentionally narrower than the full command catalog. It packages the
commands that the completion plan currently treats as part of the shortest-path
separation story.
-/
def ScopedSafetySeparationCommand : Command → Prop
  | .clearedForTakeoff _ _ => True
  | .clearedToLand _ _ => True
  | .clearedTouchAndGo _ _ => True
  | .goAround _ => True
  | .reduceSpeedTo _ _ => True
  | _ => False

/--
Exact non-certified airborne neutrality surface for `Safety-complete (N₀)`.
-/
def ScopedSafetyNeutralCommand : Command → Prop
  | .reportDownwind _ => True
  | .reportFinal _ => True
  | .proceed _ => True
  | .contactFrequency _ _ _ => True
  | .monitorFrequency _ _ _ => True
  | .squawkCode _ _ => True
  | _ => False

theorem scopedSafetySeparationCommand_hasSeparationPlan
    {command : Command} :
    ScopedSafetySeparationCommand command →
      (compile_command (classOf command)).separation = true := by
  intro hScoped
  cases command with
  | clearedForTakeoff target runway =>
      simp [compile_command, classOf, profile]
  | clearedToLand target runway =>
      simp [compile_command, classOf, profile]
  | clearedTouchAndGo target runway =>
      simp [compile_command, classOf, profile]
  | goAround target =>
      simp [compile_command, classOf, profile]
  | reduceSpeedTo target maxSpeed =>
      simp [compile_command, classOf, profile]
  | climbTo target altitude =>
      cases hScoped
  | descendTo target altitude =>
      cases hScoped
  | _ =>
      cases hScoped

theorem scopedSafetyNeutralCommand_concreteNeutral
    {command : Command} :
    ScopedSafetyNeutralCommand command →
      ConcreteNeutralAirborneCommand command := by
  intro hScoped
  cases command with
  | reportDownwind target =>
      simp [ConcreteNeutralAirborneCommand]
  | reportFinal target =>
      simp [ConcreteNeutralAirborneCommand]
  | proceed target =>
      simp [ConcreteNeutralAirborneCommand]
  | contactFrequency target controller frequency =>
      simp [ConcreteNeutralAirborneCommand]
  | monitorFrequency target controller frequency =>
      simp [ConcreteNeutralAirborneCommand]
  | squawkCode target code =>
      simp [ConcreteNeutralAirborneCommand]
  | _ =>
      cases hScoped

theorem scopedSafetyNeutralCommand_nonSeparationRelevant
    {command : Command} :
    ScopedSafetyNeutralCommand command →
      NonSeparationRelevantCommand command := by
  intro hScoped
  exact
    concreteNeutralAirborneCommand_nonSeparationRelevant
      (scopedSafetyNeutralCommand_concreteNeutral hScoped)

theorem scopedSafetySeparationCommand_relevant
    {command : Command} :
    ScopedSafetySeparationCommand command →
      SeparationRelevantCommand command := by
  intro hScoped
  have hPlan : (compile_command (classOf command)).separation = true :=
    scopedSafetySeparationCommand_hasSeparationPlan hScoped
  simpa [SeparationRelevantCommand, commandPlan, commandProfile, compile_command] using hPlan

inductive ScopedSeparationBoundaryCase
    (command : Command)
    (baselineSubject : SeparationEntityState)
    (scenario : SeparationScenario) : Prop
  | certifiedRelevant (witness : SeparationWitness) :
      ScopedSafetySeparationCommand command →
      SeparationScenarioWellFormed scenario →
      separation_check scenario = .safe witness →
        ScopedSeparationBoundaryCase command baselineSubject scenario
  | neutralIrrelevant :
      ScopedSafetyNeutralCommand command →
      scenario.subjectBefore = baselineSubject →
      SeparationNeutralTransition baselineSubject scenario.subjectAfter scenario.peer →
        ScopedSeparationBoundaryCase command baselineSubject scenario

theorem ScopedSeparationBoundaryCase.toBoundaryCase
    {command : Command}
    {baselineSubject : SeparationEntityState}
    {scenario : SeparationScenario} :
    ScopedSeparationBoundaryCase command baselineSubject scenario →
      SeparationBoundaryCase command baselineSubject scenario := by
  intro hCase
  cases hCase with
  | certifiedRelevant witness hScoped hFormed hCheck =>
      exact
        SeparationBoundaryCase.certifiedRelevant
          witness
          (scopedSafetySeparationCommand_relevant hScoped)
          hFormed
          hCheck
  | neutralIrrelevant hScoped hBefore hNeutral =>
      exact
        SeparationBoundaryCase.neutralIrrelevant
          (scopedSafetyNeutralCommand_nonSeparationRelevant hScoped)
          hBefore
          hNeutral

theorem ScopedSeparationBoundarySufficiencyTheorem
    {command : Command}
    {baselineSubject : SeparationEntityState}
    {scenario : SeparationScenario}
    (hBaseline :
      PairwiseSeparated
        { subjectBefore := baselineSubject
          subjectAfter := baselineSubject
          peer := scenario.peer
          rule := scenario.rule
          horizonSeconds := scenario.horizonSeconds })
    (hCase : ScopedSeparationBoundaryCase command baselineSubject scenario) :
    PairwiseSeparated scenario := by
  exact
    SeparationBoundarySufficiencyTheorem
      hBaseline
      (ScopedSeparationBoundaryCase.toBoundaryCase hCase)

theorem ScopedSeparationNeutralityTheorem
    {command : Command}
    {subject : SeparationEntityState}
    {peer : SeparationEntityState}
    {rule : SeparationRule}
    (hScoped : ScopedSafetyNeutralCommand command)
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
    concreteNeutralAirborneCommand_preserves_pairwise
      (scopedSafetyNeutralCommand_concreteNeutral hScoped)
      hBaseline

theorem ScopedSeparationCoverageTheorem :
    ∀ env state proposal plan peer,
      instantiate_plan env state proposal = Except.ok plan →
      ScopedSafetySeparationCommand proposal.command →
      PotentiallyConflictingPeer env state (commandTarget proposal.command) peer →
        ScenarioCoversPeer plan (commandTarget proposal.command) peer := by
  intro env state proposal plan peer hPlan hScoped hPeer
  exact
    SeparationCoverageTheorem
      env
      state
      proposal
      plan
      peer
      hPlan
      (scopedSafetySeparationCommand_hasSeparationPlan hScoped)
      hPeer

theorem collectAirApprovals_singleton_ok
    {graph : AirGraph}
    {state : AirState}
    {proposal : AirProposal}
    {approvals : List AirApproval}
    {successor : AirState}
    (hCollect :
      collectAirApprovals graph state [proposal] = .ok (approvals, successor)) :
    ∃ approval,
      approvals = [approval] ∧
      successor = approval.successor ∧
      air_certify graph state proposal = .approved approval := by
  unfold collectAirApprovals at hCollect
  cases hCert : air_certify graph state proposal with
  | rejected reason =>
      simp [hCert] at hCollect
  | approved approval =>
      simp [hCert, collectAirApprovals] at hCollect
      cases hCollect
      rename_i hApprovalsEq hSuccessorEq
      refine ⟨approval, ?_, ?_, ?_⟩
      · exact hApprovalsEq.symm
      · exact hSuccessorEq.symm
      · rfl

theorem collectApprovalBundle_singletonAir_ok
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {plan : CertificationPlan}
    {collected : CollectedApprovals}
    {proposal : AirProposal}
    (hPlanAir : plan.air = [proposal])
    (hCollected : collectApprovalBundle env state plan = .ok collected) :
    ∃ approval,
      air_certify env.airGraph state.air proposal = .approved approval ∧
      collected.airSuccessor = approval.successor := by
  unfold collectApprovalBundle at hCollected
  cases hRunway :
      collectRunwayApprovals env.runwayEnv state.runway plan.runway with
  | error err =>
      simp [hRunway] at hCollected
  | ok runwayResult =>
      cases hSurface :
          collectSurfaceApprovals env.surfaceGraph state.surface plan.surface with
      | error err =>
          simp [hRunway, hSurface] at hCollected
      | ok surfaceResult =>
          rw [hPlanAir] at hCollected
          cases hAir :
              collectAirApprovals env.airGraph state.air [proposal] with
          | error err =>
              simp [hRunway, hSurface, hAir] at hCollected
          | ok airResult =>
              rcases
                collectAirApprovals_singleton_ok
                  (graph := env.airGraph)
                  (state := state.air)
                  (proposal := proposal)
                  (approvals := airResult.1)
                  (successor := airResult.2)
                  hAir with
                ⟨approval, hApprovals, hSuccessor, hApproved⟩
              cases hSep : collectSeparationWitnesses plan.separation with
              | error err =>
                  simp [hRunway, hSurface, hAir, hSep] at hCollected
              | ok witnesses =>
                  simp [hRunway, hSurface, hAir, hSep] at hCollected
                  cases hCollected
                  exact ⟨approval, hApproved, by simpa using hSuccessor⟩

theorem collectSeparationWitnesses_member_wellFormed
    {scenarios : List SeparationScenario}
    {witnesses : List SeparationWitness}
    {scenario : SeparationScenario}
    (hCollect : collectSeparationWitnesses scenarios = .ok witnesses)
    (hMember : scenario ∈ scenarios) :
    SeparationScenarioWellFormed scenario := by
  induction scenarios generalizing witnesses scenario with
  | nil =>
      cases hMember
  | cons head tail ih =>
      unfold collectSeparationWitnesses at hCollect
      cases hCheck : separation_check head with
      | unsafeResult violation =>
          simp [hCheck] at hCollect
      | safe witness =>
          cases hTail : collectSeparationWitnesses tail with
          | error err =>
              simp [hCheck, hTail] at hCollect
          | ok tailWitnesses =>
              simp [hCheck, hTail] at hCollect
              simp at hMember
              rcases hMember with rfl | hTailMember
              · exact separation_check_safe_wellFormed hCheck
              · exact ih hTail hTailMember

theorem collectApprovalBundle_separation_ok
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {plan : CertificationPlan}
    {collected : CollectedApprovals}
    (hCollected : collectApprovalBundle env state plan = .ok collected) :
    ∃ witnesses, collectSeparationWitnesses plan.separation = .ok witnesses := by
  unfold collectApprovalBundle at hCollected
  cases hRunway :
      collectRunwayApprovals env.runwayEnv state.runway plan.runway with
  | error err =>
      simp [hRunway] at hCollected
  | ok runwayResult =>
      cases hSurface :
          collectSurfaceApprovals env.surfaceGraph state.surface plan.surface with
      | error err =>
          simp [hRunway, hSurface] at hCollected
      | ok surfaceResult =>
          cases hAir :
              collectAirApprovals env.airGraph state.air plan.air with
          | error err =>
              simp [hRunway, hSurface, hAir] at hCollected
          | ok airResult =>
              cases hSep : collectSeparationWitnesses plan.separation with
              | error err =>
                  simp [hRunway, hSurface, hAir, hSep] at hCollected
              | ok witnesses =>
                  exact ⟨witnesses, rfl⟩

theorem buildPlanWithAirAndSeparation_mem_scenario
    {graph : AirGraph}
    {state : OrchestrationState}
    {command : Command}
    {runway : List RunwayProposal}
    {airProposal : AirProposal}
    {plan : CertificationPlan}
    {scenario : SeparationScenario}
    (hPlan :
      buildPlanWithAirAndSeparation graph state command runway airProposal = .ok plan)
    (hScenario : scenario ∈ plan.separation) :
    ∃ subjectAfter peerState,
      commandSubjectAfter
        graph.altitudeBands
        command
        (toSeparationEntityState graph airProposal.aircraft airProposal.state) = .ok subjectAfter ∧
      peerState ∈ selectSeparationPeers graph state.air airProposal.aircraft ∧
      scenario =
        mkCommandSeparationScenario
          command
          (toSeparationEntityState graph airProposal.aircraft airProposal.state)
          subjectAfter
          peerState := by
  rcases buildPlanWithAirAndSeparation_ok hPlan with ⟨subjectAfter, hAfter, hPlanEq⟩
  rw [hPlanEq] at hScenario
  rcases List.mem_map.mp hScenario with ⟨peerState, hPeer, hEq⟩
  exact ⟨subjectAfter, peerState, hAfter, hPeer, hEq.symm⟩

theorem clearedForTakeoff_issuedScenario_viableSep
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {proposer : AgentId}
    {rationale : String}
    {target : EntityId}
    {runway : RunwayId}
    {plan : CertificationPlan}
    {collected : CollectedApprovals}
    {scenario : SeparationScenario}
    {witness : SeparationWitness}
    (hAirWf : AirWellFormed env.airGraph)
    (hAirInv : AirInv env.airGraph state.air)
    (hPlan :
      instantiate_plan env state
        { proposer := proposer
          command := .clearedForTakeoff target runway
          rationale := rationale } = .ok plan)
    (hCollected : collectApprovalBundle env state plan = .ok collected)
    (hScenario : scenario ∈ plan.separation)
    (hWitness : SeparationWitnessSound scenario witness) :
    Viable_sep
      (approvedPairwiseContinuations
        env.airGraph
        collected.airSuccessor
        target
        scenario.peer
        scenario.rule) := by
  rcases collectApprovalBundle_separation_ok hCollected with ⟨witnesses, hSepOk⟩
  have hWell : SeparationScenarioWellFormed scenario :=
    collectSeparationWitnesses_member_wellFormed hSepOk hScenario
  have hPairwise : PairwiseSeparated scenario := hWitness.2.2
  cases hAir : lookupAirborneState state.air.aircraft target with
  | none =>
      simp [instantiate_plan, hAir] at hPlan
  | some airState =>
      simp [instantiate_plan, hAir] at hPlan
      have hBuild :
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            (.clearedForTakeoff target runway)
            [.acquire
              { runway := runway
                aircraft := target
                kind := .occupiedTakeoffRoll }]
            { aircraft := target
              state := airState
              act := .activatePath airState.edge } = .ok plan := hPlan
      rcases
        buildPlanWithAirAndSeparation_mem_scenario
          (hPlan := hBuild)
          hScenario with
        ⟨subjectAfter, peerState, hAfter, _hPeer, hScenarioEq⟩
      have hPlanAir :
          plan.air =
            [{ aircraft := target
               state := airState
               act := .activatePath airState.edge }] := by
        rcases buildPlanWithAirAndSeparation_ok hBuild with ⟨_, _, hPlanEq⟩
        rw [hPlanEq]
      rcases
        collectApprovalBundle_singletonAir_ok
          (proposal := { aircraft := target, state := airState, act := .activatePath airState.edge })
          hPlanAir
          hCollected with
        ⟨approval, hApproved, hAirSuccessorEq⟩
      rcases
        AirKernelSoundnessTheorem
          env.airGraph
          state.air
          { aircraft := target, state := airState, act := .activatePath airState.edge }
          approval
          hAirWf
          hAirInv
          hApproved with
        ⟨hAirSound, _hSuccInv⟩
      rcases hAirSound with
        ⟨_hKernel, _hSubject, _hTick, _hLocal, _hEffect, hAirSuccessorFormula⟩
      have hViableApproval :
          Viable_sep
            (approvedPairwiseContinuations
              env.airGraph
              approval.successor
              target
              scenario.peer
              scenario.rule) := by
        exact
          viable_sep_of_capableApproval_equivIssuedScenario
            hAirWf
            hAirInv
            hApproved
            ContinueCurrentPathCapableAct.activatePath
            hWell
            hPairwise
            (by
              intro successorAirState hLookupSucc
              have hLookupFormula :
                  lookupAirborneState approval.successor.aircraft target =
                    some (activatePathSuccessorState airState airState.edge) := by
                rw [hAirSuccessorFormula]
                simp [applyAirProposal, lookupAirborneState, activatePathSuccessorState]
              have hSuccessorEq :
                  successorAirState = activatePathSuccessorState airState airState.edge := by
                rw [hLookupFormula] at hLookupSucc
                injection hLookupSucc with hEq
                exact hEq.symm
              have hAfterEq :
                  subjectAfter =
                    { toSeparationEntityState env.airGraph target airState with
                        phaseTag :=
                          commandPhaseTag
                            (.clearedForTakeoff target runway)
                            (toSeparationEntityState env.airGraph target airState).phaseTag } := by
                simpa [commandSubjectAfter] using hAfter.symm
              subst hSuccessorEq
              have hOperationalBase :
                  SeparationEntityOperationalEq
                    (toSeparationEntityState env.airGraph target
                      (activatePathSuccessorState airState airState.edge))
                    (toSeparationEntityState env.airGraph target airState) :=
                operationalEq_activatePathSuccessor_sameEdge
              have hOperationalAfter :
                  SeparationEntityOperationalEq
                    (toSeparationEntityState env.airGraph target
                      (activatePathSuccessorState airState airState.edge))
                    { toSeparationEntityState env.airGraph target airState with
                        phaseTag :=
                          commandPhaseTag
                            (.clearedForTakeoff target runway)
                            (toSeparationEntityState env.airGraph target airState).phaseTag } :=
                separationEntityOperationalEq_phaseTag_update_right hOperationalBase
              rw [hScenarioEq, hAfterEq]
              simpa [mkCommandSeparationScenario] using hOperationalAfter)
      simpa [hAirSuccessorEq] using hViableApproval

theorem clearedToLand_issuedScenario_viableSep
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {proposer : AgentId}
    {rationale : String}
    {target : EntityId}
    {runway : RunwayId}
    {plan : CertificationPlan}
    {collected : CollectedApprovals}
    {scenario : SeparationScenario}
    {witness : SeparationWitness}
    (hAirWf : AirWellFormed env.airGraph)
    (hAirInv : AirInv env.airGraph state.air)
    (hPlan :
      instantiate_plan env state
        { proposer := proposer
          command := .clearedToLand target runway
          rationale := rationale } = .ok plan)
    (hCollected : collectApprovalBundle env state plan = .ok collected)
    (hScenario : scenario ∈ plan.separation)
    (hWitness : SeparationWitnessSound scenario witness) :
    Viable_sep
      (approvedPairwiseContinuations
        env.airGraph
        collected.airSuccessor
        target
        scenario.peer
        scenario.rule) := by
  rcases collectApprovalBundle_separation_ok hCollected with ⟨witnesses, hSepOk⟩
  have hWell : SeparationScenarioWellFormed scenario :=
    collectSeparationWitnesses_member_wellFormed hSepOk hScenario
  have hPairwise : PairwiseSeparated scenario := hWitness.2.2
  cases hAir : lookupAirborneState state.air.aircraft target with
  | none =>
      simp [instantiate_plan, hAir] at hPlan
  | some airState =>
      simp [instantiate_plan, hAir] at hPlan
      have hBuild :
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            (.clearedToLand target runway)
            [.acquire
              { runway := runway
                aircraft := target
                kind := .reservedForLanding }]
            { aircraft := target
              state := airState
              act := .continueOnEdge } = .ok plan := hPlan
      rcases
        buildPlanWithAirAndSeparation_mem_scenario
          (hPlan := hBuild)
          hScenario with
        ⟨subjectAfter, peerState, hAfter, _hPeer, hScenarioEq⟩
      have hPlanAir :
          plan.air = [{ aircraft := target, state := airState, act := .continueOnEdge }] := by
        rcases buildPlanWithAirAndSeparation_ok hBuild with ⟨_, _, hPlanEq⟩
        rw [hPlanEq]
      rcases
        collectApprovalBundle_singletonAir_ok
          (proposal := { aircraft := target, state := airState, act := .continueOnEdge })
          hPlanAir
          hCollected with
        ⟨approval, hApproved, hAirSuccessorEq⟩
      rcases
        AirKernelSoundnessTheorem
          env.airGraph
          state.air
          { aircraft := target, state := airState, act := .continueOnEdge }
          approval
          hAirWf
          hAirInv
          hApproved with
        ⟨hAirSound, _hSuccInv⟩
      rcases hAirSound with
        ⟨_hKernel, _hSubject, _hTick, _hLocal, _hEffect, hAirSuccessorFormula⟩
      have hViableApproval :
          Viable_sep
            (approvedPairwiseContinuations
              env.airGraph
              approval.successor
              target
              scenario.peer
              scenario.rule) := by
        exact
          viable_sep_of_capableApproval_equivIssuedScenario
            hAirWf
            hAirInv
            hApproved
            ContinueCurrentPathCapableAct.continueOnEdge
            hWell
            hPairwise
            (by
              intro successorAirState hLookupSucc
              have hLookupFormula :
                  lookupAirborneState approval.successor.aircraft target = some airState := by
                rw [hAirSuccessorFormula]
                simp [applyAirProposal, hAir]
              have hSuccessorEq : successorAirState = airState := by
                rw [hLookupFormula] at hLookupSucc
                injection hLookupSucc with hEq
                exact hEq.symm
              have hAfterEq :
                  subjectAfter =
                    { toSeparationEntityState env.airGraph target airState with
                        phaseTag :=
                          commandPhaseTag
                            (.clearedToLand target runway)
                            (toSeparationEntityState env.airGraph target airState).phaseTag } := by
                simpa [commandSubjectAfter] using hAfter.symm
              subst successorAirState
              have hOperationalBase :
                  SeparationEntityOperationalEq
                    (toSeparationEntityState env.airGraph target airState)
                    (toSeparationEntityState env.airGraph target airState) :=
                separationEntityOperationalEq_refl
              have hOperationalAfter :
                  SeparationEntityOperationalEq
                    (toSeparationEntityState env.airGraph target airState)
                    { toSeparationEntityState env.airGraph target airState with
                        phaseTag :=
                          commandPhaseTag
                            (.clearedToLand target runway)
                            (toSeparationEntityState env.airGraph target airState).phaseTag } :=
                separationEntityOperationalEq_phaseTag_update_right hOperationalBase
              rw [hScenarioEq, hAfterEq]
              simpa [mkCommandSeparationScenario] using hOperationalAfter)
      simpa [hAirSuccessorEq] using hViableApproval

theorem clearedTouchAndGo_issuedScenario_viableSep
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {proposer : AgentId}
    {rationale : String}
    {target : EntityId}
    {runway : RunwayId}
    {plan : CertificationPlan}
    {collected : CollectedApprovals}
    {scenario : SeparationScenario}
    {witness : SeparationWitness}
    (hAirWf : AirWellFormed env.airGraph)
    (hAirInv : AirInv env.airGraph state.air)
    (hPlan :
      instantiate_plan env state
        { proposer := proposer
          command := .clearedTouchAndGo target runway
          rationale := rationale } = .ok plan)
    (hCollected : collectApprovalBundle env state plan = .ok collected)
    (hScenario : scenario ∈ plan.separation)
    (hWitness : SeparationWitnessSound scenario witness) :
    Viable_sep
      (approvedPairwiseContinuations
        env.airGraph
        collected.airSuccessor
        target
        scenario.peer
        scenario.rule) := by
  rcases collectApprovalBundle_separation_ok hCollected with ⟨witnesses, hSepOk⟩
  have hWell : SeparationScenarioWellFormed scenario :=
    collectSeparationWitnesses_member_wellFormed hSepOk hScenario
  have hPairwise : PairwiseSeparated scenario := hWitness.2.2
  cases hAir : lookupAirborneState state.air.aircraft target with
  | none =>
      simp [instantiate_plan, hAir] at hPlan
  | some airState =>
      simp [instantiate_plan, hAir] at hPlan
      have hBuild :
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            (.clearedTouchAndGo target runway)
            [.acquire
              { runway := runway
                aircraft := target
                kind := .reservedForLanding }]
            { aircraft := target
              state := airState
              act := .continueOnEdge } = .ok plan := hPlan
      rcases
        buildPlanWithAirAndSeparation_mem_scenario
          (hPlan := hBuild)
          hScenario with
        ⟨subjectAfter, peerState, hAfter, _hPeer, hScenarioEq⟩
      have hPlanAir :
          plan.air = [{ aircraft := target, state := airState, act := .continueOnEdge }] := by
        rcases buildPlanWithAirAndSeparation_ok hBuild with ⟨_, _, hPlanEq⟩
        rw [hPlanEq]
      rcases
        collectApprovalBundle_singletonAir_ok
          (proposal := { aircraft := target, state := airState, act := .continueOnEdge })
          hPlanAir
          hCollected with
        ⟨approval, hApproved, hAirSuccessorEq⟩
      rcases
        AirKernelSoundnessTheorem
          env.airGraph
          state.air
          { aircraft := target, state := airState, act := .continueOnEdge }
          approval
          hAirWf
          hAirInv
          hApproved with
        ⟨hAirSound, _hSuccInv⟩
      rcases hAirSound with
        ⟨_hKernel, _hSubject, _hTick, _hLocal, _hEffect, hAirSuccessorFormula⟩
      have hViableApproval :
          Viable_sep
            (approvedPairwiseContinuations
              env.airGraph
              approval.successor
              target
              scenario.peer
              scenario.rule) := by
        exact
          viable_sep_of_capableApproval_equivIssuedScenario
            hAirWf
            hAirInv
            hApproved
            ContinueCurrentPathCapableAct.continueOnEdge
            hWell
            hPairwise
            (by
              intro successorAirState hLookupSucc
              have hLookupFormula :
                  lookupAirborneState approval.successor.aircraft target = some airState := by
                rw [hAirSuccessorFormula]
                simp [applyAirProposal, hAir]
              have hSuccessorEq : successorAirState = airState := by
                rw [hLookupFormula] at hLookupSucc
                injection hLookupSucc with hEq
                exact hEq.symm
              have hAfterEq :
                  subjectAfter =
                    { toSeparationEntityState env.airGraph target airState with
                        phaseTag :=
                          commandPhaseTag
                            (.clearedTouchAndGo target runway)
                            (toSeparationEntityState env.airGraph target airState).phaseTag } := by
                simpa [commandSubjectAfter] using hAfter.symm
              subst successorAirState
              have hOperationalBase :
                  SeparationEntityOperationalEq
                    (toSeparationEntityState env.airGraph target airState)
                    (toSeparationEntityState env.airGraph target airState) :=
                separationEntityOperationalEq_refl
              have hOperationalAfter :
                  SeparationEntityOperationalEq
                    (toSeparationEntityState env.airGraph target airState)
                    { toSeparationEntityState env.airGraph target airState with
                        phaseTag :=
                          commandPhaseTag
                            (.clearedTouchAndGo target runway)
                            (toSeparationEntityState env.airGraph target airState).phaseTag } :=
                separationEntityOperationalEq_phaseTag_update_right hOperationalBase
              rw [hScenarioEq, hAfterEq]
              simpa [mkCommandSeparationScenario] using hOperationalAfter)
      simpa [hAirSuccessorEq] using hViableApproval

theorem goAround_issuedScenario_viableSep
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {proposer : AgentId}
    {rationale : String}
    {target : EntityId}
    {plan : CertificationPlan}
    {collected : CollectedApprovals}
    {scenario : SeparationScenario}
    {witness : SeparationWitness}
    (hAirWf : AirWellFormed env.airGraph)
    (hAirInv : AirInv env.airGraph state.air)
    (hPlan :
      instantiate_plan env state
        { proposer := proposer
          command := .goAround target
          rationale := rationale } = .ok plan)
    (hCollected : collectApprovalBundle env state plan = .ok collected)
    (hScenario : scenario ∈ plan.separation)
    (hWitness : SeparationWitnessSound scenario witness) :
    Viable_sep
      (approvedPairwiseContinuations
        env.airGraph
        collected.airSuccessor
        target
        scenario.peer
        scenario.rule) := by
  rcases collectApprovalBundle_separation_ok hCollected with ⟨witnesses, hSepOk⟩
  have hWell : SeparationScenarioWellFormed scenario :=
    collectSeparationWitnesses_member_wellFormed hSepOk hScenario
  have hPairwise : PairwiseSeparated scenario := hWitness.2.2
  cases hAir : lookupAirborneState state.air.aircraft target with
  | none =>
      simp [instantiate_plan, hAir] at hPlan
  | some airState =>
      cases hRunway : lookupRunwayCommitment state.runway.commitments target with
      | none =>
          simp [instantiate_plan, hAir, hRunway] at hPlan
      | some commitment =>
          simp [instantiate_plan, hAir, hRunway] at hPlan
          have hBuild :
              buildPlanWithAirAndSeparation
                env.airGraph
                state
                (.goAround target)
                [.release commitment]
                { aircraft := target
                  state := airState
                  act := .activateMissedApproach airState.edge } = .ok plan := hPlan
          rcases
            buildPlanWithAirAndSeparation_mem_scenario
              (hPlan := hBuild)
              hScenario with
            ⟨subjectAfter, peerState, hAfter, _hPeer, hScenarioEq⟩
          have hPlanAir :
              plan.air =
                [{ aircraft := target
                   state := airState
                   act := .activateMissedApproach airState.edge }] := by
            rcases buildPlanWithAirAndSeparation_ok hBuild with ⟨_, _, hPlanEq⟩
            rw [hPlanEq]
          rcases
            collectApprovalBundle_singletonAir_ok
              (proposal := { aircraft := target, state := airState, act := .activateMissedApproach airState.edge })
              hPlanAir
              hCollected with
            ⟨approval, hApproved, hAirSuccessorEq⟩
          rcases
            AirKernelSoundnessTheorem
              env.airGraph
              state.air
              { aircraft := target, state := airState, act := .activateMissedApproach airState.edge }
              approval
              hAirWf
              hAirInv
              hApproved with
            ⟨hAirSound, _hSuccInv⟩
          rcases hAirSound with
            ⟨_hKernel, _hSubject, _hTick, _hLocal, _hEffect, hAirSuccessorFormula⟩
          have hViableApproval :
              Viable_sep
                (approvedPairwiseContinuations
                  env.airGraph
                  approval.successor
                  target
                  scenario.peer
                  scenario.rule) := by
            exact
              viable_sep_of_capableApproval_equivIssuedScenario
                hAirWf
                hAirInv
                hApproved
                ContinueCurrentPathCapableAct.activateMissedApproach
                hWell
                hPairwise
                (by
                  intro successorAirState hLookupSucc
                  have hLookupFormula :
                      lookupAirborneState approval.successor.aircraft target =
                        some (activateMissedApproachSuccessorState airState airState.edge) := by
                    rw [hAirSuccessorFormula]
                    simp [applyAirProposal, lookupAirborneState, activateMissedApproachSuccessorState]
                  have hSuccessorEq :
                      successorAirState = activateMissedApproachSuccessorState airState airState.edge := by
                    rw [hLookupFormula] at hLookupSucc
                    injection hLookupSucc with hEq
                    exact hEq.symm
                  have hAfterEq :
                      subjectAfter =
                        { toSeparationEntityState env.airGraph target airState with
                            phaseTag :=
                              commandPhaseTag
                                (.goAround target)
                                (toSeparationEntityState env.airGraph target airState).phaseTag } := by
                    simpa [commandSubjectAfter] using hAfter.symm
                  subst hSuccessorEq
                  have hOperationalBase :
                      SeparationEntityOperationalEq
                        (toSeparationEntityState env.airGraph target
                          (activateMissedApproachSuccessorState airState airState.edge))
                        (toSeparationEntityState env.airGraph target airState) :=
                    operationalEq_activateMissedApproachSuccessor_sameEdge
                  have hOperationalAfter :
                      SeparationEntityOperationalEq
                        (toSeparationEntityState env.airGraph target
                          (activateMissedApproachSuccessorState airState airState.edge))
                        { toSeparationEntityState env.airGraph target airState with
                            phaseTag :=
                              commandPhaseTag
                                (.goAround target)
                                (toSeparationEntityState env.airGraph target airState).phaseTag } :=
                    separationEntityOperationalEq_phaseTag_update_right hOperationalBase
                  rw [hScenarioEq, hAfterEq]
                  simpa [mkCommandSeparationScenario] using hOperationalAfter)
          simpa [hAirSuccessorEq] using hViableApproval

theorem reduceSpeedTo_issuedScenario_viableSep
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {proposer : AgentId}
    {rationale : String}
    {target : EntityId}
    {maxSpeedKt : Nat}
    {plan : CertificationPlan}
    {collected : CollectedApprovals}
    {scenario : SeparationScenario}
    {witness : SeparationWitness}
    (hAirWf : AirWellFormed env.airGraph)
    (hAirInv : AirInv env.airGraph state.air)
    (hPlan :
      instantiate_plan env state
        { proposer := proposer
          command := .reduceSpeedTo target maxSpeedKt
          rationale := rationale } = .ok plan)
    (hCollected : collectApprovalBundle env state plan = .ok collected)
    (hScenario : scenario ∈ plan.separation)
    (hWitness : SeparationWitnessSound scenario witness) :
    Viable_sep
      (approvedPairwiseContinuations
        env.airGraph
        collected.airSuccessor
        target
        scenario.peer
        scenario.rule) := by
  rcases collectApprovalBundle_separation_ok hCollected with ⟨witnesses, hSepOk⟩
  have hWell : SeparationScenarioWellFormed scenario :=
    collectSeparationWitnesses_member_wellFormed hSepOk hScenario
  have hPairwise : PairwiseSeparated scenario := hWitness.2.2
  cases hAir : lookupAirborneState state.air.aircraft target with
  | none =>
      simp [instantiate_plan, hAir] at hPlan
  | some airState =>
      simp [instantiate_plan, hAir] at hPlan
      have hBuild :
          buildPlanWithAirAndSeparation
            env.airGraph
            state
            (.reduceSpeedTo target maxSpeedKt)
            []
            { aircraft := target
              state := airState
              act := .reduceSpeedMax maxSpeedKt } = .ok plan := hPlan
      rcases
        buildPlanWithAirAndSeparation_mem_scenario
          (hPlan := hBuild)
          hScenario with
        ⟨subjectAfter, peerState, hAfter, _hPeer, hScenarioEq⟩
      have hPlanAir :
          plan.air =
            [{ aircraft := target
               state := airState
               act := .reduceSpeedMax maxSpeedKt }] := by
        rcases buildPlanWithAirAndSeparation_ok hBuild with ⟨_, _, hPlanEq⟩
        rw [hPlanEq]
      rcases
        collectApprovalBundle_singletonAir_ok
          (proposal := { aircraft := target, state := airState, act := .reduceSpeedMax maxSpeedKt })
          hPlanAir
          hCollected with
        ⟨approval, hApproved, hAirSuccessorEq⟩
      rcases
        AirKernelSoundnessTheorem
          env.airGraph
          state.air
          { aircraft := target, state := airState, act := .reduceSpeedMax maxSpeedKt }
          approval
          hAirWf
          hAirInv
          hApproved with
        ⟨hAirSound, _hSuccInv⟩
      rcases hAirSound with
        ⟨_hKernel, _hSubject, _hTick, _hLocal, _hEffect, hAirSuccessorFormula⟩
      have hViableApproval :
          Viable_sep
            (approvedPairwiseContinuations
              env.airGraph
              approval.successor
              target
              scenario.peer
              scenario.rule) := by
        exact
          viable_sep_of_capableApproval_equivIssuedScenario
            hAirWf
            hAirInv
            hApproved
            ContinueCurrentPathCapableAct.reduceSpeedMax
            hWell
            hPairwise
            (by
              intro successorAirState hLookupSucc
              have hLookupFormula :
                  lookupAirborneState approval.successor.aircraft target =
                    some (speedReducedState airState maxSpeedKt) := by
                rw [hAirSuccessorFormula]
                simp [applyAirProposal, lookupAirborneState, speedReducedState]
              have hSuccessorEq :
                  successorAirState = speedReducedState airState maxSpeedKt := by
                rw [hLookupFormula] at hLookupSucc
                injection hLookupSucc with hEq
                exact hEq.symm
              have hAfterEq :
                  subjectAfter =
                    { toSeparationEntityState env.airGraph target airState with
                        speedMaxKt := maxSpeedKt } := by
                simpa [commandSubjectAfter] using hAfter.symm
              subst hSuccessorEq
              have hOperational :
                  SeparationEntityOperationalEq
                    (toSeparationEntityState env.airGraph target
                      (speedReducedState airState maxSpeedKt))
                    { toSeparationEntityState env.airGraph target airState with
                        speedMaxKt := maxSpeedKt } :=
                operationalEq_speedReducedState
              rw [hScenarioEq, hAfterEq]
              simpa [mkCommandSeparationScenario] using hOperational)
      simpa [hAirSuccessorEq] using hViableApproval

theorem ScopedIssuedScenarioViableSepTheorem
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {proposal : CommandProposal}
    {plan : CertificationPlan}
    {collected : CollectedApprovals}
    {scenario : SeparationScenario}
    {witness : SeparationWitness}
    (hAirWf : AirWellFormed env.airGraph)
    (hAirInv : AirInv env.airGraph state.air)
    (hPlan : instantiate_plan env state proposal = .ok plan)
    (hScoped : ScopedSafetySeparationCommand proposal.command)
    (hCollected : collectApprovalBundle env state plan = .ok collected)
    (hScenario : scenario ∈ plan.separation)
    (hWitness : SeparationWitnessSound scenario witness) :
    Viable_sep
      (approvedPairwiseContinuations
        env.airGraph
        collected.airSuccessor
        (commandTarget proposal.command)
        scenario.peer
        scenario.rule) := by
  cases proposal with
  | mk proposer command rationale =>
      cases command with
      | clearedForTakeoff target runway =>
          simpa using
            clearedForTakeoff_issuedScenario_viableSep
              (proposer := proposer)
              (rationale := rationale)
              hAirWf
              hAirInv
              hPlan
              hCollected
              hScenario
              hWitness
      | clearedToLand target runway =>
          simpa using
            clearedToLand_issuedScenario_viableSep
              (proposer := proposer)
              (rationale := rationale)
              hAirWf
              hAirInv
              hPlan
              hCollected
              hScenario
              hWitness
      | clearedTouchAndGo target runway =>
          simpa using
            clearedTouchAndGo_issuedScenario_viableSep
              (proposer := proposer)
              (rationale := rationale)
              hAirWf
              hAirInv
              hPlan
              hCollected
              hScenario
              hWitness
      | goAround target =>
          simpa using
            goAround_issuedScenario_viableSep
              (proposer := proposer)
              (rationale := rationale)
              hAirWf
              hAirInv
              hPlan
              hCollected
              hScenario
              hWitness
      | reduceSpeedTo target maxSpeedKt =>
          simpa using
            reduceSpeedTo_issuedScenario_viableSep
              (proposer := proposer)
              (rationale := rationale)
              hAirWf
              hAirInv
              hPlan
              hCollected
              hScenario
              hWitness
      | climbTo target altitude =>
          cases hScoped
      | descendTo target altitude =>
          cases hScoped
      | _ =>
          cases hScoped

theorem ScopedViableSepContinueCurrentPathTheorem
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
    viable_sep_of_continueCurrentPathContinuation_baseline
      hContinue
      hFormed
      hBaseline

theorem ScopedViableSepHoldCurrentPathTheorem
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
    viable_sep_of_holdCurrentPathContinuation_baseline
      hHold
      hFormed
      hBaseline

theorem ScopedViableSepReduceSpeedTheorem
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
    viable_sep_of_reduceSpeedContinuation_baseline
      hReduce
      hFormed
      hBaseline

theorem ScopedViableSepReservedBranchChoiceTheorem
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
    viable_sep_of_reservedBranchChoiceContinuation
      hBranch
      hFormed
      hCheck

theorem ScopedViableSepRecoveryPathTheorem
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
    viable_sep_of_recoveryPathContinuation
      hRecovery
      hFormed
      hCheck

inductive ScopedApprovedContinuationCase
    (graph : AirGraph)
    (state : AirState)
    (aircraft : EntityId)
    (peer : SeparationEntityState)
    (rule : SeparationRule) : Prop
  | continueCurrentPath
      {continuation : SeparationContinuation} :
      continueCurrentPathContinuation graph state aircraft peer rule = some continuation →
      SeparationScenarioWellFormed continuation.scenario →
      PairwiseSeparated (separationBaselineScenario continuation.scenario) →
        ScopedApprovedContinuationCase graph state aircraft peer rule
  | holdCurrentPath
      {continuation : SeparationContinuation} :
      holdCurrentPathContinuation graph state aircraft peer rule = some continuation →
      SeparationScenarioWellFormed continuation.scenario →
      PairwiseSeparated (separationBaselineScenario continuation.scenario) →
        ScopedApprovedContinuationCase graph state aircraft peer rule
  | reduceSpeed
      {continuation : SeparationContinuation} :
      continuation ∈ reduceSpeedContinuations graph state aircraft peer rule →
      SeparationScenarioWellFormed continuation.scenario →
      PairwiseSeparated (separationBaselineScenario continuation.scenario) →
        ScopedApprovedContinuationCase graph state aircraft peer rule
  | reservedBranchChoice
      {continuation : SeparationContinuation}
      {witness : SeparationWitness} :
      continuation ∈ reservedBranchChoiceContinuations graph state aircraft peer rule →
      SeparationScenarioWellFormed continuation.scenario →
      separation_check continuation.scenario = .safe witness →
        ScopedApprovedContinuationCase graph state aircraft peer rule
  | recoveryPath
      {continuation : SeparationContinuation}
      {witness : SeparationWitness} :
      continuation ∈ recoveryPathContinuations graph state aircraft peer rule →
      SeparationScenarioWellFormed continuation.scenario →
      separation_check continuation.scenario = .safe witness →
        ScopedApprovedContinuationCase graph state aircraft peer rule

theorem ScopedViableSepTheorem
    {graph : AirGraph}
    {state : AirState}
    {aircraft : EntityId}
    {peer : SeparationEntityState}
    {rule : SeparationRule} :
    ScopedApprovedContinuationCase graph state aircraft peer rule →
      Viable_sep (approvedPairwiseContinuations graph state aircraft peer rule) := by
  intro hCase
  cases hCase with
  | continueCurrentPath hContinue hFormed hBaseline =>
      exact
        ScopedViableSepContinueCurrentPathTheorem
          hContinue
          hFormed
          hBaseline
  | holdCurrentPath hHold hFormed hBaseline =>
      exact
        ScopedViableSepHoldCurrentPathTheorem
          hHold
          hFormed
          hBaseline
  | reduceSpeed hReduce hFormed hBaseline =>
      exact
        ScopedViableSepReduceSpeedTheorem
          hReduce
          hFormed
          hBaseline
  | reservedBranchChoice hBranch hFormed hCheck =>
      exact
        ScopedViableSepReservedBranchChoiceTheorem
          hBranch
          hFormed
          hCheck
  | recoveryPath hRecovery hFormed hCheck =>
      exact
        ScopedViableSepRecoveryPathTheorem
          hRecovery
          hFormed
          hCheck

end CertifiedAtc
