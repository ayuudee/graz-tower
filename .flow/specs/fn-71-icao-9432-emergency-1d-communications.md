# fn-71-icao-9432-emergency-1d-communications ICAO 9432 EMERGENCY-1D communications failure blind transmissions SSR

## Overview
Cover communications failure, blind transmissions, and SSR emergency code
behavior after emergency foundation work exists.

## Scope
- Communications failure state.
- Blind-transmission scheduler/evidence.
- SSR 7600/7700 where applicable.
- Recovery and no-contact traces at high level.
- Distinct from routine missed calls or ordinary frequency-transfer failures.

## Approach
Select exact communications-failure units from chunk 08. Model failure state
explicitly and write source-mapped tests that fail if ordinary radio loss or
routine no-reply traces are used as substitute evidence.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*SourceUnit*'`
- `./.flow/bin/flowctl validate --epic fn-71-icao-9432-emergency-1d-communications`
- `git diff --check`

## Acceptance
- [ ] Exact source-unit movement manifest exists.
- [ ] Communications failure and blind-transmission state are explicit.
- [ ] SSR emergency/code evidence is explicit where targeted.
- [ ] Ordinary no-reply/frequency-transfer traces do not satisfy failure
  procedure claims.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_08_distress_urgency_comms_failure/coverage_report.md`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
