# fn-65-icao-9432-vehicle-1b-vehicle-runway.2 Implement vehicle runway crossing and occupancy evidence

## Description
Implement the narrow fn-65 vehicle-runway evidence surface:
vehicle-specific runway states, crossing permission, driver acknowledgement,
clear-beyond-holding-point evidence, and source-backed tests. Do not reuse
aircraft runway occupancy as vehicle evidence and do not green towing or
expected-aircraft-operation claims without the missing model facts.

## Acceptance
- [ ] Vehicle runway state includes holding-short, crossing/on-runway, and
  clear-beyond-holding-point evidence.
- [ ] Crossing starts only after positive controller permission and driver
  acknowledgement.
- [ ] Vehicle-only runway-vacated report is rejected before clear-beyond-
  holding-point evidence.
- [ ] `24f7b6a86407ef6f` moves to covered-green.
- [ ] `735b3e9ada06105b` moves only as a split vehicle-only branch; tow extent
  remains blocked.
- [ ] `331c1cfc98ead868` remains a model gap unless an explicit expected
  landing/takeoff trigger is implemented.
- [ ] Focused source-backed and model-gap tests pass.

## Done summary
Implemented vehicle runway crossing/vacated evidence: positive permission plus acknowledgement for 24f7, vehicle-only clear-beyond-holding-point split for 735, and preserved 331 as an expected-aircraft-operation model gap.
## Evidence
- Commits:
- Tests: ./gradlew-nix :sim:jvmTest --tests '*.Icao9432VehicleRunwaySourceBackedScenarioTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest', ./gradlew-nix :sim:jvmTest --tests '*.Icao9432*Source*' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest', ./gradlew-nix detekt, .flow/bin/flowctl validate --epic fn-65-icao-9432-vehicle-1b-vehicle-runway, git diff --check
- PRs: