# fn-83-icao-9432-phrase-1-final-and-long-final ICAO 9432 PHRASE-1 final and long-final report wording evidence

## Overview
Implement a narrow source-mapped PHRASE-1 evidence slice for ICAO Doc 9432,
Manual of Radiotelephony, Fourth Edition, 2007, §4.7 final approach and
landing report wording:

- `icao9432-extracted::final_approach_landing_4_7_en::00baaf3c55155044`
  includes the `FINAL` report wording and a 7 km / 4 NM timing rule.
- `icao9432-extracted::final_approach_landing_4_7_en::4c698a5ad52a30e4`
  includes the `LONG FINAL` report wording and a greater-than-7 km final-turn
  condition.
- `icao9432-extracted::final_approach_landing_4_7_en::70e781a65920c075`
  includes straight-in `LONG FINAL` wording and about-15 km / 8 NM timing.

This epic proves only the supported rendered pilot-report wording surface. It
does not claim that current traces prove the distance, timing, final-turn, or
straight-in approach conditions.

## Scope
- Add typed source refs under `ICAO9432.FinalApproachLanding` for the final
  report wording rows with `RenderedPhraseologyTrace` scope. Constant names,
  titles, and source-mapped test descriptions must encode wording-only
  semantics so later tests cannot accidentally cite these refs as full
  distance/timing coverage.
- Add a rendered pilot-report phraseology model for supported `ReportEvent`
  templates:
  - `ReportEvent.Final` -> `FINAL`;
  - `ReportEvent.LongFinal` -> `LONG FINAL`.
  The supported renderable shape is deliberately exact: a clear pilot
  `Report` containing exactly one event, either `Final` or `LongFinal`.
  Every other clear pilot `Report` shape, including multiple events and mixed
  supported/unsupported event lists, produces exactly one typed unsupported
  fact retaining the original `Report`.
- Project rendered pilot-report phraseology facts only from pilot `Report`
  transmissions whose `TransmissionRecord.receptionQuality` is
  `ReceptionQuality.Clear`, through `EvidenceFactAdapters.fromTransmissionRecords`.
  Non-clear report transmissions project no rendered or unsupported
  pilot-report phraseology fact.
- Add DSL selectors that assert the typed token sequence and template, not raw
  string substrings.
- Add source-mapped tests using evidence adapter output. Real trace evidence
  is preferred if the LOWG fixtures currently emit final/long-final reports;
  synthetic transmission records are acceptable only for wording-surface proof
  because the source movement is deliberately split from distance/timing
  semantics.
- Update chunk-05 docs and the central blocker manifest to state that rendered
  `FINAL` / `LONG FINAL` wording is covered while distance/timing/final-turn
  and straight-in policy evidence remain blocked.
- Pin the split in tests: source-mapped assertions may cite only the rendered
  wording branch, and a companion catalog/coverage test must assert that the
  residual distance/timing/final-turn/straight-in blockers remain represented.
- Do not alter runtime pilot behavior, controller behavior, report generation,
  or flight geometry.

## Approach
1. Verify the accepted source-unit text against
   `research/txt/icao9432-extracted.txt` before cataloging the refs.
2. Extend `RadioPhraseology.kt` with a small pilot-report renderer parallel to
   the existing controller/readback renderers. Unsupported reports must remain
   explicit unsupported render results rather than silently disappearing.
3. Extend `EvidenceFactPayload` / `EvidenceFactKind` with rendered
   pilot-report phraseology and unsupported rendered pilot-report phraseology
   payloads. The unsupported payload must retain the original
   `transmissionRef` and the unsupported `Report` so a reviewer can
   distinguish unsupported report type from missing/unclear transmission.
   Reserve a new per-record sequence offset and keep `FACTS_PER_RECORD` large
   enough.
4. Extend `EvidenceDsl.kt` with focused selectors for final and long-final
   pilot reports. Add negative DSL tests for absent evidence and wrong tokens.
   Add evidence-fact tests showing all three projection cases:
   - non-report pilot transmissions emit no pilot-report phraseology payload;
   - non-clear supported reports emit no rendered or unsupported
     pilot-report phraseology payload;
   - clear unsupported report shapes emit the typed unsupported payload.
   Also add a sender-scope test: a clear `ReportEvent.Final` or `LongFinal`
   payload from any non-pilot speaker emits no pilot-report phraseology
   payload.
5. Add source refs to `EvidenceSourceCatalog.kt`, catalog expectations, and
   source-mapped tests in `Icao9432PhraseologyEvidenceTest`.
6. Update chunk-05 `source_plan.md`, `coverage_report.md`,
   `expected_gaps.md`, and `implementation_blocker_manifest.csv` with split
   status. Recalculate all affected summary counts, table states, and
   expected-gap totals. Expected chunk-05 summary after this slice:
   - `covered-green`: 2;
   - `model-gap`: 2;
   - `policy-blocked`: 6;
   - `phraseology-later`: 10;
   - `split: FINAL wording covered; distance/timing remains blocked`: 1;
   - `split: LONG FINAL wording covered; final-turn distance remains blocked`: 1;
   - `split: straight-in LONG FINAL wording covered; straight-in timing/policy remains blocked`: 1.
   Do not change any source unit whose residual claim is still
   distance/timing/policy blocked.
7. Validate with focused tests, detekt, broad regression, flow validation,
   implementation review, and completion review.

## Review considerations
- FP / type safety: new render-result and payload branches must be sealed and
  handled exhaustively. Unsupported report shapes are typed facts, not
  swallowed defaults. No `else` branch may hide a report type.
- Test architecture: this is a high-level evidence-adapter test. Assertions
  must inspect typed templates/tokens and cite exact source refs. The tests
  must be loud if a supported template is absent, malformed, or unsupported.
- Impact: test evidence and pure phraseology rendering only. No controller
  selection, pilot planning, sim movement, or policy behavior changes. The new
  payload kind increases the evidence surface, so catalog/DSL tests must pin
  non-vacuity and wrong-path behavior.
- Operational correctness: ICAO Doc 9432 §4.7 is split honestly. This epic
  covers spoken report words only; it leaves the 7 km / 4 NM, 15 km / 8 NM,
  final-turn, straight-in, and local-procedure aspects blocked until geometry
  and policy evidence exists.

## Quick commands
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `scripts/ralph/flowctl validate --epic fn-83-icao-9432-phrase-1-final-and-long-final --json`
- `scripts/ralph/flowctl codex impl-review fn-83-icao-9432-phrase-1-final-and-long-final.1 --base HEAD~1 --receipt .flow/.impl-review-receipt-fn83.json --json --sandbox read-only`
- `scripts/ralph/flowctl codex impl-review fn-83-icao-9432-phrase-1-final-and-long-final --base HEAD~1 --receipt .flow/.completion-review-receipt-fn83.json --json --sandbox read-only`

## Acceptance
- [ ] Source refs for `00baaf3c55155044`, `4c698a5ad52a30e4`, and
  `70e781a65920c075` are catalogued with `RenderedPhraseologyTrace` scope and
  exact ICAO 9432 §4.7 metadata after verifying the source text in
  `research/txt/icao9432-extracted.txt`. New constant names and titles encode
  wording-only semantics.
- [ ] Rendered pilot-report phraseology supports `Final` and `LongFinal` with
  typed templates and tokens for exactly-one-event reports only.
- [ ] Evidence facts project rendered pilot-report phraseology only from
  `ReceptionQuality.Clear` pilot `Report` transmissions; non-clear reports
  emit neither rendered nor unsupported pilot-report phraseology facts.
- [ ] Clear report-shaped payloads from non-pilot speakers emit no rendered or
  unsupported pilot-report phraseology facts.
- [ ] Unsupported clear pilot report shapes project typed unsupported facts
  carrying the original `transmissionRef` and `Report`.
- [ ] Evidence-fact tests pin unique stable fact IDs when a transmission record
  emits multiple evidence facts including the new pilot-report phraseology
  offset.
- [ ] DSL/source-mapped tests prove `FINAL` and `LONG FINAL` wording without
  claiming distance/timing/straight-in policy closure.
- [ ] Source-mapped tests and catalog/coverage assertions label the evidence
  as rendered wording branch only and pin residual blockers for the distance,
  timing, final-turn, and straight-in claims.
- [ ] Chunk-05 docs and `implementation_blocker_manifest.csv` record split
  status honestly, with affected summary counts and expected-gap counts
  recalculated.
- [ ] No runtime pilot/controller behavior changes.
- [ ] Focused tests, detekt, broad regression, flow validation,
  implementation review, and completion review are recorded.

## References
- ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §4.7
- `research/txt/icao9432-extracted.txt`
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/RadioPhraseology.kt`
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt`
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432PhraseologyEvidenceTest.kt`
