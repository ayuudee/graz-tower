# LJMB Authoring Workboard

This file tracks the bootstrap and authoring state for LJMB.

## Current package

- Manifest: [ljmb.manifest.json](cad/airports/ljmb.manifest.json)
- Baseline DXF target: [ljmb_xplane_baseline.dxf](cad/airports/ljmb_xplane_baseline.dxf)
- Airspace reference DXF: [ljmb_airspace_working.dxf](cad/airports/ljmb_airspace_working.dxf)
- Combined working DXF: [ljmb_working_combined.dxf](cad/airports/ljmb_working_combined.dxf)
- Raster underlay:
  - [ljmb_raster_underlay.png](cad/airports/ljmb_raster_underlay.png)
  - [ljmb_raster_underlay_placement.json](cad/airports/ljmb_raster_underlay_placement.json)
- OSM cache: [ljmb_osm_underlay.geojson](cad/airports/ljmb_osm_underlay.geojson)
- OSM DXF reference: [ljmb_osm_underlay.dxf](cad/airports/ljmb_osm_underlay.dxf)

## Local source inventory

- `apt.dat` sample: [LJMB.dat](migration/src/commonTest/resources/airports/LJMB.dat)
- Jepp trip kit: [LJMB.pdf](data/charts/LJMB/LJMB.pdf)
- OFMX bundle: [ofmx_lj.zip](data/charts/LJMB/ofmx_lj.zip)
- Stable extracted OFMX path: [ofmx_lj.ofmx](data/ofm/slovenia/ofmx_extracted/ofmx_lj/isolated/ofmx_lj.ofmx)
- ARINC bundle: [arinc_lj.zip](data/charts/LJMB/arinc_lj.zip)
- OpenAir bundle: [openair_lj.zip](data/charts/LJMB/openair_lj.zip)
- CUP bundle: [cup_slovenia.zip](data/charts/LJMB/cup_slovenia.zip)
- Raster/tile underlays:
  - [slippyTiles_clipped.zip](data/charts/LJMB/slippyTiles_clipped.zip)
  - [ljla_256.mbtiles](data/charts/LJMB/ljla_256.mbtiles)

## What we know already

- LJMB has one main paved runway pair `14/32`, plus taxiway graph, stands, tower viewpoint, and frequencies in the checked-in `apt.dat` sample.
- The Jepp trip kit includes:
  - airport information
  - parking positions
  - STAR pages
  - SID pages
  - at least one full instrument approach (`ILS RWY 32`)
  - VFR / airspace briefing pages
  - the actual LJMB VFR/circuit source is the Jepp `19-1` terminal VFR chart, not the circling plate
    - that page depicts the CTR/TMA reporting-point routes, two traffic circuits (left and right), the `MN1`-`MN2` connector line, and the local training/holding areas
    - the Jepp `16-1` page is the `Lctr RWY 32` circling / visual-approach chart and should not be used as the primary source for circuit geometry
- The broader OFMX designated-point set is richer than the airport-associated LJMB subset:
  - it already resolves outer VFR points like `GOLVA`, `MUREG`, `OBUTI`, `NIDLO`, `TISKO`, `DIMLO`, `MS3`, `ME4`, `MIRSO`, and `IRLIX`
  - the Jepp VFR briefing explicitly names TMA entry points `GOLVA`, `MUREG`, `PETOV`, `OBUTI`, `NIDLO`, `TISKO`, `DIMLO`, `MS2`, `MS3`, `ME3`, `ME4`, `MW1`
  - `PETOV` is currently missing from the checked-in OFMX designated-point slice, so the first LJMB VFR procedure pass will need either another source for that point or an explicit local override
  - the first structured LJMB VFR/protocol bootstrap now lands in the package:
    - `22` direct fix entities covering the resolved outer TMA entry points plus inner CTR reporting points
    - `2` published VFR procedures from Jepp `19-2`:
      - `ljmb_tma_entry_general`
      - `ljmb_ctr_entry_general`
    - `PETOV` is now kept explicit as an unresolved published point in the TMA-entry procedure instead of disappearing from the projection
- OFMX Slovenia data now exists in the stable repo data layout used by the migration tools.
- The currently selected isolated `ofmx_lj.ofmx` source still resolves VFR reporting points and airport metadata, but the present parser path yields `0` LJMB airspace records.
- OpenAir is now the first structured airspace fallback for LJMB:
  - [openair_lj.zip](data/charts/LJMB/openair_lj.zip) is now parsed directly for nearby CTR/TMA/RMZ geometry.
  - [ljmb_airspace_working.dxf](cad/airports/ljmb_airspace_working.dxf) now renders those OpenAir-backed boundaries instead of only runway/tower/VFR-point reference.
  - The current structured package now carries `9` candidate airspace volumes:
    - `CTR MARIBOR`
    - `TMA MARIBOR 1`
    - `TMA MARIBOR 2` lower slice
    - `TMA MARIBOR 2` upper slice
    - `TMA MURA`
    - `TMA DOLSKO 1` lower slice
    - `TMA DOLSKO 1` upper slice
    - `RMZ PTUJ`
    - `RMZ SLOVENSKE KONJICE`
  - `TMA MURA` and `TMA DOLSKO 1` are currently included as adjacent context from the OpenAir relevance filter; they may need pruning once the LJMB VFR slice is authored more tightly.
  - the current-core candidate now projects those worked low-level volumes plus an explicit `LJLA_OPEN_FIR_G` fallback for uncovered points, rather than reusing stale LOWG FIR metadata.
- The current combined working DXF now includes an OSM-derived raster base layer, following the same workflow as LOWG.
- The current OSM raster is cropped around the airport footprint with a 4 km margin and renders at about 2.3 m/px, which is now much closer to the LOWG underlay scale.
- The runway baseline and OSM raster now appear to be in the right place. The remaining visual mismatch came from sparse OSM airport feature coverage rather than bad raster placement.
- ARINC/OpenAir/CUP data exists locally, but the current migration pipeline does not yet parse those formats.
- The current working DXF is no longer just a bootstrap/reference file. It now contains the first authored LJMB ground graph:
  - `REF_ROUTE_RUNWAYS` carries the runway spine reference.
  - `NEW_Taxi` currently contains `17` authored taxi/apron line segments.
  - `NEW_Parking` currently contains `7` authored stand-point entities.
  - `NEW_Holding` currently contains `3` authored hold-point entities.
  - `New_Mano` currently contains `8` authored manoeuvring-area line segments around the small-runway ends.
  - All `7 / 7` parking points lie exactly on the authored `NEW_Taxi` graph.
  - All `3 / 3` authored holding points lie exactly on the authored `NEW_Taxi` graph.
  - The authored taxi graph already touches the runway reference at three exact attachment points.
  - `REF_ROUTE_RUNWAYS` is now treated as authored runway-access support geometry in the migration import, not as dead reference-only overlay. That layer is what closes the full ground graph under the current model.
  - One authored taxi junction is a point-on-segment join rather than an endpoint-to-endpoint join, so the compiler must split internal intersections and not only exact endpoint joins.
  - The three hold points currently sit on the three authored runway-entry spurs:
    - one on the west-side main-runway entry line
    - one on the south-east main-runway entry line
    - one on the small-runway entry line
  - The original `apt.dat` taxi-route naming appears usable as reference, but it is segment-level and mixed rather than a clean one-to-one taxiway labelling. In particular, the authored graph aligns against a mix of original `A`, `C/A`, `C/B`, and `D/B` route segments, so the final taxiway naming should be carried explicitly in authoring metadata rather than inferred only from nearest-edge matching.
  - A first structured package and entity bundle now build from the authored ground graph:
    - [structured-airport-package.json](cad/airports/rendered/ljmb/structured-airport-package.json)
    - [entity-bundle.json](cad/airports/rendered/ljmb/entity-bundle.json)
  - The current direct-fit LJMB package now includes:
    - `39` authored/support taxiway path entities
    - `7` authored stands
    - `4` holding-point candidates (the single small-runway hold is duplicated onto both runway directions for version 1)
    - `3` apron-style entities, including two manoeuvring-area perimeter components from `New_Mano`
    - `9` candidate OpenAir-backed airspace volumes
  - Stand names are still provisional because they are inferred from the nearest `apt.dat` parking references. They are good enough for the first package, but they are not yet authoritative authored stand names.
- `New_Mano` is currently projected as apron-style perimeter geometry because the current core model has no dedicated manoeuvring-area primitive.
- A first current-core candidate world now also builds:
  - [world-candidate.json](cad/airports/rendered/ljmb/world-candidate.json)
  - [world-validation-report.json](cad/airports/rendered/ljmb/world-validation-report.json)
  - Current LJMB subset:
    - `4` runways
    - `39` taxiways
    - `7` stands
    - `3` aprons
    - `7` runtime-usable airspace volumes
  - `RMZ PTUJ` and `RMZ SLOVENSKE KONJICE` stay boundary-only in the richer package and are not projected into the current-core subset.
  - LJMB now has a JVM current-core validation harness alongside LOWG:
    - [LjmbWorldCandidateValidationTest.kt](migration/src/jvmTest/kotlin/xyz/easiersaid/twr/migration/world/LjmbWorldCandidateValidationTest.kt)
    - the current candidate validates cleanly with `issueCount = 0` and `structuralIssueCount = 0`
- The authoring map now renders for LJMB as well:
  - [index.html](cad/airports/rendered/ljmb/index.html)
  - [ljmb_ground_overlay.svg](cad/airports/rendered/ljmb/ljmb_ground_overlay.svg)
  - [ljmb_ground_divergence_zoom.svg](cad/airports/rendered/ljmb/ljmb_ground_divergence_zoom.svg)
  - [ljmb_vfr_circuit_overlay.svg](cad/airports/rendered/ljmb/ljmb_vfr_circuit_overlay.svg)
  - The main LJMB HTML render is now treated as a candidate/publish-stage view:
    - it prefers `workingDxf` authored layers (`NEW_Taxi`, `NEW_Parking`, `NEW_Holding`, `New_Mano`) when no separate `drawings` are present
    - it suppresses source/reference overlays like X-Plane parking-access helpers and apt taxi-reference layers from the main page
    - the source/debug comparison surface is now conceptually separate from the main candidate preview

## Bootstrap decision

The first LJMB slice should follow the LOWG bootstrap pattern, but stop earlier:

1. normalize the OFMX source into `data/ofm/slovenia/...`
2. generate a baseline DXF from `apt.dat` + OFMX VFR points
3. generate a standalone editable airspace/reference DXF from `apt.dat` + OFMX
4. generate a reference-only combined working DXF before any authored drawings exist
5. generate an OSM-derived raster base layer
6. treat Jepp and the OSM raster as the initial authoring/reference substrate
7. wait on full IFR runtime projection until we either:
   - add ARINC support, or
   - choose a chart-driven/manual migration path for LJMB IFR

Current state:

- step 1 is done
- step 2 is done
- step 3 is done
- step 4 is done
- step 5 is done
- step 6 is done — the first authored `NEW_*` ground layers, circuit geometry, and OpenAir-backed airspace import are all in place

## Current world candidate state (2026-04-24)

The LJMB world candidate is now **integration-ready for multi-aerodrome runtime testing**. World-candidate counts:

- `4` runways (14, 14L, 32, 32R)
- `4` traffic circuits — main 14/32 (both directions) + glider 14L/32R (both directions), altitude 1876 ft
- `39` taxiways
- `7` stands
- `3` aprons
- `9` runtime-usable SIDs — X-Plane CIFP-ingested (`DIML1S`, `GOLV1S`, `GOLV2G`, `MURE1S`, `PETO1S`, `PETO2B`, `PETO5D`, `VALU1S`, `VALU4L`). Remaining `11` CIFP SIDs (-1J/-1N/-2G/-3H variants) carry fixless VI legs and stay in the IFR inventory only.
- `0` runtime STARs, `0` runtime approaches, `0` runtime holding patterns — deferred pending missed-approach hold-loop compilation (LOWG handles this with a GBG-specific code path; an airport-agnostic compiler is a later phase). The full inventory (`9` STARs, `4` approaches including ILS 32 / LOC 32 / RNP LPV / RNP LNAV/VNAV, `2` holding-pattern candidates at `MR` and `ERROW`) is in the structured-airport-package candidate section with provenance.
- `41` fixes — 22 local reporting points + 12 TMA entry points + 7 procedure-internal fixes. `PETOV` now resolves from the X-Plane earth_fix.dat cache.
- `2` authored VFR routes — `ljmb_mn_corridor_inbound` (MN1-MN2) and `ljmb_mw_corridor_inbound` (MW1-LAPNA), both from Jepp 19-1.
- `2` authored operational sectors (MARIBOR APPROACH, MARIBOR TOWER) — present in the candidate layer only; runtime projection deferred until explicit boundary geometry is authored (LOWG derives sector geometry from a working-airspace-sector DXF; LJMB does not yet have that).
- `8` candidate airspace volumes (CTR MARIBOR, TMA MARIBOR 1, TMA MARIBOR 2 lower+upper, TMA MURA, TMA DOLSKO 1 lower+upper, plus explicit LJLA open-FIR fallback).
- `2` published VFR procedures (`ljmb_tma_entry_general`, `ljmb_ctr_entry_general`).
- `0` structural validation issues.

## New data sources wired in (2026-04-24)

- `data/cifp/LJMB.dat` — X-Plane 12 CIFP copy for LJMB. Same format as `data/cifp/LOWG.dat`.
- `data/airac_cache/LJMB_fixes.dat`, `data/airac_cache/LJMB_navaids.dat` — per-airport subsets of X-Plane's `earth_fix.dat` / `earth_nav.dat`, produced by `bin/extract_xplane_airport_cache.py`. Resolves all CIFP-referenced terminal fixes (MB141, MB142, MB324, CUJUF, DER32, and more) plus the Maribor NDB (`MR`, 334 kHz).
- The pipeline's `cifp_fix_resolution` now unions X-Plane fixes/navaids alongside OFMX designated points, chart coding tables, and derived approach geometry. LOWG output is unchanged because LOWG doesn't declare the `xplaneFixes`/`xplaneNavaids` sources.
- `LJMB` minima policy entry added: ILS RWY 32 DA 1056' (H 200'), from Jepp 11-1.

## Integration surface

- `migration/src/commonMain/.../world/WorldCandidateLoader.kt` — extracted the `WorldCandidateDocument → AviationWorld` converter out of the duplicated LOWG/LJMB test files into a single production-grade library. Adds `mergeAviationWorlds(...)` for multi-aerodrome orchestration (enroute fix collisions resolved first-wins; aerodrome/point/airspace collisions are hard errors).
- `migration/src/jvmTest/.../MultiAerodromeLoaderTest.kt` — real-job integration test that loads both LOWG and LJMB world candidates, merges them, and validates the merged world.

## Known deferrals (not blocking integration)

The deferrals are tracked as `M*` items in `.plan` under "Migration pipeline deferrals
(LJMB and multi-aerodrome)". Quick summary, ordered by impact on multi-aerodrome runtime
parity:

- **M2** — Missed-approach hold-loop compiler is hardcoded to `LOWG_GBG_MISSED_HOLD`.
  LJMB has `LJMB_MR_MISSED_HOLD` and `LJMB_ERROW_MISSED_HOLD` candidates ready, but the
  world-candidate runtime stays at 0 holds / 0 approaches / 0 STARs until that compiler
  becomes airport-agnostic. **Highest-impact deferral**: unblocks the full LJMB IFR
  runtime in one bounded refactor.
- **M1** — 11 of 20 LJMB SIDs carry CIFP `VI`/`CA` legs without fix identifiers; the
  current waypoint-route model can't represent them, so they stay in the IFR inventory but
  not the runtime. Affects RNAV-heavy procedure variants for LJMB and likely LOWG too.
- **M3** — LJMB operational sectors (`MARIBOR APPROACH`, `MARIBOR TOWER`) are in the
  candidate layer but get filtered from the runtime AIP because they have no boundary
  geometry. Either an LJMB sector-only DXF needs authoring or the pipeline needs a mode
  that derives boundary from a referenced airspace volume.
- **M5** — VFR routes have `airspaceProfile: null` because the LOWG-specific route→airspace
  projector doesn't know about LJMB volume IDs. The two routes still work geometrically;
  they just lack the airspace classification metadata.
- **M4** — LJMB plates render but PRC-1/2/3 and ARR-1 still show LOWG procedure IDs as
  "not projected". Cosmetic; needs a manifest-declared plate list to clean up.
- **M6** — Multi-aerodrome merging uses first-wins for shared enroute fixes (GOLVA,
  DIMLO, etc.). Geometrically correct (X-Plane-sourced lat/lons match) but the pointId
  references muddle provenance.
- **M7** + **M8** — Sim runtime still reads `aerodromes.values.first()`, and there's no
  shippable launcher; both are needed before LJMB participates in real (not test-harness)
  multi-aerodrome scenarios.
- **M9** — Pre-existing 12-line text drift in `cad/airports/rendered/lowg/world-
  candidate.json` (`forcedAssumptions`/`omittedFeatures`). Not introduced by this session;
  noted for cleanup.

For full per-item descriptions, unlock conditions, and effort/impact grades, see `.plan`
in the repo root.
