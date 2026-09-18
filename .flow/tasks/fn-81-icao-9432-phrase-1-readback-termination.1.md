# fn-81-icao-9432-phrase-1-readback-termination.1 Implement source-mapped readback termination phraseology evidence

## Description
Implement source-mapped rendered phraseology evidence for ICAO Doc 9432,
Manual of Radiotelephony, Fourth Edition, 2007, §2.8.3.7 readback termination.
The implementation must prove the invariant only for the currently supported
rendered pilot readback templates and must leave broader readback coverage
visibly blocked by PHRASE-1.

## Acceptance
- [ ] Add the §2.8.3.7 source ref under an appropriate `ICAO9432`
  namespace with `RenderedPhraseologyTrace` scope.
- [ ] Add typed evidence DSL assertions that select a named supported rendered
  readback template and check its final `PhraseologyToken` is the aircraft
  callsign. Missing templates must fail.
- [ ] Add a source-mapped `Icao9432PhraseologyEvidenceTest` case proving the
  invariant for both supported templates: `LineUpReadback` and
  `FrequencyReadback`.
- [ ] Preserve the existing specific line-up and frequency readback assertions.
- [ ] Update
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/source_plan.md`,
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/coverage_report.md`,
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/expected_gaps.md`,
  and
  `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
  with split wording: supported rendered readbacks covered; remaining
  templates phraseology-later.
- [ ] Do not alter pilot/controller runtime behaviour.
- [ ] Record focused tests, detekt, broad regression, and flow validation in
  this task Evidence section; keep implementation/completion receipts at
  `.flow/.impl-review-receipt-fn81.json` and
  `.flow/.completion-review-receipt-fn81.json`.

## Done summary
Implemented source-mapped ICAO 9432 §2.8.3.7 readback termination evidence for supported rendered readbacks, with PHRASE-1 retained for unsupported templates.
## Evidence
- Commits:
- Tests:
- PRs: