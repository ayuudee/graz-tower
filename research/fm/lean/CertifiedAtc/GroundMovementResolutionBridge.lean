import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldResolution

namespace CertifiedAtc
namespace Greenfield

/--
`GroundMovementResolutionBridge` connects the scoped surface/runway extraction
world to the current greenfield resolved-execution boundary for the delivered
ground-movement slice.

This bridge stays deliberately small:

- `TaxiTo` uses explicit graph-backed taxi-route bindings
- `HoldShortOf` is bridged from extracted taxiway holding points
- `CrossRunway` uses explicit runway-crossing bindings

It does not try to model a proof-side pathfinder. The current honest boundary is
the explicit graph-backed route/crossing surface above the extracted scoped
world.
-/

def taxiwayHoldingPointPairs
    (taxiway : ScopedTaxiwaySource) : List (RunwayId × PointId) :=
  taxiway.holdingPoints.map fun hold => (hold.runway, hold.point)

def scopedWorldHoldingPointPairs :
    List ScopedTaxiwaySource → List (RunwayId × PointId)
  | [] => []
  | taxiway :: tail =>
      taxiwayHoldingPointPairs taxiway ++ scopedWorldHoldingPointPairs tail

structure GroundMovementScopedAviationWorld extends ScopedAviationWorld where
  taxiRoutes : List ConcreteTaxiRoute := []
  runwayCrossingPoints : List (RunwayId × PointId) := []
  deriving Repr

structure GroundMovementExtractionWellFormed
    (world : GroundMovementScopedAviationWorld) : Prop where
  baseWellFormed : ScopedExtractionWellFormed world.toScopedAviationWorld

def GroundMovementScopedAviationWorld.toConcreteResolutionWorld
    (world : GroundMovementScopedAviationWorld) : ConcreteResolutionWorld :=
  { taxiRoutes := world.taxiRoutes
    runwayHoldingPoints := scopedWorldHoldingPointPairs world.taxiways
    runwayCrossingPoints := world.runwayCrossingPoints }

def GroundMovementScopedAviationWorld.toResolutionWorld
    (world : GroundMovementScopedAviationWorld) : ResolutionWorld :=
  ConcreteResolutionWorld.toResolutionWorld
    (GroundMovementScopedAviationWorld.toConcreteResolutionWorld world)

theorem GroundMovementScopedAviationWorld.mem_taxiRoute_of_mem
    {world : GroundMovementScopedAviationWorld}
    {route : ConcreteTaxiRoute}
    (hMem : route ∈ world.taxiRoutes) :
    (GroundMovementScopedAviationWorld.toResolutionWorld world).taxiRoute
      route.start
      route.destination
      route.path := by
  simpa [GroundMovementScopedAviationWorld.toResolutionWorld,
    GroundMovementScopedAviationWorld.toConcreteResolutionWorld,
    ConcreteResolutionWorld.toResolutionWorld] using hMem

theorem GroundMovementScopedAviationWorld.mem_holdingPoint_of_taxiwayHoldingPoint
    {world : GroundMovementScopedAviationWorld}
    {taxiway : ScopedTaxiwaySource}
    {hold : CompileHoldingPointView}
    (hTaxiway : taxiway ∈ world.taxiways)
    (hHold : hold ∈ taxiway.holdingPoints) :
    (GroundMovementScopedAviationWorld.toResolutionWorld world).holdingPointForRunway
      hold.runway
      hold.point := by
  change
    (hold.runway, hold.point) ∈
      scopedWorldHoldingPointPairs world.taxiways
  have hPairs :
      ∀ {items : List ScopedTaxiwaySource},
        taxiway ∈ items →
          (hold.runway, hold.point) ∈ scopedWorldHoldingPointPairs items := by
    intro items hMem
    induction items with
    | nil =>
        cases hMem
    | cons head tail ih =>
        simp [scopedWorldHoldingPointPairs, taxiwayHoldingPointPairs] at hMem ⊢
        rcases hMem with rfl | hTail
        · left
          exact ⟨hold, hHold, rfl, rfl⟩
        · right
          exact ih hTail
  exact hPairs hTaxiway

theorem GroundMovementScopedAviationWorld.mem_crossingPoint_of_mem
    {world : GroundMovementScopedAviationWorld}
    {runway : RunwayId}
    {point : PointId}
    (hMem : (runway, point) ∈ world.runwayCrossingPoints) :
    (GroundMovementScopedAviationWorld.toResolutionWorld world).crossingPointForRunway
      runway
      point := by
  simpa [GroundMovementScopedAviationWorld.toResolutionWorld,
    GroundMovementScopedAviationWorld.toConcreteResolutionWorld,
    ConcreteResolutionWorld.toResolutionWorld] using hMem

end Greenfield
end CertifiedAtc
