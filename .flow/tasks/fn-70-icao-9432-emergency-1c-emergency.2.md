# fn-70-icao-9432-emergency-1c-emergency.2 Implement emergency descent safeguarding evidence

## Description
Implement the approved fn-70 evidence surface. Prefer local/source-mapped
projection evidence unless impact review requires production state. Add
high-level source-backed tests that distinguish emergency descent safeguarding
from ordinary descent or go-around behavior.

## Acceptance
- [x] Emergency descent announcement creates a typed safeguarding state.
- [x] Affected traffic is explicit and receives a safeguard action.
- [x] Safeguarding state can be reset/cleared after the emergency descent is
  no longer active.
- [x] Ordinary descent/go-around evidence cannot satisfy the emergency descent
  source unit.
- [x] Source-backed tests move only declared rows or split branches.
- [x] Focused tests pass.

## Done summary
Implemented fn-70 source-backed emergency descent projection tests and updated chunk-08 ledgers.
## Evidence
- Commits:
- Tests: ./gradlew-nix :sim:jvmTest --tests *.Icao9432EmergencyDescentSourceBackedTest --tests *.Icao9432ModelGapSourceUnitSpecTest --tests *.EvidenceSourceCatalogTest
- PRs: