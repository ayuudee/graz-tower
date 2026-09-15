# fn-51-icao-9432-chunk-02-radio-procedures-and.5 Close chunk 02 coverage report and programme handoff

## Description

Close chunk 02 with a durable report and handoff for the next chunk. The report
must distinguish actual test coverage from phraseology/policy/observation gaps.

## Acceptance

- [x] `coverage_report.md` records final count by state.
- [x] `source_plan.md` and `expected_gaps.md` agree with the coverage report.
- [x] STRATEGY or programme docs are updated only if the chunk state changes the project-level narrative.
- [x] Flow task/epic state is closed honestly.
- [x] `git diff --check` passes.
- [x] Focused tests for any authored source-mapped tests pass.
- [x] `./gradlew-nix detekt` and relevant module tests pass if production/test Kotlin changed.

## Review Considerations

FP / type safety: reporting should fail loudly on unknown coverage states.

Test architecture: the closeout is not green unless the test reports and chunk artifacts agree.

Impact: this report becomes the next chunk's baseline; stale first-pass classification must not survive as the final claim.

Operational correctness: every non-green row should still point to its exact source-unit id and blocker.

## Done summary
Closed chunk 02 as an honest gap-classification chunk. coverage_report.md records 0 covered-green, 0 covered-red, 1 expected-gap, 1 expected-gap+policy-blocked, 11 phraseology-later, 1 policy-blocked, 1 split row, and 0 not-applicable. Updated STRATEGY and .plan FN43-GAP-2; fn-52 tracks chunk-specific observation/model repairs.
## Evidence
- Commits:
- Tests: git diff --check, No Kotlin tests required: fn-51 changed Flow/docs/research artifacts only and authored no source-mapped tests.
- PRs: research/tools/requirements-spike/quality/icao9432_programme/chunk_02_radio_procedures_policy/coverage_report.md, STRATEGY.md, .plan