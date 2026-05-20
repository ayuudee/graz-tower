# fn-45-restore-broad-sim-jvm-suite.3 Repair reactive go-around golden failures

## Description
TBD

## Acceptance
- [ ] TBD

## Done summary
Repaired reactive go-around behaviour by adding attempt-scoped go-around witnesses, suppressing duplicate same-attempt GA interventions/instructions, extending the bounded go-around belief timeout, and narrowing reactive golden assertions to the authored exceedance/named-witness windows. Stale spike source refs were replaced with accepted registry ids.
## Evidence
- Commits:
- Tests:
- PRs: