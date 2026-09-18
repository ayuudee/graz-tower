# Chunk 06 Expected Gaps

Chunk: `chunk-06-go-around-after-landing-aerodrome-info`

This file records ICAO 9432 §4.8 / §4.9 / §4.10 source units that cannot
honestly be marked covered-green with the current model/evidence surface.

## Summary

| Source-unit state | Units |
|---|---:|
| `covered-structural` essential-information vocabulary | 8 |
| `model-gap` | 1 |
| `model-gap` + `policy-blocked` | 3 |
| `policy-blocked` | 3 |
| split residual | 1 |
| `phraseology-later` | 2 |

The VFR traffic-circuit continuation row and the essential-information category
rows now have structural evidence-vocabulary coverage. The after-landing
runway-vacated / taxi-to-stand rendered wording branch is covered. Timing,
omission, open pertinence, essential-aerodrome-information phraseology,
helicopter air-taxi phraseology, residual first-right/vacating wording, live
sim projection, and IFR missed-approach rows remain gaps.

## Covered-Structural Essential-Information Vocabulary

| Source unit | Test | Reason |
|---|---|---|
| `icao9432-extracted::essential_aerodrome_information_4_10_en::01c0a4bc62b1e926` | `Icao9432EssentialAerodromeInformationEvidenceTest` | Structural vocabulary for `WaterOnMovementArea` plus runway/taxiway/apron facets. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::18288908932d5ee8` | `Icao9432EssentialAerodromeInformationEvidenceTest` | Structural vocabulary for movement-area and associated-facility domains with safety relevance. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::502221a46fcc2879` | `Icao9432EssentialAerodromeInformationEvidenceTest` | Structural vocabulary for `RoughOrBrokenSurface` plus runway/taxiway/apron facets. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::66196c8442372a96` | `Icao9432EssentialAerodromeInformationEvidenceTest` | Structural vocabulary for construction/maintenance on or adjacent to movement area. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::736cc42a00337fee` | `Icao9432EssentialAerodromeInformationEvidenceTest` | Structural vocabulary for snow banks/drifts adjacent to runway/taxiway/apron. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::9b6c9dbc2af2b5b0` | `Icao9432EssentialAerodromeInformationEvidenceTest` | Structural vocabulary for temporary hazards including parked aircraft and birds. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::a531dea421075380` | `Icao9432EssentialAerodromeInformationEvidenceTest` | Structural vocabulary for lighting-system failure or irregular operation. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::c874e24413f4cdee` | `Icao9432EssentialAerodromeInformationEvidenceTest` | Structural vocabulary for winter contamination plus runway/taxiway/apron facets. |

## Model Gaps

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::go_around_4_8_en::43c33a8e74b02873` | source-mapped instrument missed-approach scenario | Current scenarios are VFR circuit operations; there is no source-mapped end-to-end instrument approach / published missed-approach procedure scenario proving this ICAO 9432 §4.8 default. |

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

## Split Residuals

| Source unit | Covered branch | Residual |
|---|---|---|
| `icao9432-extracted::after_landing_4_9_en::df25159c1e7b94a3` | `Icao9432PhraseologyEvidenceTest` covers rendered `CONTACT GROUND 118.350` plus frequency readback wording through the production `ContactFrequency` / `FrequencyReadback` renderers. | `TAKE FIRST RIGHT WHEN VACATED` and the `FIRST RIGHT` readback remain blocked until rendered vacating-runway instruction/readback phraseology exists. |

## Phraseology-Later

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::after_landing_4_9_en::203b53733da22603` | `PHRASE-1` | Requires rendered helicopter air-taxi phraseology. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75` | `PHRASE-1` | Requires rendered essential-aerodrome-information example phraseology. |
