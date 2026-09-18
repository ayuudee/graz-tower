# fn-74 Principal Self-Assessment

Epic: `fn-74-icao-9432-emergency-1g-controller-lost`

Scope: move these ICAO 9432 Chapter 9 controller-side lost-contact source
units from executable model-gap accounting into source-backed configured
coverage:

- `icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2`
- `icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e`
- `icao9432-extracted::communications_failure_9_5_en::75055714e70d4560`

Non-scope remains explicit residual accounting: Annex 10 conformance,
rendered emergency/communications-failure phraseology, any-means distress
assistance, emergency-descent follow-up specific instructions, and pilot
safety-doubt triggers.

## Assessment

- Totality: the fn-74 projection uses closed local types and typed
  `Rejected` transitions for type-valid wrong paths. No production `error()`
  or nullable default was introduced.
- Reversal completeness: `resolve(ContactRestored)` returns the projection to
  `normal()` and the reset witness checks the workflow state, direct-contact
  frequencies witness, relay requests, relay results, believed-listening marker,
  blind transmission, and policy marker.
- Interaction coverage: the evidence is intentionally local to the
  source-unit wall. It does not claim production controller scheduling,
  radio rendering, or phraseology. Wrong-path guards separate controller-side
  lost contact from routine traffic, pilot-originated communications failure,
  generic emergency labels, phraseology-only evidence, missing relay failures,
  generic direct-contact failure, missing believed-listening evidence, and blind
  clearances.
- Test coverage for known features: focused source-backed tests cover route
  aircraft relay request, other-station relay request, non-clearance blind
  transmission after failed station attempts, recovery reset, and negative
  guards.
- New-field audit: no production state fields were added. The local test
  projection's fields are all reset by `normal()` and checked by the recovery
  witness.
- Operational correctness: fn-74 treats ICAO Doc 9432 §9.5.6 route-aircraft
  and other-station relay assistance as separate available branches after calls
  fail on frequencies the aircraft is believed to be listening on, and treats
  §9.5.7 ATC blind transmission as
  non-clearance-only evidence after unsuccessful station attempts and a
  believed-listening condition. Blind clearances remain governed by the
  earlier fn-71 boundary.
- Error handling honesty: type-valid unsupported paths return `Rejected`.
  The only thrown failure is the test helper's `AssertionError` when a witness
  expected an accepted transition but received a rejection.
- Deferment honesty: fn-74 did not discover a new production deferment. The
  remaining residual source units are still listed in the chunk 08 ledgers and
  implementation blocker manifest.

## Review Focus

- Check that the three moved source units are not over-claimed as production
  behavior.
- Check that §9.5.6 route-aircraft and other-station relay assistance remain
  independent evidence branches under the believed-listening-frequency failure
  precondition, while §9.5.7 blind transmission still requires failed station
  attempts.
- Check that blind clearances remain rejected and do not weaken fn-71's
  source-backed clearance boundary.
- Check that exact-union accounting and chunk ledgers still agree.
