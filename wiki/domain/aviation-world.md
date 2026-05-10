# AviationWorld Domain Model

The root type for a complete simulation environment.

## Structure

```
AviationWorld
  geometry: PhysicalGeometry       -- all points and segments
  fixes: Map<FixId, Fix>           -- navaids and waypoints
  aerodromes: Map<AerodromeId, Aerodrome>
  airways: Map<AirwayId, Airway>
  vfrRoutes: Map<VfrRouteId, VfrRoute>
  airspace: Map<AirspaceVolumeId, AirspaceVolume>
  firs: Map<FirId, FlightInformationRegion>
```

Current runtime notes:
- `VfrRoute` now carries an `airspaceProfile`, not one mandatory route-wide `airspaceClass`.
- `AirspaceVolume` now names explicit `memberPoints`, and may also carry explicit boundary geometry.
- `Aerodrome.aip` can now own operational sectors and published VFR procedures.
- `OperationalSector` now uses a typed anchor shape rather than nullable
  `anchorPoint` / `anchorRole`.
- `PublishedVfrProcedure` now uses `PlateId`, typed contact timing, sealed
  published point references / map-label locations, an advisory bag, and
  explicit communication-failure structure. Raw publication status/provenance
  remains outside `core`.

## PhysicalGeometry

All spatial data lives here. Points have 2D meter positions (projected from lat/lon via a datum). Segments connect pairs of points with length, width, shape (straight or arc), and surface type (GROUND, RUNWAY, SKY).

`GeometrySegmentId` is **bidirectional** -- `between(a, b)` normalises to canonical order. This ensures a taxiway segment is the same whether traversed A→B or B→A.

## Aerodrome

Contains everything about one airport: runways, taxiways, stands, aprons, circuits, SIDs, STARs, approaches, holding patterns, controller roles, and AIP-owned operational structures such as operational sectors and published VFR procedures.

Key relationships:
- Runways have thresholds (must be first point of path) and exits (connecting to taxiways)
- Runways optionally carry an `obstruction: RunwayObstruction?` field (fn-12) — a typed declaration that the runway is unavailable for landing/take-off until a specified `clearsAt: SimTime` deadline, consumed by the controller's reactive obstruction-GA rule and the pre-clearance landing gate. The `clearsAt` value is immutable for an obstruction's lifetime (`None → Some → None` only; `Some(old) → Some(new)` is a programming error caught by the sim's world-diff producer)
- Taxiways have holding points (associated with specific runways)
- Circuit procedures form closed loops with sequential legs
- SIDs anchor at runway threshold; approaches end at runway threshold
- Holding patterns reference a fix and have a closed loop path
- Published VFR procedures can reference VFR routes, operational sectors, and circuit procedures
- Published VFR procedures can also carry typed contact timing and explicit
  anchored publication references without forcing raw chart/status data into
  the runtime model

## Validation

~20 rules enforced by `WorldValidation`. Key ones:
- All referenced points/segments must exist in geometry
- Every runway needs at least one holding point
- Stands must be reachable from runway holding points
- SIDs start at runway threshold
- Approaches end at runway threshold
- Circuit legs connect sequentially and form a closed loop
- Airspace covers all geometry points
- AIP references to routes, sectors, and circuits must resolve

## Construction

Currently: `buildValidatedWorld(world)` wraps validation in `Either`. The existing test fixture (`sampleWorld()` in `WorldConstructionTest.kt`) hand-builds a complete valid world with 21 points, 23 segments, 1 aerodrome.
