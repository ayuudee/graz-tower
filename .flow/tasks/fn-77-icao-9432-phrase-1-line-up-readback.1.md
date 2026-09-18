## Description

Close only `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::a93888a25f0bad03` by adding rendered phraseology evidence for the ICAO 9432 §4.5.3 line-up exchange: controller `LINE UP AND WAIT`, pilot `LINING UP [callsign]`.

Required work:

- Add typed controller phraseology support for `LineUpAndWait`.
- Add narrowly scoped typed pilot-readback phraseology support for single-atom `LineUpReadback` readbacks.
- Project rendered pilot readback facts from clear pilot transmissions and add DSL selectors for the new readback evidence.
- Add `ICAO9432.TakeoffProcedures.LineUpAndWaitPhrase` with `RenderedPhraseologyTrace` scope and catalog coverage.
- Add a LOWG source-backed sim evidence test asserting controller line-up and pilot line-up readback rendered phraseology.
- Update chunk 04 docs, central implementation manifest, and fn-77 manifest/self-assessment.

Review considerations:

- FP / type safety: use typed templates/tokens/results, not raw string matching as the source of truth.
- Test architecture: source movement depends on a real sim trace; unit tests only support renderer/projection details.
- Impact: no controller, pilot, mission, or timing behaviour changes.
- Operational correctness: cite ICAO Doc 9432 §4.5.3 and avoid closing adjacent phraseology rows.

## Acceptance

- Source-backed test fails if controller `LINE UP AND WAIT` phraseology or pilot `LINING UP` readback phraseology is missing/wrong.
- Unsupported readback shapes are not silently treated as supported.
- Chunk 04 counts become `covered-green` 3, `policy-blocked` 9, `model-gap` 1, `phraseology-later` 6.
- Focused tests, detekt, broad tests, flow validation, impl review, completion review, and `git diff --check` pass before push.

## Done summary
fn-77 closed ICAO 9432 §4.5.3 line-up/readback phraseology as source-backed rendered evidence. Added typed LineUpAndWait controller renderer, narrow LineUpReadback pilot renderer, evidence projection/selectors, catalog ref, LOWG source-backed test, docs, central manifest, movement manifest, and self-assessment. Plan and implementation reviews returned SHIP.
## Evidence
- Commits:
- Tests:
- PRs: