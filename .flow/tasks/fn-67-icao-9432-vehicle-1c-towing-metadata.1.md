# fn-67-icao-9432-vehicle-1c-towing-metadata.1 Build towing metadata and tow-extent manifest

## Description
Build the fn-67 towing source-unit movement manifest and complete impact/plan
review before implementation. The plan must distinguish structured tow
metadata from rendered vehicle/tow wording and must prevent vehicle-only
clearance evidence from greening tow extent.

## Acceptance
- [ ] Exact §5.4 / tow-extent movement manifest exists.
- [ ] Impact review completed before implementation.
- [ ] False-green boundaries are explicit for `29be`, `4b103`, and `735b`.
- [ ] `331c`, `0b45`, and non-towing policy rows remain blocked unless a
  separate model is deliberately added.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## Done summary
Built fn-67 towing manifest and incorporated impact review: target structured tow-awareness, structured type/operator metadata, and explicit vehicle+tow clear-beyond-holding-point evidence; keep rendered tow wording, expected-aircraft-operation trigger, and hazard policy blocked.
## Evidence
- Commits:
- Tests:
- PRs: