# FN20 Final v7 Split Classification

Generated: `2026-05-15`
Updated: `2026-05-16`

## Context

The v6 retry completed at `176/187` strict-complete sections. The remaining
failures were not ordinary retry noise: the model returned structured failure
payloads indicating broad or mixed source windows, typically with
`requiresSplit: true`. This v7 queue replaces those broad failed windows with
narrower line-stable manifest sections. The final run completed after two
additional density-driven split passes: EPPLS Chapter 12 was split into
line-stable background-support windows, and SERA.9010 ATIS content windows were
split down to arrival/departure sub-lists.

## Split Sources

| Source | Failed broad window | v7 split count | Reason |
| --- | --- | ---: | --- |
| CAP 413 | `ch4_4_84_part3` | 4 | Mixed RNAV, gear, wake/jet/slipstream, runway-vacating/helicopter, and vehicle/tug topics. |
| H01 | `controlled_aerodromes_pushback_deice_5_3_1_4_to_5_3_1_5` | 5 | Pushback and de/anti-icing phraseologies needed separate event scopes. |
| ICAO 4444 | `departing_aircraft_procedures_6_3` | 4 | General, standard clearance, SID, communication-failure, and departure-sequence clauses were too broad together. |
| ICAO 4444 | `aerodrome_information_to_aircraft_7_4` | 5 | Start-up, MET, traffic/incursion, position-uncertainty, wake/abnormal topics were distinct. |
| ICAO 4444 | `area_phraseologies_route_level_separation_12_3_2_2_to_12_3_2_9` | 5 | Route, level, emergency/refusal, separation, and offset phraseologies have different structures. |
| ICAO 4444 | `approach_phraseologies_12_3_3` | 4 | Departure, approach, holding, and expected-approach-time phraseologies were split by subsection. |
| ICAO 4444 | `aerodrome_phraseologies_start_taxi_12_3_4_1_to_12_3_4_7` | 3 | Start/pushback, towing/time/departure data, and taxi phraseologies were split. |
| ICAO 4444 | `aerodrome_phraseologies_holding_takeoff_12_3_4_8_to_12_3_4_12` | 4 | Holding/crossing, take-off preparation, take-off clearance, and after-take-off instructions were split. |
| ICAO 4444 | `aerodrome_phraseologies_circuit_landing_vacating_12_3_4_13_to_12_3_4_20` | 5 | Circuit/approach, landing, delay/missed approach, information, and runway-vacating blocks were split. |
| ICAO 4444 | `emergency_procedures_15_1` | 4 | Emergency general, unlawful interference, bomb threat/ground isolation, and emergency descent were split. |
| SERA | `atis_9010` | 4 | ATIS request/reply, combined, arrival-only, and departure-only contents were split. |
| SERA | `atis_arrival_departure_combined_contents_9010_b` | 3 | Combined arrival/departure ATIS list was too dense as one 21-item window; split into identity/operations, wind/visibility/CAVOK, and weather/forecast/instructions. |
| SERA | `atis_arrival_contents_9010_c` | 3 | Arrival-only ATIS list produced 23 structure items before interruption; split into identity/operations, wind/visibility/weather/CAVOK, and forecast/instructions. |
| SERA | `atis_departure_contents_9010_d` | 3 | Departure-only ATIS list was split preemptively on the same density pattern as arrival-only ATIS. |
| EPPLS | Chapter 12 broad intake candidates | 24 | EPPLS is background material, not legal authority; Chapter 12 was split into topical windows so the run could identify any source-supported background units without broad textbook contamination. |

## Queue Shape

Final `ready_to_ingest_batch.json` dry-run result:

- sections requested: `266`
- documents: CAP 413 `46`, EPPLS `24`, H01 `48`, ICAO 4444 `112`, ICAO 9432 `19`, SERA `17`

Final combined run result against `/Users/andrew/requirements-source-units-fn10-2026-05-11-v6`:

- sections requested: `266`
- sections skipped as strict-complete cache hits: `260`
- sections processed in the last pass: `6`
- sections failed: `0`
- strict completeness after run: `266/266`
- raw ingest consistency audit: `pass`, with `0` unreadable JSON and `0` hard consistency issues

The raw audit reports `22` non-manifest stale partial directories from
interrupted or superseded broad windows. None has a `run_manifest.json`; they
are not part of the final current-frame manifest and are retained only as
visible operational history.

## Review Considerations

**FP / type safety:** This is manifest/data work only. The pipeline still fails
loudly per section; no failed section is hidden or excluded.

**Test architecture:** The immediate gates are JSON parseability and
`ingest_section_batch.py --dry-run`. The final run still requires raw-root
consistency, quote/schema audits, and promotion/package validation.

**Impact:** Splitting increases manifest section count but reduces model context
pressure and makes failures local to a clause family instead of a mixed source
window.

**Operational correctness:** No new operational claims are introduced here.
Each split preserves the source document, exact line range, authority ceiling,
and section-specific notes for later extraction.
