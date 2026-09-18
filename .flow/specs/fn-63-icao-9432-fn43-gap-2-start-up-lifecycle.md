# fn-63-icao-9432-fn43-gap-2-start-up-lifecycle ICAO 9432 FN43-GAP-2 start-up lifecycle evidence

## Overview
Close the remaining start-up lifecycle observation gap while keeping start-up
phraseology under `PHRASE-1` and discretionary timing under `POLICY-1`.

## Scope
- Engine start request, start approval, delayed start, and start-at-time
  lifecycle facts.
- Observation evidence only; rendered wording remains phraseology work.
- Policy-sensitive "normally indicates" behavior remains policy work unless
  explicitly bound.

## Approach
Use the chunk 02 source units to create an exact movement manifest, then add
minimal trace facts and high-level source-mapped tests for the selected
start-up lifecycle obligations.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*SourceUnit*'`
- `./.flow/bin/flowctl validate --epic fn-63-icao-9432-fn43-gap-2-start-up-lifecycle`
- `git diff --check`

## Acceptance
- [ ] Exact source-unit movement manifest exists.
- [ ] Start-up lifecycle facts are explicit in trace evidence.
- [ ] No start-up phraseology unit moves green from lifecycle facts alone.
- [ ] No discretionary start-up timing unit moves green without policy binding.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_02_radio_procedures_policy/coverage_report.md`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
