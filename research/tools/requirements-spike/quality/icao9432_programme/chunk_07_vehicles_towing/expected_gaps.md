# Chunk 07 Expected Gaps

Chunk: `chunk-07-vehicles-and-towing`

This file records ICAO 9432 §5.1-§5.4 source units that cannot honestly be
marked covered-green with the current model/evidence surface.

## Summary

| Source-unit state | Units |
|---|---:|
| `covered-green` / split structured branch | 8 |
| `model-gap` | 1 |
| `model-gap` + `policy-blocked` | 3 |
| `model-gap` + `phraseology-later` | 0 |
| `phraseology-later` | 0 |

Split rows with a remaining `PHRASE-1` branch are counted in
`covered-green` / split structured branch, not in `model-gap` +
`phraseology-later`.

fn-66 moved the §5.2 movement-permission lifecycle rows to source-backed
coverage and split the first-call row: structured call sign/position/
destination/route content is covered, rendered wording remains `PHRASE-1`.
fn-65 moved the §5.3.1 positive runway-crossing permission row and the
vehicle-only branch of the runway-vacated timing row to source-backed coverage.
fn-67 moved structured tow-awareness/type/operator metadata and explicit
vehicle-plus-tow clear-beyond-holding-point evidence to source-backed coverage.
Apron traffic, dangerous-situation intervention policy, expected-aircraft-
operation conflict triggers, vigilance/local-procedure rows, and rendered
vehicle/tow wording remain the dominant blockers.

## Model Gaps

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::331c1cfc98ead868` | `ExpectedAircraftOperationTrigger` | No explicit expected landing/takeoff trigger that obliges the controller to instruct a runway vehicle to leave. |

## Model Gaps With Policy

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::122426242abf3225` | `VEHICLE-1`; `OperationalGuidancePolicy` | No vehicle proximity-to-aircraft evidence or local-procedure compliance model. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::606d053954eff037` | `VEHICLE-1`; `OperationalGuidancePolicy` | No apron vehicle instruction model for other traffic, give-way, or jet-blast cautions. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::0b45b4a4dc2acc0c` | `VEHICLE-1`; `ControllerInterventionPolicy` | No vehicle movement-area occupancy, dangerous-situation relation, or stop-intervention policy. |

## Model Gap With Phraseology

No rows remain in this bucket after fn-67. Structured tow metadata is covered;
rendered tow wording remains attached to the split row below as `PHRASE-1`.

## Covered Or Split Rows

| Source unit | State | Evidence |
|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::6061311019d039c7` | `covered-green` | `Icao9432VehicleMovementSourceBackedScenarioTest`; vehicle remains stationary after `HoldPosition` and only proceeds after callback permission. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::7759017903acf140` | `split: structured first-call content covered-green; rendered wording phraseology-later` | `Icao9432VehicleMovementSourceBackedScenarioTest`; typed vehicle first call carries call sign, position, destination, and route. Rendered wording remains `PHRASE-1`. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::84c61b2ef1f12ad5` | `covered-green` | `Icao9432VehicleMovementSourceBackedScenarioTest`; vehicle stops at an intermediate clearance limit and requests onward permission before continuing. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::ed59ee805ff7fe77` | `covered-green` | `Icao9432VehicleMovementSourceBackedScenarioTest`; vehicle remains stationary after standby and only proceeds after later permission. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::24f7b6a86407ef6f` | `covered-green` | `Icao9432VehicleRunwaySourceBackedScenarioTest`; vehicle starts holding short, receives positive runway-crossing permission, and begins crossing only after driver acknowledgement. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::29be4b26bb851605` | `covered-green structured tow-awareness branch` | `Icao9432VehicleTowingSourceBackedScenarioTest`; tow-specific request identifies the aircraft under tow and is addressed to the receiving station. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71` | `split: structured type/operator metadata covered-green; rendered wording phraseology-later` | `Icao9432VehicleTowingSourceBackedScenarioTest`; tow request carries aircraft type and operator metadata. Rendered vehicle/tow wording remains `PHRASE-1`. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::735b3e9ada06105b` | `covered-green for explicit vehicle+tow clear-beyond-holding-point evidence` | `Icao9432VehicleTowingSourceBackedScenarioTest`; active-tow runway-vacated report is accepted only after correlated vehicle and tow clear-beyond-holding-point evidence. This is explicit state evidence, not geometry-derived proof. |
