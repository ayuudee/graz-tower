# fn-57-icao-9432-chunk-07-vehicles-towing.1 Plan and review chunk 07 vehicle source-unit coverage

## Description
Build and review the chunk 07 source-unit coverage plan before authoring
tests. This task is complete only when all 12 source units are accounted for,
source quotes are checked, and the plan has been reviewed for false-green risk
around substituting aircraft runway/taxi traces for vehicle/towing behaviour.

## Acceptance
- [x] Create chunk-local `source_plan.md`.
- [x] Verify all accepted chunk 07 registry quotes against
      `research/txt/icao9432-extracted.txt`.
- [x] Classify every chunk 07 source unit as model-gap, policy-blocked,
      phraseology-later, or not-applicable.
- [x] Complete plan review before implementation starts.

## Done summary
Created and reviewed chunk 07 source-unit coverage plan. All 12 accepted ICAO 9432 §5.1-§5.4 vehicle/towing units were quote-checked, classified, and corrected after plan review so first-call/tow wording remains model-gap plus phraseology-later rather than a false green.
## Evidence
- Docs: `research/tools/requirements-spike/quality/icao9432_programme/chunk_07_vehicles_towing/source_plan.md`
- Review: `research/tools/requirements-spike/quality/icao9432_programme/chunk_07_vehicles_towing/plan_review.md`
