# fn-10-complete-remaining-source-window.2 Ingest and promote hardened windows

## Description
Run the ready-to-ingest batch produced by task 1 through the Ollama-backed section processor, using the verified manifest-only queue under `source_processing_queue_2026-05-09-fn10-windowed`. Promote successful outputs through the existing registry gate without bypassing failures or silently accepting malformed candidates.

## Acceptance
- [ ] Re-anchor on the task 1 queue and dry-run result before live processing.
- [ ] Live ingestion uses the exact generated `ready_to_ingest_batch.json` and records the output root.
- [ ] Any failed section is explicit in the batch summary with error class and message.
- [ ] Promotion is run only against a verified run root from this batch.
- [ ] Registry state is regenerated or refreshed so landed, pending, and rejected records reflect the promoted outputs.
- [ ] A fresh source-processing queue/status report is generated after promotion.

## Review Considerations
FP / type safety: malformed model JSON must remain a hard failure or typed pending/rejection state; no broad catch-all acceptance.

Test architecture: live batch summary, promotion output, and regenerated queue counts are required evidence.

Impact: this task expands the registry substantially and may create many pending records; task 3 owns curation, not hidden filtering here.

Operational correctness: every promoted unit must retain document ID, section ID, source line range, and authority ceiling from the manifest.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
