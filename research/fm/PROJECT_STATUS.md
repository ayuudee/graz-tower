# Project Status

Last updated: April 15, 2026

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
- a first honest route-bearing widening increment above the closed scoped
  programme: resolved semantics for `ClearedTo`, `HoldAt`, `ClearedApproach`,
  and `JoinCircuit`, a first theorem-bearing procedure-bearing extraction
  increment, a theorem-bearing extraction-to-resolution bridge for the full
  Phase A surface, a current-shape greenfield admission / issuance layer for
  that same surface, a first current-shape compound issuance layer for one
  leading route-bearing step plus immediate adjuncts, a current-shape
  lifecycle layer for that surface, a current-shape supersession layer for
  that surface, a current-shape source-level closure theorem for that surface,
  plus theorem-bearing legacy-bridge issuance for
  `ClearedApproach` and the legacy-supported `JoinCircuit` subset
- the semantic-alignment gate for the next greenfield widening step is now
  closed too: Lean now matches the current Kotlin metadata for `JoinCircuit`,
  `ExtendDownwind`, and `Orbit`, and the current persistent-only-compound
  consequence is now explicit in theorem-bearing execution regressions rather
  than being only an implicit engine artifact
- the first route-adjacent widening increment above Phase A is now in place as
  a small set of closed current-shape Phase B slices:
  `ContinueApproach`, `ExtendDownwind`, and `Orbit` now each have a
  single-step slice plus a narrow current-shape compound slice, with
  `GreenfieldSourceDomainPersistentPlain.lean` freezing the shared null-domain
  / source-domain-supplied helper story for the latter two, and
  `GreenfieldRouteAdjacentCurrentShape.lean` packaging the delivered Phase B
  surface behind one source-level current-shape theorem boundary;
  together they now have source-level issuance into `ReachableResolvedSet`,
  explicit current lifecycle behavior, and explicit supersession /
  engine-consequence theorems for the currently modeled cases
- `GreenfieldRouteAdjacentAuthority.lean` now closes the current-shape
  authority layer for that delivered Phase B surface:
  `ContinueApproach` is treated conservatively as
  `(instrumentApproach, sequence)` on the current type-level role model,
  while `ExtendDownwind` and `Orbit` map to
  `(circuitProcedure, circuit)`;
  the delivered Phase B single-step and narrow-compound slices now therefore
  also have authority-gated issuance on the current greenfield boundary
- `GreenfieldAirspaceCurrentShape.lean` now closes the first honest widening
  slice for the current Kotlin airspace-clearance family:
  `RemainOutsideControlledAirspace`,
  `ClearedToEnterControlZone`, and `SpecialVfrClearance` now exist on the
  greenfield Lean boundary with Kotlin-aligned metadata, a small single-step
  current-shape issuance package, explicit current lifecycle/supersession
  regressions, and conservative type-level authority mapping to
  `(airspaceVolume, airspaceTransit)`
- `GreenfieldAirspaceCompound.lean` now widens that same family one step
  further on the current-shape boundary:
  `ClearedToEnterControlZone` and `SpecialVfrClearance` now have a first
  narrow compound slice over immediate adjuncts, with whole-clearance
  admission, authority-gated issuance, and explicit current engine
  consequences for adjunct completion and frequency supersession
- `GreenfieldAirspaceDeliveredCurrentShape.lean` now packages that delivered
  airspace family behind one source-level current-shape theorem boundary,
  so the single-step `RemainOutsideControlledAirspace` slice and the narrow
  compound `ClearedToEnterControlZone` / `SpecialVfrClearance` slice now have
  one packaged reachable/authorized issuance surface
- `GreenfieldRadioCurrentShape.lean` now closes a small current-shape radio
  package for `ContactFrequency` and `MonitorFrequency`:
  source-level single-step admission, conservative
  `(radioRole, contact)` / `(radioRole, monitor)` authority, explicit and
  implicit frequency resolution, and theorem-bearing lifecycle/supersession
  regressions
- `GreenfieldSetSquawkCurrentShape.lean` now closes a small current-shape
  `SetSquawk` package:
  source-level single-step issuance, conservative `(radioRole, squawk)`
  authority, explicit on-activation completion, and frequency
  non-supersession regressions
- `GreenfieldConfirmSquawkCurrentShape.lean` now closes a small current-shape
  `ConfirmSquawk` package:
  source-level single-step issuance, conservative `(radioRole, squawk)`
  authority, explicit matching-code completion, and frequency
  non-supersession regressions
- `GreenfieldSquawkIdentCurrentShape.lean` now closes a small current-shape
  `SquawkIdent` package:
  source-level single-step issuance, conservative `(radioRole, squawk)`
  authority, explicit ident-activation completion, and frequency
  non-supersession regressions
- `GreenfieldSquawkStandbyCurrentShape.lean` now closes a small current-shape
  `SquawkStandby` package:
  source-level single-step issuance, conservative `(radioRole, squawk)`
  authority, explicit standby-mode completion, and frequency
  non-supersession regressions
- `GreenfieldSquawkNormalCurrentShape.lean` now closes a small current-shape
  `SquawkNormal` package:
  source-level single-step issuance, conservative `(radioRole, squawk)`
  authority, explicit matching-mode completion, and frequency
  non-supersession regressions
- `GreenfieldStopSquawkCurrentShape.lean` now closes a small current-shape
  `StopSquawk` package:
  source-level single-step issuance, conservative `(radioRole, squawk)`
  authority, explicit mode-change completion, and frequency
  non-supersession regressions
- `GreenfieldTransponderDeliveredCurrentShape.lean` now packages the
  delivered current-shape transponder family behind one source-level theorem
  boundary, with unified authority-gated issuance over the already-delivered
  single-step slices for `SetSquawk`, `ConfirmSquawk`, `SquawkIdent`,
  `SquawkStandby`, `SquawkNormal`, and `StopSquawk`
- `GreenfieldCommunicationsCompound.lean` now closes the delivered
  communications/surveillance compound seam on the current immediate
  radio/transponder model:
  mixed radio/transponder compounds now have whole-clearance resolution,
  admission, authority-gated issuance, and explicit current
  completion/supersession consequences
- `GreenfieldCommunicationsDeliveredCurrentShape.lean` now packages that
  delivered communications/surveillance surface behind one source-level
  current-shape theorem boundary, and
  `GreenfieldCommunicationsExpandedCurrentShape.lean` now packages the
  broadened branch closure for the current immediate radio/transponder model
- `GreenfieldRadioJurisdictionWorldBacked.lean` now closes the first
  world-backed published-handoff jurisdiction slice for `ContactFrequency`
  and `MonitorFrequency`, and
  `GreenfieldCommunicationsJurisdictionDeliveredCurrentShape.lean` now
  packages that branch behind one reachable/authorized theorem surface
- `GreenfieldBacktrackCurrentShape.lean` now closes a small current-shape
  `BacktrackRunway` package:
  source-level single-step issuance, conservative
  `(runway, backtrack)` authority, resolved far-end-point completion, and
  terminal behavior on reconciliation
- `GreenfieldLineUpAndWaitCurrentShape.lean` now closes a small current-shape
  `LineUpAndWait` package:
  source-level single-step issuance, conservative
  `(runway, lineUp)` authority, explicit active and conditional lifecycle
  behavior, and theorem-bearing runway/frequency supersession regressions
- `GreenfieldTakeoffCurrentShape.lean` now closes a small current-shape
  `ClearedForTakeoff` package:
  source-level single-step issuance, conservative
  `(runway, takeoff)` authority, explicit airborne completion, explicit
  conditional staging/activation, and theorem-bearing frequency
  non-supersession behavior
- `GreenfieldLandingCurrentShape.lean` now closes a small current-shape
  `ClearedToLand` package:
  source-level single-step issuance, conservative
  `(runway, land)` authority, runway-vacation completion, explicit
  conditional staging/activation, and theorem-bearing `GoAround` plus
  frequency supersession regressions
- `GreenfieldTouchAndGoCurrentShape.lean` now closes a small current-shape
  `ClearedTouchAndGo` package:
  source-level single-step issuance, conservative
  `(runway, touchAndGo)` authority, runway-transition airborne completion,
  explicit conditional staging/activation, and theorem-bearing `GoAround`
  plus frequency supersession regressions
- `GreenfieldLowApproachCurrentShape.lean` now closes a small current-shape
  `ClearedLowApproach` package:
  source-level single-step issuance, conservative
  `(runway, lowApproach)` authority, runway-transition-and-exit completion,
  explicit conditional staging/activation, and theorem-bearing `GoAround`
  plus frequency supersession regressions
- `GreenfieldGoAroundCurrentShape.lean` now closes a small current-shape
  `GoAround` package:
  source-level single-step issuance, conservative `(runway, goAround)`
  authority, explicit active lifecycle behavior, theorem-bearing landing
  supersession, and frequency non-supersession regressions
- `GreenfieldRunwayDeliveredCurrentShape.lean` now packages the delivered
  current-shape runway-operation family behind one source-level theorem
  boundary, with unified authority-gated issuance over the already-delivered
  single-step slices for `LineUpAndWait`, `ClearedForTakeoff`,
  `ClearedToLand`, `ClearedTouchAndGo`, `ClearedLowApproach`, and `GoAround`
- `GreenfieldRunwayCompound.lean` now widens that delivered runway-operation
  family through a first narrow current-shape compound layer:
  one leading runway-operation primary plus immediate adjunct tails now has
  whole-clearance resolution/admission, frozen conservative authority at the
  compound layer, and theorem-bearing current lifecycle/supersession
  consequences for the current engine
- `GreenfieldRunwayExpandedCurrentShape.lean` now packages the broadened
  current-shape runway family behind one source-level theorem boundary:
  the delivered runway-operation singles, the first narrow runway-operation
  compound slice, and single-step `BacktrackRunway` now share one reachable
  resolved-admission surface;
  this is intentionally a reachability package rather than a new cross-cutting
  runway-family authority theorem
- the phase-1 current-shape parity closure is now complete under the frozen
  widening rule:
  every family whose Kotlin semantics are already stable, whose conservative
  authority family is frozen, and whose proof surface does not require new
  world-resolution theory is now theorem-bearing on the greenfield boundary;
  this now explicitly includes the delivered route/vector-control surface
  through its first narrow immediate-adjunct compound layer and the delivered
  air-modifier/admin subset, with `TurnByDegrees` now also closed on the
  current explicit observed-turn-progress boundary
- the phase-2 communications/surveillance widening closure is now also
  complete under that same frozen rule:
  the already-delivered radio and transponder/surveillance families are now
  closed on the current immediate radio/transponder model, including mixed
  compounds and explicit lifecycle/supersession behavior, still without
  introducing new world-resolution theory
- `GreenfieldAirspaceExpandedCompound.lean` now closes the missing narrow
  compound slice for `RemainOutsideControlledAirspace`, so all three current
  Kotlin airspace-clearance families now have both a single-step slice and a
  first narrow compound slice on the greenfield boundary
- `GreenfieldAirspaceExpandedCurrentShape.lean` now packages that broadened
  current-shape airspace family behind one source-level theorem boundary
- `GreenfieldAirspaceWorldBackedCurrentShape.lean` now adds the first honest
  world-backed airspace layer above that package:
  concrete proof-side `AirspaceVolume` membership, resolved airspace payloads,
  graph-backed route/airspace interaction facts, source-level admission and
  authority-gated issuance, explicit inside-volume plus entry-transition
  violation observation for
  `RemainOutsideControlledAirspace`, and explicit persistence for
  `ClearedToEnterControlZone` and `SpecialVfrClearance`, with exit/landing
  completion for the permission pair
- `GreenfieldAirspaceWorldBackedCompound.lean` and
  `GreenfieldAirspaceWorldBackedDeliveredCurrentShape.lean` now widen that
  same world-backed airspace layer through its first narrow compound slice and
  package it behind one source-level theorem boundary:
  one leading world-backed airspace primary plus immediate adjunct tails now
  has whole-clearance admission, authority-gated issuance, and theorem-bearing
  current engine consequences for tail completion, frequency supersession, and
  entry/exit observation on the current model
- the phase-3 runway widening closure is now complete under that same frozen
  widening rule:
  the delivered runway-operation family is now widened through a first narrow
  current-shape compound slice, and the broadened current-shape runway family
  now packages that slice together with single-step `BacktrackRunway`
- the phase-4 airspace widening closure is now also complete under that same
  frozen widening rule:
  the whole current Kotlin airspace-clearance family now has both a
  single-step slice and a first narrow compound slice, packaged behind one
  source-level current-shape theorem boundary; above that, the first
  world-backed airspace layer is now theorem-bearing through both its
  single-step and first narrow-compound slices, and that world-backed branch
  is now closed for the current graph-backed point-set + transition model
- `GreenfieldModel.lean` is now aligned with the current Kotlin runway-
  clearance conditional metadata for `ClearedForTakeoff`, `ClearedToLand`,
  `ClearedTouchAndGo`, and `ClearedLowApproach`
- [parity_inventory.md](/home/andrew/dev/projects/twr2/research/fm/parity_inventory.md)
  now freezes the Kotlin-to-Lean parity / refinement boundary for the
  delivered surface:
  the delivered families are classified as scoped-core complete,
  current-shape complete, world-backed complete on the current model, or
  intentionally open, and the load-bearing drift seams are recorded for
  metadata, authority, completion, and supersession
- the route-to-95%-plan phase-2 refinement / drift-control closure is now
  also complete:
  [refinement_inventory.md](/home/andrew/dev/projects/twr2/research/fm/refinement_inventory.md)
  now freezes the enforcing Kotlin/Lean surfaces for the delivered branch set,
  [GreenfieldDeliveredRefinement.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean)
  now registers the load-bearing delivered theorem surfaces in one Lean
  module, and the tracked Kotlin drift tests now cover frozen metadata plus
  the delivered current-engine seams for stable runtime and route-adjacent
  behavior
- `GroundMovementResolutionBridge.lean`,
  `GreenfieldGroundMovementCurrentShape.lean`, and
  `GreenfieldGroundMovementDeliveredCurrentShape.lean` now close the broader
  ground / surface movement branch on the current graph-backed ground model:
  `TaxiTo`, `HoldShortOf`, and `CrossRunway` now have a world-backed ground
  boundary, `HoldPosition` now has a closed current-shape boundary, and the
  first narrow sequential ground compound layer is now theorem-bearing and
  packaged behind one source-level theorem surface on the explicit
  ground-progress observation model: traversed points, reached holding
  points, crossed runways, and stopped-on-ground state
- the next honest route-bearing gap is now an explicit optional branch rather
  than a structural blocker: on the greenfield path, the live work is only
  whatever widening we choose beyond the current graph-backed
  published-procedure + one-primary-plus-immediate-adjunct compound surface
  and beyond the newly closed small current-shape compound slices for the
  current Phase B families; the older atomic closure for `ClearedTo` /
  `HoldAt` remains optional and separate
- the delivered-branch refinement/drift-control branch is now closed too, so
  the next default FM work is no longer parity enforcement; the next branch is
  again a deliberate widening choice

It does not yet contain:

- a full proof-authoritative extraction contract from overlay-entity
  `AviationWorld` into future-project proof views; the scoped
  `Safety-complete (N₀)` extraction boundary is now theorem-bearing, but the
  broader route-bearing and dynamic semantics are still open
- settled proof-side answers for several greenfield clearance semantics that
  materially affect theorem shape: compound admission and timing, completion
  categories, step-transition effects, supersession granularity,
  clearance-limit/holding-pattern invariants, and the remaining unresolved
  instruction-level authority mapping beyond the now-delivered Phase B
  current-shape layer
- an envelope-level theorem for monotone sequencing or no-partial-issuance
- the full broad-scope separation story described in the brief beyond the
  current scoped nominal surface
- a full route-bearing package beyond the closed current-shape Phase A
  surface; Phase A itself is now closed on the current greenfield boundary,
  but full extraction closure, broader authority closure, and legacy-bridge
  closure remain incomplete, with legacy-bridge issuance still limited to
  `ClearedApproach` plus legacy-supported `JoinCircuit`
- a widened airspace package beyond the now-delivered current-shape family and
  first world-backed airspace layer; the current Lean/runtime-aligned story
  now does claim concrete airspace-volume-backed resolution for single-step
  airspace instructions and the first world-backed compound layer, plus
  graph-backed route interaction and point-set entry/exit/landing lifecycle
  semantics, but it still does not claim richer geometric/polygonal airspace
  semantics, denser route/airspace interaction semantics, or broader
  world-backed lifecycle semantics beyond the current point-set +
  transition-based model
- richer communications/surveillance semantics beyond the current immediate
  radio/transponder + published-handoff model; the delivered immediate branch
  and the published-handoff jurisdiction branch are now closed, but broader
  coordination/jurisdiction/surveillance semantics are still open
- a widened current-shape runway family beyond the now-delivered first narrow
  compound slice; the broadened family now packages the delivered
  runway-operation singles, the first narrow runway-operation compounds, and
  single-step `BacktrackRunway`, but it does not yet widen the broader
  ground-movement family or add a new cross-cutting runway-family authority
  theorem
- widened current-shape compound/authority packaging for the newly delivered
  Phase B families beyond their current narrow compound slices; the delivered
  Phase B surface is now source-level packaged and current-shape
  authority-closed, but not yet widened beyond those slices
- richer operationally detailed mode semantics beyond the current conservative
  scoped full-brief layer

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
- the older frontier/compiler contract still makes the `standalone` exclusion
  explicit: route-bearing and open-ended instructions such as `ClearedTo`,
  `JoinCircuit`, `ClearedApproach`, and `HoldAt` are not admitted into that
  older compound theorem surface directly; the widened route-bearing work now
  lives above it in `GreenfieldRouteBearing.lean`
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
- [GreenfieldRouteBearing.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearing.lean)
  now packages the first honest route-bearing widening increment above the closed
  scoped programme:
  route-bearing-core classification,
  proof that all four Phase A families require specific resolution,
  resolved-side authority mapping where a concrete entity is identified, and
  truthful resolved completion/execution facts for `ClearedTo`, `HoldAt`,
  `ClearedApproach`, and `JoinCircuit`
- [BridgeableRouteBearingIssuance.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/BridgeableRouteBearingIssuance.lean)
  now adds theorem-bearing legacy-bridge issuance for the route-bearing pair
  the older atomic path can honestly carry:
  `ClearedApproach`, and `JoinCircuit` only when the greenfield join type maps
  back into the legacy subset (`downwind`, `base`, `straightIn`)
- [RouteBearingExtraction.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean)
  now adds the first theorem-bearing procedure-bearing extraction increment
  above `ScopedExtraction`:
  widened source-world data for circuits, holding patterns, approaches, SIDs,
  airways, STARs, VFR routes, and fixes;
  origin/no-invented-id lemmas for the widened compile-view data;
  route-bearing reference preservation into `ClearanceCompileView`; and
  compile-success theorems for compile-ready widened instructions, including
  `ClearedTo` with a supported clearance limit;
  `ClearedApproach` source kinds are now also constrained to the closed
  greenfield `ApproachType` model and bridged to legacy strings only at
  compile-view emission
- `ClearedTo` and `HoldAt` therefore now have theorem-bearing resolved and
  extraction surfaces, but they are not yet admitted through the older atomic
  issuance layer
- `ClearedApproach` is now route-bearing-resolved and issuance-bridgeable, and
  it now completes on landing or published missed-approach-hold entry in the
  current Kotlin/Lean execution layer
- [GreenfieldRouteBearingCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingCompound.lean)
  now widens the current-shape theorem-bearing surface from single-step
  route-bearing clearances to one-leading-route-bearing-step compounds with
  immediate adjunct tails:
  whole-clearance resolution, admission soundness, authority-gated issuance,
  and the current world-backed approach-lifecycle boundary, where the primary
  step can complete on landing or missed-hold entry but non-persistent
  adjuncts still have to complete before the whole compound terminals
- [GreenfieldRouteBearingLifecycle.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingLifecycle.lean)
  now closes the current-shape execution behavior for that same surface:
  `ClearedTo` compounds complete on resolved limit plus adjunct completion,
  single-step `HoldAt` remains active, `HoldAt` compounds complete once their
  non-persistent adjuncts complete, single-step `ClearedApproach` completes on
  landing or missed-hold entry, compound `ClearedApproach` clearances complete
  when that primary event and any remaining adjunct completions all occur, and
  `JoinCircuit` compounds complete on circuit-membership / altitude plus
  adjunct completion
- [GreenfieldRouteBearingSupersession.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingSupersession.lean)
  now closes the first route-bearing supersession consequences on the current
  greenfield engine:
  frequency updates partially supersede mixed route/frequency compounds
  without destroying the route-bearing step, `GoAround` fully supersedes
  active approach compounds, and the current modeled `HoldAt` behavior after
  frequency supersession is explicit rather than implicit
- [GreenfieldRouteBearingCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingCurrentShape.lean)
  now packages that whole current-shape Phase A surface behind one source-level
  theorem boundary:
  if a source `StructuredClearance` is in the currently supported Phase A
  route-bearing surface and satisfies the current authority / readiness
  conditions, then there exists a resolved clearance that admits into
  `ReachableResolvedSet`
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
- [RouteBearingExtraction.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean)

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
  now compile into the older atomic command vocabulary, but theorem-bearing
  widened issuance is currently honest only for `ClearedApproach` and the
  legacy-supported `JoinCircuit` subset
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
- the route-bearing widening is now real but partial:
  `GreenfieldRouteBearing.lean` gives truthful resolved semantics for
  `ClearedTo`, `HoldAt`, `ClearedApproach`, and `JoinCircuit`,
  `RouteBearingExtraction.lean` gives the first theorem-bearing
  procedure-bearing extraction increment for that same track,
  `RouteBearingResolutionBridge.lean` and
  `GreenfieldRouteBearingAdmission.lean` now carry the full Phase A surface to
  current-shape greenfield issuance,
  `GreenfieldRouteBearingCompound.lean` widens that to one-leading-step
  compounds with immediate adjunct tails, while
  `BridgeableRouteBearingIssuance.lean` currently widens theorem-bearing
  legacy issuance only for `ClearedApproach` and legacy-supported
  `JoinCircuit`

## Recommended Next Task

The default next engineering task is no longer broader ground / surface
movement; that branch is now closed for the current graph-backed model.

1. keep the parity inventory frozen while widening:
   update [parity_inventory.md](/home/andrew/dev/projects/twr2/research/fm/parity_inventory.md)
   whenever metadata, authority, completion, supersession, or family status
   changes
2. choose one branch at a time:
   the next genuinely semantic branch beyond the current models
3. keep the currently closed scoped, current-shape, and world-backed branches
   stable while doing that work

That sequence preserves the split-kernel architecture, keeps the delivered
surface honest, and avoids reopening branches that are already closed for
their current models.

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

- using the now-frozen Kotlin-to-Lean parity/refinement boundary as a guardrail
  for the next widening branch
- choosing one genuinely semantic branch beyond the current delivered models,
  rather than reopening already-closed scoped, current-shape, world-backed,
  or refinement branches
- keeping the already-closed scoped, current-shape, and world-backed branches
  stable while that widening happens
