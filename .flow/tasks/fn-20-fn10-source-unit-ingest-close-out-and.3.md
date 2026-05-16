# fn-20-fn10-source-unit-ingest-close-out-and.3 Classify remaining failed source windows

## Description
TBD

## Acceptance
- Every remaining partial/failed section has a classification and recommended action.
- Classifications distinguish transient model JSON/schema, oversized source window, pipeline bug, and by-design/out-of-scope.
- Any deferred issue is added to .plan or the final close-out report.


## Done summary
Classified the remaining apparent failures as superseded broad windows or stale interrupted partial directories. The final manifest has 0 failed-window retry candidates and 0 ready-to-ingest windows; stale partial dirs remain visible in the raw audit warnings and split classification.
## Evidence
- Commits:
- Tests:
- PRs: