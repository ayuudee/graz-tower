---
satisfies: [R4]
---

## Description
Resolve the H01 coverage blocker. H01 is bilingual and has known risk around
German/English duplicated clauses, authority ceiling, and prior translation
judgements. It cannot be package-complete unless that risk is checked or the
package carries an explicit non-claim.

**Size:** M

**Files:**
- `research/tools/requirements-spike/documents/h01.json`
- `research/txt/h01-extracted.txt`
- `research/tools/requirements-spike/benchmark_manifest.json`
- `research/tools/requirements-spike/benchmark_judgements_2026-04-23.csv`
- `research/tools/requirements-spike/registry/ollama_first/`
- `research/tools/requirements-spike/quality/`
- `research/tools/requirements-spike/run_icao4444_ollama_first_prototype.py`

## Approach
- Inventory H01 manifest sections and compare them to the source text windows.
- Identify whether each bilingual duplicated clause has both German and English
  text represented, intentionally scoped to one language, or intentionally
  non-claimed.
- Review existing H01 candidates for authority ceiling and modality consistency
  against the H01 source family.
- Pay special attention to prior judgement notes that mention translation
  mismatch or mistranslation.
- If the right fix is re-ingestion, produce a bounded H01-only manifest or
  retry batch and route the output through normal promotion.
- If H01 work creates pending records, leave them present for task 3; task 3 is
  deliberately blocked on this task so H01 cannot race the general curation
  pass.
- If the right fix is a non-claim, make it explicit in the source package
  readiness evidence and current status report.

## Investigation Targets
**Required** (read before execution):
- `research/tools/requirements-spike/documents/h01.json` - H01 sections,
  line ranges, and notes.
- `research/txt/h01-extracted.txt` - source text for H01 windows.
- `research/tools/requirements-spike/benchmark_manifest.json` - legacy H01
  benchmark cases and source-family ceiling.
- `research/tools/requirements-spike/benchmark_judgements_2026-04-23.csv` -
  prior H01 judgement evidence.
- Current H01 records in `registry/ollama_first/`.

**Optional** (reference as needed):
- Existing H01 curation artifacts under `quality/curation/`.
- Adequacy review source windows that include H01.

## Key Context
This is a source-specific blocker, not a request to integrate H01 with CAP 413,
ICAO, or SERA. H01 may end as package-ready with a clear scope limitation, but
it must not imply bilingual completeness unless the evidence supports that.

Task 3 depends on this task so H01-created pending records are curated in the
same provenance-controlled pass as other pending records.

## Review Considerations
FP / type safety: H01 status must distinguish `bilingual_checked`,
`english_only_claim`, `german_only_claim`, `scoped_nonclaim`, and `blocked`.
Do not use a boolean complete/incomplete flag if it hides the reason.

Test architecture: run schema/quality gates after any H01 record changes and
include a deterministic H01 sample in adequacy review.

Impact: H01 package readiness depends on this task. Later source integration
can use H01 only if its language scope is clear.

Operational correctness: all H01 readback/acknowledgement claims must cite AIC
A 21/23 H01 section and line/window evidence. Do not infer German phraseology
from English text without checking the source.

## Acceptance
- [ ] H01 manifest sections are reconciled against the H01 source text windows.
- [ ] Bilingual duplicated clauses are classified as checked, intentionally
      language-scoped, or blocked.
- [ ] Existing H01 accepted/pending/rejected records are reviewed for authority
      ceiling and modality consistency.
- [ ] Any required H01 re-ingestion or promotion is routed through the same
      evidence rules as tasks 2 and 3.
- [ ] Any H01 pending records created or left by this task are explicitly
      listed for task 3 curation.
- [ ] If H01 remains scoped rather than complete, the non-claim is explicit in
      the status report and package readiness evidence.
- [ ] Post-H01 quality/reproducibility checks pass or failures are recorded as
      package blockers.

## Done summary
Resolved the H01 current-frame blocker by reviewing the manifested H01 source windows, correcting source-family authority metadata, and splitting the accidental §3.9 overlap out of §3.8.3.

Key outputs:
- H01 accepted/rejected records now respect the H01 `operational_guidance` source-family ceiling.
- `corrections_3_8_3` now ends at line 4465; `assurance_rtf_frequencies_3_9` covers lines 4467-4485.
- H01 readiness evidence explicitly scopes the package to English-side source units and disclaims German/English translation-equivalence and full-document H01 completeness.
- No H01 pending records remain for task 3.
## Evidence
- Commits:
- Tests: python3 -m py_compile research/tools/requirements-spike/build_source_processing_queue.py research/tools/requirements-spike/ingest_section_batch.py research/tools/requirements-spike/promote_to_registry.py research/tools/requirements-spike/audit_registry_reproducibility.py research/tools/requirements-spike/candidate_schema.py, python3 research/tools/requirements-spike/audit_registry_reproducibility.py --report /tmp/fn9-h01-final-reproducibility-report.json, python3 research/tools/requirements-spike/test_quality_gates.py, python3 research/tools/requirements-spike/test_override_contracts.py, bash .flow/bin/flowctl validate --epic fn-9-current-source-unit-production-readiness --json
- PRs: