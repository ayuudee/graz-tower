# Source Progress Report

Generated: `2026-05-09T11:06:16Z`

## Overall Counts

- Included current-frame sources: `8`
- Excluded future sources: `2`
- Manifest windows: `47`
- Landed manifest windows: `47`
- Ready-to-ingest manifest windows: `0`
- Pending curation records: `0`
- Failed-window retry candidates: `0`
- High-priority rows needing source-window hardening: `82`

## Source Level

| Source | State | Sections | Accepted | Pending | Rejected | Hardening | Next action |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| cap413-extracted | manifest_complete_with_hardening_backlog | 12/12 | 79 | 0 | 4 | 4 | decide whether hardening rows are current package blockers or scoped non-claims |
| egast-vfr-extracted | manifest_complete | 1/1 | 17 | 0 | 0 | 0 | eligible for H01/source-specific review and closure review |
| h01-extracted | manifest_complete_with_hardening_backlog | 4/4 | 24 | 0 | 1 | 10 | carry scoped non-claims into package; harden remaining rows before claiming full source completeness |
| icao4444-extracted | manifest_complete_with_hardening_backlog | 15/15 | 186 | 0 | 19 | 47 | decide whether hardening rows are current package blockers or scoped non-claims |
| icao9432-extracted | manifest_complete_with_hardening_backlog | 4/4 | 22 | 0 | 6 | 13 | decide whether hardening rows are current package blockers or scoped non-claims |
| safetysense22-extracted | manifest_complete | 1/1 | 15 | 0 | 1 | 0 | eligible for H01/source-specific review and closure review |
| sera-923-2012-extracted | manifest_complete_with_hardening_backlog | 9/9 | 73 | 0 | 3 | 8 | decide whether hardening rows are current package blockers or scoped non-claims |
| slovenia-vfr-extracted | manifest_complete | 1/1 | 15 | 0 | 0 | 0 | eligible for H01/source-specific review and closure review |

## Scope / Non-Claims

### h01-extracted
- Current H01 records are English-side scoped for manifested AIC A 21/23 sections 3.8.1-3.8.3; they do not certify German/English translation equivalence.
- The H01 package must not claim full-document completeness while the source-window hardening backlog still contains high-priority H01 sections outside the manifested 3.8 windows.

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
| egast-vfr-extracted | readback_advisory | landed | 17 | 0 | 0 | 539-608 |
| h01-extracted | acknowledgement_3_8_1 | landed | 11 | 0 | 1 | 4197-4337 |
| h01-extracted | end_of_conversation_3_8_2 | landed | 1 | 0 | 0 | 4339-4348 |
| h01-extracted | corrections_3_8_3 | landed | 10 | 0 | 0 | 4359-4465 |
| h01-extracted | assurance_rtf_frequencies_3_9 | landed | 2 | 0 | 0 | 4467-4485 |
| icao4444-extracted | transfer_4_3_2_1 | landed | 4 | 0 | 6 | 3086-3131 |
| icao4444-extracted | control_responsibility_4_3_1 | landed | 3 | 0 | 0 | 3071-3085 |
| icao4444-extracted | control_transfer_app_acc_sector_4_3_3_to_4_3_5 | landed | 6 | 0 | 0 | 3132-3164 |
| icao4444-extracted | readback_4_5_7_5 | landed | 8 | 0 | 1 | 3401-3429 |
| icao4444-extracted | clearance_scope_contents_4_5_1_to_4_5_7_4 | landed | 33 | 0 | 4 | 3265-3400 |
| icao4444-extracted | departing_aircraft_7_9 | landed | 19 | 0 | 0 | 9031-9110 |
| icao4444-extracted | arriving_aircraft_7_10 | landed | 13 | 0 | 1 | 9122-9166 |
| icao4444-extracted | speed_control_4_6_1 | landed | 16 | 0 | 0 | 3431-3473 |
| icao4444-extracted | wake_turbulence_5_8 | landed | 14 | 0 | 0 | 6487-6584 |
| icao4444-extracted | reduced_runway_7_11 | landed | 18 | 0 | 0 | 9175-9245 |
| icao4444-extracted | aerodrome_traffic_7_6 | landed | 15 | 0 | 3 | 8716-8810 |
| icao4444-extracted | position_reporting_voice_4_11_1_to_4_11_3 | landed | 19 | 0 | 2 | 3723-3808 |
| icao4444-extracted | systems_equipment_failure_4_14 | landed | 1 | 0 | 0 | 4116-4120 |
| icao4444-extracted | phraseology_transfer_frequency_12_3_1_4 | landed | 10 | 0 | 1 | 13784-13833 |
| icao4444-extracted | phraseology_clearance_issuance_12_3_2_1 | landed | 7 | 0 | 1 | 14257-14293 |
| icao9432-extracted | readback_2_8_3_en | landed | 8 | 0 | 0 | 3903-3950 |
| icao9432-extracted | communications_2_8_1_en | landed | 5 | 0 | 0 | 3642-3697 |
| icao9432-extracted | transfer_communications_2_8_2_en | landed | 3 | 0 | 3 | 3830-3855 |
| icao9432-extracted | taxi_4_4_en | landed | 6 | 0 | 3 | 4917-5139 |
| safetysense22-extracted | readbacks | landed | 15 | 0 | 1 | 485-552 |
| sera-923-2012-extracted | readback_8015_e | landed | 7 | 0 | 0 | 2023-2037 |
| sera-923-2012-extracted | atc_service_8005 | landed | 14 | 0 | 0 | 1934-1973 |
| sera-923-2012-extracted | separation_minima_8010 | landed | 4 | 0 | 0 | 1974-1984 |
| sera-923-2012-extracted | clearances_8015_a_d | landed | 13 | 0 | 0 | 1985-2022 |
| sera-923-2012-extracted | clearance_coordination_8015_f | landed | 9 | 0 | 1 | 2038-2071 |
| sera-923-2012-extracted | adherence_flight_plan_8020 | landed | 17 | 0 | 2 | 2072-2129 |
| sera-923-2012-extracted | position_reports_8025 | landed | 4 | 0 | 0 | 2130-2138 |
| sera-923-2012-extracted | termination_control_8030 | landed | 1 | 0 | 0 | 2139-2141 |
| sera-923-2012-extracted | communications_8035 | landed | 4 | 0 | 0 | 2142-2160 |
| slovenia-vfr-extracted | readback | landed | 15 | 0 | 0 | 524-565 |

## Tactical Level

- Ready-to-ingest windows: `0`
- Failed-window retry candidates: `0`
- Pending curation sections: `0`
- Source-window hardening rows: `82`
- Blocked ambiguous sources: `0`

### Failed Window Reconciliation

- Expected from plan: `14`
- Current computed count: `0`
- Reconciliation: Current manifest/registry state has no ready-to-ingest or pending-only manifest windows; the earlier expected count is stale unless an external failure ledger is introduced.

### Excluded Future Sources

| Source | Evidence paths |
| --- | --- |
| nolan | research/txt/nolan-fundamentals-extracted.txt |
| eppls | not present in repo artifacts |
