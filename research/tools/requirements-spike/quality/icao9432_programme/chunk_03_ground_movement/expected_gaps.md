# Chunk 03 Expected Gaps

Chunk: `chunk-03-ground-movement`

This file records ICAO 9432 §4.3 / §4.4 source units that cannot honestly be
marked covered-green with the current model/evidence surface.

## Summary

| Source-unit state | Units |
|---|---:|
| `covered-green` configured policy | 1 |
| `covered-red` | 1 |
| `model-gap` | 6 |
| `policy-blocked` | 1 |
| `phraseology-later` | 2 |

## Covered-Green Configured Policy

| Source unit | Test | Reason |
|---|---|---|
| `icao9432-extracted::taxi_4_4_en::417f64324f7495bf` | `Icao9432TaxiSourceBackedScenarioTest` | LOWG/RWY 16C is explicitly bound to `TaxiClearanceLimitPolicy.DeparturesNormallyToRunwayHoldingPoint`, and the live trace proves the departing-aircraft taxi clearance limit is a runway holding point before runway use. This does not claim that every aerodrome must use a holding-point limit. |

## Covered-Red

| Source unit | Test | Reason |
|---|---|---|
| `icao9432-extracted::taxi_4_4_en::b9e7fc3605fe616e` | `Icao9432Chunk03GroundMovementEvidenceTest` | ICAO 9432 §4.4 says taxi instructions always contain a clearance limit. Current typed taxi-instruction space includes taxi-like leaves without clearance-limit fields (`TaxiViaRunway(destination = null)`, `ExpediteTaxi`, etc.), so the source-mapped structural audit fails loudly instead of greening one observed LOWG taxi clearance. |

## Model Gaps

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::pushback_powerback_4_3_en::1aae1f61b91984e8` | `PUSHBACK-1` | No powerback manoeuvre lifecycle or reverse-engine movement model. |
| `icao9432-extracted::pushback_powerback_4_3_en::5980a8f786170b01` | `PUSHBACK-1`; `LocalProcedurePolicy` | No pushback/powerback request workflow that can route responsibility to ATC vs apron management according to local procedures. |
| `icao9432-extracted::pushback_powerback_4_3_en::b3652213a568f55f` | `PUSHBACK-1` | No ground-crew actor or visual completion signal before taxi. |
| `icao9432-extracted::taxi_4_4_en::1367907005a34ad1` | compound taxi-clearance model gap | Current protocol has standalone `CrossRunway`, `HoldShortOf`, and `TaxiViaRunway` leaves, but no typed compound taxi clearance proving a taxi limit beyond a runway contains the required crossing clearance or hold-short instruction. |
| `icao9432-extracted::taxi_4_4_en::53f33b6da4f2be58` | departure-information content evidence | ATIS acknowledgement is observable, but departure-information content on taxi instructions is not typed/rendered strongly enough to prove omission because ATIS was acknowledged. |
| `icao9432-extracted::taxi_4_4_en::eadf2541fcd51825` | whole-aircraft geometry evidence | Current traces observe `RunwayVacated` reports and aircraft point positions, but do not prove the entire aircraft is beyond the relevant runway-holding position. |

## Policy-Blocked

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::taxi_4_4_en::03985c8e2cf3f473` | `POLICY-1`, `LocalProcedurePolicy` | The taxi limit may be another aerodrome position depending on traffic circumstances; this is local/traffic policy, not a universal source-mapped pass/fail. |

## Phraseology-Later

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::pushback_powerback_4_3_en::da5fd317668b375a` | `PHRASE-1` | Requires rendered "stop push-back" phraseology evidence. |
| `icao9432-extracted::pushback_powerback_4_3_en::fc3dfdf7cc913637` | `PHRASE-1` | Requires rendered pilot / ground-crew pushback coordination phraseology. |
