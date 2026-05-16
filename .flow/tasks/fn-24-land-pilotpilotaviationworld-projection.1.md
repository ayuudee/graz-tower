---
satisfies: [R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12]
---

## Description

Land the structural pilot-firewall enforcement: define `PilotAviationWorld` / `PilotAerodrome` / `PilotRunway` parallel data classes in `:pilot/world/`, plus a `toPilotView()` extension fn on `AviationWorld`. Change `PilotInput.world` type. Call the conversion in `PilotWiring.buildPilotInput`. Adapt pilot-side call sites that read `world.aerodromes[id]` (currently `PilotRoutePlanner.kt:862`; verify no others at task time). Add reflection-based architectural test asserting no `weather`/`obstruction` field is reachable through the projection. Archive the deferment.

**Size:** M-L (3 new data classes + 1 conversion fn + 1 new test + ~10-15 modified files; ≤600 LOC across ≤20 files — type cascade across Pilot.kt + PilotRoutePlanner.kt + test fixtures, hardened per plan-review round 1).

**Files**:
- **CREATE**:
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/world/PilotAviationWorld.kt` (R1-R4; the three data classes + `toPilotView()` extension)
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/FirewallPilotAviationWorldTest.kt` (R8 — reflection-based architectural test)
- **MODIFY**:
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotInput.kt` (R5 — `world` type change + KDoc update)
  - `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/PilotWiring.kt` (R6 — `.toPilotView()` call in `buildPilotInput`)
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotRoutePlanner.kt` (R7 — 14+ signatures migrate `world: AviationWorld` → `world: PilotAviationWorld`: lines 167/181/219/263/323/364/406/457/502/583/648/699 + any helpers surfaced by compile)
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt` (R7 — 7+ signatures migrate: lines 356/486/553/600/743/1223/1296)
  - **Pilot test fixtures** (R7 cascade — append `.toPilotView()` to `AviationWorld(...)` constructions): `pilot/src/commonTest/.../PilotAgentTypeSpec.kt`, `PilotCrosswindTickATickBTest.kt`, `PilotAtcInitiatedGoAroundSpec.kt`, `PlannedGoAroundSpec.kt`, `PerTypeCircuitSpec.kt`, `TransitRoutePlanningSpec.kt`, plus any others surfaced by compile (typically 8-10 files total)
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/FirewallPilotInputTest.kt` (R9 — adapt to new world type)
  - `docs/deferments.md` (R10 — D-PASS-pilot-world-strip-dynamic-state archive flip)
- **READ ONLY**:
  - `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt` — `Aerodrome`, `Runway`, `AviationWorld` shapes (verify field lists at task time; type may have evolved since plan)
  - `docs/deferments-CONVENTION.md` § 8 — three-field archive schema

## Approach (numbered Steps)

### Step 0 — Baseline capture (BEFORE any edits)

```bash
git rev-parse HEAD > $TMPDIR/fn-24-1-base-sha.txt
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
    --offline --no-daemon 2>&1 | tee $TMPDIR/fn-24-1-base.log
# Expected: BUILD SUCCESSFUL. Nine sim goldens GREEN. Detekt baseline unchanged.

# Audit pilot-side reads of dynamic fields (sanity check: should be 0 today)
grep -rnE 'world\.aerodromes\[[^\]]+\]\.weather|world\.aerodromes\[[^\]]+\]\.runways\[[^\]]+\]\.obstruction' pilot/src/commonMain/
# Expected: 0 hits — convention enforcement is holding; the epic codifies in build graph.
```

### Step 1 — Re-read `:core` type shapes (read-only)

Open `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt`. Confirm at task time:
- `Aerodrome` field list (line ~333; ~15 fields including `aip`, `runways`, `taxiways`, `circuits`, `sids`, `stars`, `approaches`, `referencePoint`, etc., PLUS `weather: WeatherObservation?` which the projection omits).
- `Runway` field list (line ~151; `id`, `path`, `threshold`, `exits`, `declaredDistances`, PLUS `obstruction: RunwayObstruction?` which the projection omits).
- `AviationWorld` field list (line ~428; `geometry`, `fixes`, `aerodromes`, `airways`, `vfrRoutes`, `airspace`, `firs` — only `aerodromes` value-type changes; everything else flows through).

If a field has been added since this plan was written, the projection's constructor wiring will surface it at compile time (R4 contract) — extend the projection accordingly OR document why the new field also belongs in `PilotAerodrome` / `PilotRunway`.

### Step 2 — Write `PilotAviationWorld.kt` (R1, R2, R3, R4)

Create `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/world/PilotAviationWorld.kt`.

Structure:

```kotlin
// SHAPE ONLY — not implementation:
package xyz.easiersaid.twr.pilot.world

import xyz.easiersaid.twr.core.world.AviationWorld  // and the per-type imports
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.Runway

data class PilotAviationWorld(
    val geometry: PhysicalGeometry = PhysicalGeometry(),
    val fixes: Map<FixId, Fix> = emptyMap(),
    val aerodromes: Map<AerodromeId, PilotAerodrome> = emptyMap(),
    val airways: Map<AirwayId, Airway> = emptyMap(),
    val vfrRoutes: Map<VfrRouteId, VfrRoute> = emptyMap(),
    val airspace: Map<AirspaceVolumeId, AirspaceVolume> = emptyMap(),
    val firs: Map<FirId, FlightInformationRegion> = emptyMap(),
)

data class PilotAerodrome(
    // every field from Aerodrome EXCEPT `weather`. Verify list at Step 1.
    val icao: AerodromeId,
    val elevation: Feet,
    // ... all ~15 static fields ...
    val runways: Map<RunwayId, PilotRunway> = emptyMap(),  // value-type changes
    // ... static fields continue ...
    // NO `val weather: WeatherObservation?` — that's the structural omission.
)

data class PilotRunway(
    val id: RunwayId,
    val path: Path,
    val threshold: PointId,
    val exits: List<RunwayExit> = emptyList(),
    val declaredDistances: DeclaredDistances? = null,
    // NO `val obstruction: RunwayObstruction?` — structural omission.
)

fun AviationWorld.toPilotView(): PilotAviationWorld = PilotAviationWorld(
    geometry = geometry,
    fixes = fixes,
    aerodromes = aerodromes.mapValues { (_, a) -> a.toPilotView() },
    airways = airways,
    vfrRoutes = vfrRoutes,
    airspace = airspace,
    firs = firs,
)

private fun Aerodrome.toPilotView(): PilotAerodrome = PilotAerodrome(
    icao = icao,
    elevation = elevation,
    // ... wire every static field ...
    runways = runways.mapValues { (_, r) -> r.toPilotView() },
    // ... continue ...
)

private fun Runway.toPilotView(): PilotRunway = PilotRunway(
    id = id,
    path = path,
    threshold = threshold,
    exits = exits,
    declaredDistances = declaredDistances,
)
```

**Critical**: the `toPilotView()` functions use **named-argument constructor wiring**, not `copy()` or reflection. If a future field is added to `Aerodrome` or `Runway` in `:core`, the conversion fails to compile at the constructor call site — that's the R4 exhaustive-wiring gate. Don't use spread / reflection / `copy(weather = null)` — those mask future field additions.

KDoc at file top: cite `D-PASS-pilot-world-strip-dynamic-state` archive entry; reference fn-12 (Runway.obstruction) + fn-16 (Aerodrome.weather) as the dynamic-field precedents; reference fn-16.1's codex-round-2 finding that filed the deferment.

### Step 3 — Change `PilotInput.world` type (R5)

In `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotInput.kt`:
- Line ~38: `val world: AviationWorld,` → `val world: PilotAviationWorld,`
- Update KDoc on the field (or class-level if appropriate) to note the structural enforcement: "fn-24 closed `D-PASS-pilot-world-strip-dynamic-state` — `PilotAviationWorld` omits entity-level dynamic fields (`Aerodrome.weather`, `Runway.obstruction`) so they're unreachable from pilot code at compile time."
- Import the new type.

### Step 4 — Update `PilotWiring.buildPilotInput` (R6)

In `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/PilotWiring.kt:24-29`:
- Change `world = state.world,` to `world = state.world.toPilotView(),`
- Import `xyz.easiersaid.twr.pilot.world.toPilotView`

### Step 5 — Adapt pilot-side call sites (R7 — full type cascade)

Build the codebase. The compiler will surface every pilot-side reference to `AviationWorld` / `Aerodrome` / `Runway` that needs to be `PilotAviationWorld` / `PilotAerodrome` / `PilotRunway`.

**Known cascade** (from planning-time grep — verify and extend at task time):
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt`: 7+ function signatures take `world: AviationWorld` (lines ~356, 486, 553, 600, 743, 1223, 1296). All migrate to `world: PilotAviationWorld`.
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotRoutePlanner.kt`: 14+ function signatures (lines ~167, 181, 219, 263, 323, 364, 406, 457, 502, 583, 648, 699 + helpers). All migrate.
- **Pilot test fixtures** that construct `AviationWorld(...)`: `PilotAgentTypeSpec.kt` (3 sites), `FirewallPilotInputTest.kt`, `PilotCrosswindTickATickBTest.kt`, `PilotAtcInitiatedGoAroundSpec.kt`, `PlannedGoAroundSpec.kt`, `PerTypeCircuitSpec.kt`, `TransitRoutePlanningSpec.kt`, plus any others. **Strategy**: append `.toPilotView()` to each `AviationWorld(...)` construction so the test still feeds a `PilotAviationWorld` into pilot code (the tests are exercising pilot logic, not core logic).

If a pilot read genuinely needs a dynamic field (it shouldn't today, per Step 0 grep), that's a real firewall violation — surface and decide whether to (a) thread the value through a typed projection field on `PilotInput` (like `weatherByAerodrome`), or (b) audit whether the read is justified at all. Per plan-review round 1: if the test fixture migration uncovers a hidden semantic dependency (e.g. a test reads `world.aerodromes[id].weather` directly that the convention-grep missed), pause and surface; that's a real firewall violation, not a type-rewrite.

### Step 6 — Write `FirewallPilotAviationWorldTest.kt` (R8 — three-property assertion, hardened per plan-review round 1)

Create `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/FirewallPilotAviationWorldTest.kt`.

Use reflection (Kotlin KClass.memberProperties, matching the pattern in `FirewallPilotInputTest.kt`) to assert THREE distinct properties (all REQUIRED — none optional):

1. **Omission**: `PilotAerodrome` has NO property named `weather`; `PilotRunway` has NO property named `obstruction`. Failure names the offending property + the dynamic-field-omission contract.

2. **Property-set parity** (the REAL future-field gate, per plan-review round 1 — named-arg constructor wiring only catches additions to `PilotAerodrome`'s OWN constructor, not additions to core `Aerodrome`):
   ```kotlin
   // SHAPE ONLY — not implementation:
   val pilotAerodromeProps = PilotAerodrome::class.memberProperties.map { it.name }.toSet()
   val coreAerodromeProps = Aerodrome::class.memberProperties.map { it.name }.toSet()
   assertEquals(
     coreAerodromeProps - setOf("weather"),
     pilotAerodromeProps,
     "PilotAerodrome property set must equal Aerodrome minus {weather}; " +
     "missing-from-pilot: ${coreAerodromeProps - setOf("weather") - pilotAerodromeProps}; " +
     "extra-in-pilot: ${pilotAerodromeProps - (coreAerodromeProps - setOf("weather"))}"
   )
   ```
   Same shape for `PilotRunway` vs `Runway` minus `setOf("obstruction")`.

3. **Value-type substitution**: assert that `PilotAviationWorld.aerodromes`'s value type is `PilotAerodrome` (not `Aerodrome`), and `PilotAerodrome.runways`'s value type is `PilotRunway` (not `Runway`). Use `returnType.arguments` from `KClass.memberProperties.find { it.name == "aerodromes" }`. Closes the loop on R1 — confirms the projection actually swaps the entity types, not just hides fields on a shared `Aerodrome` instance.

Failure messages name the offending property/type + reference the structural-enforcement contract.

### Step 7 — Update existing `FirewallPilotInputTest.kt` (R9)

Open `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/FirewallPilotInputTest.kt`. The existing test enumerates `PilotInput`'s allowlist (canonical-constructor entry + reflection-based property scan). If it hard-codes the `AviationWorld` type, update to `PilotAviationWorld`. Most likely it just reads the field's name + presence; the type change may pass through unchanged. Verify post-task.

### Step 8 — Archive flip D-PASS in `docs/deferments.md` (R10)

Move the `### D-PASS-pilot-world-strip-dynamic-state` block from `## D-PASS` (line ~626-631) into `## Archive`. Rewrite to three-field schema:

```markdown
### D-PASS-pilot-world-strip-dynamic-state — typed pilot-chart projection that hides entity-level dynamic state
**Status:** closed
**Closed by:** fn-24-land-pilotpilotaviationworld-projection.1
**Enforcement:** `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/world/PilotAviationWorld.kt` defines `PilotAviationWorld` / `PilotAerodrome` / `PilotRunway` parallel data classes that omit `Aerodrome.weather` and `Runway.obstruction` (the dynamic entity fields). `AviationWorld.toPilotView()` extension fn projects at the firewall boundary; `PilotInput.world: PilotAviationWorld`. Reading `world.aerodromes[id].weather` or `world.aerodromes[id].runways[id].obstruction` from pilot code now fails to compile. `FirewallPilotAviationWorldTest` reflection-asserts the omission. fn-12 (Runway.obstruction) + fn-16 (Aerodrome.weather) rich-world-domain precedents codified in the type system; convention-via-KDoc replaced by structural enforcement.
```

Drop `Pinned at:` and `Blocked on:` fields (active-entry-only). Verify post-edit: `grep -nE "^### D-PASS-pilot-world-strip-dynamic-state" docs/deferments.md` should show the entry only in the `## Archive` section.

### Step 9 — Verify (R11 — two-invocation full verify, `:migration:allTests` mandatory)

```bash
# (a) Non-migration suite
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
    --offline --no-daemon 2>&1 | tee $TMPDIR/fn-24-1-post-non-migration.log

# (b) :migration:allTests (mandatory per plan-review round 1)
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :migration:allTests --offline --no-daemon 2>&1 | tee $TMPDIR/fn-24-1-post-migration.log
```

Both expect BUILD SUCCESSFUL. Nine sim goldens GREEN. Detekt baseline unchanged. `:migration:allTests` stays green (fn-19 fixed D-WORLD.2). Diff post-logs against `$TMPDIR/fn-24-1-base.log`: no NEW failures introduced.

### Step 10 — `flowctl done`

Compute concrete values (per fn-22 R6 flowctl-done state-sync discipline), then invoke:

```bash
base_sha="$(cat $TMPDIR/fn-24-1-base-sha.txt)"
implementation_sha="$(git rev-parse HEAD)"

cat > $TMPDIR/fn-24-1-summary.md <<EOF2
fn-24.1 shipped: PilotAviationWorld + PilotAerodrome + PilotRunway parallel projection landed in :pilot/world/; AviationWorld.toPilotView() extension fn wires the boundary; PilotInput.world type changed; PilotWiring.buildPilotInput projects via toPilotView(); pilot-side call sites adapted; FirewallPilotAviationWorldTest asserts no weather/obstruction reachable; FirewallPilotInputTest updated. D-PASS-pilot-world-strip-dynamic-state archived per CONVENTION §8. R14-Passed: nine sim goldens GREEN, detekt unchanged. Implementation commit ${implementation_sha}.
EOF2

cat > $TMPDIR/fn-24-1-evidence.json <<EOF2
{
  "task": "fn-24-land-pilotpilotaviationworld-projection.1",
  "base_sha": "${base_sha}",
  "implementation_sha": "${implementation_sha}",
  "files_created": ["pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/world/PilotAviationWorld.kt", "pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/FirewallPilotAviationWorldTest.kt"],
  "files_modified": "<generated from actual diff at task close — `git diff --name-only --diff-filter=M | grep -v research/tools/`; expected includes at minimum PilotInput.kt + Pilot.kt + PilotRoutePlanner.kt + PilotWiring.kt + FirewallPilotInputTest.kt + 6-8 pilot test fixtures + docs/deferments.md (per plan-review round 2 — hard-coded list goes stale during type-cascade migration)>",
  "deferment_archive_flip": "D-PASS-pilot-world-strip-dynamic-state moved from ## D-PASS active body to ## Archive",
  "verify_outcome": "BUILD SUCCESSFUL; nine sim goldens GREEN; detekt baseline unchanged"
}
EOF2

.flow/bin/flowctl done fn-24-land-pilotpilotaviationworld-projection.1 \
  --summary-file $TMPDIR/fn-24-1-summary.md \
  --evidence-json $TMPDIR/fn-24-1-evidence.json --json
```

Post-done state-sync sweep (per fn-22 R6 lesson): confirm `## Done summary` carries the interpolated text; confirm `## Evidence` has concrete SHAs (no `<placeholder>` strings); confirm `flowctl show fn-24-... --json | jq .status` == "done" before invoking impl-review.

## Acceptance

- [ ] **R1** — `PilotAviationWorld.kt` defines `PilotAviationWorld` data class mirroring `AviationWorld` shape with `aerodromes: Map<AerodromeId, PilotAerodrome>`.
- [ ] **R2** — `PilotAerodrome` mirrors `Aerodrome` minus `weather`; `runways` value-type is `PilotRunway`.
- [ ] **R3** — `PilotRunway` mirrors `Runway` minus `obstruction`.
- [ ] **R4** — `AviationWorld.toPilotView()` extension fn uses exhaustive named-argument constructor wiring (no `copy()`, no reflection, no spread).
- [ ] **R5** — `PilotInput.world: PilotAviationWorld` (type change); KDoc references the deferment closure.
- [ ] **R6** — `PilotWiring.buildPilotInput` calls `.toPilotView()`.
- [ ] **R7** — Pilot-side call sites adapt to the new types; codebase compiles.
- [ ] **R8** — `FirewallPilotAviationWorldTest` reflection-asserts THREE required properties: (1) no `weather`/`obstruction` field on projection types; (2) property-set parity vs core types minus the omitted field (the real future-field gate); (3) value-type substitution `PilotAviationWorld.aerodromes: Map<_, PilotAerodrome>` and `PilotAerodrome.runways: Map<_, PilotRunway>`.
- [ ] **R9** — Existing `FirewallPilotInputTest` adapted; still passes.
- [ ] **R10** — `docs/deferments.md` D-PASS-pilot-world-strip-dynamic-state archive-flipped per three-field locked schema.
- [ ] **R11** — Two-invocation full verify both GREEN: (a) non-migration suite (nine sim goldens, detekt unchanged), (b) `:migration:allTests` (stays green post-fn-19). No new regressions vs base log.
- [ ] **R12** — Diff scope ≤20 files / ≤600 LOC (revised per plan-review round 1 — type cascade reality).

## Key context

- The deferment's "Open question: whether the projection lives in `:pilot` or as a `:core`-side `Pilot`-typed view to keep the type singleton" is resolved as `:pilot`-side per the build-graph one-wayness principle.
- **Exhaustive named-argument constructor wiring** is the load-bearing R4 enforcement — `copy()` would mask future field additions. Use named-arg constructor calls explicitly so the compiler surfaces any future Aerodrome / Runway field additions.
- Pilot code today does NOT read `.weather` or `.obstruction` directly (verified via grep at planning time). This epic codifies in the type system what convention already enforces; no semantic refactor needed.
- Pre-existing dirty state (research/tools/requirements-spike/, fn-20 untracked files, research/pdf+txt) MUST NOT be staged.
- Codex sandbox workaround for Gradle: clone `$HOME/.gradle/{caches,native,wrapper}` to `$TMPDIR/gradle-user-home`, run with `GRADLE_USER_HOME=$TMPDIR/gradle-user-home _JAVA_OPTIONS=-Djava.io.tmpdir=$TMPDIR ./gradlew --offline --no-daemon ...`, JAVA_HOME=`/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8`.

## Done summary

_(filled by `flowctl done` at task close — see Step 10)_

## Evidence

_(filled by `flowctl done` at task close — see Step 10)_
