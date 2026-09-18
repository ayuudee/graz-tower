# fn-65 Review Resolution

Reviewer: Ptolemy (`01a0b48d-b6f7-71e3-a5b4-526420bb0bbd`)

## Findings Resolved

1. `VehicleClearBeyondHoldingPoint` was not correlated to the vacate/crossing
   instance.
   - Resolution: added `ActiveRunwayVacate` and a `VehiclePermissionId` token
     to `VehicleClearBeyondHoldingPoint`. The handler now only accepts the
     clear event when the active vacate token matches. A stale-token regression
     test keeps this pinned.

2. `24f7b6a86407ef6f` lacked a negative guard test for acknowledgement without
   positive permission.
   - Resolution: added a failure test proving `AcknowledgeRunwayCrossing`
     without a matching active crossing permission throws.

3. The fn-65 manifest still listed the expected-aircraft-operation scenario as
   a planned test.
   - Resolution: updated the manifest to state that expected landing/takeoff
     conflict remains a model gap until an explicit trigger is modelled.

## Residual Risks

- `735b3e9ada06105b` remains split-only. The implemented branch proves
  vehicle-only clear-beyond-holding-point timing via explicit sim state, not
  tow/combined extent geometry.
- `331c1cfc98ead868` remains a model gap. `VacateRunway` exists as a
  vehicle-runway instruction, but no rule ties it to an expected aircraft
  landing/takeoff operation.

## Post-Resolution Verification

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432VehicleRunwaySourceBackedScenarioTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*Source*' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix detekt`
