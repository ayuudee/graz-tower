# fn-74-icao-9432-emergency-1g-controller-lost.2 Implement controller lost-contact source-backed evidence

## Description
Implement the fn-74 source-backed evidence and ledger movement for
controller-side lost-contact relay and non-clearance blind-transmission rows.

## Acceptance
- [ ] Add `Icao9432ControllerLostContactSourceBackedTest` or equivalent
  source-backed test with closed local projection types.
- [ ] Route-aircraft relay request requires direct station contact failure.
- [ ] Inter-station relay request requires direct station contact failure.
- [ ] ATC-originated blind non-clearance transmission requires failed relay
  attempts and believed-listening state.
- [ ] Blind clearance remains rejected in the fn-74 projection.
- [ ] Wrong-path guards reject routine traffic, pilot-originated communications
  failure, generic emergency labels, missing failed-contact prerequisites,
  aircraft-not-believed-listening state, blind clearance, and phraseology-only
  evidence.
- [ ] `Icao9432ModelGapSourceUnitSpecTest` residual groups remain executable
  and exact-union coverage remains green.
- [ ] Coverage report, expected gaps, source plan, and blocker manifest are
  updated only for moved rows.

## Done summary
- Task completed
## Evidence
- Commits:
- Tests:
- PRs: