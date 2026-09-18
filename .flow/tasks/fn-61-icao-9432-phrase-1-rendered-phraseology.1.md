# fn-61-icao-9432-phrase-1-rendered-phraseology.1 Add rendered phraseology evidence contract

## Description
Add the test-side rendered phraseology evidence contract. The adapter should
project structured rendered tokens from `TransmissionRecord` while preserving
the typed protocol atom that produced the utterance.

## Acceptance
- [ ] Rendered phraseology payload carries typed token evidence and protocol provenance.
- [ ] Unsupported utterance leaves are explicit typed unsupported results, not silent success.
- [ ] Existing typed evidence facts and radio timing are unchanged.

## Done summary
Added common sim-level rendered phraseology port for selected controller clearances and evidence payloads for rendered plus unsupported phraseology projections.
## Evidence
- Commits:
- Tests: ./gradlew-nix :sim:jvmTest --tests '*.EvidenceFactsTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest', ./gradlew-nix :sim:jvmTest --tests '*.Icao9432*'
- PRs: