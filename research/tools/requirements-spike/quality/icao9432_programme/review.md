# FN46 review and red-team: ICAO 9432 programme map

## Verdict

Proceed with the map as a planning baseline, but treat the per-source
classification as reviewable input, not as executable truth. The chunk shape is
good enough to create the first test-authoring epic, with
`chunk-01-comms-readback-transfer` as the recommended start.

The highest-value property of the map is that it avoids pretending all 166
accepted ICAO 9432 units are equally testable today. Only 25 are marked
`testable-now`; policy, phraseology, observation facts, emergency modelling,
vehicles, and pushback stay explicit.

## What Works

- The inventory source is mechanically defensible: `documentId =
  icao9432-extracted` and `lifecycle.state = accepted`.
- The programme covers all 166 accepted units and keeps 21 rejected plus 2
  pending units outside the testing programme.
- The chunk plan follows operational areas rather than document order alone,
  which should produce coherent high-level scenarios.
- The testing/implementation firewall is explicit: chunk epics author failing
  source-mapped tests; separate implementation epics make them pass.
- Policy variance is visible through `needs-policy-type` and named concepts
  instead of being blurred into loose assertions.
- New blockers discovered by the map have `.plan` entries:
  `POLICY-1`, `PHRASE-1`, `VEHICLE-1`, `PUSHBACK-1`, and `EMERGENCY-1`.

## Red-Team Findings

1. **Heuristic normativity can misclassify individual units.**
   The classifier uses source metadata plus text patterns. That is good enough
   for project planning, but not for test authoring. Every chunk epic must
   re-check the accepted quote and section before writing tests.

2. **`phraseology-later` may be too broad.**
   Some source units marked phraseology-heavy may still have a semantic core
   that can be tested now. The first chunk review should split those into
   "semantic now, phraseology later" where appropriate.

3. **`needs-policy-type` is correct but underspecified.**
   The map names candidate policy concepts, but it does not yet define where
   policy lives: airport fixture, controller profile, procedure config, or
   test scenario. The first policy-heavy chunk must answer that before writing
   non-vague assertions.

4. **Emergency and vehicle chunks are intentionally late.**
   They cover many accepted units, but starting there would collapse the
   firewall because tests would immediately require new sim domains. Keep them
   later unless there is a deliberate model-building epic first.

5. **A high-level scenario can still overclaim source coverage.**
   The map encourages grouping, but each test must prove the cited unit through
   observable evidence. A scenario that merely happens near a rule is not
   source coverage.

## First Chunk Recommendation

Start with `chunk-01-comms-readback-transfer`.

Rationale:
- It has 20 units, enough breadth to exercise the programme.
- Eight units are marked `testable-now`.
- It touches both protocol-level evidence and sim-level communication facts.
- It includes policy and observation gaps without being dominated by them.
- It will force an early answer on how to express expected failing tests while
  preserving the implementation firewall.

Suggested first chunk boundaries:
- Include communications establishment, transfer/frequency, and readback.
- Author source-mapped tests only for units whose evidence is available or
  whose expected gap can fail loudly.
- Do not implement rendered phraseology or frequency-transfer facts inside the
  test-authoring epic.
- Create follow-on implementation epics for `FN44-GAP-1`, `FN44-GAP-2`,
  `POLICY-1`, and phraseology fallout as needed.

## Review Considerations

### FP / type safety

The generated CSV/JSON uses strings because it is a planning artifact. Any
promotion into permanent test code should use closed enums/sealed types for
normative kind, testability, evidence target, chunk id, and policy concept.
Do not let arbitrary string statuses leak into assertions.

### Test architecture

The map supports high-level source-mapped tests, but it does not relax the test
standards. Tests should exercise real protocol/sim behaviour and fail loudly
when evidence is missing. No disabled tests, skip lists, or broad "expected
gap" buckets without source ids.

### Impact

The map creates a durable project shape: eight chunk epics plus separate repair
epics. That is useful, but it can create administrative weight. Keep each chunk
small enough to author, review, and then hand off implementation repairs.

### Operational correctness

ICAO 9432 is operational guidance and phraseology guidance in many places, not
always a hard behavioural law. Chunk epics must distinguish mandatory
requirements from policy, examples, and phraseology. Where ICAO Doc 4444, SERA,
or CAP 413 is the stronger source, the test should cite that source too rather
than overloading ICAO 9432.
