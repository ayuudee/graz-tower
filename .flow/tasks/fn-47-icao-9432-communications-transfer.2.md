# fn-47-icao-9432-communications-transfer.2 Author testable-now communications/readback source-mapped tests

## Description
TBD

## Acceptance
- [ ] TBD

## Done summary
Authored source-mapped tests for the six honest chunk-01 test candidates: four structural readback source units through the evidence DSL and two hearback/correction source units through source-backed controller readback classification tests. Reclassified doubtful-reception repetition as COMMS-1 expected gap instead of overclaiming a protocol type.
## Evidence
- Commits:
- Tests: nix-shell --run ./gradlew :sim:jvmTest --tests *.Icao9432Chunk01ReadbackEvidenceTest :controller:jvmTest --tests *.Icao9432ReadbackConformanceSpec
- PRs: