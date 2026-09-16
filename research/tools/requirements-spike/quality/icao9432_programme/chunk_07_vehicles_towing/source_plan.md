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

The generated classifier is right that this chunk is blocked by `VEHICLE-1`
and `PHRASE-1`. Current simulator traces model aircraft, pilots, controllers,
and aircraft runway/taxi lifecycle. They do not model vehicle drivers, vehicle
positions, vehicle call signs, towing combinations, or vehicle runway
occupancy. Aircraft taxi/runway compliance must not be reused as vehicle
compliance.

## Planned Coverage

| Source unit | Source text / claim | Initial classifier | Planned state | Planned evidence / blocker |
|---|---|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::122426242abf3225` | Vehicle drivers should be vigilant and comply with local procedures and ATC instructions near aircraft. | `needs-sim-model` | `model-gap` + `policy-blocked` | `VEHICLE-1`; `OperationalGuidancePolicy`; no vehicle actor, proximity-to-aircraft evidence, or local-procedure compliance model. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::6061311019d039c7` | After `HOLD POSITION`, driver shall not proceed until controller calls back with permission. | `needs-sim-model` | `model-gap` | `VEHICLE-1`; no vehicle movement lifecycle, hold-position state, or driver permission acknowledgement model. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::606d053954eff037` | Apron proceed permission may include instructions regarding other traffic. | `needs-sim-model` | `model-gap` + `policy-blocked` | `VEHICLE-1`; traffic-dependent apron vehicle instructions and give-way/jet-blast caution policy are not modelled. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::7759017903acf140` | Vehicle first-call should identify call sign, position, intended destination, and route when possible. | `phraseology-later` | `model-gap` + `phraseology-later` | `VEHICLE-1`; `PHRASE-1`; needs vehicle call sign, vehicle position/destination/route fields, vehicle transmission actor, and rendered first-call wording. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::84c61b2ef1f12ad5` | If clearance limit is not destination, driver must stop there and request permission before proceeding. | `needs-sim-model` | `model-gap` | `VEHICLE-1`; no vehicle clearance-limit stop point or onward-permission workflow. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::ed59ee805ff7fe77` | After standby, driver shall not proceed until permission is given. | `needs-sim-model` | `model-gap` | `VEHICLE-1`; no vehicle standby/proceed lifecycle. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::0b45b4a4dc2acc0c` | Vehicle on movement area may need dangerous-situation information and stop instruction. | `needs-sim-model` | `model-gap` + `policy-blocked` | `VEHICLE-1`; no vehicle movement-area occupancy, hazard relation, or controller intervention policy. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::24f7b6a86407ef6f` | Driver shall not cross runway unless positive permission has been given and acknowledged. | `needs-sim-model` | `model-gap` | `VEHICLE-1`; no vehicle runway-crossing permission/readback model. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::29be4b26bb851605` | Tow drivers should not assume receiving station knows an aircraft is to be towed. | `needs-sim-model` | `model-gap` + `policy-blocked` | `VEHICLE-1`; no towing actor/message model or receiving-station knowledge state. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::331c1cfc98ead868` | Vehicle on runway shall be instructed to leave when aircraft landing/takeoff expected. | `needs-sim-model` | `model-gap` | `VEHICLE-1`; no vehicle runway occupancy or aircraft-operation conflict rule. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71` | Tow drivers should state aircraft type and operator where applicable. | `needs-sim-model` | `model-gap` + `phraseology-later` | `VEHICLE-1`; `PHRASE-1`; no towing metadata in vehicle transmissions or rendered tow-request wording. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::735b3e9ada06105b` | Vehicle/tow runway-vacated report shall wait until clear beyond holding point. | `needs-sim-model` | `model-gap` | `VEHICLE-1`; no vehicle/tow geometry extent or designated runway-area clearance evidence. |

## Planned Tests

1. Add source-specific expected-gap specs grouped by missing model surface:
   - vehicle movement permission/hold/standby workflow;
   - vehicle runway crossing, dangerous-situation stop instruction, runway
     vacated reporting, and vehicle/tow extent geometry;
   - towing request metadata, receiving-station tow awareness, first-call
     content, and rendered vehicle/tow phraseology.
2. Do not add covered-green scenario tests. There is no current vehicle/towing
   trace to prove these source units.

## Review Considerations

- FP / type safety: no production state changes are planned. Future vehicle
  support should introduce typed vehicle actors rather than overloading
  aircraft identifiers.
- Test architecture: source-unit gap specs should make `VEHICLE-1` concrete
  and auditable. They should not be broad skip lists.
- Impact: this chunk creates a clear future implementation boundary for
  vehicle/tow modelling without changing current aircraft behaviour.
- Operational correctness: ICAO 9432 §5.1-§5.4 is about vehicle drivers and
  towing operations, not aircraft pilots.
