# fn-60 policy substrate impact assessment

Status: pre-implementation impact assessment for
`fn-60-icao-9432-policy-1-typed-policy`.

This assessment follows `fn60_policy_matrix.md` and intentionally selects the
smallest proof set that can validate the policy-evidence shape without turning
ICAO Doc 9432 "normally", "usually", "may", or "should" language into
universal simulator law.

## Decision

Implement a minimal typed policy-evidence substrate for source-mapped tests,
then move only these two source units from policy-blocked scenario evidence to
configured-policy green:

- `icao9432-extracted::taxi_4_4_en::417f64324f7495bf`
- `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::19cfd36a9fce4587`

The implementation should not change controller behavior. The existing LOWG
behavior is already the evidence. The missing piece is an explicit, typed
configured-policy binding tied to the source assertion so the test says:

1. LOWG is configured for this branch.
2. The live trace satisfies the branch.
3. The source unit is not being claimed universally.

## Minimal Policy Model

Recommended production surface:

- A small closed policy vocabulary in `sim/src/commonMain` or another shared
  module already used by source-mapped tests.
- Policy IDs and branches are sealed or enum-backed, not strings.
- First branches only:
  - `TaxiClearanceLimitPolicy.DeparturesNormallyToRunwayHoldingPoint`
  - `TowerTransferPolicy.SeparateGroundTowerTransferAtHoldingPoint`
- A typed policy binding payload usable by evidence tests:
  - source unit reference
  - scope, for example `LOWG`, `RWY 16C`, or `separate-ground-and-tower`
  - configured branch
  - citation/provenance

Do not add a global default profile in this epic. Defaults create the most
likely false-green path: tests start passing because a default exists, not
because a scenario deliberately bound a policy.

## Coupling And Failure Modes

Coupling introduced:

- Source-mapped evidence tests will couple to a small policy vocabulary.
- The evidence DSL may gain a selector for "configured policy exists".
- The two live scenario tests may include policy binding alongside existing
  trace checks.

Coupling avoided:

- No controller planner/rule behavior changes.
- No fixture-wide policy profile yet.
- No policy branch inference from aerodrome ID.
- No general `OperationalGuidancePolicy` catch-all.

Failure modes and guards:

- False green by enum existence: prevent by requiring both configured policy
  evidence and live behavior evidence in the source-mapped expectation.
- False green by default profile: do not create global defaults in fn-60.
- Local procedure becomes doctrine: every green test must state non-universal
  scope and configured alternatives.
- Policy vocabulary grows into a dumping ground: only add branches tied to the
  two green-targeted source units. Other rows remain blocked in the matrix.
- Hidden stringly-typed policy names: use closed branch types, not free text.

## Reversal And State

No sim state transition is planned. Therefore:

- There is no forward/reversal handler pair to audit.
- No state data-class field should be added in fn-60.
- If implementation discovers a need for fixture- or controller-state policy,
  stop and revise this assessment before coding. That would require copy-site
  and reversal audit under AGENTS.md.

The implementation is reversible by deleting the policy evidence type/selector
and reverting the two source-mapped policy assertions. It must not alter runtime
controller behavior, so rollback should not affect the golden flows.

## Test Architecture

Required tests:

- Unit-level evidence selector tests for policy binding, only if the selector
  has non-trivial behavior.
- High-level source-mapped tests for the two configured-policy branches:
  - taxi clearance limit to holding point before runway use at LOWG RWY 16C;
  - GROUND-to-TOWER transfer at the holding point before runway use at LOWG
    with separate GROUND/TOWER.
- Model-gap/source-unit spec tests must continue to report remaining policy
  rows as blocked or expected gaps.

Tests to avoid:

- Enum-existence tests.
- Tests that assert "the default policy says X".
- Tests that turn `normally`, `usually`, or `may` into mandatory simulator
  behavior outside the configured branch.

## Operational Correctness

Operational source basis:

- ICAO Doc 9432 §4.4: departing aircraft clearance limit normally the
  taxi-holding point of the runway in use.
- ICAO Doc 9432 §4.5.1: at busy aerodromes with separate GROUND and TOWER,
  aircraft are usually transferred to TOWER at or approaching the
  runway-holding position.

These are not absolute rules. The correct test posture is configured
LOWG-specific behavior with explicit non-universal scope.

## Deferments

No new deferment is required for the two selected source units if the
implementation stays evidence-only. Existing blocker rows remain represented by
the matrix and existing expected-gap tests.

If implementation exposes a missing test-contract surface for policy bindings,
file it in `docs/deferments.md` before commit instead of adding a quiet skip.

## Review Considerations

FP / type safety:

- Closed policy branches; no stringly branch selection.
- Exhaustive selectors over policy branch kind; no swallowing `else`.
- No `error()` for type-valid unknown policy values. If a branch exists but is
  unsupported by a selector, return an explicit evidence failure.

Test architecture:

- Source-mapped tests prove a branch under explicit configured policy and live
  trace facts.
- Existing blocked rows remain red/blocked; no broad policy fixture can make
  them green.
- Policy binding itself is supporting evidence, not the entire assertion.

Impact:

- Low runtime impact if evidence-only.
- Medium test-surface impact because source tests gain a policy dimension.
- Main failure mode is overclaiming; guarded by matrix disposition and
  non-universal assertions.

Operational correctness:

- Both proof units cite ICAO Doc 9432 extracted §4.4/§4.5.1 rows.
- The implementation must preserve the words "normally" and "usually" as
  configured practice, not law.
