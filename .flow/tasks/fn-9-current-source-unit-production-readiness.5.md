---
satisfies: [R5]
---

## Description
Run the adequacy and closure review for the current source-unit frame. This is
the independent confidence pass before package construction.

**Size:** M

**Files:**
- `research/tools/requirements-spike/build_registry_adequacy_review.py`
- `research/tools/requirements-spike/audit_quotes.py`
- `research/tools/requirements-spike/audit_registry_reproducibility.py`
- `research/tools/requirements-spike/audit_overrides_load_bearing.py`
- `research/tools/requirements-spike/test_quality_gates.py`
- `research/tools/requirements-spike/test_override_contracts.py`
- `research/tools/requirements-spike/quality/adequacy/`
- `research/tools/requirements-spike/quality/coverage/`
- `research/tools/requirements-spike/quality/snapshots/`
- `research/tools/requirements-spike/registry/ollama_first/`

## Approach
- Regenerate the status report after tasks 2, 3, and 4.
- Run deterministic gates first: quality gates, override contracts,
  reproducibility, and quote/source traceability where the required output root
  is available.
- Treat a blocked quote/source audit as a readiness constraint. Affected
  sources may be `scoped-package-ready` only if the missing audit is explicitly
  non-critical and documented; otherwise they are `blocked`.
- Build a fresh adequacy review sample for the current registry state.
- Review the sample against the source windows, paying attention to high-risk
  tags: bilingual text, dialogue examples, nested parent/child clauses,
  background/support-only text, authority-modality mismatches, override-fired
  records, manual splits, and records previously kept pending.
- Confirm all known blockers are either resolved or carried forward as explicit
  package non-claims.
- Produce a closure note that says which sources are package-ready,
  scoped-package-ready, and blocked, with evidence.

## Investigation Targets
**Required** (read before execution):
- Outputs from tasks 1 through 4.
- `research/tools/requirements-spike/build_registry_adequacy_review.py` -
  deterministic sample generation and risk tags.
- `research/tools/requirements-spike/audit_quotes.py` - quote/source audit and
  output-root requirements.
- `research/tools/requirements-spike/audit_registry_reproducibility.py` -
  registry consistency gate.
- `research/tools/requirements-spike/test_quality_gates.py` and
  `test_override_contracts.py` - baseline quality checks.

**Optional** (reference as needed):
- Prior adequacy reviews under `quality/adequacy/`.
- Prior coverage material under `quality/coverage/`.

## Key Context
This is the 80/20 confidence pass the user asked for, not a second full manual
translation. It should be independent enough to catch false confidence, stale
counts, source drift, and high-impact extraction misses.

## Review Considerations
FP / type safety: quality scripts must fail loudly for malformed records,
unknown statuses, and schema drift. Do not paper over failures with exclusion
sets.

Test architecture: deterministic gates are mandatory when runnable. Sampling is
additional evidence, not a substitute for reproducibility and quote/source
audits.

Impact: package construction depends on this task. A source with unresolved
hard blockers can still be represented, but only as blocked or scoped.

Operational correctness: the review must verify exact citations for regulatory
and phraseology claims in sampled records. Any uncited ATC-law claim is
unverified.

## Acceptance
- [ ] Current post-retry/post-curation/post-H01 status is regenerated and
      included in evidence.
- [ ] Quality gates, override-contract tests, registry reproducibility, and
      quote/source audit are run or each blocked command is recorded with its
      concrete failure.
- [ ] A blocked quote/source audit prevents full `package-ready` status for the
      affected source; the closure note marks it `scoped-package-ready` or
      `blocked` with the reason.
- [ ] A fresh adequacy review pack is produced for the current registry state.
- [ ] The review covers count reconciliation, failure ledgers, source
      traceability, high-risk samples, and residual gaps.
- [ ] Each source is classified as package-ready, scoped-package-ready, or
      blocked with evidence.
- [ ] No package-ready source has unresolved failed windows, unresolved
      provenance-mismatched pending records, blocked quote/source audit, or
      hidden H01 scope claims.

## Done summary
Completed the current-frame adequacy and closure review. Deterministic gates pass, the fresh adequacy pack was generated and reviewed, and the closure report classifies each source as `package-ready` or `scoped-package-ready`.

No current-frame source is blocked by pending records, failed manifest windows, quote/source misses, or reproducibility mismatch. Sources with high-priority hardening backlog are explicitly scoped rather than full-document-complete.
## Evidence
- Commits:
- Tests: python3 research/tools/requirements-spike/build_registry_adequacy_review.py --output-dir research/tools/requirements-spike/quality/adequacy/adequacy_2026-05-09-fn9-current-frame --seed fn9-current-frame-2026-05-09 --record-sample-size 48 --section-sample-size 12, python3 research/tools/requirements-spike/audit_quotes.py --registry-root research/tools/requirements-spike/registry/ollama_first, python3 research/tools/requirements-spike/test_quality_gates.py, python3 research/tools/requirements-spike/test_override_contracts.py, python3 research/tools/requirements-spike/audit_registry_reproducibility.py --report /tmp/fn9-task5-reproducibility-report.json, bash .flow/bin/flowctl validate --epic fn-9-current-source-unit-production-readiness --json
- PRs: