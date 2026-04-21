# Environment

Nix development shell (`nix-shell` or direnv). JDK 21, Gradle 8, Lean 4, TLA+.
Kotlin Multiplatform targeting JVM. Modules: `protocol`, `core`, `migration`.

# Commands

```
./gradlew :protocol:allTests :core:allTests   # run all tests
./gradlew detekt                               # static analysis
./gradlew :core:jvmTest --tests '*.ActiveClearanceEngineTest'  # single test class
./gradlew build                                # full build
```

# X-Plane Data And Tooling

- Parsers for X-Plane/OFM source formats live under `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/{aptdat,cifp,ofmx}/`.
- JVM file-reading helpers currently live under `migration/src/jvmMain/kotlin/xyz/easiersaid/twr/migration/{aptdat,ofmx}/`.
- Small checked-in parser fixtures live under `migration/src/commonTest/resources/airports/`, `migration/src/commonTest/resources/cifp/`, and `migration/src/commonTest/resources/ofmx/`.
- Larger local datasets live under `data/`: `data/cifp/LOWG.dat`, `data/ofm/austria/ofmx_extracted/ofmx_lo/isolated/ofmx_lo.ofmx`, the original OFM zip archives under `data/ofm/austria/`, and reference charts under `data/charts/LOWG/`.
- `data/airports/` exists for raw airport files, but the current checked-in apt.dat samples are in `migration/src/commonTest/resources/airports/`.
- Hand-authored airport geometry work currently lives under `cad/airports/` (`lowg.dxf`, `lowg_circuits.dxf`).
- `migration/src/commonTest/kotlin/xyz/easiersaid/twr/migration/ofmx/OfmxFullFileTest.kt` expects the full Austria OFMX file at `data/ofm/austria/ofmx_extracted/ofmx_lo/isolated/ofmx_lo.ofmx`.

# Code Style

- Functional style. Arrow for typed errors (`Either`, `NonEmptyList`). Pure functions, total functions, exhaustive `when`, immutable data only. No `var`, no mutable collections, no side effects in domain logic. See `detekt.yml` for enforcement.
- Ports and adapters. Focus on good/deep modularisation. 
- When building stuff, we tend to: make it work, make it right, make it fast. Figure out which of those is relevant to a piece of work and decide the appropriate way to go about it. 

# Testing

Follow the principles in `docs/test-standards.md`. In particular:

- Prefer integration tests that exercise the full pipeline over isolated unit tests.
- Unit test only when there is a formal, independent oracle of correctness (e.g. ICAO
  standards for frequency ranges, squawk codes) or clear business value.
- Use the type system to eliminate tests: if the compiler prevents it, don't test it.
- If you can't articulate the business value of a test, don't write it.

# Project Structure

```
protocol/   Domain types, instructions, smart constructors, instruction metadata
core/       Clearance resolution, completion evaluation, supersession, world model
migration/  Data parsers (apt.dat, OFMX, CIFP) and world-building tools
research/   Formal methods (Lean proofs, TLA+ specs) — not built by Gradle
docs/       Design documents and standards
wiki/       Shared knowledge base — domain knowledge, data sources, design decisions
```

# FM Notes

- When changing `research/fm`, keep `research/fm/README.md`, `research/fm/PROJECT_STATUS.md`, and the active scope note aligned with the actual theorem status.
- Prefer widening FM by small closed slices on the current-shape greenfield boundary; treat older atomic/legacy bridge work as opt-in, not the default path.
- For recurring `research/tools/r1` overnight FM runs, treat `research/fm/r1-smoke/` as the local ignored operations workspace: check the current frontier against `research/fm/lean/`, refresh the local seed snapshot from the current Lean tree, regenerate queue artifacts instead of hand-editing stale queue files, launch the queue detached, watch the first 10-15 minutes for infrastructure or repeated early failures, then leave it alone. Preserve historical `runs/` unless there is a specific reason to reset them.

# Wiki

The `wiki/` directory is a shared knowledge base maintained by both human and AI contributors.

- **Update the wiki** when: completing a stream of work, making a design decision, discovering domain knowledge, ending a session, or committing significant changes.
- **Domain pages** (`wiki/domain/`) are living documents — keep them current.
- **Design decisions** (`wiki/design-decisions/`) are point-in-time records, dated, not updated retroactively. New decisions supersede old ones.
- **Data source pages** (`wiki/data-sources/`) document what we have, what it contains, and known gaps.
- When committing, include wiki updates as part of the commit if relevant content changed.
