# LOWG Authoring Workboard

This file tracks the hand-authored LOWG package.

The goal is to keep the CAD work, source reconciliation, and remaining procedure work explicit.

## Artifacts

- Ground geometry: [lowg.dxf](/home/andrew/dev/projects/twr2/cad/airports/lowg.dxf)
- VFR circuit geometry: [lowg_circuits.dxf](/home/andrew/dev/projects/twr2/cad/airports/lowg_circuits.dxf)
- Ad hoc airspace geometry: [lowg_airspace_working.dxf](/home/andrew/dev/projects/twr2/cad/airports/lowg_airspace_working.dxf)
- Normalized airspace geometry: [lowg_airspace_working_normalized.dxf](/home/andrew/dev/projects/twr2/cad/airports/lowg_airspace_working_normalized.dxf)
- X-Plane baseline geometry: [lowg_xplane_baseline.dxf](/home/andrew/dev/projects/twr2/cad/airports/lowg_xplane_baseline.dxf)
- Combined working geometry: [lowg_working_combined.dxf](/home/andrew/dev/projects/twr2/cad/airports/lowg_working_combined.dxf)
- OSM underlay source: [lowg_osm_underlay.geojson](/home/andrew/dev/projects/twr2/cad/airports/lowg_osm_underlay.geojson)
- OSM underlay geometry: [lowg_osm_underlay.dxf](/home/andrew/dev/projects/twr2/cad/airports/lowg_osm_underlay.dxf)
- OSM underlay raster: [lowg_osm_underlay.png](/home/andrew/dev/projects/twr2/cad/airports/lowg_osm_underlay.png)
- OSM underlay placement metadata: [lowg_osm_underlay_placement.json](/home/andrew/dev/projects/twr2/cad/airports/lowg_osm_underlay_placement.json)
- Authoring manifest: [lowg.manifest.json](/home/andrew/dev/projects/twr2/cad/airports/lowg.manifest.json)
- Reconciliation report tool: [airport_authoring_report.py](/home/andrew/dev/projects/twr2/bin/airport_authoring_report.py)
- SVG render tool: [render_airport_authoring.py](/home/andrew/dev/projects/twr2/bin/render_airport_authoring.py)
- Plate-pack render tool: [render_airport_plate.py](/home/andrew/dev/projects/twr2/bin/render_airport_plate.py)
- Plate view-model translator: [airport_plate_view_model.py](/home/andrew/dev/projects/twr2/bin/airport_plate_view_model.py)
- Structured airport-package builder: [airport_structured_package.py](/home/andrew/dev/projects/twr2/bin/airport_structured_package.py)
- Structured airport-package projection tool: [project_structured_airport_package.py](/home/andrew/dev/projects/twr2/bin/project_structured_airport_package.py)
- Structured airport-package design note: [structured-airport-package.md](/home/andrew/dev/projects/twr2/docs/design/structured-airport-package.md)
- Migration-to-core gap handoff: [airport-migration-core-gap.md](/home/andrew/dev/projects/twr2/docs/design/airport-migration-core-gap.md)
- Entity-bundle projection tool: [project_airport_entities.py](/home/andrew/dev/projects/twr2/bin/project_airport_entities.py)
- World-candidate projection tool: [project_airport_world_candidate.py](/home/andrew/dev/projects/twr2/bin/project_airport_world_candidate.py)
- DXF airspace export tool: [export_airspace_dxf.py](/home/andrew/dev/projects/twr2/bin/export_airspace_dxf.py)
- X-Plane baseline DXF export tool: [export_xplane_baseline_dxf.py](/home/andrew/dev/projects/twr2/bin/export_xplane_baseline_dxf.py)
- Combined working DXF export tool: [export_airport_working_dxf.py](/home/andrew/dev/projects/twr2/bin/export_airport_working_dxf.py)
- OSM GeoJSON-to-DXF underlay tool: [export_osm_geojson_dxf.py](/home/andrew/dev/projects/twr2/bin/export_osm_geojson_dxf.py)
- OSM raster underlay tool: [render_osm_underlay_raster.py](/home/andrew/dev/projects/twr2/bin/render_osm_underlay_raster.py)
- DXF airspace normalizer: [normalize_airspace_dxf.py](/home/andrew/dev/projects/twr2/bin/normalize_airspace_dxf.py)
- Entity-fit audit: [lowg-entity-fit.md](/home/andrew/dev/projects/twr2/cad/airports/lowg-entity-fit.md)
- Render outputs: [rendered/lowg/index.html](/home/andrew/dev/projects/twr2/cad/airports/rendered/lowg/index.html)
- Plate pack: [rendered/lowg/plate/index.html](/home/andrew/dev/projects/twr2/cad/airports/rendered/lowg/plate/index.html)
- Structured airport package: [structured-airport-package.json](/home/andrew/dev/projects/twr2/cad/airports/rendered/lowg/structured-airport-package.json)
- Projected entity bundle: [entity-bundle.json](/home/andrew/dev/projects/twr2/cad/airports/rendered/lowg/entity-bundle.json)
- Current-core world candidate: [world-candidate.json](/home/andrew/dev/projects/twr2/cad/airports/rendered/lowg/world-candidate.json)
- Current-core validation report: [world-validation-report.json](/home/andrew/dev/projects/twr2/cad/airports/rendered/lowg/world-validation-report.json)
- X-Plane airport reference: [LOWG.dat](/home/andrew/dev/projects/twr2/migration/src/commonTest/resources/airports/LOWG.dat)
- CIFP procedures: [LOWG.dat](/home/andrew/dev/projects/twr2/data/cifp/LOWG.dat)
- LOWG charts: [LOWG](/home/andrew/dev/projects/twr2/data/charts/LOWG)

## Working assumptions

- Use the full 16C/34C strip from `apt.dat` as the geometric control for runway alignment.
- Do not model the displaced threshold in the current slice.
- Treat each DXF as living in its own native frame; compute a transform per drawing.
- Treat the VFR circuit drawing as a shared graph, not as one closed polyline per eventual procedure.
- Treat `NEW_*` layers in [lowg_working_combined.dxf](/home/andrew/dev/projects/twr2/cad/airports/lowg_working_combined.dxf) as collaborative overlays pending promotion into the structured airport package.

## Source precedence

| Topic | Primary source | Secondary source | Notes |
|---|---|---|---|
| Runway axis / strip geometry | `apt.dat` | OFMX runway data | Ground control frame. |
| Ground path geometry | `lowg.dxf` | `apt.dat` taxi graph | Use X-Plane as reconciliation reference. |
| VFR circuit geometry | `lowg_circuits.dxf` | charts / local knowledge | CAD is authoritative here. |
| VFR reporting point names | OFMX | charts | Geometry still needs hand authoring. |
| IFR procedure content | CIFP | charts | Geometry may require fix supplementation. |
| Airspace metadata | OFMX | charts | Current domain model is point-claim based. |
| Frequencies / units | OFMX / `apt.dat` | charts | Reconcile if they diverge. |

## Current findings

### Ground DXF

- The longest line in `lowg.dxf` aligns well with the X-Plane 16C/34C strip.
- Most authored holding / taxi geometry fits the X-Plane taxi graph closely after that transform.
- X-Plane `apt.dat` also carries explicit taxi-route names and location/sign records for LOWG. Those records support treating the tower-side component around marker `1` as a mixed `D -> A` cluster rather than forcing it to be a single taxiway name.
- One cluster on the north-east side is now identified as the `16/34L` glider runway and the taxiway leading to `16/34C`. It should be treated as a known source divergence from X-Plane, not as an unresolved suspect.

### VFR circuit DXF

- The circuit transform now explicitly uses the two user-confirmed centre-runway marker points from the sidecar as runway-end anchors. It no longer guesses by taking the farthest DXF point pair.
- The third marker lands near the X-Plane tower viewpoint and is a good cross-check.
- The drawing currently behaves as a shared graph:
  - one main component contains the centre-runway circuit structure
  - two side components terminate on segments of that main component
- The importer will need point-on-segment splitting where a side procedure attaches to a shared leg.
- The current sidecar anchor state is now:
  - `NE` is a new explicit DXF point
  - `SW` is a new explicit DXF point
  - `SE` is the existing SE open circuit endpoint
  - `NW` is treated as `AUTOBAHN-WEST` on the existing circuit graph
- Current runway attachment status under that fit is now:
  - the centre-runway circuit spine passes through `16C/34C`
  - the main `16R/34L`-parallel leg now lies on the runway axis as a deliberate local fudge and has been trimmed inward to make the turn geometry read more naturally
  - the main `16L/34R`-parallel leg now lies on the runway axis as a deliberate local fudge and has been trimmed inward to make the turn geometry read more naturally
- The remaining approximation is now concentrated in the turn geometry into and out of the side-runway legs. Those transitions are currently represented as short line-sampled curves rather than true arcs, but they preserve the exact runway attachments.

### Source reconciliation

- The manifest/report scaffold now exists:
  - `cad/airports/lowg.manifest.json`
  - `bin/airport_authoring_report.py cad/airports/lowg.manifest.json`
  - Ground taxiway / path naming is now scaffolded in `namedMappings.groundComponents`, separate from marker-point naming.
  - Published OFM procedure and briefing content is now scaffolded separately in `publishedVfrProcedures` and `publishedAerodromeInformation`, so route geometry is not forced to carry arrival/departure protocol, comm-failure, or local-operating notes.
- The current render scaffold now exists:
  - `bin/render_airport_authoring.py cad/airports/lowg.manifest.json`
  - `cad/airports/rendered/lowg/index.html`
  - The HTML output is now a single interactive map with layer toggles, pan/zoom, and airport/airspace/full extents views.
  - The intended layering is now explicit: DXF/sidecar/map are the authoring and collaboration layer, the rich structured airport package is the authoritative structured truth, and both the strict plate view model and the current-core world candidate are downstream translations from that package.
  - A first generated LOWG plate pack now exists:
  - `bin/render_airport_plate.py cad/airports/lowg.manifest.json`
  - `cad/airports/rendered/lowg/plate/index.html`
  - The pack mirrors the current OFM page set (`AD-1`, `AD-6`, `AD-3`, `AD-4`, `AD-5`, `PRC-1` to `PRC-5`, `ENR-1`, `AD-2`, `ARR-1`) using generated SVG page maps.
  - A generated structured airport package now exists:
  - `bin/project_structured_airport_package.py cad/airports/lowg.manifest.json`
  - `cad/airports/rendered/lowg/structured-airport-package.json`
  - The renderer is now behind an explicit anti-corruption layer: `bin/airport_plate_view_model.py` translates the structured LOWG airport package plus the validated current-core subset into a dedicated plate view model, and `bin/render_airport_plate.py` renders only from that view model.
  - The current plate pack is now intentionally entity-only. Publication-supplement content has been removed from the pages, so `AD-3` / `AD-4` / `AD-5` and the VFR procedure pages now expose the actual model/projection gaps instead of being filled out from OFM notes.
  - `AD-1`, `AD-6`, and `AD-2` still draw their aerodrome layers from the validated current-core subset in `cad/airports/rendered/lowg/world-candidate.json`, but now through the same plate view model boundary as the rest of the pack.
  - The renderer no longer carries dead fallback helpers that read raw scene/projection structures directly. The remaining plate path is now structurally constrained to the view model.
  - `PRC-5` is explicitly provisional because the east-hold geometry is not yet authored.
  - `AD-2` version 1 now uses the authored `NEW_Parking` graph and `NEW_Parking_Points` stand set as the primary parking/apron source.
  - The current structured package and current-core candidate both project that authored apron graph directly.
  - The remaining parking-side compromise is only at the taxiway interface: the joins from taxiway `A` back into the authored apron graph are still projected for version 1.
  - A direct OSM underlay now exists for LOWG as `lowg_osm_underlay.geojson` plus `lowg_osm_underlay.dxf`. The underlay is fetched straight from Overpass by `bin/export_osm_geojson_dxf.py --fetch-overpass`, cached locally as GeoJSON, and then projected into the shared local DXF frame for reference/debug use.
  - A rasterized version now also exists as `lowg_osm_underlay.png`, with exact local-frame placement data in `lowg_osm_underlay_placement.json`, generated by `bin/render_osm_underlay_raster.py`.
  - `lowg_working_combined.dxf` now embeds that raster underlay by DXF image reference on layer `REF_OSM_RASTER`, already lined up in the local airport frame. Vector OSM is no longer included by default, but can still be added explicitly with `bin/export_airport_working_dxf.py --include-vector-osm`.
  - `bin/export_airport_working_dxf.py` now preserves existing `NEW_*` layers from the working DXF across rebuilds, so collaborative overlays survive regeneration instead of being wiped.
  - `bin/airport_authoring_report.py` now reads DXF files with encoding fallback, so the R2000/`ANSI_1252` working DXF remains analyzable after the raster image reference was introduced.

### NEW_Parking

- `NEW_Parking` currently contains `39` authored line segments in the combined LOWG working DXF.
- Against the `56` visible X-Plane parking positions, the current fit is promising:
  - `42 / 56` are within `5m`
  - `48 / 56` are within `15m`
  - `54 / 56` are within `30m`
  - average nearest-segment distance is about `6.0m`
  - median nearest-segment distance is about `2.0m`
- The `A/G1` side is already very tight: `27` stands there average about `1.5m` from the authored parking geometry, with worst case around `3.4m`.
- The rougher areas are the `A/S1` and `A/X` sides, where a few gates remain around `20-32m` off the current parking geometry. Those look like local geometry/coverage gaps rather than a global registration problem.
- The naive endpoint-only topology summary is misleading here. The raw endpoint degree count is still `51` degree-1 endpoints, `12` degree-2 continuations, and `1` degree-3 junction, but `25` of those apparent open endpoints already lie exactly on another parking segment as point-on-segment joins.
- So this should not be read as “the apron graph is unfinished.” The more likely picture is:
  - many leaves are legitimate stand-end leaves
  - a large share of the apparent opens are LibreCAD point-on-segment crossings that were not explicitly snapped into vertices
  - the remaining roughness is local geometry coverage on the `A/S1` and `A/X` sides, not a global registration failure

### NEW_Parking_Points

- `NEW_Parking_Points` is readable from the working DXF and currently contains `38` authored point entities.
- Geometrically, that layer is clean: all `38 / 38` points lie exactly on the authored `NEW_Parking` segment geometry within machine epsilon.
- Compared to the older X-Plane visible stand inventory, these points should be treated as a curated authored subset rather than expected to match one-for-one:
  - the X-Plane visible stand set currently has `56` points
  - the new authored parking-point set has `38` points
- The current read is therefore:
  - `NEW_Parking` provides the authored parking/apron graph
  - `NEW_Parking_Points` provides the authored stand-point geometry on top of that graph
  - the older X-Plane stand points are now reference/reconciliation data, not the geometric source of truth
- The current LOWG manifest now carries the definitive row-order stand naming for those `38` authored points, and the structured airport package / current-core candidate now use that authored stand set instead of the older X-Plane stand inventory.
- The parking-graph interpretation issue is now resolved. The compiler now splits `NEW_Parking` at real line-line intersections and treats direct authored touches onto taxiway `A` as shared graph attachment points rather than forcing everything through the older three projected branch joins.
  - A dedicated entity-fit audit now exists in `cad/airports/lowg-entity-fit.md`. The current conclusion is that LOWG is ready for a version-1 entity projection, with the runtime slice now covering VFR routes, operational sectors, published VFR procedures, runway-specific directional circuits projected from the shared authored graph, and a worked low-level CTR/TMA airspace subset in the current-core candidate. The main remaining gaps are now broader surrounding-airspace projection beyond that worked slice and deferred hold semantics.
  - `SECTOR WHISKEY` and `SECTOR ECHO` are now encoded in the LOWG manifest as structured VFR operational sectors with working-DXF boundary geometry, CTR-boundary anchors (`SENDER DOBL` / `KALSDORF`), procedure links (`PRC-2` / `PRC-3`), contact-before-entry semantics, and the published `3000 ft MSL` ceiling.
  - A generated entity bundle now exists under `cad/airports/rendered/lowg/entity-bundle.json`. It is now a compatibility projection derived from the structured airport package rather than the primary structured truth.
  - That bundle now projects LOWG reporting-point routes into the widened runtime `VfrRouteAirspaceProfile` surface. In the current LOWG candidate, `vfr_southeast_entry_path` and `vfr_southwest_entry_path` now project as `InVolume(LO585)`, `vfr_western_corridor_path` now projects as a segmented `LO585 -> LO0EF_E` route with an explicit boundary transition point, and the mixed-boundary northeast route is still left unassigned rather than being forced through invented authority.
  - The runtime AIP boundary is now also typed more tightly in the LOWG current-core projection: `PlateId`, explicit contact timing, sealed published point / sector-anchor references, grouped advisories, and explicit communication-failure structure now exist in `world-candidate.json`, while raw publication scaffold status/provenance stays in the structured airport package.
  - A generated current-core world candidate now exists under `cad/airports/rendered/lowg/world-candidate.json`, alongside a JVM-side validator harness in `migration/src/jvmTest/kotlin/xyz/easiersaid/twr/migration/world/LowgWorldCandidateValidationTest.kt`. That candidate now records the structured airport package as its immediate upstream source document and projects a worked low-level LOWG airspace set (`LO585`, `LO80C_D`, `LO0EF_E`, `LODDA_E`, `LOCB1_E`, `LO59D_E`) instead of the earlier synthetic point-claim CTR volume.
  - The JSON schema for that LOWG current-core candidate now also lives in shared migration code under `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateSchema.kt`, rather than being duplicated privately inside the validator test.
  - The current-core validation result is clean again. `cad/airports/rendered/lowg/world-validation-report.json` now reports `0` issues and `0` structural issues.
  - That clean pass is now conditional on a worked low-level 2D point-to-volume assignment rather than the old synthetic point-claim CTR volume. The remaining real gaps are broader surrounding-airspace projection, altitude-aware airspace membership, and deferred hold/loiter modelling rather than parking connectivity.
  - The richer entity bundle still keeps the hand-authored/disconnected holding candidates on `B`, `C`, `Y`, and `Z` as candidate-only data rather than pretending that the current validator can consume them honestly as-is.
  - The generated plate-pack summary now reports that clean validator result directly, and the page set is no longer split between entity-driven aerodrome sheets and supplement-driven procedure sheets. Everything in the pack now comes through the entity-only plate view model, with gap notices where the projection is still missing content.
  - `PRC-1`, `PRC-2`, `PRC-3`, `PRC-4`, `PRC-5`, and `ARR-1` now render the runtime-projected published-procedure entities where that data exists, alongside the route / sector / circuit structures they depend on. The remaining gaps are now chiefly route-airspace membership and deferred hold semantics, not whether the package carries the published procedure semantics at all.
  - `ENR-1` now reports only the currently projected airspace / route coverage plus explicit gap notices; published briefing/protocol notes no longer leak in from the sidecar.
  - The map can now show `apt.dat` taxi signs as a separate overlay, which makes X-Plane taxiway naming evidence visible while assigning the hand-authored components.
  - Marker, label, and stroke sizing is now adjusted against zoom level so the authoring overlays remain readable when moving between airport and airspace views.
  - LOWG controlled airspace and nearby special-use airspace are overlaid from OFMX boundary vertices.
  - LOWG 2 contains OFMX `FNT` boundary vertices; that boundary is currently shown as a straight-segment approximation rather than a true curved reconstruction.
- An ad hoc airspace DXF export now exists:
  - `bin/export_airspace_dxf.py cad/airports/lowg.manifest.json`
  - `cad/airports/lowg_airspace_working.dxf`
  - This DXF is a working LibreCAD artifact, not a semantic source of truth.
  - It currently exports OFMX airspace boundaries as straight `LINE` segments plus VFR reporting points, runway context, and tower context.
  - `FNT` boundary vertices are therefore still flattened to straight segments in the export.
- A normalization pass now exists for edited airspace DXFs:
  - `bin/normalize_airspace_dxf.py cad/airports/lowg.manifest.json cad/airports/lowg_airspace_working.dxf`
  - `cad/airports/lowg_airspace_working_normalized.dxf`
  - The current fit is effectively exact from the labeled VFR reporting points: scale `~1.0`, rotation `~0`, translation `(528.58, -12141.69)`.
  - The user-added `VFR Op Sectors` layer is preserved in the normalized file.
  - The interactive map now renders that `VFR Op Sectors` layer as a provisional overlay when the normalized file is present.
- A bootstrapping X-Plane baseline DXF now exists:
  - `bin/export_xplane_baseline_dxf.py cad/airports/lowg.manifest.json`
  - `cad/airports/lowg_xplane_baseline.dxf`
  - This DXF is generated directly from `apt.dat` plus OFMX VFR reporting points and does not depend on the existing hand-authored DXFs.
  - It contains layers for physical runways, X-Plane taxi-route edges, taxi nodes, taxi signs, visible parking positions, inferred `A/*` parking branches, the tower, and OFMX VFR points.
  - This is probably the cleaner general workflow for future airports: generate the baseline DXF first, author geometry on top of it in LibreCAD, then translate the result back into the structured airport package.
- A merged working DXF now exists:
  - `bin/export_airport_working_dxf.py cad/airports/lowg.manifest.json`
  - `cad/airports/lowg_working_combined.dxf`
  - This file keeps the X-Plane / OFMX baseline as reference layers and adds the transformed hand-authored ground, VFR circuit, procedure anchors, and `VFR Op Sectors` on top in the shared local frame.
  - That gives LOWG a practical “augment the baseline, do not throw away the migration work” editing surface for the next iteration.
- An OSM underlay bridge now exists:
  - `bin/export_osm_geojson_dxf.py cad/airports/lowg.manifest.json <osm.geojson>`
  - `cad/airports/lowg_osm_underlay.dxf` by default
  - This projects WGS84 OSM GeoJSON into the same airport-local DXF frame used by the other tooling.
  - The intended use is to source the GeoJSON externally, then import it as a visual/context underlay for roads, rail, water, settlement edges, and similar ground-reference features.
- Both DXFs currently expose only `LINE` and `POINT` entities on a single geometry layer, with no semantic labels embedded in the drawing. Sidecar data is therefore mandatory, not optional.
- Runway-end source agreement is mixed:
  - 34C agrees between `apt.dat`, OFMX, and CIFP to within sub-metre noise.
  - 16C differs by about `258.5m` between the `apt.dat` strip end and the OFMX/CIFP threshold position.
  - 16R/34L differs by about `150m` per end between `apt.dat` and OFMX, consistent with displaced-threshold effects.
- The checked-in OFMX designated-point inventory is not enough to anchor LOWG IFR content by itself. In the current lightweight scan, only `GOTAR` appears among the `46` distinct LOWG CIFP identifiers.

## Procedure inventory

### Ground / aerodrome

- Runway strip geometry
- Taxiway centreline graph
- Holding points
- Apron links
- Stands

Status:

- `lowg.dxf` already contains useful ground geometry.
- Ground alignment to X-Plane is promising enough to proceed.
- The `16/34L` glider-use area should stay explicitly marked as a known X-Plane divergence until semantics are attached.

### VFR circuits

Target interpretation from current understanding:

- Three runway-covering spines should exist.
- The 16L and 16R procedures crosswind earlier than the two centre-runway procedures.
- 16L and 16R join the shared downwind and shared base later than the centre-runway procedures.
- 16L and 16R turn final earlier because of runway position.

Status:

- `lowg_circuits.dxf` is the VFR-circuit-only drawing.
- The centre-runway structure is visibly present.
- Side procedures appear to attach into the shared circuit graph rather than forming independent isolated loops.
- More authoring is still needed before all intended procedures are encoded cleanly.
- The current runtime/FM model is misaligned with the intended holding/orbit semantics. It presently encodes these as closed paths, but the intended model is a loiter-capable sky region with horizontal extent and altitude.
- Do not treat the current closed-loop encoding as the target LOWG authoring convention. Keep the intended holding shapes and attachment points explicit, and treat any interim path-loop representation as provisional until the model is corrected.

### VFR routes beyond the circuit

- Reporting-point connectivity
- Arrival / departure VFR routes
- Any local joins that are not part of the immediate circuit pattern

Status:

- Not yet authored in CAD as standalone route drawings.
- For the reporting-point portions, the geometry is just the literal joins between the existing mapped VFR reporting points.
- User-confirmed route paths are now captured in the sidecar as explicit ordered path definitions:
  - `GLEISDORF -> LASSNITZHÖHE -> AUTOBAHN-OST -> circuit (NE entry)`
  - `SENDER DOBL -> AUTOBAHN-WEST -> GREEN CITY -> GRAZ-NORD`
  - `SENDER DOBL -> circuit (SW entry)` as the alternate western circuit-join option, depending on runway direction
  - `KALSDORF -> circuit (SE entry)`
- Published OFM entry/exit protocol is now scaffolded separately from raw route geometry in `publishedVfrProcedures`.
- This distinction matters: the same named points can participate in a geometric route and also in a published arrival/departure protocol with contact-before, altitude-cap, hold, comm-failure, and reporting expectations.
- `LASSNITZHÖHE` is also the CTR entry; the route should remain at or below `3000 ft` before joining.
- For simplicity, `AUTOBAHN-WEST` is treated as the NW circuit point.
- The current tooling now renders the resolved reporting-point path segments directly from the sidecar, including the explicit NE/SW circuit anchors and the SE endpoint-based join.
- There is a separate airspace-model gap on the west/east arrivals: `SECTOR WHISKEY` and `SECTOR ECHO` are boundary-sector constructs, and the charts show triangular cut-outs in the CTR so `SENDER DOBL` and `KALSDORF` sit on the CTR intersection rather than simply inside controlled airspace. The current working DXF sketch for those sectors is now treated as user-confirmed geometry and anchors, and the structured package now carries them as candidate operational-sector objects, but the runtime/model still does not consume them as first-class airspace geometry.
- PRC-4 west traffic circuit, PRC-5 east traffic / hold, and ARR-1 Gleisdorf arrival/NORDO references are now scaffolded from the OFM pack, alongside AD-1 taxi-layout reference context, AD-3/4/5/6 operational notes, and AD-2 apron/parking content backed by X-Plane stand inventory for version 1.

### IFR procedures

- SIDs
- STARs
- Instrument approaches
- Missed approaches
- Holds where needed

Status:

- Procedure content exists in CIFP.
- Geometry is not yet authored / reconciled into the local world graph.
- A further nav source is still needed for most fix positions, or they must be anchored manually.

### Airspace / operational metadata

- CTR / TMA / FIR metadata
- Frequencies
- Controller roles / units
- Local runway-use / circuit-hand notes

Status:

- OFMX already provides a large part of this.
- This should be attached after the geometry graph is stable enough.

## Ordered next steps

1. Keep `bin/airport_authoring_report.py cad/airports/lowg.manifest.json` as the repeatable LOWG status check after each authoring cycle.
2. Treat the current LOWG package and current-core candidate as the version-1 baseline: routes, sectors, published procedures, directional circuits, parking, and the worked low-level CTR/TMA slice are now in.
3. Keep the plate generator strict and entity-first through the plate view model. Missing content should show up as real gap pages, not supplement leakage.
4. Extend the LOWG worked airspace slice beyond the current low-level CTR/TMA subset so more route-airspace profiles can be assigned honestly without fabricating authority.
5. Keep the `16/34L` glider-use area explicitly annotated as a known X-Plane divergence in the manifest/report/render outputs.
6. Defer the east non-standard hold to version 2 until the loiter / hold model is corrected.
7. Decide how much broader surrounding airspace should be projected into the LOWG current-core candidate beyond the current low-level CTR/TMA subset and the operational sectors.
8. Continue CAD authoring only for genuinely missing local geometry:
   - additional controller-training routes
   - directional circuit refinements
   - future version-2 hold geometry
9. Decide the next IFR fix-position source beyond the current OFMX designated-point scan.
10. Continue tightening the generated LOWG plate pack until it is the primary review artifact for the airport package.

## Open questions

- How should glider-use semantics for the `16/34L` area and taxiway link be represented in the eventual ground model?
- Which named VFR routes outside the immediate circuit need explicit geometry first?
- What is the minimum importer manifest needed to keep future airport packages consistent?
- Which source should provide IFR fix and navaid positions once the geometry work reaches that stage?
