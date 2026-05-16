# fn-20-fn10-source-unit-ingest-close-out-and.1 Monitor v6 retry pass to completion

## Description
TBD

## Acceptance
- Retry pass has ended or been deliberately stopped.
- Latest complete/partial counts are recorded.
- No more than one ingest worker is running against the v6 root.
- Remaining sections are identified for audit/classification.


## Done summary
Final all-source v7 ingest completed against /Users/andrew/requirements-source-units-fn10-2026-05-11-v6. The final pass processed the six remaining SERA split windows, skipped 260 strict-complete sections, and ended with 0 failed sections. Strict raw completeness is 266/266.
## Evidence
- Commits:
- Tests:
- PRs: