# fn-40-spike-evidence-mapped-sim-test-harness.1 Implement evidence-mapped harness spike

## Description
TBD

## Acceptance
- [ ] TBD

## Done summary
Implemented the evidence-mapped harness spike with a narrow SimObservation anti-corruption boundary, typed samples, source/golden bases, typed outcomes, synthetic protocol observation, LOWG sim observation, and focused tests covering readback, taxi, touch-and-go, two expected gaps, and one golden assertion.
## Evidence
- Commits:
- Tests: nix-shell --run ./gradlew :sim:jvmTest --tests xyz.easiersaid.twr.sim.EvidenceMappedHarnessSpikeTest
- PRs: