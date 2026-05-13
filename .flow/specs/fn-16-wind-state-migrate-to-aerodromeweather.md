# fn-16 — Wind-state migrate from `SimState.weatherByAerodrome` to `Aerodrome.weather`

## Overview

Cross-cutting refactor that finishes applying the `project_rich_world_domain.md` principle ("time-varying state lives on the entity it concerns, not flat World-root maps") to weather. fn-12 already migrated runway obstructions from a hypothetical world-root map to `Runway.obstruction: RunwayObstruction?`. fn-14 (G3a-react, just landed) added a new consumer of weather (pilot-side `WindReport` projection) but explicitly deferred the field-shape migration as `D-PASS-wind-state-migrate-to-aerodrome` to keep the G3a-react scope contained. This epic closes that deferment.

The migration moves the world-truth weather-by-aerodrome map off `SimState` and onto the entity it describes: `Aerodrome.weather: WeatherObservation?` on `:core`'s `Aerodrome` data class. Every reader and writer migrates atomically — no shim, no parallel shape. After this epic the only place weather lives is on the aerodrome entity, matching the existing `Runway.obstruction` precedent.

**Doctrinal grounding:** weather is observed AT an aerodrome. The standing-observation product of aviation (METAR) is aerodrome-scoped (`METAR LOWG ...`). The flat-map shape was a pre-`project_rich_world_domain.md` artefact that survived G2/G3a only because it's coarse-grained enough to be tolerable; this epic removes the rot before the surface acquires further consumers (METAR/TAF ingestion, weather-volume modelling, weather-history replay are all on the deferred sibling list and are easier to land on top of the entity-on-aerodrome shape).

**Why this is more than a rename.** The flat-map lives on `SimState` (in `:sim/commonMain`) and is typed `Map<AerodromeId, WeatherObservation>`. `WeatherObservation` currently lives in `:controller` (`ControllerTypes.kt:272`). Once weather attaches to `Aerodrome` (in `:core`), `WeatherObservation` must move out of `:controller` — `:core` cannot import `:controller` (dep direction is the opposite). The cleanest destination is **`:core`** (alongside `Aerodrome`): `:core` already imports `:protocol` where `WindReport` and `PressureSetting` live, and the type is world-state, not wire-protocol. This is a load-bearing decision (Decision #4 below).

**Why the test sweep is large.** 25 test files construct `SimState.initial(... weatherByAerodrome = mapOf(...) ...)` directly or read `state.weatherByAerodrome[id]`. Each one migrates to constructing aerodromes whose `weather` field carries the observation, plus updating any in-test mutation to walk through `world.aerodromes`. This is large but mechanical — the audit is grep-scriptable and every site has a one-line transformation.

**Reuse target — fn-12's runway-obstruction migration.** `sim/src/commonMain/.../RunwayObstructionWiring.kt` is the canonical world-mutation pattern. fn-12 used a `state.world.aerodromes.mapValues { (_, aerodrome) -> ... aerodrome.copy(runways = updatedRunways) }` walk for the per-cycle expiry pass. Weather doesn't need an expiry pass (it doesn't have a `clearsAt`-style lifetime — it's a point-in-time observation that gets overwritten), but it does need a writer helper. The writer shape is a **lens helper** `AviationWorld.updateAerodrome(id) { transform(it) }` (Decision #3) — single touchpoint for both production sim writes and test fixture mutations.

## Boundaries / non-goals

- **Out: weather model expansion.** Gusts (already typed on `Wind.gustKnots`), visibility ceilings, weather volumes, precipitation, cloud layers. v1 ships the existing `WeatherObservation(wind, qnh, visibility)` triple, only the *location* changes. Filed as `D-PASS-weather-model-expansion`.
- **Out: per-runway weather.** Real-world: large airports can have wind sensors per runway (parallel runways with measurable wind differential). v1 keeps aerodrome-level scoping — `Aerodrome.weather`, not `Runway.weather`. Filed as `D-PASS-per-runway-weather`.
- **Out: weather-history / replay.** Current shape is a single point-in-time observation overwritten by each new write. No retained history. Filed as `D-PASS-weather-history-replay`.
- **Out: METAR / TAF ingestion.** Today the sim writes weather directly (test fixtures, plus the G3a-react one-shot mutation). Reading METAR/TAF cycles is a separate pipeline. Filed as `D-PASS-metar-taf-ingestion`.
- **Out: backwards-compat shim.** Hard cutover only. No parallel shape during the migration; no `weatherByAerodrome` field surviving in deprecated form. The whole point is to remove the rot — leaving a shim defeats it. The codebase rule from the system prompt ("no backwards-compat hacks") applies directly.
- **Out: per-aerodrome `WeatherObservation` validity / staleness window.** Today the observation is treated as always-current; no `observedAt` timestamp on the field. Filed as `D-PASS-weather-validity-window`.
- **Out: changing the pilot-side or controller-side firewall shape.** `PilotInput.weatherByAerodrome: Map<AerodromeId, WindReport>` (fn-14.1) keeps its existing shape. `ControllerView.weather: WeatherObservation?` (existing) keeps its existing shape. Only the **source** changes — readers walk `worldIndex.aerodromes` / `world.aerodromes` instead of `state.weatherByAerodrome`. This is structurally identical to how fn-14 sourced wind; the firewall surface is unaffected.

## Strategy Alignment

Active tracks served by this plan:

- **Runtime simulator** — closes the second rich-domain migration (after fn-12's `Runway.obstruction`). With wind state on the entity, the `project_rich_world_domain.md` principle reaches its second consumer; the precedent is now strong enough that future world-state additions (surface contamination, lighting, NOTAMs) fall into the same shape automatically.
- **Code quality / rot reduction** — removes a known pre-`rich_world_domain` shape that fn-14 explicitly listed as a deferment. The codebase ships with one less "deferred but known" inconsistency.

## Decision context

### 1. Field shape on `Aerodrome` — nullable `WeatherObservation?` (high confidence, mirrors fn-12 precedent)

**Decided.** Add `val weather: WeatherObservation? = null` to `Aerodrome` at `core/.../world/WorldModel.kt:333`. **Default-null** preserves all existing `Aerodrome(...)` constructor sites (production world loader + every fixture that doesn't model weather).

**Why nullable, not sealed.** Three options were on the table:
1. `weather: WeatherObservation?` (nullable) — matches the existing flat-map's "absence = no entry" semantics. Mirrors fn-12's `Runway.obstruction: RunwayObstruction?` precedent exactly.
2. `weather: WeatherObservation` (non-null, with `WindReport.NotReported` baked in as the empty value) — total over the type, but moves the "no observation yet" semantics into a value rather than nullability.
3. Two-field carveout (e.g. `windReport: WindReport`, `pressure: PressureSetting?`, `visibility: Int?`) — flattens the triple onto the aerodrome directly.

**(1) wins.** Rationale:
- **Mirrors `Runway.obstruction` exactly.** fn-12's precedent is the load-bearing one for the principle. Diverging now would force two patterns for "time-varying state on the entity" and defeat the precedent value.
- **`WindReport` already carries the totality.** `WindReport.NotReported` (sealed `WindReport` in `:protocol`) covers the "no wind yet" case for the wind slice; the outer nullability covers the "no weather observation at all" case (which is structurally different — e.g. an aerodrome the simulator hasn't started observing yet). Two different "no" semantics, two different mechanisms.
- **No null-deref risk in practice.** Every reader either (a) projects `aerodrome.weather?.wind ?: WindReport.NotReported` (pilot wiring) or (b) reads `aerodrome.weather` directly as a typed `WeatherObservation?` (controller view's `weather: WeatherObservation?` field). Both already handle nullability.
- The `MissingWeatherForRunwayAerodrome` invariant from `SimState.initial` (which previously rejected a runway-bearing aerodrome without a `weatherByAerodrome[id]` entry) becomes an invariant on `Aerodrome` itself: "a runway-bearing aerodrome must have `weather != null`." See Decision #6.

**Why not (3)?** The flat structure loses the cohesive `(wind, qnh, visibility)` triple that real ATC treats as a single observation. METAR is one report, not three. Keep the triple intact.

### 2. `WeatherObservation` location — move to `:core` (decided per dep-graph)

**Decided.** Move `WeatherObservation` from `:controller` (`ControllerTypes.kt:272`) to **`:core/world/WeatherObservation.kt`** (sibling to `WorldModel.kt`). `:controller` cannot stay as the home because:
- `Aerodrome` (the new owner) lives in `:core`.
- `:core` only depends on `:protocol`. It does NOT depend on `:controller` (and structurally cannot — `:controller` depends on `:core`).
- Therefore `Aerodrome.weather: WeatherObservation?` requires `WeatherObservation` to be reachable from `:core`.

**Alternative considered + rejected:** lift `WeatherObservation` to `:protocol`. `:protocol` is where `WindReport` and `PressureSetting` already live (fn-14.1 moved `WindReport` there). Arguments for: maximum reach; both `:core` and `:controller` (and `:sim`, `:pilot`) can import it.
**Why rejected:** `WeatherObservation` is **world-state**, not wire-protocol. It's a snapshot of an aerodrome's current observed conditions — not a transmission, not an instruction, not a clearance. `:protocol` is the lingua-franca-between-agents module (wire types); `:core` is the world model module (entities). `WeatherObservation` belongs with `Aerodrome` and `Runway` semantically. Keeping `:protocol` lean is the discipline.

**`:controller` consumers re-import.** The `WeatherObservation` symbol moves namespace from `xyz.easiersaid.twr.controller.WeatherObservation` to `xyz.easiersaid.twr.core.world.WeatherObservation`. Every `import xyz.easiersaid.twr.controller.WeatherObservation` line in `:controller`, `:sim` (production + tests), `:pilot` (tests only) updates. This is a mechanical rename across 48 files (per `grep -rn WeatherObservation`). The compiler catches every miss.

### 3. World-mutation lens — `AviationWorld.updateAerodrome(id) { transform }` extension (high confidence, mirrors fn-12 walk pattern)

**Decided.** Add a thin lens helper as a top-level extension on `AviationWorld` in `:core`:

```kotlin
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

**Why a helper, not raw `world.copy(aerodromes = ...)` at every call site:**
- The migration creates **one** new production write path (the sim's per-event weather-update handler — see Decision #5) and **several** test fixture write paths (every test that authors a wind shift mid-run). Without a helper, each becomes a four-line `world.aerodromes[id]?.let { ac -> world.copy(aerodromes = world.aerodromes + (id to ac.copy(weather = newWeather))) }`. The helper reduces to a single line.
- fn-12's per-cycle `expireRunwayObstructions` (in `RunwayObstructionWiring.kt:27-44`) walks `aerodromes.mapValues { ... aerodrome.copy(runways = updatedRunways) }`. That's an **all-aerodromes** walk because expiry might null any obstruction on any runway. **Weather mutations are single-aerodrome** (the test authors a wind shift at LOWG, not at every aerodrome at once), so a single-id lens is the right shape.
- Identity-equality short-circuit (`if (updated === current) this else ...`) preserves structural sharing when `transform` is a no-op, mirroring fn-12's pattern at `RunwayObstructionWiring.kt:39-44`.
- Inline `inline fun` avoids a lambda allocation per call (consistent with `core`'s perf-aware style — `WorldIndex` is in the same module).

**Alternative considered + rejected:** add `withAerodrome` as a `SimState` extension. Wrong layer — the lens is over the **world**, not over sim state. Tests sometimes need to update the world during fixture construction (before `SimState` exists). Live on `AviationWorld`.

### 4. Hard cutover, single epic — no shim, no parallel shape (locked per system-prompt rule)

**Decided.** Delete `SimState.weatherByAerodrome` in full. No deprecated field, no read-through shim, no "both shapes coexist temporarily" phase. Every reader and writer migrates in one pass, in one epic.

**Why no shim:**
- The codebase's standing rule: "no backwards-compat hacks." A shim that reads from both shapes would leave the rot indefinitely; the next epic that touched weather would just write to whichever shape was convenient.
- The flat-map has a small number of writers (1 production: `SimState.initial`; ~25 test fixtures) and a small number of readers (`PilotWiring`, `ControllerWiring`, `SimTraceQueries.weatherTransitions`). All call sites are accounted for via `grep -rn weatherByAerodrome`. The atomic-cutover risk is low.
- The compiler is the safety net. Removing the field breaks every call site that still reads/writes it; each break is mechanical to fix; the build doesn't pass until every site is migrated.

**The one allowed coexistence is intra-epic.** During the migration the IDE / compiler will see both shapes briefly as files are edited. The work-graph is structured so the final commit of the epic has neither (a) `SimState.weatherByAerodrome` nor (b) any direct mutations of it.

### 5. Sim-side write path — where does the writer live? (verified vs fn-12, decided)

**Decided.** Today `SimState.weatherByAerodrome` is **written exactly once** in production code: at `SimState.initial(...)` (the smart constructor) at `SimState.kt:227-265`. No `SimEvent` handler writes to it post-initialisation — weather is set at simulation start and stays constant unless tests author shifts via `state.copy(weatherByAerodrome = ...)` directly.

Post-migration, the production-write path becomes:
- `SimState.initial(...)` receives a `weatherByAerodrome: Map<AerodromeId, WeatherObservation>` parameter (kept for backwards-compatible caller ergonomics) and **folds it into the world** at construction time: `world = world.copy(aerodromes = ...with weather set on each)`. Then the `state.weatherByAerodrome` field is gone.
- The `MissingWeatherForRunwayAerodrome` validation runs against the **post-fold world** — i.e. checks `aerodrome.weather != null` for every runway-bearing aerodrome.
- Test fixtures keep passing the same `weatherByAerodrome` map; the constructor handles the fold. **No test-fixture migration needed for fixtures that only pass via `SimState.initial`** (most cases).

**Test fixtures that mutate weather mid-run.** `G3aPilotReactiveCrosswindTest.kt:797-802` mutates via `st.copy(weatherByAerodrome = st.weatherByAerodrome + (id to weather))` in an `onAfterEvent` hook. Post-migration, this becomes:

```kotlin
private fun authorWeather(
    st: SimState,
    aerodromeId: AerodromeId,
    weather: WeatherObservation,
): SimState =
    st.copy(world = st.world.updateAerodrome(aerodromeId) { it.copy(weather = weather) })
```

Same single-line shape, using the new lens helper from Decision #3.

**No new `SimEvent` leaf.** A weather-shift event-leaf (e.g. `SimEvent.WeatherChanged(aerodromeId, observation)`) is structurally cleaner than a `state.copy` test mutation, but it's out of scope for this epic — the world-only-test-trigger discipline (per `feedback_world_only_test_triggers.md`) is satisfied by direct world mutation via the lens, just as fn-12 used direct `runway.obstruction` mutation via a fixture-side helper. A typed event-leaf is filed as `D-PASS-weather-shift-event-leaf`.

### 6. Smart-constructor invariants — TWO checks, no silent drops (refined per codex round 1)

**Decided.** Two invariants on the post-fold world, BOTH enforced at `SimState.initial` before constructing the state:

**Invariant A (existing — predicate source migrates):** `MissingWeatherForRunwayAerodrome(aerodromeId)` — every runway-bearing aerodrome must have a weather observation. Pre-migration this read the flat-map; post-migration it reads the entity:

```kotlin
if (aerodrome.runways.isNotEmpty() && aerodrome.weather == null) {
    return InitError.MissingWeatherForRunwayAerodrome(aerodromeId).left()
}
```

**Invariant B (NEW — closes the silent-drop window):** `WeatherForUnknownAerodrome(aerodromeId)` — every key in the `weatherByAerodrome: Map<...>` parameter must correspond to an aerodrome present in `world.aerodromes`. Without this check, the smart constructor would silently drop a typo'd id (`updateAerodrome` no-ops on absent id by design — the lens is a generic shape that returns the world unchanged when the id is absent; using it as a silent-drop write at a validation boundary would violate `feedback_no_corners.md`).

```kotlin
for ((aerodromeId, _) in weatherByAerodrome) {
    if (aerodromeId !in world.aerodromes) {
        return InitError.WeatherForUnknownAerodrome(aerodromeId).left()
    }
}
```

**Order of checks** (in `SimState.initial`):
1. Validate every weather key is known (Invariant B) — fail fast on typo'd ids before any fold work.
2. Fold weather into the world.
3. Validate every runway-bearing aerodrome has weather (Invariant A) — reads from the post-fold world.

**Why both checks at this boundary, not at the lens:** `AviationWorld.updateAerodrome` is a generic lens reusable by tests, future migrations, and any single-aerodrome mutation. Its absent-id-no-op is a legitimate generic-lens shape (the caller might be probing for an aerodrome that may not exist). The simulator's `SimState.initial` is the validation boundary; making it loud is the right place.

**Invariant rationale unchanged** ("a runway-bearing aerodrome that lacks a weather observation makes the controller's runway-into-wind selection silently pick a default; refuse to construct"). Plus the new invariant ("a typo'd aerodrome id in the weather parameter would silently drop the observation, leaving the runway-bearing aerodrome with null weather and triggering Invariant A obscurely; surface the typo directly").

**Why keep the checks in `SimState.initial` rather than promoting to `Aerodrome.init`:** `Aerodrome` is a pure data class that doesn't know whether the simulator will use it. A `core`-test world-construction fixture that builds an aerodrome without weather (e.g. testing the loader, or a parser round-trip) is valid; only the **simulator** needs the weather present. Keep the simulator-specific predicates in the simulator's smart constructor. Mirrors fn-12: `Runway.obstruction` defaults null, and the simulator doesn't require it (obstructions are optional state).

### 7. Reader migration table (high confidence per grep audit)

| Reader | File:line | Pre-migration | Post-migration |
|--------|-----------|---------------|-----------------|
| Pilot wiring | `sim/.../PilotWiring.kt:41` | `state.weatherByAerodrome.mapValues { obs.wind }` | `state.world.aerodromes.mapValues { (_, a) -> a.weather?.wind ?: WindReport.NotReported }` |
| Controller wiring | `sim/.../ControllerWiring.kt:150` | `state.weatherByAerodrome[spec.aerodromeId]` | `state.world.aerodromes[spec.aerodromeId]?.weather` |
| Sim trace extractor | `sim/.../testing/SimTraceQueries.kt:208-211` | `Option.fromNullable(st.weatherByAerodrome[aerodrome])` | `Option.fromNullable(st.world.aerodromes[aerodrome]?.weather)` |

These three are the **complete** set of production-and-test readers. `ControllerView.weather` (controller side) and `PilotInput.weatherByAerodrome` (pilot side) keep their shapes unchanged — only the wiring sourcing them updates.

**`worldIndex` consideration.** `WorldIndex` is a derived structure built once from `AviationWorld` (see `core/.../world/WorldModel.kt:418-458`). It doesn't currently project weather. Post-migration, readers walk `state.world.aerodromes` directly rather than going through the index — same as fn-12's runway-obstruction readers (which read `state.world.aerodromes[id].runways[*].obstruction`, not via the index). No `WorldIndex` change.

### 8. Test fixture migration — enumerable, mechanical (high confidence)

**Decided.** Every test-fixture call site that constructs `SimState.initial(... weatherByAerodrome = ...)` keeps the same call shape — the smart constructor handles the fold (Decision #5). The only fixture sites that need active migration are:

1. **Mid-run mutations**: `G3aPilotReactiveCrosswindTest.kt:802` — migrate to use `world.updateAerodrome` lens (one line change per Decision #5).
2. **Direct reads of `state.weatherByAerodrome[id]`**: zero in production code; the test surface uses `weatherTransitions(aerodrome)` from `SimTraceQueries.kt` which itself migrates per Decision #7.
3. **`Fixture.weatherByAerodrome` field** at `Fixture.kt:504` (the `MultiAerodromeFixture` struct's `weatherByAerodrome: Map<AerodromeId, WeatherObservation>` field): stays as-is (it's a fixture-input shape), the consumer (`G2CrossAerodromeVfrTest.kt:248`) passes it through to `SimState.initial` which folds.

Enumeration of all 48 weather sites (grep result):
- **Production code** (5 files): `SimState.kt`, `PilotWiring.kt`, `ControllerWiring.kt`, plus the readers above.
- **Tests** (25+ files): grep-scriptable; all pass weather to `SimState.initial(weatherByAerodrome = ...)` and rely on it being constant for the run, except `G3aPilotReactiveCrosswindTest.kt` (the one mutator).
- **Trace extractor**: `SimTraceQueries.kt:208-211` (migrate per Decision #7).
- **Fixture struct**: `Fixture.kt:504` (unchanged shape; pass-through).

Acceptance criterion R7 below enumerates the audit script.

### 9. KDoc + memory updates (high confidence)

**Decided.** Update KDoc on:
- `Aerodrome.weather` — document the field per fn-16, mirror `Runway.obstruction`'s KDoc shape (purpose, single-writer site, default-null rationale).
- `WeatherObservation` (relocated) — note the move from `:controller` to `:core`, motivation per `project_rich_world_domain.md`.
- `SimState` — remove the `weatherByAerodrome` field reference from the class KDoc; add a note in `SimState.initial` that the `weatherByAerodrome` parameter is folded into `world.aerodromes[*].weather` at construction time.
- `SimState.InitError.MissingWeatherForRunwayAerodrome` — KDoc updated to reflect the new source (`aerodrome.weather == null`).
- `PilotWiring.buildPilotInput` — KDoc updated to reflect the new source walk.
- `ControllerWiring.buildControllerView` — KDoc updated for the `weather` field's new source.

**Memory entry**: update the **existing** `project_rich_world_domain.md` auto-memory entry **in place** (do not create a new file) to confirm the precedent list now contains both `Runway.obstruction` (fn-12) and `Aerodrome.weather` (fn-16). The principle file already exists — fn-16 grows the precedent list, not the principle. Updating in place keeps the principle and its precedents colocated. Picked per codex round 1 to resolve the epic-vs-task inconsistency.

### 10. Two-task split — atomic foundation + sweep (high confidence)

**Decided.** Split as Option A from the brief: two tasks.

- **fn-16.1 — Foundation + atomic field migration.**
  - Move `WeatherObservation` to `:core/world/`.
  - Add `Aerodrome.weather: WeatherObservation? = null`.
  - Add `AviationWorld.updateAerodrome(id) { transform }` lens.
  - Migrate the **three production readers** (`PilotWiring`, `ControllerWiring`, `SimTraceQueries.weatherTransitions`).
  - Migrate `SimState.initial` to fold weather into the world at construction time; update `InitError.MissingWeatherForRunwayAerodrome` predicate.
  - **Delete `SimState.weatherByAerodrome` field.**
  - Migrate the **one** mutating test (`G3aPilotReactiveCrosswindTest`) to use the lens helper.
  - Compile — every test-fixture site that constructs `SimState.initial` via the existing `weatherByAerodrome` parameter still compiles (smart constructor handles the fold).
  - Acceptance: full build green; eight existing goldens green; detekt baseline unchanged.

- **fn-16.2 — Test fixture sweep + KDoc / memory updates.**
  - Audit script (grep `weatherByAerodrome` in `:sim/jvmTest`) confirms zero direct accesses post-.1; the remaining references are only the constructor-parameter name passed to `SimState.initial` (which is acceptable — it's a parameter, not a field).
  - **Optional decision at .2 time**: rename the `SimState.initial(weatherByAerodrome = ...)` parameter to something more aligned with the new shape, e.g. `initialWeather` or `weatherByAerodromeAtStart`. If renamed, audit all 25+ test fixture call sites. **Default = keep the parameter name**, since it's purely a parameter name and changing it adds churn without architectural value. Pin the decision in the task spec.
  - KDoc updates per Decision #9.
  - Memory entry added.
  - Acceptance: full build green; detekt baseline unchanged; documentation reflects the new shape.

**Why not three tasks (Option B from the brief):** the field + lens + sim-side writes naturally hold together (atomic cutover), and separating them would leave a state where the field exists but no writer is migrated — the build wouldn't compile mid-task. fn-12's foundation pass (fn-12.1) was the same shape: typed surface + sim wiring + reactive rule all in one pass per `feedback_pass_scope.md` ("each pass closes more rot than it spawns"). One coupled atomic pass + one sweep pass mirrors the precedent.

## Acceptance

- **R1: `WeatherObservation` relocated to `:core`.** Move the data class from `controller/.../ControllerTypes.kt:272` (the existing definition) to `core/.../world/WeatherObservation.kt`. Shape unchanged: `data class WeatherObservation(val wind: WindReport, val qnh: PressureSetting?, val visibility: Int?)`. Imports `xyz.easiersaid.twr.protocol.WindReport` and `xyz.easiersaid.twr.protocol.PressureSetting` (both already in `:protocol`). KDoc updated to note the move motivation per `project_rich_world_domain.md`. Migration covers **three** access shapes (per codex round 2):
  1. Stale `import xyz.easiersaid.twr.controller.WeatherObservation` lines in `:sim`, `:pilot` tests, etc. — sed-replaceable.
  2. Fully-qualified inline references `xyz.easiersaid.twr.controller.WeatherObservation` (e.g. `PerAircraftRngSpec.kt:64,71,87,181`) — same sed pattern.
  3. **Same-package simple-name usages inside `:controller`** that currently need no import. `ControllerTypes.kt:54` (`val weather: WeatherObservation?` on `ControllerView`), `Guard.kt:36,695` (`weather: WeatherObservation?` on `OperatorContext` + reader inside guard), `Controller.kt:963` — each file gets a new `import xyz.easiersaid.twr.core.world.WeatherObservation` after the relocation. Audit: grep `WeatherObservation` inside `:controller` after the move; every file that uses the type but has no import now needs one. The compiler catches unresolved simple-name references.

- **R2: `Aerodrome.weather: WeatherObservation? = null` field added.** At `core/.../world/WorldModel.kt:333-388` (`Aerodrome` data class). **Final field in the constructor parameter list** (placed AFTER `ctrApproximationRadius` to avoid positional-arg call-site churn). Default-null. KDoc mirrors `Runway.obstruction`'s shape — documents that the field is the home for time-varying weather state, defaults null for aerodromes that haven't been observed, set by the sim's `SimState.initial` fold + the `AviationWorld.updateAerodrome` lens for mid-run shifts.

- **R3: `AviationWorld.updateAerodrome(id) { transform }` lens helper added.** As an inline extension in `core/.../world/` (sibling to `WorldModel.kt`; new file `WorldLenses.kt` or appended to `WorldModel.kt` — pick whichever has the cleaner KDoc surface). Identity-equality short-circuit when the transform produces the input unchanged. KDoc cites the fn-12 walk pattern in `RunwayObstructionWiring.kt:27-44` as the all-aerodromes precedent and notes this lens is the single-id counterpart. **Unit test** in `core/src/commonTest/.../WorldLensesSpec.kt` covers: (a) updates an existing aerodrome; (b) returns the input world unchanged when the id is absent; (c) preserves structural sharing when the transform is a no-op (identity-equality).

- **R4: `SimState.initial` folds `weatherByAerodrome` parameter into the world AFTER validating every key is known.** At `SimState.kt:221-269`. The `weatherByAerodrome: Map<AerodromeId, WeatherObservation>` parameter is kept (caller ergonomics unchanged). **Order of operations:**
    1. **NEW Invariant B check** (per Decision #6): for every `(id, _) in weatherByAerodrome`, fail loud if `id !in world.aerodromes` — return `InitError.WeatherForUnknownAerodrome(id).left()`. Prevents the silent-drop window where `updateAerodrome`'s no-op-on-absent-id semantics would silently lose a typo'd weather key.
    2. **Fold**: walk each entry and set `world.aerodromes[id].weather = observation` via `world.updateAerodrome(id) { it.copy(weather = obs) }`. (Safe because step 1 confirmed every id is present.)
    3. The folded world is passed forward into the constructed `SimState`.
    
    The map-iteration-order of the fold is deterministic for the `Map<AerodromeId, _>` parameter the test fixtures pass (`mapOf(...)`).

- **R5: `SimState.InitError.MissingWeatherForRunwayAerodrome` + NEW `WeatherForUnknownAerodrome` invariants.** At `SimState.kt:151-206` (`InitError` sealed interface) and `:229-232` (current check site).
    - **R5a:** Existing `MissingWeatherForRunwayAerodrome(aerodromeId)` variant's check at line 230 migrates from map-based to `aerodrome.weather == null`. The check runs against the **post-fold world** (per R4). Variant shape unchanged. KDoc updated to reflect the new source (entity, not map).
    - **R5b (NEW):** Add `InitError.WeatherForUnknownAerodrome(aerodromeId: AerodromeId)` variant to the sealed interface. KDoc cites the silent-drop closure motivation. Pre-fold check (R4 step 1) returns this error on typo'd keys. The variant is structurally identical to the existing `MissingWeatherForRunwayAerodrome` (single `aerodromeId` field) — same shape, different invariant.
    - **R5b-audit (NEW per codex round 2)**: audit every test call to `SimState.initial(...)` that passes a non-empty `weatherByAerodrome` AND defaults the `world` parameter (i.e. `world = AviationWorld()` — the default at SimState.kt:223). Such tests would previously have constructed silently (the map carried the data; `world` was empty); post-migration they will fail with `WeatherForUnknownAerodrome` because the weather map keys aren't in the (empty) `world.aerodromes`. Grep: `grep -B2 "weatherByAerodrome\s*=" --include="*.kt" sim/src/jvmTest/ | grep -A2 "SimState.initial"` plus manual review. Each hit gets either (a) a matching `world` parameter authored, or (b) the `weatherByAerodrome` parameter dropped/emptied if the test doesn't need weather. Expected hits: low (the dominant pattern is `world = world` from a Fixture-loaded world matched by the weather map).

- **R6: `SimState.weatherByAerodrome` field DELETED + every direct constructor call site migrated.** No field, no deprecation, no parallel shape. The `val weatherByAerodrome: Map<AerodromeId, WeatherObservation>` line at `SimState.kt:68` is removed; the corresponding `weatherByAerodrome = weatherByAerodrome` argument at `SimState.kt:265` is removed; the corresponding KDoc reference is removed.
  
  **R6-constructor-sweep (NEW per codex round 2)**: tests that **bypass `SimState.initial` and construct `SimState(...)` directly** must also migrate. Pre-migration list (8 confirmed call sites via `grep "SimState(" --include="*.kt"`):
  - `sim/src/jvmTest/.../AtisSpec.kt:59`
  - `sim/src/jvmTest/.../MissedHandoffEventSpec.kt:48`
  - `sim/src/jvmTest/.../ResponsibilityInvariantSpec.kt:207`
  - `sim/src/jvmTest/.../ResponsibilityStateMachineSpec.kt:66`
  - `sim/src/jvmTest/.../MissedHandoffProjectionSpec.kt:58`
  - `sim/src/jvmTest/.../RadarServiceTerminatedSpec.kt:64`
  - `sim/src/jvmTest/.../FlightPlanFilingSpec.kt:46`
  - `sim/src/jvmTest/.../KnownStripsHandoffTransitionSpec.kt:60`
  
  Each site passes `weatherByAerodrome = emptyMap()` (or similar) directly as a constructor arg. Post-migration these sites:
  - **Remove the `weatherByAerodrome = emptyMap()` named arg** entirely (field is gone).
  - Confirm the test scenario doesn't need weather — every one currently passes `emptyMap()` (no runway-bearing aerodrome modelled, or the test doesn't exercise weather), so no weather authoring is needed.
  - If a test's `world` parameter has runway-bearing aerodromes but uses the direct constructor (bypassing `SimState.initial`'s invariant check), the direct constructor is now structurally weaker (no `MissingWeatherForRunwayAerodrome` enforcement). v1 acceptance: leave the direct constructor as-is post-migration (no behaviour change vs pre-migration; the field is just gone). If any test fails because the controller silently picks a default for runway-into-wind in the absence of weather, root-cause and either: (a) switch the test to `SimState.initial` (canonical), or (b) author weather on the world via `world.updateAerodrome(id) { it.copy(weather = ...) }` before the direct constructor call. **Filed as a sibling audit**: `D-PASS-direct-simstate-constructor-canonicalization` — promoting every direct constructor site to `SimState.initial` is the larger refactor; v1 fn-16 keeps the direct-constructor escape hatch for fast unit tests but acknowledges the weakened invariant.
  
  The compiler catches every direct-constructor site (the named-arg `weatherByAerodrome = ...` becomes invalid; positional sites that passed it would also break). Build green is the bar.

- **R7: All three production readers migrated. R7a's projection form pinned (`mapNotNull`) at planning time per codex round 1 — no implementation-time decision deferred.**
  - **R7a (PilotWiring):** `sim/.../PilotWiring.kt:41` projects from `state.world.aerodromes` instead of `state.weatherByAerodrome`. **Decided form (pinned at planning time per codex round 1):**
    ```kotlin
    weatherByAerodrome = state.world.aerodromes
        .mapNotNull { (id, a) -> a.weather?.wind?.let { id to it } }
        .toMap(),
    ```
    **Rationale — preserve pre-migration absent-key semantics exactly.** Pre-migration: `state.weatherByAerodrome.mapValues { obs.wind }` projects only the aerodromes the simulator was authored to observe (keys present in the initial `weatherByAerodrome` parameter). An aerodrome present in `world.aerodromes` but absent from the weather map (the `aerodrome.runways.isEmpty() && id !in weatherByAerodrome` case — a non-runway aerodrome) was silently skipped — the pilot saw a `PilotInput.weatherByAerodrome` map with no entry for that id, and `windForMission`'s singleton-fallback / multi-aerodrome branches handled "missing key" correctly. Post-migration, if we project **every** aerodrome (using `mapValues { weather?.wind ?: WindReport.NotReported }`), an aerodrome with `weather == null` would now produce a `NotReported` entry where pre-migration produced no entry at all. That changes `windForMission`'s singleton-fallback behaviour: in a multi-aerodrome world with one weathered + one null-weather aerodrome, the map size becomes 2 (was 1), so the `map.size == 1` singleton-fallback path stops firing. The `mapNotNull` form preserves the pre-migration key set exactly. **Behaviour-equivalence is the requirement** — existing `PilotCrosswindTickATickBTest` + `WindForMissionTest` + `FirewallPilotInputTest` stay GREEN unchanged. KDoc updated to cite this decision.
  - **R7b (ControllerWiring):** `sim/.../ControllerWiring.kt:150` reads from `state.world.aerodromes[spec.aerodromeId]?.weather` instead of `state.weatherByAerodrome[spec.aerodromeId]`. `ControllerView.weather` field shape unchanged. KDoc on the call site updated.
  - **R7c (SimTraceQueries.weatherTransitions):** `sim/.../testing/SimTraceQueries.kt:208-211` reads from `st.world.aerodromes[aerodrome]?.weather` instead of `st.weatherByAerodrome[aerodrome]`. Trace shape (`Transition<Option<WeatherObservation>>`) unchanged. KDoc updated to reflect the new source.

- **R8: G3aPilotReactiveCrosswindTest mutator migrates to the lens helper.** At `G3aPilotReactiveCrosswindTest.kt:797-802`, `authorWeather` becomes:
  ```kotlin
  private fun authorWeather(
      st: SimState,
      aerodromeId: AerodromeId,
      weather: WeatherObservation,
  ): SimState =
      st.copy(world = st.world.updateAerodrome(aerodromeId) { it.copy(weather = weather) })
  ```
  KDoc updated to cite the lens helper. The two existing one-shot guards (`crosswindAuthored`, `crosswindClearedToLimit`) keep their shape.

- **R9: Test-fixture sweep audit — grep-scriptable.** After R1-R8 land, the only remaining references to `weatherByAerodrome` in the entire codebase are:
  - `SimState.initial(weatherByAerodrome: Map<...>, ...)` — parameter name (kept per Decision #10 default).
  - `Fixture.MultiAerodromeFixture.weatherByAerodrome` — fixture struct field (kept per Decision #8 #3).
  - Test fixture call sites that pass `weatherByAerodrome = mapOf(...)` to `SimState.initial(...)` — unchanged.
  - `PilotInput.weatherByAerodrome` — pilot-side field name (fn-14.1 introduced; unchanged).
  - `PilotWiring.buildPilotInput`'s `weatherByAerodrome = ...` named-arg call site (unchanged; only the source expression changes per R7a).
  - `:sim`'s `weatherTransitions(aerodrome)` extractor name (unchanged; only the body changes per R7c).
  - KDoc references in `:pilot` / `:controller` / `:sim` cross-referencing the old SimState field — these get cleaned in fn-16.2 per R12.
  
  The forbidden remaining usage post-migration is **any direct reference to `SimState.weatherByAerodrome` as a field**. Verify via `grep -n "\.weatherByAerodrome\b" --include="*.kt"` and check every hit:
  - `SimState`-typed receivers must produce a compile error (since the field is deleted).
  - `PilotInput`-typed receivers are fine (different field on a different type).
  - Map-literal arguments to `SimState.initial(weatherByAerodrome = ...)` are fine (parameter name).
  - `Fixture.weatherByAerodrome` is fine (different type's field).

- **R10: KDoc updates landed in fn-16.2.**
  - `Aerodrome.weather` (per R2).
  - `AviationWorld.updateAerodrome` (per R3).
  - `WeatherObservation` (per R1).
  - `SimState.initial` parameter (per R4) and the class KDoc (remove the `weatherByAerodrome` mention from the field-iteration block).
  - `SimState.InitError.MissingWeatherForRunwayAerodrome` (per R5).
  - `PilotWiring.buildPilotInput` (per R7a).
  - `ControllerWiring.buildControllerView` (per R7b).
  - `SimTraceQueries.weatherTransitions` (per R7c).
  - **Cross-reference scrub**: every `// fn-14` or `// fn-12` KDoc block that mentions "wind state migration is filed as `D-PASS-wind-state-migrate-to-aerodrome`" gets updated to note the deferment is **closed** in fn-16. Locations: `PilotInput.kt:71-72`, `pilot/.../observe/PilotEvent.kt` (verify), `controller/.../ControllerTypes.kt:262-271` (WeatherObservation KDoc), and any other "D-PASS-wind-state-migrate-to-aerodrome" reference. Grep `D-PASS-wind-state-migrate-to-aerodrome` to enumerate.

- **R11: Memory entry updated IN PLACE if present, else recorded in evidence (per codex round 4/7 refinement).** The existing `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/project_rich_world_domain.md` lives outside the repo (in the user's `~/.claude/` directory) and may not exist in all environments. **Unconditional rule (per codex round 7):**
  - **If the file exists**: append the two-precedents block (fn-12 / fn-16) and the "next slice follows the same shape" guidance in place. **Do not create a new memory file.**
  - **If the file is absent**: do NOT create it. Record the missing-file state in the task evidence note + capture the exact intended-append text in evidence for later user update.
  - **No environment-dependent behaviour** (CI / local / contributor — the rule is the same). User-memory files are user-authored; the task spec does not author them ex nihilo.
  - fn-16.2 task spec captures the exact intended-append text for evidence reuse.

- **R12: Build green — full verify.** `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt` exits 0. **All eight goldens green** (G0 / G1 / G1-min / G2 / G3a-trained / G3a-obstruction / G3a-obstruction-continue-approach / G3a-react). `detekt` baseline unchanged.

- **R13: `.plan` (in-repo, repo-root, canonical project-local backlog) reconciled (NEW per codex round 8).** Per `.plan:484`'s standing register-split convention, `.plan` and `~/.claude/plans/pilot-firewall.md` are sister registers carrying identical D-PF/D-AUDIT/D-PASS entries; `.plan` is the in-repo discoverability surface (on-repo readers, CI, fresh-clone reviewers resolve every deferment from `.plan` alone). fn-16's deferment-register edits must update both registers consistently. Acceptance:
  - `.plan` scanned for `D-PASS-wind-state-migrate-to-aerodrome` entry; if present, marked `DONE (2026-05-11, fn-16)` per `.plan`'s DONE convention; if absent, recorded in evidence (the deferment may have been mirrored only into the external register; either way, fn-16's outcome is consistent: closed in both).
  - All 7 NEW deferments from this epic appended to `.plan` with the full four-field contract format (what-today / why-wrong / real-fix-contract / trigger) per the pattern at `.plan:482-516` ("fn-8.3 G1 closure deferments"), Impact × Effort grading.
  - `.plan` is in-repo — no missing-file branch (unlike R11's external-memory user file).
  - The 7 NEW deferments are the same set appended to `~/.claude/plans/pilot-firewall.md` per the existing closure entry; the two registers must end up consistent.

## Strategy drift flagged for review

_(none — closes a known deferment from fn-14, aligns the codebase with the locked `project_rich_world_domain.md` principle. Reduces rot; no new functional capability; no scope expansion.)_

## Quick commands

```bash
./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt
./gradlew :core:allTests --tests "xyz.easiersaid.twr.core.world.WorldLensesSpec"
./gradlew :sim:jvmTest --tests "xyz.easiersaid.twr.sim.G3aPilotReactiveCrosswindTest"

# Sweep audit (run after fn-16.1 lands, before fn-16.2 starts):
grep -rn "\.weatherByAerodrome\b" --include="*.kt" .  # All remaining references
grep -rn "weatherByAerodrome" --include="*.kt" .       # All remaining (incl. param names)
grep -rn "D-PASS-wind-state-migrate-to-aerodrome" --include="*.kt" --include="*.md" .  # All deferment refs to close
```

## Approach

### Two-task split (per Decision #10)

1. **Task .1 — Foundation + atomic field migration.** Move `WeatherObservation` to `:core`. Add `Aerodrome.weather` + `AviationWorld.updateAerodrome` lens. Migrate the three production readers + `SimState.initial` fold + delete the `SimState.weatherByAerodrome` field. Migrate the one mutating test. Build green; eight goldens green.
2. **Task .2 — Test fixture sweep + KDoc / memory updates.** Audit confirms the sweep; close cross-reference KDocs that point at the closed deferment; memory entry added; build green; detekt unchanged.

### Reuse points (file:line refs)

| Surface | Reuse | New code |
|---------|-------|----------|
| `Runway.obstruction` precedent | `core/.../world/WorldModel.kt:151-175` (exists) | Mirror exactly for `Aerodrome.weather` |
| All-aerodromes mutation walk | `sim/.../RunwayObstructionWiring.kt:27-44` (`expireRunwayObstructions`) | Reference; new lens is single-id counterpart |
| `Aerodrome` data class | `core/.../world/WorldModel.kt:333-388` (exists) | Add `weather: WeatherObservation? = null` final field |
| `WeatherObservation` | `controller/.../ControllerTypes.kt:272-276` (exists) | Move to `core/.../world/WeatherObservation.kt`; namespace change only |
| `WindReport` sealed | `protocol/.../WindReport.kt` (fn-14.1) | Unchanged; consumed by the relocated `WeatherObservation` |
| `PressureSetting` sealed | `protocol/.../Instruction.kt:213-236` (exists) | Unchanged; consumed by the relocated `WeatherObservation` |
| Sim smart constructor | `sim/.../SimState.kt:221-269` (exists) | Fold `weatherByAerodrome` parameter into `world` at construction |
| Sim invariant | `sim/.../SimState.kt:158-160` (`MissingWeatherForRunwayAerodrome`) | Predicate source migrates from map to `aerodrome.weather` |
| Pilot wiring | `sim/.../PilotWiring.kt:41` (exists) | Walk `world.aerodromes` instead of `state.weatherByAerodrome` |
| Controller wiring | `sim/.../ControllerWiring.kt:150` (exists) | Walk `world.aerodromes[id]?.weather` instead of `state.weatherByAerodrome[id]` |
| Trace extractor | `sim/.../testing/SimTraceQueries.kt:208-211` (exists) | Walk `world.aerodromes[id]?.weather` |
| Mutator test | `sim/.../G3aPilotReactiveCrosswindTest.kt:797-802` (exists) | Use new lens helper |
| Multi-aerodrome fixture struct | `sim/.../testing/Fixture.kt:480-515` (exists) | Unchanged; passes through to `SimState.initial` |
| Pilot input shape | `pilot/.../PilotInput.kt:35-83` (exists, fn-14.1) | Unchanged shape; only the wiring source updates |
| Controller view shape | `controller/.../ControllerTypes.kt:44-156` (exists) | Unchanged shape; only the wiring source updates |
| Deferment register | `~/.claude/plans/pilot-firewall.md` | Close `D-PASS-wind-state-migrate-to-aerodrome` |

## Test notes

This epic is a refactor — no new behaviour, no new test goldens. The acceptance bar is **behavioural equivalence**: every existing test (75+ sim/pilot/controller tests) passes unchanged after R1-R12. The eight golden sim tests are the load-bearing check.

**New unit test** (per R3): `core/.../WorldLensesSpec.kt` for the `AviationWorld.updateAerodrome` lens. Three cases (update, absent, identity).

**Behavioural-equivalence spot checks**: the three readers (R7a/R7b/R7c) are the highest-risk migration sites because they touch hot paths (pilot wiring runs every pilot tick; controller wiring runs every controller cycle). Specific equivalence pins:
- **R7a (PilotWiring)** — **pinned form `mapNotNull` per codex round 1**. Pre-migration's `mapValues { obs.wind }` produced `Map<AerodromeId, WindReport>` where `id !in map` → caller's `windForMission` returns null / its singleton-fallback fires for `map.size == 1`. The pinned post-migration projection:
  ```kotlin
  state.world.aerodromes.mapNotNull { (id, a) -> a.weather?.wind?.let { id to it } }.toMap()
  ```
  preserves the pre-migration key set exactly — an aerodrome with `weather == null` produces NO entry (same as pre-migration's absent key). The alternative `mapValues { weather?.wind ?: NotReported }` form was considered and rejected because it would produce a `NotReported` entry for every aerodrome including those with `weather == null`, breaking `windForMission`'s `map.size == 1` singleton-fallback path in multi-aerodrome worlds (1 weathered + 1 null-weather aerodrome becomes a 2-entry map vs a 1-entry map). The `WindForMissionTest` (`pilot/.../WindForMissionTest.kt`) is the regression backstop.
- **R7b (ControllerWiring)**: pre-migration `state.weatherByAerodrome[id]` returns `null` if absent; post-migration `state.world.aerodromes[id]?.weather` returns `null` if either (a) aerodrome absent OR (b) aerodrome has `weather == null`. **Case (a) was impossible pre-migration** (the smart constructor required every aerodrome key to exist in `weatherByAerodrome` for runway-bearing aerodromes — non-runway aerodromes could be missing, but those are also absent from `state.world.aerodromes` until they're explicitly added). Case (b) is the new case but matches the controller's null-tolerant read (`ControllerView.weather: WeatherObservation?` is already nullable; downstream consumers handle null). **No behavioural change.**
- **R7c (SimTraceQueries.weatherTransitions)**: `Option.fromNullable(...)` over a map lookup; same null-handling either way. The migration is purely a source change. Trace pin tests stay green.

## Review considerations

### FP / type-safety axis
- `Aerodrome.weather: WeatherObservation? = null` mirrors `Runway.obstruction: RunwayObstruction? = null` exactly. Two precedents now, same shape.
- `AviationWorld.updateAerodrome` is a thin lens. Inline. Identity-short-circuit on no-op transform. Pure.
- `WeatherObservation` move from `:controller` to `:core` is namespace-only (no shape change). Compiler enforces every consumer's import update.
- `SimState.initial`'s `weatherByAerodrome: Map<...>` parameter retained — caller ergonomics preserved; smart constructor handles the fold.
- The `MissingWeatherForRunwayAerodrome` invariant retained byte-equivalent; only its source predicate changes.
- No null introduction at reader sites — every reader either projects through a nullable chain (`?.weather?.wind`) or treats absence the same way the old map's `get` did (returning null/Option).

### Test architecture axis
- No new goldens. Existing 75+ tests are the equivalence bar.
- One new unit test (`WorldLensesSpec`) for the new lens helper.
- The behavioural-equivalence reasoning at R7a (the only non-trivial reader migration) is explicit and grep-able; if a discrepancy emerges, `WindForMissionTest` + `PilotCrosswindTickATickBTest` are the regression backstops.
- Test fixture migration is enumerable and grep-scriptable.
- `G3aPilotReactiveCrosswindTest`'s mutator transform is a one-line change.

### Impact axis
- **Compile-impact dominates.** Moving `WeatherObservation`'s namespace touches ~48 files (every import). The compiler catches all of them; no silent breakage possible.
- **Field-deletion impact**: every `SimState.weatherByAerodrome` reader becomes a compile error. Three production sites accounted for in R7; trace extractor in R7c; one test mutator in R8. Comprehensive.
- **Smart constructor impact zero**: every test fixture passing `SimState.initial(weatherByAerodrome = ...)` continues to work — the fold happens inside the constructor.
- **`PilotInput` / `ControllerView` shapes unchanged.** The firewall surfaces don't move; no firewall-test changes needed.
- **No `AftnRouting` / cross-aerodrome coupling**: weather doesn't ride wire transmissions in twr2; it's read directly from world state via wiring. Migration scope contained.

### Operational axis
- Refactor only; no determinism, performance, or replay impact.
- `WorldIndex` unchanged (weather isn't indexed; readers walk `world.aerodromes` directly).
- `world.copy(aerodromes = ...)` per-mutation has the same cost as the pre-migration `state.copy(weatherByAerodrome = ...)` (both replace a Map with one entry overridden). No measurable perf delta.
- Smart constructor fold runs once at simulation start; negligible.

## Early proof point

**Task fn-16.1** lands the foundation atomically: the field, the lens, the three production readers, the deleted SimState field, the one test mutator. The full `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt` is the proof — if any of the 75+ existing tests regress, the migration's behavioural-equivalence assumption is wrong and the issue surfaces immediately. Task .2 is a paper-trail sweep that adds no behavioural risk.

## References

### Doctrinal
- **`project_rich_world_domain.md`** auto-memory entry (2026-05-10) — "Time-varying state lives on the entity, not flat World-root maps." Decided for `Runway.obstruction` (fn-12); applied to `Aerodrome.weather` here.
- **Domain-modelling rationale**: METAR/TAF reports are addressed by aerodrome (`METAR LOWG ...`) — the product shape is aerodrome-scoped, which confirms the entity-on-aerodrome choice is reality-anchored. (No exact ICAO Annex citation pinned here per codex round 5; the domain-modelling argument stands without one, and a deeper regulatory cite belongs in the future METAR-ingestion epic per `D-PASS-metar-taf-ingestion` where the wire-format is the actual subject.)

### Codebase prior art
- **fn-12** — `Runway.obstruction: RunwayObstruction?` migration. Sets the precedent shape. `RunwayObstructionWiring.kt:27-44` is the all-aerodromes walk pattern.
- **fn-14** — added the pilot-side `WindReport` consumer; filed `D-PASS-wind-state-migrate-to-aerodrome` (this epic closes it). `PilotWiring.kt:41` is the reader migrating in R7a.
- **fn-14.1** — `WindReport` moved from `:controller` to `:protocol`. Sets the precedent that weather-component types relocate cleanly across module boundaries. `WeatherObservation` follows the same pattern but lands in `:core` instead of `:protocol` (per Decision #2).

### Memory
- `project_rich_world_domain.md` — the principle being applied.
- `feedback_world_only_test_triggers.md` — test fixtures author world state, not agent decisions; `G3aPilotReactiveCrosswindTest`'s mutator pattern is the only mid-run weather author.
- `feedback_pass_scope.md` — bundle field + lens + readers + writer migration in one pass (fn-16.1).
- `feedback_no_corners.md` — hard cutover; no shim; the field is deleted, not deprecated.
- `feedback_plans_review_aware.md` — Review considerations addressed inline.

## Deferments register

**MIGRATED to `docs/deferments.md` per fn-18.3 on 2026-05-13 (commit <pending>).** The entries below are preserved for historical context. For the active deferment register, see `docs/deferments.md` in the repo.

Deferments filed by this epic in `~/.claude/plans/pilot-firewall.md` § Deferments register:

- **`D-PASS-weather-model-expansion`** — gusts (already typed on `Wind`), visibility ceilings, precipitation, cloud layers, weather volumes. Each is its own field on `WeatherObservation` or a sibling entity. Separate from the migration.
- **`D-PASS-per-runway-weather`** — large airports with per-runway wind sensors require `Runway.weather` instead of (or alongside) `Aerodrome.weather`. v1 keeps aerodrome-scope.
- **`D-PASS-weather-history-replay`** — retained observation history (rolling buffer, replay). Today's shape is point-in-time only.
- **`D-PASS-metar-taf-ingestion`** — read METAR/TAF cycles and translate to `WeatherObservation` writes. Separate pipeline.
- **`D-PASS-weather-validity-window`** — `observedAt: SimTime` on `WeatherObservation` + staleness reasoning ("METAR is 90 minutes old; treat as `WindReport.NotReported`"). Out of scope.
- **`D-PASS-weather-shift-event-leaf`** — `SimEvent.WeatherChanged(aerodromeId, observation)` for the test mutator. Today's shape is direct world mutation via the lens; an event leaf would be structurally cleaner. Filed but not blocking.

## Closures

- **Second consumer of `project_rich_world_domain.md`.** With `Runway.obstruction` (fn-12) and `Aerodrome.weather` (this epic) both on entities, the principle is past one-off and into "established pattern" status. Future world-state additions default to the same shape.
- **fn-14 deferment closed.** `D-PASS-wind-state-migrate-to-aerodrome` removed from the register.
- **`WeatherObservation` lifted out of `:controller`.** Confirms the `:controller` module is for controller-decision types, not for shared world-state types. Cleans the module boundary.
- **First `AviationWorld` lens helper.** `updateAerodrome` becomes the canonical shape for future single-entity world mutations (extension naming convention: `update<EntityType>(id) { transform }`).

## Requirement coverage

| Req | Description | Task(s) |
|-----|-------------|---------|
| R1  | `WeatherObservation` moved from `:controller` to `:core` | fn-16.1 |
| R2  | `Aerodrome.weather: WeatherObservation? = null` field added | fn-16.1 |
| R3  | `AviationWorld.updateAerodrome` lens helper + unit test | fn-16.1 |
| R4  | `SimState.initial` folds weather into world at construction | fn-16.1 |
| R5  | `InitError.MissingWeatherForRunwayAerodrome` predicate migrates to entity | fn-16.1 |
| R6  | `SimState.weatherByAerodrome` field DELETED + 8 direct-constructor sites migrated | fn-16.1 |
| R7  | Three production readers migrated (PilotWiring, ControllerWiring, SimTraceQueries) | fn-16.1 |
| R8  | `G3aPilotReactiveCrosswindTest` mutator migrates to the lens | fn-16.1 |
| R9  | Test-fixture sweep audit — no orphan `SimState.weatherByAerodrome` references | fn-16.1, fn-16.2 |
| R10 | KDoc updates landed (8 sites) | fn-16.2 |
| R11 | Memory entry added | fn-16.2 |
| R12 | Full build green (8 goldens, detekt baseline unchanged) | fn-16.1, fn-16.2 |
| R13 | `.plan` (in-repo sister register) reconciled — D-PASS-wind-state-migrate-to-aerodrome closure + 7 NEW deferments appended with four-field contracts | fn-16.2 |

## Done summary

_(filled per task during implementation)_

## Evidence

_(filled per task during implementation)_

