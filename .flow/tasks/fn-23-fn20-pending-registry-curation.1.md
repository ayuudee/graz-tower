# fn-23-fn20-pending-registry-curation.1 Preflight curation credentials and pending queue

## Description
TBD

## Acceptance
- API key prerequisite is checked without printing secrets.
- Pending counts by document are recorded.
- Dry-run smoke command is attempted when credentials exist, or the task is blocked with the exact missing prerequisite.


## Done summary
Verified curation credentials and ran a 3-record dry-run smoke. OPENAI_API_KEY loads from .env after removing a pasted prompt line. Dry run returned valid high-confidence curator actions: 2 promote, 1 reject. Pending queue before non-dry smoke: 1356 records.
## Evidence
- Commits:
- Tests:
- PRs: