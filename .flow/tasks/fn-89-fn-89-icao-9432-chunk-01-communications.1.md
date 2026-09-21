# fn-89-fn-89-icao-9432-chunk-01-communications.1 Implement communications phraseology example evidence

## Description
Implement the fn-89 plan for synthetic rendered communication phraseology
example evidence covering ICAO 9432 §2.8.1.1-§2.8.1.3 source units:

- `icao9432-extracted::communications_2_8_1_en::a685cef087951878`
- `icao9432-extracted::communications_2_8_1_en::b7acdc88125f1510`
- `icao9432-extracted::communications_2_8_1_en::5efac97fddfd54ca`

This is source-mapped synthetic example evidence only. Do not implement live
broadcast routing, acknowledgement policy, controller/pilot generation, or
general free-text phraseology.

## Acceptance
- [x] Add typed source refs for the three source units.
- [x] Use synthetic rendered-example claim scope, not
  `RenderedPhraseologyTrace`.
- [x] Use `SyntheticRenderedPhraseologyExample` via
  `sourceRenderedExample(...)`.
- [x] Add a closed rendered communication phraseology example
  payload/template/token surface for the four §2.8.1 examples.
- [x] Use a distinct communication phraseology token type.
- [x] Source coverage proves these exact source examples:
  `STEPHENVILLE TOWER G-ABCD`;
  `G-ABCD STEPHENVILLE TOWER`;
  `ALL STATIONS ALEXANDER CONTROL, FUEL DUMPING COMPLETED`;
  `ALL STATIONS G-CDAB WESTBOUND MARLO VOR TO STEPHENVILLE LEAVING FL 260 DESCENDING FL 150`.
- [x] The selector checks exact template, text, token list, and source order.
- [x] The selector ignores unrelated payload kinds but fails on extra
  same-kind payloads.
- [x] Selector tests cover absent evidence, missing example, wrong order,
  wrong template, wrong text, malformed tokens, and extra duplicate same-kind
  behavior.
- [x] Source coverage uses explicit `EvidenceFactAdapters.fromProjectedPayloads`
  payloads rather than selector helper constructors.
- [x] Existing trace-backed phraseology invariant surfaces remain unchanged.
- [x] Chunk-01 docs and the implementation blocker manifest move the three
  rows out of `phraseology-later`; counts are updated.
- [x] Focused tests, broad tests, detekt, flow validation, implementation
  review, completion review, and `git diff --check` pass.

## Done summary
Implemented synthetic rendered communication phraseology example evidence for
ICAO 9432 §2.8.1.1-§2.8.1.3. The evidence covers the full-callsign
initial-contact examples and the ground/aircraft `ALL STATIONS` broadcast
examples exactly, using a distinct communication phraseology payload/template
and token type. It does not claim live broadcast routing, acknowledgement
policy, controller/pilot generation, or general free-text phraseology.

Chunk-01 documentation and the central implementation blocker manifest now
move the three source units from `phraseology-later` to synthetic rendered
example coverage.

## Evidence
- Commits:
- Tests:
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432Chunk01CommunicationsPhraseologyEvidenceTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.EvidenceReportWriterTest'` - pass
- `./gradlew-nix detekt` - pass
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest` - pass
- `scripts/ralph/flowctl validate --epic fn-89-fn-89-icao-9432-chunk-01-communications --json` - pass
- `git diff --check` - pass
- Plan review: `.flow/.plan-review-receipt-fn89.json` - SHIP
- Implementation review: `.flow/.impl-review-receipt-fn89.json` - SHIP
- Completion review: `.flow/.completion-review-receipt-fn89.json` - SHIP
- PRs:
