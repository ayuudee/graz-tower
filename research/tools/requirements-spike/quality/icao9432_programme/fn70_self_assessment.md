# fn-70 Principal Self-Assessment

Epic: `fn-70-icao-9432-emergency-1c-emergency`

## Scope Assessed

Implemented source-backed structured projection evidence for ICAO Doc 9432
Chapter 9 emergency descent:

- typed emergency descent announcement activates safeguarding;
- affected traffic receives a general warning safeguard action;
- emergency descent resolution clears emergency aircraft, affected traffic,
  safeguard actions, and position-question state;
- ordinary descent, go-around, urgency-only/PAN PAN, and radio/FIFO ordering do
  not activate emergency-descent safeguarding;
- uncertain emergency descent position produces a controller position-question
  branch, while known position does not.

This is structured projection evidence, not production controller conflict
resolution, aircraft kinematics, or rendered phraseology.

## Commandment Assessment

1. **Totality**: New model uses sealed interfaces/enums with exhaustive `when`
   expressions over emergency descent events, resolution events, and typed
   transitions. No catch-all `else` branches were added.
2. **Reversal completeness**: Resolution resets all derived state:
   `emergencyAircraftId`, `affectedTraffic`, `safeguardActions`, and
   `positionQuestion`.
3. **Interaction coverage**: The projection is deliberately local and does not
   modify controller, pilot, radio, or kinematic behavior. Tests distinguish
   emergency descent from ordinary descent, go-around, urgency-only, and radio
   ordering.
4. **Test coverage for known features**: Source-backed tests cover the
   safeguarding row, the warning split branch, the position-question branch,
   full reset, and negative non-emergency triggers.
5. **New-field audit**: No production state fields were added.
6. **Operational correctness**: The moved rows are limited to ICAO Doc 9432
   Chapter 9 emergency descent source units. Specific-instruction necessity
   policy remains blocked for `c71568b00fb1535e`.
7. **Error handling honesty**: Test helper `acceptedState()` throws
   `AssertionError` only for failed test expectations. Type-valid model
   transitions return typed `StateTransition` values.
8. **Deferment honesty**: Residual urgency payload/addressing/interference
   policy, assistance/relay, communications failure, blind transmission, SSR,
   and phraseology work remains tracked in chunk-08 expected gaps and the
   implementation status ledger.

## Validation Run Before Review

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432EmergencyDescentSourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`

## Known Boundary

fn-70 does not prove end-to-end controller warning transmission or conflict
resolution during an emergency descent. It proves the source-unit discipline as
a structured projection branch.
