# Route-Bearing Widening Scope

Last updated: April 13, 2026

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

### 3. Completion

The widening needs honest completion observations for:

- reaching a resolved clearance limit
- entering/being established on a resolved approach
- entering and remaining in a resolved holding pattern
- joining the resolved circuit at the required membership/altitude state

If any of those need a stronger observation contract than the current
completion layer provides, that contract should be widened explicitly rather
than approximated.

### 4. Authority and Issuance

The widening must add:

- authority mapping for the route-bearing families
- compatibility and no-partial-issuance packaging where compound envelopes mix
  route-bearing and existing scoped steps
- top-layer issuance soundness for the widened surface

## Shortest Honest Order

Do the widening in this order:

1. widen extraction for route-bearing references
2. widen resolved-step semantics for the Phase A families
3. widen completion observations only as much as those resolved steps need
4. widen greenfield/execution packaging
5. widen the issuing layer and reachable-state safety package

Do not start with the top theorem. Start with the route-bearing data path.

## Definition Of Done For This Track

The first route-bearing widening slice is closed when:

- `ClearedTo`, `ClearedApproach`, `HoldAt`, and `JoinCircuit` are no longer in
  Bucket C by mere exclusion
- extraction, resolution, completion, authority, and issuance semantics are
  theorem-bearing for that set
- the widened claim is stated explicitly in the FM docs
- the already-closed scoped surface remains unchanged and valid
