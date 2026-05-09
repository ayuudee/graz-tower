# Greenfield Alignment

This note records how `research/fm` should now relate to the product-authoritative
design docs in
[path-network-design.md](docs/design/path-network-design.md)
and
[clearance-model-design.md](docs/design/clearance-model-design.md).

## Authority

For future-project architecture decisions, treat those two `docs/design/` docs
as authoritative.

Treat the current Kotlin boundary types in this repo
([Instruction.kt](protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt),
[WorldModel.kt](core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt),
and
[StructuredClearance.kt](core/src/commonMain/kotlin/xyz/easiersaid/twr/core/clearance/StructuredClearance.kt))
as a staging mirror of those ideas, not as the frozen API of the next
codebase.

`research/fm` remains the proof-authoritative place for:

- the split local certifier architecture
- whatever narrower clearance-envelope semantics are actually proved in Lean
- the explicit statement of which claims are proved and which are still open

The current legacy envelope subset is recorded in
[clearance_envelope_contract.md](research/fm/clearance_envelope_contract.md).
The structural extraction boundary is now recorded in
[aviation_world_extraction_contract.md](research/fm/aviation_world_extraction_contract.md).
The narrowed instruction-level authority subset is now recorded in
[instruction_authority_contract.md](research/fm/instruction_authority_contract.md).

## What Carries Forward

The following parts of `research/fm` still look like the right base for the next
project:

- separate local certifiers for runway, surface, air-path, and separation
- optional orchestration rather than mandatory one-issuer architecture
- entity-referenced clearance instructions and procedure references
- compound clearance work at the envelope layer, not buried in the older atomic
  command surface
- the authoritative greenfield model shape of `steps + completedSteps`, with the
  older `immediateSteps / sequentialSteps / nextSequential` frontier retained
  only as a legacy staging/compiler layer in `ClearanceEnvelope.lean`

## What Is Now Provisional

The following should no longer be treated as the default critical path:

- implementing translators from this repo's current runtime state directly into
  the proof-side `ClearanceCompileView` / `CertifierViews`
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

The next default move in `research/fm` is not "write adapters for today's repo."
It is "work against the greenfield-derived proof boundary and theorem targets
that the next project will rely on."

Once that contract is stable, translators can be implemented in the new
codebase or in a deliberately chosen staging layer.

## Post-Closure Sequence

1. keep the scoped `Safety-complete (N₀)` and `Full-brief complete` surface
   stable
2. choose one widening track at a time: richer route-bearing semantics or richer
   operational mode semantics
3. widen extraction, greenfield semantics, and top-layer theorems together
4. only then decide which new runtime boundary types belong in code
