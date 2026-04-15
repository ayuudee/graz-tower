# Agent Guide

This file is for follow-on AI agents working in `research/fm`.

## Mission

Treat `research/fm` as a standalone proof project.

Your job is not to speculate about the architecture again unless the Lean and
project docs force that conclusion. The architecture contract is already
substantially frozen.

The default success criterion is not "finish one giant whole-system theorem."
It is:

- make the local certifiers concrete and defensible
- make their ownership boundaries explicit
- put proof effort on the long-term greenfield boundary rather than on the
  older atomic command surface when those goals diverge
- only do orchestration-composition work when the user or product architecture
  actually needs a single issuing layer

## Start Here

Read in this order:

1. [README.md](/home/andrew/dev/projects/twr2/research/fm/README.md)
2. [PROJECT_STATUS.md](/home/andrew/dev/projects/twr2/research/fm/PROJECT_STATUS.md)
3. [parity_inventory.md](/home/andrew/dev/projects/twr2/research/fm/parity_inventory.md)
4. [refinement_inventory.md](/home/andrew/dev/projects/twr2/research/fm/refinement_inventory.md)
5. [greenfield_alignment.md](/home/andrew/dev/projects/twr2/research/fm/greenfield_alignment.md)
6. [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md)
7. [instruction_authority_contract.md](/home/andrew/dev/projects/twr2/research/fm/instruction_authority_contract.md)
8. [clearance_envelope_contract.md](/home/andrew/dev/projects/twr2/research/fm/clearance_envelope_contract.md)
9. [path-network-design.md](/home/andrew/dev/projects/twr2/docs/design/path-network-design.md)
10. [clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md)
11. [milestones.md](/home/andrew/dev/projects/twr2/research/fm/milestones.md)
12. [lean/README.md](/home/andrew/dev/projects/twr2/research/fm/lean/README.md)

Then open the specific Lean module for the phase you are changing.

## Current Truths

- runway kernel is concrete and proved
- surface kernel is concrete and proved
- air kernel is concrete and proved
- separation checker is concrete and proved locally
- there is a partial optional orchestration slice with proved peer coverage for
  some supported runway/surface/air commands, and the surface kernel is now
  wired through that slice for `HoldShortOf`, a conservative `TaxiTo` path,
  a concrete joint `CrossRunway` path, and a concrete joint
  `LineUpAndWait` path
- the target app/proof boundary is now anchored to
  [path-network-design.md](/home/andrew/dev/projects/twr2/docs/design/path-network-design.md)
  and
  [clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md),
  with the current repo's Kotlin protocol/world/clearance types serving as a
  staging mirror rather than the committed implementation target
- the structural extraction contract for that boundary is now recorded in
  [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md),
  including explicit authority payload requirements
- the narrowed instruction-level authority mapping is now recorded in
  [instruction_authority_contract.md](/home/andrew/dev/projects/twr2/research/fm/instruction_authority_contract.md),
  and the Lean side now has a conservative authorization checker for that
  resolved subset
- [GreenfieldModel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldModel.lean)
  is the current-shape proof-side greenfield model, while
  [ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
  remains the legacy bridge into the atomic certified path
- a partial greenfield compiler path now exists:
  `compileClearanceCommand`, `compile_clearance_instruction`, and
  `compile_frontier` compile entity-referenced instructions and current
  frontier steps back into the existing atomic certified path
- the envelope timing split is now explicit in Lean as
  `InstructionStepTiming = sequential | immediate | standalone`, and
  [clearance_envelope_contract.md](/home/andrew/dev/projects/twr2/research/fm/clearance_envelope_contract.md)
  records the narrower theorem surface that follows from that split
- the scoped surface is now both `Safety-complete (N₀)` and `Full-brief
  complete`
- the first route-bearing widening increment is now real:
  `GreenfieldRouteBearing.lean` gives truthful resolved semantics for
  `ClearedTo`, `HoldAt`, `ClearedApproach`, and `JoinCircuit`, and
  `RouteBearingExtraction.lean` now gives the first theorem-bearing
  procedure-bearing extraction increment for that track, with
  `ClearedApproach` kinds still constrained to the closed greenfield
  `ApproachType` model, while
  `RouteBearingResolutionBridge.lean`,
  `GreenfieldRouteBearingAdmission.lean`, and
  `GreenfieldRouteBearingCompound.lean`,
  `GreenfieldRouteBearingLifecycle.lean`, and
  `GreenfieldRouteBearingSupersession.lean` now carry that same Phase A
  surface through the current graph-backed published-procedure
  resolution/admission/issuance/execution path, while
  `BridgeableRouteBearingIssuance.lean` widens theorem-bearing legacy
  issuance only for `ClearedApproach` plus legacy-supported `JoinCircuit`;
  `GreenfieldRouteBearingCurrentShape.lean` now packages that whole current-
  shape Phase A surface behind one source-level theorem boundary
- the remaining FM work is now optional widening, not milestone-critical
  closure
- the broader ground / surface movement branch is now also closed on the
  current graph-backed model:
  `GroundMovementResolutionBridge.lean`,
  `GreenfieldGroundMovementCurrentShape.lean`, and
  `GreenfieldGroundMovementDeliveredCurrentShape.lean` now give a world-backed
  boundary for `TaxiTo`, `HoldShortOf`, and `CrossRunway`, a current-shape
  boundary for `HoldPosition`, and a first narrow sequential ground compound
  layer on the same delivered branch
- the next greenfield widening seam is no longer just Phase A route-bearing:
  the semantic-alignment gate for `JoinCircuit` / `ExtendDownwind` / `Orbit`
  is now closed, and `ContinueApproach`, `ExtendDownwind`, and `Orbit` each
  now have a small current-shape theorem slice, with all three now widened
  through a first narrow compound package and the shared null-domain /
  source-domain-supplied helper story frozen for `ExtendDownwind` / `Orbit`;
  `GreenfieldRouteAdjacentCurrentShape.lean` now packages that delivered
  Phase B set behind one source-level current-shape theorem boundary, and
  `GreenfieldRouteAdjacentAuthority.lean` now closes the current-shape
  authority layer for the same delivered Phase B families
- the current Kotlin airspace-clearance family now also has a first honest
  greenfield widening slice in
  `GreenfieldAirspaceCurrentShape.lean`:
  `RemainOutsideControlledAirspace`,
  `ClearedToEnterControlZone`, and `SpecialVfrClearance` now exist on the
  Lean boundary with Kotlin-aligned metadata, single-step current-shape
  issuance, explicit current lifecycle/supersession regressions, and
  conservative `airspaceVolume / airspaceTransit` authority
  `GreenfieldAirspaceCompound.lean` now widens that same family one step
  further:
  `ClearedToEnterControlZone` and `SpecialVfrClearance` now have a first
  narrow current-shape compound slice over immediate adjuncts, and
  `GreenfieldAirspaceDeliveredCurrentShape.lean` now packages the delivered
  airspace family behind one source-level current-shape theorem boundary
  `GreenfieldAirspaceWorldBackedCurrentShape.lean` now adds the first
  world-backed airspace layer above that package:
  concrete proof-side `AirspaceVolume` membership, resolved airspace
  payloads, source-level admission and authority-gated issuance, explicit
  inside-volume plus entry-transition violation observation for
  `RemainOutsideControlledAirspace`, and explicit persistence for
  `ClearedToEnterControlZone` and `SpecialVfrClearance`
  `GreenfieldAirspaceWorldBackedCompound.lean` and
  `GreenfieldAirspaceWorldBackedDeliveredCurrentShape.lean` now widen that
  same world-backed airspace layer through a first narrow compound slice and
  package it behind one source-level theorem boundary
  `GreenfieldRadioCurrentShape.lean` now also closes a small current-shape
  radio package for `ContactFrequency` and `MonitorFrequency`, including
  conservative radio-role authority plus explicit/implicit frequency
  resolution
  `GreenfieldBacktrackCurrentShape.lean` now also closes a small current-shape
  `BacktrackRunway` package with runway/backtrack authority and resolved
  far-end-point completion
  `GreenfieldLineUpAndWaitCurrentShape.lean` now also closes a small current-
  shape `LineUpAndWait` package with runway/line-up authority plus explicit
  active and conditional lifecycle behavior
  `GreenfieldTakeoffCurrentShape.lean` now also closes a small current-shape
  `ClearedForTakeoff` package with runway/takeoff authority, airborne
  completion, and explicit conditional lifecycle behavior
  `GreenfieldLandingCurrentShape.lean` now also closes a small current-shape
  `ClearedToLand` package with runway/land authority, runway-vacation
  completion, and explicit conditional lifecycle behavior
  `GreenfieldTouchAndGoCurrentShape.lean` now also closes a small current-
  shape `ClearedTouchAndGo` package with runway/touch-and-go authority,
  runway-transition airborne completion, and explicit conditional lifecycle
  behavior
  `GreenfieldLowApproachCurrentShape.lean` now also closes a small current-
  shape `ClearedLowApproach` package with runway/low-approach authority,
  runway-transition-and-exit completion, and explicit conditional lifecycle
  behavior
  `GreenfieldGoAroundCurrentShape.lean` now also closes a small current-shape
  `GoAround` package with runway/go-around authority and explicit active
  lifecycle behavior
  `GreenfieldSetSquawkCurrentShape.lean` now also closes a small current-
  shape `SetSquawk` package with radio-role/squawk authority and explicit
  on-activation completion
  `GreenfieldConfirmSquawkCurrentShape.lean` now also closes a small current-
  shape `ConfirmSquawk` package with radio-role/squawk authority and
  explicit matching-code completion
  `GreenfieldSquawkIdentCurrentShape.lean`,
  `GreenfieldSquawkStandbyCurrentShape.lean`,
  `GreenfieldSquawkNormalCurrentShape.lean`, and
  `GreenfieldStopSquawkCurrentShape.lean` now close the remaining delivered
  current-shape transponder slices
  `GreenfieldTransponderDeliveredCurrentShape.lean` now packages the
  delivered current-shape transponder family behind one source-level theorem
  boundary
  `GreenfieldCommunicationsCompound.lean` now closes the first narrow
  current-shape communications/surveillance compound slice over those
  delivered radio and transponder families, and
  `GreenfieldCommunicationsDeliveredCurrentShape.lean` now packages that
  delivered phase-2 communications/surveillance surface behind one source-
  level theorem boundary
  `GreenfieldRunwayDeliveredCurrentShape.lean` now packages the delivered
  current-shape runway-operation family behind one source-level theorem
  boundary
  `GreenfieldRunwayCompound.lean` now widens that delivered runway-operation
  family through a first narrow compound layer, and
  `GreenfieldRunwayExpandedCurrentShape.lean` now packages the broadened
  current-shape runway family behind one source-level reachable-admission
  boundary
  `GreenfieldAirspaceExpandedCompound.lean` now closes the missing narrow
  `RemainOutsideControlledAirspace` compound slice, and
  `GreenfieldAirspaceExpandedCurrentShape.lean` now packages the broadened
  current-shape airspace family behind one source-level theorem boundary
  phase 1 is now closed under the frozen widening rule:
  do not reopen families in that bucket unless runtime semantics themselves
  change; that bucket now explicitly includes the delivered route/vector-
  control subset and the delivered air-modifier/admin subset, with
  `TurnByDegrees` kept intentionally open
  phase 2 is now also closed under that same frozen widening rule:
  do not reopen the delivered communications/surveillance families unless
  their runtime semantics themselves change
  phase 3 is now also closed under that same frozen widening rule:
  do not reopen the delivered runway family unless runway runtime semantics
  themselves change
  phase 4 is now also closed under that same frozen widening rule:
  do not reopen the current airspace-clearance family unless those runtime
  semantics themselves change
  the new world-backed airspace layer is a separate widening branch:
  it is no longer accurate to describe airspace as plain/current-shape only,
  and it is no longer accurate to describe it as world-backed single-step
  only; the current graph-backed point-set + transition model is now closed
  through route/airspace interaction, entry/exit observation, and
  exit-or-landing lifecycle for the permission pair, but richer
  geometric/polygonal semantics, denser route/airspace interaction
  semantics, and broader world-backed lifecycle semantics still remain open

Do not talk as if broad route-bearing or richer operational mode semantics are
proved just because the scoped surface is closed. The current route-bearing
widening is real but partial. Do not talk as if `ClearedTo` and `HoldAt`
already have a theorem-bearing legacy atomic bridge just because they now have
truthful resolved semantics and current-shape greenfield issuance. Also do not
talk as if the optional full-orchestration theorem is the default goal unless
the user says so.

## Build Command

From the repo root:

```bash
nix-shell -p lean4 --run 'cd research/fm/lean && lake build'
```

Single-module builds are preferred while iterating.

## Working Rules

- Lean files are authoritative.
- The root docs in `research/fm/` must be updated when proof status changes.
- Keep local logic local.
- Do not widen the older atomic command interface by default if the same work
  should really happen in the greenfield clearance/compiler layer.
- When the current repo's Kotlin scaffolding diverges from `docs/design/`,
  prefer `docs/design/` for future-project intent and use `research/fm` to
  record the narrower presently proved claim.

Meaning:

- do not push runway legality into orchestration
- do not push surface graph legality into orchestration
- do not push future air transition legality into orchestration
- orchestration should compose local approvals, not replace them
- if unsure whether to spend effort on a local kernel theorem or an
  orchestration theorem, prefer the local kernel theorem unless the user
  explicitly wants single-issuer integration work
- when closing a phase, add a short product-facing note that says what narrower
  claim is now justified, what is still not justified, and what the next proof
  increment is meant to unlock

## How To Extend The Project Cleanly

For a new local kernel:

1. define the local vocabulary and state
2. define the checker
3. define the local invariant
4. prove certificate soundness and invariant preservation
5. only then add a concrete validation instance

For orchestration:

1. first ask whether orchestration is actually needed for the current task
2. widen static routing first
3. define plan instantiation for the new slice
4. connect the required local approvals
5. keep compatibility narrow
6. prove non-bypass for the widened slice

## What To Avoid

- reopening phase 1 because a later phase is hard
- introducing one giant global checker
- hiding proof debt in prose without reflecting it in Lean
- landing proof changes without updating `PROJECT_STATUS.md` and
  `milestones.md`

## Immediate Next Move

Unless the user says otherwise, the next default task in `research/fm` is:

- keep the scoped surface stable and honest
- use the frozen parity / refinement / drift-control inventory in
  [parity_inventory.md](/home/andrew/dev/projects/twr2/research/fm/parity_inventory.md)
  and the enforcing branch map in
  [refinement_inventory.md](/home/andrew/dev/projects/twr2/research/fm/refinement_inventory.md)
  as guardrails before opening a new branch:
  if metadata, authority, completion, supersession, or family status changes,
  update the inventories and the central registry in
  [GreenfieldDeliveredRefinement.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean)
- remember what is already closed and therefore not the default “next” task:
  the current graph-backed published-procedure route-bearing branch,
  the delivered Phase B route-adjacent branch,
  the current graph-backed point-set + transition airspace branch,
  the delivered communications/surveillance surface,
  the broadened current-shape runway surface,
  the current graph-backed broader ground / surface branch,
  and the delivered-branch refinement / drift-control branch
- the next widening branch is now a deliberate choice, not an automatic
  default:
  prefer one branch at a time between broader
  communications/surveillance semantics and the next genuinely semantic
  branch beyond the current models
- after that branch, choose one widening direction at a time:
  broader communications/surveillance, deeper route-bearing beyond the
  current graph-backed published-procedure model, richer airspace beyond the
  current graph-backed point-set + transition model, unresolved
  heading-progress/vector semantics, or richer operational mode semantics
- widen extraction, greenfield semantics, and issuing-layer theorems together
  rather than widening only one layer in isolation
