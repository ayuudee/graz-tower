# fn-64-icao-9432-pushback-1-pushback-and ICAO 9432 PUSHBACK-1 pushback and powerback model

## Overview
Add the minimal pushback/powerback domain model needed to express ICAO 9432
pushback source units honestly.

## Scope
- Pushback request and approval lifecycle.
- Ground crew signal or equivalent explicitly modeled fact.
- Powerback local-procedure policy, not universal behavior.
- Do not design the full vehicle actor model here.

## Approach
Start with the exact three-unit movement manifest. Either introduce a minimal
shared ground-service event concept that can survive later vehicle work, or
scope the epic to aircraft movement plus `GroundCrewSignalReceived`.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*SourceUnit*'`
- `./.flow/bin/flowctl validate --epic fn-64-icao-9432-pushback-1-pushback-and`
- `git diff --check`

## Acceptance
- [ ] Exact source-unit movement manifest exists.
- [ ] Pushback/powerback behavior has explicit state and complete reversal.
- [ ] Ground crew signal evidence is explicit where required.
- [ ] Vehicle actor concepts are not accidentally introduced or half-modeled.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_03_ground_movement/coverage_report.md`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
