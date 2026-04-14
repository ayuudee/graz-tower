# FM Research

`research/fm` is the proof-authoritative research spike for the split
certification architecture described in [brief_v4.md](/home/andrew/dev/projects/twr2/research/fm/brief_v4.md).

As of April 14, 2026, the product-authoritative world and clearance design for
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

As of April 14, 2026:

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
- the initial route-bearing widening increment now exists above the closed scoped
  programme:
  [GreenfieldRouteBearing.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearing.lean)
  packages truthful resolved-side semantics for `ClearedTo`, `HoldAt`,
  `ClearedApproach`, and `JoinCircuit`, including the fact that all four need
  specific resolution, that `ClearedTo` / `HoldAt` / `JoinCircuit` already
  have honest resolved execution or completion facts, and that
  `ClearedApproach` is resolved and intentionally non-completing in the
  current model
- the first widened issuing-layer increment also now exists in
  [BridgeableRouteBearingIssuance.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/BridgeableRouteBearingIssuance.lean):
  theorem-bearing legacy-bridge issuance for `ClearedApproach` plus
  `JoinCircuit` only where the join type still lives in the older atomic
  subset (`downwind`, `base`, `straightIn`)
- a first theorem-bearing procedure-bearing extraction increment now also
  exists in
  [RouteBearingExtraction.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean):
  route-bearing ids are proved preserved into widened compile views, the
  extracted procedure-bearing sources are proved not to invent ids, and
  compile-ready widened route-bearing instructions are proved to compile
  through the extracted `ClearanceCompileView`, including limit-supported
  `ClearedTo`; `ClearedApproach` source kinds now stay aligned to the closed
  greenfield `ApproachType` model and are bridged to legacy strings only at
  compile-view emission
- `ClearedTo` and `HoldAt` therefore now have theorem-bearing resolved and
  extraction surfaces, but they are not yet carried through the older atomic
  issuance path
- the route-bearing current-shape execution seam is now theorem-bearing too:
  [GreenfieldRouteBearingLifecycle.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingLifecycle.lean)
  closes completion/active-state behavior for `ClearedTo`, `HoldAt`,
  `ClearedApproach`, and `JoinCircuit`, and
  [GreenfieldRouteBearingSupersession.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingSupersession.lean)
  closes the first route-bearing supersession consequences on that same
  greenfield engine, including explicit proof of the current modeled `HoldAt`
  behavior after frequency supersession
- the whole current-shape Phase A surface is now also packaged behind one
  source-level theorem in
  [GreenfieldRouteBearingCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingCurrentShape.lean):
  if a source `StructuredClearance` is in the currently supported Phase A
  route-bearing surface and satisfies the present authority / readiness
  conditions, then there exists a resolved clearance that admits into
  `ReachableResolvedSet`
- the next honest route-bearing proof seam is now a choice:
  if we stay on the greenfield path, the Phase A core is now closed on the
  current-shape greenfield boundary:
  it has a theorem-bearing extraction-to-resolution
  bridge in
  [RouteBearingResolutionBridge.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean),
  plus a greenfield admission / current-shape issuance layer in
  [GreenfieldRouteBearingAdmission.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingAdmission.lean),
  a first compound current-shape issuance layer in
  [GreenfieldRouteBearingCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingCompound.lean),
  and current-shape lifecycle / supersession layers in
  `GreenfieldRouteBearingLifecycle.lean` and
  `GreenfieldRouteBearingSupersession.lean`,
  with `GreenfieldRouteBearingCurrentShape.lean` packaging that surface at one
  source-level theorem boundary;
  `ClearedApproach` completion is no longer treated as a gap because the
  current Kotlin/Lean model intentionally leaves it active until superseded;
  the default live gap is now widening beyond the current
  one-primary-plus-immediate-adjunct compound surface rather than legacy
  bridge work
- the next small widening increment above that Phase A surface is now also in
  place on the greenfield boundary:
  `JoinCircuit`, `ExtendDownwind`, and `Orbit` now match the current Kotlin
  metadata at the instruction layer, the current persistent-only-compound
  consequence is explicit rather than implicit, and
  [GreenfieldContinueApproach.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldContinueApproach.lean),
  [GreenfieldExtendDownwind.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExtendDownwind.lean),
  and
  [GreenfieldOrbit.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldOrbit.lean)
  now each provide a closed current-shape single-step theorem slice:
  source-level issuance into `ReachableResolvedSet`, current lifecycle
  behavior, and one explicit supersession / engine-consequence theorem
- a full route-bearing proof-authoritative extraction contract still does not
  exist yet; the new route-bearing extraction increment is real, but it is
  still a first procedure-bearing slice rather than the full Phase A
  extraction closure
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
- the current route-bearing / route-adjacent widening state now has a closed
  Phase A greenfield surface plus three additional small single-step
  current-shape slices for `ContinueApproach`, `ExtendDownwind`, and `Orbit`
- the immediate next widening step is no longer structural bridge work, no
  longer approach-completion work, and no longer basic closure for
  `ContinueApproach` / `ExtendDownwind` / `Orbit`; it is to decide how far to
  widen those newly delivered slices on the greenfield boundary, or whether to
  carry `ClearedTo` / `HoldAt` through the older atomic path
  That is a real design/proof choice, not just missing routine packaging:
  current greenfield `ClearedTo` / `HoldAt` do not line up 1:1 with every
  field on the older envelope/compiler surface, and the legacy compiler path
  also reintroduces state-dependent plan-instantiation obligations
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
- [GreenfieldModel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldModel.lean)
  Current Kotlin-aligned greenfield model surface:
  `steps + completedSteps`, envelope-level conditions, lifecycle categories,
  and frontier selection.
- [GreenfieldLifecycle.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldLifecycle.lean)
  Abstract active-clearance lifecycle layer over the current greenfield model.
- [GreenfieldResolved.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldResolved.lean)
  Proof-side resolved execution boundary aligned to Kotlin `ResolvedStep` /
  `ResolvedClearance`.
- [GreenfieldResolution.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldResolution.lean)
  Proof-side world-to-resolved relation for the current execution boundary.
- [GreenfieldCompletion.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldCompletion.lean)
  Structured completion-observation layer over resolved steps.
- [GreenfieldExecution.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExecution.lean)
  Resolved active-clearance execution and reconciliation layer.
- [GreenfieldReachability.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldReachability.lean)
  Reachable active-set package above the resolved execution layer.
- [GreenfieldRouteBearing.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearing.lean)
  First widened route-bearing layer above the closed scoped programme:
  truthful resolved-side semantics for `ClearedTo`, `HoldAt`,
  `ClearedApproach`, and `JoinCircuit`.
- [RouteBearingExtraction.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean)
  First widened procedure-bearing extraction layer above the scoped
  extraction boundary: widened source world, route-bearing reference
  preservation, and compile-success theorems for compile-ready widened
  instructions through `ClearanceCompileView`.
- [BridgeableRouteBearingIssuance.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/BridgeableRouteBearingIssuance.lean)
  Honest legacy-bridge issuance only for the route-bearing families the older
  atomic path can already carry: `ClearedApproach` plus legacy-supported
  `JoinCircuit`.
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
