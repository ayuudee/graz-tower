# fn-35-high-level-source-backed-scenario.1 Repair FN33 ledger and synthesis hygiene

## Description
TBD

## Acceptance
- [ ] FN33 synthesis no longer recommends a second source that conflicts with actual FN34 work.
- [ ] The readback conditional-clearance row is no longer overstated as fully covered.
- [ ] Any new status vocabulary is documented in the artifact README/report.
## Done summary
Repaired FN33 hygiene: updated stale second-source synthesis to note the FN34 EPPLS supersession, added partially_covered to the ledger vocabulary, and downgraded the broad readback conditional/other-clearance source unit from covered to partially_covered.
## Evidence
- Commits:
- Tests:
- PRs: