# FN47 plan review: chunk 01 communications, transfer, readback

## Plan

Author the first ICAO 9432 chunk as a test-authoring epic, not a behaviour
repair epic. The chunk covers 20 accepted source units from communications
establishment, frequency transfer, readback, and hearback.

The intended workflow is:

1. Re-check the accepted source-unit JSON and quote for all 20 units.
2. Assign each unit a coverage state: `covered-green`, `covered-red`,
   `expected-gap`, `phraseology-later`, `policy-blocked`, or `not-applicable`.
3. Author source-mapped tests for units whose evidence can be expressed now.
4. Record expected gaps for transfer/frequency facts, phraseology facts, policy
   concepts, and known timing/workload model gaps.
5. Produce a chunk report that makes out-of-band repair work obvious.
6. Review for overclaiming, hidden implementation coupling, and test-standard
   compliance.

## Review

The plan is aligned with the 9432 programme goal because it keeps all 20 source
units in one finite chunk and makes every source unit end in a named state.
That should prevent drift: the next action is either author more tests in this
chunk, fix a named blocker, validate after repair, or move to the next chunk.

The test/implementation firewall is explicit enough: this epic may add tests
and expected-gap records, but it may not change production controller, pilot,
sim, or phraseology behaviour to satisfy the tests.

## Red-Team Risks

1. **Coverage overclaim.** A readback test could cite a broad source unit while
   only proving that one typed instruction exists. Mitigation: each test must
   name the exact evidence and coverage report state.

2. **Phraseology leakage.** Several chunk-01 units are phraseology-heavy. Typed
   protocol semantics must not be used as a substitute for rendered RT wording.
   Those units should stay `phraseology-later` unless rendered facts exist.

3. **Policy ambiguity.** Guidance such as "whenever possible" cannot become a
   universal assertion. Mark policy-dependent units as `policy-blocked` until a
   typed policy concept exists.

4. **Broken-test management.** Red tests are useful only if the failure points
   to source id, expected evidence, observed evidence, and repair owner. The
   chunk report must be kept current.

## Decision

Proceed with fn-47. Start with task `.1`: re-check the 20 source units and
write the chunk source plan before authoring tests.

## Review Considerations

### FP / type safety

Permanent code should use typed source refs, typed gap ids, and closed coverage
states. This planning artifact may use strings; test code should avoid loose
status strings where practical.

### Test architecture

Tests must remain high-level and source-mapped. Unit-style protocol tests are
acceptable only where the source claim itself is a protocol/readback semantic
oracle, not where the real behaviour depends on sim timing or radio flow.

### Impact

This epic may intentionally make a suite red. That is acceptable if the red
state is traceable and separate repair work is named. It should not destabilize
unrelated goldens by changing behaviour.

### Operational correctness

All claims must cite accepted ICAO 9432 source-unit ids and quote windows.
Policy/guidance/phraseology must remain distinct from mandatory behaviour.
