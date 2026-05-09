# Airport Migration To Core Gap

## Purpose

This note records the current handoff boundary between the LOWG migration work
and future deliberate `core` model changes.

It is written after the first LOWG-driven runtime slice landed on
April 17, 2026. So this document is no longer a speculative "what should
change?" note. It is a status note answering:

- what LOWG already forced into the runtime model
- what still remains outside `core`
- what is now a migration/projection problem vs a true model problem
- what any future agent should change next, and what it should leave alone

Use this together with:

- [structured-airport-package.md](docs/design/structured-airport-package.md)
- [entity-driven-plates.md](docs/design/entity-driven-plates.md)
- [runtime_model_change_impact.md](research/fm/runtime_model_change_impact.md)

## First runtime slice: delivered

The following LOWG-exposed gaps are now implemented in `core`:

- `VfrRoute` no longer forces one route-wide `airspaceClass`; it now carries
  optional `VfrRouteAirspaceProfile`
- `AirspaceVolume` now distinguishes explicit `memberPoints` from optional
  boundary geometry
- `AerodromeAip` now owns first-class runtime `OperationalSector` entities
- `AerodromeAip` now owns first-class runtime `PublishedVfrProcedure` entities
- the runtime AIP surface is now also typed more honestly:
  - `PlateId` is a value class
  - contact timing is explicit rather than `beforePoint` / `beforeEntry`
  - sector anchors and published point references are sealed shapes rather
    than nullable products
  - procedure advisories are grouped, and communication-failure structure is
    explicit
  - raw publication status/provenance stays at the structured-package boundary
- world indexing / validation now claims and validates those new entities
- the Lean/FM extraction boundary was repaired for the `VfrRoute` widening and
  still typechecks

This means the migration is no longer blocked by the original four core-model
gaps that LOWG first exposed.

## What LOWG now projects honestly

The current LOWG pipeline now has:

1. authoring / collaboration inputs in DXF + sidecar
2. a richer structured airport package in
   [structured-airport-package.json](cad/airports/rendered/lowg/structured-airport-package.json)
3. a filtered current-core projection in
   [world-candidate.json](cad/airports/rendered/lowg/world-candidate.json)

For LOWG version 1, the current-core candidate now includes:

- runways / taxiways / holding points / aprons / stands
- VFR routes as entities, with honest `InVolume` / `Segmented` profiles where
  the current LOWG worked slice can justify them and null where it still
  cannot
- operational sectors
- published VFR procedures
- a worked low-level LOWG CTR/TMA airspace subset with explicit member points,
  FIR ownership, and runtime-owned boundary geometry

The candidate still validates cleanly in
[world-validation-report.json](cad/airports/rendered/lowg/world-validation-report.json).

So the migration is no longer blocked on parking, apron connectivity, basic
aerodrome geometry, or the first VFR route/sector/procedure runtime boundary.

## What is still not solved

The remaining gaps are now narrower and cleaner.

### 1. Airspace semantics are still dual, not unified

`AirspaceVolume` now owns both:

- point membership, which the current runtime and FM branch still use
- optional boundary geometry, which migration/plates/runtime can now carry

That is the correct first slice, but it is still an explicitly coupled,
syntactic model rather than one unified geometric airspace model.

Still open:

- runtime behaviors that should reason from the boundary itself rather than
  only the point set
- broader airspace projection beyond the worked LOWG low-level CTR/TMA subset
  currently fed into the world candidate
- FM widening if the proof branch should ever reason about polygonal or
  boundary-derived airspace facts

### 2. Directional circuit projection is no longer a `core` blocker

LOWG still uses a shared authored circuit graph in CAD, but that graph now
projects into explicit directional `CircuitProcedure` entities in the runtime
candidate.

That means the previous circuit question has moved:

- it is no longer a `core` modelling blocker for LOWG v1
- it remains a migration/compiler-generalization question for future airports

The current compiler is still LOWG-specific and driven by explicit traversal
choices for the authored shared graph. Do not redesign circuits in `core`
unless a second-airport pass shows a real shape mismatch that the current
`CircuitProcedure` model cannot absorb.

### 3. Hold / loiter semantics remain intentionally deferred

The current runtime still models holds/orbits as loop-backed entities. That was
already known to be wrong for the intended loiter-region semantics.

This remains deferred. LOWG version 1 explicitly excludes the east non-standard
hold from the runtime fit and the plate honesty target.

Do not treat the successful LOWG v1 fit as evidence that the hold model is now
correct. It is not.

### 4. Surrounding airspace is still selectively projected

LOWG now carries real sector geometry and the worked low-level CTR/TMA slice in
the runtime candidate, but the broader surrounding-airspace set is still
selectively kept out of the current-core world candidate.

That is a deliberate first-slice boundary:

- enough runtime-owned geometry exists to drive the current LOWG pages honestly
- not all surrounding-airspace shapes map cleanly into current runtime airspace
  volumes yet

The remaining work here is projection breadth, not another immediate core-type
addition.

## What is now a true future-model branch

The next deliberate model branch should only start after the current
runtime/package/plate boundary is considered stable.

That branch should cover:

- richer airspace semantics beyond point-membership plus optional boundary
- proof-visible operational sectors, if desired
- proof-visible published VFR procedures, if desired
- the intended loiter-region / segment-width holding semantics

Those are no longer emergency migration blockers. They are explicit future
design work.

## Guidance for the next agent

If you are continuing LOWG or another airport migration:

- use the structured airport package as the authoritative pre-core truth
- keep the plates strict and gap-friendly through the plate view model
- prefer projection/compiler work before adding more runtime types
- treat the current FM branch as intentionally narrower than runtime

If you are changing `core` again, do it only for one of the still-open items
above, and keep
[runtime_model_change_impact.md](research/fm/runtime_model_change_impact.md)
aligned with the exact FM consequences.
