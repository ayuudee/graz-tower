# fn-79 self-assessment

## Principal checks

- Totality: no sealed `when` branches or renderer cases are added.
- Reversal completeness: no state transitions or reversible simulator state
  mutations are introduced.
- Interaction coverage: the evidence test uses a real LOWG trace and checks the
  rendered phraseology facts through the source-mapped DSL.
- Test coverage for known features: the new source ref is covered by
  `Icao9432PhraseologyEvidenceTest` and catalog tests.
- New-field audit: no state/data fields are added.
- Operational correctness: coverage is limited to ICAO Doc 9432 §4.5
  `RUNWAY [designator] CLEARED FOR TAKE-OFF` phraseology.
- Error handling honesty: no new thrown error paths are introduced.
- Deferment honesty: remaining PHRASE-1 rows stay visible in chunk-04 docs and
  the fn-79 manifest.

## Validation

- GREEN: `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest'`
- GREEN: `./gradlew-nix detekt`
- GREEN: `scripts/ralph/flowctl validate --epic fn-79-icao-9432-phrase-1-takeoff-clearance --json`
- GREEN: `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- GREEN: `git diff --check`
