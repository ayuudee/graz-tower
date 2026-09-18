# fn-60 principal self-assessment

Status: completed before final review/red-team.

## Totality

- New policy domain types are closed: `TaxiClearanceLimitPolicy` and
  `TowerTransferPolicy` are enums, and `OperationalPolicyScope` is sealed.
- New evidence selector has no swallowing `else`; it filters configured-policy
  facts and fails when none or the wrong branch is present.
- No new `error()` was added for type-valid states.

## Reversal Completeness

- No runtime sim state, controller state, or pilot state was added.
- No forward/reversal state transition pair was introduced.
- If later work moves policies into fixture/controller state, that must be a
  new impact assessment with copy-site and reversal audit.

## Interaction Coverage

- The two source-backed scenarios still run through the live LOWG sim path.
- The configured policy branch is explicit in the witness, and live trace
  assertions still prove the operational behavior:
  - taxi clearance destination is a RWY 16C holding point before runway use;
  - GROUND transfers to TOWER while the aircraft is holding short at the RWY
    16C holding point.

## Test Coverage For Known Features

- `EvidenceDslTest` covers configured-policy pass, wrong-branch fail, and
  no-policy-fact fail.
- `Icao9432TaxiSourceBackedScenarioTest` covers the configured taxi branch.
- `Icao9432Chunk04RunwayDepartureEvidenceTest` covers the configured tower
  transfer branch.
- Remaining policy rows stay blocked/untouched in docs and expected-gap specs.

## New-Field Audit

- No existing state data class gained a field.
- One new evidence payload leaf was added:
  `EvidenceFactPayload.ConfiguredPolicy`.
- `EvidenceFactKind` was extended with `ConfiguredPolicy`; test coverage
  exercises projection through `fromProjectedPayloads` and selector activation.

## Operational Correctness

- ICAO Doc 9432 §4.4 normal taxi-limit language is represented as configured
  LOWG policy, not universal law.
- ICAO Doc 9432 §4.5.1 "usually transferred" language is represented as
  configured LOWG separate GROUND/TOWER policy, not universal law.
- No new controller behavior or phraseology was invented.

## Error Handling Honesty

- Missing policy evidence fails the source/evidence audit.
- Wrong configured branch fails while activating the examined policy fact.
- There is no default policy profile that can silently green a source unit.

## Deferment Honesty

- No new deferment was filed because the selected scope is complete.
- Larger policy rows remain in `fn60_policy_matrix.md` as blocked/untouched,
  and model-dominated rows remain covered by their later epics.

## Known Review Risk

- The configured policy is bound locally in source-backed tests, not read from a
  fixture-wide policy profile. This is deliberate for fn-60 to avoid global
  defaults. If policy is later used by runtime behavior, it should become a
  fixture/controller input through a separate design.
