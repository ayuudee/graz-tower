# Certifier Interface Spec

This document records the interface boundary between the local kernels and the
orchestration layer.

The signatures are authoritative only insofar as they match the Lean modules in
`research/fm/lean`.

## Two Valid Consumption Modes

The split-kernel architecture supports two different usage patterns:

1. higher-level systems consume local kernel guarantees directly
2. an optional orchestration layer consumes those local guarantees and becomes
   the single issuing layer

The first mode is now the closed project goal for the current research stream.
The second mode is optional integration work and should not be treated as a
blocker for controller work. See
[certified_runtime_contract_v1.md](research/fm/certified_runtime_contract_v1.md).

## Module Split

The split currently lives across these modules:

- [RunwayKernel.lean](research/fm/lean/CertifiedAtc/RunwayKernel.lean)
- [SurfaceKernel.lean](research/fm/lean/CertifiedAtc/SurfaceKernel.lean)
- [AirKernel.lean](research/fm/lean/CertifiedAtc/AirKernel.lean)
- [SeparationChecker.lean](research/fm/lean/CertifiedAtc/SeparationChecker.lean)
- [Interfaces.lean](research/fm/lean/CertifiedAtc/Interfaces.lean)
- [ClearanceEnvelope.lean](research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)

## Local Kernel Signatures

- `runway_certify : RunwayKernelEnv → RunwayState → RunwayProposal → RunwayDecision`
- `surface_certify : SurfaceGraph → SurfaceState → SurfaceProposal → SurfaceDecision`
- `air_certify : AirGraph → AirState → AirProposal → AirDecision`
- `separation_check : SeparationScenario → SeparationDecision`

## Orchestration Signatures

These signatures matter only for the optional single-issuer composition layer:

- `compile_command : CommandClass → PlanTemplate`
- `instantiate_plan :
   OrchestrationEnv → OrchestrationState → CommandProposal →
   Except CompileError CertificationPlan`
- `compatibility_check : CompatibilityInput → CompatibilityDecision`
- `issue_command :
   OrchestrationEnv → OrchestrationState → CommandProposal → IssueResult`

## Planned App-To-Proof Boundary

The intended long-term integration boundary is now:

- `AviationWorld`
- `ClearanceCompileView`
- `CertifierViews`
- local Lean kernels
- optional Lean clearance-envelope / orchestration layer

Relevant files:

- [Instruction.kt](protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt)
- [ClearanceModel.kt](protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/ClearanceModel.kt)
- [InstructionRules.kt](protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/InstructionRules.kt)
- [WorldModel.kt](core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt)
- [StructuredClearance.kt](core/src/commonMain/kotlin/xyz/easiersaid/twr/core/clearance/StructuredClearance.kt)
- [path-network-design.md](docs/design/path-network-design.md)
- [clearance-model-design.md](docs/design/clearance-model-design.md)
- [clearance_model_alignment.md](research/fm/clearance_model_alignment.md)
- [certifier_view_alignment.md](research/fm/certifier_view_alignment.md)
- [greenfield_alignment.md](research/fm/greenfield_alignment.md)
- [aviation_world_extraction_contract.md](research/fm/aviation_world_extraction_contract.md)

The current Kotlin boundary types in this repo are best read as a staging
mirror of the `docs/design/` design. The next job is to freeze the extraction
contract that a future-project implementation must satisfy, not to implement
adapters against the current repo's world model by default. The current Lean
modules are still the authoritative proof boundary, and
`GreenfieldModel.lean` is now the authoritative current-shape clearance model
while `ClearanceEnvelope.lean` remains the legacy bridge from the greenfield
clearance subset back into the atomic certified path.

## Ownership Boundary

Local kernels own:

- their own local vocabulary
- proposal legality inside their domain
- local certificates and local effects
- local invariant preservation
- separation-local projection and peer selection inside the separation state
  model

Orchestration owns:

- command-to-plan routing
- extraction of concrete local proposals from world state
- collection and bundling of local approvals
- narrow compatibility over the active set and the new approvals
- final issuance, if a single issuing layer is part of the product architecture

This boundary is a hard design rule. If a property can be checked purely inside
one kernel's local state model, it belongs in that kernel, not in orchestration.

## Current Implementation Status

- runway kernel: concrete and proved
- surface kernel: concrete and proved
- air kernel: concrete and proved
- separation checker: concrete and proved locally
- optional orchestration layer: partially concrete and partially proved
- Kotlin-facing delivered theorem surfaces: registered in
  [GreenfieldDeliveredRefinement.lean](research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean)
  and guarded by
  [parity_inventory.md](research/fm/parity_inventory.md) /
  [refinement_inventory.md](research/fm/refinement_inventory.md)

More specifically:

- `instantiate_plan` is concrete for the current supported
  runway/surface/air command slice, including `HoldShortOf`, `TaxiTo`,
  `CrossRunway`, `LineUpAndWait`, a conservative `JoinCircuit` path, and
  `ReduceSpeedTo`
- `compileClearanceCommand` in
  [ClearanceEnvelope.lean](research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
  now compiles greenfield entity-referenced instructions into the older atomic
  Lean `Command` language
- `compile_clearance_instruction` now compiles that greenfield instruction
  surface into the current atomic `instantiate_plan` path for the supported
  slice
- `compile_frontier` now compiles immediate compound steps plus the current
  active sequential step, preserving the selected frontier shape
- `TaxiTo` currently compiles through a conservative node-route to
  directed-segment projection in orchestration rather than a richer path model
- greenfield `TaxiTo` currently reaches that same path through the new compiler
  boundary
- `CrossRunway` currently compiles through a concrete joint runway+surface
  path from a current hold-point segment onto its protected successor segment
- `LineUpAndWait` currently reuses that same protected-entry joint path with a
  `.lineUpAndWait` runway commitment
- the separation projection now consumes stable track metadata from `AirGraph`
  instead of synthesizing a per-aircraft track id
- conservative peer coverage is concrete for that same instantiated slice
- `compatibility_check` is concrete
- `issue_command` and `executeCertifiedPath` support the current certified path
- the local kernels are already useful without completing the full
  orchestration theorem
- if orchestration remains in scope, completion is still open because the
  concrete separation path has not been widened beyond the current slice and
  the top-level theorem is still partial

## Primary Evaluation Path

For the primary kernel-first goal:

1. the local kernels must be concrete and defensible in isolation
2. higher-level systems may consume those local guarantees directly
3. no whole-system theorem is required just to make the local certifiers useful

## Optional Single-Issuer Evaluation Path

The intended certified path is:

1. `compile_command` selects the static plan shape for the command class
2. `instantiate_plan` builds the concrete runway, surface, air, and separation
   work items from current state
3. the required local kernels run independently
4. approvals are bundled
5. `compatibility_check` runs only over relevant approvals and the active set
6. only then may `issue_command` return an issued result

## Open Interface Risks

The current local-kernel stream is closed for the certified runtime contract.
Future local-kernel work should be deliberate strengthening, not milestone
completion work.

Potential future local-kernel strengthening includes:

- widening the now-concrete separation-layer targets for non-certified-command
  neutrality, boundary sufficiency, and horizon viability beyond the current
  conservative command slice and partial typed-command surface, which now
  includes `ReduceSpeedTo`

The important remaining optional composition obligations are deliberately
parked unless the product chooses a single-issuer architecture:

- turning the now-recorded extraction contract from overlay-entity
  `AviationWorld` into actual boundary checks and theorem inputs for the future
  project
- resolving the greenfield clearance semantics that affect proof shape:
  instruction-level authority mapping, compound admission and timing,
  completion taxonomy, step-transition effects, supersession granularity, and
  the
  clearance-limit/holding-pattern invariant
- turning frontier compilation into a real compound-clearance sequencing theorem
- widening the current concrete separation path to more command families
- keeping compatibility structurally narrow as orchestration widens
- making the full issuance theorem hold for more than the current partial
  runway/surface/air slice

For the current product direction, those concerns move upward into controller
design. The controller owns operational judgment and certifier selection; FM
provides checked kernels and drift-guarded theorem surfaces.
