# fn-65 Self-Assessment

Epic: `fn-65-icao-9432-vehicle-1b-vehicle-runway`

## Scope Delivered

- Added vehicle-specific runway state:
  `HoldingShort`, `Crossing`, `OnRunway`, and `ClearBeyondHoldingPoint`.
- Added vehicle runway transmissions:
  `HoldShortRunway`, `CrossRunway`, `VacateRunway`,
  `AcknowledgeRunwayCrossing`, and `RunwayVacated`.
- Added source-backed tests for:
  - ICAO Doc 9432 §5.3.1 positive permission + acknowledgement before runway
    crossing (`24f7b6a86407ef6f`).
  - ICAO Doc 9432 §5.3.1 vehicle-only runway-vacated report after clear beyond
    holding point (`735b3e9ada06105b`, split branch only).
- Kept ICAO Doc 9432 §5.3.2 (`331c1cfc98ead868`) as a model gap because this
  pass did not add an explicit expected landing/takeoff trigger.

## Principal-Agent Checklist

- Totality: new sealed transmission leaves are handled in `Comms.kt` and
  `Step.kt` without catch-all `else` branches.
- Reversal completeness: no reversible state transition pair was introduced.
  Runway crossing permission is consumed on acknowledgement; runway state is
  explicitly cleared by `RunwayVacated` only after clear-beyond-holding-point
  evidence.
- Interaction coverage: vehicle driver/controller actor-payload mismatch checks
  remain generic over the sealed vehicle transmission types and therefore cover
  the new leaves. Source-backed tests exercise controller delivery, driver
  transmission reception, and scheduled clear-beyond-holding-point handling.
- Test coverage for known features: focused tests cover the positive crossing
  path, positive vacated-report path, and premature vacated-report failure.
- New-field audit: `VehicleState` gained `activeRunwayCrossing` and
  `activeRunwayVacate` and `runwayState`. The vehicle mutation sites in
  `Step.kt` were reviewed: blocked phases clear movement permission; hold-short
  clears movement, crossing, and vacate permissions; crossing acknowledgement
  consumes the runway crossing permission; clear-beyond-holding-point is
  correlated with the active vacate token and clears movement/crossing/vacate
  permissions.
- Operational correctness: the moved claims are limited to ICAO Doc 9432
  §5.3.1. §5.3.2 remains blocked without an expected-aircraft-operation trigger.
- Error handling honesty: premature runway-vacated reports and runway-crossing
  permission in the wrong state fail loudly. Scheduled clear-beyond-holding-
  point events follow the existing stale scheduled-event no-op pattern used by
  `VehicleArriveAtLimit`.
- Deferment honesty: remaining §5.3/§5.4 work is still represented in the
  chunk-07 expected gaps and implementation blocker manifest:
  dangerous-situation policy, expected-aircraft-operation triggers, tow extent,
  tow metadata, and phraseology.

## Verification So Far

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432VehicleMovementSourceBackedScenarioTest' --tests '*.Icao9432VehicleRunwaySourceBackedScenarioTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
