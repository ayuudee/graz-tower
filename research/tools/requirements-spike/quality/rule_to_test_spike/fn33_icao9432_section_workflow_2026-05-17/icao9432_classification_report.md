# ICAO 9432 classification report

## Scope

This report classifies all 166 accepted `icao9432-extracted` source units after
the first executable readback slice. It is intentionally conservative: existing
goldens or typed structures are not counted as source-unit coverage unless this
spike bound the source unit to an explicit test or evidence target.

## Totals

| Status | Count |
| --- | ---: |
| `covered` | 4 |
| `duplicate_support` | 1 |
| `new_case_needed` | 43 |
| `blocked_by_existing_red` | 3 |
| `blocked_by_model_gap` | 41 |
| `needs_domain_review` | 59 |
| `not_sim_scope` | 15 |

No source units remain `pending_classification`.

## Section outcomes

- `readback_2_8_3_en`: first executable slice. Four source units are covered
  by source-backed behavior tests, one is type-enforced supporting evidence,
  and three expose model gaps.
- `go_around_4_8_en`: blocked by existing red/fragile go-around verification
  history from FN31/FN32. Do not use this as the next slice until that baseline
  is unquestionably green.
- `taxi_4_4_en`, `takeoff_*`, `after_landing_4_9_en`,
  `aerodrome_traffic_circuit_*`, `final_approach_landing_4_7_en`, and
  `essential_aerodrome_information_4_10_en`: high simulator relevance; these
  should become future source-backed slices.
- `communications_*` and `transfer_communications_2_8_2_en`: relevant to
  handoff/contact-frequency behavior, but most individual accepted records are
  examples or review-only records that need domain review before executable
  treatment.
- `distress_*`, `urgency_*`, and `communications_failure_9_5_en`: future-high
  relevance, but they require explicit emergency/lost-comms scenario modeling.
- `aerodrome_vehicles_*` and `test_procedures_2_8_4_en`: mostly outside the
  current aircraft simulator scope.

## Existing-test relationship

The source has many records that overlap existing golden behavior:

- taxi and runway operations overlap G0/G1/G2;
- circuit, final, touch-and-go, landing, and after-landing records overlap
  G1/G3a shapes;
- handoff/contact-frequency records overlap G2 cross-aerodrome behavior;
- go-around records overlap G3a/FN31/FN32.

Those overlaps are not marked `covered` in the ledger unless a source-unit id is
explicitly cited by a conformance case. This preserves the distinction between
"the simulator probably does something related" and "this source unit is
trace-evidence for this behavior."

## Validation

```sh
jq -s '[.[] | select(.status == "pending_classification")] | length' \
  research/tools/requirements-spike/quality/rule_to_test_spike/fn33_icao9432_section_workflow_2026-05-17/icao9432_ledger.jsonl
```

Result: `0`.

```sh
jq -s '[.[].status] | group_by(.) | map({status: .[0], count: length})' \
  research/tools/requirements-spike/quality/rule_to_test_spike/fn33_icao9432_section_workflow_2026-05-17/icao9432_ledger.jsonl
```

Result: the totals shown above.

## Finding

The ledger is useful because it prevents vague progress claims. After one
source-backed slice, only 4 of 166 records are genuinely executable-and-bound.
The next useful slices are not "more extraction"; they are targeted behavior
families: taxi/runway, final/landing, handoff communications, and emergency
communications.
