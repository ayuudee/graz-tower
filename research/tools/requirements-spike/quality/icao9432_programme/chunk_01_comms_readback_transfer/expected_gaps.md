# Chunk 01 expected gaps and blocked source units

This file records chunk-01 source units that should not be converted into passing tests in fn-47. Each remains visible for out-of-band repair or later phraseology/policy work.

## Counts

| State | Units |
|---|---:|
| `expected-gap` | 1 |
| `not-applicable` | 1 |
| `phraseology-later` | 4 |
| `policy-blocked` | 2 |
| `split: supported rendered controller templates covered; cancellation wording phraseology-later` | 1 |
| `split: supported rendered readbacks covered; remaining templates phraseology-later` | 1 |

## Records

| Source unit | State | Blocker | Claim | Handoff |
|---|---|---|---|---|
| `icao9432-extracted::communications_2_8_1_en::5efac97fddfd54ca` | `phraseology-later` | `PHRASE-1` | When an aircraft wishes to broadcast information to aircraft in its vicinity, the message should be prefaced by the call "ALL S... | Wait for rendered-transmission phraseology facts. |
| `icao9432-extracted::communications_2_8_1_en::8b0487b183cd02cf` | `policy-blocked` | `POLICY-1` | No reply is expected to such general calls unless individual stations are subsequently called upon to acknowledge receipt. | Introduce explicit policy concept before asserting one correct behaviour. |
| `icao9432-extracted::communications_2_8_1_en::a685cef087951878` | `phraseology-later` | `PHRASE-1` | When establishing communications, an aircraft should use the full call sign of both the aircraft and the aeronautical station. | Wait for rendered-transmission phraseology facts. |
| `icao9432-extracted::communications_2_8_1_en::b7acdc88125f1510` | `phraseology-later` | `PHRASE-1` | When a ground station wishes to broadcast information, the message should be prefaced by the call "ALL STATIONS". | Wait for rendered-transmission phraseology facts. |
| `icao9432-extracted::readback_2_8_3_en::36e6ad16cffe8726` | `policy-blocked` | `POLICY-1` | Whenever possible, controllers should pass a route clearance to an aircraft before start-up. | Introduce explicit policy concept before asserting one correct behaviour. |
| `icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2` | `expected-gap` | `FN33-MODEL-1` | Controllers should pass a clearance slowly and clearly, avoid passing clearances during complicated taxiing, and on no occasion... | Needs clearance timing/workload evidence or model support. |
| `icao9432-extracted::readback_2_8_3_en::f06dfa1cefd2d649` | `split: supported rendered controller templates covered; cancellation wording phraseology-later` | `PHRASE-1` | The words 'TAKE OFF' are used only when an aircraft is cleared for take-off, or when canceling a take-off clearance; at other t... | fn-82 covers supported rendered controller templates; wait for cancellation wording and unsupported template coverage. |
| `icao9432-extracted::readback_2_8_3_en::fe3b04ca9c3384d9` | `phraseology-later` | `PHRASE-1` | An ATC route clearance is not an instruction to take off or enter an active runway. | Wait for rendered-transmission phraseology facts. |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::4c808d67d281ff71` | `split: supported rendered readbacks covered; remaining templates phraseology-later` | `PHRASE-1` | An aircraft should terminate the read-back by its call sign. | fn-81 covers `LineUpReadback` and `FrequencyReadback`; wait for rendered phraseology support for other readback templates. |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::ce25c18f1b44a6a8` | `not-applicable` | `none` | See: APPENDIX 1 DIFFERENCES FROM ICAO RADIOTELEPHONY PROCEDURES | No behaviour claim for this simulator chunk. |

## Review Considerations

- FP / type safety: these gap states are planning records; permanent expected gaps in code should use typed `EvidenceGapId`s.
- Test architecture: a blocked unit is not skipped. It remains part of chunk coverage with an explicit blocker and handoff.
- Impact: COMMS-1 has moved to covered-green via fn-50, the contact-frequency phraseology row moved to rendered phraseology via fn-78, the readback-termination row is split by fn-81 for supported rendered readback templates, and the TAKE OFF word-use row is split by fn-82 for supported rendered controller templates; remaining entries are phraseology, policy, model-gap, or not-applicable records.
- Operational correctness: phraseology and policy units remain separate from structural readback semantics.
