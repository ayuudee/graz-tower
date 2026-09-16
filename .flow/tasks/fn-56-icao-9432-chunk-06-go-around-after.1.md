# fn-56-icao-9432-chunk-06-go-around-after.1 Plan and review chunk 06 source-unit coverage

## Description
Build and review the chunk 06 source-unit coverage plan before authoring
tests. This task is complete only when all 20 source units are accounted for,
source quotes are checked, and the plan has been reviewed for false-green risk
around VFR go-around continuation, IFR missed-approach modelling,
after-landing frequency/taxi timing, and essential-aerodrome-information
world modelling.

## Acceptance
- [x] Create chunk-local `source_plan.md`.
- [x] Verify all accepted chunk 06 registry quotes against
      `research/txt/icao9432-extracted.txt`.
- [x] Classify every chunk 06 source unit as covered candidate, scenario
      evidence, expected gap, policy-blocked, model-gap, phraseology-later, or
      not-applicable.
- [x] Complete plan review before implementation starts.

## Done summary
Built and reviewed chunk 06 source-unit plan; all 20 units accounted for; plan review findings fixed.
## Evidence
- Commits:
- Tests: source quote audit against `research/txt/icao9432-extracted.txt`
- Review: plan review findings fixed; final plan SHIP in `plan_review.md`
- PRs:
