# 2026-04-14: Migration Module Structure

## Decision

Created a `migration` module (initially `tools`, renamed) containing standalone parsers for aviation data formats. The module depends on `core` (transitively `protocol`) but the parsers themselves are in separate packages with no domain model dependency.

## Structure

```
migration/
  src/commonMain/kotlin/xyz/easiersaid/twr/migration/
    common/     -- GeoTypes, ParseResult (shared)
    aptdat/     -- X-Plane apt.dat parser + model
    ofmx/       -- OpenFlightMaps OFMX parser + model
    cifp/       -- X-Plane CIFP parser + model
```

## Key design principles

- **Faithful to source format** -- each parser's domain model represents what's in the file, not our ATC domain
- **Pure core** -- all parsing logic is total, no exceptions, `Either` for errors
- **IO at the edge** -- `suspend` functions for file reading in `jvmMain` only
- **Warnings vs errors** -- unknown records are warnings; malformed required fields are errors

## Boundary enforcement

Parsers should not depend on `core` or `protocol` types. Currently enforced by convention; plan to add Konsist rules to enforce statically.

## Rationale for single module (vs separate `tools` + `adapter`)

Originally planned as a standalone `tools` module with a separate adapter. Collapsed into one module because: (a) only one consumer, (b) it's a one-time migration concern, (c) extra module is ceremony for a pipeline tool.
