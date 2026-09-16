# fn-55-icao-9432-chunk-05-circuit-arrival.2 Author circuit arrival landing source-mapped evidence tests

## Description
Author source-mapped evidence tests for the chunk 05 units that are honestly
testable against current protocol/sim traces after source-plan review.
Prefer high-level circuit / arrival / landing scenarios over low-level tests.

## Acceptance
- [x] Add typed `EvidenceSourceRef` records for source units that receive
      permanent evidence coverage.
- [x] Add or adjust source-mapped tests for reviewed testable chunk 05 units.
- [x] Do not count rendered phraseology source units as covered by typed
      instruction or report events alone.
- [x] Do not repair controller/pilot/sim behaviour except narrow evidence
      projection needed to express already-observed facts.

## Done summary
Strengthened touch-and-go source-backed scenario with pilot TOUCH_AND_GO Downwind request before ClearedTouchAndGo; narrowed false-green phraseology/source citations.
## Evidence
- Commits:
- Tests: ./gradlew-nix :sim:jvmTest --tests '*.Icao9432TouchAndGoSourceBackedScenarioTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidencePermanentTwentyCaseTest' --tests '*.EvidenceSourceCatalogTest', ./gradlew-nix detekt
- PRs:
