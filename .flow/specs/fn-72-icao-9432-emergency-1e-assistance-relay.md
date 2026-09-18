# fn-72-icao-9432-emergency-1e-assistance-relay ICAO 9432 EMERGENCY-1E assistance relay and frequency policy

## Overview

Close the next narrow slice of ICAO 9432 Chapter 9 emergency blockers after
fn-68 through fn-71: assistance by non-addressed stations/aircraft, emergency
frequency-selection policy, and superfluous/interfering transmission
suppression.

This epic must keep the established boundary: source-mapped structured
projection evidence is acceptable only for the exact branch it proves. Rendered
emergency phraseology, Annex 10 conformance, and any production emergency
workflow not directly exercised remain blocked.

## Source-Unit Movement Candidates

Target only rows that can be represented honestly with closed local projection
types or existing policy vocabulary:

- `9c34a1b8d6d623fa`: another station/aircraft replies and assists when the
  called ground station does not reply.
- `2fee92c222323e6a`: alternate emergency frequency may be selected when
  necessary or desirable.
- `24f94381ed9e8ce1`: emergency communications normally stay on the frequency
  in use unless a better frequency helps.
- `82ee7048517d8478`: replying station provides needed advice, information,
  and instructions to assist.
- `8e9f7818b91d08c3`: intercepting aircraft may acknowledge/broadcast
  unacknowledged distress.
- `cb12c2f9b97c64b7`: distress/urgency call normally uses frequency in use.
- `06f7a72397c325ac`: superfluous transmissions can distract a busy emergency
  pilot.
- `5df94af7a64c3f5d`: urgency interference suppression policy.

Explicit non-targets:

- Annex 10 conformance rows `c30159856a1a5e7a` and `c1c14fab53a608c6`.
- Rendered emergency / blind-transmission phraseology rows.
- Distress/urgency message addressing and payload-policy rows
  `27a450fa3bfcbc0a`, `94e94be0c800c982`, `1de475a788206cc8`, and
  `b95d7bb1cb409bca`; these need message element presence/omission and
  addressing-selection evidence, not policy labels alone.
- Controller communications-failure relay / blind non-clearance rows
  `bb66a050093251c2`, `24c806b040f4ef5e`, and `75055714e70d4560`; these need a
  later controller lost-contact workflow epic with hard failed-attempt and
  believed-listening gates.
- Production emergency radio scheduling unless the implementation genuinely
  introduces it with high-level source-mapped trace evidence.

## Approach

1. Build an exact movement manifest before code changes.
2. Run impact review before implementation.
3. Prefer closed local source-unit projections if production concepts are not
   ready; do not imply production behavior from test-only helpers.
4. Add high-level source-backed tests with wrong-path guards:
  ordinary routine traffic, routine frequency transfer, non-emergency relay,
  generic emergency without the relevant assistance/frequency condition, and
  phraseology-only evidence must not satisfy these rows.
5. Update chunk-08 ledgers and `implementation_blocker_manifest.csv` only for
   rows declared in the manifest.
6. Perform principal self-assessment, independent completion review, focused
   and broad validation, and close Flow before commit.

## Quick Commands

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432EmergencyAssistanceRelaySourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `.flow/bin/flowctl validate --epic fn-72-icao-9432-emergency-1e-assistance-relay`
- `git diff --check`

## Acceptance

- [ ] Exact source-unit movement manifest exists and is reviewed before
  implementation.
- [ ] Moved rows have direct structured evidence for emergency assistance,
  emergency relay, frequency-policy, or interference-suppression branches.
- [ ] Policy-sensitive rows bind explicit policy concepts and do not
  universalize a valid local/controller choice.
- [ ] Relay and assistance actors are represented distinctly from the aircraft
  in distress and from the originally called station.
- [ ] Message payload/addressing rows and ATC-originated blind non-clearance
  rows remain blocked for later epics.
- [ ] Annex 10, rendered phraseology, message payload/addressing, controller
  lost-contact workflow, and all other non-target residuals remain blocked and
  executable in expected-gap specs.
- [ ] Review considerations below are satisfied.

## Review Considerations

FP / type safety:

- Use closed local projection types or production sealed types; no stringly
  policy branches where a closed enum/sealed type is appropriate.
- All `when` expressions must be exhaustive. No catch-all `else` may swallow a
  new emergency/relay/frequency branch.
- Reset/recovery must clear every derived field introduced by the projection:
  active emergency traffic, assisting actor, selected frequency, suppressed
  transmissions, and emergency relay state.

Test architecture:

- Tests should be source-mapped at the scenario/projection level, not low-level
  structural tests.
- Every moved row needs wrong-path assertions preventing ordinary radio,
  phraseology-only, or generic emergency traces from satisfying the source.
- Expected-gap exact-union accounting for chunk 08 must remain green.

Impact:

- Production code changes are optional and must be justified by impact review.
  Test-only projections must state they do not change production behavior.
- Policy rows must not become hidden defaults. A green configured branch must
  say what is not being proven universally.
- Relay actors and assisting transmitters are new conceptual pressure on the
  current single-aircraft/controller traces; do not wire global behavior
  without opt-in fixture control.

Operational correctness:

- Claims are anchored to ICAO Doc 9432 Chapter 9 source units. Do not substitute
  CAP 413 phraseology or Annex 10 general references for these source-unit
  obligations.
- Emergency assistance, urgency assistance, message addressing, communications-
  failure relay, and blind non-clearance behavior are distinct source
  obligations and must not be collapsed into one generic emergency helper.
