# fn-84-icao-9432-phrase-1-after-landing.1 Implement after-landing rendered phraseology evidence split

## Description
Implement the fn-84 after-landing phraseology split. The work should add
rendered evidence for the ICAO 9432 §4.9 branches already represented by typed
protocol leaves and leave unsupported example fragments visibly residual.

## Acceptance
- [x] Add after-landing source refs with split titles/scopes.
- [x] Render and project `RunwayVacated`, `TaxiToStand`, and generic route
  readback phraseology, without adding `AirTaxiTo` phraseology.
- [x] The §4.9 taxi-to-stand evidence must pair the generic route readback with
  a matching `TaxiToStand` rendered instruction for the same destination/route;
  the readback alone must not satisfy the source unit. Route matching must use
  the same ordered `via` list.
- [x] Add DSL selectors and selector tests for the new templates.
- [x] Add source-mapped phraseology evidence tests for the covered §4.9 branches.
- [x] Use synthetic records only if the production LOWG trace cannot emit the
  exact branch; any synthetic use must be named in the test name/sample metadata
  with the failed real-trace branch and the production renderer path exercised.
- [x] The `df25159c1e7b94a3` evidence and docs must visibly state that `TAKE
  FIRST RIGHT WHEN VACATED` remains residual.
- [x] Update every affected source catalog, chunk 06 programme coverage/gap doc,
  and summary-count/manifest artifact touched by final-state counts.
- [ ] Run focused tests, detekt, broad tests, flow validation, implementation
  review, and completion review.
- [ ] Record implementation-review and completion-review receipts in the task
  evidence.

## Done summary
- Added rendered phraseology templates/tokens for `TaxiToStand`, generic
  `TaxiRouteReadback`, and `RunwayVacatedReport`.
- Added after-landing source refs and source-mapped evidence:
  `e30350fdecad45a1` is covered by ordered rendered
  `RUNWAY VACATED -> TAXI TO STAND -> route readback` evidence;
  `df25159c1e7b94a3` is split, with `CONTACT GROUND` wording covered and
  `TAKE FIRST RIGHT WHEN VACATED` left residual.
- Updated chunk 06 docs and the central implementation blocker manifest.
- Implementation review shipped after six review passes.

## Evidence
- Commits: `d8e28d31` implementation, `dd797fcf` implementation-review receipts.
- Tests:
  - `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest'`
  - `./gradlew-nix detekt`
  - `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
  - `scripts/ralph/flowctl validate --epic fn-84-icao-9432-phrase-1-after-landing --json`
  - `git diff --check`
- Implementation review: `.flow/.impl-review-receipt-fn84-r6.json` (`SHIP`).
- Completion review: pending.
- PRs:
