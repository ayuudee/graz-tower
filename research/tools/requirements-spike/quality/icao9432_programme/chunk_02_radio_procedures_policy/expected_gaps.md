# Chunk 02 Expected Gaps

Chunk: `chunk-02-radio-procedures-and-policy`

This file records chunk-02 source units that cannot honestly be marked
covered-green with the current evidence and policy surface.

## Summary

| Source-unit state | Units |
|---|---:|
| `phraseology-later` | 11 |
| `policy-blocked` | 1 |
| `covered-red` + `policy-blocked` | 1 |
| `expected-gap` | 1 |
| `split` | 1 |

Counting note: the `split` row is the radio-test-signal source unit
`d67d1f63cbbecd7d`. Its duration obligation is treated separately from its
phraseology/callsign obligation.

## Policy-Blocked

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4` | `POLICY-1` / `CriticalPhaseTransmissionPolicy`; observation repair `fn-52-implement-icao-9432-chunk-02.1` | The ICAO 9432 §4.1.2 safety exception cannot be evaluated without typed policy. fn-52.1 repaired routine critical-phase transmission projection and now surfaces the LOWG trace as covered-red under conservative routine classification. |
| `icao9432-extracted::test_procedures_2_8_4_en::daa4fadde3c06a1f` | `POLICY-1` / `OperationalGuidancePolicy` | The 1-5 readability scale is a classification policy. Existing fn-50 `ReceptionQuality` only distinguishes clear vs doubtful reception and is not the ICAO 9432 readability scale. |

## Expected-Gap / Observation

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd` | `D-PF.1`; assessed by `fn-52-implement-icao-9432-chunk-02.2` | Need an evidence path for ATC start-up approval followed by pilot engine start. Protocol primitives exist, but the live departure tree omits startup clearance under D-PF.1 and `engineRunning == true` is failure/abort ground truth, not orderable startup evidence. |
| `icao9432-extracted::test_procedures_2_8_4_en::d67d1f63cbbecd7d` | duration repair `fn-52-implement-icao-9432-chunk-02.3`; `PHRASE-1` for content | The duration limit may be testable if a typed ground-station test-signal event exists. The spoken-number and callsign content requires rendered phraseology evidence. |

## Phraseology-Later

| Source unit | Blocker | Reason |
|---|---|---|
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::35ad4a7f3d8d2ead` | `PHRASE-1` | Engine-start request phraseology examples require rendered phraseology evidence. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::39a4619cec62a93f` | `PHRASE-1` | Start-up approval phraseology requires rendered phraseology evidence. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::86ba1c63169eceff` | `PHRASE-1` | Start-up-at-time phraseology requires rendered phraseology evidence. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::b6a69b358e0a53f2` | `PHRASE-1` | Expected-departure/start-up-at-own-discretion phraseology requires rendered phraseology evidence. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::bba49998378e3b31` | `PHRASE-1` | No-ATIS aerodrome-information request wording requires rendered phraseology evidence. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::c43cb2d0a82356bd` | `PHRASE-1` | Expected-start-up-time phraseology requires rendered phraseology evidence. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::d822c98e298fcbfd` | `PHRASE-1` | Location and ATIS acknowledgement in start-up request require rendered phraseology evidence. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::f089b47d46d9653d` | `PHRASE-1` | Delayed-departure/start-up-time phraseology requires rendered phraseology evidence; any "normally" policy is not encoded here. |
| `icao9432-extracted::test_procedures_2_8_4_en::45020d8d667c291c` | `PHRASE-1` | Radio-check request content requires rendered phraseology evidence. |
| `icao9432-extracted::test_procedures_2_8_4_en::486f651c71895e42` | `PHRASE-1` | Unable phraseology and reason require rendered phraseology evidence. |
| `icao9432-extracted::test_procedures_2_8_4_en::b0c636108a61a135` | `PHRASE-1` | Radio-check reply content and readability wording require rendered phraseology evidence. |

## Follow-Up Signals

- `FN43-GAP-2` is narrower after the task .2 scout: critical-phase
  observation facts exist, but safety-exception policy and start-up
  approval/start workflow evidence remain unresolved.
- `fn-52-implement-icao-9432-chunk-02` is the repair epic for the three
  chunk-specific observation/model gaps. It is separate from fn-51 so the
  coverage epic remains a test/gap classification effort.
