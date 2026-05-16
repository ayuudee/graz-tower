# fn-24 — Land `:pilot/PilotAviationWorld` projection (close D-PASS-pilot-world-strip-dynamic-state)

## Overview

Close the long-standing structural-enforcement deferment `D-PASS-pilot-world-strip-dynamic-state` filed by fn-16.1 (in `docs/deferments.md` at line 626-631). Post-fn-16, the rich-world-domain principle puts time-varying state on the entity (`Aerodrome.weather`, `Runway.obstruction`), and the same `AviationWorld` instance flows to both controller wiring AND `PilotInput.world`. Today the pilot firewall has typed projection fields (`PilotInput.weatherByAerodrome: Map<AerodromeId, WindReport>` — wind-only, no QNH/visibility), but the underlying `world.aerodromes[id].weather` and `world.aerodromes[id].runways[id].obstruction` ARE reachable from pilot code. The discipline is enforced by convention + KDoc, not by the build graph.

This epic introduces parallel typed projection data classes in `:pilot` that omit the dynamic entity fields, plus a conversion function at the firewall boundary. After this lands, reading `world.aerodromes[id].weather` from pilot code **fails to compile** because `PilotAviationWorld` / `PilotAerodrome` simply don't have those fields. Structural enforcement replaces convention.

**Shape**: parallel `data class PilotAviationWorld` / `PilotAerodrome` / `PilotRunway` in `:pilot/...` that mirror the `:core/world/WorldModel.kt` types minus `weather` (Aerodrome) and `obstruction` (Runway). A single `AviationWorld.toPilotView(): PilotAviationWorld` extension function lives in `:pilot`. `PilotInput.world` changes type from `AviationWorld` to `PilotAviationWorld`. `PilotWiring.buildPilotInput` calls the conversion.

## Boundaries / non-goals

- **Out: changing `:core/world/WorldModel.kt`** — `Aerodrome.weather` and `Runway.obstruction` stay on the entity per fn-12 / fn-16 precedent. The projection lives in `:pilot`, NOT as a `:core`-side `Pilot`-typed view (resolving the deferment's "open question" — the projection lives in `:pilot` to keep the build graph one-way and the pilot-firewall structurally enforced from inside the pilot module).
- **Out: changing controller-side surfaces.** `BeliefState` / `ControllerView` continue to use `AviationWorld` directly. The projection is pilot-only.
- **Out: adding new pilot-side projections beyond `world`** (e.g. typed views of `worldIndex`). Scope is the `AviationWorld` projection only; other `PilotInput` fields stay as-is.
- **Out: removing the `weatherByAerodrome` field from `PilotInput`.** The typed-projection field stays as the pilot's wind input (fn-14.1's R3 contract); the `world` field type-change is independent.
- **Out: detekt rule / source-text scan replacement.** The structural type-projection IS the enforcement; no additional source-scan needed (compile-failure is stronger than scan).

## Strategy Alignment

Active tracks served by this plan:
- **Runtime simulator** — codifies the rich-world-domain principle's pilot-firewall corollary in the build graph: pilot code cannot reach dynamic entity state because the type system doesn't expose it. Pattern-mirror of fn-16's "no shim, no parallel shape" hard-cutover discipline, applied to the firewall surface.
- **Reviewer / agent infrastructure** — "AI-generated code is locally correct and globally blind; this project hardens against that by making reversal invariants and global-state interactions an explicit review concern." The compile-time enforcement removes a class of "subtle pilot-firewall violation" defects from the review surface.

## Decision context

**Option chosen: parallel data classes in `:pilot`, omit dynamic fields**. Three options considered:

- **Option A — null out dynamic fields in projection (same types)**: `PilotInput.world: AviationWorld` keeps full type; `PilotWiring.buildPilotInput` constructs an `AviationWorld` copy with `aerodromes[*].weather = null` and `runways[*].obstruction = null`. **Rejected**: pilot code that reads `.weather` returns `null` at runtime instead of failing at compile time — weaker enforcement, satisfies the deferment letter only, not its spirit.
- **Option B — parallel typed projection (this epic)**: New `data class PilotAviationWorld` / `PilotAerodrome` / `PilotRunway` in `:pilot/world/` that mirror the source minus dynamic fields. `PilotInput.world: PilotAviationWorld`. **Chosen**: compile-time enforcement, matches the deferment's "fails to compile" contract. Cost: ~3 parallel data classes + conversion fn + ~5 pilot-code call-site adjustments (where `world.aerodromes[id]` is read).
- **Option C — `:core`-side `Pilot`-typed view**: Move the projection types into `:core` so both controller and pilot see the same singleton. **Rejected**: violates the build-graph one-wayness — `:core` would need to know about a pilot-specific concept; pilot-firewall enforcement belongs structurally inside `:pilot`. The deferment's open question is resolved this way.

**Cost estimate** (hardened per plan-review round 1 — codex finding "type cascade underestimated"): ~3 new data classes (~80 LOC), 1 extension function (~30 LOC), `PilotInput.world` type change (1 line + KDoc), `PilotWiring.buildPilotInput` conversion call (~3 lines). **Full cascade**: `Pilot.kt` has 7+ `world: AviationWorld` parameter signatures (lines 356/486/553/600/743/1223/1296), `PilotRoutePlanner.kt` has 14+ signatures (lines 167/181/219/263/323/364/406/457/502/583/648/699 + helper threading). Each gets migrated to `world: PilotAviationWorld`. Test fixtures (~10 files in `pilot/src/commonTest/`) that construct `AviationWorld()` for pilot tests get `.toPilotView()` appended. Plus the architectural test asserting `PilotAviationWorld` does NOT carry `weather` / `obstruction` AND property-set parity vs `Aerodrome` / `Runway`. **Realistic scope: ~15-25 files, ~400-600 LOC.** Up from initial estimate but still M-bounded.

## Acceptance

- **R1:** New file `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/world/PilotAviationWorld.kt` defines `data class PilotAviationWorld` mirroring `AviationWorld` (`geometry`, `fixes`, `aerodromes`, `airways`, `vfrRoutes`, `airspace`, `firs`) but with `aerodromes: Map<AerodromeId, PilotAerodrome>` — the only typed difference is the aerodrome map's value type.
- **R2:** Same file defines `data class PilotAerodrome` mirroring `Aerodrome` (all ~15 static fields including `aip`, `runways`, `taxiways`, `circuits`, `sids`, `stars`, `approaches`, `referencePoint`, etc.) but **omits** `weather: WeatherObservation?`. The `runways` field becomes `Map<RunwayId, PilotRunway>`.
- **R3:** Same file defines `data class PilotRunway` mirroring `Runway` (`id`, `path`, `threshold`, `exits`, `declaredDistances`) but **omits** `obstruction: RunwayObstruction?`. Plus any other `Runway` static fields that exist today (verify against `core/world/WorldModel.kt:151+` at task time).
- **R4:** Same file defines `fun AviationWorld.toPilotView(): PilotAviationWorld` extension that walks the source and drops dynamic fields. The conversion is a pure function with **exhaustive named-argument constructor wiring**. **Clarification per plan-review round 2** (codex finding "named-arg wiring claim was overstated for core-field additions"): named-arg wiring prevents sloppy projection construction (every `PilotAerodrome(...)` / `PilotRunway(...)` call site must enumerate every projection field), but the REAL future-field gate is `FirewallPilotAviationWorldTest`'s property-set parity assertion (R8 #2) — that test fails if `:core/Aerodrome` gains a field not added to `PilotAerodrome` (and vice versa). The two enforcements are complementary, not redundant.
- **R5:** `PilotInput.world: AviationWorld` changes to `PilotInput.world: PilotAviationWorld` (`pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotInput.kt:38`). KDoc updated to reference the new type and note the structural enforcement (D-PASS-pilot-world-strip-dynamic-state archived).
- **R6:** `PilotWiring.buildPilotInput` (`sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/PilotWiring.kt:24-29`) calls `state.world.toPilotView()` instead of passing `state.world` directly.
- **R7:** Pilot-side function signatures that take `world: AviationWorld` migrate to `world: PilotAviationWorld`. Full cascade (verified by grep at planning): `Pilot.kt` 7+ signatures (lines 356/486/553/600/743/1223/1296), `PilotRoutePlanner.kt` 14+ signatures (lines 167/181/219/263/323/364/406/457/502/583/648/699 + helpers). Call-site reads of `world.aerodromes[id]` work against `PilotAviationWorld` / `PilotAerodrome`. **Test fixtures** (`PilotAgentTypeSpec.kt`, `FirewallPilotInputTest.kt`, `PilotCrosswindTickATickBTest.kt`, `PilotAtcInitiatedGoAroundSpec.kt`, `PlannedGoAroundSpec.kt`, `PerTypeCircuitSpec.kt`, `TransitRoutePlanningSpec.kt`, and any others surfaced by compile) that construct `AviationWorld(...)` for pilot tests append `.toPilotView()` so the test still feeds a `PilotAviationWorld` into pilot code. Static fields (aip, runways, etc.) flow through unchanged; only the type signatures change. **No pilot-side reads of `.weather` or `.obstruction` exist today** (verified via grep), so this is a type-rewrite, not a semantic refactor.
- **R8** (hardened per plan-review round 1 — codex finding "named-arg constructor wiring doesn't catch new core fields"): New architectural test `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/FirewallPilotAviationWorldTest.kt` asserts via reflection (Kotlin-multiplatform `KClass.members` or property scan) THREE properties:
  1. **Omission**: `PilotAerodrome` has NO property named `weather`; `PilotRunway` has NO property named `obstruction`. Failure names the offending property.
  2. **Property-set parity (the real future-field gate)**: `PilotAerodrome::class.memberProperties.map { it.name }.toSet() == Aerodrome::class.memberProperties.map { it.name }.toSet() - setOf("weather")`. Same for `PilotRunway` minus `"obstruction"`. If a future field is added to `Aerodrome` or `Runway` in `:core` and the implementer forgets to add it to `PilotAerodrome` / `PilotRunway`, this test fails. Same in reverse — if `PilotAerodrome` accidentally gains a field not in `Aerodrome`, this fails. (Named-arg constructor wiring catches additions to `PilotAerodrome` constructor sites; property-set parity catches the bidirectional drift.)
  3. **Value-type assertion**: `PilotAviationWorld::class.memberProperties.find { it.name == "aerodromes" }\!\!.returnType` projects to `Map<AerodromeId, PilotAerodrome>` (not `Map<AerodromeId, Aerodrome>`) — closes the loop on R1's value-type-substitution. Same for `PilotAerodrome.runways → PilotRunway`.
  4. **Top-level property-name parity** (added per plan-review round 2 — codex finding "PilotAviationWorld vs AviationWorld parity missing"): `PilotAviationWorld::class.memberProperties.map { it.name }.toSet() == AviationWorld::class.memberProperties.map { it.name }.toSet()`. R1 says the top-level mirrors `AviationWorld` with only the `aerodromes` value-type substitution — this assertion catches a silent omission if a future top-level `AviationWorld` field is added without being added to `PilotAviationWorld`.
- **R9:** Existing `FirewallPilotInputTest.kt` (the canonical pilot-firewall allowlist test) updated to reflect the type change (`world: AviationWorld` → `world: PilotAviationWorld`). The existing reflection-based property scan should adapt; if it hard-codes the `AviationWorld` type, fix the hard-coded reference.
- **R10:** `docs/deferments.md` D-PASS-pilot-world-strip-dynamic-state archive-flipped per `docs/deferments-CONVENTION.md` §8 three-field locked schema: `Status: closed`, `Closed by: fn-24-land-pilotpilotaviationworld-projection.1`, `Enforcement: PilotAviationWorld + PilotAerodrome + PilotRunway parallel projection in :pilot/world/PilotAviationWorld.kt; PilotInput.world type-changed; FirewallPilotAviationWorldTest asserts no weather/obstruction fields reachable; compile-time enforcement replaces convention-only KDoc.`
- **R11** (hardened per plan-review round 1 — `:migration:allTests` mandatory, not optional): Full verify GREEN. **Two invocations** for clean baseline diff: (a) `./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt --offline --no-daemon` exits 0 (nine sim goldens GREEN; detekt baseline unchanged); (b) `./gradlew :migration:allTests --offline --no-daemon` exits 0 (still green after fn-19's reconciliation). R14-Passed — no NEW regressions vs pre-task baseline.
- **R12** (revised per plan-review round 1 — cost estimate refined): Diff scope: **2 new files** (`PilotAviationWorld.kt` + `FirewallPilotAviationWorldTest.kt`) + **~10-15 modified files** (`PilotInput.kt`, `Pilot.kt` with 7+ signature migrations, `PilotRoutePlanner.kt` with 14+ signature migrations, `PilotWiring.kt`, `FirewallPilotInputTest.kt`, ~7 test-fixture files that construct `AviationWorld(...)` for pilot tests, `docs/deferments.md`). **Total: ≤20 files, ≤600 LOC.**

## Early proof point

Task `fn-24-land-pilotpilotaviationworld-projection.1` validates the core approach by running the existing nine sim goldens against the new typed projection. If a golden test breaks due to a missed call site or an incompatible field on `PilotAviationWorld` / `PilotAerodrome` / `PilotRunway`, the projection's shape is wrong — re-evaluate which fields legitimately need to flow through (today's reads in `PilotRoutePlanner.kt`).

## Quick commands

```bash
# Pre-task baseline (R11 + R14-Passed comparison)
git rev-parse HEAD > $TMPDIR/fn-24-1-base-sha.txt
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
    --offline --no-daemon 2>&1 | tee $TMPDIR/fn-24-1-base.log

# Audit pilot-side reads of dynamic fields (must be 0 today; structurally enforced after)
grep -rnE 'world\.aerodromes\[[^\]]+\]\.weather|world\.aerodromes\[[^\]]+\]\.runways\[[^\]]+\]\.obstruction' pilot/src/commonMain/
# Expected: 0 hits (convention enforcement is holding today; the epic codifies in build graph)

# Post-task verify
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
    --offline --no-daemon 2>&1 | tee $TMPDIR/fn-24-1-post-non-migration.log
# :migration:allTests (mandatory per R11):
GRADLE_USER_HOME="$TMPDIR/gradle-user-home" _JAVA_OPTIONS="-Djava.io.tmpdir=$TMPDIR" \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew :migration:allTests --offline --no-daemon 2>&1 | tee $TMPDIR/fn-24-1-post-migration.log

# Verify D-PASS-pilot-world-strip-dynamic-state is archived (not in ## planned)
grep -nE "^### D-PASS-pilot-world-strip-dynamic-state" docs/deferments.md
# Expected line is in the ## Archive section
```

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | PilotAviationWorld data class | fn-24-land-pilotpilotaviationworld-projection.1 | — |
| R2  | PilotAerodrome (omits weather) | fn-24-land-pilotpilotaviationworld-projection.1 | — |
| R3  | PilotRunway (omits obstruction) | fn-24-land-pilotpilotaviationworld-projection.1 | — |
| R4  | toPilotView() conversion fn | fn-24-land-pilotpilotaviationworld-projection.1 | — |
| R5  | PilotInput.world type changes | fn-24-land-pilotpilotaviationworld-projection.1 | — |
| R6  | PilotWiring.buildPilotInput calls conversion | fn-24-land-pilotpilotaviationworld-projection.1 | — |
| R7  | Pilot-side call sites updated | fn-24-land-pilotpilotaviationworld-projection.1 | — |
| R8  | FirewallPilotAviationWorldTest | fn-24-land-pilotpilotaviationworld-projection.1 | — |
| R9  | FirewallPilotInputTest updated | fn-24-land-pilotpilotaviationworld-projection.1 | — |
| R10 | D-PASS deferment archived | fn-24-land-pilotpilotaviationworld-projection.1 | — |
| R11 | Full verify green | fn-24-land-pilotpilotaviationworld-projection.1 | — |
| R12 | Diff scope ≤20 files / ≤600 LOC (per plan-review round 1 — full cascade) | fn-24-land-pilotpilotaviationworld-projection.1 | — |

## Review considerations

- **FP / type safety**: The whole epic IS a type-safety improvement — compile-time enforcement replaces convention. **Reviewer focus**: confirm the conversion fn is total (no `?:` defaults that mask intent); confirm `PilotAerodrome` constructor wiring is exhaustive (if a future field is added to `Aerodrome`, the conversion fails to compile at construction); confirm no new use of `\!\!` or unsafe casts.
- **Test architecture**: 1 new architectural test (R8) + 1 updated existing test (R9). The architectural test uses reflection — confirm the multiplatform `KClass.members` / `properties` API works the same on `:pilot`'s JVM target as the existing reflection tests use elsewhere in the codebase (check `FirewallPilotInputTest.kt` for the pattern).
- **Impact**: scoped to `:pilot` + sim/PilotWiring + docs/deferments.md. Controller surface unchanged. Reviewer focus: confirm no controller / protocol / core files are touched.
- **Operational ATC correctness / applicability**: not applicable — no runtime behavior changes. Pilot still reads the same data, via the same fields the typed projection exposes. The dynamic fields the projection omits are NOT being read by pilot code today (verified via grep) — the epic codifies in the type system what convention already enforces.

## References

- Deferment to close:
  - `docs/deferments.md:626-631` — D-PASS-pilot-world-strip-dynamic-state entry; archived by R10
- Core types (read-only; do NOT modify):
  - `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:151` — `Runway` (carries `obstruction: RunwayObstruction?`)
  - `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:333` — `Aerodrome` (carries `weather: WeatherObservation?`)
  - `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:428` — `AviationWorld`
- Pilot-side touch points:
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotInput.kt:38` — `val world: AviationWorld` (type changes to `PilotAviationWorld`)
  - `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotRoutePlanner.kt:862` — `val aerodrome = world.aerodromes[destination]` (works against `PilotAviationWorld` after type change)
- Sim wiring:
  - `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/PilotWiring.kt:24-29` — `buildPilotInput` constructs `PilotInput`; calls `state.world.toPilotView()` after R6
- Test precedent:
  - `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/FirewallPilotInputTest.kt` — reflection-based property scan; the new R8 test follows the same pattern
- fn-12 + fn-16 precedents (rich-world-domain principle this epic codifies in the pilot firewall):
  - fn-12 spec — Runway.obstruction migration
  - fn-16 spec — Aerodrome.weather migration; the round-2 codex finding that filed the deferment
