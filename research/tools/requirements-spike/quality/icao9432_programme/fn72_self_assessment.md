# fn-72 Principal Self-Assessment

Epic: `fn-72-icao-9432-emergency-1e-assistance-relay`

## Scope Checked

- Target rows are limited to eight assistance, relay, frequency, and
  interference-suppression source units listed in
  `fn72_assistance_relay_frequency_manifest.md`.
- Non-target rows remain explicit residual gaps: Annex 10 conformance,
  emergency message addressing/payload policy, any-means distress
  communication, emergency-descent specific-instruction necessity,
  controller-side lost-contact relay, ATC-originated blind non-clearance
  workflow, and rendered emergency phraseology/order.
- No production controller, pilot, scheduler, SSR, frequency, or phraseology
  behavior was changed.

## Totality

- The fn-72 projection uses closed local enums/sealed types for emergency
  traffic kind, events, state, actors, assistance content policy, frequency
  policy, interference policy, relay state, resolution, and transitions.
- All `when` expressions over sealed/enum shapes are exhaustive.
- The test-local assertion helper throws only on failed test expectation, not
  as domain behavior for a type-valid operational state.
- Type-valid but unsupported branches return `StateTransition.Rejected`, not
  `error()` or silent defaults.

## Reversal Completeness

- `EmergencyResolution.EmergencyTrafficEnded` resets the projection to
  `Normal`.
- The reset witness checks every derived field introduced by the projection:
  emergency kind, called/no-reply marker, assisting actor, assistance action,
  selected frequency, suppression records, and intercepted-distress relay
  state.
- `RoutineCallAnswered` is rejected as a non-emergency resolution path.

## Interaction Coverage

- The tests are deliberately source-unit projection tests, not claims about
  production radio scheduling or controller/pilot workflows.
- Negative guards separate fn-72 assistance and relay evidence from routine
  traffic, routine no-reply, routine frequency transfer, phraseology-only
  evidence, communications-failure controller relay, non-emergency relay, and
  generic emergency labels without assistance context.
- Ledger movement was traced through the executable gap spec:
  `chunk08EmergencyPrioritySilenceRefs` was removed when emptied, the
  assistance residual was narrowed to `4b37e039e7eb8afa`, and the exact-union
  guard remains responsible for all 46 refs.

## Test Coverage

- `Icao9432EmergencyAssistanceRelaySourceBackedTest` covers:
  - non-addressed station/aircraft assistance after called ground station
    no-reply;
  - configured advice/information/instruction assistance content;
  - current-frequency continuity and alternate-frequency selection;
  - intercepted unacknowledged distress relay by an intercepting aircraft;
  - distress and urgency interference suppression under explicit policy;
  - complete recovery/reset;
  - false-positive guards for non-target contexts.
- `Icao9432ModelGapSourceUnitSpecTest` keeps remaining blockers executable.
- `EvidenceSourceCatalogTest` verifies the source catalog union remains intact.

## New-Field Audit

- No production state classes or fields were added.
- Test-local projection fields are all reset by `normal()` and asserted by the
  recovery test.

## Operational Correctness

- All regulatory claims are anchored to ICAO Doc 9432 Chapter 9 source-unit
  IDs from the accepted source catalog.
- Policy-shaped rows are labeled as configured policy evidence, not universal
  production behavior.
- Rendered phraseology and Annex 10 conformance are not claimed.

## Error Handling And Deferment Honesty

- Unsupported type-valid branches use typed `Rejected` transitions.
- Residual work is recorded in chunk 08 expected gaps, coverage report,
  source plan, and implementation blocker manifest rather than suppressed.
- No new deferment entry was needed because fn-72 is an evidence-led slice of
  the existing ICAO 9432 programme and the residuals remain in the programme
  gap ledger.

## Independent Review Fixes

- Added the missing `@Test` annotation to the emergency message payload/
  addressing residual gap probe so those residual rows are executable, not only
  counted by the exact-union guard.
- Added explicit urgency witnesses for no-reply assistance and current-
  frequency policy so source units that apply to distress and urgency are not
  proven only through distress examples.
- Reordered the local suppression transition so malformed active state with no
  emergency kind is rejected before direct-assistance traffic can be accepted;
  added a negative assertion for that path.
