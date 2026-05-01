# RR-17 Coverage Assessment

Date: 2026-04-29

## What Changed

The coverage review found six narrow source-supported gaps after the RR-13
repair. They were repaired directly in the structured registry:

- ICAO Doc 4444 7.6.2 Position 3 corrected from rejected shall-strength wording
  to a non-mandatory normal-position fact.
- ICAO Doc 4444 7.6.2 Position 4 corrected the same way.
- ICAO Doc 4444 7.6.2 Position 5 corrected the same way.
- ICAO Doc 9432 2.8.3.3 added the route-clearance-is-not-takeoff/runway-entry
  rule.
- ICAO Doc 9432 2.8.3.2 added the route-clearance-before-start-up guidance.
- ICAO Doc 4444 4.6.1.1 added the top-level speed-adjustment permission.

Audit trail:

- curation run: `quality/curation/manual_rr17_coverage_2026-04-29T22-00-00Z/`
- judgement snapshot: `quality/snapshots/judgements-2026-04-29-post-rr17-coverage.csv`
- registry counts after repair: 243 accepted, 0 pending, 16 rejected.

## Review Outputs

- `section_coverage_ledger.md` checks all 22 declared source sections.
- `concept_crosswalk.md` checks the major law/procedure/phraseology concepts
  across source families.
- `../../adequacy/adequacy_2026-04-29-rr17-post-repair-resample/` is the fresh
  post-repair sample pack and filled review.

## Conclusion

The registry is now fit for the declared 22-section ingestion frame as an
actionable rule/guidance corpus. The remaining uncovered material is explicit
and bounded:

- low-value note-taking/listener advice in EGAST and Slovenia;
- ICAO Doc 9432 taxi dialogue examples, if downstream wants example
  phraseology rather than rules;
- adjacent source sections outside the manifests, especially ICAO Doc 4444
  7.11.7 reduced-runway numerical minima, 5.8.5 opposite-direction wake
  separation, and 7.6.3.1.2+ runway-in-use taxi/report-vacated rules.

No further broad resampling looks worthwhile before a consumer demands one of
those out-of-scope areas.
