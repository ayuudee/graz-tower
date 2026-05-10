---
satisfies: [R1]
---

## Description
Stabilize the current source-unit frame before any more ingestion or curation.
Produce a fresh, independent status baseline that shows the three levels the
user asked for: source-level progress, section-level progress, and tactical
blockers or next actions.

**Size:** M

**Files:**
- `research/tools/requirements-spike/README.md`
- `research/tools/requirements-spike/RUNBOOK.md`
- `research/tools/requirements-spike/documents/`
- `research/tools/requirements-spike/registry/ollama_first/`
- `research/tools/requirements-spike/quality/`
- `research/tools/requirements-spike/build_source_processing_queue.py`
- `research/tools/requirements-spike/ingest_section_batch.py`
- `research/tools/requirements-spike/promote_to_registry.py`
- `research/tools/requirements-spike/audit_registry_reproducibility.py`
- `research/tools/requirements-spike/audit_quotes.py`

## Approach
- Treat `documents/*.json`, the live registry, source-processing queue output,
  and current quality ledgers as the control surface.
- Create an explicit current-source-frame artifact before reporting progress.
  Default inclusion is every document manifest currently present in the
  requirements-spike document manifests, plus any source already represented in
  the live registry but missing from those manifests.
- Mark Nolan and EPPLS as excluded-future unless the manifest/registry control
  set already includes them. Do not silently treat loose PDFs/text extracts as
  current-frame package obligations.
- Rebuild the source-processing queue from repo state; do not trust stale
  prose counts or an arbitrary newest temp directory.
- Confirm which run roots are actually evidenced by tracked quality artifacts
  or by existing local outputs before using them in later tasks.
- If the existing queue/status tooling does not emit the required three-level
  report, make that gap loud in the task evidence and either extend the report
  narrowly or block this task. Do not proceed with an ambiguous baseline.

## Investigation Targets
**Required** (read before execution):
- `research/tools/requirements-spike/README.md` - current source-unit pipeline
  contract and documented commands.
- `research/tools/requirements-spike/RUNBOOK.md` - promotion, curation, and
  snapshot workflow.
- `research/tools/requirements-spike/documents/*.json` - current manifest
  sections and source windows.
- `research/tools/requirements-spike/quality/source_processing_queue/` -
  previous queue outputs and status shape.
- `research/tools/requirements-spike/quality/snapshots/` - latest judgement
  snapshots and chronology.
- `research/tools/requirements-spike/registry/ollama_first/manifest.json` -
  current registry manifest.

**Optional** (reference as needed):
- `wiki/data-sources/`
- `wiki/design-decisions/`
- `docs/design/requirements-registry-clearance-comms-seam-plan-2026-04-30.md`

## Key Context
The baseline is a gate. It must answer what sources are in the current frame,
which sections are complete, which sections remain failed/pending/hardened, and
what tactical action is next for each blocker. It must also identify the exact
failed-window list to be handled by task 2.

The current-source-frame artifact is the anti-drift control: later tasks should
not independently reinterpret what counts as a current source.

## Review Considerations
FP / type safety: if report code changes, status categories must be explicit.
Do not collapse unknown, failed, pending, excluded-future, and skipped into a
single optimistic state.

Test architecture: run the deterministic queue/status command and the cheap
registry reproducibility gate. Record command failures as unverified evidence,
not as skipped work.

Impact: this task controls all later work. A stale or vague baseline can make
later packages falsely appear complete.

Operational correctness: any source-unit coverage claims must point back to a
document id and section/window. Do not make uncited ATC-law claims from memory.

## Acceptance
- [ ] A current-source-frame artifact is generated, listing each included,
      excluded-future, and blocked-ambiguous source with the evidence for that
      classification.
- [ ] A fresh status report is generated from current repo state and included
      in the task evidence.
- [ ] The report shows source-level, section-level, and tactical progress for
      every included current-frame source.
- [ ] The report explicitly excludes Nolan and EPPLS from the current-frame
      package claim unless they are already present in the manifest/registry
      control set.
- [ ] The exact failed-window list for task 2 is enumerated and reconciled
      with the expected count of 14 or the discrepancy is explained with
      evidence.
- [ ] Every run root that later tasks may use is either verified to exist and
      match current records, or marked unavailable; no later task depends on
      an inferred newest temp directory.
- [ ] `audit_registry_reproducibility.py` is run or recorded as blocked with
      the concrete command and failure.
- [ ] The next ready tasks after this one are clear: failed-window retry and
      H01 resolution.

## Done summary
Baseline stabilized. Added current-source-frame and three-level source progress report outputs to the queue builder, including relative output-dir handling. Generated research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn9-baseline/: 8 included current-frame sources, 2 excluded future sources (Nolan/EPPLS), 46/46 manifest windows landed, 0 pending curation records, 0 failed-window retry candidates, and 83 high-priority source-window-hardening rows. Reconciled the prior expected 14 failed windows as stale against current manifest/registry state. Empty ready-to-ingest batches now return an explicit no_sections success.
## Evidence
- Commits:
- Tests: python3 -m py_compile research/tools/requirements-spike/build_source_processing_queue.py research/tools/requirements-spike/ingest_section_batch.py, python3 research/tools/requirements-spike/test_quality_gates.py, python3 research/tools/requirements-spike/test_override_contracts.py, python3 research/tools/requirements-spike/audit_registry_reproducibility.py --report /tmp/fn9-registry-reproducibility-report.json, python3 research/tools/requirements-spike/ingest_section_batch.py --batch-manifest research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn9-baseline/ready_to_ingest_batch.json --dry-run, bash .flow/bin/flowctl validate --epic fn-9-current-source-unit-production-readiness --json
- PRs: