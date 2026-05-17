# FN33 ICAO 9432 section workflow artifacts

## Purpose

This directory starts the FN33 single-source, section-by-section conformance
workflow spike for `icao9432-extracted`.

ICAO 9432 was chosen because it has enough operational variety to exercise the
workflow while remaining bounded: 166 accepted source units across 23 sections.
It covers communications, readback, taxi, takeoff, circuit, landing,
go-around, after-landing, vehicles, and abnormal/emergency material.

## Artifacts

- `icao9432_ledger.jsonl` - one row per accepted source unit. Initial status is
  `pending_classification` until the section-by-section pass assigns a concrete
  workflow status.
- `icao9432_sections.jsonl` - one row per source section, with section-level
  status and simulator-relevance fields ready for classification.
- `icao9432_section_summary.json` - generated section counts from the ledger.

## Ledger fields

Each `icao9432_ledger.jsonl` row records:

- `document_id`
- `section_id`
- `source_unit_id`
- `source_item_ids`
- `claim_text`
- `modality`
- `authority_class`
- `requirement_kind`
- `testability`
- `verification_mode`
- `promotion_hint`
- `status`
- `test_or_evidence_target`
- `notes`
- `provenance`

The intended classification statuses after the initial inventory are:

- `covered`
- `new_case_needed`
- `blocked_by_existing_red`
- `blocked_by_model_gap`
- `not_sim_scope`
- `duplicate_support`
- `needs_domain_review`

## Validation

Generated from accepted registry JSON under:

`research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted`

Validation commands run on 2026-05-17:

```sh
wc -l \
  research/tools/requirements-spike/quality/rule_to_test_spike/fn33_icao9432_section_workflow_2026-05-17/icao9432_ledger.jsonl \
  research/tools/requirements-spike/quality/rule_to_test_spike/fn33_icao9432_section_workflow_2026-05-17/icao9432_sections.jsonl
```

Result:

```text
166 icao9432_ledger.jsonl
 23 icao9432_sections.jsonl
```

```sh
jq -s '[.[].source_unit_id] as $ids | {
  total: ($ids | length),
  unique: ($ids | unique | length),
  duplicates: (($ids | length) - ($ids | unique | length))
}' research/tools/requirements-spike/quality/rule_to_test_spike/fn33_icao9432_section_workflow_2026-05-17/icao9432_ledger.jsonl
```

Result:

```json
{
  "total": 166,
  "unique": 166,
  "duplicates": 0
}
```

## First slice note

The next task should select the first executable section slice from this ledger.
The strongest candidates are `taxi_4_4_en`, `readback_2_8_3_en`, or
`final_approach_landing_4_7_en`: each is operationally meaningful and likely to
teach more about evidence binding than a purely emergency-message or
communications-failure section. `go_around_4_8_en` is intentionally not the
first choice because FN31 already found a red go-around sequencing baseline.
