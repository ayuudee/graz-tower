# Clearance And Communications Window Hardening

Date: 2026-04-30

## Purpose

This is the non-LLM hardening pass for the clearance and communications
contract seam. It turns the one-page seam plan into exact source windows before
any manifest widening or extraction.

The output is intentionally small: a recommended first batch of source windows,
the current windows that the batch relies on, and explicit deferrals for
relevant but lower-yield or boundary-risky material.

## Artifacts

- `clearance_comms_window_hardening.csv`
- `proposed_manifest_additions.json`
- `ingestion_status_2026-05-01.md`

## Recommended Manifest Additions

Recommended first-batch additions: 24 windows.

The nominal plan said "around 10-15" windows. The hardening pass originally
kept that shape with 16 windows, but the first live ingestion of the combined
CAP 413 §§2.54-2.67 window produced 21 candidates while the pipeline reviews at
most 20. That is an unacceptable truncation risk, so the CAP 413 window was
split. The first split of CAP 413 §§2.57-2.64 still returned truncated invalid
JSON during live ingestion, so the transfer material is split again into four
smaller windows. Live ingestion of CAP 413 §§2.82-2.91 then showed that the
compliance/failure window was also too heavy, so it is split into compliance
timing, air-ground communication failure, and ground-air communication failure
windows. One extra tiny ICAO Doc 4444 §4.14 window is
also included because it is only five source lines and directly supports
communications/systems failure. If a strict cap is required, defer
`systems_equipment_failure_4_14`; do not recombine the CAP 413 split windows.

### Binding / Procedural Core

- SERA.8015(f), lines 2038-2071: `clearance_coordination_8015_f`.
- SERA.8020, lines 2072-2129: `adherence_flight_plan_8020`.
- SERA.8025, lines 2130-2138: `position_reports_8025`.
- SERA.8030, lines 2139-2141: `termination_control_8030`.
- SERA.8035, lines 2142-2160: `communications_8035`.
- ICAO Doc 4444 §4.3.1, lines 3071-3085:
  `control_responsibility_4_3_1`.
- ICAO Doc 4444 §§4.3.3-4.3.5, lines 3132-3164:
  `control_transfer_app_acc_sector_4_3_3_to_4_3_5`.
- ICAO Doc 4444 §§4.5.1-4.5.7.4, lines 3265-3400:
  `clearance_scope_contents_4_5_1_to_4_5_7_4`.
- ICAO Doc 4444 §§4.11.1-4.11.3, lines 3723-3808:
  `position_reporting_voice_4_11_1_to_4_11_3`.
- ICAO Doc 4444 §4.14, lines 4116-4120:
  `systems_equipment_failure_4_14`.

### Phraseology / Guidance Support

- ICAO Doc 4444 §12.3.1.4, lines 13784-13833:
  `phraseology_transfer_frequency_12_3_1_4`.
- ICAO Doc 4444 §12.3.2.1, lines 14257-14293:
  `phraseology_clearance_issuance_12_3_2_1`.
- ICAO Doc 9432 §2.8.2, English-only lines 3830-3855:
  `transfer_communications_2_8_2_en`.
- CAP 413 §§2.54-2.55, lines 6255-6272:
  `corrections_2_54_to_2_55`.
- CAP 413 §2.56, lines 6273-6287:
  `acknowledgement_receipt_2_56`.
- CAP 413 §§2.57-2.59, lines 6288-6306:
  `transfer_content_2_57_to_2_59`.
- CAP 413 §§2.60-2.61, lines 6308-6324:
  `frequency_change_permission_2_60_to_2_61`.
- CAP 413 §§2.62-2.63, lines 6326-6379:
  `standby_monitor_conditional_contact_2_62_to_2_63`.
- CAP 413 §2.64, lines 6381-6390:
  `freecall_change_intention_2_64`.
- CAP 413 §§2.65-2.67, lines 6391-6428:
  `clearance_issue_context_2_65_to_2_67`.
- CAP 413 §§2.72-2.75, lines 6556-6604:
  `unable_reclearance_critical_info_2_72_to_2_75`.
- CAP 413 §§2.82-2.87, lines 6703-6783:
  `compliance_timing_2_82_to_2_87`.
- CAP 413 §2.88, lines 6784-6832:
  `air_ground_communication_failure_2_88`.
- CAP 413 §§2.89-2.91, lines 6833-6864:
  `ground_air_communication_failure_2_89_to_2_91`.

## Existing Anchors Relied On

These are already in the declared slice and should not be duplicated:

- SERA.8005, lines 1934-1973.
- SERA.8010, lines 1974-1984.
- SERA.8015(a)-(d), lines 1985-2022.
- SERA.8015(e), lines 2023-2037.
- ICAO Doc 4444 §4.3.2.1, lines 3086-3131.
- ICAO Doc 4444 §4.5.7.5, lines 3401-3429.
- ICAO Doc 9432 §2.8.1, lines 3642-3697.
- ICAO Doc 9432 §2.8.3, lines 3903-3950.
- CAP 413 §§2.68-2.71, lines 6429-6555.
- H01 §3.8.1, lines 4197-4337.
- H01 §3.8.2, lines 4339-4348.
- H01 §3.8.3, lines 4359-4500.

## Ingestion Status

As of 2026-05-01, the first-batch source windows are hardened and manifested,
but only part of the batch is landed in the registry.

The 2026-04-30 v2 ingestion run is quarantined. It timed out on the old
combined CAP 413 §§2.82-2.91 window and then every SERA / ICAO Doc 4444 /
ICAO Doc 9432 section failed with Ollama connection timeouts.

Clean v3 promotion succeeded for the highest-value 80/20 subset:

- SERA.8015(f), SERA.8020, SERA.8025, SERA.8030, and SERA.8035:
  30 accepted records landed, 6 retained pending.
- CAP 413 §§2.82-2.87, §2.88, and §§2.89-2.91:
  12 accepted records landed, 20 retained pending.

Post-promotion reproducibility audit passed with 327 registry records audited
and 0 mismatches. The remaining ICAO Doc 4444, ICAO Doc 9432, and CAP 413
§§2.54-2.75 first-batch windows are manifest-only until a clean run promotes
them.

## Explicit Deferrals

- ICAO Doc 4444 §§12.3.1.9-12.3.1.10, lines 14018-14068:
  position-reporting phraseology. Defer unless the first-batch omission review
  shows that SERA.8025 and ICAO Doc 4444 §4.11 are insufficient.
- H01 §3.3, lines 3494-3915: initial-contact guidance. Relevant, but long,
  bilingual, and lower priority than the binding/procedural core.
- H01 §3.9, lines 4467-4630: continuous watch / frequencies. Relevant, but the
  current H01 §3.8.3 manifest window runs through line 4500, overlapping the
  §3.9 heading and early content. Do not manifest until that overlap is handled.
- H01 §3.10, lines 4631-4746: transfer of VHF communications. Relevant, but
  first-batch coverage is better served by ICAO Doc 9432 §2.8.2, ICAO Doc 4444
  §12.3.1.4, and CAP 413 §§2.57-2.64.

## Review After Hardening

Boundary review:

- SERA windows are exact section-to-next-section ranges and contain English
  legal text only.
- ICAO Doc 4444 §4.3 and §4.5 additions are intentionally non-overlapping with
  the existing §4.3.2.1 and §4.5.7.5 manifest windows.
- ICAO Doc 9432 §2.8.2 stops at line 3855, before page furniture and the Polish
  translation begin.
- CAP 413 windows are split around the existing §§2.68-2.71 readback window to
  avoid duplicate manifest coverage. §§2.54-2.67 are further split after live
  ingestion showed the combined window exceeded the candidate review cap and
  the first transfer split still produced invalid truncated JSON. §§2.82-2.91
  are split because the combined compliance/failure window produced a large
  candidate set and mixed two procedural topics.
- H01 §3.9 is not clean enough for this batch because the existing H01 §3.8.3
  manifest window overlaps it.

Coverage review:

- The selected windows cover clearance source/scope/content, adherence and
  amended clearance, readback/correction/acknowledgement context, transfer of
  control/communications/frequency, position reporting, continuous watch, and
  communications failure.
- The main known omission after this batch is local Austrian phraseology for
  initial contact and VHF transfer. That omission is explicit and lower priority
  because higher-authority or broader guidance windows cover the shared
  contract first.

## Review Considerations

FP / type safety: no domain-code change is made by this hardening pass. If the
next step changes manifest schema, source role and authority should remain
explicit data or deterministic policy, not prose-only inference.

Test architecture: the next step should run the existing ingestion/promotion
quality gates plus an omission review over every selected authoritative window,
including any source window that produces no accepted facts.

Impact: this hardening package prevents the widening batch from becoming a
document-ingestion exercise. Every selected window has a seam reason and every
deferred relevant window has a stated reason.

Operational correctness: regulatory and phraseology claims above are tied to
the cited source sections and line ranges: SERA.8015(f), SERA.8020, SERA.8025,
SERA.8030, SERA.8035; ICAO Doc 4444 §§4.3, 4.5, 4.11, 4.14, 12.3.1.4, and
12.3.2.1; ICAO Doc 9432 §2.8.2; CAP 413 §§2.54-2.75 and §§2.82-2.91.
