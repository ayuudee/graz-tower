# Chunk 01 coverage report

Chunk: ICAO 9432 communications, transfer, and readback.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` | 6 |
| `expected-gap` | 4 |
| `not-applicable` | 1 |
| `phraseology-later` | 7 |
| `policy-blocked` | 2 |

Focused verification run:

```bash
nix-shell --run './gradlew :sim:jvmTest --tests "*.Icao9432Chunk01ReadbackEvidenceTest" :controller:jvmTest --tests "*.Icao9432ReadbackConformanceSpec"'
```

Result: green.

## Coverage Table

| Source unit | Final state | Test / blocker | Claim |
|---|---|---|---|
| `icao9432-extracted::communications_2_8_1_en::0a964f42b6100596` | `expected-gap` | `COMMS-1` | If there is doubt that a message has been correctly received, a repetition of the messages shall be requested either ... |
| `icao9432-extracted::communications_2_8_1_en::5efac97fddfd54ca` | `phraseology-later` | `PHRASE-1` | When an aircraft wishes to broadcast information to aircraft in its vicinity, the message should be prefaced by the c... |
| `icao9432-extracted::communications_2_8_1_en::8b0487b183cd02cf` | `policy-blocked` | `POLICY-1` | No reply is expected to such general calls unless individual stations are subsequently called upon to acknowledge rec... |
| `icao9432-extracted::communications_2_8_1_en::a685cef087951878` | `phraseology-later` | `PHRASE-1` | When establishing communications, an aircraft should use the full call sign of both the aircraft and the aeronautical... |
| `icao9432-extracted::communications_2_8_1_en::b7acdc88125f1510` | `phraseology-later` | `PHRASE-1` | When a ground station wishes to broadcast information, the message should be prefaced by the call "ALL STATIONS". |
| `icao9432-extracted::readback_2_8_3_en::15940532b37f8528` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ReadbackEvidenceTest.kt` | Clearances and instructions to enter, land on, take off from, hold short of, cross and backtrack on any runway shall ... |
| `icao9432-extracted::readback_2_8_3_en::25c245bc4728ed60` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ReadbackEvidenceTest.kt` | Runway-in-use, altimeter settings, SSR codes, level instructions, heading and speed instructions, and transition leve... |
| `icao9432-extracted::readback_2_8_3_en::36e6ad16cffe8726` | `policy-blocked` | `POLICY-1` | Whenever possible, controllers should pass a route clearance to an aircraft before start-up. |
| `icao9432-extracted::readback_2_8_3_en::4b6ece953649da07` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ReadbackEvidenceTest.kt` | Other clearances or instructions, including conditional clearances, shall be read back or acknowledged in a manner to... |
| `icao9432-extracted::readback_2_8_3_en::58594a8ee6243296` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ReadbackEvidenceTest.kt` | ATC route clearances shall always be read back. |
| `icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2` | `expected-gap` | `FN33-MODEL-1` | Controllers should pass a clearance slowly and clearly, avoid passing clearances during complicated taxiing, and on n... |
| `icao9432-extracted::readback_2_8_3_en::f06dfa1cefd2d649` | `phraseology-later` | `PHRASE-1` | The words 'TAKE OFF' are used only when an aircraft is cleared for take-off, or when canceling a take-off clearance; ... |
| `icao9432-extracted::readback_2_8_3_en::fe3b04ca9c3384d9` | `phraseology-later` | `PHRASE-1` | An ATC route clearance is not an instruction to take off or enter an active runway. |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::17e1dfdf4ce57253` | `covered-green` | `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/requirements/Icao9432ReadbackConformanceSpec.kt` | The controller shall listen to the read-back to ascertain that the clearance or instruction has been correctly acknow... |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::4c808d67d281ff71` | `phraseology-later` | `PHRASE-1` | An aircraft should terminate the read-back by its call sign. |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::ace4ab7ff5d53a66` | `covered-green` | `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/requirements/Icao9432ReadbackConformanceSpec.kt` | The controller shall take immediate action to correct any discrepancies revealed by the read-back. |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::ce25c18f1b44a6a8` | `not-applicable` | `none` | See: APPENDIX 1 DIFFERENCES FROM ICAO RADIOTELEPHONY PROCEDURES |
| `icao9432-extracted::transfer_communications_2_8_2_en::40382df156ad071e` | `expected-gap` | `FN44-GAP-1/FN44-GAP-2` | An aircraft shall be advised by the appropriate aeronautical station to change from one radio frequency to another in... |
| `icao9432-extracted::transfer_communications_2_8_2_en::96720e821bf926cc` | `phraseology-later` | `PHRASE-1` | Phraseology for frequency change includes 'CONTACT [Unit] [Frequency]' and readback 'Frequency Callsign'. |
| `icao9432-extracted::transfer_communications_2_8_2_en::b49ae03cbbb2d538` | `expected-gap` | `FN44-GAP-1/FN44-GAP-2` | In the absence of such advice, the aircraft shall notify the aeronautical station before such a change takes place. |

## Repair / Follow-Up Handoff

- `COMMS-1`: add reception-doubt / repetition-request evidence before testing ICAO 9432 §2.8.1 doubtful reception.
- `POLICY-1`: add typed policy concepts before asserting guidance such as no-reply general calls and route-clearance timing.
- `PHRASE-1`: add rendered-transmission phraseology facts before covering ALL STATIONS, full callsign, TAKE OFF word-use, readback callsign termination, and CONTACT phraseology units.
- `FN33-MODEL-1`: add clearance timing/workload evidence for complicated taxiing / line-up / take-off clearance delivery guidance.
- `FN44-GAP-1` / `FN44-GAP-2`: add frequency-transfer facts for controller-advised and pilot-notified transfer claims.

## Review Considerations

- FP / type safety: permanent coverage reporting should use typed coverage states; this report is the chunk handoff artifact.
- Test architecture: green tests cover structural readback and hearback classification only. Phraseology, policy, frequency transfer, and reception-quality units remain blocked.
- Impact: no production behaviour was changed in this chunk.
- Operational correctness: each covered/blocked row keeps the accepted ICAO 9432 source-unit id visible.
