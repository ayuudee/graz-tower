# Chunk 04 Source Plan

Chunk: `chunk-04-runway-departure`

Scope: ICAO 9432 §4.5 runway entry, line-up, conditional clearances, take-off
clearance, cancellation, and runway-freeing behaviour.

## Source Audit

- Accepted source units: 19.
- Sections:
  - `takeoff_procedures_4_5_1_to_4_5_5_en`: 7 units.
  - `takeoff_procedures_4_5_6_to_4_5_7_en`: 5 units.
  - `takeoff_procedures_4_5_8_to_4_5_12_en`: 7 units.
- Quote audit: all 19 `source_quote_excerpt` values from
  `classification.json` normalize-match `research/txt/icao9432-extracted.txt`.

## Review Position

The generated classifier is treated as planning input, not executable truth.
Several rows marked `testable-now` contain permissive or contingency language
(`usually`, `may`, `occasionally necessary`) and cannot honestly be turned into
universal controller law. These rows should land as covered-green only when the
test proves the exact source claim from observed evidence; otherwise they must
land as source-specific gaps.

## Planned Coverage

| Source unit | Source text / claim | Initial classifier | Planned state | Planned evidence / blocker |
|---|---|---|---|---|
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3` | Take-off clearance phraseology: `RUNWAY [designator] CLEARED FOR TAKE-OFF`. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; fn-61 adds renderer support, but the source unit remains support-only / review-only rather than standalone covered-green. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::152f0ffb84869af5` | Immediate-departure line-up phraseology. | `phraseology-later` | `phraseology-later` | `PHRASE-1`. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::19cfd36a9fce4587` | At busy aerodromes with separate GROUND/TOWER, aircraft are usually transferred to TOWER at or approaching the runway-holding position. | `testable-now` | `covered-green` configured policy | LOWG departure trace captures both explicit policy branch and live behavior: `TaxiToHoldingPoint`, `ContactFrequency(role=TOWER)` while the aircraft is at the assigned runway holding point, then `Ready` / runway use. This remains configured LOWG coverage, not universal law. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::42b0460ed4f07751` | Immediate-departure readiness query phraseology. | `phraseology-later` | `phraseology-later` | `PHRASE-1`. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::4e0bacdd1c2c06e0` | Except emergency, controllers should not transmit while aircraft is taking off / early climb. | `needs-policy-type` | `policy-blocked` | `POLICY-1`; needs safety-necessity / emergency exception policy, not just routine-transmission observation. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::a93888a25f0bad03` | `LINE UP AND WAIT` phraseology and readback. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; structural readback already exists elsewhere but rendered phraseology remains blocked. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::db8a2c3dcd586b0e` | Taxi phraseology should not be interpretable as runway-entry or take-off clearance. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; semantic ambiguity of rendered taxi phraseology is not modelled. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::2660849403bff7de` | Departing aircraft must correctly identify arriving aircraft in conditional clearance. | `needs-policy-type` | `policy-blocked` | `POLICY-1`; needs conditional-clearance identification policy / evidence. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::2e598ad0323e9e2a` | Conditional clearances affecting active runways require aircraft/vehicles seen by both controller and pilot. | `testable-now` | `model-gap` | No current evidence fact proves dual controller+pilot sighting for the conditioned traffic/vehicle. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::9b30810984e06a35` | Conditional-clearance phraseology order: callsign, condition, clearance, reiteration. | `phraseology-later` | `phraseology-later` | `PHRASE-1`. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::c386a5865bdd7876` | Aircraft type may be insufficient; colour/company may be needed. | `needs-policy-type` | `policy-blocked` | `POLICY-1`; needs traffic identification policy and visual description evidence. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::dd301daf2b69fe83` | In poor visibility, controller may request pilot to report airborne. | `testable-now` | `policy-blocked` | `POLICY-1`; the source is permissive (`may`) and needs poor-visibility departure policy plus request-airborne evidence before a meaningful scenario can be green. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::0afe0064c4c933af` | Pilot abandoning take-off should inform tower as soon as practicable. | `needs-policy-type` | `policy-blocked` | `POLICY-1`; needs abandoned-takeoff communication timing policy. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::2b7c45264775e3e2` | Pilot abandoning take-off should request assistance or taxi instructions as required. | `needs-policy-type` | `policy-blocked` | `ControllerInterventionPolicy`; needs post-abort assistance/taxi request workflow. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::2cc8caf62c15688b` | Departure instructions may be given with take-off clearance. | `testable-now` | `policy-blocked` | `POLICY-1`; the source is permissive (`may`) and current `ClearedForTakeoff` is not a compound departure-instruction clearance proving co-issued departure instructions. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6b5a0d8b27525cbd` | Started take-off roll: stop immediately, repeat instruction and callsign. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; `StopImmediately` type exists but repeated rendered call is blocked. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6bee6c63069d8250` | Occasionally necessary to cancel take-off clearance due traffic / long departure. | `testable-now` | `policy-blocked` | `POLICY-1`; needs traffic-development / long-departure contingency policy and trigger evidence before source-mapped cancellation can be green. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::81490161201eb712` | Occasionally necessary to quickly free runway for landing traffic. | `testable-now` | `policy-blocked` | `POLICY-1`; needs runway-freeing-for-landing-traffic contingency policy and trigger evidence before source-mapped runway-freeing can be green. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::8af22eb8d9795cef` | Multiple runways / possible confusion: runway number should be stated in take-off clearance. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; typed `ClearedForTakeoff.runway` is structural, but rendered phraseology remains blocked. |

## Planned Tests

1. **LOWG runway-holding transfer scenario**: configured-policy coverage for
   `19cfd36a9fce4587`, scoped to LOWG / separate GROUND+TOWER / single
   departure. The source says "usually", so this is not universal law.
   Required witnesses:
   - `TaxiToHoldingPoint < ContactFrequency(role=TOWER) < Ready < LineUpAndWait`;
   - the aircraft is at/approaching the assigned runway holding point at the
     `ContactFrequency(role=TOWER)` transmission time, proven from state trace
     position/phase evidence rather than instruction order alone.
2. **Expected-gap specs** for the five source units that the classifier marked
   `testable-now` but source review shows require missing policy/model evidence:
   - `model-gap`: dual-sighting conditional clearance;
   - `policy-blocked`: poor-visibility report-airborne request, departure
     instructions with take-off clearance, take-off cancellation due traffic,
     and quickly freeing the runway for landing traffic.
3. **Expected-gap specs** for the five policy-blocked rows and eight
   phraseology rows, grouped only where the same missing concept genuinely
   blocks the rows.

## Review Considerations

- FP / type safety: any new evidence selector must be typed and exhaustive.
  Existing order selectors over typed instruction/report facts are preferred.
- Test architecture: no chunk 04 row is planned as unconditional
  covered-green. The LOWG transfer row gets scenario evidence only;
  contingency and permissive rows should not be turned green from an ordinary
  departure trace.
- Impact: no controller/pilot/sim behaviour repair belongs in this epic unless
  narrowly needed to project an already-observed fact.
- Operational correctness: source language from ICAO Doc 9432 §4.5 includes
  "usually", "may", "should", and phraseology examples. The tests must preserve
  those modalities instead of promoting them into universal law.
