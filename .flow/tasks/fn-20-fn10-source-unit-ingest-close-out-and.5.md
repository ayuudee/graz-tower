# fn-20-fn10-source-unit-ingest-close-out-and.5 Resolve EPPLS Chapter 12 intake

## Description
Resolve EPPLS Chapter 12 intake using the reviewed plan artifacts:

- Plan: `research/tools/requirements-spike/quality/next_sources/eppls_ch12_intake_plan_2026-05-15-fn20/plan.md`
- Review: `research/tools/requirements-spike/quality/next_sources/eppls_ch12_intake_plan_2026-05-15-fn20/review.md`

The source PDF is now present at `research/pdf/EPPLS.pdf`. Intake must verify identity, edition/date, extraction feasibility, and Chapter 12 line ranges before creating any manifest or queue.
## Acceptance
- EPPLS source identity is resolved from repo artifacts or marked blocked.
- If EPPLS Chapter 12 exists, candidate windows are inventoried with exact line ranges and authority ceiling.
- If absent, searched paths and blocker are recorded.


## Done summary
Resolved EPPLS intake. Verified PDF identity and deterministic text extraction, inventoried Chapter 12 line ranges, added a background-support EPPLS manifest with seven candidate windows, and confirmed queue dry-run validation sees eppls-extracted with 7 sections.
## Evidence
- Commits:
- Tests:
- PRs: