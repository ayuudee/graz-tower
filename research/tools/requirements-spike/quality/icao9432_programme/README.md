# ICAO 9432 source-unit programme map
Generated from `research/tools/requirements-spike/registry/ollama_first/` by selecting `documentId = icao9432-extracted` and `lifecycle.state = accepted`.
## Inventory Summary
- Accepted source units: 166
- Non-programme ICAO 9432 units in registry: 21 rejected, 2 pending (189 total).
- Artifacts: `inventory.csv`, `inventory.json`, `classification.csv`, `classification.json`.

## Counts By Section
| Section | Accepted |
|---|---:|
| `aerodrome_ch4_intro_start_4_1_to_4_2_en` | 10 |
| `aerodrome_traffic_circuit_4_6_part1_en` | 4 |
| `aerodrome_traffic_circuit_4_6_part2_en` | 5 |
| `aerodrome_vehicles_crossing_towing_5_3_to_5_4_en` | 6 |
| `aerodrome_vehicles_intro_movement_5_1_to_5_2_en` | 6 |
| `after_landing_4_9_en` | 5 |
| `communications_2_8_1_en` | 5 |
| `communications_failure_9_5_en` | 14 |
| `distress_messages_9_2_en` | 8 |
| `distress_urgency_intro_9_1_en` | 18 |
| `essential_aerodrome_information_4_10_en` | 12 |
| `final_approach_landing_4_7_en` | 14 |
| `go_around_4_8_en` | 3 |
| `pushback_powerback_4_3_en` | 5 |
| `readback_2_8_3_en` | 8 |
| `readback_continuation_2_8_3_7_to_2_8_3_10_en` | 4 |
| `takeoff_procedures_4_5_1_to_4_5_5_en` | 7 |
| `takeoff_procedures_4_5_6_to_4_5_7_en` | 5 |
| `takeoff_procedures_4_5_8_to_4_5_12_en` | 7 |
| `taxi_4_4_en` | 6 |
| `test_procedures_2_8_4_en` | 5 |
| `transfer_communications_2_8_2_en` | 3 |
| `urgency_emergency_descent_9_3_to_9_4_en` | 6 |

## Counts By Chunk
| Chunk | Title | Units |
|---|---|---:|
| `chunk-01-comms-readback-transfer` | Communications, transfer, readback | 20 |
| `chunk-02-radio-procedures-and-policy` | Radio procedures and critical-phase policy | 15 |
| `chunk-03-ground-movement` | Ground movement: pushback and taxi | 11 |
| `chunk-04-runway-departure` | Runway entry, line-up, and takeoff | 19 |
| `chunk-05-circuit-arrival-landing` | Circuit, final approach, and landing | 23 |
| `chunk-06-go-around-after-landing-aerodrome-info` | Go-around, after landing, and aerodrome information | 20 |
| `chunk-07-vehicles-and-towing` | Vehicles, crossing, and towing | 12 |
| `chunk-08-distress-urgency-comms-failure` | Distress, urgency, emergency descent, and communication failure | 46 |

## First-Pass Classification Counts
| Dimension | Value | Units |
|---|---|---:|
| normative_kind | `information` | 9 |
| normative_kind | `may` | 14 |
| normative_kind | `must` | 41 |
| normative_kind | `must-not` | 8 |
| normative_kind | `phraseology` | 56 |
| normative_kind | `policy` | 38 |
| current_testability | `needs-observation-fact` | 16 |
| current_testability | `needs-policy-type` | 17 |
| current_testability | `needs-sim-model` | 52 |
| current_testability | `phraseology-later` | 56 |
| current_testability | `testable-now` | 25 |
| blocking_item | `EMERGENCY-1` | 38 |
| blocking_item | `FN33-MODEL-1` | 1 |
| blocking_item | `FN43-GAP-1` | 11 |
| blocking_item | `FN43-GAP-2` | 2 |
| blocking_item | `FN44-GAP-1` | 2 |
| blocking_item | `PHRASE-1` | 56 |
| blocking_item | `POLICY-1` | 17 |
| blocking_item | `PUSHBACK-1` | 3 |
| blocking_item | `VEHICLE-1` | 11 |
| blocking_item | `none` | 25 |

## Chunk Plan
### chunk-01-comms-readback-transfer: Communications, transfer, readback
- Source units: 20
- Areas: communications-establishment, frequency-transfer, readback-hearback
- Testability: needs-observation-fact: 3, needs-policy-type: 2, phraseology-later: 7, testable-now: 8
- Initial authoring target: about 9 high-level source-mapped tests, allowing one test to cite multiple closely related units where the same scenario genuinely proves them.
- Recommended first chunk: central to the existing DSL, partly protocol-level, and likely to expose policy/observation boundaries without requiring new aircraft physics.
- Policy concepts flagged: ClearanceTimingPolicy, OperationalGuidancePolicy
- Known blockers: FN33-MODEL-1, FN44-GAP-1, PHRASE-1, POLICY-1

### chunk-02-radio-procedures-and-policy: Radio procedures and critical-phase policy
- Source units: 15
- Areas: critical-phase-radio-discipline, radio-test-procedures
- Testability: needs-observation-fact: 2, needs-policy-type: 1, phraseology-later: 12
- Initial authoring target: about 7 high-level source-mapped tests, allowing one test to cite multiple closely related units where the same scenario genuinely proves them.
- Policy concepts flagged: CriticalPhaseTransmissionPolicy, OperationalGuidancePolicy
- Known blockers: FN43-GAP-2, PHRASE-1, POLICY-1

### chunk-03-ground-movement: Ground movement: pushback and taxi
- Source units: 11
- Areas: pushback-powerback, taxi-ground-movement
- Testability: needs-policy-type: 2, needs-sim-model: 3, phraseology-later: 2, testable-now: 4
- Initial authoring target: about 5 high-level source-mapped tests, allowing one test to cite multiple closely related units where the same scenario genuinely proves them.
- Policy concepts flagged: LocalProcedurePolicy, OperationalGuidancePolicy
- Known blockers: PHRASE-1, POLICY-1, PUSHBACK-1

### chunk-04-runway-departure: Runway entry, line-up, and takeoff
- Source units: 19
- Areas: runway-entry-line-up-takeoff
- Testability: needs-policy-type: 5, phraseology-later: 8, testable-now: 6
- Initial authoring target: about 9 high-level source-mapped tests, allowing one test to cite multiple closely related units where the same scenario genuinely proves them.
- Policy concepts flagged: ControllerInterventionPolicy, OperationalGuidancePolicy
- Known blockers: PHRASE-1, POLICY-1

### chunk-05-circuit-arrival-landing: Circuit, final approach, and landing
- Source units: 23
- Areas: circuit-approach-sequencing, final-approach-landing
- Testability: needs-policy-type: 4, phraseology-later: 14, testable-now: 5
- Initial authoring target: about 10 high-level source-mapped tests, allowing one test to cite multiple closely related units where the same scenario genuinely proves them.
- Policy concepts flagged: ControllerInterventionPolicy, LocalProcedurePolicy, OperationalGuidancePolicy
- Known blockers: PHRASE-1, POLICY-1

### chunk-06-go-around-after-landing-aerodrome-info: Go-around, after landing, and aerodrome information
- Source units: 20
- Areas: after-landing-vacating, essential-aerodrome-information, go-around-missed-approach
- Testability: needs-observation-fact: 11, needs-policy-type: 3, phraseology-later: 4, testable-now: 2
- Initial authoring target: about 9 high-level source-mapped tests, allowing one test to cite multiple closely related units where the same scenario genuinely proves them.
- Policy concepts flagged: ClearanceTimingPolicy, OperationalGuidancePolicy
- Known blockers: FN43-GAP-1, PHRASE-1, POLICY-1

### chunk-07-vehicles-and-towing: Vehicles, crossing, and towing
- Source units: 12
- Areas: vehicles-crossing-towing, vehicles-ground-movement
- Testability: needs-sim-model: 11, phraseology-later: 1
- Initial authoring target: about 5 high-level source-mapped tests, allowing one test to cite multiple closely related units where the same scenario genuinely proves them.
- Policy concepts flagged: OperationalGuidancePolicy
- Known blockers: PHRASE-1, VEHICLE-1

### chunk-08-distress-urgency-comms-failure: Distress, urgency, emergency descent, and communication failure
- Source units: 46
- Areas: communications-failure, distress-urgency, emergency-descent
- Testability: needs-sim-model: 38, phraseology-later: 8
- Initial authoring target: about 12 high-level source-mapped tests, allowing one test to cite multiple closely related units where the same scenario genuinely proves them.
- Policy concepts flagged: ClearanceTimingPolicy, ControllerInterventionPolicy, OperationalGuidancePolicy
- Known blockers: EMERGENCY-1, PHRASE-1

## Review Considerations
- FP / type safety: the CSV/JSON map is intentionally loose; permanent test code should promote `normative_kind`, `current_testability`, `likely_target`, and policy concepts to closed typed vocabularies.
- Test architecture: `testable-now` means the current evidence DSL appears able to author a loud source-mapped test; it does not mean the implementation will pass. Expected failing tests belong in chunk test-authoring epics, with implementation repair separate.
- Impact: policy-sensitive units are marked so tests do not accidentally enshrine one local procedure as universal. Observation gaps remain explicit via `blocking_item`.
- Operational correctness: claims must still be checked against the accepted source-unit quote and ICAO 9432 section before test authoring. The map is a planning aid, not a substitute for citation review.
