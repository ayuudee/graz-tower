import CertifiedAtc.ScopedSafety

namespace CertifiedAtc
namespace Greenfield

/--
`ScopedModes` is the Milestone 7 full-brief layer.

It does not attempt to rebuild the nominal certifier stack. Instead it adds the
thinnest honest mode-aware layer above the scoped nominal issuer:

- assumption assessments classify the current regime
- regime selection is explicit and strongest-justified
- nominal guarantees are withdrawn when the assessment is no longer nominal
- a concrete degraded/emergency fallback command vocabulary is admitted
- fallback commands preserve the strongest currently justified regime

The mode monitor itself remains abstract at this layer; what is proved here is
the regime-selection and guarantee-preservation story once an assessment has
been produced.
-/

inductive AssumptionAssessment
  | nominal
  | degraded (reason : String)
  | emergency (kind : String)
  | multiFailure
  deriving DecidableEq, Repr

def selectedMode : AssumptionAssessment → Mode
  | .nominal => .normal
  | .degraded reason => .degraded reason
  | .emergency kind => .emergency kind
  | .multiFailure => .emergency "multi-failure"

def modeStrength : Mode → Nat
  | .normal => 2
  | .degraded _ => 1
  | .emergency _ => 0

def ModeCompatibleWithAssessment :
    AssumptionAssessment → Mode → Prop
  | .nominal, .normal => True
  | .degraded _, .degraded _ => True
  | .degraded _, .emergency _ => True
  | .emergency _, .emergency _ => True
  | .multiFailure, .emergency _ => True
  | _, _ => False

theorem selectedMode_compatible
    {assessment : AssumptionAssessment} :
    ModeCompatibleWithAssessment assessment (selectedMode assessment) := by
  cases assessment <;> simp [ModeCompatibleWithAssessment, selectedMode]

theorem selectedMode_strongest
    {assessment : AssumptionAssessment}
    {mode : Mode}
    (hCompatible : ModeCompatibleWithAssessment assessment mode) :
    modeStrength mode ≤ modeStrength (selectedMode assessment) := by
  cases assessment <;> cases mode <;> simp
    [ModeCompatibleWithAssessment, selectedMode, modeStrength] at hCompatible ⊢

theorem nonNominalAssessment_selects_nonNormal
    {assessment : AssumptionAssessment}
    (hNonNominal : assessment ≠ .nominal) :
    selectedMode assessment ≠ .normal := by
  cases assessment <;> simp [selectedMode] at hNonNominal ⊢

inductive FallbackDirective
  | holdPosition (target : EntityId)
  | stopTaxi (target : EntityId)
  | vacateProtectedSegment (target : EntityId)
  | cancelSurfaceClearance (target : EntityId)
  | maintainCurrentPath (target : EntityId)
  | holdOnPathSegment (target : EntityId) (edge : AirEdgeId)
  | goAround (target : EntityId)
  | emergencyLand (target : EntityId) (runway : RunwayId)
  | cancelAirClearance (target : EntityId)
  deriving DecidableEq, Repr

inductive FallbackCommand
  | freezeSurface
  | holdPosition (target : EntityId)
  | stopTaxi (target : EntityId)
  | vacateProtectedSegment (target : EntityId)
  | cancelSurfaceClearance (target : EntityId)
  | reserveRunwayExclusive (runway : RunwayId)
  | maintainCurrentPath (target : EntityId)
  | assignRecoveryPath (target : EntityId) (edge : AirEdgeId)
  | assignRecoveryBand (target : EntityId) (band : AltitudeBandId)
  | holdOnPathSegment (target : EntityId) (edge : AirEdgeId)
  | goAround (target : EntityId)
  | emergencyLand (target : EntityId) (runway : RunwayId)
  | cancelAirClearance (target : EntityId)
  | emergencyRunwayReservation (runway : RunwayId)
  | exclusiveRecoveryCorridorActivation (corridor : List AirEdgeId)
  deriving DecidableEq, Repr

def fallbackCommandAllowedIn : Mode → FallbackCommand → Bool
  | .normal, _ => false
  | .degraded _, .freezeSurface => true
  | .degraded _, .holdPosition _ => true
  | .degraded _, .stopTaxi _ => true
  | .degraded _, .vacateProtectedSegment _ => true
  | .degraded _, .cancelSurfaceClearance _ => true
  | .degraded _, .maintainCurrentPath _ => true
  | .degraded _, .holdOnPathSegment _ _ => true
  | .degraded _, .goAround _ => true
  | .degraded _, .cancelAirClearance _ => true
  | .degraded _, _ => false
  | .emergency _, _ => true

def recordOnce [DecidableEq α] (value : α) (values : List α) : List α :=
  if value ∈ values then
    values
  else
    value :: values

theorem recordOnce_preserves_nodup
    [DecidableEq α]
    {value : α}
    {values : List α}
    (hNodup : values.Nodup) :
    (recordOnce value values).Nodup := by
  unfold recordOnce
  by_cases hMem : value ∈ values
  · simp [hMem, hNodup]
  · simp [hMem, hNodup]

structure FallbackOverlay where
  surfaceFrozen : Bool := false
  directives : List FallbackDirective := []
  exclusiveRunways : List RunwayId := []
  recoveryPaths : List (EntityId × AirEdgeId) := []
  recoveryBands : List (EntityId × AltitudeBandId) := []
  recoveryCorridors : List (List AirEdgeId) := []
  deriving DecidableEq, Repr

def FallbackOverlayWellFormed
    (overlay : FallbackOverlay) : Prop :=
  overlay.directives.Nodup ∧
    overlay.exclusiveRunways.Nodup ∧
    overlay.recoveryPaths.Nodup ∧
    overlay.recoveryBands.Nodup ∧
    overlay.recoveryCorridors.Nodup

theorem FallbackOverlayWellFormed.empty :
    FallbackOverlayWellFormed {} := by
  simp [FallbackOverlayWellFormed]

def addDirective
    (overlay : FallbackOverlay)
    (directive : FallbackDirective) : FallbackOverlay :=
  { overlay with directives := recordOnce directive overlay.directives }

def addExclusiveRunway
    (overlay : FallbackOverlay)
    (runway : RunwayId) : FallbackOverlay :=
  { overlay with exclusiveRunways := recordOnce runway overlay.exclusiveRunways }

def addRecoveryPath
    (overlay : FallbackOverlay)
    (assignment : EntityId × AirEdgeId) : FallbackOverlay :=
  { overlay with recoveryPaths := recordOnce assignment overlay.recoveryPaths }

def addRecoveryBand
    (overlay : FallbackOverlay)
    (assignment : EntityId × AltitudeBandId) : FallbackOverlay :=
  { overlay with recoveryBands := recordOnce assignment overlay.recoveryBands }

def addRecoveryCorridor
    (overlay : FallbackOverlay)
    (corridor : List AirEdgeId) : FallbackOverlay :=
  { overlay with recoveryCorridors := recordOnce corridor overlay.recoveryCorridors }

theorem addDirective_preserves_wf
    {overlay : FallbackOverlay}
    (hWf : FallbackOverlayWellFormed overlay) :
    FallbackOverlayWellFormed (addDirective overlay directive) := by
  rcases hWf with ⟨hDirectives, hRunways, hPaths, hBands, hCorridors⟩
  unfold addDirective FallbackOverlayWellFormed
  simp [recordOnce_preserves_nodup hDirectives, hRunways, hPaths, hBands, hCorridors]

theorem addExclusiveRunway_preserves_wf
    {overlay : FallbackOverlay}
    (hWf : FallbackOverlayWellFormed overlay) :
    FallbackOverlayWellFormed (addExclusiveRunway overlay runway) := by
  rcases hWf with ⟨hDirectives, hRunways, hPaths, hBands, hCorridors⟩
  unfold addExclusiveRunway FallbackOverlayWellFormed
  simp [hDirectives, recordOnce_preserves_nodup hRunways, hPaths, hBands, hCorridors]

theorem addRecoveryPath_preserves_wf
    {overlay : FallbackOverlay}
    (hWf : FallbackOverlayWellFormed overlay) :
    FallbackOverlayWellFormed (addRecoveryPath overlay assignment) := by
  rcases hWf with ⟨hDirectives, hRunways, hPaths, hBands, hCorridors⟩
  unfold addRecoveryPath FallbackOverlayWellFormed
  simp [hDirectives, hRunways, recordOnce_preserves_nodup hPaths, hBands, hCorridors]

theorem addRecoveryBand_preserves_wf
    {overlay : FallbackOverlay}
    (hWf : FallbackOverlayWellFormed overlay) :
    FallbackOverlayWellFormed (addRecoveryBand overlay assignment) := by
  rcases hWf with ⟨hDirectives, hRunways, hPaths, hBands, hCorridors⟩
  unfold addRecoveryBand FallbackOverlayWellFormed
  simp [hDirectives, hRunways, hPaths, recordOnce_preserves_nodup hBands, hCorridors]

theorem addRecoveryCorridor_preserves_wf
    {overlay : FallbackOverlay}
    (hWf : FallbackOverlayWellFormed overlay) :
    FallbackOverlayWellFormed (addRecoveryCorridor overlay corridor) := by
  rcases hWf with ⟨hDirectives, hRunways, hPaths, hBands, hCorridors⟩
  unfold addRecoveryCorridor FallbackOverlayWellFormed
  simp [hDirectives, hRunways, hPaths, hBands, recordOnce_preserves_nodup hCorridors]

def applyFallbackCommandOverlay
    (overlay : FallbackOverlay)
    (command : FallbackCommand) : FallbackOverlay :=
  match command with
  | .freezeSurface =>
      { overlay with surfaceFrozen := true }
  | .holdPosition target =>
      addDirective overlay (.holdPosition target)
  | .stopTaxi target =>
      addDirective overlay (.stopTaxi target)
  | .vacateProtectedSegment target =>
      addDirective overlay (.vacateProtectedSegment target)
  | .cancelSurfaceClearance target =>
      addDirective overlay (.cancelSurfaceClearance target)
  | .reserveRunwayExclusive runway =>
      addExclusiveRunway overlay runway
  | .maintainCurrentPath target =>
      addDirective overlay (.maintainCurrentPath target)
  | .assignRecoveryPath target edge =>
      addRecoveryPath overlay (target, edge)
  | .assignRecoveryBand target band =>
      addRecoveryBand overlay (target, band)
  | .holdOnPathSegment target edge =>
      addDirective overlay (.holdOnPathSegment target edge)
  | .goAround target =>
      addDirective overlay (.goAround target)
  | .emergencyLand target runway =>
      addDirective overlay (.emergencyLand target runway)
  | .cancelAirClearance target =>
      addDirective overlay (.cancelAirClearance target)
  | .emergencyRunwayReservation runway =>
      addExclusiveRunway overlay runway
  | .exclusiveRecoveryCorridorActivation corridor =>
      addRecoveryCorridor overlay corridor

theorem applyFallbackCommandOverlay_preserves_wf
    {overlay : FallbackOverlay}
    {command : FallbackCommand}
    (hWf : FallbackOverlayWellFormed overlay) :
    FallbackOverlayWellFormed (applyFallbackCommandOverlay overlay command) := by
  cases command <;> simp [applyFallbackCommandOverlay]
  · exact hWf
  · exact addDirective_preserves_wf hWf
  · exact addDirective_preserves_wf hWf
  · exact addDirective_preserves_wf hWf
  · exact addDirective_preserves_wf hWf
  · exact addExclusiveRunway_preserves_wf hWf
  · exact addDirective_preserves_wf hWf
  · exact addRecoveryPath_preserves_wf hWf
  · exact addRecoveryBand_preserves_wf hWf
  · exact addDirective_preserves_wf hWf
  · exact addDirective_preserves_wf hWf
  · exact addDirective_preserves_wf hWf
  · exact addDirective_preserves_wf hWf
  · exact addExclusiveRunway_preserves_wf hWf
  · exact addRecoveryCorridor_preserves_wf hWf

structure ScopedModeState where
  orchestration : OrchestrationState
  overlay : FallbackOverlay := {}
  deriving DecidableEq, Repr

def ModeComponentGuarantee
    (world : ScopedAviationWorld)
    (state : ScopedModeState) : Prop :=
  RunwayInv world.runwayKernel state.orchestration.runway ∧
    SurfaceInv world.surfaceGraph state.orchestration.surface ∧
    AirInv world.airGraph state.orchestration.air ∧
    InterfaceInv state.orchestration ∧
    FallbackOverlayWellFormed state.overlay

def NominalModeGuarantee
    (world : ScopedAviationWorld)
    (state : ScopedModeState) : Prop :=
  ScopedOrchestrationInv world state.orchestration ∧
    FallbackOverlayWellFormed state.overlay

def DegradedModeGuarantee
    (world : ScopedAviationWorld)
    (state : ScopedModeState) : Prop :=
  ∃ reason,
    state.orchestration.mode = .degraded reason ∧
    ModeComponentGuarantee world state

def EmergencyModeGuarantee
    (world : ScopedAviationWorld)
    (state : ScopedModeState) : Prop :=
  ∃ kind,
    state.orchestration.mode = .emergency kind ∧
    ModeComponentGuarantee world state

def FallbackModeGuarantee
    (world : ScopedAviationWorld)
    (state : ScopedModeState) : Prop :=
  DegradedModeGuarantee world state ∨
    EmergencyModeGuarantee world state

def FullBriefGuarantee
    (world : ScopedAviationWorld)
    (state : ScopedModeState) : Prop :=
  NominalModeGuarantee world state ∨
    FallbackModeGuarantee world state

theorem NominalModeGuarantee.component
    {world : ScopedAviationWorld}
    {state : ScopedModeState}
    (hNominal : NominalModeGuarantee world state) :
    ModeComponentGuarantee world state := by
  rcases hNominal with ⟨hScoped, hOverlay⟩
  rcases hScoped with ⟨_hNominalAssumptions, hRunway, hSurface, hAir, hInterface⟩
  exact ⟨hRunway, hSurface, hAir, hInterface, hOverlay⟩

theorem FullBriefGuarantee.component
    {world : ScopedAviationWorld}
    {state : ScopedModeState}
    (hGuarantee : FullBriefGuarantee world state) :
    ModeComponentGuarantee world state := by
  rcases hGuarantee with hNominal | hFallback
  · exact hNominal.component
  · rcases hFallback with hDegraded | hEmergency
    · rcases hDegraded with ⟨_, _, hComponent⟩
      exact hComponent
    · rcases hEmergency with ⟨_, _, hComponent⟩
      exact hComponent

def enterAssessedMode
    (state : ScopedModeState)
    (assessment : AssumptionAssessment) : ScopedModeState :=
  { state with
      orchestration :=
        { state.orchestration with mode := selectedMode assessment } }

theorem enterAssessedMode_preserves_component
    {world : ScopedAviationWorld}
    {state : ScopedModeState}
    {assessment : AssumptionAssessment}
    (hComponent : ModeComponentGuarantee world state) :
    ModeComponentGuarantee world (enterAssessedMode state assessment) := by
  rcases hComponent with ⟨hRunway, hSurface, hAir, hInterface, hOverlay⟩
  exact ⟨hRunway, hSurface, hAir, by simpa [enterAssessedMode] using hInterface, hOverlay⟩

theorem enterAssessedMode_withdraws_nominal
    {env : OrchestrationEnv}
    {state : ScopedModeState}
    {assessment : AssumptionAssessment}
    (hNonNominal : assessment ≠ .nominal) :
    ¬ NominalAssumptions env (enterAssessedMode state assessment).orchestration := by
  intro hNominal
  unfold NominalAssumptions at hNominal
  simp [enterAssessedMode, selectedMode] at hNominal
  exact nonNominalAssessment_selects_nonNormal hNonNominal hNominal

theorem enterAssessedMode_establishes_fallback
    {world : ScopedAviationWorld}
    {state : ScopedModeState}
    {assessment : AssumptionAssessment}
    (hComponent : ModeComponentGuarantee world state)
    (hNonNominal : assessment ≠ .nominal) :
    FallbackModeGuarantee world (enterAssessedMode state assessment) := by
  have hPreserved :
      ModeComponentGuarantee world (enterAssessedMode state assessment) :=
    enterAssessedMode_preserves_component hComponent
  cases assessment with
  | nominal =>
      cases hNonNominal rfl
  | degraded reason =>
      left
      exact ⟨reason, by simp [enterAssessedMode, selectedMode], hPreserved⟩
  | emergency kind =>
      right
      exact ⟨kind, by simp [enterAssessedMode, selectedMode], hPreserved⟩
  | multiFailure =>
      right
      exact ⟨"multi-failure", by simp [enterAssessedMode, selectedMode], hPreserved⟩

def applyFallbackCommand
    (state : ScopedModeState)
    (command : FallbackCommand) : ScopedModeState :=
  { orchestration :=
      { state.orchestration with tick := state.orchestration.tick + 1 }
    overlay := applyFallbackCommandOverlay state.overlay command }

theorem applyFallbackCommand_preserves_component
    {world : ScopedAviationWorld}
    {state : ScopedModeState}
    {command : FallbackCommand}
    (hComponent : ModeComponentGuarantee world state) :
    ModeComponentGuarantee world (applyFallbackCommand state command) := by
  rcases hComponent with ⟨hRunway, hSurface, hAir, hInterface, hOverlay⟩
  exact
    ⟨hRunway,
      hSurface,
      hAir,
      by simpa [applyFallbackCommand] using hInterface,
      applyFallbackCommandOverlay_preserves_wf hOverlay⟩

theorem applyFallbackCommand_preserves_degraded
    {world : ScopedAviationWorld}
    {state : ScopedModeState}
    {command : FallbackCommand}
    (hGuarantee : DegradedModeGuarantee world state) :
    DegradedModeGuarantee world (applyFallbackCommand state command) := by
  rcases hGuarantee with ⟨reason, hMode, hComponent⟩
  exact ⟨reason, by simpa [applyFallbackCommand] using hMode,
    applyFallbackCommand_preserves_component hComponent⟩

theorem applyFallbackCommand_preserves_emergency
    {world : ScopedAviationWorld}
    {state : ScopedModeState}
    {command : FallbackCommand}
    (hGuarantee : EmergencyModeGuarantee world state) :
    EmergencyModeGuarantee world (applyFallbackCommand state command) := by
  rcases hGuarantee with ⟨kind, hMode, hComponent⟩
  exact ⟨kind, by simpa [applyFallbackCommand] using hMode,
    applyFallbackCommand_preserves_component hComponent⟩

theorem applyFallbackCommand_preserves_fallback
    {world : ScopedAviationWorld}
    {state : ScopedModeState}
    {command : FallbackCommand}
    (hGuarantee : FallbackModeGuarantee world state) :
    FallbackModeGuarantee world (applyFallbackCommand state command) := by
  rcases hGuarantee with hDegraded | hEmergency
  · exact Or.inl (applyFallbackCommand_preserves_degraded hDegraded)
  · exact Or.inr (applyFallbackCommand_preserves_emergency hEmergency)

inductive ModeAwareProposal
  | nominal (proposal : CommandProposal)
  | fallback (command : FallbackCommand)
  deriving DecidableEq, Repr

inductive ModeAwareIssueResult
  | nominalIssued (newState : ScopedModeState) (issued : IssuedRecord)
  | fallbackIssued (newState : ScopedModeState)
  | rejected
  deriving DecidableEq, Repr

def ModeAwareIssueResult.issuedState? :
    ModeAwareIssueResult → Option ScopedModeState
  | .nominalIssued newState _ => some newState
  | .fallbackIssued newState => some newState
  | .rejected => none

noncomputable def issueWithAssessment
    (world : ScopedAviationWorld)
    (assessment : AssumptionAssessment)
    (state : ScopedModeState)
    (proposal : ModeAwareProposal) : ModeAwareIssueResult :=
  match selectedMode assessment with
  | .normal =>
      match proposal with
      | .nominal nominalProposal =>
          match issue_command
              (extractOrchestrationEnv world)
              state.orchestration
              nominalProposal with
          | .issued issued =>
              .nominalIssued
                { orchestration := issued.newState
                  overlay := state.overlay }
                issued
          | .rejected _ =>
              .rejected
      | .fallback _ =>
          .rejected
  | mode =>
      let fallbackState := enterAssessedMode state assessment
      match proposal with
      | .nominal _ =>
          .rejected
      | .fallback command =>
          if fallbackCommandAllowedIn mode command then
            .fallbackIssued (applyFallbackCommand fallbackState command)
          else
            .rejected

theorem executeCertifiedPath_not_ok_of_nonNormalMode
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {proposal : CommandProposal}
    {certified : CertifiedPath}
    (hMode : state.mode ≠ .normal) :
    executeCertifiedPath env state proposal ≠ .ok certified := by
  intro hOk
  unfold executeCertifiedPath at hOk
  cases hPlan : instantiate_plan env state proposal with
  | error err =>
      simp [hPlan] at hOk
  | ok plan =>
      cases hCollected : collectApprovalBundle env state plan with
      | error reasons =>
          simp [hPlan, hCollected] at hOk
      | ok collected =>
          cases hCompat :
              compatibility_check
                { mode := state.mode
                  activeSet := state.activeSet
                  approvals := collected.approvals } with
          | compatible =>
              cases hModeState : state.mode with
              | normal =>
                  exact False.elim (hMode hModeState)
              | degraded reason =>
                  simp [compatibility_check, narrowCompatibilityDecision,
                    compatibilityShapeOf, hModeState] at hCompat
              | emergency kind =>
                  simp [compatibility_check, narrowCompatibilityDecision,
                    compatibilityShapeOf, hModeState] at hCompat
          | incompatible reason =>
              simp [hPlan, hCollected, hCompat] at hOk

theorem issue_command_not_issued_of_nonNormalMode
    {env : OrchestrationEnv}
    {state : OrchestrationState}
    {proposal : CommandProposal}
    {issued : IssuedRecord}
    (hMode : state.mode ≠ .normal) :
    issue_command env state proposal ≠ .issued issued := by
  intro hIssued
  unfold issue_command at hIssued
  cases hPath : executeCertifiedPath env state proposal with
  | error reasons =>
      simp [hPath] at hIssued
  | ok certified =>
      have hImpossible :
          executeCertifiedPath env state proposal ≠ .ok certified :=
        executeCertifiedPath_not_ok_of_nonNormalMode hMode
      exact False.elim (hImpossible hPath)

theorem issueWithAssessment_nominal_preserves
    {world : ScopedAviationWorld}
    {state next : ScopedModeState}
    {proposal : CommandProposal}
    {issued : IssuedRecord}
    (hWf : ScopedSafetyWorldWellFormed world)
    (hGuarantee : NominalModeGuarantee world state)
    (hIssue :
      issueWithAssessment world .nominal state (.nominal proposal) =
        .nominalIssued next issued) :
    NominalModeGuarantee world next := by
  rcases hGuarantee with ⟨hNominal, hOverlay⟩
  unfold issueWithAssessment at hIssue
  simp [selectedMode] at hIssue
  cases hBase :
      issue_command
        (extractOrchestrationEnv world)
        state.orchestration
        proposal with
  | rejected reasons =>
      simp [hBase] at hIssue
  | issued issuedRecord =>
      simp [hBase] at hIssue
      rcases hIssue with ⟨rfl, rfl⟩
      constructor
      · exact issue_command_preserves_ScopedOrchestrationInv hWf hNominal hBase
      · simpa using hOverlay

theorem issueWithAssessment_fallback_preserves
    {world : ScopedAviationWorld}
    {state next : ScopedModeState}
    {assessment : AssumptionAssessment}
    {command : FallbackCommand}
    (hGuarantee : FullBriefGuarantee world state)
    (hNonNominal : assessment ≠ .nominal)
    (hIssue :
      issueWithAssessment world assessment state (.fallback command) =
        .fallbackIssued next) :
    FallbackModeGuarantee world next ∧
      ¬ NominalAssumptions
          (extractOrchestrationEnv world)
          next.orchestration := by
  have hComponent : ModeComponentGuarantee world state :=
    hGuarantee.component
  unfold issueWithAssessment at hIssue
  cases hMode : selectedMode assessment with
  | normal =>
      exfalso
      exact nonNominalAssessment_selects_nonNormal hNonNominal hMode
  | degraded reason =>
      simp [hMode] at hIssue
      have hFallbackBase :
          FallbackModeGuarantee world (enterAssessedMode state assessment) :=
        enterAssessedMode_establishes_fallback hComponent hNonNominal
      split at hIssue
      · cases hIssue
        constructor
        · exact applyFallbackCommand_preserves_fallback hFallbackBase
        · simpa [applyFallbackCommand] using
            enterAssessedMode_withdraws_nominal
              (env := extractOrchestrationEnv world)
              (state := state)
              hNonNominal
      · simp at hIssue
  | emergency kind =>
      simp [hMode] at hIssue
      have hFallbackBase :
          FallbackModeGuarantee world (enterAssessedMode state assessment) :=
        enterAssessedMode_establishes_fallback hComponent hNonNominal
      split at hIssue
      · cases hIssue
        constructor
        · exact applyFallbackCommand_preserves_fallback hFallbackBase
        · simpa [applyFallbackCommand] using
            enterAssessedMode_withdraws_nominal
              (env := extractOrchestrationEnv world)
              (state := state)
              hNonNominal
      · simp at hIssue

def initialScopedModeState
    (state : OrchestrationState) : ScopedModeState :=
  { orchestration := state
    overlay := {} }

inductive ReachableScopedModeState
    (world : ScopedAviationWorld) : ScopedModeState → Prop
  | initial
      {state : OrchestrationState}
      (hNominal : ScopedOrchestrationInv world state) :
      ReachableScopedModeState world (initialScopedModeState state)
  | nominalStep
      {state next : ScopedModeState}
      {proposal : CommandProposal}
      {issued : IssuedRecord}
      (prev : ReachableScopedModeState world state)
      (issue :
        issueWithAssessment world .nominal state (.nominal proposal) =
          .nominalIssued next issued) :
      ReachableScopedModeState world next
  | fallbackStep
      {state next : ScopedModeState}
      {assessment : AssumptionAssessment}
      {command : FallbackCommand}
      (prev : ReachableScopedModeState world state)
      (nonNominal : assessment ≠ .nominal)
      (issue :
        issueWithAssessment world assessment state (.fallback command) =
          .fallbackIssued next) :
      ReachableScopedModeState world next

theorem initialScopedModeState_fullBrief
    {world : ScopedAviationWorld}
    {state : OrchestrationState}
    (hNominal : ScopedOrchestrationInv world state) :
    FullBriefGuarantee world (initialScopedModeState state) := by
  left
  constructor
  · exact hNominal
  · exact FallbackOverlayWellFormed.empty

theorem ReachableScopedModeState_preserves_fullBrief
    {world : ScopedAviationWorld}
    (hWf : ScopedSafetyWorldWellFormed world) :
    ∀ {state},
      ReachableScopedModeState world state →
        FullBriefGuarantee world state := by
  intro state hReach
  induction hReach with
  | initial hNominal =>
      exact initialScopedModeState_fullBrief hNominal
  | @nominalStep state next proposal issued prev issue ih =>
      have hNominalState : NominalModeGuarantee world state := by
        rcases ih with hNominal | hFallback
        · exact hNominal
        · rcases hFallback with hDegraded | hEmergency
          · rcases hDegraded with ⟨reason, hMode, _⟩
            unfold issueWithAssessment at issue
            simp [selectedMode] at issue
            cases hBase :
                issue_command
                  (extractOrchestrationEnv world)
                  state.orchestration
                  proposal with
            | rejected reasons =>
                simp [hBase] at issue
            | issued issuedRecord =>
                have hImpossible :
                    issue_command
                      (extractOrchestrationEnv world)
                      state.orchestration
                      proposal ≠ .issued issuedRecord :=
                  issue_command_not_issued_of_nonNormalMode
                    (state := state.orchestration)
                    (proposal := proposal)
                    (issued := issuedRecord)
                    (by simp [hMode])
                exact False.elim (hImpossible hBase)
          · rcases hEmergency with ⟨kind, hMode, _⟩
            unfold issueWithAssessment at issue
            simp [selectedMode] at issue
            cases hBase :
                issue_command
                  (extractOrchestrationEnv world)
                  state.orchestration
                  proposal with
            | rejected reasons =>
                simp [hBase] at issue
            | issued issuedRecord =>
                have hImpossible :
                    issue_command
                      (extractOrchestrationEnv world)
                      state.orchestration
                      proposal ≠ .issued issuedRecord :=
                  issue_command_not_issued_of_nonNormalMode
                    (state := state.orchestration)
                    (proposal := proposal)
                    (issued := issuedRecord)
                    (by simp [hMode])
                exact False.elim (hImpossible hBase)
      exact Or.inl (issueWithAssessment_nominal_preserves hWf hNominalState issue)
  | @fallbackStep state next assessment command prev nonNominal issue ih =>
      rcases issueWithAssessment_fallback_preserves
          (state := state)
          (assessment := assessment)
          (next := next)
          (command := command)
          ih
          nonNominal
          issue with
        ⟨hFallback, _hWithdrawn⟩
      exact Or.inr hFallback

theorem ReachableScopedModeState_nominal_or_fallback
    {world : ScopedAviationWorld}
    (hWf : ScopedSafetyWorldWellFormed world)
    {state : ScopedModeState}
    (hReach : ReachableScopedModeState world state) :
    NominalModeGuarantee world state ∨
      (FallbackModeGuarantee world state ∧
        (state.orchestration.mode ≠ .normal)) := by
  have hGuarantee := ReachableScopedModeState_preserves_fullBrief hWf hReach
  rcases hGuarantee with hNominal | hFallback
  · exact Or.inl hNominal
  · have hModeNonNormal :
        state.orchestration.mode ≠ .normal := by
      rcases hFallback with hDegraded | hEmergency
      · rcases hDegraded with ⟨reason, hMode, _⟩
        simp [hMode]
      · rcases hEmergency with ⟨kind, hMode, _⟩
        simp [hMode]
    exact Or.inr ⟨hFallback, hModeNonNormal⟩

theorem FullBriefFallbackTheorem
    {world : ScopedAviationWorld}
    (hWf : ScopedSafetyWorldWellFormed world)
    {state : ScopedModeState}
    (hReach : ReachableScopedModeState world state) :
    FullBriefGuarantee world state ∧
      ((state.orchestration.mode = .normal →
          NominalModeGuarantee world state) ∧
        (state.orchestration.mode ≠ .normal →
          FallbackModeGuarantee world state)) := by
  have hGuarantee := ReachableScopedModeState_preserves_fullBrief hWf hReach
  constructor
  · exact hGuarantee
  · constructor
    · intro hModeNormal
      rcases hGuarantee with hNominal | hFallback
      · exact hNominal
      · rcases hFallback with hDegraded | hEmergency
        · rcases hDegraded with ⟨reason, hMode, _⟩
          have : False := by simp [hMode] at hModeNormal
          exact False.elim this
        · rcases hEmergency with ⟨kind, hMode, _⟩
          have : False := by simp [hMode] at hModeNormal
          exact False.elim this
    · intro hModeNonNormal
      rcases hGuarantee with hNominal | hFallback
      · rcases hNominal with ⟨hScoped, _⟩
        rcases hScoped with ⟨hNominalAssumptions, _⟩
        exact False.elim (hModeNonNormal hNominalAssumptions)
      · exact hFallback

end Greenfield
end CertifiedAtc
