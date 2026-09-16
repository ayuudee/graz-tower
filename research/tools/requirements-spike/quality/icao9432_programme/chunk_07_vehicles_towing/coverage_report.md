# Chunk 07 Coverage Report

Chunk: ICAO 9432 vehicles, runway crossing, and aircraft towing.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` | 0 |
| `covered-red` | 0 |
| `model-gap` | 6 |
| `model-gap` + `policy-blocked` | 4 |
| `model-gap` + `phraseology-later` | 2 |

Chunk 07 deliberately produces no covered-green rows. The simulator currently
has no vehicle driver actor, vehicle movement lifecycle, vehicle runway
occupancy, vehicle/tow extent geometry, towing metadata, or rendered vehicle
phraseology. Aircraft taxi/runway traces are not vehicle compliance evidence.

## Coverage Table

| Source unit | Final state | Test / blocker | Claim |
|---|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::122426242abf3225` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1`; `OperationalGuidancePolicy` | Vehicle drivers should be vigilant and comply with local procedures and ATC instructions near aircraft. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::6061311019d039c7` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1` | After `HOLD POSITION`, driver shall not proceed until controller calls back with permission. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::606d053954eff037` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1`; `OperationalGuidancePolicy` | Apron proceed permission may include instructions regarding other traffic. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::7759017903acf140` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1`; `PHRASE-1` | Vehicle first-call should identify call sign, position, destination, and route when possible. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::84c61b2ef1f12ad5` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1` | If clearance limit is not destination, driver must stop there and request permission before proceeding. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::ed59ee805ff7fe77` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1` | After standby, driver shall not proceed until permission is given. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::0b45b4a4dc2acc0c` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1`; `ControllerInterventionPolicy` | Vehicle on movement area may need dangerous-situation information and stop instruction. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::24f7b6a86407ef6f` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1` | Driver shall not cross runway unless positive permission has been given and acknowledged. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::29be4b26bb851605` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1`; `OperationalGuidancePolicy` | Tow drivers should not assume receiving station knows an aircraft is to be towed. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::331c1cfc98ead868` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1` | Vehicle on runway shall be instructed to leave when aircraft landing/takeoff is expected. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71` | `model-gap` + `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1`; `PHRASE-1` | Tow drivers should state aircraft type and operator where applicable. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::735b3e9ada06105b` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `VEHICLE-1` | Vehicle/tow runway-vacated report shall wait until clear beyond holding point. |

## Verification

- Source-unit provenance: all 12 accepted candidate JSON records are present in
  the registry with `lifecycle.state = accepted`. Source text was checked
  against `research/txt/icao9432-extracted.txt` in §5.1-§5.4.
- Focused verification:
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`.
- Full verification:
  `./gradlew-nix :sim:jvmTest`;
  `./gradlew-nix detekt`;
  `.flow/bin/flowctl validate --epic fn-57-icao-9432-chunk-07-vehicles-towing`;
  `git diff --check`.

## Review Considerations

- FP / type safety: permanent source refs use typed `EvidenceSourceRef`
  records. No production state or evidence payload type was added.
- Test architecture: expected-gap specs decompose `VEHICLE-1` into vehicle
  actor/lifecycle, runway crossing, vehicle runway occupancy, tow metadata,
  geometry, and rendered vehicle phraseology.
- Impact: no controller, pilot, sim behaviour, phraseology rendering, or policy
  behaviour was changed.
- Operational correctness: ICAO 9432 §5.1-§5.4 vehicle-driver and towing
  obligations remain distinct from aircraft pilot taxi/runway obligations.
