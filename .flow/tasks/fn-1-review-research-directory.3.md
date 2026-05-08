---
satisfies: [R3]
---

## Description
Audit `research/tools/requirements-spike` for declared-slice discipline, source-ledger consistency, regulatory-source traceability, and existing gate coverage. The review must not widen the registry or manually reinterpret the corpus; it checks whether the current claims match the declared artifacts and scripts.

**Size:** M
**Files:** `research/tools/requirements-spike/`, `research/pdf/`, `research/txt/`, `wiki/data-sources/`, `wiki/design-decisions/`, `docs/design/requirements-registry-clearance-comms-seam-plan-2026-04-30.md`

## Approach
- Use `DECLARED_SLICE.md`, the source-section ledger, and the source inventory as the live scope contract.
- Treat dated decision records as historical unless they are still presented as current status.
- Reuse existing quality/audit scripts and report command failures loudly.
- Require registry reproducibility as a cheap current-registry gate.
- Require quote/source traceability evidence. `audit_quotes.py` needs a per-document ingest output root; use only an output root explicitly produced during the review or named by tracked run/status evidence and confirmed to exist. Do not infer an arbitrary newest temp directory. If no such root exists, record the missing output root as a blocker rather than silently skipping quote audit. Regenerating ingest output is out of scope unless the reviewer deliberately runs the documented ingest flow as verification evidence.
- Check regulatory and phraseology claims for exact source references, not memory-based summaries.

## Investigation targets
**Required** (read before coding):
- `research/tools/requirements-spike/README.md:5` - current declared-slice direction.
- `research/tools/requirements-spike/RUNBOOK.md:12` - scope rule and ingest flow.
- `research/tools/requirements-spike/registry/ollama_first/DECLARED_SLICE.md:5` - scope contract and non-claims.
- `research/tools/requirements-spike/quality/source_section_ledger/source_section_ledger_2026-04-30/source_section_ledger.md:11` - current ledger counts.
- `research/tools/requirements-spike/quality/source_inventory/source_inventory_2026-04-29/source_document_inventory.md:5` - source/PDF/text inventory and declared coverage.
- `wiki/design-decisions/2026-04-30-declared-slice-and-section-ledger.md:26` - known historical count-drift checkpoint.
- `research/tools/requirements-spike/test_quality_gates.py:1` - quality gate entrypoint.
- `research/tools/requirements-spike/test_override_contracts.py:1` - override contract tests.
- `research/tools/requirements-spike/audit_registry_reproducibility.py:1` - registry reproducibility audit.
- `research/tools/requirements-spike/audit_quotes.py:1` - quote/source audit and required output-root behavior.

**Optional** (reference as needed):
- `wiki/data-sources/research-txt-atc-review-corpus.md:25` - corpus/source family treatment.
- `docs/design/requirements-registry-clearance-comms-seam-plan-2026-04-30.md:31` - current seam plan.

## Key context
The registry is declared as a 22-window slice, not full-document or full-corpus coverage. Count differences between live ledgers and dated decisions should be treated as review findings only when they make a current claim false or leave the current source of truth ambiguous.

For regulatory traceability sampling, use a deterministic minimum: at least one accepted record per manifested source family or authority class, plus any records that current docs cite as examples. If the registry shape prevents that sample, record the blocker and sample the closest deterministic set with an explanation.

## Acceptance
- [ ] The review verifies that registry docs still state the declared-slice non-claims and do not imply full-document/full-corpus extraction.
- [ ] Live ledger, source inventory, declared-slice, status snapshot, and relevant wiki/design records are checked for count or scope drift, with dated records treated as historical unless they are used as current guidance.
- [ ] Existing Python gates are run or explicitly marked unverified: `test_quality_gates.py`, `test_override_contracts.py`, and `audit_registry_reproducibility.py --report /tmp/research-registry-reproducibility-report.json`.
- [ ] Quote/source traceability is audited with `audit_quotes.py --output-root <documented-ingest-output-root>` when a documented ingest output root exists by the lookup rule in Approach; if it is unavailable, the missing output root is recorded as a verification blocker and finding.
- [ ] Regulatory/phraseology traceability uses the deterministic minimum sample described in Key context, includes exact source references, and flags uncited or out-of-slice claims.
- [ ] Any generated audit report is recorded as review evidence and routed by task 5 rather than left as an ambiguous repo-local artifact.
- [ ] Findings identify whether each issue is a scope-contract defect, gate failure, documentation drift, source-traceability blocker, or future-widening decision.

## Done summary
Requirements registry audit complete. The requested nix-shell Python command could not run because <nixpkgs> is unavailable, but the repository scripts pass with the available Nix-store Python: 91 quality-gate tests passed, 13 override-contract tests passed, and audit_registry_reproducibility.py reported 259 audited records with 0 unexpected files and 0 mismatches. Scope documents correctly state this is not full-document or full-corpus coverage. A deterministic quote traceability sample across all 8 documents plus authority-class fillers found 0 quote misses. The full audit_quotes.py lane is blocked as a reproducible repo-local check because it requires a per-document output root containing accepted_candidates.json files, and none exists under research/tools/requirements-spike. There is also count drift: DECLARED_SLICE still says 22 manifest windows and 243 accepted records, while the live source-section ledger now says 476 rows and 41 exact manifest-window rows; the dated wiki decision records older ledger counts of 457 rows and 22 exact rows.
## Evidence
- Commits:
- Tests:
- PRs: