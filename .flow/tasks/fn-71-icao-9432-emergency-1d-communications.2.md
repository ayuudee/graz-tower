# fn-71-icao-9432-emergency-1d-communications.2 Implement communications failure evidence

## Description
Implement the approved fn-71 evidence surface. Prefer local/source-mapped
projection evidence unless impact review requires production state. Add
high-level source-backed tests that distinguish communications-failure and
blind-transmission behavior from routine radio loss.

## Acceptance
- [ ] Communications-failure state is explicit and drives alternate frequency
  and alternate station/aircraft contact branches where targeted.
- [ ] Blind-transmission mode includes repetition, next-transmission time, and
  continuation-intention payload where targeted.
- [ ] SSR 7600/7700 code evidence is explicit where targeted.
- [ ] Blind-clearance prohibition/exception behavior is explicit where
  targeted.
- [ ] Ordinary missed call/no-reply/frequency-transfer traces cannot satisfy
  communications-failure source units.
- [ ] Source-backed tests move only declared rows or split branches.
- [ ] Focused tests pass.

## Done summary
Implemented source-backed communications-failure structured projection evidence and updated chunk-08 executable/doc ledgers.
## Evidence
- Commits:
- Tests: ./gradlew-nix :sim:jvmTest --tests '*.Icao9432CommunicationsFailureSourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest', ./gradlew-nix detekt
- PRs: