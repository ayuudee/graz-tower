# fn-54-icao-9432-chunk-04-runway-departure.2 Author runway departure source-mapped evidence tests

## Description
Author source-mapped evidence tests for the chunk 04 units that are honestly
testable against current protocol/sim traces after the source-plan review.
Prefer high-level departure scenarios and structural protocol evidence over
low-level unit tests.

## Acceptance
- [x] Add typed `EvidenceSourceRef` records for source units that receive
      permanent evidence coverage.
- [x] Add source-mapped tests for reviewed testable runway-departure units.
- [x] Use covered-red outcomes where the source unit is testable but current
      protocol/sim behaviour does not satisfy it.
- [x] Do not repair controller/pilot behaviour except narrow evidence
      projection needed to express already-observed facts.

## Done summary
Added chunk 04 runway-departure scenario evidence for LOWG GROUND-to-TOWER transfer at the runway holding point, plus typed catalog coverage.
## Evidence
- Commits:
- Tests: focused chunk04/model-gap/catalog sim jvm tests, ./gradlew-nix detekt
- PRs:
