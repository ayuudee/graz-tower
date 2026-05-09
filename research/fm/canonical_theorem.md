# Canonical Top-Level Theorem

This file describes the optional composition theorem for the single-issuer
variant of the split-kernel architecture.

It is not the minimum success criterion for `research/fm`.

The primary deliverables are:

- isolated local kernel contracts
- local kernel soundness theorems
- explicit ownership boundaries between those kernels

The canonical theorem matters only if the product architecture really wants one
central layer that is solely responsible for issuance.

For that optional architecture, the theorem is:

```text
If orchestration issues a command, then:

1. the command class compiled to its required static certification plan shape,
2. that plan instantiated successfully against the current world,
3. every required local approval in that plan succeeded,
4. the narrow compatibility check accepted the approval bundle,
5. and the resulting orchestration state still satisfies the interface
   invariants.
```

In Lean this statement currently lives as:

- `CanonicalTopLevelTheorem` in
  [Interfaces.lean](research/fm/lean/CertifiedAtc/Interfaces.lean)

## What Is Already Proved

The theorem is not yet proved in full, but important pieces under it now are.

Local kernel progress:

- runway kernel soundness is proved
- surface kernel soundness is proved
- air kernel soundness is proved
- separation checker soundness is proved

Orchestration progress:

- static routing exists
- plan instantiation is now concrete across a wider runway/air separation slice
- conservative peer coverage is proved for the current instantiated slice
- narrow compatibility is explicit and proved for the current compatibility
  function
- non-bypass is proved for the issuance path that currently exists
- milestone 2 proves the first joint-act issuance slice for takeoff, landing,
  and go-around

## When This Theorem Matters

It matters when:

- one component is meant to be the exclusive issuer of commands
- the product needs a single proof story for routing, bundling, compatibility,
  and issuance
- non-bypass at the issuing layer is part of the intended certification claim

It does not matter when:

- higher-level systems can consume kernel-local guarantees directly
- the main value is the isolated certifiers themselves
- a whole-system issuance theorem would cost more than it buys

## What Is Already True Without It

Even without the canonical theorem:

- runway, surface, air, and separation can each have their own concrete local
  proof story
- those local guarantees can be consumed by higher-level systems
- the split architecture remains meaningful because the kernels do not collapse
  into one global checker

## What Is Still Missing If You Do Want It

The full theorem remains open because two major architectural obligations are
still not discharged:

- orchestration is still only widened through a partial
  runway/surface/air slice
- the full top-level issuance theorem is still not proved

There are also orchestration-level statements that are still only declared:

- `CanonicalTopLevelTheorem`

## Interpretation

`research/fm` should now be read as:

- the split architecture is real
- the local certifiers are the main proof deliverable
- the canonical theorem is optional composition work on top of those local
  certifiers, not the baseline definition of success
