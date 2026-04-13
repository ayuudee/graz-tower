# Project Status

Last updated: April 13, 2026

This file is the current execution status for `research/fm`.

## Executive Summary

`research/fm` is no longer just an architecture sketch.

It now contains:

- a frozen split-kernel contract
- a greenfield clearance boundary scaffold above the old atomic command layer
- a product-authoritative future-project input in
  [path-network-design.md](/home/andrew/dev/projects/twr2/docs/design/path-network-design.md)
  and
  [clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md),
  with the current repo's Kotlin boundary files acting as a staging mirror
- a concrete proved runway kernel
- a concrete proved surface kernel with one validation graph
- a concrete proved air-path kernel with one validation graph
- a concrete proved separation checker
- an optional partial atomic orchestration slice for:
  hold-short-of, taxi-to, takeoff, landing, touch-and-go, go-around,
  join-circuit, extend-downwind, continue-approach, reduce-speed-to, climb,
  descend, cleared-approach, and cross-controlled-airspace

It does not yet contain:

- a full proof-authoritative extraction contract from overlay-entity
  `AviationWorld` into future-project proof views; the scoped
  `Safety-complete (N₀)` extraction boundary is now theorem-bearing, but the
  broader route-bearing and dynamic semantics are still open
- settled proof-side answers for several greenfield clearance semantics that
  materially affect theorem shape: compound admission and timing, completion
  categories, step-transition effects, supersession granularity,
  clearance-limit/holding-pattern invariants, and instruction-level authority
  mapping
- an envelope-level theorem for monotone sequencing or no-partial-issuance
- the full broad-scope separation story described in the brief beyond the
  current scoped nominal surface
- richer route-bearing and operationally detailed mode semantics beyond the
  current conservative scoped full-brief layer

Scoped nominal status:

- `Safety-complete (N₀)` is now closed for the scoped surface in
  [safety_complete_scope.md](/home/andrew/dev/projects/twr2/research/fm/safety_complete_scope.md)
- `Full-brief complete` is now also closed for that same scoped surface
- remaining FM work is now optional widening, not milestone-critical closure

## Delivered Proof Artifacts

### Runway

- `runway_certify` is concrete
- local soundness is proved by `RunwayKernelMilestone1Theorem`

Source:

- [RunwayKernel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RunwayKernel.lean)

### Surface

- `surface_certify` is concrete
- local soundness is proved by `SurfaceKernelSoundnessTheorem`
- a concrete validation graph is included
- `TestAerodromeProtectedEntryApproved` proves one protected-entry approval path

Source:

- [SurfaceKernel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SurfaceKernel.lean)

### Optional Composition Layer

- `instantiate_plan` is concrete for the current partial
  runway/surface/air command slice
- the surface kernel is now wired through orchestration for `HoldShortOf` and
  `TaxiTo`
- `TaxiTo` currently uses a conservative node-route to directed-segment
  projection over the present surface graph model
- `CrossRunway` now compiles through a concrete joint runway+surface path:
  runway protection uses `protectedForCrossing`, and the surface move enters a
  protected successor segment from the current hold-point segment
- `LineUpAndWait` now compiles through that same protected-entry surface path,
  but with a `.lineUpAndWait` runway commitment instead
- narrow compatibility is concrete
- `SeparationCoverageTheorem` is proved for the current instantiated slice
- `NonBypassTheorem` is proved
- `JointActsMilestone2Theorem` is proved

Source:

- [Interfaces.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/Interfaces.lean)
- [JointActs.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/JointActs.lean)

### Greenfield Clearance Boundary

- the app-facing instruction surface is now entity-referenced on the Kotlin
  side
- explicit compound-clearance content now follows the greenfield
  `steps + completedSteps` shape, with envelope-level conditions and derived
  frontier selection in the proof layers
- the future-project world and clearance model now lives in
  [path-network-design.md](/home/andrew/dev/projects/twr2/docs/design/path-network-design.md)
  and
  [clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md),
  and the Kotlin boundary types in this repo should be read as a staging mirror
  of that direction
- proof-side `ClearanceCompileView` now exists as the middle layer between the
  rich world model and proof-friendly certifier inputs
- the structural extraction contract from `AviationWorld` into
  `ClearanceCompileView` / `CertifierViews` is now recorded in
  [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md)
- [ScopedExtraction.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedExtraction.lean)
  now makes that extraction boundary theorem-bearing for the scoped
  `Safety-complete (N₀)` surface via `ScopedAviationWorld`,
  `extractCompileView`, `extractCertifierViews`, and `extractOrchestrationEnv`
- extracted runway, taxiway, and role/frequency facts are now proved not to
  invent ids, and the in-scope runway / taxiway / role references are now
  proved preserved through extraction
- the scoped extraction layer now also proves the operational bridge back into
  the certifier-local views: runway references remain known to the runway
  kernel, taxiway segments remain known to the surface graph, and extracted
  holding-point context remains backed by real surface hold-point entries
- `ClearanceEnvelope.lean` now defines matching proof-side greenfield
  instructions, procedure references, compound frontier selection, and
  structured-clearance scaffolding
- the proof-side compile views and extraction contract now include explicit
  authority payload: role-authority grants and controller-role assignments
- extracted authority data is now theorem-bearing for the scoped surface:
  world-level role grants and controller-role assignments imply
  `controllerHasAuthorityGrant` and `instructionIssuerAuthorized`
- scoped clearance-reference lifecycle stability is now proved at the
  extraction boundary for the `Safety-complete (N₀)` instruction surface
- a narrowed instruction-level authority contract is now recorded in
  [instruction_authority_contract.md](/home/andrew/dev/projects/twr2/research/fm/instruction_authority_contract.md)
- `instructionRequiredAuthorityGrant?`, `instructionIssuerAuthorized`,
  `compoundClearanceIssuerAuthorized`, and
  `structuredClearanceIssuerAuthorized` now exist for the currently
  authority-resolved instruction subset
- that authority surface now also has frontier-level checks aligned with the
  existing envelope compiler shape via
  `compoundClearanceFrontierIssuerAuthorized` and
  `structuredClearanceFrontierIssuerAuthorized`
- the envelope layer now has authorization-aware compile seams via
  `compile_clearance_instruction_as_issuer`, `compile_frontier_as_issuer`,
  `compile_clearance_content_frontier_as_issuer`, and
  `compile_structured_clearance_frontier_as_issuer`
- whole-clearance authorization now implies frontier authorization for compound
  and structured clearances
- for authorized frontiers, the checked compiler now reduces back to the
  existing unchecked compiler via
  `compile_frontier_as_issuer_eq_compile_frontier_of_authorized`,
  `compile_clearance_content_frontier_as_issuer_eq_compile_clearance_content_frontier_of_authorized`,
  and
  `compile_structured_clearance_frontier_as_issuer_eq_compile_structured_clearance_frontier_of_authorized`
- successful checked frontier / content / structured-clearance compilation now
  also implies frontier authorization rather than only preserving frontier
  shape, with the top-level packaged result stated by
  `compile_structured_clearance_frontier_as_issuer_ok_authorized_and_matches`
- proof-side step timing is now explicit across the whole greenfield
  instruction surface via `InstructionStepTiming`
- `instructionCompoundTiming?_none_iff_standalone` and
  `instructionCompoundTiming?_isSome_iff_not_standalone` now make that timing
  split explicit at the proof boundary
- the current proof contract now makes the `standalone` exclusion explicit:
  route-bearing and open-ended instructions such as `ClearedTo`,
  `JoinCircuit`, `ClearedApproach`, and `HoldAt` are not yet admitted into the
  current compound theorem surface
- `StepCompletionObservation` and
  `instructionSatisfiedByObservation` now define an explicit completion model
  for the admitted sequential surface subset
- `compileClearanceCommand` is concrete for the current greenfield instruction
  surface
- `compile_clearance_instruction` is concrete and compiles greenfield
  instructions into the existing atomic `instantiate_plan` path
- `compile_frontier` is concrete for the current frontier shape
- `compile_frontier_ok_matches` proves that compiled frontiers preserve the
  selected immediate-plus-active-sequential instruction frontier
- one-step sequencing lemmas now exist above that frontier shape:
  `advanceSequentialStep_never_retreats`,
  `advanceSequentialStep_advances_by_at_most_one`,
  `advanceSequentialStep_preservesWellFormed`,
  `activeSequentialStep_after_advance_eq_nextIndex`, and
  `frontierInstructions_after_advance`
- observation-driven advancement now exists for the admitted sequential subset:
  satisfied observations advance at most one step and shift the active
  frontier, while unsatisfied observations preserve the current frontier, via
  `advanceSequentialStepOnObservation_never_retreats`,
  `advanceSequentialStepOnObservation_advances_by_at_most_one`,
  `advanceSequentialStepOnObservation_preservesWellFormed`,
  `activeSequentialStep_after_satisfied_observation_eq_nextIndex`,
  `frontierInstructions_after_satisfied_observation`, and
  `frontierInstructions_after_unsatisfied_observation`
- those observation lemmas are now also packaged into a stronger
  movement-envelope theorem surface for the admitted subset:
  `advanceSequentialStepOnObservation_no_skipping`,
  `advanceSequentialStepOnObservation_frontier_preserved_or_shifted`, and
  `advanceSequentialStepOnObservation_movementEnvelope`
- the checked structured-clearance seam now lifts that package for compound
  content via
  `compile_structured_clearance_frontier_as_issuer_ok_compound_movementEnvelope`
- the current Kotlin-aligned greenfield model now also includes the remaining
  scoped neutral instruction families `ReportDownwind`, `ReportFinal`, and
  `Proceed`
- `GoAround` supersession is now aligned to the runtime surface by including
  `.speed` in its superseded-domain set
- [ScopedGreenfield.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedGreenfield.lean)
  now packages the scoped greenfield theorem surface above the current
  model/lifecycle/resolution/execution stack:
  exact scoped instruction classification,
  scoped instruction-to-authority mapping,
  no-partial-issuance for compound surface envelopes,
  theorem-bearing conditional surface-compound normalization, and
  the resolved reachability wrapper used by the final top-layer path
- the scoped greenfield safety surface is now narrowed honestly at the
  air-modifier boundary: only knot speed targets remain in the current
  `Safety-complete (N₀)` claim
- [ScopedIssuance.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedIssuance.lean)
  now owns the final scoped issuing-layer theorem package above the greenfield
  boundary:
  routing completeness,
  plan-instantiation correctness,
  peer-coverage soundness,
  compatibility narrowness,
  authority-gated issuance,
  non-bypass, and
  issuance soundness
  together with the theorem-bearing bridge from scoped greenfield instructions
  into the older atomic certified command path
- [ScopedSafety.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSafety.lean)
  now closes the reachable-state safety layer above that scoped issuing
  boundary:
  scoped world/state well-formedness,
  component-preserving approval collection,
  issuance preservation of nominal/interface/kernel invariants,
  reachable issued-state semantics, and
  issued-step separation soundness for scoped separation-certified acts

Source:

- [Instruction.kt](/home/andrew/dev/projects/twr2/protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt)
- [ClearanceModel.kt](/home/andrew/dev/projects/twr2/protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/ClearanceModel.kt)
- [InstructionRules.kt](/home/andrew/dev/projects/twr2/protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/InstructionRules.kt)
- [WorldModel.kt](/home/andrew/dev/projects/twr2/core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt)
- [StructuredClearance.kt](/home/andrew/dev/projects/twr2/core/src/commonMain/kotlin/xyz/easiersaid/twr/core/clearance/StructuredClearance.kt)
- [path-network-design.md](/home/andrew/dev/projects/twr2/docs/design/path-network-design.md)
- [clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md)
- [ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
- [clearance_model_alignment.md](/home/andrew/dev/projects/twr2/research/fm/clearance_model_alignment.md)
- [greenfield_alignment.md](/home/andrew/dev/projects/twr2/research/fm/greenfield_alignment.md)
- [clearance_envelope_contract.md](/home/andrew/dev/projects/twr2/research/fm/clearance_envelope_contract.md)
- [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md)
- [instruction_authority_contract.md](/home/andrew/dev/projects/twr2/research/fm/instruction_authority_contract.md)

### Air

- `air_certify` is concrete
- local soundness is proved by `AirKernelSoundnessTheorem`
- a concrete airborne validation graph is included
- a real `reduceSpeedMax` air act now exists and is proved locally

Source:

- [AirKernel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/AirKernel.lean)

Validation artifacts:

- `TestAirGraph`
- `TestAirState`
- `TestAirBranchProposal`
- `TestAirSpeedReductionProposal`

### Separation

- `separation_check` is concrete
- local soundness is proved by `SeparationCheckerSoundnessTheorem`
- `H_sep` is explicit in Lean
- `SeparationNeutralTransition` is defined
- `SeparationBoundarySufficiencyTheorem` is proved for the current local
  step model
- `Viable_sep` and continuation kinds are defined
- `toSeparationEntityState` and `selectSeparationPeers` now live in the
  separation module rather than in orchestration
- separation projection now takes stable track identity from `AirGraph` rather
  than synthesizing `trackId` from the aircraft id
- a conservative concrete neutral-command slice is wired for
  `ReportDownwind`, `ReportFinal`, `Proceed`, `ContactFrequency`,
  `MonitorFrequency`, and `SquawkCode`
- a partial generated continuation set is wired through `air_certify` for
  continue-current-path, hold-current-path, speed-reduction,
  reserved-branch-choice, and recovery-path candidates
- `separation_check_safe_of_pairwise` now closes the local direction back from
  pairwise separation to a concrete safe result for well-formed scenarios
- the continuation set now has stronger viability support:
  `viable_sep_of_continueCurrentPathContinuation_baseline`,
  `viable_sep_of_holdCurrentPathContinuation_baseline`, and
  `viable_sep_of_reduceSpeedContinuation_baseline` now turn baseline pairwise
  separation into `Viable_sep` over the approved continuation set for those
  concrete families
- the reserved-branch and recovery-path continuation families now also have
  explicit membership and viability wrappers through
  `approvedPairwiseContinuations`
- `ScopedSeparation.lean` now packages the exact `Safety-complete (N₀)`
  separation scope: scoped command classification, scoped neutrality, scoped
  peer coverage, `ScopedSeparationBoundarySufficiencyTheorem`, and
  `ScopedViableSepTheorem` over the concrete continuation-family cases

Source:

- [SeparationChecker.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SeparationChecker.lean)
- [ScopedSeparation.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSeparation.lean)
- [certifier_view_alignment.md](/home/andrew/dev/projects/twr2/research/fm/certifier_view_alignment.md)

## Open Proof Debt

### Kernel-Local Gaps

Still incomplete relative to the brief:

- [brief_v4.md](/home/andrew/dev/projects/twr2/research/fm/brief_v4.md)
- [SeparationChecker.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SeparationChecker.lean)

Current local status:

- the concrete separation checker now has explicit Lean targets for
  non-certified-command neutrality, boundary sufficiency, and
  `Viable_sep`-style horizon viability
- those targets are now connected to the exact `Safety-complete (N₀)` scoped
  theorem surface via [ScopedSeparation.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSeparation.lean),
  and to a stronger concrete continuation package in
  [SeparationChecker.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SeparationChecker.lean)
- `ScopedIssuedScenarioViableSepTheorem` now closes the final scoped
  command-family wrapper into
  `viable_sep_of_capableApproval_equivIssuedScenario`
- the altitude-only modifier slice is no longer the blocker; it has already
  been removed from the shortest-path scoped claim
- the current hold-current-path case is conservative and collapses onto the
  same state-preserving `continueOnEdge` proposal in the present air model
- the generated continuation set now has concrete representatives for the
  continuation classes named in the brief within the current air model

### Mode Layer

- [ScopedModes.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedModes.lean)
  now provides the full-brief mode layer over the scoped nominal issuer
- assumption assessment is explicit via `AssumptionAssessment`
- strongest-justified regime selection is proved via `selectedMode_strongest`
- nominal guarantee withdrawal is proved via
  `enterAssessedMode_withdraws_nominal`
- degraded/emergency command vocabulary is concrete via `FallbackCommand`
- the mode-aware top layer is `issueWithAssessment`
- reachable scoped mode states preserve the full-brief guarantee package via
  `ReachableScopedModeState_preserves_fullBrief` and
  `FullBriefFallbackTheorem`
- this layer is intentionally conservative: the monitor is abstract and
  fallback commands update a theorem-bearing overlay rather than richer local
  certifier state

### Optional Composition Gaps

Still incomplete in:

- [Interfaces.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/Interfaces.lean)

Current limitation:

- concrete separation instantiation and peer coverage are only wired through the
  currently supported runway/air separation commands, not the full
  separation-relevant command surface

### Greenfield Boundary Gaps

Still incomplete in:

- [ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
- [GreenfieldModel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldModel.lean)

Current limitation:

- the compiler currently sits above the old atomic command layer rather than
  replacing it
- only the subset already supported by `instantiate_plan` currently yields
  certified plans through the greenfield compiler
- proof-side `ClearanceCompileView` / `CertifierViews` remain extraction
  targets, but the current Kotlin world/protocol/clearance types are only a
  staging mirror; the structural extraction contract from the overlay-entity
  `AviationWorld` is now explicit, but the final future-project runtime API is
  still not frozen
- `TaxiTo`, `JoinCircuit`, `ClearedTo`, `ClearedApproach`, and `HoldAt`
  now compile into the older atomic command vocabulary, but some of those
  atomic commands still do not have a current certified plan path
- the greenfield clearance docs still leave several proof-relevant semantics
  open or contested: compound admission and timing for `ClearedTo`,
  completion taxonomy for non-self-completing instructions, mixed-concern
  supersession granularity, step-transition effects inside compounds,
  the remaining instruction-level authority mapping, and the
  clearance-limit/holding-pattern invariant
- the current narrowed contract deliberately keeps many of those instructions in
  the `standalone` bucket, but the stronger theorems that would widen them back
  into compounds are still open
- compound sequencing now has a stronger packaged movement-envelope theorem for
  no-skipping over realized step completions in the admitted subset, but not
  yet no-partial-issuance or widened route-bearing compounds

### Optional Full Orchestration Theorem

Still open in:

- [Interfaces.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/Interfaces.lean)

Current stated-but-unproved targets:

- `CanonicalTopLevelTheorem`

## What Another Agent Should Assume

- the split architecture is stable
- the product-authoritative world and clearance docs for future-project work
  are now
  [path-network-design.md](/home/andrew/dev/projects/twr2/docs/design/path-network-design.md)
  and
  [clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md)
- the new target integration boundary is
  `AviationWorld -> ClearanceCompileView -> CertifierViews -> atomic Lean kernels`,
  with `ClearanceEnvelope.lean` above the atomic path
- runway ownership is settled
- surface ownership is settled
- air ownership is settled and concrete
- separation ownership is settled and concrete for the current local checker,
  with boundary and viability targets now partially wired to concrete command
  and continuation semantics, and with separation-local projection and peer
  selection now living in the separation module
- orchestration exists as an optional composition layer, not the only source of
  value in the project
- the old atomic command interface is no longer the only boundary that matters;
  the greenfield clearance compiler path exists as a proof scaffold, but the
  future-project extraction contract should be taken from `docs/design/` rather
  than from the current repo's runtime staging model, with the structural
  portion now recorded in
  [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md)

## Recommended Next Task

The default next engineering task is now optional widening rather than
milestone-critical closure:

1. widen the route-bearing proof surface above the current scoped nominal claim
   using
   [route_bearing_scope.md](/home/andrew/dev/projects/twr2/research/fm/route_bearing_scope.md)
   as the guardrail, starting with the extraction and resolved-semantics
   prerequisites for `ClearedTo`, `ClearedApproach`, `HoldAt`, and
   `JoinCircuit`
2. or replace the conservative mode-overlay semantics with richer degraded /
   emergency operational semantics if the product needs them
3. keep the current scoped surface stable unless a widening theorem forces a
   model change

That sequence preserves the split-kernel architecture and keeps the project
focused on the long-term product boundary instead of accumulating proof work on
the current repo's staging model.

## Product Framing

What can honestly be said now:

- the proof artifact contains concrete local certifiers for runway, surface,
  air-path, and pairwise separation, each with an explicit boundary
- there is now a partial proof-side path from the greenfield clearance model
  back into those atomic certifiers, rather than only a theorem-only atomic
  command vocabulary
- a higher-level system could consume those local guarantees without first
  requiring one giant whole-system theorem
- there is also a partial orchestration experiment showing one way to compose a
  subset of those guarantees, but that is not the primary value claim

What still cannot be said:

- this is not a certifiable ATC product
- this is not a complete command surface
- this is not evidence that the full separation boundary story from the brief
  has been proved
- this is not evidence that a production system or simulator integration is
  ready, whether with or without central orchestration

What the next phase is working toward:

- widening the extraction boundary from the scoped runway/taxiway/role subset
  to the route-bearing procedure subset
- making route-bearing resolution and completion theorem-bearing above the
  current greenfield/runtime boundary
- then widening issuance and reachable-state safety to cover the Phase A
  route-bearing families without regressing the already-closed scoped claim
