# FN47 review: chunk 01 source-mapped test wall

## Verdict

Proceed, with one important framing note: chunk 01 is a mixed success, not a
complete implementation milestone. It proves the workflow:

- every source unit in the chunk has a named state;
- source-mapped tests can be written without changing production behaviour;
- blocked units remain explicit;
- repair work is now visible and separable.

Final chunk state:

| State | Units |
|---|---:|
| `covered-green` | 6 |
| `expected-gap` | 4 |
| `phraseology-later` | 7 |
| `policy-blocked` | 2 |
| `not-applicable` | 1 |

## What Works

- The test/implementation firewall held. No controller, pilot, sim, or
  phraseology production behaviour was changed to make source tests pass.
- The source plan corrected two overclaims before test authoring:
  - the Appendix cross-reference became `not-applicable`;
  - doubtful-reception repetition became `expected-gap` via `COMMS-1`.
- Structural readback source units are now covered through exact evidence
  source refs, not only broad `RequiredItems` grouping.
- Hearback/correction source units are covered by source-backed controller
  readback classification tests.
- Transfer, phraseology, and policy units remain explicit rather than weakened
  into current-implementation assertions.

## Red-Team Findings

1. **Hearback coverage is still narrow.**
   The two hearback tests prove correct/incorrect classification, not the full
   controller action pipeline from observed readback to emitted correction.
   This is acceptable for fn-47 because the source unit under test says the
   controller must listen/ascertain and correct discrepancies, and existing
   round-trip sim coverage exercises correction emission. A later repair or
   validation pass may want a source-mapped integration test for the full
   pipeline.

2. **Structural readback tests are protocol-level.**
   That is appropriate for source units whose oracle is the required readback
   atom set, but it would be wrong for phraseology units. The phraseology rows
   correctly remain blocked by `PHRASE-1`.

3. **Policy blockers are doing real work.**
   `POLICY-1` prevents guidance such as "whenever possible" from becoming a
   universal assertion. Do not close those rows until there is an explicit
   policy object in the test setup.

4. **The next action is clear.**
   Either create repair epics for `COMMS-1`, `POLICY-1`, `PHRASE-1`,
   `FN33-MODEL-1`, and `FN44-GAP-*`, or move to chunk 02 knowing chunk 01 has
   open blocked rows. The programme should not lose sight of those blocked rows.

## Recommendation

Keep fn-47 as the model for subsequent chunks:

1. Source re-check.
2. Conservative source plan.
3. Source-mapped tests for honest candidates.
4. Explicit expected-gap ledger.
5. Coverage report and review.

For out-of-band repair, use separate epics and then a validation task/epic that
updates `coverage_report.md` from blocked/red to covered-green. Do not merge
repair behaviour into source test-authoring epics.

## Review Considerations

### FP / type safety

The production code was not touched. Test catalog additions use typed
`EvidenceSourceRef`s and registry validation. Future coverage-state machinery
should avoid raw strings if it moves from research artifacts into code.

### Test architecture

The new tests exercise real protocol/controller behaviour. They are not merely
testing that a type exists. The remaining blocked rows are documented instead
of skipped.

### Impact

The main coupling added is to the evidence source catalog and source-citation
validation. That is intended. The cost is that adding source refs requires
maintaining exact catalog pins, which is acceptable for a regulatory test wall.

### Operational correctness

Every covered or blocked row retains the accepted ICAO 9432 source-unit id.
Phraseology and policy claims remain distinct from structural behaviour claims.
