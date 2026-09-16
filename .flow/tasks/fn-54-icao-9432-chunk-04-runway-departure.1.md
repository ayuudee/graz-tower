# fn-54-icao-9432-chunk-04-runway-departure.1 Plan and review chunk 04 source-unit coverage

## Description
Build and review the chunk 04 source-unit coverage plan before authoring
tests. This task is complete only when all 19 source units are accounted for,
source quotes are mechanically checked, and the plan has been reviewed for
false-green risk around "usually", "may", "should", conditional clearance
visibility, poor visibility, and take-off cancellation.

## Acceptance
- [x] Create chunk-local `source_plan.md`.
- [x] Verify all accepted chunk 04 registry quotes against
      `research/txt/icao9432-extracted.txt`.
- [x] Classify every chunk 04 source unit as testable, covered-red candidate,
      expected-gap, policy-blocked, or phraseology-later.
- [x] Complete plan review before implementation starts.

## Done summary
Built and reviewed chunk 04 source-unit plan; all 19 source quotes verified; plan review shipped after adding transfer-location witness and policy-state fixes.
## Evidence
- Commits:
- Tests:
- PRs:
