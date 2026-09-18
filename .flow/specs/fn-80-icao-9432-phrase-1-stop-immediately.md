# fn-80-icao-9432-phrase-1-stop-immediately ICAO 9432 PHRASE-1 stop-immediately phraseology evidence

## Overview

Add source-mapped rendered phraseology evidence for the existing
`StopImmediately` instruction surface:

- `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6b5a0d8b27525cbd`

The source row says that when an aircraft has commenced take-off roll and
abandoning take-off is necessary to avert dangerous traffic, the aircraft should
be instructed to stop immediately and the instruction and callsign repeated.

This epic covers only the rendered phraseology surface:
`[callsign] STOP IMMEDIATELY [callsign] STOP IMMEDIATELY`.

## Scope

In scope:

- Verify the accepted source quote against `research/txt/icao9432-extracted.txt`.
- Add `StopImmediatelyInstruction` rendered controller phraseology support for
  existing `StopImmediately`. This renderer emits the full transmission phrase,
  not an instruction fragment to be wrapped elsewhere.
- Add tokens needed for `STOP` / `IMMEDIATELY`.
- Add a source ref scoped as `RenderedPhraseologyTrace`.
- Add a source-mapped rendered phraseology test using a synthetic
  `StopImmediately` transmission record, because routine LOWG goldens do not
  contain this emergency instruction.
- Update chunk-04 docs and central manifest as a split:
  `split: rendered phraseology covered; takeoff-roll/dangerous-traffic trigger
  policy blocked`.

Out of scope:

- No live takeoff-roll emergency scenario.
- No dangerous-traffic trigger policy or controller selection change.
- No pilot movement or runway-duty behaviour change.
- No closure for takeoff cancellation, runway-freeing, or stop-immediately
  operational trigger semantics.

## Approach

1. Read the source lines around the STOP IMMEDIATELY example and the accepted
   `classification.json` row.
2. Extend `RadioPhraseology` for `StopImmediately` with typed tokens and text.
3. Add an evidence DSL selector for stop-immediately phraseology.
4. Add `ICAO9432.TakeoffProcedures.StopImmediatelyPhrase` to the source catalog
   and catalog tests.
5. Add a focused source-mapped test in `Icao9432PhraseologyEvidenceTest` using
   `EvidenceFactAdapters.fromTransmissionRecords` and a synthetic
  `ControllerOutput.Instruct.fromEmergencyPolicy(StopImmediately(...))` record.
   The test must go through `EvidenceFactAdapters.fromTransmissionRecords` so
   it exercises the same rendered phraseology projection path used by real
   transmissions.
6. Update chunk-04 programme docs/manifests and add fn-80 manifest +
   self-assessment.
7. Run focused tests, detekt, broad regression, flow validation, impl review,
   completion review, commit, push.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`

## Acceptance

- [ ] Existing `StopImmediately` can render controller phraseology as a full
  phrase with exactly this structural token order: callsign, STOP, IMMEDIATELY,
  callsign, STOP, IMMEDIATELY. Exactly two callsign tokens are present.
- [ ] A source-mapped test cites `6b5a0d8b27525cbd` and asserts the rendered
  token sequence structurally.
- [ ] Chunk-04 `source_plan.md`, `coverage_report.md`, `expected_gaps.md`, and
  the central `implementation_blocker_manifest.csv` record the split status
  `rendered phraseology covered; takeoff-roll/dangerous-traffic trigger policy
  blocked`, not a false full operational green.
- [ ] No operational controller/pilot/sim behaviour changes are introduced.
- [ ] Flow review and validation artifacts are recorded.

## Review Considerations

- FP / type safety: add explicit sealed enum/token branches only; unsupported
  instructions must remain typed unsupported.
- Test architecture: use source-mapped rendered phraseology evidence. Synthetic
  transmission is acceptable only because this is phraseology rendering, not
  operational trigger coverage.
- Impact: production change is limited to the pure renderer. No state mutation,
  reversal, or controller rule selection changes.
- Operational correctness: ICAO Doc 9432 §4.5 emergency stop phraseology is
  cited, but the dangerous-traffic/takeoff-roll trigger remains outside this
  epic.

## References
- `research/txt/icao9432-extracted.txt`
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_04_runway_departure/source_plan.md`
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_04_runway_departure/coverage_report.md`
