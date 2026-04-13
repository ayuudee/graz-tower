import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldReachability

namespace CertifiedAtc
namespace Greenfield

/--
Exact scoped greenfield surface for `Safety-complete (N₀)`.

This is the theorem-bearing package above the greenfield runtime model for the
scoped surface frozen in `safety_complete_scope.md`. It deliberately excludes
the route-bearing and open-ended families in Bucket C.

`setSquawk` is used here as the greenfield analogue of the older atomic
`SquawkCode` family in the scoped claim.

The scoped air-modifier surface is intentionally narrower than the full
greenfield constructors. The current atomic certified-path bridge can
faithfully carry:

- `ReduceSpeedTo` only in knot form

But the current shortest honest `Safety-complete (N₀)` claim is narrower still:
only `ReduceSpeedTo` remains in the scoped air-modifier surface. The altitude-
only modifier slice is deferred until the separation-layer continuation story
can justify it uniformly.
-/

def ScopedBridgeableAirModifierInstruction : AtcInstruction → Prop
  | .reduceSpeedTo _ (.inKnots _) => True
  | _ => False

def ScopedCertifiedInstruction : AtcInstruction → Prop
  | .taxiTo _ _ _ => True
  | .holdShortOf _ _ => True
  | .crossRunway _ _ => True
  | .lineUpAndWait _ _ => True
  | .clearedForTakeoff _ _ => True
  | .clearedToLand _ _ => True
  | .clearedTouchAndGo _ _ => True
  | .goAround _ => True
  | .reduceSpeedTo _ (.inKnots _) => True
  | _ => False

def ScopedNeutralInstruction : AtcInstruction → Prop
  | .reportDownwind _ => True
  | .reportFinal _ => True
  | .proceed _ => True
  | .contactFrequency _ _ _ => True
  | .monitorFrequency _ _ _ => True
  | .setSquawk _ _ => True
  | _ => False

def ScopedSafetyInstruction (instruction : AtcInstruction) : Prop :=
  ScopedCertifiedInstruction instruction ∨ ScopedNeutralInstruction instruction

def ScopedSurfaceMovementInstruction : AtcInstruction → Prop
  | .taxiTo _ _ _ => True
  | .holdShortOf _ _ => True
  | .crossRunway _ _ => True
  | .lineUpAndWait _ _ => True
  | _ => False

def ScopedAuthorityMappedInstruction : AtcInstruction → Prop
  | .taxiTo _ _ _ => True
  | .holdShortOf _ _ => True
  | .crossRunway _ _ => True
  | .lineUpAndWait _ _ => True
  | .clearedForTakeoff _ _ => True
  | .clearedToLand _ _ => True
  | .clearedTouchAndGo _ _ => True
  | .goAround _ => True
  | .reduceSpeedTo _ _ => True
  | .climbTo _ _ => True
  | .descendTo _ _ => True
  | .contactFrequency _ _ _ => True
  | .monitorFrequency _ _ _ => True
  | .setSquawk _ _ => True
  | _ => False

def scopedInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | .taxiTo _ _ _ =>
      some { entityType := .taxiway, operation := .taxi }
  | .holdShortOf _ _ =>
      some { entityType := .runway, operation := .cross }
  | .crossRunway _ _ =>
      some { entityType := .runway, operation := .cross }
  | .lineUpAndWait _ _ =>
      some { entityType := .runway, operation := .lineUp }
  | .clearedForTakeoff _ _ =>
      some { entityType := .runway, operation := .takeoff }
  | .clearedToLand _ _ =>
      some { entityType := .runway, operation := .land }
  | .clearedTouchAndGo _ _ =>
      some { entityType := .runway, operation := .touchAndGo }
  | .goAround _ =>
      some { entityType := .runway, operation := .land }
  | .reduceSpeedTo _ _ =>
      some { entityType := .airspaceVolume, operation := .speed }
  | .climbTo _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .descendTo _ _ =>
      some { entityType := .airspaceVolume, operation := .altitude }
  | .contactFrequency _ _ _ =>
      some { entityType := .radioRole, operation := .contact }
  | .monitorFrequency _ _ _ =>
      some { entityType := .radioRole, operation := .monitor }
  | .setSquawk _ _ =>
      some { entityType := .radioRole, operation := .squawk }
  | _ => none

def ScopedSurfaceCompoundContent (content : CompoundClearanceContent) : Prop :=
  ∀ instruction ∈ content.steps, ScopedSurfaceMovementInstruction instruction

def ScopedSafetyStructuredClearance (clearance : StructuredClearance) : Prop :=
  ∀ instruction ∈ structuredInstructions clearance, ScopedSafetyInstruction instruction

theorem scopedInstructionRequiredAuthorityGrant?_isSome_iff
    (instruction : AtcInstruction) :
    (∃ grant, scopedInstructionRequiredAuthorityGrant? instruction = some grant) ↔
      ScopedAuthorityMappedInstruction instruction := by
  cases instruction <;>
    simp [scopedInstructionRequiredAuthorityGrant?, ScopedAuthorityMappedInstruction]

theorem scopedSurfaceMovementInstruction_frontierTiming
    {instruction : AtcInstruction}
    (hScoped : ScopedSurfaceMovementInstruction instruction) :
    instructionFrontierTiming instruction = .movement := by
  cases instruction <;>
    simp [ScopedSurfaceMovementInstruction, instructionFrontierTiming] at hScoped ⊢

theorem scopedSurfaceMovementInstruction_mayBeConditional
    {instruction : AtcInstruction}
    (hScoped : ScopedSurfaceMovementInstruction instruction) :
    instructionMayBeConditional instruction = true := by
  cases instruction <;>
    simp [ScopedSurfaceMovementInstruction, instructionMayBeConditional] at hScoped ⊢

theorem scopedSafetyInstruction_conditional_iff_surfaceMovement
    {instruction : AtcInstruction}
    (hScoped : ScopedSafetyInstruction instruction) :
    instructionMayBeConditional instruction = true ↔
      ScopedSurfaceMovementInstruction instruction := by
  cases instruction <;> simp [ScopedSafetyInstruction,
    ScopedCertifiedInstruction, ScopedNeutralInstruction,
    ScopedSurfaceMovementInstruction, instructionMayBeConditional] at hScoped ⊢

theorem instructionSupersedesIn_goAround_scoped
    (target : AircraftId) :
    instructionSupersedesIn (.goAround target) =
      [.runway, .route, .level, .speed] := rfl

theorem allStepsMayBeConditional_true_of_scopedSurfaceSteps :
    ∀ {steps : List AtcInstruction},
      (∀ instruction ∈ steps, ScopedSurfaceMovementInstruction instruction) →
        allStepsMayBeConditional steps = true := by
  intro steps hScoped
  induction steps with
  | nil =>
      simp [allStepsMayBeConditional]
  | cons head tail ih =>
      have hHead : ScopedSurfaceMovementInstruction head := hScoped head (by simp)
      have hTail : ∀ instruction ∈ tail, ScopedSurfaceMovementInstruction instruction := by
        intro instruction hMem
        exact hScoped instruction (by simp [hMem])
      simpa [allStepsMayBeConditional, scopedSurfaceMovementInstruction_mayBeConditional hHead] using
        ih hTail

theorem allStepsMayBeConditional_true_of_scopedSurfaceCompound
    {content : CompoundClearanceContent}
    (hScoped : ScopedSurfaceCompoundContent content) :
    allStepsMayBeConditional content.steps = true :=
  allStepsMayBeConditional_true_of_scopedSurfaceSteps hScoped

theorem immediateFrontierSteps_eq_nil_of_scopedSurfaceSteps :
    ∀ {steps : List AtcInstruction},
      (∀ instruction ∈ steps, ScopedSurfaceMovementInstruction instruction) →
        immediateFrontierSteps steps = [] := by
  intro steps hScoped
  induction steps with
  | nil =>
      simp [immediateFrontierSteps]
  | cons head tail ih =>
      have hHead : ScopedSurfaceMovementInstruction head := hScoped head (by simp)
      have hTail : ∀ instruction ∈ tail, ScopedSurfaceMovementInstruction instruction := by
        intro instruction hMem
        exact hScoped instruction (by simp [hMem])
      have hTiming : instructionFrontierTiming head = .movement :=
        scopedSurfaceMovementInstruction_frontierTiming hHead
      simpa [immediateFrontierSteps, hTiming] using
        ih hTail

theorem immediateFrontierSteps_eq_nil_of_scopedSurfaceCompound
    {content : CompoundClearanceContent}
    (hScoped : ScopedSurfaceCompoundContent content) :
    immediateFrontierSteps content.steps = [] :=
  immediateFrontierSteps_eq_nil_of_scopedSurfaceSteps hScoped

theorem frontierInstructions_eq_activeMovement_of_scopedSurfaceCompound
    {content : CompoundClearanceContent}
    (hScoped : ScopedSurfaceCompoundContent content) :
    frontierInstructions (.compound content) =
      match activeMovementStep? content with
      | none => []
      | some (_, instruction) => [instruction] := by
  have hImmediate : immediateFrontierSteps content.steps = [] :=
    immediateFrontierSteps_eq_nil_of_scopedSurfaceCompound hScoped
  cases hActive : activeMovementStep? content <;>
    simp [frontierInstructions, hImmediate, hActive]

theorem frontierInstructions_length_le_one_of_scopedSurfaceCompound
    {content : CompoundClearanceContent}
    (hScoped : ScopedSurfaceCompoundContent content) :
    (frontierInstructions (.compound content)).length ≤ 1 := by
  rw [frontierInstructions_eq_activeMovement_of_scopedSurfaceCompound hScoped]
  cases hActive : activeMovementStep? content <;> simp

theorem normalizeConditionalEnvelope_ok_of_scopedSurfaceCompound
    (clearance : StructuredClearance)
    (content : CompoundClearanceContent)
    (hScoped : ScopedSurfaceCompoundContent content)
    (hWrapped : anyWrappedConditionalStep content.steps = false) :
    normalizeConditionalEnvelope { clearance with content := .compound content } =
      .ok { clearance with content := .compound content } := by
  have hAllConditional : allStepsMayBeConditional content.steps = true :=
    allStepsMayBeConditional_true_of_scopedSurfaceCompound hScoped
  cases clearance <;> simp [normalizeConditionalEnvelope, hWrapped, hAllConditional]

theorem stageIncomingClearanceChecked_ok_of_scopedSurfaceCompound
    (clearance : StructuredClearance)
    (content : CompoundClearanceContent)
    (hScoped : ScopedSurfaceCompoundContent content)
    (hWrapped : anyWrappedConditionalStep content.steps = false) :
    stageIncomingClearanceChecked { clearance with content := .compound content } =
      .ok (stageIncomingClearance { clearance with content := .compound content }) := by
  simp [stageIncomingClearanceChecked,
    normalizeConditionalEnvelope_ok_of_scopedSurfaceCompound clearance content hScoped hWrapped]

theorem scopedResolvedAdmission_reachable_of_resolved
    {existing : List ManagedResolvedClearance}
    {world : ResolutionWorld}
    {initialState finalState : ResolutionState}
    {clearance : StructuredClearance}
    {resolved : ResolvedClearance}
    (hReach : ReachableResolvedSet existing)
    (hFresh : resolved.source.id ∉ resolvedClearanceIds existing)
    (hResolve : ResolvesClearance world initialState clearance resolved finalState) :
    ReachableResolvedSet (admitResolvedClearance existing resolved).clearances := by
  exact ReachableResolvedSet.admit_of_resolved hReach hFresh hResolve

end Greenfield
end CertifiedAtc
