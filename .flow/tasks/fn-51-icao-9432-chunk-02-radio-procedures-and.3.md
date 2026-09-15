# fn-51-icao-9432-chunk-02-radio-procedures-and.3 Scout FN43-GAP-2 evidence surfaces for critical-phase and start-up facts

## Description

Inspect the current sim evidence DSL, event trace, and transmission records to
decide whether chunk 02's two `FN43-GAP-2` units can be covered without new
production behaviour:

- Critical-phase transmission restraint:
  `aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4`
- Engine-start-after-approval workflow:
  `aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd`

This task is scouting plus impact assessment. If a new evidence fact is
needed, describe it and route it to a separate repair epic; do not implement it
inside this task.

## Acceptance

- [x] Current evidence facts/selectors are inspected for phase-at-transmission and engine-start approval/start observability.
- [x] Existing sim events and `TransmissionRecord` projections are inspected for reusable facts.
- [x] A short impact assessment is recorded before any new evidence payload or selector is proposed.
- [x] Each `FN43-GAP-2` row is classified as coverable-now or repair-needed.
- [x] The critical-phase row is not declared fully coverable unless a typed safety-exception policy concept exists in addition to phase-at-transmission evidence.
- [x] No production code is changed in this task.

## Review Considerations

FP / type safety: new facts, if proposed, must be typed payloads with exhaustive selectors/adapters.

Test architecture: source-mapped tests should observe real simulation traces, not inject `fromProjectedPayloads` as the primary proof.

Impact: phase-at-transmission facts could affect many chunks. Keep any proposed surface generic enough for later chunks, but create a separate implementation epic before building it.

Operational correctness: critical-phase radio discipline has a safety exception; evidence should observe transmissions during protected phases, not decide policy locally.

## Done summary
Completed FN43/evidence scout. Found CriticalPhaseWindow facts and selector support, but no real CriticalPhaseTransmission projection from sim records; engine-start approval/start has protocol and mission primitives but no source-mapped lifecycle evidence; radio-test duration lacks a typed ground-station test-signal identity. No production code changed.
## Evidence
- Commits:
- Tests: rg scout over EvidenceFacts/EvidenceDsl/EvidenceSourceCatalog/TransmissionRecord/SimEvent/protocol startup primitives
- PRs: research/tools/requirements-spike/quality/icao9432_programme/chunk_02_radio_procedures_policy/fn43_gap2_scout.md