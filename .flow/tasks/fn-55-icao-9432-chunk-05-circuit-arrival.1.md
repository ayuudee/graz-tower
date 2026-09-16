# fn-55-icao-9432-chunk-05-circuit-arrival.1 Plan and review chunk 05 source-unit coverage

## Description
Build and review the chunk 05 source-unit coverage plan before authoring
tests. This task is complete only when all 23 source units are accounted for,
source quotes are mechanically checked, and the plan has been reviewed for
false-green risk around local procedures, traffic-dependent approach choices,
touch-and-go phraseology, low-pass / low-approach modelling, and final /
long-final distance thresholds.

## Acceptance
- [x] Create chunk-local `source_plan.md`.
- [x] Verify all accepted chunk 05 registry quotes against
      `research/txt/icao9432-extracted.txt`.
- [x] Classify every chunk 05 source unit as covered candidate, scenario
      evidence, expected gap, policy-blocked, model-gap, or phraseology-later.
- [x] Complete plan review before implementation starts.

## Done summary
Built and reviewed chunk 05 source-unit plan; all 23 source quotes verified; plan review shipped after removing ambiguous mixed final state and tightening touch-and-go request witness.
## Evidence
- Commits:
- Tests:
- PRs:
