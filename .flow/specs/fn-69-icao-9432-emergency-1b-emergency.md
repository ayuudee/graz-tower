# fn-69-icao-9432-emergency-1b-emergency ICAO 9432 EMERGENCY-1B emergency priority and radio silence

## Overview
Add emergency priority and radio-silence behavior as a separate proof surface
from emergency payload construction.

## Scope
- Emergency priority arbitration.
- Radio silence instruction/release behavior.
- Assistance/relay actors only if selected source units require them.
- No emergency descent, communications failure, blind-transmission, or SSR
  green-out unless explicitly targeted.

## Approach
Build on emergency classification/payload state. Select exact priority/silence
source units and write high-level source-mapped scenarios that distinguish
emergency priority from ordinary radio ordering.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*SourceUnit*'`
- `./.flow/bin/flowctl validate --epic fn-69-icao-9432-emergency-1b-emergency`
- `git diff --check`

## Acceptance
- [ ] Exact source-unit movement manifest exists.
- [ ] Priority/silence state transitions include complete reset/reversal.
- [ ] Ordinary radio serialization does not satisfy emergency priority claims.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_08_distress_urgency_comms_failure/coverage_report.md`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
