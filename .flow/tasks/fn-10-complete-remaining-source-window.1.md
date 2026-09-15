## Description
Convert the 82 high-priority hardening rows into exact, bounded manifest windows. Avoid broad duplicate chapter windows when a row already has covered subwindows; split large sections into processable windows where needed.

## Acceptance
- [ ] All 82 input rows are reconciled to exact line ranges, manifest windows, or explicit blockers.
- [ ] `documents/*.json` contains the new windows needed for ingestion.
- [ ] The source-section ledger no longer leaves completed rows as unexplained high-priority hardening backlog.
- [ ] A regenerated source-processing queue shows the expected ready-to-ingest windows.
- [ ] Batch dry-run passes for the generated ready-to-ingest manifest.

## Review Considerations
FP / type safety: no implicit catch-all source states; each row must be `manifested`, `already_covered`, or `blocked` with evidence.

Test architecture: queue regeneration and dry-run batch validation are required before live Ollama work.

Impact: later tasks depend on exact windows; over-broad windows can create duplicate source units and candidate caps.

Operational correctness: keep source-specific authority ceilings: ICAO/SERA authoritative, CAP/H01/ICAO9432 operational guidance.

## Done summary
Flow housekeeping: task was operationally completed during the fn10 windowed source-processing pass, as already recorded in this task's Done summary. Later fn-20/fn-23 closeout superseded the queue and curation evidence.
## Evidence
- Commits:
- Tests: Existing task Done summary cites source_processing_queue_2026-05-09-fn10-windowed-v5 with 0 current hardening rows and dry-run validation.
- PRs: Superseding closeout epics: fn-20-fn10-source-unit-ingest-close-out-and, fn-23-fn20-pending-registry-curation.