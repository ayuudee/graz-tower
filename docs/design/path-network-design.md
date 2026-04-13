# Path Network Design: Entities as the World

## The core insight

The physical world is just geometry. Points and segments with positions, lengths, and widths. They have no operational meaning. All meaning comes from **overlay entities** — runways, taxiways, circuits, airways — that reference the geometry and say "this is what this is."

The graph is the territory. The entities are the map.

## Two layers

### Physical geometry

The graph is made of **points** and **segments**.

A **point** (`PointId`) is a position in space (x, y, and optionally altitude). Points are identified by `PointId`. A point is a stand, a fix, a threshold, a junction — anywhere something can be.

A **segment** (`SegmentId`) connects two points. Every segment has:

- **Length** in meters (distance between its endpoints; may follow a curve)
- **Width** in meters (taxiway: ~15m, runway: ~45m, holding racetrack: orbit diameter)
- **Shape** (straight line or arc — an arc is defined by a radius; covers taxiway bends, holding pattern turns, and circuit geometry)
- **Surface type** (ground, runway surface, sky)

Segments are implicit — they exist where two points are declared adjacent by an entity's path. A `SegmentId` is the pair `(PointId, PointId)`. Points and segments have no names, no operational kind, no airspace class. They are inert geometry.

### Overlay entities

Entities reference points and the segments between them, giving them meaning. Every point and segment in the world must belong to at least one entity — runways, taxiways, circuits, aprons, etc. There is no geometry without operational meaning.

A **path** (`Path`) is an ordered list of at least two points. Consecutive pairs form segments. Paths are the standard way entities claim geometry.

```
Path(
  points: List<PointId>                    // ordered, >= 2, consecutive pairs form segments
)
```

An entity's `path: Path` defines both the points and segments it claims — each consecutive pair of points in the list is a segment owned by that entity.

Entities are the source of truth for operational meaning. The physical geometry is the union of all points and segments referenced by entities. A derived index makes the world traversable at runtime.

## Entity types

### Aerodrome-scoped

**Runway** — one directional use of a physical strip.

Runway 16C and runway 34C are **separate entities** that share most of their underlying path segments. Each has its own threshold, exits, declared distances, and procedures. Active runway selection chooses which entity's procedures are in use.

```
Runway(
  id: RunwayId,                           // "16C"
  path: Path,                              // ordered threshold → far end
  threshold: PointId,                      // landing threshold (= path.first)
  exits: List<RunwayExit>,               // turn-offs available when using this direction
  declaredDistances: DeclaredDistances?,  // TORA, TODA, ASDA, LDA per direction
)

RunwayExit(
  point: PointId,                          // point on the runway path
  taxiway: TaxiwayId,                     // which taxiway it connects to
)

DeclaredDistances(
  tora: Meters,   // takeoff run available
  toda: Meters,   // takeoff distance available (includes clearway)
  asda: Meters,   // accelerate-stop distance available (includes stopway)
  lda: Meters,    // landing distance available (from threshold, excludes displaced portion)
  clearway: Meters?,  // distance beyond runway end available for initial climb-out
)
```

Two reciprocal runways (16C, 34C) are linked by sharing path segments. The physical strip is the union of their segments. `threshold` is `path.first`; `path.last` is the far end of the runway in this direction. For a non-displaced threshold, `threshold` is the same point as the reciprocal runway's `path.last`.

**Intersection departures.** The runway entity does not fix a departure point. A full-length departure starts at `path.last` (the reciprocal threshold). An intersection departure starts at any point where a taxiway meets the runway path. The departure point is a property of the clearance, not the runway — `ClearedForTakeoff` is issued after the aircraft has taxied to a position on the runway. Reduced declared distances for intersection departures are derived from the aircraft's position along the runway path.

**Not modelled (deferred):**
- Displaced threshold asymmetry — the area between the runway start and a displaced threshold is usable for takeoff but not landing. Can be derived from comparing `threshold` to `path.first` if they differ, but the rules around this are not explicitly enforced.
- Stopway geometry — ASDA references stopway length but no physical segments exist beyond the runway end. If the simulation needs rejected takeoff overrun, stopway segments would need to be added to the path.

**Taxiway** — a named, contiguous path with holding points.

```
Taxiway(
  name: String,                           // "A" — for phraseology
  path: Path,                              // contiguous segments
  holdingPoints: List<HoldingPoint>,      // all holding positions on this taxiway
  bidirectional: Boolean,                 // true for most taxiways; false for one-way
)

HoldingPoint(
  point: PointId,                          // position on the taxiway path
  name: String?,                          // "A1" — for phraseology; optional at small aerodromes
  type: HoldingPointType,
  runway: RunwayId?,                      // which runway this hold protects; null for intermediate holds
)

enum HoldingPointType {
  CAT_A,                                  // standard runway holding position (Pattern A markings)
  CAT_B,                                  // Cat II/III position, further from runway (Pattern B markings)
  INTERMEDIATE,                           // traffic management hold, not runway-associated
}
```

A runway holding point is a point on a taxiway with respect to a specific runway. "Hold short of runway 16C" happens at a specific holding point (with `runway = RunwayId("16C")`) on a specific taxiway. A taxiway may have multiple holding points for the same runway at different distances (Cat A and Cat B). Intermediate holding points (`runway = null`) are used by controllers for traffic management: "hold at Alpha 3."

**Segment ownership rule**: each path segment belongs to exactly one taxiway. Where two taxiways meet, they share a junction point, but the segments on each side belong to their respective taxiway exclusively. This eliminates ambiguity in phraseology and holding point applicability.

**Stand** — a parking position on an apron.

```
Stand(
  id: StandId,
  name: String,                           // "Gate A1", "Stand 4"
  point: PointId,                          // position (must be on an apron path)
)
```

A stand is a named point. Its connectivity to the taxi network comes from the apron it sits on — the apron's paths connect stand points to taxiway junction points. No explicit `connectedTo` is needed; reachability is geometric and validated by construction-time invariants.

**Apron** — a paved manoeuvring area connecting stands to the taxi network.

```
Apron(
  id: ApronId,
  name: String,                           // "Main Apron", "GA Apron"
  paths: List<Path>,                      // internal manoeuvring paths (stand ↔ taxiway junctions)
  stands: Set<StandId>,                   // stands within this apron
  capacity: Int?,                         // max aircraft, if constrained
)
```

An apron is a real entity owning real segments. Aircraft push back from a stand onto an apron path, manoeuvre along apron paths, and reach a taxiway at a shared junction point. The apron's paths form the internal network between stands and taxiway entry points. Segment ownership follows the same rule as taxiways — apron segments are exclusive to the apron; junction points may be shared with taxiways.

**Circuit procedure** — a closed loop for a directional runway use, with pre-built extensions, joins, and go-around.

```
CircuitProcedure(
  runway: RunwayId,                       // "16C" — directional
  direction: CircuitDirection,            // Left / Right
  legs: List<CircuitLeg>,                 // ordered: upwind, crosswind, downwind, base, final
  altitude: Level,                        // circuit altitude AMSL (QNH-referenced)
  reportingPoints: Map<LegName, PointId>,
  joinProcedures: List<CircuitJoin>,      // how aircraft enter the circuit
  extendedDownwind: ExtendedDownwind?,    // pre-built extension with off-ramps
  orbitPoints: List<OrbitPoint>,          // pre-built orbit geometry at specific circuit positions
  goAroundPath: Path,                     // from runway back to upwind leg (climb straight ahead, rejoin)
) {
  init { require(legs.last().to == legs.first().from) }  // closed loop
}

CircuitLeg(
  name: LegName,                          // Upwind, Crosswind, Downwind, Base, Final
  path: Path,                              // segments for this leg (may include arcs)
)

enum LegName {
  UPWIND,
  CROSSWIND,
  DOWNWIND,
  BASE,
  FINAL,
}

CircuitJoin(
  type: JoinType,                         // OVERHEAD, DOWNWIND, BASE, STRAIGHT_IN, CROSSWIND, etc.
  entryPoint: PointId,                    // where the aircraft enters the circuit
  entryPath: Path?,                       // approach path to the entry point (null if direct to a circuit point)
)

ExtendedDownwind(
  extendedPath: Path,                      // continues past normal downwind end
  offRamps: List<OffRamp>,               // rejoin points back to base leg
)

OffRamp(
  path: Path,                              // from branch point on extended downwind to rejoin on base/final
)                                         // first point is on extendedPath, last point is on base leg

OrbitPoint(
  point: PointId,                          // where on the circuit the orbit begins
  loop: Path,                              // pre-built closed 360° loop at this point
  direction: OrbitDirection,              // matches circuit direction
)
```

"Extend downwind" uses pre-built geometry. The controller instructs the pilot to continue on the extended path; the pilot takes an off-ramp when cleared for base turn. No runtime entity creation needed.

**Circuit joins.** Aircraft enter the circuit via designated join procedures. The overhead join has a dedicated entry path: the aircraft arrives above circuit altitude, crosses the upwind end on the dead side, descends to circuit altitude, and joins crosswind or downwind. Other joins (direct downwind, base, straight-in) are entry points on existing circuit legs — the `entryPath` is the approach path to reach that point, or null if the aircraft is already positioned (e.g. departing traffic joining upwind directly).

**Orbits** are pre-built like extended downwind. An `OrbitPoint` defines a 360° loop at a specific position on the circuit. The controller issues "orbit right" and the pilot follows the pre-built geometry, rejoining the circuit at the same point.

**Go-around.** The `goAroundPath` is the VFR go-around from the runway back to the upwind leg — typically climb straight ahead and rejoin. This is distinct from an instrument missed approach procedure.

The same physical path segments can be "downwind" in one circuit and "base" in another (different runway, different direction). The leg name lives on the procedure, not on the geometry.

**Holding pattern** — a racetrack procedure at a fix, modelled as a closed loop of path segments. Same approach as circuit orbit points: pre-built loitering geometry at a known position.

```
HoldingPattern(
  id: HoldingPatternId,
  fix: FixId,                             // the holding fix
  inboundCourse: Degrees,
  turnDirection: TurnDirection,           // Left / Right
  loop: Path,                              // pre-built racetrack loop (closed)
  legTime: Minutes?,                      // outbound leg duration (1 min below 14000ft, 1.5 above)
  legDistance: DmeDistanceNm?,            // outbound leg distance (alternative to time)
  maxSpeed: Knots?,                       // maximum holding speed (affects racetrack width)
  altitude: Level,                        // base altitude (AMSL)
  stackSeparation: Feet?,                 // vertical separation per level (e.g. 1000ft)
)
```

The racetrack is modelled as a closed `Path` with segment width sufficient for the orbit. Stacking (multiple aircraft holding at different altitudes) uses `stackSeparation` — same geometry, different altitudes.

Entry procedures (direct, parallel, teardrop) are determined at runtime by the pilot based on arrival heading relative to the inbound course. They use the same racetrack geometry with different initial manoeuvres to join it — this is a pilot-layer concern, not path network geometry.

### Route-scoped

**VFR route** — a named corridor between aerodromes.

```
VfrRoute(
  name: String,
  waypoints: List<Waypoint>,              // with per-waypoint altitude constraints
  airspaceClass: AirspaceClass,
)
```

VFR routes use `List<Waypoint>` (not `Path`) so altitude constraints can vary along the route — e.g. "not above 2000ft in the CTR, not below 1500ft in the TMA."

**Airway** — a named IFR corridor.

```
Airway(
  name: String,                           // "W4"
  waypoints: List<Waypoint>,              // ordered fixes with altitude constraints
  altitudeBand: AltitudeBand,
  bidirectional: Boolean,
)
```

**SID** — standard instrument departure, links a runway to the en-route structure.

A SID has a common trunk (waypoints from the runway) and zero or more transitions that branch from the trunk to different en-route fixes.

```
SID(
  name: String,                           // "KEMIK 1A"
  runway: RunwayId,                       // "16C" — which direction
  waypoints: List<Waypoint>,              // common trunk, with altitude/speed constraints
  transitions: Map<String, List<Waypoint>>,  // keyed by transition name ("BALSI", "NERDU")
)                                         // each transition branches from the last trunk waypoint
```

A SID with no transitions ends at its last waypoint, which is typically a fix shared with an airway. A SID with transitions shares a common initial climb, then branches: "KEMIK 1A BALSI transition" follows the trunk then the BALSI waypoint sequence. The connection to airways is implicit through shared fixes — no explicit `connectsTo`.

**STAR** — standard terminal arrival, links the en-route structure to the approach.

A STAR has zero or more entry transitions that merge into a common trunk ending near the approach.

```
STAR(
  name: String,                           // "BALSI 2B"
  waypoints: List<Waypoint>,              // common trunk
  transitions: Map<String, List<Waypoint>>,  // entry transitions, keyed by name
)                                         // each transition merges into the first trunk waypoint
```

The STAR's last waypoint is typically an IAF shared with one or more instrument approaches. The connection to approaches is implicit through shared fixes — no explicit `connectsTo`. The controller assigns an approach separately.

**Instrument approach** — from initial approach fix to runway threshold, with missed approach.

```
InstrumentApproach(
  name: String,                           // "ILS 16C"
  type: ApproachType,                     // ILS, VOR, RNAV, Visual, etc.
  runway: RunwayId,                       // "16C" — directional
  waypoints: List<Waypoint>,              // IAF -> IF -> FAF -> threshold
  minimumAltitude: ApproachMinimum,       // DA/DH for precision, MDA/MDH for non-precision
  missedApproach: MissedApproachProcedure,
)

ApproachMinimum(
  type: MinimumType,                      // DA or MDA
  altitude: Level,                        // decision altitude / minimum descent altitude
  height: Level?,                         // decision height / minimum descent height (QFE-referenced)
)

enum MinimumType {
  DECISION_ALTITUDE,                      // precision approaches (ILS, PAR) — DA/DH
  MINIMUM_DESCENT_ALTITUDE,              // non-precision approaches (VOR, NDB, RNAV) — MDA/MDH
}

MissedApproachProcedure(
  waypoints: List<Waypoint>,              // climb, turn, go to holding fix
  holdAt: HoldingPatternId,
)
```

**Waypoint** — a point within a procedure with optional constraints.

```
Waypoint(
  point: PointId,
  name: String?,                          // published name: "KEMIK", "BALSI"
  altitudeConstraint: AltitudeConstraint?,  // At / AtOrAbove / AtOrBelow / Between
  speedConstraint: SpeedConstraint?,
)
```

### Airspace-scoped

**Airspace volume** — a region with regulatory properties.

```
AirspaceVolume(
  id: AirspaceVolumeId,
  name: String,                           // "LOWG CTR", "LOWG TMA"
  type: AirspaceVolumeType,              // CTR, TMA, ATZ, FIR, UIR, OCA
  airspaceClass: AirspaceClass,           // A, B, C, D, E, F, G
  altitudeBand: AltitudeBand,
  points: Set<PointId>,                   // the points (and their segments) within this volume
  fir: FirId,                             // which FIR this volume belongs to
)

enum AirspaceVolumeType {
  CTR,                                    // control zone — from surface upward, around an aerodrome
  TMA,                                    // terminal manoeuvring area — controlled airspace above a CTR
  ATZ,                                    // aerodrome traffic zone — at uncontrolled/partially controlled aerodromes
  FIR,                                    // flight information region
  UIR,                                    // upper information region
  OCA,                                    // oceanic control area
}

FlightInformationRegion(
  id: FirId,
  name: String,                           // "Wien FIR"
  volumes: Set<AirspaceVolumeId>,         // constituent airspace volumes
)
```

Airspace volumes own points and segments, like every other entity — the world's geometry *is* the airspace. A point can be part of Taxiway("Alpha") AND inside AirspaceVolume("LOWG CTR", Class D). Every point must be inside at least one airspace volume. FIR membership is needed for VFR flight planning (rules vary by FIR).

The volume type determines which rules apply. For example, VFR flights must obtain clearance before entering a CTR (SERA.3215), but not a TMA in Class D. The airspace class determines separation services provided, speed limitations, and communication requirements per SERA.6001.

### Global

**Fix** — a named point in space, shared across procedures.

```
Fix(
  name: String,                           // "KEMIK" — the published name
  point: PointId,
  type: FixType,                          // Waypoint, VOR, NDB, Marker
)
```

Fixes are referenced by SIDs, STARs, airways, approaches. "Direct KEMIK" means fly to the point that fix KEMIK references. Multiple procedures share fixes — the SID ends at KEMIK, the airway starts at KEMIK. Same PointId.

## Roles and jurisdiction

An aerodrome declares **roles**. Each role has defined authorities over entities and operations. Controllers **fill** roles.

```
AerodromeRole(
  name: RoleName,
  authorities: Set<Authority>,            // what this role can do
  frequency: Frequency,                   // radio frequency
)

enum RoleName {
  CLEARANCE_DELIVERY,                     // IFR clearance delivery (route, altitude, squawk)
  GROUND,                                 // surface movement
  TOWER,                                  // runway operations, circuit traffic
  APPROACH,                               // arrival sequencing, instrument approaches
  DEPARTURE,                              // departure sequencing after takeoff
  AREA_CONTROL,                           // en-route traffic between aerodromes
  AFIS,                                   // aerodrome flight information service (uncontrolled)
}

Authority(
  entityType: EntityType,                 // Taxiway, Runway, Circuit, Approach, etc.
  operations: Set<Operation>,             // Taxi, Cross, Takeoff, Land, Circuit, Sequence, etc.
)
```

Examples:
- **Clearance Delivery** has authority: `ClearanceDelivery` on routes/SIDs
- **Ground** has authority: `Taxi` on taxiways, `Cross` on runways, `Pushback` on stands/aprons
- **Tower** has authority: `Takeoff`/`Land` on runways, `Circuit` on circuit procedures
- **Approach** has authority: `Sequence` on STARs/approaches, `Hold` on holding patterns
- **AFIS** has authority: `Information` on all entities (provides information, not clearances)

A controller fills one or more roles:
- LOWG: three controllers — Clearance Delivery, Ground, and Tower
- Crawton (EGZC): one controller fills both Ground and Tower
- Uncontrolled with AFIS (EGZA): one AFIS operator provides information; aircraft are self-separating
- Uncontrolled without AFIS: no controllers; aircraft are `SELF_MANAGED`

The role is the unit of authority. The controller is the unit of staffing. Phraseology references the role name ("contact Tower"), which is also the controller's callsign when filling that role. Handoff points derive naturally from role boundaries — "contact Tower when ready" at the holding point is the boundary between Ground's taxiway authority and Tower's runway authority.

**Not modelled (deferred):** Instance-level authority (e.g. Ground East/West controlling specific taxiways). Authority is over entity types, not specific entity instances. This is sufficient for single-controller and small multi-controller aerodromes.

## AIP (Aeronautical Information Publication)

The AIP is the operational publication for an aerodrome. It holds the information a pilot needs to operate there — frequencies, ATIS, handoff sequences, special procedures — that isn't geometry or entity structure.

```
AerodromeAIP(
  atisFrequency: Frequency?,             // ATIS broadcast frequency (if available)
  handoffSequence: List<HandoffStep>,     // ordered: who hands off to whom
  activeRunwaySelection: ActiveRunwayRule, // how active runway is determined (wind, preference)
  noiseAbatement: List<NoiseRule>?,       // circuit restrictions, preferred runway times, etc.
  specialInstructions: List<String>?,     // "report overhead at 2000ft", etc.
)
```

The AIP publishes the ATIS frequency. ATIS *content* (active runway, QNH, information letter, transition level, weather) is dynamic runtime state — it changes with conditions and is modelled in the simulation layer, not the path network.

```

HandoffStep(
  from: RoleName,                         // Ground
  to: RoleName,                           // Tower
  at: HandoffPoint,                       // at holding point, at airborne, at boundary fix, etc.
  pilotAction: PilotAction,              // "contact Tower on 118.25", "monitor Tower", etc.
)
```

The AIP composes with the role model: roles define *who has authority*, the AIP defines *how operations flow between them*. A pilot arriving at LOWG consults the AIP to know: get ATIS on 126.425, contact Ground on 121.700, expect handoff to Tower on 118.200 at the holding point.

## Composite: Aerodrome

```
Aerodrome(
  icao: AerodromeId,                      // "LOWG"
  elevation: Feet,                        // aerodrome elevation AMSL (for altimeter settings)
  magneticVariation: Degrees,             // local magnetic variation (for runway numbering)
  transitionAltitude: Level,              // below this: altitudes (QNH); above: flight levels
  transitionLevel: Level?,                // above this: flight levels; may be dynamic (set by ATC)
  aip: AerodromeAIP,                      // operational publication
  roles: Map<RoleName, AerodromeRole>,
  controllers: Map<ControllerId, Set<RoleName>>,
  runways: Map<RunwayId, Runway>,
  taxiways: Map<TaxiwayId, Taxiway>,
  stands: Map<StandId, Stand>,
  aprons: Map<ApronId, Apron>,
  circuits: List<CircuitProcedure>,        // may have multiple per runway (L/R, noise abatement)
  sids: Map<String, SID>,
  stars: Map<String, STAR>,
  approaches: Map<String, InstrumentApproach>,
  holdingPatterns: Map<HoldingPatternId, HoldingPattern>,
)
```

An aerodrome doesn't own the path segments directly. It's a collection of overlay entities that together describe an airport. The physical geometry is the union of all path segments referenced by these entities.

`transitionAltitude` is fixed per aerodrome. `transitionLevel` may be dynamic — in some states it is published, in others it is set by ATC based on QNH and included in the ATIS. When `transitionLevel` is null, it must be provided at runtime.

## Composite: AviationWorld

```
AviationWorld(
  fixes: Map<FixId, Fix>,
  aerodromes: Map<AerodromeId, Aerodrome>,
  airways: Map<AirwayId, Airway>,
  vfrRoutes: Map<VfrRouteId, VfrRoute>,
  airspace: Map<AirspaceVolumeId, AirspaceVolume>,
  firs: Map<FirId, FlightInformationRegion>,
)
```

This is the complete world. Everything the simulation knows. Controllers, pilots, physics, and phraseology all derive their view from this.

## Derived: WorldIndex

At construction time, walk all entities and derive:

```
WorldIndex(
  positions: Map<PointId, Position>,
  adjacency: Map<PointId, Set<PointId>>,
  surfaceAt: (PointId, PointId) -> Surface,
  lengthOf: (PointId, PointId) -> Meters,
  widthOf: (PointId, PointId) -> Meters,
  entitiesAt: (PointId) -> Set<EntityRef>,
)
```

This is a cache, not a source of truth. Rebuilt when entities change. Used by physics and pathfinding at runtime.

`entitiesAt` returns all entities referencing a point. Callers know what they're looking for — the tick loop resolves operational context from the aircraft's current clearance, not from the raw entity set.

## Construction-time invariants

- Every point and segment is claimed by >= 1 entity (no orphan geometry)
- Every point is inside >= 1 airspace volume
- Every circuit is a closed loop
- Every runway has >= 1 holding point per direction (on some taxiway)
- Every stand is reachable from every holding point via taxiways
- Every runway exit references a valid taxiway
- Every SID starts at a runway's departure end
- Every STAR ends at an approach (or holding fix)
- Every instrument approach ends at a runway threshold and has a missed approach
- Shared points have consistent positions across all entities that reference them
- Each taxiway segment belongs to exactly one taxiway (junction points may be shared, segments may not)
- Every holding pattern has segments forming a closed racetrack
- Every aerodrome role is filled by at least one controller (or the aerodrome is uncontrolled)
- Reciprocal runways share at least one segment

## How the downstream systems change

### Phase transitions

Today keyed off `segment.kind`. In this model, the tick loop resolves operational context from entities:

- "Aircraft entered a segment belonging to Runway 16C" → runway phase
- "Aircraft entered the downwind leg of CircuitProcedure 16C" → report downwind
- "Aircraft reached the holding point on Taxiway Alpha for Runway 16C" → holding short

The path segment is inert geometry. The entity gives it meaning.

### Routing

Today BFS filtered by SegmentKind. In this model, controllers compose clearances from overlay entities:

- "Taxi via Alpha, Bravo, hold short 16C" → Taxiway(A).path + Taxiway(B).path, stop at holdingPoint
- "Cleared KEMIK 1A departure" → SID("KEMIK 1A").waypoints
- "Extend downwind" → CircuitProcedure.extendedDownwind.extendedPath, pilot takes off-ramp when cleared

The pilot follows waypoint sequences. Physics walks path segments between waypoints.

### Controller jurisdiction

Today `Set<SegmentId>`. In this model, derived from roles:

- Ground role has authority over: taxiways (taxi), runways (cross), stands/aprons (pushback)
- Tower role has authority over: runways (takeoff/land), circuits (circuit instructions)
- Approach role has authority over: STARs, approaches (sequence), holding patterns (hold)

No manually maintained segment sets. A controller's jurisdiction is the union of entity sets for all roles they fill.

### Conflict detection

Today manually specified `Set<SegmentPair>`. In this model, structural conflicts are inferred from entity relationships at construction time:

- Runway and its final approach share a threshold point → conflict pair
- Two crossing runways share a point → conflict pair
- Taxiway crossing a runway shares a point → crossing clearance required

Separation and wake turbulence conflicts are not structural — they are runtime rules operating on aircraft positions and paths, not on shared points.

### Phraseology

The comm module gets what it needs directly from entities:

- "Taxi via Alpha, hold short runway one six Charlie" — from Taxiway.name and Runway.id
- "Report downwind" — from CircuitLeg.name
- "Cleared KEMIK one alpha departure" — from SID.name
- "Direct BALSI" — from Fix.name
- "Extend downwind, I'll call your base" — from ExtendedDownwind existence

No reverse-engineering names from segment IDs.

### Runway crossings

Detected from entity relationships. If a taxiway's path passes through a point that's on a runway, that's a crossing. The controller issues "Cross runway 16C." Falls out naturally from shared points between Taxiway and Runway entities. (Taxiway segments are exclusive to one taxiway, but junction *points* can be shared with runways.)

## Vectoring and approach sequencing

Aircraft are always on pre-built paths. The simulation controls the entire airspace — every possible way an aircraft can arrive at a point is a pre-built path in the world. There is no runtime path creation and no off-network movement.

**Vectoring is branch selection.** When a controller "vectors" an aircraft, they are selecting which fork the aircraft takes at a branch point in the path network. The geometry is pre-built; the controller decides which branch. The phraseology layer renders the branch selection as heading instructions ("turn left heading 270, vectors for ILS 16C") even though mechanically the aircraft is following a pre-built path.

The simulation knows the difference between a vector branch and an airway/SID junction — this matters for training purposes, as the trainee needs to learn vectoring phraseology and decision-making.

### Pre-built approach alternatives

For each instrument approach from each arrival direction, the path network includes multiple pre-built paths of varying length and geometry, all terminating on the final approach course. A realistic set for one ILS approach from one direction:

- **3–4 downwind lengths** (short, medium, long, extended) — each a separate path with off-ramps at 2–3nm intervals for the base turn
- **1–2 intercept distances** (close, standard)
- **1 straight-in option** where geometry permits

This gives roughly 7–9 paths per arrival quadrant per runway. For four cardinal arrival directions and one runway, ~30 paths. Manageable at construction time.

### Spacing: path selection + speed control

The controller achieves approach spacing through two tools together:

- **Path selection** (coarse) — choosing a short vs. long downwind gives spacing in ~5–8nm chunks
- **Speed control** (fine) — covers ~3–6nm of adjustment within each path length

These ranges overlap, giving effectively continuous spacing. Speed control is not optional — it is load-bearing for realistic sequencing.

The extended downwind with off-ramps model (already in the circuit design) is exactly this pattern applied to IFR approaches: the aircraft is on a downwind leg, and the controller decides at which off-ramp to turn base. Each off-ramp is a branch point. Off-ramps spaced at 2–3nm, combined with speed control, match real-world controller granularity.

### Delay absorption

- **Holding patterns** at published fixes — standard delay tool, pre-built as path entities
- **360 for spacing** — pre-built orbit loops at 3–5 strategic points per arrival flow (entry to terminal area, start of downwind, common metering points). Same concept as circuit orbit points.
- **Path stretching** — selecting a longer downwind alternative

### Missed approach re-entry

Pre-built paths from the missed approach procedure back into the approach pattern (typically a loop back to a downwind leg). The controller selects which re-entry path based on the traffic picture.

### What this does not model

- **Fully improvised vectoring** in chaotic/weather conditions — out of scope. The simulation does not generate conditions that require creative free-form vectors.
- **Continuous incremental heading tweaking** — approximated by branch points near the intercept. The discrete granularity matches real-world controller precision when accounting for reaction time and communication delay.
- **Pilot-initiated deviations to novel points** — common shortcuts (direct-to between key fixes) are pre-built. Novel requests are outside scope.

### Deviation handling

If a pilot misses a branch point (fails to take the assigned turn), the aircraft continues on its current path to the next available branch or the path's end. If the aircraft reaches the end of its path with no further geometry, the simulation stops and replays from a checkpoint. This is a pilot error condition, not a normal operating mode.

## Construction-time invariants

- Every point and segment is claimed by >= 1 entity (no orphan geometry)
- Every point is inside >= 1 airspace volume
- Every airspace volume belongs to an FIR
- Every circuit is a closed loop
- Every runway has >= 1 holding point per direction (on some taxiway)
- Every stand is reachable from every holding point via taxiways and apron paths
- Every runway exit references a valid taxiway
- Every SID starts at a runway threshold
- Every SID has altitude constraints meeting minimum climb gradient (3.3% default)
- Every STAR ends at a fix shared with an instrument approach (or holding fix)
- Every instrument approach ends at a runway threshold and has a missed approach
- Shared points have consistent positions across all entities that reference them
- Each taxiway segment belongs to exactly one taxiway (junction points may be shared, segments may not)
- Each apron segment belongs to exactly one apron (junction points may be shared with taxiways)
- Every holding pattern has a closed loop path
- Every aerodrome role is filled by at least one controller (or the aerodrome is uncontrolled/AFIS)
- Reciprocal runways share at least one segment
- No naming collisions between airways/SIDs/STARs in the same airspace

## Not modelled (deferred)

The following are acknowledged as real-world concerns but are outside the current simulation scope:

- **Displaced threshold asymmetry** — area between runway start and displaced threshold is usable for takeoff but not landing
- **Stopway geometry** — ASDA references stopway length but no physical segments exist beyond the runway end
- **Taxiway weight/wingspan restrictions** — all taxiways are assumed usable by all aircraft types
- **Instance-level controller authority** — Ground East/West over specific taxiways; authority is type-level only
- **LAHSO operations** — land and hold short of intersecting runway
- **Multiple aircraft on the same runway** — reduced separation operations
- **Vehicle movements** — tugs, fuel trucks, emergency vehicles on taxiways
- **Simultaneous parallel operations** — independent/dependent parallel approaches
- **Wake turbulence categories** — Doc 4444 §4.9
- **RMZ/TMZ** — SERA.6005
- **Minimum Sector Altitudes** — Doc 8168
- **Approach category A–E differentiation** — Doc 8168
- **Semi-circular cruising level rule** — SERA.5005(d)
- **250kt below FL100 rule** — SERA.6001 (could be a global speed constraint in the simulation layer)
- **Runtime runway state** — active for departures/arrivals/both/closed (simulation layer concern, not path network)
- **Dynamic ATIS content** — active runway, QNH, information letter (simulation layer concern)
- **Inter-role coordination** — Tower releasing departures to Approach, Ground requesting crossings from Tower (simulation layer concern)
- **Runway change procedures** — transitioning between active runway configurations mid-session