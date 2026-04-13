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

There are now three Lean layers above the local certifiers:

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
- proof-frontier selection is explicit and separate from runtime timing

`GreenfieldLifecycle.lean` now adds the first Lean layer for the higher-level
runtime semantics:

- managed clearances with suppressed domains
- staged admission of new clearances
- full and partial supersession
- abstract completion advancement over satisfied step indices
- ordered activation of conditional clearances

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
- reasoning about active-clearance reconciliation without committing to a world
  extraction or completion sensor model yet
- giving the FM side a stable target while the Kotlin architecture settles

## Next Lean Moves

The next valuable Lean steps are:

1. prove stronger properties over `GreenfieldLifecycle.lean`:
   activation order, suppression monotonicity, and non-interference across
   aircraft
2. connect the abstract completion oracle to a narrower proof-side completion
   observation contract
3. only then decide how much of the older `ClearanceEnvelope.lean` theorem
   surface should be adapted or replaced
