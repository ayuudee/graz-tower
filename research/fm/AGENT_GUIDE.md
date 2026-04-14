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
3. [greenfield_alignment.md](/home/andrew/dev/projects/twr2/research/fm/greenfield_alignment.md)
4. [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md)
5. [instruction_authority_contract.md](/home/andrew/dev/projects/twr2/research/fm/instruction_authority_contract.md)
6. [clearance_envelope_contract.md](/home/andrew/dev/projects/twr2/research/fm/clearance_envelope_contract.md)
7. [path-network-design.md](/home/andrew/dev/projects/twr2/docs/design/path-network-design.md)
8. [clearance-model-design.md](/home/andrew/dev/projects/twr2/docs/design/clearance-model-design.md)
9. [milestones.md](/home/andrew/dev/projects/twr2/research/fm/milestones.md)
10. [lean/README.md](/home/andrew/dev/projects/twr2/research/fm/lean/README.md)

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
  surface through the current-shape greenfield
  resolution/admission/issuance/execution path, while
  `BridgeableRouteBearingIssuance.lean` widens theorem-bearing legacy
  issuance only for `ClearedApproach` plus legacy-supported `JoinCircuit`;
  `GreenfieldRouteBearingCurrentShape.lean` now packages that whole current-
  shape Phase A surface behind one source-level theorem boundary
- the remaining FM work is now optional widening, not milestone-critical
  closure
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
  `GreenfieldTransponderDeliveredCurrentShape.lean` now packages the
  delivered current-shape transponder family behind one source-level theorem
  boundary
  `GreenfieldRunwayDeliveredCurrentShape.lean` now packages the delivered
  current-shape runway-operation family behind one source-level theorem
  boundary

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
- choose one widening direction at a time:
  route-bearing proof scope or richer operational mode semantics
- if staying on the route-bearing track, start from the delivered first slice
  rather than redoing it:
  resolved semantics, the first procedure-bearing extraction increment, the
  extraction-to-resolution bridge, the first greenfield admission /
  current-shape issuance layer, the first route-bearing compound issuance
  layer, and the first current-shape lifecycle / supersession layers are
  already in place for the full Phase A route-bearing surface, and
  `GreenfieldRouteBearingCurrentShape.lean` now packages that surface at one
  source-level theorem boundary;
  `ContinueApproach`, `ExtendDownwind`, and `Orbit` now also have small
  current-shape theorem slices on top of that surface, and all three are now
  widened through a narrow compound slice, with the delivered Phase B set now
  also packaged behind one source-level current-shape theorem boundary and
  closed through a current-shape authority layer;
  the current Kotlin airspace-clearance family also now has a first
  single-step current-shape package in `GreenfieldAirspaceCurrentShape.lean`,
  and its two persistent families now also have a first narrow compound slice
  in `GreenfieldAirspaceCompound.lean`, with the delivered airspace family now
  also packaged behind one source-level current-shape theorem boundary;
  the next honest work on the greenfield path is therefore no longer
  structural bridge work, is not `ClearedApproach` completion, and is not
  basic current-shape compound closure for `ContinueApproach`,
  `ExtendDownwind`, or `Orbit`;
  the live gap is instead wider execution packaging beyond those slices,
  widening the airspace-clearance family beyond its current narrow slice, or a
  shift to a new narrow family
- widen extraction, greenfield semantics, and issuing-layer theorems together
  rather than widening only one layer in isolation
