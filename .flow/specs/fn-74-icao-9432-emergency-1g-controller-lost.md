# fn-74-icao-9432-emergency-1g-controller-lost ICAO 9432 EMERGENCY-1G controller lost-contact relay workflow

## Overview

Close the next narrow ICAO 9432 Chapter 9 residual after fn-73: controller-side
lost-contact workflow after a station is unable to contact an aircraft.

This epic must not claim general Annex 10 communications-failure conformance,
pilot-side receiver-failure/blind-transmission phraseology, or blind ATC
clearance authority. The target is structured evidence for controller attempts
to use route aircraft/stations as relay helpers and, after those attempts fail,
for ATC-originated blind transmission of non-clearance messages when the
aircraft is believed to be listening.

## Source-Unit Movement Candidates

Target rows:

- `bb66a050093251c2`: station unable to contact aircraft asks route aircraft
  to call or relay.
- `24c806b040f4ef5e`: station unable to contact aircraft asks other stations
  to call or relay.
- `75055714e70d4560`: if station attempts fail, non-clearance messages may be
  blind-transmitted when the aircraft is believed to be listening.

Explicit non-targets:

- Annex 10 communications-failure conformance row `c1c14fab53a608c6`.
- Rendered blind-transmission phraseology rows already split to `PHRASE-1`.
- Blind ATC clearance prohibition/exception row `b73dda299970c2f3`, already
  covered by fn-71 and not reopened here.
- General any-means distress communication row `4b37e039e7eb8afa`.
- Emergency phraseology/order rows.

## Approach

1. Build an exact movement manifest before code changes.
2. Run impact review before implementation.
3. Prefer a closed local source-unit projection unless impact review identifies
   a bounded production port that should be introduced.
4. Add source-backed tests with explicit sequencing:
   - direct station contact fails;
   - route aircraft relay may be requested;
   - other station relay may be requested;
   - after those attempts fail and the aircraft is believed listening, ATC may
     blind-transmit non-clearance messages.
5. Add wrong-path guards:
   routine traffic, pilot-originated communications failure, generic emergency
   labels, blind clearance, no failed direct contact, no failed relay attempts,
   aircraft not believed listening, and rendered phraseology-only evidence must
   not satisfy these rows.
6. Update chunk-08 ledgers and `implementation_blocker_manifest.csv` only for
   the three manifest rows.
7. Perform principal self-assessment, independent completion review, focused
   and broad validation, and close Flow before commit.

## Quick Commands

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432ControllerLostContactSourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `.flow/bin/flowctl validate --epic fn-74-icao-9432-emergency-1g-controller-lost`
- `git diff --check`

## Acceptance

- [ ] Exact source-unit movement manifest exists and is reviewed before
  implementation.
- [ ] Moved rows have direct structured evidence for controller lost-contact
  route-aircraft relay, inter-station relay, and non-clearance blind
  transmission after failed station attempts.
- [ ] Blind non-clearance evidence is gated by failed station attempts and
  believed-listening state.
- [ ] Blind clearance remains rejected unless covered by the separate fn-71
  originator-request exception; fn-74 does not loosen it.
- [ ] Annex 10, rendered phraseology, any-means distress communication, and
  emergency phraseology/order residuals remain blocked and executable.
- [ ] Review considerations below are satisfied.

## Review Considerations

FP / type safety:

- Use closed local projection types or production sealed types; no stringly
  workflow branches where closed enums/sealed types are appropriate.
- All `when` expressions must be exhaustive. Unsupported type-valid paths must
  return typed rejection, not silent defaults or `error()`.
- Reset/recovery must clear every derived field introduced by the projection:
  contact state, requested relay actor, relay attempt result, believed-listening
  marker, blind-message type, blind-transmission state, and policy marker.

Test architecture:

- Tests should prove workflow sequencing, not just row citation.
- Every moved row needs wrong-path assertions preventing ordinary radio,
  pilot-originated blind transmission, blind clearance, or generic emergency
  traces from satisfying the source.
- Expected-gap exact-union accounting for chunk 08 must remain green.

Impact:

- Production code changes are optional and must be justified by impact review.
  Test-only projections must state they do not change production behavior.
- Do not merge controller-side lost-contact workflow with pilot-side
  communications-failure transmission evidence from fn-71.

Operational correctness:

- Claims are anchored to ICAO Doc 9432 Chapter 9 source units. Do not use Annex
  10 general communications-failure references as coverage for these rows.
- Route-aircraft relay, inter-station relay, non-clearance blind transmission,
  blind-clearance prohibition, and rendered phraseology are distinct source
  obligations.
