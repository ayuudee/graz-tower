# fn-64-icao-9432-pushback-1-pushback-and.2 Implement minimal pushback lifecycle and evidence

## Description
Implement the reviewed minimal pushback lifecycle and source-mapped evidence
without widening into vehicle actor modelling or rendered phraseology.

## Acceptance
- [ ] Pushback-required scenario proves request -> approval -> manoeuvre
  completion -> ground-crew visual free-to-taxi signal -> taxi request.
- [ ] Pushback-required mission branch cannot emit `RequestTaxi` before the
  ground-crew visual free-to-taxi signal.
- [ ] Ground-departure controller stages/reconciliation include explicit
  pre-taxi pushback handling; pushback is not silently absorbed as ordinary
  `AwaitTaxiRequest` taxi lifecycle.
- [ ] Coverage for local-procedure routing is explicitly scoped to the
  ATC/GROUND branch; apron management remains visible as unsupported.
- [ ] Powerback remains blocked unless a true reverse-by-engine lifecycle is
  implemented.
- [ ] Existing LOWG goldens remain on the no-pushback path.
- [ ] Relevant source-mapped tests and focused regressions pass.

## Done summary
Implemented source-backed ICAO 9432 pushback lifecycle: pilot request/await steps, controller pushback approval, typed ground-crew completion event projected into controller belief, taxi-after-pushback guard, certification surface support, source-backed scenario tests, and chunk-03 programme doc updates.
## Evidence
- Commits:
- Tests:
- PRs: