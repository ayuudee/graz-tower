# Chunk 04 Expected Gaps

Chunk: `chunk-04-runway-departure`

This file records ICAO 9432 §4.5 source units that cannot honestly be marked
covered-green with the current model/evidence surface.

## Summary

| Source-unit state | Units |
|---|---:|
| `policy-blocked` | 10 |
| `model-gap` | 1 |
| `phraseology-later` | 8 |

## Policy-Blocked

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::19cfd36a9fce4587` | `POLICY-1`; scenario evidence only | ICAO 9432 §4.5.1 says aircraft are *usually* transferred to TOWER at/approaching the runway-holding position. The LOWG trace proves one usual-pattern scenario, not a universal law. |
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
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3` | `PHRASE-1`; support-only / review-only | Requires rendered take-off clearance phraseology and is classified as a support-only example, not standalone sim-executable coverage. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::152f0ffb84869af5` | `PHRASE-1` | Requires rendered immediate-departure line-up phraseology. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::42b0460ed4f07751` | `PHRASE-1` | Requires rendered immediate-departure readiness query. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::a93888a25f0bad03` | `PHRASE-1` | Requires rendered `LINE UP AND WAIT` phraseology and acknowledgement. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::db8a2c3dcd586b0e` | `PHRASE-1` | Requires rendered taxi phraseology ambiguity checking. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::9b30810984e06a35` | `PHRASE-1` | Requires rendered conditional-clearance element ordering. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6b5a0d8b27525cbd` | `PHRASE-1` | Requires rendered repeated stop-immediately instruction and callsign. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::8af22eb8d9795cef` | `PHRASE-1` | Requires rendered runway-number inclusion in take-off clearance where confusion is possible. |
