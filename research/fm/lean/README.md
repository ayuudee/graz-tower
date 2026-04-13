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
11. [CertifiedAtc/JointActs.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/JointActs.lean)
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
  envelope-level conditions, lifecycle timing/categories, and frontier selection over the mixed step list.
- `GreenfieldLifecycle`
  Abstract lifecycle layer over `GreenfieldModel`: managed clearances, suppressed domains, admission, supersession, conditional activation, and completion advancement over abstract satisfied-step indices.
- `JointActs`
  Narrow orchestration milestone theorem for the first joint-act slice.

## Current Lean Split

There are now three distinct Lean layers above the local certifiers:

1. `ClearanceEnvelope.lean`
   The older proof/compiler surface that still bridges into the atomic command path.
2. `GreenfieldModel.lean`
   The new Kotlin-aligned model surface that mirrors the runtime clearance shape directly.
3. `GreenfieldLifecycle.lean`
   The new abstract active-clearance engine over that model surface.

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

Use `ClearanceEnvelope.lean` when you need:

- the existing compiler path into the atomic certified stack
- the older frontier/sequencing theorem surface that already sits above that compiler
