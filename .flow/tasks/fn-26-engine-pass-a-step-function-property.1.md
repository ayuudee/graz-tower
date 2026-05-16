---
satisfies: [R1, R2, R3, R4, R5, R6, R7, R8, R9, R10]
---

## Description

Add Kotest property tests for the sim engine's `step()` function. Single task. 4+ property tests (totality, monotonicity, determinism, certifier preservation). Generators seeded from LOWG world candidate.

**Size:** M (~300 LOC across 2-3 new files + 1 modified build.gradle.kts).

**Files**:
- **CREATE**:
  - `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/StepPropertyTest.kt` — Kotest spec with 4+ property tests
  - `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EngineGenerators.kt` — `Arb<SimState>`, `Arb<SimEvent>`, `arbPostFilingState`, `arbPostFilingStatePlusEvent` builders seeded from LOWG
  - `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/RunwayKernelAdapter.kt` (OPTIONAL — may live inline in StepPropertyTest.kt; per plan-review round 3 — extract if helper grows >30 LOC): `buildRunwayKernelInput(state, instruction)` helper that constructs `RunwayKernelInput` from post-state runway/duty/observation/aircraft-phase + the emitted controller instruction
- **MODIFY**:
  - `sim/build.gradle.kts` — add kotest engine + runner + property + assertions to jvmTest deps; register Kotest JUnit5 runner
- **READ ONLY**:
  - `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/certify/RunwayKernelBoundarySpec.kt` — Kotest pattern to mirror
  - `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/certify/RunwayKernel.kt` — R6 oracle
  - `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt` — LOWG loader pattern for seeding generators
  - `gradle/libs.versions.toml` — kotest 5.9.1 declared

## Approach (numbered Steps)

### Step 0 — Baseline + Kotest setup

```bash
git rev-parse HEAD > $TMPDIR/fn-26-1-base-sha.txt
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
    --offline --no-daemon 2>&1 | tee $TMPDIR/fn-26-1-base.log
```

### Step 1 — Add Kotest deps + runner to `sim/build.gradle.kts` (R8)

```kotlin
// Shape only — in jvmTest dependencies block:
jvmTest {
    dependencies {
        // ... existing ...
        implementation(libs.kotest.framework.engine)
        implementation(libs.kotest.assertions.core)
        implementation(libs.kotest.property)
        implementation(libs.kotest.runner.junit5)
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach { useJUnitPlatform() }  // Kotest 5.x runs on JUnit5
```

Verify `tasks.withType<Test> { useJUnitPlatform() }` doesn't already exist (it should — JUnit5 is already used). If `useJUnitPlatform()` is present, Kotest auto-discovers.

### Step 2 — Write `EngineGenerators.kt` (R2)

Seed-from-LOWG strategy:

```kotlin
// SHAPE ONLY — verify against Fixtures.kt + SimState API at impl time:
object EngineGenerators {
    // Load LOWG once; reuse the loaded state as a seed.
    // ACTUAL API (verified per plan-review round 1): Fixtures.<NAME>.load().getOrElse { ... } returns a LoadedFixture
    // with world, worldIndex, standPointId, weatherByAerodrome, initialEvents. Mirror G2CrossAerodromeVfrTest.kt:193 pattern.
    private val baseLoaded by lazy {
        Fixtures.LOWG_LJMB_VFR.load().getOrElse { error("LOWG fixture load failed: $it") }
        // OR use the G0-style fixture: Fixtures.LOWG_TWO_AIRCRAFT (the G1 anchor; G1B4ClosurePinSpec.kt:72 pattern)
        // Pick whichever is simplest; document the choice in evidence.
    }

    fun arbBaseState(): Arb<SimState> = arbitrary { rs ->
        // Construct SimState via SimState.initial(seed, world, worldIndex, aircraft, controllers, weatherByAerodrome).getOrElse { error(...) }
        // Vary the SEED across iterations; keep world/worldIndex/controllers/aircraft fixed.
        SimState.initial(
            seed = rs.random.nextLong(),
            world = baseLoaded.world,
            worldIndex = baseLoaded.worldIndex,
            aircraft = /* listOf(...) from baseLoaded fixture pattern */,
            controllers = /* listOf(ground, tower) from baseLoaded fixture pattern */,
            weatherByAerodrome = baseLoaded.weatherByAerodrome,
        ).getOrElse { error("SimState.initial rejected: $it") }
    }

    fun arbEvent(state: SimState): Arb<SimEvent> = Arb.choice(
        // Each SimEvent subclass gets an Arb. CRITICAL: every event's time MUST satisfy event.time >= state.now
        // (step() has a `require(event.time >= state.now)` boundary at Step.kt:197). Use:
        //   Arb.long(0L..MAX_DELTA_MS).map { state.now + SimDuration.ofMillis(it) }
        // to generate future-only event times.
        arbPilotDecisionTick(state),  // time = state.now + Arb.long(0..30_000).map { SimDuration.ofMillis(it) }
        arbPhysicsTick(state),
        arbControllerCycle(state),
        // Defer harder events (FlightPlanFiled, ProcessInstructionForAircraft, etc.) until property tests reveal value.
    )

    fun arbStatePlusEvent(): Arb<Pair<SimState, SimEvent>> =
        arbBaseState().flatMap { state -> arbEvent(state).map { ev -> state to ev } }

    // R6 needs a post-filing state so ControllerCycle events emit runway instructions (per plan-review round 2)
    fun arbPostFilingState(): Arb<SimState> = arbitrary { rs ->
        val initial = /* arbBaseState().sample(rs).value */
        // Drive through fixture's initialEvents (which include FlightPlanFiled):
        runUntil(
            initial = initial,
            initialEvents = baseLoaded.initialEvents + /* a few ControllerCycle ticks per fixture pattern */,
            until = initial.now + SimDuration.ofMillis(60_000L),  // 60s sim = enough for controllers to load strips
        )
    }

    fun arbPostFilingStatePlusEvent(): Arb<Pair<SimState, SimEvent>> =
        arbPostFilingState().flatMap { state -> arbEvent(state).map { ev -> state to ev } }
}
```

Generators ONLY produce events compatible with the seeded state (e.g. `PilotDecisionTick` references an aircraft that exists in the state). Invalid-input bugs are out of scope — engine fails on invalid input is acceptable; we don't test "engine rejects invalid input cleanly" here.

### Step 3 — Write `StepPropertyTest.kt` (R1, R3-R6)

```kotlin
// SHAPE ONLY — Kotest FunSpec or StringSpec:
class StepPropertyTest : FunSpec({
    test("R3 totality: step never throws") {
        checkAll(EngineGenerators.arbStatePlusEvent()) { (state, event) ->
            // No exception = pass. Wrap in shouldNotThrow if Kotest's default is to fail on throw.
            shouldNotThrowAny { step(state, event) }
        }
    }

    test("R4 monotonicity: newState.now >= state.now") {
        checkAll(EngineGenerators.arbStatePlusEvent()) { (state, event) ->
            val (newState, _) = step(state, event)
            newState.now shouldBeGreaterThanOrEqualTo state.now
            newState.seq shouldBeGreaterThanOrEqualTo state.seq
        }
    }

    test("R5 determinism: step is deterministic") {
        checkAll(EngineGenerators.arbStatePlusEvent()) { (state, event) ->
            val first = step(state, event)
            val second = step(state, event)
            first shouldBe second
        }
    }

    test("R6 conditional runway-kernel preservation (non-vacuous)") {
        // Reshaped per plan-review rounds 1+2+3 — uses arbPostFilingStatePlusEvent so controller runway-instruction
        // emissions are actually reachable (per round-2 vacuity finding). Counts kernel-call branch hits and fails
        // if <5% — non-vacuity is a load-bearing assertion, not just reporting.
        var totalCases = 0
        var kernelCaseHits = 0  // per-case counter (renamed per round-4 nitpick — was kernelCases incremented per instruction)
        checkAll(EngineGenerators.arbPostFilingStatePlusEvent()) { (state, event) ->
            totalCases++
            val (newState, emitted) = step(state, event)
            // Real extraction chain (per plan-review round 2):
            val runwayInstructions = emitted.filterIsInstance<SimEvent.TransmissionStart>()
                .mapNotNull { ts ->
                    val utterance = ts.transmission.utterance
                    if (utterance \!is Utterance.FromController) return@mapNotNull null
                    val output = utterance.output as? ControllerOutput.Instruct ?: return@mapNotNull null
                    val instruction = (output.dispatch as? Dispatch.Direct)?.instruction ?: return@mapNotNull null
                    when (instruction) {
                        is LineUpAndWait, is ClearedForTakeoff, is ClearedToLand -> instruction
                        else -> null
                    }
                }
            if (runwayInstructions.isEmpty()) {
                // no runway operation proposed — kernel preservation N/A
                return@checkAll
            }
            if (runwayInstructions.isNotEmpty()) {
                kernelCaseHits++   // count cases (not instructions) — per plan-review round 4 nitpick
            }
            for (instr in runwayInstructions) {
                val input = buildRunwayKernelInput(newState, instr)  // see RunwayKernelAdapter (sibling fn in this file or new RunwayKernelAdapter.kt)
                val decision = KotlinRunwayKernel.evaluate(input)
                decision.shouldBeInstanceOf<RunwayKernelDecision.Accepted>()
            }
        }
        // Non-vacuity hard gate (per plan-review rounds 2+4 — explicit per-case counter, not per-instruction):
        check(kernelCaseHits >= totalCases / 20) {
            "R6 vacuous: only $kernelCaseHits of $totalCases generated cases reached the runway-kernel branch " +
                "(<5% threshold). The post-filing seed isn't producing runway-instruction-emitting cases. " +
                "Surface as planning defect; do not relax the threshold."
        }
    }
})
```

Mirror `RunwayKernelBoundarySpec.kt`'s assertion style. Per plan-review round 2/3: `KotlinRunwayKernel.evaluate(RunwayKernelInput)` IS proposal-based; build `RunwayKernelInput` from emitted controller instructions. No fallback to a state-validation API exists; if the input-build path doesn't compile, the task needs work, not a documented skip.

### Step 4 — Tune R7 (bounded runtime)

Default Kotest property iteration count is 1000. Cap at 500 via `PropTestConfig(iterations = 500)`:

```kotlin
val propConfig = PropTestConfig(iterations = 500)
test("...", propConfig) { ... }
```

Override via project-specific system property (avoids conflict with Kotest's own `kotest.framework.*` keys):

```kotlin
val iterations = System.getProperty("twr.stepPropertyIterations")?.toIntOrNull() ?: 500
```

Verify default-config test execution stays under 60 seconds (R7 raised per plan-review round 2 — fixture-backed + post-filing-drive properties add real load) by running once with timing.

### Step 5 — Verify (R9)

```bash
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :sim:jvmTest --tests "*StepPropertyTest*" --offline --no-daemon
# Then full verify
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests :migration:allTests detekt \
    --offline --no-daemon
```

Both BUILD SUCCESSFUL. If a property test fails on default seed, that's a real bug — fix in scope OR prove the input was invalid + constrain the generator. Per epic ship policy (round 2): no `@Disabled`, no follow-up epics for default-seed failures; the bug is a blocker.

### Step 6 — `flowctl done` (with bash interpolation per fn-22 R6)

```bash
base_sha="$(cat $TMPDIR/fn-26-1-base-sha.txt)"
implementation_sha="$(git rev-parse HEAD)"
# ... write summary + evidence with interpolated values, then flowctl done.
```

## Acceptance

- [ ] **R1** — StepPropertyTest.kt with 4+ property tests
- [ ] **R2** — EngineGenerators seeded from LOWG world candidate, deterministic per Kotest seed
- [ ] **R3** — Totality: step() never throws
- [ ] **R4** — Monotonicity: now and seq advance
- [ ] **R5** — Determinism: same input → same output
- [ ] **R6** — Conditional runway-kernel preservation: when `step()` emits a TransmissionStart → Utterance.FromController → ControllerOutput.Instruct with a runway instruction (LineUpAndWait / ClearedForTakeoff / ClearedToLand), build a RunwayKernelInput and assert KotlinRunwayKernel.evaluate(...) returns RunwayKernelDecision.Accepted. Non-vacuity: ≥5% of generated cases must reach the kernel-call branch (explicit counter assertion — see Step 3; classify() alone would only report, not enforce).
- [ ] **R7** — Bounded runtime: 500 iterations default, ≤60s (raised per plan-review round 2 — fixture-backed + post-filing-drive add real load); `twr.stepPropertyIterations` system-property override for nightly
- [ ] **R8** — kotest deps + JUnit5 runner in :sim jvmTest
- [ ] **R9** — Full verify green; new property tests pass with default seed. Default-seed failures are blockers (per epic ship policy round 2): fix in scope OR prove input was invalid + constrain generator.
- [ ] **R10** — Diff ≤5 files / ≤600 LOC (per plan-review round 1)

## Key context

- **Generators seed from world candidates** — not from-scratch. LOWG is the default seed; the LJMB candidate (fn-19 fixed) is also available for multi-aerodrome testing if needed.
- **R6 is proposal-based** — `KotlinRunwayKernel.evaluate(RunwayKernelInput)` evaluates a specific proposal (runway instruction). The property extracts emitted controller runway-instruction outputs from `TransmissionStart → Utterance.FromController → ControllerOutput.Instruct → Dispatch.Direct.instruction`. No state-validation fallback exists.
- **kotest-property is already a declared dependency** in `libs.versions.toml`; this epic activates it.
- **Pre-existing dirty state** (research/tools/requirements-spike/, fn-20+23 untracked, research/pdf+txt) MUST NOT be staged.

## Done summary
Added 4 Kotest property tests for the sim engine `step()` function (totality, monotonicity, determinism, conditional runway-kernel preservation) seeded from the LOWG fixture, activating kotest 5.9.1 in `:sim/jvmTest`. The R6 generator drives the sim to the pre-emission state for the first runway instruction so the property reaches the kernel-call branch every cycle; an explicit ≥5% non-vacuity gate hard-fails if the seed regresses.
## Evidence
- Commits: 1260654d536a96c43c7322678f914cfce6490880
- Tests: ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests :migration:allTests detekt --offline --no-daemon
- PRs: