# Identifier Reconciliation

The three data sources use different naming conventions for the same entities.

## Runway designators

| apt.dat | OFMX | CIFP |
|---|---|---|
| `16C` (each end separate) | `16C/34C` (paired) | `RW16C` (prefixed) |

Normalisation: strip `RW` prefix from CIFP, split paired OFMX designators.

## Fix/navaid identifiers

OFMX and CIFP both use plain identifiers (e.g., `GRZ`) but reference different fix universes:

- **OFMX** designated points: VFR reporting points (GRAZ-NORD, SENDER DOBL, KALSDORF, AUTOBAHN-OST, AUTOBAHN-WEST, GREEN CITY) and some navaids (GRZ VOR)
- **CIFP** procedure fixes: IFR waypoints (ABIRI, PIBIP, XIBAR, GOLVA, GOTAR, MUREG, RONOT, VAGIL) and navaids (GRZ, GBG, OEG)
- **Overlap:** GRZ and GBG appear in both sources. Most fixes are source-exclusive.

Fix positions come from:
- OFMX `Dpn` elements (lat/lon in OFMX format: `47.65381389N`)
- `earth_fix.dat` / `earth_nav.dat` for CIFP-referenced fixes not in OFMX (needed, but not currently checked into this repo)

## Airport identifiers

All three sources use ICAO codes (LOWG). OFMX additionally provides IATA (GRZ).
