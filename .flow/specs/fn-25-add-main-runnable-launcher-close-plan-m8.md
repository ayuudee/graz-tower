# fn-25 — Add `main()` runnable launcher (close .plan M8)

## Overview

`.plan` M8 — "No main()/runnable launcher" — has been a known friction point since the project began. The codebase is library + tests only; running the sim today requires invoking a JUnit test. The .plan note explicitly suggests "Lowest-friction is a `main()` in `:sim`" (line 446). This epic adds a minimal `sim/src/jvmMain/kotlin/.../Launcher.kt` with `fun main(args: Array<String>)` that:

1. Accepts a path arg to a `world-candidate.json` file.
2. Loads + validates via existing `WorldCandidateLoader.toWorld`.
3. Constructs a minimal `SimState` with a single C172 aircraft on the loaded aerodrome's first stand (using a tiny `jvmMain`-side fixture extracted from the `jvmTest/testing/Fixtures.kt` pattern).
4. Runs `runUntil(...)` for 1800 sim seconds (30 sim minutes).
5. Prints a one-line summary of the resulting state (aircraft phase, position, transmissions count).

This is the **lowest-friction launcher** that .plan M8 anticipated. It does NOT replicate the full Fixtures.kt scenario authoring DSL — that stays in `jvmTest`. The launcher is a smoke-runner: load a world, run a single aircraft, print "the sim runs." Anyone — agent or human — can now exercise the runtime end-to-end without a test harness.

## Boundaries / non-goals

- **Out: multi-aircraft scenarios.** Single C172 only. Multi-aircraft authoring is a future extension (or `Fixtures.kt`-as-DSL, separately).
- **Out: multi-aerodrome / merged worlds.** Single world-candidate.json only. `WorldCandidateLoader.mergeAviationWorlds` is library-callable but not exposed via `main()` in v1.
- **Out: CLI argument framework.** No `clikt` / `kotlinx-cli` / etc. — `args[0]` is the path, no flags. Adding a flag framework is its own follow-up.
- **Out: real-time tick pacing.** The launcher runs as fast as the event loop processes; no sleep / clock-pacing. Useful for smoke testing, not for live demo.
- **Out: Gradle `application` plugin.** A single `JavaExec` task (`:sim:runLauncher`) suffices for v1 (see R6); the application plugin can be added separately if launcher use grows.
- **Out: extracting `Fixtures.kt` to `commonMain` / `jvmMain` proper.** Only the minimum slice needed for the launcher (1 aircraft on 1 stand) gets a `jvmMain`-side helper; the broader fixture API stays in `jvmTest`.

## Strategy Alignment

Active tracks served by this plan:
- **Runtime simulator** — closes a known friction point. The "students see the runtime" claim in the strategy is harder to back when the runtime can only be exercised through tests. A real `main()` makes the sim demonstrable.
- **Reviewer / agent infrastructure** — agents can run the sim end-to-end as part of their workflow (e.g. autonomous adversarial loop OR-3, if it ever lights up, needs to be able to spin up the runtime; today it can't).

## Decision context

**Why `:sim` and not a new `:app` module**: per .plan M8 line 446-447, "Lowest-friction is a `main()` in `:sim`." Creating a new module adds build-graph overhead for a single-file launcher; `:sim` already has `jvmMain` source root (carries `Driver.kt`, `HeapEventQueue.kt`). The launcher belongs next to those.

**Why a tiny `jvmMain` fixture instead of reusing `jvmTest/testing/Fixtures.kt`**: the test fixtures live in `jvmTest` for good reason (they're test-fixtures, exercise `kotlin-test` assertions, depend on `:migration` for `WorldCandidateLoader`). The launcher needs only a minimal "build a SimState with one aircraft" — extracting that slice to `jvmMain` keeps the launcher self-contained without dragging `jvmTest` dependencies into the runtime build graph.

**Why 1800 sim seconds**: matches the typical golden-test duration (G0 / G1 / G3a goldens run for ~1300-1500 sim seconds). One aircraft completing a circuit takes ~600-1000 sim seconds; 1800 gives margin without being so long the launcher seems hung.

## Acceptance

- **R1:** New file `sim/src/jvmMain/kotlin/xyz/easiersaid/twr/sim/Launcher.kt` defines `fun main(args: Array<String>)` that takes `args[0]` as the path to a `world-candidate.json` file. If `args.isEmpty()` or the path doesn't exist, print usage and exit non-zero.
- **R2:** Launcher calls `WorldCandidateLoader.toWorld(json.decodeFromString<WorldCandidateDocument>(Files.readString(path)))` to load the world. Loader exceptions surface as program-level errors (no swallowed exceptions; the `Either` / typed errors from the loader print to stderr + exit non-zero if applicable).
- **R3** (hardened per plan-review rounds 1+2 — codex findings "empty initialEvents" + "shape didn't match LowgGoldenTest API"): Launcher constructs a minimal `LauncherScenario(initial: SimState, initialEvents: List<SimEvent>, aircraftId: AircraftId, primaryAerodrome: AerodromeId)` using the **EXACT shapes from `LowgGoldenTest.kt:118-142`** (the current G0 anchor):
  - Mission: `createMission(goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)), startPhase = PilotPhase.AtStand, time = now, ...)` — typed-outcome shape per fn-11.1.
  - Aircraft: `AircraftState(id, callsign = Callsign("OEABC"), position = ..., positionPoint = ..., phase = PilotPhase.AtStand, ...)` per LowgGoldenTest:125-133.
  - SimState: `SimState.initial(seed = 42L, world, worldIndex, aircraft = listOf(aircraft), controllers = listOf(ground, tower), weatherByAerodrome = ...).getOrElse { error(...) }` — returns `Either`-like, MUST use `.getOrElse { error(...) }`.
  - Bootstrap events from `LowgGoldenTest.kt:184-191`: `AtisIssued` + `PilotDecisionTick` + `PhysicsTick` + `ControllerCycle` for each controller, plus the `FlightPlanFiled` event from `loaded.initialEvents`.
  - **FlightPlanFiled routing**: do NOT use `aerodrome.icao` as recipient. Use the same routing the loader-provided `initialEvents` would have produced (a controller — typically GROUND staffing the aircraft's departure). If `WorldCandidateLoader` returns a `Loaded` with `initialEvents`, take the `FlightPlanFiled` from there (`loaded.initialEvents.filterIsInstance<SimEvent.FlightPlanFiled>()`). Otherwise construct via `FiledPlan.Vfr(...)` and route to the actual ground controller by looking up `state.controllers.values.first { it.roles.contains(RoleName.GROUND) }`.
  - **Worker latitude**: the launcher fixture's implementation literally mirrors the LowgGoldenTest construction; if any field above doesn't exist (e.g. the actual `SimState.initial` signature has a different parameter set on this branch), match the test verbatim — it's the source of truth, not this spec. The fixture extraction lives in `sim/src/jvmMain/kotlin/xyz/easiersaid/twr/sim/LauncherFixture.kt`. **Reuses existing `SimState.initial` smart constructor**; does not duplicate validation.
- **R4:** Launcher calls `runUntil(scenario.initial, scenario.initialEvents, SimTime.millis(1_800_000))` (1800 sim seconds = 30 sim minutes). Captures the final `SimState`. `initialEvents` is non-empty (per R3); empty bootstrap means the sim never runs.
- **R5** (hardened per plan-review round 1 — codex finding "event count was a placeholder; SimState exposes seq + nextTransmissionId"): Launcher prints a one-line summary to stdout using REAL `SimState` fields: `"OK — aerodrome=$icao | aircraft=$callsign | phase=$phase | now=${final.now} | seq=${final.seq} | transmissions=${final.nextTransmissionId}"`. `seq` is the monotonic event counter (every event-emit advances it; reaches the thousands during a real 1800s run); `nextTransmissionId` counts total transmissions issued (radio activity; >0 proves the controller + pilot interacted). Format is human-readable but greppable. Exit 0 on success.
- **R6** (hardened per plan-review round 1 — codex finding "documented invocations not valid"): Add a minimal Gradle `JavaExec` task `runLauncher` in `sim/build.gradle.kts` that invokes `xyz.easiersaid.twr.sim.LauncherKt` against a path. The task takes the path via a Gradle property: `./gradlew :sim:runLauncher -Pworld=cad/airports/rendered/lowg/world-candidate.json`. The task depends on `:sim:jvmJar` (or equivalent) to assemble the runtime classpath. Avoids the `application` plugin (no plugin-level config; one custom task). The `.plan` M8 closure note documents this exact invocation.
- **R7:** `.plan` M8 → `DONE (2026-05-16)` with one-line closure summary + the invocation example. Original narrative preserved per .plan maintenance rule (one-week traceability).
- **R8** (hardened per plan-review round 1 — codex finding "smoke test doesn't verify the sim actually ran"): New test `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LauncherSmokeTest.kt` invokes `main(...)` programmatically against `cad/airports/rendered/lowg/world-candidate.json`. Asserts FOUR load-bearing things from stdout: (1) starts with `"OK — "`; (2) contains `aerodrome=LOWG`; (3) `now=` field is non-zero (parses the `now=` value and asserts > `SimTime.ZERO`'s string repr — sim time advanced); (4) `transmissions=` field is `> 0` (controller + pilot interacted via radio). Plus `seq=` > some lower bound (e.g. 100) to confirm the event loop processed real work. This is a smoke test, not a golden — but the four assertions DO prove the sim ran, not just that `main()` returned cleanly.
- **R9:** Full verify GREEN: `./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests :migration:allTests detekt --offline --no-daemon` exits 0. Two-invocation split (per fn-19 / fn-24 precedent) — non-migration suite + `:migration:allTests` separately. Nine sim goldens GREEN. Detekt baseline unchanged. R14-Passed style — no NEW regressions.
- **R10:** Diff scope: 3 new files (`Launcher.kt`, `LauncherFixture.kt`, `LauncherSmokeTest.kt`) + 2 modified files (`.plan` for R7 + `sim/build.gradle.kts` for R6 `runLauncher` task + `:migration` `jvmMain` dep). Total ≤6 files, ≤250 LOC.

## Early proof point

Task `fn-25-add-main-runnable-launcher-close-plan-m8.1` validates the launcher by running its smoke test (R8). If the smoke test fails — e.g. `WorldCandidateLoader.toWorld` doesn't produce a state that the existing `runUntil` accepts — the launcher's shape is wrong; the right fix is to surface what the test-fixtures do that the launcher fixture doesn't.

## Quick commands

```bash
# Pre-task baseline
git rev-parse HEAD > $TMPDIR/fn-25-1-base-sha.txt
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
    --offline --no-daemon 2>&1 | tee $TMPDIR/fn-25-1-base-non-migration.log

# Launcher smoke (post-task)
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :sim:jvmTest --tests "*LauncherSmokeTest*" --offline --no-daemon

# Post-task verify (two-invocation)
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
    --offline --no-daemon 2>&1 | tee $TMPDIR/fn-25-1-post-non-migration.log
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :migration:allTests --offline --no-daemon 2>&1 | tee $TMPDIR/fn-25-1-post-migration.log
```

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | Launcher.kt with fun main() taking args[0] path | fn-25-add-main-runnable-launcher-close-plan-m8.1 | — |
| R2  | WorldCandidateLoader.toWorld integration | fn-25-add-main-runnable-launcher-close-plan-m8.1 | — |
| R3  | LauncherFixture: 1× C172 at first stand | fn-25-add-main-runnable-launcher-close-plan-m8.1 | — |
| R4  | runUntil for 1800 sim seconds | fn-25-add-main-runnable-launcher-close-plan-m8.1 | — |
| R5  | One-line summary print to stdout | fn-25-add-main-runnable-launcher-close-plan-m8.1 | — |
| R6  | Documented invocation command | fn-25-add-main-runnable-launcher-close-plan-m8.1 | Gradle `application` plugin OUT per Boundaries |
| R7  | .plan M8 → DONE with closure summary | fn-25-add-main-runnable-launcher-close-plan-m8.1 | — |
| R8  | LauncherSmokeTest invokes main() against LOWG candidate | fn-25-add-main-runnable-launcher-close-plan-m8.1 | — |
| R9  | Full verify green (two-invocation) | fn-25-add-main-runnable-launcher-close-plan-m8.1 | — |
| R10 | Diff scope ≤6 files / ≤250 LOC (per round 1: build.gradle.kts edit + Gradle task add) | fn-25-add-main-runnable-launcher-close-plan-m8.1 | — |

## Review considerations

- **FP / type safety**: the launcher must respect `WorldCandidateLoader`'s typed-error return shape if it returns `Either<LoadError, AviationWorld>` (verify at task time). If it throws, that's fine — `main()` can let exceptions propagate to stderr; the JVM produces a stack trace and exits non-zero, which is acceptable for a smoke launcher. **Reviewer focus**: confirm no swallowed exceptions; if the loader returns `Either`, `getOrElse { error("...") }` is acceptable for v1.
- **Test architecture**: the smoke test programmatically invokes `main()`, captures stdout (via `System.setOut`/`PrintStream` or a kotlinx-coroutines-based capture if needed), and asserts the "OK —" prefix. **Reviewer focus**: confirm stdout capture doesn't break parallel test execution (single-threaded test should be fine; if the test framework runs in parallel, sync on a lock).
- **Impact**: scoped to `:sim/jvmMain` + 1 test + `.plan`. No production runtime behavior change. No controller / pilot / world-model surface changes. **Reviewer focus**: confirm `:sim/commonMain` is untouched — the launcher is JVM-only.
- **Operational ATC correctness / applicability**: the launcher runs the existing event-loop with the existing controller / pilot machinery. No new ATC semantics. **Reviewer focus**: confirm the 1800 sim-second duration doesn't accidentally exercise an unreviewed code path (e.g. mission completion + cleanup that goldens don't normally hit at this duration).

## References

- `.plan:442-448` — M8 narrative
- `sim/src/jvmMain/kotlin/xyz/easiersaid/twr/sim/Driver.kt` — `runUntil` entry point
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt:105` — `object Fixtures` (the pattern the LauncherFixture mirrors)
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateLoader.kt` — world loader
- `cad/airports/rendered/lowg/world-candidate.json` — the smoke-test target world
- Strategy: Runtime simulator track (the launcher closes a friction point for "students see the runtime")
