# fn-55-icao-9432-chunk-05-circuit-arrival ICAO 9432 chunk 05 circuit arrival landing source-mapped tests

## Goal & Context

Author source-mapped coverage for ICAO 9432 chunk 05:
`chunk-05-circuit-arrival-landing`.

Scope:

- `aerodrome_traffic_circuit_4_6_part1_en`
- `aerodrome_traffic_circuit_4_6_part2_en`
- `final_approach_landing_4_7_en`

This chunk contains 23 accepted source units covering circuit joining,
position reports, final / long-final reports, low-pass / low-approach
requests, undercarriage-observation phraseology, and touch-and-go training.
The test-authoring epic must preserve the source modality. In particular,
`may`, `should`, traffic-dependent, and local-procedure source units must not
be promoted into universal controller law.

## Boundaries

In scope:

- Verify all 23 accepted source-unit quote excerpts against
  `research/txt/icao9432-extracted.txt`.
- Produce `source_plan.md`, `expected_gaps.md`, and `coverage_report.md` under
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_05_circuit_arrival_landing/`.
- Reuse or tighten existing source-mapped tests where they honestly prove a
  chunk 05 source unit.
- Add narrow source-mapped scenario tests for current behaviour only where the
  evidence proves the source claim.
- Record policy, model, and phraseology blockers loudly.

Out of scope:

- Fixing controller, pilot, sim, or rendered phraseology behaviour.
- Treating typed instruction existence as rendered phraseology compliance.
- Marking low-pass / straight-in / local-procedure rows green unless the trace
  proves the specific source claim.
- Building generic phraseology rendering or linter support.

## Planned Shape

1. Build and review the chunk-local source plan.
2. Treat the generated classifier as planning input, not truth.
3. Use the existing touch-and-go source-backed scenario as candidate evidence
   for the touch-and-go request capability only after it proves the pilot
   request / intent on the radio, not merely the controller's later
   `ClearedTouchAndGo`.
4. Keep `CLEARED TOUCH AND GO`, `FINAL`, `LONG FINAL`, right-hand pattern,
   ATIS-in-initial-call, low-pass example, and undercarriage wording source
   units in `phraseology-later` unless rendered wording is asserted.
5. Keep straight-in approach, planned circuit entry timing, routine reports
   under local procedures, and traffic-coordinate delay/accelerate rows
   policy/model blocked unless the plan identifies an honest current evidence
   surface.
6. Run focused chunk tests, broad `:sim:jvmTest`, detekt, Flow validation, and
   `git diff --check`.
7. Run independent implementation review before closing and committing.

## Acceptance

- All 23 chunk 05 source units have a reviewed final planned state.
- Every coverage claim cites the accepted source-unit id.
- No phraseology source unit is marked covered-green from typed instruction
  evidence alone.
- Policy-sensitive source units are marked with the missing policy concept.
- Expected gaps are source-specific and not broad silent deferrals.
- Flow tasks and checked-in sidecars match runtime state before commit.

## Review Considerations

### FP / Type Safety

No production ADT or state field is planned. If implementation discovers a
needed evidence projection, it must be typed and closed, not stringly. No new
`else` branch or `error()` for type-valid states belongs in this epic.

### Test Architecture

Tests should be high-level source-mapped scenario specs. Existing scenario
tests may be reused only if their assertions prove the source unit. Phraseology
and local-procedure rows should become expected gaps when the current evidence
surface cannot prove them.

### Impact

The main coupling risk is false confidence: existing touch-and-go tests already
cite both request and clearance-phrase source units, but typed
`ClearedTouchAndGo` does not prove either rendered `CLEARED TOUCH AND GO`
wording or the pilot's preceding request by itself. This epic must correct the
coverage report, and code if needed, without expanding behaviour.

### Operational Correctness

All claims are grounded in ICAO Doc 9432 §4.6 and §4.7 source-unit ids. The
source includes local-procedure, traffic-dependent, and permissive wording;
coverage must preserve those modalities.
