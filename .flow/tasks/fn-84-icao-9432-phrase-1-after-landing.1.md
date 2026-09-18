# fn-84-icao-9432-phrase-1-after-landing.1 Implement after-landing rendered phraseology evidence split

## Description
Implement the fn-84 after-landing phraseology split. The work should add
rendered evidence for the ICAO 9432 §4.9 branches already represented by typed
protocol leaves and leave unsupported example fragments visibly residual.

## Acceptance
- [ ] Add after-landing source refs with split titles/scopes.
- [ ] Render and project `RunwayVacated`, `TaxiToStand`, and generic route
  readback phraseology, without adding `AirTaxiTo` phraseology.
- [ ] The §4.9 taxi-to-stand evidence must pair the generic route readback with
  a matching `TaxiToStand` rendered instruction for the same destination/route;
  the readback alone must not satisfy the source unit. Route matching must use
  the same ordered `via` list.
- [ ] Add DSL selectors and selector tests for the new templates.
- [ ] Add source-mapped phraseology evidence tests for the covered §4.9 branches.
- [ ] Use synthetic records only if the production LOWG trace cannot emit the
  exact branch; any synthetic use must be named in the test name/sample metadata
  with the failed real-trace branch and the production renderer path exercised.
- [ ] The `df25159c1e7b94a3` evidence and docs must visibly state that `TAKE
  FIRST RIGHT WHEN VACATED` remains residual.
- [ ] Update every affected source catalog, chunk 06 programme coverage/gap doc,
  and summary-count/manifest artifact touched by final-state counts.
- [ ] Run focused tests, detekt, broad tests, flow validation, implementation
  review, and completion review.
- [ ] Record implementation-review and completion-review receipts in the task
  evidence.

## Done summary
Pending.

## Evidence
- Commits:
- Tests:
- Implementation review:
- Completion review:
- PRs:
