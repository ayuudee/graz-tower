# Source Document Inventory - refreshed 2026-05-01

## Answer

The current requirements-spike ingestion manifest does not represent all local source text.
It references 8 document manifests and 46 selected line-range sections.
Those windows cover 2431 unique lines out of 90910 lines in the 8 manifested text extracts (2.67%).
Across all local text extracts in `research/txt/`, they cover 2431 unique lines out of 141767 (1.71%).
That leaves 88479 lines inside the manifested source texts outside the selected windows, plus 50857 lines in local text extracts not referenced by any `documents/*.json` manifest.

So `46` is only the count of selected ingestion windows. It is not the count of available documents, document sections, or source material.

## Corpus Check

- Repo-wide PDFs found: 25.
- Requirements-source PDFs under `research/pdf/`: 9; all are inventoried here.
- Non-requirements PDFs outside `research/pdf/`: 16; listed in `repo_pdf_supplement.csv`.
- Source text extracts under `research/txt/`: 12; all are inventoried here.
- Per-document ingestion manifests found: 8.
- Manifest source paths missing from disk: 0.
- Filesystem text extracts not in this inventory: 0.
- Filesystem PDFs not in this inventory: 0.

## Text Extract Inventory

| Document id | Role | Lines | Manifest sections | Covered lines | Covered % | Notes |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| cap413-extracted | manifested_full_extract | 21957 | 12 | 509 | 2.32% | Full CAP 413 extract used by documents/cap413.json. |
| cap413-aerodrome-chapter | unmanifested_subset_extract | 2701 | 0 | 0 | 0.00% | Local excerpt/subset file; not referenced by documents/*.json. |
| egast-vfr-extracted | manifested_full_extract | 2154 | 1 | 70 | 3.25% | Full EGAST guide extract used by documents/egast.json. |
| h01-extracted | manifested_full_extract | 16402 | 3 | 293 | 1.79% | Full bilingual AIC extract used by documents/h01.json. |
| h01-aerodrome-chapter | unmanifested_subset_extract | 2301 | 0 | 0 | 0.00% | Local excerpt/subset file; not referenced by documents/*.json. |
| icao4444-extracted | manifested_full_extract | 31591 | 15 | 869 | 2.75% | Full Doc 4444 extract used by documents/icao4444.json. |
| icao9432-extracted | manifested_full_extract | 10131 | 4 | 353 | 3.48% | Bilingual English/Polish extract used by documents/icao9432.json. |
| icao9432-aerodrome-chapter | unmanifested_subset_extract | 3001 | 0 | 0 | 0.00% | Local excerpt/subset file; not referenced by documents/*.json. |
| nolan-fundamentals-extracted | unmanifested_full_extract | 42854 | 0 | 0 | 0.00% | Separate local textbook extract; not referenced by documents/*.json. |
| safetysense22-extracted | manifested_full_extract | 2002 | 1 | 68 | 3.40% | Full SafetySense leaflet extract used by documents/safetysense22.json. |
| sera-923-2012-extracted | manifested_full_extract | 4899 | 9 | 227 | 4.63% | Full CELEX extract used by documents/sera.json. |
| slovenia-vfr-extracted | manifested_full_extract | 1774 | 1 | 42 | 2.37% | Full Slovenia VFR guide extract used by documents/slovenia-vfr.json. |

## PDF Map

| PDF | Pages | Text extract(s) | Identity check |
| --- | ---: | --- | --- |
| `research/pdf/249.pdf` | 372 | cap413-extracted; cap413-aerodrome-chapter | pdftotext first page: 'Radiotelephony Manual CAP 413'; pdfinfo pages=372. |
| `research/pdf/CELEX_32012R0923_EN_TXT.pdf` | 66 | sera-923-2012-extracted | pdftotext first page: Commission Implementing Regulation (EU) No 923/2012; pdfinfo pages=66. |
| `research/pdf/EGAST_Radiotelephony-guide-for-VFR-pilots.pdf` | 25 | egast-vfr-extracted | pdftotext first page: 'A Guide to phraseology FOR GENERAL AVIATION PILOTS IN EUROPE'; pdfinfo pages=25. |
| `research/pdf/H_01_LO_Circ_2023_A_21_en_2023-11-16_1111697.pdf` | 148 | h01-extracted; h01-aerodrome-chapter | pdftotext first page: AIC A 21/23, effective date 28 DEC 2023; pdfinfo pages=148. |
| `research/pdf/Zalacznik1.pdf` | 194 | icao9432-extracted; icao9432-aerodrome-chapter | pdftotext first page: Doc 9432 Manual of Radiotelephony; pdfinfo pages=194. |
| `research/pdf/filea0162087916d2b1.pdf` | 40 | slovenia-vfr-extracted | pdftotext first page: Contents with 12 VFR phraseology chapters; pdfinfo pages=40. |
| `research/pdf/fundies.pdf` | 674 | nolan-fundamentals-extracted | pdftotext first page: Fundamentals of Air Traffic Control, Fifth Edition; pdfinfo pages=674. |
| `research/pdf/icao-4444.pdf` | 476 | icao4444-extracted | pdftotext first page: Doc 4444 PANS-ATM, Sixteenth Edition 2016; pdfinfo pages=476. |
| `research/pdf/safetysense22-radiotelephony.pdf` | 30 | safetysense22-extracted | pdftotext first page: Radiotelephony for General Aviation Pilots SSL 22; pdfinfo pages=30. |

## Repo PDFs Outside Requirements Source Scope

These are real repository PDFs, but they are airport chart/source-data support files or OFM package metadata. They are not represented by `research/txt/` extracts or `documents/*.json` requirements-spike manifests.

| PDF | Category | Notes |
| --- | --- | --- |
| `data/charts/LJMB/LJMB.pdf` | airport_chart | Airport chart/source-data support PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/charts/LOWG/LOWG_Approach_ILS CAT II-III or LOC 34C_04092025.pdf` | airport_chart | Airport chart/source-data support PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/charts/LOWG/LOWG_Approach_RNP RWY16C_04092025.pdf` | airport_chart | Airport chart/source-data support PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/charts/LOWG/LOWG_Approach_RNP RWY34C_04092025.pdf` | airport_chart | Airport chart/source-data support PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/charts/LOWG/LOWG_Approach_VOR16C_04092025.pdf` | airport_chart | Airport chart/source-data support PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/charts/LOWG/LOWG_Approach_VOR34C_04092025.pdf` | airport_chart | Airport chart/source-data support PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/charts/LOWG/LOWG_Arrival_RNAV transition 16C & 34C_05092024.pdf` | airport_chart | Airport chart/source-data support PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/charts/LOWG/LOWG_Arrival_STAR_05092024.pdf` | airport_chart | Airport chart/source-data support PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/charts/LOWG/LOWG_Departure_SID16C_07092023.pdf` | airport_chart | Airport chart/source-data support PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/charts/LOWG/LOWG_Departure_SID34C_07092023.pdf` | airport_chart | Airport chart/source-data support PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/charts/LOWG/LOWG_Ground_Aerodrome Overview_30102025.pdf` | airport_chart | Airport chart/source-data support PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/charts/LOWG/LOWG_VFR_VFR Chart_02102025.pdf` | airport_chart | Airport chart/source-data support PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/ofm/austria/ofmx_extracted/ofmx_lo/amendments.pdf` | ofm_package_metadata | OFM package readme/amendments PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/ofm/austria/ofmx_extracted/ofmx_lo/readme.pdf` | ofm_package_metadata | OFM package readme/amendments PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/ofm/slovenia/ofmx_extracted/ofmx_lj/amendments.pdf` | ofm_package_metadata | OFM package readme/amendments PDF outside requirements-spike law/phraseology ingestion scope. |
| `data/ofm/slovenia/ofmx_extracted/ofmx_lj/readme.pdf` | ofm_package_metadata | OFM package readme/amendments PDF outside requirements-spike law/phraseology ingestion scope. |

## Manifest Sections

| Document id | Section id | Lines | Family |
| --- | --- | ---: | --- |
| cap413-extracted | corrections_2_54_to_2_55 | 6255-6272 (18) | cap413_clearance_comms_guidance_family |
| cap413-extracted | acknowledgement_receipt_2_56 | 6273-6287 (15) | cap413_clearance_comms_guidance_family |
| cap413-extracted | transfer_content_2_57_to_2_59 | 6288-6306 (19) | cap413_clearance_comms_guidance_family |
| cap413-extracted | frequency_change_permission_2_60_to_2_61 | 6308-6324 (17) | cap413_clearance_comms_guidance_family |
| cap413-extracted | standby_monitor_conditional_contact_2_62_to_2_63 | 6326-6379 (54) | cap413_clearance_comms_guidance_family |
| cap413-extracted | freecall_change_intention_2_64 | 6381-6390 (10) | cap413_clearance_comms_guidance_family |
| cap413-extracted | clearance_issue_context_2_65_to_2_67 | 6391-6428 (38) | cap413_clearance_comms_guidance_family |
| cap413-extracted | readback_2_68_2_71 | 6429-6555 (127) | cap413_readback_family |
| cap413-extracted | unable_reclearance_critical_info_2_72_to_2_75 | 6556-6604 (49) | cap413_clearance_comms_guidance_family |
| cap413-extracted | compliance_timing_2_82_to_2_87 | 6703-6783 (81) | cap413_clearance_comms_guidance_family |
| cap413-extracted | air_ground_communication_failure_2_88 | 6784-6832 (49) | cap413_clearance_comms_guidance_family |
| cap413-extracted | ground_air_communication_failure_2_89_to_2_91 | 6833-6864 (32) | cap413_clearance_comms_guidance_family |
| egast-vfr-extracted | readback_advisory | 539-608 (70) | egast_vfr_readback_family |
| h01-extracted | acknowledgement_3_8_1 | 4197-4337 (141) | h01_readback_family |
| h01-extracted | end_of_conversation_3_8_2 | 4339-4348 (10) | h01_end_of_conversation_family |
| h01-extracted | corrections_3_8_3 | 4359-4500 (142) | h01_corrections_family |
| icao4444-extracted | transfer_4_3_2_1 | 3086-3131 (46) | icao4444_transfer_family |
| icao4444-extracted | control_responsibility_4_3_1 | 3071-3085 (15) | icao4444_clearance_comms_contract_family |
| icao4444-extracted | control_transfer_app_acc_sector_4_3_3_to_4_3_5 | 3132-3164 (33) | icao4444_clearance_comms_contract_family |
| icao4444-extracted | readback_4_5_7_5 | 3401-3429 (29) | icao4444_readback_family |
| icao4444-extracted | clearance_scope_contents_4_5_1_to_4_5_7_4 | 3265-3400 (136) | icao4444_clearance_comms_contract_family |
| icao4444-extracted | departing_aircraft_7_9 | 9031-9110 (80) | icao4444_departing_aircraft_family |
| icao4444-extracted | arriving_aircraft_7_10 | 9122-9166 (45) | icao4444_arriving_aircraft_family |
| icao4444-extracted | speed_control_4_6_1 | 3431-3473 (43) | icao4444_speed_control_family |
| icao4444-extracted | wake_turbulence_5_8 | 6487-6584 (98) | icao4444_wake_turbulence_family |
| icao4444-extracted | reduced_runway_7_11 | 9175-9245 (71) | icao4444_reduced_runway_family |
| icao4444-extracted | aerodrome_traffic_7_6 | 8716-8810 (95) | icao4444_aerodrome_traffic_family |
| icao4444-extracted | position_reporting_voice_4_11_1_to_4_11_3 | 3723-3808 (86) | icao4444_clearance_comms_contract_family |
| icao4444-extracted | systems_equipment_failure_4_14 | 4116-4120 (5) | icao4444_clearance_comms_contract_family |
| icao4444-extracted | phraseology_transfer_frequency_12_3_1_4 | 13784-13833 (50) | icao4444_clearance_comms_phraseology_family |
| icao4444-extracted | phraseology_clearance_issuance_12_3_2_1 | 14257-14293 (37) | icao4444_clearance_comms_phraseology_family |
| icao9432-extracted | readback_2_8_3_en | 3903-3950 (48) | icao9432_readback_family |
| icao9432-extracted | communications_2_8_1_en | 3642-3697 (56) | icao9432_communications_family |
| icao9432-extracted | transfer_communications_2_8_2_en | 3830-3855 (26) | icao9432_clearance_comms_phraseology_family |
| icao9432-extracted | taxi_4_4_en | 4917-5139 (223) | icao9432_taxi_family |
| safetysense22-extracted | readbacks | 485-552 (68) | safetysense22_readback_family |
| sera-923-2012-extracted | readback_8015_e | 2023-2037 (15) | sera_readback_family |
| sera-923-2012-extracted | atc_service_8005 | 1934-1973 (40) | sera_atc_service_family |
| sera-923-2012-extracted | separation_minima_8010 | 1974-1984 (11) | sera_separation_minima_family |
| sera-923-2012-extracted | clearances_8015_a_d | 1985-2022 (38) | sera_clearances_family |
| sera-923-2012-extracted | clearance_coordination_8015_f | 2038-2071 (34) | sera_clearance_comms_contract_family |
| sera-923-2012-extracted | adherence_flight_plan_8020 | 2072-2129 (58) | sera_clearance_comms_contract_family |
| sera-923-2012-extracted | position_reports_8025 | 2130-2138 (9) | sera_clearance_comms_contract_family |
| sera-923-2012-extracted | termination_control_8030 | 2139-2141 (3) | sera_clearance_comms_contract_family |
| sera-923-2012-extracted | communications_8035 | 2142-2160 (19) | sera_clearance_comms_contract_family |
| slovenia-vfr-extracted | readback | 524-565 (42) | slovenia_vfr_readback_family |

## Checks Performed

1. Filesystem pass: enumerated `research/pdf/*.pdf` and `research/txt/*.txt`; every file is represented in this inventory.
2. Pipeline-manifest pass: parsed every `research/tools/requirements-spike/documents/*.json`; every manifest `sourcePath` exists on disk.
3. PDF identity pass: checked `pdfinfo` page counts and `pdftotext` first-page identity for each PDF/text mapping.
4. Coverage pass: merged manifest line ranges per document and counted unique covered lines against full text-extract line counts.

## Reliability Notes

- High confidence: this is exhaustive for repo-local source files under `research/pdf/` and `research/txt/` as of 2026-04-29.
- High confidence: the 46-section count is exactly the current `documents/*.json` line-window count.
- High confidence: the line-coverage percentages are deterministic counts over the checked-in text extracts.
- Line counts are text line records, including an unterminated final line where present; this is the right basis for manifest line windows.
- The all-`research/txt/` denominator is a file-inventory metric, not a deduplicated semantic corpus metric; the three aerodrome excerpt files overlap their full-source extracts.
- Boundary: this does not claim that no external source documents exist elsewhere; it claims the local source corpus currently present in the repository is fully inventoried.
