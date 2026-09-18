# fn-65 Vehicle Runway Manifest

Epic: `fn-65-icao-9432-vehicle-1b-vehicle-runway`

Source scope: ICAO Doc 9432 §5.3, chunk
`chunk-07-vehicles-and-towing`.

## Mission

Add explicit vehicle runway-crossing and runway-occupancy evidence on top of
fn-66's vehicle movement lifecycle. The goal is not to make vehicles part of
the aircraft runway-duty machine yet; it is to prove vehicle-specific source
claims without using aircraft taxi/runway traces as a substitute.

## Source-Unit Movement Manifest

| Source unit | ICAO 9432 section | Current state | fn-65 target state | Evidence required |
|---|---|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::24f7b6a86407ef6f` | §5.3.1 | `model-gap` | `covered-green` | Vehicle must be holding short; controller must give positive runway-crossing permission; driver must acknowledge; crossing may begin only after both permission and acknowledgement. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::331c1cfc98ead868` | §5.3.2 | `model-gap` | unchanged in fn-65 implementation | Vehicle-on-runway state now exists, but no explicit expected landing/takeoff trigger was added in this pass. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::735b3e9ada06105b` | §5.3.1 | `model-gap` | `split: vehicle-only clear-beyond-holding-point covered-green; tow/combined extent geometry blocked` | Vehicle-only scenario must prove `RunwayVacated` is transmitted only after explicit clear-beyond-holding-point state. Tow extent remains blocked. |

Rows deliberately unchanged:

- `0b45b4a4dc2acc0c`: dangerous-situation stop instruction remains
  `model-gap + policy-blocked`; needs hazard relation and intervention policy.
- `29be4b26bb851605`: receiving-station tow awareness remains towing work.
- `4b103081585bfb71`: tow aircraft type/operator metadata and rendered wording
  remain towing + phraseology work.

## Implementation Plan

1. Extend the vehicle runway surface, not aircraft runway observations.
   - Add vehicle-specific runway phases/facts such as holding short, crossing,
     on runway, and clear beyond holding point.
   - Keep `RunwayObservation.occupants: Set<AircraftId>` unchanged in this
     pass unless we do a deliberate typed occupant migration. Do not put
     `VehicleId` into aircraft occupant sets.

2. Add vehicle runway transmissions.
   - Controller: hold short runway, cross runway, vacate runway.
   - Driver: acknowledgement/crossing and runway-vacated report.
   - Actor/payload mismatch checks from fn-66 must apply to new leaves.

3. Add movement cleanup.
   - Positive crossing permission is consumed when crossing starts.
   - Runway occupancy is set only by crossing/on-runway facts and cleared only
     by explicit beyond-holding-point evidence.
   - A vacated report before beyond-holding-point state must fail loudly or be
     impossible through the helper path.

4. Add source-mapped high-level tests.
   - Crossing: hold short -> cross permission -> acknowledgement -> crossing.
   - Vacated: crossing/on-runway -> beyond holding point -> runway vacated
     report. Assert no earlier report.
   - Keep expected-aircraft-operation conflict as a model gap until an explicit
     landing/takeoff expectation trigger is modelled.

5. Update programme docs and blocker manifest.
   - Move only the declared rows.
   - Preserve tow extent and hazard/policy blockers.

## Review Considerations

### FP / Type Safety

- Vehicle runway state must be closed and explicit. New `when` expressions over
  vehicle transmissions or phases must be exhaustive.
- Do not broaden aircraft-only types accidentally. If a typed runway occupant
  abstraction is introduced, every aircraft consumer must be audited.
- If the type system allows an early vacated report, the handler must reject it
  loudly; do not silently accept it.

### Test Architecture

- Tests must assert ordering and absence: no crossing before positive
  permission and acknowledgement; no vacated report before clear-beyond-
  holding-point evidence.
- The vacated-report source unit can only move as a split vehicle-only branch
  unless tow extent geometry is also implemented.
- Do not prove §5.3 rows via generic `RunwayObstruction`; obstruction is not
  crossing permission, acknowledgement, or holding-point clearance evidence.

### Impact

- fn-65 should keep aircraft controller runway-duty behaviour unchanged unless
  the implementation explicitly chooses a typed occupant migration and audits
  all aircraft consumers.
- The vehicle runway surface should be usable by fn-67 towing work without
  implying tow extent is already represented.

### Operational Correctness

- ICAO Doc 9432 §5.3.1 says a driver shall not cross a runway unless positive
  permission has been given and acknowledged.
- ICAO Doc 9432 §5.3.1 says a runway-vacated report shall not be made until the
  vehicle and tow are clear of the designated runway area, beyond the holding
  point.
- ICAO Doc 9432 §5.3.2 says a vehicle operating on the runway shall be
  instructed to leave the runway when an aircraft is expected to land or take
  off.

## Impact Review Incorporated

Kant's impact assessment agrees that fn-65 must not use aircraft runway
observations as the proof surface and recommends a narrow vehicle-specific
runway evidence surface. It also flags `735b3e9ada06105b` as split-only unless
tow/combined extent geometry is implemented; this manifest follows that
constraint.
