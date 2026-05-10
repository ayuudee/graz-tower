# Mac Mini Ingest Handoff

This is the handoff for Codex on the Mac mini. Read `AGENTS.md` first, then this file. The objective is to keep Ollama busy until the v5 source window queue is ingested, validated, and ready for promotion.

## Current Objective

Translate every remaining current-frame source window into durable source units.

Authoritative queue:

`research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn10-windowed-v5/ready_to_ingest_batch.json`

Current v5 queue status:

- Total queued sections: 185
- `cap413-extracted`: 39
- `h01-extracted`: 42
- `icao4444-extracted`: 77
- `icao9432-extracted`: 19
- `sera-923-2012-extracted`: 8
- Already landed before this queue: 47 manifest windows
- High-priority source-window hardening rows: 0
- Pending curation records: 0

The previous local run on `/tmp` was lost after a reboot. Ignore any remembered `/tmp` completion counts and rerun from the authoritative v5 manifest into a durable directory.

## Hard Rules

- Do not use `/tmp` for the output root. Use a durable directory under `$HOME`.
- Do not run two ingest processes against the same output root.
- If rerunning after an interruption, use the same durable output root. `ingest_section_batch.py` skips strict completed sections and removes/retries incomplete section directories.
- Do not promote a run with failures unless the failures have been reviewed and the user explicitly accepts a partial promotion.
- Do not hide failures with skip lists or manual edits. If a section repeatedly fails, split the source window, regenerate a new queue version, and rerun that queue.
- Keep the three-level status visible after each meaningful chunk: source level, section level, tactical level.

## Preflight

From the repo root on the Mac mini:

```bash
git status --short
git pull --ff-only
```

Validate the queue without contacting Ollama:

```bash
python3 research/tools/requirements-spike/ingest_section_batch.py \
  --batch-manifest research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn10-windowed-v5/ready_to_ingest_batch.json \
  --dry-run
```

Check Ollama. The repo default is `http://biggy:11434`; override `BASE_URL` to `http://localhost:11434` only if Ollama is local to the Mac mini.

```bash
export BASE_URL="${BASE_URL:-http://biggy:11434}"
curl -sS "$BASE_URL/api/tags" | python3 -m json.tool | grep -E 'qwen3.6:35b-a3b|qwen2.5-coder:32b'
```

If the models are not visible, fix Ollama connectivity before starting the batch.

## Launch

Use a dated durable root. If resuming an existing run, reuse the same `RUN_ROOT`.

```bash
export RUN_ROOT="$HOME/requirements-source-units-fn10-2026-05-10-v5"
export BASE_URL="${BASE_URL:-http://biggy:11434}"
mkdir -p "$RUN_ROOT"

setsid python3 research/tools/requirements-spike/ingest_section_batch.py \
  --batch-manifest research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn10-windowed-v5/ready_to_ingest_batch.json \
  --output-root "$RUN_ROOT" \
  --heartbeat-seconds 60 \
  --base-url "$BASE_URL" \
  --json-repair-attempts 3 \
  > "$RUN_ROOT.ingest.log" 2>&1 &

echo "$!" > "$RUN_ROOT.ingest.pid"
```

## Monitor

```bash
ps -p "$(cat "$RUN_ROOT.ingest.pid")" -o pid,etime,stat,command
tail -f "$RUN_ROOT.ingest.log"
```

Useful condensed log view:

```bash
grep -n '^\[doc\]\|^\[run\]\|^\[skip\]\|^\[doc-done\]\|^\[fail\]' "$RUN_ROOT.ingest.log" | tail -120
```

Count durable completed section directories:

```bash
python3 - <<'PY'
import json
import os
from collections import Counter
from pathlib import Path

root = Path(os.environ["RUN_ROOT"])
done = Counter()
partial = []
for doc_dir in sorted(path for path in root.iterdir() if path.is_dir()):
    for section_dir in sorted(path for path in doc_dir.iterdir() if path.is_dir()):
        manifest = section_dir / "run_manifest.json"
        judged = section_dir / "judged_candidates.json"
        if manifest.exists() and judged.exists():
            data = json.loads(judged.read_text())
            if data.get("candidateCount", 0) > 0 and data.get("candidateCount") == data.get("judgedCandidateCount"):
                done[doc_dir.name] += 1
            else:
                partial.append(f"{doc_dir.name}/{section_dir.name}")
        else:
            partial.append(f"{doc_dir.name}/{section_dir.name}")

print("completed by source:")
for doc, count in sorted(done.items()):
    print(f"  {doc}: {count}")
print(f"completed total: {sum(done.values())}")
if partial:
    print("partial:")
    for item in partial[-20:]:
        print(f"  {item}")
PY
```

## Completion Check

The run is complete only when this file exists:

`$RUN_ROOT/batch_run_summary.json`

Inspect it:

```bash
python3 -m json.tool "$RUN_ROOT/batch_run_summary.json" | sed -n '1,260p'
```

Expected accounting:

- `sectionsRequested` is 185.
- `sectionsProcessed + sectionsSkipped + sectionsFailed` equals 185.
- `sectionsFailed` should be 0 before normal promotion.

If the process exits without `batch_run_summary.json`, inspect the last partial section in the log and rerun the same launch command with the same `RUN_ROOT`.

## Known Retry Watchlist

The lost `/tmp` run had these problem sections before the machine rebooted:

- `cap413-extracted/ch4_4_50_to_4_60`: invalid JSON in structure attempt
- `cap413-extracted/ch4_4_84_part1`: timeout
- `cap413-extracted/ch4_4_84_part2`: timeout
- `cap413-extracted/ch4_4_84_part3`: invalid JSON
- `cap413-extracted/ch4_4_136_to_4_143`: active partial when the old run was lost

If these fail again, treat them as source-window sizing problems. Split the relevant window in `research/tools/requirements-spike/documents/cap413.json`, regenerate the source ledger and a new queue version, dry-run it, then launch the new queue against a new durable output root.

## Promotion Path

Only use this path after a clean `batch_run_summary.json`, or after an explicit decision to promote a partial run.

```bash
python3 research/tools/requirements-spike/promote_to_registry.py \
  --source-run-root "$RUN_ROOT"

python3 research/tools/requirements-spike/audit_registry_reproducibility.py \
  --registry-root research/tools/requirements-spike/registry/ollama_first \
  --dry-run-only \
  --report research/tools/requirements-spike/quality/closure/fn10-post-promote-reproducibility.json

python3 research/tools/requirements-spike/audit_quotes.py \
  --registry-root research/tools/requirements-spike/registry/ollama_first

python3 research/tools/requirements-spike/check_ollama_first_regressions.py \
  --baseline research/tools/requirements-spike/quality/snapshots/judgements-2026-05-09-fn9-post-h01-curation-noop.csv \
  --current research/tools/requirements-spike/quality/judgements.csv \
  --report research/tools/requirements-spike/quality/closure/fn10-post-promote-regressions.json

python3 research/tools/requirements-spike/build_source_processing_queue.py \
  --output-dir research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-10-fn10-post-promote

python3 research/tools/requirements-spike/build_source_unit_packages.py
```

If promotion creates pending records, follow the existing requirements-spike curation runbook and keep those pending records visible. Do not call the source complete while pending curation remains.

## Required Status Report To User

After each meaningful chunk, report the three levels:

1. Source level: current source, completed sources, remaining source counts, and excluded future sources (`nolan`, `eppls`).
2. Section level: completed, failed, and remaining section counts per source.
3. Tactical level: PID, log path, output root, active section or last completed section, failures, and next command.

The committed v5 progress report is here:

`research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn10-windowed-v5/source_progress_report.md`

Use it as the baseline, then update counts from the durable run root and `batch_run_summary.json`.

## Commit Discipline

After promotion and validation:

```bash
git status --short
sed -n '1,220p' .plan
git diff --stat
```

Update `.plan` only if this work resolves or creates a known issue. Commit the generated registry, quality, progress, package, and wiki artifacts together with a concise evidence summary.
