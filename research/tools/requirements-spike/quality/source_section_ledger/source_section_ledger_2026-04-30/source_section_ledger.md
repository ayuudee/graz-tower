# Source Section Ledger - 2026-04-30

## Scope

This ledger is the control surface for future source widening. It covers the local `research/txt/` corpus at table-of-contents / major-section granularity and includes exact manifest-window rows. As of this ledger, `documents/*.json` contains 46 exact windows: 30 have accepted registry records and 16 are manifest-only windows awaiting clean ingestion/promotion.

It is not paragraph-level atomization. A row with `extract` means the section should be considered for a future topic batch; it does not mean the section has already been translated.

## Summary

- Ledger rows: 481
- Exact manifest-window rows: 46
- Landed manifest-window rows: 30
- Manifest-only rows ready for Ollama ingestion: 16
- High-priority `extract` or `partially_extracted` rows: 83
- High-priority non-manifest rows needing exact source windows: 83

The disposition table counts all rows. The Ollama-ready count above is restricted to exact `manifest_window` rows, so it excludes high-level TOC rollups that also carry `manifest_only` disposition.

### By Disposition

| Disposition | Rows |
| --- | ---: |
| background_only | 13 |
| defer_with_reason | 100 |
| duplicate_subset | 3 |
| extract | 93 |
| extracted_current | 45 |
| manifest_only | 19 |
| out_of_scope | 13 |
| partially_extracted | 12 |
| support_only | 183 |

### By Document

| Document | Rows |
| --- | ---: |
| cap413-aerodrome-chapter | 1 |
| cap413-extracted | 49 |
| egast-vfr-extracted | 9 |
| h01-aerodrome-chapter | 1 |
| h01-extracted | 53 |
| icao4444-extracted | 146 |
| icao9432-aerodrome-chapter | 1 |
| icao9432-extracted | 94 |
| nolan-fundamentals-extracted | 13 |
| safetysense22-extracted | 30 |
| sera-923-2012-extracted | 71 |
| slovenia-vfr-extracted | 13 |

## High-Priority Future Extraction Rows

| Document | Section | Disposition | Rationale |
| --- | --- | --- | --- |
| cap413-extracted | CAP413 Ch2 - Radiotelephony general procedures | partially_extracted | Current registry covers readback requirements plus CAP 413 §§2.82-2.91; other communication procedures remain available. |
| cap413-extracted | CAP413 Ch2 Clearance Issue and Read-back Requirements - Clearance issue and read-back requirements | partially_extracted | Registry covers CAP 413 2.68-2.71 and exact windows exist for 2.65-2.67 and 2.72-2.75. |
| cap413-extracted | CAP413 Ch4 - Aerodrome phraseology | extract | High-value tower/taxi/takeoff/landing phraseology coverage not represented by current manifest. |
| cap413-extracted | CAP413 Ch8 - Emergency phraseology | extract | Emergency communications likely valuable for controller/pilot failure modes. |
| h01-extracted | H01 3.3 - Establishment of radiotelephony communications | extract | Austrian operational guidance; bilingual text needs English-side filtering before extraction. |
| h01-extracted | H01 3.8 - Exchange of communications | partially_extracted | Austrian operational guidance; bilingual text needs English-side filtering before extraction. |
| h01-extracted | H01 3.9 - Assurance of RTF communication/frequencies to be used | extract | Austrian operational guidance; bilingual text needs English-side filtering before extraction. |
| h01-extracted | H01 3.10 - Transfer of VHF communications | extract | Austrian operational guidance; bilingual text needs English-side filtering before extraction. |
| h01-extracted | H01 4 - Contingencies | extract | Austrian operational guidance; bilingual text needs English-side filtering before extraction. |
| h01-extracted | H01 4.1 - Distress and urgency communication procedures | extract | Austrian operational guidance; bilingual text needs English-side filtering before extraction. |
| h01-extracted | H01 4.4 - Voice communications failure | extract | Austrian operational guidance; bilingual text needs English-side filtering before extraction. |
| h01-extracted | H01 5.3 - Controlled aerodromes | extract | Austrian operational guidance; bilingual text needs English-side filtering before extraction. |
| h01-extracted | H01 5.4 - Aerodromes without air traffic control | extract | Austrian operational guidance; bilingual text needs English-side filtering before extraction. |
| h01-extracted | H01 5.6 - General flight handling phraseology | extract | Austrian operational guidance; bilingual text needs English-side filtering before extraction. |
| h01-extracted | H01 5.8 - Phraseologies in contingencies | extract | Austrian operational guidance; bilingual text needs English-side filtering before extraction. |
| icao4444-extracted | ICAO4444 4.1 - Responsibility for the provision of air traffic control service | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 4.2 - Responsibility for the provision of flight information service and alerting service | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 4.3 - Division of responsibility for control between air traffic control units | partially_extracted | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 4.5 - Air traffic control clearances | partially_extracted | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 4.6 - Horizontal speed control instructions | partially_extracted | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 4.7 - Vertical speed control instructions | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 4.9 - Wake turbulence categories | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 4.10 - Altimeter setting procedures | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 4.11 - Position reporting | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 4.14 - Failure or irregularity of systems and equipment | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 5.2 - Provisions for the separation of controlled traffic | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 5.3 - Vertical separation | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 5.4 - Horizontal separation | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 5.6 - Minimum separation between departing aircraft | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 5.7 - Separation of departing aircraft from arriving aircraft | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 5.8 - Time-based wake turbulence longitudinal separation minima | partially_extracted | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 5.10 - Essential traffic information | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 5.11 - Reduction in separation minima | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 6.1 - Reduction in separation minima in the vicinity of aerodromes | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 6.2 - Essential local traffic | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 6.3 - Procedures for departing aircraft | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 6.4 - Information for departing aircraft | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 6.5 - Procedures for arriving aircraft | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 6.6 - Information for arriving aircraft | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 6.7 - Operations on parallel or near-parallel runways | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.1 - Functions of aerodrome control towers | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.2 - Selection of runway-in-use | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.3 - Initial call to aerodrome control tower | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.4 - Information to aircraft by aerodrome control towers | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.5 - Essential information on aerodrome conditions | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.6 - Control of aerodrome traffic | partially_extracted | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.7 - Control of traffic in the traffic circuit | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.8 - Order of priority for arriving and departing aircraft | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.11 - Reduced runway separation minima between aircraft using the same runway | partially_extracted | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.12 - Use of a visual surveillance system in aerodrome control service | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.13 - Procedures for low visibility operations | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.14 - Suspension of visual flight rules operations | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.15 - Authorization of special VFR flights | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.16 - Aeronautical ground lights | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 7.17 - Designation of hot spots | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 12.1 - Communications procedures | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 12.2 - General phraseologies | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 12.3 - ATC phraseologies | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 15.1 - Emergency procedures | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 15.3 - Air-ground communications failure | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 15.4 - Assistance to VFR flights | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao4444-extracted | ICAO4444 15.6 - ATC contingencies | extract | Primary ICAO procedural source; extract by topic where relevant to tower/controller behaviour. |
| icao9432-extracted | ICAO9432 2.8 - Communications | partially_extracted | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| icao9432-extracted | ICAO9432 2.8.2 - Transfer of communications | extract | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| icao9432-extracted | ICAO9432 Ch4 - Aerodrome control: aircraft | extract | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| icao9432-extracted | ICAO9432 4.2 - Departure information and engine starting procedures | extract | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| icao9432-extracted | ICAO9432 4.3 - Push-back | extract | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| icao9432-extracted | ICAO9432 4.5 - Take-off procedures | extract | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| icao9432-extracted | ICAO9432 4.6 - Aerodrome traffic circuit | extract | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| icao9432-extracted | ICAO9432 4.7 - Final approach and landing | extract | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| icao9432-extracted | ICAO9432 4.8 - Go around | extract | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| icao9432-extracted | ICAO9432 4.9 - After landing | extract | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| icao9432-extracted | ICAO9432 4.10 - Essential aerodrome information | extract | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| icao9432-extracted | ICAO9432 Ch5 - Aerodrome control: vehicles | extract | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| icao9432-extracted | ICAO9432 Ch9 - Distress, urgency, and communications failure | extract | ICAO radiotelephony manual; bilingual extraction requires English-side filtering. |
| sera-923-2012-extracted | SERA.3225 - Operation on and in the vicinity of an aerodrome | extract | Binding SERA material relevant to ATC service, communications, FIS, aerodrome, or emergencies. |
| sera-923-2012-extracted | SERA.6005 - Requirements for communications and SSR transponder | extract | Binding SERA material relevant to ATC service, communications, FIS, aerodrome, or emergencies. |
| sera-923-2012-extracted | SERA.7001 - General — Objectives of the air traffic services | extract | Binding SERA material relevant to ATC service, communications, FIS, aerodrome, or emergencies. |
| sera-923-2012-extracted | SERA.7005 - Coordination between the aircraft operator and air traffic services | extract | Binding SERA material relevant to ATC service, communications, FIS, aerodrome, or emergencies. |
| sera-923-2012-extracted | SERA.9005 - Scope of flight information service | extract | Binding SERA material relevant to ATC service, communications, FIS, aerodrome, or emergencies. |
| sera-923-2012-extracted | SERA.9010 - Automatic terminal information service (ATIS) | extract | Binding SERA material relevant to ATC service, communications, FIS, aerodrome, or emergencies. |
| sera-923-2012-extracted | SERA.11005 - Service to aircraft in the event of an emergency | extract | Binding SERA material relevant to ATC service, communications, FIS, aerodrome, or emergencies. |
| sera-923-2012-extracted | SERA.12015 - Reporting of aircraft observations by voice communication | extract | Binding SERA material relevant to ATC service, communications, FIS, aerodrome, or emergencies. |

## Checks

1. Exact manifest-window rows are read from `documents/*.json`; their disposition is computed from whether accepted registry records exist for the same document/section.
2. SERA section rows are generated from the source text's `SERA.xxxx` headings before the appendices/differences material.
3. CAP 413, EGAST, H01, ICAO 4444, ICAO 9432, SafetySense, Slovenia, and Nolan rows are keyed from their table-of-contents / major-section structure.
4. The three aerodrome excerpt files are marked `duplicate_subset` rather than queued for separate extraction.

## Review Considerations

FP / type safety: no Kotlin/domain code changed.

Test architecture: this is an audit ledger. The check is deterministic regeneration plus row-id uniqueness and manifest cross-checks.

Impact: future widening can now be tracked by section disposition instead of vague document-level intent.

Operational correctness: no new ATC rule claim is made. Dispositions only rank source sections for future extraction/support/defer decisions.
