# fn-73-icao-9432-emergency-1f-message ICAO 9432 EMERGENCY-1F message addressing and payload policy

## Overview

Close the next narrow ICAO 9432 Chapter 9 emergency residual after fn-72:
structured emergency message addressing and payload-policy evidence for
distress and urgency messages.

This epic must not claim rendered phraseology, spoken element order, Annex 10
conformance, or production emergency radio workflow. The goal is to move only
rows whose obligation can be represented honestly as a closed source-backed
message-addressing or message-payload policy projection.

## Source-Unit Movement Candidates

Target rows:

- `27a450fa3bfcbc0a`: distress message normally addresses the current station
  or the station responsible for the area.
- `94e94be0c800c982`: a station other than the distressed aircraft may vary
  distress-message elements when the circumstance is clearly stated.
- `1de475a788206cc8`: urgency messages contain the distress-message elements
  required by the circumstances.
- `b95d7bb1cb409bca`: urgency calls normally use the frequency in use and are
  addressed to the station in communication or responsible-area station.

Explicit non-targets:

- Rendered emergency phraseology rows `13d1c2accd0f7a73`,
  `9907744b4723d14c`, `bf04647e26f9c018`, and `f0e99a4c08ea0cb3`.
- Annex 10 conformance rows `c30159856a1a5e7a` and `c1c14fab53a608c6`.
- Any-means distress communication residual `4b37e039e7eb8afa`.
- Emergency-descent follow-up specific-instruction necessity row
  `c71568b00fb1535e`.
- Controller communications-failure relay / blind non-clearance rows
  `bb66a050093251c2`, `24c806b040f4ef5e`, and `75055714e70d4560`.

## Approach

1. Build an exact movement manifest before code changes.
2. Run impact review before implementation.
3. Prefer closed local source-unit projections over production changes unless
   impact review says a production concept is ready and bounded.
4. Add source-backed tests with explicit wrong-path guards:
   routine traffic, phraseology-only evidence, generic emergency labels,
   distress-specific addressing reused for urgency without an urgency context,
   urgency payload reused for distress without distress context, and relayed
   message variation without a clearly stated circumstance must not satisfy
   these rows.
5. Keep payload policy distinct from rendered ordering:
   - payload presence/selection may be structured evidence;
   - spoken wording, repetition, and ordering remain `PHRASE-1`.
6. Split the current emergency message residual gap group deliberately:
   moved policy rows leave the group, and the remaining executable residual
   spec must describe only rendered phraseology/order gaps.
7. Update chunk-08 ledgers and `implementation_blocker_manifest.csv` only for
   rows declared in the movement manifest.
8. Perform principal self-assessment, independent completion review, focused
   and broad validation, and close Flow before commit.

## Quick Commands

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432EmergencyMessagePolicySourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `.flow/bin/flowctl validate --epic fn-73-icao-9432-emergency-1f-message`
- `git diff --check`

## Acceptance

- [ ] Exact source-unit movement manifest exists and is reviewed before
  implementation.
- [ ] Moved rows have direct structured evidence for emergency message
  addressing or payload-policy branches.
- [ ] Policy-sensitive rows bind explicit policy concepts and do not
  universalize one valid local/controller choice.
- [ ] Distress and urgency message policy are represented distinctly where the
  source distinguishes them.
- [ ] Relayed/non-distressed distress-message variation is gated by clear
  circumstances explicitly carried in the structured projection and does not
  erase the distressed-aircraft default message structure.
- [ ] Urgency payload policy has a non-vacuous test shape: at least one missing
  required element is rejected and at least one non-required element may be
  omitted.
- [ ] The residual emergency message gap spec is rewritten to cover only
  rendered phraseology/order after the four policy rows move.
- [ ] Rendered phraseology/order rows, Annex 10 rows, any-means communication,
  emergency-descent follow-up instruction necessity, and controller lost-contact
  workflow rows remain blocked and executable in expected-gap specs.
- [ ] Review considerations below are satisfied.

## Review Considerations

FP / type safety:

- Use closed local projection types or production sealed types; no stringly
  policy branches where a closed enum/sealed type is appropriate.
- All `when` expressions must be exhaustive. No catch-all `else` may swallow a
  new emergency message/addressing branch.
- Reset/recovery must clear every derived field introduced by the projection:
  emergency kind, sender role, addressee, responsible station/area marker,
  selected payload elements, variation reason, and policy marker.
- Type-valid but unsupported states must return typed rejection/evidence gaps,
  not silent defaults or `error()`.

Test architecture:

- Tests should be source-mapped at the scenario/projection level, not low-level
  structural tests.
- Every moved row needs wrong-path assertions preventing ordinary radio,
  phraseology-only, or generic emergency traces from satisfying the source.
- Expected-gap exact-union accounting for chunk 08 must remain green.

Impact:

- Production code changes are optional and must be justified by impact review.
  Test-only projections must state they do not change production behavior.
- Payload-policy evidence must not be allowed to imply rendered order or
  wording. That work remains a phraseology-renderer concern.
- Emergency addressing policy introduces pressure on current station/area
  responsibility concepts; keep it local unless a real production port is
  intentionally introduced.

Operational correctness:

- Claims are anchored to ICAO Doc 9432 Chapter 9 source units. Do not substitute
  CAP 413 phraseology or Annex 10 general references for these source-unit
  obligations.
- Distress addressing, relayed distress-message variation, urgency payload
  selection, urgency addressing, and phraseology rendering are distinct source
  obligations and must not be collapsed into one generic emergency helper.
