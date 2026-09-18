# Chunk 07 Source Plan

Chunk: `chunk-07-vehicles-and-towing`

Scope: ICAO 9432 §5.1 introduction, §5.2 movement instructions, §5.3
crossing runways, and §5.4 vehicles towing aircraft.

## Source Audit

- Accepted source units: 12.
- Sections:
  - `aerodrome_vehicles_intro_movement_5_1_to_5_2_en`: 6 units.
  - `aerodrome_vehicles_crossing_towing_5_3_to_5_4_en`: 6 units.
- Quote audit: all 12 accepted source units were checked against
  `research/txt/icao9432-extracted.txt`. Source text is present in §5.1-§5.4;
  some excerpts span wrapped lines in the extracted text.

## Review Position

The generated classifier was right that this chunk started blocked by
`VEHICLE-1` and `PHRASE-1`. fn-66 adds a minimal vehicle-driver actor,
vehicle-specific radio utterances, vehicle position/destination/route state,
and a permission lifecycle for standby, hold-position, and intermediate
clearance-limit cases. It does not model vehicle runway occupancy, vehicle/tow
extent geometry, towing combinations, apron traffic policy, or rendered vehicle
phraseology. Aircraft taxi/runway compliance must not be reused as vehicle
compliance.

## Planned Coverage

| Source unit | Source text / claim | Initial classifier | Planned state | Planned evidence / blocker |
|---|---|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::122426242abf3225` | Vehicle drivers should be vigilant and comply with local procedures and ATC instructions near aircraft. | `needs-sim-model` | `model-gap` + `policy-blocked` | `VEHICLE-1`; `OperationalGuidancePolicy`; no vehicle actor, proximity-to-aircraft evidence, or local-procedure compliance model. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::6061311019d039c7` | After `HOLD POSITION`, driver shall not proceed until controller calls back with permission. | `needs-sim-model` | `covered-green` | `Icao9432VehicleMovementSourceBackedScenarioTest`; vehicle remains stationary after `HoldPosition` and proceeds only after callback permission. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::606d053954eff037` | Apron proceed permission may include instructions regarding other traffic. | `needs-sim-model` | `model-gap` + `policy-blocked` | `VEHICLE-1`; traffic-dependent apron vehicle instructions and give-way/jet-blast caution policy are not modelled. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::7759017903acf140` | Vehicle first-call should identify call sign, position, intended destination, and route when possible. | `phraseology-later` | `split: structured first-call content covered-green; rendered wording phraseology-later` | `Icao9432VehicleMovementSourceBackedScenarioTest`; typed vehicle first-call carries call sign, position, destination, and route. `PHRASE-1` remains for rendered wording. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::84c61b2ef1f12ad5` | If clearance limit is not destination, driver must stop there and request permission before proceeding. | `needs-sim-model` | `covered-green` | `Icao9432VehicleMovementSourceBackedScenarioTest`; vehicle stops at intermediate clearance limit and requests onward permission before continuing. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::ed59ee805ff7fe77` | After standby, driver shall not proceed until permission is given. | `needs-sim-model` | `covered-green` | `Icao9432VehicleMovementSourceBackedScenarioTest`; vehicle remains stationary after standby and proceeds only after later permission. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::0b45b4a4dc2acc0c` | Vehicle on movement area may need dangerous-situation information and stop instruction. | `needs-sim-model` | `model-gap` + `policy-blocked` | `VEHICLE-1`; no vehicle movement-area occupancy, hazard relation, or controller intervention policy. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::24f7b6a86407ef6f` | Driver shall not cross runway unless positive permission has been given and acknowledged. | `needs-sim-model` | `model-gap` | `VEHICLE-1`; no vehicle runway-crossing permission/readback model. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::29be4b26bb851605` | Tow drivers should not assume receiving station knows an aircraft is to be towed. | `needs-sim-model` | `model-gap` + `policy-blocked` | `VEHICLE-1`; no towing actor/message model or receiving-station knowledge state. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::331c1cfc98ead868` | Vehicle on runway shall be instructed to leave when aircraft landing/takeoff expected. | `needs-sim-model` | `model-gap` | `VEHICLE-1`; no vehicle runway occupancy or aircraft-operation conflict rule. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71` | Tow drivers should state aircraft type and operator where applicable. | `needs-sim-model` | `model-gap` + `phraseology-later` | `VEHICLE-1`; `PHRASE-1`; no towing metadata in vehicle transmissions or rendered tow-request wording. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::735b3e9ada06105b` | Vehicle/tow runway-vacated report shall wait until clear beyond holding point. | `needs-sim-model` | `model-gap` | `VEHICLE-1`; no vehicle/tow geometry extent or designated runway-area clearance evidence. |

## Planned Tests

1. `Icao9432VehicleMovementSourceBackedScenarioTest` proves the fn-66
   movement-permission rows with vehicle-specific radio and state evidence.
2. `Icao9432ModelGapSourceUnitSpecTest` keeps expected gaps grouped by missing
   model surface:
   - vehicle vigilance, local-procedure compliance, and apron traffic policy;
   - vehicle runway crossing, dangerous-situation stop instruction, runway
     vacated reporting, and vehicle/tow extent geometry;
   - towing request metadata, receiving-station tow awareness, and rendered
     vehicle/tow phraseology.

## Review Considerations

- FP / type safety: fn-66 introduces `VehicleId`, `VehicleState`, and
  vehicle-specific radio utterances rather than overloading `AircraftId`,
  `PilotTransmission`, or aircraft `AtcInstruction` leaves.
- Test architecture: source-unit gap specs and source-backed scenario tests
  make the remaining `VEHICLE-1` boundaries concrete and auditable. They are
  not broad skip lists.
- Impact: fn-66 adds a vehicle movement lifecycle without changing current
  aircraft behaviour. Runway occupancy, towing metadata, and rendered
  phraseology remain future implementation boundaries.
- Operational correctness: ICAO 9432 §5.1-§5.4 is about vehicle drivers and
  towing operations, not aircraft pilots.
