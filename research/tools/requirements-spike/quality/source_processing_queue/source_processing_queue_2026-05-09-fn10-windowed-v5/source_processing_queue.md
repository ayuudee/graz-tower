# Source Processing Queue

Generated: 2026-05-09T19:13:55Z

## Counts

- Manifest windows: `232`
- Landed manifest windows: `47`
- Manifest windows ready for Ollama ingestion: `185`
- Pending curation records: `0`
- High-priority ledger rows needing exact source-window hardening: `0`

## Ready To Ingest By Document

| Document | Windows |
| --- | ---: |
| cap413-extracted | 39 |
| h01-extracted | 42 |
| icao4444-extracted | 77 |
| icao9432-extracted | 19 |
| sera-923-2012-extracted | 8 |

## Next Command

Validate the batch without contacting Ollama:

```bash
nix-shell -p python3 --run "python3 research/tools/requirements-spike/ingest_section_batch.py \
  --batch-manifest research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn10-windowed-v5/ready_to_ingest_batch.json \
  --dry-run"
```

Run the exact manifest-only batch with:

```bash
export RUN_ROOT="$HOME/requirements-source-units-$(date +%Y-%m-%d)"
nix-shell -p python3 --run "python3 research/tools/requirements-spike/ingest_section_batch.py \
  --batch-manifest research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn10-windowed-v5/ready_to_ingest_batch.json \
  --output-root \"$RUN_ROOT\" \
  --heartbeat-seconds 60"
```

Then promote and audit:

```bash
nix-shell -p python3 --run "python3 research/tools/requirements-spike/promote_to_registry.py \
  --source-run-root \"$RUN_ROOT\""
nix-shell -p python3 --run "python3 research/tools/requirements-spike/audit_registry_reproducibility.py"
```

Then follow Phase G in `research/tools/requirements-spike/RUNBOOK.md` for any `pending/` records and snapshot `quality/judgements.csv`.

Do not queue `needsSourceWindowHardening` rows directly; they still need exact line windows in `documents/*.json` first.
