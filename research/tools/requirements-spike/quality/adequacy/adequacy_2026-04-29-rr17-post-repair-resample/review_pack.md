# Registry Adequacy Review Pack

- seed: `rr17-post-repair-resample-2026-04-29`
- record sample: `32`
- section omission sample: `8`

## How To Review

For each sampled record, compare `claimText` and `exactSourceQuotes` with the source. Use verdicts:

- `correct`
- `minor_metadata_issue`
- `major_error`
- `wrong_lifecycle`
- `needs_split_or_bundle`
- `cannot_assess`

For each sampled section, scan the source window and the section's accepted/rejected record summary. Use omission verdicts:

- `no_material_omission`
- `possible_omission`
- `material_omission`
- `cannot_assess`

Rule-of-three approximation: if 0 major record errors are found in 48 records, the 95% upper bound on major record-error rate is about 6.3% for the sampled frame. If 0 material omissions are found in 12 sections, the corresponding upper bound is about 25%; this is a smoke check for systemic omissions, not a proof of completeness.

## Sampled Records

### R001 `cap413-extracted::readback_2_68_2_71::5be7cfa8ac79626b`

- bucket: `candidates`
- document/section: `cap413-extracted/readback_2_68_2_71`
- authority/modality: `operational_guidance` / `none`
- kind/testability: `workflow_constraint` / `sim_executable`
- risk tags: `challenger_overridden;curated;dialogue_examples;executable_or_partial;judge_demoted`

**Claim**

If a readback is not received for a required message, the pilot/driver will be asked to do so.

**Exact Source Quotes**

```text
If a readback is not received the pilot/driver will be asked to do so
```

### R002 `egast-vfr-extracted::readback_advisory::2c0a6da5b0abc95f`

- bucket: `candidates`
- document/section: `egast-vfr-extracted/readback_advisory`
- authority/modality: `background_support` / `note`
- kind/testability: `background_support` / `review_only`
- risk tags: `challenger_overridden;curated;judge_demoted;local_or_best_practice;nested_conditions;support_only`

**Claim**

Reading back a clearance and any safety critical information helps both the pilot and the controller understand what the aircraft has been instructed to do.

**Exact Source Quotes**

```text
Reading back a clearance and any safety critical information helps both the pilot and the controller understand what the aircraft has been instructed to do.
```

### R003 `h01-extracted::acknowledgement_3_8_1::1ba408f68e9b501d`

- bucket: `candidates`
- document/section: `h01-extracted/acknowledgement_3_8_1`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `bilingual;challenger_overridden;curated;executable_or_partial;judge_demoted;nested_conditions;parent_child`

**Claim**

Clearances and instructions to enter, land on, take off from, hold short of, cross, taxi and backtrack on any runway shall always be read back.

**Exact Source Quotes**

```text
The following items shall always be read back:
```

```text
Clearances and instructions to enter, land on, take off from, hold short of, cross, taxi and backtrack on any runway
```

### R004 `h01-extracted::acknowledgement_3_8_1::647df1446645072b`

- bucket: `rejected`
- document/section: `h01-extracted/acknowledgement_3_8_1`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `partially_executable`
- risk tags: `bilingual;curated;curation_reject;executable_or_partial;judge_demoted;nested_conditions;non_accepted;parent_child`

**Claim**

The flight crew shall read back to the air traffic controller safety-related parts of ATC clearances and instructions which are transmitted by voice.

**Exact Source Quotes**

```text
The flight crew shall read back to the air traffic controller safety-related parts of ATC clearances and instructions which are transmitted by voice.
```

### R005 `h01-extracted::acknowledgement_3_8_1::d19604b475643777`

- bucket: `candidates`
- document/section: `h01-extracted/acknowledgement_3_8_1`
- authority/modality: `operational_guidance` / `may`
- kind/testability: `advisory_permission` / `review_only`
- risk tags: `bilingual;curated;judge_demoted;nested_conditions;parent_child`

**Claim**

It is permissible for verification for the receiving station to read back the message as an additional acknowledgement of receipt.

**Exact Source Quotes**

```text
It is permissible for verification for the receiving station to read back the message as an additional acknowledgement of receipt.
```

### R006 `h01-extracted::corrections_3_8_3::16272622eb9270ac`

- bucket: `candidates`
- document/section: `h01-extracted/corrections_3_8_3`
- authority/modality: `operational_guidance` / `should`
- kind/testability: `best_practice` / `review_only`
- risk tags: `bilingual;challenger_overridden;curated;judge_demoted`

**Claim**

Specific items should be requested, as appropriate, such as SAY AGAIN WIND.

**Exact Source Quotes**

```text
Specific items should be requested, as appropriate, such as “SAY AGAIN WIND”.
```

### R007 `h01-extracted::end_of_conversation_3_8_2::1d66565b4872e6a0`

- bucket: `candidates`
- document/section: `h01-extracted/end_of_conversation_3_8_2`
- authority/modality: `operational_guidance` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `bilingual;challenger_overridden;executable_or_partial`

**Claim**

A radiotelephone conversation shall be terminated by the receiving ATS unit or the aircraft using its own call sign.

**Exact Source Quotes**

```text
A radiotelephone conversation shall be terminated by the receiving ATS unit or the aircraft using its own call sign.
```

### R008 `icao4444-extracted::aerodrome_traffic_7_6::1ebd71be057d48f1`

- bucket: `rejected`
- document/section: `icao4444-extracted/aerodrome_traffic_7_6`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;curated;curation_reject;executable_or_partial;judge_demoted;non_accepted;table_or_figure`

**Claim**

Take-off clearance shall be issued at Position 3, if not practicable at Position 2.

**Exact Source Quotes**

```text
Position 3. Take-off clearance is issued here, if not practicable at position 2.
```

### R009 `icao4444-extracted::aerodrome_traffic_7_6::ea64ad7c56d8e4ef`

- bucket: `candidates`
- document/section: `icao4444-extracted/aerodrome_traffic_7_6`
- authority/modality: `best_practice` / `should`
- kind/testability: `best_practice` / `partially_executable`
- risk tags: `executable_or_partial;judge_overridden;table_or_figure`

**Claim**

Aircraft should be watched closely as they approach these positions so that proper clearances may be issued without delay.

**Exact Source Quotes**

```text
Aircraft should be watched closely as they approach these positions so that proper clearances may be issued without delay.
```

### R010 `icao4444-extracted::arriving_aircraft_7_10::b69123447be6f6b8`

- bucket: `rejected`
- document/section: `icao4444-extracted/arriving_aircraft_7_10`
- authority/modality: `background_support` / `note`
- kind/testability: `definition` / `review_only`
- risk tags: `curated;curation_reject;judge_demoted;nested_conditions;non_accepted;support_only`

**Claim**

See 7.6.3.1.2.2.

**Exact Source Quotes**

```text
Note 3.— See 7.6.3.1.2.2.
```

### R011 `icao4444-extracted::arriving_aircraft_7_10::b74da3c290fac10d`

- bucket: `candidates`
- document/section: `icao4444-extracted/arriving_aircraft_7_10`
- authority/modality: `background_support` / `note`
- kind/testability: `definition` / `review_only`
- risk tags: `curated;judge_demoted;nested_conditions;support_only`

**Claim**

Wake turbulence categories of aircraft and longitudinal separation minima are contained in Chapter 4, Section 4.9 and Chapter 5, Section 5.8, respectively.

**Exact Source Quotes**

```text
Note 2.— Wake turbulence categories of aircraft and longitudinal separation minima are contained in Chapter 4, Section 4.9 and Chapter 5, Section 5.8, respectively.
```

### R012 `icao4444-extracted::departing_aircraft_7_9::6ed1fb7ae6420569`

- bucket: `candidates`
- document/section: `icao4444-extracted/departing_aircraft_7_9`
- authority/modality: `background_support` / `note`
- kind/testability: `background_support` / `review_only`
- risk tags: `curated;nested_conditions;support_only;table_or_figure`

**Claim**

For aircraft subject to ATFM requirements, it is the responsibility of the pilot and the operator to ensure that the aircraft is ready to taxi in time to meet any required departure time.

**Exact Source Quotes**

```text
Note 2.— For aircraft subject to ATFM requirements, it is the responsibility of the pilot and the operator to ensure that the aircraft is ready to taxi in time to meet any required departure time, bearing in mind that once a departure sequence is established on the taxiway system, it can be difficult, and sometimes impossible, to change the order.
```

### R013 `icao4444-extracted::readback_4_5_7_5::1cc502e99b33ace6`

- bucket: `rejected`
- document/section: `icao4444-extracted/readback_4_5_7_5`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;curation_reject;executable_or_partial;judge_demoted;nested_conditions;non_accepted;parent_child`

**Claim**

The flight crew shall read back to the air traffic controller safety-related parts of ATC clearances and instructions which are transmitted by voice.

**Exact Source Quotes**

```text
The flight crew shall read back to the air traffic controller safety-related parts of ATC clearances and instructions which are transmitted by voice.
```

### R014 `icao4444-extracted::readback_4_5_7_5::80a171af98372544`

- bucket: `candidates`
- document/section: `icao4444-extracted/readback_4_5_7_5`
- authority/modality: `background_support` / `note`
- kind/testability: `definition` / `review_only`
- risk tags: `curated;judge_demoted;nested_conditions;parent_child;support_only`

**Claim**

The procedures and provisions relating to the exchange and acknowledgement of CPDLC messages are contained in Annex 10, Volume II and the PANS-ATM, Chapter 14.

**Exact Source Quotes**

```text
Note.— The procedures and provisions relating to the exchange and acknowledgement of CPDLC messages are contained in Annex 10, Volume II and the PANS-ATM, Chapter 14.
```

### R015 `icao4444-extracted::reduced_runway_7_11::178b6b27f3c6c039`

- bucket: `candidates`
- document/section: `icao4444-extracted/reduced_runway_7_11`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;executable_or_partial;nested_conditions`

**Claim**

The tailwind component shall not exceed 5 kt when applying reduced runway separation minima.

**Exact Source Quotes**

```text
Reduced runway separation minima shall be subject to the following conditions:
```

```text
tailwind component shall not exceed 5 kt;
```

### R016 `icao4444-extracted::speed_control_4_6_1::5b64dff0f7a93bf6`

- bucket: `candidates`
- document/section: `icao4444-extracted/speed_control_4_6_1`
- authority/modality: `operational_guidance` / `should`
- kind/testability: `phraseology_rule` / `partially_executable`
- risk tags: `challenger_overridden;curated;executable_or_partial;judge_demoted`

**Claim**

At levels at or above 7 600 m (FL 250), speed adjustments should be expressed in multiples of 0.01 Mach.

**Exact Source Quotes**

```text
At levels at or above 7 600 m (FL 250), speed adjustments should be expressed in multiples of 0.01 Mach
```

### R017 `icao4444-extracted::speed_control_4_6_1::8357e703f52d70a6`

- bucket: `candidates`
- document/section: `icao4444-extracted/speed_control_4_6_1`
- authority/modality: `background_support` / `note`
- kind/testability: `performance_limitation_note` / `review_only`
- risk tags: `curated;judge_demoted;support_only`

**Claim**

When an aircraft is heavily loaded and at a high level, its ability to change speed may, in cases, be very limited.

**Exact Source Quotes**

```text
When an aircraft is heavily loaded and at a high level, its ability to change speed may, in cases, be very limited.
```

### R018 `icao4444-extracted::transfer_4_3_2_1::86dd6fa9b1c3d453`

- bucket: `candidates`
- document/section: `icao4444-extracted/transfer_4_3_2_1`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `workflow_constraint` / `partially_executable`
- risk tags: `curated;executable_or_partial;manual_split;nested_conditions;parent_child`

**Claim**

Control of an arriving aircraft shall be transferred from the unit providing approach control service to the unit providing aerodrome control service when the aircraft satisfies one of the conditions listed in ICAO Doc 4444 section 4.3.2.1.1, as specified in letters of agreement or ATS unit instructions.

**Exact Source Quotes**

```text
4.3.2.1.1 Arriving aircraft. Control of an arriving aircraft shall be transferred from the unit providing approach
control service to the unit providing aerodrome control service when the aircraft:
a)

is in the vicinity of the aerodrome, and
1) it is considered that approach and landing will be completed in visual reference to the ground, or
2) has reached uninterrupted visual meteorological conditions, or

b) is at a prescribed point or level, or
c)

has landed,

as specified in letters of agreement or ATS unit instructions.
```

### R019 `icao4444-extracted::transfer_4_3_2_1::98068c7af46e0390`

- bucket: `rejected`
- document/section: `icao4444-extracted/transfer_4_3_2_1`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `partially_executable`
- risk tags: `curated;curation_reject;executable_or_partial;judge_demoted;nested_conditions;non_accepted;parent_child;quote_audit_fail`

**Claim**

Control of a departing aircraft shall be transferred from the unit providing aerodrome control service to the unit providing approach control service when instrument meteorological conditions prevail at the aerodrome, as specified in letters of agreement or local instructions.

**Exact Source Quotes**

```text
Control of a departing aircraft shall be transferred from the unit providing aerodrome control service to the unit providing approach control service: b) when instrument meteorological conditions prevail at the aerodrome... as specified in letters of agreement or local instructions.
```

### R020 `icao4444-extracted::transfer_4_3_2_1::f0cb8347ee5a5ea9`

- bucket: `candidates`
- document/section: `icao4444-extracted/transfer_4_3_2_1`
- authority/modality: `background_support` / `note`
- kind/testability: `operational_exception` / `review_only`
- risk tags: `curated;judge_demoted;nested_conditions;parent_child;support_only`

**Claim**

Control of certain flights may be transferred directly from an ACC to an aerodrome control tower and vice versa, by prior arrangement between the units concerned for the relevant part of approach control service to be provided by the ACC or the aerodrome control tower, as applicable.

**Exact Source Quotes**

```text
Even though there is an approach control unit, control of certain flights may be transferred directly from an ACC to an aerodrome control tower and vice versa, by prior arrangement between the units concerned for the relevant part of approach control service to be provided by the ACC or the aerodrome control tower, as applicable.
```

### R021 `icao4444-extracted::wake_turbulence_5_8::d196eeff6310db45`

- bucket: `candidates`
- document/section: `icao4444-extracted/wake_turbulence_5_8`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;executable_or_partial;table_or_figure`

**Claim**

A separation minimum of 3 minutes shall be applied between a LIGHT or MEDIUM aircraft when taking off behind a HEAVY aircraft or a LIGHT aircraft when taking off behind a MEDIUM aircraft from an intermediate part of a parallel runway separated by less than 760 m (2 500 ft).

**Exact Source Quotes**

```text
A separation minimum of 3 minutes shall be applied between a LIGHT or MEDIUM aircraft when taking off behind a HEAVY aircraft or a LIGHT aircraft when taking off behind a MEDIUM aircraft from:
```

```text
b) an intermediate part of a parallel runway separated by less than 760 m (2 500 ft).
```

### R022 `icao9432-extracted::communications_2_8_1_en::0a964f42b6100596`

- bucket: `candidates`
- document/section: `icao9432-extracted/communications_2_8_1_en`
- authority/modality: `operational_guidance` / `shall`
- kind/testability: `rule` / `review_only`
- risk tags: `challenger_overridden;curated;judge_demoted`

**Claim**

If there is doubt that a message has been correctly received, a repetition of the messages shall be requested either in full or in part.

**Exact Source Quotes**

```text
If there is doubt that a message has been correctly received, a repetition of the messages shall be requested either in full or in part.
```

### R023 `icao9432-extracted::taxi_4_4_en::03985c8e2cf3f473`

- bucket: `candidates`
- document/section: `icao9432-extracted/taxi_4_4_en`
- authority/modality: `operational_guidance` / `may`
- kind/testability: `rule` / `partially_executable`
- risk tags: `curated;dialogue_examples;executable_or_partial;judge_demoted`

**Claim**

The clearance limit for departing aircraft may be any other position on the aerodrome depending on prevailing traffic circumstances.

**Exact Source Quotes**

```text
but it may be any other position on the aerodrome depending on the prevailing traffic circumstances
```

### R024 `icao9432-extracted::taxi_4_4_en::453f27d271407f98`

- bucket: `rejected`
- document/section: `icao9432-extracted/taxi_4_4_en`
- authority/modality: `operational_guidance` / `shall`
- kind/testability: `workflow_constraint` / `partially_executable`
- risk tags: `curated;dialogue_examples;executable_or_partial;judge_demoted;non_accepted`

**Claim**

After completion of pushback, ground staff shall provide a visual signal to the pilot indicating no objections to commencing taxiing.

**Exact Source Quotes**

```text
Po zakończeniu wypychania obsługa naziemna podaje pilotowi sygnał wzrokowy informujący, że nie ma przeciwwskazań do rozpoczęcia kołowania
```

### R025 `icao9432-extracted::taxi_4_4_en::9b3186528c0a6e32`

- bucket: `rejected`
- document/section: `icao9432-extracted/taxi_4_4_en`
- authority/modality: `operational_guidance` / `should`
- kind/testability: `phraseology_rule` / `review_only`
- risk tags: `curated;dialogue_examples;judge_demoted;non_accepted`

**Claim**

If the pilot intends to interrupt the pushback maneuver, they should use the expression 'przerwij wypychanie'.

**Exact Source Quotes**

```text
Jeżeli pilot zamierza przerwać manewr wypychania, powinien użyć wyrażenia: „przerwij wypychanie”
```

### R026 `safetysense22-extracted::readbacks::0dfd75aad80a651f`

- bucket: `candidates`
- document/section: `safetysense22-extracted/readbacks`
- authority/modality: `best_practice` / `must`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;executable_or_partial;local_or_best_practice;nested_conditions`

**Claim**

Clearance to enter, backtrack, cross, or hold short of an active runway must be read back in their entirety.

**Exact Source Quotes**

```text
The following items must be read back in their entirety if addressed to you in a transmission from an ATSU:
```

```text
9. Clearance to enter, backtrack, cross, or hold short of an active runway
```

### R027 `safetysense22-extracted::readbacks::9b6c62502e773767`

- bucket: `rejected`
- document/section: `safetysense22-extracted/readbacks`
- authority/modality: `best_practice` / `must`
- kind/testability: `rule` / `partially_executable`
- risk tags: `curated;curation_reject;executable_or_partial;judge_demoted;local_or_best_practice;nested_conditions;non_accepted`

**Claim**

The following items must be read back in their entirety if addressed to you in a transmission from an ATSU:

**Exact Source Quotes**

```text
The following items must be read back in their entirety if addressed to you in a transmission from an ATSU:
```

### R028 `sera-923-2012-extracted::atc_service_8005::dd09fb0eb790d595`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/atc_service_8005`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;curated;executable_or_partial;judge_demoted;nested_conditions`

**Claim**

Separation by an air traffic control unit shall be obtained by horizontal separation, specifically longitudinal separation, by maintaining an interval between aircraft operating along the same, converging or reciprocal tracks, expressed in time or distance.

**Exact Source Quotes**

```text
(c) Except for cases when a reduction in separation minima in the vicinity of aerodromes can be applied, separation by an air traffic control unit shall be obtained by at least one of the following:
```

```text
(2) horizontal separation, obtained by providing:
```

```text
(i) longitudinal separation, by maintaining an interval between aircraft operating along the same, converging or reciprocal tracks, expressed in time or distance; or
```

### R029 `sera-923-2012-extracted::clearances_8015_a_d::9285a92b423c68d6`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/clearances_8015_a_d`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;executable_or_partial;nested_conditions`

**Claim**

An air traffic control clearance shall indicate aircraft identification as shown in the flight plan.

**Exact Source Quotes**

```text
An air traffic control clearance shall indicate:
```

```text
(1) aircraft identification as shown in the flight plan;
```

### R030 `sera-923-2012-extracted::readback_8015_e::c86e7463f898e278`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/readback_8015_e`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;executable_or_partial;nested_conditions`

**Claim**

ATC route clearances shall always be read back.

**Exact Source Quotes**

```text
(1) The flight crew shall read back to the air traffic controller safety-related parts of ATC clearances and instructions which are transmitted by voice. The following items shall always be read back:
```

```text
(i) ATC route clearances;
```

### R031 `sera-923-2012-extracted::separation_minima_8010::14871d3b19e91d53`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/separation_minima_8010`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `deterministic_test`
- risk tags: `curated;judge_demoted`

**Claim**

Details of the selected separation minima and of their areas of application shall be notified to pilots and aircraft operators through aeronautical information publications, where separation is based on the use by aircraft of specified navigation aids or specified navigation techniques.

**Exact Source Quotes**

```text
(c) Details of the selected separation minima and of their areas of application shall be notified:
```

```text
(2) to pilots and aircraft operators through aeronautical information publications, where separation is based on the use by aircraft of specified navigation aids or specified navigation techniques.
```

### R032 `slovenia-vfr-extracted::readback::a9ad75de93e0b18a`

- bucket: `candidates`
- document/section: `slovenia-vfr-extracted/readback`
- authority/modality: `background_support` / `note`
- kind/testability: `definition` / `review_only`
- risk tags: `curated;local_or_best_practice;nested_conditions;support_only`

**Claim**

′WILCO′ means ′I understand your message and will comply with it′.

**Exact Source Quotes**

```text
′WILCO′ means ′I understand your message and will comply with it′.
```

## Sampled Source Sections

### S001 `cap413-extracted/readback_2_68_2_71`

- source: `research/txt/cap413-extracted.txt:6429-6555`
- records in section: `8`
- risk tags: `dialogue_examples`
- source window file: `source_windows/S001_cap413-extracted__readback_2_68_2_71.txt`

§2.68-2.71 Read-back Requirements (narrowed scope; the §2.65-2.67 introductory clauses and §2.72 UNABLE handling are out of scope for the readback-family proving slice). Numbered subsections with embedded RTF dialogue examples (BIGJET 347, G-ABCD, G-CD).

### S002 `egast-vfr-extracted/readback_advisory`

- source: `research/txt/egast-vfr-extracted.txt:539-608`
- records in section: `17`
- risk tags: `local_or_best_practice;nested_conditions`
- source window file: `source_windows/S002_egast-vfr-extracted__readback_advisory.txt`

Readback advisory family. Mixed best-practice prose, bullet list of items requiring readback, acknowledgement-by-callsign rule, Wilco usage, and read-back-when-required guidance, with inline page-layout noise. No clause numbering.

### S003 `h01-extracted/acknowledgement_3_8_1`

- source: `research/txt/h01-extracted.txt:4197-4337`
- records in section: `12`
- risk tags: `bilingual;nested_conditions;parent_child`
- source window file: `source_windows/S003_h01-extracted__acknowledgement_3_8_1.txt`

§3.8.1 ACKNOWLEDGEMENT OF RECEIPT, items a)-h). Bilingual German/English: each lettered item appears twice (German first, English second) with the same letter marker. Nested numbered list 1-4 (the items always to be read back) is laid out separately from its parent clause c) due to two-column extraction.

### S004 `icao4444-extracted/departing_aircraft_7_9`

- source: `research/txt/icao4444-extracted.txt:9031-9110`
- records in section: `19`
- risk tags: `nested_conditions;table_or_figure`
- source window file: `source_windows/S004_icao4444-extracted__departing_aircraft_7_9.txt`

§7.9 Control of Departing Aircraft: §7.9.1 Departure sequence (with priority factors a-f), §7.9.2 Separation of departing aircraft (the 'crossed end of runway / started a turn' rule), §7.9.3.1-7.9.3.5 Take-off clearance (timing, ATC clearance prerequisite, TAKE-OFF phraseology rule, 'immediate take-off' clearance). Includes inline Note text and figure-layout artifacts.

### S005 `icao9432-extracted/taxi_4_4_en`

- source: `research/txt/icao9432-extracted.txt:4917-5139`
- records in section: `9`
- risk tags: `dialogue_examples`
- source window file: `source_windows/S005_icao9432-extracted__taxi_4_4_en.txt`

§4.4 Taxi Instructions, English portion. Comprehensive operational phraseology section: taxi-clearance content, runway-crossing, hold-short instructions, aircraft-stand procedures, with extensive RTF dialogue examples interleaved with rule prose.

### S006 `safetysense22-extracted/readbacks`

- source: `research/txt/safetysense22-extracted.txt:485-552`
- records in section: `16`
- risk tags: `local_or_best_practice;nested_conditions`
- source window file: `source_windows/S006_safetysense22-extracted__readbacks.txt`

Readbacks section. Best-practice prose with a numbered list (1-15) of items requiring readback, each item laid out across two lines (number on its own line, item text following) due to two-column extraction.

### S007 `sera-923-2012-extracted/atc_service_8005`

- source: `research/txt/sera-923-2012-extracted.txt:1934-1973`
- records in section: `14`
- risk tags: `nested_conditions`
- source window file: `source_windows/S007_sera-923-2012-extracted__atc_service_8005.txt`

SERA.8005 Operation of air traffic control service. Three top-level paragraphs (a/b/c): (a) what an ATC unit shall do (4 numbered duties); (b) which classes of flight separation must be provided between (5 numbered cases plus exception); (c) separation methods (vertical / horizontal with sub-cases). Clean clause hierarchy.

### S008 `slovenia-vfr-extracted/readback`

- source: `research/txt/slovenia-vfr-extracted.txt:524-565`
- records in section: `15`
- risk tags: `local_or_best_practice;nested_conditions`
- source window file: `source_windows/S008_slovenia-vfr-extracted__readback.txt`

Read-back / Items / Wilco / Acknowledgement-by-call-sign / Transmitting-technique sub-sections. Best-practice prose with bullet list of items requiring readback, mostly mirroring CAP 413 / EGAST shape.

