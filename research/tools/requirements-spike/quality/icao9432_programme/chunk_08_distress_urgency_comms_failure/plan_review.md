# Chunk 08 Plan Review

Review target:

- `.flow/specs/fn-58-icao-9432-chunk-08-distress-urgency.md`
- `source_plan.md`
- `expected_gaps.md`

Independent reviewers:

- Plan review: subagent `01a0aa55-be14-7871-beb1-13f4bb1d1188`.
- Impact review: subagent `01a0aa55-e208-7962-bfda-b095700a4581`.

## Findings And Resolution

| Severity | Finding | Resolution |
|---|---|---|
| None | Plan review found all 46 accepted rows accounted for exactly once and counts consistent. | No change required. |
| Blocking impact | Grouped gap specs could silently omit one source unit because `assertHasModelGap()` proves only at least one probe gapped. | Added an exact-union guard requirement tying the gap-spec source refs to `ICAO9432.DistressUrgencyCommsFailure.Chunk08Items`. |
| Blocking impact | Planned communications-failure group was too broad. | Split planned specs into eight narrower missing surfaces. |
| Blocking impact | Broad group specs must remain source-specific. | Added requirement for narrowly grouped source-specific probes rather than broad `EMERGENCY-1` assertions. |
| Blocking impact | Catalog refs should not look like covered scenario evidence. | Planned chunk 08 refs as `ProjectionGapSource`. |

## Final Plan Status

SHIP after impact fixes.
