# Chunk 06 Coverage Report

Chunk: ICAO 9432 go-around, after landing, and essential aerodrome
information.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` | 4 |
| `covered-structural` | 8 |
| `covered-red` | 0 |
| `model-gap` | 1 |
| `model-gap` + `policy-blocked` | 3 |
| `policy-blocked` | 3 |
| `split residual` | 0 |
| `phraseology-later` | 1 |

The covered-green source units are the VFR go-around default, the rendered
after-landing first-right / contact-ground wording branch, the rendered
after-landing runway-vacated / taxi-to-stand wording branch, and the rendered
after-landing helicopter air-taxi wording branch. The
essential-aerodrome-information category/definition rows are covered only as
structural evidence vocabulary. They do not claim live sim projection, timing,
receipt, omission policy, open pertinence, or rendered phraseology coverage.

## Coverage Table

| Source unit | Final state | Test / blocker | Claim |
|---|---|---|---|
| `icao9432-extracted::go_around_4_8_en::43c33a8e74b02873` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest` | Instrument approach go-around defaults to the missed approach procedure unless instructed otherwise. |
| `icao9432-extracted::go_around_4_8_en::6c8993a0519d5d64` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `OperationalGuidancePolicy` | Transmissions to aircraft going around should be brief and kept to a minimum. |
| `icao9432-extracted::go_around_4_8_en::c3581d40a48406bb` | `covered-green` | `Icao9432Chunk06GoAroundEvidenceTest` | VFR aircraft continues in the normal traffic circuit unless instructed otherwise. |
| `icao9432-extracted::after_landing_4_9_en::203b53733da22603` | `covered-green rendered helicopter air-taxi phraseology` | `Icao9432PhraseologyEvidenceTest` | Synthetic ICAO 9432 example renders `G-HELI AIR-TAXI TO HELICOPTER STAND` and `AIR-TAXI TO HELICOPTER STAND G-HELI` through typed transmission records. |
| `icao9432-extracted::after_landing_4_9_en::4a512226eec962cb` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Pilot remains on tower frequency until runway vacated unless otherwise advised. |
| `icao9432-extracted::after_landing_4_9_en::5d742dc66caa1790` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `ClearanceTimingPolicy` | Controller should not issue taxi instructions until landing roll completed unless absolutely necessary. |
| `icao9432-extracted::after_landing_4_9_en::df25159c1e7b94a3` | `covered-green rendered first-right/contact-ground phraseology` | `Icao9432PhraseologyEvidenceTest` | Synthetic ICAO 9432 example renders `TAKE FIRST RIGHT WHEN VACATED`, `CONTACT GROUND 118.350`, and `FIRST RIGHT 118.350 FASTAIR 345` through typed transmission records. |
| `icao9432-extracted::after_landing_4_9_en::e30350fdecad45a1` | `covered-green` | `Icao9432PhraseologyEvidenceTest` | Runway-vacated and taxi-to-stand rendered phraseology. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::01c0a4bc62b1e926` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest`; structural evidence vocabulary | Essential aerodrome information includes water on runway, taxiway, or apron. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::1306eb5cc586df34` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1`; `OperationalGuidancePolicy` | Essential aerodrome information may be omitted when already known from other sources. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::18288908932d5ee8` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest`; structural evidence vocabulary | Essential aerodrome information concerns the movement area and associated facilities needed for safe operation. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1`; `ClearanceTimingPolicy` | Essential aerodrome information should be passed before start-up/taxi and before final approach where possible. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::502221a46fcc2879` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest`; structural evidence vocabulary | Essential aerodrome information includes rough or broken movement-area surfaces. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::66196c8442372a96` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest`; structural evidence vocabulary | Essential aerodrome information includes construction or maintenance work on or adjacent to the movement area. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::736cc42a00337fee` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest`; structural evidence vocabulary | Essential aerodrome information includes snow banks or drifts adjacent to movement areas. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75` | `phraseology-later` | `PHRASE-1` | Example essential-aerodrome-information phraseology. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::9b6c9dbc2af2b5b0` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest`; structural evidence vocabulary | Essential aerodrome information includes other temporary hazards, including parked aircraft and birds on the ground or in the air. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::a531dea421075380` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest`; structural evidence vocabulary | Essential aerodrome information includes failure or irregular operation of aerodrome lighting systems. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::c874e24413f4cdee` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest`; structural evidence vocabulary | Essential aerodrome information includes snow, slush, or ice on runway, taxiway, or apron. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::c92651071a9c009e` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `FN43-GAP-1`; `OperationalGuidancePolicy` | Essential aerodrome information includes any other pertinent information. |

## Verification

- Source-unit provenance: all 20 accepted candidate JSON records are present in
  the registry with `lifecycle.state = accepted`. Source excerpts were checked
  against `research/txt/icao9432-extracted.txt`; non-literal hits are explained
  by line wrapping, list-item splitting, or multi-line phraseology examples in
  the extracted text.
- Verification commands:
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest'`.
  `./gradlew-nix detekt`.
  `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`.
  `scripts/ralph/flowctl validate --epic fn-87-icao-9432-after-landing-helicopter-air --json`.
  `git diff --check`.

## Review Considerations

- FP / type safety: permanent source refs use typed `EvidenceSourceRef`
  records. Essential aerodrome information categories use closed evidence
  enums rather than strings. Rendered after-landing phraseology adds typed
  templates/tokens, with unsupported phraseology remaining explicit unsupported
  evidence rather than implied compliance.
- Test architecture: the covered-green VFR row is proven by a real LOWG VFR
  trace with `GoingAround -> post-GA Downwind -> ClearedToLand -> RunwayVacated`.
  The after-landing runway-vacated / taxi-to-stand row is proven by rendered
  phraseology facts in strict `RUNWAY VACATED -> TAXI TO STAND -> readback`
  order. The helicopter air-taxi row is proven by a synthetic typed ICAO
  example trace in strict `AIR-TAXI TO HELICOPTER STAND -> readback` order.
  The first-right / contact-ground row is proven by a synthetic typed
  ICAO example trace in strict `TAKE FIRST RIGHT WHEN VACATED -> CONTACT
  GROUND -> FIRST RIGHT + frequency readback` order, while unsupported
  vacating/readback variants remain explicit unsupported evidence.
- Impact: no controller, pilot, movement, clearance, or policy behaviour was
  changed. The phraseology renderer/evidence surface was extended for supported
  after-landing templates only; §4.10 category coverage remains structural
  evidence-vocabulary coverage.
- Operational correctness: ICAO 9432 §4.8's IFR and VFR branches remain
  distinct; §4.9 `should` rows and §4.10 timing/omission/open-pertinence rows
  remain gaps until policy/model support exists.
