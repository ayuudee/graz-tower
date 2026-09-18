# fn-65-icao-9432-vehicle-1b-vehicle-runway.1 Build vehicle runway manifest and reviewed implementation plan

## Description
Build the fn-65 source-unit movement manifest and run/reconcile the impact
assessment before implementation. The plan must explicitly prevent false greens
for tow extent, dangerous-situation policy, and expected aircraft operations.

## Acceptance
- [x] Exact §5.3 source-unit movement manifest exists.
- [x] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.
- [x] Impact assessment incorporated before code changes.
- [x] Rows that remain blocked are explicitly named.

## Done summary
Built fn-65 vehicle runway manifest and incorporated impact assessment: target crossing permission+ack, expected-aircraft-operation vacate instruction, and split vehicle-only vacated-report evidence; keep hazard/policy and towing rows blocked.
## Evidence
- Commits:
- Tests:
- PRs:
