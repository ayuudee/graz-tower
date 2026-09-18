# fn-67-icao-9432-vehicle-1c-towing-metadata.2 Implement towing metadata and tow-extent evidence

## Description
Implement the approved fn-67 towing evidence surface: structured tow metadata,
tow-specific driver radio request, and vehicle+tow clear-beyond-holding-point
evidence. Keep rendered vehicle/tow wording blocked unless the implementation
explicitly adds reviewed vehicle phraseology rendering.

## Acceptance
- [ ] Tow request identifies aircraft under tow to the receiving station.
- [ ] Tow request carries aircraft type and operator where applicable.
- [ ] Vehicle with an active tow cannot report runway vacated until both the
  vehicle and tow extent are clear beyond the holding point.
- [ ] Stale tow-clear events cannot prove current clearance.
- [ ] Source-backed tests move only the declared source units/split branches.
- [ ] Focused tests pass.

## Done summary
Implemented structured tow metadata, tow-specific request handling, correlated vehicle/tow clear-beyond-holding-point evidence, and source-mapped towing tests. Updated chunk 07 ledgers and blocker manifest for 29be, 4b103, and 735.
## Evidence
- Commits:
- Tests: ./gradlew-nix :sim:jvmTest --tests '*.Icao9432VehicleTowingSourceBackedScenarioTest' --tests '*.Icao9432VehicleRunwaySourceBackedScenarioTest' --tests '*.Icao9432VehicleMovementSourceBackedScenarioTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest', ./gradlew-nix :sim:jvmTest --tests '*.Icao9432*Source*' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest', ./gradlew-nix detekt
- PRs: