---
satisfies: [R3, R4, R6, R7]
---

## Description

Switch `OutsideAerodromeRadius.evaluate` from snap-point-coords to kinematic
coords. Add a focused unit spec asserting the geometric property (rule
fires at the physical 12 NM ring, not at the snap). Add R6's firewall pin
inside `FirewallSensorReadingTest` (not a new file). Fold in the typed-NM
helper on `Meters.Companion` to replace the `Meters(22_224.0)` magic.

**Size:** M
**Files:**
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt`
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt`
  (the `Meters` data class is here, line 28; needs a new companion)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerDeparture.kt`
  (two call sites — DEP-RADAR-SERVICE-TERMINATED ~line 322 + DEP-CROSS-AERODROME-RELEASE ~line 370)
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/bdi/OutsideAerodromeRadiusSpec.kt`
  (NEW — focused unit spec for the geometric property)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/FirewallSensorReadingTest.kt`
  (NEW `@Test` added inside the existing class)

## Approach

1. **`OutsideAerodromeRadius.evaluate`** (Guard.kt:431-459):
   - Replace `val acPos = ctx.worldIndex.positions[ac.position] ?: return false`
     with a direct read of `ac.coords` (the new field from fn-6.1).
   - Keep the ARP proxy lookup (lexicographically-first runway threshold
     coords) unchanged.
   - The squared-distance computation stays the same.
   - The `?: return false` lookup-miss path goes away — `coords` is non-
     nullable. fn-6.1 mitigates the placeholder hazard at the fixture
     level via `fromTestPoint`; production callers always carry a real
     kinematic position.
   - Update KDoc: replace the existing "Pass 7 post-impl" / approximation
     commentary with: "Reads the aircraft's kinematic position
     (`ac.coords`, set by `ControllerWiring` from sim-side
     `AircraftState.position`); the radius gate fires when the aircraft
     physically crosses the configured ring. The earlier snap-point read
     (`worldIndex.positions[ac.position]`) was off by half-snap-distance
     in the worst case, leaving cross-aerodrome release events bunched
     against the destination's first published REP. Real CTR boundaries
     are typed polygons (FM/Lean campaign territory, fn-4 lineage); the
     circular-radius approximation is intentional pending that work."

2. **`Meters.Companion`** (`core/.../world/WorldModel.kt:28`): the existing
   `Meters` data class has no companion. Add one:
   ```kotlin
   data class Meters(val value: Double) {
       init { require(value >= 0.0) { "Meters must be >= 0" } }
       companion object {
           fun fromNauticalMiles(nm: Int): Meters = Meters(nm * 1852.0)
       }
   }
   ```
   1 NM = 1852 m exactly (international NM). Verify the `Knots` /
   `AltitudeFeet` companion-helper patterns elsewhere in the codebase to
   match style (Knots may have `Knots.unsafe(int)` or similar — match
   that idiom).

3. **`TowerDeparture.kt` call sites**: replace `Meters(22_224.0)` with
   `Meters.fromNauticalMiles(12)` at both sites (DEP-RADAR-SERVICE-
   TERMINATED + DEP-CROSS-AERODROME-RELEASE). The trailing-comment
   "// 12 NM — D-AUDIT.7" can stay as a doctrine pointer; the new helper
   makes the doctrine number self-documenting at the call site.

4. **New unit spec — `OutsideAerodromeRadiusSpec`** at
   `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/bdi/
   OutsideAerodromeRadiusSpec.kt`. This is R3's empirical pin. Test rows:
   - **`evaluate returns false 100m inside the configured ring`**: build
     a minimal `OperatorContext` with a single aerodrome (use the
     `AviationWorld`-builder helper from existing specs — likely
     `controller/src/commonTest/.../testing/...` exposes one), build an
     `AircraftObservation` via `fromTestPoint(...)` but override `coords`
     to a `Position` 100 m inside the 12 NM ring (i.e. ARP + radial offset
     of 12 NM - 100 m). Assert `OutsideAerodromeRadius(Meters
     .fromNauticalMiles(12)).evaluate(ac, commitment, ctx)` returns
     `false`.
   - **`evaluate returns true 100m outside the configured ring`**: same
     setup, `coords` 100 m outside. Assert `true`.
   - **`evaluate returns false when ARP cannot be resolved`**: world has
     no aerodrome at the controller's `aerodromeId` (defensive — should
     return `false`). This pins the existing `?: return false` defensive
     fall-through that the kinematic version preserves.
   - The spec is rule-agnostic: it tests `OutsideAerodromeRadius` directly,
     not `DEP-CROSS-AERODROME-RELEASE` or `DEP-RADAR-SERVICE-TERMINATED`.
     Both call sites pick up identical kinematic semantics; one spec
     suffices.

5. **R6 firewall pin** — add a new `@Test` inside the existing
   `FirewallSensorReadingTest` class (sim/src/jvmTest/.../FirewallSensorReadingTest.kt):
   ```
   @Test
   fun `coords assigns from kinematic state-position only`() {
       // Read SensorReading.kt source, strip comments (re-using the
       // existing pattern), find the `coords = <ident>` line inside
       // toSensorReading(), assert ident == "position".
   }
   ```
   Implementation sketch (write the actual code in the task):
   - Read `SensorReading.kt` + strip comments (mirror lines 33-44 of
     existing test).
   - Use `Regex("""coords\s*=\s*(\w+)""").find(codeOnly)?.groupValues?.get(1)`
     to capture the RHS identifier.
   - Assert the captured identifier is exactly `position` (positive). If
     the regex fails to match, fail with "expected `coords = position`
     in toSensorReading()".
   - Add a sibling assertion inside the same `@Test` (or a new one): scan
     the `toSensorReading()` function body specifically for `val\s+position`
     declarations. None should exist (no local-var shadow that would
     bypass the bare-identifier firewall). Pattern:
     `Regex("""val\s+position\b""").containsMatchIn(toSensorReadingBody)
     == false`.

6. **R4 structural assertion** (no new code; evidence captured in task
   markdown):
   - Run `grep -rn 'ac\.coords' controller/src/commonMain` and capture
     the result. Expected: matches only inside `Guard.kt` at the
     `OutsideAerodromeRadius` call site. Document this in the task's
     `## Evidence` section as "structural pin for R4: positionPoint
     semantics unchanged elsewhere".

## Investigation targets

**Required** (read before coding):
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt:411-459`
  — current `OutsideAerodromeRadius` shape + KDoc.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerDeparture.kt`
  — find both rule call sites (around line 322 for DEP-RADAR-SERVICE-
  TERMINATED, ~370 for DEP-CROSS-AERODROME-RELEASE). Both pass
  `Meters(22_224.0)`.
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:28-32`
  — current `Meters` data class. No companion. Add one.
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:34-38`
  (or wherever `Knots` / `AltitudeFeet` lives) — companion-helper style for
  `fromX(Int)` factory functions.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/FirewallSensorReadingTest.kt:33-44`
  — comment-strip pipeline to reuse for the new `@Test`.
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SensorReading.kt`
  (post fn-6.1 changes) — the source the new firewall test will scan.
- `controller/src/commonTest/kotlin/.../testing/` — find an existing
  `AviationWorld` builder helper to construct a minimal world for the
  new `OutsideAerodromeRadiusSpec`.

**Optional** (reference as needed):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt`
  — sanity-check the rule firing in the cross-aerodrome flow after the
  change. The fn-6.3 task tightens the gap pin; in this task verify the
  test stays green (the relaxed `> 0` bound still passes — the rule fires
  earlier now, which makes the gap larger, not smaller).
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/bdi/`
  — sibling Guard specs (e.g. for `Airborne`, `OnRunway`) to copy the
  `@Test` setup pattern.

## Key context

- `OutsideAerodromeRadius`'s ARP proxy: lexicographically-first runway
  threshold coords (Guard.kt:449-451). LOWG has 16C/16L/16R/34C/34L/34R —
  first lex is "16C". The proxy is stable; no impact from this change.
- Per ICAO Annex 11 §2.11.5.2, CTR ≥ 5 NM in approach directions. The
  current 22 224 m / 12 NM is ~2.4× the floor, ~1.7× a typical small towered
  CTR (LOWG real CTR is ~7 NM laterally per AIP). The gate is generous;
  retuning to ~7 NM is the deferred `D-AUDIT-lowg-ctr-radius` epic, not this
  task. fn-6 only swaps the geometric source, not the threshold.
- 1 NM = 1852 m exactly (international nautical mile). 12 NM = 22 224 m,
  matches the existing magic value. The new helper is exact, not lossy.
- The new firewall test's regex must NOT trip on KDoc / `//` comment text
  that mentions `position` etc. Strip comments before scanning (mirror
  the existing pattern in `FirewallSensorReadingTest`).
- The grep gate for R4 (`grep -rn 'ac\.coords' controller/src/commonMain`)
  is evidence, not code. Capture in task `## Evidence` section.

## Acceptance

- [ ] `OutsideAerodromeRadius.evaluate` reads `ac.coords` directly; no
      `worldIndex.positions[ac.position]` lookup. The defensive ARP-not-
      found `return false` path is preserved.
- [ ] KDoc on `OutsideAerodromeRadius` updated to reflect kinematic-vs-snap
      (per §Approach 1 suggested text or equivalent).
- [ ] `Meters` data class has a `companion object` with `fun fromNauticalMiles
      (nm: Int): Meters`. Style matches existing companion-helpers in the
      codebase.
- [ ] Both `Meters(22_224.0)` call sites in `TowerDeparture.kt` (DEP-RADAR-
      SERVICE-TERMINATED + DEP-CROSS-AERODROME-RELEASE) use
      `Meters.fromNauticalMiles(12)` instead.
- [ ] New file `controller/src/commonTest/.../bdi/OutsideAerodromeRadiusSpec.kt`
      with at least three `@Test` rows (inside-ring → false, outside-ring
      → true, ARP-not-found → false).
- [ ] All three `OutsideAerodromeRadiusSpec` rows pass.
- [ ] `FirewallSensorReadingTest` gains a new `@Test` `coords assigns from
      kinematic state-position only` that asserts (positive) the
      `coords = <ident>` line in `toSensorReading()` captures `position`,
      and (negative) the function body has no `val\s+position\b` shadow.
- [ ] New firewall `@Test` passes; existing `FirewallSensorReadingTest`
      tests stay green.
- [ ] `FirewallObservationTest` stays green.
- [ ] `G2CrossAerodromeVfrTest` stays green with the existing relaxed
      bound (the gap is now larger because the rule fires earlier; the
      `> 0` check holds — fn-6.3 tightens to `>= 30_000L`).
- [ ] `LowgGoldenTest` stays green.
- [ ] R4 structural evidence: `grep -rn 'ac\.coords' controller/src/commonMain`
      output captured in task evidence; matches only inside
      `Guard.kt:OutsideAerodromeRadius`.
- [ ] Full test suite stays green; `./gradlew detekt` baseline unchanged.

## Done summary

## Evidence
