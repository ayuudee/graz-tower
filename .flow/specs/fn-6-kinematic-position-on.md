# Kinematic position on AircraftObservation

## Overview

Expose the aircraft's true kinematic coordinates on `AircraftObservation` so
the controller's geometric guards fire when the aircraft *physically* crosses
the configured boundary, rather than waiting for `positionPoint` to snap to
the next-nearest published fix. Adds `coords: Position` to both `SensorReading`
(the firewall-allowed sim → controller projection) and `AircraftObservation`,
and switches `OutsideAerodromeRadius.evaluate` to use `coords` directly.

This is the natural follow-on from `fn-5` (G2 cross-aerodrome). G2 ships
with its R4 gap-magnitude pin relaxed from the doctrinal `≥ 30 s` to
`> 0` because the snap-point geometry can't satisfy it: the cruise route
from `LOWG_RWY_16C_THR` to `LJMB_FIX_OSMOT` has no intermediate fixes, so
`positionPoint` snaps abruptly at the perpendicular bisector — and that
same snap event triggers the pilot's autonomous `InitialContact`. Both
fire within one controller cycle, leaving ~4 s of nominal gap. The
kinematic coords let the rule fire at the actual 12 NM ring (~9 min into
the flight at C172 cruise), restoring multi-minute Class-G transit time.

## Quick commands

```bash
# Build + run the load-bearing tests
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :sim:jvmTest \
    --tests "xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest" \
    --tests "xyz.easiersaid.twr.sim.LowgGoldenTest" \
    --tests "xyz.easiersaid.twr.sim.FirewallSensorReadingTest" \
    --console=plain

nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :controller:jvmTest \
    --tests "xyz.easiersaid.twr.controller.FirewallObservationTest" \
    --tests "xyz.easiersaid.twr.controller.bdi.OutsideAerodromeRadiusSpec" \
    --console=plain

# Detekt must stay clean (no new violations beyond the pre-existing baseline).
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew detekt --console=plain

# Full check (must stay green)
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest :protocol:jvmTest --console=plain
```

## Boundaries / non-goals

- **Out: deriving `position` from `coords` (one-way derivation).** Folding
  divergence-elimination here doubles the pass blast radius — every
  `AircraftObservation` consumer that reads `position` would need
  re-auditing. Filed as `D-PASS-fn6-snap-derived` in
  `~/.claude/plans/pilot-firewall.md` for a future pass. fn-6 keeps both
  fields free arguments; downstream divergence is a future tightening.
  Fixture-level mitigation lands in fn-6.1 (helper that derives test
  `coords` from `positionPoint` so test fixtures cannot diverge).
- **Out: 12 NM → ~7 NM CTR-radius doctrine retune.** ICAO Annex 11 §2.11.5.2
  floor is 5 NM in approach directions; current 22 224 m / 12 NM is ~2.4×
  the floor. Real polygons live in AIPs. Filed as `D-AUDIT-lowg-ctr-radius`.
  Out of fn-6's kinematic-vs-snap scope.
- **Out: polygon-containment airspace checks.** `OutsideAerodromeRadius`
  stays a circular-radius approximation; polygon work is FM/Lean campaign
  territory (`fn-4` lineage, now `done`). The KDoc on
  `OutsideAerodromeRadius` already names the approximation.
- **Out: sensor-noise / radar-update cadence modelling.** Perfect-sensor
  semantics continue. `coords` reflects the latest physics-tick truth; a
  future `RadarTrack { lastReturn, lastReturnAt, smoothed }` wrapper would
  layer cadence + noise on top without re-purposing the existing field.
- **Out: changes to `positionPoint` semantics.** Entity-membership,
  route-progress, and reconciliation continue to key on the snap field.
  This pass is purely additive at the field level.

## Decision context

**Two roads were on the table** for tightening the Class-G gap pin (per
the agent's elaboration in the capture conversation):

1. **Kinematic position on the observation** *(this epic — selected)*
2. Intermediate ENR fixes on the cruise route (rejected because: per-
   aerodrome authoring burden scales with the number of cross-aerodrome
   pairs; option 1 is one structural change vs. world-data work per route).

Option 1 also aligns with the firewall principle the user has emphasized
throughout (`feedback_firewall_principle.md` / `feedback_reality_anchored.md`):
the controller sees radar position. The snap point is a sim-internal graph
artifact, not a real radar return. Per ICAO Annex 11 §6 (ATS surveillance)
and Doc 4444 §8 (radar/ADS-B services), surveillance returns are positional;
the "snap to nearest published fix" projection is a sim-internal artefact of
the world graph, used for entity-membership and route-progress, not for
airspace gates.

**Decision: `coords` is non-nullable.** Perfect-sensor model is in scope for
fn-6; coverage-loss / radar-shadow modelling lands as a separate `RadarTrack`
wrapper (per §Boundaries 4th bullet), not by relaxing this field. Future
work wraps `coords` in `Option<Position>` if needed; consumers re-audit at
that point. Rationale: avoids null-handling boilerplate at every guard for a
property that doesn't exist today, and keeps the field shape parallel to
the existing `position: PointId` (also non-nullable).

**Pass scope (per `feedback_pass_scope.md`):** fold a small typed-NM helper
into the same pass — adding a `companion object` to `Meters` (currently
unowned, at `core/.../world/WorldModel.kt:28`) with `fun fromNauticalMiles
(nm: Int): Meters`. Replaces `Meters(22_224.0)` at the rule's call sites
with `Meters.fromNauticalMiles(12)`. Value classes belong in the closing
pass rather than as drip-feed follow-ups.

**Decision: R6 firewall pin lives inside `FirewallSensorReadingTest`** as a
new `@Test` rather than a new file. Both pins guard the same source
(`SensorReading.kt`); the existing class already has the comment-strip /
project-root / regex-scan plumbing. Mirrors the existing two-firewall split
without proliferating files: controller-side `FirewallObservationTest`
guards the canonical-constructor allowlist; sim-side
`FirewallSensorReadingTest` guards both the forbidden-name negative scan
*and* the new `coords = position` positive scan.

## Approach

The data flows through two existing firewall projections, both of which
must be updated:

1. **`SensorReading`** (`sim/src/commonMain/kotlin/.../SensorReading.kt:29-45`)
   is the single firewall-allowed `AircraftState → controller-side` projection.
   `toSensorReading()` currently reads `state.positionPoint` (snap) only. Add
   `coords: Position` to the data class and read `state.position`. Verified
   firewall-clean: `FirewallSensorReadingTest`'s forbidden-name regex list
   (`pilotMission`, `targetSpeedMps`, `targetAltitudeM`, `phase`, `route`,
   `EntityRef`, `entitiesByPoint`) does **not** include `position` — the
   kinematic field is sensor-observable.

2. **`AircraftObservation`** (`controller/.../ControllerTypes.kt:161-184`)
   gains `coords: Position`. The factory `AircraftObservationFactory.from(...)`
   threads it (parameter count goes from 9 → 10; see Risks below for detekt).
   The canonical-constructor allowlist in `FirewallObservationTest` gets the
   new named arg. All ~13 test fixture / call sites get updated; a new test
   helper `AircraftObservation.fromTestPoint(...)` derives `coords` from
   `worldIndex.positions[point]` so fixtures can't diverge from the snap
   field.

3. **`OutsideAerodromeRadius`** (`controller/.../bdi/Guard.kt:431-459`) drops
   the `acPos = ctx.worldIndex.positions[ac.position]` lookup and uses
   `ac.coords` directly. The KDoc updates to reflect kinematic-vs-snap. A
   focused unit spec lands alongside the change to pin the empirical
   property R3 makes.

4. **`Meters` typed-NM helper** at `core/.../world/WorldModel.kt:28` gets a
   new `companion object` with `fun fromNauticalMiles(nm: Int): Meters =
   Meters(nm * 1852.0)`. Two call sites in `TowerDeparture.kt` (lines ~322
   and ~370) replace `Meters(22_224.0)` with `Meters.fromNauticalMiles(12)`.

5. **R6 firewall pin** lands inside `sim/src/jvmTest/.../FirewallSensorReadingTest.kt`
   as a new `@Test` `coords assigns from kinematic state-position only`.
   Sketch: read `SensorReading.kt` source, strip comments, run
   `Regex("""coords\s*=\s*(\w+)""")` against the `toSensorReading()` body,
   assert the captured identifier is exactly `position` (the bare-identifier
   read of `AircraftState.position`). Sibling `@Test` asserts no local `val
   position` declaration shadows the receiver field inside the function body.

6. **`G2CrossAerodromeVfrTest`** R4 gap pin tightens from `> 0L` back to
   `>= 30_000L`. The inline "tentative band" comment block at lines 455-478
   gets removed, along with the "deferred to a future pass" language in the
   class docstring. After the change, `grep -rn 'fn-6\b' sim/src/jvmTest/`
   should return zero matches.

### Pattern reuse

- `Position` data class at `core/src/commonMain/kotlin/.../core/world/WorldModel.kt:46`
  — `(xMeters, yMeters, altitudeFeet?)`, finite-value `require` checks,
  default equality. Use as-is.
- `Meters` data class at `core/.../world/WorldModel.kt:28` — currently no
  companion. Adding one is part of fn-6's pass scope (per §Decision context).
- `AircraftState.position: Position` at `pilot/.../AircraftState.kt:36` is
  the source of truth, sim-side.
- `AircraftState.positionPoint: PointId` at `pilot/.../AircraftState.kt:37`
  — same data, snap-projected. Both already coexist sim-side; the pattern
  is established.
- `FirewallSensorReadingTest`'s comment-strip / project-root / regex-scan
  plumbing — reuse for the new `coords = position` positive scan.

## Risks / dependencies

- **Dep:** `fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb` is open. fn-6's R5
  modifies fn-5's R4 test pin. fn-6 cannot close cleanly until fn-5 is
  formally `done` (its acceptance pins are green per `project_g2_status.md`,
  but the epic is not yet closed).
- **Risk: detekt `LongParameterList`.** `AircraftObservationFactory.from(...)`
  goes from 9 → 10 parameters; `detekt.yml`'s `functionThreshold` is 8 (the
  9-param state already trips it pre-existing in baseline). fn-6.1 must
  either (a) add `@Suppress("LongParameterList")` with a comment pointing at
  `FirewallObservationTest` (the firewall enforces the param-list shape
  intentionally), or (b) raise the threshold to 10 in `detekt.yml`. Pick
  one explicitly; document the choice in the task evidence. Pre-existing
  detekt complaints (`applyPrecedence` etc.) stay unchanged.
- **Risk: `Position(0,0)` fixture-level divergence.** Fixtures using
  `Position(0,0)` as a placeholder would produce a `coords` far from any
  real ARP, making `OutsideAerodromeRadius` fire spuriously where the
  `worldIndex.positions[ac.position]` lookup previously returned false on
  unknown PointId. Per `feedback_no_corners.md`, this can't be punted to
  "R7 will catch it". fn-6.1 introduces an `AircraftObservation.fromTestPoint(
  point: PointId, worldIndex: WorldIndex, …)` helper that derives
  `coords = worldIndex.positions[point]` so test fixtures structurally
  cannot diverge. Direct `AircraftObservation(...)` construction stays
  available for the firewall test allowlist (which uses a sentinel
  `Position(-999999.0, -999999.0)` that's deliberately not a fixture
  default).
- **Risk: doctrine drift on field meaning.** Avoid re-purposing `coords`
  later (e.g. swapping it from "instantaneous truth" to "smoothed track")
  without a new field name. Practice-scout flagged this as the agent-
  observability literature's silent-rule-drift failure mode. Documented in
  the field's KDoc.
- **Risk: snap-vs-coords drift in production.** The pilot side keeps both
  consistent (positionPoint = nearest known PointId of position). The
  firewall test guards the ingress, not the consistency. Drift is
  structurally possible but currently invisible. The deferred
  `D-PASS-fn6-snap-derived` addresses this.

## Acceptance

- **R1:** `AircraftObservation` gains a `coords: Position` field, populated
  by `AircraftObservationFactory.from(...)` from the corresponding
  `SensorReading.coords`. `SensorReading` itself gains `coords: Position`,
  populated by `toSensorReading()` from `state.position` (the bare-identifier
  read on the `AircraftState` receiver).
- **R2:** `FirewallObservationTest`'s canonical-constructor allowlist
  accepts the new `coords` named arg using a recognisable sentinel value
  (e.g. `Position(-999999.0, -999999.0)`); the firewall-clean assertion
  still passes (compile-time gate). The `~13` direct construction / `from(...)`
  call sites in `controller/src/commonTest`, `controller/src/jvmTest`,
  and `sim/src/jvmTest` are all updated; new test code uses the
  `fromTestPoint(...)` helper where divergence-from-snap would be a hazard.
- **R3:** `OutsideAerodromeRadius.evaluate` reads `ac.coords` directly,
  not via `worldIndex.positions[ac.position]`. A focused unit spec
  (`controller/src/commonTest/.../bdi/OutsideAerodromeRadiusSpec.kt`)
  asserts the geometric property: build an `AircraftObservation` with
  `coords` 100 m inside the configured ring (12 NM) and assert
  `evaluate()` returns `false`; build one 100 m outside and assert it
  returns `true`. Both call sites in `TowerDeparture.kt`
  (DEP-CROSS-AERODROME-RELEASE + DEP-RADAR-SERVICE-TERMINATED) pick up
  identical kinematic semantics; the unit spec is rule-agnostic.
- **R4:** `positionPoint`-keyed semantics elsewhere are unchanged. Verified
  by a structural assertion: `grep -rn 'ac\.coords' controller/src/commonMain`
  finds matches only in `Guard.kt:OutsideAerodromeRadius` (or a documented
  allowlist in fn-6.2 evidence). Other guards / commitment reconciliation /
  route-progress code does not read `coords` — only the new geometric guard
  does. R7's no-regression sweep complements this with the empirical pass.
- **R5:** `G2CrossAerodromeVfrTest`'s R4 gap-magnitude pin tightens to
  `firstTxToLjmbMs - lastLowgInstrMs >= 30_000L`. The inline "tentative
  band" comment block (lines 455-478) is removed; the class docstring's
  "deferred to a future pass" language is removed and replaced with a
  spec'd-out replacement bullet (see fn-6.3 §Approach). After the change,
  `grep -rn 'fn-6\b' sim/src/jvmTest/` returns zero matches.
- **R6:** `FirewallSensorReadingTest` gains a new `@Test` asserting
  `toSensorReading()` populates `coords` from `state.position` (positive:
  the captured identifier in `coords = <ident>` is exactly `position`;
  negative: the function body has no local `val position` shadowing the
  receiver). Both sub-tests live alongside the existing forbidden-name
  scan, sharing its comment-strip pipeline.
- **R7:** No regression: `LowgGoldenTest` (G0), `G2CrossAerodromeVfrTest`,
  `FirewallSensorReadingTest`, `FirewallObservationTest`, all sim/jvm
  tests, all pilot/controller/core/protocol suites stay green. `./gradlew
  detekt` produces no NEW violations beyond the pre-existing baseline.

## Early proof point

Task `fn-6-kinematic-position-on.1` lands the field through both firewall
projections (`SensorReading` + `AircraftObservation`), updates every
construction site, and lands the `fromTestPoint` helper. If
`FirewallObservationTest`'s canonical-constructor allowlist or
`FirewallSensorReadingTest`'s regex source-scan rejects the new field, OR
detekt's `LongParameterList` cannot be cleanly suppressed / threshold-raised,
the whole approach needs re-evaluation: either the field's sensor justification
is weaker than expected, the firewall doctrine is more restrictive than the
spec assumed, or the param-count is a real architectural pressure that wants
a parameter-object refactor. Re-evaluate before fn-6.2/.3.

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | `coords: Position` on `SensorReading` + `AircraftObservation`, threaded via `from(...)` | fn-6.1 | — |
| R2  | `FirewallObservationTest` allowlist + ~13 fixture / call sites + `fromTestPoint` helper | fn-6.1 | — |
| R3  | `OutsideAerodromeRadius` reads `ac.coords` + `OutsideAerodromeRadiusSpec` empirical pin | fn-6.2 | — |
| R4  | `positionPoint` semantics unchanged (structural grep + R7 regression) | fn-6.2 (grep evidence) | — |
| R5  | `G2CrossAerodromeVfrTest` R4 gap pin tightens to `>= 30_000L` | fn-6.3 | — |
| R6  | New `@Test` in `FirewallSensorReadingTest` for `coords = position` source | fn-6.2 | — |
| R7  | No regression across all jvm test suites + detekt baseline | fn-6.1, fn-6.2, fn-6.3 | — |

## Deferments register

Forward-looking entries (not acceptance criteria):

- **`D-PASS-fn6-snap-derived`** — derive `position: PointId` from `coords:
  Position` via `worldIndex.nearestPoint(coords)` in the canonical
  constructor, making divergence structurally impossible at the production
  level. Fn-6 mitigates at the *fixture* level via `fromTestPoint`, but
  production `AircraftObservation.from(...)` callers still pass both as
  free arguments. Future pass.
- **`D-AUDIT-lowg-ctr-radius`** — retune the 12 NM gate per LOWG-AIP CTR
  geometry (probably ~7 NM). Real CTR is polygonal; 12 NM is a generous
  approximation. References ICAO Annex 11 §2.11.5.2 + LOWG AIP AD 2.17.
  Future doctrine pass.
