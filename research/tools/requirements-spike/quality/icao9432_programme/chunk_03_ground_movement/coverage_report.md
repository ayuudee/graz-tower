# Chunk 03 Coverage Report

Chunk: ICAO 9432 ground movement: pushback, powerback, and taxi.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` | 0 |
| `covered-red` | 1 |
| `model-gap` | 6 |
| `policy-blocked` | 2 |
| `phraseology-later` | 2 |

Chunk 03 deliberately produces no green universal closure. The most important
result is the covered-red structural audit for ICAO 9432 §4.4 taxi clearance
limits: current typed taxi instructions do not guarantee a clearance-limit
field across the whole taxi-like instruction space.

## Coverage Table

| Source unit | Final state | Test / blocker | Claim |
|---|---|---|---|
| `icao9432-extracted::pushback_powerback_4_3_en::1aae1f61b91984e8` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `PUSHBACK-1` | Power-back is aircraft reverse movement using engine power. |
| `icao9432-extracted::pushback_powerback_4_3_en::5980a8f786170b01` | `model-gap` + `policy-blocked` | `Icao9432ModelGapSourceUnitSpecTest`; `PUSHBACK-1`; `LocalProcedurePolicy` | Push-back/power-back requests go to ATC or apron management depending on local procedures. |
| `icao9432-extracted::pushback_powerback_4_3_en::b3652213a568f55f` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest`; `PUSHBACK-1` | Ground crew signals when the aircraft is free to taxi. |
| `icao9432-extracted::pushback_powerback_4_3_en::da5fd317668b375a` | `phraseology-later` | `PHRASE-1` | Stop-pushback phraseology. |
| `icao9432-extracted::pushback_powerback_4_3_en::fc3dfdf7cc913637` | `phraseology-later` | `PHRASE-1` | Pilot / ground-crew pushback coordination phraseology. |
| `icao9432-extracted::taxi_4_4_en::03985c8e2cf3f473` | `policy-blocked` | `POLICY-1`; `LocalProcedurePolicy` | Taxi limit may be another aerodrome position depending on traffic. |
| `icao9432-extracted::taxi_4_4_en::1367907005a34ad1` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest` | Taxi limit beyond runway requires cross clearance or hold-short instruction. |
| `icao9432-extracted::taxi_4_4_en::417f64324f7495bf` | `policy-blocked` with scenario evidence | `POLICY-1`; legacy `Icao9432TaxiSourceBackedScenarioTest` scenario leg | Departing taxi limit normally holding point. |
| `icao9432-extracted::taxi_4_4_en::53f33b6da4f2be58` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest` | ATIS acknowledgement removes need to pass departure information with taxi instruction. |
| `icao9432-extracted::taxi_4_4_en::b9e7fc3605fe616e` | `covered-red` | `Icao9432Chunk03GroundMovementEvidenceTest` | Taxi instruction always contains a clearance limit. |
| `icao9432-extracted::taxi_4_4_en::eadf2541fcd51825` | `model-gap` | `Icao9432ModelGapSourceUnitSpecTest` | Runway vacated when entire aircraft is beyond holding position. |

## Verification

- Source-unit provenance: all 11 accepted candidate JSON records have
  `verbatimQuoteCheck.status = pass`, `lifecycle.state = accepted`, and
  normalized source quotes match `research/txt/icao9432-extracted.txt`.
- Focused verification:
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432Chunk03GroundMovementEvidenceTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`.

## Review Considerations

- FP / type safety: source refs are typed and registry-validated. The taxi
  clearance-limit audit uses typed protocol leaves, not rendered strings.
- Test architecture: the universal taxi-limit source lands covered-red rather
  than being narrowed to one passing LOWG trace. Pushback and runway-vacated
  rows are model gaps, not skipped rows.
- Impact: no pushback, ground-crew, apron-management, phraseology, or
  local-procedure policy behaviour was added.
- Operational correctness: ICAO 9432 §4.3 local-procedure/pushback actor
  requirements and §4.4 "normally/may/depending" language are not asserted as
  unconditional simulator law.
