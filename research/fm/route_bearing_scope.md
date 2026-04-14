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
  extraction surfaces today, plus current-shape greenfield issuance, but they
  still do not have a theorem-bearing legacy atomic issuance path
- [RouteBearingResolutionBridge.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean)
  now closes the extraction-to-resolution seam for the full current Phase A
  surface:
  `ClearedTo`, published `HoldAt`, non-circling `ClearedApproach`, and
  `JoinCircuit` when the extracted circuit source carries an explicit supported
  join procedure
- [GreenfieldRouteBearingAdmission.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingAdmission.lean)
  now packages the first honest greenfield admission layer for that same
  bridged Phase A surface:
  authority-gated admission over the widened compile view, resolved-clearance
  existence from extracted world data, admission soundness into
  `ReachableResolvedSet`, and a packaged current-shape issuance theorem for the
  same surface
- [GreenfieldRouteBearingCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingCompound.lean)
  now widens that current-shape theorem surface from single-step route-bearing
  clearances to a narrow but useful compound family:
  one leading Phase A route-bearing step plus zero or more immediate adjunct
  instructions; it packages whole-clearance resolution, admission soundness,
  authority-gated issuance, and keeps `ClearedApproach` explicitly
  non-completing rather than inventing a completion rule
- [GreenfieldRouteBearingLifecycle.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingLifecycle.lean)
  now closes the current-shape execution behavior for that same surface:
  `ClearedTo` compounds complete on resolved limit plus adjunct completion,
  single-step `HoldAt` remains active, `HoldAt` compounds complete once their
  non-persistent adjuncts complete, single-step and compound
  `ClearedApproach` remain active, and `JoinCircuit` compounds complete on
  circuit-membership / altitude plus adjunct completion
- [GreenfieldRouteBearingSupersession.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingSupersession.lean)
  now closes the first route-bearing supersession consequences on the current
  greenfield engine:
  frequency updates partially supersede mixed route/frequency compounds
  without destroying the route-bearing step, `GoAround` fully supersedes
  active approach compounds, and the current modeled `HoldAt` behavior after
  frequency supersession is explicit rather than implicit
- [GreenfieldRouteBearingCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingCurrentShape.lean)
  now packages that whole current-shape Phase A surface behind one source-level
  theorem boundary:
  if a source `StructuredClearance` is in the currently supported Phase A
  route-bearing surface and satisfies the current authority / readiness
  conditions, then there exists a resolved clearance that admits into
  `ReachableResolvedSet`
- the full Phase A core therefore now has theorem-bearing resolved semantics,
  extraction, extraction-to-resolution bridge, single-step current-shape
  greenfield issuance, a first compound current-shape issuance layer, and a
  first theorem-bearing lifecycle / supersession package on that same
  current-shape boundary
- the first route-bearing widening slice is therefore now closed on the
  current-shape greenfield boundary
- the semantic-alignment gate for the next widening step is also now closed:
  Lean now matches the current Kotlin metadata for `JoinCircuit`,
  `ExtendDownwind`, and `Orbit` at the instruction layer
  (`JoinCircuit` / `ExtendDownwind` / `Orbit` no longer claim a metadata
  domain in Lean, matching `InstructionRules.kt`), while the current-shape
  source-level packages continue to choose an explicit fallback source domain
  where the runtime requires one
- the current execution caveat for persistent-only compounds is now explicit:
  on the present engine, once all non-persistent adjunct domains are
  suppressed, a compound with only persistent steps remaining terminals;
  the new `ExtendDownwind` / `Orbit` slices prove that current behavior
- the first honest Phase B increment is now also delivered on the same
  greenfield boundary:
  [GreenfieldContinueApproach.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldContinueApproach.lean),
  [GreenfieldContinueApproachCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldContinueApproachCompound.lean),
  [GreenfieldExtendDownwind.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExtendDownwind.lean),
  [GreenfieldExtendDownwindCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExtendDownwindCompound.lean),
  and
  [GreenfieldOrbit.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldOrbit.lean),
  [GreenfieldOrbitCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldOrbitCompound.lean),
  with
  [GreenfieldSourceDomainPersistentPlain.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldSourceDomainPersistentPlain.lean)
  freezing the shared null-domain / source-domain-supplied persistent-plain
  convention used by `ExtendDownwind` and `Orbit`, and
  [GreenfieldRouteAdjacentCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteAdjacentCurrentShape.lean)
  packaging the whole currently delivered Phase B surface behind one
  source-level current-shape theorem boundary
  now give the first small current-shape Phase B closures:
  `ContinueApproach` now has both a single-step theorem slice and a narrow
  compound slice for one leading `ContinueApproach` plus immediate adjuncts,
  and `ExtendDownwind` / `Orbit` now each have both a single-step theorem
  slice and a narrow compound slice over the same immediate-adjunct tail
  family;
  these delivered slices package source-level issuance into
  `ReachableResolvedSet`, explicit current lifecycle behavior, and explicit
  supersession / engine-consequence theorems for the currently modeled cases
- [GreenfieldRouteAdjacentAuthority.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteAdjacentAuthority.lean)
  now closes the current-shape authority layer for that delivered Phase B
  surface:
  `ContinueApproach` is mapped conservatively to
  `(instrumentApproach, sequence)` on the current type-level role model,
  while `ExtendDownwind` and `Orbit` map to
  `(circuitProcedure, circuit)`;
  the delivered Phase B single-step and narrow-compound slices now therefore
  also have authority-gated issuance on the current greenfield boundary
- legacy atomic issuance remains partial:
  `ClearedApproach` and only the legacy-supported `JoinCircuit` subset are
  still the only route-bearing families carried through the older atomic path
- `ClearedApproach` completion is now treated honestly:
  the current Kotlin/Lean model intentionally leaves it active until
  superseded, so it is not a live widening gap
- if we stay on the greenfield path, the current live widening gap is now no
  longer Phase A closure work and no longer current-shape authority closure
  for the delivered Phase B families:
  it is widening beyond the now-closed current-shape Phase A core, beyond the
  one-primary-plus-immediate-adjunct compound surface, beyond the newly closed
  current-shape compound slices for `ContinueApproach`, `ExtendDownwind`, and
  `Orbit`, beyond the newly closed source-level Phase B packaging theorem,
  and beyond the newly closed current-shape Phase B authority layer, while
  keeping the next execution semantics honest
- the remaining legacy-atomic closure for `ClearedTo` / `HoldAt` is now a
  separate optional branch, not the default next step
  This is a real choice, not just missing packaging:
  current greenfield `ClearedTo` and `HoldAt` do not line up 1:1 with every
  field on the older envelope/compiler surface, and the legacy compile path
  also reintroduces state-dependent plan-instantiation obligations.

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
- honest route-bearing lifecycle semantics
- route-bearing authority mapping
- top-layer issuance packaging

If any widening step tries to bypass those boundaries, it is the wrong step.

### 4. Keep open-ended sequencing incremental

The next widening step after Phase A should still be taken one family at a
time.

`ContinueApproach`, `ExtendDownwind`, and `Orbit` were deliberately kept out of
the first route-bearing slice. They are now entering as small current-shape
single-step closures, not as one broad new theorem package.

`CrossControlledAirspace` remains outside that work.

## Target Command Families

### Phase A: Route-Bearing Core

These are the first widening targets.

- `ClearedTo`
  Reason: route intent, clearance limit semantics, and route-bearing top-layer
  packaging.

- `ClearedApproach`
  Reason: published approach resolution, runway/procedure interface, and
  authority / issuance packaging.

- `HoldAt`
  Reason: holding-pattern resolution and clearance-limit / continuation
  semantics.

- `JoinCircuit`
  Reason: circuit entry semantics and resolved completion against circuit
  membership.

### Phase B: Route-Adjacent Small Slices

These were explicitly not in the first widening slice. The shortest honest
continuation is now to widen them one at a time on the current-shape
greenfield boundary.

- `Orbit`
- `ExtendDownwind`
- `ContinueApproach`

Current delivered state:

- `ContinueApproach` now has a closed current-shape single-step slice plus a
  first narrow current-shape compound slice:
  source-level issuance, active lifecycle, explicit `GoAround`
  supersession, and explicit current behavior for one leading
  `ContinueApproach` plus immediate adjuncts.
- `ExtendDownwind` now has a closed current-shape single-step slice plus a
  first narrow current-shape compound slice:
  source-level issuance, active lifecycle, explicit non-supersession by a
  frequency update, and explicit current behavior for one leading
  `ExtendDownwind` plus immediate adjuncts, including the current
  persistent-only compound terminalization behavior after frequency
  suppression.
- `Orbit` now has the same closure shape as `ExtendDownwind`:
  single-step plus a narrow current-shape compound slice over immediate
  adjuncts.
- the delivered Phase B surface now also has a current-shape authority layer:
  `ContinueApproach` is treated conservatively as
  `(instrumentApproach, sequence)` on the current type-level role model,
  while `ExtendDownwind` and `Orbit` are treated as
  `(circuitProcedure, circuit)`.

What remains open for Phase B is no longer basic current-shape compound
closure or current-shape authority closure for these three families. It is
now wider execution packaging beyond these delivered slices, or widening a
different family such as the current Kotlin airspace-clearance surface.

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

### 4. Completion And Lifecycle

The widening needs honest completion observations for:

- reaching a resolved clearance limit
- entering and remaining in a resolved holding pattern
- joining the resolved circuit at the required membership/altitude state

For `ClearedApproach`, the current Kotlin/Lean model intentionally does not
model completion. It remains active until superseded. Do not invent a
completion rule unless the runtime/design model changes first.

If any of the positively modeled cases need a stronger observation contract
than the current completion layer provides, that contract should be widened
explicitly rather than approximated.

That completion/lifecycle seam is now theorem-bearing for the current
greenfield widening surface via
`GreenfieldRouteBearingLifecycle.lean` and
`GreenfieldRouteBearingSupersession.lean`.
What remains open is later widening beyond that surface, not basic current-
shape execution honesty.

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
- step 3 is now closed for the Phase A families via
  `RouteBearingResolutionBridge.lean`
- step 4 is now closed for the current Phase A surface on the current-shape
  execution boundary via `GreenfieldRouteBearingLifecycle.lean` and
  `GreenfieldRouteBearingSupersession.lean`
- step 5 is now closed for the Phase A families at the current-shape
  greenfield admission/issuance boundary via
  `GreenfieldRouteBearingAdmission.lean` and
  `GreenfieldRouteBearingCompound.lean`
- step 6 is partially closed in two different ways:
  current-shape greenfield issuance plus lifecycle / supersession for the full
  Phase A surface, including one-primary-plus-immediate-adjunct compounds, and
  legacy-bridge issuance only for the bridgeable pair
  `ClearedApproach` and legacy-supported `JoinCircuit`
- the current-shape greenfield Phase A slice is now also packaged end to end by
  `GreenfieldRouteBearingCurrentShape.lean`
- `ClearedApproach` completion is not part of the live gap:
  it is intentionally unmodeled and non-completing in the current runtime/FM
  surface
- if we stay on the greenfield path, the current live widening gap is now
  later widening beyond:
  the now-closed current one-primary-plus-immediate-adjunct Phase A surface,
  plus the newly closed single-step current-shape slices for
  `ContinueApproach`, `ExtendDownwind`, and `Orbit`
- the remaining legacy-atomic closure for `ClearedTo` / `HoldAt` remains an
  optional branch rather than the default next step

## Definition Of Done For This Track

The first route-bearing widening slice is closed when:

- `ClearedTo`, `ClearedApproach`, `HoldAt`, and `JoinCircuit` are no longer in
  Bucket C by mere exclusion
- extraction, resolution, authority, issuance, and the currently intended
  lifecycle semantics are theorem-bearing for that set
- the widened claim is stated explicitly in the FM docs
- the already-closed scoped surface remains unchanged and valid

That bar is now met on the current-shape greenfield boundary.
The remaining live work is optional legacy closure or later widening, not
Phase A closure itself.
