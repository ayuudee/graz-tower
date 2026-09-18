# fn-79-icao-9432-phrase-1-takeoff-clearance ICAO 9432 PHRASE-1 takeoff-clearance phraseology evidence

## Overview

Close the remaining chunk-04 base take-off clearance phraseology source unit:

- `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3`

The source claim is the rendered ICAO Doc 9432 §4.5 take-off clearance form:
`RUNWAY [designator] CLEARED FOR TAKE-OFF`.

This epic turns existing renderer support into source-mapped evidence. It does
not change controller behaviour and does not re-open fn-76's distinct source
unit about stating the runway number where confusion is possible.

## Scope

In scope:

- Add an `ICAO9432.TakeoffProcedures.TakeoffClearancePhrase` source ref with
  `RenderedPhraseologyTrace` scope.
- Add a source-mapped LOWG rendered phraseology assertion for OE-ABC runway 16C
  proving `RUNWAY`, runway designator, `CLEARED`, `FOR`, `TAKE`, `OFF`.
- Update catalog tests and chunk-04 programme manifests so only
  `13264a6ac6d529c3` moves from `phraseology-later` to `covered-green`
  rendered phraseology.
- Keep residual PHRASE-1 rows visible.

Out of scope:

- No controller selection, pilot behaviour, runway-duty, or clearance lifecycle
  changes.
- No closure for immediate-departure line-up/readiness query phraseology,
  taxi-ambiguity phraseology, conditional-clearance order, stop-immediately
  repetition, or policy/model-blocked chunk-04 rows.
- No claim that runway-confusion risk is operationally detected; fn-76 remains
  the declared-branch proof for that separate row.

## Approach

1. Verify the accepted source row text and section mapping against
   `research/txt/icao9432-extracted.txt` before catalog/test edits. The row's
   source quote is the §4.5 example line `G-CD RUNWAY 06 CLEARED FOR TAKE-OFF`.
2. Add the source ref to `EvidenceSourceCatalog`.
3. Add it to the catalog test's complete expected id set and
   `RenderedPhraseologyTrace` expected scope set.
4. Extend `Icao9432PhraseologyEvidenceTest` with a source-mapped assertion over
   the existing LOWG circuit-training trace using
   `renderedPhraseology(aircraft).takeoffClearance(RunwayId("16C"))`.
5. Update chunk-04 `source_plan.md`, `coverage_report.md`, and the central
   implementation blocker manifest.
6. Add a short fn-79 manifest and self-assessment recording the movement and
   residual scope.
7. Run focused tests, detekt, broad regression, flow validation, implementation
   review, completion review, then commit and push.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`

## Acceptance

- [ ] The exact source unit
  `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3`
  is present in the typed source catalog.
- [ ] A source-mapped test proves the rendered take-off clearance template over
  a real LOWG trace.
- [ ] Chunk-04 documentation and the central manifest move only this row to
  `covered-green` rendered phraseology.
- [ ] Residual PHRASE-1/policy/model-gap rows remain explicitly blocked.
- [ ] Flow plan review, implementation review, completion review, and validation
  artifacts are recorded.

## Review Considerations

- FP / type safety: no new production state or nullable branch is required. The
  source catalog remains typed by `EvidenceSourceRef`; existing rendered
  phraseology `when` coverage is reused.
- Test architecture: the evidence test should assert token structure through the
  DSL, not merely that `ClearedForTakeoff` was emitted.
- Impact: this should be a source-catalog/docs/test movement over existing
  rendering behaviour, with no operational behaviour changes.
- Operational correctness: the cited claim is ICAO Doc 9432 §4.5 take-off
  clearance wording. The row is separate from fn-76's confusion-risk runway
  number rule and must not overclaim operational activation.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_04_runway_departure/source_plan.md`
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_04_runway_departure/coverage_report.md`
- `research/txt/icao9432-extracted.txt`
