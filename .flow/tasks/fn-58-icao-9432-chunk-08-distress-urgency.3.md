# fn-58-icao-9432-chunk-08-distress-urgency.3 Add chunk 08 emergency model-gap specs

## Description
Add permanent source-specific gap specs for chunk 08. Do not add green scenario
tests unless the current simulator exposes real distress/urgency/comms-failure
behaviour.

## Acceptance
- [x] Add chunk 08 model-gap specs for the selected grouped source units.
- [x] Cite typed source refs or explicit `SourceUnitRef` ids.
- [x] Add an exact-union guard proving the chunk 08 gap-spec source refs equal
      `ICAO9432.DistressUrgencyCommsFailure.Chunk08Items`.
- [x] Do not use normal radio/go-around/VFR traces as emergency compliance
      evidence.
- [x] Keep phraseology blocked on rendered wording support.

## Done summary
Added chunk 08 `ProjectionGapSource` refs, catalog pins, grouped expected-gap
specs, and an exact coverage guard that checks all 46 source refs appear once
across the grouped spec source lists.
## Evidence
- Tests: `./gradlew-nix :sim:jvmTest --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- Files: `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalog.kt`
- Files: `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalogTest.kt`
- Files: `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432ModelGapSourceUnitSpecTest.kt`
