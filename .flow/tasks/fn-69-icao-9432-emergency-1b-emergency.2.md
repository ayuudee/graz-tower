# fn-69-icao-9432-emergency-1b-emergency.2 Implement emergency priority and silence evidence

## Description
Implement the approved fn-69 evidence surface. Prefer local/source-mapped
evidence unless impact review requires production state. Add high-level
source-backed tests that distinguish emergency priority order and radio-silence
lifecycle from ordinary radio serialization.

## Acceptance
- [x] Distress > urgency > routine priority is represented by closed ordering.
- [x] Emergency-active frequency silence state distinguishes involved from
  uninvolved stations.
- [x] Silence imposition and termination/reversal are both represented and
  tested.
- [x] Ordinary radio serialization/collision does not satisfy priority/silence
  claims.
- [x] Source-backed tests move only declared rows or split branches.
- [x] Focused tests pass.

## Done summary
Implemented fn-69 source-backed priority/silence projection tests and updated chunk-08 ledgers.
## Evidence
- Commits:
- Tests: ./gradlew-nix :sim:jvmTest --tests *.Icao9432EmergencyPrioritySilenceSourceBackedTest --tests *.Icao9432ModelGapSourceUnitSpecTest --tests *.EvidenceSourceCatalogTest
- PRs: