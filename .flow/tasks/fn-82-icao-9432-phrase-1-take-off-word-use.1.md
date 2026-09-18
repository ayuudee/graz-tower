# fn-82-icao-9432-phrase-1-take-off-word-use.1 Implement source-mapped take-off word-use phraseology evidence

## Description
Implement source-mapped rendered phraseology evidence for ICAO Doc 9432,
Manual of Radiotelephony, Fourth Edition, 2007, §2.8.3.3 TAKE OFF word-use.
The implementation must prove the invariant only across the currently
supported rendered controller phraseology templates and must leave cancellation
wording plus unsupported templates visibly blocked.

## Acceptance
- [ ] Add the §2.8.3.3 source ref under `ICAO9432.Readback` with
  `RenderedPhraseologyTrace` scope.
- [ ] Add a typed evidence DSL assertion that fails if any supported rendered
  controller template is absent or if `PhraseologyToken.TakeOff` appears
  outside `RenderedPhraseologyTemplate.TakeoffClearance`.
- [ ] Add focused negative DSL tests for empty evidence, a missing required
  template, a prohibited `TakeOff` token on a non-takeoff template, and mixed
  valid/invalid facts.
- [ ] Add a source-mapped `Icao9432PhraseologyEvidenceTest` case using real
  LOWG evidence for certified runway/frequency templates and a synthetic
  emergency-policy `StopImmediately` record for the trigger-blocked template.
- [ ] Preserve existing phraseology evidence tests.
- [ ] Update chunk-01 programme docs and
  `implementation_blocker_manifest.csv` with split wording: supported rendered
  controller templates covered; cancellation wording and unsupported templates
  remain blocked.
- [ ] Do not alter runtime pilot/controller behavior.
- [ ] Record focused tests, detekt, broad regression, flow validation,
  implementation review, and completion review.

## Done summary
Implemented source-mapped ICAO 9432 §2.8.3.3 TAKE OFF word-use evidence for supported rendered controller templates, with cancellation wording and unsupported templates retained as PHRASE-1.
## Evidence
- Commits:
- Tests:
- PRs: