# Chunk 07 Expected Gaps

Chunk: `chunk-07-vehicles-and-towing`

This file records ICAO 9432 §5.1-§5.4 source units that cannot honestly be
marked covered-green with the current model/evidence surface.

## Summary

| Source-unit state | Units |
|---|---:|
| `covered-green` / split structured branch | 4 |
| `model-gap` | 3 |
| `model-gap` + `policy-blocked` | 4 |
| `model-gap` + `phraseology-later` | 1 |
| `phraseology-later` | 0 |

fn-66 moved the §5.2 movement-permission lifecycle rows to source-backed
coverage and split the first-call row: structured call sign/position/
destination/route content is covered, rendered wording remains `PHRASE-1`.
Runway, towing, apron traffic, and vigilance/local-procedure rows remain the
dominant blockers.

## Model Gaps

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::24f7b6a86407ef6f` | `VEHICLE-1` | No vehicle runway-crossing permission and acknowledgement model. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::331c1cfc98ead868` | `VEHICLE-1` | No vehicle runway occupancy or aircraft-operation conflict rule. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::735b3e9ada06105b` | `VEHICLE-1` | No vehicle/tow extent or designated-runway-area clearance geometry. |

## Model Gaps With Policy

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::122426242abf3225` | `VEHICLE-1`; `OperationalGuidancePolicy` | No vehicle proximity-to-aircraft evidence or local-procedure compliance model. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::606d053954eff037` | `VEHICLE-1`; `OperationalGuidancePolicy` | No apron vehicle instruction model for other traffic, give-way, or jet-blast cautions. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::0b45b4a4dc2acc0c` | `VEHICLE-1`; `ControllerInterventionPolicy` | No vehicle movement-area occupancy, dangerous-situation relation, or stop-intervention policy. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::29be4b26bb851605` | `VEHICLE-1`; `OperationalGuidancePolicy` | No tow request actor/message model or receiving-station knowledge state. |

## Model Gap With Phraseology

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71` | `VEHICLE-1`; `PHRASE-1` | No towing metadata in vehicle transmissions and no rendered tow-request wording. |

## Covered Or Split Rows

| Source unit | State | Evidence |
|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::6061311019d039c7` | `covered-green` | `Icao9432VehicleMovementSourceBackedScenarioTest`; vehicle remains stationary after `HoldPosition` and only proceeds after callback permission. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::7759017903acf140` | `split: structured first-call content covered-green; rendered wording phraseology-later` | `Icao9432VehicleMovementSourceBackedScenarioTest`; typed vehicle first call carries call sign, position, destination, and route. Rendered wording remains `PHRASE-1`. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::84c61b2ef1f12ad5` | `covered-green` | `Icao9432VehicleMovementSourceBackedScenarioTest`; vehicle stops at an intermediate clearance limit and requests onward permission before continuing. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::ed59ee805ff7fe77` | `covered-green` | `Icao9432VehicleMovementSourceBackedScenarioTest`; vehicle remains stationary after standby and only proceeds after later permission. |
