# fn-61-icao-9432-phrase-1-rendered-phraseology ICAO 9432 PHRASE-1 rendered phraseology evidence architecture

## Overview
First executable epic in the ICAO 9432 implementation roadmap. Build the
rendered phraseology evidence port and assertion vocabulary needed to prove
wording obligations without confusing typed protocol atoms for spoken radio
phraseology.

## Scope
- Create a rendered phraseology evidence contract that preserves typed
  instruction/report atoms alongside rendered utterance text.
- Define the phraseology obligation taxonomy: mandatory words, ordered phrase,
  semantic slot, example dialogue, readback, ambiguity/absence constraint, and
  forbidden-meaning constraint.
- Support structured rendered tokens with source/protocol provenance where a
  source unit depends on slots or semantic meaning.
- Prove the architecture with a small non-emergency cross-section from
  communications, start-up, takeoff, and landing.
- Exclude emergency wording until emergency state and message payload evidence
  exists.

## Approach
Plan the exact source-unit movement manifest first. Then add the narrowest
evidence adapter and high-level source-mapped tests that prove the selected
phraseology obligations. Do not green all `PHRASE-1` units in this epic.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*SourceUnit*'`
- `./.flow/bin/flowctl validate --epic fn-61-icao-9432-phrase-1-rendered-phraseology`
- `git diff --check`

## Acceptance
- [ ] Exact source-unit movement manifest exists: green-targeted,
  remain-blocked, untouched, and anti-overcoverage guard.
- [ ] Rendered phraseology evidence keeps typed protocol atoms and rendered
  wording traceable without embedding rendering in domain decisions.
- [ ] Phraseology obligation taxonomy is documented and used by the proof-set
  tests.
- [ ] No emergency phraseology unit moves green.
- [ ] Ambiguity/forbidden-meaning units are not satisfied by substring checks
  or brittle full-string snapshots.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_roadmap.md`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_red_team.md`
