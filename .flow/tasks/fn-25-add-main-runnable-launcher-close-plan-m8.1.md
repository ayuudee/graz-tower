---
satisfies: [R1, R2, R3, R4, R5, R6, R7, R8, R9, R10]
---

## Description

Add a minimal JVM `main()` to `:sim` that loads a `world-candidate.json`, constructs a single C172 aircraft, runs the existing event loop for 1800 sim seconds, and prints a one-line summary. Closes `.plan` M8.

**Size:** S-M (~200 LOC across 3 new files + 2 modified files — `.plan` + `sim/build.gradle.kts`).

**Files**:
- **CREATE**:
  - `sim/src/jvmMain/kotlin/xyz/easiersaid/twr/sim/Launcher.kt` — `fun main(args: Array<String>)` (R1-R5)
  - `sim/src/jvmMain/kotlin/xyz/easiersaid/twr/sim/LauncherFixture.kt` — minimal `internal fun buildSingleAircraftState(world): SimState` helper (R3)
  - `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LauncherSmokeTest.kt` — programmatic invocation + stdout assertion (R8)
- **MODIFY**:
  - `.plan` — M8 → DONE with closure summary + invocation example (R7)
  - `sim/build.gradle.kts` — add `:migration` to `jvmMain` deps + register `runLauncher` JavaExec task (R6)
- **READ ONLY**:
  - `sim/src/jvmMain/kotlin/xyz/easiersaid/twr/sim/Driver.kt` — `runUntil` signature
  - `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt` — `object Fixtures` pattern for `buildSingleAircraftState`
  - `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateLoader.kt` — loader contract
  - `cad/airports/rendered/lowg/world-candidate.json` — smoke test target

## Approach (numbered Steps)

### Step 0 — Baseline capture (BEFORE any edits)

```bash
git rev-parse HEAD > $TMPDIR/fn-25-1-base-sha.txt
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
    --offline --no-daemon 2>&1 | tee $TMPDIR/fn-25-1-base-non-migration.log
# Expected: BUILD SUCCESSFUL.
```

### Step 1 — Read existing surfaces (Driver, Fixtures, WorldCandidateLoader)

- `sim/jvmMain/.../Driver.kt`: `runUntil(initial: SimState, initialEvents: List<SimEvent>, until: SimTime, queueFactory: () -> EventQueue = ::HeapEventQueue): SimState`. Library-internal.
- `sim/jvmTest/testing/Fixtures.kt:105 object Fixtures`: scenario authoring DSL. Read enough to extract a minimal "one C172 at first stand, intent: complete one circuit" fixture for `jvmMain`.
- `migration/commonMain/.../WorldCandidateLoader.kt`: `toWorld(document: WorldCandidateDocument)`; return type may be `AviationWorld` directly or `Either<LoadError, AviationWorld>` — verify and adapt.

### Step 2 — Write `LauncherFixture.kt` (R3 — returns LauncherScenario with bootstrap events)

Create `sim/src/jvmMain/kotlin/xyz/easiersaid/twr/sim/LauncherFixture.kt`. Return a `LauncherScenario` data class with the initial SimState AND the bootstrap events the sim needs to actually run (verified pattern: `LowgGoldenTest.kt:185-191` adds `AtisIssued` + `PilotDecisionTick` + `PhysicsTick` + `ControllerCycle` for each controller, plus an existing `FlightPlanFiled` event from the loader's `initialEvents`).

**Critical**: empty `initialEvents` means `runUntil`'s `drive()` returns immediately. The launcher MUST seed boot events.

**The source of truth is `LowgGoldenTest.kt:118-191` — mirror its construction VERBATIM**, adapting only the callsign (`OEABC` is fine; the launcher isn't competing with goldens). The shape (annotated for the worker; field signatures match LowgGoldenTest exactly):

```kotlin
// SHAPE ONLY — verify against LowgGoldenTest:118-191 at implementation time:
data class LauncherScenario(
    val initial: SimState,
    val initialEvents: List<SimEvent>,
    val aircraftId: AircraftId,
    val primaryAerodrome: AerodromeId,
)

internal fun buildLauncherScenario(loaded: LoadedWorld /* whatever WorldCandidateLoader returns */): LauncherScenario {
    // Pick the first runway-bearing aerodrome with a usable stand.
    val aerodrome = loaded.world.aerodromes.values.firstOrNull { it.runways.isNotEmpty() && it.stands.isNotEmpty() }
        ?: error("No runway-bearing aerodrome with at least one stand in the world")
    // Mission — match LowgGoldenTest:118-124 typed-outcome shape:
    val now = SimTime.ZERO
    val aircraftId = AircraftId("DEMO-1")
    val mission = createMission(
        goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        startPhase = PilotPhase.AtStand,
        time = now,
        // ... other createMission params per LowgGoldenTest
    )
    // Aircraft — match LowgGoldenTest:125-133:
    val stand = aerodrome.stands.values.first()
    val aircraft = AircraftState(
        id = aircraftId,
        callsign = Callsign("OEABC"),  // demo callsign
        position = loaded.world.geometry.points.getValue(/* stand point id */),
        positionPoint = /* stand point id */,
        phase = PilotPhase.AtStand,
        // ... other AircraftState params per LowgGoldenTest
    )
    // Controllers — pull from the loaded world's staffing (LowgGoldenTest builds `ground` + `tower`)
    val controllers = /* loaded.controllers or built from loaded.world's role mappings */
    // SimState — match LowgGoldenTest:135-142, getOrElse on the Either-like return:
    val initialState = SimState.initial(
        seed = 42L,
        world = loaded.world,
        worldIndex = loaded.worldIndex,
        aircraft = listOf(aircraft),
        controllers = controllers,
        weatherByAerodrome = /* loaded.weather or default */,
        // ... other SimState.initial params per LowgGoldenTest
    ).getOrElse { error("SimState.initial rejected the launcher fixture: $it") }
    // Bootstrap events — match LowgGoldenTest:184-191 (use loaded.initialEvents for the FlightPlanFiled):
    val atis = /* minimal Atis or loaded.atisByAerodrome[aerodrome.icao] if present */
    val initialEvents = loaded.initialEvents + listOf(
        SimEvent.AtisIssued(time = now, aerodrome = aerodrome.icao, atis = atis),
        SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
        SimEvent.PhysicsTick(time = now),
    ) + initialState.controllers.keys.map { SimEvent.ControllerCycle(time = now, controllerId = it) }
    return LauncherScenario(initialState, initialEvents, aircraftId, aerodrome.icao)
}
```

**FlightPlanFiled routing**: take it from `loaded.initialEvents.filterIsInstance<SimEvent.FlightPlanFiled>()` if the loader provides it (the G0 fixture does — see `LowgGoldenTest.kt:162-167`). The launcher should NOT reconstruct a FlightPlanFiled from scratch; the loader's pre-built event has the right recipient routing.

**Worker latitude**: where the spec says `/* loaded.X */` or `/* per LowgGoldenTest */`, the implementer reads the test file at that line range and copies the construction. The plan deliberately defers exact field signatures because they may have evolved since planning; LowgGoldenTest is the source of truth, not this spec. The fixture should land at ~80-120 LOC.

### Step 3 — Write `Launcher.kt` (R1, R2, R4, R5)

Create `sim/src/jvmMain/kotlin/xyz/easiersaid/twr/sim/Launcher.kt`:

```kotlin
// SHAPE ONLY — not implementation:
package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.migration.world.WorldCandidateDocument
import xyz.easiersaid.twr.migration.world.WorldCandidateLoader
import xyz.easiersaid.twr.protocol.SimTime

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage: launcher <path-to-world-candidate.json>")
        kotlin.system.exitProcess(1)
    }
    val path = Paths.get(args[0])
    if (\!Files.exists(path)) {
        System.err.println("Error: file not found: $path")
        kotlin.system.exitProcess(1)
    }
    val json = Json { ignoreUnknownKeys = true; isLenient = true }    // match WorldCandidateLoader's Json config — verify at task time
    val document = json.decodeFromString<WorldCandidateDocument>(Files.readString(path))
    val world = WorldCandidateLoader.toWorld(document)               // adapt if Either-returning
    val scenario = buildLauncherScenario(world)
    val final = runUntil(
        initial = scenario.initial,
        initialEvents = scenario.initialEvents,                       // non-empty per R3 — sim actually runs
        until = SimTime.millis(1_800_000L),                          // 1800 sim seconds = 30 sim minutes
    )
    val aircraft = final.aircraft[scenario.aircraftId]
    println(
        "OK — aerodrome=${scenario.primaryAerodrome.id} | " +
            "aircraft=${aircraft?.callsign?.value ?: "(none)"} | " +
            "phase=${aircraft?.phase ?: "(none)"} | " +
            "now=${final.now} | " +
            "seq=${final.seq} | " +
            "transmissions=${final.nextTransmissionId}"
    )
    // seq advances on every event; nextTransmissionId increments per transmission issued.
}
```

The exact field names (`callsign.value`, `final.now`, `final.eventCount`) are placeholders — verify against `SimState` / `AircraftState` types at task time. The one-line stdout format is the load-bearing R5 contract.

`dependencies`: the launcher needs `:migration` for `WorldCandidateLoader` + `WorldCandidateDocument`. Add `implementation(project(":migration"))` to `sim/build.gradle.kts`'s `jvmMain` block (currently absent — verify).

### Step 4 — Update `sim/build.gradle.kts` for `:migration` dependency + `runLauncher` task (R6)

Two changes in `sim/build.gradle.kts`:

**a) `:migration` dependency at `jvmMain` scope** (currently only at `jvmTest`):
```kotlin
jvmMain {
    dependencies {
        implementation(libs.kotlinx.serialization.json)
        implementation(project(":migration"))     // NEW for the launcher
    }
}
```
If adding creates circular build-graph (`:migration` → `:sim`), surface immediately. Most likely fine: `:sim` → `:migration` (sim consumes migration for parsing).

**b) `runLauncher` JavaExec task** (R6 — real invocation):
```kotlin
// At task-DSL level in sim/build.gradle.kts:
tasks.register<JavaExec>("runLauncher") {
    group = "application"
    description = "Run the sim launcher against a world-candidate.json. Usage: ./gradlew :sim:runLauncher -Pworld=<path>"
    classpath = kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles +
                kotlin.jvm().compilations.getByName("main").output.allOutputs
    mainClass.set("xyz.easiersaid.twr.sim.LauncherKt")
    args = listOf(project.findProperty("world") as? String ?: error("Usage: -Pworld=<path-to-world-candidate.json>"))
}
```

Exact classpath wiring may need adjustment depending on KMP plugin version; the goal is `mainClass = LauncherKt` + `:migration` runtime jar on classpath. If the classpath expression doesn't compile, simplify to `sourceSets["jvmMain"].runtimeClasspath` or equivalent — the load-bearing claim is "the gradle task exists and runs the launcher with the migration jar available."

### Step 5 — Write `LauncherSmokeTest.kt` (R8 — four load-bearing assertions)

Create `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LauncherSmokeTest.kt`:

```kotlin
// SHAPE ONLY — not implementation:
class LauncherSmokeTest {
    @Test
    fun mainLoadsLowgRunsSimAndPrintsOk() {
        val originalOut = System.out
        val baos = ByteArrayOutputStream()
        System.setOut(PrintStream(baos))
        try {
            main(arrayOf("cad/airports/rendered/lowg/world-candidate.json"))
        } finally {
            System.setOut(originalOut)
        }
        val output = baos.toString()
        // (1) prefix
        assertTrue(output.startsWith("OK — "), "Launcher output should start with 'OK —'; got: $output")
        // (2) aerodrome
        assertTrue(output.contains("aerodrome=LOWG"), "Expected LOWG aerodrome; got: $output")
        // (3) now advanced (sim actually ran)
        val nowMatch = Regex("now=SimTime\\(millis=([0-9]+)\\)").find(output) // adjust regex to actual SimTime toString
        assertTrue(nowMatch \!= null && nowMatch.groupValues[1].toLong() > 0L,
            "Expected non-zero 'now=...' indicating sim advanced; got: $output")
        // (4) transmissions > 0 (controller + pilot interacted via radio)
        val txMatch = Regex("transmissions=([0-9]+)").find(output)
        assertTrue(txMatch \!= null && txMatch.groupValues[1].toLong() > 0L,
            "Expected transmissions > 0 indicating radio activity; got: $output")
        // Optionally: (5) seq > 100 (event loop processed real work)
        val seqMatch = Regex("seq=([0-9]+)").find(output)
        assertTrue(seqMatch \!= null && seqMatch.groupValues[1].toLong() > 100L,
            "Expected seq > 100 indicating event loop ran; got: $output")
    }
}
```

The regex shapes (`SimTime(millis=...)`) depend on `SimTime.toString()`'s actual format — verify against the type at task time and adjust. The load-bearing claim is: stdout matches the shape AND the sim actually advanced its state (now > 0 + transmissions > 0 + seq > threshold).

**Potential gotcha**: if `kotlin.system.exitProcess` is reached (e.g. file-not-found case), the test process dies. The smoke test should only exercise the success path; if implementation reaches `exitProcess` only on error, the smoke test never hits it.

### Step 6 — Update `.plan` M8 to DONE (R7)

In `.plan` line 442-448, prepend a DONE line above the existing narrative:

```
**M8 — No main()/runnable launcher** — DONE (2026-05-16)
fn-25 added `sim/src/jvmMain/kotlin/.../Launcher.kt` with `fun main(args: Array<String>)`.
Run: `./gradlew :sim:runLauncher -Pworld=cad/airports/rendered/lowg/world-candidate.json` (the new Gradle JavaExec task added in fn-25.1). Or run the smoke test: `./gradlew :sim:jvmTest --tests "*LauncherSmokeTest*"`.

<original 7-line narrative preserved below>
```

(One-week traceability rule preserved.)

### Step 7 — Verify (R9)

```bash
# Targeted launcher smoke
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :sim:jvmTest --tests "*LauncherSmokeTest*" --offline --no-daemon

# Full verify (two invocations per fn-19/fn-24 precedent)
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
    --offline --no-daemon 2>&1 | tee $TMPDIR/fn-25-1-post-non-migration.log
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :migration:allTests --offline --no-daemon 2>&1 | tee $TMPDIR/fn-25-1-post-migration.log
```

Both BUILD SUCCESSFUL. Nine sim goldens GREEN (now ten — `LauncherSmokeTest` joins as the tenth sim test that exercises the runtime, though it's a smoke not a golden). Detekt unchanged.

### Step 8 — `flowctl done`

Compute concrete values + interpolate (per fn-22 R6 flowctl-done state-sync discipline):

```bash
base_sha="$(cat $TMPDIR/fn-25-1-base-sha.txt)"
implementation_sha="$(git rev-parse HEAD)"

cat > $TMPDIR/fn-25-1-summary.md <<EOF2
fn-25.1 shipped: Launcher.kt added in :sim/jvmMain with fun main() that loads world-candidate.json, builds 1× C172 at first stand, runs 1800 sim seconds, prints one-line summary. LauncherSmokeTest exercises the path against LOWG. .plan M8 → DONE. Implementation commit ${implementation_sha}.
EOF2

cat > $TMPDIR/fn-25-1-evidence.json <<EOF2
{
  "task": "fn-25-add-main-runnable-launcher-close-plan-m8.1",
  "base_sha": "${base_sha}",
  "implementation_sha": "${implementation_sha}",
  "files_created": ["sim/src/jvmMain/kotlin/xyz/easiersaid/twr/sim/Launcher.kt", "sim/src/jvmMain/kotlin/xyz/easiersaid/twr/sim/LauncherFixture.kt", "sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LauncherSmokeTest.kt"],
  "files_modified": "<from git diff at task close>",
  "plan_M8_status": "DONE",
  "verify_outcome": "non-migration green; :migration:allTests green; smoke test green"
}
EOF2

.flow/bin/flowctl done fn-25-add-main-runnable-launcher-close-plan-m8.1 \
  --summary-file $TMPDIR/fn-25-1-summary.md \
  --evidence-json $TMPDIR/fn-25-1-evidence.json --json
```

Post-done state-sync sweep (per fn-22 R6 lesson): confirm Done summary + Evidence carry the interpolated values; confirm task .json status=done before completion review.

## Acceptance

- [ ] **R1** — `Launcher.kt` with `fun main(args: Array<String>)`; usage + exit-1 on missing/invalid args.
- [ ] **R2** — `WorldCandidateLoader.toWorld(...)` called; exceptions surface (no swallowing).
- [ ] **R3** — `LauncherFixture` builds single C172 at first stand using `SimState.initial`.
- [ ] **R4** — `runUntil` called with `SimTime.millis(1_800_000)`.
- [ ] **R5** — One-line "OK — ..." summary on stdout; exit 0.
- [ ] **R6** — `sim/build.gradle.kts` has a `runLauncher` JavaExec task; `./gradlew :sim:runLauncher -Pworld=<path>` works; `.plan` M8 closure note documents this exact invocation.
- [ ] **R7** — `.plan` M8 → DONE; original narrative preserved.
- [ ] **R8** — `LauncherSmokeTest` programmatically invokes main(); asserts FOUR load-bearing things: "OK —" prefix, `aerodrome=LOWG`, `now > 0`, `transmissions > 0` (plus seq > threshold for extra confidence). Proves the sim ran, not just that main() returned.
- [ ] **R9** — Two-invocation full verify both GREEN; nine sim goldens GREEN; detekt unchanged.
- [ ] **R10** — Diff scope ≤6 files / ≤250 LOC (3 new + 2 modified: build.gradle.kts edit + Gradle task add + .plan M8 update).

## Key context

- This is the LOWEST-FRICTION launcher per .plan M8's prescription. Resist scope creep — no CLI framework, no multi-aircraft, no real-time pacing.
- Smoke test asserts the launcher doesn't crash and prints the right shape. Semantic correctness is the goldens' job, not the launcher's.
- If `WorldCandidateLoader.toWorld` returns `Either`, use `getOrElse { error(...) }` for v1 — typed-error UX can land later.
- Pre-existing dirty state (research/tools/requirements-spike/, fn-20 + fn-23 untracked, research/pdf+txt) MUST NOT be staged.
- Codex sandbox: clone `$HOME/.gradle/{caches,native,wrapper}` to `$TMPDIR/gradle-user-home`; `_JAVA_OPTIONS=-Djava.io.tmpdir=$TMPDIR`; JAVA_HOME=`/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8`.

## Done summary

_(filled by `flowctl done` at task close — see Step 8)_

## Evidence

_(filled by `flowctl done` at task close — see Step 8)_
