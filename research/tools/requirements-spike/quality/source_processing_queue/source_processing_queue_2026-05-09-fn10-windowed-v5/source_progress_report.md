# Source Progress Report

Generated: `2026-05-09T19:13:55Z`

## Overall Counts

- Included current-frame sources: `8`
- Excluded future sources: `2`
- Manifest windows: `232`
- Landed manifest windows: `47`
- Ready-to-ingest manifest windows: `185`
- Pending curation records: `0`
- Failed-window retry candidates: `0`
- High-priority rows needing source-window hardening: `0`

## Source Level

| Source | State | Sections | Accepted | Pending | Rejected | Hardening | Next action |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| cap413-extracted | needs_ingest | 12/51 | 79 | 0 | 4 | 0 | run ready-to-ingest batch for remaining manifest windows |
| egast-vfr-extracted | manifest_complete | 1/1 | 17 | 0 | 0 | 0 | eligible for H01/source-specific review and closure review |
| h01-extracted | needs_ingest | 4/46 | 24 | 0 | 1 | 0 | run ready-to-ingest batch for remaining manifest windows |
| icao4444-extracted | needs_ingest | 15/92 | 186 | 0 | 19 | 0 | run ready-to-ingest batch for remaining manifest windows |
| icao9432-extracted | needs_ingest | 4/23 | 22 | 0 | 6 | 0 | run ready-to-ingest batch for remaining manifest windows |
| safetysense22-extracted | manifest_complete | 1/1 | 15 | 0 | 1 | 0 | eligible for H01/source-specific review and closure review |
| sera-923-2012-extracted | needs_ingest | 9/17 | 73 | 0 | 3 | 0 | run ready-to-ingest batch for remaining manifest windows |
| slovenia-vfr-extracted | manifest_complete | 1/1 | 15 | 0 | 0 | 0 | eligible for H01/source-specific review and closure review |

## Scope / Non-Claims

### h01-extracted
- H01 records are English-side scoped for manifested AIC A 21/23 sections; they do not certify German/English translation equivalence.
- The H01 package should claim completeness only for manifested English-side windows that have landed and been curated.

## Section Level

| Source | Section | State | Accepted | Pending | Rejected | Lines |
| --- | --- | --- | ---: | ---: | ---: | --- |
| cap413-extracted | corrections_2_54_to_2_55 | landed | 2 | 0 | 1 | 6255-6272 |
| cap413-extracted | acknowledgement_receipt_2_56 | landed | 2 | 0 | 0 | 6273-6287 |
| cap413-extracted | transfer_content_2_57_to_2_59 | landed | 4 | 0 | 1 | 6288-6306 |
| cap413-extracted | frequency_change_permission_2_60_to_2_61 | landed | 5 | 0 | 0 | 6308-6324 |
| cap413-extracted | standby_monitor_conditional_contact_2_62_to_2_63 | landed | 4 | 0 | 0 | 6326-6379 |
| cap413-extracted | freecall_change_intention_2_64 | landed | 2 | 0 | 0 | 6381-6390 |
| cap413-extracted | clearance_issue_context_2_65_to_2_67 | landed | 9 | 0 | 1 | 6391-6428 |
| cap413-extracted | readback_2_68_2_71 | landed | 8 | 0 | 0 | 6429-6555 |
| cap413-extracted | unable_reclearance_critical_info_2_72_to_2_75 | landed | 11 | 0 | 1 | 6556-6604 |
| cap413-extracted | compliance_timing_2_82_to_2_87 | landed | 13 | 0 | 0 | 6703-6783 |
| cap413-extracted | air_ground_communication_failure_2_88 | landed | 14 | 0 | 0 | 6784-6832 |
| cap413-extracted | ground_air_communication_failure_2_89_to_2_91 | landed | 5 | 0 | 0 | 6833-6864 |
| cap413-extracted | ch4_4_1_to_4_4 | ready_to_ingest | 0 | 0 | 0 | 7906-7986 |
| cap413-extracted | ch4_4_5 | ready_to_ingest | 0 | 0 | 0 | 7987-8100 |
| cap413-extracted | ch4_4_6_to_4_10 | ready_to_ingest | 0 | 0 | 0 | 8101-8182 |
| cap413-extracted | ch4_4_11_to_4_12 | ready_to_ingest | 0 | 0 | 0 | 8183-8321 |
| cap413-extracted | ch4_4_13_to_4_16 | ready_to_ingest | 0 | 0 | 0 | 8322-8429 |
| cap413-extracted | ch4_4_17_to_4_23 | ready_to_ingest | 0 | 0 | 0 | 8430-8527 |
| cap413-extracted | ch4_4_24_to_4_25 | ready_to_ingest | 0 | 0 | 0 | 8528-8557 |
| cap413-extracted | ch4_4_26 | ready_to_ingest | 0 | 0 | 0 | 8558-8572 |
| cap413-extracted | ch4_4_27_to_4_30 | ready_to_ingest | 0 | 0 | 0 | 8573-8618 |
| cap413-extracted | ch4_4_31 | ready_to_ingest | 0 | 0 | 0 | 8619-8629 |
| cap413-extracted | ch4_4_32 | ready_to_ingest | 0 | 0 | 0 | 8630-8670 |
| cap413-extracted | ch4_4_33 | ready_to_ingest | 0 | 0 | 0 | 8671-8688 |
| cap413-extracted | ch4_4_34_to_4_39 | ready_to_ingest | 0 | 0 | 0 | 8689-8864 |
| cap413-extracted | ch4_4_40_to_4_44 | ready_to_ingest | 0 | 0 | 0 | 8865-8979 |
| cap413-extracted | ch4_4_45_to_4_49 | ready_to_ingest | 0 | 0 | 0 | 8980-9111 |
| cap413-extracted | ch4_4_50_to_4_60 | ready_to_ingest | 0 | 0 | 0 | 9112-9293 |
| cap413-extracted | ch4_4_61_to_4_69 | ready_to_ingest | 0 | 0 | 0 | 9294-9443 |
| cap413-extracted | ch4_4_70_to_4_83 | ready_to_ingest | 0 | 0 | 0 | 9444-9604 |
| cap413-extracted | ch4_4_84_part1 | ready_to_ingest | 0 | 0 | 0 | 9605-9733 |
| cap413-extracted | ch4_4_84_part2 | ready_to_ingest | 0 | 0 | 0 | 9734-9895 |
| cap413-extracted | ch4_4_84_part3 | ready_to_ingest | 0 | 0 | 0 | 9896-10089 |
| cap413-extracted | ch4_4_85 | ready_to_ingest | 0 | 0 | 0 | 10090-10225 |
| cap413-extracted | ch4_4_86_to_4_93 | ready_to_ingest | 0 | 0 | 0 | 10226-10350 |
| cap413-extracted | ch4_4_94_to_4_107 | ready_to_ingest | 0 | 0 | 0 | 10351-10494 |
| cap413-extracted | ch4_4_108_to_4_118 | ready_to_ingest | 0 | 0 | 0 | 10495-10677 |
| cap413-extracted | ch4_4_119_to_4_128 | ready_to_ingest | 0 | 0 | 0 | 10678-10864 |
| cap413-extracted | ch4_4_129_to_4_135 | ready_to_ingest | 0 | 0 | 0 | 10865-11025 |
| cap413-extracted | ch4_4_136_to_4_143 | ready_to_ingest | 0 | 0 | 0 | 11026-11143 |
| cap413-extracted | ch4_4_144_to_4_152 | ready_to_ingest | 0 | 0 | 0 | 11144-11283 |
| cap413-extracted | ch4_4_153 | ready_to_ingest | 0 | 0 | 0 | 11284-11472 |
| cap413-extracted | ch4_4_154_to_4_156_part1 | ready_to_ingest | 0 | 0 | 0 | 11473-11571 |
| cap413-extracted | ch4_4_154_to_4_156_part2 | ready_to_ingest | 0 | 0 | 0 | 11572-11708 |
| cap413-extracted | ch4_4_157_to_4_173 | ready_to_ingest | 0 | 0 | 0 | 11709-11857 |
| cap413-extracted | ch4_4_174_to_4_183 | ready_to_ingest | 0 | 0 | 0 | 11858-12027 |
| cap413-extracted | ch4_4_184_to_4_190 | ready_to_ingest | 0 | 0 | 0 | 12028-12166 |
| cap413-extracted | ch4_4_191_to_4_199 | ready_to_ingest | 0 | 0 | 0 | 12167-12280 |
| cap413-extracted | emergency_general_service_8_1_to_8_12 | ready_to_ingest | 0 | 0 | 0 | 16255-16429 |
| cap413-extracted | emergency_message_specials_8_13_to_8_20 | ready_to_ingest | 0 | 0 | 0 | 16430-16597 |
| cap413-extracted | emergency_practice_relay_silence_fuel_8_21_to_8_34 | ready_to_ingest | 0 | 0 | 0 | 16598-16841 |
| egast-vfr-extracted | readback_advisory | landed | 17 | 0 | 0 | 539-608 |
| h01-extracted | establishment_rtf_intro_3_3_to_3_3_1 | ready_to_ingest | 0 | 0 | 0 | 3494-3688 |
| h01-extracted | establishment_rtf_ifr_initial_calls_3_3_1_1 | ready_to_ingest | 0 | 0 | 0 | 3689-3779 |
| h01-extracted | establishment_rtf_vfr_initial_calls_3_3_1_2 | ready_to_ingest | 0 | 0 | 0 | 3780-3915 |
| h01-extracted | acknowledgement_3_8_1 | landed | 11 | 0 | 1 | 4197-4337 |
| h01-extracted | end_of_conversation_3_8_2 | landed | 1 | 0 | 0 | 4339-4348 |
| h01-extracted | corrections_3_8_3 | landed | 10 | 0 | 0 | 4359-4465 |
| h01-extracted | assurance_rtf_frequencies_3_9 | landed | 2 | 0 | 0 | 4467-4485 |
| h01-extracted | transfer_vhf_communications_3_10 | ready_to_ingest | 0 | 0 | 0 | 4631-4746 |
| h01-extracted | distress_urgency_general_4_1_1 | ready_to_ingest | 0 | 0 | 0 | 4827-4931 |
| h01-extracted | distress_aircraft_action_4_1_2_1 | ready_to_ingest | 0 | 0 | 0 | 4932-5055 |
| h01-extracted | distress_ats_action_4_1_2_2 | ready_to_ingest | 0 | 0 | 0 | 5056-5160 |
| h01-extracted | distress_silence_termination_4_1_2_3_to_4_1_2_5 | ready_to_ingest | 0 | 0 | 0 | 5161-5307 |
| h01-extracted | urgency_aircraft_action_4_1_3_1 | ready_to_ingest | 0 | 0 | 0 | 5308-5407 |
| h01-extracted | urgency_ats_action_4_1_3_2 | ready_to_ingest | 0 | 0 | 0 | 5408-5486 |
| h01-extracted | urgency_other_medical_emergency_frequency_4_1_3_3_to_4_1_4 | ready_to_ingest | 0 | 0 | 0 | 5487-5673 |
| h01-extracted | unlawful_interference_direction_finding_4_2_to_4_3 | ready_to_ingest | 0 | 0 | 0 | 5674-5819 |
| h01-extracted | voice_failure_air_ground_4_4_1 | ready_to_ingest | 0 | 0 | 0 | 5820-6022 |
| h01-extracted | voice_failure_ground_air_relay_4_4_2 | ready_to_ingest | 0 | 0 | 0 | 6023-6132 |
| h01-extracted | blocked_frequency_unauthorized_use_4_5_to_4_6 | ready_to_ingest | 0 | 0 | 0 | 6133-6264 |
| h01-extracted | minimum_fuel_transponder_emergency_4_7_to_4_8 | ready_to_ingest | 0 | 0 | 0 | 6265-6418 |
| h01-extracted | controlled_aerodromes_ground_start_5_3_1_1_to_5_3_1_3 | ready_to_ingest | 0 | 0 | 0 | 6822-6967 |
| h01-extracted | controlled_aerodromes_pushback_deice_5_3_1_4_to_5_3_1_5 | ready_to_ingest | 0 | 0 | 0 | 6968-7107 |
| h01-extracted | controlled_aerodromes_taxi_5_3_1_6_part1 | ready_to_ingest | 0 | 0 | 0 | 7108-7275 |
| h01-extracted | controlled_aerodromes_taxi_5_3_1_6_part2 | ready_to_ingest | 0 | 0 | 0 | 7276-7463 |
| h01-extracted | controlled_aerodromes_departure_instructions_5_3_1_7_to_5_3_1_8 | ready_to_ingest | 0 | 0 | 0 | 7464-7592 |
| h01-extracted | controlled_aerodromes_conditional_takeoff_prep_5_3_1_9_to_5_3_1_11 | ready_to_ingest | 0 | 0 | 0 | 7593-7735 |
| h01-extracted | controlled_aerodromes_takeoff_5_3_1_12_to_5_3_1_13 | ready_to_ingest | 0 | 0 | 0 | 7736-7857 |
| h01-extracted | controlled_aerodromes_airborne_entry_5_3_2_1_to_5_3_2_3 | ready_to_ingest | 0 | 0 | 0 | 7858-8001 |
| h01-extracted | controlled_aerodromes_circuit_reports_5_3_2_4_to_5_3_2_6 | ready_to_ingest | 0 | 0 | 0 | 8002-8114 |
| h01-extracted | controlled_aerodromes_landing_missed_5_3_2_7_to_5_3_2_11 | ready_to_ingest | 0 | 0 | 0 | 8115-8265 |
| h01-extracted | controlled_aerodromes_after_landing_5_3_3 | ready_to_ingest | 0 | 0 | 0 | 8266-8358 |
| h01-extracted | uncontrolled_aerodromes_5_4 | ready_to_ingest | 0 | 0 | 0 | 8359-8517 |
| h01-extracted | general_flight_handling_clearances_5_6_1 | ready_to_ingest | 0 | 0 | 0 | 9212-9395 |
| h01-extracted | general_transfer_callsign_holding_delay_5_6_2_to_5_6_5 | ready_to_ingest | 0 | 0 | 0 | 9396-9605 |
| h01-extracted | general_flight_plan_rmz_5_6_6_to_5_6_7 | ready_to_ingest | 0 | 0 | 0 | 9606-9715 |
| h01-extracted | general_flight_rules_channel_5_6_8_to_5_6_9 | ready_to_ingest | 0 | 0 | 0 | 9716-9857 |
| h01-extracted | general_traffic_information_5_6_10_1_to_5_6_10_3 | ready_to_ingest | 0 | 0 | 0 | 9858-10020 |
| h01-extracted | general_traffic_avoidance_5_6_10_4_to_5_6_10_6 | ready_to_ingest | 0 | 0 | 0 | 10021-10125 |
| h01-extracted | general_position_reporting_5_6_11 | ready_to_ingest | 0 | 0 | 0 | 10126-10320 |
| h01-extracted | general_levels_5_6_12 | ready_to_ingest | 0 | 0 | 0 | 10321-10445 |
| h01-extracted | general_level_changes_5_6_13 | ready_to_ingest | 0 | 0 | 0 | 10446-10617 |
| h01-extracted | general_speed_5_6_14 | ready_to_ingest | 0 | 0 | 0 | 10618-10725 |
| h01-extracted | general_warnings_df_intercept_misc_5_6_15_to_5_6_21 | ready_to_ingest | 0 | 0 | 0 | 10726-10907 |
| h01-extracted | contingency_comms_low_altitude_5_8_1_to_5_8_3 | ready_to_ingest | 0 | 0 | 0 | 12001-12086 |
| h01-extracted | contingency_emergency_descent_fuel_landing_gear_5_8_4_to_5_8_6 | ready_to_ingest | 0 | 0 | 0 | 12087-12181 |
| h01-extracted | contingency_misc_loss_position_blocked_runway_5_8_7_to_5_8_9 | ready_to_ingest | 0 | 0 | 0 | 12182-12285 |
| icao4444-extracted | atc_service_responsibility_4_1 | ready_to_ingest | 0 | 0 | 0 | 3027-3051 |
| icao4444-extracted | fis_alerting_responsibility_4_2 | ready_to_ingest | 0 | 0 | 0 | 3052-3070 |
| icao4444-extracted | control_responsibility_4_3_1 | landed | 3 | 0 | 0 | 3071-3085 |
| icao4444-extracted | transfer_4_3_2_1 | landed | 4 | 0 | 6 | 3086-3131 |
| icao4444-extracted | control_transfer_app_acc_sector_4_3_3_to_4_3_5 | landed | 6 | 0 | 0 | 3132-3164 |
| icao4444-extracted | clearance_scope_contents_4_5_1_to_4_5_7_4 | landed | 33 | 0 | 4 | 3265-3400 |
| icao4444-extracted | readback_4_5_7_5 | landed | 8 | 0 | 1 | 3401-3429 |
| icao4444-extracted | speed_control_4_6_1 | landed | 16 | 0 | 0 | 3431-3473 |
| icao4444-extracted | speed_control_methods_arrivals_sid_star_4_6_2_to_4_6_4 | ready_to_ingest | 0 | 0 | 0 | 3475-3532 |
| icao4444-extracted | vertical_speed_control_4_7 | ready_to_ingest | 0 | 0 | 0 | 3533-3568 |
| icao4444-extracted | wake_turbulence_categories_4_9 | ready_to_ingest | 0 | 0 | 0 | 3588-3627 |
| icao4444-extracted | altimeter_setting_4_10 | ready_to_ingest | 0 | 0 | 0 | 3628-3722 |
| icao4444-extracted | position_reporting_voice_4_11_1_to_4_11_3 | landed | 19 | 0 | 2 | 3723-3808 |
| icao4444-extracted | ads_c_position_reports_4_11_4_to_4_11_6 | ready_to_ingest | 0 | 0 | 0 | 3809-3890 |
| icao4444-extracted | systems_equipment_failure_4_14 | landed | 1 | 0 | 0 | 4116-4120 |
| icao4444-extracted | controlled_traffic_separation_5_2 | ready_to_ingest | 0 | 0 | 0 | 4213-4268 |
| icao4444-extracted | vertical_separation_5_3 | ready_to_ingest | 0 | 0 | 0 | 4269-4352 |
| icao4444-extracted | horizontal_lateral_application_5_4_1_1 | ready_to_ingest | 0 | 0 | 0 | 4353-4571 |
| icao4444-extracted | horizontal_lateral_criteria_nav_aids_5_4_1_2_1_to_5_4_1_2_1_2 | ready_to_ingest | 0 | 0 | 0 | 4572-4685 |
| icao4444-extracted | horizontal_lateral_criteria_rnav_parallel_5_4_1_2_1_3_to_5_4_1_2_1_6 | ready_to_ingest | 0 | 0 | 0 | 4686-4785 |
| icao4444-extracted | horizontal_lateral_criteria_intersecting_transition_5_4_1_2_1_7_to_5_4_1_2_1_10 | ready_to_ingest | 0 | 0 | 0 | 4786-4921 |
| icao4444-extracted | longitudinal_application_5_4_2_1 | ready_to_ingest | 0 | 0 | 0 | 4922-5002 |
| icao4444-extracted | longitudinal_time_same_level_5_4_2_2_1 | ready_to_ingest | 0 | 0 | 0 | 5003-5140 |
| icao4444-extracted | longitudinal_time_climb_descent_same_track_5_4_2_2_2 | ready_to_ingest | 0 | 0 | 0 | 5141-5189 |
| icao4444-extracted | longitudinal_time_reciprocal_tracks_5_4_2_2_3_part1 | ready_to_ingest | 0 | 0 | 0 | 5190-5272 |
| icao4444-extracted | longitudinal_time_reciprocal_tracks_5_4_2_2_3_part2 | ready_to_ingest | 0 | 0 | 0 | 5273-5416 |
| icao4444-extracted | longitudinal_dme_gnss_5_4_2_3 | ready_to_ingest | 0 | 0 | 0 | 5417-5540 |
| icao4444-extracted | longitudinal_mach_time_5_4_2_4 | ready_to_ingest | 0 | 0 | 0 | 5541-5685 |
| icao4444-extracted | longitudinal_mach_rnav_distance_5_4_2_5 | ready_to_ingest | 0 | 0 | 0 | 5686-5816 |
| icao4444-extracted | longitudinal_rnp_rnav_no_ads_c_5_4_2_6 | ready_to_ingest | 0 | 0 | 0 | 5817-5919 |
| icao4444-extracted | longitudinal_ads_b_ads_c_5_4_2_7_to_5_4_2_8 | ready_to_ingest | 0 | 0 | 0 | 5920-6075 |
| icao4444-extracted | performance_based_longitudinal_5_4_2_9 | ready_to_ingest | 0 | 0 | 0 | 6076-6291 |
| icao4444-extracted | departing_aircraft_minimum_separation_5_6 | ready_to_ingest | 0 | 0 | 0 | 6302-6351 |
| icao4444-extracted | departure_arrival_separation_5_7 | ready_to_ingest | 0 | 0 | 0 | 6352-6482 |
| icao4444-extracted | wake_turbulence_5_8 | landed | 14 | 0 | 0 | 6487-6584 |
| icao4444-extracted | wake_turbulence_opposite_direction_5_8_5 | ready_to_ingest | 0 | 0 | 0 | 6584-6636 |
| icao4444-extracted | essential_traffic_information_5_10 | ready_to_ingest | 0 | 0 | 0 | 6681-6724 |
| icao4444-extracted | reduction_separation_minima_5_11 | ready_to_ingest | 0 | 0 | 0 | 6725-6766 |
| icao4444-extracted | aerodrome_vicinity_reduced_separation_6_1 | ready_to_ingest | 0 | 0 | 0 | 6767-6782 |
| icao4444-extracted | essential_local_traffic_6_2 | ready_to_ingest | 0 | 0 | 0 | 6783-6796 |
| icao4444-extracted | departing_aircraft_procedures_6_3 | ready_to_ingest | 0 | 0 | 0 | 6797-6987 |
| icao4444-extracted | departing_aircraft_information_6_4 | ready_to_ingest | 0 | 0 | 0 | 6988-7012 |
| icao4444-extracted | arriving_aircraft_standard_clearances_6_5_1_to_6_5_2_3 | ready_to_ingest | 0 | 0 | 0 | 7013-7105 |
| icao4444-extracted | arriving_aircraft_star_visual_instrument_6_5_2_4_to_6_5_4 | ready_to_ingest | 0 | 0 | 0 | 7106-7206 |
| icao4444-extracted | arriving_aircraft_holding_6_5_5 | ready_to_ingest | 0 | 0 | 0 | 7207-7260 |
| icao4444-extracted | arriving_aircraft_sequence_eat_onward_6_5_6_to_6_5_8 | ready_to_ingest | 0 | 0 | 0 | 7261-7379 |
| icao4444-extracted | arriving_aircraft_information_6_6 | ready_to_ingest | 0 | 0 | 0 | 7380-7472 |
| icao4444-extracted | parallel_runways_departing_6_7_1_to_6_7_2 | ready_to_ingest | 0 | 0 | 0 | 7473-7538 |
| icao4444-extracted | parallel_runways_independent_types_requirements_6_7_3_1_to_6_7_3_2_part1 | ready_to_ingest | 0 | 0 | 0 | 7539-7653 |
| icao4444-extracted | parallel_runways_independent_requirements_6_7_3_2_part2 | ready_to_ingest | 0 | 0 | 0 | 7654-7795 |
| icao4444-extracted | parallel_runways_independent_monitoring_6_7_3_2_to_6_7_3_3 | ready_to_ingest | 0 | 0 | 0 | 7796-7873 |
| icao4444-extracted | parallel_runways_dependent_approaches_6_7_3_4 | ready_to_ingest | 0 | 0 | 0 | 7874-8070 |
| icao4444-extracted | parallel_runways_rnp_ar_6_7_3_5 | ready_to_ingest | 0 | 0 | 0 | 8071-8175 |
| icao4444-extracted | parallel_runways_segregated_6_7_3_6 | ready_to_ingest | 0 | 0 | 0 | 8176-8288 |
| icao4444-extracted | aerodrome_tower_functions_7_1 | ready_to_ingest | 0 | 0 | 0 | 8289-8386 |
| icao4444-extracted | runway_in_use_selection_7_2 | ready_to_ingest | 0 | 0 | 0 | 8387-8444 |
| icao4444-extracted | aerodrome_initial_call_7_3 | ready_to_ingest | 0 | 0 | 0 | 8445-8461 |
| icao4444-extracted | aerodrome_information_to_aircraft_7_4 | ready_to_ingest | 0 | 0 | 0 | 8462-8665 |
| icao4444-extracted | aerodrome_conditions_information_7_5 | ready_to_ingest | 0 | 0 | 0 | 8666-8715 |
| icao4444-extracted | aerodrome_traffic_7_6 | landed | 15 | 0 | 3 | 8716-8810 |
| icao4444-extracted | aerodrome_non_aircraft_traffic_7_6_3_2 | ready_to_ingest | 0 | 0 | 0 | 8811-8951 |
| icao4444-extracted | aerodrome_traffic_circuit_7_7 | ready_to_ingest | 0 | 0 | 0 | 8952-9022 |
| icao4444-extracted | aerodrome_arrival_departure_priority_7_8 | ready_to_ingest | 0 | 0 | 0 | 9023-9030 |
| icao4444-extracted | departing_aircraft_7_9 | landed | 19 | 0 | 0 | 9031-9110 |
| icao4444-extracted | arriving_aircraft_7_10 | landed | 13 | 0 | 1 | 9122-9166 |
| icao4444-extracted | reduced_runway_7_11 | landed | 18 | 0 | 0 | 9175-9245 |
| icao4444-extracted | reduced_runway_minima_values_7_11_7 | ready_to_ingest | 0 | 0 | 0 | 9246-9289 |
| icao4444-extracted | visual_surveillance_aerodrome_7_12 | ready_to_ingest | 0 | 0 | 0 | 9290-9308 |
| icao4444-extracted | low_visibility_operations_7_13 | ready_to_ingest | 0 | 0 | 0 | 9309-9396 |
| icao4444-extracted | suspend_vfr_operations_7_14 | ready_to_ingest | 0 | 0 | 0 | 9397-9424 |
| icao4444-extracted | special_vfr_authorization_7_15 | ready_to_ingest | 0 | 0 | 0 | 9425-9447 |
| icao4444-extracted | aeronautical_ground_lights_7_16 | ready_to_ingest | 0 | 0 | 0 | 9448-9554 |
| icao4444-extracted | hot_spots_7_17 | ready_to_ingest | 0 | 0 | 0 | 9555-9561 |
| icao4444-extracted | communications_procedures_12_1 | ready_to_ingest | 0 | 0 | 0 | 13391-13395 |
| icao4444-extracted | general_phraseologies_12_2 | ready_to_ingest | 0 | 0 | 0 | 13396-13468 |
| icao4444-extracted | atc_phraseologies_levels_12_3_1_1_to_12_3_1_2_part1 | ready_to_ingest | 0 | 0 | 0 | 13470-13638 |
| icao4444-extracted | atc_phraseologies_levels_minimum_fuel_12_3_1_2_to_12_3_1_3_part2 | ready_to_ingest | 0 | 0 | 0 | 13639-13783 |
| icao4444-extracted | phraseology_transfer_frequency_12_3_1_4 | landed | 10 | 0 | 1 | 13784-13833 |
| icao4444-extracted | atc_phraseologies_channel_callsign_traffic_12_3_1_5_to_12_3_1_7 | ready_to_ingest | 0 | 0 | 0 | 13834-14017 |
| icao4444-extracted | atc_phraseologies_met_position_reports_12_3_1_8_to_12_3_1_10 | ready_to_ingest | 0 | 0 | 0 | 14018-14125 |
| icao4444-extracted | atc_phraseologies_aerodrome_status_rvsm_gnss_nav_12_3_1_11_to_12_3_1_15 | ready_to_ingest | 0 | 0 | 0 | 14126-14256 |
| icao4444-extracted | phraseology_clearance_issuance_12_3_2_1 | landed | 7 | 0 | 1 | 14257-14293 |
| icao4444-extracted | area_phraseologies_route_level_separation_12_3_2_2_to_12_3_2_9 | ready_to_ingest | 0 | 0 | 0 | 14294-14449 |
| icao4444-extracted | approach_phraseologies_12_3_3 | ready_to_ingest | 0 | 0 | 0 | 14450-14681 |
| icao4444-extracted | aerodrome_phraseologies_start_taxi_12_3_4_1_to_12_3_4_7 | ready_to_ingest | 0 | 0 | 0 | 14682-14903 |
| icao4444-extracted | aerodrome_phraseologies_holding_takeoff_12_3_4_8_to_12_3_4_12 | ready_to_ingest | 0 | 0 | 0 | 14904-15101 |
| icao4444-extracted | aerodrome_phraseologies_circuit_landing_vacating_12_3_4_13_to_12_3_4_20 | ready_to_ingest | 0 | 0 | 0 | 15102-15322 |
| icao4444-extracted | coordination_phraseologies_12_3_5_to_12_3_6 | ready_to_ingest | 0 | 0 | 0 | 15323-15541 |
| icao4444-extracted | emergency_procedures_15_1 | ready_to_ingest | 0 | 0 | 0 | 17529-17745 |
| icao4444-extracted | air_ground_communications_failure_15_3 | ready_to_ingest | 0 | 0 | 0 | 17950-18093 |
| icao4444-extracted | assistance_to_vfr_flights_15_4 | ready_to_ingest | 0 | 0 | 0 | 18094-18174 |
| icao4444-extracted | atc_contingencies_15_6 | ready_to_ingest | 0 | 0 | 0 | 18383-18481 |
| icao9432-extracted | communications_2_8_1_en | landed | 5 | 0 | 0 | 3642-3697 |
| icao9432-extracted | transfer_communications_2_8_2_en | landed | 3 | 0 | 3 | 3830-3855 |
| icao9432-extracted | readback_2_8_3_en | landed | 8 | 0 | 0 | 3903-3950 |
| icao9432-extracted | readback_continuation_2_8_3_7_to_2_8_3_10_en | ready_to_ingest | 0 | 0 | 0 | 4011-4085 |
| icao9432-extracted | test_procedures_2_8_4_en | ready_to_ingest | 0 | 0 | 0 | 4086-4197 |
| icao9432-extracted | aerodrome_ch4_intro_start_4_1_to_4_2_en | ready_to_ingest | 0 | 0 | 0 | 4706-4818 |
| icao9432-extracted | pushback_powerback_4_3_en | ready_to_ingest | 0 | 0 | 0 | 4819-4916 |
| icao9432-extracted | taxi_4_4_en | landed | 6 | 0 | 3 | 4917-5139 |
| icao9432-extracted | takeoff_procedures_4_5_1_to_4_5_5_en | ready_to_ingest | 0 | 0 | 0 | 5140-5235 |
| icao9432-extracted | takeoff_procedures_4_5_6_to_4_5_7_en | ready_to_ingest | 0 | 0 | 0 | 5236-5329 |
| icao9432-extracted | takeoff_procedures_4_5_8_to_4_5_12_en | ready_to_ingest | 0 | 0 | 0 | 5330-5481 |
| icao9432-extracted | aerodrome_traffic_circuit_4_6_part1_en | ready_to_ingest | 0 | 0 | 0 | 5482-5566 |
| icao9432-extracted | aerodrome_traffic_circuit_4_6_part2_en | ready_to_ingest | 0 | 0 | 0 | 5567-5674 |
| icao9432-extracted | final_approach_landing_4_7_en | ready_to_ingest | 0 | 0 | 0 | 5675-5795 |
| icao9432-extracted | go_around_4_8_en | ready_to_ingest | 0 | 0 | 0 | 5796-5867 |
| icao9432-extracted | after_landing_4_9_en | ready_to_ingest | 0 | 0 | 0 | 5868-5932 |
| icao9432-extracted | essential_aerodrome_information_4_10_en | ready_to_ingest | 0 | 0 | 0 | 5933-6011 |
| icao9432-extracted | aerodrome_vehicles_intro_movement_5_1_to_5_2_en | ready_to_ingest | 0 | 0 | 0 | 6012-6185 |
| icao9432-extracted | aerodrome_vehicles_crossing_towing_5_3_to_5_4_en | ready_to_ingest | 0 | 0 | 0 | 6186-6347 |
| icao9432-extracted | distress_urgency_intro_9_1_en | ready_to_ingest | 0 | 0 | 0 | 8686-8808 |
| icao9432-extracted | distress_messages_9_2_en | ready_to_ingest | 0 | 0 | 0 | 8809-8993 |
| icao9432-extracted | urgency_emergency_descent_9_3_to_9_4_en | ready_to_ingest | 0 | 0 | 0 | 8994-9157 |
| icao9432-extracted | communications_failure_9_5_en | ready_to_ingest | 0 | 0 | 0 | 9158-9252 |
| safetysense22-extracted | readbacks | landed | 15 | 0 | 1 | 485-552 |
| sera-923-2012-extracted | aerodrome_operations_3225 | ready_to_ingest | 0 | 0 | 0 | 1441-1448 |
| sera-923-2012-extracted | communications_transponder_requirements_6005 | ready_to_ingest | 0 | 0 | 0 | 1883-1897 |
| sera-923-2012-extracted | ats_objectives_7001 | ready_to_ingest | 0 | 0 | 0 | 1900-1907 |
| sera-923-2012-extracted | ats_operator_coordination_7005 | ready_to_ingest | 0 | 0 | 0 | 1917-1925 |
| sera-923-2012-extracted | atc_service_8005 | landed | 14 | 0 | 0 | 1934-1973 |
| sera-923-2012-extracted | separation_minima_8010 | landed | 4 | 0 | 0 | 1974-1984 |
| sera-923-2012-extracted | clearances_8015_a_d | landed | 13 | 0 | 0 | 1985-2022 |
| sera-923-2012-extracted | readback_8015_e | landed | 7 | 0 | 0 | 2023-2037 |
| sera-923-2012-extracted | clearance_coordination_8015_f | landed | 9 | 0 | 1 | 2038-2071 |
| sera-923-2012-extracted | adherence_flight_plan_8020 | landed | 17 | 0 | 2 | 2072-2129 |
| sera-923-2012-extracted | position_reports_8025 | landed | 4 | 0 | 0 | 2130-2138 |
| sera-923-2012-extracted | termination_control_8030 | landed | 1 | 0 | 0 | 2139-2141 |
| sera-923-2012-extracted | communications_8035 | landed | 4 | 0 | 0 | 2142-2160 |
| sera-923-2012-extracted | flight_information_service_scope_9005 | ready_to_ingest | 0 | 0 | 0 | 2173-2192 |
| sera-923-2012-extracted | atis_9010 | ready_to_ingest | 0 | 0 | 0 | 2201-2330 |
| sera-923-2012-extracted | emergency_service_11005 | ready_to_ingest | 0 | 0 | 0 | 2361-2371 |
| sera-923-2012-extracted | voice_aircraft_observations_12015 | ready_to_ingest | 0 | 0 | 0 | 2840-2843 |
| slovenia-vfr-extracted | readback | landed | 15 | 0 | 0 | 524-565 |

## Tactical Level

- Ready-to-ingest windows: `185`
- Failed-window retry candidates: `0`
- Pending curation sections: `0`
- Source-window hardening rows: `0`
- Blocked ambiguous sources: `0`

### Failed Window Reconciliation

- Expected from plan: `none`
- Current computed count: `0`
- Reconciliation: No external failure ledger is wired into this queue; ready-to-ingest windows are tracked separately and must not be counted as failed retries.

### Excluded Future Sources

| Source | Evidence paths |
| --- | --- |
| nolan | research/txt/nolan-fundamentals-extracted.txt |
| eppls | not present in repo artifacts |
