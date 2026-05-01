import CertifiedAtc.GreenfieldCompletion

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldExecution` is the resolved active-clearance layer.

It keeps the existing abstract lifecycle machine as a lower-level algebra for
status transitions and supersession shape, but the managed execution state now
owns `ResolvedClearance` values and evaluates completion against resolved step
payloads rather than raw instructions.
-/

structure ManagedResolvedClearance where
  resolved : ResolvedClearance
  suppressedDomains : UniqueSet ClearanceDomain := {}
  deriving DecidableEq, Repr

def ManagedResolvedClearance.source (managed : ManagedResolvedClearance) : StructuredClearance :=
  managed.resolved.source

def ManagedResolvedClearance.aircraft (managed : ManagedResolvedClearance) : AircraftId :=
  managed.source.aircraft

def ManagedResolvedClearance.status (managed : ManagedResolvedClearance) : CertifiedAtc.ClearanceStatus :=
  managed.source.status

def ManagedResolvedClearance.stepDomains
    (managed : ManagedResolvedClearance) : UniqueSet ClearanceDomain :=
  managed.resolved.stepDomains

def ManagedResolvedClearance.effectiveDomains
    (managed : ManagedResolvedClearance) : UniqueSet ClearanceDomain :=
  UniqueSet.diff managed.stepDomains managed.suppressedDomains

def ManagedResolvedClearance.withResolved
    (managed : ManagedResolvedClearance)
    (resolved : ResolvedClearance) :
    ManagedResolvedClearance :=
  { managed with resolved := resolved }

def ManagedResolvedClearance.withSource
    (managed : ManagedResolvedClearance)
    (source : StructuredClearance) :
    ManagedResolvedClearance :=
  managed.withResolved (managed.resolved.withSource source)

def ManagedResolvedClearance.withStatus
    (managed : ManagedResolvedClearance)
    (status : CertifiedAtc.ClearanceStatus) :
    ManagedResolvedClearance :=
  managed.withSource (clearanceWithStatus managed.source status)

def ManagedResolvedClearance.suppress
    (managed : ManagedResolvedClearance)
    (domains : UniqueSet ClearanceDomain) :
    ManagedResolvedClearance :=
  { managed with
      suppressedDomains := UniqueSet.union managed.suppressedDomains domains }

def ManagedResolvedClearance.clearSuppression
    (managed : ManagedResolvedClearance) :
    ManagedResolvedClearance :=
  { managed with suppressedDomains := {} }

def ManagedResolvedClearance.effectiveSteps
    (managed : ManagedResolvedClearance) : List ResolvedStep :=
  managed.resolved.effectiveSteps managed.suppressedDomains

def ManagedResolvedClearance.requiredCompletionStepIndices
    (managed : ManagedResolvedClearance) : List Nat :=
  managed.resolved.requiredCompletionStepIndices managed.suppressedDomains

def ManagedResolvedClearance.lifecycleView
    (managed : ManagedResolvedClearance) : ManagedClearance :=
  { clearance := managed.source
    suppressedDomains := managed.suppressedDomains }

def ManagedResolvedClearance.withLifecycleView
    (managed : ManagedResolvedClearance)
    (view : ManagedClearance) :
    ManagedResolvedClearance :=
  { resolved := managed.resolved.withSource view.clearance
    suppressedDomains := view.suppressedDomains }

@[simp] theorem ManagedResolvedClearance.withLifecycleView_lifecycleView
    (managed : ManagedResolvedClearance) :
    managed.withLifecycleView managed.lifecycleView = managed := by
  cases managed
  rfl

@[simp] theorem ManagedResolvedClearance.lifecycleView_source_id
    (managed : ManagedResolvedClearance) :
    managed.lifecycleView.source.id = managed.source.id := by
  rfl

@[simp] theorem ManagedResolvedClearance.withResolved_source_id
    (managed : ManagedResolvedClearance)
    (resolved : ResolvedClearance) :
    (managed.withResolved resolved).source.id = resolved.source.id := by
  rfl

@[simp] theorem ManagedResolvedClearance.withResolved_allStepsCompatible
    (managed : ManagedResolvedClearance)
    (resolved : ResolvedClearance) :
    (managed.withResolved resolved).resolved.allStepsCompatible = resolved.allStepsCompatible := by
  rfl

@[simp] theorem ManagedResolvedClearance.withSource_source_id
    (managed : ManagedResolvedClearance)
    (source : StructuredClearance) :
    (managed.withSource source).source.id = source.id := by
  cases managed
  rfl

@[simp] theorem ManagedResolvedClearance.withSource_allStepsCompatible
    (managed : ManagedResolvedClearance)
    (source : StructuredClearance) :
    (managed.withSource source).resolved.allStepsCompatible = managed.resolved.allStepsCompatible := by
  cases managed
  rfl

@[simp] theorem ManagedResolvedClearance.withStatus_source_id
    (managed : ManagedResolvedClearance)
    (status : CertifiedAtc.ClearanceStatus) :
    (managed.withStatus status).source.id = managed.source.id := by
  cases managed
  rfl

@[simp] theorem ManagedResolvedClearance.withStatus_allStepsCompatible
    (managed : ManagedResolvedClearance)
    (status : CertifiedAtc.ClearanceStatus) :
    (managed.withStatus status).resolved.allStepsCompatible = managed.resolved.allStepsCompatible := by
  cases managed
  rfl

@[simp] theorem ManagedResolvedClearance.suppress_source_id
    (managed : ManagedResolvedClearance)
    (domains : UniqueSet ClearanceDomain) :
    (managed.suppress domains).source.id = managed.source.id := by
  rfl

@[simp] theorem ManagedResolvedClearance.suppress_allStepsCompatible
    (managed : ManagedResolvedClearance)
    (domains : UniqueSet ClearanceDomain) :
    (managed.suppress domains).resolved.allStepsCompatible = managed.resolved.allStepsCompatible := by
  rfl

@[simp] theorem ManagedResolvedClearance.clearSuppression_source_id
    (managed : ManagedResolvedClearance) :
    managed.clearSuppression.source.id = managed.source.id := by
  rfl

@[simp] theorem ManagedResolvedClearance.clearSuppression_allStepsCompatible
    (managed : ManagedResolvedClearance) :
    managed.clearSuppression.resolved.allStepsCompatible = managed.resolved.allStepsCompatible := by
  rfl

@[simp] theorem ManagedResolvedClearance.withLifecycleView_source_id
    (managed : ManagedResolvedClearance)
    (view : ManagedClearance) :
    (managed.withLifecycleView view).source.id = view.source.id := by
  cases managed
  rfl

@[simp] theorem ManagedResolvedClearance.withLifecycleView_allStepsCompatible
    (managed : ManagedResolvedClearance)
    (view : ManagedClearance) :
    (managed.withLifecycleView view).resolved.allStepsCompatible = managed.resolved.allStepsCompatible := by
  cases managed
  rfl

def stageIncomingResolvedClearance
    (clearance : ResolvedClearance) :
    ManagedResolvedClearance :=
  let staged := stageIncomingClearance clearance.source
  { resolved := clearance.withSource staged.clearance
    suppressedDomains := staged.suppressedDomains }

@[simp] theorem stageIncomingResolvedClearance_source_id
    (clearance : ResolvedClearance) :
    (stageIncomingResolvedClearance clearance).source.id = clearance.source.id := by
  cases clearance
  rfl

@[simp] theorem stageIncomingResolvedClearance_allStepsCompatible
    (clearance : ResolvedClearance) :
    (stageIncomingResolvedClearance clearance).resolved.allStepsCompatible = clearance.allStepsCompatible := by
  cases clearance
  rfl

def findResolvedById
    (clearances : List ManagedResolvedClearance)
    (id : ClearanceId) :
    Option ManagedResolvedClearance :=
  clearances.find? (fun managed => managed.source.id = id)

def resolvedClearanceIds
    (clearances : List ManagedResolvedClearance) :
    List ClearanceId :=
  clearances.map (fun managed => managed.source.id)

def UniqueResolvedClearanceIds
    (clearances : List ManagedResolvedClearance) : Prop :=
  (resolvedClearanceIds clearances).Nodup

def AllResolvedCompatible
    (clearances : List ManagedResolvedClearance) : Prop :=
  ∀ managed ∈ clearances, managed.resolved.allStepsCompatible = true

def WellFormedResolvedSet
    (clearances : List ManagedResolvedClearance) : Prop :=
  UniqueResolvedClearanceIds clearances ∧ AllResolvedCompatible clearances

def removeResolvedById
    (clearances : List ManagedResolvedClearance)
    (id : ClearanceId) :
    List ManagedResolvedClearance :=
  clearances.filter (fun managed => managed.source.id ≠ id)

def pendingResolvedConditionalViews
    (clearances : List ManagedResolvedClearance) :
    List ManagedClearance :=
  (sortByIssuedAt (clearances.map ManagedResolvedClearance.lifecycleView)).filter fun managed =>
    managed.status = .conditionPending && managed.source.condition.isSome

def reattachLifecycleViews
    (original : List ManagedResolvedClearance)
    : List ManagedClearance → List ManagedResolvedClearance
  | [] => []
  | view :: tail =>
      match findResolvedById original view.clearance.id with
      | some managed => managed.withLifecycleView view :: reattachLifecycleViews original tail
      | none => reattachLifecycleViews original tail

structure ResolvedSupersessionApplication where
  updatedExisting : List ManagedResolvedClearance
  fullySuperseded : List ManagedResolvedClearance
  partiallySuperseded : List ManagedResolvedClearance
  deriving DecidableEq, Repr

def applyIncomingResolvedSupersession
    (existing : List ManagedResolvedClearance)
    (incoming : ManagedResolvedClearance) :
    ResolvedSupersessionApplication :=
  let applied := applyIncomingSupersession (existing.map ManagedResolvedClearance.lifecycleView) incoming.lifecycleView
  { updatedExisting := reattachLifecycleViews existing applied.updatedExisting
    fullySuperseded := reattachLifecycleViews existing applied.fullySuperseded
    partiallySuperseded := reattachLifecycleViews existing applied.partiallySuperseded }

structure ResolvedClearanceAdmission where
  incoming : ManagedResolvedClearance
  clearances : List ManagedResolvedClearance
  terminalClearances : List ManagedResolvedClearance
  fullySuperseded : List ManagedResolvedClearance
  partiallySuperseded : List ManagedResolvedClearance
  deriving DecidableEq, Repr

def admitResolvedClearance
    (existing : List ManagedResolvedClearance)
    (incoming : ResolvedClearance) :
    ResolvedClearanceAdmission :=
  let stagedIncoming := stageIncomingResolvedClearance incoming
  let supersession :=
    if stagedIncoming.status = .active then
      applyIncomingResolvedSupersession existing stagedIncoming
    else
      { updatedExisting := existing
        fullySuperseded := []
        partiallySuperseded := [] }
  let allClearances := supersession.updatedExisting ++ [stagedIncoming]
  let clearances := allClearances.filter (fun managed => !(statusTerminal managed.status))
  let terminalClearances := allClearances.filter (fun managed => statusTerminal managed.status)
  { incoming := stagedIncoming
    clearances := clearances
    terminalClearances := terminalClearances
    fullySuperseded := supersession.fullySuperseded
    partiallySuperseded := supersession.partiallySuperseded }

structure ResolvedStepCompletion where
  step : ResolvedStep
  result : CompletionResult
  deriving DecidableEq, Repr

structure ResolvedCompletionEvaluation where
  source : ManagedResolvedClearance
  updated : ManagedResolvedClearance
  stepResults : List ResolvedStepCompletion
  newlyCompletedSteps : UniqueSet Nat
  isComplete : Bool
  deriving DecidableEq, Repr

def evaluateResolvedStepCompletion
    (managed : ManagedResolvedClearance)
    (observation : CompletionObservation)
    (step : ResolvedStep) :
    CompletionResult :=
  if step.domain ∈ managed.suppressedDomains then
    .notApplicable
  else
    match observedResolvedStepCompletion? observation step with
    | some result => result
    | none =>
        match step.completionCategory with
        | some .persistent => .notApplicable
        | some .onActivation => .complete
        | _ => .notComplete

def evaluateResolvedCompletion
    (managed : ManagedResolvedClearance)
    (observation : CompletionObservation) :
    ResolvedCompletionEvaluation :=
  let stepResults := managed.resolved.steps.map fun step =>
    { step := step
      result := evaluateResolvedStepCompletion managed observation step }
  let existingCompleted := managed.resolved.completedSteps
  let newlyCompletedSteps :=
    UniqueSet.ofList <|
      (stepResults.filterMap fun stepResult =>
        if stepResult.result = .complete then some stepResult.step.index else none).filter
          (fun index => index ∉ existingCompleted)
  let updatedSource :=
    match managed.source.content with
    | .single _ =>
        let singleComplete :=
          match stepResults with
          | [{ result := .complete, .. }] => true
          | _ => false
        let nextStatus :=
          if singleComplete then
            CertifiedAtc.ClearanceStatus.completed
          else
            managed.source.status
        clearanceWithStatus managed.source nextStatus
    | .compound _ =>
        let updatedCompleted := addCompletedSteps existingCompleted newlyCompletedSteps
        let updatedSource := withCompletedSteps managed.source updatedCompleted
        let isComplete :=
          managed.requiredCompletionStepIndices.all (fun index => index ∈ updatedCompleted)
        let nextStatus :=
          if isComplete then
            CertifiedAtc.ClearanceStatus.completed
          else
            updatedSource.status
        clearanceWithStatus updatedSource nextStatus
  let updatedManaged := managed.withSource updatedSource
  { source := managed
    updated := updatedManaged
    stepResults := stepResults
    newlyCompletedSteps := newlyCompletedSteps
    isComplete := updatedManaged.status = .completed }

def evaluateActiveResolvedCompletions
    (existing : List ManagedResolvedClearance)
    (observation : CompletionObservation) :
    List ManagedResolvedClearance × List ResolvedCompletionEvaluation :=
  existing.foldl
    (fun (acc : List ManagedResolvedClearance × List ResolvedCompletionEvaluation) managed =>
      let updatedExisting := acc.1
      let evaluations := acc.2
      if managed.status ≠ .active then
        (updatedExisting ++ [managed], evaluations)
      else
        let evaluation := evaluateResolvedCompletion managed observation
        (updatedExisting ++ [evaluation.updated], evaluations ++ [evaluation]))
    ([], [])

def pendingResolvedConditionalIds
    (clearances : List ManagedResolvedClearance) :
    List ClearanceId :=
  (pendingResolvedConditionalViews clearances).filterMap fun managed =>
    if managed.status = .conditionPending && managed.source.condition.isSome then
      some managed.source.id
    else
      none

structure ResolvedConditionActivation where
  before : ManagedResolvedClearance
  after : ManagedResolvedClearance
  deriving DecidableEq, Repr

structure ResolvedClearanceReconciliation where
  clearances : List ManagedResolvedClearance
  terminalClearances : List ManagedResolvedClearance
  completionEvaluations : List ResolvedCompletionEvaluation
  activatedClearances : List ResolvedConditionActivation
  fullySuperseded : List ManagedResolvedClearance
  partiallySuperseded : List ManagedResolvedClearance
  deriving DecidableEq, Repr

def activatePendingResolvedFrom
    (conditionEvaluator : ConditionEvaluator) :
    List ClearanceId →
    List ManagedResolvedClearance →
    List ResolvedConditionActivation →
    List ManagedResolvedClearance →
    List ManagedResolvedClearance →
    ResolvedClearanceReconciliation
  | [], working, activations, fullySuperseded, partiallySuperseded =>
      let clearances := working.filter (fun managed => !(statusTerminal managed.status))
      let terminalClearances := working.filter (fun managed => statusTerminal managed.status)
      { clearances := clearances
        terminalClearances := terminalClearances
        completionEvaluations := []
        activatedClearances := activations.reverse
        fullySuperseded := fullySuperseded.reverse
        partiallySuperseded := partiallySuperseded.reverse }
  | clearanceId :: tail, working, activations, fullySuperseded, partiallySuperseded =>
      match findResolvedById working clearanceId with
      | none =>
          activatePendingResolvedFrom conditionEvaluator tail working activations fullySuperseded partiallySuperseded
      | some current =>
          if current.status ≠ .conditionPending then
            activatePendingResolvedFrom conditionEvaluator tail working activations fullySuperseded partiallySuperseded
          else
            match current.source.condition with
            | none =>
                activatePendingResolvedFrom conditionEvaluator tail working activations fullySuperseded partiallySuperseded
            | some condition =>
                if !(conditionEvaluator current.aircraft condition) then
                  activatePendingResolvedFrom conditionEvaluator tail working activations fullySuperseded partiallySuperseded
                else
                  let activated := current.withStatus .active
                  let others := removeResolvedById working activated.source.id
                  let supersession := applyIncomingResolvedSupersession others activated
                  let nextWorking := supersession.updatedExisting ++ [activated]
                  activatePendingResolvedFrom conditionEvaluator tail nextWorking
                    ({ before := current, after := activated } :: activations)
                    (supersession.fullySuperseded ++ fullySuperseded)
                    (supersession.partiallySuperseded ++ partiallySuperseded)

def reconcileResolvedClearances
    (existing : List ManagedResolvedClearance)
    (observation : CompletionObservation := {})
    (conditionEvaluator : ConditionEvaluator := fun _ _ => false) :
    ResolvedClearanceReconciliation :=
  let completionPass := evaluateActiveResolvedCompletions existing observation
  let working := completionPass.1
  let completionEvaluations := completionPass.2
  let activations :=
    activatePendingResolvedFrom conditionEvaluator (pendingResolvedConditionalIds working) working [] [] []
  { activations with completionEvaluations := completionEvaluations }

def AllResolvedNonterminal
    (clearances : List ManagedResolvedClearance) : Prop :=
  ∀ managed ∈ clearances, statusTerminal managed.status = false

def AllResolvedActivationsWellStaged
    (activations : List ResolvedConditionActivation) : Prop :=
  ∀ activation ∈ activations,
    activation.before.status = .conditionPending ∧ activation.after.status = .active

def activatedResolvedIds
    (activations : List ResolvedConditionActivation) : List ClearanceId :=
  activations.map (fun activation => activation.before.source.id)

def sampleManagedResolvedRouteFrequency : ManagedResolvedClearance :=
  { resolved := sampleResolvedRouteFrequency
    suppressedDomains := UniqueSet.singleton .frequency }

theorem ManagedResolvedClearance.suppress_monotone
    {managed : ManagedResolvedClearance}
    {domains : UniqueSet ClearanceDomain}
    {domain : ClearanceDomain}
    (hMem : domain ∈ managed.suppressedDomains) :
    domain ∈ (managed.suppress domains).suppressedDomains := by
  simpa [ManagedResolvedClearance.suppress] using
    (UniqueSet.mem_union_left (left := managed.suppressedDomains) (right := domains) hMem)

theorem filterNonterminal_eq_self_of_allResolvedNonterminal
    (clearances : List ManagedResolvedClearance)
    (hAll : AllResolvedNonterminal clearances) :
    clearances.filter (fun managed => !(statusTerminal managed.status)) = clearances := by
  induction clearances with
  | nil =>
      simp
  | cons head tail ih =>
      have hHead : statusTerminal head.status = false :=
        hAll head (by simp)
      have hTail : AllResolvedNonterminal tail := by
        intro managed hMem
        exact hAll managed (by simp [hMem])
      simp [hHead, ih hTail]

theorem filterTerminal_eq_nil_of_allResolvedNonterminal
    (clearances : List ManagedResolvedClearance)
    (hAll : AllResolvedNonterminal clearances) :
    clearances.filter (fun managed => statusTerminal managed.status) = [] := by
  induction clearances with
  | nil =>
      simp
  | cons head tail ih =>
      have hHead : statusTerminal head.status = false :=
        hAll head (by simp)
      have hTail : AllResolvedNonterminal tail := by
        intro managed hMem
        exact hAll managed (by simp [hMem])
      simp [hHead, ih hTail]

theorem stageIncomingResolvedClearance_active_nonterminal
    (incoming : ResolvedClearance)
    (hActive : (stageIncomingResolvedClearance incoming).status = .active) :
    statusTerminal (stageIncomingResolvedClearance incoming).status = false := by
  rw [hActive]
  simp [statusTerminal]

theorem activatePendingResolvedFrom_preserves_wellStagedActivations
    (conditionEvaluator : ConditionEvaluator)
    (ids : List ClearanceId)
    (working : List ManagedResolvedClearance)
    (activations : List ResolvedConditionActivation)
    (fullySuperseded partiallySuperseded : List ManagedResolvedClearance)
    (hSeed : AllResolvedActivationsWellStaged activations) :
    AllResolvedActivationsWellStaged
      (activatePendingResolvedFrom
        conditionEvaluator
        ids
        working
        activations
        fullySuperseded
        partiallySuperseded).activatedClearances := by
  induction ids generalizing working activations fullySuperseded partiallySuperseded with
  | nil =>
      simp [activatePendingResolvedFrom, AllResolvedActivationsWellStaged] at hSeed ⊢
      intro activation hMem
      have hRevMem : activation ∈ activations := by
        simpa using hMem
      exact hSeed activation hRevMem
  | cons clearanceId tail ih =>
      unfold activatePendingResolvedFrom
      split
      · exact ih _ _ _ _ hSeed
      · rename_i current hFind
        split
        · exact ih _ _ _ _ hSeed
        · rename_i hPendingStatus
          cases hCondition : current.source.condition with
          | none =>
              exact ih _ _ _ _ hSeed
          | some condition =>
              by_cases hEval : conditionEvaluator current.aircraft condition = false
              · have hInactive : !(conditionEvaluator current.aircraft condition) = true := by
                  simp [hEval]
                simpa [hCondition, hEval, hInactive] using ih _ _ _ _ hSeed
              · have hEvalTrue : conditionEvaluator current.aircraft condition = true := by
                  cases hCond : conditionEvaluator current.aircraft condition with
                  | false =>
                      exfalso
                      exact hEval hCond
                  | true =>
                      rfl
                have hNextSeed : AllResolvedActivationsWellStaged ({ before := current, after := current.withStatus .active } :: activations) := by
                    intro activation hMem
                    simp at hMem
                    rcases hMem with hHead | hTail
                    · cases hHead
                      have hPending : current.status = .conditionPending := by
                        simpa using hPendingStatus
                      have hAfterActive : (current.withStatus .active).status = .active := by
                        cases current
                        rfl
                      exact ⟨hPending, hAfterActive⟩
                    · exact hSeed activation hTail
                let activated := current.withStatus .active
                let others := removeResolvedById working activated.source.id
                let supersession := applyIncomingResolvedSupersession others activated
                let nextWorking := supersession.updatedExisting ++ [activated]
                simpa [hCondition, hEvalTrue, activated, others, supersession, nextWorking] using
                  ih
                    nextWorking
                    ({ before := current, after := activated } :: activations)
                    (supersession.fullySuperseded ++ fullySuperseded)
                    (supersession.partiallySuperseded ++ partiallySuperseded)
                    hNextSeed

theorem pendingResolvedConditionalViews_ordered
    (clearances : List ManagedResolvedClearance) :
    IssuedAtOrdered (pendingResolvedConditionalViews clearances) := by
  unfold pendingResolvedConditionalViews
  exact issuedAtOrdered_filter _ _ (sortByIssuedAt_ordered _)

theorem findResolvedById_some_of_mem
    {clearances : List ManagedResolvedClearance}
    {managed : ManagedResolvedClearance}
    (hMem : managed ∈ clearances) :
    ∃ matched, findResolvedById clearances managed.source.id = some matched := by
  induction clearances with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem
      rcases hMem with hEq | hTail
      · subst managed
        exact ⟨head, by simp [findResolvedById]⟩
      · have ⟨matched, hFound⟩ := ih hTail
        by_cases hEq : head.source.id = managed.source.id
        · exact ⟨head, by simp [findResolvedById, hEq]⟩
        · exact ⟨matched, by simpa [findResolvedById, hEq] using hFound⟩

theorem findResolvedById_eq_some_mem
    {clearances : List ManagedResolvedClearance}
    {id : ClearanceId}
    {managed : ManagedResolvedClearance}
    (hFound : findResolvedById clearances id = some managed) :
    managed ∈ clearances := by
  induction clearances with
  | nil =>
      simp [findResolvedById] at hFound
  | cons head tail ih =>
      by_cases hEq : head.source.id = id
      · simp [findResolvedById, hEq] at hFound
        rcases hFound with rfl
        simp
      · simp [findResolvedById, hEq] at hFound
        simp [ih hFound]

theorem findResolvedById_eq_some_of_mem_unique
    {clearances : List ManagedResolvedClearance}
    (hUnique : UniqueResolvedClearanceIds clearances)
    {managed : ManagedResolvedClearance}
    (hMem : managed ∈ clearances) :
    findResolvedById clearances managed.source.id = some managed := by
  induction clearances with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp [UniqueResolvedClearanceIds, resolvedClearanceIds] at hUnique
      simp at hMem
      rcases hMem with hEq | hTail
      · subst hEq
        simp [findResolvedById]
      · have hManagedIdInTail : ∃ entry, entry ∈ tail ∧ entry.source.id = managed.source.id := by
          exact ⟨managed, hTail, rfl⟩
        have hHeadNe : head.source.id ≠ managed.source.id := by
          intro hEq
          rcases hManagedIdInTail with ⟨entry, hEntryMem, hEntryId⟩
          have hEntryNe : ¬ entry.source.id = head.source.id :=
            hUnique.1 entry hEntryMem
          apply hEntryNe
          calc
            entry.source.id = managed.source.id := hEntryId
            _ = head.source.id := hEq.symm
        simpa [findResolvedById, hHeadNe] using ih hUnique.2 hTail

theorem findResolvedById_some_of_id_mem
    {clearances : List ManagedResolvedClearance}
    (hUnique : UniqueResolvedClearanceIds clearances)
    {id : ClearanceId}
    (hMem : id ∈ resolvedClearanceIds clearances) :
    ∃ managed, findResolvedById clearances id = some managed := by
  simp [resolvedClearanceIds] at hMem
  rcases hMem with ⟨managed, hManagedMem, hManagedId⟩
  refine ⟨managed, ?_⟩
  simpa [hManagedId] using findResolvedById_eq_some_of_mem_unique hUnique hManagedMem

theorem findResolvedById_exists_of_id_mem
    {clearances : List ManagedResolvedClearance}
    {id : ClearanceId}
    (hMem : id ∈ resolvedClearanceIds clearances) :
    ∃ managed, findResolvedById clearances id = some managed := by
  simp [resolvedClearanceIds] at hMem
  rcases hMem with ⟨managed, hManagedMem, hManagedId⟩
  rcases findResolvedById_some_of_mem hManagedMem with ⟨matched, hFound⟩
  exact ⟨matched, by simpa [hManagedId] using hFound⟩

theorem uniqueClearanceIds_map_lifecycleView
    (clearances : List ManagedResolvedClearance)
    (hUnique : UniqueResolvedClearanceIds clearances) :
    UniqueClearanceIds (clearances.map ManagedResolvedClearance.lifecycleView) := by
  simpa [UniqueClearanceIds, UniqueResolvedClearanceIds, clearanceIds, resolvedClearanceIds,
    ManagedResolvedClearance.lifecycleView] using hUnique

theorem clearanceIds_map_lifecycleView
    (clearances : List ManagedResolvedClearance) :
    clearanceIds (clearances.map ManagedResolvedClearance.lifecycleView) =
      resolvedClearanceIds clearances := by
  induction clearances with
  | nil =>
      simp [clearanceIds, resolvedClearanceIds]
  | cons head tail ih =>
      calc
        clearanceIds ((head :: tail).map ManagedResolvedClearance.lifecycleView)
            = head.lifecycleView.source.id :: clearanceIds (tail.map ManagedResolvedClearance.lifecycleView) := by
                simp [clearanceIds]
        _ = head.source.id :: resolvedClearanceIds tail := by
              simp [ih]
        _ = resolvedClearanceIds (head :: tail) := by
              simp [resolvedClearanceIds]

theorem reattachLifecycleViews_from_subset
    (original current : List ManagedResolvedClearance)
    (hUnique : UniqueResolvedClearanceIds original)
    (hSubset : ∀ managed ∈ current, managed ∈ original) :
    reattachLifecycleViews original (current.map ManagedResolvedClearance.lifecycleView) = current := by
  induction current with
  | nil =>
      simp [reattachLifecycleViews]
  | cons head tail ih =>
      have hHeadMem : head ∈ original := hSubset head (by simp)
      have hHeadFind :
          findResolvedById original head.source.id = some head :=
        findResolvedById_eq_some_of_mem_unique hUnique hHeadMem
      have hHeadFindView :
          findResolvedById original head.lifecycleView.clearance.id = some head := by
        simpa [ManagedResolvedClearance.lifecycleView, ManagedResolvedClearance.source] using hHeadFind
      have hTailSubset : ∀ managed ∈ tail, managed ∈ original := by
        intro managed hMem
        exact hSubset managed (by simp [hMem])
      unfold reattachLifecycleViews
      simp [hHeadFindView, ih hTailSubset]

theorem reattachLifecycleViews_map_lifecycleView_self
    (original : List ManagedResolvedClearance)
    (hUnique : UniqueResolvedClearanceIds original) :
    reattachLifecycleViews original (original.map ManagedResolvedClearance.lifecycleView) = original := by
  exact reattachLifecycleViews_from_subset original original hUnique (by
    intro managed hMem
    exact hMem)

theorem reattachLifecycleViews_preserves_lifecycleViews
    (original : List ManagedResolvedClearance)
    (views : List ManagedClearance)
    (hFound : ∀ view ∈ views, ∃ managed, findResolvedById original view.clearance.id = some managed) :
    (reattachLifecycleViews original views).map ManagedResolvedClearance.lifecycleView = views := by
  induction views with
  | nil =>
      simp [reattachLifecycleViews]
  | cons view tail ih =>
      have hViewFound : ∃ managed, findResolvedById original view.clearance.id = some managed :=
        hFound view (by simp)
      rcases hViewFound with ⟨managed, hManaged⟩
      simp [reattachLifecycleViews, hManaged]
      constructor
      · simp [ManagedResolvedClearance.lifecycleView, ManagedResolvedClearance.withLifecycleView,
          ManagedResolvedClearance.source, ResolvedClearance.withSource]
      · exact ih (by
          intro nextView hMem
          exact hFound nextView (by simp [hMem]))

theorem reattachLifecycleViews_preserves_ids
    (original : List ManagedResolvedClearance)
    (views : List ManagedClearance)
    (hFound : ∀ view ∈ views, ∃ managed, findResolvedById original view.clearance.id = some managed) :
    resolvedClearanceIds (reattachLifecycleViews original views) = clearanceIds views := by
  have hViews :=
    reattachLifecycleViews_preserves_lifecycleViews original views hFound
  simpa [resolvedClearanceIds, clearanceIds, ManagedResolvedClearance.lifecycleView] using
    congrArg clearanceIds hViews

theorem reattachLifecycleViews_preserves_unique_ids
    (original : List ManagedResolvedClearance)
    (views : List ManagedClearance)
    (hFound : ∀ view ∈ views, ∃ managed, findResolvedById original view.clearance.id = some managed)
    (hUniqueViews : UniqueClearanceIds views) :
    UniqueResolvedClearanceIds (reattachLifecycleViews original views) := by
  have hIds :=
    reattachLifecycleViews_preserves_ids original views hFound
  rw [UniqueResolvedClearanceIds, hIds]
  exact hUniqueViews

theorem reattachLifecycleViews_preserves_compatibility
    (original : List ManagedResolvedClearance)
    (views : List ManagedClearance)
    (hFound : ∀ view ∈ views, ∃ managed, findResolvedById original view.clearance.id = some managed)
    (hCompat : AllResolvedCompatible original) :
    AllResolvedCompatible (reattachLifecycleViews original views) := by
  induction views with
  | nil =>
      intro managed hMem
      cases hMem
  | cons view tail ih =>
      have hViewFound : ∃ managed, findResolvedById original view.clearance.id = some managed :=
        hFound view (by simp)
      rcases hViewFound with ⟨matched, hMatched⟩
      have hMatchedMem : matched ∈ original :=
        findResolvedById_eq_some_mem hMatched
      have hMatchedCompat : matched.resolved.allStepsCompatible = true :=
        hCompat matched hMatchedMem
      have hTailFound :
          ∀ nextView ∈ tail, ∃ managed, findResolvedById original nextView.clearance.id = some managed := by
        intro nextView hMem
        exact hFound nextView (by simp [hMem])
      intro managed hMem
      simp [reattachLifecycleViews, hMatched] at hMem
      rcases hMem with hHead | hTail
      · rcases hHead with rfl
        simpa using hMatchedCompat
      · exact ih hTailFound managed hTail

theorem uniqueResolvedClearanceIds_filter
    (clearances : List ManagedResolvedClearance)
    (predicate : ManagedResolvedClearance → Bool)
    (hUnique : UniqueResolvedClearanceIds clearances) :
    UniqueResolvedClearanceIds (clearances.filter predicate) := by
  unfold UniqueResolvedClearanceIds resolvedClearanceIds at hUnique ⊢
  induction clearances with
  | nil =>
      simp
  | cons head tail ih =>
      have hHeadId : head.source.id ∉ tail.map (fun managed => managed.source.id) :=
        (List.nodup_cons.mp hUnique).1
      have hTailUnique : (tail.map (fun managed => managed.source.id)).Nodup :=
        (List.nodup_cons.mp hUnique).2
      by_cases hKeep : predicate head = true
      · have hHeadFiltered :
            head.source.id ∉ (tail.filter predicate).map (fun managed => managed.source.id) := by
            intro hMem
            apply hHeadId
            simp at hMem ⊢
            rcases hMem with ⟨managed, hManagedMem, hManagedId⟩
            exact ⟨managed, (by simpa using hManagedMem.1), hManagedId⟩
        simpa [hKeep] using
          (List.nodup_cons.mpr ⟨hHeadFiltered, by simpa using ih hTailUnique⟩)
      · simpa [hKeep] using ih hTailUnique

theorem allResolvedCompatible_filter
    (clearances : List ManagedResolvedClearance)
    (predicate : ManagedResolvedClearance → Bool)
    (hCompat : AllResolvedCompatible clearances) :
    AllResolvedCompatible (clearances.filter predicate) := by
  intro managed hMem
  have hFacts : managed ∈ clearances ∧ predicate managed = true := by
    simpa using hMem
  have hBase : managed ∈ clearances := by
    exact hFacts.1
  exact hCompat managed hBase

theorem allResolvedCompatible_snoc
    (clearances : List ManagedResolvedClearance)
    (managed : ManagedResolvedClearance)
    (hCompat : AllResolvedCompatible clearances)
    (hManaged : managed.resolved.allStepsCompatible = true) :
    AllResolvedCompatible (clearances ++ [managed]) := by
  intro current hMem
  simp at hMem
  rcases hMem with hCurrent | hCurrent
  · exact hCompat current hCurrent
  · rcases hCurrent with rfl
    exact hManaged

theorem removeResolvedById_preserves_unique_ids
    (clearances : List ManagedResolvedClearance)
    (id : ClearanceId)
    (hUnique : UniqueResolvedClearanceIds clearances) :
    UniqueResolvedClearanceIds (removeResolvedById clearances id) := by
  simpa [removeResolvedById] using
    uniqueResolvedClearanceIds_filter clearances (fun managed => managed.source.id ≠ id) hUnique

theorem removeResolvedById_excludes_id
    (clearances : List ManagedResolvedClearance)
    (id : ClearanceId) :
    id ∉ resolvedClearanceIds (removeResolvedById clearances id) := by
  intro hMem
  simp [removeResolvedById, resolvedClearanceIds] at hMem
  rcases hMem with ⟨managed, hManagedMem, hManagedId⟩
  exact hManagedMem.2 hManagedId

theorem uniqueResolvedClearanceIds_append_fresh
    (existing : List ManagedResolvedClearance)
    (incoming : ManagedResolvedClearance)
    (hUnique : UniqueResolvedClearanceIds existing)
    (hFresh : incoming.source.id ∉ resolvedClearanceIds existing) :
    UniqueResolvedClearanceIds (existing ++ [incoming]) := by
  induction existing with
  | nil =>
      simp [UniqueResolvedClearanceIds, resolvedClearanceIds]
  | cons head tail ih =>
      simp [UniqueResolvedClearanceIds, resolvedClearanceIds] at hUnique hFresh ⊢
      rcases hUnique with ⟨hHeadTail, hTailUnique⟩
      rcases hFresh with ⟨hFreshHead, hFreshTail⟩
      constructor
      · constructor
        · exact hHeadTail
        · exact fun hEq => hFreshHead hEq.symm
      · have hFreshTailIds : incoming.source.id ∉ resolvedClearanceIds tail := by
            simpa [resolvedClearanceIds] using hFreshTail
        simpa [UniqueResolvedClearanceIds, resolvedClearanceIds] using ih hTailUnique hFreshTailIds

theorem applyIncomingResolvedSupersession_other_aircraft_lifecycleInvariant
    (existing : List ManagedResolvedClearance)
    (incoming : ManagedResolvedClearance)
    (hOther : ∀ managed ∈ existing, managed.aircraft ≠ incoming.aircraft) :
    let result := applyIncomingResolvedSupersession existing incoming
    result.updatedExisting.map ManagedResolvedClearance.lifecycleView =
      existing.map ManagedResolvedClearance.lifecycleView ∧
      result.fullySuperseded = [] ∧
      result.partiallySuperseded = [] := by
  have hLifecycle :
      applyIncomingSupersession
          (existing.map ManagedResolvedClearance.lifecycleView)
          incoming.lifecycleView =
        { updatedExisting := existing.map ManagedResolvedClearance.lifecycleView
          fullySuperseded := []
          partiallySuperseded := [] } := by
    apply applyIncomingSupersession_identity_of_other_aircraft
    intro managed hMem
    simp at hMem
    rcases hMem with ⟨resolvedManaged, hResolvedMem, rfl⟩
    exact hOther resolvedManaged hResolvedMem
  have hFound :
      ∀ view ∈ existing.map ManagedResolvedClearance.lifecycleView,
        ∃ managed, findResolvedById existing view.clearance.id = some managed := by
    intro view hMem
    simp at hMem
    rcases hMem with ⟨managed, hManagedMem, rfl⟩
    exact findResolvedById_some_of_mem hManagedMem
  simp [applyIncomingResolvedSupersession, hLifecycle]
  constructor
  · exact reattachLifecycleViews_preserves_lifecycleViews existing _ hFound
  · constructor <;> simp [reattachLifecycleViews]

theorem applyIncomingResolvedSupersession_other_aircraft_invariant
    (existing : List ManagedResolvedClearance)
    (incoming : ManagedResolvedClearance)
    (hUnique : UniqueResolvedClearanceIds existing)
    (hOther : ∀ managed ∈ existing, managed.aircraft ≠ incoming.aircraft) :
    let result := applyIncomingResolvedSupersession existing incoming
    result.updatedExisting = existing ∧
      result.fullySuperseded = [] ∧
      result.partiallySuperseded = [] := by
  have hLifecycle :
      applyIncomingSupersession
          (existing.map ManagedResolvedClearance.lifecycleView)
          incoming.lifecycleView =
        { updatedExisting := existing.map ManagedResolvedClearance.lifecycleView
          fullySuperseded := []
          partiallySuperseded := [] } := by
    apply applyIncomingSupersession_identity_of_other_aircraft
    intro managed hMem
    simp at hMem
    rcases hMem with ⟨resolvedManaged, hResolvedMem, rfl⟩
    exact hOther resolvedManaged hResolvedMem
  have hReattach :
      reattachLifecycleViews existing (existing.map ManagedResolvedClearance.lifecycleView) = existing :=
    reattachLifecycleViews_map_lifecycleView_self existing hUnique
  simp [applyIncomingResolvedSupersession, hLifecycle, hReattach, reattachLifecycleViews]

theorem applyIncomingResolvedSupersession_updatedExisting_ids
    (existing : List ManagedResolvedClearance)
    (incoming : ManagedResolvedClearance)
    (hUnique : UniqueResolvedClearanceIds existing) :
    resolvedClearanceIds (applyIncomingResolvedSupersession existing incoming).updatedExisting =
      resolvedClearanceIds existing := by
  let applied :=
    applyIncomingSupersession
      (existing.map ManagedResolvedClearance.lifecycleView)
      incoming.lifecycleView
  have hLifecycleIds :
      clearanceIds applied.updatedExisting =
        clearanceIds (existing.map ManagedResolvedClearance.lifecycleView) := by
    simpa [applied] using
      applyIncomingSupersession_updatedExisting_ids
        (existing.map ManagedResolvedClearance.lifecycleView)
        incoming.lifecycleView
  have hFound :
      ∀ view ∈ applied.updatedExisting, ∃ managed, findResolvedById existing view.clearance.id = some managed := by
    intro view hMem
    have hIdMemApplied : view.clearance.id ∈ clearanceIds applied.updatedExisting := by
      simp [clearanceIds]
      exact ⟨view, hMem, rfl⟩
    have hIdMemExisting : view.clearance.id ∈ resolvedClearanceIds existing := by
      have : view.clearance.id ∈ clearanceIds (existing.map ManagedResolvedClearance.lifecycleView) := by
        simpa [hLifecycleIds] using hIdMemApplied
      simpa [clearanceIds, resolvedClearanceIds, ManagedResolvedClearance.lifecycleView] using this
    exact findResolvedById_some_of_id_mem hUnique hIdMemExisting
  have hReattachIds :
      resolvedClearanceIds (reattachLifecycleViews existing applied.updatedExisting) =
        clearanceIds applied.updatedExisting :=
    reattachLifecycleViews_preserves_ids existing applied.updatedExisting hFound
  calc
    resolvedClearanceIds (applyIncomingResolvedSupersession existing incoming).updatedExisting
        = resolvedClearanceIds (reattachLifecycleViews existing applied.updatedExisting) := by
            simp [applyIncomingResolvedSupersession, applied]
    _ = clearanceIds applied.updatedExisting := hReattachIds
    _ = clearanceIds (existing.map ManagedResolvedClearance.lifecycleView) := hLifecycleIds
    _ = resolvedClearanceIds existing := by
          exact clearanceIds_map_lifecycleView existing

theorem applyIncomingResolvedSupersession_preserves_unique_ids
    (existing : List ManagedResolvedClearance)
    (incoming : ManagedResolvedClearance)
    (hUnique : UniqueResolvedClearanceIds existing) :
    UniqueResolvedClearanceIds (applyIncomingResolvedSupersession existing incoming).updatedExisting := by
  let applied :=
    applyIncomingSupersession
      (existing.map ManagedResolvedClearance.lifecycleView)
      incoming.lifecycleView
  have hLifecycleUnique : UniqueClearanceIds applied.updatedExisting := by
    simpa [applied] using
      applyIncomingSupersession_preserves_unique_ids
        (existing.map ManagedResolvedClearance.lifecycleView)
        incoming.lifecycleView
        (uniqueClearanceIds_map_lifecycleView existing hUnique)
  have hLifecycleIds :
      clearanceIds applied.updatedExisting =
        clearanceIds (existing.map ManagedResolvedClearance.lifecycleView) := by
    simpa [applied] using
      applyIncomingSupersession_updatedExisting_ids
        (existing.map ManagedResolvedClearance.lifecycleView)
        incoming.lifecycleView
  have hFound :
      ∀ view ∈ applied.updatedExisting, ∃ managed, findResolvedById existing view.clearance.id = some managed := by
    intro view hMem
    have hIdMemApplied : view.clearance.id ∈ clearanceIds applied.updatedExisting := by
      simp [clearanceIds]
      exact ⟨view, hMem, rfl⟩
    have hIdMemExisting : view.clearance.id ∈ resolvedClearanceIds existing := by
      have : view.clearance.id ∈ clearanceIds (existing.map ManagedResolvedClearance.lifecycleView) := by
        simpa [hLifecycleIds] using hIdMemApplied
      simpa [clearanceIds, resolvedClearanceIds, ManagedResolvedClearance.lifecycleView] using this
    exact findResolvedById_some_of_id_mem hUnique hIdMemExisting
  simpa [applyIncomingResolvedSupersession, applied] using
    reattachLifecycleViews_preserves_unique_ids existing applied.updatedExisting hFound hLifecycleUnique

theorem applyIncomingResolvedSupersession_preserves_compatibility
    (existing : List ManagedResolvedClearance)
    (incoming : ManagedResolvedClearance)
    (hCompat : AllResolvedCompatible existing) :
    AllResolvedCompatible (applyIncomingResolvedSupersession existing incoming).updatedExisting := by
  let applied :=
    applyIncomingSupersession
      (existing.map ManagedResolvedClearance.lifecycleView)
      incoming.lifecycleView
  have hLifecycleIds :
      clearanceIds applied.updatedExisting =
        clearanceIds (existing.map ManagedResolvedClearance.lifecycleView) := by
    simpa [applied] using
      applyIncomingSupersession_updatedExisting_ids
        (existing.map ManagedResolvedClearance.lifecycleView)
        incoming.lifecycleView
  have hFound :
      ∀ view ∈ applied.updatedExisting, ∃ managed, findResolvedById existing view.clearance.id = some managed := by
    intro view hMem
    have hIdMemApplied : view.clearance.id ∈ clearanceIds applied.updatedExisting := by
      simp [clearanceIds]
      exact ⟨view, hMem, rfl⟩
    have hIdMemExisting : view.clearance.id ∈ resolvedClearanceIds existing := by
      have : view.clearance.id ∈ clearanceIds (existing.map ManagedResolvedClearance.lifecycleView) := by
        simpa [hLifecycleIds] using hIdMemApplied
      simpa [clearanceIds, resolvedClearanceIds, ManagedResolvedClearance.lifecycleView] using this
    exact findResolvedById_exists_of_id_mem hIdMemExisting
  simpa [applyIncomingResolvedSupersession, applied] using
    reattachLifecycleViews_preserves_compatibility existing applied.updatedExisting hFound hCompat

theorem activatePendingResolvedFrom_preserves_unique_ids
    (conditionEvaluator : ConditionEvaluator)
    (ids : List ClearanceId)
    (working : List ManagedResolvedClearance)
    (activations : List ResolvedConditionActivation)
    (fullySuperseded partiallySuperseded : List ManagedResolvedClearance)
    (hWorking : UniqueResolvedClearanceIds working) :
    UniqueResolvedClearanceIds
        (activatePendingResolvedFrom
          conditionEvaluator
          ids
          working
          activations
          fullySuperseded
          partiallySuperseded).clearances ∧
      UniqueResolvedClearanceIds
        (activatePendingResolvedFrom
          conditionEvaluator
          ids
          working
          activations
          fullySuperseded
          partiallySuperseded).terminalClearances := by
  induction ids generalizing working activations fullySuperseded partiallySuperseded with
  | nil =>
      constructor
      · simpa [activatePendingResolvedFrom] using
          uniqueResolvedClearanceIds_filter
            working
            (fun managed => !(statusTerminal managed.status))
            hWorking
      · simpa [activatePendingResolvedFrom] using
          uniqueResolvedClearanceIds_filter
            working
            (fun managed => statusTerminal managed.status)
            hWorking
  | cons clearanceId tail ih =>
      unfold activatePendingResolvedFrom
      split
      · exact ih _ _ _ _ hWorking
      · rename_i current hFind
        split
        · exact ih _ _ _ _ hWorking
        · rename_i hPendingStatus
          cases hCondition : current.source.condition with
          | none =>
              exact ih _ _ _ _ hWorking
          | some condition =>
              by_cases hEval : conditionEvaluator current.aircraft condition = false
              · have hInactive : !(conditionEvaluator current.aircraft condition) = true := by
                  simp [hEval]
                simpa [hCondition, hEval, hInactive] using ih _ _ _ _ hWorking
              · have hEvalTrue : conditionEvaluator current.aircraft condition = true := by
                  cases hCond : conditionEvaluator current.aircraft condition with
                  | false =>
                      exfalso
                      exact hEval hCond
                  | true =>
                      rfl
                let activated := current.withStatus .active
                let others := removeResolvedById working activated.source.id
                have hOthersUnique : UniqueResolvedClearanceIds others := by
                  simpa [others] using
                    removeResolvedById_preserves_unique_ids working activated.source.id hWorking
                let supersession := applyIncomingResolvedSupersession others activated
                have hSupersessionUnique : UniqueResolvedClearanceIds supersession.updatedExisting := by
                  simpa [supersession] using
                    applyIncomingResolvedSupersession_preserves_unique_ids others activated hOthersUnique
                have hSupersessionIds :
                    resolvedClearanceIds supersession.updatedExisting = resolvedClearanceIds others := by
                  simpa [supersession] using
                    applyIncomingResolvedSupersession_updatedExisting_ids others activated hOthersUnique
                have hActivatedFresh :
                    activated.source.id ∉ resolvedClearanceIds supersession.updatedExisting := by
                  have hFreshOthers : activated.source.id ∉ resolvedClearanceIds others := by
                    simpa [others] using removeResolvedById_excludes_id working activated.source.id
                  simpa [hSupersessionIds] using hFreshOthers
                let nextWorking := supersession.updatedExisting ++ [activated]
                have hNextWorkingUnique : UniqueResolvedClearanceIds nextWorking := by
                  simpa [nextWorking] using
                    uniqueResolvedClearanceIds_append_fresh
                      supersession.updatedExisting
                      activated
                      hSupersessionUnique
                      hActivatedFresh
                simpa [hCondition, hEvalTrue, activated, others, supersession, nextWorking] using
                  ih
                    nextWorking
                    ({ before := current, after := activated } :: activations)
                    (supersession.fullySuperseded ++ fullySuperseded)
                    (supersession.partiallySuperseded ++ partiallySuperseded)
                    hNextWorkingUnique

theorem activatePendingResolvedFrom_preserves_compatibility
    (conditionEvaluator : ConditionEvaluator)
    (ids : List ClearanceId)
    (working : List ManagedResolvedClearance)
    (activations : List ResolvedConditionActivation)
    (fullySuperseded partiallySuperseded : List ManagedResolvedClearance)
    (hWorking : AllResolvedCompatible working) :
    AllResolvedCompatible
        (activatePendingResolvedFrom
          conditionEvaluator
          ids
          working
          activations
          fullySuperseded
          partiallySuperseded).clearances ∧
      AllResolvedCompatible
        (activatePendingResolvedFrom
          conditionEvaluator
          ids
          working
          activations
          fullySuperseded
          partiallySuperseded).terminalClearances := by
  induction ids generalizing working activations fullySuperseded partiallySuperseded with
  | nil =>
      constructor
      · simpa [activatePendingResolvedFrom] using
          allResolvedCompatible_filter
            working
            (fun managed => !(statusTerminal managed.status))
            hWorking
      · simpa [activatePendingResolvedFrom] using
          allResolvedCompatible_filter
            working
            (fun managed => statusTerminal managed.status)
            hWorking
  | cons clearanceId tail ih =>
      unfold activatePendingResolvedFrom
      split
      · exact ih _ _ _ _ hWorking
      · rename_i current hFind
        split
        · exact ih _ _ _ _ hWorking
        · rename_i hPendingStatus
          cases hCondition : current.source.condition with
          | none =>
              exact ih _ _ _ _ hWorking
          | some condition =>
              by_cases hEval : conditionEvaluator current.aircraft condition = false
              · have hInactive : !(conditionEvaluator current.aircraft condition) = true := by
                  simp [hEval]
                simpa [hCondition, hEval, hInactive] using ih _ _ _ _ hWorking
              · have hEvalTrue : conditionEvaluator current.aircraft condition = true := by
                  cases hCond : conditionEvaluator current.aircraft condition with
                  | false =>
                      exfalso
                      exact hEval hCond
                  | true =>
                      rfl
                let activated := current.withStatus .active
                have hCurrentMem : current ∈ working :=
                  findResolvedById_eq_some_mem hFind
                have hActivatedCompat : activated.resolved.allStepsCompatible = true := by
                  simpa [activated] using hWorking current hCurrentMem
                let others := removeResolvedById working activated.source.id
                have hOthersCompat : AllResolvedCompatible others := by
                  simpa [others, removeResolvedById] using
                    allResolvedCompatible_filter working (fun managed => managed.source.id ≠ activated.source.id) hWorking
                let supersession := applyIncomingResolvedSupersession others activated
                have hSupersessionCompat : AllResolvedCompatible supersession.updatedExisting := by
                  simpa [supersession] using
                    applyIncomingResolvedSupersession_preserves_compatibility others activated hOthersCompat
                let nextWorking := supersession.updatedExisting ++ [activated]
                have hNextWorkingCompat : AllResolvedCompatible nextWorking := by
                  simpa [nextWorking] using
                    allResolvedCompatible_snoc supersession.updatedExisting activated hSupersessionCompat hActivatedCompat
                simpa [hCondition, hEvalTrue, activated, others, supersession, nextWorking] using
                  ih
                    nextWorking
                    ({ before := current, after := activated } :: activations)
                    (supersession.fullySuperseded ++ fullySuperseded)
                    (supersession.partiallySuperseded ++ partiallySuperseded)
                    hNextWorkingCompat

theorem admitResolvedClearance_condition_pending_has_no_supersession
    (existing : List ManagedResolvedClearance)
    (incoming : ResolvedClearance)
    (hPending : (stageIncomingResolvedClearance incoming).status = .conditionPending) :
    let admission := admitResolvedClearance existing incoming
    admission.fullySuperseded = [] ∧
      admission.partiallySuperseded = [] := by
  simp [admitResolvedClearance, hPending]

theorem admitResolvedClearance_preserves_unique_ids_of_fresh
    (existing : List ManagedResolvedClearance)
    (incoming : ResolvedClearance)
    (hUnique : UniqueResolvedClearanceIds existing)
    (hFresh : incoming.source.id ∉ resolvedClearanceIds existing) :
    UniqueResolvedClearanceIds (admitResolvedClearance existing incoming).clearances ∧
      UniqueResolvedClearanceIds (admitResolvedClearance existing incoming).terminalClearances := by
  let staged := stageIncomingResolvedClearance incoming
  let supersession :=
    if staged.status = .active then
      applyIncomingResolvedSupersession existing staged
    else
      { updatedExisting := existing
        fullySuperseded := []
        partiallySuperseded := [] }
  have hUpdatedUnique : UniqueResolvedClearanceIds supersession.updatedExisting := by
    by_cases hActive : staged.status = .active
    · simpa [staged, supersession, hActive] using
        applyIncomingResolvedSupersession_preserves_unique_ids existing staged hUnique
    · simpa [staged, supersession, hActive] using hUnique
  have hUpdatedIds : resolvedClearanceIds supersession.updatedExisting = resolvedClearanceIds existing := by
    by_cases hActive : staged.status = .active
    · simpa [staged, supersession, hActive] using
        applyIncomingResolvedSupersession_updatedExisting_ids existing staged hUnique
    · simp [staged, supersession, hActive]
  have hFreshUpdated : staged.source.id ∉ resolvedClearanceIds supersession.updatedExisting := by
    simpa [staged, hUpdatedIds] using hFresh
  have hAllUnique : UniqueResolvedClearanceIds (supersession.updatedExisting ++ [staged]) := by
    exact uniqueResolvedClearanceIds_append_fresh supersession.updatedExisting staged hUpdatedUnique hFreshUpdated
  constructor
  · simpa [admitResolvedClearance, staged, supersession] using
      uniqueResolvedClearanceIds_filter
        (supersession.updatedExisting ++ [staged])
        (fun managed => !(statusTerminal managed.status))
        hAllUnique
  · simpa [admitResolvedClearance, staged, supersession] using
      uniqueResolvedClearanceIds_filter
        (supersession.updatedExisting ++ [staged])
        (fun managed => statusTerminal managed.status)
        hAllUnique

theorem admitResolvedClearance_preserves_wellFormed_of_fresh_and_compatible
    (existing : List ManagedResolvedClearance)
    (incoming : ResolvedClearance)
    (hWellFormed : WellFormedResolvedSet existing)
    (hFresh : incoming.source.id ∉ resolvedClearanceIds existing)
    (hIncomingCompat : incoming.allStepsCompatible = true) :
    WellFormedResolvedSet (admitResolvedClearance existing incoming).clearances ∧
      WellFormedResolvedSet (admitResolvedClearance existing incoming).terminalClearances := by
  rcases hWellFormed with ⟨hUnique, hCompat⟩
  let staged := stageIncomingResolvedClearance incoming
  let supersession :=
    if staged.status = .active then
      applyIncomingResolvedSupersession existing staged
    else
      { updatedExisting := existing
        fullySuperseded := []
        partiallySuperseded := [] }
  have hUpdatedCompat : AllResolvedCompatible supersession.updatedExisting := by
    by_cases hActive : staged.status = .active
    · simpa [staged, supersession, hActive] using
        applyIncomingResolvedSupersession_preserves_compatibility existing staged hCompat
    · simpa [staged, supersession, hActive] using hCompat
  have hStagedCompat : staged.resolved.allStepsCompatible = true := by
    simpa [staged] using hIncomingCompat
  have hAllCompat : AllResolvedCompatible (supersession.updatedExisting ++ [staged]) := by
    exact allResolvedCompatible_snoc supersession.updatedExisting staged hUpdatedCompat hStagedCompat
  have hUniquePreserved :=
    admitResolvedClearance_preserves_unique_ids_of_fresh existing incoming hUnique hFresh
  constructor
  · refine ⟨hUniquePreserved.1, ?_⟩
    simpa [admitResolvedClearance, staged, supersession] using
      allResolvedCompatible_filter
        (supersession.updatedExisting ++ [staged])
        (fun managed => !(statusTerminal managed.status))
        hAllCompat
  · refine ⟨hUniquePreserved.2, ?_⟩
    simpa [admitResolvedClearance, staged, supersession] using
      allResolvedCompatible_filter
        (supersession.updatedExisting ++ [staged])
        (fun managed => statusTerminal managed.status)
        hAllCompat

theorem admitResolvedClearance_active_other_aircraft_appends
    (existing : List ManagedResolvedClearance)
    (incoming : ResolvedClearance)
    (hUnique : UniqueResolvedClearanceIds existing)
    (hOther : ∀ managed ∈ existing, managed.aircraft ≠ (stageIncomingResolvedClearance incoming).aircraft)
    (hExistingNonterminal : AllResolvedNonterminal existing)
    (hIncomingActive : (stageIncomingResolvedClearance incoming).status = .active) :
    let admission := admitResolvedClearance existing incoming
    admission.clearances = existing ++ [stageIncomingResolvedClearance incoming] ∧
      admission.terminalClearances = [] ∧
      admission.fullySuperseded = [] ∧
      admission.partiallySuperseded = [] := by
  have hInvariant :
      (applyIncomingResolvedSupersession existing (stageIncomingResolvedClearance incoming)).updatedExisting = existing ∧
        (applyIncomingResolvedSupersession existing (stageIncomingResolvedClearance incoming)).fullySuperseded = [] ∧
        (applyIncomingResolvedSupersession existing (stageIncomingResolvedClearance incoming)).partiallySuperseded = [] := by
    simpa using
      (applyIncomingResolvedSupersession_other_aircraft_invariant
        existing
        (stageIncomingResolvedClearance incoming)
        hUnique
        hOther)
  have hStagedNonterminal :
      statusTerminal (stageIncomingResolvedClearance incoming).status = false :=
    stageIncomingResolvedClearance_active_nonterminal incoming hIncomingActive
  have hCombinedNonterminal :
      AllResolvedNonterminal (existing ++ [stageIncomingResolvedClearance incoming]) := by
    intro managed hMem
    simp at hMem
    rcases hMem with hExisting | hStaged
    · exact hExistingNonterminal managed hExisting
    · rcases hStaged with rfl
      exact hStagedNonterminal
  have hClearances :
      (existing ++ [stageIncomingResolvedClearance incoming]).filter
          (fun managed => !(statusTerminal managed.status)) =
        existing ++ [stageIncomingResolvedClearance incoming] :=
    filterNonterminal_eq_self_of_allResolvedNonterminal _ hCombinedNonterminal
  have hTerminals :
      (existing ++ [stageIncomingResolvedClearance incoming]).filter
          (fun managed => statusTerminal managed.status) = [] :=
    filterTerminal_eq_nil_of_allResolvedNonterminal _ hCombinedNonterminal
  simp [admitResolvedClearance, hIncomingActive, hInvariant.1, hInvariant.2.1, hInvariant.2.2,
    hClearances, hTerminals]

theorem evaluateResolvedCompletion_updated_source_id
    (managed : ManagedResolvedClearance)
    (observation : CompletionObservation) :
    (evaluateResolvedCompletion managed observation).updated.source.id = managed.source.id := by
  cases managed with
  | mk resolved suppressedDomains =>
      cases resolved with
      | mk source steps =>
          cases source with
          | mk id aircraft content domain issuedBy issuedAt status condition =>
              cases content <;> rfl

theorem evaluateResolvedCompletion_updated_allStepsCompatible
    (managed : ManagedResolvedClearance)
    (observation : CompletionObservation) :
    (evaluateResolvedCompletion managed observation).updated.resolved.allStepsCompatible =
      managed.resolved.allStepsCompatible := by
  cases managed with
  | mk resolved suppressedDomains =>
      cases resolved with
      | mk source steps =>
          cases source with
          | mk id aircraft content domain issuedBy issuedAt status condition =>
              cases content <;> rfl

theorem evaluateActiveResolvedCompletions_preserves_ids_from
    (remaining updatedExisting : List ManagedResolvedClearance)
    (evaluations : List ResolvedCompletionEvaluation)
    (observation : CompletionObservation) :
    resolvedClearanceIds
        ((remaining.foldl
          (fun (acc : List ManagedResolvedClearance × List ResolvedCompletionEvaluation) managed =>
            let updatedExisting := acc.1
            let evaluations := acc.2
            if managed.status ≠ .active then
              (updatedExisting ++ [managed], evaluations)
            else
              let evaluation := evaluateResolvedCompletion managed observation
              (updatedExisting ++ [evaluation.updated], evaluations ++ [evaluation]))
          (updatedExisting, evaluations)).1) =
      resolvedClearanceIds updatedExisting ++ resolvedClearanceIds remaining := by
  induction remaining generalizing updatedExisting evaluations with
  | nil =>
      simp [resolvedClearanceIds]
  | cons head tail ih =>
      by_cases hInactive : head.status ≠ .active
      · simpa [List.foldl, hInactive, resolvedClearanceIds, List.append_assoc] using
          ih (updatedExisting ++ [head]) evaluations
      · have hHeadId : (evaluateResolvedCompletion head observation).updated.source.id = head.source.id :=
          evaluateResolvedCompletion_updated_source_id head observation
        simpa [List.foldl, hInactive, resolvedClearanceIds, List.append_assoc, hHeadId] using
          ih
            (updatedExisting ++ [(evaluateResolvedCompletion head observation).updated])
            (evaluations ++ [evaluateResolvedCompletion head observation])

theorem evaluateActiveResolvedCompletions_preserves_ids
    (existing : List ManagedResolvedClearance)
    (observation : CompletionObservation) :
    resolvedClearanceIds (evaluateActiveResolvedCompletions existing observation).1 =
      resolvedClearanceIds existing := by
  simpa [evaluateActiveResolvedCompletions] using
    evaluateActiveResolvedCompletions_preserves_ids_from existing [] [] observation

theorem evaluateActiveResolvedCompletions_preserves_compatibility_from
    (remaining updatedExisting : List ManagedResolvedClearance)
    (evaluations : List ResolvedCompletionEvaluation)
    (observation : CompletionObservation)
    (hUpdated : AllResolvedCompatible updatedExisting)
    (hRemaining : AllResolvedCompatible remaining) :
    AllResolvedCompatible
        ((remaining.foldl
          (fun (acc : List ManagedResolvedClearance × List ResolvedCompletionEvaluation) managed =>
            let updatedExisting := acc.1
            let evaluations := acc.2
            if managed.status ≠ .active then
              (updatedExisting ++ [managed], evaluations)
            else
              let evaluation := evaluateResolvedCompletion managed observation
              (updatedExisting ++ [evaluation.updated], evaluations ++ [evaluation]))
          (updatedExisting, evaluations)).1) := by
  induction remaining generalizing updatedExisting evaluations with
  | nil =>
      simpa [List.foldl] using hUpdated
  | cons head tail ih =>
      have hHeadCompat : head.resolved.allStepsCompatible = true :=
        hRemaining head (by simp)
      have hTailCompat : AllResolvedCompatible tail := by
        intro managed hMem
        exact hRemaining managed (by simp [hMem])
      by_cases hInactive : head.status ≠ .active
      · have hUpdated' : AllResolvedCompatible (updatedExisting ++ [head]) := by
          exact allResolvedCompatible_snoc updatedExisting head hUpdated hHeadCompat
        simpa [List.foldl, hInactive] using
          ih (updatedExisting ++ [head]) evaluations hUpdated' hTailCompat
      · have hHeadUpdatedCompat :
            (evaluateResolvedCompletion head observation).updated.resolved.allStepsCompatible = true := by
          simpa [evaluateResolvedCompletion_updated_allStepsCompatible] using hHeadCompat
        have hUpdated' :
            AllResolvedCompatible (updatedExisting ++ [(evaluateResolvedCompletion head observation).updated]) := by
          exact allResolvedCompatible_snoc
            updatedExisting
            (evaluateResolvedCompletion head observation).updated
            hUpdated
            hHeadUpdatedCompat
        simpa [List.foldl, hInactive] using
          ih
            (updatedExisting ++ [(evaluateResolvedCompletion head observation).updated])
            (evaluations ++ [evaluateResolvedCompletion head observation])
            hUpdated'
            hTailCompat

theorem evaluateActiveResolvedCompletions_preserves_compatibility
    (existing : List ManagedResolvedClearance)
    (observation : CompletionObservation)
    (hCompat : AllResolvedCompatible existing) :
    AllResolvedCompatible (evaluateActiveResolvedCompletions existing observation).1 := by
  have hSeed : AllResolvedCompatible ([] : List ManagedResolvedClearance) := by
    intro managed hMem
    cases hMem
  simpa [evaluateActiveResolvedCompletions] using
    evaluateActiveResolvedCompletions_preserves_compatibility_from existing [] [] observation hSeed hCompat

theorem evaluateActiveResolvedCompletions_preserves_unique_ids
    (existing : List ManagedResolvedClearance)
    (observation : CompletionObservation)
    (hUnique : UniqueResolvedClearanceIds existing) :
    UniqueResolvedClearanceIds (evaluateActiveResolvedCompletions existing observation).1 := by
  rw [UniqueResolvedClearanceIds, evaluateActiveResolvedCompletions_preserves_ids]
  exact hUnique

theorem reconcileResolvedClearances_preserves_unique_ids
    (existing : List ManagedResolvedClearance)
    (observation : CompletionObservation := {})
    (conditionEvaluator : ConditionEvaluator := fun _ _ => false)
    (hUnique : UniqueResolvedClearanceIds existing) :
    UniqueResolvedClearanceIds
        (reconcileResolvedClearances existing observation conditionEvaluator).clearances ∧
      UniqueResolvedClearanceIds
        (reconcileResolvedClearances existing observation conditionEvaluator).terminalClearances := by
  have hAfterCompletionUnique :=
    evaluateActiveResolvedCompletions_preserves_unique_ids existing observation hUnique
  simpa [reconcileResolvedClearances] using
    activatePendingResolvedFrom_preserves_unique_ids
      conditionEvaluator
      (pendingResolvedConditionalIds (evaluateActiveResolvedCompletions existing observation).1)
      (evaluateActiveResolvedCompletions existing observation).1
      []
      []
      []
      hAfterCompletionUnique

theorem reconcileResolvedClearances_preserves_wellFormed
    (existing : List ManagedResolvedClearance)
    (observation : CompletionObservation := {})
    (conditionEvaluator : ConditionEvaluator := fun _ _ => false)
    (hWellFormed : WellFormedResolvedSet existing) :
    WellFormedResolvedSet
        (reconcileResolvedClearances existing observation conditionEvaluator).clearances ∧
      WellFormedResolvedSet
        (reconcileResolvedClearances existing observation conditionEvaluator).terminalClearances := by
  rcases hWellFormed with ⟨hUnique, hCompat⟩
  have hUniquePreserved :=
    reconcileResolvedClearances_preserves_unique_ids existing observation conditionEvaluator hUnique
  have hAfterCompletionCompat :=
    evaluateActiveResolvedCompletions_preserves_compatibility existing observation hCompat
  have hCompatPreserved :
      AllResolvedCompatible
          (reconcileResolvedClearances existing observation conditionEvaluator).clearances ∧
        AllResolvedCompatible
          (reconcileResolvedClearances existing observation conditionEvaluator).terminalClearances := by
    simpa [reconcileResolvedClearances] using
      activatePendingResolvedFrom_preserves_compatibility
        conditionEvaluator
        (pendingResolvedConditionalIds (evaluateActiveResolvedCompletions existing observation).1)
        (evaluateActiveResolvedCompletions existing observation).1
        []
        []
        []
        hAfterCompletionCompat
  constructor
  · exact ⟨hUniquePreserved.1, hCompatPreserved.1⟩
  · exact ⟨hUniquePreserved.2, hCompatPreserved.2⟩

theorem activatePendingResolvedFrom_falseEvaluator_partition
    (ids : List ClearanceId)
    (working : List ManagedResolvedClearance) :
    let result := activatePendingResolvedFrom (fun _ _ => false) ids working [] [] []
    result.clearances = working.filter (fun managed => !(statusTerminal managed.status)) ∧
      result.terminalClearances = working.filter (fun managed => statusTerminal managed.status) ∧
      result.activatedClearances = [] ∧
      result.fullySuperseded = [] ∧
      result.partiallySuperseded = [] := by
  induction ids generalizing working with
  | nil =>
      simp [activatePendingResolvedFrom]
  | cons clearanceId tail ih =>
      unfold activatePendingResolvedFrom
      split
      · simp [ih]
      · rename_i current hFind
        cases hCondition : current.source.condition <;> simp [ih]

theorem reconcileResolvedClearances_falseEvaluator_no_activations
    (existing : List ManagedResolvedClearance)
    (observation : CompletionObservation := {}) :
    let reconciliation := reconcileResolvedClearances existing observation (fun _ _ => false)
    reconciliation.activatedClearances = [] ∧
      reconciliation.fullySuperseded = [] ∧
      reconciliation.partiallySuperseded = [] := by
  have hPartition :=
    activatePendingResolvedFrom_falseEvaluator_partition
      (pendingResolvedConditionalIds (evaluateActiveResolvedCompletions existing observation).1)
      (evaluateActiveResolvedCompletions existing observation).1
  simp [reconcileResolvedClearances, hPartition]

theorem reconcileResolvedClearances_falseEvaluator_partition
    (existing : List ManagedResolvedClearance)
    (observation : CompletionObservation := {}) :
    let completionPass := evaluateActiveResolvedCompletions existing observation
    let reconciliation := reconcileResolvedClearances existing observation (fun _ _ => false)
    reconciliation.clearances = completionPass.1.filter (fun managed => !(statusTerminal managed.status)) ∧
      reconciliation.terminalClearances =
        completionPass.1.filter (fun managed => statusTerminal managed.status) ∧
      reconciliation.completionEvaluations = completionPass.2 ∧
      reconciliation.activatedClearances = [] ∧
      reconciliation.fullySuperseded = [] ∧
      reconciliation.partiallySuperseded = [] := by
  have hPartition :=
    activatePendingResolvedFrom_falseEvaluator_partition
      (pendingResolvedConditionalIds (evaluateActiveResolvedCompletions existing observation).1)
      (evaluateActiveResolvedCompletions existing observation).1
  simp [reconcileResolvedClearances, hPartition]

theorem reconcileResolvedClearances_of_no_pending_conditionals
    (existing : List ManagedResolvedClearance)
    (observation : CompletionObservation := {})
    (conditionEvaluator : ConditionEvaluator := fun _ _ => false)
    (hNoPending :
      pendingResolvedConditionalIds (evaluateActiveResolvedCompletions existing observation).1 = []) :
    let completionPass := evaluateActiveResolvedCompletions existing observation
    let reconciliation := reconcileResolvedClearances existing observation conditionEvaluator
    reconciliation.clearances = completionPass.1.filter (fun managed => !(statusTerminal managed.status)) ∧
      reconciliation.terminalClearances =
        completionPass.1.filter (fun managed => statusTerminal managed.status) ∧
      reconciliation.completionEvaluations = completionPass.2 ∧
      reconciliation.activatedClearances = [] ∧
      reconciliation.fullySuperseded = [] ∧
      reconciliation.partiallySuperseded = [] := by
  simp [reconcileResolvedClearances, hNoPending, activatePendingResolvedFrom]

theorem reconcileResolvedClearances_activated_wellStaged
    (existing : List ManagedResolvedClearance)
    (observation : CompletionObservation := {})
    (conditionEvaluator : ConditionEvaluator := fun _ _ => false) :
    AllResolvedActivationsWellStaged
      (reconcileResolvedClearances existing observation conditionEvaluator).activatedClearances := by
  have hSeed : AllResolvedActivationsWellStaged ([] : List ResolvedConditionActivation) := by
    intro activation hMem
    cases hMem
  simpa [reconcileResolvedClearances] using
    activatePendingResolvedFrom_preserves_wellStagedActivations
      conditionEvaluator
      (pendingResolvedConditionalIds (evaluateActiveResolvedCompletions existing observation).1)
      (evaluateActiveResolvedCompletions existing observation).1
      []
      []
      []
      hSeed

def sampleResolvedConditionalCross : ResolvedClearance :=
  { source :=
      { id := "COND-CROSS"
        aircraft := "TEST123"
        content := .single (.crossRunway "TEST123" "RWY-09")
        domain := .ground
        issuedBy := "CTRL-1"
        issuedAt := 1
        status := .issued
        condition := some (.afterTraffic (.byDescription "landing 737") .landing) }
    steps :=
      [ compileResolvedStep
          0
          .ground
          (.crossRunway "TEST123" "RWY-09")
          (.crossing { runway := "RWY-09", crossingPoint := "X-09" })
          rfl ] }

def sampleResolvedConditionalLineUp : ResolvedClearance :=
  { source :=
      { id := "COND-LUP"
        aircraft := "TEST123"
        content := .single (.lineUpAndWait "TEST123" "RWY-09")
        domain := .runway
        issuedBy := "CTRL-1"
        issuedAt := 2
        status := .issued
        condition := some (.afterTraffic (.byDescription "landing 737") .landing) }
    steps :=
      [ compileResolvedStep
          0
          .runway
          (.lineUpAndWait "TEST123" "RWY-09")
          .plain
          rfl ] }

def sampleConditionalActivationOrderState : List ManagedResolvedClearance :=
  (admitResolvedClearance
      (admitResolvedClearance [] sampleResolvedConditionalCross).clearances
      sampleResolvedConditionalLineUp).clearances

example :
    activatedResolvedIds
      (reconcileResolvedClearances
        sampleConditionalActivationOrderState
        {}
        (fun _ _ => true)).activatedClearances =
      ["COND-CROSS", "COND-LUP"] := by
  native_decide

example :
    (evaluateResolvedCompletion
        sampleManagedResolvedRouteFrequency
        sampleResolvedRouteFrequencyObservation).updated.status = .completed := by
  native_decide

example :
    (admitResolvedClearance [] sampleResolvedRouteFrequency).incoming.status = .active := by
  native_decide

end Greenfield
end CertifiedAtc
