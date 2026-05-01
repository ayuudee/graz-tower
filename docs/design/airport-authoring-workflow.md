# Airport Authoring Workflow

This document defines how hand-authored airport worlds should be organised.

It exists to keep CAD work, source-data reconciliation, and world-building systematic.

## Scope

This workflow is for airports where:

- geometry is being authored by hand in CAD/GIS
- X-Plane / OFMX / CIFP data are used as reference and metadata sources
- the output will eventually be an `AviationWorld`

It is not a design for a generic "import any airport automatically" pipeline.

## Core rules

1. CAD is authoritative for geometry that the source data does not contain or does not contain accurately enough.
2. Source datasets remain authoritative for published metadata and procedure content unless we have a deliberate override.
3. Each drawing may live in its own local frame. Do not assume multiple DXFs share scale, rotation, or origin.
4. Reconciliation must be explicit. We should know which source won for each class of data.
5. Partial authoring is allowed. Importers must be able to report incomplete structure without forcing a final world too early.

## Source precedence

Use explicit precedence by data class.

| Data class | Primary source | Secondary source | Notes |
|---|---|---|---|
| Runway strip axis / gross runway geometry | `apt.dat` | OFMX runway/runway-direction data | Use the full strip axis as the geometric control unless a specific airport needs an override. |
| Displaced thresholds | Deferred / optional | CIFP runway thresholds | Do not model unless the current slice requires it. |
| Taxiway centreline geometry | CAD | `apt.dat` taxi network | CAD can correct or simplify the X-Plane network where needed. |
| Holding points / local ground markers | CAD markers | `apt.dat` taxi nodes, taxi signs, active zones | Reconcile authored points against the X-Plane graph. |
| Stands / aprons | CAD | `apt.dat` stands / startup data | Use source data as naming/reference support. |
| VFR circuits | CAD | charts / local knowledge / OFM imagery | This is exactly the geometry we decided to hand-author. |
| VFR route geometry | CAD | OFMX reporting points | OFMX gives point names, not route geometry. |
| IFR procedure content | CIFP | charts | CIFP is the published procedure source, but fix positions may need supplementation. |
| IFR fix / navaid positions | OFMX / nav databases / manual lookup | CIFP identifiers | CIFP names the fixes but does not locate all of them. |
| Airspace names / class / limits | OFMX | charts | Current domain model is point-claim based, not polygon-first. |
| Controller units / frequencies | OFMX / `apt.dat` | charts | Prefer OFMX for operational metadata. |

## Authoring package

Each airport should be treated as an authoring package containing:

- one or more CAD/GIS geometry artifacts
- a human-readable airport workboard
- a machine-readable manifest when importer work starts
- references to which source datasets are being reconciled

Suggested layout:

```text
cad/airports/
  lowg.dxf
  lowg_circuits.dxf
  lowg_osm_underlay.geojson  # optional OSM vector underlay in WGS84
  lowg-authoring.md
  lowg.manifest.json      # add when importer work begins
```

## Optional OSM underlay

For future airports, a good bootstrap path is:

1. generate an X-Plane baseline DXF
2. either export an OSM vector extract around the airport as GeoJSON or fetch it directly from Overpass
3. project that GeoJSON into the airport-local DXF frame as an underlay
4. author missing geometry on top

The current bridge supports both modes:

- local GeoJSON input
- direct Overpass fetch with a cached GeoJSON artifact

For the direct path:

```bash
nix-shell -p python3 --run 'PYTHONDONTWRITEBYTECODE=1 python3 bin/export_osm_geojson_dxf.py cad/airports/lowg.manifest.json --fetch-overpass'
```

That will:

- derive a local airport bounding box from `apt.dat`
- fetch OSM underlay classes directly from Overpass
- cache the fetched GeoJSON under `cad/airports/<airport>_osm_underlay.geojson`
- emit the projected DXF under `cad/airports/<airport>_osm_underlay.dxf`

Use OSM as a visual/reference substrate for roads, rail, rivers, settlement edges, and similar
ground context.

Do not treat OSM as the semantic source of truth for aerodrome/procedure entities.

That means:

- OSM helps with visual registration and local geography
- `apt.dat` / OFMX / CIFP / authored CAD still define the aviation world
- for CAD tracing, a rendered local raster underlay embedded by reference into the working DXF is now the preferred default
- cached vector OSM remains useful as a reference/debug layer, but should not be stuffed into the
  main working DXF by default

## Import model

The importer should not assume "one CAD polyline equals one domain procedure".

Instead, import authored geometry as a graph:

- points
- segments
- marker points / anchors
- attachments where a path endpoint lands on another path's segment

This is important for VFR circuits and other shared geometry. Different procedures may traverse shared sky segments at different entry points, with different leg names.

### Required importer capabilities

- independent transform per drawing
- anchor-based similarity transforms (scale, rotation, translation)
- point-on-segment splitting when one authored path joins another mid-segment
- partial-structure reporting: open chains, unmatched markers, unresolved names, conflicting source data

## Ordered workflow

Work through airports in a fixed order.

### 1. Establish the geometric control frame

- Choose the reference datum / local XY frame.
- Choose the control geometry, usually the full runway strip axis from `apt.dat`.
- Fit each drawing independently into that frame.
- Record the transform and confidence.

### 2. Build the ground graph

- Runway strips
- taxiways
- holding points
- aprons
- stands

Cross-check against `apt.dat` taxi nodes / edges / active zones.

### 3. Build the VFR graph

- circuits
- joins
- go-arounds
- VFR routes
- reporting point connectivity

Treat the authored drawing as geometry first. Procedure semantics can be layered on afterwards.

### 4. Attach IFR procedure content

- SIDs
- STARs
- approaches
- missed approaches
- holds

Use CIFP as the content source. Where fix positions are missing, record the gap explicitly instead of guessing.

### 5. Attach operational metadata

- frequencies
- controller roles
- airspace names / class / limits
- local procedures and runway-use notes

### 6. Validate and reconcile

Produce a reconciliation report covering:

- unmatched CAD markers
- suspect geometry regions
- unresolved joins
- missing fix positions
- missing procedure anchors
- source conflicts

Do this before trying to emit a final `AviationWorld`.

## Authoring conventions

### Geometry

- Work in meters where possible.
- Prefer stable anchor points over visual alignment by eye.
- Record any deliberate simplification.
- In the combined working DXF, reserve `NEW_*` layers for collaborative hand-authored overlays that
  have not yet been promoted into the structured airport package.
- The combined-DXF exporter should preserve existing `NEW_*` content across rebuilds so the working
  file remains a safe collaboration surface.
- Once a `NEW_*` layer has been translated upstream into structured/package data, remove it from the
  working DXF rather than letting duplicate truths accumulate.

### Semantics

- Keep geometry and meaning separate.
- A shared segment can belong to multiple higher-level procedures.
- Leg naming belongs in the overlay/procedure layer, not in the raw graph.

### Waiting / holding geometry

- The intended domain model for loitering / waiting is not yet aligned with the current runtime/FM implementation.
- Today, `OrbitPoint` and `HoldingPattern` are represented as closed loop paths in code and proofs. That is an implementation shortcut, not a settled modelling decision.
- The intended shape is a loiter-capable sky region with horizontal extent and altitude, not merely a named closed path.
- Until that model is corrected, avoid over-committing airport authoring conventions to the current closed-loop representation. Record the intended waiting geometry and attachment points explicitly, but treat any path-loop encoding as provisional.

### Partial work

- "Present but provisional" is a valid state.
- "Known missing" is a valid state.
- "Guessed" should be avoided; if unavoidable, mark it explicitly.

## Deliverables by phase

Before final world-building exists, each airport should still be able to produce:

- a transform summary
- a geometry inventory
- a source precedence summary
- a reconciliation report
- a remaining-work checklist
- a generated plate-style review artifact that puts geometry, published procedures, and local operational notes into one inspectable output

These are the outputs that keep the work organised between cycles.
