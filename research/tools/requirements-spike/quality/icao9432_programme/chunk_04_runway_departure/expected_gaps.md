# Chunk 04 Expected Gaps

Chunk: `chunk-04-runway-departure`

This file records ICAO 9432 §4.5 source units that cannot honestly be marked
covered-green with the current model/evidence surface.

## Summary

| Source-unit state | Units |
|---|---:|
| `covered-green` | 4 |
| `policy-blocked` | 9 |
| `model-gap` | 1 |
| `phraseology-later` | 4 |
| `split: rendered phraseology covered; trigger policy blocked` | 1 |

## Covered-Green Configured Policy

| Source unit | Test | Reason |
|---|---|---|
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::19cfd36a9fce4587` | `Icao9432Chunk04RunwayDepartureEvidenceTest` | LOWG separate GROUND/TOWER operations are explicitly bound to `TowerTransferPolicy.SeparateGroundTowerTransferAtHoldingPoint`, and the live trace proves GROUND transfers the aircraft to TOWER while it is holding short at the RWY 16C holding point before runway use. This does not claim a universal transfer point for every aerodrome/service shape. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3` | `Icao9432PhraseologyEvidenceTest` | The LOWG trace proves rendered controller `RUNWAY [designator] CLEARED FOR TAKE-OFF` phraseology. This does not close immediate-departure, conditional-clearance, taxi-ambiguity, or stop-immediately phraseology. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::a93888a25f0bad03` | `Icao9432PhraseologyEvidenceTest` | The LOWG trace proves rendered controller `RUNWAY [designator] LINE UP AND WAIT` phraseology and rendered pilot `LINING UP [callsign]` acknowledgement. This does not close immediate-departure or conditional-clearance phraseology. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::8af22eb8d9795cef` | `Icao9432PhraseologyEvidenceTest` | In an explicitly declared several-runways / confusion-risk branch, the rendered take-off clearance contains `RUNWAY` plus the active runway designator token. This is rendered phraseology evidence, not typed activation evidence for detecting confusion risk. |

## Split Coverage

| Source unit | Covered branch | Remaining blocker |
|---|---|---|
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6b5a0d8b27525cbd` | `Icao9432PhraseologyEvidenceTest` proves rendered `STOP IMMEDIATELY` repeated with exactly two callsign tokens. | Takeoff-roll / dangerous-traffic trigger policy and a live emergency scenario remain blocked. |

## Policy-Blocked

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::4e0bacdd1c2c06e0` | `POLICY-1` | Needs typed emergency / safety-necessity policy before the "except emergency" communication rule can be asserted. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::2660849403bff7de` | `POLICY-1` | Needs conditional-clearance traffic-identification evidence and policy. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::c386a5865bdd7876` | `POLICY-1` | Needs policy for when aircraft type is insufficient and colour/company description is required. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::dd301daf2b69fe83` | `POLICY-1` | The source says the controller *may* request an airborne report in poor visibility; current tests lack poor-visibility departure policy and request-airborne evidence. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::0afe0064c4c933af` | `POLICY-1` | Needs abandoned-takeoff communication timing policy. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::2b7c45264775e3e2` | `ControllerInterventionPolicy` | Needs post-abort assistance / taxi-instruction request workflow. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::2cc8caf62c15688b` | `POLICY-1` | The source says departure instructions *may* be given with take-off clearance; current protocol lacks a compound departure-instruction take-off clearance model. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6bee6c63069d8250` | `POLICY-1` | Needs unexpected-traffic / long-departure cancellation policy and trigger evidence. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::81490161201eb712` | `POLICY-1` | Needs runway-freeing-for-landing-traffic policy and trigger evidence. |

## Model Gaps

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::2e598ad0323e9e2a` | dual-sighting evidence | Current traces can observe conditional clearances but do not prove that both controller and pilot see the conditioned aircraft or vehicle. |

## Phraseology-Later

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::152f0ffb84869af5` | `PHRASE-1` | Requires rendered immediate-departure line-up phraseology. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::42b0460ed4f07751` | `PHRASE-1` | Requires rendered immediate-departure readiness query. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::db8a2c3dcd586b0e` | `PHRASE-1` | Requires rendered taxi phraseology ambiguity checking. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::9b30810984e06a35` | `PHRASE-1` | Requires rendered conditional-clearance element ordering. |
