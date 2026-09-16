# Chunk 04 Coverage Report

Chunk: ICAO 9432 runway entry, line-up, and take-off.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` | 0 |
| `covered-red` | 0 |
| `model-gap` | 1 |
| `policy-blocked` | 10 |
| `phraseology-later` | 8 |

Chunk 04 deliberately produces no universal covered-green rows. The LOWG
departure trace provides scenario evidence for the usual GROUND-to-TOWER
transfer pattern in ICAO 9432 §4.5.1, but the source says "usually", so final
coverage remains policy-blocked rather than promoted to universal law.

## Coverage Table

| Source unit | Final state | Test / blocker | Claim |
|---|---|---|---|
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3` | `phraseology-later` | `PHRASE-1` | Take-off clearance phraseology. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::152f0ffb84869af5` | `phraseology-later` | `PHRASE-1` | Immediate-departure line-up phraseology. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::19cfd36a9fce4587` | `policy-blocked` with scenario evidence | `Icao9432Chunk04RunwayDepartureEvidenceTest`; `POLICY-1` | Aircraft are usually transferred to TOWER at/approaching runway-holding position. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::42b0460ed4f07751` | `phraseology-later` | `PHRASE-1` | Immediate-departure readiness query phraseology. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::4e0bacdd1c2c06e0` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Except emergency, controllers should not transmit during take-off / early climb. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::a93888a25f0bad03` | `phraseology-later` | `PHRASE-1` | `LINE UP AND WAIT` phraseology. |
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::db8a2c3dcd586b0e` | `phraseology-later` | `PHRASE-1` | Taxi phraseology must not imply runway entry / take-off clearance. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::2660849403bff7de` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Departing aircraft must identify arriving aircraft in conditional clearance. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::2e598ad0323e9e2a` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest` | Conditional runway clearance requires controller and pilot sighting. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::9b30810984e06a35` | `phraseology-later` | `PHRASE-1` | Conditional clearance phraseology order. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::c386a5865bdd7876` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Aircraft type may be insufficient; colour/company may be needed. |
| `icao9432-extracted::takeoff_procedures_4_5_6_to_4_5_7_en::dd301daf2b69fe83` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Poor-visibility report-airborne request may be used. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::0afe0064c4c933af` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Abandoned take-off should be reported to tower as soon as practicable. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::2b7c45264775e3e2` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `ControllerInterventionPolicy` | Abandoned take-off should request assistance or taxi instructions as required. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::2cc8caf62c15688b` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Departure instructions may be given with take-off clearance. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6b5a0d8b27525cbd` | `phraseology-later` | `PHRASE-1` | Stop-immediately instruction and callsign repeated during take-off roll. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6bee6c63069d8250` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | Take-off clearance cancellation may be necessary due traffic / long departure. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::81490161201eb712` | `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `POLICY-1` | It may be necessary to quickly free runway for landing traffic. |
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::8af22eb8d9795cef` | `phraseology-later` | `PHRASE-1` | Runway number should be stated in take-off clearance where confusion is possible. |

## Verification

- Source-unit provenance: all 19 accepted candidate JSON records have source
  quotes that normalize-match `research/txt/icao9432-extracted.txt`.
- Focused verification:
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432Chunk04RunwayDepartureEvidenceTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`.

## Review Considerations

- FP / type safety: permanent source refs use typed `EvidenceSourceRef`
  records. No production state or evidence payload type was added.
- Test architecture: the LOWG transfer witness is a real scenario run with
  trace correlation to the `ContactFrequency(TOWER)` transmission id and the
  aircraft's holding-point state. It is scenario evidence only, not universal
  closure.
- Impact: no controller, pilot, sim behaviour, phraseology rendering, or
  policy behaviour was changed.
- Operational correctness: ICAO 9432 §4.5.1's "usually" language remains
  policy-blocked; `may`/`should`/traffic-contingency rows remain gaps.
