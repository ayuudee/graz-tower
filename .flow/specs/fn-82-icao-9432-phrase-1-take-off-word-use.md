# fn-82-icao-9432-phrase-1-take-off-word-use ICAO 9432 PHRASE-1 take-off word-use phraseology evidence

## Overview
Implement a source-mapped PHRASE-1 evidence slice for ICAO Doc 9432,
Manual of Radiotelephony, Fourth Edition, 2007, §2.8.3.3:

> The word "TAKE OFF" is only used when an aircraft is cleared for take-off,
> or when cancelling a take-off clearance; at other times "DEPARTURE" or
> "AIRBORNE" is used.

The current renderer supports take-off clearance wording but does not support
take-off-clearance cancellation wording. This epic proves the invariant for the
supported rendered controller phraseology surface and records the cancellation
exception as still phraseology/model blocked.

## Scope
- Add an evidence catalog source for
  `icao9432-extracted::readback_2_8_3_en::f06dfa1cefd2d649` with
  `RenderedPhraseologyTrace` scope.
- Add an evidence DSL assertion over rendered controller phraseology facts that
  fails if any supported rendered controller template other than
  `TakeoffClearance` contains `PhraseologyToken.TakeOff`.
- Add a source-mapped test covering all currently supported rendered
  controller templates: use real LOWG circuit evidence for certified runway
  and frequency templates (`ContactFrequencyInstruction`,
  `LineUpAndWaitInstruction`, `TakeoffClearance`,
  `TouchAndGoClearance`) and a synthetic emergency-policy transmission record
  for `StopImmediatelyInstruction`, whose operational trigger remains policy
  blocked.
- Update chunk-01 docs and the blocker manifest with split wording:
  supported rendered controller templates covered; cancellation wording and
  unsupported templates remain PHRASE-1/model blocked.
- Do not alter runtime controller selection, pilot behavior, readback
  generation, or the production phraseology renderer.

## Approach
1. Extend `ICAO9432.Readback` with a phraseology source ref and include it in
   `EvidenceSourceCatalog.All`.
2. Extend `AuditRenderedPhraseologySubject` or add a small aggregate subject
   so tests can assert the `TakeOff` token appears only where allowed. Use
   typed `PhraseologyToken` values, not string parsing.
3. Build a combined test evidence set from the real LOWG touch-and-go trace
   plus a synthetic `StopImmediately` transmission through
   `EvidenceFactAdapters.fromTransmissionRecords`. This avoids bypassing the
   certified runway-clearance path while still making the supported template
   set non-vacuous.
4. Add the source-mapped evidence test to `Icao9432PhraseologyEvidenceTest`.
5. Update chunk-01 programme docs/manifests honestly.
6. Validate with focused tests, detekt, broad regression, flow validation,
   implementation review, and completion review.

## Review considerations
- FP / type safety: no production sealed branches or state fields should be
  added. The assertion must inspect typed tokens and fail loudly for
  unsupported/vacuous evidence.
- Test architecture: this is a high-level evidence test through the fact
  adapter. Real LOWG trace evidence is preferred for certified runway/frequency
  templates; synthetic evidence is limited to `StopImmediately`, matching the
  already documented trigger-policy gap.
- Impact: evidence/test/docs only. No runtime behavior, selection policy, or
  phraseology renderer expansion should occur.
- Operational correctness: the regulatory claim is ICAO Doc 9432, Manual of
  Radiotelephony, Fourth Edition, 2007, §2.8.3.3. The cancellation exception is
  explicitly not closed because no cancellation rendered template exists.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `scripts/ralph/flowctl validate --epic fn-82-icao-9432-phrase-1-take-off-word-use --json`

## Acceptance
- [ ] `ICAO9432.Readback.TakeOffWordUse` cites
  `icao9432-extracted::readback_2_8_3_en::f06dfa1cefd2d649` with
  `RenderedPhraseologyTrace` scope and source metadata identifying ICAO Doc
  9432, Manual of Radiotelephony, Fourth Edition, 2007, §2.8.3.3.
- [ ] The evidence DSL can assert that the `TakeOff` token appears only in
  `TakeoffClearance` among supplied rendered controller phraseology facts.
- [ ] Focused DSL tests cover empty evidence, a missing required template,
  `TakeOff` on a non-`TakeoffClearance` fact, and mixed valid/invalid facts.
- [ ] The test covers every currently supported rendered controller template
  and fails if any supported template is absent from the evidence set.
- [ ] Chunk-01 `source_plan.md`, `coverage_report.md`, `expected_gaps.md`,
  and `implementation_blocker_manifest.csv` record a split status rather than
  a full universal closure.
- [ ] No runtime sim behavior changes.
- [ ] Focused tests, detekt, broad regression, flow validation,
  implementation review, and completion review are recorded.

## References
- ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §2.8.3.3
- `research/txt/icao9432-extracted.txt`
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/RadioPhraseology.kt`
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432PhraseologyEvidenceTest.kt`
