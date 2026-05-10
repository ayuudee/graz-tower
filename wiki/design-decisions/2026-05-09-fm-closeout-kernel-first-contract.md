# FM closeout: kernel-first certified runtime contract

Date: 2026-05-09

## Decision

Close the current FM research stream around a kernel-first certified runtime
contract instead of continuing to make the optional single-issuer orchestration
theorem the default completion target.

The delivered FM artifact is:

- four independent certified Lean kernels: runway, surface, air-path, and
  separation
- the scoped safety / full-brief package over the current model
- the Kotlin-facing parity / refinement registry and drift-control rules
- an explicit non-claim that the whole ATC agent or whole Kotlin issuance path
  is formally verified

Controller work now owns operational composition. The controller may consume
the certified kernels and delivered theorem surfaces as guardrails, but it must
decide which checks an operational proposal requires and must keep regulatory
and phraseology claims cited at the controller-rule level.

## Rationale

The original FM value was independent certification of the core safety
methods. That value is now present and checked. Continuing to widen the
optional composition layer risks turning research closure into an open-ended
instruction-family expansion exercise.

The kernel-first contract gives the product something usable:

- checked local certifiers
- explicit ownership boundaries
- a clear Kotlin drift contract
- a small, honest claim surface for controller integration

It also keeps the stronger single-issuer theorem available as future work if
the product architecture actually needs it.

## Consequences

- `research/fm/certified_runtime_contract_v1.md` is the closeout contract.
- `research/fm/parity_inventory.md`,
  `research/fm/refinement_inventory.md`, and
  `research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean` define
  the Kotlin-facing delivered boundary.
- Future FM work should strengthen a kernel, repair drift, or add a small
  controller-needed theorem surface. It should not widen instruction families
  just because the pattern is available.
- Unsupported controller behavior must remain visibly unsupported. The FM
  closeout must not be marketed as whole-agent verification.

## Review Considerations

FP / type safety:

- new controller-visible instruction families must not silently inherit a
  certified status through default branches
- Kotlin metadata drift must fail tests until the parity inventory and Lean
  registry are updated

Test architecture:

- Lean root build verifies the proof-side contract
- Kotlin drift tests verify runtime alignment for delivered branches
- controller tests remain responsible for operational decision behavior

Impact:

- reduces FM scope pressure and creates a stable integration boundary
- gives up a whole-issuer certification claim unless/until that theorem is
  deliberately reopened

Operational correctness:

- real-world procedure and phraseology remain controller obligations and must
  cite their regulatory sources there
