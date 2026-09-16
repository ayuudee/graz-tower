# Chunk 03 Source Plan

Chunk: `chunk-03-ground-movement`

Sections:

- `pushback_powerback_4_3_en` (ICAO Doc 9432 §4.3)
- `taxi_4_4_en` (ICAO Doc 9432 §4.4)

## Provenance Check

All 11 chunk rows were re-read from
`research/tools/requirements-spike/quality/icao9432_programme/classification.json`.
Each row points to an accepted candidate JSON under
`research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/`.

Mechanical verification command:

```bash
python3 - <<'PY'
import json, re
from pathlib import Path
base = Path('research/tools/requirements-spike/quality/icao9432_programme')
rows = json.loads((base / 'classification.json').read_text())
chunk = [r for r in rows if r['chunk_id'] == 'chunk-03-ground-movement']
source = Path('research/txt/icao9432-extracted.txt').read_text()
def norm(s):
    return re.sub(r'\s+', ' ', s).strip()
source_norm = norm(source)
for r in chunk:
    data = json.loads(Path(r['registry_path']).read_text())
    quotes = data.get('exactSourceQuotes', [])
    hits = sum(1 for q in quotes if norm(q) in source_norm)
    assert hits == len(quotes), r['source_unit_id']
    assert data.get('audit', {}).get('verbatimQuoteCheck', {}).get('status') == 'pass'
    assert data.get('lifecycle', {}).get('state') == 'accepted'
print(len(chunk))
PY
```

Result: pass, 11 rows.

## Inventory

| Source unit | Section | Planned state | Blocker | Lines | Claim |
|---|---|---|---|---:|---|
| `icao9432-extracted::pushback_powerback_4_3_en::1aae1f61b91984e8` | `pushback_powerback_4_3_en` | `model-gap` | `PUSHBACK-1` | 4819-4916 | Power-back is aircraft reverse movement using engine power. |
| `icao9432-extracted::pushback_powerback_4_3_en::5980a8f786170b01` | `pushback_powerback_4_3_en` | `model-gap` + `policy-blocked` | `PUSHBACK-1`, `LocalProcedurePolicy` | 4819-4916 | Requests for push-back or power-back go to ATC or apron management depending on local procedures. |
| `icao9432-extracted::pushback_powerback_4_3_en::b3652213a568f55f` | `pushback_powerback_4_3_en` | `model-gap` | `PUSHBACK-1` | 4819-4916 | Ground crew gives a visual signal after the manoeuvre to indicate the aircraft is free to taxi. |
| `icao9432-extracted::pushback_powerback_4_3_en::da5fd317668b375a` | `pushback_powerback_4_3_en` | `phraseology-later` | `PHRASE-1` | 4819-4916 | Pilot stop-pushback phraseology. |
| `icao9432-extracted::pushback_powerback_4_3_en::fc3dfdf7cc913637` | `pushback_powerback_4_3_en` | `phraseology-later` | `PHRASE-1` | 4819-4916 | Pilot / ground-crew pushback coordination phraseology. |
| `icao9432-extracted::taxi_4_4_en::03985c8e2cf3f473` | `taxi_4_4_en` | `policy-blocked` | `POLICY-1`, `LocalProcedurePolicy` | 4917-5139 | Taxi clearance limit may be another aerodrome position depending on traffic. |
| `icao9432-extracted::taxi_4_4_en::1367907005a34ad1` | `taxi_4_4_en` | `model-gap` | compound taxi/runway-crossing clearance semantics | 4917-5139 | Taxi limit beyond a runway requires explicit crossing clearance or hold-short instruction. |
| `icao9432-extracted::taxi_4_4_en::417f64324f7495bf` | `taxi_4_4_en` | `policy-blocked` with scenario evidence | `POLICY-1`, `OperationalGuidancePolicy` | 4917-5139 | Departing-aircraft taxi clearance limit will normally be the runway holding point. |
| `icao9432-extracted::taxi_4_4_en::53f33b6da4f2be58` | `taxi_4_4_en` | `model-gap` | departure-information content evidence | 4917-5139 | With ATIS acknowledged, controller does not need to pass departure information when issuing taxi instructions. |
| `icao9432-extracted::taxi_4_4_en::b9e7fc3605fe616e` | `taxi_4_4_en` | `covered-red` structural audit | current typed taxi-instruction space includes non-routed taxi ops | 4917-5139 | Taxi instruction always contains a clearance limit. |
| `icao9432-extracted::taxi_4_4_en::eadf2541fcd51825` | `taxi_4_4_en` | `model-gap` | whole-aircraft holding-position geometry evidence | 4917-5139 | Runway is vacated when the entire aircraft is beyond the relevant runway-holding position. |

## Implemented Coverage

- `Icao9432Chunk03GroundMovementEvidenceTest`:
  - `b9e7fc3605fe616e` lands covered-red via structural audit over current
    typed taxi-instruction leaves. The audit activates typed instruction facts
    and fails on taxi-like leaves without a clearance-limit field.
- `Icao9432ModelGapSourceUnitSpecTest`:
  - `1aae1f61b91984e8`, `5980a8f786170b01`, and `b3652213a568f55f` land as
    `PUSHBACK-1` model gaps.
  - `1367907005a34ad1` lands as a compound taxi/runway-crossing clearance
    semantics gap.
  - `53f33b6da4f2be58` lands as a departure-information content evidence gap.
  - `eadf2541fcd51825` lands as a whole-aircraft holding-position geometry
    evidence gap.
- Existing legacy `Icao9432TaxiSourceBackedScenarioTest` still demonstrates
  the LOWG scenario leg for `417f64324f7495bf` and `b9e7fc3605fe616e`, but it
  is not used as universal source closure for chunk 03.

## Review Considerations

- FP / type safety: prefer existing typed `TaxiToHoldingPoint`,
  `TaxiViaRunway`, `CrossRunway`, and `HoldShortOf` surfaces. If a new
  evidence fact is needed, it must be closed/typed and covered by exhaustive
  tests.
- Test architecture: tests must be high-level and source-mapped. Policy rows
  with "normally", "may", or "depending" language must not be made
  unconditional pass/fail rules.
- Impact: pushback/powerback is intentionally not implemented here; no
  ground-crew or apron-management actor should be introduced in this
  test-authoring epic.
- Operational correctness: cite ICAO Doc 9432 §4.3 for pushback/powerback and
  §4.4 for taxi. Phraseology examples remain blocked by `PHRASE-1`.
