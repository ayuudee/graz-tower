---
satisfies: [R6, R7]
---

## Description
Build the current per-source packages after closure review, then prepare and
red-team the next-source gap-analysis plan for Nolan and EPPLS. This task should
produce the usable structured-output product for the current frame without
prematurely integrating sources.

**Size:** M

**Files:**
- `research/tools/requirements-spike/registry/ollama_first/`
- `research/tools/requirements-spike/quality/`
- `research/tools/requirements-spike/documents/`
- `research/txt/`
- `wiki/data-sources/`
- `.flow/specs/fn-9-current-source-unit-production-readiness.md`

## Approach
- Use the closure review from task 5 as the hard gate for package readiness.
- Create a single package root for this run under the requirements-spike quality
  artifacts, named by timestamp or run id.
- Emit a package manifest, one package JSON per included source, and a
  validation report.
- For each current source, build a package containing schema name/version,
  document id, source path, included manifest sections, package status
  (`ready`, `scoped_ready`, or `blocked`), source units, provenance, validation
  evidence, blockers, and explicit residual gaps or non-claims.
- Keep packages per-source. Do not merge, deduplicate across sources, or choose
  final regulatory precedence in this task.
- If a small package writer is needed, keep it narrowly scoped to packaging the
  current registry artifacts. Do not build an open-ended general-purpose tool.
- Add or run a deterministic package validation command that reconciles package
  inventory against task 5 closure classifications, live registry counts, and
  the final three-level status report. If no validator exists and none is added,
  package completion is blocked.
- Regenerate the status report after package creation and verify it matches the
  package inventory.
- Prepare the Nolan and EPPLS gap-analysis plan only after current package
  readiness is known. The plan should define inventory, relevance assessment,
  extraction boundaries, validation, and red-team review.
- Independently red-team the Nolan/EPPLS plan in this task. Resolve findings in
  the plan or record them as blockers before marking R7 satisfied.

## Investigation Targets
**Required** (read before execution):
- Task 5 closure review and ready/blocked source classifications.
- Current records under `registry/ollama_first/candidates/`,
  `pending/`, and `rejected/`.
- Current document manifests under `documents/`.
- Current text extracts under `research/txt/`.

**Optional** (reference as needed):
- `wiki/data-sources/` pages for source inventory notes.
- Existing package/downstream artifacts under `research/tools/requirements-spike/downstream/`.

## Package Artifact Contract
Package root contents:

- `package_manifest.json` - schema/version, generated time, source-frame id,
  package list, closure review reference, final status report reference, and
  validation report reference.
- `<documentId>.source_package.json` - one file per included source, with
  package status, source units, provenance, validation evidence, blockers, and
  non-claims.
- `validation_report.json` - deterministic reconciliation of package manifest,
  package files, closure classifications, final status report, and live registry
  counts.

A package with status `ready` must have no unresolved failed windows, no
unresolved provenance-mismatched pending records, no blocked quote/source audit,
and no hidden H01 scope claim. Anything less is `scoped_ready` or `blocked`.

## Key Context
The package is the product for this stage. It should be boring and auditable:
one source at a time, with explicit evidence and non-claims. Integration comes
later.

## Review Considerations
FP / type safety: package status must be explicit and total. A source is ready,
scoped-ready, or blocked; missing fields should fail package generation rather
than producing incomplete JSON.

Test architecture: package inventory must reconcile with registry counts,
status report counts, and closure-review classifications. Run cheap registry
gates after packaging if any tracked artifacts changed.

Impact: downstream consumers may treat these packages as trusted source-unit
inputs. The package metadata must make limitations hard to miss.

Operational correctness: every packaged regulatory or phraseology unit must
retain document/section/window provenance. Nolan/EPPLS planning must preserve
the same citation requirement.

## Acceptance
- [ ] Every current source is represented by a package or by an explicit
      blocked-source record explaining why no package was built.
- [ ] Each package contains source units, source/document metadata,
      provenance, validation evidence, package status, and residual gaps or
      non-claims.
- [ ] The package root contains `package_manifest.json`, one per-source package
      JSON for each included source, and `validation_report.json`.
- [ ] Package inventory reconciles with task 5 closure classifications and the
      final three-level status report.
- [ ] No package claims cross-source integration, final precedence, or full
      corpus completeness.
- [ ] Package construction fails loudly or blocks for missing required fields,
      malformed records, unresolved failed windows, blocked quote/source audit,
      or unresolved hard blockers.
- [ ] A deterministic package validation command is run, or package completion
      is blocked because no validator exists.
- [ ] A Nolan and EPPLS gap-analysis plan is drafted after package readiness is
      known, independently red-teamed, and updated or blocked based on the
      red-team findings before those sources are ingested.
- [ ] The final status report is included in the task evidence.

## Done summary
Built and validated the current-frame per-source package set, then drafted and red-teamed the Nolan/EPPLS gap-analysis plan.

Package output contains 8 source packages and 431 accepted source units. Validation reconciled package inventory against task 5 closure classifications, live registry counts, and the final three-level status report. Nolan is available for later inventory as background/conceptual support; EPPLS is intake-blocked because no EPPLS PDF/text extract is present in the repo.
## Evidence
- Commits:
- Tests: python3 research/tools/requirements-spike/build_source_processing_queue.py --output-dir research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn9-final, python3 research/tools/requirements-spike/build_source_unit_packages.py --status-report research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn9-final/source_progress_report.json --output-dir research/tools/requirements-spike/quality/source_packages/source_packages_2026-05-09-fn9-current-frame, python3 -m py_compile research/tools/requirements-spike/build_source_processing_queue.py research/tools/requirements-spike/ingest_section_batch.py research/tools/requirements-spike/build_source_unit_packages.py research/tools/requirements-spike/promote_to_registry.py research/tools/requirements-spike/audit_registry_reproducibility.py research/tools/requirements-spike/candidate_schema.py, python3 research/tools/requirements-spike/test_quality_gates.py, python3 research/tools/requirements-spike/test_override_contracts.py, python3 research/tools/requirements-spike/audit_registry_reproducibility.py --report /tmp/fn9-task6-reproducibility-report.json, bash .flow/bin/flowctl validate --epic fn-9-current-source-unit-production-readiness --json
- PRs: