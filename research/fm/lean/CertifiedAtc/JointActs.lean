import CertifiedAtc.Interfaces

namespace CertifiedAtc

/--
Second milestone theorem target for the split architecture.

This milestone is no longer "prove the whole airport instance". It is:

- fix the routing table for the first three joint acts
- prove that orchestration cannot issue them without runway, air, and separation
  approval plus compatibility acceptance

The local runway / air / separation soundness theorems remain separate.
-/
theorem JointActsMilestone2Theorem :
  ∀ env state proposal issued,
    milestone2JointCommand proposal.command →
    issue_command env state proposal = .issued issued →
      ∃ plan approvals,
        instantiate_plan env state proposal = Except.ok plan ∧
        PlanMatchesTemplate (compile_command (classOf proposal.command)) plan ∧
        ApprovalsSatisfyPlan env state plan approvals ∧
        compatibility_check
          { mode := state.mode
            activeSet := state.activeSet
            approvals := approvals } = .compatible ∧
        (compile_command (classOf proposal.command)).runway = true ∧
        (compile_command (classOf proposal.command)).air = true ∧
        (compile_command (classOf proposal.command)).separation = true ∧
        (compile_command (classOf proposal.command)).compatibility = true ∧
        (compile_command (classOf proposal.command)).joint = true := by
  intro env state proposal issued hJoint hIssued
  rcases NonBypassTheorem env state proposal issued hIssued with
    ⟨plan, approvals, hPlan, hBundle, hCompat⟩
  cases proposal with
  | mk proposer command rationale =>
      cases command with
      | clearedForTakeoff target runway =>
          exact ⟨plan, approvals, hPlan,
            PlanInstantiationTheorem env state
              { proposer := proposer, command := .clearedForTakeoff target runway, rationale := rationale }
              plan hPlan,
            hBundle,
            hCompat,
            by simp [classOf, compile_command, profile],
            by simp [classOf, compile_command, profile],
            by simp [classOf, compile_command, profile],
            by simp [classOf, compile_command, profile],
            by simp [classOf, compile_command, profile]⟩
      | clearedToLand target runway =>
          exact ⟨plan, approvals, hPlan,
            PlanInstantiationTheorem env state
              { proposer := proposer, command := .clearedToLand target runway, rationale := rationale }
              plan hPlan,
            hBundle,
            hCompat,
            by simp [classOf, compile_command, profile],
            by simp [classOf, compile_command, profile],
            by simp [classOf, compile_command, profile],
            by simp [classOf, compile_command, profile],
            by simp [classOf, compile_command, profile]⟩
      | goAround target =>
          exact ⟨plan, approvals, hPlan,
            PlanInstantiationTheorem env state
              { proposer := proposer, command := .goAround target, rationale := rationale }
              plan hPlan,
            hBundle,
            hCompat,
            by simp [classOf, compile_command, profile],
            by simp [classOf, compile_command, profile],
            by simp [classOf, compile_command, profile],
            by simp [classOf, compile_command, profile],
            by simp [classOf, compile_command, profile]⟩
      | _ =>
          cases hJoint

end CertifiedAtc
