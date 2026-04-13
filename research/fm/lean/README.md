# Lean Project Guide

This is the standalone Lean 4 project for the FM work inside `twr2`.

## Build

From the repository root:

```bash
nix-shell -p lean4 --run 'cd research/fm/lean && lake build'
```

## Module Order

Read modules in roughly this order:

1. [CertifiedAtc/Core.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/Core.lean)
2. [CertifiedAtc/CommandCatalog.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/CommandCatalog.lean)
3. [CertifiedAtc/RunwayKernel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RunwayKernel.lean)
4. [CertifiedAtc/SurfaceKernel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SurfaceKernel.lean)
5. [CertifiedAtc/AirKernel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/AirKernel.lean)
6. [CertifiedAtc/SeparationChecker.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SeparationChecker.lean)
7. [CertifiedAtc/Interfaces.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/Interfaces.lean)
   Optional. This is the existing atomic orchestration layer.
8. [CertifiedAtc/ScopedSeparation.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSeparation.lean)
   Optional. This is the scoped `Safety-complete (N₀)` separation package over
   the current certifier and orchestration surfaces.
9. [CertifiedAtc/ScopedExtraction.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedExtraction.lean)
   Optional. This is the scoped extraction boundary from proof-side world facts
   into `ClearanceCompileView`, certifier-local views, and issuer-authority
   facts.
10. [CertifiedAtc/ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
   Optional. This is the older greenfield-to-atomic staging compiler and theorem surface.
11. [CertifiedAtc/GreenfieldModel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldModel.lean)
   Optional. This is the current Kotlin-aligned greenfield boundary for protocol, compound clearances, conditional normalization, and lifecycle/frontier reasoning.
12. [CertifiedAtc/GreenfieldLifecycle.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldLifecycle.lean)
    Optional. This is the abstract active-clearance state machine over the Kotlin-aligned model: staging, supersession, completion advancement, and conditional activation.
13. [CertifiedAtc/GreenfieldResolved.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldResolved.lean)
    Optional. This is the proof-side resolved execution boundary aligned to Kotlin `ResolvedStep` / `ResolvedClearance`.
14. [CertifiedAtc/GreenfieldResolution.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldResolution.lean)
    Optional. This is the proof-side world-to-resolved relation: it states what world facts justify a resolved step/clearance.
15. [CertifiedAtc/GreenfieldCompletion.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldCompletion.lean)
    Optional. This evaluates structured observations against resolved steps: reached point, runway transition, circuit membership, altitude/speed, radio role/frequency, and transponder state.
16. [CertifiedAtc/GreenfieldExecution.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExecution.lean)
    Optional. This is the resolved active-clearance layer: managed resolved clearances, resolved completion, and active-set reconciliation.
17. [CertifiedAtc/GreenfieldReachability.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldReachability.lean)
    Optional. This packages execution preservation results into a reachable active-set boundary.
18. [CertifiedAtc/ScopedGreenfield.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedGreenfield.lean)
    Optional. This is the scoped greenfield theorem package for
    `Safety-complete (N₀)`: scoped authority mapping, no-partial-issuance for
    surface compounds, conditional surface-envelope normalization, and the
    reachability wrapper into the resolved execution layer.
19. [CertifiedAtc/ScopedIssuance.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedIssuance.lean)
    Optional. This is the final scoped issuing layer for `Safety-complete (N₀)`:
    the theorem-bearing bridge into the older atomic certified path, plus the
    routing/instantiation/coverage/authority/non-bypass/issuance package.
20. [CertifiedAtc/ScopedSafety.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSafety.lean)
    Optional. This is the reachable-state safety layer above the scoped
    issuing boundary: state-preservation for nominal/runway/surface/air/interface
    invariants plus issued-step separation soundness.
21. [CertifiedAtc/JointActs.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/JointActs.lean)
    Optional. This is a narrow orchestration milestone module.

## What Each Module Owns

- `Core`
  Shared vocabulary, base identifiers, atomic command language, and common structures.
- `CommandCatalog`
  Static command classes and plan-template routing contract.
- `RunwayKernel`
  Local runway commitment checker and local theorem.
- `SurfaceKernel`
  Local surface graph checker and local theorem.
- `AirKernel`
  Concrete local air-path checker over guarded transitions and altitude bands.
- `SeparationChecker`
  Concrete local pairwise separation checker with a local soundness theorem.
- `ScopedSeparation`
  Scoped separation theorem package for `Safety-complete (N₀)`: exact command
  classification, neutrality, peer coverage,
  `ScopedSeparationBoundarySufficiencyTheorem`, and
  `ScopedViableSepTheorem` for the current shortest-path surface.
- `SeparationChecker`
  Also now includes `viable_sep_of_capableApproval_equivIssuedScenario`, the
  local continue-capable successor theorem consumed by
  `ScopedIssuedScenarioViableSepTheorem`.
- `ScopedExtraction`
  Scoped extraction boundary for `Safety-complete (N₀)`: proof-side source
  world, deterministic extraction into `ClearanceCompileView` and certifier
  views, no-invented-id theorems, scoped reference preservation, operational
  bridge facts, and authority/lifecycle preservation.
- `Interfaces`
  Optional orchestration/composition layer over the existing atomic command path.
- `ClearanceEnvelope`
  Legacy greenfield staging layer: its own instruction surface, explicit frontier shape, and partial compiler back into the atomic certified path.
- `GreenfieldModel`
  Current-shape greenfield boundary aligned to the Kotlin model:
  `ClearanceContent.Single | Compound(steps, completedSteps)`,
  envelope-level conditions, lifecycle timing/categories, explicit `UniqueSet`
  state for set-like lifecycle fields, and frontier selection over the mixed
  step list.
- `GreenfieldLifecycle`
  Abstract lifecycle layer over `GreenfieldModel`: managed clearances, suppressed domains, admission, supersession, conditional activation, and completion advancement over abstract satisfied-step indices.
- `GreenfieldResolved`
  Current-shape resolved execution boundary aligned to Kotlin `ResolvedStep` / `ResolvedClearance`, with resolved points, runway facts, radio roles, and circuit joins.
- `GreenfieldResolution`
  Proof-side world-to-resolved relation that justifies resolved payloads from world/state facts instead of treating them as hand-assembled data.
  It now also includes a finite `ConcreteResolutionWorld` bridge for list-backed
  proof data, so the layer can be exercised concretely without inventing a full
  extracted runtime.
- `GreenfieldCompletion`
  Structured completion-observation layer over `GreenfieldResolved`: evaluates proof-side observations against resolved step payloads while keeping unsupported families explicit.
- `GreenfieldExecution`
  Resolved active-clearance layer over `GreenfieldResolved` and `GreenfieldCompletion`: managed resolved clearances, resolved completion, supersession bridging, and active-set reconciliation.
  The current theorem surface also makes the unique-clearance-id assumption
  explicit where exact resolved-set invariants depend on id-based reattachment.
  It now also includes first admission/reconciliation invariants above that
  boundary, not just helper lemmas about individual transitions.
  Activation is also status-gated here now, matching Kotlin: superseded
  conditionals cannot be reactivated later in the same pass.
  Reconciliation now also matches Kotlin more closely at the terminal-clearance
  boundary: terminal results are taken directly from the final working set
  rather than from a separate terminal accumulator.
- `GreenfieldReachability`
  Reachable active-set layer above `GreenfieldExecution`: packages fresh
  admission and reconciliation preservation into a reusable `ReachableResolvedSet`
  boundary and exposes well-formedness as a derived invariant.
- `ScopedGreenfield`
  Scoped greenfield theorem package for `Safety-complete (N₀)`: exact scoped
  instruction surface, scoped authority mapping, movement-envelope frontier
  shape, conditional surface-compound normalization, and the resolved
  reachability wrapper used by the final issuing-layer theorem.
- `ScopedIssuance`
  Final scoped issuing layer for `Safety-complete (N₀)`: honest greenfield to
  atomic bridging for the current scoped surface, plus routing completeness,
  plan-instantiation correctness, peer coverage, compatibility narrowness,
  authority gating, non-bypass, and issuance soundness.
- `ScopedSafety`
  Reachable-state safety layer above `ScopedIssuance`: scoped world/state
  well-formedness, component-preserving approval collection, issuance
  preservation of nominal/runway/surface/air/interface invariants, reachable
  issued-state semantics, and issued-step separation soundness.
- `JointActs`
  Narrow orchestration milestone theorem for the first joint-act slice.

## Current Lean Split

There are now twelve distinct Lean layers above the local certifiers:

1. `ClearanceEnvelope.lean`
   The older proof/compiler surface that still bridges into the atomic command path.
2. `ScopedExtraction.lean`
   The scoped extraction boundary from proof-side world facts into
   `ClearanceCompileView`, certifier-local views, and issuer-authority facts.
3. `GreenfieldModel.lean`
   The new Kotlin-aligned model surface that mirrors the runtime clearance shape directly.
4. `GreenfieldLifecycle.lean`
   The new abstract active-clearance engine over that model surface.
5. `GreenfieldResolved.lean`
   The new resolved-step execution boundary aligned to Kotlin compiled clearances.
6. `GreenfieldResolution.lean`
   The proof-side world/state relation that derives valid resolved clearances.
7. `GreenfieldCompletion.lean`
   The structured observation contract that evaluates proof-side facts against resolved steps.
8. `GreenfieldExecution.lean`
   The resolved active-clearance layer that closes the loop from admitted clearances to completion and reconciliation.
9. `GreenfieldReachability.lean`
   The reachable active-set layer that packages execution preservation into a reusable invariant boundary.
10. `ScopedGreenfield.lean`
   The scoped `Safety-complete (N₀)` theorem package above the greenfield model
   and execution layers.
11. `ScopedIssuance.lean`
   The final scoped issuing layer above extraction and greenfield execution:
   bridge into the old certified path plus the Milestone 5 theorem package.
12. `ScopedSafety.lean`
   The reachable-state safety layer above the scoped issuing boundary:
   preserved nominal/kernel/interface invariants, reachable issued-state
   semantics, and issued-step separation soundness.

That split is intentional. The Kotlin/runtime model has moved to:

- typed entity/procedure references
- `steps + completedSteps`
- envelope-level conditional state
- lifecycle timing and completion categories

The old envelope module is still useful for the existing theorem work, but it is no longer the authoritative model shape.

## Immediate Use

Use `GreenfieldModel.lean` when you want to:

- reason about the current Kotlin clearance shape directly
- normalize conditional clearances at the envelope level
- talk about compound frontier selection over `completedSteps`
- prove lifecycle/helper lemmas without first translating into the older staging compiler surface

Use `GreenfieldLifecycle.lean` when you want to:

- reason about active-clearance set evolution without importing geometry
- study supersession and suppressed-domain semantics directly
- model condition-pending to active activation order
- talk about completion as abstract satisfied step indices before proving world-backed completion facts

Use `ScopedExtraction.lean` when you want to:

- reason about the theorem-bearing extraction boundary for the scoped
  `Safety-complete (N₀)` surface
- connect proof-side runway, taxiway, role/frequency, and authority facts to
  `ClearanceCompileView`
- recover the certifier-local runway / surface / air views without appealing to
  prose-only extraction assumptions
- prove that in-scope extracted references remain available through the scoped
  clearance lifecycle

Use `GreenfieldResolved.lean` when you want to:

- reason about completion against resolved points/runways/roles instead of raw instructions
- mirror Kotlin `ResolvedStep` / `ResolvedClearance` shapes on the proof side
- talk about backtrack far-end points, resolved route limits, and resolved circuit joins explicitly

Use `GreenfieldResolution.lean` when you want to:

- justify resolved steps from proof-side world facts and current state
- connect world assumptions to `ResolvedClearance` without inventing a full extracted runtime
- prove compatibility and step-count facts about resolved clearances
- exercise that relation over a finite list-backed world via
  `ConcreteResolutionWorld.toResolutionWorld`

Use `GreenfieldCompletion.lean` when you want to:

- connect proof-side observations to resolved step completion
- model point arrival, runway transitions, circuit membership, radio handoff, altitude/speed, and transponder completion
- keep unsupported families explicit rather than silently approximating them

Use `GreenfieldExecution.lean` when you want to:

- reason about managed resolved clearances directly
- combine resolved completion with supersession and conditional activation
- stay aligned to the Kotlin active-clearance engine without dropping back to raw instruction semantics
- make explicit which execution properties only need lifecycle-view equality
  and which stronger ones additionally require unique clearance ids
- rely on the now-proved unique-id preservation path through admission,
  conditional activation, and full reconciliation

Use `GreenfieldReachability.lean` when you want to:

- reason about reachable active sets rather than restating fresh-id and compatibility side conditions
- recover `WellFormedResolvedSet` from reachability automatically
- bridge world-backed resolution facts into execution reachability via `ReachableResolvedSet.admit_of_resolved`

Use `ScopedIssuance.lean` when you want to:

- work at the final scoped issuing boundary for `Safety-complete (N₀)`
- bridge scoped greenfield instructions into the older atomic certified path
  without silently widening the claim
- use the top-layer theorem package for routing, plan instantiation, peer
  coverage, compatibility narrowness, authority gating, non-bypass, and
  issuance soundness

Use `ScopedSafety.lean` when you want to:

- work above the final scoped issuing boundary instead of restating local
  preservation lemmas by hand
- recover the scoped nominal/runway/surface/air/interface invariant package on
  reachable issued states
- connect issued scoped separation-certified acts to witness-backed peer
  coverage and separation soundness

Use `ScopedGreenfield.lean` when you want to:

- stay strictly inside the scoped `Safety-complete (N₀)` greenfield surface
- use the scoped greenfield authority mapping instead of re-deriving grants ad hoc
- rely on the no-partial-issuance theorem for compound surface envelopes
- connect scoped surface compounds to checked staging and resolved reachability
- avoid reopening Bucket C route-bearing semantics while building the final top layer

Use `ClearanceEnvelope.lean` when you need:

- the existing compiler path into the atomic certified stack
- the older frontier/sequencing theorem surface that already sits above that compiler
