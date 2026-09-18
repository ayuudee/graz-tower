# fn-60 review resolution

Status: final review findings resolved.

Reviewer result: no blocking findings.

Resolved medium findings:

- Evidence wording drift: `fn60_policy_matrix.md` and
  `fn60_policy_impact_assessment.md` now say configured-policy binding where
  the source-backed scenario specs bind policy locally. The separate
  `EvidenceFactPayload.ConfiguredPolicy` selector remains covered by
  `EvidenceDslTest` as reusable evidence infrastructure, but the two green
  source-unit specs do not claim to consume a projected policy fact.
- Branch/scope mismatch risk: `ConfiguredOperationalPolicy` is now generic over
  the scope type, and policy branch enums implement `OperationalPolicyBranch<S>`
  for their valid scope. A tower-transfer branch cannot be paired with a runway
  scope, and a taxi-clearance-limit branch cannot be paired with a
  service-shape scope through this constructor.

Residual risk accepted:

- Policy bindings are still authored locally in tests rather than loaded from
  fixture/runtime configuration. This is deliberate in fn-60 to avoid global
  defaults and false greens. Runtime policy configuration should be a later
  design/impact pass.
