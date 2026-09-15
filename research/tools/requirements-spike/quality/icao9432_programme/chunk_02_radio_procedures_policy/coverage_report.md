# Chunk 02 Coverage Report

Chunk: ICAO 9432 radio procedures and critical-phase policy.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` | 0 |
| `covered-red` + `policy-blocked` | 1 |
| `expected-gap` | 1 |
| `phraseology-later` | 11 |
| `policy-blocked` | 1 |
| `split` | 1 |
| `not-applicable` | 0 |

fn-51 originally authored no source-mapped tests because every chunk-02 unit
was blocked by missing phraseology, policy, or observation/model
infrastructure. fn-52.1 repaired the critical-phase observation surface and
turned `095624c5163849a4` into an honest covered-red source-mapped result
under conservative routine classification. This remains not a green-test
closure.

Repair epic:

- `fn-52-implement-icao-9432-chunk-02`: chunk-specific observation/model
  repairs for critical-phase transmission projection, start-up approval/start
  evidence, and ground-station radio-test-signal duration identity.
- `fn-52.2` assessed the start-up approval/start row and blocked it on D-PF.1
  rather than inferring engine start from mission-step completion or default
  `engineRunning == true`.

Cross-cutting blockers:

- `PHRASE-1`: rendered-transmission phraseology evidence.
- `POLICY-1`: typed operational policy concepts, including
  `CriticalPhaseTransmissionPolicy` and readability-scale policy.

## Coverage Table

| Source unit | Final state | Test / blocker | Claim |
|---|---|---|---|
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4` | `covered-red` + `policy-blocked` | `EvidenceProjectionPressureTest`; `fn-52-implement-icao-9432-chunk-02.1`; `POLICY-1` | Controllers should not transmit during take-off, initial climb, late final, or landing roll unless necessary for safety. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::35ad4a7f3d8d2ead` | `phraseology-later` | `PHRASE-1` | Engine-start request phraseology examples. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::39a4619cec62a93f` | `phraseology-later` | `PHRASE-1` | Start-up approval phraseology with QNH. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::86ba1c63169eceff` | `phraseology-later` | `PHRASE-1` | Start-up-at-time approval phraseology. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd` | `expected-gap` | `D-PF.1`; `fn-52-implement-icao-9432-chunk-02.2` | After ATC approval, the pilot starts engines assisted as necessary by ground crew. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::b6a69b358e0a53f2` | `phraseology-later` | `PHRASE-1` | Expected departure time and start-up-at-own-discretion phraseology. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::bba49998378e3b31` | `phraseology-later` | `PHRASE-1` | Where no ATIS is provided, the pilot may request current aerodrome information before start-up. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::c43cb2d0a82356bd` | `phraseology-later` | `PHRASE-1` | Expected start-up time phraseology. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::d822c98e298fcbfd` | `phraseology-later` | `PHRASE-1` | Engine-start request includes location and ATIS acknowledgement. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::f089b47d46d9653d` | `phraseology-later` | `PHRASE-1` | If departure is delayed, controller normally indicates start-up or expected start-up time. |
| `icao9432-extracted::test_procedures_2_8_4_en::45020d8d667c291c` | `phraseology-later` | `PHRASE-1` | Radio-check transmissions include called station, aircraft id, radio-check words, and frequency. |
| `icao9432-extracted::test_procedures_2_8_4_en::486f651c71895e42` | `phraseology-later` | `PHRASE-1` | Pilot unable to execute an instruction or clearance notifies using unable phraseology and gives a reason. |
| `icao9432-extracted::test_procedures_2_8_4_en::b0c636108a61a135` | `phraseology-later` | `PHRASE-1` | Radio-check replies include calling station, replying station, and readability information. |
| `icao9432-extracted::test_procedures_2_8_4_en::d67d1f63cbbecd7d` | `split` | `fn-52-implement-icao-9432-chunk-02.3`; `PHRASE-1` | Ground-station test-signal duration is limited to 10 seconds; content must be spoken numbers followed by station callsign. |
| `icao9432-extracted::test_procedures_2_8_4_en::daa4fadde3c06a1f` | `policy-blocked` | `POLICY-1` | Transmission readability is classified on a 1-5 readability scale. |

## Verification

- Source-unit provenance: all 15 accepted candidate JSON records have
  `verbatimQuoteCheck.status = pass`, `lifecycle.state = accepted`, and
  normalized `exactSourceQuotes` matches in
  `research/txt/icao9432-extracted.txt`.
- fn-51 changed no Kotlin production or test code; fn-52.1 added the
  critical-phase source-mapped covered-red test and observation projection.
- Remaining chunk-02 rows are still blocked by `PHRASE-1`, `POLICY-1`,
  D-PF.1, or the open fn-52.3 radio-test observation task.

## Review Considerations

- FP / type safety: fn-52.1 reused existing evidence payloads and kept
  `TransmissionNecessity.SafetyNecessary` non-emitted until a reason-bearing
  policy type exists. Unknown future coverage states must remain explicit in
  the chunk-local report rather than coerced into green.
- Test architecture: chunk 02 is still mostly a gap-classification closure,
  but critical-phase radio discipline now has a permanent covered-red wall in
  `EvidenceProjectionPressureTest` and `EvidencePermanentTwentyCaseTest`.
  Future green coverage belongs after sim behavior or `POLICY-1` work.
- Impact: fn-52.1 narrowed `FN43-GAP-2`: critical-phase windows and routine
  controller-transmission projection exist; safety-necessity classification
  remains blocked by `POLICY-1`.
- Impact: fn-52.2 deliberately avoids changing `AircraftState.engineRunning`
  because that field is already coupled to failure physics and abort
  recognition; startup lifecycle belongs with D-PF.1.
- Operational correctness: ICAO 9432 §4.1.2 contains a safety exception; it is
  deliberately not asserted as an unconditional no-transmission rule.
