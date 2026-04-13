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

def stageIncomingResolvedClearance
    (clearance : ResolvedClearance) :
    ManagedResolvedClearance :=
  let staged := stageIncomingClearance clearance.source
  { resolved := clearance.withSource staged.clearance
    suppressedDomains := staged.suppressedDomains }

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
    match step.completionCategory with
    | some .persistent => .notApplicable
    | some .onActivation => .complete
    | _ =>
        match observedResolvedStepCompletion? observation step with
        | some result => result
        | none => .notComplete

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
    List ManagedResolvedClearance →
    ResolvedClearanceReconciliation
  | [], working, activations, terminalSeed, fullySuperseded, partiallySuperseded =>
      let clearances := working.filter (fun managed => !(statusTerminal managed.status))
      let terminalClearances :=
        (terminalSeed ++ working.filter (fun managed => statusTerminal managed.status)).eraseDups
      { clearances := clearances
        terminalClearances := terminalClearances
        completionEvaluations := []
        activatedClearances := activations.reverse
        fullySuperseded := fullySuperseded.reverse
        partiallySuperseded := partiallySuperseded.reverse }
  | clearanceId :: tail, working, activations, terminalSeed, fullySuperseded, partiallySuperseded =>
      match findResolvedById working clearanceId with
      | none =>
          activatePendingResolvedFrom conditionEvaluator tail working activations terminalSeed fullySuperseded partiallySuperseded
      | some current =>
          match current.source.condition with
          | none =>
              activatePendingResolvedFrom conditionEvaluator tail working activations terminalSeed fullySuperseded partiallySuperseded
          | some condition =>
              if !(conditionEvaluator current.aircraft condition) then
                activatePendingResolvedFrom conditionEvaluator tail working activations terminalSeed fullySuperseded partiallySuperseded
              else
                let activated := current.withStatus .active
                let others := removeResolvedById working activated.source.id
                let supersession := applyIncomingResolvedSupersession others activated
                let nextWorking := supersession.updatedExisting ++ [activated]
                activatePendingResolvedFrom conditionEvaluator tail nextWorking
                  ({ before := current, after := activated } :: activations)
                  terminalSeed
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
    activatePendingResolvedFrom conditionEvaluator (pendingResolvedConditionalIds working) working [] [] [] []
  { activations with completionEvaluations := completionEvaluations }

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
