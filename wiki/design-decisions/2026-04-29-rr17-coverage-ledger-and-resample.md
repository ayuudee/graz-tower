# RR-17 Coverage Ledger And Resample

Date: 2026-04-29

## Decision

After the RR-13 direct repair, run one bounded coverage pass rather than
another broad extraction effort:

- repair only narrow source-supported gaps found while building the ledger;
- write a 22-section coverage ledger for the declared manifests;
- write a concept crosswalk across the major law/procedure/phraseology topics;
- run a fresh post-repair adequacy sample as a smoke check.

The artifacts are under:

- `research/tools/requirements-spike/quality/coverage/coverage_2026-04-29-rr17-80-20/`
- `research/tools/requirements-spike/quality/adequacy/adequacy_2026-04-29-rr17-post-repair-resample/`

The new current regression snapshot is
`research/tools/requirements-spike/quality/snapshots/judgements-2026-04-29-post-rr17-coverage.csv`.

## Repair

Six records were added directly to the structured registry:

- ICAO Doc 4444 7.6.2 Position 3, with corrected non-mandatory wording.
- ICAO Doc 4444 7.6.2 Position 4, with corrected non-mandatory wording.
- ICAO Doc 4444 7.6.2 Position 5, with corrected non-mandatory wording.
- ICAO Doc 9432 2.8.3.3 route clearance is not take-off or active-runway-entry
  authority.
- ICAO Doc 9432 2.8.3.2 route clearance should, whenever possible, be passed
  before start-up.
- ICAO Doc 4444 4.6.1.1 speed-adjustment permission.

Counts after repair: 243 accepted, 0 pending, 16 rejected.

## Result

The ledger finds the registry fit for the declared 22-section frame. The fresh
post-repair sample found 32/32 records correct and 0 material omissions across
8 sampled sections.

This is still not a full-document extraction. The remaining useful source
expansions are explicit:

- ICAO Doc 4444 7.11.7 numerical reduced-runway minima;
- ICAO Doc 4444 5.8.5 opposite-direction wake separation;
- ICAO Doc 4444 7.6.3.1.2+ runway-in-use taxi/report-vacated rules;
- ICAO Doc 9432 4.4 example phraseology, if a future consumer wants example
  dialogue rather than rules.

## Review Considerations

FP / type safety: no Kotlin/domain code changed. Registry records retain
content-addressed canonical IDs and pass quote/schema/authority gates.

Test architecture: keep the existing quality-gate tests and reproducibility
audit as the machine checks. The RR-17 sample is a review artifact, not a
replacement for deterministic registry audits.

Impact: downstream consumers can treat the declared section frame as fit for
actionable rule/guidance use. Consumers needing reduced-runway numeric minima,
opposite-direction wake separation, or runway-in-use taxi rules must request a
manifest widening rather than assuming those facts are present.

Operational correctness: regulatory and phraseology claims remain source-bound:
ICAO Doc 4444 4.3.2.1, 4.5.7.5, 4.6.1, 5.8.1-5.8.4, 7.6.1-7.6.3.1.1,
7.9, 7.10, 7.11.1-7.11.6; ICAO Doc 9432 2.8.1, 2.8.3, 4.4; SERA.8005,
SERA.8010, SERA.8015; CAP 413 2.68-2.71; AIC A 21/23 H01 3.8.1-3.8.3;
EGAST GA9; SafetySense 02; and Slovenia VFR readback guidance.
