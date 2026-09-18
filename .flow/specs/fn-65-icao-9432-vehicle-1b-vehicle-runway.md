# fn-65-icao-9432-vehicle-1b-vehicle-runway ICAO 9432 VEHICLE-1B vehicle runway crossing and occupancy

## Overview
Prove vehicle runway crossing and occupancy obligations without treating the
mere existence of vehicle actors as runway-vacated evidence.

## Scope
- Vehicle runway crossing clearance and hold-short/vacated reports.
- Runway occupancy/conflict evidence for vehicles.
- Holding-point and extent evidence where the source unit turns on whether the
  vehicle is clear of the runway.
- Towing metadata remains out of scope unless explicitly selected.

## Approach
Build on the vehicle movement lifecycle. Select exact crossing/occupancy units
from chunk 07 and add source-mapped tests that fail if vehicle geometry or
runway-clear evidence is absent.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*SourceUnit*'`
- `./.flow/bin/flowctl validate --epic fn-65-icao-9432-vehicle-1b-vehicle-runway`
- `git diff --check`

## Acceptance
- [ ] Exact source-unit movement manifest exists.
- [ ] Vehicle runway occupancy and clear-of-runway facts are explicit.
- [ ] No towing/vacated-geometry unit moves green without explicit extent
  evidence.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_07_vehicles_towing/coverage_report.md`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
