# RR-13 Direct Repair Summary

Date: 2026-04-29

The RR-13 adequacy review found sampled blockers in the registry output.
Per the follow-up decision, these were repaired directly in the structured
registry rather than by building a reusable remediation tool.

## Changes

- Repaired accepted records whose `exactSourceQuotes` were verbatim but
  insufficient by adding parent/list/table context and recomputing
  canonical IDs.
- Promoted corrected canonical replacements for source-supported records
  that had been rejected only because of quote-shape defects.
- Added missing EGAST and Slovenia VFR readback guidance records from the
  reviewed source windows.
- Rejected three adjacent Polish/pushback records that had contaminated
  the accepted English ICAO 9432 taxi section.
- Appended 62 `manual_repair` rows to `quality/judgements.csv`.
- Refreshed `registry/ollama_first/manifest.json`.
- Snapshotted the repaired ledger at
  `quality/snapshots/judgements-2026-04-29-post-rr13-repair.csv`.

## Result

Live registry counts after repair:

- candidates: 237
- pending: 0
- rejected: 16
- total auditable records: 253

Targeted checks after repair:

- Accepted quote-audit failures: 0
- EGAST sampled omissions present: ROGER guidance, WILCO permitted-use
  exceptions, and take-off-clearance-first guidance.
- ICAO 4444 departure-sequence sampled omissions present: route after
  take-off, minimum departure interval, wake turbulence, priority
  aircraft, and ATFM aircraft factors.
- Slovenia sampled omissions present: readback item list, WILCO
  definition, ROGER acknowledgement guidance, and take-off-clearance-
  first guidance.
- ICAO 9432 taxi accepted records no longer include pushback or
  `przerwij wypychanie` contamination.

The old `post-rr11-rr12` regression baseline intentionally fails after
this repair because the ICAO 9432 taxi section's accepted count drops
when contaminated records are rejected. The new `post-rr13-repair`
snapshot is the correct baseline for future regression checks.
