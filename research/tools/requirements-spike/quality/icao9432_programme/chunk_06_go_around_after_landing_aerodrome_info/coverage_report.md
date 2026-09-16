# Chunk 06 Coverage Report

Chunk: ICAO 9432 go-around, after landing, and essential aerodrome
information.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` | 1 |
| `covered-red` | 0 |
| `model-gap` | 9 |
| `model-gap` + `policy-blocked` | 3 |
| `policy-blocked` | 3 |
| `phraseology-later` | 4 |

The only covered-green source unit in this chunk is the VFR go-around default:
after a VFR `Report(GoingAround)`, the aircraft continues into the normal
traffic circuit. The source-mapped test proves this with an aircraft-facing
post-GA `Downwind` report before the recovery landing clearance. It does not
claim coverage for the IFR missed-approach branch or for radio brevity policy.

## Coverage Table

| Source unit | Final state | Test / blocker | Claim |
|---|---|---|---|
| `icao9432-extracted::go_around_4_8_en::43c33a8e74b02873` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest` | Instrument approach go-around defaults to the missed approach procedure unless instructed otherwise. |
| `icao9432-extracted::go_around_4_8_en::6c8993a0519d5d64` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `OperationalGuidancePolicy` | Transmissions to aircraft going around should be brief and kept to a minimum. |
| `icao9432-extracted::go_around_4_8_en::c3581d40a48406bb` | `covered-green` | `Icao9432Chunk06GoAroundEvidenceTest` | VFR aircraft continues in the normal traffic circuit unless instructed otherwise. |
| `icao9432-extracted::after_landing_4_9_en::203b53733da22603` | `phraseology-later` | `PHRASE-1` | Air-taxi to helicopter stand example phraseology. |
| `icao9432-extracted::after_landing_4_9_en::4a512226eec962cb` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Pilot remains on tower frequency until runway vacated unless otherwise advised. |
| `icao9432-extracted::after_landing_4_9_en::5d742dc66caa1790` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `ClearanceTimingPolicy` | Controller should not issue taxi instructions until landing roll completed unless absolutely necessary. |
| `icao9432-extracted::after_landing_4_9_en::df25159c1e7b94a3` | `phraseology-later` | `PHRASE-1` | Vacating-runway and contact-ground example phraseology. |
| `icao9432-extracted::after_landing_4_9_en::e30350fdecad45a1` | `phraseology-later` | `PHRASE-1` | Runway-vacated and taxi-to-stand example phraseology. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::01c0a4bc62b1e926` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1` | Essential aerodrome information includes water on runway, taxiway, or apron. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::1306eb5cc586df34` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1`; `OperationalGuidancePolicy` | Essential aerodrome information may be omitted when already known from other sources. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::18288908932d5ee8` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1` | Essential aerodrome information concerns the movement area and associated facilities needed for safe operation. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1`; `ClearanceTimingPolicy` | Essential aerodrome information should be passed before start-up/taxi and before final approach where possible. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::502221a46fcc2879` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1` | Essential aerodrome information includes rough or broken movement-area surfaces. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::66196c8442372a96` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1` | Essential aerodrome information includes construction or maintenance work on or adjacent to the movement area. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::736cc42a00337fee` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1` | Essential aerodrome information includes snow banks or drifts adjacent to movement areas. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75` | `phraseology-later` | `PHRASE-1` | Example essential-aerodrome-information phraseology. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::9b6c9dbc2af2b5b0` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1` | Essential aerodrome information includes other temporary hazards, including parked aircraft and birds on the ground or in the air. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::a531dea421075380` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1` | Essential aerodrome information includes failure or irregular operation of aerodrome lighting systems. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::c874e24413f4cdee` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1` | Essential aerodrome information includes snow, slush, or ice on runway, taxiway, or apron. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::c92651071a9c009e` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1`; `OperationalGuidancePolicy` | Essential aerodrome information includes any other pertinent information. |

## Verification

- Source-unit provenance: all 20 accepted candidate JSON records are present in
  the registry with `lifecycle.state = accepted`. Source excerpts were checked
  against `research/txt/icao9432-extracted.txt`; non-literal hits are explained
  by line wrapping, list-item splitting, or multi-line phraseology examples in
  the extracted text.
- Verification commands:
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432Chunk06GoAroundEvidenceTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`.
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*' --tests '*.EvidenceSourceCatalogTest'`.
  `./gradlew-nix :sim:jvmTest`.
  `./gradlew-nix detekt`.
  `.flow/bin/flowctl validate --epic fn-56-icao-9432-chunk-06-go-around-after`.
  `git diff --check`.

## Review Considerations

- FP / type safety: permanent source refs use typed `EvidenceSourceRef`
  records. No production state or evidence payload type was added.
- Test architecture: the covered-green VFR row is proven by a real LOWG VFR
  trace with `GoingAround -> post-GA Downwind -> ClearedToLand -> RunwayVacated`.
  The test cites only the VFR source unit.
- Impact: no controller, pilot, sim behaviour, phraseology rendering, or policy
  behaviour was changed.
- Operational correctness: ICAO 9432 §4.8's IFR and VFR branches remain
  distinct; §4.9 `should` rows and §4.10 information categories remain gaps
  until policy/model support exists.
