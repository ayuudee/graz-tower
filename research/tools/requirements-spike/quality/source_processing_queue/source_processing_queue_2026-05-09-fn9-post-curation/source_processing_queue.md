# Source Processing Queue

Generated: 2026-05-09T11:06:16Z

## Counts

- Manifest windows: `47`
- Landed manifest windows: `47`
- Manifest windows ready for Ollama ingestion: `0`
- Pending curation records: `0`
- High-priority ledger rows needing exact source-window hardening: `82`

## Ready To Ingest By Document

| Document | Windows |
| --- | ---: |

## Next Command

Validate the batch without contacting Ollama:

```bash
nix-shell -p python3 --run "python3 research/tools/requirements-spike/ingest_section_batch.py \
  --batch-manifest /home/andrew/dev/projects/twr2/research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn9-post-curation/ready_to_ingest_batch.json \
  --dry-run"
```

Run the exact manifest-only batch with:

```bash
nix-shell -p python3 --run "python3 research/tools/requirements-spike/ingest_section_batch.py \
  --batch-manifest /home/andrew/dev/projects/twr2/research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn9-post-curation/ready_to_ingest_batch.json \
  --output-root /tmp/requirements-source-units-$(date +%Y-%m-%d)"
```

Then promote and audit:

```bash
nix-shell -p python3 --run "python3 research/tools/requirements-spike/promote_to_registry.py \
  --source-run-root /tmp/requirements-source-units-$(date +%Y-%m-%d)"
nix-shell -p python3 --run "python3 research/tools/requirements-spike/audit_registry_reproducibility.py"
```

Then follow Phase G in `research/tools/requirements-spike/RUNBOOK.md` for any `pending/` records and snapshot `quality/judgements.csv`.

Do not queue `needsSourceWindowHardening` rows directly; they still need exact line windows in `documents/*.json` first.
