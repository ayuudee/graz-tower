# fn-59-icao-9432-implementation-roadmap.3 Create executable child epics

## Description
Create executable Flow child epics from the reviewed roadmap. The child epics
must preserve the testing/implementation firewall and be small enough that
source-unit movement from blocked/red to green can be audited.

## Acceptance
- [ ] Follow-on Flow epics exist for the reviewed implementation sequence
  after review/red-team findings have been incorporated into the roadmap.
- [ ] Each child epic states whether it is evidence substrate, policy
  substrate, observation repair, model expansion, or emergency-domain work.
- [ ] Each child epic carries the exact source-unit movement manifest
  requirement: green-targeted, remain-blocked, untouched, and anti-overcoverage
  guard.
- [ ] The first recommended execution epic is clearly identified.

## Done summary
Created twelve executable follow-on Flow epics for phraseology, policy, observation gaps, pushback, vehicle slices, and emergency slices, each with source-unit movement manifest gates.
## Evidence
- Commits:
- Tests:
- PRs: