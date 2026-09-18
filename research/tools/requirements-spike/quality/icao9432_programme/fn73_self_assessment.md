# fn-73 Principal Self-Assessment

Epic: `fn-73-icao-9432-emergency-1f-message`

## Scope Checked

- Target rows are limited to four message addressing/payload-policy source
  units listed in `fn73_message_policy_manifest.md`.
- Non-target rows remain explicit residual gaps: rendered emergency
  phraseology/order, Annex 10 conformance, any-means distress communication,
  emergency-descent follow-up instruction necessity, and controller
  communications-failure relay/blind non-clearance workflow.
- No production controller, pilot, radio scheduler, SSR, frequency, or
  phraseology-rendering behavior was changed.

## Totality

- The fn-73 projection uses closed local enums/sealed types for message kind,
  event, state, sender, addressing policy, relayed variation policy, urgency
  payload policy, urgency addressing policy, addressee, payload elements,
  frequency selection, policy marker, resolution, and transitions.
- `when` expressions over sealed/enum shapes are exhaustive.
- Unsupported type-valid branches return `StateTransition.Rejected`, not
  `error()` or silent defaults.
- The test-local assertion helper throws only on failed test expectation.

## Reversal Completeness

- `EmergencyMessageResolution.EmergencyMessageComplete` resets the projection
  to `Normal`.
- The reset witness checks every derived field introduced by the projection:
  emergency kind, sender, addressee, responsible-area station, selected payload
  elements, stated variation reason, frequency selection, and policy marker.
- `RoutineMessageComplete` is rejected as a non-emergency resolution path.

## Interaction Coverage

- The tests are source-unit projection tests, not claims about production radio
  scheduling, controller workflow, or rendered phraseology.
- Negative guards separate fn-73 message policy evidence from routine traffic,
  phraseology-only emergency evidence, generic emergency labels, cross-use of
  distress/urgency contexts, unclear relayed variation, relayed variation by
  the distressed aircraft, and communications-failure controller workflow.
- Ledger movement was traced through the executable gap spec:
  the four moved rows left the message residual group, the residual wording was
  rewritten to phraseology/order only, and the exact-union guard remains at 46
  refs.

## Test Coverage

- `Icao9432EmergencyMessagePolicySourceBackedTest` covers:
  - distress addressing to current station;
  - distress addressing to responsible-area station;
  - relayed distress payload variation with explicitly stated circumstance;
  - urgency payload policy with required elements present and non-required
    element omitted;
  - urgency payload rejection when a required element is missing;
  - urgency current-frequency/current-station addressing;
  - urgency current-frequency/responsible-area addressing;
  - complete recovery/reset;
  - false-positive guards for non-target contexts.
- `Icao9432ModelGapSourceUnitSpecTest` keeps remaining blockers executable.
- `EvidenceSourceCatalogTest` verifies the source catalog union remains intact.

## New-Field Audit

- No production state classes or fields were added.
- Test-local projection fields are all reset by `normal()` and asserted by the
  recovery test.

## Operational Correctness

- Regulatory claims are anchored to ICAO Doc 9432 Chapter 9 source-unit IDs
  from the accepted source catalog.
- Policy-shaped rows are labeled as configured policy evidence, not universal
  production behavior.
- Rendered MAYDAY/PAN PAN wording, repetition, order, and Annex 10 conformance
  are not claimed.

## Error Handling And Deferment Honesty

- Unsupported type-valid branches use typed `Rejected` transitions.
- Residual work is recorded in chunk 08 expected gaps, coverage report, source
  plan, and implementation blocker manifest rather than suppressed.
- No new deferment entry was needed because fn-73 is an evidence-led slice of
  the existing ICAO 9432 programme and the residuals remain in the programme
  gap ledger.

## Independent Review Fixes

- Added the missing recovery assertion for `currentStation` so every derived
  projection field is proven cleared by reset.
- Changed `EmergencyMessageComplete` resolution to reject normal/non-active
  state instead of accepting a silent no-op reset.
- Strengthened the urgency payload witness to assert that all configured
  required elements survive selection, not just one representative element.
