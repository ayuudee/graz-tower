# Chunk 07 Coverage Report

Chunk: ICAO 9432 vehicles, runway crossing, and aircraft towing.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` / split structured branch | 8 |
| `covered-red` | 0 |
| `model-gap` | 1 |
| `model-gap` + `policy-blocked` | 3 |
| `model-gap` + `phraseology-later` | 0 |

Historically split rows are counted in `covered-green` / split structured
branch, not in `model-gap` + `phraseology-later`. fn-85 closes the two rendered
vehicle/tow wording residuals that previously lived in this chunk.

Chunk 07 now has a minimal fn-66 vehicle movement lifecycle, a narrow fn-65
vehicle-runway evidence surface, and fn-67 structured towing evidence:
vehicle-specific radio utterances, vehicle state, standby/hold-position
non-movement, intermediate clearance-limit stop/request/continue behaviour,
positive runway-crossing permission with driver acknowledgement, vehicle-only
clear-beyond-holding-point reporting, tow request metadata, receiving-station
tow addressing, and explicit vehicle-plus-tow clear-beyond-holding-point
reporting, rendered vehicle first-call wording, and rendered tow-request
type/operator wording. It still has no expected-aircraft-operation conflict
trigger, geometry-derived tow extent, apron traffic policy, or
dangerous-situation intervention policy. Aircraft taxi/runway traces are not
vehicle compliance evidence.

## Coverage Table

| Source unit | Final state | Test / blocker | Claim |
|---|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::122426242abf3225` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1`; `OperationalGuidancePolicy` | Vehicle drivers should be vigilant and comply with local procedures and ATC instructions near aircraft. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::6061311019d039c7` | `covered-green` | `Icao9432VehicleMovementSourceBackedScenarioTest` | After `HOLD POSITION`, driver shall not proceed until controller calls back with permission. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::606d053954eff037` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1`; `OperationalGuidancePolicy` | Apron proceed permission may include instructions regarding other traffic. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::7759017903acf140` | `covered-green rendered vehicle first-call branch` | `Icao9432VehicleMovementSourceBackedScenarioTest`; `RenderedPhraseologyTrace` | Vehicle first-call should identify call sign, position, destination, and route when possible. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::84c61b2ef1f12ad5` | `covered-green` | `Icao9432VehicleMovementSourceBackedScenarioTest` | If clearance limit is not destination, driver must stop there and request permission before proceeding. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::ed59ee805ff7fe77` | `covered-green` | `Icao9432VehicleMovementSourceBackedScenarioTest` | After standby, driver shall not proceed until permission is given. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::0b45b4a4dc2acc0c` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1`; `ControllerInterventionPolicy` | Vehicle on movement area may need dangerous-situation information and stop instruction. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::24f7b6a86407ef6f` | `covered-green` | `Icao9432VehicleRunwaySourceBackedScenarioTest` | Driver shall not cross runway unless positive permission has been given and acknowledged. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::29be4b26bb851605` | `covered-green structured tow-awareness branch` | `Icao9432VehicleTowingSourceBackedScenarioTest` | Tow drivers should not assume receiving station knows an aircraft is to be towed. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::331c1cfc98ead868` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `ExpectedAircraftOperationTrigger` | Vehicle on runway shall be instructed to leave when aircraft landing/takeoff is expected. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71` | `covered-green rendered tow-request type/operator branch` | `Icao9432VehicleTowingSourceBackedScenarioTest`; `RenderedPhraseologyTrace` | Tow drivers should state aircraft type and operator where applicable. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::735b3e9ada06105b` | `covered-green for explicit vehicle+tow clear-beyond-holding-point evidence` | `Icao9432VehicleTowingSourceBackedScenarioTest` | Vehicle/tow runway-vacated report shall wait until clear beyond holding point. |

## Verification

- Source-unit provenance: all 12 accepted candidate JSON records are present in
  the registry with `lifecycle.state = accepted`. Source text was checked
  against `research/txt/icao9432-extracted.txt` in §5.1-§5.4.
- Focused verification:
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432VehicleMovementSourceBackedScenarioTest' --tests '*.Icao9432VehicleRunwaySourceBackedScenarioTest' --tests '*.Icao9432VehicleTowingSourceBackedScenarioTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`.
- Full verification:
  `./gradlew-nix :sim:jvmTest`;
  `./gradlew-nix detekt`;
  `.flow/bin/flowctl validate --epic fn-57-icao-9432-chunk-07-vehicles-towing`;
  `git diff --check`.

## Review Considerations

- FP / type safety: permanent source refs use typed `EvidenceSourceRef`
  records. Vehicle actors use `VehicleId`/`VehicleState` and vehicle-specific
  utterances rather than aircraft identifiers or aircraft protocol leaves.
- Test architecture: source-backed vehicle scenarios prove the movement
  lifecycle rows, the narrow vehicle-runway rows, and structured towing rows.
  Expected-gap specs still decompose remaining blockers into
  expected-aircraft-operation conflict triggers, hazard policy, and apron
  traffic policy.
- Impact: vehicle movement/runway/tow state and radio utterances were added to
  the sim. Current aircraft controller/pilot behaviour is intentionally
  unchanged. fn-85 adds rendered evidence over existing vehicle/tow radio
  payloads only.
- Operational correctness: ICAO 9432 §5.1-§5.4 vehicle-driver and towing
  obligations remain distinct from aircraft pilot taxi/runway obligations.
