# fn-71-icao-9432-emergency-1d-communications.1 Build communications failure movement manifest

## Description
Build the source-unit movement manifest for the fn-71 communications-failure,
blind-transmission, and SSR slice. Select only rows that can be honestly moved
with typed communications-failure state, blind-transmission scheduling, SSR
code evidence, and blind-clearance prohibition evidence. Leave phraseology,
route/relay policy, and Annex 10 conformance rows blocked unless a narrow split
is explicit.

## Acceptance
- [ ] Manifest lists exact source units, current states, target states, and
  evidence required.
- [ ] Communications failure, blind-transmission, SSR, and blind-clearance
  rows are separated.
- [ ] Recovery/reset is planned before forward-path evidence.
- [ ] Ordinary missed-call, no-reply, or frequency-transfer traces are
  explicitly rejected as communications-failure evidence.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## Done summary
Built fn-71 communications-failure movement manifest with impact review constraints, moved/split row list, negative guard requirements, and residual blocker policy.
## Evidence
- Commits:
- Tests:
- PRs: