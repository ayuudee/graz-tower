---
satisfies: [R3]
---

## Description
Curate pending source-unit candidates using the correct provenance bundle for
each pending record. The task should reduce resolvable pending records without
promoting unsupported or stale-root candidates.

**Size:** M

**Files:**
- `research/tools/requirements-spike/curate_pending_with_gpt.py`
- `research/tools/requirements-spike/promote_to_registry.py`
- `research/tools/requirements-spike/candidate_schema.py`
- `research/tools/requirements-spike/canonical_id.py`
- `research/tools/requirements-spike/registry/ollama_first/pending/`
- `research/tools/requirements-spike/registry/ollama_first/candidates/`
- `research/tools/requirements-spike/registry/ollama_first/rejected/`
- `research/tools/requirements-spike/quality/curation/`
- `research/tools/requirements-spike/quality/judgements.csv`

## Approach
- Use task 1, task 2, and task 4 evidence to identify the source-run root for
  pending candidates. Do not curate against a stale or guessed temp root.
- Include H01 pending records created or left by task 4 in this pass; this task
  depends on task 4 specifically to avoid a curation race.
- Run a small dry-run or smoke curation first to confirm API credentials,
  schema validation, and provenance bundle loading.
- Use full curation only after the smoke pass proves the correct source windows
  and sibling context are being supplied.
- Accept the curator's conservative outcome rules: promote only when supported,
  reject when unsupported/duplicate, and keep pending when the record needs
  re-extraction or the evidence is insufficient.
- Snapshot `quality/judgements.csv` after curation with a name that identifies
  this task or run.
- Regenerate the source/status report after curation.

## Investigation Targets
**Required** (read before execution):
- `research/tools/requirements-spike/curate_pending_with_gpt.py` - curation
  actions, correction limits, API environment, and audit artifacts.
- `research/tools/requirements-spike/registry/ollama_first/pending/` - current
  pending records.
- Task 2 producing run roots and promotion output.
- Task 4 H01 pending/non-claim evidence.
- `research/tools/requirements-spike/candidate_schema.py` - validation and
  authority/modality invariants.

**Optional** (reference as needed):
- Existing curation runs under `quality/curation/`.
- `research/tools/requirements-spike/quality/snapshots/` for naming precedent.

## Key Context
The product is trustworthy structured output, not maximum promotion. A pending
record with inadequate provenance is still a blocker or rejection candidate; it
is not something to wave through because the text looks plausible.

## Review Considerations
FP / type safety: corrections are limited to non-identity fields supported by
the script. Changes to claim text or exact quotes require re-extraction.

Test architecture: use smoke curation, schema validation, registry
reproducibility, and post-curation status counts. If an API or environment
failure blocks curation, record it loudly.

Impact: this task moves records between pending, candidates, and rejected. The
audit artifacts must preserve why each movement happened.

Operational correctness: authoritative, operational-guidance, best-practice,
and support-only records must retain the right authority/modality ceiling for
their source document.

## Acceptance
- [ ] Every curated pending record is linked to a verified producing run root
      or explicitly left pending/rejected because that provenance is missing.
- [ ] H01 pending records created or left by task 4 are included in this pass,
      or the task evidence proves there are none.
- [ ] A curation smoke pass is run before full curation, or the task records
      why full curation is blocked.
- [ ] Curation audit artifacts are written for all processed records.
- [ ] No record is promoted after changing identity fields such as claim text
      or exact source quotes.
- [ ] Post-curation registry reproducibility passes or fails with recorded
      evidence.
- [ ] A judgement snapshot and refreshed three-level status report are present
      after curation.
- [ ] Remaining pending records, if any, are classified as re-extraction
      blockers, missing-provenance blockers, or deliberate unresolved review
      items.

## Done summary
Completed the curation pass as a verified no-op: task 2 and task 4 left zero pending records in `registry/ollama_first/pending/`, so no LLM curation was run and no record movement was needed.

Preserved the required evidence anyway: pending count was checked as zero, judgements were snapshotted after H01 repairs, the three-level status report was regenerated, and registry reproducibility passed.
## Evidence
- Commits:
- Tests: find research/tools/requirements-spike/registry/ollama_first/pending -type f -name '*.json' 2>/dev/null | wc -l  # 0, python3 research/tools/requirements-spike/build_source_processing_queue.py --output-dir research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn9-post-curation, python3 research/tools/requirements-spike/audit_registry_reproducibility.py --report /tmp/fn9-task3-reproducibility-report.json
- PRs: