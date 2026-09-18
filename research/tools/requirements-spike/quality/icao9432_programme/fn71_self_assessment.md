# fn-71 Self-Assessment

Scope: ICAO 9432 Chapter 9 communications-failure structured projection
coverage for route-appropriate alternate contact attempts, blind-transmission
payload scheduling, SSR 7600/7700 distinction, and blind-clearance
prohibition/exception handling.

## Totality

- New local test projection types are closed (`sealed interface` / `enum`) and
  all `when` expressions are exhaustive without catch-all `else`.
- `acceptedState()` throws only in test assertion code when a witness expected
  an accepted transition and received a typed rejected transition.
- Negative guards reject ordinary no-reply, missed-call, routine frequency
  transfer, non-route-appropriate alternate frequencies, generic PAN PAN, and
  Annex 10/relay overclaims.

## Reversal Completeness

- `CommunicationsRestored` resets mode, contact attempts, blind-transmission
  schedule, SSR code, and blind-clearance originator-request state.
- Reset is covered by a dedicated test with all derived fields populated before
  recovery.

## Interaction Coverage

- This slice is intentionally local/source-mapped projection evidence. It does
  not claim production pilot/controller communications-failure workflow,
  rendered phraseology, production radio scheduling, or Annex 10 conformance.
- `Icao9432ModelGapSourceUnitSpecTest` retains residual controller-side relay,
  ATC-originated blind non-clearance, and Annex 10 blockers.

## Test Coverage

- `Icao9432CommunicationsFailureSourceBackedTest` covers the moved/split rows
  and includes wrong-path/negative guards.
- `Icao9432ModelGapSourceUnitSpecTest` retains exact-union accounting for all
  46 chunk-08 source units.
- Focused verification passed:
  `./gradlew-nix :sim:jvmTest --tests '*.Icao9432CommunicationsFailureSourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`.

## New-Field Audit

- No production state class fields were added.

## Operational Correctness

- Source claims remain mapped to ICAO 9432 §9.5 communications-failure
  procedure rows and ICAO 9432 §9.2 distress SSR row.
- The test deliberately distinguishes communications-failure SSR 7600 from
  distress SSR 7700 and keeps PAN PAN from satisfying distress SSR evidence.
- Rendered `TRANSMITTING BLIND` and receiver-failure phraseology are not
  claimed.

## Error Handling Honesty

- Reachable but unsupported paths return typed `StateTransition.Rejected`.
- The only thrown assertion is the test helper for expected accepted witness
  transitions.

## Deferment Honesty

- Existing programme docs and model-gap specs keep residual blockers visible:
  controller-side relay requests, ATC-originated blind non-clearance workflow,
  Annex 10 conformance, rendered blind-transmission phraseology, and distress
  assistance/any-means behavior.
- No new deferment entry is needed because this slice updates the active 9432
  programme expected-gap ledgers rather than discovering an unrelated shipping
  deferment.
