## Description

Close only `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::8af22eb8d9795cef` from `phraseology-later` to `covered-green` as a declared-branch rendered phraseology closure.

Required work:

- Add permanent `ICAO9432.TakeoffProcedures.RunwayNumberInTakeoffClearance` source ref with `RenderedPhraseologyTrace` scope and include it in the catalog aggregate set validated by `EvidenceSourceCatalogTest`.
- Add a source-backed sim test that runs a LOWG trace, cites the source, declares samples for several-runways/confusion-risk/active-runway, and asserts `renderedPhraseology(AircraftId("OE-ABC")).takeoffClearance(RunwayId("16C"))`.
- Update chunk 04 source plan, expected gaps, coverage report, central blocker manifest, and add `research/tools/requirements-spike/quality/icao9432_programme/fn76_phraseology_runway_number_manifest.md`.
- Keep `13264a6ac6d529c3` phraseology-later / support-only.

Review considerations:

- FP / type safety: use typed `EvidenceSourceRef`, `RenderedPhraseologyTemplate`, and `PhraseologyToken` evidence.
- Test architecture: use integration-style source-backed evidence over projected sim facts.
- Impact: no controller/pilot behaviour changes are expected.
- Operational correctness: ICAO Doc 9432 §4.5.8 is conditional and advisory; multi-runway/confusion-risk condition is declared sample scope, not observed activation evidence.

## Acceptance

- The source-backed test fails for missing rendered phraseology, missing runway designator token, or wrong runway designator.
- Docs state fn-76 does not introduce general confusion-risk policy/model evidence.
- Focused tests pass:
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest'`
- Static and broad verification pass:
  `./gradlew-nix detekt`
  `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `scripts/ralph/flowctl validate --epic fn-76-icao-9432-phrase-1-runway-number --json` passes.
- `git diff --check` passes.

## Done summary
fn-76 closed ICAO 9432 §4.5.8 runway-number takeoff clearance as declared-branch rendered phraseology evidence. Added typed source ref, source-backed LOWG phraseology test, chunk 04 docs, central manifest row, and fn-76 manifest/self-assessment. Impl review returned SHIP after central-manifest correction.
## Evidence
- Commits:
- Tests:
- PRs: