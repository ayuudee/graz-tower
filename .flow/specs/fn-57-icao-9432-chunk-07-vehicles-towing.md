# fn-57-icao-9432-chunk-07-vehicles-towing ICAO 9432 chunk 07 vehicles towing source-mapped tests

## Overview
Author source-mapped coverage for ICAO 9432 chunk 07:
`chunk-07-vehicles-and-towing`.

Scope sections:

- `aerodrome_vehicles_intro_movement_5_1_to_5_2_en`
- `aerodrome_vehicles_crossing_towing_5_3_to_5_4_en`

This chunk contains 12 accepted source units covering vehicle movement,
runway-crossing permission, runway vacation, potential-danger stop
instructions, and aircraft towing calls. The epic is expected-gap heavy:
current simulator traces model aircraft and controllers, not vehicle drivers,
tow combinations, vehicle call signs, or vehicle runway occupancy.

## Scope
In scope:

- Verify all 12 accepted source-unit quote excerpts against
  `research/txt/icao9432-extracted.txt`.
- Produce `source_plan.md`, `expected_gaps.md`, and `coverage_report.md` under
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_07_vehicles_towing/`.
- Add source-specific model-gap specs for vehicle movement, runway crossing,
  towing, runway-vacated reporting, and rendered vehicle phraseology.
- Keep every source disposition loud and source-specific.

Out of scope:

- Implementing vehicle actors, vehicle physics, or vehicle controller rules.
- Treating aircraft taxi/runway behavior as vehicle compliance.
- Treating typed aircraft runway crossing or vacating evidence as vehicle
  crossing/towing evidence.
- Building rendered phraseology support.

## Approach
1. Build the chunk-local source plan from accepted registry records and quote
   checks.
2. Classify rows as `model-gap`, `model-gap + policy-blocked`, or
   `phraseology-later`.
3. Add permanent source-unit gap specs in `Icao9432ModelGapSourceUnitSpecTest`
   or adjacent test code. These specs should make `VEHICLE-1` concrete:
   missing vehicle actor, vehicle position/lifecycle, driver readback,
   runway-crossing permission, towing metadata, and runway-vacated geometry.
4. Do not add scenario green tests unless a real vehicle/towing trace exists.
5. Run focused chunk tests, broad `:sim:jvmTest`, detekt, Flow validation, and
   `git diff --check`.
6. Run independent implementation review before closing and committing.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix :sim:jvmTest`
- `./gradlew-nix detekt`
- `.flow/bin/flowctl validate --epic fn-57-icao-9432-chunk-07-vehicles-towing`

## Acceptance
- [x] All 12 chunk 07 accepted source units have a reviewed final state.
- [x] Every coverage/gap claim cites the accepted source-unit id.
- [x] No aircraft-only trace is used as vehicle compliance evidence.
- [x] The `VEHICLE-1` blocker is decomposed into concrete missing concepts.
- [x] Phraseology rows are not marked covered-green from typed objects.
- [x] Flow tasks and checked-in sidecars match runtime state before commit.

## Review Considerations

### FP / Type Safety

No production ADT or state field is planned. If vehicle concepts are later
implemented, they must be typed actors/state, not strings or overloaded
aircraft fields. This epic should not add `else` fallbacks or `error()` paths.

### Test Architecture

Tests should be high-level source-unit gap specs. Since no vehicle world model
exists, most value is in precise expected gaps rather than fake green scenario
tests.

### Impact

The main coupling risk is false equivalence between aircraft runway/taxi
behavior and vehicle behavior. Avoid using existing aircraft runway crossing,
taxi, or vacate traces for vehicle source units.

### Operational Correctness

ICAO 9432 §5.1-§5.4 concerns vehicle drivers and towing operations on the
movement area. These are distinct operational actors from aircraft pilots.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/README.md`
- `research/tools/requirements-spike/quality/icao9432_programme/classification.json`
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/`
- `research/txt/icao9432-extracted.txt`
