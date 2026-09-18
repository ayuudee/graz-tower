# fn-70-icao-9432-emergency-1c-emergency.1 Build emergency descent movement manifest

## Description
Build the source-unit movement manifest for the fn-70 emergency-descent
safeguarding slice. Select only rows that can be honestly moved with emergency
descent announcement, affected-traffic, safeguard action, and reset evidence.
Leave urgency payload/addressing/interference policy and communications-failure
rows blocked.

## Acceptance
- [x] Manifest lists exact source units, current states, target states, and
  evidence required.
- [x] Mandatory safeguarding, policy-dependent specific instructions, and
  optional position questioning are separated.
- [x] Safeguarding reversal/reset is planned before forward-path evidence.
- [x] Ordinary descent, arrival, or go-around traces are explicitly rejected as
  emergency-descent evidence.
- [x] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## Done summary
Created and impact-reviewed fn-70 movement manifest for emergency descent safeguarding projection coverage.
## Evidence
- Commits:
- Tests:
- PRs: