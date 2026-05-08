---
satisfies: [R1, R2, R3, R4, R9]
---

## Description

Foundation pass for G1: lands the four blocking infra changes the
two-aircraft test depends on. Per `feedback_pass_scope`, fold all
typed-value wins into this single foundation task rather than spawning
follow-ups.

This is the **early proof point** for fn-8: if the `SeparationAssessment`
ADT refactor has hidden blast radius (touches > ~10 sites) or
`SimRandom.derive` reveals deeper PRNG-determinism issues (e.g. the
sim has multiple un-aircraft-scoped RNGs), STOP and re-plan before
fn-8.2 starts.

**Size:** M
**Files:**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt`
  (new `LOWG_TWO_AIRCRAFT` constant + supporting Fixture extension if
  needed)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixture.kt`
  (extend with `startPoints: Map<AircraftId, PointId>` if the existing
  shape can't accommodate; otherwise the new Fixture object overrides
  the standPointId pattern in its own way)
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimRandom.kt`
  (add `derive(label: String)` if absent)
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/Step.kt` or whatever
  dispatches pilot decision ticks (thread `state.rng.derive(id.value)`
  per aircraft)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/SeparationEngine.kt`
  + sibling `WakeSeparation.kt` / `SeparationAssessment.kt` (extend to
  return `SeparationAssessment` with `rule: WakeRule` field)
- Possible call-site sweep wherever `assessSeparation` is consumed
  (audit blast radius first; if > 10 sites, escalate per Risks)

## Approach

### 1. `LOWG_TWO_AIRCRAFT` fixture

Add a new `Fixtures.LOWG_TWO_AIRCRAFT` object alongside existing
`Fixtures.LOWG` and `Fixtures.LOWG_LJMB_VFR` in
`sim/src/jvmTest/kotlin/.../testing/Fixtures.kt`.

Carries:
- Two distinct `PointId`s for the two aircraft's start stands.
  Implementer picks two adjacent GA stands from the LOWG world JSON
  (e.g. `LOWG_STAND_1_POINT` + `LOWG_STAND_2_POINT` or whichever
  cluster the implementer prefers; document choice in KDoc).
- `flightPlans: Map<AircraftId, FiledPlan>` with two entries: aircraft
  A and B, both VFR LOWG → LOWG (circuit training).
- `startPoints: Map<AircraftId, PointId>` — the new shape.

**Two design options for the `Fixture` shape:**
- (a) Extend the existing `Fixture` class to optionally carry
  `startPoints: Map<AircraftId, PointId>?` alongside
  `standPointId: PointId`. Existing fixtures (`LOWG`, `LOWG_LJMB_VFR`)
  set `startPoints = null`; the new fixture sets it. Test code reads
  `fixture.startPoints ?: mapOf(only-aircraft to fixture.standPointId)`.
- (b) Have `Fixture` carry only `startPoints: Map<AircraftId, PointId>`
  and refactor the existing fixtures to use the map shape with one
  entry. Test code always reads `fixture.startPoints[id]`.

**Recommended: (a)** — minimal blast radius on existing fixtures;
existing `LOWG` and `LOWG_LJMB_VFR` semantics unchanged. Document the
asymmetry in `Fixture` KDoc.

### 2. `SimRandom.derive(label: String): SimRandom`

Sub-PRNG factory. Look at the existing `SimRandom` class first; if it
already has `derive`, use it. Otherwise add:

```kotlin
fun SimRandom.derive(label: String): SimRandom {
    // Deterministic salt-based split. label.hashCode() is stable
    // across JVM versions per Kotlin spec (uses java.lang.String.hashCode).
    return SimRandom(seed xor label.hashCode().toLong())
}
```

(Sketch only — pick the canonical hash/split that matches the existing
`SimRandom` shape. If `SimRandom` is `Random`-backed, use its splittable
form.)

**Thread `derive` through pilot decision-tick handlers** so each pilot
gets its own sub-RNG keyed by `AircraftId.value`. Likely site:
`Step.kt:handlePilotProcessingComplete` or
`Step.kt:handlePilotDecisionTick` — wherever the pilot's decision logic
reads `state.rng`. Pass `state.rng.derive(ac.id.value)` instead.

**Pin determinism via a quick property-test or evidence**:
- Run G0 with seed=42 and aircraft id `OE-ABC`; capture trace.
- Run G0 with seed=42 and aircraft id `OE-XYZ`; capture trace.
- Aircraft A's tick outcomes should be identical in both runs (sub-PRNG
  is keyed by id, not by tick order).
- Document this in evidence; not a per-commit test.

### 3. `SeparationAssessment` + `WakeRule` ADT

Audit current `SeparationEngine.assessSeparation` return type at
`controller/src/commonMain/kotlin/.../assess/SeparationEngine.kt`. Per
repo-scout it's a 312-line file with structured assessment logic.

Extend (or introduce) the ADT:

```kotlin
sealed interface WakeRule {
    /** RECAT-EU CAT F → CAT F (light → light): no wake minimum. */
    data object RecatFFNoMin : WakeRule
    /** Doc 4444 §5.8 leader/follower with concrete minimum. */
    data class LeaderFollower(
        val leader: WakeCategory,
        val follower: WakeCategory,
        val minimum: Meters,
    ) : WakeRule
    /** Wake category absent / unknown — engine fails closed. */
    data object UnknownCategory : WakeRule
    // ... add cases as Doc 4444 §5.8 / RECAT-EU coverage requires
}

data class SeparationAssessment(
    val rule: WakeRule,
    val minimumDistance: Meters?,
    // ... existing fields preserved
)
```

**Audit blast radius BEFORE writing the change**: grep
`assessSeparation\|SeparationAssessment` across the codebase. If > 10
call sites need updating, STOP and split this work into its own task
(per Risks register on the epic).

If the existing return type already names cases, this may be an
extension rather than a refactor — confirm at audit time.

### 4. Tick-ordering audit

Confirm `SimState.advance` (or whichever per-cycle dispatcher feeds
pilot decisions) total-orders aircraft by `AircraftId.value`. Repo-scout
flagged that `Fixture.load()` already sorts (`Fixture.kt:255`); the
question is whether subsequent tick processing preserves that order.

If it doesn't, fix with a `sortedBy { it.id.value }` pass at the
dispatch site. If it does, capture the evidence in the task summary.

## Investigation targets

**Required** (read before coding):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixture.kt`
  (current `Fixture` shape; existing `flightPlans` map pattern)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt`
  (existing `LOWG` + `LOWG_LJMB_VFR` patterns to mirror)
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimRandom.kt`
  (current PRNG shape; check for `derive` or splittable factory)
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/Step.kt`
  (find pilot-tick dispatch site; ~line 200ish per repo-scout)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/SeparationEngine.kt`
  (assess return type — 312 lines; `assessSeparation` at ~line 25)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/assess/WakeSeparation.kt`
  (WakeCategory mapping; full Doc 4444 §5.8 table per repo-scout)
- `cad/airports/rendered/lowg/world-candidate.json:1240+`
  (LOWG_STAND_* point IDs — pick two)

**Optional** (reference as needed):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LowgGoldenTest.kt`
  (single-aircraft golden — see how `Fixtures.LOWG.standPointId` is
  consumed; mirror for two)

## Key context

- **Don't gold-plate the PRNG sub-derivation.** Use the simplest
  deterministic salt that's stable across JVM versions
  (`label.hashCode()` is stable per Kotlin spec). This is a small
  helper, not a full splittable-RNG redesign.
- **Don't break existing tests on the SeparationAssessment refactor.**
  If the current return type is widely consumed, prefer a non-breaking
  extension (add `rule` as a new field; existing readers ignore it)
  over replacing the type. Use the safer path even if it leaves a
  cleanup follow-up.
- **The fixture extension is "option a" by default** (extend `Fixture`
  with optional `startPoints`). If during implementation it becomes
  obvious that a clean shape requires a deeper refactor, surface it in
  task evidence and either revisit (b) or file as a follow-up.

## Acceptance

- [ ] `Fixtures.LOWG_TWO_AIRCRAFT` exists with two distinct stand
      `PointId`s, two `flightPlans` entries (VFR LOWG circuit training
      for both aircraft), and a documented stand-pair choice in KDoc
      citing the LOWG AIP / GA cluster.
- [ ] `Fixture` shape supports the multi-aircraft start-points pattern
      (option a recommended; see Approach).
- [ ] `SimRandom.derive(label: String): SimRandom` exists. Pilot
      decision-tick handlers thread per-aircraft sub-PRNG via
      `state.rng.derive(ac.id.value)`.
- [ ] Determinism evidence captured in `## Evidence`: swapping aircraft
      ID order in fixture seeding does not change either pilot's tick
      outcomes (or, if behaviour depends on tick order rather than
      PRNG, document why and confirm tick ordering is total).
- [ ] `SeparationAssessment` return shape from
      `SeparationEngine.assessSeparation` carries a `rule: WakeRule`
      field. `WakeRule` is a sealed hierarchy with at minimum
      `RecatFFNoMin`, `LeaderFollower(leader, follower, minimum)`, and
      one error case (`UnknownCategory` or similar).
- [ ] All existing call sites of `assessSeparation` build and run
      green (no broken consumers).
- [ ] Tick ordering audit captured in `## Evidence`: `SimState.advance`
      (or whichever cycle dispatcher feeds pilot decisions) total-orders
      pilot ticks by `AircraftId.value`. If a fix was needed, named in
      evidence.
- [ ] `LowgGoldenTest`, `G2CrossAerodromeVfrTest` stay green.
- [ ] Full test suite stays green; `./gradlew detekt` baseline unchanged.

## Done summary

## Evidence
