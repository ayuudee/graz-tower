# Chunk 06 Source Plan

Chunk: `chunk-06-go-around-after-landing-aerodrome-info`

Scope: ICAO 9432 §4.8 go around, §4.9 after landing, and §4.10 essential
aerodrome information.

## Source Audit

- Accepted source units: 20.
- Sections:
  - `go_around_4_8_en`: 3 units.
  - `after_landing_4_9_en`: 5 units.
  - `essential_aerodrome_information_4_10_en`: 12 units.
- Quote audit: all 20 accepted source units were checked against
  `research/txt/icao9432-extracted.txt`. Several stored quote excerpts do not
  literal-match because the extracted text wraps lines, splits list item `f)`,
  or stores phraseology examples over multiple lines; the underlying source
  text is present in §4.8-§4.10.

## Review Position

This chunk must not turn ICAO 9432 prose into simulator law verbatim. The
go-around default for VFR aircraft is a strong candidate for green coverage
because the existing simulator models VFR circuit recovery after go-around.
The instrument missed-approach default is not the same claim and should remain
a model gap unless an instrument approach / missed approach procedure surface
exists.

After-landing timing rows use `should` and exception language, so they need
policy concepts before they become universal assertions. Essential aerodrome
information rows mostly define categories and timing for operational
information that the sim does not yet project from typed movement-area
hazards, facility serviceability, or aircraft-known information. fn-62 adds
structural evidence vocabulary only; it is not live sim-observed information.

Typed protocol instructions are not rendered phraseology. Phraseology examples
remain `phraseology-later` unless rendered wording is asserted.

## Planned Coverage

| Source unit | Source text / claim | Initial classifier | Planned state | Planned evidence / blocker |
|---|---|---|---|---|
| `icao9432-extracted::go_around_4_8_en::43c33a8e74b02873` | Instrument approach go-around defaults to the missed approach procedure unless instructed otherwise. | `testable-now` | `model-gap` | Current goldens are VFR circuit scenarios. There is no source-mapped end-to-end instrument-approach scenario proving a published missed-approach procedure. |
| `icao9432-extracted::go_around_4_8_en::6c8993a0519d5d64` | Transmissions to aircraft going around should be brief and kept to a minimum. | `needs-policy-type` | `policy-blocked` with possible scenario evidence | Needs `OperationalGuidancePolicy` / radio-load metric before it can be asserted universally. Existing traces can show concise transmissions only as scenario evidence. |
| `icao9432-extracted::go_around_4_8_en::c3581d40a48406bb` | VFR aircraft continue in the normal traffic circuit unless instructed otherwise. | `testable-now` | `covered-green` candidate | Existing G3a VFR go-around trace should prove `Report(GoingAround)` followed by re-entry to downwind / recovery circuit and later landing clearance. |
| `icao9432-extracted::after_landing_4_9_en::203b53733da22603` | Air-taxi to helicopter stand example phraseology. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; helicopter air-taxi rendered phraseology is not modelled. |
| `icao9432-extracted::after_landing_4_9_en::4a512226eec962cb` | Unless otherwise advised, pilots should remain on tower frequency until runway vacated. | `needs-policy-type` | `policy-blocked` with possible scenario evidence | Needs frequency-retention policy / observation that no alternate advice was issued; current traces may show a LOWG instance only. |
| `icao9432-extracted::after_landing_4_9_en::5d742dc66caa1790` | Unless absolutely necessary, controllers should not issue taxi instructions until landing roll completed. | `needs-policy-type` | `policy-blocked` | Needs `ClearanceTimingPolicy` and landing-roll/taxi-instruction timing evidence with emergency/necessity classification. |
| `icao9432-extracted::after_landing_4_9_en::df25159c1e7b94a3` | Vacating-runway / contact-ground example phraseology. | `phraseology-later` | `split: CONTACT GROUND wording covered; TAKE FIRST RIGHT WHEN VACATED remains blocked` | `Icao9432PhraseologyEvidenceTest` covers the rendered contact-ground branch; first-right/vacating wording remains residual until rendered vacating-runway instruction/readback phraseology exists. |
| `icao9432-extracted::after_landing_4_9_en::e30350fdecad45a1` | Runway-vacated / taxi-to-stand example phraseology. | `phraseology-later` | `covered-green` | `Icao9432PhraseologyEvidenceTest` asserts rendered runway-vacated report, taxi-to-stand instruction, and matching ordered taxi-route readback evidence. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::01c0a4bc62b1e926` | Essential aerodrome information includes water on runway, taxiway, or apron. | `structural-vocabulary` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest` covers structural `WaterOnMovementArea` vocabulary plus runway/taxiway/apron facets. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::1306eb5cc586df34` | Essential aerodrome information may be omitted when known from other sources. | `needs-observation-fact` | `model-gap` + `policy-blocked` | Requires aircraft-known-information state and source-of-information policy. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::18288908932d5ee8` | Essential aerodrome information definition: movement area / facilities necessary for safe aircraft operation. | `structural-vocabulary` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest` covers movement-area and associated-facility domains with safety relevance. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc` | Essential aerodrome information should be passed before start-up/taxi and before final approach where possible. | `needs-observation-fact` | `model-gap` + `policy-blocked` | Existing FN43-GAP-1 expected-gap test covers missing aircraft-facing information receipt and timing evidence. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::502221a46fcc2879` | Essential aerodrome information includes rough or broken movement-area surfaces. | `structural-vocabulary` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest` covers structural `RoughOrBrokenSurface` vocabulary plus runway/taxiway/apron facets. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::66196c8442372a96` | Essential aerodrome information includes construction or maintenance work on or adjacent to movement area. | `structural-vocabulary` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest` covers structural `ConstructionOrMaintenance` vocabulary with on/adjacent movement-area facets. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::736cc42a00337fee` | Essential aerodrome information includes snow banks or drifts adjacent to movement area. | `structural-vocabulary` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest` covers structural `SnowBankOrDrift` vocabulary with adjacent runway/taxiway/apron facets. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75` | Example essential-aerodrome-information phraseology for construction, lighting, and runway condition messages. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; rendered caution / serviceability / runway-condition examples are not asserted. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::9b6c9dbc2af2b5b0` | Essential aerodrome information includes other temporary hazards, including parked aircraft and birds on the ground or in the air. | `structural-vocabulary` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest` covers structural `TemporaryHazard` vocabulary with parked-aircraft and bird facets. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::a531dea421075380` | Essential aerodrome information includes failure or irregular operation of aerodrome lighting systems. | `structural-vocabulary` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest` covers structural lighting-system vocabulary. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::c874e24413f4cdee` | Essential aerodrome information includes snow, slush, or ice on runway, taxiway, or apron. | `structural-vocabulary` | `covered-structural` | `Icao9432EssentialAerodromeInformationEvidenceTest` covers structural `WinterContamination` vocabulary plus runway/taxiway/apron facets. |
| `icao9432-extracted::essential_aerodrome_information_4_10_en::c92651071a9c009e` | Essential aerodrome information includes any other pertinent information. | `needs-observation-fact` | `model-gap` + `policy-blocked` | Needs a typed open-category essential-information model and relevance policy. |

## Planned Tests

1. **VFR go-around circuit continuation**: add a chunk-06 source-backed
   scenario using a LOWG VFR circuit-training go-around trace. The test should
   cite only `c3581d40a48406bb` and prove same-aircraft VFR circuit context,
   `Report(GoingAround)` followed by a post-GA `ReportEvent.Downwind`, then a
   later recovery `ClearedToLand`, then `RunwayVacated`. The test must not
   substitute tower stage regression or later landing alone for the
   aircraft-facing normal-circuit continuation witness.
2. **Expected-gap specs**:
   - IFR missed approach procedure model gap;
   - go-around radio brevity policy gap;
   - after-landing tower-frequency / taxi-timing policy gaps;
   - essential-aerodrome-information model and policy gaps.
3. **Coverage report**: one final state per source unit, preserving
   phraseology, policy, and model boundaries.

## Review Considerations

- FP / type safety: new source refs should be typed `EvidenceSourceRef`
  entries. No production state changes are planned.
- Test architecture: the VFR test must prove circuit continuation after
  go-around, not just a `GoAround` / `GoingAround` transmission.
- Impact: avoid duplicating the whole G3a golden in a brittle way. The chunk
  test may reuse the same fixture shape but should assert only the source-unit
  claim.
- Operational correctness: ICAO 9432 §4.8 distinguishes IFR missed-approach
  procedure from VFR traffic-circuit continuation. These cannot be collapsed
  into a single generic go-around assertion.
