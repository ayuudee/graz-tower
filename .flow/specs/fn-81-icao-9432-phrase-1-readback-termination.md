# fn-81-icao-9432-phrase-1-readback-termination ICAO 9432 PHRASE-1 readback termination phraseology evidence

## Overview
Implement a source-mapped PHRASE-1 evidence slice for ICAO Doc 9432,
Manual of Radiotelephony, Fourth Edition, 2007, §2.8.3.7:

> An aircraft should terminate the read-back by its call sign.

The current rendered pilot readback surface is deliberately small:
`LineUpReadback` and `FrequencyReadback`. This epic proves the call-sign
termination invariant for that supported rendered surface only. It must not
claim universal coverage for all possible readbacks.

## Scope
- Add an evidence catalog source for
  `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::4c808d67d281ff71`
  with `RenderedPhraseologyTrace` scope.
- Add DSL assertions that prove each named supported rendered pilot readback
  template terminates with `PhraseologyToken.AircraftCallsign(aircraftId)`.
- Add source-mapped tests proving both currently rendered readback templates:
  line-up acknowledgement and frequency readback.
- Update the exact chunk-01 docs and implementation blocker manifest to record
  a split status: supported rendered readback templates covered; unsupported
  readback templates remain PHRASE-1:
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/source_plan.md`,
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/coverage_report.md`,
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_01_comms_readback_transfer/expected_gaps.md`,
  and
  `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`.
- Do not change controller selection, pilot decision-making, readback
  generation, or operational policy.

## Approach
1. Extend the evidence source catalog with a `ReadbackContinuation` source ref
   for §2.8.3.7.
2. Extend `AuditRenderedPilotReadbackPhraseologySubject` with compact template-
   specific assertions that select exactly one supported template at a time
   (`LineUpReadback`, `FrequencyReadback`) and verify the final token is that
   aircraft's callsign. The test must fail if either supported template is not
   observed.
3. Add an `Icao9432PhraseologyEvidenceTest` case using existing LOWG evidence
   generation. The test should cite the new source and prove both supported
   rendered readbacks terminate with the callsign independently.
4. Update programme docs/manifests honestly:
   `split: supported rendered readbacks covered; remaining readback templates phraseology-later`.
5. Run focused phraseology/evidence tests, detekt, broad regression, flow
   validation, implementation review, and completion review.

## Review considerations
- FP / type safety: no production state fields or sealed branches should be
  added. The DSL assertion should inspect typed `PhraseologyToken` lists, not
  parse strings. No `else` branches or `error()` paths are expected.
- Test architecture: this is a high-level source-mapped evidence test over the
  existing evidence adapter. It should prove the rendered fact shape rather
  than unit-testing renderer internals. It must be non-vacuous: both currently
  supported readback templates must be present and checked separately.
- Impact: the change should couple only the evidence catalog, evidence DSL,
  phraseology evidence tests, and coverage docs. It should not widen the
  phraseology renderer's operational surface.
- Operational correctness: the regulatory claim is ICAO Doc 9432 §2.8.3.7.
  The coverage is only for supported rendered readback examples currently
  emitted by the sim/evidence harness.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `scripts/ralph/flowctl validate --epic fn-81-icao-9432-phrase-1-readback-termination --json`

## Acceptance
- [ ] `ICAO9432.ReadbackContinuation.ReadbackTerminatesWithCallsign` cites
  `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::4c808d67d281ff71`
  with `RenderedPhraseologyTrace` scope and source metadata identifying ICAO
  Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §2.8.3.7.
- [ ] The evidence DSL can assert, per named supported rendered readback
  template, that the final token is the aircraft callsign token.
- [ ] A source-mapped evidence test proves the invariant for the currently
  supported rendered readback templates: `LineUpReadback` and
  `FrequencyReadback`. The test fails if either template is absent.
- [ ] The exact chunk-01 docs listed in Scope record a split status rather than
  a full universal readback closure.
- [ ] No operational sim behaviour changes.
- [ ] Focused tests, detekt, broad regression, and flow validation are recorded
  in the task Evidence section; implementation review and completion review
  are recorded as `.flow/.impl-review-receipt-fn81.json` and
  `.flow/.completion-review-receipt-fn81.json`.

## References
- ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §2.8.3.7
- `research/txt/icao9432-extracted.txt`
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/RadioPhraseology.kt`
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432PhraseologyEvidenceTest.kt`
