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

## PhysicalGeometry

All spatial data lives here. Points have 2D meter positions (projected from lat/lon via a datum). Segments connect pairs of points with length, width, shape (straight or arc), and surface type (GROUND, RUNWAY, SKY).

`GeometrySegmentId` is **bidirectional** -- `between(a, b)` normalises to canonical order. This ensures a taxiway segment is the same whether traversed A→B or B→A.

## Aerodrome

Contains everything about one airport: runways, taxiways, stands, aprons, circuits, SIDs, STARs, approaches, holding patterns, controller roles.

Key relationships:
- Runways have thresholds (must be first point of path) and exits (connecting to taxiways)
- Taxiways have holding points (associated with specific runways)
- Circuit procedures form closed loops with sequential legs
- SIDs anchor at runway threshold; approaches end at runway threshold
- Holding patterns reference a fix and have a closed loop path

## Validation

~20 rules enforced by `WorldValidation`. Key ones:
- All referenced points/segments must exist in geometry
- Every runway needs at least one holding point
- Stands must be reachable from runway holding points
- SIDs start at runway threshold
- Approaches end at runway threshold
- Circuit legs connect sequentially and form a closed loop
- Airspace covers all geometry points

## Construction

Currently: `buildValidatedWorld(world)` wraps validation in `Either`. The existing test fixture (`sampleWorld()` in `WorldConstructionTest.kt`) hand-builds a complete valid world with 21 points, 23 segments, 1 aerodrome.
