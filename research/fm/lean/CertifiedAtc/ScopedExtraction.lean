import CertifiedAtc.ClearanceEnvelope

namespace CertifiedAtc

/--
Scoped proof-side source world for the `Safety-complete (N₀)` extraction
boundary.

This is intentionally narrower than the full greenfield `AviationWorld`. It
keeps only the source facts the scoped proof package actually needs:

- runway and taxiway entities for the surface/runway slice
- role/frequency and authority/staffing data for issuer checks
- the already-local certifier views for runway, surface, and air

The point of this module is not to rebuild the whole runtime world in Lean. It
is to turn the extraction contract into theorem-bearing boundary facts for the
scoped safety package.
-/

structure ScopedRunwaySource where
  id : RunwayId
  path : List PointId
  pathSegments : List SurfaceSegmentId
  threshold : PointId
  departureEnd : PointId
  exits : List CompileRunwayExitView := []
  deriving DecidableEq, Repr

def ScopedRunwaySource.toCompileView
    (runway : ScopedRunwaySource) : CompileRunwayView :=
  { id := runway.id
    path := runway.path
    pathSegments := runway.pathSegments
    threshold := runway.threshold
    departureEnd := runway.departureEnd
    exits := runway.exits }

structure ScopedTaxiwaySource where
  id : TaxiwayId
  path : List PointId
  directedSegments : List SurfaceSegmentId
  holdingPoints : List CompileHoldingPointView := []
  bidirectional : Bool := true
  deriving DecidableEq, Repr

def ScopedTaxiwaySource.toCompileView
    (taxiway : ScopedTaxiwaySource) : CompileTaxiwayView :=
  { id := taxiway.id
    path := taxiway.path
    directedSegments := taxiway.directedSegments
    holdingPoints := taxiway.holdingPoints
    bidirectional := taxiway.bidirectional }

structure ScopedRoleFrequencySource where
  role : RoleName
  frequency : Frequency
  deriving DecidableEq, Repr

def ScopedRoleFrequencySource.toCompileView
    (role : ScopedRoleFrequencySource) : CompileRoleFrequencyView :=
  { role := role.role
    frequency := role.frequency }

structure ScopedRoleAuthoritySource where
  role : RoleName
  grants : List CompileAuthorityGrantView := []
  deriving DecidableEq, Repr

def ScopedRoleAuthoritySource.toCompileView
    (authority : ScopedRoleAuthoritySource) : CompileRoleAuthorityView :=
  { role := authority.role
    grants := authority.grants }

structure ScopedControllerRoleSource where
  controller : AgentId
  roles : List RoleName := []
  deriving DecidableEq, Repr

def ScopedControllerRoleSource.toCompileView
    (assignment : ScopedControllerRoleSource) :
    CompileControllerRoleAssignmentView :=
  { controller := assignment.controller
    roles := assignment.roles }

structure ScopedAviationWorld where
  runways : List ScopedRunwaySource := []
  taxiways : List ScopedTaxiwaySource := []
  roles : List ScopedRoleFrequencySource := []
  handoffs : List CompileHandoffView := []
  roleAuthorities : List ScopedRoleAuthoritySource := []
  controllerRoles : List ScopedControllerRoleSource := []
  runwayKernel : RunwayKernelEnv
  surfaceGraph : SurfaceGraph
  airGraph : AirGraph
  deriving Repr

structure ScopedCertifierViews where
  runway : RunwayKernelEnv
  surface : SurfaceGraph
  air : AirGraph
  deriving DecidableEq, Repr

def extractCompileView
    (world : ScopedAviationWorld) : ClearanceCompileView :=
  { runways := world.runways.map ScopedRunwaySource.toCompileView
    taxiways := world.taxiways.map ScopedTaxiwaySource.toCompileView
    roles := world.roles.map ScopedRoleFrequencySource.toCompileView
    handoffs := world.handoffs
    roleAuthorities := world.roleAuthorities.map ScopedRoleAuthoritySource.toCompileView
    controllerRoles := world.controllerRoles.map ScopedControllerRoleSource.toCompileView }

def extractCertifierViews
    (world : ScopedAviationWorld) : ScopedCertifierViews :=
  { runway := world.runwayKernel
    surface := world.surfaceGraph
    air := world.airGraph }

def extractOrchestrationEnv
    (world : ScopedAviationWorld) : OrchestrationEnv :=
  { runwayEnv := world.runwayKernel
    surfaceGraph := world.surfaceGraph
    airGraph := world.airGraph }

def findByString {α : Type} (key : α → String) : List α → String → Option α
  | [], _ => none
  | head :: tail, target =>
      if key head = target then
        some head
      else
        findByString key tail target

theorem findByString_eq_some_of_mem_nodup
    {α : Type}
    (key : α → String) :
    ∀ {items : List α} {value : α} {target : String},
      value ∈ items →
      key value = target →
      (items.map key).Nodup →
      findByString key items target = some value := by
  intro items value target hMem hKey hNodup
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem
      simp at hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [findByString, hKey]
      · have hHeadNe : key head ≠ target := by
          intro hEq
          exact
            (hHeadNotIn value hTailMem)
              (by
                calc
                  key value = target := hKey
                  _ = key head := hEq.symm)
        have hTailFind := ih hTailMem hTailNodup
        simpa [findByString, hHeadNe] using hTailFind

theorem list_any_eq_true_of_mem
    {α : Type}
    (p : α → Bool) :
    ∀ {items : List α} {value : α},
      value ∈ items →
      p value = true →
      items.any p = true := by
  intro items
  induction items with
  | nil =>
      intro value hMem
      cases hMem
  | cons head tail ih =>
      intro value hMem hTrue
      simp at hMem
      rcases hMem with rfl | hTailMem
      · simp [hTrue]
      · simp [ih hTailMem hTrue]

def findCompileRunway
    (view : ClearanceCompileView) (runwayId : RunwayId) :
    Option CompileRunwayView :=
  findByString CompileRunwayView.id view.runways runwayId

def findCompileRoleFrequency
    (view : ClearanceCompileView) (role : RoleName) :
    Option CompileRoleFrequencyView :=
  findByString CompileRoleFrequencyView.role view.roles role

structure RunwaySourceOperationalFacts
    (world : ScopedAviationWorld)
    (runway : ScopedRunwaySource) : Prop where
  knownToRunwayKernel : runway.id ∈ world.runwayKernel.runways
  pathSegmentsKnown : AllSegmentsKnown world.surfaceGraph runway.pathSegments
  thresholdOnPath : runway.threshold ∈ runway.path
  departureEndOnPath : runway.departureEnd ∈ runway.path
  exitsSupported :
    ∀ exit ∈ runway.exits,
      exit.point ∈ runway.path ∧
        ∃ taxiway ∈ world.taxiways, taxiway.id = exit.taxiway

structure TaxiwaySourceOperationalFacts
    (world : ScopedAviationWorld)
    (taxiway : ScopedTaxiwaySource) : Prop where
  directedSegmentsKnown : AllSegmentsKnown world.surfaceGraph taxiway.directedSegments
  holdingPointsSupported :
    ∀ hold ∈ taxiway.holdingPoints,
      ∃ entrySegment,
        hold.entrySegment = some entrySegment ∧
          hold.runway ∈ world.runwayKernel.runways ∧
            ∃ surfaceHold ∈ world.surfaceGraph.holdPoints,
              surfaceHold.segment = entrySegment ∧
                surfaceHold.runway = hold.runway

structure ScopedExtractionWellFormed
    (world : ScopedAviationWorld) : Prop where
  runwayIds :
    (world.runways.map (fun runway => runway.id)).Nodup
  taxiwayIds :
    (world.taxiways.map (fun taxiway => taxiway.id)).Nodup
  roleNames :
    (world.roles.map (fun role => role.role)).Nodup
  roleAuthorityNames :
    (world.roleAuthorities.map (fun authority => authority.role)).Nodup
  controllerIds :
    (world.controllerRoles.map (fun assignment => assignment.controller)).Nodup
  runwayOperational :
    ∀ runway ∈ world.runways, RunwaySourceOperationalFacts world runway
  taxiwayOperational :
    ∀ taxiway ∈ world.taxiways, TaxiwaySourceOperationalFacts world taxiway

def WorldRoleHasGrant
    (world : ScopedAviationWorld)
    (role : RoleName)
    (grant : CompileAuthorityGrantView) : Prop :=
  ∃ authority ∈ world.roleAuthorities,
    authority.role = role ∧
      grant ∈ authority.grants

def WorldControllerHasRole
    (world : ScopedAviationWorld)
    (controller : AgentId)
    (role : RoleName) : Prop :=
  ∃ assignment ∈ world.controllerRoles,
    assignment.controller = controller ∧
      role ∈ assignment.roles

def WorldControllerHasGrant
    (world : ScopedAviationWorld)
    (controller : AgentId)
    (grant : CompileAuthorityGrantView) : Prop :=
  ∃ role,
    WorldControllerHasRole world controller role ∧
      WorldRoleHasGrant world role grant

def ScopedInstructionReferencesKnown
    (world : ScopedAviationWorld) : ClearanceInstruction → Prop
  | .holdShortOf _ runway =>
      ∃ source ∈ world.runways, source.id = runway
  | .taxiVia _ taxiways _ =>
      ∀ taxiway ∈ taxiways, ∃ source ∈ world.taxiways, source.id = taxiway
  | .crossRunway _ runway =>
      ∃ source ∈ world.runways, source.id = runway
  | .lineUpAndWait _ runway =>
      ∃ source ∈ world.runways, source.id = runway
  | .clearedForTakeoff _ runway =>
      ∃ source ∈ world.runways, source.id = runway
  | .clearedToLand _ runway =>
      ∃ source ∈ world.runways, source.id = runway
  | .clearedTouchAndGo _ runway =>
      ∃ source ∈ world.runways, source.id = runway
  | .contactFrequency _ role frequency =>
      ∃ source ∈ world.roles,
        source.role = role ∧
          source.frequency = frequency
  | .monitorFrequency _ role frequency =>
      ∃ source ∈ world.roles,
        source.role = role ∧
          source.frequency = frequency
  | .reportDownwind _ => True
  | .reportFinal _ => True
  | .proceed _ => True
  | .goAround _ => True
  | .reduceSpeedTo _ _ => True
  | .climbTo _ _ => True
  | .descendTo _ _ => True
  | .squawkCode _ _ => True
  | _ => False

def ScopedInstructionReferencesExtractable
    (view : ClearanceCompileView) : ClearanceInstruction → Prop
  | .holdShortOf _ runway =>
      ∃ runwayView, findCompileRunway view runway = some runwayView
  | .taxiVia _ taxiways _ =>
      ∀ taxiway ∈ taxiways,
        ∃ taxiwayView, findCompileTaxiway view taxiway = some taxiwayView
  | .crossRunway _ runway =>
      ∃ runwayView, findCompileRunway view runway = some runwayView
  | .lineUpAndWait _ runway =>
      ∃ runwayView, findCompileRunway view runway = some runwayView
  | .clearedForTakeoff _ runway =>
      ∃ runwayView, findCompileRunway view runway = some runwayView
  | .clearedToLand _ runway =>
      ∃ runwayView, findCompileRunway view runway = some runwayView
  | .clearedTouchAndGo _ runway =>
      ∃ runwayView, findCompileRunway view runway = some runwayView
  | .contactFrequency _ role frequency =>
      findCompileRoleFrequency view role =
        some { role := role, frequency := frequency }
  | .monitorFrequency _ role frequency =>
      findCompileRoleFrequency view role =
        some { role := role, frequency := frequency }
  | .reportDownwind _ => True
  | .reportFinal _ => True
  | .proceed _ => True
  | .goAround _ => True
  | .reduceSpeedTo _ _ => True
  | .climbTo _ _ => True
  | .descendTo _ _ => True
  | .squawkCode _ _ => True
  | _ => False

theorem extractCertifierViews_runway_exact
    (world : ScopedAviationWorld) :
    (extractCertifierViews world).runway = world.runwayKernel := rfl

theorem extractCertifierViews_surface_exact
    (world : ScopedAviationWorld) :
    (extractCertifierViews world).surface = world.surfaceGraph := rfl

theorem extractCertifierViews_air_exact
    (world : ScopedAviationWorld) :
    (extractCertifierViews world).air = world.airGraph := rfl

theorem extractOrchestrationEnv_runway_exact
    (world : ScopedAviationWorld) :
    (extractOrchestrationEnv world).runwayEnv = world.runwayKernel := rfl

theorem extractOrchestrationEnv_surface_exact
    (world : ScopedAviationWorld) :
    (extractOrchestrationEnv world).surfaceGraph = world.surfaceGraph := rfl

theorem extractOrchestrationEnv_air_exact
    (world : ScopedAviationWorld) :
    (extractOrchestrationEnv world).airGraph = world.airGraph := rfl

theorem findCompileRunway_eq_some_of_mem
    {world : ScopedAviationWorld}
    {runway : ScopedRunwaySource}
    (hWf : ScopedExtractionWellFormed world)
    (hMem : runway ∈ world.runways) :
    findCompileRunway (extractCompileView world) runway.id =
      some runway.toCompileView := by
  unfold findCompileRunway extractCompileView
  apply findByString_eq_some_of_mem_nodup (key := CompileRunwayView.id)
  · exact List.mem_map.mpr ⟨runway, hMem, rfl⟩
  · rfl
  · simpa [ScopedRunwaySource.toCompileView] using hWf.runwayIds

theorem findCompileTaxiway_go_eq_some_of_mem
    {items : List CompileTaxiwayView}
    {taxiway : CompileTaxiwayView}
    (hMem : taxiway ∈ items)
    (hNodup : (items.map CompileTaxiwayView.id).Nodup) :
    findCompileTaxiway.go taxiway.id items = some taxiway := by
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [findCompileTaxiway.go]
      · have hHeadNe : head.id ≠ taxiway.id := by
          intro hEq
          exact (hHeadNotIn taxiway hTailMem) (by simp [hEq])
        have hTailFind := ih hTailMem hTailNodup
        simp [findCompileTaxiway.go, hHeadNe, hTailFind]

theorem findCompileTaxiway_eq_some_of_mem
    {world : ScopedAviationWorld}
    {taxiway : ScopedTaxiwaySource}
    (hWf : ScopedExtractionWellFormed world)
    (hMem : taxiway ∈ world.taxiways) :
    findCompileTaxiway (extractCompileView world) taxiway.id =
      some taxiway.toCompileView := by
  have hMemCompile :
      taxiway.toCompileView ∈
        world.taxiways.map ScopedTaxiwaySource.toCompileView := by
    exact List.mem_map.mpr ⟨taxiway, hMem, rfl⟩
  have hNodupCompile :
      (List.map CompileTaxiwayView.id
        (world.taxiways.map ScopedTaxiwaySource.toCompileView)).Nodup := by
    simpa [ScopedTaxiwaySource.toCompileView] using hWf.taxiwayIds
  unfold extractCompileView
  unfold findCompileTaxiway
  simpa [ScopedTaxiwaySource.toCompileView] using
    (findCompileTaxiway_go_eq_some_of_mem hMemCompile hNodupCompile)

theorem findCompileRoleFrequency_eq_some_of_mem
    {world : ScopedAviationWorld}
    {role : ScopedRoleFrequencySource}
    (hWf : ScopedExtractionWellFormed world)
    (hMem : role ∈ world.roles) :
    findCompileRoleFrequency (extractCompileView world) role.role =
      some role.toCompileView := by
  unfold findCompileRoleFrequency extractCompileView
  apply findByString_eq_some_of_mem_nodup (key := CompileRoleFrequencyView.role)
  · exact List.mem_map.mpr ⟨role, hMem, rfl⟩
  · rfl
  · simpa [ScopedRoleFrequencySource.toCompileView] using hWf.roleNames

theorem findCompileRoleAuthority_go_eq_some_of_mem
    {items : List CompileRoleAuthorityView}
    {authority : CompileRoleAuthorityView}
    (hMem : authority ∈ items)
    (hNodup : (items.map CompileRoleAuthorityView.role).Nodup) :
    findCompileRoleAuthority.go authority.role items = some authority := by
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [findCompileRoleAuthority.go]
      · have hHeadNe : head.role ≠ authority.role := by
          intro hEq
          exact (hHeadNotIn authority hTailMem) (by simp [hEq])
        have hTailFind := ih hTailMem hTailNodup
        simp [findCompileRoleAuthority.go, hHeadNe, hTailFind]

theorem findCompileRoleAuthority_eq_some_of_mem
    {world : ScopedAviationWorld}
    {authority : ScopedRoleAuthoritySource}
    (hWf : ScopedExtractionWellFormed world)
    (hMem : authority ∈ world.roleAuthorities) :
    findCompileRoleAuthority (extractCompileView world) authority.role =
      some authority.toCompileView := by
  have hMemCompile :
      authority.toCompileView ∈
        world.roleAuthorities.map ScopedRoleAuthoritySource.toCompileView := by
    exact List.mem_map.mpr ⟨authority, hMem, rfl⟩
  have hNodupCompile :
      (List.map CompileRoleAuthorityView.role
        (world.roleAuthorities.map ScopedRoleAuthoritySource.toCompileView)).Nodup := by
    simpa [ScopedRoleAuthoritySource.toCompileView] using hWf.roleAuthorityNames
  unfold extractCompileView
  unfold findCompileRoleAuthority
  simpa [ScopedRoleAuthoritySource.toCompileView] using
    (findCompileRoleAuthority_go_eq_some_of_mem hMemCompile hNodupCompile)

theorem findCompileControllerRoles_go_eq_some_of_mem
    {items : List CompileControllerRoleAssignmentView}
    {assignment : CompileControllerRoleAssignmentView}
    (hMem : assignment ∈ items)
    (hNodup : (items.map CompileControllerRoleAssignmentView.controller).Nodup) :
    findCompileControllerRoles.go assignment.controller items = some assignment := by
  induction items with
  | nil =>
      cases hMem
  | cons head tail ih =>
      simp at hMem hNodup
      rcases hNodup with ⟨hHeadNotIn, hTailNodup⟩
      rcases hMem with rfl | hTailMem
      · simp [findCompileControllerRoles.go]
      · have hHeadNe : head.controller ≠ assignment.controller := by
          intro hEq
          exact (hHeadNotIn assignment hTailMem) (by simp [hEq])
        have hTailFind := ih hTailMem hTailNodup
        simp [findCompileControllerRoles.go, hHeadNe, hTailFind]

theorem findCompileControllerRoles_eq_some_of_mem
    {world : ScopedAviationWorld}
    {assignment : ScopedControllerRoleSource}
    (hWf : ScopedExtractionWellFormed world)
    (hMem : assignment ∈ world.controllerRoles) :
    findCompileControllerRoles (extractCompileView world) assignment.controller =
      some assignment.toCompileView := by
  have hMemCompile :
      assignment.toCompileView ∈
        world.controllerRoles.map ScopedControllerRoleSource.toCompileView := by
    exact List.mem_map.mpr ⟨assignment, hMem, rfl⟩
  have hNodupCompile :
      (List.map CompileControllerRoleAssignmentView.controller
        (world.controllerRoles.map ScopedControllerRoleSource.toCompileView)).Nodup := by
    simpa [ScopedControllerRoleSource.toCompileView] using hWf.controllerIds
  unfold extractCompileView
  unfold findCompileControllerRoles
  simpa [ScopedControllerRoleSource.toCompileView] using
    (findCompileControllerRoles_go_eq_some_of_mem hMemCompile hNodupCompile)

theorem extractCompileView_runway_origin
    {world : ScopedAviationWorld}
    {runwayView : CompileRunwayView}
    (hMem : runwayView ∈ (extractCompileView world).runways) :
    ∃ source ∈ world.runways, source.toCompileView = runwayView := by
  unfold extractCompileView at hMem
  rcases List.mem_map.mp hMem with ⟨source, hSource, hEq⟩
  exact ⟨source, hSource, hEq⟩

theorem extractCompileView_taxiway_origin
    {world : ScopedAviationWorld}
    {taxiwayView : CompileTaxiwayView}
    (hMem : taxiwayView ∈ (extractCompileView world).taxiways) :
    ∃ source ∈ world.taxiways, source.toCompileView = taxiwayView := by
  unfold extractCompileView at hMem
  rcases List.mem_map.mp hMem with ⟨source, hSource, hEq⟩
  exact ⟨source, hSource, hEq⟩

theorem extractCompileView_role_origin
    {world : ScopedAviationWorld}
    {roleView : CompileRoleFrequencyView}
    (hMem : roleView ∈ (extractCompileView world).roles) :
    ∃ source ∈ world.roles, source.toCompileView = roleView := by
  unfold extractCompileView at hMem
  rcases List.mem_map.mp hMem with ⟨source, hSource, hEq⟩
  exact ⟨source, hSource, hEq⟩

theorem knownRunwayReference_preserved
    {world : ScopedAviationWorld}
    {runwayId : RunwayId}
    (hWf : ScopedExtractionWellFormed world)
    (hKnown : ∃ source ∈ world.runways, source.id = runwayId) :
    (∃ runwayView,
      findCompileRunway (extractCompileView world) runwayId = some runwayView) ∧
      runwayId ∈ (extractCertifierViews world).runway.runways := by
  rcases hKnown with ⟨source, hMem, rfl⟩
  constructor
  · exact ⟨source.toCompileView, findCompileRunway_eq_some_of_mem hWf hMem⟩
  · exact (hWf.runwayOperational source hMem).knownToRunwayKernel

theorem knownTaxiwayReference_preserved
    {world : ScopedAviationWorld}
    {taxiwayId : TaxiwayId}
    (hWf : ScopedExtractionWellFormed world)
    (hKnown : ∃ source ∈ world.taxiways, source.id = taxiwayId) :
    ∃ taxiwayView,
      findCompileTaxiway (extractCompileView world) taxiwayId = some taxiwayView ∧
        AllSegmentsKnown (extractCertifierViews world).surface
          taxiwayView.directedSegments := by
  rcases hKnown with ⟨source, hMem, rfl⟩
  refine ⟨source.toCompileView, findCompileTaxiway_eq_some_of_mem hWf hMem, ?_⟩
  simpa [extractCertifierViews] using
    (hWf.taxiwayOperational source hMem).directedSegmentsKnown

theorem extractedTaxiwayHoldingPoint_supported
    {world : ScopedAviationWorld}
    {taxiway : ScopedTaxiwaySource}
    {hold : CompileHoldingPointView}
    (hWf : ScopedExtractionWellFormed world)
    (hTaxiway : taxiway ∈ world.taxiways)
    (hHold : hold ∈ taxiway.holdingPoints) :
    ∃ entrySegment,
      hold.entrySegment = some entrySegment ∧
        hold.runway ∈ (extractCertifierViews world).runway.runways ∧
          ∃ surfaceHold ∈ (extractCertifierViews world).surface.holdPoints,
            surfaceHold.segment = entrySegment ∧
              surfaceHold.runway = hold.runway := by
  rcases (hWf.taxiwayOperational taxiway hTaxiway).holdingPointsSupported hold hHold with
    ⟨entrySegment, hEntry, hRunway, surfaceHold, hSurfaceHold, hSegment, hRunwayEq⟩
  refine ⟨entrySegment, hEntry, ?_, surfaceHold, ?_, hSegment, hRunwayEq⟩
  · simpa [extractCertifierViews] using hRunway
  · simpa [extractCertifierViews] using hSurfaceHold

theorem knownRoleFrequency_preserved
    {world : ScopedAviationWorld}
    {role : RoleName}
    {frequency : Frequency}
    (hWf : ScopedExtractionWellFormed world)
    (hKnown :
      ∃ source ∈ world.roles,
        source.role = role ∧
          source.frequency = frequency) :
    findCompileRoleFrequency (extractCompileView world) role =
      some { role := role, frequency := frequency } := by
  rcases hKnown with ⟨source, hMem, hRole, hFrequency⟩
  cases source
  simp at hRole hFrequency
  subst hRole
  subst hFrequency
  simpa using findCompileRoleFrequency_eq_some_of_mem hWf hMem

theorem roleAuthorityHasGrant_eq_true_of_mem
    {authority : CompileRoleAuthorityView}
    {grant : CompileAuthorityGrantView}
    (hMem : grant ∈ authority.grants) :
    roleAuthorityHasGrant authority grant = true := by
  unfold roleAuthorityHasGrant
  exact list_any_eq_true_of_mem (fun existing => existing = grant) hMem (by simp)

theorem controllerHasAuthorityGrant_of_worldControllerHasGrant
    {world : ScopedAviationWorld}
    {controller : AgentId}
    {grant : CompileAuthorityGrantView}
    (hWf : ScopedExtractionWellFormed world)
    (hGrant : WorldControllerHasGrant world controller grant) :
    controllerHasAuthorityGrant (extractCompileView world) controller grant = true := by
  rcases hGrant with ⟨role, hControllerRole, hRoleGrant⟩
  rcases hControllerRole with ⟨assignment, hAssignMem, hControllerEq, hRoleMem⟩
  rcases hRoleGrant with ⟨authority, hAuthorityMem, hRoleEq, hGrantMem⟩
  have hAssignmentFind :
      findCompileControllerRoles (extractCompileView world) controller =
        some assignment.toCompileView := by
    subst hControllerEq
    simpa using findCompileControllerRoles_eq_some_of_mem hWf hAssignMem
  have hAuthorityFind :
      findCompileRoleAuthority (extractCompileView world) role =
        some authority.toCompileView := by
    subst hRoleEq
    simpa using findCompileRoleAuthority_eq_some_of_mem hWf hAuthorityMem
  have hGrantTrue :
      roleAuthorityHasGrant authority.toCompileView grant = true := by
    exact roleAuthorityHasGrant_eq_true_of_mem hGrantMem
  unfold controllerHasAuthorityGrant
  rw [hAssignmentFind]
  apply list_any_eq_true_of_mem
    (fun existingRole =>
      match findCompileRoleAuthority (extractCompileView world) existingRole with
      | none => false
      | some foundAuthority => roleAuthorityHasGrant foundAuthority grant)
  · simpa [ScopedControllerRoleSource.toCompileView] using hRoleMem
  · simp [hAuthorityFind, hGrantTrue]

theorem instructionIssuerAuthorized_of_worldControllerHasGrant
    {world : ScopedAviationWorld}
    {controller : AgentId}
    {instruction : ClearanceInstruction}
    {grant : CompileAuthorityGrantView}
    (hWf : ScopedExtractionWellFormed world)
    (hMapped : instructionRequiredAuthorityGrant? instruction = some grant)
    (hGrant : WorldControllerHasGrant world controller grant) :
    instructionIssuerAuthorized (extractCompileView world) controller instruction = true := by
  simp [instructionIssuerAuthorized, hMapped,
    controllerHasAuthorityGrant_of_worldControllerHasGrant hWf hGrant]

theorem scopedInstructionLifecycleStable
    {world : ScopedAviationWorld}
    {instruction : ClearanceInstruction}
    (hWf : ScopedExtractionWellFormed world)
    (hKnown : ScopedInstructionReferencesKnown world instruction) :
    ScopedInstructionReferencesExtractable (extractCompileView world) instruction := by
  cases instruction <;> simp [ScopedInstructionReferencesKnown,
    ScopedInstructionReferencesExtractable] at hKnown ⊢
  · rcases hKnown with ⟨source, hMem, rfl⟩
    exact ⟨source.toCompileView, findCompileRunway_eq_some_of_mem hWf hMem⟩
  · intro taxiwayId hTaxiwayMem
    rcases hKnown taxiwayId hTaxiwayMem with ⟨source, hMem, rfl⟩
    exact ⟨source.toCompileView, findCompileTaxiway_eq_some_of_mem hWf hMem⟩
  · rcases hKnown with ⟨source, hMem, rfl⟩
    exact ⟨source.toCompileView, findCompileRunway_eq_some_of_mem hWf hMem⟩
  · rcases hKnown with ⟨source, hMem, rfl⟩
    exact ⟨source.toCompileView, findCompileRunway_eq_some_of_mem hWf hMem⟩
  · rcases hKnown with ⟨source, hMem, rfl⟩
    exact ⟨source.toCompileView, findCompileRunway_eq_some_of_mem hWf hMem⟩
  · rcases hKnown with ⟨source, hMem, rfl⟩
    exact ⟨source.toCompileView, findCompileRunway_eq_some_of_mem hWf hMem⟩
  · rcases hKnown with ⟨source, hMem, rfl⟩
    exact ⟨source.toCompileView, findCompileRunway_eq_some_of_mem hWf hMem⟩
  · exact knownRoleFrequency_preserved hWf hKnown
  · exact knownRoleFrequency_preserved hWf hKnown

end CertifiedAtc
