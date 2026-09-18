# fn-66 Vehicle Movement Manifest

Epic: `fn-66-icao-9432-vehicle-1a-vehicle-movement`

Source scope: ICAO Doc 9432 §5.1.2 and §5.2.1-§5.2.4, chunk
`chunk-07-vehicles-and-towing`.

## Mission

Introduce the smallest honest vehicle movement and permission lifecycle needed
to move selected `VEHICLE-1A` source units out of generic model-gap status,
without using aircraft taxi behaviour as vehicle evidence and without greening
runway-crossing or towing units that need later geometry/occupancy work.

This is intentionally ordered before fn-65 even though fn-65 has the lower
number: fn-65's runway-crossing and runway-occupancy claims need a vehicle
actor and permission lifecycle first.

## Source-Unit Movement Manifest

| Source unit | ICAO 9432 section | Current state | fn-66 target state | Evidence required |
|---|---|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::6061311019d039c7` | §5.2.3 | `model-gap` | `covered-green` | A vehicle receives `HOLD POSITION`, does not move, then only proceeds after a later explicit permission/callback. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::ed59ee805ff7fe77` | §5.2.2 | `model-gap` | `covered-green` | A vehicle receives `STANDBY`, does not move, then only proceeds after explicit permission. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::84c61b2ef1f12ad5` | §5.2.3 | `model-gap` | `covered-green` | A vehicle permission has a clearance limit short of destination; the vehicle stops at the limit and requests further permission before proceeding. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::7759017903acf140` | §5.2.1 | `model-gap + phraseology-later` | `split: structured first-call content covered-green; rendered wording phraseology-later` | A first-call transmission/event carries vehicle call sign, position, intended destination, and route when possible. Rendered wording remains blocked by `PHRASE-1`. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::122426242abf3225` | §5.1.2 | `model-gap + policy-blocked` | unchanged | Driver vigilance, aircraft proximity, local-procedure compliance, and full ATC-instruction compliance remain broader than the minimal permission lifecycle. |
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::606d053954eff037` | §5.2.4 | `model-gap + policy-blocked` | unchanged or configured-branch only if cheap | Apron traffic instructions are discretionary. Do not assert a universal requirement; only green a configured branch if explicit traffic-instruction evidence exists. |

Rows from ICAO 9432 §5.3-§5.4 remain untouched for fn-65/fn-67:

- `0b45b4a4dc2acc0c`: dangerous-situation stop instruction.
- `24f7b6a86407ef6f`: runway crossing requires permission and acknowledgement.
- `29be4b26bb851605`: receiving station tow awareness.
- `331c1cfc98ead868`: vehicle on runway leaves for expected aircraft operation.
- `4b103081585bfb71`: tow request states aircraft type/operator.
- `735b3e9ada06105b`: runway-vacated report only after vehicle/tow clear beyond holding point.

## Implementation Plan

1. Add a minimal closed vehicle movement surface.
   - `VehicleId`/vehicle call sign remains separate from `AircraftId`.
   - Vehicle position, destination, and route are explicit data.
   - Permission state must distinguish at least: awaiting permission, held,
     standby, proceeding to clearance limit, stopped at clearance limit, and
     complete.

2. Add trace/evidence events for vehicle movement.
   - First call with structured fields.
   - Controller response: standby, hold position, proceed to a point/route.
   - Driver acknowledgement/request for further permission.
   - Vehicle movement facts: moved, stopped at clearance limit, did not move
     before permission.

3. Add source-mapped tests.
   - Prefer a high-level minimal vehicle scenario over isolated unit tests.
   - Assertions must verify ordering and absence, not just that a type exists.
   - At least one negative/regression assertion must fail if the vehicle moves
     after standby/hold but before callback permission.

4. Update programme docs and blocker manifest.
   - Move only declared rows.
   - Leave runway crossing/towing and rendered phraseology blocked.
   - Record any configured branch explicitly as a branch, not universal law.

## Review Considerations

### FP / Type Safety

- Vehicle identifiers must not be aliases for `AircraftId`.
- Vehicle instruction/transmission state should be a closed sealed surface; any
  `when` added over it must be exhaustive.
- No catch-all `else` may silently treat unknown vehicle instructions as
  allowed movement.
- Absence of route or destination is only allowed where the source permits
  "when possible"; otherwise construction should require the field.

### Test Architecture

- Tests must be source-mapped to the exact §5.2 rows above.
- A test that only checks field presence is insufficient for hold/standby rows;
  it must prove no movement before permission and movement only after permission.
- A test that proves the structured first-call fields must not claim rendered
  wording or exact phraseology.

### Impact

- Keep the first implementation out of aircraft mission planning and runway duty
  machinery unless a source unit truly needs those interactions.
- Do not let vehicle state change aircraft-only invariants, controller
  responsibility ownership, or recent aircraft radio history.
- Later fn-65 must be able to add runway occupancy without undoing fn-66's
  vehicle actor model.

### Operational Correctness

- ICAO Doc 9432 §5.2.2 says standby means the driver waits for callback and
  shall not proceed until permission is given.
- ICAO Doc 9432 §5.2.3 says `HOLD POSITION` means the driver shall not proceed
  until callback permission; other replies define a point to proceed to, and if
  that point is not the destination the driver must stop there and request
  permission before proceeding further.
- ICAO Doc 9432 §5.2.1 first-call requires vehicle call sign, position,
  intended destination, and route when possible. Rendered wording remains under
  `PHRASE-1`.

## Plan Review

Self-review before implementation:

- The plan deliberately moves fn-66 before fn-65 because fn-65 depends on the
  vehicle lifecycle. This avoids false-green runway occupancy tests.
- The target rows are narrow enough to prove without runway/tow geometry.
- The risky row is `7759017903acf140`: it must be split so structured fields do
  not masquerade as rendered phraseology.
- The local-procedure/vigilance row `122426242abf3225` remains blocked because a
  minimal vehicle permission lifecycle does not prove vigilance, aircraft
  proximity, or local-procedure compliance.
- The apron-traffic row `606d053954eff037` is discretionary. Greening it as a
  universal rule would violate the policy guardrails.

Impact-assessment adjustments from Hooke:

- Keep vehicle radio and controller outputs separate from aircraft-only
  `PilotTransmission` and `AtcInstruction` surfaces. A broad generic actor
  migration is too large for fn-66, but overloading aircraft protocol would be
  a false-green risk.
- Add explicit cleanup/supersession checks: standby or hold must suspend any
  stale proceed permission; reaching an intermediate clearance limit completes
  the active permission and leaves the vehicle waiting.
- Use high-level scenario tests that prove movement absence during hold/standby
  windows, not unit tests that only prove a type exists.
- The impact assessment referred to the clearance-limit row as §5.2.4; the
  source text places that obligation in §5.2.3. The manifest keeps the §5.2.3
  citation.
