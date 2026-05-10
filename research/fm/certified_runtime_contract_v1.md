# Certified Runtime Contract v1

Last updated: May 9, 2026

This is the closeout contract for the current `research/fm` stream.

The project is no longer trying to make the optional single-issuer
orchestration theorem the definition of success. The useful delivered result is
smaller and sharper:

- four independent Lean certifiers with checked local soundness claims
- a theorem-bearing scoped safety package over those certifiers
- a registry of delivered Kotlin-facing theorem surfaces
- drift-control rules that keep Kotlin runtime semantics from silently moving
  away from the Lean claim

Future safety work should move upward into the controller architecture. The
controller may consume the certified kernels and delivered theorem registry as
guardrails, but the controller remains responsible for deciding which
certifier checks to request for an operational situation.

## Certified Kernels

These are the core certified methods.

| Kernel | Lean function | Checked theorem | Claim |
| --- | --- | --- | --- |
| Runway | `runway_certify` | `RunwayKernelMilestone1Theorem` | Approved runway proposals preserve runway-local invariants. |
| Surface | `surface_certify` | `SurfaceKernelSoundnessTheorem` | Approved surface proposals are legal for the modeled surface graph and preserve surface-local invariants. |
| Air path | `air_certify` | `AirKernelSoundnessTheorem` | Approved air proposals are legal for the modeled airborne graph and preserve air-local invariants. |
| Separation | `separation_check` | `SeparationCheckerSoundnessTheorem` plus scoped separation theorems | Accepted separation scenarios satisfy the modeled separation rule set; the scoped continuation and viability packages close the current nominal separation surface. |

The current root build checks these claims through
`lake build CertifiedAtc`.

## Scoped Safety Package

The scoped package is also closed for the current model:

- `ScopedGreenfield`
- `ScopedIssuance`
- `ScopedSafety`
- `ScopedModes`

This package is still deliberately scoped. It is not a proof that the whole ATC
agent is strategically correct, and it is not a proof that every future
controller action is automatically routed through the right certifier bundle.

## Kotlin Relationship

The Kotlin relationship is a drift-guarded contract, not a full formal
refinement proof.

The authoritative map is:

- [parity_inventory.md](parity_inventory.md) says what Kotlin-facing branches
  are closed and under what model.
- [refinement_inventory.md](refinement_inventory.md) says where each closure is
  enforced by Kotlin tests and Lean theorem anchors.
- [GreenfieldDeliveredRefinement.lean](lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean)
  is the central Lean registry for delivered theorem surfaces.

A branch may be called delivered only when all of the following are true:

- the Kotlin runtime semantics are explicit
- the Lean model matches that runtime boundary
- tracked Kotlin tests fail on drift in metadata, authority, resolution,
  completion, supersession, or lifecycle behavior
- the Lean theorem surface is imported by the root build
- the FM status docs name the exact claim and the exact non-claim

This deliberately stops short of saying Kotlin is mechanically refined from
Lean. That would be a different project.

## Controller Contract

Controller work should treat the FM output as a certified toolbox:

- call or mirror the runway kernel for runway-resource commitments
- call or mirror the surface kernel for surface graph movement and protected
  entry
- call or mirror the air-path kernel for graph-backed airborne transitions
- call or mirror the separation checker for modeled pairwise safety
- use the delivered refinement registry to know which higher-level instruction
  families already have theorem-bearing semantics and drift gates

The controller owns the operational decision:

- which clearances are operationally appropriate
- which certifier checks are required for a proposed action
- which regulatory procedure and phraseology justify the action
- how unsupported or not-yet-certified branches fail loudly

The FM closeout does not certify controller judgment. It supplies checked local
methods and model-alignment guardrails that the controller can build on.

## Explicit Non-Claims

This contract does not claim:

- the full ATC agent is formally verified
- every Kotlin issuance path is forced through a proved single issuing layer
- the optional `CanonicalTopLevelTheorem` is complete
- polygonal / continuous airspace geometry is proved
- richer route-bearing behavior beyond the current graph-backed
  published-procedure model is proved
- richer communications, surveillance, coordination, or mode semantics are
  proved
- regulatory or phraseology correctness unless a controller rule cites and
  implements the relevant source directly

These are not hidden defects in the closeout. They are deliberately future
controller / product work.

## Future Work Rule

New FM work should happen only when it does one of these:

1. strengthens one of the four local certifiers
2. repairs Kotlin drift against an already delivered theorem surface
3. adds a small closed theorem surface needed by a controller feature
4. replaces a current conservative model with a more faithful one and updates
   Kotlin drift gates at the same time

Do not widen instruction families merely because the current Lean pattern makes
it mechanically easy.

## Review Considerations

FP / type safety:

- delivered Kotlin branches must keep exhaustive instruction metadata and
  lifecycle classification; unknown runtime families must fail drift gates or
  remain outside the certified claim
- no `else` / default runtime behavior should silently classify a new
  instruction as certified

Test architecture:

- Lean build proves the proof-side model
- Kotlin drift tests guard the runtime boundary
- both are required for a delivered branch

Impact:

- this closeout reduces FM scope pressure and makes controller integration the
  next architectural owner
- the cost is that whole-agent and whole-issuer correctness remain unproved

Operational correctness:

- controller rules still need regulation and phraseology citations
- FM claims here are structural safety / lifecycle / authority claims unless a
  specific operational source is cited elsewhere
