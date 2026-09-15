# Chunk 02 Source Plan

Chunk: `chunk-02-radio-procedures-and-policy`

Sections:

- `aerodrome_ch4_intro_start_4_1_to_4_2_en` (ICAO 9432 §4.1-§4.2)
- `test_procedures_2_8_4_en` (ICAO 9432 §2.8.4)

## Provenance Check

All 15 chunk rows were re-read from
`research/tools/requirements-spike/quality/icao9432_programme/classification.json`.
Each row points to an accepted candidate JSON under
`research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/`.

Mechanical verification:

```bash
python3 - <<'PY'
import json, re
from pathlib import Path
base = Path('research/tools/requirements-spike/quality/icao9432_programme')
rows = json.loads((base/'classification.json').read_text())
chunk = [r for r in rows if r['chunk_id'] == 'chunk-02-radio-procedures-and-policy']
source = Path('research/txt/icao9432-extracted.txt').read_text()
def norm(s):
    return re.sub(r'\s+', ' ', s).strip()
source_norm = norm(source)
for r in chunk:
    data = json.loads(Path(r['registry_path']).read_text())
    quotes = data.get('exactSourceQuotes', [])
    hits = sum(1 for q in quotes if norm(q) in source_norm)
    assert hits == len(quotes)
    assert data.get('audit', {}).get('verbatimQuoteCheck', {}).get('status') == 'pass'
    assert data.get('lifecycle', {}).get('state') == 'accepted'
PY
```

Result: pass. Every candidate has `lifecycle.state = accepted`, registry
`verbatimQuoteCheck.status = pass`, and all `exactSourceQuotes` matched the
source text after whitespace normalization.

Note: a naive raw substring check fails on line-broken quotes such as §4.1.2.
The accepted registry audit and the local re-check both use windowed /
normalized matching, which is the correct comparison for extracted PDF text.

## Inventory

| Source unit | Section | Current classification | Blocker | Lines | Registry audit | Claim |
|---|---|---|---|---:|---|---|
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4` | `aerodrome_ch4_intro_start_4_1_to_4_2_en` | `needs-observation-fact` + `needs-policy-type` / `simEvidence` | `FN43-GAP-2`, `CriticalPhaseTransmissionPolicy` | 4706-4818 | accepted / quote pass | Controllers should not transmit during take-off, initial climb, late final, or landing roll unless necessary for safety. Observation can support phase-at-transmission only; the exception is policy. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::35ad4a7f3d8d2ead` | `aerodrome_ch4_intro_start_4_1_to_4_2_en` | `phraseology-later` | `PHRASE-1` | 4706-4818 | accepted / quote pass | Engine-start request phraseology examples. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::39a4619cec62a93f` | `aerodrome_ch4_intro_start_4_1_to_4_2_en` | `phraseology-later` | `PHRASE-1` | 4706-4818 | accepted / quote pass | Start-up approval phraseology with QNH. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::86ba1c63169eceff` | `aerodrome_ch4_intro_start_4_1_to_4_2_en` | `phraseology-later` | `PHRASE-1` | 4706-4818 | accepted / quote pass | Start-up-at-time approval phraseology. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd` | `aerodrome_ch4_intro_start_4_1_to_4_2_en` | `needs-observation-fact` / `simEvidence` | `FN43-GAP-2` | 4706-4818 | accepted / quote pass | After ATC approval, the pilot starts engines assisted as necessary by ground crew. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::b6a69b358e0a53f2` | `aerodrome_ch4_intro_start_4_1_to_4_2_en` | `phraseology-later` | `PHRASE-1` | 4706-4818 | accepted / quote pass | Expected departure time and start-up-at-own-discretion phraseology. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::bba49998378e3b31` | `aerodrome_ch4_intro_start_4_1_to_4_2_en` | `phraseology-later` | `PHRASE-1` | 4706-4818 | accepted / quote pass | Where no ATIS is provided, the pilot may request current aerodrome information before start-up. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::c43cb2d0a82356bd` | `aerodrome_ch4_intro_start_4_1_to_4_2_en` | `phraseology-later` | `PHRASE-1` | 4706-4818 | accepted / quote pass | Expected start-up time phraseology. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::d822c98e298fcbfd` | `aerodrome_ch4_intro_start_4_1_to_4_2_en` | `phraseology-later` | `PHRASE-1` | 4706-4818 | accepted / quote pass | Engine-start request includes location and ATIS acknowledgement. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::f089b47d46d9653d` | `aerodrome_ch4_intro_start_4_1_to_4_2_en` | `phraseology-later` | `PHRASE-1` | 4706-4818 | accepted / quote pass | If departure is delayed, controller normally indicates start-up or expected start-up time. |
| `icao9432-extracted::test_procedures_2_8_4_en::45020d8d667c291c` | `test_procedures_2_8_4_en` | `phraseology-later` | `PHRASE-1` | 4086-4197 | accepted / quote pass | Radio-check transmissions include called station, aircraft id, radio-check words, and frequency. |
| `icao9432-extracted::test_procedures_2_8_4_en::486f651c71895e42` | `test_procedures_2_8_4_en` | `phraseology-later` | `PHRASE-1` | 4086-4197 | accepted / quote pass | Pilot unable to execute an instruction or clearance notifies using unable phraseology and gives a reason. |
| `icao9432-extracted::test_procedures_2_8_4_en::b0c636108a61a135` | `test_procedures_2_8_4_en` | `phraseology-later` | `PHRASE-1` | 4086-4197 | accepted / quote pass | Radio-check replies include calling station, replying station, and readability information. |
| `icao9432-extracted::test_procedures_2_8_4_en::d67d1f63cbbecd7d` | `test_procedures_2_8_4_en` | `split-gap` | `FN51-SCOUT-1`, `PHRASE-1` | 4086-4197 | accepted / quote pass | Ground-station test-signal duration is limited to 10 seconds; content must be spoken numbers followed by station callsign. Duration may be separable from phraseology/callsign content. |
| `icao9432-extracted::test_procedures_2_8_4_en::daa4fadde3c06a1f` | `test_procedures_2_8_4_en` | `needs-policy-type` / `simEvidence` | `POLICY-1`, `OperationalGuidancePolicy` | 4086-4197 | accepted / quote pass | Transmission readability is classified on a 1-5 readability scale. |

## Reclassification Notes For Task .2

- The critical-phase unit `095624c5163849a4` is not a simple observation
  gap. Phase-at-transmission evidence can prove when a controller transmitted,
  but the safety exception requires a typed `CriticalPhaseTransmissionPolicy`.
- The engine-start-after-approval unit `95034efc191fa9cd` remains a candidate
  for evidence-surface scouting. Task .3 must decide whether current events can
  observe both approval and subsequent start without adding behaviour.
- The radio-test-signal unit `d67d1f63cbbecd7d` should be split: duration may
  be a timing/evidence question; spoken-number and callsign content remains
  phraseology-later unless `PHRASE-1` exists.
- The remaining 11 phraseology rows stay blocked by `PHRASE-1` at this stage.
- The readability-scale row `daa4fadde3c06a1f` stays blocked by `POLICY-1`
  unless a typed readability policy concept already exists.

## Planned Coverage States

| Source unit | Planned state | Reason |
|---|---|---|
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4` | `expected-gap` + `policy-blocked` | Current DSL has `CriticalPhaseWindow` / `CriticalPhaseTransmission` types and `criticalPhase(...).routineControllerTransmissions().none()`, but real sim projection currently emits windows only. Routine/safety-necessary transmission projection is still missing, and the ICAO 9432 §4.1.2 safety exception cannot be fully evaluated without typed `CriticalPhaseTransmissionPolicy`. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::35ad4a7f3d8d2ead` | `phraseology-later` | Requires rendered engine-start request phraseology checks (`PHRASE-1`). |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::39a4619cec62a93f` | `phraseology-later` | Requires rendered start-up approval phraseology checks (`PHRASE-1`). |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::86ba1c63169eceff` | `phraseology-later` | Requires rendered start-up-at-time phraseology checks (`PHRASE-1`). |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd` | `expected-gap` pending task .3 scout | Protocol has `StartupApproved`, and the pilot mission can complete `AWAIT_STARTUP_APPROVAL`, but no source-mapped evidence fact currently proves the workflow "approval then engine start assisted as necessary". Task .3 must confirm whether an existing sim trace can observe approval/start without new facts. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::b6a69b358e0a53f2` | `phraseology-later` | Requires rendered expected-departure/start-up-at-own-discretion phraseology checks (`PHRASE-1`). |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::bba49998378e3b31` | `phraseology-later` | Classified as phraseology-later because the source claim is phrased around request wording; any broader ATIS/no-ATIS policy would be `POLICY-1`. |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::c43cb2d0a82356bd` | `phraseology-later` | Requires rendered expected-start-up-time phraseology checks (`PHRASE-1`). |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::d822c98e298fcbfd` | `phraseology-later` | Requires rendered request content proving location and ATIS acknowledgement (`PHRASE-1`). |
| `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::f089b47d46d9653d` | `phraseology-later` | Requires rendered delayed-departure/start-up-time phraseology and operational guidance policy (`PHRASE-1` / possible `POLICY-1` later). |
| `icao9432-extracted::test_procedures_2_8_4_en::45020d8d667c291c` | `phraseology-later` | Requires rendered radio-check transmission phraseology (`PHRASE-1`). |
| `icao9432-extracted::test_procedures_2_8_4_en::486f651c71895e42` | `phraseology-later` | Requires rendered unable phraseology plus reason (`PHRASE-1`). |
| `icao9432-extracted::test_procedures_2_8_4_en::b0c636108a61a135` | `phraseology-later` | Requires rendered radio-check reply phraseology and readability wording (`PHRASE-1`). |
| `icao9432-extracted::test_procedures_2_8_4_en::d67d1f63cbbecd7d` | `split`: duration `expected-gap` / content `phraseology-later` | The 10-second duration obligation is separable in principle, but only if current evidence can identify ground-station test signals without string-linting rendered speech. Spoken-number and callsign content remains `PHRASE-1`. |
| `icao9432-extracted::test_procedures_2_8_4_en::daa4fadde3c06a1f` | `policy-blocked` | Requires a typed readability-scale / operational-guidance policy concept (`POLICY-1`); fn-50 reception quality is not a 1-5 readability classification. |
