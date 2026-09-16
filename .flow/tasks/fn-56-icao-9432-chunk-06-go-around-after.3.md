# fn-56-icao-9432-chunk-06-go-around-after.3 Add honest chunk 06 evidence tests or gap specs

## Description
Add or tighten permanent source-mapped tests only where the current sim can
honestly prove the source claim or expose a source-backed failure. Leave all
other rows as loud expected gaps.

## Acceptance
- [x] Reuse existing go-around golden traces only if assertions prove the
      cited source-unit claim.
- [x] Add chunk 06 evidence tests or gap specs for the rows selected by the
      reviewed plan.
- [x] Do not mark typed instruction/report existence as rendered phraseology
      compliance.
- [x] Do not implement broad controller/pilot behaviour outside the reviewed
      chunk 06 plan.

## Done summary
Added chunk 06 VFR go-around source-backed scenario and source-specific gap specs/catalog refs.
## Evidence
- Commits:
- Tests: `./gradlew-nix :sim:jvmTest --tests '*.Icao9432Chunk06GoAroundEvidenceTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- PRs:
