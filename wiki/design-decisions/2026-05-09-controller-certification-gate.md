# Controller certification gate

Date: May 9, 2026

## Decision

Make controller instruction emission certification-gated by construction.

Procedures may produce `ProposedAction`s, but normal controller instructions
must be emitted from `CertifiedInstruction`s. `ControllerOutput.Instruct` should
stop being a public data class and become a private-constructor output with
named factories for certified, administrative, reissue, and emergency-policy
paths.

The implementation plan is documented in
`docs/design/controller-certification-gate-plan.md`.

## Rationale

The FM closeout certified four independent kernel families: runway, surface,
air-path, and separation. Kotlin currently has strong drift guards for delivered
clearance semantics, but controller output construction is still possible in
multiple places without a first-class certification boundary.

Visibility and type construction should carry the architectural rule:
controller code should not have to remember to call the certifier; it should be
unable to emit a normal instruction without one.

## Consequences

- The controller gets a runtime counterpart to the FM kernel-first contract.
- New safety-relevant instruction emissions must declare a certification plan.
- Administrative and emergency outputs remain possible, but only through named,
  evidenced paths.
- Coordination reissue needs special handling so replayed instructions carry or
  reference the original certificate rather than bypassing certification.

## Review Considerations

FP / type safety:

- certified wrappers and `ControllerOutput.Instruct` must not expose public
  `copy`
- certification failures are typed, not thrown
- classification must be leaf-explicit and fail closed

Test architecture:

- use firewall tests for non-bypass
- use controller integration tests to prove emitted outputs carry evidence
- keep existing core drift tests intact

Impact:

- expected touch points are controller output construction, arbitration,
  reactive separation, coordination reissue, and controller tests
- protocol and core clearance semantics should remain unchanged in the first
  milestone

Operational correctness:

- procedure rules still carry regulation and phraseology references
- certification answers local safety/coherence under a snapshot, not full
  regulatory correctness
