import CertifiedAtc.ScopedExtraction
import CertifiedAtc.GreenfieldModel

namespace CertifiedAtc

/--
Procedure-bearing source extensions above the scoped nominal extraction world.

This module intentionally widens only the portion of `AviationWorld` that the
first route-bearing proof increment needs:

- circuits
- holding patterns
- approaches
- SIDs / airways / STARs / VFR routes
- fixes

The scoped nominal claim remains closed and unchanged; this module is the next
procedure-bearing extraction layer above it.
-/

def legacyApproachTypeString : Greenfield.ApproachType → String
  | .ils => "ILS"
  | .loc => "LOC"
  | .rnav => "RNAV"
  | .rnp => "RNP"
  | .vor => "VOR"
  | .ndb => "NDB"
  | .sra => "SRA"
  | .visual => "VISUAL"
  | .par => "PAR"

def greenfieldCircuitDirection : CircuitDirection → Greenfield.CircuitDirection
  | .left => .leftHand
  | .right => .rightHand

structure ScopedCircuitJoinSource where
  type : Greenfield.JoinType
  entryPoint : PointId
  entryPath : Option (List PointId) := none
  deriving DecidableEq, Repr

structure ScopedCircuitSource where
  id : CircuitProcedureId
  runway : RunwayId
  direction : CircuitDirection
  altitude : Greenfield.Level
  legs : List CompileCircuitLegView
  altitudeFt : Int
  reportingPoints : List CompileReportingPointView := []
  joinProcedures : List ScopedCircuitJoinSource := []
  extendedDownwind : Option CompileExtendedDownwindView := none
  deriving DecidableEq, Repr

def ScopedCircuitSource.toCompileView
    (circuit : ScopedCircuitSource) : CompileCircuitProcedureView :=
  { id := circuit.id
    runway := circuit.runway
    direction := circuit.direction
    legs := circuit.legs
    altitudeFt := circuit.altitudeFt
    reportingPoints := circuit.reportingPoints
    extendedDownwind := circuit.extendedDownwind }

structure ScopedHoldingPatternSource where
  id : HoldingPatternId
  fix : FixId
  fixPoint : PointId
  inboundCourseDegrees : Int
  turnDirection : OrbitDirection
  path : List PointId
  edges : List AirEdgeId
  altitudeFt : Int
  stackSeparationFt : Option Int := none
  deriving DecidableEq, Repr

def ScopedHoldingPatternSource.toCompileView
    (hold : ScopedHoldingPatternSource) : CompileHoldingPatternView :=
  { id := hold.id
    fix := hold.fix
    fixPoint := hold.fixPoint
    inboundCourseDegrees := hold.inboundCourseDegrees
    turnDirection := hold.turnDirection
    path := hold.path
    edges := hold.edges
    altitudeFt := hold.altitudeFt
    stackSeparationFt := hold.stackSeparationFt }

structure ScopedApproachSource where
  id : ApproachId
  runway : RunwayId
  kind : Greenfield.ApproachType
  waypoints : List CompileWaypointView
  threshold : PointId
  missedApproach : CompileMissedApproachView
  deriving DecidableEq, Repr

def ScopedApproachSource.toCompileView
    (approach : ScopedApproachSource) : CompileApproachView :=
  { id := approach.id
    runway := approach.runway
    kind := legacyApproachTypeString approach.kind
    waypoints := approach.waypoints
    threshold := approach.threshold
    missedApproach := approach.missedApproach }

structure ScopedSidSource where
  id : SidId
  runway : RunwayId
  waypoints : List CompileWaypointView
  connectsTo : Option AirwayId := none
  deriving DecidableEq, Repr

def ScopedSidSource.toCompileView
    (sid : ScopedSidSource) : CompileSidView :=
  { id := sid.id
    runway := sid.runway
    waypoints := sid.waypoints
    connectsTo := sid.connectsTo }

structure ScopedAirwaySource where
  id : AirwayId
  waypoints : List CompileWaypointView
  bidirectional : Bool
  deriving DecidableEq, Repr

def ScopedAirwaySource.toCompileView
    (airway : ScopedAirwaySource) : CompileAirwayView :=
  { id := airway.id
    waypoints := airway.waypoints
    bidirectional := airway.bidirectional }

structure ScopedStarSource where
  id : StarId
  waypoints : List CompileWaypointView
  connectsTo : Option ApproachId := none
  deriving DecidableEq, Repr

def ScopedStarSource.toCompileView
    (star : ScopedStarSource) : CompileStarView :=
  { id := star.id
    waypoints := star.waypoints
    connectsTo := star.connectsTo }

structure ScopedVfrRouteSource where
  id : VfrRouteId
  waypoints : List CompileWaypointView
  airspaceClass : AirspaceClass
  deriving DecidableEq, Repr

def ScopedVfrRouteSource.toCompileView
    (route : ScopedVfrRouteSource) : CompileVfrRouteView :=
  { id := route.id
    waypoints := route.waypoints
    airspaceClass := route.airspaceClass }

structure ScopedFixSource where
  id : FixId
  point : PointId
  name : String
  deriving DecidableEq, Repr

def ScopedFixSource.toCompileView
    (fix : ScopedFixSource) : CompileFixView :=
  { id := fix.id
    point := fix.point
    name := fix.name }

structure RouteBearingScopedAviationWorld extends ScopedAviationWorld where
  circuits : List ScopedCircuitSource := []
  holdingPatterns : List ScopedHoldingPatternSource := []
  approaches : List ScopedApproachSource := []
  sids : List ScopedSidSource := []
  airways : List ScopedAirwaySource := []
  stars : List ScopedStarSource := []
  vfrRoutes : List ScopedVfrRouteSource := []
  fixes : List ScopedFixSource := []
  deriving Repr

def extractRouteBearingCompileView
    (world : RouteBearingScopedAviationWorld) : ClearanceCompileView :=
  { (extractCompileView world.toScopedAviationWorld) with
      circuits := world.circuits.map ScopedCircuitSource.toCompileView
      holdingPatterns := world.holdingPatterns.map ScopedHoldingPatternSource.toCompileView
      approaches := world.approaches.map ScopedApproachSource.toCompileView
      sids := world.sids.map ScopedSidSource.toCompileView
      airways := world.airways.map ScopedAirwaySource.toCompileView
      stars := world.stars.map ScopedStarSource.toCompileView
      vfrRoutes := world.vfrRoutes.map ScopedVfrRouteSource.toCompileView
      fixes := world.fixes.map ScopedFixSource.toCompileView }

structure RouteBearingExtractionWellFormed
    (world : RouteBearingScopedAviationWorld) : Prop where
  baseWellFormed : ScopedExtractionWellFormed (world.toScopedAviationWorld)
  circuitIds :
    (world.circuits.map (fun circuit => circuit.id)).Nodup
  holdingPatternIds :
    (world.holdingPatterns.map (fun hold => hold.id)).Nodup
  approachIds :
    (world.approaches.map (fun approach => approach.id)).Nodup
  sidIds :
    (world.sids.map (fun sid => sid.id)).Nodup
  airwayIds :
    (world.airways.map (fun airway => airway.id)).Nodup
  starIds :
    (world.stars.map (fun star => star.id)).Nodup
  vfrRouteIds :
    (world.vfrRoutes.map (fun route => route.id)).Nodup
  fixIds :
    (world.fixes.map (fun fix => fix.id)).Nodup
  circuitsKnownRunways :
    ∀ circuit ∈ world.circuits,
      ∃ runway ∈ world.toScopedAviationWorld.runways, runway.id = circuit.runway
  holdingPatternsKnownFixes :
    ∀ hold ∈ world.holdingPatterns,
      ∃ fix ∈ world.fixes, fix.id = hold.fix ∧ fix.point = hold.fixPoint
  approachesKnownRunways :
    ∀ approach ∈ world.approaches,
      ∃ runway ∈ world.toScopedAviationWorld.runways, runway.id = approach.runway
  sidsKnownRunways :
    ∀ sid ∈ world.sids,
      ∃ runway ∈ world.toScopedAviationWorld.runways, runway.id = sid.runway
  sidWaypointsNonempty :
    ∀ sid ∈ world.sids, sid.waypoints ≠ []
  airwayWaypointsNonempty :
    ∀ airway ∈ world.airways, airway.waypoints ≠ []
  starWaypointsNonempty :
    ∀ star ∈ world.stars, star.waypoints ≠ []
  vfrRouteWaypointsNonempty :
    ∀ route ∈ world.vfrRoutes, route.waypoints ≠ []
  sidConnectionsKnown :
    ∀ sid ∈ world.sids,
      ∀ airwayId, sid.connectsTo = some airwayId →
        ∃ airway ∈ world.airways, airway.id = airwayId
  starConnectionsKnown :
    ∀ star ∈ world.stars,
      ∀ approachId, star.connectsTo = some approachId →
        ∃ approach ∈ world.approaches, approach.id = approachId
  missedApproachHoldsKnown :
    ∀ approach ∈ world.approaches,
      ∀ holdId, approach.missedApproach.holdAt = some holdId →
        ∃ hold ∈ world.holdingPatterns, hold.id = holdId

def ProcedureRefKnown
    (world : RouteBearingScopedAviationWorld) : ProcedureRef → Prop
  | .viaSid sidId =>
      ∃ sid ∈ world.sids, sid.id = sidId
  | .viaAirway airwayId =>
      ∃ airway ∈ world.airways, airway.id = airwayId
  | .viaStar starId =>
      ∃ star ∈ world.stars, star.id = starId
  | .viaRoute routeId =>
      ∃ route ∈ world.vfrRoutes, route.id = routeId
  | .direct fixId =>
      ∃ fix ∈ world.fixes, fix.id = fixId

def ProcedureRefExtractable
    (view : ClearanceCompileView) : ProcedureRef → Prop
  | .viaSid sidId =>
      ∃ sidView, findCompileSid view sidId = some sidView
  | .viaAirway airwayId =>
      ∃ airwayView, findCompileAirway view airwayId = some airwayView
  | .viaStar starId =>
      ∃ starView, findCompileStar view starId = some starView
  | .viaRoute routeId =>
      ∃ routeView, findCompileVfrRoute view routeId = some routeView
  | .direct fixId =>
      ∃ fixView, findCompileFix view fixId = some fixView

def RouteBearingInstructionReferencesKnown
    (world : RouteBearingScopedAviationWorld) : ClearanceInstruction → Prop
  | .joinCircuit _ circuitId _ =>
      ∃ circuit ∈ world.circuits, circuit.id = circuitId
  | .clearedApproach _ approachId =>
      ∃ approach ∈ world.approaches, approach.id = approachId
  | .holdAt _ holdId =>
      ∃ hold ∈ world.holdingPatterns, hold.id = holdId
  | .clearedTo _ _ via limit _ =>
      ProcedureRefKnown world via ∧
        match limit with
        | none => True
        | some fixId => ∃ fix ∈ world.fixes, fix.id = fixId
  | _ => False

def RouteBearingInstructionReferencesExtractable
    (view : ClearanceCompileView) : ClearanceInstruction → Prop
  | .joinCircuit _ circuitId _ =>
      ∃ circuitView, findCompileCircuit view circuitId = some circuitView
  | .clearedApproach _ approachId =>
      ∃ approachView, findCompileApproach view approachId = some approachView
  | .holdAt _ holdId =>
      ∃ holdView, findCompileHoldingPattern view holdId = some holdView
  | .clearedTo _ _ via limit _ =>
      ProcedureRefExtractable view via ∧
        match limit with
        | none => True
        | some fixId => ∃ fixView, findCompileFix view fixId = some fixView
  | _ => False

def ProcedureRefLimitSupported
    (world : RouteBearingScopedAviationWorld)
    (via : ProcedureRef)
    (limitId : FixId) : Prop :=
  ∃ fix ∈ world.fixes, fix.id = limitId ∧
    match via with
    | .viaSid sidId =>
        ∃ sid ∈ world.sids, sid.id = sidId ∧ fix.point ∈ routePointsOfWaypoints sid.waypoints
    | .viaAirway airwayId =>
        ∃ airway ∈ world.airways, airway.id = airwayId ∧
          fix.point ∈ routePointsOfWaypoints airway.waypoints
    | .viaStar starId =>
        ∃ star ∈ world.stars, star.id = starId ∧
          fix.point ∈ routePointsOfWaypoints star.waypoints
    | .viaRoute routeId =>
        ∃ route ∈ world.vfrRoutes, route.id = routeId ∧
          fix.point ∈ routePointsOfWaypoints route.waypoints
    | .direct fixId =>
        fix.id = fixId

def RouteBearingInstructionCompileReady
    (world : RouteBearingScopedAviationWorld) : ClearanceInstruction → Prop
  | .joinCircuit _ circuitId _ =>
      ∃ circuit ∈ world.circuits, circuit.id = circuitId
  | .clearedApproach _ approachId =>
      ∃ approach ∈ world.approaches, approach.id = approachId
  | .holdAt _ holdId =>
      ∃ hold ∈ world.holdingPatterns, hold.id = holdId
  | .clearedTo _ _ via limit _ =>
      match limit with
      | none => ProcedureRefKnown world via
      | some limitId => ProcedureRefLimitSupported world via limitId
  | _ => False

theorem procedureRefLimitSupported_implies_known
    {world : RouteBearingScopedAviationWorld}
    {via : ProcedureRef}
    {limitId : FixId}
    (hLimit : ProcedureRefLimitSupported world via limitId) :
    ProcedureRefKnown world via := by
  cases via with
  | viaSid sidId =>
      rcases hLimit with ⟨_, _, _, sid, hSidMem, hSidEq, _⟩
      exact ⟨sid, hSidMem, hSidEq⟩
  | viaAirway airwayId =>
      rcases hLimit with ⟨_, _, _, airway, hAirwayMem, hAirwayEq, _⟩
      exact ⟨airway, hAirwayMem, hAirwayEq⟩
  | viaStar starId =>
      rcases hLimit with ⟨_, _, _, star, hStarMem, hStarEq, _⟩
      exact ⟨star, hStarMem, hStarEq⟩
  | viaRoute routeId =>
      rcases hLimit with ⟨_, _, _, route, hRouteMem, hRouteEq, _⟩
      exact ⟨route, hRouteMem, hRouteEq⟩
  | direct fixId =>
      rcases hLimit with ⟨fix, hFixMem, _, hDirect⟩
      exact ⟨fix, hFixMem, hDirect⟩

theorem routeBearingInstructionCompileReady_implies_known
    {world : RouteBearingScopedAviationWorld}
    {instruction : ClearanceInstruction}
    (hReady : RouteBearingInstructionCompileReady world instruction) :
    RouteBearingInstructionReferencesKnown world instruction := by
  cases instruction with
  | joinCircuit target circuitId joinType =>
      exact hReady
  | clearedApproach target approachId =>
      exact hReady
  | holdAt target holdId =>
      exact hReady
  | clearedTo target destination via limit altitude =>
      cases limit with
      | none =>
          have hKnown : ProcedureRefKnown world via := by
            simpa [RouteBearingInstructionCompileReady] using hReady
          exact ⟨hKnown, trivial⟩
      | some limitId =>
          have hLimit : ProcedureRefLimitSupported world via limitId := by
            simpa [RouteBearingInstructionCompileReady] using hReady
          have hKnown : ProcedureRefKnown world via :=
            procedureRefLimitSupported_implies_known hLimit
          rcases hLimit with ⟨fix, hFixMem, hFixEq, _⟩
          exact ⟨hKnown, ⟨fix, hFixMem, hFixEq⟩⟩
  | _ =>
      cases hReady

theorem routePointsOfWaypoints_ne_nil_of_ne_nil
    {waypoints : List CompileWaypointView}
    (hWaypoints : waypoints ≠ []) :
    routePointsOfWaypoints waypoints ≠ [] := by
  cases waypoints with
  | nil =>
      contradiction
  | cons head tail =>
      simp [routePointsOfWaypoints]

theorem truncateRouteAtPoint_eq_some_of_mem
    {route : List AirNodeId}
    {point : AirNodeId}
    (hMem : point ∈ route) :
    ∃ truncated, truncateRouteAtPoint route point = some truncated := by
  have hGo :
      ∀ (prefixRev remaining : List AirNodeId),
        point ∈ remaining →
          ∃ suffix,
            truncateRouteAtPoint.go point remaining prefixRev =
              some (prefixRev.reverse ++ suffix) := by
    intro prefixRev remaining
    induction remaining generalizing prefixRev with
    | nil =>
        intro hRemaining
        cases hRemaining
    | cons head tail ih =>
        intro hRemaining
        by_cases hHead : head = point
        · refine ⟨[point], ?_⟩
          simp [truncateRouteAtPoint.go, hHead, List.reverse_cons]
        · have hTail : point ∈ tail := by
            simp at hRemaining
            exact hRemaining.resolve_left (fun hEq => hHead hEq.symm)
          rcases ih (head :: prefixRev) hTail with ⟨suffix, hSuffix⟩
          refine ⟨head :: suffix, ?_⟩
          simp [truncateRouteAtPoint.go, hHead, hSuffix, List.reverse_cons, List.append_assoc]
  simpa [truncateRouteAtPoint] using hGo [] route hMem

theorem findCompileCircuit_go_eq_some_of_mem
    {items : List CompileCircuitProcedureView}
    {circuit : CompileCircuitProcedureView}
    (hMem : circuit ∈ items)
    (hNodup : (items.map CompileCircuitProcedureView.id).Nodup) :
    findCompileCircuit.go circuit.id items = some circuit := by
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [findCompileCircuit.go]
      · have hHeadNe : head.id ≠ circuit.id := by
          intro hEq
          exact (hHeadNotIn circuit hTailMem) (by simp [hEq])
        simp [findCompileCircuit.go, hHeadNe, ih hTailMem hTailNodup]

theorem findCompileHoldingPattern_go_eq_some_of_mem
    {items : List CompileHoldingPatternView}
    {hold : CompileHoldingPatternView}
    (hMem : hold ∈ items)
    (hNodup : (items.map CompileHoldingPatternView.id).Nodup) :
    findCompileHoldingPattern.go hold.id items = some hold := by
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [findCompileHoldingPattern.go]
      · have hHeadNe : head.id ≠ hold.id := by
          intro hEq
          exact (hHeadNotIn hold hTailMem) (by simp [hEq])
        simp [findCompileHoldingPattern.go, hHeadNe, ih hTailMem hTailNodup]

theorem findCompileApproach_go_eq_some_of_mem
    {items : List CompileApproachView}
    {approach : CompileApproachView}
    (hMem : approach ∈ items)
    (hNodup : (items.map CompileApproachView.id).Nodup) :
    findCompileApproach.go approach.id items = some approach := by
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [findCompileApproach.go]
      · have hHeadNe : head.id ≠ approach.id := by
          intro hEq
          exact (hHeadNotIn approach hTailMem) (by simp [hEq])
        simp [findCompileApproach.go, hHeadNe, ih hTailMem hTailNodup]

theorem findCompileSid_go_eq_some_of_mem
    {items : List CompileSidView}
    {sid : CompileSidView}
    (hMem : sid ∈ items)
    (hNodup : (items.map CompileSidView.id).Nodup) :
    findCompileSid.go sid.id items = some sid := by
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [findCompileSid.go]
      · have hHeadNe : head.id ≠ sid.id := by
          intro hEq
          exact (hHeadNotIn sid hTailMem) (by simp [hEq])
        simp [findCompileSid.go, hHeadNe, ih hTailMem hTailNodup]

theorem findCompileAirway_go_eq_some_of_mem
    {items : List CompileAirwayView}
    {airway : CompileAirwayView}
    (hMem : airway ∈ items)
    (hNodup : (items.map CompileAirwayView.id).Nodup) :
    findCompileAirway.go airway.id items = some airway := by
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [findCompileAirway.go]
      · have hHeadNe : head.id ≠ airway.id := by
          intro hEq
          exact (hHeadNotIn airway hTailMem) (by simp [hEq])
        simp [findCompileAirway.go, hHeadNe, ih hTailMem hTailNodup]

theorem findCompileStar_go_eq_some_of_mem
    {items : List CompileStarView}
    {star : CompileStarView}
    (hMem : star ∈ items)
    (hNodup : (items.map CompileStarView.id).Nodup) :
    findCompileStar.go star.id items = some star := by
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [findCompileStar.go]
      · have hHeadNe : head.id ≠ star.id := by
          intro hEq
          exact (hHeadNotIn star hTailMem) (by simp [hEq])
        simp [findCompileStar.go, hHeadNe, ih hTailMem hTailNodup]

theorem findCompileVfrRoute_go_eq_some_of_mem
    {items : List CompileVfrRouteView}
    {route : CompileVfrRouteView}
    (hMem : route ∈ items)
    (hNodup : (items.map CompileVfrRouteView.id).Nodup) :
    findCompileVfrRoute.go route.id items = some route := by
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [findCompileVfrRoute.go]
      · have hHeadNe : head.id ≠ route.id := by
          intro hEq
          exact (hHeadNotIn route hTailMem) (by simp [hEq])
        simp [findCompileVfrRoute.go, hHeadNe, ih hTailMem hTailNodup]

theorem findCompileFix_go_eq_some_of_mem
    {items : List CompileFixView}
    {fix : CompileFixView}
    (hMem : fix ∈ items)
    (hNodup : (items.map CompileFixView.id).Nodup) :
    findCompileFix.go fix.id items = some fix := by
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [findCompileFix.go]
      · have hHeadNe : head.id ≠ fix.id := by
          intro hEq
          exact (hHeadNotIn fix hTailMem) (by simp [hEq])
        simp [findCompileFix.go, hHeadNe, ih hTailMem hTailNodup]

theorem extractRouteBearingCompileView_circuit_origin
    {world : RouteBearingScopedAviationWorld}
    {circuitView : CompileCircuitProcedureView}
    (hMem : circuitView ∈ (extractRouteBearingCompileView world).circuits) :
    ∃ source ∈ world.circuits, source.toCompileView = circuitView := by
  unfold extractRouteBearingCompileView at hMem
  rcases List.mem_map.mp hMem with ⟨source, hSource, hEq⟩
  exact ⟨source, hSource, hEq⟩

theorem extractRouteBearingCompileView_holdingPattern_origin
    {world : RouteBearingScopedAviationWorld}
    {holdView : CompileHoldingPatternView}
    (hMem : holdView ∈ (extractRouteBearingCompileView world).holdingPatterns) :
    ∃ source ∈ world.holdingPatterns, source.toCompileView = holdView := by
  unfold extractRouteBearingCompileView at hMem
  rcases List.mem_map.mp hMem with ⟨source, hSource, hEq⟩
  exact ⟨source, hSource, hEq⟩

theorem extractRouteBearingCompileView_approach_origin
    {world : RouteBearingScopedAviationWorld}
    {approachView : CompileApproachView}
    (hMem : approachView ∈ (extractRouteBearingCompileView world).approaches) :
    ∃ source ∈ world.approaches, source.toCompileView = approachView := by
  unfold extractRouteBearingCompileView at hMem
  rcases List.mem_map.mp hMem with ⟨source, hSource, hEq⟩
  exact ⟨source, hSource, hEq⟩

theorem extractRouteBearingCompileView_sid_origin
    {world : RouteBearingScopedAviationWorld}
    {sidView : CompileSidView}
    (hMem : sidView ∈ (extractRouteBearingCompileView world).sids) :
    ∃ source ∈ world.sids, source.toCompileView = sidView := by
  unfold extractRouteBearingCompileView at hMem
  rcases List.mem_map.mp hMem with ⟨source, hSource, hEq⟩
  exact ⟨source, hSource, hEq⟩

theorem extractRouteBearingCompileView_airway_origin
    {world : RouteBearingScopedAviationWorld}
    {airwayView : CompileAirwayView}
    (hMem : airwayView ∈ (extractRouteBearingCompileView world).airways) :
    ∃ source ∈ world.airways, source.toCompileView = airwayView := by
  unfold extractRouteBearingCompileView at hMem
  rcases List.mem_map.mp hMem with ⟨source, hSource, hEq⟩
  exact ⟨source, hSource, hEq⟩

theorem extractRouteBearingCompileView_star_origin
    {world : RouteBearingScopedAviationWorld}
    {starView : CompileStarView}
    (hMem : starView ∈ (extractRouteBearingCompileView world).stars) :
    ∃ source ∈ world.stars, source.toCompileView = starView := by
  unfold extractRouteBearingCompileView at hMem
  rcases List.mem_map.mp hMem with ⟨source, hSource, hEq⟩
  exact ⟨source, hSource, hEq⟩

theorem extractRouteBearingCompileView_vfrRoute_origin
    {world : RouteBearingScopedAviationWorld}
    {routeView : CompileVfrRouteView}
    (hMem : routeView ∈ (extractRouteBearingCompileView world).vfrRoutes) :
    ∃ source ∈ world.vfrRoutes, source.toCompileView = routeView := by
  unfold extractRouteBearingCompileView at hMem
  rcases List.mem_map.mp hMem with ⟨source, hSource, hEq⟩
  exact ⟨source, hSource, hEq⟩

theorem extractRouteBearingCompileView_fix_origin
    {world : RouteBearingScopedAviationWorld}
    {fixView : CompileFixView}
    (hMem : fixView ∈ (extractRouteBearingCompileView world).fixes) :
    ∃ source ∈ world.fixes, source.toCompileView = fixView := by
  unfold extractRouteBearingCompileView at hMem
  rcases List.mem_map.mp hMem with ⟨source, hSource, hEq⟩
  exact ⟨source, hSource, hEq⟩

theorem findCompileCircuit_eq_some_of_mem
    {world : RouteBearingScopedAviationWorld}
    {circuit : ScopedCircuitSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : circuit ∈ world.circuits) :
    findCompileCircuit (extractRouteBearingCompileView world) circuit.id =
      some circuit.toCompileView := by
  have hMemCompile :
      circuit.toCompileView ∈
        world.circuits.map ScopedCircuitSource.toCompileView := by
    exact List.mem_map.mpr ⟨circuit, hMem, rfl⟩
  have hNodupCompile :
      (List.map CompileCircuitProcedureView.id
        (world.circuits.map ScopedCircuitSource.toCompileView)).Nodup := by
    simpa [ScopedCircuitSource.toCompileView] using hWf.circuitIds
  unfold findCompileCircuit
  simpa [extractRouteBearingCompileView, ScopedCircuitSource.toCompileView] using
    (findCompileCircuit_go_eq_some_of_mem
      (items := world.circuits.map ScopedCircuitSource.toCompileView)
      (circuit := circuit.toCompileView)
      hMemCompile
      hNodupCompile)

theorem findCompileHoldingPattern_eq_some_of_mem
    {world : RouteBearingScopedAviationWorld}
    {hold : ScopedHoldingPatternSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : hold ∈ world.holdingPatterns) :
    findCompileHoldingPattern (extractRouteBearingCompileView world) hold.id =
      some hold.toCompileView := by
  have hMemCompile :
      hold.toCompileView ∈
        world.holdingPatterns.map ScopedHoldingPatternSource.toCompileView := by
    exact List.mem_map.mpr ⟨hold, hMem, rfl⟩
  have hNodupCompile :
      (List.map CompileHoldingPatternView.id
        (world.holdingPatterns.map ScopedHoldingPatternSource.toCompileView)).Nodup := by
    simpa [ScopedHoldingPatternSource.toCompileView] using hWf.holdingPatternIds
  unfold findCompileHoldingPattern
  simpa [extractRouteBearingCompileView, ScopedHoldingPatternSource.toCompileView] using
    (findCompileHoldingPattern_go_eq_some_of_mem
      (items := world.holdingPatterns.map ScopedHoldingPatternSource.toCompileView)
      (hold := hold.toCompileView)
      hMemCompile
      hNodupCompile)

theorem findCompileApproach_eq_some_of_mem
    {world : RouteBearingScopedAviationWorld}
    {approach : ScopedApproachSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : approach ∈ world.approaches) :
    findCompileApproach (extractRouteBearingCompileView world) approach.id =
      some approach.toCompileView := by
  have hMemCompile :
      approach.toCompileView ∈
        world.approaches.map ScopedApproachSource.toCompileView := by
    exact List.mem_map.mpr ⟨approach, hMem, rfl⟩
  have hNodupCompile :
      (List.map CompileApproachView.id
        (world.approaches.map ScopedApproachSource.toCompileView)).Nodup := by
    simpa [ScopedApproachSource.toCompileView] using hWf.approachIds
  unfold findCompileApproach
  simpa [extractRouteBearingCompileView, ScopedApproachSource.toCompileView] using
    (findCompileApproach_go_eq_some_of_mem
      (items := world.approaches.map ScopedApproachSource.toCompileView)
      (approach := approach.toCompileView)
      hMemCompile
      hNodupCompile)

theorem findCompileSid_eq_some_of_mem
    {world : RouteBearingScopedAviationWorld}
    {sid : ScopedSidSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : sid ∈ world.sids) :
    findCompileSid (extractRouteBearingCompileView world) sid.id =
      some sid.toCompileView := by
  have hMemCompile :
      sid.toCompileView ∈
        world.sids.map ScopedSidSource.toCompileView := by
    exact List.mem_map.mpr ⟨sid, hMem, rfl⟩
  have hNodupCompile :
      (List.map CompileSidView.id
        (world.sids.map ScopedSidSource.toCompileView)).Nodup := by
    simpa [ScopedSidSource.toCompileView] using hWf.sidIds
  unfold findCompileSid
  simpa [extractRouteBearingCompileView, ScopedSidSource.toCompileView] using
    (findCompileSid_go_eq_some_of_mem
      (items := world.sids.map ScopedSidSource.toCompileView)
      (sid := sid.toCompileView)
      hMemCompile
      hNodupCompile)

theorem findCompileAirway_eq_some_of_mem
    {world : RouteBearingScopedAviationWorld}
    {airway : ScopedAirwaySource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : airway ∈ world.airways) :
    findCompileAirway (extractRouteBearingCompileView world) airway.id =
      some airway.toCompileView := by
  have hMemCompile :
      airway.toCompileView ∈
        world.airways.map ScopedAirwaySource.toCompileView := by
    exact List.mem_map.mpr ⟨airway, hMem, rfl⟩
  have hNodupCompile :
      (List.map CompileAirwayView.id
        (world.airways.map ScopedAirwaySource.toCompileView)).Nodup := by
    simpa [ScopedAirwaySource.toCompileView] using hWf.airwayIds
  unfold findCompileAirway
  simpa [extractRouteBearingCompileView, ScopedAirwaySource.toCompileView] using
    (findCompileAirway_go_eq_some_of_mem
      (items := world.airways.map ScopedAirwaySource.toCompileView)
      (airway := airway.toCompileView)
      hMemCompile
      hNodupCompile)

theorem findCompileStar_eq_some_of_mem
    {world : RouteBearingScopedAviationWorld}
    {star : ScopedStarSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : star ∈ world.stars) :
    findCompileStar (extractRouteBearingCompileView world) star.id =
      some star.toCompileView := by
  have hMemCompile :
      star.toCompileView ∈
        world.stars.map ScopedStarSource.toCompileView := by
    exact List.mem_map.mpr ⟨star, hMem, rfl⟩
  have hNodupCompile :
      (List.map CompileStarView.id
        (world.stars.map ScopedStarSource.toCompileView)).Nodup := by
    simpa [ScopedStarSource.toCompileView] using hWf.starIds
  unfold findCompileStar
  simpa [extractRouteBearingCompileView, ScopedStarSource.toCompileView] using
    (findCompileStar_go_eq_some_of_mem
      (items := world.stars.map ScopedStarSource.toCompileView)
      (star := star.toCompileView)
      hMemCompile
      hNodupCompile)

theorem findCompileVfrRoute_eq_some_of_mem
    {world : RouteBearingScopedAviationWorld}
    {route : ScopedVfrRouteSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : route ∈ world.vfrRoutes) :
    findCompileVfrRoute (extractRouteBearingCompileView world) route.id =
      some route.toCompileView := by
  have hMemCompile :
      route.toCompileView ∈
        world.vfrRoutes.map ScopedVfrRouteSource.toCompileView := by
    exact List.mem_map.mpr ⟨route, hMem, rfl⟩
  have hNodupCompile :
      (List.map CompileVfrRouteView.id
        (world.vfrRoutes.map ScopedVfrRouteSource.toCompileView)).Nodup := by
    simpa [ScopedVfrRouteSource.toCompileView] using hWf.vfrRouteIds
  unfold findCompileVfrRoute
  simpa [extractRouteBearingCompileView, ScopedVfrRouteSource.toCompileView] using
    (findCompileVfrRoute_go_eq_some_of_mem
      (items := world.vfrRoutes.map ScopedVfrRouteSource.toCompileView)
      (route := route.toCompileView)
      hMemCompile
      hNodupCompile)

theorem findCompileFix_eq_some_of_mem
    {world : RouteBearingScopedAviationWorld}
    {fix : ScopedFixSource}
    (hWf : RouteBearingExtractionWellFormed world)
    (hMem : fix ∈ world.fixes) :
    findCompileFix (extractRouteBearingCompileView world) fix.id =
      some fix.toCompileView := by
  have hMemCompile :
      fix.toCompileView ∈
        world.fixes.map ScopedFixSource.toCompileView := by
    exact List.mem_map.mpr ⟨fix, hMem, rfl⟩
  have hNodupCompile :
      (List.map CompileFixView.id
        (world.fixes.map ScopedFixSource.toCompileView)).Nodup := by
    simpa [ScopedFixSource.toCompileView] using hWf.fixIds
  unfold findCompileFix
  simpa [extractRouteBearingCompileView, ScopedFixSource.toCompileView] using
    (findCompileFix_go_eq_some_of_mem
      (items := world.fixes.map ScopedFixSource.toCompileView)
      (fix := fix.toCompileView)
      hMemCompile
      hNodupCompile)

theorem knownProcedureRef_preserved
    {world : RouteBearingScopedAviationWorld}
    {via : ProcedureRef}
    (hWf : RouteBearingExtractionWellFormed world)
    (hKnown : ProcedureRefKnown world via) :
    ProcedureRefExtractable (extractRouteBearingCompileView world) via := by
  cases via with
  | viaSid sidId =>
      rcases hKnown with ⟨sid, hMem, rfl⟩
      exact ⟨sid.toCompileView, findCompileSid_eq_some_of_mem hWf hMem⟩
  | viaAirway airwayId =>
      rcases hKnown with ⟨airway, hMem, rfl⟩
      exact ⟨airway.toCompileView, findCompileAirway_eq_some_of_mem hWf hMem⟩
  | viaStar starId =>
      rcases hKnown with ⟨star, hMem, rfl⟩
      exact ⟨star.toCompileView, findCompileStar_eq_some_of_mem hWf hMem⟩
  | viaRoute routeId =>
      rcases hKnown with ⟨route, hMem, rfl⟩
      exact ⟨route.toCompileView, findCompileVfrRoute_eq_some_of_mem hWf hMem⟩
  | direct fixId =>
      rcases hKnown with ⟨fix, hMem, rfl⟩
      exact ⟨fix.toCompileView, findCompileFix_eq_some_of_mem hWf hMem⟩

theorem knownRouteBearingInstructionReferences_preserved
    {world : RouteBearingScopedAviationWorld}
    {instruction : ClearanceInstruction}
    (hWf : RouteBearingExtractionWellFormed world)
    (hKnown : RouteBearingInstructionReferencesKnown world instruction) :
    RouteBearingInstructionReferencesExtractable
      (extractRouteBearingCompileView world) instruction := by
  cases instruction with
  | joinCircuit target circuitId joinType =>
      rcases hKnown with ⟨circuit, hMem, rfl⟩
      exact ⟨circuit.toCompileView, findCompileCircuit_eq_some_of_mem hWf hMem⟩
  | clearedApproach target approachId =>
      rcases hKnown with ⟨approach, hMem, rfl⟩
      exact ⟨approach.toCompileView, findCompileApproach_eq_some_of_mem hWf hMem⟩
  | holdAt target holdId =>
      rcases hKnown with ⟨hold, hMem, rfl⟩
      exact ⟨hold.toCompileView, findCompileHoldingPattern_eq_some_of_mem hWf hMem⟩
  | clearedTo target destination via limit altitude =>
      rcases hKnown with ⟨hVia, hLimit⟩
      constructor
      · exact knownProcedureRef_preserved hWf hVia
      · cases limit with
        | none =>
            trivial
        | some fixId =>
            rcases hLimit with ⟨fix, hMem, rfl⟩
            exact ⟨fix.toCompileView, findCompileFix_eq_some_of_mem hWf hMem⟩
  | _ =>
      cases hKnown

theorem compileClearanceCommand_joinCircuit_ok_of_known
    {world : RouteBearingScopedAviationWorld}
    {target : EntityId}
    {circuitId : CircuitProcedureId}
    {joinType : JoinType}
    (hWf : RouteBearingExtractionWellFormed world)
    (hKnown : ∃ circuit ∈ world.circuits, circuit.id = circuitId) :
    ∃ command,
      compileClearanceCommand
        (extractRouteBearingCompileView world)
        (.joinCircuit target circuitId joinType) = .ok command := by
  rcases hKnown with ⟨circuit, hMem, rfl⟩
  refine ⟨.joinCircuit target circuit.direction joinType (some circuit.runway), ?_⟩
  simp [compileClearanceCommand, findCompileCircuit_eq_some_of_mem hWf hMem,
    ScopedCircuitSource.toCompileView]

theorem compileClearanceCommand_clearedApproach_ok_of_known
    {world : RouteBearingScopedAviationWorld}
    {target : EntityId}
    {approachId : ApproachId}
    (hWf : RouteBearingExtractionWellFormed world)
    (hKnown : ∃ approach ∈ world.approaches, approach.id = approachId) :
    ∃ command,
      compileClearanceCommand
        (extractRouteBearingCompileView world)
        (.clearedApproach target approachId) = .ok command := by
  rcases hKnown with ⟨approach, hMem, rfl⟩
  refine ⟨.clearedApproach target approach.runway (legacyApproachTypeString approach.kind), ?_⟩
  simp [compileClearanceCommand, findCompileApproach_eq_some_of_mem hWf hMem,
    ScopedApproachSource.toCompileView, legacyApproachTypeString]

theorem compileClearanceCommand_holdAt_ok_of_known
    {world : RouteBearingScopedAviationWorld}
    {target : EntityId}
    {holdId : HoldingPatternId}
    (hWf : RouteBearingExtractionWellFormed world)
    (hKnown : ∃ hold ∈ world.holdingPatterns, hold.id = holdId) :
    ∃ command,
      compileClearanceCommand
        (extractRouteBearingCompileView world)
        (.holdAt target holdId) = .ok command := by
  rcases hKnown with ⟨hold, hMem, rfl⟩
  refine ⟨.holdAt target hold.fixPoint hold.turnDirection, ?_⟩
  simp [compileClearanceCommand, findCompileHoldingPattern_eq_some_of_mem hWf hMem,
    ScopedHoldingPatternSource.toCompileView]

theorem compileProcedureRoute_ok_of_known_no_limit
    {world : RouteBearingScopedAviationWorld}
    {via : ProcedureRef}
    (hWf : RouteBearingExtractionWellFormed world)
    (hKnown : ProcedureRefKnown world via) :
    ∃ route,
      compileProcedureRoute
        (extractRouteBearingCompileView world)
        via
        none = .ok route := by
  cases via with
  | viaSid sidId =>
      rcases hKnown with ⟨sid, hMem, rfl⟩
      refine ⟨routePointsOfWaypoints sid.waypoints, ?_⟩
      have hNonempty := hWf.sidWaypointsNonempty sid hMem
      cases hRoute : routePointsOfWaypoints sid.waypoints with
      | nil =>
          exact False.elim ((routePointsOfWaypoints_ne_nil_of_ne_nil hNonempty) hRoute)
      | cons head tail =>
          simp [compileProcedureRoute, findCompileSid_eq_some_of_mem hWf hMem,
            ScopedSidSource.toCompileView, hRoute, applyRouteLimit]
  | viaAirway airwayId =>
      rcases hKnown with ⟨airway, hMem, rfl⟩
      refine ⟨routePointsOfWaypoints airway.waypoints, ?_⟩
      have hNonempty := hWf.airwayWaypointsNonempty airway hMem
      cases hRoute : routePointsOfWaypoints airway.waypoints with
      | nil =>
          exact False.elim ((routePointsOfWaypoints_ne_nil_of_ne_nil hNonempty) hRoute)
      | cons head tail =>
          simp [compileProcedureRoute, findCompileAirway_eq_some_of_mem hWf hMem,
            ScopedAirwaySource.toCompileView, hRoute, applyRouteLimit]
  | viaStar starId =>
      rcases hKnown with ⟨star, hMem, rfl⟩
      refine ⟨routePointsOfWaypoints star.waypoints, ?_⟩
      have hNonempty := hWf.starWaypointsNonempty star hMem
      cases hRoute : routePointsOfWaypoints star.waypoints with
      | nil =>
          exact False.elim ((routePointsOfWaypoints_ne_nil_of_ne_nil hNonempty) hRoute)
      | cons head tail =>
          simp [compileProcedureRoute, findCompileStar_eq_some_of_mem hWf hMem,
            ScopedStarSource.toCompileView, hRoute, applyRouteLimit]
  | viaRoute routeId =>
      rcases hKnown with ⟨route, hMem, rfl⟩
      refine ⟨routePointsOfWaypoints route.waypoints, ?_⟩
      have hNonempty := hWf.vfrRouteWaypointsNonempty route hMem
      cases hRoute : routePointsOfWaypoints route.waypoints with
      | nil =>
          exact False.elim ((routePointsOfWaypoints_ne_nil_of_ne_nil hNonempty) hRoute)
      | cons head tail =>
          simp [compileProcedureRoute, findCompileVfrRoute_eq_some_of_mem hWf hMem,
            ScopedVfrRouteSource.toCompileView, hRoute, applyRouteLimit]
  | direct fixId =>
      rcases hKnown with ⟨fix, hMem, rfl⟩
      exact ⟨[fix.point], by
        simp [compileProcedureRoute, findCompileFix_eq_some_of_mem hWf hMem,
          ScopedFixSource.toCompileView, applyRouteLimit]⟩

theorem compileProcedureRoute_ok_of_limit_supported
    {world : RouteBearingScopedAviationWorld}
    {via : ProcedureRef}
    {limitId : FixId}
    (hWf : RouteBearingExtractionWellFormed world)
    (hLimit : ProcedureRefLimitSupported world via limitId) :
    ∃ route,
      compileProcedureRoute
        (extractRouteBearingCompileView world)
        via
        (some limitId) = .ok route := by
  cases via with
  | viaSid sidId =>
      rcases hLimit with ⟨fix, hFixMem, rfl, sid, hSidMem, rfl, hPointMem⟩
      cases hRoute : routePointsOfWaypoints sid.waypoints with
      | nil =>
          have hPointMem' := hPointMem
          rw [hRoute] at hPointMem'
          cases hPointMem'
      | cons head tail =>
          have hPointMem' := hPointMem
          rw [hRoute] at hPointMem'
          rcases truncateRouteAtPoint_eq_some_of_mem hPointMem' with ⟨truncated, hTruncated⟩
          refine ⟨truncated, ?_⟩
          simp [compileProcedureRoute,
            findCompileSid_eq_some_of_mem hWf hSidMem,
            findCompileFix_eq_some_of_mem hWf hFixMem,
            ScopedSidSource.toCompileView,
            ScopedFixSource.toCompileView,
            hRoute,
            hTruncated,
            applyRouteLimit]
  | viaAirway airwayId =>
      rcases hLimit with ⟨fix, hFixMem, rfl, airway, hAirwayMem, rfl, hPointMem⟩
      cases hRoute : routePointsOfWaypoints airway.waypoints with
      | nil =>
          have hPointMem' := hPointMem
          rw [hRoute] at hPointMem'
          cases hPointMem'
      | cons head tail =>
          have hPointMem' := hPointMem
          rw [hRoute] at hPointMem'
          rcases truncateRouteAtPoint_eq_some_of_mem hPointMem' with ⟨truncated, hTruncated⟩
          refine ⟨truncated, ?_⟩
          simp [compileProcedureRoute,
            findCompileAirway_eq_some_of_mem hWf hAirwayMem,
            findCompileFix_eq_some_of_mem hWf hFixMem,
            ScopedAirwaySource.toCompileView,
            ScopedFixSource.toCompileView,
            hRoute,
            hTruncated,
            applyRouteLimit]
  | viaStar starId =>
      rcases hLimit with ⟨fix, hFixMem, rfl, star, hStarMem, rfl, hPointMem⟩
      cases hRoute : routePointsOfWaypoints star.waypoints with
      | nil =>
          have hPointMem' := hPointMem
          rw [hRoute] at hPointMem'
          cases hPointMem'
      | cons head tail =>
          have hPointMem' := hPointMem
          rw [hRoute] at hPointMem'
          rcases truncateRouteAtPoint_eq_some_of_mem hPointMem' with ⟨truncated, hTruncated⟩
          refine ⟨truncated, ?_⟩
          simp [compileProcedureRoute,
            findCompileStar_eq_some_of_mem hWf hStarMem,
            findCompileFix_eq_some_of_mem hWf hFixMem,
            ScopedStarSource.toCompileView,
            ScopedFixSource.toCompileView,
            hRoute,
            hTruncated,
            applyRouteLimit]
  | viaRoute routeId =>
      rcases hLimit with ⟨fix, hFixMem, rfl, route, hRouteMem, rfl, hPointMem⟩
      cases hRoute : routePointsOfWaypoints route.waypoints with
      | nil =>
          have hPointMem' := hPointMem
          rw [hRoute] at hPointMem'
          cases hPointMem'
      | cons head tail =>
          have hPointMem' := hPointMem
          rw [hRoute] at hPointMem'
          rcases truncateRouteAtPoint_eq_some_of_mem hPointMem' with ⟨truncated, hTruncated⟩
          refine ⟨truncated, ?_⟩
          simp [compileProcedureRoute,
            findCompileVfrRoute_eq_some_of_mem hWf hRouteMem,
            findCompileFix_eq_some_of_mem hWf hFixMem,
            ScopedVfrRouteSource.toCompileView,
            ScopedFixSource.toCompileView,
            hRoute,
            hTruncated,
            applyRouteLimit]
  | direct fixId =>
      rcases hLimit with ⟨fix, hMem, rfl, rfl⟩
      have hPointMem : fix.point ∈ ([fix.point] : List AirNodeId) := by
        simp
      rcases truncateRouteAtPoint_eq_some_of_mem hPointMem with ⟨truncated, hTruncated⟩
      refine ⟨truncated, ?_⟩
      simp [compileProcedureRoute,
        findCompileFix_eq_some_of_mem hWf hMem,
        ScopedFixSource.toCompileView,
        hTruncated,
        applyRouteLimit]

theorem compileClearanceCommand_clearedTo_ok_of_known_no_limit
    {world : RouteBearingScopedAviationWorld}
    {target : EntityId}
    {destination : AerodromeId}
    {via : ProcedureRef}
    {altitude : Int}
    (hWf : RouteBearingExtractionWellFormed world)
    (hKnown : ProcedureRefKnown world via) :
    ∃ command,
      compileClearanceCommand
        (extractRouteBearingCompileView world)
        (.clearedTo target destination via none altitude) = .ok command := by
  rcases compileProcedureRoute_ok_of_known_no_limit hWf hKnown with ⟨route, hRoute⟩
  refine ⟨.clearedTo target destination route altitude, ?_⟩
  simp [compileClearanceCommand, hRoute]

theorem compileClearanceCommand_clearedTo_ok_of_limit_supported
    {world : RouteBearingScopedAviationWorld}
    {target : EntityId}
    {destination : AerodromeId}
    {via : ProcedureRef}
    {limitId : FixId}
    {altitude : Int}
    (hWf : RouteBearingExtractionWellFormed world)
    (hLimit : ProcedureRefLimitSupported world via limitId) :
    ∃ command,
      compileClearanceCommand
        (extractRouteBearingCompileView world)
        (.clearedTo target destination via (some limitId) altitude) = .ok command := by
  rcases compileProcedureRoute_ok_of_limit_supported hWf hLimit with ⟨route, hRoute⟩
  refine ⟨.clearedTo target destination route altitude, ?_⟩
  simp [compileClearanceCommand, hRoute]

theorem compileRouteBearingInstruction_ok_of_compileReady
    {world : RouteBearingScopedAviationWorld}
    {instruction : ClearanceInstruction}
    (hWf : RouteBearingExtractionWellFormed world)
    (hReady : RouteBearingInstructionCompileReady world instruction) :
    ∃ command,
      compileClearanceCommand
        (extractRouteBearingCompileView world)
        instruction = .ok command := by
  cases instruction with
  | joinCircuit target circuitId joinType =>
      exact compileClearanceCommand_joinCircuit_ok_of_known hWf hReady
  | clearedApproach target approachId =>
      exact compileClearanceCommand_clearedApproach_ok_of_known hWf hReady
  | holdAt target holdId =>
      exact compileClearanceCommand_holdAt_ok_of_known hWf hReady
  | clearedTo target destination via limit altitude =>
      cases limit with
      | none =>
          have hKnown : ProcedureRefKnown world via := by
            simpa [RouteBearingInstructionCompileReady] using hReady
          exact compileClearanceCommand_clearedTo_ok_of_known_no_limit hWf hKnown
      | some limitId =>
          have hLimit : ProcedureRefLimitSupported world via limitId := by
            simpa [RouteBearingInstructionCompileReady] using hReady
          exact compileClearanceCommand_clearedTo_ok_of_limit_supported hWf hLimit
  | _ =>
      cases hReady
