# fn-68-icao-9432-emergency-1a-emergency ICAO 9432 EMERGENCY-1A emergency classification and message payload

## Overview
Introduce emergency classification and emergency message payload evidence for
ICAO 9432 Chapter 9 without bundling priority, silence, descent, comms failure,
or SSR behavior.

## Scope
- Distress versus urgency classification.
- Pilot emergency declaration state.
- Emergency message payload fields.
- Rendered emergency wording remains blocked until phraseology evidence can
  prove it against this payload.

## Approach
Use chunk 08 expected-gap decomposition to select exact classification/payload
units. Ordinary VFR, go-around, or routine radio traces must not satisfy
emergency evidence claims.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*SourceUnit*'`
- `./.flow/bin/flowctl validate --epic fn-68-icao-9432-emergency-1a-emergency`
- `git diff --check`

## Acceptance
- [ ] Exact source-unit movement manifest exists.
- [ ] Emergency state and payload types are closed and explicit.
- [ ] No emergency phraseology unit moves green from payload state alone.
- [ ] Priority/silence/descent/comms-failure/SSR units remain blocked unless
  explicitly targeted.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_08_distress_urgency_comms_failure/coverage_report.md`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
