# fn-57-icao-9432-chunk-07-vehicles-towing.3 Add chunk 07 vehicle model-gap specs

## Description
Add permanent source-specific gap specs for chunk 07. Do not add green
scenario tests unless the current simulator exposes real vehicle/towing
behaviour.

## Acceptance
- [x] Add chunk 07 model-gap specs for the selected grouped source units.
- [x] Cite typed source refs or explicit `SourceUnitRef` ids.
- [x] Do not use aircraft-only traces as vehicle compliance evidence.
- [x] Keep phraseology blocked on rendered wording support.

## Done summary
Added chunk 07 expected-gap specs and source catalog refs. The specs cite typed source refs for all 12 vehicle/towing units and deliberately avoid using aircraft-only traces as vehicle compliance evidence.
## Evidence
- Tests: `./gradlew-nix :sim:jvmTest --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- Files: `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalog.kt`
- Files: `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalogTest.kt`
- Files: `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432ModelGapSourceUnitSpecTest.kt`
