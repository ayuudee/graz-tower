# fn-53-icao-9432-chunk-03-ground-movement ICAO 9432 chunk 03 ground movement source-mapped tests

## Overview
Author source-mapped evidence coverage for ICAO 9432 chunk 03
(`chunk-03-ground-movement`): pushback/powerback (§4.3) and taxi (§4.4).

This epic is primarily a test/evidence-mapping epic. It must not repair sim
behaviour in the same pass unless the repair is limited to evidence projection
needed to express an already-observed fact. Domain behaviour repairs remain
separate epics.

## Scope
Accepted source units: 11.

- `pushback_powerback_4_3_en`: 5 units.
- `taxi_4_4_en`: 6 units.

Coverage targets:

- Cover the testable taxi units with source-mapped evidence or structural
  evidence where current typed surfaces are sufficient.
- Record pushback/powerback as expected gaps where the sim lacks pushback,
  powerback, ground-crew, and apron-management actors.
- Record taxi policy units as policy-blocked when the source says "normally",
  "may", or depends on prevailing traffic circumstances / local procedures.
- Keep phraseology units blocked by `PHRASE-1`; do not add rendered-string
  assertions.

## Approach
1. Build a chunk-local source plan from the accepted registry records and
   verify source-unit quotes against `research/txt/icao9432-extracted.txt`.
2. Review the plan before implementation, with special attention to false
   universals around "normally", "may", local procedures, and pushback actor
   gaps.
3. Add typed source refs for any chunk 03 source units that receive permanent
   evidence-DSL coverage.
4. Add source-mapped tests:
   - taxi clearance contains a clearance limit;
   - taxi limit beyond a runway requires either an explicit cross-runway
     clearance or an instruction to hold short;
   - ATIS-acknowledged taxi instruction does not require duplicate departure
     information;
   - runway-vacated evidence is surfaced only where current trace evidence is
     strong enough, otherwise record an observation gap.
5. Add expected-gap coverage for pushback/powerback and policy/phraseology
   blocked rows.
6. Produce chunk-local `source_plan.md`, `expected_gaps.md`, and
   `coverage_report.md`.

## Review Considerations

- FP / type safety: typed source refs must use the registry-backed
  `EvidenceSourceRef` surface. New evidence payloads/selectors, if any, must
  use closed types and exhaustive `when` handling. No catch-all branch should
  convert unknown ground-movement semantics into a pass.
- Test architecture: prefer source-mapped high-level evidence tests over unit
  tests. A test may cite multiple source units only when the same observed
  scenario genuinely proves all of them. Expected gaps must be loud and
  source-specific.
- Impact: do not model pushback, powerback, ground crew, apron-management, or
  local-procedure policy inside this epic. Those are separate repair epics.
  Avoid over-coupling taxi evidence to LOWG-specific geometry unless the
  source claim is explicitly scenario-scoped by the evidence case.
- Operational correctness: ICAO Doc 9432 §4.3 pushback/powerback depends on
  local procedures and ground crew. ICAO Doc 9432 §4.4 taxi includes policy
  language ("normally", "may", "depending on prevailing traffic
  circumstances") that must not be asserted as universal ATC law.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432Chunk03GroundMovementEvidenceTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix :sim:jvmTest`
- `./gradlew-nix detekt`
- `.flow/bin/flowctl validate --epic fn-53-icao-9432-chunk-03-ground-movement`

## Acceptance
- [ ] Chunk-local source plan accounts for all 11 accepted source units.
- [ ] Plan review is complete before implementation.
- [ ] Testable taxi units have source-mapped evidence tests or a documented
      reason they are not honestly testable.
- [ ] Pushback/powerback model gaps are recorded without silent skips.
- [ ] Policy and phraseology blockers remain explicit.
- [ ] Completion coverage report names final state per source unit.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/classification.json`
- `research/tools/requirements-spike/quality/icao9432_programme/proposed_chunk_epics.md`
- `research/txt/icao9432-extracted.txt`
