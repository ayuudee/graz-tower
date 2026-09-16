# fn-55-icao-9432-chunk-05-circuit-arrival.3 Record circuit arrival landing policy and phraseology expected gaps

## Description
Record policy, phraseology, and model/evidence gaps for chunk 05 source units
that cannot honestly be marked covered-green in this epic.

## Acceptance
- [x] Record `PHRASE-1` units as phraseology-later with source ids.
- [x] Record `POLICY-1` units as policy-blocked with the missing policy
      concept named.
- [x] Record low-pass / low-approach and final-distance missing model/evidence
      primitives as source-specific expected gaps.
- [x] Avoid broad or silent deferrals.

## Done summary
Recorded chunk 05 local-procedure policy, low-approach workflow, final-distance, and phraseology expected gaps with source-specific docs and specs.
## Evidence
- Commits:
- Tests: ./gradlew-nix :sim:jvmTest --tests '*.Icao9432ModelGapSourceUnitSpecTest'
- PRs:
