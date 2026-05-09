# Ollama-first lane — runbook

The sections below are user-driven because they require Ollama at
`biggy:11434` (or whatever `--base-url` you pass) and/or a frontier-LLM
API key (OpenAI / Anthropic) for the curation step. All offline tooling
is exercised by `test_quality_gates.py` + `test_override_contracts.py`
(104 tests total: 91 quality-gate + 13 override-contract).

The end-to-end flow when ingesting fresh content is:
**ingest → promote → curate → audit → snapshot**. Steps in detail below.

## Scope rule before any ingestion

The promoted registry is the landed 46-window slice described in
`registry/ollama_first/DECLARED_SLICE.md`. The live document manifests now
contain 46 sections, all of which have passed the standard ingest, promote,
curate, audit, and snapshot flow. The source-section disposition ledger is
`quality/source_section_ledger/source_section_ledger_2026-04-30/source_section_ledger.md`.
Do not describe the registry as full-document or full-corpus coverage.

Before adding or widening any source section:

1. Add or update the section's row in the source-section ledger.
2. Add the explicit line range to `documents/{documentId}.json`.
3. Run the normal ingest, promote, curate, audit, and snapshot flow.
4. Update `DECLARED_SLICE.md`, the registry status snapshot, and the relevant
   quality/source-inventory or source-section-ledger artifact, making clear
   which manifest windows have actually landed in `candidates/`.

This keeps omissions visible: a source section is either extracted,
support-only, duplicate/subset material, out of scope, or explicitly deferred
with a reason.

## Landed clearance/comms queue — 2026-05-04

The exact-window queue that was current on 2026-05-01 is now landed:

- `quality/source_processing_queue/source_processing_queue_2026-05-01/source_processing_queue.md`
- `quality/source_processing_queue/source_processing_queue_2026-05-01/ready_to_ingest_batch.json`

It contained 16 manifest windows for the Ollama-backed section processor:
8 CAP 413 windows, 7 ICAO Doc 4444 windows, and 1 ICAO Doc 9432 window. Those
windows produced 130 judged candidates, then promotion and curation moved the
registry to 423 accepted candidates, 0 pending records, and 34 rejected records.
RR-21 adequacy adjudication then added eight corrected accepted replacements,
bringing the current registry to 431 accepted candidates, 0 pending records,
and 34 rejected records.

Before starting the live Ollama pass, validate the batch manifest against the
current `documents/*.json` line ranges:

```bash
nix-shell -p python3 --run "python3 research/tools/requirements-spike/ingest_section_batch.py \
  --batch-manifest research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-01/ready_to_ingest_batch.json \
  --dry-run"
```

Then run the remaining exact windows:

```bash
nix-shell -p python3 --run "python3 research/tools/requirements-spike/ingest_section_batch.py \
  --batch-manifest research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-01/ready_to_ingest_batch.json \
  --output-root /tmp/requirements-source-units-$(date +%Y-%m-%d)"
```

Promote and audit the batch output:

```bash
nix-shell -p python3 --run "python3 research/tools/requirements-spike/promote_to_registry.py \
  --source-run-root /tmp/requirements-source-units-$(date +%Y-%m-%d)"
nix-shell -p python3 --run "python3 research/tools/requirements-spike/audit_registry_reproducibility.py"
```

After promotion, Phase G curation resolved all pending records. The current
post-curation snapshot was
`quality/snapshots/judgements-2026-05-04-post-clearance-comms.csv`.
The current post-RR-21 adequacy snapshot is
`quality/snapshots/judgements-2026-05-04-post-rr21-adequacy.csv`.

## Phase E — Re-ingest the corpus through the gates

This is the first full corpus pass after the gates and registry landed.
It is the moment when canonicalIds become real identifiers and downstream
consumers can begin to dereference them.

### Steps

```bash
PYTHON=/path/to/python3
ROOT="$(git rev-parse --show-toplevel)"
SCRATCH=/tmp/ollama-first-phase-e-$(date +%Y-%m-%d)

# 1. Drive ingestion across every document manifest under documents/.
$PYTHON $ROOT/research/tools/requirements-spike/ingest_all_documents.py \
    --output-root $SCRATCH

# 2. Promote the scratch output through the gates into the registry.
$PYTHON $ROOT/research/tools/requirements-spike/promote_to_registry.py \
    --source-run-root $SCRATCH

# 3. Confirm the registry round-trips cleanly (no manual edits drifted IDs).
$PYTHON $ROOT/research/tools/requirements-spike/audit_registry_reproducibility.py

# 3b. Confirm accepted registry quotes remain verbatim in source text.
$PYTHON $ROOT/research/tools/requirements-spike/audit_quotes.py \
    --registry-root $ROOT/research/tools/requirements-spike/registry/ollama_first

# 4. Snapshot the outcome CSV as the regression-detector baseline.
#    `quality/` and `quality/snapshots/` are pre-created so this works
#    even if step 2 emitted no rows (which would mean step 2 is broken,
#    but the snapshot copy itself shouldn't add a confusing failure
#    on top).
mkdir -p $ROOT/research/tools/requirements-spike/quality/snapshots
cp $ROOT/research/tools/requirements-spike/quality/judgements.csv \
   $ROOT/research/tools/requirements-spike/quality/snapshots/judgements-$(date +%Y-%m-%d).csv
```

## Phase G — Curation pass (frontier-LLM second opinion on `pending/`)

The qwen3.6 judge demotes conservatively because it sees only a single
candidate plus its own source window. A frontier LLM (gpt-5.x / Claude)
with the full registry context — sibling candidates already accepted,
override audit trail, judge rationale, full source — can resolve most
of the demotions deterministically.

The Phase E pass left ~37% of candidates in `pending/`; the Phase G
curation pass typically resolves ~95% of those automatically with high
confidence (37 promote / 21 promote_with_corrections / 28 reject in the
2026-04-29 run).

```bash
set -a; source $ROOT/.env; set +a
$PYTHON $ROOT/research/tools/requirements-spike/curate_pending_with_gpt.py \
    --source-run-root $SCRATCH \
    --provider openai          # or `anthropic`
    --model gpt-5.5            # or `claude-opus-4-7`
    [--limit 3 --dry-run]      # smoke test first if you've changed prompts
    [--filter-document <id>]   # mop up after a transient API failure
```

Allowed actions per record (constrained via OpenAI `response_format` or
Anthropic tool-use):

- `promote` — judge was over-cautious; move pending → candidates as-is.
- `promote_with_corrections` — change `modality` / `authorityClass` /
  `requirementKind` only (NEVER `claimText` or `exactSourceQuotes` —
  those are identity fields), re-run G2+G3, then move to candidates.
  If the corrections still fail a gate, action silently demotes to
  `keep_pending`.
- `reject` — claim not in source, or umbrella whose atomic items
  already cover it. Move to rejected/.
- `keep_pending` — genuinely ambiguous; record stays with annotation.

Each curation event writes a per-record audit JSON to
`quality/curation/{runId}/{canonicalId}.json` containing the full
context bundle, the LLM response, and the applied action.
It also appends a post-curation row to `quality/judgements.csv` with
`eventSource=curator`, so the regression detector's latest-row view
matches the final lifecycle state. The 2026-04-29 pre-RR-12 curation
audit directories have been backfilled into the CSV.

After curating, **refresh the manifest** (the curator doesn't call
`update_manifest` itself):

```bash
$PYTHON -c "
import sys; sys.path.insert(0, '$ROOT/research/tools/requirements-spike')
from promote_to_registry import update_manifest
from pathlib import Path
update_manifest(Path('$ROOT/research/tools/requirements-spike/registry/ollama_first'))
"
$PYTHON $ROOT/research/tools/requirements-spike/audit_registry_reproducibility.py
```

Curator rows preserve rejected candidates' failed gate evidence. The
regression detector hard-fails quote-audit failures only for latest
`accepted` rows; failed quote audits on `rejected` rows are evidence for
the rejection, not a current registry failure.

### What to check after the run

- `registry/ollama_first/manifest.json` — counts per document, per
  modality, per authority class. Compare against the corpus expectation:
  ICAO 4444 will dominate; SafetySense / Slovenia will be small.
- `registry/ollama_first/pending/` — every entry is awaiting decision.
  Run **Phase G** (below) to resolve most of them automatically; the
  residual is genuine human-curation work.
- `quality/parse_failures.json` — should be empty or near-empty. Each
  entry is a section the pipeline could not parse; investigate.
- `runs/{runId}.json` — per-run conflict log. `cross_bucket` and
  `source_drift` records require curation.

### If a section regresses on re-promote

The regression detector is your baseline-vs-current comparator:

```bash
$PYTHON $ROOT/research/tools/requirements-spike/check_ollama_first_regressions.py \
    --baseline $ROOT/research/tools/requirements-spike/quality/snapshots/judgements-2026-04-28.csv \
    --current $ROOT/research/tools/requirements-spike/quality/judgements.csv
```

A non-zero exit means at least one previously-accepted candidate flipped
to non-accepted with identical models AND identical verdict-affecting
prompt SHAs (challenge / bundleGate / judge). That's a pure stochastic
regression and the override pattern should catch it; if it doesn't,
the override is not load-bearing in that case.

## RR-13 — Adequacy review pack (80/20 statistical check)

Internal gates prove traceability, schema consistency, and reproducible
registry identity. They do not prove the registry is a complete and
honest translation of the ingested source corpus. Use this pack to give
an independent reviewer a bounded sample to check.

```bash
$PYTHON $ROOT/research/tools/requirements-spike/build_registry_adequacy_review.py \
    --output-dir $ROOT/research/tools/requirements-spike/quality/adequacy/adequacy_$(date +%Y-%m-%d)-rr13-80-20
```

The 2026-04-29 fixed-seed pack is available at
`quality/adequacy/adequacy_2026-04-29-rr13-80-20/`:

- `record_review.csv` — 48 sampled records across lifecycle bucket,
  document, authority class, modality, testability, and risk tags.
- `section_omission_review.csv` — 12 sampled source sections for
  omission review against source windows.
- `review_pack.md` — human-readable packet with review instructions.
- `source_windows/` — line-numbered source excerpts for the section
  omission checks.

Review verdicts belong in the CSVs. With zero major record errors in 48
records, the rule-of-three upper bound is about 6.3% for major record
error rate in the sampled frame. With zero material omissions in 12
sections, the upper bound is about 25%, so the section sample is a smoke
check for systemic omissions rather than a completeness proof.

The 2026-04-29 same-agent first-pass review is already filled in the
CSV files and summarized in `adequacy_assessment.md`. It found material
translation gaps that were repaired directly; see `repair_summary.md`
and `.plan` RR-14 to RR-16.

RR-17 adds the post-repair coverage pass:

- `quality/coverage/coverage_2026-04-29-rr17-80-20/` — 22-section
  coverage ledger and concept crosswalk.
- `quality/adequacy/adequacy_2026-04-29-rr17-post-repair-resample/` —
  fresh 32-record / 8-section resample after RR-17 coverage repair.
- `quality/snapshots/judgements-2026-04-29-post-rr17-coverage.csv` —
  current regression baseline.

Use `quality/snapshots/judgements-2026-05-04-post-rr21-adequacy.csv`
as the current registry regression baseline. The pre-RR-21
`quality/snapshots/judgements-2026-05-04-post-clearance-comms.csv`
snapshot remains the baseline for detecting regressions introduced by the
adequacy repair pass. The RR-17 snapshot remains the baseline for the older
22-window post-repair adequacy frame, and
`quality/snapshots/judgements-2026-05-01-post-clearance-comms-partial.csv`
is retained only as the partial-run baseline used to check the 2026-05-04
promotion.

## Phase F — Periodic override-sunset audit

Run weekly, or whenever any of `apply_*_override` is edited.

```bash
$PYTHON $ROOT/research/tools/requirements-spike/audit_overrides_load_bearing.py \
    --case safetysense22_readback_family \
    --output-dir /tmp/override-audit-$(date +%Y-%m-%d)
```

Output:

- `loadBearingScore: 0.0` ⇒ overrides made no difference on this case.
  If three consecutive runs across multiple cases score 0, retire the
  override and absorb its logic into the prompts.
- `loadBearingScore > 0` ⇒ overrides are doing real work; keep them.
- A sudden score increase ⇒ underlying models regressed and overrides
  are doing more work than before. Investigate the model release.

Rotate cases each run so we cover the corpus over time.

## How to onboard a new document

1. Add `documents/{newDocumentId}.json` with the section list (look at
   `icao4444.json` for the schema).
2. Run a single-section pre-flight: `ingest_document.py --document
   {id}` against one section to verify the prompts cope with the
   document's structural shape.
3. Promote the pre-flight output: `promote_to_registry.py
   --source-run-dir /tmp/...`. Inspect `pending/` carefully — a new
   document's first section will surface unfamiliar candidate shapes.
4. Iterate prompts if the gates surface false positives. Each prompt
   change bumps `PROMPT_VERSION_SHA` automatically; the regression
   detector's "stable context" check then ignores the affected period.
5. Once a section reads cleanly, ingest the rest of the document.

## Troubleshooting

- **Promoter says `cross_bucket` conflict**: a candidate was previously
  in `candidates/` but the new run wants it in `pending/` (or vice
  versa). Resolve by manually deleting the stale entry, then re-promote.
- **Promoter says `source_drift`**: the source `.txt` file has changed
  since the registry record was promoted (`provenance.sourceSha256`
  differs). Decide: did we re-extract the source on purpose? If yes,
  delete the affected records and re-promote. If no, restore the source.
- **Promoter says `source_drift_unknown`**: a legacy record without a
  recorded `sourceSha256` exists. Delete that record and re-promote;
  the new record will carry full provenance.
- **`audit_registry_reproducibility.py --dry-run-only` fails**: the
  registry has a manual edit that drifted a canonicalId. The mismatch
  kind tells you which check failed; fix the offending record.
