# Registry Adequacy Review Pack

- seed: `rr13-80-20-2026-04-29`
- record sample: `48`
- section omission sample: `12`

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

### R002 `egast-vfr-extracted::readback_advisory::2a5d4629a99284e3`

- bucket: `candidates`
- document/section: `egast-vfr-extracted/readback_advisory`
- authority/modality: `best_practice` / `must`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;curated;executable_or_partial;judge_demoted;local_or_best_practice;nested_conditions`

**Claim**

If in any doubt you must request clarification.

**Exact Source Quotes**

```text
If in any doubt you must request clarification.
```

### R003 `egast-vfr-extracted::readback_advisory::2c0a6da5b0abc95f`

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

### R004 `egast-vfr-extracted::readback_advisory::60929bb516ae7098`

- bucket: `candidates`
- document/section: `egast-vfr-extracted/readback_advisory`
- authority/modality: `best_practice` / `should`
- kind/testability: `best_practice` / `partially_executable`
- risk tags: `challenger_overridden;curated;executable_or_partial;judge_demoted;local_or_best_practice;nested_conditions`

**Claim**

It is normally best to read back the items in the order given.

**Exact Source Quotes**

```text
It is normally best to read back the items in the order given
```

### R005 `h01-extracted::acknowledgement_3_8_1::11f3df660516dfe3`

- bucket: `candidates`
- document/section: `h01-extracted/acknowledgement_3_8_1`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `bilingual;challenger_overridden;executable_or_partial;nested_conditions;parent_child`

**Claim**

Voice read-back of CPDLC messages shall not be required.

**Exact Source Quotes**

```text
Voice read-back of CPDLC messages shall not be required.
```

### R006 `h01-extracted::acknowledgement_3_8_1::1ba408f68e9b501d`

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

### R007 `h01-extracted::acknowledgement_3_8_1::299a154acafe87cc`

- bucket: `candidates`
- document/section: `h01-extracted/acknowledgement_3_8_1`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `bilingual;challenger_overridden;executable_or_partial;nested_conditions;parent_child`

**Claim**

Transition levels shall always be read back, whether issued by the controller or contained in ATIS broadcasts.

**Exact Source Quotes**

```text
The following items shall always be read back:
```

```text
Transition levels, whether issued by the controller or contained in ATIS broadcasts.
```

### R008 `h01-extracted::acknowledgement_3_8_1::6281074482768bcc`

- bucket: `candidates`
- document/section: `h01-extracted/acknowledgement_3_8_1`
- authority/modality: `operational_guidance` / `should`
- kind/testability: `phraseology_rule` / `partially_executable`
- risk tags: `bilingual;curated;executable_or_partial;judge_demoted;nested_conditions;parent_child`

**Claim**

If both instructions subject to read-back and other information – such as weather reports – are received in the same message, the information should be acknowledged with the words such as WEATHER RECEIVED after the instruction has been read back.

**Exact Source Quotes**

```text
If both instructions subject to read-back and other information – such as weather reports – are received in the same message, the information should be acknowledged with the words such as WEATHER RECEIVED after the instruction has been read back.
```

### R009 `h01-extracted::acknowledgement_3_8_1::647df1446645072b`

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

### R010 `h01-extracted::acknowledgement_3_8_1::d19604b475643777`

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

### R011 `h01-extracted::acknowledgement_3_8_1::fb4e915bd2eabca3`

- bucket: `candidates`
- document/section: `h01-extracted/acknowledgement_3_8_1`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `bilingual;curated;executable_or_partial;judge_demoted;nested_conditions;parent_child`

**Claim**

Runway-in-use, altimeter settings, SSR codes, newly assigned communication channels, level instructions, heading and speed instructions shall always be read back.

**Exact Source Quotes**

```text
The following items shall always be read back:
```

```text
Runway-in-use, altimeter settings, SSR codes, newly assigned communication channels, level instructions, heading and speed instructions
```

### R012 `h01-extracted::corrections_3_8_3::16272622eb9270ac`

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

### R013 `h01-extracted::end_of_conversation_3_8_2::1d66565b4872e6a0`

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

### R014 `icao4444-extracted::aerodrome_traffic_7_6::ea64ad7c56d8e4ef`

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

### R015 `icao4444-extracted::aerodrome_traffic_7_6::f11471c05d5603f6`

- bucket: `rejected`
- document/section: `icao4444-extracted/aerodrome_traffic_7_6`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;curated;curation_reject;executable_or_partial;judge_demoted;non_accepted;table_or_figure`

**Claim**

Clearance to land shall be issued at Position 4 as practicable.

**Exact Source Quotes**

```text
Position 4. Clearance to land is issued here as practicable.
```

### R016 `icao4444-extracted::arriving_aircraft_7_10::b69123447be6f6b8`

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

### R017 `icao4444-extracted::arriving_aircraft_7_10::dcc6bc5c93d748ec`

- bucket: `candidates`
- document/section: `icao4444-extracted/arriving_aircraft_7_10`
- authority/modality: `background_support` / `note`
- kind/testability: `definition` / `review_only`
- risk tags: `curated;judge_demoted;nested_conditions;support_only`

**Claim**

See Figure 7-2.

**Exact Source Quotes**

```text
Note 1.— See Figure 7-2.
```

### R018 `icao4444-extracted::departing_aircraft_7_9::2732dc9b2e895827`

- bucket: `rejected`
- document/section: `icao4444-extracted/departing_aircraft_7_9`
- authority/modality: `authoritative_requirement` / `should`
- kind/testability: `rule` / `partially_executable`
- risk tags: `curated;curation_reject;executable_or_partial;judge_demoted;nested_conditions;non_accepted;quote_audit_fail;table_or_figure`

**Claim**

Factors to be considered in relation to the departure sequence include aircraft subject to ATFM requirements.

**Exact Source Quotes**

```text
Factors which should be considered in relation to the departure sequence include, inter alia: f) aircraft subject to ATFM requirements.
```

### R019 `icao4444-extracted::departing_aircraft_7_9::55c3a91070f9adbb`

- bucket: `candidates`
- document/section: `icao4444-extracted/departing_aircraft_7_9`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `partially_executable`
- risk tags: `challenger_overridden;executable_or_partial;judge_overridden;nested_conditions;table_or_figure`

**Claim**

For aircraft subject to ATFM requirements, it is the responsibility of the pilot and the operator to ensure that the aircraft is ready to taxi in time to meet any required departure time.

**Exact Source Quotes**

```text
For aircraft subject to ATFM requirements, it is the responsibility of the pilot and the operator to ensure that the aircraft is ready to taxi in time to meet any required departure time
```

### R020 `icao4444-extracted::departing_aircraft_7_9::abacaff9ac399930`

- bucket: `candidates`
- document/section: `icao4444-extracted/departing_aircraft_7_9`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;curated;executable_or_partial;judge_demoted;nested_conditions;table_or_figure`

**Claim**

A departing aircraft will not normally be permitted to commence take-off until all preceding landing aircraft are clear of the runway-in-use.

**Exact Source Quotes**

```text
or until all preceding landing aircraft are clear of the runway-in-use
```

### R021 `icao4444-extracted::readback_4_5_7_5::1cc502e99b33ace6`

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

### R022 `icao4444-extracted::readback_4_5_7_5::80a171af98372544`

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

### R023 `icao4444-extracted::reduced_runway_7_11::6b0262350c8ef4b3`

- bucket: `candidates`
- document/section: `icao4444-extracted/reduced_runway_7_11`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;executable_or_partial;nested_conditions`

**Claim**

Minimum separation continues to exist between two departing aircraft immediately after take-off of the second aircraft when applying reduced runway separation minima.

**Exact Source Quotes**

```text
minimum separation continues to exist between two departing aircraft immediately after take-off of the second aircraft;
```

### R024 `icao4444-extracted::speed_control_4_6_1::1217a29793be2295`

- bucket: `candidates`
- document/section: `icao4444-extracted/speed_control_4_6_1`
- authority/modality: `operational_guidance` / `note`
- kind/testability: `advisory_note` / `review_only`
- risk tags: `curated;judge_demoted`

**Claim**

Cancellation of any speed control instruction does not relieve the flight crew of compliance with speed limitations associated with airspace classifications as specified in Annex 11.

**Exact Source Quotes**

```text
Cancellation of any speed control instruction does not relieve the flight crew of compliance with speed limitations associated with airspace classifications as specified in Annex 11 — Air Traffic Services, Appendix 4.
```

### R025 `icao4444-extracted::speed_control_4_6_1::8357e703f52d70a6`

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

### R026 `icao4444-extracted::speed_control_4_6_1::e9f810be22ca5add`

- bucket: `candidates`
- document/section: `icao4444-extracted/speed_control_4_6_1`
- authority/modality: `background_support` / `note`
- kind/testability: `cross_reference` / `review_only`
- risk tags: `curated;judge_demoted;support_only`

**Claim**

Provisions concerning longitudinal separation using the Mach number technique are contained in Chapter 5, Separation Methods and Minima.

**Exact Source Quotes**

```text
Provisions concerning longitudinal separation using the Mach number technique are contained in Chapter 5, Separation Methods and Minima.
```

### R027 `icao4444-extracted::transfer_4_3_2_1::2670284e94e0eeb0`

- bucket: `rejected`
- document/section: `icao4444-extracted/transfer_4_3_2_1`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `partially_executable`
- risk tags: `challenger_overridden;curated;curation_reject;executable_or_partial;nested_conditions;non_accepted;parent_child;quote_audit_fail`

**Claim**

Control of an arriving aircraft shall be transferred from the unit providing approach control service to the unit providing aerodrome control service when the aircraft is at a prescribed point or level, as specified in letters of agreement or ATS unit instructions.

**Exact Source Quotes**

```text
Control of an arriving aircraft shall be transferred from the unit providing approach control service to the unit providing aerodrome control service when the aircraft: b) is at a prescribed point or level... as specified in letters of agreement or ATS unit instructions.
```

### R028 `icao4444-extracted::transfer_4_3_2_1::6524e899c0a46912`

- bucket: `candidates`
- document/section: `icao4444-extracted/transfer_4_3_2_1`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `workflow_constraint` / `partially_executable`
- risk tags: `curated;executable_or_partial;manual_split;nested_conditions;parent_child`

**Claim**

Control of a departing aircraft shall be transferred from the unit providing aerodrome control service to the unit providing approach control service under the VMC or IMC timing conditions listed in ICAO Doc 4444 section 4.3.2.1.3, as specified in letters of agreement or local instructions.

**Exact Source Quotes**

```text
4.3.2.1.3 Departing aircraft. Control of a departing aircraft shall be transferred from the unit providing aerodrome
control service to the unit providing approach control service:
a)

when visual meteorological conditions prevail in the vicinity of the aerodrome:
1) prior to the time the aircraft leaves the vicinity of the aerodrome,
2) prior to the aircraft entering instrument meteorological conditions, or
3) when the aircraft is at a prescribed point or level,

as specified in letters of agreement or ATS unit instructions;
```

```text
b) when instrument meteorological conditions prevail at the aerodrome:
1) immediately after the aircraft is airborne, or
2) when the aircraft is at a prescribed point or level,
as specified in letters of agreement or local instructions.
```

### R029 `icao4444-extracted::transfer_4_3_2_1::f0cb8347ee5a5ea9`

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

### R030 `icao4444-extracted::wake_turbulence_5_8::ad5ab8aad62283e3`

- bucket: `rejected`
- document/section: `icao4444-extracted/wake_turbulence_5_8`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;curation_reject;executable_or_partial;judge_demoted;non_accepted;quote_audit_fail;table_or_figure`

**Claim**

A separation minimum of 2 minutes shall be applied between a LIGHT or MEDIUM aircraft and a HEAVY aircraft and between a LIGHT aircraft and a MEDIUM aircraft when operating on a runway with a displaced landing threshold when an arriving LIGHT or MEDIUM aircraft follows a HEAVY aircraft departure and an arriving LIGHT aircraft follows a MEDIUM aircraft departure if the projected flight paths are expected to cross.

**Exact Source Quotes**

```text
A separation minimum of 2 minutes shall be applied between a LIGHT or MEDIUM aircraft and a HEAVY aircraft and between a LIGHT aircraft and a MEDIUM aircraft when operating on a runway with a displaced landing threshold when: an arriving LIGHT or MEDIUM aircraft follows a HEAVY aircraft departure and an arriving LIGHT aircraft follows a MEDIUM aircraft departure if the projected flight paths are expected to cross.
```

### R031 `icao4444-extracted::wake_turbulence_5_8::eb48ff5054e3f496`

- bucket: `candidates`
- document/section: `icao4444-extracted/wake_turbulence_5_8`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;executable_or_partial;table_or_figure`

**Claim**

A separation minimum of 3 minutes shall be applied to a LIGHT aircraft landing behind a HEAVY or MEDIUM aircraft.

**Exact Source Quotes**

```text
LIGHT aircraft behind a HEAVY or MEDIUM aircraft — 3 minutes.
```

### R032 `icao9432-extracted::communications_2_8_1_en::0a964f42b6100596`

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

### R033 `icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2`

- bucket: `candidates`
- document/section: `icao9432-extracted/readback_2_8_3_en`
- authority/modality: `operational_guidance` / `should`
- kind/testability: `best_practice` / `review_only`
- risk tags: `curated;judge_demoted`

**Claim**

Controllers should pass a clearance slowly and clearly, avoid passing clearances during complicated taxiing, and on no occasion should a clearance be passed when the pilot is engaged in line up or take-off manoeuvres.

**Exact Source Quotes**

```text
Controllers should pass a clearance slowly and clearly
```

```text
controllers should avoid passing a clearance to a pilot engaged in complicated taxiing manoeuvres
```

```text
on no occasion should a clearance be passed when the pilot is engaged in line up or take-off manoeuvres.
```

### R034 `icao9432-extracted::taxi_4_4_en::417f64324f7495bf`

- bucket: `candidates`
- document/section: `icao9432-extracted/taxi_4_4_en`
- authority/modality: `operational_guidance` / `none`
- kind/testability: `best_practice` / `partially_executable`
- risk tags: `challenger_overridden;curated;dialogue_examples;executable_or_partial;judge_demoted`

**Claim**

For departing aircraft, the clearance limit will normally be the taxi-holding point of the runway in use.

**Exact Source Quotes**

```text
For departing aircraft, the clearance limit will normally be the taxi-holding point of the runway in use
```

### R035 `icao9432-extracted::taxi_4_4_en::eadf2541fcd51825`

- bucket: `candidates`
- document/section: `icao9432-extracted/taxi_4_4_en`
- authority/modality: `background_support` / `note`
- kind/testability: `definition` / `review_only`
- risk tags: `curated;dialogue_examples;judge_demoted;support_only`

**Claim**

The runway is considered vacated when the entire aircraft is beyond the relevant runway-holding position.

**Exact Source Quotes**

```text
The runway is vacated when the entire aircraft is beyond the relevant runway-holding position
```

### R036 `safetysense22-extracted::readbacks::127ec87d0fe63b0a`

- bucket: `candidates`
- document/section: `safetysense22-extracted/readbacks`
- authority/modality: `best_practice` / `must`
- kind/testability: `rule` / `sim_executable`
- risk tags: `executable_or_partial;local_or_best_practice;nested_conditions`

**Claim**

Taxi/towing instructions must be read back in their entirety.

**Exact Source Quotes**

```text
1. Taxi/towing instructions
```

### R037 `safetysense22-extracted::readbacks::9b6c62502e773767`

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

### R038 `sera-923-2012-extracted::atc_service_8005::2f46ccb194a81cff`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/atc_service_8005`
- authority/modality: `operational_guidance` / `may`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;executable_or_partial;nested_conditions`

**Claim**

A flight may be cleared subject to maintaining own separation in respect of a specific portion of the flight below 3 050 m (10 000 ft) during climb or descent, during day in visual meteorological conditions, when requested by the pilot of an aircraft and agreed by the pilot of the other aircraft and if so prescribed by the competent authority for the cases listed under b) above in airspace Classes D and E.

**Exact Source Quotes**

```text
except that, when requested by the pilot of an aircraft and agreed by the pilot of the other aircraft and if so prescribed by the competent authority for the cases listed under b) above in airspace Classes D and E, a flight may be cleared subject to maintaining own separation in respect of a specific portion of the flight below 3 050 m (10 000 ft) during climb or descent, during day in visual meteorological conditions.
```

### R039 `sera-923-2012-extracted::clearances_8015_a_d::71094db33155f93d`

- bucket: `rejected`
- document/section: `sera-923-2012-extracted/clearances_8015_a_d`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;curated;curation_reject;executable_or_partial;nested_conditions;non_accepted;quote_audit_fail`

**Claim**

An air traffic control clearance shall indicate any necessary instructions or information on other matters such as approach or departure manoeuvres, communications and the time of expiry of the clearance.

**Exact Source Quotes**

```text
An air traffic control clearance shall indicate:
```

```text
(5) any necessary instructions or information on other matters such as approach or departure manoeuvres, communications and the time of expiry of the clearance.
```

### R040 `sera-923-2012-extracted::clearances_8015_a_d::886221665bbeb27f`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/clearances_8015_a_d`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;executable_or_partial;nested_conditions`

**Claim**

An air traffic control clearance shall indicate the clearance limit.

**Exact Source Quotes**

```text
An air traffic control clearance shall indicate:
```

```text
(2) clearance limit;
```

### R041 `sera-923-2012-extracted::readback_8015_e::913a7181c8e8bdd9`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/readback_8015_e`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;executable_or_partial;nested_conditions`

**Claim**

ATC route clearances shall always be read back.

**Exact Source Quotes**

```text
(i) ATC route clearances;
```

### R042 `sera-923-2012-extracted::separation_minima_8010::e254c674f789e45e`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/separation_minima_8010`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `deterministic_test`
- risk tags: `curated;judge_demoted`

**Claim**

Details of the selected separation minima and of their areas of application shall be notified to the air traffic services units concerned.

**Exact Source Quotes**

```text
(c) Details of the selected separation minima and of their areas of application shall be notified:
```

```text
(1) to the air traffic services units concerned; and
```

### R043 `slovenia-vfr-extracted::readback::2397c3fc7f51c903`

- bucket: `rejected`
- document/section: `slovenia-vfr-extracted/readback`
- authority/modality: `best_practice` / `note`
- kind/testability: `definition` / `review_only`
- risk tags: `challenger_overridden;curated;curation_reject;judge_demoted;local_or_best_practice;nested_conditions;non_accepted;quote_audit_fail;support_only`

**Claim**

'WILCO' means 'I understand your message and will comply with it'.

**Exact Source Quotes**

```text
'WILCO' means 'I understand your message and will comply with it'.
```

### R044 `slovenia-vfr-extracted::readback::34951c851cc73e37`

- bucket: `rejected`
- document/section: `slovenia-vfr-extracted/readback`
- authority/modality: `best_practice` / `should`
- kind/testability: `phraseology_rule` / `partially_executable`
- risk tags: `curated;curation_reject;executable_or_partial;local_or_best_practice;nested_conditions;non_accepted;quote_audit_fail`

**Claim**

If a transmission contains information that does not need to be read back, the pilot should acknowledge it by transmitting his call sign or by transmitting his call sign preceded by the word 'ROGER'.

**Exact Source Quotes**

```text
If a transmission contains information that does not need to be read back, the pilot should acknowledge it by transmitting his call sign or by transmitting his call sign preceded by the word 'ROGER'.
```

### R045 `slovenia-vfr-extracted::readback::a55009e7044c1c4b`

- bucket: `candidates`
- document/section: `slovenia-vfr-extracted/readback`
- authority/modality: `best_practice` / `should`
- kind/testability: `best_practice` / `partially_executable`
- risk tags: `curated;executable_or_partial;judge_demoted;local_or_best_practice;nested_conditions`

**Claim**

It is normally best to read back the items in the order given.

**Exact Source Quotes**

```text
It is normally best to read back the items in the order given.
```

### R046 `slovenia-vfr-extracted::readback::a6eb85ecaac0bd8b`

- bucket: `candidates`
- document/section: `slovenia-vfr-extracted/readback`
- authority/modality: `best_practice` / `shall`
- kind/testability: `best_practice` / `review_only`
- risk tags: `challenger_overridden;curated;judge_demoted;local_or_best_practice;nested_conditions`

**Claim**

Transmissions shall be conducted concisely in a normal conversational tone.

**Exact Source Quotes**

```text
Transmissions shall be conducted concisely in a normal conversational tone.
```

### R047 `slovenia-vfr-extracted::readback::f586b025372a3411`

- bucket: `candidates`
- document/section: `slovenia-vfr-extracted/readback`
- authority/modality: `best_practice` / `should`
- kind/testability: `phraseology_rule` / `partially_executable`
- risk tags: `executable_or_partial;local_or_best_practice;nested_conditions`

**Claim**

The standard phrase 'WILCO' should not be used instead of a full read back of the items from the previous paragraph.

**Exact Source Quotes**

```text
This standard phrase should not be used instead of a full read back of the items from the previous paragraph.
```

### R048 `slovenia-vfr-extracted::readback::fd9994dd1fc98ea3`

- bucket: `rejected`
- document/section: `slovenia-vfr-extracted/readback`
- authority/modality: `best_practice` / `should`
- kind/testability: `best_practice` / `partially_executable`
- risk tags: `challenger_overridden;curated;curation_reject;executable_or_partial;judge_demoted;local_or_best_practice;nested_conditions;non_accepted;quote_audit_fail`

**Claim**

If the departure instructions are transmitted together with a take-off clearance, it is more appropriate to read back the take-off clearance first, followed by the departure instructions.

**Exact Source Quotes**

```text
If the departure instructions are transmitted together with a take-off clearance, it is more appropriate to read back the take-off clearance first, followed by the departure instructions.
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
- records in section: `14`
- risk tags: `local_or_best_practice;nested_conditions`
- source window file: `source_windows/S002_egast-vfr-extracted__readback_advisory.txt`

Readback advisory family. Mixed best-practice prose, bullet list of items requiring readback, acknowledgement-by-callsign rule, Wilco usage, and read-back-when-required guidance, with inline page-layout noise. No clause numbering.

### S003 `h01-extracted/acknowledgement_3_8_1`

- source: `research/txt/h01-extracted.txt:4197-4337`
- records in section: `12`
- risk tags: `bilingual;nested_conditions;parent_child`
- source window file: `source_windows/S003_h01-extracted__acknowledgement_3_8_1.txt`

§3.8.1 ACKNOWLEDGEMENT OF RECEIPT, items a)-h). Bilingual German/English: each lettered item appears twice (German first, English second) with the same letter marker. Nested numbered list 1-4 (the items always to be read back) is laid out separately from its parent clause c) due to two-column extraction.

### S004 `h01-extracted/end_of_conversation_3_8_2`

- source: `research/txt/h01-extracted.txt:4339-4348`
- records in section: `1`
- risk tags: `bilingual`
- source window file: `source_windows/S004_h01-extracted__end_of_conversation_3_8_2.txt`

§3.8.2 END OF CONVERSATION. Bilingual single-paragraph rule: a radiotelephone conversation shall be terminated by the receiving ATS unit or aircraft using its own callsign.

### S005 `icao4444-extracted/aerodrome_traffic_7_6`

- source: `research/txt/icao4444-extracted.txt:8716-8810`
- records in section: `15`
- risk tags: `table_or_figure`
- source window file: `source_windows/S005_icao4444-extracted__aerodrome_traffic_7_6.txt`

§7.6.1-7.6.3.1.1 Control of Aerodrome Traffic: §7.6.1 General clarity-of-instructions duty, §7.6.2 Designated positions of aircraft (positions 1-6 with associated controller actions), §7.6.3.1 Traffic on the manoeuvring area, §7.6.3.1.1 Taxi clearance content rules. Includes inline figure-layout artifacts.

### S006 `icao4444-extracted/departing_aircraft_7_9`

- source: `research/txt/icao4444-extracted.txt:9031-9110`
- records in section: `19`
- risk tags: `nested_conditions;table_or_figure`
- source window file: `source_windows/S006_icao4444-extracted__departing_aircraft_7_9.txt`

§7.9 Control of Departing Aircraft: §7.9.1 Departure sequence (with priority factors a-f), §7.9.2 Separation of departing aircraft (the 'crossed end of runway / started a turn' rule), §7.9.3.1-7.9.3.5 Take-off clearance (timing, ATC clearance prerequisite, TAKE-OFF phraseology rule, 'immediate take-off' clearance). Includes inline Note text and figure-layout artifacts.

### S007 `icao4444-extracted/readback_4_5_7_5`

- source: `research/txt/icao4444-extracted.txt:3401-3429`
- records in section: `9`
- risk tags: `nested_conditions;parent_child`
- source window file: `source_windows/S007_icao4444-extracted__readback_4_5_7_5.txt`

§4.5.7.5 Readback family: parent clause, subordinate list (a/b/c), notes, and following standalone clauses (4.5.7.5.1.1, 4.5.7.5.2, 4.5.7.5.2.1).

### S008 `icao4444-extracted/transfer_4_3_2_1`

- source: `research/txt/icao4444-extracted.txt:3086-3131`
- records in section: `10`
- risk tags: `nested_conditions;parent_child`
- source window file: `source_windows/S008_icao4444-extracted__transfer_4_3_2_1.txt`

§4.3.2.1 Transfer family: arriving and departing control-transfer clauses, nested conditions, and supporting note. Parent + mutually-exclusive sibling shape (a/b).

### S009 `icao9432-extracted/taxi_4_4_en`

- source: `research/txt/icao9432-extracted.txt:4917-5139`
- records in section: `9`
- risk tags: `dialogue_examples`
- source window file: `source_windows/S009_icao9432-extracted__taxi_4_4_en.txt`

§4.4 Taxi Instructions, English portion. Comprehensive operational phraseology section: taxi-clearance content, runway-crossing, hold-short instructions, aircraft-stand procedures, with extensive RTF dialogue examples interleaved with rule prose.

### S010 `safetysense22-extracted/readbacks`

- source: `research/txt/safetysense22-extracted.txt:485-552`
- records in section: `16`
- risk tags: `local_or_best_practice;nested_conditions`
- source window file: `source_windows/S010_safetysense22-extracted__readbacks.txt`

Readbacks section. Best-practice prose with a numbered list (1-15) of items requiring readback, each item laid out across two lines (number on its own line, item text following) due to two-column extraction.

### S011 `sera-923-2012-extracted/atc_service_8005`

- source: `research/txt/sera-923-2012-extracted.txt:1934-1973`
- records in section: `14`
- risk tags: `nested_conditions`
- source window file: `source_windows/S011_sera-923-2012-extracted__atc_service_8005.txt`

SERA.8005 Operation of air traffic control service. Three top-level paragraphs (a/b/c): (a) what an ATC unit shall do (4 numbered duties); (b) which classes of flight separation must be provided between (5 numbered cases plus exception); (c) separation methods (vertical / horizontal with sub-cases). Clean clause hierarchy.

### S012 `slovenia-vfr-extracted/readback`

- source: `research/txt/slovenia-vfr-extracted.txt:524-565`
- records in section: `8`
- risk tags: `local_or_best_practice;nested_conditions`
- source window file: `source_windows/S012_slovenia-vfr-extracted__readback.txt`

Read-back / Items / Wilco / Acknowledgement-by-call-sign / Transmitting-technique sub-sections. Best-practice prose with bullet list of items requiring readback, mostly mirroring CAP 413 / EGAST shape.

