# Clearance Model Alignment

This note records how `research2` now relates to the greenfield clearance model.

As of April 12, 2026, the product-authoritative clearance and world-model docs
for future-project work are
[clearance-model-design.md](/home/andrew/dev/projects/twr/greenfield/clearance-model-design.md)
and
[path-network-design.md](/home/andrew/dev/projects/twr/greenfield/path-network-design.md).
The Kotlin boundary types in this repo are still useful, but they should now be
read as a staging mirror rather than as the frozen API of the next project.

The important split is:

1. `AviationWorld`
2. `ClearanceCompileView`
3. `CertifierViews`
4. local Lean certifiers
5. optional Lean orchestration and clearance-envelope layer

The old direct world-to-certifier framing is no longer enough once clearances are
entity-referenced and may be compound.

## Current Boundary

The current Kotlin boundary pieces are now:

- [Instruction.kt](/home/andrew/dev/projects/twr/protocol/src/commonMain/kotlin/dev/twr/protocol/types/Instruction.kt)
- [ProcedureRef.kt](/home/andrew/dev/projects/twr/protocol/src/commonMain/kotlin/dev/twr/protocol/types/ProcedureRef.kt)
- [ClearanceContent.kt](/home/andrew/dev/projects/twr/protocol/src/commonMain/kotlin/dev/twr/protocol/types/ClearanceContent.kt)
- [ClearanceCompileView.kt](/home/andrew/dev/projects/twr/core/src/commonMain/kotlin/dev/twr/core/model/ClearanceCompileView.kt)
- [CertifierViews.kt](/home/andrew/dev/projects/twr/core/src/commonMain/kotlin/dev/twr/core/model/CertifierViews.kt)
- [StructuredClearance.kt](/home/andrew/dev/projects/twr/core/src/commonMain/kotlin/dev/twr/core/model/StructuredClearance.kt)

The current Lean boundary pieces are now:

- [Interfaces.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/Interfaces.lean)
- [ClearanceEnvelope.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/ClearanceEnvelope.lean)

## What Changed

### App-Level Instructions

The app-facing instruction language now follows the greenfield direction:

- `TaxiVia`, not `TaxiTo`
- `JoinCircuit` by circuit-procedure id
- `ContactFrequency` and `MonitorFrequency` by role
- `ClearedTo` by `ProcedureRef` plus optional clearance limit
- `ClearedApproach` by approach id
- `HoldAt` by holding-pattern id

These are entity-referenced instructions. They are not the same thing as the
current atomic Lean `Command`.

### Compound Clearance Shape

The app boundary now has explicit compound-clearance structure through
`ClearanceContent.Compound`, split into:

- `immediateSteps`
- `sequentialSteps`
- `nextSequential`

This is intentional. It avoids the ambiguous single mixed-index `currentStep`
shape from the draft.

The narrower proof-side contract for what is actually admitted into that
compound surface now lives in
[clearance_envelope_contract.md](/home/andrew/dev/projects/twr/research2/clearance_envelope_contract.md).

### Compile Layer

`ClearanceCompileView` is the new middle layer. It keeps exactly the overlay
entity and AIP facts needed to compile greenfield clearances into atomic
certifier work:

- taxiways and holding points
- runways and exits
- circuits, approaches, holding patterns
- route procedures and fixes
- role/frequency and handoff data

It is richer than `CertifierViews` and narrower than the full world model.
Inside this repo, it is best read as a staging shape for proof exploration
rather than as the final extraction contract of the next codebase.

### Lean Envelope Layer

`ClearanceEnvelope.lean` now introduces the proof-side scaffolding for:

- entity-referenced clearance instructions
- procedure references
- explicit `sequential | immediate | standalone` instruction timing
  classification
- compound frontier selection
- a partial compiler from greenfield instructions into the current atomic
  `CertificationPlan` path
- compiled-frontier signatures above atomic `CertificationPlan`

This module does not replace the current local kernels or atomic orchestration
path. It sits above them.

## What Has Not Changed Yet

- the local kernels are still atomic
- `Interfaces.lean` still certifies one atomic `CommandProposal` at a time
- the current proved orchestration slice is still fundamentally expressed in
  the older atomic command language
- the new compiler only gets the greenfield boundary back to the current
  supported atomic slice; it does not yet replace that slice
- the structural extraction contract from overlay-entity `AviationWorld` into
  future-project proof views is now recorded in
  [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr/research2/aviation_world_extraction_contract.md),
  but the full semantic contract above it is not complete yet
- translators into the current repo's `ClearanceCompileView` and
  `CertifierViews` do not exist, but implementing them here is no longer the
  default next step
- several greenfield clearance semantics that materially affect theorem shape
  are still unsettled: which instructions are admitted in compounds, how mixed
  timing works, what counts as completion for non-self-completing instructions,
  how step transitions change pilot behavior, how issuer authority maps onto
  instruction families, and what the clearance-limit invariant requires from
  the world model
- no envelope-level theorem about monotone sequencing, no-skipping, or
  no-partial-issuance exists yet

## Immediate Next Step

The next structural move is to finish the proof boundary above the current
partial compiler against the greenfield docs:

- use
  [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr/research2/aviation_world_extraction_contract.md)
  as the stable structural contract that a future-project `AviationWorld` must
  satisfy for the certifier-local views
- turn the open greenfield clearance semantics into explicit proof-side
  decisions
- make sequencing a first-class theorem above `compile_frontier`
- then widen command coverage and separation through that stabilized path rather
  than continuing to prove directly against the older atomic interface
