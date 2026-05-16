# Source Processing Queue

Generated: 2026-05-16T12:06:25Z

## Counts

- Manifest windows: `313`
- Landed manifest windows: `310`
- Manifest windows ready for Ollama ingestion: `0`
- Pending curation records: `203`
- High-priority ledger rows needing exact source-window hardening: `0`

## Ready To Ingest By Document

| Document | Windows |
| --- | ---: |

## Next Command

Validate the batch without contacting Ollama:

```bash
nix-shell -p python3 --run "python3 research/tools/requirements-spike/ingest_section_batch.py \
  --batch-manifest research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-15-fn20-final-v7/ready_to_ingest_batch.json \
  --dry-run"
```

Run the exact manifest-only batch with:

```bash
export RUN_ROOT="$HOME/requirements-source-units-$(date +%Y-%m-%d)"
nix-shell -p python3 --run "python3 research/tools/requirements-spike/ingest_section_batch.py \
  --batch-manifest research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-15-fn20-final-v7/ready_to_ingest_batch.json \
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
