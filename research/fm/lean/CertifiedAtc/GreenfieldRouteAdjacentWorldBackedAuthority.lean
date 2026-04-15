import CertifiedAtc.GreenfieldRouteBearingCompound
import CertifiedAtc.GreenfieldRouteAdjacentWorldBackedCurrentShape

namespace CertifiedAtc
namespace Greenfield

/--
World-backed authority closure for the delivered route-adjacent surface.

This keeps the same conservative authority mapping as the earlier current-shape
module, but depends only on the world-backed route-adjacent boundary.
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

end Greenfield
end CertifiedAtc
