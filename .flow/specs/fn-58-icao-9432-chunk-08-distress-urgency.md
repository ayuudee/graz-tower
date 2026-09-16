# fn-58-icao-9432-chunk-08-distress-urgency ICAO 9432 chunk 08 distress urgency comms failure source-mapped tests

## Overview
Author source-mapped coverage for ICAO 9432 chunk 08:
`chunk-08-distress-urgency-comms-failure`.

Scope sections:

- `distress_urgency_intro_9_1_en`
- `distress_messages_9_2_en`
- `urgency_emergency_descent_9_3_to_9_4_en`
- `communications_failure_9_5_en`

This chunk contains 46 accepted source units covering distress/urgency
priority, distress and urgency message content, emergency descent broadcasts,
communications failure procedures, blind transmissions, SSR failure codes, and
radio-failure relay procedures. The epic is expected-gap heavy: current
simulator traces do not model emergency states, distress/urgency traffic
priority, radio failure mode, emergency descent conflict safeguarding, blind
transmission loops, or emergency phraseology rendering.

## Scope
In scope:

- Verify all 46 accepted source-unit quote excerpts against
  `research/txt/icao9432-extracted.txt`.
- Produce `source_plan.md`, `expected_gaps.md`, `plan_review.md`, and
  `coverage_report.md` under
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_08_distress_urgency_comms_failure/`.
- Add source-specific model-gap specs for distress/urgency priority,
  emergency traffic silence, distress/urgency message content, emergency
  descent safeguarding, communications-failure routing, blind transmission, SSR
  failure codes, and relay/assistance procedures.
- Keep every source disposition loud and source-specific.

Out of scope:

- Implementing emergency aircraft state, emergency controller rules, emergency
  descent conflict-resolution, comms-failure routing, SSR 7600/7700 behavior,
  blind transmission scheduling, or rendered emergency phraseology.
- Treating ordinary VFR go-around, ordinary radio, or normal controller
  sequencing traces as distress/urgency/comms-failure compliance evidence.
- Marking phraseology-only rows as covered-green from typed objects without
  rendered wording support.

## Approach
1. Build the chunk-local source plan from accepted registry records and quote
   checks.
2. Classify rows as `model-gap`, `model-gap + policy-blocked`, or
   `model-gap + phraseology-later`.
3. Add permanent source-unit gap specs in `Icao9432ModelGapSourceUnitSpecTest`
   or adjacent test code. These specs should make `EMERGENCY-1` concrete with
   narrowly grouped surfaces: distress/urgency classification; emergency
   priority/radio silence; emergency message payload/addressing/rendering;
   emergency assistance/relay/silence termination; emergency descent
   safeguarding; communications-failure frequency search/routing; blind
   transmission/repetition/scheduling/receiver-failure phraseology; SSR
   7600/7700 plus blind-clearance prohibition.
4. Add a chunk-level exact-union guard proving that the union of source refs
   cited by chunk 08 gap specs equals
   `ICAO9432.DistressUrgencyCommsFailure.Chunk08Items`.
5. Do not add scenario green tests unless a real emergency/comms-failure trace
   exists.
6. Run focused chunk tests, broad `:sim:jvmTest`, detekt, Flow validation, and
   `git diff --check`.
7. Run independent implementation review before closing and committing.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix :sim:jvmTest`
- `./gradlew-nix detekt`
- `.flow/bin/flowctl validate --epic fn-58-icao-9432-chunk-08-distress-urgency`

## Acceptance
- [x] All 46 chunk 08 accepted source units have a reviewed final state.
- [x] Every coverage/gap claim cites the accepted source-unit id.
- [x] No normal radio, go-around, or VFR traffic trace is used as emergency
      compliance evidence.
- [x] The `EMERGENCY-1` blocker is decomposed into concrete missing concepts.
- [x] Phraseology rows are not marked covered-green from typed objects.
- [x] Flow tasks and checked-in sidecars match runtime state before commit.

## Review Considerations

### FP / Type Safety

No production ADT or state field is planned. Future emergency support should
introduce typed emergency conditions, radio-failure modes, priority classes, and
SSR emergency code state rather than strings or overloaded normal-flight flags.
Catalog refs for this chunk should use `ProjectionGapSource` so they do not
look like covered scenario evidence. This epic should not add `else` fallbacks
or `error()` paths.

### Test Architecture

Tests should be high-level source-unit gap specs. Since no emergency/comms-
failure world model exists, most value is in precise expected gaps rather than
fake green scenario tests. The chunk also needs an exact-union guard so grouped
specs cannot silently omit one of the 46 accepted source units.

### Impact

The main coupling risk is false equivalence between ordinary radio/go-around
behavior and distress/urgency/comms-failure behavior. Avoid using existing
radio traces, go-around traces, or normal traffic sequencing as emergency
evidence.

### Operational Correctness

ICAO 9432 Chapter 9 concerns emergency communications, distress/urgency
priority, emergency descent, and communications failure. These are distinct
operational states from normal VFR training and ordinary ATC sequencing.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/README.md`
- `research/tools/requirements-spike/quality/icao9432_programme/classification.json`
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/`
- `research/txt/icao9432-extracted.txt`
