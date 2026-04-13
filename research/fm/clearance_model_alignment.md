# Clearance Model Alignment

This note records how the current FM work relates to the greenfield clearance
model now implemented in Kotlin.

## Authoritative Sources

The product-authoritative model lives in:

- [path-network-design.md](/home/andrew/dev/projects/twr2/docs/design/path-network-design.md)
- [clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md)

The Kotlin implementation that now matters for Lean alignment is:

- [Instruction.kt](/home/andrew/dev/projects/twr2/protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt)
- [ClearanceModel.kt](/home/andrew/dev/projects/twr2/protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/ClearanceModel.kt)
- [InstructionRules.kt](/home/andrew/dev/projects/twr2/protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/InstructionRules.kt)
- [StructuredClearance.kt](/home/andrew/dev/projects/twr2/core/src/commonMain/kotlin/xyz/easiersaid/twr/core/clearance/StructuredClearance.kt)
- [ResolvedClearance.kt](/home/andrew/dev/projects/twr2/core/src/commonMain/kotlin/xyz/easiersaid/twr/core/clearance/ResolvedClearance.kt)
- [CompletionEvaluation.kt](/home/andrew/dev/projects/twr2/core/src/commonMain/kotlin/xyz/easiersaid/twr/core/clearance/CompletionEvaluation.kt)
- [SupersessionEngine.kt](/home/andrew/dev/projects/twr2/core/src/commonMain/kotlin/xyz/easiersaid/twr/core/clearance/SupersessionEngine.kt)
- [ActiveClearanceEngine.kt](/home/andrew/dev/projects/twr2/core/src/commonMain/kotlin/xyz/easiersaid/twr/core/clearance/ActiveClearanceEngine.kt)

## Current Lean Boundary

There are now seven Lean layers above the local certifiers:

- [ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
  This is the older staging/compiler surface. It still owns the partial bridge
  back into the atomic certified command path and the theorem work that already
  sits above that bridge.
- [GreenfieldModel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldModel.lean)
  This is the current Kotlin-aligned clearance/lifecycle boundary. It mirrors
  the modern model shape directly rather than translating it into the older
  proof-side envelope first.
- [GreenfieldLifecycle.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldLifecycle.lean)
  This is the abstract active-clearance state machine above the current model
  boundary. It captures managed clearances, suppressed domains, admission,
  supersession, completion advancement, and conditional activation without
  importing geometry or world resolution.
- [GreenfieldResolved.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldResolved.lean)
  This is the proof-side resolved execution boundary aligned to Kotlin
  `ResolvedStep` / `ResolvedClearance`. It carries the concrete facts that
  completion actually depends on: destination points, runway transitions,
  far-end backtrack points, resolved route limits, radio roles, and circuit
  joins.
- [GreenfieldResolution.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldResolution.lean)
  This is the proof-side world-to-resolved relation. It states what world and
  state facts justify a resolved step or resolved clearance, so the resolved
  layer is no longer treated as hand-authored data.
- [GreenfieldCompletion.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldCompletion.lean)
  This is the structured proof-side completion observation contract above the
  resolved layer. It turns concrete observations like reached point,
  runway transitions, circuit membership, radio contact, altitude/speed, and
  transponder state into resolved step completion.
- [GreenfieldExecution.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExecution.lean)
  This is the resolved active-clearance layer. It combines resolved completion,
  lifecycle status updates, supersession bridging, and conditional activation
  over managed resolved clearances.

That split is intentional. `ClearanceEnvelope.lean` is still useful, but it is
no longer the authoritative model shape or lifecycle surface.

## What Changed

The current model is defined by:

- typed entity and procedure references
- `ClearanceContent.Single | Compound(steps, completedSteps)`
- envelope-level `condition`
- explicit `InstructionTiming`, `CompletionCategory`, and supersession rules
- active-clearance reconciliation over completion and supersession

`GreenfieldModel.lean` now mirrors that shape directly:

- the instruction/rule vocabulary follows the Kotlin surface rather than the
  older proof-only instruction language
- compound clearances use a mixed step list plus completed indices
- conditional clearances are normalized into envelope-level state
- set-like lifecycle state now uses an explicit `UniqueSet` abstraction rather
  than raw lists with ad hoc deduplication
- proof-frontier selection is explicit and separate from runtime timing

`GreenfieldLifecycle.lean` now owns the lower-level lifecycle algebra:

- managed clearances with suppressed domains
- staged admission of new clearances
- full and partial supersession
- abstract completion advancement over satisfied step indices
- ordered activation of conditional clearances

`GreenfieldResolved.lean` closes the previous semantic gap by introducing the
same resolved execution surface the Kotlin runtime actually uses:

- resolved taxi destinations and runway crossings
- resolved backtrack far-end points
- resolved route limits and airway/direct-join points
- resolved role/frequency and circuit-join facts

`GreenfieldResolution.lean` removes the biggest remaining discomfort in that
layer:

- resolved clearances can now be justified from proof-side world/state facts
- compatibility of resolved payloads is proved from the resolution relation
- resolved step count is tied back to the source clearance

`GreenfieldCompletion.lean` now narrows completion against that resolved
surface:

- completion observations are explicit rather than opaque oracle outputs
- supported families reduce to concrete proof-side checks over resolved payloads
- unsupported families remain explicit as unsupported rather than being guessed

`GreenfieldExecution.lean` then closes the loop:

- managed resolved clearances mirror the Kotlin active set more closely
- completion is evaluated over resolved steps, not raw instructions
- supersession still reuses the abstract lifecycle algebra, so the lower layer
  remains useful instead of being thrown away
- under an explicit unique-clearance-id assumption, other-aircraft supersession
  now preserves the resolved active set exactly rather than only preserving its
  lifecycle projection

## Important Deliberate Mismatch

`GreenfieldModel.lean` keeps two timing notions:

1. runtime timing
   `sequential | immediate | persistent`
2. proof-frontier timing
   `movement | immediate | standalone`

That is deliberate.

The Kotlin/runtime model treats instructions like `HoldShortOf`,
`LineUpAndWait`, and `HoldAt` as persistent lifecycle steps. The proof side
still needs a frontier notion that can talk about movement-style sequencing for
the admitted subset. Those are related, but they are not identical.

## Immediate Value

The new Lean boundary is immediately useful for:

- proving lifecycle/helper lemmas over the actual Kotlin clearance shape
- reasoning about `completedSteps` and frontier selection without going through
  the old compiler layer
- making conditional-clearance normalization precise before wider theorem work
- reasoning about active-clearance reconciliation without dropping back to raw
  instruction semantics
- turning a substantial subset of Kotlin completion semantics into a resolved,
  proof-side observation contract
- giving the FM side a stable target while the Kotlin architecture settles

## Next Lean Moves

The next valuable Lean steps are:

1. refine the unsupported observation cases that still need richer resolved
   world facts, especially any families beyond the current completion-relevant
   subset
2. connect the new resolution relation more directly to any future proof-side
   world model, instead of leaving it as an abstract relational interface
3. prove wider execution properties above the now-stable resolved boundary:
   reconciliation invariants, activation ordering through the full engine, and
   end-to-end admission/completion facts
4. only then decide how much of the older `ClearanceEnvelope.lean` theorem
   surface should be adapted or replaced
