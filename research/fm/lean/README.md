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
8. [CertifiedAtc/ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
   Optional. This is the older greenfield-to-atomic staging compiler and theorem surface.
9. [CertifiedAtc/GreenfieldModel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldModel.lean)
   Optional. This is the current Kotlin-aligned greenfield boundary for protocol, compound clearances, conditional normalization, and lifecycle/frontier reasoning.
10. [CertifiedAtc/GreenfieldLifecycle.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldLifecycle.lean)
    Optional. This is the abstract active-clearance state machine over the Kotlin-aligned model: staging, supersession, completion advancement, and conditional activation.
11. [CertifiedAtc/GreenfieldResolved.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldResolved.lean)
    Optional. This is the proof-side resolved execution boundary aligned to Kotlin `ResolvedStep` / `ResolvedClearance`.
12. [CertifiedAtc/GreenfieldResolution.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldResolution.lean)
    Optional. This is the proof-side world-to-resolved relation: it states what world facts justify a resolved step/clearance.
13. [CertifiedAtc/GreenfieldCompletion.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldCompletion.lean)
    Optional. This evaluates structured observations against resolved steps: reached point, runway transition, circuit membership, altitude/speed, radio role/frequency, and transponder state.
14. [CertifiedAtc/GreenfieldExecution.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExecution.lean)
    Optional. This is the resolved active-clearance layer: managed resolved clearances, resolved completion, and active-set reconciliation.
15. [CertifiedAtc/JointActs.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/JointActs.lean)
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
- `GreenfieldCompletion`
  Structured completion-observation layer over `GreenfieldResolved`: evaluates proof-side observations against resolved step payloads while keeping unsupported families explicit.
- `GreenfieldExecution`
  Resolved active-clearance layer over `GreenfieldResolved` and `GreenfieldCompletion`: managed resolved clearances, resolved completion, supersession bridging, and active-set reconciliation.
  The current theorem surface also makes the unique-clearance-id assumption
  explicit where exact resolved-set invariants depend on id-based reattachment.
- `JointActs`
  Narrow orchestration milestone theorem for the first joint-act slice.

## Current Lean Split

There are now seven distinct Lean layers above the local certifiers:

1. `ClearanceEnvelope.lean`
   The older proof/compiler surface that still bridges into the atomic command path.
2. `GreenfieldModel.lean`
   The new Kotlin-aligned model surface that mirrors the runtime clearance shape directly.
3. `GreenfieldLifecycle.lean`
   The new abstract active-clearance engine over that model surface.
4. `GreenfieldResolved.lean`
   The new resolved-step execution boundary aligned to Kotlin compiled clearances.
5. `GreenfieldResolution.lean`
   The proof-side world/state relation that derives valid resolved clearances.
6. `GreenfieldCompletion.lean`
   The structured observation contract that evaluates proof-side facts against resolved steps.
7. `GreenfieldExecution.lean`
   The resolved active-clearance layer that closes the loop from admitted clearances to completion and reconciliation.

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

Use `GreenfieldResolved.lean` when you want to:

- reason about completion against resolved points/runways/roles instead of raw instructions
- mirror Kotlin `ResolvedStep` / `ResolvedClearance` shapes on the proof side
- talk about backtrack far-end points, resolved route limits, and resolved circuit joins explicitly

Use `GreenfieldResolution.lean` when you want to:

- justify resolved steps from proof-side world facts and current state
- connect world assumptions to `ResolvedClearance` without inventing a full extracted runtime
- prove compatibility and step-count facts about resolved clearances

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

Use `ClearanceEnvelope.lean` when you need:

- the existing compiler path into the atomic certified stack
- the older frontier/sequencing theorem surface that already sits above that compiler
