# fn-44-pressure-test-source-mapped-evidence.1 Design projection contract and source-unit slice

## Description
Read the FN43 implementation review, the selected source-unit JSON records, and the current evidence fact/harness code. Produce a short design note for the projection contract before implementation.

The design must decide the minimal positive/negative/gap target for each selected source unit and identify the exact facts/selectors needed. It must be opinionated: either implement the fact honestly or keep the source as a narrow typed expected gap. No decorative citations.

The design note should live under `research/tools/requirements-spike/quality/evidence_mapped_test_harness/fn44_projection_pressure_test_2026-05-19/`.

## Acceptance
- [x] Design note names the selected source-unit ids and exact ICAO Doc 9432 sections.
- [x] Design note states which source units are expected to become positive in this epic and which may remain typed gaps.
- [x] Impact assessment covers coupling to sim traces, report adequacy, negative/window evidence, and public DSL ceremony.
- [x] Review considerations cover FP/type safety, test architecture, impact, and operational correctness.
- [x] No code changes are made before the impact/design note exists.

## Done summary
Designed and reviewed the FN44 projection pressure-test slice before implementation. The design selects four ICAO Doc 9432 source units and records the projection contract, impact assessment, and review constraints.
## Evidence
- Commits:
- Tests:
- PRs: