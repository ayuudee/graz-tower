# fn-60-icao-9432-policy-1-typed-policy.3 Implement minimal typed policy evidence and proof set

## Description
Implement only the minimal typed policy surface selected by the matrix and impact assessment. Add high-level source-mapped tests for configured policy branches and regression tests that wrong policy branches do not satisfy those source units. Do not create broad default doctrine or green model-gap/emergency/vehicle/pushback units merely because policy types exist.
## Acceptance
- [ ] Policy types are closed, typed, and cite/trace to source-unit policy concepts.
- [ ] Configured branch tests are high-level scenario/evidence tests, not low-level enum tests.
- [ ] Wrong-path policy branch tests fail or remain blocked rather than passing silently.
- [ ] No source unit outside the movement manifest changes state.
- [ ] No catch-all default policy is treated as universal law.
- [ ] All new state fields, if any, have mutation/copy-site audit and reversal semantics reviewed before forward-path implementation.
## Done summary
Implemented a minimal typed policy substrate and evidence selector. Added configured-policy assertions to the two green-targeted LOWG source-backed scenarios, updated programme coverage artifacts, and kept runtime controller behavior unchanged.
## Evidence
- Commits:
- Tests: ./gradlew-nix :sim:jvmTest --tests '*.EvidenceDslTest' --tests '*.Icao9432TaxiSourceBackedScenarioTest' --tests '*.Icao9432Chunk04RunwayDepartureEvidenceTest', ./gradlew-nix :sim:jvmTest --tests '*.Icao9432*' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest', ./gradlew-nix detekt, git diff --check
- PRs: