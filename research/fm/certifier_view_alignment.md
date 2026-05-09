# Certifier View Alignment

This note records the intended boundary between the richer app/world model and
the Lean certifier kernels.

The source-of-truth world is the overlay-entity design in
[path-network-design.md](docs/design/path-network-design.md),
with envelope semantics from
[clearance-model-design.md](docs/design/clearance-model-design.md).
The proof side should not consume that whole world directly. It should consume
compiled projections that keep only the facts the certifiers and
clearance-envelope layer actually use.

The current repo staging inputs around that boundary are:

- [Instruction.kt](protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt)
- [WorldModel.kt](core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt)
- [StructuredClearance.kt](core/src/commonMain/kotlin/xyz/easiersaid/twr/core/clearance/StructuredClearance.kt)
- [ResolvedClearance.kt](core/src/commonMain/kotlin/xyz/easiersaid/twr/core/clearance/ResolvedClearance.kt)

The current Lean-side boundaries are:

- [ClearanceEnvelope.lean](research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
- [GreenfieldModel.lean](research/fm/lean/CertifiedAtc/GreenfieldModel.lean)
- [clearance_model_alignment.md](research/fm/clearance_model_alignment.md)

## View Split

The compiled proof boundary is now explicitly split into two layers:

- `ClearanceCompileView`
- `CertifierViews`

`CertifierViews` are explicitly split into:

- `RunwayCertifierView`
- `SurfaceCertifierView`
- `AirCertifierView`
- `SeparationCertifierView`

This is intentional. The local Lean kernels already have that split, so the
certifier-view boundary should match it rather than force one giant integration
model into the proofs. `ClearanceCompileView` sits above that split and keeps
the extra entity, procedure, and communications data needed to compile
greenfield clearances before dropping down into certifier-local views.

The projection keeps:

- explicit directed adjacency
- explicit hold-point and protected-entry structure
- explicit runway commitment conflict structure
- explicit air branches, junctions, guard points, and altitude bands
- explicit separation track identity
- explicit compiled procedure slices for circuits, holding patterns, and
  approaches where orchestration will need them

The projection drops:

- geometry detail not used by the certifiers
- phraseology-only naming concerns except where needed for command routing
- staffing/controller assignment detail
- general world indexing and runtime cache concerns

## Lean Delta

To stay at the current proof milestone while moving toward this compiled-view
boundary, only one Lean seam needed to change immediately.

### Runway

No structural change was required. The current runway kernel already wants a
narrow local environment plus commitment conflict kinds.

### Surface

No structural change was required. The current surface kernel already consumes a
directed segment graph with hold points and protected-entry checks.

### Air

One field was added to the air graph:

- `AirEdge.separationTrack`

That is the proof-friendly counterpart of the compiled separation-track identity
in the new view model.

### Separation

The separation layer previously synthesized `trackId` from the aircraft id. That
was a placeholder and not a defensible long-term projection boundary.

The separation projection now consumes track identity from the air graph:

- `toSeparationEntityState` now takes `AirGraph`
- `selectSeparationPeers` now takes `AirGraph`
- `Interfaces.lean` now passes the full air graph into the separation scenario
  builder

This keeps the project at the same proof milestone while making the intended
compiled-view boundary more honest.

## Still Open

The extraction contract from the richer greenfield `AviationWorld` into
proof-local views is now structurally frozen in
[aviation_world_extraction_contract.md](research/fm/aviation_world_extraction_contract.md),
including explicit authority payload, but the instruction-level and dynamic
semantics above that contract are still open.

The current repo does not yet have a Kotlin `ClearanceCompileView` /
`CertifierViews` layer; those remain proof-side extraction targets rather than
tracked runtime files. Implementing repo-local translators into those proof
views is no longer the default next move.

The partial Lean compiler from `ClearanceCompileView` into the existing atomic
certified path now exists, but it should now be read as proof scaffold rather
than as the end-state architecture.

The next integration work after that structural extraction contract is still
larger than this alignment step:

- use compiled circuit procedures to replace the current conservative
  `JoinCircuit` path
- use compiled holding-pattern views to support `HoldAt` honestly
- use compiled approach and missed-approach views to widen the current air slice
- turn the new role/entity authority scaffold into actual issuer-authority
  checks and theorem statements
- widen the clearance-envelope sequencing story above the current frontier
  compiler so compound clearances are proved at the right layer

## Current FM Status

For the scoped `Safety-complete (N₀)` surface, this alignment is now
theorem-bearing rather than only aspirational.

[ScopedExtraction.lean](research/fm/lean/CertifiedAtc/ScopedExtraction.lean)
now proves the scoped bridge from proof-side world facts into:

- `ClearanceCompileView`
- runway / surface / air certifier-local views
- issuer-authority facts consumed by `instructionIssuerAuthorized`

It also proves the concrete cross-layer facts the scoped surface needs:

- runway references stay known to the runway kernel
- taxiway segments and holding-point context stay backed by the surface graph
- extracted authority/staffing facts imply the existing compile-view authority
  checks

The still-open part is the broader future-project world, not the scoped
surface bridge itself.
