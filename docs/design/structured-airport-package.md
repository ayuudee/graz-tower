# Structured Airport Package

## Intent

Before an airport can be translated into `core`, it needs a richer structured package that can hold
everything we know about the airport, including concepts the current model still cannot represent.

This package is not the plate output and not the runtime world.

It is the authoritative structured airport description that sits between:

1. authoring / collaboration inputs
2. downstream projections such as:
   - current-core world candidate
   - strict plate view model
   - future full-core world import

## Layering

The intended stack is:

1. **Authoring / collaboration layer**
   - DXF
   - sidecar
   - interactive map
   - source-reconciliation tools

2. **Structured airport package**
   - rich structured LOWG data
   - contains both directly projectable entities and richer candidate concepts
   - allowed to be more expressive than the current `core` model

3. **Projection layer**
   - current-core subset projection
   - strict plate view model projection
   - later, full upgraded-core projection

The structured package is the truth. Everything else is a translation.

For LOWG, the current implementation lives in:

- `bin/airport_structured_package.py`
- `bin/project_structured_airport_package.py`
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateSchema.kt`
- `cad/airports/rendered/lowg/structured-airport-package.json`

Status as of April 17, 2026:

- the first LOWG runtime slice now projects VFR routes, operational sectors,
  published VFR procedures, directional circuits, and a worked low-level LOWG
  CTR/TMA airspace slice out of this package into the current-core candidate
- the second LOWG cleanup pass now also narrows raw publication/AIP data at
  that boundary: the current-core projection carries typed `PlateId`,
  typed contact timing, sealed point/anchor references, and an advisory bag,
  while raw publication status/provenance remains in the structured package
- the current LOWG route-airspace fit is now mixed by design:
  `InVolume(LO585)` for the southeast/southwest entries,
  segmented `LO585 -> LO0EF_E` for the western corridor,
  and no projected runtime profile yet for the northeast entry, where the
  pre-CTR leg still cannot be assigned honestly under the current low-level
  slice
- the package still matters because it remains the only honest place to hold
  richer authoring/procedure truth before projection

## Why this layer exists

Without this layer, we get two failure modes:

- we force airport knowledge directly into the current `core` model and lose important semantics
- we let renderers or one-off tools read raw authoring/source data directly and accidentally invent
  a second semantics path

The structured package solves both:

- richer than the current model
- stricter than raw source data
- explicit about projection gaps

## Package contents

The package should contain four classes of information.

### 1. Direct core-fit entities

These should already be shaped so they can translate cleanly into the current or future runtime
model:

- airport metadata
- frequencies
- physical geometry points and paths
- runways
- taxiways
- holding points
- aprons
- stands
- fixes

### 2. Candidate operational structures

These are operationally real, but may not yet fit the current `core` model:

- VFR routes
- circuit graphs and directional circuit procedures
- operational sectors
- airspace boundary geometry
- holding / loiter constructs
- named joins / anchors / route attachment points

### 3. Publication-facing operational semantics

These are structured facts, not renderer prose:

- published VFR procedure protocol
- altitude constraints
- contact-before requirements
- omit-report expectations
- route-end “hold unless cleared further” behavior
- local operating restrictions
- COM failure behavior

These may later migrate into `core`, but they belong in the structured package immediately even if
they cannot yet be projected into runtime entities.

### 4. Explicit projection diagnostics

The package should carry explicit status, not rely on tribal memory:

- projection status per object
- blocked fields
- unresolved references
- known source divergences
- known modeling gaps

## LOWG shape

For LOWG, the structured package should become the single place where we can answer:

- what is physically there
- what procedures exist
- what route/circuit/sector/hold geometry exists
- what is known but not yet representable in `core`
- what remains unknown or provisional

Concretely, LOWG should be complete enough here that the following can all be projected from it:

- the entity-only plate pack
- the current-core validation candidate
- the collaborative map overlays

If two of those disagree, the package or the translator is wrong.

## Collaboration role of the map

The map is not the gold artifact.

The map is the collaboration and debugging surface for developing the package:

- compare authored geometry with projected structures
- inspect joins, sectors, route continuity, and parking access
- resolve naming and ownership problems
- expose disagreements between sources

The map may show provisional overlays and comparison layers that should never appear in the strict
plate output.

## Translation targets

The structured airport package should feed three translators.

### A. Plate translator

Input:

- structured airport package only

Output:

- strict plate view model

Constraint:

- if the package lacks something, the plate shows a gap

### B. Current-core translator

Input:

- structured airport package

Output:

- best honest subset that fits the current runtime model

Constraint:

- temporary assumptions must be explicit

### C. Full-core translator

Input:

- structured airport package

Output:

- full runtime world once the necessary model changes exist

Constraint:

- no temporary assumptions for in-scope airport behavior

## Exit criteria for “package complete”

An airport package is complete enough to drive model work when:

- all known airport/procedure/sector/route information is captured structurally
- every missing part is explicitly tagged as unknown, deferred, or blocked by the model
- the collaborative map can inspect the whole package
- the plate translator can produce an entity-only pack with honest gap pages

At that point, remaining failures are model failures, not airport-data failures.

## LOWG next use

For LOWG, this means the next package-completion focus is now narrower:

- keep the east non-standard hold explicit as a structured blocked concept
- keep the projected directional circuits honest and LOWG-specific until a second-airport
  generalization pass exists
- continue using the map to resolve remaining geometry and ownership issues
- widen the remaining non-runtime special-use surrounding-airspace projection
  only when the current-core subset needs it

Only once that package is stable should the corresponding `core` changes be designed.

The current actionable handoff for that next step now lives in
[airport-migration-core-gap.md](/home/andrew/dev/projects/twr2/docs/design/airport-migration-core-gap.md).
