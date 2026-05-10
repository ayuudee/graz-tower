# LJMB Sources

## Current local package

- Jepp trip kit: [data/charts/LJMB/LJMB.pdf](data/charts/LJMB/LJMB.pdf)
- OFMX bundle: [data/charts/LJMB/ofmx_lj.zip](data/charts/LJMB/ofmx_lj.zip)
- Stable extracted OFMX path: [data/ofm/slovenia/ofmx_extracted/ofmx_lj/isolated/ofmx_lj.ofmx](data/ofm/slovenia/ofmx_extracted/ofmx_lj/isolated/ofmx_lj.ofmx)
- ARINC bundle: [data/charts/LJMB/arinc_lj.zip](data/charts/LJMB/arinc_lj.zip)
- OpenAir bundle: [data/charts/LJMB/openair_lj.zip](data/charts/LJMB/openair_lj.zip)
- CUP bundle: [data/charts/LJMB/cup_slovenia.zip](data/charts/LJMB/cup_slovenia.zip)
- Raster/tile underlays:
  - [data/charts/LJMB/slippyTiles_clipped.zip](data/charts/LJMB/slippyTiles_clipped.zip)
  - [data/charts/LJMB/ljla_256.mbtiles](data/charts/LJMB/ljla_256.mbtiles)
- `apt.dat` sample: [migration/src/commonTest/resources/airports/LJMB.dat](migration/src/commonTest/resources/airports/LJMB.dat)

## What each source gives us

- `apt.dat`
  - runway pair `14/32`
  - taxi graph
  - stands
  - tower viewpoint
  - frequencies
  - service-vehicle data
- Jepp trip kit
  - airport information
  - parking positions
  - STAR pages
  - SID pages
  - `ILS RWY 32`
  - VFR/airspace briefing pages
  - the LJMB VFR/circuit truth lives on the Jepp `19-1` terminal VFR chart, not the circling plate
    - that page depicts the CTR/TMA entry routes, two traffic circuits (`left` and `right`), the `MN1`-`MN2` connector, and the local training/holding areas
  - the Jepp `16-1` `Lctr RWY 32` page is the circling / visual-approach chart and should not be treated as the primary circuit source
- OFMX bundle
  - airport metadata
  - airspace
  - frequencies
  - designated points
  - runways
- ARINC bundle
  - likely IFR procedure truth in ARINC form
  - not yet consumable by the current migration pipeline
- OpenAir / CUP
  - alternative airspace / waypoint packaging
  - OpenAir is now part of the current LJMB airspace bootstrap path
  - CUP is not yet part of the current migration pipeline

## Current migration fit

- LJMB is ready for the same **baseline DXF bootstrap** used on LOWG.
- That bootstrap now exists as:
  - [cad/airports/ljmb_xplane_baseline.dxf](cad/airports/ljmb_xplane_baseline.dxf)
  - [cad/airports/ljmb_airspace_working.dxf](cad/airports/ljmb_airspace_working.dxf)
  - [cad/airports/ljmb_working_combined.dxf](cad/airports/ljmb_working_combined.dxf)
  - [cad/airports/ljmb_raster_underlay.png](cad/airports/ljmb_raster_underlay.png)
  - [cad/airports/ljmb_raster_underlay_placement.json](cad/airports/ljmb_raster_underlay_placement.json)
  - [cad/airports/ljmb_osm_underlay.geojson](cad/airports/ljmb_osm_underlay.geojson)
  - [cad/airports/ljmb_osm_underlay.dxf](cad/airports/ljmb_osm_underlay.dxf)
- The combined working DXF now works before any authored LJMB drawings exist. It carries apt.dat baseline geometry, taxi/parking reference, VFR reporting points, and an OSM-derived raster underlay.
- The combined working DXF is now also the first authored LJMB source surface:
  - `REF_ROUTE_RUNWAYS` carries the runway reference spine
  - `NEW_Taxi` carries the authored taxi/apron graph
  - `NEW_Parking` carries authored stand-point geometry
  - `NEW_Holding` carries authored holding points
  - `New_Mano` carries authored manoeuvring-area geometry around the small-runway ends
- The structured-package/entity-bundle path now consumes those authored ground layers directly:
  - [cad/airports/rendered/ljmb/structured-airport-package.json](cad/airports/rendered/ljmb/structured-airport-package.json)
  - [cad/airports/rendered/ljmb/entity-bundle.json](cad/airports/rendered/ljmb/entity-bundle.json)
- LJMB now follows the same OSM-derived raster workflow as LOWG.
- The LJMB OSM raster is now cropped around the airport footprint with a 4 km margin instead of using the full projected OSM feature extents, bringing it much closer to the LOWG underlay scale.
- The runway baseline and OSM raster placement now look broadly correct. The main visual limitation is that the OSM fetch does not include full airport aeroway geometry, so the underlay mostly contributes terminal/hangar context rather than a full mapped runway/taxi truth layer.
- The first authored LJMB graph is structurally usable already:
  - `NEW_Taxi` contains `17` line segments
  - `NEW_Parking` contains `7` stand points
  - `NEW_Holding` contains `3` hold points
  - all `7 / 7` stand points lie exactly on the authored taxi graph
  - all `3 / 3` authored holding points lie exactly on the authored taxi graph
  - the authored taxi graph touches the runway reference at three exact attachment points
  - one internal junction is a point-on-segment join, so future graph import must split internal intersections rather than relying only on endpoint joins
  - the original X-Plane taxi-route naming is available as reference, but it is segment-level and mixed, so final taxiway naming should be authored explicitly rather than inferred only from nearest-edge matching
  - `REF_ROUTE_RUNWAYS` is now treated as authored runway-access support geometry in the migration import, not as dead reference-only overlay
  - the current direct-fit LJMB package now contains `39` taxiway path entities, `7` stands, `4` holding-point candidates, and `3` apron-style entities
  - the single authored small-runway holding point is duplicated onto both runway directions in the version-1 package because the current model still expects runway-direction-specific holding references
  - stand names are still provisional because they are inferred from the nearest `apt.dat` parking references rather than explicitly authored
  - `New_Mano` is currently represented as apron-style perimeter geometry because the current core model has no dedicated manoeuvring-area primitive
- The current LJMB OFMX selection still resolves VFR reporting points and airport metadata, but the present parser path yields `0` LJMB airspace records.
- OpenAir now supplies the first structured LJMB airspace candidate set:
  - [cad/airports/ljmb_airspace_working.dxf](cad/airports/ljmb_airspace_working.dxf) now renders OpenAir-backed airspace boundaries
  - [cad/airports/rendered/ljmb/structured-airport-package.json](cad/airports/rendered/ljmb/structured-airport-package.json) now carries `9` candidate airspace volumes:
    - `CTR MARIBOR`
    - `TMA MARIBOR 1`
    - `TMA MARIBOR 2` lower/upper slices
    - `TMA MURA`
    - `TMA DOLSKO 1` lower/upper slices
    - `RMZ PTUJ`
    - `RMZ SLOVENSKE KONJICE`
  - `TMA MURA` and `TMA DOLSKO 1` are currently included as adjacent context from the OpenAir relevance filter; they may need pruning when the LJMB VFR slice is tightened.
- The full OFMX designated-point set is also useful beyond the airport-associated LJMB points:
  - it resolves outer VFR points like `GOLVA`, `MUREG`, `OBUTI`, `NIDLO`, `TISKO`, `DIMLO`, `MS3`, `ME4`, `MIRSO`, and `IRLIX`
  - the Jepp VFR briefing names TMA entry points `GOLVA`, `MUREG`, `PETOV`, `OBUTI`, `NIDLO`, `TISKO`, `DIMLO`, `MS2`, `MS3`, `ME3`, `ME4`, `MW1`
  - `PETOV` is currently missing from the checked-in OFMX designated-point slice, so LJMB VFR procedure authoring will need another source or an explicit local override for that point
  - the first published VFR bootstrap now projects into the candidate world as two Jepp-backed entry procedures:
    - `ljmb_tma_entry_general`
    - `ljmb_ctr_entry_general`
  - `PETOV` is kept explicit as an unresolved published point in the TMA-entry procedure rather than disappearing from the projection
- LJMB now also has a first current-core subset at [cad/airports/rendered/ljmb/world-candidate.json](cad/airports/rendered/ljmb/world-candidate.json):
  - `4` runways
  - `39` taxiways
  - `7` stands
  - `3` aprons
  - `8` runtime-usable airspace volumes, including explicit `LJLA_OPEN_FIR_G` fallback coverage for uncovered points
  - the two RMZ records remain boundary-only and stay out of the current-core subset
  - [cad/airports/rendered/ljmb/world-validation-report.json](cad/airports/rendered/ljmb/world-validation-report.json) is now generated by [LjmbWorldCandidateValidationTest.kt](migration/src/jvmTest/kotlin/xyz/easiersaid/twr/migration/world/LjmbWorldCandidateValidationTest.kt) and is currently clean (`issueCount = 0`, `structuralIssueCount = 0`)
- The LJMB authoring/render surface now exists too:
  - [cad/airports/rendered/ljmb/index.html](cad/airports/rendered/ljmb/index.html)
  - The main LJMB HTML render now acts as a candidate/publish-stage view:
    - it reads authored `workingDxf` layers directly when no separate DXF `drawings` are configured
    - it suppresses provisional X-Plane parking/access and taxi-reference overlays from the main page
    - reference/debug overlays are now conceptually separate from the candidate preview
- LJMB is **not yet** ready for LOWG-style IFR runtime projection from existing repo tooling alone, because we do not currently parse the local ARINC/OpenAir/CUP bundles.
- The right first slice is:
  - normalize OFMX into a stable `data/ofm/` path
  - generate a baseline DXF
  - generate an editable OFMX/reference DXF
  - generate a reference-only combined working DXF
  - start ground/VFR authoring
  - import the first local CTR/TMA candidate set from OpenAir
  - defer IFR projection policy until after that bootstrap

## X-Plane 12 sources (added 2026-04-24)

X-Plane 12 ships a complete CIFP-style terminal procedure dataset for LJMB and the global
fix/navaid catalogue needed to resolve all CIFP fix references. The migration pipeline now
consumes this data via:

- `data/cifp/LJMB.dat` — direct copy of `/home/xplane/X-Plane 12/Resources/default
  data/CIFP/LJMB.dat`. 20 SIDs, 9 STARs, 4 approaches (`I32` ILS, `Q32` LOC, `R32-Y` RNP
  LPV, `R32-Z` RNP LNAV/VNAV), 2 RWY records, 2 PRDAT records.
- `data/airac_cache/LJMB_fixes.dat` and `data/airac_cache/LJMB_navaids.dat` — per-airport
  subsets of X-Plane's `earth_fix.dat` and `earth_nav.dat`, produced by
  `bin/extract_xplane_airport_cache.py`. Resolves all CIFP-referenced terminal fixes
  (`MB141`, `MB142`, `MB324`, `CUJUF`, `DER32`, etc.) plus the Maribor NDB `MR` (334 kHz)
  and the runway 32 ILS / GS / DME-ILS / LPV / OM / MM. Also resolves `PETOV` for the VFR
  publication, replacing the previous "literal unresolved" placeholder.

The pipeline change in `bin/airport_authoring_report.py` and
`bin/airport_structured_package.py` unions X-Plane's fix and navaid caches into
`cifp_fix_resolution` alongside OFMX designated points and chart coding tables. LOWG
output is unchanged because LOWG's manifest does not declare the new `xplaneFixes` and
`xplaneNavaids` source paths.

Regenerating the per-airport caches when X-Plane updates its AIRAC cycle:

```
python bin/extract_xplane_airport_cache.py LJMB \
    --xplane-root '/home/xplane/X-Plane 12' \
    --output-dir data/airac_cache
```

## What still doesn't ingest from local sources

- ARINC bundle (`data/charts/LJMB/arinc_lj.zip`) is parsed by no current pipeline path. It
  carries airspace boundaries and terminal fixes but no SIDs/STARs/IAPs (verified
  2026-04-24); X-Plane CIFP supersedes it for terminal procedures.
- CUP bundle (`data/charts/LJMB/cup_slovenia.zip`) is also unused.
- Jepp PDF (`data/charts/LJMB/LJMB.pdf`) text is extractable for procedure metadata, but
  procedure routes are vector graphics; coordinates come from X-Plane / OFMX, not from
  the PDF itself. The Jepp 11-1 ILS RWY 32 minima (DA 1056', H 200') were captured into
  the per-airport IFR minima policy in `bin/airport_structured_package.py`.

## Outstanding LJMB-specific authoring decisions

- Whether the OpenAir-backed adjacent context (`TMA MURA`, `TMA DOLSKO 1`) should stay in
  the LJMB candidate slice or be pruned to a tighter Maribor-only set. Currently in.
- LJMB-native operational sector boundary DXF — sectors are declared in the manifest but
  lack a working-sector DXF; tracked as `M3` in `.plan`.
- Jepp `19-1` VFR-chart-derived training/holding area is not authored as separate
  geometry yet (it's the visible "Training/Holding" zone on the chart north-east of the
  field). The two main 14/32 circuits and the 14L/32R glider circuit are authored.
- **TOWER vs AFIS resolution (G2 Phase A, 2026-05-07).** LJMB's
  `cad/airports/rendered/ljmb/world-candidate.json` originally shipped without a `roles`
  block, leaving the world-candidate loader's roles-required validation to fail at
  `RoleNotPublished(role=TOWER, aerodrome=LJMB)`. The Slovenia AIP AD 2.LJMB lists
  `MARIBOR TOWER 119.205 MHz` — a controlled-airspace TOWER, not an AFIS. (LJMB's actual
  ATS unit at this frequency is staffed during published hours; AFIS at field-uncontrolled
  hours is a separate concern parked for a future "uncontrolled-hours" pass.) The
  authoring decision: publish `roles: { TOWER: { name: "TOWER", frequencyMhz: "119.205",
  authorities: ["PLACEHOLDER"] } }` in the world-candidate. The `authorities` PLACEHOLDER
  is intentional — full authority binding is outside G2's VFR transit scope and would
  require `wiki/identifier-reconciliation.md` updates first. The frequency `119.205` is
  pinned by `FixtureLoadSpec` (Phase A) so a regression to e.g. `119.20` or `119.250` is
  caught at unit-test latency.
- **ctrApproximationRadius (fn-7, 2026-05-09).** 18 NM, conservative placeholder = same
  as LOWG. Slovenia eAIP is not bot-fetchable (Slovenia Control's site returns 403/404
  to scripted clients), so the LJMB AIP AD 2.17 polygon was not transcribed for fn-7;
  real-polygon transcription is deferred as `D-AUDIT-ljmb-polygon` in
  `~/.claude/plans/pilot-firewall.md` § Deferments register. **Note: the 18 NM value
  is reused from LOWG as a conservative bound, not derived from regional-CTR comparisons.**
  18 NM under-fires the boundary-release rule (LOWG_TOWER holds traffic too long when
  LJMB's real polygon is smaller than 18 NM) which is regulatorily-safe. The 5 NM ICAO
  Annex 11 §2.11 floor — what a `null` schema field would default to in
  `WorldCandidateLoader` — would over-fire (release inside real CTR) at almost every
  controlled aerodrome and is intentionally NOT used for LJMB. The exact authored value
  is pinned by `CtrApproximationRadiusLoaderTest.expected` (R9), so a deliberate
  retune (after AIP transcription) requires a paired test-update + plan-review.
