# Lean Project Guide

This is the standalone Lean 4 project for `research2`.

## Build

From the repository root:

```bash
nix-shell -p lean4 --run 'cd research2/lean && lake build'
```

## Module Order

Read modules in roughly this order:

1. [CertifiedAtc/Core.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/Core.lean)
2. [CertifiedAtc/CommandCatalog.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/CommandCatalog.lean)
3. [CertifiedAtc/RunwayKernel.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/RunwayKernel.lean)
4. [CertifiedAtc/SurfaceKernel.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/SurfaceKernel.lean)
5. [CertifiedAtc/AirKernel.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/AirKernel.lean)
6. [CertifiedAtc/SeparationChecker.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/SeparationChecker.lean)
7. [CertifiedAtc/Interfaces.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/Interfaces.lean)
   Optional. Read this when you are working on the composition layer.
8. [CertifiedAtc/ClearanceEnvelope.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/ClearanceEnvelope.lean)
   Optional. Read this when you are working on the greenfield clearance and
   sequencing boundary.
9. [CertifiedAtc/JointActs.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/JointActs.lean)
   Optional. This is a narrow orchestration milestone module.

## What Each Module Owns

- `Core`
  Shared vocabulary, base identifiers, common structures.
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
  Optional orchestration / composition layer: plan instantiation, approval
  collection, compatibility, peer coverage, and issuance path.
- `ClearanceEnvelope`
  Greenfield clearance vocabulary, explicit
  `sequential | immediate | standalone` timing, compound frontier selection,
  and partial compiler into the current atomic certified path.
- `JointActs`
  Narrow orchestration milestone theorem for the first joint-act slice.

## Current Proof Shape

The primary stack is:

1. prove local kernels independently
2. expose explicit local guarantees and ownership boundaries
3. let higher-level systems consume those guarantees directly if they want to

The optional composition stack is:

1. instantiate orchestration plans against current state
2. bundle required local approvals
3. run narrow compatibility
4. issue only through orchestration, if a single issuing layer is actually part
   of the desired architecture

The new greenfield clearance stack above that is:

1. compile entity-referenced instructions and compound frontiers against
   `ClearanceCompileView`
2. map them back into the current atomic certified path
3. eventually replace that bridge with the greenfield-derived extraction
   boundary that the next project will implement, together with a full
   sequencing theorem

The primary stack is already real for runway, surface, air, and the current
pairwise separation checker. The optional composition stack is partially real
for a limited runway/air slice.

## Known Open Areas

- the brief-level local separation obligations around neutrality, boundary
  sufficiency, and horizon viability are now represented as Lean targets, but
  only partially connected to concrete command families and continuation-set
  generation; the remaining gap is mostly typed-command-surface coverage rather
  than the continuation set itself
- `ClearanceEnvelope.lean` now has a partial compiler back into the atomic
  certification path, and the structural extraction contract from the
  greenfield `AviationWorld` is now recorded in
  [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr/research2/aviation_world_extraction_contract.md);
  the remaining gap is the semantics above that contract, not repo-local
  translators
- the narrowed instruction-level authority mapping is now recorded in
  [instruction_authority_contract.md](/home/andrew/dev/projects/twr/research2/instruction_authority_contract.md),
  and the Lean envelope layer now has a conservative issuer-authorization
  checker for the resolved subset, including frontier-level authorization above
  the current envelope compiler surface and authorization-aware compile seams
  for frontier, content, and structured-clearance compilation that reduce to
  the existing compiler when authorization succeeds and prove frontier-level
  authorization when checked compilation succeeds
- the narrowed envelope theorem surface is now recorded in
  [clearance_envelope_contract.md](/home/andrew/dev/projects/twr/research2/clearance_envelope_contract.md),
  including the current explicit `standalone` exclusion and the now-packaged
  movement-envelope theorem surface for the admitted sequential subset
- the compound-clearance sequencing theorem is still open above the current
  frontier compiler, along with several greenfield clearance semantics that
  should be settled before the theorem is widened much further: compound
  admission and timing, completion categories, step-transition effects,
  supersession granularity, the remaining instruction-level authority mapping,
  and
  clearance-limit/holding-pattern invariants
- `Interfaces.lean` still contains the stated-but-unproved
  `CanonicalTopLevelTheorem`
- plan instantiation is still only widened through a partial
  runway/surface/air slice
  for the optional composition layer
