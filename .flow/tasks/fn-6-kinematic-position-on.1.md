---
satisfies: [R1, R2, R4, R7]
---

## Description

Land the kinematic-coords field through both firewall projections — `SensorReading`
(sim-side, the single firewall-allowed `AircraftState → controller-side`
projection) and `AircraftObservation` (controller-side, with its canonical-
constructor allowlist). Thread the new field through the factory, add an
`AircraftObservation.fromTestPoint(...)` helper that derives `coords` from
the world index (so test fixtures cannot diverge), and update every
construction site.

This is the **early proof point** for fn-6: if either firewall test rejects
the new field, or detekt's `LongParameterList` cannot be cleanly handled,
the whole approach needs re-evaluation before fn-6.2/.3.

**Size:** M (borderline; promote to L if `fromTestPoint` ends up touching
more sites than estimated)
**Files:**
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SensorReading.kt`
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt`
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/AircraftObservationFactory.kt`
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/ControllerWiring.kt`
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/FirewallObservationTest.kt`
- `detekt.yml` (only if threshold-raise path chosen — see Approach §7)
- ~13 controller / sim test fixture / call sites (enumerated in §Approach)

## Approach

1. **`SensorReading`** (sim/.../SensorReading.kt:29-45): add `val coords:
   Position` field to the data class. In `toSensorReading()`, populate
   `coords = position` (the `position` reference is the bare-identifier
   read of `AircraftState.position`, the kinematic field — distinct from
   the existing `positionPoint` snap read).
   - Verified firewall-clean: `FirewallSensorReadingTest`'s forbidden-name
     regex (`pilotMission`, `targetSpeedMps`, `targetAltitudeM`, `phase`,
     `route`, `EntityRef`, `entitiesByPoint`) does not include `position`.
2. **`AircraftObservation`** (controller/.../ControllerTypes.kt:161): add
   `val coords: xyz.easiersaid.twr.core.world.Position` to the data class
   alongside the existing `position: PointId`. Update the KDoc with the
   following disambiguation paragraph (suggested text):
   > `coords` carries the kinematic position from primary surveillance
   > (per ICAO Annex 11 §6 / Doc 4444 §8 — surveillance returns are
   > positional); `position` carries the same return projected onto the
   > published-fix graph for chart-anchored consumers (route-progress,
   > entity membership). Two fields because airspace-boundary semantics
   > need geometry and graph-progress semantics need fix identity. A future
   > tightening (`D-PASS-fn6-snap-derived`) derives one from the other.
3. **`AircraftObservationFactory.from(...)`**: add `coords: Position` to the
   parameter list (going from 9 → 10 params). Pass it through to the
   data-class constructor. The `worldIndex` parameter stays — entity
   derivation continues from `position: PointId`.
4. **`AircraftObservation.fromTestPoint(...)`** (new test helper): co-locate
   on `AircraftObservation.Companion` in
   `controller/src/commonTest/kotlin/.../AircraftObservationTestFixtures.kt`
   (new file). Signature sketch: `fun AircraftObservation.Companion.
   fromTestPoint(point: PointId, worldIndex: WorldIndex, callsign: Callsign
   = ..., onGround: Boolean = false, ...): AircraftObservation`. Internally
   it derives `coords = worldIndex.positions[point] ?: error("...")` so
   fixture coords cannot drift from the snap field. Fixtures that
   currently build with `Position(0,0)` placeholders should switch to this
   helper unless they specifically need a divergent coords (in which case
   they document why inline). The helper is test-scope only — do NOT add
   it to commonMain.
5. **`ControllerWiring.toObservation`** (sim/.../ControllerWiring.kt:166):
   the single production call site for `from()`. Pass `reading.coords` (the
   new `SensorReading` field).
6. **`FirewallObservationTest`** canonical-constructor allowlist
   (controller/commonTest/.../FirewallObservationTest.kt:34): add `coords =
   xyz.easiersaid.twr.core.world.Position(xMeters = -999_999.0, yMeters =
   -999_999.0)` to the named-arg block. Use a recognisable sentinel — NOT
   `Position(0.0, 0.0)` — and add a one-line comment "// firewall sentinel:
   not a fixture default; tests should use AircraftObservation.fromTestPoint
   for derived coords". Compile-time gate: omitting the field changes the
   constructor signature → test fails to compile.
7. **Detekt `LongParameterList`** — `from(...)` going from 9 → 10 params
   trips the rule's threshold (currently 8). Pick ONE explicitly:
   - **(a) Suppress at the function:** add `@Suppress("LongParameterList")`
     to `AircraftObservationFactory.from(...)` with comment:
     `// Param list is intentionally wide; FirewallObservationTest enforces
     the canonical constructor as the sole AircraftObservation factory
     (firewall doctrine).`
   - **(b) Raise the threshold:** edit `detekt.yml` to set
     `LongParameterList.functionThreshold: 12` (room for one more without
     re-touching the rule).
   - Recommendation: **(a)** — narrower, doctrine-anchored. Document the
     choice in task evidence either way.
8. **Test fixture sites** that directly construct `AircraftObservation` (5
   total — pre-existing + the new helper):
   - `controller/src/commonTest/.../FirewallObservationTest.kt:34` (allowlist
     — covered above with sentinel)
   - `controller/src/commonTest/.../TaxiToHoldingActionSpec.kt:41` — switch
     to `fromTestPoint`
   - `controller/src/commonTest/.../GuardSpec.kt:34` — switch to
     `fromTestPoint`
   - `controller/src/commonTest/.../AtisLetterMismatchAdvisorySpec.kt:71` —
     switch to `fromTestPoint`
   - `controller/src/commonTest/.../RunwayLengthGatingSpec.kt:62` — switch
     to `fromTestPoint`
9. **`.from()` call sites** (8 total — production + fixtures):
   - `sim/src/commonMain/.../ControllerWiring.kt:171` (production — covered
     above; passes `reading.coords` not via the helper)
   - `controller/src/commonTest/.../DetermineServiceKindSpec.kt:49,62,75,88,101,114`
     (6 fixtures) — add `coords` named arg derived from the spec's
     `worldIndex.positions[<position>]` lookup
   - `controller/src/commonTest/.../CoordinationsCleanupSpec.kt:67` — same
   - `controller/src/jvmTest/.../ReadbackQueryEscalationIntegrationTest.kt:56` — same

   Total: ~13 sites across both fixture-direct construction and `.from()`
   calls.

## Investigation targets

**Required** (read before coding):
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SensorReading.kt` — current
  shape + `toSensorReading()` body. Confirm bare-identifier `position` read
  is unambiguous (no local var shadowing — verified at plan time).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt:140-184`
  — `AircraftObservation` KDoc + data-class fields + canonical-constructor
  allowlist comments.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/AircraftObservationFactory.kt:24-48`
  — current `from(...)` parameter list (9 params; 10 after).
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/FirewallObservationTest.kt:26-51`
  — the canonical-constructor allowlist enforcement mechanism.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/FirewallSensorReadingTest.kt:45-70`
  — forbidden-pattern list. Verify `position` is NOT in the list (it isn't,
  but sanity-check the regex's negative-lookbehind shape `(?<![.\w])` after
  change to confirm bare-identifier `position` reads do NOT match).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/AircraftState.kt:33-71`
  — confirms `position: Position` (kinematic) and `positionPoint: PointId`
  (snap) coexist sim-side. The KDoc states "two position representations are
  carried side-by-side".
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:46`
  — `Position` data class shape.
- `detekt.yml` — current `LongParameterList.functionThreshold` value.
  Confirm baseline file (`detekt-baseline.xml`) is empty (no per-function
  suppressions cached).

**Optional** (reference as needed):
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/ControllerWiring.kt:60-180`
  — the surrounding `buildControllerView` + `toObservation` flow.

## Key context

- `FirewallSensorReadingTest` has TWO assertions: (1) forbidden-pattern source
  scan; (2) "exactly one production site for SensorReading" via regex on
  function return types. Adding a new field to the existing data class +
  reading inside the existing `toSensorReading()` does NOT trip either
  assertion (the second only counts producers, not field reads).
- `Position` data class has finite-value `require` checks. `Position(0.0, 0.0)`
  is valid for tests but is the placeholder hazard the plan's `fromTestPoint`
  helper exists to prevent.
- The KDoc on `AircraftObservation` explicitly states the rule: "Adding a
  new field here requires it to map to a real-world sensor or visual cue.
  The architectural test enforces this — adding a non-sensor field fails
  to compile against the test's canonical-constructor allowlist." Update
  the KDoc with the disambiguation paragraph from §Approach 2.
- `Meters` at `core/.../world/WorldModel.kt:28` has no companion. fn-6.2
  adds the `fromNauticalMiles` helper there; this task doesn't touch it
  directly, but mention if you encounter a circular import issue.

## Acceptance

- [ ] `SensorReading` has a `coords: Position` field.
- [ ] `toSensorReading()` reads `state.position` (kinematic, bare identifier)
      and assigns it to `coords`. The existing `positionPoint` read for the
      `position: PointId` field is preserved.
- [ ] `AircraftObservation` has a `coords: Position` field alongside
      `position: PointId`, with the disambiguation KDoc paragraph from
      §Approach 2.
- [ ] `AircraftObservationFactory.from(...)` takes a `coords: Position`
      parameter and threads it to the constructor.
- [ ] `AircraftObservation.fromTestPoint(point, worldIndex, ...)` helper
      lives in `controller/src/commonTest/.../AircraftObservationTestFixtures.kt`
      and derives `coords = worldIndex.positions[point]`.
- [ ] `ControllerWiring.toObservation` passes `reading.coords` to `.from()`.
- [ ] `FirewallObservationTest`'s canonical-constructor allowlist names the
      new `coords` arg with a `Position(-999999.0, -999999.0)` sentinel + the
      "firewall sentinel" comment.
- [ ] All other `AircraftObservation` and `AircraftObservation.from()`
      construction sites (per §Approach 8 + 9 — 13 sites total) updated.
      Test fixture sites use `fromTestPoint` where divergence-from-snap
      would be a hazard; documented inline if a fixture deliberately
      diverges.
- [ ] `LongParameterList` resolved via either `@Suppress` or `detekt.yml`
      threshold raise; choice documented in task evidence.
- [ ] `FirewallSensorReadingTest` stays green (forbidden-pattern scan does
      not match `position`).
- [ ] `FirewallObservationTest` stays green.
- [ ] Full test suite (`./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest
      :core:jvmTest :protocol:jvmTest`) stays green.
- [ ] `./gradlew detekt` produces no NEW violations beyond the pre-existing
      baseline.

## Done summary

## Evidence
