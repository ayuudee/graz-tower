# fn-66-icao-9432-vehicle-1a-vehicle-movement ICAO 9432 VEHICLE-1A vehicle movement and permission lifecycle

## Overview
Introduce vehicle movement actors only far enough to prove vehicle first-call
and movement-permission lifecycle source units.

## Scope
- Vehicle identity/call sign, position, destination, and route.
- Vehicle transmissions in the shared radio evidence stream.
- Movement permission lifecycle for vehicles on the manoeuvring area.
- No runway-vacated geometry or towing metadata green-out in this epic.

## Approach
Use the chunk 07 expected-gap decomposition to select a movement/permission
subset and keep runway occupancy and towing units blocked unless explicitly in
scope.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*SourceUnit*'`
- `./.flow/bin/flowctl validate --epic fn-66-icao-9432-vehicle-1a-vehicle-movement`
- `git diff --check`

## Acceptance
- [ ] Exact source-unit movement manifest exists.
- [ ] Vehicle actor/evidence types are closed and do not weaken aircraft-only
  invariants.
- [ ] Runway crossing/occupancy and towing/vacated geometry units remain
  blocked unless explicitly targeted.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_07_vehicles_towing/coverage_report.md`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
