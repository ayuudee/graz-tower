# Chunk 01 source plan: communications, transfer, readback

Source: `classification.csv` rows with `chunk_id = chunk-01-comms-readback-transfer`. Registry re-check confirms all 20 rows still have `lifecycle.state = accepted` and exact source quotes.

- Source units: 20
- Sections: `communications_2_8_1_en`, `transfer_communications_2_8_2_en`, `readback_2_8_3_en`, `readback_continuation_2_8_3_7_to_2_8_3_10_en`.
- Artifacts: `source_plan.csv`, `source_plan.json`.

## Planned Coverage Counts

| State | Units |
|---|---:|
| `candidate-test` | 9 |
| `expected-gap` | 1 |
| `not-applicable` | 1 |
| `phraseology-later` | 5 |
| `policy-blocked` | 2 |
| `rendered-phraseology` | 1 |
| `split: supported rendered readbacks covered; remaining templates phraseology-later` | 1 |

## Unit Plan

| Source unit | Section | Claim | Planned state | Blocker | Notes |
|---|---|---|---|---|---|
| `icao9432-extracted::communications_2_8_1_en::0a964f42b6100596` | `communications_2_8_1_en` | If there is doubt that a message has been correctly received, a repetition of the messages shall be requested either in full or in part. | `candidate-test` | `none` | Covered by fn-50 real radio-overlap evidence: stepped-on controller transmission produces ReceptionDoubt resolved by pilot SayAgain. |
| `icao9432-extracted::communications_2_8_1_en::5efac97fddfd54ca` | `communications_2_8_1_en` | When an aircraft wishes to broadcast information to aircraft in its vicinity, the message should be prefaced by the call "ALL STATIONS". | `phraseology-later` | `PHRASE-1` | Block on PHRASE-1; typed semantics alone do not prove rendered RT phraseology. |
| `icao9432-extracted::communications_2_8_1_en::8b0487b183cd02cf` | `communications_2_8_1_en` | No reply is expected to such general calls unless individual stations are subsequently called upon to acknowledge receipt. | `policy-blocked` | `POLICY-1` | Block on typed policy concept OperationalGuidancePolicy; do not assert one universal behaviour. |
| `icao9432-extracted::communications_2_8_1_en::a685cef087951878` | `communications_2_8_1_en` | When establishing communications, an aircraft should use the full call sign of both the aircraft and the aeronautical station. | `phraseology-later` | `PHRASE-1` | Block on PHRASE-1; typed semantics alone do not prove rendered RT phraseology. |
| `icao9432-extracted::communications_2_8_1_en::b7acdc88125f1510` | `communications_2_8_1_en` | When a ground station wishes to broadcast information, the message should be prefaced by the call "ALL STATIONS". | `phraseology-later` | `PHRASE-1` | Block on PHRASE-1; typed semantics alone do not prove rendered RT phraseology. |
| `icao9432-extracted::readback_2_8_3_en::15940532b37f8528` | `readback_2_8_3_en` | Clearances and instructions to enter, land on, take off from, hold short of, cross and backtrack on any runway shall always be read back. | `candidate-test` | `none` | Author protocolEvidence source-mapped test if current DSL can express readback/hearback semantic evidence. |
| `icao9432-extracted::readback_2_8_3_en::25c245bc4728ed60` | `readback_2_8_3_en` | Runway-in-use, altimeter settings, SSR codes, level instructions, heading and speed instructions, and transition levels shall always be read back. | `candidate-test` | `none` | Author protocolEvidence source-mapped test if current DSL can express readback/hearback semantic evidence. |
| `icao9432-extracted::readback_2_8_3_en::36e6ad16cffe8726` | `readback_2_8_3_en` | Whenever possible, controllers should pass a route clearance to an aircraft before start-up. | `policy-blocked` | `POLICY-1` | Block on typed policy concept ClearanceTimingPolicy; do not assert one universal behaviour. |
| `icao9432-extracted::readback_2_8_3_en::4b6ece953649da07` | `readback_2_8_3_en` | Other clearances or instructions, including conditional clearances, shall be read back or acknowledged in a manner to clearly indicate that they ha... | `candidate-test` | `none` | Author protocolEvidence source-mapped test if current DSL can express readback/hearback semantic evidence. |
| `icao9432-extracted::readback_2_8_3_en::58594a8ee6243296` | `readback_2_8_3_en` | ATC route clearances shall always be read back. | `candidate-test` | `none` | Author protocolEvidence source-mapped test if current DSL can express readback/hearback semantic evidence. |
| `icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2` | `readback_2_8_3_en` | Controllers should pass a clearance slowly and clearly, avoid passing clearances during complicated taxiing, and on no occasion should a clearance ... | `expected-gap` | `FN33-MODEL-1` | Record expected gap for FN33-MODEL-1 with accepted source id and quote. |
| `icao9432-extracted::readback_2_8_3_en::f06dfa1cefd2d649` | `readback_2_8_3_en` | The words 'TAKE OFF' are used only when an aircraft is cleared for take-off, or when canceling a take-off clearance; at other times, the word 'DEPA... | `phraseology-later` | `PHRASE-1` | Block on PHRASE-1; typed semantics alone do not prove rendered RT phraseology. |
| `icao9432-extracted::readback_2_8_3_en::fe3b04ca9c3384d9` | `readback_2_8_3_en` | An ATC route clearance is not an instruction to take off or enter an active runway. | `phraseology-later` | `PHRASE-1` | Block on PHRASE-1; typed semantics alone do not prove rendered RT phraseology. |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::17e1dfdf4ce57253` | `readback_continuation_2_8_3_7_to_2_8_3_10_en` | The controller shall listen to the read-back to ascertain that the clearance or instruction has been correctly acknowledged by the flight crew. | `candidate-test` | `none` | Author protocolEvidence source-mapped test if current DSL can express readback/hearback semantic evidence. |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::4c808d67d281ff71` | `readback_continuation_2_8_3_7_to_2_8_3_10_en` | An aircraft should terminate the read-back by its call sign. | `split: supported rendered readbacks covered; remaining templates phraseology-later` | `PHRASE-1` | fn-81 proves `LineUpReadback` and `FrequencyReadback` rendered pilot readback facts terminate with the aircraft callsign token. Unsupported readback templates remain blocked on PHRASE-1. |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::ace4ab7ff5d53a66` | `readback_continuation_2_8_3_7_to_2_8_3_10_en` | The controller shall take immediate action to correct any discrepancies revealed by the read-back. | `candidate-test` | `none` | Author protocolEvidence source-mapped test if current DSL can express readback/hearback semantic evidence. |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::ce25c18f1b44a6a8` | `readback_continuation_2_8_3_7_to_2_8_3_10_en` | See: APPENDIX 1 DIFFERENCES FROM ICAO RADIOTELEPHONY PROCEDURES | `not-applicable` | `none` | Accepted source unit is an appendix cross-reference, not a behaviour claim for this simulator chunk. |
| `icao9432-extracted::transfer_communications_2_8_2_en::40382df156ad071e` | `transfer_communications_2_8_2_en` | An aircraft shall be advised by the appropriate aeronautical station to change from one radio frequency to another in accordance with agreed proced... | `candidate-test` | `none` | Covered by Icao9432Chunk01FrequencyTransferEvidenceTest controller-advised path. |
| `icao9432-extracted::transfer_communications_2_8_2_en::96720e821bf926cc` | `transfer_communications_2_8_2_en` | Phraseology for frequency change includes 'CONTACT [Unit] [Frequency]' and readback 'Frequency Callsign'. | `rendered-phraseology` | `none` | Covered by fn-78 rendered phraseology evidence for controller CONTACT [unit] [frequency] and pilot [frequency] [callsign] readback. |
| `icao9432-extracted::transfer_communications_2_8_2_en::b49ae03cbbb2d538` | `transfer_communications_2_8_2_en` | In the absence of such advice, the aircraft shall notify the aeronautical station before such a change takes place. | `candidate-test` | `none` | Covered by Icao9432Chunk01FrequencyTransferEvidenceTest pilot-notified path. |

## Immediate Test Candidates

- `icao9432-extracted::readback_2_8_3_en::15940532b37f8528`: Clearances and instructions to enter, land on, take off from, hold short of, cross and backtrack on any runway shall always be read back. Target: `protocolEvidence`.
- `icao9432-extracted::readback_2_8_3_en::25c245bc4728ed60`: Runway-in-use, altimeter settings, SSR codes, level instructions, heading and speed instructions, and transition levels shall always be read back. Target: `protocolEvidence`.
- `icao9432-extracted::readback_2_8_3_en::4b6ece953649da07`: Other clearances or instructions, including conditional clearances, shall be read back or acknowledged in a manner to clearly indicate that they have been understood and will be complied with. Target: `protocolEvidence`.
- `icao9432-extracted::readback_2_8_3_en::58594a8ee6243296`: ATC route clearances shall always be read back. Target: `protocolEvidence`.
- `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::17e1dfdf4ce57253`: The controller shall listen to the read-back to ascertain that the clearance or instruction has been correctly acknowledged by the flight crew. Target: `protocolEvidence`.
- `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::ace4ab7ff5d53a66`: The controller shall take immediate action to correct any discrepancies revealed by the read-back. Target: `protocolEvidence`.
- `icao9432-extracted::communications_2_8_1_en::0a964f42b6100596`: If there is doubt that a message has been correctly received, a repetition of the messages shall be requested either in full or in part. Target: `simEvidence`.

## Expected Gaps / Blocked / Not Applicable Units

- `icao9432-extracted::communications_2_8_1_en::5efac97fddfd54ca` -> `phraseology-later` via `PHRASE-1`: When an aircraft wishes to broadcast information to aircraft in its vicinity, the message should be prefaced by the call "ALL STATIONS".
- `icao9432-extracted::communications_2_8_1_en::8b0487b183cd02cf` -> `policy-blocked` via `POLICY-1`: No reply is expected to such general calls unless individual stations are subsequently called upon to acknowledge receipt.
- `icao9432-extracted::communications_2_8_1_en::a685cef087951878` -> `phraseology-later` via `PHRASE-1`: When establishing communications, an aircraft should use the full call sign of both the aircraft and the aeronautical station.
- `icao9432-extracted::communications_2_8_1_en::b7acdc88125f1510` -> `phraseology-later` via `PHRASE-1`: When a ground station wishes to broadcast information, the message should be prefaced by the call "ALL STATIONS".
- `icao9432-extracted::readback_2_8_3_en::36e6ad16cffe8726` -> `policy-blocked` via `POLICY-1`: Whenever possible, controllers should pass a route clearance to an aircraft before start-up.
- `icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2` -> `expected-gap` via `FN33-MODEL-1`: Controllers should pass a clearance slowly and clearly, avoid passing clearances during complicated taxiing, and on no occasion should a clearance be passed when the pilot is engaged in line up or take-off manoeuvres.
- `icao9432-extracted::readback_2_8_3_en::f06dfa1cefd2d649` -> `phraseology-later` via `PHRASE-1`: The words 'TAKE OFF' are used only when an aircraft is cleared for take-off, or when canceling a take-off clearance; at other times, the word 'DEPARTURE' or 'AIRBORNE' is used.
- `icao9432-extracted::readback_2_8_3_en::fe3b04ca9c3384d9` -> `phraseology-later` via `PHRASE-1`: An ATC route clearance is not an instruction to take off or enter an active runway.
- `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::4c808d67d281ff71` -> `split: supported rendered readbacks covered; remaining templates phraseology-later` via `PHRASE-1`: fn-81 covers current `LineUpReadback` and `FrequencyReadback` rendered templates; unsupported templates remain phraseology-later.
- `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::ce25c18f1b44a6a8` -> `not-applicable` via `none`: See: APPENDIX 1 DIFFERENCES FROM ICAO RADIOTELEPHONY PROCEDURES
- `icao9432-extracted::transfer_communications_2_8_2_en::40382df156ad071e` -> `candidate-test` via `none`: An aircraft shall be advised by the appropriate aeronautical station to change from one radio frequency to another in accordance with agreed procedures.
- `icao9432-extracted::transfer_communications_2_8_2_en::b49ae03cbbb2d538` -> `candidate-test` via `none`: In the absence of such advice, the aircraft shall notify the aeronautical station before such a change takes place.

## Review Considerations

- FP / type safety: planned states are string artifacts here; test code should use typed helpers where available.
- Test architecture: candidate tests are not automatic coverage. Each needs a source-mapped assertion that proves the cited claim.
- Impact: blocked units stay in the report so the chunk remains complete even before implementation repair.
- Operational correctness: test authoring must re-open each registry path and quote before asserting behaviour.
