import CertifiedAtc.GreenfieldRouteAdjacentCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
Current-shape authority closure for the delivered Phase B route-adjacent
surface.

This module stays conservative:

- `ExtendDownwind` and `Orbit` are treated as circuit control on
  `circuitProcedure`
- `ContinueApproach` is treated as approach sequencing on
  `instrumentApproach`
- authority remains type-level, matching the current greenfield role model;
  this module does not claim resolved instance-level controller jurisdiction
- the theorem surface closes only the already-delivered single-step and narrow
  compound slices
-/

def routeAdjacentInstructionRequiredAuthorityGrant? :
    AtcInstruction → Option CompileAuthorityGrantView
  | .continueApproach _ =>
      some { entityType := .instrumentApproach, operation := .sequence }
  | .extendDownwind _ =>
      some { entityType := .circuitProcedure, operation := .circuit }
  | .orbit _ _ =>
      some { entityType := .circuitProcedure, operation := .circuit }
  | instruction =>
      routeBearingCompoundInstructionRequiredAuthorityGrant? instruction

@[simp] theorem routeAdjacentInstructionRequiredAuthorityGrant?_continueApproach
    (target : AircraftId) :
    routeAdjacentInstructionRequiredAuthorityGrant? (.continueApproach target) =
      some { entityType := .instrumentApproach, operation := .sequence } := by
  simp [routeAdjacentInstructionRequiredAuthorityGrant?]

@[simp] theorem routeAdjacentInstructionRequiredAuthorityGrant?_extendDownwind
    (target : AircraftId) :
    routeAdjacentInstructionRequiredAuthorityGrant? (.extendDownwind target) =
      some { entityType := .circuitProcedure, operation := .circuit } := by
  simp [routeAdjacentInstructionRequiredAuthorityGrant?]

@[simp] theorem routeAdjacentInstructionRequiredAuthorityGrant?_orbit
    (target : AircraftId)
    (direction : OrbitDirection) :
    routeAdjacentInstructionRequiredAuthorityGrant? (.orbit target direction) =
      some { entityType := .circuitProcedure, operation := .circuit } := by
  simp [routeAdjacentInstructionRequiredAuthorityGrant?]

def routeAdjacentInstructionIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId)
    (instruction : AtcInstruction) : Bool :=
  match routeAdjacentInstructionRequiredAuthorityGrant? instruction with
  | none => true
  | some grant => controllerHasAuthorityGrant view controller grant

def routeAdjacentInstructionsIssuerAuthorized
    (view : ClearanceCompileView)
    (controller : AgentId) :
    List AtcInstruction → Bool
  | [] => true
  | instruction :: tail =>
      routeAdjacentInstructionIssuerAuthorized view controller instruction &&
        routeAdjacentInstructionsIssuerAuthorized view controller tail

def RouteAdjacentWorldAuthorized
    (world : RouteBearingScopedAviationWorld)
    (controller : AgentId)
    (steps : List AtcInstruction) : Prop :=
  ∀ instruction ∈ steps,
    match routeAdjacentInstructionRequiredAuthorityGrant? instruction with
    | none => True
    | some grant =>
        WorldControllerHasGrant world.toScopedAviationWorld controller grant

theorem routeAdjacentInstructionIssuerAuthorized_eq_true_of_unmapped
    {view : ClearanceCompileView}
    {controller : AgentId}
    {instruction : AtcInstruction}
    (hUnmapped : routeAdjacentInstructionRequiredAuthorityGrant? instruction = none) :
    routeAdjacentInstructionIssuerAuthorized view controller instruction = true := by
  simp [routeAdjacentInstructionIssuerAuthorized, hUnmapped]

theorem routeAdjacentInstructionIssuerAuthorized_eq_true_of_worldAuthorized
    {world : RouteBearingScopedAviationWorld}
    {controller : AgentId}
    {instruction : AtcInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMapped : routeAdjacentInstructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world.toScopedAviationWorld controller grant) :
    routeAdjacentInstructionIssuerAuthorized
      (extractRouteBearingCompileView world)
      controller
      instruction = true := by
  simp [routeAdjacentInstructionIssuerAuthorized, hMapped]
  change
    controllerHasAuthorityGrant
      (extractCompileView world.toScopedAviationWorld)
      controller
      grant = true
  exact
    controllerHasAuthorityGrant_of_worldControllerHasGrant
      hWf.baseWellFormed
      hGrant

theorem routeAdjacentInstructionsIssuerAuthorized_eq_true_of_worldAuthorized :
    ∀ {world : RouteBearingScopedAviationWorld}
      {controller : AgentId}
      {steps : List AtcInstruction},
      RouteBearingExtractionWellFormed world →
      RouteAdjacentWorldAuthorized world controller steps →
        routeAdjacentInstructionsIssuerAuthorized
          (extractRouteBearingCompileView world)
          controller
          steps = true := by
  intro world controller steps hWf hAuth
  induction steps with
  | nil =>
      simp [routeAdjacentInstructionsIssuerAuthorized]
  | cons head tail ih =>
      have hHeadAuth := hAuth head (by simp)
      have hTailAuth : RouteAdjacentWorldAuthorized world controller tail := by
        intro instruction hMem
        exact hAuth instruction (by simp [hMem])
      cases hGrant : routeAdjacentInstructionRequiredAuthorityGrant? head with
      | none =>
          simp [routeAdjacentInstructionsIssuerAuthorized,
            routeAdjacentInstructionIssuerAuthorized_eq_true_of_unmapped hGrant,
            ih hTailAuth]
      | some grant =>
          have hHeadGrant :
              WorldControllerHasGrant world.toScopedAviationWorld controller grant := by
            simpa [hGrant] using hHeadAuth
          have hHeadOk :
              routeAdjacentInstructionIssuerAuthorized
                (extractRouteBearingCompileView world)
                controller
                head = true :=
            routeAdjacentInstructionIssuerAuthorized_eq_true_of_worldAuthorized
              (world := world)
              (controller := controller)
              (instruction := head)
              (grant := grant)
              hWf
              hGrant
              hHeadGrant
          simp [routeAdjacentInstructionsIssuerAuthorized, hHeadOk, ih hTailAuth]

inductive RouteAdjacentAuthorityCurrentShapeIssuable
    (world : RouteBearingScopedAviationWorld) :
    StructuredClearance → Prop
  | continueSingle
      {clearance : StructuredClearance}
      (hIssuable : ContinueApproachCurrentShapeIssuable clearance) :
      RouteAdjacentAuthorityCurrentShapeIssuable world clearance
  | continueCompound
      {clearance : StructuredClearance}
      (hIssuable : ContinueApproachCompoundCurrentShapeIssuable world clearance) :
      RouteAdjacentAuthorityCurrentShapeIssuable world clearance
  | extendDownwindSingle
      {clearance : StructuredClearance}
      (hIssuable : ExtendDownwindCurrentShapeIssuable clearance) :
      RouteAdjacentAuthorityCurrentShapeIssuable world clearance
  | extendDownwindCompound
      {clearance : StructuredClearance}
      (hIssuable : ExtendDownwindCompoundCurrentShapeIssuable world clearance) :
      RouteAdjacentAuthorityCurrentShapeIssuable world clearance
  | orbitSingle
      {clearance : StructuredClearance}
      (hIssuable : OrbitCurrentShapeIssuable clearance) :
      RouteAdjacentAuthorityCurrentShapeIssuable world clearance
  | orbitCompound
      {clearance : StructuredClearance}
      (hIssuable : OrbitCompoundCurrentShapeIssuable world clearance) :
      RouteAdjacentAuthorityCurrentShapeIssuable world clearance

theorem RouteAdjacentAuthorityCurrentShapeIssuanceTheorem
    {world : RouteBearingScopedAviationWorld}
    {existing : List ManagedResolvedClearance}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReach : ReachableResolvedSet existing)
    (hFresh : clearance.id ∉ resolvedClearanceIds existing)
    (hIssuable : RouteAdjacentAuthorityCurrentShapeIssuable world clearance)
    (hAuthority :
      RouteAdjacentWorldAuthorized world clearance.issuedBy (structuredInstructions clearance)) :
    ∃ resolved,
      routeAdjacentInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true ∧
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState ∧
      ReachableResolvedSet
        (admitResolvedClearance existing resolved).clearances := by
  have hAuthorized :
      routeAdjacentInstructionsIssuerAuthorized
        (extractRouteBearingCompileView world)
        clearance.issuedBy
        (structuredInstructions clearance) = true :=
    routeAdjacentInstructionsIssuerAuthorized_eq_true_of_worldAuthorized
      (world := world)
      (controller := clearance.issuedBy)
      (steps := structuredInstructions clearance)
      hWf
      hAuthority
  cases hIssuable with
  | continueSingle hSingle =>
      rcases ContinueApproachCurrentShapeIssuanceTheorem
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle with
          ⟨resolved, hResolve, hReachable⟩
      exact ⟨resolved, hAuthorized, hResolve, hReachable⟩
  | continueCompound hCompound =>
      rcases ContinueApproachCompoundCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hCompound with
          ⟨resolved, hResolve, hReachable⟩
      exact ⟨resolved, hAuthorized, hResolve, hReachable⟩
  | extendDownwindSingle hSingle =>
      rcases ExtendDownwindCurrentShapeIssuanceTheorem
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle with
          ⟨resolved, hResolve, hReachable⟩
      exact ⟨resolved, hAuthorized, hResolve, hReachable⟩
  | extendDownwindCompound hCompound =>
      rcases ExtendDownwindCompoundCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hCompound with
          ⟨resolved, hResolve, hReachable⟩
      exact ⟨resolved, hAuthorized, hResolve, hReachable⟩
  | orbitSingle hSingle =>
      rcases OrbitCurrentShapeIssuanceTheorem
          (world := RouteBearingScopedAviationWorld.toResolutionWorld world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hSingle with
          ⟨resolved, hResolve, hReachable⟩
      exact ⟨resolved, hAuthorized, hResolve, hReachable⟩
  | orbitCompound hCompound =>
      rcases OrbitCompoundCurrentShapeIssuanceTheorem
          (world := world)
          (existing := existing)
          (initialState := initialState)
          (clearance := clearance)
          hReach
          hFresh
          hCompound with
          ⟨resolved, hResolve, hReachable⟩
      exact ⟨resolved, hAuthorized, hResolve, hReachable⟩

end Greenfield
end CertifiedAtc
