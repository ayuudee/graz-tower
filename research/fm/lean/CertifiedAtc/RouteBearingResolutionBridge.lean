import CertifiedAtc.RouteBearingExtraction
import CertifiedAtc.GreenfieldResolution

namespace CertifiedAtc
namespace Greenfield

/--
`RouteBearingResolutionBridge` connects the first widened procedure-bearing
extraction world to the current greenfield resolved-execution boundary.

This bridge is intentionally honest and partial:

- `ClearedTo` is bridged only at the currently modeled resolved fact:
  clearance-limit fix -> clearance-limit point
- `HoldAt` is bridged for published holds
- `ClearedApproach` is bridged for non-circling approaches
- `JoinCircuit` is bridged only when the extracted circuit source carries an
  explicit supported join procedure
-/

def scopedCircuitJoinBinding
    (circuit : ScopedCircuitSource)
    (join : ScopedCircuitJoinSource)
    (runway : Option RunwayId) : ConcreteCircuitJoinBinding :=
  { direction := greenfieldCircuitDirection circuit.direction
    joinType := join.type
    runway := runway
    circuit := circuit.id
    altitude := circuit.altitude }

def circuitJoinBindingsFor
    (circuit : ScopedCircuitSource) : List ScopedCircuitJoinSource → List ConcreteCircuitJoinBinding
  | [] => []
  | join :: tail =>
      scopedCircuitJoinBinding circuit join (some circuit.runway) ::
        scopedCircuitJoinBinding circuit join none ::
        circuitJoinBindingsFor circuit tail

def circuitJoinBindings
    (circuit : ScopedCircuitSource) : List ConcreteCircuitJoinBinding :=
  circuitJoinBindingsFor circuit circuit.joinProcedures

def worldCircuitJoinBindings : List ScopedCircuitSource → List ConcreteCircuitJoinBinding
  | [] => []
  | circuit :: tail =>
      circuitJoinBindings circuit ++ worldCircuitJoinBindings tail

def airwayPointBindings
    (airway : ScopedAirwaySource) : List ConcreteAirwayPointBinding :=
  airway.waypoints.map fun waypoint =>
    { airway := airway.id
      point := waypoint.point }

def worldAirwayPointBindings
    (airways : List ScopedAirwaySource) : List ConcreteAirwayPointBinding :=
  match airways with
  | [] => []
  | airway :: tail => airwayPointBindings airway ++ worldAirwayPointBindings tail

def greenfieldRoleName? : CertifiedAtc.RoleName → Option RoleName
  | "CLEARANCE_DELIVERY" => some .clearanceDelivery
  | "GROUND" => some .ground
  | "TOWER" => some .tower
  | "APPROACH" => some .approach
  | "DEPARTURE" => some .departure
  | "AREA_CONTROL" => some .areaControl
  | "AFIS" => some .afis
  | "clearanceDelivery" => some .clearanceDelivery
  | "ground" => some .ground
  | "tower" => some .tower
  | "approach" => some .approach
  | "departure" => some .departure
  | "areaControl" => some .areaControl
  | "afis" => some .afis
  | _ => none

def scopedHandoffBinding?
    (handoff : CompileHandoffView) : Option ConcretePublishedHandoffBinding := do
  let fromRole <- greenfieldRoleName? handoff.fromRole
  let toRole <- greenfieldRoleName? handoff.toRole
  pure
    { fromRole := fromRole
      toRole := toRole
      action :=
        match handoff.action with
        | .contact => .contact
        | .monitor => .monitor
      location :=
        match handoff.location with
        | .holdingPoint point => .holdingPoint point
        | .boundaryFix fix => .boundaryFix fix
        | .airborne => .airborne }

def scopedHandoffBindings
    (handoffs : List CompileHandoffView) : List ConcretePublishedHandoffBinding :=
  handoffs.filterMap scopedHandoffBinding?

def scopedHandoffAction
    (handoff : CompileHandoffView) : ResolvedPublishedHandoffAction :=
  match handoff.action with
  | .contact => .contact
  | .monitor => .monitor

def scopedHandoffPoint
    (handoff : CompileHandoffView) : ResolvedPublishedHandoffPoint :=
  match handoff.location with
  | .holdingPoint point => .holdingPoint point
  | .boundaryFix fix => .boundaryFix fix
  | .airborne => .airborne

def scopedResolvedHandoff?
    (handoff : CompileHandoffView) : Option (RoleName × RoleName × ResolvedPublishedHandoffAction × ResolvedPublishedHandoffPoint) := do
  let fromRole <- greenfieldRoleName? handoff.fromRole
  let toRole <- greenfieldRoleName? handoff.toRole
  pure (fromRole, toRole, scopedHandoffAction handoff, scopedHandoffPoint handoff)

def scopedHandoffBinding
    (handoff : CompileHandoffView)
    (fromRole toRole : RoleName) : ConcretePublishedHandoffBinding :=
  { fromRole := fromRole
    toRole := toRole
    action :=
      match handoff.action with
      | .contact => .contact
      | .monitor => .monitor
    location :=
      match handoff.location with
      | .holdingPoint point => .holdingPoint point
      | .boundaryFix fix => .boundaryFix fix
      | .airborne => .airborne }

def RouteBearingScopedAviationWorld.toConcreteResolutionWorld
    (world : RouteBearingScopedAviationWorld) : ConcreteResolutionWorld :=
  { fixPoints :=
      world.fixes.map (fun fix => (fix.id, fix.point))
    airwayPoints :=
      worldAirwayPointBindings world.airways
    holdingPatterns :=
      world.holdingPatterns.map fun hold =>
        { hold := .published hold.fix
          pattern := hold.id
          fix := hold.fix }
    approaches :=
      world.approaches.map fun approach =>
        { approachType := approach.kind
          runway := approach.runway
          circlingRunway := none
          approach := approach.id }
    publishedHandoffs :=
      scopedHandoffBindings world.handoffs
    airspaceVolumes :=
      world.airspaceVolumes.map fun airspace =>
        { airspace := airspace.id
          points := airspace.points }
    circuitJoins :=
      worldCircuitJoinBindings world.circuits }

def routeBearingFixPoint?
    (world : RouteBearingScopedAviationWorld)
    (fixId : FixId) : Option PointId :=
  (world.fixes.find? (fun fix => fix.id = fixId)).map (fun fix => fix.point)

def routeBearingFixPoints?
    (world : RouteBearingScopedAviationWorld)
    (fixes : List FixId) : Option (List PointId) :=
  fixes.mapM (routeBearingFixPoint? world)

def takePointsThrough
    (exitPoint : PointId)
    (points : List PointId) : Option (List PointId) :=
  let rec go (seen : List PointId) : List PointId → Option (List PointId)
    | [] => none
    | point :: tail =>
        let seen' := seen ++ [point]
        if point = exitPoint then
          some seen'
        else
          go seen' tail
  go [] points

def airwayPointsThroughExit?
    (world : RouteBearingScopedAviationWorld)
    (airwayId : AirwayId)
    (exitFix : FixId) : Option (List PointId) := do
  let airway <- world.airways.find? (fun airway => airway.id = airwayId)
  let exitPoint <- routeBearingFixPoint? world exitFix
  let points := airway.waypoints.map (fun waypoint => waypoint.point)
  takePointsThrough exitPoint points

def routeBearingRouteSpecPoints?
    (world : RouteBearingScopedAviationWorld)
    (route : RouteSpec) : Option (List PointId) :=
  match route with
  | .direct fix =>
      routeBearingFixPoint? world fix |>.map List.singleton
  | .via fixes =>
      routeBearingFixPoints? world fixes
  | .airway airway exitFix =>
      airwayPointsThroughExit? world airway exitFix
  | .viaSid sidId =>
      (world.sids.find? (fun sid => sid.id = sidId)).map fun sid =>
        sid.waypoints.map (fun waypoint => waypoint.point)
  | .viaStar starId =>
      (world.stars.find? (fun star => star.id = starId)).map fun star =>
        star.waypoints.map (fun waypoint => waypoint.point)
  | .viaRoute routeId =>
      (world.vfrRoutes.find? (fun route => route.id = routeId)).map fun route =>
        route.waypoints.map (fun waypoint => waypoint.point)

def pointsThroughOrFull
    (limitPoint : PointId)
    (points : List PointId) : List PointId :=
  (takePointsThrough limitPoint points).getD points

private def connectedTransitionPointsToLimit?
    (trunkPoints : List PointId)
    (transitionWaypoints : List CompileWaypointView)
    (limitPoint : PointId) : Option (List PointId) := do
  let firstPoint :: remainingPoints := routePointsOfWaypoints transitionWaypoints
    | none
  if trunkPoints.reverse.head? = some firstPoint then
    takePointsThrough limitPoint (trunkPoints ++ remainingPoints)
  else
    none

private def publishedProcedurePointsToLimit
    (trunkWaypoints : List CompileWaypointView)
    (transitions : List (List CompileWaypointView))
    (limitPoint : PointId) : List PointId :=
  let trunkPoints := routePointsOfWaypoints trunkWaypoints
  match takePointsThrough limitPoint trunkPoints with
  | some points => points
  | none =>
      let rec go : List (List CompileWaypointView) → Option (List PointId)
        | [] => none
        | transition :: tail =>
            match connectedTransitionPointsToLimit? trunkPoints transition limitPoint with
            | some points => some points
            | none => go tail
      (go transitions).getD trunkPoints

def routeBearingRoutePointsToLimit?
    (world : RouteBearingScopedAviationWorld)
    (route : Option RouteSpec)
    (clearanceLimit : FixId) : Option (List PointId) := do
  let limitPoint <- routeBearingFixPoint? world clearanceLimit
  match route with
  | none => pure [limitPoint]
  | some routeSpec =>
      match routeSpec with
      | .direct fix =>
          (routeBearingFixPoint? world fix).map List.singleton
      | .via fixes =>
          (routeBearingFixPoints? world fixes).map (pointsThroughOrFull limitPoint)
      | .airway airway exitFix =>
          (airwayPointsThroughExit? world airway exitFix).map (pointsThroughOrFull limitPoint)
      | .viaSid sidId =>
          (world.sids.find? (fun sid => sid.id = sidId)).map fun sid =>
            publishedProcedurePointsToLimit sid.waypoints sid.transitions limitPoint
      | .viaStar starId =>
          (world.stars.find? (fun star => star.id = starId)).map fun star =>
            publishedProcedurePointsToLimit star.waypoints star.transitions limitPoint
      | .viaRoute routeId =>
          (world.vfrRoutes.find? (fun route => route.id = routeId)).map fun route =>
            pointsThroughOrFull limitPoint (routePointsOfWaypoints route.waypoints)

def holdingPatternLoopPoints?
    (world : RouteBearingScopedAviationWorld)
    (holdingPattern : HoldingPatternId) : Option (List PointId) :=
  (world.holdingPatterns.find? (fun hold => hold.id = holdingPattern)).map (fun hold => hold.path)

def approachWaypointPoints?
    (world : RouteBearingScopedAviationWorld)
    (approachId : ApproachId) : Option (List PointId) :=
  (world.approaches.find? (fun approach => approach.id = approachId)).map fun approach =>
    routePointsOfWaypoints approach.waypoints

def approachThresholdPoint?
    (world : RouteBearingScopedAviationWorld)
    (approachId : ApproachId) : Option PointId :=
  (world.approaches.find? (fun approach => approach.id = approachId)).map (fun approach => approach.threshold)

def approachMissedApproach?
    (world : RouteBearingScopedAviationWorld)
    (approachId : ApproachId) : Option (HoldingPatternId × List PointId) := do
  let approach <- world.approaches.find? (fun candidate => candidate.id = approachId)
  let holdingPattern <- approach.missedApproach.holdAt
  pure (holdingPattern, routePointsOfWaypoints approach.missedApproach.path)

def uniqueCircuitJoinForType?
    (circuit : ScopedCircuitSource)
    (joinType : JoinType) : Option ScopedCircuitJoinSource :=
  match circuit.joinProcedures.filter (fun join => join.type = joinType) with
  | [join] => some join
  | _ => none

def circuitJoinEntry?
    (world : RouteBearingScopedAviationWorld)
    (circuitId : CircuitProcedureId)
    (joinType : JoinType) : Option (PointId × List PointId) := do
  let circuit <- world.circuits.find? (fun candidate => candidate.id = circuitId)
  let join <- uniqueCircuitJoinForType? circuit joinType
  pure (join.entryPoint, join.entryPath.getD [join.entryPoint])

def circuitProcedurePoints?
    (world : RouteBearingScopedAviationWorld)
    (circuitId : CircuitProcedureId) : Option (List PointId) := do
  let circuit <- world.circuits.find? (fun candidate => candidate.id = circuitId)
  let firstLeg :: remainingLegs := circuit.legs | none
  pure <|
    firstLeg.startPoint :: (firstLeg.to :: remainingLegs.map (fun leg => leg.to))

def RouteBearingScopedAviationWorld.toResolutionWorld
    (world : RouteBearingScopedAviationWorld) : ResolutionWorld :=
  { (ConcreteResolutionWorld.toResolutionWorld
      (RouteBearingScopedAviationWorld.toConcreteResolutionWorld world)) with
      routeSpecPoints := fun route points =>
        routeBearingRouteSpecPoints? world route = some points
      routeClearancePoints := fun route clearanceLimit points =>
        routeBearingRoutePointsToLimit? world (some route) clearanceLimit = some points
      holdingPatternLoop := fun holdingPattern points =>
        holdingPatternLoopPoints? world holdingPattern = some points
      approachWaypoints := fun approach points =>
        approachWaypointPoints? world approach = some points
      approachThreshold := fun approach threshold =>
        approachThresholdPoint? world approach = some threshold
      approachMissedApproach := fun approach holdingPattern points =>
        approachMissedApproach? world approach = some (holdingPattern, points)
      circuitJoinEntry := fun circuit joinType entryPoint entryPathPoints =>
        circuitJoinEntry? world circuit joinType = some (entryPoint, entryPathPoints)
      circuitProcedurePoints := fun circuit points =>
        circuitProcedurePoints? world circuit = some points }

private theorem findById_eq_some_of_mem
    {α β : Type}
    [DecidableEq β]
    (key : α → β)
    {items : List α}
    {item : α}
    (hMem : item ∈ items)
    (hNodup : (items.map key).Nodup) :
    items.find? (fun candidate => decide (key candidate = key item)) = some item := by
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [List.find?]
      · have hHeadNe : key head ≠ key item := by
          intro hEq
          exact hHeadNotIn item hTailMem (by simp [hEq])
        simp [List.find?, hHeadNe, ih hTailMem hTailNodup]

theorem RouteBearingScopedAviationWorld.routeSpecPoints_of_eq_some
    {world : RouteBearingScopedAviationWorld}
    {route : RouteSpec}
    {points : List PointId}
    (hPoints : routeBearingRouteSpecPoints? world route = some points) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).routeSpecPoints route points := by
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld] using hPoints

theorem RouteBearingScopedAviationWorld.routeClearancePoints_of_eq_some
    {world : RouteBearingScopedAviationWorld}
    {route : RouteSpec}
    {clearanceLimit : FixId}
    {points : List PointId}
    (hPoints : routeBearingRoutePointsToLimit? world (some route) clearanceLimit = some points) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).routeClearancePoints route clearanceLimit points := by
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld] using hPoints

theorem RouteBearingScopedAviationWorld.holdingPatternLoop_of_eq_some
    {world : RouteBearingScopedAviationWorld}
    {holdingPattern : HoldingPatternId}
    {points : List PointId}
    (hPoints : holdingPatternLoopPoints? world holdingPattern = some points) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).holdingPatternLoop holdingPattern points := by
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld] using hPoints

theorem RouteBearingScopedAviationWorld.approachWaypoints_of_eq_some
    {world : RouteBearingScopedAviationWorld}
    {approach : ApproachId}
    {points : List PointId}
    (hPoints : approachWaypointPoints? world approach = some points) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).approachWaypoints approach points := by
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld] using hPoints

theorem RouteBearingScopedAviationWorld.approachThreshold_of_eq_some
    {world : RouteBearingScopedAviationWorld}
    {approach : ApproachId}
    {threshold : PointId}
    (hThreshold : approachThresholdPoint? world approach = some threshold) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).approachThreshold approach threshold := by
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld] using hThreshold

theorem RouteBearingScopedAviationWorld.approachMissedApproach_of_eq_some
    {world : RouteBearingScopedAviationWorld}
    {approach : ApproachId}
    {holdingPattern : HoldingPatternId}
    {points : List PointId}
    (hMissed : approachMissedApproach? world approach = some (holdingPattern, points)) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).approachMissedApproach approach holdingPattern points := by
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld] using hMissed

theorem RouteBearingScopedAviationWorld.circuitJoinEntry_of_eq_some
    {world : RouteBearingScopedAviationWorld}
    {circuit : CircuitProcedureId}
    {joinType : JoinType}
    {entryPoint : PointId}
    {entryPathPoints : List PointId}
    (hEntry : circuitJoinEntry? world circuit joinType = some (entryPoint, entryPathPoints)) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).circuitJoinEntry circuit joinType entryPoint entryPathPoints := by
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld] using hEntry

theorem RouteBearingScopedAviationWorld.circuitProcedurePoints_of_eq_some
    {world : RouteBearingScopedAviationWorld}
    {circuit : CircuitProcedureId}
    {points : List PointId}
    (hPoints : circuitProcedurePoints? world circuit = some points) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).circuitProcedurePoints circuit points := by
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld] using hPoints

theorem RouteBearingScopedAviationWorld.mem_fixPoint_of_mem
    {world : RouteBearingScopedAviationWorld}
    {fix : ScopedFixSource}
    (hMem : fix ∈ world.fixes) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).fixPoint fix.id fix.point := by
  have hMap :
      (fix.id, fix.point) ∈
        (RouteBearingScopedAviationWorld.toConcreteResolutionWorld world).fixPoints := by
    exact List.mem_map.mpr ⟨fix, hMem, rfl⟩
  exact
    ConcreteResolutionWorld.mem_fixPoint
      (world := RouteBearingScopedAviationWorld.toConcreteResolutionWorld world)
      hMap

theorem RouteBearingScopedAviationWorld.findFix_eq_some_of_mem
    {world : RouteBearingScopedAviationWorld}
    {fix : ScopedFixSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : fix ∈ world.fixes) :
    world.fixes.find? (fun candidate => decide (candidate.id = fix.id)) = some fix := by
  exact findById_eq_some_of_mem (fun candidate : ScopedFixSource => candidate.id) hMem hWf.fixIds

theorem RouteBearingScopedAviationWorld.routeBearingFixPoint_eq_some_of_mem
    {world : RouteBearingScopedAviationWorld}
    {fix : ScopedFixSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : fix ∈ world.fixes) :
    routeBearingFixPoint? world fix.id = some fix.point := by
  unfold routeBearingFixPoint?
  simp [RouteBearingScopedAviationWorld.findFix_eq_some_of_mem (world := world) hWf hMem]

theorem RouteBearingScopedAviationWorld.mem_publishedHoldingPattern_of_mem
    {world : RouteBearingScopedAviationWorld}
    {hold : ScopedHoldingPatternSource}
    (hMem : hold ∈ world.holdingPatterns) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).holdingPatternFor
      (.published hold.fix) hold.id hold.fix := by
  have hMap :
      { hold := .published hold.fix, pattern := hold.id, fix := hold.fix } ∈
        (RouteBearingScopedAviationWorld.toConcreteResolutionWorld world).holdingPatterns := by
    exact List.mem_map.mpr ⟨hold, hMem, rfl⟩
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld,
    RouteBearingScopedAviationWorld.toConcreteResolutionWorld,
    ConcreteResolutionWorld.toResolutionWorld] using hMap

def uniquePublishedHoldingPatternForFix?
    (world : RouteBearingScopedAviationWorld)
    (fixId : FixId) : Option HoldingPatternId :=
  match world.holdingPatterns.filter (fun hold => hold.fix = fixId) with
  | [hold] => some hold.id
  | _ => none

theorem RouteBearingScopedAviationWorld.uniquePublishedHoldingPatternForFix_of_eq_some
    {world : RouteBearingScopedAviationWorld}
    {fixId : FixId}
    {holdingPattern : HoldingPatternId}
    (hHold : uniquePublishedHoldingPatternForFix? world fixId = some holdingPattern) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).holdingPatternFor
      (.published fixId)
      holdingPattern
      fixId := by
  unfold uniquePublishedHoldingPatternForFix? at hHold
  cases hFiltered : List.filter (fun hold => hold.fix = fixId) world.holdingPatterns with
      | nil =>
          simp [hFiltered] at hHold
      | cons hold tail =>
          cases tail with
          | nil =>
              simp [hFiltered] at hHold
              subst holdingPattern
              have hMemFiltered : hold ∈ List.filter (fun candidate => candidate.fix = fixId) world.holdingPatterns := by
                simp [hFiltered]
              have hFilterProps := List.mem_filter.mp hMemFiltered
              have hMem : hold ∈ world.holdingPatterns := hFilterProps.1
              have hFix : hold.fix = fixId := by
                simpa using hFilterProps.2
              simpa [hFix] using
                RouteBearingScopedAviationWorld.mem_publishedHoldingPattern_of_mem
                  (world := world)
                  hMem
          | cons tailHead tailTail =>
              simp [hFiltered] at hHold

theorem RouteBearingScopedAviationWorld.findApproach_eq_some_of_mem
    {world : RouteBearingScopedAviationWorld}
    {approach : ScopedApproachSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : approach ∈ world.approaches) :
    world.approaches.find? (fun candidate => decide (candidate.id = approach.id)) = some approach := by
  exact
    findById_eq_some_of_mem
      (fun candidate : ScopedApproachSource => candidate.id)
      hMem
      hWf.approachIds

theorem RouteBearingScopedAviationWorld.mem_nonCirclingApproach_of_mem
    {world : RouteBearingScopedAviationWorld}
    {approach : ScopedApproachSource}
    (hMem : approach ∈ world.approaches) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).approachFor
      approach.kind approach.runway none approach.id := by
  have hMap :
      { approachType := approach.kind
        runway := approach.runway
        circlingRunway := none
        approach := approach.id } ∈
        (RouteBearingScopedAviationWorld.toConcreteResolutionWorld world).approaches := by
    exact List.mem_map.mpr ⟨approach, hMem, rfl⟩
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld,
    RouteBearingScopedAviationWorld.toConcreteResolutionWorld,
    ConcreteResolutionWorld.toResolutionWorld] using hMap

theorem RouteBearingScopedAviationWorld.mem_approachWaypoints_of_mem
    {world : RouteBearingScopedAviationWorld}
    {approach : ScopedApproachSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : approach ∈ world.approaches) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).approachWaypoints
      approach.id
      (routePointsOfWaypoints approach.waypoints) := by
  exact
    RouteBearingScopedAviationWorld.approachWaypoints_of_eq_some
      (world := world)
      (approach := approach.id)
      (points := routePointsOfWaypoints approach.waypoints)
      (by
        unfold approachWaypointPoints?
        simp [RouteBearingScopedAviationWorld.findApproach_eq_some_of_mem (world := world) hWf hMem])

theorem RouteBearingScopedAviationWorld.mem_approachThreshold_of_mem
    {world : RouteBearingScopedAviationWorld}
    {approach : ScopedApproachSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : approach ∈ world.approaches) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).approachThreshold
      approach.id
      approach.threshold := by
  exact
    RouteBearingScopedAviationWorld.approachThreshold_of_eq_some
      (world := world)
      (approach := approach.id)
      (threshold := approach.threshold)
      (by
        unfold approachThresholdPoint?
        simp [RouteBearingScopedAviationWorld.findApproach_eq_some_of_mem (world := world) hWf hMem])

theorem RouteBearingScopedAviationWorld.mem_approachMissedApproach_of_mem
    {world : RouteBearingScopedAviationWorld}
    {approach : ScopedApproachSource}
    {holdingPattern : HoldingPatternId}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : approach ∈ world.approaches)
    (hHold : approach.missedApproach.holdAt = some holdingPattern) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).approachMissedApproach
      approach.id
      holdingPattern
      (routePointsOfWaypoints approach.missedApproach.path) := by
  exact
    RouteBearingScopedAviationWorld.approachMissedApproach_of_eq_some
      (world := world)
      (approach := approach.id)
      (holdingPattern := holdingPattern)
      (points := routePointsOfWaypoints approach.missedApproach.path)
      (by
        unfold approachMissedApproach?
        simp [RouteBearingScopedAviationWorld.findApproach_eq_some_of_mem (world := world) hWf hMem, hHold])

theorem ScopedCircuitSource.uniqueCircuitJoinForType_of_eq_some
    {circuit : ScopedCircuitSource}
    {joinType : JoinType}
    {join : ScopedCircuitJoinSource}
    (hJoin : uniqueCircuitJoinForType? circuit joinType = some join) :
    join ∈ circuit.joinProcedures ∧ join.type = joinType := by
  unfold uniqueCircuitJoinForType? at hJoin
  cases hFiltered : List.filter (fun candidate => candidate.type = joinType) circuit.joinProcedures with
  | nil =>
      simp [hFiltered] at hJoin
  | cons head tail =>
      cases tail with
      | nil =>
          simp [hFiltered] at hJoin
          subst join
          have hMemFiltered :
              head ∈ List.filter (fun candidate => candidate.type = joinType) circuit.joinProcedures := by
            simp [hFiltered]
          have hFilterProps := List.mem_filter.mp hMemFiltered
          exact ⟨hFilterProps.1, by simpa using hFilterProps.2⟩
      | cons tailHead tailTail =>
          simp [hFiltered] at hJoin

theorem RouteBearingScopedAviationWorld.findCircuit_eq_some_of_mem
    {world : RouteBearingScopedAviationWorld}
    {circuit : ScopedCircuitSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : circuit ∈ world.circuits) :
    world.circuits.find? (fun candidate => decide (candidate.id = circuit.id)) = some circuit := by
  exact
    findById_eq_some_of_mem
      (fun candidate : ScopedCircuitSource => candidate.id)
      hMem
      hWf.circuitIds

theorem RouteBearingScopedAviationWorld.mem_circuitJoin_of_mem
    {world : RouteBearingScopedAviationWorld}
    {circuit : ScopedCircuitSource}
    {join : ScopedCircuitJoinSource}
    {runway : Option RunwayId}
    (hCircuitMem : circuit ∈ world.circuits)
    (hJoinMem : join ∈ circuit.joinProcedures)
    (hRunway : runway = none ∨ runway = some circuit.runway) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).circuitJoin
      (greenfieldCircuitDirection circuit.direction)
      join.type
      runway
      circuit.id
      circuit.altitude := by
  have hInner :
      scopedCircuitJoinBinding circuit join runway ∈ circuitJoinBindings circuit := by
    unfold circuitJoinBindings
    have hInnerFor :
        ∀ joins : List ScopedCircuitJoinSource,
          join ∈ joins →
            scopedCircuitJoinBinding circuit join runway ∈
              circuitJoinBindingsFor circuit joins := by
      intro joins hMem
      induction joins with
      | nil =>
          cases hMem
      | cons head tail ih =>
          simp [circuitJoinBindingsFor] at hMem ⊢
          rcases hMem with hEq | hTail
          · subst head
            cases hRunway with
            | inl hNone =>
                simp [scopedCircuitJoinBinding, hNone]
            | inr hSome =>
                simp [scopedCircuitJoinBinding, hSome]
          · exact Or.inr (Or.inr (ih hTail))
    exact hInnerFor circuit.joinProcedures hJoinMem
  have hOuter :
      scopedCircuitJoinBinding circuit join runway ∈
        (RouteBearingScopedAviationWorld.toConcreteResolutionWorld world).circuitJoins := by
    unfold RouteBearingScopedAviationWorld.toConcreteResolutionWorld
    have hOuterFor :
        ∀ circuits : List ScopedCircuitSource,
          circuit ∈ circuits →
            scopedCircuitJoinBinding circuit join runway ∈
              worldCircuitJoinBindings circuits := by
      intro circuits hMem
      induction circuits with
      | nil =>
          cases hMem
      | cons head tail ih =>
          simp [worldCircuitJoinBindings] at hMem ⊢
          rcases hMem with hEq | hTail
          · subst head
            exact Or.inl hInner
          · exact Or.inr (ih hTail)
    exact hOuterFor world.circuits hCircuitMem
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld,
    RouteBearingScopedAviationWorld.toConcreteResolutionWorld,
    ConcreteResolutionWorld.toResolutionWorld, scopedCircuitJoinBinding] using hOuter

theorem RouteBearingScopedAviationWorld.mem_circuitJoinEntry_of_mem
    {world : RouteBearingScopedAviationWorld}
    {circuit : ScopedCircuitSource}
    {join : ScopedCircuitJoinSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hCircuitMem : circuit ∈ world.circuits)
    (hJoinUnique : uniqueCircuitJoinForType? circuit join.type = some join) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).circuitJoinEntry
      circuit.id
      join.type
      join.entryPoint
      (join.entryPath.getD [join.entryPoint]) := by
  exact
    RouteBearingScopedAviationWorld.circuitJoinEntry_of_eq_some
      (world := world)
      (circuit := circuit.id)
      (joinType := join.type)
      (entryPoint := join.entryPoint)
      (entryPathPoints := join.entryPath.getD [join.entryPoint])
      (by
        unfold circuitJoinEntry?
        simp [RouteBearingScopedAviationWorld.findCircuit_eq_some_of_mem (world := world) hWf hCircuitMem, hJoinUnique])

theorem RouteBearingScopedAviationWorld.mem_circuitProcedurePoints_of_mem
    {world : RouteBearingScopedAviationWorld}
    {circuit : ScopedCircuitSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hCircuitMem : circuit ∈ world.circuits)
    (hLegs : circuit.legs ≠ []) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).circuitProcedurePoints
      circuit.id
      ((match circuit.legs with
        | [] => []
        | firstLeg :: remainingLegs =>
            firstLeg.startPoint :: (firstLeg.to :: remainingLegs.map (fun leg => leg.to)))) := by
  exact
    RouteBearingScopedAviationWorld.circuitProcedurePoints_of_eq_some
      (world := world)
      (circuit := circuit.id)
      (points :=
        match circuit.legs with
        | [] => []
        | firstLeg :: remainingLegs =>
            firstLeg.startPoint :: (firstLeg.to :: remainingLegs.map (fun leg => leg.to)))
      (by
        unfold circuitProcedurePoints?
        cases hLegList : circuit.legs with
        | nil =>
            contradiction
        | cons firstLeg remainingLegs =>
            simp [RouteBearingScopedAviationWorld.findCircuit_eq_some_of_mem (world := world) hWf hCircuitMem, hLegList])

theorem RouteBearingScopedAviationWorld.mem_airspaceVolume_of_mem
    {world : RouteBearingScopedAviationWorld}
    {airspace : ScopedAirspaceVolumeSource}
    (hMem : airspace ∈ world.airspaceVolumes) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).airspaceVolume
      airspace.id
      airspace.points := by
  have hMap :
      { airspace := airspace.id, points := airspace.points } ∈
        (RouteBearingScopedAviationWorld.toConcreteResolutionWorld world).airspaceVolumes := by
    exact List.mem_map.mpr ⟨airspace, hMem, rfl⟩
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld,
    RouteBearingScopedAviationWorld.toConcreteResolutionWorld,
    ConcreteResolutionWorld.toResolutionWorld] using hMap

theorem RouteBearingScopedAviationWorld.mem_publishedHandoff_of_mem
    {world : RouteBearingScopedAviationWorld}
    {handoff : CompileHandoffView}
    {fromRole toRole : RoleName}
    (hFrom : greenfieldRoleName? handoff.fromRole = some fromRole)
    (hTo : greenfieldRoleName? handoff.toRole = some toRole)
    (hMem : handoff ∈ world.handoffs) :
    (RouteBearingScopedAviationWorld.toResolutionWorld world).publishedHandoff
      fromRole
      toRole
      (scopedHandoffAction handoff)
      (scopedHandoffPoint handoff) := by
  have hMap :
      scopedHandoffBinding handoff fromRole toRole ∈
        (RouteBearingScopedAviationWorld.toConcreteResolutionWorld world).publishedHandoffs := by
    have hFilter :
        scopedHandoffBinding? handoff =
          some (scopedHandoffBinding handoff fromRole toRole) := by
      simp [scopedHandoffBinding?, scopedHandoffBinding, hFrom, hTo]
    unfold RouteBearingScopedAviationWorld.toConcreteResolutionWorld scopedHandoffBindings
    exact List.mem_filterMap.mpr ⟨handoff, hMem, hFilter⟩
  simpa [RouteBearingScopedAviationWorld.toResolutionWorld,
    RouteBearingScopedAviationWorld.toConcreteResolutionWorld,
    ConcreteResolutionWorld.toResolutionWorld, scopedHandoffBindings, scopedHandoffBinding,
    scopedHandoffAction, scopedHandoffPoint] using hMap

def RouteBearingInstructionResolutionReady
    (world : RouteBearingScopedAviationWorld) : AtcInstruction → Prop
  | .clearedTo _ clearanceLimit route =>
      ∃ fix ∈ world.fixes,
        fix.id = clearanceLimit ∧
          (routeBearingRoutePointsToLimit? world route clearanceLimit).isSome
  | .holdAt _ (.published fixId) _ =>
      ∃ hold ∈ world.holdingPatterns,
        hold.fix = fixId ∧
          holdingPatternLoopPoints? world hold.id = some hold.path ∧
          (∃ fix ∈ world.fixes, fix.id = fixId ∧ fix.point = hold.fixPoint)
  | .clearedApproach _ approachType runway none =>
      ∃ approach ∈ world.approaches,
        approach.kind = approachType ∧
        approach.runway = runway ∧
        (∃ holdId,
          approach.missedApproach.holdAt = some holdId ∧
          ∃ hold ∈ world.holdingPatterns, hold.id = holdId)
  | .joinCircuit _ direction joinType runway =>
      ∃ circuit ∈ world.circuits,
        direction = greenfieldCircuitDirection circuit.direction ∧
        (∃ join, uniqueCircuitJoinForType? circuit joinType = some join) ∧
        (runway = none ∨ runway = some circuit.runway) ∧
        circuit.legs ≠ []
  | _ => False

def singletonResolvedClearance
    (clearance : StructuredClearance)
    (step : ResolvedStep) : ResolvedClearance :=
  { source := clearance
    steps := [step] }

theorem resolvesSingleInstructionClearance
    {world : ResolutionWorld}
    {initialState finalState : ResolutionState}
    {instruction : AtcInstruction}
    {step : ResolvedStep}
    {clearance : StructuredClearance}
    (hNormalized : normalizeConditionalEnvelope clearance = .ok clearance)
    (hContent : clearance.content = .single instruction)
    (hDomain : clearance.domain = step.domain)
    (hStep : ResolvesIndexedStep world initialState clearance.domain 0 instruction step finalState) :
    ResolvesClearance
      world
      initialState
      clearance
      (singletonResolvedClearance clearance step)
      finalState := by
  refine ⟨hNormalized, rfl, ?_⟩
  have hSteps :
      ResolvesSteps world initialState clearance.domain [(0, instruction)] [step] finalState := by
    apply ResolvesSteps.cons
    · exact hStep
    · simpa using ResolvesSteps.nil world finalState clearance.domain
  simpa [singletonResolvedClearance, structuredInstructions, contentInstructions,
    indexedSteps, enumerateFrom, hContent, hDomain] using hSteps

theorem resolvesIndexedClearedTo_of_ready
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {index : Nat}
    {target : AircraftId}
    {clearanceLimit : FixId}
    {route : Option RouteSpec}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.clearedTo target clearanceLimit route)) :
    ∃ step,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        .route
        index
        (.clearedTo target clearanceLimit route)
        step
        state := by
  rcases hReady with ⟨fix, hMem, hFixEq, hRouteReady⟩
  subst clearanceLimit
  cases hRoutePts : routeBearingRoutePointsToLimit? world route fix.id with
  | none =>
      simp [hRoutePts] at hRouteReady
  | some routePoints =>
      let holdingPattern := uniquePublishedHoldingPatternForFix? world fix.id
      refine ⟨compileResolvedStep
          index
          .route
          (.clearedTo target fix.id route)
          (.route
            { clearanceLimitFix := fix.id
              clearanceLimitPoint := fix.point
              routePoints := routePoints
              clearanceLimitHoldingPattern := holdingPattern })
          (by simp [resolutionCompatible]), ?_⟩
      apply ResolvesIndexedStep.route
      · exact RouteBearingScopedAviationWorld.mem_fixPoint_of_mem (world := world) hMem
      · cases route with
        | none =>
            have hLookup :=
              RouteBearingScopedAviationWorld.routeBearingFixPoint_eq_some_of_mem
                (world := world)
                hWf
                hMem
            simpa [routeBearingRoutePointsToLimit?, hLookup] using hRoutePts.symm
        | some routeSpec =>
            exact
              RouteBearingScopedAviationWorld.routeClearancePoints_of_eq_some
                (world := world)
                (route := routeSpec)
                (clearanceLimit := fix.id)
                hRoutePts
      · cases hHolding : uniquePublishedHoldingPatternForFix? world fix.id with
        | none =>
            simp [holdingPattern, hHolding]
        | some resolvedHoldingPattern =>
            simpa [holdingPattern, hHolding] using
              RouteBearingScopedAviationWorld.uniquePublishedHoldingPatternForFix_of_eq_some
                (world := world)
                (fixId := fix.id)
                (holdingPattern := resolvedHoldingPattern)
                hHolding

theorem resolvesIndexedPublishedHoldAt_of_ready
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {index : Nat}
    {target : AircraftId}
    {fixId : FixId}
    {efc : Option String}
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.holdAt target (.published fixId) efc)) :
    ∃ step,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        .route
        index
        (.holdAt target (.published fixId) efc)
        step
        state := by
  rcases hReady with ⟨hold, hMem, hFixEq, hLoopEq, fix, hFixMem, hFixId, hFixPoint⟩
  subst fixId
  refine ⟨compileResolvedStep
      index
      .route
      (.holdAt target (.published hold.fix) efc)
      (.holding
        { holdingPattern := hold.id
          fix := hold.fix
          fixPoint := hold.fixPoint
          loopPoints := hold.path })
      (by simp [resolutionCompatible]), ?_⟩
  apply ResolvesIndexedStep.holding
  · exact
      RouteBearingScopedAviationWorld.mem_publishedHoldingPattern_of_mem
        (world := world) hMem
  · simpa [hFixId, hFixPoint] using
      RouteBearingScopedAviationWorld.mem_fixPoint_of_mem
        (world := world)
        hFixMem
  · exact
      RouteBearingScopedAviationWorld.holdingPatternLoop_of_eq_some
        (world := world)
        (holdingPattern := hold.id)
        (points := hold.path)
        hLoopEq

theorem resolvesIndexedNonCirclingClearedApproach_of_ready
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {index : Nat}
    {target : AircraftId}
    {approachType : ApproachType}
    {runway : RunwayId}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.clearedApproach target approachType runway none)) :
    ∃ step,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        .route
        index
        (.clearedApproach target approachType runway none)
        step
        state := by
  rcases hReady with ⟨approach, hMem, hKindEq, hRunwayEq, holdingPattern, hHoldAt, hold, hHoldMem, hHoldId⟩
  subst approachType
  subst runway
  refine ⟨compileResolvedStep
      index
      .route
      (.clearedApproach target approach.kind approach.runway none)
      (.approach
        { approach := approach.id
          runway := approach.runway
          waypointPoints := routePointsOfWaypoints approach.waypoints
          thresholdPoint := approach.threshold
          missedApproachPoints := routePointsOfWaypoints approach.missedApproach.path
          missedApproachHoldingPattern := holdingPattern })
      (by simp [resolutionCompatible]), ?_⟩
  apply ResolvesIndexedStep.approach
  · exact
      RouteBearingScopedAviationWorld.mem_nonCirclingApproach_of_mem
        (world := world) hMem
  · exact
      RouteBearingScopedAviationWorld.mem_approachWaypoints_of_mem
        (world := world)
        hWf
        hMem
  · exact
      RouteBearingScopedAviationWorld.mem_approachThreshold_of_mem
        (world := world)
        hWf
        hMem
  · exact
      RouteBearingScopedAviationWorld.mem_approachMissedApproach_of_mem
        (world := world)
        (approach := approach)
        (holdingPattern := holdingPattern)
        hWf
        hMem
        hHoldAt

theorem resolvesIndexedJoinCircuit_of_ready
    {world : RouteBearingScopedAviationWorld}
    {state : ResolutionState}
    {index : Nat}
    {target : AircraftId}
    {direction : CircuitDirection}
    {joinType : JoinType}
    {runway : Option RunwayId}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.joinCircuit target direction joinType runway)) :
    ∃ step,
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        state
        .runway
        index
        (.joinCircuit target direction joinType runway)
        step
        state := by
  rcases hReady with ⟨circuit, hCircuitMem, hRest⟩
  rcases hRest with ⟨hDirEq, hJoin, hRunway, hLegs⟩
  rcases hJoin with ⟨join, hJoinUnique⟩
  have hJoinProps :=
    ScopedCircuitSource.uniqueCircuitJoinForType_of_eq_some
      (circuit := circuit)
      (joinType := joinType)
      (join := join)
      hJoinUnique
  rcases hJoinProps with ⟨hJoinMem, hJoinEq⟩
  subst direction
  subst joinType
  refine ⟨compileResolvedStep
      index
      .runway
      (.joinCircuit target (greenfieldCircuitDirection circuit.direction) join.type runway)
      (.circuitJoin
        { circuit := circuit.id
          altitude := circuit.altitude
          entryPoint := join.entryPoint
          entryPathPoints := join.entryPath.getD [join.entryPoint]
          circuitPoints :=
            match circuit.legs with
            | [] => []
            | firstLeg :: remainingLegs =>
                firstLeg.startPoint :: (firstLeg.to :: remainingLegs.map (fun leg => leg.to)) })
      (by simp [resolutionCompatible]), ?_⟩
  apply ResolvesIndexedStep.joinCircuit
  · exact
      RouteBearingScopedAviationWorld.mem_circuitJoin_of_mem
        (world := world)
        (circuit := circuit)
        (join := join)
        hCircuitMem
        hJoinMem
        hRunway
  · exact
      RouteBearingScopedAviationWorld.mem_circuitJoinEntry_of_mem
        (world := world)
        (circuit := circuit)
        (join := join)
        hWf
        hCircuitMem
        hJoinUnique
  · exact
      RouteBearingScopedAviationWorld.mem_circuitProcedurePoints_of_mem
        (world := world)
        (circuit := circuit)
        hWf
        hCircuitMem
        hLegs

theorem resolvesSingleClearedToClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {target : AircraftId}
    {clearanceLimit : FixId}
    {route : Option RouteSpec}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.clearedTo target clearanceLimit route))
    (hContent :
      clearance.content = .single (.clearedTo target clearanceLimit route))
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  rcases resolvesIndexedClearedTo_of_ready (world := world) (state := initialState)
      (index := 0) hWf hReady with ⟨step, hStep⟩
  have hStepDomain : step.domain = .route := by
    cases hStep <;> simp [compileResolvedStep, instructionDomain?]
  have hDomainStep : clearance.domain = step.domain := by
    simpa [hStepDomain] using hDomain
  have hStepAtDomain :
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance.domain
        0
        (.clearedTo target clearanceLimit route)
        step
        initialState := by
    simpa [hDomain] using hStep
  have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
    cases clearance with
    | mk id aircraft content domain issuedBy issuedAt status condition =>
        dsimp at hContent hCondition
        subst content
        subst condition
        simp [normalizeConditionalEnvelope, instructionMayBeConditional]
  refine ⟨singletonResolvedClearance clearance step, ?_⟩
  exact resolvesSingleInstructionClearance
    hNormalized
    hContent
    hDomainStep
    hStepAtDomain

theorem resolvesSinglePublishedHoldAtClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {target : AircraftId}
    {fixId : FixId}
    {efc : Option String}
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.holdAt target (.published fixId) efc))
    (hContent :
      clearance.content = .single (.holdAt target (.published fixId) efc))
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  rcases resolvesIndexedPublishedHoldAt_of_ready (world := world) (state := initialState)
      (index := 0) hReady with ⟨step, hStep⟩
  have hStepDomain : step.domain = .route := by
    cases hStep <;> simp [compileResolvedStep, instructionDomain?]
  have hDomainStep : clearance.domain = step.domain := by
    simpa [hStepDomain] using hDomain
  have hStepAtDomain :
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance.domain
        0
        (.holdAt target (.published fixId) efc)
        step
        initialState := by
    simpa [hDomain] using hStep
  have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
    cases clearance with
    | mk id aircraft content domain issuedBy issuedAt status condition =>
        dsimp at hContent hCondition
        subst content
        subst condition
        simp [normalizeConditionalEnvelope, instructionMayBeConditional]
  refine ⟨singletonResolvedClearance clearance step, ?_⟩
  exact resolvesSingleInstructionClearance
    hNormalized
    hContent
    hDomainStep
    hStepAtDomain

theorem resolvesSingleNonCirclingClearedApproachClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {target : AircraftId}
    {approachType : ApproachType}
    {runway : RunwayId}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.clearedApproach target approachType runway none))
    (hContent :
      clearance.content = .single (.clearedApproach target approachType runway none))
    (hDomain : clearance.domain = .route)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  rcases resolvesIndexedNonCirclingClearedApproach_of_ready
      (world := world) (state := initialState) (index := 0) hWf hReady with ⟨step, hStep⟩
  have hStepDomain : step.domain = .route := by
    cases hStep <;> simp [compileResolvedStep, instructionDomain?]
  have hDomainStep : clearance.domain = step.domain := by
    simpa [hStepDomain] using hDomain
  have hStepAtDomain :
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance.domain
        0
        (.clearedApproach target approachType runway none)
        step
        initialState := by
    simpa [hDomain] using hStep
  have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
    cases clearance with
    | mk id aircraft content domain issuedBy issuedAt status condition =>
        dsimp at hContent hCondition
        subst content
        subst condition
        simp [normalizeConditionalEnvelope, instructionMayBeConditional]
  refine ⟨singletonResolvedClearance clearance step, ?_⟩
  exact resolvesSingleInstructionClearance
    hNormalized
    hContent
    hDomainStep
    hStepAtDomain

theorem resolvesSingleJoinCircuitClearance_of_ready
    {world : RouteBearingScopedAviationWorld}
    {initialState : ResolutionState}
    {clearance : StructuredClearance}
    {target : AircraftId}
    {direction : CircuitDirection}
    {joinType : JoinType}
    {runway : Option RunwayId}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReady :
      RouteBearingInstructionResolutionReady world
        (.joinCircuit target direction joinType runway))
    (hContent :
      clearance.content = .single (.joinCircuit target direction joinType runway))
    (hDomain : clearance.domain = .runway)
    (hCondition : clearance.condition = none) :
    ∃ resolved,
      ResolvesClearance
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance
        resolved
        initialState := by
  rcases resolvesIndexedJoinCircuit_of_ready
      (world := world) (state := initialState) (index := 0) hWf hReady with ⟨step, hStep⟩
  have hStepDomain : step.domain = .runway := by
    cases hStep <;> simp [compileResolvedStep, instructionDomain?]
  have hDomainStep : clearance.domain = step.domain := by
    simpa [hStepDomain] using hDomain
  have hStepAtDomain :
      ResolvesIndexedStep
        (RouteBearingScopedAviationWorld.toResolutionWorld world)
        initialState
        clearance.domain
        0
        (.joinCircuit target direction joinType runway)
        step
        initialState := by
    simpa [hDomain] using hStep
  have hNormalized : normalizeConditionalEnvelope clearance = .ok clearance := by
    cases clearance with
    | mk id aircraft content domain issuedBy issuedAt status condition =>
        dsimp at hContent hCondition
        subst content
        subst condition
        simp [normalizeConditionalEnvelope, instructionMayBeConditional]
  refine ⟨singletonResolvedClearance clearance step, ?_⟩
  exact resolvesSingleInstructionClearance
    hNormalized
    hContent
    hDomainStep
    hStepAtDomain

end Greenfield
end CertifiedAtc
