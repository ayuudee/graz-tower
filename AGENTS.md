# Commandments

These are non-negotiable. Every agent — main conversation, subagent, review agent — must follow them. **If you are a subagent, confirm you have read the commandments before proceeding.**

## 1. No corners cut

Do not take shortcuts that defer problems, hide failures, or create future surprises. If something cannot be done correctly right now, either do it correctly or make the incomplete state **loudly visible** (throw an exception, fail the test, leave a compile error). Silent workarounds — skip lists, `@Suppress`, `TODO` comments that disable checks, catch-all `else` branches that swallow unexpected cases — are forbidden.

**Test example**: if a new production rule isn't covered by an exhaustive condition-space test, expand the condition space. Do not add the rule ID to an exclusion list.

**Code example**: if a sealed `when` gains a new branch, handle it. Do not add `else -> Unit` or `else -> null`. If the handler isn't written yet, `else -> error("${branch::class.simpleName} not yet handled")`.

## 2. No half-baked work

Every commit must leave the codebase in a state where all tests pass and no known-incorrect behavior is silently accepted. If a feature is partially implemented, the unfinished part must fail loudly — not pass silently by being excluded from checks.

## 3. Throw on the unimplemented

When code encounters a state it genuinely cannot handle AND the state is **provably impossible at the type level** (see Commandment 8), it must throw. `error()` is correct for states a well-typed caller cannot construct. Returning `null`, `emptyList()`, or a no-op is only correct when the absence is a defined, documented part of the domain.

If the type system allows the state but the code doesn't handle it yet, use typed errors (`Either<NotYetImplemented, T>`) or explicitly documented no-ops — not `error()`. The distinction: `error()` means "impossible," `Left(NotYetImplemented)` means "possible but deferred."

## 4. Tests prove the real job

Tests must exercise real behavior, not structural properties that the type system or compiler already guarantees. If a test can only pass by checking something the code already enforces, it adds no confidence and should not exist. When a test needs to exclude cases, that is a signal that the test's scope is wrong — fix the scope, don't carve exceptions.

## 5. The pilot owns the plan

The pilot agent receives a high-level goal and plans how to achieve it. The test harness does not decompose the goal, stitch phases together, or swap goals mid-flight. If the test needs to do this, the pilot's planning capability is incomplete — fix the pilot, don't work around it in the test.

## 6. Protocol is source of truth

Controller and pilot behavior must be traceable to ATC regulations (ICAO, SERA, CAP 413). Every rule carries regulation references. Every pilot transmission follows standard phraseology. Invented behaviors that have no regulatory basis are bugs, not features.

## 7. Cite your sources for law and phraseology

When making claims about ATC law, regulations, or RT phraseology, always provide the specific source: document, edition/date, section/paragraph. For example: "CAP 413 para 4.50" or "ICAO Doc 9432 §5.9.5". Do not state regulatory facts without a citation. Once cited, verify the citation is accurate — check the actual text if available in `research/txt/` or the wiki. An uncited regulatory claim is an unverified claim.

## 8. Dead programs tell no lies

If the program reaches a state it genuinely cannot handle, it must crash — not silently recover, not return a plausible default, not log and continue. A crash with a clear message is infinitely more useful than silent corruption. `error()` is correct for **provably impossible** states.

However: **if the type system allows a state, it is reachable**. Do not use `error()` for states that are merely unused or not-yet-exercised — that is a lie about impossibility. If you believe a state is impossible, make it **unrepresentable in the types** (sealed hierarchies, non-nullable fields, smart constructors). If the types allow it, handle it — either functionally (`Either`, `Option`) or by documenting the operational semantics of the "unexpected" case.

The test: before writing `error("X should not happen")`, ask: "could a well-typed caller construct this input?" If yes, it CAN happen, and `error()` is wrong.

---

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
