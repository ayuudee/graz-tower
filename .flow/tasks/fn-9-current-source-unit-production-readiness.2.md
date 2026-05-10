---
satisfies: [R2]
---

## Description
Retry or repair the failed source windows identified by the stabilized
baseline. The goal is not to widen the corpus; it is to turn each known failed
window into ingested output, a deliberate rejection, or a loud blocking defect.

**Size:** M/L depending on Ollama runtime and failure shape

**Files:**
- `research/tools/requirements-spike/ingest_section_batch.py`
- `research/tools/requirements-spike/ingest_document.py`
- `research/tools/requirements-spike/run_icao4444_ollama_first_prototype.py`
- `research/tools/requirements-spike/promote_to_registry.py`
- `research/tools/requirements-spike/documents/`
- `research/tools/requirements-spike/quality/source_processing_queue/`
- `research/tools/requirements-spike/quality/`

## Approach
- Start from the exact failed-window list produced by task 1.
- Build a retry batch containing only those failed windows, unless task 1
  proves a window was already ingested correctly after the failure report.
- Dry-run the batch before contacting Ollama and confirm every retry row still
  matches its manifest source window.
- Run the retry batch into a fresh, explicitly named output root.
- If a retry failure exposes a script defect, fix the defect rather than adding
  skip lists or silently accepting partial candidate output.
- Promote successful retry output through the normal promotion path, preserving
  pending candidates for task 3.
- Preserve failed-section details in a defect ledger with document id, section
  id, error class, raw error, attempted command, and recommended next action.

## Investigation Targets
**Required** (read before execution):
- Task 1 status report and failed-window list.
- `research/tools/requirements-spike/ingest_section_batch.py` - batch
  validation, strict already-ingested check, heartbeat, and failure summaries.
- `research/tools/requirements-spike/ingest_document.py` - document aggregation
  and section summary behavior.
- `research/tools/requirements-spike/run_icao4444_ollama_first_prototype.py` -
  candidate extraction/judgement behavior and known JSON/schema failure paths.
- `research/tools/requirements-spike/promote_to_registry.py` - promotion and
  pending behavior.

**Optional** (reference as needed):
- Previous temp output roots named by task 1.
- Prior batch summaries under `quality/source_processing_queue/`.

## Key Context
The user wants current docs made as complete as possible, not a new thematic
slice. Retrying failed windows is bounded work: it uses known manifest windows
and does not invent new sections.

## Review Considerations
FP / type safety: parser or schema states that are possible must be represented
as failures or typed outcome records. Do not treat missing judged candidates as
success.

Test architecture: dry-run the batch, run the batch, then run registry
reproducibility and any relevant quote/source audit after promotion.

Impact: this task can change registry counts and pending queues. It must leave
enough evidence for task 3 to curate against the correct producing run root.

Operational correctness: no regulatory claim is repaired by paraphrase alone.
Any manually corrected source-unit record must retain exact source quotes and
source references.

## Acceptance
- [ ] The retry batch contains only windows justified by task 1.
- [ ] The retry batch dry-run passes before Ollama processing starts.
- [ ] Every failed window from task 1 is resolved as ingested, explicitly
      rejected as not a valid source-unit window, or recorded as a blocking
      defect with evidence.
- [ ] Successful retry output is promoted through the normal registry path.
- [ ] Any script fixes are covered by the relevant lightweight Python checks or
      by a focused regression check.
- [ ] The post-retry status report is regenerated and shows zero silent
      skipped failed windows.
- [ ] The producing run root for every new pending/candidate record is recorded
      for task 3.

## Done summary
Retry verification complete. Task 1 baseline and post-retry report both show 0 failed-window retry candidates, 0 ready-to-ingest manifest windows, and 0 pending curation records. The ready-to-ingest batch is intentionally empty and now dry-runs successfully with explicit no_sections output. No Ollama retry or promotion was needed; post-retry registry reproducibility still passes.
## Evidence
- Commits:
- Tests: python3 research/tools/requirements-spike/ingest_section_batch.py --batch-manifest research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn9-baseline/ready_to_ingest_batch.json --dry-run, python3 research/tools/requirements-spike/build_source_processing_queue.py --output-dir research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn9-post-retry, python3 research/tools/requirements-spike/audit_registry_reproducibility.py --report /tmp/fn9-post-retry-registry-reproducibility-report.json
- PRs: