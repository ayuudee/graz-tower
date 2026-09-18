# fn-85 Vehicle / Tow Phraseology Manifest

## Objective

Close the remaining `PHRASE-1` branches for the two chunk 07 split rows that
already have structured vehicle/tow evidence:

| Source unit | Current state | Target state | Evidence required |
|---|---|---|---|
| `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::7759017903acf140` | `split: structured first-call content covered-green; rendered wording phraseology-later` | `covered-green rendered vehicle first-call branch` | Rendered vehicle-driver first-call evidence must include vehicle call sign, current position, intended destination, and route when present. |
| `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71` | `split: structured type/operator metadata covered-green; rendered wording phraseology-later` | `covered-green rendered tow-request metadata branch` | Rendered tow request evidence must include the vehicle/tow call sign, receiving station/addressee, aircraft under tow, aircraft type, and operator when applicable. |

## Non-Goals

- Do not implement apron traffic policy, dangerous-situation intervention
  policy, vehicle vigilance/local-procedure compliance, expected aircraft
  operation triggers, or geometry-derived tow extent.
- Do not turn aircraft taxi/runway phraseology into vehicle evidence.
- Do not broaden operational behaviour. This is an evidence/phraseology slice
  over already-authored vehicle transmission payloads.
- Do not claim generic vehicle phraseology coverage beyond the two named source
  units.

## Implementation Plan

1. Re-read current vehicle/tow radio payloads in `VehicleMovement.kt`,
   `Step.kt`, and source-backed tests to identify the typed transmission
   surface that already carries first-call and tow metadata.
2. Add narrow rendered vehicle phraseology value types and renderers in the sim
   phraseology surface. Renderer success must carry template, tokens, and text.
   Renderer non-success must be a typed unsupported-payload result for vehicle
   transmissions not covered by fn-85; no catch-all success paths.
3. Add evidence DSL selectors for:
   - rendered vehicle first-call with call sign, position, destination, and
     ordered route;
   - rendered tow request with addressee/receiving station, aircraft under tow,
     aircraft type, and operator.
4. Add selector tests for positive evidence plus absent rendered evidence,
   mismatched route/metadata, malformed/missing rendered-token cases, and
   aircraft taxi/runway phraseology being rejected as vehicle evidence.
5. Extend source-backed chunk 07 tests to assert rendered phraseology alongside
   the existing structured evidence for the two source units.
6. Update `EvidenceSourceCatalog`, catalog tests, `coverage_report.md`,
   `expected_gaps.md`, `source_plan.md`, and
   `implementation_blocker_manifest.csv` so the two PHRASE-1 residual branches
   are either removed or renamed as covered rendered branches.
7. Validate with focused tests, broad relevant tests, detekt, flow validation,
   implementation review, completion review, and `git diff --check`.

## Review Considerations

- FP / type safety: Renderer dispatch must be typed and explicit. If vehicle
  transmissions are sealed, use exhaustive `when`; otherwise use narrow typed
  entry points over concrete payload classes. Missing wording support should be
  a typed unsupported result, not an empty phrase or false positive.
- Test architecture: Keep tests high-level and source-mapped. Selector tests
  prove the small matching algebra. Renderer unsupported payloads, selector
  no-match, selector mismatch, and malformed rendered tokens must be distinct
  failure surfaces. Source-backed tests prove the real payloads produce the
  source-unit evidence.
- Impact: This should only add rendered evidence over existing vehicle/tow
  transmissions. It should not affect controller/pilot behaviour, vehicle
  state progression, or aircraft phraseology.
- Operational correctness: ICAO Doc 9432 §5.2 requires vehicle first-call
  identity/position/destination/route content where possible. ICAO Doc 9432
  §5.4 requires tow drivers to state aircraft type and operator where
  applicable. These are vehicle-driver / towing statements, not aircraft pilot
  procedures.
