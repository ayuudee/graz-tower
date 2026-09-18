# fn-69-icao-9432-emergency-1b-emergency.1 Build emergency priority and silence movement manifest

## Description
Build the source-unit movement manifest for the fn-69 emergency priority and
radio-silence slice. Select only rows that can be honestly moved with closed
priority ordering and silence lifecycle evidence. Leave superfluous-
transmission suppression policy, assistance/relay actors, emergency descent,
communications failure, blind transmission, SSR, and phraseology rows blocked.

## Acceptance
- [x] Manifest lists exact source units, current states, target states, and
  evidence required.
- [x] Priority ordering rows are separated from radio-silence lifecycle rows.
- [x] Silence reversal/termination is planned before forward-path evidence.
- [x] Ordinary radio serialization is explicitly rejected as priority evidence.
- [x] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## Done summary
Created and impact-reviewed fn-69 movement manifest for emergency priority and silence projection coverage.
## Evidence
- Commits:
- Tests:
- PRs: