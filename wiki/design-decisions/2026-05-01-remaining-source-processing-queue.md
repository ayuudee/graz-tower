# Remaining Source Processing Queue

Date: 2026-05-01

## Decision

Treat the next requirements-registry widening step as a fixed exact-window
batch, not as a document-level ingestion. The current queue is generated from
three sources of truth:

- live section manifests under `research/tools/requirements-spike/documents/`;
- accepted/pending/rejected registry records under
  `research/tools/requirements-spike/registry/ollama_first/`;
- the source-section disposition ledger at
  `research/tools/requirements-spike/quality/source_section_ledger/source_section_ledger_2026-04-30/`.

Queue artifact:

- `research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-01/source_processing_queue.md`

## Result

The refreshed boundary is:

- 46 exact manifest windows exist.
- 30 exact manifest windows have accepted registry records.
- 16 exact manifest windows are ready for Ollama-backed ingestion.
- 26 already-produced records remain in `pending/` and need curation, not
  another ingestion pass.
- 83 high-priority non-manifest ledger rows remain potential future extraction
  work, but they need exact line windows before section processing.

The durable runner for the 16-window queue is
`research/tools/requirements-spike/ingest_section_batch.py`. It validates that
the batch still matches `documents/*.json` before contacting Ollama, and its
dry-run passed for the 2026-05-01 queue.

## Implication

The immediate way forward is to run the 16-window batch, promote it, audit it,
curate pending records, and snapshot the resulting `judgements.csv`. The 83
non-manifest high-priority rows are a later manifest-hardening exercise rather
than part of this queue.

## Review Considerations

FP / type safety: no Kotlin/domain code changed.

Test architecture: confidence comes from deterministic ledger regeneration,
queue JSON generation, Python syntax checks, and the batch dry-run manifest
validator. The live Ollama pass still needs the normal promotion and
reproducibility audits.

Impact: downstream consumers now have an explicit ready queue and no longer
need to infer remaining work from stale "22-window" or "44-section" scope
statements.

Operational correctness: no new ATC rule claim is made. The queue only selects
already-manifested source windows for translation by the existing section
processor.
