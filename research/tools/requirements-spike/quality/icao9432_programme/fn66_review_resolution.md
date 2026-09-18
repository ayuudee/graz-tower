# fn-66 Review Resolution

Reviewer: Volta

## Findings Resolved

1. Vehicle actor/content mismatch could mutate the wrong vehicle.
   - Resolution: `Step.applyVehicleDriverTransmission` now checks that
     `SpeakerRef.VehicleDriver.vehicleId` matches the vehicle id in the
     `VehicleDriverTransmission` payload.
   - Resolution: `Step.handleVehicleDriverProcessingComplete` now checks that
     the receiving vehicle id matches the vehicle id in the delivered
     `VehicleControllerTransmission` payload.
   - Regression: `Icao9432VehicleMovementSourceBackedScenarioTest.vehicle radio
     payload cannot mutate a different vehicle` pins both mismatch directions.

2. Stale-permission cancellation needed a direct regression.
   - Resolution: the source-backed scenario now includes a proceed-then-hold
     case where the scheduled arrival event must not move the vehicle after
     `HoldPosition` supersedes the permission.

3. `expected_gaps.md` was stale.
   - Resolution: chunk-07 expected gaps now list fn-66 covered/split rows and
     retain only genuine runway/towing/policy/phraseology blockers.

4. Route absence remains unrepresentable despite "when possible".
   - Resolution: documented as branch coverage, not universal first-call
     phraseology. The covered branch is structured first-call content when a
     route is available; rendered wording and route-not-possible variants
     remain outside fn-66.

## Validation After Resolution

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432VehicleMovementSourceBackedScenarioTest'`
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432VehicleMovementSourceBackedScenarioTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*Source*' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix detekt`
- `git diff --check`
