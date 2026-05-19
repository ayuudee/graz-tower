# fn-40-spike-evidence-mapped-sim-test-harness.2 Implement mini monitor harness spike

## Description
TBD

## Acceptance
- [ ] TBD

## Done summary
Implemented the mini monitor-first spike over the same SimObservation boundary. It compiles and passes focused tests for synthetic readback, LOWG taxi, touch-and-go, golden completion, and two expected-gap monitors, but the implementation shows materially higher ceremony than the evidence-mapped harness.
## Evidence
- Commits:
- Tests: nix-shell --run ./gradlew :sim:jvmTest --tests xyz.easiersaid.twr.sim.MiniConformanceMonitorSpikeTest
- PRs: