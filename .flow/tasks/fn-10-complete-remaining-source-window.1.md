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
Completed the 82-row source-window hardening pass. Current superseding queue is source_processing_queue_2026-05-09-fn10-windowed-v5: 232 manifest windows, 185 ready-to-ingest windows, 0 current hardening rows, 0 unresolved old rows, 0 unexpected outcomes. v5 supersedes earlier v1-v4 queues after live validation split CAP413 4.24-4.33 into narrower manifest windows.
## Evidence
- Commits:
- Tests: python3 research/tools/requirements-spike/quality/source_section_ledger/source_section_ledger_2026-04-30/build_source_section_ledger.py, python3 research/tools/requirements-spike/build_source_processing_queue.py --output-dir research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn10-windowed-v5, python3 research/tools/requirements-spike/ingest_section_batch.py --batch-manifest research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn10-windowed-v5/ready_to_ingest_batch.json --output-root /tmp/requirements-source-units-fn10-2026-05-09-v5 --dry-run
- PRs: