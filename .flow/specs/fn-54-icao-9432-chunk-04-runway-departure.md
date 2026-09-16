# fn-54-icao-9432-chunk-04-runway-departure ICAO 9432 chunk 04 runway departure source-mapped tests

## Overview
Author source-mapped evidence coverage for ICAO 9432 chunk 04
(`chunk-04-runway-departure`): runway entry, line-up, conditional
clearances, take-off clearance, and take-off cancellation / runway-freeing
behaviour.

This is a test/evidence-mapping epic. It must not repair controller, pilot, or
sim behaviour in the same pass except for narrowly scoped evidence projection
needed to express an already-observed fact. Behaviour repairs remain separate
implementation epics so the regulatory test wall stays independent.

## Scope
Accepted source units: 19.

- `takeoff_procedures_4_5_1_to_4_5_5_en`: 7 units.
- `takeoff_procedures_4_5_6_to_4_5_7_en`: 5 units.
- `takeoff_procedures_4_5_8_to_4_5_12_en`: 7 units.

Current classification:

- `testable-now`: 6 units.
- `needs-policy-type`: 5 units (`POLICY-1`).
- `phraseology-later`: 8 units (`PHRASE-1`).

Expected coverage targets:

- Source-plan all 19 accepted units from
  `research/tools/requirements-spike/quality/icao9432_programme/classification.json`.
- Verify every accepted quote against `research/txt/icao9432-extracted.txt`.
- Author high-level source-mapped evidence tests only where existing traces or
  structural protocol surfaces can honestly prove the claim.
- Record policy-sensitive rows as policy-blocked where the source says
  "usually", "should", "may", or depends on visibility, emergency, traffic
  development, or controller intervention policy.
- Keep rendered phraseology rows blocked by `PHRASE-1`; do not add string
  phraseology assertions in this epic.

## Approach
1. Build chunk-local artifacts under
   `research/tools/requirements-spike/quality/icao9432_programme/chunk_04_runway_departure/`.
   At minimum produce `source_plan.md`, `expected_gaps.md`, and
   `coverage_report.md`.
2. Mechanically verify the 19 source-unit quotes before authoring tests.
3. Reclassify the six `testable-now` rows after source-text review:
   - tower transfer at or approaching runway-holding position;
   - conditional runway clearance visibility constraints;
   - poor-visibility request to report airborne;
   - departure instructions with take-off clearance;
   - take-off clearance cancellation;
   - quickly freeing the runway for landing traffic.
4. Prefer one or two believable high-level departure scenarios over many
   unit-level assertions. Use existing LOWG/G0/G1-style traces where they
   genuinely prove line-up/takeoff ordering or runway lifecycle facts.
5. Add typed source refs for chunk 04 source units that receive permanent
   evidence-DSL coverage.
6. Add source-mapped tests or loud covered-red tests for the truly testable
   rows. Do not green a source unit from a scenario that merely happens near
   the behaviour.
7. Record expected gaps for:
   - `PHRASE-1` rendered phraseology;
   - `POLICY-1` operational guidance / controller intervention policy;
   - any "testable-now" row whose source review reveals a missing evidence
     primitive.
8. Produce the final coverage report with one final state per source unit:
   `covered-green`, `covered-red`, `model-gap`, `policy-blocked`, or
   `phraseology-later`.

## Review Considerations

- FP / type safety: new source refs must use typed `EvidenceSourceRef`
  records. New evidence payloads/selectors, if needed, must use closed types
  and exhaustive `when` handling. Do not hide unmodelled departure states with
  catch-all branches.
- Test architecture: tests should be high-level and source-mapped. A test may
  cite multiple units only when the same observed trace proves all cited units.
  Expected gaps must be source-specific and loud; no skip lists, disabled
  tests, or broad "later" buckets without source ids.
- Impact: this epic must not implement departure policy, rendered
  phraseology, poor-visibility operations, or new abort-takeoff behaviour.
  If the evidence surface cannot express a claim, record that as a gap and
  create a later repair epic.
- Operational correctness: ICAO Doc 9432 §4.5 mixes phraseology examples,
  "may"/"should" operational guidance, and stronger runway-safety constraints.
  Do not turn "usually", "may", or "should" into universal controller law.
  Where stronger law is needed, cross-check existing ICAO Doc 4444 / CAP 413
  anchors before authoring assertions.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432Chunk04RunwayDepartureEvidenceTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix :sim:jvmTest`
- `./gradlew-nix detekt`
- `.flow/bin/flowctl validate --epic fn-54-icao-9432-chunk-04-runway-departure`

## Acceptance
- [ ] Chunk-local source plan accounts for all 19 accepted source units.
- [ ] Plan review is complete before implementation starts.
- [ ] Source quotes are verified against `research/txt/icao9432-extracted.txt`.
- [ ] Testable runway-departure units have source-mapped evidence tests or a
      documented reason they are not honestly testable.
- [ ] Policy and phraseology blockers remain explicit.
- [ ] Completion coverage report names final state per source unit.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/classification.json`
- `research/tools/requirements-spike/quality/icao9432_programme/proposed_chunk_epics.md`
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_03_ground_movement/coverage_report.md`
- `research/txt/icao9432-extracted.txt`
