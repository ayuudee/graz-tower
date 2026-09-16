# Chunk 05 Source Plan

Chunk: `chunk-05-circuit-arrival-landing`

Scope: ICAO 9432 §4.6 aerodrome traffic circuit and §4.7 final approach and
landing.

## Source Audit

- Accepted source units: 23.
- Sections:
  - `aerodrome_traffic_circuit_4_6_part1_en`: 4 units.
  - `aerodrome_traffic_circuit_4_6_part2_en`: 5 units.
  - `final_approach_landing_4_7_en`: 14 units.
- Quote audit: all 23 `source_quote_excerpt` values from
  `classification.json` normalize-match `research/txt/icao9432-extracted.txt`.

## Review Position

The generated classifier is planning input. Several rows marked `testable-now`
are permissive or local-procedure claims. A test may prove a concrete scenario
or capability, but must not convert "may", "should", "traffic conditions", or
"local procedures" into universal law.

Typed instruction facts are not rendered phraseology. A trace containing
`ClearedTouchAndGo` can support a typed touch-and-go clearance / capability
claim, but it cannot close a source unit whose operative claim is the rendered
phrase `CLEARED TOUCH AND GO`.

## Planned Coverage

| Source unit | Source text / claim | Initial classifier | Planned state | Planned evidence / blocker |
|---|---|---|---|---|
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::58ae778732ec6347` | Right-hand traffic circuit pattern should be specified. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; requires rendered circuit-joining phraseology / pattern wording. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::667985b4a6159d18` | Requests to join should be made in sufficient time for planned entry taking traffic into account. | `needs-policy-type` | `policy-blocked` | `POLICY-1`; needs planned-entry timing and traffic-accounting policy/evidence. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::a4fcedaac8a838e1` | Where ATIS is provided, receipt should be acknowledged in the initial call. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; requires rendered initial-call content. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part1_en::d65650486b4d1b8f` | Depending on traffic and arrival direction, straight-in approach may be possible. | `needs-policy-type` | `policy-blocked` | `LocalProcedurePolicy`; permissive traffic/local-procedure row, not universal law. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::28bea79b8da559cd` | Circuit coordination may require instructions to delay or accelerate aircraft. | `testable-now` | `policy-blocked` | `ControllerInterventionPolicy`; current plan lacks typed delay/accelerate circuit instruction policy and trigger evidence. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::34445db09fdd6e0a` | Pilot entering the circuit shall provide position reports in accordance with local procedures. | `testable-now` | `policy-blocked` with possible scenario evidence | Candidate LOWG trace can prove actual Downwind/Base/Final-style reports, but final coverage remains policy-blocked until local-procedure requirements are typed. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::7e3aec5e5fd60c41` | At ATIS aerodromes, pilot should confirm ATIS when establishing contact with tower. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; requires rendered initial-contact content. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::b64030acf6ef4bbd` | Depending on traffic situation and arrival direction, straight-in approach may be possible. | `needs-policy-type` | `policy-blocked` | `LocalProcedurePolicy`; same source concept as `d656...` in the bilingual continuation. |
| `icao9432-extracted::aerodrome_traffic_circuit_4_6_part2_en::dcf776a1b9c8a303` | Having joined the circuit, pilot makes routine reports as required by local procedures. | `needs-policy-type` | `policy-blocked` with possible scenario evidence | Local procedures decide required reports; ordinary circuit traces can be scenario evidence only. |
| `icao9432-extracted::final_approach_landing_4_7_en::00baaf3c55155044` | `FINAL` report timing within 7 km / 4 NM or at 7 km if no clearance after long final. | `phraseology-later` | `phraseology-later` | `PHRASE-1` plus distance-at-report evidence; current typed `ReportEvent.Final` does not prove rendered wording or 7 km threshold. |
| `icao9432-extracted::final_approach_landing_4_7_en::0ece166e11d7728e` | Pilots may request `TOUCH AND GO` when training in the traffic circuit. | `testable-now` | `covered-green` candidate | Tighten `Icao9432TouchAndGoSourceBackedScenarioTest` so the LOWG trace proves the pilot's touch-and-go request / intent before `ClearedTouchAndGo`, not just the controller clearance. Review must confirm the source claim is capability, not rendered phraseology. |
| `icao9432-extracted::final_approach_landing_4_7_en::1327871f46c1d348` | Low-pass undercarriage phrase `WHEELS APPEAR UP` may be used. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; no rendered low-pass undercarriage phraseology. |
| `icao9432-extracted::final_approach_landing_4_7_en::1960f59d8b9efecb` | Low-pass undercarriage phrase `LANDING GEAR APPEARS DOWN` may be used. | `phraseology-later` | `phraseology-later` | `PHRASE-1`. |
| `icao9432-extracted::final_approach_landing_4_7_en::1db805d02051bf47` | Low-pass phrase for wheel not appearing up/down may be used. | `phraseology-later` | `phraseology-later` | `PHRASE-1`. |
| `icao9432-extracted::final_approach_landing_4_7_en::4c698a5ad52a30e4` | `LONG FINAL` report when turning final greater than 7 km / 4 NM. | `phraseology-later` | `phraseology-later` | `PHRASE-1` plus distance-at-report evidence. |
| `icao9432-extracted::final_approach_landing_4_7_en::63836b7aef62a6f6` | Pilot may request to fly past tower/observation point for visual inspection from ground. | `testable-now` | `model-gap` | `ClearedLowApproach` exists, but current sim lacks a pilot low-pass request workflow and controller rule producing it from scenario evidence. |
| `icao9432-extracted::final_approach_landing_4_7_en::70e781a65920c075` | Straight-in approach `LONG FINAL` report at about 15 km / 8 NM. | `phraseology-later` | `phraseology-later` | `PHRASE-1`, straight-in procedure policy, and distance-at-report evidence. |
| `icao9432-extracted::final_approach_landing_4_7_en::7bbc96aa5ee36893` | Low-pass phrase for wheel appearing up/down may be used. | `phraseology-later` | `phraseology-later` | `PHRASE-1`. |
| `icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4` | ATC may clear `TOUCH AND GO` using phrase `CLEARED TOUCH AND GO`. | `phraseology-later` | `phraseology-later` with typed-trace scenario evidence | Existing test proves typed `ClearedTouchAndGo`, not rendered phraseology. Keep phraseology-later unless rendered wording is asserted. |
| `icao9432-extracted::final_approach_landing_4_7_en::aaf5262d8e7750b2` | Example dialogue for low-pass request and clearance. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; illustrative dialogue, no rendered dialogue support. |
| `icao9432-extracted::final_approach_landing_4_7_en::b1c21e2f70bbf36f` | If unable to approve touch-and-go due traffic, ATC may instruct full stop or another circuit. | `phraseology-later` | `phraseology-later` | Requires rendered phraseology; also depends on traffic-congestion policy before an operational scenario can be green. |
| `icao9432-extracted::final_approach_landing_4_7_en::e17d8b9b99c43496` | For training, pilot may request an approach along/parallel to runway without landing. | `testable-now` | `model-gap` | No current mission/scenario workflow for training low approach / low pass request. |
| `icao9432-extracted::final_approach_landing_4_7_en::fbae3a11e1d068a3` | Example dialogue for `LONG FINAL` / `FINAL`, wind, and landing clearance. | `phraseology-later` | `phraseology-later` | `PHRASE-1`; illustrative dialogue, no rendered dialogue support. |

## Planned Tests

1. **Touch-and-go capability check**: adjust
   `Icao9432TouchAndGoSourceBackedScenarioTest` so it honestly covers
   `0ece166e11d7728e` as a circuit-training request capability by asserting a
   pilot report / request with touch-and-go intent before `ClearedTouchAndGo`.
   It must not claim rendered phraseology closure for `a4c8fffd8a61adb4`.
2. **Circuit report scenario evidence**: if current LOWG circuit traces expose
   useful Downwind/Base/Final reports, add scenario evidence for `34445...` and
   `dcf776...` while leaving final coverage policy-blocked because local
   procedures are not typed.
3. **Expected-gap specs** for:
   - policy/local-procedure rows;
   - low-pass / low-approach model gaps;
   - final/long-final distance/phraseology rows;
   - undercarriage-observation and example-dialogue phraseology rows.

## Review Considerations

- FP / type safety: no production state changes are planned. Any new source ref
  must use typed `EvidenceSourceRef`, not raw strings in ordinary citation
  APIs.
- Test architecture: distinguish scenario capability from universal law. The
  touch-and-go trace may be green for request capability only if the pilot
  request / intent is observed before clearance, not for rendered phraseology.
- Impact: the likely code impact is limited to test/catalog changes and
  expected-gap documentation. If source review finds that an existing test
  overclaims phraseology coverage, narrow the cited source set rather than
  broadening behaviour.
- Operational correctness: ICAO 9432 §4.6 / §4.7 includes local-procedure,
  traffic-dependent, permissive, and example wording. Coverage must preserve
  those modalities.
