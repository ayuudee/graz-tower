# Registry Adequacy Review Pack

- seed: `fn9-current-frame-2026-05-09`
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

### R002 `cap413-extracted::clearance_issue_context_2_65_to_2_67::130f07dba17f0938`

- bucket: `candidates`
- document/section: `cap413-extracted/clearance_issue_context_2_65_to_2_67`
- authority/modality: `operational_guidance` / `none`
- kind/testability: `definition` / `review_only`
- risk tags: `challenger_overridden;curated;judge_demoted;local_or_best_practice;support_only`

**Claim**

An ATC route clearance is NOT an instruction to take-off or enter an active runway.

**Exact Source Quotes**

```text
An ATC route clearance is NOT an instruction to take-off or enter an active runway.
```

### R003 `cap413-extracted::clearance_issue_context_2_65_to_2_67::983c86331a930a1b`

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

### R004 `cap413-extracted::compliance_timing_2_82_to_2_87::16ae1b859dbd3904`

- bucket: `candidates`
- document/section: `cap413-extracted/compliance_timing_2_82_to_2_87`
- authority/modality: `operational_guidance` / `example`
- kind/testability: `phraseology_rule` / `review_only`
- risk tags: `curated;dialogue_examples;judge_demoted;support_only`

**Claim**

Examples of using 'after passing' include: 'BIGJET 347, after passing North Cross, descend FL80' and 'After passing North Cross, descend FL80, BIGJET 347'.

**Exact Source Quotes**

```text
BIGJET 347, after passing North Cross, descend FL80
```

```text
After passing North Cross, descend FL80, BIGJET 347
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

### R007 `cap413-extracted::frequency_change_permission_2_60_to_2_61::f3ee57c068d9104f`

- bucket: `candidates`
- document/section: `cap413-extracted/frequency_change_permission_2_60_to_2_61`
- authority/modality: `operational_guidance` / `example`
- kind/testability: `phraseology_rule` / `review_only`
- risk tags: `curated;judge_demoted;support_only`

**Claim**

Example phraseology for frequency change: 'BIGJET 347, contact Wrayton Control 129.125'.

**Exact Source Quotes**

```text
BIGJET 347, contact Wrayton Control 129.125
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

### R010 `cap413-extracted::transfer_content_2_57_to_2_59::f21c2b31952f844e`

- bucket: `candidates`
- document/section: `cap413-extracted/transfer_content_2_57_to_2_59`
- authority/modality: `operational_guidance` / `may`
- kind/testability: `rule` / `partially_executable`
- risk tags: `curated;executable_or_partial;judge_demoted`

**Claim**

If no further communication is received from the pilot after an acknowledgement, satisfactory transfer of communication may be assumed.

**Exact Source Quotes**

```text
If no further communication is received from the pilot after an acknowledgement, satisfactory transfer of communication may be assumed.
```

### R011 `cap413-extracted::unable_reclearance_critical_info_2_72_to_2_75::6e7ad694d76c98e9`

- bucket: `candidates`
- document/section: `cap413-extracted/unable_reclearance_critical_info_2_72_to_2_75`
- authority/modality: `operational_guidance` / `example`
- kind/testability: `best_practice` / `review_only`
- risk tags: `challenger_overridden;curated;judge_demoted;support_only`

**Claim**

Windshear Warnings are considered examples of critical information.

**Exact Source Quotes**

```text
Windshear Warnings.
```

### R012 `cap413-extracted::unable_reclearance_critical_info_2_72_to_2_75::af1bed78594b1d89`

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
- authority/modality: `operational_guidance` / `shall`
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
- authority/modality: `operational_guidance` / `shall`
- kind/testability: `rule` / `partially_executable`
- risk tags: `bilingual;curated;curation_reject;executable_or_partial;judge_demoted;nested_conditions;non_accepted;parent_child`

**Claim**

The flight crew shall read back to the air traffic controller safety-related parts of ATC clearances and instructions which are transmitted by voice.

**Exact Source Quotes**

```text
The flight crew shall read back to the air traffic controller safety-related parts of ATC clearances and instructions which are transmitted by voice.
```

### R016 `h01-extracted::assurance_rtf_frequencies_3_9::9f2e05427db05b16`

- bucket: `candidates`
- document/section: `h01-extracted/assurance_rtf_frequencies_3_9`
- authority/modality: `operational_guidance` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `bilingual;challenger_overridden;dialogue_examples;executable_or_partial`

**Claim**

The air-ground control station shall designate the frequency (-ies) to be used under normal conditions by aircraft stations operating under its control.

**Exact Source Quotes**

```text
The air-ground control station shall designate the frequency (-ies) to be used under normal conditions by aircraft stations operating under its control.
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

### R018 `icao4444-extracted::aerodrome_traffic_7_6::ea64ad7c56d8e4ef`

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

### R019 `icao4444-extracted::aerodrome_traffic_7_6::f11471c05d5603f6`

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

### R020 `icao4444-extracted::arriving_aircraft_7_10::dcc6bc5c93d748ec`

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

### R021 `icao4444-extracted::clearance_scope_contents_4_5_1_to_4_5_7_4::85846fa1a8b72599`

- bucket: `candidates`
- document/section: `icao4444-extracted/clearance_scope_contents_4_5_1_to_4_5_7_4`
- authority/modality: `operational_guidance` / `note`
- kind/testability: `definition` / `review_only`
- risk tags: `curated;judge_demoted;support_only`

**Claim**

See 6.3.2.3 pertaining to standard clearances for departing aircraft and 6.5.2.3 pertaining to standard clearances for arriving aircraft.

**Exact Source Quotes**

```text
See 6.3.2.3 pertaining to standard clearances for departing aircraft and 6.5.2.3 pertaining to standard clearances for arriving aircraft.
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

### R023 `icao4444-extracted::departing_aircraft_7_9::d6865a7f538c64f6`

- bucket: `candidates`
- document/section: `icao4444-extracted/departing_aircraft_7_9`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;executable_or_partial;nested_conditions;table_or_figure`

**Claim**

A departing aircraft will not normally be permitted to commence take-off until the preceding departing aircraft has crossed the end of the runway-in-use or has started a turn.

**Exact Source Quotes**

```text
a departing aircraft will not normally be permitted to commence take-off until the preceding departing aircraft has crossed the end of the runway-in-use or has started a turn
```

### R024 `icao4444-extracted::phraseology_clearance_issuance_12_3_2_1::180ca61ddf3bad84`

- bucket: `candidates`
- document/section: `icao4444-extracted/phraseology_clearance_issuance_12_3_2_1`
- authority/modality: `operational_guidance` / `shall`
- kind/testability: `phraseology_rule` / `sim_executable`
- risk tags: `challenger_overridden;executable_or_partial;judge_overridden`

**Claim**

Reclearance with amended clearance details shall use the format: RECLEARED (amended clearance details) [REST OF CLEARANCE UNCHANGED].

**Exact Source Quotes**

```text
RECLEARED (amended clearance details) [REST OF CLEARANCE UNCHANGED];
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

### R026 `icao4444-extracted::position_reporting_voice_4_11_1_to_4_11_3::336bf88cabfbd998`

- bucket: `candidates`
- document/section: `icao4444-extracted/position_reporting_voice_4_11_1_to_4_11_3`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `challenger_overridden;curated;executable_or_partial;judge_demoted`

**Claim**

Position reports shall contain time.

**Exact Source Quotes**

```text
time
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

### R029 `icao4444-extracted::reduced_runway_7_11::c43ffaf1e23f64ed`

- bucket: `candidates`
- document/section: `icao4444-extracted/reduced_runway_7_11`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `definition` / `sim_executable`
- risk tags: `challenger_overridden;executable_or_partial;nested_conditions`

**Claim**

Category 1 aircraft are defined as single-engine propeller aircraft with a maximum certificated take-off mass of 2 000 kg or less.

**Exact Source Quotes**

```text
Category 1 aircraft: single-engine propeller aircraft with a maximum certificated take-off mass of 2 000 kg or less;
```

### R030 `icao4444-extracted::speed_control_4_6_1::68998a3add172915`

- bucket: `candidates`
- document/section: `icao4444-extracted/speed_control_4_6_1`
- authority/modality: `operational_guidance` / `may`
- kind/testability: `advisory_permission` / `sim_executable`
- risk tags: `curated;executable_or_partial`

**Claim**

Aircraft may, subject to conditions specified by the appropriate authority, be instructed to adjust speed in a specified manner.

**Exact Source Quotes**

```text
In order to facilitate a safe and orderly flow of traffic, aircraft may, subject to conditions specified by the
appropriate authority, be instructed to adjust speed in a specified manner.
```

### R031 `icao4444-extracted::speed_control_4_6_1::8357e703f52d70a6`

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

### R032 `icao4444-extracted::transfer_4_3_2_1::2198f6235c44d5d7`

- bucket: `rejected`
- document/section: `icao4444-extracted/transfer_4_3_2_1`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `partially_executable`
- risk tags: `curated;curation_reject;executable_or_partial;judge_demoted;nested_conditions;non_accepted;parent_child;quote_audit_fail`

**Claim**

Control of a departing aircraft shall be transferred from the unit providing aerodrome control service to the unit providing approach control service when visual meteorological conditions prevail in the vicinity of the aerodrome, as specified in letters of agreement or ATS unit instructions.

**Exact Source Quotes**

```text
Control of a departing aircraft shall be transferred from the unit providing aerodrome control service to the unit providing approach control service: a) when visual meteorological conditions prevail in the vicinity of the aerodrome... as specified in letters of agreement or ATS unit instructions;
```

### R033 `icao4444-extracted::transfer_4_3_2_1::6524e899c0a46912`

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

### R034 `icao4444-extracted::transfer_4_3_2_1::f0cb8347ee5a5ea9`

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

### R035 `icao4444-extracted::wake_turbulence_5_8::6dd2f290eea7522a`

- bucket: `candidates`
- document/section: `icao4444-extracted/wake_turbulence_5_8`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;executable_or_partial;table_or_figure`

**Claim**

A minimum separation of 2 minutes shall be applied between a LIGHT or MEDIUM aircraft taking off behind a HEAVY aircraft or a LIGHT aircraft taking off behind a MEDIUM aircraft when the aircraft are using the same runway.

**Exact Source Quotes**

```text
A minimum separation of 2 minutes shall be applied between a LIGHT or MEDIUM aircraft taking off behind a HEAVY aircraft or a LIGHT aircraft taking off behind a MEDIUM aircraft when the aircraft are using:
```

```text
a) the same runway;
```

### R036 `icao9432-extracted::taxi_4_4_en::417f64324f7495bf`

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

### R037 `icao9432-extracted::taxi_4_4_en::453f27d271407f98`

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

### R038 `icao9432-extracted::transfer_communications_2_8_2_en::96720e821bf926cc`

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

### R039 `icao9432-extracted::transfer_communications_2_8_2_en::b3f67ba3a9d8a2b8`

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

### R040 `safetysense22-extracted::readbacks::3d2c7c537110ab85`

- bucket: `candidates`
- document/section: `safetysense22-extracted/readbacks`
- authority/modality: `best_practice` / `must`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;executable_or_partial;local_or_best_practice;nested_conditions`

**Claim**

Transition Levels must be read back in their entirety.

**Exact Source Quotes**

```text
The following items must be read back in their entirety if addressed to you in a transmission from an ATSU:
```

```text
15. Transition Levels
```

### R041 `safetysense22-extracted::readbacks::9b6c62502e773767`

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

### R042 `sera-923-2012-extracted::adherence_flight_plan_8020::35da37ec7ec88084`

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

### R043 `sera-923-2012-extracted::atc_service_8005::dd09fb0eb790d595`

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

### R044 `sera-923-2012-extracted::clearance_coordination_8015_f::0196b1fddfacc533`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/clearance_coordination_8015_f`
- authority/modality: `authoritative_requirement` / `mixed`
- kind/testability: `rule` / `partially_executable`
- risk tags: `curated;executable_or_partial`

**Claim**

When an aircraft intends to leave a control area for flight outside controlled airspace, and will subsequently reenter the same or another control area, a clearance from the point of departure to the aerodrome of first intended landing may be issued. Such clearance or revisions thereto shall apply only to those portions of the flight conducted within controlled airspace.

**Exact Source Quotes**

```text
When an aircraft intends to leave a control area for flight outside controlled airspace, and will subsequently reenter the same or another control area, a clearance from the point of departure to the aerodrome of first intended landing may be issued. Such clearance or revisions thereto shall apply only to those portions of the flight conducted within controlled airspace.
```

### R045 `sera-923-2012-extracted::clearances_8015_a_d::2e38db256d177b3e`

- bucket: `candidates`
- document/section: `sera-923-2012-extracted/clearances_8015_a_d`
- authority/modality: `authoritative_requirement` / `shall`
- kind/testability: `rule` / `sim_executable`
- risk tags: `curated;executable_or_partial;nested_conditions`

**Claim**

An air traffic control clearance shall indicate any necessary instructions or information on other matters such as approach or departure manoeuvres, communications and the time of expiry of the clearance.

**Exact Source Quotes**

```text
An air traffic control clearance shall indicate:
```

```text
(5) any necessary instructions or information on other matters such as approach or departure manoeuvres, communi­
cations and the time of expiry of the clearance.
```

### R046 `sera-923-2012-extracted::readback_8015_e::c86e7463f898e278`

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

### R047 `sera-923-2012-extracted::separation_minima_8010::14871d3b19e91d53`

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

### S004 `h01-extracted/assurance_rtf_frequencies_3_9`

- source: `research/txt/h01-extracted.txt:4467-4485`
- records in section: `2`
- risk tags: `bilingual;dialogue_examples`
- source window file: `source_windows/S004_h01-extracted__assurance_rtf_frequencies_3_9.txt`

§3.9 ASSURANCE OF RTF COMMUNICATION/FREQUENCIES TO BE USED. Bilingual German/English: aircraft frequency-use obligation and air-ground control station frequency designation responsibility.

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

### S009 `icao9432-extracted/transfer_communications_2_8_2_en`

- source: `research/txt/icao9432-extracted.txt:3830-3855`
- records in section: `6`
- risk tags: `dialogue_examples`
- source window file: `source_windows/S009_icao9432-extracted__transfer_communications_2_8_2_en.txt`

ICAO Doc 9432 §2.8.2 English-only transfer-of-communications guidance and examples. Polish translation begins after this window. Hardened in clearance_comms_2026-04-30.

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
- records in section: `15`
- risk tags: `local_or_best_practice;nested_conditions`
- source window file: `source_windows/S012_slovenia-vfr-extracted__readback.txt`

Read-back / Items / Wilco / Acknowledgement-by-call-sign / Transmitting-technique sub-sections. Best-practice prose with bullet list of items requiring readback, mostly mirroring CAP 413 / EGAST shape.
