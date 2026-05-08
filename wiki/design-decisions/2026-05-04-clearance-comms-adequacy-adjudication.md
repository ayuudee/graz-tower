# Clearance/Comms Adequacy Adjudication

Date: 2026-05-04

## Decision

Treat the 2026-05-04 80/20 adequacy review as completed evidence for the
declared 46-window clearance/comms registry slice, after applying direct
structured-output repairs for material findings.

This does not turn the registry into a full-document or full-corpus extraction.
It means the current declared slice has now passed both mechanical validation
and an independently adjudicated sample review.

## Findings

The review pack at
`research/tools/requirements-spike/quality/adequacy/adequacy_2026-05-04-clearance-comms-80-20/`
sampled 48 records and 12 source sections.

Four sampled records were source-supported but incorrectly left rejected because
their quotes crossed page-layout breaks or used non-verbatim punctuation:

- CAP 413 2.66: tactical restrictions that remain in place shall be reiterated
  after local departure instructions or for already-airborne aircraft.
- CAP 413 2.74: critical information definition.
- ICAO Doc 4444 4.5.7.2.1: standard departure/arrival route phrase permission.
- SERA.8020(c)(2)(ii): destination-changed flight-plan-change request content.

A targeted sibling sweep found four additional same-pattern source-supported
rejections:

- ICAO Doc 4444 4.5.7.4.2: use of "UNABLE" when a requested change cannot be
  cleared.
- ICAO Doc 4444 4.5.7.2.2: "cleared flight planned route" shall not be used
  for re-clearance.
- ICAO Doc 4444 4.5.7.2.1: "cleared flight planned route" usage permission.
- SERA.8020(a)(1)(ii): direct operation between navigation facilities/points
  when not on an established ATS route.

All eight findings are repaired as new accepted content-addressed records. The
original rejected records remain in `rejected/` for audit history.

## Result

Post-repair registry counts:

- 431 accepted records.
- 0 pending records.
- 34 rejected records.

Validation:

- Reproducibility audit: 465 records, 0 mismatches.
- Accepted quote audit: 546 quotes, 0 misses.
- Regression check against `judgements-2026-05-04-post-clearance-comms.csv`: 0
  hard fails, 0 soft warnings.
- Offline tests: 91 quality-gate tests and 13 override-contract tests.

Current snapshot:

- `research/tools/requirements-spike/quality/snapshots/judgements-2026-05-04-post-rr21-adequacy.csv`

## Implication

Downstream consumers may cite the registry as a mechanically validated and
80/20-adequacy-reviewed translation of the declared 46-window slice. They must
still respect the declared-slice boundary in
`research/tools/requirements-spike/registry/ollama_first/DECLARED_SLICE.md`.

## Review Considerations

FP / type safety: no Kotlin/domain code changed. The repair is registry data
plus a narrow audit script. Content identity remains content-addressed over
document, section, claim, and quote set.

Test architecture: the load-bearing checks are quote audit, reproducibility
audit, regression comparison, the two Python test files, and the completed
record/section adequacy CSVs.

Impact: this raises confidence in the existing declared slice without widening
scope. The main remaining failure mode is over-claiming beyond those 46 source
windows.

Operational correctness: every repaired operational claim is tied to its source
document and section above; no uncited ATC rule is introduced by this decision.
