import CertifiedAtc.Core

namespace CertifiedAtc

inductive RunwayCommitmentKind
  | clear
  | lineUpAndWait
  | reservedForLanding
  | occupiedTakeoffRoll
  | occupiedLandingRoll
  | protectedForCrossing
  | vacating
  deriving DecidableEq, Repr

structure RunwayCommitment where
  runway : RunwayId
  aircraft : EntityId
  kind : RunwayCommitmentKind
  deriving DecidableEq, Repr

structure RunwayKernelEnv where
  runways : List RunwayId
  incompatible : List (RunwayCommitmentKind × RunwayCommitmentKind)
  deriving DecidableEq, Repr

structure RunwayState where
  commitments : List RunwayCommitment
  deriving DecidableEq, Repr

inductive RunwayProposal
  | acquire (commitment : RunwayCommitment)
  | release (commitment : RunwayCommitment)
  deriving DecidableEq, Repr

structure RunwayEffect where
  added : List RunwayCommitment := []
  removed : List RunwayCommitment := []
  footprint : Footprint := {}
  deriving DecidableEq, Repr

structure RunwayApproval where
  certificate : KernelCertificate
  effect : RunwayEffect
  successor : RunwayState
  deriving DecidableEq, Repr

inductive RunwayRejectReason
  | unknownRunway (runway : RunwayId)
  | conflict (existing : RunwayCommitment) (proposed : RunwayCommitment)
  | malformedProposal (detail : String)
  deriving DecidableEq, Repr

inductive RunwayDecision
  | approved (value : RunwayApproval)
  | rejected (reason : RunwayRejectReason)
  deriving DecidableEq, Repr

abbrev RunwayCertifySig : Type := RunwayKernelEnv → RunwayState → RunwayProposal → RunwayDecision

def proposalCommitment : RunwayProposal → RunwayCommitment
  | .acquire commitment => commitment
  | .release commitment => commitment

def proposalRunway (proposal : RunwayProposal) : RunwayId :=
  (proposalCommitment proposal).runway

def proposalAircraft (proposal : RunwayProposal) : EntityId :=
  (proposalCommitment proposal).aircraft

def footprintOfCommitment (commitment : RunwayCommitment) : Footprint :=
  { runways := [commitment.runway]
    entities := [commitment.aircraft] }

def proposalEffect : RunwayProposal → RunwayEffect
  | .acquire commitment =>
      { added := [commitment]
        footprint := footprintOfCommitment commitment }
  | .release commitment =>
      { removed := [commitment]
        footprint := footprintOfCommitment commitment }

def removeCommitment (target : RunwayCommitment) : List RunwayCommitment → List RunwayCommitment
  | [] => []
  | head :: tail =>
      if head = target then
        removeCommitment target tail
      else
        head :: removeCommitment target tail

def applyRunwayProposal : RunwayState → RunwayProposal → RunwayState
  | state, .acquire commitment =>
      { commitments := commitment :: state.commitments }
  | state, .release commitment =>
      { commitments := removeCommitment commitment state.commitments }

def mkRunwayCertificate (state : RunwayState) (proposal : RunwayProposal) : KernelCertificate :=
  { id := s!"runway:{state.commitments.length}:{proposalAircraft proposal}:{proposalRunway proposal}"
    kernel := .runway
    subject := proposalAircraft proposal
    issuedAtTick := state.commitments.length
    assumptions := ["local-runway-only", "conflict-table-only"] }

def mkRunwayApproval (state : RunwayState) (proposal : RunwayProposal) : RunwayApproval :=
  { certificate := mkRunwayCertificate state proposal
    effect := proposalEffect proposal
    successor := applyRunwayProposal state proposal }

def KindsConflict (env : RunwayKernelEnv)
    (left right : RunwayCommitmentKind) : Prop :=
  (left, right) ∈ env.incompatible ∨ (right, left) ∈ env.incompatible

def RunwayConflict (env : RunwayKernelEnv)
    (existing proposed : RunwayCommitment) : Prop :=
  existing.runway = proposed.runway ∧
    (existing.aircraft = proposed.aircraft ∨
      KindsConflict env existing.kind proposed.kind)

instance (env : RunwayKernelEnv)
    (left right : RunwayCommitmentKind) :
    Decidable (KindsConflict env left right) := by
  unfold KindsConflict
  infer_instance

instance (env : RunwayKernelEnv)
    (existing proposed : RunwayCommitment) :
    Decidable (RunwayConflict env existing proposed) := by
  unfold RunwayConflict
  infer_instance

def CommitmentsKnownRunways (env : RunwayKernelEnv)
    (commitments : List RunwayCommitment) : Prop :=
  ∀ commitment ∈ commitments, commitment.runway ∈ env.runways

def CommitmentsConflictFree (env : RunwayKernelEnv)
    (commitments : List RunwayCommitment) : Prop :=
  ∀ left ∈ commitments, ∀ right ∈ commitments, left ≠ right →
    ¬ RunwayConflict env left right

def RunwayWellFormed (env : RunwayKernelEnv) : Prop :=
  env.runways.Nodup

def RunwayInv (env : RunwayKernelEnv) (state : RunwayState) : Prop :=
  state.commitments.Nodup ∧
    CommitmentsKnownRunways env state.commitments ∧
    CommitmentsConflictFree env state.commitments

def RunwayCertificateSound
    (env : RunwayKernelEnv) (state : RunwayState)
    (proposal : RunwayProposal) (approval : RunwayApproval) : Prop :=
  approval.certificate.kernel = .runway ∧
    approval.certificate.subject = proposalAircraft proposal ∧
    approval.certificate.issuedAtTick = state.commitments.length ∧
    proposalRunway proposal ∈ env.runways ∧
    approval.effect = proposalEffect proposal ∧
    approval.successor = applyRunwayProposal state proposal

def findConflict (env : RunwayKernelEnv)
    (proposed : RunwayCommitment) : List RunwayCommitment → Option RunwayCommitment
  | [] => none
  | head :: tail =>
      if RunwayConflict env head proposed then
        some head
      else
        findConflict env proposed tail

def runway_certify : RunwayCertifySig := fun env state proposal =>
  let commitment := proposalCommitment proposal
  if commitment.runway ∈ env.runways then
    match proposal with
    | .acquire commitment =>
        match findConflict env commitment state.commitments with
        | some existing => .rejected (.conflict existing commitment)
        | none => .approved (mkRunwayApproval state proposal)
    | .release commitment =>
        if commitment ∈ state.commitments then
          .approved (mkRunwayApproval state proposal)
        else
          .rejected
            (.malformedProposal
              s!"cannot release inactive runway commitment for {commitment.aircraft} on {commitment.runway}")
  else
    .rejected (.unknownRunway commitment.runway)

theorem KindsConflict_comm {env : RunwayKernelEnv}
    {left right : RunwayCommitmentKind} :
    KindsConflict env left right → KindsConflict env right left := by
  intro hConflict
  simpa [KindsConflict, or_comm] using hConflict

theorem RunwayConflict_comm {env : RunwayKernelEnv}
    {left right : RunwayCommitment} :
    RunwayConflict env left right → RunwayConflict env right left := by
  intro hConflict
  rcases hConflict with ⟨hRunway, hRest⟩
  refine ⟨hRunway.symm, ?_⟩
  cases hRest with
  | inl hAircraft =>
      exact Or.inl hAircraft.symm
  | inr hKinds =>
      exact Or.inr (KindsConflict_comm hKinds)

theorem RunwayConflict_self (env : RunwayKernelEnv)
    (commitment : RunwayCommitment) :
    RunwayConflict env commitment commitment := by
  unfold RunwayConflict
  simp [KindsConflict]

theorem mem_of_mem_removeCommitment
    {target member : RunwayCommitment} :
    ∀ {commitments : List RunwayCommitment},
      member ∈ removeCommitment target commitments →
        member ∈ commitments := by
  intro commitments hMem
  induction commitments with
  | nil =>
      cases hMem
  | cons head tail ih =>
      by_cases hEq : head = target
      · simp [removeCommitment, hEq] at hMem
        simpa using Or.inr (ih hMem)
      · simp [removeCommitment, hEq] at hMem
        cases hMem with
        | inl hHead =>
            simp [hHead]
        | inr hTail =>
            simpa using Or.inr (ih hTail)

theorem nodup_removeCommitment
    {target : RunwayCommitment} :
    ∀ {commitments : List RunwayCommitment},
      commitments.Nodup →
        (removeCommitment target commitments).Nodup := by
  intro commitments hNodup
  induction commitments with
  | nil =>
      simp [removeCommitment]
  | cons head tail ih =>
      by_cases hEq : head = target
      · have hTailNodup : tail.Nodup := (List.nodup_cons.mp hNodup).2
        simpa [removeCommitment, hEq] using ih hTailNodup
      · rcases List.nodup_cons.mp hNodup with ⟨hNotMem, hTailNodup⟩
        have hHeadFresh : head ∉ removeCommitment target tail := by
          intro hMem
          apply hNotMem
          exact mem_of_mem_removeCommitment hMem
        have hTailPreserved : (removeCommitment target tail).Nodup := ih hTailNodup
        simpa [removeCommitment, hEq] using
          (List.nodup_cons.mpr ⟨hHeadFresh, hTailPreserved⟩)

theorem findConflict_eq_some {env : RunwayKernelEnv}
    {proposed existing : RunwayCommitment} :
    ∀ {commitments : List RunwayCommitment},
      findConflict env proposed commitments = some existing →
        existing ∈ commitments ∧ RunwayConflict env existing proposed := by
  intro commitments hFind
  induction commitments with
  | nil =>
      simp [findConflict] at hFind
  | cons head tail ih =>
      simp [findConflict] at hFind
      by_cases hConflict : RunwayConflict env head proposed
      · simp [hConflict] at hFind
        cases hFind
        exact ⟨by simp, hConflict⟩
      · simp [hConflict] at hFind
        rcases ih hFind with ⟨hMem, hConflictTail⟩
        exact ⟨by simp [hMem], hConflictTail⟩

theorem findConflict_eq_none {env : RunwayKernelEnv}
    {proposed : RunwayCommitment} :
    ∀ {commitments : List RunwayCommitment},
      findConflict env proposed commitments = none →
        ∀ existing, existing ∈ commitments → ¬ RunwayConflict env existing proposed := by
  intro commitments hFind existing hMem
  induction commitments with
  | nil =>
      simp at hMem
  | cons head tail ih =>
      simp [findConflict] at hFind
      by_cases hConflict : RunwayConflict env head proposed
      · simp [hConflict] at hFind
      · simp [hConflict] at hFind
        simp at hMem
        cases hMem with
        | inl hHead =>
            subst existing
            exact hConflict
        | inr hTail =>
            exact ih hFind hTail

theorem mkRunwayApproval_sound
    {env : RunwayKernelEnv} {state : RunwayState} {proposal : RunwayProposal}
    (hRunway : proposalRunway proposal ∈ env.runways) :
    RunwayCertificateSound env state proposal (mkRunwayApproval state proposal) := by
  cases proposal <;>
    simp [RunwayCertificateSound, mkRunwayApproval, mkRunwayCertificate,
      proposalAircraft, proposalRunway, proposalCommitment,
      proposalEffect, applyRunwayProposal] at hRunway ⊢ <;>
    exact hRunway

theorem acquire_preserves_inv
    {env : RunwayKernelEnv} {state : RunwayState} {commitment : RunwayCommitment}
    (hInv : RunwayInv env state)
    (hRunway : commitment.runway ∈ env.runways)
    (hNoConflict :
      ∀ existing, existing ∈ state.commitments → ¬ RunwayConflict env existing commitment) :
    RunwayInv env (applyRunwayProposal state (.acquire commitment)) := by
  rcases hInv with ⟨hNodup, hKnown, hConflictFree⟩
  have hFresh : commitment ∉ state.commitments := by
    intro hMem
    exact (hNoConflict commitment hMem) (RunwayConflict_self env commitment)
  constructor
  · simpa [applyRunwayProposal] using List.nodup_cons.mpr ⟨hFresh, hNodup⟩
  constructor
  · intro existing hMem
    simp [applyRunwayProposal] at hMem
    cases hMem with
    | inl hHead =>
        subst existing
        exact hRunway
    | inr hTail =>
        exact hKnown existing hTail
  · intro left hLeft right hRight hNe
    simp [applyRunwayProposal] at hLeft hRight
    cases hLeft with
    | inl hLeftHead =>
        subst left
        cases hRight with
        | inl hRightHead =>
            subst right
            exact False.elim (hNe rfl)
        | inr hRightTail =>
            intro hConflict
            exact (hNoConflict right hRightTail) (RunwayConflict_comm hConflict)
    | inr hLeftTail =>
        cases hRight with
        | inl hRightHead =>
            subst right
            exact hNoConflict left hLeftTail
        | inr hRightTail =>
            exact hConflictFree left hLeftTail right hRightTail hNe

theorem release_preserves_inv
    {env : RunwayKernelEnv} {state : RunwayState} {commitment : RunwayCommitment}
    (hInv : RunwayInv env state) :
    RunwayInv env (applyRunwayProposal state (.release commitment)) := by
  rcases hInv with ⟨hNodup, hKnown, hConflictFree⟩
  constructor
  · simpa [applyRunwayProposal] using nodup_removeCommitment (target := commitment) hNodup
  constructor
  · intro existing hMem
    exact hKnown existing (mem_of_mem_removeCommitment hMem)
  · intro left hLeft right hRight hNe
    exact hConflictFree left (mem_of_mem_removeCommitment hLeft) right
      (mem_of_mem_removeCommitment hRight) hNe

/--
Milestone 1 starting theorem.

The runway kernel is proved as a closed local system over commitment kinds and
their conflict table. No airport topology appears here.
-/
theorem RunwayKernelMilestone1Theorem :
  ∀ env state proposal approval,
    RunwayWellFormed env →
    RunwayInv env state →
    runway_certify env state proposal = .approved approval →
      RunwayCertificateSound env state proposal approval ∧
      RunwayInv env approval.successor := by
  intro env state proposal approval _hWellFormed hInv hApproved
  cases proposal with
  | acquire commitment =>
      by_cases hRunway : commitment.runway ∈ env.runways
      · cases hFind : findConflict env commitment state.commitments with
        | none =>
            simp [runway_certify, proposalCommitment, hRunway, hFind] at hApproved
            subst approval
            constructor
            · exact mkRunwayApproval_sound hRunway
            ·
              apply acquire_preserves_inv hInv hRunway
              exact findConflict_eq_none hFind
        | some existing =>
            simp [runway_certify, proposalCommitment, hRunway, hFind] at hApproved
      · simp [runway_certify, proposalCommitment, hRunway] at hApproved
  | release commitment =>
      by_cases hRunway : commitment.runway ∈ env.runways
      · by_cases hMem : commitment ∈ state.commitments
        · simp [runway_certify, proposalCommitment, hRunway, hMem] at hApproved
          subst approval
          constructor
          · exact mkRunwayApproval_sound hRunway
          · exact release_preserves_inv hInv
        · simp [runway_certify, proposalCommitment, hRunway, hMem] at hApproved
      · simp [runway_certify, proposalCommitment, hRunway] at hApproved

end CertifiedAtc
