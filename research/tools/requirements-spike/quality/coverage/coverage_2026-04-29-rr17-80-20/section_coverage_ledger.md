# RR-17 Section Coverage Ledger

Date: 2026-04-29

Frame: the 22 sections declared in `research/tools/requirements-spike/documents/*.json`.
This is not a claim of full-document coverage for every source PDF/TXT file.

Live registry after the RR-17 coverage repair:

- accepted candidates: 243
- pending: 0
- rejected: 16
- auditable records: 259
- judgement snapshot: `quality/snapshots/judgements-2026-04-29-post-rr17-coverage.csv`

## Reading The Ledger

- `Fit` means the declared source section has no material omission for
  downstream rule/guidance consumers.
- `Fit / bundled` means the source is represented as a family-level fact
  whose exact quote preserves the child alternatives; it is actionable, but
  less atomized than a condition-per-record form.
- `Fit / boundary` means the declared line window is covered, but adjacent
  high-value text outside the declared window remains available for a future
  source expansion.
- `Possible low-value residual` means omitted content is human technique or
  illustrative example phraseology, not a missing normative rule.

| Source Section | Records | Verdict | Coverage Notes |
|---|---:|---|---|
| CAP 413 2.68-2.71 (`cap413-extracted/readback_2_68_2_71`) | 8 accepted | Fit | Covers route-clearance readback, full-readback list, callsign inclusion, missing-readback follow-up, clarification requests, abbreviated readback for non-listed items, and incorrect-readback correction using NEGATIVE. RTF examples are not separately atomized. |
| EGAST GA9 readback advisory (`egast-vfr-extracted/readback_advisory`) | 17 accepted | Fit / possible low-value residual | Covers readback item list, acknowledgement by callsign/ROGER, WILCO meaning and limits, clarification, readback completeness, normal readback order, and the take-off-clearance-first exception. Residual: note-taking / another-listener advice is not represented. |
| AIC A 21/23 H01 3.8.1 (`h01-extracted/acknowledgement_3_8_1`) | 11 accepted, 1 rejected | Fit | Covers aircraft/ATS acknowledgement callsign rules, safety-related readback child items, other clearances/instructions, controller discrepancy correction, CPDLC voice-readback exception, optional verification readback, and WEATHER RECEIVED-style acknowledgement. The rejected umbrella is redundant over accepted child records. |
| AIC A 21/23 H01 3.8.2 (`h01-extracted/end_of_conversation_3_8_2`) | 1 accepted | Fit | Covers termination of a radiotelephone conversation by the receiving ATS unit or aircraft using its own callsign. |
| AIC A 21/23 H01 3.8.3 plus 3.9 fragment (`h01-extracted/corrections_3_8_3`) | 12 accepted | Fit | Covers CORRECTION, CORRECTION I SAY AGAIN, SAY AGAIN variants, NEGATIVE I SAY AGAIN, SAY AGAIN WIND, difficult-reception repeats, and the included frequency-use/frequency-designation fragment. The section ID is slightly narrower than the line window, but the extra 3.9 content is represented rather than silently ignored. |
| ICAO Doc 4444 4.3.2.1 (`icao4444-extracted/transfer_4_3_2_1`) | 4 accepted, 6 rejected | Fit / bundled | Covers arriving transfer, departing transfer, transfer-of-communications timing, and direct ACC/tower transfer by prior arrangement. Child-condition rejected records remain rejected because of quote-shape or bundle issues; the accepted arriving/departing records preserve the full child alternatives in exact quotes. |
| ICAO Doc 4444 4.5.7.5 (`icao4444-extracted/readback_4_5_7_5`) | 8 accepted, 1 rejected | Fit | Covers readback-required item list, other clearances/instructions, controller listen-and-correct duty, CPDLC voice-readback exception, and CPDLC cross-reference note. Rejected parent is redundant/non-standalone. |
| ICAO Doc 4444 4.6.1 (`icao4444-extracted/speed_control_4_6_1`) | 16 accepted | Fit | Covers the top-level speed-adjustment permission added in RR-17, adequate notice, persistence until cancelled/amended, holding-pattern exclusion, limited/frequent speed-change guidance, flight-crew unable-to-comply notification, controller alternate method, Mach/IAS expression, cancellation advisory, and performance notes. |
| ICAO Doc 4444 5.8.1-5.8.4 (`icao4444-extracted/wake_turbulence_5_8`) | 14 accepted | Fit / boundary | Covers declared wake turbulence applicability, caution/responsibility, arriving minima, departing same/parallel/crossing runway minima, intermediate-departure minima, and displaced-threshold minima. Boundary: 5.8.5 opposite-direction wake separation begins just after the declared scope and is not extracted. |
| ICAO Doc 4444 7.6.1-7.6.3.1.1 (`icao4444-extracted/aerodrome_traffic_7_6`) | 15 accepted, 3 rejected | Fit / boundary | Covers clear/concise visual-observation instructions, aerodrome positions 1-6 after RR-17 corrected replacements for positions 3-5, close-watch/no-delay guidance, taxi-clearance content, runway-crossing/hold-short explicit clearance, standard taxi routes, and route-designator guidance. Boundary: 7.6.3.1.2+ runway-in-use taxi/report-vacated/holding-position rules are outside the declared section. |
| ICAO Doc 4444 7.9 (`icao4444-extracted/departing_aircraft_7_9`) | 19 accepted | Fit | Covers departure sequence order/deviation, all six sequence factors, ATFM readiness note, departure separation, take-off-clearance separation assurance, ATC-clearance prerequisite, tower forwarding, TAKE-OFF word-use rule, runway-designator inclusion, and immediate take-off continuous-movement duty. |
| ICAO Doc 4444 7.10 (`icao4444-extracted/arriving_aircraft_7_10`) | 13 accepted, 1 rejected | Fit | Covers landing-aircraft separation, landing-clearance timing and runway designator, landing/roll-out requests, HEAVY touchdown-zone constraint, unable-to-comply advice, and runway-vacated reporting. The rejected cross-reference-only record is not actionable. |
| ICAO Doc 4444 7.11.1-7.11.6 (`icao4444-extracted/reduced_runway_7_11`) | 18 accepted | Fit / boundary | Covers safety-assessment prerequisite and factors, AIP/local publication, controller training, daylight constraint, aircraft categories, departing-vs-preceding-landing exclusion, and all conditions in 7.11.6. Boundary: numerical minima in 7.11.7 are outside the declared source window except for the heading line. |
| ICAO Doc 9432 2.8.1 English (`icao9432-extracted/communications_2_8_1_en`) | 5 accepted | Fit | Covers full callsigns on establishment, ALL STATIONS broadcast form from ground and aircraft, no-reply expectation for general calls, and request for repetition when reception is doubtful. |
| ICAO Doc 9432 2.8.3 English (`icao9432-extracted/readback_2_8_3_en`) | 8 accepted | Fit | Covers slow/clear clearance delivery, route clearance before start-up when possible, no clearance during line-up/take-off, route clearance not being take-off/runway-entry authority, TAKE OFF word-use rule, readback-required list, and other-clearance acknowledgement. |
| ICAO Doc 9432 4.4 English taxi (`icao9432-extracted/taxi_4_4_en`) | 6 accepted, 3 rejected | Fit / possible low-value residual | Covers taxi clearance limit, normal and alternate departing clearance limits, explicit runway-crossing/hold-short clearance, runway-vacated definition, and ATIS departure-information shortcut. Polish pushback contamination is rejected. Residual: dialogue examples for follow/give-way, backtrack/line-up/hold-short, report-vacated/crossing, and air-taxi/avoidance are not atomized. |
| UK CAA SafetySense 02 readbacks (`safetysense22-extracted/readbacks`) | 15 accepted, 1 rejected | Fit | Covers all 15 listed readback items with parent context. Rejected umbrella list heading is redundant over the accepted item-level records. |
| SERA.8005 (`sera-923-2012-extracted/atc_service_8005`) | 14 accepted | Fit | Covers ATC-unit information/position/clearance/coordination duties, all listed separation classes, own-separation exception, and vertical/horizontal separation methods including longitudinal and lateral separation. |
| SERA.8010 (`sera-923-2012-extracted/separation_minima_8010`) | 4 accepted | Fit | Covers ANSP selection/authority approval, neighbouring-airspace consultation, ATS-unit notification, and pilot/operator AIP notification when separation is based on specified navigation aids or techniques. |
| SERA.8015(a)-(d) (`sera-923-2012-extracted/clearances_8015_a_d`) | 13 accepted | Fit | Covers clearance basis, controlled-flight clearance requirement, unsatisfactory clearance/amendment, priority report, potential reclearance in flight, controlled-aerodrome taxi clearance, transonic clearance boundaries, and all five clearance-content fields. |
| SERA.8015(e) (`sera-923-2012-extracted/readback_8015_e`) | 7 accepted | Fit | Covers ATC route clearances, runway clearances/instructions, grouped runway-in-use/altimeter/SSR/channel/level/heading/speed items, transition levels, other clearance acknowledgement, controller listen-and-correct duty, and CPDLC exception. |
| Slovenia VFR readback (`slovenia-vfr-extracted/readback`) | 15 accepted | Fit / possible low-value residual | Covers background rationale, required readback items, readback completeness/callsign, normal order, take-off-clearance-first exception, WILCO meaning and limits, ROGER/callsign acknowledgement, and transmitting technique. Residual: note-taking aid is not represented. |

## Overall Finding

The declared 22-section ingestion frame is fit for purpose as an actionable
rule/guidance registry after the RR-17 repair. The remaining uncovered juice is
bounded and explicit:

- low-value human technique notes in EGAST and Slovenia;
- illustrative ICAO Doc 9432 taxi dialogue examples;
- adjacent, high-value source sections that were not in the declared manifests.

The highest-value future expansion, if needed, is not another blind resample. It
is a deliberate manifest widening for ICAO Doc 4444 7.11.7, 5.8.5, and
7.6.3.1.2+.
