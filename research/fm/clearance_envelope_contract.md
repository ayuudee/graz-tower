# Clearance Envelope Contract

This note narrows
[clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md)
into the smaller clearance subset that the legacy
[ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
bridge still treats as proof-authoritative.

The purpose is not to replace the greenfield design. It is to say which part of
that design the older staging compiler still justifies, which part is currently
excluded on purpose, and which invariants the future project should carry
forward.

The project-authoritative greenfield model is now the `steps + completedSteps`
shape in
[ClearanceModel.kt](/home/andrew/dev/projects/twr2/protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/ClearanceModel.kt)
and
[GreenfieldModel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldModel.lean).
This document records the older `ClearanceEnvelope.lean` frontier/compiler
surface, not the final runtime shape.

## Scope

This contract is about the greenfield clearance envelope only.

It does not change the split-kernel claim:

- runway, surface, air-path, and separation remain local certifiers
- the clearance envelope sits above them
- the current atomic orchestration path remains a bridge, not the end state

## Settled Decisions

### 1. Sequential indexing is over sequential steps only in the legacy bridge

For the legacy `ClearanceEnvelope.lean` bridge, the proof-side compound shape
is:

- `immediateSteps`
- `sequentialSteps`
- `nextSequential`

This resolves the mixed-index ambiguity from the greenfield draft. `nextSequential`
indexes only `sequentialSteps`, never the full mixed instruction list.

The current authoritative model instead keeps a single `steps` list plus
`completedSteps`; the frontier is derived from that shape rather than stored as
separate immediate/sequential buckets.

That split is now reflected across:

- [ClearanceModel.kt](/home/andrew/dev/projects/twr2/protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/ClearanceModel.kt)
- [InstructionRules.kt](/home/andrew/dev/projects/twr2/protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/InstructionRules.kt)
- [GreenfieldModel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldModel.lean)
- [ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)

### 2. Every instruction has an explicit timing class

For the current proof subset, every instruction is classified as exactly one of:

- `sequential`
- `immediate`
- `standalone`

`standalone` means "not admitted into the current compound theorem surface."
It does not mean "unimportant." It means the envelope theorem should not claim
to sequence it yet.

Current timing classification:

- `sequential`: `TaxiTo`, `CrossRunway`, `HoldShortOf`, `BacktrackRunway`,
  `LineUpAndWait`
- `immediate`: `ClimbTo`, `DescendTo`, `ReduceSpeedTo`, `SquawkCode`,
  `ContactFrequency`, `MonitorFrequency`
- `standalone`: everything else in the current greenfield instruction surface

Important consequence:

- `ClearedTo` is currently `standalone`
- `JoinCircuit` is currently `standalone`
- `ClearedApproach` is currently `standalone`
- `HoldAt` is currently `standalone`

So the greenfield IFR example that combines `ClearedTo` with immediate
modifiers is not yet part of the proved compound subset. If the future project
wants that shape, it needs a richer route-intent and completion story first.

### 3. Surface compounds use a normal form

The current proof subset should treat surface compounds conservatively.

The intended normal form is:

1. a `TaxiTo` leg moves to the next decision point
2. a discrete runway act happens there if needed
3. another `TaxiTo` leg resumes after that act
4. the envelope ends at a terminal fence such as `HoldShortOf`

The important restriction is:

- no `TaxiTo` step may silently span a runway crossing that is also modeled as
  a separate `CrossRunway` step

So the proof-friendly form of a taxi route with a runway crossing is not
"one long TaxiTo plus CrossRunway later." It is "TaxiTo to the crossing
entry, then CrossRunway, then TaxiTo onward."

This is deliberately narrower than natural-language phraseology. It keeps the
clearance envelope aligned with the certifier ownership boundary.

### 4. Compound admission requires local frontier observability

The first sequencing theorem should only range over instructions whose effect on
the active frontier is locally observable without introducing a larger pilot or
communications semantics.

That is why the current admitted classes are:

- local movement steps with a discrete next frontier
- local scalar modifiers that are active from clearance activation

And that is why the following remain `standalone` for now:

- open-ended holds such as `HoldPosition` and `Orbit`
- route-bearing procedures such as `ClearedTo`, `JoinCircuit`,
  `ClearedApproach`, and `HoldAt`
- reports and other obligations whose completion boundary is not yet captured
  by the current envelope observation model

### 5. The first compound theorem should be narrower than full envelope atomicity

The greenfield draft asks for whole-envelope atomicity. That is directionally
right, but the first theorem should be narrower.

For now, the theorem target should be:

- monotone advancement of `nextSequential`
- no skipping of sequential steps
- compilation of the currently selected frontier only
- no admission of `standalone` instructions into the theorem surface

The theorem should not yet claim that every mixed-concern bundle has the right
supersession behavior.

In particular, movement envelopes and independently superseding modifiers such
as squawk or frequency changes should not be treated as fully solved just
because they share the `immediate` bucket.

### 6. Clearance limits require a world-level invariant

If the future project admits `ClearedTo(limit = F)`, the extraction boundary
from `AviationWorld` must guarantee:

- either `F` has an associated hold entity that can be entered lawfully
- or the instruction surface names some other explicit terminal behavior

So "limit fix exists" is not enough. The proof-relevant invariant is
"limit fix has a valid continuation regime."

### 7. Pilot intent is derived, not the proof boundary

The greenfield draft's `PilotIntent` is too lossy to be the proof-authoritative
boundary for route-bearing instructions.

For `research/fm`, the safer claim is:

- clearances are the proof boundary
- intent is a derived execution cache used by pilot logic and physics
- route-bearing clearances should not be weakened into a single target waypoint
  inside the proof contract

## Current Exclusions

The following are intentionally excluded from the first strong compound theorem:

- `ClearedTo` inside compound envelopes
- `JoinCircuit` inside compound envelopes
- `ClearedApproach` inside compound envelopes
- `HoldAt` inside compound envelopes
- open-ended movement instructions such as `Orbit` and `ExtendDownwind`
- per-step conditional semantics inside one compound envelope
- amendment semantics
- full mixed-concern supersession inside one compound envelope

Those are not rejected forever. They are just outside the current justified
claim.

## Near-Term Lean Targets

Delivered now:

1. `instructionCompoundTiming?_none_iff_standalone`
   This is now a direct contract check for the timing split.
2. `CompoundClearanceWellFormed`
   already enforces that only admitted timing classes appear in the respective
   lists.
3. frontier monotonicity and bounded advancement
   via `advanceSequentialStep_never_retreats`,
   `advanceSequentialStep_advances_by_at_most_one`, and
   `advanceSequentialStep_preservesWellFormed`.
4. frontier compilation and one-step frontier shift
   via `compile_frontier_ok_matches`,
   `activeSequentialStep_after_advance_eq_nextIndex`, and
   `frontierInstructions_after_advance`.
5. observation-driven movement-envelope stepping
   for the currently admitted sequential surface subset, via
   `StepCompletionObservation`,
   `instructionSatisfiedByObservation`,
   `advanceSequentialStepOnObservation`,
   `advanceSequentialStepOnObservation_never_retreats`,
   `advanceSequentialStepOnObservation_advances_by_at_most_one`,
   `activeSequentialStep_after_satisfied_observation_eq_nextIndex`,
   `frontierInstructions_after_satisfied_observation`, and
   `frontierInstructions_after_unsatisfied_observation`.
6. stronger movement-envelope packaging
   for the admitted sequential surface subset, via
   `advanceSequentialStepOnObservation_nextSequential_eq_succ_of_satisfied`,
   `advanceSequentialStepOnObservation_no_skipping`,
   `advanceSequentialStepOnObservation_frontier_preserved_or_shifted`, and
   `advanceSequentialStepOnObservation_movementEnvelope`.
7. checked structured-clearance lift
   of that movement-envelope surface for compound content, via
   `compile_structured_clearance_frontier_as_issuer_ok_compound_movementEnvelope`.

Next:

8. no-partial-issuance packaging
   above the current frontier and movement-envelope surface.
9. widened route-bearing / open-ended coverage
   only after the semantics for those instructions are explicit enough to
   justify a stronger theorem surface.

Only after that should the project widen route-bearing standalone instructions
into a richer envelope theorem.

## Product Translation

What the future project should inherit from this note is not the exact Kotlin
files in this repo.

It should inherit:

- the explicit timing split
- the sequential-index discipline
- the conservative surface normal form
- the rule that only locally frontier-observable instructions belong in the
  first strong envelope theorem
- the clearance-limit continuation invariant

That is the narrower claim that `research/fm` can justify today.
