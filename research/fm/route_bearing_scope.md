# Route-Bearing Widening Scope

Last updated: April 14, 2026

This document defines the next widening track after the scoped
`Safety-complete (N₀)` and `Full-brief complete` closures.

It does not replace
[safety_complete_scope.md](/home/andrew/dev/projects/twr2/research/fm/safety_complete_scope.md).
That document remains the authoritative statement of the already-closed scoped
claim. This note defines the next command families we intend to pull into the
theorem-bearing surface, and the order in which to do it.

## Purpose

The Kotlin model now supports a broader route-bearing and procedure-aware
surface than the currently proved FM claim.

The next widening track should therefore optimize for:

- product value
- minimal theorem-shape expansion
- reuse of the existing greenfield/runtime boundary
- no regression in the already-closed scoped claim

This note is the guardrail for that widening work.

## Current Delivered Increment

As of April 14, 2026, the first honest widening increment is now in place.

- [GreenfieldRouteBearing.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearing.lean)
  gives truthful resolved-side semantics for all four Phase A families:
  `ClearedTo`, `HoldAt`, `ClearedApproach`, and `JoinCircuit`
- [BridgeableRouteBearingIssuance.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/BridgeableRouteBearingIssuance.lean)
  adds theorem-bearing legacy-bridge issuance only for the route-bearing pair
  that the older atomic path can already carry honestly:
  `ClearedApproach`, and `JoinCircuit` only when the join type maps back into
  the legacy subset (`downwind`, `base`, `straightIn`)
- [RouteBearingExtraction.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean)
  adds the first theorem-bearing procedure-bearing extraction increment:
  widened source-world extraction for fixes, holds, approaches, SIDs, airways,
  STARs, VFR routes, and circuit procedures; route-bearing reference
  preservation into `ClearanceCompileView`; and compile-success theorems for
  compile-ready widened instructions, including supported-limit `ClearedTo`;
  `ClearedApproach` source kinds stay aligned to the closed greenfield
  `ApproachType` model and are bridged to legacy strings only at compile-view
  emission
- `ClearedTo` and `HoldAt` therefore now have theorem-bearing resolved and
  extraction surfaces today, but still not theorem-bearing issuance
- [RouteBearingResolutionBridge.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean)
  now closes the missing extraction-to-resolution seam for the current
  bridgeable subset:
  `ClearedTo`, published `HoldAt`, and non-circling `ClearedApproach`
- [GreenfieldRouteBearingAdmission.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingAdmission.lean)
  now packages the first honest greenfield admission layer for that same
  bridged subset:
  authority-gated admission over the widened compile view, resolved-clearance
  existence from extracted world data, and admission soundness into
  `ReachableResolvedSet`
- `ClearedTo` and published `HoldAt` therefore now have theorem-bearing
  resolved, extraction, bridge, and greenfield-admission surfaces today, but
  still not theorem-bearing legacy atomic issuance
- `ClearedApproach` is route-bearing-resolved and issuance-bridgeable, but it
  still has no modeled completion in the current Kotlin/Lean execution layer
- the next explicit structural gap is now `JoinCircuit`: extracted circuit data
  still lacks the join-entry support facts needed to bridge it into the
  resolved execution world

## Widening Principles

### 1. Widen one semantic cluster at a time

Do not widen route-bearing clearances, open-ended sequencing behavior, and
cross-unit coordination all at once.

The next cluster is:

- `ClearedTo`
- `ClearedApproach`
- `HoldAt`
- `JoinCircuit`

These are the smallest meaningful set that makes the route-bearing model
useful without forcing every remaining airborne family in at the same time.

That remains the full target set, but the currently delivered widening increment is
only the first honest part of it.

### 2. Preserve the current scoped claim

Nothing in this document changes the truth of the already-closed scoped
programme.

Until this widening track closes, the current proven claim remains exactly the
one in
[safety_complete_scope.md](/home/andrew/dev/projects/twr2/research/fm/safety_complete_scope.md).

### 3. Make extraction and resolution lead the widening

The route-bearing gap is not primarily about local runway/surface certifiers.
It is about:

- procedure-preserving extraction
- world-backed resolution
- route-bearing completion semantics
- route-bearing authority mapping
- top-layer issuance packaging

If any widening step tries to bypass those boundaries, it is the wrong step.

### 4. Keep open-ended sequencing out unless forced

The following remain outside the first widening slice unless a theorem forces
them in:

- `Orbit`
- `ExtendDownwind`
- `ContinueApproach`
- `CrossControlledAirspace`

These create larger open-ended or coordination-heavy theorem obligations than
the first route-bearing step needs.

## Target Command Families

### Phase A: Route-Bearing Core

These are the first widening targets.

- `ClearedTo`
  Reason: route intent, clearance limit semantics, and route-bearing top-layer
  packaging.

- `ClearedApproach`
  Reason: published approach resolution, approach-specific completion
  semantics, and runway/procedure interface.

- `HoldAt`
  Reason: holding-pattern resolution and clearance-limit / continuation
  semantics.

- `JoinCircuit`
  Reason: circuit entry semantics and resolved completion against circuit
  membership.

### Phase B: Deferred Route-Adjacent Families

These are explicitly not in the first widening slice.

- `Orbit`
- `ExtendDownwind`
- `ContinueApproach`

They remain deferred because they are more open-ended than the route-bearing
core and would dilute the shortest honest widening path.

### Phase C: Deferred Coordination/Airspace Families

- `CrossControlledAirspace`

This remains separate because it widens coordination and authority semantics in
a different direction from procedure-bearing route semantics.

## Required Proof Expansions

The first widening slice should add the following theorem-bearing boundaries.

### 1. Extraction

The extraction boundary must widen from the scoped runway/taxiway/role subset
to procedure-bearing data:

- fixes
- holding patterns
- SIDs / STARs where referenced by `ClearedTo`
- instrument approaches
- circuit procedures

Required outcomes:

- no invented procedure ids
- reference preservation for route-bearing instructions
- operational preservation for procedure and limit references

### 2. Resolution

The proof stack needs a stronger route-bearing resolved boundary, aligned to
the Kotlin runtime:

- resolved route limit / route path for `ClearedTo`
- resolved approach for `ClearedApproach`
- resolved holding pattern for `HoldAt`
- resolved circuit membership target for `JoinCircuit`

### 3. Extraction-to-Resolution Bridge

The widening also needs an explicit theorem-bearing bridge from extracted
procedure-bearing world data into the resolved execution world.

That bridge should prove, at minimum:

- extracted fix data justifies the resolved clearance-limit fact for
  `ClearedTo`
- extracted holding-pattern data justifies the resolved holding fact for
  `HoldAt`
- extracted approach data justifies the resolved approach fact for
  `ClearedApproach`
- extracted circuit data eventually justifies the resolved circuit-join fact
  for `JoinCircuit`

Do not treat extraction theorems and resolved semantics as an end-to-end
closure until this bridge exists.

### 4. Completion

The widening needs honest completion observations for:

- reaching a resolved clearance limit
- entering/being established on a resolved approach
- entering and remaining in a resolved holding pattern
- joining the resolved circuit at the required membership/altitude state

If any of those need a stronger observation contract than the current
completion layer provides, that contract should be widened explicitly rather
than approximated.

### 5. Authority and Issuance

The widening must add:

- authority mapping for the route-bearing families
- compatibility and no-partial-issuance packaging where compound envelopes mix
  route-bearing and existing scoped steps
- top-layer issuance soundness for the widened surface

## Shortest Honest Order

Do the widening in this order:

1. widen extraction for route-bearing references
2. widen resolved-step semantics for the Phase A families
3. prove the extraction-to-resolution bridge
4. widen completion observations only as much as those resolved steps need
5. widen greenfield/execution packaging
6. widen the issuing layer and reachable-state safety package

Do not start with the top theorem. Start with the route-bearing data path.

Current status against that order:

- step 1 now has a first theorem-bearing procedure-bearing increment via
  `RouteBearingExtraction.lean`, but the full Phase A extraction closure is
  still open
- step 2 is now closed for the Phase A families at the resolved boundary
- step 3 is now closed for `ClearedTo`, published `HoldAt`, and non-circling
  `ClearedApproach` via `RouteBearingResolutionBridge.lean`; `JoinCircuit`
  remains open at this step
- step 5 is now partially closed for that same bridged subset via
  `GreenfieldRouteBearingAdmission.lean`
- step 6 is partially closed in two different ways:
  greenfield admission for the bridged subset, and legacy-bridge issuance only
  for the bridgeable pair
  `ClearedApproach` and legacy-supported `JoinCircuit`
- the current live widening gap is the route-bearing circuit bridge, followed
  by honest `ClearedApproach` completion and the remaining issuance closure

## Definition Of Done For This Track

The first route-bearing widening slice is closed when:

- `ClearedTo`, `ClearedApproach`, `HoldAt`, and `JoinCircuit` are no longer in
  Bucket C by mere exclusion
- extraction, resolution, completion, authority, and issuance semantics are
  theorem-bearing for that set
- the widened claim is stated explicitly in the FM docs
- the already-closed scoped surface remains unchanged and valid
