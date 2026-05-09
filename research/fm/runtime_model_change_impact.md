# Runtime Model Change Impact

## Purpose

This note records the exact `research/fm` impact of the `core` changes exposed
by the LOWG migration.

It is intentionally narrower than a redesign note. The goal is to answer:

- what breaks mechanically on the Lean/FM side if the runtime model changes
- what does **not** need to change immediately
- what only changes if we also widen the proof boundary rather than merely the
  runtime model

Use this together with:

- [airport-migration-core-gap.md](docs/design/airport-migration-core-gap.md)
- [aviation_world_extraction_contract.md](research/fm/aviation_world_extraction_contract.md)
- [parity_inventory.md](research/fm/parity_inventory.md)

## Executive summary

From the FM point of view, the LOWG-driven runtime changes split into three
classes.

Status as of April 18, 2026:

- the first runtime slice is now implemented in Kotlin
- the route/airspace cleanup pass is also implemented in Kotlin
- the remaining items in this note are the still-open FM-boundary decisions,
  not undone repository work

### A. Pure runtime changes with no immediate Lean churn

These can change in runtime without forcing immediate Lean/extraction edits,
because the current proof-visible route extraction does not carry route-airspace
payloads at all:

- widening `VfrRoute.airspaceProfile` into nullable
  `InVolume` / `InClass` / `Segmented`
- making segmented route-airspace legs volume-authoritative
- distinguishing `AirspaceVolume.memberPoints` from optional boundary geometry

### B. No immediate Lean theorem/code changes, but docs/boundary notes must be explicit

These can be added to runtime `core` without immediately changing Lean code,
**if** the FM boundary intentionally continues to project only the subset of
facts the current proofs use:

- adding airspace boundary geometry alongside the current point-membership model
- adding operational sectors as runtime/world entities only
- adding published VFR procedures as runtime/world entities only
- tightening the runtime AIP typing around those entities (`PlateId`, typed
  contact timing, sealed point/anchor references, grouped advisories, explicit
  communication-failure structure) while leaving raw publication provenance and
  scaffold status outside the proof-visible boundary

### C. Full Lean theorem-surface changes

These require real Lean world/extraction/proof work if we want the FM boundary
to mirror the richer runtime semantics rather than ignore them:

- replacing point-set airspace semantics with polygon/boundary semantics
- making operational sectors proof-visible / instruction-addressable /
  authority-bearing
- making published VFR procedures proof-visible / instruction-addressable /
  resolution-bearing
- replacing loop-based orbit/holding semantics with region/segment loiter
  semantics

## Impact matrix

| Runtime change | Lean compile break? | Minimum FM action | Wider FM action if proof boundary widens |
| --- | --- | --- | --- |
| Widen `VfrRoute.airspaceProfile` to nullable `InVolume` / `InClass` / `Segmented` | No | Document that FM still extracts VFR routes as waypoint sequences only | Add proof-visible route-airspace payloads and theorems if route/airspace semantics become proof-visible |
| Make segmented route-airspace legs volume-authoritative | No | Document that FM still ignores route-airspace payloads entirely | Add proof-visible route-airspace payloads and related validation / resolution theorems |
| Add airspace boundary geometry while keeping point membership | No | Document that FM remains closed on the point-membership subprojection | Widen airspace proof world and completion semantics |
| Add runtime operational-sector entities only | No | Document that sectors remain outside the current proof boundary | Add ids, compile views, extraction, authority, and airspace/procedure theorems |
| Add runtime published-VFR-procedure entities only | No | Document that published-procedure semantics remain package/runtime-only, not proof-visible | Add ids, compile views, extraction, and route-bearing/admission theorems |
| Compile shared LOWG circuit graph into existing `CircuitProcedure` entities | No | None | None |
| Replace loop-based orbit/hold semantics | Yes, if applied to current resolved shapes | Defer | Widen route-adjacent + holding theorem surfaces |

## 1. `VfrRoute` widening

Under the current FM boundary, this turned out to be cheaper than first feared.

### Why

The current proof-visible route extraction already ignores route-airspace
payloads entirely:

- [CompileVfrRouteView](research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean#L343)
  carries only `id` and `waypoints`
- [ScopedVfrRouteSource](research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean#L154)
  likewise carries only `id` and `waypoints`

So changing Kotlin from a route-wide `airspaceClass` to nullable
`VfrRouteAirspaceProfile` does **not** mechanically break the current Lean
compile/extraction boundary.

### Immediate Lean files that must change

None.

Important non-impact:

- [RouteBearingResolutionBridge.lean](research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean)
  still uses only waypoint points when converting a VFR route into route
  points. It does not consume route-airspace payloads.

### FM docs that should be updated

- [aviation_world_extraction_contract.md](research/fm/aviation_world_extraction_contract.md)
  to make it explicit that the proof-visible extraction remains narrower than
  the runtime route-airspace model
- [README.md](research/fm/README.md)
  so it does not imply proof-visible route-airspace parity
- [PROJECT_STATUS.md](research/fm/PROJECT_STATUS.md)
  so the staging-mirror description matches the narrower proof boundary
- [parity_inventory.md](research/fm/parity_inventory.md)
  and [route_bearing_scope.md](research/fm/route_bearing_scope.md)
  so the route-bearing boundary note remains honest

### What does **not** need to change

These do not currently depend on the route-wide class field:

- world-backed route-bearing theorems
- world-backed airspace theorems
- route-bearing resolution bridge
- circuit/orbit/holding proofs

## 2. Airspace boundary geometry

The FM impact depends on whether runtime keeps the current point-membership
subprojection intact.

### 2A. If runtime adds boundary geometry **alongside** point membership

This is the preferred first runtime slice.

#### Immediate Lean code impact

None required.

Reason:

- the current delivered airspace theorem surface is explicitly closed only for
  the point-set + transition model in
  [parity_inventory.md](research/fm/parity_inventory.md#L25)
- the world-backed airspace proofs currently consume only `volume.points` in
  [GreenfieldAirspaceWorldBackedCurrentShape.lean](research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCurrentShape.lean#L27)
  and the associated bridge in
  [RouteBearingResolutionBridge.lean](research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean#L167)

So Lean can remain correct **if** we explicitly say:

- runtime now owns richer airspace boundary geometry
- the current FM branch still reasons over the extracted point-membership
  subprojection only

#### Docs that should change

- [parity_inventory.md](research/fm/parity_inventory.md)
  to make the “point-set + transition model” limitation even more explicit
- [README.md](research/fm/README.md)
  if it would otherwise read as if the delivered airspace branch mirrors the
  full runtime airspace entity
- [aviation_world_extraction_contract.md](research/fm/aviation_world_extraction_contract.md)
  if we want to record boundary geometry as an intentionally unextracted
  runtime-owned fact for now

### 2B. If runtime replaces point membership with polygon/boundary semantics

This is a real FM theorem-surface change.

#### Exact Lean definitions that become stale

##### `research/fm/lean/CertifiedAtc/GreenfieldResolved.lean`

- `ResolvedAirspaceInstruction.points` at
  [GreenfieldResolved.lean](research/fm/lean/CertifiedAtc/GreenfieldResolved.lean#L128)
- `airspaceRouteInsidePoints`
- `airspaceRouteEntryTransitions`
- `airspaceRouteExitTransitions`

##### `research/fm/lean/CertifiedAtc/GreenfieldCompletion.lean`

- `CompletionObservation.activeAirspaces`
- `CompletionObservation.airspaceTransitions`
- `airspaceInside`
- `airspaceEntered`
- `airspaceExited`

See
[GreenfieldCompletion.lean](research/fm/lean/CertifiedAtc/GreenfieldCompletion.lean#L17).

##### `research/fm/lean/CertifiedAtc/GreenfieldResolution.lean`

- `ResolutionWorld.airspaceVolume` at
  [GreenfieldResolution.lean](research/fm/lean/CertifiedAtc/GreenfieldResolution.lean#L15)
- `ConcreteAirspaceVolumeBinding`
- `ConcreteResolutionWorld.airspaceVolumes`
- `ResolvesIndexedStep.remainOutsideControlledAirspace`
- `ResolvesIndexedStep.clearedToEnterControlZone`
- `ResolvesIndexedStep.specialVfrClearance`

##### `research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean`

- `ScopedAirspaceVolumeSource` at
  [RouteBearingExtraction.lean](research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean#L172)

##### `research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean`

- `toConcreteResolutionWorld` airspace section at
  [RouteBearingResolutionBridge.lean](research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean#L167)
- `mem_airspaceVolume_of_mem` at
  [RouteBearingResolutionBridge.lean](research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean#L837)

##### Airspace theorem packages

- [GreenfieldAirspaceWorldBackedCurrentShape.lean](research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCurrentShape.lean)
- [GreenfieldAirspaceWorldBackedCompound.lean](research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCompound.lean)
- [GreenfieldAirspaceWorldBackedDeliveredCurrentShape.lean](research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedDeliveredCurrentShape.lean)
- [GreenfieldDeliveredRefinement.lean](research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean)

These modules are currently proved against the explicit point-set + transition
model and would need theorem restatement and proof repair.

#### FM docs that must change

- [README.md](research/fm/README.md)
- [PROJECT_STATUS.md](research/fm/PROJECT_STATUS.md)
- [parity_inventory.md](research/fm/parity_inventory.md)
- [instruction_authority_contract.md](research/fm/instruction_authority_contract.md)
  only if the authority family stops being conservatively `airspaceVolume`
- [aviation_world_extraction_contract.md](research/fm/aviation_world_extraction_contract.md)

## 3. Operational sectors

There is currently no operational-sector concept anywhere on the Lean side.

### If sectors are runtime/world entities only

#### Immediate Lean code impact

None required.

Reason:

- no delivered theorem surface currently references sector ids
- no compile view currently carries sectors
- no current instruction family resolves against sectors directly

In that case, the correct FM move is just to document that sectors remain
outside the current proof boundary.

### If sectors become proof-visible or instruction-addressable

Then real Lean widening is required.

#### First edit sites

##### IDs / front-door model

- [GreenfieldModel.lean](research/fm/lean/CertifiedAtc/GreenfieldModel.lean#L175)
- likely a new id type or equivalent identifier family

##### Compile view and authority surface

- [ClearanceEnvelope.lean](research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean#L435)
  for a new compile-view list
- [ClearanceEnvelope.lean](research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean#L378)
  if sectors become authority-bearing and need a new entity family in
  `CompileAuthorityEntityType`

##### Extraction layer

- [RouteBearingExtraction.lean](research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean)
  for sector source structures, well-formedness conditions, and origin/preservation theorems

##### Resolution / theorem layer

- airspace theorem files if sector instructions remain part of the airspace
  family
- route-bearing theorem files if procedures start resolving through sectors

#### FM docs that must change

- [aviation_world_extraction_contract.md](research/fm/aviation_world_extraction_contract.md)
- [instruction_authority_contract.md](research/fm/instruction_authority_contract.md)
  if sectors become authority-bearing
- [parity_inventory.md](research/fm/parity_inventory.md)
- [README.md](research/fm/README.md)
- [PROJECT_STATUS.md](research/fm/PROJECT_STATUS.md)

## 4. Published VFR procedures

There is also no published-VFR-procedure entity on the Lean side today.

### If published procedures are runtime/world entities only

#### Immediate Lean code impact

None required.

Reason:

- the current route-bearing and route-adjacent proofs already run against
  extracted circuits, holding patterns, approaches, airways, SIDs, STARs, VFR
  routes, and fixes
- published VFR procedures do not yet appear as a proof-visible entity family
  or instruction reference

This is therefore like sectors: runtime can grow first, and FM can explicitly
leave the new entity outside the current proof boundary.

### If published procedures become proof-visible or instruction-addressable

Then the first edit sites are similar to sectors:

- [GreenfieldModel.lean](research/fm/lean/CertifiedAtc/GreenfieldModel.lean)
  for ids / instruction references if needed
- [ClearanceEnvelope.lean](research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean#L435)
  for compile-view additions
- [RouteBearingExtraction.lean](research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean)
  for source structures, well-formedness, and origin theorems

If runtime instructions start resolving against published VFR procedures rather
than existing route/circuit/airspace entities, then the route-bearing theorem
surface will need a real widening branch:

- [RouteBearingResolutionBridge.lean](research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean)
- [GreenfieldRouteBearing*.lean](research/fm/lean/CertifiedAtc/)
- possibly the route-adjacent current-shape branch too

That is not a small maintenance edit. It is a new FM branch.

## 5. Directional circuits from the shared LOWG graph

This is **not** a Lean problem if we compile the shared graph into ordinary
`CircuitProcedure` entities before extraction.

Current Lean route-adjacent and route-bearing proofs already assume:

- explicit circuit ids
- explicit join entry points
- explicit circuit point lists
- explicit orbit loops

See:

- [GreenfieldResolution.lean](research/fm/lean/CertifiedAtc/GreenfieldResolution.lean#L36)
- [RouteBearingResolutionBridge.lean](research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean#L338)

So:

- if migration compiles LOWG into normal directional circuits, Lean need not
  change
- only if runtime decides to expose shared-graph circuits directly would Lean
  need a new circuit model

That second option is not justified yet.

## 6. Loop-based orbit / holding semantics

This is explicitly deferred, but it is the biggest FM change if/when it lands.

Current Lean route-adjacent and holding proofs assume loop-based semantics:

- `ScopedOrbitPointSource.loopPoints` in
  [RouteBearingExtraction.lean](research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean#L41)
- `ResolutionWorld.circuitOrbit` in
  [GreenfieldResolution.lean](research/fm/lean/CertifiedAtc/GreenfieldResolution.lean#L36)
- `ResolutionWorld.holdingPatternLoop` in
  [GreenfieldResolution.lean](research/fm/lean/CertifiedAtc/GreenfieldResolution.lean#L27)
- `ResolvedAirspaceInstruction` is unaffected, but
  `ResolvedHoldingInstruction` / `ResolvedOrbitInstruction` and completion
  observation are not

Exact proof surfaces affected:

- [GreenfieldOrbit.lean](research/fm/lean/CertifiedAtc/GreenfieldOrbit.lean)
- [GreenfieldOrbitCompound.lean](research/fm/lean/CertifiedAtc/GreenfieldOrbitCompound.lean)
- [GreenfieldRouteAdjacentWorldBackedCurrentShape.lean](research/fm/lean/CertifiedAtc/GreenfieldRouteAdjacentWorldBackedCurrentShape.lean)
- [GreenfieldRouteAdjacentWorldBackedCompound.lean](research/fm/lean/CertifiedAtc/GreenfieldRouteAdjacentWorldBackedCompound.lean)
- the published-hold route-bearing bridge and lifecycle files for `HoldAt`

This should remain a deliberate later branch, not part of the first
migration-to-core slice.

## Recommended sequencing

To keep the split honest:

1. Change Kotlin `VfrRoute` first and do the small Lean compile/extraction fix.
2. Add runtime airspace boundary geometry without changing the current
   point-membership subprojection used by Lean.
3. Add runtime operational sectors and published VFR procedures without
   immediately widening the proof boundary.
4. Update FM docs to state that those new entities are runtime-only for now.
5. Only then decide whether sectors/published procedures deserve a new FM
   widening branch.

## Minimal FM follow-up set for the first runtime slice

If the first runtime slice is:

- widened `VfrRoute`
- richer `AirspaceVolume` with added boundary geometry
- runtime-only operational sectors
- runtime-only published VFR procedures

then the exact FM follow-up set is:

### Lean code

- [ClearanceEnvelope.lean](research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
- [RouteBearingExtraction.lean](research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean)

### FM docs

- [parity_inventory.md](research/fm/parity_inventory.md)
- [aviation_world_extraction_contract.md](research/fm/aviation_world_extraction_contract.md)
- [README.md](research/fm/README.md)
- [PROJECT_STATUS.md](research/fm/PROJECT_STATUS.md)

Nothing else on the Lean theorem side needs to move for that first slice.
