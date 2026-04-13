# `M₀` And Why It Is Still Not The Foundation

`M₀` is still not part of the proof foundation for `research/fm`.

That remains deliberate.

The split architecture separated three concerns that were previously bundled
together:

- runway commitment algebra
- surface movement over a concrete graph
- airborne movement over a concrete graph

Those do not need to enter the proof stack at the same time.

## What Does Not Need A Concrete Airport

- the runway kernel
- the high-level orchestration contract
- the static command catalog

These can and should be developed generically first.

## What Does Need A Concrete Airport

- the surface kernel
- the air-path kernel

These are graph-dependent kernels. They should be validated against concrete
instances once their local semantics are defined.

## What Has Changed Since The Original Decision

The original decision to drop `M₀` has not been reversed, but the project has
now advanced far enough to validate one graph-dependent kernel.

Specifically:

- the surface kernel is now concrete
- it includes one concrete validation graph and a proved protected-entry example

That means the practical role of a minimal airport is now clear:

- it is a validation artifact for graph-dependent kernels
- it is not the base object that the whole proof architecture is built around

## Consequence For Future Work

The air-path kernel should follow the same pattern as the surface kernel:

1. define the generic local semantics first
2. prove the local kernel theorem
3. instantiate against one concrete airborne graph afterward

That keeps the architecture clean while still forcing reality checks on the
graph-dependent pieces.
