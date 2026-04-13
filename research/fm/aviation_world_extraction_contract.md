# AviationWorld Extraction Contract

This note freezes the stable part of the extraction boundary from the
greenfield
[AviationWorld](/home/andrew/dev/projects/twr/greenfield/path-network-design.md)
into the proof-local views used by `research2`.

It is not a repo-specific adapter plan. It is the narrower contract that a
future-project implementation should satisfy before the proofs are refined into
runtime code.

The source inputs remain:

- [path-network-design.md](/home/andrew/dev/projects/twr/greenfield/path-network-design.md)
- [clearance-model-design.md](/home/andrew/dev/projects/twr/greenfield/clearance-model-design.md)

## Boundary Shape

The proof-facing extraction stack is:

1. `AviationWorld`
2. `ClearanceCompileView`
3. `CertifierViews`
4. local Lean certifiers
5. optional clearance-envelope / orchestration theorems

The boundary is intentionally split.

`ClearanceCompileView` keeps the entity, procedure, communications, and
authority facts needed to interpret entity-referenced clearances.

`CertifierViews` keep only the local graph/resource facts that runway, surface,
air, and separation kernels need.

The important design rule is:

- authority and clearance interpretation belong above the local kernels
- local kernels should not re-derive controller jurisdiction from raw world
  structure

## Settled Extraction Obligations

### 1. Reference preservation

If a proof-facing clearance or procedure references an id, extraction must
preserve a matching entity or procedure view for that id.

That includes at least:

- runways
- taxiways
- circuit procedures
- holding patterns
- approaches
- SIDs
- STARs
- airways
- VFR routes
- fixes

Extraction must not invent ids that do not exist in `AviationWorld`.

### 2. Operational structure preservation

The extracted views must preserve the operational facts the proofs actually use,
not just names.

Examples:

- runway threshold, departure-end, exits, and protected-entry context
- taxiway paths and runway-specific holding points
- circuit leg order, reporting points, and extended-downwind geometry
- holding-pattern fix, path, turn direction, and stack separation
- approach waypoint sequence and missed-approach linkage
- route-procedure waypoint order and clearance-limit-relevant fixes

### 3. Local-certifier preservation

`CertifierViews` must preserve enough graph and resource structure for the local
kernels to remain the owners of their current obligations.

That means extraction must carry through:

- runway commitment conflict structure
- surface segment adjacency and hold-point entry segments
- air edges, branches, junctions, guard points, altitude bands, and separation
  track identity
- separation horizon/rule inputs

The boundary should not collapse those local facts into one global clearance
view.

### 4. Communication and authority preservation

The greenfield world makes role the unit of authority and controller the unit
of staffing. The proof boundary must preserve both.

So `ClearanceCompileView` should carry, at minimum:

- role-to-frequency facts
- handoff relations between roles
- role-to-authority grants
- controller-to-role assignments

The proofs should consume explicit extracted authority data rather than
reconstructing it from controller ids or phraseology.

### 5. Lifecycle stability for referenced entities

The extraction boundary must support the clearance-level invariant from
[clearance-model-design.md](/home/andrew/dev/projects/twr/greenfield/clearance-model-design.md):
if a live clearance references an entity, that entity must remain extractable
for the clearance lifecycle unless the clearance is superseded or cancelled.

This does not require the current repo to model dynamic entity deletion now.
It does require the future project not to treat entity lookup as best-effort
cache behavior.

## Minimal Authority Payload

The authority payload should stay generic.

It should not directly encode every instruction form. It should encode
authority as grants over:

- an entity family
- an operation family

That is the stable shape already implied by the greenfield role model:

- Ground: taxiway-taxi and runway-cross style grants
- Tower: runway takeoff/landing and circuit-management style grants
- Approach: approach/arrival sequencing and holding-pattern style grants

The future project may refine the exact grant vocabulary, but the proof
boundary should already assume data shaped like:

- `roleAuthorities : RoleName -> Set (entity family × operation family)`
- `controllerRoles : ControllerId/AgentId -> Set RoleName`

This is enough to support future authority theorems without hard-coding
staffing policy into the local kernels.

## Explicit Ownership Split

The extraction/authority contract implies this ownership rule:

- local kernels decide whether a proposal is legal inside their own state model
- the clearance-envelope / orchestration layer decides whether the issuer is
  allowed to ask for that proposal at all

So authority checking is not a runway-kernel theorem and not a surface-kernel
theorem. It is a boundary theorem above them.

## What Is Frozen Now

The following part of the boundary should now be treated as stable enough for
continued formalization:

- `AviationWorld` extracts into `ClearanceCompileView` and `CertifierViews`
- `ClearanceCompileView` is responsible for entity/procedure references,
  communication data, and authority data
- `CertifierViews` remain authority-free and local-kernel-shaped
- authority is expressed as role grants plus controller-role assignments

## What Is Still Open

This note does not freeze everything.

Still open:

- the final instruction-to-authority mapping for some instruction families
- pending-handoff semantics as a proof input
- dynamic entity creation/deletion semantics
- amendment semantics for compound clearances
- the stronger route-bearing theorem surface for `ClearedTo`,
  `ClearedApproach`, and `HoldAt`

So the boundary is now structurally frozen, but not semantically complete for
every clearance form.

## Immediate Implication

The next proof work should assume:

- the future world extractor must produce explicit authority data
- the authority check belongs at the clearance-envelope / orchestration
  boundary
- sequencing and clearance-limit work can continue against this boundary
  without waiting for repo-specific translators
