# fn-51-icao-9432-chunk-02-radio-procedures-and.2 Classify chunk 02 coverage states and expected gaps

## Description

Turn the verified inventory into an honest chunk-02 source plan:
`candidate-test`, `expected-gap`, `phraseology-later`,
`policy-blocked`, or `not-applicable`.

The expected starting point is 11 pure `PHRASE-1` rows, 1 split radio-test
duration/phraseology row, 2 `FN43-GAP-2` rows, and 1 `POLICY-1` row.
Reclassify only when current code/evidence proves the old label wrong.

## Acceptance

- [x] Each of the 15 rows has a planned coverage state and a short reason.
- [x] All `PHRASE-1` rows remain phraseology-later unless a real rendered-transmission evidence layer already exists.
- [x] All `POLICY-1` rows remain policy-blocked unless a typed policy concept already exists.
- [x] The two `FN43-GAP-2` rows are split into candidate-test, expected-gap, or policy-blocked-with-observation-support based only on already-existing current evidence surfaces.
- [x] The critical-phase row is classified as observation-only support plus policy-blocked unless typed `CriticalPhaseTransmissionPolicy` already exists.
- [x] `test_procedures_2_8_4_en::d67d1f63cbbecd7d` is split into a duration obligation and a phraseology/callsign obligation, or the artifact states why the split cannot be made.
- [x] `expected_gaps.md` is created with blocker ids, source-unit ids, and repair-epic recommendation if needed.

## Review Considerations

FP / type safety: use closed vocabulary for planned states in the artifact. Avoid prose-only state labels that cannot be counted later.

Test architecture: candidate-test means "there is a real evidence path to assert"; expected-gap means no real evidence path exists yet.

Impact: this task defines the wall between test authoring and implementation repair. Do not let it hide production gaps as green tests or add new evidence facts inside the coverage epic.

Operational correctness: policy-sensitive wording such as "normally", "may", and "unless necessary for safety" must not become a universal sim law.

## Done summary
Classified chunk-02 planned coverage states. Kept 11 pure phraseology rows blocked by PHRASE-1, classified the critical-phase row as observation-support plus policy-blocked, kept readability scale policy-blocked, split d67d radio-test-signal into duration vs phraseology/callsign obligations, and recorded expected gaps.
## Evidence
- Commits:
- Tests: Light current-code evidence scout: existing CriticalPhaseWindow/CriticalPhaseTransmission selectors found; no source-mapped engine-start approval/start evidence fact identified.
- PRs: research/tools/requirements-spike/quality/icao9432_programme/chunk_02_radio_procedures_policy/source_plan.md, research/tools/requirements-spike/quality/icao9432_programme/chunk_02_radio_procedures_policy/expected_gaps.md