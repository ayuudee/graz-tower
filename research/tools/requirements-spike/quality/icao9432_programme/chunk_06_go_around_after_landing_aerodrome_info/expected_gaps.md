# Chunk 06 Expected Gaps

Chunk: `chunk-06-go-around-after-landing-aerodrome-info`

This file records ICAO 9432 §4.8 / §4.9 / §4.10 source units that cannot
honestly be marked covered-green with the current model/evidence surface.

## Summary

| Source-unit state | Units |
|---|---:|
| `model-gap` | 9 |
| `model-gap` + `policy-blocked` | 3 |
| `policy-blocked` | 3 |
| `phraseology-later` | 4 |

The remaining chunk unit, VFR traffic-circuit continuation after go-around, is
planned as a covered-green candidate.

## Model Gaps

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::go_around_4_8_en::43c33a8e74b02873` | source-mapped instrument missed-approach scenario | Current scenarios are VFR circuit operations; there is no source-mapped end-to-end instrument approach / published missed-approach procedure scenario proving this ICAO 9432 §4.8 default. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::01c0a4bc62b1e926` | `FN43-GAP-1` | No typed water / surface-contamination information model for movement areas. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::18288908932d5ee8` | `FN43-GAP-1` | No typed essential-aerodrome-information model for movement areas and associated facilities. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::502221a46fcc2879` | `FN43-GAP-1` | No typed rough/broken surface model for runways, taxiways, or aprons. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::66196c8442372a96` | `FN43-GAP-1` | No typed construction/maintenance hazard model on or adjacent to the movement area. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::736cc42a00337fee` | `FN43-GAP-1` | No typed adjacent snow-bank/drift hazard model. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::9b6c9dbc2af2b5b0` | `FN43-GAP-1` | No general temporary-hazard information model. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::a531dea421075380` | `FN43-GAP-1` | No typed aerodrome-lighting serviceability model. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::c874e24413f4cdee` | `FN43-GAP-1` | No typed winter-contamination model for runways, taxiways, or aprons. |

## Model Gaps With Policy

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::essential_aerodrome_information_4_10_en::1306eb5cc586df34` | `FN43-GAP-1`; `OperationalGuidancePolicy` | Requires typed aircraft-known-information state and source-of-information policy before omission can be judged. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc` | `FN43-GAP-1`; `ClearanceTimingPolicy` | Existing expected-gap spec covers missing aircraft-facing information receipt and timing evidence. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::c92651071a9c009e` | `FN43-GAP-1`; `OperationalGuidancePolicy` | Requires typed open-category essential-information model and relevance policy. |

## Policy-Blocked

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::go_around_4_8_en::6c8993a0519d5d64` | `OperationalGuidancePolicy` | "Brief and kept to a minimum" needs a typed radio-load / brevity policy before universal assertion. |
| `icao9432-extracted::after_landing_4_9_en::4a512226eec962cb` | `POLICY-1` | Needs proof that no alternate frequency advice was issued and a policy for tower-frequency retention until vacated. |
| `icao9432-extracted::after_landing_4_9_en::5d742dc66caa1790` | `ClearanceTimingPolicy` | Needs landing-roll completion, taxi-instruction timing, and absolute-necessity classification. |

## Phraseology-Later

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::after_landing_4_9_en::203b53733da22603` | `PHRASE-1` | Requires rendered helicopter air-taxi phraseology. |
| `icao9432-extracted::after_landing_4_9_en::df25159c1e7b94a3` | `PHRASE-1` | Requires rendered vacating-runway / frequency-change example phraseology. |
| `icao9432-extracted::after_landing_4_9_en::e30350fdecad45a1` | `PHRASE-1` | Requires rendered runway-vacated / taxi-to-stand example phraseology. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75` | `PHRASE-1` | Requires rendered essential-aerodrome-information example phraseology. |
