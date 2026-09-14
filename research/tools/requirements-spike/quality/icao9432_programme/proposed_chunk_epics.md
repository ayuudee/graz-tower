# Proposed ICAO 9432 chunk epics

These are proposed follow-on Flow epics after fn-46. Each epic is a
test-authoring epic unless explicitly stated otherwise. Behaviour repairs stay
in separate implementation epics.

## Recommended Order

1. **Communications, transfer, readback**
   - Scope: `communications_2_8_1_en`,
     `transfer_communications_2_8_2_en`, `readback_2_8_3_en`,
     `readback_continuation_2_8_3_7_to_2_8_3_10_en`.
   - Units: 20.
   - Initial target: about 9 source-mapped tests.
   - Expected fallout: `FN44-GAP-1`, `POLICY-1`, `PHRASE-1`, and
     existing FN33 timing/workload model gaps.
   - Why first: it is central, partly testable now, and exercises the firewall
     without needing new vehicle or emergency domains.

2. **Ground movement: pushback and taxi**
   - Scope: `pushback_powerback_4_3_en`, `taxi_4_4_en`.
   - Units: 11.
   - Initial target: about 5 source-mapped tests.
   - Expected fallout: `PUSHBACK-1`, `POLICY-1`, `PHRASE-1`.
   - Why second: taxi already has existing scenario support, while pushback
     exposes a contained model gap.

3. **Runway entry, line-up, and takeoff**
   - Scope: `takeoff_procedures_4_5_1_to_4_5_5_en`,
     `takeoff_procedures_4_5_6_to_4_5_7_en`,
     `takeoff_procedures_4_5_8_to_4_5_12_en`.
   - Units: 19.
   - Initial target: about 9 source-mapped tests.
   - Expected fallout: `POLICY-1`, `PHRASE-1`.
   - Why third: high operational value and close to existing runway lifecycle
     tests.

4. **Circuit, final approach, and landing**
   - Scope: `aerodrome_traffic_circuit_4_6_part1_en`,
     `aerodrome_traffic_circuit_4_6_part2_en`,
     `final_approach_landing_4_7_en`.
   - Units: 23.
   - Initial target: about 10 source-mapped tests.
   - Expected fallout: `POLICY-1`, `PHRASE-1`.
   - Why fourth: broad overlap with current goldens, but more policy and
     sequencing ambiguity than runway departure.

5. **Go-around, after landing, and aerodrome information**
   - Scope: `go_around_4_8_en`, `after_landing_4_9_en`,
     `essential_aerodrome_information_4_10_en`.
   - Units: 20.
   - Initial target: about 9 source-mapped tests.
   - Expected fallout: `FN43-GAP-1`, `POLICY-1`, `PHRASE-1`.
   - Why fifth: go-around and after-landing paths now have strong goldens, but
     aerodrome information needs observation facts.

6. **Radio procedures and critical-phase policy**
   - Scope: `test_procedures_2_8_4_en`,
     `aerodrome_ch4_intro_start_4_1_to_4_2_en`.
   - Units: 15.
   - Initial target: about 7 source-mapped tests.
   - Expected fallout: `FN43-GAP-2`, `POLICY-1`, `PHRASE-1`.
   - Why sixth: critical-phase radio discipline is important but evidence
     projections are currently thin.

7. **Vehicles, crossing, and towing**
   - Scope: `aerodrome_vehicles_intro_movement_5_1_to_5_2_en`,
     `aerodrome_vehicles_crossing_towing_5_3_to_5_4_en`.
   - Units: 12.
   - Initial target: about 5 source-mapped tests or expected-gap records.
   - Expected fallout: `VEHICLE-1`, `PHRASE-1`.
   - Why late: this likely needs a new actor domain before meaningful tests can
     pass.

8. **Distress, urgency, emergency descent, and communication failure**
   - Scope: `distress_urgency_intro_9_1_en`, `distress_messages_9_2_en`,
     `urgency_emergency_descent_9_3_to_9_4_en`,
     `communications_failure_9_5_en`.
   - Units: 46.
   - Initial target: about 12 source-mapped tests or expected-gap records.
   - Expected fallout: `EMERGENCY-1`, `PHRASE-1`.
   - Why last: it is the largest area and currently requires emergency/failure
     scenario primitives.

## First Epic Draft

Title: **ICAO 9432 communications, transfer, and readback source-mapped tests**

Goal: Author source-mapped tests for the testable-now portion of chunk 01 and
loud expected-gap tests/reports for the blocked portion, without fixing
implementation behaviour in the same epic.

Acceptance:
- Inventory all 20 chunk-01 units in the epic plan.
- Re-check each accepted quote before test authoring.
- Author high-level source-mapped tests for readback and communication
  establishment units whose evidence is available.
- For transfer/frequency and policy-sensitive units, author expected-gap records
  or failing tests that cite the missing concept (`FN44-GAP-1`,
  `POLICY-1`, or `PHRASE-1`).
- Produce a per-source coverage report: passing, failing, expected gap, or
  deferred with explicit reason.
- Do not change controller/pilot/sim behaviour except to fix defects in the
  evidence DSL that prevent the tests from being expressed.
