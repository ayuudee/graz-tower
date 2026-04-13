import CertifiedAtc.GreenfieldModel

namespace CertifiedAtc
namespace Greenfield

/--
`GreenfieldLifecycle` is the abstract active-clearance state machine above the
current greenfield model shape.

It intentionally works over the Kotlin-aligned `StructuredClearance` surface
and an abstract completion oracle that reports which step indices are satisfied
right now. That keeps lifecycle reasoning aligned to the runtime engine without
pulling world resolution or geometry into Lean at this layer.

The unchecked staging/admission functions below assume the input clearances are
already normalized by `normalizeConditionalEnvelope`. Checked wrappers are
provided for the boundary where that assumption is not yet established.
-/

structure CompiledStep where
  index : Nat
  instruction : AtcInstruction
  domain : ClearanceDomain
  timing : Option InstructionTiming
  completionCategory : Option CompletionCategory
  deriving DecidableEq, Repr

def compiledStepsFrom
    (clearanceDomain : ClearanceDomain) :
    List (Nat × AtcInstruction) → List CompiledStep
  | [] => []
  | (index, instruction) :: tail =>
      { index := index
        instruction := instruction
        domain := (instructionDomain? instruction).getD clearanceDomain
        timing := instructionTiming? instruction
        completionCategory := instructionCompletionCategory? instruction } ::
      compiledStepsFrom clearanceDomain tail

def compiledSteps (clearance : StructuredClearance) : List CompiledStep :=
  compiledStepsFrom clearance.domain (indexedSteps (structuredInstructions clearance))

def clearanceCompletedSteps : StructuredClearance → UniqueSet Nat
  | { content := .single _, .. } => {}
  | { content := .compound content, .. } => content.completedSteps

def clearanceStepDomains (clearance : StructuredClearance) : UniqueSet ClearanceDomain :=
  UniqueSet.ofList ((compiledSteps clearance).map CompiledStep.domain)

def clearanceSupersedesDomains (clearance : StructuredClearance) : UniqueSet ClearanceDomain :=
  UniqueSet.ofList ((structuredInstructions clearance).foldr (fun instruction acc => instructionSupersedesIn instruction ++ acc) [])

def clearanceWithStatus (clearance : StructuredClearance) (status : CertifiedAtc.ClearanceStatus) :
    StructuredClearance :=
  { clearance with status := status }

def withCompletedSteps
    (clearance : StructuredClearance)
    (completedSteps : UniqueSet Nat) :
    StructuredClearance :=
  match clearance.content with
  | .single _ => clearance
  | .compound content =>
      { clearance with
          content := .compound { content with completedSteps := completedSteps } }

structure ManagedClearance where
  clearance : StructuredClearance
  suppressedDomains : UniqueSet ClearanceDomain := {}
  deriving DecidableEq, Repr

def ManagedClearance.source (managed : ManagedClearance) : StructuredClearance :=
  managed.clearance

def ManagedClearance.aircraft (managed : ManagedClearance) : AircraftId :=
  managed.clearance.aircraft

def ManagedClearance.status (managed : ManagedClearance) : CertifiedAtc.ClearanceStatus :=
  managed.clearance.status

def ManagedClearance.stepDomains (managed : ManagedClearance) : UniqueSet ClearanceDomain :=
  clearanceStepDomains managed.clearance

def ManagedClearance.effectiveDomains (managed : ManagedClearance) : UniqueSet ClearanceDomain :=
  UniqueSet.diff managed.stepDomains managed.suppressedDomains

def ManagedClearance.withClearance
    (managed : ManagedClearance)
    (clearance : StructuredClearance) :
    ManagedClearance :=
  { managed with clearance := clearance }

def ManagedClearance.withStatus
    (managed : ManagedClearance)
    (status : CertifiedAtc.ClearanceStatus) :
    ManagedClearance :=
  managed.withClearance (clearanceWithStatus managed.clearance status)

def ManagedClearance.suppress
    (managed : ManagedClearance)
    (domains : UniqueSet ClearanceDomain) :
    ManagedClearance :=
  { managed with
      suppressedDomains := UniqueSet.union managed.suppressedDomains domains }

def ManagedClearance.clearSuppression (managed : ManagedClearance) : ManagedClearance :=
  { managed with suppressedDomains := {} }

def ManagedClearance.effectiveSteps (managed : ManagedClearance) : List CompiledStep :=
  (compiledSteps managed.clearance).filter
    (fun step => step.domain ∉ managed.suppressedDomains)

def ManagedClearance.requiredCompletionStepIndices
    (managed : ManagedClearance) : List Nat :=
  (compiledSteps managed.clearance).filterMap fun step =>
    if step.domain ∈ managed.suppressedDomains then
      none
    else if step.completionCategory = some .persistent then
      none
    else
      some step.index

def statusTerminal : CertifiedAtc.ClearanceStatus → Bool
  | .completed => true
  | .superseded => true
  | .cancelled => true
  | _ => false

def statusSupersedable : CertifiedAtc.ClearanceStatus → Bool
  | .issued => true
  | .readbackPending => true
  | .conditionPending => true
  | .active => true
  | _ => false

def stageIncomingClearance (clearance : StructuredClearance) : ManagedClearance :=
  let nextStatus :=
    if statusTerminal clearance.status then
      clearance.status
    else if clearance.condition.isSome then
      .conditionPending
    else
      match clearance.status with
      | .issued => .active
      | .readbackPending => .active
      | .conditionPending => .active
      | status => status
  { clearance := clearanceWithStatus clearance nextStatus }

def stageIncomingClearanceChecked
    (clearance : StructuredClearance) :
    Except NormalizeError ManagedClearance :=
  match normalizeConditionalEnvelope clearance with
  | .ok normalized => .ok (stageIncomingClearance normalized)
  | .error error => .error error

def domainOverlap
    (existing incoming : UniqueSet ClearanceDomain) :
    UniqueSet ClearanceDomain :=
  UniqueSet.inter existing incoming

structure PartiallySupersededClearance where
  clearance : ManagedClearance
  suppressedDomains : UniqueSet ClearanceDomain
  deriving DecidableEq, Repr

def PartiallySupersededClearance.remainingDomains
    (entry : PartiallySupersededClearance) : UniqueSet ClearanceDomain :=
  UniqueSet.diff entry.clearance.effectiveDomains entry.suppressedDomains

def PartiallySupersededClearance.remainingSteps
    (entry : PartiallySupersededClearance) : List CompiledStep :=
  entry.clearance.effectiveSteps.filter (fun step => step.domain ∉ entry.suppressedDomains)

structure SupersessionDecision where
  incoming : StructuredClearance
  fullySuperseded : List ManagedClearance
  partiallySuperseded : List PartiallySupersededClearance
  unaffected : List ManagedClearance
  deriving DecidableEq, Repr

def determineSupersessionFrom
    (incoming : StructuredClearance)
    (supersedesDomains : UniqueSet ClearanceDomain) :
    List ManagedClearance →
    List ManagedClearance →
    List PartiallySupersededClearance →
    List ManagedClearance →
    SupersessionDecision
  | [], fullySuperseded, partiallySuperseded, unaffected =>
      { incoming := incoming
        fullySuperseded := fullySuperseded.reverse
        partiallySuperseded := partiallySuperseded.reverse
        unaffected := unaffected.reverse }
  | managed :: tail, fullySuperseded, partiallySuperseded, unaffected =>
      if !(statusSupersedable managed.status) || managed.aircraft ≠ incoming.aircraft then
        determineSupersessionFrom incoming supersedesDomains tail
          fullySuperseded partiallySuperseded (managed :: unaffected)
      else
        let overlap := domainOverlap managed.effectiveDomains supersedesDomains
        if overlap.isEmpty then
          determineSupersessionFrom incoming supersedesDomains tail
            fullySuperseded partiallySuperseded (managed :: unaffected)
        else if overlap = managed.effectiveDomains then
          determineSupersessionFrom incoming supersedesDomains tail
            (managed :: fullySuperseded) partiallySuperseded unaffected
        else
          determineSupersessionFrom incoming supersedesDomains tail
            fullySuperseded
            ({ clearance := managed, suppressedDomains := overlap } :: partiallySuperseded)
            unaffected

def determineSupersession
    (incoming : StructuredClearance)
    (existing : List ManagedClearance) :
    SupersessionDecision :=
  determineSupersessionFrom incoming (clearanceSupersedesDomains incoming) existing [] [] []

structure SupersessionApplication where
  updatedExisting : List ManagedClearance
  fullySuperseded : List ManagedClearance
  partiallySuperseded : List ManagedClearance
  deriving DecidableEq, Repr

def applyIncomingSupersessionFrom
    (incoming : ManagedClearance) :
    List ManagedClearance →
    List ManagedClearance →
    List ManagedClearance →
    List ManagedClearance →
    SupersessionApplication
  | [], updatedExisting, fullySuperseded, partiallySuperseded =>
      { updatedExisting := updatedExisting.reverse
        fullySuperseded := fullySuperseded.reverse
        partiallySuperseded := partiallySuperseded.reverse }
  | managed :: tail, updatedExisting, fullySuperseded, partiallySuperseded =>
      if !(statusSupersedable managed.status) || managed.aircraft ≠ incoming.aircraft then
        applyIncomingSupersessionFrom incoming tail
          (managed :: updatedExisting) fullySuperseded partiallySuperseded
      else
        let overlap := domainOverlap managed.effectiveDomains (clearanceSupersedesDomains incoming.clearance)
        if overlap.isEmpty then
          applyIncomingSupersessionFrom incoming tail
            (managed :: updatedExisting) fullySuperseded partiallySuperseded
        else if overlap = managed.effectiveDomains then
          let superseded := managed.clearSuppression.withStatus .superseded
          applyIncomingSupersessionFrom incoming tail
            (superseded :: updatedExisting)
            (superseded :: fullySuperseded)
            partiallySuperseded
        else
          let suppressed := managed.suppress overlap
          applyIncomingSupersessionFrom incoming tail
            (suppressed :: updatedExisting)
            fullySuperseded
            (suppressed :: partiallySuperseded)

def applyIncomingSupersession
    (existing : List ManagedClearance)
    (incoming : ManagedClearance) :
    SupersessionApplication :=
  applyIncomingSupersessionFrom incoming existing [] [] []

structure ClearanceAdmission where
  incoming : ManagedClearance
  clearances : List ManagedClearance
  terminalClearances : List ManagedClearance
  fullySuperseded : List ManagedClearance
  partiallySuperseded : List ManagedClearance
  deriving DecidableEq, Repr

def admitClearance
    (existing : List ManagedClearance)
    (incoming : StructuredClearance) :
    ClearanceAdmission :=
  let stagedIncoming := stageIncomingClearance incoming
  let supersession :=
    if stagedIncoming.status = .active then
      applyIncomingSupersession existing stagedIncoming
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

def admitClearanceChecked
    (existing : List ManagedClearance)
    (incoming : StructuredClearance) :
    Except NormalizeError ClearanceAdmission :=
  match normalizeConditionalEnvelope incoming with
  | .ok normalized => .ok (admitClearance existing normalized)
  | .error error => .error error

inductive CompletionResult
  | complete
  | notComplete
  | notApplicable
  deriving DecidableEq, Repr

structure StepCompletion where
  step : CompiledStep
  result : CompletionResult
  deriving DecidableEq, Repr

abbrev CompletionOracle := ManagedClearance → UniqueSet Nat
abbrev ConditionEvaluator := AircraftId → ConditionalPredicate → Bool

def stepCompletionResult
    (managed : ManagedClearance)
    (completedNow : UniqueSet Nat)
    (step : CompiledStep) :
    CompletionResult :=
  if step.domain ∈ managed.suppressedDomains then
    .notApplicable
  else
    match step.completionCategory with
    | some .persistent => .notApplicable
    | some .onActivation => .complete
    | _ =>
        if step.index ∈ completedNow then
          .complete
        else
          .notComplete

def addCompletedSteps
    (completedSteps : UniqueSet Nat)
    (newlyCompleted : UniqueSet Nat) :
    UniqueSet Nat :=
  UniqueSet.union completedSteps newlyCompleted

structure ManagedCompletionEvaluation where
  source : ManagedClearance
  updated : ManagedClearance
  stepResults : List StepCompletion
  newlyCompletedSteps : UniqueSet Nat
  isComplete : Bool
  deriving DecidableEq, Repr

def evaluateManagedCompletion
    (managed : ManagedClearance)
    (completionOracle : CompletionOracle) :
    ManagedCompletionEvaluation :=
  let completedNow := completionOracle managed
  let steps := compiledSteps managed.clearance
  let stepResults := steps.map fun step =>
    { step := step
      result := stepCompletionResult managed completedNow step }
  let existingCompleted := clearanceCompletedSteps managed.clearance
  let newlyCompletedSteps :=
    UniqueSet.ofList <|
      (stepResults.filterMap fun stepResult =>
        if stepResult.result = .complete then some stepResult.step.index else none).filter
          (fun index => index ∉ existingCompleted)
  let updatedClearance :=
    match managed.clearance.content with
    | .single _ =>
        let singleComplete :=
          match stepResults with
          | [{ result := .complete, .. }] => true
          | _ => false
        let nextStatus :=
          if singleComplete then
            CertifiedAtc.ClearanceStatus.completed
          else
            managed.clearance.status
        clearanceWithStatus managed.clearance nextStatus
    | .compound _ =>
        let updatedCompleted := addCompletedSteps existingCompleted newlyCompletedSteps
        let updatedClearance := withCompletedSteps managed.clearance updatedCompleted
        let isComplete :=
          managed.requiredCompletionStepIndices.all (fun index => index ∈ updatedCompleted)
        let nextStatus :=
          if isComplete then
            CertifiedAtc.ClearanceStatus.completed
          else
            updatedClearance.status
        clearanceWithStatus updatedClearance nextStatus
  let updatedManaged := managed.withClearance updatedClearance
  { source := managed
    updated := updatedManaged
    stepResults := stepResults
    newlyCompletedSteps := newlyCompletedSteps
    isComplete := updatedManaged.status = .completed }

def insertByIssuedAt
    (managed : ManagedClearance) :
    List ManagedClearance → List ManagedClearance
  | [] => [managed]
  | head :: tail =>
      if managed.source.issuedAt ≤ head.source.issuedAt then
        managed :: head :: tail
      else
        head :: insertByIssuedAt managed tail

def sortByIssuedAt : List ManagedClearance → List ManagedClearance
  | [] => []
  | head :: tail => insertByIssuedAt head (sortByIssuedAt tail)

def IssuedAtOrdered : List ManagedClearance → Prop
  | [] => True
  | [_] => True
  | first :: second :: tail =>
      first.source.issuedAt ≤ second.source.issuedAt ∧ IssuedAtOrdered (second :: tail)

structure ConditionActivation where
  before : ManagedClearance
  after : ManagedClearance
  deriving DecidableEq, Repr

structure ClearanceReconciliation where
  clearances : List ManagedClearance
  terminalClearances : List ManagedClearance
  completionEvaluations : List ManagedCompletionEvaluation
  activatedClearances : List ConditionActivation
  fullySuperseded : List ManagedClearance
  partiallySuperseded : List ManagedClearance
  deriving DecidableEq, Repr

def evaluateActiveCompletions
    (existing : List ManagedClearance)
    (completionOracle : CompletionOracle) :
    List ManagedClearance × List ManagedCompletionEvaluation :=
  existing.foldl
    (fun (acc : List ManagedClearance × List ManagedCompletionEvaluation) managed =>
      let updatedExisting := acc.1
      let evaluations := acc.2
      if managed.status ≠ .active then
        (updatedExisting ++ [managed], evaluations)
      else
        let evaluation := evaluateManagedCompletion managed completionOracle
        (updatedExisting ++ [evaluation.updated], evaluations ++ [evaluation]))
    ([], [])

def findById
    (clearances : List ManagedClearance)
    (id : ClearanceId) :
    Option ManagedClearance :=
  clearances.find? (fun managed => managed.source.id = id)

def removeById
    (clearances : List ManagedClearance)
    (id : ClearanceId) :
    List ManagedClearance :=
  clearances.filter (fun managed => managed.source.id ≠ id)

def pendingConditionalIds (clearances : List ManagedClearance) : List ClearanceId :=
  (sortByIssuedAt clearances).filterMap fun managed =>
    if managed.status = .conditionPending && managed.source.condition.isSome then
      some managed.source.id
    else
      none

def activatePendingFrom
    (conditionEvaluator : ConditionEvaluator) :
    List ClearanceId →
    List ManagedClearance →
    List ConditionActivation →
    List ManagedClearance →
    List ManagedClearance →
    List ManagedClearance →
    ClearanceReconciliation
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
      match findById working clearanceId with
      | none =>
          activatePendingFrom conditionEvaluator tail working activations terminalSeed fullySuperseded partiallySuperseded
      | some current =>
          match current.source.condition with
          | none =>
              activatePendingFrom conditionEvaluator tail working activations terminalSeed fullySuperseded partiallySuperseded
          | some condition =>
              if !(conditionEvaluator current.aircraft condition) then
                activatePendingFrom conditionEvaluator tail working activations terminalSeed fullySuperseded partiallySuperseded
              else
                let activated := current.withStatus .active
                let others := removeById working activated.source.id
                let supersession := applyIncomingSupersession others activated
                let nextWorking := supersession.updatedExisting ++ [activated]
                activatePendingFrom conditionEvaluator tail nextWorking
                  ({ before := current, after := activated } :: activations)
                  terminalSeed
                  (supersession.fullySuperseded ++ fullySuperseded)
                  (supersession.partiallySuperseded ++ partiallySuperseded)

def reconcileClearances
    (existing : List ManagedClearance)
    (completionOracle : CompletionOracle := fun _ => {})
    (conditionEvaluator : ConditionEvaluator := fun _ _ => false) :
    ClearanceReconciliation :=
  let completionPass := evaluateActiveCompletions existing completionOracle
  let working := completionPass.1
  let completionEvaluations := completionPass.2
  let activations :=
    activatePendingFrom conditionEvaluator (pendingConditionalIds working) working [] [] [] []
  { activations with completionEvaluations := completionEvaluations }

theorem stageIncomingClearance_condition_pending
    (clearance : StructuredClearance)
    (hCondition : clearance.condition.isSome = true)
    (hTerminal : statusTerminal clearance.status = false) :
    (stageIncomingClearance clearance).status = .conditionPending := by
  simp [stageIncomingClearance, ManagedClearance.status, clearanceWithStatus, hCondition, hTerminal]

theorem stageIncomingClearanceChecked_rejects_wrapped_conditional_compound
    (clearance : StructuredClearance)
    (content : CompoundClearanceContent)
    (hWrapped : anyWrappedConditionalStep content.steps = true) :
    stageIncomingClearanceChecked { clearance with content := .compound content } =
      .error .conditionalStepNotSupported := by
  cases clearance
  simp [stageIncomingClearanceChecked, normalizeConditionalEnvelope, hWrapped]

theorem stageIncomingClearance_active_of_unconditional_issued
    (clearance : StructuredClearance)
    (hCondition : clearance.condition.isSome = false) :
    (stageIncomingClearance { clearance with status := .issued }).status = .active := by
  simp [stageIncomingClearance, ManagedClearance.status, clearanceWithStatus, hCondition, statusTerminal]

theorem admitClearance_condition_pending_has_no_supersession
    (existing : List ManagedClearance)
    (incoming : StructuredClearance)
    (hCondition : incoming.condition.isSome = true)
    (hTerminal : statusTerminal incoming.status = false) :
    let admission := admitClearance existing incoming
    admission.fullySuperseded = [] ∧ admission.partiallySuperseded = [] := by
  simp [admitClearance, stageIncomingClearance, ManagedClearance.status, clearanceWithStatus, hCondition, hTerminal]

theorem stepCompletionResult_onActivation_complete
    (managed : ManagedClearance)
    (completedNow : UniqueSet Nat)
    (step : CompiledStep)
    (hSuppressed : step.domain ∉ managed.suppressedDomains)
    (hCategory : step.completionCategory = some .onActivation) :
    stepCompletionResult managed completedNow step = .complete := by
  simp [stepCompletionResult, hSuppressed, hCategory]

theorem evaluateManagedCompletion_onActivation_single_completes
    (aircraft : AircraftId)
    (controller : ControllerId)
    (clearanceId : ClearanceId)
    (issuedAt : TickNumber)
    (domain : ClearanceDomain)
    (instruction : AtcInstruction)
    (hCategory : instructionCompletionCategory? instruction = some .onActivation) :
    let clearance : StructuredClearance :=
      { id := clearanceId
        aircraft := aircraft
        content := .single instruction
        domain := domain
        issuedBy := controller
        issuedAt := issuedAt
        status := .active
        condition := none }
    let evaluation :=
      evaluateManagedCompletion { clearance := clearance } (fun _ => {})
    evaluation.updated.status = .completed ∧ evaluation.isComplete = true := by
  cases hDomain : instructionDomain? instruction <;>
    simp [evaluateManagedCompletion, ManagedClearance.status, clearanceCompletedSteps,
      ManagedClearance.withClearance, stepCompletionResult, clearanceWithStatus, compiledSteps, compiledStepsFrom,
      structuredInstructions, contentInstructions, indexedSteps, enumerateFrom, hCategory, hDomain]
  · rfl
  · rfl

def sampleSuppressedFrequencyCompound : ManagedClearance :=
  { clearance :=
      { id := "CLR-ROUTE-FREQ"
        aircraft := "TEST123"
        content := .compound
          { steps :=
              [ .clearedTo "TEST123" "HOLD" (some (.viaSid "SID1"))
              , .contactFrequency "TEST123" .approach none ] }
        domain := .route
        issuedBy := "CTRL-1"
        issuedAt := 1
        status := .active
        condition := none }
    suppressedDomains := UniqueSet.singleton .frequency }

example :
    (evaluateManagedCompletion sampleSuppressedFrequencyCompound (fun _ => UniqueSet.singleton 0)).updated.status =
      .completed := by
  native_decide

example :
    clearanceCompletedSteps
        (evaluateManagedCompletion sampleSuppressedFrequencyCompound (fun _ => UniqueSet.singleton 0)).updated.clearance =
      UniqueSet.singleton 0 := by
  native_decide

theorem mem_addCompletedSteps_of_mem
    {completedSteps newlyCompleted : UniqueSet Nat}
    {index : Nat}
    (hMem : index ∈ completedSteps) :
    index ∈ addCompletedSteps completedSteps newlyCompleted := by
  have hFold :
      ∀ {pending : List Nat} {seed : UniqueSet Nat},
        index ∈ seed →
          index ∈ pending.foldl UniqueSet.insert seed := by
    intro pending
    induction pending with
    | nil =>
        intro seed hSeed
        simpa using hSeed
    | cons head tail ih =>
        intro seed hSeed
        simp
        exact ih (UniqueSet.mem_insert_of_mem hSeed)
  simpa [addCompletedSteps, UniqueSet.union] using
    (hFold (pending := newlyCompleted.values) (seed := completedSteps) hMem)

theorem insertByIssuedAt_preserves_order
    (managed : ManagedClearance)
    {clearances : List ManagedClearance}
    (hOrdered : IssuedAtOrdered clearances) :
    IssuedAtOrdered (insertByIssuedAt managed clearances) := by
  induction clearances with
  | nil =>
      simp [insertByIssuedAt, IssuedAtOrdered]
  | cons head tail ih =>
      cases tail with
      | nil =>
          by_cases hInsert : managed.source.issuedAt ≤ head.source.issuedAt
          · simp [insertByIssuedAt, IssuedAtOrdered, hInsert]
          · have hHeadManaged : head.source.issuedAt ≤ managed.source.issuedAt := by
              exact Nat.le_of_lt (Nat.lt_of_not_ge hInsert)
            simp [insertByIssuedAt, IssuedAtOrdered, hInsert, hHeadManaged]
      | cons second rest =>
          simp [IssuedAtOrdered] at hOrdered
          rcases hOrdered with ⟨hHeadSecond, hTail⟩
          by_cases hInsert : managed.source.issuedAt ≤ head.source.issuedAt
          · simp [insertByIssuedAt, IssuedAtOrdered, hInsert]
            exact ⟨hHeadSecond, hTail⟩
          · have hHeadManaged : head.source.issuedAt ≤ managed.source.issuedAt := by
              exact Nat.le_of_lt (Nat.lt_of_not_ge hInsert)
            have hInsertedTail : IssuedAtOrdered (insertByIssuedAt managed (second :: rest)) :=
              ih hTail
            by_cases hInsertTail : managed.source.issuedAt ≤ second.source.issuedAt
            · simp [insertByIssuedAt, IssuedAtOrdered, hInsert, hInsertTail, hHeadManaged]
              exact hTail
            · simp [insertByIssuedAt, IssuedAtOrdered, hInsert, hInsertTail, hHeadSecond]
              simpa [insertByIssuedAt, hInsertTail, IssuedAtOrdered] using hInsertedTail

theorem sortByIssuedAt_ordered
    (clearances : List ManagedClearance) :
    IssuedAtOrdered (sortByIssuedAt clearances) := by
  induction clearances with
  | nil =>
      simp [sortByIssuedAt, IssuedAtOrdered]
  | cons head tail ih =>
      simp [sortByIssuedAt]
      exact insertByIssuedAt_preserves_order head ih

theorem issuedAtOrdered_head_le_of_mem_tail
    {head : ManagedClearance}
    {tail : List ManagedClearance}
    {managed : ManagedClearance}
    (hOrdered : IssuedAtOrdered (head :: tail))
    (hMem : managed ∈ tail) :
    head.source.issuedAt ≤ managed.source.issuedAt := by
  induction tail generalizing head with
  | nil =>
      cases hMem
  | cons second rest ih =>
      simp [IssuedAtOrdered] at hOrdered
      rcases hOrdered with ⟨hHeadSecond, hTailOrdered⟩
      simp at hMem
      rcases hMem with rfl | hRest
      · exact hHeadSecond
      · exact Nat.le_trans hHeadSecond (ih hTailOrdered hRest)

theorem issuedAtOrdered_filter
    (clearances : List ManagedClearance)
    (predicate : ManagedClearance → Bool)
    (hOrdered : IssuedAtOrdered clearances) :
    IssuedAtOrdered (clearances.filter predicate) := by
  induction clearances with
  | nil =>
      simp [IssuedAtOrdered]
  | cons head tail ih =>
      cases tail with
      | nil =>
          by_cases hHead : predicate head
          · simp [hHead, IssuedAtOrdered]
          · simp [hHead, IssuedAtOrdered]
      | cons second rest =>
          simp [IssuedAtOrdered] at hOrdered
          rcases hOrdered with ⟨hHeadSecond, hTailOrdered⟩
          by_cases hHead : predicate head
          · have hFilteredTailOrdered := ih hTailOrdered
            cases hFilteredTail : List.filter predicate (second :: rest) with
            | nil =>
                simp [hHead, hFilteredTail, IssuedAtOrdered]
            | cons first filteredRest =>
                have hMemFiltered : first ∈ List.filter predicate (second :: rest) := by
                  simp [hFilteredTail]
                have hMemTail : first ∈ second :: rest := by
                  have hFilterFacts :
                      (first = second ∨ first ∈ rest) ∧ predicate first = true := by
                    simpa using hMemFiltered
                  simpa using hFilterFacts.1
                have hHeadFirst :=
                  issuedAtOrdered_head_le_of_mem_tail
                    (head := head)
                    (tail := second :: rest)
                    (by exact ⟨hHeadSecond, hTailOrdered⟩)
                    hMemTail
                simp [hHead, hFilteredTail, IssuedAtOrdered, hHeadFirst]
                simpa [hFilteredTail] using hFilteredTailOrdered
          · simpa [hHead] using ih hTailOrdered

theorem applyIncomingSupersessionFrom_identity_of_other_aircraft
    (incoming : ManagedClearance)
    (remaining updatedExisting fullySuperseded partiallySuperseded : List ManagedClearance)
    (hOther : ∀ managed ∈ remaining, managed.aircraft ≠ incoming.aircraft) :
    applyIncomingSupersessionFrom incoming remaining updatedExisting fullySuperseded partiallySuperseded =
      { updatedExisting := updatedExisting.reverse ++ remaining
        fullySuperseded := fullySuperseded.reverse
        partiallySuperseded := partiallySuperseded.reverse } := by
  induction remaining generalizing updatedExisting fullySuperseded partiallySuperseded with
  | nil =>
      simp [applyIncomingSupersessionFrom]
  | cons head tail ih =>
      have hHead : head.aircraft ≠ incoming.aircraft := hOther head (by simp)
      have hTail : ∀ managed ∈ tail, managed.aircraft ≠ incoming.aircraft := by
        intro managed hMem
        exact hOther managed (by simp [hMem])
      simp [applyIncomingSupersessionFrom, hHead, ih _ _ _ hTail, List.reverse_cons, List.append_assoc]

theorem applyIncomingSupersession_identity_of_other_aircraft
    (existing : List ManagedClearance)
    (incoming : ManagedClearance)
    (hOther : ∀ managed ∈ existing, managed.aircraft ≠ incoming.aircraft) :
    applyIncomingSupersession existing incoming =
      { updatedExisting := existing
        fullySuperseded := []
        partiallySuperseded := [] } := by
  simpa [applyIncomingSupersession] using
    applyIncomingSupersessionFrom_identity_of_other_aircraft incoming existing [] [] [] hOther

end Greenfield
end CertifiedAtc
