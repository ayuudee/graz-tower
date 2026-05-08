# Registry Adequacy Review Pack

- seed: `clearance-comms-2026-05-04`
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

### R001 `cap413-extracted::air_ground_communication_failure_2_88::8d2caa3a520ede11`

- bucket: `candidates`
- document/section: `cap413-extracted/air_ground_communication_failure_2_88`
- authority/modality: `background_support` / `note`
- kind/testability: `cross_reference` / `review_only`
- risk tags: `challenger_overridden;curated;judge_demoted;support_only`

**Claim**

Specific procedures for the action to be taken by pilots of IFR and Special VFR flights are contained in the appropriate AIP ENR and/or AD sections.

**Exact Source Quotes**

```text
Specific procedures for the action to be taken by pilots of IFR and Special VFR flights are contained in the appropriate AIP ENR and/or AD sections.
```

### R002 `cap413-extracted::clearance_issue_context_2_65_to_2_67::983c86331a930a1b`

- bucket: `rejected`
- document/section: `cap413-extracted/clearance_issue_context_2_65_to_2_67`
- authority/modality: `operational_guidance` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;curated;curation_reject;executable_or_partial;local_or_best_practice;non_accepted;quote_audit_fail`

**Claim**

When a route clearance is passed subsequent to local departure instructions, or to an aircraft that is already airborne, tactical restrictions that remain in place shall be reiterated to ensure that the immediate profile to be flown by the pilot is unambiguous.

**Exact Source Quotes**

```text
When a route clearance is passed subsequent to local departure instructions, or to an aircraft that is already airborne, tactical restrictions that remain in place shall be reiterated to ensure that the immediate profile to be flown by the pilot is unambiguous.
```

### R003 `cap413-extracted::clearance_issue_context_2_65_to_2_67::fa08347c34840807`

- bucket: `candidates`
- document/section: `cap413-extracted/clearance_issue_context_2_65_to_2_67`
- authority/modality: `operational_guidance` / `mixed`
- kind/testability: `workflow_constraint` / `review_only`
- risk tags: `challenger_overridden;curated;judge_demoted;local_or_best_practice`

**Claim**

Generally, controllers will avoid passing a clearance to a pilot engaged in complicated taxiing manoeuvres and on no occasion when the pilot is engaged in line up or take-off manoeuvres.

**Exact Source Quotes**

```text
Generally, controllers will avoid passing a clearance to a pilot engaged in complicated taxiing manoeuvres and on no occasion when the pilot is engaged in line up or take-off manoeuvres.
```

### R004 `cap413-extracted::compliance_timing_2_82_to_2_87::4cb8628d976ff868`

- bucket: `candidates`
- document/section: `cap413-extracted/compliance_timing_2_82_to_2_87`
- authority/modality: `operational_guidance` / `example`
- kind/testability: `phraseology_rule` / `review_only`
- risk tags: `curated;dialogue_examples;judge_demoted;support_only`

**Claim**

Examples of using 'now' include: 'BIGJET 347, reduce speed now 210 kt' and 'Reducing speed now 210 kt, BIGJET 347'.

**Exact Source Quotes**

```text
BIGJET 347, reduce speed now 210 kt
```

```text
Reducing speed now 210 kt, BIGJET 347
```

### R005 `cap413-extracted::corrections_2_54_to_2_55::69ca0abbf1802295`

- bucket: `rejected`
- document/section: `cap413-extracted/corrections_2_54_to_2_55`
- authority/modality: `operational_guidance` / `example`
- kind/testability: `best_practice` / `review_only`
- risk tags: `curated;curation_reject;judge_demoted;non_accepted;support_only`

**Claim**

Example of partial correction: 'BIGJET 347, Wicken 47 FL280 Marlow 07 correction Marlow 57'.

**Exact Source Quotes**

```text
BIGJET 347, Wicken 47 FL280 Marlow 07 correction Marlow 57
```

### R006 `cap413-extracted::freecall_change_intention_2_64::14b857a7efd5c5a1`

- bucket: `candidates`
- document/section: `cap413-extracted/freecall_change_intention_2_64`
- authority/modality: `operational_guidance` / `example`
- kind/testability: `phraseology_example` / `review_only`
- risk tags: `curated;judge_demoted;support_only`

**Claim**

Examples of phraseology for requesting a change to another agency include 'request change to [Agency] [Frequency]' and '[Agency], [Callsign], changing to [Agency] [Frequency]'.

**Exact Source Quotes**

```text
Westbury, G-ABCD, request change to Wrayton Information 125.750
```

```text
Wrayton Information, G-ABCD, changing to Wrayton Centre 121.5 for Practice Pan
```

### R007 `cap413-extracted::frequency_change_permission_2_60_to_2_61::e87cb5ce96b795fd`

- bucket: `candidates`
- document/section: `cap413-extracted/frequency_change_permission_2_60_to_2_61`
- authority/modality: `operational_guidance` / `none`
- kind/testability: `best_practice` / `review_only`
- risk tags: `challenger_overridden;curated;judge_demoted`

**Claim**

An aircraft will normally be advised by the appropriate aeronautical station to change from one radio frequency to another in accordance with agreed procedures.

**Exact Source Quotes**

```text
An aircraft will normally be advised by the appropriate aeronautical station to change from one radio frequency to another in accordance with agreed procedures.
```

### R008 `cap413-extracted::ground_air_communication_failure_2_89_to_2_91::d18240dcecab405d`

- bucket: `candidates`
- document/section: `cap413-extracted/ground_air_communication_failure_2_89_to_2_91`
- authority/modality: `operational_guidance` / `shall`
- kind/testability: `rule` / `partially_executable`
- risk tags: `challenger_overridden;curated;executable_or_partial;judge_demoted`

**Claim**

Blind transmission messages shall include the level, route and EAT (or ETA) to which it is assumed the aircraft is adhering.

**Exact Source Quotes**

```text
1. The level, route and EAT (or ETA) to which it is assumed the aircraft is adhering.
```

### R009 `cap413-extracted::readback_2_68_2_71::5be7cfa8ac79626b`

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

### R010 `cap413-extracted::transfer_content_2_57_to_2_59::28c08ec2a7005ec7`

- bucket: `candidates`
- document/section: `cap413-extracted/transfer_content_2_57_to_2_59`
- authority/modality: `operational_guidance` / `should`
- kind/testability: `best_practice` / `partially_executable`
- risk tags: `curated;executable_or_partial;judge_demoted`

**Claim**

Transfer of communication instructions should be passed in a single message.

**Exact Source Quotes**

```text
Transfer of communication instructions should be passed in a single message.
```

### R011 `cap413-extracted::unable_reclearance_critical_info_2_72_to_2_75::af1bed78594b1d89`

- bucket: `rejected`
- document/section: `cap413-extracted/unable_reclearance_critical_info_2_72_to_2_75`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `definition` / `review_only`
- risk tags: `challenger_overridden;curated;curation_reject;judge_overridden;non_accepted;quote_audit_fail`

**Claim**

Critical information is information, other than that required to enable routine flight, which must be received by pilots to ensure the safety and effective operation of their aircraft.

**Exact Source Quotes**

```text
Critical information is information, other than that required to enable routine flight, which must be received by pilots to ensure the safety and effective operation of their aircraft.
```

### R012 `cap413-extracted::unable_reclearance_critical_info_2_72_to_2_75::edaf457e4ec53926`

- bucket: `candidates`
- document/section: `cap413-extracted/unable_reclearance_critical_info_2_72_to_2_75`
- authority/modality: `operational_guidance` / `example`
- kind/testability: `best_practice` / `review_only`
- risk tags: `challenger_overridden;curated;judge_demoted;support_only`

**Claim**

Weather hazards (thunderstorms, hail, icing, etc.) are considered examples of critical information.

**Exact Source Quotes**

```text
Weather hazards (thunderstorms, hail, icing, etc.).
```

### R013 `egast-vfr-extracted::readback_advisory::2c0a6da5b0abc95f`

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

### R014 `h01-extracted::acknowledgement_3_8_1::1ba408f68e9b501d`

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

### R015 `h01-extracted::acknowledgement_3_8_1::647df1446645072b`

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

### R016 `h01-extracted::acknowledgement_3_8_1::d19604b475643777`

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

### R017 `h01-extracted::corrections_3_8_3::16272622eb9270ac`

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

### R018 `h01-extracted::end_of_conversation_3_8_2::1d66565b4872e6a0`

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

### R019 `icao4444-extracted::aerodrome_traffic_7_6::1ebd71be057d48f1`

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

### R020 `icao4444-extracted::aerodrome_traffic_7_6::ea64ad7c56d8e4ef`

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

### R021 `icao4444-extracted::arriving_aircraft_7_10::dcc6bc5c93d748ec`

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

### R022 `icao4444-extracted::clearance_scope_contents_4_5_1_to_4_5_7_4::b0cea0c2e3246c75`

- bucket: `rejected`
- document/section: `icao4444-extracted/clearance_scope_contents_4_5_1_to_4_5_7_4`
- authority/modality: `authoritative_requirement` / `may`
- kind/testability: `phraseology_rule` / `partially_executable`
- risk tags: `curated;curation_reject;executable_or_partial;non_accepted;quote_audit_fail`

**Claim**

The phrases 'cleared (designation) departure' or 'cleared (designation) arrival' may be used when standard departure or arrival routes have been established by the appropriate ATS authority and published in Aeronautical Information Publications (AIPs).

**Exact Source Quotes**

```text
The phrases 'cleared (designation) departure' or 'cleared (designation) arrival' may be used when standard departure or arrival routes have been established
```

### R023 `icao4444-extracted::departing_aircraft_7_9::ace9c0a1a5794eee`

- bucket: `candidates`
- document/section: `icao4444-extracted/departing_aircraft_7_9`
- authority/modality: `operational_guidance` / `should`
- kind/testability: `best_practice` / `partially_executable`
- risk tags: `curated;executable_or_partial;nested_conditions;table_or_figure`

**Claim**

Factors to be considered in relation to the departure sequence include the need to apply wake turbulence separation minima.

**Exact Source Quotes**

```text
Factors which should be considered in relation to the departure sequence include, inter alia:
```

```text
d) need to apply wake turbulence separation minima;
```

### R024 `icao4444-extracted::phraseology_clearance_issuance_12_3_2_1::bb268465618a9fbc`

- bucket: `candidates`
- document/section: `icao4444-extracted/phraseology_clearance_issuance_12_3_2_1`
- authority/modality: `operational_guidance` / `shall`
- kind/testability: `phraseology_rule` / `sim_executable`
- risk tags: `challenger_overridden;executable_or_partial;judge_overridden`

**Claim**

Clearance to enter controlled airspace shall use the format: ENTER CONTROLLED AIRSPACE (or CONTROL ZONE) [VIA (significant point or route)] AT (level) [AT (time)].

**Exact Source Quotes**

```text
ENTER CONTROLLED AIRSPACE (or CONTROL ZONE) [VIA (significant point or route)] AT (level) [AT (time)];
```

### R025 `icao4444-extracted::phraseology_transfer_frequency_12_3_1_4::50ac98b0cfa6d73b`

- bucket: `candidates`
- document/section: `icao4444-extracted/phraseology_transfer_frequency_12_3_1_4`
- authority/modality: `operational_guidance` / `shall`
- kind/testability: `phraseology_rule` / `sim_executable`
- risk tags: `challenger_overridden;curated;executable_or_partial;judge_demoted`

**Claim**

Transfer of control phraseology shall use: AT (or OVER) (time or place) [or WHEN] [PASSING/LEAVING/REACHING (level)] CONTACT (unit call sign) (frequency);

**Exact Source Quotes**

```text
AT (or OVER) (time or place) [or WHEN]
```

```text
[PASSING/LEAVING/REACHING (level)] CONTACT
```

```text
(unit call sign) (frequency);
```

### R026 `icao4444-extracted::position_reporting_voice_4_11_1_to_4_11_3::e7db6822d68514f3`

- bucket: `candidates`
- document/section: `icao4444-extracted/position_reporting_voice_4_11_1_to_4_11_3`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;curated;executable_or_partial;judge_demoted`

**Claim**

Position reports shall contain position.

**Exact Source Quotes**

```text
position
```

### R027 `icao4444-extracted::readback_4_5_7_5::1cc502e99b33ace6`

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

### R028 `icao4444-extracted::readback_4_5_7_5::80a171af98372544`

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

### R029 `icao4444-extracted::reduced_runway_7_11::ab9c766811a8897c`

- bucket: `candidates`
- document/section: `icao4444-extracted/reduced_runway_7_11`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `definition` / `sim_executable`
- risk tags: `challenger_overridden;executable_or_partial;nested_conditions`

**Claim**

Category 2 aircraft are defined as single-engine propeller aircraft with a maximum certificated take-off mass of more than 2 000 kg but less than 7 000 kg, and twin-engine propeller aircraft with a maximum certificated take-off mass of less than 7 000 kg.

**Exact Source Quotes**

```text
Category 2 aircraft: single-engine propeller aircraft with a maximum certificated take-off mass of more than 2 000 kg but less than 7 000 kg; and twin-engine propeller aircraft with a maximum certificated take-off mass of less than 7 000 kg;
```

### R030 `icao4444-extracted::speed_control_4_6_1::8357e703f52d70a6`

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

### R031 `icao4444-extracted::transfer_4_3_2_1::6524e899c0a46912`

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

### R032 `icao4444-extracted::transfer_4_3_2_1::e87aa82016b9b93b`

- bucket: `rejected`
- document/section: `icao4444-extracted/transfer_4_3_2_1`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `partially_executable`
- risk tags: `curated;curation_reject;executable_or_partial;judge_demoted;nested_conditions;non_accepted;parent_child;quote_audit_fail`

**Claim**

Control of an arriving aircraft shall be transferred from the unit providing approach control service to the unit providing aerodrome control service when the aircraft is in the vicinity of the aerodrome and it is considered that approach and landing will be completed in visual reference to the ground, or has reached uninterrupted visual meteorological conditions, as specified in letters of agreement or ATS unit instructions.

**Exact Source Quotes**

```text
Control of an arriving aircraft shall be transferred from the unit providing approach control service to the unit providing aerodrome control service when the aircraft: a) is in the vicinity of the aerodrome, and 1) it is considered that approach and landing will be completed in visual reference to the ground, or 2) has reached uninterrupted visual meteorological conditions... as specified in letters of agreement or ATS unit instructions.
```

### R033 `icao4444-extracted::transfer_4_3_2_1::f0cb8347ee5a5ea9`

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

### R034 `icao4444-extracted::wake_turbulence_5_8::3cebabb5ef5a6558`

- bucket: `candidates`
- document/section: `icao4444-extracted/wake_turbulence_5_8`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;executable_or_partial;table_or_figure`

**Claim**

A separation minimum of 2 minutes shall be applied between a LIGHT or MEDIUM aircraft and a HEAVY aircraft and between a LIGHT aircraft and a MEDIUM aircraft when operating on a runway with a displaced landing threshold when a departing LIGHT or MEDIUM aircraft follows a HEAVY aircraft arrival and a departing LIGHT aircraft follows a MEDIUM aircraft arrival.

**Exact Source Quotes**

```text
A separation minimum of 2 minutes shall be applied between a LIGHT or MEDIUM aircraft and a HEAVY aircraft and between a LIGHT aircraft and a MEDIUM aircraft when operating on a runway with a displaced landing threshold when:
```

```text
a) a departing LIGHT or MEDIUM aircraft follows a HEAVY aircraft arrival and a departing LIGHT aircraft follows a MEDIUM aircraft arrival; or
```

### R035 `icao9432-extracted::taxi_4_4_en::417f64324f7495bf`

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

### R036 `icao9432-extracted::taxi_4_4_en::453f27d271407f98`

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

### R037 `icao9432-extracted::transfer_communications_2_8_2_en::96720e821bf926cc`

- bucket: `candidates`
- document/section: `icao9432-extracted/transfer_communications_2_8_2_en`
- authority/modality: `operational_guidance` / `example`
- kind/testability: `phraseology_rule` / `review_only`
- risk tags: `curated;dialogue_examples;judge_demoted`

**Claim**

Phraseology for frequency change includes 'CONTACT [Unit] [Frequency]' and readback 'Frequency Callsign'.

**Exact Source Quotes**

```text
FASTAIR 345 CONTACT ALEXANDER CONTROL 129.1
```

```text
129.1 FASTAIR 345
```

### R038 `icao9432-extracted::transfer_communications_2_8_2_en::b3f67ba3a9d8a2b8`

- bucket: `rejected`
- document/section: `icao9432-extracted/transfer_communications_2_8_2_en`
- authority/modality: `background_support` / `note`
- kind/testability: `definition` / `review_only`
- risk tags: `curated;curation_reject;dialogue_examples;judge_demoted;non_accepted;support_only`

**Claim**

See APPENDIX 1 for differences from ICAO Radiotelephony Procedures.

**Exact Source Quotes**

```text
15 See: APPENDIX 1 DIFFERENCES FROM ICAO RADIOTELEPHONY PROCEDURES
```

### R039 `safetysense22-extracted::readbacks::90320bb2ed9a12a0`

- bucket: `candidates`
- document/section: `safetysense22-extracted/readbacks`
- authority/modality: `best_practice` / `must`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;executable_or_partial;local_or_best_practice;nested_conditions`

**Claim**

VDF information must be read back in their entirety.

**Exact Source Quotes**

```text
The following items must be read back in their entirety if addressed to you in a transmission from an ATSU:
```

```text
12. VDF information
```

### R040 `safetysense22-extracted::readbacks::9b6c62502e773767`

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

### R041 `sera-923-2012-extracted::adherence_flight_plan_8020::35da37ec7ec88084`

- bucket: `rejected`
- document/section: `sera-923-2012-extracted/adherence_flight_plan_8020`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `partially_executable`
- risk tags: `challenger_overridden;curated;curation_reject;executable_or_partial;non_accepted;quote_audit_fail`

**Claim**

Requests for flight plan changes involving a change of route with destination changed shall include aircraft identification, flight rules, description of revised route of flight to revised destination aerodrome including related flight plan data, beginning with the position from which requested change of route is to commence, revised time estimates, alternate aerodrome(s), and any other pertinent information.

**Exact Source Quotes**

```text
Destination changed: aircraft identification; flight rules; description of revised route of flight to revised destination aerodrome including related flight plan data, beginning with the position from which requested change of route is to commence; revised time estimates; alternate aerodrome(s); any other pertinent information
```

### R042 `sera-923-2012-extracted::atc_service_8005::2f46ccb194a81cff`

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

### R043 `sera-923-2012-extracted::clearance_coordination_8015_f::2456f0967378ae5e`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/clearance_coordination_8015_f`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `partially_executable`
- risk tags: `curated;executable_or_partial;judge_demoted`

**Claim**

When prescribed by the ATS unit, aircraft shall contact a downstream air traffic control unit, for the purpose of receiving a downstream clearance prior to the transfer of control point.

**Exact Source Quotes**

```text
When prescribed by the ATS unit, aircraft shall contact a downstream air traffic control unit, for the purpose of receiving a downstream clearance prior to the transfer of control point.
```

### R044 `sera-923-2012-extracted::clearances_8015_a_d::9285a92b423c68d6`

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

### R045 `sera-923-2012-extracted::communications_8035::d87a9dd92c5ecbeb`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/communications_8035`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `partially_executable`
- risk tags: `curated;executable_or_partial;judge_demoted`

**Claim**

An aircraft operated as a controlled flight shall maintain continuous air-ground voice communication watch on the appropriate communication channel of, and establish two-way communication as necessary with, the appropriate air traffic control unit, except as may be prescribed by the relevant ANSP in respect of aircraft forming part of aerodrome traffic at a controlled aerodrome.

**Exact Source Quotes**

```text
An aircraft operated as a controlled flight shall maintain continuous air-ground voice communication watch
```

```text
establish two-way communication as necessary with, the appropriate air traffic control unit
```

### R046 `sera-923-2012-extracted::readback_8015_e::73a06821c9400fa6`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/readback_8015_e`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;executable_or_partial;nested_conditions`

**Claim**

Runway-in-use, altimeter settings, SSR codes, newly assigned communication channels, level instructions, heading and speed instructions shall always be read back.

**Exact Source Quotes**

```text
(1) The flight crew shall read back to the air traffic controller safety-related parts of ATC clearances and instructions which are transmitted by voice. The following items shall always be read back:
```

```text
(iii) runway-in-use, altimeter settings, SSR codes, newly assigned communication channels, level instructions, heading and speed instructions; and
```

### R047 `sera-923-2012-extracted::separation_minima_8010::e254c674f789e45e`

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

### R048 `slovenia-vfr-extracted::readback::a9ad75de93e0b18a`

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

### S004 `h01-extracted/end_of_conversation_3_8_2`

- source: `research/txt/h01-extracted.txt:4339-4348`
- records in section: `1`
- risk tags: `bilingual`
- source window file: `source_windows/S004_h01-extracted__end_of_conversation_3_8_2.txt`

§3.8.2 END OF CONVERSATION. Bilingual single-paragraph rule: a radiotelephone conversation shall be terminated by the receiving ATS unit or aircraft using its own callsign.

### S005 `icao4444-extracted/departing_aircraft_7_9`

- source: `research/txt/icao4444-extracted.txt:9031-9110`
- records in section: `19`
- risk tags: `nested_conditions;table_or_figure`
- source window file: `source_windows/S005_icao4444-extracted__departing_aircraft_7_9.txt`

§7.9 Control of Departing Aircraft: §7.9.1 Departure sequence (with priority factors a-f), §7.9.2 Separation of departing aircraft (the 'crossed end of runway / started a turn' rule), §7.9.3.1-7.9.3.5 Take-off clearance (timing, ATC clearance prerequisite, TAKE-OFF phraseology rule, 'immediate take-off' clearance). Includes inline Note text and figure-layout artifacts.

### S006 `icao4444-extracted/readback_4_5_7_5`

- source: `research/txt/icao4444-extracted.txt:3401-3429`
- records in section: `9`
- risk tags: `nested_conditions;parent_child`
- source window file: `source_windows/S006_icao4444-extracted__readback_4_5_7_5.txt`

§4.5.7.5 Readback family: parent clause, subordinate list (a/b/c), notes, and following standalone clauses (4.5.7.5.1.1, 4.5.7.5.2, 4.5.7.5.2.1).

### S007 `icao4444-extracted/reduced_runway_7_11`

- source: `research/txt/icao4444-extracted.txt:9175-9245`
- records in section: `18`
- risk tags: `nested_conditions`
- source window file: `source_windows/S007_icao4444-extracted__reduced_runway_7_11.txt`

§7.11.1-7.11.6 Reduced runway separation minima between aircraft using the same runway: safety-assessment prerequisite, hours-of-daylight constraint, aircraft classification, departing-vs-landing exclusion, conditional preconditions list.

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

### S011 `sera-923-2012-extracted/clearances_8015_a_d`

- source: `research/txt/sera-923-2012-extracted.txt:1985-2022`
- records in section: `13`
- risk tags: `nested_conditions`
- source window file: `source_windows/S011_sera-923-2012-extracted__clearances_8015_a_d.txt`

SERA.8015 Air traffic control clearances paragraphs (a)-(d): (a) basis of clearances, (b) operation subject to clearance with sub-numbered cases, (c) potential reclearance, (d) contents of clearances (5 numbered fields). The (e) readback paragraph already covered separately as readback_8015_e.

### S012 `slovenia-vfr-extracted/readback`

- source: `research/txt/slovenia-vfr-extracted.txt:524-565`
- records in section: `15`
- risk tags: `local_or_best_practice;nested_conditions`
- source window file: `source_windows/S012_slovenia-vfr-extracted__readback.txt`

Read-back / Items / Wilco / Acknowledgement-by-call-sign / Transmitting-technique sub-sections. Best-practice prose with bullet list of items requiring readback, mostly mirroring CAP 413 / EGAST shape.
