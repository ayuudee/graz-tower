# fn-62-icao-9432-fn43-gap-1-essential ICAO 9432 FN43-GAP-1 essential aerodrome information evidence

## Overview
Repair the essential-aerodrome-information observation gap for ICAO 9432 §4.10
source units without conflating information transmission, pilot knowledge, and
clearance timing policy.

## Scope
- Source units currently blocked by `FN43-GAP-1`.
- Evidence for information given, information received/known, and timing before
  the relevant operational decision.
- Policy-sensitive "when necessary" or timing claims remain under policy unless
  this epic explicitly binds a policy.

## Approach
Plan the exact §4.10 source-unit movement manifest, add the smallest trace facts
needed to prove receipt/known-state, and write high-level source-mapped tests
against believable aerodrome-information scenarios.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*SourceUnit*'`
- `./.flow/bin/flowctl validate --epic fn-62-icao-9432-fn43-gap-1-essential`
- `git diff --check`

## Acceptance
- [ ] Exact source-unit movement manifest exists.
- [ ] Evidence distinguishes controller event, transmitted information, and
  pilot-known information.
- [ ] No policy-sensitive unit moves green without an explicit policy binding.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_06_go_around_after_landing_aerodrome_info/coverage_report.md`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
