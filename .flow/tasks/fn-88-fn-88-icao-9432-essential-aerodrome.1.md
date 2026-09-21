# fn-88-fn-88-icao-9432-essential-aerodrome.1 Implement essential aerodrome information rendered example evidence

## Description
Implement the fn-88 plan for rendered essential-aerodrome-information example
phraseology evidence for ICAO 9432 §4.10 source unit
`icao9432-extracted::essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75`.

This is evidence vocabulary and selector work only. Do not implement live
controller generation, policy, timing, omission, construction geometry,
lighting serviceability state, runway-condition modelling, or aircraft receipt
projection.

## Acceptance
- [x] Add a typed source ref for
  `icao9432-extracted::essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75`.
- [x] Add a synthetic rendered-example claim scope and use it for this source
  ref instead of `RenderedPhraseologyTrace`.
- [x] Add a dedicated synthetic rendered-phraseology example claim kind and
  builder method; do not use `source` or `sourceVocabulary` for this source
  case.
- [x] Add a closed rendered essential-aerodrome-information example evidence
  payload/template surface for the three §4.10 examples.
- [x] Use a distinct essential-aerodrome-information phraseology token type,
  not shared `PhraseologyToken`.
- [x] The positive source case proves these exact source examples:
  `FASTAIR 345 CAUTION CONSTRUCTION WORK ADJACENT TO GATE 37`;
  `CENTRE LINE TAXIWAY LIGHTING UNSERVICEABLE`;
  `RUNWAY CONDITIONS 09: AVAILABLE WIDTH 32 METRES, COVERED WITH THIN PATCHES OF ICE, BRAKING ACTION POOR`.
- [x] The selector checks exact template, text, token list, and source order.
- [x] The selector ignores unrelated evidence payload kinds, but requires
  exactly three rendered essential-aerodrome-information phraseology payloads
  and fails on extra same-kind payloads.
- [x] Selector tests cover absent evidence, missing example, wrong order, wrong
  template, wrong text, malformed tokens, and extra duplicate same-kind payload
  behavior.
- [x] The new payload/templates remain separate from `RenderedPhraseologyTemplate`
  and are excluded from controller/pilot/vehicle rendered phraseology invariant
  sets.
- [x] Source coverage uses explicit `EvidenceFactAdapters.fromProjectedPayloads`
  payloads and states that it is synthetic rendered-example evidence only.
- [x] Structural essential-aerodrome-information evidence remains unchanged in
  claim scope and wording.
- [x] Chunk 06 docs and the implementation blocker manifest move the row out
  of `phraseology-later`; expected-gaps counts are updated.
- [x] Focused tests, broad tests, detekt, flow validation, implementation
  review, completion review, and `git diff --check` pass. Record review
  receipts from `flowctl codex impl-review --base origin/main` and
  `flowctl codex completion-review --base origin/main`.

## Done summary
Implemented a narrow synthetic rendered-example evidence surface for ICAO 9432
§4.10 essential aerodrome information examples. The coverage is intentionally
limited to exact source example wording, templates, tokens, and source order;
it does not claim live controller projection, timing, omission, open pertinence,
or policy behavior.

The source catalog now records
`icao9432-extracted::essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75`
under `SyntheticRenderedPhraseologyExample`, separate from trace-backed
rendered phraseology. Chunk 06 documentation and the implementation blocker
manifest now move the source unit out of `phraseology-later`.

## Evidence
- Commits:
- Tests:
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432EssentialAerodromeInformationEvidenceTest' --tests '*.EvidenceReportWriterTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest'` - pass
- `./gradlew-nix detekt` - pass
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest` - pass
- `scripts/ralph/flowctl validate --epic fn-88-fn-88-icao-9432-essential-aerodrome --json` - pass
- `git diff --check` - pass
- Plan review: `.flow/.plan-review-receipt-fn88-r3.json` - SHIP
- Implementation review: `.flow/.impl-review-receipt-fn88-r3.json` - SHIP
- Completion review: `.flow/.completion-review-receipt-fn88.json` - SHIP
- PRs:
