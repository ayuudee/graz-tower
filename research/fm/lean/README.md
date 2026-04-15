# Lean Project Guide

This is the standalone Lean 4 project for the FM work inside `twr2`.

## Build

From the repository root:

```bash
nix-shell -p lean4 --run 'cd research/fm/lean && lake build'
```

## Module Order

Read modules in roughly this order:

1. [CertifiedAtc/Core.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/Core.lean)
2. [CertifiedAtc/CommandCatalog.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/CommandCatalog.lean)
3. [CertifiedAtc/RunwayKernel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RunwayKernel.lean)
4. [CertifiedAtc/SurfaceKernel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SurfaceKernel.lean)
5. [CertifiedAtc/AirKernel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/AirKernel.lean)
6. [CertifiedAtc/SeparationChecker.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SeparationChecker.lean)
7. [CertifiedAtc/Interfaces.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/Interfaces.lean)
   Optional. This is the existing atomic orchestration layer.
8. [CertifiedAtc/ScopedSeparation.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSeparation.lean)
   Optional. This is the scoped `Safety-complete (N₀)` separation package over
   the current certifier and orchestration surfaces.
9. [CertifiedAtc/ScopedExtraction.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedExtraction.lean)
   Optional. This is the scoped extraction boundary from proof-side world facts
   into `ClearanceCompileView`, certifier-local views, and issuer-authority
   facts.
10. [CertifiedAtc/RouteBearingExtraction.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean)
   Optional. This is the first widened procedure-bearing extraction layer
   above `ScopedExtraction`: widened source-world data, route-bearing
   reference preservation, and compile-success theorems for compile-ready
   widened instructions.
11. [CertifiedAtc/RouteBearingResolutionBridge.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean)
   Optional. This is the first theorem-bearing bridge from the widened
   procedure-bearing extraction world into the current resolved execution
   world for `ClearedTo`, published `HoldAt`, non-circling
   `ClearedApproach`, and supported `JoinCircuit`.
12. [CertifiedAtc/ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
   Optional. This is the older greenfield-to-atomic staging compiler and theorem surface.
13. [CertifiedAtc/GreenfieldModel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldModel.lean)
   Optional. This is the current Kotlin-aligned greenfield boundary for protocol, compound clearances, conditional normalization, and lifecycle/frontier reasoning.
14. [CertifiedAtc/GreenfieldLifecycle.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldLifecycle.lean)
    Optional. This is the abstract active-clearance state machine over the Kotlin-aligned model: staging, supersession, completion advancement, and conditional activation.
15. [CertifiedAtc/GreenfieldResolved.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldResolved.lean)
    Optional. This is the proof-side resolved execution boundary aligned to Kotlin `ResolvedStep` / `ResolvedClearance`.
16. [CertifiedAtc/GreenfieldResolution.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldResolution.lean)
    Optional. This is the proof-side world-to-resolved relation: it states what world facts justify a resolved step/clearance.
17. [CertifiedAtc/GreenfieldCompletion.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldCompletion.lean)
    Optional. This evaluates structured observations against resolved steps: reached point, runway transition, circuit membership, altitude/speed, radio role/frequency, and transponder state.
18. [CertifiedAtc/GreenfieldExecution.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExecution.lean)
    Optional. This is the resolved active-clearance layer: managed resolved clearances, resolved completion, and active-set reconciliation.
19. [CertifiedAtc/GreenfieldReachability.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldReachability.lean)
    Optional. This packages execution preservation results into a reachable active-set boundary.
20. [CertifiedAtc/GreenfieldPlainCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldPlainCurrentShape.lean)
    Optional. This is the reusable current-shape helper for single-step
    greenfield instructions that resolve as plain steps rather than
    world-specific payloads.
21. [CertifiedAtc/GreenfieldContinueApproach.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldContinueApproach.lean)
    Optional. This closes the current-shape single-step `ContinueApproach`
    slice: source-level issuance, active lifecycle, and explicit `GoAround`
    supersession.
22. [CertifiedAtc/GreenfieldContinueApproachCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldContinueApproachCompound.lean)
    Optional. This widens `ContinueApproach` one step further on the current
    greenfield boundary: one leading `ContinueApproach` plus immediate adjunct
    tails, with explicit lifecycle and supersession consequences.
23. [CertifiedAtc/GreenfieldSourceDomainPersistentPlain.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldSourceDomainPersistentPlain.lean)
    Optional. This freezes the shared helper for metadata-domain-less
    persistent plain instructions whose runtime domain comes from the source
    clearance.
24. [CertifiedAtc/GreenfieldExtendDownwind.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExtendDownwind.lean)
    Optional. This closes the current-shape single-step `ExtendDownwind`
    slice and makes the current persistent-only compound consequence explicit.
25. [CertifiedAtc/GreenfieldExtendDownwindCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExtendDownwindCompound.lean)
    Optional. This widens `ExtendDownwind` through a first narrow current-shape
    compound slice over immediate adjunct tails.
26. [CertifiedAtc/GreenfieldOrbit.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldOrbit.lean)
    Optional. This closes the current-shape single-step `Orbit` slice and
    makes the same persistent-only compound consequence explicit.
27. [CertifiedAtc/GreenfieldOrbitCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldOrbitCompound.lean)
    Optional. This widens `Orbit` through the same narrow current-shape
    compound slice shape as `ExtendDownwind`.
28. [CertifiedAtc/GreenfieldRouteAdjacentCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteAdjacentCurrentShape.lean)
    Optional. This packages the delivered Phase B route-adjacent surface
    behind one source-level current-shape theorem boundary.
29. [CertifiedAtc/GreenfieldRouteAdjacentAuthority.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteAdjacentAuthority.lean)
    Optional. This closes the current-shape authority layer for the delivered
    Phase B route-adjacent surface.
30. [CertifiedAtc/GreenfieldAirspaceCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceCurrentShape.lean)
    Optional. This closes the first current-shape single-step slice for the
    current Kotlin airspace-clearance family.
31. [CertifiedAtc/GreenfieldAirspaceCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceCompound.lean)
    Optional. This widens the persistent airspace-clearance families through a
    first narrow current-shape compound slice.
32. [CertifiedAtc/GreenfieldAirspaceDeliveredCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceDeliveredCurrentShape.lean)
    Optional. This packages the delivered current-shape airspace-clearance
    surface behind one source-level theorem boundary.
33. [CertifiedAtc/GreenfieldRadioCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRadioCurrentShape.lean)
    Optional. This closes the delivered current-shape radio package for
    `ContactFrequency` and `MonitorFrequency`.
34. [CertifiedAtc/GreenfieldBacktrackCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldBacktrackCurrentShape.lean)
    Optional. This closes the delivered current-shape `BacktrackRunway`
    package.
35. [CertifiedAtc/GreenfieldLineUpAndWaitCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldLineUpAndWaitCurrentShape.lean)
    Optional. This closes the delivered current-shape `LineUpAndWait`
    package.
36. [CertifiedAtc/GreenfieldTakeoffCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldTakeoffCurrentShape.lean)
    Optional. This closes the delivered current-shape `ClearedForTakeoff`
    package.
37. [CertifiedAtc/GreenfieldLandingCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldLandingCurrentShape.lean)
    Optional. This closes the delivered current-shape `ClearedToLand`
    package.
38. [CertifiedAtc/GreenfieldTouchAndGoCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldTouchAndGoCurrentShape.lean)
    Optional. This closes the delivered current-shape `ClearedTouchAndGo`
    package.
39. [CertifiedAtc/GreenfieldLowApproachCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldLowApproachCurrentShape.lean)
    Optional. This closes the delivered current-shape `ClearedLowApproach`
    package.
40. [CertifiedAtc/GreenfieldGoAroundCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldGoAroundCurrentShape.lean)
    Optional. This closes the delivered current-shape `GoAround` package.
41. [CertifiedAtc/GreenfieldRunwayDeliveredCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRunwayDeliveredCurrentShape.lean)
    Optional. This packages the delivered current-shape runway-operation
    family behind one source-level theorem boundary.
42. [CertifiedAtc/GreenfieldSetSquawkCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldSetSquawkCurrentShape.lean)
    Optional. This closes the delivered current-shape `SetSquawk` package.
43. [CertifiedAtc/GreenfieldConfirmSquawkCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldConfirmSquawkCurrentShape.lean)
    Optional. This closes the delivered current-shape `ConfirmSquawk`
    package.
44. [CertifiedAtc/GreenfieldSquawkIdentCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldSquawkIdentCurrentShape.lean)
    Optional. This closes the delivered current-shape `SquawkIdent` package.
45. [CertifiedAtc/GreenfieldSquawkStandbyCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldSquawkStandbyCurrentShape.lean)
    Optional. This closes the delivered current-shape `SquawkStandby`
    package.
46. [CertifiedAtc/GreenfieldSquawkNormalCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldSquawkNormalCurrentShape.lean)
    Optional. This closes the delivered current-shape `SquawkNormal`
    package.
47. [CertifiedAtc/GreenfieldStopSquawkCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldStopSquawkCurrentShape.lean)
    Optional. This closes the delivered current-shape `StopSquawk` package.
48. [CertifiedAtc/GreenfieldTransponderDeliveredCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldTransponderDeliveredCurrentShape.lean)
    Optional. This packages the delivered current-shape transponder family
    behind one source-level theorem boundary.
49. [CertifiedAtc/GreenfieldCommunicationsCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldCommunicationsCompound.lean)
    Optional. This widens the delivered radio and transponder families through
    a first narrow mixed current-shape compound slice.
50. [CertifiedAtc/GreenfieldCommunicationsDeliveredCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldCommunicationsDeliveredCurrentShape.lean)
    Optional. This packages the delivered phase-2 communications/surveillance
    surface behind one source-level theorem boundary.
51. [CertifiedAtc/GreenfieldRouteBearing.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearing.lean)
    Optional. This is the first route-bearing widening layer above the closed
    scoped programme: truthful resolved semantics for `ClearedTo`, `HoldAt`,
    `ClearedApproach`, and `JoinCircuit`.
52. [CertifiedAtc/GreenfieldRouteBearingAdmission.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingAdmission.lean)
    Optional. This is the first current-shape route-bearing admission layer:
    authority-gated admission, resolved-clearance existence, and packaged
    current-shape issuance for the full bridged Phase A surface.
53. [CertifiedAtc/GreenfieldRouteBearingCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingCompound.lean)
    Optional. This widens the current-shape route-bearing surface from
    single-step clearances to one leading Phase A route-bearing step plus
    immediate adjunct tails.
54. [CertifiedAtc/GreenfieldRouteBearingLifecycle.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingLifecycle.lean)
    Optional. This closes the current-shape lifecycle behavior for the widened
    route-bearing surface: what completes, what stays active, and what goes
    terminal through reconciliation.
55. [CertifiedAtc/GreenfieldRouteBearingSupersession.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingSupersession.lean)
    Optional. This closes the first route-bearing supersession consequences on
    the current greenfield engine: partial frequency supersession, full
    `GoAround` supersession, and the currently modeled `HoldAt` consequence.
56. [CertifiedAtc/GreenfieldRouteBearingCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteBearingCurrentShape.lean)
    Optional. This packages the whole current-shape Phase A route-bearing
    surface behind one source-level theorem boundary.
57. [CertifiedAtc/BridgeableRouteBearingIssuance.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/BridgeableRouteBearingIssuance.lean)
    Optional. This is the first widened issuing layer for the route-bearing
    track: theorem-bearing legacy-bridge issuance for `ClearedApproach` plus
    legacy-supported `JoinCircuit`.
58. [CertifiedAtc/ScopedGreenfield.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedGreenfield.lean)
    Optional. This is the scoped greenfield theorem package for
    `Safety-complete (N₀)`: scoped authority mapping, no-partial-issuance for
    surface compounds, conditional surface-envelope normalization, and the
    reachability wrapper into the resolved execution layer.
59. [CertifiedAtc/ScopedIssuance.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedIssuance.lean)
    Optional. This is the final scoped issuing layer for `Safety-complete (N₀)`:
    the theorem-bearing bridge into the older atomic certified path, plus the
    routing/instantiation/coverage/authority/non-bypass/issuance package.
60. [CertifiedAtc/ScopedSafety.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSafety.lean)
    Optional. This is the reachable-state safety layer above the scoped
    issuing boundary: state-preservation for nominal/runway/surface/air/interface
    invariants plus issued-step separation soundness.
61. [CertifiedAtc/ScopedModes.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedModes.lean)
    Optional. This is the scoped full-brief mode layer: assumption assessment,
    strongest-justified fallback, nominal guarantee withdrawal, concrete
    fallback vocabulary, and reachable mode-aware preservation.
62. [CertifiedAtc/JointActs.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/JointActs.lean)
    Optional. This is a narrow orchestration milestone module.
63. [CertifiedAtc/GreenfieldRunwayCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRunwayCompound.lean)
    Optional. This widens the delivered runway-operation family through a
    first narrow current-shape compound slice.
64. [CertifiedAtc/GreenfieldRunwayExpandedCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRunwayExpandedCurrentShape.lean)
    Optional. This packages the broadened current-shape runway family behind
    one source-level reachable-admission theorem boundary.
65. [CertifiedAtc/GreenfieldAirspaceExpandedCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceExpandedCompound.lean)
    Optional. This widens the current airspace-clearance family by adding the
    missing narrow compound slice for `RemainOutsideControlledAirspace`.
66. [CertifiedAtc/GreenfieldAirspaceExpandedCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceExpandedCurrentShape.lean)
    Optional. This packages the broadened current-shape airspace family behind
    one source-level theorem boundary.
67. [CertifiedAtc/GreenfieldAirspaceWorldBackedCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCurrentShape.lean)
    Optional. This adds the first world-backed airspace layer above the
    current-shape airspace package: concrete airspace-volume-backed resolution
    facts, source-level admission and authority-gated issuance, and the first
    theorem-bearing inside/outside plus entry/exit observation semantics.
68. [CertifiedAtc/GreenfieldAirspaceWorldBackedCompound.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCompound.lean)
    Optional. This widens that world-backed airspace layer through a first
    narrow compound slice over immediate adjunct tails.
69. [CertifiedAtc/GreenfieldAirspaceWorldBackedDeliveredCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedDeliveredCurrentShape.lean)
    Optional. This packages the delivered world-backed airspace surface behind
    one source-level theorem boundary.

## What Each Module Owns

- `Core`
  Shared vocabulary, base identifiers, atomic command language, and common structures.
- `CommandCatalog`
  Static command classes and plan-template routing contract.
- `RunwayKernel`
  Local runway commitment checker and local theorem.
- `SurfaceKernel`
  Local surface graph checker and local theorem.
- `AirKernel`
  Concrete local air-path checker over guarded transitions and altitude bands.
- `SeparationChecker`
  Concrete local pairwise separation checker with a local soundness theorem.
- `ScopedSeparation`
  Scoped separation theorem package for `Safety-complete (N₀)`: exact command
  classification, neutrality, peer coverage,
  `ScopedSeparationBoundarySufficiencyTheorem`, and
  `ScopedViableSepTheorem` for the current shortest-path surface.
- `SeparationChecker`
  Also now includes `viable_sep_of_capableApproval_equivIssuedScenario`, the
  local continue-capable successor theorem consumed by
  `ScopedIssuedScenarioViableSepTheorem`.
- `ScopedExtraction`
  Scoped extraction boundary for `Safety-complete (N₀)`: proof-side source
  world, deterministic extraction into `ClearanceCompileView` and certifier
  views, no-invented-id theorems, scoped reference preservation, operational
  bridge facts, and authority/lifecycle preservation.
- `RouteBearingExtraction`
  First widened procedure-bearing extraction layer above `ScopedExtraction`:
  widened source-world data for route-bearing entities, procedure-reference
  preservation into `ClearanceCompileView`, and compile-success theorems for
  compile-ready widened instructions, including supported-limit `ClearedTo`.
  `ClearedApproach` source kinds remain aligned to closed greenfield
  `ApproachType` values and are bridged to legacy strings only at
  compile-view emission.
- `RouteBearingResolutionBridge`
  First theorem-bearing bridge from the widened procedure-bearing extraction
  world into the current resolved execution world for `ClearedTo`, published
  `HoldAt`, and non-circling `ClearedApproach`.
- `Interfaces`
  Optional orchestration/composition layer over the existing atomic command path.
- `ClearanceEnvelope`
  Legacy greenfield staging layer: its own instruction surface, explicit frontier shape, and partial compiler back into the atomic certified path.
- `GreenfieldModel`
  Current-shape greenfield boundary aligned to the Kotlin model:
  `ClearanceContent.Single | Compound(steps, completedSteps)`,
  envelope-level conditions, lifecycle timing/categories, explicit `UniqueSet`
  state for set-like lifecycle fields, and frontier selection over the mixed
  step list.
- `GreenfieldLifecycle`
  Abstract lifecycle layer over `GreenfieldModel`: managed clearances, suppressed domains, admission, supersession, conditional activation, and completion advancement over abstract satisfied-step indices.
- `GreenfieldResolved`
  Current-shape resolved execution boundary aligned to Kotlin `ResolvedStep` / `ResolvedClearance`, with resolved points, runway facts, radio roles, and circuit joins.
- `GreenfieldResolution`
  Proof-side world-to-resolved relation that justifies resolved payloads from world/state facts instead of treating them as hand-assembled data.
  It now also includes a finite `ConcreteResolutionWorld` bridge for list-backed
  proof data, so the layer can be exercised concretely without inventing a full
  extracted runtime.
- `GreenfieldCompletion`
  Structured completion-observation layer over `GreenfieldResolved`: evaluates proof-side observations against resolved step payloads while keeping unsupported families explicit.
- `GreenfieldExecution`
  Resolved active-clearance layer over `GreenfieldResolved` and `GreenfieldCompletion`: managed resolved clearances, resolved completion, supersession bridging, and active-set reconciliation.
  The current theorem surface also makes the unique-clearance-id assumption
  explicit where exact resolved-set invariants depend on id-based reattachment.
  It now also includes first admission/reconciliation invariants above that
  boundary, not just helper lemmas about individual transitions.
  Activation is also status-gated here now, matching Kotlin: superseded
  conditionals cannot be reactivated later in the same pass.
  Reconciliation now also matches Kotlin more closely at the terminal-clearance
  boundary: terminal results are taken directly from the final working set
  rather than from a separate terminal accumulator.
- `GreenfieldReachability`
  Reachable active-set layer above `GreenfieldExecution`: packages fresh
  admission and reconciliation preservation into a reusable `ReachableResolvedSet`
  boundary and exposes well-formedness as a derived invariant.
- `GreenfieldPlainCurrentShape`
  Reusable current-shape helper for single-step greenfield instructions that
  resolve as plain steps and admit directly into the reachable resolved set.
- `GreenfieldContinueApproach`
  Small current-shape closure for single-step `ContinueApproach`: source-level
  issuance, active lifecycle, and explicit `GoAround` supersession.
- `GreenfieldContinueApproachCompound`
  Narrow current-shape compound widening for `ContinueApproach`: one leading
  `ContinueApproach` plus immediate adjuncts, with explicit active-state and
  supersession consequences.
- `GreenfieldSourceDomainPersistentPlain`
  Shared helper for the metadata-domain-less persistent families whose runtime
  domain is supplied by the source clearance.
- `GreenfieldExtendDownwind`
  Small current-shape closure for single-step `ExtendDownwind`: source-level
  issuance, active lifecycle, and explicit proof of the present
  persistent-only compound consequence.
- `GreenfieldExtendDownwindCompound`
  Narrow current-shape compound widening for `ExtendDownwind`.
- `GreenfieldOrbit`
  Small current-shape closure for single-step `Orbit`: source-level issuance,
  active lifecycle, and the same explicit persistent-only compound
  consequence.
- `GreenfieldOrbitCompound`
  Narrow current-shape compound widening for `Orbit`.
- `GreenfieldRouteAdjacentCurrentShape`
  Source-level current-shape packaging for the delivered Phase B
  route-adjacent surface.
- `GreenfieldRouteAdjacentAuthority`
  Current-shape authority closure for the delivered Phase B route-adjacent
  surface: conservative type-level grant mapping for `ContinueApproach`,
  `ExtendDownwind`, and `Orbit`, plus authority-gated issuance for the
  delivered single-step and narrow-compound slices.
- `GreenfieldAirspaceCurrentShape`
  Small current-shape single-step closure for the current Kotlin airspace-
  clearance family: `RemainOutsideControlledAirspace`,
  `ClearedToEnterControlZone`, and `SpecialVfrClearance`.
- `GreenfieldAirspaceCompound`
  Narrow current-shape compound widening for the persistent airspace-
  clearance families: `ClearedToEnterControlZone` and `SpecialVfrClearance`.
- `GreenfieldAirspaceDeliveredCurrentShape`
  Source-level current-shape packaging for the delivered airspace-clearance
  surface.
- `GreenfieldAirspaceExpandedCompound`
  The phase-4 widening increment that adds the missing
  `RemainOutsideControlledAirspace` narrow compound slice.
- `GreenfieldAirspaceExpandedCurrentShape`
  Source-level current-shape packaging for the broadened airspace-clearance
  surface.
- `GreenfieldAirspaceWorldBackedCurrentShape`
  First world-backed airspace layer above the broadened current-shape package:
  concrete `AirspaceVolume` membership, resolved airspace payloads, source-
  level admission/authorized issuance, and first theorem-bearing
  inside/outside plus entry/exit observation semantics.
- `GreenfieldAirspaceWorldBackedCompound`
  First narrow compound layer above the world-backed airspace boundary:
  one leading world-backed airspace primary plus immediate adjunct tails.
- `GreenfieldAirspaceWorldBackedDeliveredCurrentShape`
  Source-level packaging for the delivered world-backed airspace surface.
- `GreenfieldRadioCurrentShape`
  Source-level current-shape packaging for the delivered radio family:
  `ContactFrequency` and `MonitorFrequency`.
- `GreenfieldCommunicationsCompound`
  First narrow mixed current-shape compound layer for the delivered
  communications/surveillance families:
  one leading radio/transponder instruction plus immediate tails from those
  same delivered families.
- `GreenfieldCommunicationsDeliveredCurrentShape`
  Source-level current-shape packaging for the delivered phase-2
  communications/surveillance surface.
- `GreenfieldBacktrackCurrentShape`
  Source-level current-shape packaging for the delivered `BacktrackRunway`
  slice.
- `GreenfieldLineUpAndWaitCurrentShape`
  Source-level current-shape packaging for the delivered `LineUpAndWait`
  slice.
- `GreenfieldTakeoffCurrentShape`
  Source-level current-shape packaging for the delivered
  `ClearedForTakeoff` slice.
- `GreenfieldLandingCurrentShape`
  Source-level current-shape packaging for the delivered `ClearedToLand`
  slice.
- `GreenfieldTouchAndGoCurrentShape`
  Source-level current-shape packaging for the delivered
  `ClearedTouchAndGo` slice.
- `GreenfieldLowApproachCurrentShape`
  Source-level current-shape packaging for the delivered
  `ClearedLowApproach` slice.
- `GreenfieldGoAroundCurrentShape`
  Source-level current-shape packaging for the delivered `GoAround` slice.
- `GreenfieldRunwayDeliveredCurrentShape`
  Source-level current-shape packaging for the delivered runway-operation
  family.
- `GreenfieldRunwayCompound`
  The phase-3 widening increment that adds the first narrow current-shape
  compound slice for the delivered runway-operation family.
- `GreenfieldRunwayExpandedCurrentShape`
  Source-level reachable-admission packaging for the broadened current-shape
  runway family.
- `GreenfieldSetSquawkCurrentShape`
  Source-level current-shape packaging for the delivered `SetSquawk` slice.
- `GreenfieldConfirmSquawkCurrentShape`
  Source-level current-shape packaging for the delivered `ConfirmSquawk`
  slice.
- `GreenfieldSquawkIdentCurrentShape`
  Source-level current-shape packaging for the delivered `SquawkIdent`
  slice.
- `GreenfieldSquawkStandbyCurrentShape`
  Source-level current-shape packaging for the delivered `SquawkStandby`
  slice.
- `GreenfieldSquawkNormalCurrentShape`
  Source-level current-shape packaging for the delivered `SquawkNormal`
  slice.
- `GreenfieldStopSquawkCurrentShape`
  Source-level current-shape packaging for the delivered `StopSquawk`
  slice.
- `GreenfieldTransponderDeliveredCurrentShape`
  Source-level current-shape packaging for the delivered transponder family.
- `GreenfieldRouteBearing`
  First route-bearing widening layer above the closed scoped programme:
  route-bearing-core classification, proof that all Phase A families need
  specific resolution, resolved-side authority mapping where a concrete entity
  is identified, and truthful resolved execution/completion facts for
  `ClearedTo`, `HoldAt`, `ClearedApproach`, and `JoinCircuit`.
- `GreenfieldRouteBearingAdmission`
  First current-shape route-bearing admission layer: current compile-view
  authority gating plus resolved-clearance existence, admission soundness, and
  a packaged current-shape issuance theorem for the full bridged Phase A
  surface.
- `GreenfieldRouteBearingCompound`
  First current-shape route-bearing compound layer:
  one leading Phase A route-bearing step plus immediate adjunct tails,
  whole-clearance resolution/admission/issuance, and explicit preservation of
  non-completing `ClearedApproach` semantics.
- `GreenfieldRouteBearingLifecycle`
  First current-shape route-bearing lifecycle layer:
  theorem-bearing completion / active-state behavior for the widened
  route-bearing surface through reconciliation.
- `GreenfieldRouteBearingSupersession`
  First current-shape route-bearing supersession layer:
  theorem-bearing partial frequency supersession, full `GoAround`
  supersession, and explicit proof of the current modeled `HoldAt`
  consequence after frequency supersession.
- `GreenfieldRouteBearingCurrentShape`
  Current-shape closure wrapper for the Phase A route-bearing surface:
  one source-level theorem boundary over the already-proved single-step,
  compound, lifecycle, and supersession layers.
- `BridgeableRouteBearingIssuance`
  First widened issuing layer for the route-bearing track: theorem-bearing
  legacy-bridge issuance for `ClearedApproach` and for `JoinCircuit` only when
  its greenfield join type maps back into the legacy atomic subset.
- `ScopedGreenfield`
  Scoped greenfield theorem package for `Safety-complete (N₀)`: exact scoped
  instruction surface, scoped authority mapping, movement-envelope frontier
  shape, conditional surface-compound normalization, and the resolved
  reachability wrapper used by the final issuing-layer theorem.
- `ScopedIssuance`
  Final scoped issuing layer for `Safety-complete (N₀)`: honest greenfield to
  atomic bridging for the current scoped surface, plus routing completeness,
  plan-instantiation correctness, peer coverage, compatibility narrowness,
  authority gating, non-bypass, and issuance soundness.
- `ScopedSafety`
  Reachable-state safety layer above `ScopedIssuance`: scoped world/state
  well-formedness, component-preserving approval collection, issuance
  preservation of nominal/runway/surface/air/interface invariants, reachable
  issued-state semantics, and issued-step separation soundness.
- `ScopedModes`
  Scoped full-brief mode layer: abstract assessment output, strongest-justified
  regime selection, conservative fallback commands, mode-aware issuance, and
  reachable full-brief guarantees.
- `JointActs`
  Narrow orchestration milestone theorem for the first joint-act slice.

## Current Lean Split

There are now twenty-six distinct Lean layers above the local certifiers:

1. `ClearanceEnvelope.lean`
   The older proof/compiler surface that still bridges into the atomic command path.
2. `ScopedExtraction.lean`
   The scoped extraction boundary from proof-side world facts into
   `ClearanceCompileView`, certifier-local views, and issuer-authority facts.
3. `RouteBearingExtraction.lean`
   The first widened procedure-bearing extraction layer above
   `ScopedExtraction`: widened source-world data, route-bearing reference
   preservation, and compile-success theorems for compile-ready widened
   instructions.
4. `RouteBearingResolutionBridge.lean`
   The first theorem-bearing bridge from widened extracted route-bearing world
   data into the current resolved execution world for the bridgeable subset.
5. `GreenfieldModel.lean`
   The new Kotlin-aligned model surface that mirrors the runtime clearance shape directly.
6. `GreenfieldLifecycle.lean`
   The new abstract active-clearance engine over that model surface.
7. `GreenfieldResolved.lean`
   The new resolved-step execution boundary aligned to Kotlin compiled clearances.
8. `GreenfieldResolution.lean`
   The proof-side world/state relation that derives valid resolved clearances.
9. `GreenfieldPlainCurrentShape.lean`
   Reusable current-shape helper for single-step greenfield instructions that
   stay plain at the resolved boundary.
10. `GreenfieldContinueApproach.lean`
   Small current-shape single-step closure for `ContinueApproach`.
11. `GreenfieldContinueApproachCompound.lean`
   Small current-shape compound closure for `ContinueApproach`.
12. `GreenfieldSourceDomainPersistentPlain.lean`
   Shared helper for metadata-domain-less persistent current-shape families.
13. `GreenfieldExtendDownwind.lean`
   Small current-shape single-step closure for `ExtendDownwind`.
14. `GreenfieldExtendDownwindCompound.lean`
   Small current-shape compound closure for `ExtendDownwind`.
15. `GreenfieldOrbit.lean`
   Small current-shape single-step closure for `Orbit`.
16. `GreenfieldOrbitCompound.lean`
   Small current-shape compound closure for `Orbit`.
17. `GreenfieldRouteAdjacentCurrentShape.lean`
   Source-level current-shape packaging for the delivered Phase B surface.
18. `GreenfieldRouteAdjacentAuthority.lean`
   Current-shape authority closure for the delivered Phase B route-adjacent surface.
19. `GreenfieldAirspaceCurrentShape.lean`
   Small current-shape single-step closure for the current Kotlin airspace-clearance family.
20. `GreenfieldAirspaceCompound.lean`
   Narrow current-shape compound closure for the persistent airspace-clearance families.
21. `GreenfieldAirspaceDeliveredCurrentShape.lean`
   Source-level current-shape packaging for the delivered airspace-clearance surface.
22. `GreenfieldAirspaceExpandedCompound.lean`
   The phase-4 widening increment that adds the missing
   `RemainOutsideControlledAirspace` narrow compound slice.
23. `GreenfieldAirspaceExpandedCurrentShape.lean`
   Source-level current-shape packaging for the broadened airspace-clearance
   surface.
24. `GreenfieldCompletion.lean`
   The structured observation contract that evaluates proof-side facts against resolved steps.
25. `GreenfieldExecution.lean`
   The resolved active-clearance layer that closes the loop from admitted clearances to completion and reconciliation.
26. `GreenfieldReachability.lean`
   The reachable active-set layer that packages execution preservation into a reusable invariant boundary.
27. `GreenfieldRouteBearing.lean`
   The first widened route-bearing layer above the closed scoped programme:
   truthful resolved semantics for `ClearedTo`, `HoldAt`,
   `ClearedApproach`, and `JoinCircuit`.
28. `GreenfieldRouteBearingAdmission.lean`
   The first current-shape route-bearing admission layer:
   authority-gated admission, resolved-clearance existence, and packaged
   current-shape issuance for the full bridged Phase A surface.
29. `GreenfieldRouteBearingCompound.lean`
   The first route-bearing compound layer above that admission boundary:
   one leading Phase A route-bearing step plus immediate adjunct tails,
   whole-clearance resolution/admission/issuance, and explicit non-completing
   `ClearedApproach` semantics.
30. `GreenfieldRouteBearingLifecycle.lean`
   The first current-shape route-bearing lifecycle layer:
   theorem-bearing completion, active-state, and terminal-state behavior for
   the widened route-bearing surface.
31. `GreenfieldRouteBearingSupersession.lean`
   The first current-shape route-bearing supersession layer:
   partial frequency supersession, full `GoAround` supersession, and the
   current modeled `HoldAt` consequence after frequency supersession.
32. `GreenfieldRouteBearingCurrentShape.lean`
   The current-shape closure wrapper for the Phase A route-bearing surface:
   one source-level theorem boundary over the admitted route-bearing singles
   and compounds.
33. `BridgeableRouteBearingIssuance.lean`
   The first widened issuing layer for the route-bearing track:
   theorem-bearing legacy-bridge issuance for `ClearedApproach` plus
   legacy-supported `JoinCircuit`.
34. `ScopedGreenfield.lean`
   The scoped `Safety-complete (N₀)` theorem package above the greenfield model
   and execution layers.
35. `ScopedIssuance.lean`
   The final scoped issuing layer above extraction and greenfield execution:
   bridge into the old certified path plus the Milestone 5 theorem package.
36. `ScopedSafety.lean`
   The reachable-state safety layer above the scoped issuing boundary:
   preserved nominal/kernel/interface invariants, reachable issued-state
   semantics, and issued-step separation soundness.
35. `ScopedModes.lean`
   The full-brief mode layer above scoped nominal safety: abstract assessment,
   strongest-justified fallback, conservative fallback command semantics, and
   reachable full-brief guarantees.

That split is intentional. The Kotlin/runtime model has moved to:

- typed entity/procedure references
- `steps + completedSteps`
- envelope-level conditional state
- lifecycle timing and completion categories

The old envelope module is still useful for the existing theorem work, but it is no longer the authoritative model shape.

## Immediate Use

Use `GreenfieldModel.lean` when you want to:

- reason about the current Kotlin clearance shape directly
- normalize conditional clearances at the envelope level
- talk about compound frontier selection over `completedSteps`
- prove lifecycle/helper lemmas without first translating into the older staging compiler surface

Use `GreenfieldLifecycle.lean` when you want to:

- reason about active-clearance set evolution without importing geometry
- study supersession and suppressed-domain semantics directly
- model condition-pending to active activation order
- talk about completion as abstract satisfied step indices before proving world-backed completion facts

Use `GreenfieldRouteBearing.lean` when you want to:

- reason honestly about the widened route-bearing core at the resolved boundary
- talk about `ClearedTo`, `HoldAt`, `ClearedApproach`, and `JoinCircuit`
  without pretending they already share one top-level issuance path
- distinguish between route-bearing families that already have resolved
  completion facts and `ClearedApproach`, which is intentionally resolved but
  non-completing in the current model

Use `RouteBearingResolutionBridge.lean` when you want to:

- connect widened extracted procedure-bearing world facts to the resolved
  execution world
- prove that Phase A route-bearing references resolve from extracted data
- avoid treating extraction lemmas and resolved semantics as an end-to-end
  theorem before the bridge exists

Use `GreenfieldRouteBearingAdmission.lean` when you want to:

- reason about single-step current-shape route-bearing issuance on the
  greenfield boundary
- prove authority-gated admission, resolved-clearance existence, and packaged
  issuance for the bridged Phase A surface

Use `GreenfieldRouteBearingCompound.lean` when you want to:

- reason about one-leading-route-bearing-step compounds with immediate adjunct
  tails
- prove whole-clearance resolution/admission/issuance for current-shape
  route-bearing compounds
- keep `ClearedApproach` explicitly non-completing while widening compound
  packaging

Use `GreenfieldRouteBearingLifecycle.lean` when you want to:

- prove what the current widened route-bearing clearances actually do through
  completion and reconciliation
- make single-step `HoldAt` persistence explicit
- show that compound `ClearedApproach` remains active even after adjunct
  completion
- show that route and circuit-join compounds go terminal when their resolved
  completion facts are observed

Use `GreenfieldRouteBearingSupersession.lean` when you want to:

- reason about route-bearing supersession on the current greenfield engine
- prove that frequency updates partially supersede mixed route/frequency
  compounds without destroying the route-bearing step
- prove that `GoAround` fully supersedes active approach compounds
- keep the current modeled `HoldAt` post-supersession behavior visible rather
  than implicit

Use `GreenfieldRouteBearingCurrentShape.lean` when you want to:

- talk about the whole current Phase A route-bearing greenfield surface at one
  source-level theorem boundary
- avoid re-dispatching manually between the single-step and compound route-
  bearing issuance theorems
- prove that a source route-bearing clearance in the currently supported
  surface admits into `ReachableResolvedSet`

Use `RouteBearingExtraction.lean` when you want to:

- reason about the first widened procedure-bearing extraction layer above the
  scoped nominal boundary
- prove that widened route-bearing references survive into
  `ClearanceCompileView`
- prove that compile-ready widened instructions, including supported-limit
  `ClearedTo`, actually compile through the extracted view
- keep extracted `ClearedApproach` kinds aligned to the closed greenfield
  `ApproachType` model while still targeting the legacy atomic compile view

Use `BridgeableRouteBearingIssuance.lean` when you want to:

- reason about the first honest widened issuing layer above the scoped claim
- prove issuance results for `ClearedApproach`
- prove issuance results for `JoinCircuit` only when the join type is still in
  the legacy atomic subset
- avoid over-claiming that `ClearedTo` or `HoldAt` already have a theorem-
  bearing legacy bridge

Use `ScopedExtraction.lean` when you want to:

- reason about the theorem-bearing extraction boundary for the scoped
  `Safety-complete (N₀)` surface
- connect proof-side runway, taxiway, role/frequency, and authority facts to
  `ClearanceCompileView`
- recover the certifier-local runway / surface / air views without appealing to
  prose-only extraction assumptions
- prove that in-scope extracted references remain available through the scoped
  clearance lifecycle

Use `GreenfieldResolved.lean` when you want to:

- reason about completion against resolved points/runways/roles instead of raw instructions
- mirror Kotlin `ResolvedStep` / `ResolvedClearance` shapes on the proof side
- talk about backtrack far-end points, resolved route limits, and resolved circuit joins explicitly

Use `GreenfieldResolution.lean` when you want to:

- justify resolved steps from proof-side world facts and current state
- connect world assumptions to `ResolvedClearance` without inventing a full extracted runtime
- prove compatibility and step-count facts about resolved clearances
- exercise that relation over a finite list-backed world via
  `ConcreteResolutionWorld.toResolutionWorld`

Use `GreenfieldCompletion.lean` when you want to:

- connect proof-side observations to resolved step completion
- model point arrival, runway transitions, circuit membership, radio handoff, altitude/speed, and transponder completion
- keep unsupported families explicit rather than silently approximating them

Use `GreenfieldExecution.lean` when you want to:

- reason about managed resolved clearances directly
- combine resolved completion with supersession and conditional activation
- stay aligned to the Kotlin active-clearance engine without dropping back to raw instruction semantics
- make explicit which execution properties only need lifecycle-view equality
  and which stronger ones additionally require unique clearance ids
- rely on the now-proved unique-id preservation path through admission,
  conditional activation, and full reconciliation

Use `GreenfieldReachability.lean` when you want to:

- reason about reachable active sets rather than restating fresh-id and compatibility side conditions
- recover `WellFormedResolvedSet` from reachability automatically
- bridge world-backed resolution facts into execution reachability via `ReachableResolvedSet.admit_of_resolved`

Use `ScopedIssuance.lean` when you want to:

- work at the final scoped issuing boundary for `Safety-complete (N₀)`
- bridge scoped greenfield instructions into the older atomic certified path
  without silently widening the claim
- use the top-layer theorem package for routing, plan instantiation, peer
  coverage, compatibility narrowness, authority gating, non-bypass, and
  issuance soundness

Use `ScopedSafety.lean` when you want to:

- work above the final scoped issuing boundary instead of restating local
  preservation lemmas by hand
- recover the scoped nominal/runway/surface/air/interface invariant package on
  reachable issued states
- connect issued scoped separation-certified acts to witness-backed peer
  coverage and separation soundness

Use `ScopedGreenfield.lean` when you want to:

- stay strictly inside the scoped `Safety-complete (N₀)` greenfield surface
- use the scoped greenfield authority mapping instead of re-deriving grants ad hoc
- rely on the no-partial-issuance theorem for compound surface envelopes
- connect scoped surface compounds to checked staging and resolved reachability
- avoid reopening Bucket C route-bearing semantics while building the final top layer

Use `ClearanceEnvelope.lean` when you need:

- the existing compiler path into the atomic certified stack
- the older frontier/sequencing theorem surface that already sits above that compiler
