# Completion Milestones

Last updated: April 13, 2026

This document defines the shortest path from the current FM state to
completion.

It is intentionally narrower than the full historical roadmap in
[milestones.md](/home/andrew/dev/projects/twr2/research/fm/milestones.md).
The goal here is not to enumerate every worthwhile improvement. The goal is to
reach the point where the certifiers and the top issuing layer are actually
proved for the safety properties the project claims.

Current status:

- `Safety-complete (N₀)` is now achieved for the scoped surface defined in
  [safety_complete_scope.md](/home/andrew/dev/projects/twr2/research/fm/safety_complete_scope.md)
- `Full-brief complete` is now also achieved for that same scoped surface

## Completion Bars

There are two completion bars.

### 1. Safety-complete (`N₀`)

This is the primary target and the shortest path from here.

The project is `Safety-complete (N₀)` when all of the following hold:

- the in-scope safety-critical command and clearance surface is frozen
- every issued in-scope safety-critical act passes through the required local
  certification path
- runway, surface, air-path, and separation each have complete local theorem
  coverage for the scoped surface
- the extraction boundary from
  [AviationWorld](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md)
  into proof views is mechanized rather than only documented
- the greenfield clearance and active-clearance semantics are stable enough to
  support top-level proofs
- the single issuing layer proves routing, instantiation, peer coverage,
  compatibility narrowness, authority gating, non-bypass, and issuance
  soundness for the scoped surface
- reachable issued states preserve the scoped runway, surface, air/separation,
  and interface invariant families under the nominal assumption set in
  [brief_v4.md](/home/andrew/dev/projects/twr2/research/fm/brief_v4.md)

This is the main definition of "complete" for the current programme.

### 2. Full-brief complete

This is strictly later than `Safety-complete (N₀)`.

The project is `Full-brief complete` when `Safety-complete (N₀)` holds and the
remaining brief-level mode obligations are also proved:

- degraded-mode invariant families
- emergency-mode invariant families
- guarantee withdrawal when assumptions fail
- fallback to the strongest justified remaining regime

For the current narrowed programme, this second bar is now also closed.

## Starting Point

As of now, the project already has:

- a frozen split-kernel architecture
- concrete local runway, surface, air-path, and separation kernels
- local soundness theorems for all four kernels
- a real greenfield proof stack for model, lifecycle, resolution, completion,
  execution, and reachability
- a partial older atomic orchestration slice with non-bypass and a first joint
  theorem

The main milestone work is now closed for the scoped surface.

What remains after completion is optional follow-on work:

- widen the theorem surface beyond the current scoped command families
- replace conservative mode-overlay semantics with richer operational mode
  semantics if the product needs them
- widen extraction and proof coverage toward the broader route-bearing model

## Shortest-Path Rules

To avoid deviation, follow these rules until `Safety-complete (N₀)` lands.

- Do not widen the command surface unless the widening is required by an
  already-scoped safety theorem.
- Do not spend time polishing the old atomic orchestration path into the final
  architecture.
- Do not build repo-local runtime adapters unless they are needed as theorem
  witnesses for the extraction boundary.
- Do not widen degraded/emergency proofs before nominal safety closes.
- Do not treat proof elegance refactors as milestone work unless they unblock a
  safety theorem directly.
- If a command family still has unsettled authority or lifecycle semantics,
  either settle it or mark it explicitly out of scope for `Safety-complete
  (N₀)`. Do not guess.

## Milestone Sequence

These milestones are ordered by dependency, not by convenience.

### Milestone 1: Freeze The Final Safety Theorem Surface

Status: `complete`

Purpose:
turn the current "proof inventory" into an exact theorem target for
`Safety-complete (N₀)`.

Work:

- define the exact in-scope command and clearance surface
- classify every in-scope family as:
  local-certifier certified, globally composed, or proved neutral
- freeze the exact invariant families that must hold at completion:
  runway, taxiway, air/separation, and interface
- freeze the nominal top-level theorem package for the issuing layer

Recommended outputs:

- a theorem-target note derived from
  [brief_v4.md](/home/andrew/dev/projects/twr2/research/fm/brief_v4.md)
- [safety_complete_scope.md](/home/andrew/dev/projects/twr2/research/fm/safety_complete_scope.md)
  This is the current scoped theorem-target inventory.
- a scoped command-family table
- explicit theorem declarations for the final top layer

Exit condition:

- there is no ambiguity about what must be proved for `Safety-complete (N₀)`
- no theorem depends on an unstated expansion of the command surface later

### Milestone 2: Finish The Separation Certifier Properly

Status: `complete`

Primary module:

- [SeparationChecker.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SeparationChecker.lean)

Purpose:
close the largest remaining local-kernel gap.

Work:

- finish non-certified-command neutrality for the scoped airborne surface
- finish the stepwise boundary-sufficiency story over `H_sep`
- finish the `Viable_sep` continuation theorem over the concrete continuation
  set
- finish coverage for the scoped separation-relevant command families

Current state:

- [ScopedSeparation.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSeparation.lean)
  now packages the exact `Safety-complete (N₀)` separation scope:
  scoped command classification, scoped neutrality, scoped peer coverage,
  `ScopedSeparationBoundarySufficiencyTheorem`, and
  `ScopedViableSepTheorem` over the concrete continuation-family cases
- [SeparationChecker.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/SeparationChecker.lean)
  now has stronger viability support for the concrete continuation families:
  `continueCurrentPath`, `holdCurrentPath`, and `reduceSpeed` can now establish
  `Viable_sep` from baseline pairwise separation, the reserved-branch and
  recovery-path families now have explicit membership and viability wrappers,
  and `viable_sep_of_capableApproval_equivIssuedScenario` now discharges the
  local continuation theorem for continue-capable approved air acts whenever
  the issued separation scenario's `subjectAfter` is operationally equivalent
  to the approved successor state

- [ScopedSeparation.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSeparation.lean)
  now also contains `ScopedIssuedScenarioViableSepTheorem`, which closes the
  final scoped packaging gap by taking `ClearedForTakeoff`,
  `ClearedToLand`, `ClearedTouchAndGo`, `GoAround`, and knot-based
  `ReduceSpeedTo` through plan instantiation, approved air successor
  extraction, issued-scenario membership, and into the local viable-successor
  theorem

Exit condition:

- the separation layer stands as a complete local certifier for the scoped
  surface
- `H_sep` plus `Viable_sep` are no longer only partial or conservative targets

Why this comes now:

- the top issuing-layer theorem is not credible while the main local
  separation obligations are still partial

### Milestone 3: Mechanize The Extraction Boundary

Status: `complete`

Primary sources:

- [aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md)
- [certifier_view_alignment.md](/home/andrew/dev/projects/twr2/research/fm/certifier_view_alignment.md)

Recommended Lean destination:

- [ScopedExtraction.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedExtraction.lean)

Purpose:
turn the extraction notes into theorem-bearing proof inputs.

Delivered:

- a scoped proof-side source world for `Safety-complete (N₀)`:
  `ScopedAviationWorld`
- deterministic extraction into:
  `ClearanceCompileView`, `ScopedCertifierViews`, and `OrchestrationEnv`
- no-invented-id theorems for extracted runways, taxiways, and role/frequency
  facts
- scoped reference-preservation theorems for the in-scope runway, taxiway, and
  role/frequency references
- operational-preservation theorems connecting extracted runway/taxiway facts
  back to the certifier-local runway and surface views
- authority-preservation theorems from extracted role grants and
  controller-role assignments into `controllerHasAuthorityGrant` and
  `instructionIssuerAuthorized`
- scoped lifecycle-stability theorem for the `Safety-complete (N₀)`
  clearance-instruction surface

Scope note:

- this milestone is intentionally closed only for the scoped
  `Safety-complete (N₀)` surface
- route-bearing procedure preservation and the clearance-limit /
  holding-pattern world invariant remain deferred with Bucket C

Why this comes now:

- without this layer, the eventual top theorem still rests on notes instead of
  proved boundary facts

Exit condition:

- for the scoped `Safety-complete (N₀)` surface, no safety-critical theorem now
  depends on extraction assumptions that only exist in prose

### Milestone 4: Close The Remaining Greenfield Semantic Gaps

Status: `complete`

Primary modules:

- [GreenfieldModel.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldModel.lean)
- [GreenfieldLifecycle.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldLifecycle.lean)
- [GreenfieldResolved.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldResolved.lean)
- [GreenfieldResolution.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldResolution.lean)
- [GreenfieldCompletion.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldCompletion.lean)
- [GreenfieldExecution.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldExecution.lean)
- [GreenfieldReachability.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldReachability.lean)

Purpose:
stabilize the final proof-side clearance and execution semantics that the top
layer will rely on.

Delivered:

- [ScopedGreenfield.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedGreenfield.lean)
  now packages the exact scoped greenfield theorem surface above the current
  model / lifecycle / resolution / execution stack
- the scoped greenfield authority mapping now exists as
  `scopedInstructionRequiredAuthorityGrant?` for the in-scope Bucket A/B
  families that need issuer gating
- the greenfield model now carries the missing neutral scoped instructions
  `ReportDownwind`, `ReportFinal`, and `Proceed`
- the greenfield model now aligns `GoAround` supersession with the runtime
  shape by including `.speed` in the superseded domain set
- the scoped compound surface now has a real no-partial-issuance package:
  movement-only compounds expose no immediate frontier steps and at most one
  live frontier instruction
- conditional surface compounds are now packaged honestly at the greenfield
  boundary via theorem-bearing normalization and checked staging results
- resolved execution compatibility / reachability for the scoped surface is now
  packaged through the existing reachability layer rather than left implicit

Scope note:

- route-bearing and open-ended families remain out of scope with Bucket C
- this milestone is closed for the scoped `Safety-complete (N₀)` surface, not
  for the broader route/procedure theorem surface

Why this comes after extraction:

- these theorems should close against the real future-project boundary, not
  against another temporary staging shape

Exit condition:

- for the scoped `Safety-complete (N₀)` surface, the greenfield execution
  boundary is now stable enough to serve as the final proof-side issuance
  surface
- no scoped top-level theorem still depends on a deferred semantic question in
  the greenfield layer

### Milestone 5: Build The Final Uber Layer On The Greenfield Boundary

Status: `complete`

Recommended Lean destination:

- a new top-layer module above the greenfield execution boundary

Purpose:
replace "partial orchestration scaffold" with the actual final issuing-layer
proof surface.

This layer should own:

- routing from scoped command/clearance classes to static certification plans
- instantiation of local proposals and separation scenarios from extracted
  proof views
- peer selection
- approval bundling
- authority gating
- compatibility
- admission into the active set
- supersession and lifecycle integration
- issuance

Target theorem package:

- routing completeness
- plan-instantiation correctness
- peer-coverage soundness
- compatibility narrowness
- authority-gated issuance
- non-bypass
- issuance soundness

Delivered:

- [ScopedIssuance.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedIssuance.lean)
  now owns the final scoped issuing-layer theorem package above
  `ScopedExtraction`, `ScopedGreenfield`, and the resolved execution boundary
- a theorem-bearing bridge now exists from the scoped greenfield instruction
  surface into the older atomic certified-path layer via:
  `bridgeableLevelToLegacyAltitudeFt?`,
  `bridgeableSpeedToLegacyKnots?`,
  `scopedAtomicCommand?`, and
  `scopedCommandProposal`
- the scoped theorem package now includes:
  `ScopedRoutingCompletenessTheorem`,
  `ScopedPlanInstantiationCorrectnessTheorem`,
  `ScopedPeerCoverageSoundnessTheorem`,
  `ScopedCompatibilityNarrownessTheorem`,
  `ScopedAuthorityGatedIssuanceTheorem`,
  `ScopedNonBypassTheorem`, and
  `ScopedCertifiedIssuanceSoundnessTheorem`
- active-set lifecycle integration is now packaged honestly at the final
  top-layer boundary by reusing resolved admission reachability rather than
  silently dropping back to the older atomic interface

Scope note:

- the scoped air-modifier surface is now explicitly narrowed to the variants
  the current local separation story can carry honestly:
  `ReduceSpeedTo` in knots
- Mach targets and the full `ClimbTo` / `DescendTo` family remain outside the
  scoped `Safety-complete (N₀)` claim until the local air/separation boundary
  is strengthened enough to justify continuation existence uniformly

Exit condition:

- every issued in-scope safety-critical act is proved to pass through the
  required certification and compatibility path above the greenfield boundary

Important constraint:

- do not treat
  [Interfaces.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/Interfaces.lean)
  as the final home of this theorem unless doing so is genuinely cheaper than a
  greenfield-final module with the same theorem shape

### Milestone 6: Prove Reachable-State Safety For The Issuing Layer

Status: `complete`

Purpose:
close the top proof story for `Safety-complete (N₀)`.

Work:

- define the reachable issued-state semantics for the final top layer
- prove that issuance preserves the interface invariant family
- connect local kernel soundness back into system-level invariant
  preservation
- prove that reachable issued states preserve:
  runway invariants,
  taxiway invariants,
  air/separation invariants, and
  interface invariants

Delivered:

- [ScopedSafety.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedSafety.lean)
  now closes the reachable-state package above
  [ScopedIssuance.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedIssuance.lean)
- a scoped world/state well-formedness boundary now exists via:
  `ScopedSafetyWorldWellFormed` and `ScopedOrchestrationInv`
- approval collection now has theorem-bearing component-preservation lemmas for
  runway, surface, and air successor states
- issued commands now preserve the scoped nominal/interface/kernel invariant
  package via `issue_command_preserves_ScopedOrchestrationInv`
- the scoped final layer now has a reachable-state relation:
  `ReachableScopedIssuedState`
- the top reachable-state preservation theorem now exists as
  `ScopedReachableSafetyTheorem`
- separation is now packaged honestly at this layer as issued-step soundness,
  not as a stored orchestration-state invariant:
  `ScopedSeparationIssuanceSafetyTheorem` and
  `ScopedIssueStepSeparationSoundTheorem`

Scope note:

- this milestone deliberately treats runway, surface, air, and interface as
  state invariants, but separation as witness-backed issued-step safety
- that split is intentional because the orchestration state does not store a
  separation-state projection
- with Milestone 2 now closed, this milestone closes the scoped nominal
  `Safety-complete (N₀)` bar rather than merely preparing for it

Exit condition:

- no further reachable-state theorem work blocks `Safety-complete (N₀)` for
  the scoped surface
- together with Milestone 2, the scoped nominal safety bar is fully closed

### Milestone 7: Add The Full-Brief Mode Layer

Status: `complete`

Purpose:
close the remaining brief-level obligations after nominal safety is already
done.

Work:

- formalize degraded and emergency invariant families
- prove guarantee withdrawal when assumptions fail
- prove fallback to the strongest justified remaining regime
- integrate those mode transitions into the top issuing layer

Delivered:

- [ScopedModes.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ScopedModes.lean)
  now provides the full-brief mode layer above the scoped nominal issuer
- `AssumptionAssessment`, `selectedMode`, and `selectedMode_strongest` make
  regime assessment and strongest-justified fallback explicit
- `enterAssessedMode_withdraws_nominal` proves nominal guarantees are withdrawn
  once the assessment is non-nominal
- a concrete degraded/emergency fallback vocabulary now exists as
  `FallbackCommand`
- `issueWithAssessment` integrates nominal issuance and fallback issuance into
  one theorem-bearing mode-aware top layer
- `ReachableScopedModeState_preserves_fullBrief` and
  `FullBriefFallbackTheorem` close the scoped full-brief preservation story

Scope note:

- the mode monitor remains abstract at this layer; only the assessment output
  is modeled
- fallback commands use a conservative overlay semantics rather than richer
  operational state changes
- this is intentional for the shortest honest path: the theorem claim is now
  closed without pretending richer emergency automation than has been proved

Exit condition:

- `Full-brief complete` is true

## What Not To Do Before Milestone 6

These are real temptations, but they are not on the shortest path.

- replacing every old atomic theorem with a greenfield theorem before the final
  safety package needs it
- widening unsupported command families just because the model can express them
- broad runtime-code refinement work
- proof cleanup whose only benefit is elegance
- adding more airport instances without a theorem need
- full degraded/emergency closure before nominal closure

## Immediate Next Move

The scoped programme is now complete.

The next work, if desired, is optional scope widening:

- enrich the mode monitor from an abstract assessment output to a more concrete
  observation/failure model
- replace conservative fallback overlay actions with richer certified
  operational emergency behaviour
- widen the scoped surface toward the broader route-bearing and greenfield
  model
