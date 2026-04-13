# FM Research

`research/fm` is the proof-authoritative research spike for the split
certification architecture described in [brief_v4.md](/home/andrew/dev/projects/twr2/research/fm/brief_v4.md).

As of April 13, 2026, the product-authoritative world and clearance design for
the next project lives in
[path-network-design.md](/home/andrew/dev/projects/twr2/docs/design/path-network-design.md)
and
[clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md).
`research/fm` should now be read as the formalization base for that future
project, not as a commitment to the current repo's runtime model.

The working claim is narrower than "prove ATC."

The primary goal is to mechanize discrete local guarantees for separate
certifiers with explicit ownership boundaries, so higher-level systems can
consume those guarantees without collapsing everything into one giant proof
object.

Those primary certifiers are:

- runway
- surface
- air-path
- separation

A secondary goal, only if the product architecture actually wants a single
issuing layer, is to show one orchestration pattern that composes those local
guarantees into a central certified issuance path.

## Goal Hierarchy

Read `research/fm` in this order of importance:

1. robust isolated certifiers with explicit local contracts
2. clear boundaries between those certifiers
3. optional orchestration / composition proofs if a single-issuer architecture
   is desired

## Current Status

As of April 13, 2026:

- architecture contract: frozen
- generic runway kernel: implemented and proved locally
- optional orchestration layer: the current atomic certified path still
  supports `HoldShortOf`, `TaxiTo`, `CrossRunway`, `ClearedForTakeoff`,
  `LineUpAndWait`, `ClearedToLand`, `ClearedTouchAndGo`, `GoAround`,
  `JoinCircuit`, `ExtendDownwind`, `ContinueApproach`, `ReduceSpeedTo`,
  `ClimbTo`, `DescendTo`, `ClearedApproach`, and `CrossControlledAirspace`
- the product-authoritative world and clearance model now lives in
  [path-network-design.md](/home/andrew/dev/projects/twr2/docs/design/path-network-design.md)
  and
  [clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md)
- the current Kotlin model in this repo remains a useful staging mirror of that
  direction: entity-referenced instructions and typed procedure references in
  [Instruction.kt](/home/andrew/dev/projects/twr2/protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt),
  compound-clearance vocabulary in
  [ClearanceModel.kt](/home/andrew/dev/projects/twr2/protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/ClearanceModel.kt),
  instruction timing/domain rules in
  [InstructionRules.kt](/home/andrew/dev/projects/twr2/protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/InstructionRules.kt),
  the world model in
  [WorldModel.kt](/home/andrew/dev/projects/twr2/core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt),
  and clearance lifecycle state in
  [StructuredClearance.kt](/home/andrew/dev/projects/twr2/core/src/commonMain/kotlin/xyz/easiersaid/twr/core/clearance/StructuredClearance.kt)
- the structural extraction contract from overlay-entity `AviationWorld` into
  proof-local views is now recorded in
  [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md),
  including the requirement that authority data extract as explicit role grants
  plus controller-role assignments
- the scoped `Safety-complete (N₀)` extraction boundary is now theorem-bearing
  in
  [ScopedExtraction.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedExtraction.lean):
  extracted compile-view facts are proved not to invent ids, in-scope
  runway/taxiway/role references are proved preserved, the runway/surface
  operational bridge back into the certifier-local views is proved, and
  extracted authority data now implies `controllerHasAuthorityGrant` and
  `instructionIssuerAuthorized`
- the narrowed instruction-level authority surface is now recorded in
  [instruction_authority_contract.md](/home/andrew/dev/projects/twr2/research/fm/instruction_authority_contract.md),
  with a conservative Lean mapping for the currently authority-resolved
  instruction families
- the Lean side now has
  [GreenfieldModel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldModel.lean)
  as the current-shape greenfield model, while
  [ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
  remains the legacy frontier/compiler bridge back into the existing atomic
  certified path for the currently supported slice
- the scoped greenfield theorem surface is now explicitly packaged in
  [ScopedGreenfield.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedGreenfield.lean):
  greenfield-side authority mapping for the scoped surface, a no-partial-
  issuance theorem for compound surface envelopes, theorem-bearing conditional
  surface-compound normalization, and a scoped reachability wrapper over the
  resolved execution layer
- the final scoped issuing-layer theorem package now exists in
  [ScopedIssuance.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedIssuance.lean):
  a theorem-bearing bridge into the older atomic certified path, plus routing,
  plan-instantiation, peer-coverage, compatibility, authority-gated issuance,
  non-bypass, and issuance soundness for the scoped `Safety-complete (N₀)`
  surface
- the reachable-state safety layer above that final issuing boundary now exists
  in
  [ScopedSafety.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSafety.lean):
  scoped world/state well-formedness, preservation of the nominal / runway /
  surface / air / interface invariant package through issuance, a reachable
  issued-state relation, and issued-step separation soundness for the scoped
  separation-certified surface
- the scoped air-modifier claim is now narrowed honestly to the variants that
  the current local air/separation proof story can actually carry: knot speed
  targets only
- a full route-bearing proof-authoritative extraction contract does not exist
  yet; the current theorem-bearing extraction module is intentionally scoped to
  the `Safety-complete (N₀)` surface
- surface kernel over a concrete surface graph: implemented and proved locally
- air kernel over a concrete airborne graph: implemented and proved locally
- separation layer: concrete local checker plus explicit Lean targets for
  `H_sep`, non-certified-command neutrality, stepwise boundary sufficiency, and
  `Viable_sep`; a conservative neutral-command slice and a partial generated
  continuation set are now wired through the current air-state model, including
  a conservative hold-current-path case and a real speed-reduction act;
  [ScopedSeparation.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSeparation.lean)
  now also exposes `ScopedSeparationBoundarySufficiencyTheorem` and
  `ScopedViableSepTheorem`, and
  [SeparationChecker.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SeparationChecker.lean)
  now proves `viable_sep_of_capableApproval_equivIssuedScenario`; that local
  theorem is now consumed by
  `ScopedIssuedScenarioViableSepTheorem` in `ScopedSeparation`, so the scoped
  nominal separation bar is closed
- [ScopedModes.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedModes.lean)
  now closes the full-brief mode layer for the same scoped surface: abstract
  assumption assessment, strongest-justified regime selection, nominal
  guarantee withdrawal, a concrete fallback command vocabulary, and reachable
  full-brief preservation over the mode-aware top layer
- optional orchestration layer: partial composition proof exists, but it is not
  the primary success criterion
- code refinement and enforcement: not started

The default critical path is now:

- the scoped programme is now both `Safety-complete (N₀)` and
  `Full-brief complete`
- further work is optional scope widening, not completion-critical milestone
  work
- the active widening track is the route-bearing proof surface defined in
  [route_bearing_scope.md](/home/andrew/dev/projects/twr2/research/fm/route_bearing_scope.md)
- richer mode semantics remain a secondary widening direction, not the default
  next task

## Reading Order

For a new human or AI agent, start here:

1. [AGENT_GUIDE.md](/home/andrew/dev/projects/twr2/research/fm/AGENT_GUIDE.md)
2. [PROJECT_STATUS.md](/home/andrew/dev/projects/twr2/research/fm/PROJECT_STATUS.md)
3. [greenfield_alignment.md](/home/andrew/dev/projects/twr2/research/fm/greenfield_alignment.md)
4. [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md)
5. [instruction_authority_contract.md](/home/andrew/dev/projects/twr2/research/fm/instruction_authority_contract.md)
6. [clearance_envelope_contract.md](/home/andrew/dev/projects/twr2/research/fm/clearance_envelope_contract.md)
7. [path-network-design.md](/home/andrew/dev/projects/twr2/docs/design/path-network-design.md)
8. [clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md)
9. [milestones.md](/home/andrew/dev/projects/twr2/research/fm/milestones.md)
10. [completion_milestones.md](/home/andrew/dev/projects/twr2/research/fm/completion_milestones.md)
    Shortest path from the current state to `Safety-complete (N₀)` and then to
    full-brief closure.
11. [safety_complete_scope.md](/home/andrew/dev/projects/twr2/research/fm/safety_complete_scope.md)
    Exact theorem-target inventory for the scoped `Safety-complete (N₀)` claim.
12. [route_bearing_scope.md](/home/andrew/dev/projects/twr2/research/fm/route_bearing_scope.md)
    Exact next widening target above the scoped completed claim.
13. [certifier_interfaces.md](/home/andrew/dev/projects/twr2/research/fm/certifier_interfaces.md)
14. [canonical_theorem.md](/home/andrew/dev/projects/twr2/research/fm/canonical_theorem.md)
   Optional. Read this only if you are working on the single-issuer
   composition layer.
15. [lean/README.md](/home/andrew/dev/projects/twr2/research/fm/lean/README.md)

Then go to the specific Lean module you need.

## Directory Layout

- [brief_v4.md](/home/andrew/dev/projects/twr2/research/fm/brief_v4.md)
  Original design brief.
- [canonical_theorem.md](/home/andrew/dev/projects/twr2/research/fm/canonical_theorem.md)
  Optional top-level orchestration theorem and what it is supposed to mean.
- [command_catalog.md](/home/andrew/dev/projects/twr2/research/fm/command_catalog.md)
  Static command vocabulary and plan-shape contract.
- [certifier_interfaces.md](/home/andrew/dev/projects/twr2/research/fm/certifier_interfaces.md)
  Kernel and orchestration signatures, plus implementation status.
- [certifier_view_alignment.md](/home/andrew/dev/projects/twr2/research/fm/certifier_view_alignment.md)
  Boundary from the greenfield world into proof-friendly certifier inputs.
- [clearance_model_alignment.md](/home/andrew/dev/projects/twr2/research/fm/clearance_model_alignment.md)
  Boundary from greenfield clearances into the current atomic Lean certified
  path.
- [clearance_envelope_contract.md](/home/andrew/dev/projects/twr2/research/fm/clearance_envelope_contract.md)
  Narrowed proof-authoritative subset of the greenfield clearance envelope.
- [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md)
  Structural extraction contract from `AviationWorld` into proof-local views.
- [instruction_authority_contract.md](/home/andrew/dev/projects/twr2/research/fm/instruction_authority_contract.md)
  Narrowed instruction-to-authority mapping for the currently stable subset.
- [greenfield_alignment.md](/home/andrew/dev/projects/twr2/research/fm/greenfield_alignment.md)
  How `research/fm` should now relate to the product-authoritative
  `docs/design/` specs and the future project boundary.
- [m0_instance.md](/home/andrew/dev/projects/twr2/research/fm/m0_instance.md)
  Why a concrete airport is not part of the proof foundation.
- [milestones.md](/home/andrew/dev/projects/twr2/research/fm/milestones.md)
  Nine-phase roadmap and current execution status.
- [completion_milestones.md](/home/andrew/dev/projects/twr2/research/fm/completion_milestones.md)
  Shortest-path completion plan from the current FM state to
  `Safety-complete (N₀)`.
- [safety_complete_scope.md](/home/andrew/dev/projects/twr2/research/fm/safety_complete_scope.md)
  Exact scoped theorem target for `Safety-complete (N₀)`.
- [route_bearing_scope.md](/home/andrew/dev/projects/twr2/research/fm/route_bearing_scope.md)
  Exact next widening target for the route-bearing proof surface.
- [PROJECT_STATUS.md](/home/andrew/dev/projects/twr2/research/fm/PROJECT_STATUS.md)
  Current theorem inventory, proof debt, and next work.
- [AGENT_GUIDE.md](/home/andrew/dev/projects/twr2/research/fm/AGENT_GUIDE.md)
  Working guide for follow-on agents.
- [lean/](/home/andrew/dev/projects/twr2/research/fm/lean)
  Standalone Lean 4 project and source of truth.

## Lean Modules

The Lean project is organized as:

- [Core.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/Core.lean)
  Shared vocabulary and base types.
- [CommandCatalog.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/CommandCatalog.lean)
  Typed command catalog and static plan templates.
- [RunwayKernel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RunwayKernel.lean)
  Concrete, proved runway kernel.
- [SurfaceKernel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SurfaceKernel.lean)
  Concrete, proved surface kernel with a validation graph.
- [AirKernel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/AirKernel.lean)
  Concrete local air-path checker with local soundness theorem.
- [SeparationChecker.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SeparationChecker.lean)
  Concrete pairwise separation checker with a local soundness theorem.
- [ScopedSeparation.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSeparation.lean)
  Scoped `Safety-complete (N₀)` separation theorem package over the current
  certifier and orchestration surfaces.
- [ScopedExtraction.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedExtraction.lean)
  Scoped extraction boundary from proof-side world facts into
  `ClearanceCompileView`, certifier-local views, and issuer-authority facts.
- [ScopedGreenfield.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedGreenfield.lean)
  Scoped greenfield theorem package for `Safety-complete (N₀)`: scoped
  authority mapping, no-partial-issuance for surface compounds, and the bridge
  into the resolved execution/reachability layer.
- [ScopedIssuance.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedIssuance.lean)
  Final scoped issuing layer above extraction and greenfield execution:
  honest greenfield-to-atomic bridging plus the Milestone 5 theorem package.
- [ScopedSafety.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSafety.lean)
  Reachable-state safety layer above the scoped issuing boundary:
  state preservation for nominal/runway/surface/air/interface invariants plus
  issued-step separation soundness.
- [Interfaces.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/Interfaces.lean)
  Optional orchestration / composition layer, including plan instantiation,
  compatibility, peer coverage, and a partial issuance path.
- [ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
  Greenfield clearance vocabulary, frontier selection, and partial compiler
  into the current atomic certified path.
- [JointActs.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/JointActs.lean)
  Narrow orchestration milestone theorem for the first joint acts.

## Build

From the repo root:

```bash
nix-shell -p lean4 --run 'cd research/fm/lean && lake build'
```

Build a single module:

```bash
nix-shell -p lean4 --run 'cd research/fm/lean && lake build CertifiedAtc.SurfaceKernel'
```

## Working Rules

- Lean is the source of truth. Docs summarize it; they do not override it.
- Any proof-progress change in `research/fm/lean` should be reflected in the
  status and roadmap docs in the same change.
- Keep the split ownership boundary intact:
  runway, surface, air, and separation stay local; orchestration, if used,
  composes rather than replaces them.
