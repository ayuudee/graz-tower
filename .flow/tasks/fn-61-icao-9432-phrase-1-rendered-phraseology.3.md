# fn-61-icao-9432-phrase-1-rendered-phraseology.3 Author source-mapped proof-set tests

## Description
Author source-mapped proof-set tests for the green-targeted non-emergency
phraseology units and regression tests for the phraseology evidence contract.

## Acceptance
- [ ] `ClearedTouchAndGo` source unit lands covered-green from live LOWG trace rendered tokens.
- [ ] `ClearedForTakeoff` renderer support is tested, but source unit `13264a6ac6d529c3` remains blocked as support-only / review-only rather than standalone covered-green.
- [ ] Start-up, emergency, vehicle/tow, pushback, and unsupported phraseology units remain blocked.
- [ ] Tests do not rely on brittle full-string snapshots for ordered phrase semantics.

## Done summary
Added source-mapped touch-and-go rendered phraseology test plus renderer/selector regression tests; start-up, emergency, vehicle/tow, pushback and takeoff support-only source units remain blocked.
## Evidence
- Commits:
- Tests: Icao9432PhraseologyEvidenceTest, EvidenceFactsTest, EvidenceDslTest, Icao9432ModelGapSourceUnitSpecTest
- PRs: