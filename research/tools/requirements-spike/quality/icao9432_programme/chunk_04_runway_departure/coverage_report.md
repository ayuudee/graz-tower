# Chunk 04 Coverage Report

Chunk: ICAO 9432 runway entry, line-up, and take-off.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` | 4 |
| `covered-red` | 0 |
| `model-gap` | 1 |
| `policy-blocked` | 9 |
| `phraseology-later` | 4 |
| `split: rendered phraseology covered; trigger policy blocked` | 1 |

Chunk 04 now has one configured-policy green row: the LOWG separate
GROUND/TOWER departure branch is explicitly bound to transfer at the holding
point, and the live trace proves that branch. This is not universal closure:
ICAO 9432 §4.5.1 says "usually", so other service shapes remain policy
questions.

fn-79 adds source-mapped rendered phraseology evidence for the base take-off
clearance wording in ICAO 9432 §4.5: `RUNWAY [designator] CLEARED FOR
TAKE-OFF`. This is a narrow rendered-phraseology closure for
`13264a6ac6d529c3`; it does not change controller behaviour or close
immediate-departure, conditional-clearance, taxi-ambiguity, or
stop-immediately phraseology rows.

fn-80 adds rendered phraseology evidence for ICAO 9432 §4.5.11's emergency
stop instruction wording: `STOP IMMEDIATELY` repeated with the aircraft callsign
repeated. The row is intentionally split: rendered wording is covered, but the
takeoff-roll / dangerous-traffic trigger remains blocked as operational policy
and scenario work.

fn-76 adds a declared-branch rendered phraseology green row for ICAO 9432
§4.5.8: where several runways are in use and pilot confusion is possible, the
rendered take-off clearance evidence contains `RUNWAY` plus the active runway
designator. This does not add a typed operational activation model for detecting
runway-confusion risk; the branch is declared in the source-backed test samples.

fn-77 adds rendered phraseology evidence for the basic ICAO 9432 §4.5.3 line-up
exchange: controller `RUNWAY [designator] LINE UP AND WAIT` and pilot `LINING UP [callsign]`. The
closure is limited to that exchange and does not move immediate-departure,
conditional-clearance, taxi-ambiguity, or stop-immediately phraseology rows.

## Coverage Table

| Source unit | Final state | Test / blocker | Claim |
|---|---|---|---|
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3` | `covered-green` rendered phraseology | `Icao9432PhraseologyEvidenceTest`; `RenderedPhraseologyTrace` | `RUNWAY [designator] CLEARED FOR TAKE-OFF` phraseology. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::152f0ffb84869af5` | `phraseology-later` | `PHRASE-1` | Immediate-departure line-up phraseology. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::19cfd36a9fce4587` | `covered-green` configured policy | `Icao9432Chunk04RunwayDepartureEvidenceTest`; `TowerTransferPolicy.SeparateGroundTowerTransferAtHoldingPoint` | Aircraft are usually transferred to TOWER at/approaching runway-holding position for the configured LOWG separate-function branch. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::42b0460ed4f07751` | `phraseology-later` | `PHRASE-1` | Immediate-departure readiness query phraseology. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::4e0bacdd1c2c06e0` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Except emergency, controllers should not transmit during take-off / early climb. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::a93888a25f0bad03` | `covered-green` rendered phraseology | `Icao9432PhraseologyEvidenceTest`; `RenderedPhraseologyTrace` | `RUNWAY [designator] LINE UP AND WAIT` phraseology and `LINING UP [callsign]` acknowledgement. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::db8a2c3dcd586b0e` | `phraseology-later` | `PHRASE-1` | Taxi phraseology must not imply runway entry / take-off clearance. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::2660849403bff7de` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Departing aircraft must identify arriving aircraft in conditional clearance. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::2e598ad0323e9e2a` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest` | Conditional runway clearance requires controller and pilot sighting. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::9b30810984e06a35` | `phraseology-later` | `PHRASE-1` | Conditional clearance phraseology order. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::c386a5865bdd7876` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Aircraft type may be insufficient; colour/company may be needed. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::dd301daf2b69fe83` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Poor-visibility report-airborne request may be used. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::0afe0064c4c933af` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Abandoned take-off should be reported to tower as soon as practicable. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::2b7c45264775e3e2` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `ControllerInterventionPolicy` | Abandoned take-off should request assistance or taxi instructions as required. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::2cc8caf62c15688b` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Departure instructions may be given with take-off clearance. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6b5a0d8b27525cbd` | `split: rendered phraseology covered; takeoff-roll/dangerous-traffic trigger policy blocked` | `Icao9432PhraseologyEvidenceTest`; `RenderedPhraseologyTrace`; operational trigger policy remains blocked | Stop-immediately instruction and callsign repeated during take-off roll. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6bee6c63069d8250` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Take-off clearance cancellation may be necessary due traffic / long departure. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::81490161201eb712` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | It may be necessary to quickly free runway for landing traffic. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::8af22eb8d9795cef` | `covered-green` declared-branch rendered phraseology | `Icao9432PhraseologyEvidenceTest`; `RenderedPhraseologyTrace` | Runway number should be stated in take-off clearance where confusion is possible. |

## Verification

- Source-unit provenance: all 19 accepted candidate JSON records have source
  quotes that normalize-match `research/txt/icao9432-extracted.txt`.
- Focused verification:
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432Chunk04RunwayDepartureEvidenceTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`.

## Review Considerations

- FP / type safety: permanent source refs use typed `EvidenceSourceRef`
  records. No production state was added.
- Test architecture: the LOWG transfer witness is a real scenario run with
  trace correlation to the `ContactFrequency(TOWER)` transmission id and the
  aircraft's holding-point state. It is green only under an explicit configured
  LOWG policy branch, not universal closure.
- Impact: fn-79 adds source coverage for existing rendered take-off clearance
  phraseology only. No controller, pilot, sim behaviour, or policy behaviour was
  changed for chunk 04.
- Operational correctness: ICAO 9432 §4.5.1's "usually" language is represented
  as configured LOWG policy, not unconditional doctrine; other
  `may`/`should`/traffic-contingency rows remain gaps.
