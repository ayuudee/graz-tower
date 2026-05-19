# fn-41-spike-twenty-evidence-mapped-cases.2 Implement twenty-case evidence-mapped suite

## Description
TBD

## Acceptance
- [ ] TBD

## Done summary
Implemented a 20-case evidence-mapped suite: 12 synthetic source-mapped protocol/readback cases plus 8 LOWG scenario cases covering source-mapped ordering, golden completion, invariant ordering, and two explicit expected gaps. The focused suite passes.
## Evidence
- Commits:
- Tests: nix-shell --run ./gradlew :sim:jvmTest --tests xyz.easiersaid.twr.sim.EvidenceMappedTwentyCaseSpikeTest
- PRs: