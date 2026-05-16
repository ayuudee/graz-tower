# FN10 Source-Unit Ingest Close-Out and Remaining-Source Review

## Goal & Context
Finish the one-off FN10 source-unit ingestion stream with evidence that the produced output is a faithful, auditable description of the underlying source units. The current v6 ingest root is `/Users/andrew/requirements-source-units-fn10-2026-05-11-v6`; it has already completed most of the declared current-frame queue and is currently running a focused retry pass over remaining partial windows.

This epic is not a product feature and not a long-lived ingestion platform. It is a close-out and QA stream for this specific run, plus a final source-scope check for the two remaining sources called out by prior gap analysis: EPPLS and Nolan.

## Architecture & Data Models
Use the existing requirements-spike artifacts and registry flow:

- Raw ingest root: `/Users/andrew/requirements-source-units-fn10-2026-05-11-v6`.
- Queue manifest: `research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-11-fn10-windowed-v6/ready_to_ingest_batch.json`.
- Raw-root audit artifacts:
  - `/Users/andrew/requirements-source-units-fn10-2026-05-11-v6/raw_ingest_consistency_audit.json`
  - `/Users/andrew/requirements-source-units-fn10-2026-05-11-v6/raw_ingest_consistency_audit.md`
- Nolan source evidence currently present:
  - `research/pdf/fundies.pdf`
  - `research/txt/nolan-fundamentals-extracted.txt`
- EPPLS source evidence is not currently present under an EPPLS name in `research/pdf/` or `research/txt/`; intake must first identify whether one of the anonymous PDFs is EPPLS, or else record EPPLS as blocked.

Raw-root consistency means every complete section has readable JSON, matching `run_manifest.json` and `judged_candidates.json` counts, matching source window provenance, and no duplicate candidate IDs. Failed or partial sections remain loud; they are not promoted as complete.

## API Contracts
No application API changes.

Operational commands and artifacts are the contract:

- Use `.flow/bin/flowctl` for task tracking.
- Use `ingest_section_batch.py` for retrying the v6 queue against the existing durable root; complete sections must be strict-skipped.
- Use raw-root consistency audit before any promotion.
- Use existing promotion/registry audit tooling only after raw-root audit is green for the complete subset.
- For EPPLS/Nolan, produce source-review artifacts under `research/tools/requirements-spike/quality/next_sources/` or another dated quality directory, with exact paths, identity evidence, line ranges, and inclusion/exclusion rationale.

## Edge Cases & Constraints
- Duplicate workers previously wrote to the same v6 root. The current raw-root audit passes for the 171 complete sections, but any final promotion must be preceded by a fresh raw-root audit.
- `failures.json` can contain stale entries for sections that later completed. Treat strict-complete artifacts as source of completion truth; keep stale failure entries visible in audit warnings.
- Do not silently repair or suppress malformed model output. Schema repair must be audited; failures remain explicit.
- Nolan is a textbook/background source. It must not be promoted as legal or phraseology authority unless a unit is explicitly tied to a primary cited regulation, and that dependency is recorded.
- EPPLS cannot be ingested unless the source file and edition are positively identified. If absent, the correct output is a loud blocker, not an invented manifest.
- EPPLS Chapter 12, if found, should be reviewed for ingestable radio/phraseology/source units with exact line provenance and source-local authority ceiling.

## Acceptance Criteria
- [ ] The active v6 retry pass has finished or been stopped deliberately, with current status recorded.
- [ ] Raw-root consistency audit is rerun after the retry pass and passes for all strict-complete sections.
- [ ] Remaining failed/partial windows are classified as transient JSON/schema, oversized source-window, pipeline bug, or by-design not worth further retry.
- [ ] Complete v6 sections are promoted or packaged only after passing raw-root audit and existing registry/quote/schema gates.
- [ ] Final close-out report states complete/failed counts by source, candidate counts, accepted/advisory/review counts, and non-claims.
- [ ] EPPLS intake is resolved: either Chapter 12 is identified and inventoried for candidate ingestion windows, or EPPLS is marked blocked with searched paths and evidence.
- [ ] Nolan is inventoried with a relevance pass and either scoped background-support windows are created, or a no-ingest decision is recorded with rationale.
- [ ] Wiki and `.plan` are updated if this close-out resolves or defers known source-scope work.

## Boundaries
- Do not integrate Nolan or EPPLS records with CAP/ICAO/SERA/H01 precedence decisions in this epic.
- Do not claim full-corpus completeness beyond the explicitly audited current-frame plus any explicitly accepted EPPLS/Nolan windows.
- Do not hand-edit model outputs into accepted records unless a separate manual repair audit trail is created using existing manual-repair conventions.
- Do not run multiple ingest workers against the same output root.

## Decision Context
The v6 run is close enough to completion that audit-first close-out is more valuable than continuing broad experimentation. The goal is a trustworthy one-off artifact: complete sections can be accepted if they pass deterministic consistency and registry gates; failed sections should remain loud and classified. EPPLS and Nolan were previously excluded future sources, but this close-out should re-check them so the final non-claims are intentional and evidence-backed.

## Review Considerations

**FP / type safety:** The close-out treats section state as total and explicit: complete, failed/partial, blocked, or out-of-scope. No catch-all success states are allowed. Model schema normalization remains audited; invalid type-valid states should be represented in reports rather than hidden.

**Test architecture:** Raw-root audit is the required pre-promotion test for this run. Promotion must then run existing quote/schema/registry reproducibility gates. EPPLS/Nolan source-review artifacts need deterministic input evidence and line-range provenance so later reviewers can reproduce inclusion/exclusion decisions.

**Impact:** This produces a reliable one-off source-unit dataset and a documented boundary for sources not included. It also prevents the duplicate-worker incident from contaminating promotion by requiring a raw-root audit before registry movement.

**Operational correctness:** ATC law and phraseology claims remain source-bound. ICAO/SERA/CAP/H01 records keep their source authority. Nolan is background support by default. EPPLS authority is unknown until the document identity and edition are verified; Chapter 12 can only be ingested with exact document/section/line citations.
