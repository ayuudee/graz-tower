# Data Sources Overview

We have four categories of source data for building airport worlds.

## X-Plane apt.dat

Physical airport layout. One file per airport.

**Contains:** Runways (endpoints, width, surface, displaced thresholds), taxi network (nodes + edges as a graph, with named taxiways), active zones (which edges cross which runways), stands/parking, helipads, pavement polygons, taxi signs, lighting, tower viewpoint, ATC frequencies, ATC flow rules (runway assignments by wind), pattern runway + direction (record 1101), service vehicles.

**Parser:** `migration/src/.../aptdat/` -- parses all record types. Tested against LOWG (3 runways, 96 taxi nodes, 86 active zones) and LJMB.

**Test data:** `migration/src/commonTest/resources/airports/LOWG.dat`, `LJMB.dat`

## OpenFlightMaps OFMX

Operational/airspace data. AIXM 4.5 XML with VFR extensions. Regional snapshots (we have Austria).

**Contains:** Airports (ICAO, IATA, elevation, magnetic variation, transition altitude), runways (dimensions, PCN, composition), runway directions (bearings, threshold positions), airspace (CTR/TMA/FIR with 3D boundaries and polygons), designated points (VFR reporting points -- VFR-MRP, VFR-RP -- and ICAO waypoints), ATC units and services, frequencies with call signs, service-airspace associations, navaids (VOR, NDB, DME).

**Does not contain:** VFR route geometry connecting reporting points, traffic circuit geometry, IFR procedures. The OFMX schema has `codeVfrPattern` (L/R) on runway directions but it's not populated in the Austrian data.

**Parser:** `migration/src/.../ofmx/` -- parses Ahp, Rwy, Rdn, Ase, Abd, Dpn, Uni, Ser, Fqy, Sae. Tested against hand-crafted LOWG subset + full 16MB Austria file (237 airports, 418 designated points).

**Note on OFM maps:** The OFM web viewer shows accurate circuit patterns and VFR routes rendered into raster tiles. This geometry exists in OFM's rendering pipeline but is NOT in the downloadable OFMX data. The tiles are pre-rendered at `nwy-tiles-api.prod.newaydata.com`.

**Repo data:** `data/ofm/austria/ofmx_extracted/` (full Austria), local Slovenia bundles under `data/charts/LJMB/` and extracted Slovenia data under `data/ofm/slovenia/ofmx_extracted/`, `migration/src/commonTest/resources/ofmx/lowg_subset.ofmx`

**Current parser scope:** The extracted Austria package also contains `ofmx_lo_ofmShapeExtension.xml`, but the current parser only consumes `isolated/ofmx_lo.ofmx`.

## X-Plane CIFP

IFR procedures. One file per airport. X-Plane's comma-delimited CIFP format (based on ARINC 424 concepts).

**Contains:** SIDs (with runway transitions), STARs, instrument approaches (VOR/DME, ILS, RNAV with approach transitions, final segments, missed approach), runway threshold data (coordinates, elevation, ILS identifier), precision approach minimums. Each procedure leg has: path terminator (IF/TF/CF/DF/CA/HA/HM etc.), fix reference, altitude/speed constraints, recommended navaid, turn direction.

**Parser:** `migration/src/.../cifp/` -- parses SID, STAR, APPCH, RWY, PRDAT sections. Groups legs into procedures by name + route type + transition. Tested against LOWG (21 SIDs, 8 STARs, 5 approach types, 173 total legs).

**Test data:** `data/cifp/LOWG.dat`, `migration/src/commonTest/resources/cifp/LOWG.dat`

## X-Plane earth_fix.dat / earth_nav.dat

Global waypoint and navaid position database. Needed because CIFP references fixes by name without positions.

**Purpose:** `earth_fix.dat` provides lat/lon for ICAO waypoints. `earth_nav.dat` provides lat/lon + frequency + type for navaids.

**Repo status:** Not currently checked into this repo working tree. Earlier notes refer to a 2017-vintage copy inspected outside the current tree.

**Not yet parsed.** Simple line-oriented formats, trivial to add once sourced.

**Gap:** Some newer CIFP waypoints (PIBIP, XIBAR, RONOT) were not found in the previously inspected 2017-vintage `earth_fix.dat`. We would need a current version or manual lookup.

## Local chart / nav bundles

Some airports may also have local chart/navigation bundles that are not yet part of the general migration pipeline.

Current example:

- `data/charts/LJMB/`
  - Jepp trip kit PDF
  - OFMX Slovenia bundle
  - ARINC bundle
  - OpenAir bundle
  - CUP bundle
  - clipped raster/tile underlays

These are useful bootstrap sources, but today only the OFMX and chart/PDF material fit directly into the existing migration flow.

## Requirements / RT source units

Structured source-unit packages for ATC rules, radio telephony, clearances,
readback, and related phraseology now live under
`research/tools/requirements-spike/quality/source_packages/`. See
`wiki/data-sources/requirements-source-units.md` for the current package frame,
source readiness statuses, and scope non-claims.
