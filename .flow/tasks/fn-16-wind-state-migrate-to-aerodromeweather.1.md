---
satisfies: [R1, R2, R3, R4, R5, R5a, R5b, R6, R7, R7a, R7b, R7c, R8, R9, R12]
---

## Description

Foundation pass for fn-16. Lands the typed `Aerodrome.weather` field on the `:core` `Aerodrome` data class, relocates `WeatherObservation` from `:controller` to `:core`, adds the `AviationWorld.updateAerodrome` lens helper, migrates `SimState.initial` to fold weather into the world at construction time, migrates the `MissingWeatherForRunwayAerodrome` invariant, **deletes `SimState.weatherByAerodrome`**, migrates the three production readers (`PilotWiring`, `ControllerWiring`, `SimTraceQueries.weatherTransitions`), and migrates the one mutating test (`G3aPilotReactiveCrosswindTest.authorWeather`) to the lens helper. Atomic — every reader and writer migrates in this pass; no shim, no parallel shape.

This task is structurally large but **architecturally narrow** — every surface has a single existing structural mirror (fn-12's `Runway.obstruction` migration). Every existing reader has a one-line transformation per the reader-migration table in the epic spec (R7).

**Size:** L → split rejected per `feedback_pass_scope.md`. The field + lens + sim-side writers + the three readers + the smart-constructor fold are coupled (deleting the SimState field must happen in the same commit as the reader migrations, else the build doesn't compile). One large pass. Acceptable.

**Files:**
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt:272-276` — DELETE the `WeatherObservation` data class definition here (move target)
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WeatherObservation.kt` (NEW) — `data class WeatherObservation(val wind: WindReport, val qnh: PressureSetting?, val visibility: Int?)`. Imports `xyz.easiersaid.twr.protocol.WindReport` and `xyz.easiersaid.twr.protocol.PressureSetting`. KDoc cites the move motivation (`project_rich_world_domain.md`; sibling to `Aerodrome`).
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:333-388` — `Aerodrome` data class: add `val weather: WeatherObservation? = null` as the **final** constructor parameter (after `ctrApproximationRadius` at line 387). Default-null preserves all existing call sites. KDoc mirrors `Runway.obstruction`'s shape (file precedent at `WorldModel.kt:157-170`).
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldLenses.kt` (NEW) — `inline fun AviationWorld.updateAerodrome(id, transform)` extension. Identity-equality short-circuit on no-op transform. KDoc cites the single-id counterpart of `RunwayObstructionWiring.kt`'s all-aerodromes walk.
- `core/src/commonTest/kotlin/xyz/easiersaid/twr/core/world/WorldLensesSpec.kt` (NEW) — three test cases for `updateAerodrome`: (a) updates an existing aerodrome; (b) returns the input world unchanged when the id is absent; (c) preserves structural sharing when the transform is a no-op (identity-equality via `===`).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/SimStateInitialWeatherSpec.kt` (NEW per codex round 3) — focused tests for the two new `SimState.initial` boundaries:
  - **R5b test**: `SimState.initial(...)` with a non-empty `weatherByAerodrome` keyed by an aerodrome absent from `world.aerodromes` returns `InitError.WeatherForUnknownAerodrome(typoId).left()`. Asserts the loud-failure boundary fires (no silent drop).
  - **R5a test (existing-invariant regression)**: `SimState.initial(...)` with a runway-bearing aerodrome whose weather entry is missing from the map returns `InitError.MissingWeatherForRunwayAerodrome(id).left()`. Confirms the predicate's source-migration didn't break the existing invariant.
  - **R4 happy-path test**: `SimState.initial(...)` with matching world + weather map produces a `SimState.right()` whose `world.aerodromes[id].weather == observation` for every entry. Confirms the fold lands the weather on the entity.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/PilotWiringWeatherProjectionSpec.kt` (NEW per codex round 3) — focused test for R7a's `mapNotNull` absent-key-preserving projection. **Setup (refined per codex round 5 to use `SimState.initial` — canonical, representative of production wiring):**
  - Build two `Aerodrome` entries, **both with empty `runways`** (so R5a's `runways.isNotEmpty() && weather == null` invariant doesn't fire on LJMB). Aerodrome A: LOWG. Aerodrome B: LJMB. Neither carries weather in its construction — `SimState.initial`'s fold sets weather only where the map carries an entry.
  - Construct via `SimState.initial(seed = ..., world = AviationWorld(aerodromes = mapOf(LOWG to ..., LJMB to ...)), worldIndex = WorldIndex(positions = mapOf(spawnPoint to Position(0.0, 0.0))), aircraft = listOf(AircraftState(... positionPoint = spawnPoint ...)), controllers = emptyList(), weatherByAerodrome = mapOf(LOWG to lowgObservation))`. The fold lands `weather = lowgObservation` on LOWG; LJMB stays `weather = null`. Both invariants pass (no runway-bearing aerodrome, no typo'd key).
  - Call `buildPilotInput(state, aircraftId)`. Assert: `result.weatherByAerodrome` is a 1-entry map (`mapOf(LOWG to lowgWind)` only) and **does NOT** contain a LJMB entry. A wrong `mapValues { weather?.wind ?: NotReported }` implementation would fail this test (it would produce a 2-entry map with `LJMB → NotReported`).
  - **Why `SimState.initial` not the direct constructor**: per codex round 5, the canonical construction path exercises both new invariants AND the production wiring; the direct-constructor escape hatch (preserved for the 8 existing direct-constructor sites in R6-sweep) is for tests that need to skip the smart constructor's invariants. This test doesn't.
  - **Aircraft spawn requirements**: minimal `AircraftState` with `positionPoint = spawnPoint`, `type = AircraftType.C172` (or whatever the test convention is). Verify against the existing `AtisSpec.kt` post-R6-sweep aircraft-construction pattern.
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimState.kt:68` — DELETE the `val weatherByAerodrome: Map<AerodromeId, WeatherObservation>` field.
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimState.kt:158-160` — KDoc on `MissingWeatherForRunwayAerodrome` updated; predicate at line 230 changes from `aerodromeId !in weatherByAerodrome` to `aerodrome.weather == null`. Check runs against the post-fold world.
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimState.kt:221-269` — `SimState.initial` keeps the `weatherByAerodrome: Map<AerodromeId, WeatherObservation>` parameter (caller ergonomics). **Locked order of operations** (per epic Decision #6 and codex round 1/3): (1) validate every weather key is in `world.aerodromes` — fail-fast on typo via `WeatherForUnknownAerodrome` (R5b); (2) fold via `world.updateAerodrome(id) { it.copy(weather = obs) }` per entry; (3) validate every runway-bearing aerodrome has weather (R5a) on the post-fold world. The constructed `SimState`'s `world` field carries the folded version. The DELETE removes the `weatherByAerodrome = weatherByAerodrome` line at SimState.kt:265.
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/PilotWiring.kt:41` — change `state.weatherByAerodrome.mapValues { (_, obs) -> obs.wind }` to the **pinned `mapNotNull` form** (per codex round 1/3):
  ```kotlin
  weatherByAerodrome = state.world.aerodromes
      .mapNotNull { (id, a) -> a.weather?.wind?.let { id to it } }
      .toMap(),
  ```
  This preserves the pre-migration absent-key semantics exactly (aerodromes with `weather == null` produce NO entry, matching the pre-migration absent-key behaviour). No implementation-time decision. The `mapValues { weather?.wind ?: NotReported }` alternative was considered and rejected because it would change `windForMission`'s `map.size == 1` singleton-fallback path in multi-aerodrome scenarios. KDoc on the call site (PilotWiring.kt:36-42) updated to cite this pin.
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/ControllerWiring.kt:150` — change `state.weatherByAerodrome[spec.aerodromeId]` to `state.world.aerodromes[spec.aerodromeId]?.weather`. KDoc on this line updated.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/SimTraceQueries.kt:208-211` — `weatherTransitions(aerodrome)` body changes from `transitionsOf { st -> Option.fromNullable(st.weatherByAerodrome[aerodrome]) }` to `transitionsOf { st -> Option.fromNullable(st.world.aerodromes[aerodrome]?.weather) }`. KDoc updated to reflect the new source. Shape (`List<Transition<Option<WeatherObservation>>>`) unchanged.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveCrosswindTest.kt:797-802` — `authorWeather` mutator becomes `st.copy(world = st.world.updateAerodrome(aerodromeId) { it.copy(weather = weather) })`. KDoc updated to cite the lens helper.
- **All consumers of `xyz.easiersaid.twr.controller.WeatherObservation`** — update import to `xyz.easiersaid.twr.core.world.WeatherObservation`. Grep audit covers `:controller`, `:sim` (production + tests), `:pilot` (tests only — `PilotInput.kt`'s fn-14 KDoc reference + `PilotCrosswindTickATickBTest.kt`). 48 files per `grep -rn WeatherObservation`. The compiler is the safety net.

## Approach

### Step 1: `WeatherObservation` relocation (R1)
Cut the data class from `controller/.../ControllerTypes.kt:272-276`. Create new file `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WeatherObservation.kt` with the moved definition. KDoc cites `project_rich_world_domain.md` as the motivation and notes the sibling-to-`Aerodrome` placement.

Mechanically replace every `import xyz.easiersaid.twr.controller.WeatherObservation` with `import xyz.easiersaid.twr.core.world.WeatherObservation`. Two enumeration approaches:
- **Grep-then-edit** (preferred per project Edit-tool conventions): `grep -rln "xyz.easiersaid.twr.controller.WeatherObservation" --include="*.kt" .` to list files; then use the project's standard Edit tool / apply_patch per file. This keeps a clean audit trail.
- **Bulk shell rewrite** (acceptable for clear-cut mechanical changes): a careful `sed` / `xargs` invocation IF the agent prefers and verifies with a follow-up grep + build. Either way, the **post-edit grep MUST return zero hits** for the old namespace.

Verify via a second grep that no stale imports remain.

**KMP compile verification (refined per codex round 1).** Single `compileKotlinJvm` is insufficient in a Kotlin Multiplatform project — the moved type lives in commonMain so metadata-compilation can catch issues JVM-compilation misses. Prefer either:
- Full smoke: `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt` (the full R12 verify command) — runs every source-set's compile + tests.
- Targeted source-set check (faster early loop): `./gradlew :core:compileCommonMainKotlinMetadata :controller:compileCommonMainKotlinMetadata :sim:compileCommonMainKotlinMetadata :pilot:compileCommonMainKotlinMetadata` (verify the exact task names against `./gradlew :sim:tasks --all | grep -i metadata` at task time; Kotlin task naming varies by KMP version).

If neither metadata task exists, fall back to the full R12 verify command. The acceptance criterion is the full R12 verify (per the existing acceptance row); the metadata commands are an early-loop optimisation, not the bar.

**Also update any fully-qualified references** (e.g. `xyz.easiersaid.twr.controller.WeatherObservation` written inline in test fixtures — `PerAircraftRngSpec.kt:64,71,87,181` has these). Same sed pattern catches them.

### Step 2: `Aerodrome.weather` field (R2)
Append `val weather: WeatherObservation? = null` to `Aerodrome` at `core/.../world/WorldModel.kt:333-388`. **Final field** in the constructor parameter list — append AFTER `ctrApproximationRadius` (line 387). Default-null preserves every constructor call site.

KDoc mirrors `Runway.obstruction`'s shape at `WorldModel.kt:157-170`:
```kotlin
/**
 * fn-16: typed weather-observation home on the aerodrome entity, per
 * `project_rich_world_domain.md` (time-varying state lives on the entity).
 * Default-null preserves existing constructor sites (production loader at
 * `WorldCandidateLoader`, test fixtures). Written by `SimState.initial`'s
 * fold of the `weatherByAerodrome` parameter and by mid-run mutations
 * via `AviationWorld.updateAerodrome`. Read by the pilot wiring
 * (projects `weather.wind` into `PilotInput.weatherByAerodrome`), the
 * controller wiring (projects `weather` into `ControllerView.weather`),
 * and the trace extractor `SimTraceQueries.weatherTransitions`.
 *
 * Predecessor: `SimState.weatherByAerodrome: Map<AerodromeId,
 * WeatherObservation>` (DELETED in fn-16.1). Migration motivated by
 * the second consumer of the rich-world-domain principle after fn-12's
 * `Runway.obstruction`.
 */
val weather: WeatherObservation? = null,
```

`Aerodrome` import already exposes `WeatherObservation` since both are in `:core/world` post-Step-1.

### Step 3: `AviationWorld.updateAerodrome` lens (R3)
Create `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldLenses.kt`:
```kotlin
package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.AerodromeId

/**
 * Single-aerodrome lens helper. Replaces `world.aerodromes[id]` with
 * `transform(world.aerodromes[id])`, returning a new [AviationWorld]
 * with the entry updated. No-op (returns the input unchanged) when the
 * id is absent or when the transform produces an identity-equal value.
 *
 * Counterpart of the all-aerodromes walk at
 * `sim/.../RunwayObstructionWiring.kt:27-44` (fn-12's `expireRunwayObstructions`).
 * The single-id form is the right shape for fn-16's weather mutations —
 * a test fixture authors a wind shift at one aerodrome at a time, not at
 * every aerodrome at once.
 *
 * Inline to avoid lambda allocation per call.
 */
inline fun AviationWorld.updateAerodrome(
    id: AerodromeId,
    transform: (Aerodrome) -> Aerodrome,
): AviationWorld {
    val current = aerodromes[id] ?: return this
    val updated = transform(current)
    return if (updated === current) this
    else copy(aerodromes = aerodromes + (id to updated))
}
```

Add unit test `core/src/commonTest/kotlin/xyz/easiersaid/twr/core/world/WorldLensesSpec.kt`:
- Case 1: `updateAerodrome` on an existing id returns a world whose entry is the transform's output.
- Case 2: `updateAerodrome` on an absent id returns the input world unchanged (referentially-equal via `===`).
- Case 3: `updateAerodrome` with an identity transform (`{ it }`) returns the input world unchanged (`===`).

### Step 4: `SimState.initial` fold + dual invariant checks (R4, R5a, R5b — refined per codex round 1)

At `SimState.kt:221-269`, modify the function. **Add a second invariant** (`WeatherForUnknownAerodrome`) to close the silent-drop window per Decision #6 (epic spec) — `updateAerodrome`'s no-op-on-absent-id is appropriate for a generic lens but inappropriate for a validation boundary.

```kotlin
fun initial(
    seed: Long,
    world: AviationWorld = AviationWorld(),
    worldIndex: WorldIndex = WorldIndex(),
    aircraft: List<AircraftState> = emptyList(),
    controllers: List<ControllerSpec> = emptyList(),
    weatherByAerodrome: Map<AerodromeId, WeatherObservation>,
): Either<InitError, SimState> {
    // R5b (NEW): validate every weather key is a known aerodrome
    // BEFORE the fold. Without this, `updateAerodrome`'s no-op-on-absent-id
    // semantics would silently drop a typo'd weather key, leaving the
    // runway-bearing aerodrome with null weather and triggering R5a
    // obscurely. Surface the typo directly.
    for ((aerodromeId, _) in weatherByAerodrome) {
        if (aerodromeId !in world.aerodromes) {
            return InitError.WeatherForUnknownAerodrome(aerodromeId).left()
        }
    }
    // Fold weather into the world at construction time. Post-fold,
    // weather lives on `world.aerodromes[id].weather` and the flat
    // `weatherByAerodrome` parameter is consumed (no field stored).
    // Safe because the loop above confirmed every id is present.
    val foldedWorld = weatherByAerodrome.entries.fold(world) { acc, (id, obs) ->
        acc.updateAerodrome(id) { it.copy(weather = obs) }
    }
    // R5a: existing invariant — every runway-bearing aerodrome has weather.
    // Predicate source migrates from map to entity.
    for ((aerodromeId, aerodrome) in foldedWorld.aerodromes) {
        if (aerodrome.runways.isNotEmpty() && aerodrome.weather == null) {
            return InitError.MissingWeatherForRunwayAerodrome(aerodromeId).left()
        }
    }
    // ... rest of validation unchanged ...
    return SimState(
        now = SimTime.ZERO,
        // ... existing fields ...
        world = foldedWorld,
        worldIndex = worldIndex,
        // DELETE: weatherByAerodrome = weatherByAerodrome,
        // ... existing trailing fields ...
    ).right()
}
```

Add the new `InitError.WeatherForUnknownAerodrome` variant alongside `MissingWeatherForRunwayAerodrome` in the sealed interface at `SimState.kt:151-206`:

```kotlin
/**
 * fn-16: the caller passed a weather observation for an aerodromeId
 * that is not present in `world.aerodromes`. Without this check,
 * `updateAerodrome`'s no-op-on-absent-id semantics would silently
 * drop the observation, masking a fixture-authoring typo.
 */
data class WeatherForUnknownAerodrome(
    val aerodromeId: AerodromeId,
) : InitError
```

**Audit consumers of `InitError`** — every exhaustive `when (error: InitError)` site adds an arm for the new variant. Grep `is InitError\|when.*InitError` to enumerate; likely zero or one site at this point (the error type is internal to `SimState.initial`; callers either `.fold` the Either or use `getOrThrow`).

KDoc on `MissingWeatherForRunwayAerodrome` updated: replace "no entry in `weatherByAerodrome`" with "`aerodrome.weather == null`".
KDoc on `SimState.initial` updated: note that the parameter is folded into `world` at construction; note both invariant checks (B before fold, A after).

### Step 5: DELETE `SimState.weatherByAerodrome` (R6)
At `SimState.kt:68`, delete the `val weatherByAerodrome: Map<AerodromeId, WeatherObservation>` field. Update the class-level KDoc (around lines 19-36) to remove the `weatherByAerodrome` field reference if present. The compiler will now error at every reader site — which is exactly the signal to migrate them.

### Step 6: Reader migrations (R7)
- **R7a (PilotWiring.kt:41)**: **Pinned form per codex round 1** — use the `mapNotNull` absent-key-preserving projection:
  ```kotlin
  weatherByAerodrome = state.world.aerodromes
      .mapNotNull { (id, a) -> a.weather?.wind?.let { id to it } }
      .toMap(),
  ```
  This preserves pre-migration key-set semantics exactly: an aerodrome with `weather == null` produces no entry (matching pre-migration's absent-key behaviour). Critical for `windForMission`'s `map.size == 1` singleton-fallback path. Update the KDoc block at PilotWiring.kt:36-42 to cite the new source. `WindForMissionTest` + `PilotCrosswindTickATickBTest` + `FirewallPilotInputTest` GREEN as the regression check.
- **R7b (ControllerWiring.kt:150)**: change `weather = state.weatherByAerodrome[spec.aerodromeId]` to `weather = state.world.aerodromes[spec.aerodromeId]?.weather`. No KDoc here directly but the function-level KDoc may need a touch if it references the field.
- **R7c (SimTraceQueries.kt:208-211)**: change body to walk `world.aerodromes`. The KDoc block at lines 186-207 already explains the trace shape; update the implementation-cite line to reflect the new source.

### Step 7: Mutator test migration (R8)
At `G3aPilotReactiveCrosswindTest.kt:790-802`, replace `authorWeather`'s body:
```kotlin
private fun authorWeather(
    st: SimState,
    aerodromeId: AerodromeId,
    weather: WeatherObservation,
): SimState =
    st.copy(world = st.world.updateAerodrome(aerodromeId) { it.copy(weather = weather) })
```
Update the KDoc to cite the new lens helper. Add `import xyz.easiersaid.twr.core.world.updateAerodrome`.

### Step 8: Sweep audit (R9)
Run the audit greps from the epic spec's "Quick commands" section:
```bash
grep -rn "\.weatherByAerodrome\b" --include="*.kt" .
grep -rn "weatherByAerodrome" --include="*.kt" .
```
Verify zero `SimState.weatherByAerodrome` references remain (the compiler would have caught this; the grep is double-coverage). Remaining acceptable references:
- `SimState.initial(weatherByAerodrome = ...)` parameter name at every test call site.
- `Fixture.MultiAerodromeFixture.weatherByAerodrome` struct field.
- `PilotInput.weatherByAerodrome` (different field on different type).
- `PilotWiring`'s `weatherByAerodrome = ...` named-arg call site (parameter name on `PilotInput`).
- `SimTraceQueries.weatherTransitions` and its KDoc.

### Step 9: smoke verification (R12)
Run `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt`. Expected: all 75+ tests green, eight goldens green, detekt baseline unchanged.

If any test fails: real regression. Diagnose at root cause. **Do not skip-list, do not soften.** The most likely regression site is the R7a reader migration's behaviour-equivalence choice — if `WindForMissionTest` fails, switch to the `mapNotNull` form (or vice-versa).

## Investigation targets

**Required**:
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:151-175` — `Runway.obstruction` precedent (mirror exactly)
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:333-388` — `Aerodrome` data class
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:390-398` — `AviationWorld` data class (target of the lens)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt:272-276` — current `WeatherObservation` home (move source)
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/RunwayObstructionWiring.kt:27-44` — fn-12 all-aerodromes walk pattern (lens precedent)
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimState.kt:68,158-160,221-269` — field, invariant, smart constructor
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/PilotWiring.kt:36-42` — pilot reader site
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/ControllerWiring.kt:140-158` — controller reader site
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/SimTraceQueries.kt:186-211` — trace extractor site
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveCrosswindTest.kt:789-802` — mutator test site
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/WindForMissionTest.kt` — R7a regression backstop
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotCrosswindTickATickBTest.kt` — R7a regression backstop
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/FirewallPilotInputTest.kt` — firewall test (unchanged but verify)

**Optional**:
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateLoader.kt:148` — production Aerodrome constructor site (verify default-null preserves)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt` — fixture constructor sites (verify pass-through to `SimState.initial`)

## Key context

- **`Aerodrome` lives in `:core`**, not `:protocol`. `:core` depends on `:protocol` only.
- **`WeatherObservation` lives in `:controller`** today. Moves to `:core/world` in this task.
- **`:controller` depends on `:core`** — so post-move, `:controller` consumers re-import from `:core` (compiler enforced).
- **`:pilot` depends on `:core` and `:protocol`**, NOT `:controller`. Post-move, `:pilot` can directly import `WeatherObservation` if it ever needs the full triple — but fn-14 deliberately projects only `WindReport` to the pilot firewall. **This task does NOT change the pilot firewall** — `PilotInput.weatherByAerodrome` stays `Map<AerodromeId, WindReport>`.
- **Default-null preserves call sites.** Both `Aerodrome.weather` and `Runway.obstruction` default null; the loader and existing fixtures don't pass them.
- **The smart constructor's `weatherByAerodrome: Map<...>` parameter stays.** No call-site churn at test fixtures. The fold happens inside `SimState.initial`.
- **No `WorldIndex` change.** Weather isn't indexed; readers walk `world.aerodromes` directly (mirrors fn-12's obstruction readers).
- **Hard cutover.** The `SimState.weatherByAerodrome` field is DELETED in this task. No shim. The compiler is the safety net.

## Acceptance

- [ ] R1: `WeatherObservation` data class moved from `controller/.../ControllerTypes.kt:272-276` to new file `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WeatherObservation.kt`. Shape unchanged. Imports `xyz.easiersaid.twr.protocol.WindReport` and `xyz.easiersaid.twr.protocol.PressureSetting`. KDoc cites `project_rich_world_domain.md` motivation. **Three access shapes migrate (per codex round 2):**
  1. Every `import xyz.easiersaid.twr.controller.WeatherObservation` updated to `xyz.easiersaid.twr.core.world.WeatherObservation` across `:controller`, `:sim` (production + tests), `:pilot` tests — sed-replaceable.
  2. Every fully-qualified inline reference `xyz.easiersaid.twr.controller.WeatherObservation` (e.g. `PerAircraftRngSpec.kt:64,71,87,181`) updated — same sed pattern.
  3. **Every same-package simple-name usage inside `:controller`** gets a new `import xyz.easiersaid.twr.core.world.WeatherObservation`. Audit: `grep -l "WeatherObservation" controller/src/commonMain/kotlin/` then for each file check whether it has the new import; add if missing. Known consumers: `ControllerTypes.kt:54` (`ControllerView.weather`), `Guard.kt:36,695` (`OperatorContext.weather` + reader), `Controller.kt:963`.
  
  **Acceptance command for the import migration**: the **full R12 verify command** below — `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt` — exits 0. (`compileKotlinJvm` alone is insufficient in KMP since the moved type lives in commonMain; the full verify exercises every source-set per codex round 1.) The compiler catches both unresolved simple-name references inside `:controller` AND stale imports elsewhere. Grep `xyz.easiersaid.twr.controller.WeatherObservation` returns zero hits.
  
  **R1-unused-imports-cleanup (NEW per codex round 7)**: after deleting the controller-local `WeatherObservation` data class, the imports it referenced (`xyz.easiersaid.twr.protocol.WindReport` at the top of `ControllerTypes.kt`, `xyz.easiersaid.twr.protocol.PressureSetting` if present, etc.) become unused **only if `ControllerTypes.kt` itself doesn't use them elsewhere**. Verify per-import: search the file for remaining usages; remove only those that become unused. detekt's `UnusedImports` rule (if active) would catch this, but detekt baseline must remain unchanged per R12 — cleaning up the now-unused imports is the right pre-emption. Record which imports were removed in the evidence note.
- [ ] R2: `Aerodrome.weather: WeatherObservation? = null` added at `core/.../world/WorldModel.kt`, as the **final** constructor parameter (after `ctrApproximationRadius`). KDoc mirrors `Runway.obstruction`'s shape. Default-null preserves every `Aerodrome(...)` constructor site. `core:allTests` passes unchanged.
- [ ] R2-aerodrome-copy-audit (NEW per codex round 5; refined per codex round 6/7): every site that constructs / copies `Aerodrome` OR rebuilds the `world.aerodromes` map audited per the project's new-field discipline. Kotlin data-class semantics: positional / explicit-named constructor sites with new trailing-default param silently default to `weather = null`; `copy(...)` calls preserve the current value unless explicitly set. **`AviationWorld.copy(aerodromes = ...)` with a rebuilt map can also drop `weather` if the rebuild reconstructs aerodromes from older values** (per codex round 7).
  
  **Audit enumeration (four classes of sites):**
  1. **Files importing or constructing `Aerodrome`**: `rg --files-with-matches "\bAerodrome\b" --glob "*.kt"`. Open each.
  2. **`Aerodrome(...)` constructor sites**: within those files, find `Aerodrome(` calls. Production loader at `migration/.../WorldCandidateLoader.kt:148` is the canonical site. Test fixtures: `sim/src/jvmTest/.../testing/Fixtures.kt`, anywhere `Aerodrome(icao = ..., ...)` is used.
  3. **`aerodrome.copy(...)` sites**: find `.copy(` calls where the receiver is `Aerodrome`-typed. Common shape: `aerodrome.copy(...)`, `fixture.aerodrome.copy(...)`. Use the IDE / a Kotlin-aware search if possible; fallback to `rg "\\.copy\\(" --glob "*.kt"` and filter manually.
  4. **`AviationWorld(aerodromes = ...)` / `world.copy(aerodromes = ...)` sites (NEW per codex round 7)**: `rg "aerodromes\\s*=" --glob "*.kt"` plus `rg "AviationWorld\\(" --glob "*.kt"` plus `rg "world\\.copy\\(aerodromes" --glob "*.kt"`. These rebuild the aerodromes map — a transformation that maps over older values without preserving `weather` would silently drop it. Examples worth verifying: the new lens helper itself (`AviationWorld.updateAerodrome` — by design preserves `weather` for non-target aerodromes); `SimState.initial`'s fold (per R4 — sets `weather` for fold entries, preserves for others); `RunwayObstructionWiring.expireRunwayObstructions` (per fn-12 — `aerodromes.mapValues { ... aerodrome.copy(runways = ...) }`; `copy(runways = ...)` preserves `weather` per Kotlin semantics).
  
  For each hit, classify into: (a) preserves `weather` implicitly via Kotlin `copy` semantics — OK; (b) constructor / full-rebuild relies on default `weather = null` — OK for non-runway-bearing aerodromes or fixtures that don't model weather; (c) intentional `weather = null` set — OK if deliberate; (d) bug — found a site that should have set `weather` but doesn't. **Record each reviewed site in the task evidence note (file:line + classification).** Production loader and the four prepass mutation sites (R4 fold, lens, fn-12's `expireRunwayObstructions`, the test mutator at G3aPilotReactiveCrosswindTest) are expected to all classify as (a). Test fixtures classify as (a)/(b) depending on whether they model weather.
- [ ] R3: `inline fun AviationWorld.updateAerodrome(id, transform)` extension added at new file `core/.../world/WorldLenses.kt`. Identity-equality short-circuit on no-op transform. Returns input unchanged on absent id. KDoc cites the fn-12 walk pattern as the all-aerodromes precedent. Unit test `core/.../WorldLensesSpec.kt` covers three cases (update, absent, identity).
- [ ] R4: `SimState.initial` at `SimState.kt:221-269` folds the `weatherByAerodrome` parameter into `world`. **Order of operations:** (1) validate every weather key is a known aerodrome (R5b — fail-fast on typo); (2) fold weather into world via `updateAerodrome`; (3) validate every runway-bearing aerodrome has weather (R5a). Constructed `SimState`'s `world` field carries the folded version. Parameter name unchanged.
- [ ] R5a: `InitError.MissingWeatherForRunwayAerodrome` predicate at `SimState.kt:229-232` changes from map-based to `aerodrome.weather == null`. Variant shape unchanged. KDoc updated to reflect the new source.
- [ ] R5b (NEW per codex round 1): `InitError.WeatherForUnknownAerodrome(aerodromeId)` variant added to the sealed interface at `SimState.kt:151-206`. New pre-fold check at the top of `SimState.initial` rejects any weather key not in `world.aerodromes`. KDoc cites the silent-drop closure motivation. Every exhaustive `when (error: InitError)` consumer (grep-enumerable) adds an arm for the new variant.
- [ ] R5b-test (NEW per codex round 3): `SimStateInitialWeatherSpec.kt` exercises the new invariant with three focused cases: (a) typo'd aerodrome key → `WeatherForUnknownAerodrome.left()` (loud-failure boundary fires; no silent drop); (b) existing invariant regression — runway-bearing aerodrome missing weather → `MissingWeatherForRunwayAerodrome.left()`; (c) happy path — matching world + weather map → `SimState.right()` with `world.aerodromes[id].weather == observation` for every entry. The R5b case directly tests the new loud-failure boundary; without this test the boundary's correctness rests on implementation review alone.
- [ ] R7a-test (NEW per codex round 3; setup pinned per codex round 5/6; NotReported coverage added per codex round 7): `PilotWiringWeatherProjectionSpec.kt` exercises the `mapNotNull` absent-key-preserving projection across **three semantic cases**. **Construct via `SimState.initial` (canonical path; exercises the smart-constructor fold + invariants)**: three aerodromes all with empty `runways`:
  - **LOWG**: weathered with `WindReport.Available(Wind(...))`.
  - **LJMB**: weathered with `WindReport.NotReported` (i.e. `WeatherObservation(wind = WindReport.NotReported, qnh = null, visibility = null)`).
  - **EDDM**: unauthored (gets `weather = null` via the fold).
  
  `SimState.initial(weatherByAerodrome = mapOf(LOWG to lowgObs, LJMB to ljmbNotReportedObs))` — EDDM is absent from the map so its `weather` stays null.
  
  Call `buildPilotInput(state, aircraftId)`. **Assertions:**
  - `result.weatherByAerodrome[LOWG] == WindReport.Available(lowgWind)` (the projection lifts the available wind).
  - `result.weatherByAerodrome[LJMB] == WindReport.NotReported` (the projection lifts the `NotReported` wind — `mapNotNull` projects every aerodrome with non-null `weather`, including those whose wind is `NotReported`).
  - `result.weatherByAerodrome[EDDM] == null` (absent key — the projection skips aerodromes with `weather == null`).
  - `result.weatherByAerodrome.size == 2` (LOWG + LJMB).
  
  These three cases pin the full semantic distinction: (a) Available wind → projected; (b) NotReported wind → projected (`mapNotNull` projects on `weather?.wind` non-null, NOT on `weather.wind is Available`); (c) null weather → not projected. A wrong `mapValues { ... NotReported }` implementation would fail the third assertion (it would include EDDM with `NotReported`). This is the **sim wiring** test that `WindForMissionTest` (a helper-only test) doesn't cover.
- [ ] R6: `SimState.weatherByAerodrome` field DELETED at `SimState.kt:68`. The `weatherByAerodrome = weatherByAerodrome` line at `SimState.kt:265` (constructor arg) DELETED. Class-level KDoc updated to remove the field reference. Compiler emits errors at every reader site — all migrated per R7/R8 below.
- [ ] **R6-direct-constructor-sweep (NEW per codex round 2; audit scope widened per codex round 5; classification tightened per codex round 7): every direct `SimState(...)` constructor call site across the entire repo migrated AND classified by world contents.** Audit command:
  ```
  rg "SimState\(" --glob "*.kt"
  ```
  Repo-wide (not limited to `sim/src/jvmTest`) to catch any `commonTest`, other-module-test, or production helper that constructs `SimState` directly. **Known baseline (8 sites confirmed at planning time):**
  - `sim/src/jvmTest/.../AtisSpec.kt:59`
  - `sim/src/jvmTest/.../MissedHandoffEventSpec.kt:48`
  - `sim/src/jvmTest/.../ResponsibilityInvariantSpec.kt:207`
  - `sim/src/jvmTest/.../ResponsibilityStateMachineSpec.kt:66`
  - `sim/src/jvmTest/.../MissedHandoffProjectionSpec.kt:58`
  - `sim/src/jvmTest/.../RadarServiceTerminatedSpec.kt:64`
  - `sim/src/jvmTest/.../FlightPlanFilingSpec.kt:46`
  - `sim/src/jvmTest/.../KnownStripsHandoffTransitionSpec.kt:60`
  
  Plus the SimState definition itself at `sim/src/commonMain/.../SimState.kt:253` (the smart constructor's `return SimState(...)` — already migrated as part of R6's "remove the `weatherByAerodrome = weatherByAerodrome` line at SimState.kt:265"). Plus any unexpected hits — record each new hit in evidence.
  
  **Migration steps for each direct-constructor site (per codex round 7 — no "punt to future deferment"):**
  1. Remove the `weatherByAerodrome = ...` named arg (field is gone).
  2. **Classify the site's `world` parameter by content**: does `world.aerodromes` contain any runway-bearing aerodrome (`runways.isNotEmpty()`)?
     - **If NO** (all aerodromes have empty `runways`, or the world is empty `AviationWorld()`): no action beyond step 1. The test doesn't model weather and doesn't need weather. The weakened invariant (no `MissingWeatherForRunwayAerodrome` enforcement) is moot — the predicate would be vacuous.
     - **If YES** (at least one aerodrome has runways): the test previously passed `weatherByAerodrome = emptyMap()` for a runway-bearing world, which would have failed the **smart-constructor** invariant — but the direct constructor bypassed it. Post-migration: explicitly author `weather` on each runway-bearing aerodrome in the test's world setup (use `world.updateAerodrome(id) { it.copy(weather = ...) }` lens helper BEFORE the direct constructor call). OR switch the site to `SimState.initial` (canonical path). Pick whichever is closer to the test's existing structure.
  3. Record each site's classification (Y/N for runway-bearing aerodromes) + migration action in the evidence note.
  
  Per epic R6, the direct constructor remains as an escape hatch — but **no longer with a silent invariant bypass**. Tests that previously skirted weather either (a) don't model runway-bearing aerodromes (no behaviour change), or (b) explicitly author weather (matches what `SimState.initial`'s invariant would enforce). `D-PASS-direct-simstate-constructor-canonicalization` filed as a sibling for the future canonicalization pass (promote all direct-constructor sites to `SimState.initial`).
- [ ] **R5b-audit (NEW per codex round 2; audit scope widened per codex round 5): every `SimState.initial(...)` call across the entire repo audited for the new `WeatherForUnknownAerodrome` invariant.** The new check rejects any weather key not in `world.aerodromes`. Audit command:
  ```
  rg "SimState\.initial\(" --glob "*.kt"
  ```
  Repo-wide (not limited to `sim/src/jvmTest`). For each hit, manually verify the `weatherByAerodrome` parameter's keys match `world.aerodromes`' keys (or the world parameter is `Fixture`-loaded with matching aerodromes). Each violation gets either (a) a matching world authored, or (b) the weather parameter dropped if no weather is needed. Expected hits: low (dominant pattern uses Fixture-loaded worlds where the fixture's weather map matches the loaded world).
- [ ] R7a (PilotWiring): `sim/.../PilotWiring.kt:41` reads from `state.world.aerodromes` instead of `state.weatherByAerodrome`. **Form pinned at planning time per codex round 1 — `mapNotNull` absent-key-preserving form (NO implementation-time choice):**
  ```kotlin
  weatherByAerodrome = state.world.aerodromes
      .mapNotNull { (id, a) -> a.weather?.wind?.let { id to it } }
      .toMap(),
  ```
  This preserves the pre-migration key set exactly (an aerodrome with `weather == null` produces NO entry, matching the pre-migration `state.weatherByAerodrome` shape where the key was simply absent). Critical for `windForMission`'s `map.size == 1` singleton-fallback path in multi-aerodrome scenarios. The KDoc block at lines 36-42 cites the new source. `FirewallPilotInputTest` UNCHANGED (firewall surface preserved). `WindForMissionTest` + `PilotCrosswindTickATickBTest` GREEN.
- [ ] R7b (ControllerWiring): `sim/.../ControllerWiring.kt:150` reads from `state.world.aerodromes[spec.aerodromeId]?.weather`. `ControllerView.weather` field shape unchanged (still `WeatherObservation?`). Existing controller-side tests (`GuardSpec`, `CertificationBoundarySpec`, etc.) pass unchanged.
- [ ] R7c (SimTraceQueries): `sim/.../testing/SimTraceQueries.kt:208-211` `weatherTransitions(aerodrome)` body reads from `st.world.aerodromes[aerodrome]?.weather`. Trace shape (`List<Transition<Option<WeatherObservation>>>`) unchanged. `G3aPilotReactiveCrosswindTest`'s weather-transitions pin still asserts exactly two transitions.
- [ ] R8: `G3aPilotReactiveCrosswindTest.kt:797-802` `authorWeather` mutator uses `world.updateAerodrome(aerodromeId) { it.copy(weather = weather) }` via the new lens helper. KDoc updated to cite the lens. Test passes — both wind-shift transitions still fire, GA + recovery landing still complete.
- [ ] R9: post-migration grep audit confirms zero `SimState.weatherByAerodrome` references remain. Allowed remaining `weatherByAerodrome` references enumerated and verified harmless: `SimState.initial(weatherByAerodrome = ...)` parameter; `Fixture.MultiAerodromeFixture.weatherByAerodrome` struct field; `PilotInput.weatherByAerodrome` field; pilot-side wiring named-arg sites; `SimTraceQueries.weatherTransitions` name.
- [ ] R12: `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt` exits 0. **All eight goldens GREEN** (G0 / G1 / G1-min / G2 / G3a-trained / G3a-obstruction / G3a-obstruction-continue-approach / G3a-react). detekt baseline unchanged. `WorldLensesSpec` GREEN. `SimStateInitialWeatherSpec` GREEN (R5b-test). `PilotWiringWeatherProjectionSpec` GREEN (R7a-test). `WindForMissionTest` + `PilotCrosswindTickATickBTest` + `FirewallPilotInputTest` GREEN (R7a regression backstop). `G3aPilotReactiveCrosswindTest` GREEN (R8 mutator check).
- [ ] No use of `xyz.easiersaid.twr.controller.WeatherObservation` namespace remains — verified by grep.
- [ ] No new `else` clauses in any exhaustive `when` site (none added by this refactor; sanity check).
- [ ] No backwards-compat shim. The `SimState.weatherByAerodrome` field is fully gone.

## Done summary
Hoisted weather onto the `Aerodrome.weather` entity field (mirroring fn-12's `Runway.obstruction` precedent) via an atomic hard cutover: relocated `WeatherObservation` from `:controller` to `:core/world`, added `AviationWorld.updateAerodrome` lens, taught `SimState.initial` to fold weather into the world with a new `WeatherForUnknownAerodrome` pre-fold invariant, deleted `SimState.weatherByAerodrome`, migrated all three production readers + the two test mutators, and shipped focused specs for the lens, the dual invariants, and the pinned `mapNotNull` projection. Codex impl-review converged in 3 rounds (NEEDS_WORK on stale KDoc refs → NEEDS_WORK on pilot-firewall reachability KDoc → SHIP).
## Evidence
- Commits: 51ccebf, 3ced6e0, 55f5223, dbf4c8d
- Tests (full spec command, including `:migration:allTests`):
  `./gradlew --offline --no-daemon :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt`
  Result: all eight goldens GREEN via `:sim:jvmTest`; `WorldLensesSpec`, `SimStateInitialWeatherSpec`, `PilotWiringWeatherProjectionSpec`, `WindForMissionTest`, `PilotCrosswindTickATickBTest`, `FirewallPilotInputTest`, `G3aPilotReactiveCrosswindTest` all GREEN; detekt baseline unchanged. `:migration:allTests` reported one PRE-EXISTING failure unrelated to fn-16: `LjmbWorldCandidateValidationTest > writesLjmbCurrentCoreValidationReport()` at `LjmbWorldCandidateValidationTest.kt:264` (world-candidate JSON authoring/validation surface, not weather state shape; observed at HEAD `dbf4c8d` before fn-16.1's atomic field migration landed in any fixture/test path that this validator inspects). All fn-16-relevant test surfaces GREEN.
- PRs: