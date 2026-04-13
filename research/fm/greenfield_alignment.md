# Greenfield Alignment

This note records how `research2` should now relate to the product-authoritative
design docs in
[path-network-design.md](/home/andrew/dev/projects/twr/greenfield/path-network-design.md)
and
[clearance-model-design.md](/home/andrew/dev/projects/twr/greenfield/clearance-model-design.md).

## Authority

For future-project architecture decisions, treat those two `greenfield/` docs
as authoritative.

Treat the current Kotlin boundary types in this repo
([ClearanceCompileView.kt](/home/andrew/dev/projects/twr/core/src/commonMain/kotlin/dev/twr/core/model/ClearanceCompileView.kt),
[CertifierViews.kt](/home/andrew/dev/projects/twr/core/src/commonMain/kotlin/dev/twr/core/model/CertifierViews.kt),
and
[StructuredClearance.kt](/home/andrew/dev/projects/twr/core/src/commonMain/kotlin/dev/twr/core/model/StructuredClearance.kt))
as a staging mirror of those ideas, not as the frozen API of the next
codebase.

`research2` remains the proof-authoritative place for:

- the split local certifier architecture
- whatever narrower clearance-envelope semantics are actually proved in Lean
- the explicit statement of which claims are proved and which are still open

The current narrowed envelope subset is recorded in
[clearance_envelope_contract.md](/home/andrew/dev/projects/twr/research2/clearance_envelope_contract.md).
The structural extraction boundary is now recorded in
[aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr/research2/aviation_world_extraction_contract.md).
The narrowed instruction-level authority subset is now recorded in
[instruction_authority_contract.md](/home/andrew/dev/projects/twr/research2/instruction_authority_contract.md).

## What Carries Forward

The following parts of `research2` still look like the right base for the next
project:

- separate local certifiers for runway, surface, air-path, and separation
- optional orchestration rather than mandatory one-issuer architecture
- entity-referenced clearance instructions and procedure references
- compound clearance work at the envelope layer, not buried in the older atomic
  command surface
- the split `immediateSteps` / `sequentialSteps` / `nextSequential` shape as
  the current best answer to the mixed-index ambiguity in the greenfield
  clearance draft

## What Is Now Provisional

The following should no longer be treated as the default critical path:

- implementing translators from this repo's current `WorldState` into the
  current Kotlin `ClearanceCompileView` / `CertifierViews`
- treating the current core/protocol files as the final extraction contract of
  the next project
- widening proof work around repo-specific routing assumptions before the
  remaining greenfield-derived semantics are frozen

## Greenfield Formalization Targets

The next proof-side work should make the following explicit:

- instruction-level authority mapping above the now-frozen structural
  extraction contract from overlay-entity `AviationWorld`
- compound sequencing monotonicity and no-skipping
- compound atomicity and supersession semantics, especially for mixed-concern
  compounds such as taxi plus squawk/frequency steps
- a completion taxonomy that distinguishes sequential, immediate,
  non-completing, and informational steps
- step-transition semantics inside compound envelopes, not only initial
  activation semantics
- the clearance-limit invariant: if a limit is used, the world must provide a
  valid hold or some other terminal behavior
- which instructions are admitted inside compound envelopes, especially the
  current `ClearedTo` example

## Working Implication

The next default move in `research2` is not "write adapters for today's repo."
It is "work against the greenfield-derived proof boundary and theorem targets
that the next project will rely on."

Once that contract is stable, translators can be implemented in the new
codebase or in a deliberately chosen staging layer.

## Near-Term Sequence

1. align the proof docs to `greenfield/`
2. resolve the remaining greenfield clearance semantics that block stronger
   Lean theorems
3. turn the stabilized subset into real sequencing and boundary theorems above
   [ClearanceEnvelope.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/ClearanceEnvelope.lean)
4. only then decide which extraction types and translators belong in code
