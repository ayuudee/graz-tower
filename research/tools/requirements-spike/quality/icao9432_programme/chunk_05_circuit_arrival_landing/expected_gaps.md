# Chunk 05 Expected Gaps

Chunk: `chunk-05-circuit-arrival-landing`

This file records ICAO 9432 §4.6 / §4.7 source units that cannot honestly be
marked covered-green with the current model/evidence surface.

## Summary

| Source-unit state | Units |
|---|---:|
| `policy-blocked` | 6 |
| `model-gap` | 2 |
| `phraseology-later` | 14 |

## Policy-Blocked

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::667985b4a6159d18` | `POLICY-1` | Needs planned circuit-entry timing and traffic-accounting policy/evidence. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::d65650486b4d1b8f` | `LocalProcedurePolicy` | Straight-in approach availability depends on traffic and arrival direction; not universal law. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::28bea79b8da559cd` | `ControllerInterventionPolicy` | Needs typed delay/accelerate circuit instruction policy and trigger evidence. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::34445db09fdd6e0a` | `LocalProcedurePolicy`; scenario evidence only | LOWG circuit traces can show reports, but cannot prove which reports local procedures require. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::b64030acf6ef4bbd` | `LocalProcedurePolicy` | Bilingual continuation of straight-in permissive/local-procedure concept. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::dcf776a1b9c8a303` | `LocalProcedurePolicy`; scenario evidence only | Routine report requirements are local-procedure-defined. |

## Model Gaps

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::final_approach_landing_4_7_en::63836b7aef62a6f6` | low-pass request workflow | Protocol has `ClearedLowApproach`, but no source-mapped pilot request / visual-inspection fly-past scenario. |
| `icao9432-extracted::final_approach_landing_4_7_en::e17d8b9b99c43496` | training low-approach workflow | No current mission/scenario workflow for an approach along or parallel to the runway without landing. |

## Phraseology-Later

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::58ae778732ec6347` | `PHRASE-1` | Requires rendered right-hand circuit pattern wording. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::a4fcedaac8a838e1` | `PHRASE-1` | Requires rendered initial-call ATIS acknowledgement. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::7e3aec5e5fd60c41` | `PHRASE-1` | Requires rendered tower initial-contact ATIS confirmation. |
| `icao9432-extracted::final_approach_landing_4_7_en::00baaf3c55155044` | `PHRASE-1`; distance-at-report evidence | Requires rendered `FINAL` report and 7 km / 4 NM threshold evidence. |
| `icao9432-extracted::final_approach_landing_4_7_en::1327871f46c1d348` | `PHRASE-1` | Requires rendered undercarriage phrase `WHEELS APPEAR UP`. |
| `icao9432-extracted::final_approach_landing_4_7_en::1960f59d8b9efecb` | `PHRASE-1` | Requires rendered undercarriage phrase `LANDING GEAR APPEARS DOWN`. |
| `icao9432-extracted::final_approach_landing_4_7_en::1db805d02051bf47` | `PHRASE-1` | Requires rendered wheel-not-appearing-up/down phrase. |
| `icao9432-extracted::final_approach_landing_4_7_en::4c698a5ad52a30e4` | `PHRASE-1`; distance-at-report evidence | Requires rendered `LONG FINAL` report and greater-than-7 km / 4 NM threshold evidence. |
| `icao9432-extracted::final_approach_landing_4_7_en::70e781a65920c075` | `PHRASE-1`; straight-in policy; distance-at-report evidence | Requires rendered straight-in `LONG FINAL` report at about 15 km / 8 NM. |
| `icao9432-extracted::final_approach_landing_4_7_en::7bbc96aa5ee36893` | `PHRASE-1` | Requires rendered wheel-appearing-up/down phrase. |
| `icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4` | `PHRASE-1`; typed-trace evidence only | Current traces prove typed `ClearedTouchAndGo`, not rendered `CLEARED TOUCH AND GO` wording. |
| `icao9432-extracted::final_approach_landing_4_7_en::aaf5262d8e7750b2` | `PHRASE-1` | Requires rendered low-pass example dialogue. |
| `icao9432-extracted::final_approach_landing_4_7_en::b1c21e2f70bbf36f` | `PHRASE-1`; traffic-congestion policy | Requires rendered unable-touch-and-go phraseology and traffic-congestion policy. |
| `icao9432-extracted::final_approach_landing_4_7_en::fbae3a11e1d068a3` | `PHRASE-1` | Requires rendered `LONG FINAL` / `FINAL` example dialogue, wind, and landing clearance wording. |
