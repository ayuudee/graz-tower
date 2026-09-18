# fn-85-icao-9432-vehicletow-phraseology ICAO 9432 vehicle/tow phraseology evidence

## Overview

Close the two remaining chunk 07 rendered-phraseology residual branches that
already have structured vehicle/tow evidence:

- `icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::7759017903acf140`
  (ICAO Doc 9432 §5.2): vehicle first-call identifies call sign, position,
  destination, and route when possible.
- `icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71`
  (ICAO Doc 9432 §5.4): tow driver states aircraft type and operator where
  applicable.

This is a rendered-evidence slice over existing vehicle transmission payloads,
not a vehicle-behaviour expansion.

## Scope

In scope:

- Render vehicle first-call phraseology from the existing typed vehicle
  first-call payload.
- Render tow-request phraseology from the existing typed tow payload.
- Add evidence selectors and selector tests for matching and mismatch cases.
- Extend the existing chunk 07 source-backed vehicle/tow tests with rendered
  wording assertions for the two named source units.
- Update `EvidenceSourceCatalog`, catalog tests, chunk 07 docs, and the central
  blocker manifest to remove these two open `PHRASE-1` residual branches.

Out of scope:

- Apron traffic policy, dangerous-situation intervention policy, vehicle
  vigilance/local-procedure compliance, expected-aircraft-operation runway
  conflict triggers, and geometry-derived tow extent.
- Reusing aircraft taxi/runway phraseology as vehicle evidence.
- Claiming generic vehicle phraseology coverage beyond the two named rows.

## Approach

1. Re-read `VehicleMovement.kt`, the vehicle/tow radio handling in `Step.kt`,
   `Icao9432VehicleMovementSourceBackedScenarioTest`, and
   `Icao9432VehicleTowingSourceBackedScenarioTest`.
2. Add a narrow rendered vehicle phraseology surface. The result algebra must
   distinguish success from unsupported payloads:
   - renderer success: concrete rendered phraseology with template, tokens, and
     text;
   - renderer unsupported: typed unsupported payload result for vehicle
     transmissions not covered by fn-85.
3. Keep selector failures separate from renderer unsupported results:
   - `NoMatch`: no rendered transmission of the requested kind exists;
   - mismatch: rendered transmission exists but call sign/route/tow metadata do
     not match;
   - malformed rendered evidence: token sequence cannot be parsed into the
     required typed shape.
4. Add selectors for vehicle first-call and tow request, with tests covering:
   missing call sign, reordered route, wrong destination, missing addressee,
   wrong aircraft under tow, missing aircraft type/operator, and aircraft
   phraseology being rejected as vehicle evidence.
5. Update source-backed tests to prove the actual existing vehicle/tow payloads
   render the wording needed by the two source units.
6. Update docs and
   `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
   counts. Remaining chunk 07 model/policy blockers must remain unchanged and
   visible.

## Quick commands

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432VehicleMovementSourceBackedScenarioTest' --tests '*.Icao9432VehicleTowingSourceBackedScenarioTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest'`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `./gradlew-nix detekt`
- `scripts/ralph/flowctl validate --epic fn-85-icao-9432-vehicletow-phraseology --json`
- `git diff --check`

## Acceptance

- [x] Rendered vehicle first-call evidence covers source unit
  `7759017903acf140` with call sign, position, destination, and ordered route.
- [x] Rendered tow-request metadata evidence covers source unit
  `4b103081585bfb71` with addressee/receiving station, aircraft under tow,
  aircraft type, and operator.
- [x] Vehicle/tow phraseology matching is typed and rejects mismatched,
  malformed, absent, and aircraft-phraseology evidence in selector tests.
- [x] Chunk 07 docs and
  `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
  no longer list these two rendered wording branches as open `PHRASE-1`
  residuals.
- [x] Remaining chunk 07 policy/model blockers stay explicit and unchanged.
- [x] Focused tests, broad relevant tests, detekt, flow validation,
  implementation review, completion review, and `git diff --check` pass.

## Review Considerations

- FP / type safety: Use typed render results. Avoid catch-all success paths,
  raw string parsing as the only source of truth, and `error()` for type-valid
  unsupported payloads.
- Test architecture: Selector tests cover the matching algebra; source-backed
  tests prove the real existing payloads. Do not add tests that merely assert
  type structure.
- Impact: No change to controller/pilot aircraft behaviour or vehicle state
  progression. The slice should add evidence/phraseology only.
- Operational correctness: ICAO Doc 9432 §5.2 and §5.4 are the regulatory
  anchors. The covered claims are vehicle-driver/tow-driver wording claims.

## References

- `research/tools/requirements-spike/quality/icao9432_programme/fn85_vehicle_tow_phraseology_manifest.md`
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_07_vehicles_towing/coverage_report.md`
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_07_vehicles_towing/expected_gaps.md`
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_07_vehicles_towing/source_plan.md`
