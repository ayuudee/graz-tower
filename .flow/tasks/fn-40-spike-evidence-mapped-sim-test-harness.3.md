# fn-40-spike-evidence-mapped-sim-test-harness.3 Review both spikes and recommend path

## Description
TBD

## Acceptance
- [ ] TBD

## Done summary
Reviewed both spikes. The recommendation is to proceed with evidence-mapped tests over a narrow SimObservation anti-corruption port, judged by a terse public DSL rather than internal harness size. The mini monitor architecture remains useful as an extraction direction, but its current call site leaks too much machinery.
## Evidence
- Commits:
- Tests: nix-shell --run ./gradlew :sim:jvmTest --tests EvidenceMappedHarnessSpikeTest --tests EvidenceMappedFacadeSpikeTest --tests MiniConformanceMonitorSpikeTest
- PRs: