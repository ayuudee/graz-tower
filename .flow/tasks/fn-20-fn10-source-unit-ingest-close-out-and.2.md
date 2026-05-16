# fn-20-fn10-source-unit-ingest-close-out-and.2 Rerun raw ingest consistency audit

## Description
TBD

## Acceptance
- Raw-root audit rerun after retry pass.
- Audit reports 0 unreadable JSON and 0 hard consistency issues for strict-complete sections.
- Audit artifacts are preserved under the durable root.


## Done summary
Reran raw-root consistency audit against the preserved batch_manifest_used.json. Audit passed with 266/266 strict-complete sections, 0 unreadable JSON files, and 0 hard consistency issues. It records 22 non-manifest stale partial directories as warnings only.
## Evidence
- Commits:
- Tests:
- PRs: