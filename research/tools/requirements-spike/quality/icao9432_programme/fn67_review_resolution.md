# fn-67 Review Resolution

Reviewer: `01a0b49f-7fc3-7670-8cb5-45b5c2334e76`

## Findings Resolved

1. `InitialCall` left stale `activeTow` state behind.
   - Resolution: `VehicleDriverTransmission.InitialCall` now sets
     `activeTow = null`.
   - Regression: `fresh vehicle initial call clears stale active tow state` in
     `Icao9432VehicleTowingSourceBackedScenarioTest`.

2. fn-67 manifest title overused "geometry".
   - Resolution: renamed the manifest title to
     "Towing Metadata And Clear-Beyond-Holding-Point Evidence Manifest".

3. Phraseology count tables were easy to misread.
   - Resolution: `coverage_report.md` and `expected_gaps.md` now state that
     split rows with a remaining `PHRASE-1` branch are counted under covered /
     split structured branch, not under `model-gap + phraseology-later`.

## Reviewer No-Issue Areas

- Source-unit honesty: no rendered phraseology or geometry-derived proof is
  overclaimed.
- Sealed-leaf handling: `RequestTow` and `TowClearBeyondHoldingPoint` have
  explicit handlers.
- State-machine token behaviour: stale tow-clear events and actor/payload
  mismatch are covered.
- Test level: tests are source-mapped scenarios rather than constructor-only
  checks.

## Verification After Resolution

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432VehicleTowingSourceBackedScenarioTest' --tests '*.Icao9432VehicleRunwaySourceBackedScenarioTest' --tests '*.Icao9432VehicleMovementSourceBackedScenarioTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*Source*' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix detekt`
