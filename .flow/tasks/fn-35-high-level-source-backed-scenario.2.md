# fn-35-high-level-source-backed-scenario.2 Add source citation and ledger validation

## Description
TBD

## Acceptance
- [ ] Test or script fails for cited source-unit ids that do not exist in the accepted registry.
- [ ] Covered/partially-covered ledger rows are mechanically checked against test/scenario citations.
- [ ] Validation output is recorded in task evidence.
## Done summary
Added JVM citation validation for source-unit-backed tests. The validator checks that Kotlin SourceUnitRef citations exist in the accepted registry and that FN33 covered/partially-covered ledger rows are backed by Kotlin citations.
## Evidence
- Commits:
- Tests:
- PRs: