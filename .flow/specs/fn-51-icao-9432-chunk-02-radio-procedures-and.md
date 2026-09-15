# ICAO 9432 chunk 02 radio procedures and critical-phase policy

## Goal & Context

Work the next ICAO 9432 programme chunk after chunk 01 closure. The
current programme map defines chunk 02 as
`chunk-02-radio-procedures-and-policy`, covering:

- `aerodrome_ch4_intro_start_4_1_to_4_2_en`
- `test_procedures_2_8_4_en`

The chunk contains 15 accepted source units. First-pass classification says:

- `phraseology-later`: 11 units, all blocked by `PHRASE-1`
- `split-gap`: 1 unit whose duration obligation may be testable
  separately from its phraseology/callsign content
- `needs-observation-fact`: 2 units, both blocked by `FN43-GAP-2`
- `needs-policy-type`: 1 unit, blocked by `POLICY-1`

This is therefore not a broad production-repair epic. The job is to make
chunk 02 honest and ready for the two-actor source-mapped workflow:
inventory each source unit, verify quote provenance, author any
testable-now evidence that can be expressed without policy/phraseology
smuggling, and record expected gaps for everything blocked by missing
phraseology, policy, or observation surfaces.

## Source-Unit Inventory

| Source unit | Classification | Blocker | Claim |
|---|---|---|---|
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4` | `needs-observation-fact` + `needs-policy-type` / `simEvidence` | `FN43-GAP-2` + `CriticalPhaseTransmissionPolicy` | Controllers should not transmit to an aircraft during take-off, initial climb, the last part of final approach or the landing roll, unless necessary for safety. Observation can prove phase-at-transmission only; the safety exception requires typed policy. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::35ad4a7f3d8d2ead` | `phraseology-later` | `PHRASE-1` | Engine-start request phraseology includes `READY TO START UP`, `START NUMBER ONE`, or `STARTING NUMBER ONE`. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::39a4619cec62a93f` | `phraseology-later` | `PHRASE-1` | Start-up approval phraseology includes `START UP APPROVED [QNH]`. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::86ba1c63169eceff` | `phraseology-later` | `PHRASE-1` | Start-up-at-time phraseology includes `START UP AT [Time] [QNH]`. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd` | `needs-observation-fact` / `simEvidence` | `FN43-GAP-2` | Having received ATC approval, the pilot starts engines assisted as necessary by ground crew. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::b6a69b358e0a53f2` | `phraseology-later` | `PHRASE-1` | Expected-departure-time / start-up-at-own-discretion phraseology. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::bba49998378e3b31` | `phraseology-later` | `PHRASE-1` | Where no ATIS is provided, the pilot may ask for aerodrome information before requesting start-up. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::c43cb2d0a82356bd` | `phraseology-later` | `PHRASE-1` | Expected start-up-time phraseology. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::d822c98e298fcbfd` | `phraseology-later` | `PHRASE-1` | With the engine-start request, the pilot states location and acknowledges ATIS. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::f089b47d46d9653d` | `phraseology-later` | `PHRASE-1` | If departure will be delayed, the controller normally indicates a start-up or expected start-up time. |
| `icao9432-extracted::test_procedures_2_8_4_en::45020d8d667c291c` | `phraseology-later` | `PHRASE-1` | Radio-check transmissions include station called, aircraft id, `RADIO CHECK`, and frequency. |
| `icao9432-extracted::test_procedures_2_8_4_en::486f651c71895e42` | `phraseology-later` | `PHRASE-1` | A pilot unable to execute a clearance/instruction notifies with unable phraseology and reason. |
| `icao9432-extracted::test_procedures_2_8_4_en::b0c636108a61a135` | `phraseology-later` | `PHRASE-1` | Radio-check replies include calling station, replying station, and readability information. |
| `icao9432-extracted::test_procedures_2_8_4_en::d67d1f63cbbecd7d` | `split-gap`: duration may be `needs-observation-fact`; content remains `phraseology-later` | `FN51-SCOUT-1` + `PHRASE-1` | Ground-station test signals must not continue for more than 10 seconds and include spoken numbers followed by callsign. Task .2 must decide whether the duration obligation is separately testable without rendered phraseology. |
| `icao9432-extracted::test_procedures_2_8_4_en::daa4fadde3c06a1f` | `needs-policy-type` / `simEvidence` | `POLICY-1` + `OperationalGuidancePolicy` | Transmission readability is classified on the 1-5 readability scale. |

## Intended Output

Create durable chunk-02 artifacts under
`research/tools/requirements-spike/quality/icao9432_programme/chunk_02_radio_procedures_policy/`:

- `source_plan.md` plus machine snapshots if useful.
- `expected_gaps.md` listing all phraseology, policy, and observation gaps
  with canonical source-unit ids.
- `coverage_report.md` recording final state distribution.
- Optional source-mapped tests only where a real evidence surface already
  exists today. Missing evidence facts must become expected-gap rows plus
  named repair epics unless this epic is formally re-planned and reviewed.

## Implementation Strategy

1. Re-check quote provenance for all 15 units against both
   `research/txt/icao9432-extracted.txt` and the accepted candidate JSON
   metadata where both exist. If either source is unavailable or mismatched,
   record that explicitly.
2. Reclassify each unit for current reality after fn-48 through fn-50:
   do not trust the stale first-pass labels blindly.
3. Treat `PHRASE-1` as a hard boundary. Do not build rendered phraseology
   linting inside this chunk.
4. Treat `POLICY-1` as a hard boundary. Do not encode one universal policy
   for `normally`, `may`, readability scale use, or safety-exception
   judgement.
5. For the two `FN43-GAP-2` rows, scout current sim evidence first.
   Critical-phase transmission evidence can at most cover the observable
   phase-at-transmission part; the "unless necessary for safety" exception
   stays policy-blocked until `CriticalPhaseTransmissionPolicy` exists.
   If engine-start approval/start facts do not already exist, record an
   expected gap and spawn a repair epic.
6. For `test_procedures_2_8_4_en::d67d1f63cbbecd7d`, split the duration
   obligation from the phraseology/callsign content during classification.
   The duration part may be testable only if current transmission-duration
   evidence can identify ground-station test signals without rendered
   phraseology shortcuts.
7. Close the chunk with an honest distribution. Covered-red is acceptable
   only where the test proves a real failing evidence path; otherwise use
   expected-gap for missing evidence/policy/phraseology infrastructure.

## Acceptance Criteria

- [x] All 15 chunk-02 source units are inventoried in a chunk-local plan.
- [x] Quote provenance is verified against the accepted source-unit JSON and
  source text line ranges before any test/gap classification is finalized.
- [x] `PHRASE-1` rows remain phraseology-later unless a real rendered
  phraseology evidence layer already exists; no local string-lint shortcut is
  introduced.
- [x] `POLICY-1` rows remain policy-blocked unless a typed policy concept
  already exists; no universal operational policy is encoded in tests.
- [x] `FN43-GAP-2` rows are either covered by source-mapped tests using
  already-existing real sim evidence facts or recorded as expected gaps with
  repair epic pointers.
- [x] The critical-phase unit is not marked fully covered unless both the
  observable phase-at-transmission evidence and the typed safety-exception
  policy concept exist.
- [x] The `d67d1f63cbbecd7d` radio-test-signal source unit is explicitly
  split into duration and phraseology/callsign obligations, or the report
  explains why it cannot be split honestly.
- [x] `coverage_report.md` records `covered-green`, `covered-red`,
  `expected-gap`, `phraseology-later`, `policy-blocked`, and
  `not-applicable` counts for chunk 02.
- [x] Plan review and red-team notes are recorded before production code is
  changed.
- [x] Verification commands for any authored tests pass; `git diff --check`
  passes before commit.

## Review Considerations

- FP / type safety: If new evidence facts are needed, model them as sealed
  payloads with exhaustive adapter/selector handling. Do not add stringly
  tags, nullable pseudo-states, or broad `else` handling. If no evidence
  surface exists, record an expected gap rather than smuggling state through
  test-only fixtures.
- Test architecture: Tests must be source-mapped and high-level. A useful
  test should exercise a believable scenario or protocol sample and then
  assert evidence facts. It should not assert helper internals, enum
  membership, or compiler-guaranteed structure.
- Impact: The main coupling risk is letting chunk 02 accidentally implement
  cross-cutting `PHRASE-1`, `POLICY-1`, or reusable observation facts. Those
  are programme-level or repair epics, not chunk-local quick fixes. If current
  evidence is insufficient, the correct outcome is a loud expected-gap row plus
  a named repair epic.
- Operational correctness: Claims are ICAO Doc 9432 source units:
  §2.8.4 for radio test procedures and §4.1-§4.2 for start-up and
  critical-phase radio discipline. Any regulatory assertion in tests or docs
  must cite the source-unit id and section.
