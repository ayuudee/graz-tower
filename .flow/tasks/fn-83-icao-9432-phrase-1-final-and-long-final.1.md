# fn-83-icao-9432-phrase-1-final-and-long-final.1 Implement source-mapped final and long-final report wording evidence

## Description
Implement the fn-83 PHRASE-1 wording slice for ICAO 9432 §4.7 final approach
reports. The implementation must prove only rendered pilot-report wording for
supported `FINAL` and `LONG FINAL` reports. Distance, timing, final-turn, and
straight-in policy aspects remain explicit blockers.

## Acceptance
- [x] Add exact source refs under `ICAO9432.FinalApproachLanding` for:
  - `icao9432-extracted::final_approach_landing_4_7_en::00baaf3c55155044`;
  - `icao9432-extracted::final_approach_landing_4_7_en::4c698a5ad52a30e4`;
  - `icao9432-extracted::final_approach_landing_4_7_en::70e781a65920c075`.
  Name/title them as wording-only refs.
- [x] Verify those three accepted source-unit quotes against
  `research/txt/icao9432-extracted.txt` before cataloging them, and record the
  verification in this task's `Done summary`.
- [x] Add rendered pilot-report phraseology for `ReportEvent.Final` and
  `ReportEvent.LongFinal`, with typed templates/tokens. The only supported
  shape is a `Report` with exactly one event, either `Final` or `LongFinal`.
- [x] Project rendered and unsupported pilot-report phraseology facts from
  `ReceptionQuality.Clear` pilot report transmissions only; non-clear reports
  emit neither rendered nor unsupported pilot-report phraseology facts.
- [x] Unsupported clear pilot report shapes emit typed unsupported facts that
  retain `transmissionRef` and the original `Report`; multiple-event and mixed
  supported/unsupported reports are unsupported as whole reports.
- [x] Add DSL selectors and negative tests for missing evidence / wrong tokens.
- [x] Add fact-adapter tests for non-report silence, non-clear supported-report
  suppression, non-pilot speaker suppression, unsupported clear report
  projection, and unique stable fact IDs for records with multiple projected
  facts.
- [x] Add source-mapped phraseology tests that cite the exact source refs and
  prove wording only.
- [x] Add a catalog/coverage assertion that residual distance/timing/final-turn
  and straight-in blockers remain represented for the three split source rows.
- [x] Update chunk-05 docs and `implementation_blocker_manifest.csv` with
  split wording: report words covered, operational geometry/policy still
  blocked. Recalculate affected summary counts and table states to:
  `covered-green` 2, `model-gap` 2, `policy-blocked` 6, `phraseology-later`
  10, plus one split row each for `FINAL` wording, final-turn `LONG FINAL`
  wording, and straight-in `LONG FINAL` wording.
- [x] Do not change runtime pilot/controller behavior.
- [x] Record focused tests, detekt, broad regression, flow validation,
  implementation review, and completion review.

## Done summary
Implemented wording-only rendered pilot-report phraseology evidence for ICAO
9432 §4.7 `FINAL` / `LONG FINAL` report source rows. Verified the three
accepted source-unit quotes against `research/txt/icao9432-extracted.txt`
lines 5678-5681 and the registry candidate JSON:
`00baaf3c55155044`, `4c698a5ad52a30e4`, and `70e781a65920c075`.
Coverage is explicitly split: rendered report wording is source-mapped;
distance, timing, final-turn, straight-in, and local-procedure semantics remain
blocked in chunk-05 docs and the central manifest.

## Evidence
- Commits:
- Tests:
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `scripts/ralph/flowctl validate --epic fn-83-icao-9432-phrase-1-final-and-long-final --json`
- `git diff --check`
- PRs:
