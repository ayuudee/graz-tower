# FM Roadmap

This file is the longer-form planning document for `research/fm`.

It replaces the earlier narrow two-milestone framing. The project has already
passed that point. The useful plan now is the full staged proof path, with
explicit status for each phase.

For milestone-critical closure status, use
[completion_milestones.md](research/fm/completion_milestones.md).
That shorter plan is now closed for the scoped surface; this roadmap remains for
broader and optional follow-on work.

## Goal Hierarchy

The roadmap has two tracks:

- primary track: robust local certifiers with explicit isolated guarantees
- optional track: a single-issuer orchestration layer that composes those
  guarantees

Unless the user says otherwise, prefer the primary track.

## Status Legend

- `complete`: delivered and built
- `in_progress`: some real proof work landed, but the exit condition is not yet
  met
- `not_started`: still a shell, interface, or planned phase

## Phase 1: Freeze The Architecture Contract

Status: `complete`

Deliverables:

- shared core vocabulary
- command catalog
- static plan shapes
- kernel signatures
- orchestration signatures

Exit condition:

- no ambiguity about what each kernel owns
- no ambiguity about what orchestration owns

Delivered in:

- [Core.lean](research/fm/lean/CertifiedAtc/Core.lean)
- [CommandCatalog.lean](research/fm/lean/CertifiedAtc/CommandCatalog.lean)
- [Interfaces.lean](research/fm/lean/CertifiedAtc/Interfaces.lean)
- [command_catalog.md](research/fm/command_catalog.md)
- [certifier_interfaces.md](research/fm/certifier_interfaces.md)

## Phase 2: Finish The Generic Runway Kernel

Status: `complete`

Work:

- define commitment kinds
- define conflict semantics
- define `runway_certify`
- prove approval preserves runway invariants

Exit condition:

- runway kernel is a closed proved local checker with no airport graph
  dependency

Delivered in:

- [RunwayKernel.lean](research/fm/lean/CertifiedAtc/RunwayKernel.lean)

Primary theorem:

- `RunwayKernelMilestone1Theorem`

## Phase 3: Optional Composition Milestone For The First Joint Acts

Status: `complete`

Work:

- define `instantiate_plan` for `ClearedForTakeoff`, `ClearedToLand`, and
  `GoAround`
- define approval bundling
- define the narrow compatibility check for this slice
- prove non-bypass
- prove those commands require runway, air, separation, and compatibility

Exit condition:

- if the single-issuer architecture is used, those three commands can only be
  issued through the certified path

Delivered in:

- [Interfaces.lean](research/fm/lean/CertifiedAtc/Interfaces.lean)
- [JointActs.lean](research/fm/lean/CertifiedAtc/JointActs.lean)

Primary theorems:

- `PlanInstantiationTheorem`
- `CompatibilityNarrownessTheorem`
- `NonBypassTheorem`
- `JointActsMilestone2Theorem`

## Phase 4: Build The Surface Kernel Over Real Airport Surface Graphs

Status: `complete`

Work:

- define the surface graph schema
- define reservations, protected-entry tokens, and movement legality
- prove local soundness
- instantiate against one simulator-style airport surface graph

Exit condition:

- taxi and surface commands are locally certifiable against a real graph

Delivered in:

- [SurfaceKernel.lean](research/fm/lean/CertifiedAtc/SurfaceKernel.lean)

Primary theorem:

- `SurfaceKernelSoundnessTheorem`

Validation artifact:

- `TestAerodromeSurfaceGraph`
- `TestAerodromeProtectedEntryApproved`

## Phase 5: Build The Air-Path Kernel Over Real Airborne Graphs

Status: `complete`

Work:

- define edges, branches, junctions, guard points, and altitude bands
- define reservations and transition legality
- prove local soundness
- instantiate against one simulator-style airborne graph

Exit condition:

- air-path acts are locally certifiable against a real graph

Delivered in:

- [AirKernel.lean](research/fm/lean/CertifiedAtc/AirKernel.lean)

Primary theorem:

- `AirKernelSoundnessTheorem`

Validation artifacts:

- `TestAirGraph`
- `TestAirState`
- `TestAirBranchProposal`
- `TestAirSpeedReductionProposal`

## Phase 6: Finish The Separation Layer Properly

Status: `in_progress`

Work:

- define the generic pairwise checker
- define the rule language
- formalize neutrality of non-certified airborne acts
- formalize boundary sufficiency
- formalize horizon viability where needed by the brief-level separation story
- peer selection and conservative coverage remain supporting work for the
  optional composition track

Exit condition:

- the separation layer stands as a strong local certifier even before any
  single-issuer orchestration theorem is finished

Current state:

- a concrete pairwise checker and `SeparationCheckerSoundnessTheorem` now exist
  in [SeparationChecker.lean](research/fm/lean/CertifiedAtc/SeparationChecker.lean)
- `SeparationCoverageTheorem` is now proved in
  [Interfaces.lean](research/fm/lean/CertifiedAtc/Interfaces.lean)
- `SeparationNeutralTransition` and
  `SeparationBoundarySufficiencyTheorem` now provide a local stepwise target
  for non-certified-command neutrality and boundary sufficiency
- `H_sep` and `Viable_sep` continuation semantics now exist in Lean
- a conservative concrete neutral-command slice now exists for the current
  report, proceed, and coordination commands that are modeled as separation
  no-ops
- the project now has explicit proof-side compiled app-to-certifier views in
  Lean; with the April 2026 pivot to `docs/design/`, those compile-view shapes
  should be treated as proof-side extraction targets rather than as the default
  repo-local Kotlin implementation target
- the separation layer now consumes stable track metadata from the air graph
  rather than synthesizing a per-aircraft track id
- a partial generated continuation set now exists for continue-current-path,
  hold-current-path, speed-reduction, reserved-branch-choice, and
  recovery-path candidates via `air_certify`
- the optional orchestration layer now also uses the surface kernel for a first
  concrete `HoldShortOf` path over the current hold-point model
- the optional orchestration layer now also uses the surface kernel for a
  conservative `TaxiTo` path via node-route projection onto the current
  directed surface graph
- the optional orchestration layer now also uses a concrete joint
  runway/surface path for `CrossRunway`, acquiring a
  `protectedForCrossing` runway commitment and moving onto the protected
  successor segment from the current hold-point segment
- the current typed orchestration slice now also includes `LineUpAndWait`
  through that same protected-entry joint path, using the existing
  `.lineUpAndWait` runway commitment kind
- the current typed orchestration slice now also includes a conservative
  `JoinCircuit` path over the present air model
- the current typed orchestration slice now also includes `ReduceSpeedTo`,
  backed by the real `reduceSpeedMax` air act rather than a conservative alias
- the checker is still narrower than the full brief-level separation contract
  because command-surface coverage is still partial and many non-certified
  command families still have no explicit neutrality proof story

## Phase 7: Optional Composition Track For A Single Issuance Kernel

Status: `in_progress`

Work:

- formalize lifecycle integration
- formalize footprint-based compatibility
- formalize dependency consistency
- formalize ownership and mode consistency
- prove issuance implies all required local approvals plus compatibility

Exit condition:

- if a product architecture wants one central issuer, that issuance path is
  structurally proved

Current state:

- milestone 2 slice is proved
- the instantiated orchestration surface is now wider than milestone 2 and
  includes several additional surface, air, and separation command families
- the project now has a greenfield clearance-boundary scaffold in Kotlin:
  entity-referenced instructions, compound clearance content,
  the world model, and `StructuredClearance`
- the future project model is now documented in
  [path-network-design.md](docs/design/path-network-design.md)
  and
  [clearance-model-design.md](docs/design/clearance-model-design.md),
  which should be treated as product-authoritative over the current repo's
  staging types
- the structural extraction contract from `AviationWorld` into
  `ClearanceCompileView` / `CertifierViews` is now explicit, including
  role-authority grants and controller-role assignments at the compile-view
  boundary
- the authority surface is now narrowed further by an explicit partial
  instruction-to-grant mapping for the stable instruction subset
- [ClearanceEnvelope.lean](research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
  now mirrors that boundary in Lean with greenfield clearance instructions,
  an explicit `sequential | immediate | standalone` timing split, frontier
  selection, and a partial compiler back into the current atomic certified path
- the current frontier compiler now preserves immediate steps plus the active
  sequential step, and the current `advanceSequentialStep` operation now has
  monotonic one-step progression lemmas above that scaffold
- the admitted sequential surface subset now also has an explicit completion
  observation model and observation-driven one-step advancement / frontier-shift
  lemmas
- the authorization-aware frontier/content/structured compile surface is now
  conservative in both directions for the currently resolved subset:
  authorized inputs reduce to the unchecked compiler, and successful checked
  compilation proves frontier-level issuer authorization while preserving the
  selected frontier instruction set
- the envelope layer now has a packaged movement-envelope theorem for
  no-skipping over realized completions in the admitted sequential subset, but
  is still short of no-partial-issuance and wider route-bearing coverage
- the full canonical theorem is still only stated
- this remains optional integration work, not the only source of research value

Open targets:

- `CanonicalTopLevelTheorem`

## Phase 8: Refine Into Code

Status: `not_started`

Work:

- export kernel interfaces into Kotlin and Rust design
- if the single-issuer architecture is adopted, make `Issued` constructible
  only through orchestration
- encode the routing table in code
- keep compatibility narrow in implementation

Exit condition:

- code architecture matches the proof architecture

## Phase Close-Out Rule

Each phase close-out should add a short product-facing note that answers:

- what narrower claim is now justified by the proof work
- what still is not justified in product or certification terms
- what the next phase is intended to unlock

## Phase 9: Add Continuous Enforcement

Status: `not_started`

Work:

- runtime checks for unproved slices
- if orchestration remains in scope, always-run tests for routing completeness
- if orchestration remains in scope, always-run tests for non-bypass
- graph-instance validation for real airports

Exit condition:

- code drift breaks loudly

## Immediate Next Move

The current critical path is not "prove everything."

It is:

- freeze the greenfield-derived extraction boundary and theorem targets that a
  future project will rely on
- resolve the greenfield clearance semantics that block stronger Lean
  sequencing, atomicity, authority, and clearance-limit theorems
- make sequencing real above the current frontier compiler before widening much
  more proof work on the older atomic command interface
- then widen separation and command coverage through that stabilized boundary
