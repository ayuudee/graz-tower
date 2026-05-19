# fn-44-pressure-test-source-mapped-evidence.3 Implement essential-aerodrome-information evidence case

## Description
Implement the ICAO Doc 9432 §4.10 essential-aerodrome-information timing source case against the best current evidence projection.

A positive case requires real activated evidence showing information was passed before taxi/final approach, or that known receipt from another source is explicitly represented. If current observations cannot support that, split the existing gap into a narrower typed gap rather than pretending absence is proof.

## Acceptance
- [x] Public test cites `icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc` through a typed catalog ref.
- [x] Case is either positive with activated facts or an explicitly narrower typed expected gap with `.plan` tracking.
- [x] Report output makes applicability and adequacy honest for this case.
- [x] No raw source ids, fact ids, monitor vocabulary, or report plumbing appear in the public test body.
- [x] Focused FN44 tests pass.

## Done summary
Implemented a narrow positive essential-aerodrome-information evidence case: pilot initial contact with ATIS information code projects KnownReceivedElsewhere before taxi and activates the source-mapped case. Broader final-approach and hazard-specific projection remains outside this task.
## Evidence
- Commits:
- Tests:
- PRs: