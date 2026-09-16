# fn-58-icao-9432-chunk-08-distress-urgency.1 Plan and review chunk 08 emergency source-unit coverage

## Description
Build and review the chunk 08 source-unit coverage plan before authoring
tests. This task is complete only when all 46 source units are accounted for,
source quotes are checked, and the plan has been reviewed for false-green risk
around substituting normal radio, go-around, or VFR sequencing traces for
distress/urgency/comms-failure behaviour.

## Acceptance
- [x] Create chunk-local `source_plan.md`.
- [x] Verify all accepted chunk 08 registry quotes against
      `research/txt/icao9432-extracted.txt`.
- [x] Classify every chunk 08 source unit as model-gap, policy-blocked,
      phraseology-later, or not-applicable.
- [x] Complete plan review before implementation starts.

## Done summary
Created and reviewed chunk 08 source-unit coverage plan. All 46 accepted ICAO
9432 Chapter 9 emergency and communications-failure units were quote-checked,
classified, and reviewed; impact findings were incorporated before
implementation.
## Evidence
- Docs: `research/tools/requirements-spike/quality/icao9432_programme/chunk_08_distress_urgency_comms_failure/source_plan.md`
- Review: `research/tools/requirements-spike/quality/icao9432_programme/chunk_08_distress_urgency_comms_failure/plan_review.md`
