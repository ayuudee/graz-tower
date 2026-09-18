# fn-67 Towing Metadata And Clear-Beyond-Holding-Point Evidence Manifest

Epic: `fn-67-icao-9432-vehicle-1c-towing-metadata`

Source scope: ICAO Doc 9432 §5.4 and the tow/combined-extent branch of §5.3,
chunk `chunk-07-vehicles-and-towing`.

## Mission

Add enough towing-specific state to stop treating a standalone vehicle as proof
for a vehicle towing an aircraft. The goal is a narrow source-mapped towing
surface: the radio trace must identify the aircraft under tow to the receiving
station, carry type and operator metadata where applicable, and prove runway-
vacated timing only after explicit vehicle+tow clear-beyond-holding-point
evidence. This epic does not claim physical holding-point geometry unless it
actually implements geometry-derived extent checks.

## Source-Unit Movement Manifest

| Source unit | ICAO 9432 section | Current state | fn-67 target state | Evidence required |
|---|---|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::29be4b26bb851605` | §5.4 | `model-gap + policy-blocked` | `covered-green structured tow-awareness branch` | A tow-specific vehicle-driver radio request must identify that an aircraft is under tow when contacting the receiving station, so the receiving station is not silently assumed to know. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71` | §5.4 | `model-gap + phraseology-later` | `split: structured type/operator metadata covered-green; rendered towing wording phraseology-later` | The tow request must carry aircraft type and operator metadata. Rendered vehicle/tow wording remains blocked unless this epic deliberately extends the vehicle phraseology renderer. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::735b3e9ada06105b` | §5.3.1 | `split: vehicle-only clear-beyond-holding-point covered-green; tow/combined extent geometry blocked` | `covered-green for explicit vehicle+tow clear-beyond-holding-point evidence` | A runway-vacated report with an active tow must be accepted only after correlated evidence that both the vehicle and tow extent are clear beyond the holding point. This is explicit state evidence, not geometry-derived proof unless implemented. |

Rows deliberately unchanged:

- `331c1cfc98ead868`: expected landing/takeoff conflict trigger remains a
  model gap; tow metadata does not prove expected-aircraft-operation policy.
- `0b45b4a4dc2acc0c`: dangerous-situation information and stop instruction
  remains `model-gap + policy-blocked`; needs hazard relation and intervention
  policy.
- `122426242abf3225` and `606d053954eff037`: vigilance, local procedures, and
  apron traffic policy remain policy/model gaps outside fn-67.

## Implementation Plan

1. Add structured tow metadata to the vehicle domain.
   - Introduce a small `TowMetadata`/`ActiveTow` style type carrying the towed
     aircraft identifier, aircraft type, and operator where applicable.
   - Keep it vehicle-domain specific. Do not overload `AircraftState` or create
     fake live aircraft behaviour merely to prove a tow request.

2. Add a tow-specific driver radio request.
   - The request should be a real `Utterance.FromVehicleDriver` leaf, not a
     test-only side channel.
   - It must carry the aircraft-under-tow fact plus type/operator metadata.
   - The test for `29be...` must prove the request is addressed to the
     receiving station/frequency, not merely that the payload exists.
   - Actor/payload mismatch checks from fn-66 must continue to cover it.

3. Add tow-extent clearance evidence.
   - Represent whether the tow extent is clear beyond the holding point.
   - If a vehicle has an active tow, `RunwayVacated` must require both vehicle
     clear-beyond-holding-point and tow clear-beyond-holding-point evidence.
   - Tie tow-clear state to the active runway-vacate correlation token,
     following fn-65's `ActiveRunwayVacate` fix. A stale tow-clear event must
     not prove current clearance.

4. Add high-level source-mapped tests.
   - Tow request identifies aircraft-under-tow to the receiving station.
   - Tow request carries aircraft type and operator as structured metadata.
   - Vehicle+tow runway-vacated report waits for both vehicle and tow clear
     evidence; premature report fails loudly.
   - Stale/wrong-token tow-clear evidence does not clear the active tow.
   - Actor/payload mismatch fails for the new tow transmission leaf.

5. Update programme ledgers.
   - Move only the declared rows.
   - Keep rendered towing wording phraseology-later unless vehicle/tow
     rendering is deliberately implemented and reviewed.
   - Keep policy rows blocked where policy or hazard relation is still absent.

## Review Considerations

### FP / Type Safety

- Tow metadata should be a closed, explicit value object. Optional operator is
  acceptable only if absence is the real domain state; a source-mapped test for
  `4b103...` must use the applicable/operator-present branch.
- New sealed transmission leaves must be handled exhaustively in duration,
  delivery, and state-update paths.
- Correlated tow-clear events must not silently clear unrelated active tow or
  vacate state.

### Test Architecture

- Tests must be high-level source-mapped vehicle/tow scenarios, not pure
  constructor tests.
- `4b103...` can move only as structured metadata unless rendered vehicle
  phraseology is added. Do not use substring checks as source proof.
- `735...` must test absence/failure before combined clear evidence, not just a
  happy-path final state. If implementation uses explicit clear-state evidence
  rather than real geometry, docs must say that plainly.

### Impact

- This should remain in the sim vehicle domain and should not alter aircraft
  pilot/controller runway-duty behaviour.
- The tow model should be reusable by a later actual towing movement model, but
  fn-67 does not need towing physics.
- Moving `29be...` from policy-blocked is justified only for the explicit
  structured tow-awareness branch; local receiving-station operational policy
  stays outside this pass.

### Operational Correctness

- ICAO Doc 9432 §5.4 says drivers towing aircraft should not assume the
  receiving station is aware that an aircraft is to be towed.
- ICAO Doc 9432 §5.4 says drivers should state the type, and where applicable
  the operator, of the aircraft to be towed.
- ICAO Doc 9432 §5.3.1 says a runway-vacated report shall not be made until
  the vehicle and tow are clear of the designated runway area, beyond the
  holding point.
