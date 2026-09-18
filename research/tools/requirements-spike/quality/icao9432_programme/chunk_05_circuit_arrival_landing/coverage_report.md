# Chunk 05 Coverage Report

Chunk: ICAO 9432 circuit, final approach, and landing.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` | 2 |
| `covered-red` | 0 |
| `model-gap` | 2 |
| `policy-blocked` | 6 |
| `phraseology-later` | 13 |

The covered-green source units in this chunk are the touch-and-go request
capability and the rendered `CLEARED TOUCH AND GO` clearance phrase. They are
covered by real LOWG circuit-training traces that observe
`ReportEvent.Downwind(circuitIntent = TOUCH_AND_GO)` before
`ClearedTouchAndGo`, plus fn-61 rendered phraseology evidence for the clearance
wording.

## Coverage Table

| Source unit | Final state | Test / blocker | Claim |
|---|---|---|---|
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::58ae778732ec6347` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1` | Right-hand traffic circuit pattern should be specified. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::667985b4a6159d18` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Join request should be early enough for planned entry with traffic considered. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::a4fcedaac8a838e1` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1` | ATIS receipt should be acknowledged in initial call. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::d65650486b4d1b8f` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `LocalProcedurePolicy` | Straight-in approach may be possible depending on traffic / arrival direction. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::28bea79b8da559cd` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `ControllerInterventionPolicy` | Circuit coordination may require delay or acceleration instructions. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::34445db09fdd6e0a` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `LocalProcedurePolicy` | Pilot entering circuit shall provide position reports under local procedures. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::7e3aec5e5fd60c41` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1` | Pilot should confirm ATIS when contacting tower. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::b64030acf6ef4bbd` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `LocalProcedurePolicy` | Straight-in approach may be possible depending on traffic / arrival direction. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::dcf776a1b9c8a303` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `LocalProcedurePolicy` | Pilot in circuit makes routine reports required by local procedures. |
| `icao9432-extracted::final_approach_landing_4_7_en::00baaf3c55155044` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1`; distance evidence | `FINAL` report timing. |
| `icao9432-extracted::final_approach_landing_4_7_en::0ece166e11d7728e` | `covered-green` | `Icao9432TouchAndGoSourceBackedScenarioTest` | Pilot may request touch-and-go in circuit training. |
| `icao9432-extracted::final_approach_landing_4_7_en::1327871f46c1d348` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1` | `WHEELS APPEAR UP` phrase. |
| `icao9432-extracted::final_approach_landing_4_7_en::1960f59d8b9efecb` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1` | `LANDING GEAR APPEARS DOWN` phrase. |
| `icao9432-extracted::final_approach_landing_4_7_en::1db805d02051bf47` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1` | Wheel does-not-appear-up/down phrase. |
| `icao9432-extracted::final_approach_landing_4_7_en::4c698a5ad52a30e4` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1`; distance evidence | `LONG FINAL` when final turn is greater than 7 km / 4 NM. |
| `icao9432-extracted::final_approach_landing_4_7_en::63836b7aef62a6f6` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest` | Pilot may request fly-past for visual inspection from ground. |
| `icao9432-extracted::final_approach_landing_4_7_en::70e781a65920c075` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1`; policy/distance evidence | Straight-in `LONG FINAL` at about 15 km / 8 NM. |
| `icao9432-extracted::final_approach_landing_4_7_en::7bbc96aa5ee36893` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1` | Wheel appears up/down phrase. |
| `icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4` | `covered-green` | `Icao9432PhraseologyEvidenceTest`; `RenderedPhraseologyTrace` | ATC may clear touch-and-go using `CLEARED TOUCH AND GO`. |
| `icao9432-extracted::final_approach_landing_4_7_en::aaf5262d8e7750b2` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1` | Low-pass example dialogue. |
| `icao9432-extracted::final_approach_landing_4_7_en::b1c21e2f70bbf36f` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1`; traffic policy | Unable touch-and-go alternative instructions due traffic. |
| `icao9432-extracted::final_approach_landing_4_7_en::e17d8b9b99c43496` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest` | Training approach along or parallel to runway without landing. |
| `icao9432-extracted::final_approach_landing_4_7_en::fbae3a11e1d068a3` | `phraseology-later` | `Icao9432ModelGapSourceUnitSpecTest`; `PHRASE-1` | `LONG FINAL` / `FINAL` example dialogue. |

## Verification

- Source-unit provenance: all 23 accepted candidate JSON records have source
  quotes that normalize-match `research/txt/icao9432-extracted.txt`.
- Focused verification:
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432TouchAndGoSourceBackedScenarioTest' --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidencePermanentTwentyCaseTest' --tests '*.EvidenceSourceCatalogTest'`.

## Review Considerations

- FP / type safety: permanent green citations use typed `EvidenceSourceRef`.
  Rendered phraseology evidence is a sealed test-side payload with typed
  phrase tokens.
- Test architecture: touch-and-go request is proven by pilot intent before
  controller clearance. Touch-and-go wording is proven by rendered phraseology
  evidence over the observed controller clearance. Other phraseology and policy
  rows remain explicit gaps.
- Impact: no controller, pilot, sim behaviour, or policy behaviour was
  changed. Phraseology rendering is a test-side evidence adapter over observed
  typed transmissions.
- Operational correctness: ICAO 9432 §4.6 / §4.7 local-procedure, traffic,
  permissive, and phraseology modalities remain distinct.
