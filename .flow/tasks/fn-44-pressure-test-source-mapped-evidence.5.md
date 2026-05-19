# fn-44-pressure-test-source-mapped-evidence.5 Implement transfer-of-communications evidence cases

## Description
Implement ICAO Doc 9432 §2.8.2 transfer-of-communications cases for controller-advised frequency change and pilot notification absent advice, using the smallest existing scenario surface that can honestly produce evidence.

Prefer procedural evidence over phraseology. If the existing sim does not model frequencies/units enough for one branch, represent that as a typed expected gap and make the report explain the missing projection.

## Acceptance
- [ ] Public tests cite `icao9432-extracted::transfer_communications_2_8_2_en::40382df156ad071e` and `icao9432-extracted::transfer_communications_2_8_2_en::b49ae03cbbb2d538` through typed catalog refs.
- [ ] At least one transfer case becomes positive with activated evidence, unless the design note proves no honest current projection exists.
- [ ] Any non-positive transfer source is a typed expected gap with `.plan` tracking.
- [ ] Tests do not assert phraseology unless phraseology support is explicitly added with source backing.
- [ ] Focused FN44 tests pass.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
