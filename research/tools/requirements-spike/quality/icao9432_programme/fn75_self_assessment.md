# fn-75 Principal Self-Assessment

Epic: `fn-75-icao-9432-emergency-1h-pilot-safety`

Scope: move
`icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98`
from standalone `model-gap + policy-blocked` accounting into source-backed
configured coverage for ICAO Doc 9432 §9.1.7.

Non-scope remains explicit residual accounting: Annex 10 conformance and
rendered emergency phraseology/order. Previously split rows still document
residual any-means distress communication and emergency-descent
specific-instruction policy facets.

## Assessment

- Totality: the fn-75 projection uses closed local sealed/enumerated types and
  typed `Rejected` transitions for type-valid wrong paths. No production
  `error()`, nullable default, or catch-all `else` was introduced.
- Reversal completeness: `resolve(AssistanceNoLongerNeeded)` returns the
  projection to `normal()` and the reset witness checks state, safety-doubt
  reason, policy marker, assistance request, and source witness.
- Interaction coverage: the evidence is intentionally local to the source-unit
  wall. It does not claim production pilot decision scheduling, rendered
  phraseology, Annex 10 conformance, or a complete taxonomy of safety-doubt
  causes. Wrong-path guards separate safety doubt from routine preference,
  passenger convenience, ATC-originated prompts, phraseology-only evidence,
  generic emergency labels, no active state, and missing/non-safety policy.
- Test coverage for known features: focused source-backed tests cover the
  positive safety-doubt + policy path, reset, and negative guards.
- New-field audit: no production state fields were added. The local test
  projection's fields are all reset by `normal()` and checked by the recovery
  witness.
- Operational correctness: fn-75 claims only ICAO Doc 9432 §9.1.7's advice
  that pilots seek assistance whenever flight safety is in doubt.
- Error handling honesty: type-valid unsupported paths return `Rejected`. The
  only thrown failure is the test helper's `AssertionError` when a witness
  expected an accepted transition but received a rejection.
- Deferment honesty: fn-75 did not discover a new production deferment. The
  remaining residual source units and split residual facets are still listed in
  the chunk 08 ledgers and implementation blocker manifest.

## Review Focus

- Check that the moved source unit is not over-claimed as production pilot
  behavior or rendered phraseology.
- Check that generic MAYDAY/PAN PAN labels alone cannot satisfy the safety-doubt
  source unit.
- Check that policy gating is explicit and does not silently suppress a
  witnessed safety doubt.
- Check that exact-union accounting and chunk ledgers still agree.
