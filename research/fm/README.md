# FM Research

`research/fm` is the proof-authoritative research spike for the split
certification architecture described in [brief_v4.md](/home/andrew/dev/projects/twr2/research/fm/brief_v4.md).

As of April 15, 2026, the product-authoritative world and clearance design for
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

As of April 15, 2026:

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
  `ClearedApproach` is resolved against concrete published approach /
  missed-approach facts and completes on landing or published
  missed-approach-hold entry in the current graph-backed published-procedure
  model
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
- the next honest route-bearing proof seam is now optional rather than the
  default next task:
  on the greenfield path, the Phase A core is closed on the current
  graph-backed published-procedure boundary:
  it has a theorem-bearing extraction-to-resolution bridge in
  [RouteBearingResolutionBridge.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean),
  a greenfield admission / current-shape issuance layer in
  [GreenfieldRouteBearingAdmission.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingAdmission.lean),
  a first compound current-shape issuance layer in
  [GreenfieldRouteBearingCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingCompound.lean),
  current-shape lifecycle / supersession layers in
  `GreenfieldRouteBearingLifecycle.lean` and
  `GreenfieldRouteBearingSupersession.lean`,
  and `GreenfieldRouteBearingCurrentShape.lean` packages that surface at one
  source-level theorem boundary under extracted-world well-formedness;
  `ClearedApproach` completion is no longer treated as a gap because the
  current Kotlin/Lean model now completes it on landing or
  missed-approach-hold entry, while compound approach clearances still obey
  the current engine rule that any non-persistent adjuncts must also complete;
  any widening beyond the current graph-backed published-procedure +
  one-primary-plus-immediate-adjunct compound surface is now a deliberate
  future branch, not the default continuation
- the next small widening increment above that Phase A surface is now also in
  place on the greenfield boundary:
  `JoinCircuit`, `ExtendDownwind`, and `Orbit` now match the current Kotlin
  metadata at the instruction layer, the current persistent-only-compound
  consequence is explicit rather than implicit, and
  [GreenfieldContinueApproach.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldContinueApproach.lean),
  [GreenfieldContinueApproachCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldContinueApproachCompound.lean),
  [GreenfieldExtendDownwind.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExtendDownwind.lean),
  [GreenfieldExtendDownwindCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExtendDownwindCompound.lean),
  and
  [GreenfieldOrbit.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldOrbit.lean),
  [GreenfieldOrbitCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldOrbitCompound.lean),
  with
  [GreenfieldSourceDomainPersistentPlain.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldSourceDomainPersistentPlain.lean)
  freezing the shared source-domain-supplied convention for the
  metadata-domain-less persistent families, and
  [GreenfieldRouteAdjacentCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteAdjacentCurrentShape.lean)
  packaging the whole delivered Phase B set behind one source-level
  current-shape theorem boundary
  now provide the first small closed Phase B slices:
  `ContinueApproach` now has a single-step slice plus a narrow current-shape
  compound slice, and `ExtendDownwind` / `Orbit` now each also have a narrow
  current-shape compound slice on top of their single-step slice;
  together these packages cover source-level issuance into
  `ReachableResolvedSet`, current lifecycle behavior, and explicit current
  supersession / engine-consequence theorems
- [GreenfieldRouteAdjacentAuthority.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteAdjacentAuthority.lean)
  now closes the current-shape authority layer for that delivered Phase B
  set:
  `ContinueApproach` is treated conservatively as
  `(instrumentApproach, sequence)` on the current type-level role model,
  while `ExtendDownwind` and `Orbit` map to
  `(circuitProcedure, circuit)`;
  the delivered Phase B slices now therefore also have authority-gated
  issuance on the current greenfield boundary
- the next current-Kotlin-family widening increment is now also in place:
  [GreenfieldAirspaceCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceCurrentShape.lean)
  and
  [GreenfieldAirspaceCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceCompound.lean)
  now close the first honest current-shape airspace-clearance slice:
  `RemainOutsideControlledAirspace` now has a single-step package, and
  `ClearedToEnterControlZone` / `SpecialVfrClearance` now have both a
  single-step package and a first narrow compound slice over immediate
  adjuncts;
  these instructions now exist on the greenfield Lean boundary with Kotlin-
  aligned metadata, resolve as plain route-domain steps, have explicit current
  lifecycle/supersession regressions, and use a conservative type-level
  authority mapping to `(airspaceVolume, airspaceTransit)`
- [GreenfieldAirspaceDeliveredCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceDeliveredCurrentShape.lean)
  now packages that delivered airspace-clearance slice behind one source-level
  current-shape theorem boundary, including a unified authority-gated issuance
  theorem over the already-delivered single-step and narrow-compound cases
- [GreenfieldRadioCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRadioCurrentShape.lean)
  now closes a small current-shape radio package for
  `ContactFrequency` and `MonitorFrequency`:
  source-level single-step admission, conservative
  `(radioRole, contact)` / `(radioRole, monitor)` authority, explicit and
  implicit frequency resolution, and theorem-bearing lifecycle/supersession
  regressions
- [GreenfieldSetSquawkCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldSetSquawkCurrentShape.lean)
  now closes a small current-shape `SetSquawk` package:
  source-level single-step issuance, conservative `(radioRole, squawk)`
  authority, explicit on-activation completion, and frequency
  non-supersession behavior
- [GreenfieldConfirmSquawkCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldConfirmSquawkCurrentShape.lean)
  now closes a small current-shape `ConfirmSquawk` package:
  source-level single-step issuance, conservative `(radioRole, squawk)`
  authority, explicit matching-code completion, and frequency
  non-supersession behavior
- [GreenfieldSquawkIdentCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldSquawkIdentCurrentShape.lean)
  now closes a small current-shape `SquawkIdent` package:
  source-level single-step issuance, conservative `(radioRole, squawk)`
  authority, explicit ident-activation completion, and frequency
  non-supersession behavior
- [GreenfieldSquawkStandbyCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldSquawkStandbyCurrentShape.lean)
  now closes a small current-shape `SquawkStandby` package:
  source-level single-step issuance, conservative `(radioRole, squawk)`
  authority, explicit standby-mode completion, and frequency
  non-supersession behavior
- [GreenfieldSquawkNormalCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldSquawkNormalCurrentShape.lean)
  now closes a small current-shape `SquawkNormal` package:
  source-level single-step issuance, conservative `(radioRole, squawk)`
  authority, explicit matching-mode completion, and frequency
  non-supersession behavior
- [GreenfieldStopSquawkCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldStopSquawkCurrentShape.lean)
  now closes a small current-shape `StopSquawk` package:
  source-level single-step issuance, conservative `(radioRole, squawk)`
  authority, explicit mode-change completion, and frequency
  non-supersession behavior
- [GreenfieldTransponderDeliveredCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldTransponderDeliveredCurrentShape.lean)
  now packages the delivered current-shape transponder family behind one
  source-level theorem boundary, with unified authority-gated issuance over
  the already-delivered single-step slices for `SetSquawk`,
  `ConfirmSquawk`, `SquawkIdent`, `SquawkStandby`, `SquawkNormal`, and
  `StopSquawk`
- [GreenfieldCommunicationsCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldCommunicationsCompound.lean)
  now closes the first narrow current-shape communications/surveillance
  compound slice over those delivered radio and transponder families:
  one leading communication/surveillance instruction plus immediate tails
  from the same delivered families, with whole-clearance admission,
  authority-gated issuance, and explicit current completion/supersession
  consequences
- [GreenfieldCommunicationsDeliveredCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldCommunicationsDeliveredCurrentShape.lean)
  now packages that delivered phase-2 communications/surveillance surface
  behind one source-level current-shape theorem boundary
- [GreenfieldBacktrackCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldBacktrackCurrentShape.lean)
  now closes a small current-shape `BacktrackRunway` package:
  source-level single-step issuance, conservative `(runway, backtrack)`
  authority, resolved far-end-point completion, and theorem-bearing terminal
  behavior after reconciliation
- [GreenfieldLineUpAndWaitCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldLineUpAndWaitCurrentShape.lean)
  now closes a small current-shape `LineUpAndWait` package:
  source-level single-step issuance, conservative `(runway, lineUp)`
  authority, explicit active and conditional lifecycle behavior, and
  theorem-bearing runway/frequency supersession regressions
- [GreenfieldTakeoffCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldTakeoffCurrentShape.lean)
  now closes a small current-shape `ClearedForTakeoff` package:
  source-level single-step issuance, conservative `(runway, takeoff)`
  authority, explicit airborne completion, explicit conditional
  staging/activation, and theorem-bearing frequency non-supersession
  behavior
- [GreenfieldLandingCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldLandingCurrentShape.lean)
  now closes a small current-shape `ClearedToLand` package:
  source-level single-step issuance, conservative `(runway, land)` authority,
  runway-vacation completion, explicit conditional staging/activation, and
  theorem-bearing `GoAround` supersession plus frequency non-supersession
  behavior
- [GreenfieldTouchAndGoCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldTouchAndGoCurrentShape.lean)
  now closes a small current-shape `ClearedTouchAndGo` package:
  source-level single-step issuance, conservative `(runway, touchAndGo)`
  authority, runway-transition airborne completion, explicit conditional
  staging/activation, and theorem-bearing `GoAround` supersession plus
  frequency non-supersession behavior
- [GreenfieldLowApproachCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldLowApproachCurrentShape.lean)
  now closes a small current-shape `ClearedLowApproach` package:
  source-level single-step issuance, conservative `(runway, lowApproach)`
  authority, runway-transition-and-exit completion, explicit conditional
  staging/activation, and theorem-bearing `GoAround` supersession plus
  frequency non-supersession behavior
- [GreenfieldGoAroundCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldGoAroundCurrentShape.lean)
  now closes a small current-shape `GoAround` package:
  source-level single-step issuance, conservative `(runway, goAround)`
  authority, explicit active lifecycle behavior, theorem-bearing landing
  supersession, and frequency non-supersession behavior
- [GreenfieldRunwayDeliveredCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRunwayDeliveredCurrentShape.lean)
  now packages the delivered current-shape runway-operation family behind one
  source-level theorem boundary, with unified authority-gated issuance over
  the already-delivered single-step slices for `LineUpAndWait`,
  `ClearedForTakeoff`, `ClearedToLand`, `ClearedTouchAndGo`,
  `ClearedLowApproach`, and `GoAround`
- [GreenfieldModel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldModel.lean)
  now aligns `instructionMayBeConditional` with the current Kotlin runway-
  clearance metadata for `ClearedForTakeoff`, `ClearedToLand`,
  `ClearedTouchAndGo`, and `ClearedLowApproach`
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
- code refinement and drift enforcement: closed for the delivered branch set
  via
  [refinement_inventory.md](/home/andrew/dev/projects/twr2/research/fm/refinement_inventory.md),
  the proof-side registry in
  [GreenfieldDeliveredRefinement.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean),
  and tracked Kotlin drift tests; broader automated refinement beyond the
  delivered branch set remains open

The default critical path is now:

- the scoped programme is now both `Safety-complete (N₀)` and
  `Full-brief complete`
- further work is optional scope widening, not completion-critical milestone
  work
- the active widening method is now incremental small closed slices on the
  greenfield boundary; the route-bearing proof surface defined in
  [route_bearing_scope.md](/home/andrew/dev/projects/twr2/research/fm/route_bearing_scope.md)
  remains the main route-centric widening track, but it is no longer the only
  live widening branch
- the current route-bearing / route-adjacent widening state now has a closed
  Phase A greenfield surface plus small current-shape compound slices for
  `ContinueApproach`, `ExtendDownwind`, and `Orbit`, with one source-level
  current-shape theorem packaging for that delivered Phase B set plus a
  current-shape authority layer for the same families
- the current Kotlin airspace-clearance family now also has a first honest
  greenfield slice:
  `RemainOutsideControlledAirspace` is covered as a small single-step package,
  while `ClearedToEnterControlZone` and `SpecialVfrClearance` are now widened
  through a first narrow current-shape compound slice, and the delivered
  family is now packaged behind one source-level current-shape theorem
  boundary;
  above that delivered package, a first world-backed airspace layer now exists
  too, with both single-step semantics and a first narrow compound layer;
  inside/outside plus entry/exit observation semantics are now explicit on the
  current model, but richer geometric and route-backed world airspace
  semantics are still open
- the current Kotlin radio family now also has a small closed current-shape
  slice:
  `ContactFrequency` and `MonitorFrequency` now have a single-step packaged
  theorem surface with explicit/implicit frequency resolution, conservative
  radio-role authority, and current lifecycle/supersession behavior
- the delivered radio plus transponder/surveillance families are now also
  widened through a first narrow communications/surveillance compound layer:
  one leading communication/surveillance instruction plus immediate tails
  from those same delivered families now has a packaged current-shape
  reachable/authorized issuance surface and explicit current
  completion/supersession regressions
- `BacktrackRunway` now also has a small closed current-shape slice on the
  greenfield boundary:
  single-step issuance, runway/backtrack authority, and resolved far-end-point
  completion are now theorem-bearing
- `LineUpAndWait` now also has a small closed current-shape slice on the
  greenfield boundary:
  single-step issuance, runway/line-up authority, explicit conditional
  staging/activation, and current runway/frequency supersession behavior are
  now theorem-bearing
- `ClearedForTakeoff` now also has a small closed current-shape slice on the
  greenfield boundary:
  single-step issuance, runway/takeoff authority, airborne completion,
  explicit conditional staging/activation, and current frequency
  non-supersession behavior are now theorem-bearing
- `ClearedToLand` now also has a small closed current-shape slice on the
  greenfield boundary:
  single-step issuance, runway/land authority, runway-vacation completion,
  explicit conditional staging/activation, and current `GoAround` /
  frequency supersession behavior are now theorem-bearing
- `ClearedTouchAndGo` now also has a small closed current-shape slice on the
  greenfield boundary:
  single-step issuance, runway/touch-and-go authority, runway-transition
  airborne completion, explicit conditional staging/activation, and current
  `GoAround` / frequency supersession behavior are now theorem-bearing
- `ClearedLowApproach` now also has a small closed current-shape slice on the
  greenfield boundary:
  single-step issuance, runway/low-approach authority, runway-transition-
  and-exit completion, explicit conditional staging/activation, and current
  `GoAround` / frequency supersession behavior are now theorem-bearing
- `GoAround` now also has a small closed current-shape slice on the
  greenfield boundary:
  single-step issuance, runway/go-around authority, explicit active lifecycle
  behavior, and current landing / frequency supersession behavior are now
  theorem-bearing
- the delivered current-shape runway-operation family is now also packaged
  behind one source-level theorem boundary:
  `LineUpAndWait`, `ClearedForTakeoff`, `ClearedToLand`,
  `ClearedTouchAndGo`, `ClearedLowApproach`, and `GoAround` now have one
  unified current-shape reachable/authorized issuance surface
- `GreenfieldRunwayCompound.lean` now widens that delivered runway-operation
  family through a first narrow current-shape compound slice:
  one leading runway-operation primary plus immediate adjunct tails now has
  whole-clearance resolution/admission, frozen conservative authority at the
  compound layer, and theorem-bearing current lifecycle/supersession
  consequences for the current engine
- `GreenfieldRunwayExpandedCurrentShape.lean` now packages the broadened
  current-shape runway family behind one source-level theorem boundary:
  the delivered runway-operation singles, the first narrow runway-operation
  compound slice, and single-step `BacktrackRunway` now share one reachable
  resolved-admission surface;
  this is intentionally a reachability package, not a new cross-cutting
  runway-family authority theorem
- `GreenfieldAirspaceExpandedCompound.lean` now closes the missing narrow
  compound slice for `RemainOutsideControlledAirspace`, so all three current
  Kotlin airspace-clearance families now have both a single-step slice and a
  first narrow compound slice on the greenfield boundary
- `GreenfieldAirspaceExpandedCurrentShape.lean` now packages that broadened
  current-shape airspace family behind one source-level theorem boundary
- the first world-backed airspace increment is now also in place:
  the Kotlin runtime now resolves
  `RemainOutsideControlledAirspace`,
  `ClearedToEnterControlZone`, and `SpecialVfrClearance` against concrete
  `AirspaceVolume` entities, and
  [GreenfieldAirspaceWorldBackedCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCurrentShape.lean)
  plus
  [GreenfieldAirspaceWorldBackedCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCompound.lean)
  and
  [GreenfieldAirspaceWorldBackedDeliveredCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedDeliveredCurrentShape.lean)
  now add the matching Lean boundary for world-backed airspace semantics:
  concrete airspace-volume existence in the proof-side world, resolved
  airspace payloads, graph-backed route/airspace interaction facts, source-
  level admission and authority-gated issuance, a first narrow compound layer
  over immediate adjunct tails, an explicit inside-volume plus
  entry-transition violation observation for
  `RemainOutsideControlledAirspace`, explicit persistence for
  `ClearedToEnterControlZone` and `SpecialVfrClearance`, exit/landing
  completion for the permission pair, and theorem-bearing current engine
  consequences for world-backed airspace compounds;
  this branch is now closed for the current graph-backed point-set +
  transition airspace model
- the phase-1 current-shape parity closure is now complete under the frozen
  rule used for this widening programme:
  every family whose Kotlin semantics are already stable, whose conservative
  authority family is frozen, and whose proof surface does not require new
  world-resolution theory is now theorem-bearing on the greenfield boundary;
  this now explicitly includes the delivered route/vector-control surface in
  [GreenfieldRouteControlCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteControlCurrentShape.lean),
  [GreenfieldRouteControlCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteControlCompound.lean),
  and
  [GreenfieldRouteControlDeliveredCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteControlDeliveredCurrentShape.lean),
  plus the delivered air-modifier/admin subset in
  [GreenfieldAirModifierCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirModifierCurrentShape.lean),
  with the route/vector surface now widened through its first narrow
  immediate-adjunct compound layer and `TurnByDegrees` closed on the current
  explicit observed-turn-progress boundary; richer heading-hold semantics
  remain a separate open branch
- the phase-2 communications/surveillance widening closure is now also
  complete under that same frozen rule:
  the already-delivered radio and transponder/surveillance families are now
  widened through their first narrow mixed current-shape compound/package
  surface, still without introducing new world-resolution theory
- the phase-3 runway widening closure is now also complete under that same
  frozen rule:
  the delivered runway-operation family is now widened through a first narrow
  current-shape compound slice, and the broadened current-shape runway family
  now packages that slice together with single-step `BacktrackRunway`
- the phase-4 airspace widening closure is now also complete under that same
  frozen rule:
  the whole current Kotlin airspace-clearance family now has both a
  single-step slice and a first narrow compound slice, packaged behind one
  source-level current-shape theorem boundary; above that, the first
  world-backed airspace layer is now theorem-bearing through its single-step
  and first narrow-compound slices
- the parity / refinement / drift-control branch is now closed and frozen in
  [parity_inventory.md](/home/andrew/dev/projects/twr2/research/fm/parity_inventory.md):
  the delivered families are now classified as scoped-core complete,
  current-shape complete, world-backed complete on the current model, or
  intentionally open, and the load-bearing drift seams are recorded for
  metadata, authority, completion, and supersession
- the route-to-95%-plan phase 2 is now also closed:
  [refinement_inventory.md](/home/andrew/dev/projects/twr2/research/fm/refinement_inventory.md)
  now freezes the enforcement boundary for the delivered surface,
  [GreenfieldDeliveredRefinement.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean)
  now re-exports the load-bearing theorem surfaces for the delivered branches,
  and the tracked Kotlin drift suites in
  [DeliveredMetadataParityTest.kt](/home/andrew/dev/projects/twr2/core/src/commonTest/kotlin/xyz/easiersaid/twr/core/clearance/DeliveredMetadataParityTest.kt),
  [StableRuntimeCurrentShapeTest.kt](/home/andrew/dev/projects/twr2/core/src/commonTest/kotlin/xyz/easiersaid/twr/core/clearance/StableRuntimeCurrentShapeTest.kt),
  [GroundMovementCurrentShapeTest.kt](/home/andrew/dev/projects/twr2/core/src/commonTest/kotlin/xyz/easiersaid/twr/core/clearance/GroundMovementCurrentShapeTest.kt),
  and
  [RouteAdjacentCurrentShapeTest.kt](/home/andrew/dev/projects/twr2/core/src/commonTest/kotlin/xyz/easiersaid/twr/core/clearance/RouteAdjacentCurrentShapeTest.kt)
  now make delivered metadata and current-engine behavior fail loudly on drift
- the delivered broader ground / surface movement branch is now also closed:
  [GroundMovementResolutionBridge.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GroundMovementResolutionBridge.lean),
  [GreenfieldGroundMovementCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldGroundMovementCurrentShape.lean),
  and
  [GreenfieldGroundMovementDeliveredCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldGroundMovementDeliveredCurrentShape.lean)
  now close the current graph-backed ground branch:
  world-backed `TaxiTo`, `HoldShortOf`, and `CrossRunway`, current-shape
  `HoldPosition`, and a first narrow sequential ground compound layer now all
  have packaged source-level issuance plus authority-gated issuance on the
  current model
- the next default FM work is no longer broader ground / surface movement, and
  it is no longer the delivered-branch refinement / drift-control branch
  either; the next deliberate widening choices are now broader
  communications/surveillance semantics or the next genuinely semantic branch
  beyond the current models
- richer mode semantics remain a secondary widening direction, not the default
  next task

## Reading Order

For a new human or AI agent, start here:

1. [AGENT_GUIDE.md](/home/andrew/dev/projects/twr2/research/fm/AGENT_GUIDE.md)
2. [PROJECT_STATUS.md](/home/andrew/dev/projects/twr2/research/fm/PROJECT_STATUS.md)
3. [parity_inventory.md](/home/andrew/dev/projects/twr2/research/fm/parity_inventory.md)
4. [refinement_inventory.md](/home/andrew/dev/projects/twr2/research/fm/refinement_inventory.md)
5. [greenfield_alignment.md](/home/andrew/dev/projects/twr2/research/fm/greenfield_alignment.md)
6. [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md)
7. [instruction_authority_contract.md](/home/andrew/dev/projects/twr2/research/fm/instruction_authority_contract.md)
8. [clearance_envelope_contract.md](/home/andrew/dev/projects/twr2/research/fm/clearance_envelope_contract.md)
8. [path-network-design.md](/home/andrew/dev/projects/twr2/docs/design/path-network-design.md)
9. [clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md)
10. [milestones.md](/home/andrew/dev/projects/twr2/research/fm/milestones.md)
11. [completion_milestones.md](/home/andrew/dev/projects/twr2/research/fm/completion_milestones.md)
    Shortest path from the current state to `Safety-complete (N₀)` and then to
    full-brief closure.
12. [safety_complete_scope.md](/home/andrew/dev/projects/twr2/research/fm/safety_complete_scope.md)
    Exact theorem-target inventory for the scoped `Safety-complete (N₀)` claim.
13. [route_bearing_scope.md](/home/andrew/dev/projects/twr2/research/fm/route_bearing_scope.md)
    Exact widening target for the route-bearing proof surface.
14. [certifier_interfaces.md](/home/andrew/dev/projects/twr2/research/fm/certifier_interfaces.md)
15. [canonical_theorem.md](/home/andrew/dev/projects/twr2/research/fm/canonical_theorem.md)
   Optional. Read this only if you are working on the single-issuer
   composition layer.
16. [lean/README.md](/home/andrew/dev/projects/twr2/research/fm/lean/README.md)

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
- [parity_inventory.md](/home/andrew/dev/projects/twr2/research/fm/parity_inventory.md)
  Frozen Kotlin-to-Lean parity inventory for the delivered FM surface, plus
  drift-control rules and the recommended next widening branch.
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
