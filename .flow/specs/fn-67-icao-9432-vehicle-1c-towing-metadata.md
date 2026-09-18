# fn-67-icao-9432-vehicle-1c-towing-metadata ICAO 9432 VEHICLE-1C towing metadata and vacated geometry

## Overview
Cover towing-specific ICAO 9432 source units that require tow metadata and
vacated-state geometry.

## Scope
- Aircraft type/operator metadata where tow phraseology or request content
  requires it.
- Towed-aircraft geometry/extent and vacated-state evidence.
- Rendered towing wording remains phraseology work unless the exact unit is
  targeted with phraseology evidence.

## Approach
Build on vehicle movement and runway occupancy work. Select exact towing units,
then add the minimal metadata and geometry evidence required by those claims.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*SourceUnit*'`
- `./.flow/bin/flowctl validate --epic fn-67-icao-9432-vehicle-1c-towing-metadata`
- `git diff --check`

## Acceptance
- [ ] Exact source-unit movement manifest exists.
- [ ] Tow metadata and vacated-state geometry are explicit in trace evidence.
- [ ] Phraseology-only towing units remain blocked unless rendered wording is
  proven under the phraseology contract.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_07_vehicles_towing/coverage_report.md`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
