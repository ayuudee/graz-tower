# fn-79-icao-9432-phrase-1-takeoff-clearance.1 Implement source-mapped takeoff-clearance phraseology evidence

## Description
Implement source-mapped rendered take-off clearance phraseology evidence for the ICAO 9432 source unit.

## Acceptance
- [ ] The exact source unit `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3` is present in the typed source catalog.
- [ ] A source-mapped test proves the rendered take-off clearance template over a real LOWG trace.
- [ ] Chunk-04 documentation and the central manifest move only this row to `covered-green` rendered phraseology.
- [ ] Residual PHRASE-1/policy/model-gap rows remain explicitly blocked.
- [ ] Flow plan review, implementation review, completion review, and validation artifacts are recorded.

## Done summary
Closed the ICAO 9432 §4.5 base take-off clearance phraseology row with source-mapped rendered phraseology evidence. Added the typed source ref, cited it from the existing LOWG take-off clearance rendered phraseology trace, and moved only 13264a6ac6d529c3 in chunk-04 docs/manifests while keeping residual PHRASE-1 rows visible.
## Evidence
- Commits:
- Tests:
- PRs: