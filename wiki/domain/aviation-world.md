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

## AircraftType (`:protocol`)

Aircraft types are doctrine-anchored data both the pilot and the
controller reference (sealed catalogue in
`protocol/.../AircraftType.kt`). The pilot reads `kinematics`,
`circuitPattern`, `runUpDurationMs`, and (fn-14) `maxCrosswindKnots`
directly; the controller sees only the strip-projected
`icaoDesignator` + sensor-projected `wakeCategory` + the firewall-
narrow `runwayRequirementsFor(designator)` lookup. The type itself
never crosses the firewall.

Doctrine-anchored fields (per-leaf KDoc cites the source):
- `icaoDesignator: IcaoTypeDesignator` — ICAO Doc 8643.
- `wakeCategory: WakeCategory` — ICAO Doc 8643.
- `kinematics: Kinematics` — POH / FCOM (taxi / rotation / climb /
  approach speed; climb rate; waypoint-capture radius).
- `runwayLengthM: RunwayLengthRequirements` — TCDS / AFM.
- `circuitPattern: CircuitPattern` — POH §4 / FAA AIM 4-3-3 (pattern
  altitude AGL; downwind lateral offset).
- `cruiseAltitudeM: Double` — engineering tuning (sim default).
- `runUpDurationMs: Long` — POH §4 procedural duration.
- **`maxCrosswindKnots: Knots`** (fn-14) — POH "maximum demonstrated
  crosswind component" per 14 CFR §23.233(a) (pre-Amendment 64) /
  FAA AC 23-8B. Consumed by the pilot's reactive-GA recognition
  `derivePilotEvent`'s crosswind branch — when the crosswind component
  computed from the world's wind report exceeds this value while the
  aircraft is on final, the pilot self-initiates a go-around. Per FAA
  AFH (FAA-H-8083-3C) Chapter 9, attempting a landing in crosswinds
  exceeding the demonstrated value is Common Error #1. Per-leaf
  values:
  - **C172** = 15 kt (POH §2: "Maximum demonstrated crosswind velocity
    is 15 knots (not a limitation)").
  - **B738** = 33 kt (FCOM Limitations §1: 33 kt steady crosswind on
    dry/grooved runway).
- **`maxTailwindKnots: Knots`** (fn-15) — typed maximum tailwind
  component the type's operating handbook (POH / FCOM) or industry
  guidance recognises as the operational maximum. Consumed by the
  pilot's reactive-GA recognition `derivePilotEvent`'s tailwind branch
  — when the tailwind component computed from the world's wind report
  against the active runway exceeds this value while the aircraft is
  on final, the pilot self-initiates a go-around. Cross-reference
  `maxCrosswindKnots` for the sibling pattern (lateral control
  authority); tailwind is the **complementary axis** (touchdown energy
  / runway remaining / go-around margin) and the physically stronger
  constraint when both apply, hence `derivePilotEvent`'s branch order
  DA → tailwind → crosswind.

  **Per-type doctrinal severity asymmetry** (load-bearing — codex
  round-1 closure from fn-15.1; surfaced in
  `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md`
  as a deliberate modelling choice):
  - **C172** = 10 kt — **advisory, not a hard limitation**. The
    Cessna 172R / 172S NAV III POH §2 (Operating Limitations) does
    **NOT** publish a tailwind component limitation. The 10 kt value
    is the **FAA AFH Ch 9 (FAA-H-8083-3C) industry-standard advisory**
    for light singles — performance information / advisory framing,
    not a certification limitation. (Same modelling rationale as the
    crosswind axis: the sim models a competent VFR pilot as going
    around when the advisory is exceeded.)
  - **B738** = 15 kt — **hard operational limitation**. The Boeing
    737-800 FCOM Limitations §1 publishes 15 kt steady tailwind on dry
    runway as a hard operational limitation (Limitations section, no
    exception). Same doctrinal severity as the FCOM crosswind clause.

  The asymmetry only surfaces on the tailwind axis (crosswind has POH-
  demonstrated values on both leaves). **Manufacturer values are not
  regulations** — the typed datum lives on `AircraftType`, NOT in
  `RegulationDatabase`. `RegulationDatabase` carries the public
  regulatory citations (FAA AFH Ch 9 — tailwind risk anchor;
  ICAO Doc 4444 §7.11.6 — 5 kt reduced-runway peer anchor) that
  motivate the recognition behaviour, NOT the POH/FCOM values
  themselves.

Reuses the positive-only `Knots` smart type (`protocol/.../Instruction
.kt:80`); every POH / FCOM / AFH-advisory crosswind / tailwind value
is ≥ 1 kt by construction.

The POH "maximum demonstrated crosswind" is **performance information,
NOT a limitation** in the certification sense. The sim models a
competent VFR pilot as going around when the demonstrated value is
exceeded; this is the correct modelling choice even though it
overstates real-world strictness (real PICs sometimes attempt and
succeed beyond). The personal-minimums judgement layer is filed as a
deferment (`D-PASS-g3a-react-personal-minimums` per fn-14 +
`D-PASS-g3a-react-tailwind-personal-minimums` per fn-15).
