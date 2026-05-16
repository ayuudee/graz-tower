---
satisfies: [R1, R2, R3, R4, R5, R6, R7, R8, R9, R10]
---

## Description

Add Kotest property tests for the sim engine's `step()` function. Single task. 4+ property tests (totality, monotonicity, determinism, certifier preservation). Generators seeded from LOWG world candidate.

**Size:** M (~300 LOC across 2-3 new files + 1 modified build.gradle.kts).

**Files**:
- **CREATE**:
  - `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/StepPropertyTest.kt` — Kotest spec with 4+ property tests
  - `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EngineGenerators.kt` — `Arb<SimState>`, `Arb<SimEvent>` builders seeded from LOWG
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

tasks.withType<Test> {
    useJUnitPlatform()  // Kotest 5.x runs on JUnit5
}
```

Verify `tasks.withType<Test> { useJUnitPlatform() }` doesn't already exist (it should — JUnit5 is already used). If `useJUnitPlatform()` is present, Kotest auto-discovers.

### Step 2 — Write `EngineGenerators.kt` (R2)

Seed-from-LOWG strategy:

```kotlin
// SHAPE ONLY — verify against Fixtures.kt + SimState API at impl time:
object EngineGenerators {
    // Load LOWG once; reuse the loaded state as a seed.
    private val baseLoaded by lazy {
        Fixtures.LOWG.loadFor(/* default G0-style fixture */)
    }

    fun arbBaseState(): Arb<SimState> = arbitrary { rs ->
        val base = baseLoaded.toBaseSimState(seed = rs.random.nextLong())
        // Vary the seed; keep world/worldIndex/controllers fixed
        base
    }

    fun arbEvent(state: SimState): Arb<SimEvent> = Arb.choice(
        // Each SimEvent subclass gets an Arb. Start with the easy ones:
        arbPilotDecisionTick(state),
        arbPhysicsTick(state),
        arbControllerCycle(state),
        // Defer harder events (FlightPlanFiled, ProcessInstructionForAircraft, etc.) until property tests reveal value.
    )

    fun arbStatePlusEvent(): Arb<Pair<SimState, SimEvent>> =
        arbBaseState().flatMap { state -> arbEvent(state).map { ev -> state to ev } }
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

    test("R6 runway certifier preservation (post-event)") {
        checkAll(EngineGenerators.arbStatePlusEvent()) { (state, event) ->
            val (newState, _) = step(state, event)
            // Use Kotlin-side runway kernel shim to assert runway invariants hold:
            val runwayDecision = RunwayKernel.certify(newState.toRunwayKernelEnv(), newState.toRunwayState(), newState.toRunwayProposal())
            // Pass = runway state still valid. Adapt to the actual RunwayKernel shim's API at impl time.
            runwayDecision.shouldBeApproved()
        }
    }
})
```

Mirror `RunwayKernelBoundarySpec.kt`'s assertion style. If `RunwayKernel.kt`'s API doesn't expose a state-validation entry, fall back to asserting the simpler engine invariants (R3-R5); R6 may need a Kotlin-shim widening as a separate epic — record that case as a follow-up deferment, don't block R3-R5 on it.

### Step 4 — Tune R7 (bounded runtime)

Default Kotest property iteration count is 1000. Cap at 500 via `PropTestConfig(iterations = 500)`:

```kotlin
val propConfig = PropTestConfig(iterations = 500)
test("...", propConfig) { ... }
```

Override via system property for nightly runs:

```kotlin
val iterations = System.getProperty("kotest.property.iterations")?.toIntOrNull() ?: 500
```

Verify default-config test execution stays under 30 seconds (R7) by running once with timing.

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

Both BUILD SUCCESSFUL. If a property test fails on default seed, that's a real bug — surface in evidence; file as a follow-up epic; don't paper over.

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
- [ ] **R6** — Runway certifier shim asserts post-event runway state is valid (or documented follow-up if shim doesn't expose state-validation)
- [ ] **R7** — Bounded runtime: 500 iterations default, ≤30s; system-property override for nightly
- [ ] **R8** — kotest deps + JUnit5 runner in :sim jvmTest
- [ ] **R9** — Full verify green; new property tests pass with default seed (or seeds-found bugs filed as follow-up epics, not blockers)
- [ ] **R10** — Diff ≤5 files / ≤400 LOC

## Key context

- **Generators seed from world candidates** — not from-scratch. LOWG is the default seed; the LJMB candidate (fn-19 fixed) is also available for multi-aerodrome testing if needed.
- **R6 may need shim work** — if the Kotlin-side `RunwayKernel.kt` doesn't expose a state-validation entry point, file a follow-up "extend certifier shim to allow state-validation queries" deferment rather than fabricating a shim.
- **kotest-property is already a declared dependency** in `libs.versions.toml`; this epic activates it.
- **Pre-existing dirty state** (research/tools/requirements-spike/, fn-20+23 untracked, research/pdf+txt) MUST NOT be staged.

## Done summary

_(filled by `flowctl done` at task close)_

## Evidence

_(filled by `flowctl done` at task close)_
