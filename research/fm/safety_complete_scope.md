# Safety-Complete Scope

Last updated: April 13, 2026

This document is the Milestone 1 deliverable from
[completion_milestones.md](/home/andrew/dev/projects/twr2/research/fm/completion_milestones.md).

Its job is to freeze the exact theorem target for
`Safety-complete (N₀)`.

This is the shortest-path scope, not the largest interesting scope.

The next widening target above this closed scoped claim is defined separately
in
[route_bearing_scope.md](/home/andrew/dev/projects/twr2/research/fm/route_bearing_scope.md).

## Purpose

The project already has more modeled command families than it can currently
close honestly under one top-level proof story.

So this document answers, explicitly:

- which command families count toward `Safety-complete (N₀)`
- which of those must be locally certified and globally composed
- which are instead justified by neutrality
- which are explicitly out of scope and therefore must not be counted in the
  safety-complete claim
- which theorem families must exist before we can say "complete"

## Adversarial Scope Rules

These rules are here to prevent fake closure.

### 1. Do not include a family just because the model can express it

A command family is in scope for `Safety-complete (N₀)` only if:

- its certifier ownership is stable
- its extraction boundary needs are understood
- its lifecycle and completion semantics are stable enough to support final
  proofs
- the top-layer theorem obligation it creates is clear

If any of those fail, the family is not yet in scope.

### 2. Do not exclude a family without paying the product cost

If a family is out of scope for `Safety-complete (N₀)`, then the resulting
claim does not cover issuing that family.

That is not a documentation footnote. It is a real limit on what the completed
proof claim means.

### 3. Neutrality is not a free pass

A command family may be treated as non-certified only if there is a real
neutrality story for the relevant invariant family.

For airborne commands, this means explicit separation-neutrality theorems over
`H_sep`, not just "we do not think this changes anything."

### 4. The old atomic path does not define the final scope

The older atomic orchestration slice remains useful evidence, but it does not
get to decide the final `Safety-complete (N₀)` claim.

The final scope is defined by what can be proved honestly above the greenfield
boundary.

## Scoped Command Surface

The scoped surface is split into three buckets.

### Bucket A: Must Be Certified And Composed

These families are in scope and must pass through the final issuing-layer proof
path.

#### Surface-only or runway/surface joint

- `HoldShortOf`
  Safety reason: runway-entry fence and protected-segment discipline.
  Required path: `surface + compatibility`.

- `TaxiTo`
  Safety reason: graph-consistent surface movement and segment authority.
  Required path: `surface + compatibility`.

- `CrossRunway`
  Safety reason: runway occupancy plus protected surface entry.
  Required path: `runway + surface + compatibility`.

- `LineUpAndWait`
  Safety reason: protected runway entry and runway commitment coherence.
  Required path: `runway + surface + compatibility`.

#### Runway + air + separation joint acts

- `ClearedForTakeoff`
  Required path: `runway + air + separation + compatibility`.

- `ClearedToLand`
  Required path: `runway + air + separation + compatibility`.

- `ClearedTouchAndGo`
  Required path: `runway + air + separation + compatibility`.

- `GoAround`
  Required path: `runway + air + separation + compatibility`.

#### Air + separation modifiers

- `ReduceSpeedTo`
  Scope note: knot targets only in the current scoped claim.
  Required path: `air + separation + compatibility`.

### Bucket B: Must Be Justified By Neutrality

These families are in scope, but the shortest honest path is not to force them
through local certification. It is to prove that, for the scoped safety claim,
they are neutral with respect to the relevant airborne separation story.

- `ReportDownwind`
- `ReportFinal`
- `Proceed`
- `ContactFrequency`
- `MonitorFrequency`
- `SquawkCode`

For `Safety-complete (N₀)`, these require:

- explicit classification as non-separation-relevant in the scoped model
- explicit neutrality over `H_sep`
- no hidden dependency on top-layer compatibility or local kernel approval

### Bucket C: Explicitly Out Of Scope

These families are not part of the `Safety-complete (N₀)` claim.

- `StartupApproved`
- `HoldPosition`
- `BacktrackRunway`
- `JoinCircuit`
- `Orbit`
- `ExtendDownwind`
- `ContinueApproach`
- `ClearedTo`
- `ClearedApproach`
- `CrossControlledAirspace`
- `HoldAt`
- `ClimbTo`
- `DescendTo`
- non-bridgeable air-modifier variants:
  `ReduceSpeedTo` in Mach

## Why Bucket C Is Out

This is the shortest-path justification for each exclusion class.

### Surface or runway-adjacent families not yet worth widening

- `StartupApproved`
- `HoldPosition`
- `BacktrackRunway`

These are not excluded because they are unimportant.
They are excluded because they do not need to be on the shortest path to close
the runway/surface/separation safety package, and in the case of
`BacktrackRunway` they sit outside the shortest-path scoped claim even though
the current-shape greenfield/runtime slice is now closed separately.

### Route-bearing, circuit, or open-ended airborne families

- `JoinCircuit`
- `Orbit`
- `ExtendDownwind`
- `ContinueApproach`
- `ClearedTo`
- `ClearedApproach`
- `HoldAt`

These are the main theorem-shape expanders.
They pull in richer route intent, open-ended completion, procedure resolution,
clearance-limit behavior, and compound semantics that are not needed for the
shortest honest closure of the safety package above.

### Broader coordination / airspace semantics

- `CrossControlledAirspace`

This family still sits in a broader coordination and airspace-authority space
than the current shortest-path theorem package needs.

### Altitude-only air modifiers

- `ClimbTo`
- `DescendTo`

These remain modeled and bridgeable in parts of the current stack, but they
are no longer part of the shortest-path `Safety-complete (N₀)` claim.

The blocker is the local separation viability story: the current proof package
does not yet justify the required continuation-existence result uniformly for
the altitude-only modifier slice, so keeping them in scope would overclaim.

## Greenfield Clearance Reading Of The Scope

At the greenfield boundary, the scoped `Safety-complete (N₀)` families should
be read as:

- surface movement envelope subset:
  `TaxiTo`, `CrossRunway`, `HoldShortOf`, `LineUpAndWait`
- runway/air joint acts:
  `ClearedForTakeoff`, `ClearedToLand`, `ClearedTouchAndGo`, `GoAround`
- airborne scalar modifiers:
  `ReduceSpeedTo` in knots
- neutral airborne coordination/reporting subset:
  `ReportDownwind`, `ReportFinal`, `Proceed`, `ContactFrequency`,
  `MonitorFrequency`, `SetSquawk`

Important:

- `BacktrackRunway` remains modeled and useful, but it is not part of the
  shortest-path completion claim; it now has a separate small current-shape
  greenfield closure outside the scoped nominal bar
- route-bearing and open-ended clearances remain outside the scoped claim until
  their semantics are proved strongly enough to support top-level safety
  theorems
- Mach targets and all `ClimbTo` / `DescendTo` variants remain outside the
  scoped claim until the separation-layer continuation story is widened or the
  local air/separation boundary is strengthened

## Theorem Target Inventory

`Safety-complete (N₀)` requires the following theorem families.

### A. Local certifier closure

Already present and still required:

- `RunwayKernelMilestone1Theorem`
- `SurfaceKernelSoundnessTheorem`
- `AirKernelSoundnessTheorem`
- `SeparationCheckerSoundnessTheorem`

Must be completed or widened for the scoped surface:

- a scoped separation-neutrality package for Bucket B
- a scoped boundary-sufficiency package over `H_sep`
- a scoped `Viable_sep` package over the concrete continuation set
- scoped separation coverage for every Bucket A family with
  `separation = true`

### B. Extraction-boundary closure

Must exist above the greenfield world boundary:

- reference preservation for every in-scope entity and procedure reference
- operational-structure preservation for runway, surface, air, and separation
  proof inputs
- authority-data preservation
- lifecycle stability for referenced entities
- clearance-limit/holding-pattern preservation where applicable

### C. Greenfield semantic closure

Must exist for the scoped families:

- issuer-authority theorems for every in-scope family that is authority-gated
- stable completion and supersession semantics for every Bucket A family
- stable compound and frontier theorems for the scoped surface envelope subset
- no-partial-issuance packaging for the scoped compound surface
- resolved execution compatibility and reachability for the scoped surface

### D. Final uber-layer theorem package

Must exist above the greenfield boundary:

- routing completeness for Bucket A and Bucket B
- plan-instantiation correctness for Bucket A
- peer-coverage soundness for the Bucket A separation slice
- compatibility narrowness for Bucket A
- authority-gated issuance for Bucket A
- non-bypass for Bucket A
- issuance soundness for Bucket A

### E. Reachable-state safety

Must exist for the final issuing layer:

- issuance preserves interface invariants
- reachable issued states preserve runway invariants
- reachable issued states preserve taxiway invariants
- reachable issued states preserve air/separation invariants
- reachable issued states preserve interface invariants

## Suggested Final Theorem Names

These names are not frozen, but the theorem roles are.

- `ScopedSeparationNeutralityTheorem`
- `ScopedSeparationBoundarySufficiencyTheorem`
- `ScopedViableSepTheorem`
- `ScopedExtractionSoundnessTheorem`
- `ScopedAuthorityGateTheorem`
- `ScopedRoutingCompletenessTheorem`
- `ScopedPlanInstantiationCorrectnessTheorem`
- `ScopedPeerCoverageTheorem`
- `ScopedCompatibilityNarrownessTheorem`
- `ScopedNonBypassTheorem`
- `ScopedIssuanceSoundnessTheorem`
- `ScopedReachableSafetyTheorem`

## What Will Not Count As Completion

The following do not justify the phrase `Safety-complete (N₀)` by themselves:

- local kernel soundness without extraction-boundary theorems
- greenfield lifecycle and execution lemmas without a final issuing-layer proof
- a wider static command catalog than the proved scoped surface
- partial support for route-bearing/open-ended families
- the older atomic non-bypass theorem alone
- doc-only claims about neutrality or authority

## Immediate Consequence For The Next Stage

Milestone 2 should now optimize for this exact scope.

That means:

- finish separation for Bucket A and Bucket B only
- do not widen route-bearing or open-ended families during local-separation
  closure
- use this scoped table as the guardrail for every later theorem and proof
  refactor
